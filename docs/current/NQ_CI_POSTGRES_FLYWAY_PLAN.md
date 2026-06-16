# NQ CI PostgreSQL / Flyway Plan

任务：NQ-CI-POSTGRES-FLYWAY-PLAN / NQ-CI-POSTGRES-FLYWAY-2A-IMPL / NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW / NQ-CI-POSTGRES-FLYWAY-2B-PLAN / NQ-CI-POSTGRES-FLYWAY-2B-IMPL / NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW / NQ-CI-POSTGRES-FLYWAY-2C-PLAN / NQ-CI-POSTGRES-FLYWAY-2C-PLAN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2C-FIRST-RUN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2C-FREEZE-REVIEW / NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX / NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIRST-RUN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FREEZE-REVIEW / NQ-CI-POSTGRES-FLYWAY-2D-PLAN / NQ-CI-POSTGRES-FLYWAY-2D-IMPL / NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW
日期：2026-06-15
状态：Batch 2A FROZEN / ACCEPTED；Batch 2B FROZEN / ACCEPTED；Batch 2C FROZEN / ACCEPTED；2C-HYGIENE-FIX FROZEN / ACCEPTED；Batch 2D IMPLEMENTED / FIRST-RUN-FIX #2 CI FAILED / FIRST-RUN-FIX REQUIRED；Batch 2E NOT STARTED

## Current state

- Project: NexusQuant / NQ。
- Current branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted。
- NQ CI Batch 1 已实现并通过 GitHub Actions run `27496906788` first green review。
- Batch 1 当前 jobs：`diff-check`、`backend`、`frontend`、`research`。
- Batch 1 backend job 已临时使用 GitHub Actions `postgres:16` service 和 CI-only seed watcher 支撑 `mvn -f backend/pom.xml test`，但这只是 runner dependency workaround，不是 Batch 2 PostgreSQL / Flyway hardening。
- Batch 2A PostgreSQL / Flyway empty DB smoke：FROZEN / ACCEPTED；GitHub Actions run `27501253175` 在 commit `7836640ebae46d6fc62771611f5215661b3267dc` 上 completed / success，并已完成 freeze review。
- `postgres-flyway` job `81284424653` completed / success；step `Run empty database Flyway smoke` success；日志显示 empty PostgreSQL 16.14 DB 从 V1 迁移到 V31，并执行 `validate`。
- Batch 2B Flyway info / schema artifact / docs update：FROZEN / ACCEPTED；详见 `NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`。
- Batch 2C repository real PostgreSQL smoke：FROZEN / ACCEPTED；GitHub Actions run `27535619157` / job `81384164182` completed / success；freeze review 已接受其作为当前 `dev` repository-only real DB 最小验证基线；2C-HYGIENE-FIX 已由 GitHub Actions run `27550583713` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；详见 `NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`。
- Batch 2D nq-app context smoke：IMPLEMENTED / FIRST-RUN-FIX #2 CI FAILED / FIRST-RUN-FIX REQUIRED；详见 `NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`。
- Batch 2E CI-only seed watcher cleanup：NOT STARTED。
- Batch 3 no-outbound guard：PENDING。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI: NOT STARTED。
- DH runtime: NOT INTEGRATED / not connected to NQ。
- LIVE: DISABLED。
- real exchange permission probe adapter: NOT IMPLEMENTED。

## Scope

Allowed in Batch 2A implementation:

- 修改 `.github/workflows/ci.yml` 新增最小 `postgres-flyway` job。
- 同步 `NQ_CI_BASELINE_PLAN.md`、`README.md`、`TESTING.md`、`WORKLOG.md` 的 Batch 2A implementation / pending first CI run 状态。

Forbidden in Batch 2A implementation:

- 不修改 Java / TypeScript / Python / test code。
- 不新增 API，不新增 migration，不修改历史 migration。
- 不修改 backend 生产逻辑、frontend、research、scripts、deploy。
- 不启动 `nq-app` full context，不运行 repository real PostgreSQL smoke，不运行 frontend E2E。
- 不插入 legacy account seed、test fixture seed、真实账户 seed 或真实交易所 seed。
- 不启用 Testcontainers，不使用 `baselineOnMigrate`，不运行 Flyway `clean`。
- 不开启 LIVE，不接 AI，不接 DH runtime，不实现 NQ RealClient / real provider / real exchange permission probe adapter。
- 不调用 OKX / Binance / Bybit / Gate / Coinbase / Kraken，不读取或输出真实 credential material。
- 不把 Batch 2B freeze 扩展为完整 CI hardening；不把 Batch 2E、Batch 3/4/5 写成 implemented；Batch 2D 只能写为 IMPLEMENTED / FIRST-RUN-FIX REQUIRED，直到下一次 CI run 重新跑绿后另起 freeze review。

