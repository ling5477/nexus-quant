# NQ CI Log Redaction Proof Plan (Batch 4C-C)

任务：NQ-CI-SECURITY-GUARD-BATCH-4C-C-LOG-REDACTION-PROOF-PLAN
日期：2026-06-18
状态：Batch 4C-C log redaction proof **LOG PROOF COMPLETED / PENDING FREEZE REVIEW**（`NQ-CI-SECURITY-GUARD-BATCH-4C-C-LOG-REDACTION-PROOF-IMPL`，2026-06-18，P0/P1=0；基于 latest green run `27732660516`（commit `a6d4bf74`，event push / branch dev，completed / success，7/7 jobs green，ci.yml blob `4a40ef78` 与当前 HEAD 一致）的 review-time per-job log proof，详见「Log redaction proof evidence」段）。前序：plan review **PASS / ACCEPTED AS PROOF / REVIEW BASELINE**（`NQ-CI-SECURITY-GUARD-BATCH-4C-C-PLAN-REVIEW`，2026-06-18，28 项全部满足，详见「Plan review」段）。**Batch 4C-C 不得写成 FROZEN；Batch 4C 整体仍 NOT FROZEN**。本轮只做 review-time log proof 文档，**未修改 `.github/workflows/ci.yml`**（静态断言列为可选 future hardening，未落地），不改 Java / TypeScript / Python 代码与测试，不新增 API / migration，不新增 log 扫描 job，不读取本地 logs，不上传 artifact / logs。Batch 4C-A plan review **PASS / ACCEPTED AS IMPLEMENTATION BASELINE**；Batch 4C-B pre-upload artifact redaction gate **FROZEN / ACCEPTED**（immutable run `27701669084`，frozen baseline = `ci.yml` `Pre-upload redaction gate (PostgreSQL schema artifacts)` step，blob `4a40ef78` / commit `c734102d`，详见 `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`）。**Batch 4C 整体仍 NOT FROZEN**（4C-C log redaction proof 仅完成本轮 planning，尚未实现也未冻结）。Batch 4B minimal secret scan baseline 仍 FROZEN / ACCEPTED（frozen baseline commit `31540de8`，run `27674393780`）；Batch 3 no-outbound guard 仍 FROZEN / ACCEPTED（run `27634370657`）；Batch 4F dependency audit OPTIONAL / NOT STARTED；Batch 5 frontend E2E hardening PENDING。AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；RealClient / real provider / real permission probe adapter NOT IMPLEMENTED。

本文件是 `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md` 内「Batch 4C-C: log redaction proof」子段落的详细规划，独立成文，与 `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` / `NQ_CI_POSTGRES_FLYWAY_*_PLAN.md` 同例。Batch 4C-B（artifact）与 Batch 4C-C（log）扫描目标不同，不重复彼此。

> 注：本文件刻意把所有 credential 模式写成「省略连字符 / 仅前缀 / 占位描述」形态（如 `BEGIN ... PRIVATE KEY`、`sk-` 前缀、`AKIA` + 16 位占位），不写任何完整凭证字面量，避免本规划文件自身触发 secret-scan job（参照 Batch 4C-B first-run 的 `AKIA` 文档示例误报教训）。

## Task classification

- Primary type: `CI_CD` planning。
- Auxiliary: `LOG_REDACTION_PLANNING`、`ARTIFACT_SECURITY_REVIEW`、`CREDENTIAL_BOUNDARY_REVIEW`、`DOCUMENTATION`。
- Primary skill: `nq-dh-workflow-router`（任务分类、范围限定、Gate / 安全边界检查）。无辅助 skill 命中实现需求；本轮严格 planning-only，结论来自只读检查与已冻结 Batch 1 / 2 / 3 / 4B / 4C-B 事实源。

## Scope

Allowed in this planning batch：

- repository: NexusQuant / NQ，branch `dev`（HEAD `ad8f9a2c`，已对齐 `origin/dev`）。
- 只读检查 `.github/workflows/ci.yml`（7 jobs、`::add-mask::`、`set -euo pipefail`、gitleaks `--redact`、pre-upload gate finding 输出、各 step 的 echo / log 行为）与 `docs/current` CI 事实源。
- 新增本文件 `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md`。
- 同步 `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`、`NQ_CI_SECURITY_GUARD_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`、`README.md`、`TESTING.md`、`WORKLOG.md` 的 Batch 4C-C planning 状态。

Forbidden in this planning batch：

- 不修改 `.github/workflows/ci.yml`；不新增 log 扫描 job / step。
- 不修改 Java / TypeScript / Python 代码与测试代码；不新增测试。
- 不新增 API；不新增 migration；不修改历史 migration。
- 不修改 frontend 页面、research 逻辑、scripts、deploy。
- 不读取、打印、复制或输出真实 credential material。
- 不把 `.env` / secrets / dumps / logs / backups / `.git` / target / node_modules / dist / build 作为数据源扫描。
- 不上传 artifact；不上传 raw gitleaks JSON report。
- 不使用 repository secrets / write / id-token / continue-on-error。
- 不开启 LIVE / AI / DH runtime；不实现 RealClient / real provider / real permission probe adapter；不调用真实交易所。
- 不把 Batch 4C-C 写成 implemented；不把 Batch 4C 整体写成 FROZEN；不把 Batch 4F / Batch 5 写成 started。

## Files inspected

只读检查（无修改）：

