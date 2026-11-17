# Deployment and releases

## Repository setup

The source repository is `shahfauzan/Farm-Family` on the `main` branch and contains both `frontend/` and `backend/`.

## Continuous integration

`.github/workflows/build.yml` runs for pushes, pull requests, and manual dispatch. It validates Edge Function request contracts, applies all four migrations in the embedded PGlite backend test, and builds the Android debug APK. It does not deploy to production.

## Production environment

Create a GitHub Environment named `production`. Add:

- Environment secret `SUPABASE_ACCESS_TOKEN`. It must be a Supabase access token allowed to deploy Edge Functions, manage function secrets, and push Auth configuration.
- Environment secret `SUPABASE_DB_PASSWORD` for the tagged release workflow.
- Environment variable `SUPABASE_PROJECT_REF` (defaults to the configured FARM FAMILY project reference if omitted).
- Environment secrets `OPENAI_API_KEY` and `OPENWEATHER_API_KEY`; optional `OPENAI_MODEL` is an environment variable.

The release workflow links the Supabase project, runs `supabase db push --dry-run --linked` to verify connectivity and migration planning, and only then applies migrations. If the dry run or migration deployment fails, function deployment and release creation do not run.

The `Deploy Supabase functions` workflow copies the two provider secrets into Supabase Edge Function secrets. The Android app must never receive these server credentials. Do not put secret values in GitHub variables, workflow files, documentation, or logs. If the GitHub workflow fails its credential check, verify the exact environment secret names above; do not paste secret values into chat.

The live Auth redirect allow list and site URL use `farmfamily://auth-callback`. Supabase rejected custom email templates on the current free tier with the default mail provider. Changing the confirmation template requires a custom SMTP provider or an eligible plan; email confirmation remains enabled.

Before enabling production releases, verify the GitHub runner's network access and credentials against the intended Supabase project. Configure environment approvals/branch restrictions as appropriate for the team.

## Release process

1. Open a pull request and require the build workflow to pass.
2. Merge the reviewed change to the protected release branch.
3. Create and push a semantic version tag (`vMAJOR.MINOR.PATCH`), or manually dispatch the release workflow with a version.
4. The workflow validates the exact commit, reruns backend tests, and builds the Android release APK.
5. It verifies database connectivity with a migration dry run, applies pending migrations, and deploys Supabase Edge Functions.
6. Only after successful deployment does it create a GitHub Release and attach the APK.

The production release workflow signs the APK only when its Android signing secrets are available. The local debug APK is for direct testing, not Play Store publication.

## Failure and rollback

The workflow stops at the first failed validation, database operation, or function deployment. Investigate deployment logs without printing secret values. Database rollbacks are not automatic: prepare and review a forward-fix migration or a separately tested restore plan. Never rewrite applied migration history to recover a production deployment.

## Current limitations

The direct GitHub Supabase deployment workflow is currently failing, so provider API secrets have not yet been copied to the hosted project. The `OPENAI_API_KEY` and `OPENWEATHER_API_KEY` names were absent from the live Edge Function secret list at the last check. Add or correct the production `SUPABASE_ACCESS_TOKEN` secret and rerun the workflow before expecting AI or weather responses.