## Current Batch 1 CI state

当前 `.github/workflows/ci.yml` 的 jobs：

| Job | Current behavior | Batch 2 relevance |
| --- | --- | --- |
| `diff-check` | 对 PR / push / manual run 执行 changed-file whitespace check。 | 保留为基础 hygiene gate。 |
| `backend` | Java 21 + Maven cache + `mvn -f backend/pom.xml test`；当前已配置 `postgres:16` service、`NQ_DB_URL`、`NQ_DB_USER`、`NQ_DB_PASSWORD`，并用 CI-only seed watcher 插入最小 `accounts` legacy row。 | 说明 full Maven test 已依赖 PostgreSQL service；Batch 2 应拆出独立 `postgres-flyway` job，避免 seed workaround 掩盖 schema 问题。 |
| `postgres-flyway` | Java 21 + Maven cache + `postgres:16` service；使用 disposable CI PostgreSQL service DB；job steps 最早注册 `NQ_FLYWAY_DB_URL` / `NQ_FLYWAY_DB_USER` / `NQ_FLYWAY_DB_PASSWORD` masking；通过临时 Java smoke runner 调用 Flyway API 执行 `migrate` + `validate`，校验 current version 为 V31 并打印 `flyway_schema_history`；随后生成 / 检查 / 上传 schema artifacts，运行 Batch 2C `nq-infra` repository PostgreSQL smoke，并追加 Batch 2D `nq-app` context smoke。 | Batch 2A / 2B / 2C FROZEN / ACCEPTED；2C-HYGIENE-FIX FROZEN / ACCEPTED；Batch 2D IMPLEMENTED / FIRST-RUN-FIX #2 CI FAILED / FIRST-RUN-FIX REQUIRED。Run `27596768301` selected the smoke under `ci-app-smoke`, skipped=0, and started the servlet web context, but the test method failed with `NotAMockException`. |
| `frontend` | Node 22 + npm cache + `npm ci` + `npm run build`。 | 不属于 Batch 2；frontend E2E hardening 仍为 Batch 5。 |
| `research` | Python 3.11 + pip cache + `pytest` / `mypy --no-sqlite-cache` / `ruff --no-cache`。 | 不属于 Batch 2。 |

当前 backend job 已使用 PostgreSQL，但目的仍是让 Batch 1 full Maven test 在 fresh GitHub runner 上可运行，不提供以下 Batch 2 能力：

- Batch 2A 已新增显式 empty DB migration smoke job。
- Batch 2B 已在 `postgres-flyway` job 中实现 Flyway `info` / schema metadata artifact generation，并由 GitHub Actions run `27521750442` first green + freeze review 固化为 FROZEN / ACCEPTED。
- Batch 2B 已生成 migration checksum metadata artifact；freeze review 确认 artifact 可下载且文件齐全。
- Batch 2B 已实现 schema-only dump artifact；freeze review 确认无 data-row marker。
- 无 docs/current `DB_SCHEMA.md` 与 migration 的自动 drift 检查。
- 无 repeatable migration 策略检查。
- 无 CI seed 边界文档化与去除条件。

## Current PostgreSQL / Flyway state

- `backend/nq-app/pom.xml` 依赖 `flyway-core`、`flyway-database-postgresql` 和 PostgreSQL runtime driver。
- `backend/nq-infra/pom.xml` 依赖 `flyway-core` 和 `spring-jdbc`，但当前 repository 单测多为 Recording / mock `JdbcTemplate`，不是 real DB tests。
- `application-local.yml` 使用 PostgreSQL datasource，Flyway enabled，locations 为 `classpath:db/migration`。
- `application-test.yml` 使用 PostgreSQL datasource placeholder，但 Flyway disabled；当前 test profile 不是 Batch 2 首选验证 profile。
- `docker-compose.yml` 提供本地 PostgreSQL，默认镜像 `${NQ_DB_IMAGE:-postgres:17.7}`，端口 `${NQ_DB_PORT:-5432}:5432`。
- GitHub Actions backend service 当前使用 `postgres:16`，与 local compose `postgres:17.7` 存在版本差异，Batch 2 应明确选择并记录。
- 当前 Flyway migration 最大版本为 `V31__schema_credential_permission_probe.sql`。
- 当前未发现 tracked repeatable migration（`R__*.sql`）。
- 当前未发现 Testcontainers dependency 或 `PostgreSQLContainer` 使用证据。
- 当前未发现 H2 / embedded DB 作为 repository real DB 测试基线。
- `nq-app` local Spring context tests 已能在 CI PostgreSQL service 上启动；代表 context startup 可行，但不等于 schema hardening 已完成。