- `.github/workflows/ci.yml`（HEAD `ad8f9a2c`，7 jobs：`diff-check`、`no-outbound-guard`、`backend`、`postgres-flyway`、`frontend`、`research`、`secret-scan`）。
- `docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`、`NQ_CI_SECURITY_GUARD_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`、`README.md`、`TESTING.md`、`WORKLOG.md`。

## Files changed

- 新增：本文件 `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md`。
- 同步：`NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`、`NQ_CI_SECURITY_GUARD_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`、`README.md`、`TESTING.md`、`WORKLOG.md`（仅记录 Batch 4C-C planning 状态）。
- 未修改 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy。

## Current CI log security baseline

基于 HEAD `ad8f9a2c` 的 `.github/workflows/ci.yml` 只读复核，当前 CI 日志安全基线如下（这些是 4C-C log proof 的事实起点，本轮均已只读验证）：

- 7 jobs 全部使用 `set -euo pipefail`，**无任何 `set -x`**（不回显展开后的命令与变量值）；**无 `printenv` / `env` dump / 裸 `env` 打印**。
- 顶层 `permissions: contents: read`；`secret-scan` job 另显式 `permissions: contents: read`；**无 `id-token` / write / `packages` / `pull-requests` 等更高权限**。
- **无 repository secrets 注入**：CI 仅使用 disposable CI-only PostgreSQL 占位值（`backend` job：`POSTGRES_PASSWORD` / `NQ_DB_PASSWORD` = `123456`；`postgres-flyway` job：`nq_ci` / `nq_ci_user` / `nq_ci_password`）。无 `secrets.` 引用、无 `GITLEAKS_LICENSE`、无 `gitleaks-action`。
- **无 `continue-on-error`**：所有 security / guard step（no-outbound guard、pre-upload redaction gate、secret-scan）fail closed。
- `postgres-flyway` job 首个 step `Mask CI-only PostgreSQL connection values` 用 `::add-mask::` 屏蔽 `NQ_FLYWAY_DB_URL` / `NQ_FLYWAY_DB_USER` / `NQ_FLYWAY_DB_PASSWORD`，且位于 `Checkout` / `Set up Java` 之前（mask 尽量靠前）。
- `secret-scan` job：gitleaks `--redact`，JSON 报告写入 `RUNNER_TEMP`（**不上传**），失败分支只输出 sanitized `RuleID / File / Lines / Fingerprint`，绝不输出 `Secret / Match / matched line / commit / author`；custom backstop 只输出 `pattern | file`。
- `postgres-flyway` job 的 frozen pre-upload redaction gate：finding 只输出 `rule | file`，data-row 检查用 `grep -qE`（静默）、credential 检查用 `grep -rIlE -l`（只列文件名），从不回显匹配行 / 值。
- 当前唯一上传 artifact = `nq-postgres-flyway-schema-artifacts`（已过 frozen pre-upload gate）；**未上传** surefire test reports、frontend build outputs、research（pytest / mypy / ruff）outputs、gitleaks JSON report。
- 默认 CI 不调用真实交易所（Batch 3 frozen）；adapter 离线构造，不产生 raw request / raw response / signature 真实值。

## Log risk inventory

| # | 风险源 | 当前 CI 中是否存在 | Risk class | 4C-C 应做的证明 |
| --- | --- | --- | --- | --- |
| 1 | env dump / `printenv` / `set -x` | 无（7 jobs 均 `set -euo pipefail`，无 `-x`/`printenv`/`env` dump） | P2（回归防护） | 静态断言 workflow 无 `printenv` / `env` dump / `set -x`；任何未来误加都属阻断回归 |
| 2 | raw request / raw response | 默认 CI 无真实交易所调用；adapter 离线 | P2 | review-time log scan 断言无 raw request / raw response 真实值落地 |
| 3 | connection string / credentials-in-URL | `postgres-flyway` 用 `::add-mask::` 屏蔽 DB URL/user/pass；`backend` job 的 `NQ_DB_URL` / `NQ_DB_PASSWORD`（`123456`）**未 mask** | P3 residual | 复核值为 disposable CI-only、非真实凭证、无 `set -x` 回显；记录 `backend` job mask 不对称为 P3 hardening follow-up |
| 4 | signature | 无真实签名链路进默认 CI | P2 | log scan 断言无 `signature[:=]` 真实值；区分协议常量 / 字段名 |
| 5 | API key / secret / passphrase / token | 默认 CI 无真实 credential；gitleaks `--redact` | P2 | log scan 断言无真实 credential material 形态值 |
| 6 | private key / PEM block | 仅 Binance fake 测试私钥 / `PRIVATE_KEY_BEGIN` 协议常量（已 allowlist） | P2 | log scan 区分 fake / 协议常量 与真实 `BEGIN ... PRIVATE KEY` 块 |
| 7 | cookie / password / mnemonic | 无真实值；disposable DB password `123456` 属 CI-only | P2 / P3 | log scan 断言无真实 cookie / password / mnemonic 值；`123456` 标注 disposable |
| 8 | encrypted_payload / decrypted_payload 真实值 | DH Integration-0 仅字段名 / mock / `FAKE-PLACEHOLDER` | P2 | log scan 区分「字段名引用」与「真实 payload 值落地」，后者 fail |
| 9 | Spring Boot generated password | app-context smoke 启动时框架可能打印 generated security password | P3 residual | 复核为 dev / disposable、随机一次性、非 production credential |
| 10 | disposable CI PostgreSQL values | `backend` `123456`、`postgres-flyway` `nq_ci_*`（后者已 mask） | P3 residual | 标注 disposable / CI-only / 公共 workflow 源码可见，非真实凭证 |
| 11 | gitleaks / gate finding 输出 | secret-scan sanitized（RuleID/File/Lines/Fingerprint）；gate `rule | file` | P2（已合规） | 保持 sanitized；断言不输出 `Secret / Match / matched line / commit / author` / 命中值 |
| 12 | GitHub platform token 回显 | `GITHUB_TOKEN` 被平台 mask 为 `***` | P3（已 masked） | 标注平台级 mask，非泄露 |

