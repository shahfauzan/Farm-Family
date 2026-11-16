# FARM FAMILY frontend rebuild matrix

The supplied Figma PDF is a single canvas with approximately 129 Android frames and state surfaces. This matrix groups those frames by user journey while retaining the state coverage required by the product contract. A feature is complete only when its UI, navigation, real data operation, validation, permissions, and loading/empty/error behavior are connected.

## Visual and application foundation

| Area | Required surfaces / states | Data and behavior | Status |
|---|---|---|---|
| Design system | Splash, typography, forest/leaf/cream colors, 8 dp grid, 48 dp controls, icons, buttons, inputs, row and card variants | Inter; accessible contrast, labels, reduced motion | Implemented; visual comparison still needed |
| Navigation | Home, Records, Reports, AI, More; nested routes; selected tab; back; restored state | Stable family and auth context across routes | Implemented; device verification pending |
| Shared states | Loading, skeleton, empty, no results, offline, pending, syncing, synced, failed, conflict, permission denied, retry | Never claim a remote success on failed requests | Partial; server operations show states, generic offline queue and conflict resolution remain |

## Authentication and family

| Journey | Required surfaces / states | Existing contracts | Status |
|---|---|---|---|
| Authentication | Splash/session restore; sign in; create account; validation; busy; auth/network error; confirmation email; forgot password; recovery email; set password; invalid/expired link; sign out confirmation | Supabase Auth email/password and deep link | Implemented; live Supabase verification pending |
| Family setup | No family; create family; join by code/link; invalid, expired, revoked invitation; success; multiple-family chooser | `create_family`; `accept-invitation` Edge Function | Implemented; live verification pending |
| Family overview | Summary; member list; member detail; role and permission display; loading/error/empty | `family_members`, `profiles`, family-scoped RLS | Partial; roster is implemented in migration `202611160006_family_member_roster.sql`; apply is pending because the connected Supabase project is inactive |
| Invitations | Create role-scoped invite; expiry; code/link share/copy; pending/accepted/expired/revoked; revoke confirmation/result | `create_family_invitation`; `revoke_family_invitation` | Partial; code/result available, native share and confirmation polish remain |
| Roles | OWNER, EDITOR, VIEWER; permission display; denied add/edit/delete; server authorization error | Existing permission bits and RPCs | Implemented; device verification pending |

## Home, records, and reports

| Journey | Required surfaces / states | Existing contracts | Status |
|---|---|---|---|
| Home | Greeting; family context; metrics; season; recent activity; quick actions; sync status; no-data/loading/error/offline | Fields, season, year/finance summaries, activity | Partial; data and weather wired, activity/offline queue remains |
| Weather | Current conditions; forecast; stale; refresh; unavailable; loading; network/permission error | `farm-weather`; coordinates from field location | Partial; current conditions from saved coordinates, no forecast or manual refresh |
| Records hub | Search; filters; module rows; empty/no-results/loading/error; add action | `family_search` and record tables | Partial; search and module navigation work, filter controls remain |
| Fields | List/detail/add/edit/delete; area units; location; validation; sync state | `fields`; versioned `sync_update`; soft delete | Planned |
| Crop seasons and crops | List/detail/add/edit; field/crop relationships; multiple seasons per field; dates and purpose; completeness flags | `crop_seasons`, `crops` | Implemented; owner-only completeness flags included |
| Seeds | List/detail/add/edit/delete; quantity, unit, amount, currency, date | `seed_records` | Planned |
| Fertilizer | List/detail/add/edit/delete; product, quantity, amount, date | `fertilizer_records` | Planned |
| Labor | List/detail/add/edit/delete; date, workers, hours, amount | `labor_records` | Planned |
| Machinery | List/detail/add/edit/delete; service, provider, quantity, amount, date | `machinery_usage` | Planned |
| Expenses | Overview/list/detail/add/edit/delete; categories; date/filter/search; validation; save/delete confirmation | `expenses`, `expense_categories` | Planned |
| Harvests | List/detail/add/edit/delete; season, date, quantity, unit, grade | `harvest_records` | Planned |
| Sales | List/detail/add/edit/delete; harvest/season, quantity, unit price, total, buyer | `sales` | Planned |
| Historical prices | List/detail/add/edit/delete; crop, market, observed date, unit, price, currency/source | `historical_prices` | Planned |
| Reports | Home; expense, harvest, sales, field/crop performance; filters/date ranges; charts; no-data/loading/error | Year/season/finance/field/crop/activity/price report RPCs | Partial; year summary, year selector, value bars and record analysis wired |

## AI, notifications, backup, offline, and settings

| Journey | Required surfaces / states | Existing contracts | Status |
|---|---|---|---|
| Farm AI | Landing; conversation; suggestions; thinking; answer with supporting records; no data; unavailable/error; review and confirm allowlisted write action; result | Read-only `ai-assistant`; safe write action requires additional backend contract | Partial; authenticated read-only answers; write preview is not implemented |
| Notifications | Center/list/detail; unread/read; action; empty/loading/error | `notifications` RLS and `read_at` update | Partial; family inbox and read marking wired, detail/action links remain |
| Backup/export | Format selection; PDF/CSV/XLSX/ZIP; progress; complete/error; download | `family-backups` Edge Function and private Storage | Partial; ZIP/CSV creation, listing, and signed download wired |
| Import/restore | Select ZIP; validate; malformed/unsupported/invalid relationships; preview/counts/duplicates; explicit confirm; complete/error | `family-imports`; transactional confirm RPC | Partial; local ZIP upload, server validation preview, and explicit transactional confirmation wired; visual conflict review pending |
| Offline and sync | Local save; pending; syncing; synced; failed; conflict; compare server/local; resolve/retry | Current local field queue; extend generic records; `sync_update` conflict response | Partial; legacy field queue remains; other record types are online-only |
| Profile/settings | Profile view/edit; family settings; sync settings; About/help; security; sign out confirmation | `profiles`, family rows, Supabase Auth | Partial; profile, family name, sign out, and basic app info wired; remaining settings panels omitted |

## Implementation gate

Do not mark an item complete from a visual placeholder. Each journey must use authenticated family-scoped data, enforce UI and backend permissions, and show real operation outcomes. Existing Supabase migrations and Edge Functions are preserved unless a listed flow cannot be implemented safely with their contracts; any required migration must be applied and verified before enabling its dependent screen in a live environment.

## Verification and deployment notes

- The Supabase management connection identified project `mtjpjrjxftqlwescilyc` as inactive. Read-only table/migration inspection timed out; migration 006 was not applied to the remote database.
- `:app:compileDebugKotlin` passes. `:app:testDebugUnitTest` reports `NO-SOURCE`; no unit tests exist.
- The connected Windows environment has no available `adb` command/device, so runtime screen comparison against the PDF has not been performed.
- The local Supabase CLI is unavailable, so the new SQL migration has not been applied or tested against a live database.
