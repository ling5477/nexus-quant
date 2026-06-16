# NQ CI PostgreSQL / Flyway 2D Plan

任务：NQ-CI-POSTGRES-FLYWAY-2D-PLAN / NQ-CI-POSTGRES-FLYWAY-2D-IMPL / NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX
日期：2026-06-15 / 2026-06-16
状态：IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN

## Current state

- Project: NexusQuant / NQ。
- Current branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted。
- NQ CI Batch 1：FROZEN / ACCEPTED。
- Batch 2A PostgreSQL / Flyway empty DB smoke：FROZEN / ACCEPTED。
- Batch 2B schema artifact baseline：FROZEN / ACCEPTED。
- Batch 2C repository-only real PostgreSQL smoke baseline：FROZEN / ACCEPTED。
- 2C-HYGIENE-FIX：FROZEN / ACCEPTED。
- Batch 2D `nq-app` context smoke：IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN。
- Batch 2E CI-only seed watcher cleanup：NOT STARTED。
- Batch 3 no-outbound guard：PENDING。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI：NOT STARTED。
- DH runtime：NOT INTEGRATED / not connected to NQ。
- LIVE：DISABLED。
- real exchange adapter / real provider / RealClient：NOT IMPLEMENTED。

## Scope

This document records the accepted plan and the minimal implementation for the `nq-app` Spring context smoke. Batch 2D now adds one test-only Spring context smoke and one CI step in the existing `postgres-flyway` job. It does not modify backend production code, frontend, research, scripts, deploy, API, migration, historical migration, seed policy, no-outbound guard, security scan, frontend E2E hardening, AI, DH runtime, LIVE, RealClient, real provider, or real exchange adapter.

Implemented Batch 2D shape:

- Added one explicitly selected `nq-app` context smoke test: `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java`.
- Reused the existing GitHub Actions PostgreSQL service inside the same `postgres-flyway` job; no cross-job DB sharing is assumed.
- Reused the schema already migrated by the direct Flyway step before context startup.
- Used CI-only fake profile name `ci-app-smoke` plus explicit test properties; `local` and current `test` profiles are not used.
- Kept LIVE disabled, bootstrap admin disabled, catalog sync disabled, OKX recovery disabled, OKX WS disabled, Binance WS disabled, scheduler side effects disabled by property, and real adapter / WS constructors replaced by test mocks.
- Set `spring.flyway.enabled=false` in the context smoke because the same job already ran Flyway migrate / validate before this step; this prevents a second context-owned migration and keeps 2D focused on Spring wiring against the migrated schema.

Forbidden for Batch 2D:

- Do not use `local` profile.
- Do not reuse real developer-machine config.
- Do not reuse Batch 1 CI-only seed watcher as the Batch 2D fixture strategy.
- Do not start frontend E2E.
- Do not implement Batch 2E seed watcher cleanup.
- Do not implement Batch 3 no-outbound guard.
- Do not implement Batch 4 secret scan.
- Do not implement Batch 5 frontend E2E hardening.
- Do not enable LIVE, AI, DH runtime, RealClient, real provider, real exchange adapter, real exchange permission probe, or any real exchange call.

## Current CI state

Current `.github/workflows/ci.yml` has these relevant jobs:

| Job | Current behavior | 2D interpretation |
| --- | --- | --- |
| `backend` | Runs `mvn -f backend/pom.xml test` with PostgreSQL service `postgres:16`; uses a CI-only watcher to insert one `accounts` row after Flyway creates the table. | Batch 1 compatibility path only. It currently satisfies existing local-profile full context tests but must not become the 2D seed or profile policy. |
| `postgres-flyway` | Runs direct Flyway API empty DB migrate + validate to V31, uploads schema metadata artifacts, runs the Batch 2C repository-only PostgreSQL smoke, then runs Batch 2D `nq-app` context smoke. | 2A / 2B / 2C FROZEN / ACCEPTED baseline plus 2D IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN. The first run failed in the 2D step; the first-run fix (pre-stubbed CI-only adapter venues) is applied locally and the next CI run is pending confirmation. |
| `frontend` | Runs `npm ci` + `npm run build`. | Outside Batch 2D; frontend E2E hardening remains Batch 5. |
| `research` | Runs Python pytest / mypy / ruff. | Outside Batch 2D. |