## PostgreSQL service vs Testcontainers decision

| Option | Fit | Pros | Cons | Batch 2 decision |
| --- | --- | --- | --- | --- |
| GitHub Actions service container | CI empty DB migration smoke | 不改代码 / 不加依赖；workflow 可读；日志和 artifacts 易收集；与当前 Batch 1 backend workaround 接近。 | 与本地 `postgres:17.7` 可能存在版本差异；service readiness 和 seed watcher 需谨慎隔离。 | Batch 2 第一阶段优先使用。 |
| Testcontainers | 后续 repository real DB tests | 测试自持 DB 生命周期；并行隔离更好；适合 SQL / JSONB / constraint 行为回归。 | 需要新增或启用 test dependency / test code；会扩大 Batch 2A scope。 | 后续增强，不与 Batch 2A 最小实施混在一起，除非现有测试已稳定使用。 |
| docker-compose | 本地人工验证 | 与本地 runbook 一致；适合开发机复现。 | GitHub Actions 中使用 compose 会增加启动和网络复杂度；不适合作为最小 PR gate。 | 只作为本地复现参考，不作为 Batch 2A 首选 CI 方案。 |
| 本地 PostgreSQL | 开发机验证 | 快速，已被本地 local profile 使用。 | 不可移植；容易依赖开发机残留数据；无法作为 GitHub merge gate。 | 不作为 CI 方案。 |
| H2 / embedded database | 纯单测或简单 DAO | 启动快。 | 无法覆盖 PostgreSQL JSONB、TIMESTAMPTZ、CHECK、index、Flyway dialect 和 schema history。 | 不用于 Batch 2 PostgreSQL / Flyway 验证。 |

Recommendation:

- Batch 2A 使用 GitHub Actions PostgreSQL service container 建立 `postgres-flyway` 最小 job。
- Testcontainers 作为 Batch 2C 或后续 dedicated test enhancement；只有当新增 repository real DB smoke 已评审且依赖稳定时才引入。

## Flyway migration validation plan

Batch 2 必须验证：

1. Empty DB 从 `V1` 迁移到当前最大版本 `V31`。
2. Migration order 必须严格按版本推进；不允许 out-of-order。
3. Checksum drift 必须阻塞 PR；历史 migration 不可编辑。
4. Repeatable migration 当前未发现；若未来新增 `R__*.sql`，必须在 plan / docs 中说明触发条件、可重复性和 checksum 变化预期。
5. `baselineOnMigrate` 默认禁止；只允许在已有生产库引入 Flyway 的独立 DBA / deployment plan 中评审，不进入 PR CI 默认路径。
6. `clean` 禁止用于生产语境；CI 如需清理只能删除 disposable CI database / container，不运行会误导生产习惯的 clean 流程。
7. Migration naming 必须使用 `V<version>__<lower_snake_description>.sql`，版本单调递增，不跳改历史。
8. CI 应记录 Flyway schema history、applied version、state、checksum 和 execution order。
9. Schema drift 检查应至少对 `docs/current/DB_SCHEMA.md` 中声明的关键表 / 字段 / CHECK / COMMENT 与 migration output 做人工或脚本化对照；脚本化 drift guard 可在 2B / 后续批次推进。
10. Legacy seed / accounts seed 不应进入 migration，除非未来有明确产品级默认数据策略；当前 CI seed 只能服务测试。

最小实现建议：

- 使用 fresh PostgreSQL database：`nq_ci`。
- 使用 CI-only PostgreSQL 用户和密码：`nq_ci_user` / `nq_ci_password`。
- Batch 2A 不使用 `NQ_PROFILE=local`，不启动 `nq-app`，避免触发 `ApplicationRunner` side effects。
- 使用 Maven `process-classes` 准备 `nq-app` / `nq-infra` runtime classpath，并在 workflow step 中生成临时 Java smoke runner 调用 Flyway API。
- Flyway 配置固定为 `locations("classpath:db/migration")`、`baselineOnMigrate(false)`、`cleanDisabled(true)`、`outOfOrder(false)`。
- 迁移完成后执行 `validate`，校验 current version 为 `31`，并输出 `flyway_schema_history`。
- Batch 2B 已补 schema metadata artifact / schema-only dump / docs，GitHub Actions run `27521750442` first green confirmed，并已完成 freeze review。

