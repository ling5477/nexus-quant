# NQ GateM-5C Runtime UI Paper Boundary Banners

> Task: `NQ-GATEM-5C-RUNTIME-UI-PAPER-BOUNDARY-BANNERS`
> Type: `FRONTEND_UI + RUNTIME_GUARD_BANNER + PAPER_REAL_BOUNDARY_UI + TEST_REVIEW + DOCUMENTATION_SYNC`
> Status: `PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`
> Date: `2026-06-30`

## Scope

Implemented read-only runtime guard banners on Paper Trading and TradingWorkbench pages.

Changed frontend surface:

- Added shared `RuntimeGuardBanner`.
- `/paper-trading/*` route shell now shows `Paper-only boundary`.
- `/trading` now shows `Runtime guarded: LIVE disabled`.
- Added backend-free Playwright smoke `runtime-paper-boundary-banners-smoke.spec.ts`.

## Implementation

Paper Trading banner states:

- Paper Trading is simulated.
- Paper order != real order.
- Paper fill != real fill.
- Paper balance/position != real account balance/position.
- Paper risk pass != LIVE authorization.
- Published strategy, Paper risk pass, readiness rows, and permission probe SKIPPED do not authorize LIVE trading.

TradingWorkbench banner states:

- LIVE disabled.
- Real provider not implemented.
- NoReal/Fake/Stub/FutureReal not live-ready.
- Permission probe SKIPPED / disabled is not verified.

The banners are not closable controls and do not unlock, disable, or bypass any existing action. Backend fail-closed guards remain authoritative.

## Validation

| Command | Result | Notes |
| --- | --- | --- |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build passed. Existing Vite large chunk warning remains non-blocking. |
| `cd frontend; npm run test:e2e -- tests/e2e/runtime-paper-boundary-banners-smoke.spec.ts --project=chromium` | PASS | Backend-free route stubs verify Paper and Trading guard copy and no `/api/**` write endpoint calls. |

Known non-blocking output:

- Existing `NO_COLOR` / `FORCE_COLOR` warning remains.
- Existing Ant Design `Card.bordered` and `Modal.destroyOnClose` deprecation warnings remain.
- Existing `useForm` not connected warning appears when the TradingWorkbench page mounts inactive drawers; this was not introduced by the banner logic.

Not run:

- Maven backend tests: not run because this task did not modify backend code, backend API, trading services, Paper services, risk services, or migration.
- Python pytest / mypy / ruff: not run because this task did not modify `research/**`.
- Real local backend smoke: not run; this task intentionally uses backend-free UI smoke only.

## Boundary Confirmation

- No backend changes.
- No migration changes.
- No research / scripts / deploy / workflow changes.
- No new API.
- No TradingWorkbench order logic change.
- No Paper service or risk service change.
- No permission probe POST.
- No write endpoint call from the guard smoke.
- No ingestion `run-once`.
- No order / cancel / withdraw / transfer triggered by banners.
- No WebSocket.
- No credential material read or display.
- No LIVE enablement.
- No AI or DH runtime integration.
- No RealClient / real provider / real exchange adapter implementation.

The banners explicitly preserve the distinction between Paper readiness and LIVE authorization. Paper-ready, DB-fresh, readiness rows, and permission probe `SKIPPED` remain non-authorizing runtime signals.

## Final Decision

`PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`

Recommended commit:

```text
feat(frontend): add paper real boundary guard banners
```
