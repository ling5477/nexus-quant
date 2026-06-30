# NQ GateM-6D Operational Readiness Real Backend Smoke

> Task: `NQ-GATEM-6D-OPERATIONAL-READINESS-REAL-BACKEND-SMOKE`
> Type: `FRONTEND_E2E + REAL_BACKEND_SMOKE + OPERATIONAL_READINESS_VALIDATION + DOCUMENTATION_SYNC`
> Status: `PASS / REAL BACKEND SMOKE VERIFIED / SELF-REVIEWED / READY TO COMMIT`
> Date: `2026-06-30`

## Scope

This smoke validates the GateM-6B backend safe summary and GateM-6C frontend integration against a real local backend:

- real local `nq-app`, Spring profile `local`, port `18888`.
- `/runtime/readiness` frontend page.
- real authenticated `GET /api/runtime/operational-readiness`.
- no route mock for `/api/runtime/operational-readiness`.
- no full E2E matrix expansion.

This task did not modify frontend production code, backend code, migrations, research tools, scripts, deploy files, CI workflow, TradingWorkbench mutation logic, adapter behavior, actuator implementation, LIVE, AI, DH runtime, RealClient, or real provider behavior.

## Implementation

New spec:

- `frontend/tests/e2e/runtime-operational-readiness-real-backend-smoke.spec.ts`

Behavior:

- uses the existing local login endpoint only to obtain a browser session.
- does not use the account fixture helper, so it does not create or reset exchange accounts.
- performs a direct authenticated real backend preflight for `GET /api/runtime/operational-readiness`.
- opens `/runtime/readiness`.
- waits for the page's own real `GET /api/runtime/operational-readiness`.
- asserts the API and UI remain fail-closed:
  - `LIVE status = DISABLED`
  - `AI status = NOT_STARTED`
  - `DH runtime status = NOT_INTEGRATED`
  - `Real provider status = NOT_IMPLEMENTED`
  - `Credential exposure status = NOT_EXPOSED`
  - `Permission probe status = SKIPPED`
  - all operational rows remain `ready=false` and `BLOCKED`
- asserts the UI does not show live-ready, verified, or `LIVE 已授权`.
- asserts no browser call reaches external exchange hosts.
- asserts no permission-probe endpoint, ingestion run-once, order, cancel, transfer, or withdraw endpoint is called.

## Backend Environment

- Pre-start health: `http://127.0.0.1:18888/actuator/health` was unavailable, so no pre-existing backend process was reused.
- Startup command: `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run "-Dspring-boot.run.profiles=local"`.
- Runtime profile: `local`.
- Health before smoke: `/actuator/health = UP`.
- Direct readiness preflight: authenticated `GET /api/runtime/operational-readiness = 200`.
- Database startup observation: local PostgreSQL was reachable and Flyway reported schema version `31` up to date.
- Shutdown check: after stopping the backend job, `/actuator/health` was unavailable.

No raw environment dump, full config dump, credential material, exchange secret, API key, token value, or generated password value is recorded in this document.

## Validation

Executed locally on 2026-06-30:

| Command | Result | Notes |
| --- | --- | --- |
| `git status --short` | PASS | Worktree was clean before edits. |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build completed; existing large chunk warning remains. |
| backend startup + authenticated readiness preflight | PASS | `/actuator/health = UP`; authenticated `GET /api/runtime/operational-readiness = 200`. |
| `cd frontend; npm run test:e2e -- tests/e2e/runtime-operational-readiness-real-backend-smoke.spec.ts --project=chromium` | PASS | 1 Chromium real-backend smoke passed. |
| backend stop + health recheck | PASS | `/actuator/health` was unavailable after the backend job was stopped. |

Known non-blocking output:

- Vite large chunk warning remains.
- Playwright still prints the existing `NO_COLOR` / `FORCE_COLOR` warning.
- Maven/Spring startup printed existing compile warnings and a development generated password warning; the value was not copied into docs.

Not run:

- Full frontend E2E: not run because this task required one targeted real-backend smoke only.
- Maven test suite: not run because this task did not modify backend code and the backend was validated by startup health plus the real API/UI smoke.
- Python pytest / mypy / ruff: not run because this task did not modify `research/**`.

## Boundary Confirmation

- No backend changes.
- No migration changes.
- No frontend production code changes.
- No research / scripts / deploy / workflow changes.
- No new backend API.
- No actuator / backend readiness implementation change.
- No TradingWorkbench order, cancel, transfer, or withdraw behavior.
- No LIVE UI entry.
- No permission probe POST.
- No ingestion run-once.
- No external exchange browser requests.
- No credential material read or displayed.
- No raw environment or full config display.
- No LIVE enablement.
- No AI or DH runtime integration.
- No RealClient / real provider / real exchange adapter implementation.

## Findings

- P0: none.
- P1: none.
- P2: none.
- P3: none.

## Rollback

Revert:

- `frontend/tests/e2e/runtime-operational-readiness-real-backend-smoke.spec.ts`
- `docs/current/frontend/NQ_GATEM_6D_OPERATIONAL_READINESS_REAL_BACKEND_SMOKE.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

No backend, database, workflow, provider, exchange, LIVE, AI, or DH runtime side effect is involved.

## Final Decision

`PASS / REAL BACKEND SMOKE VERIFIED / SELF-REVIEWED / READY TO COMMIT`

Recommended commit:

```text
test(frontend): add operational readiness real backend smoke
```
