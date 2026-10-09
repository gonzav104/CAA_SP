package com.caa.api.services.impl;

import com.caa.api.dtos.AceptarInvitacionDTO;
import com.caa.api.dtos.AceptarInvitacionResponseDTO;
import com.caa.api.dtos.InvitacionOrganizacionRegistroDTO;
import com.caa.api.dtos.InvitacionResponseDTO;
import com.caa.api.exceptions.AccesoDenegadoException;
import com.caa.api.exceptions.ConflictoException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.EstadoInvitacion;
import com.caa.api.models.Invitacion;
import com.caa.api.models.Membresia;
import com.caa.api.models.MembresiaId;
import com.caa.api.models.Organizacion;
import com.caa.api.models.Paciente;
import com.caa.api.models.PacienteFamiliar;
import com.caa.api.models.PacienteFamiliarId;
import com.caa.api.models.PermisoColaborador;
import com.caa.api.models.RolGestion;
import com.caa.api.models.TipoInvitacion;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.InvitacionRepository;
import com.caa.api.repositories.MembresiaRepository;
import com.caa.api.repositories.OrganizacionRepository;
import com.caa.api.repositories.PacienteFamiliarRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AccesoService;
import com.caa.api.services.AccesoService.AccesoPaciente;
import com.caa.api.services.AccesoService.Capacidad;
import com.caa.api.services.EmailService;
import com.caa.api.services.InvitacionService;
import com.caa.api.services.TokenSeguro;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitaciones a organización y familiares (design §9, spec {@code invitations}). Nada se
 * concede hasta la aceptación. El token crudo vive solo en memoria entre su generación y la
 * llamada a {@link EmailService}; solo su hash SHA-256 se persiste.
 */
@Service
@RequiredArgsConstructor
public class InvitacionServiceImpl implements InvitacionService {

    /**
     * Mensaje único para token inexistente, no PENDIENTE, expirado o email no coincidente:
     * nunca revelar cuál de los casos es (mismo criterio que {@code AuthService}).
     */
    private static final String MENSAJE_INVITACION_INVALIDA = "El enlace no es válido o ha expirado";

    private static final int DIAS_VALIDEZ = 7;

    private final InvitacionRepository invitacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrganizacionRepository organizacionRepository;
    private final MembresiaRepository membresiaRepository;
    private final PacienteFamiliarRepository pacienteFamiliarRepository;
    private final AccesoService accesoService;
    private final TokenSeguro tokenSeguro;
    private final EmailService emailService;

