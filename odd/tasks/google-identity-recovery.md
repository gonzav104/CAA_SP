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
- [ ] WU2 — Email + Google recovery, locked atomic credential mutation, password/JWT invalidation and concurrency proof.
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
