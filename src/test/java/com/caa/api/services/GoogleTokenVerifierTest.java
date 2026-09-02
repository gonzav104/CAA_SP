package com.caa.api.services;

import com.caa.api.services.GoogleTokenVerifier.GoogleUsuario;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleTokenVerifier — verificación de tokens de Google")
class GoogleTokenVerifierTest {

    private final GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
    private final GoogleTokenVerifier googleTokenVerifier = new GoogleTokenVerifier(mockVerifier);

    @Test
    @DisplayName("Token null → Optional.empty()")
    void tokenNull_devuelveVacio() throws Exception {
        assertThat(googleTokenVerifier.verificar(null)).isEmpty();
        verify(mockVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("Token en blanco → Optional.empty()")
    void tokenEnBlanco_devuelveVacio() throws Exception {
        assertThat(googleTokenVerifier.verificar("  ")).isEmpty();
        verify(mockVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("Token inválido (verifier retorna null) → Optional.empty()")
    void tokenInvalido_devuelveVacio() throws Exception {
        given(mockVerifier.verify("token-falso")).willReturn(null);

        Optional<GoogleUsuario> resultado = googleTokenVerifier.verificar("token-falso");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Token válido pero email_verified=false → Optional.empty()")
    void emailVerifiedFalse_devuelveVacio() throws Exception {
        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("test@ejemplo.com");
        payload.set("name", "Test User");
        payload.setEmailVerified(false);
        given(mockIdToken.getPayload()).willReturn(payload);

        given(mockVerifier.verify("token-google")).willReturn(mockIdToken);

        Optional<GoogleUsuario> resultado = googleTokenVerifier.verificar("token-google");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Token válido y email_verified=true → devuelve GoogleUsuario con email y nombre")
    void emailVerifiedTrue_devuelveGoogleUsuario() throws Exception {
        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("test@ejemplo.com");
        payload.set("name", "Test User");
        payload.setEmailVerified(true);
        given(mockIdToken.getPayload()).willReturn(payload);

        given(mockVerifier.verify("token-google")).willReturn(mockIdToken);

        Optional<GoogleUsuario> resultado = googleTokenVerifier.verificar("token-google");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().email()).isEqualTo("test@ejemplo.com");
        assertThat(resultado.get().nombre()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Token con email_verified=null → Optional.empty()")
    void emailVerifiedNull_devuelveVacio() throws Exception {
        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("test@ejemplo.com");
        // emailVerified queda null por defecto
        given(mockIdToken.getPayload()).willReturn(payload);

        given(mockVerifier.verify("token-google")).willReturn(mockIdToken);

        Optional<GoogleUsuario> resultado = googleTokenVerifier.verificar("token-google");

        assertThat(resultado).isEmpty();
    }
}