Batch 2D is implemented as a clearly isolated step after the existing `postgres-flyway` artifacts and repository smoke. First-run review did not confirm green status; the only next implementation path is `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX`.

## First CI run review

Run reviewed: GitHub Actions `NQ CI Baseline` run `27590822405`, branch `dev`, commit `521e100b58ec2ee2b06463bf7558ff65a9630cf4`, event `push`, created `2026-06-16T02:52:29Z`, completed `2026-06-16T02:54:35Z`.

Decision: FAIL / FIRST-RUN-FIX REQUIRED. Batch 2D must not be written as FIRST GREEN RUN CONFIRMED, FROZEN, or ACCEPTED.

Evidence:

- Overall run: `completed / failure`.
- `postgres-flyway` job (`PostgreSQL / Flyway smoke`, job `81570960942`): `completed / failure`.
- `Run empty database Flyway smoke`: success; logs show 31 migrations applied and validated, current version V31.
- `Generate PostgreSQL schema artifacts`, `Check PostgreSQL schema artifacts`, `Upload PostgreSQL schema artifacts`: success; artifact `nq-postgres-flyway-schema-artifacts` id `7656304957` uploaded.
- `Run repository PostgreSQL smoke`: success; `JdbcRepositoryPostgresSmokeTest` showed tests=1 / skipped=0 / failures=0 / errors=0 and Maven `BUILD SUCCESS`.
- `Run nq-app PostgreSQL context smoke`: failure.
- `NqAppContextPostgresSmokeTest`: actually selected and executed under profile `ci-app-smoke`; not skipped.
- Surefire summary for `NqAppContextPostgresSmokeTest`: tests=1 / skipped=0 / failures=0 / errors=1.
- Failure root cause from CI log: Spring context failed while creating `AdapterBackedTradingVenueGateway`; nested cause `IllegalArgumentException: venue must not be blank`.
- The CI step used `nq.app.context.smoke.required=true` and the GitHub Actions PostgreSQL service DB properties, so this was a real CI-required execution, not a local optional skip.

Boundary observations from the failed run:

- `local` profile was not used; CI log showed active profile `ci-app-smoke`.
- Current `test` profile was not reused as-is.
- No successful app context startup was confirmed.
- No evidence of `AuthSeedConfiguration` execution or admin / operator / viewer seed creation was found before the context failure.
- No evidence of legacy account, exchange account, or credential row creation was confirmed for the 2D step.
- No successful OKX / Binance / Bybit / Gate / Coinbase / Kraken access was found; however Batch 3 no-outbound guard is still not implemented.
- CI logs still expose disposable CI PostgreSQL service connection material in service initialization / automatic step environment display before masking can fully protect it. This is not real production credential material, but it fails the stricter Batch 2D first-run acceptance item that requires no JDBC password / full connection string / env dump in logs.

Next action: `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` only. Fix scope must stay limited to `.github/workflows/ci.yml`, `backend/nq-app` test, and `docs/current` status records. Do not mix Batch 2E, Batch 3-5, LIVE, AI, DH runtime, RealClient, real provider, or real exchange adapter.

## First-run fix (NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX, 2026-06-16)

Status after fix: IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN. Not FIRST GREEN, not FROZEN, not ACCEPTED. A new GitHub Actions run is required to confirm the fix on CI.

### Failure root cause

The production `AdapterBackedTradingVenueGateway` (in `nq-scheduler`) is an eager singleton whose constructor builds a venue→adapter routing map by calling `adapter.venue()` on every `TradingAdapter` bean during context refresh. The first 2D smoke replaced the OKX / Binance adapters with bare `@MockitoBean` mocks; a bare Mockito mock returns a blank `venue()`, so the gateway threw `IllegalArgumentException: venue must not be blank` while the context was still being created. Stubbing a `@MockitoBean` in a JUnit lifecycle hook cannot fix this, because the eager singleton gateway is built before any `@BeforeEach` runs.

