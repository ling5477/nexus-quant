# NQ CI PostgreSQL / Flyway Plan

任务：NQ-CI-POSTGRES-FLYWAY-PLAN
日期：2026-06-14
状态：PLANNING-ONLY；Batch 2 plan documented；implementation NOT STARTED

## Current state

- Project: NexusQuant / NQ。
- Current branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted。
- NQ CI Batch 1 已实现并通过 GitHub Actions run `27496906788` first green review。
- Batch 1 当前 jobs：`diff-check`、`backend`、`frontend`、`research`。
- Batch 1 backend job 已临时使用 GitHub Actions `postgres:16` service 和 CI-only seed watcher 支撑 `mvn -f backend/pom.xml test`，但这只是 runner dependency workaround，不是 Batch 2 PostgreSQL / Flyway hardening。
- Batch 2 PostgreSQL / Flyway：PENDING；本文件只规划，不修改 `.github/workflows/ci.yml`。
- Batch 3 no-outbound guard：PENDING。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI: NOT STARTED。
- DH runtime: NOT INTEGRATED / not connected to NQ。
- LIVE: DISABLED。
- real exchange permission probe adapter: NOT IMPLEMENTED。

## Scope

Allowed in this planning task:

- 只读检查 `.github/workflows/ci.yml`、backend Maven poms、Flyway migrations、application profiles、`docker-compose.yml`、backend test tree 和 current docs。
- 新增本文件，并同步 `NQ_CI_BASELINE_PLAN.md`、`README.md`、`TESTING.md`、`WORKLOG.md` 的 Batch 2 planning 状态。

Forbidden in this planning task:

- 不修改 `.github/workflows/ci.yml`，不新增 workflow。
- 不修改 Java / TypeScript / Python / test code。
- 不新增 API，不新增 migration，不修改历史 migration。
- 不修改 backend 生产逻辑、frontend、research、scripts、deploy。
- 不开启 LIVE，不接 AI，不接 DH runtime，不实现 NQ RealClient / real provider / real exchange permission probe adapter。
- 不调用 OKX / Binance / Bybit / Gate / Coinbase / Kraken，不读取或输出真实 credential material。
- 不把 Batch 2 写成 implemented。

## Current Batch 1 CI state

当前 `.github/workflows/ci.yml` 的 jobs：

| Job | Current behavior | Batch 2 relevance |
| --- | --- | --- |
| `diff-check` | 对 PR / push / manual run 执行 changed-file whitespace check。 | 保留为基础 hygiene gate。 |
| `backend` | Java 21 + Maven cache + `mvn -f backend/pom.xml test`；当前已配置 `postgres:16` service、`NQ_DB_URL`、`NQ_DB_USER`、`NQ_DB_PASSWORD`，并用 CI-only seed watcher 插入最小 `accounts` legacy row。 | 说明 full Maven test 已依赖 PostgreSQL service；Batch 2 应拆出独立 `postgres-flyway` job，避免 seed workaround 掩盖 schema 问题。 |
| `frontend` | Node 22 + npm cache + `npm ci` + `npm run build`。 | 不属于 Batch 2；frontend E2E hardening 仍为 Batch 5。 |
| `research` | Python 3.11 + pip cache + `pytest` / `mypy --no-sqlite-cache` / `ruff --no-cache`。 | 不属于 Batch 2。 |

当前 backend job 已使用 PostgreSQL，但目的仍是让 Batch 1 full Maven test 在 fresh GitHub runner 上可运行，不提供以下 Batch 2 能力：

- 无独立 Flyway `info` / schema artifact。
- 无显式 empty DB migration smoke job。
- 无 migration checksum drift artifact。
- 无 schema dump artifact。
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

- 使用一个 fresh PostgreSQL database，例如 `nexus_quant_flyway_ci`。
- 设置 `NQ_PROFILE=local` 或后续专用 CI profile，并显式注入测试 datasource。
- 启动最小 Spring Boot context 或执行稳定的 Flyway migrate path，让 `classpath:db/migration` 应用到 empty DB。
- 迁移完成后查询 `flyway_schema_history` 并保存 artifact。
- 再执行一次 validate/info；若 toolchain 暂无独立 Maven Flyway plugin，可先以 Spring/Flyway startup + `flyway_schema_history` query 作为 2A，2B 再补显式 info artifact。

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

- Flyway empty DB migration smoke 必须先无 seed 运行，证明 V1-V31 可建库。
- 需要 app context / repository smoke 时，再执行 test fixture seed。
- Seed 不得包含真实账户、真实交易所账户、真实 credential、API key、secret、passphrase、token、cookie 或 LIVE 标记。
- Seed 必须使用 `PAPER` / `ACTIVE` / fake account code，只服务测试。
- 当 repository / app context 测试改为显式 fixture seed 或测试代码自建数据后，应移除或收口 CI-only seed watcher。

## nq-app context / repository DB test plan

Batch 2 不应一次性变成 full integration test 改造。建议分层：

- Batch 2A：只跑 Flyway empty DB migration smoke；不 seed；不启动全量 E2E。
- Batch 2B：保存 Flyway info / schema artifact，并同步 docs。
- Batch 2C：若需要，新增或启用最小 repository real PostgreSQL smoke；优先覆盖 PostgreSQL-specific 行为，例如 JSONB cast、CHECK constraint、unique index、TIMESTAMPTZ、row lock / transaction semantics。
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

## Proposed CI job design

Suggested job name:

- `postgres-flyway`（UI display name: `PostgreSQL / Flyway smoke`）。

