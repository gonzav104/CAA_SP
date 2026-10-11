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

## Progress

Explicit recovery endpoints and concurrency evidence are recorded in the next work unit. Do not deploy the partial feature.