## Seed / legacy account boundary

Current CI-only seed watcher purpose:

- Batch 1 backend job 需要 `mvn -f backend/pom.xml test` 在 fresh GitHub runner 上通过。
- `nq-app` local Spring context tests 启动后依赖 legacy `accounts` 至少有一条 `PAPER / ACTIVE` row。
- Seed watcher 等待 Flyway 创建 `accounts` 表后插入 `ci-local-account`，避免改生产 migration 或测试代码。

Assessment:

- 该 watcher 属于 Batch 1 workaround，不是生产 runtime seed，不是 Flyway seed，不是 schema baseline。
- Batch 2 应显式文档化：CI seed 只能服务测试，不得成为生产默认数据。
- 不应把 legacy account seed 写入 migration；否则会污染所有环境并掩盖空库真实性。
- 允许在 workflow 或后续 test fixture SQL 中插入 fake legacy account，但必须使用明显 CI-only 值，且只在测试 DB / disposable DB 中执行。
- Seed 可能掩盖 schema 问题：如果 migration 本身缺约束、默认值或必填字段，过早 seed 可能让 context smoke 通过但 empty DB drift 未暴露。

Batch 2 seed policy:

- Flyway empty DB migration smoke 必须无 seed 运行，证明 V1-V31 可建库。
- Batch 2A 的 no-seed 定义：无 legacy account seed、无 test fixture seed、无真实账户 seed、无真实交易所 seed；不启动 app context，因此也不触发 `AuthSeedConfiguration` local/test user seed。
- 需要 app context / repository smoke 时，再执行 test fixture seed。
- Seed 不得包含真实账户、真实交易所账户、真实 credential、API key、secret、passphrase、token、cookie 或 LIVE 标记。
- Seed 必须使用 `PAPER` / `ACTIVE` / fake account code，只服务测试。
- 当 repository / app context 测试改为显式 fixture seed 或测试代码自建数据后，应移除或收口 CI-only seed watcher。

## nq-app context / repository DB test plan

Batch 2 不应一次性变成 full integration test 改造。建议分层：

- Batch 2A：只跑 Flyway empty DB migration smoke；不 seed；不启动全量 E2E。
- Batch 2B：保存 Flyway info / schema artifact，并同步 docs。
- Batch 2C：已新增最小 repository real PostgreSQL smoke；优先覆盖 PostgreSQL-specific 行为，例如 JSONB cast、unique index / `ON CONFLICT`、TIMESTAMPTZ 和 rollback-safe fixture behavior。
- Batch 2D：若需要，启动 `nq-app` context smoke；必须使用 CI-only datasource、fake users、LIVE disabled、real provider disabled / no-real port。
- Batch 2E：条件成熟后移除或收口 CI-only seed watcher。

Current test baseline:

- `nq-infra` repository 单测大量使用 Recording / mock `JdbcTemplate`，能检查 SQL shape，但不能证明 SQL 在 PostgreSQL 上真实执行。
- `nq-app` 存在 `@SpringBootTest` + `@ActiveProfiles("local")` 的 local context tests，已在 Batch 1 backend CI 中触发 Flyway 和真实 PostgreSQL service。
- `application-test.yml` 当前 Flyway disabled，不适合作为 Batch 2 empty DB migration smoke 的默认入口。

External call avoidance:

- Batch 2 不实现 no-outbound guard；该项仍为 Batch 3。
- 但 Batch 2 job 必须不注入真实 exchange credentials，不开启 WS / recovery / LIVE / real adapter。
- `nq-app` context smoke 若进入 Batch 2D，必须显式设置相关 env 保持 disabled / fake / local-only，不访问真实交易所。

## Schema drift / artifact plan

Recommended artifacts:

- `flyway-info.txt`：`flyway_schema_history` 的 version、description、type、script、checksum、success、installed_on、execution_time。
- `schema-tables.txt`：`information_schema.tables` / `pg_catalog` 表列表。
- `schema-columns.txt`：关键 columns、data types、nullable、default。
- `schema-constraints.txt`：primary key、unique、foreign key、CHECK constraint。
- `schema-comments.txt`：table / column comments，覆盖新增表与关键字段注释规则。
- Optional `schema-dump.sql`：仅 schema-only dump，不含 data，不含 credential material。

