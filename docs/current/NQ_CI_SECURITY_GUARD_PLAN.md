# NQ CI Security Guard Plan

任务：NQ-CI-SECURITY-GUARD-BATCH-4-PLAN
日期：2026-06-17
状态：Batch 4A plan review PASS / ACCEPTED；Batch 4B secret scan minimal implementation IMPLEMENTED / FIRST CI RUN FAILED / FIRST-RUN-FIX REQUIRED（first run `27662197509`：6/7 jobs green，仅 `Secret scan` job 失败于 gitleaks `leaks found: 1`，详见「Batch 4B first-run review」段落）；Batch 4C artifact/log redaction guard NOT STARTED；Batch 4F dependency audit OPTIONAL / NOT STARTED；Batch 5 frontend E2E hardening PENDING。`.github/workflows/ci.yml` 已含最小 `secret-scan` job（pinned gitleaks CLI + custom regex backstop）；本轮 first-run review 只评审 + 改 docs，未改 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy。Batch 3 no-outbound guard 仍 FROZEN / ACCEPTED（run `27634370657`），不重复实现。不得把 Batch 4B 写成 FIRST GREEN / FROZEN / ACCEPTED；不得把 Batch 4 写成 fully implemented；不得把 Batch 4C / dependency audit 写成 implemented；不得把 Batch 5 写成 started。

## Task classification

- Primary type: `CI_CD` planning。
- Auxiliary: `SECURITY_AUDIT`（secret scan / artifact / permission baseline）、`DOCUMENTATION`。
- Primary skill: `nq-dh-workflow-router`（任务分类、范围限定、Gate / 安全边界检查）。无辅助 skill 命中实现需求；MCP / 网络访问未使用。
- 本轮严格 planning-only：所有结论来自只读检查与已冻结的 Batch 1/2/3 事实源，不落地任何 guard。

## Scope

Allowed in this planning batch:

- 只读检查 `.github/workflows/ci.yml`、`.github/CODEOWNERS`、`.github/pull_request_template.md`。
- 只读检查 `docs/current/NQ_CI_BASELINE_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_PLAN.md`、`NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`、`README.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`。
- 只读检查 backend / frontend / research 配置文件与 tracked `.env.example` 模板（不读取真实 secrets）。
- 新增本文件 `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`。
- 同步 `docs/current/README.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md` 的 Batch 4 planning 状态。

Forbidden in this planning batch:

- 不修改 `.github/workflows/ci.yml`。
- 不修改 Java / TypeScript / Python 代码与测试代码，不新增测试。
- 不新增 API，不新增 migration，不修改历史 migration。
- 不修改 backend production code、frontend、research、scripts、deploy。
- 不读取、打印、复制或输出真实 credential material。
- 不把 `.env` / secrets / dumps / logs / backups / `.git` / `target` / `node_modules` / `dist` / `build` 作为数据源扫描。
- 不开启 LIVE，不接 AI，不接 DH runtime。
- 不实现 RealClient、真实 provider、真实 OKX / Binance permission probe adapter。
- 不调用真实交易所，不下单 / 撤单 / 转账 / 提现。
- 不把 Batch 4 写成 implemented；不把 Batch 5 写成 started。

## Files inspected

只读检查（无修改）：

- `.github/workflows/ci.yml`（6 jobs：`diff-check`、`backend`、`postgres-flyway`、`frontend`、`research`，以及 Batch 3 新增的 no-outbound guard job）。
- `.github/CODEOWNERS`、`.github/pull_request_template.md`。
- `.gitignore`、`.env.example`、`deploy/.env.freeze.example`（确认 ignore 边界与模板为占位符）。
- `docs/current/NQ_CI_BASELINE_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_PLAN.md`、`NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`、`README.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`。
- `git ls-files` 输出（确认 tracked 文件中无真实 `.env` / `*.key` / `*.pem` / keystore / dump）。

## Files changed

- 新增：`docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`（本文件）。
- 同步：`docs/current/README.md`、`docs/current/ROADMAP.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`（仅记录 Batch 4 planning 状态）。
- 未修改任何 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy。

## Current CI baseline

- Project: NexusQuant / NQ；branch `dev`；stage GateJ completed；Next: GateK-PLAN。
- `.github/workflows/ci.yml` 当前 6 jobs 全 FROZEN / ACCEPTED：
  - `diff-check`：changed-file whitespace gate（`git diff --check`）。
  - `backend`：PostgreSQL service + post-Flyway CI-only legacy account fixture + `mvn -f backend/pom.xml test`。
  - `postgres-flyway`：empty DB Flyway smoke、schema artifact 生成 / redaction check / upload、repository PostgreSQL smoke、`nq-app` context smoke。
  - `frontend`：`npm ci` + `npm run build`。
  - `research`：`pytest` + `mypy` + `ruff`。
  - `no-outbound-guard`（Batch 3，run `27634370657`）：exchange credential env-absence 检查、denylist coverage 检查、`NoOutboundExchangeGuardTest`。
