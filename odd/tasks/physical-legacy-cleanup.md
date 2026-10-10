# Physical legacy cleanup (017)

Remove only `usuarios.rol` and `pacientes.terapeuta_id` after the completed code cleanup. Active database execution is NOT authorized in this objective.

## Inventory and boundaries

- Remove nullable `usuarios.rol` and nullable `pacientes.terapeuta_id`.
- Remove only the verified `fk_paciente_terapeuta` FK and `idx_pacientes_terapeuta` index attached to the latter.
- Preserve `rol_usuario`, `cartillas.creador_id`, every multi-tenant object and every retained data value.
- Reject unexpected dependencies, partial cleanup and pre-cutover schemas rather than guessing.
- Keep historical migrations 001–016 unchanged; new installations use a separate final-state bootstrap.
- No active database access, production writes, migration replay, cleanup outside these columns, Auth/Security changes, frontend, RDD, push, prune, volume or backup deletion.

## Work units

- [x] WU-A: Atomic migration 017, rejection/rollback/data-preservation tests, historical extractor boundaries.
- [x] WU-B: Final-state bootstrap, compose mount, schema-equivalence test and operator documentation.
- [ ] WU-C: Independent full suite and real-data-copy rehearsal; stop before active database execution (parent verifier).

Routes: A/B delegated writer (multi-file SQL and tests); C delegated verifier (isolated PostgreSQL and runtime checks). Meaningful RED → GREEN tests run on PostgreSQL 15/Testcontainers. No synthetic RED is required for passive documentation.

## Acceptance and checks

- Only the two obsolete columns and verified dependent objects disappear.
- No DML, no CASCADE; transaction failure restores schema and data.
- Verified rerun is a no-op; partial cleanup fails.
- Unknown local indexes/checks and external dependencies prevent cleanup.
- Full retained-row equality and final-schema equivalence are tested.
- Existing migration tests remain valid and migration 011–016 bytes are preserved.
- Directed PostgreSQL tests, `git diff --check`, compile, then one final full suite and backend startup against an isolated post-017 copy.

## Delivery and recovery

Forecast: 700–1000 authored lines, mostly final DDL and tests. Cached delivery strategy: auto-chain / feature-branch-chain. Two coherent work-unit commits; no PR or remote action. The 400-line heuristic is not a reason to compress DDL or weaken tests. Source rollback does not restore dropped values: use a verified backup or separately designed forward recovery.

Engram mirror pending: no authoritative runtime session identity is available. No session is invented.

## Evidence

WU-A RED: `./mvnw -o test -Dtest=LegacyCleanupPostgresIntegrationTest` on isolated PostgreSQL 15: 8 assertion failures because migration 017 did not exist. Initial sandbox Docker access failed; rerun outside sandbox supplied the meaningful RED.

WU-A GREEN: same command, 8 tests, zero failures/errors/skips. Tests cover complete retained-data equality, rerun, pre-016/partial state, unexpected index/check, changed known index, string-bodied function and view-triggered transactional rollback. Added full public function/view snapshot coverage for rejection assertions. Directed historical regression is recorded with WU-B.

WU-B implementation completed below; final independent verification remains pending.

WU-A regression: `LegacyCleanupPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest,MigracionBackfillPostgresIntegrationTest`: 12 tests green. First combined run had one infrastructure connection-refused error before test SQL; unchanged rerun passed all 12. Historical `init.sql` prefix is byte-for-byte identical to HEAD. `git diff --check` clean.

WU-A commit: `fafed00 feat(db): guard physical legacy cleanup migration 017` (326 additions, 2 deletions). No RDD was opened, as explicitly requested.

WU-B RED: `LegacyCleanupPostgresIntegrationTest#freshBootstrapMatchesHistoricalUpgradeWithoutCreatingLegacyColumns` failed with `NoSuchFileException: bootstrap.sql`. The initial equivalence run then detected a bootstrap default mismatch caused by explicit `public, pg_catalog` lookup order; changing the new bootstrap to `pg_catalog, public` preserves the historical builtin UUID default. The equivalence assertion was not weakened. The isolated equivalence rerun passed.

WU-B bounded corrections: directed tests exposed EOF historical fixtures accidentally executing the appended 017. Authorized additional surfaces are `OwnerDeferredTriggerPostgresIntegrationTest`, `CompositeFkPostgresIntegrationTest`, `InvitacionConstraintsPostgresIntegrationTest`, and `PacienteCreationRollbackPostgresIntegrationTest`; each now explicitly stops before 017. The concurrency test instead uses the final bootstrap and creates users without the removed role column. The comprehensive schema snapshot required explicit PostgreSQL internal-char casts. No migration SQL was changed for these fixture fixes.

EOF-loader audit: all executable historical setup paths are now bounded. `CartillaPrincipalUnicaIntegrationTest` only extracts its first historical UPDATE and asserts index declaration text, so it remains unchanged. New bootstrap equivalence is intentionally the sole full-history loader.

Bootstrap materialization used synthetic `caa017-bootstrap-writer`, PostgreSQL 15.19 with data on tmpfs and no active volume. It was stopped after generating DDL; no volume/backup deletion. The migration history before 017 remains byte-for-byte identical to `66697c5` (SHA256 `dff428298e7665597920169c267c83673659050a64236b2a0060a2ad71756587`).

Delivery slicing: WU-A stays below 400 authored lines. WU-B exceeds that advisory budget because its complete 389-line final DDL, equivalence proof, safe fixture boundaries and operator documentation are one coherent install behavior. Keep this bounded slice rather than splitting tests from schema; recommend an explicit size exception if later opening a PR. No PR is created here.

WU-B GREEN: `./mvnw -o test -Dtest=LegacyCleanupPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest,MigracionBackfillPostgresIntegrationTest,MigracionBackfillIntegrationTest,TransferenciaPropiedadConcurrenciaPostgresIntegrationTest,OwnerDeferredTriggerPostgresIntegrationTest,CompositeFkPostgresIntegrationTest,InvitacionConstraintsPostgresIntegrationTest,PacienteCreationRollbackPostgresIntegrationTest`: **37 tests, 0 failures, 0 errors, 0 skipped**, 1:47 min. The initial directed run had 28 errors from obsolete fixtures and the test-helper cast; the bounded correction above resolved them. `./mvnw -o -DskipTests compile`: BUILD SUCCESS. `git diff --check`: clean.

WU-B source rollback boundary: bootstrap.sql, Compose SQL mount, README/operator guidance, final-schema equivalence and bounded historical fixture setup. Reverting these files does not alter any existing persistent schema. No active database was accessed or changed.

Next: WU-C independent full suite once, restore/rehearse 017 against a real-data post-cutover copy, start backend against that isolated post-017 schema, and report before authorizing active execution. No claim of active 017 application is made.