### Fix summary (test-only)

Changed only `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java`. No production code, no migration, no workflow logic change.

- The two REST exchange adapters are now supplied as pre-stubbed Mockito mocks from a nested `@TestConfiguration` (`StubbedExchangeAdapterConfig`) that overrides the named `ExchangeAdapterConfiguration` beans `okxTradingAdapter` / `binanceTradingAdapter`. Each mock's `venue()` is stubbed to a distinct, non-blank, CI-only fake value (`CI-SMOKE-FAKE-OKX` / `CI-SMOKE-FAKE-BINANCE`) so the production gateway can build its routing map at refresh time without constructing a real adapter, reading a credential, or contacting an exchange.
- Added `spring.main.allow-bean-definition-overriding=true` to the smoke `@TestPropertySource` so the two named adapter beans can be overridden. Local full-context tests load the same composition root without this flag, so it cannot hide a real duplicate-definition problem; it only enables the two intentional CI-only adapter substitutions.
- OKX / Binance WS clients remain `@MockitoBean` (the gateway never reads them, so `verifyNoInteractions` on them still proves no WebSocket connect at startup).
- The assertion changed from `verifyNoInteractions` on the exchange adapters to `verify(..., never()).placeOrder/cancelOrder/getOrder(...)`. Reason: the gateway legitimately calls `venue()` at composition time, so a blanket `verifyNoInteractions` on the adapters could never pass; the smoke instead proves no order is placed / cancelled / queried during startup, which is the real exchange-side-effect guarantee.

### Why this does not mask app-context risk

Only the outermost exchange I/O edges (two REST adapters, two WS clients) are substituted — exactly the isolation Batch 2D requires (no real exchange, no credential). The datasource (bound to the Flyway-migrated CI PostgreSQL), repositories, domain services, security wiring, scheduler wiring, and the `AdapterBackedTradingVenueGateway` itself remain real production beans, so the smoke still exercises the real Spring composition root against PostgreSQL.

### Seed / AuthSeed boundary (re-confirmed)

- `AuthSeedConfiguration` is `@Profile({"local","test"})`; the smoke runs under `ci-app-smoke`, so the seed runner is not activated and no seed users are created.
- `AuthBootstrapAdminConfiguration` is `@ConditionalOnProperty(nq.auth.bootstrap-admin.enabled=true)`; the smoke sets it `false`, so no admin is bootstrapped.
- No legacy `accounts`, exchange account, or credential row is created; no credential material is read, decrypted, printed, copied, or output.

### CI log hygiene status

- The `postgres-flyway` job already runs a first `Mask CI-only PostgreSQL connection values` step that registers `::add-mask::` for `NQ_FLYWAY_DB_URL` / `NQ_FLYWAY_DB_USER` / `NQ_FLYWAY_DB_PASSWORD`, so every subsequent step's log is masked.
- No workflow step uses `set -x`, `env`, `printenv`, a full environment dump, or echoes the JDBC password / full connection string; DB properties reach Maven only through `-D...="${VAR}"` (masked, not echoed). The step-level requirement "no JDBC password / connection string / env dump in subsequent steps" is satisfied, so `.github/workflows/ci.yml` needs no change for this fix.
- Residual: GitHub Actions prints the PostgreSQL service container's `env` (including the disposable `POSTGRES_PASSWORD`) in its own "Initialize containers" output, which runs before any step and therefore before `::add-mask::` can apply. This value is a throwaway, non-production CI database password with no value outside the ephemeral job. Fully removing it would require either GitHub Secrets (excluded by Batch 2D's "no GitHub secret requirement") or changing the shared PostgreSQL service auth model used by the FROZEN 2A / 2B / 2C steps (out of 2D first-run-fix scope). It is recorded here as an accepted P3 platform residual and deferred to a future CI secrets policy.

### Local validation

```
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest -Dsurefire.failIfNoSpecifiedTests=false
```

