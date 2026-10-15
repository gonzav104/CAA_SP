# Backend close-out

Locator: `odd/tasks/backend-close-out.md` · Engram mirror `odd/backend-close-out/tasks`

## Objective

Close the backend for frontend work: remove dead code and obsolete names, fix only small/safe (class B) security and robustness findings, and freeze the onboarding contract. No redesign, no new infrastructure, no new migrations, no frontend changes, no active DB changes.

## Verified starting state (2026-10-15)

- `main` = `767e8da`, contains `feat/google-identity-recovery` up to `a104035`.
- Active `caa_db` is post-017 (no `usuarios.rol`, no `pacientes.terapeuta_id`) and post-018 (all `google_*` columns present), verified read-only via `information_schema`. The earlier docs stating "018 NOT applied" are stale.
- Worktree branches `feat/b1..b3` are pre-multitenant (migrations 010/011 already re-implemented on main); ignored.
- RDD: on (default). The frontend repo does not reference `requiereRol`.

## Findings classification

A (blocks frontend): none.

B (fix now): see tasks.

C (accepted pre-deploy debt):
- CSRF disabled; auth cookie is HttpOnly + `SameSite=Lax`, no state-changing GET. Frontend and API must be same-site in deploy.
- Logout clears the cookie but does not bump `tokenVersion`; a stolen token lives until expiry (1h).
- Rate-limit bucket map is keyed by email and never evicted (unbounded growth); no per-IP limiting.
- Registration returns 409 for an existing email (enumeration); no small fix without email verification.
- First materializer of an ARASAAC id chooses the global `etiqueta`; Cloudinary images are never deleted on update/delete or rollback.
- `PUT /cartillas/{id}` with `esPrincipal` overlaps `PUT /cartillas/{id}/principal` (contract; left).
- "Pictogram in use" returns 400 instead of 409 (contract; left).
- Each request loads the user twice (filter + service); cheap, left.

## Tasks

- [x] WU1 — Cleanup (no behavior change). Route: delegated direct (writer trigger: 10+ files).
  - Delete dead code: `UsuarioRepository.findByResetToken`, `PacienteService.pacienteLegibleParaUsuario`/`verificarEdicionParaUsuario`, `JwtService.validarYObtenerEmail`, `RateLimitTimeMeter.reiniciar`; retarget their tests.
  - `AuthService` reuses `TokenSeguro` for token generation/hashing (remove duplicate `hashResetToken` and own `SecureRandom`).
  - Rename obsolete `emailTerapeuta` parameters to `email`; rename `GoogleAuthResponseDTO.requiereRol` to `requiereRegistro` (JSON contract change; frontend does not consume it yet).
  - `@EntityGraph(attributePaths = "usuario")` on `PacienteFamiliarRepository.findByPaciente_Id` (N+1 in collaborator listing).
  - `spring.jpa.show-sql=false` in prod.
  - Docs: record that 018 is applied to `caa_db`.
- [x] WU2 — Small security/robustness fixes. Route: delegated direct.
  - HTML-escape `nombre` in welcome and password-reset emails.
  - Send invitation emails after commit, reusing the existing `afterCommit` mechanism.
  - Revoke invitations with a conditional `PENDIENTE` update (race with accept).
  - Multipart limits (5MB file) and 400/413/415 handlers instead of 500.
  - Foreign custom pictogram in item create/update returns 404 instead of a 400 existence oracle.
- [x] WU3 — Full suite once, close report. Route: inline (bounded command + summary).

## Checks

Targeted tests per WU; `./mvnw test` once at the end.

## Progress

