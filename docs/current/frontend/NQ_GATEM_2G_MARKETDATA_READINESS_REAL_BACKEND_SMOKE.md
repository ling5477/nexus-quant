# NQ GateM-2G MarketData Readiness Real Backend Smoke

> Task: `NQ-GATEM-2G-MARKETDATA-READINESS-REAL-BACKEND-SMOKE`
> Type: `FRONTEND_E2E + REAL_BACKEND_SMOKE + MARKETDATA_READINESS_VALIDATION + DOCUMENTATION`
> Status: `PASS / EMPTY-NO-DATA REAL BACKEND READINESS SMOKE`
> Scope: MarketData page real local backend smoke for `/api/marketdata/bars` and `/api/marketdata/readiness`.

## Objective

This slice validates the MarketData page against the real local `nq-app` backend. It proves that the page can:

- log in through the real local backend.
- open the MarketData page.
- submit a MarketData bars query.
- issue real `GET /api/marketdata/bars`.
- issue real `GET /api/marketdata/readiness`.
- render the K-line container.
- render the volume container.
- render the Data Quality / Readiness area.
- display at least one backend readiness field from `status`, `freshnessStatus`, `sourceHealthStatus`, or `backendSupportLevel`.

## Backend Environment

- Backend mode: real local `nq-app` backend, `local` Spring profile, port `18888`.
- Health: `/actuator/health` returned `UP`.
- Database: local PostgreSQL was reachable through the backend health component; Flyway schema was up to date at version `31`.
- Bars precheck: the Playwright smoke performed readonly authenticated API preflight across UI-supported MarketData dimensions and selected `BINANCE / SPOT / BTC-USDT / 1m`.
- Precheck result: `preflightBars=0`, `readinessBarCount=0`, `readinessStatus=NO_DATA`.
- Final branch: empty/no-data real backend readiness smoke. No fixture was inserted.

No migration, backend code, controller, bars/readiness query logic, real exchange adapter, RealClient, real provider, WebSocket, LIVE, AI, or DH runtime was added or enabled.

## Implemented Test

New spec:

- `frontend/tests/e2e/marketdata-readiness-real-backend-smoke.spec.ts`

Behavior:

- Fails clearly if local backend health is unavailable.
- Uses existing `loginToConsole()` to enter the console through the real backend.
- Performs readonly bars/readiness API preflight to decide positive vs empty branch.
- Does not stub `/api/marketdata/bars`.
- Does not stub `/api/marketdata/readiness`.
- Navigates to the MarketData page and submits the query through the form.
- Waits for real page requests to both `/api/marketdata/bars` and `/api/marketdata/readiness`.
- Positive branch: if bars exist, asserts K-line canvas, volume canvas, readiness bar count, last bar time, gap and quality summary.
- Empty branch: if no bars exist, asserts no-data K-line/volume state, `NO_DATA` readiness, readiness-backed `Bars loaded = 0`, and Data Quality / Readiness visibility.

## Validation

Executed locally on 2026-06-29:

| Command | Result | Notes |
| --- | --- | --- |
| `npm ci` | PASS | Restored frontend lockfile dependencies after the first build found missing local `lightweight-charts` install artifacts. |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build completed; existing large chunk warning remains. |
| backend startup + `cd frontend; npm run test:e2e -- tests/e2e/marketdata-readiness-real-backend-smoke.spec.ts --project=chromium` | PASS | 1 Chromium test passed. Backend health was UP. Real page requests hit both `/api/marketdata/bars` and `/api/marketdata/readiness`. Local bars count was 0, so the accepted branch was empty/no-data readiness smoke. |

## Findings

- P0: none.
- P1: none.
- P2: none.
- P3: positive bars fixture pending. The local DB had no bars for the selected UI-supported scope, so this run accepted the empty/no-data branch. A later slice may add a controlled positive fixture only through an existing approved seed/test mechanism.

## Boundary Confirmation

This task did not implement or enable:

- backend changes.
- backend migration changes.
- `MarketdataController` changes.
- backend bars/readiness query changes.
- TradingWorkbench integration.
- real exchange adapter / RealClient / real provider.
- OKX / Binance / Bybit / Gate / Coinbase / Kraken external calls.
- WebSocket, realtime, or polling tests.
- order, cancel, withdraw, or transfer behavior.
- LIVE trading.
- AI or DH runtime integration.

Existing backend-free MarketData smokes remain in place.