## Existing log protections

4C-C 复核的已存在保护（均在 frozen baseline 内，本轮不改、不弱化）：

- `permissions: contents: read`（顶层 + secret-scan job 两处），无 write / id-token。
- 无 repository secrets 注入；无 `GITLEAKS_LICENSE` / `gitleaks-action`。
- 无 `continue-on-error`（security / guard step fail closed）。
- `::add-mask::` 屏蔽 disposable CI DB 值（`postgres-flyway` job 的 URL / user / password）。
- gitleaks `--redact`（日志 / 报告不输出 secret value）。
- secret-scan sanitized 失败分支（仅 RuleID / File / Lines / Fingerprint）。
- pre-upload redaction gate finding 只输出 `rule | file`（Batch 4C-B frozen）。
- 全 job `set -euo pipefail` 而非 `set -x`（不回显展开命令）。

## Log proof plan

### 执行方式（item 3）

4C-C **不是**新增一个「在 CI 内自扫自身日志」的 job/step，而是一个 **review-time（评审期）log proof** 方法：

1. **review-time `gh run view --log` / per-job logs 复核**：在评审一次目标 GitHub Actions run 时，用 `gh run view <run-id> --log`（或 per-job `gh run view --job <job-id> --log`）拉取已完成 run 的 job logs，对其按 Pattern checklist 做只读扫描，产出「每类 secret 模式在日志中未出现真实值」的 proof 表。
2. **不读取本地 logs**：只针对 GitHub Actions 已完成 run 的 job logs；不读取开发机本地 `*.log` / `logs/` / `dumps/` / `backups/`。
3. **不上传 logs artifact**：不把 job logs 作为 artifact 上传（避免把日志再次落到可下载产物）；proof 结论以文字 + sanitized 摘要形式写进评审报告 / `TESTING.md`。
4. **不自扫 streaming logs**：CI runner 无法可靠自扫自身正在产生的 streaming 日志（顺序 / 缓冲限制），因此 4C-C 不在 workflow 内加「扫描自身日志」step。CI 内可做的是 **静态断言**（见下）+ 对 **CI 生成的报告文件**（已由 Batch 4C-B pre-upload gate 覆盖 artifact 目录）的扫描。
5. **静态断言（CI 内可做、但本轮不实现）**：对 workflow 文本断言无 `printenv` / `env` dump / `set -x`，作为「日志不会 dump env」的前置保证。本轮只在文档层固定该断言为验收项；是否落地为自动静态检查 step 属可选 future hardening（见「自动化与边界」）。

### 证明产物（proof 表骨架）

4C-C 实现轮应对目标 run 产出如下 proof 表（每行：风险类别 → 期望 → 证据来源 → 结论）：

| 风险类别 | 期望 | 证据来源（review-time） | 结论 |
| --- | --- | --- | --- |
| env dump / `set -x` | 日志无展开后的 env / 变量值回显 | workflow 静态断言 + job logs 抽查 | 待 4C-C 实现轮填 |
| raw request / response | 日志无真实 raw req/resp | adapter / backend job logs | 待填 |
| connection string | 仅 disposable CI 值，且 `postgres-flyway` 已 mask | postgres-flyway / backend job logs | 待填 |
| signature | 无真实签名值 | backend / adapter job logs | 待填 |
| credential material（key/secret/passphrase/token/private key/cookie/password/mnemonic） | 无真实值 | 全 7 job logs | 待填 |
| encrypted_payload / decrypted_payload | 仅字段名引用，无真实值 | 全 7 job logs | 待填 |
| gitleaks / gate finding | 仅 sanitized 字段 | secret-scan / postgres-flyway job logs | 待填 |
| disposable CI 值 / Spring Boot dev password | 标注 disposable / masked | postgres-flyway / backend job logs | 待填 |

## Jobs to inspect

4C-C log proof 必须覆盖当前 7 个 jobs（顺序不分先后）：

1. **Diff check**（`diff-check`）：仅 `git diff --check`，无 credential；确认无 `set -x` / env dump。
2. **No-outbound guard**（`no-outbound-guard`）：env-absence 检查、denylist coverage、`NoOutboundExchangeGuardTest`；确认 forbidden env 名只打印**名称**不打印值（`echo "Forbidden ... is set: ${name}"` 只回显变量名）。
3. **Backend Maven test**（`backend`）：PostgreSQL service + Flyway fixture + `mvn test`；重点复核 disposable `NQ_DB_PASSWORD` / `POSTGRES_PASSWORD`（`123456`）是否被任何 step 回显（当前无 `set -x`，Maven 不打印 env），并复核 Spring Boot generated dev password 是否出现（标注 disposable）。
4. **PostgreSQL / Flyway smoke**（`postgres-flyway`）：mask step、Flyway smoke、schema 生成、pre-upload gate、upload、repository / context smoke；复核 `::add-mask::` 生效、gate finding 只 `rule | file`、smoke 打印的是 schema 元数据（`installed_rank|version|...`）而非数据行 / 凭证。
5. **Secret scan**（`secret-scan`）：gitleaks install / scan / backstop；复核 `--redact`、sanitized 失败分支、backstop `pattern | file`，且日志中出现的 `BEGIN ... PRIVATE KEY` / `RuleID=` 等字样均为 runner 回显的 **step 脚本本体**（`##[group]Run ...`）而非执行输出 / 真实凭证。
6. **Frontend build**（`frontend`）：`npm ci` + `npm run build`；复核 `npm audit` advisory summary 为既有非阻断信息、无 token / secret 打印。
7. **Research quality gate**（`research`）：`pytest` + `mypy` + `ruff`；复核无 credential、无 raw payload 真实值打印。

