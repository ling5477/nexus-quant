# NQ CI PostgreSQL / Flyway 2C Plan

任务：NQ-CI-POSTGRES-FLYWAY-2C-PLAN
日期：2026-06-15
状态：PLAN FROZEN / ACCEPTED / IMPLEMENTATION NOT STARTED

## Current state

- Project: NexusQuant / NQ。
- Current branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted。
- NQ CI Batch 1：FROZEN / ACCEPTED。
- NQ CI Batch 2A PostgreSQL / Flyway empty DB migration smoke：FROZEN / ACCEPTED。
- NQ CI Batch 2B schema artifact baseline：FROZEN / ACCEPTED。
- Batch 2C repository real PostgreSQL smoke：PLAN FROZEN / ACCEPTED / IMPLEMENTATION NOT STARTED。
- Batch 2D `nq-app` context smoke：NOT STARTED。
- Batch 2E CI-only seed watcher cleanup：NOT STARTED。
- Batch 3 no-outbound guard：PENDING。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI：NOT STARTED。
- DH runtime：NOT INTEGRATED / not connected to NQ。
- LIVE：DISABLED。
- real exchange adapter / real provider / RealClient：NOT IMPLEMENTED。

## Scope

This document only plans Batch 2C. It does not implement workflow, Java tests, repository tests, migrations, seed scripts, no-outbound guard, secret scan, frontend E2E hardening, AI, DH runtime, LIVE, RealClient or real provider work.

Allowed implementation scope after review:

- Add a minimal repository-only PostgreSQL smoke path in CI.
- Prefer `nq-infra` pure JDBC repository smoke.
- Use GitHub Actions PostgreSQL service container with CI-only values.
- Reuse Flyway migrations V1-V31 to prepare a disposable schema.
- Run only explicitly selected repository smoke tests or a reviewed test-only smoke runner.
- Keep all fixture data fake, local to the disposable CI database, and cleaned by rollback or truncation.

Forbidden implementation scope:

- Do not start `nq-app` full Spring context.
- Do not use `@SpringBootTest` tests for 2C.
- Do not activate `local` / `test` profiles just to get repository smoke.
- Do not trigger `AuthSeedConfiguration` or any `ApplicationRunner`.
- Do not use Batch 1 CI-only seed watcher.
- Do not insert legacy account seed by default.
- Do not insert real account seed, real exchange account seed or real credential material.
- Do not call OKX / Binance / Bybit / Gate / Coinbase / Kraken.
- Do not enable LIVE.
- Do not introduce Testcontainers in 2C-1.
- Do not change production code, API, migration, frontend, research, scripts or deploy.

## Current CI state

Current `.github/workflows/ci.yml` has these relevant jobs:

| Job | Current behavior | 2C interpretation |
| --- | --- | --- |
| `backend` | Runs `mvn -f backend/pom.xml test` with PostgreSQL service `postgres:16`; uses a CI-only watcher to insert one `accounts` row after Flyway creates the table. | This is Batch 1 compatibility for existing local-profile context tests. It is not repository real DB hardening and must not be reused as 2C seed policy. |
| `postgres-flyway` | Runs direct Flyway API empty DB migrate + validate to V31 and uploads schema metadata artifacts. | 2A/2B baseline. It does not start app context, does not seed, and does not run repository smoke. |

Batch 2C should be isolated from both:

- It should not pollute `postgres-flyway` schema artifacts.
- It should be removable without touching 2A/2B.
- It should use its own CI job or own disposable database if implemented inside an existing reviewed job.

Recommended future job name:

- Internal id: `postgres-repository-smoke`
- UI name: `PostgreSQL / Repository smoke`

## Repository test inventory

Source-only inspection found the current repository test baseline is mostly SQL-shape tests, not real PostgreSQL execution.

### Recording / mock `JdbcTemplate` tests

These tests use `RecordingJdbcTemplate`, `RecordingNamedParameterJdbcTemplate` or `Mockito.mock(JdbcTemplate)`. They validate SQL text, parameters and mapper branches, but do not prove PostgreSQL execution, constraints, JSONB casts, `ON CONFLICT`, transaction behavior or TIMESTAMPTZ handling.

Representative files:

- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/audit/infra/jdbc/JdbcAuditLogRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/risk/infra/jdbc/JdbcRiskEventRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/eventstore/infra/EventStoreAppenderTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataBarRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyDefinitionRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunQueryRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyScheduleRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerRiskAuditRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/scheduler/infra/jdbc/JdbcTradeRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/scheduler/infra/jdbc/JdbcLedgerReconcileRepositoryTest.java`

Decision:

- Do not convert all Recording tests in one batch.
- Keep Recording tests as fast unit tests.
- Add separate small real PostgreSQL smoke tests for selected repository behavior.

### Current real DB / Spring context tests

Current `nq-app` tests that already touch real PostgreSQL do so through Spring context and local profile:

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/MarketdataControllerLocalIntegrationTest.java`
  - Uses `@SpringBootTest(classes = NexusQuantApplication.class)` and `@ActiveProfiles("local")`.
  - Uses `MockMvc` and `JdbcTemplate`.
  - Writes fixture marketdata and queries the DB through HTTP controller paths.
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/ResearchBacktestHappyPathLocalTest.java`
  - Uses `@SpringBootTest(classes = NexusQuantApplication.class)` and `@ActiveProfiles("local")`.
  - Requires `accounts` to contain at least one legacy account.
  - Exercises marketdata, research, backtest and evaluation through full app wiring.
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/OkxBootstrapNoOutboundLocalContextTest.java`
  - Uses `@SpringBootTest(classes = NexusQuantApplication.class)` and `@ActiveProfiles("local")`.
  - Adds a `ProxySelector` probe to fail if OKX public instruments outbound is attempted.

Decision:

- These are not Batch 2C candidates.
- They belong to Batch 2D because they start `nq-app` context and local profile.
- They can trigger local/test profile beans, including `AuthSeedConfiguration`.
- They are useful evidence for future 2D planning, not for repository-only 2C.

### Web MVC / in-memory / adapter tests

- `@WebMvcTest` controller tests do not prove repository SQL execution.
- Core service tests use in-memory repositories and should remain unit tests.
- Adapter tests use fake servers, local probes or runtime config stubs. They are not repository DB tests.
- `BinanceWsClientLiveDiagnosticTest` is manual diagnostic style and must never enter default CI repository smoke.

Decision:

- Exclude from 2C.
- Do not use adapter tests to prove DB behavior.
- Do not mix no-outbound guard into 2C; Batch 3 owns that.

## Batch 2C candidate scope

### 2C-1 minimal repository smoke

Recommended first slice:

- Create one dedicated repository PostgreSQL smoke test class in `nq-infra` test scope, after separate implementation review.
- Use a direct `DataSource` / `JdbcTemplate` against a disposable CI PostgreSQL database.
- Run Flyway V1-V31 first, using the same `classpath:db/migration` baseline as 2A.
- Do not start Spring Boot.
- Do not use `NQ_PROFILE=local` or `NQ_PROFILE=test`.
- Do not trigger `AuthSeedConfiguration`.

Preferred 2C-1 repository behaviors:

| Candidate | Why safe enough for 2C-1 | Fixture need | Cleanup |
| --- | --- | --- | --- |
| `JdbcAuditLogRepository` | Simple insert into `audit_logs`; validates JSON serialization and table compatibility. | Fake actor / trace / detail only. | Transaction rollback or delete by unique trace. |
| `JdbcRiskEventRepository` | Simple insert into `risk_events`; validates status/severity values and table compatibility. | Fake order id / trace only. | Transaction rollback or delete by trace / biz id. |
| `EventStoreAppender` | Validates event store insert and JSON payload path without app context. | Fake event id / event payload only. | Transaction rollback or delete by event id. |
| `JdbcMarketdataBarRepository` | Covers PostgreSQL-specific `ON CONFLICT`, quoted `"interval"`, numeric and timestamp behavior. | Fake `BINANCE` / `BTCUSDT` / `SPOT` bars only; no exchange call. | Transaction rollback or delete by exchange/symbol/time suffix. |

2C-1 should start with two to four focused assertions, not a broad integration suite.

### 2C-2 expanded repository smoke

Allowed only after 2C-1 is stable:

- Add strategy repository smoke if FK setup is explicit and fake:
  - `strategy_definitions`
  - `strategy_runs`
  - `strategy_run_events`
  - `strategy_schedules`
- Add paper/backtest repository smoke only when required fixture graph is documented:
  - `backtest_runs`
  - `sim_orders`
  - `paper_trading_runs`
  - `paper_trading_orders`
  - `paper_trading_positions`
- Add account / credential repository smoke only with fake credential material and redaction review:
  - `exchange_accounts`
  - `exchange_account_credentials`
  - `credential_audit_logs`

Deferred by default:

- `JdbcExchangeAccountCredentialRepository` because it touches credential material shape, `pgp_sym_encrypt` / `pgp_sym_decrypt`, lifecycle state and permission probe fields. It may be valuable later, but it requires stricter fake material, log redaction and artifact policy.
- Any repository that requires live exchange status, provider calls or external API semantics.

### 2C-3 required-check evaluation

After at least one green run and review:

- Decide whether `PostgreSQL / Repository smoke` becomes required for:
  - backend changes touching `nq-infra` JDBC repositories,
  - migration changes,
  - `docs/current/DB_SCHEMA.md` changes,
  - CI DB configuration changes.
