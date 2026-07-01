# NQ-GATEN-1 Public MarketData Contract Plan Review

## Status

**PASS / CONTRACT PLAN REVIEW / READY TO COMMIT**

This document is the GateN-1 public marketdata contract plan review. It defines the public-only marketdata contract baseline for later GateN work and consumes the GateN-0 reconciliation baseline.

This is a planning-only and docs-only review. It does not start GateN implementation, does not add API, does not add migration, does not implement adapter code, does not call real exchange APIs, and does not authorize LIVE or private trading.

## Current GateN-1 Decision

GateN-1 decision:

- Use official exchange docs as the protocol source of truth.
- Use existing NQ public marketdata surfaces as inventory, not as automatic authorization.
- Approve a public-only internal contract shape for GateN-2 planning.
- Keep all private, signed, account, order, balance, transfer, withdraw, user-data-stream, credential, permission probe, RealClient, real provider, LIVE, AI, and DH runtime paths out of GateN-1.

Final state:

- GateN-1 public marketdata contract plan review: **PASS / CONTRACT PLAN REVIEW / READY TO COMMIT**.
- GateN implementation: **NOT STARTED**.
- GateN route: **Public MarketData / Exchange Sandbox Planning**.
- GateN current status: **PLAN ONLY / NOT IMPLEMENTED**.

Current negative boundaries:

- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.
- Public readiness is not trading readiness.
- Public adapter is not private trading adapter.
- Historical live-0 is not current LIVE ready.
- Any real outbound must be a separate task with separate review.

## Review Target

Review target:

- OKX current official public marketdata docs.
- Binance current official public marketdata docs.
- Candidate exchange official-docs inventory for Bybit / Gate / Coinbase / Kraken.
- Current NQ public marketdata surfaces.
- Current NQ private/trading surfaces as forbidden-boundary evidence.
- Public contract taxonomy, readiness, freshness, health, gap, rate-limit, timeout, retry, no-egress, and separation rules.

Not reviewed as implementation:

- No code path was changed or executed.
- No exchange API endpoint was called.
- No credential material was read or emitted.
- No private trading, account, balance, order, cancel, transfer, withdraw, user data stream, or permission probe flow was used.

## Official Docs Delta-Check Summary

Official docs checked on 2026-07-01:

- OKX API v5 docs: <https://app.okx.com/docs-v5/en/>.
- Binance Spot REST market data endpoints: <https://developers.binance.com/docs/binance-spot-api-docs/rest-api/market-data-endpoints>.
- Binance Spot REST general endpoints: <https://developers.binance.com/docs/binance-spot-api-docs/rest-api/general-endpoints>.
- Binance Spot WebSocket streams: <https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams>.
- Bybit V5 market kline docs: <https://bybit-exchange.github.io/docs/v5/market/kline>.
- Gate API v4 official docs entry: <https://www.gate.com/docs/developers/apiv4/en/>.
- Coinbase Exchange docs entry: <https://docs.cdp.coinbase.com/exchange/docs/welcome>.
- Kraken REST OHLC docs entry: <https://docs.kraken.com/api/docs/rest-api/get-ohlc-data/>.

Delta-check conclusion:

- OKX and Binance have enough official public marketdata documentation to define a GateN internal public contract baseline.
- Binance official docs page checked under `developers.binance.com/docs/...` currently displays an official legacy-docs banner and points to actively maintained docs. GateN-2 must re-pin the active Binance source URL before implementation.
- Bybit / Gate / Coinbase / Kraken are only candidate exchange documentation entries in this review; their endpoint contracts are not extracted in this round.
- Exact endpoint fields, enum ranges, timestamps, and rate limits must remain source-derived fields in GateN-2; do not hardcode protocol facts from memory.

## OKX Public MarketData Contract Facts

OKX official docs checked:

- REST public marketdata / market endpoints:
  - `GET /api/v5/market/candles`.
  - `GET /api/v5/market/history-candles`.
  - `GET /api/v5/market/tickers`.
  - `GET /api/v5/market/ticker`.
  - Public order book / trade / index / mark price families exist in the same official marketdata area but are not selected for GateN-2 unless separately reviewed.