- Workflow 顶层 `permissions: contents: read`（最小化已就位）。
- 当前 CI 不注入任何 repository secrets；仅使用 disposable CI-only PostgreSQL 占位值（`nq_ci` / `nq_ci_user` / `nq_ci_password` / `postgres` / `123456`）。
- `postgres-flyway` job 已存在一个 artifact redaction check（schema-only dump + 高风险 credential pattern fail-closed），是 Batch 4 secret/artifact scan 的最近一份事实先例。
- AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；RealClient / real provider / real permission probe adapter NOT IMPLEMENTED；默认 credential permission probe port = `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。

## Current security risk inventory

| Area | Current evidence | Risk class for Batch 4 | Required proof |
| --- | --- | --- | --- |
| 无专用 secret scan job | 当前 6 jobs 无 gitleaks / secret pattern scan；唯一相关检查是 `postgres-flyway` 内针对 schema artifact 的 redaction check。 | P1（Batch 4 实现目标）：tracked source / config / workflow / docs 无统一 secret 扫描。 | Batch 4B 必须新增 tracked-file secret scan，fail closed。 |
| `.env.example` 模板误报 | `.env.example`、`frontend/.env.example`、`deploy/.env.freeze.example` 为 tracked 占位模板，含 `REPLACE_WITH_LOCAL_*` / `CHANGE_ME_*` 与本地默认值（如 `NQ_DB_PASSWORD=123456`、空 `NQ_*_API_KEY=`）。 | P2：naive scanner 会把占位模板 / 本地 DB 默认值误判为 secret。 | Batch 4B 必须为这三个模板配置 allowlist / placeholder 例外，且仍禁止真实值进入模板。 |
| Binance adapter fake 测试私钥 | `BinanceRuntimeConfigTest` / `BinanceRequestSignerTest` / `BinanceHttpClientTest` 含 PEM 私钥 header（`BEGIN PRIVATE KEY`，连字符省略）+ fake body（base64 `Zm9v`）等 fake PEM；`BinanceEd25519RequestSigner` 含 `PRIVATE_KEY_BEGIN` 协议常量。 | P2：PEM-header 规则会命中既有合法测试 fixture 与协议常量。 | Batch 4B 已对这 4 个文件做 path 精确 allowlist（gitleaks + backstop），避免阻塞绿灯而不放宽真实 secret 检测。 |
| Artifact 泄露 | `postgres-flyway` 上传 `nq-postgres-flyway-schema-artifacts`（schema-only，已过 redaction check）。无其他上传 artifact。 | P2：未来若上传 Surefire reports / build logs / frontend / research 产物，可能夹带 secret。 | Batch 4C 必须把 "upload 前 redaction" 固化为通用规则，不只针对 schema dump。 |
| Job 日志泄露 | Batch 2C/2D/3 已记录 disposable CI PostgreSQL 值的平台级显示与 Spring Boot generated dev password 作为 P3 log hygiene residual；`gho_` GitHub token 被平台 mask 为 `***`。 | P2/P3：CI 日志 hygiene residual 已知且非真实 credential。 | Batch 4C 提供 log redaction proof，确认无真实 credential / raw request / raw response / signature。 |
| GitHub Actions 权限过大 | 顶层 `permissions: contents: read`；各 job 未单独提权；无 `pull-requests: write`、`id-token: write` 等。 | P3：当前最小化已就位，主要是回归防护。 | Batch 4B 固定 security job `contents: read`，禁止隐式 write，禁止注入 secrets。 |
| `continue-on-error` 掩盖 | 当前 workflow 无 `continue-on-error`；no-outbound guard 与 redaction check 均 fail closed。 | P2：若安全步骤被设为 `continue-on-error` 将掩盖失败。 | Batch 4 禁止安全步骤使用 `continue-on-error`；任何 soft-fail 必须显式 review。 |
| Dependency audit 噪声 | `frontend` job 日志已出现既有 `npm audit` advisory summary（非阻断）。 | P2：dependency audit 噪声大，若直接 blocking 会阻塞 dev。 | Dependency audit 列为可选 Batch 4F，非阻断起步，不混入 secret scan baseline。 |
| CODEOWNERS / PR 模板占位 | `.github/CODEOWNERS` 仍用占位 `@YOUR_GITHUB_USERNAME`；`.github/pull_request_template.md` 仅 1 行近空。 | P3：审查治理未生效；不属于 secret scan，但属安全治理 backlog。 | 记录为 P3 follow-up，不在 Batch 4 secret scan baseline 内强行处理。 |
| 真实 credential 是否已泄露 | `git ls-files` 中无真实 `.env` / `*.key` / `*.pem` / keystore / dump；高风险字面量扫描（`AKIA` / `sk-` / `ghp_` / `gho_` / `xox` / PEM）仅命中 Binance fake 测试私钥与 PEM 常量。 | P0/P1=0：当前未发现真实 credential material。 | Batch 4 baseline 把"现状无真实泄露"作为基线，并防回归。 |

## Secret scan plan

扫描范围（fail-closed）：

- 只扫描 **tracked source / config / workflow / docs**：基于 `git ls-files` 或在 clean checkout 上运行 secret scanner（scanner 默认 honor `.gitignore`）。
- 必须排除（不作为数据源）：`.git`、`target`、`node_modules`、`dist`、`build`、`coverage`、`test-results`、`playwright-report`、`logs`、`*.log`、`dumps` / `*.dump`、`backups` / `*.backup` / `*.bak`、`artifacts/`、`freeze-evidence/`、`release/`。这些已在 `.gitignore` 覆盖；secret scan 额外显式排除以防 untracked 本地文件被扫。
- 明确不读取本地真实 `.env` / `.env.<profile>` / 任何真实 secret 文件；`.gitignore` 已 ignore `.env` 与 `.env.*`（仅放行三个 `*.example` 模板）。
- 工具选择：
  - 主扫描：`gitleaks`（pinned 版本 / commit SHA）。gitleaks 在本地文件系统做正则 + 熵检测，不向外部服务发起验证请求，符合 no-outbound 边界。
    - 扫描目标限定为**当前 checkout 的 tracked working tree**（如 `gitleaks detect --no-git --source .` 在 clean checkout 上，叠加上文排除目录）；full git-history 扫描属可选 / 单独决策项，不在 Batch 4B baseline 默认开启，避免历史误报与 merge gate 非确定性。
    - 优先使用 pinned **gitleaks CLI binary**（或不需要 license 的执行路径），避免 `gitleaks-action` 在 GitHub org 账号下要求 `GITLEAKS_LICENSE` repository secret —— 该要求会与"不向 test / security job 注入 repository secret"边界冲突（见 GitHub Actions permissions plan）。若必须用官方 action，须先确认账号类型不需要 license，并单独 review。
  - 备份扫描：一个轻量 **custom regex fail-closed step**，复用并扩展 `postgres-flyway` job 现有 redaction 正则（`ci.yml` 当前的高风险 credential pattern），作为 deterministic backstop，避免单一工具漂移。
  - 不默认采用 `trufflehog` 的 verified-secret 模式：其 verify 会对外部 provider 发起请求，与 no-outbound 边界冲突；若引入只允许 `--no-verification` / unverified 模式，且需单独 review。
- 误报处理：
  - 用 gitleaks allowlist（`.gitleaks.toml` / `.gitleaksignore`）white-list 三个 `.env.example` 模板的占位行、Binance fake 测试私钥与 `PRIVATE_KEY_BEGIN` 常量行、disposable CI-only PostgreSQL 占位值。
  - allowlist 必须按 **path + 规则 + 指纹** 精确范围，禁止全局放宽 PEM / API key 规则。
  - 误报修正只允许加 allowlist 条目或收紧占位符命名，禁止删除真实检测规则、禁止把真实 secret 写进 allowlist。
  - 所有 finding 只报告 file / path / rule / line，绝不打印命中值。

## Credential pattern plan

Batch 4 secret scan 至少覆盖以下凭证模式（命中即 fail closed，输出脱敏）：

- API key（通用 `api[_-]?key` 赋值）。
- API secret（`api[_-]?secret` / `secret` 赋值）。
- passphrase（`passphrase` 赋值）。
- token（`token` / bearer token 赋值；GitHub `ghp_` / `gho_` / `ghs_` / `ghr_`）。
- private key（`private[_-]?key` 赋值）。
- PEM block（`-----BEGIN [A-Z ]*PRIVATE KEY-----`）。
- JWT（`eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+` 三段式）。
- GitHub token（`ghp_` / `gho_` / `ghs_` / `ghr_` / `github_pat_`）。
- AWS access key（`AKIA[0-9A-Z]{16}`，及 secret access key 形态）。
- OpenAI / Anthropic style API key（`sk-[A-Za-z0-9]{20,}`、`sk-ant-[A-Za-z0-9-]+`、`OPENAI_API_KEY` / `ANTHROPIC_API_KEY` 赋值非占位值）。
- exchange API credential material（`NQ_OKX_*_API_KEY` / `*_API_SECRET` / `*_API_PASSPHRASE`、`NQ_BINANCE_*_API_KEY` / `*_API_SECRET` / `*_PRIVATE_KEY` / `*_PRIVATE_KEY_PATH` 出现非占位真实值）。
- Slack / 通用 webhook token（`xox[baprs]-`）。
- mnemonic / 助记词、cookie、完整 keystore 内容。
- encrypted_payload / decrypted_payload 泄露风险：当前 DH Integration-0 契约文档把 `encrypted_payload` / `decrypted_payload` 作为字段名引用（contract-only，未实现）。Batch 4 规则必须区分"字段名引用"（allowlist 文档与契约）与"真实 payload 值落地"（fail closed）。

占位例外（不得视为命中）：`REPLACE_WITH_LOCAL_*`、`CHANGE_ME_*`、空赋值（`KEY=`）、明显 fake 测试值（`Zm9v` 等）、disposable CI-only DB 占位。占位例外只能放在 allowlist，禁止放宽核心规则。

## Artifact / log security plan

Artifact security（延续并通用化）：

- schema artifact redaction check 延续：`postgres-flyway` 现有 schema-only dump data-row 检测 + 高风险 credential pattern 检测保持 fail-closed，不回退。
- CI artifacts 不包含 credential material：把"upload 前 redaction scan"固化为通用规则，任何未来 `upload-artifact` 步骤都必须先过 secret/redaction 检查，再上传。
- logs 不输出 secret：job 日志禁止 `printenv` / `env` dump、禁止打印真实 connection string / API key / secret / passphrase / token / private key / signature / raw request / raw response / encrypted_payload / decrypted_payload。
- backend test reports 不包含 secret：若未来上传 Surefire / 测试报告，必须先 redaction 扫描；当前未上传，保持不上传或上传前脱敏二选一，明确记录。
- frontend / research outputs 不包含 secret：`npm run build`、Playwright report、pytest / mypy / ruff 输出若上传，必须先 redaction 扫描；当前 frontend / research 不上传产物。

Log redaction proof（Batch 4C）：

- 复核 secret scan / backend / postgres-flyway / frontend / research job 日志，确认无真实 credential material。
- 复核已知 P3 residual（disposable CI PostgreSQL 值平台级显示、Spring Boot generated dev password、`gho_` token mask 为 `***`），确认其为 disposable / masked，不是真实 production credential。
- 输出 proof 表：每类 secret 模式在日志中"未出现真实值"。

## GitHub Actions permissions plan

- workflow permissions 最小化：顶层保持 `permissions: contents: read`；security / secret scan job 显式声明 `permissions: contents: read`，不隐式继承更高权限。
- pull_request / push 权限边界：默认不授予 `pull-requests: write`、`issues: write`、`id-token: write`、`packages: write`、`contents: write`。若后续要把 finding 作为 PR 注解发布，必须按 job 单独授予最小 `pull-requests: write`，并单独 review，不得全局放宽。
- 不使用 repository secrets：secret scan / security job 不注入任何 repository secret、不依赖真实 credential、不访问真实交易所。
- 不将 secrets 注入 test jobs：保持 Batch 1/2/3 现状（仅 disposable CI-only PostgreSQL 占位值），禁止把真实 exchange / cloud / JWT secret 注入任何 test job。
- 不允许 `continue-on-error` 掩盖安全失败：secret scan、redaction check、LIVE/boundary guard 必须 fail closed；任何 soft-fail 都属阻断项，需显式 review。
- action 固定：secret scan action 必须 pin 到固定版本 / commit SHA，避免供应链漂移（与现有 GitHub-provided actions Node.js 20 deprecation 一并纳入 maintenance 评估，但不在本 baseline 强行升级）。

### LIVE / boundary disabled guard（static，pattern-based）

- 复用 `deploy/.env.freeze.example` 已有的边界开关作为基线事实：`NQ_AI_ENABLED=false`、`NQ_AI_SIGNAL_ENABLED=false`、`NQ_AI_PAPER_TRADING_ENABLED=false`、`NQ_DH_ENABLED=false`、`NQ_REAL_TRADING_ENABLED=false`、`NQ_LIVE_TRADING_ENABLED=false`、`NQ_TRADING_ENV=SIM`。
- guard 必须基于"启用型赋值"模式（如 `NQ_LIVE_TRADING_ENABLED=true`、`NQ_REAL_TRADING_ENABLED=true`、`NQ_AI_ENABLED=true`、`NQ_DH_ENABLED=true`、`NQ_TRADING_ENV=LIVE`）fail closed，**不得**对裸关键字（`LIVE` / `AI` / `DH`）报警，避免对"LIVE disabled / AI not started"等正常文案与现有 `STATUS.md` / `ROADMAP.md` 措辞产生海量误报。
- backend tests 继续覆盖 `LIVE_CREDENTIAL_BLOCKED` 与 NoReal permission probe 行为（已在 Batch 3 固化），Batch 4 不重复实现，只在文档层声明这是回归基线。

## Dependency audit boundary

- Batch 4 baseline **不包含** blocking dependency audit。Batch 4 baseline = secret scan（4B）+ artifact / log redaction proof（4C）+ permissions / boundary guard。
- dependency audit（`npm audit`、Maven dependency vulnerability check / OWASP dependency-check、`pip-audit`）列为 **可选 Batch 4F later plan**，非阻断起步，triage 后再选择性把 high / critical 提升为 blocking。
- 不得让 dependency audit 阻塞 Batch 4 baseline：若 4F 未启动，Batch 4 仍可凭 4B/4C/4D/4E 冻结。
- dependency audit 不混入 secret scan baseline：两者分属不同 job / 不同 sub-batch，证据与 freeze 各自独立。
- `frontend` job 现有 `npm audit` advisory summary 属既有非阻断信息，不在 Batch 4 baseline 内改判为 blocking。

## Batch 4 implementation strategy

### Batch 4A: security guard plan review

- Status: PASS / ACCEPTED AS IMPLEMENTATION BASELINE（`NQ-CI-SECURITY-GUARD-BATCH-4A-PLAN-REVIEW`，2026-06-17，P0/P1=0）。
- Scope: 仅文档与只读 source review。
- Success: 本 plan P0/P1=0；secret scan 范围、credential pattern、artifact / log、permissions、dependency audit 边界被接受；无 workflow / 代码 / 测试 / migration 改动。
- Review evidence（25 项评审 checklist 全部满足）：
  - secret scan 限定 tracked safe paths、显式排除 `.git` / `target` / `node_modules` / `dist` / `build` / `coverage` / `logs` / `dumps` / `backups`、不读本地真实 `.env`：通过。
  - pinned gitleaks + custom regex backstop（复用现有 redaction 正则）、禁止 trufflehog verify / 外部验证请求：通过。
  - 误报治理 path + rule + fingerprint 精确 allowlist、禁止放宽核心规则、finding 只 file/path/rule 不输出值：通过。
  - credential pattern 覆盖 API key / secret / passphrase / token / private key / PEM / JWT / GitHub token / AWS / OpenAI / Anthropic / exchange credential / Slack / mnemonic / cookie / keystore；`encrypted_payload` / `decrypted_payload` 区分字段名引用 vs 真实值；占位例外限定 `REPLACE_WITH_LOCAL` / `CHANGE_ME` / 空赋值 / fake 测试值 / CI-only DB placeholder：通过。
  - artifact upload 前 redaction 通用规则、logs 禁止 env dump / raw req-resp / signature / connection string / secret、backend 报告 + frontend / research 产物若上传须 redaction：通过。
  - workflow permissions 最小化 `contents: read`、禁止 write / id-token（除非单独 review）、禁止 repository secret 注入 test job、禁止 `continue-on-error` 掩盖 security failure：通过。
  - Batch 4 baseline 不含 blocking dependency audit、dependency audit 归可选 Batch 4F、不重复 Batch 3 no-outbound、不做 frontend E2E hardening、Batch 5 仍 PENDING：通过。
  - 评审期复核：tracked safe paths 高风险字面量（含 `sk-ant-` / `github_pat_`）仅命中 Binance fake 测试私钥与 `PRIVATE_KEY_BEGIN` 协议常量，无真实泄露；tracked secret-like 文件仅三个 allowlisted `.env.example` 模板。
  - 评审新增 2 项 P3 实现提示（见 findings 表）：gitleaks 扫描目标限定 tracked tree、优先 gitleaks CLI binary 以规避 `GITLEAKS_LICENSE` repository-secret 耦合。均非阻断，不影响 baseline 接受。

### Batch 4B: secret scan minimal implementation

- Status: IMPLEMENTED / FIRST CI RUN FAILED / FIRST-RUN-FIX REQUIRED（impl `NQ-CI-SECURITY-GUARD-BATCH-4B-SECRET-SCAN-IMPL` 2026-06-17；first run `27662197509` completed / failure，仅 `Secret scan` job 失败于 gitleaks `leaks found: 1`，详见「Batch 4D: first-run review」）。workflow 已落地最小 secret scan baseline，但首次 CI run 未通过；不得写成 FIRST GREEN / FROZEN / ACCEPTED。
- 已实现内容（`.github/workflows/ci.yml` 新增 `secret-scan` job）：
  - job 级 `permissions: contents: read`；不注入任何 repository secret；不依赖 `GITLEAKS_LICENSE`；无 `continue-on-error`；secret scan 失败 fail closed 阻塞 CI。
  - 步骤 1：安装 pinned gitleaks CLI binary（`GITLEAKS_VERSION=8.18.4`），从 GitHub release 以 `curl`（无 token / 无 auth header）下载，安装后 `gitleaks version` 必须等于 `8.18.4`，否则 fail。不使用 `gitleaks-action`。
  - 步骤 2：基于 `git ls-files -z` 构建 tracked safe-file 列表，排除 `.env` / `.env.*` / `secrets` / `credentials` / `*.pem` / `*.key` / `*.p12` / `*.jks` / `*.keystore` 与 `target` / `node_modules` / `dist` / `build` / `coverage` / `logs` / `dumps` / `backups` / `.git`。
  - 步骤 3：把 safe 文件 stage 到 `RUNNER_TEMP` 后用 `gitleaks detect --no-git --source <staging> --redact` 扫描（只扫当前 tracked working tree，禁止 full-history scan；`--redact` 保证日志/报告不输出 secret value）。gitleaks 配置 inline 写入 `RUNNER_TEMP`（非 tracked，不被扫描），`[extend] useDefault = true` + 精确 allowlist（4 个 Binance fake-key / PEM 协议常量文件 by path + `REPLACE_WITH_LOCAL` / `CHANGE_ME` / `FAKE-PLACEHOLDER` 占位 marker by value），核心规则未放宽。
  - 步骤 4：custom regex backstop（pattern 通过 quoted heredoc inline，无新增 tracked 文件），覆盖 `sk-ant-` / `sk-proj-` / `sk-` / `github_pat_` / `gh[pousr]_` / `AKIA` / `ASIA` / PEM private key（含 RSA / EC / OPENSSH / DSA / PGP）/ `xoxb-` / `xoxp-` / value-bearing mnemonic / value-bearing 凭证赋值（`apiKey` / `secret` / `passphrase` / `privateKey` / `token` / `encrypted_payload` / `decrypted_payload`）。backstop 只输出 `file | pattern`，绝不输出命中值；value-bearing pattern 过滤 placeholder marker；`pem_private` 对 4 个 Binance 文件精确 path allowlist。
- 已落实 4A 两个 P3 实现提示：扫描目标限定当前 tracked working tree（`--no-git`，非 full-history）；用 pinned gitleaks CLI binary 而非 `gitleaks-action`，不依赖 `GITLEAKS_LICENSE` repository secret。
- 与 4A 计划的差异（已记录，非越界）：
  - `.env.example` 三个模板按本轮 4B 排除清单（含 `.env.*`）被排除出扫描范围，因此无需对模板占位行单独 allowlist；模板仍由 `.gitignore` 占位纪律 + 独立 sweep 保证 placeholder-only。后续 Batch 4C/freeze 可评估是否把模板重新纳入 scope + allowlist。
  - LIVE/boundary static guard 不在本轮最小 secret-scan-only 4B 内（4B 任务范围限定 secret scan）；LIVE / RealClient rejection 回归仍由 Batch 3 backend tests（`LIVE_CREDENTIAL_BLOCKED` / NoReal probe）覆盖。LIVE/boundary static guard 留作后续 Batch 4 step / 4C。
  - 本文件 P2 风险表内既有 PEM 字面量已软化为 `BEGIN PRIVATE KEY`（连字符省略），避免 security 文档自命中 scanner。
- 本地验证：custom regex backstop（file-driven，复刻 workflow 逻辑）对当前 tracked safe tree 0 非 allowlisted 命中（含新增 `secret-scan` job 与本文件，均未自命中）。gitleaks CLI 未在本地执行（本地 Windows 开发环境 `python` 为 Microsoft Store stub、无预装 gitleaks）；gitleaks layer 的完整 FP 面留待 GitHub Actions first run 验证（Batch 4D）。
- Success 判据（待 first CI run 确认）：scanner 对受控 fake secret fail closed；Binance fake 测试私钥 / PEM 协议常量被精确 allowlist 而不放宽真实检测；job 不注入 repository secret、不访问真实交易所、`contents: read`；无 Batch 5 frontend E2E hardening、无 dependency audit blocking。

### Batch 4C: artifact / log redaction proof

- Status target: IMPLEMENTED / PENDING FIRST CI RUN 或 evidence 收集。
- Scope: 把 "upload 前 redaction" 固化为通用规则；提供 secret scan / backend / postgres-flyway / frontend / research job 的 log redaction proof。
- Success: 任何 upload artifact 前有 redaction 检查；日志无真实 credential / raw request / raw response / signature / encrypted_payload / decrypted_payload；已知 P3 residual 明确标注为 disposable / masked。

### Batch 4D: first-run review

- Status: FAIL / FIRST-RUN-FIX REQUIRED（`NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-REVIEW`，first run `27662197509`，commit `6db97535`，event push / branch dev，2026-06-17，completed / failure）。
- Scope: 评审第一次包含 secret scan 的 GitHub Actions run、jobs、steps、logs。

#### Batch 4B first-run review

per-job 结论（run `27662197509`）：

| Job | 结论 |
| --- | --- |
| Diff check | success |
| No-outbound guard | success |
| Backend Maven test | success |
| PostgreSQL / Flyway smoke | success |
| Frontend build | success |
| Research quality gate | success |
| **Secret scan** | **failure** |

- 失败定位：唯一失败 job 是 `Secret scan`，唯一失败 step 是 `Run pinned gitleaks secret scan (tracked working tree, no history)`。其它 6 个 job 全 green，证明 no-outbound / backend / postgres-flyway / frontend / research baseline 未回归。
- gitleaks 安装与运行：install step success；gitleaks `8.18.4` 版本校验通过；`gitleaks detect --no-git --redact` 实际执行，日志 `scan completed in 911ms` 后 `WRN leaks found: 1`，脚本按设计 `rc != 0 -> exit 1` fail closed（无 `continue-on-error`）。
- 失败类别：**gitleaks default-ruleset false-positive（1 finding）**，不是 binary install / 版本 / tracked-list staging / YAML / heredoc / 脚本错误。gitleaks 默认规则集比 custom backstop 的窄正则更宽，命中 1 处 custom backstop / 现有 allowlist 未覆盖的内容；custom backstop step（step #6）因 gitleaks step 先失败被 skip，未在 CI 运行（其本地复刻验证仍为 0 命中）。
- **诊断缺口（根因之一）**：gitleaks step 未带 `-v` / `--verbose`，默认只打印 `leaks found: N` 摘要，未把命中的 `RuleID` / `File` / `Line`（即使 `--redact` 也会显示这些非敏感字段）输出到 CI 日志；JSON 报告写入 `RUNNER_TEMP` 但未上传（Batch 4C 未开始）。因此当前无法从 CI 日志直接定位 FP 的具体 rule / file。
- 安全确认：`--redact` 生效，日志未输出 secret value（仅 `leaks found: 1`）；无真实 credential material 出现在任何 job 日志；secret-scan job `permissions: contents: read`、未注入 repository secret、未用 `gitleaks-action` / `GITLEAKS_LICENSE` / `id-token` / write / `continue-on-error`；未扫描禁止目录；未做 full-history scan；未调用真实交易所；未开启 LIVE / AI / DH。
- Review decision：FAIL / FIRST-RUN-FIX REQUIRED。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-FIX`，不得写成 FIRST GREEN / FROZEN / ACCEPTED，不得混入 Batch 4C / 4F / Batch 5。

