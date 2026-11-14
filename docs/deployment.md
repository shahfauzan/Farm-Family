# Deployment and releases

## Repository setup

The intended source repository is a private GitHub repository containing both `frontend/` and `backend/`. This workspace currently has no Git repository or remote configured, so repository privacy, branch protection, and workflow execution have not been verified.

## Continuous integration

`.github/workflows/build.yml` runs for pushes, pull requests, and manual dispatch. It validates Edge Function request contracts, applies all four migrations in the embedded PGlite backend test, and builds the Android debug APK. It does not deploy to production.

## Production environment

Create a GitHub Environment named `production`. Add:

- Environment secrets `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD`.
- Environment variable `SUPABASE_PROJECT_REF`.

The release workflow links the Supabase project, runs `supabase db push --dry-run --linked` to verify connectivity and migration planning, and only then applies migrations. If the dry run or migration deployment fails, function deployment and release creation do not run.

Set `OPENAI_API_KEY` and `OPENWEATHER_API_KEY` (and optional `OPENAI_MODEL`) as Supabase Edge Function secrets in the target project. The Android app must never receive these server credentials. Do not put secret values in GitHub variables, workflow files, documentation, or logs.

Before enabling production releases, verify the GitHub runner's network access and credentials against the intended Supabase project. Configure environment approvals/branch restrictions as appropriate for the team.

## Release process

1. Open a pull request and require the build workflow to pass.
2. Merge the reviewed change to the protected release branch.
3. Create and push a semantic version tag (`vMAJOR.MINOR.PATCH`), or manually dispatch the release workflow with a version.
4. The workflow validates the exact commit, reruns backend tests, and builds the Android release APK.
5. It verifies database connectivity with a migration dry run, applies pending migrations, and deploys Supabase Edge Functions.
6. Only after successful deployment does it create a GitHub Release and attach the APK.

The APK is unsigned because signing credentials/configuration are not present. It is not suitable for Play Store publication until a secure signing process is deliberately configured.

## Failure and rollback

The workflow stops at the first failed validation, database operation, or function deployment. Investigate deployment logs without printing secret values. Database rollbacks are not automatic: prepare and review a forward-fix migration or a separately tested restore plan. Never rewrite applied migration history to recover a production deployment.

## Current limitations

The release workflow has not been run against the hosted project. It requires valid production Environment configuration and a successful GitHub-runner-to-Supabase connection. Android frontend/backend integration, signed app distribution, and hosted Supabase runtime verification remain outstanding.
