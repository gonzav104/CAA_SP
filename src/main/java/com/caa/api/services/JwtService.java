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
     * Genera un JWT firmado con HMAC (algoritmo elegido automáticamente según el largo del key:
     * HS256 si 256-383 bits, HS384 si 384-511 bits, HS512 si 512+ bits).
     * Subject = email, claim "rol" = RolUsuario, claim "tokenVersion" = versión actual del token
     * del usuario (fallback 0 si es null — datos viejos), issued = ahora, expiracion configurable.
     */
    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expirationMs);

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .claim("tokenVersion", tokenVersionDe(usuario))
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Fallback defensivo: en datos viejos {@code tokenVersion} puede ser null
     * → se trata como 0 (el valor con el que nacen todos los usuarios).
     */
    private int tokenVersionDe(Usuario usuario) {
        return usuario.getTokenVersion() == null ? 0 : usuario.getTokenVersion();
    }

    /**
     * Valida la firma y expiracion del token, y devuelve el email (subject).
     * Lanza RuntimeException con mensaje claro si el token no es valido.
     */
    public String validarYObtenerEmail(String token) {
        return obtenerClaimsValidados(token).getSubject();
    }

    /**
     * Parsea y valida el token (firma + expiración), devolviendo los claims.
     * Mismo criterio de errores que {@link #validarYObtenerEmail(String)}: lanza
     * RuntimeException con mensaje claro si el token no es válido.
     */
    public Claims obtenerClaimsValidados(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
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