FIX 任务建议（供 `NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-FIX`，本轮不实现）：

1. 先让 finding 可见：gitleaks step 加 `-v`（配合既有 `--redact`，只暴露 RuleID / File / Line，不暴露 secret value），或临时把 redacted JSON 报告打印 / 上传，定位具体 rule 与 file。
2. 定位后做最小、精确处置：若确为 fake test value / placeholder / 协议常量，则按 path + rule + fingerprint 精确 allowlist（禁止放宽核心规则）；若 default 规则与本项目 credential-pattern 计划不匹配产生噪声，可评估收敛 ruleset，但需单独说明。
3. 不得为了过绿放宽真实检测、不得删测试样例、不得 broad allowlist。

### Batch 4E: freeze review

- Status target: FROZEN / ACCEPTED。
- Scope: 冻结 secret scan / redaction / permission baseline；同步 `docs/current` current facts 与 next action。
- Success: Batch 4 成为当前 `dev` security guard baseline；Batch 5 仍 PENDING；required-check 提升决策记录在案。

### Batch 4F（可选）: dependency audit later plan

- Status target: LATER PLAN / NOT STARTED。
- Scope: 规划 `npm audit` / Maven dependency check / `pip-audit`，非阻断起步，triage 后选择性提升。
- Success: dependency audit 不阻塞 Batch 4 baseline；与 secret scan 分离；high / critical 提升策略明确。

