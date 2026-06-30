# NQ GateM Runtime UI 5A Runtime Readiness Overview

> Task: `NQ-GATEM-5-RUNTIME-UI-5A-RUNTIME-READINESS-OVERVIEW`
> Type: `FRONTEND_UI + RUNTIME_READINESS_OVERVIEW + READONLY_API_INTEGRATION + BACKEND_FREE_E2E + DOCUMENTATION_SYNC`
> Status: `PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`
> Date: `2026-06-30`

## Scope

Implemented a read-only `/runtime/readiness` console page for GateM runtime readiness overview.

Changed frontend surface:

- Added `RuntimeReadinessPage`.
- Added `/runtime/readiness` route.
- Added side navigation entry `运行边界`.
- Updated `AppLoadingScreen` to use Ant Design `Card` `variant="borderless"` because this page renders the shared loading screen during auth preflight.
- Added backend-free Playwright smoke `runtime-readiness-overview-smoke.spec.ts`.

Existing `/adapter-readiness` remains compatible and unchanged.

## Implementation

The page reuses the existing adapter readiness query:

- Data source: `GET /api/adapters/readiness`.
- No new backend API.
- No backend contract change.
- No migration.
- No `MarketdataController` change.
- No TradingWorkbench action change.

The page displays:

- Runtime guard summary.
- `LIVE disabled`.
- Adapter readiness matrix summary.
- Permission probe status as `PERMISSION_PROBE_DISABLED / SKIPPED`.
- NoReal / Fake / Stub / FutureReal blocker semantics.
- `READY_FOR_PAPER_ONLY` as Paper-only, not real authorization.
- MarketData readiness card and link to `/marketdata`.
- `PENDING_BACKEND_SUPPORT` for missing central runtime flags and Paper-to-Real aggregate support.

## Validation

| Command | Result | Notes |
| --- | --- | --- |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build passed. Existing Vite large chunk warning remains non-blocking. |
| `cd frontend; npm run test:e2e -- tests/e2e/runtime-readiness-overview-smoke.spec.ts --project=chromium` | PASS | 1 Chromium test passed. Backend-free route stubs cover `/api/adapters/readiness`, auth, and account context. |

Known non-blocking output:

- Vite large chunk warning remains.
- Playwright run prints existing `NO_COLOR` / `FORCE_COLOR` warning.

Not run:

- Maven backend tests: not run because this task did not modify backend code, backend API, or migration.
- Python pytest / mypy / ruff: not run because this task did not modify `research/**`.
- Real local backend smoke: not run; this task intentionally uses backend-free UI smoke only.

## Boundary Confirmation

- No backend changes.
- No migration changes.
- No research / scripts / deploy / workflow changes.
- No new API.
- No permission probe POST.
- No write endpoint call.
- No ingestion trigger.
- No order / cancel / withdraw / transfer.
- No WebSocket.
- No credential material read or display.
- No LIVE enablement.
- No AI or DH runtime integration.
- No RealClient / real provider / real exchange adapter implementation.

`MarketData fresh` is shown only as query-scoped DB readiness available from the MarketData page. The runtime overview does not fabricate global source health and keeps missing aggregate support as `PENDING_BACKEND_SUPPORT`.

## Final Decision

`PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`

Recommended commit:

```text
feat(frontend): add runtime readiness overview
```
