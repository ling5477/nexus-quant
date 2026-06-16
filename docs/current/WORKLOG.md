# Worklog: DOC-CLEAN + BASELINE-FIX

日期：2026-05-16

## NQ-CI-POSTGRES-FLYWAY-2D-FREEZE-REVIEW

日期：2026-06-16

### 目标

冻结 Batch 2D `nq-app` context smoke baseline，确认它可以作为 GateK CI Batch 2D 当前基线。本轮只同步允许的 `docs/current` 状态记录，不修改 workflow、业务代码、测试代码、migration、frontend、research、scripts 或 deploy。

### 评审证据

- GitHub Actions run `27601707199`，workflow `NQ CI Baseline`，branch `dev`，commit `cbc03013bd393e2534befa10b25cc5b4c62b54a4`：completed / success。
- jobs：Diff check / Frontend build / Backend Maven test / Research quality gate / `PostgreSQL / Flyway smoke` 均 success。
- `PostgreSQL / Flyway smoke` job `81604024163`：all steps success。
- Step `Run empty database Flyway smoke`：success。
- Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts`：success；artifact `nq-postgres-flyway-schema-artifacts` / id `7660159897` / digest `sha256:45b0e86ab0f499d70d04e02b7845850af11a98b3fd091f1e9c1f4b4718dc6f05`。
- Step `Run repository PostgreSQL smoke`：success。
- Step `Run nq-app PostgreSQL context smoke`：success。
- `NqAppContextPostgresSmokeTest`：active profile `ci-app-smoke`；Surefire summary tests=1 / skipped=0 / failures=0 / errors=0；`nq-app SUCCESS`；Maven `BUILD SUCCESS`。

### 边界确认

- P0/P1 findings：0。
- 未使用 `local` profile；未 as-is 复用 current `test` profile。
- 未发现 `AuthSeedConfiguration` 执行、admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据。
- 未发现成功访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未开启 LIVE；未接 AI；未接 DH runtime；未实现 RealClient / real provider / real exchange adapter。
- Batch 2D freeze 只接受 context startup baseline；不证明 Batch 3 no-outbound guard。
- Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。
- CI logs 未发现真实 credential material；disposable CI PostgreSQL service values 与 Spring Boot generated development security password 作为 P3 log hygiene residual 延后。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PASS / FROZEN / ACCEPTED。Batch 2D 冻结为当前 `dev` `nq-app` context smoke baseline。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2E-PLAN`、Batch 3 pre-planning，或按用户选择暂停 CI 线。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW after FIRST-RUN-FIX #3

日期：2026-06-16

### 目标

只评审 FIRST-RUN-FIX #3 推送后的 GitHub Actions run，确认 `nq-app` context smoke 是否在 CI PostgreSQL service DB 上真实执行并通过。本轮不进入 Batch 2E，不进入 Batch 3-5，不修改业务代码、workflow、测试代码、migration、frontend、research、scripts 或 deploy，只同步允许的 `docs/current` 状态记录。

### 评审证据

- GitHub Actions run `27601707199`，workflow `NQ CI Baseline`，branch `dev`，commit `cbc03013bd393e2534befa10b25cc5b4c62b54a4`：completed / success。
- jobs：Diff check / Frontend build / Backend Maven test / Research quality gate / `PostgreSQL / Flyway smoke` 均 success。
- `PostgreSQL / Flyway smoke` job `81604024163`：all steps success。
- Step `Run empty database Flyway smoke`：success；Batch 2A migration smoke 未回归。
- Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts`：success；artifact `nq-postgres-flyway-schema-artifacts` / id `7660159897` / digest `sha256:45b0e86ab0f499d70d04e02b7845850af11a98b3fd091f1e9c1f4b4718dc6f05`。
- Step `Run repository PostgreSQL smoke`：success；Batch 2C repository smoke 未回归。
- Step `Run nq-app PostgreSQL context smoke`：success。
- `NqAppContextPostgresSmokeTest`：真实执行且未 skip；active profile `ci-app-smoke`；Surefire summary tests=1 / skipped=0 / failures=0 / errors=0；`nq-app SUCCESS`；Maven `BUILD SUCCESS`。
- `OkxRecoveryService` logged startup skip: recovery disabled / mapped trade env SIM。

### 边界确认

- 未使用 `local` profile；未 as-is 复用 current `test` profile。
- 未发现 `AuthSeedConfiguration` 执行、admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据。
- 未发现成功 order / cancel / transfer / withdraw / private REST / WS connect 路径。
- 未开启 LIVE；未接 AI；未接 DH runtime；未实现 RealClient / real provider / real exchange adapter。
- Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。
- Batch 2D 只证明 context startup；完整 no-outbound guard 仍由 Batch 3 独立覆盖。
- CI logs 未发现真实生产 credential material；但仍有 disposable CI PostgreSQL service values 的平台级显示和 Spring Boot generated development security password，记录为 P3 CI / app-smoke log hygiene residual。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PASS / ACCEPTED FOR FIRST GREEN RUN。Batch 2D 当前为 FIRST GREEN RUN CONFIRMED，但尚未 FROZEN / final ACCEPTED。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2D-FREEZE-REVIEW`、`NQ-CI-POSTGRES-FLYWAY-2E-PLAN` 或 Batch 3 pre-planning。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX after NotAMockException

日期：2026-06-16

### 目标

最小修复 Batch 2D `nq-app` context smoke 在 GitHub Actions run `27596768301` 中的 `NotAMockException`。本轮只调整 `NqAppContextPostgresSmokeTest` 的验证策略与允许的 `docs/current` 状态记录，不修改 backend production code、workflow、migration、frontend、research、scripts 或 deploy。

### 失败根因

Run `27596768301` 已证明 servlet web context 能在 `ci-app-smoke` 下启动到测试体，但测试体对 `OkxExchangeAdapter` 做 Mockito `verify(...)`。CI 实际注入的是 component-scanned real adapter，不是 mock，因此 Mockito 抛出 `NotAMockException`。这说明 REST adapter bean override / verification strategy 不适合作为 Batch 2D context smoke 验证策略。

### 修改范围

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 实现摘要

- 删除 REST adapter Mockito verify 路径；Batch 2D 不再对可能是真实 bean 的 OKX / Binance REST adapter 做 `verify(...)`。
- 保持测试目标为 context loads + `ci-app-smoke` active profile + no seed + no LIVE + no WS interaction。
- 保持 `@ActiveProfiles("ci-app-smoke")` 和 `SpringBootTest.WebEnvironment.MOCK`。
- 新增 active profile 断言，防止回落到 `local` 或 current `test` profile。
- WS client 仍为 `@MockitoBean`，并在 `verifyNoInteractions` 前用 `mockingDetails(...).isMock()` 确认是 Mockito mock。
- 未调用 `placeOrder` / `cancelOrder` / `getOrder` / private REST / WS 方法。
- 文档状态收口为 `IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN`；不写 FIRST GREEN / FROZEN / ACCEPTED。

### 验证记录

- `idea-mcp build_project` 目标测试文件：通过，`isSuccess=true`，无 problems。
- 首次 Maven 命令未引用 `-Dsurefire.failIfNoSpecifiedTests=false`，PowerShell 将其解析成非法 lifecycle phase `.failIfNoSpecifiedTests=false`，该失败为命令转义问题。
- 重跑：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：BUILD SUCCESS；`NqAppContextPostgresSmokeTest` tests=1 / failures=0 / errors=0 / skipped=1。本地无 CI DB properties 且未设置 `nq.app.context.smoke.required=true`，只能证明编译与 Surefire selection；CI required path 仍需 GitHub Actions 验证 tests=1 / skipped=0 / failures=0 / errors=0。

### 边界确认

- 未改 backend production code / workflow / migration / frontend / research / scripts / deploy。
- 未新增 API；未修改历史 migration。
- 未使用 `local` profile；未 as-is 复用 current `test` profile。
- 未触发 `AuthSeedConfiguration`；未创建 admin/operator/viewer seed users、legacy accounts、exchange accounts 或 credential rows。
- 未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken；未读取或输出真实 credential material。
- 未开启 LIVE；未接 AI；未接 DH runtime；未实现 RealClient / real provider。
- 未实现 Batch 2E；未实现 Batch 3 no-outbound guard；未实现 Batch 4 secret scan；未实现 Batch 5 frontend E2E hardening。

### Review decision

PASS / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN。下一步只能 re-run `NQ CI Baseline` on `dev` 后执行 `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW`；如仍失败，继续 scoped 2D first-run fix。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW after FIRST-RUN-FIX #2

日期：2026-06-16

### 目标

只评审 FIRST-RUN-FIX #2 推送后的 GitHub Actions run，确认 `nq-app` context smoke 是否在 CI PostgreSQL service DB 上真实执行并通过。本轮不进入 Batch 2E，不进入 Batch 3-5，不修改业务代码、workflow、测试代码、migration、frontend、research、scripts 或 deploy，只同步允许的 `docs/current` 状态记录。

### 评审证据

- GitHub Actions run `27596768301`，workflow `NQ CI Baseline`，branch `dev`，commit `5b6ec1aafa43d483e8ea0a6385efa09f9d0ec392`：completed / failure。
- jobs：Diff check / Frontend build / Backend Maven test / Research quality gate 均 success；`PostgreSQL / Flyway smoke` failed。
- `PostgreSQL / Flyway smoke` job `81588559094`：仅 step `Run nq-app PostgreSQL context smoke` 失败。
- Step `Run empty database Flyway smoke`：success；V1-V31 migration smoke 未回归。
- Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts`：success；artifact `nq-postgres-flyway-schema-artifacts` / id `7658307273` uploaded。
- Step `Run repository PostgreSQL smoke`：success；Batch 2C repository smoke 未回归。
- `NqAppContextPostgresSmokeTest`：真实执行且未 skip；active profile `ci-app-smoke`；Surefire summary tests=1 / skipped=0 / failures=0 / errors=1。
- Root cause：servlet web context 已启动，测试体失败于 `NotAMockException`；`verify(...)` 的 `OkxExchangeAdapter` 不是 Mockito mock，说明 FIRST-RUN-FIX #2 里的 REST adapter bean override / verification strategy 在 CI context 中不可靠。

### 边界确认

- 未使用 `local` profile；未 as-is 复用 current `test` profile。
- 未发现 `AuthSeedConfiguration` 执行、admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据。
- 未发现成功 order / cancel / transfer / withdraw 路径。
- 未开启 LIVE；未接 AI；未接 DH runtime；未实现 RealClient / real provider / real exchange adapter。
- Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。
- CI logs 未发现真实生产 credential material；但仍有 disposable CI PostgreSQL service connection material 的平台级显示和 Spring Boot generated development security password，严格 log hygiene 验收项未满足。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

FAIL / FIRST-RUN-FIX REQUIRED。Batch 2D 不能标记为 FIRST GREEN RUN CONFIRMED，不能标记为 FROZEN / ACCEPTED。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` only。修复范围只允许 `.github/workflows/ci.yml`、`backend/nq-app` test 和 `docs/current` 状态记录；不得混入 Batch 2E、Batch 3-5、LIVE、AI、DH runtime、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW

日期：2026-06-16

### 目标

只评审包含 Batch 2D 变更的 GitHub Actions first run，确认 `nq-app` context smoke 是否在 CI PostgreSQL service DB 上真实执行并通过。本轮不进入 Batch 2E，不进入 Batch 3-5，不修改业务代码，不修复 workflow / test，只同步允许的 `docs/current` 状态记录。

### 评审证据

- GitHub Actions run `27590822405`，workflow `NQ CI Baseline`，branch `dev`，commit `521e100b58ec2ee2b06463bf7558ff65a9630cf4`：completed / failure。
- `postgres-flyway` job `PostgreSQL / Flyway smoke` / `81570960942`：completed / failure。
- Step `Run empty database Flyway smoke`：success；31 migrations applied / validated，current version V31。
- Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts`：success；artifact `nq-postgres-flyway-schema-artifacts` / id `7656304957` uploaded。
- Step `Run repository PostgreSQL smoke`：success；`JdbcRepositoryPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0；Maven `BUILD SUCCESS`。
- Step `Run nq-app PostgreSQL context smoke`：failure。
- `NqAppContextPostgresSmokeTest`：真实执行且未 skip；Surefire summary tests=1 / skipped=0 / failures=0 / errors=1。
- Root cause：Spring context failed while creating `AdapterBackedTradingVenueGateway` through the trading strategy dependency chain；nested cause `IllegalArgumentException: venue must not be blank`。

### 边界确认

- 未使用 `local` profile；CI log 显示 active profile `ci-app-smoke`。
- 未 as-is 复用 current `test` profile。
- CI step 设置 `nq.app.context.smoke.required=true`，因此不是本地 optional skip。
- 未确认 app context 成功启动。
- 未发现 `AuthSeedConfiguration` 执行、admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据；但由于 context startup 失败，不能声明完整 smoke 通过。
- 未发现 OKX / Binance / Bybit / Gate / Coinbase / Kraken 成功访问；Batch 3 no-outbound guard 仍未实现。
- CI logs 仍出现 disposable CI PostgreSQL service connection material / full connection string in service initialization or automatic step environment display；不是真实生产 credential material，但不满足本轮 stricter no JDBC password / full connection string / env dump 验收项。
- Batch 2E 仍 NOT STARTED。
- Batch 3 no-outbound guard 仍 PENDING。
- Batch 4 security guard / secret scan 仍 PENDING。
- Batch 5 frontend E2E hardening 仍 PENDING。
- AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

FAIL / FIRST-RUN-FIX REQUIRED。Batch 2D 不能标记为 FIRST GREEN RUN CONFIRMED，不能标记为 FROZEN / ACCEPTED。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` only。修复范围只允许 `.github/workflows/ci.yml`、`backend/nq-app` test 和 `docs/current` 状态记录；不得混入 Batch 2E、Batch 3-5、LIVE、AI、DH runtime、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2D-IMPL

日期：2026-06-16

### 目标

实现 GateK CI Batch 2D 最小 `nq-app` context smoke。只在 CI PostgreSQL service DB 和已迁移 Flyway schema 上验证 Spring context 可启动；不进入 Batch 2E，不进入 Batch 3-5，不开启 AI / DH runtime / LIVE / RealClient / real provider / real exchange adapter。

### 修改文件

- `.github/workflows/ci.yml`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 实现摘要

- 新增 `NqAppContextPostgresSmokeTest`，使用 `@SpringBootTest(webEnvironment = NONE)`。
- 使用 `@ActiveProfiles("ci-app-smoke")` 和 explicit `nq.app.context.smoke.*` datasource properties；不使用 `local`，不 as-is 复用 current `test` profile。
- 通过 `@EnabledIfSystemProperty(named = "nq.app.context.smoke.required", matches = "true")` 让普通本地 full Maven test 不要求 CI PostgreSQL service；CI step 显式设置 required，缺少 datasource properties 必须失败。
- Context smoke 设置 `spring.flyway.enabled=false`，因为同一 `postgres-flyway` job 已先完成 direct Flyway migrate / validate。
- 显式禁用 bootstrap admin、catalog sync、OKX recovery、OKX WS、Binance WS、scheduler side effects。
- 使用 `MockitoBean` 替换 OKX / Binance adapter 和 WS client，避免真实构造器读取 `.env` 或构造真实 exchange client path，并用 `verifyNoInteractions` 固化不调用 adapter / WS 方法。
- 在 `.github/workflows/ci.yml` 的 `postgres-flyway` job 中，于 Flyway / schema artifacts / 2C repository smoke 后追加 `Run nq-app PostgreSQL context smoke` step；不新增 job，不假设跨 job 共享 DB。

### 边界确认

- 未修改 backend production code。
- 未修改 frontend、research、scripts、deploy。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未使用 `local` profile。
- 未 as-is 复用 current `test` profile。
- 未触发 `AuthSeedConfiguration`。
- 未创建 admin / operator / viewer seed users。
- 未创建 legacy accounts。
- 未创建 exchange accounts。
- 未创建 credential rows。
- 未读取 `.env`。
- 未依赖 GitHub real secrets。
- 未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未调用 adapter methods。
- 未执行 order / cancel / transfer / withdraw / permission probe HTTP。
- 未使用 Testcontainers。
- 未新增 `continue-on-error`、`skipTests`、bare `env`、`printenv` 或 full environment dump。
- Batch 2E 仍 NOT STARTED。
- Batch 3 no-outbound guard 仍 PENDING。
- Batch 4 security guard / secret scan 仍 PENDING。
- Batch 5 frontend E2E hardening 仍 PENDING。
- AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

### 验证状态

- 本地 selected Maven command 已执行：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'
```

- 结果：BUILD SUCCESS；`NqAppContextPostgresSmokeTest` tests=1 / skipped=1。
- Skipped 原因：本地未设置 `nq.app.context.smoke.required=true` 和 CI PostgreSQL datasource properties；这是本地编译 / Surefire selection 验证，不是 CI PostgreSQL context startup 证明。
- CI step 显式设置 `nq.app.context.smoke.required=true`；GitHub Actions 中缺少 datasource properties、context 启动失败或 adapter / WS mock 被调用都会阻塞该 Maven step。
- 本地无法证明 CI PostgreSQL app context smoke，因为缺少 GitHub Actions PostgreSQL service DB 和 `nq.app.context.smoke.*` CI properties。
- 真实 context startup 等待 GitHub Actions first run review。

### Review decision

PASS / IMPLEMENTED / PENDING FIRST CI RUN。不得写成 FROZEN / ACCEPTED。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2D-PLAN

日期：2026-06-15

### 目标

规划 GateK CI Batch 2D 最小 `nq-app` context smoke。只做 planning，不修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy，不实现 2D，不启动 2E，不进入 Batch 3-5。

### Evidence reviewed

- `.github/workflows/ci.yml`：当前 `backend` job 仍有 Batch 1 CI-only seed watcher；`postgres-flyway` job 已冻结 2A / 2B / 2C / 2C-HYGIENE，未启动 app context。
- `backend/pom.xml`、`backend/nq-app/pom.xml`、`backend/nq-infra/pom.xml`：确认 `nq-app` 是 Spring Boot composition root，包含 adapter / scheduler / auth / security / infra 等模块。
- `backend/nq-app/src/main/resources/application*.yml`：确认默认 profile 落到 `local`，`local` 启用 Flyway 与 seed users，`test` Flyway disabled 且仍有 seed users，`freeze` 禁用 catalog sync / OKX recovery / WS 但需 freeze secrets。
- `backend/nq-app/src/test/**`：确认 full app context tests 当前为 `ResearchBacktestHappyPathLocalTest`、`MarketdataControllerLocalIntegrationTest`、`OkxBootstrapNoOutboundLocalContextTest`，均使用 `@ActiveProfiles("local")`。
- `AuthSeedConfiguration`：`@Profile({"local", "test"})` + `ApplicationRunner`，会把 `nq.security.users` seed 到 DB。
- `AuthBootstrapAdminConfiguration`：仅在 `nq.auth.bootstrap-admin.enabled=true` 时运行，2D 必须保持 false。
- `ExchangeAdapterConfiguration`、catalog sync、OKX recovery、WS / scheduled services：2D 必须禁用相关 side effects，不调用 adapter / run-once / controller workflow。
- `AccountModuleConfiguration`：默认 permission probe port 是 `NoRealExchangeCredentialPermissionProbePort`，2D 必须保持 no-real。

### Plan decision

- Batch 2D 当前状态：PLAN ONLY / NOT IMPLEMENTED。
- Future 2D-1 只允许最小 context-load smoke，优先 `webEnvironment = NONE` 或等价非 web context。
- Future 2D 必须使用 CI-only fake profile / explicit properties，不使用 `local` profile，不复用 current `test` profile as-is。
- Future 2D 不插入 legacy seed、auth seed users、exchange accounts、credential rows 或真实 credential material。
- Future 2D 必须显式禁用 bootstrap admin、catalog sync、OKX recovery、OKX WS、Binance WS，并保持 no-real permission probe port。
- Future 2D 不实现 no-outbound guard / secret scan / frontend E2E hardening；这些仍属于 Batch 3 / 4 / 5。
- Future 2D 初始不得直接 required；需 first green + freeze review 后再评估。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 边界确认

- 未修改 `.github/workflows/ci.yml`。
- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未修改 backend production code。
- 未修改 frontend、research、scripts、deploy。
- 未启动 `nq-app` context。
- 未触发 `AuthSeedConfiguration`。
- 未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- 未读取、打印、复制或输出真实 credential material。
- Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### Review decision

PASS / PLAN ONLY / NOT IMPLEMENTED。Batch 2D 已完成规划，不代表 workflow / code / test / migration 已实现。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2D-PLAN-REVIEW` 或 2D plan fix。不得直接进入 2D implementation、2E implementation、Batch 3-5 implementation、AI、DH runtime、LIVE、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FREEZE-REVIEW

日期：2026-06-15

### 目标

冻结 `NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX`，确认它成为当前 Batch 2C CI log hygiene baseline。本轮只做 freeze review 与允许的 `docs/current` 状态同步，不修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy，不进入 Batch 2D / 2E，不进入 Batch 3-5。

### Evidence reviewed

- GitHub Actions run `27550583713`，workflow `NQ CI Baseline`，branch `dev`，commit `bcc751e7a7f4f6a60ccb877603cfbf809d55b632`，status `completed`，conclusion `success`。
- Job `PostgreSQL / Flyway smoke` / `81435457348` completed / success。
- Steps `Initialize containers`、`Mask CI-only PostgreSQL connection values`、`Run empty database Flyway smoke`、`Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts`、`Run repository PostgreSQL smoke` all completed / success。
- GitHub MCP decoded log 显示后续 step env 中 `NQ_FLYWAY_DB_URL`、`NQ_FLYWAY_DB_USER`、`NQ_FLYWAY_DB_PASSWORD` 均为 `***` 或未直接打印。
- Repository smoke log 显示 `JdbcRepositoryPostgresSmokeTest` Surefire summary 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。
- `gh run view --log --job 81435457348` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；本轮使用 GitHub MCP decoded logs 复核 masking / repository smoke evidence。可信度：高，因 `gh` run / job metadata、GitHub MCP job / step / log 一致。

### Freeze decision

- `2C-HYGIENE-FIX` 已实现并通过 first green run review。
- 后续 step log 可见性已降低。
- Service container initialization 和 masking step automatic `env:` display 的 CI fake value visibility 为 accepted P2 residual。
- 未发现真实 credential material。
- P0/P1 为 0。
- 结论：PASS / FROZEN / ACCEPTED。

### 边界确认

- 未修改 `.github/workflows/ci.yml`。
- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未修改 backend production code。
- 未修改 frontend、research、scripts、deploy。
- 未启动 `nq-app` context。
- 未使用 `@SpringBootTest`。
- 未触发 `AuthSeedConfiguration`。
- 未新增 `printenv` / bare `env` / full environment dump。
- 未新增 `continue-on-error`。
- 未新增 `skipTests` 或 soft-fail。
- 未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- Batch 2C 保持 FROZEN / ACCEPTED；Batch 2D / 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PASS / FROZEN / ACCEPTED。`2C-HYGIENE-FIX` 冻结为当前 Batch 2C CI log hygiene baseline。P0/P1 为 0；P2 residual accepted，不是真实 credential leakage。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2D-PLAN`、`NQ-CI-POSTGRES-FLYWAY-2E-PLAN` 或 Batch 3 pre-planning。不得直接进入 Batch 2D/2E implementation、Batch 3-5 implementation、AI、DH runtime、LIVE、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIRST-RUN-REVIEW

日期：2026-06-15

### 目标

评审包含 `NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX` 的 GitHub Actions run，确认 `postgres-flyway` job 仍 green，Flyway / schema artifact / repository smoke 未回归，并判断 CI-only PostgreSQL URL / user / password 在后续 step logs 中是否被 mask 或不再直接显示。本轮只做 CI log hygiene review 与允许的 `docs/current` 状态同步，不进入 Batch 2D / 2E，不进入 Batch 3-5。

### Evidence reviewed

- GitHub Actions run `27550583713`，workflow `NQ CI Baseline`，branch `dev`，commit `bcc751e7a7f4f6a60ccb877603cfbf809d55b632`，status `completed`，conclusion `success`。
- Job `PostgreSQL / Flyway smoke` / `81435457348` completed / success。
- Steps `Initialize containers`、`Mask CI-only PostgreSQL connection values`、`Run empty database Flyway smoke`、`Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts`、`Run repository PostgreSQL smoke` all completed / success。
- Repository smoke log 显示 `JdbcRepositoryPostgresSmokeTest` Surefire summary 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。
- Artifact `nq-postgres-flyway-schema-artifacts` / id `7639914125` metadata：size `74668` bytes，digest `sha256:f12207d6a9f305ce42726110a65cb8c7d99f166008167c552f786425de5e46a0`，expires `2026-06-29T13:45:04Z`。
- `gh run view --log` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；本轮改用 GitHub MCP decoded logs。可信度：高，因 `gh` run metadata、GitHub MCP job / step / log 和 artifact metadata 一致。

### Masking review

- Step `Mask CI-only PostgreSQL connection values` executed successfully。
- Masking step 之后的后续 step env 中，`NQ_FLYWAY_DB_URL`、`NQ_FLYWAY_DB_USER`、`NQ_FLYWAY_DB_PASSWORD` 显示为 `***` 或不再直接打印。
- GitHub service container 初始化发生在 job steps 之前，因此 Docker / service env output 仍可能显示 disposable CI-only fake DB values。
- Masking step 自身的 automatic `env:` display 也可能在 masking 生效前显示 disposable CI-only fake DB values。
- 结论：后续 step log 可见性已降低；剩余 service-level / masking-step-env display 仅为 accepted P2 hygiene residual，不是真实 credential material。

### 边界确认

- 未修改 `.github/workflows/ci.yml`。
- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未修改 backend production code。
- 未修改 frontend、research、scripts、deploy。
- 未启动 `nq-app` context。
- 未使用 `@SpringBootTest`。
- 未触发 `AuthSeedConfiguration`。
- 未新增 `printenv` / bare `env` / full environment dump。
- 未新增 `continue-on-error`。
- 未新增 `skipTests` 或 soft-fail。
- 未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- Batch 2C 保持 FROZEN / ACCEPTED；Batch 2D / 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PASS / FIRST GREEN RUN CONFIRMED。`2C-HYGIENE-FIX` 不破坏 CI；Flyway empty DB V1-V31 smoke、schema artifact generation / check / upload、repository PostgreSQL smoke 均未回归。后续 step log 对 CI-only DB connection values 的可见性已降低；service container 初始化和 masking step automatic `env:` display 的 CI fake value 可见性记录为 accepted P2 residual，不升级为 P1/P0。

### 下一步

Freeze follow-up：已由 `NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FREEZE-REVIEW` 关闭为 PASS / FROZEN / ACCEPTED。当前下一步为 `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`、`NQ-CI-POSTGRES-FLYWAY-2E-PLAN` 或 Batch 3 pre-planning。不得直接进入 Batch 2D/2E implementation、Batch 3-5 implementation、AI、DH runtime、LIVE、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX

日期：2026-06-15

### 目标

只处理 GateK CI Batch 2C P2 log hygiene：降低 GitHub Actions logs 中 CI-only PostgreSQL URL / user / password 的可见性。Batch 2C repository-only real PostgreSQL smoke 保持 FROZEN / ACCEPTED；Batch 2D / 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### 修改范围

- `.github/workflows/ci.yml`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 实现摘要

- 在 `postgres-flyway` job 的第一个 step 增加 `Mask CI-only PostgreSQL connection values`。
- 通过 GitHub Actions `::add-mask::` 注册 `NQ_FLYWAY_DB_URL`、`NQ_FLYWAY_DB_USER`、`NQ_FLYWAY_DB_PASSWORD`。
- 不 echo 原始值，不引入 GitHub secret store，不改变 disposable CI service DB values。
- 不修改 Flyway smoke、schema artifact generation、artifact redaction checks、artifact upload 或 repository smoke Maven command。

### CI masking review

- `::add-mask::` 对 masking step 之后的 job logs 生效。
- GitHub service container 初始化发生在 job steps 之前；如 service command output 仍显示 `nq_ci` / `nq_ci_user` / `nq_ci_password`，记录为 GitHub Actions service-level CI fake value exposure。
- 这些值是 disposable CI fake service DB values，不是真实 credential material，不是 production DB credentials；本轮不升级为 P1/P0。

### 边界确认

- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未修改 backend production code。
- 未修改 frontend、research、scripts、deploy。
- 未启动 `nq-app` context，未使用 `@SpringBootTest`，未触发 `AuthSeedConfiguration`。
- 未使用 `continue-on-error`，未允许 skip / soft-fail。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- 未读取、打印、复制或输出真实 credential material。

### 下一步

Hygiene follow-up：first-run review 和 freeze review 已关闭。当前下一步为 `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`、`NQ-CI-POSTGRES-FLYWAY-2E-PLAN` 或 Batch 3 pre-planning。不得直接进入 Batch 3-5、AI、DH runtime、LIVE、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-FREEZE-REVIEW

日期：2026-06-15

### 目标

冻结 GateK CI Batch 2C repository-only real PostgreSQL smoke baseline，确认它成为当前 `dev` CI 的 repository real DB 最小验证基线。本轮只允许文档同步，不修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy。预检时工作区已有非本轮 `backend/nq-auth/src/main/java/com/guidinglight/nexusquant/auth/application/DbAuthService.java` import 排序 diff，本轮未触碰该文件。

### Evidence reviewed

- GitHub Actions run `27535619157`，workflow `NQ CI Baseline`，branch `dev`，commit `9adb71b8dc56a0bf881952da918ebaab5fdbeb7f`，status `completed`，conclusion `success`。
- Job `PostgreSQL / Flyway smoke` / `81384164182` completed / success。
- Steps `Run empty database Flyway smoke`、`Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts`、`Run repository PostgreSQL smoke` all completed / success。
- GitHub MCP decoded log 显示 `postgres:16` service reached `healthy`，checkout commit 为 `9adb71b8dc56a0bf881952da918ebaab5fdbeb7f`。
- Repository smoke log 显示 `JdbcRepositoryPostgresSmokeTest` Surefire summary 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。
- Artifact `nq-postgres-flyway-schema-artifacts` / id `7633555246` metadata：size `74655` bytes，digest `sha256:f303e6d26410ae759778ea26f2b42503d42c952c9b0905739d51dcd717f89c3b`，expires `2026-06-29T09:05:23Z`。
- 下载 artifact 复核：ZIP 恰含 `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`。
- `flyway-info.txt` 共 31 rows，首行为 `V1__init.sql`，末行为 `V31__schema_credential_permission_probe.sql`，全部 success。
- `schema-dump.sql` 中 `INSERT INTO`、`COPY ... FROM stdin`、`-- Data for Name:` 均为 0；artifact high-risk credential / raw request / raw response assignment pattern 为 0。

### P2 log hygiene

- GitHub Actions 自动 step env / service command output 显示 CI-only PostgreSQL URL / user / password。
- 这些值是 disposable CI-only fake service DB values，不是真实 credential material，不是 production DB credential。
- 结论：Accepted P2，不阻塞 Batch 2C freeze。
- `NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX` 已增加 masking step，并已由 first-run review + freeze review 固化为 FROZEN / ACCEPTED。不得混入 Batch 2D/2E、生产代码、真实 credential flow、真实交易所、LIVE、AI 或 DH runtime。

### Boundary confirmation

- 未修改 `.github/workflows/ci.yml`。
- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未修改 backend production code；预先存在的 `DbAuthService.java` import diff 不属于本轮。
- 未修改 frontend、research、scripts、deploy。
- 未启动 `nq-app` full context。
- 未使用 `@SpringBootTest`。
- 未触发 `AuthSeedConfiguration`。
- 未复用 Batch 1 CI-only seed watcher；backend job 既有 watcher 仍只属于 Batch 1 compatibility，不属于 2C repository smoke。
- 未插入 legacy seed。
- 未纳入 credential repository。
- 仅使用 `ci-repo-smoke-*` fake fixture，并通过 transaction rollback 隔离数据。
- 未打印真实 credential material。
- 未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- Batch 2D / 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PASS / FROZEN / ACCEPTED。Batch 2C 冻结为当前 `dev` repository-only real PostgreSQL smoke baseline。P0/P1 为 0；P2 log hygiene accepted，不阻塞 freeze。

### 下一步

Hygiene follow-up：first-run review 和 freeze review 已关闭。当前下一步为 `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`、`NQ-CI-POSTGRES-FLYWAY-2E-PLAN` 或 Batch 3 pre-planning。不得直接进入真实交易所、LIVE、AI、DH runtime、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-FIRST-RUN-REVIEW

日期：2026-06-15

### 目标

评审包含 Batch 2C repository-only real PostgreSQL smoke 的 GitHub Actions 首次运行结果，确认 CI service PostgreSQL 上真实执行并通过。本轮只做 CI run / job / log / artifact / boundary review，并同步允许的 `docs/current` 文档；不修改 workflow、生产代码、测试代码、migration、frontend、research、scripts 或 deploy。

### Evidence reviewed

- GitHub Actions run `27535619157`，workflow `NQ CI Baseline`，branch `dev`，commit `9adb71b8dc56a0bf881952da918ebaab5fdbeb7f`，status `completed`，conclusion `success`。
- Job `PostgreSQL / Flyway smoke` / `81384164182` completed / success。
- Step `Run empty database Flyway smoke` success；artifact `flyway-info.txt` 复核 V1-V31 共 31 条 migration row，首版本 `1`，末版本 `31`，全部 success。
- Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` success；artifact `nq-postgres-flyway-schema-artifacts` / id `7633555246` 下载复核通过。
- Step `Run repository PostgreSQL smoke` success；job log 显示 `JdbcRepositoryPostgresSmokeTest` Surefire summary 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。
- Artifact ZIP 恰含 `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`；`schema-dump.sql` 无 data row marker；artifact high-risk credential pattern count 为 0。
- P2 log hygiene finding：GitHub Actions 自动 step env / service command output 会显示 CI-only PostgreSQL URL / user / password；未发现真实 credential material，但 freeze review 前需决定是否收口该日志暴露。

### Boundary confirmation

- 未启动 `nq-app` full context。
- 未使用 `@SpringBootTest`。
- 未触发 `AuthSeedConfiguration`。
- 未复用 Batch 1 CI-only seed watcher；backend job 既有 watcher 仍只属于 Batch 1 compatibility，不属于 2C repository smoke。
- 未插入 legacy seed。
- 未纳入 credential repository。
- 未修改 migration、frontend、research、scripts 或 deploy。
- 未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- 未读取、打印、复制或输出真实 credential material。
- Batch 2D / 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PASS / ACCEPTED FOR FIRST GREEN RUN。Batch 2C 当时状态为 IMPLEMENTED / FIRST GREEN RUN CONFIRMED；后续已由 `NQ-CI-POSTGRES-FLYWAY-2C-FREEZE-REVIEW` 冻结为 FROZEN / ACCEPTED，P2 log hygiene finding 已由 `NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX` / first-run review / freeze review 收口为 accepted P2 residual.

### 下一步

Follow-up：Batch 2C freeze review and hygiene freeze review are now closed. Current next action is `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`, `NQ-CI-POSTGRES-FLYWAY-2E-PLAN`, or Batch 3 pre-planning。不得直接进入 Batch 3-5、AI、DH runtime、LIVE、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-IMPL

日期：2026-06-15

### 目标

实现 GateK CI Batch 2C repository-only real PostgreSQL smoke 的最小基线。只验证少量 `nq-infra` 纯 JDBC repository 在 Flyway 迁移完成后的真实 PostgreSQL schema 上可执行 insert/read/upsert；不启动 `nq-app` full context，不触发 `AuthSeedConfiguration`，不复用 Batch 1 CI-only seed watcher，不接真实交易所。

### 修改文件

- `.github/workflows/ci.yml`
- `backend/nq-infra/pom.xml`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/JdbcRepositoryPostgresSmokeTest.java`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 实现摘要

- 在 `nq-infra` 新增 `JdbcRepositoryPostgresSmokeTest`，通过 system properties 接收 CI PostgreSQL service DB 连接信息。
- 测试默认无 `nq.postgres.smoke.*` properties 时 disabled，避免普通本地 `mvn test` 要求 Docker / PostgreSQL；CI step 设置 `nq.postgres.smoke.required=true`，缺失连接信息或 repository 失败必须阻塞 job。
- Smoke 选择低风险 repository：
  - `JdbcAuditLogRepository`：验证 `audit_logs` insert 与 JSONB detail readback。
  - `JdbcRiskEventRepository`：验证 `risk_events` insert 与 decision / severity / reason readback。
  - `JdbcMarketdataBarRepository`：验证 `marketdata_bars` `ON CONFLICT` insert/update、TIMESTAMPTZ、quoted `"interval"`、JSONB payload readback。
- 所有 fixture 使用 `ci-repo-smoke-*` fake value，并在 `TransactionTemplate` 中 `setRollbackOnly()`；不生成 data dump artifact，不上传业务数据。
- `postgres-flyway` job 保持同一 PostgreSQL service 生命周期：先 Flyway migrate / validate，再生成并上传 2B schema-only artifacts，最后执行 repository smoke，避免 smoke rows 污染 schema artifacts。
- `backend/nq-infra/pom.xml` 仅新增 test-scope `org.postgresql:postgresql` JDBC driver，不新增生产依赖。

### 验证记录

- 首次 PowerShell 本地 Maven 命令因未引用带点号的 `-D` property 失败，Maven 报 `Unknown lifecycle phase ".failIfNoSpecifiedTests=false"`；这是命令转义问题，非代码编译失败。
- 复跑命令通过：

```powershell
mvn -f backend/pom.xml -pl nq-infra -am test -Dtest=JdbcRepositoryPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'
```

- 结果：BUILD SUCCESS；`JdbcRepositoryPostgresSmokeTest` Tests run: 1, Failures: 0, Errors: 0, Skipped: 1。Skipped 原因是本地未提供 `nq.postgres.smoke.*` properties；CI step 会设置 `nq.postgres.smoke.required=true`，不能 skip / soft-fail。
- 本地未完整复刻 GitHub Actions PostgreSQL service-container 真 DB 执行；first CI run review 仍 pending。

### 边界确认

- 未启动 `nq-app` full context。
- 未使用 `@SpringBootTest`。
- 未触发 `AuthSeedConfiguration`。
- 未使用 `local` / `test` profile 触发 app runner。
- 未复用 Batch 1 CI-only seed watcher。
- 未新增 legacy account seed。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未修改 backend production code。
- 未修改 frontend、research、scripts、deploy。
- 未实现 Batch 2D / 2E。
- 未实现 Batch 3 no-outbound guard。
- 未实现 Batch 4 security guard / secret scan。
- 未实现 Batch 5 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- 未读取、打印、复制或输出真实 credential material。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2C-FIRST-RUN-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2C-FIRST-RUN-FIX`。不得直接进入 2D app context smoke、2E seed watcher cleanup、Batch 3-5、AI、DH runtime、LIVE、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-PLAN-REVIEW

日期：2026-06-15

### 目标

评审 `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md` 是否可作为 GateK CI Batch 2C repository real PostgreSQL smoke implementation baseline。本轮 review-only，只允许同步 `docs/current` 文档，不修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy。

### Evidence reviewed

- `NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md` 覆盖 repository test inventory、Batch 2C-1 / 2C-2 / 2C-3 切片、seed / fixture、transaction / cleanup、security boundary、batch boundary 和 rollback。
- `.github/workflows/ci.yml` 仍只有 Batch 1 backend PostgreSQL service + CI-only legacy `accounts` seed watcher，以及 Batch 2A/2B `postgres-flyway` job；本轮未修改 workflow。
- `nq-infra` repository tests 主要使用 `RecordingJdbcTemplate` / `RecordingNamedParameterJdbcTemplate` / `Mockito.mock(JdbcTemplate)`，与 plan 中“Recording / mock 不等于 real PostgreSQL smoke”的判断一致。
- `nq-app` local context tests 使用 `@SpringBootTest` + `@ActiveProfiles("local")`，且 `ResearchBacktestHappyPathLocalTest` 依赖 legacy `accounts` row；这些属于 2D，不属于 2C。
- `AuthSeedConfiguration` 是 `local` / `test` profile 的 `ApplicationRunner`，验证了 2C plan 中避免 `nq-app` context / runner / seed 的必要性。
- `JdbcExchangeAccountCredentialRepository` 涉及 `pgp_sym_encrypt` / `pgp_sym_decrypt` / credential material shape，验证了 credential repository 推迟到 2C-2+ 并要求 fake material、脱敏和 cleanup 单独评审的必要性。
- audit log、risk event、event store、marketdata bars 均为合理 2C-1 候选：它们能覆盖 JSONB、insert、`ON CONFLICT`、timestamp 和 quoted `"interval"` 等 PostgreSQL-specific 行为，同时不需要 app context 或 exchange adapter。

### Review result

- P0/P1：0。
- Batch 2C plan 接受为 FROZEN / ACCEPTED implementation baseline。
- Batch 2C implementation remains NOT STARTED。
- Batch 2D / 2E remain NOT STARTED。
- Batch 3-5 remain PENDING。
- AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

### Boundary confirmation

- 未修改 `.github/workflows/ci.yml`。
- 未修改 backend、frontend、research、scripts、deploy。
- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未实现 repository real PostgreSQL smoke。
- 未启动 `nq-app` context，未触发 `AuthSeedConfiguration`。
- 未插入 legacy account seed、test fixture seed、real account seed 或 real exchange seed。
- 未引入 Testcontainers。
- 未实现 Batch 2D / 2E。
- 未实现 Batch 3 no-outbound guard。
- 未实现 Batch 4 security guard / secret scan。
- 未实现 Batch 5 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- 未调用 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未读取、打印、复制或输出真实 credential material。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2C-IMPL`, `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-FIX`, or separate 2D / 2E / Batch 3 pre-planning。不得直接进入真实交易所、LIVE、AI、DH runtime、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2C-PLAN

日期：2026-06-15

### 目标

规划 Batch 2C 如何在 GitHub Actions PostgreSQL service 上增加最小 repository real PostgreSQL smoke。本轮只做 planning-only，不修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy。

### Evidence reviewed

- `.github/workflows/ci.yml` 当前 `backend` job 使用 PostgreSQL service 和 CI-only legacy `accounts` seed watcher；这是 Batch 1 runner dependency workaround，不作为 2C repository smoke seed 策略。
- `.github/workflows/ci.yml` 当前 `postgres-flyway` job 已覆盖 2A empty DB Flyway V1-V31 migration smoke 和 2B schema artifact generation / upload；不启动 app context、不插入 seed、不跑 repository real DB smoke。
- `backend/pom.xml` 是 Java 21 Maven multi-module parent；`nq-app` 依赖 Flyway / PostgreSQL runtime；`nq-infra` 依赖 `spring-jdbc` / Flyway / JUnit / Mockito。
- `application-local.yml` 启用 PostgreSQL datasource + Flyway；`application-test.yml` 使用 PostgreSQL placeholder 但 Flyway disabled；`AuthSeedConfiguration` 仅在 `local` / `test` profile 通过 `ApplicationRunner` seed users。
- `nq-infra` repository 单测主要使用 `RecordingJdbcTemplate` / `RecordingNamedParameterJdbcTemplate` / `Mockito.mock(JdbcTemplate)`，证明 SQL shape 和参数，不证明真实 PostgreSQL 执行。
- `nq-app` 的 `MarketdataControllerLocalIntegrationTest`、`ResearchBacktestHappyPathLocalTest`、`OkxBootstrapNoOutboundLocalContextTest` 使用 `@SpringBootTest` + `@ActiveProfiles("local")`，应划给 Batch 2D，不纳入 2C。

### Planning result

- 新增 `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`，状态固定为 `PLAN ONLY / READY FOR REVIEW / NOT IMPLEMENTED`。
- Batch 2C-1 建议新增独立 repository-only PostgreSQL smoke，优先低风险 `nq-infra` 纯 JDBC repository：audit log、risk event、event store、marketdata bars。
- Batch 2C-2 才评估 strategy / paper / ledger / account / credential repository 扩展；credential repository 需 fake material、redaction 和 cleanup 单独评审。
- Batch 2C-3 才评估是否转 required check。
- Seed 策略固定为默认 no legacy account seed，不复用 Batch 1 seed watcher，不触发 `AuthSeedConfiguration`；必要 fixture 只能是 CI-only fake rows 并 rollback / cleanup。
- Transaction / cleanup 策略固定为优先事务 rollback，必要时按唯一 test id 显式 cleanup；不污染 2A/2B schema artifacts，不运行 Flyway `clean`。
- Rollback 策略固定为只回滚 2C job 或 smoke invocation，不影响 2A/2B。

### Boundary confirmation

- 未修改 `.github/workflows/ci.yml`。
- 未修改 backend、frontend、research、scripts、deploy。
- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration，未修改历史 migration。
- 未启动 `nq-app` context，未触发 `AuthSeedConfiguration`。
- 未插入 legacy account seed、test fixture seed、real account seed 或 real exchange seed。
- 未实现 repository real PostgreSQL smoke。
- 未实现 Batch 2D / 2E。
- 未实现 Batch 3 no-outbound guard。
- 未实现 Batch 4 security guard / secret scan。
- 未实现 Batch 5 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- 未调用 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未读取、打印、复制或输出真实 credential material。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PLAN READY FOR REVIEW。Batch 2C remains NOT IMPLEMENTED；Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2C-PLAN-REVIEW` or `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-FIX`。不得直接进入 2C implementation、2D app context smoke、2E seed watcher cleanup、Batch 3-5、AI、DH runtime、LIVE、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW

日期：2026-06-15

### 目标

冻结 Batch 2B PostgreSQL / Flyway schema artifact baseline，确认它成为当前 `dev` CI 的 schema artifact 最小验证基线。本轮只允许文档同步，不修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy。

### Evidence reviewed

- GitHub Actions run `27521750442` latest attempt jobs all completed / success。
- Job `PostgreSQL / Flyway smoke` / `81340926116` completed / success。
- Steps `Run empty database Flyway smoke`、`Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` all completed / success。
- Job log confirmed disposable PostgreSQL `postgres:16` service and direct Flyway empty DB smoke reached V31。
- Artifact `nq-postgres-flyway-schema-artifacts` / id `7628309014` metadata: size `74662` bytes, digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5`, `expired=false`, `expires_at=2026-06-29T03:14:04Z`。
- In-memory artifact ZIP review confirmed exactly 7 required files: `flyway-info.txt`, `schema-tables.txt`, `schema-columns.txt`, `schema-constraints.txt`, `schema-indexes.txt`, `schema-comments.txt`, `schema-dump.sql`。
- Artifact review found no missing / extra / empty file；`flyway-info.txt` covers V1-V31；`schema-dump.sql` data-row marker count is `0`；high-risk credential / raw request / raw response pattern count is `0`。

### Boundary confirmation

- 未修改 `.github/workflows/ci.yml`。
- 未修改 backend、frontend、research、scripts、deploy。
- 未新增 API，未新增 migration，未修改历史 migration。
- 未修改 Java / TypeScript / Python / test code。
- 未启动 `nq-app` context。
- 未运行 repository real PostgreSQL smoke。
- 未插入 seed，未修改 CI-only seed watcher。
- 未启用 Testcontainers。
- 未实现 Batch 3 no-outbound guard。
- 未实现 Batch 4 security guard / secret scan。
- 未实现 Batch 5 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、real provider 或 real exchange adapter。
- 未调用真实交易所，未读取、打印、复制或输出真实 credential material。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### Review decision

PASS / FROZEN / ACCEPTED。Batch 2B 已冻结为当前 `dev` 的 PostgreSQL / Flyway schema artifact minimal baseline。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2C-PLAN`，`NQ-CI-POSTGRES-FLYWAY-2B-FIX` only if a later regression is found，or Batch 3 pre-planning。不得直接进入真实交易所、LIVE、AI、DH runtime、RealClient、real provider 或 real exchange adapter。

## NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW

日期：2026-06-15

### 目标

评审包含 Batch 2B schema artifact generation / upload 变更的 GitHub Actions 首次运行结果，判断是否可将 Batch 2B 标记为 first green run confirmed。本轮只做 CI run / job / artifact / redaction / retention 证据复核，并同步允许的 `docs/current` 文档；不修改 `.github/workflows/ci.yml`，不改业务代码、测试代码、migration、frontend、research、scripts 或 deploy。

### Run evidence

- GitHub Actions run `27521750442`，workflow `NQ CI Baseline`，branch `dev`，commit `c62ebddd5a522bbdf72bc018064b9eb36d8fe9e1`，status `completed`，conclusion `success`。
- Job `PostgreSQL / Flyway smoke` / `81340926116` completed / success。
- Step `Run empty database Flyway smoke` success；job log 显示 `Flyway empty database smoke reached V31`。
- Step `Generate PostgreSQL schema artifacts` success。
- Step `Check PostgreSQL schema artifacts` success。
- Step `Upload PostgreSQL schema artifacts` success；log 显示 7 files uploaded。

### Artifact evidence

- Artifact `nq-postgres-flyway-schema-artifacts` / id `7628309014` uploaded successfully。
- Artifact metadata：size `74662` bytes；digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5`；created `2026-06-15T03:14:05Z`；expires `2026-06-29T03:14:04Z`，符合 `dev` push 14-day retention。
- Downloaded ZIP contained exactly 7 required files：`flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`。
- 本地 artifact 检查：无 missing / extra / empty file；`schema-dump.sql` data-row pattern 命中数为 0；high-risk credential / raw request / raw response pattern 命中数为 0。

### Boundary confirmation

- 未启动 `nq-app` context。
- 未跑 repository real PostgreSQL smoke。
- 未插入 seed，未修改 CI-only seed watcher。
- 未启用 Testcontainers。
- 未实现 no-outbound guard / secret scan / frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient、real provider 或 real exchange adapter。
- Batch 2C / 2D / 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### Review decision

PASS / ACCEPTED FOR FIRST GREEN RUN。Batch 2B 当前状态为 IMPLEMENTED / FIRST GREEN RUN CONFIRMED；尚未 freeze / accepted。

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2C-PLAN`。

## NQ-CI-POSTGRES-FLYWAY-2B-IMPL

日期：2026-06-15

### 本轮目标

在既有 `postgres-flyway` empty DB migration smoke 基础上实现 Batch 2B schema artifact generation / upload。本轮只改 `.github/workflows/ci.yml` 和允许的 `docs/current` 文档，不改 Java / TypeScript / Python 代码，不改测试，不新增 API，不新增或修改 migration，不改 backend 生产逻辑、frontend、research、scripts 或 deploy。

### 修改文件

- `.github/workflows/ci.yml`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 实现摘要

- 在 `postgres-flyway` job 中安装 PostgreSQL client，用于 `psql` / `pg_dump`。
- 在 Flyway `migrate` + `validate` 之后生成 `artifacts/postgres-flyway/`：
  - `flyway-info.txt`
  - `schema-tables.txt`
  - `schema-columns.txt`
  - `schema-constraints.txt`
  - `schema-indexes.txt`
  - `schema-comments.txt`
  - `schema-dump.sql`
- Artifact 查询只读取 `flyway_schema_history`、`information_schema`、`pg_catalog` / `pg_indexes` metadata 和 table / column comments，不查询业务表 row values。
- `schema-dump.sql` 使用 `pg_dump --schema-only --no-owner --no-privileges`，并通过阻塞式检查拒绝 `INSERT`、`COPY ... FROM stdin` 和 data dump marker。
- 增加 artifact redaction check，阻塞 high-risk credential material pattern；不执行 `env` / `printenv`，不输出 full environment。
- 使用 `actions/upload-artifact@v4` 上传 `nq-postgres-flyway-schema-artifacts`；PR / branch retention 7 days，`dev` push retention 14 days。
- `postgres-flyway` job 继续 blocking；未使用 `continue-on-error`，未 soft-fail artifact 生成或检查。

### 边界确认

- 未启动 `nq-app` full context。
- 未运行 repository real PostgreSQL smoke。
- 未插入 legacy account seed、test fixture seed、real account seed 或 real exchange seed。
- 未启用 Testcontainers。
- 未实现 Batch 3 no-outbound guard。
- 未实现 Batch 4 gitleaks / secret scan。
- 未实现 Batch 5 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken adapter。
- 未调用真实交易所，未读取、打印、复制或输出真实 credential material。

### 验证记录

- `git status --short`：已执行。
- `git diff --check`：已执行。
- `git diff --stat`：已执行。
- `git diff -- backend`、`git diff -- frontend`、`git diff -- research`、`git diff -- scripts`、`git diff -- deploy`、`git diff -- backend/**/db/migration`：已执行；禁止范围 diff 为空。
- `rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current`：已执行；workflow 不新增 skip / soft-fail / LIVE / real exchange 行为，docs 命中仅为禁止说明、历史记录或边界说明。
- 已检查 artifact generation 不使用 `env` / `printenv`，`psql` 不使用 JDBC URL，`pg_dump` 包含 `--schema-only --no-owner --no-privileges`。
- 本地未运行 GitHub Actions PostgreSQL service container，未实际上传 artifact；first CI run 仍 pending。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW`。如 first run 失败，只能进入 `NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-FIX`，不得混入 Batch 2C/2D/2E、Batch 3-5、LIVE、AI、DH runtime、RealClient、real provider 或真实交易所路径。

---

## NQ-CI-POSTGRES-FLYWAY-2B-PLAN

日期：2026-06-14

### 本轮目标

规划 Batch 2B 如何在既有 `postgres-flyway` empty DB migration smoke 基础上增加 schema artifact / docs review 能力。本轮只做方案，不修改 workflow，不改 Java / TypeScript / Python 代码，不改测试，不新增 API，不新增或修改 migration，不改 frontend、research、scripts 或 deploy。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 计划摘要

- 新增 Batch 2B planning-only 文档，状态固定为 `PLAN ONLY / NOT IMPLEMENTED`。
- 规划 artifact：`flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`，以及 optional schema-only `schema-dump.sql`。
- 规划 generation source：Batch 2A `postgres-flyway` empty DB 在 Flyway migrate + validate 后，通过 `information_schema`、`pg_catalog`、`pg_indexes` 和 `flyway_schema_history` 导出 metadata。
- 规划 retention：PR / branch review 默认 7 days，`dev` push 默认 14 days，必要时再评审提升到 30 days。
- 规划 redaction：artifact 不得包含 `.env`、API key、secret、passphrase、token、cookie、private key、credential material、raw request / response 或 data rows。
- 规划 `DB_SCHEMA.md` drift review checklist：2B 第一阶段只做人工 review，不直接脚本化 blocking。
- 明确 2B 不跑 repository real DB smoke，不启动 `nq-app` context，不改 CI-only seed watcher，不启用 Testcontainers。

### 边界确认

- 未修改 `.github/workflows/ci.yml`，未新增 workflow。
- 未修改 backend / frontend / research / scripts / deploy。
- 未新增 API，未新增 migration，未修改历史 migration。
- 未修改 Java / TypeScript / Python / test code。
- 未启动 `nq-app` full context。
- 未运行 repository real PostgreSQL smoke、frontend E2E 或 Testcontainers。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未读取、打印、复制或输出真实 credential material。
- Batch 2B 是 PLAN ONLY / NOT IMPLEMENTED；Batch 2C/2D/2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2B-PLAN-REVIEW`，或评审接受后进入 `NQ-CI-POSTGRES-FLYWAY-2B-IMPL`。

---

## NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW

日期：2026-06-14

### 本轮目标

冻结 Batch 2A PostgreSQL / Flyway empty DB migration smoke baseline，确认它成为当前 `dev` CI 的 PostgreSQL/Flyway 最小验证基线。本轮只允许文档同步，不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。

### 同步与冲突处理

- 已先执行 `git status --short` 和 `git branch --show-current`，确认在 `dev` 分支且存在 5 个 first-run review 文档变更。
- 已使用 `git stash push -u -m "wip: postgres flyway 2a first-run review docs"` 保存本地文档变更。
- 已执行 `git fetch origin`、`git switch dev`、`git pull --ff-only origin dev`，快进到前端 PR #1 合并后的 `origin/dev`。
- 用户提示 PR #2 也已合并后，已再次执行 `git fetch origin` 与 `git pull --ff-only origin dev`；本地 `dev` 确认为 `ea38f79d feat(frontend): 登录页 + 异常页 (B0.1) (#2)`。
- 已执行 `git stash pop`，`docs/current/TESTING.md` 与 `docs/current/WORKLOG.md` 自动合并成功，无冲突标记。
- 已保留前端 B0 / B0.1 文档事实源和 PostgreSQL/Flyway 2A first-run review 事实源。

### First-run commit

- Commit: `docs(gatek): confirm PostgreSQL Flyway CI first green run`
- Files:
  - `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
  - `docs/current/NQ_CI_BASELINE_PLAN.md`
  - `docs/current/README.md`
  - `docs/current/TESTING.md`
  - `docs/current/WORKLOG.md`

### Freeze review summary

- Batch 2A implementation 已完成。
- GitHub Actions run `27501253175` completed / success。
- `postgres-flyway` job completed / success。
- Empty DB migration smoke 从 V1 跑到 V31。
- Flyway validate 31 migrations 成功。
- 未使用 `baselineOnMigrate` 绕过；未运行 Flyway `clean`。
- 未插入 legacy account seed、test fixture seed、real account seed 或 real exchange seed。
- 未启动 `nq-app` full context，未触发 `AuthSeedConfiguration`。
- 未跑 repository real DB smoke、frontend E2E 或 Testcontainers。
- 未注入真实交易所 credential，未开启 LIVE，未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- Batch 2B/2C/2D/2E 仍 NOT STARTED；Batch 3 no-outbound、Batch 4 security scan、Batch 5 frontend E2E hardening 仍 PENDING。

### Review decision

PASS / FROZEN / ACCEPTED。Batch 2A 已冻结为当前 `dev` 的 PostgreSQL / Flyway empty DB migration smoke baseline。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2B-PLAN`。

---

## NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW

日期：2026-06-14

### 本轮目标

只评审 GitHub Actions `postgres-flyway` 首次运行结果，判断 Batch 2A 是否可以冻结为 PostgreSQL / Flyway empty DB migration smoke baseline。本轮不修改 workflow，不修改 Java / TypeScript / Python / 测试代码，不新增 API，不新增或修改 migration，不进入 Batch 2B/2C/2D/2E 或 Batch 3-5。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### GitHub Actions run summary

- Run: `27501253175`
- Workflow: `NQ CI Baseline`
- Branch / commit: `dev` / `7836640ebae46d6fc62771611f5215661b3267dc`
- Status / conclusion: completed / success
- Jobs: `Diff check`、`Backend Maven test`、`PostgreSQL / Flyway smoke`、`Frontend build`、`Research quality gate` all completed / success
- Artifacts: none；Batch 2B schema artifacts 仍未开始。

### postgres-flyway review

- Job `PostgreSQL / Flyway smoke` / id `81284424653` completed / success。
- `Initialize containers` 使用 `postgres:16` service；日志显示 PostgreSQL 16.14 ready。
- `Prepare Flyway runtime classpath` completed / success。
- `Run empty database Flyway smoke` completed / success。
- 日志显示 empty DB 状态：`Schema history table ... does not exist yet`、`Current version ... << Empty Schema >>`。
- 日志显示 `Successfully applied 31 migrations ... now at version v31`、`Successfully validated 31 migrations`、`Flyway empty database smoke reached V31`。
- `flyway_schema_history` 已打印 V1-V31，row 31 为 `V31__schema_credential_permission_probe.sql`，success 为 `true`。

### 边界确认

- 未使用 `baselineOnMigrate` 绕过；workflow 固定 `baselineOnMigrate(false)`。
- 未运行 Flyway `clean`；workflow 固定 `cleanDisabled(true)`，未发现 `cleanDisabled(false)`。
- 未插入 legacy account seed、test fixture seed、真实账户 seed 或真实交易所 seed。
- 未启动 `nq-app` full context，未触发 `AuthSeedConfiguration`。
- 未跑 repository real PostgreSQL smoke，未跑 frontend E2E。
- 未启用 Testcontainers。
- 未使用 `continue-on-error`、`skipTests` 或 skip 伪装通过。
- 未注入真实交易所 credential，未开启 LIVE，未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- Batch 2B/2C/2D/2E、Batch 3/4/5 仍 pending / not started。

### 验证记录

- 已执行 `git status --short`、`git diff --check`、`git diff --stat`。
- 已执行 `git show --stat --oneline --name-only HEAD`。
- 已执行 forbidden-area diff：`git diff -- backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration`，输出均为空。
- 已执行 workflow / docs keyword scan：`rg "continue-on-error|skipTests|baselineOnMigrate|cleanDisabled\(false\)|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current`；workflow 仅命中 `baselineOnMigrate(false)`，docs 命中为历史记录、安全边界或禁止项说明。
- `gh run view --log` 因 GitHub 权限返回 `HTTP 403: Must have admin rights to Repository`；已使用 GitHub MCP 读取同一 job 的 decoded logs、jobs 和 steps。

### Review decision

PASS / ACCEPTED。Batch 2A 可冻结为 PostgreSQL / Flyway empty DB migration smoke baseline。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN`。

---

## NQ-CI-POSTGRES-FLYWAY-2A-IMPL

日期：2026-06-14

### 本轮目标

实现 GateK CI Batch 2A：在 `.github/workflows/ci.yml` 中新增最小 `postgres-flyway` job，使用 GitHub Actions PostgreSQL service container 验证 empty DB 从 V1 到 V31 的 Flyway migration smoke。本轮不做 Batch 2B/2C/2D/2E，不做 no-outbound/security/frontend E2E hardening。

### 修改文件

- `.github/workflows/ci.yml`
- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 变更摘要

- 新增 `postgres-flyway` job，使用 `postgres:16` service 和测试专用 `nq_ci` / `nq_ci_user` / `nq_ci_password`。
- 使用 Java 21 + Maven cache；通过 `mvn -f backend/pom.xml -pl nq-app -am process-classes ... dependency:build-classpath` 准备 Flyway runtime classpath，不运行 tests，不启动 app context。
- 在 workflow step 中生成临时 Java smoke runner，直接调用 Flyway API：`migrate` + `validate`，固定 `locations("classpath:db/migration")`、`baselineOnMigrate(false)`、`cleanDisabled(true)`、`outOfOrder(false)`。
- smoke runner 校验 current version 为 V31，并输出 `flyway_schema_history` 到 job logs。
- 明确 no-seed：不插入 legacy account seed、test fixture seed、真实账户 seed或真实交易所 seed；不依赖 Batch 1 CI-only seed watcher；不触发 `AuthSeedConfiguration`。
- 文档同步 Batch 2A implemented / pending first CI run；Batch 2B schema artifact/docs、Batch 2C repository real PostgreSQL smoke、Batch 2D `nq-app` context smoke、Batch 2E seed watcher cleanup 仍 pending。

### 验证记录

- 已执行 `git status --short`、`git diff --check`、`git diff --stat`。
- 已执行 forbidden-area diff：`git diff -- backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration`。
- 已执行 workflow / docs keyword scan：`rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current`。
- 已执行 Maven classpath 准备命令并通过：`mvn -f backend/pom.xml -pl nq-app -am process-classes org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-DincludeScope=runtime" "-Dmdep.outputFile=target/flyway-classpath.txt"`；23 个 reactor module `SUCCESS`，未启动 PostgreSQL、未运行 tests、未启动 app context。
- 首次未加 PowerShell 引号的本地干跑失败为 shell 参数解析问题；workflow 使用 bash，命令语义不受该本地 PowerShell 问题影响。
- 本机未安装 `actionlint`，Ruby 不可用，系统 Python 与 Codex bundled Python 均无 PyYAML，bundled Node 未发现 `yaml` / `js-yaml`；本轮未执行完整 workflow lint，first CI run 仍是最终语法验证。
- 本地未运行 GitHub Actions service container；`postgres-flyway` first CI run 当时 pending，已由 `NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW` 关闭。
- 未运行 backend full Maven test、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮未修改 Java / TypeScript / Python / test / migration / backend production code。

### 边界确认

- 未修改 Java、TypeScript、Python、测试代码、backend 生产逻辑、frontend、research、scripts、deploy。
- 未新增 API、migration，未修改历史 migration。
- 未启动 `nq-app` full context，未运行 repository real PostgreSQL smoke，未运行 frontend E2E。
- 未启用 Testcontainers，未使用 `baselineOnMigrate`，未运行 Flyway `clean`。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。
- 未注入真实 credential。

### 下一步

Next concrete action（implementation 当时）：push / PR 到 `dev` 后观察 GitHub Actions `NQ CI Baseline` 的 `postgres-flyway` first run；随后执行 `NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW`。该 action 已由本轮 review 关闭；当前 next concrete action 为 `NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN`。

---

## NQ-CI-POSTGRES-FLYWAY-PLAN

日期：2026-06-14

### 本轮目标

输出 GateK CI Batch 2 PostgreSQL / Flyway planning-only 文档，规划后续 GitHub Actions 中如何验证 PostgreSQL service、Flyway empty DB migration smoke、schema baseline、CI-only seed 边界和安全边界。本轮不修改 workflow、不改代码、不新增 migration、不改测试。

### 修改文件

- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 变更摘要

- 新增 Batch 2 主计划文档，明确当前 Batch 1 backend job 已有 PostgreSQL service + CI-only seed watcher，但那是 runner dependency workaround，不是 Batch 2 hardening implemented。
- 建议 Batch 2A 优先使用 GitHub Actions PostgreSQL service container 做 Flyway V1-V31 empty DB migration smoke；Testcontainers 后置到 repository real DB smoke 增强，不与 2A 混合。
- 明确 CI seed 只能服务测试，不得进入生产 runtime seed 或 migration；Flyway empty DB smoke 必须先无 seed 运行。
- 规划 Flyway info、schema tables/columns/constraints/comments、schema-only dump 等 artifact；`DB_SCHEMA.md` drift 先进入 2B review checklist，后续可脚本化阻塞。
- 固定安全边界：不注入真实交易所 credential，不开启 LIVE，不访问 OKX/Binance/Bybit/Gate/Coinbase/Kraken，不连接真实 NQ/DH runtime，不把 Batch 3 no-outbound guard 写成已实现。

### 验证记录

- `git status --short`、`git diff --check`、`git diff --stat` 已在编辑前执行，工作树为空。
- 用户指定的 `.github`、migration、test、application config、forbidden-area diff 和 PostgreSQL/Flyway/security `rg` 检查已执行；结果登记在 `TESTING.md`。
- 本轮未运行 Maven / frontend / Python 测试；原因是 docs-only planning，未修改 code / test / workflow / migration。

### 边界确认

- 未修改 `.github/workflows/ci.yml`。
- 未修改 backend、frontend、research、scripts、deploy。
- 未新增 API、测试或 migration，未修改历史 migration。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 credential material。

### 下一步

Next concrete action：`NQ-CI-POSTGRES-FLYWAY-PLAN-REVIEW`，或在 review 接受后进入 `NQ-CI-POSTGRES-FLYWAY-2A-IMPL`。

---

## GATEK-ARCHITECTURE-BASELINE-REVIEW

日期：2026-06-14

### 本轮目标

只读审查 NQ 当前 architecture baseline、backend module boundary、frontend baseline、research Python 边界、docs/current 事实源、test baseline、CI / observability / deployment / security 后续入口。本轮只允许文档变更；不修改 Java、TypeScript、Python、测试代码、部署脚本或 migration；不启动 GateK implementation、AI、DH runtime、LIVE 或真实 permission probe adapter。

### 修改文件

- `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`
- `README.md`

### 变更摘要

- 新增 GateK architecture baseline review 主文档，冻结当前审查结论：P0=0，P1=0，baseline accepted with P2 follow-up。
- 记录 backend 多模块分层、ArchUnit 边界护栏、frontend Ant Design / TanStack Query / NQ Console Design System baseline、research Python 独立边界和 docs/current 主事实一致性。
- 登记 P2 follow-up：`ARCHITECTURE.md` / `MODULES.md` 旧阶段措辞同步、minimal CI baseline plan、frontend GateK page matrix、no-egress/fake-server plan、observability/deployment planning。
- README 和 docs/current README 仅新增报告索引；未修改代码、API、migration 或部署脚本。

### 验证记录

- `git status --short`、`git diff --check`、`git diff --stat` 已执行，结果见 `TESTING.md`。
- `git diff -- backend`、`git diff -- frontend`、`git diff -- research`、`git diff -- scripts`、`git diff -- deploy`、`git diff -- backend/**/db/migration` 已执行，结果见 `TESTING.md`。
- 阶段误写扫描已执行：命中均为否定式、禁止说明、风险说明或历史语境；未把 GateK implementation、AI、DH runtime、LIVE 或 real adapter 写成已启动/已实现。

### 边界确认

- 未修改 Java、TypeScript、Python、测试代码、部署脚本或 migration。
- 未新增 API、Controller、Service、Repository、Adapter 或 migration。
- 未启动 GateK implementation、AI、DH runtime、NQ RealClient、真实 Provider、真实交易所调用、LIVE 或真实 OKX/Binance permission probe adapter。
- 未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 credential material。

---

## NQ-CREDENTIAL-PERMISSION-PROBE-FREEZE-P3-CLEANUP

日期：2026-06-14

### 本轮目标

完成 credential permission probe freeze 后 P3 cleanup：分离 NoReal fake result 的 `requestId` 与 `traceId`，并收口 permission probe 文档层级。本轮不新增功能，不新增 API，不新增 migration，不接真实交易所，不接 AI / DH，不开启 LIVE。

### 修改文件

- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/probe/NoRealExchangeCredentialPermissionProbePort.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/NoRealExchangeCredentialPermissionProbePortTest.java`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`
- `README.md`

### 变更摘要

- NoReal port 生成本地脱敏 `noreal-probe-<uuid>` requestId，traceId 仍按请求链路透传。
- NoReal test 断言 `requestId != traceId`、requestId 不含输入 payload、status 仍为 `SKIPPED`、error category 仍为 `REAL_EXCHANGE_PROBE_DISABLED`，并保留真实 host 禁访 guard。
- 文档层级固定为：freeze review 是当前权威冻结结论；`API.md` 是 API 对外语义；`DB_SCHEMA.md` 是字段语义；设计审计和 code/API/test review 保留为历史证据。
- 未删除文档；无需要合并删除的重复文档。索引只做降噪和权威入口排序。

### 验证记录

- `mvn -f backend/pom.xml -pl nq-infra,nq-core,nq-api,nq-app -am test`：通过；23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`，`nq-app` 52 tests / 0 failures / 0 errors。
- `mvn -f backend/pom.xml test`：通过；23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`，`nq-app` 52 tests / 0 failures / 0 errors。
- `git status --short`、`git diff --check`、`git diff --stat`、禁止范围 diff 和指定 `rg` 检查已执行，结果见 `TESTING.md`。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改 Controller API 语义，未新增 API。
- 未修改前端、Python 或部署脚本。
- 未实现真实 OKX/Binance/Bybit/Gate permission probe adapter，未真实 HTTP 探活，未调用真实交易所。
- 未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 API key、secret、token、私钥、助记词、passphrase 或 credential material。

---

## NQ-CREDENTIAL-PERMISSION-PROBE-FREEZE-REVIEW

日期：2026-06-14

### 本轮目标

冻结审查 credential permission probe 最小后端实现，只接受当前 no-real-exchange / guarded backend baseline。本轮只做文档同步和 freeze review 记录；不修改 Java、测试、migration、API 语义、前端、Python 或部署脚本；不接真实交易所 adapter、不调用 OKX / Binance / Bybit / Gate、不接 AI / DH、不开启 LIVE。

### 修改文件

- `docs/current/CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`
- `docs/current/README.md`
- `README.md`

### 变更摘要

- 新增 freeze review 主文档，记录 permission probe guarded backend implementation 为 FROZEN / ACCEPTED。
- 明确真实 OKX/Binance/Bybit/Gate permission probe adapter 仍 NOT IMPLEMENTED。
- 明确默认 runtime 行为仍为 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。
- 明确 LIVE credential probe 为 DISABLED / REJECTED，AI / DH / LIVE 均 NOT STARTED。
- 记录 `b473eec1` commit subject 是 `docs(credential): review permission probe implementation design`，但实际包含 implementation/API/tests，后续不得仅凭 subject 误判为 docs-only。
- 将 P3 遗留限制为 freeze 后 cleanup：NoReal port requestId / traceId 混同；文档 gate 顺序与实现顺序轻微差异。

### 验证记录

- `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app,nq-adapter-okx,nq-adapter-binance -am test`：通过；23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`，`nq-app` 52 tests / 0 failures / 0 errors。
- `mvn -f backend/pom.xml test`：通过；23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`，`nq-app` 52 tests / 0 failures / 0 errors。
- `git status --short`、`git diff --check`、`git diff --stat`、`git diff -- backend/nq-infra/src/main/resources/db/migration`、`git diff -- frontend`、`git diff -- research`、`git diff -- scripts` 已执行，结果见 `TESTING.md`。

### 边界确认

- 未修改 Java、测试代码、migration、前端、Python、部署脚本。
- 未新增 API，未新增 migration，未修改历史 migration。
- 未实现真实 OKX/Binance permission probe adapter，未调用真实交易所，未真实 HTTP 探活。
- 未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 API key、secret、token、私钥、助记词、passphrase、cookie 或 credential material。
- 未把 GateK-PLAN 写成 GateK implementation，未把 DH not integrated 写成 DH integrated，未把 LIVE disabled 写成 LIVE enabled。

---

## NQ-FRONTEND-LOGIN-PAGE-PROFESSIONALIZATION

日期：2026-06-13

### 本轮目标

将登录页从“左侧项目名 + 右侧登录框”的基础布局升级为专业量化交易基础设施控制台入口。只改登录页展示、登录相关 E2E 和本轮验证文档；不改 Paper Trading、Dashboard、Backtest、Strategy、Risk 页面；不改 backend、API、鉴权逻辑、token 存储、migration、deploy、scripts；不接 AI / DH，不开启 LIVE，不调用真实交易所。

### 修改文件

- `frontend/src/pages/login/LoginPage.tsx`
- `frontend/src/styles/index.css`
- `frontend/tests/e2e/support.ts`
- `frontend/tests/e2e/login-page-smoke.spec.ts`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 变更摘要

- 登录页重构为独立 `AuthShell` / `ProductIdentityPanel` / `SystemPosturePanel` / `LoginCard` / `SecurityNotice` 展示结构，左侧展示 NexusQuant、Quant Trading Infrastructure Console、Strategy Research、Backtest、Paper Trading、Risk Control、Audit Trail 和 Gate / LIVE / PAPER 状态。
- 登录卡片展示 `Sign in to Console`、DEV / PAPER / LOCAL 环境 Badge、用户名 / 密码输入、登录按钮、脱敏错误提示和安全提示。
- 登录错误提示不再直接输出 traceId、path 或后端细节；只按认证失败、服务不可用和网络问题做用户可理解提示。
- 继续复用现有 `authApi.login`、`useAuthStore.setSession`、redirect 逻辑和 token 存储；未修改登录接口协议、后端鉴权或 token 生命周期。
- 样式复用 NQ Console Design System v1 的 CSS variables、`NqStatusTag` 和 `NqEnvironmentBadge`；新增低对比网格、克制 radial background、小圆角、弱阴影和响应式上下布局。
- 更新登录 E2E helper 的可见文本选择器，新增登录页 smoke test，验证关键文案、状态标签、空凭证输入和安全提示。

### 验证记录

- `npm run build`（frontend）：通过；仍有既有 Vite chunk > 500 kB 警告。
- `npm run test:e2e -- tests/e2e/login-page-smoke.spec.ts --project=chromium`：通过，1 passed。
- `npm run test:e2e`：通过，25 passed / 1 skipped；唯一 skipped 仍为未配置订单 ID 的既有订单详情链路。
- 本地后端为 E2E 临时启动：首次按 Runbook `-pl nq-app` 启动失败，原因是本地 Maven 仓缺少 reactor 模块产物；改用 `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` 启动成功，`/actuator/health` 为 `UP`。
- Browser 运行态验证：Product Design Browser 初始化连续超时，按降级规则使用 Playwright browser 工具验证 `http://127.0.0.1:5179/login`；桌面 1440x900 和移动 390x844 均无水平溢出，关键 Gate/LIVE/PAPER 文案、登录卡片和安全提示可见。
- `git diff --check`：通过；无 whitespace error，仅有 LF/CRLF 工作区提示。
- `git status --short`：通过；工作区仅包含本轮允许范围文件和新增登录页 smoke。
- `git diff --stat`：已执行；Git 默认不统计 untracked 文件，新增 `frontend/tests/e2e/login-page-smoke.spec.ts` 由 `git status --short` 确认。

### 边界确认

- 未修改 backend、python、migration、deploy、scripts、`.github`。
- 未修改 Paper Trading、Dashboard、Backtest、Strategy、Risk 页面。
- 未新增 API，未修改登录接口协议，未修改后端鉴权逻辑，未修改 token 存储逻辑。
- 未开启 LIVE，未接 AI，未接 DH，未调用真实交易所，未输出或硬编码真实密钥、token、secret、cookie 或凭证。

---

## NQ-FRONTEND-PAPER-TRADING-CONSOLE-DEEPEN

日期：2026-06-13

### 本轮目标

将 Paper Trading 页面从「列表 + Drawer」升级为内联运行控制台：顶部状态条 + 左侧 run 列表（焦点选择）+ 中部权益/回撤/日报/稳定性 + 右侧告警/恢复/心跳/调度/操作 + 底部事实表 Tabs。新增 5 个面板组件。只改 frontend Paper Trading 相关与 docs；不改后端、不新增 API、不接 AI/DH、不开启 LIVE。

### 新增文件

- `frontend/src/components/paper/`：`NqAlertPanel`、`NqRecoveryPanel`、`NqHeartbeatPanel`、`NqScheduleFirePanel`、`NqStabilityCheckPanel` + `index.ts`（自包含既有 paper-trading hooks，复用 React Query 缓存键，不重复请求）

### 修改文件

- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`（Drawer → 内联 `<section aria-label="Paper Trading 详情">` 控制台；左列表内部滚动；紧急停机改 NqDangerConfirmButton 移入操作区；状态条/日报/曲线接入 Nq 组件）
- `frontend/src/styles/index.css`（新增 `.nq-run-id` 纯 CSS 省略、`.nq-row-active` 焦点行高亮）
- `frontend/tests/e2e/paper-trading-*.spec.ts`（7 个：`drawer` 改 `getByRole('region', {name:'Paper Trading 详情'})`；移除已转面板/操作区的 tab.click：告警/恢复/心跳/调度/稳定性/日报/异常停机；移除 Drawer Close；BASIC_HEALTH_CHECK 改 `.first()`）
- `docs/current/WORKLOG.md`、`docs/current/TESTING.md`

### 关键结论与坑

- E2E 行内按钮被粘性页头拦截点击：根因是窗口滚动把目标行对齐到视口顶部、落入 `position:sticky` 页头下方；`scroll-margin-top` 不被 Playwright CDP 滚动尊重，最终用「列表内部滚动 `scroll={{y:420}}`」让定位滚动表体而非窗口，结构性解决。
- run 列表必须渲染完整 paperRunId 文本（E2E 以 `hasText` 全量 id 定位行），改用纯 CSS 省略而非 AntD JS ellipsis，避免 DOM 文本被截断。
- 顶部状态条新增「风控状态」展示 checkType，导致 BASIC_HEALTH_CHECK 在页面出现两处；spec 改 `.first()`（与既有 PASSED 断言一致），保留状态条信息不削弱断言。

---

## NQ-FRONTEND-DESIGN-SYSTEM-V1-AND-TRADING-UI-REFACTOR

日期：2026-06-13

### 本轮目标

建立前端 Design System v1（深色优先、高密度、小圆角、低阴影、数字等宽），封装 Nq 基础业务组件，并按优先级重构 Dashboard（安全总览）与 Paper Trading 控制台视觉。只改 frontend 与 docs/current；不改 backend、不新增 API、不新增 migration、不接 AI / DH / 真实交易所、不开启 LIVE。

### 新增文件

- `frontend/src/styles/tokens.css`（CSS variables 全量 token）
- `frontend/src/theme/tokens.ts` / `antd-theme.ts` / `chart-theme.ts`（TS token 镜像 + AntD darkAlgorithm 主题 + 图表同源主题）
- `frontend/src/components/nq/`（NqPageHeader / NqMetricCard / NqStatusTag / NqEnvironmentBadge / NqRiskBanner / NqFilterBar / NqDataTable+nqNumericColumn / NqPriceText / NqPercentText / NqAmountText / NqEmptyState / NqErrorState / NqLoadingState / NqDangerConfirmButton / NqEquityCurveChart / NqDrawdownChart / charts 基建）
- `docs/current/FRONTEND_DESIGN_SYSTEM.md`

### 修改文件

- `frontend/package.json` / `package-lock.json`（仅新增 echarts ^6.1.0）
- `frontend/src/app/providers/AppProviders.tsx`（接入 nqAntdTheme）
- `frontend/src/styles/index.css`（深色外壳重写，去渐变/毛玻璃，新增 .nq-num / .nq-mono / .nq-col-num 工具类）
- `frontend/src/components/page/PageHero.tsx`（收敛为 NqPageHeader 薄适配，全部存量页面统一换肤）
- `frontend/src/components/layout/AppHeader.tsx`（移除过时的 "GateJ-FREEZE Console" 硬编码副标题）
- `frontend/src/pages/dashboard/DashboardPage.tsx`（重构为安全总览：安全横幅 + Paper Run 汇总 + 焦点 run 绩效 + 最近事件）
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`（状态摘要条、权益/回撤 ECharts、Nq 组件换装；保留全部 E2E 文案选择器）
- `frontend/tests/e2e/support.ts`（修复存量登录 fixture 断裂：288c28f8 改了登录文案并移除凭证预填，fixture 未同步）

### 关键结论

- E2E 自 2026-05-28/29 起已整体断裂（288c28f8 登录文案 + dc1288e0 marketdata 日期必填均未同步测试），与本轮重构无关；本轮修复登录 fixture 后 22 passed / 1 skipped / 2 failed。
- 剩余 2 个失败（marketdata dataset / ingestion smoke）为 dc1288e0 引入的存量 spec 与表单必填规则不匹配，留待独立测试同步批次处理，不混入本轮。
- 后端 drawdown / dailyReturn / uptimeRatio 为比例值（DrawdownCalculator 证据），前端统一用 NqPercentText ratio 模式换算展示。



日期：2026-06-12

### 本轮目标

只读审计并设计 credential permission probe 的 code/API/test 实现方案，回答 V31 schema 是否足够、是否需要 schema 补丁、Controller / Service / Port / Adapter / Repository / Audit / Tests 如何分层，以及是否允许进入下一步 code implementation 批次。本轮不实现 Java，不新增 API，不新增 migration，不改测试或配置，不调用真实交易所，不读取或输出真实密钥，不接 AI / DH / LIVE。

### 新增文件

- `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`

### 修改文件

- `README.md`
- `docs/current/README.md`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 审计结论摘要

- V31 schema 足够支撑下一步最小 code/API/test implementation；本轮不需要 schema 补丁。
- 后续如需 `withdraw_enabled=false` hard CHECK、probe attempt history 或更细粒度权限结果表，必须单独 schema-only 批次，不能混入本轮。
- 推荐新增独立 `ExchangeCredentialPermissionProbePort`；Service 只做 owner/account/credential 校验、ACTIVE 校验、Paper safety gate、LIVE 禁止、状态写回、`failed_auth_count` 和 audit log。
- 真实 HTTP 调用必须隔离在 adapter 层，且只能调用安全 read-only endpoint；禁止 order / cancel / transfer / withdraw endpoint。
- API response 和 audit metadata 只允许脱敏 summary，不返回 raw exchange response、headers、signature、encrypted/decrypted payload、API key、secret、private key、passphrase。
- 测试矩阵必须包含 unit / adapter / web/API / no-real-exchange，使用 mock port、fake server、ProxySelector 或 socket guard 证明不访问 `www.okx.com` / `api.binance.com`。
- OKX bootstrap no-outbound fix 已消除构造期外联；未来 permission probe 不得重新引入构造期外联，只能由显式 API/Service 调用触发。

### P0/P1/P2/P3

- P0：无。
- P1：实现批次必须包含独立 probe port、no-real-exchange 测试护栏、LIVE 默认拒绝和 Paper safety gate；若缺失则阻塞验收。
- P2：V31 未强制 `withdraw_enabled=false`，实现批次需代码层拒绝 true；幂等/并发策略需在 code 批次明确；GET latest 可选。
- P3：文档索引需持续区分 design completed 与 runtime implemented。

### 进入下一步结论

允许进入单独 code implementation 批次，建议任务名：`NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-IMPLEMENTATION`。该批次仍不得新增 migration、不得接 AI/DH/LIVE、不得调用真实交易所、不得下单/撤单/转账/提现。

### 验证记录

- 本轮 docs-only，未运行 Maven / frontend / Python 测试；原因：未修改 Java、测试、配置、migration、前端、Python 或部署脚本。
- 按任务要求执行 `git status --short`、`git diff --check`、`git diff --stat`。

### 边界确认

- 未修改 Java、Repository、Service、Controller、DTO 或 API。
- 未新增 migration，未修改历史 migration。
- 未修改前端、Python 或部署脚本。
- 未调用 OKX、Binance、Bybit、Gate 或任何真实交易所。
- 未实现 permission probe，未真实 HTTP 探活。
- 未下单、撤单、转账或提现。
- 未读取或输出真实密钥。
- 未接 AI、DH runtime 或 LIVE。

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-FIX

日期：2026-06-12

### 本轮目标

修复 OKX public instruments 启动期外联问题，确保 full Maven test / local Spring Boot integration test 在应用上下文启动阶段不会访问 `https://www.okx.com/api/v5/public/instruments?instType=SPOT`。本轮不新增 migration，不修改前端、Python 或部署脚本，不接 AI / DH / LIVE，不调用真实交易所，不读取或输出真实密钥。

### 修改文件

- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxInstrumentsCache.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapter.java`
- `backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxInstrumentsCacheTest.java`
- `backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapterBootstrapNoOutboundTest.java`
- `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceFiltersCacheTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/OkxBootstrapNoOutboundLocalContextTest.java`
- `docs/current/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 修复说明

- `OkxInstrumentsCache` 构造函数不再调用 `refreshNow("bootstrap-okx-instruments")`；构造期只保存依赖与本地 cache 状态。
- `refreshIfDue` 改为 cache 为空或刷新窗口过期时才调用 `refreshNow`，保持 `snapshot` / `getRequired` 首次真实读取 instruments metadata 时刷新。
- `OkxExchangeAdapter` 默认依赖创建抽出 package-private overload，便于测试用本地 fake baseUrl 证明默认依赖构造不访问 public instruments endpoint。
- 保持生产运行时首次使用 instruments 时仍可刷新；未改变下单、撤单、订单状态机、credential 或 permission probe 路径。
- Binance 逻辑未修改，只在 `BinanceFiltersCacheTest` 增加构造期不 fetch 的对照断言。

### 回归测试

- `OkxInstrumentsCacheTest.shouldNotFetchDuringConstructionAndRefreshOnFirstSnapshot`：fake public client 计数，构造后 `getCount=0`，首次 `snapshot` 后 `getCount=1`。
- `OkxExchangeAdapterBootstrapNoOutboundTest.shouldCreateDefaultDependenciesWithoutFetchingPublicInstruments`：默认依赖与 adapter 构造后本地 fake server `hitCount=0`，首次 `snapshot` 后 `hitCount=1`。
- `OkxBootstrapNoOutboundLocalContextTest.shouldBootstrapLocalContextWithoutOkxPublicInstrumentsOutbound`：local full Spring context 启动前安装 `ProxySelector` 探针，断言 `www.okx.com/api/v5/public/instruments?instType=SPOT` 访问次数为 0，且日志不包含 `okx_adapter_bootstrap_fallback_enabled`。
- `BinanceFiltersCacheTest`：构造后 `fetchCount=0`，保持 Binance 惰性刷新行为。

### 验证记录

- `mvn -f backend/pom.xml -pl nq-adapter-okx,nq-app -am test`：通过，`BUILD SUCCESS`，`nq-adapter-okx` 27 tests / 0 failures，`nq-app` 52 tests / 0 failures。
- `git diff --check`：通过，无 whitespace error；仅有 Git LF/CRLF 工作区提示。
- `mvn -f backend/pom.xml test`：通过，23 个 backend module 全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 02:43。
- 禁止范围 diff 检查：通过，`backend/nq-infra/src/main/resources/db/migration`、`frontend`、`research`、`scripts` 无 diff。
- 日志 / surefire 报告关键字扫描：未命中 `okx_adapter_bootstrap_fallback_enabled`、`www.okx.com/api/v5/public/instruments` 或 `api/v5/public/instruments?instType=SPOT`。

### 边界确认

- 未新增 migration。
- 未修改前端、Python 或部署脚本。
- 未接 AI、DH runtime 或 LIVE。
- 未真实下单、撤单、转账或提现。
- 未调用 OKX、Binance 或任何真实交易所；测试使用 fake client、本地 fake server 和 `ProxySelector` 探针。
- 未读取或输出真实密钥；permission probe 仍未实现。

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW-DOC

日期：2026-06-12

### 本轮目标

将 `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW` 只读审计结论落到 `docs/current`，形成可追踪审计报告。本轮只做文档落档，不修改代码、配置、migration、测试、前端、Python 或部署脚本，不调用真实交易所，不实现 fix。

### 新增文件

- `docs/current/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`

### 修改文件

- `README.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 审计结论摘要

- OKX public instruments 外联触发路径：`MarketdataControllerLocalIntegrationTest` / `ResearchBacktestHappyPathLocalTest` 使用 `@SpringBootTest` + `local` profile 启动完整 Spring context，`ExchangeAdapterConfiguration` 构造 `OkxExchangeAdapter`，默认依赖构造 `OkxInstrumentsCache`，其构造函数 eager 执行 `refreshNow("bootstrap-okx-instruments")` 并访问 `/api/v5/public/instruments?instType=SPOT`。
- 根因：`OkxInstrumentsCache` 构造期 eager refresh；`OkxRuntimeConfig.fromSystemEnv()` 不读 Spring YAML；`stub-on-bootstrap-failure` 只吞失败，不阻止外联尝试。
- Binance 对照：`BinanceFiltersCache` 构造期不刷新，`exchangeInfo` 仅在 `getRequired` / `snapshot` 触发刷新窗口时拉取，未发现同类启动期外联。
- 影响：无 P0；P1 为 no-outbound test isolation 违反；P2 为 Spring profile 配置不能统一约束 OKX runtime 与测试边界不收敛；P3 为文档/命名易误读。
- GateJ completed 不受影响；credential permission probe 不受影响。
- 推荐后续 FIX：首选惰性化 `OkxInstrumentsCache` bootstrap；或增加 Spring 驱动 no-outbound / stub bootstrap mode；补 no-outbound 回归测试。

### 验证记录

- 本轮 docs-only，未运行 `mvn test` / `npm run build` / `pytest`；原因：未修改 Java、配置、migration、测试、前端、Python 或部署脚本。
- 按任务要求执行 `git diff --check`、`git diff --stat`、`git status --short` 进行文档改动范围和空白检查。

### 边界确认

- 未修改 Java、配置、migration、测试、前端、Python 或部署脚本。
- 未调用 OKX、Binance 或任何真实交易所。
- 未实现 fix，未接 AI / DH / LIVE。
- 未读取或输出真实密钥、API key、secret、token、cookie、passphrase、private key、助记词或交易所凭证。

## NQ-DH-INTEGRATION0-SAFETY-GATE-CLOSE

日期：2026-06-12

### 本轮目标

输出 NQ-DH Integration-0 safety gate close / acceptance report，正式判定 Integration-0 验收通过并关闭。本轮只做验收文档，不写代码、不改测试代码、不新增 API/migration/RealClient/真实 Provider、不做真实联调。

### 新增文件

- `docs/current/NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`

### 修改文件

- `docs/current/STATUS.md`、`docs/current/README.md`、`docs/current/ROADMAP.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`

### 验收结论

- Integration-0：**PASS / CLOSED / ACCEPTED**。
- Runtime integration NOT STARTED；Integration-1 NOT STARTED；DH NOT INTEGRATED；AI NOT STARTED；LIVE DISABLED。
- 已完成链路：三轮审计 + 汇总 → 事实源同步 → 契约冻结 → contract test 矩阵设计 → contract test 代码实现（NQ 16 + DH 16）→ implementation review（PASS）→ 本次验收关闭。
- 契约范围：10 个契约 contract-only / mock-only / test-protected。
- 测试覆盖：INT0-T01..T15 两侧各 16 用例 passed，含 negative path、audit event shape、forbidden side-effect。
- Integration-1 前置 blocker：DH P1-4 residual（rate limit / memory cap / replay nonce persistence）+ header `X-DH-NQ-*`/`X-NQ-DH-*` 对齐 + 真实通道安全前置。

### 验证记录

- 本轮 docs-only：未运行 `mvn test` / `npm run build` / `pytest`；未修改 Java、测试代码、frontend、Python、API、migration、部署脚本；验收依据引用上一轮已通过的 `mvn -f backend/pom.xml test` BUILD SUCCESS（nq-app 51 tests）与 implementation review（PASS）。
- 已执行 `git status --short`、`git diff --check`、`git diff --stat` 核对改动范围（仅 `docs/current`）。

### 边界确认

- 未修改代码、未修改测试代码、未新增 API、未新增 migration、未新增 NQ/DH RealClient、未新增真实 Provider。
- 未做真实 HTTP、未做真实 NQ 调用、未做真实交易所调用、未接 AI、未开启 LIVE、未读取或输出真实密钥、未读写 NQ DB、无交易副作用。
- 未把 Integration-0 写成真实集成；未把 Integration-1 写成已开始；未把 DH 写成 integrated；未把 AI 写成 started；未把 LIVE 写成 enabled。

## NQ-DH-INTEGRATION0-CONTRACT-TEST-IMPL

日期：2026-06-12

### 本轮目标

把已冻结的 Integration-0 contract test matrix（INT0-T01..T15）落成可运行测试代码与 fixture。本轮只新增测试代码与 test resources，不实现真实集成，不修改生产代码，不新增 API/migration/Controller/Service/Repository/DTO，不新增 RealClient/真实 Provider，不做真实 HTTP/真实 NQ/真实交易所调用。

### 新增文件（仅 src/test）

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration0/support/`：9 个 test-only helper（Int0Contract / Int0Signing / Int0NonceStore / Int0ContractValidator / Int0RequestFactory / Int0AuditEvent / Int0ValidationResult / Int0SideEffectTracker / Int0CredentialAccessTracker）。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration0/`：3 个测试类（NqDhIntegration0ContractValidationTest / NqDhIntegration0SecurityContractTest / NqDhIntegration0NoSideEffectTest），共 16 用例覆盖 INT0-T01..T15。
- `backend/nq-app/src/test/resources/integration0/`：10 个脱敏 fixture JSON（fx-candidate-valid/invalid、fx-feedback-valid/invalid、fx-forbidden-fields、fx-raw-prompt-context、fx-tenant-mismatch、fx-readonly-query、fx-forbidden-calls，超限 payload 由测试动态生成）。

### 覆盖关系（INT0-T01..T15）

- ContractValidation：T02 可开放能力、T03 header 缺失、T07 tenant mismatch、T08 payload>64KiB、T11 candidate schema、T12 feedback schema。
- SecurityContract：T01 禁止能力、T04 HMAC、T05 timestamp 窗口、T06 nonce replay（test-only 内存 nonce）、T09 forbidden field、T10 raw prompt/context、T13 audit required、T15 no credential access。
- NoSideEffect：T14 无交易副作用 + 无凭证访问。

### 验证记录

- `mvn -f backend/pom.xml test`：BUILD SUCCESS。nq-app 51 tests / 0 failures / 0 errors（含本轮新增 16 用例；原 35 + 16）。
- 仅 Integration-0 定向运行：`NqDhIntegration0*Test` 16 passed / 0 failed。
- ArchUnit（ModuleBoundaryArchTest / PackageBoundaryArchTest）仍全绿，新增 `..app.integration0..` 测试包未触碰受护栏的 `..domain.. / ..api.. / ..trading.application..` 边界。
- `git diff --check` 通过；`git status --short` 仅命中 `backend/nq-app/src/test/**`。

### 边界确认

- 未修改任何 `src/main`；未新增 API / migration / Controller / Service / Repository / DTO 到 main。
- 未新增 NQ RealClient / DH RealClient / 真实 Provider；未做真实 HTTP / 真实 NQ / 真实交易所调用。
- 未下单 / 撤单 / 启停 Paper Run / 改策略状态 / 读写 NQ DB / 开启 LIVE；未读取或输出真实密钥（全部固定假值 `int0-test-secret` / `t-test-int0` / `dh-int0-test`）。
- 未把 Integration-0 写成真实集成；未把 DH 写成 integrated；未把 AI 写成 started；未把 LIVE 写成 enabled。

### Integration-1 前置（不在本轮）

- nonce store 为 test-only 内存实现；Integration-1 前必须补持久化 / 集中缓存 nonce（replay nonce persistence）。
- rate limit、memory cap 仍缺失（DH P1-4 residual），阻塞 Integration-1。

## NQ-DH-INTEGRATION0-MOCK-CONTRACT-TEST-DESIGN

日期：2026-06-11

### 本轮目标

将已冻结的 15 项 mock / contract test plan 拆成详细测试矩阵，定义 mock/stub 行为、NQ/DH 侧期望、forbidden side-effect 检查、验收标准与 Integration-0/1 blocker，并产出后续“写测试代码”任务输入材料。本轮只做设计，不写测试代码，不修改 Java/frontend/Python/API/migration，不做真实联调。

### 修改文件

- `docs/current/NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`（新增详细矩阵 §6-§12）
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 产出内容

- NQ contract test matrix：INT0-T01..T15，每项含 testId/testName/targetSystem/testType/purpose/inputFixture/requiredHeaders/payload/expectedStatus/expectedResult/expectedAuditEvent/forbiddenSideEffect/blocksIntegration0/blocksIntegration1/implementationOwner/futureCodeLocationSuggestion（建议路径，未创建代码文件）。
- DH contract test matrix（DH 仓库镜像，DH 视角）。
- shared fixture list：18 个 fixtureId（全部脱敏占位，tenant=t-test-*，无真实密钥）。
- forbidden side-effect checklist：16 项（无下单/撤单/Paper/凭证/DB/HTTP/LIVE/禁止字段落库/跨租户等）。
- Integration-0 acceptance checklist 与 Integration-1 blocker checklist。
- next implementation task draft：`NQ-DH-INTEGRATION0-CONTRACT-TEST-IMPL`（草案，本轮不执行）。
- DH P1-4 residual（rate limit / memory cap / replay nonce 持久化）明确仍为 Integration-1 前置修复，不在本轮处理。

### 验证记录

- 本轮 docs-only，未运行 `mvn test` / `npm run build` / `pytest`；原因：未修改 Java、frontend、Python、API、migration、测试代码或部署脚本。
- 已执行 `git status --short`、`git diff --check`、`git diff --stat` 核对改动范围。

### 边界确认

- 未修改代码、未新增 API、未新增 migration、未新增 Controller/Service/Repository/DTO、未修改测试代码、未改部署脚本。
- 未新增 NQ RealClient、未新增 DH RealClient、未新增真实 Provider、未做真实 HTTP / 真实交易所调用、未做真实联调、未接 AI、未开启 LIVE。
- 未下单/撤单/启停 Paper Run/改策略状态/读写 NQ DB/读取凭证。
- `futureCodeLocationSuggestion` 仅为建议路径，未创建任何代码文件。
- 未把本轮写成 implemented；未把 Integration-0 写成真实集成；未把 DH 写成 integrated；未把 AI 写成 started；未把 LIVE 写成 enabled。

## NQ-DH-INTEGRATION-0-CONTRACT-FREEZE

日期：2026-06-11

### 本轮目标

冻结 NQ-DH Integration-0 的契约与边界，输出可作为后续 mock / contract test 的稳定依据。本轮只做契约冻结、边界文档、安全策略文档、mock/contract-test 设计，不做真实集成，不修改 Java/API/migration，不新增运行时代码。

### 新增文件

- `docs/current/NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`
- `docs/current/NQ_DH_INTEGRATION0_SECURITY_POLICY.md`
- `docs/current/NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`

### 修改文件

- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 冻结内容

- DH → NQ 禁止能力清单（下单/撤单/改订单状态/改策略状态/Paper 启停/改风控/读凭证/读写 NQ DB/绕过 API·风控·状态机·审计/触发 LIVE/NL·Agent output·feedback 驱动交易），每项含 Integration-0=否 / Integration-1=默认否 / LIVE=否 / 需代码硬闸=是 / 需审计=是。
- DH → NQ 可开放只读/候选能力清单（12 项），统一约束：真实 HTTP=否、需认证/签名/tenant/requestId/traceId/timestamp/nonce/replay/payload≤64KiB/audit=是、进入执行路径=否。
- header / auth / replay 契约：`X-NQ-DH-Source / Tenant-Id / Request-Id / Trace-Id / Timestamp / Nonce / Signature` + `Content-Type: application/json`；±300s 窗口、HMAC-SHA256 候选、64 KiB 上限、source allowlist、签名原材料/raw/prompt 不落库。
- 10 个数据契约草案（DHSignalCandidate / DHResearchReport / DHRiskReview / DHDecisionSummary / NQFeedbackEvent / NQPaperResultSummary / NQStrategyMetadata / NQBacktestSummary / NQErrorResponse / NQDhContractError），contract-only / mock-only。
- 统一禁止字段清单（API key/secret、token、cookie、passphrase、private key、mnemonic、raw request/response、full prompt/context、signature raw material、DB DSN、password、2FA secret、recovery code 等）。
- NQ 不可信输入处理原则 + 拒绝矩阵（400/401/403/409/413/423/429）。
- mock / contract test 设计（15 项），只写计划不写代码。
- Integration-0 验收标准；DH P1-4 残留登记为 Integration-1 前置，不在本轮修复。

### 验证记录

- 本轮 docs-only，未运行 `mvn test` / `npm run build` / `pytest`；原因：未修改 Java、frontend、Python、API、migration、测试代码或部署脚本。
- 已执行 `git status --short`、`git diff --check`、`git diff --stat` 核对改动范围。

### 边界确认

- 未修改代码、未新增 API、未新增 migration、未新增 Controller/Service/Repository/DTO。
- 未新增 NQ RealClient、未新增 DH RealClient、未新增真实 Provider、未做真实联调、未接 AI、未接 DH runtime、未开启 LIVE。
- 未读取或输出真实密钥；未读写 NQ DB。
- 未把本轮写成 implemented；未把 Integration-0 写成真实集成；未把 DH not integrated 写成 integrated；未把 AI not started 写成 started；未把 LIVE disabled 写成 enabled。

## DOC-SYNC-GATEK-PRE-AND-INT0-REGISTRATION

日期：2026-06-11

### 本轮目标

把 NQ / DH 三轮只读审计结论与当前阶段事实同步到事实源文档，避免后续开发误判阶段。本轮只做文档同步，不修改代码，不启动 Integration-0 实现，不启动 GateK 实现。

### 三轮审计事实

- 第一轮：NQ 全仓只读审计 completed。
- 第二轮：DH 全仓只读审计 completed。
- 第三轮：NQ-DH 联合边界审计 completed（DH 仓库 `docs/current/NQ_DH_INTEGRATION_SECURITY_AUDIT_REPORT.md`）。
- 三轮审计汇总 completed。

### 同步口径

- NQ：Current GateJ completed；Next GateK-PLAN；GateK implementation not started；AI not started；DH not integrated；LIVE disabled。
- Integration-0：allowed only as contract / mock / documentation work line, not runtime integration；禁止真实联调、NQ RealClient、真实 Provider、真实交易、读取凭证、读写 NQ DB、开启 LIVE。
- DH：P1-1 / P1-2 / P1-3 已关闭；P1-4 残留（rate limit / memory cap / replay nonce 持久化）阻塞 Integration-1，不阻塞 Integration-0。
- NQ 侧仍无 DH 入站端点、无 DH client、无 feedback outbox，DH not integrated 成立。

### 修改文件

- `CLAUDE.md`
- `AGENTS.md`
- `docs/current/STATUS.md`
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 验证记录

- 本轮只改文档，未运行 `mvn test` / `npm run build` / `npm run test:e2e` / `pytest`；原因：未修改 Java、前端、Python 或部署代码（符合 AGENTS.md「只改文档可不跑全量测试」规则）。
- 已执行 `git status --short`、`git diff --check`、`git diff --stat` 核对改动范围。

### 边界确认

- 未修改任何代码、API、migration、测试或部署脚本。
- 未新增 NQ RealClient、未新增真实 Provider、未做真实联调、未接 AI、未接 DH 运行时、未开启 LIVE、未读取或输出真实密钥。
- 未把 GateK-PLAN 写成 GateK implementation；未把 Integration-0 写成真实集成；未把 AI 写成 started；未把 DH 写成 integrated；未把 LIVE 写成 enabled。

## Credential Permission Probe Schema

日期：2026-06-08

### 本轮目标

新增 permission probe schema-only migration，为后续真实交易所权限探活做数据库准备。本轮只允许新增一个 Flyway migration 和同步文档；不实现 permission probe，不修改 Java/API/前端/Python/部署，不接 AI/DH/LIVE，不下单、撤单、转账或提现。

### 修改文件

- `backend/nq-infra/src/main/resources/db/migration/V31__schema_credential_permission_probe.sql`
- `docs/current/DB_SCHEMA.md`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_CHANGE + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读确认 credential schema / docs 边界。
- 新增 V31 migration：为 `exchange_account_credentials` 增加 `permission_probe_status`、`last_permission_probe_at`、`last_permission_probe_error`、`ip_allowlist_probe_status`。
- 扩展 `permission_scope` CHECK，允许 `READ_ONLY / TRADE / FUNDING` 或 `NULL`；`NULL` 继续表示未确认权限，不等于 `TRADE`。
- 扩展 `credential_audit_logs.event_type` CHECK，允许 `PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED`。
- 更新新增字段、`withdraw_enabled`、`credential_audit_logs` 表、`event_type`、`metadata` COMMENT，继续声明敏感信息禁入边界。
- `withdraw_enabled` 本轮不新增 `CHECK (withdraw_enabled = FALSE)`：V29 已有 `NOT NULL DEFAULT FALSE`，但本轮未确认现有数据是否全部为 false，避免盲目硬约束破坏已有数据；`withdraw_enabled=true` 不得视为可接受生产状态。
- 同步 DB_SCHEMA、permission probe design review、credential governance plan、README 索引、WORKLOG 和 TESTING。

### 验证记录

- 已执行 `git diff --check`，通过；无 whitespace error。
- 已执行 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；`Total time: 02:24 min`，`Finished at: 2026-06-08T13:26:33+08:00`。
- Maven 过程中 `nq-app` local integration test 的 Flyway 日志显示：成功验证 31 个 migrations，并从 V30 迁移到 V31。
- 范围检查：本轮只新增一个 migration；未修改历史 migration；未修改 Java/API、前端、Python 或部署脚本。
- 边界风险：按任务要求执行全量 Maven 时，既有 `MarketdataControllerLocalIntegrationTest` 在 local profile 启动中触发 OKX public instruments bootstrap fallback，日志显示 `No route to host`。这不是本轮 permission probe、未使用 credential、未调用 private endpoint、未下单/撤单/转账/提现，但验证阶段不能写成完全没有真实交易所触达尝试。

### 边界确认

- 未修改历史 migration。
- 未修改 Java、Repository、Service、Controller、DTO 或 API。
- 未修改前端、Python 或部署脚本。
- 未实现 permission probe，未新增 permission probe endpoint。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未接 AI、DH、LIVE 或真实交易。

## Credential Permission Probe Design Review

日期：2026-06-08

### 本轮目标

只读设计审计真实交易所 credential permission probe，明确 READ_ONLY / TRADE / FUNDING 权限校验、withdraw 禁用、IP allowlist、失败重试、`failed_auth_count`、告警、前端风险提示和 Paper/LIVE 隔离。本轮不新增 migration，不修改 Java/API/前端/Python/部署，不调用真实交易所，不接 AI/DH/LIVE，不实现 permission probe。

### 修改文件

- `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`
- `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_ANALYSIS + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读确认当前 credential verification / Service / Port 边界。
- 只读确认当前 `verification_status='VERIFIED'` 仅代表本地结构性校验，不代表真实交易所权限可用。
- 输出 permission probe 设计审计：建议新增 `permission_probe_status`、`last_permission_probe_at`、脱敏错误字段、`ip_allowlist_probe_status`，扩展 `permission_scope` 支持 `FUNDING`，并强化 `withdraw_enabled=false`。
- 明确推荐先进入 schema-only 批次，再单独做 code/API/test；本轮不实现真实交易所调用。
- 同步 README 索引、freeze review 后续任务状态、WORKLOG 和 TESTING。

### 验证记录

- 已执行 `git diff --check`，通过；无 whitespace error。
- 已执行范围检查：本轮只修改 `docs/current` 文档和 README 索引；未新增 migration，未修改 Java/API、前端、Python 或部署脚本。
- 未执行 Maven：本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，未修改业务代码、migration、API、前端、Python 或部署脚本。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改 Java、Repository、Service、Controller、DTO 或 API。
- 未修改前端、Python 或部署脚本。
- 未调用 OKX、Binance、Bybit、Gate 或任何真实交易所。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未接 AI、DH、LIVE 或真实交易。
- 未实现 permission probe。

## DB Schema Credential Governance Doc Cleanup Batch 5-G-A

日期：2026-06-08

### 本轮目标

只修复 Batch 5-G freeze review 发现的 P3 文案问题：修正 credential disable endpoint OpenAPI description 的过期描述；为 Batch 5-F-A enable governance review 增加历史快照说明；同步 freeze review、README 索引、WORKLOG 和 TESTING。本轮不新增 migration，不修改 credential 业务逻辑，不新增 API，不接真实交易所、AI、DH 或 LIVE。

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java`
- `docs/current/CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md`
- `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_CHANGE + DOCUMENTATION`；主 skill 为 `java-backend-maintenance`，只用于 Controller OpenAPI description 文案修复。
- 将 disable endpoint description 从“本轮不提供 enable 接口”修正为当前事实：disable 只标记 `DISABLED` 并写 audit；恢复必须通过独立 enable 命令完成本地结构性校验。
- 在 `CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md` 增加历史快照说明：该文档记录 5-F-A 当时 enable endpoint 未实现的审计结论；当前事实以 5-F-C enable command 和 5-G freeze review 为准。
- 更新 `CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`，把 P3 cleanup 标记为已由 Batch 5-G-A 关闭。
- 同步 README 索引、WORKLOG 和 TESTING。

### 验证记录

- 已执行 `git diff --check`，通过；无 whitespace error。
- 已执行 `mvn -f backend/pom.xml -pl nq-api -am test`，通过；相关 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。
- 已执行范围检查：本轮未新增 migration，未修改 Repository / Service / DTO / 测试业务语义，未修改前端/Python/部署脚本。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改 credential 业务逻辑。
- 未修改 Repository / Service / DTO / 测试业务语义。
- 未新增 API。
- 未调用真实交易所，未实现 permission probe。
- 未接 AI、DH、LIVE 或真实交易。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。

## DB Schema Credential Governance Freeze Review Batch 5-G

日期：2026-06-08

### 本轮目标

只读复核 credential lifecycle governance 当前实现，冻结 Batch 5-A ~ 5-F-C 的 schema、API、Service、Repository、audit log、测试和文档边界。本轮不新增功能，不修改 Java，不新增 migration，不接真实交易所，不接 AI / DH / LIVE，不实现 permission probe。

### 修改文件

- `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_ANALYSIS + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读检查 credential Service / Repository / Controller / DTO / tests。
- 只读复核 V29 / V30 migration：V29 拆分 lifecycle 与 verification，新增 append-only audit log；V30 仅重建 audit event CHECK 增加 `ENABLED` 并更新注释。
- 只读复核 Repository / JDBC：active summary / active material 查询均要求 `is_active=true AND credential_status='ACTIVE'`；无 `credentialType` 多 active type 返回冲突；enable 内部 material 读取只用于本地结构性校验。
- 只读复核 CommandService / VerificationService：revoke / disable / expire / rotate / enable 都通过 `credential_status` 表达生命周期；verification_status 只承载结构性校验结果；enable 只允许 `DISABLED` 且拒绝 `REVOKED / ROTATED / EXPIRED`。
- 只读复核 Controller / DTO / tests：API response 仅返回非敏感 summary；测试覆盖 lifecycle command、active material selection、response 脱敏、audit metadata 脱敏、permission_scope 不被解释为 TRADE。
- 输出冻结复核报告：允许条件冻结 Batch 5 credential governance；无需 P0/P1/P2 修复批次；建议 P3 cleanup 修复过期描述；允许进入真实交易所权限探活设计审计。

### 验证记录

- 已执行 `git diff --check`，通过；无 whitespace error。
- 已执行范围检查：本轮未新增 migration，未修改 Java/API，未修改前端/Python/部署脚本。
- 未执行 `mvn -f backend/pom.xml test`：本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，未修改业务代码、migration、API、前端、Python 或部署脚本；测试覆盖结论来自只读检查测试文件与上一轮 `TESTING.md` 已记录的实际验证结果。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改 Java / Repository / Service / Controller / DTO / API。
- 未修改前端、Python 或部署脚本。
- 未接真实交易所，未实现 permission probe。
- 未接 AI、DH、LIVE 或真实交易。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未把 GateK-PLAN 写成实现已启动。

## DB Schema Credential Enable Command Batch 5-F-C

日期：2026-06-08

### 本轮目标

实现最小 credential enable command：`POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`。该命令只允许 `DISABLED` 且 `is_active=false` 的 credential 经本地结构性校验后恢复为 `ACTIVE`，并写入 append-only `credential_audit_logs.event_type='ENABLED'`。本轮不新增 migration，不修改历史 migration，不做真实交易所权限探活，不修改前端、Python 或部署脚本。

### 修改文件

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeAccountCredentialRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandService.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialEnableRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandServiceTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationServiceTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java`
- `README.md`
- `docs/current/README.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_CHANGE + DOCUMENTATION`；主 skill 为 `java-backend-maintenance`；辅助 `db-schema-migration-review` 仅用于确认 Batch 5-F-B `V30` 已准备 `ENABLED` audit event，不新增 migration。
- Repository port 新增 enable 所需的内部方法：按 owner/account/credentialId 锁定 credential material、检查同 account + credentialType 是否已有其他 ACTIVE、以及只针对 `DISABLED AND is_active=false` 的 `markEnabled`。
- JDBC Repository 使用 `SELECT ... FOR UPDATE` 读取目标 credential material；该 material 仅供 Service 本地结构性校验使用，不进入 API response、audit metadata 或日志。
- Service 新增单事务 `enable`：先校验 owner/account，再锁定目标 credential，拒绝 `ACTIVE / REVOKED / ROTATED / EXPIRED`，拒绝带 `revoked_at / rotated_at` 历史标记的不可恢复记录，检查同 type 无其他 ACTIVE，执行本地结构性校验，成功后写回 `ACTIVE / VERIFIED / last_verified_at / updated_at` 并追加 `ENABLED` audit log。
- API 新增 `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`；请求体只接收必填 `reason`，`credentialType` 从 credentialId 对应记录派生，不接收 actor、credential material 或权限声明。
- audit metadata 只保存脱敏状态、来源、credentialType、reasonPresent 和 verificationStatus；不保存 secret、token、API key、private key、passphrase、签名、明文 payload 或交易所凭证。
- 测试覆盖 enable 成功、同 type 其他 ACTIVE 冲突、非可恢复状态拒绝、结构性校验失败保持 DISABLED、reason 缺失/敏感词拒绝、Repository SQL/更新语义和 Controller response 脱敏。
- 文档同步 API、DB schema 当前事实、治理计划、README 索引、WORKLOG 和 TESTING。

### 验证记录

- 已执行 `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app -am test`，通过；实际 reactor 覆盖 23 个后端模块，`BUILD SUCCESS`。
- 已执行 `mvn -f backend/pom.xml test`，通过；23 个后端模块均为 `SUCCESS`，最终 `BUILD SUCCESS`，总耗时 `02:11 min`。
- 已执行 `git diff --check`，通过；无 whitespace error。
- 已执行范围检查：本轮未新增 migration，未修改历史 migration，未修改前端/Python/部署脚本，未新增真实交易所权限探活、reveal/decrypt/includeSecret endpoint、AI、DH、LIVE 或真实交易路径。

### 边界确认

- 未新增 migration；Batch 5-F-C 复用 Batch 5-F-B 已准备的 `V30__schema_credential_enable_audit_event.sql`。
- 未修改历史 migration。
- 未修改 `exchange_account_credentials` 字段；未新增字段，未做数据 backfill。
- 未清零 `failed_auth_count`，未清空 `revoked_at / rotated_at` 历史字段，未把 `permission_scope=NULL` 解释为 `TRADE`。
- 未修改前端、Python 或部署脚本。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未调用真实交易所，未做真实交易所权限探活。
- 未接 AI、DH、LIVE 或真实交易。
- 未把 GateK-PLAN 写成实现已启动。

## DB Schema Credential Enable Audit Event Schema Batch 5-F-B

日期：2026-06-08

### 本轮目标

新增 schema-only migration，为 `credential_audit_logs.event_type` CHECK 增加 `ENABLED`，并同步 `docs/current` 文档。`ENABLED` 只表示未来 enable command 可用独立 append-only audit event 记录 `DISABLED` credential 经校验后重新启用；本轮不实现 enable endpoint，不修改 Java/API。

### 修改文件

- `backend/nq-infra/src/main/resources/db/migration/V30__schema_credential_enable_audit_event.sql`
- `README.md`
- `docs/current/README.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_CHANGE + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读确认 V29 audit log 约束和 credential API 现状。
- 只读确认当前最大 migration 为 `V29`，因此新增 `V30__schema_credential_enable_audit_event.sql`。
- 只读确认 V29 `credential_audit_logs.event_type` 允许 `CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED`，不包含 `ENABLED`。
- 只读确认 Java/API 未实现 credential enable endpoint；`ExchangeAccountCredentialController` 当前只有 create、verify、revoke、rotate、disable、expire 等 credential command。
- 新增 migration 仅重建 `chk_credential_audit_logs_event_type` 并更新 `credential_audit_logs` 注释；不新增字段、不修改 `exchange_account_credentials`、不做数据 backfill。
- 文档同步 Batch 5-F-B 为 schema completed，同时补齐 README 索引并明确 enable endpoint not implemented，GateK implementation / AI / DH / LIVE 均未启动。

### 验证记录

- 已执行 `git diff --check`，通过；无 whitespace error。
- 已执行范围检查：只新增一个 migration，未修改历史 migration，未修改 Java/API，未新增 enable endpoint，未修改前端/Python/部署。
- 已执行 `mvn -f backend/pom.xml test`，通过；详见 `docs/current/TESTING.md`。

### 边界确认

- 未修改历史 migration。
- 未修改 Java / Repository / Service / Controller / DTO / API / 前端 / Python / 部署脚本。
- 未新增 enable endpoint，未新增 rotate / revoke / disable / expire 行为，未把本轮写成 enable implemented。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未调用真实交易所，未做真实交易所权限探活。
- 未接 AI、DH、LIVE 或真实交易。
- 未把 GateK-PLAN 写成实现已启动。

## DB Schema Credential Enable Governance Review Batch 5-F-A

日期：2026-06-07

### 本轮目标

只读审计 credential enable / re-enable 生命周期设计，确认是否允许从 `DISABLED` 恢复为 `ACTIVE`，以及 enable 需要哪些前置校验、冲突检测、audit log 和测试。本轮只写 `docs/current` 文档，不新增 migration，不修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。

### 修改文件

- `docs/current/CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/README.md`
- `README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_ANALYSIS + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读确认 credential lifecycle / active selection / revoke-disable-expire-rotate 路径。
- 只读确认 V12 active partial unique index 约束同一 account + credentialType active 唯一。
- 只读确认 V29 `credential_status` 允许 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`，但 `credential_audit_logs.event_type` 不包含 `ENABLED`。
- 只读确认 Batch 5-C `revoke / disable / expire` 都会让 credential 退出 active material，且 `REVOKED / ROTATED` 当前具备不可恢复语义。
- 只读确认 Batch 5-D-B rotate 只从 ACTIVE 派生，旧 credential 标记 `ROTATED`，新 credential 创建 `ACTIVE`，并写 `ROTATED / CREATED` audit log。
- 只读确认 Batch 5-E-B active material deterministic selection 已避免多 ACTIVE type 静默选择。
- 输出 enable 建议：当前不实现；后续只允许 `DISABLED` 严格恢复，`REVOKED / ROTATED` 永久不可恢复，`EXPIRED` 默认走 rotate。
- 输出 Batch 5-F-B 建议：先做 schema-only migration 增加 `ENABLED` audit event；不应复用 `VERIFIED / USED / CREATED`。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行工作树范围检查：本轮只修改文档和 README 索引。
- 已执行 migration diff 范围检查：本轮未新增 migration，未修改历史 migration。
- 后端/前端/Python 全量测试未执行：本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改 Java / Repository / Service / Controller / DTO / API / 前端 / Python / 部署脚本。
- 未新增 enable endpoint，未把本轮审计写成 enable 已实现。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未调用真实交易所，未做真实交易所权限探活。
- 未接 AI、DH、LIVE 或真实交易。
- 未把 GateK-PLAN 写成实现已启动。

## DB Schema Credential Active Credential Uniqueness Review Batch 5-E-C

日期：2026-06-07

### 本轮目标

只读评估 `exchange_account_credentials` 是否需要从“account + credential_type active 唯一”升级为“account 全局 active 唯一”，或继续保留多 credential type active 模型。本轮只写 `docs/current` 文档，不新增 migration，不修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。

### 修改文件

- `docs/current/CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/README.md`
- `README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_ANALYSIS + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读确认 active summary / active material / credential type / account credential 路径。
- 只读确认 V12 partial unique index 精确定义为 `uq_exchange_account_credentials_active_type ON exchange_account_credentials (exchange_account_id, credential_type) WHERE is_active = TRUE`。
- 只读确认 V29 新增 `credential_status` 和 `permission_scope`，但没有新增 account 全局 active 唯一约束；`permission_scope` 当前不由代码写入、读取或过滤。
- 只读确认 Batch 5-E-B 已通过代码层 deterministic selection：无 `credentialType` 多 ACTIVE type 返回 `409 STATE_CONFLICT`，显式 `credentialType` 只选择对应 ACTIVE credential。
- 输出结论：当前不建议新增 account 全局 active unique constraint；保留多 credential type active 模型，避免阻碍未来 READ_ONLY / TRADE / 可能的 FUNDING 权限拆分。
- 输出后续决策：当前不需要 Batch 5-E-D migration；Batch 5-F enable endpoint 继续推迟，先做 enable 只读审计。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行 migration diff 范围检查：本轮未新增 migration，未修改历史 migration。
- 后端/前端/Python 全量测试未执行：本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改 Java / Repository / Service / Controller / DTO / API / 前端 / Python / 部署脚本。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未新增 enable endpoint。
- 未调用真实交易所，未做真实交易所权限探活。
- 未接 AI、DH、LIVE 或真实交易。
- 未把 GateK-PLAN 写成实现已启动。

## DB Schema Credential Active Material Deterministic Selection Batch 5-E-B

日期：2026-06-07

### 本轮目标

修复同一 exchange account 存在多个 ACTIVE credential type 时 active summary / active material 的非确定性选择问题。无 `credentialType` 路径不得再通过 `ORDER BY updated_at DESC LIMIT 1` 静默返回任意 ACTIVE credential；允许显式 `credentialType` 选择，或在多候选时返回业务冲突。

### 修改文件

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeAccountCredentialRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationService.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandServiceTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationServiceTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_CHANGE + DOCUMENTATION`；主 skill 为 `java-backend-maintenance`；辅助 `db-schema-migration-review` 仅用于确认 V12 / V29 schema 和 Batch 5-E-A 审计结论。
- Repository port 新增 active 候选列表和 `credentialType` aware summary/material 查询；无 type 默认方法在候选数大于 1 时抛出 `IllegalStateException`，由 API 统一映射为 `409 STATE_CONFLICT`。
- JDBC 删除 active summary/material 无 type 路径里的 `ORDER BY updated_at DESC LIMIT 1` 静默选择；material 读取必须带 `credentialType`，无 type material 会先通过 summary 候选集判断冲突。
- CommandService / VerificationService 支持可选 `credentialType`；`verifyActive` 多 active type 未指定时返回冲突，指定 type 时只校验对应 ACTIVE credential。
- Controller 为 `GET /credentials/active` 和 `POST /credentials/verify` 增加可选 `credentialType` 查询参数；API response 仍只返回非敏感摘要。
- 测试覆盖单 active 兼容、多 active no-type conflict、显式 type 查询/校验、指定不存在 type、inactive lifecycle 不可读、rotate 后同 type 只读新 credential、API response 脱敏、未新增 enable 方法和不依赖 `permission_scope`。

### 验证记录

- 已执行 `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api -am test`，通过；相关 reactor module 均为 `SUCCESS`，`BUILD SUCCESS`。
- 已执行 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。
- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改前端、Python 或部署脚本。
- 未新增 enable endpoint。
- 未调用真实交易所，未新增真实下单、撤单或真实交易路径。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未把 secret 写入 audit metadata，API response 不包含 encrypted/decrypted payload、secret、token、private key 或 passphrase。
- 未接 AI、DH、LIVE，未把 GateK-PLAN 写成实现已启动。

## DB Schema Credential Active Material Selection Review Batch 5-E-A

日期：2026-06-07

### 本轮目标

只读审计 credential active summary / active material 查询是否需要显式 `credentialType` 或 `permission_scope` 过滤，避免未来一个 exchange account 同时存在多个 ACTIVE credential type 时出现非确定性选择。本轮只写 `docs/current` 文档，不新增 migration，不修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。

### 修改文件

- `docs/current/CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/README.md`
- `README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_ANALYSIS + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读确认 active summary / active material / credential type / account credential 路径。
- 只读检查 V12 / V29 schema、credential Repository port、JDBC Repository、command service、verification service、Controller、DTO 和相关 tests。
- 确认当前 `findActiveSummary` 与 `findActiveMaterial` 已同时要求 `is_active=true` 和 `credential_status='ACTIVE'`，但不带 `credential_type` 或 `permission_scope`，并使用 `ORDER BY updated_at DESC LIMIT 1`。
- 确认 V12 partial unique index 只约束同一 `exchange_account_id + credential_type` 的 active 唯一，不是 account 全局 active 唯一。
- 确认 V29 `permission_scope` 当前只是 schema 元数据，代码未写入、读取或过滤；`NULL` 不能被解释为 READ_ONLY 或 TRADE。
- 输出 Batch 5-E-B 建议：先做代码层冲突检测或显式 credential type 选择；`verifyActive` 和 `GET /credentials/active` 不应在多 active type 下静默返回最新一条；enable endpoint 继续推迟。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行工作树范围检查：本轮只修改文档文件。
- 后端/前端/Python 全量测试未执行：本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改 Java / Repository / Service / Controller / DTO / API / 前端 / Python / 部署脚本。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未新增 enable endpoint。
- 未调用真实交易所，未做真实交易所权限探活。
- 未接 AI、DH、LIVE 或真实交易。
- 未把 GateK-PLAN 写成实现已启动。

## DB Schema Credential Rotate Command Batch 5-D-B

日期：2026-06-07

### 本轮目标

实现显式 credential rotate command。rotate 只允许从旧 ACTIVE credential 派生，在单事务内完成旧 credential 锁定、旧 credential 标记 `ROTATED`、新 credential 创建为 `ACTIVE`、旧 `ROTATED` audit log 与新 `CREATED` audit log 写入；不新增 migration，不实现 enable endpoint，不做真实交易所权限探活，不输出 secret。

### 修改文件

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/command/ExchangeAccountCredentialRotateCommand.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeAccountCredentialRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialRotateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandServiceTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationServiceTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_CHANGE + DOCUMENTATION`；主 skill 为 `java-backend-maintenance`；辅助 `db-schema-migration-review` 仅用于确认 V29 schema 与 Batch 5-D-A rotate review，结论为无需新增 migration。
- 新增 `ExchangeAccountCredentialRotateCommand` 和 `ExchangeAccountCredentialRotateRequestBody`；请求体只包含新 credential material 与必填 reason，`credentialType` 从旧 ACTIVE credential 派生。
- Repository 新增 `findActiveByCredentialIdForOwnerForUpdate` 和 `markRotated`；JDBC 使用 `FOR UPDATE` 锁定旧 ACTIVE credential，并写入 `rotated_at / rotated_by`。
- Service 新增 `rotate` 事务方法：校验账户归属、校验旧 credential ACTIVE、拒绝非 ACTIVE 状态、校验新 material、要求 reason、旧 credential 标记 `ROTATED`、新 credential 创建 `ACTIVE`，并追加旧 `ROTATED` / 新 `CREATED` audit log。
- 受 V12 partial unique index 约束，物理 SQL 顺序先把旧 credential 标记 inactive 再插入新 active；任一步失败由事务回滚，避免成功响应留下无 active 或半成品 audit。
- Controller 新增 `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/rotate`，响应仍只返回 `ExchangeAccountCredentialSummaryResponse` 非敏感摘要。
- 测试新增/更新 rotate 成功、旧 ROTATED、新 ACTIVE、old/new audit log、active material 只返回新 credential、非 ACTIVE 派生拒绝、reason 缺失/敏感词拒绝、重复 rotate 旧 credential 拒绝、API response 脱敏、未新增 enable 方法、JDBC `FOR UPDATE` / `rotated_by` 路径。

### 验证记录

- 初次执行 `mvn -f backend/pom.xml test` 在 `nq-api` 失败，原因是测试用 standalone MockMvc 断言未映射 `/enable` 返回 404，但该测试配置会把 no-handler 场景落到通用 500；已改为反射检查 Controller 未声明 `enable` 方法。
- 修复测试后已执行 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。
- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改前端、Python 或部署脚本。
- 未读取、输出或提交真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未新增 enable endpoint。
- 未调用真实交易所，未做真实交易所权限探活。
- 未接 AI、DH、LIVE 或真实交易。
- 未把 GateK-PLAN 写成实现已启动，也未把 credential lifecycle 写成全部完成。

---

## DB Schema Credential Rotate Governance Review Batch 5-D-A

日期：2026-06-07

### 本轮目标

只读审计 credential rotate 生命周期设计，判断后续 Batch 5-D-B 是否可以实现显式 rotate endpoint，并明确新旧 credential 版本、active material、audit log、幂等和失败回滚边界。本轮只允许修改 `docs/current` 文档，不新增 migration，不修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。

### 修改文件

- `docs/current/CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/README.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_ANALYSIS + DOCUMENTATION`；主 skill 为 `db-schema-migration-review`；辅助 `java-backend-maintenance` 仅用于只读确认 credential 创建、upsert、active material、audit log 路径。
- 只读检查 V12 / V29 schema、credential command service、verification service、Repository port、JDBC Repository、Controller、DTO 和 Batch 5-C tests。
- 确认当前 upsert 已支持最小版本化轮换：新 credential 版本创建，旧同类型 active credential 标记 `ROTATED` 且退出 active。
- 确认 `verification_status` 当前主要用于结构性校验 `PENDING / VERIFIED / FAILED`，Batch 5-C 已不再用它承载轮换旧版本生命周期语义。
- 确认 rotate endpoint 仍未实现，upsert 轮换也尚未写入 `ROTATED` / `CREATED` audit log。
- 输出 Batch 5-D-B 建议：显式 rotate command、同事务创建新版本和标记旧版本、写双 audit log、reason 必填、actor 从认证主体解析、禁止从非 ACTIVE 状态派生、禁止真实交易所探活和 enable 混做。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行工作树范围检查：本轮只修改 `docs/current` 文档。
- 已执行禁止范围检查：未新增 migration，未修改 backend Java，未修改 API，未修改 frontend，未修改 Python，未修改部署脚本。
- 已执行阶段和禁写状态检查：未把 GateK-PLAN 写成实现已启动，未把 AI、DH、LIVE 或 rotate 写成已启用或已实现；相关命中均为禁止项或未实现说明。

### 边界确认

- 未读取 `.env`、secrets、credentials、logs、dump、backup、`target`、node_modules、dist、build、`.git` 内容作为本轮依据。
- 未输出真实密钥、API key、exchange secret、tenant data、token、cookie、私钥、助记词、passphrase、encrypted payload 或 decrypted payload。
- 未新增 migration，未修改历史 migration。
- 未修改 Java / Repository / Service / Controller / DTO / API / 前端 / Python / 部署脚本。
- 未新增 rotate endpoint 或 enable endpoint。
- 未接 AI、DH、LIVE 或真实交易。

---

## DB Schema Credential Revocation Governance Batch 5-B

日期：2026-06-07

### 本轮目标

按 `CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md` 执行 credential revocation schema-only migration。只新增 credential 生命周期、撤销、轮换、权限元数据、使用/失败计数字段，以及 append-only `credential_audit_logs` 表；不接入 Java 业务逻辑，不新增 API，不修改 Repository 查询。

### 修改文件

- `backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql`
- `docs/current/DB_SCHEMA.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/WORKLOG.md`
- `docs/current/TESTING.md`
- `docs/current/README.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_CHANGE + DOCUMENTATION`，主 skill 为 `db-schema-migration-review`，辅助 `java-backend-maintenance` 仅用于只读确认 credential Repository 当前字段兼容性。
- 确认当前最大 Flyway migration 为 V28，本轮新增 V29。
- 只读确认 `JdbcExchangeAccountCredentialRepository` 当前 insert/select/update 仍只接入既有 `verification_status/is_active/revoked_at` 字段，因此新增字段采用默认值或可空设计，避免 schema-only 迁移破坏现有写入路径。
- `exchange_account_credentials` 新增 `credential_status`、`revoked_by`、`revoke_reason`、`rotated_at`、`rotated_by`、`last_used_at`、`failed_auth_count`、`permission_scope`、`withdraw_enabled`、`ip_allowlist_required`、`external_secret_ref`、`key_alias`。
- 历史 `verification_status='REVOKED'` 或 `is_active=false` 记录按现有轮换旧版本语义回填为 `credential_status='ROTATED'`，并用 `revoked_at/updated_at` 补齐 `rotated_at`。
- 新增 `credential_audit_logs` append-only 审计日志表，包含 credential/account 外键、event_type、actor、reason、metadata、created_at，以及按 credential/account/event 的查询索引。
- 所有新增字段和表均补充 PostgreSQL `COMMENT`；敏感文本和 JSONB metadata 注释明确禁止保存密钥、token、API secret、私钥、助记词、cookie、passphrase、签名、明文 payload 或交易所凭证。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行新增 migration 范围检查：本轮只新增 `V29__schema_credential_revocation_governance.sql`，未修改历史 migration。
- 已执行禁止范围检查：未修改 Java、Repository、Service、Controller、DTO、前端、Python 或部署脚本；未新增 API；未实现 revoke/rotate endpoint；未接 AI、DH、LIVE 或真实交易。
- 已执行 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。该结果只证明当前后端测试和 Flyway 迁移装配通过，不代表 revoke/rotate 业务行为已实现。

### 边界确认

- 未读取 `.env`、secrets、credentials、logs、dump、backup、`target`、node_modules、dist、build、`.git` 内容作为本轮依据；一次初始 `rg` 范围过宽输出了 `backend/nq-infra/target/classes` 中的 generated migration 副本，已废弃该结果并重跑显式排除 `target` 的范围检查。
- 未输出真实密钥、API key、exchange secret、tenant data、token、cookie、私钥或助记词。
- 未修改历史 migration。
- 未修改 Java / API / Repository / DTO / 前端 / Python / 部署脚本。
- 未实现 credential revocation 业务功能；Batch 5-C 才允许接 Repository / Service / API / tests。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。

---

## DB Schema Credential Revocation Governance Review Batch 5-A

日期：2026-06-07

### 本轮目标

只读审计 NQ 当前 credential / exchange account / secret metadata 相关表和代码路径，判断后续是否需要引入凭证撤销、失效、轮换和审计字段。本轮只允许新增/更新 `docs/current` 文档，不新增 migration，不修改 Java、Repository、API、前端、Python 或部署脚本。

### 修改文件

- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 使用 `nq-dh-workflow-router` 分类为 `CODE_ANALYSIS + DOCUMENTATION`，主 skill 为 `db-schema-migration-review`，辅助 `java-backend-maintenance` 仅用于只读确认 credential 相关后端路径。
- 只读检查 `exchange_accounts` 与 `exchange_account_credentials` 的 migration、注释、状态约束和当前文档计划。
- 只读确认 `ExchangeAccountCredential*` Domain / Repository / Service / Controller / DTO / Test 路径，确认 API response 当前只返回 masked 摘要，不返回解密 payload。
- 输出 P0/P1/P2/P3 风险，明确当前无确认型 P0，但存在撤销语义、权限元数据、审计字段和独立 audit log 缺口。
- 新增 Batch 5-B schema-only 与 Batch 5-C code/API/test 后续拆分计划。

### 验证记录

- 已执行 `git diff --check`，通过。
- 已执行工作树范围检查：本轮只修改 `docs/current` 文档。
- 已执行禁止范围检查：未新增 migration，未修改 backend Java，未修改 frontend，未修改 Python，未修改 deploy/scripts。
- 已执行阶段和禁写状态检查：未把 GateK-PLAN 写成实现已启动，未把 AI、DH 或 LIVE 写成已启用。

### 边界确认

- 未读取 `.env`、secrets、credentials、logs、dump、backup、target、node_modules、dist、build、`.git` 内容。
- 未输出真实密钥、API key、exchange secret、tenant data、token、cookie、私钥或助记词。
- 未新增 credential revocation schema，未实现 revoke API。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。

---

## DB Schema Research Backtest Archive Commands Batch 4-B

日期：2026-06-07

### 本轮目标

为 `research_configs`、`backtest_configs` 增加受控归档命令语义，使系统可以显式把配置标记为 `ARCHIVED`，并记录 `archived_at / archived_by / archive_reason / updated_at`。归档后默认列表隐藏，按 ID 详情仍可读取，历史 run / evaluation / publish 追溯不被破坏。本轮不新增 migration，不修改历史 migration，不新增前端 / Python / 部署，不接入 AI、DH、LIVE 或真实交易路径。

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/ConfigArchiveRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/ResearchConfigController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/BacktestConfigController.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/ResearchConfigService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/config/BacktestConfigService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/ResearchConfigApiService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/backtest/BacktestConfigApiService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/ResearchConfig.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/BacktestConfig.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/ResearchConfigRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/BacktestConfigRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcResearchConfigRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcBacktestConfigRepository.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/ResearchBacktestServiceTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/ResearchConfigControllerTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestConfigControllerTest.java`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 确认 V28 已提供 `status/archived_at/archived_by/archive_reason` 字段与归档一致性 CHECK，Batch 4-A 已实现默认列表过滤和新运行状态闸门；本轮未发现需要新增 migration 的 schema blocker。
- Repository 新增 `archive(...)` 命令，只更新两张配置表自身的 V28 生命周期字段，不触碰回测事实、评估、发布、Paper facts 或行情时序表。
- Service 新增归档命令：未归档配置写入 `ARCHIVED`、`archived_at`、`archived_by`、`archive_reason` 和 `updated_at`；已归档配置重复归档幂等返回当前详情。
- API 新增 `POST /api/research-configs/{configId}/archive` 与 `POST /api/backtest-configs/{configId}/archive`；请求体只允许 `archiveReason`，`archived_by` 由服务端认证主体解析，缺失时使用 `system`。
- 应用层限制 `archiveReason` 长度并拒绝明显包含密钥、token、API secret、私钥、助记词等敏感材料的原因文本。
- 新增/修改后端测试覆盖归档后默认列表隐藏、ID 可读、历史 run 仍可读、重复归档幂等和 API endpoint 响应。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。
- 已执行禁止范围和阶段措辞扫描：未新增/修改 migration；新增代码未处理 credentials、positions、risk_events、订单、成交、账本、审计、事件、Paper facts、Backtest facts、评估结果、发布记录或 marketdata timeseries；文档命中均为负面边界或历史说明；未误写 GateK / AI / DH / LIVE 启动状态。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未处理凭证、持仓、风控事件、订单、成交、账本、审计、事件、Paper facts、Backtest facts、评估结果、发布记录或 marketdata timeseries。
- 未实现逻辑删除、物理删除或 retention purge。
- 未新增 includeArchived HTTP 查询参数。
- 未修改前端、Python 或部署脚本。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未读取或提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## DB Schema Research Backtest Archive Semantics Batch 4-A

日期：2026-06-07

### 本轮目标

接管 V28 新增的 `research_configs`、`backtest_configs` `status` 与归档元数据字段，实现最小 Repository 查询语义和后端测试：默认业务列表隐藏 `ARCHIVED`，`DISABLED` 仍可见但不能用于新运行，按 ID 查询仍可读取 archived 配置用于历史追溯。本轮不新增 migration，不新增外部 API 参数，不修改前端 / Python / 部署，不接入 AI、DH、LIVE 或真实交易路径。

### 修改文件

- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/ResearchConfig.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/BacktestConfig.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/ResearchConfigRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/BacktestConfigRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/config/BacktestConfigService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/BacktestRunService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcResearchConfigRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcBacktestConfigRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/ResearchConfigResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestConfigResponse.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/ResearchBacktestServiceTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/ResearchConfigControllerTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestConfigControllerTest.java`
- `docs/current/DB_SCHEMA.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 确认 V28 已新增 `status/archived_at/archived_by/archive_reason` 与 CHECK 约束，未发现需要新增 migration 的 schema blocker。
- Domain 接管 `ACTIVE / ARCHIVED / DISABLED` 状态字段与归档元数据一致性校验，并保留旧构造器默认 `ACTIVE`，避免破坏既有测试与调用点。
- Repository 默认列表查询排除 `ARCHIVED`；按 ID 查询不按 status 过滤；新增 includeArchived 内部查询路径，不暴露外部 API 参数。
- `BacktestConfigService.create` 拒绝从非 `ACTIVE` research config 派生新 backtest config。
- `BacktestRunService.create` 拒绝从非 `ACTIVE` research config 或 backtest config 创建新 run。
- API response DTO 同步 status/archive 字段；未新增 create/list 请求参数或新业务 endpoint。
- 后端测试覆盖默认列表隐藏 `ARCHIVED`、`DISABLED` 默认可见、`ARCHIVED` 按 ID 可读、非 ACTIVE 配置不能创建新 run。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。
- 已执行禁止范围和阶段措辞扫描：未新增/修改 migration；新增代码未处理 credentials、positions、risk_events、订单、成交、账本、审计、事件、Paper facts、Backtest facts、评估结果、发布记录或 marketdata timeseries；文档命中均为负面边界或历史说明。

### 边界确认

- 未新增 migration，未修改历史 migration。
- 未处理凭证、持仓、风控事件、订单、成交、账本、审计、事件、Paper facts、Backtest facts、评估结果、发布记录或 marketdata timeseries。
- 未实现逻辑删除、物理删除或 retention purge。
- 未新增外部业务 API 参数或 endpoint。
- 未修改前端、Python 或部署脚本。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未读取或提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## DB Schema Research Backtest Config Governance Batch 3-B

日期：2026-06-06

### 本轮目标

新增一个 Flyway migration，只对 `research_configs`、`backtest_configs` 两类研究 / 回测配置表做归档状态、状态约束、归档元数据和更新时间注释治理。本轮不处理回测事实表、评估结果表、发布记录、Paper facts、订单、成交、账本、风控事件、持仓、行情时序或凭证相关表，不新增业务 API，不修改前端 / Python / 部署，不接入 AI、DH 或真实交易路径。

### 修改文件

- `backend/nq-infra/src/main/resources/db/migration/V28__schema_research_backtest_config_governance.sql`
- `docs/current/DB_SCHEMA.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 确认当前最大 Flyway migration 为 V27，本轮新增 V28。
- 确认用户候选表名与当前 schema 一致：`research_configs`、`backtest_configs` 均由 `V7__gate_f1_research_backtest_skeleton.sql` 创建。
- 确认两张表已存在 `created_at/updated_at`，本轮不重复新增；仅更新 `updated_at` 注释，明确其只表示配置元数据最后更新时间。
- 两张表新增 `status`，默认 `ACTIVE`，允许值为 `ACTIVE / ARCHIVED / DISABLED`。
- 两张表新增 `archived_at/archived_by/archive_reason`，并新增归档一致性 CHECK：只有 `status=ARCHIVED` 时才允许存在归档元数据，且归档状态必须有 `archived_at`。
- `archive_reason` 注释明确不得保存密钥、token、API secret、私钥、助记词、cookie 或账户访问材料。
- 未修改 Java Repository / Domain / DTO：新增字段都有 DB 默认值，现有 insert/select 与绑定更新路径不需要为本批 schema 变更同步字段。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行 V28 migration 禁止范围扫描，未命中禁止表名、AI、DH、LIVE、真实交易、逻辑删除或 retention purge 相关结构变更。
- 已执行 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。

### 边界确认

- 未修改历史 migration。
- 未处理凭证、持仓、风控事件、订单、成交、账本、审计、事件、Paper facts、Backtest facts、评估结果、发布记录或 marketdata timeseries。
- 未实现逻辑删除、物理删除或 retention purge。
- 未新增业务 API。
- 未修改前端、Python 或部署脚本。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未读取或提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## DB Schema Master Table Governance Batch 3-A

日期：2026-06-06

### 本轮目标

新增一个 Flyway migration，对 `roles`、legacy `accounts`、`instrument_catalog` 这类主数据 / 配置表做最小字段与 CHECK 约束治理。本轮不处理事实表、事件表、账本表、审计表、风控事件表、Paper facts、Backtest facts、marketdata timeseries，不新增 API，不修改前端 / Python / 部署，不接入 AI、DH 或真实交易路径。

### 修改文件

- `backend/nq-infra/src/main/resources/db/migration/V27__schema_master_table_governance.sql`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/TradingVerificationController.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/AuthSecurityWebMvcTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/TradingVerificationControllerLocalTest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/maintenance/TradingMaintenanceService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/port/OrderCommandStrategyExecutionGateway.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/port/ManualStrategyTriggerGateway.java`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/SchedulerTradingMaintenanceService.java`
- `docs/current/DB_SCHEMA.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 确认当前最大 Flyway migration 为 V26，本轮新增 V27。
- `roles` 新增 `updated_at`，用于角色主数据维护时间追踪；既有 `role_code` 唯一约束保持不变。
- legacy `accounts` 新增 `updated_at`，并对 `status` 增加 `ACTIVE / DISABLED` CHECK；迁移先把历史异常状态归一为 `DISABLED`。
- `instrument_catalog` 新增 `instrument_type IN ('SPOT')` CHECK；`status` 先治理为非空大写代码，保留当前交易所原生状态语义。
- 未新增唯一索引：`roles.role_code`、`accounts.account_code`、`instrument_catalog(exchange_code, exchange_symbol)`、`instrument_catalog(exchange_code, internal_symbol)` 均已有唯一约束。
- 新增字段都有数据库默认值，现有 Repository insert/upsert 不需要因为 V27 改字段列表。
- 为完成后端验证，修复了既有 package/path 不一致问题：`TradingMaintenanceService` 对齐到 `trading.application.maintenance`，`ManualStrategyTriggerGateway` 与 `OrderCommandStrategyExecutionGateway` 对齐到各自 `application.port` 包，并同步相关 import。该修复不改变 API 契约或业务行为。

### 验证记录

- 已执行 `git diff --check`，通过；仅有 Windows 换行提示，无 whitespace error。
- 已执行 V27 migration 禁止范围扫描，未命中禁止表、事件、时序、AI、DH、真实交易、逻辑删除或 retention 相关结构变更。
- 首次 `mvn -f backend/pom.xml test` 在 `nq-app` 暴露既有 package/path 不一致问题，已做最小 Java 同步修复。
- 已执行 `mvn -f backend/pom.xml clean test`，通过；用于清理旧 package 残留 class。
- 已再次执行用户要求的 `mvn -f backend/pom.xml test`，通过；23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。

### 边界确认

- 未修改历史 migration。
- 未处理 `positions`、`risk_events`、订单、成交、账本、审计、Paper facts、Backtest facts 或 marketdata timeseries。
- 未实现逻辑删除、risk_events 逻辑删除或 retention purge。
- 未新增业务 API。
- 未修改前端、Python 或部署脚本。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未读取或提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## DB Schema Comment Business Normalization

日期：2026-06-06

### 本轮目标

新增一个 Flyway comment-only migration，清理数据库表/字段注释中的工程交付批次措辞，并把注释改写为稳定业务语义。本轮只做 PostgreSQL COMMENT 归一化和文档同步，不新增字段、索引、约束、数据变更、Repository 过滤、逻辑删除、retention purge、AI、DH 或真实交易路径。

### 修改文件

- `backend/nq-infra/src/main/resources/db/migration/V26__schema_comment_business_normalization.sql`
- `docs/current/DB_SCHEMA.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 确认当前最大 Flyway migration 为 V25，本轮新增 V26。
- 新增 `V26__schema_comment_business_normalization.sql`，文件只包含 `COMMENT ON TABLE` 与 `COMMENT ON COLUMN`。
- 将策略、研究、回测、发布、账户、行情、dataset、Paper Trading、风控、曲线、复盘、停机、稳定性检查等表/字段注释改为长期业务语义。
- 为 JSONB / payload / snapshot / config / request / result / summary / detail 类字段补充敏感信息禁入边界。
- 更新 `DB_SCHEMA_GOVERNANCE_PLAN.md`，标记 Batch 2 comment-only migration 已完成。
- 更新 `DB_SCHEMA.md`，记录 V26 只做注释治理，不代表 schema 字段治理完成。

### 验证记录

- 已执行 `git diff --check`。
- 已执行阶段化关键词扫描，确认新增 V26 migration 未命中任务要求清理的工程交付批次关键词；`DB_SCHEMA.md`、`DB_SCHEMA_GOVERNANCE_PLAN.md`、`WORKLOG.md` 中命中均为当前项目状态、历史记录或批次计划描述。
- 已人工检查 V26 migration：只包含 `COMMENT ON TABLE` / `COMMENT ON COLUMN` / 空行；不包含 `ALTER TABLE`、`CREATE INDEX`、`UPDATE`、`DELETE`、`INSERT`。
- 已执行 `git status --short`，确认未修改旧 migration、Java、前端、Python、部署或脚本。
- 未执行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest/mypy/ruff`：本轮只新增 comment-only migration 和 Markdown 文档，未修改业务代码、API、Repository、前端页面、Python 或部署脚本。

### 边界确认

- 未修改历史 migration。
- 未新增表、字段、索引、约束或数据变更。
- 未修改 Java、Repository、API、前端、Python 或部署脚本。
- 未实现逻辑删除或 retention purge。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## Codex Workflow 输出字段口径小修

日期：2026-06-06

### 本轮目标

修复 Codex Workflow 标准输出格式中 `Summary` / `Findings` 字段口径不一致问题，将必填输出字段统一为 `Findings`。本轮只做 Markdown / Skill 文档口径小修，不修改业务代码、生产环境配置、API、migration、Python、部署脚本、AI、DH 或真实交易路径。

### 修改文件

- `AGENTS.md`
- `.agents/skills/nq-dh-workflow-router/SKILL.md`
- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 修复标准输出块中的字段冲突：`AGENTS.md`、`.agents/skills/nq-dh-workflow-router/SKILL.md`、`NQ_DH_CODEX_PLUGIN_WORKFLOW.md` 已统一使用 `Findings`。
- 保持 `NQ_DH_WORKFLOW_ROUTER_SKILL.md` 与 `CODEX_PROJECT_INSTRUCTIONS.md` 已有 `Findings` 口径不变。
- `NQ_DH_CODEX_TASK_TEMPLATES.md` 未发现标准输出格式块使用 `Summary`，无需修改。

### 验证记录

- 已执行 `git status --short`。
- 已执行 `git diff --check`。
- 已执行只读文本检查，确认标准输出格式均使用 `Findings`，且没有把 `Summary` 作为必填输出字段。
- 已检查 GateJ completed / Next: GateK-PLAN / AI not started / DH integration not started / not connected to NQ 未被改错。
- 未执行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest/mypy/ruff`：本轮仅修改 Markdown / Skill 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。

### 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未修改后端、前端、Python 或部署脚本。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未新增 RealClient、real provider 或真实交易路径。
- 未提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## Codex Workflow 文档一致性小修

日期：2026-06-06

### 本轮目标

修复 Codex Workflow Router Skill 状态表述不一致问题，并让 `CODEX_PROJECT_INSTRUCTIONS.md` 同步 `AGENTS.md` 中的 active skills 和 `nq-dh-workflow-router` 前置使用规则。本轮只做 Markdown 文档一致性小修，不修改业务代码、生产环境配置、API、migration、Python、部署脚本、AI、DH 或真实交易路径。

### 修改文件

- `AGENTS.md`
- `docs/current/README.md`
- `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md`
- `docs/current/CODEX_PROJECT_INSTRUCTIONS.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 修复 P2：Router Skill 状态已与 `AGENTS.md` active skills 对齐；`NQ_DH_WORKFLOW_ROUTER_SKILL.md` 改为 `nq-dh-workflow-router` active skill 的源规格与维护规范。
- 修复 P3：`CODEX_PROJECT_INSTRUCTIONS.md` 已补充 `nq-dh-workflow-router` 前置分类规则、范围限定规则、禁止默认调用所有插件、禁止扫描全仓库和固定输出字段。
- 更新 `AGENTS.md` 与 `docs/current/README.md` 中的 Router Skill 入口说明，统一为当前 active skill 口径。
- 外部 Codex App 如需手动创建 Skill，可从 `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md` 复制规格；当前仓库按 `AGENTS.md` 将其作为 active skill 使用。

### 验证记录

- 已执行 `git status --short`。
- 已执行 `git diff --check`。
- 已执行只读文本检查，确认 Router Skill 为当前 active skill 口径、Project Instructions 包含 `nq-dh-workflow-router` 前置规则、README 入口仍有效，且 GateJ completed / Next: GateK-PLAN / AI not started / DH integration not started / not connected to NQ 未被改错。
- 未执行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest/mypy/ruff`：本轮仅修改 Markdown 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。

### 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未修改后端、前端、Python 或部署脚本。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未新增 RealClient、real provider 或真实交易路径。
- 未提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## Codex Workflow 文档固化

日期：2026-06-06

### 本轮目标

固化 NQ / DH 项目的 Codex 插件使用规则、工作流路由、AGENTS.md 规则、Skill 说明和任务模板，让后续开发任务能先分类、再选择最少必要插件。本轮只做文档和规则固化，不修改业务代码、生产环境配置、API、migration、Python、部署脚本、AI、DH 或真实交易路径。

### 新增文件

- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`
- `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md`
- `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md`
- `docs/current/CODEX_PROJECT_INSTRUCTIONS.md`

### 修改文件

- `AGENTS.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 执行内容

- 在 `AGENTS.md` 中新增项目定位、NQ/DH 边界、插件路由入口、代码修改前检查、代码修改后验证和默认输出格式。
- 新增 `NQ_DH_CODEX_PLUGIN_WORKFLOW.md`，固化任务类型枚举、插件路由表、插件优先级、标准执行流程、NQ 边界、DH 边界和默认输出格式。
- 新增 `NQ_DH_WORKFLOW_ROUTER_SKILL.md`，作为 `NQ-DH Workflow Router` Skill 的源规格；当前仓库按 `AGENTS.md` 将 `nq-dh-workflow-router` 作为 active skill 使用。
- 新增 `NQ_DH_CODEX_TASK_TEMPLATES.md`，覆盖代码审查、前端页面优化、回测图表、交易所字段对比、Gate 冻结报告、一键部署审查和 DH Integration-0 模板。
- 新增 `CODEX_PROJECT_INSTRUCTIONS.md`，用于复制到 Codex Project Instructions。
- 在 `docs/current/README.md` 中追加 Codex Workflow 入口。

### 验证记录

- 已读取 `AGENTS.md`、`README.md`、`docs/current/README.md`、`STATUS.md`、`WORKLOG.md`、`TESTING.md` 和用户粘贴请求。
- 已检查目标同名文档此前不存在。
- 已检查新增索引链接指向本轮创建的文档和根目录 `AGENTS.md`。
- 未执行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest/mypy/ruff`：本轮仅修改 Markdown 文档，未修改业务代码或部署配置。

### 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未修改后端、前端、Python 或部署脚本。
- 未接入 AI、DH 或真实交易。
- 未开启 LIVE trading。
- 未提交 credentials、API key、exchange secret、tenant data、token、cookie、生产 `.env`。

---

## GateJ-FREEZE UI + UX Smoke Review 文档整理

日期：2026-05-30

### 本轮目标

将本次 Chrome 目标模式下完成的 NexusQuant GateJ-FREEZE UI + UX 只读 smoke review 结果整理为当前事实源文档。本轮只做文档整理，不修改前端代码、后端代码、API、migration、脚本、配置，不重新 build、部署或重启服务，不影响当前 GateJ-FREEZE 7d 连续运行验收。

### 新增文件

- `docs/current/GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md`

### 修改文件

- `docs/current/STATUS.md`
- `docs/current/WORKLOG.md`

### 记录结论

- Functional stability: PASS。
- UI/UX professionalism: FAIL。
- 页面均可打开，未发现白屏、崩溃、`internal server error`、明显 Console error/warn；观察到的 Network 请求为 200。
- 未发现旧 Gate 文案残留：`GateH-1`、`GateH-2`、`GateI-3`、`GateH-PRE`、`GateG`、`LOCAL`、`Gate-3`。
- 主要 UI/UX 问题：Dashboard 暴露工程实现文案；freeze 期间多个写动作按钮仍可点击；Instrument Catalog 同步入口未前端禁用；Dashboard / Paper Trading / Schedules / Runs 缺关键摘要；空态说明偏弱；表格密度和中英文术语仍需统一。
- 当前 GateJ-FREEZE 最终稳定性状态：30m observation PASS，1h acceptance PASS，24h acceptance PASS，7d acceptance PASS，GateJ completed: yes。

### Acceptance impact

- 本次 UI/UX FAIL 不等同于后端或运行稳定性 FAIL。
- 不打断当前 7d 连续运行验收。
- 不建议当前立即修复 UI/UX 问题，因为修复需要重新 build / release / deploy，会破坏当前连续运行证据。
- 若 7d 最终 PASS，可以判定 GateJ-FREEZE 稳定性验收通过，但不能声明 UI/UX 专业化已完成。
- 建议在 7d 验收完成后单独开 `GateJ-POST-FREEZE-UI-AUDIT-FIX` 跟踪。

### 本轮未执行

- 未执行 `mvn -f backend/pom.xml test`：本轮仅整理文档，未修改 Java / API / migration。
- 未执行 `npm run build` 或 `npm run test:e2e`：本轮仅整理文档，未修改前端代码。
- 未执行 Python `pytest / mypy / ruff`：本轮未修改 `research/py`。
- 本记录发生在 2026-05-30，当时未创建 `docs/gates/gate-j`；2026-06-05 GateJ 7d 验收通过后已允许创建冻结快照。

### 边界确认

- 未新增、编辑、删除、启动、停止、下单、发布、评估或触发外部同步。
- 未修改服务器配置。
- 未输出、保存或提交密码、JWT token、cookie、localStorage token。
- 未新增 release、dist、jar、zip、log、dump、freeze-evidence 文件。

---

## 修改文件清单

- `.env.example`
- `README.md`
- `docker-compose.yml`
- `docs/README.md`
- `docs/DOC_RULES.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/ARCHITECTURE.md`
- `docs/current/MODULES.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/RUNBOOK.md`
- `docs/current/ROADMAP.md`
- `docs/current/PLAN_GATEH.md`
- `docs/current/WORKLOG.md`
- `docs/templates/WORK_ORDER.md`
- `docs/templates/GATE_PLAN.md`
- `docs/templates/CHECKLIST.md`
- `docs/templates/ADR.md`

## 归档文件清单

- `docs/archive/legacy-root-docs/ARCHITECTURE.md`
- `docs/archive/legacy-root-docs/CONTRACTS.md`
- `docs/archive/legacy-root-docs/DB_SCHEMA.md`
- `docs/archive/legacy-root-docs/DECISIONS.md`
- `docs/archive/legacy-root-docs/EVOLUTION_RULES.md`
- `docs/archive/legacy-root-docs/GATE_A_CHECKLIST.md`
- `docs/archive/legacy-root-docs/MODULES.md`
- `docs/archive/legacy-root-docs/NUMERIC_POLICY.md`
- `docs/archive/legacy-root-docs/RECOVERY_RUNBOOK.md`
- `docs/archive/legacy-root-docs/ROADMAP.md`
- `docs/archive/legacy-root-docs/WORK.md`
- `docs/archive/gate-inputs/GATEF_INPUTS.md`
- `docs/archive/gate-inputs/LEGACY_CONSOLE_INPUTS.md`

## 配置修复清单

- `docker-compose.yml`：PostgreSQL 默认映射修正为 `${NQ_DB_PORT:-5432}:5432`。
- `.env.example`：`NQ_DB_PORT` 默认修正为 `5432`。
- `backend/nq-app/src/main/resources/application-local.yml`：已确认默认连接 `localhost:${NQ_DB_PORT:5432}` 并支持 `NQ_DB_URL` 覆盖。

## 验证命令和结果

- `git status --short`：已执行，工作区包含本次 docs/config 修改与 `git mv` 归档。
- `rg` 检查：已确认 `docker-compose.yml`、`.env.example`、`application-local.yml` 命中 `5432` 默认配置。
- `Test-Path docs/DOC_RULES.md`、`docs/archive/legacy-root-docs`、`docs/archive/gate-inputs`、`docs/templates`：均存在。
- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`。
- `npm ci`：首次因 Node cache 权限/占用失败；提权重跑通过，提示 4 个 `npm audit` 漏洞。
- `npm run build`：通过，Vite 提示 bundle chunk 超过 500 kB。
- `python -m pytest -q`：BASELINE-FIX-2 后通过，`2 passed in 0.01s`。
- `python -m mypy src`：BASELINE-FIX-2 后通过，`Success: no issues found in 8 source files`。
- `python -m ruff check .`：BASELINE-FIX-2 后通过，`All checks passed!`。
- `mvn -f backend/pom.xml -pl nq-app spring-boot:run -Dspring-boot.run.profiles=local`：失败，缺少 `-am` 时无法解析 reactor module 依赖。
- `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local`：通过，后端启动在 `18888`，连接 `jdbc:postgresql://localhost:5432/nexus_quant`。
- `Invoke-RestMethod http://localhost:18888/actuator/health`：通过，返回 `UP`。
- `POST /api/auth/login` + `GET /api/auth/me`：通过，登录用户 `admin`，`/api/auth/me` 返回当前默认账户 alias。
- `npm run test:e2e`：BASELINE-FIX-2 后通过，8 个 Playwright 用例中 5 passed、3 skipped。

## BASELINE-FIX-2 执行记录

### E2E 修复

- 修改 `frontend/tests/e2e/support.ts`：每次 `loginToConsole` 前先通过 `/api/auth/login` 获取 token，再调用 `/api/exchange-accounts/900001/set-default`，把 admin 默认账户固定为 `rc1-admin-default`。
- 修改 `frontend/tests/e2e/account-credential-write-smoke.spec.ts`：先断言默认账户已切到 `rc1-admin-alt`，再模拟用户从 header 下拉显式选择 alt 账户，避免把“默认账户变更”误判成“当前已选账户必须被强制覆盖”。
- 修改 `frontend/playwright.config.ts`：增加 `actionTimeout`、`navigationTimeout`，并保留外部 dev server 模式。
- 新增 `frontend/tests/e2e/run-e2e.mjs` 并修改 `frontend/package.json`：`npm run test:e2e` 现在由 runner 启动 Vite、等待 `4173`、以外部 server 模式执行 Playwright、最后停止 Vite，避免 Windows 下 Playwright 内置 webServer 回收导致命令挂住。

### Python dev 环境修复

- 修改 `research/py/pyproject.toml`：新增 `[project.optional-dependencies].dev`，包含 `pytest`、`mypy`、`ruff`。
- 修改 `research/py/README.md`：补充 `python -m pip install -e ".[dev]"` 和统一验证命令。
- 当前环境中 `python -m pip install -e ".[dev]"` 两次超时；为完成验证，提权执行 `python -m pip install pytest mypy ruff` 并成功安装工具。

### BASELINE-FIX-2 最终验证

- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- `npm run test:e2e`：通过，5 passed、3 skipped。
- `python -m pytest -q`：通过，2 passed。
- `python -m mypy src`：通过。
- `python -m ruff check .`：通过。
- `POST /api/auth/login` + `GET /api/auth/me`：通过，默认账户为 `rc1-admin-default / 900001`。

## 未完成项

- GateH 正式功能开发未启动。
- 虚拟币量化 V1 未完成。
- AI 自动交易未进入开发。
- `npm audit` 存在 4 个漏洞提示，本任务未做依赖升级或修复。
- Vite chunk > 500 kB 警告仍存在，本任务未处理构建体积。
- E2E 中 3 个用例因当前环境缺少对应预置数据或环境变量而 skip。

## 下一步进入 GateH-PLAN 的条件

- 文档入口与当前状态无冲突。
- PostgreSQL `5432` 本地基线稳定。
- 后端、前端、Python 验证结果已如实记录。
- BASELINE-FIX-2 已修复 E2E 默认账户不幂等和 Python 工具缺失问题。
- GateH scope、API、DB、前端、测试矩阵、回滚边界形成正式计划。

## GateH-PLAN 执行记录

日期：2026-05-17

### 本轮修改文件

- `docs/current/PLAN_GATEH.md`
- `docs/current/GATEH_API_PLAN.md`
- `docs/current/GATEH_DB_PLAN.md`
- `docs/current/GATEH_FRONTEND_PLAN.md`
- `docs/current/GATEH_TEST_PLAN.md`
- `docs/current/GATEH_WORK_ORDER.md`
- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/WORKLOG.md`

### 本轮新增文件

- `docs/current/GATEH_API_PLAN.md`
- `docs/current/GATEH_DB_PLAN.md`
- `docs/current/GATEH_FRONTEND_PLAN.md`
- `docs/current/GATEH_TEST_PLAN.md`
- `docs/current/GATEH_WORK_ORDER.md`

### 本轮执行内容

- 重写 GateH 总计划，明确 GateH 背景、目标、不做范围、GateH-1/2/3 拆分、验收标准、规划入口、风险与回滚策略。
- 新增 API 规划文档，覆盖 Trading Workspace、Instrument、Marketdata Bar、Ingestion Job、Dataset、Backtest Dataset Binding。
- 新增 DB 规划文档，明确 `instrument_catalog`、`marketdata_bars`、ingestion jobs/runs、datasets、backtest dataset binding 的规划边界。
- 新增前端规划文档，规划 `/trading`、`/instruments`、`/marketdata`、`/marketdata/ingestion`、`/backtests`。
- 新增测试规划文档，保留当前验证基线并规划 GateH E2E 矩阵。
- 新增 GateH work order 草案，拆分 GateH-1-WO、GateH-2-WO、GateH-3-WO。
- 同步 `STATUS.md` 和 `ROADMAP.md`，将当前状态更新为 `GateH-PLAN`，未将 GateH 写成 completed。

### 本轮未执行内容

- 未开发 GateH 功能代码。
- 未新增 API 实现。
- 未新增 DB migration。
- 未新增前端页面实现。
- 未新增历史行情抓取代码。
- 未接入 AI。
- 未修改交易、策略、账户业务逻辑。
- 未处理 `npm audit`。
- 未处理 Vite chunk 警告。

### 验证记录

- 本轮只修改文档，未重新执行全量 `mvn`、`npm`、Python 测试。
- 沿用当前验证基线：后端 `mvn test` 通过，前端 `npm run build` 通过，E2E 5 passed / 3 skipped，Python pytest/mypy/ruff 通过。
- 已规划执行文件存在性、状态文案和禁止项检查。

### 下一步进入 GateH-1-WO 的条件

- GateH-PLAN 文档完成审阅。
- 确认 GateH-1 只做交易工作台正式化，不夹带 GateH-2/3 实现。
- 为 GateH-1 单独开 work order，并按 API、DB、前端、测试矩阵拆解可验收任务。
- GateH-1 开工前再次确认不接入 AI、不新增历史行情抓取、不修改策略核心逻辑。

## GateH-1-WO 执行记录

日期：2026-05-17

### 本轮范围

- 正式化 `/trading` 交易工作台。
- 增加 `GET /api/trading/orders` 订单列表查询。
- 订单详情继续展示订单、最新成交、账户余额快照和持仓快照。
- 强化账户上下文校验：交易工作台列表查询必须使用已登记的 exchange account。
- 显示 SIM / LIVE 边界。
- 下单前展示风控摘要与“服务端风控不可绕过”的明确状态。
- `/trade-validation` 保持兼容，并在页面内标记为过渡入口。
- 更新 E2E smoke。

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/OrderView.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/OrderListResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/TradingVerificationController.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/query/OrderQueryView.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/query/TradingQueryFacade.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/query/JdbcTradingQueryFacade.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/TradingVerificationControllerLocalTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/AuthSecurityWebMvcTest.java`
- `frontend/src/api/trading-workbench.ts`
- `frontend/src/api/query-keys.ts`
- `frontend/src/hooks/useTradingWorkbench.ts`
- `frontend/src/types/trading-workbench.ts`
- `frontend/src/pages/trading/TradingWorkbenchPage.tsx`
- `frontend/src/router/routes.tsx`
- `frontend/tests/e2e/account-context-smoke.spec.ts`
- `frontend/tests/e2e/account-credential-write-smoke.spec.ts`
- `frontend/tests/e2e/trading-workbench-query.spec.ts`
- `docs/current/API.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 未做范围

- 未开发 GateH-2 历史行情接入。
- 未新增 marketdata ingestion。
- 未开发 GateH-3 dataset 绑定。
- 未新增 DB migration。
- 未接入 AI。
- 未新增美股/A 股、合约全量、高频、复杂因子平台。
- 未修改策略核心逻辑。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`。
- `npm run test:e2e`：通过，7 passed、3 skipped。

### E2E skipped 原因

- `research-detail`：当前环境缺少对应预置 detail 条件，沿用既有 skip。
- `strategies-detail`：当前环境缺少对应预置 detail 条件，沿用既有 skip。
- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，跳过真实订单详情查询链路。

### 下一步进入 GateH-2-WO 的条件

- GateH-1 变更完成审查并提交。
- 若需要更强验收，配置 `E2E_TRADE_ORDER_ID` 后补跑真实订单详情 E2E。
- GateH-2 开工前再次确认范围只包含 OKX / Binance SPOT 历史 K 线接入，不夹带 dataset 绑定或 AI。

## GateH-2-WO 执行记录

日期：2026-05-17

### 本轮范围

- 实现 OKX / Binance SPOT 历史 OHLCV K 线接入最小闭环。
- 增强 `marketdata_bars`，新增 `market_type`、`quote_volume`、`trade_count`、`quality_status`、`raw_payload_json`。
- 新增 `marketdata_ingestion_jobs` 与 `marketdata_ingestion_runs`。
- 新增接入任务创建、列表、详情、运行记录与 run-once API。
- 增强 `/marketdata` 页面，展示 K 线查询、接入任务、运行结果。
- 新增 marketdata E2E smoke。

### 修改文件

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/BarInterval.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/HistoricalBar.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/HistoricalMarketDataQuery.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataBarUpsertStats.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/MarketdataBarRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/MarketdataIngestionService.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiExceptionHandler.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataBarRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcHistoricalMarketDataPort.java`
- `frontend/src/api/marketdata.ts`
- `frontend/src/types/marketdata.ts`
- `frontend/src/pages/marketdata/MarketdataPage.tsx`
- `frontend/tests/e2e/marketdata-bars-query-smoke.spec.ts`
- `frontend/tests/e2e/marketdata-ingestion-smoke.spec.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/STATUS.md`
- `docs/current/WORKLOG.md`

### 新增文件

- `backend/nq-infra/src/main/resources/db/migration/V16__gate_h2_marketdata_ingestion.sql`
- `backend/nq-infra/src/main/resources/db/migration/V17__gate_h2_ingestion_created_by_width.sql`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataIngestionStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataIngestionJob.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataIngestionRun.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/command/CreateMarketdataIngestionJobCommand.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/HistoricalKlineProvider.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/MarketdataIngestionJobRepository.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/HistoricalKlineRequest.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/HistoricalKlineBar.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/service/HistoricalKlineAdapter.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/service/HistoricalKlineAdapterException.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java`
- `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/adapter/AdapterHistoricalKlineProvider.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataIngestionJobRepository.java`

### DB/migration 说明

- `V16` 新增 GateH-2 marketdata ingestion 结构，并为新增表和新增字段补齐 PostgreSQL COMMENT。
- `V17` 将 `marketdata_ingestion_jobs.created_by` 扩展为 `VARCHAR(512)`，兼容 Spring Security principal 审计名，并补充字段 COMMENT。
- `marketdata_bars` 唯一约束升级为 `exchange_code + market_type + symbol + interval + open_time`，用于幂等 upsert。
- 新增索引用于 bars 范围查询、job 列表和 run 列表。

### 未做范围

- 未进入 GateH-3。
- 未新增 dataset/backtest 绑定。
- 未接入 AI。
- 未新增 AI 模块或 AI 自动交易接口。
- 未接合约、资金费率、深度、逐笔成交、链上数据、新闻资讯。
- 未新增美股/A 股适配。
- 未修改交易核心状态机或策略核心逻辑。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`。
- `npm run test:e2e`：通过，9 passed、3 skipped。
- Python 验证本轮未重新执行；本轮未修改 Python，沿用 BASELINE-FIX 已通过基线。

### E2E 说明

- 新增 `marketdata-bars-query-smoke`。
- 新增 `marketdata-ingestion-smoke`。
- E2E 不依赖外网交易所稳定性；目标是验证页面、API、job/run 状态查询闭环。

### 剩余风险

- 真实 OKX/Binance 大范围历史数据回填未在本轮执行。
- 当前 run-once 在本地网络条件下可能返回空 bars，但会记录明确统计。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning 仍存在，本轮不处理。
- `npm audit` 与 Vite chunk 体积警告仍按既有风险记录，未在 GateH-2 处理。

### 下一步进入 GateH-3-WO 的条件

- GateH-2 变更完成审查并提交。
- GateH-3 只能做行情数据质量、dataset、backtest config 绑定与结果追溯。
- GateH-3 不得夹带 AI、交易核心重构、策略核心逻辑、美股/A 股适配或合约全量接入。

## GateH-3-WO 执行记录

日期：2026-05-17

### 本轮范围

- 新增 marketdata dataset 定义。
- 新增 dataset 覆盖范围与质量统计。
- 新增 backtest config 绑定 dataset。
- 新增 backtest run 创建时的 dataset 快照。
- 前端增强 `/marketdata` dataset 区域和 `/backtests` dataset 绑定入口。
- 新增 GateH-3 E2E smoke。

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/BacktestConfigController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestConfigResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestRunResponse.java`
- `backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/application/backtest/BacktestExecutionService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/**`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/**`
- `frontend/src/api/backtests.ts`
- `frontend/src/api/marketdata.ts`
- `frontend/src/hooks/useBacktestsListQuery.ts`
- `frontend/src/pages/backtests/BacktestsPage.tsx`
- `frontend/src/pages/marketdata/MarketdataPage.tsx`
- `frontend/src/types/backtests.ts`
- `frontend/src/types/marketdata.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/STATUS.md`
- `docs/current/WORKLOG.md`

### 新增文件

- `backend/nq-infra/src/main/resources/db/migration/V18__gate_h3_marketdata_dataset_binding.sql`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/MarketdataDatasetService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/command/CreateMarketdataDatasetCommand.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataDataset.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataDatasetCoverage.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataDatasetStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataQualityStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/MarketdataDatasetRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataDatasetRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/dto/CreateMarketdataDatasetRequest.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/dto/MarketdataDatasetResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestDatasetBindingRequestBody.java`
- `frontend/tests/e2e/marketdata-dataset-smoke.spec.ts`
- `frontend/tests/e2e/backtest-dataset-binding-smoke.spec.ts`

### DB/migration 说明

- `V18` 新增 `marketdata_datasets` 和 `marketdata_dataset_coverage`。
- `V18` 给 `backtest_configs` 新增 `dataset_id` 和 `dataset_snapshot_json`。
- `V18` 给 `backtest_runs` 新增 `dataset_snapshot_json`。
- 所有新增表均有 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均有 PostgreSQL `COMMENT ON COLUMN`。
- `marketdata_datasets` 唯一约束用于避免同名同范围重复 dataset。
- `backtest_runs.dataset_snapshot_json` 在 run 创建时从 config 固化，保证历史 run 可追溯。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本为 `18`。
- `npm run test:e2e`：通过，10 passed、4 skipped。
- Python 验证本轮未重新执行；本轮未修改 Python，沿用 BASELINE-FIX 已通过基线。

### E2E 说明

- 新增 `marketdata-dataset-smoke`，通过。
- 新增 `backtest-dataset-binding-smoke`，当前本地库没有可绑定 backtest config 种子，按明确原因 skip。
- 绑定 API 已通过后端 controller 测试覆盖。

### 未做范围

- 未接入 AI。
- 未新增 AI 模块或 AI 自动交易接口。
- 未新增合约全量、资金费率、深度、逐笔成交、链上数据、新闻资讯。
- 未新增美股/A 股适配。
- 未修改交易核心状态机。
- 未修改策略核心逻辑。
- 未修改回测引擎核心算法。

### 剩余风险

- 当前 E2E 绑定 UI 链路依赖本地存在 backtest config 种子；当前种子为空，因此该用例 skip。
- dataset 质量统计第一版只做 expected/actual/missing/invalid/duplicate 聚合，不做复杂连续缺口区间明细。
- `npm audit` 与 Vite chunk 体积警告仍按既有风险记录，未在 GateH-3 处理。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning 仍存在，本轮不处理。

### 下一步进入 GateI-PLAN 的条件

- GateH-3 变更完成审查并提交。
- GateI-PLAN 只能规划虚拟币量化 V1 完整闭环。
- GateI-PLAN 不得夹带 AI 接入；AI 只能在虚拟币 V1 和 Paper Trading 稳定后进入后续 Gate。

## GateI-PLAN 执行记录

日期：2026-05-18

### 本轮范围

- 只做 GateI 规划文档。
- 规划虚拟币量化 V1 完整闭环。
- 明确 GateI-1 / GateI-2 / GateI-3 / GateI-4 拆分。
- 同步当前状态、路线、API、DB、测试与工作日志入口。

### 本轮新增文件

- `docs/current/PLAN_GATEI.md`
- `docs/current/GATEI_API_PLAN.md`
- `docs/current/GATEI_DB_PLAN.md`
- `docs/current/GATEI_FRONTEND_PLAN.md`
- `docs/current/GATEI_TEST_PLAN.md`
- `docs/current/GATEI_WORK_ORDER.md`

### 本轮修改文件

- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 本轮执行内容

- 新增 GateI 总计划，明确背景、目标、不做范围、四个子 Gate、规划入口、风险、回滚策略和进入 GateJ 条件。
- 新增 GateI API 规划，覆盖 Strategy Version、Publish Version、Backtest Config Enhanced、Evaluation Report、Paper Trading Run、Risk Result、Equity Curve、Position Curve、Trade Replay、Emergency Stop。
- 新增 GateI DB 规划，覆盖策略版本、发布版本、回测增强、评估报告、Paper run、风控结果、资金曲线、持仓曲线、复盘和异常停机事件。
- 新增 GateI 前端规划，覆盖 `/strategies`、`/publishes`、`/backtests`、`/evaluations`、`/paper-trading`、`/risk`、`/portfolio/equity-curve`、`/portfolio/position-curve`、`/replay`、`/emergency-stop`。
- 新增 GateI 测试规划，规划后端单元测试、集成测试、API smoke、前端 build、E2E 矩阵、本地启动、migration 验证和冻结标准。
- 新增 GateI work order 草案，拆分 GateI-1-WO 到 GateI-4-WO。
- 同步 `STATUS.md` 和 `ROADMAP.md`，写清 GateH completed、当前执行 GateI-PLAN、AI 仍未开始。

### 本轮未执行内容

- 未开发 GateI 功能代码。
- 未新增 API 实现。
- 未新增 DB migration。
- 未新增前端页面实现。
- 未接入 AI。
- 未新增 AI 模块、AI 信号、AI Paper Trading 或 AI 自动交易。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心逻辑。
- 未修改回测核心算法。
- 未处理 `npm audit`。
- 未处理 Vite chunk 警告。

### 验证记录

- 本轮只修改文档，未重新执行全量 `mvn`、`npm`、Python 测试。
- 已执行 `git status --short --branch`。
- 已检查 GateI 六份规划文档存在。
- 已检查 `STATUS.md` 写清 GateH completed、当前执行 GateI-PLAN、AI 仍未开始。
- 已检查本轮变更未新增业务代码、migration、API 实现或前端页面实现。

### 下一步进入 GateI-1-WO 的条件

- GateI-PLAN 文档完成审查。
- GateI-1-WO 单独开工，并只做策略版本与发布链路正式化。
- GateI-1-WO 不得夹带 GateI-2/3/4 实现。
- GateI-1-WO 不得接入 AI，不得修改策略核心算法，不得新增美股/A 股或合约全量能力。

## GateI-1-WO 执行记录

日期：2026-05-18

### 本轮范围

- 实现策略版本模型、create/list/detail API。
- 固化策略参数快照、配置快照、来源快照和 checksum。
- 发布记录可绑定 `strategy_version_id`。
- 发布时固化 `version_snapshot_json`。
- 前端 `/strategies` 增加策略版本区域和创建入口。
- 前端 `/publishes` 展示策略版本绑定与版本快照。
- 新增 GateI-1 E2E smoke。

### 本轮新增文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/PublishController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyVersionCreateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyVersionResponse.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyVersionService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/command/StrategyVersionCreateRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyVersion.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyVersionSnapshot.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyVersionStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyVersionRepository.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/StrategyVersionServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcStrategyVersionSnapshotQueryPort.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyVersionRepository.java`
- `backend/nq-infra/src/main/resources/db/migration/V19__gate_i1_strategy_versions.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/StrategyVersionSnapshotView.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/StrategyVersionSnapshotQueryPort.java`
- `frontend/tests/e2e/publish-version-smoke.spec.ts`
- `frontend/tests/e2e/strategy-version-smoke.spec.ts`

### 本轮修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestPublishRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestPublishResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/BacktestRunController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyDefinitionController.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyDefinitionService.java`
- `backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/application/eval/api/BacktestRunApiService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcBacktestPublishRecordRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/BacktestPublishService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/command/BacktestPublishRequest.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/BacktestPublishRecord.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/BacktestPublishRecordRepository.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/BacktestPublishServiceTest.java`
- `frontend/src/api/publishes.ts`
- `frontend/src/api/query-keys.ts`
- `frontend/src/api/strategies.ts`
- `frontend/src/hooks/usePublishesListQuery.ts`
- `frontend/src/hooks/useStrategyListQuery.ts`
- `frontend/src/pages/publishes/PublishesPage.tsx`
- `frontend/src/pages/strategies/StrategiesPage.tsx`
- `frontend/src/types/publishes.ts`
- `frontend/src/types/strategies.ts`
- `frontend/tests/e2e/strategies-query.spec.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/STATUS.md`

### DB/migration 说明

- `V19` 新增 `strategy_versions`。
- `V19` 给 `backtest_publish_records` 新增 `strategy_version_id` 和 `version_snapshot_json`。
- `strategy_versions.strategy_code + version` 唯一约束用于保证同一策略下版本号幂等唯一。
- `backtest_publish_records.strategy_version_id` 索引用于按策略版本追溯发布记录。
- 所有新增表均有 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均有 PostgreSQL `COMMENT ON COLUMN`。

### 实现说明

- 后端新增策略版本 service/domain/repository/API，`nq-core` 不依赖 JDBC，SQL 位于 `nq-infra`。
- 发布服务通过 `StrategyVersionSnapshotQueryPort` 读取策略版本快照，避免 `nq-research` 反向依赖 `nq-core`。
- 发布时如果传入 `strategyVersionId`，必须存在且状态为 `ACTIVE`。
- 发布记录固化 `version_snapshot_json`，后续策略版本变化不会改写历史发布结果。
- 修正 `/api/strategies/{strategyCode}` 和 status 更新按 `strategyCode` 查询/更新，避免把业务编码误当内部 `strategyId`。
- 前端策略定义详情新增版本列表和创建表单；发布结果列表和详情展示策略版本 ID 与版本快照。
- E2E 在本地库缺少策略定义时，通过正式 `POST /api/strategies` 创建最小 SIM fixture，再验证策略版本创建链路。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本为 `19`。
- `npm run test:e2e`：通过，13 passed、3 skipped。
- Python 验证本轮未重新执行；本轮未修改 Python，沿用 BASELINE-FIX 已通过基线。

### 未做范围

- 未进入 GateI-2/3/4。
- 未接入 AI。
- 未新增 AI 模块、AI 信号、AI Paper Trading 或 AI 自动交易。
- 未做 Paper Trading。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

### 剩余风险

- `backtest_publish_records.strategy_version_id` 当前为可空，历史发布记录不会自动回填策略版本；后续如需回填必须单独评估。
- 当前发布绑定仅要求策略版本 `ACTIVE`，尚未进入 GateI-2 的评估指标与回测配置增强。
- `npm audit` 与 Vite chunk 体积警告仍按既有风险记录，未在 GateI-1 处理。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning 仍存在，本轮不处理。

### 下一步进入 GateI-2-WO 的条件

- GateI-1 变更完成审查并提交。
- GateI-2-WO 只能做回测配置、评估指标、结果追溯增强。
- GateI-2-WO 不得夹带 AI、Paper Trading 运行闭环、美股/A 股、合约全量、高频或复杂因子平台。

## GateI-2-WO 执行记录

日期：2026-05-19

### 本轮范围

- 增强 backtest config，使其可绑定 strategy version，并展示 strategy version、dataset、param、config 快照。
- 增强 backtest run 创建链路，在创建 run 时固化 strategy version snapshot、dataset snapshot、param snapshot、config snapshot。
- 增强 evaluation report 指标，持久化并返回 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON。
- 增强 `/backtests` 和 `/evaluations` 页面追溯展示。
- 新增 GateI-2 E2E smoke，并修复本地 E2E fixture 对固定账户 ID 的依赖。

### 新增文件

- `backend/nq-infra/src/main/resources/db/migration/V20__gate_i2_backtest_traceability.sql`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestStrategyVersionBindingRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/EvaluationController.java`
- `frontend/tests/e2e/gatei2-fixtures.ts`
- `frontend/tests/e2e/backtest-config-enhanced-smoke.spec.ts`
- `frontend/tests/e2e/evaluation-report-enhanced-smoke.spec.ts`

### 修改文件

- `backend/nq-api/**/research/**`
- `backend/nq-core/**/research/**`
- `backend/nq-backtest/**`
- `backend/nq-eval/**`
- `backend/nq-infra/**/research/**`
- `frontend/src/api/backtests.ts`
- `frontend/src/api/evaluations.ts`
- `frontend/src/hooks/useBacktestsListQuery.ts`
- `frontend/src/hooks/useEvaluationsListQuery.ts`
- `frontend/src/pages/backtests/BacktestsPage.tsx`
- `frontend/src/pages/evaluations/EvaluationsPage.tsx`
- `frontend/src/types/backtests.ts`
- `frontend/src/types/evaluations.ts`
- `frontend/tests/e2e/support.ts`
- `frontend/tests/e2e/strategy-version-smoke.spec.ts`
- `frontend/tests/e2e/research-detail.spec.ts`
- `frontend/tests/e2e/research-query.spec.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/STATUS.md`

### DB / Migration

- `V20__gate_i2_backtest_traceability.sql` 只新增 GateI-2 所需字段和索引，未修改历史 migration。
- `backtest_configs` 新增 `strategy_version_id`、`strategy_version_snapshot_json`、`param_snapshot_json`、`config_snapshot_json`，复用 GateH-3 `dataset_id` 与 `dataset_snapshot_json`。
- `backtest_runs` 新增 `strategy_version_id`、`strategy_version_snapshot_json`、`param_snapshot_json`、`config_snapshot_json`，复用 GateH-3 `dataset_snapshot_json`。
- `backtest_eval_reports` 新增 `total_return`、`annualized_return`、`profit_loss_ratio`、`metrics_json`。
- 新增索引覆盖 `backtest_configs.strategy_version_id`、`backtest_runs.strategy_version_id`、`backtest_eval_reports.backtest_run_id`。
- `V20` 未新增表；所有新增字段均有 PostgreSQL `COMMENT ON COLUMN` 注释，JSONB 字段注释包含用途与敏感信息禁入规则。

### 后端实现

- `PATCH /api/backtest-configs/{configId}/strategy-version` 绑定 strategy version，并固化版本快照与参数快照。
- `POST /api/backtest-runs` 从 config 复制 strategy version、dataset、param、config 快照，保证历史 run 不受后续 config 变更影响。
- `GET /api/backtest-configs`、`GET /api/backtest-configs/{configId}`、`GET /api/backtest-runs/{runId}` 返回完整追溯字段。
- 新增 `GET /api/evaluations` 与 `GET /api/evaluations/{evaluationId}`，返回增强指标和 `metricsJson`。
- API 层不写 SQL，core 不依赖 JDBC，JDBC 实现仍在 infra。
- 未修改策略核心算法、回测核心算法或交易核心状态机。

### 前端实现

- `/backtests` 展示 strategy version、dataset、参数快照、配置快照，并支持绑定 strategy version 与创建 run 后查看 run 级快照。
- `/evaluations` 展示 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON。
- 页面保留 loading、empty、error 状态。
- 服务端数据仍通过 Axios + TanStack Query 获取；Zustand 不存 backtest/evaluation 服务端数据。

### E2E 实现

- 新增 `backtest-config-enhanced-smoke`，验证 `/backtests` 页面 strategy version / dataset 追溯、config snapshot、run snapshot。
- 新增 `evaluation-report-enhanced-smoke`，验证 `/evaluations` 核心指标、详情和 `metrics JSON`。
- E2E fixture 使用正式 API 创建本地数据，不依赖外网交易所。
- `support.ts` 按 alias 解析真实 `exchangeAccountId`，避免本地自增 ID 漂移。
- 本地验证库补入 `accounts.account_id=3001` 作为 legacy strategy account 种子，用于既有 `strategy_definitions.account_id` 外键；该种子不属于 migration。

### 验证结果

- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- `npm run test:e2e`：通过，17 passed / 1 skipped。
- E2E skipped 原因：`trading workspace / 配置订单 ID 时可打开订单详情` 未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI-2 主链。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。

### 未完成项与边界

- 未处理 `npm audit` 4 个依赖漏洞提示。
- 未处理 Vite chunk > 500 kB 警告。
- 未进入 GateI-3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增 SIM/Paper Trading 运行闭环。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机、策略核心算法或回测核心算法。

### GateI-3 结论

- GateI-2-WO 已完成。
- 允许进入 GateI-3-WO，但只能在本轮变更审查/提交后单独开工。
- GateI-3 只能做 SIM/Paper Trading 运行闭环，不能夹带 AI。

## GateH Freeze Snapshot 归档记录

日期：2026-05-19

### 本轮范围

- 新建/复用 `docs/gates/gate-h/` 作为 GateH completed 的只读历史快照目录。
- 将 GateH 完成相关文档从 `docs/current/` 复制归档到 `docs/gates/gate-h/`。
- 更新 `docs/gates/gate-h/README.md`，明确 GateH completed、GateH 范围、GateH 不包含 AI、不包含 GateI 策略版本/发布链路/Paper Trading。

### 归档文件

- `docs/gates/gate-h/PLAN_GATEH.md`
- `docs/gates/gate-h/GATEH_API_PLAN.md`
- `docs/gates/gate-h/GATEH_DB_PLAN.md`
- `docs/gates/gate-h/GATEH_FRONTEND_PLAN.md`
- `docs/gates/gate-h/GATEH_TEST_PLAN.md`
- `docs/gates/gate-h/GATEH_WORK_ORDER.md`
- `docs/gates/gate-h/API.md`
- `docs/gates/gate-h/DB_SCHEMA.md`
- `docs/gates/gate-h/TESTING.md`
- `docs/gates/gate-h/STATUS.md`
- `docs/gates/gate-h/ROADMAP.md`
- `docs/gates/gate-h/WORKLOG.md`
- `docs/gates/gate-h/README.md`

### 边界确认

- 使用复制归档，未移动 `docs/current/` 中的 GateI 文档。
- 未创建 `docs/gates/gate-i/`。
- 未改业务代码。
- 未新增 migration。
- 未新增 API。
- 未改前端页面。

## 项目入口文档同步记录

日期：2026-05-19

### 本轮范围

- 已同步根目录 `README.md`，使项目总入口反映 DOC-CLEAN、BASELINE-FIX、GateH、GateI-PLAN、GateI-1-WO、GateI-2-WO 已完成，Next 为 GateI-3-WO。
- 已同步根目录 `AGENTS.md`，使 Codex / Agent 执行纪律切换到 `Current stage: GateI-3-WO preparation`。
- 明确 GateI-3-WO 只能做 SIM / Paper Trading 运行闭环。
- 明确 AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始且禁止夹带。

### 边界确认

- 本轮只改入口和 Agent 执行纪律文档。
- 未改业务代码。
- 未新增 migration。
- 未新增 API。
- 未新增前端页面。
- 未接入 AI。
- 未创建 `docs/gates/gate-i/`。

### 验证说明

- 本轮为文档同步任务，不重新执行 `mvn`、`npm`、Python 全量测试。
- 已按任务要求执行 `git status --short` 与 README / AGENTS / WORKLOG 关键词检查。

## GateI-3-WO 执行记录

日期：2026-05-19

### 本轮范围

- 实现 SIM/Paper Trading 运行闭环最小版本。
- 新增 4 张 paper_trading 表：`paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions`。
- 新增后端 Paper Trading run 领域、JDBC 持久化、应用服务、API 服务和 controller。
- 新增前端 `/paper-trading` 入口、API 客户端、TanStack Query hooks、列表/详情/创建 UI。
- 新增 Paper Trading run E2E smoke 与 fixture 链路。
- 同步 docs/current 文档：API、DB_SCHEMA、TESTING、WORKLOG、STATUS。

### 新增文件

后端：

- `backend/nq-infra/src/main/resources/db/migration/V21__gate_i3_paper_trading.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingRun.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingRunStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingOrder.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperOrderStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingTrade.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingPosition.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingRunRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingOrderRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingTradeRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingPositionRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingRunService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingRunCreateCommand.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/paper/PaperTradingApiService.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingRunServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingRunRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingOrderRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingTradeRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingPositionRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingRunResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingRunCreateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingOrderResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingTradeResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingPositionResponse.java`

前端：

- `frontend/src/types/paper-trading.ts`
- `frontend/src/api/paper-trading.ts`
- `frontend/src/hooks/usePaperTradingQuery.ts`
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`
- `frontend/tests/e2e/paper-trading-fixtures.ts`
- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`

### 修改文件

- `frontend/src/api/query-keys.ts`：新增 `paperTradingQueryKeys`。
- `frontend/src/router/routes.tsx`：注册 `/paper-trading` 路由。
- `frontend/src/router/navigation.tsx`：新增 `paper-trading` 菜单项。
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/STATUS.md`
- `docs/current/WORKLOG.md`

### DB / Migration

- `V21__gate_i3_paper_trading.sql` 只新增 GateI-3 所需 4 张表，未修改历史 migration。
- 4 张表均包含 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段 `paper_trading_runs.status`、`paper_trading_orders.status`、`paper_trading_runs.trade_env`、`paper_trading_orders.side`、`paper_trading_trades.side` 均通过 `CHECK` 约束限制允许值。
- 外键约束：`paper_runs.publish_id → backtest_publish_records.publish_record_id`、`paper_runs.strategy_version_id → strategy_versions.strategy_version_id`、`paper_orders.paper_run_id → paper_runs`、`paper_trades.paper_order_id → paper_orders` 与 `paper_run_id → paper_runs`、`paper_positions.paper_run_id → paper_runs`。
- `paper_trading_positions` 通过 `(paper_run_id, symbol)` 唯一约束保证持仓行幂等。
- 索引：`idx_paper_runs_publish_id`、`idx_paper_runs_strategy_version_id`、`idx_paper_runs_status`、`idx_paper_orders_run_id`、`idx_paper_orders_run_symbol_status`、`idx_paper_trades_run_id`、`idx_paper_trades_order_id`、`idx_paper_trades_symbol_time`、`idx_paper_positions_run_id`。
- 新增字段 `paper_trading_runs.interval_code` 而非 `interval`，避免 PostgreSQL `INTERVAL` 关键字冲突。

### 后端实现

- `nq-research` 承载领域模型、port、应用服务，不依赖 JDBC。
- `nq-infra` 承载 JDBC 实现，使用 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB。
- `nq-api` 提供 `PaperTradingController`，所有写操作委派给 `PaperTradingApiService`，不直接写 SQL。
- `PaperTradingApiService` 把领域 `IllegalArgumentException` 映射为 HTTP 404、`IllegalStateException` 映射为 HTTP 409。
- 创建 Paper run 时通过 `BacktestPublishRecordRepository` 加载 publish 与 publish snapshot/version snapshot；通过 `BacktestRunRepository` 加载发布关联的 backtest run，复制 dataset snapshot 与 param snapshot；request body 中的 `configSnapshotJson` 作为运行级 config snapshot 固化。
- Paper run 状态机：`CREATED → RUNNING`（仅 start）；`RUNNING → STOPPED`（仅 stop）；非法状态过渡返回 409。
- `created_by` 第一版固定为 `system`，与既有 `BacktestPublishService` 等模块一致；后续可按权限链路接入登录用户。
- 不调用任何真实交易所下单接口。
- 不修改交易核心状态机、策略核心算法、回测核心算法。

### 前端实现

- `/paper-trading` 增强菜单与路由入口，归类到 `策略运行`。
- 提供查询区（按 publishId / status 过滤）、列表区、创建弹窗、详情抽屉。
- 详情抽屉包含订单、成交、持仓和快照标签页，每个标签页都有 loading / empty / error 状态。
- 服务端数据通过 Axios + TanStack Query 获取；Zustand 不存 Paper Trading 服务端数据。
- 列表行内提供 `查看详情`、`启动`、`停止` 按钮，按状态启用/禁用。

### E2E 实现

- 新增 `paper-trading-run-smoke.spec.ts`：登录 → 准备 fixture → 打开 `/paper-trading` → 查询 → 创建 run → 校验返回 `CREATED` 与快照绑定 → 启动 → 校验返回 `RUNNING` → 停止 → 校验返回 `STOPPED` → 打开详情 → 验证 orders/trades/positions 空态与快照标签。
- 新增 `paper-trading-fixtures.ts`：通过正式 API 完整链路准备数据，沿用 GateI-2 fixture 路径并扩展到 publish。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

### 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- `npm run test:e2e`：本轮未在干净本地 `5432` 实例上启动后端执行；E2E spec 与 fixture 已就绪，等待下一次完整本地验证窗口或 GateI-3-FIX 时执行。

### 剩余风险

- E2E 在本轮未实际跑通，依赖后续本地 5432 + Flyway V21 的本地 profile 启动，并需要 `accounts.account_id=3001` 种子。
- `paper_trading_orders/trades/positions` 第一版只在 controller 提供查询接口，第一版 Paper run 不会自动产生订单/成交/持仓事实，由 GateI-4 的撮合与风控回写填充。
- `created_by` 暂用 `system`；未与登录用户上下文打通，后续接入风控/审计时需要补充。
- `idempotencyKey` 字段未在第一版接入；同 publishId 重复创建会在 publishId / status 维度产生多条 run，由 GateI-4 风控边界一并完善。
- `npm audit` 4 个依赖漏洞、Vite chunk > 500 kB 警告、Ant Design React 19 / `Card.bordered` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。

### GateI-4 结论

- 后端 `mvn test` 通过且包含新增 PaperTradingRunServiceTest；前端 `npm run build` 通过。
- E2E 已在本地 PostgreSQL 5432 + Flyway V21 + 后端 local profile 环境下完整执行并通过。
- **允许进入 GateI-4-WO**，但只能在本轮变更审查/提交后单独开工。
- GateI-4 只能做风控回写、资金曲线、持仓曲线、交易复盘与异常停机，不能夹带 AI。

## GateI-3-FIX 执行记录

日期：2026-05-20

### 本轮范围

- 启动本地后端 local profile，确认 Flyway V21 已应用。
- 确认 account_id=3001 种子存在。
- 执行 `npm run test:e2e`，修复 Paper Trading E2E 选择器问题。
- 不扩展业务功能，不进入 GateI-4。

### 修改文件

- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`：修复 5 处 Playwright 选择器。

### 是否修改业务代码

否。只修改 E2E 测试选择器，未修改后端、前端业务代码、migration 或 API。

### Flyway V21 验证结果

- 后端启动日志：`Successfully validated 21 migrations`，`Current version of schema "public": 21`。
- `paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions` 表均存在（通过 API 返回 200 验证）。

### 后端 health 验证结果

- `GET /actuator/health` 返回 `{"status":"UP"}`。

### E2E 命令与结果

- 命令：`npm run test:e2e`
- 结果：**18 passed / 1 skipped**
- 耗时：1.3m

### skipped 用例说明

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID` 环境变量，为既有交易订单详情链路，不影响 GateI-3 Paper Trading 主链。

### 是否使用本地 seed

是。使用 `accounts.account_id=3001` 作为 legacy strategy account 种子（GateI-2 已补入，非 migration）。

### 是否调用外网

否。E2E fixture 全部通过本地后端 API 创建，不依赖外网交易所。

### 是否调用真实交易所

否。后端启动时 OKX adapter 因 `No route to host` 降级为 stub rejection，不影响 Paper Trading 链路。

### 是否调用 LIVE 下单

否。Paper Trading run 固定 `trade_env=SIM`，不调用任何真实交易所下单接口。

## GateI-4-WO 执行记录

日期：2026-05-20

### 本轮范围

- 实现 GateI-4 Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘与异常停机最小闭环。
- 新增 5 张监控/审计表：`paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events`。
- 新增后端 5 个领域记录、4 个 enum、5 个 repository port、5 个 JDBC 实现。
- 新增 `PaperTradingMonitorService` 应用服务并扩展 `PaperTradingApiService`、`PaperTradingController`。
- 新增 6 个响应 DTO + 1 个请求 DTO。
- 前端扩展 5 个新 Tab（风控结果 / 资金曲线 / 持仓曲线 / 交易复盘 / 异常停机），新增 5 个查询 hook + 2 个 mutation hook。
- 新增 `PaperTradingMonitorServiceTest` 单元测试。
- 同步 docs/current 文档：API、DB_SCHEMA、TESTING、WORKLOG、STATUS。

### 新增文件

后端：

- `backend/nq-infra/src/main/resources/db/migration/V22__gate_i4_paper_trading_monitor.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRiskCheckResult.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/RiskCheckStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/RiskCheckSeverity.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EquityCurveSnapshot.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PositionCurveSnapshot.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/TradeReplayRecord.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EmergencyStopEvent.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EmergencyStopTriggerType.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EmergencyStopStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRiskCheckResultRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/EquityCurveSnapshotRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PositionCurveSnapshotRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/TradeReplayRecordRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/EmergencyStopEventRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingMonitorService.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingMonitorServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRiskCheckResultRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcEquityCurveSnapshotRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPositionCurveSnapshotRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcTradeReplayRecordRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcEmergencyStopEventRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRiskCheckResultResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/EquityCurveSnapshotResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PositionCurveSnapshotResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/TradeReplayRecordResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/EmergencyStopEventResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/EmergencyStopRequestBody.java`

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java`：扩展 7 个新端点。
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/paper/PaperTradingApiService.java`：注入 `PaperTradingMonitorService` 并新增 7 个委派方法。
- `frontend/src/types/paper-trading.ts`：新增 5 类监控/事件类型。
- `frontend/src/api/paper-trading.ts`：新增 7 个监控/异常停机 API。
- `frontend/src/api/query-keys.ts`：新增 5 个监控查询 key。
- `frontend/src/hooks/usePaperTradingQuery.ts`：新增 5 个查询 hook + 2 个 mutation hook。
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：详情抽屉扩展 5 个新 Tab。
- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`：扩展 GateI-4 监控/异常停机断言。
- `docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/STATUS.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`。

### DB / Migration

- `V22__gate_i4_paper_trading_monitor.sql` 只新增 GateI-4 所需 5 张表，未修改历史 migration。
- 5 张表均包含 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段 `paper_risk_check_results.status/severity`、`emergency_stop_events.trigger_type/status` 均通过 `CHECK` 约束限制允许值。
- 外键统一指向 `paper_trading_runs.paper_run_id`。
- 索引：`idx_risk_results_run_id_time`、`idx_equity_curve_run_id_time`、`idx_position_curve_run_id_time`、`idx_replay_run_id_time`、`idx_emergency_stop_run_id_time`，均按 `(paper_run_id, time DESC)` 组织。

### 后端实现

- `nq-research` 承载领域模型、port、`PaperTradingMonitorService` 应用服务，不依赖 JDBC。
- `nq-infra` 承载 5 个 JDBC 实现，遵循既有 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB 模式。
- `nq-api` 通过 `PaperTradingApiService` 委派到 `PaperTradingMonitorService`，不直接写 SQL。
- `runRiskCheckOnce` 第一版只写最小 `BASIC_HEALTH_CHECK / PASSED / LOW`，等待具体规则在后续 Gate 实现。
- `triggerEmergencyStop` 复用 `PaperTradingRunService.stop`：`RUNNING` 时调用 stop 状态机、写入 `APPLIED`；非 RUNNING 时记录 `FAILED` 并保留原因，不引入新状态。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。

### 前端实现

- 5 个新 Tab 全部走 TanStack Query；服务端数据不进 Zustand。
- "执行风控检查" 按钮触发 `useRunRiskOnceMutation`；"紧急停机" 通过 `Modal.confirm` + `useEmergencyStopMutation`，触发后 invalidate 所有 paper-trading query。
- 第一版无图表库依赖，资金/持仓曲线均以表格呈现。
- 既有 GateI-3 创建 / 启动 / 停止 / 详情逻辑保持不变。

### 单元测试

- `PaperTradingMonitorServiceTest` 覆盖 5 个用例：风控 run-once 正常写入、风控 list 空态、运行中触发 emergency stop 应用并停机、非 RUNNING 触发 emergency stop 记 FAILED、emergency stop list 空态。
- 复用 `PaperTradingRunServiceTest` 的 in-memory 仓储以避免重复实现。

### E2E 实现

- 在 `paper-trading-run-smoke.spec.ts` 中扩展 GateI-4 链路覆盖：执行风控检查、查看 5 个新 Tab、触发紧急停机后断言 run 进入 STOPPED。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

### 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`，35 tests / 0 failures（含 `PaperTradingMonitorServiceTest` 5 用例）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- `npm run test:e2e`：本轮未在干净本地实例上执行；spec 与 fixture 已就绪，等待下一次本地完整窗口或 GateI-4-FIX 时执行。

### 剩余风险

- E2E 在本轮未实际跑通，依赖后续本地 5432 + Flyway V22 启动后端 local profile 后执行。
- 第一版风控只写 `BASIC_HEALTH_CHECK`；具体撮合回写、风控规则、资金/持仓快照定时器在后续 Gate 实现，本轮 5 张表只承载结构。
- `idempotencyKey` 仍未接入；同 paperRunId 重复触发紧急停机会写多条 FAILED 记录，符合事件流语义但需在 GateI 闭环时审视。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

### GateI-4 结论

- 后端 `mvn test` 通过且包含 5 个新增 monitor 用例；前端 `npm run build` 通过。
- E2E 待补；GateI-4 自身实现已完成，留 GateI-4-FIX 跑 E2E 与可能的选择器修复。
- 不接 AI、不接 LIVE 下单、不修改交易核心状态机；满足 `CLAUDE.md` GateI-4 边界要求。
- GateI 仍未整体完成；不创建 `docs/gates/gate-i`，等待全部 GateI-* 完成与冻结。

## GateI-4-FIX 执行记录

日期：2026-05-21

### 本轮范围

- 重启后端 local profile，确认 Flyway V22 已应用。
- 确认 5 张 GateI-4 monitor 表存在。
- 执行 `npm run test:e2e`，修复 GateI-4 E2E 选择器与组件问题。
- 不扩展业务功能，不进入 GateJ。

### 修改文件

- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`：修复 GateI-4 E2E 用例（改用 UI 操作替代 standalone request、修复 PASSED 断言、修复紧急停机 modal 选择器）。
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：将"执行风控检查"和"紧急停机"按钮移到 `PaperListSection` 外部（空态时仍可见）；将 `Modal.confirm` 改为 `modal.confirm`（通过 `App.useApp()` 获取，确保在 App context 下正确渲染）。

### 是否修改业务代码

是，但仅限 UI 布局调整和 Ant Design API 用法修正：
- 按钮从 `PaperListSection` children 移到外层（功能不变，只是空态时也可见）。
- `Modal.confirm` → `modal.confirm`（Ant Design 5.x App context 最佳实践）。
- 不修改后端、不修改 migration、不修改 API。

### Flyway V22 验证结果

- Flyway schema history 确认 version=22, description="gate i4 paper trading monitor"。
- 5 张表均存在：`paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events`。

### 后端 health 验证结果

- `GET /actuator/health` 返回 `{"status":"UP"}`。

### E2E 命令与结果

- 命令：`npm run test:e2e`
- 结果：**19 passed / 1 skipped**
- 耗时：1.4m

### skipped 用例说明

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID` 环境变量，为既有交易订单详情链路，不影响 GateI 主链。

### GateI-4-FIX 修复内容

1. GateI-4 E2E 用例原使用 Playwright `request` fixture 调用 API，但该 fixture 不共享浏览器登录态（Bearer token），导致 401。修复：改为通过 UI 操作（创建/启动/查看详情）和 UI 按钮（执行风控检查/紧急停机）完成全链路。
2. "执行风控检查"按钮原在 `PaperListSection` children 内，空态时被 `<Empty>` 替代不可见。修复：将按钮移到 `PaperListSection` 外层。
3. "紧急停机"按钮同理移到外层。
4. `Modal.confirm` 静态方法在 Ant Design 5.x + `App` wrapper 下不渲染 modal。修复：改用 `App.useApp()` 返回的 `modal.confirm`。
5. `PASSED` 文本断言因 Ant Design Tag 渲染时机需要 `.first()` 和 timeout。

### 是否调用外网

否。E2E fixture 全部通过本地后端 API 创建。

### 是否调用真实交易所

否。后端 OKX adapter 降级为 stub rejection。

### 是否调用 LIVE 下单

否。Paper Trading run 固定 `trade_env=SIM`。

### GateI-4-FIX 结论

- 后端测试通过（35 tests / 0 failures）、前端 build 通过、E2E 19 passed / 1 skipped。
- GateI 全部子阶段已完成：GateI-1-WO → GateI-2-WO → GateI-3-WO → GateI-3-FIX → GateI-4-WO → GateI-4-FIX。
- **GateI completed。**
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- GateK-PLAN 不启动 AI；AI 相关工作仍需后续另起 Gate / review，当前 AI not started。

## GateI Freeze + 文档同步

日期：2026-05-21

### 本轮范围

- GateI completed 后的文档同步与冻结归档。
- 不开发业务代码，不新增 migration，不新增 API，不改前端页面，不接 AI。

### 执行内容

- 已同步 README.md、AGENTS.md、CLAUDE.md（GateI completed, Next: GateJ-PLAN, AI not started）。
- 已同步 docs/current/README.md、docs/current/ROADMAP.md（修正过期表述）。
- 已同步 docs/README.md（补充 gate-h、gate-i 入口，写清文档使用规则）。
- 已创建 docs/gates/gate-i/（README + FREEZE_SUMMARY + 12 个归档文件）。
- 已检查并修正 docs/gates/gate-h/README.md（修正 GateI 仍在推进的过期表述）。
- 已确认 docs/DOC_RULES.md 规则完整（无需修改）。

### 新增文件

- `docs/gates/gate-i/README.md`
- `docs/gates/gate-i/FREEZE_SUMMARY.md`
- `docs/gates/gate-i/PLAN_GATEI.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_API_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_DB_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_FRONTEND_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_TEST_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_WORK_ORDER.md`（从 docs/current 复制）
- `docs/gates/gate-i/API.md`（从 docs/current 复制）
- `docs/gates/gate-i/DB_SCHEMA.md`（从 docs/current 复制）
- `docs/gates/gate-i/TESTING.md`（从 docs/current 复制）
- `docs/gates/gate-i/STATUS.md`（从 docs/current 复制）
- `docs/gates/gate-i/ROADMAP.md`（从 docs/current 复制）
- `docs/gates/gate-i/WORKLOG.md`（从 docs/current 复制）

### 修改文件

- `docs/README.md`
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/WORKLOG.md`
- `docs/gates/gate-h/README.md`

### 是否修改业务代码

否。本轮只做文档同步和冻结归档。

### 是否新增 migration

否。

### 是否新增 API

否。

### 是否改前端页面

否。

### 是否接入 AI

否。

## GateJ-PLAN 执行记录

日期：2026-05-21

### 本轮范围

- 只做 GateJ 规划文档。
- 规划 Paper Trading 稳定运行。
- 明确 GateJ-1 / GateJ-2 / GateJ-3 / GateJ-FREEZE 拆分。
- 同步当前状态、路线、API、DB、测试与工作日志入口。

### 本轮新增文件

- `docs/current/PLAN_GATEJ.md`
- `docs/current/GATEJ_API_PLAN.md`
- `docs/current/GATEJ_DB_PLAN.md`
- `docs/current/GATEJ_FRONTEND_PLAN.md`
- `docs/current/GATEJ_TEST_PLAN.md`
- `docs/current/GATEJ_WORK_ORDER.md`

### 本轮修改文件

- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `README.md`
- `AGENTS.md`
- `CLAUDE.md`

### 本轮执行内容

- 新增 GateJ 总计划，明确背景、目标、不做范围、四个子阶段、完成标准。
- 新增 GateJ API 规划，覆盖 Schedule、Heartbeat、Daily Report、Alert、Recovery、Stability Check 六类 API。
- 新增 GateJ DB 规划，覆盖 7 张新表的字段、约束、索引、JSONB 用途和幂等策略。
- 新增 GateJ 前端规划，覆盖 7 个新 Tab 和详情页增强。
- 新增 GateJ 测试规划，覆盖单元测试、集成测试、E2E 矩阵和连续运行验收。
- 新增 GateJ 工作单，拆分 GateJ-1-WO 到 GateJ-FREEZE。
- 同步 STATUS.md、ROADMAP.md、API.md、DB_SCHEMA.md、TESTING.md。
- 同步 README.md、AGENTS.md、CLAUDE.md。

### 本轮未执行内容

- 未开发 GateJ 功能代码。
- 未新增 API 实现。
- 未新增 DB migration。
- 未新增前端页面实现。
- 未接入 AI。
- 未新增 AI 模块、AI 信号、AI Paper Trading 或 AI 自动交易。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未处理 npm audit。
- 未处理 Vite chunk 警告。

### 验证记录

- 本轮只修改文档，未重新执行全量 mvn、npm、Python 测试。
- 沿用 GateI completed 验证基线：后端 35 tests / 0 failures、前端 build 通过、E2E 19 passed / 1 skipped、Python pytest/mypy/ruff 通过。
- 已执行 git status --short。
- 已检查 6 份 GateJ 规划文档存在。
- 已检查 STATUS.md 写清 GateJ-PLAN、AI not started。
- 已检查本轮变更未新增业务代码、migration、API 实现或前端页面实现。

### 下一步进入 GateJ-1-WO 的条件

- GateJ-PLAN 文档完成审查。
- GateJ-1-WO 单独开工，只做 Paper run 调度与连续运行。
- GateJ-1-WO 不得夹带 GateJ-2/3 实现。
- GateJ-1-WO 不得接入 AI。

## GateJ-1-WO 执行记录

日期：2026-05-21

### 本轮范围

- 实现 GateJ-1 Paper run 调度与连续运行最小闭环。
- 新增 3 张表：`paper_run_schedules`、`paper_run_schedule_fires`、`paper_run_heartbeats`。
- 新增后端 4 个 enum/record（`PaperRunSchedule`、`PaperRunScheduleStatus`、`PaperRunScheduleFire`、`PaperRunScheduleFireStatus`、`PaperRunHeartbeat`、`PaperRunHeartbeatStatus`）、3 个 repository port、3 个 JDBC 实现。
- 新增 `PaperRunScheduleService` 应用服务并扩展 `PaperTradingApiService`。
- 新增 1 个新 controller `PaperTradingScheduleController` 与扩展 `PaperTradingController`（增加 heartbeat 端点）。
- 新增 5 个 DTO：`PaperRunScheduleResponse`、`PaperRunScheduleCreateRequestBody`、`PaperRunScheduleStatusUpdateRequestBody`、`PaperRunScheduleFireResponse`、`PaperRunHeartbeatResponse`。
- 前端扩展 2 个新 Tab（调度计划 / 心跳），新增 4 个查询 hook + 4 个 mutation hook。
- 新增 `PaperRunScheduleServiceTest` 单元测试（11 用例）。
- 新增 `paper-trading-schedule-smoke.spec.ts` E2E 用例。

### 新增文件

后端：

- `backend/nq-infra/src/main/resources/db/migration/V23__gate_j1_paper_run_schedules.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunSchedule.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunScheduleStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunScheduleFire.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunScheduleFireStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunHeartbeat.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunHeartbeatStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRunScheduleRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRunScheduleFireRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRunHeartbeatRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperRunScheduleCreateCommand.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperRunScheduleService.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperRunScheduleServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunScheduleRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunScheduleFireRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunHeartbeatRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingScheduleController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleCreateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleStatusUpdateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleFireResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunHeartbeatResponse.java`

前端：

- `frontend/tests/e2e/paper-trading-schedule-smoke.spec.ts`

### 修改文件

后端：

- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/paper/PaperTradingApiService.java`：注入 `PaperRunScheduleService` 并新增 8 个委派方法。
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java`：扩展 2 个 heartbeat 端点。

前端：

- `frontend/src/types/paper-trading.ts`：新增 5 类调度/心跳类型。
- `frontend/src/api/paper-trading.ts`：新增 8 个调度/心跳 API。
- `frontend/src/api/query-keys.ts`：新增 3 个查询 key。
- `frontend/src/hooks/usePaperTradingQuery.ts`：新增 3 个查询 hook + 4 个 mutation hook。
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：详情抽屉扩展调度计划/心跳 2 个 Tab；Drawer 宽度从 840 调整到 1080 以避免 Tabs 溢出。

文档：

- `docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/ROADMAP.md`。
- `README.md`、`AGENTS.md`、`CLAUDE.md`。

### DB / Migration

- `V23__gate_j1_paper_run_schedules.sql` 只新增 GateJ-1 所需 3 张表，未修改历史 migration。
- 3 张表均包含 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段 `paper_run_schedules.status`、`paper_run_schedule_fires.status`、`paper_run_heartbeats.status` 均通过 `CHECK` 约束限制允许值。
- 外键统一指向 `paper_trading_runs.paper_run_id`；`paper_run_schedule_fires.schedule_id` 关联 `paper_run_schedules.schedule_id`。
- 索引：`idx_paper_run_schedules_run_id/status/next_fire`、`idx_schedule_fires_schedule_id/run_id/fired_at`、`idx_heartbeats_run_id_time`。

### 后端实现

- `nq-research` 承载领域模型、port、`PaperRunScheduleService` 应用服务，不依赖 JDBC。
- `nq-infra` 承载 3 个 JDBC 实现，遵循既有 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB 模式。
- `nq-api` 通过 `PaperTradingApiService` 委派到 `PaperRunScheduleService`，不直接写 SQL。
- `createSchedule`：校验 paperRunId 存在 + cron 表达式 5/6/7 字段校验，第一版默认 ENABLED 状态。
- `runScheduleOnce`：仅 ENABLED 状态可触发；非 ENABLED 返回 409。第一版 fire 状态固定 SUCCEEDED，不调用真实交易所。
- `runHeartbeatOnce`：根据 Paper run 状态映射 heartbeat status（RUNNING→OK / STOPPED|FAILED→STOPPED / 其他→UNKNOWN）。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。
- 第一版不实现后台常驻调度器自动触发，仅提供 run-once 手动触发。

### 前端实现

- 2 个新 Tab 全部走 TanStack Query；服务端数据不进 Zustand。
- "创建调度"按钮在调度计划 Tab 顶部触发 Modal 表单（名称、cron、时区）。
- 调度行内提供"触发记录"、"执行一次"、"启用/禁用"操作；run-once 按钮在 ENABLED 状态下可用。
- "执行心跳检查"按钮触发 `useRunHeartbeatOnceMutation`，触发后 invalidate paper-trading query。
- 第一版无图表库依赖。
- Drawer 宽度从 840 调整为 1080，避免 11 个 Tab 触发 Ant Design Tabs 溢出折叠。

### 单元测试

- `PaperRunScheduleServiceTest` 覆盖 11 个用例：
  - `createScheduleShouldInsertWithEnabledStatus`
  - `createScheduleShouldRejectMissingRun`
  - `createScheduleShouldRejectInvalidCron`
  - `updateScheduleStatusShouldTransition`
  - `updateScheduleStatusShouldRejectInvalidStatus`
  - `runScheduleOnceShouldWriteSucceededFire`
  - `runScheduleOnceShouldRejectDisabledSchedule`
  - `listFiresShouldReturnByScheduleId`
  - `runHeartbeatOnceShouldWriteRecord`
  - `runHeartbeatOnceShouldRecordStoppedWhenRunStopped`
  - `listHeartbeatsShouldReturnByRunId`

### E2E 实现

- 新增 `paper-trading-schedule-smoke.spec.ts`，覆盖：登录 → 准备 fixture → 创建并启动 Paper run → 打开详情抽屉 → 调度计划 Tab → 创建调度 → 触发 run-once → 查看 fire 记录 → 禁用调度 → 心跳 Tab → 执行心跳检查 → 校验心跳记录。
- E2E 选择器全部限定在 `drawer = page.getByLabel('Paper Trading 详情')` 范围，避免与侧边栏 menu 项冲突。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

### 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，35 tests / 0 failures（含 PaperRunScheduleServiceTest 11 用例）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- 后端 local profile 启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本 `23`。
- `npm run test:e2e`：通过，**20 passed / 1 skipped**。
- `npm run test:e2e` skipped 用例：`trading workspace / 配置订单 ID 时可打开订单详情`，未配置 `E2E_TRADE_ORDER_ID`，与 GateJ-1 主链无关。

### 剩余风险

- 第一版 fire 状态固定 `SUCCEEDED`；后台常驻调度器自动触发未实现。
- 第一版 cron 表达式仅做字段数（5/6/7）合法性校验，未做完整 cron 语义校验。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

### 边界确认

- 未进入 GateJ-2（日报、告警）。
- 未进入 GateJ-3（恢复、稳定性验收）。
- 未进入 GateJ-FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做真实 LIVE 下单。
- 未调用真实交易所下单接口。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

### GateJ-1 结论

- 后端测试通过、前端 build 通过、E2E 20 passed / 1 skipped。
- GateJ-1-WO 已完成，所有验收标准已满足。
- **允许进入 GateJ-2-WO**，但只能在本轮变更审查/提交后单独开工。
- GateJ-2-WO 只能做运行监控、日报、告警，不能夹带恢复、稳定性验收或 AI。

---

# Worklog: GateJ-2-WO

日期：2026-05-21

## 目标

GateJ-2-WO：Paper Trading 运行监控 + 日报 + 告警。在 GateJ-1 完成的调度/心跳基础上，新增日报与告警事件能力，建立监控基础。仍不接 AI、不调用真实交易所下单、不动核心状态机/策略/回测算法。

## 修改文件清单

数据库 migration（新增 1 个）：

- 新增 `backend/nq-infra/src/main/resources/db/migration/V24__gate_j2_paper_run_daily_reports_alerts.sql`。

后端 nq-research（domain / port / service / command）：

- 新增 `backend/nq-research/.../research/domain/paper/PaperRunDailyReport.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunDailyReportStatus.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunAlert.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunAlertSeverity.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunAlertStatus.java`。
- 新增 `backend/nq-research/.../research/domain/paper/port/PaperRunDailyReportRepository.java`。
- 新增 `backend/nq-research/.../research/domain/paper/port/PaperRunAlertRepository.java`。
- 新增 `backend/nq-research/.../research/application/paper/PaperRunMonitorService.java`。
- 新增 `backend/nq-research/.../research/application/paper/PaperRunDailyReportGenerateCommand.java`。
- 新增 `backend/nq-research/.../research/application/paper/PaperRunAlertCreateCommand.java`。
- 修改 `backend/nq-research/.../research/application/api/paper/PaperTradingApiService.java`。

后端 nq-infra（JDBC 实现）：

- 新增 `backend/nq-infra/.../research/infra/paper/jdbc/JdbcPaperRunDailyReportRepository.java`。
- 新增 `backend/nq-infra/.../research/infra/paper/jdbc/JdbcPaperRunAlertRepository.java`。

后端 nq-api（DTO + Controller）：

- 新增 `backend/nq-api/.../paper/api/dto/PaperRunDailyReportResponse.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunDailyReportGenerateRequestBody.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunAlertResponse.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunAlertCreateRequestBody.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunAlertAckRequestBody.java`。
- 修改 `backend/nq-api/.../paper/api/web/PaperTradingController.java`。

后端测试：

- 新增 `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperRunMonitorServiceTest.java`（12 用例）。

前端：

- 修改 `frontend/src/api/paper-trading.ts`：新增 daily-reports / alerts API 客户端方法。
- 修改 `frontend/src/api/query-keys.ts`：新增 paper-trading dailyReports / alerts query keys。
- 修改 `frontend/src/types/paper-trading.ts`：新增 PaperRunDailyReportItem / PaperRunAlertItem / 请求与响应类型。
- 修改 `frontend/src/hooks/usePaperTradingQuery.ts`：新增 query/mutation hooks。
- 修改 `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：新增"日报"、"告警"两个 Tab。

前端 E2E：

- 新增 `frontend/tests/e2e/paper-trading-daily-report-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/paper-trading-alert-smoke.spec.ts`。

文档：

- 修改 `docs/current/STATUS.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`、`docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/ROADMAP.md`。
- 修改 `CLAUDE.md`、`AGENTS.md`、`README.md`。

## DB schema 变化

- 新增表 `paper_run_daily_reports`：
  - 主键 `report_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - 唯一约束 `uq_daily_reports_run_date (paper_run_id, report_date)`，保证按日幂等。
  - 状态 `status` CHECK：`GENERATED / PARTIAL / FAILED`。
  - JSONB 字段 `report_json` 用于保存日报详细数据，明确不保存密钥/token/cookie。
- 新增表 `paper_run_alerts`：
  - 主键 `alert_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - 严重程度 `severity` CHECK：`LOW / MEDIUM / HIGH / CRITICAL`。
  - 状态 `status` CHECK：`OPEN / ACKED / RESOLVED`。
  - JSONB 字段 `event_snapshot_json` 用于保存事件快照，明确不保存密钥/token/cookie。
- 所有新增表与字段均补齐 `COMMENT ON TABLE` / `COMMENT ON COLUMN`。
- 未修改任何已有 migration。

## 后端实现

- `nq-research` 承载领域模型、port、`PaperRunMonitorService` 应用服务，不依赖 JDBC。
- `nq-infra` 承载 2 个 JDBC 实现，遵循既有 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB 模式。
- `nq-api` 通过 `PaperTradingApiService` 委派到 `PaperRunMonitorService`，不直接写 SQL。
- `generateDailyReport`：校验 paperRunId 存在 + reportDate 缺省时使用当前 UTC 日期，按 (paperRunId, reportDate) 通过 ON CONFLICT 实现幂等，alert_count 实时统计当日告警总数。
- `createAlert`：校验 paperRunId 存在 + severity 校验，新建告警状态固定 OPEN。无效 severity 返回 400，其他业务校验返回 404。
- `ackAlert`：OPEN → ACKED 转换；ACKED 状态再次 ack 幂等；RESOLVED 状态拒绝 ack 返回 409。
- `resolveAlert`：任意非 RESOLVED → RESOLVED；RESOLVED 状态再次 resolve 幂等。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。

## 前端实现

- 2 个新 Tab（日报、告警）全部走 TanStack Query；服务端数据不进 Zustand。
- 日报 Tab 顶部"生成今日日报"按钮触发 `useGenerateDailyReportMutation`；第一版传空 `{}`，由后端使用当前 UTC 日期。
- 告警 Tab 顶部"创建测试告警"按钮触发 `useCreateAlertMutation`，默认创建 SYSTEM_NOTICE / LOW 告警，便于本地 smoke。
- 告警行内提供"确认"、"解决"按钮；按当前 status 条件展示。
- 第一版无图表库依赖。

## 单元测试

- `PaperRunMonitorServiceTest` 覆盖 12 个用例：
  - `generateDailyReportShouldCreateReport`
  - `generateDailyReportShouldUseCurrentDateWhenNull`
  - `generateDailyReportShouldRejectMissingRun`
  - `listDailyReportsShouldReturnByRunId`
  - `createAlertShouldInsertOpenAlert`
  - `createAlertShouldRejectInvalidSeverity`
  - `ackAlertShouldTransitionToAcked`
  - `ackAlertShouldBeIdempotent`
  - `ackAlertShouldRejectResolved`
  - `resolveAlertShouldTransitionToResolved`
  - `resolveAlertShouldBeIdempotent`
  - `listAlertsShouldFilterByStatus`

## E2E 实现

- 新增 `paper-trading-daily-report-smoke.spec.ts`：登录 → 准备 fixture → 创建并启动 Paper run → 打开详情抽屉 → 日报 Tab → 生成今日日报 → 校验列表 → 再次生成确认幂等。
- 新增 `paper-trading-alert-smoke.spec.ts`：登录 → 准备 fixture → 创建并启动 Paper run → 打开详情抽屉 → 告警 Tab → 创建测试告警 → 校验列表 → 确认告警 (OPEN → ACKED) → 解决告警 (ACKED → RESOLVED)。
- E2E 选择器全部限定在 `drawer = page.getByLabel('Paper Trading 详情')` 范围。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

## 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，BUILD SUCCESS，35 tests / 0 failures（含 PaperRunMonitorServiceTest 12 用例）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- 后端 local profile 启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本 `24`。
- `npm run test:e2e`：通过，**22 passed / 1 skipped**。
- `npm run test:e2e` skipped 用例：`trading workspace / 配置订单 ID 时可打开订单详情`，未配置 `E2E_TRADE_ORDER_ID`，与 GateJ-2 主链无关。

## 修复记录

- 初次 E2E 执行时发现 `PaperRunDailyReportGenerateRequestBody.reportDate` 标注了 `@NotNull`，与 `PaperRunMonitorService` 对 `reportDate = null` 时默认使用当日的实现冲突，前端调用空请求体被 400 拒绝。修复：移除该字段的 `@NotNull` 注解，允许空 body 走默认当日。
- 初次 alert E2E 用 `tr.filter({hasText: alertId})` 定位行失败：表格不显示 alertId 列。修复：改用 `tr.filter({hasText: '手动测试告警'})` 通过标题文本定位。

## 剩余风险

- 第一版日报字段（total_equity / daily_pnl / max_drawdown 等）使用占位 `BigDecimal.ZERO`，未与 equity_curve_snapshots 实际数据联动；属于 GateJ-2 范围之外的增量优化。
- 第一版告警来源（HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED）尚未由后台监控自动产出，仅支持手动 POST 创建；自动监控产出预留到 GateJ-3。
- 外部通知（邮件、Slack、钉钉）按工作单边界明确不做。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

## 边界确认

- 未进入 GateJ-3（恢复、稳定性验收）。
- 未进入 GateJ-FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做真实 LIVE 下单。
- 未调用真实交易所下单接口。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未引入图表库。
- 未做外部通知集成。

## GateJ-2 结论

- 后端测试通过、前端 build 通过、E2E 22 passed / 1 skipped。
- GateJ-2-WO 已完成，所有验收标准已满足。
- **允许进入 GateJ-3-WO**，但只能在本轮变更审查/提交后单独开工。
- GateJ-3-WO 只能做异常恢复、失败重试、稳定性验收结构，不能夹带连续运行验收或 AI。

---

# Worklog: GateJ-3-WO

日期：2026-05-22

## 目标

GateJ-3-WO：Paper Trading 异常恢复、失败重试、运行稳定性检查与自动告警最小落库。在 GateJ-1/2 基础上补齐恢复事件、稳定性验收结构、HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小能力。仍不接 AI、不调用真实交易所下单、不动核心状态机/策略/回测算法。

## 修改文件清单

数据库 migration（新增 1 个）：

- 新增 `backend/nq-infra/src/main/resources/db/migration/V25__gate_j3_paper_run_recovery_stability.sql`。

后端 nq-research（domain / port / service / command）：

- 新增 `PaperRunRecoveryEvent` / `PaperRunRecoveryType` / `PaperRunRecoveryStatus`。
- 新增 `PaperRunStabilityCheck` / `PaperRunStabilityCheckStatus`。
- 新增 `PaperRunRecoveryEventRepository`、`PaperRunStabilityCheckRepository` port。
- 扩展 `PaperRunAlertRepository`（新增 `countCriticalOpenByRunIdAndDateRange` / `countByRunIdAndTypeAndDateRange`）。
- 扩展 `PaperRunDailyReportRepository`（新增 `countByRunIdAndDateRange`）。
- 扩展 `PaperRunHeartbeatRepository`（新增 `countByRunIdAndDateRange` / `findLatestByRunId`）。
- 扩展 `PaperRunScheduleFireRepository`（新增 `listByRunIdAndStatus` / `countByRunIdAndStatusAndDateRange`）。
- 新增 `PaperRunRecoveryService`、`PaperRunStabilityCheckService`、`PaperRunMonitorRunService`。
- 新增 `PaperRunRecoverCommand` / `PaperRunRetryFailedStepCommand` / `PaperRunStabilityCheckGenerateCommand`。
- 修改 `PaperTradingApiService`：注入新服务并暴露恢复 / 稳定性验收 / 监控守护方法，统一 404 / 400 / 409 错误码映射。

后端 nq-infra（JDBC 实现）：

- 新增 `JdbcPaperRunRecoveryEventRepository` / `JdbcPaperRunStabilityCheckRepository`。
- 修改 `JdbcPaperRunAlertRepository` / `JdbcPaperRunDailyReportRepository` / `JdbcPaperRunHeartbeatRepository` / `JdbcPaperRunScheduleFireRepository`：补齐 port 新增方法。

后端 nq-api（DTO + Controller）：

- 新增 `PaperRunRecoveryEventResponse` / `PaperRunRecoverRequestBody` / `PaperRunRetryFailedStepRequestBody`。
- 新增 `PaperRunStabilityCheckResponse` / `PaperRunStabilityCheckGenerateRequestBody`。
- 新增 `PaperRunMonitorRunOnceResponse`。
- 修改 `PaperTradingController`：新增 7 个 endpoints（recovery-events / recover / retry-failed-step / stability-checks GET/POST/detail / monitor/run-once）。

后端测试：

- 新增 `PaperRunRecoveryServiceTest`（9 用例）。
- 新增 `PaperRunStabilityCheckServiceTest`（10 用例）。
- 新增 `PaperRunMonitorRunServiceTest`（8 用例）。
- 修改 `PaperRunMonitorServiceTest` / `PaperRunScheduleServiceTest`：补齐 port 新增方法的 in-memory 实现，保持原有 12 + 11 用例通过。

前端：

- 修改 `frontend/src/types/paper-trading.ts`：新增 PaperRunRecoveryEventItem / PaperRunStabilityCheckItem / PaperRunMonitorRunOnceResponse 等类型。
- 修改 `frontend/src/api/paper-trading.ts`：新增 listRecoveryEvents / recover / retryFailedStep / listStabilityChecks / generateStabilityCheck / runMonitorOnce。
- 修改 `frontend/src/api/query-keys.ts`：新增 recoveryEvents / stabilityChecks query keys。
- 修改 `frontend/src/hooks/usePaperTradingQuery.ts`：新增 6 个 query/mutation hooks。
- 修改 `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：新增"恢复事件"、"稳定性验收"两个 Tab；Drawer 宽度从 1080 调整为 1280。

前端 E2E：

- 新增 `frontend/tests/e2e/paper-trading-recovery-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/paper-trading-stability-check-smoke.spec.ts`。

文档：

- 修改 `docs/current/STATUS.md`、`WORKLOG.md`、`TESTING.md`、`API.md`、`DB_SCHEMA.md`、`ROADMAP.md`。
- 修改 `CLAUDE.md`、`AGENTS.md`、`README.md`。

## DB schema 变化

- 新增表 `paper_run_recovery_events`：
  - 主键 `recovery_event_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - CHECK：`recovery_type` ∈ MANUAL_RECOVER / RETRY_FAILED_STEP / HEARTBEAT_LAG_RECOVER / SCHEDULE_FIRE_RECOVER；`status` ∈ STARTED / SUCCEEDED / FAILED / SKIPPED。
  - JSONB 字段 `request_json` / `result_json` 注释明确不保存密钥/token/cookie。
- 新增表 `paper_run_stability_checks`：
  - 主键 `stability_check_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - CHECK：`status` ∈ PASSED / FAILED / PARTIAL；`check_window_end > check_window_start`；`uptime_ratio` ∈ [0, 1]。
  - 唯一约束 `uq_stability_checks_run_window`。
  - JSONB 字段 `summary_json` 注释明确不保存密钥/token/cookie。
- 所有新增表与字段均补齐 `COMMENT ON TABLE` / `COMMENT ON COLUMN`。
- 未修改任何已有 migration。

## 后端实现要点

- `recover` / `retryFailedStep`：根据 Paper run 状态映射 recovery status（STOPPED → SKIPPED；其它 → SUCCEEDED）。每次记录独立事件，不幂等。
- `generateStabilityCheck`：校验窗口合法（end > start）+ paperRunId 存在；按 `(paper_run_id, check_window_start, check_window_end)` ON CONFLICT 幂等；按第一版口径计算 status / uptime_ratio。
- `runOnce` 监控守护：检测 heartbeat lag（阈值固定 300s，仅对 RUNNING 状态生效）+ schedule fire failed（最近 5 分钟）；每种 alert_type 在 5 分钟去重窗口内不重复创建；第一版只落库，不外发。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。

## 前端实现要点

- 2 个新 Tab 全部走 TanStack Query；服务端数据不进 Zustand。
- 稳定性验收 Tab 显式备注第一版口径并明确不等于 GateJ-FREEZE 最终验收。
- 第一版无图表库依赖。

## 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，BUILD SUCCESS（GateJ-3 新增 27 用例 + 既有用例全部通过）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- 后端 local profile 启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本 `25`。
- `npm run test:e2e`：通过，24 passed / 1 skipped；唯一 skipped 与 GateJ-3 无关。

## 修复记录

- 监控守护 dedupe 单元测试在固定 Clock 下失败：因 end 边界 exclusive 导致 `createdAt == now` 被排除。修复：监控守护查询时使用 `now.plusSeconds(1)` 作为上界。
- 13 个 Tab 触发 Ant Design Tabs 溢出折叠："恢复事件 / 稳定性验收"被收进 ellipsis 菜单且 tabpanel 不切换。修复：Drawer 宽度 1080 → 1280。
- 新增 port 方法导致既有测试 in-memory repo 编译失败：补全相关方法。

## 剩余风险

- 第一版 `uptime_ratio` 粗略口径（PASSED=1.0 / PARTIAL=0.9 / FAILED 有心跳=0.5 / 无心跳=0），未按时间精确加权。
- 自动告警去重仅按 alert_type + 5 分钟时间窗口；未做 fire_id / event 维度去重。
- HEARTBEAT_LAG 阈值固定 300 秒；未提供运行时配置入口。
- 外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）按边界明确不做。
- 自动恢复策略引擎按边界明确不做（仅落库 alert，不自动触发 recover）。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning 仍在。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

## 边界确认

- 未进入 GateJ-FREEZE 正式验收归档（1h/24h/7d 由 GateJ-FREEZE 独立执行）。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做真实 LIVE 下单。
- 未调用真实交易所下单接口。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机、策略核心算法、回测核心算法。
- 未引入图表库。
- 未做外部通知集成。
- 未做自动恢复策略引擎。
- 未把 GateJ 整体写为 completed（仅 GateJ-3-WO completed）。
- 未把 AI 写为 started。

## GateJ-3 结论

- 后端测试通过、前端 build 通过、E2E 24 passed / 1 skipped。
- GateJ-3-WO 已完成，所有验收标准已满足。
- **允许进入 GateJ-FREEZE**，但只能在本轮变更审查/提交后单独开工；GateJ-FREEZE 只能做 1h/24h/7d 连续运行验收与冻结，不能夹带 AI。

---

# Worklog: DOC-CLEAN-2

日期：2026-05-22

## 目标

在 GateJ-3-WO completed、Next: GateJ-FREEZE 阶段执行一次文档梳理：让 `docs/current/` 只承载当前事实和 GateJ 阶段规划，不再保留已冻结 Gate 的计划副本；让 `docs/gates/` 只承载已完成 Gate 的冻结卷宗；让根目录 README / AGENTS / CLAUDE 与 `docs/README.md` / `docs/current/README.md` 入口清晰、重复最少。本轮不动业务代码、API、migration、前端页面实现。

## 删除的冗余文档（12 个）

`docs/current/` 删除以下 12 个 GateH / GateI 计划副本（已通过 `diff -q` 与 `docs/gates/gate-h/`、`docs/gates/gate-i/` 中的冻结副本逐一比对，全部 `[same]`）：

- `docs/current/PLAN_GATEH.md`、`GATEH_API_PLAN.md`、`GATEH_DB_PLAN.md`、`GATEH_FRONTEND_PLAN.md`、`GATEH_TEST_PLAN.md`、`GATEH_WORK_ORDER.md`
- `docs/current/PLAN_GATEI.md`、`GATEI_API_PLAN.md`、`GATEI_DB_PLAN.md`、`GATEI_FRONTEND_PLAN.md`、`GATEI_TEST_PLAN.md`、`GATEI_WORK_ORDER.md`

## 归档的历史文档

本轮无新增归档：

- 上述 12 个 GateH / GateI 计划副本已在 `docs/gates/gate-h/` 与 `docs/gates/gate-i/` 中保存为 Gate 冻结卷宗，无需另行归档。
- `docs/archive/{gate-inputs,legacy-root-docs,rc1}/` 既有结构清晰，本轮不调整。

## 优化的入口文档

- `docs/README.md`：移除 "Next: GateJ-PLAN" 等过期描述；同步至 `GateJ-3-WO completed / Next: GateJ-FREEZE`；新增 GateJ 规划与 DOC_CLEAN_REPORT 入口；新增"已完成 Gate 的计划文档只保留在 `docs/gates/gate-x/`，不在 `docs/current/` 重复"的规则说明。
- `docs/current/README.md`：从 "GateI completed / Next: GateJ-PLAN" 同步至 `GateJ-3-WO completed / Next: GateJ-FREEZE`；新增 GateJ 规划文件清单与历史 Gate 冻结卷宗指引；明确 GateJ-FREEZE 不夹带 AI / 新业务功能。
- `README.md`：移除已删除的 `docs/current/PLAN_GATEI.md`、`docs/current/GATEI_WORK_ORDER.md` 引用，改为指向当前 GateJ 规划文档；扩展"当前明确不做"清单（含外部通知 / 自动恢复策略引擎）；明确 E2E skipped 与 GateJ 主链无关。
- `CLAUDE.md` / `AGENTS.md`：在 Current stage 之外新增"GateJ-FREEZE 允许范围 / 禁止范围"小节，明确 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不创建 `docs/gates/gate-j/` 除非 GateJ completed。

## 新增文档

- `docs/current/DOC_CLEAN_REPORT.md`：本轮清理报告（删除/归档/保留清单、最终结构、未删除但仍需观察的文件、当前结论）。

## docs/current 最终结构

```
docs/current/
├── README.md, STATUS.md, ROADMAP.md, WORKLOG.md, TESTING.md
├── API.md, DB_SCHEMA.md, MODULES.md, ARCHITECTURE.md, RUNBOOK.md
├── PLAN_GATEJ.md, GATEJ_{API,DB,FRONTEND,TEST}_PLAN.md, GATEJ_WORK_ORDER.md
└── DOC_CLEAN_REPORT.md
```

不再保留 GateH / GateI 计划副本。

## docs/gates 最终结构

```
docs/gates/{README.md, gate-a/, ..., gate-g/, gate-h/, gate-i/}
```

`gate-j/` 不存在，待 GateJ-FREEZE 通过后再创建。

## 已修正的过期状态

- `docs/README.md` 中 "Next: GateJ-PLAN"。
- `docs/current/README.md` 中 "GateI completed / Next: GateJ-PLAN"。
- `README.md` 中已删除的 `docs/current/PLAN_GATEI.md` / `GATEI_WORK_ORDER.md` 引用。

## 边界确认

- 未修改 backend / frontend / research 业务代码。
- 未新增 migration、API 实现。
- 未改前端页面实现。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未创建 `docs/gates/gate-j/`。
- 未把 GateJ 写为 completed。
- 未把 GateK 写为 started。
- 未把 AI 写为 started。
- 未删除 `docs/gates/gate-h/`、`docs/gates/gate-i/`、`docs/templates/`、`docs/DOC_RULES.md`。
- 未删除仍有历史价值的 `docs/archive/` 内容。

## 验证

- `git status --short`：仅 docs 路径下的删除/修改/新增；无业务代码、migration、API 实现、前端页面实现变更。
- 因本轮只动文档，未重跑 `mvn test`、`npm run build`、`npm run test:e2e`、Python `pytest/mypy/ruff`；沿用 GateJ-3-WO 的通过基线（mvn BUILD SUCCESS / Flyway V25 / npm build / E2E 24 passed 1 skipped）。

## DOC-CLEAN-2 结论

- 文档结构已收口到 GateJ-FREEZE 前稳定状态。
- 当前事实唯一指向 `docs/current/`；已完成 Gate 的计划文档不在 `docs/current/` 与 `docs/gates/` 之间重复。
- README / AGENTS / CLAUDE / docs/README / docs/current/README 全部同步到 `GateJ-3-WO completed / Next: GateJ-FREEZE / AI not started / GateK not started`。
- 允许继续进入 GateJ-FREEZE，但 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新业务功能。

---

# Worklog: PRE-FREEZE-CODE-AUDIT

日期：2026-05-22

## 目标

在 GateJ-FREEZE 之前执行前置代码 / 文档 / 实现真实性 / 运行链路审查。本轮不做功能开发、不修业务代码、不接 AI、不创建 `docs/gates/gate-j/`。

## 范围

按要求覆盖 14 类审查：
1. 文档状态一致性
2. 实现真实性与文档一致性
3. 后端模块边界
4. 数据库 / Flyway / 注释 / 约束 / 索引
5. Paper Trading 主链完整性
6. Schedule / Heartbeat / Report / Alert / Recovery / Stability 运行链
7. API 命名、DTO、错误处理、分页、幂等
8. 前端页面与数据层结构
9. E2E 稳定性与测试数据幂等
10. Python research 模块
11. Paper / LIVE 隔离
12. AI 未接入与未来 AI 接入边界
13. GateJ-FREEZE 验收准备度
14. 技术债与非阻塞风险分级

## 修改文件清单

文档：
- 新增 `docs/current/PRE_FREEZE_AUDIT_REPORT.md`。
- 新增 `docs/current/PRE_FREEZE_AUDIT_FIX_PLAN.md`。
- 新增 `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`。
- 修改 `docs/current/STATUS.md`：同步阶段为 `PRE-FREEZE-CODE-AUDIT completed`，写明 P0/P1 统计与下一步条件。
- 修改 `docs/current/WORKLOG.md`：追加本轮审查记录。
- 修改 `docs/current/TESTING.md`：追加本轮验证记录。
- 修改 `README.md` / `AGENTS.md` / `CLAUDE.md`：同步阶段表述与下一步条件。

代码：
- **未修改** backend、frontend、research/py 任何业务代码。
- **未新增** Flyway migration、API、前端页面实现。

## 是否修改业务代码

否。本轮纯文档审查与状态同步。

## 是否新增 migration

否。

## 是否新增 API

否。

## 是否改前端页面实现

否。

## 是否接入 AI

否。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 docs/current 与根目录入口文档变更 |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，0 failures / 0 errors（archunit ModuleBoundary 6、PackageBoundary 1、nq-app suite 35 全部通过；Paper 单元测试 PaperTradingRunService 4 + PaperTradingMonitorService 5 + PaperRunScheduleService 11 + PaperRunMonitorService 12 + PaperRunRecoveryService 9 + PaperRunStabilityCheckService 10 + PaperRunMonitorRunService 8 全部通过）|
| `npm run build` | 通过 | Vite 通过，dist/index.js ≈ 1.48 MB（gzip 446 kB），仍有 chunk > 500 kB 警告 |
| `npm run test:e2e` | **未在本轮重跑** | 沿用 GateJ-3-WO 24 passed / 1 skipped 基线；P1-1 要求 GateJ-FREEZE 入场前补跑 |
| `python -m pytest -q` | **未在本轮重跑** | 当前 shell 仅 WindowsApps stub（`python.exe` exit 49），无真实 Python 解释器；沿用 BASELINE-FIX-2 / GateJ-3 通过基线；P1-2 要求 GateJ-FREEZE 入场前补跑 |
| `python -m mypy src` | **未在本轮重跑** | 同上；P1-2 |
| `python -m ruff check .` | **未在本轮重跑** | 同上；P1-2 |

## P0 / P1 / P2 / P3 统计

- P0：0
- P1：4（P1-1 入场前重跑 E2E；P1-2 入场前重跑 Python；P1-3 PaperTradingPage 重构，不阻塞；P1-4 验收记录模板，已闭环）
- P2：11（详见 PRE_FREEZE_AUDIT_REPORT.md 第 25 节）
- P3：4（详见 PRE_FREEZE_AUDIT_REPORT.md 第 29 节）

## 是否允许进入 GateJ-FREEZE

允许。GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新业务功能。入场前必须重跑一次 `npm run test:e2e` 与 Python `pytest/mypy/ruff` 确认基线。

## 边界确认

- 未修改 backend / frontend / research 业务代码。
- 未新增 migration、API、前端页面实现。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未创建 `docs/gates/gate-j/`。
- 未把 GateJ 写为 completed。
- 未把 GateK 写为 started。
- 未把 AI 写为 started。
- 未把失败验证写成通过：E2E 与 Python 本轮未执行的部分均明确标记为「未在本轮重跑」，并通过 P1-1 / P1-2 列入 GateJ-FREEZE 入场前的必做项。

## 结论

- 文档、代码、DB、API、前端、E2E、Python、Paper/LIVE 隔离、AI 边界、模块边界全部一致。
- Paper Trading 主链完整。
- GateJ-FREEZE 准备度就绪。
- 允许进入 GateJ-FREEZE。详见 `PRE_FREEZE_AUDIT_REPORT.md` 第 30 节与 `PRE_FREEZE_AUDIT_FIX_PLAN.md` 第 9 节。

---

# Worklog: PRE-FREEZE-CODE-AUDIT-SECOND-PASS

日期：2026-05-22

## 目标

Codex 接手执行 PRE-FREEZE-CODE-AUDIT 二次审查与实际验证，复核 Claude 第一轮结论，补齐第一轮未实际执行的 E2E 与 Python 基线，并判断是否允许进入 GateJ-FREEZE。

## 本轮范围

- 复核 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。
- 实际执行后端测试、前端 build、完整 E2E、Python pytest/mypy/ruff。
- 二次抽查 API、DB、Paper/LIVE 隔离和 AI 边界。
- 只更新文档，不修业务代码。

## 修改文件清单

- `docs/current/PRE_FREEZE_AUDIT_REPORT.md`
- `docs/current/PRE_FREEZE_AUDIT_FIX_PLAN.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `README.md`
- `AGENTS.md`
- `CLAUDE.md`

## 新增文件清单

无。

## 是否修改业务代码

否。

## 是否新增 migration / API / 前端页面实现

否。

## 是否接入 AI

否。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；Vite chunk > 500 kB 警告仍存在 |
| `cd frontend && npm run test:e2e` | 通过 | 后端 local profile 启动成功；Flyway 当前版本 25；Playwright 24 passed / 1 skipped / 0 failed |
| `cd research/py && python -m pytest -q` | 通过 | 2 passed |
| `cd research/py && python -m mypy src` | 通过 | Success: no issues found in 8 source files |
| `cd research/py && python -m ruff check .` | 通过 | All checks passed |

## 实现真实性二次抽查

- API：指定 20 个 GateJ 主链 endpoint 均存在于 `PaperTradingController` / `PaperTradingScheduleController`，对应 DTO 与 `PaperTradingApiService` / application service 委派存在。
- DB：V21-V25 覆盖 16 张 Paper 表；COMMENT ON TABLE / COMMENT ON COLUMN、CHECK、FK、关键 UNIQUE、关键 index 均存在。
- 前端：`/paper-trading` 详情抽屉 15 个 Tab 存在，并通过 TanStack Query / Axios client 对应后端能力。
- E2E：完整 25 tests total，GateJ 主链 spec 全部执行通过；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路。
- Python：offline research 工具链 pytest/mypy/ruff 全部通过。

## Paper / LIVE / AI 边界

- `backend/nq-research/.../application/paper/**` 与 `backend/nq-api/.../paper/**` 未发现 `TradingAdapter`、`placeOrder`、`cancelOrder`、`RestTemplate`、`WebClient`、`HttpClient` 调用。
- schedule / heartbeat / daily report / alert / recover / retry / stability / monitor run-once 均只写本地 DB 或聚合本地状态。
- emergency stop 只调用 `PaperTradingRunService.stop` 停止 Paper run，不调用真实交易所撤单。
- `backend` / `frontend/src` / `research/py` 未发现 OpenAI / Anthropic / LLM provider / AI Signal / AI Trading 业务接入。

## 新发现分级

- P0：0。
- P1：0。Claude 第一轮 P1-1 / P1-2 已由本轮实际验证关闭；P1-3 不阻塞；P1-4 已闭环。
- P2：新增 1 项前端 runtime warning 集合（Ant Design React 19 compatibility、`Card.bordered`、`Modal.destroyOnClose`、`useForm` 未连接、`Descriptions` span 合计不匹配），不阻塞 GateJ-FREEZE。
- P3：0。

## 边界确认

- 未执行 GateJ-FREEZE 1h/24h/7d 连续运行验收。
- 未创建 `docs/gates/gate-j/`。
- 未把 GateJ 写成 completed。
- 未把 GateK 写成 started。
- 未把 AI 写成 started。
- 未把失败验证写成通过。

## 结论

允许进入 GateJ-FREEZE，但必须在本轮审查报告提交后单独开工。GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

# Worklog: AUDIT-FIX

## 本轮目标

关闭 FULL_SECURITY_AUDIT 报告中阻塞 GateJ-FREEZE 的两项问题：旧 OKX dome 验收脚本 P1、Windows excluded port 导致的 E2E 端口失败。本轮不新增业务功能、不新增 API、不新增 migration、不接 AI、不修改交易下单/风控/撮合/恢复/调度核心逻辑。

## 修改文件清单

- `scripts/gated_okx_dome_verify.ps1`
- `docs/archive/scripts/gated_okx_dome_verify.ps1`
- `frontend/playwright.config.ts`
- `frontend/playwright.config.js`
- `frontend/tests/e2e/run-e2e.mjs`
- `frontend/vite.config.ts`
- `frontend/vite.config.js`
- `frontend/.env.example`
- `docs/current/API.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/AUDIT_FIX_REPORT.md`

## P1 处理

- 旧 `scripts/gated_okx_dome_verify.ps1` 已从可执行 `scripts/` 区域移出，归档到 `docs/archive/scripts/gated_okx_dome_verify.ps1` 作为历史证据。
- 原 `scripts/gated_okx_dome_verify.ps1` 仅保留安全阻断 stub，明确旧 `/__gated/**` 是历史路径，GateJ 不允许执行该脚本，不得用于真实交易验收。
- `docs/current/API.md` 已再次确认当前正式 HTTP API 统一使用 `/api/**`，`/__gated/**` 不属于当前可执行 API。

## E2E 端口处理

- `frontend/playwright.config.ts` 默认 `baseURL` 和 Vite webServer 端口从 `4173` 调整为 `5179`。
- `frontend/tests/e2e/run-e2e.mjs` Vite 启动端口从 `4173` 调整为 `5179`。
- `frontend/vite.config.ts` Vite dev / preview 默认端口从 `4173` 调整为 `5179`。
- `frontend/.env.example` 中 `E2E_BASE_URL` 同步为 `http://127.0.0.1:5179`。
- 原因：当前 Windows TCP excluded range 包含 `4141-4240`，`4173` 会触发 `EACCES`。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 变更限定在 AUDIT-FIX 范围；上一轮 `FULL_SECURITY_AUDIT_REPORT.md` 仍为未跟踪新增报告 |
| `git diff --stat` | 已执行 | 用于确认变更规模 |
| `git diff -- scripts/gated_okx_dome_verify.ps1 docs/archive/scripts/gated_okx_dome_verify.ps1 frontend/playwright.config.ts frontend/tests/e2e/run-e2e.mjs docs/current/API.md docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md` | 已执行 | 用于确认 P1 与 E2E 端口修复 diff |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 chunk > 500 kB 警告 |
| `cd frontend && npm run test:e2e` | 通过 | 首次在后端未启动时失败于 `127.0.0.1:18888 ECONNREFUSED`；启动后端 local profile 后复跑通过，Vite 监听 `127.0.0.1:5179`，结果 24 passed / 1 skipped / 0 failed |

## 结论

- FULL_SECURITY_AUDIT 登记的 P1 已关闭。
- E2E `4173 EACCES` 端口问题已关闭，当前 E2E 使用 `5179` 并已通过完整回归。
- 建议允许重新进入 GateJ-FREEZE 判断；GateJ-FREEZE 必须单独开工，只做 1h / 24h / 7d 连续运行验收与冻结。

## 边界确认

- 未新增后端业务功能。
- 未新增前端业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI。
- 未修改交易下单、风控、撮合、恢复、调度核心逻辑。
- 未执行真实 OKX / Binance 下单脚本。
- 未读取或输出 `.env`、私钥、Token、交易所凭证明文。

---

# Worklog: GateJ-FREEZE-FIX

日期：2026-05-28

## 本轮目标

修复 GateJ-FREEZE ECS 部署后的两个阻塞问题：登录页仍展示本地联调敏感信息；服务器 `users.password_hash` 存在非 BCrypt 值导致 `/api/auth/login` 返回 401。本轮只允许修改登录页安全展示、auth 初始化/部署脚本、freeze 部署文档，不新增 API、不新增 migration、不接 AI/DH/真实交易。

## 根因

- 登录页仍保留旧本地联调说明，生产/freeze 构建中展示 legacy console gate、本地端口、默认账号密码、认证 API 和 Authorization header 示例。
- 服务器日志 `BCrypt non-hash warning` 表明登录接口已到达认证逻辑，但数据库中的目标用户 `password_hash` 不是 BCrypt 格式；因此 Nginx 代理和接口连通性不是根因。

## 修改文件清单

- `frontend/src/pages/login/LoginPage.tsx`
- `frontend/src/styles/index.css`
- `frontend/src/router/RequireAuth.tsx`
- `frontend/src/pages/dashboard/DashboardPage.tsx`
- `frontend/src/components/page/ListPageShell.tsx`
- `frontend/src/pages/{strategies,schedules,runs,research,backtests,evaluations,publishes}/*.tsx`
- `frontend/src/utils/env.ts`
- `frontend/src/store/auth-store.ts`
- `backend/nq-app/src/main/resources/application-freeze.yml`
- `deploy/.env.freeze.example`
- `deploy/docker-compose.freeze.yml`
- `scripts/seed-freeze-user.sh`
- `scripts/build-freeze-release.ps1`
- `docs/current/GATEJ_FREEZE_DEPLOYMENT.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- 登录页只保留 `NexusQuant 控制台`、用户名、密码、登录按钮和错误提示；移除默认表单值，前端不再展示默认账号/密码。
- 清理会进入 production bundle 的旧 legacy console gate 和认证协议展示文案，确保 `frontend/dist` 不含指定敏感串。
- 新增 `freeze` profile，连接服务器 PostgreSQL、启用 Flyway、禁用 `local` 默认 seed users，避免启动时把固定本地用户 hash 写回服务器库。
- 新增 `scripts/seed-freeze-user.sh`：从 `.env.freeze` 或进程环境读取 `NQ_FREEZE_ADMIN_USERNAME` / `NQ_FREEZE_ADMIN_PASSWORD`，使用 PostgreSQL 容器内 `pgcrypto` 生成 BCrypt hash，幂等 upsert 用户并授予 `ADMIN / OPERATOR / VIEWER`。
- 更新 release 打包脚本，确保 `seed-freeze-user.sh` 进入 release 包，并在 `RELEASE_INFO.md` 写明 seed 步骤。
- 更新 freeze 部署文档，固定顺序为：启动 compose -> seed freeze user -> curl 登录验证 -> 浏览器登录验证 -> 健康检查与连续验收。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 chunk > 500 kB 警告 |
| `rg -n "<redacted-local-test-password>\|18888\|legacy console gate\|/api/auth/login\|<redacted-authorization-header-prefix>" frontend/dist` | 通过 | 无命中 |
| `rg -n "/api/auth/me" frontend/dist` | 通过 | 无命中 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 生成 `release/nq-gatej-freeze-release.zip`；首次因沙箱无法写入本机 Maven repository tracking file 失败，提权重跑通过 |
| `jar tf backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar \| Select-String application-freeze.yml` | 通过 | jar 内包含 `BOOT-INF/classes/application-freeze.yml` |

## 新 release 包

- `release/nq-gatej-freeze-release.zip`
- 大小：约 29.5 MiB。
- release 包不提交 Git；`.gitignore` 已忽略 `release/`。

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易、AI、DH。
- 未提交真实密码、release zip、jar、dist、logs、dump 或 freeze-evidence。
- GateJ 仍未写为 completed；需要重新部署新 release 后再做首次启动验收。

---

# Worklog: GateJ-FREEZE-FIX-SECOND-PASS

日期：2026-05-28

## 本轮目标

复查 GateJ-FREEZE-FIX 后是否仍残留生产/freeze 不应出现的登录页敏感信息、默认账号密码、local profile、错误认证初始化或 release/Git 污染。本轮只允许修复审查发现的 P0/P1/P2 阻塞项，不新增业务功能、API、migration，不接 AI/DH/真实交易。

## 本轮修复

- 清理 `frontend/.env.example` 和 `frontend/README.md` 中的默认测试密码展示。
- 清理 `frontend/vite.config.*`、`frontend/playwright.config.*` 中旧 legacy console gate 注释。
- 清理后端注释和 E2E suite 名称中的旧 legacy console gate 标签，不改变业务逻辑或测试断言。
- 新增 `docs/current/GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区仅包含 GateJ-FREEZE-FIX 与本轮 second pass 范围修改；release/dist 等产物未进入 Git |
| 源码敏感词扫描 | 已执行 | 阻塞残留已修复；剩余命中均为允许项或历史文档记录 |
| `rg ... frontend/dist` | 通过 | 无敏感/旧联调关键词命中 |
| release zip 解压后 `rg ... release/second-pass-scan` | 通过 | 除允许的 `18888` 部署端口配置外，无敏感/旧联调关键词命中 |
| `.gitignore` / `git ls-files` | 通过 | 未发现 release/dist/env/jar/zip/dump/log/evidence 追踪污染 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip` |

## 结论

- `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md` 结论为 PASS。
- 允许重新部署 GateJ-FREEZE-FIX release。
- GateJ 仍未 completed；必须在服务器重新部署后执行首次启动验收，再进入 1h / 24h / 7d 连续运行验收。

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未修改交易核心状态机、策略核心算法或回测核心算法。

---

# Worklog: GateJ-FREEZE-FIX-3

日期：2026-05-28

## 本轮目标

修复 ECS 实测发现的 `scripts/seed-freeze-user.sh` 问题：特殊字符密码导致手工 `source .env.freeze` 报 Bash 语法错误，以及 seed SQL 使用 `nq_freeze_seed_user_id` 临时表后出现 relation 不存在。本轮只修改 seed 脚本、freeze 部署模板/文档和验证记录，不新增业务功能、API、migration，不接 AI/DH/真实交易。

## 根因

- `.env.freeze` 是 Docker Compose/env 模板，不是 Bash 脚本；密码包含 `>`、`)` 等 shell 特殊字符时，手工 `source .env.freeze` 会让 Bash 按脚本语法解释密码，导致 syntax error 或泄露风险。
- 旧 seed SQL 使用 `CREATE TEMP TABLE ... ON COMMIT DROP` 保存用户 id；PostgreSQL autocommit 下该临时表会在 statement 提交后被 drop，后续 `DELETE/INSERT user_roles` 再引用会报 `relation "nq_freeze_seed_user_id" does not exist`。

## 修改文件清单

- `scripts/seed-freeze-user.sh`
- `deploy/.env.freeze.example`
- `scripts/build-freeze-release.ps1`
- `docs/current/GATEJ_FREEZE_DEPLOYMENT.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- `seed-freeze-user.sh` 不再使用临时表，也不依赖跨 statement 的 CTE 结果。
- seed SQL 改为单个 `psql` session/transaction：设置 session-local 参数、确保角色存在、upsert 指定 freeze 用户、设置 `enabled=true`、重绑 `ADMIN / OPERATOR / VIEWER`，并校验 BCrypt hash 可由同一明文匹配。
- 密码读取改为 `.env.freeze` / 进程环境 / 交互式隐藏输入三选一；当 `.env.freeze` 保持 `CHANGE_ME` 占位符时，脚本会在 TTY 下提示输入密码，不 echo 明文。
- `.env.freeze.example` 和部署文档明确禁止手工 `source .env.freeze`；如密码包含 shell 特殊字符，推荐保留占位符并通过 seed 脚本交互式输入。
- `RELEASE_INFO.md` 生成内容同步说明禁止 `source .env.freeze` 和交互式 seed 密码流程。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 是未安装发行版的 WSL stub；本机无 Git Bash，Docker daemon 未运行。需在 Linux ECS 或可用 Bash 环境复跑。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未提交真实密码、`.env.freeze`、release zip、jar、dist、logs、dump 或 freeze-evidence。

---

# Worklog: Credential Revocation Governance Batch 5-C

日期：2026-06-07

## 本轮目标

在 Batch 5-B 已完成 V29 schema-only 治理的前提下，接入 credential lifecycle 最小后端能力：Repository 读取新字段、Service 提供 `revoke / disable / expire` 状态流转、API 暴露最小 command endpoint、audit log append-only 写入，并补齐 core / infra / api 回归测试。

## 修改文件清单

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeAccountCredentialSummary.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeAccountCredentialMaterial.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeAccountCredentialRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialSummaryResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialLifecycleRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandServiceTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationServiceTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- API 摘要响应新增 `credentialStatus / revokedAt / rotatedAt` 等非敏感 lifecycle 字段，不暴露 encrypted payload、secret、token、private key、passphrase 或 decrypted payload。
- `findActiveSummary / findActiveByAccountAndType / findActiveMaterial` 默认同时要求 `is_active=true` 和 `credential_status='ACTIVE'`。
- `revoke / disable / expire` 通过 owner 受控 credential 查询执行状态流转；重复 revoke 幂等，`REVOKED / ROTATED` 后的 disable / expire 返回状态冲突。
- lifecycle reason 做长度限制和敏感词拒绝；audit metadata 只保存状态和来源。
- upsert 轮换旧 active 版本只写 `credential_status='ROTATED'`，不再把 `verification_status` 改写为 `REVOKED`。
- standalone MockMvc 测试补齐 Jackson Java time converter，保证 `Instant` 按生产 JSON 语义输出 ISO 字符串。

## 验证记录

- 首次执行 `mvn -f backend/pom.xml test` 失败：`ExchangeAccountCredentialControllerWebMvcTest` 期望 `revokedAt` 为 ISO-8601 字符串，但 standalone MockMvc 使用默认 Jackson 配置输出 epoch seconds。
- 修复后执行 `mvn -f backend/pom.xml -pl nq-api -am test` 通过。
- 轮换语义修正后执行 `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api -am test` 通过。
- 本轮最终验证结果以 `docs/current/TESTING.md` 为准。

## 边界确认

- 未新增 migration。
- 未修改前端、Python、部署或 release 产物。
- 未接入 AI、DH、Agent credential 调用、LIVE 或真实交易所私有链路。
- 未新增真实下单、撤单、真实交易所权限探活、rotate endpoint 或 enable endpoint。
- 未读取、输出或提交真实 credential material、API key、secret、token、private key、passphrase、cookie 或 decrypted payload。

---

# Worklog: GateJ-FREEZE-FINAL-DOC

日期：2026-06-05

## 本轮目标

整理 GateJ-FREEZE 最终验收文档并创建 `docs/gates/gate-j` 冻结快照。GateJ-FREEZE 连续运行验收已完成，本轮只做文档事实同步与冻结，不修改后端代码、前端代码、API、migration、脚本、部署配置，不执行 build/deploy/restart，不接入 AI/DH/真实交易。

## 最终验收事实

- 起点：2026-05-29 14:53:20 +08:00。
- 7d checkpoint：2026-06-05 14:53:24 +08:00。
- health-loop 样本数：2025。
- health-loop 最新样本：2026-06-05 15:40:58 +08:00。
- 30m observation：PASS。
- 1h acceptance：PASS。
- 24h acceptance：PASS。
- 7d acceptance：PASS。
- nginx：Up 7 days。
- nq-app：Up 7 days。
- postgres：Up 7 days healthy。
- 18888 health：UP。
- 5179 health：UP。
- after-7d.sql：已生成，266K。
- 磁盘：约 30G 可用，使用率约 21%。
- Swap：0B 使用。
- 5179 安全组：已确认只允许本人 IP 访问。

## 168h 日志补扫

after-7d checkpoint 中 `docker compose logs --since=7d` 不被当前 Compose 识别，输出 `invalid value for "since": failed to parse value as time or duration: "7d"`。已补跑合法窗口：

```bash
docker compose --env-file .env.freeze -f docker-compose.freeze.yml logs --since=168h nq-app
```

补跑错误扫描文件：

```text
/opt/nexus-quant/freeze-evidence/reports/after-7d/nq-app-error-scan-168h.txt
```

补跑结果：`wc -l = 0`。168h 后端日志未命中 `api_unhandled_exception`、`Binance request failed`、`status=451`、`BCrypt`、`Encoded password`、`authentication required`、`ERROR`、`Exception`、`OutOfMemory`、`OOM`。

## 修改文件清单

- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/README.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/ROADMAP.md`
- `docs/current/GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md`
- `docs/current/PRE_FREEZE_AUDIT_REPORT.md`

## 新增文件清单

- `docs/current/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`
- `docs/gates/gate-j/README.md`
- `docs/gates/gate-j/FREEZE_SUMMARY.md`
- `docs/gates/gate-j/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`
- `docs/gates/gate-j/GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md`
- `docs/gates/gate-j/` 下 GateJ 相关 planning / API / DB / frontend / test / work order 文档快照。
- `docs/gates/gate-j/` 下当前事实文档快照：`STATUS.md`、`TESTING.md`、`WORKLOG.md`、`ROADMAP.md`、`API.md`、`DB_SCHEMA.md` 等。

## 结论

- Current stage: GateJ completed。
- Next: GateK-PLAN。
- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- UI/UX professionalism remains post-freeze remediation。
- No new backend/frontend business code, API, migration, deployment, AI, DH, or real trading path was added in this final documentation stage。

## 边界确认

- 未启动 GateK 实现。
- 未接入 AI。
- 未接入 DH。
- 未新增交易所扩展。
- 未新增真实 LIVE 下单路径。
- 未修改后端业务代码、前端业务代码、API、migration、脚本或部署配置。
- 未新增 release、dist、jar、zip、log、dump、freeze-evidence 或 `.env.freeze`。

---

# Worklog: GateJ-FREEZE-FIX-7

日期：2026-05-29

## 本轮目标

修复 ECS 浏览器复验发现的 freeze 控制台旧阶段文案、开发接口说明和不专业筛选控件残留。本轮只允许前端 UI 展示与筛选控件修复，不新增 API、migration 或后端业务流程，不接入 AI/DH/真实交易。

## 根因

- 多个页面延续 GateH/GateI 开发阶段的 badge、说明文案和占位符，production/freeze bundle 仍会展示旧阶段、接口路径和本地筛选字段说明。
- 策略、调度、运行、Paper Trading、评估、发布等页面的枚举筛选仍使用自由文本 Input，容易输入非法状态值。
- Marketdata 与 Backtests 的时间字段仍使用普通文本输入，要求用户手写 ISO 字符串，不符合 freeze 控制台验收质量。

## 修改文件清单

- `frontend/src/constants/filter-options.ts`
- `frontend/src/pages/trading/TradingWorkbenchPage.tsx`
- `frontend/src/pages/marketdata/MarketdataPage.tsx`
- `frontend/src/pages/instruments/InstrumentsPage.tsx`
- `frontend/src/pages/strategies/StrategiesPage.tsx`
- `frontend/src/pages/schedules/SchedulesPage.tsx`
- `frontend/src/pages/runs/RunsPage.tsx`
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`
- `frontend/src/pages/evaluations/EvaluationsPage.tsx`
- `frontend/src/pages/publishes/PublishesPage.tsx`
- `frontend/src/pages/backtests/BacktestsPage.tsx`
- `frontend/src/pages/research/ResearchPage.tsx`
- `frontend/src/router/routes.tsx`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- 页面 badge 改为业务域名：Trading、Marketdata、Catalog、Strategies、Schedules、Runs、Paper Trading、Evaluations、Publishes。
- 清理页面可见文案中的旧 Gate、`LOCAL`、`GET /api` / `POST /api`、`publishId 过滤`、`本地筛选字段`、`真实请求参数` 等开发说明。
- 新增前端静态筛选选项，枚举筛选使用 Ant Design `Select`：exchange、market、symbol、interval、strategyType、tradeEnv、enabled/status、scheduleType、runStatus、triggerType、paper status、evaluationStatus、publishStatus。
- Marketdata 查询 / 接入任务 / Dataset 的 start/end 改为 `DatePicker`，提交时转换为后端需要的 ISO 字符串。
- Backtests 新建配置 start/end 同步改为 `DatePicker`，避免控制台继续暴露普通 ISO 时间输入框。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd frontend && npm run build` | 通过 | 首次因 `PaperTradingPage` 漏加 `Select` import 失败，补齐后通过；仍有既有 Vite chunk > 500 kB 警告。 |
| `frontend/dist` 残留扫描 | 通过 | 大小写敏感扫描未命中旧 Gate / LOCAL / API 开发说明残留。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 首次因沙箱无法写入 Maven repository tracking file 失败；提权重跑通过。 |
| release zip 解压后 frontend/dist 残留扫描 | 通过 | 未命中旧 Gate / LOCAL / API 开发说明残留。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`31,014,538` bytes

## ECS 待复验

当前本地环境没有 ECS 登录/上传上下文，因此未在本轮环境执行服务器命令，不能把 ECS 复验写成通过。上传新 release 并 `unzip -o` 后，必须执行：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml up -d --force-recreate nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
curl -fsS http://127.0.0.1:5179/actuator/health
```

浏览器复验必须确认页面不再出现旧 Gate / LOCAL / 接口说明残留；Instrument Catalog 同步仍是受控提示，不是 internal server error；后端日志无 `ERROR` / `Exception` / `api_unhandled_exception path=/api/instruments/sync`。

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未修改后端业务流程。
- 未接入 AI、DH 或真实交易。
- 未提交真实密码、`.env.freeze`、release zip、jar、dist、logs、dump 或 freeze-evidence。
- ECS 复验未完成前，不允许进入 GateJ-FREEZE 首次启动验收。

---

# Worklog: GateJ-FREEZE-FIX-5

日期：2026-05-29

## 本轮目标

修复 FIX-4 release 在 ECS 上暴露出的 `.sh` CRLF 换行问题。服务器实测 `backup-db.sh` / `freeze-health-loop.sh` 运行时报 `invalid option name line 2: set: pipefail`，且执行 `sed -i 's/\r$//' scripts/*.sh` 后全部 `bash -n` 通过，说明 release artifact 不可复现。本轮只修换行策略与 release 打包兜底，不新增 API、migration 或业务功能，不接入 AI/DH/真实交易。

## 根因

- 仓库缺少 `.gitattributes` 对 shell/yaml/PowerShell 文件的跨平台换行约束，Windows 环境下脚本可能被写入或保留为 CRLF。
- `scripts/build-freeze-release.ps1` 旧逻辑只把脚本复制到 staging 目录后直接压缩，没有在 zip 前对 staging 内 `scripts/*.sh` 做 LF 归一化。
- Linux Bash 会把 CRLF 中的 `\r` 作为 `set -euo pipefail` 参数内容的一部分，导致 `pipefail\r` 被解析为非法 option name，进而阻断 ECS 冻结验收脚本启动。

## 修改文件清单

- `.gitattributes`
- `scripts/build-freeze-release.ps1`
- `scripts/seed-freeze-user.sh`（仅换行从 CRLF 归一为 LF）
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- 新增 `.gitattributes`，强制 `*.sh text eol=lf`、`*.yml text eol=lf`、`*.yaml text eol=lf`、`*.ps1 text eol=crlf`。
- 仓库 `scripts/*.sh` 已机械转换为 LF；本轮发现 `seed-freeze-user.sh` 含 CRLF，其余脚本已是 LF。
- `scripts/build-freeze-release.ps1` 新增 `Convert-StagedShellScriptsToLf`，在 zip 前读取 staging `scripts/*.sh` 并把 CRLF / lone CR 统一转换为 LF。
- 转换只作用于 release staging 副本；如果脚本不是合法 UTF-8，严格解码会让打包失败，避免静默发出损坏脚本。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| 仓库 `scripts/*.sh` CRLF 字节检查 | 通过 | 5 个 shell 脚本均为 `HasCRLF=False`。 |
| `git diff --check` | 通过 | 无空白错误；仅有 `build-freeze-release.ps1` 将按 `.gitattributes` 维持 CRLF 的 Git 提示。 |
| `mvn -f backend/pom.xml test` | 通过 | 首次 120s 超时；提高超时复跑后 Reactor `BUILD SUCCESS`，`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 生成 `release/nq-gatej-freeze-release.zip`。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 `scripts/*.sh` 全部 `HasCRLF=False`。 |

新 release 包路径与大小：

- `release/nq-gatej-freeze-release.zip`
- `30,979,533` bytes

## ECS 待验证

当前本地环境没有 ECS 登录/上传上下文，因此未在本轮环境执行服务器命令，不能把 ECS 复验写成通过。上传新 release 并 `unzip -o` 后，必须不执行 `sed`，直接运行：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
bash scripts/backup-db.sh before-freeze
nohup bash scripts/freeze-health-loop.sh > /opt/nexus-quant/freeze-evidence/health/freeze-health-loop.out 2>&1 &
grep -n '"status":"UP"\|UP' /opt/nexus-quant/freeze-evidence/health/health-check-7d.log | tail
```

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未提交真实密码、`.env.freeze`、release zip、jar、dist、logs、dump 或 freeze-evidence。

- ECS 复验未完成前，不允许进入 GateJ-FREEZE 首次启动验收。

---

# Worklog: GateJ-FREEZE-FIX-6

日期：2026-05-29

## 本轮目标

修复 ECS freeze 控制台 Instrument Catalog 页面点击“同步 Catalog”后，Binance `/api/v3/exchangeInfo` 返回 451 被后端抛成 `internal server error` 的验收阻塞问题。同时清理生产/freeze 可见页面中 `GateH-PRE`、`LOCAL` 等旧阶段/本地环境文案残留。本轮只做 freeze 验收阻塞修复，不新增 API、migration 或业务功能，不接入 AI/DH/真实交易。

## 根因

- `InstrumentCatalogController.sync` 调用 `AdapterInstrumentCatalogSyncService.sync`，当 `exchangeCode` 为空时默认同步 `OKX` 和 `BINANCE`。
- Binance 分支调用 `binanceExchangeAdapter.filtersCache().snapshot(traceId)`；cache 需要刷新时会访问 Binance `exchangeInfo`。
- ECS 所在网络/地域下 Binance 返回 451，`BinanceApiException` 未被 service 层转换，最终进入 `ApiExceptionHandler.handleException`，记录 `api_unhandled_exception path=/api/instruments/sync` 并返回 500。
- 前端 Header 与 Instrument Catalog 页面仍展示 `GateH-PRE Account Context`、`GateH-PRE / PRE-2`，且生产构建缺省环境标签可能回退为本地标识。

## 修改文件清单

- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/AdapterInstrumentCatalogSyncService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/instrument/InstrumentCatalogSyncService.java`
- `backend/nq-app/src/main/resources/application.yml`
- `backend/nq-app/src/main/resources/application-freeze.yml`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/marketdata/api/web/InstrumentCatalogControllerTest.java`
- `backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/AdapterInstrumentCatalogSyncServiceTest.java`
- `frontend/src/components/layout/AppHeader.tsx`
- `frontend/src/pages/instruments/InstrumentsPage.tsx`
- `frontend/src/utils/env.ts`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- 新增配置 `nq.instrument.catalog-sync.enabled`：默认 true，freeze profile 默认 false。
- freeze 下调用 `/api/instruments/sync` 会在触达 OKX/Binance adapter 前返回 409，消息为“当前环境禁用外部交易所同步”。
- 非 freeze 且 Binance `exchangeInfo` 抛 `BinanceApiException` 时，service 层转换为 `IllegalStateException("外部交易所 instrument catalog 同步暂不可用")`，由现有 `ApiExceptionHandler` 输出 409 `STATE_CONFLICT`，避免进入 `api_unhandled_exception`。
- 前端 Instrument Catalog sync 409 会显示友好 warning，不再把受控错误展示为 internal server error。
- Header 副标题改为 `GateJ-FREEZE Console`，Instrument Catalog badge 改为 `GateJ-FREEZE`，production/freeze 缺省 env label 改为 `GateJ-FREEZE`。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-api,nq-scheduler -am test` | 通过 | 覆盖 controller 409 与 service 禁用/外部异常转换。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |
| `frontend/dist` 禁止串扫描 | 通过 | 未命中 `GateG` / `GateH-PRE` / `ChangeMe123` / `admin / ChangeMe123` / `/api/auth/login` / `/api/auth/me` / `Authorization: Bearer`。 |
| release zip 解压后禁止串扫描 | 通过 | 未命中上述禁止串。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 `scripts/*.sh` 全部 `HasCRLF=False`。 |

新 release 包路径与大小：

- `release/nq-gatej-freeze-release.zip`
- `30,980,280` bytes

## ECS 待验证

当前本地环境没有 ECS 登录/上传上下文，因此未在本轮环境执行服务器命令，不能把 ECS 复验写成通过。上传新 release 并 `unzip -o` 后，必须不执行 `sed`，直接运行：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml restart nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
```

浏览器复验：

- 进入 Instrument Catalog。
- 点击查询不报 500，列表为空允许。
- 点击“同步 Catalog”不显示 internal server error；freeze 下应显示“当前环境禁用外部交易所同步”。
- 后端日志不得出现 `api_unhandled_exception path=/api/instruments/sync`，不得以 ERROR 记录 `Binance request failed status=451`。

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未提交真实密码、`.env.freeze`、release zip、jar、dist、logs、dump 或 freeze-evidence。
- ECS 复验未完成前，不允许进入 GateJ-FREEZE 首次启动验收。

---

# Worklog: GateJ-FREEZE-FIX-4

日期：2026-05-28

## 本轮目标

修复 `scripts/seed-freeze-user.sh` 的交互式隐藏输入路径。服务器实测在 `.env.freeze` 删除/注释 `NQ_FREEZE_ADMIN_PASSWORD` 且进程环境 unset 后，交互输入正常密码仍被误判为多行，阻塞 GateJ-FREEZE 首次启动验收。

## 根因

`FREEZE_PASSWORD="$(read_secret_value "NQ_FREEZE_ADMIN_PASSWORD")"` 通过命令替换捕获函数 stdout。FIX-3 中 `read -r -s -p ...` 后使用 `echo` 输出视觉换行，该换行写到了 stdout，被命令替换捕获到密码值前部；随后单行校验检测到真实换行，报 `NQ_FREEZE_ADMIN_PASSWORD must be a single-line value`。这不是密码本身多行，而是交互提示换行污染了返回值。

## 修改文件清单

- `scripts/seed-freeze-user.sh`
- `docs/current/GATEJ_FREEZE_DEPLOYMENT.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- 将 `read_secret_value` 中交互输入后的视觉换行从 `echo` 改为 `printf '\n' >&2`。
- 保持 stdout 只输出密码值本身，避免命令替换捕获提示换行。
- 密码明文仍不写入 stdout/stderr；stderr 只输出提示名和换行。
- 保持三种密码来源：进程环境、`.env.freeze`、交互式隐藏输入。
- 保持单个 `psql` session + transaction，不新增 API、migration 或业务功能。

## 验证记录

- 本地 `bash -n scripts/seed-freeze-user.sh` 仍无法执行：当前 Windows `bash` 是未安装发行版的 WSL stub，且本机无 Git Bash、Docker daemon 未运行。
- 本地 `git diff --check` 通过。
- 本地 `mvn -f backend/pom.xml test` 通过：Reactor `BUILD SUCCESS`，`nq-app` 35 tests / 0 failures / 0 errors。
- 本地 `cd frontend && npm run build` 通过：仍有既有 Vite chunk size 警告。
- 本地 `.\scripts\build-freeze-release.ps1` 通过：重新生成 `release/nq-gatej-freeze-release.zip`。
- ECS 必须复验：
  - `bash -n scripts/seed-freeze-user.sh`
  - `unset NQ_FREEZE_ADMIN_PASSWORD` 后交互式执行 seed 成功
  - 进程环境方式执行 seed 成功
  - `hash_prefix` 为 `$2a$` 或 `$2b$`
  - `curl` 登录返回 200，且验证命令不打印 token

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未提交真实密码、`.env.freeze`、release zip、jar、dist、logs、dump 或 freeze-evidence。

---

# Worklog: NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-IMPLEMENTATION

日期：2026-06-13

## 本轮目标

实现 V31 permission probe 的最小后端 code/API/test 能力，并保持 no-real-exchange 测试隔离。当前阶段仍为 GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE disabled。

## 修改范围

- 新增独立 `ExchangeCredentialPermissionProbePort`，Service 只依赖 port，不直接写 HTTP。
- 新增 `CredentialPermissionProbeService`，按 owner/account、credential row lock、ACTIVE/is_active、LIVE blocked、withdraw risk、Paper safety gate、IN_PROGRESS 并发检查顺序做本地 gate。
- 新增 credential permission probe API：
  - `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe`
  - `GET /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe/latest`
- 扩展 JDBC repository 读取/写回 V31/V29 非敏感字段：`permission_probe_status`、`permission_scope`、`withdraw_enabled`、`ip_allowlist_probe_status`、`failed_auth_count`、`last_permission_probe_at`、`last_permission_probe_error`。
- 新增默认 `NoRealExchangeCredentialPermissionProbePort`，仅返回 `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`，不创建 HTTP client。
- 新增 OKX/Binance adapter 边界类，只固化错误分类与 forbidden endpoint 规则，不接真实交易所。
- 补齐 Service、Repository、Web/API、Adapter boundary、No-real-exchange guard 测试。

## 安全与审计策略

- API request body 只接受 `reason`、`dryRun`、`mode`、`paperSafetyConfirmed`；未知字段直接返回 `MALFORMED_REQUEST`。
- `credentialType`、actor、credential material 均由服务端派生；API response 不返回 raw response、headers、signature、encrypted/decrypted payload、API key、secret、passphrase、private key。
- audit event 覆盖 `PERMISSION_PROBE_STARTED`、`PERMISSION_PROBE_SUCCEEDED`、`PERMISSION_PROBE_FAILED`、`PERMISSION_PROBE_SKIPPED`。
- audit metadata 只写 account/credential/status/scope/IP/error category/requestId/traceId 等脱敏字段；不写 raw request、raw response、headers、signature 或 credential material。
- `AUTH_FAILED`、`INVALID_API_KEY`、`SIGNATURE_FAILED`、`IP_ALLOWLIST_FAILED` 增加 `failed_auth_count`；成功不自动清零。
- `permission_scope = NULL` 不被当作 `TRADE`。

## 边界确认

- 未新增 migration，未修改历史 migration。
- 未修改前端、Python 或部署脚本。
- 未开启 LIVE，LIVE credential probe 默认 `SKIPPED`。
- 未调用 OKX / Binance / Bybit / Gate 或任意真实交易所。
- 未新增真实下单、撤单、转账、提现路径。
- 未接 AI，未接 DH runtime，未把 GateK-PLAN 写成 GateK implementation started。

---

# Worklog: NQ-GATEK-PLAN

日期：2026-06-14

## 本轮目标

本轮只做 GateK planning-only 文档与当前事实源同步。当前事实固定为 GateJ completed；Next: GateK-PLAN；GateK implementation not started；AI not started；DH integration not integrated / not connected to NQ；LIVE disabled；Multi-exchange expansion not started。

## 修改范围

- 新增 `docs/current/GATEK_PLAN.md`，定义 GateK 定位、非目标、六条主线、任务矩阵、验收标准、风险清单、backlog、安全审计前置、review-before-implementation 条件和 GateK 完成标准。
- 同步 `README.md`、`docs/current/README.md`、`docs/current/ROADMAP.md`、`docs/current/STATUS.md`、`AGENTS.md`、`CLAUDE.md` 的 GateK-PLAN 定位，避免继续把 GateK 缩窄写成 AI 信号接入规划。
- 追加本轮 `WORKLOG.md` / `TESTING.md` 记录，说明本轮为 docs-only planning，未运行 backend/frontend/Python build/test。

## 验证记录

- `git diff --check`：通过；仅输出既有 LF/CRLF 工作区提示，无 whitespace error。
- `git diff --stat`：已检查；仅文档变更。
- `git status --short`：已检查；仅允许文档范围内变更和新增 `docs/current/GATEK_PLAN.md`。
- `git diff -- backend`、`git diff -- frontend`、`git diff -- research`、`git diff -- scripts`、`git diff -- deploy`、`git diff -- backend/**/db/migration`：输出均为空。
- 阶段误写扫描：命中项均为禁止、否定或风险说明语境，未新增正向 `GateK implementation started`、`AI started`、`DH integrated`、`LIVE enabled` 事实声明。

## 边界确认

- 未修改 backend、frontend、research、scripts、deploy。
- 未新增 API、Controller、Service、Repository、Adapter 或 migration。
- 未实现 GateK 功能、AI、DH runtime integration、NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未开启 LIVE，未下单、撤单、转账、提现。
- 未读取、打印、复制、输出真实 API key、secret、token、私钥、助记词、passphrase。

---

# Worklog: NQ-CI-BASELINE-IMPL

日期：2026-06-14

## 本轮目标

本轮进入 GateK implementation Batch 1，仅创建最小 GitHub Actions CI baseline。当前事实固定为 GateJ completed；GateK implementation 仅限本轮 CI baseline Batch 1；AI not started；DH runtime not integrated；LIVE disabled；real exchange permission probe adapter not implemented。

## 修改范围

- 新增 `.github/workflows/ci.yml`。
- 同步 `docs/current/NQ_CI_BASELINE_PLAN.md`：当时登记 Batch 1 implemented / first CI run pending；该 pending 状态已由 `NQ-CI-BASELINE-FIRST-RUN-REVIEW` 关闭，Batch 2 PostgreSQL/Flyway、Batch 3 no-outbound、Batch 4 security guard、Batch 5 frontend E2E hardening 仍 pending。
- 同步 `docs/current/README.md`：更新 CI baseline 入口状态。
- 追加 `docs/current/TESTING.md` 本轮验证记录。
- 追加本 `WORKLOG.md` 记录。

## Workflow 摘要

- Trigger：`pull_request` to `dev`、`push` to `dev`、`workflow_dispatch`。
- `diff-check`：checkout + changed-file whitespace check，兼容 PR / push / manual run。
- `backend`：Java 21 + Maven cache + `mvn -f backend/pom.xml test`，不使用 skip tests。
- `frontend`：Node 22 + npm cache + `npm ci` + `npm run build`。
- `research`：Python 3.11 + pip cache + `python -m pip install -e ".[dev]"` + pytest / mypy / ruff；mypy / ruff 使用 cache-independent flags，避免工具 cache 权限影响 CI 结论。

## 未纳入本轮

- 未实现 PostgreSQL/Flyway hardening。
- 未实现 no-outbound guard。
- 未实现 gitleaks / secret scan。
- 未实现 dependency audit。
- 未实现 frontend E2E hardening。
- 未加入 frontend B1/B2/B3 页面施工。

## 边界确认

- 未修改 Java / TypeScript / Python 代码。
- 未修改测试代码。
- 未新增 API。
- 未新增或修改 migration。
- 未修改 backend 生产逻辑。
- 未修改 frontend B0 / Design System v2 分支内容。
- 未修改 scripts / deploy。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。
- 未读取、打印、复制、输出真实 credential material。

## 本地验证

- `git diff --check`：通过，仅有 Windows LF/CRLF 工作区提示。
- `mvn -f backend/pom.xml test`：通过，23 个 Maven module `SUCCESS`，`BUILD SUCCESS`。
- `frontend` 下 `npm ci`：通过。
- `frontend` 下 `npm run build`：通过，仅有 Vite chunk size warning。
- `research/py` 下 `python -m pytest -q`：通过，2 passed。
- `research/py` 下 `python -m mypy src`：本机 Python 3.14.2 + mypy 2.1.0 默认 sqlite cache 打不开，未写成通过；`python -m mypy src --no-sqlite-cache` 通过。
- `research/py` 下 `python -m ruff check .`：本机 `.ruff_cache` 写入被拒绝，未写成通过；`python -m ruff check . --no-cache` 通过。
- GitHub Actions first run：pending，需要 push 或 PR 后观察。

## 下一步

Next concrete action：push 或 PR 到 `dev`，观察首次 `NQ CI Baseline` GitHub Actions run；如失败，只做 `NQ-CI-BASELINE-FIRST-RUN-FIX`，不得混入 Batch 2-5。

---

# Worklog: NQ-CI-BASELINE-FIRST-RUN-FIX

日期：2026-06-14

## 本轮目标

修复首次 GitHub Actions `NQ CI Baseline` run 的 Batch 1 backend job 失败。当前事实固定为 NQ-CI-BASELINE-IMPL / Batch 1 已提交；GateK implementation 仅限 CI Batch 1 first-run fix；AI not started；DH runtime not integrated；LIVE disabled；real exchange adapter not implemented。

## 失败摘要

- Run：`27496510294`。
- Failed job：`Backend Maven test`。
- Failed step：`Run backend tests`。
- Failed command：`mvn -f backend/pom.xml test`。
- Passing jobs：`diff-check`、`frontend`、`research`。

## 根因

`nq-app` 中多个 `local` profile full Spring context 测试需要 PostgreSQL。GitHub runner 没有本地 PostgreSQL，而 `application-local.yml` 默认 datasource 指向 `jdbc:postgresql://localhost:5432/nexus_quant`。本机验证通过依赖本机已有 PostgreSQL。

第二次 run 中 PostgreSQL service 与 Flyway 已可用，但全新 DB 缺少 legacy `accounts` seed；`ResearchBacktestHappyPathLocalTest` 第 59 行查询 `accounts` 表期望至少一条 legacy account，实际为 0。

## 修复

- 在 `.github/workflows/ci.yml` 的 `backend` job 增加 ephemeral `postgres:16` service。
- 为 backend job 设置 `NQ_DB_URL`、`NQ_DB_USER`、`NQ_DB_PASSWORD`，匹配 `application-local.yml` 默认连接。
- 在 backend test step 内增加 CI-only seed watcher：等待 Flyway 创建 `accounts` 表后，通过 PostgreSQL service container 插入一条最小 `PAPER / ACTIVE` legacy account。
- 保留 `mvn -f backend/pom.xml test`，未使用 `skipTests`，未使用 `continue-on-error`。

## 边界确认

- 这是 Batch 1 runner dependency fix，不是 PostgreSQL/Flyway hardening。
- 未新增 Flyway 专项验证 job，未新增 migration order / schema drift / repeatability 检查。
- Batch 2 PostgreSQL/Flyway、Batch 3 no-outbound、Batch 4 security scan、Batch 5 frontend E2E hardening 仍 pending。
- 未修改 backend / frontend / research 代码，未修改测试代码，未新增 API 或 migration，未修改 scripts / deploy。
- 未开启 LIVE，未接 AI，未接 DH runtime，未实现 RealClient / Provider / real permission probe adapter。
- 未注入真实交易所 credential，未调用真实交易所。

## 下一步

Next concrete action：提交并 push first-run fix，观察下一次 `NQ CI Baseline` run；如通过，进入 `NQ-CI-BASELINE-FIRST-RUN-REVIEW`。

---

# Worklog: NQ-CI-BASELINE-FIRST-RUN-REVIEW

日期：2026-06-14

## 本轮目标

评审 `NQ CI Baseline` Batch 1 首次 GitHub Actions green run 是否可冻结为当前 `dev` 最小 CI 基线。本轮只做 review 与 current docs 状态同步，不修改 workflow、业务代码、测试代码、API、migration、scripts 或 deploy。

## GitHub Actions 结果

- Run：`27496906788`。
- `Diff check`：success。
- `Backend Maven test`：success。
- `Frontend build`：success。
- `Research quality gate`：success。

## Review 结论

- `.github/workflows/ci.yml` 只包含 Batch 1 jobs：`diff-check`、`backend`、`frontend`、`research`。
- Backend job 保留 `mvn -f backend/pom.xml test`，未使用 `skipTests` 或 `continue-on-error`。
- CI-only seed watcher 只服务 fresh GitHub runner 的最小 legacy `accounts` seed，不进入生产代码、migration 或 runtime seed 逻辑。
- Frontend job 只执行 `npm ci` 与 `npm run build`，未引入 B1/B2/B3 页面施工。
- Research job 执行 `pytest`、`mypy --no-sqlite-cache`、`ruff --no-cache`，未访问外部数据源或下载大型数据集。
- Batch 2 PostgreSQL/Flyway、Batch 3 no-outbound、Batch 4 security guard、Batch 5 frontend E2E hardening 仍 pending。

## 边界确认

- 未修改 backend / frontend / research 代码。
- 未修改测试代码。
- 未新增 API 或 migration。
- 未修改 scripts / deploy。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 RealClient / Provider / real permission probe adapter。
- 未注入真实交易所 credential，未调用真实交易所。

## 下一步

Next concrete action：冻结 `NQ-CI-BASELINE-IMPL` Batch 1 为当前 `dev` 最小 CI baseline；后续只能另起 Batch 2 PostgreSQL/Flyway planning / implementation，不得混入 Batch 3-5、AI、DH runtime、LIVE 或真实交易所。

---

# Worklog: NQ-CI-BASELINE-PLAN

日期：2026-06-14

## 本轮目标

本轮只规划 NQ CI baseline，输出后续 `NQ-CI-BASELINE-IMPL` 应如何分层实施、哪些测试必须跑、哪些失败阻塞 merge、哪些需要 PostgreSQL/Flyway、哪些必须 no-outbound / no-secret / LIVE disabled、哪些可后置。当前事实固定为 GateJ completed；Next: GateK-PLAN；GateK implementation not started；AI not started；DH runtime not integrated；LIVE disabled；real exchange permission probe adapter not implemented。

## 修改范围

- 新增 `docs/current/NQ_CI_BASELINE_PLAN.md`，覆盖 Current state、CI goals、Non-goals、Job matrix、Backend / Frontend / Research baseline、PostgreSQL / Flyway、No-outbound guard、Security guard、Branch / PR policy、Required / forbidden secrets、P0/P1/P2/P3 risks、Implementation batches、Validation commands 和 Next concrete action。
- 同步 `docs/current/README.md`，加入 CI baseline plan 入口，并明确不代表 CI implemented。
- 同步 `docs/current/ROADMAP.md`，登记 CI baseline plan 已落档为 planning-only。
- 追加 `docs/current/TESTING.md` 本轮 docs-only 验证记录。

## 验证记录

- `git status --short`：已执行。
- `git diff --check`：已执行。
- `git diff --stat`：已执行。
- `git ls-files .github`：已执行，当前 tracked `.github` 只有 `CODEOWNERS` 与 `pull_request_template.md`。
- `git ls-files backend/frontend/research | head`：PowerShell 环境无 `head`，原命令按用户要求执行失败；已用 `Select-Object -First 20` 等价复跑。
- `rg "name:|on:|jobs:" .github docs/current README.md`：已执行。
- `rg "mvn|npm run build|test:e2e|pytest|mypy|ruff|flyway|postgres|Testcontainers|OKX|Binance|NoReal|LIVE" docs/current backend frontend research README.md`：已执行；后续分析用排除 `frontend/node_modules` / `target` / `build` / `dist` 的版本复跑，避免依赖目录噪音。
- 禁止范围 diff 检查按用户清单执行。

## 边界确认

- 未创建 `.github/workflows/**`。
- 未修改 backend、frontend、research、scripts、deploy。
- 未新增 API、Controller、Service、Repository、Adapter 或 migration。
- 未实现 CI workflow、GateK 功能、AI、DH runtime integration、NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未开启 LIVE，未下单、撤单、转账、提现。
- 未调用真实交易所。
- 未读取、打印、复制、输出真实 API key、secret、token、私钥、助记词、passphrase 或 credential material。

---

# Worklog: GATEK-ARCH-DOC-SYNC

日期：2026-06-14

## 本轮目标

本轮只做 GateK docs-only architecture wording sync，关闭 `GATEK_ARCHITECTURE_BASELINE_REVIEW.md` 登记的 P2 文档漂移：`docs/current/ARCHITECTURE.md` / `docs/current/MODULES.md` 旧 Gate / GateH / V1 措辞不再作为 current state。当前事实固定为 GateJ completed；Next: GateK-PLAN；GateK planning baseline accepted；GateK implementation not started；AI not started；DH runtime not integrated；LIVE disabled；real exchange permission probe adapter not implemented。

## 修改范围

- 同步 `docs/current/ARCHITECTURE.md`：补充当前事实、GateK planning baseline、backend 分层、PAPER / LIVE 硬隔离、permission probe no-real baseline、frontend Design System v1、research/py 与 GateK CI baseline 规划语境。
- 同步 `docs/current/MODULES.md`：将 GateH / GateI / GateJ 标注为 previous completed phase / archived history，修正 adapter、frontend、research/py 当前职责和禁止范围。
- 更新 `docs/current/README.md`：明确 `GATEK_ARCHITECTURE_BASELINE_REVIEW.md` 是审查报告，`ARCHITECTURE.md` / `MODULES.md` 是 P2 follow-up 后的 current fact source。
- 追加本轮 `WORKLOG.md` / `TESTING.md` 记录。

## 边界确认

- 未修改 backend、frontend、research、scripts、deploy。
- 未新增 API、Controller、Service、Repository、Adapter 或 migration。
- 未实现 GateK 功能、AI、DH runtime integration、NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未开启 LIVE，未下单、撤单、转账、提现。
- 未读取、打印、复制、输出真实 API key、secret、token、私钥、助记词、passphrase。

---

# Worklog: GATEK-PLAN-FREEZE-REVIEW

日期：2026-06-14

## 本轮目标

本轮只做 GateK planning freeze review，审查 `docs/current/GATEK_PLAN.md` 与当前事实源是否可作为后续 GateK 工作的冻结规划基线。当前事实固定为 GateJ completed；Next: GateK-PLAN；GateK implementation not started；AI not started；DH runtime not integrated；LIVE disabled；Multi-exchange expansion not started；真实 OKX/Binance permission probe adapter not implemented。

## 修改范围

- 修正 `AGENTS.md` / `CLAUDE.md` 中可能误读为 GateK 启动 AI 的旧口径，明确 GateK-PLAN 不启动 AI。
- 修正 `CLAUDE.md` active skills 清单，补回 `nq-dh-workflow-router` 并要求 NQ / DH / Gate / FREEZE 任务先路由。
- 修正 `docs/current/STATUS.md` 中虚拟币量化 V1 完成状态和 GateJ-FREEZE 旧 next 口径。
- 修正 `docs/current/ROADMAP.md` 中 Integration-0 contract test “下一步可实现”与已验收关闭之间的重复阶段口径。
- 追加本轮 `WORKLOG.md` / `TESTING.md` 记录。

## Freeze review 结论

- `docs/current/GATEK_PLAN.md` 已明确 GateK 定位、non-goals、GateK implementation not started、AI/DH runtime/LIVE/real adapter not started。
- GateK-1 到 GateK-6 主线、P0/P1/P2 task matrix、GateK completion criteria、review-before-implementation 条件和下一步顺序完整。
- CI / observability / deployment 均保持 planning-only；frontend productization 仍限制在当前后端就绪范围内；Integration-0 只保留 contract / mock / stub / contract test / security docs 线。
- 本轮修补后未发现 P0/P1/P2 阻塞项，GateK-PLAN 可作为冻结规划基线。

## 验证记录

- `git status --short`：已执行。
- `git diff --check`：已执行。
- `git diff --stat`：已执行。
- `git diff -- backend`、`git diff -- frontend`、`git diff -- research`、`git diff -- scripts`、`git diff -- deploy`、`git diff -- backend/**/db/migration`：已执行。
- 敏感词扫描：已执行；命中项仅允许为否定式、禁止说明、字段名或历史说明，不得包含真实 credential material。

## 边界确认

- 未修改 backend、frontend、research、scripts、deploy。
- 未新增 API、Controller、Service、Repository、Adapter 或 migration。
- 未实现 GateK 功能、AI、DH runtime integration、NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未开启 LIVE，未下单、撤单、转账、提现。
- 未读取、打印、复制、输出真实 API key、secret、token、私钥、助记词、passphrase。

---

# Worklog: NQ-FRONTEND-B0-DESIGN-TOKENS-V2-APP-SHELL-STATUS-SYSTEM

日期：2026-06-14

## 本轮目标

把已审定的 NQ Console Design System v2 基线（`docs/current/frontend/NQ_DESIGN_TOKENS_V2.md` + `docs/current/frontend/ref/nq-design-system/`）落进真实前端工程（React + Vite + TS + AntD 5），只做 B0（施工状态 READY_NOW）：tokens 单一来源 + AntD/ECharts/Lightweight Charts 主题派生 + 四件状态组件 + AppShell + 应用入口接线 + 一个自检演示路由。本轮不做 B1+ 业务页面、不做 AI/Agent/DH 页面、不接真实 WebSocket/SSE/交易所 adapter、不碰 LIVE、不改后端 API。

## 接线范围决策（用户确认）

现有 Design System v1（`@/theme/*`、`--nq-color-*`、`@/components/nq/*`）已是 GateJ 冻结、驱动 ~20 个线上页面 + login E2E。v2 与 v1 CSS 变量命名空间不冲突（v2=`--nq-*` / v1=`--nq-color-*`），唯一全局冲突点是单一全局 AntD `ConfigProvider` 主题。经用户确认采用「作用域限定到演示路由」：v2 的 `ConfigProvider(nqAntdTheme)` / `applyNqCssVars` / `registerNqEchartsTheme` 仅在新建自检路由 `/dev/design-system` 内激活；**不改全局 `AppProviders`，不动 v1 页面**，可一键回滚。既有页面迁移到 v2 token 留作后续切片。

## 修改范围

- 新增 v2 设计系统模块 `frontend/src/nq-design-system/`（12 个文件 + README）：
  - `tokens/nq-tokens.ts`（唯一来源）、`tokens/nq-css-vars.ts`（生成/注入 CSS 变量）、`tokens/nq-tokens.css`（:root 兜底，当前不全局 import）。
  - `theme/nqAntdTheme.ts`、`theme/nqEchartsTheme.ts`、`theme/nqLwcOptions.ts`。
  - `status/StatusTag.tsx`、`status/EnvironmentBadge.tsx`、`status/RiskBanner.tsx`、`status/DataFreshness.tsx`、`shell/AppShell.tsx`、`index.ts`。
- 新增自检演示页 `frontend/src/pages/dev/DesignSystemDemoPage.tsx` + 作用域样式 `DesignSystemDemoPage.css`（规则均命名空间到 `.nq-ds-demo`）。
- 修改 `frontend/src/router/routes.tsx`：注册公开、非业务、不在侧导航的自检路由 `/dev/design-system`（位于 `RequireAuth` 之外，不依赖登录/后端）。

## 与审定参考实现的差异（为通过本仓库 strict tsconfig，最小适配）

- `import React from 'react'` 改为按需 `import type { CSSProperties | ReactNode }` 或移除（仓库启用 `jsx: react-jsx` + `noUnusedLocals`）。
- `nqEchartsTheme.ts` 从 `'echarts/core'` 引入（与 `src/components/nq/charts/echarts-core.ts` 一致，保持 tree-shaking，`registerTheme` 在 core 上可用），而非全量 `'echarts'`。
- `nqLwcOptions()` 去掉未使用的 `convention` 形参（仓库启用 `noUnusedParameters`；涨跌色只作用于 `nqCandleColors`，chart chrome 与惯例无关）。
- 行为/取值/配色与审定基线一致，未改动 token 值。

## 验证记录

- `npm run build`（`tsc -b && vite build`）：**通过**。tsc 类型检查 0 error；vite build `✓ built in ~1s`。>500 kB 单 chunk warning 为既有单包结构所致（echarts 在 v1 已打包），非本轮回归。
- 真机自检（vite preview + Playwright Chromium 截图 `/dev/design-system`，无后端依赖）：**通过，0 console error / 0 page error**。
  - INTL_CRYPTO 默认：`--nq-up`=`#33d6a6`(绿)、`--nq-down`=`#ff5c6c`(红)；`.nq-up` 实算 `rgb(51,214,166)`。
  - 切换 CN_STOCK 后：`--nq-up`=`#ff5c6c`(红)、`--nq-down`=`#33d6a6`(绿)；`.nq-up` 实算 `rgb(255,92,108)`；数字、K 线 swatch、ECharts PnL 柱同步翻转（一处生效）。
  - LIVE（实心红+点）与 PAPER（描边）样式明显不同；四件状态组件、AppShell、暗色分层、CJK 14px、数字 tabular-nums 渲染正常。
  - `body` 背景仍为 v1 的 `#0d1219`（未被全局覆盖），证明作用域接线未泄漏到 v1 页面。
- `npm run test:e2e`：**本轮未运行**。原因：现有 E2E 多数 spec 依赖后端（`127.0.0.1:18888`，本环境未启动）；本轮只新增公开自检路由与独立模块，未改任何既有页面或全局主题，既有 E2E 语义不受影响。Playwright Chromium 已就绪，后端就绪后由用户侧执行全量 E2E。

## 边界确认

- 未改后端、未改 API/契约、未新增 migration。
- 未改任何既有业务页面、未改全局 `AppProviders` / v1 主题。
- 未接真实 WebSocket/SSE/交易所 adapter，未碰 LIVE 交易能力。
- 未做 AI / Agent / DH 页面（B8 仍 BLOCKED），未 mock 成熟业务页。
- 未读取、打印、输出真实 API key、secret、token、私钥、助记词、passphrase。
- 回滚方式：删除 `frontend/src/nq-design-system/`、`frontend/src/pages/dev/`，还原 `frontend/src/router/routes.tsx` 两处新增即可完全回退。

---

# Worklog: NQ-FRONTEND-B0-LOGIN-AND-EXCEPTION-PAGES（B0.1）

日期：2026-06-14

## 本轮目标

承接 B0（设计系统已落地、Draft PR #1），在其分支之上做 B0.1：重做登录页 + 四个异常页，仍属 B0（READY_NOW）。复用 `@/nq-design-system` 的 v2 token 与主题，只读 `var(--nq-*)`。不接 AI/Agent/DH、不接真实 socket/交易所、不碰 LIVE、不改后端 API、不改鉴权逻辑。

## 并发隔离

Codex 正在 `dev` 上做 GateK-PLAN，与本任务共用同一个 git 工作树/HEAD（Codex 多次切换 HEAD）。经用户确认，本轮在独立 git worktree（`E:/Project/nexus-quant-fe-b01`，分支 `feat/nq-frontend-b0-login-exception`，基于 `feat/nq-frontend-ds-v2`）执行，`node_modules` 用 junction 复用主工作树，HEAD 完全隔离。PR 边界：B0.1 作为 stacked Draft PR（base = `feat/nq-frontend-ds-v2`），不与 B0 PR 合并，B1–B7 不压进同一 PR。

## 修改范围

- 新增共享外壳：`frontend/src/components/standalone/StandaloneSurface.{tsx,css}`（登录/异常共用的 v2 ConfigProvider + `applyNqCssVars` 作用域全屏壳）、`ExceptionView.{tsx,css}`（异常统一表现层）。
- 重做登录页：`frontend/src/pages/login/LoginPage.tsx` + 新增 `LoginPage.css`。居中平衡双区；左区叙事（系统是什么/能做什么/风控审计边界/为什么可信）；右区认证卡片复用既有 `authApi.login`；移除主视觉里的 Gate/里程碑/DEV/PAPER/LOCAL，仅保留 footer 极小号 `受控环境 · 默认 PAPER · LIVE 已禁用`；移动端上下堆叠、认证卡片置顶首屏。
- 新增四个异常页：`frontend/src/pages/exceptions/{AuthFailurePage,ForbiddenPage,SystemErrorPage,WelcomePage}.tsx`。鉴权失败区分会话过期/身份校验失败/环境不允许访问（`?reason=`）；无权限说明缺少角色 + 如何申请；系统错误含 Request ID + 发生时间 + 返回入口；空系统初始化给出第一步动作。
- 重做 404：`frontend/src/pages/not-found/NotFoundPage.tsx` 改用统一 `ExceptionView`，替代 AntD 默认模板。
- 路由：`frontend/src/router/routes.tsx` 注册 `/exception/{auth,forbidden,error,welcome}`（`RequireAuth` 之外的公开展示路由，不依赖后端）。
- E2E：`frontend/tests/e2e/login-page-smoke.spec.ts` 更新为新登录页断言（保留空凭证 + 安全边界断言，新增 Gate/DEV/PAPER/LOCAL 不出现的负向断言）。

## 范围决策与说明

- 文案中文化：与控制台既有中文导航一致，并满足 CJK 14px / 文案规范。
- 异常页本轮只交付“原因 + 下一步”的表现层与公开路由；真实触发接线（会话过期跳转、403 拦截、错误边界、空态检测）属后续切片，本轮不改鉴权/错误处理逻辑。
- `docs/current/frontend/NQ_DESIGN_TOKENS_V2.md` 无“第 4 节 业务页面设计方案”章节（实际第 4 节为状态系统）；本轮以任务正文 + `docs/current/README.md` 产品定位 + v1 文案延续为权威依据。该文档交叉引用偏差已记录。
- v1 登录样式 `.login-page*`（`frontend/src/styles/index.css`）在本轮后成为未使用死代码；为最小变更暂不删除，留作后续 cleanup 切片。

## 验证记录

- `npm run build`（worktree，`tsc -b && vite build`）：**通过**，tsc 0 error，`✓ built in 880ms`。
- `login-page-smoke.spec.ts`（Playwright Chromium，外部 vite preview，无后端）：**1 passed**。
- 真机自检（Playwright Chromium 截图，9 条路由，**0 console / 0 page error**）：登录页桌面端双区整体居中（非靠右）、主视觉无 Gate/DEV/PAPER/LOCAL、footer 仅极小号环境元信息；登录页移动端上下堆叠、认证卡片置顶首屏；`/exception/auth` 三种 reason 文案各异；`/exception/forbidden` 显示缺少角色 + 申请指引（403）；`/exception/error` 含 Request ID + 发生时间（mono/tabular）+ 返回入口（500）；`/exception/welcome` 给出第一步动作；404 改用统一异常层。暗色对比度、主色非 AntD 蓝（#5b8cff）、中文 14px、圆角 4/6 均符合 token。
- `npm run test:e2e`（全量）：**未跑**。原因：多数 spec 依赖后端（`:18888`，本环境未启动）；本轮仅单独运行了无后端依赖的 login smoke 并通过。

## 边界确认

- 未改后端 API/契约、未新增 migration、未改鉴权逻辑（`authApi` / `auth-store` / `RequireAuth` 原样复用）。
- 未做 AI/Agent/DH 页面（B8 仍 BLOCKED）、未接真实 socket/交易所、未碰 LIVE、未展示任何默认凭证/明文、未新增凭证处理路径。
- 回滚方式：删除 `frontend/src/components/standalone/`、`frontend/src/pages/exceptions/`、`frontend/src/pages/login/LoginPage.css`，还原 `LoginPage.tsx` / `NotFoundPage.tsx` / `routes.tsx` / `login-page-smoke.spec.ts` 即可完全回退。

---

# Worklog: NQ-FRONTEND-TABLE-DENSITY-B0.2

日期：2026-06-14

## 本轮目标

PR #1(B0 设计系统)、PR #2(B0.1 登录/异常页)均已合入 dev。本轮 B0.2 封装表格密度与列格式基础能力,为后续 Dashboard / Backtest / Strategy / Risk / MarketData 页面迁移做准备。只做基础组件 + 自检,不做业务大页面,不迁移既有页面。

## 分支与隔离

在独立 git worktree（`E:/Project/nexus-quant-fe-b01`,`node_modules` junction）基于最新 `origin/dev`(`ea38f79d`)新建分支 `feat/nq-frontend-table-density-b02`,与 Codex 的 dev HEAD 隔离。PR 边界:B0.2 独立 PR(base = `dev`),不混入 B1–B7。

## 修改范围

- 新增 `frontend/src/nq-design-system/format/nqFormat.ts`:纯函数 `formatNqNumber / formatNqMoney / formatNqPercent / nqDirectionOf`,空值统一 "-",`NQ_DIRECTION_VAR` 把涨跌方向映射到 `var(--nq-up/--nq-down/--nq-flat)`。
- 新增 `frontend/src/nq-design-system/format/cells.tsx`:列组件 `NumberCell / MoneyCell / PercentCell / ChangeCell / StatusCell`。数字等宽 tabular;`ChangeCell` 用行情方向色(随惯例翻转,**不复用 success/danger**);`StatusCell` 把状态值映射为 `StatusTag` 语义色并保留原文。
- 新增 `frontend/src/nq-design-system/table/tableDensity.ts`:`NQ_TABLE_DENSITY`(compact 28 / standard 32 / comfortable 36,对齐设计规范 §3)+ `nqTableClassName()` + `nqAntdTableCellPadding()`(供后续 AntD Table 迁移取值,本轮不改业务页)。
- 新增 `frontend/src/nq-design-system/table/nq-table.css`:命名空间 `.nq-ds-table*` 的密度与列对齐样式(`.nq-ds-col-num` 右对齐 + tabular),只读 `var(--nq-*)`,**不与 v1 `.nq-table`/`.nq-col-num` 冲突**。
- 更新 `frontend/src/nq-design-system/index.ts`:导出上述 format / table API。
- 更新 `frontend/src/pages/dev/DesignSystemDemoPage.tsx`:新增"表格密度 + 列格式"自检区(密度 Segmented 切换 + 样本表,含金额/持仓/仓位占比/浮动盈亏/日涨跌幅/状态/更新时间列);涨跌列随既有行情惯例开关一处翻转。
- 新增 `frontend/tests/e2e/design-system-table-smoke.spec.ts`:表格密度切换 + 列格式 + 涨跌方向色 smoke。

## 说明

- 列组件用内联样式读 `var(--nq-*)`(与既有 v2 组件一致),不依赖全局 class,零碰撞、零影响既有页面。
- 未迁移任何业务页面(Dashboard/Backtest/Strategy/Risk/MarketData);本轮只产出可复用基础能力 + 自检。

## 验证记录

- `npm run build`（worktree，`tsc -b && vite build`）：**通过**,tsc 0 error,`✓ built in 844ms`。
- `design-system-table-smoke.spec.ts` + `login-page-smoke.spec.ts`（Playwright Chromium,外部 dev server,无后端）：**2 passed**。表格 smoke 断言:密度 standard→compact class 切换、金额列 `64,231.50 USDT`、涨跌 up 色 `rgb(51,214,166)` 且 up≠down。
- 真机自检(Playwright Chromium 截图 `/dev/design-system`,**0 console / 0 page error**):表格密度切换、数字右对齐 tabular、金额/百分比/涨跌/状态列渲染正常,涨跌色随惯例翻转。
- 全量 `npm run test:e2e`：**未跑**。原因:多数 spec 依赖后端(`:18888`,本环境未启动);本轮仅跑无后端依赖的 design-system / login smoke 并通过,未改既有业务页面/全局主题。

## 边界确认

- 未改 backend / migration / research / deploy / scripts;未改 GateK 阶段事实源(STATUS/ROADMAP/GATEK_PLAN)。
- 未接 AI / DH runtime / LIVE / real exchange;未新增业务大页面;未把 B1–B7 混入;未全局替换 AppProviders;未迁移既有业务页。
- 回滚方式:删除 `frontend/src/nq-design-system/format/`、`frontend/src/nq-design-system/table/`、`frontend/tests/e2e/design-system-table-smoke.spec.ts`,还原 `nq-design-system/index.ts` 与 `DesignSystemDemoPage.tsx` 即可完全回退。

---

# Worklog: NQ-FRONTEND-USE-LIVE-QUERY-B0.3

日期：2026-06-14

## 本轮目标

B0/B0.1/B0.2 均已合入 dev。本轮 B0.3 封装实时数据获取抽象 `useLiveQuery`(polling / 手动刷新 / freshness 归一化),为后续 Monitor / Paper Trading / MarketData / Risk 页面迁移做准备。当前阶段只 polling + 手动刷新,**不接 WebSocket / SSE**,不迁移业务大页面。

## 分支与隔离

独立 git worktree(`E:/Project/nexus-quant-fe-b01`,`node_modules` junction)基于最新 `origin/dev`(`7a479406`)新建 `feat/nq-frontend-use-live-query-b03`,与 Codex 的 dev HEAD 隔离。PR 边界:B0.3 独立 PR(base = `dev`),不混入 B1–B7。

## 修改范围

- 新增 `frontend/src/hooks/useLiveQuery.ts`:基于 TanStack Query 的实时数据抽象。
  - 支持 `pollingIntervalMs`(轮询)、`refresh()`(手动刷新)、`enabled`(启停)、`pauseOnHidden`(失焦暂停)。
  - 状态归一化为 `LiveStatus = loading / fresh / stale / error / disabled`;`liveStatusToFreshness()` 映射到 DataFreshness 的 `FreshnessState`。
  - 输出 `lastUpdatedAt`(epoch ms)、`latencyMs`(包裹 queryFn 计时)、`errorReason`(按 HTTP 状态脱敏)。
  - `staleAfterMs` 默认 `pollingIntervalMs*2`(无轮询 30s);1s 轻量 tick 让 fresh→stale 随时间推移与相对时间显示实时更新;失焦默认暂停轮询。
- 更新 `frontend/src/pages/dev/DesignSystemDemoPage.tsx`:新增"实时数据(useLiveQuery)"自检区(本地模拟源:轮询间隔切换 / 暂停·恢复 / 立即刷新 / 模拟错误;DataFreshness 由归一化状态驱动;最新价用 MoneyCell)。
- 新增 `frontend/tests/e2e/design-system-live-query-smoke.spec.ts`:fresh / disabled / error / 手动刷新归一化 smoke。

## 说明

- 模拟源是本地 fake(随机延迟,可模拟错误),**不打后端、不连 socket**;真实数据接入在页面迁移阶段由各页提供 queryFn。
- 列组件 / hook 读 `var(--nq-*)` 与 FreshnessState,与既有 v2 组件一致;未改全局 `AppProviders`(QueryClient 复用既有 Provider)。
- 未迁移任何业务页面(Monitor/Paper/MarketData/Risk);本轮只产出可复用抽象 + 自检。

## 验证记录

- `npm run build`（worktree，`tsc -b && vite build`）：**通过**,tsc 0 error,`✓ built in ~1s`。
- `npm run test:e2e -- design-system-live-query-smoke.spec.ts design-system-table-smoke.spec.ts login-page-smoke.spec.ts --project=chromium`(无后端 dev server)：**3 passed**。live-query smoke 断言 fresh→disabled→fresh→error→fresh 归一化(DataFreshness 同步 Fresh/Disabled/Error)。
- 真机调试(Playwright Chromium,**0 console error**):status 持续 `fresh`,轮询每 3s 更新,`Fresh (Xs ago · Yms)` latency 实测 387ms→219ms,惯例与状态显示正常。
- 全量 `npm run test:e2e`：**未跑**。原因:多数 spec 依赖后端(`:18888`,本环境未启动);本轮仅跑无后端依赖的 design-system / live-query / login smoke 并通过,未改既有业务页面/全局主题。

## 边界确认

- 未改 backend / migration / research / deploy / scripts;未改 GateK 阶段事实源。
- 未接 WebSocket / SSE;未接 AI / DH runtime / LIVE / real exchange;未新增业务大页面;未把 B1–B7 混入;未全局替换 AppProviders;未迁移既有业务页。
- 回滚方式:删除 `frontend/src/hooks/useLiveQuery.ts`、`frontend/tests/e2e/design-system-live-query-smoke.spec.ts`,还原 `DesignSystemDemoPage.tsx` 即可完全回退。

---

# Worklog: NQ-FRONTEND-BACKTEST-DETAIL-VISUALIZATION-B1

日期：2026-06-14

## 本轮目标

B0/B0.1/B0.2/B0.3 均已合入 dev。本轮 B1 新增回测详情可视化页,展示权益/回撤曲线、关键指标摘要、数据集快照、参数快照、交易/风险摘要。**只复用现有真实 API,缺口显式 unavailable,不用假数据伪装。** 不接 AI/DH/LIVE/socket,不迁移其它业务页。

## API / 数据依赖核查(关键)

- **关键指标 + 交易/风险摘要:可用** —— `GET /evaluations?backtestConfigId` + `GET /evaluations/{id}` 提供 totalReturn/totalReturnRate、maxDrawdown/maxDrawdownRate、sharpeRatio、winRate、orderCount/tradeCount、winning/losing/flat、netPnl、finalEquity、profitLossRatio、totalFee、totalSlippage、realized/unrealizedPnl 等真实字段。
- **数据集快照:可用** —— `GET /marketdata/datasets` 按 `config.datasetId` 匹配 typed 字段(symbol/interval/start-end/barCount/gapCount/qualityStatus/status)。
- **参数/策略/配置快照:可用** —— `GET /backtest-configs/{id}` 的 strategyVersionId + paramSnapshotJson + strategyVersionSnapshotJson + configSnapshotJson + 区间 + initialCapital + createdAt。
- **权益/回撤时间序列:缺口** —— 后端**无回测时间序列端点**(不同于 paper-trading 的 equity-curve);仅有聚合指标 + 不透明 `reportJson`/`metricsJson`。本轮对 report/metrics JSON 做**防御式解析**(候选键 `equityCurve/equity/equitySeries`、`drawdownCurve/drawdown/drawdownSeries`),解析到真实数组才渲染,否则显式 unavailable;**未编造曲线**。建议后端补 `GET /backtest-runs/{id}/equity-curve`(及 drawdown)端点,或在 reportJson 固化序列结构。
- 单位口径假设:`*Rate`(收益率/回撤率/胜率)按后端比例值 ×100 展示并在 UI 注明;若后端已是百分比口径需后端对齐(已在页面文案标注)。

## 修改范围

- 新增 `frontend/src/components/backtest/BacktestCurveChart.tsx`:v2 ECharts 'nq' 主题的权益/回撤曲线,无序列时显式 unavailable(不编造)。
- 新增 `frontend/src/pages/backtests/BacktestDetailPage.tsx`:回测详情页。`useLiveQuery`(`pollingIntervalMs=0`,仅手动刷新 + freshness,不轮询静态回测)驱动评估明细;config/evaluations/datasets 用 useQuery;指标卡 + 曲线 + 交易/风险摘要表(复用 B0.2 NumberCell/MoneyCell/PercentCell/ChangeCell/StatusCell)+ 数据集快照(typed)+ 参数/策略/配置快照(JSON 美化);各字段缺失显式 `—`/empty/unavailable。进入页面 `applyNqCssVars()` 注入 `--nq-*`(additive,与 v1 `--nq-color-*` 不冲突)。
- 改 `frontend/src/router/routes.tsx`:新增 `backtests/:backtestConfigId` 详情路由(ConsoleLayout 子路由,沿用 backtests 菜单高亮)。
- 改 `frontend/src/pages/backtests/BacktestsPage.tsx`:列表操作列新增"可视化"入口(navigate 到详情路由),保留既有"查看详情"抽屉,最小改动。
- 改 `frontend/src/pages/dev/DesignSystemDemoPage.tsx`:新增"回测曲线(BacktestCurveChart)"组件自检区(样本权益/回撤 + 无序列 unavailable),供 backend-free smoke。
- 新增 `frontend/tests/e2e/design-system-backtest-chart-smoke.spec.ts`。

## 验证记录

- `npm run build`（worktree，`tsc -b && vite build`）：**通过**,tsc 0 error,`✓ built in ~0.9s`。
- `npm run test:e2e -- design-system-backtest-chart-smoke.spec.ts design-system-live-query-smoke.spec.ts design-system-table-smoke.spec.ts login-page-smoke.spec.ts --project=chromium`(无后端 dev server)：**4 passed**。backtest chart smoke 断言样本曲线渲染 canvas + 无序列显式 unavailable。
- 真机自检(Playwright Chromium 截图 `/dev/design-system` 回测曲线区,**0 console error**):权益(primary 面积)/回撤(danger 面积,负值)/unavailable 占位渲染正常。
- **BacktestDetailPage 本身未做浏览器 e2e**:它是 `RequireAuth` 下的业务页,依赖后端(`:18888`)+ 登录态,本环境均不可用;其组成部分(曲线组件 / B0.2 列 / useLiveQuery)已由 design-system smoke 覆盖,页面通过 `tsc` 类型检查与 hook 顺序复核。后端就绪环境需补 backtest detail e2e。
- 全量 `npm run test:e2e`:**未跑**。原因:多数 spec 依赖后端,本环境未启动。

## 边界确认

- 未改 backend / migration / research / deploy / scripts;未改 GateK 阶段事实源。
- 未接 AI / DH runtime / LIVE / real exchange;未接 WebSocket/SSE;**未用假数据伪装后端已就绪**(曲线缺口显式 unavailable + 报告)。
- 未新增 Backtest 以外业务大页面;未把 Dashboard/Strategy/Risk/Paper 迁移混入;未全局替换 AppProviders。
- 回滚方式:删除 `frontend/src/components/backtest/`、`frontend/src/pages/backtests/BacktestDetailPage.tsx`、`frontend/tests/e2e/design-system-backtest-chart-smoke.spec.ts`,还原 `routes.tsx` / `BacktestsPage.tsx` / `DesignSystemDemoPage.tsx` 即可完全回退。

---

# Worklog: NQ-BACKTEST-EQUITY-DRAWDOWN-SERIES-API-PLAN

日期：2026-06-15

## 本轮目标

为 B1 回测详情的权益/回撤曲线规划后端时间序列契约。**planning only,只读 + docs,不改任何 Java/TS/Python 代码、不新增 migration、不实现 API。**

## 关键发现(只读后端审计)

- 回测权益/PnL 时间序列**已存在并已暴露**:
  - 表 `sim_pnl_snapshots`(`V8__gate_f3_simulated_execution_facts.sql`),按 `backtest_run_id` + `snapshot_time` 存储,索引 `(backtest_run_id, snapshot_time)`;字段含 `equity / cash_balance / position_market_value / realized_pnl / unrealized_pnl / total_fee / total_slippage / net_pnl`。
  - 端点 `GET /api/backtest-runs/{runId}/pnl-snapshots`(`BacktestRunController` → `BacktestFactQueryService.listPnlSnapshots` → `JdbcSimPnlSnapshotRepository`)返回该序列。
  - 评估侧 `DrawdownCalculator` / `EvaluationMetricCalculator` 已基于该 equity 序列计算 maxDrawdown 等。
- 结论:**B1 曲线 unavailable 是前端未接线既有端点,不是后端缺口。** 无需新增后端 API/表/migration;回撤序列由 equity 客户端派生(与后端同口径)。
- `API.md` 此前漏记 sim-orders/trades/positions/pnl-snapshots 端点,本轮补记为事实。

## 修改范围(docs only)

- 新增 `docs/current/BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md`(逐条回答 10 个问题 + 契约/schema/前端对接/测试/风险)。
- 更新 `docs/current/API.md`:补记既有 run-fact 端点 + 指向 plan 文档,标注前端消费为 planning 未实现。
- 更新 `docs/current/README.md`:当前事实文件列表新增 plan 文档指针(planning 口径)。
- 更新 `docs/current/WORKLOG.md` / `TESTING.md`:本轮 planning 记录。

## 验证记录

- 本轮 **docs-only / planning-only**,未改代码,未运行 `mvn test` / `npm run build` / `npm run test:e2e`(无代码变更,无需构建)。
- 只读核查命令:`rg`(equity/drawdown/reportJson/metricsJson/SimPnlSnapshot)、读取 `BacktestRunController`、`BacktestFactQueryService`、`SimPnlSnapshot(Response)`、`JdbcSimPnlSnapshotRepository`、`DrawdownCalculator`、`V8` migration。
- `git status --short`:仅 5 个 docs/current 文档变更。

## 边界确认

- 未改 Java / TypeScript / Python 代码;未新增 API 实现;未新增/修改 migration;未改前端页面。
- 未接 AI / DH runtime / LIVE / real exchange;未用 mock 替代真实契约。
- 未把 API plan 写成 implemented(前端 B1.1 明确标注为 planning / 未实现;已存在的后端端点据实记录为 implemented)。
- 回滚方式:删除新增 plan 文档,还原 `API.md` / `README.md` / `WORKLOG.md` / `TESTING.md` 本轮追加段落即可。

---

# Worklog: NQ-FRONTEND-BACKTEST-EQUITY-CURVE-WIRING-B1.1

日期：2026-06-15

## 本轮目标

把 B1 回测详情页的权益/回撤曲线接到既有真实端点 `GET /api/backtest-runs/{runId}/pnl-snapshots`。**前端 only:不新增后端 API、不新增 migration、不用假数据。**

## 修改范围

- `frontend/src/types/backtests.ts`:新增 `SimPnlSnapshotItem`(对应后端 `SimPnlSnapshotResponse`:snapshotTime/equity/cashBalance/positionMarketValue/realized+unrealizedPnl/netPnl/totalFee/totalSlippage)。
- `frontend/src/api/backtests.ts`:新增 `pnlSnapshots(runId)` → `GET /backtest-runs/{runId}/pnl-snapshots`。
- `frontend/src/api/query-keys.ts`:`backtestsQueryKeys.pnlSnapshots(runId)`。
- `frontend/src/pages/backtests/BacktestDetailPage.tsx`:
  - `runId = selectedEvaluation.backtestRunId`(与展示指标同一 run);`useLiveQuery` 拉 pnl-snapshots(`pollingIntervalMs=0`,仅手动刷新)。
  - equity 曲线 = `snapshotTime + equity` 直接映射(过滤 null)。
  - drawdown 曲线 = 客户端派生 `equity − 运行峰值`(≤0,向下),口径同后端 `DrawdownCalculator` 的 `peak − equity` 取负(代码注释 + 页面文案 + 本文均说明)。
  - 移除原 report/metrics JSON 防御式解析(`extractSeries`/`normalizePoint`),改用真实序列。
  - 无 `runId` / 加载错误 / 空快照 → 明确 unavailable 文案(不编造);新增曲线区 `DataFreshness`(点数 + latency)+ 来源说明。
  - 指标卡 / 数据集快照 / 参数快照 / 交易风险摘要保持不变(不回退)。
- `frontend/tests/e2e/design-system-backtest-chart-smoke.spec.ts`:更新注释,绑定 B1.1 契约(有序列渲染 / 无序列 unavailable,组件即页面喂入真实序列的同一组件)。

## 验证记录

- `npm run build`(worktree,`tsc -b && vite build`):**通过**,tsc 0 error。
- `npm run test:e2e -- design-system-backtest-chart-smoke + live-query + table + login --project=chromium`(无后端 dev server):**4 passed**。backtest chart smoke 覆盖有序列渲染 canvas + 无序列 unavailable。
- `BacktestDetailPage` 页面级 e2e:**未跑(诚实标注)** —— 该页在 `RequireAuth` 下依赖后端(`:18888`)+ 登录态,本环境不可用;曲线组件 + 映射逻辑由 design-system smoke + tsc 覆盖,页面级有序列/无序列 e2e 需带后端环境执行。
- 全量 `npm run test:e2e`:未跑(多数 spec 依赖后端)。

## 边界确认

- 未改 backend / migration / research / deploy / scripts;未改 GateK 事实源;未新增后端 API。
- 未接 AI/DH/LIVE/real exchange/WebSocket/SSE;未用假数据(空/缺 run 显式 unavailable);未把 Dashboard/Strategy/Risk/Paper 迁移混入;未全局替换 AppProviders。
- 回滚方式:还原 `types/backtests.ts` / `api/backtests.ts` / `query-keys.ts` / `BacktestDetailPage.tsx` / 该 smoke 即可(曲线退回 B1 的 unavailable 行为)。

---

# Worklog: NQ-FRONTEND-BACKTEST-DETAIL-E2E-B1.2

日期：2026-06-15

## 本轮目标

为 BacktestDetailPage 补**页面级 E2E**:有真实 pnl snapshots 的 run → 曲线渲染 + 指标/快照/摘要不回退;无可用序列 → 显式 unavailable。走真实后端 + 真实登录 + 真实 fixture,不伪造。

## Fixture / 数据现状(只读)

- 既有 E2E 是**真实后端集成**(`support.ts loginToConsole` 真实 `POST /api/auth/login` + 真实账户准备 + 真实表单提交),无 route-stub。
- 既有 `gatei2-fixtures.ts` 已提供 `prepareGateI2EvaluationFixture`:创建 config → `POST /api/backtest-runs` → `POST .../start`(执行,写入 `sim_pnl_snapshots`)→ `POST .../evaluate`,返回 `backtestConfigId / backtestRunId / evalReportId`。**足以产出"有快照 + 有评估"的真实 run**。
- **测试发现的 bug**:`support.ts` 登录助手仍用旧英文选择器 `Username / Password / Sign in`,B0.1 登录页改版后已不存在 → 所有页面级 e2e 在登录前置会失败。本轮按 `frontend/tests/e2e/**` 允许范围修复为 `账号 / 密码 / 登录`(与 `login-page-smoke` 一致)。
- **缺口(已报告,未伪造)**:"已评估但 sim_pnl_snapshots 为空的 run"无法经现有 API 复现(执行后的 run 必写逐 bar 权益快照;未执行的 run 无 evaluation → 页面解析不出 runId)。因此"空序列 → unavailable"用真实可达的**无 run/无评估**路径验证(`所选评估缺少 backtestRunId`),并在文档标注该子场景不可 API 复现。

## 修改范围(仅 tests/e2e)

- `frontend/tests/e2e/support.ts`:修复登录选择器为 B0.1 中文(`账号 / 密码 / 登录`)。
- `frontend/tests/e2e/backtest-detail-smoke.spec.ts`(新增):
  - 用例 1(有快照):`prepareGateI2EvaluationFixture` → `/backtests/{configId}` → 等 `GET /api/backtest-runs/{runId}/pnl-snapshots` ok + 断言非空 → 权益/回撤曲线 canvas 渲染、无 unavailable、指标摘要非空、交易风险摘要表存在、数据集/参数快照区存在(不回退)。
  - 用例 2(无 run):`prepareGateI2BacktestTraceFixture`(不建 run)→ `/backtests/{configId}` → 断言曲线 `所选评估缺少 backtestRunId` unavailable + 指标空态明确 + 数据集快照区仍在。
- 未改任何 src(本轮无需 pages/backtests / api / types 小修)。

## 验证记录

- `npm run build`(`tsc -b && vite build`):**通过**,tsc 0 error。
- `playwright test --list`(全 27 文件 / 31 用例,含本轮 2 个新用例,无需 server):**全部编译/收集通过**(确认 `support.ts` 修复与新 spec 的 import/类型正确)。
- 无后端 smoke(`login-page-smoke` + `design-system-*`,共 4 用例):**4 passed**(确认本轮改动未回退既有 backend-free smoke)。
- **本 spec(backtest-detail-smoke)+ 所有依赖 `loginToConsole` 的 backend 集成 spec:本环境未运行** —— 后端 `127.0.0.1:18888` 不可达(`curl` 返回 000)。**阻塞原因 = 后端未启动**(非测试失败、非 fixture 不足)。
- 全量 `npm run test:e2e`:同因后端不可用未跑。

## 需要的后端 fixture / 运行条件(供带后端环境执行)

- 启动本地后端(`:18888`)+ PostgreSQL;`E2E_USERNAME / E2E_PASSWORD`(默认 admin / ChangeMe123!);可用 OKX/SIM 默认账户(support.ts 自动准备)。
- 用例 1 全自动 seed(`prepareGateI2EvaluationFixture`,含 fixture bars 导入 + run + start + evaluate)。
- 跑:`npm run test:e2e -- tests/e2e/backtest-detail-smoke.spec.ts --project=chromium`(带后端)。

## 边界确认

- 未改 backend / migration / research / deploy / scripts;未改 GateK 事实源;未新增后端 API。
- 未接 AI/DH/LIVE/real exchange/WebSocket/SSE;**未用假数据伪装真实后端**(用真实 fixture + 真实端点;空场景用真实可达 unavailable);未迁移 Dashboard/Strategy/Risk/Paper;未全局替换 AppProviders;既有 backend-free smoke 不回退。
- 回滚方式:删除 `backtest-detail-smoke.spec.ts`,还原 `support.ts` 登录选择器即可(注:还原 support.ts 会回到对 B0.1 登录页失效的旧选择器,不建议)。

---

# NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX

日期：2026-06-16

## 本轮目标

最小修复 Batch 2D `nq-app` context smoke 首次 CI 失败（GitHub Actions run `27590822405`，step `Run nq-app PostgreSQL context smoke`，`NqAppContextPostgresSmokeTest` errors=1）。失败根因：Spring context 创建 `AdapterBackedTradingVenueGateway` 时 `IllegalArgumentException: venue must not be blank`。保持无 seed、无真实交易所、无真实 credential、无 LIVE、无 AI、无 DH runtime，并收口 CI log hygiene。

## 失败根因（只读分析）

- `AdapterBackedTradingVenueGateway`（`nq-scheduler`，eager singleton）构造期对每个 `TradingAdapter` bean 调用 `adapter.venue()` 组装 venue→adapter 路由表。
- 首版 smoke 用裸 `@MockitoBean` 替换 OKX/Binance adapter；Mockito mock 的 `venue()` 默认返回 blank，gateway 在 context refresh 期间即抛 `venue must not be blank`。
- `@MockitoBean` 无法在 context refresh 前 stub（eager singleton 早于任何 `@BeforeEach`）。

## 修改范围（仅 nq-app test）

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java`：
  - 新增嵌套 `@TestConfiguration StubbedExchangeAdapterConfig`，以预先 stub 的 Mockito mock 覆盖 `okxTradingAdapter` / `binanceTradingAdapter`，`venue()` 固定为互异 CI-only fake 值 `CI-SMOKE-FAKE-OKX` / `CI-SMOKE-FAKE-BINANCE`，使生产 gateway 能在 refresh 期建表。
  - `@TestPropertySource` 增加 `spring.main.allow-bean-definition-overriding=true`（仅用于这两个具名 adapter bean 覆盖；本地 full-context 测试不带此 flag 也能起，故不掩盖重复定义风险）。
  - OKX/Binance WS client 仍 `@MockitoBean`；断言由对 adapter 的 `verifyNoInteractions` 改为 `verify(..., never()).placeOrder/cancelOrder/getOrder(...)`（gateway 合法调用 `venue()`，blanket `verifyNoInteractions` 永不可过），WS client 保留 `verifyNoInteractions`。
- 未改 backend production code、frontend、research、scripts、deploy；未新增/修改 migration、API；未改 `.github/workflows/ci.yml`（见下）。

## CI log hygiene

- `postgres-flyway` job 首步 `Mask CI-only PostgreSQL connection values` 已对 `NQ_FLYWAY_DB_URL/USER/PASSWORD` 注册 `::add-mask::`，后续 step 日志被遮蔽。
- 无 step 使用 `set -x`、`env`、`printenv`、full env dump，也不 echo JDBC password / 完整连接串；DB 属性仅经 `-D...="${VAR}"` 传给 Maven。step 级 hygiene 已满足，故本轮 `ci.yml` 无需改动。
- 残留：GitHub Actions 在 "Initialize containers" 自身输出 PostgreSQL service 容器 env（含一次性 `POSTGRES_PASSWORD`），早于任何 step、无法被 `::add-mask::` 覆盖。该值为一次性非生产 CI DB 口令；彻底消除需 GitHub Secrets（2D 明确排除）或改动 FROZEN 2A/2B/2C 共享 service auth（超出 2D first-run-fix 范围），记为 P3 平台残留并延后。

## 验证记录

- `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest -Dsurefire.failIfNoSpecifiedTests=false`：**BUILD SUCCESS**；`NqAppContextPostgresSmokeTest` tests=1 / failures=0 / errors=0 / **skipped=1**（本地无 `nq.app.context.smoke.required`，类被 `@EnabledIfSystemProperty` 跳过）。本地仅证明编译与 Surefire 选择；真实 PostgreSQL context 启动需下一次 GitHub Actions run 确认（CI required=true 后 skipped 必须为 0）。
- `git status --short` / `git diff --check` / `git diff --stat`：仅 1 个 test 文件改动；无 whitespace 错误；migration / frontend / research / scripts / deploy diff 均为空。

## 状态

Batch 2D：IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN（不得写 FIRST GREEN / FROZEN / ACCEPTED）。Batch 2E NOT STARTED；Batch 3-5 PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED。

## 边界确认

- 未改 backend production code / frontend / research / scripts / deploy；未新增 API / migration；未改历史 migration。
- 未用 `local` profile；未触发 `AuthSeedConfiguration`（`@Profile({"local","test"})`，smoke 走 `ci-app-smoke`）；`AuthBootstrapAdminConfiguration` 经 `nq.auth.bootstrap-admin.enabled=false` 关闭；未创建 seed users / legacy accounts / exchange accounts / credential rows。
- 未访问 OKX/Binance/Bybit/Gate/Coinbase/Kraken；未实现 RealClient / real provider；未读取或输出真实 credential material；未开启 LIVE / AI / DH runtime。
- 未实现 Batch 2E / Batch 3 no-outbound guard / Batch 4 secret scan / Batch 5 frontend E2E hardening。

## 回滚方式

`git checkout -- backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java` 还原测试，并 revert 本轮 `docs/current/*` 状态文案即可；不涉及生产代码 / migration / workflow，回滚无副作用。

## 下一步

re-run `NQ CI Baseline`（dev）→ `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW`；若仍红则继续 `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX`。

---

# NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW (+ FIRST-RUN-FIX #2)

日期：2026-06-16

## 本轮目标

评审 first-run fix（commit `7156b32c`）后的 GitHub Actions run，确认 nq-app context smoke 是否在 CI PostgreSQL service DB 上真实通过。结果为 FAIL：暴露第二个根因，按任务 CI 失败时允许的 carve-out 转入 FIRST-RUN-FIX，应用最小 test-only 修复。

## 评审结论（run 27592872701）

- run `27592872701`（commit `7156b32c`，push，dev）：completed / **failure**，1m54s。
- jobs：Diff check ✓ / Frontend build ✓ / Backend Maven test ✓ / Research quality gate ✓ / **PostgreSQL / Flyway smoke ✗**。
- `PostgreSQL / Flyway smoke` job `81577141123`：仅 `Run nq-app PostgreSQL context smoke` 失败；Flyway V1-V31、schema artifact generate/check/upload、repository PostgreSQL smoke（`JdbcRepositoryPostgresSmokeTest` tests=1/skipped=0/failures=0/errors=0）均仍 success。
- venue 修复已生效：不再有 `venue must not be blank`，context 已越过 `AdapterBackedTradingVenueGateway`。

## 第二个根因

- `NqAppContextPostgresSmokeTest` errors=1：Spring 创建 `securityFilterChain`（`SecurityConfiguration`）失败 —— `No qualifying bean of type 'HttpSecurity' available`。
- 原因：smoke 用 `webEnvironment = NONE` + `spring.main.web-application-type=none`，应用为非 web 上下文；`HttpSecurity` 仅由 `HttpSecurityConfiguration`（`@ConditionalOnWebApplication(type = SERVLET)`）提供，非 web 下缺失，生产 servlet-web `SecurityConfiguration` 无法装配。NexusQuant 是 servlet web 应用，composition root 必须以 servlet web 上下文启动。属原 2D smoke 设计缺陷，venue 修复后才暴露。

## 第二次修复（test-only）

- `backend/nq-app/src/test/java/.../smoke/NqAppContextPostgresSmokeTest.java`：
  - `webEnvironment = SpringBootTest.WebEnvironment.NONE` → `WebEnvironment.MOCK`；删除 `spring.main.web-application-type=none`。`MOCK` 加载完整 servlet web 上下文（DispatcherServlet wiring + Spring Security filter chain），不起 server、不开端口、不调 controller，`HttpSecurity` 可用，`securityFilterChain` 正常装配。
  - 与既有 `local` profile full-context 测试（`MarketdataControllerLocalIntegrationTest` / `OkxBootstrapNoOutboundLocalContextTest`，默认 `MOCK`）一致，属本 app 已证可行模式，降低风险。
  - adapter venue stub / mocks / datasource / 断言均不变；未改生产代码 / migration / workflow。

## 验证记录

- `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest -Dsurefire.failIfNoSpecifiedTests=false`：**BUILD SUCCESS**；tests=1 / failures=0 / errors=0 / **skipped=1**（本地无 CI DB props，类被 `@EnabledIfSystemProperty` 跳过）。真实 servlet-web context 启动需下一次 GitHub Actions run 确认（skipped=0 / errors=0）。
- `git status --short` / `git diff --check`（无 whitespace 错误）/ `git diff --stat`：仅 1 个 test 文件 + docs 改动；migration / frontend / research / scripts / deploy diff 均为空。

## 状态

Batch 2D：IMPLEMENTED / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN（仍不得写 FIRST GREEN / FROZEN / ACCEPTED）。Batch 2E NOT STARTED；Batch 3-5 PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED。

## 边界确认

- 未改 backend production code / frontend / research / scripts / deploy / workflow；未新增 API / migration；未改历史 migration。
- 未用 `local` profile；未 as-is 复用 `test` profile；未触发 `AuthSeedConfiguration`；未创建 seed users / legacy accounts / exchange accounts / credential rows。
- 未访问真实交易所；未实现 RealClient / real provider / real adapter；未开启 LIVE / AI / DH runtime；未读取或输出真实 credential material。
- 未实现 Batch 2E / Batch 3-5。

## 下一步

push 第二次修复 → re-run `NQ CI Baseline`（dev）→ `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW`；若仍红则继续 `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX`。建议 commit message：`test(ci): nq-app context smoke 改用 MOCK web 环境修复 security 装配（Batch 2D first-run fix #2）`。
