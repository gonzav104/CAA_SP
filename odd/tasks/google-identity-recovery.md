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
- [x] WU3 — Independent final suite/package/startup, runbook and rehearsal evidence; stop before active migration.

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

Next step: stop before applying 018 to the active database. Implementation and isolated rehearsal are complete; active application requires separate authorization.

### WU3 historical fixture correction

- WU2 commit: `5eae47f` (690 insertions, 15 deletions).
- Independent final-suite RED: 588 tests, zero failures, one error. `CutoverReadinessPostgresIntegrationTest` booted the latest `Usuario` mapping while intentionally retaining the pre-016 schema, causing SQLSTATE 42703 for `google_recovery_email`.
- Bounded correction: seed organizations and OWNER memberships together via JDBC instead of current application services; remove the now-unneeded Spring context. Preserve the historical pre-016 schema and every original readiness invariant. Explicit counts verify four users, two organizations, three memberships, three patients and two clinical assignments; no Google columns exist in the historical fixture.
- Focused GREEN: `./mvnw -o test -Dtest=CutoverReadinessPostgresIntegrationTest` — one test, zero failures/errors/skips on disposable PostgreSQL 15. `git diff --check` passed.
- No production or migration SQL changed; no active database access. Final independent verification is recorded below.


### WU3 final independent verification receipt

Verified source boundary: `d29ac73`, including `db1b548` (stable Google binding and additive schema), `5eae47f` (atomic dual-proof recovery), and `d29ac73` (historical readiness fixture isolation).

- Completed final command: `./mvnw -o test` — **588 tests, zero failures, zero errors, zero skipped**. Evidence was reconciled from 70 fresh XML reports matching Surefire launcher `surefirebooter20261012191933722_3.jar`; the stale removed `UsuarioRepositoryTest` report was excluded.
- Final `./mvnw -o -DskipTests package` — **PASS**. This packages the verified source without repeating the test suite.
- An earlier resumed attempt lost its process/log and contained partial Docker errors. It was not counted as a completed run or a PASS. One fresh final run completed successfully after resume.
- Included PostgreSQL proofs: Google recovery 15 tests; additive migration 018 four tests; legacy cleanup/bootstrap parity ten tests; historical cutover/restore three tests; historical readiness one test. The current backend starts as a real Spring application against disposable post-018 PostgreSQL during rehearsal.
- The first 40,056 bytes of historical `init.sql` remain identical. Migration 018 SHA-256 remains `410d556bb7881d16ad2f2efa5c481ee54b6e7a07907f854c52bb08e591018c05`.
- Diagnostic logs: `/tmp/google-identity-independent-resumed-final-full.log` and `/tmp/google-identity-independent-resumed-package.log`. The durable results above do not depend on retaining temporary logs.
- At the time: no active database was accessed or migrated; **018 is NOT applied to caa_db**. No RDD, push or production deployment occurred.
- Update 2026-10-15: the active `caa_db` was verified read-only (`information_schema`) to be post-018.
- Engram mirror remains pending because the runtime has no authoritative registered session identity.

All implementation work units and independent verification are complete. Remaining deployment and integration boundaries are documented in `docs/google-identity-recovery.md`; no frontend recovery screen, live-provider test or Google email-transfer policy is claimed.

### WU-P2 — defer ordinary password recovery email until commit

- Scope: reuse the existing `afterCommit` helper for the ordinary forgot-password email only. No token logic, Google subject logic, migration, schema or active database changes.
- Route: delegated direct; transaction-bound notification behavior requires real PostgreSQL evidence and a separate fix commit.
- [x] Prove RED/GREEN for no delivery before commit, committed hash visibility from an independent connection, exactly one delivery after commit, and zero delivery on forced rollback.
- [x] Run focused auth/reset/email/PostgreSQL tests, then independent final suite; commit this bounded fix separately only after final verification.
- Allowed production change: `afterCommit(() -> emailService.enviarRecuperacionPassword(usuario, token))`.
- Engram mirror remains pending unavailable authoritative runtime identity.


WU-P2 observed evidence:
- RED: the two new PostgreSQL transaction tests failed before the production change (two failures, zero errors). The email callback observed no committed hash through its independent physical connection, and the rollback path had already invoked email.
- GREEN: `./mvnw -o test -Dtest=GoogleRecoveryPostgresIntegrationTest,AuthServiceTest,RecuperoPasswordIntegrationTest,EmailServiceTest` — 60 tests, zero failures/errors/skips. The PostgreSQL callback uses a separate `DriverManager` connection and compares the committed stored hash to the actual emitted raw token; no sleeps or shared connection stand in for visibility proof.
- The affected H2 success test now explicitly commits its enclosing test transaction before checking email, and deletes its committed fixture afterward. Other H2 cases are unchanged.
- Production diff is exactly the requested `afterCommit` wrapper; `init.sql` and `bootstrap.sql` are unchanged. `git diff --check` passed.
- Temporary diagnostics: `/tmp/google-p2-red.log`, `/tmp/google-p2-targeted.log`. The writer did not run the full suite; final independent verification and delivery are recorded below.


WU-P2 final independent receipt:
- One completed final full suite passed: **590 tests, zero failures, zero errors, zero skipped**. Evidence reconciled from 70 fresh XML reports matching Surefire launcher `surefirebooter-20261012195805921_3.jar`; one stale report was excluded.
- Diagnostic log: `/tmp/google-identity-p2-independent-full.log`. The results above are retained as durable evidence.
- Independent scope check confirmed precisely four authorized files and one production line; migration and bootstrap SQL remain unchanged.
- Delivered as a separate `fix(auth): send password recovery email after commit` commit containing the production wrapper, PostgreSQL proofs, bounded H2 fixture adaptation, and this receipt. No repeated tests, active database access, RDD or push.
