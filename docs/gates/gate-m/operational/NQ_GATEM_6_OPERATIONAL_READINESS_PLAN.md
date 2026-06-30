# NQ GateM-6 Operational Readiness Plan

Task: NQ-GATEM-6-OPERATIONAL-READINESS-PLAN

Status: IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED

Date: 2026-06-30

GateM authoritative definition: Exchange / MarketData Runtime Readiness.

GateM-5 Runtime Guarded UI is IMPLEMENTED / SMOKE VERIFIED / CLOSED. This document started as the GateM-6 operational readiness planning baseline; GateM-6 is now IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED. The original plan baseline did not implement any runtime capability, API, page, migration, workflow, startup check, health aggregation, runtime config guard, deploy automation, LIVE trading, AI runtime, DH runtime, RealClient, real provider, or real exchange integration.

Implementation update: GateM-6A has implemented a frontend read-only runtime health/config/profile overview. GateM-6B has implemented a backend read-only disabled capability summary MVP at `GET /api/runtime/operational-readiness`. GateM-6C has integrated `/runtime/readiness` with that backend safe summary. GateM-6D has verified the API/UI loop with a real local backend smoke. GateM-6E has added the local operational readiness runbook `NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md`. GateM-6F has verified the local runbook / real backend API / Runtime UI loop with a final targeted smoke. GateM-6 is now closed as **IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED** and the next step is **NQ-GATEM-FREEZE-READINESS-REVIEW**. These updates still do not enable LIVE, AI runtime, DH runtime, RealClient, real provider, real exchange integration, real permission probe, external exchange calls, production deployment, or startup mutation.

## Current Facts

- LIVE is DISABLED.
- AI is NOT STARTED.
- DH runtime is NOT INTEGRATED.
- real exchange adapter / RealClient / real provider is NOT IMPLEMENTED.
- Existing OKX / Binance adapter code is not authorized as a real execution provider and must not be described as real-ready.
- Paper-ready, DB-fresh, actuator health UP, and permission probe SKIPPED do not equal real-ready.

## Goal

GateM-6 makes the runtime operational state easier to inspect without changing the runtime itself. The completed implementation line clarifies:

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

## Original Operational Baseline

This section records the baseline observed when GateM-6 was planned. It is retained as historical planning context; current GateM-6 closeout status is recorded above as **IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED**.

### Actuator Health

Current backend configuration exposes actuator health / info and has liveness / readiness probe support in app configuration. `/actuator/health` is useful for process and dependency health, but it is not a trading readiness signal and must not be shown as LIVE-ready.

Original gap: there was no consolidated operational readiness summary that safely combined actuator health, profile, env safety, adapter readiness, MarketData readiness, and runtime blockers.

### Runtime Readiness UI

`/runtime/readiness` exists and is read-only. It already shows LIVE disabled, paper-only readiness, NoReal / Fake / Stub / FutureReal not live-ready, permission probe skipped or disabled, and links to MarketData. It consumes adapter readiness and labels missing runtime flags / paper boundary aggregation as `PENDING_BACKEND_SUPPORT`.

Original gap: the page did not yet provide the GateM-6 scoped operational health / profile / startup / diagnostics summary.

### Adapter Readiness

`GET /api/adapters/readiness` exists and is read-only. It reports venue / capability readiness fail-closed. Current OKX / Binance capabilities are not ready for real execution; Noop / Paper / Sim paths are no-real or simulated only. The endpoint must remain descriptive and must not trigger adapter calls, permission probes, external network, or credential reads.

### MarketData Readiness

`GET /api/marketdata/readiness` exists and is read-only. It derives source health / freshness / gap state from existing local DB facts and does not call exchange adapters or external exchanges. K-line and volume inspection remains under `/marketdata`.

Original boundary: MarketData freshness can support paper/runtime diagnosis, but it must not be rendered as live-ready.

### Paper / Trading Guard Banner

Paper Trading and Trading Workbench have read-only guard banners. They distinguish paper simulation from real execution and state that LIVE is disabled, real provider is not implemented, and permission probe SKIPPED / disabled is not verified.

Original boundary: banners are UI communication, not a replacement for backend fail-closed guards.

### Dashboard Runtime Summary

Dashboard has a Runtime Readiness summary card with links to Runtime Readiness and MarketData. It is read-only and must remain a summary, not a trading control.

Original gap: the card did not roll up actuator health, startup blockers, safe config status, or diagnostic state.