Result: BUILD SUCCESS; `NqAppContextPostgresSmokeTest` tests=1 / failures=0 / errors=0 / skipped=1. The class is `@EnabledIfSystemProperty(nq.app.context.smoke.required=true)`, so without CI DB properties it compiles and is selected but skipped. This proves compilation and Surefire selection only; the real PostgreSQL context startup must be confirmed by the next GitHub Actions run (where the CI step sets `nq.app.context.smoke.required=true` and skipped must be 0).

## App context test inventory

Source-only inspection found the following `nq-app` tests relevant to Spring context and profile planning:

| Test | Context style | Profile | 2D relevance |
| --- | --- | --- | --- |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/ResearchBacktestHappyPathLocalTest.java` | `@SpringBootTest(classes = NexusQuantApplication.class)` + `@AutoConfigureMockMvc` | `@ActiveProfiles("local")` | Full app context; reads first row from legacy `accounts`; can depend on Batch 1 seed watcher in CI. Not suitable as-is for 2D. |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/MarketdataControllerLocalIntegrationTest.java` | `@SpringBootTest(classes = NexusQuantApplication.class)` + `@AutoConfigureMockMvc` | `@ActiveProfiles("local")` | Full app context with DB-backed fixture ingest/query. Not suitable as-is for minimal 2D because it tests controller behavior, not pure context startup. |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/OkxBootstrapNoOutboundLocalContextTest.java` | `@SpringBootTest(classes = NexusQuantApplication.class)` | `@ActiveProfiles("local")` | Full local context and a targeted OKX public instruments no-outbound probe. Useful evidence, but not a complete Batch 3 no-outbound guard and not the preferred 2D smoke profile. |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/TradingVerificationControllerLocalTest.java` | `@WebMvcTest` + mocked services | `@ActiveProfiles("local")` | MVC slice; does not prove full app context. It references trading routes but uses mocks. Exclude from 2D context smoke. |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/ResearchBacktestQueryControllerLocalTest.java` | `@WebMvcTest(useDefaultFilters = false)` + explicit test config | `@ActiveProfiles("local")` | MVC slice; not full app context. Exclude from 2D context smoke. |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/AuthSecurityWebMvcTest.java` | `@WebMvcTest` + mocked repositories | `@ActiveProfiles("test")` | MVC auth slice. `test` profile currently has Flyway disabled and still matches `AuthSeedConfiguration` profile if full context is used, so it must not be the default 2D full-context profile. |

Current main-profile facts:

- `backend/nq-app/src/main/resources/application.yml` defaults to `spring.profiles.active: ${NQ_PROFILE:local}`.
- `application-local.yml` enables Flyway, sets local DB defaults, defines seed users, keeps OKX recovery disabled, and keeps WS disabled.
- `application-test.yml` uses test DB placeholders but has Flyway disabled and defines seed users.
- `application-freeze.yml` disables catalog sync, OKX recovery, OKX WS, and Binance WS, but it requires freeze secrets and is not a CI app context profile.
- `application-gated-verify.yml` is a specialized gated verification profile, not a general CI context smoke profile.

## Runner / scheduler / provider inventory

Source-only inspection found these context-startup or runtime side-effect candidates:

| Component | Trigger | Current risk for 2D |
| --- | --- | --- |
| `AuthSeedConfiguration` | `@Profile({"local", "test"})` + `ApplicationRunner` | Seeds `nq.security.users` into DB on full context startup for `local` / `test`. 2D must avoid these profiles or explicitly disable / replace this behavior before implementation. |
| `AuthBootstrapAdminConfiguration` | `@ConditionalOnProperty(prefix = "nq.auth.bootstrap-admin", name = "enabled", havingValue = "true")` + `ApplicationRunner` | Safe by default if property is absent / false; 2D must keep it false and must not bootstrap real admin users. |
| `ExchangeAdapterConfiguration` | Always-on `@Configuration` | Instantiates OKX / Binance adapter and WS client beans. Constructors create HTTP clients and caches; actual external calls are normally triggered by adapter operations / cache snapshots, so the 2D smoke must avoid invoking adapter methods and must disable related runtime services. |
| `AdapterInstrumentCatalogSyncService` | Component method invoked by API / service call | Can call OKX / Binance public metadata through adapter caches when `nq.instrument.catalog-sync.enabled=true`. 2D must set it false and must not call catalog sync endpoints. |
| `OkxRecoveryService` | `ContextRefreshedEvent` and `@Scheduled`; property `nq.okx.recovery.enabled` defaults true in code / base config | P0 candidate if enabled, because startup recovery can call OKX adapter methods. 2D must set `nq.okx.recovery.enabled=false`. |
| `OkxRestReconcileService` / `BinanceRestReconcileService` | `@Scheduled` methods | Should not be exercised by a pure context smoke. 2D must not enable scheduling-driven reconciliation or call run-once endpoints. |
| `PaperMatchingService` | `@Scheduled` method | Local Paper-only side effect, but still writes / mutates if matchable orders exist. 2D must keep the context smoke free of business data and should disable scheduling if implementation enables scheduling. |
| OKX / Binance WS runners and bridges | Conditional on `nq.okx.ws.enabled=true` / `nq.binance.ws.enabled=true`, some local-only runners | 2D must keep both WS flags false and must not use local profile to enable smoke runners. |
| `AccountModuleConfiguration` permission probe port | Always provides `NoRealExchangeCredentialPermissionProbePort` | Current default is no-real and returns guarded `SKIPPED`; 2D must preserve this and must not wire a real permission probe adapter. |

No `@EnableScheduling` / `spring.task.scheduling.enabled` usage was found in the current source scan. 2D should still set explicit scheduler-disabling properties in the future smoke so the safety boundary does not rely on this incidental absence.

## Batch 2D goal

Minimal Batch 2D goal:

- Verify that the `nq-app` Spring application context can start against a disposable CI PostgreSQL database after Flyway schema migration.
- Keep the test at context startup only; no HTTP controller workflow, no frontend E2E, no scheduler business execution, no adapter operation, no seed creation.
- Fail on context wiring errors, missing required beans, datasource misconfiguration, or profile/property drift that blocks a no-side-effect startup.

Non-goals:

- Do not prove no-outbound comprehensively. Batch 3 owns no-outbound guard.
- Do not prove secret scan or artifact redaction. Batch 4 owns security guard / secret scan.
- Do not prove frontend login or E2E. Batch 5 owns frontend E2E hardening.
- Do not clean up Batch 1 seed watcher. Batch 2E owns CI-only seed watcher cleanup.

## Profile / property boundary

2D must not use `local` profile:

- `local` is the application default when `NQ_PROFILE` is absent.
- `local` enables `AuthSeedConfiguration`, local seed users, local DB defaults, and local fallback assumptions.
- `local` is appropriate for developer runs, not CI app context smoke.

2D should not use the current `test` profile as-is:

- `test` still matches `AuthSeedConfiguration`.
- `test` currently has `spring.flyway.enabled=false`.
- It carries test seed users and DB placeholders, not a reviewed CI app smoke contract.

Recommended future strategy:

1. Prefer explicit properties in the smoke test / Maven command first, to avoid adding a new profile file in the first implementation slice.
2. If explicit properties become too large or duplicated, introduce a reviewed CI-only fake profile such as `ci-app-smoke` in a separate implementation patch.
3. The future profile / property set must include at minimum:
   - `spring.profiles.active=ci-app-smoke` or an equivalent non-`local` / non-`test` profile.
   - `spring.datasource.url/user/password` pointing only to the disposable CI PostgreSQL DB.
   - `spring.flyway.enabled=false` if the schema has already been migrated by the direct Flyway step, or `true` only if 2D owns a fresh app-smoke DB and migration is part of that smoke.
   - `nq.auth.bootstrap-admin.enabled=false`.
   - `nq.security.users=[]`.
   - `nq.instrument.catalog-sync.enabled=false`.
   - `nq.okx.recovery.enabled=false`.
   - `nq.okx.ws.enabled=false`.
   - `nq.binance.ws.enabled=false`.
   - `nq.okx.adapter.stub-on-bootstrap-failure=false` unless a specific no-outbound-safe fake adapter policy is reviewed.
   - `nq.account.credentials.verification-mode=STRUCTURAL`.