- Public metadata / status endpoints:
  - `GET /api/v5/public/instruments`.
  - `GET /api/v5/system/status`.
- WebSocket public channel families:
  - Public marketdata channels include ticker, candle, trade, and book-style data families in the OKX WebSocket docs.
  - Channel routing may differ between public and business endpoints; GateN-2 must pin exact WebSocket URL, channel name, and product line before implementation.

OKX contract implications:

- `instId` is the primary instrument key for selected marketdata endpoints.
- `instType` is required for instrument metadata selection.
- Candles/history-candles must map official bar values to NQ `timeframe` without inventing unsupported intervals.
- Timestamps must be normalized into UTC instants in NQ; original provider timestamp unit must be preserved in provider metadata during parsing tests.
- OKX public docs contain both public and private sections; GateN must not import account, balance, order, position, bills, trading, funding, transfer, withdraw, or private WebSocket channels.
- OKX public docs include API key creation/authentication sections because the full docs include private APIs. Those sections are explicitly out of GateN-1 authority.

GateN-1 approved OKX public subset:

- Historical/public OHLCV bars.
- Public instrument metadata.
- Public ticker snapshot, after GateN-2 re-confirms endpoint parameters.
- Public exchange/system status, after GateN-2 re-confirms status schema.
- Public WebSocket candles/tickers only as no-egress fake-server contract candidates, not as real outbound implementation.

## Binance Public MarketData Contract Facts

Binance official docs checked:

- REST public marketdata endpoints include:
  - `GET /api/v3/klines`.
  - `GET /api/v3/ticker/24hr`.
  - `GET /api/v3/ticker/price`.
  - `GET /api/v3/ticker/bookTicker`.
  - `GET /api/v3/depth`.
  - Recent trades and aggregate trades endpoint families.
- REST metadata endpoint:
  - `GET /api/v3/exchangeInfo`.
- WebSocket public stream families include:
  - Raw and combined streams.
  - Kline/candlestick streams with stream name shape `<symbol>@kline_<interval>`.
  - Individual symbol ticker streams with stream name shape `<symbol>@ticker>`.
  - Depth stream families.

Binance facts extracted for GateN-1:

- `GET /api/v3/klines` is for kline/candlestick bars; klines are identified by open time.
- `GET /api/v3/klines` request parameters include `symbol`, `interval`, optional `startTime`, `endTime`, `timeZone`, and `limit`; official docs list default `limit=500` and maximum `limit=1000`.
- `GET /api/v3/klines` request weight is documented as `2`.
- `GET /api/v3/exchangeInfo` returns exchange trading rules and symbol information; official docs list request weight `20`.
- 24h ticker weight varies by symbol selection; all-symbol calls can be materially heavier than single-symbol calls.
- Price ticker and book ticker endpoints support symbol-specific and broader queries, with lower weight for symbol-scoped calls.
- WebSocket stream symbols are lowercase in stream names.
- Binance WebSocket docs list `wss://stream.binance.com:9443` and `wss://stream.binance.com:443` as base endpoints.
- Binance WebSocket docs also identify `wss://data-stream.binance.vision` as market-data only, with user data stream unavailable from that URL.
- Binance WebSocket docs state connection and message limits; GateN must encode these as provider limit metadata rather than ignoring them.

Binance contract implications:

- GateN must require symbol-scoped REST calls by default; all-symbol calls are disabled until a separate batch-size and rate-limit review approves them.
- GateN must store request weight metadata per endpoint shape.
- GateN must normalize Binance symbols into NQ canonical symbols without treating that mapping as exchange-wide metadata completeness.
- GateN must keep WebSocket market streams separate from user data stream and WebSocket API trading/account surfaces.

GateN-1 approved Binance public subset:

- Historical/public OHLCV bars via `klines`.
- Public symbol/instrument metadata via `exchangeInfo`.
- Public ticker snapshot via ticker endpoints.
- Public WebSocket kline/ticker/depth streams only as no-egress fake-server contract candidates, not as real outbound implementation.

## Candidate Exchanges Official Docs Inventory

This review only registers candidate exchange docs entries. It does not extract their full endpoint contract.