- WU1 done (delegated writer). Dead code removed after `rg` confirmed zero production consumers; tests retargeted: `GoogleRecoveryPostgresIntegrationTest` → `findLockedByResetToken` in a `TransactionTemplate`; `AccesoFamiliarServiceTest` helper cases → real `AccesoServiceImpl.exigirCapacidad` with mocked repositories (same 5 scenarios); `JwtServiceTest` → `obtenerClaimsValidados`.
- `AuthService` uses `TokenSeguro.generar()` for Google recovery and `TokenSeguro.hash()` everywhere; password-reset token stays `UUID` (only the hash was deduplicated). `AuthServiceTest` uses a real `@Spy TokenSeguro`.
- `./mvnw -q -o test-compile`: success.
- `./mvnw -o test -Dtest='AuthServiceTest,JwtServiceTest,AccesoFamiliarServiceTest,ColaboradorServiceTest,RateLimit*Test,GoogleRecoveryPostgresIntegrationTest,TokenSeguroTest,RecuperoPasswordIntegrationTest'`: 107 tests, 0 failures, 0 errors, BUILD SUCCESS (Testcontainers PostgreSQL available).
- WU2 done (delegated writer). `AuthService.afterCommit` moved verbatim to `services/AfterCommit.run` (runs immediately without active synchronization); used by `AuthService` and both invitation emails. Revoke uses conditional `InvitacionRepository.revocarSiPendiente` (PENDIENTE -> REVOCADA + `resueltaEn`, never touches `aceptadaPor`); response stays 204 in every state (0 rows = idempotent no-op, as before). Foreign custom pictogram now throws the same `RecursoNoEncontradoException("Pictograma custom no encontrado")` as a missing one (400 -> 404). Multipart limits 5MB/6MB; handlers 413/400/415 (previously 500 via catch-all).
- WU2 RED: `test-compile` failed on missing `revocarSiPendiente` and the three handler methods; then runtime RED (handler test stashed): `EmailServiceTest.welcomeEscapesName`, `passwordRecoveryEscapesName`, `InvitacionServiceImplTest.crearOrganizacion_emailSoloTrasCommit`, `crearFamiliar_rollback_noEnviaEmail`, `revocarOrganizacion_pendiente_laRevoca`, `ItemCartillaServiceTest.crear_customDeOtroPaciente_lanzaExcepcion` failed. Repository tests for `revocarSiPendiente` only had compile RED. `ColaboradorServiceIntegrationTest` (class-level `@Transactional`) now fires registered `afterCommit` callbacks to capture the invitation token.
- WU2 GREEN: `./mvnw -q -o test-compile` success; targeted run (Email, ItemCartilla, Invitacion*, GlobalExceptionHandler, Colaborador integration, AuthService, GoogleRecovery Postgres, RecuperoPassword, CartillaService, PictogramaCustomIntegrationTest): 165 tests, 0 failures, 0 errors, BUILD SUCCESS.
- WU1 commit `2ca1044`; WU2 commit `e3bd2bc`.
- RDD: WU1 alone assessed medium/`slice_budget_reached`; user chose skip (exact decline invocation refused because untracked files appeared mid-run; no substitute command run). WU1+WU2 range (`767e8da..e3bd2bc`, 41 files, 745 lines) medium: user granted, lens `review-reliability`, **approved** and acknowledged (lineage `review-4e3b1aba3b09528e`, authority burned). Advisory only: `requiereRegistro` is a JSON rename; manual `afterCommit` firing in `ColaboradorServiceIntegrationTest`.
- WU3 `./mvnw -o test`: 601 tests, 0 failures, 1 error — `LegacyCleanupPostgresIntegrationTest.rejectsPartialCleanupWithoutChangingAnything` (Testcontainers connection refused at startup; class untouched). Re-run alone: 10/10 pass, BUILD SUCCESS → environmental flake.

## Frontend contract (frozen)

- Identity: `POST /api/usuarios/registro`, `POST /auth/login`, `POST /auth/google` → `{requiereRegistro, tipo, email, nombre}` (if `requiereRegistro`, call `POST /auth/google/completar-registro`), Google recovery endpoints, `POST /auth/olvide-password` / reset, `POST /auth/logout`, `GET /api/usuarios/me`.
- Spaces: `GET /api/organizaciones` (`[]` = no organization; `miRolGestion`, `miEsTerapeuta`), `POST /api/organizaciones`, members and patients under `/api/organizaciones/{id}/...`.
- Familiar: `GET /api/pacientes` merges team and familiar access; `miPermiso != null` = familiar.
- Invitations: org `POST|GET|DELETE /api/organizaciones/{id}/invitaciones[/{invId}]`, familiar `POST /api/pacientes/{id}/colaboradores`, `GET|DELETE /api/pacientes/{id}/invitaciones[/{invId}]`; resend = repeat POST; accept `POST /api/invitaciones/aceptar {token}` (generic 404 on any failure).
- No aggregate `/me/espacios`: `me` + `organizaciones` + `pacientes` cover every onboarding case.
- Status changes in this feature: foreign custom pictogram on item create/update 400 → 404; uploads >5MB 413, missing part 400, bad content type 415 (were 500); 1–5MB uploads now accepted.

## Next step

Backend closed for frontend work. Push/PR of `chore/backend-close-out` is the user's decision.
