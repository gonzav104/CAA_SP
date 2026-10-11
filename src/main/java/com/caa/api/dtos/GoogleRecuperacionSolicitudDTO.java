package com.caa.api.dtos;

import jakarta.validation.constraints.NotBlank;

/** Google proof identifies the intended subject and verified email, never a client-supplied email. */
public record GoogleRecuperacionSolicitudDTO(@NotBlank String idToken) {
}
