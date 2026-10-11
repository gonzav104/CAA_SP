# Google identity binding and verified recovery

Prevent email-only Google authentication into pre-created local identities. Persist verified Google subjects and require email possession plus fresh Google proof to recover historical unbound accounts. Active databases are outside this task.

## Scope and states

| State | Required behavior |
|---|---|
| No subject or email account | Existing registration completion creates a bound identity only |
| Exact subject and email | Authenticate the explicitly bound account |
| Existing email without subject | Refuse login/completion; require verified recovery |
| Conflicting subject/email | Refuse without merging identities |
| Successful recovery | Replace password, increment token version, clear all recovery challenges |

No organization, membership, role selection, frontend, CSRF, general rate-limit or session redesign. Migration 018 is additive with no inferred backfill; never apply it to the active database. No RDD or push.

## Delivery and checks

Delivery: auto-chain / feature-branch-chain, local commits only. Forecast 900–1300 authored lines; cohesive security and PostgreSQL proof slices may need a size exception (no code compression). All three work units use delegated direct execution because they span service, persistence, schema and concurrency tests. Test-first applies to deterministic regressions; schema rehearsal uses disposable PostgreSQL 15 only.

- [x] WU1 — Explicit unique Google subject; refuse email-only authentication; additive 018 and bootstrap; unit/schema regression proof.
- [x] WU2 — Email + Google recovery, locked atomic credential mutation, password/JWT invalidation and concurrency proof.
- [ ] WU3 — Independent final suite/package/startup, runbook and rehearsal evidence; stop before active migration.

Targeted commands:
- `./mvnw -o test -Dtest=GoogleTokenVerifierTest,AuthServiceTest,EmailServiceTest,RecuperoPasswordIntegrationTest,SecurityIntegrationTest`
- `./mvnw -o test -Dtest=GoogleIdentityMigrationPostgresIntegrationTest,GoogleRecoveryPostgresIntegrationTest,LegacyCleanupPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest`
- Final full suite once, owned by independent verifier.

## Progress

Inventory confirmed: Google verifier discards subject; both Google entry points resolve by email; users have no provider provenance. Existing password reset mutations load and save whole entities without locks, so they must share the recovery row-lock boundary to avoid resurrecting replaced credentials.

Engram mirror pending: authoritative runtime session identity is unavailable; no unattributed memory writes attempted.

### WU1 evidence

- RED: precreated local-email Google login regression, 1 failure on original service.
- GREEN: targeted verifier/auth/email/reset/security suite, 61 tests.
- PostgreSQL GREEN: migration 018, legacy cleanup and cutover rehearsal, 17 tests; additive values/schema preservation, NULL backfill, UNIQUE/check constraints, guarded rerun and bootstrap equivalence passed.
- Historical 016 smoke initially failed because latest entity mapping needs 018. Approved fixture-only adaptation advances its disposable DB through 017/018 after all historical assertions, before current-backend smoke; exact backup restoration and 016 replay remain verified.
- Runtime boundary: latest Spring context starts and tenant service smoke passes on disposable post-018 PostgreSQL. Active database untouched.
- Rollback boundary: revert this auth/schema-code commit only before deployment; no active migration occurred. Existing historical SQL remains byte-identical.
- WU1 cohesive slice exceeds ~400 authored lines because it includes guarded DDL and PostgreSQL evidence; recommend size exception for that slice rather than separating untested schema from behavior. No PR opened.

### WU2 evidence

- WU1 commit: `db1b548` (452 insertions, 65 deletions).
- RED: public recovery endpoint request without required proof expected 400, got 401 before endpoint implementation.
- GREEN: `GoogleTokenVerifierTest,AuthServiceTest,EmailServiceTest,RecuperoPasswordIntegrationTest,SecurityIntegrationTest`: 64 tests, zero failures/errors/skips.
- GREEN: `GoogleIdentityMigrationPostgresIntegrationTest,GoogleRecoveryPostgresIntegrationTest,LegacyCleanupPostgresIntegrationTest,CutoverRehearsalPostgresIntegrationTest`: 32 tests, zero failures/errors/skips; includes 15 real Spring/PostgreSQL recovery tests.
- Proof includes attacker precreation refusals, first/repeated Google subject login, subject/email conflicts, two independent recovery proofs, expired/used/reissued tokens, old-password rejection, old-JWT rejection by HTTP filter, invalidated reset links, zero tenant grants, concurrent double-consumption, UNIQUE-subject conflict rollback, and actual PostgreSQL lock waits around competing forgot/reset/recovery mutations.
- Expected concurrent UNIQUE-subject violation is part of the passing rollback proof, not an unexpected migration failure.
- `./mvnw -o -DskipTests package`: passed (compile/package, tests intentionally skipped). Final independent full suite/package belongs to WU3; not run by writer.
- Reused the existing forgot-password email limiter for Google recovery issuance; no general rate-limit work.
- WU2 rollback boundary: endpoints/service locking/email implementation and their tests; do not redeploy unsafe email-only auth or discard established bindings after actual deployment.
- WU2 is a cohesive security slice exceeding ~400 lines because all transaction/concurrency proofs belong with its behavior; recommend size exception. No code compression, PR, RDD or push.

Migration 018 SHA-256 (from its marker through EOF): `410d556bb7881d16ad2f2efa5c481ee54b6e7a07907f854c52bb08e591018c05`.

Next step: independent verifier runs final full suite once and final package/read-only audit. Active caa_db remains post-017; stop before any active migration.

### WU3 historical fixture correction

- WU2 commit: `5eae47f` (690 insertions, 15 deletions).
- Independent final-suite RED: 588 tests, zero failures, one error. `CutoverReadinessPostgresIntegrationTest` booted the latest `Usuario` mapping while intentionally retaining the pre-016 schema, causing SQLSTATE 42703 for `google_recovery_email`.
- Bounded correction: seed organizations and OWNER memberships together via JDBC instead of current application services; remove the now-unneeded Spring context. Preserve the historical pre-016 schema and every original readiness invariant. Explicit counts verify four users, two organizations, three memberships, three patients and two clinical assignments; no Google columns exist in the historical fixture.
- Focused GREEN: `./mvnw -o test -Dtest=CutoverReadinessPostgresIntegrationTest` — one test, zero failures/errors/skips on disposable PostgreSQL 15. `git diff --check` passed.
- No production or migration SQL changed; no active database access. Final independent suite verification remains pending with the parent verifier.
