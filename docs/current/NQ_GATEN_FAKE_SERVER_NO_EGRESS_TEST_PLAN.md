# NQ-GATEN-2 Fake Server / No-Egress Public MarketData Test Plan

## Status

**PASS / TEST PLAN BASELINE / READY TO COMMIT**

This document is the GateN-2 fake-server / no-egress public marketdata test plan. It consumes GateN-0 reconciliation and GateN-1 public marketdata contract plan review, and provides the test boundary for later GateN-3 public adapter skeleton and GateN-4 sandbox fixture smoke work.

This is a planning-only and docs-only test plan. It does not implement a fake server, does not add test code, does not add API, does not add migration, does not modify CI, does not call real exchange APIs, and does not authorize LIVE, private trading, RealClient, real provider, credential access, or real permission probe execution.

## Current GateN-2 Decision

GateN-2 decision:

- Accept fake-server / no-egress public marketdata testing as the required next baseline before any adapter skeleton.
- Keep default tests local-only, deterministic, credential-free, and no-egress.
- Require explicit forbidden-endpoint coverage before any public adapter implementation.
- Treat fake server as a test source only, never as a real provider.
- Keep GateN implementation **NOT STARTED** until a separate implementation task is authorized.

Current baseline:

- GateN route: **Public MarketData / Exchange Sandbox Planning**.
- GateN current status: **PLAN ONLY / NOT IMPLEMENTED**.
- GateN implementation: **NOT STARTED**.
- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.
- Public marketdata readiness is not trading authorization.
- Current real outbound remains forbidden by default.

## Inputs From GateN-1

GateN-2 uses these GateN-1 inputs:

- Official docs remain the protocol source of truth.
- OKX and Binance are the minimum public marketdata planning targets.
- Bybit / Gate / Coinbase / Kraken remain candidate documentation entries only.
- Public source taxonomy:
  - `LOCAL_DB`.
  - `FIXTURE`.
  - `FAKE_SERVER`.
  - `NO_EGRESS_SANDBOX`.
  - `PUBLIC_SANDBOX_CANDIDATE`.
- Readiness states:
  - `FRESH`.
  - `STALE`.
  - `GAP`.
  - `ERROR`.
  - `DISABLED`.
  - `PENDING_BACKEND_SUPPORT`.
- Public/private separation rules:
  - Public marketdata adapter != private trading adapter.
  - Public WebSocket market stream != private user data stream.
  - Public readiness != trading authorization.
  - Historical live-0 != current LIVE readiness.
- Rate-limit / timeout / retry metadata must be source-derived and bounded.

GateN-2 does not re-author official protocol facts. Exact endpoint fields, enum values, timestamp units, pagination, weights, WebSocket limits, and deprecation notes must still be traced back to official docs before implementation.

## Fake-Server Contract Scope

Fake-server scope is limited to public marketdata response simulation.

Minimum OKX public payload families to simulate:

- `GET /api/v5/market/candles`.
- `GET /api/v5/market/history-candles`.
- `GET /api/v5/market/ticker` / `GET /api/v5/market/tickers`.
- `GET /api/v5/public/instruments`.
- `GET /api/v5/system/status`, if GateN-3 or GateN-4 consumes exchange status.

Minimum Binance public payload families to simulate:

- `GET /api/v3/klines`.
- `GET /api/v3/ticker/24hr`.
- `GET /api/v3/ticker/price`.
- `GET /api/v3/ticker/bookTicker`.
- `GET /api/v3/exchangeInfo`.
- Public WebSocket kline/ticker payload shapes only as deterministic fake-server messages, not real WebSocket outbound.

Candidate exchanges:

- Bybit / Gate / Coinbase / Kraken are not in the GateN-2 minimum fake-server scope.
- They may only be added after a separate official-docs contract extraction task.

Fake-server fixture rules:

- Fixtures must be deterministic.
- Fixtures must be versioned with a clear provider and endpoint label.
- Fixtures must not contain API key, secret, passphrase, token, signature, cookie, private key, mnemonic, account id, balance, order id from real accounts, withdrawal address, or raw credential material.
- Fixtures must not be copied from private/signed provider responses.
- Error fixtures must be sanitized and minimal; no raw provider full body dumps.
- Fake-server base URL must be explicit and local-only in later tests.
- Fake-server response metadata must include `sourceType=FAKE_SERVER` or equivalent assertion data once implemented.

## No-Egress Strategy

Default unit and integration tests must not access the real network.

No-egress strategy:

- Deny real exchange hostnames by default in test harnesses.
- Require fake-server base URL allowlist before any HTTP client can run in tests.
- Assert no credential lookup is required for public fake-server tests.
- Fail the test if a private/signed/account/trading endpoint path is requested.
- Fail the test if a real exchange host is contacted.
- Fail closed on unknown host, unknown path, unknown method, unsupported symbol, unsupported interval, malformed payload, timeout, and rate-limit simulation.
- Keep CI default no-egress unless a future separate task authorizes a bounded public outbound profile.

Explicitly forbidden real hosts in default tests:

- `okx.com`.
- `binance.com`.
- `bybit.com`.
- `gate.io`.
- `gate.com`.
- `coinbase.com`.
- `kraken.com`.

Future public outbound, if ever proposed, must require:

- Separate task.
- Separate review.
- Explicit profile.
- Official-docs endpoint contract.
- Timeout / retry / rate-limit policy.
- No credential access.
- No private endpoint access.
- No LIVE.
- User authorization.

## Forbidden Endpoint List

Forbidden endpoint classes:

- signed REST endpoints.
- private REST endpoints.
- private WebSocket channels.
- user data streams.
- account streams.
- login/authenticated WebSocket APIs.
- account, balance, position, wallet, funding, fee tier, bills, ledger, deposit, transfer, withdraw endpoints.
- order, place order, amend order, cancel order, batch order, open orders, order history, execution history, fills endpoints.
- permission probe endpoints or any real permission probe execution path.
- credential validation endpoints or credential-backed health probes.

Forbidden OKX examples by class:

- account and balance APIs.
- trade order / cancel / amend APIs.
- positions / bills / funding / transfer / withdraw APIs.
- private WebSocket order/account/position channels.
- any endpoint requiring OK-ACCESS-* headers or request signing.

Forbidden Binance examples by class:

- signed account endpoints.
- signed order / cancel / open order endpoints.
- user data stream listen-key lifecycle.
- private account / balance / trade / execution endpoints.
- any endpoint requiring API key, signature, timestamp signing, or account identity.

Forbidden interpretation:

- A fake-server response that resembles public kline or ticker does not prove real provider readiness.
- A public endpoint parser does not authorize private trading.
- A no-egress test pass does not authorize public internet egress.
- A stale/gap/fresh readiness result does not authorize trading.

## Test Category Matrix

