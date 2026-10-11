# Post-cutover legacy code cleanup

Remove obsolete Java compatibility paths now that `caa_db` is officially post-cutover, while preserving the physical legacy columns and every still-valid audit or compatibility boundary.

## Objective

- Remove the global `Usuario.rol` authorization concept from production Java and public user responses.
- Stop writing or reading `Paciente.terapeuta_id` from the active entity/service model.
- Keep authorization centered on `Organizacion`, `Membresia`, `PacienteTerapeuta`, `PacienteFamiliar`, and `AccesoService`.

## Scope and exclusions

- Authorized: backend Java code, related tests, and current-state README documentation listed by the cleanup task.
- Excluded: physical database cleanup, migrations 011–016, production data, frontend changes, Auth/Security hardening, Docker cleanup, push, and RDD.
- Preserve `cartillas.creador_id` as valid creator/audit metadata.
- Preserve raw migration SQL and migration/rehearsal evidence.

## Inventory

| Classification | References | Reason |
|---|---|---|
| REMOVE | `RolUsuario`, `Usuario.rol`, `UsuarioResponseDTO.rol`, service response reads, role fixtures/assertions | Global role no longer governs authorization after cutover. |
| REMOVE | `Paciente.terapeuta`, creation dual-write, production queries/finders based on `terapeuta_id`, compatibility-only DTO constructors | Clinical authorization and assignment now use `PacienteTerapeuta` and `AccesoService`. |
| KEEP | `cartillas.creador_id` and creator mappings | Still valid creator/audit metadata. |
| KEEP | JWT missing-role claim coverage and constant `ROLE_USUARIO` authentication authority | Tests current token/authentication behavior, not `RolUsuario`. |
| KEEP | `GoogleAuthResponseDTO.requiereRol` | Separate response contract explicitly outside this cleanup. |
| KEEP | `AccesoService` compatibility facades and `emailTerapeuta` names | Still-valid active API/internal naming; removal is not inferred. |
| KEEP | Migration SQL, readiness/rehearsal tests, and legacy schema evidence | Historical/operational proof must remain reproducible. |
| DEFER | Physical removal of `usuarios.rol` and `pacientes.terapeuta_id` | Requires a separate authorized database cleanup. |

## Tasks

- [x] **WU-A — Remove the global user role from active code and API responses.**
  - Route: delegated direct; multi-file preparation and writer triggers apply.
  - Acceptance: no production use of `RolUsuario` or `Usuario.rol`; registration and `/me` responses do not expose `rol`; preserved JWT/authentication compatibility checks remain valid.
  - Checks: `./mvnw -o test -Dtest=UsuarioServiceTest,AuthServiceTest,JwtServiceTest,SecurityIntegrationTest`; `git diff --check`; bounded global role-reference search.
  - Test-first exception: this removes dead persistence/response compatibility and has no meaningful behavioral RED; compile/test failures during removal are not recorded as RED.
  - Evidence: production and test searches contain no `RolUsuario`, `getRol`, `setRol`, or user `.rol(...)` references; 42 focused tests passed. The first sandboxed run was unavailable because Mockito could not self-attach, then the identical command passed outside the sandbox.
- [x] **WU-B — Remove the patient therapist dual-write from active code.**
  - Route: delegated direct; multi-file preparation and writer triggers apply.
  - Acceptance: `Paciente` no longer maps `terapeuta_id`; creation writes organization and `PacienteTerapeuta` only; historical H2 backfill proof seeds legacy state explicitly; current authorization remains unchanged.
  - Checks: `./mvnw -o test -Dtest=PacienteServiceImplTest,MigracionBackfillIntegrationTest,MigracionBackfillPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest,OrganizacionPacienteControllerIntegrationTest`; `./mvnw -o compile`; `git diff --check`; bounded global therapist-reference search.
  - Test-first exception: removing a post-cutover dual-write has no meaningful deterministic RED without reintroducing legacy behavior.
  - Evidence: 41 focused patient/backfill/PostgreSQL rehearsal tests passed; the adjusted post-cutover `PacienteFamiliar` persistence fixture passed independently; `./mvnw -o compile`, global production searches, and `git diff --check` passed. Historical H2 backfill proof now creates and seeds `terapeuta_id` explicitly through JDBC.
- [x] **WU-C — Preserve the pre-cutover readiness fixture after production dual-write removal.**
  - Route: delegated direct follow-up; the source regression is isolated to one historical PostgreSQL fixture.
  - Observed RED: the outside-sandbox full suite ran 551 tests and reported two errors. `CutoverReadinessPostgresIntegrationTest` failed because its schema stops at 015a, where `pacientes.terapeuta_id` is still `NOT NULL`, but the cleaned production registration path no longer writes it. The other error was an independent Testcontainers connection refusal.
  - Acceptance: keep the readiness test at the real pre-016 schema, preserve its invariants, and do not restore production dual-write or change migration SQL.
  - Implementation: organizations and memberships still use the real service; patient and `PacienteTerapeuta` fixture rows are inserted explicitly through JDBC, including the required legacy therapist identifier.
  - Checks: `./mvnw -o test -Dtest=CutoverReadinessPostgresIntegrationTest`; `git diff --check`; bounded global production legacy-reference search.
  - Evidence: the focused PostgreSQL/Testcontainers readiness test passed (1 test, 0 failures, 0 errors).

## Delivery

- Forecast: 250–350 authored changed lines.
- Actual before WU-C: 614 authored changed lines across the first two work units; WU-C adds only the focused regression fixture and its evidence.
- Strategy: retain the cohesive work-unit commits and recommend a feature-branch chain because the actual total exceeds 400 lines. No PR was created.
- Commit boundaries:
  1. `refactor(auth): remove legacy global user role`
  2. `refactor(patients): stop legacy therapist dual-write`
  3. `test(multitenant): preserve pre-cutover readiness fixture`

## Recovery state

- Engram mirror topic: `odd/post-cutover-legacy-cleanup/tasks`.
- Mirror status: pending; no registered Engram session identity is available to this worker.
- Final full suite: `./mvnw -o test` passed on `f609401` with 551 tests, 0 failures, 0 errors, and 0 skipped.
- Final targeted PostgreSQL verification: readiness and deferred OWNER-trigger tests passed together (8 tests, 0 failures, 0 errors, and 0 skipped).
- Startup verification: the current executable JAR started successfully with read-only connections against a PostgreSQL 15.19 cold copy of the stopped post-cutover data volume; the active container and volume were not started or modified.
- Final repository checks: production legacy-reference searches returned zero matches, `git diff --check` passed, and the worktree was clean before this verification receipt.
- Next step: no implementation work remains in this cleanup; physical legacy-column removal is a separate, explicitly deferred objective.
