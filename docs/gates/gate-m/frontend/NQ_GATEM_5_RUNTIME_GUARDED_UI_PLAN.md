# NQ GateM-5 Runtime Guarded UI Plan

> Task: `NQ-GATEM-5-RUNTIME-GUARDED-UI-PLAN`
> Type: `FRONTEND_PLANNING + RUNTIME_READINESS_UI + API_BOUNDARY_REVIEW + DOCUMENTATION`
> Status: `PLAN ONLY / NOT IMPLEMENTED`
> Date: `2026-06-30`

## 1. Current State

GateM authoritative definition is **Exchange / MarketData Runtime Readiness**. The current runtime state remains fail-closed:

- LIVE: `DISABLED`.
- AI: `NOT STARTED`.
- DH runtime: `NOT INTEGRATED`.
- Real exchange adapter / RealClient / real provider: `NOT IMPLEMENTED`.
- Real permission probe: not implemented; current no-real permission probe remains `SKIPPED`.
- Adapter readiness API and page exist.
- MarketData bars/readiness API and K-line / volume / Data Quality UI exist.
- Paper-to-Real backend boundary is fail-closed.

This plan only defines a frontend runtime readiness experience. It does not implement UI, backend APIs, migrations, jobs, probes, adapters, provider clients, LIVE routing, AI pages, or DH runtime pages.

## 2. Goal

Runtime Guarded UI must answer three questions without triggering side effects:

1. What can current NQ runtime safely do?
2. What can it not do?
3. Why is a capability blocked, disabled, skipped, pending backend support, or paper-only?

The UI must make Paper readiness, MarketData DB freshness, Adapter readiness, NoReal boundaries, permission probe status, and LIVE disabled state visible without implying real trading readiness.

## 3. Non-goals

- No frontend implementation in this task.
- No backend API implementation or contract change.
- No migration.
- No TradingWorkbench order-flow enhancement.
- No new order / cancel / withdraw / transfer UI.
- No new LIVE UI entry.
- No AI or DH runtime page.
- No real exchange connection, adapter call, provider call, credential read, credential scan, or permission probe execution.
- No collection trigger, ingestion trigger, or marketdata adapter subscription.
- No interpretation that `no-real`, `fake`, `stub`, `paper-only`, or `SKIPPED` equals real-ready.

## 4. Existing UI / API Capability

### 4.1 Adapter readiness

- Existing route: `/adapter-readiness`.
- Existing frontend entry: `AdapterReadinessPage`.
- Existing API: `GET /api/adapters/readiness`.
- Current API behavior: read-only static runtime readiness matrix. It reports venue x capability readiness for `NOOP`, `PAPER`, `SIM`, `OKX`, and `BINANCE`.
- Current safe interpretation:
  - `NOOP` / `PAPER` / `SIM`: `NO_REAL` and paper/sim-only.
  - `OKX` / `BINANCE`: not live-authorized, not real-ready.
  - All current rows are fail-closed; no row authorizes LIVE trading.

### 4.2 MarketData readiness

- Existing page: `MarketdataPage`.
- Existing APIs:
  - `GET /api/marketdata/bars`.
  - `GET /api/marketdata/readiness`.
- Current readiness is a DB-only no-migration MVP based on local bars / ingestion facts. It must not be displayed as live exchange source health.
- Supported display states include `FRESH`, `STALE`, `GAP`, `NO_DATA`, `UNKNOWN`, and related quality summaries.

### 4.3 Paper runtime readiness

- Existing pages: Paper Trading route shell and run pages.
- Existing read APIs include run list, run detail, run summary, portfolio, diagnostics, and auto-review style endpoints.
- Existing write APIs include create/start/stop/run-once style actions. Runtime Guarded UI must not call those write endpoints.
- Existing safety signal: Paper summary exposes SIM/Paper-only semantics such as LIVE disabled and no real exchange touched.

### 4.4 TradingWorkbench

- Existing page contains real trading-workbench actions and SIM/LIVE environment display.
- Runtime Guarded UI planning may place read-only guard banners or blockers near the page, but must not modify order workflows, add order UI, add LIVE entry points, or weaken existing backend guards.

### 4.5 Dashboard / Operations

- Dashboard currently summarizes Paper Trading safety and operations-style status.
- It can host a compact Runtime Guarded summary later, but should not become the only source of runtime readiness because the full matrix needs a dedicated page.