The smoke must also explicitly set `NQ_PROFILE` or `spring.profiles.active`; relying on the base default is forbidden because it falls back to `local`.

## DB strategy

Recommended DB policy:

- Use the GitHub Actions PostgreSQL service container, not local PostgreSQL and not Testcontainers in 2D-1.
- Prefer a separate disposable database name for app context, for example `nq_ci_app`, so 2D can be rolled back without disturbing 2A / 2B / 2C artifacts.
- Prepare the app-smoke database with Flyway V1-V31 before starting context, or let context Flyway migrate only if the 2D implementation owns an isolated empty app-smoke DB.
- Do not upload data row artifacts.
- Do not run Flyway `clean`.
- Do not use `baselineOnMigrate(true)`.
- Do not insert legacy account seed, auth users, real accounts, real exchange accounts, or credential rows.

If the future context smoke fails because a bean requires an `accounts` row or auth user row at startup, that is a 2D planning blocker, not permission to seed implicitly. The implementation should either narrow the context smoke or defer seed strategy to Batch 2E.

## Seed / AuthSeed boundary

Batch 2D decision:

- `AuthSeedConfiguration` must not run in the first 2D smoke slice.
- The smoke must not use `local` / `test` profile.
- The smoke must not create admin / operator / viewer seed users.
- The smoke must not create legacy `accounts` rows.
- The smoke must not create exchange account / credential rows.
- The smoke must not read, decrypt, print, copy, or output credential material.

Batch 2E boundary:

- Batch 2E owns CI-only seed watcher cleanup.
- 2D may document that existing Batch 1 backend job still uses a watcher for legacy full Maven tests, but 2D must not change or rely on it.
- Any future seed fixture must be explicit, fake, local to the disposable CI database, and reviewed under 2E or a later implementation plan.

## Security boundary

Batch 2D must preserve:

- No OKX / Binance / Bybit / Gate / Coinbase / Kraken network access.
- No public exchange bootstrap call.
- No private exchange REST / WS call.
- No order, cancel, transfer, withdraw, funding, account-balance, private listenKey, or permission-probe HTTP operation.
- No LIVE.
- No AI.
- No DH runtime.
- No RealClient.
- No real provider.
- No real exchange adapter / permission probe adapter.
- No GitHub secret requirement.
- No `.env` read.
- No production DB credential.
- No raw credential payload in logs or artifacts.

2D does not implement Batch 3 no-outbound guard. Until Batch 3 exists, 2D reduces outbound risk by construction:

- Use context-load only, preferably `webEnvironment = NONE`.
- Avoid controller calls and scheduled/manual run-once endpoints.
- Disable catalog sync, OKX recovery, OKX WS, and Binance WS.
- Preserve default no-real permission probe port.
- Keep exchange credentials blank / fake placeholders only.

## Implementation slicing

### 2D-1: minimal context smoke implementation

Status: IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN. See "First-run fix (NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX, 2026-06-16)" above for the root cause and the pre-stubbed venue fix.

Implemented smoke shape:

- Added `NqAppContextPostgresSmokeTest` under `backend/nq-app/src/test/**`.
- Uses `@SpringBootTest(classes = NexusQuantApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)`.
- Uses `@ActiveProfiles("ci-app-smoke")`; it does not use `local` and does not reuse the current `test` profile as-is.
- The test is enabled only when `nq.app.context.smoke.required=true`, and the CI step sets that property explicitly. Missing datasource properties fail the smoke in CI.
- The context receives only explicit `nq.app.context.smoke.*` datasource system properties from the disposable CI PostgreSQL service DB.
- The context sets `spring.flyway.enabled=false` because Flyway migration is already completed earlier in the same job.
- The test replaces OKX / Binance WS client beans with `@MockitoBean` test doubles, and replaces the OKX / Binance REST adapter beans (`okxTradingAdapter` / `binanceTradingAdapter`) with pre-stubbed Mockito mocks from a nested `@TestConfiguration`, so real adapter / WS constructors do not read `.env` or construct real exchange client paths. The first run used bare `@MockitoBean` adapters whose blank `venue()` broke the production `AdapterBackedTradingVenueGateway`; the first-run fix pre-stubs each adapter `venue()` to a distinct CI-only fake value so the gateway can build its routing map at refresh time. See the first-run-fix section above.
- The assertion is context-load only plus `verify(..., never()).placeOrder/cancelOrder/getOrder(...)` on the exchange adapters (the gateway legitimately reads `venue()` at composition time, so a blanket `verifyNoInteractions` on the adapters is not used) and `verifyNoInteractions` on the WS mocks; it does not call controllers, services, scheduler jobs, adapter order methods, order / cancel / transfer / withdraw / permission probe HTTP, or exchange hosts.
- The CI step runs:

```bash
mvn -f backend/pom.xml -pl nq-app -am test \
  -Dtest=NqAppContextPostgresSmokeTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dnq.app.context.smoke.required=true \
  -Dnq.app.context.smoke.url="${NQ_FLYWAY_DB_URL}" \
  -Dnq.app.context.smoke.user="${NQ_FLYWAY_DB_USER}" \
  -Dnq.app.context.smoke.password="${NQ_FLYWAY_DB_PASSWORD}"
```

No `continue-on-error`, `skipTests`, Testcontainers, GitHub real secrets, `.env` read, bare `env`, `printenv`, full environment dump, seed watcher, seed SQL, migration change, or production code change is introduced by this step.

### 2D-2: app context profile hardening plan

Status: PENDING / NOT STARTED.

After 2D-1 is stable, evaluate whether explicit properties should be promoted to a tracked `application-ci-app-smoke.yml` file. If added, that profile must:

- Be CI-only and fake-only.
- Keep `AuthSeedConfiguration` out of scope by not matching `local` / `test`.
- Disable bootstrap admin.
- Disable catalog sync, OKX recovery, OKX WS, Binance WS, and any future scheduler side-effect trigger.
- Keep datasource pointed to the disposable CI DB.
- Avoid all real credential placeholders except obvious non-production defaults.

### 2D-3: required check evaluation

Status: PENDING FIRST CI RUN.

Do not make 2D required immediately. First require:

1. One first green run.
2. Freeze review with P0/P1=0.
3. Evidence that startup does not rely on Batch 1 seed watcher.
4. Evidence that no exchange host was contacted by construction and logs show no outbound failure.
5. Stable runtime under the selected timeout.

Only after that review should 2D be considered for required-check promotion on backend / app / profile / migration changes.

## Batch boundary

| Batch | Boundary |
| --- | --- |
| 2A | Empty DB Flyway V1-V31 migration smoke. FROZEN / ACCEPTED. |
| 2B | Schema artifact baseline. FROZEN / ACCEPTED. |
| 2C | Repository-only real PostgreSQL smoke. FROZEN / ACCEPTED. |
| 2D | `nq-app` context smoke. IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN. |
| 2E | CI-only seed watcher cleanup. NOT STARTED. |
| 3 | no-outbound guard. PENDING and not implemented by 2D. |
| 4 | security guard / secret scan. PENDING and not implemented by 2D. |
| 5 | frontend E2E hardening. PENDING and not implemented by 2D. |

## P0/P1/P2/P3 findings

