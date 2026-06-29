# NQ GateM-2D MarketData Source Health Plan

> Task: `NQ-GATEM-2D-MARKETDATA-SOURCE-HEALTH-PLAN`
> Type: `BACKEND_PLANNING + MARKETDATA_READINESS + API_BOUNDARY_REVIEW + DOCUMENTATION`
> Status: `PLAN ONLY / NOT IMPLEMENTED`
> Scope: MarketData source health / freshness / gap / ingestion readiness backend aggregation planning only.

## 1. Current state

GateM current authoritative definition is **Exchange / MarketData Runtime Readiness**. This plan only covers MarketData source health/readiness aggregation. It does not implement code, API, migration, frontend changes, real exchange access, LIVE, AI, DH runtime, RealClient, real provider, WebSocket, order, cancel, withdraw or transfer behavior.

Relevant completed facts:

- `NQ-FRONTEND-CHART-FOUNDATION-B0.4` is completed.
- `NQ-GATEM-2-MARKETDATA-KLINE-READINESS` is completed as a frontend page slice using existing `/api/marketdata/bars`.
- `NQ-GATEM-2B-MARKETDATA-QUALITY-READINESS-VIEW` is completed as a frontend-only data quality/freshness/gap expression. Source health is explicitly `Pending backend support`.
- `NQ-GATEM-2C-MARKETDATA-REAL-BACKEND-SMOKE` is completed as `PASS / EMPTY-NO-DATA REAL BACKEND SMOKE`.
- Local real backend in 2C started, `/actuator/health = UP`, Flyway schema version `31` was up to date, and the MarketData page hit real `/api/marketdata/bars`.
- Current local DB had no queryable bars in the tested fixed GateH/GateM dimensions, so positive bars fixture remains a separate pending task.

Current hard boundaries:

- LIVE remains `DISABLED`.
- AI remains `NOT STARTED`.
- DH runtime remains `NOT INTEGRATED`.
- Real exchange adapter / RealClient / real provider remains `NOT IMPLEMENTED`.
- Existing OKX/Binance historical kline adapter code is legacy network-capable code and must not be called by a source health read endpoint.

## 2. Existing backend data model

Existing tables that can support a no-migration MVP:

### `marketdata_bars`

Current fields relevant to readiness:

- Identity: `exchange_code`, `market_type`, `symbol`, `"interval"`.
- Time: `open_time`, `close_time`, `ingested_at`.
- OHLCV: `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `quote_volume`, `trade_count`.
- Source / quality: `source`, `quality_status`, `raw_payload_json`.
- Unique key: `exchange_code + market_type + symbol + interval + open_time`.
- Query index: `idx_marketdata_bars_scope_time_desc` on `exchange_code, market_type, symbol, interval, open_time DESC`.

`marketdata_bars` can provide `barCount`, `firstBarTime`, `lastBarTime`, bar-level `qualityStatus` counts and source value. It does not store source-level health, disabled reason, error code, or source-specific freshness policy.

### `marketdata_ingestion_jobs`

Current fields relevant to readiness:

- Identity and scope: `job_id`, `exchange_code`, `market_type`, `symbol`, `"interval"`, `start_time`, `end_time`.
- State: `status`.
- Source/audit: `source`, `created_by`, `created_at`, `updated_at`, `request_json`.

`updated_at` can explain the latest job state for a scope, but it is not a dedicated `last_success_at` or `last_failure_at`.

### `marketdata_ingestion_runs`

Current fields relevant to readiness:

- Identity: `run_id`, `job_id`.
- State: `status`.
- Time: `started_at`, `finished_at`, `requested_start_time`, `requested_end_time`, `actual_start_time`, `actual_end_time`, `created_at`.
- Counts: `fetched_bars`, `inserted_bars`, `updated_bars`, `skipped_bars`.
- Failure/debug: `error_message`, `raw_summary_json`.

MVP can derive:

- `lastSuccessAt`: latest `finished_at` from runs with `status in ('SUCCEEDED', 'PARTIAL')` for matching job scope.
- `lastFailureAt`: latest `finished_at` from runs with `status = 'FAILED'` for matching job scope.
- `latestLatencyMs`: `finished_at - started_at` when both exist.
- `sourceHealthReason`: sanitized status/reason text derived from latest run/job facts.

MVP cannot reliably return:

- persisted `latency_ms`.
- structured `error_code`.
- `disabled_reason`.
- source-specific policy metadata.

### `marketdata_datasets` and `marketdata_dataset_coverage`

Current fields relevant to readiness:

- Dataset: `status`, `quality_status`, `bar_count`, `gap_count`, `source`, `updated_at`.
- Coverage: `expected_bars`, `actual_bars`, `missing_bars`, `duplicate_bars`, `invalid_bars`, `quality_status`, `summary_json`, `created_at`.

These tables can explain dataset-level quality only after a dataset/coverage exists or after `refresh-quality` has run. They should be treated as supporting evidence, not as the only source of runtime source health.

## 3. Existing API capability

Current implemented MarketData APIs:

- `GET /api/marketdata/bars`
- `POST /api/marketdata/ingestion-jobs`
- `GET /api/marketdata/ingestion-jobs`
- `GET /api/marketdata/ingestion-jobs/{jobId}`
- `GET /api/marketdata/ingestion-jobs/{jobId}/runs`
- `POST /api/marketdata/ingestion-jobs/{jobId}/run-once`
- `GET /api/marketdata/datasets`
- `POST /api/marketdata/datasets`
- `GET /api/marketdata/datasets/{datasetId}`
- `POST /api/marketdata/datasets/{datasetId}/refresh-quality`

Current `/api/marketdata/bars` response fields:

- `exchangeCode`
- `marketType`
- `symbol`
- `interval`
- `openTime`
- `closeTime`
- `openPrice`
- `highPrice`
- `lowPrice`
- `closePrice`
- `volume`
- `quoteVolume`
- `tradeCount`
- `qualityStatus`

Current gaps:

- No `GET /api/marketdata/readiness`.
- No `GET /api/marketdata/source-health`.
- No `GET /api/marketdata/quality/overview`.
- No API returns source health state, source health reason, backend support level, first/last bar aggregate, expected bar count, aggregated gap count, latest success/failure run timestamps, latest latency, disabled reason or generated time in one response.

## 4. Frontend gap

`MarketdataPage` already has:

- K-line readiness view.
- Volume chart.
- Data Quality / Readiness section.
- Bars table.
- Ingestion jobs/runs tables.
- Dataset table.
- loading / empty / error / stale / gap / unknown-quality states.

The page currently derives quality/freshness/gap from the submitted bars query and returned `MarketdataBar[]`.

Missing backend fields for real source health:

- source-level status (`FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, `UNKNOWN`, `NO_DATA`).
- source health reason.
- latest source success/failure timestamps by scope.
- latest ingestion latency by scope.
- source-specific freshness policy.
- disabled reason.
- expected bar count from backend.
- server-side gap count for the requested scope.
- support level that tells the page whether the backend is returning MVP, unavailable, or future persistent source health.

The current UI phrase `Pending backend support` is correct and must not be changed to `READY` until a backend readiness endpoint exists and is verified.

## 5. Proposed source health model

Recommended source health dimensions:

| Dimension | MVP support | Notes |
| --- | --- | --- |
| exchange health | Partial | Scope by `exchangeCode`; do not probe real exchange. Derive only from local DB bars/jobs/runs. |
| symbol/instrument health | Yes | Current backend canonical field is `symbol`; do not introduce `instrumentId` until an instrument-catalog join is explicitly implemented. |
| timeframe health | Yes | Scope by `interval`. |
| dataset health | Partial | Use existing datasets/coverage when available; not required for source readiness MVP. |
| ingestion job health | Yes | Derive latest job/run status for matching exchange/market/symbol/interval. |

Status set:

- `FRESH`: bars exist and latest bar is within freshness policy.
- `STALE`: bars exist but latest bar is older than freshness policy.
- `GAP`: expected range is calculable and missing/gap evidence exists.
- `ERROR`: latest relevant ingestion run failed, or quality evidence is invalid.
- `DISABLED`: future persistent source configuration says the source is disabled. MVP should normally not emit this unless a clear no-real/fail-closed source state is explicitly represented in local config.
- `UNKNOWN`: insufficient local facts to decide; must never be interpreted as ready.
- `NO_DATA`: no bars exist for the requested scope.

Recommended top-level response fields:

- `exchangeCode`
- `marketType`
- `symbol`
- `interval`
- `status`
- `freshnessStatus`
- `qualityStatusSummary`
- `barCount`
- `firstBarTime`
- `lastBarTime`
- `expectedBarCount`
- `gapCount`
- `unknownQualityCount`
- `lastSuccessAt`
- `lastFailureAt`
- `latestLatencyMs`
- `sourceHealthStatus`
- `sourceHealthReason`
- `backendSupportLevel`
- `generatedAt`

