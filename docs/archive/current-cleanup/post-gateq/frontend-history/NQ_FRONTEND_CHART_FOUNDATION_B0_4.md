# NQ Frontend Chart Foundation B0.4

> Task: `NQ-FRONTEND-CHART-FOUNDATION-B0.4`
> Type: `FRONTEND_UI + DESIGN_SYSTEM_COMPONENTS + CHART_FOUNDATION + TEST_REVIEW`
> Status: `IMPLEMENTED / READY FOR REVIEW`
> Scope: frontend design-system chart foundation and `/dev/design-system` self-check only.

## Authoritative Scope

B0.4 adds the NQ Console chart foundation for K-line and volume rendering with `lightweight-charts`.

Implemented surface:

- `NqKlineChart`: candlestick chart component for caller-supplied internal bar data.
- `NqVolumeChart`: histogram volume chart component for caller-supplied internal bar data.
- `nqLwcOptions`: shared Lightweight Charts chrome/theme options sourced from NQ design tokens.
- `/dev/design-system`: static mock self-check section for chart rendering, loading, empty, error and stale states.
- `design-system-chart-smoke.spec.ts`: no-backend Chromium smoke covering canvas rendering, states and market color convention switch.

## Data Contract

The chart components accept `NqKlineBar`:

```text
time, open, high, low, close, optional volume, optional qualityStatus
```

The component contract is intentionally frontend-local. It does not bind to any backend DTO, external exchange payload, WebSocket payload, order model, position model, or credential-bearing object.

## State Model

The B0.4 chart components expose visible states:

- `loading`: loading placeholder, no chart creation.
- `empty`: explicit no-data placeholder, no fabricated series.
- `error`: visible error placeholder.
- `stale`: `DataFreshness` marker with source label and detail.

## Color Convention

K-line and volume direction colors use NQ market tokens:

- default `INTL_CRYPTO`: green up / red down.
- optional `CN_STOCK`: red up / green down.

These colors remain independent of `success` / `danger`; danger is still reserved for risk, failure and LIVE-sensitive operations.

## Boundary Confirmation

B0.4 does not implement or enable:

- backend API changes.
- MarketdataController changes.
- TradingWorkbench changes.
- real exchange adapter / RealClient / real provider.
- credential read or output.
- WebSocket or real-time transport.
- order, cancel, withdraw or transfer behavior.
- LIVE trading.
- AI or DH runtime integration.

The `/dev/design-system` demo uses static mock bars only.

## Validation

Executed locally on 2026-06-29:

| Command | Result | Notes |
| --- | --- | --- |
| `npm run build` | PASS | TypeScript build and Vite production build completed. Vite reported existing large chunk warning. |
| `npm run test:e2e -- tests/e2e/design-system-chart-smoke.spec.ts --project=chromium` | PASS | 1 Chromium smoke passed; Vite dev route only, no backend. |

## Next Boundary

Future Market Data or Trading Workbench adoption must be a separate task. It must explicitly define data freshness, API contract, no-real boundary and failure behavior before wiring real page data into these components.
