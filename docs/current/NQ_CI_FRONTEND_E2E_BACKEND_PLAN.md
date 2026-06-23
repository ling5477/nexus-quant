# NQ CI Frontend E2E Backend Plan

任务：NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5A-PLAN
日期：2026-06-23
状态：5A plan = PASS / PLAN ONLY / NOT IMPLEMENTED；5A plan review = PASS / ACCEPTED AS BATCH 5B IMPLEMENTATION BASELINE；5B implementation = IMPLEMENTED；5C first-run review = FAIL / FIRST-RUN-FIX REQUIRED；5C-fix implementation = IMPLEMENTED / PENDING RE-RUN；NOT FROZEN

## Task classification

- Primary: `CI_PLANNING`
- Auxiliary: `FRONTEND_E2E_HARDENING_PLAN` / `BACKEND_STARTUP_REVIEW` / `SECURITY_BOUNDARY_REVIEW` / `DOCUMENTATION`
- Gate: GateK CI planning-only for real backend + frontend adapter readiness E2E readiness smoke.
- Boundary: this document plans CI only. It does not implement workflow changes, code changes, tests, migration, real provider, RealClient, AI, DH runtime, LIVE, or real exchange access.

## Scope

Allowed:

- Read-only inspect `.github/workflows/ci.yml`.
- Read-only inspect `frontend/tests/e2e/adapter-readiness-panel-backend-smoke.spec.ts`, `frontend/tests/e2e/run-e2e.mjs`, `frontend/tests/e2e/support.ts`, `frontend/playwright.config.ts`, `frontend/vite.config.ts`.
- Read-only inspect `backend/nq-app` local/test profile resources.
- Read-only inspect current CI / testing / worklog docs.
- Add this plan and minimally sync current docs.

Not allowed:

- No workflow edit.
- No Java / TypeScript / Python code edit.
- No test edit or new test.
- No API or migration.
- No frontend page or backend production logic change.
- No deploy/script change.
- No outbound exchange access, no real credential read, no repository secrets, no LIVE, no AI, no DH runtime, no RealClient / real provider / real permission probe.
- Do not describe OKX / Binance as future-real-ready.

## Files inspected

- `.github/workflows/ci.yml`
- `frontend/tests/e2e/adapter-readiness-panel-backend-smoke.spec.ts`
- `frontend/tests/e2e/run-e2e.mjs`
- `frontend/tests/e2e/support.ts`
- `frontend/playwright.config.ts`
- `frontend/vite.config.ts`
- `backend/nq-app/src/main/resources/application.yml`
- `backend/nq-app/src/main/resources/application-local.yml`
- `backend/nq-app/src/main/resources/application-test.yml`
- `docs/current/README.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/ROADMAP.md`
- `docs/current/STATUS.md`

Note: `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md` is no longer present in current docs in this working tree; PostgreSQL / Flyway authority is currently summarized through `NQ_CI_BASELINE_PLAN.md` and historical evidence indexes.

## Current CI state

- Current `ci.yml` jobs:
  - `diff-check`
  - `no-outbound-guard`
  - `ci-security-smoke`
  - `backend`
  - `postgres-flyway`
  - `frontend`
  - `frontend-no-backend-e2e`
  - `research`
  - `secret-scan`
- Existing PostgreSQL services:
  - `backend` job has `postgres:16` service with CI-only `postgres / 123456 / nexus_quant`.
  - `postgres-flyway` job has `postgres:16` service with CI-only `nq_ci_user / nq_ci_password / nq_ci`.
- Existing backend app context smoke:
  - `postgres-flyway` runs `NqAppContextPostgresSmokeTest`.
  - Batch 2D/2E are recorded as frozen/accepted in `NQ_CI_BASELINE_PLAN.md`.
- Existing frontend build:
  - `frontend` job runs `npm ci` and `npm run build`.
  - `frontend-no-backend-e2e` also runs production build before its four no-backend E2E specs.
- Existing Playwright / E2E job:
  - `frontend-no-backend-e2e` runs four explicit no-backend specs through `playwright.ci.config.ts`.
  - It intentionally does not start backend / PostgreSQL / Flyway / auth seed.
- Existing security/no-outbound:
  - `no-outbound-guard` and `ci-security-smoke` already assert CI env-name and no-outbound behavior.
  - `secret-scan` scans tracked safe files only and does not read local secret directories.

## Current E2E state

