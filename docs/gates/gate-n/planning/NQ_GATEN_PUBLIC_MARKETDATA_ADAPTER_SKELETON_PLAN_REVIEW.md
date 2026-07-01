# NQ-GATEN-3 Public MarketData Adapter Skeleton Plan Review

## Status

**PASS / SKELETON PLAN REVIEW / READY TO COMMIT**

This document is the GateN-3 public marketdata adapter skeleton plan review. It consumes GateN-2 fake-server / no-egress public marketdata test-plan baseline and defines the minimum future skeleton scope, adapter boundary, DTO / capability / readiness model, no-egress constraints, and GateN-4 entry criteria.

This is a planning-only and docs-only review. It does not implement an adapter skeleton, does not add fake-server code, does not add test code, does not add API, does not add migration, does not modify CI, does not call real exchange APIs, and does not authorize LIVE, private trading, RealClient, real provider, credential access, or real permission probe execution.

GateN-4 input status: [NQ-GATEN-4 MarketData Sandbox Fixture Smoke Plan Review](NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_PLAN_REVIEW.md) = **PASS / FIXTURE SMOKE PLAN REVIEW / READY TO COMMIT**. GateN-4 consumes this skeleton plan review as input for deterministic fixture smoke scope, readiness simulation, no-egress validation, and GateN-5 entry criteria, but still does not implement adapter skeleton, fake server, tests, API, migration, CI workflow, real outbound, private trading, credential access, LIVE, AI, DH runtime, RealClient, real provider, or real permission probe behavior.

## Current GateN-3 Decision

GateN-3 decision:

- Accept a public-only adapter skeleton design baseline for later implementation review.
- Keep the first future skeleton fake-server / fixture / no-egress only.
- Keep public marketdata adapter contracts separate from private trading adapters.
- Keep public marketdata readiness diagnostic-only; it is not trading authorization.
- Keep GateN implementation **NOT STARTED** until a separate implementation task is authorized.

Current baseline:

- GateN route: **Public MarketData / Exchange Sandbox Planning**.
- GateN current status: **PLAN ONLY / NOT IMPLEMENTED**.
- GateN implementation: **NOT STARTED**.
- fake server: **NOT_IMPLEMENTED**.
- adapter skeleton: **NOT_IMPLEMENTED**.
- test code: **NOT_ADDED**.
- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.
- Public marketdata readiness is not trading authorization.
- Current real outbound remains forbidden by default.

## Inputs From GateN-2

GateN-3 uses these GateN-2 inputs:

- Default tests must not contact real exchange hosts.
- Fake-server / fixture sources are the only acceptable default future implementation inputs.
- Forbidden endpoint coverage must exist before any public adapter code is accepted.
- Public fixture payloads must not contain credential, account, order, balance, transfer, withdraw, token, signature, or private response material.
- Readiness states must include `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, and `PENDING_BACKEND_SUPPORT`.
- Source taxonomy must include `LOCAL_DB`, `FIXTURE`, `FAKE_SERVER`, `NO_EGRESS_SANDBOX`, and `PUBLIC_SANDBOX_CANDIDATE`.
- Future public outbound requires a separate task, separate review, explicit profile, official-docs contract, timeout / retry / rate-limit policy, no credential access, no private endpoint access, no LIVE, and user authorization.

GateN-3 does not re-author official protocol facts. Exact endpoint fields, enum values, timestamp units, pagination, weights, WebSocket limits, and deprecation notes must still be traced back to official docs before implementation.

## Skeleton Interface Proposal

Recommended public-only interface name:

- `NqPublicMarketDataAdapter`, or the closest existing project naming convention if implementation review finds a better local fit.

The interface must be public marketdata only. It must not extend, reuse, or depend on private `TradingAdapter` behavior.

Minimum method candidates:

| Method | GateN-3 decision | Reason |
| --- | --- | --- |
| `getBars` | Include in first skeleton contract. | OHLCV bars are the primary GateN marketdata surface and map to existing historical kline evidence. |
| `getInstrumentMetadata` | Include in first skeleton contract. | Instrument metadata is required to normalize symbol, venue, interval, precision, and supported status without private data. |
| `getTicker` | Include as a named public capability, but allow first implementation to return `PENDING_BACKEND_SUPPORT` unless the task explicitly includes ticker fixtures. | Ticker is public and needed by GateN-4 smoke, but it must not bloat the first skeleton into broad exchange coverage. |
| `getExchangeStatus` | Include as a named public capability, but allow first implementation to return `PENDING_BACKEND_SUPPORT` unless the task explicitly includes status fixtures. | Exchange status is public diagnostic metadata; it is not account permission or trading authorization. |

Methods deferred out of GateN-3:

- Real WebSocket subscription.
- Private user data stream.
- Account / balance / position / wallet APIs.
- Order / cancel / amend / fills APIs.
- Transfer / withdraw / deposit APIs.
- Credential validation.
- Permission probe.
- Real provider health probe.
- Runtime scheduling or ingestion.
- New HTTP API surface.

## Adapter Class / Package Proposal

Package placement is a proposal only; implementation must still follow the existing module layout and pass a separate code review.

Recommended contract placement:

- Public contract and DTOs in adapter API or another shared adapter contract module.
- Fake / fixture implementation in test-support or explicitly local-only adapter code, depending on the later implementation scope.
- OKX / Binance public skeleton classes in provider-specific adapter modules only after no-egress tests exist.

Recommended class inventory:

| Proposed class | Status | Boundary |
| --- | --- | --- |
| `NqPublicMarketDataAdapter` | Future contract candidate. | Public-only interface; no credential, no account, no order, no private stream. |
| `FakePublicMarketDataAdapter` | Future fake-source candidate. | Deterministic local fake only; no real network. |
| `FixturePublicMarketDataAdapter` | Future fixture-source candidate. | Repository/test fixture only; no real network. |
| `OkxPublicMarketDataAdapter` skeleton | Future no-egress skeleton candidate. | Must use fake-server / fixture base URL in tests; no default real OKX host. |
| `BinancePublicMarketDataAdapter` skeleton | Future no-egress skeleton candidate. | Must use fake-server / fixture base URL in tests; no default real Binance host. |
| `FutureRealPublicMarketDataAdapter` | Naming placeholder only. | Future contract label; no implementation, no provider, no outbound. |

Private `TradingAdapter` must remain separate. It must not be reused as the public marketdata adapter abstraction because it carries private trading, account, order, credential, readiness, and authorization semantics that are forbidden in GateN.

## DTO / Capability / Readiness Model Proposal

DTOs must contain public marketdata fields only.

Allowed DTO candidates:

- `PublicMarketDataBar`: venue, instrument id, interval, open time, close time, open, high, low, close, volume, quote volume when public, source type, observed timestamp.
- `PublicInstrumentMetadata`: venue, instrument id, base asset, quote asset, instrument type, status, price precision, quantity precision, minimum quantity, minimum notional when public.
- `PublicTickerSnapshot`: venue, instrument id, last price, bid, ask, 24h high / low / volume when public, source type, observed timestamp.
- `PublicExchangeStatus`: venue, status, maintenance window when public, reason code, source type, observed timestamp.

Forbidden DTO fields:

- API key, secret, passphrase, token, cookie, signature, private key, mnemonic.
- account id, user id, balance, position, wallet, ledger, fee tier, funding record.
- order id, client order id, trade execution id, fill id from private accounts.
- transfer id, withdraw id, deposit address, withdrawal address.
- private response raw body, signed request, request headers, or auth headers.

Capability model:

- `MarketDataCapability.BARS`.
- `MarketDataCapability.INSTRUMENT_METADATA`.
- `MarketDataCapability.TICKER`.
- `MarketDataCapability.EXCHANGE_STATUS`.

Readiness decision model:

- `MarketDataReadinessDecision`.
- Required fields: venue, capability, source type, readiness status, allowed diagnostic flag, reason code, reason text, checked timestamp, no-egress flag, docs reference.
- `allowed` means public diagnostic use only. It must not mean trading authorization, order authorization, private account authorization, or LIVE readiness.

Readiness statuses:

- `FRESH`.
- `STALE`.
- `GAP`.
- `ERROR`.
- `DISABLED`.
- `PENDING_BACKEND_SUPPORT`.

## Source Taxonomy Mapping

| Source type | GateN meaning | GateN-3 skeleton use |
| --- | --- | --- |
| `LOCAL_DB` | Existing local marketdata facts. | Read-only inventory / readiness input; not adapter implementation in this review. |
| `FIXTURE` | Static deterministic public payload. | Preferred first source for parser and DTO contract tests after authorization. |
| `FAKE_SERVER` | Local fake HTTP server with deterministic public exchange payloads. | Preferred source for HTTP adapter skeleton tests after authorization. |
| `NO_EGRESS_SANDBOX` | Test harness proving no real external network call. | Mandatory guard for future implementation. |
| `PUBLIC_SANDBOX_CANDIDATE` | Future public network candidate after separate review. | Not implementation-ready and not default. |

## Forbidden Carry-Over List

The following must not be carried into the public marketdata skeleton:

- Private `TradingAdapter` execution semantics.
- Signed REST endpoints.
- Private REST endpoints.
- Private WebSocket channels.
- User data streams.
- Account, balance, position, wallet, funding, fee tier, bills, ledger, deposit, transfer, or withdraw endpoints.
- Order, place order, amend order, cancel order, batch order, open order, order history, execution history, or fills endpoints.
- Permission probe endpoints or any real permission probe execution path.
- Credential validation endpoints or credential-backed health probes.
- Real exchange private trading readiness.
- LIVE enablement.
- RealClient / real provider implementation.
- AI runtime or DH runtime.
- Any statement that public marketdata readiness equals trading authorization.

## No-Egress Implementation Constraints

Future implementation must be local-only by default.

Required constraints:

- No default real OKX / Binance / Bybit / Gate / Coinbase / Kraken base URL auto-call.
- No credential lookup for public fake-server / fixture tests.
- No signed request construction.
- No auth header construction.
- No private endpoint path registration.
- No outbound network unless the task explicitly authorizes a public-network profile after review.
- Fake-server base URL must be explicit and local-only.
- Unknown host, unknown path, unsupported method, unsupported symbol, malformed payload, timeout, and simulated rate-limit errors must fail closed.
- Logs, DTOs, fixtures, and error messages must not contain credential material or raw private payloads.

Default real-host contact must be a test failure.

## Test Expectations For Later Implementation

GateN-3 does not add tests. Later implementation must add tests before the skeleton can be accepted.

Required future test categories:

- Unit parser tests with deterministic OKX / Binance public fixtures.
- Fake-server contract tests for endpoint path, method, payload, status code, and error mapping.
- No-egress guard tests blocking real exchange hosts.
- Forbidden endpoint static / route tests for account, order, cancel, transfer, withdraw, user streams, credential, and permission probe paths.
- DTO hygiene tests proving no private/account/order/balance/credential fields.
- Readiness mapping tests for `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, and `PENDING_BACKEND_SUPPORT`.
- Simulated timeout tests.
- Simulated rate-limit error tests.
- Log redaction checks.

