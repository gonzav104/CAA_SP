package com.caa.api.controllers;

import com.caa.api.dtos.AuthResponseDTO;
import com.caa.api.dtos.GoogleAuthResponseDTO;
import com.caa.api.dtos.GoogleCompletarRegistroDTO;
import com.caa.api.dtos.GoogleLoginDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.dtos.OlvidePasswordDTO;
import com.caa.api.dtos.RestablecerPasswordDTO;
import com.caa.api.services.AuthService;
import com.caa.api.services.AuthService.GoogleLoginResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
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

        setJwtCookie(response, authResponse.token());

        AuthResponseDTO safeResponse = new AuthResponseDTO(null, authResponse.tipo());
        return ResponseEntity.ok(safeResponse);
    }

    @PostMapping("/google")
    public ResponseEntity<GoogleAuthResponseDTO> loginConGoogle(
            @Valid @RequestBody GoogleLoginDTO dto,
            HttpServletResponse response) {

        GoogleLoginResult result = authService.loginConGoogle(dto.idToken());

        result.token().ifPresent(token -> setJwtCookie(response, token));

        return ResponseEntity.ok(result.dto());
    }

    @PostMapping("/google/completar-registro")
    public ResponseEntity<GoogleAuthResponseDTO> completarRegistroGoogle(
            @Valid @RequestBody GoogleCompletarRegistroDTO dto,
            HttpServletResponse response) {

        GoogleLoginResult result = authService.completarRegistroGoogle(dto);

        result.token().ifPresent(token -> setJwtCookie(response, token));

        return ResponseEntity.ok(result.dto());
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

    @PostMapping("/olvide-password")
    public ResponseEntity<Map<String, String>> olvidePassword(
            @Valid @RequestBody OlvidePasswordDTO dto) {

        authService.olvidePassword(dto.email());

        // Respuesta idéntica exista o no el email: nunca revelar si la cuenta está registrada.
        return ResponseEntity.ok(Map.of(
                "message", "Si el email está registrado, vas a recibir un enlace para restablecer tu contraseña"));
    }

    @PostMapping("/restablecer-password")
    public ResponseEntity<Map<String, String>> restablecerPassword(
            @Valid @RequestBody RestablecerPasswordDTO dto) {

        authService.restablecerPassword(dto);

        return ResponseEntity.ok(Map.of(
                "message", "Tu contraseña fue restablecida correctamente"));
    }

    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(3600);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}