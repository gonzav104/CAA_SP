package com.caa.api.services;

import com.caa.api.models.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // Usa los bytes directos del string (UTF-8), sin decodificar Base64.
        // Así podés poner cualquier clave arbitraria en el .env.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Genera un JWT firmado con HS256.
     * Subject = email, claim "rol" = RolUsuario, issued = ahora, expiracion configurable.
     */
    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expirationMs);

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida la firma y expiracion del token, y devuelve el email (subject).
     * Lanza RuntimeException con mensaje claro si el token no es valido.
     */
    public String validarYObtenerEmail(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (SecurityException e) {
            throw new RuntimeException("Token con firma invalida", e);
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token expirado", e);
        } catch (MalformedJwtException e) {
            throw new RuntimeException("Token malformado", e);
        } catch (UnsupportedJwtException e) {
            throw new RuntimeException("Token no soportado", e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Token vacio o nulo", e);
        }
    }
}
