package com.caa.api.services.impl;

import com.caa.api.dtos.CartillaActualizacionDTO;
import com.caa.api.dtos.CartillaRegistroDTO;
import com.caa.api.dtos.CartillaResponseDTO;
import com.caa.api.models.Cartilla;
import com.caa.api.models.Paciente;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.CartillaRepository;
import com.caa.api.repositories.PacienteRepository;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.CartillaService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartillaServiceImpl implements CartillaService {

    private final CartillaRepository cartillaRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public CartillaResponseDTO crearCartilla(UUID pacienteId, CartillaRegistroDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

        Paciente paciente = pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado o no tiene permisos"));

        Cartilla cartilla = Cartilla.builder()
                .paciente(paciente)
                .nombre(dto.nombre())
                .esPrincipal(dto.esPrincipal() != null && dto.esPrincipal())
                .build();

        Cartilla guardada = cartillaRepository.save(cartilla);
        return toResponseDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartillaResponseDTO> obtenerCartillasDePaciente(UUID pacienteId, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado o no tiene permisos"));

        return cartillaRepository.findByPacienteId(pacienteId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public CartillaResponseDTO actualizarCartilla(UUID pacienteId, UUID cartillaId,
                                                  CartillaActualizacionDTO dto, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado o no tiene permisos"));

        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cartilla no encontrada o no tiene permisos"));

        cartilla.setNombre(dto.nombre());
        if (dto.esPrincipal() != null) {
            cartilla.setEsPrincipal(dto.esPrincipal());
        }

        Cartilla actualizada = cartillaRepository.save(cartilla);
        return toResponseDTO(actualizada);
    }

    @Override
    @Transactional
    public void eliminarCartilla(UUID pacienteId, UUID cartillaId, String emailTerapeuta) {
        Usuario terapeuta = usuarioRepository.findByEmail(emailTerapeuta)
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));

        pacienteRepository.findByIdAndTerapeutaId(pacienteId, terapeuta.getId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado o no tiene permisos"));

        Cartilla cartilla = cartillaRepository.findByIdAndPacienteId(cartillaId, pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cartilla no encontrada o no tiene permisos"));

        cartillaRepository.delete(cartilla);
    }

    private CartillaResponseDTO toResponseDTO(Cartilla c) {
        return new CartillaResponseDTO(
                c.getId(),
                c.getPaciente().getId(),
                c.getNombre(),
                c.isEsPrincipal(),
                c.getCreadoEn()
        );
    }
}