| Exchange | Official docs entry checked | GateN-1 classification | Notes |
| --- | --- | --- | --- |
| Bybit | `https://bybit-exchange.github.io/docs/v5/market/kline` | Candidate public marketdata source | Page reachable; full endpoint extraction deferred. |
| Gate | `https://www.gate.com/docs/developers/apiv4/en/` | Candidate public marketdata source | Official docs entry known; shell fetch returned 403, so no contract facts extracted. |
| Coinbase | `https://docs.cdp.coinbase.com/exchange/docs/welcome` | Candidate public marketdata source | Page reachable; full endpoint extraction deferred. |
| Kraken | `https://docs.kraken.com/api/docs/rest-api/get-ohlc-data/` | Candidate public marketdata source | Page reachable; full endpoint extraction deferred. |

GateN-2 must not include Bybit / Gate / Coinbase / Kraken implementation unless a separate candidate-contract extraction task is approved.

## Existing NQ Public Surface Mapping

Current NQ public marketdata surface:

| NQ surface | Current status | GateN-1 use | Boundary |
| --- | --- | --- | --- |
| `HistoricalKlineAdapter` | Implemented adapter API | Public OHLCV inventory | Contract input only. |
| `OkxHistoricalKlineAdapter` | Implemented legacy network-capable adapter | OKX historical OHLCV inventory | Not real-provider authorization. |
| `BinanceHistoricalKlineAdapter` | Implemented legacy network-capable adapter | Binance historical OHLCV inventory | Not real-provider authorization. |
| `AdapterHistoricalKlineProvider` | Implemented infra bridge | Existing mapping from adapter bars to core bars | Do not change in GateN-1. |
| `GET /api/marketdata/bars` | Implemented read API | Current local DB bars query | Local DB only. |
| ingestion jobs / runs | Implemented control/read surface | Existing historical ingestion lineage | Not GateN real outbound approval. |
| `GET /api/marketdata/readiness` | Implemented DB-only readiness summary | Existing freshness/health baseline | Does not call adapter or exchange. |
| local DB facts | Implemented `marketdata_bars`, jobs, runs | Readiness and bars source | No migration in GateN-1. |

GateN-1 mapping decision:

- Reuse these surfaces as evidence and contract inputs.
- Do not add endpoints.
- Do not add schema.
- Do not call existing legacy network-capable adapters.
- Do not reinterpret readiness as trading authorization.

## Forbidden Private / Trading Carry-Over List

The following must not enter GateN public authority:

- `TradingAdapter` as a public marketdata adapter.
- `OkxExchangeAdapter` / `BinanceExchangeAdapter` private trading methods.
- `placeOrder`, `cancelOrder`, `getOrder`, `listOpenOrders`.
- Signed REST endpoints.
- Private WebSocket, user data stream, account stream, or login/auth channel.
- Account, balance, position, order, cancel, transfer, withdraw, deposit, funding, fee tier, bills, ledger, risk, or wallet endpoints.
- Real credential read, validation, or probe.
- Real permission probe execution.
- RealClient / real provider.
- LIVE enablement.
- Historical live-0 evidence as current readiness.
- Public marketdata readiness as trading readiness.
- `/actuator/health` or operational readiness as LIVE authorization.

## NQ Public MarketData Internal Contract Proposal

GateN-1 approves this internal contract proposal for GateN-2 planning only.

### Public Source

Required fields:

- `sourceType`: one of the source taxonomy values.
- `exchangeCode`: `OKX`, `BINANCE`, or future approved candidate.
- `marketType`: initially `SPOT`.
- `transport`: `REST`, `WEBSOCKET`, `LOCAL_DB`, `FIXTURE`, or `FAKE_SERVER`.
- `officialDocsUrl`: official source URL used for endpoint contract.
- `officialDocsCheckedAt`: ISO-8601 UTC timestamp for docs extraction.
- `authRequired`: must be `false` for GateN public source.
- `privateBoundary`: text explaining why no private/signed/user-data path is involved.

### Public Instrument

Required fields:

- `exchangeCode`.
- `marketType`.
- `providerInstrumentId`.
- `canonicalSymbol`.
- `baseAsset`.
- `quoteAsset`.
- `status`.
- `rawStatus`.
- `pricePrecision` / `quantityPrecision` when source provides them.
- `minNotional` / size filters when source provides them.
- `sourceUpdatedAt`.