These tests must remain fake-server / fixture / no-egress only unless a later public-network review explicitly authorizes otherwise.

## GateN-4 Entry Criteria

GateN-4 may start only after a separate GateN-3 implementation has been authorized, implemented, reviewed, and accepted.

GateN-4 entry requirements:

- Public adapter skeleton exists and is accepted.
- Fake-server / fixture tests exist and pass.
- No-egress guard exists and passes.
- Forbidden endpoint tests exist and pass.
- DTO hygiene tests exist and pass.
- No backend / frontend / research / scripts / deploy / `.github` forbidden drift is present unless separately authorized.
- No real outbound, credential access, private trading, LIVE, AI, DH runtime, RealClient, real provider, or real permission probe exists.
- GateN-4 fixture smoke plan covers fixture OHLCV, instrument metadata, ticker/status, freshness, gap, timeout, and simulated rate-limit error.

GateN-4 must still be no-real / no-egress by default.

## P0 / P1 / P2 / P3 Findings

### P0

- None in this docs-only review.

Potential future P0:

- Any implementation that can place/cancel orders, transfer/withdraw funds, read private account data, expose credentials, run real permission probe, or enable LIVE.
- Any code path that contacts real exchange hosts by default in tests or local skeleton execution.

### P1

- Public adapter must not reuse private `TradingAdapter`; doing so would mix public diagnostics with private trading semantics.
- Public DTOs must not contain account, order, balance, credential, transfer, withdraw, signature, or private raw payload fields.
- Readiness decisions must not be named or interpreted as trading authorization.
- OKX / Binance skeletons must not carry over default real host auto-call behavior.

### P2

- `getTicker` and `getExchangeStatus` are public but can expand scope; they should be included as named capabilities while allowing `PENDING_BACKEND_SUPPORT` until fixtures and tests exist.
- Exact protocol fields still require official-docs delta-check before implementation.
- Package placement should be confirmed against the existing module layout in the implementation task to avoid churn.

### P3

- Final class names may change during implementation to match local style.
- Existing historical docs contain broad GateL / GateM / GateN wording; current GateN-3 document should be treated as the precise skeleton review baseline.

## Final Decision

**PASS / SKELETON PLAN REVIEW / READY TO COMMIT**

GateN-3 is accepted as a public marketdata adapter skeleton plan review baseline. It authorizes GateN-4 planning inputs and future implementation constraints, but does not authorize adapter code, fake-server code, test code, API, migration, CI workflow changes, real outbound calls, private trading, credentials, permission probe, LIVE, AI, DH runtime, RealClient, or real provider work.

## Recommended Next Task

`NQ-GATEN-4-MARKETDATA-SANDBOX-FIXTURE-SMOKE-PLAN-REVIEW`

GateN-4 should plan fixture smoke coverage for OHLCV, instrument metadata, ticker/status, freshness, gap, timeout, and simulated rate-limit error after GateN-3 skeleton implementation is separately authorized and accepted. It must remain fake-server / fixture / no-egress only unless a later task explicitly authorizes a different boundary.

Commit recommendation:

`docs(gaten): review public marketdata adapter skeleton`