- Keep docs-only changes eligible for diff/doc validation only, as long as no backend / migration / workflow files change.

## Seed / fixture boundary

Default policy:

- 2C does not use legacy account seed.
- 2C does not use Batch 1 CI-only seed watcher.
- 2C does not use `AuthSeedConfiguration`.
- 2C does not read or require real credential material.

Allowed fixture data:

- Fake values created inside the smoke test or runner.
- Obvious non-production identifiers such as `ci-repo-smoke-*`.
- `PAPER` / `SIM` only when the target table requires environment fields.
- Fake exchange codes only as stored enum-like values, not as adapter invocations.
- Fake credential strings only if a later 2C-2 credential smoke is accepted.

Forbidden fixture data:

- Real OKX / Binance / Bybit / Gate / Coinbase / Kraken credentials.
- Real account ids, tenant data, API keys, API secrets, passphrases, tokens, cookies, private keys or mnemonic values.
- LIVE markers or LIVE credential fixtures.
- Data loaded from `.env`, local secrets, GitHub secrets or user-specific files.

If a repository requires an `accounts` row through FK:

- Insert a local fake `accounts` row inside the same test transaction.
- Use a clearly fake `account_code` such as `ci-repo-smoke-account`.
- Roll it back or delete it in `finally`.
- Do not call this legacy seed and do not promote it to runtime or migration seed.

## Transaction / cleanup strategy

Preferred strategy:

- Each repository smoke runs inside a transaction and rolls back after assertions.
- Use `DataSourceTransactionManager` / `TransactionTemplate` or an equivalent test helper if implemented in JUnit.
- Use unique test ids even when rollback is expected, so cleanup remains safe if a test aborts after partial writes.

Fallback strategy:

- If a repository operation requires commit semantics, use `try/finally` cleanup.
- Delete by unique test ids.
- Avoid broad table truncation unless table dependency order is explicitly reviewed.

CI database strategy:

- Use a disposable PostgreSQL service database per job.
- Prefer a separate 2C job or separate database name from 2A/2B, for example `nq_ci_repo`.
- Run Flyway migrations before repository smoke.
- Do not upload data dumps.
- Do not pollute 2B schema artifacts with smoke rows.

Never do:

- Do not run Flyway `clean`.
- Do not set `baselineOnMigrate(true)`.
- Do not modify migration history.
- Do not retain DB data rows as artifacts.

## Security boundary

2C must preserve these boundaries:

- No OKX / Binance / Bybit / Gate / Coinbase / Kraken network access.
- No real exchange private API.
- No public exchange bootstrap as part of repository smoke.
- No LIVE.
- No AI.
- No DH runtime.
- No RealClient.
- No real provider.
- No production DB credential.
- No GitHub secret requirement.
- No `.env` read.
- No raw credential payload in logs or artifacts.

2C does not implement Batch 3 no-outbound guard. Until Batch 3 exists, 2C should reduce outbound risk by construction:

- stay in `nq-infra` repository scope,
- avoid `nq-app` context,
- avoid adapter modules,
- avoid profiles that instantiate adapter runtime beans,
- avoid credential material paths in 2C-1.

## Batch boundary

| Batch | Boundary |
| --- | --- |
| 2A | Empty DB Flyway V1-V31 migration smoke. Already FROZEN / ACCEPTED. |
| 2B | Schema artifact baseline. Already FROZEN / ACCEPTED. |
| 2C | Repository-only real PostgreSQL smoke. This document is planning-only; implementation NOT STARTED. |
| 2D | `nq-app` context smoke. NOT STARTED. Owns `@SpringBootTest`, local/test profile and `AuthSeedConfiguration` evaluation. |
| 2E | CI-only seed watcher cleanup. NOT STARTED. |
| 3 | no-outbound guard. PENDING and not implemented by 2C. |
| 4 | security guard / secret scan. PENDING and not implemented by 2C. |
| 5 | frontend E2E hardening. PENDING and not implemented by 2C. |

## P0/P1/P2/P3 findings

