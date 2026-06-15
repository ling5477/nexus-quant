# Testing

本文记录统一验证命令和当前基线验证结果。未执行的验证不能写成通过。

## NQ-CI-POSTGRES-FLYWAY-2C-PLAN 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C planning-only：只规划 repository real PostgreSQL smoke，不修改 workflow，不改 Java / TypeScript / Python 代码，不改测试代码，不新增 API，不新增 migration，不修改历史 migration，不改 backend 生产逻辑、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 预检 | 通过 | `Get-Location` 为 `F:\project\nexus-quant`；`git branch --show-current` 为 `dev`；编辑前 `git status --short` 为空。 |
| Current docs review | 通过 | 已复核 `AGENTS.md`、`README.md`、`docs/current/README.md`、`NQ_CI_POSTGRES_FLYWAY_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`。 |
| Workflow read-only review | 通过 | 只读复核 `.github/workflows/ci.yml`；当前有 `backend` PostgreSQL service + CI-only seed watcher，以及 `postgres-flyway` 2A/2B job；本轮未修改 workflow。 |
| Maven / config review | 通过 | 已复核 `backend/pom.xml`、`backend/nq-app/pom.xml`、`backend/nq-infra/pom.xml`、`application.yml`、`application-local.yml`、`application-test.yml`。 |
| Repository test inventory | 通过 | `nq-infra` repository 测试主要使用 `RecordingJdbcTemplate` / `Mockito.mock(JdbcTemplate)`；未发现现成 Testcontainers / real PostgreSQL repository test baseline。 |
| Spring context boundary | 通过 | `nq-app` 中 `MarketdataControllerLocalIntegrationTest`、`ResearchBacktestHappyPathLocalTest`、`OkxBootstrapNoOutboundLocalContextTest` 使用 `@SpringBootTest` + `local` profile，划入 2D，不纳入 2C。 |
| Seed boundary | 通过 | 2C plan 默认不使用 legacy account seed、不复用 Batch 1 seed watcher、不触发 `AuthSeedConfiguration`；如 future fixture 必需，只允许 CI-only fake fixture 并 rollback / cleanup。 |
| Security boundary | 通过 | 2C plan 禁止 OKX / Binance / Bybit / Gate / Coinbase / Kraken 外联，禁止 LIVE、AI、DH runtime、RealClient、real provider、真实 credential。 |
| Forbidden-area diff | 通过 | `git diff -- .github`、`backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| Docs-only diff | 通过 | `git status --short` 仅显示允许的 `docs/current` 修改和新增文件；`git diff --stat` 覆盖 tracked docs diff。 |
| Whitespace check | 通过 | `git diff --check` 通过；另用 `rg "[ \t]+$"` 检查本轮新增 / 修改 docs，无 trailing whitespace 命中。 |

本轮执行 / 复核命令：

```powershell
Get-Location
git status --short
git branch --show-current
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
rg "[ \t]+$" docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md docs/current/NQ_CI_BASELINE_PLAN.md docs/current/README.md docs/current/TESTING.md docs/current/WORKLOG.md
```

Source-only follow-up scans also used `--glob '!**/target/**'` to avoid build output noise. PowerShell direct path globs such as `backend/**/src/test` were not used for final evidence because Windows treats them as invalid path arguments; equivalent `rg --glob` filters were used.

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮只新增 / 同步 `docs/current` planning 文档，未修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy。

Review decision: PLAN READY FOR REVIEW。Batch 2C remains NOT IMPLEMENTED；Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-REVIEW` or `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2B freeze review：冻结 PostgreSQL / Flyway schema artifact baseline，确认它成为当前 `dev` CI 的 schema artifact 最小验证基线。本轮只同步允许的 `docs/current` 文档；未修改 `.github/workflows/ci.yml`，未修改 Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 预检 | 通过 | `Get-Location` 为 `F:\project\nexus-quant`；`git branch --show-current` 为 `dev`；编辑前 `git status --short` 为空。 |
| GitHub Actions run | 通过 | GitHub 插件复核 run `27521750442` latest attempt jobs 全部 completed / success；artifact metadata 绑定 branch `dev` 与 commit `c62ebddd5a522bbdf72bc018064b9eb36d8fe9e1`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81340926116` completed / success。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；job log 显示 `Flyway empty database smoke reached V31`。 |
| Schema artifact generation | 通过 | Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` 均 success。 |
| Artifact metadata | 通过 | Artifact `nq-postgres-flyway-schema-artifacts` / id `7628309014`，size `74662` bytes，digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5`，`expired=false`，`expires_at=2026-06-29T03:14:04Z`，符合 `dev` push 14-day retention。 |
| Artifact file list | 通过 | In-memory ZIP review confirmed exactly 7 required files: `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`；无 missing / extra / empty file。 |
| Flyway V1-V31 artifact | 通过 | `flyway-info.txt` 有 31 条非空 migration rows，首版本 `1`，末版本 `31`。 |
| `schema-dump.sql` schema-only check | 通过 | In-memory review 对 `INSERT INTO`、`COPY ... FROM stdin`、`-- Data for Name:`、dump data terminator pattern 的命中数为 0。 |
| Artifact redaction | 通过 | In-memory review 对 `.env`、API key、secret、passphrase、token、cookie、private key、mnemonic、credential material、raw request / raw response high-risk pattern 的命中数为 0。 |
| Workflow boundary | 通过 | `rg` 复核 `.github/workflows/ci.yml`：artifact 使用 metadata 查询与 `pg_dump --schema-only --no-owner --no-privileges`；未发现 `printenv` / bare `env` / `continue-on-error`。 |
| Forbidden-area diff | 通过 | `git diff -- .github`、`backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| Stage wording scan | 通过 | `rg` 复核 Batch 2B / 2C / 2D / 2E / Batch 3-5 文档口径；2B 冻结后，2C/2D/2E 仍 NOT STARTED，Batch 3-5 仍 PENDING。 |

本轮执行 / 复核命令：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "Batch 2B|FIRST GREEN|FROZEN|ACCEPTED|Batch 2C|Batch 2D|Batch 2E|no-outbound|security scan|frontend E2E" docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md docs/current/NQ_CI_BASELINE_PLAN.md docs/current/TESTING.md docs/current/WORKLOG.md
rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
```

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是 freeze review 只冻结已成功的 GitHub Actions run / artifact 证据并同步文档，未修改 workflow、Java / TypeScript / Python / test / migration / frontend / research / scripts / deploy。

Review decision: PASS / FROZEN / ACCEPTED。Batch 2B 已冻结为当前 `dev` 的 PostgreSQL / Flyway schema artifact minimal baseline。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2C-PLAN`、后续发现回归时的 `NQ-CI-POSTGRES-FLYWAY-2B-FIX`，或 Batch 3 前置 planning；不得直接进入真实交易所、LIVE、AI 或 DH runtime。

## NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2B first-run review：只评审 GitHub Actions run `27521750442` 的 schema / Flyway artifact 生成、上传、retention 和 redaction 结果，并同步允许的 `docs/current` 文档。未修改 `.github/workflows/ci.yml`，未修改 Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | Run `27521750442`，workflow `NQ CI Baseline`，branch `dev`，commit `c62ebddd5a522bbdf72bc018064b9eb36d8fe9e1`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81340926116` completed / success。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；job log 显示 `Flyway empty database smoke reached V31`。 |
| Artifact generation | 通过 | Step `Generate PostgreSQL schema artifacts` success。 |
| Artifact check | 通过 | Step `Check PostgreSQL schema artifacts` success；blocking check 未发现 data rows 或 high-risk credential pattern。 |
| Artifact upload | 通过 | Step `Upload PostgreSQL schema artifacts` success；log 显示 7 files uploaded。 |
| Artifact metadata | 通过 | Artifact `nq-postgres-flyway-schema-artifacts` / id `7628309014`，size `74662` bytes，digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5`，`expires_at=2026-06-29T03:14:04Z`，符合 `dev` push 14-day retention。 |
| Artifact download check | 通过 | 下载 ZIP 后确认仅包含 `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`，无 missing / extra / empty file。 |
| `schema-dump.sql` data rows | 通过 | 本地检查 `INSERT` / `COPY ... FROM stdin` / data dump marker 命中数为 0。 |
| Artifact redaction | 通过 | 本地检查 `.env`、API key、secret、passphrase、token、cookie、private key、mnemonic、credential material、raw request / response pattern 命中数为 0。 |
| Boundary scan | 通过 | `postgres-flyway` 未启动 `nq-app` context，未跑 repository real DB smoke，未插入 seed，未启用 Testcontainers，未实现 no-outbound guard / secret scan / frontend E2E hardening。 |

本轮执行 / 复核命令：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
```

Boundary confirmation:

- Batch 2B first green run confirmed；尚未 freeze / accepted。
- Batch 2C repository real PostgreSQL smoke：NOT STARTED。
- Batch 2D `nq-app` context smoke：NOT STARTED。
- Batch 2E CI-only seed watcher cleanup：NOT STARTED。
- Batch 3 no-outbound guard：PENDING。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI：NOT STARTED；DH runtime：NOT INTEGRATED；LIVE：DISABLED；real exchange adapter：NOT IMPLEMENTED。

Review decision: PASS / ACCEPTED FOR FIRST GREEN RUN。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2C-PLAN`。

## 统一验证命令

### 后端验证

```powershell
mvn -f backend/pom.xml test
```

### 前端验证

```powershell
Set-Location frontend
npm ci
npm run build
npm run test:e2e
```

### Python 验证

首次本地验证前安装 dev 依赖：

```powershell
Set-Location research/py
python -m pip install -e ".[dev]"
```

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

### 本地启动验证

```powershell
docker compose up -d postgres
```

启动 `nq-app` local profile 后检查：

```powershell
Invoke-RestMethod http://localhost:18888/actuator/health
```

并检查：

- `POST /api/auth/login`
- `GET /api/auth/me`

## 本地 PostgreSQL 规则

- 本地 PostgreSQL 默认端口是 `5432`。
- 使用本机 PostgreSQL 时，不重复启动 `docker-compose postgres`。
- 使用 `docker-compose postgres` 时，确认本机 `5432` 未被占用。

## 本次实际验证记录

## NQ-CI-POSTGRES-FLYWAY-2B-IMPL 验证记录（2026-06-15）

本轮是 GateK CI Batch 2B implementation：只在既有 `.github/workflows/ci.yml` 的 `postgres-flyway` job 中增加 schema artifact generation / upload，并同步允许的 `docs/current` 文档。未修改 Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

本轮本地未运行 GitHub Actions PostgreSQL service container，也未触发 `actions/upload-artifact`；`postgres-flyway` artifact first CI run 仍 pending，必须由后续 `NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW` 复核。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区变更仅限 `.github/workflows/ci.yml` 与允许的 `docs/current` 文件。 |
| `git diff --check` | 已执行 | 用于检查 whitespace error。 |
| `git diff --stat` | 已检查 | 用于确认变更范围。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| forbidden keyword scan | 已执行 | `rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current`；workflow 不允许新增真实交易所 / LIVE / skip / soft-fail 行为，docs 命中只能是禁止说明、历史记录或边界说明。 |
| artifact command boundary | 已检查 | Workflow 使用 libpq connection string 调用 `psql`，未把 JDBC URL 传给 `psql`；未使用 `env` / `printenv` 输出 full environment。 |
| schema-only dump boundary | 已检查 | `pg_dump` 命令包含 `--schema-only --no-owner --no-privileges`。 |
| data row boundary | 已检查 | Artifact 查询来源限定为 `flyway_schema_history`、`information_schema`、`pg_constraint` / `pg_class` / `pg_namespace`、`pg_indexes`、`obj_description` / `col_description`；未查询业务表 row values。 |
| redaction boundary | 已检查 | 新增 artifact check 会阻塞 high-risk credential material pattern，并检查 `schema-dump.sql` 不含 `INSERT` / `COPY ... FROM stdin` / data dump marker。 |

安全边界：

- 未启动 `nq-app` context，未触发 `AuthSeedConfiguration`。
- 未跑 repository real PostgreSQL smoke，未插入 seed，未启用 Testcontainers。
- 未实现 no-outbound guard、gitleaks / secret scan 或 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider 或真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken adapter。
- 未读取、打印、复制或输出真实 credential material。

Review decision: IMPLEMENTED / PENDING FIRST CI RUN。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW`；如果 first run 失败，则只能做 `NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2B-PLAN 验证记录（2026-06-14）

本轮是 GateK CI Batch 2B planning-only：只新增 / 同步 `docs/current` 文档，规划 Flyway / schema artifact、retention、redaction 和 `DB_SCHEMA.md` drift review。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 编辑前工作区干净；本地 `dev` 在 2A freeze commit 后比 `origin/dev` ahead 1。 |
| `.github/workflows/ci.yml` 只读复核 | 已执行 | 当前仅有 Batch 2A `postgres-flyway` job；本轮未修改 workflow。 |
| `DB_SCHEMA.md` / migration 只读复核 | 已执行 | 当前最大 migration 为 `V31__schema_credential_permission_probe.sql`；2B 只规划 artifact / drift review，不新增或修改 migration。 |
| Batch 2B 状态检查 | 通过 | `NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md` 明确 `PLAN ONLY / NOT IMPLEMENTED`。 |
| Batch boundary | 通过 | Batch 2C/2D/2E 仍 NOT STARTED；Batch 3 no-outbound、Batch 4 security guard / secret scan、Batch 5 frontend E2E hardening 仍 PENDING。 |
| Security boundary | 通过 | Artifact plan 明确不保存 `.env`、API key、secret、passphrase、token、cookie、private key、credential material、raw request / response 或 data rows；LIVE DISABLED，AI NOT STARTED，DH runtime NOT INTEGRATED。 |

Review decision: PLAN READY FOR REVIEW。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN-REVIEW`，或评审接受后的 `NQ-CI-POSTGRES-FLYWAY-2B-IMPL`；不得混入 2C/2D/2E、Batch 3-5、LIVE、AI、DH runtime 或真实交易所路径。

## NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW 验证记录（2026-06-14）

本轮是 GateK docs-only / CI freeze review：只冻结 Batch 2A PostgreSQL / Flyway empty DB migration smoke baseline，不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git fetch origin` / `git pull --ff-only origin dev` | 通过 | 本地 `dev` 已同步到 `origin/dev`，包含前端 PR #1 与 PR #2 合并后的文档事实源。 |
| First-run review commit | 通过 | 已提交 `docs(gatek): confirm PostgreSQL Flyway CI first green run`，只包含允许的 5 个 `docs/current` 文件。 |
| GitHub Actions run `27501253175` | 通过 | `NQ CI Baseline` completed / success；`postgres-flyway` job completed / success。 |
| Flyway V1-V31 review | 通过 | 日志证据显示 empty DB 从 V1 迁移到 V31，并 `Successfully validated 31 migrations`。 |
| No baseline / clean boundary | 通过 | Workflow 使用 `baselineOnMigrate(false)`、`cleanDisabled(true)`；未发现 `cleanDisabled(false)`。 |
| Seed / context boundary | 通过 | 未插入 legacy account seed / test fixture seed / real account seed / real exchange seed；未启动 `nq-app` full context，未触发 `AuthSeedConfiguration`。 |
| Expansion boundary | 通过 | 未跑 repository real DB smoke、frontend E2E 或 Testcontainers；Batch 2B/2C/2D/2E 仍 NOT STARTED，Batch 3-5 仍 PENDING。 |
| Security boundary | 通过 | 未注入真实交易所 credential，未开启 LIVE，未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken；AI NOT STARTED，DH runtime NOT INTEGRATED。 |

Review decision: PASS / FROZEN / ACCEPTED。Batch 2A 已冻结为当前 `dev` 的 PostgreSQL / Flyway empty DB migration smoke baseline。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN`。

## NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW 验证记录（2026-06-14）

本轮是 GateK CI Batch 2A first-run review：只复核 GitHub Actions `postgres-flyway` 首次运行结果，并同步允许的 `docs/current` 文档。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | `NQ CI Baseline` run `27501253175`，`push` to `dev`，commit `7836640ebae46d6fc62771611f5215661b3267dc`，completed / success。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / id `81284424653` completed / success；`Initialize containers`、`Prepare Flyway runtime classpath`、`Run empty database Flyway smoke` 均 success。 |
| Flyway empty DB smoke | 通过 | 日志显示 `Schema history table ... does not exist yet`、`Current version ... << Empty Schema >>`、V1-V31 逐版 migration、`Successfully applied 31 migrations ... now at version v31`。 |
| Flyway validate | 通过 | 日志显示 migration 前后均有 validate，最终 `Successfully validated 31 migrations`。 |
| `flyway_schema_history` | 通过 | 日志输出 `installed_rank|version|description|type|script|checksum|success`，覆盖 row 1/V1 到 row 31/V31，且 success 均为 `true`。 |
| Batch 2A smoke marker | 通过 | 日志输出 `Flyway empty database smoke reached V31`。 |
| No baseline / clean boundary | 通过 | Workflow 静态复核为 `baselineOnMigrate(false)`、`cleanDisabled(true)`；未发现 `cleanDisabled(false)`。 |
| No seed boundary | 通过 | `postgres-flyway` job 未插入 legacy account seed、test fixture seed、真实账户 seed 或真实交易所 seed；Batch 1 backend seed watcher 未进入该 job。 |
| No app / repository / E2E expansion | 通过 | `postgres-flyway` job 未启动 `nq-app` full context，未触发 `AuthSeedConfiguration`，未跑 repository real DB smoke，未跑 frontend E2E。 |
| No Testcontainers / skip / continue-on-error | 通过 | Workflow 未启用 Testcontainers，未使用 `continue-on-error`，未用 skip 伪装通过，未使用 `skipTests`。 |
| Security boundary | 通过 | Workflow 仅使用 CI-only PostgreSQL service env；未注入真实交易所 credential，未开启 LIVE，未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。 |
| `git status --short` | 通过 | first-run review 编辑前工作区干净；编辑后仅包含允许的 `docs/current` 文档。 |
| `git diff --check` | 通过 | 未发现 whitespace error。 |
| `git diff --stat` | 已检查 | first-run review 只同步 current docs。 |
| `git show --stat --oneline --name-only HEAD` | 已检查 | HEAD 为 `7836640e ci(gatek): add PostgreSQL Flyway migration smoke`，包含 `.github/workflows/ci.yml` 与允许的 current docs。 |
| forbidden-area diff | 通过 | `git diff -- backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| keyword scan | 已执行 | `rg "continue-on-error|skipTests|baselineOnMigrate|cleanDisabled\(false\)|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current` 已执行；workflow 仅命中 `baselineOnMigrate(false)`，docs 命中为历史记录、安全边界或禁止项说明。 |

Review decision: PASS / ACCEPTED。Batch 2A 可冻结为 PostgreSQL / Flyway empty DB migration smoke baseline。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN`。

## NQ-CI-POSTGRES-FLYWAY-2A-IMPL 验证记录（2026-06-14）

本轮是 GateK CI Batch 2A implementation：只修改 `.github/workflows/ci.yml` 新增 `postgres-flyway` job，并同步 current docs。Batch 2A 只覆盖 PostgreSQL service + Flyway empty DB V1-V31 migration smoke；未实现 Batch 2B schema artifact/docs、Batch 2C repository real PostgreSQL smoke、Batch 2D `nq-app` context smoke、Batch 2E seed watcher cleanup、Batch 3 no-outbound guard、Batch 4 security guard / secret scan、Batch 5 frontend E2E hardening。

本轮 implementation 当时未运行 GitHub Actions 本体，`postgres-flyway` first CI run 当时 pending；该 pending 状态已由 `NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW` 关闭。未运行 backend full Maven test、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮未修改 Java / TypeScript / Python / test / migration / backend production code。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 确认工作区变更仅限 `.github/workflows/ci.yml` 与允许的 `docs/current` 文件。 |
| `git diff --check` | 通过 | 未发现 whitespace error。 |
| `git diff --stat` | 已检查 | 变更集中在 CI workflow 与 current docs。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend Java / resources / tests。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| Workflow boundary review | 通过 | 新增 `postgres-flyway` job 使用 `postgres:16`、`nq_ci` / `nq_ci_user` / `nq_ci_password`、Java 21、Maven cache；通过临时 Java smoke runner 调用 Flyway `migrate` + `validate`，校验 current version 为 V31 并打印 `flyway_schema_history`。 |
| `mvn -f backend/pom.xml -pl nq-app -am process-classes org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-DincludeScope=runtime" "-Dmdep.outputFile=target/flyway-classpath.txt"` | 通过 | 23 个 reactor module `SUCCESS`，生成 `backend/nq-app/target/flyway-classpath.txt`；该命令只准备 classpath / resources，不启动 PostgreSQL、不运行 tests、不启动 app context。首次未加 PowerShell 引号的本地干跑失败为 shell 参数解析问题，workflow bash 命令不受影响。 |
| Seed boundary review | 通过 | `postgres-flyway` 不插入 legacy account seed、test fixture seed、real account seed 或 real exchange seed；不依赖 Batch 1 CI-only seed watcher。 |
| App context / repository boundary review | 通过 | `postgres-flyway` 不启动 `nq-app` full context，不运行 `@SpringBootTest`，不触发 `AuthSeedConfiguration`，不跑 repository real PostgreSQL smoke。 |
| Testcontainers / Flyway safety review | 通过 | 未启用 Testcontainers；未使用 `baselineOnMigrate`；未运行 Flyway `clean`；未设置 `continue-on-error`。 |
| Security keyword scan | 已执行 | `rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current` 已执行；命中项用于边界复核，workflow 未注入真实交易所 credential，未开启 LIVE，未加入 Batch 3/4/5。 |
| Workflow lint | 未执行 | 本机未安装 `actionlint`，Ruby 不可用，系统 Python 与 Codex bundled Python 均无 PyYAML，bundled Node 未发现 `yaml` / `js-yaml`；本轮未伪造 workflow lint 通过，语法仍以 GitHub Actions first run 为准。 |

边界确认：

- Batch 2A implemented；first CI run 当时 pending，已由 `NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW` 关闭。
- 未修改 Java / TypeScript / Python / test code。
- 未新增 API，未新增 migration，未修改历史 migration。
- 未修改 backend 生产逻辑、frontend、research、scripts、deploy。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。

## NQ-CI-POSTGRES-FLYWAY-PLAN 验证记录（2026-06-14）

本轮是 GateK CI Batch 2 planning-only / docs-only：只新增 `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md` 并同步 current docs 入口，不修改 `.github/workflows/ci.yml`，不修改 backend、frontend、research、scripts、deploy、测试代码、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；这些命令不适用于只写 Batch 2 方案的文档轮次。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 预检通过 | 编辑前工作树为空。 |
| `git diff --check` | 预检通过 | 编辑前无 whitespace error。 |
| `git diff --stat` | 预检已执行 | 编辑前无 tracked diff。 |
| `git ls-files .github` | 已检查 | 当前 tracked `.github` 包含 `CODEOWNERS`、`pull_request_template.md`、`workflows/ci.yml`。 |
| `git ls-files "backend/**/db/migration/**"` | 已检查 | 当前最大 migration 为 `V31__schema_credential_permission_probe.sql`。 |
| `git ls-files "backend/**/src/test/**"` | 已检查 | 确认 backend test tree；`nq-app` 存在 local profile Spring context tests，`nq-infra` repository tests 多为 Recording / mock JDBC。 |
| `git ls-files "backend/**/application*.yml" "backend/**/application*.yaml" "backend/**/application*.properties"` | 已检查 | 当前 application configs 位于 `backend/nq-app/src/main/resources/`；local profile PostgreSQL + Flyway enabled，test profile PostgreSQL placeholder + Flyway disabled。 |
| `git diff -- backend` | 预检通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 预检通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 预检通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 预检通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 预检通过 | 输出为空，未改 deploy。 |
| `git diff -- .github` | 预检通过 | 输出为空，未改 workflow。 |
| `git diff -- backend/**/db/migration` | 预检通过 | 输出为空，未新增或修改 migration。 |
| Broad PostgreSQL/Flyway scan | 已执行 | 按用户指定 `rg` 执行；该 broad scan 会命中 `backend/target` 生成报告，后续证据提取已用排除 `target/build/dist` 的版本复跑。 |
| Security keyword scan | 已执行 | 命中项均为禁止说明、字段名、fake fixture、历史记录或 no-real boundary；本轮未读取或输出真实 credential material。 |

边界确认：

- Batch 2 只写为 planning documented，implementation not started。
- 未修改 `.github/workflows/ci.yml`。
- 未修改 backend / frontend / research / scripts / deploy。
- 未新增 API、migration 或测试。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。

## GATEK-ARCHITECTURE-BASELINE-REVIEW 验证记录（2026-06-14）

本轮为 GateK review-only / docs-only：只审查 architecture baseline、module boundary、test baseline、docs/facts 和 security baseline，并新增 / 同步文档。未修改 backend、frontend、research、scripts、deploy、测试代码、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以只读审查、Git diff、forbidden-area diff、阶段措辞和敏感边界检查为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 只包含允许的 README / docs/current 文档变更；新增 `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md` 由 status 确认。 |
| `git diff --check` | 通过 | 退出码 0；仅输出既有 Windows 工作区 LF/CRLF 提示，无 whitespace error。 |
| `git diff --stat` | 已检查 | tracked diff 仅覆盖 README / docs/current 文档；Git 默认不统计 untracked 新报告文件。 |
| `git diff -- backend` | 通过 | 输出为空，未改后端。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改脚本。 |
| `git diff -- deploy` | 通过 | 输出为空，未改部署目录。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `git ls-files backend/frontend/research/.github/deploy/scripts` | 已检查 | backend/frontend/research/deploy/scripts 结构符合当前基线；`.github/workflows` 当前无 tracked workflow。 |
| Backend boundary scan | 已检查 | `nq-core` / `nq-api` main code 未命中 JDBC / infra 直接依赖；`nq-api` SQL literal 抽查为空；ArchUnit boundary tests 已存在。 |
| Frontend stack scan | 已检查 | `package.json` 维持 React / Vite / Ant Design / TanStack Query / Axios / Zustand / Playwright；未发现 shadcn / Tailwind 体系接入。 |
| Research baseline scan | 已检查 | `research/py/pyproject.toml` 维持 pytest / mypy / ruff dev baseline；README 明确不作为 Java / Python runtime bridge。 |
| Stage wording scan | 已检查 | `rg "GateK implementation|AI started|DH integrated|LIVE enabled|real adapter implemented" README.md AGENTS.md CLAUDE.md docs/current` 命中均为否定式、禁止说明、风险说明或历史语境。 |
| Security / no-outbound scan | 已检查 | Permission probe freeze review、OKX bootstrap no-outbound review、Integration-0 docs 均保持 no-real / no-runtime / no-LIVE 边界；未读取或输出真实 credential material。 |

边界确认：

- 未修改 Java / TypeScript / Python / 测试代码 / 部署脚本 / migration。
- 未新增 API / migration。
- 未启动 GateK implementation / AI / DH runtime / LIVE / real adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 credential material。

## NQ-CREDENTIAL-PERMISSION-PROBE-FREEZE-P3-CLEANUP 验证记录（2026-06-14）

本轮为 P3 cleanup：只修复 NoReal fake result 的 `requestId` / `traceId` 字段质量，并收口 permission probe 文档层级。未新增功能、API、migration、前端、Python 或部署脚本；未接真实交易所、AI、DH 或 LIVE。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-infra,nq-core,nq-api,nq-app -am test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `git status --short` | 通过 | 只包含本轮允许范围文件；另有进入本轮前已存在的 `docs/current/API.md` 与 `docs/current/DB_SCHEMA.md` GateI 归档链接修正，本轮保留且未回退。 |
| `git diff --check` | 通过 | 退出码 0；仅 Git LF/CRLF 工作区提示。 |
| `git diff --stat` | 已执行 | diff 只覆盖允许的 NoReal port、NoReal unit test、README 和 docs/current 文档。 |
| `git diff -- backend/nq-infra/src/main/resources/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `git diff -- frontend` | 通过 | 输出为空，未修改前端。 |
| `git diff -- research` | 通过 | 输出为空，未修改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未修改脚本或部署入口。 |
| `rg "permission probe implemented|real exchange permission probe|OKX permission probe adapter|Binance permission probe adapter" docs/current README.md` | 已检查 | 命中均为 guarded baseline、NOT IMPLEMENTED、future review 或历史证据说明；未把真实交易所 adapter 写成 implemented。 |
| `rg "GateK implementation|AI started|DH integrated|LIVE enabled" docs/current README.md` | 已检查 | 命中均为否定式、禁止说明或“not started / not integrated / disabled”口径。 |
| `rg "apiKey|secret|passphrase|private key|mnemonic|signature|headers|raw response" docs/current backend/nq-infra/src/main/java backend/nq-infra/src/test/java` | 已检查 | 命中均为敏感信息禁入说明、脱敏边界、测试护栏或既有配置字段名；本轮未新增真实 credential material。 |

边界预期：

- NoReal port requestId 与 traceId 不再混同。
- NoReal port 仍不创建 HTTP client、不访问 OKX/Binance、不下单、不撤单、不转账、不提现。
- 文档当前状态统一：guarded backend implementation FROZEN / ACCEPTED；real exchange adapter NOT IMPLEMENTED；default behavior 为 NoReal `SKIPPED`；LIVE probe DISABLED / REJECTED。

## NQ-CREDENTIAL-PERMISSION-PROBE-FREEZE-REVIEW 验证记录（2026-06-14）

本轮只做 credential permission probe no-real-exchange / guarded backend freeze review 和文档同步；未修改 Java、测试代码、migration、API 语义、前端、Python 或部署脚本。冻结口径：permission probe guarded backend implementation FROZEN / ACCEPTED；real exchange permission probe adapter NOT IMPLEMENTED；默认 runtime 行为为 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`；LIVE credential probe DISABLED / REJECTED；AI / DH / LIVE NOT STARTED。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 审查开始前为空；文档同步后仅包含本轮允许的 docs/current / README 文档变更。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app,nq-adapter-okx,nq-adapter-binance -am test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `git diff --check` | 通过 | 无 whitespace error；如出现 LF/CRLF 提示，仅为 Git 行尾转换提示。 |
| `git diff --stat` | 已执行 | 仅统计本轮允许的文档变更。 |
| `git diff -- backend/nq-infra/src/main/resources/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `git diff -- frontend` | 通过 | 输出为空，未修改前端。 |
| `git diff -- research` | 通过 | 输出为空，未修改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未修改脚本或部署入口。 |

边界扫描：

- P0/P1=0；P2 无阻塞项；P3 仅保留 NoReal port requestId / traceId 混同和文档 gate 顺序轻微差异。
- no-real-exchange 证据充分：默认 bean 为 `NoRealExchangeCredentialPermissionProbePort`；NoReal test 使用 `ProxySelector` guard；Service tests 覆盖 LIVE/inactive/non-ACTIVE/Paper gate/withdraw risk/latest no-port；WebMvc tests 覆盖 response 脱敏和 request body 拒绝 credential material；adapter boundary tests 只覆盖错误分类和 forbidden endpoint，不实现真实 HTTP adapter。
- 未调用真实交易所；未实现真实 OKX/Binance permission probe adapter；未读取或输出真实 credential material。
- 阶段措辞保持 GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE disabled。

## NQ-FRONTEND-LOGIN-PAGE-PROFESSIONALIZATION 验证记录（2026-06-13）

本轮只改登录页、登录相关 E2E 和当前验证文档；未修改 backend、API、鉴权逻辑、token 存储、migration、deploy、scripts、Paper Trading、Dashboard、Backtest、Strategy、Risk、AI、DH 或 LIVE 交易逻辑。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build` | 通过 | frontend 下执行，`tsc -b && vite build` 成功；仍有既有 Vite chunk > 500 kB 警告。 |
| `npm run test:e2e -- tests/e2e/login-page-smoke.spec.ts --project=chromium` | 通过 | 新增登录页 smoke 单独通过，1 passed；验证登录页关键文案、Gate/LIVE/PAPER 状态、安全提示和空凭证输入。 |
| `npm run test:e2e` | 通过 | frontend 下执行完整 E2E，25 passed / 1 skipped；唯一 skipped 仍为未配置订单 ID 的既有订单详情链路。 |
| 后端本地启动 | 通过 | 首次按 Runbook `-pl nq-app` 启动失败，因本地 Maven 仓缺少 reactor 模块产物；改用 `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` 启动，`/actuator/health` 返回 `UP`。 |
| Browser 运行态验证 | 通过（降级） | Product Design Browser 初始化连续超时；按降级规则使用 Playwright browser 工具打开 `http://127.0.0.1:5179/login`，桌面 1440x900 与移动 390x844 均无水平溢出，登录卡片、安全提示和 Gate/LIVE/PAPER 文案可见。 |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 LF/CRLF 工作区提示。 |
| `git status --short` | 通过 | 工作区仅包含本轮允许范围文件：登录页、全局登录样式、登录 E2E helper、新增登录页 smoke、`WORKLOG.md`、`TESTING.md`。 |
| `git diff --stat` | 已执行 | 当前 tracked diff 统计为 5 个文件；Git 默认不统计 untracked 文件，新增 `frontend/tests/e2e/login-page-smoke.spec.ts` 由 `git status --short` 确认。 |