- `adapter-readiness-panel-backend-smoke.spec.ts` depends on a real backend. It does not stub readiness, calls `loginToConsole`, waits for a real `GET /api/adapters/readiness`, and asserts status 200 plus fail-closed UI/payload semantics.
- E2E login mechanism:
  - `support.ts` defaults to `E2E_USERNAME=admin` and `E2E_PASSWORD=ChangeMe123!`.
  - It posts to `/api/auth/login`, obtains a bearer token, lists/creates `OKX / SIM` test exchange accounts by alias, resets the default account, then performs the visible login form flow.
  - The token is used in memory by Playwright request helpers; CI plan must not print it.
- Local profile default auth:
  - `application-local.yml` defines local users including `admin`, `operator`, `viewer`, and `disabled`.
  - The default password hash matches the existing local E2E default password used by `support.ts`.
- Test profile:
  - `application-test.yml` also defines test users and defaults `nq.env-safety.no-outbound=true`.
  - Its Flyway is disabled by default, so using test profile for full runtime backend would need explicit DB/migration design and is not the first recommendation.
- Exchange bootstrap:
  - `application.yml` and profile resources use placeholder/sentinel exchange endpoints by default.
  - `application-local.yml` disables OKX recovery by default and disables OKX/Binance WS by default.
  - `application.yml` has catalog sync default true; the CI backend E2E job must explicitly disable any startup path that can depend on public exchange metadata.
- No-outbound coverage:
  - Current no-outbound guard is proven in test jobs and app-context smoke, but no CI job currently wraps a long-running `spring-boot:run` + Playwright process pair.
  - Therefore Batch 5B implementation should explicitly set `CI=true`, `NQ_NO_OUTBOUND=true`, placeholder endpoints, and either reuse the existing JVM/test guard pattern where possible or treat runtime process coverage as a P2 follow-up if not technically wired in the first implementation slice.

## Proposed CI job

- Job name: `frontend-e2e-backend-smoke`
- Purpose: prove one authenticated frontend smoke can run against a real local Spring Boot backend and real PostgreSQL service while adapter readiness remains fail-closed.
- Merge policy: blocking only after first green review and freeze acceptance.

### PostgreSQL

- Use a job-local `postgres:16` service, not another job's database.
- Recommended env:
  - `POSTGRES_USER: nq_e2e_user`
  - `POSTGRES_PASSWORD: nq_e2e_password`
  - `POSTGRES_DB: nq_e2e`
  - map `5432:5432`
  - health check `pg_isready -U nq_e2e_user -d nq_e2e`
- Rationale:
  - GitHub Actions service containers are job-scoped, so reusing Batch 2 service across jobs is not reliable.
  - A fresh DB avoids cross-job state leakage and makes seed/auth failures deterministic.

### Backend startup

Recommended first implementation command shape:

```bash
mvn -f backend/pom.xml -pl nq-app -am -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Dnq.no-outbound.guard.required=true" \
  > "${RUNNER_TEMP}/nq-backend-e2e.log" 2>&1 &
echo "$!" > "${RUNNER_TEMP}/nq-backend-e2e.pid"
```

Required backend env:

- `CI=true`
- `NQ_NO_OUTBOUND=true`
- `NQ_APP_PORT=18888`
- `NQ_DB_URL=jdbc:postgresql://localhost:5432/nq_e2e`
- `NQ_DB_USER=nq_e2e_user`
- `NQ_DB_PASSWORD=nq_e2e_password`
- `NQ_AI_ENABLED=false`
- `NQ_DH_RUNTIME_ENABLED=false`
- `NQ_REAL_EXCHANGE_ENABLED=false`
- `NQ_OKX_BASE_URL=PLACEHOLDER_ONLY`
- `NQ_OKX_WS_URL=PLACEHOLDER_ONLY`
- `NQ_BINANCE_DOME_BASE_URL=PLACEHOLDER_ONLY`
- `NQ_BINANCE_REAL_BASE_URL=PLACEHOLDER_ONLY`
- `NQ_BINANCE_DOME_WS_URL=PLACEHOLDER_ONLY`
- `NQ_BINANCE_REAL_WS_URL=PLACEHOLDER_ONLY`
- `NQ_INSTRUMENT_CATALOG_SYNC_ENABLED=false`
- `NQ_OKX_RECOVERY_ENABLED=false`
- `NQ_OKX_WS_ENABLED=false`
- `NQ_BINANCE_WS_ENABLED=false`

Do not inject:

- `NQ_LIVE_ENABLED`
- `NQ_REAL_PROVIDER_ENABLED`
- `NQ_REAL_CLIENT_ENABLED`
- Any OKX/Binance API key / secret / passphrase / private key env.

Reason: existing no-outbound guard treats those env names as forbidden in CI even when set to `"false"` in some contexts. Absence is safer and matches accepted 5B-ENV behavior.

### Health check