| Priority | Finding | Decision |
| --- | --- | --- |
| P0 | Running `@SpringBootTest` or `nq-app` full context in 2C can trigger local/test seed and adapter beans. | Exclude from 2C; reserve for 2D. |
| P0 | Any real exchange credential, LIVE flag, RealClient, real provider or exchange network call in 2C violates GateK boundary. | Forbidden. |
| P0 | Repository smoke data rows uploaded as artifacts could leak fixture or future credential material. | Do not upload data dumps; artifacts remain schema-only in 2B. |
| P1 | Current repository tests are mostly Recording / mock tests, so they do not prove PostgreSQL dialect behavior. | Add separate minimal real PostgreSQL smoke rather than mutating all unit tests. |
| P1 | Batch 1 CI-only seed watcher can hide empty DB and fixture requirements if reused. | Do not reuse watcher; 2C fixtures must be explicit and rollback-safe. |
| P1 | Credential repository real DB smoke can touch encrypted payload shape and fake material; careless logs could create secret-like output. | Defer credential repository to 2C-2 with fake-only material and redaction review. |
| P2 | `postgres:16` CI differs from local compose `postgres:17.7`. | Keep 2A/2B baseline for now; record compatibility risk, do not change version in 2C planning. |
| P2 | Broad repository expansion may make CI slow or flaky. | Slice 2C-1 / 2C-2 / 2C-3 and require first green review before required-check promotion. |
| P3 | Existing broad `rg backend docs/current` scans can include `backend/**/target/**` noise in a dirty build tree. | Use source-only follow-up scans for evidence; keep final review evidence tied to source and current docs. |

## Proposed implementation slicing

### 2C-1: minimal repository smoke

Status: NOT STARTED.

Implementation outline for future review:

1. Add a separate `postgres-repository-smoke` job or reviewed 2C path.
2. Start PostgreSQL service with CI-only database, user and password.
3. Prepare Flyway runtime classpath.
4. Run Flyway `migrate` + `validate` against disposable DB.
5. Run a minimal `nq-infra` repository smoke class or runner.
6. Cover only selected low-risk repositories:
   - audit log,
   - risk event,
   - event store,
   - marketdata bars.
7. Use transaction rollback or explicit cleanup.
8. Do not upload data rows.

Suggested command shape for implementation review only:

```bash
mvn -f backend/pom.xml -pl nq-infra -am test -Dtest=JdbcRepositoryPostgresSmokeTest
```

The exact test class does not exist yet and must be introduced only in a later implementation task.

### 2C-2: expanded repository coverage

Status: NOT STARTED.

- Add strategy / paper / ledger / account repository smoke only after fixture graphs are documented.
- Use fake fixtures only.
- Keep each repository group independently disableable or removable.
- Re-evaluate credential repository separately before adding it.

### 2C-3: required check decision

Status: NOT STARTED.

- Review first green evidence and flake rate.
- Decide whether to make the job required for backend/migration/schema docs changes.
- Keep rollback path limited to removing the 2C job or disabling only the 2C smoke invocation.

## Rollback plan

If Batch 2C repository smoke is flaky or too broad after implementation:

- Revert only the 2C workflow job or 2C smoke invocation.
- Keep `postgres-flyway` 2A/2B unchanged.
- Keep Flyway empty DB smoke and schema artifacts required.
- Keep repository smoke plan documented as pending fix.
- Do not modify migrations or production code to make 2C pass.
- Do not replace repository smoke failure with `continue-on-error` if the job is already required; instead demote or revert the 2C job through review.

## Validation commands

Planning/doc validation for this task:

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "Repository|Jdbc|RecordingJdbcTemplate|SpringBootTest|ActiveProfiles|Testcontainers|PostgreSQL|Flyway|seed|AuthSeedConfiguration" backend docs/current
rg "LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|OKX|Binance|Bybit|Gate|Coinbase|Kraken" backend .github docs/current
```

Notes:

- On PowerShell, path globs like `backend/**/src/test` can be invalid as direct path arguments. Use `rg --glob` filters with `backend` as the root path for source-only follow-up scans.
- Broad required scans can include `backend/**/target/**` if build outputs are present. Source-only follow-up scans must exclude `**/target/**` before drawing conclusions.

No Maven / npm / Python test is required for this planning-only task because it only updates `docs/current` documents and does not modify workflow, Java / TypeScript / Python code, tests, migration, frontend, research, scripts or deploy.

## Freeze review decision

Review decision: PASS / FROZEN / ACCEPTED.

This document is accepted as the GateK CI Batch 2C implementation baseline. It authorizes only a later reviewed Batch 2C implementation consistent with this plan: repository-only real PostgreSQL smoke, no `nq-app` full context, no legacy seed, no real credential material, no Testcontainers in 2C-1, no real exchange access, no LIVE, no AI, no DH runtime, no RealClient and no real provider.

Batch 2C implementation remains NOT STARTED. Batch 2D and Batch 2E remain NOT STARTED. Batch 3-5 remain PENDING. AI remains NOT STARTED. DH runtime remains NOT INTEGRATED. LIVE remains DISABLED. Real exchange adapter / real provider / RealClient remain NOT IMPLEMENTED.

## Next concrete action

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2C-IMPL`, `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-FIX`, or separate 2D / 2E / Batch 3 pre-planning.

Do not proceed directly to 2D app context smoke, 2E seed watcher cleanup, Batch 3 no-outbound guard, Batch 4 secret scan, Batch 5 frontend E2E hardening, AI, DH runtime, LIVE, RealClient, real provider or real exchange adapter from this review-only task.
