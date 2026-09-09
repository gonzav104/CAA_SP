package com.caa.api.config;

import com.resend.Resend;
import com.resend.services.domains.Domains;
import com.resend.services.domains.dto.DomainDTO;
import com.resend.services.domains.model.ListDomainsResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResendHealthCheck — valida la config de emails al arrancar (solo loguea, nunca lanza)")
class ResendHealthCheckTest {

    @Mock
    private Resend resend;

    @Mock
    private Domains domains;

    @Mock
    private ApplicationArguments args;

    @Test
    @DisplayName("API key con formato inválido → avisa pero NO hace llamada HTTP")
    void apiKeyFormatoInvalido_noLlamaAlaApi() {
        ResendHealthCheck healthCheck =
                new ResendHealthCheck(resend, "test-resend-api-key", "no-reply@mydomain.com", true);

        healthCheck.run(args);

        // La key no arranca con "re_": no tiene sentido pegarle a la API.
        verify(resend, never()).domains();
    }

    @Test
    @DisplayName("Dominio from-email verificado → loguea OK")
    void dominioVerificado_logueaOk() throws Exception {
        ResendHealthCheck healthCheck =
                new ResendHealthCheck(resend, "re_test123", "no-reply@mydomain.com", true);

        given(resend.domains()).willReturn(domains);
        ListDomainsResponse respuesta = new ListDomainsResponse(
                List.of(dominio("mydomain.com", "verified")), false, "list");
        given(domains.list()).willReturn(respuesta);

        healthCheck.run(args);

        verify(resend).domains();
        verify(domains).list();
    }

    @Test
    @DisplayName("Dominio from-email NO verificado → loguea error (el escenario que nos mordió)")
    void dominioNoVerificado_logueaError() throws Exception {
        ResendHealthCheck healthCheck =
                new ResendHealthCheck(resend, "re_test123", "no-reply@mydomain.com", true);

        given(resend.domains()).willReturn(domains);
        ListDomainsResponse respuesta = new ListDomainsResponse(
                List.of(dominio("otrodominio.com", "verified")), false, "list");
        given(domains.list()).willReturn(respuesta);

        healthCheck.run(args);

        verify(resend).domains();
        verify(domains).list();
    }

    @Test
    @DisplayName("Dominio de sandbox (resend.dev) → loguea warning de modo test")
    void dominioSandbox_logueaWarning() throws Exception {
        ResendHealthCheck healthCheck =
                new ResendHealthCheck(resend, "re_test123", "onboarding@resend.dev", true);

        given(resend.domains()).willReturn(domains);
        ListDomainsResponse respuesta = new ListDomainsResponse(List.of(), false, "list");
        given(domains.list()).willReturn(respuesta);

        healthCheck.run(args);

        verify(resend).domains();
    }

    @Test
    @DisplayName("Health check deshabilitado → no hace NINGUNA verificación")
    void deshabilitado_noVerificaNada() {
        ResendHealthCheck healthCheck =
                new ResendHealthCheck(resend, "re_test123", "no-reply@mydomain.com", false);

        healthCheck.run(args);

        verify(resend, never()).domains();
    }

    private static DomainDTO dominio(String nombre, String estado) {
        // Constructor: (id, name, createdAt, status, region)
        return new DomainDTO("id", nombre, "createdAt", estado, "region");
    }
}