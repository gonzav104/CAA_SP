package com.caa.api.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifica tokens de ID de Google (login con Google).
 * Devuelve un Optional vacío ante cualquier fallo, sin propagar detalles al cliente.
 */
@Component
public class GoogleTokenVerifier {

    private final String googleClientId;

    public GoogleTokenVerifier(@Value("${google.client-id}") String googleClientId) {
        this.googleClientId = googleClientId;
    }

    public Optional<GoogleUsuario> verificar(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                            new NetHttpTransport(), new GsonFactory())
                    .setAudience(List.of(googleClientId))
                    .build();

            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return Optional.empty();
            }

            GoogleIdToken.Payload payload = token.getPayload();
            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                return Optional.empty();
            }

            Object nombreObj = payload.get("name");
            String nombre = nombreObj != null ? nombreObj.toString() : email;

            return Optional.of(new GoogleUsuario(email, nombre));
        } catch (Exception e) {
            // Nunca exponer detalles del fallo al cliente
            return Optional.empty();
        }
    }

    public record GoogleUsuario(String email, String nombre) {
    }
}
