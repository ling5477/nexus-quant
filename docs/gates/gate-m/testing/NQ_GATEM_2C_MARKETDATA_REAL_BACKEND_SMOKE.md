# NQ GateM-2C MarketData Real Backend Smoke

> Task: `NQ-GATEM-2C-MARKETDATA-REAL-BACKEND-SMOKE`
> Type: `FRONTEND_E2E + REAL_BACKEND_SMOKE + MARKETDATA_READINESS_VALIDATION + DOCUMENTATION`
> Status: `PASS / EMPTY-NO-DATA REAL BACKEND SMOKE`
> Scope: MarketData page real local backend bars smoke only.

## Objective

This slice validates that the existing MarketData page can call the real local backend `GET /api/marketdata/bars` through `marketdataApi.listBars()` and render:

- K-line readiness area.
- Volume chart area.
- Data Quality / Readiness area.
- Bar count.
- Last bar time or no-data state.
- Freshness state.
- Quality / gap / unavailable readiness state.

## Backend Environment

- Backend mode: real local `nq-app` backend, `local` Spring profile, port `18888`.
- Health: `/actuator/health` returned `UP`.
- Database: local PostgreSQL reported by backend startup as PostgreSQL `17.7`; Flyway schema was already at version `31`.
- Bars precheck: read-only queries across GateH/GateM fixed MarketData dimensions found no local bars in the tested window.
- Final smoke path: empty/no-data real backend smoke; no fixture was inserted.

No migration, backend code, controller, bars query logic, real exchange adapter, RealClient, real provider, WebSocket, LIVE, AI, or DH runtime was added or enabled.

## Implemented Test

New spec:

- `frontend/tests/e2e/marketdata-real-backend-smoke.spec.ts`

Behavior:

- Fails clearly if local backend health is unavailable.
- Logs in through the real local backend using the existing E2E login helper.
- Performs a read-only bars data precheck through `GET /api/marketdata/bars`.
- Does not stub `/api/marketdata/bars`.
- Navigates to the MarketData page and submits the bars query through the page form.
- Waits for the real page request to `/api/marketdata/bars`.
- Positive branch: if bars exist, asserts K-line canvas, volume canvas, bar count and quality status rendering.
- Empty branch: if no bars exist, asserts no-data K-line/volume state and Data Quality / Readiness unavailable state.

## Validation

Executed locally on 2026-06-29:

| Command | Result | Notes |
| --- | --- | --- |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build completed; existing large chunk warning remains. |
| backend startup + `cd frontend; npm run test:e2e -- tests/e2e/marketdata-real-backend-smoke.spec.ts --project=chromium` | PASS | 1 Chromium test passed. Backend health was UP. Real page request hit `/api/marketdata/bars`. Local bars count was 0, so the accepted path was empty/no-data real backend smoke. |

## Findings

- P0: none.
- P1: none.
- P2: none.
- P3: positive bars fixture pending. The local DB had no bars for the checked fixed MarketData dimensions, so this run accepted the empty/no-data branch. A later slice can add a controlled test fixture only through an existing approved seed/test mechanism.

## Boundary Confirmation

This task did not implement or enable:

- backend API changes.
- `MarketdataController` changes.
- backend bars query changes.
- backend migration changes.
- TradingWorkbench integration.
- real exchange adapter / RealClient / real provider.
- OKX / Binance / Bybit / Gate / Coinbase / Kraken external calls.
- WebSocket, polling, or realtime stream tests.
- order, cancel, withdraw, or transfer behavior.
- LIVE trading.
- AI or DH runtime integration.
