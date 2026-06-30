# NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN

## Status

**PASS / PLAN ONLY / READY TO COMMIT**

This document is the GateN planning baseline for Public MarketData / Exchange Sandbox. It does not start GateN implementation.

## Current Baseline

GateM is already **FINALIZED / FROZEN / ACCEPTED / TAGGED**.

- GateM release tag: `nq-gatem-freeze`.
- Tagged commit: `64194844` (`docs(gatem): freeze GateM runtime readiness baseline`).
- Frozen baseline: no-real runtime readiness baseline.
- NQ-NEXT-PHASE-PLAN already recommends **GateN Public MarketData / Exchange Sandbox Planning**.
- GateN current status: **PLAN ONLY / NOT IMPLEMENTED**.

Current negative boundaries remain unchanged:

- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.
- MarketData readiness: diagnostic only, not trading authorization.
- Operational readiness: safe summary only, not LIVE authorization.

## Goal

Plan how GateN can build a safe public marketdata / exchange sandbox path without crossing into private exchange APIs, credentials, LIVE trading, real provider readiness, AI runtime, or DH runtime.

GateN should create contracts, boundaries, test strategy, and implementation batches for a future public-only or fake-source-only marketdata sandbox.

## Non-Goals

- Do not implement GateN in this task.
- Do not add backend API, DTO, migration, CI workflow, frontend page, E2E, scheduler behavior, deploy config, or business feature.
- Do not enable LIVE.
- Do not connect private OKX / Binance APIs.
- Do not read, validate, copy, print, or output credential material.
- Do not run real permission probe.
- Do not implement RealClient, real provider, real exchange private adapter, or wallet capability.
- Do not place orders, cancel orders, transfer, withdraw, query private account, query private balance, or query private positions.
- Do not start AI runtime, AI signal generation, AI Paper Trading, or AI-controlled trading.
- Do not start DH runtime integration or let DH affect NQ trading state.
- Do not write public marketdata readiness as trading readiness.
- Do not write sandbox readiness as production readiness.

## Allowed Scope

This planning task may only update current documentation:

- `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md`.
- `docs/current/NQ_NEXT_PHASE_PLAN.md` for GateN route sync only.
- `docs/current/STATUS.md`.
- `docs/current/ROADMAP.md`.
- `docs/current/README.md`.
- `docs/current/WORKLOG.md`.
- `docs/current/TESTING.md`.
- Root `README.md` for GateN planning entry and no-real boundary only.

## Forbidden Scope

This planning task must not modify:

- `frontend/**`.
- `backend/**`.
- `research/**`.
- `scripts/**`.
- `deploy/**`.
- `.github/**`.
- `backend/**/db/migration/**`.

It also must not create implementation artifacts, generated files, local secrets, raw logs, screenshots, trace files, test results, or deployment outputs.

## Existing Capability Checked

GateM and current code already provide the following read-only or fail-closed foundations:

- `GET /api/marketdata/readiness` summarizes local DB bars / ingestion facts and does not call adapter or external exchange.
- `DefaultAdapterReadinessService` is static and fail-closed, with no IO, credential, network, or side effect.
- `NoopMarketDataAdapter` returns `subscribed=false` with `NO_REAL_DISABLED`, so no-real marketdata is not reported as success.
- `OperationalReadinessService` returns a safe summary without adapter, permission probe, HTTP, database, file, or exchange client dependency.
- Runtime UI shows LIVE disabled, Paper-only, no-real adapter state, permission probe disabled / skipped, and no write endpoint usage.
- Marketdata UI displays readiness / quality status from existing APIs and states that it does not connect WebSocket, private exchange streams, trading signals, or indicators.

## GateN Positioning

GateN is:

- Public MarketData / Exchange Sandbox Planning.
- no-real / no-private / no-trading.
- A planning bridge from GateM fail-closed runtime readiness toward future real-provider preparation.
- A contract and test-strategy phase before any implementation.

GateN is not:

- LIVE.
- Real provider readiness.
- Private trading integration.
- Permission probe execution.
- AI / DH runtime integration.
- Production deployment readiness.

## Public MarketData Boundary

