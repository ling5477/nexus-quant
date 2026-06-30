# NQ GateM-2H MarketData Positive Bars Fixture Plan

> Task: `NQ-GATEM-2H-MARKETDATA-POSITIVE-BARS-FIXTURE-PLAN`
> Type: `TEST_FIXTURE_PLANNING + MARKETDATA_READINESS + E2E_STRATEGY_REVIEW + DOCUMENTATION`
> Status: `PLAN ONLY / NOT IMPLEMENTED`
> Scope: Plan a controlled positive bars fixture for MarketData real-backend smoke. No fixture implementation in this task.

## 1. Current state

Current GateM authoritative definition remains **Exchange / MarketData Runtime Readiness**. This task is planning-only for a future MarketData positive bars smoke fixture.

Completed facts already accepted before this plan:

- GateM-2E backend MVP added read-only `GET /api/marketdata/readiness`.
- GateM-2F wired the MarketData page to call `/api/marketdata/readiness`.
- GateM-2G real-backend smoke passed the empty/no-data branch against a real local backend.
- 2G verified `/actuator/health = UP`, real page requests to `/api/marketdata/bars`, real page requests to `/api/marketdata/readiness`, and `preflightBars=0 / readinessBarCount=0 / readinessStatus=NO_DATA`.
- Positive bars fixture remains pending.

Current hard boundaries:

- LIVE remains `DISABLED`.
- AI remains `NOT STARTED`.
- DH runtime remains `NOT INTEGRATED`.
- Real exchange adapter / RealClient / real provider remains `NOT IMPLEMENTED`.
- Existing OKX/Binance legacy network-capable code must not be used to prepare this fixture.

This plan does not implement fixture code, add migration, modify backend Java, modify frontend TypeScript, modify scripts/deploy/CI, call a real exchange, or insert real market data.

## 2. Files inspected

Read-only inspection covered:

- `backend/nq-infra/src/main/resources/db/migration/V13__rc1_marketdata_bars.sql`
- `backend/nq-infra/src/main/resources/db/migration/V16__gate_h2_marketdata_ingestion.sql`
- `backend/nq-infra/src/main/resources/db/migration/V18__gate_h3_marketdata_dataset_binding.sql`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataController.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/MarketdataBarIngestService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/FixtureMarketdataRegistry.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/MarketdataReadinessService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataReadinessQuery.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/HistoricalBar.java`
- `backend/nq-core/src/main/resources/backtest/fixtures/btcusdt_1m_sample.csv`
- `backend/nq-core/src/main/resources/backtest/fixtures/ethusdt_1m_sample.csv`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcHistoricalMarketDataPort.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataBarRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataReadinessRepository.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/AuthSeedConfiguration.java`
- `backend/nq-app/src/main/resources/application-local.yml`
- `backend/nq-app/src/main/resources/application-test.yml`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/MarketdataControllerLocalIntegrationTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/application/MarketdataReadinessServiceTest.java`
- `frontend/src/constants/filter-options.ts`
- `frontend/src/pages/marketdata/MarketdataPage.tsx`
- `frontend/src/types/marketdata.ts`
- `frontend/tests/e2e/support.ts`
- `frontend/tests/e2e/gatei2-fixtures.ts`
- `frontend/tests/e2e/paper-trading-fixtures.ts`
- `frontend/tests/e2e/marketdata-real-backend-smoke.spec.ts`
- `frontend/tests/e2e/marketdata-readiness-real-backend-smoke.spec.ts`
- `docs/current/frontend/NQ_GATEM_2C_MARKETDATA_REAL_BACKEND_SMOKE.md`
- `docs/current/NQ_GATEM_2D_MARKETDATA_SOURCE_HEALTH_PLAN.md`
- `docs/current/frontend/NQ_GATEM_2G_MARKETDATA_READINESS_REAL_BACKEND_SMOKE.md`
- `docs/current/API.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 3. Existing DB schema support

### `marketdata_bars`

Existing schema already supports a deterministic positive bars fixture without migration.

Required identity fields:

- `exchange_code`
- `market_type`
- `symbol`
- `"interval"`
- `open_time`

