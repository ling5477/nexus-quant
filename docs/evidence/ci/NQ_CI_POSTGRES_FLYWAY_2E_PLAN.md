# NQ CI PostgreSQL / Flyway Batch 2E Plan

任务：NQ-CI-POSTGRES-FLYWAY-2E-PLAN / NQ-CI-POSTGRES-FLYWAY-2E-IMPL / NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-FIX / NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW-AFTER-FIX / NQ-CI-POSTGRES-FLYWAY-2E-FREEZE-REVIEW
日期：2026-06-16
状态：FROZEN / ACCEPTED

## Task classification

- Primary type: `CI_CD`
- Auxiliary types: `DOCUMENTATION`, `SECURITY_BOUNDARY_GUARD`, `TEST_BASELINE_FIX`, `CI_FREEZE_REVIEW`
- 主 skill：`nq-dh-workflow-router`，用于确认 NQ / GateK / CI / credential / LIVE / DH 边界。
- 本轮冻结 Batch 2E seed watcher cleanup baseline：只评审 run `27614046762` 与已落地的 first-run fix 证据，并同步允许的 `docs/current` 状态记录；不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。

## Scope

- Repository: NexusQuant / NQ。
- Branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- Batch state: 2A / 2B / 2C / 2C-HYGIENE-FIX / 2D 均为 FROZEN / ACCEPTED；Batch 2E watcher cleanup first GitHub Actions run failed 后已完成 scoped first-run fix；GitHub Actions run `27614046762` 已确认 first green，并经本 freeze review 接受为 FROZEN / ACCEPTED；Batch 3-5 仍 PENDING。
- Target files inspected:
  - `.github/workflows/ci.yml`
  - `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/AuthSeedConfiguration.java`
  - `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/AuthBootstrapAdminConfiguration.java`
  - `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java`
  - `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/JdbcRepositoryPostgresSmokeTest.java`
  - `backend/nq-app/src/main/resources/application.yml`
  - `backend/nq-app/src/main/resources/application-local.yml`
  - `backend/nq-app/src/main/resources/application-test.yml`
  - `backend/nq-infra/src/main/resources/db/migration/V1__init.sql`
  - `backend/nq-infra/src/main/resources/db/migration/V12__rc1_account_and_credentials.sql`
  - `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- Explicit exclusions:
  - 不修改后端 / 前端 / research / scripts / deploy / migration / 测试代码。
  - 不新增 seed users、legacy accounts、exchange accounts 或 credential rows。
  - 不进入 Batch 3 no-outbound guard、Batch 4 security guard / secret scan、Batch 5 frontend E2E hardening。

## Files inspected

- `.github/workflows/ci.yml`：当前 `backend` job 的 CI-only seed watcher 已删除；`Run backend tests` step 直接执行 `mvn -f backend/pom.xml test`；`postgres-flyway` job 不使用该 watcher。
- `.github/workflows/ci.yml`：first-run fix 在 backend Maven test 前新增同步 `Prepare backend CI legacy account fixture` step；该 step 先用 Flyway API 在 backend job 的 disposable PostgreSQL 上迁移并 validate 到 V31，再插入一条 CI-only legacy `accounts` fixture，并校验没有创建 `exchange_accounts` 或 `exchange_account_credentials` rows。
- `AuthSeedConfiguration.java`：确认仅 `local` / `test` profile 下注册 seed users runner。
- `AuthBootstrapAdminConfiguration.java`：确认只在 `nq.auth.bootstrap-admin.enabled=true` 时注册 bootstrap admin runner。
- `NqAppContextPostgresSmokeTest.java`：确认 Batch 2D 使用 `ci-app-smoke` profile、`nq.auth.bootstrap-admin.enabled=false`、`spring.flyway.enabled=false`、WS disabled、scheduler disabled；不使用 `local` 或 current `test` profile。
- `JdbcRepositoryPostgresSmokeTest.java`：确认 Batch 2C 使用显式 `nq.postgres.smoke.*` datasource，repository-only，事务 rollback，不启动 `nq-app` context。
- `application.yml` / `application-local.yml` / `application-test.yml`：确认默认 profile 是 `local`，local/test 会提供 auth seed users；Batch 2D 通过 `ci-app-smoke` 避开这些 profile。
- `V1__init.sql`：确认 legacy `accounts` 表由 V1 创建。
- `V12__rc1_account_and_credentials.sql`：确认会从 legacy `accounts` 回填 `exchange_accounts`，但未插入 `exchange_account_credentials`。

## Files changed

- Updated:
  - `.github/workflows/ci.yml`
  - `docs/current/NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md`
  - `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
  - `docs/current/README.md`
  - `docs/current/TESTING.md`
  - `docs/current/WORKLOG.md`
