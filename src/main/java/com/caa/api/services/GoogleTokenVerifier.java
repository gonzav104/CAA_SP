package com.caa.api.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifica tokens de ID de Google (login con Google).
 * Devuelve un Optional vacío ante cualquier fallo (token inválido, email no verificado, etc.),
 * sin propagar detalles específicos al cliente.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleTokenVerifier(@Value("${google.client-id}") String googleClientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(), new GsonFactory())
                .setAudience(List.of(googleClientId))
                .build();
    }

    // Visible for testing: permite inyectar un verifier mockeado
    GoogleTokenVerifier(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    public Optional<GoogleUsuario> verificar(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }

        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return Optional.empty();
            }

            GoogleIdToken.Payload payload = token.getPayload();
            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                return Optional.empty();
            }

            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                return Optional.empty();
            }

            Object nombreObj = payload.get("name");
            String nombre = nombreObj != null ? nombreObj.toString() : email;

            return Optional.of(new GoogleUsuario(email, nombre));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record GoogleUsuario(String email, String nombre) {
    }
}