Allowed future public marketdata categories, after separate review:

- Public OHLCV bars.
- Public instrument metadata.
- Public exchange status / capability metadata.
- Fake-server responses that mimic public marketdata shape.
- Local DB / fixture-derived marketdata facts.
- No-egress sandbox tests proving parsing and error behavior without external calls.

Forbidden in GateN unless a later task explicitly re-authorizes after review:

- Private account data.
- Private balance, position, order, cancel, transfer, withdraw, deposit, funding, fee-tier, or user stream endpoints.
- Signed requests.
- API key / secret / passphrase / private key / mnemonic usage.
- Real permission probe.
- Any trading, risk, order, ledger, or account mutation.

Public marketdata readiness must remain diagnostic. It cannot promote:

- adapter readiness to trading readiness;
- sandbox availability to real provider readiness;
- `/actuator/health` to LIVE readiness;
- Paper/SIM readiness to LIVE authorization.

## Exchange Sandbox Boundary

GateN sandbox source types:

- `LOCAL_DB`: existing `marketdata_bars` and ingestion facts.
- `FIXTURE`: repository-controlled or test-controlled static data.
- `FAKE_SERVER`: local fake HTTP server with deterministic public payloads.
- `NO_EGRESS_SANDBOX`: test harness proving no external network calls.
- `PUBLIC_SANDBOX_CANDIDATE`: future candidate only; not default and not implementation-ready in this task.

Provider semantics:

- Fake provider: deterministic local-only source; never real provider.
- Stub provider: contract placeholder; never real provider.
- No-real provider: explicit disabled/fail-closed provider; never real provider.
- Future-real contract: checklist and acceptance criteria only; not implemented.
- Forbidden endpoint list: all private, signed, account, order, transfer, withdraw, and permission endpoints.

## Data Source Priority

GateN source priority is:

1. Existing local DB / fixture facts.
2. Fake-server public payloads.
3. No-egress sandbox harness.
4. Exchange public sandbox / testnet only as a later candidate after separate review.
5. Real public internet call only after explicit public-network review, timeout / retry / rate-limit design, no-egress tests, and user authorization.

Default behavior must never require live internet access.

## Adapter Contract Rules

GateN must keep these contracts separate:

- Public marketdata adapter != private trading adapter.
- Public marketdata adapter != permission probe.
- MarketData readiness != adapter trading readiness.
- Source health != account permission.
- Public sandbox != production provider.

Any future adapter contract must define:

- Allowed capabilities.
- Forbidden capabilities.
- Source type.
- Network policy.
- Timeout.
- Retry limit.
- Rate-limit behavior.
- Error mapping.
- Payload size limits.
- Logging redaction rules.
- No-egress test proof.
- Fallback / fail-closed behavior.

Unknown venue, unknown capability, malformed payload, unsupported operation, unavailable sandbox, stale data, source gap, rate limit, and timeout must all fail closed.

## Testing Strategy

GateN testing must be layered:

- Static no-egress guard: prove test path does not use real exchange endpoints or private hostnames.
- Fake-server contract test: prove parser, normalization, error mapping, stale data, malformed payload, and rate-limit behavior.
- Local DB fixture test: prove existing bars / ingestion facts remain usable without migration.
- Backend service unit test: verify source priority and fail-closed state transitions.
- Backend API test only after an API is separately authorized; this plan does not authorize one.
- Backend-free frontend smoke: only for display wording / state rendering, not real backend proof.
- Real local backend smoke: only after implementation is authorized and still no private exchange / credential / LIVE.

Forbidden default tests:

- Tests that call real exchange APIs.
- Tests that require real credentials.
- Tests that call private endpoints.
- Tests that depend on live internet as the only pass path.
- Tests that skip no-egress assertions.
- Tests that treat empty/no-data as public readiness success without explicit status.

## Frontend Impact Planning

Future UI work, if separately authorized, should show source state without implying trading readiness:

- Source type: `LOCAL_DB`, `FIXTURE`, `FAKE_SERVER`, `NO_EGRESS_SANDBOX`, `PUBLIC_SANDBOX_CANDIDATE`.
- Source status: `AVAILABLE`, `UNAVAILABLE`, `STALE`, `GAP_DETECTED`, `MALFORMED`, `RATE_LIMITED`, `TIMEOUT`, `BLOCKED`, `UNKNOWN_REQUIRES_REVIEW`.
- Runtime labels: `Public marketdata only`, `No private endpoint`, `No credential`, `No trading`, `LIVE disabled`.
- Detail state: latest timestamp, bar count, gap count, source health, backend support level, last checked time, and failure reason.

Frontend must not show:

- `live-ready`.
- `real provider ready`.
- `trading authorization`.
- `permission verified`.
- `private account connected`.
- `LIVE enabled`.

Any frontend action that starts ingestion, refreshes quality, or runs a source check must explain whether it is local-only / fake-server-only and must never hide no-real boundaries.

## Security Boundary

GateN must enforce:

- No credential material.
- No raw environment dump.
- No private endpoint.
- No signed request.
- No raw provider payload in logs or docs.
- No order / cancel / withdraw / transfer / private account operation.
- No LIVE.
- No AI runtime.
- No DH runtime.
- No RealClient / real provider.
- No real permission probe.
- No wallet, private key, mnemonic, or signing capability.

If future implementation uses HTTP for fake server or public sandbox, it must include:

- Explicit timeout.
- Bounded retry.
- Rate-limit policy.
- Payload size limit.
- Redacted logs.
- Error code mapping.
- No request / response body dumps.
- No sensitive headers.
- No transaction or account semantics.

## Workstream Breakdown

### GateN-1: Public MarketData Contract Plan Review

Allowed scope:

- Docs-only contract review.
- Source taxonomy.
- Allowed / forbidden public fields.
- Error model.
- Source priority.
- No-egress and fake-server test requirements.
- UI wording requirements.

Forbidden scope:

- Code.
- API.
- Migration.
- CI workflow.
- Real endpoint.
- Private endpoint.
- Credential.
- LIVE.

Acceptance criteria:

- Contract clearly separates public marketdata from private trading.
- Forbidden endpoint list is explicit.
- Source priority is explicit.
- P0/P1/P2 risks are recorded.
- First implementation slice is bounded and no-real.

### GateN-2: Fake Server / No-Egress Test Plan

Allowed scope:

- Test plan for local fake server.
- No-egress assertions.
- Fixture payload matrix.
- Malformed/stale/gap/rate-limit/timeout cases.
- Log redaction expectations.

Forbidden scope:

- Real exchange API call.
- Real public internet call by default.
- Credential or private endpoint.
- CI workflow implementation unless separately authorized.

Acceptance criteria:

- Test harness can prove behavior without internet.
- Fixture payloads contain no secret-like values.
- Negative paths are first-class, not optional.
- No-egress guard is mandatory before public-network candidate review.

### GateN-3: Public MarketData Adapter Skeleton

Allowed scope after separate implementation authorization:

- Narrow backend skeleton for source abstraction.
- Fake-server / fixture source only.
- Timeout / retry / rate-limit defaults.
- Fail-closed error mapping.
- Unit tests and fake-server tests.

Forbidden scope:

- Real provider.
- Private trading adapter.
- Permission probe.
- Credential lookup.
- LIVE profile behavior.
- Migration unless separately approved.

Acceptance criteria:

- Skeleton cannot reach private endpoints.
- Unknown source fails closed.
- No unbounded fetch or cache.
- Tests cover success, timeout, malformed payload, stale data, rate limit, and no-egress.

### GateN-4: MarketData Sandbox Fixture Smoke

Allowed scope after separate authorization:

- Targeted local smoke using local DB / fixture / fake server.
- No private exchange, no credential, no LIVE.
- Explicit no-data / data-present / unavailable branching.

Forbidden scope:

- Real exchange dependency.
- Private endpoint.
- Permission probe.
- Trading or account state change.
- Treating empty data as readiness success.

Acceptance criteria:

- Smoke reports `DATA_PRESENT`, `NO_DATA`, or `UNAVAILABLE` explicitly.
- No real exchange host is contacted.
- Logs contain no credential-like values or raw provider body.
- Result remains diagnostic only.