- Not changed:
  - `backend/**`
  - `frontend/**`
  - `research/**`
  - `scripts/**`
  - `deploy/**`
  - `backend/**/db/migration/**`

## Implementation summary

- Removed the `backend` job background seed watcher from `.github/workflows/ci.yml`.
- `Run backend tests` now directly executes `mvn -f backend/pom.xml test`.
- Removed watcher polling for Docker `postgres:16`, `public.accounts` detection, `ci-local-account` insertion, `seed_pid`, `wait`, and seed exit-status merge logic.
- No fallback SQL was added because local backend Maven test passed after watcher deletion.
- No `postgres-flyway` job step was changed; Batch 2A / 2B / 2C / 2D paths remain intact.

## First-run fix

- Failure log access: GitHub MCP decoded job logs for run `27610448572`, job `81633181802` were available in the first-run fix turn. Earlier `gh` log access remained 403, but MCP logs provided the Maven / Surefire failure lines.
- Failing module: `nq-app`。
- Failing test class: `com.guidinglight.nexusquant.app.web.ResearchBacktestHappyPathLocalTest`。
- Failing test method: `shouldRunMinimalDbBackedResearchBacktestEvalHappyPath`。
- Failing line: `ResearchBacktestHappyPathLocalTest.java:59`。
- SQL / stack trace: `JdbcTemplate.queryForObject("SELECT account_id FROM accounts ORDER BY account_id LIMIT 1", Long.class)` threw `org.springframework.dao.EmptyResultDataAccessException: Incorrect result size: expected 1, actual 0`。
- Surefire summary: `Tests run: 53, Failures: 0, Errors: 1, Skipped: 1`。
- Maven reactor impact: `nq-app` failed; previous 22 reactor modules succeeded.
- Root cause: GitHub backend job uses a fresh PostgreSQL service DB. After the background watcher deletion, the `local` profile happy-path test no longer has any legacy `accounts` row. The failure is test fixture ownership for the legacy `accounts` table, not a Flyway migration failure, not an `exchange_accounts` backfill failure, and not a credential row failure.
- Fix applied: added `.github/workflows/ci.yml` backend job step `Prepare backend CI legacy account fixture` before `Run backend tests`。
- Fixture sequence:
  1. Runs Flyway `migrate()` + `validate()` on the backend job disposable DB with `baselineOnMigrate(false)`, `cleanDisabled(true)`, and `outOfOrder(false)`.
  2. Verifies current Flyway version is V31.
  3. Inserts one CI-only legacy row into `accounts`: `account_code='ci-backend-test-account'`, `venue='PAPER'`, `status='ACTIVE'`.
  4. Verifies exactly one matching legacy `accounts` row exists.
  5. Verifies no `exchange_accounts` row is created for that fixture.
  6. Verifies `exchange_account_credentials` remains empty.
- Fallback decision: use explicit synchronous CI-only fixture SQL after Flyway migration completion and before backend Maven test. Do not restore the background watcher.
- First green follow-up: GitHub Actions run `27614046762` confirmed backend Maven test and `postgres-flyway` job both green after the first-run fix. At the first green checkpoint, Batch 2E status was FIRST GREEN RUN CONFIRMED, not FROZEN or ACCEPTED; this was later closed by the freeze review below.

