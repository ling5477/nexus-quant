# NQ-GATEM-2I MarketData Positive Bars Fixture Smoke

> Task: `NQ-GATEM-2I-MARKETDATA-POSITIVE-BARS-FIXTURE-SMOKE`
> Type: `FRONTEND_E2E + TEST_FIXTURE_IMPLEMENTATION + REAL_BACKEND_SMOKE + MARKETDATA_READINESS_VALIDATION`
> Status: `PASS / IMPLEMENTED / READY FOR REVIEW`

## Scope

This task implements a controlled fake-bars fixture for MarketData real-backend positive smoke.

Allowed and used scope:

- `frontend/tests/e2e/marketdata-positive-bars-fixture.ts`
- `frontend/tests/e2e/marketdata-positive-bars-fixture-smoke.spec.ts`
- `docs/current/frontend/NQ_GATEM_2I_MARKETDATA_POSITIVE_BARS_FIXTURE_SMOKE.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

No backend code, migration, production API, workflow, research, scripts, deploy, TradingWorkbench, `MarketdataController`, bars/readiness query logic, real exchange adapter, RealClient, WebSocket, LIVE, AI, or DH runtime was modified.

## Fixture

Fixture identity:

- `exchangeCode = BINANCE`
- `marketType = SPOT`
- `symbol = BTC-USDT`
- `interval = 1m`
- `source = E2E_POSITIVE_FIXTURE`
- UTC window: `2025-01-01T00:00:00Z` to `2025-01-01T00:05:59Z`
- bar count: `6`
- `quality_status = OK`
- `raw_payload_json.fake = true`

The helper uses `psql` from Playwright test code and writes only to local test DB. It prepares rows with scoped cleanup before insert and removes the same scope/window/source in `finally`.

Cleanup predicate includes:

- `exchange_code`
- `market_type`
- `symbol`
- `"interval"`
- `source = E2E_POSITIVE_FIXTURE`
- fixed fixture window

## Validation

Local backend environment:

- Started `nq-app` local profile on `127.0.0.1:18888`.
- `/actuator/health` returned `UP`.
- Local PostgreSQL was reachable.
- Flyway reported schema version `31` and up to date.

Commands:

| Command | Result | Notes |
| --- | --- | --- |
| `git status --short` | PASS | Changes limited to allowed frontend E2E/docs paths. |
| `git diff --check` | PASS | No whitespace errors. |
| `git diff --stat` | PASS | Final scoped diff reviewed after docs update. |
| `cd frontend; npm run build` | PASS | TypeScript build + Vite production build passed; existing large chunk warning remains. |
| `cd frontend; npm run test:e2e -- tests/e2e/marketdata-positive-bars-fixture-smoke.spec.ts --project=chromium` | PASS | 1 Chromium test passed. |
| Scoped cleanup count query | PASS | `E2E_POSITIVE_FIXTURE` rows for the exact scope/window = `0` after test cleanup. |

Positive evidence from the passing smoke:

```text
source=E2E_POSITIVE_FIXTURE
scope=BINANCE/SPOT/BTC-USDT/1m
fixtureBars=6
preflightBars=6
readinessBarCount=6
readinessStatus=FRESH
freshnessStatus=FRESH
sourceHealthStatus=FRESH
```

The page used real backend requests for both:

- `/api/marketdata/bars`
- `/api/marketdata/readiness`

The smoke verified K-line canvas, volume canvas, Data Quality / Readiness area, bar count, last bar time, readiness status, freshness status, source health status, quality summary, and gap count.

## Validation Fixes During Implementation

Two test-only issues were found and fixed before the final PASS:

1. The first helper version used a data-modifying CTE and then counted the target table in the same statement. PostgreSQL uses the same statement snapshot, so the count returned `0`. The helper now performs `DELETE/INSERT` in a transaction and then runs a separate `SELECT COUNT`.
2. The first page run filled `DatePicker` with UTC-looking text. Ant Design submits local wall-clock values, so Asia/Shanghai converted that to the wrong UTC window. The helper now derives DatePicker input text from the fixed UTC fixture window so submitted page queries match the DB rows across local time zones.

## Boundary Confirmation

- No stub for `/api/marketdata/bars`.
- No stub for `/api/marketdata/readiness`.
- No real exchange call.
- No ingestion `run-once`.
- No credential material read or logged.
- No LIVE enablement.
- No AI or DH runtime.
- No RealClient, real provider, WebSocket, order, cancel, withdraw, or transfer path.
- No backend, migration, research, scripts, deploy, or workflow diff.
- Existing backend-free and empty/no-data real-backend smoke files remain in place.

## Final Decision

`NQ-GATEM-2I-MARKETDATA-POSITIVE-BARS-FIXTURE-SMOKE` is `PASS / IMPLEMENTED / READY FOR REVIEW`.

Recommended commit:

```text
test(frontend): add marketdata positive bars smoke
```
