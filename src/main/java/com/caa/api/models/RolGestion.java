package com.caa.api.models;

/**
 * Rol de GOBERNANZA de una {@link Membresia} dentro de una {@link Organizacion}.
 * <p>
 * Independiente de la capacidad clínica ({@code Membresia.esTerapeuta}, booleano):
 * ninguna combinación de {@code RolGestion} x {@code esTerapeuta} está prohibida.
 * Este enum NO modela capacidad clínica — no existe ningún valor "TERAPEUTA" aquí.
 */
public enum RolGestion {
    OWNER,
    ADMIN,
    MIEMBRO
}