## 5. Page Placement Decision

Recommended placement:

1. Add a dedicated read-only `/runtime/readiness` route in the future implementation.
2. Implement it by extending or extracting the existing `AdapterReadinessPage` logic so the adapter matrix is reused instead of duplicated.
3. Keep `/adapter-readiness` as a compatibility route, redirect, or adapter-focused sub-entry. The exact compatibility shape can be decided in implementation, but it must not remove the current adapter readiness capability.
4. Add Dashboard summary only as a compact 5D card.
5. Add page-local read-only guard banners to MarketData, Paper Trading, and TradingWorkbench only after the dedicated runtime view is established.

Naming note: `docs/current/README.md` already uses GateM-5A / 5B / 5C for adapter readiness API, panel, and real-backend smoke history. The batches below are local to this Runtime Guarded UI plan and should use task names such as `NQ-GATEM-5-RUNTIME-UI-5A-*` to avoid collision with existing records.

## 6. Runtime Guarded UI Modules

| Module | Purpose | Required behavior |
| --- | --- | --- |
| Runtime summary | One-screen answer for current runtime posture | Must say Paper-only / LIVE disabled / NoReal where applicable; must not show live-ready green. |
| Adapter readiness | Venue x capability matrix | Consume `GET /api/adapters/readiness`; show `NO_REAL`, `FAKE`, `STUB`, `FUTURE_REAL_DISABLED`, `LIVE_NOT_AUTHORIZED`, `CREDENTIAL_UNCONFIGURED`, `PERMISSION_PROBE_DISABLED` as blocked or disabled. |
| MarketData readiness | Bars freshness, gap, and source health posture | Consume `GET /api/marketdata/readiness`; `UNKNOWN` or API failure must fail closed. |
| Paper runtime readiness | Whether Paper runtime is usable and bounded | Use read-only Paper APIs; show Paper ready separately from any real readiness. |
| LIVE disabled status | Global live-trading authorization posture | Current value must display as `LIVE_NOT_AUTHORIZED` / `DISABLED`; no LIVE CTA. |
| Permission probe status | Explain probe disabled/skipped/currently not verified | Use adapter readiness `PERMISSION_PROBE` row as current safe source; credential-scoped latest probe is pending integration for global UI. |
| Paper-to-Real boundary | Explain why Paper state cannot become real trading | Show fail-closed backend boundary and blockers; do not offer bypass. |
| Environment flags | Surface safe runtime flags and uncertainty | Missing aggregate runtime flags API must be `PENDING_BACKEND_SUPPORT`. |
| Runtime blockers | Human-readable blockers and next safe action | Every blocker needs source, reason, severity, and whether backend support is pending. |

## 7. Data Source Mapping

| UI need | Current data source | Current status | UI mapping |
| --- | --- | --- | --- |
| Adapter readiness matrix | `GET /api/adapters/readiness` | Available | Primary source for adapter, venue, capability, allowed, live authorization, reasons, and generated time. |
| Adapter permission probe readiness | `GET /api/adapters/readiness` row for `PERMISSION_PROBE` | Available as no-real/disabled signal | Show `PERMISSION_PROBE_DISABLED` / `SKIPPED`; never show verified. |
| Credential-specific latest probe | `GET /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe/latest` | Exists but credential-scoped | Do not scan globally. Use only from credential context or after a future safe aggregate API. |
| Permission probe execution | `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe` | Existing action endpoint | Forbidden for Runtime Guarded UI. Must not be called by readiness page. |
| MarketData readiness | `GET /api/marketdata/readiness` | Available | Show `FRESH`, `STALE`, `GAP`, `NO_DATA`, `UNKNOWN`; source health unknown or API failure is not ready. |
| MarketData bars fallback | `GET /api/marketdata/bars` | Available | Can support chart/data context; must not replace readiness API for source health claims. |
| MarketData ingestion trigger | `POST /api/marketdata/ingestion-jobs/*/run-once` style endpoints | Existing action endpoints | Forbidden for Runtime Guarded UI. |
| Paper run summary | `GET /api/paper-trading/runs/{id}/summary` | Available | Show SIM/Paper-only safety, `liveEnabled=false`, `realExchangeTouched=false` where provided. |
| Paper run list/detail | `GET /api/paper-trading/runs`, `GET /api/paper-trading/runs/{id}` | Available | Read-only run context and latest/focused run state. |
| Paper portfolio/diagnostics/review | Existing read-only Paper APIs | Available | Optional supporting cards; no write actions. |
| Paper create/start/stop/risk run-once | Existing write APIs | Available | Forbidden for Runtime Guarded UI readiness refresh. |
| App health | `/actuator/health` | Available outside `/api/**` | Optional app-health preflight only; must not equal runtime readiness. |
| Central runtime flags | No confirmed aggregate API | Missing | Display as `PENDING_BACKEND_SUPPORT`; do not infer LIVE readiness from frontend env alone. |
| Paper-to-Real boundary aggregate | No confirmed aggregate API | Missing | Use current facts and existing signals; mark unified backend status as `PENDING_BACKEND_SUPPORT` if needed. |