## Pattern checklist

review-time log scan 至少覆盖以下模式（命中即需人工裁定真实值 vs FP；本文件刻意写成省略 / 前缀 / 占位形态）：

- 完整 `AKIA` + 16 位、`ASIA` + 16 位（AWS access key id 形态）。
- `sk-` 前缀（含 `sk-ant-` / `sk-proj-`）长随机串（OpenAI / Anthropic 形态）。
- `github_pat_` / `ghp_` / `gho_`（及 `ghs_` / `ghr_`）GitHub token 形态。
- `xoxb-` / `xoxp-`（Slack token 形态）。
- `BEGIN ... PRIVATE KEY`（PEM 块，连字符省略）。
- value-bearing 赋值：`apiKey` / `api_key` / `secret` / `apiSecret` / `passphrase` / `token` / `privateKey` / `private key` / `password` / `cookie` 后接 `[:=]` + 真实值形态（非占位 / 非字段名 / 非空）。
- connection strings / credentials-in-URL：`scheme://user:pass@host`（密码段非占位）。
- `signature` 赋值真实值。
- `raw request` / `raw response` 真实报文体落地。
- `encrypted_payload` / `decrypted_payload` 真实值（区分 DH 契约字段名引用 vs 真实 payload 落地）。

## Finding output policy

- finding 只允许输出：**job 名** / **line category（风险类别）** / **rule（pattern 名）** / **safe excerpt（脱敏摘要）**。
- **禁止输出 secret value**。
- **禁止输出完整 matching line**（若该行可能含真实值）；只允许「类别 + 文件 / job + 规则名」或经人工脱敏后的极短 excerpt。
- 与已存在保护一致：gitleaks 保持 `--redact` + sanitized（RuleID / File / Lines / Fingerprint）；pre-upload gate 保持 `rule | file`；4C-C review 报告沿用同一脱敏纪律。
- proof 表 / 评审报告中引用日志行时，必须先脱敏（如 `apiKey=<masked>`、`password=***`、`AKIA…<omitted>`）。

## False positive policy

- **可作为 non-blocking FP**（记录但不阻断）：
  - 文档 / 边界说明文字（如「不输出 token」「LIVE disabled」「credential material」等散文）。
  - 字段名 / 列名引用（如 `password_hash` 列名、`encrypted_payload` 契约字段名、`apiKey=<masked>` 脱敏占位）。
  - regex pattern 本体 / step 脚本回显（runner `##[group]Run ...` 显示的 pattern 定义，非执行输出）。
  - 协议常量（如 `PRIVATE_KEY_BEGIN`）、明显 fake 测试值（`Zm9v` 等）、占位 marker（`REPLACE_WITH_LOCAL` / `CHANGE_ME` / `FAKE-PLACEHOLDER`）。
  - disposable CI-only 值（`123456` / `nq_ci_*`），但需标注 disposable 并复核未由 `set -x` 真实回显。
- **必须阻断（真实值形态）**：任何匹配上述 Pattern checklist 且形态为真实凭证 / 真实报文 / 真实 payload 的命中，一律按真实泄露处理，触发 fix。
- **不得靠 broad allowlist 关闭**：FP 只能按「类别 + 精确上下文」逐条裁定，禁止用宽 allowlist 一刀切关闭某规则 / 某文件 / 某 job。

## Batch 4B / 4C-B / 4F / Batch 5 boundary

- **Batch 4B（FROZEN）**：扫描 tracked **source** 树是否提交真实 secret。4C-C 不重复、不改 4B。
- **Batch 4C-B（FROZEN）**：CI **生成的 artifacts** 上传前 redaction（pre-upload gate，扫 `artifacts/postgres-flyway/`）。**4C-C 评审 CI logs，不重复 artifact gate**——两者扫描目标不同（4C-B = 生成产物文件；4C-C = job 日志）。
- **Batch 4C-C（本计划）**：review-time CI **log** redaction proof + 静态 `printenv`/`env`/`set -x` 断言。与 4B / 4C-B 共享 Pattern checklist，但目标是 job 日志。
- **Batch 4F（OPTIONAL / NOT STARTED）**：dependency audit（`npm audit` / Maven dependency check / `pip-audit`，CVE / dependency hygiene），**不在 4C-C 做**，与 redaction 无关，单独分批。
- **Batch 5（PENDING）**：frontend E2E hardening；若未来新增 Playwright reports / logs 上传，**必须先受 Batch 4C-B artifact pre-upload gate 约束（artifact 侧），并纳入 4C-C log proof 策略（日志侧）**。4C-C 只定义策略，不实现 Batch 5。

## Automation boundary（item 11）

