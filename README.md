# CAA_SP — Backend API de Comunicación Aumentativa y Alternativa (CAA)

API REST en **Java 21 / Spring Boot 4** que da soporte a la aplicación de CAA para familias de San Pedro, Buenos Aires. Permite a **terapeutas y familiares** gestionar pacientes, cartillas de comunicación (tableros), pictogramas (ARASAAC + custom), sesiones terapéuticas y colaboradores.

Consumida por el frontend **CAA_SP_Front** (React 19 + TypeScript).

---

## Tabla de contenidos

1. [TL;DR — Levantarlo en 3 pasos (dev)](#tl-dr--levantarlo-en-3-pasos-dev)
2. [Stack tecnológico](#stack-tecnológico)
3. [Estructura del repositorio](#estructura-del-repositorio)
4. [Variables de entorno](#variables-de-entorno)
5. [Base de datos](#base-de-datos)
6. [API REST](#api-rest)
7. [Seguridad](#seguridad)
8. [Rendimiento](#rendimiento)
9. [Tests](#tests)
10. [Despliegue (contenedores)](#despliegue-contenedores)
11. [Contrato Front ↔ Back](#contrato-front--back)
12. [Referencias](#referencias)

---

## TL;DR — Levantarlo en 3 pasos (dev)

```bash
# 1. Configurar variables (credenciales de Postgres, JWT, Cloudinary, Resend, Google)
cp .env.example .env
#    ← editá .env y completá los valores reales

# 2. Levantar solo la base de datos (Postgres 15 en el puerto 5444)
docker compose up -d db

# 3. Correr la API
./mvnw spring-boot:run        # Linux / WSL / macOS
.\mvnw.cmd spring-boot:run    # Windows
```

Verificación: `GET http://localhost:8080/v3/api-docs` responde 200 con `"title":"CAA_SP API"`. La app **no arranca** si falta `RESEND_API_KEY` o `POSTGRES_PASSWORD` en el entorno (falla rápido, a propósito).

Para correr los **tests**: `./mvnw test` (285 tests, suite completa en ~40 s).

> **Para levantar TODO containerizado (API + BD)**, ver [Despliegue (contenedores)](#despliegue-contenedores).

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation) |
| Seguridad | Spring Security 7 + JWT (jjwt 0.13.0) en cookie httpOnly |
| Persistencia | PostgreSQL 15 (`docker-compose`), Hibernate, esquema vía `init.sql` (`ddl-auto=none`) |
| Docs API | SpringDoc OpenAPI 3.1.0 (Swagger UI) |
| Cloud | Cloudinary (upload de imágenes) |
| Emails | Resend (transaccionales: bienvenida, recupero de contraseña, invitaciones) |
| Rate limit | Bucket4j (en memoria, single-node) |
| Build | Maven Wrapper (`./mvnw` / `mvnw.cmd`) |
| Contenedores | Docker Compose V2 (Postgres + API) |

**No agregar frameworks ni dependencias nuevas sin justificar primero por qué son necesarias.**

---

## Estructura del repositorio

```
CAA_SP/
├── init.sql                  # Esquema PostgreSQL + migraciones idempotentes (001→006)
├── docker-compose.yml        # Postgres (dev) + API (prod) — lectura de .env con format: raw
├── Dockerfile                # Multi-stage: build JDK 21 → runtime JRE 21 (usuario sin privilegios)
├── .env.example              # Plantilla de variables (copiar a .env, que NO se versiona)
├── .dockerignore             # Excluye .env, target/, *.md del build context
├── mvnw / mvnw.cmd           # Maven Wrapper
└── src/
    ├── main/java/com/caa/api/
    │   ├── controllers/      # 10 controllers REST (rutas anidadas por ownership)
    │   ├── services/         # 11 interfaces + impl + AuthService, JwtService, RateLimitService, ...
    │   ├── repositories/     # 9 Spring Data JPA (ownership a nivel de query)
    │   ├── models/           # 10 entidades + 3 enums (RolUsuario, PermisoColaborador, ParadigmaCartilla)
    │   ├── dtos/             # 36 records (request/response) con Bean Validation
    │   ├── exceptions/       # Conflicto, RecursoNoEncontrado, AccesoDenegado, CredencialesInvalidas, ...
    │   └── config/           # SecurityConfig, JwtAuthenticationFilter, GlobalExceptionHandler, OpenApiConfig, ...
    └── test/java/com/caa/api/   # 285 tests (unitarios + integración HTTP contra H2)
```

**Arquitectura por capas** (orden estricto de dependencias):

```
Controller  →  Service  →  Repository  →  Database
```

- Controllers finos: solo delegan a services con `Principal` (nunca usan repositorios).
- `GlobalExceptionHandler` es el **único** traductor excepción → HTTP.
- Ownership de recursos se aplica **dentro de los queries del repositorio** (ver [Seguridad](#seguridad)).
- No mover clases entre capas ni introducir capas nuevas sin que la tarea lo requiera explícitamente.

---

## Variables de entorno

La app lee todo de variables de entorno (o del archivo `.env` local cargado por dotenv-java). **El `.env` está gitignored y nunca debe versionarse.**

### Inventario completo (19 variables)

| Variable | ¿Requerida? | Default | Descripción |
|---|---|---|---|
| `POSTGRES_PASSWORD` | ✅ sí | — | Password de Postgres. Sin default: compose falla si falta (`${VAR:?msg}`). |
| `JWT_SECRET` | ✅ sí | — | Secreto de firma de JWT (≥ 256 bits en producción). Sin default. |
| `RESEND_API_KEY` | ✅ sí | — | API key de Resend. Sin default → la app no arranca sin ella (intencional). |
| `CLOUDINARY_CLOUD_NAME` | ✅ sí | — | Cloud de Cloudinary. |
| `CLOUDINARY_API_KEY` | ✅ sí | — | API key de Cloudinary. |
| `CLOUDINARY_API_SECRET` | ✅ sí | — | API secret de Cloudinary. |
| `DATASOURCE_URL` | no | `jdbc:postgresql://localhost:5444/caa_db` | JDBC URL. En contenedor se inyecta `jdbc:postgresql://db:5432/...` (compose). |
| `POSTGRES_USER` | no | `postgres` | Usuario de Postgres (default no sensible). |
| `POSTGRES_DB` | no | `caa_db` | Nombre de la base (usada por compose y el healthcheck). |
| `JWT_EXPIRATION_MS` | no | `3600000` | Expiración del token (1 h). El `Max-Age` de la cookie se deriva de acá. |
| `COOKIE_SECURE` | no | `false` | Secure flag de la cookie JWT. **Debe ser `true` en producción** (perfil `prod` lo fuerza). |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173,http://localhost:5174` | Orígenes CORS, separados por coma (se hace `trim()` de espacios). |
| `GOOGLE_CLIENT_ID` | no | *(vacío)* | Client ID web de Google OAuth. Vacío: dev arranca igual. |
| `RESEND_FROM_EMAIL` | no | `onboarding@resend.dev` | Remitente de emails. ⚠️ El default es el **sandbox** de Resend: en prod los emails reales NO salen. Usar dominio verificado. |
| `RESEND_HEALTH_CHECK_ENABLED` | no | `true` | Health check de Resend al arrancar (validar key + dominio). Solo loguea, nunca detiene la app. |
| `FRONTEND_URL` | no | `http://localhost:5173` | Base URL usada en los links de los emails (recupero de contraseña). En prod debe apuntar al frontend real. |
| `RATE_LIMIT_ENABLED` | no | `true` | Activa/desactiva el rate limit (tests la apagan). |
| `RATE_LIMIT_LOGIN_MAX` / `_WINDOW_MINUTES` | no | `5` / `15` | Cupo de login fallido por email cada 15 min. |
| `RATE_LIMIT_OLVIDE_PASSWORD_MAX` / `_WINDOW_MINUTES` | no | `3` / `15` | Cupo de "olvidé mi contraseña" por email cada 15 min. |

> Al tocar `application.properties`, mantener el archivo `.env.example` sincronizado. Las variables sin default deben seguir sin default; las sensibles, nunca con valor real.

---

## Base de datos

**PostgreSQL 15**, schema definido en `init.sql`, montado read-only en el contenedor (`/docker-entrypoint-initdb.d/`). `spring.jpa.hibernate.ddl-auto=none` — **Hibernate jamás crea/actualiza el esquema**; la fuente de verdad es `init.sql`.

### Tablas (9)

| Tabla | Propósito | Claves / constraints clave |
|---|---|---|
| `usuarios` | Terapeutas y familiares | `email` UNIQUE · `rol` enum · `reset_token` · `token_version` (invalidación de sesiones) |
| `pacientes` | Pacientes | `terapeuta_id` FK → `usuarios` **RESTRICT** |
| `pacientes_familiares` | Vínculo familiar ↔ paciente | PK compuesta `(paciente_id, usuario_id)` · `permiso` enum · FK CASCADE |
| `sesiones` | Sesiones clínicas (solo terapeuta) | FK → `pacientes` CASCADE |
| `pictogramas_globales` | Catálogo compartido (ARASAAC) | `arasaac_id` UNIQUE nullable (dedupe de materialización) |
| `pictogramas_custom` | Pictogramas subidos por paciente | FK → `pacientes` CASCADE |
| `cartillas` | Tableros de comunicación | `creador_id` (ownership por creador) · `paradigma` enum · FK CASCADE/RESTRICT |
| `categorias` | Categorías del tablero | `cartilla_id` FK CASCADE · `orden` |
| `items_cartilla` | Celdas del tablero | `recurso_global_id` XOR `recurso_custom_id` (CHECK `check_origen_recurso`) · FK SET NULL |

### Regla entidad ↔ init.sql (obligatoria)

> Si una tarea modifica una entidad JPA o la estructura persistente: **revisar `init.sql` y actualizarlo** (nombres, tipos, constraints y relaciones). **No** dejar una entidad y un esquema desincronizados a propósito.

### Migraciones

`init.sql` se ejecuta **una sola vez en un volumen vacío**. Para bases ya inicializadas, contiene **migraciones idempotentes** embebidas (bloques `ALTER TABLE ... IF NOT EXISTS` + backfill):

| Migración | Cambio |
|---|---|
| 001 | Esquema base (CREATE TABLEs) |
| 002 | `cartillas.creador_id` (ownership por creador, backfill al terapeuta dueño) |
| 003 | `usuarios.reset_token` / `reset_token_expira` |
| 004 | `usuarios.token_version` (invalidar sesiones al restablecer password) |
| 005 | `pictogramas_globales.arasaac_id` + UNIQUE (dedupe ARASAAC) |
| 006 | `cartillas.paradigma` (TAXONOMICA / ESQUEMATICA, default `TAXONOMICA`) |

---

## API REST

Base URL: `http://localhost:8080` (dev). Prefijo de dominio: `/api/**`; autenticación en `/auth/**`.

### Autenticación

| Método | Ruta | Body | Comportamiento |
|---|---|---|---|
| `POST` | `/auth/login` | `{email, password}` | Setea cookie `jwt` (httpOnly + SameSite=Lax). 401 genérico si falla. Rate limit 5/15 min. |
| `POST` | `/auth/google` | `{idToken}` | Login con Google (idToken verificado server-side). |
| `POST` | `/auth/google/completar-registro` | `{idToken, rol}` | Alta vía Google para cuentas nuevas. |
| `POST` | `/auth/olvide-password` | `{email}` | Envía email de recupero. Respuesta **idéntica** exista o no el email. Rate limit 3/15 min. |
| `POST` | `/auth/restablecer-password` | `{token, password}` | Cambia la password con token de un solo uso (1 h, hasheado SHA-256 en BD). Invalida sesiones previas (`token_version`++). |
| `POST` | `/auth/logout` | — | Borra la cookie `jwt`. |
| `POST` | `/api/usuarios/registro` | `{email, password, nombre, rol}` | Crea cuenta. **No setea cookie** (el front debe llevar al login). |
| `GET` | `/api/usuarios/me` | — | Usuario autenticado actual. |

### Pacientes

| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| `GET` | `/api/pacientes` | Autenticado | Rol-aware: terapeuta ve los propios; familiar ve los vinculados con `miPermiso`. |
| `POST` | `/api/pacientes` | **Solo TERAPEUTA** | Crea paciente (403 si otro rol). Body: `{nombre, apellido, fechaNacimiento}`. |
| `GET` | `/api/pacientes/{id}` | Autenticado | Ownership por terapeuta/familiar. |
| `PUT` | `/api/pacientes/{id}` | Autenticado | Actualiza datos. |
| `DELETE` | `/api/pacientes/{id}` | Autenticado | Elimina (CASCADE en BD). |

### Cartillas, categorías e ítems (tablero)

Rutas anidadas bajo `/api/pacientes/{pacienteId}/cartillas/{cartillaId}/categorias/{categoriaId}/items` — **ownership por creador** en modificación:

| Método | Ruta | Notas |
|---|---|---|
| `GET` / `POST` | `/api/pacientes/{pacienteId}/cartillas` | Lista / crea. Body: `{nombre, esPrincipal, paradigma}`. |
| `GET` | `.../cartillas/{cartillaId}` | **Detalle con categorías + items anidados** (1 consulta plana, sin N+1). |
| `PUT` / `DELETE` | `.../cartillas/{cartillaId}` | Solo el **creador** puede modificar/borrar. |
| `POST` / `GET` | `.../categorias` | Crea con `{nombre, colorHex, orden}` / lista. |
| `PUT` / `DELETE` | `.../categorias/{categoriaId}` | Solo creador de la cartilla. |
| `POST` / `GET` | `.../items` | Crea con `{textoHablado, ordenVisual, recursoGlobalId, recursoCustomId}` — **XOR** de recursos validado. |
| `PUT` / `DELETE` | `.../items/{itemId}` | Solo creador. |

### Sesiones (recurso clínico, solo TERAPEUTA)

| Método | Ruta | Notas |
|---|---|---|
| `POST` / `GET` | `/api/pacientes/{pacienteId}/sesiones` | Crea / lista sesiones. |
| `GET` / `PUT` / `DELETE` | `.../sesiones/{sesionId}` | Detalle / actualiza / elimina. |

Los familiares **no** ven sesiones (decisión de modelo).

### Pictogramas

| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| `GET` | `/api/pictogramas-globales` | Autenticado | Catálogo compartido (ARASAAC). Sin paginación. |
| `GET` | `/api/pictogramas-globales/{id}` | Autenticado | Detalle por id. |
| `POST` | `/api/pictogramas-globales/materializar` | Autenticado | `{arasaacId, etiqueta}` → **201** si crea, **200** si ya existía (dedupe por `arasaac_id` UNIQUE). Idempotente. |
| `GET` / `POST` | `/api/pacientes/{pacienteId}/pictogramas-custom` | Autenticado | `POST` es **multipart** (`etiqueta` + `archivo`) → sube a Cloudinary y persiste. |
| `GET` / `PUT` | `.../pictogramas-custom/{id}` | Autenticado | Detalle / edición (multipart). |
| `DELETE` | `.../pictogramas-custom/{id}` | **Solo terapeuta** | El borrado de pictogramas custom es exclusivo del terapeuta. |

### Colaboradores (solo TERAPEUTA)

| Método | Ruta | Notas |
|---|---|---|
| `POST` | `/api/pacientes/{pacienteId}/colaboradores` | Vincula a un familiar por email: `{email, permiso}`. |
| `GET` | `.../colaboradores` | Lista vínculos del paciente. |
| `PUT` | `.../colaboradores/{usuarioId}` | Cambia permiso (`LECTURA` | `EDICION_LIMITADA`). |
| `DELETE` | `.../colaboradores/{usuarioId}` | Desvincula. |

### Formato de errores

Todas las respuestas de error respetan el mismo shape:

```json
{ "timestamp": "2026-09-18T00:00:00", "status": 404, "error": "Recurso no encontrado", "message": "..." }
```

| HTTP | Causa | Mensaje (genérico a propósito) |
|---|---|---|
| `400` | Validación de DTO / JSON malformado / tipo de parámetro inválido / UUID mal formado | Por campo (validación) o genérico (parseo) |
| `401` | Token inválido, expirado o ausente | "Token invalido, expirado o no proporcionado" |
| `403` | Rol insuficiente | "Solo un terapeuta puede registrar un paciente" |
| `404` | Recurso inexistente **o** sin permisos (anti-enumeración) / ruta inexistente | "no encontrado o no tiene permisos" |
| `405` | Método no soportado en la ruta | Genérico |
| `409` | Conflicto (email ya registrado, violación de integridad) | Genérico (nunca expone constraint/columna/SQL) |
| `429` | Rate limit excedido | Genérico |
| `500` | Error inesperado | "Ocurrio un error inesperado" (detalle solo en logs) |

**Regla de oro**: nunca revelar detalles internos al cliente; los mensajes son claros y seguros.

### Documentación OpenAPI

- **Dev** (sin perfil): Swagger UI en `http://localhost:8080/swagger-ui.html` y `GET /v3/api-docs`.
- **Prod** (perfil `prod`): **deshabilitado** (`springdoc.api-docs.enabled=false`).

---

## Seguridad

### Modelo de confianza y roles

| Rol | Puede |
|---|---|
| `TERAPEUTA` | Todo: pacientes propios, sesiones, colaboradores, pictogramas custom, cartillas. |
| `FAMILIAR` | Solo pacientes **vinculados** y con el permiso de su vínculo (`LECTURA` o `EDICION_LIMITADA`). No ve sesiones. No crea pacientes. |

### Ownership (a nivel de repositorio)

Todo acceso a recursos anidados pasa por métodos del repositorio que filtran por la relación de propiedad:

- `findByIdAndTerapeutaId` (Paciente) → `findByIdAndPacienteId` (Sesión/Cartilla) → `findByIdAndPacienteIdAndCreadorId` (Cartilla) → `findByIdAndCategoriaId` (Item).
- **No confiar solo en el ID recibido**: validar que el recurso pertenece al paciente y el paciente al usuario.
- Mensaje único "no encontrado o no tiene permisos" en todos los caminos (protege contra enumeración de recursos).

### Autenticación

- JWT en **cookie httpOnly** (`jwt`), `SameSite=Lax`, `Secure` en prod (`app.cookie.secure=true` vía perfil `prod`), `Max-Age` derivado de `jwt.expiration-ms`.
- El JWT **nunca** viaja en el body (solo cookie). `AuthResponseDTO.token` es siempre `null`.
- `JwtAuthenticationFilter`: valida firma + expiración + `tokenVersion` (consulta al usuario por email).
- Passwords: **BCrypt strength 10**. Reset token: UUID aleatorio, guardado como SHA-256, 1 h de expiración, un solo uso.
- **Rate limit** en memoria (single-node): login 5 fallos / 15 min, olvide-password 3 / 15 min, por `(operación, email)`.

### Reglas del proyecto (no negociables)

- No exponer JWT; no almacenar tokens en respuestas; no loguear credenciales/tokens.
- No diferenciar públicamente errores que permitan enumerar cuentas.
- No escribir secretos reales en código, properties, `init.sql`, docs, logs ni commits → variables de entorno.
- **No introducir endpoints que accedan a recursos por ID sin comprobar ownership.**

---

## Rendimiento

Las consultas de corte pesado (detalle de cartilla con categorías + ítems + pictogramas, y el listado "mis pacientes" de un familiar) se resuelven en **consultas planas** con `@EntityGraph` y `IN` — sin loops N+1.

**Regla de oro**: al tocar consultas JPA/relaciones/carga de entidades, revisar si se **introduce o empeora** un N+1.

**Guard automático**: `QueryCountRegressionIntegrationTest` cuenta PreparedStatement entre fixtures chico y grande y falla si los deltas se disparan — un N+1 nuevo rompe el test aunque los asserts de negocio sigan en verde.

---

## Tests

```bash
./mvnw test        # Linux / WSL / macOS
.\mvnw.cmd test    # Windows
```

**285 tests · 0 fallos · ~40 s** (estado 2026-09-18). Distribución: unitarios de servicios + DTOs + integración HTTP (MockMvc) + config.

- Corren contra **H2 en memoria** (`ddl-auto=create-drop`); `init.sql` no se ejecuta en la suite.
- Cubren casos límite: token vencido/firma inválida, 401 genérico, 429 rate limit, errores sin fuga de secretos, ownership entre terapeutas, permiso de familiar coincidente con su vínculo, regresión de conteo de queries.
- Convención: cada test con su propio email (cero flakiness), reloj inyectado en rate limit (sin sleeps).

**Antes de modificar código**: localizar los tests relacionados, entender qué cubren y **no borrarlos** para hacer pasar el build. Después: correr los tests relevantes y `./mvnw test` si el cambio puede afectar otras áreas.

---

## Despliegue (contenedores)

El backend está **100% containerizado**: Dockerfile multi-stage + servicio `api` en compose, con la API esperando a que Postgres esté sano (`depends_on: db → condition: service_healthy`).

```bash
docker compose up -d --build     # levanta db + api
# API → http://localhost:8080 · PostgreSQL interno → db:5432 (host: 5444)
```

### Piezas clave

| Pieza | Detalle |
|---|---|
| `Dockerfile` | Stage 1: `eclipse-temurin:21-jdk-alpine` → `./mvnw clean package -DskipTests`. Stage 2: `eclipse-temurin:21-jre-alpine`, usuario sin privilegios `caa`, `COPY --from=build .../api-*.jar app.jar`. |
| `docker-compose.yml` | `db` (postgres:15-alpine, healthcheck `pg_isready`, volúmenes persistente `db_data_final` + init.sql :ro) y `api` (build local, `SPRING_PROFILES_ACTIVE=prod`, override `DATASOURCE_URL=jdbc:postgresql://db:5432/...`). |
| `.env` como única fuente | Ambos servicios usan `env_file` con `format: raw`. **Motivo**: Compose interpola `${VAR}` y trunca los valores en el primer `$` — un secreto con `$` llegaba incompleto en silencio. `format: raw` entrega el valor byte a byte. Requiere Compose ≥ 2.24 (verificado en v5.5.1). |
| `application-prod.properties` | Deltas de producción: SpringDoc/Swagger **off** + `app.cookie.secure=true`. Se activa **solo** desde compose; en el host el perfil queda sin activar (Swagger disponible en dev). |

### Requisitos de entorno para `docker compose up`

- Docker Engine con **Compose V2 ≥ 2.24** (por el `env_file.format: raw`).
- `.env` en la raíz con **todas** las variables del [inventario](#variables-de-entorno) que no tienen default (al menos `POSTGRES_PASSWORD`, `JWT_SECRET`, `RESEND_API_KEY`, Cloudinary).

---

## Contrato Front ↔ Back

El frontend **CAA_SP_Front** (React 19) consume esta API con Axios (`withCredentials`). Reglas del contrato:

- **Nunca** cambiar nombres de endpoints, métodos HTTP, nombres de campos JSON, tipos, códigos HTTP, mensajes de error o estructuras de respuesta sin revisar el impacto en el front.
- Si un cambio de contrato es inevitable: documentar **qué cambia, por qué, qué consumidores afecta y qué se debe modificar en el front**.

---

## Referencias

- **Reglas de trabajo del proyecto**: `CLAUDE.md` (convenciones: tareas pequeñas, tests, entidad↔init.sql, contrato front, control de alcance).
- **Frontend**: repositorio `CAA_SP_Front` (React 19 + TypeScript).