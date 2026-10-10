# Physical legacy cleanup: migration 017

Migration 017 removes only `public.usuarios.rol` and `public.pacientes.terapeuta_id` from a verified post-cutover database. This change is rehearsed on isolated PostgreSQL 15; execution against active `caa_db` requires a separate explicit authorization.

## What changes

| Object | Treatment |
|---|---|
| `usuarios.rol` | Drop the nullable legacy column; no application consumer remains. |
| `pacientes.terapeuta_id` | Drop the nullable legacy column; assignments live in `pacientes_terapeutas`. |
| `fk_paciente_terapeuta` | Verify its exact definition, then drop. |
| `idx_pacientes_terapeuta` | Verify its exact nonunique, unconditional btree definition, then drop. |
| `cartillas.creador_id`, FK and index | Preserve as audit metadata. |
| `rol_usuario` enum | Preserve; removing standalone types is outside this migration. |
| Multi-tenant tables, columns, constraints and data | Preserve without DML or backfills. |

No API contract or authorization behavior changes in this objective. Auth/Security hardening, `requiereRol`, `/me/espacios` and frontend remain out of scope.

## Installation versus upgrade

- **New empty database:** run `bootstrap.sql` once. Compose mounts it read-only at the existing entrypoint filename. It creates the final post-017 schema without ever creating the two legacy columns. The persistent volume mapping is unchanged. Bootstrap is not idempotent.
- **Existing legacy database:** retain `init.sql` as historical upgrade evidence. Apply only the stage-appropriate migration blocks in a separately validated procedure.
- **Existing post-016 database:** extract only 017. Do not execute all of `init.sql`, `bootstrap.sql`, or replay 011–016. After physical cleanup, earlier backfills still reference deleted columns and are intentionally not applicable.

The equivalence test compares final columns/defaults/types/nullability, constraints, indexes, triggers, functions, views and enums, ignoring physical attribute ordering and dropped-column catalog slots. A PostgreSQL application concurrency test boots from the final bootstrap.

## Inventory before a separately authorized execution

Run catalog inspection on the isolated copy first. These queries are read-only and should be retained with the rehearsal evidence:

```sql
SELECT table_name, column_name, udt_schema, udt_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (table_name, column_name) IN (('usuarios', 'rol'), ('pacientes', 'terapeuta_id'));

SELECT d.deptype, pg_describe_object(d.classid, d.objid, d.objsubid) AS dependent
FROM pg_depend d
JOIN pg_attribute a ON a.attrelid = d.refobjid AND a.attnum = d.refobjsubid
WHERE d.refclassid = 'pg_class'::regclass AND NOT a.attisdropped
  AND ((a.attrelid = 'public.usuarios'::regclass AND a.attname = 'rol')
    OR (a.attrelid = 'public.pacientes'::regclass AND a.attname = 'terapeuta_id'))
ORDER BY 1, 2;

SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid IN ('public.usuarios'::regclass, 'public.pacientes'::regclass);
SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('usuarios', 'pacientes');
```

Also inspect explicit references in user-defined functions, triggers and views. PostgreSQL does not track arbitrary dynamically constructed SQL or external clients: search those consumers separately. Never interpret a clean dependency catalog as proof that external legacy writers are absent.

## Disposable rehearsal

1. Use a verified post-cutover backup/copy on a separately named PostgreSQL 15 instance. Never mount the active volume writable; do not delete volumes or backups.
2. Record the instance identity, PostgreSQL version, pre-017 catalog inventory, counts and fingerprints of every public table. Fingerprints for comparison exclude exactly the two columns intentionally removed. Keep a complete pre-017 backup including those values.
3. Confirm a post-016 schema, zero patients without an organization, one OWNER per organization and the expected multi-tenant FKs. Preserve all rows, including deliberately unassigned patients.
4. Extract the migration without altering it:
   ```sh
   sed -n '/^-- MIGRACIÓN 017/,$p' init.sql > /tmp/caa-migration-017.sql
   ```
   Execute it using `psql -X -v ON_ERROR_STOP=1` against the explicitly identified disposable instance. Do not use an ambient connection or active Compose service.
5. On **any failure**, stop. If retaining the same session, issue `ROLLBACK` before read-only diagnostics. Do not adapt SQL to force real data through a guard.
6. Compare all retained-row fingerprints and counts; validate schema, FKs, OWNER and assignment/family relations. Confirm `cartillas.creador_id` data, FK and index remain. Check that exactly the two legacy columns and approved dependents disappeared.
7. Reapply only 017 on the disposable post-017 database: it must be a verified no-op. Start the unchanged backend against that copy, run targeted PostgreSQL tests and representative read-only checks. Keep active data untouched.
8. Report evidence and **stop before active execution**.

## Transaction and rollback guarantees

017 runs in one transaction with a five-second lock timeout. It takes exclusive locks on the two altered tables and share locks on organizational/clinical relation tables to stabilize checks. Maintenance-window coordination remains necessary; lock timeout is a failure, not permission to retry automatically.

Guards reject a pre-cutover schema, invalid constraints, missing organization/OWNER invariant, unexpected column type/nullability/default, partial cleanup, changed known dependencies and unapproved automatically dropped local objects. PostgreSQL `RESTRICT` rejects external dependencies; there is no `CASCADE`. Tests prove that a dependency on the second column aborts the transaction and restores the first column and every retained value after rollback.

A successful rerun requires both columns absent, no leftover approved legacy objects, and passing post-cutover guards. It is not a repair mechanism for partial cleanup.

**After COMMIT, reverting Java or SQL source does not recover deleted values.** Restore a verified pre-017 backup in a controlled recovery procedure or design and authorize a separate forward recovery. No automatic down-migration or production rollback is provided.

## Verification ownership

Directed tests cover success, complete retained-data equality, no-op rerun, pre-016 and partial-state rejection, unknown local index/check, changed index definition, explicit function consumer, external-view rollback and fresh-bootstrap equivalence. Historical migration extraction stops at its own boundary, so 016 tests never accidentally run 017.

Independent verification completed: the once-only full suite passed 561 tests, and the PostgreSQL 15.19 real-data-copy rehearsal preserved all retained fingerprints and passed 42 HTTP checks. Operational evidence and limitations are recorded in `odd/tasks/physical-legacy-cleanup.md`. Active `caa_db` was not accessed or modified; applying 017 there still requires separate authorization.