Required bar payload fields:

- `close_time`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `volume`
- `source`
- `ingested_at`

Optional/enriched fields from GateH-2:

- `quote_volume`
- `trade_count`
- `quality_status`
- `raw_payload_json`

Important constraints and indexes:

- Unique key: `exchange_code + market_type + symbol + interval + open_time`.
- Query index: `idx_marketdata_bars_scope_time_desc` on `exchange_code, market_type, symbol, interval, open_time DESC`.
- `marketdata_bars` has no symbol CHECK constraint, so it can store current UI canonical symbols such as `BTC-USDT`.

No migration is needed for the future positive fixture if it only writes a small deterministic row set to `marketdata_bars` using existing fields.

### `marketdata_ingestion_jobs` and `marketdata_ingestion_runs`

Readiness aggregation uses these tables as optional ingestion facts:

- `lastSuccessAt`: latest finished `SUCCEEDED` or `PARTIAL` run for the scope.
- `lastFailureAt`: latest finished `FAILED` run for the scope.
- `latestRunStatus` and latency: derived from latest run metadata.

The positive bars smoke does not need to create ingestion jobs/runs for minimum acceptance. If a future implementation wants readiness to show `lastSuccessAt`, it may insert a matching fake local run only if that remains test-only and clearly labeled. That is not required for the first positive bars fixture.

### `marketdata_datasets` and `marketdata_dataset_coverage`

Dataset tables are not required for `/api/marketdata/bars` or `/api/marketdata/readiness` positive smoke. They can be left untouched.

## 4. Current query dimensions

`GET /api/marketdata/bars` currently requires:

- `exchangeCode`
- `marketType` with default `SPOT`
- `symbol`
- `interval`
- `startTime`
- `endTime`
- `page`
- `size`

The JDBC query filters by:

```text
exchange_code = ?
market_type = ?
symbol = ?
"interval" = ?
open_time >= ?
close_time <= ?
```

`GET /api/marketdata/readiness` currently requires:

- `exchangeCode`
- `marketType` with default `SPOT`
- `symbol` or `instrumentId`
- `interval`
- optional `from`
- optional `to`

Readiness aggregation filters bars by:

```text
exchange_code = ?
market_type = ?
symbol = ?
"interval" = ?
open_time >= from
close_time <= to
```

It also loads ingestion facts from `marketdata_ingestion_jobs/runs` for the same exchange/market/symbol/interval scope.

## 5. Existing fixture capability

Existing controlled fixture pieces:

- `FixtureMarketdataRegistry` registers `BINANCE_BTCUSDT_1M_SAMPLE` and `BINANCE_ETHUSDT_1M_SAMPLE`.
- `MarketdataBarIngestService` reads the registered CSV fixture and upserts rows to `marketdata_bars`.
- `POST /api/marketdata/bars/ingestions/fixture` exposes explicit authenticated fixture ingest.
- `MarketdataControllerLocalIntegrationTest` proves fixture ingest plus `/api/marketdata/bars` can return positive DB rows.
- `frontend/tests/e2e/gatei2-fixtures.ts` and `frontend/tests/e2e/paper-trading-fixtures.ts` already call the existing fixture ingest endpoint for local E2E setup.

Existing limitations for GateM-2H:

- Registered CSV fixtures use legacy symbols `BTCUSDT` / `ETHUSDT`.
- Current MarketData page symbol options use canonical UI symbols `BTC-USDT` / `ETH-USDT` / `SOL-USDT`.
- 2G real-backend smoke queries UI-supported dimensions, for example `BINANCE / SPOT / BTC-USDT / 1m`.
- Existing fixture ingest endpoint enforces requested scope equals the registered dataset scope, so it cannot create `BTC-USDT` rows from `BINANCE_BTCUSDT_1M_SAMPLE` without code changes.
- Current 2G query window ends at `2026-12-31T23:59:59Z`; the existing six-row fixture covers only `2025-01-01T00:00:00Z` to `2025-01-01T00:05:59Z`, so a future positive smoke must query the fixture window if it expects `FRESH` and `gapCount=0`.

