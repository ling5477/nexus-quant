# NQ-GATEN-0 Exchange Docs And Existing Adapter Reconciliation

## Status

**PASS / RECONCILIATION BASELINE / READY TO COMMIT**

This document records the GateN-0 reconciliation baseline for early OKX / Binance official-docs work, existing adapter/interface/test/API evidence, and current GateN public marketdata / exchange sandbox boundaries.

This is a planning-only and docs-only reconciliation. It does not start GateN implementation, does not rewrite exchange official docs from scratch, does not call real exchange APIs, and does not authorize LIVE or private trading.

GateN-1 reference status: [NQ-GATEN-1 Public MarketData Contract Plan Review](NQ_GATEN_PUBLIC_MARKETDATA_CONTRACT_PLAN_REVIEW.md) = **PASS / CONTRACT PLAN REVIEW / READY TO COMMIT**. GateN-1 consumes this reconciliation as input and keeps GateN implementation **NOT STARTED**.

## Task Classification

- Task name: `NQ-GATEN-0-EXCHANGE-DOCS-AND-EXISTING-ADAPTER-RECONCILIATION`.
- Type: `DOCUMENTATION_RECONCILIATION + EXISTING_ADAPTER_INVENTORY + OFFICIAL_DOCS_DELTA_CHECK + SECURITY_BOUNDARY_REVIEW`.
- Level: GateN planning-only / docs-only.
- Main output: current fact baseline for GateN-1 Public MarketData Contract Plan Review.

## Scope

Reviewed evidence:

- Current fact source: `README.md`, `docs/current/README.md`, `docs/current/STATUS.md`, `docs/current/ROADMAP.md`, `docs/current/API.md`, `docs/current/DB_SCHEMA.md`, `docs/current/TESTING.md`, `docs/current/WORKLOG.md`, `docs/current/NQ_NEXT_PHASE_PLAN.md`, `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md`.
- Historical official-docs and implementation evidence under `docs/gates/` and `docs/archive/`.
- Backend adapter/API/test surfaces under `backend/`, read-only.
- Public marketdata, adapter readiness, permission probe, no-real, fake, sandbox, and historical live-0 wording.

Explicitly not done:

- No backend, frontend, research, script, deploy, workflow, migration, API, page, E2E, adapter, RealClient, real provider, or runtime behavior change.
- No real OKX / Binance / Bybit / Gate / Coinbase / Kraken API call.
- No credential file read, no credential material output, no permission probe execution.
- No order, cancel, transfer, withdraw, private account, private balance, private position, or user-data stream operation.

## GateN Current Baseline

GateM is already **FINALIZED / FROZEN / ACCEPTED / TAGGED** with release tag `nq-gatem-freeze`.

Current GateN route:

- GateN route: **Public MarketData / Exchange Sandbox Planning**.
- GateN status: **PLAN ONLY / NOT IMPLEMENTED**.
- GateN-0 reconciliation: **PASS / RECONCILIATION BASELINE / READY TO COMMIT**.
- GateN implementation: **NOT STARTED**.

Current negative boundaries:

- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.
- Public marketdata readiness is not trading authorization.
- Sandbox readiness is not production readiness.
- Historical live-0 evidence is historical evidence only, not current LIVE readiness.

## Existing Official Docs Inventory

Early official-docs inventory exists and can be reused as source-history evidence:

- `docs/gates/gate-d/SOURCES.md` records OKX Spot REST / WebSocket official documentation as external sources.
- `docs/gates/gate-d/SOURCES.md` records Binance Spot REST / WebSocket official documentation as external sources.
- GateD evidence under `docs/gates/gate-d/` records earlier OKX / Binance real-spike and live-0 work that depended on those official docs.

Current delta-check pointers, checked only as documentation pages:

- OKX official API v5 docs: <https://app.okx.com/docs-v5/en/>.
- Binance Spot market data REST docs: <https://developers.binance.com/docs/binance-spot-api-docs/rest-api/market-data-endpoints>.
- Binance Spot general endpoints docs: <https://developers.binance.com/docs/binance-spot-api-docs/rest-api/general-endpoints>.

Decision:

- Early official-docs整理可以复用 as historical source inventory.
- GateN must not copy old protocol assumptions blindly.
- GateN-1 must re-read exact current request/response schemas, rate limits, auth requirements, timestamp semantics, pagination/limit behavior, endpoint status, and error payloads from official docs before any implementation.
- NQ internal contracts may abstract common fields, but protocol facts must come from official exchange docs.

## Existing OKX / Binance Adapter Inventory

Adapter API surfaces:

- `backend/nq-adapter-api/src/main/java/.../HistoricalKlineAdapter.java`: public historical OHLCV adapter contract.
- `backend/nq-adapter-api/src/main/java/.../MarketDataAdapter.java`: marketdata subscription contract for bars, trades, and order book.
- `backend/nq-adapter-api/src/main/java/.../TradingAdapter.java`: private trading contract for order placement, cancellation, order query, and open-order listing.
- `backend/nq-adapter-api/src/main/java/.../DefaultAdapterReadinessService.java`: static fail-closed readiness policy, no IO, no credential, no network.