- **本轮只能规划**：4C-C 当前交付物是 planning 文档 + review-time proof 方法，不实现任何 workflow / job / step。
- 若建议「自动 log scanning」（如 CI 内对生成报告文件 / 对 `gh run view --log` 输出做自动扫描，或落地静态 `printenv`/`set -x` 检查 step），**只能标为 future hardening**，不得作为 4C-C 当前实现目标，不得在本轮落地。
- 任何 future 自动化都必须延续：`contents: read`、无 repository secret / write / id-token / continue-on-error、finding 脱敏、不上传 logs artifact、不自扫 streaming 日志。

## Security boundary

- 不需要也不允许真实 credentials。
- 不读取本地 `.env` / 真实 secret 文件 / 本地 logs；log proof 只针对 GitHub Actions 已完成 run 的 job logs（review-time）。
- 不上传 artifact；不上传 logs / raw gitleaks JSON report。
- 保持 `permissions: contents: read`；不注入 repository secret；不使用 write / id-token；不使用 `continue-on-error`。
- 不开启 LIVE / AI / DH runtime；不实现 RealClient / real provider / real permission probe adapter；不调用真实交易所。
- finding 输出只报 job / category / rule / safe excerpt，绝不打印命中真实值。

## P0/P1/P2/P3 findings

| Priority | Finding | Decision |
| --- | --- | --- |
| P0 | None for this planning-only baseline。当前 CI 日志未发现真实 credential material 泄露源。 | P0 planning blockers = 0。 |
| P1 | None for this planning-only baseline。4C-C log proof 属实现目标，不是 planning blocker。 | P1 planning blockers = 0。 |
| P2 | 当前缺 4C-C log redaction proof（review-time）正式产物。 | 作为 4C-C 实现目标；本轮只 planning，不得写成 implemented。 |
| P2 | 静态 `printenv`/`env`/`set -x` 断言尚未落地为可重复检查。 | 列为 4C-C 实现项 / 可选 future hardening；本轮固定为验收项。 |
| P3 | `backend` job 的 `NQ_DB_PASSWORD` / `POSTGRES_PASSWORD`（`123456`）未 `::add-mask::`（与 `postgres-flyway` job 已 mask 不对称）。 | disposable CI-only、非真实凭证、无 `set -x` 回显；记录为 mask 对称性 hardening follow-up，本轮不改 workflow。 |
| P3 | Spring Boot app-context smoke 可能打印 generated dev security password。 | dev / disposable / 一次性随机，非 production credential；4C-C 复核标注。 |
| P3 | log proof 依赖 review-time `gh run view --log`（CI 不自扫 streaming 日志）。 | 接受为 review-time 证据 + 静态断言组合；自动化为 future hardening。 |

## Validation

