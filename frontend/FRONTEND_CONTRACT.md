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

Authentication and email deep links use Supabase. Family create/join and membership lookup use the existing RPC/Edge Function contract. Fields are cached in a family-scoped SQLite database. New fields are written locally first, carry a stable `local_id`, and are retried when records are refreshed; uncertain insert responses are reconciled against that ID. A cached field list remains available when the field request cannot reach the server, and the UI distinguishes offline/pending states from a successful server fetch.

Offline support is limited to fields and foreground refresh retries. Authentication, family setup, and crop-season reads remain online-only. There is no background connectivity worker, general sync engine, queued edit/delete, or conflict-resolution UI. State is currently held by Compose screen state rather than a ViewModel. Members/invitations, other farm-record modules, reports, AI, weather, notifications, backup/import/export, and most designed screens are not implemented. Live Supabase and rendered-device behavior have not been verified.

## Example route contract

Expense List

- Backend: family expense query and family-scoped operations
- Read: authorized family expenses
- Create: expense create RPC or edge-function flow
- Update: sync update with version handling
- Delete: soft-delete operation
- States: loading, empty, syncing, synced, error, offline, conflict