## Freeze review

- Freeze decision: PASS / FROZEN / ACCEPTED for Batch 2E seed watcher cleanup baseline.
- Freeze scope: accept the current `dev` CI baseline where the backend job background seed watcher remains deleted and the backend job uses an explicit post-Flyway CI-only legacy `accounts` fixture before `mvn -f backend/pom.xml test`.
- GitHub Actions evidence: run `27614046762`, workflow `NQ CI Baseline`, branch `dev`, commit `50c4c65e956b207bcfa47b4ed2027b452d3809fc`, completed / success.
- Required jobs: `Diff check`, `Backend Maven test`, `Frontend build`, `Research quality gate`, and `PostgreSQL / Flyway smoke` all completed / success.
- Backend evidence: job `81645397239` completed / success; `Prepare backend CI legacy account fixture` and `Run backend tests` completed / success; `ResearchBacktestHappyPathLocalTest` tests=1 / failures=0 / errors=0 / skipped=0; backend reactor 23/23 modules SUCCESS; `nq-app` SUCCESS; Maven `BUILD SUCCESS`.
- Fixture evidence: `BackendCiLegacyAccountFixture` runs Flyway `migrate()` + `validate()` with `baselineOnMigrate(false)`, `cleanDisabled(true)`, and `outOfOrder(false)`, verifies current version V31, inserts only `ci-backend-test-account / PAPER / ACTIVE` into legacy `accounts`, and fail-closes on linked `exchange_accounts` rows or any `exchange_account_credentials` row.
- Seed watcher removal evidence: backend job has no background seed watcher, no `public.accounts` polling, no `ci-local-account`, no `seed_pid`, and no watcher `wait` / exit-status merge.
- PostgreSQL / Flyway evidence: job `81645397302` completed / success; empty DB Flyway smoke, schema artifacts, repository PostgreSQL smoke, and `nq-app` context smoke all remained green; `NqAppContextPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0; Maven `BUILD SUCCESS`.
- Security boundary: no API key, secret, passphrase, token, private key, encrypted_payload, decrypted_payload, or real credential material was written by 2E; no LIVE, AI, DH runtime, RealClient, real provider, or real exchange adapter was started; no OKX / Binance / Bybit / Gate / Coinbase / Kraken call was introduced by 2E.
- Batch boundary: Batch 3 no-outbound guard, Batch 4 security guard / secret scan, and Batch 5 frontend E2E hardening remain PENDING.
- Freeze note: Batch 2E FROZEN / ACCEPTED does not prove Batch 3 no-outbound guard, does not add Batch 4 secret scanning, and does not harden Batch 5 frontend E2E.

## First green run review after first-run fix

- GitHub Actions run: `27614046762`, workflow `NQ CI Baseline`, branch `dev`, commit `50c4c65e956b207bcfa47b4ed2027b452d3809fc`, event `push`, title `ci(gatek): add explicit backend CI account fixture`.
- Run result: completed / success.
- `Diff check`: job `81645397268` completed / success.
- `Frontend build`: job `81645397229` completed / success；`npm ci` and `npm run build` completed, with only the known Vite chunk-size warning and existing `npm audit` advisory summary.
- `Research quality gate`: job `81645397244` completed / success；pytest `2 passed`, mypy `Success: no issues found in 8 source files`, ruff `All checks passed!`.
- `Backend Maven test`: job `81645397239` completed / success.
  - Step `Prepare backend CI legacy account fixture`: completed / success.
  - Step `Run backend tests`: completed / success.
  - Log evidence: fixture step generated and ran `BackendCiLegacyAccountFixture`, using Flyway `migrate()` + `validate()` with `baselineOnMigrate(false)`, `cleanDisabled(true)`, and `outOfOrder(false)` before backend tests.
  - Log evidence: fixture code uses `FIXTURE_ACCOUNT_CODE = "ci-backend-test-account"` and inserts exactly one legacy `accounts` row with `PAPER / ACTIVE`.
  - Log evidence: fixture code fail-closes on `exchange_accounts` rows linked to the fixture and on any `exchange_account_credentials` row.
  - Log evidence: no background watcher, no `public.accounts` polling loop, no `ci-local-account`, no `seed_pid`, and no watcher `wait` / exit-status merge appears in the backend job steps.
  - Log evidence: `ResearchBacktestHappyPathLocalTest` ran with tests=1 / failures=0 / errors=0 / skipped=0.
  - Log evidence: backend Maven reactor 23/23 modules SUCCESS; `nq-app` SUCCESS; final Maven `BUILD SUCCESS`.
- `PostgreSQL / Flyway smoke`: job `81645397302` completed / success.
  - Steps `Run empty database Flyway smoke`, `Generate PostgreSQL schema artifacts`, `Check PostgreSQL schema artifacts`, `Upload PostgreSQL schema artifacts`, `Run repository PostgreSQL smoke`, and `Run nq-app PostgreSQL context smoke` all completed / success.
  - Log evidence: `NqAppContextPostgresSmokeTest` ran under `ci-app-smoke` with tests=1 / skipped=0 / failures=0 / errors=0; `nq-app` SUCCESS; Maven `BUILD SUCCESS`.
  - 2A / 2B / 2C / 2D accepted baselines remain green.
- Log access note: GitHub MCP provided run jobs and decoded job logs. A later `gh run view --log` retry hit GitHub unauthenticated rate limiting, so detailed log review used GitHub MCP output plus workflow static inspection.
- First green decision: PASS / ACCEPTED FOR FIRST GREEN RUN. At that review point, Batch 2E was FIRST GREEN RUN CONFIRMED, not FROZEN / ACCEPTED; this was later closed by this freeze review.

## First-run review

- GitHub Actions run: `27610448572`, workflow `NQ CI Baseline`, branch `dev`, commit `d149952bbd39883847302996b0930437890b8121`, event `push`, title `ci(gatek): remove backend CI seed watcher`.
- Run result: completed / failure.
- `Diff check`: success.
- `Frontend build`: success.
- `Research quality gate`: success.
- `PostgreSQL / Flyway smoke`: success. Steps `Run empty database Flyway smoke`, `Generate PostgreSQL schema artifacts`, `Check PostgreSQL schema artifacts`, `Upload PostgreSQL schema artifacts`, `Run repository PostgreSQL smoke`, and `Run nq-app PostgreSQL context smoke` all completed / success.
- `Backend Maven test`: failure. Step `Run backend tests` completed / failure with exit code 1.
- Failure detail gap: `gh run view --log-failed` and job log download returned HTTP 403 (`Must have admin rights to Repository`), so this review could not read the Maven stack trace or failing test name. The next fix task must first retrieve the backend job log and record the exact failing test, SQL / stack trace, and root cause before changing workflow or adding any fixture.
- First-run decision: FAIL / FIRST-RUN-FIX REQUIRED. Batch 2E must not be marked FIRST GREEN, FROZEN, or ACCEPTED in this state.

## Files changed in planning turn

- Added: `docs/current/NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md`
- Updated:
  - `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
  - `docs/current/README.md`
  - `docs/current/TESTING.md`
  - `docs/current/WORKLOG.md`
