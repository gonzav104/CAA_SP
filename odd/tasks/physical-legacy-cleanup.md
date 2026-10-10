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
- [ ] WU-B: Final-state bootstrap, compose mount, schema-equivalence test and operator documentation.
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

WU-B and final independent verification remain pending.

WU-A regression: `LegacyCleanupPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest,MigracionBackfillPostgresIntegrationTest`: 12 tests green. First combined run had one infrastructure connection-refused error before test SQL; unchanged rerun passed all 12. Historical `init.sql` prefix is byte-for-byte identical to HEAD. `git diff --check` clean.