### Public OHLCV Bar

Required fields:

- `exchangeCode`.
- `marketType`.
- `symbol`.
- `timeframe`.
- `openTime`.
- `closeTime`.
- `open`.
- `high`.
- `low`.
- `close`.
- `volume`.
- `quoteVolume` when source provides it.
- `tradeCount` when source provides it.
- `isClosed` when source provides it.
- `sourceLatencyMs` when measurable.
- `sourceType`.
- `qualityStatus`.

### Public Ticker

Required fields:

- `exchangeCode`.
- `marketType`.
- `symbol`.
- `lastPrice`.
- `bidPrice` / `askPrice` when source provides them.
- `open24h` / `high24h` / `low24h` / `volume24h` when source provides them.
- `sourceUpdatedAt`.
- `sourceType`.
- `qualityStatus`.

### Public Exchange Status

Required fields:

- `exchangeCode`.
- `providerStatus`.
- `normalizedStatus`: `ONLINE`, `DEGRADED`, `MAINTENANCE`, `UNKNOWN`, or `UNAVAILABLE`.
- `effectiveFrom` / `effectiveTo` when source provides them.
- `message`.
- `officialDocsUrl`.

## Source Taxonomy

Allowed source taxonomy:

- `LOCAL_DB`: local NQ DB facts only.
- `FIXTURE`: deterministic file/test fixture data.
- `FAKE_SERVER`: local fake exchange server.
- `NO_EGRESS_SANDBOX`: test harness that proves no external network call.
- `PUBLIC_SANDBOX_CANDIDATE`: public-docs-reviewed candidate that is still not private trading authorization.

Rules:

- Default GateN-2 source should be `FAKE_SERVER` or `NO_EGRESS_SANDBOX`.
- `PUBLIC_SANDBOX_CANDIDATE` cannot be used for real outbound until a separate implementation review approves it.
- Source taxonomy must be visible in logs, test assertions, docs, and UI wording once implemented.

## Freshness / Health / Gap Model

Approved public marketdata readiness states:

- `FRESH`: data is within configured freshness threshold and has no known gap.
- `STALE`: data exists but is older than configured threshold.
- `GAP`: expected interval sequence has a missing bar or discontinuity.
- `ERROR`: source query, parsing, normalization, or persistence failed.
- `DISABLED`: source is intentionally disabled or no-real.
- `PENDING_BACKEND_SUPPORT`: current API/backend does not yet expose the needed fact.

Required model fields:

- `freshnessStatus`.
- `sourceHealthStatus`.
- `qualityStatusSummary`.
- `gapCount`.
- `lastSuccessAt`.
- `lastFailureAt`.
- `lastBarOpenTime`.
- `lastBarCloseTime`.
- `expectedInterval`.
- `actualInterval`.
- `sourceType`.

Rules:

- `UNKNOWN`, malformed payload, empty required fields, or backend unavailable must fail closed.
- `FRESH` does not imply trading readiness.
- `FRESH` does not imply exchange account availability.
- `FRESH` does not imply LIVE authorization.

## Rate Limit / Timeout / Retry Model

GateN internal contract must carry provider-derived rate-limit metadata:

- `endpointKey`.
- `providerWeight`.
- `limitWindow`.
- `maxRequests`.
- `burstAllowed`.
- `retryable`.
- `backoffPolicy`.
- `timeoutMs`.
- `maxAttempts`.

Rules:

- Provider weights and limits must come from official docs and be stored as contract metadata, not scattered magic numbers.
- All public outbound candidates must have explicit timeout and max-attempt limits before implementation.
- Infinite retry is forbidden.
- Retry is allowed only for transient transport / rate-limit / provider-unavailable errors and must preserve max attempts.
- Parsing, schema mismatch, unsupported symbol, unsupported interval, and private-boundary violations are fail-closed and not blindly retryable.
- All-symbol or all-instrument calls are disabled by default unless a batch-size and rate-limit review approves them.

## Public / Private Separation Rules

Hard rules:

- Public marketdata adapter != private trading adapter.
- Public marketdata adapter != permission probe.
- Public WebSocket market stream != private user data stream.
- Public docs source != signed/private endpoint authority.
- Historical live-0 != current LIVE readiness.
- Public readiness != trading authorization.