- Not changed:
  - `.github/workflows/ci.yml`
  - `backend/**`
  - `frontend/**`
  - `research/**`
  - `scripts/**`
  - `deploy/**`
  - `backend/**/db/migration/**`

## Current CI state

| Job | Current state | 2E relevance |
| --- | --- | --- |
| `diff-check` | `git diff --check` hygiene gate。 | Not affected. |
| `backend` | Runs `Prepare backend CI legacy account fixture` followed by `mvn -f backend/pom.xml test` with PostgreSQL service `postgres:16`; the CI-only background seed watcher remains removed. | Primary 2E target. The backend job no longer inserts `ci-local-account` through a watcher; it uses one synchronous post-Flyway legacy `accounts` fixture and guards against `exchange_accounts` / credential rows. |
| `postgres-flyway` | Runs empty DB Flyway smoke, schema artifacts, repository PostgreSQL smoke, and `nq-app` context smoke. | 2A-2D accepted baseline. Does not use the backend job seed watcher. |
| `frontend` | `npm ci` + `npm run build`。 | Not affected by 2E. |
| `research` | Python install + pytest + mypy + ruff。 | Not affected by 2E. |

## Seed watcher inventory

Removed watcher location:

- `.github/workflows/ci.yml`, `backend` job, step `Run backend tests`。