    // ──────────────────────────────────────────────
    //  ORGANIZACIÓN
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    public InvitacionResponseDTO crearOrganizacion(UUID organizacionId, InvitacionOrganizacionRegistroDTO dto,
                                                    String emailInvitador) {
        Usuario invitador = usuarioAutenticado(emailInvitador);
        Membresia membresiaInvitador = accesoService.exigirRolGestion(
                organizacionId, invitador, Set.of(RolGestion.OWNER, RolGestion.ADMIN));

        if (dto.rolGestion() == RolGestion.OWNER) {
            throw new IllegalArgumentException("No se puede proponer el rol OWNER en una invitación");
        }
        if (membresiaInvitador.getRolGestion() == RolGestion.ADMIN && dto.rolGestion() != RolGestion.MIEMBRO) {
            throw new AccesoDenegadoException("ADMIN solo puede proponer el rol MIEMBRO");
        }

        String email = normalizar(dto.email());
        rechazarSiYaEsMiembro(organizacionId, email);

        Organizacion organizacion = membresiaInvitador.getOrganizacion();
        Invitacion invitacion = invitacionRepository
                .findByOrganizacion_IdAndEmailAndEstado(organizacionId, email, EstadoInvitacion.PENDIENTE)
                .orElseGet(() -> Invitacion.builder()
                        .tipo(TipoInvitacion.ORGANIZACION)
                        .organizacion(organizacion)
                        .email(email)
                        .invitadoPor(invitador)
                        .estado(EstadoInvitacion.PENDIENTE)
                        .build());

        invitacion.setRolGestionPropuesto(dto.rolGestion());
        invitacion.setEsTerapeutaPropuesto(dto.esTerapeuta());

        String tokenCrudo = emitirToken(invitacion);
        Invitacion guardada = invitacionRepository.saveAndFlush(invitacion);

        // Efecto secundario: EmailServiceImpl nunca propaga excepciones.
        emailService.enviarInvitacionOrganizacion(email, organizacion, dto.rolGestion(), dto.esTerapeuta(), tokenCrudo);

        return toResponseDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitacionResponseDTO> listarOrganizacion(UUID organizacionId, String emailInvitador) {
        Usuario invitador = usuarioAutenticado(emailInvitador);
        accesoService.exigirRolGestion(organizacionId, invitador, Set.of(RolGestion.OWNER, RolGestion.ADMIN));

        return invitacionRepository.findByOrganizacion_IdAndEstado(organizacionId, EstadoInvitacion.PENDIENTE)
                .stream().map(this::toResponseDTO).toList();
    }

    @Override
    @Transactional
    public void revocarOrganizacion(UUID organizacionId, UUID invitacionId, String emailInvitador) {
        Usuario invitador = usuarioAutenticado(emailInvitador);
        accesoService.exigirRolGestion(organizacionId, invitador, Set.of(RolGestion.OWNER, RolGestion.ADMIN));

        Invitacion invitacion = invitacionDeOrganizacion(organizacionId, invitacionId);
        revocarSiPendiente(invitacion);
    }

    // ──────────────────────────────────────────────
    //  FAMILIAR (PACIENTE)
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    public InvitacionResponseDTO crearFamiliar(UUID pacienteId, String emailInvitado, PermisoColaborador permiso,
                                                String emailInvitador) {
        Usuario invitador = usuarioAutenticado(emailInvitador);
        AccesoPaciente acceso = accesoService.exigirCapacidad(pacienteId, invitador, Capacidad.GESTION_CLINICA);
        Paciente paciente = acceso.paciente();

        String email = normalizar(emailInvitado);
        rechazarSiYaEsColaborador(pacienteId, email);

        Invitacion invitacion = invitacionRepository
                .findByPaciente_IdAndEmailAndEstado(pacienteId, email, EstadoInvitacion.PENDIENTE)
                .orElseGet(() -> Invitacion.builder()
                        .tipo(TipoInvitacion.PACIENTE_FAMILIAR)
                        .paciente(paciente)
                        .email(email)
                        .invitadoPor(invitador)
                        .estado(EstadoInvitacion.PENDIENTE)
                        .build());

        invitacion.setPermisoPropuesto(permiso);

        String tokenCrudo = emitirToken(invitacion);
        Invitacion guardada = invitacionRepository.saveAndFlush(invitacion);

        // Efecto secundario: EmailServiceImpl nunca propaga excepciones.
        emailService.enviarInvitacionFamiliar(email, paciente, permiso, tokenCrudo);

        return toResponseDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitacionResponseDTO> listarFamiliar(UUID pacienteId, String emailInvitador) {
        Usuario invitador = usuarioAutenticado(emailInvitador);
        accesoService.exigirCapacidad(pacienteId, invitador, Capacidad.GESTION_CLINICA);

        return invitacionRepository.findByPaciente_IdAndEstado(pacienteId, EstadoInvitacion.PENDIENTE)
                .stream().map(this::toResponseDTO).toList();
    }

    @Override
    @Transactional
    public void revocarFamiliar(UUID pacienteId, UUID invitacionId, String emailInvitador) {
        Usuario invitador = usuarioAutenticado(emailInvitador);
        accesoService.exigirCapacidad(pacienteId, invitador, Capacidad.GESTION_CLINICA);

        Invitacion invitacion = invitacionDeFamiliar(pacienteId, invitacionId);
        revocarSiPendiente(invitacion);
    }

    // ──────────────────────────────────────────────
    //  ACEPTACIÓN (ambos tipos)
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    public AceptarInvitacionResponseDTO aceptar(AceptarInvitacionDTO dto, String emailAutenticado) {
        Usuario usuario = usuarioAutenticado(emailAutenticado);

        Invitacion invitacion = invitacionRepository.findByTokenHash(tokenSeguro.hash(dto.token()))
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_INVITACION_INVALIDA));

        if (!normalizar(usuario.getEmail()).equals(invitacion.getEmail())) {
            throw new RecursoNoEncontradoException(MENSAJE_INVITACION_INVALIDA);
        }

        LocalDateTime ahora = LocalDateTime.now();
        int filas = invitacionRepository.aceptarSiVigente(invitacion.getId(), usuario, ahora);
        if (filas != 1) {
            throw new RecursoNoEncontradoException(MENSAJE_INVITACION_INVALIDA);
        }

        if (invitacion.getTipo() == TipoInvitacion.ORGANIZACION) {
            return aceptarOrganizacion(invitacion, usuario);
        }
        return aceptarFamiliar(invitacion, usuario);
    }

