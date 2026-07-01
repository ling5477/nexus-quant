# NQ-GATEN-5 Runtime UI Sandbox Source Display Implementation Plan

## Status

**PASS / IMPLEMENTATION PLAN READY / READY TO COMMIT**

This document is a GateN implementation planning baseline. It does not implement frontend code, backend code, API, tests, fake-server runtime, adapter skeleton, real provider, RealClient, LIVE, AI runtime, DH runtime, or real exchange egress.

## Current GateN-5 Implementation Planning Decision

- GateN-5 Runtime UI Sandbox Source Display implementation remains **NOT STARTED**.
- The next implementation slice is limited to one compact sandbox/source display block inside the existing `/marketdata` Data Quality / Readiness area.
- The first slice must not add a sidebar entry, dashboard, standalone page, runtime readiness page change, backend endpoint, migration, CI workflow, or broad E2E expansion.
- The display must stay no-real by default: public marketdata readiness is not trading authorization.
- LIVE remains **DISABLED**.
- AI remains **NOT STARTED**.
- DH runtime remains **NOT_INTEGRATED**.
- RealClient / real provider remains **NOT_IMPLEMENTED**.
- Real exchange private trading remains **NOT_IMPLEMENTED**.
- Permission probe real execution remains **NOT_IMPLEMENTED**.

## Inputs From GateN-5 Plan Review

- GateN-5 plan review completed as **PASS / RUNTIME UI SANDBOX SOURCE DISPLAY PLAN REVIEW / READY TO COMMIT**.
- Accepted placement is the existing MarketData readiness/source-health area, not a new page.
- Accepted source taxonomy is `LOCAL_DB / FIXTURE / FAKE_SERVER / NO_EGRESS_SANDBOX / PUBLIC_SANDBOX_CANDIDATE`.
- Accepted readiness taxonomy is `FRESH / STALE / GAP / ERROR / DISABLED / PENDING_BACKEND_SUPPORT`.
- Accepted venue and capability display scope is `OKX / Binance` with `bars / instrument metadata / ticker / exchange status`.
- Accepted diagnostics are `reasonCode`, `reasonText`, `checkedAt`, `noEgress`, and `sourceLabel`.
- Historical live-0 evidence must not appear as a current UI readiness badge.

## Existing Frontend Evidence For Future Implementation

- `frontend/src/pages/marketdata/MarketdataPage.tsx` already has a Data Quality / Readiness view identified by `data-testid="marketdata-quality-readiness-view"`.
- The existing MarketData page already renders readiness, source health, backend support, freshness, gap counts, last success, last failure, and quality summary from local backend data.
- `frontend/src/api/marketdata.ts` already calls the existing marketdata readiness and bars endpoints.
- `frontend/src/types/marketdata.ts` already defines `MarketdataReadinessSummary` with `status`, `freshnessStatus`, `sourceHealthStatus`, `sourceHealthReason`, `backendSupportLevel`, `generatedAt`, and related local DB-derived fields.
- Runtime readiness navigation exists elsewhere in the frontend, but GateN-5 first slice must not modify it.

## Minimal UI Implementation Slice

Future GateN-5 implementation should add a compact `Sandbox source` block to the existing `/marketdata` Data Quality / Readiness area.

Required display behavior:

- `sourceType`
  - Show `LOCAL_DB` when the value is derived from current `/api/marketdata/readiness` or `/api/marketdata/bars` local backend facts.
  - Show `PENDING_BACKEND_SUPPORT` for fixture, fake-server, no-egress sandbox, or public sandbox candidate source facts unless a future approved backend contract supplies explicit fields.
  - Do not infer real provider status from local DB facts.
- `readiness`
  - Map existing readiness and source-health fields only into `FRESH / STALE / GAP / ERROR / DISABLED / PENDING_BACKEND_SUPPORT`.
  - If existing fields are missing or ambiguous, show `PENDING_BACKEND_SUPPORT`.
  - Never display trading or account authorization.
- `venue`
  - Display `OKX` or `Binance` only from existing marketdata query/context.
  - Do not probe a provider to discover venues.
- `capability`
  - Display `bars` from the existing marketdata bars/readiness flow.
  - Display `instrument metadata`, `ticker`, and `exchange status` as `PENDING_BACKEND_SUPPORT` unless the existing data model already provides current local facts.
- `diagnostic`
  - `reasonCode`: use existing status/source-health codes when available; otherwise `PENDING_BACKEND_SUPPORT`.
  - `reasonText`: use existing source-health reason or a local explanatory text; do not mention real readiness.
  - `checkedAt`: use existing `generatedAt` or local query timestamp if available.
  - `noEgress`: show `PENDING_BACKEND_SUPPORT` until an approved backend contract supplies an explicit no-egress fact; the UI may state that the first slice performs no browser-side exchange egress.
  - `sourceLabel`: use a UI-local label such as `Local DB marketdata readiness` or `Sandbox source pending backend support`.

Required UI states:

- No submitted query: compact empty state, no real-source claim.
- Loading: existing MarketData loading pattern.
- Backend readiness unavailable: `PENDING_BACKEND_SUPPORT` or `ERROR`, depending on the existing error state.
- Bars present with known quality: show local DB facts only.
- Stale, gap, or error state: display the mapped readiness and diagnostic reason without implying trading capability.

## Allowed Future File Ranges

Future implementation, if separately authorized, may only touch:

- `frontend/src/pages/marketdata/**`
- `frontend/src/api/marketdata.ts`, only for existing interface or light type adaptation; if a new endpoint or method is needed, stop and request an API plan.
- `frontend/src/types/marketdata.ts`, only for UI-safe source/readiness display types.
- `frontend/tests/e2e/**` or an existing component/smoke test location, only for the minimum assertion required by the implementation task.
- `docs/current/**` for status synchronization.
- `README.md`, only for GateN status and document index synchronization if the implementation task explicitly allows it.

Still forbidden in the future implementation slice:

- `backend/**`
- `research/**`
- `scripts/**`
- `deploy/**`
- `.github/**`
- database migration paths
- new API
- new page
- CI workflow changes
- real HTTP / WebSocket implementation
- adapter skeleton implementation
- fake-server runtime implementation
- RealClient / real provider implementation
- real permission probe
- credential access
- order, cancel, transfer, withdraw, account, or balance capability

## Data Source Constraints

- First slice data sources are limited to existing `/api/marketdata/readiness`, existing `/api/marketdata/bars`, existing local DB-derived facts, and current route/query context.
- No new API is allowed in the GateN-5 UI implementation slice.
- No dynamic fixture smoke result is allowed unless it is already exposed by an approved existing no-real contract.
- No fake-server runtime result is allowed because fake-server runtime is not implemented.
- No real exchange host, provider base URL, credential, signed endpoint, account endpoint, or permission probe may be used.
- Missing source fields must be represented as `PENDING_BACKEND_SUPPORT`, not guessed.

## UI Wording Rules

Allowed wording:

- `Sandbox`
- `Fixture`
- `No-egress`
- `Public candidate`
- `Local DB`
- `Pending backend support`
- `Public marketdata candidate`

Forbidden wording:

- `ready for live`
- `live ready`
- `real-ready`
- `provider ready`
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

Historical live-0 evidence must remain historical documentation evidence only and must not appear in the current UI source/readiness block.

## Validation Commands For Future Implementation

Future implementation must run at minimum:

```powershell
Set-Location frontend
npm run build
```

Future implementation must also run at least one smoke or component-level assertion that proves:

- The MarketData sandbox/source block renders in the existing `/marketdata` readiness area.
- It uses only allowed source/readiness wording.
- It does not show forbidden real/live/trading authorization wording.
- It does not require a real backend fixture-smoke result API.

Required future scans:

```powershell
rg -n "ready for live|live ready|real-ready|provider ready|trading authorized|account authorized|permission verified|private ready|LIVE_READY|TRADING_AUTHORIZED|REAL_PROVIDER_READY|PRIVATE_READY|ACCOUNT_AUTHORIZED|PERMISSION_VERIFIED" frontend/src/pages/marketdata frontend/tests/e2e docs/current
rg -n "secret|token|apiKey|passphrase|privateKey|mnemonic" frontend/src/pages/marketdata frontend/src/types/marketdata.ts frontend/src/api/marketdata.ts frontend/tests/e2e
rg -n "okx.com|binance.com|bybit.com|gate.io|gate.com|coinbase.com|kraken.com" frontend/src/pages/marketdata frontend/src/api/marketdata.ts frontend/tests/e2e
git diff -- backend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- backend/**/db/migration
```

## API Stop Condition

If implementation needs any field that cannot be derived from existing `/api/marketdata/readiness`, `/api/marketdata/bars`, local DB facts, or route/query context, the implementation must stop.

Stop and create a separate API plan request if any of the following is required:

- explicit backend `sourceType`
- explicit backend `noEgress`
- per-capability source diagnostics
- fixture-smoke result exposure
- fake-server runtime status
- provider public sandbox result
- real-host probe result
- credential, account, permission, private trading, or provider authorization status

## GateN-FREEZE Entry Criteria

GateN-FREEZE can start only after:

- GateN-0 exchange docs and existing adapter reconciliation remains accepted.
- GateN-1 public marketdata contract plan review remains accepted.
- GateN-2 fake-server / no-egress test plan remains accepted.
- GateN-3 public marketdata adapter skeleton plan review remains accepted.
- GateN-4 fixture smoke implementation remains implemented and self-reviewed.
- GateN-5 plan review remains accepted.
- GateN-5 implementation plan remains accepted.
- GateN-5 implementation is separately implemented and validated.

GateN-FREEZE must still confirm:

- LIVE is **DISABLED**.
- AI is **NOT STARTED**.
- DH runtime is **NOT_INTEGRATED**.
- RealClient / real provider is **NOT_IMPLEMENTED**.
- Real exchange private trading is **NOT_IMPLEMENTED**.
- Permission probe real execution is **NOT_IMPLEMENTED**.
- Public marketdata readiness is not trading authorization.

## P0/P1/P2/P3 Findings

- P0: None.
- P1: None.
- P2: Existing frontend/backend contracts do not expose explicit `sourceType`, `noEgress`, or per-capability diagnostics. Future implementation must render `PENDING_BACKEND_SUPPORT` or stop for an API plan instead of inventing provider facts.
- P3: Keep the first slice compact inside existing `/marketdata`; adding a new page or runtime dashboard would create scope bloat and weaken the GateN no-real boundary.

## Final Decision

**PASS / IMPLEMENTATION PLAN READY / READY TO COMMIT**

GateN-5 may proceed to a separately authorized minimal frontend implementation task. This plan does not authorize backend, API, adapter, fake-server runtime, real provider, private trading, LIVE, AI, DH runtime, credential access, or real exchange egress.

## Recommended Next Task

`NQ-GATEN-5-RUNTIME-UI-SANDBOX-SOURCE-DISPLAY-IMPLEMENTATION`

Recommended commit message:

```text
docs(gaten): plan runtime ui sandbox source display implementation
```