Removed watcher behavior:

1. Starts in background before `mvn -f backend/pom.xml test`。
2. Polls Docker for the `postgres:16` service container.
3. Waits until `SELECT to_regclass('public.accounts')` returns `accounts`.
4. Runs:
   - `INSERT INTO accounts (account_code, venue, status) VALUES ('ci-local-account', 'PAPER', 'ACTIVE') ON CONFLICT (account_code) DO NOTHING;`
5. Waits for Maven and watcher exit status; job fails if Maven fails or watcher times out.

Removed direct write:

- The backend job no longer directly writes the legacy `accounts` row.

Removed indirect effect:

- The removed watcher can no longer race with `V12__rc1_account_and_credentials.sql` and can no longer indirectly create an `exchange_accounts` row with:
  - `exchange_code = UPPER('PAPER')`
  - `trade_env = 'SIM'`
  - `account_alias = 'ci-local-account'`
  - `legacy_account_id = accounts.account_id`
  - `status = 'ACTIVE'`
- No inspected migration inserts `exchange_account_credentials` rows from the watcher row.

Historical purpose:

- Batch 1 compatibility workaround for full backend Maven tests on fresh GitHub runners.
- It exists because local-profile Spring context tests include code paths that expect at least one legacy account row.
- It is not part of Flyway validation, not production seed, not runtime startup seed, and not a schema baseline.

## Batch 2E cleanup plan

### Decision target

Batch 2E should remove the long-lived ad hoc watcher if backend Maven tests can pass with explicit fixture ownership. If deletion is not possible in one implementation slice, Batch 2E should first narrow the watcher so it cannot mask migration/schema/app-context defects.

### Recommended implementation slices

1. `2E-1`: Confirm dependency by CI-local reproduction or review-only dry run.
   - Command baseline for future implementation:
     - `mvn -f backend/pom.xml test`
     - GitHub Actions `backend` job without watcher, or equivalent disposable PostgreSQL service reproduction.
   - Success:
     - If backend job passes without watcher, delete watcher in 2E implementation.
     - If backend job fails due to missing account fixture, capture exact failing test and SQL / stack trace before adding any replacement.

2. `2E-2`: Replace watcher with explicit fixture ownership if needed.
   - Preferred replacement: test-owned fixture setup in the specific local-profile tests that require a legacy account.
   - Acceptable CI-only replacement only if test code cannot be changed in the same slice: a named fixture SQL step that runs after Flyway reaches the final version and before the specific backend tests that require the row.
   - Required fixture properties:
     - CI-only fake values.
     - `PAPER` / `ACTIVE` legacy account only if the existing test still needs legacy `accounts`.
     - No `exchange_account_credentials`.
     - No API key, secret, passphrase, token, cookie, private key, mnemonic, decrypted payload or raw request / response.
     - Explicit cleanup or disposable DB lifecycle.

3. `2E-3`: If deletion is blocked, narrow the existing watcher.
   - Do not let it race with Flyway V1-V31 migration.
   - Do not let it run for `postgres-flyway`, repository smoke, or `nq-app` context smoke.
   - Require a named guard such as a future `NQ_CI_ENABLE_LEGACY_ACCOUNT_FIXTURE=true`, scoped to the backend Maven job only.
   - Fail closed if the expected test stage does not need it.