### GateN-5: Runtime UI Sandbox Source Display

Allowed scope after separate authorization:

- Frontend-only display of sandbox/public source status.
- Existing API only unless a backend API was separately approved.
- Loading / empty / error / stale / no-permission / disabled states.
- Explicit `No trading`, `No credential`, `LIVE disabled` labels.

Forbidden scope:

- New backend API unless separately authorized.
- New E2E unless separately authorized.
- Hiding risk state.
- Showing real-ready, live-ready, trading authorization, or permission verified.

Acceptance criteria:

- UI cannot be mistaken for LIVE / real provider readiness.
- Error state fails closed.
- Empty state explains data source and next safe action.
- Risk banners remain visible.

### GateN-FREEZE

Allowed scope:

- Freeze review after GateN implementation batches are completed and verified.
- Evidence matrix.
- Known residuals.
- Post-freeze rules.

Forbidden scope:

- Freezing incomplete implementation.
- Freezing public sandbox as production readiness.
- Freezing public marketdata as trading authorization.
- Adding new implementation during freeze.

Acceptance criteria:

- P0/P1/P2 blockers are zero or explicitly accepted with user approval.
- All validation commands are real and documented.
- Forbidden scope diff is empty.
- No-real boundary remains explicit.

## Proposed Task Order

1. `NQ-GATEN-1-PUBLIC-MARKETDATA-CONTRACT-PLAN-REVIEW`.
2. `NQ-GATEN-2-FAKE-SERVER-NO-EGRESS-TEST-PLAN`.
3. `NQ-GATEN-3-PUBLIC-MARKETDATA-ADAPTER-SKELETON`.
4. `NQ-GATEN-4-MARKETDATA-SANDBOX-FIXTURE-SMOKE`.
5. `NQ-GATEN-5-RUNTIME-UI-SANDBOX-SOURCE-DISPLAY`.
6. `NQ-GATEN-FREEZE`.

## P0 / P1 / P2 / P3 Risks

### P0

- None in this planning-only task.

Potential future P0:

- Any implementation that can place/cancel orders, transfer/withdraw funds, read private account data, or expose credentials.
- Any task that enables LIVE without explicit Gate authorization.

### P1

- None blocking this planning baseline.

Potential future P1:

- Treating public marketdata sandbox as trading authorization.
- Treating fake-server / no-egress tests as real provider proof.
- Calling real public internet by default without review.
- Mixing permission probe with public marketdata.

### P2

- Fake-server / no-egress contract is not implemented yet.
- Public marketdata source taxonomy is a plan, not code.
- Future public-network candidate still needs timeout / retry / rate-limit / payload / log policy.
- Existing Marketdata page has ingestion actions; any future UI slice must keep local/fake/public boundaries visible.

### P3

- Historical docs still contain old AI / LIVE route wording in append-only records; current README / ROADMAP / STATUS are the authority.
- Paper Trading productization and UI/UX professionalism remain valuable follow-ups but are not GateN entry blockers.

## Recommended First Implementation Task

Recommended first implementation task after plan review:

```text
NQ-GATEN-2-FAKE-SERVER-NO-EGRESS-TEST-PLAN
```

Reason:

- It keeps the next step test-first.
- It avoids real exchange, credentials, LIVE, and private endpoints.
- It creates acceptance evidence before any adapter skeleton.

Entry conditions:

- This GateN plan is accepted.
- `NQ-GATEN-1-PUBLIC-MARKETDATA-CONTRACT-PLAN-REVIEW` is accepted.
- User explicitly authorizes implementation or the next planning task.
- P0/P1/P2 blockers are not open.

## Final Decision

Decision: **PASS / PLAN ONLY / READY TO COMMIT**.

GateN baseline:

- GateN Public MarketData / Exchange Sandbox Planning.
- GateN implementation **NOT STARTED**.
- No real provider.
- No private trading.
- No credential.
- No LIVE.
- No AI runtime.
- No DH runtime.

Commit recommendation:

```text
docs(gaten): plan public marketdata sandbox
```