## 8. State Model and Display Semantics

### 8.1 Adapter / trading readiness states

- `READY_FOR_PAPER_ONLY`: informational / paper-safe. Never live-ready.
- `NO_REAL`: blocked / no-real.
- `FAKE`: blocked / fake implementation.
- `STUB`: blocked / stub implementation.
- `FUTURE_REAL_DISABLED`: blocked / future-real disabled.
- `LIVE_NOT_AUTHORIZED`: blocked / live authorization absent.
- `PERMISSION_PROBE_DISABLED`: disabled / skipped, not verified.
- `CREDENTIAL_UNCONFIGURED`: blocked / credential missing or intentionally unconfigured.
- `BLOCKED`: hard blocker.
- `DISABLED`: intentionally off.
- `PENDING_BACKEND_SUPPORT`: not available from current APIs.

### 8.2 MarketData readiness states

- `FRESH`: local DB freshness is acceptable for the selected dataset. It does not imply live exchange readiness.
- `STALE`: degraded.
- `GAP`: degraded or blocked depending on downstream action.
- `NO_DATA`: blocked for data-dependent workflows.
- `UNKNOWN`: blocked / not ready.

### 8.3 Color and copy rules

- Do not use green for live readiness unless a future Gate explicitly implements and authorizes real LIVE readiness.
- Use informational blue or neutral styling for `READY_FOR_PAPER_ONLY`.
- Use warning / error / disabled styling for no-real, fake, stub, skipped, disabled, blocked, or unknown states.
- Paper-ready and Live-ready must be visually separate.
- `permission probe skipped` must not be rendered as verified.
- `sourceHealthStatus=UNKNOWN` must not be rendered as ready.
- Any missing API must be labelled `PENDING_BACKEND_SUPPORT`, not silently treated as ready.

## 9. Minimal Implementation Batches

### Runtime UI 5A: Runtime readiness overview

Recommended next implementation task. Add or extend a read-only `RuntimeReadinessPage` at `/runtime/readiness`, reusing existing adapter readiness query and table logic.

Allowed future implementation scope:

- Frontend route / navigation.
- Frontend page/component/type/query code.
- Backend-free Playwright route-stub smoke.
- Optional docs current testing/worklog update.

Forbidden:

- Backend changes.
- API changes.
- Migration.
- TradingWorkbench action changes.
- Credential reads.
- LIVE enablement.

Success:

- Page displays adapter readiness fail-closed.
- Page shows LIVE disabled and no-real blockers.
- Page does not render any live-ready state.
- API failure renders blocked/unavailable.

### Runtime UI 5B: MarketData readiness card

Add a MarketData readiness card to the runtime overview using `GET /api/marketdata/readiness`.

Success:

- Shows freshness / gap / no-data / unknown separately.
- Shows DB-only / `NO_MIGRATION_MVP` limitation.
- Does not trigger ingestion or adapter calls.
- Does not display unknown source health as ready.

### Runtime UI 5C: Paper-to-Real guard banners and blockers

Add read-only guard banners or blocker panels to Paper Trading, MarketData, and TradingWorkbench contexts.

Success:

- Paper page says Paper-ready is not real-ready.
- TradingWorkbench banner says LIVE remains disabled / not authorized when current runtime reports so.
- No order/cancel UX is added.
- No existing write API is called for readiness.
- Unified Paper-to-Real backend status is marked `PENDING_BACKEND_SUPPORT` until a safe aggregate API exists.

### Runtime UI 5D: Dashboard summary card