    private AceptarInvitacionResponseDTO aceptarOrganizacion(Invitacion invitacion, Usuario usuario) {
        UUID organizacionId = invitacion.getOrganizacion().getId();
        // Bloqueo de fila (design §3.3 punto 3) antes de leer/escribir Membresia.
        organizacionRepository.findConBloqueoById(organizacionId);

        MembresiaId membresiaId = new MembresiaId(organizacionId, usuario.getId());
        if (membresiaRepository.findById(membresiaId).isEmpty()) {
            Membresia membresia = Membresia.builder()
                    .id(membresiaId)
                    .organizacion(invitacion.getOrganizacion())
                    .usuario(usuario)
                    .rolGestion(invitacion.getRolGestionPropuesto())
                    .esTerapeuta(Boolean.TRUE.equals(invitacion.getEsTerapeutaPropuesto()))
                    .build();
            membresiaRepository.save(membresia);
        }

        return new AceptarInvitacionResponseDTO(TipoInvitacion.ORGANIZACION, organizacionId, null);
    }

    private AceptarInvitacionResponseDTO aceptarFamiliar(Invitacion invitacion, Usuario usuario) {
        UUID pacienteId = invitacion.getPaciente().getId();
        PacienteFamiliarId id = new PacienteFamiliarId(pacienteId, usuario.getId());

        if (pacienteFamiliarRepository.findById(id).isEmpty()) {
            PacienteFamiliar vinculo = PacienteFamiliar.builder()
                    .id(id)
                    .paciente(invitacion.getPaciente())
                    .usuario(usuario)
                    .permiso(invitacion.getPermisoPropuesto())
                    .build();
            pacienteFamiliarRepository.save(vinculo);
        }

        return new AceptarInvitacionResponseDTO(TipoInvitacion.PACIENTE_FAMILIAR, null, pacienteId);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Usuario usuarioAutenticado(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void rechazarSiYaEsMiembro(UUID organizacionId, String emailNormalizado) {
        Optional<Usuario> existente = usuarioRepository.findByEmail(emailNormalizado);
        boolean yaEsMiembro = existente.isPresent()
                && membresiaRepository.findById(new MembresiaId(organizacionId, existente.get().getId())).isPresent();
        if (yaEsMiembro) {
            throw new ConflictoException("No se puede invitar a este usuario");
        }
    }

    private void rechazarSiYaEsColaborador(UUID pacienteId, String emailNormalizado) {
        Optional<Usuario> existente = usuarioRepository.findByEmail(emailNormalizado);
        boolean yaEsColaborador = existente.isPresent()
                && pacienteFamiliarRepository.findByPaciente_IdAndUsuario_Id(pacienteId, existente.get().getId()).isPresent();
        if (yaEsColaborador) {
            throw new ConflictoException("No se puede invitar a este usuario");
        }
    }

    /** Genera un token nuevo, lo hashea en la invitación y resetea su vigencia a 7 días. */
    private String emitirToken(Invitacion invitacion) {
        String tokenCrudo = tokenSeguro.generar();
        invitacion.setTokenHash(tokenSeguro.hash(tokenCrudo));
        invitacion.setExpiraEn(LocalDateTime.now().plusDays(DIAS_VALIDEZ));
        return tokenCrudo;
    }

    private Invitacion invitacionDeOrganizacion(UUID organizacionId, UUID invitacionId) {
        return invitacionRepository.findById(invitacionId)
                .filter(i -> i.getTipo() == TipoInvitacion.ORGANIZACION
                        && i.getOrganizacion() != null
                        && i.getOrganizacion().getId().equals(organizacionId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Invitación no encontrada"));
    }

    private Invitacion invitacionDeFamiliar(UUID pacienteId, UUID invitacionId) {
        return invitacionRepository.findById(invitacionId)
                .filter(i -> i.getTipo() == TipoInvitacion.PACIENTE_FAMILIAR
                        && i.getPaciente() != null
                        && i.getPaciente().getId().equals(pacienteId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Invitación no encontrada"));
    }

    /** Revocar es idempotente: una invitación ya resuelta (ACEPTADA/REVOCADA/EXPIRADA) no cambia. */
    private void revocarSiPendiente(Invitacion invitacion) {
        if (invitacion.getEstado() == EstadoInvitacion.PENDIENTE) {
            invitacion.setEstado(EstadoInvitacion.REVOCADA);
            invitacion.setResueltaEn(LocalDateTime.now());
            invitacionRepository.save(invitacion);
        }
    }

    private InvitacionResponseDTO toResponseDTO(Invitacion i) {
        return new InvitacionResponseDTO(
                i.getId(), i.getTipo(), i.getEmail(), i.getRolGestionPropuesto(), i.getEsTerapeutaPropuesto(),
                i.getPermisoPropuesto(), i.getEstado(), i.getExpiraEn());
    }
}