Conclusion: the repo has a useful fixture ingest mechanism, but it does not by itself satisfy the current UI canonical positive branch. A future implementation must make the fixture rows, UI query symbol, and readiness query window match exactly.

## 6. Local/test seed capability

Current profile behavior:

- `application-local.yml` enables Flyway and starts the app on port `18888`.
- `application-test.yml` keeps Flyway disabled by default.
- `AuthSeedConfiguration` runs under `local` and `test`, seeding configured users only.
- No local/test profile currently auto-seeds `marketdata_bars`.
- `frontend/tests/e2e/support.ts` logs in and ensures local SIM account fixtures through existing API calls.

Seed boundary:

- Do not add long-lived MarketData bars to local/test startup seed.
- Do not tie positive bars to `AuthSeedConfiguration`.
- Do not reuse CI Batch 2D/2E app-context seed paths.
- Do not modify `.github/workflows` or CI Postgres/Flyway smoke seed behavior.
- Any positive bars fixture must be explicit per E2E run, test-only, deterministic, and either cleaned up or made harmless by unique scope/window/source labeling.

## 7. Candidate approaches

### A. E2E prepares bars through a test-only DB fixture helper

Summary: before the positive smoke opens the page, a test-only helper writes a tiny deterministic row set directly to `marketdata_bars` for the exact UI-supported scope, for example `BINANCE / SPOT / BTC-USDT / 1m` and `2025-01-01T00:00:00Z` to `2025-01-01T00:05:59Z`.

Recommended row shape:

- `exchange_code = BINANCE`
- `market_type = SPOT`
- `symbol = BTC-USDT`
- `"interval" = 1m`
- six continuous rows, one per minute.
- `quality_status = OK`
- `source = E2E_POSITIVE_FIXTURE`
- `raw_payload_json` contains a non-sensitive fixture marker such as task name and `fake=true`.

Pros:

- Matches current MarketData page options and 2G positive branch directly.
- No production API addition.
- No migration.
- No external exchange call.
- No dependency on legacy `BTCUSDT` fixture symbol.
- Easy to keep isolated to local/test E2E.

Risks:

- E2E becomes coupled to DB setup and local DB connection details.
- Requires a narrow helper and cleanup policy.
- Must not be confused with real exchange data.

Mitigations:

- Use a distinct `source` value and fixed test window.
- Use idempotent upsert on the existing unique key.
- Delete only rows matching `source = E2E_POSITIVE_FIXTURE` and the exact test scope/window during cleanup.
- If a DB write helper requires new dependencies or unsafe credentials, stop and re-plan before implementation.

### B. Backend test profile auto-seeds deterministic MarketData bars

Summary: add automatic MarketData bars seed under a local/test profile.

Pros:

- Simple for browser tests after app startup.
- Reuses real backend without per-spec setup.

Risks:

- Pollutes every local/test run.
- Blurs fixture state with long-lived application seed.
- Could interact badly with `AuthSeedConfiguration` and CI app-context smoke boundaries.
- Harder to prove a positive branch is prepared only for the current E2E.

Decision: not recommended for 2H.

### C. Use existing public API to create dataset or bars

Summary: use existing authenticated API calls only.

Existing API facts:

- `POST /api/marketdata/bars/ingestions/fixture` can insert registered fixture rows.
- `POST /api/marketdata/datasets` creates dataset metadata and coverage from existing bars; it does not create bars.
- `POST /api/marketdata/ingestion-jobs/{jobId}/run-once` can trigger ingestion and must not be used for this fixture because it may call legacy network-capable provider paths.

Pros:

- Closest to current user/API path.
- Already used by GateI E2E fixture setup.

Risks:

- Current registered fixture symbols are `BTCUSDT` / `ETHUSDT`, while the MarketData page and 2G smoke use `BTC-USDT` / `ETH-USDT`.
- Dataset API cannot create bars.
- Expanding production fixture ingest just for a smoke risks turning a test need into API behavior.

Decision: conditionally useful but insufficient as-is for the current UI positive branch. Do not add a new production API only for this test.

### D. Continue only empty/no-data real-backend smoke

Summary: leave 2G as the only real-backend smoke and accept no-data branch.