- Poll `http://127.0.0.1:18888/actuator/health` for up to 120 seconds.
- Success condition:
  - HTTP 200.
  - JSON status `UP`.
  - DB component is `UP` when included.
- Failure handling:
  - Print a bounded tail of backend log after redaction scan.
  - Do not print env.
  - Stop backend process.
  - Upload sanitized backend startup log only after redaction gate.

Example:

```bash
for i in {1..120}; do
  if curl -fsS http://127.0.0.1:18888/actuator/health | tee "${RUNNER_TEMP}/nq-health.json" | grep -q '"status":"UP"'; then
    exit 0
  fi
  sleep 1
done
exit 1
```

### Frontend startup

- First implementation should use the existing `npm run test:e2e -- adapter-readiness-panel-backend-smoke.spec.ts` runner.
- `run-e2e.mjs` starts Vite dev server at `127.0.0.1:5179`.
- `vite.config.ts` proxies dev server `/api` to `VITE_API_PROXY_TARGET` or default `http://127.0.0.1:18888`.
- Set `VITE_API_PROXY_TARGET=http://127.0.0.1:18888` explicitly.
- Do not use `vite preview` for this backend smoke unless a separate implementation first adds and reviews preview proxy behavior. Current `vite.config.ts` defines proxy only under `server`, not `preview`.

### Playwright command

Recommended first slice:

```bash
cd frontend
npm ci
npx playwright install --with-deps chromium
npm run build
VITE_API_PROXY_TARGET=http://127.0.0.1:18888 \
  npm run test:e2e -- adapter-readiness-panel-backend-smoke.spec.ts --project=chromium
```

Scope:

- Only run `adapter-readiness-panel-backend-smoke.spec.ts`.
- Do not run full E2E suite in Batch 5B implementation.
- Do not use retries to hide deterministic failures. Keep Playwright `retries=0`.
- Full E2E expansion is a later Batch 5E planning item.

### Shutdown

- Always kill the backend PID in a final `if: always()` step.
- Prefer process group cleanup when available; at minimum:

```bash
if [ -f "${RUNNER_TEMP}/nq-backend-e2e.pid" ]; then
  kill "$(cat "${RUNNER_TEMP}/nq-backend-e2e.pid")" || true
fi
```

- After kill, probe port 18888 or log process status to prove cleanup.

### Artifacts

Upload only after redaction gate:

- `backend-startup.log` sanitized from `${RUNNER_TEMP}/nq-backend-e2e.log`.
- `health.json` if it contains no secret-like material.
- Playwright trace/report/screenshot on failure only if a Batch 4C-compatible binary/text policy is defined for this job.
- npm/Vite log only if bounded, sanitized, and generated by CI.

Do not upload:

- Raw `.env`.
- Raw repository secrets.
- Token/cookie dumps.
- Full browser storage state.
- Unredacted backend logs.

## Security boundary plan

- CI must not use repository secrets for this job.
- CI must not define OKX/Binance real endpoints.
- CI must not inject real exchange credentials or credential-like env names.
- `local` backend profile is acceptable only with CI env hardening:
  - `CI=true`
  - `NQ_NO_OUTBOUND=true`
  - placeholder/sentinel endpoints
  - exchange recovery / WS / catalog sync disabled
  - no LIVE / no AI / no DH runtime / no real exchange flags
- Readiness API must return:
  - `allowed=false` for all items.
  - no `READY`.
  - no `liveAuthorized=true`.
  - OKX/Binance `NOT_READY`.
  - NOOP/PAPER/SIM `NO_REAL`.
  - permission probe `REAL_PROVIDER_NOT_IMPLEMENTED`.
- E2E must assert page does not display ready / usable / tradable state.
- Logs and artifacts must not contain `secret`, `apikey`, `api_key`, `token`, `signature`, `passphrase`, private key markers, cookie, or credential material.
- No CI step may call OKX / Binance / Bybit / Bitget / Coinbase / Gate / Kraken / Crypto.com / Hyperliquid.
- No success wording may imply OKX/Binance future-real readiness.

## No-outbound / credential boundary

Current accepted guard coverage:

- `no-outbound-guard` job validates forbidden env names, denylist coverage, and no-outbound guard tests.
- `ci-security-smoke` aggregates EnvSafety / no-outbound / NoReal smoke.
- `postgres-flyway` app context smoke has no-outbound guard evidence in current docs.

Gap for this new job:

- A long-running `spring-boot:run` process plus Playwright browser is a new CI runtime shape.
- If the existing test-scope `ProxySelector` guard is not installed into this `spring-boot:run` process, Batch 5B should still set denylist/no-outbound env and disable all startup exchange paths, but must record runtime no-outbound parity as P2 follow-up.
- It is not acceptable to omit this gap or claim full runtime no-outbound parity without evidence.

