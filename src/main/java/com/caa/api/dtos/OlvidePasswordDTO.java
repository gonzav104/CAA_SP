package com.caa.api.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OlvidePasswordDTO(
        @NotBlank @Email String email
) {
}