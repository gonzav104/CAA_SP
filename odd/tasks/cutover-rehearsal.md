# Cutover rehearsal (disposable PostgreSQL 15)

## Objective and scope
Implement existing migration 016 and prove pre/post cutover, backend startup, full snapshot rollback and reapplication using synthetic representative data in Testcontainers only. Baseline: `467a1b8`.
No architecture redesign, active DB access, `caa_sp_db_data_final`, volume deletion, prune, legacy cleanup, push or real cutover.

## Delivery
Forecast: 500–750 authored changed lines. Strategy: auto-chain / feature-branch-chain (previously approved). Local work-unit boundaries: T1 bootstrap contract; T2 migration with focused proof; T3 complete rehearsal evidence. Parent owns commits; IDs pending. No remote operations.
All tasks use delegated direct (preparation and multiple non-trivial files). Approximately 400 lines per task is advisory, not a reason to omit proof.

## Tasks and acceptance
- [x] T1 Correct contradictory 015a comment and distinguish one-time bootstrap from selected reentrant migrations. Existing readiness remains pre016. Structural readback required. Commit: pending.
- [x] T2 Implement atomic 016 per existing spec, preserving modern unassigned patients and legacy columns. Observe missing-migration RED then real PostgreSQL GREEN. Commit: pending.
- [x] T3 Exercise preflight, migration, postconditions, backend startup/service smoke, exact snapshot restore, and reapplication; focused suite then full suite once. Commit: pending.

## Verification
`./mvnw -o test -Dtest=CutoverRehearsalPostgresIntegrationTest` (RED then GREEN).
`./mvnw -o test -Dtest=CutoverReadinessPostgresIntegrationTest,MigracionBackfillPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest`.
`./mvnw -o test` once at final.
Stop immediately on migration data/constraint/transaction failure; report phase and SQLSTATE without adapting SQL or fixtures.

## Progress and recovery
Task document created before source edits. Engram mirror pending: actual save attempt rejected with `Codex host session resolution could not be confirmed`.
T1 structural readback: corrected 015a comment and documented one-time bootstrap/selected replay; readiness explicitly ends before 016. `git diff --check` clean.
T2 RED: focused test 1 failure, 0 errors, missing `-- MIGRACIÓN 016` assertion. GREEN: focused real PostgreSQL rehearsal 2 tests, 0 failures/errors/skips. Initial sandbox attempt could not reach Docker and executed no migration; approved escalated run passed.
T3 first lifecycle passed: 4 patients pre, 1 late NULL organization; post constraints and assignments valid; selected 016 repeat unchanged; real Spring startup/service writes; container-local logical snapshot restored exact pre schema/data; reapplication and selected replay valid. Strengthened mapping/schema assertions afterward; three-class focused suite: 4 tests, 0 failures/errors/skips (27.657 s). Final full suite executed ONCE: 551 tests, 0 failures/errors/skips (02:02 min). Logs: `/tmp/cutover-red.log`, `/tmp/cutover-green.log`, `/tmp/cutover-focused.log`, `/tmp/cutover-full.log`.
No migration data/constraint/transaction failure observed. No commits yet (parent owned).
Parent spot check: repeated `./mvnw -o test -Dtest=CutoverRehearsalPostgresIntegrationTest`, 2 tests passed with no failures/errors/skips (20.104 s); `/tmp/cutover-parent-spot.log`. Full suite was not repeated.
Delivery boundary: T1-T3 form one coherent local work unit because pre016 extraction requires the new marker and migration proof depends on the rehearsal. Keep tests and runbook with SQL rather than artificial file-type commits. Approximately 440 authored lines; recommend a small size exception for this slice at eventual PR preparation. No PR or remote operation authorized.
Next: local work-unit commit and native review consent. No real cutover authorized. Task outcomes verified; commit closure remains pending. Initial workspace assessment was unassessable due to undeclared untracked files; committed-only assessment will cover the explicit selected paths.

## Change inventory
Tracked diff: 76 additions / 7 deletions. New rehearsal test 155 lines, helper 93, SQL fixture 25, runbook 51; task record additional. Roughly 440 authored lines total, within forecast. Both Markdown documents are ignored by `.gitignore:39` and require explicit force-add to include in commits. No shared-base changes needed.