Required implementation evidence:

- Backend startup log has no real exchange host attempt.
- Health reaches UP without exchange bootstrap.
- Playwright readiness payload is fail-closed.
- No repository secret names or exchange credential env names are injected.

## Artifact / log redaction plan

- Reuse Batch 4C principles:
  - scan only generated artifact directory.
  - fail closed on high-risk credential patterns.
  - report rule + file only, never matched value or line.
  - reject binary artifacts unless a job-specific binary handling policy exists.
- For first implementation, prefer no Playwright artifact upload unless failure investigation requires it and a redaction gate is added in the same Batch 5B implementation review.
- If trace/report/screenshot upload is added:
  - upload on failure only.
  - use short retention, for example 7 days.
  - do not include storage state, cookies, local storage, bearer token, or raw API payload with sensitive fields.
  - scan text artifacts before upload.
  - document binary trace/screenshot limitations honestly.

## Failure handling

- PostgreSQL service failure:
  - fail job.
  - show `pg_isready` status and bounded service logs if available.
  - do not retry indefinitely.
- Flyway/backend startup failure:
  - fail job.
  - upload sanitized backend log.
  - classify whether failure is DB migration, Spring context, port, env safety, or startup exchange path.
- Auth/login failure:
  - fail job.
  - inspect whether local profile users loaded, DB migrated, JWT config loaded, or account fixture setup failed.
  - do not print bearer token.
- E2E failure:
  - classify as UI, API, backend, auth seed/account fixture, or CI environment.
  - do not rerun automatically to mask deterministic failure.
- Backend shutdown failure:
  - fail or mark as cleanup failure if port remains open.
  - collect process status without printing env.

## Batch split

- Batch 5A: this plan. Status: `PASS / PLAN ONLY / NOT IMPLEMENTED`.
- Batch 5A-review: review this plan and decide whether it is accepted as implementation baseline.
- Batch 5B: implementation. Status: `IMPLEMENTED / PENDING FIRST CI RUN`. Modify only `.github/workflows/ci.yml` and minimal current docs; add `frontend-e2e-backend-smoke` job.
- Batch 5C: first CI run review / fix. Verify target commit has GitHub Actions run, job conclusion, logs, health, payload, Playwright result, artifact handling, no-outbound and credential boundaries.
- Batch 5D: freeze review. Freeze only after first green evidence and no P0/P1 unresolved findings.
- Batch 5E: full E2E expansion plan. Optional / later; not part of this plan or implementation.

## Acceptance criteria

Future Batch 5B/5C/5D acceptance requires all of the following:

1. GitHub Actions job `frontend-e2e-backend-smoke` passes on the target commit.
2. Backend `/actuator/health` returns `UP`.
3. Playwright `adapter-readiness-panel-backend-smoke.spec.ts` passes against real local backend.
4. `GET /api/adapters/readiness` returns HTTP 200 after authenticated login.
5. Payload has exactly the expected 45 fail-closed entries or an intentionally reviewed equivalent matrix.
6. OKX/Binance entries are `NOT_READY`.
7. NOOP/PAPER/SIM entries are `NO_REAL`.
8. PLACE/CANCEL entries have `liveAuthorized=false`.
9. Permission probe reports `REAL_PROVIDER_NOT_IMPLEMENTED`.
10. Page does not display ready / usable / tradable state.
11. Logs/artifacts contain no secret, API key, token, signature, passphrase, cookie, private key, or credential material.
12. Backend process is stopped even on failure.
13. No LIVE, AI, DH runtime, RealClient, real provider, real permission probe, or real exchange call is started.
14. No OKX/Binance future-real-ready wording is introduced.

## Findings

### P0

- None in this planning review.

### P1

- `vite preview` is not suitable as-is for the backend smoke because current proxy configuration exists under Vite dev `server`, not `preview`. Batch 5B should use the existing `run-e2e.mjs` dev-server runner unless a separate reviewed preview proxy implementation is added.
- The real backend smoke requires deterministic account fixture setup through `support.ts`; Batch 5B must treat auth/account fixture failures as real failures, not as skip/pass.

### P2

