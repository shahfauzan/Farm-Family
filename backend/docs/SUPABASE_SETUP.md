# Supabase setup and deployment guide

This backend is designed for Supabase Auth email/password only. It does not use Google OAuth, phone OTP, or any other external identity provider in v1.

## 1. Environment variables

Create a local `.env` file from `.env.example` in the `backend/` directory.

Client-safe values:
- `SUPABASE_URL`: project URL from your Supabase project.
- `SUPABASE_ANON_KEY`: anonymous/public key for client access.

Server-only secrets:
- `OPENAI_API_KEY`: required by the AI edge function.
- `OPENAI_MODEL`: optional model override for the AI function.
- `OPENWEATHER_API_KEY`: required by the authenticated weather edge function.

Do not place service-role, OpenAI, or OpenWeather keys in Android/frontend code.

## 2. Local Supabase setup

Required tooling:
- Docker Desktop or Podman
- Supabase CLI (`npx --yes supabase` or a local install)

Run:

```bash
cd backend
npx --yes supabase start
npx --yes supabase db reset
npx --yes supabase test db
```

If using a local project, this applies the migrations in `supabase/migrations` and validates the schema contract.

## 3. Production Supabase project setup

After creating the project in Supabase:

1. Copy the project URL and anon key from the project settings.
2. Enable email/password auth in Supabase Auth.
3. Configure email confirmation and SMTP if you want confirmation flows.
4. Ensure the project has PostgreSQL, Storage, and Edge Functions enabled.
5. Configure the GitHub `production` Environment as described in the repository's [deployment guide](../../docs/deployment.md).

Production migrations must be applied through the root release workflow. Do not run `supabase db push` or `supabase db reset` against the hosted production project manually.

## 4. Function deployment

The root release workflow deploys all Edge Functions after successful production migration deployment.

Set secrets:

```bash
supabase secrets set OPENAI_API_KEY=... OPENWEATHER_API_KEY=... OPENAI_MODEL=gpt-4.1-mini
```

## 5. Required auth configuration

In Supabase Auth:
- enable email/password sign-up
- enable sign-in
- configure redirect URLs for your app and any email flows
- set confirmation email / SMTP if needed

Do not enable Google OAuth for v1.

## 6. Storage and backup configuration

The backend expects a private bucket named:
- `family-backups`

This bucket is private and only family owners can create or download under their cluster.

## 7. Post-deployment verification

After setup, verify:
- auth signup/login works with email/password
- `profiles` rows are created automatically
- family creation and owner membership succeed
- invitation accept flow works
- AI edge function responds only to allowlisted operations
- backup export and import preview/confirm paths work
- `farm-weather` returns current conditions and forecast only to an authenticated family member
- RLS denies cross-family access attempts

## 8. Important safety rules

- Never expose service-role keys in Android/frontend code.
- Never expose OpenAI keys in Android/frontend code.
- Never expose OpenWeather keys in Android/frontend code.
- Never use Google OAuth for this v1 backend.
- Never treat backend migrations as complete until they have been applied and tested on a real project.