## Batch 5 boundary

- Batch 4 不做 frontend E2E hardening；Playwright browser cache、backend startup for E2E、mock-server / preview-server 策略、flaky skip policy 属 Batch 5。
- Batch 5 仍 PENDING，不得写成 started。

## Security boundary

- 不需要也不允许真实 credentials。
- CI 不读取 `.env` / 真实 secret 文件；任何读 `.env` 的代码路径必须保持手动 gated 且不进入默认测试执行。
- 默认 CI / 默认 Maven test 不访问真实交易所（Batch 3 已冻结，不重复实现）。
- 不开启 LIVE，不接 AI，不接 DH runtime，不实现 RealClient / real provider / real permission probe adapter。
- 不下单 / 撤单 / 转账 / 提现 / private REST / private WS / credential probe / permission probe 到真实交易所。
- CI 日志 / 报告 / 注解 / 截图 / 上传 artifact 不得包含真实 API key / secret / passphrase / token / cookie / private key / mnemonic / raw request / raw response / headers / signatures / encrypted_payload / decrypted_payload / credential material。
- secret scan 只报告 file / path / rule，绝不打印命中值。

## P0/P1/P2/P3 findings

| Priority | Finding | Decision |
| --- | --- | --- |
| P0 | None for this planning-only baseline；tracked safe paths 未发现真实 credential material。 | P0 planning blockers = 0。 |
| P1 | None for this planning-only baseline。当前无专用 secret scan job 属实现目标，不是 planning blocker。 | P1 planning blockers = 0。 |
| P2 | 当前无专用 CI secret scan / gitleaks job。 | 作为 Batch 4B 实现目标；不得写成 implemented。 |
| P2 | 三个 `.env.example` 模板与本地默认值（`123456`、空 API key）会被 naive scanner 误判。 | Batch 4B 必须 path/rule/指纹精确 allowlist，禁止放宽核心规则。 |
| P2 | Binance fake 测试私钥与 `PRIVATE_KEY_BEGIN` 常量会命中 PEM 规则。 | Batch 4B allowlist 这些 test fixtures / 常量行。 |
| P2 | 未来 upload artifact / 测试报告可能夹带 secret。 | Batch 4C 把 upload 前 redaction 固化为通用规则。 |
| P2 | dependency audit 噪声大，直接 blocking 会阻塞 dev。 | 列为可选 Batch 4F，非阻断起步，不混入 secret scan baseline。 |
| P3 | 已知 CI log hygiene residual（disposable CI PostgreSQL 值、Spring Boot dev password）。 | 继续标注为 disposable / masked，非真实 credential；Batch 4C 复核。 |
| P3 | `.github/CODEOWNERS` 仍用占位 `@YOUR_GITHUB_USERNAME`；`pull_request_template.md` 近空。 | 记录为安全治理 follow-up，不在 secret scan baseline 内强行处理。 |
| P3 | GitHub-provided actions Node.js 20 deprecation；secret scan action 需 pin。 | 与 Batch 3 P3 一并纳入 maintenance；本 baseline 不升级。 |
| P3 | gitleaks 扫描目标若不显式限定，可能落到 full git-history，带来历史误报与 merge gate 非确定性。 | Batch 4B 默认只扫当前 tracked working tree；full-history 扫描为可选 / 单独决策项。 |
| P3 | `gitleaks-action` 在 GitHub org 账号下要求 `GITLEAKS_LICENSE` repository secret，可能与"不注入 repository secret"边界冲突。 | Batch 4B 优先用 pinned gitleaks CLI binary / 无 license 路径；若用官方 action 须确认账号类型不需 license 并单独 review。 |