Recommended `backendSupportLevel` values:

- `NO_MIGRATION_MVP`: computed from existing tables only.
- `UNAVAILABLE`: backend cannot compute stable readiness for the supplied scope.
- `FUTURE_PERSISTED_SOURCE_HEALTH_REQUIRED`: requires future migration/source-health table.

## 6. Proposed freshness model

MVP inputs:

- latest `marketdata_bars.close_time` for `exchangeCode + marketType + symbol + interval`.
- interval duration from existing `BarInterval`.
- optional query `startTime/endTime`.
- generated server time `generatedAt`.
- latest matching ingestion run status as explanatory metadata only.

Recommended calculation:

1. If no bars exist, return `freshnessStatus = NO_DATA`.
2. If interval cannot be parsed, return `freshnessStatus = UNKNOWN`.
3. If `endTime` is supplied, calculate query-window coverage first:
   - `lastBarTime + interval >= endTime` means the returned/local range reaches the requested window end.
   - otherwise mark query coverage stale/incomplete.
4. For source-level current freshness, compare `generatedAt - lastBarTime` against a conservative policy:
   - MVP default: `freshnessThreshold = max(2 * interval, 5 minutes)`.
   - Result `FRESH` only when `lastBarTime` is inside the threshold.
   - Result `STALE` otherwise.
5. Do not use ingestion run success alone to mark data `FRESH`; a successful run can still insert zero bars or end before the current expected time.
6. Future source-specific policy can make the threshold configurable per exchange/symbol/interval, but that is outside the no-migration MVP.

## 7. Proposed gap model

MVP inputs:

- `startTime/endTime` when supplied.
- interval duration.
- count of bars in the requested scope.
- first/last bar times.
- `quality_status` distribution in `marketdata_bars`.
- optional latest dataset coverage if a matching dataset exists.

Recommended calculation:

1. If `startTime/endTime` and interval are present:
   - `expectedBarCount = floor((endTime - startTime) / interval) + 1`.
   - `actualBarCount = COUNT(*)` for bars in the same scope where `open_time >= startTime` and `close_time <= endTime`.
   - `sequenceGapCount = max(0, expectedBarCount - actualBarCount)`.
2. Count bar-level quality gap signals:
   - `qualityGapSignalCount = count(quality_status in ('GAP_DETECTED', 'INCOMPLETE'))`.
   - `invalidQualityCount = count(quality_status not in ('OK', 'GAP_DETECTED', 'INCOMPLETE') or invalid OHLCV values)`.
   - `unknownQualityCount = count(quality_status is null or blank)`; current DB default is `OK`, but response and future imports must remain defensive.
3. Recommended response:
   - `gapCount = max(sequenceGapCount, qualityGapSignalCount)` to avoid double-counting the same gap.
   - include component counts under `qualityStatusSummary`.
4. If the time window is absent or expected count cannot be computed:
   - use quality signals only.
   - return `expectedBarCount = null`.
   - return `gapCount = null` or `UNKNOWN` reason when gap is unavailable.
5. Dataset coverage can be included as `datasetEvidence` in a future version, but MVP should not require a dataset row to compute source readiness.

## 8. Proposed API contract draft

Recommended MVP endpoint:

```http
GET /api/marketdata/readiness?exchangeCode=BINANCE&marketType=SPOT&symbol=BTC-USDT&interval=1m&startTime=2026-06-29T00:00:00Z&endTime=2026-06-29T01:00:00Z
```

Reason for choosing `/api/marketdata/readiness`:

- It covers freshness, gap, quality and ingestion readiness in one read model.
- It avoids implying that the endpoint probes a live exchange source.
- It leaves `/api/marketdata/source-health` available for a future persisted source-health resource if a migration is approved.

Query fields:

| Field | Required | Notes |
| --- | --- | --- |
| `exchangeCode` | yes | Current backend convention; allowed current values remain `OKX / BINANCE`. |
| `marketType` | yes, defaultable to `SPOT` | Keep existing API convention. |
| `symbol` | yes | Current backend symbol field; do not use `instrumentId` in MVP. |
| `interval` | yes | Existing `BarInterval` wire values. |
| `startTime` | optional | Enables query-window coverage/gap calculation. |
| `endTime` | optional | Enables query-window coverage/gap calculation. |

Response draft:

```json
{
  "exchangeCode": "BINANCE",
  "marketType": "SPOT",
  "symbol": "BTC-USDT",
  "interval": "1m",
  "status": "STALE",
  "freshnessStatus": "STALE",
  "qualityStatusSummary": {
    "okCount": 0,
    "gapSignalCount": 0,
    "invalidCount": 0,
    "unknownQualityCount": 0,
    "statuses": []
  },
  "barCount": 0,
  "firstBarTime": null,
  "lastBarTime": null,
  "expectedBarCount": 61,
  "gapCount": 61,
  "lastSuccessAt": null,
  "lastFailureAt": null,
  "latestLatencyMs": null,
  "sourceHealthStatus": "NO_DATA",
  "sourceHealthReason": "No local bars found for the requested scope; backend did not call external exchange.",
  "backendSupportLevel": "NO_MIGRATION_MVP",
  "generatedAt": "2026-06-29T00:00:00Z"
}
```

HTTP behavior:

- `200` with stable readiness payload for valid supported scope, including `NO_DATA` and `UNKNOWN`.
- `400` for invalid interval/time range/scope.
- `401/403` follows existing authenticated `/api/**` behavior.
- Do not expose stack traces, SQL, adapter details, credentials or raw provider response.

## 9. No-migration MVP option

Recommended implementation path for the next backend task:

1. Add a backend read model and service, for example:
   - `MarketdataReadinessService`
   - `MarketdataReadinessSummary`
   - `MarketdataReadinessRepository` port
   - `JdbcMarketdataReadinessRepository`
   - `MarketdataReadinessController` or a small addition under marketdata API package.
2. Add one read-only endpoint: `GET /api/marketdata/readiness`.
3. Use only existing tables:
   - `marketdata_bars`
   - `marketdata_ingestion_jobs`
   - `marketdata_ingestion_runs`
   - optionally `marketdata_datasets` / `marketdata_dataset_coverage` as supporting evidence.
4. Use bounded aggregate SQL by `exchange_code + market_type + symbol + interval` and optional `startTime/endTime`.
5. Never call:
   - `HistoricalKlineProvider`
   - `HistoricalKlineAdapter`
   - OKX / Binance / Bybit / Gate / Coinbase / Kraken external endpoint
   - WebSocket
   - trading adapter
   - credential repository/material path
6. Return `UNKNOWN` or `NO_DATA`, not `READY`, when evidence is insufficient.

No-migration MVP advantages:

- Fastest path to remove frontend `Pending backend support`.
- No schema lock or backfill risk.
- Uses current indexes for bounded scope/time aggregates.
- Keeps source health as a read model, not a scheduler/prober.

MVP limitations:

- No persisted source status.
- No source-specific disabled reason.
- No structured error code.
- No durable latency history beyond derived latest run duration.
- Freshness policy is conservative and hardcoded until future configuration is approved.

## 10. Migration-required future option

Only if MVP is insufficient, plan a separate migration-backed source health table.

Possible table: `marketdata_source_health_snapshots`

Candidate fields:

- `source_health_id`
- `exchange_code`
- `market_type`
- `symbol`
- `"interval"`
- `status`
- `freshness_status`
- `quality_status`
- `last_bar_time`
- `last_success_at`
- `last_failure_at`
- `latest_latency_ms`
- `last_error_code`
- `disabled_reason`
- `policy_json`
- `generated_at`
- `created_at`

Migration requirements:

- New Flyway migration only; never modify historical migration.
- `COMMENT ON TABLE` and `COMMENT ON COLUMN` for every new table/field.
- Explicit status CHECK constraints.
- JSONB comments must state that credentials, tokens, cookies, secrets and raw provider responses are forbidden.
- Index by `exchange_code, market_type, symbol, interval, generated_at DESC`.
- Separate review via DB schema migration skill/review before implementation.

Future table should still be read-only from the API perspective and must not trigger exchange calls.

## 11. Security boundary

The proposed readiness API must be:

- read-only.
- authenticated under existing `/api/**` security.
- local DB aggregation only.
- fail-closed.
- bounded by scope and optional time range.
- free of credentials, token, cookie, secret, passphrase, private key, mnemonic, raw signature, raw request header and raw provider response.

The endpoint must not:

- create or run ingestion jobs.
- trigger `run-once`.
- call `HistoricalKlineProvider`.
- call OKX / Binance / Bybit / Gate / Coinbase / Kraken.
- call private adapter endpoints.
- enable LIVE.
- connect AI or DH runtime.
- expose stack traces or SQL.
- return `READY` for `NO_DATA`, `UNKNOWN`, `DISABLED`, `ERROR` or `STALE`.

