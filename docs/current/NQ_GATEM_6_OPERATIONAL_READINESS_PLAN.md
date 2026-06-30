# NQ GateM-6 Operational Readiness Plan

Task: NQ-GATEM-6-OPERATIONAL-READINESS-PLAN

Status: PLAN ONLY / NOT IMPLEMENTED

Date: 2026-06-30

GateM authoritative definition: Exchange / MarketData Runtime Readiness.

GateM-5 Runtime Guarded UI is IMPLEMENTED / SMOKE VERIFIED / CLOSED. GateM-6 is the next planning slice for operational readiness. This document does not implement any runtime capability, API, page, migration, workflow, startup check, health aggregation, runtime config guard, deploy automation, LIVE trading, AI runtime, DH runtime, RealClient, real provider, or real exchange integration.

## Current Facts

- LIVE is DISABLED.
- AI is NOT STARTED.
- DH runtime is NOT INTEGRATED.
- real exchange adapter / RealClient / real provider is NOT IMPLEMENTED.
- Existing OKX / Binance adapter code is not authorized as a real execution provider and must not be described as real-ready.
- Paper-ready, DB-fresh, actuator health UP, and permission probe SKIPPED do not equal real-ready.

## Goal

GateM-6 plans how to make the runtime operational state easier to inspect without changing the runtime itself. The implementation line should clarify:

- runtime health and profile status;
- safe config boundary and disabled capability summary;
- startup checks and blockers;
- log / diagnostic safety boundary;
- adapter readiness and MarketData readiness rollup;
- Dashboard / Runtime UI operational summary;
- deployment runbook and local operational checklist.

The plan is intentionally read-only and fail-closed. Missing data sources must be labeled `PENDING_BACKEND_SUPPORT`, not inferred as ready.

## Non-Goals

GateM-6 does not:

- enable LIVE;
- connect a real exchange;
- implement a real permission probe;
- read or print credential material;
- print raw env or full config dumps;
- implement AI or DH runtime;
- implement RealClient or real provider;
- add production deploy automation;
- add trading, order, cancel, withdraw, transfer, or WebSocket capability;
- treat disabled, no-real, fake, stub, paper-only, or SKIPPED as ready.

## Current Operational Baseline

### Actuator Health

Current backend configuration exposes actuator health / info and has liveness / readiness probe support in app configuration. `/actuator/health` is useful for process and dependency health, but it is not a trading readiness signal and must not be shown as LIVE-ready.

Gap: there is no consolidated operational readiness summary that safely combines actuator health, profile, env safety, adapter readiness, MarketData readiness, and runtime blockers.

### Runtime Readiness UI

`/runtime/readiness` exists and is read-only. It already shows LIVE disabled, paper-only readiness, NoReal / Fake / Stub / FutureReal not live-ready, permission probe skipped or disabled, and links to MarketData. It consumes adapter readiness and labels missing runtime flags / paper boundary aggregation as `PENDING_BACKEND_SUPPORT`.

Gap: the page does not yet provide a full operational health / profile / startup / diagnostics summary.

### Adapter Readiness

`GET /api/adapters/readiness` exists and is read-only. It reports venue / capability readiness fail-closed. Current OKX / Binance capabilities are not ready for real execution; Noop / Paper / Sim paths are no-real or simulated only. The endpoint must remain descriptive and must not trigger adapter calls, permission probes, external network, or credential reads.

### MarketData Readiness

`GET /api/marketdata/readiness` exists and is read-only. It derives source health / freshness / gap state from existing local DB facts and does not call exchange adapters or external exchanges. K-line and volume inspection remains under `/marketdata`.

Gap: MarketData freshness can support paper/runtime diagnosis, but it must not be rendered as live-ready.

### Paper / Trading Guard Banner

Paper Trading and Trading Workbench have read-only guard banners. They distinguish paper simulation from real execution and state that LIVE is disabled, real provider is not implemented, and permission probe SKIPPED / disabled is not verified.

Gap: banners are UI communication, not a replacement for backend fail-closed guards.

### Dashboard Runtime Summary

Dashboard has a Runtime Readiness summary card with links to Runtime Readiness and MarketData. It is read-only and must remain a summary, not a trading control.

Gap: the card does not yet roll up actuator health, startup blockers, safe config status, or diagnostic state.

### Logs, Config, Profile

The backend has env-safety startup guard code and profile-specific no-outbound / LIVE / AI / DH / real-provider flags. Observability registers trace ID handling. These are useful foundations, but a safe user-facing runtime config / profile summary is not yet implemented.

Current boundary: raw env, raw config, credential values, secret values, token values, passphrase values, request signatures, cookies, and full diagnostic dumps must never be printed to the UI or docs.

### Runbook

`docs/current/RUNBOOK.md` already covers local startup and health checks. GateM-6 should later add an operational checklist or runbook refinement, but this planning task does not implement it.

## Readiness Model

Operational readiness should separate process health from capability readiness:

- Process health: actuator health, app reachability, DB availability where reported.
- Runtime boundary: active profile category, LIVE disabled, no-outbound guard, AI / DH / real-provider disabled.
- Capability readiness: adapter readiness, MarketData readiness, paper-only status, paper-to-real guard.
- Diagnostics: safe trace/log summary and startup blockers without sensitive payloads.
- User-facing conclusion: paper/runtime diagnosis only, never real-ready unless a future Gate explicitly implements and verifies real provider + authorization.

Recommended UI status vocabulary:

- `HEALTH_UP`, `HEALTH_DOWN`, `HEALTH_UNKNOWN`
- `PROFILE_SAFE`, `PROFILE_REVIEW_REQUIRED`, `PROFILE_UNKNOWN`
- `CONFIG_SAFE`, `CONFIG_BLOCKED`, `CONFIG_UNKNOWN`, `PENDING_BACKEND_SUPPORT`
- `CAPABILITY_DISABLED`, `NO_REAL`, `FAKE`, `STUB`, `FUTURE_REAL_DISABLED`
- `LIVE_DISABLED`, `LIVE_NOT_AUTHORIZED`
- `PERMISSION_PROBE_DISABLED`, `SKIPPED_NOT_VERIFIED`
- `FRESH`, `STALE`, `GAP`, `NO_DATA`, `UNKNOWN`
- `BLOCKED`, `DISABLED`, `PENDING_BACKEND_SUPPORT`

UI colors and wording must not use green or success language for LIVE readiness unless real LIVE readiness is implemented and explicitly authorized in a future Gate.

## Data Source Mapping

| Source | Current availability | Planned use | Boundary |
| --- | --- | --- | --- |
| `/actuator/health` | Existing backend actuator health | Process / dependency health indicator | Health UP is not real-ready and not LIVE authorization. |
| `/api/adapters/readiness` | Existing read-only API | Adapter / capability readiness card and blockers | Must not trigger adapter call, permission probe, external network, or credential read. |
| `/api/marketdata/readiness` | Existing read-only API | Source health, freshness, gap, no-data summary | DB-fresh is not live-ready. |
| Existing `/runtime/readiness` state | Existing frontend state | Runtime boundary copy and summary composition | Missing backend support remains `PENDING_BACKEND_SUPPORT`. |
| Existing app config / env flags | Startup guard exists; no safe runtime summary API | Future redacted profile/config summary | No raw env, no full config dump, no secret material. Missing UI source is `PENDING_BACKEND_SUPPORT`. |
| Startup checks | Startup guard code exists; no user-facing startup summary | Future blocker summary | Must be redacted and fail-closed. |
| Logs / trace IDs | Trace ID support exists; no safe diagnostic summary API | Future safe diagnostics summary | No raw request/response, no credential-like payload, no full log dump. |
| Deployment runbook | Local runbook exists | Future operational checklist | Docs-only until a separate implementation task permits more. |

## Implementation Batches

### 6A: Runtime Health / Config / Profile Overview

Goal: extend the existing Runtime Readiness overview with an operational section for process health, safe profile category, and config boundary.

Allowed future scope:

- frontend read-only UI under existing Runtime Readiness page;
- existing actuator health only if safely reachable in the frontend runtime;
- existing adapter / MarketData readiness APIs;
- docs sync and backend-free smoke.

Forbidden future scope:

- new backend API unless separately authorized;
- raw env or full config dump;
- credential, secret, token, passphrase, private key, cookie, signature display;
- LIVE enablement or real exchange calls;
- permission probe POST or ingestion run-once.

Success:

- unknown or missing health/config/profile data is shown as `PENDING_BACKEND_SUPPORT` or unknown, not ready;
- LIVE remains disabled and visibly distinct from paper readiness.

### 6B: Startup Check / Disabled Capability Summary

Goal: plan and, if later authorized, expose a safe summary of startup blockers and disabled capabilities.

Allowed future scope:

- redacted capability summary for LIVE / AI / DH runtime / real provider / RealClient / external exchange access;
- no-real and no-outbound blocker wording;
- backend review of existing startup guard behavior.

Forbidden future scope:

- startup mutation or auto-remediation;
- printing raw env/config values;
- treating startup success as real trading readiness;
- adding real permission probe or real provider.

Success:

- users can see why runtime is paper-only / no-real without seeing secrets or operational internals.

### 6C: Log Boundary / Safe Diagnostic Summary

Goal: define a safe diagnostic summary surface using trace IDs and categorized blockers.

Allowed future scope:

- redacted diagnostic categories;
- trace ID visibility;
- last-known safe blocker labels.

Forbidden future scope:

- full log viewer;
- raw request / response body;
- raw adapter payload;
- credential-like strings or headers;
- production log export or external collector integration unless separately authorized.

Success:

- diagnostics explain state without exposing sensitive material or live exchange details.

### 6D: Operations Dashboard Or Runtime Status Card Refinement