Implementation guardrails for later tasks:

- Separate package/interface names for public marketdata candidates.
- No dependency from public marketdata code to credential, account, trading, order, ledger, risk, or permission probe services.
- No API key / secret / passphrase / signature fields in public marketdata DTOs, logs, fixtures, fake-server payloads, or docs examples.
- No order/cancel/transfer/withdraw words in public adapter implementation except in explicit forbidden-boundary tests/docs.

## No-Egress Requirements For Implementation

GateN-2 and later implementation tasks must start with no-egress controls:

- Use fake server or fixture source first.
- Add tests that fail if public marketdata implementation attempts real exchange hosts.
- Keep external network disabled in CI for public marketdata adapter tests.
- Keep credentials absent and assert no credential lookup occurs.
- Require explicit allowlist of fake-server base URL in tests.
- Capture source taxonomy in test output.
- Ensure error payloads are sanitized and do not include raw provider response bodies.

No real outbound path may be introduced until:

- Official docs contract is extracted and reviewed.
- No-egress fake-server implementation is green.
- Rate-limit / timeout / retry model is frozen.
- Security boundary review confirms no private endpoints, no credentials, no LIVE, no permission probe, no RealClient, and no trading.
- User explicitly authorizes a separate real outbound review task.

## GateN-2 Inputs

Recommended next task:

`NQ-GATEN-2-FAKE-SERVER-NO-EGRESS-PUBLIC-MARKETDATA-TEST-PLAN`

GateN-2 inputs:

- This GateN-1 contract plan review.
- GateN-0 exchange docs and existing adapter reconciliation.
- Official docs URLs listed above.
- Existing NQ public surface mapping.
- Source taxonomy.
- Freshness / health / gap model.
- Rate limit / timeout / retry model.
- Public/private separation rules.
- No-egress implementation requirements.

GateN-2 must remain planning/test-plan unless separately authorized for code. If implementation is authorized later, first implementation must use fake server / fixture / no-egress before any real public outbound.

GateN-2 status update:

- [NQ-GATEN-2 Fake Server / No-Egress Public MarketData Test Plan](NQ_GATEN_FAKE_SERVER_NO_EGRESS_TEST_PLAN.md) = **PASS / TEST PLAN BASELINE / READY TO COMMIT**.
- GateN-2 defines fake-server contract scope, no-egress strategy, forbidden endpoint list, test category matrix, fixture and taxonomy plan, readiness state simulation plan, security boundary, and GateN-3 entry criteria.
- GateN implementation remains **NOT STARTED**.
- GateN-2 does not implement fake server, adapter code, test code, API, migration, CI workflow, real outbound, private trading, credential access, LIVE, AI, DH runtime, RealClient, real provider, or real permission probe execution.

## P0/P1/P2/P3 Findings

### P0

- None found.

### P1

- None found for this docs-only GateN-1 contract review.

### P2

- Binance official docs page checked under the current official URL displayed a legacy-docs banner; GateN-2 must pin the active maintained Binance docs URL before implementation.
- OKX docs combine public, private, trading, account, and authentication content in one large docs surface. GateN-2 must extract only public endpoint facts and must keep private endpoints out.
- Existing OKX / Binance adapter code is legacy network-capable. GateN implementation must not route through those adapters until fake-server/no-egress guardrails are in place.
- Rate limits, weights, WebSocket limits, and endpoint schemas are provider facts. They must remain traceable to official docs and cannot become unreviewed magic constants.

### P3

- Gate candidate exchanges beyond OKX/Binance are inventory-only in this review; full extraction remains future work.
- Historical docs contain OKX/Binance/LIVE/live-0 wording. Future keyword scans must separate historical evidence from current authorization.

## Final Decision

**PASS / CONTRACT PLAN REVIEW / READY TO COMMIT**

GateN-1 is accepted as a public marketdata contract plan review baseline. It authorizes GateN-2 planning for fake-server / no-egress public marketdata tests, but does not authorize implementation, real outbound calls, private trading, credentials, permission probe, LIVE, AI, DH runtime, RealClient, or real provider work.

Commit recommendation:

`docs(gaten): review public marketdata contract plan`