OKX public marketdata adapter evidence:

- `backend/nq-adapter-okx/src/main/java/.../OkxHistoricalKlineAdapter.java` implements `HistoricalKlineAdapter`.
- Current scope is SPOT historical OHLCV bars for fixed symbols and intervals from GateH/GateM lineage.
- It is legacy network-capable code and must not be treated as current authorized real provider behavior.

Binance public marketdata adapter evidence:

- `backend/nq-adapter-binance/src/main/java/.../BinanceHistoricalKlineAdapter.java` implements `HistoricalKlineAdapter`.
- Current scope is SPOT historical OHLCV bars for fixed symbols and intervals from GateH/GateM lineage.
- It is legacy network-capable code and must not be treated as current authorized real provider behavior.

Infra bridge evidence:

- `backend/nq-infra/src/main/java/.../AdapterHistoricalKlineProvider.java` bridges `HistoricalKlineAdapter` into the core historical-bar model without making core depend on concrete OKX / Binance adapters.
- `backend/nq-infra/src/main/java/.../JdbcMarketdataReadinessRepository.java` derives readiness from local DB bars and ingestion job/run facts, not from live adapter calls.

Readiness and no-real guard evidence:

- `DefaultAdapterReadinessService` marks OKX / Binance capabilities as not ready under the current no-real baseline.
- `ExchangeAdapterConfiguration` wires OKX / Binance trading adapters behind readiness guard behavior from GateM.
- `NoopMarketDataAdapter` reports no-real disabled behavior instead of pretending a real subscription succeeded.
- `NoRealExchangeCredentialPermissionProbePort` returns `SKIPPED` / `REAL_EXCHANGE_PROBE_DISABLED` without HTTP client, credential read, order/cancel, transfer, withdraw, or exchange call.

## Existing Public MarketData Implemented Surface

Current implemented public marketdata surface that can be inventoried for GateN:

- `GET /api/marketdata/bars`: reads local `marketdata_bars` data.
- `POST /api/marketdata/ingestion-jobs`, ingestion job list/detail/runs, and run-once APIs: existing GateH/GateM ingestion control surface; not a GateN authorization.
- `GET /api/marketdata/readiness`: DB-only source-health summary, no-migration MVP, no adapter call, no external exchange call, no credential call, no LIVE call.
- DB facts: `marketdata_bars`, `marketdata_ingestion_jobs`, `marketdata_ingestion_runs`.
- Current frontend marketdata readiness/quality UI consumes existing API data and does not imply trading readiness.

This surface is useful for GateN-1 as existing public marketdata and local-readiness inventory. It does not authorize reusing private adapter paths or enabling live exchange connectivity.

## Existing Private / Trading Surface

Private or trading-related surfaces exist in code and must remain separated from GateN public marketdata:

- `TradingAdapter` defines order placement, cancellation, order query, and open-order listing.
- `OkxExchangeAdapter` and `BinanceExchangeAdapter` implement trading-adapter behavior.
- Historical GateD records include OKX and Binance live-0 / real-spike evidence.
- GateL/GateM hardening established disabled endpoints, unconfigured credentials, raw payload suppression, no-real status, and readiness guard behavior.

Current interpretation:

- Existing private/trading adapter code is not current authorized real provider behavior.
- Existing private/trading adapter code is not GateN public marketdata implementation input except as forbidden-boundary evidence.
- GateN must keep public adapter, private trading adapter, and permission probe separated.
- Any future private trading, signed endpoint, credential, user-data stream, permission probe, or LIVE path requires separate review and is default forbidden.

## Historical Simulation / Live-0 Evidence Classification

Simulation / Paper evidence:

- GateI / GateJ completed SIM / Paper Trading runtime and stability work.
- GateM finalized no-real runtime readiness baseline with Paper-only and no-real boundaries.
- These records remain current Paper/SIM evidence only.

Historical live-0 / spike evidence:

- `docs/gates/gate-d/GATE_D_CHECKLIST.md` records earlier OKX and Binance real-spike / live-0 acceptance items.
- `docs/gates/gate-d/WORK.md` records OKX official script samples and Binance LIMIT-to-cancel historical evidence.
- These records are historical evidence and can inform source inventory, but they are not current LIVE readiness, not production readiness, and not trading authorization.

Classification decision:

- Historical live-0 evidence = historical spike / source-history evidence.
- Historical live-0 evidence != current LIVE enabled.
- Historical live-0 evidence != current RealClient / real provider implemented.
- Historical live-0 evidence != current permission probe authorization.
- Historical live-0 evidence != GateN implementation started.

## Deprecated / Unsafe / Not-Current Paths

The following paths or concepts must not be carried forward as current GateN implementation:

- `docs/archive/scripts/gated_okx_dome_verify.ps1`: archive-only historical script.
- Historical `__gated/**` compatibility paths: archive/historical only; not current API authority.
- Direct private adapter execution paths for order/cancel/query/open-orders.
- Process credential sourcing from env/system properties as a direct runtime credential path.
- Default real exchange endpoints without explicit no-real sentinel boundaries.
- Raw provider payload propagation into current API/DTO/log output.
- Permission probe real execution.
- Any path that treats `SKIPPED`, Paper-ready, DB-fresh, `/actuator/health=UP`, public marketdata readiness, or operational readiness as LIVE/trading authorization.

## Official Docs Delta-Check Needs

GateN-1 must perform targeted official-docs extraction before any implementation:

- OKX:
  - Historical candles / OHLCV endpoint contract.
  - Public instruments endpoint contract.
  - Public ticker/orderbook/trades endpoint contract only if GateN-1 selects those surfaces.
  - Public WebSocket marketdata channels only if GateN-1 selects WebSocket.
- Binance:
  - Klines endpoint contract.
  - Exchange info / instruments contract.
  - Public ticker/orderbook/trades endpoint contract only if GateN-1 selects those surfaces.
  - Public WebSocket market streams only if GateN-1 selects WebSocket.

GateN-1 must record:

- Official URL and accessed date.
- Public/private/auth boundary for each endpoint.
- Required and optional parameters.
- Symbol/instrument naming rules.
- Interval/timeframe mapping.
- Pagination, `limit`, cursor, start/end time semantics.
- Response field order, numeric precision, timestamp unit, timezone assumptions.
- Rate limits and retry/backoff expectations.
- Error payload model and fail-closed mapping.
- Payload size limits and parsing constraints.
- Regional, product-line, or deprecation caveats.

## GateN-1 Contract Review Inputs

GateN-1 should use this baseline as input and produce a contract review before implementation.

Required inputs:

- Existing official-docs inventory from GateD.
- Current official-docs delta extraction for selected public marketdata endpoints.
- Existing `HistoricalKlineAdapter` inventory.
- Existing OKX / Binance historical kline adapter inventory.
- Existing local DB readiness and bars API inventory.
- Existing no-real / readiness guard / permission probe boundary evidence.

Recommended source taxonomy:

- `LOCAL_DB`: current DB-only marketdata facts.
- `FIXTURE`: deterministic fixture data.
- `FAKE_SERVER`: no-egress fake exchange server.
- `NO_EGRESS_SANDBOX`: local sandbox path with no public internet call.
- `PUBLIC_SANDBOX_CANDIDATE`: future reviewed public-docs-only candidate, still not private trading authorization.

Required separation:

- Public marketdata adapter != private trading adapter.
- Public marketdata adapter != permission probe.
- Public marketdata readiness != trading authorization.
- Public WebSocket marketdata != private user data stream.
- Official protocol fact != NQ internal normalized contract.

## Forbidden Carry-Over List

Do not carry the following into GateN as implementation authority:

- Historical live-0 as current LIVE readiness.
- GateD live-spike evidence as current production readiness.
- OKX / Binance private trading adapter methods as public marketdata path.
- Signed/private account/order/cancel/balance/position/transfer/withdraw endpoints.
- Real credentials or credential validation.
- Real permission probe.
- Real provider / RealClient.
- LIVE enablement.
- AI runtime or DH runtime.
- `SKIPPED`, Paper-ready, DB-fresh, `/actuator/health=UP`, operational readiness, or public marketdata readiness as trading authorization.

## Risks

### P0

- None found in this docs-only reconciliation.

### P1

- None found for the current planning-only baseline.

### P2

- Official exchange docs are external and can drift. GateN-1 must re-check exact official docs before coding or test-contract freeze.
- Existing OKX / Binance adapter code is legacy network-capable. GateN must not accidentally route public marketdata planning through private trading or signed paths.
- Historical live-0 records can be misread as current authorization. Current docs must keep historical/spike wording explicit.

### P3

- Historical docs contain older route wording and append-only evidence. Keyword searches will keep finding OKX/Binance/LIVE terms in historical contexts.
- Current public marketdata and readiness docs must continue using explicit `PLAN ONLY / NOT IMPLEMENTED` and no-real boundary language until GateN implementation is separately authorized.

## Final Decision

**PASS / RECONCILIATION BASELINE / READY TO COMMIT**

GateN-0 can serve as the fact baseline for GateN-1 because early official-docs inventory, existing OKX/Binance adapter evidence, public marketdata implemented surface, private/trading forbidden surface, historical live-0 classification, and no-real/security boundaries were reconciled without code or runtime changes.

Recommended next task:

`NQ-GATEN-1-PUBLIC-MARKETDATA-CONTRACT-PLAN-REVIEW`

GateN-1 must remain contract/planning review unless separately authorized. It must use official docs for protocol facts, keep public marketdata and private trading separated, and keep all real external calls forbidden by default.

Commit recommendation:

`docs(gaten): reconcile exchange docs and existing adapters`