4. `2E-4`: Freeze after first green run review.
   - Required evidence:
     - backend Maven job success.
     - `postgres-flyway` job still success.
     - no legacy watcher path in `postgres-flyway`.
     - no seed users / exchange account credentials / real credentials.
     - no Batch 3-5 implementation.

### Deletion criteria

Delete the watcher when all are true:

- `backend` job passes without the background polling process.
- Local-profile tests either do not require a legacy account row, or create their own explicit fake fixture.
- Batch 2A empty DB Flyway smoke remains seedless.
- Batch 2C repository smoke remains repository-only and rollback-safe.
- Batch 2D context smoke remains `ci-app-smoke`, no auth seed, no legacy account seed, no exchange account seed, no credential rows.

### Replacement criteria

Use explicit fixture SQL only if all are true:

- A named test still requires at least one legacy `accounts` row.
- The failing test is identified and cannot be converted to test-owned setup within the same implementation slice.
- The fixture runs after migration completion, not while migrations are in progress.
- The fixture is scoped to one job / one stage and cannot silently feed 2A/2B/2C/2D baselines.

### Independent CI profile decision

- 2E does not require a new independent Spring profile if the watcher can be deleted or replaced by test-owned fixture setup.
- Do not reuse `local` or current `test` as a broad CI fixture profile for 2E cleanup, because both are tied to auth seed behavior and can blur test/runtime boundaries.
- If implementation proves a profile-level fixture switch is unavoidable, add it only in a separate reviewed implementation slice and keep it CI-only, explicit, fake-data-only, and disabled by default.
- Existing `ci-app-smoke` remains owned by Batch 2D context startup and must not be expanded into a general backend Maven seed profile without review.

### Rollback

- If deletion causes backend Maven CI failure, restore only the previous watcher block or the explicit fixture step from the immediately prior commit.
- Do not modify migrations or production seed code as rollback.
- Do not add `baselineOnMigrate`, Flyway `clean`, `skipTests`, `continue-on-error`, or profile changes as rollback.

## Batch 2E implementation

- Implementation decision: delete the background watcher and do not add fallback SQL.
- Fallback decision: not needed in this implementation slice because `mvn -f backend/pom.xml test` passed locally after watcher deletion.
- First CI status: failed. GitHub Actions run `27610448572` failed in the `Backend Maven test` job / `Run backend tests` step after watcher deletion.
- If first CI run fails due to a missing legacy account fixture, the next task must capture the exact failing test, SQL / stack trace, and root cause before adding any explicit migration-after fixture SQL.
- The fallback remains constrained to backend Maven test only; it does not affect `postgres-flyway`, 2A / 2B / 2C / 2D, migrations, production seed code, auth users, credential rows, LIVE, AI, DH runtime, RealClient, or real providers.

## AuthSeed / bootstrap admin boundary

- `AuthSeedConfiguration` is `@Profile({"local", "test"})` and seeds users from `nq.security.users` through `AuthSeedService.seedUsers(...)`.
- Batch 2D avoids it by using `@ActiveProfiles("ci-app-smoke")`; this must remain unchanged in 2E.
- `AuthBootstrapAdminConfiguration` is controlled by `nq.auth.bootstrap-admin.enabled=true`.
- Batch 2D explicitly sets `nq.auth.bootstrap-admin.enabled=false`; 2E must not enable it.
- Batch 2E implementation must not create admin / operator / viewer seed users as a side effect.
- If future backend full Maven tests keep `@ActiveProfiles("local")`, the cleanup must not broaden local/test auth seed behavior; it must isolate only the legacy account fixture need.

## Legacy accounts / exchange accounts / credential boundary