## Validation

本轮 planning / doc 验证（只读，已执行）：

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
rg "apiKey|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|AKIA|sk-|xox|ghp_|gho_|JWT|OPENAI_API_KEY|ANTHROPIC_API_KEY|BINANCE|OKX|LIVE|RealClient" .github backend frontend research docs/current
```

执行结果摘要：

- `git status --short`：编辑前 clean；`git diff --check`：无 whitespace error；`git diff --stat`：仅本轮允许的 `docs/current` 文件。
- `git diff -- .github / backend / frontend / research / scripts / deploy / backend/**/db/migration`：均为空（forbidden 区域无改动）。
- 高风险字面量扫描（`AKIA` / `sk-` / `ghp_` / `gho_` / `xox` / PEM block，scoped 到 tracked safe paths，排除 `node_modules` / `target` / `dist` / `build` / `.git`）：仅命中 Binance 适配器既有 fake 测试私钥与 `PRIVATE_KEY_BEGIN` 协议常量，无真实 credential。
- `encrypted_payload` / `decrypted_payload` / `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `RealClient` 命中均为 DH 契约字段名引用、boundary "NOT IMPLEMENTED" 声明或 credential-governance 代码，非真实泄露。
- `git ls-files` 中无真实 `.env` / `*.key` / `*.pem` / keystore / dump；仅三个 `*.example` 占位模板被 `.gitignore` 显式放行。