Pros:

- Safest and already proven.
- No DB writes.
- No fixture identity risk.

Risks:

- Does not cover positive K-line canvas rendering against the real backend.
- Does not prove readiness positive summary with `barCount > 0`.
- Leaves 2G's positive branch dependent on accidental local DB state.

Decision: acceptable as current baseline, but does not satisfy the positive-bars readiness goal.

## 8. Recommended fixture approach

Recommended path: **A, with a new narrow test-only fixture helper that writes canonical UI-scope bars directly to the local test DB**.

Implementation rules for the next task:

1. Keep all fixture code test-only.
2. Do not modify `MarketdataController`.
3. Do not add production API.
4. Do not add migration.
5. Do not call ingestion `run-once`.
6. Do not call OKX / Binance / Bybit / Gate / Coinbase / Kraken.
7. Do not read credential material.
8. Do not enable LIVE.
9. Insert only deterministic fake OHLCV bars for one exact scope/window.
10. Query the same exact scope/window from the page.

Recommended fixture scope:

```text
exchangeCode = BINANCE
marketType   = SPOT
symbol       = BTC-USDT
interval     = 1m
from         = 2025-01-01T00:00:00Z
to           = 2025-01-01T00:05:59Z
barCount     = 6
source       = E2E_POSITIVE_FIXTURE
```

Expected readiness result for the complete row set:

- `barCount > 0`
- `expectedBarCount = 6`
- `gapCount = 0`
- `qualityStatusSummary.okCount = 6`
- `unknownQualityCount = 0`
- `status = FRESH` when the query `to` equals the fixture window end.
- `freshnessStatus = FRESH` for the same bounded query.
- `sourceHealthStatus = FRESH` under the current 2E response model.

If future implementation needs a migration to prepare these rows, stop. The existing schema is sufficient; a migration would mean the fixture plan is crossing into product data modeling and needs a separate DB review.

## 9. Data identity and fake-data labeling

Fixture data must never be represented as real market data.

Required labeling:

- `source = E2E_POSITIVE_FIXTURE` or another equally explicit fake-source value.
- `raw_payload_json` must not contain real provider payload, credentials, token, headers, signatures, private keys, passphrases, cookies, mnemonics, or raw request/response material.
- Documentation and test logs must call it `controlled fixture`, `fake fixture`, or `E2E fixture`, not `real exchange bars`.
- Do not use `EXCHANGE_HISTORICAL` for this fixture.

Recommended cleanup:

- Before inserting, delete rows matching the exact fixture scope/window and `source = E2E_POSITIVE_FIXTURE`.
- After the smoke, either delete the same rows or keep them only if the test environment is disposable and the source label makes them harmless.
- Never delete broad `marketdata_bars` ranges without source/scope/window predicates.

## 10. No-outbound boundary

The future positive smoke must prove no-outbound by construction:

- It may call `/actuator/health`, `/api/auth/login`, local account setup API used by `loginToConsole`, `/api/marketdata/bars`, and `/api/marketdata/readiness`.
- It must not call `POST /api/marketdata/ingestion-jobs/{jobId}/run-once`.
- It must not call `HistoricalKlineProvider`, OKX/Binance historical adapter, WebSocket, permission probe, trading adapter, order/cancel/withdraw/transfer path, or credential material path.
- It must not set `LIVE=true`, `LIVE_ENABLED=true`, or any real-provider enable flag.
- Test logs should include the selected fixture scope and `source=E2E_POSITIVE_FIXTURE`, not provider URLs or secrets.

## 11. Positive smoke acceptance criteria

Minimum acceptance:

- Backend health is `UP`.
- Controlled fixture preparation completes for the exact UI query scope/window.
- `/api/marketdata/bars` returns `bars.length > 0`.
- `/api/marketdata/readiness` returns `barCount > 0`.
- The page submits the same query through the MarketData form.
- Real page request to `/api/marketdata/bars` returns `200`.
- Real page request to `/api/marketdata/readiness` returns `200`.
- K-line container is visible.
- K-line canvas is visible.
- Volume container is visible.
- Volume canvas is visible.
- Bar count is displayed.
- Last bar time is displayed and matches the fixture window.
- Readiness `status`, `freshnessStatus`, and `sourceHealthStatus` are displayed.
- Quality summary includes `OK` or `okCount > 0`.
- Gap count is displayed.
- No real exchange network call is required or made.
- LIVE remains disabled.
- AI and DH runtime remain not started/not integrated.