补充说明：

- 完整 E2E 输出仍包含既有 Ant Design React 19 compatibility、`Card.bordered`、`Modal.destroyOnClose`、`useForm` 和 `Descriptions` span 警告；本轮登录页已将新增 Card 改为 `variant="borderless"`，未新增登录页 `bordered` 警告。
- 本轮未执行 Maven / Python 全量验证；原因是未修改 backend / Python 代码。本轮为 E2E 临时启动过后端 local profile，并在验证后停止本轮启动的 `nq-app` 与 Vite 进程。

## NQ-FRONTEND-PAPER-TRADING-CONSOLE-DEEPEN 验证记录（2026-06-13）

- `npm run build`（frontend，含 `tsc -b`）：通过。
- `git diff --check`：通过（仅 LF/CRLF 行尾提示，无空白错误）。
- `npm run test:e2e`（本地拉起 `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run` + 本地 PostgreSQL 5432）：**24 passed / 1 skipped / 0 failed**，与上一轮基线一致，未倒退。
  - 7 个 paper-trading spec 全部通过，覆盖创建/启动/停止、订单/成交/持仓/快照、风控 run-once、资金/持仓曲线、交易复盘、紧急停机、告警 ACK/RESOLVE、日报生成、调度创建/执行一次/禁用、心跳、恢复/重试/监控守护、稳定性验收。
  - 迭代中修复两处与本轮重构直接相关的失败：
    1. 行内按钮被 `position:sticky` 页头拦截点击 → 给左侧 run 列表加内部滚动 `scroll={{y:420}}`，定位时滚动表体而非窗口。
    2. 顶部状态条新增展示风控 checkType 导致 `BASIC_HEALTH_CHECK` 多匹配 → spec 改 `.first()`。
- 视觉冒烟：Playwright 截图确认内联控制台（顶部状态条 / 左列表焦点高亮 / 中部曲线与日报 / 右侧操作区与告警面板）渲染正常。
- 后端 / Python：未跑（本轮未改 backend/python 代码）。

## NQ-FRONTEND-DESIGN-SYSTEM-V1-AND-TRADING-UI-REFACTOR 验证记录（2026-06-13）

- `npm install echarts`（frontend）：通过，新增 echarts ^6.1.0，lock 同步更新。
- `npm run build`（frontend，含 `tsc -b` 类型检查）：通过（vite 8 构建成功；chunk >500kB 警告为 echarts 体积所致，构建前已存在同类警告基线）。
- `npm run typecheck` / `npm run lint`：脚本不存在（package.json 未定义），类型检查由 `npm run build` 内的 `tsc -b` 覆盖。
- `npm run test:e2e`（本地拉起 `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run` + 本地 PostgreSQL 5432）：**24 passed / 1 skipped / 0 failed**。
  - 全部 7 个 paper-trading spec、dashboard smoke、strategies / research / backtests / evaluations / publishes / accounts / trading-workbench spec 通过，证明本轮 UI 重构未破坏既有交互契约。
  - 前置修复：`tests/e2e/support.ts` 登录 fixture 自 288c28f8（2026-05-28）起断裂（登录文案改为 "NexusQuant 控制台"/"登录" 且移除表单凭证预填，fixture 未同步），修复前 24 个用例全部在登录步骤失败。
  - 原存量 2 个失败：`marketdata-dataset-smoke` / `marketdata-ingestion-smoke`，根因为 dc1288e0（2026-05-29）给 Marketdata 表单加 开始/结束时间 必填规则但未同步 spec（spec 未填日期，提交被表单校验拦截）。已通过同步 DatePicker 必填输入修复；未降低页面校验，未跳过测试（只改两个 spec，未改 MarketdataPage 业务代码）。
- 视觉冒烟：Playwright 截图验证登录页与 Dashboard 深色主题、安全横幅、指标条、空态渲染正常。
- 后端 / Python：未跑（本轮未改 backend/python 代码）。

## NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-DESIGN-REVIEW 验证记录（2026-06-12）

本轮只读审计 credential permission probe code/API/test 实现方案，新增设计审计报告并同步 README/WORKLOG/TESTING/plan 状态。未修改 Java、Repository、Service、Controller、DTO、API、migration、前端、Python 或部署脚本；未调用真实交易所；未实现 permission probe。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过，含既有非本轮改动 | 当前命中本轮允许文档：`README.md`、`docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`、`docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`、`docs/current/README.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、新增 `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`；另有预检时已存在的 `backend/nq-adapter-binance/.../BinanceFiltersCacheTest.java`，本轮未触碰或回退。 |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 Git 将 LF 转为 CRLF 的工作区提示。 |
| `git diff --stat` | 通过，含既有非本轮改动 | 当前工作区总 stat 包含 7 个 tracked 文件、96 insertions / 6 deletions；其中 `BinanceFiltersCacheTest.java` 为预检时已存在的非本轮 Java 改动；新增报告文件未 staged，因此不出现在 `git diff --stat` 中，由 `git status --short` 确认。 |
| Maven / frontend / Python | 未执行 | docs-only；未修改业务代码、测试代码、配置、migration、前端、Python 或部署脚本，不把未执行测试写成通过。 |
| 真实交易所调用 | 未执行 | 本轮未调用 OKX、Binance、Bybit、Gate 或任何真实交易所；未读取或输出真实密钥。 |

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-FIX 验证记录（2026-06-12）

本轮修复 OKX instruments cache 构造期 eager refresh，补充 no-outbound 回归测试，并同步审计报告状态。未新增 migration，未修改前端、Python 或部署脚本，未调用真实交易所，未接 AI / DH / LIVE。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-adapter-okx,nq-app -am test` | 通过 | `BUILD SUCCESS`；`nq-adapter-okx` 27 tests / 0 failures；`nq-app` 52 tests / 0 failures；新增 no-outbound app context 测试通过。 |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 Git 将 LF 转为 CRLF 的工作区提示。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 backend module 全部 `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures；总耗时 02:43。 |
| migration diff 检查 | 通过 | `git diff --name-only -- backend/nq-infra/src/main/resources/db/migration` 无输出。 |
| frontend diff 检查 | 通过 | `git diff --name-only -- frontend` 无输出。 |
| research diff 检查 | 通过 | `git diff --name-only -- research` 无输出。 |
| deploy scripts diff 检查 | 通过 | `git diff --name-only -- scripts` 无输出；未修改部署脚本。 |
| no-outbound 证据 | 通过 | `OkxInstrumentsCacheTest` / `OkxExchangeAdapterBootstrapNoOutboundTest` 用 fake client/server 证明构造期 0 次 public GET、首次显式读取才刷新；`OkxBootstrapNoOutboundLocalContextTest` 用 `ProxySelector` 探针证明 local Spring context 启动期访问 `www.okx.com` public instruments 次数为 0，且日志不含 `okx_adapter_bootstrap_fallback_enabled`。 |
| 日志 / surefire 报告关键字扫描 | 通过 | 未命中 `okx_adapter_bootstrap_fallback_enabled`、`www.okx.com/api/v5/public/instruments` 或 `api/v5/public/instruments?instType=SPOT`。 |
| 真实交易所调用 | 未执行 | 本轮测试不依赖真实 OKX/Binance 网络，不读取或输出真实密钥。 |

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW-DOC 验证记录（2026-06-12）

本轮只将 OKX bootstrap no-outbound 只读审计结论落到 `docs/current`，新增 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` 并更新 README/WORKLOG/TESTING 索引。未修改 Java、配置、migration、测试、frontend、Python 或部署脚本，未调用 OKX、Binance 或任何真实交易所，未实现 fix。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 Git 将 LF 转为 CRLF 的工作区提示。 |
| `git diff --stat` | 通过 | 已跟踪 diff 集中在 `README.md`、`docs/current/README.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`；新增报告文件因未 staged 不在该命令统计中，由 `git status --short` 单独确认。 |
| `git status --short` | 通过 | 仅命中允许范围：4 个 Markdown 修改文件 + 1 个新增 `docs/current/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`。 |
| 全量测试 | 未执行 | docs-only；未修改业务代码、测试代码、配置、migration、frontend、Python 或部署脚本。 |
| 真实交易所调用 | 未执行 | 本轮未调用 OKX、Binance 或任何真实交易所；未读取或输出真实密钥。 |

## NQ-DH-INTEGRATION0-SAFETY-GATE-CLOSE 验证记录（2026-06-12）

本轮只做 Integration-0 safety gate close / acceptance report（新增 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` 并更新 STATUS/README/ROADMAP/WORKLOG/TESTING），未修改任何 Java、测试代码、frontend、Python、API、migration 或部署脚本，故本轮未运行全量测试，验收依据引用上一轮已通过结果。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮新增/修改的 `docs/current` Markdown。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` 与 STATUS/README/ROADMAP/WORKLOG/TESTING。 |
| 全量测试 | 未执行 | docs-only；未改业务/测试代码；引用上一轮 `mvn -f backend/pom.xml test` BUILD SUCCESS（nq-app 51 tests / 0 failures，Integration-0 16 passed，ArchUnit 全绿）作为验收依据。 |
| 验收口径检查 | 通过 | Integration-0 = PASS/CLOSED/ACCEPTED；Runtime integration / Integration-1 / AI NOT STARTED；DH NOT INTEGRATED；LIVE DISABLED；未误写真实集成。 |

## NQ-DH-INTEGRATION0-CONTRACT-TEST-IMPL 验证记录（2026-06-12）