Drift policy:

- PR 中修改 tracked migration 后，必须由 Batch 2 job 重新生成 artifact。
- 历史 migration checksum drift、deleted migration、edited migration、missing migration 应阻塞 merge。
- `docs/current/DB_SCHEMA.md` 与 migration output 的差异先作为 2B review checklist；后续可脚本化为 blocking guard。
- V1-V31 必须全部校验；未来新增 V32+ 自动纳入当前最大版本。

## Implemented CI job design

Job name:

- `postgres-flyway`（UI display name: `PostgreSQL / Flyway smoke`）。

Working directory:

- repo root；Maven command 使用 `-f backend/pom.xml`。

Services:

```yaml
services:
  postgres:
    image: postgres:16
    env:
      POSTGRES_USER: nq_ci_user
      POSTGRES_PASSWORD: nq_ci_password
      POSTGRES_DB: nq_ci
    ports:
      - 5432:5432
    options: >-
      --health-cmd "pg_isready -U nq_ci_user -d nq_ci"
      --health-interval 10s
      --health-timeout 5s
      --health-retries 10
```

Environment:

```yaml
env:
  CI: "true"
  NQ_FLYWAY_DB_URL: jdbc:postgresql://localhost:5432/nq_ci
  NQ_FLYWAY_DB_USER: nq_ci_user
  NQ_FLYWAY_DB_PASSWORD: nq_ci_password
```

Log hygiene:

- The first `postgres-flyway` step registers `NQ_FLYWAY_DB_URL`, `NQ_FLYWAY_DB_USER`, and `NQ_FLYWAY_DB_PASSWORD` through GitHub Actions `::add-mask::`.
- The values remain disposable CI-only fake service DB values and are not moved into a real secret store in this batch.
- Service container initialization can occur before masking is active; any remaining service-level display of `nq_ci` / `nq_ci_user` / `nq_ci_password` is tracked as accepted P2 hygiene unless real credential material appears.

Java and cache:

- Java 21 via `actions/setup-java@v4`。
- Maven cache enabled, keyed by backend poms。

Implemented command:

- Prepare classpath without running tests or app context:

```bash
mvn -f backend/pom.xml -pl nq-app -am process-classes \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -DincludeScope=runtime \
  -Dmdep.outputFile=target/flyway-classpath.txt
```

- Generate and run a temporary Java smoke runner in `$RUNNER_TEMP`:

```bash
javac -cp "${classpath}" "${smoke_dir}/FlywaySmoke.java"
java -cp "${smoke_dir}:${classpath}" FlywaySmoke
```

This path calls Flyway `migrate` + `validate` directly and checks current version `31`; it does not start Spring Boot, does not run `@SpringBootTest`, and does not insert seed data.

Batch 2B artifact generation:

```bash
psql "$NQ_CI_PSQL_URL" -c "SELECT installed_rank, version, description, type, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank;"
pg_dump --schema-only --no-owner --no-privileges "$NQ_CI_PSQL_URL" > artifacts/postgres-flyway/schema-dump.sql
```

Timeout:

- `timeout-minutes: 15` for Batch 2A。
- Raise to `20` only if artifact collection and context startup are consistently close to the limit。

Artifacts:

- Batch 2A prints `flyway_schema_history` to job logs。
- Batch 2B uploads `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`。
- `schema-dump.sql` must be schema-only and must not contain data.
- Retention: 7 days for PR / branch, 14 days for `dev` push.

Merge blocking:

- Batch 2A / 2B may become required after first green + freeze review.
- Before first green / freeze review, run it manually or on PR as non-required observation, but never report skipped / failed as passed.
- After accepted, `postgres-flyway` should block migration / backend / DB docs changes.

## Security boundary

CI Batch 2 must enforce these boundaries by configuration and review:

- 不注入真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken API key、secret、passphrase、private key、token、cookie。
- 不设置 LIVE enabled，不启动真实 LIVE 下单、撤单、转账、提现。
- 不连接真实 NQ / DH runtime，不实现 NQ RealClient，不接 DH runtime。
- 不访问真实交易所 host；正式 no-outbound guard 仍是 Batch 3，不能在本轮写成已实现。
- PostgreSQL CI password 只能是测试默认值，例如 `"123456"`，不得使用 production DB credential。
- CI logs / artifacts 不得输出 credential material、raw request、raw response、headers、signature、decrypted payload。
- Permission probe 默认仍为 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`；真实 adapter NOT IMPLEMENTED。

## Implementation batches

### Batch 2A: PostgreSQL service + Flyway empty DB migration smoke

- Status: FROZEN / ACCEPTED。
- Added separate `postgres-flyway` job。
- Uses GitHub Actions PostgreSQL service container。
- Runs empty DB V1-V31 migration smoke through direct Flyway API。
- No legacy account seed, no test fixture seed, no real account seed, no real exchange seed。
- No `nq-app` full context and no `ApplicationRunner` side effects。
- No Testcontainers。
- No repository test expansion。

First green evidence:

- GitHub Actions run: `27501253175` (`NQ CI Baseline`)。
- Trigger / branch / commit: `push` on `dev`, `7836640ebae46d6fc62771611f5215661b3267dc`。
- Run result: completed / success。
- Job: `PostgreSQL / Flyway smoke` (`postgres-flyway`) job id `81284424653`, completed / success。
- Steps: `Initialize containers` / `Prepare Flyway runtime classpath` / `Run empty database Flyway smoke` all completed / success。
- Log evidence: PostgreSQL service was `postgres:16` / PostgreSQL 16.14；Flyway logged `Schema history table ... does not exist yet` and `Current version ... << Empty Schema >>` before migration。
- Log evidence: Flyway applied V1 through V31, logged `Successfully applied 31 migrations ... now at version v31`, then `Successfully validated 31 migrations`。
- Log evidence: `flyway_schema_history` printed `installed_rank|version|description|type|script|checksum|success` with rows `1|1|...|true` through `31|31|schema credential permission probe|SQL|V31__schema_credential_permission_probe.sql|...|true`。
- Log evidence: smoke runner printed `Flyway empty database smoke reached V31`。
- Run artifacts at 2A first green: none；Batch 2B schema artifacts are now implemented and first green confirmed by GitHub Actions run `27521750442`。

Freeze review evidence:

- Batch 2A implementation completed and first green run evidence reviewed。
- GitHub Actions run `27501253175` completed / success；`postgres-flyway` job completed / success。
- Empty DB migration smoke applied V1-V31 and validated 31 migrations。
- No `baselineOnMigrate` bypass；no Flyway `clean`。
- No legacy account seed、test fixture seed、real account seed or real exchange seed。
- No `nq-app` full context、no `AuthSeedConfiguration`、no repository real DB smoke、no frontend E2E、no Testcontainers。
- No real exchange credentials、no LIVE、no OKX / Binance / Bybit / Gate / Coinbase / Kraken access。

### Batch 2B: Flyway info / schema artifact / docs update

- Status: FROZEN / ACCEPTED。
- Planning document: `docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`。
- Planned artifacts: `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`，optional schema-only `schema-dump.sql`。
- Generation source: existing `postgres-flyway` empty DB after Flyway migrate + validate；use `information_schema` / `pg_catalog` / `pg_indexes` and `flyway_schema_history` metadata。
- Retention: 7 days for PR / branch review, 14 days for `dev` push by default。
- Redaction: artifact must not contain `.env`、API key、secret、passphrase、token、cookie、private key、credential material、raw request / response or data rows。
- `DB_SCHEMA.md` drift review starts as manual checklist；scripted blocking checker is deferred unless a separate 2B-2 review accepts it。
- Freeze evidence: run `27521750442` latest attempt jobs all completed / success；`postgres-flyway` job `81340926116` success；artifact `7628309014` uploaded with digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5` and 14-day `dev` retention；downloaded ZIP contained exactly the 7 required non-empty files with no missing / extra file；in-memory review found Flyway V1-V31, no `schema-dump.sql` data-row marker, and no high-risk credential / raw request / raw response pattern。
- Does not run repository real DB smoke, does not start `nq-app` context, does not modify CI-only seed watcher, does not use Testcontainers。

### Batch 2C: repository real Postgres smoke

- Status: FROZEN / ACCEPTED。
- Planning document: `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`。
- Added minimal repository-only smoke only for PostgreSQL-specific behavior。
- Prefer `nq-infra` pure JDBC repository smoke and avoid `nq-app` full context。
- Keep `@SpringBootTest` / `@ActiveProfiles("local")` tests in Batch 2D, not 2C。
- Avoid `AuthSeedConfiguration` and Batch 1 CI-only seed watcher。
- Default to no legacy account seed；if a fixture is unavoidable, use CI-only fake fixture rows inside rollback-safe transactions。
- Do not convert every `RecordingJdbcTemplate` test into real DB integration test。
- Implemented 2C-1 repositories: audit log, risk event, and marketdata bars, because they prove insert / JSON / `ON CONFLICT` / timestamp behavior without app context or exchange adapters。
- Defer credential repository real DB smoke to 2C-2+ unless fake material, log redaction and cleanup boundaries are separately reviewed。