Recommended negative assertions:

- Page does not show `Marketdata bars 查询失败`.
- Page does not show `MarketData source health unavailable`.
- Page does not show `No bars returned`.
- Test artifacts do not contain `apiKey`, `secret`, `passphrase`, `private key`, `mnemonic`, `Authorization`, cookie, raw credential, or real exchange host.

## 12. Required stop rules

Stop and re-plan if any of these becomes necessary:

- Add or modify Flyway migration.
- Modify historical migration.
- Add production API.
- Modify `MarketdataController`.
- Modify bars/readiness query semantics.
- Call real exchange endpoints.
- Read real credential material.
- Enable LIVE.
- Add RealClient or real provider.
- Add WebSocket or realtime subscription.
- Reuse app startup seed in a way that affects unrelated local/test/CI contexts.
- Make fixture data indistinguishable from real exchange data.

## 13. P0/P1/P2/P3 risks

### P0

- None in this planning-only task.

### P1

- Future implementation could use ingestion `run-once` or legacy OKX/Binance provider code to create bars. Mitigation: fixture setup must write local DB test rows or use a proven no-outbound test-only helper only.
- Future smoke could query `BTC-USDT` while seeding `BTCUSDT`, making the positive branch silently fall back to no-data. Mitigation: assert exact preflight scope and `bars.length > 0` before opening the page.
- Future fixture could be mislabeled as real exchange data. Mitigation: require explicit fake `source` and optional `raw_payload_json` marker.

### P2

- Direct DB fixture helper couples Playwright to DB connection details. Mitigation: keep helper narrow, scoped to local backend smoke, and fail fast when DB env is unavailable.
- Cleanup could accidentally delete non-fixture bars. Mitigation: cleanup must use exact `source + exchange + market + symbol + interval + open_time` predicates.
- Readiness can show `STALE` or `GAP` if the query window is too wide. Mitigation: query exactly the fixture window for first positive smoke.
- `AuthSeedConfiguration` local/test user seed can be confused with MarketData fixture seed. Mitigation: document that auth seed is separate and do not add MarketData startup seed.

### P3

- Current registered CSV fixtures use legacy no-dash symbols. This is acceptable for GateI backtest fixtures but not enough for current MarketData UI positive smoke.
- Existing 2G empty/no-data smoke remains valuable and should stay as the fail-closed baseline after adding a positive smoke.

## 14. Recommended next implementation task

Recommended next task:

`NQ-GATEM-2I-MARKETDATA-POSITIVE-BARS-FIXTURE-SMOKE`

Proposed scope:

- Add a test-only positive bars fixture preparation helper.
- Prepare canonical UI-scope fake rows for `BINANCE / SPOT / BTC-USDT / 1m`.
- Add or update one Playwright real-backend smoke to force the positive branch.
- Keep the existing empty/no-data smoke behavior available.
- No backend production code change.
- No frontend production code change unless a test-only selector requires a minimal non-functional test id.
- No migration.
- No CI workflow change unless separately authorized.

Suggested validation:

```powershell
Set-Location frontend
npm run build
npm run test:e2e -- tests/e2e/marketdata-readiness-real-backend-smoke.spec.ts --project=chromium
```

If the implementation touches backend tests or a DB helper outside frontend E2E, add the smallest relevant Maven test scope and explain why.

## 15. Final decision

Proceed later with a narrow test-only fixture helper that prepares canonical fake `marketdata_bars` rows for the exact MarketData UI scope/window. Do not add migration, do not add production API, do not auto-seed local/test profiles, and do not use real exchange ingestion.

This 2H task remains `PLAN ONLY / NOT IMPLEMENTED`.

## 16. Commit recommendation

If only this plan and allowed current docs are changed, use:

```text
docs(gatem): plan marketdata positive bars fixture
```
