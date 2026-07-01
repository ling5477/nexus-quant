# NQ-GATEN-4 MarketData Sandbox Fixture Smoke Plan Review

## Status

**PASS / FIXTURE SMOKE PLAN REVIEW / READY TO COMMIT**

This document is the GateN-4 marketdata sandbox fixture smoke plan review. It consumes GateN-3 public marketdata adapter skeleton plan review and defines the minimum future fixture smoke scope, fixture hygiene rules, readiness simulation matrix, timeout / rate-limit / malformed payload simulation, no-egress validation plan, and GateN-5 entry criteria.

This is a planning-only and docs-only review. It does not implement fixture smoke tests, does not add test code, does not implement a fake server, does not implement an adapter skeleton, does not add API, does not add migration, does not modify CI, does not call real exchange APIs, and does not authorize LIVE, private trading, RealClient, real provider, credential access, or real permission probe execution.

Implementation planning status: [NQ-GATEN-4 MarketData Sandbox Fixture Smoke Implementation Plan](NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_IMPLEMENTATION_PLAN.md) = **PASS / IMPLEMENTATION PLAN READY / READY TO COMMIT**. It defines the minimum future implementation slice, future allowed file ranges, fixture set, readiness expectation matrix, no-egress verification design, future validation commands, and GateN-5 entry criteria. It still does not start GateN implementation.

## Current GateN-4 Decision

GateN-4 decision:

- Accept deterministic fixture / fake-server / no-egress sandbox fixture smoke as the minimum future acceptance baseline.
- Require coverage for public OHLCV bars, instrument metadata, ticker, and exchange status.
- Require readiness simulation for `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, and `PENDING_BACKEND_SUPPORT`.
- Require timeout, rate-limit simulated error, and malformed payload simulation.
- Keep all future GateN-4 execution local-only by default.
- Keep GateN implementation **NOT STARTED** until a separate implementation task is authorized.

Current baseline:

- GateN route: **Public MarketData / Exchange Sandbox Planning**.
- GateN current status: **PLAN ONLY / NOT IMPLEMENTED**.
- GateN implementation: **NOT STARTED**.
- fake server: **NOT_IMPLEMENTED**.
- adapter skeleton: **NOT_IMPLEMENTED**.
- sandbox fixture smoke: **NOT_IMPLEMENTED**.
- test code: **NOT_ADDED**.
- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.
- Public marketdata readiness is not trading authorization.
- Current real outbound remains forbidden by default.

## Inputs From GateN-3

GateN-4 uses these GateN-3 inputs:

- `NqPublicMarketDataAdapter` or equivalent public-only interface remains a proposal, not implemented.
- `getBars` and `getInstrumentMetadata` are the first-slice public surface candidates.
- `getTicker` and `getExchangeStatus` are named public capability candidates, but may remain `PENDING_BACKEND_SUPPORT` until fixtures and implementation exist.
- Public DTOs must contain only public marketdata fields.
- Public adapter skeleton must not reuse private `TradingAdapter`.
- Source taxonomy remains `LOCAL_DB`, `FIXTURE`, `FAKE_SERVER`, `NO_EGRESS_SANDBOX`, and `PUBLIC_SANDBOX_CANDIDATE`.
- No-egress remains mandatory; default real-host contact is a failure.
- Future public outbound requires separate task, separate review, explicit profile, official-docs contract, timeout / retry / rate-limit policy, no credential access, no private endpoint access, no LIVE, and user authorization.

GateN-4 does not re-author official protocol facts. Exact payload fields, endpoint paths, enum values, timestamp units, pagination, weights, WebSocket limits, and deprecation notes must still be traced back to official docs before implementation.

## Fixture Smoke Scope

GateN-4 fixture smoke scope is public marketdata only.

Minimum fixture families:

| Fixture family | Minimum smoke purpose | Source boundary |
| --- | --- | --- |
| OHLCV bars | Prove public bar parsing, interval mapping, timestamp ordering, and source tagging. | `FIXTURE` or `FAKE_SERVER`, no real network. |
| Instrument metadata | Prove symbol / instrument id normalization, base / quote asset mapping, instrument status, and precision metadata. | `FIXTURE` or `FAKE_SERVER`, no credential. |
| Ticker | Prove public ticker snapshot parsing and diagnostic freshness calculation. | `FIXTURE` or `FAKE_SERVER`; may remain `PENDING_BACKEND_SUPPORT` if not implemented. |
| Exchange status | Prove public exchange status or maintenance diagnostic mapping. | `FIXTURE` or `FAKE_SERVER`; may remain `PENDING_BACKEND_SUPPORT` if not implemented. |
| Source taxonomy mapping | Prove `FIXTURE`, `FAKE_SERVER`, and `NO_EGRESS_SANDBOX` are explicit in outputs. | No implicit real provider. |
| Freshness / stale / gap / error / disabled | Prove readiness simulation is diagnostic and fail-closed. | No trading authorization. |
| Timeout simulation | Prove bounded timeout handling and explicit diagnostic failure. | No real timeout against external host. |
| Rate-limit simulated error | Prove 429 / rate-limit style error mapping without retry loops. | Simulated only. |
| Malformed payload simulation | Prove bad payloads map to `ERROR` and do not produce ready data. | Sanitized fixture only. |

Out of scope:

- Real public network calls.
- Real exchange WebSocket.
- Private user data stream.
- Account / balance / position / wallet APIs.
- Order / cancel / amend / fills APIs.
- Transfer / withdraw / deposit APIs.
- Credential validation.
- Permission probe.
- Runtime ingestion or scheduling.
- New frontend page, backend API, E2E, migration, or CI workflow.

## Fixture Hygiene Rules

Fixture rules:

- Fixtures must be deterministic.
- Fixtures must be repository-controlled or generated from sanitized synthetic data.
- Fixtures must be versioned with provider, endpoint family, source type, scenario, and schema intent.
- Fixtures must be offline-runnable.
- Fixtures must not require internet access.
- Fixtures must not require credential lookup.
- Fixtures must label `source=FIXTURE`, `source=FAKE_SERVER`, or `source=NO_EGRESS_SANDBOX`.
- Fixtures must not be copied directly from real API raw dumps.
- Fixtures must not contain credential, secret, token, signature, private key, passphrase, cookie, raw auth header, account id, private order id, private trade id, balance, wallet, transfer, withdraw, deposit address, or raw private provider response material.
- Error fixtures must be sanitized and minimal; no raw full response dumps.

Fixture review must fail if a fixture implies real provider connectivity, real account identity, private endpoint access, or trading authorization.

## Readiness Simulation Matrix

| Readiness state | Minimum simulation | Required interpretation |
| --- | --- | --- |
| `FRESH` | Deterministic bar / ticker timestamp within accepted freshness window. | Public diagnostic fresh only; not trading authorization. |
| `STALE` | Deterministic public data older than freshness threshold. | Diagnostic stale; no ready promotion. |
| `GAP` | Missing bar interval or discontinuity in ordered public bars. | Diagnostic data quality gap; not account or permission status. |
| `ERROR` | Malformed payload, unsupported schema, simulated provider error, or parse failure. | Fail closed; no ready data output. |
| `DISABLED` | Source disabled by explicit fixture/sandbox setting. | Intentional disabled state; not an implementation failure and not real-ready. |
| `PENDING_BACKEND_SUPPORT` | Ticker / exchange status or mapping not implemented yet. | Valid planned gap; must not be converted to `FRESH` or trading-ready. |

Readiness rules:

- Any readiness result is diagnostic only.
- Readiness must not authorize order, cancel, transfer, withdraw, private account access, permission probe, LIVE, RealClient, or real provider.
- Ticker and exchange status may remain `PENDING_BACKEND_SUPPORT` until explicitly implemented.
- Unknown state must fail closed as `ERROR` or `PENDING_BACKEND_SUPPORT`, not `FRESH`.

## Timeout / Rate-Limit / Malformed Payload Simulation

Timeout simulation:

- Use deterministic fake delay or explicit timeout fixture, not real external latency.
- Result must map to `ERROR` or equivalent fail-closed diagnostic status.
- No infinite retry.
- No retry that crosses into real host.

Rate-limit simulated error:

- Use sanitized synthetic 429 / rate-limit payload shape.
- Result must include explicit rate-limit reason code.
- Retry policy, if later implemented, must be bounded and testable.
- Rate-limit simulation must not imply real provider connectivity.

Malformed payload simulation:

- Include missing required field, wrong type, unsupported interval, unsupported symbol, and invalid timestamp cases.
- Result must fail closed.
- Parser must not produce partial ready data from malformed payload.
- Error messages must not include raw private payloads or credential-like values.

## No-Egress Validation Plan

Default future tests must not access real network.

Forbidden real hosts in default tests:

- `okx.com`.
- `binance.com`.
- `bybit.com`.
- `gate.io`.
- `gate.com`.
- `coinbase.com`.
- `kraken.com`.

No-egress validation expectations:

- Block real exchange hosts by default.
- Allow only local fixture or explicit local fake-server sources.
- Fail closed on unknown host.
- Fail closed on unknown path.
- Fail closed on private endpoint path.
- Fail closed on signed endpoint path.
- Fail closed on credential lookup.
- Fail closed on real permission probe execution.
- Assert output source type is not implicit real provider.

Real public outbound remains forbidden unless a future task provides:

- Separate task.
- Separate review.
- Explicit public-network profile.
- Official-docs endpoint contract.
- Timeout / retry / rate-limit policy.
- No credential access.
- No private endpoint access.
- No LIVE.
- User authorization.

## Forbidden Carry-Over List

The following must not be carried into GateN-4 fixture smoke:

- Private `TradingAdapter` invocation.
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
- Any statement that fake server is real provider.
- Any statement that sandbox fixture smoke is real exchange connectivity.
- Any statement that public marketdata readiness equals trading authorization.

## GateN-5 Entry Criteria

GateN-5 Runtime UI Sandbox Source Display may start only after GateN-4 fixture smoke implementation has been separately authorized, implemented, verified, and accepted.

GateN-5 entry requirements:

- GateN-4 fixture smoke implementation exists and is accepted.
- Fixture OHLCV, instrument metadata, ticker/status, freshness, gap, timeout, rate-limit, and malformed payload scenarios are verified.
- No-egress validation passes.
- Forbidden endpoint and credential checks pass.
- Source taxonomy is visible to the future UI as diagnostic source metadata.
- Readiness states remain diagnostic only.
- No real outbound, credential access, private trading, LIVE, AI, DH runtime, RealClient, real provider, or real permission probe exists.

GateN-5 UI constraints:

- Display source, readiness, freshness, gap, and diagnostic status only.
- Do not display real-ready, live-ready, trading-authorized, account-authorized, or permission-probe-ready state.
- Do not add order, cancel, transfer, withdraw, account, balance, credential, or permission-probe actions.
- Keep LIVE / AI / DH runtime / private trading forbidden.

## P0 / P1 / P2 / P3 Findings

### P0

- None in this docs-only review.

Potential future P0:

- Any fixture smoke implementation that contacts real exchange hosts by default.
- Any implementation that reads credentials, calls private endpoints, places/cancels orders, transfers/withdraws funds, runs real permission probe, enables LIVE, or presents sandbox fixture smoke as real exchange connectivity.

### P1

- Fixture smoke must not invoke private `TradingAdapter`.
- Fixture payloads must not be raw real API dumps or contain credential/account/order/balance/private payload material.
- Readiness simulation must not become trading authorization.
- Fake server and fixture source must not be named or displayed as real provider.

### P2

- `ticker` and `exchangeStatus` may remain `PENDING_BACKEND_SUPPORT` until implementation exists; tests must not force premature support.
- Official-docs delta-check remains required before any exact provider protocol implementation.
- Future UI display must keep source taxonomy and diagnostic-only wording explicit.

### P3

- Scenario naming should remain stable so later implementation can map docs, fixtures, and tests without churn.
- Historical docs contain broad GateL / GateM / GateN wording; current GateN-4 document should be treated as the precise fixture smoke review baseline.

## Final Decision

**PASS / FIXTURE SMOKE PLAN REVIEW / READY TO COMMIT**

GateN-4 is accepted as a marketdata sandbox fixture smoke plan review baseline. It authorizes GateN-5 planning inputs and future fixture-smoke implementation constraints, but does not authorize fixture smoke code, fake-server code, adapter code, test code, API, migration, CI workflow changes, real outbound calls, private trading, credentials, permission probe, LIVE, AI, DH runtime, RealClient, or real provider work.

## Recommended Next Task

`NQ-GATEN-4-MARKETDATA-SANDBOX-FIXTURE-SMOKE-IMPLEMENTATION-PLAN`

This next task must be separately authorized. It must remain deterministic fixture / fake-server / no-egress only, and must not implement real outbound, credentials, private trading, LIVE, AI, DH runtime, RealClient, real provider, or real permission probe behavior.

Commit recommendation:

`docs(gaten): review marketdata sandbox fixture smoke`
