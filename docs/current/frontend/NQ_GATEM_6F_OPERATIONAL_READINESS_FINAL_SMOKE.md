# NQ GateM-6F Operational Readiness Final Smoke

Status: **PASS / FINAL SMOKE VERIFIED / SELF-REVIEWED / READY TO COMMIT**

Date: 2026-06-30

Scope: GateM-6 final smoke only. This validates the local runbook path, real local backend safe summary API, Runtime UI rendering, and no-write / no-real boundaries. It does not add business capability, API, migration, status matrix coverage, production deploy workflow, LIVE trading, AI runtime, DH runtime, RealClient, real provider, or real exchange integration.

## Implementation

- Added `frontend/tests/e2e/runtime-operational-readiness-final-smoke.spec.ts`.
- The smoke authenticates through the existing local login endpoint and directly verifies authenticated `GET /api/runtime/operational-readiness = 200`.
- The smoke opens `/runtime/readiness`, waits for the page's real `GET /api/runtime/operational-readiness`, and verifies the UI shows the real backend safe summary.
- The smoke asserts `LIVE=DISABLED`, `AI=NOT_STARTED`, `DH runtime=NOT_INTEGRATED`, `real provider=NOT_IMPLEMENTED`, `credential exposure=NOT_EXPOSED`, and `permission probe=SKIPPED`.
- The smoke asserts all operational rows remain fail-closed / `BLOCKED`.
- The smoke asserts no `live-ready`, `verified`, `LIVE authorized`, or `LIVE 已授权` positive UI signal is displayed.
- The smoke tracks browser requests and asserts no permission-probe endpoint, ingestion run-once, order, cancel, transfer, withdraw, or external exchange host request is called.

## Compatibility Fix

The first final smoke run exposed one UI wording mismatch: the Adapter readiness matrix summary used the table header `LIVE authorized`. Values were still `0`, but the phrase conflicted with the 6F requirement that the Runtime UI not display a positive `LIVE authorized` signal. The minimal production-code fix changed that header to `LIVE auth count` in `frontend/src/pages/runtime/RuntimeReadinessPage.tsx`. No data field, API contract, trading logic, backend behavior, or readiness calculation changed.

## Validation

| Command | Result | Notes |
| --- | --- | --- |
| `cd frontend; npm run build` | PASS | TypeScript build and Vite production build passed after the final smoke spec and the minimal UI label fix. Existing Vite large chunk warning remains. |
| backend startup: `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run "-Dspring-boot.run.profiles=local"` | PASS | Pre-start health was unavailable; the smoke started its own local backend. Startup reached `/actuator/health = UP`. |
| authenticated `GET /api/runtime/operational-readiness` | PASS | Direct API preflight returned HTTP `200`; token value and credential material were not recorded. |
| `cd frontend; npm run test:e2e -- tests/e2e/runtime-operational-readiness-final-smoke.spec.ts --project=chromium` | PASS | 1 Chromium final real-backend smoke passed. |
| backend stop + `/actuator/health` recheck | PASS | Backend job stopped; post-stop health was unavailable. |

Known warnings: Playwright still prints the existing `NO_COLOR` / `FORCE_COLOR` warning. Backend startup may emit local development warnings; generated password values are not copied into docs or reports.

Not run: full frontend E2E, Maven backend test suite, and Python pytest/mypy/ruff were not run because this task is final smoke-only and did not change backend, research, scripts, deploy, `.github`, or migration files.

## Boundary Confirmation

- LIVE remains DISABLED.
- AI remains NOT STARTED.
- DH runtime remains NOT_INTEGRATED.
- real exchange adapter / RealClient / real provider remains NOT_IMPLEMENTED.
- Actuator health UP is process health only, not readiness or LIVE authorization.
- Permission probe remains SKIPPED and was not invoked.
- No ingestion run-once, order, cancel, transfer, withdraw, or external exchange browser request was observed.
- No credential material, raw env, full config dump, or generated password value was recorded.