rg 仅用于 tracked safe paths；未把扫描扩展到 `.env` / secrets / logs / dumps / backups / `target` / `node_modules` / `dist` / `build` / `.git`。本轮 docs-only / planning-only，未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff（且明确禁止改 workflow / 代码 / 测试 / migration）。

实现阶段验证（Batch 4B/4C，本轮不执行）：secret scanner 必须对受控 fake secret fail closed；CI first-run 证据归 Batch 4D review。

## Boundary confirmation

- 本轮（Batch 4B first-run review）只评审 first run `27662197509` 并改 docs；**未修改 `.github/workflows/ci.yml`**。secret-scan job 的 workflow 改动已在 commit `6db97535` 落地（仅新增 `secret-scan` job，未改既有 6 个 job）。
- 未修改 Java / TypeScript / Python 代码与测试代码；未新增测试。
- 未新增 API；未新增 migration；未修改历史 migration。
- 未修改 backend production code / frontend / research / scripts / deploy。
- 未新增 tracked 文件（gitleaks 配置与 backstop pattern 仍 inline 写入 `RUNNER_TEMP`，不落仓库）。
- secret-scan job 未注入 repository secret；未使用 write / id-token permission；未使用 `continue-on-error`；不依赖 `GITLEAKS_LICENSE`（已对 commit `6db97535` 的 `ci.yml` 复核确认）。
- 未读取、打印、复制或输出真实 credential material；CI 日志 `--redact` 生效未输出 secret value；未把禁止目录作为数据源扫描；未做 full-history scan。
- 未调用真实交易所；未下单 / 撤单 / 转账 / 提现。
- 未开启 LIVE / AI / DH runtime；未实现 RealClient / real provider / real permission probe adapter。
- Batch 4B secret scan IMPLEMENTED / FIRST CI RUN FAILED / FIRST-RUN-FIX REQUIRED；Batch 4C / 4F NOT STARTED；Batch 5 仍 PENDING。

