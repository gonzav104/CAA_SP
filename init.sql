

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

-- ========================================================
-- MIGRACIÓN 006 — cartillas.paradigma (paradigma de organización del tablero)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 005.
-- Solo soporta TAXONOMICA y ESQUEMATICA hoy (escena-visual queda fuera de alcance:
-- requiere imagen de escena + coordenadas de hotspot por ítem, que el modelo no tiene).
-- Backfill: toda cartilla existente queda en TAXONOMICA, que es el comportamiento
-- actual del front (organizacionDeCartilla siempre devolvía el default hasta ahora).
-- ========================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'paradigma_cartilla'
    ) THEN
        CREATE TYPE paradigma_cartilla AS ENUM ('TAXONOMICA', 'ESQUEMATICA');
    END IF;
END $$;

ALTER TABLE cartillas ADD COLUMN IF NOT EXISTS paradigma paradigma_cartilla NOT NULL DEFAULT 'TAXONOMICA';

-- ========================================================
-- MIGRACIÓN 007 — items_cartilla.es_core (ítem "core" del tablero)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 006.
-- Binario clínico: default FALSE; el core lo marca el terapeuta.
-- Backfill = false: ningún ítem existente es core hasta que se marque explícitamente.
-- ========================================================
ALTER TABLE items_cartilla ADD COLUMN IF NOT EXISTS es_core BOOLEAN NOT NULL DEFAULT FALSE;

-- ========================================================
-- MIGRACIÓN 008 — pacientes.grid_size (tamaño de grilla del tablero en modo de uso)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 007.
-- Columna nullable: ausencia = el tablero usa su grilla por defecto.
-- ========================================================
ALTER TABLE pacientes ADD COLUMN IF NOT EXISTS grid_size INTEGER;

-- ========================================================
-- MIGRACIÓN 009 — items_cartilla.texto_visible / visible_en_modo_uso
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 008.
-- texto_visible: texto corto que se muestra bajo el pictograma (distinto de texto_hablado).
-- Backfill de texto_visible: etiqueta del pictograma (global o custom) o, si no hay, texto_hablado,
-- recortado a 30 caracteres. Luego se fuerza NOT NULL (re-ejecutar no tiene efecto).
-- visible_en_modo_uso: permite ocultar una tarjeta sin borrarla; default TRUE.
-- ========================================================
ALTER TABLE items_cartilla ADD COLUMN IF NOT EXISTS texto_visible VARCHAR(30);

UPDATE items_cartilla i
SET texto_visible = LEFT(COALESCE(
        (SELECT g.etiqueta FROM pictogramas_globales g WHERE g.id = i.recurso_global_id),
        (SELECT c.etiqueta FROM pictogramas_custom c WHERE c.id = i.recurso_custom_id),
        i.texto_hablado), 30)
WHERE i.texto_visible IS NULL;

ALTER TABLE items_cartilla ALTER COLUMN texto_visible SET NOT NULL;

ALTER TABLE items_cartilla ADD COLUMN IF NOT EXISTS visible_en_modo_uso BOOLEAN NOT NULL DEFAULT TRUE;

-- ========================================================
-- MIGRACIÓN 010 — cartillas: una sola cartilla principal por paciente
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 009.
-- (1) Normalización: si un paciente tiene más de una cartilla con es_principal = TRUE,
--     se conserva la MÁS ANTIGUA (creado_en; id como desempate determinista) y el resto
--     pasa a FALSE. Escrita sin UPDATE ... FROM para que sea portable (PostgreSQL y H2).
--     DEBE ejecutarse ANTES de crear el índice, o la creación fallaría con duplicados.
--     Re-ejecutarla no tiene efecto: sin duplicados no actualiza ninguna fila.
-- (2) Índice único parcial: a lo sumo una fila con es_principal = TRUE por paciente.
--     Es la última línea de defensa ante carreras entre requests concurrentes;
--     el servicio ya desmarca la principal anterior dentro de la misma transacción.
-- ========================================================
UPDATE cartillas
SET es_principal = FALSE
WHERE es_principal
  AND id IN (
      SELECT id FROM (
          SELECT id,
                 ROW_NUMBER() OVER (PARTITION BY paciente_id ORDER BY creado_en ASC NULLS LAST, id ASC) AS rn
          FROM cartillas
          WHERE es_principal
      ) ranked
      WHERE ranked.rn > 1
  );