本轮 planning / doc 验证（只读，已执行；HEAD `ad8f9a2c`）：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "printenv|set -x|env dump|add-mask|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action|AKIA|ASIA|LIVE|RealClient" .github docs/current backend frontend research
```

执行结果摘要：

- `git status --short` clean（编辑前）；`git diff --check` clean；`git diff --stat` 仅本轮允许的 `docs/current` 文件。
- `git diff -- .github/workflows/ci.yml / backend / frontend / research / scripts / deploy / backend/**/db/migration` 均空（forbidden 区域无改动）。
- `.github/workflows/ci.yml`（HEAD `ad8f9a2c`）：**无 `printenv` / `env` dump / `set -x`**；`::add-mask::` 出现在 `postgres-flyway` job 屏蔽 3 个 disposable DB 值；**无 `continue-on-error` / `id-token` / `GITLEAKS_LICENSE` / `gitleaks-action`**；`permissions` 仅顶层 + secret-scan 两处 `contents: read`。
- `backend` job 的 `NQ_DB_PASSWORD` / `POSTGRES_PASSWORD` = `123456`（disposable CI-only，未 mask，已记为 P3）；`postgres-flyway` job 的对应值已 `::add-mask::`。
- `git grep -nE 'AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}'`（tracked safe paths）= 0 完整 AWS-key 字面量（Batch 4C-B first-run-fix 仍生效）。
- rg 命中均为 docs CI 事实源引用、ci.yml 既有 mask / redact / pattern 项、DH 契约字段名、JWT / credential-governance 代码引用，**无真实 credential material**（whole-tree gitleaks 0 findings + backstop 0 命中已在 Batch 4B / 4C-B 冻结证据中验证）。

rg 仅用于 tracked safe paths；未把扫描扩展到 `.env` / secrets / logs / dumps / backups / target / node_modules / dist / build / `.git`。本轮 docs-only / planning-only，未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff（且明确禁止改 workflow / 代码 / 测试 / migration）。

实现阶段验证（Batch 4C-C 实现轮，本轮不执行）：对目标 GitHub Actions run 以 review-time `gh run view --log` 拉取 7 job logs，按 Pattern checklist 产出 proof 表；CI first-run / freeze 证据归后续 4C-C 实现 + review 子批次。

## Boundary confirmation

- 未修改 `.github/workflows/ci.yml`；未新增 log 扫描 job / step。
- 未修改 Java / TypeScript / Python 代码与测试代码；未新增测试。
- 未新增 API；未新增 migration；未修改历史 migration。
- 未修改 frontend 页面、research 逻辑、scripts、deploy。
- 未读取、打印、复制或输出真实 credential material；未把禁止目录作为数据源扫描；未上传 artifact / logs / raw gitleaks report。
- 未使用 repository secrets / write / id-token / continue-on-error；保持 `contents: read`。
- 未调用真实交易所；未开启 LIVE / AI / DH runtime；未实现 RealClient / real provider / real permission probe adapter。
- **Batch 4C-C 保持 PLAN ONLY / NOT IMPLEMENTED**；**Batch 4C 整体仍 NOT FROZEN**；Batch 4C-B pre-upload artifact redaction gate 仍 FROZEN / ACCEPTED；Batch 4B 仍 FROZEN / ACCEPTED；Batch 3 仍 FROZEN / ACCEPTED；Batch 4F 仍 OPTIONAL / NOT STARTED；Batch 5 仍 PENDING。

## Plan review

`NQ-CI-SECURITY-GUARD-BATCH-4C-C-PLAN-REVIEW`，2026-06-18。对本 plan + `.github/workflows/ci.yml`（HEAD `a6d4bf74`，只读）做 28 项评审，全部满足，P0/P1 = 0。结论 **PASS / ACCEPTED AS PROOF / REVIEW BASELINE**。

| # | 评审项 | 结果 | 证据 |
| --- | --- | --- | --- |
| 1 | current CI log security baseline 准确 | 通过 | 逐条对 `ci.yml` 复核（下列 3-9 项） |
| 2 | 7 jobs 全部纳入 log proof checklist | 通过 | 「Jobs to inspect」列 Diff check / No-outbound guard / Backend Maven test / PostgreSQL / Flyway smoke / Frontend build / Research quality gate / Secret scan |
| 3 | ci.yml 无 `set -x` / `printenv` / `env` dump | 通过 | `rg "printenv\|set -x\|env dump" ci.yml` = 0；7 jobs 均 `set -euo pipefail` |
| 4 | permissions 仅 `contents: read` | 通过 | ci.yml line 12-13（顶层）+ 777-778（secret-scan）|
| 5 | 无 repo secrets / write / id-token / continue-on-error | 通过 | `rg "id-token\|continue-on-error\|GITLEAKS_LICENSE\|gitleaks-action" ci.yml` = 0；无 `secrets.` 引用 |
| 6 | postgres-flyway `::add-mask::` disposable DB 值 | 通过 | ci.yml line 365-367（URL / user / password）|
| 7 | secret-scan `--redact` | 通过 | ci.yml line 886 |
| 8 | secret-scan sanitized 仅 RuleID / File / Lines / Fingerprint | 通过 | ci.yml line 896-902（`never Secret / Match / matched line / commit / author`）|
| 9 | pre-upload gate finding 仅 `rule \| file` | 通过 | ci.yml line 577 / 618 / 659（`grep -rIlE -l`）/ 668 |
| 10 | log risk inventory 覆盖完整清单 | 通过 | 12 行覆盖 env dump/`set -x`、raw req/resp、connection string、signature、key/secret/passphrase/token、private key/PEM、cookie/password/mnemonic、encrypted_payload/decrypted_payload、Spring Boot generated password、disposable DB 值、gitleaks/gate finding、platform token mask |
| 11 | log proof 限定 review-time `gh run view --log` / per-job logs | 通过 | 「Log proof plan」item 1 |
| 12 | 明确不读取本地 logs | 通过 | item 2 + Security boundary |
| 13 | 明确不上传 logs artifact | 通过 | item 3 + Security boundary |
| 14 | 明确不自扫 streaming logs | 通过 | item 4 |
| 15 | pattern checklist 覆盖完整 | 通过 | AKIA/ASIA、sk-/sk-ant-/sk-proj-、github_pat_/ghp_/gho_、xoxb-/xoxp-、BEGIN…PRIVATE KEY、value-bearing apiKey/secret/passphrase/token/privateKey/password/cookie、credentials-in-URL、signature、raw request/response、encrypted_payload/decrypted_payload |
| 16 | finding 输出仅 job / category / rule / safe excerpt | 通过 | 「Finding output policy」 |
| 17 | 禁止输出 secret value | 通过 | 「Finding output policy」 |
| 18 | 禁止输出可能含值的完整 matching line | 通过 | 「Finding output policy」 |
| 19 | FP 策略区分字段名 / regex pattern / step 脚本回显 / 占位 marker / disposable 值 与真实值 | 通过 | 「False positive policy」 |
| 20 | 真实值形态一律阻断 | 通过 | 「False positive policy」必须阻断段 |
| 21 | 禁止 broad allowlist | 通过 | 「False positive policy」末段 |
| 22 | 明确 4C-C 不重复 4C-B artifact gate | 通过 | 「Batch 4B / 4C-B / 4F / Batch 5 boundary」 |
| 23 | 明确 4C-C 不做 4F dependency audit | 通过 | 同上 |
| 24 | 明确 4C-C 不做 Batch 5 frontend E2E hardening | 通过 | 同上 |
| 25 | 自动 log scanning 仅 future hardening | 通过 | 「Automation boundary」 |
| 26 | 允许进入 4C-C proof / review implementation 轮 | 通过 | 「Next concrete action」列实现轮为合法下一步 |
| 27 | P2 记录缺正式 log proof 产物 + 静态断言未落地 | 通过 | P2 两行 |
| 28 | P3 记录 backend `123456` 未 mask、Spring Boot dev password、review-time 依赖 `gh run view --log` | 通过 | P3 三行 |

复核期 ci.yml 关键锚点（只读，HEAD `a6d4bf74`）：唯一 `upload-artifact`（line 676，`nq-postgres-flyway-schema-artifacts`，`if-no-files-found: error`、retention 14/7）；`backend` job `POSTGRES_PASSWORD` / `NQ_DB_PASSWORD` = `123456`（line 174 / 188，未 mask，disposable CI-only）vs `postgres-flyway` `NQ_FLYWAY_DB_PASSWORD` 已 `::add-mask::`（line 367），plan P3 mask 对称性 follow-up 属实；`git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = 0（含本 plan，4C-B first-run-fix 仍生效，本 plan 未自触发 secret-scan）。

