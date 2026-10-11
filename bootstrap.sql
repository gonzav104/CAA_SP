-- Canonical empty-database bootstrap (PostgreSQL 15), final schema after migration 017.
-- Non-idempotent: run only on a new empty database. Existing databases use isolated
-- migration blocks from init.sql; never replay that historical bootstrap on an upgrade.
-- Materialized from the historical schema; schema-equivalence tests prevent drift.
-- rol_usuario remains intentionally: removing the standalone enum is outside 017.
BEGIN;
SET LOCAL search_path = pg_catalog, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';

CREATE TYPE public.estado_invitacion AS ENUM (
    'PENDIENTE',
    'ACEPTADA',
    'REVOCADA',
    'EXPIRADA'
);

CREATE TYPE public.paradigma_cartilla AS ENUM (
    'TAXONOMICA',
    'ESQUEMATICA'
);

CREATE TYPE public.permiso_colaborador AS ENUM (
    'LECTURA',
    'EDICION_LIMITADA'
);

CREATE TYPE public.rol_gestion AS ENUM (
    'OWNER',
    'ADMIN',
    'MIEMBRO'
);

CREATE TYPE public.rol_usuario AS ENUM (
    'TERAPEUTA',
    'FAMILIAR'
);

CREATE TYPE public.tipo_invitacion AS ENUM (
    'ORGANIZACION',
    'PACIENTE_FAMILIAR'
);

CREATE FUNCTION public.fn_verificar_un_owner() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;

CREATE TABLE public.cartillas (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    paciente_id uuid NOT NULL,
    creador_id uuid NOT NULL,
    nombre character varying(100) NOT NULL,
    es_principal boolean DEFAULT false NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    paradigma public.paradigma_cartilla DEFAULT 'TAXONOMICA'::public.paradigma_cartilla NOT NULL
);

CREATE TABLE public.categorias (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    cartilla_id uuid NOT NULL,
    nombre character varying(100) NOT NULL,
    color_hex character varying(7) DEFAULT '#E0E0E0'::character varying NOT NULL,
    orden integer DEFAULT 0 NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.invitaciones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tipo public.tipo_invitacion NOT NULL,
    organizacion_id uuid,
    paciente_id uuid,
    email character varying(255) NOT NULL,
    rol_gestion_propuesto public.rol_gestion,
    es_terapeuta_propuesto boolean,
    permiso_propuesto public.permiso_colaborador,
    token_hash character varying(64) NOT NULL,
    estado public.estado_invitacion DEFAULT 'PENDIENTE'::public.estado_invitacion NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    invitado_por_id uuid NOT NULL,
    aceptada_por_id uuid,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    resuelta_en timestamp with time zone,
    CONSTRAINT chk_invitacion_contexto CHECK ((((tipo = 'ORGANIZACION'::public.tipo_invitacion) AND (organizacion_id IS NOT NULL) AND (paciente_id IS NULL) AND (rol_gestion_propuesto = ANY (ARRAY['ADMIN'::public.rol_gestion, 'MIEMBRO'::public.rol_gestion])) AND (es_terapeuta_propuesto IS NOT NULL) AND (permiso_propuesto IS NULL)) OR ((tipo = 'PACIENTE_FAMILIAR'::public.tipo_invitacion) AND (paciente_id IS NOT NULL) AND (organizacion_id IS NULL) AND (permiso_propuesto IS NOT NULL) AND (rol_gestion_propuesto IS NULL) AND (es_terapeuta_propuesto IS NULL))))
);

CREATE TABLE public.items_cartilla (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    categoria_id uuid NOT NULL,
    texto_hablado character varying(255) NOT NULL,
    orden_visual integer DEFAULT 0 NOT NULL,
    recurso_global_id uuid,
    recurso_custom_id uuid,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    es_core boolean DEFAULT false NOT NULL,
    texto_visible character varying(30) NOT NULL,
    visible_en_modo_uso boolean DEFAULT true NOT NULL,
    CONSTRAINT check_origen_recurso CHECK ((((recurso_global_id IS NOT NULL) AND (recurso_custom_id IS NULL)) OR ((recurso_global_id IS NULL) AND (recurso_custom_id IS NOT NULL))))
);