- Runtime no-outbound parity for long-running `spring-boot:run` + Playwright is not yet proven as a CI job shape. If Batch 5B cannot install the same process-level guard in the running app, record this as a P2 follow-up and rely on env safety, disabled startup paths, placeholder endpoints, and log proof for the first slice.
- Binary Playwright trace/screenshot handling needs a specific redaction policy before upload. The first implementation should avoid binary uploads or upload failure-only with explicit limitations.
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md` is not present in current docs; current PostgreSQL/Flyway facts must be cited from `NQ_CI_BASELINE_PLAN.md` and evidence indexes.

## Docs changed

- Added `docs/current/NQ_CI_FRONTEND_E2E_BACKEND_PLAN.md`.
- Synchronized current docs entry points for plan-only status.

## Git status

To be captured after this planning document is added and docs are synchronized.

## Commit recommendation

Recommended commit message after validation:

```text
docs(ci): 规划真实后端前端 E2E smoke
```

Commit only documentation files from this planning batch. Do not include workflow, code, tests, migration, deploy, scripts, generated artifacts, local logs, or credentials.

## Recommended next task

`NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5A-PLAN-REVIEW`

Review this plan only. Do not implement `.github/workflows/ci.yml` until the plan review is accepted.

## Final recommendation

NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5A-PLAN：PASS.

The plan is acceptable as a planning baseline if Batch 5A-review confirms:

- `frontend-e2e-backend-smoke` remains independent and narrow.
- It starts a job-local PostgreSQL service and local backend.
- It runs only the adapter readiness real-backend smoke first.
- It preserves no-secret / no-outbound / no-LIVE / no-AI / no-DH / no-real-provider boundaries.
- It treats artifacts and logs as security-sensitive outputs.

Implementation is not started.

## Plan review addendum

任务：NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5A-PLAN-REVIEW
日期：2026-06-23
结论：PASS / ACCEPTED AS BATCH 5B IMPLEMENTATION BASELINE；REVIEW ONLY / NOT IMPLEMENTED

### Review verdict

The plan is sufficient to guide Batch 5B workflow implementation.

It correctly keeps the first CI slice narrow: a standalone `frontend-e2e-backend-smoke` job, job-local PostgreSQL, local backend health gate, Vite dev proxy, one Playwright backend smoke spec, deterministic shutdown, and security-sensitive artifact handling.

This review accepts the plan with no P0/P1 blockers. The P2 items below must be carried into Batch 5B/5C evidence and must not be silently described as already solved.

### Current state review

- `ci.yml` job inventory is accurate: `diff-check`, `no-outbound-guard`, `ci-security-smoke`, `backend`, `postgres-flyway`, `frontend`, `frontend-no-backend-e2e`, `research`, `secret-scan`.
- PostgreSQL service coverage is accurately described: `backend` and `postgres-flyway` each use job-local PostgreSQL service containers.
- Backend app context smoke is accurately described through `postgres-flyway` / `NqAppContextPostgresSmokeTest`.
- Frontend build and no-backend E2E are accurately described.
- There is no current real-backend frontend adapter readiness CI job.
- `adapter-readiness-panel-backend-smoke.spec.ts` really depends on a running local backend and a Vite dev `/api` proxy; it does not stub readiness.
- `support.ts` default login and fixture flow are suitable for a narrow CI smoke if treated as deterministic setup and not as skip/pass.
- `vite preview` has no `/api` proxy in current `vite.config.ts`; using the existing dev server runner is the correct first implementation choice.

### Proposed job review

- Job name: accepted. `frontend-e2e-backend-smoke` is explicit and independent.
- PostgreSQL: accepted. Use a separate job-local PostgreSQL service; do not attempt to reuse Batch 2 job state.
- Backend startup: accepted with one clarification. The command should be CI-online by default, not Maven offline, unless a later implementation proves all plugins/dependencies are cached in Actions. The existing `-pl nq-app -am -DskipTests spring-boot:run -Dspring-boot.run.profiles=local` shape is correct.
- Health check: accepted. `/actuator/health=UP` is the correct gate before Playwright.
- Frontend startup: accepted. `VITE_API_PROXY_TARGET=http://127.0.0.1:18888` matches `vite.config.ts` dev proxy and `run-e2e.mjs`.
- Playwright command: accepted. First slice should run only `adapter-readiness-panel-backend-smoke.spec.ts`; full E2E expansion is explicitly out of scope.
- Shutdown: accepted. Backend PID cleanup must be `if: always()` and should verify port/process cleanup.
- Artifacts: accepted. First implementation should avoid binary Playwright uploads unless a Batch 4C-compatible policy is implemented in the same workflow slice.

### Security boundary review