## Log redaction proof evidence

`NQ-CI-SECURITY-GUARD-BATCH-4C-C-LOG-REDACTION-PROOF-IMPL`，2026-06-18。结论 **LOG PROOF COMPLETED / PENDING FREEZE REVIEW**，P0/P1 = 0。

### Proof run

- run `27732660516`（commit `a6d4bf74`，event push / branch dev）= **completed / success**，7/7 jobs green。
- ci.yml blob `4a40ef78` 在 HEAD（`d3e828c0`）/ `a6d4bf74`（proof run commit）/ `66cb3d40`（4C-B frozen）/ `c734102d`（gate impl）四处一致——proof run 的 ci.yml 与当前 HEAD 字节一致，proof 对当前 baseline 有效。HEAD 自身 run（`27733445791`）评审时仍 in_progress；按计划取 latest green run，且因 ci.yml blob 一致而等价。
- 取证方式：`gh run view 27732660516 --log`（review-time，远端 CI run 日志）拉到临时文件扫描后即删除；**未读取本地 logs，未持久化日志到仓库，未上传 logs artifact**。pattern 扫描只取 count / sanitized category，从不打印命中真实值。

### Per-job log proof（7 jobs）

| Job | 结论 | log proof 摘要 |
| --- | --- | --- |
| Diff check | success | 仅 `git diff --check`；无 credential、无 `set -x` / `printenv` / `env` dump。 |
| No-outbound guard | success | forbidden env 检查只回显**变量名**不回显值；denylist 覆盖；`NoOutboundExchangeGuardTest` 通过；无真实凭证。 |
| Backend Maven test | success | Maven / 测试输出无真实 credential；disposable `123456`（backend DB password）出现 5 次（service-init / env block，未 mask，disposable CI-only）；Spring 生成 dev password 出现 5 次（ephemeral，值未打印）。 |
| PostgreSQL / Flyway smoke | success | `::add-mask::` 生效；pre-upload gate `no high-risk credential pattern ... (text-only, fail closed)`；artifact `nq-postgres-flyway-schema-artifacts`（74666 bytes）gate 后上传；disposable `nq_ci_password` 出现 2 次（service-container `docker create` + mask-step 自身 env block，平台级 service init 在 mask 生效前）；Spring 生成 dev password 1 次（ephemeral）。 |
| Frontend build | success | `npm ci` + `npm run build`；`npm audit` advisory 为既有非阻断摘要；无 token / secret。 |
| Research quality gate | success | `pytest` + `mypy` + `ruff`；无 credential、无 raw payload 真实值。 |
| Secret scan | success | `Installed gitleaks version: 8.18.4`；`gitleaks ... --redact` → `INF no leaks found` → `gitleaks: no leaks found in tracked working tree.`；custom backstop `no non-allowlisted matches`；失败/ sanitized 分支**未执行**（0 finding）。 |

### Pattern checklist result（14 类）

| # | Pattern | 真实值命中 | 说明 |
| --- | --- | --- | --- |
| 1 | 完整 `AKIA` / `ASIA` + 16 | **0** | 仅 gate/secret-scan step-script 内 `AKIA[0-9A-Z]{16}` 正则定义回显（FP）。 |
| 2 | `sk-` / `sk-ant-` / `sk-proj-` + 长串 | **0** | 仅 step-script 内 `sk-[A-Za-z0-9...]` 正则定义回显（FP）。 |
| 3 | `github_pat_` / `ghp_` / `gho_` + 长串 | **0** | `GITHUB_TOKEN` 被平台 mask 为 `***`（≥53 处 `***`）。 |
| 4 | `xoxb-` / `xoxp-` + 长串 | **0** | 无命中。 |
| 5 | `BEGIN ... PRIVATE KEY` 完整 PEM 块（含 `-----`） | **0** | 仅 step-script 内 dash-omitted `BEGIN ... PRIVATE KEY` 正则定义回显（FP）。 |
| 6 | value-bearing `apiKey`/`secret`/`passphrase`/`token`/`privateKey`/`password`/`cookie` + 真实值 | **0** | 仅 step-script 内赋值正则定义 + disposable 短值（见 12）。 |
| 7 | credentials-in-URL（`scheme://user:pass@`） | **0** | 无命中。 |
| 8 | `signature` + 真实值 | **0** | 无命中。 |
| 9 | raw request / raw response 真实报文 | **0** | 无命中。 |
| 10 | `encrypted_payload` / `decrypted_payload` 真实值 | **0** | 无命中（DH 仅契约字段名，未进 CI runtime）。 |
| 11 | Spring Boot generated password | **0 真实凭证** | 6 次「generated security password」——ephemeral 随机 dev password，值未打印；disposable / 非 production（P3）。 |
| 12 | disposable CI PostgreSQL values | **0 真实凭证** | `123456`×5（backend）、`nq_ci_password`×2（postgres-flyway）——disposable CI-only、明文已在公开 `ci.yml` 源，平台级 service-init / env block 显示（P3）。 |
| 13 | platform token mask | n/a | `***` mask 生效（GITHUB_TOKEN 等），证明平台 masking active。 |
| 14 | `printenv` / `set -x` / `env` dump | **0** | 日志无 `+ cmd` set-x 命令回显、无 `printenv` 调用、无 env dump。 |