| Priority | Finding | Decision |
| --- | --- | --- |
| P0 | Using `local` profile for 2D would trigger local seed users and developer-machine defaults. | Forbidden. Use CI-only fake profile / explicit properties. |
| P0 | Using `test` profile as-is still matches `AuthSeedConfiguration` and has Flyway disabled. | Not accepted as the default 2D full-context profile. |
| P0 | Leaving `nq.okx.recovery.enabled` at default true can trigger startup recovery and adapter calls. | Must explicitly set false in future 2D smoke. |
| P0 | Any OKX / Binance / Bybit / Gate / Coinbase / Kraken access, LIVE enablement, RealClient, real provider, or real credential material violates GateK CI boundary. | Forbidden. |
| P1 | Existing Batch 1 CI-only seed watcher can hide app context fixture requirements if reused. | Do not reuse in 2D; keep 2E cleanup separate. |
| P1 | `ExchangeAdapterConfiguration` instantiates adapter beans even when context only loads. | Keep smoke context-load only and avoid adapter methods; disable catalog sync / recovery / WS. |
| P1 | Full `@SpringBootTest` local integration tests are broader than a context smoke and may need legacy account data. | Do not reuse as-is for 2D. |
| P2 | The first 2D smoke may add CI time or flake due to context startup complexity. | Start non-required; freeze only after first green review. |
| P2 | Current source has `@Scheduled` methods but no `@EnableScheduling` found. | Do not rely on absence; future smoke should explicitly disable scheduler side effects where possible. |
| P3 | Existing docs scans include many historical / forbidden-term hits. | Treat broad `rg` hits as review input; separate real risk from boundary wording. |

## Rollback plan

If future Batch 2D implementation is flaky or too broad:

- Revert only the 2D job / step / smoke test.
- Keep 2A empty DB Flyway smoke unchanged.
- Keep 2B schema artifacts unchanged.
- Keep 2C repository-only smoke unchanged.
- Do not weaken 2A / 2B / 2C required behavior to make 2D pass.
- Do not add `continue-on-error` after 2D becomes required; demote or revert the 2D check through review.
- Do not modify production code, migrations, or seed policy just to make context smoke green.

## Validation commands

Implementation validation for this task:

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
rg "@SpringBootTest|ActiveProfiles|AuthSeedConfiguration|ApplicationRunner|CommandLineRunner|Scheduler|Scheduled|RealClient|provider|exchange|LIVE|OKX|Binance|Bybit|Gate|Coinbase|Kraken" backend docs/current
rg "apiKey|secret|passphrase|token|private key|mnemonic|credential material" backend .github docs/current
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'
```

Notes:

- The local Maven command runs the selected test class without CI DB properties; the class is disabled unless `nq.app.context.smoke.required=true`, so local execution validates compilation and Surefire selection but cannot prove the CI PostgreSQL context startup. The real app context smoke must be reviewed after the first GitHub Actions run.
- Frontend build / E2E and Python pytest / mypy / ruff are not required for Batch 2D because this task does not modify frontend or research.
- Broad `rg` commands intentionally match docs/current forbidden-boundary wording, migration comments, and fake test values. Findings must be reviewed against changed files and source context.

## Review decision

Review decision: FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN.

Batch 2D implements only the minimal `nq-app` context startup smoke, using CI-only fake profile / explicit properties, the same disposable PostgreSQL service DB after Flyway migration, no seed, no local profile, no current test profile reuse, no AuthSeed runner, no scheduler business execution, no adapter order-method calls, no LIVE, no AI, no DH runtime, no RealClient, no real provider, and no real exchange credential material. First run `27590822405` failed in `Run nq-app PostgreSQL context smoke` (`venue must not be blank`); the first-run fix replaces the bare `@MockitoBean` adapters with pre-stubbed CI-only fake-venue mocks so the production gateway can build its routing map at refresh time. The fix is test-only and validated locally (BUILD SUCCESS, skipped=1 without CI DB properties). Batch 2D must not be marked FIRST GREEN RUN CONFIRMED, FROZEN, or ACCEPTED until a new GitHub Actions run shows the 2D step green with skipped=0.

## Next concrete action

Next concrete action: re-run `NQ CI Baseline` on `dev` and then `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW`. If the next run is still red, continue with another `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` slice.

Do not proceed directly to 2E seed watcher cleanup implementation, Batch 3 no-outbound guard implementation, Batch 4 secret scan implementation, Batch 5 frontend E2E hardening implementation, AI, DH runtime, LIVE, RealClient, real provider or real exchange adapter from this first-run fix.
