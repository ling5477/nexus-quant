# NQ GateM-5B Runtime UI MarketData Readiness Deep Link

> Task: `NQ-GATEM-5B-RUNTIME-UI-MARKETDATA-READINESS-DEEP-LINK`
> Type: `FRONTEND_UI + RUNTIME_READINESS_UI + MARKETDATA_READINESS_LINKAGE + TEST_REVIEW + DOCUMENTATION_SYNC`
> Status: `PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`
> Date: `2026-06-30`

## Scope

Implemented a read-only deep link from `/runtime/readiness` to `/marketdata` so operators can move from Runtime readiness context into the existing MarketData K-line / volume / Data Quality readiness page.

Changed frontend surface:

- Runtime MarketData card CTA now links to `/marketdata?exchangeCode=BINANCE&marketType=SPOT&symbol=BTC-USDT&interval=1m`.
- MarketData page reads only the safe query fields `exchangeCode`, `marketType`, `symbol`, and `interval`.
- MarketData page validates query values against existing select options before pre-filling the query form.
- MarketData page does not auto-submit from the deep link; bars/readiness remain explicit read-only queries.
- Added backend-free Playwright smoke `runtime-marketdata-readiness-link-smoke.spec.ts`.
- Updated the existing Runtime overview smoke to assert the new safe deep link.

## Implementation

The deep link uses only non-sensitive URL query parameters:

- `exchangeCode`
- `marketType`
- `symbol`
- `interval`

Unsupported or unknown query values are ignored. No date range is passed through the URL. The MarketData page keeps `startTime` / `endTime` user-entered so the deep link cannot silently trigger a query window or collection workflow.

The MarketData page displays an informational notice when runtime query context is applied. The notice explicitly says the page will not auto-trigger collection, ingestion `run-once`, or write endpoints.

## Validation

| Command | Result | Notes |
| --- | --- | --- |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build passed. Existing Vite large chunk warning remains non-blocking. |
| `cd frontend; npm run test:e2e -- tests/e2e/runtime-marketdata-readiness-link-smoke.spec.ts --project=chromium` | PASS | Backend-free route stubs verify Runtime CTA, MarketData URL query prefill, K-line / quality sections, and no write endpoint calls. |

Known non-blocking output:

- Vite large chunk warning remains.
- Playwright run may print existing `NO_COLOR` / `FORCE_COLOR` warning.

Not run:

- Maven backend tests: not run because this task did not modify backend code, backend API, or migration.
- Python pytest / mypy / ruff: not run because this task did not modify `research/**`.
- Real local backend smoke: not run; this task intentionally uses backend-free UI smoke only.

## Boundary Confirmation

- No backend changes.
- No migration changes.
- No research / scripts / deploy / workflow changes.
- No new API.
- No MarketdataController change.
- No TradingWorkbench change.
- No permission probe POST.
- No write endpoint call.
- No ingestion `run-once`.
- No order / cancel / withdraw / transfer.
- No WebSocket.
- No credential material read or display.
- No LIVE enablement.
- No AI or DH runtime integration.
- No RealClient / real provider / real exchange adapter implementation.

`MarketData fresh` remains query-scoped DB readiness. The deep link only pre-fills safe query fields and does not make MarketData freshness, no-real adapter readiness, Paper-only state, or permission probe `SKIPPED` look like real/live readiness.

## Final Decision

`PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`

Recommended commit:

```text
feat(frontend): link runtime and marketdata readiness
```