- Plan correctly forbids repository secrets, real credential env, real OKX/Binance endpoints, LIVE, AI, DH runtime, RealClient, real provider, and real permission probe.
- Plan correctly requires readiness to remain fail-closed: no `READY`, no `allowed=true`, no `liveAuthorized=true`, OKX/Binance `NOT_READY`, NOOP/PAPER/SIM `NO_REAL`, permission probe `REAL_PROVIDER_NOT_IMPLEMENTED`.
- Plan correctly requires E2E to assert no ready / usable / tradable state and no secret-like text.
- Plan correctly disables known startup risk paths for this job through env: `NQ_INSTRUMENT_CATALOG_SYNC_ENABLED=false`, `NQ_OKX_RECOVERY_ENABLED=false`, `NQ_OKX_WS_ENABLED=false`, `NQ_BINANCE_WS_ENABLED=false`, and placeholder endpoints.
- Plan correctly avoids injecting `NQ_LIVE_ENABLED`, `NQ_REAL_PROVIDER_ENABLED`, and `NQ_REAL_CLIENT_ENABLED`, preserving the accepted 5B-ENV env-name boundary.

### No-outbound / credential boundary review

Accepted with P2 follow-up.

Batch 3 no-outbound evidence proves the test-scope guard and app-context smoke path, but it does not automatically prove a long-running `spring-boot:run` process plus Playwright browser runtime shape. The plan correctly names this as a gap instead of overstating coverage.

Batch 5B may proceed without first implementing new no-outbound runtime parity if it:

- keeps all exchange endpoints placeholder/sentinel,
- disables startup exchange paths,
- avoids real credential env and repository secrets,
- records backend startup logs,
- verifies no real exchange host appears in logs/artifacts,
- carries runtime parity as P2 until 5B/5C evidence closes it.

### Artifact / log redaction review

Accepted.

The plan correctly reuses Batch 4C principles: scan generated artifact directories only, fail closed, report rule + file only, never matched values, and avoid scanning `.env`, secrets, dumps, backups, or local forbidden directories.

For Batch 5B, artifact redaction is not optional if backend logs or Playwright output are uploaded. If binary traces/screenshots are not covered by a concrete policy, first implementation should not upload them.

### Failure handling review

Accepted.

The plan covers backend health timeout, PostgreSQL service failure, Flyway/Spring context startup failure, auth/login failure, fixture setup failure, readiness API failure, Playwright failure, backend cleanup failure, and no retry-as-pass. Batch 5B should preserve `retries=0` and classify deterministic failures rather than masking them.

### Batch split review

Accepted:

- Batch 5A: plan.
- Batch 5A-review: this review.
- Batch 5B: workflow implementation.
- Batch 5C: first CI run review / fix.
- Batch 5D: freeze review.
- Batch 5E: optional full E2E expansion plan, later only.

### Batch 5B entry decision

Allowed to enter Batch 5B implementation after this review.

Batch 5B default scope:

- Allowed: `.github/workflows/ci.yml` and minimal docs status updates.
- Not allowed by default: Java / TypeScript / Python production code, backend production logic, frontend page code, migration, deploy, scripts, real provider, RealClient, real permission probe, LIVE, AI, DH runtime, real exchange endpoint, repository secrets.
- `run-e2e.mjs`, `vite.config.ts`, and the E2E spec should not be changed in Batch 5B unless the implementation uncovers a CI-only test wiring issue that cannot be solved in workflow env/command configuration. If such a carve-out is needed, it must be minimal, explicitly justified, and still avoid product/runtime behavior changes.

### Review findings

#### P0

- None.

#### P1

- None.

#### P2

- Runtime no-outbound parity for `spring-boot:run` + Playwright is not yet proven by existing Batch 3 evidence. Carry as P2 until Batch 5B/5C proves or explicitly scopes it.
- Binary Playwright trace/screenshot upload remains deferred unless a concrete Batch 4C-compatible handling policy is added.
- CI implementation should not use Maven `-o` by default; GitHub Actions may not have all Spring Boot plugin/dependency artifacts cached. This is an implementation command hygiene point, not a plan blocker.

### Final review recommendation

NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5A-PLAN-REVIEW：PASS.

The plan is accepted as the Batch 5B implementation baseline. Batch 5B may proceed as a workflow-only implementation slice, with P2 no-outbound runtime parity and artifact binary-handling limits carried forward explicitly.

## Batch 5B implementation addendum

任务：NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5B-IMPLEMENTATION
日期：2026-06-23
结论：IMPLEMENTED / PENDING FIRST CI RUN；NOT FROZEN

### Implementation summary

Batch 5B added an independent GitHub Actions job `frontend-e2e-backend-smoke` to `.github/workflows/ci.yml`.

The job is intentionally narrow:

- Starts a job-local `postgres:16` service with CI-only `nexus / nexus / nexus_quant` database settings.
- Starts `nq-app` with local profile on port `18888` using `mvn -f backend/pom.xml -pl nq-app -am -DskipTests spring-boot:run -Dspring-boot.run.profiles=local`.
- Sets `CI=true`, `NQ_NO_OUTBOUND=true`, placeholder exchange endpoints, and disables catalog sync / OKX recovery / OKX WS / Binance WS.
- Does not inject `NQ_LIVE_ENABLED`, `NQ_REAL_PROVIDER_ENABLED`, `NQ_REAL_CLIENT_ENABLED`, repository secrets, or real exchange credential env names.
- Waits for `http://127.0.0.1:18888/actuator/health` to report `UP`.
- Uses the existing Vite dev server runner through `npm run test:e2e -- adapter-readiness-panel-backend-smoke.spec.ts --project=chromium` with `VITE_API_PROXY_TARGET=http://127.0.0.1:18888`.
- Runs only the adapter readiness backend smoke spec, not the full E2E suite.
- Always attempts to stop the backend process group and confirms the health endpoint no longer responds.
- Uploads only generated text artifacts (`backend.log`, `health.json`) after a pre-upload redaction gate; binary Playwright trace/report/screenshot upload remains deferred.

### Security boundary

- No repository secrets are used.
- No real exchange credentials are injected.
- No real OKX/Binance endpoint is configured.
- LIVE / AI / DH runtime / RealClient / real provider / real permission probe remain disabled or not implemented.
- Readiness remains expected to fail closed: no `READY`, no `allowed=true`, no `liveAuthorized=true`, OKX/Binance `NOT_READY`, NOOP/PAPER/SIM `NO_REAL`, permission probe `REAL_PROVIDER_NOT_IMPLEMENTED`.
- `spring-boot:run` + Playwright runtime no-outbound parity remains a P2 evidence item until Batch 5C verifies the first GitHub Actions run logs/artifacts.

### Validation status

This document records implementation wiring only. The GitHub Actions first run has not yet been reviewed, so Batch 5B must not be described as frozen or first-green confirmed.

Expected next evidence task:

`NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5C-FIRST-RUN-REVIEW`

## Batch 5C first-run review addendum

任务：NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5C-FIRST-RUN-REVIEW
日期：2026-06-23
结论：FAIL / FIRST-RUN-FIX REQUIRED；NOT FROZEN；NOT FIRST GREEN

### Run evidence

- Workflow: NQ CI Baseline.
- Run id / URL: `28033918182` / `https://github.com/ling5477/nexus-quant/actions/runs/28033918182`.
- Commit: `2e9c956ebc5b0a01b57f44e98642553e37bf7226` (`2e9c956e`).
- Branch / trigger: `dev` / `push`.
- Overall status: completed / failure.
- Target job: `Frontend backend E2E smoke` (`frontend-e2e-backend-smoke`, job id `82981901389`) existed and completed failure.

### Job review

- PostgreSQL service: `Initialize containers` success.
- Backend startup: `Start nq-app local backend` success.
- Health check: `Wait for backend health UP` success.
- Frontend E2E: `Run adapter readiness backend E2E` success.
- Playwright command: workflow command remains `npm run test:e2e -- adapter-readiness-panel-backend-smoke.spec.ts --project=chromium`, so the target job did not expand to full E2E.
- Shutdown: `Cleanup backend process` success; `Stop containers` success.
- Artifact preparation: `Prepare sanitized backend smoke artifacts` success.
- Artifact upload: skipped because pre-upload artifact redaction gate failed.
- First failing step: `Pre-upload redaction gate (frontend backend smoke artifacts)`.

### Security / no-outbound evidence

- The job uses `contents: read` only and the workflow review found no `secrets.` reference in the target job.
- The job env uses `CI=true`, `NQ_NO_OUTBOUND=true`, placeholder OKX/Binance endpoints, `NQ_AI_ENABLED=false`, `NQ_DH_RUNTIME_ENABLED=false`, and `NQ_REAL_EXCHANGE_ENABLED=false`.
- The target job does not inject `NQ_LIVE_ENABLED`, `NQ_REAL_PROVIDER_ENABLED`, or `NQ_REAL_CLIENT_ENABLED`.
- `NQ_INSTRUMENT_CATALOG_SYNC_ENABLED=false`, `NQ_OKX_RECOVERY_ENABLED=false`, `NQ_OKX_WS_ENABLED=false`, and `NQ_BINANCE_WS_ENABLED=false` remain set for this job.
- Backend log content and generated smoke artifacts were not available to this review because GitHub job-log download returned HTTP 403 and the smoke artifact upload was correctly skipped after the redaction gate failure.
- Because logs/artifacts were not inspectable, `spring-boot:run + Playwright runtime no-outbound parity` remains P2 evidence gap and is not closed by this run.

### Artifact / redaction review