Add a compact dashboard summary card linking to `/runtime/readiness`.

Success:

- Summarizes current runtime as paper-only / no-real / LIVE disabled.
- Links to full details.
- Does not become the only source of readiness detail.
- Does not hide blockers to make the dashboard look healthy.

## 10. Testing Strategy

Testing should be narrow and behavior-focused:

- Use backend-free Playwright route stubs for 5A-5D first.
- Use one real local backend smoke later for core read-only APIs after the UI is stable.
- Do not build a full E2E matrix for every state combination.
- Cover representative states only:
  - Adapter all fail-closed / no-real.
  - MarketData `FRESH`.
  - MarketData `GAP` or `NO_DATA`.
  - MarketData `UNKNOWN` / API unavailable.
  - Paper summary with `liveEnabled=false` and `realExchangeTouched=false`.
  - Permission probe `SKIPPED` / disabled.
- Assert copy boundaries:
  - `LIVE disabled`.
  - `NoReal` / `not implemented`.
  - `Paper-only`.
  - `permission probe skipped`, not verified.
  - `source health unknown`, not ready.

No Maven, npm build, Playwright, or backend smoke is required for this planning-only documentation task.

## 11. Security Boundary

Runtime Guarded UI must remain read-only:

- No real trade, order, cancel, withdraw, transfer, or account mutation.
- No collection, ingestion, adapter subscription, or adapter capability probe.
- No credential read, credential scan, credential display, token display, secret display, cookie display, or raw provider payload display.
- No permission probe execution from runtime readiness UI.
- No LIVE enablement.
- No AI or DH runtime integration.
- No interpretation that `disabled`, `no-real`, `fake`, `stub`, `paper-only`, `unknown`, or `skipped` means ready.

## 12. P0 / P1 / P2 / P3 Risks

### P0

- None introduced by this planning-only docs task.
- Future implementation P0 would be any UI or API path that triggers real trading, reads credentials, enables LIVE, or represents fake/no-real as real-ready.

### P1

- Paper readiness, DB freshness, or adapter matrix rows could be misread as real trading readiness.
- Runtime UI could accidentally call write endpoints such as paper start/stop, marketdata run-once, permission probe `POST`, order, or cancel.
- Permission probe `SKIPPED` could be displayed as verified.
- Credential-scoped probe APIs could be misused as a global readiness scan.

### P2

- No central aggregate runtime flags API exists, so some environment flags and Paper-to-Real boundary cards need `PENDING_BACKEND_SUPPORT`.
- MarketData `NO_MIGRATION_MVP` source health can be mistaken for real exchange health unless copy is explicit.
- Existing Dashboard / TradingWorkbench wording may remain less explicit until runtime banners are implemented.
- Overbuilding the E2E status matrix would slow delivery without adding proportional risk coverage.

### P3

- `/adapter-readiness` and `/runtime/readiness` naming needs a compatibility decision.
- Color semantics need a compact legend so paper-only blue / blocked warning / disabled gray are obvious.
- Dashboard summary must stay dense enough for operations use without hiding blockers.

## 13. Recommended Next Implementation Task

Task name:

`NQ-GATEM-5-RUNTIME-UI-5A-RUNTIME-READINESS-OVERVIEW`

Task type:

`FRONTEND_IMPLEMENTATION + BACKEND_FREE_E2E + DOCS_CURRENT_LIGHT_RECORD`

Scope:

- Implement read-only `/runtime/readiness`.
- Reuse existing adapter readiness API and frontend query.
- Keep `/adapter-readiness` compatible.
- Add explicit LIVE disabled / NoReal / permission probe skipped summary.
- Add backend-free Playwright smoke for success and API-unavailable states.
- Do not modify backend, migration, TradingWorkbench order actions, credential APIs, real adapters, LIVE, AI, or DH runtime.

Acceptance:

- Runtime overview renders fail-closed from mocked adapter readiness.
- No green live-ready state appears.
- `READY_FOR_PAPER_ONLY` is visually distinct from any future live readiness.
- `PERMISSION_PROBE_DISABLED` / skipped is not verified.
- API unavailable state remains blocked.

## 14. Final Decision

`PLAN ONLY / NOT IMPLEMENTED / READY FOR REVIEW`

This plan is safe to use as the implementation baseline for Runtime UI 5A, provided implementation remains frontend-only, read-only, and backend-free for the first batch.
