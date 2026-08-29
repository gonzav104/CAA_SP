package com.caa.api.config;

import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                String email = jwtService.validarYObtenerEmail(token);

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

                    if (usuario != null) {
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                                "ROLE_" + usuario.getRol().name());

                        // Guardamos el EMAIL como principal (no el objeto Usuario).
                        // Así principal.getName() devuelve el email directamente,
                        // y el servicio no necesita hacer un extra lookup.
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        usuario.getEmail(), null, List.of(authority));
                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (RuntimeException e) {
                // Token invalido o expirado — dejamos pasar sin autenticar.
                // La request sera rechazada por .anyRequest().authenticated() si corresponde.
            }
        }

        filterChain.doFilter(request, response);
    }
}
