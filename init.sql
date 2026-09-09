

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
                          reset_token VARCHAR(255),
                          reset_token_expira TIMESTAMP WITH TIME ZONE,
                          token_version INTEGER NOT NULL DEFAULT 0,
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
                           creador_id UUID NOT NULL,
                           nombre VARCHAR(100) NOT NULL,
                           es_principal BOOLEAN NOT NULL DEFAULT FALSE,
                           creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_cartilla_paciente FOREIGN KEY (paciente_id)
                               REFERENCES pacientes(id) ON DELETE CASCADE,
                           CONSTRAINT fk_cartilla_creador FOREIGN KEY (creador_id)
                               REFERENCES usuarios(id) ON DELETE RESTRICT
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
CREATE INDEX idx_cartillas_creador ON cartillas(creador_id);
CREATE INDEX idx_categorias_cartilla ON categorias(cartilla_id);
CREATE INDEX idx_items_categoria ON items_cartilla(categoria_id);
CREATE INDEX idx_pictogramas_custom_paciente ON pictogramas_custom(paciente_id);
CREATE INDEX idx_sesiones_paciente ON sesiones(paciente_id);

-- ========================================================
-- MIGRACIÓN 002 — cartillas.creador_id (ownership por creador)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 001.
-- Las cartillas existentes quedan a nombre de su terapeuta dueño.
-- ========================================================

-- 1. Columna nullable primero (la base ya tiene filas)
ALTER TABLE cartillas ADD COLUMN IF NOT EXISTS creador_id UUID;

-- 2. Backfill: toda cartilla existente queda a nombre de su terapeuta dueño
UPDATE cartillas c
SET creador_id = p.terapeuta_id
FROM pacientes p
WHERE c.paciente_id = p.id
  AND c.creador_id IS NULL;

-- 3. Bloquear creación sin creador
ALTER TABLE cartillas ALTER COLUMN creador_id SET NOT NULL;

-- 4. FK con guarda (PostgreSQL NO tiene ADD CONSTRAINT IF NOT EXISTS)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_cartilla_creador'
    ) THEN
        ALTER TABLE cartillas
            ADD CONSTRAINT fk_cartilla_creador
            FOREIGN KEY (creador_id) REFERENCES usuarios(id) ON DELETE RESTRICT;
    END IF;
END $$;

-- 5. Índice para el look-up de ownership (findByIdAndPacienteIdAndCreadorId)
CREATE INDEX IF NOT EXISTS idx_cartillas_creador ON cartillas(creador_id);

-- ========================================================
-- MIGRACIÓN 003 — usuarios.reset_token / reset_token_expira (recupero de contraseña)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 002.
-- Ambas columnas son nullable: solo se pueblan cuando se pide un recupero.
-- ========================================================
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS reset_token VARCHAR(255);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS reset_token_expira TIMESTAMP WITH TIME ZONE;

-- ========================================================
-- MIGRACIÓN 004 — usuarios.token_version (invalidar sesiones al restablecer password)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 003.
-- Las filas existentes quedan en 0 (sin backfill): la versión arranca en 0 para todos.
-- ========================================================
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

-- ========================================================
-- MIGRACIÓN 005 — pictogramas_globales.arasaac_id (dedupe materialización ARASAAC)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 004.
-- Columna nullable: los 24 seedados no requieren backfill obligatorio.
-- UNIQUE constraint: garantiza dedupe por arasaacId (idempotencia del endpoint materializar).
-- ========================================================
ALTER TABLE pictogramas_globales ADD COLUMN IF NOT EXISTS arasaac_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_pictogramas_globales_arasaac_id'
    ) THEN
        ALTER TABLE pictogramas_globales
            ADD CONSTRAINT uq_pictogramas_globales_arasaac_id UNIQUE (arasaac_id);
    END IF;
END $$;