### Secret value / raw line output review

- proof 全程只输出 count / job / category / sanitized excerpt；**未打印任何 secret value、未打印可能含值的完整 matching line**。
- Spring generated dev password、disposable DB 值在分析输出中均被 redact / 仅按 category 计数。
- secret-scan `--redact` 生效、`no leaks found`、sanitized 失败分支未触发；唯一 `RuleID=` 命中是 step-script body（jq 模板 `"  RuleID=\(.RuleID) ..."`，cyan `##[group]Run` 回显），非真实 finding。

### False positive classification（逐项）

| FP 类别 | 命中位置 | 判定 |
| --- | --- | --- |
| regex pattern 定义回显 | gate / secret-scan step-script（`AKIA[0-9A-Z]{16}` / `sk-[A-Za-z0-9...]` / `BEGIN ... PRIVATE KEY` / 赋值正则） | non-blocking FP：step 脚本本体回显，非执行输出 |
| jq / sanitized 模板回显 | secret-scan 失败分支脚本（`RuleID=...` 模板） | non-blocking FP：未执行的脚本本体回显 |
| disposable CI 值 | `123456`（backend）、`nq_ci_password`（postgres-flyway） | non-blocking P3：disposable CI-only，明文已在公开 ci.yml；platform service-init 在 mask 前显示 |
| ephemeral dev password | Spring「generated security password」 | non-blocking P3：每次启动随机、ephemeral、dev-only、非 production |
| platform masked token | `***`（GITHUB_TOKEN） | non-blocking：平台 mask 生效 |

真实值形态（完整 AKIA/ASIA、长 sk-/pat/token、完整 PEM、value-bearing 真实凭证、creds-in-URL、真实 signature / raw req-resp / payload）命中数 = **0**，无 P0/P1 阻断项。

### Static workflow assertion

- 本轮**未修改 `.github/workflows/ci.yml`**。静态断言（grep workflow 文本禁止出现 `printenv` / `env` dump / `set -x` / `continue-on-error` / `id-token` / `GITLEAKS_LICENSE` / `gitleaks-action`）列为**可选 future hardening**：落地为最小 step 会改动 workflow、需自身 first-run review，故不混入本 proof 轮，避免引入未验证 workflow 变更。
- 当前等价保证由本轮 review-time 静态复核给出：HEAD `ci.yml` `rg "printenv|set -x|env dump|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action"` = 0；7 jobs 均 `set -euo pipefail`（非 `-x`）。

## Review decision

LOG REDACTION PROOF **LOG PROOF COMPLETED / PENDING FREEZE REVIEW**（`NQ-CI-SECURITY-GUARD-BATCH-4C-C-LOG-REDACTION-PROOF-IMPL`，2026-06-18，P0/P1 = 0）。7 jobs review-time per-job log proof 全部完成，14 类 pattern 真实值命中 = 0，仅 disposable / ephemeral / platform-mask / step-script-echo 非阻断 FP（逐项说明）。**Batch 4C-C 不写 FROZEN**（freeze 留 freeze review 轮）。

前序 PLAN REVIEW **PASS / ACCEPTED AS PROOF / REVIEW BASELINE**（`NQ-CI-SECURITY-GUARD-BATCH-4C-C-PLAN-REVIEW`，2026-06-18，P0/P1 = 0，28 项全部满足）。

本轮已产出 review-time log proof（run `27732660516`，7 jobs，14 类 pattern 真实值命中 = 0）。Batch 4C-C 推进为 **LOG PROOF COMPLETED / PENDING FREEZE REVIEW**，**但不写 FROZEN**；**Batch 4C 整体仍 NOT FROZEN**（freeze 留 freeze review 轮）。Batch 4C-B pre-upload artifact redaction gate 仍 FROZEN / ACCEPTED（不重复、不改），Batch 4B secret scan 仍 FROZEN / ACCEPTED（frozen baseline commit `31540de8`），Batch 4F dependency audit 仍 OPTIONAL / NOT STARTED，Batch 5 frontend E2E hardening 仍 PENDING，均不得写成 started / implemented。

## Next concrete action

Next concrete action：`NQ-CI-SECURITY-GUARD-BATCH-4C-C-FREEZE-REVIEW`（基于 immutable green run `27732660516` 冻结 Batch 4C-C log redaction proof 子基线），或（可选）`NQ-CI-SECURITY-GUARD-BATCH-4C-C-STATIC-ASSERTION`（最小 workflow 静态断言 step + 自身 first-run review），或 `NQ-CI-SECURITY-GUARD-BATCH-4F`（dependency audit later plan）、Batch 5 planning，或暂停 CI 线。

**Batch 4C-C 当前 LOG PROOF COMPLETED / PENDING FREEZE REVIEW**（plan + plan review + log proof 完成，未 FROZEN）；**Batch 4C 整体仍 NOT FROZEN**（只冻结了 4C-B pre-upload gate 子基线，4C-C 待 freeze review）；Batch 4F 仍 OPTIONAL / NOT STARTED；Batch 5 仍 PENDING；不得把 Batch 4C-C 写成 FROZEN，不得把 Batch 4C 整体写成 FROZEN，不得把 Batch 4F / Batch 5 写成 started。