### Batch 2D: nq-app context smoke

- Status: IMPLEMENTED / FIRST-RUN-FIX #2 CI FAILED / FIRST-RUN-FIX REQUIRED。
- Planning and implementation document: `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`。
- Added `NqAppContextPostgresSmokeTest` under `backend/nq-app/src/test/**`。
- Added a `postgres-flyway` job step after Flyway migrate / schema artifacts / Batch 2C repository smoke: `Run nq-app PostgreSQL context smoke`。
- Uses `@SpringBootTest(webEnvironment = MOCK)` with `ci-app-smoke` profile and explicit CI datasource system properties; it does not use `local` and does not reuse the current `test` profile as-is。
- Uses the same disposable CI PostgreSQL service DB after Flyway migration and sets `spring.flyway.enabled=false` in the context smoke to avoid duplicate migration ownership。
- Keeps `AuthSeedConfiguration` out of scope by avoiding `local` / `test` profiles。
- Keeps bootstrap admin, catalog sync, OKX recovery, OKX WS, Binance WS, and scheduler side effects disabled by explicit properties。
- Replaces OKX / Binance WS client beans with `@MockitoBean` and the REST adapter beans with pre-stubbed Mockito mocks (distinct CI-only fake `venue()`), so real constructors do not read `.env`, the production `AdapterBackedTradingVenueGateway` can build its routing map at refresh time, and adapter order methods are not invoked。
- Does not insert legacy seed, auth seed users, real accounts, exchange accounts, credential rows, or real credential material。
- Does not call controller workflows, run-once endpoints, adapter methods, scheduler jobs, frontend E2E, or any external exchange host。
- Do not make 2D required until first green + freeze review confirms P0/P1=0 and no seed watcher dependency。
- First run evidence: GitHub Actions run `27590822405`, commit `521e100b58ec2ee2b06463bf7558ff65a9630cf4`, completed / failure. `postgres-flyway` job failed only at step `Run nq-app PostgreSQL context smoke`; earlier Flyway V1-V31, schema artifact generation / check / upload, and Batch 2C repository PostgreSQL smoke steps succeeded. `NqAppContextPostgresSmokeTest` ran with tests=1 / skipped=0 / failures=0 / errors=1; root cause was `AdapterBackedTradingVenueGateway` failing with `venue must not be blank`. First-run fix (2026-06-16, test-only): pre-stubbed CI-only fake-venue mocks for the OKX / Binance REST adapters so the gateway builds its routing map at refresh; validated locally with BUILD SUCCESS / skipped=1. Second run `27592872701`, commit `7156b32c`, completed / failure: the venue error was gone but the context failed creating `securityFilterChain` because `webEnvironment = NONE` made the app non-web and `HttpSecurity` (`@ConditionalOnWebApplication(type = SERVLET)`) was absent. Second fix (2026-06-16, test-only): switch the smoke to `WebEnvironment.MOCK` (full servlet web context, no server), matching the existing `local`-profile full-context tests; validated locally with BUILD SUCCESS / skipped=1. Third run `27596768301`, commit `5b6ec1aa`, completed / failure: Flyway V1-V31, schema artifacts, and repository smoke still succeeded; `NqAppContextPostgresSmokeTest` ran under `ci-app-smoke` with tests=1 / skipped=0 / failures=0 / errors=1, context startup succeeded, then the test body failed with `NotAMockException` because adapter verification targeted a real `OkxExchangeAdapter`. Next action is `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` only.

### Batch 2E: CI-only seed watcher cleanup

- Replace ad hoc watcher with explicit test fixture seed or remove if no longer needed。
- Keep any seed out of production migration and runtime startup。
- Document removal condition before deleting.

## P0/P1/P2/P3 findings

