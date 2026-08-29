

-- Habilitar extensión para generación de UUIDs nativos
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ========================================================
-- 1. TIPOS ENUMERADOS (DOMINIO)
-- ========================================================
CREATE TYPE rol_usuario AS ENUM ('TERAPEUTA', 'FAMILIAR');
CREATE TYPE permiso_colaborador AS ENUM ('LECTURA', 'EDICION_LIMITADA');

-- ========================================================
-- 2. TABLAS DE IDENTIDAD Y ACCESO
-- ========================================================
CREATE TABLE usuarios (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          email VARCHAR(255) NOT NULL UNIQUE,
                          password_hash VARCHAR(255) NOT NULL,
                          nombre VARCHAR(100) NOT NULL,
                          rol rol_usuario NOT NULL,
                          creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pacientes (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           terapeuta_id UUID NOT NULL,
                           nombre VARCHAR(100) NOT NULL,
                           apellido VARCHAR(100) NOT NULL,
                           fecha_nacimiento DATE NOT NULL,
                           creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_paciente_terapeuta FOREIGN KEY (terapeuta_id)
                               REFERENCES usuarios(id) ON DELETE RESTRICT
);

CREATE TABLE pacientes_familiares (
                                      paciente_id UUID NOT NULL,
                                      usuario_id UUID NOT NULL,
                                      permiso permiso_colaborador NOT NULL DEFAULT 'LECTURA',
                                      vinculado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (paciente_id, usuario_id),
                                      CONSTRAINT fk_pf_paciente FOREIGN KEY (paciente_id)
                                          REFERENCES pacientes(id) ON DELETE CASCADE,
                                      CONSTRAINT fk_pf_usuario FOREIGN KEY (usuario_id)
                                          REFERENCES usuarios(id) ON DELETE CASCADE
);

-- ========================================================
-- 3. TABLA DE SESIONES / EVOLUCIÓN TERAPÉUTICA
-- ========================================================
CREATE TABLE sesiones (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          paciente_id UUID NOT NULL,
                          fecha_hora TIMESTAMP NOT NULL,
                          disposicion VARCHAR(255),
                          objetivos_trabajados TEXT NOT NULL,
                          observaciones TEXT,
                          estrategias_y_proximos_pasos TEXT,
                          creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_sesion_paciente FOREIGN KEY (paciente_id)
                              REFERENCES pacientes(id) ON DELETE CASCADE
);

-- ========================================================
-- 4. TABLAS DE RECURSOS MULTIMEDIA (MODELO HÍBRIDO)
-- ========================================================
CREATE TABLE pictogramas_globales (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      etiqueta VARCHAR(100) NOT NULL,
                                      imagen_url TEXT NOT NULL,
                                      creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pictogramas_custom (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    paciente_id UUID NOT NULL,
                                    etiqueta VARCHAR(100) NOT NULL,
                                    imagen_url TEXT NOT NULL,
                                    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_custom_paciente FOREIGN KEY (paciente_id)
                                        REFERENCES pacientes(id) ON DELETE CASCADE
);

-- ========================================================
-- 5. TABLAS DEL TABLERO Y COMUNICACIÓN
-- ========================================================
CREATE TABLE cartillas (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           paciente_id UUID NOT NULL,
                           nombre VARCHAR(100) NOT NULL,
                           es_principal BOOLEAN NOT NULL DEFAULT FALSE,
                           creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_cartilla_paciente FOREIGN KEY (paciente_id)
                               REFERENCES pacientes(id) ON DELETE CASCADE
);

CREATE TABLE categorias (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            cartilla_id UUID NOT NULL,
                            nombre VARCHAR(100) NOT NULL,
                            color_hex VARCHAR(7) NOT NULL DEFAULT '#E0E0E0',
                            orden INT NOT NULL DEFAULT 0,
                            creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT fk_categoria_cartilla FOREIGN KEY (cartilla_id)
                                REFERENCES cartillas(id) ON DELETE CASCADE
);

CREATE TABLE items_cartilla (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                categoria_id UUID NOT NULL,
                                texto_hablado VARCHAR(255) NOT NULL,
                                orden_visual INT NOT NULL DEFAULT 0,
                                recurso_global_id UUID NULL,
                                recurso_custom_id UUID NULL,
                                creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_item_categoria FOREIGN KEY (categoria_id)
                                    REFERENCES categorias(id) ON DELETE CASCADE,
                                CONSTRAINT fk_item_global FOREIGN KEY (recurso_global_id)
                                    REFERENCES pictogramas_globales(id) ON DELETE SET NULL,
                                CONSTRAINT fk_item_custom FOREIGN KEY (recurso_custom_id)
                                    REFERENCES pictogramas_custom(id) ON DELETE SET NULL,
    -- Validación: El item debe apuntar obligatoriamente a una imagen (global O custom)
                                CONSTRAINT check_origen_recurso CHECK (
                                    (recurso_global_id IS NOT NULL AND recurso_custom_id IS NULL) OR
                                    (recurso_global_id IS NULL AND recurso_custom_id IS NOT NULL)
                                    )
);

-- ========================================================
-- 6. ÍNDICES DE RENDIMIENTO (OPTIMIZACIÓN DE CONSULTAS)
-- ========================================================
CREATE INDEX idx_pacientes_terapeuta ON pacientes(terapeuta_id);
CREATE INDEX idx_cartillas_paciente ON cartillas(paciente_id);
CREATE INDEX idx_categorias_cartilla ON categorias(cartilla_id);
CREATE INDEX idx_items_categoria ON items_cartilla(categoria_id);
CREATE INDEX idx_pictogramas_custom_paciente ON pictogramas_custom(paciente_id);
CREATE INDEX idx_sesiones_paciente ON sesiones(paciente_id);