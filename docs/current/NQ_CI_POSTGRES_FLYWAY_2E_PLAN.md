# NQ CI PostgreSQL / Flyway Batch 2E Plan

任务：NQ-CI-POSTGRES-FLYWAY-2E-PLAN / NQ-CI-POSTGRES-FLYWAY-2E-IMPL
日期：2026-06-16
状态：IMPLEMENTED / PENDING FIRST CI RUN

## Task classification

- Primary type: `CI_CD`
- Auxiliary types: `DOCUMENTATION`, `SECURITY_BOUNDARY_GUARD`, `TEST_BASELINE_FIX`
- 主 skill：`nq-dh-workflow-router`，用于确认 NQ / GateK / CI / credential / LIVE / DH 边界。
- 本轮实现 Batch 2E 最小 cleanup：只修改 backend CI job 的 `Run backend tests` step 和 `docs/current` 状态记录；不修改 Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。

## Scope

- Repository: NexusQuant / NQ。
- Branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- Batch state: 2A / 2B / 2C / 2C-HYGIENE-FIX / 2D 均为 FROZEN / ACCEPTED；Batch 2E 已实现 watcher cleanup，当前为 IMPLEMENTED / PENDING FIRST CI RUN；Batch 3-5 仍 PENDING。
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

- `.github/workflows/ci.yml`：确认 `backend` job 仍包含 CI-only seed watcher；`postgres-flyway` job 不使用该 watcher。
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
| `backend` | Runs `mvn -f backend/pom.xml test` with PostgreSQL service `postgres:16`; the CI-only background seed watcher has been removed. | Primary 2E target. The backend job no longer inserts `ci-local-account` into legacy `accounts`. |
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
- First CI status: pending. Local Maven passed, but GitHub runner behavior must still be reviewed through `NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW`.
- If first CI run fails due to a missing legacy account fixture, the next task must capture the exact failing test, SQL / stack trace, and root cause before adding any explicit migration-after fixture SQL.
- The fallback remains constrained to backend Maven test only; it must not affect `postgres-flyway`, 2A / 2B / 2C / 2D, migrations, production seed code, auth users, credential rows, LIVE, AI, DH runtime, RealClient, or real providers.

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
| legacy `accounts` | Removed watcher no longer inserts `ci-local-account` into `accounts`. | Cleanup implemented. If CI fails, use only explicit backend-test fixture review path. |
| `exchange_accounts` | V12 can backfill rows from legacy `accounts`, but the removed watcher no longer creates a race seed. | Avoid migration-race seed. If a future fixture is required, run it after migration or make the indirect row intentional and documented. |
| `exchange_account_credentials` | V12 creates table and constraints; no inspected watcher path inserts credential rows. | 2E must not create credential rows or material. |
| auth users | Local/test profile can seed users through `AuthSeedConfiguration`. | Do not trigger or broaden auth seed. |
| bootstrap admin | Conditional runner exists behind `nq.auth.bootstrap-admin.enabled=true`. | Keep disabled. |

## Impact assessment

- Backend Maven test: local `mvn -f backend/pom.xml test` passed after watcher deletion. GitHub runner first run is still required before freeze.
- `postgres-flyway` job: should not be affected; 2A/2B/2C/2D already avoid the watcher.
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

- Batch 2E is IMPLEMENTED / PENDING FIRST CI RUN in this document.
- Batch 3 no-outbound guard remains PENDING.
- Batch 4 security guard / secret scan remains PENDING.
- Batch 5 frontend E2E hardening remains PENDING.
- Batch 2E must not be marked FROZEN / ACCEPTED until first-run review confirms the backend and `postgres-flyway` jobs remain green with P0/P1=0.

## P0/P1/P2/P3 findings

### P0

- 无。

### P1

- 无。

### P2

- Current backend seed watcher is a long-lived ad hoc CI compatibility layer that can hide missing explicit fixtures in local-profile Spring tests.
- Watcher can race with Flyway: direct `accounts` insert after V1 may be indirectly backfilled into `exchange_accounts` by V12, making account state depend on timing rather than explicit test ownership.
- Backend Maven test may fail when watcher is removed; 2E implementation needs first-run review / minimal fix loop.

### P3

- Current watcher name and placement do not make the migration-race / V12 backfill implication obvious to reviewers.
- Existing docs mention watcher as compatibility seed but do not yet provide a concrete deletion / fallback decision tree; this plan closes that gap.

## Validation

Executed in the implementation turn:

- `Get-Location`
- `git status --short`
- `git branch --show-current`
- `mvn -f backend/pom.xml test`：BUILD SUCCESS；reactor 23/23 modules SUCCESS；total time 02:22；local run used local PostgreSQL 17.7 for local-profile Spring tests and skipped CI-only `NqAppContextPostgresSmokeTest` as expected without `nq.app.context.smoke.required=true`。

Pending:

- GitHub Actions first run after watcher deletion.

Reason:

- Local Maven validates the backend test baseline, but it is not a perfect GitHub runner reproduction because CI uses GitHub Actions `postgres:16` service while the local run used PostgreSQL 17.7 on `localhost:5432`.

## Boundary confirmation

- `.github/workflows/ci.yml` modified only to remove the backend job background seed watcher.
- No backend / frontend / research / scripts / deploy / migration files modified.
- No tests added or changed.
- No seed users, legacy accounts, exchange accounts, credential rows, or credential material created.
- No real exchange, LIVE, AI, DH runtime, RealClient, real provider, or permission probe adapter started.
- Batch 2E is IMPLEMENTED / PENDING FIRST CI RUN.
- Batch 3-5 remain PENDING.

## Review decision

PASS / IMPLEMENTED / PENDING FIRST CI RUN.

P0/P1 = 0. The background seed watcher has been removed and no fallback SQL was added because local backend Maven test passed.

## Next concrete action

Next concrete action must be one of:

- `NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW`
- `NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-FIX`

Do not combine 2E first-run review / fix with Batch 3 no-outbound, Batch 4 security guard, Batch 5 frontend E2E, AI, DH runtime, LIVE, RealClient, real provider, real exchange adapter, or credential material work.
