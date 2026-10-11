# Disposable cutover rehearsal

The initial bootstrap in `init.sql` is **not idempotent**. Run its pre-016 prefix once on an empty disposable database. Never rerun the whole file to test idempotency. Select migration 016 alone for subsequent executions; it repeats the necessary 013/015 data statements atomically.

This procedure is not authorization for a production cutover. It must not connect to the active database, mount `caa_sp_db_data_final`, delete volumes, prune Docker, or remove legacy columns.

## Run

Requirements: Java 21, Maven wrapper dependencies cached for `-o`, Docker access and `postgres:15-alpine` available.

```sh
./mvnw -o test -Dtest=CutoverRehearsalPostgresIntegrationTest
./mvnw -o test -Dtest=CutoverReadinessPostgresIntegrationTest,MigracionBackfillPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest
./mvnw -o test
```

The test creates an isolated PostgreSQL 15 Testcontainer with dynamically assigned connection details. No production dump, named volume or application-default DSN is used. All backup/restore commands run inside that container. Standard Testcontainers teardown removes only its owned ephemeral containers.

## What 016 does

One explicit transaction repeats the data statements from 013 (missing organizations and OWNER memberships) and 015 (only patients whose organization was NULL at entry). The latter scope preserves intentionally unassigned modern patients and respects `es_terapeuta`. A guard raises SQLSTATE `23502` if any patient remains without an organization. Then it sets `pacientes.organizacion_id NOT NULL` and drops NOT NULL from `pacientes.terapeuta_id` and `usuarios.rol`. Existing columns and authorization architecture remain unchanged.

The 013 workspace-creation semantics are preserved verbatim: a legacy creator with patients and no own organization can receive a workspace even if already a member of another organization. The patient backfill never relocates modern patients.

## Evidence and rollback boundary

| Phase | Check |
|---|---|
| Pre-cutover | Bootstrap through 015a once; synthetic late legacy row plus modern assigned, manager-unassigned and deliberately unassigned patients; idle therapist, family link and clinical history. Exactly one NULL organization is expected and reported, not falsely classified ready. |
| Snapshot | Container-local `pg_dump --format=custom` of the disposable database before 016. |
| Cutover | Execute only 016, check nullability, OWNER integrity, migrated assignment, unchanged modern organizations and unassigned patients, preserved unrelated data. |
| Idempotence | Execute only 016 again and compare every public-table row plus columns, constraints, indexes, triggers, OWNER function and enum definitions. |
| Backend | Start actual Spring backend after migration with explicit container datasource and random HTTP port; use real organization/patient services to write post-cutover data. External email health check disabled. |
| Rollback | Close backend, restore the pre016 dump with `pg_restore --clean --if-exists --exit-on-error --single-transaction`; compare pre016 public schema/data snapshot exactly. This removes post-cutover smoke data too, unlike a constraint-only rollback. |
| Reapply | Execute 016 on restored state, repeat semantic postconditions and exact idempotent replay comparison. Generated UUIDs/timestamps can differ between independent first applications. |

This is a **disposable database snapshot rollback**, not the design's full expand down-migration and not a live-data rollback plan. Production rollback would need an approved backup, outage/write coordination and explicit treatment of writes after cutover.

On a migration data/constraint/transaction failure, stop at the first failure and report phase, SQLSTATE and error before changing SQL or fixtures. Expected constraint-negative assertions are separate probes, not failed migrations. No automatic repair or migration retry is performed.

## Limits

Synthetic fixtures are reproducible but are not a production-like anonymized dump. Runtime startup/service smoke does not replace a full deployed HTTP workflow. Production volume, active database, real cutover and legacy cleanup remain outside this procedure.

## Observed verification (2026-10-09)

- RED: missing migration marker, 1 assertion failure (before implementation).
- Focused rehearsal: 2 tests, no failures/errors/skips.
- Readiness + backfill + rehearsal: 4 tests, no failures/errors/skips.
- Final full suite (one run): **551 tests, 0 failures, 0 errors, 0 skipped**.
- Initial sandbox Docker access failed before executing SQL; explicitly approved Docker execution passed. No migration failure required SQL adaptation.
