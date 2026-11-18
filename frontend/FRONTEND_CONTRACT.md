# FARM FAMILY Frontend contract

## Product scope

The frontend follows the FARM FAMILY product contract: family-scoped farm operations, authenticated access, local-first synchronization, and backend-driven data access.

## Screen map

- Login
  - Auth state: logged out, restoring, logging in, logged in, logging out, auth error
  - Backend operation: Supabase Auth email/password sign-in/sign-up, confirmation, password recovery, and reset
  - Session: refreshable tokens encrypted with Android Keystore
  - UI states: loading and explicit configuration/network/auth errors; no demo sign-in

- Home
  - Backend operation: family overview query and family context lookup
  - Local model: dashboard summary, last synced state, offline mode
  - Empty states: no fields, no expenses, no synced records

- Records
  - Routes: Fields, Expenses, Harvest, Sales, AI
  - Backend operation: family record listing plus create/update/delete through backend RPCs
  - Local model: sync status, local ID, server ID, conflict status

- Reports
  - Backend operation: inherited family summaries and report queries
  - Response model: totals, KPIs, pending sync, summary cards

- AI
  - Backend operation: authenticated AI endpoint with allowlisted actions
  - Response model: text, list, summary, chart-ready values

- More
  - Family members, notifications, settings, profile, security, sign out
  - Backend operation: membership queries and auth/session teardown
- Invitations
  - Owners can share an eight-digit code or a locally generated QR link
  - QR links expire after one minute; the code follows the invitation's configured expiry
  - Joining still uses the existing authenticated account and membership flow

## Local-first architecture

UI
↓
Compose screen state
↓
Repository
↓
SQLite field cache / field-insert queue
↓
Supabase backend

## Required state handling

- pending
- syncing
- synced
- failed
- conflict

Every screen must support loading, error, empty, offline, auth, and permission states.

## Contract requirements

- No hardcoded production family IDs
- No fake main production data in real user flows
- No generic direct SQL UPDATE/DELETE assumptions from the client
- Use RPC and backend operations that match the database contract
- Conflicts must be surfaced and never silently overwritten
- Auth and family context must be loaded before family-scoped actions

## Current implementation boundary

The Android app uses Supabase Auth and family-scoped repository operations. The authenticated workspace includes home, farm record modules, reporting, AI/weather entry points, family and invitation management, member permissions, profile, backup/import/export, notifications, settings, and recycle-bin flows. Field records use the local cache and stable IDs for foreground retry/reconciliation; other modules use their repository and Supabase operations. Hindi mode is persisted locally and translates UI strings routed through `FarmText`; any untranslated string falls back to its English source.

Database migrations define family data, access policies, invitations, backup/import operations, report support, weather quota, and function grants. Hosted schema and ACL checks were run during the current maintenance pass, and backend migration/contract tests passed. An Android debug APK compiled successfully. These checks do not equal a full live-account/device test: email confirmation, real invitations, external API calls, and backup restore against a production user's data still require an end-to-end test. AI and weather also require their server-side provider secrets to be configured in Supabase Edge Function secrets. Offline behavior remains limited; do not treat all modules as a general-purpose offline sync engine.

## Example route contract

Expense List

- Backend: family expense query and family-scoped operations
- Read: authorized family expenses
- Create: expense create RPC or edge-function flow
- Update: sync update with version handling
- Delete: soft-delete operation
- States: loading, empty, syncing, synced, error, offline, conflict