- Run artifact metadata shows only the existing `nq-postgres-flyway-schema-artifacts` artifact was uploaded.
- `nq-frontend-e2e-backend-smoke-artifacts` was not uploaded because `Pre-upload redaction gate (frontend backend smoke artifacts)` failed before upload.
- This is fail-closed and prevents potentially sensitive backend log / health output from being published, but it means Batch 5C cannot accept the run as first green.
- The failure likely comes from a high-risk pattern in generated text artifacts such as `backend.log` or `health.json`; exact rule/file output could not be read due GitHub Actions log permission 403 in this review.

### Failure classification

- Primary failure: artifact redaction fail.
- First failing step: `Pre-upload redaction gate (frontend backend smoke artifacts)`.
- Non-primary areas that passed: PostgreSQL service, backend startup, backend health, Playwright backend smoke, backend cleanup, existing jobs.
- Existing jobs: Diff check, no-outbound guard, CI security smoke, backend Maven test, PostgreSQL/Flyway smoke, frontend build, frontend no-backend E2E, research quality gate, and secret scan all completed success.
- Minimal next fix recommendation: open a separate `NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5C-FIX` task to inspect the redaction gate finding, then either suppress/avoid the benign generated log line at source, tighten sanitization so the post-sanitized artifact no longer contains value-bearing assignment text, or narrow the artifact gate rule only if the finding is demonstrably a false positive and still does not print matched values.
- Batch 5D freeze is blocked until a rerun of the target job is green and artifact/log evidence is reviewable.

### Review findings

#### P0

- None identified from accessible metadata.

#### P1

- The first run failed at the artifact redaction gate. This blocks first-green acceptance and Batch 5D freeze.

#### P2

- Backend log / health artifact content could not be inspected because the smoke artifact was not uploaded after fail-closed redaction and job log download returned GitHub API HTTP 403.
- `spring-boot:run + Playwright runtime no-outbound parity` remains unclosed because log/artifact evidence is unavailable even though the target smoke path itself passed.

### Final review recommendation

NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5C-FIRST-RUN-REVIEW：FAIL.

Do not mark Batch 5B as first green or frozen. Proceed only to `NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5C-FIX`, then rerun first-run review. Freeze remains Batch 5D and is blocked by this failed first run.

## Batch 5C-fix implementation addendum

任务：NQ-CI-FRONTEND-E2E-BACKEND-BATCH-5C-FIX
日期：2026-06-23
结论：IMPLEMENTED / PENDING RE-RUN；NOT FROZEN；NOT FIRST GREEN

### Failure source

- Run: `28033918182`.
- Job: `frontend-e2e-backend-smoke`.
- First failing step: `Pre-upload redaction gate (frontend backend smoke artifacts)`.
- Root cause: current workflow generated `backend.log` with sensitive assignment field names preserved as `<field>=<redacted>`. The pre-upload gate intentionally matches assignment-shaped strings such as `secret=`, `token=`, `password=`, and `signature=`, so a sanitized-but-still-assignment-shaped log can fail closed. Because job logs returned HTTP 403 and artifact upload was skipped, the exact CI rule/file output remains uninspectable; the fix removes that failure class without weakening the gate.
- Affected artifacts: generated text-only `backend.log` and `health.json`.

### Redaction gate fix

- Scan directory: changed frontend backend smoke artifacts from repo workspace `artifacts/frontend-e2e-backend-smoke` to generated temp dir `${RUNNER_TEMP}/nq-frontend-e2e-backend-smoke-artifacts`.
- File list: added a metadata-only debug step that prints file name, byte size, MIME type, and MIME encoding; it never prints artifact content.
- Sanitization: backend raw log is transformed into uploaded `backend.log` by replacing the full sensitive assignment token shape with `[redacted-sensitive-assignment]`, so uploaded text no longer preserves `secret=` / `token=` / `password=` / `signature=` keys.
- File filter: gate still rejects binary files before grep; Playwright trace/report/screenshot/video remain not uploaded.
- Rules: high-risk credential, token, key, raw payload, and real exchange host patterns remain enforced.
- Output policy: findings are `REDACTION_HIT rule=<rule> file=<path>` only; matched values and lines are never printed.
- Fail-closed behavior: missing artifact dir, missing/empty required files, binary files, or any rule hit fail the job.
- Upload dependency: `Upload frontend backend smoke artifacts` still runs only when the redaction gate succeeds.

### Batch state

Batch 5C-fix is implemented and awaits a GitHub Actions re-run. This does not establish first green evidence and does not unblock Batch 5D freeze by itself. Next step is re-run review of `frontend-e2e-backend-smoke`; only a green target run with reviewable artifact/log evidence may proceed toward Batch 5D freeze review.
