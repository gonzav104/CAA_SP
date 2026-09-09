package com.caa.api.config;

import com.resend.Resend;
import com.resend.services.domains.dto.DomainDTO;
import com.resend.services.domains.model.ListDomainsResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Health check de Resend al arrancar de la app.
 * <p>
 * Valida la configuración de emails ANTES de que nadie intente usar el flujo de
 * recupero de contraseña y descubra recién ahí que no llega nada. Avisa con
 * ERROR/WARN claros y accionables: API key ausente/mal formateada, dominio de
 * {@code from-email} no verificado, o modo sandbox.
 * <p>
 * NO detiene el arranque: el email es un efecto secundario y nunca debe tumbar
 * la operación principal (misma filosofía que {@code EmailServiceImpl}). Solo
 * loguea — pero en nivel ERROR, imposible de no ver.
 * <p>
 * Sobre la llamada HTTP: se usa {@code domains().list()}, una llamada ligera de
 * solo lectura. Con una API key válida el arranque de la app queda supeditado a
 * que Resend responda; si Resend está caído, se loguea el error y la app sigue.
 */
@Component
public class ResendHealthCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResendHealthCheck.class);

    /** Las API keys de Resend SIEMPRE arrancan con este prefijo. */
    private static final String PREFIJO_API_KEY = "re_";

    /** Dominio de sandbox de Resend: solo funciona en modo test/desarrollo. */
    private static final String DOMINIO_SANDBOX = "resend.dev";

    private static final String ESTADO_VERIFICADO = "verified";

    private final Resend resend;
    private final String apiKey;
    private final String fromEmail;
    private final boolean enabled;

    public ResendHealthCheck(
            Resend resend,
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from-email:}") String fromEmail,
            @Value("${app.resend.health-check.enabled:true}") boolean enabled) {
        this.resend = resend;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("Resend health check deshabilitado (app.resend.health-check.enabled=false)");
            return;
        }
        verificar();
    }

    private void verificar() {
        // 1. API key ausente (defensivo: sin la env var el context ya no arranca,
        //    pero un valor vacío en .env sí llega acá).
        if (apiKey.isBlank()) {
            log.error("""
                    ═══════════════════════════════════════════════════════════════
                    ✗ RESEND_API_KEY no está configurada en el .env.
                      Sin ella, NINGÚN email sale (bienvenida, recupero de
                      contraseña, invitaciones de colaborador).
                      Solución: agregá RESEND_API_KEY=re_... al .env y reiniciá.
                    ═══════════════════════════════════════════════════════════════""");
            return;
        }

        // 2. Formato sospechoso: evita llamadas HTTP en tests y captura key
        //    claramente inválidas. Una key de Resend siempre arranca con "re_".
        if (!apiKey.startsWith(PREFIJO_API_KEY)) {
            log.warn("""
                    ═══════════════════════════════════════════════════════════════
                    ? RESEND_API_KEY no tiene formato de API key de Resend
                      (debería empezar con "{}"). Si es una key de prueba o un
                      placeholder, los emails NO van a salir en producción.
                      Solución: pegá la key real del dashboard de Resend.
                    ═══════════════════════════════════════════════════════════════""",
                    PREFIJO_API_KEY);
            return;
        }

        // 3. Config parece real → validamos contra la API (llamada ligera).
        try {
            ListDomainsResponse respuesta = resend.domains().list();
            verificarFromEmail(respuesta.getData());
        } catch (Exception e) {
            log.error("""
                    ═══════════════════════════════════════════════════════════════
                    ✗ No se pudo validar la configuración de Resend contra la API.
                      Los emails NO se van a poder enviar.
                      Causa probable: RESEND_API_KEY inválida o sin permisos.
                    ═══════════════════════════════════════════════════════════════
                    Detalle técnico:""",
                    e);
        }
    }

    /**
     * Valida que el dominio de {@code RESEND_FROM_EMAIL} esté verificado en Resend.
     * Un email desde un dominio no verificado es rechazado por Resend — el
     * usuario final no recibe nada y la app ni se entera (el error se traga).
     */
    private void verificarFromEmail(List<DomainDTO> dominios) {
        String dominioFrom = extraerDominio(fromEmail);
        if (dominioFrom == null) {
            log.error("RESEND_FROM_EMAIL '{}' no tiene formato de email válido. "
                    + "Solución: usá un email real de un dominio verificado en Resend.", fromEmail);
            return;
        }

        if (dominioFrom.equalsIgnoreCase(DOMINIO_SANDBOX)) {
            log.warn("""
                    ═══════════════════════════════════════════════════════════════
                    ? Estás usando el dominio de sandbox de Resend ("{}").
                      Solo funciona en modo test: los emails llegan únicamente al
                      email verificado de la cuenta de Resend, NO al destinatario real.
                      Solución: agregá y verificá un dominio en Resend y configurá
                      RESEND_FROM_EMAIL=algo@tudominio.com en el .env.
                    ═══════════════════════════════════════════════════════════════""",
                    DOMINIO_SANDBOX);
            return;
        }

        boolean verificado = dominios != null && dominios.stream()
                .anyMatch(d -> d.getName() != null
                        && d.getName().equalsIgnoreCase(dominioFrom)
                        && ESTADO_VERIFICADO.equalsIgnoreCase(d.getStatus()));

        if (verificado) {
            log.info("Resend OK: API key válida y dominio '{}' verificado. Emails listos para salir.",
                    dominioFrom);
        } else {
            log.error("""
                    ═══════════════════════════════════════════════════════════════
                    ✗ El dominio "{}" de RESEND_FROM_EMAIL NO está verificado en Resend.
                      Resend rechaza todo email enviado desde un dominio no verificado:
                      el usuario pide recuperar su contraseña, la app responde "revisá
                      tu email" y nunca llega nada.
                      Solución: agregá el dominio en Resend, completá la verificación
                      DNS y reiniciá la app.
                    ═══════════════════════════════════════════════════════════════""",
                    dominioFrom);
        }
    }

    /**
     * Extrae el dominio de un email ("algo@dominio.com" → "dominio.com").
     * Devuelve null si el from-email no es un email válido.
     */
    private static String extraerDominio(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        String dominio = email.substring(email.indexOf('@') + 1).trim();
        return dominio.isBlank() ? null : dominio;
    }
}