| Area | Current evidence | 2E boundary |
| --- | --- | --- |
| legacy `accounts` | Removed watcher no longer inserts `ci-local-account` into `accounts`; first-run fix inserts `ci-backend-test-account` synchronously after Flyway V31 validation. | Cleanup + explicit fixture applied. Fixture is backend-job only and exists to satisfy `ResearchBacktestHappyPathLocalTest` legacy account dependency. |
| `exchange_accounts` | V12 can backfill rows from legacy `accounts`, but the first-run fix inserts the fixture after V31, so it cannot be backfilled by V12. | Fixture step fails closed if an `exchange_accounts` row exists for the fixture. |
| `exchange_account_credentials` | V12 creates table and constraints; first-run fix does not insert credential rows. | Fixture step fails closed if any credential row exists before backend Maven test. |
| auth users | Local/test profile can seed users through `AuthSeedConfiguration`. | Do not trigger or broaden auth seed. |
| bootstrap admin | Conditional runner exists behind `nq.auth.bootstrap-admin.enabled=true`. | Keep disabled. |

## Impact assessment

- Backend Maven test: local `mvn -f backend/pom.xml test` passed after watcher deletion; GitHub Actions run `27610448572` then exposed the missing legacy `accounts` fixture; first-run fix now prepares one synchronized legacy `accounts` fixture after Flyway V31 and before backend Maven test. Follow-up run `27614046762` confirmed backend Maven test success on the GitHub runner.
- `postgres-flyway` job: first-run review confirmed success in run `27610448572`; follow-up run `27614046762` confirmed the job remains success after the backend fixture fix. 2A/2B/2C/2D paths remained green.
- Batch 2A: dependency reduced to zero because empty DB Flyway smoke is no-seed and does not start `nq-app`.
- Batch 2B: dependency reduced to zero because artifacts are generated from the seedless Flyway-migrated DB.
- Batch 2C: dependency reduced to zero because repository smoke uses explicit fake data inside a rollback-only transaction and does not touch legacy `accounts`.
- Batch 2D: dependency reduced to zero because `ci-app-smoke` context uses no local/test auth seed and intentionally creates no legacy/exchange/credential rows.
- Batch 3 no-outbound: not strictly required before 2E because 2E touches only seed/fixture cleanup planning; however 2E implementation must not claim no-outbound coverage. If implementation changes app context behavior, stop and route to Batch 3 pre-planning first.
- Security: deleting the watcher decreases the risk of hidden fixture state and migration-race behavior.

## Security boundary

- No LIVE, no AI, no DH runtime, no RealClient, no real provider.
- No OKX / Binance / Bybit / Gate / Coinbase / Kraken private or public calls.
- No API key, secret, passphrase, private key, token, cookie, mnemonic, credential material, raw request, raw response, signature string, or production DB credential.
- CI-only PostgreSQL service values are disposable fake values and must remain non-production.
- 2E implementation must not use real GitHub secrets.
- 2E implementation must not upload artifacts containing data rows if a fixture is introduced.

## Batch boundary

- Batch 2E is FROZEN / ACCEPTED in this document.
- Batch 3 no-outbound guard remains PENDING.
- Batch 4 security guard / secret scan remains PENDING.
- Batch 5 frontend E2E hardening remains PENDING.
- Batch 2E freeze accepts only the CI seed watcher cleanup baseline and explicit post-Flyway fixture; it does not start Batch 3-5.

## P0/P1/P2/P3 findings

### P0

- 无。

### P1

- 无。Historical P1 from run `27610448572` was closed by the scoped first-run fix and GitHub Actions run `27614046762` first green review.

### P2

- 无。Historical watcher-race and missing legacy fixture risks were closed by removing the background watcher, adding the explicit post-Flyway CI-only fixture, and confirming run `27614046762` green before this freeze.

### P3

- 无。

## Validation

Executed in the implementation turn:

- `Get-Location`
- `git status --short`
- `git branch --show-current`
- `mvn -f backend/pom.xml test`：BUILD SUCCESS；reactor 23/23 modules SUCCESS；total time 02:22；local run used local PostgreSQL 17.7 for local-profile Spring tests and skipped CI-only `NqAppContextPostgresSmokeTest` as expected without `nq.app.context.smoke.required=true`。

GitHub Actions first run:

- `gh run view 27610448572 --json ...`：completed / failure.
- `gh run view --job 81633181802`：`Backend Maven test` failed in `Run backend tests`; annotation only reports exit code 1.
- `gh run view --job 81633181744`：`PostgreSQL / Flyway smoke` success, including 2A / 2B / 2C / 2D steps.
- `gh run view --log-failed` / `gh run view --job 81633181802 --log-failed`：failed with HTTP 403, so backend Maven failure logs were not readable in this review.

GitHub Actions first green run after fix:

- GitHub Actions MCP run jobs for run `27614046762`：completed / success; jobs `Diff check`、`Backend Maven test`、`Frontend build`、`Research quality gate`、`PostgreSQL / Flyway smoke` all completed / success.
- GitHub Actions MCP backend job logs for job `81645397239`：`Prepare backend CI legacy account fixture` and `Run backend tests` success; `ResearchBacktestHappyPathLocalTest` tests=1 / failures=0 / errors=0 / skipped=0; backend reactor 23/23 modules SUCCESS; `nq-app` SUCCESS; Maven `BUILD SUCCESS`.
- GitHub Actions MCP `postgres-flyway` job logs for job `81645397302`：empty DB Flyway smoke, schema artifacts, repository PostgreSQL smoke, and `nq-app` context smoke success; `NqAppContextPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0; Maven `BUILD SUCCESS`.
- `gh run view 27614046762 --json ...`：completed / success; later `gh run view --log` attempts hit GitHub unauthenticated rate limiting, so detailed log review used GitHub MCP decoded logs.

Freeze review validation:

- `git status --short`
- `git diff --check`
- `git diff --stat`
- `git show --stat --oneline --name-only HEAD`
- `git diff -- .github`
- `git diff -- backend`
- `git diff -- frontend`
- `git diff -- research`
- `git diff -- scripts`
- `git diff -- deploy`
- `git diff -- backend/**/db/migration`
- GitHub Actions MCP run jobs / backend job logs / `postgres-flyway` job logs for run `27614046762`.
- `rg "ci-local-account|background seed|INSERT INTO accounts|seed_pid|to_regclass|exchange_account_credentials|skipTests|continue-on-error|baselineOnMigrate|flyway clean|apiKey|secret|passphrase|token|private key|credential material|LIVE|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github docs/current` for workflow and docs boundary review.

Reason:

- Local Maven validates the backend test baseline, but it was not a perfect GitHub runner reproduction because CI uses GitHub Actions `postgres:16` service while the local run used PostgreSQL 17.7 on `localhost:5432`.

## Boundary confirmation

- `.github/workflows/ci.yml` modified only to keep the backend job background seed watcher removed and add the synchronous backend CI legacy account fixture.
- No backend production code / frontend / research / scripts / deploy / migration files modified in the first-run fix; this review only updates allowed `docs/current` records.
- No tests added or changed by the 2E first-run fix.
- No seed users, exchange accounts, credential rows, or credential material created; the only new fixture is one backend-job-only legacy `accounts` row inserted after Flyway V31 in disposable CI DB.
- No real exchange, LIVE, AI, DH runtime, RealClient, real provider, or permission probe adapter started.
- Batch 2E is FROZEN / ACCEPTED.
- Batch 3-5 remain PENDING.

## Review decision

PASS / FROZEN / ACCEPTED.

P0 = 0. P1 = 0. The historical first-run failure in `Backend Maven test` / `Run backend tests` was closed by the scoped post-Flyway CI-only fixture and confirmed green by GitHub Actions run `27614046762`. Batch 2E seed watcher cleanup is frozen as the current `dev` CI baseline.

## Next concrete action

Next concrete action must be one of:

- Batch 3 pre-planning
- Batch 4 / Batch 5 later planning
- Pause the CI line

Do not combine 2E freeze review with Batch 3 no-outbound implementation, Batch 4 security guard implementation, Batch 5 frontend E2E hardening, AI, DH runtime, LIVE, RealClient, real provider, real exchange adapter, or credential material work.
