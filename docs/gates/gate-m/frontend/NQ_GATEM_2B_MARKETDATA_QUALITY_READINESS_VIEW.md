# NQ GateM-2B MarketData Quality Readiness View

> Task: `NQ-GATEM-2B-MARKETDATA-QUALITY-READINESS-VIEW`
> Type: `FRONTEND_UI + MARKETDATA_READINESS + DATA_QUALITY_VIEW + TEST_REVIEW`
> Status: `IMPLEMENTED / READY FOR REVIEW`
> Scope: MarketData page data quality / freshness / gap / qualityStatus expression only.

## Authoritative Scope

This task extends the existing GateM-2 MarketData chart readiness page. It does not add a backend API, backend field, real exchange source, live transport or trading workflow.

Implemented surface:

- `MarketdataPage` adds a `Data Quality / Readiness` section backed by the existing bars query.
- The page keeps the existing `NqKlineChart`, `NqVolumeChart`, Bars table, ingestion jobs, runs and datasets sections.
- The quality view displays exchange, instrument, interval, query window, bar count, first bar time, last bar time, latest close, latest volume, freshness, qualityStatus aggregation, gap count, unknown quality count and source health.
- Source health is explicitly shown as `Pending backend support` / `not available from current API`.

## Data Quality Model

The front-end readiness summary is derived only from the returned `MarketdataBar[]` and the submitted query:

- `GOOD`: bars are present, sequential, and qualityStatus values are OK.
- `GAP`: qualityStatus or interval sequence indicates missing bars.
- `STALE`: last bar does not cover the submitted query end window by the current interval estimate.
- `WARN`: qualityStatus is incomplete or contains non-OK values.
- `ERROR`: bars query failed.
- `UNKNOWN`: query is pending, loading, empty, or fields are insufficient.

`UNKNOWN` is never displayed as READY. Missing source health is not fabricated as OK.

## Boundary Confirmation

This task does not implement or enable:

- backend API changes.
- `MarketdataController` changes.
- backend bars query changes.
- TradingWorkbench integration.
- real exchange adapter / RealClient / real provider.
- WebSocket or live refresh transport.
- order, cancel, withdraw or transfer behavior.
- buy/sell markers, moving averages, VWAP or indicator systems.
- LIVE trading.
- AI or DH runtime integration.

## Validation

Executed locally on 2026-06-29:

| Command | Result | Notes |
| --- | --- | --- |
| `npm run build` | PASS | TypeScript build and Vite production build completed; Vite retained the existing large chunk warning. |
| `npm run test:e2e -- tests/e2e/marketdata-quality-readiness-smoke.spec.ts --project=chromium` | PASS | Backend-free Chromium smoke; mocked auth, account context and `/api/marketdata/bars`; verified quality readiness area, bar count, freshness, last bar time, sequence gap, unknown quality, source-health unavailable state and K-line canvas. |

Real backend bars E2E was not required or run in this task.