Goal: refine Dashboard and Runtime summary cards to show operational status without adding controls.

Allowed future scope:

- frontend-only summary card refinements;
- read-only links to Runtime Readiness and MarketData;
- fail-closed state rendering.

Forbidden future scope:

- new LIVE UI entry;
- order, cancel, transfer, withdraw, ingestion, permission-probe actions;
- TradingWorkbench order logic changes.

Success:

- Dashboard accurately summarizes operational blockers and never promotes paper/DB health to real-ready.

### 6E: Deployment Runbook / Local Operational Checklist

Goal: update or add runbook material for local operational readiness checks.

Allowed future scope:

- docs-only local checklist;
- commands for build, smoke, actuator health, readiness UI, and forbidden-boundary verification;
- rollback and troubleshooting notes.

Forbidden future scope:

- production deploy automation;
- workflow changes;
- cloud resource changes;
- credential setup instructions that expose or copy secret values.

Success:

- a developer can run local readiness checks without touching real exchanges or credentials.

### 6F: GateM Operational Final Smoke

Goal: add one final smoke that covers the GateM-6 operational UI chain after 6A-6E.

Allowed future scope:

- one backend-free primary smoke by default;
- optional real-backend smoke only if a later task explicitly authorizes it and preserves no-outbound / no-secret boundaries.

Forbidden future scope:

- broad status matrix E2E expansion;
- real external exchange calls;
- write endpoints;
- permission probe POST;
- ingestion run-once;
- LIVE enablement.

Success:

- build and one main smoke pass; operational readiness UI communicates disabled / no-real / paper-only boundaries correctly.

## Security Boundary

GateM-6 implementation must preserve these invariants:

- do not output credential material;
- do not output raw env;
- do not output secret, token, passphrase, private key, cookie, signature, or mnemonic material;
- do not print full config dumps;
- do not call external exchanges;
- do not call write endpoints;
- do not call permission probe POST;
- do not trigger ingestion run-once;
- do not enable LIVE;
- do not connect AI or DH runtime;
- do not implement RealClient or real provider;
- do not describe disabled / no-real / fake / stub / paper-only / SKIPPED as ready.

## Testing Strategy

Implementation batches should use narrow validation:

- frontend build for frontend changes;
- one main backend-free Playwright smoke per user-facing UI slice;
- no full state combination matrix;
- real-backend smoke only when a later task explicitly authorizes it and the backend can be kept local, no-outbound, and credential-safe;
- docs-only batches use `git diff --check`, `git diff --stat`, forbidden-scope diffs, and focused `rg` checks.

Expected 6F final validation:

- `cd frontend; npm run build`
- `cd frontend; npm run test:e2e -- tests/e2e/<gatem-6-operational-final-smoke>.spec.ts --project=chromium`

Maven backend tests are not required for frontend-only or docs-only batches unless backend code is explicitly changed in a future task.

## Risks

### P0

- Any UI or doc states LIVE is enabled, real provider is implemented, or real exchange is ready when current runtime remains disabled / no-real.
- Any implementation reads, prints, stores, or exposes credential material.
- Any implementation calls real exchange, order, cancel, withdraw, transfer, permission probe POST, ingestion run-once, or external network without explicit authorization.

### P1

- Actuator health UP is displayed as runtime-ready or live-ready.
- MarketData DB freshness is displayed as real market connectivity or live-readiness.
- Permission probe SKIPPED / disabled is displayed as verified.
- Startup or profile summary prints raw env/config values.

### P2

- Missing backend support is hidden instead of marked `PENDING_BACKEND_SUPPORT`.
- Dashboard and Runtime pages use inconsistent labels for the same blocker.
- Operational readiness splits across pages without a clear primary source.

### P3

- Runbook and UI terminology drift.
- Existing non-blocking frontend warnings remain visible in smoke output.
- Operational status naming becomes too verbose for compact Dashboard cards.

## Recommended Next Implementation Task

Recommended next task:

`NQ-GATEM-6A-RUNTIME-HEALTH-CONFIG-PROFILE-OVERVIEW`

Suggested scope:

- frontend-only first pass under `/runtime/readiness`;
- use existing read-only data sources only;
- show actuator health only if safely reachable in current frontend runtime, otherwise mark as `PENDING_BACKEND_SUPPORT`;
- keep config/profile summary redacted and fail-closed;
- add one backend-free smoke;
- update docs/current/TESTING.md and docs/current/WORKLOG.md only if implementation and smoke pass.

Suggested explicit prohibitions:

- no backend API;
- no migration;
- no workflow;
- no raw env/config dump;
- no credential access or display;
- no permission probe POST;
- no ingestion run-once;
- no order/cancel/withdraw/transfer;
- no LIVE/AI/DH/RealClient/real provider.

## Final Decision

GateM-6 Operational Readiness is planned only. Implementation remains NOT STARTED until a separate GateM-6A task authorizes a narrowly scoped read-only implementation.
