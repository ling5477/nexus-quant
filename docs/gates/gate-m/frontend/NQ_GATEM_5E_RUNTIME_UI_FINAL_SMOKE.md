# NQ GateM-5E Runtime UI Final Smoke

> Task: `NQ-GATEM-5E-RUNTIME-UI-FINAL-SMOKE`
> Type: `FRONTEND_E2E + RUNTIME_UI_REGRESSION_SMOKE + DOCUMENTATION_SYNC`
> Status: `PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`
> Date: `2026-06-30`

## Scope

Added one backend-free Playwright summary smoke for the Runtime Guarded UI chain.

Changed frontend test surface:

- Added `runtime-ui-final-smoke.spec.ts`.
- The smoke visits `/dashboard`, `/runtime/readiness`, `/marketdata`, `/paper-trading`, and `/trading`.
- The smoke verifies Dashboard -> Runtime Readiness, Dashboard -> MarketData Readiness, and Runtime -> MarketData navigation.

No page source code was changed.

## Coverage

The final smoke checks the following read-only boundary copy:

- `LIVE disabled`.
- `Real provider not implemented`.
- `Paper simulated only` and `Paper-only boundary`.
- `Permission probe SKIPPED / disabled is not verified`.
- `NoReal/Fake/Stub/FutureReal not live-ready`.
- Runtime overview `PERMISSION_PROBE_DISABLED / SKIPPED`.
- Runtime overview `RealClient / real provider / real exchange adapter not implemented`.

The smoke also verifies that `/marketdata` remains renderable with K-line and Data Quality / Readiness sections and that the Runtime -> MarketData deep link safely pre-fills non-sensitive query fields.

## Validation

| Command | Result | Notes |
| --- | --- | --- |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build passed. Existing Vite large chunk warning remains non-blocking. |
| `cd frontend; npm run test:e2e -- tests/e2e/runtime-ui-final-smoke.spec.ts --project=chromium` | PASS | 1 Chromium test passed. Backend-free route stubs, no real backend, no real exchange, no credential, no LIVE. |

Implementation note:

- First smoke run failed because the test treated Vite dev-server HMR `ws://127.0.0.1:<port>/?token=...` as an application WebSocket. The smoke now filters local Vite HMR and still asserts no application or external exchange WebSocket.

Known non-blocking output:

- Existing Vite large chunk warning remains.
- Existing `NO_COLOR` / `FORCE_COLOR` warning remains.
- Existing Ant Design `Card.bordered`, `Modal.destroyOnClose`, React 19 compatibility, and inactive `useForm` warnings remain.

Not run:

- Full E2E suite: not required by this smoke-only task.
- Maven backend tests: not run because this task did not modify backend code, backend API, paper/trading/readiness services, or migration.
- Python pytest / mypy / ruff: not run because this task did not modify `research/**`.
- Real local backend smoke: not run; this task intentionally uses backend-free UI smoke only.

## Boundary Confirmation

- No backend changes.
- No migration changes.
- No research / scripts / deploy / workflow changes.
- No frontend page or component source changes.
- No new API.
- No backend readiness / paper / trading API changes.
- No TradingWorkbench order logic change.
- No LIVE UI entry.
- No order capability.
- No real exchange call.
- No credential material read or display.
- No permission probe POST.
- No ingestion `run-once`.
- No order / cancel / transfer / withdraw write endpoint call.
- No application WebSocket.
- No LIVE enablement.
- No AI or DH runtime integration.
- No RealClient / real provider / real exchange adapter implementation.

The smoke keeps Paper-ready, DB-fresh, and permission probe `SKIPPED` as non-authorizing runtime signals. It does not display live-ready or verified permission status.

## Final Decision

`PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`

Recommended commit:

```text
test(frontend): add runtime guarded UI final smoke
```
