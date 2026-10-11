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
- [x] WU-C: Independent full suite and real-data-copy rehearsal; stop before active database execution (parent verifier).

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

WU-B implementation and independent WU-C verification are complete; evidence follows.

WU-A regression: `LegacyCleanupPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest,MigracionBackfillPostgresIntegrationTest`: 12 tests green. First combined run had one infrastructure connection-refused error before test SQL; unchanged rerun passed all 12. Historical `init.sql` prefix is byte-for-byte identical to HEAD. `git diff --check` clean.

WU-A commit: `fafed00 feat(db): guard physical legacy cleanup migration 017` (326 additions, 2 deletions). No RDD was opened, as explicitly requested.

WU-B RED: `LegacyCleanupPostgresIntegrationTest#freshBootstrapMatchesHistoricalUpgradeWithoutCreatingLegacyColumns` failed with `NoSuchFileException: bootstrap.sql`. The initial equivalence run then detected a bootstrap default mismatch caused by explicit `public, pg_catalog` lookup order; changing the new bootstrap to `pg_catalog, public` preserves the historical builtin UUID default. The equivalence assertion was not weakened. The isolated equivalence rerun passed.

WU-B bounded corrections: directed tests exposed EOF historical fixtures accidentally executing the appended 017. Authorized additional surfaces are `OwnerDeferredTriggerPostgresIntegrationTest`, `CompositeFkPostgresIntegrationTest`, `InvitacionConstraintsPostgresIntegrationTest`, and `PacienteCreationRollbackPostgresIntegrationTest`; each now explicitly stops before 017. The concurrency test instead uses the final bootstrap and creates users without the removed role column. The comprehensive schema snapshot required explicit PostgreSQL internal-char casts. No migration SQL was changed for these fixture fixes.

EOF-loader audit: all executable historical setup paths are now bounded. `CartillaPrincipalUnicaIntegrationTest` only extracts its first historical UPDATE and asserts index declaration text, so it remains unchanged. New bootstrap equivalence is intentionally the sole full-history loader.

Bootstrap materialization used synthetic `caa017-bootstrap-writer`, PostgreSQL 15.19 with data on tmpfs and no active volume. It was stopped after generating DDL; no volume/backup deletion. The migration history before 017 remains byte-for-byte identical to `66697c5` (SHA256 `dff428298e7665597920169c267c83673659050a64236b2a0060a2ad71756587`).

Delivery slicing: WU-A stays below 400 authored lines. WU-B exceeds that advisory budget because its complete 389-line final DDL, equivalence proof, safe fixture boundaries and operator documentation are one coherent install behavior. Keep this bounded slice rather than splitting tests from schema; recommend an explicit size exception if later opening a PR. No PR is created here.

WU-B GREEN: `./mvnw -o test -Dtest=LegacyCleanupPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest,MigracionBackfillPostgresIntegrationTest,MigracionBackfillIntegrationTest,TransferenciaPropiedadConcurrenciaPostgresIntegrationTest,OwnerDeferredTriggerPostgresIntegrationTest,CompositeFkPostgresIntegrationTest,InvitacionConstraintsPostgresIntegrationTest,PacienteCreationRollbackPostgresIntegrationTest`: **37 tests, 0 failures, 0 errors, 0 skipped**, 1:47 min. The initial directed run had 28 errors from obsolete fixtures and the test-helper cast; the bounded correction above resolved them. `./mvnw -o -DskipTests compile`: BUILD SUCCESS. `git diff --check`: clean.

WU-B source rollback boundary: bootstrap.sql, Compose SQL mount, README/operator guidance, final-schema equivalence and bounded historical fixture setup. Reverting these files does not alter any existing persistent schema. No active database was accessed or changed.

Next: report the completed rehearsal and stop. Applying 017 to active `caa_db` requires separate explicit authorization; it has NOT been applied there.

## Independent WU-C receipt

- WU-B commit: `b803271 feat(db): bootstrap new installations without legacy columns` (577 additions, 37 deletions); with WU-A `fafed00`, 942 authored lines versus the 700–1000 forecast.
- `./mvnw -o test` ran once at final verification: **561 tests, 0 failures, 0 errors, 0 skipped**, 68 fresh XML reports; one stale XML report excluded from totals.
- `./mvnw -o -DskipTests package`: **BUILD SUCCESS**. `git diff --check`: clean.
- PostgreSQL **15.19** real-data copy: exact committed 017 applied successfully, then reapplication was a verified no-op. Retained schema and all 13 table counts/fingerprints were identical before/after, excluding exactly the intentionally dropped values.
- Removed exactly the two approved columns, `fk_paciente_terapeuta`, `idx_pacientes_terapeuta`, and the FK's four internal RI triggers. An independent pre-017 evidence copy verified the exact `tgconstraint` linkage of all four triggers.
- Preserved `rol_usuario`, all `cartillas.creador_id` values/FK/index, and every other schema object and relation.
- Backend started against the isolated post-017 copy, skipped existing seed data, and passed **42 HTTP checks: 25 × 200, 2 × 401, 15 × 404**, including expected authorization denials. It did not load the project's source `.env`; Resend health probing was disabled.
- Backend stopped after checks. Both rehearsal clones are retained and stopped. Active `caa_postgres` remains stopped with identical state/mounts; it was neither accessed nor started/modified. This is container-state evidence, not a new active-data query.

### Preserved table counts

| Table | Before = after |
|---|---:|
| usuarios | 42 |
| organizaciones | 26 |
| membresias | 26 |
| pacientes | 66 |
| pacientes_terapeutas | 66 |
| pacientes_familiares | 4 |
| cartillas | 51 |
| categorias | 117 |
| items_cartilla | 358 |
| pictogramas_custom | 6 |
| pictogramas_globales | 68 |
| sesiones | 26 |
| invitaciones | 0 |

### Private recovery evidence and limitations

- Private pre-017 backup: `backups/physical-legacy-rehearsal-20261010T202450Z/pre017.dump`, **82,950 bytes**, SHA256 `aba6863f5406df808da8b913f62e1f10bf3489a2f287fbe3a291551d3f8edc4a`. Archive listing verified; this receipt does **not** claim that this particular logical backup was restore-tested.
- Sanitized verifier receipt: `backups/physical-legacy-rehearsal-20261010T202450Z/sanitized-run-summary.json`. Backup contents remain ignored/private and were not committed.
- Exact 017 SHA256: `24f2ba042f5d0a207420fbf62d09bda99ce85a39a251953e51c0e82182f97351`; historical prefix remained identical.
- Rehearsal setbacks were harness-only: the initial strict schema oracle omitted the approved FK's internal RI triggers, Docker reassigned the clone's random port after restart, and private harness binding/alias issues needed correction. Exact dependency classification and verified port were corrected without source/SQL changes or data failures.
- Arbitrary dynamic SQL and external legacy clients still require operational confirmation. Engram mirror remains pending authoritative host session registration.

**Outcome: PASS, STOPPED BEFORE ACTIVE APPLICATION.** No active migration, RDD, push, prune, backup deletion or volume deletion occurred.