## Review decision

Batch 4A plan review：PASS / ACCEPTED AS IMPLEMENTATION BASELINE（2026-06-17，P0/P1=0）。
Batch 4B secret scan minimal implementation：IMPLEMENTED；first CI run（`27662197509`）**FAILED**。
Batch 4B first-run review：**FAIL / FIRST-RUN-FIX REQUIRED**（2026-06-17）。7 个 job 中 6 个 green，仅 `Secret scan` job 失败于 gitleaks `leaks found: 1`（gitleaks default-ruleset FP，custom backstop / 现有 allowlist 未覆盖）；非 install / 版本 / staging / YAML / 脚本错误。`--redact` 生效未泄露 secret value；secret-scan job 边界（`contents: read`、无 repository secret、无 `gitleaks-action` / `GITLEAKS_LICENSE` / `id-token` / write / `continue-on-error`、无 full-history scan）均合规。Batch 4C / 4F 仍 NOT STARTED；Batch 5 仍 PENDING；Batch 3 仍 FROZEN / ACCEPTED。不得把 Batch 4B 写成 FIRST GREEN / FROZEN / ACCEPTED，不得把 Batch 4 写成 fully implemented。

## Next concrete action

Next concrete action：`NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-FIX`。第一步先让 finding 可见（gitleaks 加 `-v` 配合 `--redact`，或临时打印 / 上传 redacted 报告，定位具体 RuleID / File / Line），再做最小精确处置（path + rule + fingerprint allowlist 或收敛 ruleset，禁止放宽核心规则 / 删测试样例 / broad allowlist）。修复后重跑 CI，再进入 `NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-REVIEW`（second pass）。

Batch 4B 当前 IMPLEMENTED / FIRST CI RUN FAILED / FIRST-RUN-FIX REQUIRED；Batch 4C / 4F NOT STARTED；Batch 5 仍 PENDING；不得把 Batch 4 写成 fully implemented，不得把 Batch 4C / dependency audit / Batch 5 写成 started。
