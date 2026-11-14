# FARM FAMILY Frontend contract

## Product scope

The frontend follows the FARM FAMILY product contract: family-scoped farm operations, authenticated access, local-first synchronization, and backend-driven data access.

## Screen map

- Login
  - Auth state: logged out, logging in, logged in, session expired, auth error
  - Backend operation: Supabase Auth email/password sign in
  - UI states: loading, error, demo-only access in debug builds only

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
ViewModel / screen state
↓
Repository
↓
Local cache / sync queue
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

## Example route contract

Expense List

- Backend: family expense query and family-scoped operations
- Read: authorized family expenses
- Create: expense create RPC or edge-function flow
- Update: sync update with version handling
- Delete: soft-delete operation
- States: loading, empty, syncing, synced, error, offline, conflict