CREATE UNIQUE INDEX IF NOT EXISTS uq_cartillas_una_principal_por_paciente
    ON cartillas (paciente_id) WHERE es_principal;

-- ========================================================
-- MIGRACIÓN 011 — tipos enumerados multi-tenant (rol_gestion, tipo_invitacion, estado_invitacion)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 010.
-- rol_gestion es el rol de GOBERNANZA de una Membresia (OWNER/ADMIN/MIEMBRO), independiente
-- de la capacidad clínica (membresias.es_terapeuta, booleano, MIGRACIÓN 012). No existe ningún
-- valor de rol "TERAPEUTA" en este enum: ambos atributos son independientes a propósito.
-- ========================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'rol_gestion'
    ) THEN
        CREATE TYPE rol_gestion AS ENUM ('OWNER', 'ADMIN', 'MIEMBRO');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'tipo_invitacion'
    ) THEN
        CREATE TYPE tipo_invitacion AS ENUM ('ORGANIZACION', 'PACIENTE_FAMILIAR');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'estado_invitacion'
    ) THEN
        CREATE TYPE estado_invitacion AS ENUM ('PENDIENTE', 'ACEPTADA', 'REVOCADA', 'EXPIRADA');
    END IF;
END $$;

-- ========================================================
-- MIGRACIÓN 012 — organizaciones, membresias (modelo multi-tenant)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 011.
-- Tablas nuevas: CREATE TABLE IF NOT EXISTS las vuelve no-op en re-ejecuciones.
-- rol_gestion y es_terapeuta son atributos INDEPENDIENTES de una Membresia (ver MIGRACIÓN 011):
-- ninguna combinación de los dos está prohibida, por lo que no hay CHECK que los relacione.
-- uq_membresias_un_owner_por_organizacion: índice único PARCIAL (solo filas OWNER) que garantiza
-- a lo sumo un OWNER por organización; la garantía de AL MENOS un OWNER llega en la MIGRACIÓN 015
-- (trigger de restricción diferido, solo disponible una vez pobladas las filas existentes).
-- ========================================================
CREATE TABLE IF NOT EXISTS organizaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(150) NOT NULL,
    creado_por_id UUID NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_organizacion_creado_por FOREIGN KEY (creado_por_id)
        REFERENCES usuarios(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS membresias (
    organizacion_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    rol_gestion rol_gestion NOT NULL,
    es_terapeuta BOOLEAN NOT NULL DEFAULT FALSE,
    unido_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organizacion_id, usuario_id),
    CONSTRAINT fk_membresia_organizacion FOREIGN KEY (organizacion_id)
        REFERENCES organizaciones(id) ON DELETE CASCADE,
    CONSTRAINT fk_membresia_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_membresias_un_owner_por_organizacion
    ON membresias (organizacion_id) WHERE rol_gestion = 'OWNER';

CREATE INDEX IF NOT EXISTS idx_membresias_usuario ON membresias(usuario_id);

-- ========================================================
-- MIGRACIÓN 013 — backfill: organización + membresía OWNER por terapeuta existente
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 012.
-- Alcance: solo terapeutas dueños de >=1 paciente (pacientes.terapeuta_id), no usuarios.rol.
-- Los OWNER backfilleados nacen es_terapeuta = TRUE: son terapeutas en ejercicio real de esos
-- pacientes. Envuelto en BEGIN/COMMIT porque las dos INSERT deben ser atómicas entre sí.
-- Re-ejecutar no inserta filas adicionales: ambas INSERT usan NOT EXISTS sobre su propio destino.
-- ========================================================
BEGIN;

INSERT INTO organizaciones (nombre, creado_por_id)
SELECT LEFT('Consultorio de ' || u.nombre, 150), u.id
FROM usuarios u
WHERE EXISTS (SELECT 1 FROM pacientes p WHERE p.terapeuta_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM organizaciones o WHERE o.creado_por_id = u.id);

INSERT INTO membresias (organizacion_id, usuario_id, rol_gestion, es_terapeuta)
SELECT o.id, o.creado_por_id, 'OWNER', TRUE
FROM organizaciones o
WHERE NOT EXISTS (SELECT 1 FROM membresias m WHERE m.organizacion_id = o.id);

COMMIT;

-- ========================================================
-- MIGRACIÓN 014 — pacientes.organizacion_id, pacientes_terapeutas, invitaciones (DDL)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 013.
-- Solo DDL: el backfill de pacientes.organizacion_id llega en la MIGRACIÓN 015, después de que
-- esta migración exista en todos los entornos (expand antes de backfill).
-- uq_pacientes_id_organizacion es el objetivo del FK compuesto fk_pt_paciente_organizacion.
-- pacientes_terapeutas: tabla nueva (CREATE TABLE IF NOT EXISTS + FKs simples inline); los dos
-- FK compuestos (fk_pt_membresia, fk_pt_paciente_organizacion) se agregan con el idiom de guarda
-- porque dependen de columnas/índices recién creados en esta misma migración.
-- invitaciones: tabla nueva; chk_invitacion_contexto (dos ramas, ORGANIZACION vs PACIENTE_FAMILIAR)
-- también vía el idiom de guarda, siguiendo la convención existente para CHECK constraints.
-- ========================================================
ALTER TABLE pacientes ADD COLUMN IF NOT EXISTS organizacion_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_paciente_organizacion'
    ) THEN
        ALTER TABLE pacientes
            ADD CONSTRAINT fk_paciente_organizacion
            FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id) ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_pacientes_id_organizacion'
    ) THEN
        ALTER TABLE pacientes
            ADD CONSTRAINT uq_pacientes_id_organizacion UNIQUE (id, organizacion_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacientes_organizacion ON pacientes(organizacion_id);

CREATE TABLE IF NOT EXISTS pacientes_terapeutas (
    paciente_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    organizacion_id UUID NOT NULL,
    asignado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (paciente_id, usuario_id),
    CONSTRAINT fk_pt_paciente FOREIGN KEY (paciente_id)
        REFERENCES pacientes(id) ON DELETE CASCADE,
    CONSTRAINT fk_pt_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id) ON DELETE RESTRICT
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pt_membresia'
    ) THEN
        ALTER TABLE pacientes_terapeutas
            ADD CONSTRAINT fk_pt_membresia
            FOREIGN KEY (organizacion_id, usuario_id)
            REFERENCES membresias (organizacion_id, usuario_id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pt_paciente_organizacion'
    ) THEN
        ALTER TABLE pacientes_terapeutas
            ADD CONSTRAINT fk_pt_paciente_organizacion
            FOREIGN KEY (paciente_id, organizacion_id)
            REFERENCES pacientes (id, organizacion_id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacientes_terapeutas_usuario ON pacientes_terapeutas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_pacientes_terapeutas_org_usuario ON pacientes_terapeutas(organizacion_id, usuario_id);

CREATE TABLE IF NOT EXISTS invitaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo tipo_invitacion NOT NULL,
    organizacion_id UUID,
    paciente_id UUID,
    email VARCHAR(255) NOT NULL,
    rol_gestion_propuesto rol_gestion,
    es_terapeuta_propuesto BOOLEAN,
    permiso_propuesto permiso_colaborador,
    token_hash VARCHAR(64) NOT NULL,
    estado estado_invitacion NOT NULL DEFAULT 'PENDIENTE',
    expira_en TIMESTAMP WITH TIME ZONE NOT NULL,
    invitado_por_id UUID NOT NULL,
    aceptada_por_id UUID,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resuelta_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_invitacion_organizacion FOREIGN KEY (organizacion_id)
        REFERENCES organizaciones(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitacion_paciente FOREIGN KEY (paciente_id)
        REFERENCES pacientes(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitacion_invitado_por FOREIGN KEY (invitado_por_id)
        REFERENCES usuarios(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invitacion_aceptada_por FOREIGN KEY (aceptada_por_id)
        REFERENCES usuarios(id) ON DELETE RESTRICT
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_invitacion_contexto'
    ) THEN
        ALTER TABLE invitaciones
            ADD CONSTRAINT chk_invitacion_contexto CHECK (
                (tipo = 'ORGANIZACION' AND organizacion_id IS NOT NULL AND paciente_id IS NULL
                    AND rol_gestion_propuesto IN ('ADMIN', 'MIEMBRO') AND es_terapeuta_propuesto IS NOT NULL
                    AND permiso_propuesto IS NULL)
                OR
                (tipo = 'PACIENTE_FAMILIAR' AND paciente_id IS NOT NULL AND organizacion_id IS NULL
                    AND permiso_propuesto IS NOT NULL AND rol_gestion_propuesto IS NULL
                    AND es_terapeuta_propuesto IS NULL)
            );
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invitaciones_token_hash ON invitaciones(token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uq_invitaciones_pendiente_org
    ON invitaciones (organizacion_id, email) WHERE estado = 'PENDIENTE' AND tipo = 'ORGANIZACION';
CREATE UNIQUE INDEX IF NOT EXISTS uq_invitaciones_pendiente_paciente
    ON invitaciones (paciente_id, email) WHERE estado = 'PENDIENTE' AND tipo = 'PACIENTE_FAMILIAR';
CREATE INDEX IF NOT EXISTS idx_invitaciones_paciente ON invitaciones(paciente_id);
CREATE INDEX IF NOT EXISTS idx_invitaciones_organizacion ON invitaciones(organizacion_id);

-- ========================================================
-- MIGRACIÓN 015 — backfill: pacientes.organizacion_id + pacientes_terapeutas + invariante OWNER
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 014.
-- Alcance ESTRICTO a pacientes sin organización todavía (tmp_pacientes_sin_org): es lo que hace
-- segura una re-ejecución una vez que el código nuevo ya setea organizacion_id en la creación
-- (esos pacientes quedan fuera de tmp_pacientes_sin_org) y una vez que existen pacientes
-- deliberadamente sin terapeuta asignado (no se los reasigna). El filtro es_terapeuta = TRUE en el
-- INSERT a pacientes_terapeutas es un refuerzo del invariante de la sección 3.5 del diseño: nunca
-- se crea una fila de asignación para un terapeuta_id legado que ya no es terapeuta (es_terapeuta
-- = FALSE) de su organización.
-- La función y los triggers de restricción diferida se crean DESPUÉS de todo backfill (no pueden
-- existir antes: fallarían con organizaciones backfilleadas sin OWNER todavía en la misma tx) y
-- están guardados por pg_trigger para que la re-ejecución sea no-op.
-- ========================================================
BEGIN;

CREATE LOCAL TEMPORARY TABLE tmp_pacientes_sin_org AS
SELECT id FROM pacientes WHERE organizacion_id IS NULL;

UPDATE pacientes p
SET organizacion_id = (
    SELECT o.id
    FROM organizaciones o
    JOIN membresias m ON m.organizacion_id = o.id
                      AND m.usuario_id = o.creado_por_id
                      AND m.rol_gestion = 'OWNER'
    WHERE o.creado_por_id = p.terapeuta_id
    ORDER BY o.creado_en ASC NULLS LAST, o.id ASC
    LIMIT 1
)
WHERE p.organizacion_id IS NULL;

INSERT INTO pacientes_terapeutas (paciente_id, usuario_id, organizacion_id)
SELECT p.id, p.terapeuta_id, p.organizacion_id
FROM pacientes p
WHERE p.id IN (SELECT id FROM tmp_pacientes_sin_org)
  AND p.organizacion_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM membresias m
      WHERE m.organizacion_id = p.organizacion_id
        AND m.usuario_id = p.terapeuta_id
        AND m.es_terapeuta = TRUE
  )
  AND NOT EXISTS (
      SELECT 1 FROM pacientes_terapeutas pt
      WHERE pt.paciente_id = p.id AND pt.usuario_id = p.terapeuta_id
  );

DROP TABLE tmp_pacientes_sin_org;

COMMIT;

CREATE OR REPLACE FUNCTION fn_verificar_un_owner() RETURNS TRIGGER AS $$
DECLARE
    v_org UUID;
    v_org_origen UUID;
    v_owners INTEGER;
BEGIN
    -- En una función de trigger por fila, el registro no aplicable a la operación
    -- (OLD en INSERT, NEW en DELETE) no está asignado: no se lo debe referenciar
    -- ni siquiera dentro de COALESCE, o PostgreSQL lanza "record is not assigned yet".
    -- Por eso cada rama toca únicamente el registro garantizado por TG_OP.
    v_org_origen := NULL;

    IF TG_TABLE_NAME = 'organizaciones' THEN
        v_org := NEW.id;
    ELSE
        CASE TG_OP
            WHEN 'DELETE' THEN
                v_org := OLD.organizacion_id;
            WHEN 'INSERT' THEN
                v_org := NEW.organizacion_id;
            ELSE
                -- UPDATE: organizacion_id es NOT NULL y forma parte de la PK de
                -- membresias, por lo que NEW siempre trae el valor vigente. Si el
                -- UPDATE además cambió organizacion_id, la organización de ORIGEN
                -- (OLD) también pudo quedar sin ningún OWNER y debe revalidarse.
                v_org := NEW.organizacion_id;
                IF OLD.organizacion_id IS DISTINCT FROM NEW.organizacion_id THEN
                    v_org_origen := OLD.organizacion_id;
                END IF;
        END CASE;
    END IF;

    IF v_org_origen IS NOT NULL AND EXISTS (SELECT 1 FROM organizaciones WHERE id = v_org_origen) THEN
        SELECT COUNT(*) INTO v_owners
        FROM membresias
        WHERE organizacion_id = v_org_origen AND rol_gestion = 'OWNER';

        IF v_owners <> 1 THEN
            RAISE EXCEPTION 'La organizacion % debe tener exactamente un OWNER (tiene %)', v_org_origen, v_owners
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM organizaciones WHERE id = v_org) THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*) INTO v_owners
    FROM membresias
    WHERE organizacion_id = v_org AND rol_gestion = 'OWNER';

    IF v_owners <> 1 THEN
        RAISE EXCEPTION 'La organizacion % debe tener exactamente un OWNER (tiene %)', v_org, v_owners
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_membresias_un_owner') THEN
        CREATE CONSTRAINT TRIGGER trg_membresias_un_owner
            AFTER INSERT OR UPDATE OF rol_gestion, organizacion_id OR DELETE ON membresias
            DEFERRABLE INITIALLY DEFERRED
            FOR EACH ROW EXECUTE FUNCTION fn_verificar_un_owner();
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_organizaciones_un_owner') THEN
        CREATE CONSTRAINT TRIGGER trg_organizaciones_un_owner
            AFTER INSERT ON organizaciones
            DEFERRABLE INITIALLY DEFERRED
            FOR EACH ROW EXECUTE FUNCTION fn_verificar_un_owner();
    END IF;
END $$;

-- ========================================================
-- MIGRACIÓN 015a — usuarios.rol: relajar NOT NULL (soporte multi-tenant)
-- Idempotente: aplicable sobre bases ya inicializadas con la MIGRACIÓN 015.
-- Numerada como sub-paso de la 015 (no 016) a propósito: la MIGRACIÓN 016 está reservada
-- para el cutover de la Fase 10, que vuelve a poner rol/terapeuta_id en NOT NULL una vez
-- completada la migración a organizaciones. Esta migración es el correlato de esquema del
-- cambio ya aplicado en Usuario.java (@Column(nullable = true) sobre rol, tarea 1.14 de la
-- Fase 1): un usuario puede existir sin rol de legado mientras su identidad de gobernanza
-- viva en membresias.rol_gestion. ALTER COLUMN ... DROP NOT NULL es naturalmente idempotente
-- en PostgreSQL (no falla si la columna ya es nullable), igual que el ALTER ... SET NOT NULL
-- de la MIGRACIÓN 009; no se necesita un bloque DO de guarda.
-- ========================================================
ALTER TABLE usuarios ALTER COLUMN rol DROP NOT NULL;
