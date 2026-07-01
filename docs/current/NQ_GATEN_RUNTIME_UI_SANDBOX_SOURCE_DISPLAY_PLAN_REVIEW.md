# NQ-GATEN-5 Runtime UI Sandbox Source Display Plan Review

## Status

**PASS / RUNTIME UI SANDBOX SOURCE DISPLAY PLAN REVIEW / READY TO COMMIT**

This document is a planning-only / docs-only review for GateN-5. It does not implement UI, does not modify frontend code, does not add API, does not add tests, does not change CI, and does not start any real provider, fake-server runtime, adapter skeleton, LIVE, AI, DH runtime, credential access, or permission probe execution.

## Current GateN-5 Decision

GateN-5 is accepted as a minimal display-planning baseline for sandbox marketdata source visibility.

- GateN-5 plan review status: **PASS / RUNTIME UI SANDBOX SOURCE DISPLAY PLAN REVIEW / READY TO COMMIT**.
- GateN-5 implementation status: **NOT STARTED**.
- GateN production adapter / API / runtime status: **NOT STARTED**.
- fake server runtime: **NOT IMPLEMENTED**.
- adapter skeleton: **NOT IMPLEMENTED**.
- real public outbound: **NOT STARTED**.
- private trading adapter: **NOT STARTED**.
- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.

GateN-5 may proceed only as a future implementation plan or separately authorized implementation. That future work must remain display-only unless a later Gate explicitly changes the boundary.

## Inputs From GateN-4

GateN-4 fixture smoke is already recorded as **IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**.

GateN-4 provides these inputs for GateN-5:

- Deterministic OKX / Binance fixture resources exist under test resources.
- Fixture families cover OHLCV bars, instrument metadata, ticker, exchange status, stale, gap, timeout simulated, rate-limit simulated, malformed payload, unsupported symbol, fake-server unavailable, and disabled source scenarios.
- Readiness mappings cover `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, and `PENDING_BACKEND_SUPPORT`.
- Fixture hygiene and no-egress assertions prove the test-only baseline does not require real hosts, credentials, private endpoints, signed endpoints, private trading adapters, permission probe real execution, order, cancel, transfer, withdraw, account, or balance paths.

GateN-4 does not provide production runtime source data, a fake-server process, a public marketdata adapter skeleton, a frontend API contract, or real provider readiness. GateN-5 UI planning must not infer those capabilities.

## UI Source / Readiness Display Scope

Allowed source taxonomy:

| Field | Allowed values | Display meaning |
| --- | --- | --- |
| `sourceType` | `LOCAL_DB`, `FIXTURE`, `FAKE_SERVER`, `NO_EGRESS_SANDBOX`, `PUBLIC_SANDBOX_CANDIDATE` | Diagnostic source family only. |
| `readiness` | `FRESH`, `STALE`, `GAP`, `ERROR`, `DISABLED`, `PENDING_BACKEND_SUPPORT` | Public marketdata diagnostic status only. |
| `diagnostic` | `reasonCode`, `reasonText`, `checkedAt`, `noEgress=true`, `sourceLabel` | Explain why the badge has its current state. |
| `venue` | `OKX`, `Binance` | Public marketdata venue label only. |
| `capability` | `bars`, `instrument metadata`, `ticker`, `exchange status` | Public data capability label only. |

Display requirements:

- Show `Sandbox`, `Fixture`, `No-egress`, or `Public candidate` labels where applicable.
- Show `noEgress=true` whenever the source is fixture, fake-server, or no-egress sandbox.
- Show `PENDING_BACKEND_SUPPORT` when an API or backend source field does not exist yet.
- Show unavailable/error states as fail-closed, not as a positive fallback.
- Keep `public marketdata readiness` visibly separate from trading, account, permission, or LIVE authorization.

Do not display:

- `real-ready`
- `live-ready`
- `provider-ready`
- `trading-authorized`
- `LIVE_READY`
- `TRADING_AUTHORIZED`
- `REAL_PROVIDER_READY`
- `PRIVATE_READY`
- `ACCOUNT_AUTHORIZED`
- `PERMISSION_VERIFIED`

## Page Placement Proposal

Preferred first placement: existing `/marketdata` page.

Rationale:

- `/marketdata` already consumes `/api/marketdata/bars` and `/api/marketdata/readiness`.
- The page already has a K-line readiness / source health area and can host a small sandbox source badge or source diagnostics block without changing navigation.
- A local block near the existing readiness panel keeps scope small and avoids implying a new runtime capability.

Secondary placement: existing `/runtime/readiness` page.

Rationale:

- `/runtime/readiness` already summarizes runtime boundaries, MarketData readiness, adapter no-real state, LIVE disabled state, and backend-support gaps.
- A future summary card may link to `/marketdata`, but should not duplicate detailed MarketData diagnostics.

Rejected for the first slice:

- A new top-level page.
- A new sidebar item.
- A broad dashboard redesign.
- A new Operations page.
- A full fake-server control console.

First implementation should modify only an existing page local block after separate authorization. The expected UI shape is a compact source/readiness diagnostic panel plus sandbox/no-egress badges, not a complete new workflow.

## Data Source Assumptions

Allowed first-slice data sources without new API:

- Existing `/api/marketdata/readiness` fields: `status`, `freshnessStatus`, `sourceHealthStatus`, `sourceHealthReason`, `backendSupportLevel`, `generatedAt`.
- Existing `/api/marketdata/bars` and local DB facts for bar count, first/last bar time, freshness estimate, and gap/quality fallback.
- Existing frontend route/query context for venue, market type, symbol, interval, and runtime deep link.
- Static frontend-safe labels derived from GateN documentation only where the label is clearly a plan/sandbox marker and not a runtime claim.

Not available without a separate API plan:

- Dynamic GateN fixture smoke execution results.
- Fake-server availability.
- Per-venue public sandbox candidate liveness.
- Runtime `noEgress=true` generated by backend.
- Any real provider health, credential health, private endpoint health, permission-probe result, account authorization, or LIVE authorization.

If a future implementation cannot truthfully display the required data from existing safe sources, it must stop and become an API plan/review task. This GateN-5 review does not authorize a new backend API.

## Forbidden UI Wording

Allowed wording examples:

- `Sandbox source`
- `Fixture source`
- `No-egress sandbox`
- `Public candidate`
- `Diagnostic only`
- `Pending backend support`
- `Source health unavailable`
- `No trading authorization`
- `LIVE disabled`

Forbidden wording:

- `ready for live`
- `live ready`
- `real-ready`
- `provider ready`
- `real provider ready`
- `trading authorized`
- `account authorized`
- `permission verified`
- `private ready`
- `LIVE_READY`
- `TRADING_AUTHORIZED`
- `REAL_PROVIDER_READY`
- `PRIVATE_READY`
- `ACCOUNT_AUTHORIZED`
- `PERMISSION_VERIFIED`

Historical live-0 evidence must not appear as a current UI readiness badge. It may remain only in docs/archive or historical evidence contexts.

## Implementation Constraints

This plan review does not authorize implementation.

Future implementation constraints:

- Prefer one local block in `/marketdata`; optionally add a small summary/deep link in `/runtime/readiness`.
- Do not add a new page in the first slice.
- Do not add a new API unless a separate API plan is approved first.
- Do not add E2E unless separately authorized; the minimum future implementation should include at least one smoke test or component-level assertion.
- Do not call real OKX / Binance / Bybit / Gate / Coinbase / Kraken hosts.
- Do not read credentials, environment secrets, account material, balances, orders, private streams, or permission probe results.
- Do not reuse private `TradingAdapter`.
- Do not display actions for order, cancel, transfer, withdraw, account, balance, credential, permission probe, LIVE, AI, or DH runtime.
- Do not present fixture smoke as real exchange connectivity.

## Validation Expectations

Future implementation must validate:

- Frontend build passes.
- At least one smoke test or component-level assertion covers the sandbox/source display.
- UI fails closed when backend readiness is unavailable or incomplete.
- UI does not contain secret/token/apiKey/passphrase-like material.
- UI does not contain forbidden readiness strings such as `LIVE_READY`, `TRADING_AUTHORIZED`, `REAL_PROVIDER_READY`, `PRIVATE_READY`, `ACCOUNT_AUTHORIZED`, or `PERMISSION_VERIFIED`.
- No real exchange host appears as an active runtime target.
- Forbidden-scope diffs remain empty unless a later task explicitly authorizes a different scope.

This docs-only review itself requires only documentation diff/keyword/boundary checks.

## GateN-FREEZE Entry Criteria

GateN-FREEZE may start only after:

- GateN-0 reconciliation remains current.
- GateN-1 contract review remains current.
- GateN-2 fake-server/no-egress test plan remains current.
- GateN-3 adapter skeleton plan review remains current.
- GateN-4 fixture smoke implementation remains implemented and validated.
- GateN-5 implementation is separately authorized, implemented, validated, and accepted.
- GateN-0 through GateN-5 statuses are synchronized in `docs/current`.
- P0/P1/P2 blockers are zero or explicitly accepted by the user.

GateN-FREEZE still must not enable LIVE, AI runtime, DH runtime, private trading, RealClient, real provider, real permission probe, credential access, or real exchange private trading.

## P0 / P1 / P2 / P3 Findings

### P0

- None in this docs-only plan review.

Potential future P0:

- UI or API path triggers real exchange host, credential access, permission probe real execution, private trading adapter, order/cancel/transfer/withdraw, or LIVE action.

### P1

- None blocking this plan review.

Potential future P1:

- UI wording implies live-ready, provider-ready, trading authorization, account authorization, permission verification, or private readiness.
- UI treats historical live-0 evidence as current runtime readiness.
- UI falls back from fake-server unavailable to a real host.

### P2

- Dynamic GateN fixture smoke results are not exposed through an API. A future UI implementation must either use existing safe readiness/local DB facts or stop for an API plan.
- Fake-server runtime remains not implemented, so `FAKE_SERVER` must be shown only as a planned/source taxonomy label unless future runtime support is separately implemented.

### P3

- First slice should avoid a new page and keep source/readiness diagnostics close to the existing MarketData readiness panel.
- Wording should stay consistent across `/marketdata` and `/runtime/readiness` to avoid status drift.

## Final Decision

**PASS / RUNTIME UI SANDBOX SOURCE DISPLAY PLAN REVIEW / READY TO COMMIT**

GateN-5 is approved as a display-planning baseline only. The next work item should plan a minimal UI implementation, but no UI code, frontend route, API, test code, fake-server runtime, adapter skeleton, real outbound, credential access, private trading, LIVE, AI, DH runtime, RealClient, real provider, or permission probe work is started by this review.

## Recommended Next Task

`NQ-GATEN-5-RUNTIME-UI-SANDBOX-SOURCE-DISPLAY-IMPLEMENTATION-PLAN`

Recommended commit:

`docs(gaten): plan runtime ui sandbox source display`
