# NQ GateM-2 MarketData Kline Readiness

> Task: `NQ-GATEM-2-MARKETDATA-KLINE-READINESS`
> Type: `FRONTEND_UI + MARKETDATA_READINESS + KLINE_PAGE_INTEGRATION + TEST_REVIEW`
> Status: `IMPLEMENTED / READY FOR REVIEW`
> Scope: MarketData page chart readiness view only.

## Authoritative Scope

This task wires the completed B0.4 chart foundation into `MarketdataPage`.

Implemented surface:

- `MarketdataPage` derives `NqKlineBar[]` from existing `/api/marketdata/bars` responses.
- The page renders `NqKlineChart` and `NqVolumeChart` from the existing `barsQuery`.
- The page keeps the existing Bars table, ingestion jobs, runs and datasets sections.
- The page displays exchange, instrument, timeframe, bar count, last bar time, data quality and freshness.
- The page exposes loading, initial empty, query empty, error, stale and gap / degraded quality states.

## Data Flow

```text
MarketdataPage query form
  -> marketdataApi.listBars(query)
  -> MarketdataBar[]
  -> page-level mapper
  -> NqKlineBar[]
  -> NqKlineChart / NqVolumeChart
```

The chart components do not fetch data. All HTTP remains in `MarketdataPage` via the existing `marketdataApi.listBars()` path.

## Quality And Freshness

- `qualityStatus` is rendered when present.
- `GAP_DETECTED` / degraded statuses are shown as non-blocking warning state.
- Missing `qualityStatus` is shown as a non-blocking informational state; the UI does not fabricate gap status.
- Stale state is derived when the last returned bar does not cover the submitted query end time by at least one interval.

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
| `npm run build` | PASS | TypeScript build and Vite production build completed; Vite retained existing large chunk warning. |
| `npm run test:e2e -- tests/e2e/marketdata-kline-readiness-smoke.spec.ts --project=chromium` | PASS | Backend-free Chromium smoke; mocked auth, account context and `/api/marketdata/bars`; verified initial empty state, K-line canvas, volume canvas, metadata and `GAP_DETECTED` quality display. |

Real backend bars E2E was not run in this task. The smoke is intentionally backend-free and does not call real exchange endpoints.
