# Explicit Google identity and account recovery

Google authentication must resolve a verified stable subject (`sub`), never link an existing identity merely because its email matches. Historical accounts have no trustworthy provider provenance and require explicit recovery. No email-derived subject backfill is safe.

## Deployment boundary

Apply the additive MIGRACIÓN 018 block from `init.sql` only after a verified backup and an approved maintenance window. It requires post-017 and rejects partial/unexpected schema. Deploy schema before this application version. `bootstrap.sql` already includes the final schema for new installations. Existing migrations remain historical and must not be replayed against an upgraded database.

**This implementation rehearses only on disposable PostgreSQL 15. The active database must not be migrated as part of this task.**

## Authentication contract

| Identity state | Google login / registration completion |
|---|---|
| Matching persisted subject and exact email | Authenticate that identity |
| Unknown subject and unused email | Existing `requiereRol=true` response; completion creates subject-bound identity |
| Existing email but unbound subject | Generic 401; no JWT, no implicit link; recovery required |
| Different subject, reused subject or changed email | Generic 401; no merge or email rewrite |

`requiereRol` retains its current registration-completion meaning, not role selection. Neither creation nor authentication grants memberships or organizations. Users whose Google email changes require a separately approved resolution; this change deliberately fails closed.

## Explicit recovery protocol

1. Obtain a fresh Google ID token for the identity being recovered.
2. `POST /auth/google/recuperacion/solicitar` with `{ "idToken": "<Google proof>" }`.
   The backend derives the email and subject from verified Google claims. Eligible unbound accounts receive an email; missing/already-bound/conflicting accounts receive the same generic 200 without a token or cookie. Invalid Google proof returns generic 401.
3. Open the email challenge, obtain a fresh Google ID token for the same subject/email, and call `POST /auth/google/recuperacion/completar` with `{ "idToken": "<fresh Google proof>", "token": "<email challenge>", "password": "<new strong password>" }`.
4. A committed recovery returns the existing Google success DTO and an HttpOnly JWT cookie. The previous password, every old tokenVersion JWT and any outstanding ordinary password-reset link are invalidated.

The replacement password follows the existing registration/reset strength policy and must not match the existing password hash. Knowing the old password is not sufficient proof. Invalid, expired, reissued, used, mismatched or conflicting challenges return the same generic 401, without JWT or partial credential changes. Invalid request fields return 400. No automatic account merge or permission changes occur.

The existing forgot-password limiter is reused for challenge issuance by verified email; no general rate-limiting redesign is included. A 429 retains that existing limiter contract.

### Persistence and concurrency

- `google_sub`: nullable unique stable identifier; existing users remain NULL until proved, never inferred from email.
- Challenge fields: SHA-256 hash only, one-hour expiry, verified proposed subject and email. A database check requires the entire quartet or all NULL, and forbids a pending challenge on a bound identity.
- Random challenge: 32 bytes from `SecureRandom`, URL-safe Base64. The raw challenge exists only in the recovery email and in transient process memory.
- First-read pessimistic row locks serialize issuance, completion, ordinary forgot-password and ordinary reset. This narrow reset change prevents stale entity saves from resurrecting the old password, Google binding or token version.
- Completion validates both proofs, replaces the password, increments tokenVersion and clears both challenge types in the same transaction. Database UNIQUE subject conflicts roll back every field.
- Successful ordinary password reset clears pending Google recovery; a new email+Google challenge is then required.
- Google challenge emails are sent only after commit, with escaped display names and a configured frontend URL. Delivery is best-effort, not an outbox guarantee.

### Frontend boundary

The email deep link is `<app.frontend-url>/recuperar-google?token=...`. **No frontend page is implemented in this backend-only objective.** Clients must integrate the two endpoints and handle recovery-required generic authentication failures before advertising a self-service recovery experience. Do not store ID tokens/challenges in logs or tickets.

## Rehearsal and rollback boundary

The PostgreSQL Testcontainers rehearsal starts with representative historical post-017 data, captures all existing table rows and column definitions, applies only 018, and compares retained data/schema exactly. New fields must all remain NULL. It validates nullable uniqueness, complete challenges, guarded replay, historical-upgrade/fresh-bootstrap parity and the current backend with real transactions/HTTP filters.

The separate application tests exercise simultaneous token consumers, same-subject uniqueness races across different rows, and reset/forgot-password interleavings while observing PostgreSQL row-lock waits. They verify invalid old JWTs/password/reset links and zero automatically granted tenant access.

No active database is touched. A later production application requires separate authorization, a fresh restorable backup, a closed write window, exact instance identification and matching preconditions. Stop on differences. The additive fields may remain if application rollback is necessary; never restore email-only Google login after successful recoveries. Do not remove subjects/challenges or reset credential versions as a rollback shortcut.

## Verified delivery status

At source commit `d29ac73`, the independent final suite passed **588 tests with zero failures, errors or skips**, and the final offline package succeeded. PostgreSQL proofs cover migration replay/preservation, historical bootstrap parity, current-backend startup, recovery concurrency and historical readiness. The full recovery evidence is recorded in `odd/tasks/google-identity-recovery.md`.

At the time of the rehearsal: **implementation and isolated rehearsal are complete; migration 018 had NOT been applied to the active database.** A separate approved deployment was still required.

Update 2026-10-15: the active `caa_db` was verified read-only (`information_schema`) to be post-018.

## Remaining risks outside this objective

- Historical email-only Google accounts must recover explicitly; transparent historical login is intentionally rejected.
- Changed Google email fails closed; account/email-transfer policy is not implemented.
- Provider exchange remains verified by Google's library in production and mocked only at the boundary in local tests; no live Google/Resend request is required by these tests.
- An attacker may have created tenant data before recovery. This flow transfers control of the existing identity; it neither rewrites historical data nor invents memberships.
- General anti-abuse, CSRF, session lifecycle, onboarding and frontend integration remain separate objectives.