| Category | Purpose | Minimum cases | Implementation phase | Boundary |
| --- | --- | --- | --- | --- |
| Unit fake parser tests | Parse deterministic public payloads | OKX candles/history-candles/ticker/instruments; Binance klines/ticker/exchangeInfo | Planned in GateN-2; implement in GateN-3 | No network, no credential. |
| Fake-server contract tests | Verify endpoint path, method, payload, error mapping | success, malformed, provider error, unsupported symbol, unsupported interval | Planned in GateN-2; implement in GateN-3/GateN-4 | Fake server only. |
| No-egress guard tests | Prove tests cannot contact real exchange hosts | block OKX/Binance/Bybit/Gate/Coinbase/Kraken hosts; allow only local fake server | Planned in GateN-2; required before GateN-3 code | Real host contact is failure. |
| Forbidden endpoint static / route tests | Prove private paths are not reachable | order, cancel, account, balance, transfer, withdraw, user stream, permission probe | Planned in GateN-2; implement in GateN-3 | Private route usage is failure. |
| Timeout / rate-limit simulated tests | Exercise bounded retry behavior | timeout, 429/rate-limit, provider unavailable, max attempts exhausted | Planned in GateN-2; implement after retry model exists | Infinite retry forbidden. |
| Stale / gap / disabled / error simulated tests | Exercise readiness states | `STALE`, `GAP`, `ERROR`, `DISABLED`, malformed payload | Planned in GateN-2; implement in GateN-3/GateN-4 | No READY promotion. |
| Readiness mapping tests | Map source health to diagnostic status | `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, `PENDING_BACKEND_SUPPORT` | Planned in GateN-2; implement when mapping exists | Not trading authorization. |
| Log redaction checks | Prove no sensitive material appears | no key, secret, passphrase, token, signature, private key, raw body dumps | Planned in GateN-2; implement with tests/log artifacts | Redaction failure blocks. |

## Fixture And Taxonomy Plan

Fixture source taxonomy expectations:

- `LOCAL_DB`: existing local NQ DB marketdata facts; no exchange call.
- `FIXTURE`: static deterministic payloads and expected normalized outputs.
- `FAKE_SERVER`: local fake exchange server serving deterministic public payloads.
- `NO_EGRESS_SANDBOX`: harness mode that proves real network access is blocked.
- `PUBLIC_SANDBOX_CANDIDATE`: future candidate only, not enabled by default.

Fixture naming plan:

- `exchange`.
- `marketType`.
- `endpointFamily`.
- `transport`.
- `caseType`.
- `officialDocsUrl`.
- `officialDocsCheckedAt`.

Required fixture case types:

- success bars.
- empty bars.
- stale bars.
- gap bars.
- malformed payload.
- unsupported symbol.
- unsupported interval.
- provider unavailable.
- rate limited.
- timeout.
- exchange maintenance / degraded status.
- disabled source.

Fixture hygiene:

- No credentials.
- No real account identifiers.
- No withdrawal addresses.
- No private order identifiers from real venues.
- No provider full response dumps when unnecessary.
- No sensitive headers.
- No hidden LIVE or real-provider wording.

## Readiness State Simulation Plan

GateN-2 requires deterministic simulated inputs for each readiness state:

| State | Simulation input | Expected result | Boundary |
| --- | --- | --- | --- |
| `FRESH` | recent closed bar sequence with no gap | diagnostic fresh marketdata state | Not trading-ready. |
| `STALE` | last bar older than threshold | stale status with last successful timestamp | Not fatal unless policy says so. |
| `GAP` | missing interval in expected sequence | gap status and gap count > 0 | No silent success. |
| `ERROR` | malformed payload, provider error, parse failure, or fake-server 5xx | error status with sanitized reason | No raw provider body. |
| `DISABLED` | source disabled or no-real mode | disabled status | Intentional no-real state. |
| `PENDING_BACKEND_SUPPORT` | backend/API field not implemented yet | pending status | Must not be displayed as ready. |

Unknown, null, partial, or inconsistent states must fail closed and must not become `FRESH`.

## Security Boundary

GateN-2 security boundary:

- No real exchange API call.
- No real public internet call by default.
- No private endpoint.
- No signed request.
- No credential read.
- No credential output.
- No credential validation.
- No permission probe real execution.
- No order, cancel, transfer, withdraw, account, balance, position, wallet, funding, ledger, or risk mutation.
- No RealClient.
- No real provider.
- No LIVE.
- No AI runtime.
- No DH runtime.
- No raw provider response body dumps in logs, docs, fixtures, or artifacts.

P0 blockers for later implementation:

- Any real outbound to OKX / Binance / Bybit / Gate / Coinbase / Kraken in default tests.
- Any credential material read or output.
- Any private/signed endpoint request.
- Any order/cancel/transfer/withdraw path.
- Any LIVE enablement.

## GateN-3 Entry Criteria

GateN-3 public adapter skeleton may start only if all criteria are met:

- GateN-2 test plan is accepted.
- GateN-3 is explicitly authorized as an implementation task.
- Fake-server endpoint scope is fixed for OKX and Binance minimum public marketdata.
- No-egress guard test design is explicit.
- Forbidden endpoint list is accepted.
- Fixture hygiene rules are accepted.
- Readiness state simulation matrix is accepted.
- Timeout / rate-limit / retry simulation plan is accepted.
- GateN-3 scope remains fake-server / fixture / no-egress only.
- GateN-3 still forbids real outbound, private trading, credentials, LIVE, AI, DH runtime, RealClient, real provider, and real permission probe.

Tests that must be planned before GateN-3:

- Unit fake parser tests.
- Fake-server contract tests.
- No-egress guard tests.
- Forbidden endpoint static / route tests.
- Readiness mapping tests.
- Log redaction checks.

Tests that may be implemented during GateN-3 or GateN-4 after authorization:

- Parser tests with deterministic fixtures.
- Local fake-server contract tests.
- No-egress harness tests.
- Simulated timeout / rate-limit / retry tests.
- Stale / gap / disabled / error simulated tests.
- Sandbox fixture smoke.

## Risks

- Fake-server can be mistaken for real provider proof.
- No-egress test pass can be mistaken for public outbound authorization.
- Public marketdata readiness can be mistaken for trading authorization.
- Historical live-0 evidence can be mistaken for current LIVE readiness.
- Official docs URL drift can make endpoint facts stale.
- Binance official docs legacy banner requires re-pinning active maintained docs before implementation.
- Candidate exchanges beyond OKX/Binance have not had endpoint contracts extracted.

## P0/P1/P2/P3 Findings

### P0

- None in this docs-only GateN-2 planning task.

Future P0 blockers:

- real outbound in default tests.
- credential read or output.
- LIVE enablement.
- private trading.
- order / cancel / transfer / withdraw.
- real permission probe execution.

### P1

- None blocking this GateN-2 test plan baseline.

Future P1 risks:

- public/private adapter mixing.
- historical live-0 interpreted as current readiness.
- fake server interpreted as real provider.
- no-egress pass interpreted as public outbound authorization.

### P2

- GateN-2 is still a test plan; fake server and no-egress tests are not implemented.
- Official docs URL drift remains a future implementation risk.
- Binance legacy-docs banner requires active-docs URL pinning before implementation.
- Bybit / Gate / Coinbase / Kraken candidate endpoint contracts remain unextracted.

### P3

- Fixture naming and taxonomy must stay consistent when code is later added.
- Keyword scans will continue to hit historical LIVE / real-provider / order / cancel wording in docs and backend; those hits must be classified by context.
- `NQ_NEXT_PHASE_PLAN.md` contains earlier GateN workstream naming drafts; current GateN-0/1/2 documents are the more precise current planning line.

## Final Decision

**PASS / TEST PLAN BASELINE / READY TO COMMIT**

GateN-2 is accepted as a fake-server / no-egress public marketdata test-plan baseline. It authorizes planning inputs for GateN-3 public adapter skeleton, but does not authorize implementation, fake-server code, test code, CI workflow changes, real outbound calls, private trading, credentials, permission probe, LIVE, AI, DH runtime, RealClient, or real provider work.

## Recommended Next Task

`NQ-GATEN-3-PUBLIC-MARKETDATA-ADAPTER-SKELETON-PLAN-REVIEW`

GateN-3 should remain fake-server / fixture / no-egress only unless the user separately authorizes implementation. It must not introduce real outbound, credentials, private trading, LIVE, AI, DH runtime, RealClient, real provider, or real permission probe behavior.

Commit recommendation:

`docs(gaten): plan fake-server no-egress marketdata tests`
