package com.caa.api.controllers;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.services.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO dto,
            HttpServletResponse response) {

        AuthResponseDTO authResponse = authService.login(dto);

        Cookie cookie = new Cookie("jwt", authResponse.token());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // true en producción con HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(3600); // 1 hora
        // SameSite se setea vía attribute porque Cookie de jakarta no lo tiene directamente
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        // Devolvemos el body sin el token (ya está en la cookie)
        AuthResponseDTO safeResponse = new AuthResponseDTO(null, authResponse.tipo());
        return ResponseEntity.ok(safeResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        return ResponseEntity.noContent().build();
    }
}