| Priority | Finding | Decision |
| --- | --- | --- |
| P0 | CI must not enable LIVE, call real exchange hosts, output secrets, or modify history migrations. | Blocking boundary for any Batch 2 implementation. |
| P0 | Real credentials / production DB credentials must not be injected. | Default CI secrets required: none. |
| P1 | Current seed watcher can hide empty DB schema issues if treated as Flyway validation. | Batch 2A must run migration smoke before seed. |
| P1 | Migration repeatability / checksum drift is not yet independently captured. | Add Flyway history artifact and block checksum drift after accepted. |
| P1 | GitHub `postgres:16` and local compose `postgres:17.7` differ. | Choose one intentionally for CI and record compatibility risk. |
| P2 | Full context or repository expansion can increase CI time. | Split 2A-2E; keep 2A small. |
| P2 | Schema artifacts may be incomplete at first. | Start with Flyway history + table/column/constraint/comment metadata; add dump later. |
| P2 | `DB_SCHEMA.md` drift remains manual until a checker exists. | Use 2B review checklist first; future script can block. |
| P3 | Job naming / report readability can drift. | Use stable job name `postgres-flyway` and concise artifacts. |
| P3 | Seed wording can be misunderstood as runtime seed. | Always write CI-only test seed, never production runtime seed. |

## Validation

Batch 2A first-run review validation executed locally:

- `git status --short`
- `git diff --check`
- `git diff --stat`
- `git show --stat --oneline --name-only HEAD`
- `git diff -- backend`
- `git diff -- frontend`
- `git diff -- research`
- `git diff -- scripts`
- `git diff -- deploy`
- `git diff -- .github`
- `git diff -- backend/**/db/migration`
- `rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current`
- GitHub Actions run details / jobs / logs reviewed for run `27501253175` and job `81284424653`。
- Workflow boundary review: `postgres-flyway` uses `postgres:16` with `nq_ci` / `nq_ci_user` / `nq_ci_password` only; no real exchange credentials, no LIVE flag, no Testcontainers, no app context, no seed watcher.

Notes:

- `gh run view --log` 因 GitHub 权限返回 `HTTP 403: Must have admin rights to Repository`；改用 GitHub MCP 读取同一 job 的 decoded logs 和 steps。
- 本轮未运行 backend full Maven test、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮为 first-run review + docs/current 记录，不修改 Java / TypeScript / Python / test / migration / workflow。

## Boundary confirmation

- Batch 2A FROZEN / ACCEPTED。
- Batch 2B FROZEN / ACCEPTED。
- Batch 2C FROZEN / ACCEPTED。
- 已修改 `.github/workflows/ci.yml`，仅新增 `postgres-flyway` job。
- 未修改 backend / frontend / research / scripts / deploy。
- 未新增 API，未新增 migration，未修改历史 migration。
- 未修改 Java / TypeScript / Python / test code。
- 未启动 `nq-app` full context，未插入 legacy account seed / test fixture seed / real account seed / real exchange seed。
- 未启用 Testcontainers，未使用 `baselineOnMigrate`，未运行 Flyway `clean`。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 credential material。

## Review decision

PASS / FROZEN / ACCEPTED for Batch 2A；PASS / FROZEN / ACCEPTED for Batch 2B；PASS / FROZEN / ACCEPTED for Batch 2C；FIRST-RUN-FIX #2 CI FAILED / FIRST-RUN-FIX REQUIRED for Batch 2D。

Batch 2A 已冻结为当前 `dev` 的 PostgreSQL / Flyway empty DB migration smoke baseline。Batch 2B 已冻结为当前 `dev` 的 PostgreSQL / Flyway schema artifact minimal baseline。Batch 2C repository-only real PostgreSQL smoke 已由 GitHub Actions run `27535619157` first green confirmed，并经 freeze review 接受为当前 `dev` repository real DB 最小验证基线。2C-HYGIENE-FIX 已由 GitHub Actions run `27550583713` first green confirmed，并经 freeze review 固化为当前 Batch 2C CI log hygiene baseline。Batch 2D 当前只实现最小 `nq-app` context smoke；run `27596768301` 在 FIRST-RUN-FIX #2 后仍失败（`NotAMockException`），因此仍不能写成 FIRST GREEN RUN CONFIRMED、FROZEN 或 ACCEPTED。禁止 `local` profile、current `test` profile as-is、隐式 seed、真实交易所、LIVE、AI、DH runtime、RealClient、real provider 和真实 credential material。不得把本轮写成 Batch 2E started、Batch 3 no-outbound、Batch 4 security scan、Batch 5 frontend E2E、AI、DH runtime、LIVE、real provider 或真实 permission probe adapter 已实现。

## Next concrete action

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` only；修复后再 re-run `NQ CI Baseline` on `dev` 并重新执行 first-run review。