### Logs, Config, Profile

The backend has env-safety startup guard code and profile-specific no-outbound / LIVE / AI / DH / real-provider flags. Observability registers trace ID handling. At the original planning baseline, a safe user-facing runtime config / profile summary was not yet implemented; GateM-6B/6C now expose the scoped operational readiness safe summary without raw env, full config dumps, or credential material.

Current boundary: raw env, raw config, credential values, secret values, token values, passphrase values, request signatures, cookies, and full diagnostic dumps must never be printed to the UI or docs.

### Runbook

`docs/current/RUNBOOK.md` already covers general local startup and health checks. GateM-6E has added the scoped local operational readiness runbook `NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md` for startup, health, authenticated operational readiness API checks, `/runtime/readiness` inspection, forbidden-action review, shutdown, troubleshooting, and completion criteria. It is local validation material only, not a production deploy runbook or LIVE authorization.

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
| Deployment runbook | `NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md` exists for local validation | Local operational checklist | Docs-only, local-only; not production deploy and not LIVE authorization. |

## Implementation Batches

### 6A: Runtime Health / Config / Profile Overview

Goal: extend the existing Runtime Readiness overview with an operational section for process health, safe profile category, and config boundary.

Implementation status: **COMPLETED**. The frontend Runtime Operational Readiness Overview was implemented as a read-only overview and kept unknown / missing backend support fail-closed.

Implemented scope:

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

Goal: expose a safe summary of startup blockers and disabled capabilities.

Implementation status: **PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT** for the backend MVP. The implemented endpoint is `GET /api/runtime/operational-readiness`; it returns explicit DTO fields for `liveStatus`, `aiStatus`, `dhRuntimeStatus`, `realProviderStatus`, `credentialExposureStatus`, `externalExchangeCallStatus`, `permissionProbeStatus`, `startupBoundaryStatus`, `profileBoundaryStatus`, `configDiagnosticsStatus`, and `logDiagnosticsStatus`. Every status item carries `status / ready / reasonCode / reason`, and current baseline keeps every item `ready=false`.

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
- implemented MVP does not depend on adapter, permission probe, external exchange, DB, file, HTTP client, or repository collaborators.

### 6C: Operational Readiness Frontend Integration

Goal: connect `/runtime/readiness` to the GateM-6B safe summary from `GET /api/runtime/operational-readiness`.

Implementation status: **PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**. The Runtime Readiness page now prefers the backend safe summary in the `Operational Readiness` section and fails closed to `UNAVAILABLE / PENDING_BACKEND_SUPPORT` if the API fails or the payload is incomplete.

Implemented scope:

- frontend `OperationalReadinessResponse` / `OperationalReadinessStatusResponse` types;
- read-only API client for `GET /api/runtime/operational-readiness`;
- TanStack Query key and `/runtime/readiness` rendering;
- backend-free smoke covering success and unavailable fail-closed behavior.

Forbidden scope preserved:

- no backend code change;
- no migration;
- no write endpoint;
- no permission probe POST;
- no ingestion run-once;
- no order / cancel / transfer / withdraw;
- no credential material display;
- no LIVE, AI, DH runtime, RealClient, or real provider.

Success:

- `/runtime/readiness` displays the backend safe summary and keeps every current operational status blocked / fail-closed.

### 6D: Operational Readiness Real Backend Smoke

Goal: verify GateM-6B backend summary and GateM-6C frontend integration against a real local backend.

Implementation status: **PASS / REAL BACKEND SMOKE VERIFIED / SELF-REVIEWED / READY TO COMMIT**. The targeted Playwright smoke starts from a real local backend, does not mock `/api/runtime/operational-readiness`, validates authenticated API HTTP `200`, opens `/runtime/readiness`, waits for the page's real GET, and confirms the UI remains fail-closed.

Implemented scope:

- `frontend/tests/e2e/runtime-operational-readiness-real-backend-smoke.spec.ts`;
- real local backend profile `local`;
- `/actuator/health = UP` before smoke;
- post-smoke backend stop and health unreachable check.

Forbidden scope preserved:

- no frontend production code change;
- no backend code change;
- no migration;
- no permission probe POST;
- no ingestion run-once;
- no order / cancel / transfer / withdraw;
- no external exchange browser request;
- no credential output;
- no LIVE, AI, DH runtime, RealClient, or real provider.

Success:

- local backend API/UI loop is verified without promoting actuator health, Paper-only, `SKIPPED`, or NoReal signals to real-ready.

### 6E: Deployment Runbook / Local Operational Checklist

Goal: update or add runbook material for local operational readiness checks.

Implementation status: **PASS / DOCS ONLY / READY TO COMMIT**. The runbook `NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md` documents local-only startup, health, authenticated operational readiness API check, `/runtime/readiness` inspection, forbidden actions, shutdown, troubleshooting, and completion criteria.

Implemented scope:

- docs-only local checklist;
- backend startup command for local profile;
- `/actuator/health` check and process-health boundary;
- authenticated `GET /api/runtime/operational-readiness` check;
- `/runtime/readiness` frontend smoke path;
- forbidden actions checklist;
- shutdown and health DOWN / unreachable confirmation;
- local troubleshooting notes.

Forbidden scope preserved:

- production deploy automation;
- workflow changes;
- cloud resource changes;
- credential setup instructions that expose or copy secret values.

Success:

- a developer can run local readiness checks without touching real exchanges or credentials.

### 6F: GateM Operational Final Smoke

Goal: add one final smoke that covers the GateM-6 operational UI chain after 6A-6E.

Implementation status: **PASS / FINAL SMOKE VERIFIED / SELF-REVIEWED / READY TO COMMIT**. The targeted final smoke validates the local runbook path against a real local backend: `/actuator/health = UP`, authenticated `GET /api/runtime/operational-readiness = 200`, `/runtime/readiness` displays the backend safe summary, no forbidden write endpoint is invoked, and backend shutdown makes health unavailable.

Implemented scope:

- `frontend/tests/e2e/runtime-operational-readiness-final-smoke.spec.ts`;
- real local backend profile `local`;
- authenticated operational readiness API preflight;
- Runtime UI final smoke for `LIVE=DISABLED`, `AI=NOT_STARTED`, `DH runtime=NOT_INTEGRATED`, `real provider=NOT_IMPLEMENTED`, `credential exposure=NOT_EXPOSED`, and `permission probe=SKIPPED`;
- no `live-ready`, `verified`, `LIVE authorized`, or `LIVE 已授权` positive UI signal;
- no permission-probe endpoint, ingestion run-once, order, cancel, transfer, withdraw, or external exchange browser request;
- post-smoke backend stop and health unreachable check.

Implementation-period fix:

- the final smoke exposed an old Adapter readiness matrix table header `LIVE authorized`; this was minimally changed to `LIVE auth count` so the Runtime UI does not display the prohibited positive authorization phrase. The `liveAuthorized` data field and readiness calculations were not changed.

Forbidden future scope:

- broad status matrix E2E expansion;
- real external exchange calls;
- write endpoints;
- permission probe POST;
- ingestion run-once;
- LIVE enablement.

Success:

- build and one final real-backend smoke pass; operational readiness UI communicates disabled / no-real / paper-only boundaries correctly.

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
- `cd frontend; npm run test:e2e -- tests/e2e/runtime-operational-readiness-final-smoke.spec.ts --project=chromium`

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

`NQ-GATEM-FREEZE-READINESS-REVIEW`

Suggested scope:

- review whether GateM runtime readiness is ready for freeze based on already completed GateM-5 and GateM-6 evidence;
- no new API, migration, page, E2E, status matrix, LIVE, AI, DH runtime, RealClient, real provider, or real exchange integration;
- preserve fail-closed runtime wording and no-real boundaries.

Suggested explicit prohibitions:

- no full log API;
- no migration;
- no workflow;
- no raw env/config dump;
- no credential access or display;
- no permission probe POST;
- no ingestion run-once;
- no order/cancel/withdraw/transfer;
- no LIVE/AI/DH/RealClient/real provider.

## Final Decision

GateM-6 Operational Readiness is **IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED**. GateM-6A Runtime Operational Readiness Overview, GateM-6B Operational Readiness Summary API, GateM-6C Frontend Integration, GateM-6D Real Backend Smoke, GateM-6E Local Operational Runbook, and GateM-6F Final Smoke are complete. The next step is **NQ-GATEM-FREEZE-READINESS-REVIEW**. This is not production readiness and not LIVE authorization. LIVE remains DISABLED; AI remains NOT STARTED; DH runtime remains NOT INTEGRATED; RealClient / real provider / real exchange adapter / real permission probe remain NOT IMPLEMENTED.