CREATE TABLE public.membresias (
    organizacion_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    rol_gestion public.rol_gestion NOT NULL,
    es_terapeuta boolean DEFAULT false NOT NULL,
    unido_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.organizaciones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(150) NOT NULL,
    creado_por_id uuid NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.pacientes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(100) NOT NULL,
    apellido character varying(100) NOT NULL,
    fecha_nacimiento date NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    grid_size integer,
    organizacion_id uuid NOT NULL
);

CREATE TABLE public.pacientes_familiares (
    paciente_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    permiso public.permiso_colaborador DEFAULT 'LECTURA'::public.permiso_colaborador NOT NULL,
    vinculado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.pacientes_terapeutas (
    paciente_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    organizacion_id uuid NOT NULL,
    asignado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.pictogramas_custom (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    paciente_id uuid NOT NULL,
    etiqueta character varying(100) NOT NULL,
    imagen_url text NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.pictogramas_globales (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    etiqueta character varying(100) NOT NULL,
    imagen_url text NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    arasaac_id bigint
);

CREATE TABLE public.sesiones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    paciente_id uuid NOT NULL,
    fecha_hora timestamp without time zone NOT NULL,
    disposicion character varying(255),
    objetivos_trabajados text NOT NULL,
    observaciones text,
    estrategias_y_proximos_pasos text,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.usuarios (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    nombre character varying(100) NOT NULL,
    reset_token character varying(255),
    reset_token_expira timestamp with time zone,
    token_version integer DEFAULT 0 NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ONLY public.cartillas
    ADD CONSTRAINT cartillas_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.categorias
    ADD CONSTRAINT categorias_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invitaciones
    ADD CONSTRAINT invitaciones_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.items_cartilla
    ADD CONSTRAINT items_cartilla_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.membresias
    ADD CONSTRAINT membresias_pkey PRIMARY KEY (organizacion_id, usuario_id);

ALTER TABLE ONLY public.organizaciones
    ADD CONSTRAINT organizaciones_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.pacientes_familiares
    ADD CONSTRAINT pacientes_familiares_pkey PRIMARY KEY (paciente_id, usuario_id);

ALTER TABLE ONLY public.pacientes
    ADD CONSTRAINT pacientes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.pacientes_terapeutas
    ADD CONSTRAINT pacientes_terapeutas_pkey PRIMARY KEY (paciente_id, usuario_id);

ALTER TABLE ONLY public.pictogramas_custom
    ADD CONSTRAINT pictogramas_custom_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.pictogramas_globales
    ADD CONSTRAINT pictogramas_globales_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.sesiones
    ADD CONSTRAINT sesiones_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.pacientes
    ADD CONSTRAINT uq_pacientes_id_organizacion UNIQUE (id, organizacion_id);

ALTER TABLE ONLY public.pictogramas_globales
    ADD CONSTRAINT uq_pictogramas_globales_arasaac_id UNIQUE (arasaac_id);

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_email_key UNIQUE (email);

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);

CREATE INDEX idx_cartillas_creador ON public.cartillas USING btree (creador_id);

CREATE INDEX idx_cartillas_paciente ON public.cartillas USING btree (paciente_id);

CREATE INDEX idx_categorias_cartilla ON public.categorias USING btree (cartilla_id);

CREATE INDEX idx_invitaciones_organizacion ON public.invitaciones USING btree (organizacion_id);

CREATE INDEX idx_invitaciones_paciente ON public.invitaciones USING btree (paciente_id);

CREATE INDEX idx_items_categoria ON public.items_cartilla USING btree (categoria_id);

CREATE INDEX idx_membresias_usuario ON public.membresias USING btree (usuario_id);

CREATE INDEX idx_pacientes_organizacion ON public.pacientes USING btree (organizacion_id);

CREATE INDEX idx_pacientes_terapeutas_org_usuario ON public.pacientes_terapeutas USING btree (organizacion_id, usuario_id);

CREATE INDEX idx_pacientes_terapeutas_usuario ON public.pacientes_terapeutas USING btree (usuario_id);

CREATE INDEX idx_pictogramas_custom_paciente ON public.pictogramas_custom USING btree (paciente_id);

CREATE INDEX idx_sesiones_paciente ON public.sesiones USING btree (paciente_id);

CREATE UNIQUE INDEX uq_cartillas_una_principal_por_paciente ON public.cartillas USING btree (paciente_id) WHERE es_principal;

CREATE UNIQUE INDEX uq_invitaciones_pendiente_org ON public.invitaciones USING btree (organizacion_id, email) WHERE ((estado = 'PENDIENTE'::public.estado_invitacion) AND (tipo = 'ORGANIZACION'::public.tipo_invitacion));

CREATE UNIQUE INDEX uq_invitaciones_pendiente_paciente ON public.invitaciones USING btree (paciente_id, email) WHERE ((estado = 'PENDIENTE'::public.estado_invitacion) AND (tipo = 'PACIENTE_FAMILIAR'::public.tipo_invitacion));

CREATE UNIQUE INDEX uq_invitaciones_token_hash ON public.invitaciones USING btree (token_hash);

CREATE UNIQUE INDEX uq_membresias_un_owner_por_organizacion ON public.membresias USING btree (organizacion_id) WHERE (rol_gestion = 'OWNER'::public.rol_gestion);

CREATE CONSTRAINT TRIGGER trg_membresias_un_owner AFTER INSERT OR DELETE OR UPDATE OF rol_gestion, organizacion_id ON public.membresias DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.fn_verificar_un_owner();

CREATE CONSTRAINT TRIGGER trg_organizaciones_un_owner AFTER INSERT ON public.organizaciones DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.fn_verificar_un_owner();

ALTER TABLE ONLY public.cartillas
    ADD CONSTRAINT fk_cartilla_creador FOREIGN KEY (creador_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.cartillas
    ADD CONSTRAINT fk_cartilla_paciente FOREIGN KEY (paciente_id) REFERENCES public.pacientes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.categorias
    ADD CONSTRAINT fk_categoria_cartilla FOREIGN KEY (cartilla_id) REFERENCES public.cartillas(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.pictogramas_custom
    ADD CONSTRAINT fk_custom_paciente FOREIGN KEY (paciente_id) REFERENCES public.pacientes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.invitaciones
    ADD CONSTRAINT fk_invitacion_aceptada_por FOREIGN KEY (aceptada_por_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.invitaciones
    ADD CONSTRAINT fk_invitacion_invitado_por FOREIGN KEY (invitado_por_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.invitaciones
    ADD CONSTRAINT fk_invitacion_organizacion FOREIGN KEY (organizacion_id) REFERENCES public.organizaciones(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.invitaciones
    ADD CONSTRAINT fk_invitacion_paciente FOREIGN KEY (paciente_id) REFERENCES public.pacientes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.items_cartilla
    ADD CONSTRAINT fk_item_categoria FOREIGN KEY (categoria_id) REFERENCES public.categorias(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.items_cartilla
    ADD CONSTRAINT fk_item_custom FOREIGN KEY (recurso_custom_id) REFERENCES public.pictogramas_custom(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.items_cartilla
    ADD CONSTRAINT fk_item_global FOREIGN KEY (recurso_global_id) REFERENCES public.pictogramas_globales(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.membresias
    ADD CONSTRAINT fk_membresia_organizacion FOREIGN KEY (organizacion_id) REFERENCES public.organizaciones(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.membresias
    ADD CONSTRAINT fk_membresia_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.organizaciones
    ADD CONSTRAINT fk_organizacion_creado_por FOREIGN KEY (creado_por_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.pacientes
    ADD CONSTRAINT fk_paciente_organizacion FOREIGN KEY (organizacion_id) REFERENCES public.organizaciones(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.pacientes_familiares
    ADD CONSTRAINT fk_pf_paciente FOREIGN KEY (paciente_id) REFERENCES public.pacientes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.pacientes_familiares
    ADD CONSTRAINT fk_pf_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.pacientes_terapeutas
    ADD CONSTRAINT fk_pt_membresia FOREIGN KEY (organizacion_id, usuario_id) REFERENCES public.membresias(organizacion_id, usuario_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.pacientes_terapeutas
    ADD CONSTRAINT fk_pt_paciente FOREIGN KEY (paciente_id) REFERENCES public.pacientes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.pacientes_terapeutas
    ADD CONSTRAINT fk_pt_paciente_organizacion FOREIGN KEY (paciente_id, organizacion_id) REFERENCES public.pacientes(id, organizacion_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.pacientes_terapeutas
    ADD CONSTRAINT fk_pt_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.sesiones
    ADD CONSTRAINT fk_sesion_paciente FOREIGN KEY (paciente_id) REFERENCES public.pacientes(id) ON DELETE CASCADE;

COMMIT;