## 12. Testing strategy

Backend unit tests:

- status resolver: `NO_DATA`, `UNKNOWN`, `FRESH`, `STALE`, `GAP`, `ERROR`.
- freshness resolver with interval thresholds.
- gap resolver with expected/actual count, quality gap signal and unknown quality cases.
- no external adapter call invariant using mocks.

Repository tests:

- no bars and no jobs returns `NO_DATA`.
- bars present and within policy returns `FRESH`.
- bars present but outside policy returns `STALE`.
- missing interval sequence returns `GAP`.
- failed latest ingestion run returns `ERROR` reason while not overwriting bar facts.
- query is bounded and uses existing scope/time index shape.

Controller tests:

- `GET /api/marketdata/readiness` happy path.
- empty/no-data response shape.
- invalid interval/time range returns controlled `400`.
- response does not contain raw payload, credential, token, secret, private key, passphrase or stack trace text.

Frontend follow-up tests:

- After backend 2E exists, wire `MarketdataPage` to the new readiness endpoint.
- Keep existing bars-derived summary as a fallback display only when readiness endpoint is unavailable.
- Assert source health no longer says `Pending backend support` when API returns a real readiness payload.
- Preserve current no-backend and real-backend smoke boundaries.

Validation commands for implementation task:

```powershell
mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra,nq-app -am test
Set-Location frontend
npm run build
npm run test:e2e -- tests/e2e/marketdata-quality-readiness-smoke.spec.ts --project=chromium
```

For this planning-only task, validation is docs/diff scope only.

## 13. P0/P1/P2/P3 risks

### P0

- None in this planning-only task.

### P1

- A future readiness endpoint could accidentally call legacy network-capable OKX/Binance historical adapter code. Mitigation: repository/service must aggregate local DB only and tests must assert no provider call.
- A future API could label `NO_DATA` or `UNKNOWN` as ready. Mitigation: stable fail-closed status resolver and tests.

### P2

- Gap calculation can be wrong if expected bar count uses inconsistent inclusive/exclusive range semantics. Mitigation: mirror existing dataset coverage formula intentionally, document it and test boundary times.
- Unbounded aggregate queries can become expensive. Mitigation: require `exchangeCode + marketType + symbol + interval`, cap windows for detailed gap checks, and use existing scope/time index.
- `lastSuccessAt` and `lastFailureAt` are derived from runs, not dedicated columns. Mitigation: expose them as derived fields and keep `backendSupportLevel = NO_MIGRATION_MVP`.
- Source-specific freshness policy is not persisted. Mitigation: use conservative default policy and mark future policy migration separately.

### P3

- API naming could drift between `instrumentId` and current `symbol`. Recommendation: MVP uses `symbol`; add `instrumentId` only in a later instrument-catalog join task.
- Positive bars real-backend smoke is still pending. Recommendation: separate controlled fixture task using existing approved seed/test mechanism.

## 14. Recommended next implementation task

Recommended next task:

`NQ-GATEM-2E-MARKETDATA-SOURCE-HEALTH-BACKEND-MVP`

Scope:

- Implement `GET /api/marketdata/readiness`.
- Add backend DTO/service/repository tests.
- Use no migration.
- Aggregate only existing DB facts.
- Do not modify frontend in 2E except possibly generated API docs if the repo requires it.
- Do not trigger ingestion, external exchange calls, WebSocket, trading actions, LIVE, AI or DH runtime.

Suggested follow-up tasks:

- `NQ-GATEM-2F-MARKETDATA-SOURCE-HEALTH-FRONTEND-WIRING`: consume the new readiness API in `MarketdataPage`.
- `NQ-GATEM-2G-MARKETDATA-POSITIVE-BARS-FIXTURE-SMOKE`: add controlled positive bars fixture smoke through an existing approved test/seed mechanism.
- `NQ-GATEM-2H-MARKETDATA-SOURCE-HEALTH-PERSISTENCE-PLAN`: only if MVP proves insufficient and a migration-backed source health table is justified.

## Final decision

Proceed with a no-migration backend MVP in the next implementation task. The MVP should add one read-only readiness endpoint backed by existing bars, ingestion job/run and optional dataset coverage facts. It must never call external exchanges or mark missing evidence as ready.

This 2D task remains `PLAN ONLY / NOT IMPLEMENTED`.