Working directory:

- repo root；Maven command 使用 `-f backend/pom.xml`。

Services draft:

```yaml
services:
  postgres:
    image: postgres:16
    env:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: "123456"
      POSTGRES_DB: nexus_quant_flyway_ci
    ports:
      - 5432:5432
    options: >-
      --health-cmd "pg_isready -U postgres -d nexus_quant_flyway_ci"
      --health-interval 10s
      --health-timeout 5s
      --health-retries 10
```

Environment draft:

```yaml
env:
  CI: "true"
  NQ_PROFILE: local
  NQ_DB_URL: jdbc:postgresql://localhost:5432/nexus_quant_flyway_ci
  NQ_DB_USER: postgres
  NQ_DB_PASSWORD: "123456"
  NQ_OKX_WS_ENABLED: "false"
  NQ_BINANCE_WS_ENABLED: "false"
  NQ_OKX_RECOVERY_ENABLED: "false"
  NQ_GATED_VERIFY_ENABLED: "false"
```

Java and cache:

- Java 21 via `actions/setup-java@v4`。
- Maven cache enabled, keyed by backend poms。

Command options:

1. Batch 2A minimum:

```bash
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=OkxBootstrapNoOutboundLocalContextTest
```

This starts local Spring context and applies Flyway, but it still runs a context test and may require fixture discipline.

2. Preferred cleaner 2A if implemented with no code changes:

```bash
mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local
```

Then wait for successful startup / Flyway completion and terminate after artifact collection. This needs careful process handling and timeout.

3. Future 2B hardening:

```bash
psql "$CI_DATABASE_URL" -c "SELECT installed_rank, version, description, type, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank;"
pg_dump --schema-only --no-owner --no-privileges "$CI_DATABASE_URL" > schema-dump.sql
```

Timeout:

- `timeout-minutes: 15` for Batch 2A。
- Raise to `20` only if artifact collection and context startup are consistently close to the limit。

Artifacts:

- Upload `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-comments.txt`。
- Optional schema dump must be schema-only and must not contain data.
- Retention: 7 days for PR, 14-30 days for `dev` push if useful.

Merge blocking:

- Batch 2A may become required after first green review.
- Before first green review, run it manually or on PR as non-required observation, but never report skipped / failed as passed.
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

- Add separate `postgres-flyway` job。
- Use GitHub Actions PostgreSQL service container。
- Run empty DB V1-V31 migration smoke。
- No seed before migration smoke。
- No Testcontainers。
- No repository test expansion。

### Batch 2B: Flyway info / schema artifact / docs update

- Save `flyway_schema_history` and schema metadata artifacts。
- Record maximum version and checksum evidence。
- Update `NQ_CI_BASELINE_PLAN.md`、`TESTING.md`、`WORKLOG.md` after first green review。

### Batch 2C: repository real Postgres smoke, if needed

- Add minimal repository smoke only for PostgreSQL-specific behavior。
- Prefer test fixtures with explicit setup/cleanup。
- Do not convert every RecordingJdbcTemplate test into real DB integration test。

### Batch 2D: nq-app context smoke, if needed

- Start minimal `nq-app` Spring context against CI PostgreSQL。
- Keep LIVE disabled and real provider side effects disabled。
- Do not run frontend E2E here。

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

Planning-only validation executed for this document:

- `git status --short`
- `git diff --check`
- `git diff --stat`
- `git ls-files .github`
- `git ls-files "backend/**/db/migration/**"`
- `git ls-files "backend/**/src/test/**"`
- `git ls-files "backend/**/application*.yml" "backend/**/application*.yaml" "backend/**/application*.properties"`
- `git diff -- backend`
- `git diff -- frontend`
- `git diff -- research`
- `git diff -- scripts`
- `git diff -- deploy`
- `git diff -- .github`
- `git diff -- backend/**/db/migration`
- `rg "flyway|Flyway|postgres|PostgreSQL|Testcontainers|jdbc:postgresql|baselineOnMigrate|spring.datasource|accounts|seed|migration" backend docs/current docker-compose.yml .github README.md`
- `rg "LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github docs/current backend`

Notes:

- 用户指定的 broad `rg` 命令会命中 `backend/target` 生成报告；后续证据提取已使用 `--glob '!**/target/**'` 排除生成产物，避免把 surefire output 当作 source fact。
- 本轮未运行 Maven / frontend / Python tests；原因是只改 `docs/current` 文档，未修改 code / test / workflow / migration。

## Boundary confirmation

- Batch 2 仍是 PLANNING-ONLY；implementation NOT STARTED。
- 未修改 `.github/workflows/ci.yml`。
- 未修改 backend / frontend / research / scripts / deploy。
- 未新增 API，未新增 migration，未修改历史 migration。
- 未修改 Java / TypeScript / Python / test code。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 credential material。

## Review decision

CONDITIONALLY ACCEPTED FOR PLANNING。

Batch 2 可进入 `NQ-CI-POSTGRES-FLYWAY-PLAN-REVIEW`；若 review 接受，下一步只能进入 Batch 2A implementation：PostgreSQL service + Flyway empty DB migration smoke。不得混入 Batch 3 no-outbound、Batch 4 security scan、Batch 5 frontend E2E、AI、DH runtime、LIVE、real provider 或真实 permission probe adapter。

## Next concrete action

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-PLAN-REVIEW`，或在 review 接受后进入 `NQ-CI-POSTGRES-FLYWAY-2A-IMPL`。
