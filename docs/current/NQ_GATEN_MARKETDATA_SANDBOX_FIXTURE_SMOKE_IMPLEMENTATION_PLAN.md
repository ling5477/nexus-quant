# NQ-GATEN-4 MarketData Sandbox Fixture Smoke Implementation Plan

## Status

**PASS / IMPLEMENTATION PLAN READY / READY TO COMMIT**

This document is the GateN-4 marketdata sandbox fixture smoke implementation plan. It consumes the GateN-4 fixture smoke plan review and defines the smallest future implementation slice, future allowed file ranges, fixture set, readiness expectation matrix, no-egress verification design, validation commands, GateN-5 entry criteria, and forbidden carry-over list.

This task is implementation planning only. It does not implement fixture smoke code, does not add tests, does not implement a fake server, does not implement an adapter skeleton, does not add API, does not add migration, does not modify CI, does not call real exchange APIs, and does not authorize LIVE, private trading, RealClient, real provider, credential access, or real permission probe execution.

## Current GateN-4 Implementation Planning Decision

Decision:

- Accept a future GateN-4 implementation slice only if it remains deterministic fixture / local fake-server / no-egress.
- Keep the first future implementation slice test-oriented and backend-local.
- Do not implement real HTTP client behavior in the first slice.
- Do not implement WebSocket behavior in the first slice.
- Do not implement private trading adapter behavior in the first slice.
- Do not add backend API or frontend UI in the first slice.
- Do not make fixture smoke a prerequisite for LIVE, trading authorization, real provider readiness, or permission probe readiness.

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

## Inputs From GateN-4 Plan Review

GateN-4 plan review accepted these inputs:

- Deterministic fixture smoke is the minimum future acceptance baseline.
- Minimum fixture families are OHLCV bars, instrument metadata, ticker, exchange status, source taxonomy mapping, freshness / stale / gap / error / disabled states, timeout simulation, rate-limit simulated error, and malformed payload simulation.
- Readiness states are `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, and `PENDING_BACKEND_SUPPORT`.
- `ticker` and `exchangeStatus` may stay `PENDING_BACKEND_SUPPORT` if the first implementation slice does not implement them.
- No-egress validation must block real exchange hosts by default.
- Unknown host, unknown path, private endpoint, signed endpoint, credential lookup, and real permission probe execution must fail closed.
- GateN-5 Runtime UI Sandbox Source Display may start only after fixture smoke implementation is separately authorized, implemented, verified, and accepted.

GateN-4 plan review does not authorize current implementation. It supplies constraints for a later implementation task only.

## Minimal Implementation Slice

The recommended future implementation slice is:

1. Add deterministic fixture resources for public marketdata scenarios only.
2. Add a local fixture loader or test-local fake source only if needed by tests.
3. Add fixture smoke tests that parse and classify fixture payloads without external network.
4. Add readiness mapping tests for `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, and `PENDING_BACKEND_SUPPORT`.
5. Add no-egress guard assertions for real exchange host denial and private/signed endpoint denial.
6. Add fixture hygiene checks for no credential-like material and no private account/order/balance/transfer/withdraw payloads.

The first future implementation slice must not include:

- Real HTTP client.
- Real WebSocket client.
- Real public internet call.
- Private `TradingAdapter` invocation.
- Production adapter rewiring.
- New backend API.
- New frontend page.
- New E2E.
- New migration.
- CI workflow change.
- Scheduler, ingestion job, or runtime poller behavior.

Expected future result if implemented and verified:

```text
IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT
```

That future status is allowed only after code/tests actually exist and validation commands pass.

## Allowed Future Implementation File Ranges

These ranges are for the separately authorized future implementation task. They are not modified by this planning task.

Preferred future test/resource ranges:

- `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/**`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gaten/marketdata/**`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/**` only for no-egress guard reuse or a narrow smoke test.
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/application/**` only for pure readiness mapping unit tests.
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/marketdata/infra/fixture/**` only if reusing existing fixture-port patterns without runtime outbound behavior.

Allowed future test support reuse:

- `ExchangeNoOutboundGuard` style test-scope guard from `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/`.
- Existing marketdata fixture patterns such as `FixtureHistoricalMarketDataPortTest`.
- Existing readiness domain tests such as `MarketdataReadinessServiceTest`.

Forbidden future file ranges unless a later task separately authorizes them:

- `backend/**/src/main/**` production adapter or runtime wiring changes.
- `backend/nq-api/**` API controller / DTO changes.
- `backend/**/db/migration/**`.
- `frontend/**`.
- `research/**`.
- `scripts/**`.
- `deploy/**`.
- `.github/**`.

Production adapter real connection logic must not be touched in the first fixture-smoke implementation.

## Fixture Set

Future fixture file names should be stable and scenario-explicit.

Recommended directory shape:

```text
backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/
  okx/public/ohlcv_btc_usdt_1m_fresh.json
  okx/public/ohlcv_btc_usdt_1m_stale.json
  okx/public/ohlcv_btc_usdt_1m_gap.json
  okx/public/instrument_btc_usdt_spot.json
  okx/public/ticker_btc_usdt_fresh.json
  okx/public/exchange_status_available.json
  okx/public/timeout_simulated.json
  okx/public/rate_limit_429_simulated.json
  okx/public/malformed_missing_timestamp.json
  okx/public/malformed_wrong_type.json
  okx/public/unsupported_symbol.json
  okx/public/fake_server_unavailable.json
  binance/public/ohlcv_btc_usdt_1m_fresh.json
  binance/public/ohlcv_btc_usdt_1m_stale.json
  binance/public/ohlcv_btc_usdt_1m_gap.json
  binance/public/instrument_btc_usdt_spot.json
  binance/public/ticker_btc_usdt_fresh.json
  binance/public/exchange_status_available.json
  binance/public/timeout_simulated.json
  binance/public/rate_limit_429_simulated.json
  binance/public/malformed_missing_timestamp.json
  binance/public/malformed_wrong_type.json
  binance/public/unsupported_symbol.json
  binance/public/fake_server_unavailable.json
```

Required fixture families:

| Fixture | Purpose | Required boundary |
| --- | --- | --- |
| OHLCV bars | Parse bar timestamps, open/high/low/close/volume, interval ordering, and gap detection. | Public-only, synthetic/sanitized, no account fields. |
| Instrument metadata | Parse symbol, instrument id, base/quote, status, precision, and min size style metadata. | Public-only, no private account status. |
| Ticker | Parse last price / best bid-ask style snapshot if implemented. | May remain `PENDING_BACKEND_SUPPORT`. |
| Exchange status | Parse public exchange/system status if implemented. | May remain `PENDING_BACKEND_SUPPORT`. |
| Stale | Produce stale diagnostic state. | Must not promote to ready. |
| Gap | Produce gap diagnostic state. | Must not imply account permission issue. |
| Timeout simulated | Produce deterministic timeout diagnostic. | No real external latency. |
| Rate-limit simulated | Produce deterministic rate-limit diagnostic. | No real retry loop. |
| Malformed payload | Produce parse failure or invalid schema diagnostic. | Fail closed, no partial ready data. |
| Unsupported symbol | Produce unsupported input diagnostic. | No fallback to real provider. |
| Fake-server unavailable | Produce unavailable diagnostic. | No fallback to real host. |

Fixture hygiene requirements:

- No API key, secret, token, passphrase, signature, private key, mnemonic, cookie, auth header, account id, private order id, private trade id, balance, transfer, withdraw, deposit address, or private raw provider payload.
- No real host URL as an active endpoint.
- No copied raw private provider response.
- No logs or comments containing credential-like material.

## Readiness Expectation Matrix

| Scenario | Expected readiness | Expected support level | Forbidden interpretation |
| --- | --- | --- | --- |
| Fresh OHLCV bars | `FRESH` | `FIXTURE` or `NO_EGRESS_SANDBOX` | Not `LIVE_READY`; not `TRADING_AUTHORIZED`. |
| Fresh ticker | `FRESH` or `PENDING_BACKEND_SUPPORT` | `FIXTURE` or `PENDING_BACKEND_SUPPORT` | Not real provider proof. |
| Instrument metadata | `FRESH` or `PENDING_BACKEND_SUPPORT` | `FIXTURE` or `PENDING_BACKEND_SUPPORT` | Not account permission. |
| Exchange status available | `FRESH` or `PENDING_BACKEND_SUPPORT` | `FIXTURE` or `PENDING_BACKEND_SUPPORT` | Not venue trading authorization. |
| Stale fixture | `STALE` | `FIXTURE` | Not ready promotion. |
| Gap fixture | `GAP` | `FIXTURE` | Not account, balance, or private stream state. |
| Timeout simulated | `ERROR` | `NO_EGRESS_SANDBOX` | Not real latency measurement. |
| Rate-limit simulated | `ERROR` | `NO_EGRESS_SANDBOX` | Not real provider connectivity. |
| Malformed payload | `ERROR` | `FIXTURE` | No partial ready data. |
| Unsupported symbol | `ERROR` or `PENDING_BACKEND_SUPPORT` | `FIXTURE` | No fallback to live host. |
| Fake-server unavailable | `ERROR` or `DISABLED` | `FAKE_SERVER` | No fallback to real host. |
| Explicit disabled source | `DISABLED` | `FIXTURE` or `FAKE_SERVER` | Not implementation success. |

The future implementation must not introduce `LIVE_READY`, `TRADING_AUTHORIZED`, `REAL_PROVIDER_READY`, `PRIVATE_READY`, `ACCOUNT_AUTHORIZED`, or `PERMISSION_VERIFIED`.

## No-Egress Verification Design

Future tests must prove default no-egress behavior before any fixture smoke can be accepted.

Required checks:

- Real exchange hosts are denied by default: `okx.com`, `binance.com`, `bybit.com`, `gate.io`, `gate.com`, `coinbase.com`, and `kraken.com`.
- Unknown host fails closed.
- Unknown path fails closed.
- Private endpoint path fails closed.
- Signed endpoint path fails closed.
- Credential lookup fails closed or is not attempted.
- Real permission probe execution is not attempted.
- Fake-server unavailable does not fall back to real exchange host.
- Fixture parsing does not require DNS, internet, exchange credentials, or process env credentials.

Private/signed path denylist must include at least:

- account.
- balance.
- position.
- wallet.
- funding.
- order.
- cancel.
- amend.
- batch order.
- open orders.
- fills.
- execution history.
- user data stream.
- deposit.
- transfer.
- withdraw.
- permission probe.
- signed request paths or signature-bearing query/header paths.

The future implementation should prefer a test-scope guard over production rewiring. If a production guard change is needed, it must be split into a separate review task.

## Forbidden Carry-Over List

Forbidden in this planning task and in the first future implementation slice:

- Real HTTP client.
- Real WebSocket client.
- Real public internet call.
- Private `TradingAdapter` invocation.
- Signed REST endpoint.
- Private REST endpoint.
- Private WebSocket channel.
- User data stream.
- Account, balance, position, wallet, funding, fee tier, bills, ledger, deposit, transfer, or withdraw endpoint.
- Order, place order, amend order, cancel order, batch order, open order, order history, execution history, or fills endpoint.
- Credential validation endpoint.
- Credential-backed health probe.
- Real permission probe.
- LIVE enablement.
- RealClient.
- real provider.
- AI runtime.
- DH runtime.
- New API.
- New frontend page.
- New E2E.
- New migration.
- CI workflow change.
- Statement that fixture smoke is real exchange connectivity.
- Statement that fake server is a real provider.
- Statement that public marketdata readiness is trading authorization.

## Validation Commands For Future Implementation

Future GateN-4 implementation must run at least:

```powershell
git status --short
mvn -f backend/pom.xml -pl nq-app -am -Dtest=*GateN*MarketData*,*Marketdata*Fixture*,*NoOutbound* "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app -am -Dtest=*Marketdata*Readiness*,*Fixture*,*NoOutbound* "-Dsurefire.failIfNoSpecifiedTests=false" test
git diff --check
git diff --stat
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- "backend/**/db/migration"
rg "apiKey|secret|passphrase|private key|mnemonic|cookie|signature|Authorization|withdraw|transfer|order|cancel|balance|account|position|user data|okx.com|binance.com|bybit.com|gate.io|gate.com|coinbase.com|kraken.com" backend/nq-app/src/test/resources/gaten backend/nq-app/src/test/java backend/nq-core/src/test/java backend/nq-infra/src/test/java
rg "LIVE_READY|TRADING_AUTHORIZED|REAL_PROVIDER_READY|PRIVATE_READY|ACCOUNT_AUTHORIZED|PERMISSION_VERIFIED" backend docs/current
```

Static guard / ArchUnit requirement:

- Not mandatory for the first fixture-only implementation if no production package boundary changes occur.
- Required if the future implementation touches shared adapter abstractions, production source wiring, HTTP client setup, or package boundaries.

Log redaction requirement:

- Required if tests capture or assert error messages.
- Must verify no credential-like value, raw provider payload, Authorization header, signature, API key, secret, passphrase, account id, private order id, balance, transfer, withdraw, or private response body appears in logs or assertion output.

No-real-host string check:

- Required for fixture resources and tests.
- Real host strings are allowed only in denylist / forbidden assertion context, not as active connection URLs.

## GateN-5 Entry Criteria

GateN-5 Runtime UI Sandbox Source Display may start only after all criteria below are met in a separate future task:

- GateN-4 fixture smoke implementation is separately authorized.
- GateN-4 fixture smoke code/tests are implemented.
- Required Maven tests pass.
- No-egress validation passes.
- Fixture sensitive-word scan passes.
- No real host string check passes, except denylist/negative-context references.
- Forbidden endpoint checks pass.
- No credential material is loaded or output.
- Fixture/readiness outputs expose source taxonomy as diagnostic source metadata.
- Readiness states remain diagnostic only.
- GateN-4 implementation is recorded as `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT` or stronger after real validation.

GateN-5 must still remain display-only unless separately authorized. It must show source/readiness/diagnostic state only and must not display real-ready, live-ready, trading-authorized, account-authorized, permission-probe-ready, private-ready, order, cancel, transfer, withdraw, account, balance, or credential actions.

## P0 / P1 / P2 / P3 Findings

### P0

- None in this docs-only implementation planning task.

Potential future P0:

- Future fixture smoke contacts a real exchange host by default.
- Future implementation reads credentials, calls private endpoints, places/cancels orders, transfers/withdraws funds, runs real permission probe, enables LIVE, or presents fixture smoke as real exchange connectivity.

### P1

- First implementation must stay test/resource scoped and no-egress by default.
- Fixture payloads must not contain credential/account/order/balance/private payload material.
- Readiness states must not add or imply `LIVE_READY`, `TRADING_AUTHORIZED`, or `REAL_PROVIDER_READY`.
- Any production adapter, API, CI, frontend, migration, or real-network need must be split into a separate review task.

### P2

- The precise future test class names remain implementation choices, but they must map back to the fixture families and readiness matrix in this plan.
- Static/ArchUnit guards are optional only while implementation remains test/resource scoped; they become required if shared production boundaries change.
- `ticker` and `exchangeStatus` can remain `PENDING_BACKEND_SUPPORT`; forcing them into the first slice would enlarge scope.

### P3

- Scenario names should stay stable so future implementation can map docs, fixtures, and tests without churn.
- Future implementation should keep fixture JSON small and focused rather than storing full provider-like dumps.

## Final Decision

**PASS / IMPLEMENTATION PLAN READY / READY TO COMMIT**

GateN-4 fixture smoke implementation planning is accepted as the baseline for a future, separately authorized implementation task. It does not start GateN implementation and does not add fixture smoke code, fake server code, adapter code, test code, API, migration, CI workflow, real outbound calls, private trading, credentials, permission probe, LIVE, AI, DH runtime, RealClient, or real provider work.

## Recommended Next Task

`NQ-GATEN-4-MARKETDATA-SANDBOX-FIXTURE-SMOKE-IMPLEMENTATION`

That future task must be separately authorized and must remain deterministic fixture / local fake-server / no-egress only by default. It must target `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT` only after code and validation actually exist.

Commit recommendation:

`docs(gaten): plan marketdata sandbox fixture smoke implementation`