本轮把 Integration-0 contract test matrix（INT0-T01..T15）落成可运行测试代码与脱敏 fixture，仅新增 `backend/nq-app/src/test/**`，未修改任何 `src/main`、API、migration 或部署。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS；`nq-app` 51 tests / 0 failures / 0 errors（原 35 + 本轮 16）。 |
| `NqDhIntegration0*Test` 定向 | 通过 | 16 tests / 0 failures（ContractValidation 6 + Security 8 + NoSideEffect 2）。 |
| ArchUnit 边界 | 通过 | ModuleBoundaryArchTest / PackageBoundaryArchTest 全绿；新增 `..app.integration0..` 测试包未触碰受护栏边界。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git status --short` | 通过 | 仅命中 `backend/nq-app/src/test/**`（测试代码与 fixtures）。 |
| 生产代码边界 | 通过 | 未修改 `src/main`，未新增 API / migration / Controller / Service / Repository / DTO / RealClient / 真实 Provider。 |
| 真实通道边界 | 通过 | 未做真实 HTTP / 真实 NQ / 真实交易所；未读取真实密钥（固定假值）；未开启 LIVE。 |

说明：nonce store 为 test-only 内存实现；Integration-1 前必须补持久化 nonce、rate limit、memory cap（DH P1-4 residual），不在本轮范围。

## NQ-DH-INTEGRATION0-MOCK-CONTRACT-TEST-DESIGN 验证记录（2026-06-11）

本轮将 Integration-0 已冻结的 15 项 contract test 拆成详细矩阵（每项 16 字段）+ 共享 fixture + forbidden side-effect checklist + 验收/blocker 清单 + 下一步代码任务草案，写入 `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md` 并更新 README/ROADMAP/WORKLOG/TESTING。本轮**只做设计不写测试代码**，未修改 Java、frontend、Python、API、migration、测试代码或部署脚本，故未运行全量测试。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮修改的事实源 Markdown 文件。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md` 与 README/ROADMAP/WORKLOG/TESTING。 |
| 全量测试 | 未执行 | docs + contract test design only，未写测试代码、未改业务代码/API/migration/部署。 |
| 代码文件创建检查 | 通过 | `futureCodeLocationSuggestion` 仅为建议路径，未创建任何 `.java` 或测试代码文件。 |
| 集成/口径边界检查 | 通过 | 未实现集成、未接真实 HTTP/RealClient/Provider、未开启 LIVE；未把本轮写成 implemented，未把 Integration-0 写成真实集成。 |

## NQ-DH-INTEGRATION-0-CONTRACT-FREEZE 验证记录（2026-06-11）

本轮只做 Integration-0 契约冻结与安全策略 / contract test 计划文档，新增 3 份 `NQ_DH_INTEGRATION0_*.md` 并更新 README/ROADMAP/WORKLOG/TESTING；未修改任何 Java、frontend、Python、API、migration、测试或部署代码，故未运行全量测试（符合 AGENTS.md「只改文档可不跑全量测试，须写清未跑原因」规则）。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮新增/修改的事实源 Markdown 文件。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 `docs/current/NQ_DH_INTEGRATION0_*.md` 与 README/ROADMAP/WORKLOG/TESTING。 |
| 全量测试 | 未执行 | 本轮 docs + contract design only，未修改业务代码、API、migration、测试或部署脚本。 |
| 集成边界检查 | 通过 | 未实现集成；未新增 RealClient / 真实 Provider / 真实 HTTP；未做真实联调；未开启 LIVE。 |
| 阶段口径检查 | 通过 | 未把本轮写成 implemented；未把 Integration-0 写成真实集成；未把 DH 写成 integrated；未把 AI 写成 started；未把 LIVE 写成 enabled。 |

## DOC-SYNC-GATEK-PRE-AND-INT0-REGISTRATION 验证记录（2026-06-11）

本轮只同步 NQ / DH 三轮审计结论与阶段事实到事实源文档，未修改任何 Java、前端、Python、部署、API、migration 或测试代码，故未运行全量测试（符合 AGENTS.md「只改文档可不跑全量测试，但须写清未跑原因」规则）。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮同步的事实源 Markdown 文件。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 NQ `CLAUDE.md` / `AGENTS.md` / `docs/current/{STATUS,README,ROADMAP,WORKLOG,TESTING}.md`。 |
| 全量测试 | 未执行 | 本轮 docs-only，未修改业务代码、API、migration、测试或部署脚本。 |
| 阶段口径检查 | 通过 | 未把 GateK-PLAN 写成 GateK implementation；未把 Integration-0 写成真实集成；未把 AI 写成 started；未把 DH 写成 integrated；未把 LIVE 写成 enabled。 |

## Credential Permission Probe Schema 验证记录（2026-06-08）

本轮新增 permission probe schema-only migration，并同步 `docs/current` 文档和 README 索引；未实现 permission probe，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本，未接 AI、DH、LIVE。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；`Total time: 02:24 min`，`Finished at: 2026-06-08T13:26:33+08:00`。 |
| Flyway migration 验证 | 通过 | Maven 中 `nq-app` local integration test 成功验证 31 个 migrations，并从 V30 迁移到 V31。 |
| migration 范围检查 | 通过 | 本轮只新增 `V31__schema_credential_permission_probe.sql`；未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 本轮未修改 Java、Repository、Service、Controller、DTO、测试业务语义或新增 API。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| permission probe 实现边界 | 通过 | 未实现 permission probe，未新增 permission probe endpoint，未新增 Java enum 或 API DTO。 |
| 真实交易所触达隔离 | 有残余风险 | 本轮 migration/docs 未实现或主动调用 permission probe；但全量 Maven 中既有 `MarketdataControllerLocalIntegrationTest` 在 local profile 启动时触发 OKX public instruments bootstrap fallback，并因 `No route to host` 失败。该日志不涉及 credential/private endpoint/下单/撤单/转账/提现，但不能把本次验证写成完全零真实交易所触达尝试。 |

## Credential Permission Probe Design Review 验证记录（2026-06-08）

本轮只读设计审计真实交易所 credential permission probe，并新增设计审计文档与索引记录；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本；未调用真实交易所，未实现 permission probe。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| 允许路径范围检查 | 通过 | 本轮只修改 `docs/current` 文档和 README 索引。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 本轮未修改 Java、Repository、Service、Controller、DTO、测试业务语义或新增 API。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| 真实交易所 / AI / DH / LIVE 边界检查 | 通过 | 未调用 OKX、Binance、Bybit、Gate 或任何真实交易所；未接 AI、DH、LIVE；未实现 permission probe。 |
| Maven 测试 | 未执行 | 本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，未修改业务代码、migration、API、前端、Python 或部署脚本；不把未执行测试写成通过。 |

## DB Schema Credential Governance Doc Cleanup Batch 5-G-A 验证记录（2026-06-08）

本轮只修复 Batch 5-G freeze review 发现的 P3 文案问题：修正 credential disable endpoint OpenAPI description 的过期描述；为 Batch 5-F-A enable governance review 增加历史快照说明；同步 freeze review、README 索引、WORKLOG 和 TESTING。本轮未新增 migration，未修改 credential 业务逻辑，未修改 Repository / Service / DTO / 测试业务语义，未新增 API，未修改前端、Python 或部署脚本。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `mvn -f backend/pom.xml -pl nq-api -am test` | 通过 | 20 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；`Total time: 03:36 min`，`Finished at: 2026-06-08T12:02:21+08:00`。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 仅修改 `ExchangeAccountCredentialController.java` 的 OpenAPI description 文案；未修改 credential 业务逻辑、Repository、Service、DTO、测试业务语义或新增 API。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| 全量后端测试 `mvn -f backend/pom.xml test` | 未执行 | 本轮编译验证范围未因改动扩大；已按任务要求执行 `nq-api -am` 测试并通过，不把未执行的全量后端测试写成通过。 |

## DB Schema Credential Governance Freeze Review Batch 5-G 验证记录（2026-06-08）

本轮只读复核 Batch 5-A ~ 5-F-C credential governance，并新增冻结复核文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration；只读复核 V29 / V30。 |
| Java/API 范围检查 | 通过 | 本轮未修改 Java、Repository、Service、Controller、DTO 或 API；发现一个 P3 过期 OpenAPI description，已记录到 freeze review，不在本轮修改 Java。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| credential governance 必查项 | 通过，含 P3 note | API response 脱敏、audit metadata 脱敏、lifecycle tests、active material selection、rotate/enable 状态语义、permission_scope 与 failed_auth_count 边界均通过；仅存在过期文案 P3。 |
| 后端 Maven 测试 | 未执行 | 本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，未修改业务代码、migration、API、前端、Python 或部署脚本；不把本轮未执行测试写成通过。上一轮 5-F-C 的 Maven 通过记录保留在下方对应章节。 |

## DB Schema Credential Enable Command Batch 5-F-C 验证记录（2026-06-08）

本轮实现最小 credential enable command，并同步 `docs/current` 文档；未新增 migration，未修改历史 migration，未修改前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app -am test` | 通过 | 实际 reactor 覆盖 23 个后端模块，`BUILD SUCCESS`；新增关键测试包括 `ExchangeAccountCredentialCommandServiceTest` 15 tests / 0 failures、`JdbcExchangeAccountCredentialRepositoryTest` 2 tests / 0 failures、`ExchangeAccountCredentialControllerWebMvcTest` 4 tests / 0 failures。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个后端模块均为 `SUCCESS`，最终 `BUILD SUCCESS`，总耗时 `02:11 min`，完成时间 `2026-06-08T11:31:38+08:00`。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration；Batch 5-F-C 复用 Batch 5-F-B 已准备的 `V30__schema_credential_enable_audit_event.sql`。 |
| Java/API enable 回归覆盖 | 通过 | 覆盖 `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`、reason 必填、同 account + credentialType 其他 ACTIVE 冲突、`ACTIVE / REVOKED / ROTATED / EXPIRED` 拒绝、结构性校验失败保持 `DISABLED`、response 脱敏。 |
| 禁止范围检查 | 通过 | 未修改前端、Python、部署脚本；未新增真实交易所权限探活、reveal/decrypt/includeSecret endpoint、AI、DH、LIVE 或真实交易路径；未把 GateK-PLAN 写成实现已启动。 |

验证过程中的已知非本轮问题 / 既有 warning：

- Maven settings.xml 仍提示 `Unrecognised tag: 'profiles'`。
- 部分测试仍有既有 SLF4J provider、Mockito dynamic agent warning。
- `TradingVerificationControllerLocalTest.shouldReturnUnifiedInternalError` 会按测试预期触发统一 internal error 日志，测试结果仍为 0 failure。
- local profile 下 OKX adapter bootstrap 仍可能因本地网络返回 fallback warning，不影响本轮 credential enable command 测试通过结论。

## DB Schema Credential Enable Audit Event Schema Batch 5-F-B 验证记录（2026-06-08）

本轮新增 schema-only migration，为 `credential_audit_logs.event_type` CHECK 增加 `ENABLED`，并同步 `docs/current` 文档；未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| migration diff 范围检查 | 通过 | 只新增 `V30__schema_credential_enable_audit_event.sql`；未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 未修改 Java；只读检索未发现 credential enable endpoint 或 `enableCredential` 方法。 |
| 文档索引范围检查 | 通过 | 仅补齐 `README.md` 与 `docs/current/README.md` 中 Batch 5-F-B schema-only 当前事实索引；未写成 enable implemented。 |
| 禁止范围检查 | 通过 | 未新增 enable endpoint；未新增 rotate / revoke / disable / expire 行为；未修改前端、Python、部署；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动；未把本轮写成 enable implemented。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；后端模块测试通过。 |

## DB Schema Credential Enable Governance Review Batch 5-F-A 验证记录（2026-06-07）

本轮只读审计 credential enable / re-enable 生命周期设计，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 工作树范围检查 | 通过 | 本轮只修改 `docs/current` 文档和必要 README 索引；未修改 backend Java、API、frontend、Python 或部署脚本。 |
| migration diff 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围检查 | 通过 | 未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动；未把本轮审计写成 enable 已实现。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Active Credential Uniqueness Review Batch 5-E-C 验证记录（2026-06-07）

本轮只读评估 active credential 唯一性模型，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| migration diff 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围检查 | 通过 | 未修改 Java、Repository、Service、Controller、DTO、API、frontend、Python 或部署脚本；未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Active Material Deterministic Selection Batch 5-E-B 验证记录（2026-06-07）

本轮接入 deterministic active summary / active material selection：无 `credentialType` 多 ACTIVE type 返回 conflict，显式 `credentialType` 只选择对应 ACTIVE credential；未新增 migration，未修改前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api -am test` | 通过 | 相关 reactor module 均为 `SUCCESS`，`BUILD SUCCESS`；覆盖 Repository / Service / Controller active selection 回归。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，以及既有 controller local test 的预期 internal error 日志，不影响通过结论。 |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 新增 migration 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围检查 | 通过 | 未修改 frontend、Python 或部署脚本；未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动。 |
| active selection 回归覆盖 | 通过 | 覆盖单 active 兼容、多 active no-type conflict、指定 `credentialType` 查询/校验、指定不存在 type、inactive lifecycle 不可读、rotate 后同 type 只读新 credential、API response 脱敏、不依赖 `permission_scope`。 |

## DB Schema Credential Active Material Selection Review Batch 5-E-A 验证记录（2026-06-07）

本轮只读审计 credential active summary / active material 选择语义，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 工作树范围检查 | 通过 | 本轮只修改文档文件；未新增 migration，未修改 backend Java、API、frontend、Python 或部署脚本。 |
| 禁止范围检查 | 通过 | 未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Rotate Command Batch 5-D-B 验证记录（2026-06-07）

本轮实现显式 credential rotate command，并同步 `docs/current` 文档；未新增 migration，未修改前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | 初次执行因新增测试的 no-handler 断言不匹配 standalone MockMvc 行为失败；修正为反射检查无 `enable` 方法后复跑通过。最终 23 个 reactor module 均为 `SUCCESS`，`BUILD SUCCESS`。 |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 新增 migration 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围扫描 | 通过 | 未修改 frontend、Python 或部署脚本；未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易。 |
| rotate 回归覆盖 | 通过 | 覆盖 ACTIVE rotate 成功、旧 `ROTATED`、新 `ACTIVE`、old/new audit log、active material 只返回新 credential、非 ACTIVE 派生拒绝、reason 缺失/敏感词拒绝、重复 rotate 旧 credential 拒绝、API response 脱敏、audit metadata 不含敏感字段。 |

## DB Schema Credential Rotate Governance Review Batch 5-D-A 验证记录（2026-06-07）

本轮只读审计 credential rotate 生命周期设计，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 工作树范围检查 | 通过 | 本轮只修改 `docs/current` 文档。 |
| 禁止范围检查 | 通过 | 未新增 migration，未修改 backend Java、API、frontend、Python 或部署脚本；未新增 rotate endpoint 或 enable endpoint；未接 AI、DH、LIVE 或真实交易。 |
| 阶段与禁写状态检查 | 通过 | 未把 GateK-PLAN 写成实现已启动，未把 AI、DH、LIVE 或 rotate 写成已启用或已实现；相关命中均为禁止项或未实现说明。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Revocation Governance Batch 5-B 验证记录（2026-06-07）

本轮新增 `V29__schema_credential_revocation_governance.sql` 并同步 credential revocation / DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 新增 migration 范围检查 | 通过 | 本轮只新增 `backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql`，未修改历史 migration。 |
| 禁止范围扫描 | 通过 | 未修改 Java、Repository、Service、Controller、DTO、前端、Python 或部署脚本；未新增 API；未实现 revoke/rotate endpoint；未接 AI、DH、LIVE 或真实交易。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；该结果只证明当前后端测试和 Flyway 迁移装配通过，不代表 revoke/rotate 业务行为已实现。 |

## DB Schema Governance Batch 4-B 验证记录（2026-06-07）

本轮为 `research_configs` / `backtest_configs` 增加受控归档命令；未新增 migration，未修改历史 migration。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 禁止范围扫描 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff，未新增 migration；新增代码只触达 research/backtest config archive 命令、DTO、Repository、Service、Controller 和测试。文档中出现 credentials、positions、risk_events、orders/trades/ledger/audit、facts、marketdata timeseries、AI、DH、LIVE、真实交易等词均为禁止范围或历史边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 4-A 验证记录（2026-06-07）

本轮接管 `research_configs` / `backtest_configs` V28 status/archive 字段的 Repository 与 Service 语义；未新增 migration，未修改历史 migration。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 禁止范围扫描 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff，未新增 migration；代码改动只触达 research/backtest 配置 domain、Repository、Service、DTO 和测试。文档中出现 credentials、positions、risk_events、orders/trades/ledger/audit、facts、marketdata timeseries、AI、DH、LIVE、真实交易等词均为禁止范围或历史边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 3-B 验证记录（2026-06-06）

本轮新增 `V28__schema_research_backtest_config_governance.sql` 并同步 DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| V28 禁止范围扫描 | 通过 | 新 migration 未命中禁止表名、AI、DH、LIVE、真实交易、逻辑删除或 retention purge 相关结构变更；只命中两张目标配置表自身的约束名。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 3-A 验证记录（2026-06-06）

本轮新增 `V27__schema_master_table_governance.sql` 并同步 DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| V27 禁止范围扫描 | 通过 | 未命中禁止表、事件、时序、AI、DH、真实交易、逻辑删除或 retention 相关结构变更。 |
| `mvn -f backend/pom.xml test` | 初次失败后修复重跑通过 | 初次在 `nq-app` 暴露既有 package/path 不一致问题；已修复 `TradingMaintenanceService`、`ManualStrategyTriggerGateway`、`OrderCommandStrategyExecutionGateway` 的 package/import。 |
| `mvn -f backend/pom.xml clean test` | 通过 | 清理旧 package 残留 class 后，23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。 |
| `mvn -f backend/pom.xml test` | 通过 | 修复后按用户要求重跑原命令，23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。 |

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含本次 docs/config 修改与 `git mv` 归档，详见 `WORKLOG.md` |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；local integration 日志确认连接 `jdbc:postgresql://localhost:5432/nexus_quant` |
| `npm ci` | 通过 | 首次因 `D:\Tool\NodeJs\node_cache` 写入权限/占用失败；提权重跑后成功安装 177 packages；`npm audit` 提示 4 个漏洞（2 moderate、2 high），本任务未执行 `npm audit fix` |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；Vite 提示 bundle chunk 超过 500 kB，属于既有构建体积风险 |
| `npm run test:e2e` | 通过 | BASELINE-FIX-2 后通过；8 个 Playwright 用例中 5 passed、3 skipped。E2E runner 会启动 Vite、设置外部 dev server 模式、运行 Playwright、最后停止 Vite |
| `python -m pip install -e ".[dev]"` | 未在当前环境完成 | 已在 `pyproject.toml` 补充 dev extras；当前本机 editable install 两次卡在 build/editable 阶段超时。为完成当前验证，使用等价工具安装命令补齐当前用户环境 |
| `python -m pip install pytest mypy ruff` | 通过 | 提权执行成功；下载较慢并发生断点续传，最终安装 `pytest-9.0.3`、`mypy-2.1.0`、`ruff-0.15.13` |
| `python -m pytest -q` | 通过 | `2 passed in 0.01s` |
| `python -m mypy src` | 通过 | `Success: no issues found in 8 source files` |
| `python -m ruff check .` | 通过 | `All checks passed!` |
| 本地启动验证 | 通过 | `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` 启动成功；`/actuator/health` 返回 `UP`；`POST /api/auth/login` 和 `GET /api/auth/me` 成功，当前默认账户恢复为 `rc1-admin-default / 900001` |

## 当前剩余风险

- 未执行 `docker compose up -d postgres`：当前本机已有 PostgreSQL `5432` 可用，后端测试和 local profile 均已连接该实例。
- `npm audit` 仍提示 4 个漏洞（2 moderate、2 high），后续单独处理。
- Vite build 仍提示 chunk 超过 500 kB，后续单独处理。
- E2E 中 3 个详情/交易链路用例按当前环境数据条件 skip，不代表对应业务链路已完整验证。

## GateJ-FREEZE-FINAL-DOC 验证记录（2026-06-05）

本轮只做最终验收文档整理和 `docs/gates/gate-j` 冻结快照，不执行 build/deploy/restart，不修改后端/前端业务代码、API、migration、脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| GateJ-FREEZE 30m observation | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 1h acceptance | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 24h acceptance | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 7d acceptance | PASS | 7d checkpoint 为 2026-06-05 14:53:24 +08:00；health-loop 最新样本为 2026-06-05 15:40:58 +08:00。 |
| health-loop 样本数 | 2025 | 起点为 2026-05-29 14:53:20 +08:00。 |
| 168h nq-app 错误补扫 | 通过 | `docker compose logs --since=7d` 不被当前 Compose 识别，已补跑 `--since=168h`；`nq-app-error-scan-168h.txt` 的 `wc -l = 0`。 |
| 18888 health | UP | freeze 后端 health 正常。 |
| 5179 health | UP | freeze 前端 health 正常。 |
| nginx / nq-app / postgres | Up 7 days | postgres 为 healthy。 |
| after-7d.sql | 已生成 | 文件大小 266K；不进入 Git 冻结快照。 |
| 5179 安全组 | 通过 | 已确认只允许本人 IP 访问。 |
| UI/UX smoke review | Functional stability PASS；UI/UX professionalism FAIL | 不影响 GateJ-FREEZE 稳定性验收；登记为 post-freeze remediation。 |
| build/deploy/restart | 未执行 | 用户明确禁止，本轮只做文档冻结。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮未改业务代码、前端代码、API、migration、脚本或部署配置；不执行 build/deploy/restart。 |

边界确认：

- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- GateK not started；Next 仅为 GateK-PLAN。
- No new backend/frontend business code, API, migration, deployment, AI, DH, or real trading path was added in this final documentation stage。

## Codex Workflow 文档固化验证记录（2026-06-06）

本轮只新增和更新 Codex 插件路由、工作流、任务模板、Project Instructions 与索引文档，不修改后端/前端业务代码、API、migration、Python、脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| 同名文档存在性检查 | 已执行 | 目标 4 个新文档此前不存在，本轮新建；`docs/current/README.md` 已存在，本轮追加入口。 |
| `docs/current/README.md` 链接检查 | 已执行 | 已追加 `AGENTS.md`、插件工作流、Router Skill、任务模板、Project Instructions 的相对链接入口。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 禁止范围检查 | 已执行 | 明确禁止 LIVE trading、真实下单/撤单路径、真实 DH 接入、real provider、RealClient、credentials 泄露。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown 文档，未修改业务代码、API、migration、前端页面、Python 或部署配置。 |

## Codex Workflow 文档一致性小修验证记录（2026-06-06）

本轮只修复 Codex Workflow Router Skill 状态表述和 Project Instructions 前置规则，不修改后端/前端业务代码、API、migration、Python、部署脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 用于确认本轮 Markdown 文档变更范围。 |
| `git diff --check` | 已执行 | 用于检查空白错误。 |
| Router Skill 状态表述检查 | 已执行 | `NQ_DH_WORKFLOW_ROUTER_SKILL.md` 已写明 `nq-dh-workflow-router` 当前按 `AGENTS.md` 作为 active skill 使用。 |
| Project Instructions 前置规则检查 | 已执行 | `CODEX_PROJECT_INSTRUCTIONS.md` 已补充 `nq-dh-workflow-router` 前置分类、范围限定和固定输出字段。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。 |

## Codex Workflow 输出字段口径小修验证记录（2026-06-06）

本轮只统一 Codex Workflow 标准输出字段，将必填输出字段统一为 `Findings`，不再把 `Summary` 作为必填字段；不修改后端/前端业务代码、API、migration、Python、部署脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 用于确认本轮 Markdown / Skill 文档变更范围。 |
| `git diff --check` | 已执行 | 用于检查空白错误。 |
| 输出字段口径检查 | 已执行 | `AGENTS.md`、`.agents/skills/nq-dh-workflow-router/SKILL.md`、`NQ_DH_CODEX_PLUGIN_WORKFLOW.md`、`NQ_DH_WORKFLOW_ROUTER_SKILL.md`、`CODEX_PROJECT_INSTRUCTIONS.md` 的标准输出格式均使用 `Findings`。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown / Skill 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。 |

## GateH-1-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；新增 trading workspace 订单列表 controller 测试通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；E2E 后已停止监听 `18888` 的临时 Java 进程 |
| `npm run test:e2e` | 通过 | 10 个 Playwright 用例中 7 passed、3 skipped |

GateH-1 E2E 覆盖：

- `/trading` 正式交易工作台可进入。
- 页面显示正式账户上下文与 SIM / LIVE。
- 订单列表表格可加载，空态可见。
- 下单前检查抽屉展示风控摘要和服务端风控不可绕过状态。
- `/trade-validation` 旧路径仍可访问，并展示过渡入口提示。
- `E2E_TRADE_ORDER_ID` 未配置时，真实订单详情链路按原因 skip。

GateH-1 剩余验证风险：

- 当前本地没有配置 `E2E_TRADE_ORDER_ID`，因此订单详情真实数据链路未在本次 E2E 中执行，通过 skip 明确记录。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning 和 Vite chunk > 500 kB 警告仍存在，本轮不处理。

## GateH-2-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateH-2 migration、API、adapter bridge 与既有 local integration 均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `17` |
| `npm run test:e2e` | 通过 | 12 个 Playwright 用例中 9 passed、3 skipped；新增 `marketdata-bars-query-smoke` 与 `marketdata-ingestion-smoke` 均通过 |

GateH-2 E2E 覆盖：

- `/marketdata` 可打开。
- 页面展示 GateH-2 固定查询维度：OKX/BINANCE、SPOT、BTC-USDT、1m。
- K 线查询不报错，并展示 Bars 表格空态/数据态。
- 可通过页面创建 `marketdata_ingestion_jobs`。
- 可通过页面触发 `run-once`。
- 页面可查询 job/run 状态与运行结果。

GateH-2 交易所访问说明：

- 本轮 E2E 不依赖外网交易所稳定性。
- `run-once` 走本地后端真实 API 与 adapter 路径；当交易所接口返回空数据或外网不可用时，运行记录仍保存明确状态和统计。
- 本轮未执行真实生产交易所长时间回填或大范围历史数据下载。

GateH-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

## GateH-3-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateH-3 migration、dataset API、backtest dataset binding API、run snapshot 字段和既有回测链路均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `18` |
| `npm run test:e2e` | 通过 | 14 个 Playwright 用例中 10 passed、4 skipped；新增 `marketdata-dataset-smoke` 通过，`backtest-dataset-binding-smoke` 因当前本地库没有可绑定 backtest config 种子而 skip |

GateH-3 E2E 覆盖：

- `/marketdata` 可创建 dataset。
- dataset 可展示覆盖范围、状态、质量状态、bar/gap 统计。
- dataset 可触发 `refresh-quality`。
- `/backtests` 已提供 dataset 绑定入口。
- 当前本地库没有 `research_configs/backtest_configs` 种子，`backtest-dataset-binding-smoke` 未执行 UI 绑定提交；后端 controller 测试已覆盖 `PATCH /api/backtest-configs/{configId}/dataset`。

GateH-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

## GateI-PLAN 验证记录

日期：2026-05-18

本轮只修改文档，不重新执行全量后端、前端、Python 测试。

必须检查项：

- `git status --short --branch`：已执行，当前仅规划文档变更。
- `docs/current/PLAN_GATEI.md`：存在。
- `docs/current/GATEI_API_PLAN.md`：存在。
- `docs/current/GATEI_DB_PLAN.md`：存在。
- `docs/current/GATEI_FRONTEND_PLAN.md`：存在。
- `docs/current/GATEI_TEST_PLAN.md`：存在。
- `docs/current/GATEI_WORK_ORDER.md`：存在。
- `docs/current/STATUS.md`：已写清 GateH completed、当前执行 GateI-PLAN、AI 仍未开始。
- 未新增业务代码、migration、API 实现或前端页面实现。
- 未接入 AI。

沿用当前验证基线：

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- Python `pytest`、`mypy`、`ruff` 已通过。

## GateI-1-WO 验证记录

日期：2026-05-18

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；新增策略版本 service 测试、发布绑定 service 测试、既有 local integration 测试均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `19` |
| `npm run test:e2e` | 通过 | 16 个 Playwright 用例中 13 passed、3 skipped；新增 `strategy-version-smoke` 与 `publish-version-smoke` 均通过 |

GateI-1 E2E 覆盖：

- `/strategies` 可打开并查询策略定义。
- 当本地库缺少策略定义时，E2E 通过正式 `POST /api/strategies` 创建最小 SIM 策略定义 fixture。
- 策略详情可展示“策略版本”和“创建策略版本”区域。
- 可创建 `ACTIVE` 策略版本，并展示参数快照、配置快照和状态。
- `/publishes` 可展示策略版本 ID 与版本快照入口。

GateI-1 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

GateI-1 边界确认：

- 未进入 GateI-2/3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未修改策略核心算法、交易核心状态机或回测核心算法。

## GateI-2-WO 验证记录

日期：2026-05-19

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateI-2 migration V20、回测配置绑定、run 快照固化、evaluation 指标增强和既有 local integration 均通过 |
| `npm ci` | 通过 | 恢复前端依赖；原因是本地 `node_modules/typescript` 目录不完整导致首次 build 找不到 `typescript/bin/tsc`；命令完成后仍有 4 个 npm audit 告警，本轮不处理 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run '-Dspring-boot.run.profiles=local'` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `20` |
| `npm run test:e2e` | 通过 | 全量 Playwright 17 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateI-2 backtest/evaluation 主链 |

GateI-2 E2E 变更：

- 新增 `frontend/tests/e2e/backtest-config-enhanced-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/evaluation-report-enhanced-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/gatei2-fixtures.ts`，通过正式 API 导入本地 fixture bars、创建 dataset、strategy version、research config、backtest config、run 和 evaluation，不依赖外网交易所。
- 更新 `frontend/tests/e2e/support.ts`，按账户 alias 解析真实 `exchangeAccountId`，避免本地自增 ID 漂移导致登录前置失败。
- 本地验证库补入 E2E legacy strategy account 种子 `accounts.account_id=3001`，用于满足既有 `strategy_definitions.account_id` 外键；该操作不是 migration，不进入产品数据结构。

GateI-2 E2E 已覆盖：

- `/backtests` 页面展示 strategy version / dataset 追溯信息。
- 回测配置详情展示 strategy version snapshot、param snapshot、dataset snapshot、config snapshot。
- 回测运行详情展示 run 级 strategy version snapshot、dataset snapshot、param snapshot、config snapshot。
- `/evaluations` 页面展示 total return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON。
- 无数据时页面保留明确 empty 状态。

GateI-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未配置 `E2E_TRADE_ORDER_ID`，既有交易订单详情 E2E 仍按明确原因 skip；不影响 GateI-2 主链。

GateI-2 边界确认：

- 未进入 GateI-3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增 SIM/Paper Trading 运行闭环。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateI-3-WO 验证记录

日期：2026-05-19

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateI-3 Flyway V21 编译通过；新增 `PaperTradingRunServiceTest` 4 个用例覆盖创建、启动、停止、状态拒绝；既有 35 个 nq-app suite 测试全通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |

GateI-3 E2E 说明：

- 新增 `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`，覆盖：Paper Trading 页面打开、列表查询、创建 Paper run、启动 Paper run、停止 Paper run、查看 orders/trades/positions 空态、查看快照标签。
- 新增 `frontend/tests/e2e/paper-trading-fixtures.ts`，通过正式 API 完整链路准备 fixture：fixture bars 导入 → strategy → strategy version → research config → backtest config → strategy version 绑定 → backtest run → start → evaluate → publish；最终返回可用的 `publishId`。
- E2E 不依赖外网交易所；不调用真实 LIVE 下单接口。
- E2E 需要后端 local profile 启动且 Flyway 到 V21；本轮提交前未在干净本地 5432 实例上执行该完整 E2E（具体执行需要先启动后端、确保 fixture 账户种子 3001 存在）。

GateI-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未在本轮启动后端 local profile 并执行 `npm run test:e2e`；E2E spec 已就绪，等待 GateI-3-FIX 或下次完整本地验证窗口执行。

GateI-3 边界确认：

- 未进入 GateI-4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未调用真实交易所下单接口。

## GateI-3-FIX 验证记录

日期：2026-05-20

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests，0 failures |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway V21 已应用 |
| `npm run test:e2e` | 通过 | 18 passed / 1 skipped |

GateI-3-FIX 修复内容：

- `paper-trading-run-smoke.spec.ts`：`getByLabel('发布 ID')` → `getByPlaceholder('发布记录 ID（publishId）')`，修复 Ant Design Form.Item label 关联问题。
- `paper-trading-run-smoke.spec.ts`：Modal OK 按钮从 `getByRole('button', {name: '确 定'})` → `getByRole('button', {name: 'OK', exact: true})`，修复无中文 locale 时按钮文本为 "OK" 且与 "OKX" 冲突。
- `paper-trading-run-smoke.spec.ts`：移除 `waitForResponse` 对 GET 列表刷新的显式等待，改用 `await expect(row).toBeVisible({timeout: 15_000})` 等待 UI 更新。
- `paper-trading-run-smoke.spec.ts`：Drawer 内断言从 `page.getByText('Paper Run ID')` → `page.getByLabel('Paper Trading 详情').getByText('Paper Run ID')`，避免与表头重复元素冲突。
- `paper-trading-run-smoke.spec.ts`：按钮选择器使用 `.or()` 兼容 `getByRole('link')` 和 `getByRole('button')`，适配 Ant Design Table 内 `type="link"` 按钮的实际 role。

GateI-3-FIX E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run（POST /api/paper-trading/runs 返回 CREATED + 快照绑定）。
- 可启动 Paper run（POST .../start 返回 RUNNING）。
- 可停止 Paper run（POST .../stop 返回 STOPPED）。
- 详情抽屉可打开，展示 Paper Run ID、状态、快照。
- 订单/成交/持仓标签页展示明确空态。
- 快照标签页展示 Publish Snapshot 和 Strategy Version Snapshot。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。
- 使用本地 account_id=3001 种子。

GateI-3-FIX skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI-3 主链。

GateI-3-FIX 结论：

- GateI-3-WO + GateI-3-FIX 已完成。
- 后端测试通过、前端 build 通过、E2E 18 passed / 1 skipped。
- 允许进入 GateI-4-WO，但只能在本轮变更审查/提交后单独开工。
- GateI-4 只能做风控回写、资金曲线、持仓曲线、交易复盘与异常停机，不能夹带 AI。

## GateI-4-WO 验证记录

日期：2026-05-20

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；35 tests / 0 failures，含 PaperTradingMonitorServiceTest 5 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `npm run test:e2e` | 未执行 | 本轮未启动本地后端 local profile；spec 已扩展，等待 GateI-4-FIX 窗口执行 |

GateI-4 新增测试覆盖：

- `PaperTradingMonitorServiceTest`：5 个用例覆盖 runRiskCheckOnce 正常写入、listRiskResults 空态、emergencyStop APPLIED（RUNNING → STOPPED）、emergencyStop FAILED（非 RUNNING）、listEmergencyStops 空态。
- E2E spec 已扩展 GateI-4 链路（风控检查 / 5 个新 Tab / 紧急停机），待本地后端启动后执行。

GateI-4 skipped 说明：

- E2E 未执行：本轮未启动本地后端 local profile + Flyway V22，spec 已就绪。

GateI-4 结论：

- 后端测试通过、前端 build 通过。
- E2E 待 GateI-4-FIX 窗口执行。
- GateI 仍未整体完成；不创建 `docs/gates/gate-i`。

## GateI-4-FIX 验证记录

日期：2026-05-21

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，35 tests / 0 failures |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `22` |
| 5 张 GateI-4 表存在 | 通过 | `paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events` 全部存在 |
| `npm run test:e2e` | 通过 | 19 passed / 1 skipped；新增 GateI-4 monitor smoke 用例通过 |

GateI-4-FIX 修复内容：

- 改 GateI-4 E2E 用例：从 `request` fixture 调用 API（不共享 token）改为通过 UI 操作完成全链路。
- 改 PaperTradingPage：将"执行风控检查"和"紧急停机"按钮从 `PaperListSection` children 移到外层（空态时仍可见）。
- 改 Modal 调用方式：`Modal.confirm` → `App.useApp().modal.confirm`，确保在 App context 下正确渲染。
- 修复 PASSED 文本断言：使用 `.first()` 避免多元素冲突。

GateI-4-FIX skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI 主链。

GateI-4-FIX 结论：

- GateI-4-WO + GateI-4-FIX 已完成。
- GateI 全部子阶段已完成：GateI-1-WO → GateI-2-WO → GateI-3-WO → GateI-3-FIX → GateI-4-WO → GateI-4-FIX。
- **GateI completed。**
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- GateJ 不是 AI 阶段；GateK-PLAN 不启动 AI，AI 相关工作仍需后续另起 Gate / review。

## GateJ-PLAN 验证记录

日期：2026-05-21

本轮只修改文档，不重新执行全量后端、前端、Python 测试。

沿用 GateI completed 验证基线：

- 后端 `mvn -f backend/pom.xml test`：35 tests / 0 failures。
- 前端 `npm run build`：通过。
- E2E `npm run test:e2e`：19 passed / 1 skipped。
- Python `pytest`、`mypy`、`ruff`：通过。

本轮只改文档，未跑全量测试原因：无业务代码变更、无 migration 变更、无 API 变更、无前端页面变更。

GateJ 测试规划入口为 [GATEJ_TEST_PLAN.md](./GATEJ_TEST_PLAN.md)。

GateJ 规划 E2E 矩阵：

- paper-schedule-smoke
- paper-heartbeat-smoke
- paper-daily-report-smoke
- paper-alert-smoke
- paper-recovery-smoke
- paper-stability-check-smoke

GateJ 规划连续运行验收：

- 1 小时短验收
- 24 小时中验收
- 7 天稳定性验收

## GateJ-1-WO 验证记录

日期：2026-05-21

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests / 0 failures；含 PaperRunScheduleServiceTest 11 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `23` |
| `npm run test:e2e` | 通过 | 20 passed / 1 skipped；新增 paper-trading-schedule-smoke 通过 |

GateJ-1 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 调度计划 Tab 可展示空态。
- 可创建调度计划（ENABLED 状态）。
- 可执行一次调度（run-once），fire 记录为 SUCCEEDED。
- 可查看触发记录。
- 可禁用调度（DISABLED）。
- 心跳 Tab 可展示空态。
- 可执行心跳检查（run-once），heartbeat 状态为 OK。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。

GateJ-1 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-1 主链。

GateJ-1 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。

GateJ-1 边界确认：

- 未进入 GateJ-2/3/FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增日报、告警、恢复、稳定性验收。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateJ-2-WO 验证（2026-05-21）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests / 0 failures；含 PaperRunMonitorServiceTest 12 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `24` |
| `npm run test:e2e` | 通过 | 22 passed / 1 skipped；新增 paper-trading-daily-report-smoke / paper-trading-alert-smoke 通过 |

GateJ-2 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 日报 Tab 可展示空态。
- 可生成今日日报（status = GENERATED）。
- 可重复生成同一日期日报（幂等）。
- 告警 Tab 可展示空态。
- 可创建测试告警（SYSTEM_NOTICE / LOW / OPEN）。
- 可确认告警（OPEN → ACKED，acknowledgedBy 写入）。
- 可解决告警（ACKED → RESOLVED，resolvedAt 写入）。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。

GateJ-2 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-2 主链。

GateJ-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning。

GateJ-2 边界确认：

- 未进入 GateJ-3 / GateJ-FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增恢复、稳定性验收、外部通知（邮件、Slack、钉钉）。
- 未引入图表库。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateJ-3-WO 验证（2026-05-22）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS；新增 PaperRunRecoveryServiceTest 9 用例、PaperRunStabilityCheckServiceTest 10 用例、PaperRunMonitorRunServiceTest 8 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `25` |
| `npm run test:e2e` | 通过 | 24 passed / 1 skipped；新增 paper-trading-recovery-smoke / paper-trading-stability-check-smoke 通过 |

GateJ-3 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 恢复事件 Tab 可展示空态。
- 可执行恢复（MANUAL_RECOVER），写入 recovery event。
- 可执行重试失败步骤（RETRY_FAILED_STEP），写入 recovery event。
- 可执行监控守护一次（HEARTBEAT_LAG 自动告警最小落库）。
- 告警 Tab 可看到 HEARTBEAT_LAG 自动告警。
- 稳定性验收 Tab 可展示空态。
- 可生成最近 24h 稳定性验收（无心跳 → FAILED，验证第一版口径）。
- 同窗口重复生成幂等。
- 不依赖外网交易所，不调用真实 LIVE 下单。

GateJ-3 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-3 主链。

GateJ-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning。
- 未执行 GateJ-FREEZE 的 1h/24h/7d 连续运行验收（属 GateJ-FREEZE 范围）。

GateJ-3 边界确认：

- 未进入 GateJ-FREEZE 正式验收归档。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）。
- 未做自动恢复策略引擎。
- 未调用真实 LIVE 下单接口。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未引入图表库。

## PRE-FREEZE-CODE-AUDIT 验证记录（2026-05-22）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 docs/current 与根目录入口文档变更，无业务代码、migration、API 实现、前端页面实现变更 |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，0 failures / 0 errors（archunit ModuleBoundaryArchTest 6 用例 + PackageBoundaryArchTest 1 用例通过；nq-app suite 35 全通过；Paper 单元测试 PaperTradingRunService 4 + PaperTradingMonitorService 5 + PaperRunScheduleService 11 + PaperRunMonitorService 12 + PaperRunRecoveryService 9 + PaperRunStabilityCheckService 10 + PaperRunMonitorRunService 8 全部通过）|
| `npm run build` | 通过 | `tsc -b && vite build` 成功；dist/index.js ≈ 1.48 MB（gzip 446 kB）；仍有 chunk > 500 kB 警告 |
| `npm run test:e2e` | 本轮未实际执行 | 沿用 GateJ-3-WO 24 passed / 1 skipped 通过基线；P1-1 要求 GateJ-FREEZE 入场前补跑（启动后端 local profile + 5432 + 种子 `account_id=3001` 后执行）|
| `python -m pytest -q` | 本轮未实际执行 | 当前 shell `python.exe` 仅 Windows App Execution Alias stub，调用 exit 49；沿用 BASELINE-FIX-2 / GateJ-3 通过基线；P1-2 要求 GateJ-FREEZE 入场前在真实 Python 环境补跑 |
| `python -m mypy src` | 本轮未实际执行 | 同上；P1-2 |
| `python -m ruff check .` | 本轮未实际执行 | 同上；P1-2 |

未跑验证不写成通过：本轮未执行的 E2E 与 Python 三件套均明确标记为「未在本轮重跑」，并通过 PRE_FREEZE_AUDIT_FIX_PLAN.md P1-1 / P1-2 列入 GateJ-FREEZE 入场前必做项。

PRE-FREEZE-CODE-AUDIT 结论：

- 后端单元测试全部通过；前端 build 通过。
- 文档、代码、DB、API、前端、E2E spec、Python 模块、Paper/LIVE 隔离、AI 边界、模块边界一致。
- 无 P0 阻塞性问题。
- P1 共 4 条：P1-1 / P1-2 是 GateJ-FREEZE 入场前必做的验证补跑；P1-3 不阻塞；P1-4 已闭环。
- 允许进入 GateJ-FREEZE，但 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## PRE-FREEZE-CODE-AUDIT second pass 验证记录（2026-05-22）

本轮由 Codex 执行二次审查与实际验证。未修业务代码，未新增 API / migration / 前端页面实现，未接 AI，未执行 GateJ-FREEZE 1h/24h/7d 连续运行验收。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` suite `35 tests / 0 failures / 0 errors / 0 skipped`；Paper 相关 service 测试均通过 |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；`dist/assets/index-CLLFLWD4.js` 约 1,478.51 kB（gzip 446.09 kB）；Vite chunk > 500 kB 警告仍存在，作为 P2 |
| `cd frontend && npm run test:e2e` | 通过 | 后端 local profile 启动成功，`/actuator/health` 返回 `UP`，Flyway 当前版本 `25`；完整 Playwright 25 tests total，24 passed / 1 skipped / 0 failed |
| `cd research/py && python -m pytest -q` | 通过 | 使用真实 Python 解释器执行；`2 passed in 0.03s` |
| `cd research/py && python -m mypy src` | 通过 | `Success: no issues found in 8 source files` |
| `cd research/py && python -m ruff check .` | 通过 | `All checks passed!` |

E2E skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有订单详情链路，不影响 GateJ 主链。
- GateJ 主链 smoke 已全部执行并通过：schedule/heartbeat、daily report、alert、recovery、stability check、monitor run-once。

环境说明：

- 默认 shell `python` 指向 `C:\Users\lingy\AppData\Local\Microsoft\WindowsApps\python.exe`，不是可用解释器；本轮使用 workspace bundled Python 临时置于 `PATH` 首位后执行同样的 `python -m ...` 命令。
- 首次 E2E 启动后端时遇到 Maven 本地仓库目录冲突；提权重跑后该问题消失。随后一次 PowerShell 参数引用错误导致 Maven 将 profile 参数误识别为 lifecycle phase；修正引用后后端启动与完整 E2E 均通过。上述两次失败未进入业务 E2E 断言，不计为业务功能失败。

PRE-FREEZE-CODE-AUDIT second pass 结论：

- 后端、前端 build、完整 E2E、Python pytest/mypy/ruff 均已实际执行并通过。
- API / DB / Paper-LIVE 隔离 / AI 边界二次抽查未发现 P0/P1。
- 允许进入 GateJ-FREEZE，但 GateJ-FREEZE 必须单独开工，只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## AUDIT-FIX 验证记录（2026-05-26）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 AUDIT-FIX 范围文件变更，外加上一轮新增安全审查报告 |
| `git diff --stat` | 已执行 | 用于确认变更范围 |
| `git diff -- scripts/gated_okx_dome_verify.ps1 docs/archive/scripts/gated_okx_dome_verify.ps1 frontend/playwright.config.ts frontend/tests/e2e/run-e2e.mjs docs/current/API.md docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md` | 已执行 | 用于确认 P1 stub / 归档、E2E 端口与文档事实源变更 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 chunk > 500 kB 警告 |
| `cd frontend && npm run test:e2e` | 通过 | 首次在后端未启动时失败于 `127.0.0.1:18888 ECONNREFUSED`；启动后端 local profile 后复跑通过，Vite 监听 `127.0.0.1:5179`，结果 24 passed / 1 skipped / 0 failed |

端口修复说明：

- `4173` 位于当前 Windows TCP excluded range `4141-4240` 内，会导致 Vite 监听 `127.0.0.1:4173` 返回 `EACCES`。
- E2E/Vite 端口统一调整为 `5179`，Playwright `baseURL`、run-e2e 启动参数、Vite dev / preview 默认端口和 `.env.example` 保持一致。
- 唯一 skipped 用例仍为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，不影响 GateJ 主链。

## GateJ-FREEZE-FIX 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 Vite chunk > 500 kB 警告 |
| `rg -n "<redacted-local-test-password>\|18888\|legacy console gate\|/api/auth/login\|<redacted-authorization-header-prefix>" frontend/dist` | 通过 | 无命中；`rg` 返回 1 表示未找到匹配项 |
| `rg -n "/api/auth/me" frontend/dist` | 通过 | 无命中；额外确认登录页不再暴露当前用户接口路径 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 首次因沙箱无法写入本机 Maven repository tracking file 失败；提权重跑成功，生成 `release/nq-gatej-freeze-release.zip` |
| `jar tf backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar \| Select-String application-freeze.yml` | 通过 | jar 内包含 `BOOT-INF/classes/application-freeze.yml` |

脚本语法说明：

- 当前 Windows 环境只有 `C:\WINDOWS\system32\bash.exe`，调用 `bash -n` 会进入 WSL 未安装提示，未能在本机执行 bash 语法检查。
- `seed-freeze-user.sh` 已通过文本审查、release 包纳入检查和服务器执行流程文档约束；最终 shell 运行需在 Linux ECS 上随重新部署验证。

本轮未执行：

- 未重新执行 `npm run test:e2e`：本轮改动限定在登录页展示、freeze profile、部署脚本与 freeze 文档；按任务验收要求执行了后端测试、前端 build、dist 敏感串扫描和 release 打包。
- 未执行 Python `pytest/mypy/ruff`：本轮未修改 `research/py`。

## GateJ-FREEZE-FIX-SECOND-PASS 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含 GateJ-FREEZE-FIX 与本轮 second pass 文档/注释/测试描述清理；未提交 release/dist/env/jar/zip/dump/log/evidence |
| 源码敏感词扫描 | 已执行 | 阻塞残留已修复；剩余命中均为允许项或历史文档记录，详见 `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md` |
| `rg ... frontend/dist` | 通过 | 无敏感/旧联调关键词命中 |
| release zip 解压后 `rg ... release/second-pass-scan` | 通过 | 除允许的 `18888` 部署端口配置外，无敏感/旧联调关键词命中 |
| `.gitignore` 检查 | 通过 | release/dist/target/env/log/dump/evidence 已覆盖 |
| `git ls-files` 污染检查 | 通过 | 未发现不该追踪的 release/dist/env/jar/zip/dump/log/evidence |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip` |

## GateJ-FREEZE-FIX-3 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 指向未安装 Linux 发行版的 WSL stub；本机未安装 Git Bash，Docker daemon 也未运行，无法获得可用 Bash。脚本已按 Bash 语法静态审查，需在 Linux ECS 或可用 Bash 环境复跑该命令。 |
 | `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
 | `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |

| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`，release info 已包含禁止 `source .env.freeze` 与交互式 seed 密码说明。 |

GateJ-FREEZE-FIX-3 变更限定在 seed 脚本、freeze env 模板、freeze 部署文档、release info 和当前事实源文档；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

## GateJ-FREEZE-FIX-4 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 仍指向未安装 Linux 发行版的 WSL stub；本机未安装 Git Bash，Docker daemon 未运行，无法获得可用 Bash。ECS 或可用 Bash 环境必须复跑。 |
| `unset NQ_FREEZE_ADMIN_PASSWORD` 后交互式执行 seed | 待 ECS 复验 | 本轮修复点是 `read -s -p` 后的视觉换行改写 stderr，避免命令替换捕获换行并误判多行；需在 Linux ECS 上用真实 TTY 复验。 |
| 进程环境方式执行 seed | 待 ECS 复验 | 当前本机无运行中的 freeze PostgreSQL 容器，需在 ECS 上复验。 |
| `hash_prefix` 为 `$2a$` 或 `$2b$` | 待 ECS 复验 | 需在 ECS PostgreSQL 容器内查询，禁止输出完整 hash。 |
| `curl` 登录 200 且不打印 token | 待 ECS 复验 | 需在 ECS 本机验证并只输出 HTTP status。 |

ECS 建议复验命令：

```bash
cd /opt/nexus-quant
bash -n scripts/seed-freeze-user.sh

unset NQ_FREEZE_ADMIN_PASSWORD
# 确保 .env.freeze 中 NQ_FREEZE_ADMIN_PASSWORD 缺失、注释或保留 CHANGE_ME 占位符，再交互式输入验收密码。
bash scripts/seed-freeze-user.sh

NQ_FREEZE_ADMIN_PASSWORD='<single-line-password>' bash scripts/seed-freeze-user.sh

docker compose --env-file .env.freeze -f docker-compose.freeze.yml exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT substring(password_hash from 1 for 4) AS hash_prefix FROM users WHERE username = '${NQ_FREEZE_ADMIN_USERNAME}' AND enabled = TRUE;"

status="$(
  curl -sS -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"${NQ_FREEZE_ADMIN_USERNAME}\",\"password\":\"<single-line-password>\"}" \
    'http://127.0.0.1:18888/api/auth/login'
)"
test "$status" = "200"
```

本轮本地可验证项：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |

| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |

## GateJ-FREEZE-FIX-5 验证记录（2026-05-29）

本轮修复 release 包内 `.sh` CRLF 换行导致 ECS Bash 解析 `set -euo pipefail` 失败的问题。修复范围限定在换行策略、release 打包脚本和当前事实源文档；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| 仓库 `scripts/*.sh` CRLF 字节检查 | 通过 | `backup-db.sh`、`deploy-freeze.sh`、`freeze-health-loop.sh`、`health-check.sh`、`seed-freeze-user.sh` 均为 `HasCRLF=False`。 |
| `git diff --check` | 通过 | 无空白错误；仅有 `build-freeze-release.ps1` 将按 `.gitattributes` 维持 CRLF 的 Git 提示。 |
| `mvn -f backend/pom.xml test` | 通过 | 首次 120s 超时未得出测试失败结论；提高超时后复跑通过，Reactor `BUILD SUCCESS`，23 个 backend module `SUCCESS`，`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`；打包脚本在 zip 前对 staging `scripts/*.sh` 做 LF 归一化兜底。 |
| release zip 解压后 CRLF 检查 | 通过 | 解压到本机临时目录后，zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`30,979,533` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
bash scripts/backup-db.sh before-freeze
nohup bash scripts/freeze-health-loop.sh > /opt/nexus-quant/freeze-evidence/health/freeze-health-loop.out 2>&1 &
grep -n '"status":"UP"\|UP' /opt/nexus-quant/freeze-evidence/health/health-check-7d.log | tail
```

结论：本地 release 可复现性已修复；ECS 尚未在本轮环境执行，未完成 ECS 复验前不得进入 GateJ-FREEZE 首次启动验收。

## GateJ-FREEZE-FIX-6 验证记录（2026-05-29）

本轮修复 ECS freeze 控制台点击 Instrument Catalog “同步 Catalog”后因 Binance `exchangeInfo` 返回 451 被抛成 500 的问题，并清理生产/freeze 可见页面中的旧阶段与本地环境文案。修复范围限定在 freeze 验收阻塞问题；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-api,nq-scheduler -am test` | 通过 | 覆盖 `/api/instruments/sync` 409 受控错误与 `AdapterInstrumentCatalogSyncService` 禁用/外部异常转换测试。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |
| `frontend/dist` 禁止串扫描 | 通过 | 未命中 `GateG`、`GateH-PRE`、`ChangeMe123`、`admin / ChangeMe123`、`/api/auth/login`、`/api/auth/me`、`Authorization: Bearer`。 |
| release zip 解压后禁止串扫描 | 通过 | 解压目录未命中上述禁止串。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`30,980,280` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml restart nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
# 浏览器进入 Instrument Catalog：查询允许为空；点击同步 Catalog 不得显示 internal server error。
# 后端日志不得出现：api_unhandled_exception path=/api/instruments/sync
```

结论：本地已修复 freeze release 中 Instrument Catalog sync 的 500 风险与前端旧文案残留；ECS 尚未在本轮环境执行，未完成 ECS 复验前不得进入 GateJ-FREEZE 首次启动验收。

## GateJ-FREEZE-FIX-7 验证记录（2026-05-29）

本轮修复 freeze 控制台旧 Gate 文案、开发接口说明和不专业筛选控件。修复范围限定在前端 UI 展示与筛选控件；未新增 API、migration 或后端业务流程，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd frontend && npm run build` | 通过 | 首次因 `PaperTradingPage` 漏加 `Select` import 失败，补齐后通过；仍有既有 Vite chunk > 500 kB 警告。 |
| `frontend/dist` 残留扫描 | 通过 | 大小写敏感扫描未命中 `GateH-1`、`GateH-2`、`GateI-3`、`GateH-PRE`、`GateG`、`LOCAL`、`Gate-3`、`GET /api`、`POST /api`、`publishId 过滤`、`本地筛选字段`、`真实请求参数`。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 首次因沙箱无法写入本机 Maven repository tracking file 失败；提权重跑通过并重新生成 release zip。 |
| release zip 解压后 frontend/dist 残留扫描 | 通过 | 解压目录 `frontend/dist` 未命中上述旧 Gate / LOCAL / 开发接口说明残留。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`31,014,538` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml up -d --force-recreate nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
curl -fsS http://127.0.0.1:5179/actuator/health
```

浏览器复验：

- 页面不再出现旧 Gate / LOCAL / API 开发说明残留。
- 重点页面枚举筛选项为 Select，时间字段为 DatePicker。
- Instrument Catalog “同步 Catalog” 仍显示受控提示，不显示 internal server error。
- 后端日志不得出现 `ERROR` / `Exception` / `api_unhandled_exception path=/api/instruments/sync`。

结论：本地 release 已可上传 ECS 复验；ECS 浏览器与日志复验通过前不得进入 GateJ-FREEZE 首次启动验收。

## Credential Revocation Governance Batch 5-C 验证记录（2026-06-07）

本轮接入 credential lifecycle 最小后端能力：`credential_status` 读取、`revoke / disable / expire` command API、active material 生命周期过滤和 append-only audit log 写入。未新增 migration、前端、Python、部署、AI、DH、LIVE 或真实交易所私有链路。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 失败后已修复 | 首次失败点为 `ExchangeAccountCredentialControllerWebMvcTest` 中 `Instant` 在 standalone MockMvc 下输出 epoch seconds；补齐 Jackson Java time converter 后不再复现。 |
| `mvn -f backend/pom.xml -pl nq-api -am test` | 通过 | 覆盖 Credential API WebMvc 测试和 API 依赖模块。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api -am test` | 通过 | 覆盖 Service lifecycle 流转、JDBC SQL、API command endpoint、active material 过滤和敏感字段缺失断言。 |

最终收口验证：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |

## NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-IMPLEMENTATION 验证记录（2026-06-13）

本轮实现 V31 permission probe 最小后端 code/API/test 能力，默认 no-real-exchange port 返回 `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`，不访问 OKX/Binance 或其他真实交易所；未新增 migration、前端、Python、部署脚本、AI、DH、LIVE 或真实交易路径。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-core -am -Dtest=CredentialPermissionProbeServiceTest test` | 失败，非代码失败 | Reactor 前置模块没有匹配测试，Surefire 将 no matching tests 视为失败。 |
| `mvn -f backend/pom.xml -pl nq-core -am -Dtest=CredentialPermissionProbeServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test` | 通过 | `CredentialPermissionProbeServiceTest` 9 tests / 0 failures / 0 errors；覆盖 LIVE/inactive/non-ACTIVE/Paper gate/withdraw risk、STARTED/SUCCEEDED/FAILED/SKIPPED audit、failed_auth_count 策略、scope null、IN_PROGRESS 并发和 latest no-port。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app,nq-adapter-okx,nq-adapter-binance -am test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `git diff -- backend/nq-infra/src/main/resources/db/migration` | 通过 | 输出为空，未新增/修改 migration。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改部署/脚本。 |

边界扫描：

- 阶段措辞扫描通过：未新增 GateK implementation started / AI started / DH integrated / LIVE enabled 的正向语义；命中项均为 `not started`、`not integrated`、`disabled` 或禁止说明。
- Permission probe 相关 surefire reports 未命中 `www.okx.com` / `api.binance.com`。
- 全量 surefire reports 未命中 `No route to host`、`ConnectException`、`UnknownHostException`、`request failed`、真实 endpoint 请求或 `api.binance.com`。
- 全量 `nq-app` surefire reports 仍包含既有 OKX adapter 配置摘要 `baseUrl=https://www.okx.com`，这是 local profile fingerprint，不是本轮 permission probe 访问证据。

## NQ-GATEK-PLAN 验证记录（2026-06-14）

本轮是 docs-only planning：只新增 / 同步 GateK-PLAN 文档和 current facts 入口，不修改 backend、frontend、research、scripts、deploy、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以文档边界、Git diff 和阶段措辞检查为准。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出既有 LF/CRLF 工作区提示，无 whitespace error。 |
| `git diff --stat` | 已检查 | 仅文档变更；新增文件通过 `git status --short` 确认。 |
| `git status --short` | 已检查 | 仅允许文档范围内变更和新增 `docs/current/GATEK_PLAN.md`。 |
| `git diff -- backend` | 通过 | 输出为空，未改后端。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改脚本。 |
| `git diff -- deploy` | 通过 | 输出为空，未改部署目录。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |

阶段与安全边界：

- GateK 只写为 planning / architecture / productization / deployment / observability / security boundary stage。
- GateK implementation 明确为 not started。
- AI 明确为 not started。
- DH 明确为 not integrated / not connected to NQ；Integration-0 只作为 contract / mock / docs / contract test line。
- LIVE 明确为 disabled。
- 未读取、打印、复制或输出 credential material、`.env`、`*.key`、`*.pem`、`*.log`。

## GATEK-PLAN-FREEZE-REVIEW 验证记录（2026-06-14）

本轮是 docs-only freeze review：只审查和修正 GateK-PLAN 与入口事实源文档，不修改 backend、frontend、research、scripts、deploy、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以文档边界、Git diff、forbidden-area diff、阶段措辞和敏感信息扫描为准。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已检查 | 仅允许文档范围内变更。 |
| `git diff --check` | 通过 | 无 whitespace error；如出现 LF/CRLF 提示，按既有 Windows 工作区提示处理。 |
| `git diff --stat` | 已检查 | 仅文档变更。 |
| `git diff -- backend` | 通过 | 输出为空，未改后端。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改脚本。 |
| `git diff -- deploy` | 通过 | 输出为空，未改部署目录。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `rg ".env|.key|.pem|private key|api secret|passphrase|mnemonic|password" README.md AGENTS.md CLAUDE.md docs/current` | 已检查 | 命中项仅允许为否定式、禁止说明、字段名、占位符或历史脱敏说明；不得包含真实 credential material。 |

阶段与安全边界：

- GateK-PLAN 明确为 planning / architecture / productization / deployment / observability / security boundary stage。
- GateK implementation 明确为 not started。
- AI 明确为 not started，GateK-PLAN 不启动 AI 信号、AI runtime 或 AI Paper Trading。
- DH 明确为 not integrated / not connected to NQ；Integration-0 只作为 contract / mock / docs / contract test line。
- LIVE 明确为 disabled。
- 真实 OKX/Binance permission probe adapter 明确为 not implemented。

## GATEK-ARCH-DOC-SYNC 验证记录（2026-06-14）

本轮是 docs-only architecture wording sync：只同步 `docs/current/ARCHITECTURE.md`、`docs/current/MODULES.md`、`docs/current/README.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`，不修改 backend、frontend、research、scripts、deploy、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以文档边界、Git diff、forbidden-area diff 和阶段措辞扫描为准。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已检查 | 本轮修改 5 个允许文档；工作区另有非本轮的 `docs/current/frontend/**` staged / modified 文件。 |
| `git diff --check` | 通过 | 仅出现既有 Windows LF/CRLF 提示，无 whitespace error。 |
| `git diff --stat` | 已检查 | 包含本轮 5 个允许文档；另显示非本轮的 `docs/current/frontend/NQ_DESIGN_TOKENS_V2.md` 与 `docs/current/frontend/NQ_FRONTEND_BUILD_MATRIX.md`。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 top-level frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `rg "GateK implementation|AI started|DH integrated|LIVE enabled|real adapter implemented" README.md AGENTS.md CLAUDE.md docs/current` | 已检查 | 命中项均为 not started / disabled / not integrated / not implemented / 禁止说明 / 历史语境，未发现正向误写。 |
| `rg "GateH|Gate I|GateJ|GateK|V1" docs/current/ARCHITECTURE.md docs/current/MODULES.md` | 已检查 | GateH / V1 均为 previous completed phase / archived history 或 GateI/GateJ completed 语境。 |

阶段与安全边界：

- GateK-PLAN 明确为 planning baseline，不是 GateK implementation started。
- AI 明确为 not started。
- DH runtime 明确为 not integrated / not connected to NQ。
- LIVE 明确为 disabled。
- 真实 OKX/Binance permission probe adapter 明确为 not implemented。

## NQ-CI-BASELINE-PLAN 验证记录（2026-06-14）

本轮是 CI planning-only / docs-only：只新增 `docs/current/NQ_CI_BASELINE_PLAN.md` 并同步 current docs 入口，不创建 `.github/workflows/**`，不修改 backend、frontend、research、scripts、deploy、测试代码、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；这些命令只被规划为后续 `NQ-CI-BASELINE-IMPL` 的 CI baseline。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已检查 | 编辑前为空；编辑后仅允许 docs/current 文档变更。 |
| `git diff --check` | 通过 | 编辑前通过；编辑后复跑，若出现 LF/CRLF 提示按既有 Windows 工作区提示处理，不能写成 whitespace failure。 |
| `git diff --stat` | 已检查 | 用于确认 diff 只覆盖 docs/current 文档。 |
| `git ls-files .github` | 已检查 | 当前 tracked `.github` 只有 `.github/CODEOWNERS` 与 `.github/pull_request_template.md`；无 tracked workflow。 |
| `git ls-files backend/frontend/research \| head` | 原命令失败 | PowerShell 环境无 `head`；已用 `Select-Object -First 20` 等价复跑。 |
| `git ls-files backend/frontend/research \| Select-Object -First 20` | 已检查 | 确认 backend、frontend、research tracked 结构入口。 |
| `rg "name:|on:|jobs:" .github docs/current README.md` | 已检查 | 未发现 `.github/workflows` job 定义；命中主要来自文档模板和计划文本。 |
| CI baseline keyword scan | 已检查 | 用排除 `frontend/node_modules`、`target`、`build`、`dist` 的 `rg` 复跑，确认 Maven/npm/E2E/Python/Flyway/PostgreSQL/no-outbound/LIVE/NoReal 当前事实。 |
| 禁止范围 diff 检查 | 已检查 | `backend`、`frontend`、`research`、`scripts`、`deploy`、`.github`、`backend/**/db/migration` 均要求输出为空。 |

阶段与安全边界：

- NQ CI baseline 只写为 plan，不写成 implemented。
- `.github/workflows/**` 未创建。
- GateK implementation 明确为 not started。
- AI 明确为 not started。
- DH runtime 明确为 not integrated / not connected to NQ。
- LIVE 明确为 disabled。
- real exchange permission probe adapter 明确为 not implemented。
- 本轮未读取或输出真实 credential material。

## NQ-CI-BASELINE-IMPL 验证记录（2026-06-14）

本轮是 GateK CI baseline Batch 1 implementation：只新增 `.github/workflows/ci.yml`，并同步 `docs/current` 文档。Batch 1 只覆盖 GitHub Actions 最小 baseline：diff check、backend Maven test、frontend `npm ci` + build、research pytest / mypy / ruff。未实现 PostgreSQL/Flyway hardening、no-outbound guard、gitleaks / secret scan、dependency audit、frontend E2E hardening；未修改 backend、frontend、research、scripts、deploy、测试代码、API 或 migration。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| Workflow file path | 已新增 | `.github/workflows/ci.yml`。 |
| Workflow jobs | 已配置 | `diff-check`、`backend`、`frontend`、`research`；research job 对 mypy / ruff 使用 cache-independent flags，避免本地 cache 权限影响检查结论。 |
| GitHub Actions first run | Pending | 本地无法实际触发 GitHub Actions；需 push 或 PR 到 `dev` 后观察首次 `NQ CI Baseline` run。 |
| `git status --short` | 已检查 | 只允许 `.github/workflows/` untracked 与 `docs/current/NQ_CI_BASELINE_PLAN.md`、`docs/current/README.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md` 变更。 |
| `git diff --check` | 通过 | 退出码 0；仅出现 Windows LF/CRLF 工作区提示，不视为 whitespace failure。 |
| `git diff --stat` | 已检查 | tracked diff 只覆盖 4 个 docs/current 文档；`.github/workflows/ci.yml` 是新增 untracked 文件，需由 `git status --short` 确认。 |
| `git ls-files .github` | 已检查 | tracked `.github` 仍只有 `.github/CODEOWNERS` 与 `.github/pull_request_template.md`；新增 workflow 尚未 staged。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| Forbidden keyword scan | 已检查 | `rg "skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current/NQ_CI_BASELINE_PLAN.md docs/current/TESTING.md docs/current/WORKLOG.md`：workflow 无命中；docs 命中均为禁止项、pending 风险、历史记录或安全边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor 23 modules `SUCCESS`，`BUILD SUCCESS`；未使用 `skipTests`。 |
| `npm ci` | 通过 | 在 `frontend` 下执行，依赖安装成功。 |
| `npm run build` | 通过 | 在 `frontend` 下执行，Vite build 成功；仅有 chunk size warning。 |
| `python -m pytest -q` | 通过 | 在 `research/py` 下执行，2 passed。 |
| `python -m mypy src` | 本机默认 cache 失败 | 本机 Python 3.14.2 + mypy 2.1.0 打开 sqlite cache 失败；未写成通过。 |
| `python -m mypy src --no-sqlite-cache` | 通过 | 类型检查本身通过，`Success: no issues found in 8 source files`；workflow 使用该命令，CI 仍需首次 GitHub Actions run 验证 Linux/Python 3.11 环境。 |
| `python -m ruff check .` | 本机 cache 写入失败 | 本机 `.ruff_cache` 临时文件写入被拒绝；未写成通过。 |
| `python -m ruff check . --no-cache` | 通过 | Lint 本身通过，`All checks passed!`；workflow 使用该命令，CI 仍需首次 GitHub Actions run 验证 Linux/Python 3.11 环境。 |

未覆盖项：

- PostgreSQL/Flyway：仍为 Batch 2 pending。
- no-outbound guard implementation：仍为 Batch 3 pending。
- gitleaks / secret scan / dependency audit：仍为 Batch 4 pending。
- frontend E2E hardening：仍为 Batch 5 pending。

安全边界：

- CI workflow 不注入交易所 credential。
- CI workflow 不设置 LIVE enablement。
- CI workflow 不包含真实交易所 diagnostic、order、cancel、transfer、withdraw 或 real adapter job。
- 本轮未读取、打印、复制或输出真实 credential material。

## NQ-CI-BASELINE-FIRST-RUN-FIX 验证记录（2026-06-14）

首次 GitHub Actions run `27496510294` 已触发，`diff-check`、`frontend`、`research` 通过，`backend` job 在 `Run backend tests` step 失败。失败命令为 `mvn -f backend/pom.xml test`，失败 module 为 `nq-app`；失败类包括 `MarketdataControllerLocalIntegrationTest`、`OkxBootstrapNoOutboundLocalContextTest`、`ResearchBacktestHappyPathLocalTest`，均为 `local` profile full Spring context 测试。

Root cause：GitHub runner 没有本地 PostgreSQL，而 `application-local.yml` 默认 datasource 指向 `jdbc:postgresql://localhost:5432/nexus_quant`；本机验证通过依赖本机已有 PostgreSQL。第一次修复在 backend job 增加 ephemeral PostgreSQL service 与对应 `NQ_DB_*` env。第二次 run 中 PostgreSQL 与 Flyway 已可用，但全新 DB 缺少 legacy `accounts` seed，`ResearchBacktestHappyPathLocalTest` 在 `SELECT account_id FROM accounts ORDER BY account_id LIMIT 1` 处失败。因此补充 CI-only seed watcher：在 Flyway 创建 `accounts` 表后插入一条最小 `PAPER / ACTIVE` legacy account。这不是 PostgreSQL/Flyway hardening：未新增 Flyway 专项验证 job，未新增 migration order / schema drift / repeatability 检查，Batch 2 仍 pending。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| Failed CI run summary | 已检查 | Run `27496510294`；failed job `Backend Maven test`；failed step `Run backend tests`；command `mvn -f backend/pom.xml test`。 |
| `gh run view 27496510294` | 已检查 | `diff-check`、`frontend`、`research` 成功；`backend` 失败。 |
| GitHub job logs | 已检查 | GitHub connector 读取 backend job logs；确认 `nq-app` local Spring context tests 因 runner 环境缺 PostgreSQL 失败。 |
| Fix | 已实施 | `.github/workflows/ci.yml` backend job 增加 `postgres:16` service、health check、`NQ_DB_URL` / `NQ_DB_USER` / `NQ_DB_PASSWORD`，并增加 CI-only seed watcher 插入最小 legacy account。 |
| First green run | 已确认 | Fix 已 push；后续 run `27496906788` 已在 `NQ-CI-BASELINE-FIRST-RUN-REVIEW` 中确认四个 job success。 |

边界：

- 未修改 backend / frontend / research 代码。
- 未修改测试代码。
- 未新增 API 或 migration。
- 未修改 scripts / deploy。
- 未加入 no-outbound guard implementation、gitleaks / secret scan、dependency audit 或 frontend E2E hardening。
- 未使用 `skipTests` 或 `continue-on-error`。
- 未注入真实 credential，未开启 LIVE，未调用真实交易所。

## NQ-CI-BASELINE-FIRST-RUN-REVIEW 验证记录（2026-06-14）

本轮只评审 `NQ CI Baseline` 首次 green run，不修改 workflow、backend、frontend、research、测试代码、API、migration、scripts 或 deploy。GitHub Actions run `27496906788` 已由 GitHub connector 复核，四个 Batch 1 job 均为 `completed / success`。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run `27496906788` | 通过 | `Diff check`、`Backend Maven test`、`Frontend build`、`Research quality gate` 全部 success。 |
| Workflow scope review | 通过 | `.github/workflows/ci.yml` 只包含 Batch 1：diff check、backend Maven test、frontend build、research quality gate；未加入 PostgreSQL/Flyway hardening、no-outbound guard、secret scan、dependency audit 或 frontend E2E hardening。 |
| Backend job review | 通过 | 保留 `mvn -f backend/pom.xml test`；未使用 `-DskipTests`；未使用 `continue-on-error`；CI-only seed watcher 只等待 Flyway 创建 `accounts` 表并插入最小 `PAPER / ACTIVE` legacy account，不进入生产代码、migration 或 runtime seed 逻辑。 |
| Frontend job review | 通过 | 执行 `npm ci` 与 `npm run build`；未触碰 frontend B0 Draft PR、B1/B2/B3 页面施工或 AppProviders 全局替换。 |
| Research job review | 通过 | 执行 `pytest`、`mypy --no-sqlite-cache`、`ruff --no-cache`；no-cache 参数用于规避 runner / 本机 cache 权限噪音，不降低检查强度。 |
| Forbidden diff | 通过 | `git diff -- backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| Forbidden keyword scan | 已检查 | workflow 未命中 `skipTests`、`continue-on-error`、`LIVE=true`、`LIVE_ENABLED`、真实交易所调用或真实 credential 字段；docs/current 命中均为禁止、历史或 pending 风险说明。 |

Review decision：Batch 1 baseline 可冻结为当前 `dev` 的最小 CI 基线。

仍 pending：

- Batch 2 PostgreSQL/Flyway hardening。
- Batch 3 no-outbound guard。
- Batch 4 secret scan / security guard。
- Batch 5 frontend E2E hardening。

## NQ-FRONTEND-B0-DESIGN-TOKENS-V2 验证记录（2026-06-14）

本轮是 frontend-only 改动：新增 v2 设计系统模块 `frontend/src/nq-design-system/`、自检演示页 `frontend/src/pages/dev/`，并在 `frontend/src/router/routes.tsx` 注册公开自检路由 `/dev/design-system`。接线作用域限定在该路由（v2 `ConfigProvider`/`applyNqCssVars`/`registerNqEchartsTheme`），未改全局 `AppProviders`、未动 v1 页面、未改后端/契约/migration。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 类型检查 0 error；`✓ built in ~1s`。>500 kB 单 chunk warning 为既有单包结构（echarts 在 v1 已打包），非本轮回归。 |
| 真机自检：`vite preview` + Playwright Chromium 截图 `/dev/design-system` | **通过** | 0 console error / 0 page error。INTL_CRYPTO 默认 `--nq-up=#33d6a6`(绿)/`--nq-down=#ff5c6c`(红)，`.nq-up` 实算 `rgb(51,214,166)`；切换 CN_STOCK 后翻转为 `--nq-up=#ff5c6c`(红)，`.nq-up` 实算 `rgb(255,92,108)`，数字 + K 线 swatch + ECharts PnL 柱同步翻转。 |
| 视觉断言（同上截图） | **通过** | LIVE（实心红+点）≠ PAPER（描边）；四件状态组件 + AppShell + 暗色分层 + CJK 14px + 数字 tabular-nums 正常；`body` 背景仍为 v1 `#0d1219`，作用域接线未泄漏到 v1。 |
| `npm run test:e2e` | **未运行** | 现有 E2E 多数 spec 依赖后端（`127.0.0.1:18888`，本环境未启动）；本轮只新增公开自检路由与独立模块，未改既有页面/全局主题，既有 E2E 语义不受影响。Playwright Chromium 已就绪，后端就绪后由用户侧执行全量 E2E。 |
| `git status --short` | 已检查 | 仅 `frontend/src/nq-design-system/`、`frontend/src/pages/dev/`（新增）、`frontend/src/router/routes.tsx`（修改）+ 本轮 `WORKLOG.md`/`TESTING.md`。`dist` / `tsbuildinfo` 已 gitignore，未入库；临时截图脚本已删除。 |

阶段与安全边界：

- 只做 B0（READY_NOW）基础系统，未做 B1+ 业务页面，未做 AI/Agent/DH 页面（B8 仍 BLOCKED）。
- 未接真实 WebSocket/SSE/交易所 adapter；实时数据本阶段只留 TanStack Query polling / 手动刷新规范。
- LIVE 明确为 disabled；未下单、撤单、转账、提现。
- 未读取、打印、输出真实 API key、secret、token、私钥、助记词、passphrase。

## NQ-FRONTEND-B0-LOGIN-AND-EXCEPTION-PAGES（B0.1）验证记录（2026-06-14）

本轮 frontend-only：重做登录页 + 四个异常页 + 404，复用 `@/nq-design-system` v2。在独立 git worktree（`feat/nq-frontend-b0-login-exception`，基于 `feat/nq-frontend-ds-v2`）执行，与 Codex 的 `dev` HEAD 隔离。未改后端/契约/migration/鉴权逻辑。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error；`✓ built in 880ms`。>500 kB 单 chunk warning 为既有单包结构，非本轮回归。 |
| `login-page-smoke.spec.ts`（Playwright Chromium，外部 vite preview，无后端） | **1 passed** | 断言新登录页：NexusQuant + 定位 + 4 能力 + 空账号/密码 + 安全边界；并负向断言 `GateJ completed` / `Next: GateK-PLAN` / `DEV / PAPER / LOCAL controlled access` 不出现。 |
| 真机自检：Playwright Chromium 截图（9 路由） | **通过** | 0 console / 0 page error。登录页桌面端整体居中双区（非靠右）、主视觉无 Gate/DEV/PAPER/LOCAL；移动端上下堆叠、卡片置顶首屏；`/exception/auth` 三 reason 各异、`/exception/forbidden` 缺少角色+申请指引(403)、`/exception/error` Request ID+时间+返回入口(500)、`/exception/welcome` 第一步动作、404 统一异常层。暗色对比度 / 主色 #5b8cff / 中文 14px / 圆角 4-6 均符合 token。 |
| `npm run test:e2e`（全量） | **未跑** | 多数 spec 依赖后端（`:18888`，本环境未启动）；本轮仅单独运行无后端依赖的 login smoke 并通过，且未改既有业务页面/全局主题。 |
| `git status --short`（worktree） | 已检查 | 仅 B0.1 源文件变更；`tsc -b` 回生的 `playwright.config.*` / `vite.config.*`（CRLF）已 `git checkout` 还原，未入提交。 |

阶段与安全边界：

- 仍属 B0（READY_NOW）：登录页 + 四个异常页 + 404；未做 B1+ 业务页面，未做 AI/Agent/DH 页面（B8 仍 BLOCKED）。
- 未改鉴权逻辑（`authApi` / `auth-store` / `RequireAuth` 原样复用）；登录不展示默认凭证/明文，不新增凭证处理路径。
- 异常页本轮只交付表现层 + 公开路由；真实触发接线属后续切片。
- LIVE 明确为 disabled；未接真实 socket/交易所；未改后端 API。

## NQ-FRONTEND-TABLE-DENSITY-B0.2 验证记录（2026-06-14）

本轮 frontend-only：在 `@/nq-design-system` 新增表格密度 token + 列格式组件(数字右对齐/tabular/金额/百分比/状态/涨跌列),并在 `/dev/design-system` 自检。基于最新 `origin/dev` 在独立 worktree 执行。未改后端/契约/migration/GateK 事实源,未迁移既有业务页。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error；`✓ built in 844ms`。>500 kB 单 chunk warning 为既有单包结构,非本轮回归。 |
| `npm run test:e2e -- tests/e2e/design-system-table-smoke.spec.ts tests/e2e/login-page-smoke.spec.ts --project=chromium`（dev server,无后端） | **2 passed** | 表格 smoke:密度 standard→compact class 切换、金额列 `64,231.50 USDT`、涨跌 up 色 `rgb(51,214,166)` 且 up≠down(独立于 success/danger);login smoke 保持通过。 |
| 真机自检：Playwright Chromium 截图 `/dev/design-system` | **通过** | 0 console / 0 page error;表格密度切换、数字右对齐 tabular、金额/百分比/涨跌/状态列渲染正常,涨跌色随惯例翻转。 |
| `npm run test:e2e`（全量） | **未跑** | 多数 spec 依赖后端(`:18888`,本环境未启动);本轮仅跑无后端依赖的 design-system / login smoke 并通过,未改既有业务页面/全局主题。 |

阶段与安全边界：

- B0.2 仅产出可复用基础能力(表格密度 + 列格式)+ 自检,未做 B1+ 业务页面,未迁移既有页面,未做 AI/Agent/DH 页面（B8 仍 BLOCKED）。
- 涨跌列必须使用行情方向色(`var(--nq-up/--nq-down/--nq-flat)`),与 success/danger 解耦,随惯例开关一处翻转。
- 未接真实 socket/交易所;未碰 LIVE;未改后端 API;未全局替换 AppProviders。
