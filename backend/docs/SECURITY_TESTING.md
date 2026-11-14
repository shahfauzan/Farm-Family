# Security verification status

The `supabase/tests/schema_contract.test.sql` file is a pgTAP schema contract test. It checks core tables, sync/soft-delete columns, RLS flags, and the absence of direct authenticated update/delete policies on fields and write policies on audit logs.

`supabase/tests/embedded_migrations.ts` executes all four migrations in PGlite 0.5.16 with mocked Supabase `auth`/`storage` schemas, then exercises family A/B RLS isolation, protected inserts/updates/deletes, cross-family links, atomic version update/stale conflict, invite accept/replay/invalid/revoked/expired cases, role escalation, owner checks, soft delete/restore, import authorization and rollback, the weather quota, and harvest unit aggregation. Run it with `deno run --allow-read supabase/tests/embedded_migrations.ts`.

The weather request contract validators have unit tests in `supabase/functions/farm-weather/validation.test.ts`; run them with `deno test supabase/functions/farm-weather/validation.test.ts`.

The embedded test has been executed successfully. It is useful local evidence for PostgreSQL DDL and RLS/function behavior, but it is not an actual Supabase deployment test: the `pgcrypto` functions and Auth/Storage schemas are mocked, and PGlite does not validate Supabase's real grants, JWT gateway, Storage service or concurrency behavior. The pgTAP file itself has **not** been executed. `npx --yes supabase --version` resolves CLI 3.1.0, but `npx --yes supabase start` fails because Docker and Podman are not installed/on PATH. `psql` is unavailable. No Supabase URL, database URL/password, access token or project reference is configured. Before production, install Docker Desktop or Podman and run `supabase start`, `supabase db reset`, then `supabase test db`.

Required adversarial tests still to add and execute against two test users and two families:

- Cross-family SELECT/INSERT/UPDATE/DELETE and cross-family foreign-key attempts.
- Family A access to Family B notifications, backup records, private Storage objects, invitation rows, and audit logs.
- OWNER/EDITOR/VIEWER escalation, owner removal, and last-owner protection.
- Expired, replayed, random, and concurrently accepted invitation tokens.
- Stale version update/delete races and atomic conflict persistence.
- Soft-delete/restore authorization, RPC field allowlists, server-managed sync metadata, and immutable audit history.
- Private backup signed URL authorization, ZIP bomb/malformed archive handling, import duplicate/cross-family rejection, constraint failure rollback, and repeated confirmation.
- AI rate/payload/result limits and prompt injection attempts.
- Concurrent version updates (the embedded test covers sequential stale writes only), backup export/download service authorization, and real Edge Function invocation.

The embedded test does not exercise AI abuse/rate limits, exported file bytes, signed URL behavior, actual Supabase Auth, OpenWeather upstream behavior, deployed Edge Function authorization, or simultaneous concurrency. No Edge Function was deployed or called against Supabase.

Do not interpret a passing schema contract as proof that the above isolation behavior has passed. Production launch should wait until those cases run in CI against the actual Supabase migration.
