# NQ CI Artifact / Log Redaction Plan (Batch 4C)

任务：NQ-CI-SECURITY-GUARD-BATCH-4C-PLAN
日期：2026-06-17
状态：Batch 4C-A plan review **PASS / ACCEPTED AS IMPLEMENTATION BASELINE**（2026-06-17，P0/P1=0）；Batch 4C-B pre-upload redaction gate **IMPLEMENTED**，首次 CI run（`27698183911`，commit `c734102d`）的 **pre-upload gate step 本身 first-run GREEN**（postgres-flyway job 全绿、gate 在 upload 前执行、artifact 正常上传），但**整体 run FAILED**：唯一失败为 secret-scan job 的 gitleaks step，命中 4C-B 文档更新引入的 gitleaks default-ruleset 误报（`docs/current/TESTING.md` 内一处 AWS 官方文档示例 access key id，`AKIA` 前缀 + 16 字符，非真实凭证、非 gate 缺陷）。**Batch 4C-B FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN**（`NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`，2026-06-17，doc-only：把 `TESTING.md` 内该 AWS 示例串中和为 shaped placeholder，未改 `ci.yml` / gitleaks 规则 / allowlist），详见「Batch 4C-B」/「Batch 4C-D」段落。Batch 4C 整体仍 **NOT FROZEN**；Batch 4C-C log redaction proof 未开始；Batch 4F dependency audit OPTIONAL / NOT STARTED；Batch 5 frontend E2E hardening PENDING。本文件未改业务代码 / 测试 / migration / frontend / research / scripts / deploy / `.github/workflows/ci.yml`。Batch 4B minimal secret scan baseline 仍 FROZEN / ACCEPTED（frozen baseline commit `31540de8`，run `27674393780`）；Batch 4F dependency audit 仍 OPTIONAL / NOT STARTED；Batch 5 frontend E2E hardening 仍 PENDING。

本文件是 `NQ_CI_SECURITY_GUARD_PLAN.md` 内「Batch 4C: artifact / log redaction proof」的详细实现规划，独立成文与 `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` / `NQ_CI_POSTGRES_FLYWAY_*_PLAN.md` 同例。

## Task classification

- Primary type: `CI_CD` planning。
- Auxiliary: `ARTIFACT_SECURITY_PLANNING`、`LOG_REDACTION_PLANNING`、`CREDENTIAL_BOUNDARY_REVIEW`、`DOCUMENTATION`。
- Primary skill: `nq-dh-workflow-router`。本轮严格 planning-only，结论来自只读检查与已冻结 Batch 1/2/3/4B 事实源。

## Scope

- repository: NexusQuant / NQ，branch `dev`。
- 只读检查 `.github/workflows/ci.yml`（artifact 生成 / redaction check / upload、`::add-mask::`、gitleaks report 处理）与 `docs/current` CI 事实源。
- 允许新增本文件 + 同步 `NQ_CI_SECURITY_GUARD_PLAN.md` / `NQ_CI_BASELINE_PLAN.md` / `README.md` / `TESTING.md` / `WORKLOG.md`。
- excluded：`.env` / secrets / dumps / logs / backups / `.git` / target / node_modules / dist / build（不作为数据源）。
- expected output：artifact / log redaction proof 的 implementation baseline plan，可供 Batch 4C-B/4C-C 实现。

Forbidden in this planning batch：不改 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy；不读取或输出真实 credential material；不上传未脱敏 artifact；不开启 LIVE / AI / DH；不实现 RealClient / real provider / real probe adapter；不调用真实交易所；不把 Batch 4C 写成 implemented；不把 Batch 4F / Batch 5 写成 started。

## Files inspected

- `.github/workflows/ci.yml`（只读，HEAD `7369ed4f`；frozen secret-scan baseline = commit `31540de8`）。
- `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`、`README.md`、`TESTING.md`、`WORKLOG.md`。

## Files changed

- 新增：本文件 `docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`。
- 同步：`NQ_CI_SECURITY_GUARD_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`、`README.md`、`TESTING.md`、`WORKLOG.md`。
- 未修改 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy。

## Current CI security baseline

- Batch 1 / 2A-2E / 3 no-outbound guard / 4B minimal secret scan 均 FROZEN / ACCEPTED。
- `.github/workflows/ci.yml` 7 jobs：`diff-check`、`no-outbound-guard`、`backend`、`postgres-flyway`、`frontend`、`research`、`secret-scan`。
- 顶层 `permissions: contents: read`；secret-scan job 同样 `contents: read`；无 repository secret 注入；无 `continue-on-error`；无 `id-token` / write perms。
- 当前**唯一**上传 artifact：`nq-postgres-flyway-schema-artifacts`（`postgres-flyway` job，7 个文件：`flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`），生成在 `artifacts/postgres-flyway/`。
  - upload 前有 **`Check PostgreSQL schema artifacts`** redaction step（fail closed）：① schema-dump.sql data-row 检查（无 `INSERT` / `COPY ... FROM stdin` / data dump）；② credential pattern 检查（`.env` / `BEGIN PRIVATE KEY` / `AKIA[0-9A-Z]{16}` / `sk-...` / `apiKey[:=]` / `secret[:=]` / `passphrase[:=]` / `token[:=]` / `cookie[:=]` / `private key[:=]` / `mnemonic[:=]` / `credential material[:=]` / `raw request[:=]` / `raw response[:=]`）。
  - `actions/upload-artifact@v4`，`if-no-files-found: error`，retention 14（push to `dev`）/ 7（其它）。
- `postgres-flyway` job 用 `::add-mask::` 屏蔽 `NQ_FLYWAY_DB_URL` / `NQ_FLYWAY_DB_USER` / `NQ_FLYWAY_DB_PASSWORD`（disposable CI-only 值）。
- secret-scan job（Batch 4B）：gitleaks `--redact`，JSON 报告写入 `RUNNER_TEMP`（`report-path`），**不上传**；失败分支只输出 sanitized RuleID / File / Lines / Fingerprint。
- 当前 **未上传** surefire test reports、frontend build outputs、research outputs。
- backend / no-outbound-guard / secret-scan job 无 `printenv` / `env` dump / `set -x`。

## Artifact risk inventory

| Artifact | 当前状态 | Risk class for 4C | Required proof |
| --- | --- | --- | --- |
| `nq-postgres-flyway-schema-artifacts`（7 files） | 已上传；upload 前已有专用 redaction check（data-row + credential pattern，fail closed） | P2：检查现存但 pattern 集窄于 Batch 4B；且仅针对 schema artifact 专用，不是通用 gate | 4C-B 把它泛化为可复用 pre-upload redaction gate，并把 pattern 与 Batch 4B 收敛 |
| gitleaks JSON report | 写入 `RUNNER_TEMP`，**未上传**；`--redact` | P2：若未来误上传 raw report 会带 file 路径 + redacted 结构 | 4C-B 明文禁止上传 raw gitleaks report（即使 redacted） |
| Surefire test reports（backend） | **未上传** | P2：未来若上传需先过 redaction | 4C-B：任何 surefire 上传前必须过 pre-upload gate；默认保持不上传 |
| frontend build outputs / Playwright report | **未上传** | P2：Batch 5 frontend E2E hardening 可能新增 Playwright report 上传 | 4C 定义 gate；Batch 5 若上传必须先过 gate（4C 不实现 Batch 5） |
| research outputs（pytest / coverage） | **未上传** | P3：未来若上传需先过 gate | 同上 |
| 任意未来 `upload-artifact` | 无统一约束 | P2：缺通用 pre-upload redaction gate | 4C-B 提供通用 gate，要求任何 upload 前调用 |

## Log risk inventory

| 风险 | 当前状态 | Risk class | Required proof |
| --- | --- | --- | --- |
| env dump / `printenv` / `set -x` | 当前各 job 无 | P2：未来误加会泄露 env | 4C-C 静态断言 workflow 无 `printenv` / `env` dump / `set -x` |
| connection string | CI-only DB 值用 `::add-mask::` 屏蔽 | P3 residual：平台级 service 初始化 / 自动 `env:` 显示可能在 mask 前出现（disposable CI-only 值，非真实凭证） | 4C-C 复核 mask 步骤靠前、值为 disposable，记录为已知 P3 |
| raw request / response / signature | 默认 CI 无真实交易所调用（Batch 3 frozen）；adapter 离线构造 | P2：未来 adapter / live diagnostic 误进默认 CI 会打印 raw req/resp | 4C-C log scan 断言无 raw request / response / signature 真实值 |
| API key / secret / passphrase / token | 默认 CI 无真实 credential；gitleaks `--redact` | P2 | 4C-C log scan 断言无真实 credential material |
| private key / PEM block | 仅 Binance fake 测试私钥 / 协议常量（已 allowlist） | P2 | log scan 区分 fake / 协议常量 与真实 PEM |
| encrypted_payload / decrypted_payload 真实值 | DH Integration-0 仅用字段名 / mock / `FAKE-PLACEHOLDER` | P2 | log scan 区分「字段名引用」与「真实 payload 值落地」，后者 fail |
| gitleaks finding 输出 | 仅 sanitized RuleID / File / Lines / Fingerprint | P2（已合规） | 4C-C 保持 sanitized；禁止输出 Secret / Match / matched line / commit / author |

## Redaction proof plan

### 1. 通用 pre-upload redaction gate（4C-B）

- 把现有 `Check PostgreSQL schema artifacts` 的 credential pattern 检查泛化为**可复用的 pre-upload redaction gate**：任何 `actions/upload-artifact` 之前，必须先对待上传目录运行该 gate（扫描目录内文件，命中即 fail closed）。
- gate 只扫 **CI 生成的可控输出目录**（如 `artifacts/**`、`RUNNER_TEMP` 下的报告目录），**不扫**本地禁止目录（`.env` / secrets / dumps / logs / backups / `.git` / target / node_modules / dist / build）。
- gate finding 输出只允许 file / path / rule（pattern 名），**不输出 secret value / 匹配行**。
- gate 保持 fail closed（命中 `exit 1`），禁止 `continue-on-error`。
- schema artifact 现有 data-row 检查（无 `INSERT` / `COPY` / data dump）继续保留，作为 schema-dump 专用补充。

### 2. Credential pattern 收敛（4C-B）

- 当前 credential pattern 存在 **3 处同源漂移风险**：① schema-check inline regex（`ci.yml`）；② secret-scan gitleaks 配置（`useDefault` + allowlist）；③ secret-scan custom backstop pattern。三者目前各自维护。
- 4C-B 的 pre-upload gate 应复用 / 收敛到与 Batch 4B custom backstop 一致的更宽 pattern 集（`sk-ant-` / `sk-proj-` / `github_pat_` / `gh[pousr]_` / `AKIA` / `ASIA` / PEM private key / `xoxb-` / `xoxp-` / value-bearing 凭证赋值 / `encrypted_payload` / `decrypted_payload` 真实值），并保留 schema-check 既有项（`.env` / `cookie` / `raw request` / `raw response` / `mnemonic` / `credential material`）。
- 收敛实现细节留 4C-B；本 plan 只固定「pre-upload gate 不得比现有 schema-check 更弱、不得放宽核心 pattern」。
- 占位例外延续 Batch 4B：`REPLACE_WITH_LOCAL` / `CHANGE_ME` / `FAKE-PLACEHOLDER` / 空赋值 / 明显 fake 测试值；占位例外只能精确 allowlist，禁止 broad allowlist。

### 3. Log redaction proof（4C-C）

- 静态断言：workflow 无 `printenv` / `env` dump / `set -x`；保留 `::add-mask::` 对 disposable CI-only DB 值。
- review-time log scan proof：对 CI job logs（review 时通过 `gh run view --log` 拉取）扫描 credential pattern，输出 proof 表「每类 secret 模式在日志中未出现真实值」。CI 不自扫自身 streaming 日志（runner 限制）；可对 CI 生成的报告文件做扫描。
- 已知 P3 residual（disposable CI PostgreSQL 值平台级显示、Spring Boot generated dev password、`gho_` token mask 为 `***`）继续标注为 disposable / masked，非真实 production credential。
- 保持 gitleaks `--redact` + sanitized finding 输出；secret scan 只报告 file / path / rule。

## Artifact upload boundary

- 只允许上传 **CI 生成的可控输出**，且必须先过 pre-upload redaction gate。
- **禁止上传 raw gitleaks JSON report**（即使 `--redact`）；report 仅留 `RUNNER_TEMP` 供失败分支提取 sanitized 字段。
- `upload-artifact` 必须 `if-no-files-found: error`、retention 有界（延续现有 7/14 天策略）。
- artifact scan 只扫 CI artifact / report 目录，不扫本地禁止目录。
- 不上传未脱敏 artifact；不把 secret / connection string / raw req-resp / signature / private key 写入 artifact。

## Log output boundary

- 只检查当前 CI job logs（review-time）+ masking；**不读取本地 logs**。
- 禁止 `printenv` / `env` dump / `set -x`；禁止打印真实 connection string / API key / secret / passphrase / token / private key / signature / raw request / raw response / encrypted_payload / decrypted_payload 真实值。
- gitleaks / 任何 finding 输出只允许 RuleID / File / Lines / Fingerprint（或 file / path / rule），不输出 secret value / matched line / commit / author。

## Credential pattern reuse

- shared pattern source-of-truth（目标）：以 Batch 4B custom backstop pattern 为基础，并集 schema-check 既有项，作为 pre-upload gate + log scan 的统一 pattern 集。
- 现状 3 处同源（schema-check / gitleaks config / backstop）记为 parity follow-up（与 Batch 3 denylist 三处同源 P3 同类）；4C-B 至少要让 pre-upload gate 引用统一 pattern，不引入第 4 处独立漂移。
- 占位 / fake / 协议常量例外延续 Batch 4B 精确 allowlist 策略。

## Batch 4B / 4F / Batch 5 boundary

- **Batch 4B（FROZEN）**：扫描 tracked **source** 树是否提交真实 secret。4C 不重复、不改 4B。
- **Batch 4C（本计划）**：CI **生成的 artifacts / outputs** 上传前 redaction + **log** redaction proof。与 4B 共享 pattern，但扫描目标不同（生成产物 / 日志，而非源码树）。
- **Batch 4F（OPTIONAL / NOT STARTED）**：dependency audit（`npm audit` / Maven dependency check / `pip-audit`，CVE），与 redaction 无关，非阻断起步，单独分批。
- **Batch 5（PENDING）**：frontend E2E hardening；若新增 Playwright report 上传，必须先过 4C pre-upload gate。4C 只定义 gate，不实现 Batch 5。

## Security boundary

- 不需要也不允许真实 credentials。
- 不读取本地 `.env` / 真实 secret 文件；artifact / log scan 只针对 CI 生成可控输出 / CI job logs。
- 不上传未脱敏 artifact；不上传 raw gitleaks report。
- 保持 `permissions: contents: read`；不注入 repository secret；不使用 write / id-token；不使用 `continue-on-error` 掩盖安全失败。
- 不开启 LIVE / AI / DH runtime；不实现 RealClient / real provider / real permission probe adapter；不调用真实交易所。
- finding 输出只报 file / path / rule，绝不打印命中值。

## Batch 4C implementation strategy

### Batch 4C-A: artifact / log redaction plan review
- Status: **PASS / ACCEPTED AS IMPLEMENTATION BASELINE**（`NQ-CI-SECURITY-GUARD-BATCH-4C-A-PLAN-REVIEW`，2026-06-17，P0/P1=0）。
- Scope: 仅文档与只读 source review。
- Success: 本 plan P0/P1=0；artifact / log 风险盘点、pre-upload gate 设计、pattern 收敛、边界被接受；无 workflow / 代码 / 测试 / migration 改动。
- Review evidence（23 项评审 checklist 全部满足，对 `.github/workflows/ci.yml` HEAD `1aa8515f` 只读复核）：
  - artifact inventory 准确：唯一上传 artifact = `nq-postgres-flyway-schema-artifacts`（`ci.yml` 唯一 `actions/upload-artifact@v4`，第 600 行）；surefire / frontend build / research outputs 当前未上传；gitleaks JSON report 写 `RUNNER_TEMP` 未上传。
  - schema artifact 既有 `Check PostgreSQL schema artifacts`（data-row + credential pattern，fail closed）被识别为通用 pre-upload gate 先例。
  - P2 识别完整：无通用 pre-upload gate、schema-check pattern 窄于 Batch 4B backstop、3 处同源漂移、raw report 误上传风险；并明文禁止上传 raw gitleaks JSON report、artifact scan 只扫 CI 生成可控输出、禁止扫描本地禁止目录。
  - log risk inventory 覆盖 env dump / `set -x` / raw request-response / connection string / signature / credential material / encrypted_payload-decrypted_payload；CI logs proof 只做 review-time `gh run view --log`，不读本地 logs；finding 只输出 file/path/rule，不输出 secret value。
  - credential pattern 复用 Batch 4B backstop + schema-check 既有项，规划同源 parity，避免第 4 套漂移；保留 `contents: read`，禁止 repository secret / write / id-token / continue-on-error。
  - 边界：4C 不重复 4B source-tree secret scan、不做 4F dependency audit、不做 Batch 5 frontend E2E hardening；Batch 5 若上传 Playwright report 必须先过 4C gate；仍禁止 LIVE / AI / DH runtime / RealClient / real provider；允许进入 4C-B minimal pre-upload redaction gate implementation。
  - 评审期复核：`git status` clean、forbidden 区域 0 diff；`.github` 内唯一 `upload-artifact` 即 schema artifacts，无 `continue-on-error` / `id-token` / `GITLEAKS_LICENSE` / `gitleaks-action`；rg 命中均为 docs / 契约字段名 / JWT auth 代码引用，无真实 credential material（与 Batch 4B frozen 0-findings 一致）。
- 非阻断 P3 实现提示（留给 4C-B，不影响 baseline 接受）：① pre-upload gate 泛化后若指向含二进制 / zip 的产物目录（如未来 Playwright trace.zip / 截图 / 视频），`grep` 文本扫描需明确二进制 / 压缩包处理策略，避免漏扫或噪声；② pattern 收敛时 PEM 规则取 schema-check（`BEGIN [A-Z ]*PRIVATE KEY`）与 backstop（带 `-----` 前缀分组）二者更宽者，不得弱化。

### Batch 4C-B: pre-upload redaction gate minimal implementation
- Status: **IMPLEMENTED；gate FIRST-RUN GREEN（run `27698183911` 的 gate step + postgres-flyway job 全绿），整体 run FAILED 于无关文档 gitleaks FP → FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN**（FP 见「Batch 4C-D」；fix 见下文「Batch 4C-B first-run fix」；commit `c734102d`，2026-06-17）。仅改 `.github/workflows/ci.yml` 的 `postgres-flyway` job，把既有 `Check PostgreSQL schema artifacts` step 改造为 **`Pre-upload redaction gate (PostgreSQL schema artifacts)`** step，位置仍在 `Generate ... artifacts` 与 `Upload ... artifacts` 之间（upload 前 fail closed）。inline 实现，未新增 tracked 脚本（优先 inline）。
- 实现要点：
  - gate 只扫 `artifacts/postgres-flyway/`（CI 生成可控目录），不扫 `.env` / secrets / 本地禁止目录。
  - binary / zip handling：先用 `file -b --mime-encoding` 逐文件判定，命中 `binary` 即 fail closed 拒绝（zip / video / screenshot 等二进制留待 Batch 5），避免对二进制做不可靠文本 grep；当前 schema artifacts 全为 text，正常通过。
  - schema-dump data-row 检查保留，并由 `-RInE`（会回显匹配行）改为 `grep -qE`（静默），避免输出 matched line。
  - credential pattern 收敛为「既有 schema-check 全集 ∪ Batch 4B backstop 高风险项」：`\.env` / `BEGIN [A-Z ]*PRIVATE KEY`（dash-omitted，避免被 secret-scan 自命中）/ `AKIA` / `ASIA` / `sk-`（含 sk-ant- / sk-proj-）/ `gh[pousr]_` / `github_pat_` / `xoxb-` / `xoxp-` / credentials-in-URL（`scheme://user:pass@`）/ value-bearing `apiKey`-`api_key` / `apiSecret`-`secret` / `passphrase` / `token` / `password` / `cookie` / `privateKey`-`private key` / `mnemonic` / `signature` / `credential material` / `raw request` / `raw response` / `encrypted_payload` / `decrypted_payload`。不弱于既有 schema-check（camelCase `apiKey` / `privateKey` 仍显式覆盖）。
  - finding 输出只 `rule | file`（per-rule `grep -rIlE -l`），绝不输出 secret value / matched line / raw content；fail closed（`exit 1`）。
  - 保持 `permissions: contents: read`；无 repository secret / write / id-token / continue-on-error / `gitleaks-action` / `GITLEAKS_LICENSE`；未上传 raw gitleaks JSON report（gitleaks report 仍只留 `RUNNER_TEMP`）；当前唯一 artifact 上传路径 `nq-postgres-flyway-schema-artifacts` 已接入 gate。
- parity follow-up（满足实现要求 9）：gate inline pattern 集是「former schema-check 源」的演进，pattern 源总数仍为 3（本 gate + secret-scan gitleaks config + secret-scan backstop），未引入第 4 套独立漂移源；待出现第 2 个 upload 路径时，再把 gate body 提升为 `.github/scripts` 共享 helper 收敛为单一来源。
- 本地验证（已执行，证据见 `TESTING.md` 同名段）：bash 语法检查通过；YAML 结构校验通过（7 jobs / 唯一 upload-artifact / 无 tab）；gate dry-run——clean schema-like artifacts PASS（`password_hash` 列名、散文 "API key"、无凭证 URL 均不误报），fake-secret artifact fail closed 且只输出 `rule | file`（AKIA example / url password / payload 值均未打印），binary artifact 被拒；secret-scan custom backstop 对修改后 ci.yml 0 自命中。
- 未验证项（PENDING FIRST CI RUN，归 Batch 4C-D）：gitleaks default-ruleset 对修改后 ci.yml 的完整 FP 面、真实 PostgreSQL schema 输出对新增 pattern 的实际命中面（共享子集已由既有 schema-check 在 Batch 4B 绿灯证明），均待 GitHub Actions 首跑确认；`file` / `find` 在 ubuntu-latest 默认可用。
- 边界：未做 Batch 4C-C log redaction proof；未做 Batch 4F dependency audit；未做 Batch 5 frontend E2E hardening；未新增 Playwright / frontend / research artifact 上传。

### Batch 4C-C: log redaction proof
- Status target: IMPLEMENTED / evidence 收集。
- Scope: 静态断言无 `printenv` / `env` dump / `set -x`；保留 `::add-mask::`；review-time log scan proof 表。
- Success: CI 日志无真实 credential / raw request / raw response / signature / encrypted_payload / decrypted_payload 真实值；已知 P3 residual 明确标注 disposable / masked。

### Batch 4C-D: first-run review
- Status: **FAIL / FIRST-RUN-FIX REQUIRED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-D-FIRST-RUN-REVIEW`，first run `27698183911`，commit `c734102d`，event push / branch dev，2026-06-17，completed / failure）。
- Scope: 评审第一次包含 pre-upload gate 的 GitHub Actions run、jobs、steps、logs、artifacts。
- per-job 结论（run `27698183911`）：

  | Job | 结论 |
  | --- | --- |
  | Diff check | success |
  | No-outbound guard | success |
  | Backend Maven test | success |
  | **PostgreSQL / Flyway smoke** | **success** |
  | Frontend build | success |
  | Research quality gate | success |
  | **Secret scan** | **failure** |

- **pre-upload redaction gate 本身 first-run GREEN**：postgres-flyway job step-level `✓ Generate PostgreSQL schema artifacts` → `✓ Pre-upload redaction gate (PostgreSQL schema artifacts)` → `✓ Upload PostgreSQL schema artifacts`（顺序正确，gate 在 upload 前）。gate 日志输出 `Pre-upload redaction gate: no high-risk credential pattern in artifacts/postgres-flyway (text-only, fail closed).`，证明：只扫 `artifacts/postgres-flyway/`、text-only 断言通过（binary 未误杀）、无 data-row / credential finding、fail-closed 模式生效；artifact `nq-postgres-flyway-schema-artifacts`（74663 bytes）成功上传，未上传 raw gitleaks report，`if-no-files-found: error` / retention 有界不变。
- **唯一失败 = secret-scan job 的 gitleaks step**，类别为 **gitleaks default-ruleset false positive（非 gate 缺陷、非真实泄露）**：4C-B 文档更新把 gate dry-run 用的 AWS 官方文档示例 access key id（`AKIA` 前缀 + 16 字符）写进了 `docs/current/TESTING.md`，被 gitleaks `aws-access-token` 规则命中。sanitized 日志只输出 `RuleID=aws-access-token File=docs/current/TESTING.md Lines=16-16 Fingerprint=docs/current/TESTING.md:aws-access-token:16`，`--redact` 生效、**未输出 secret value / matched line / Match / Secret / commit / author**。custom backstop step 因 gitleaks step 先失败被 skip。
- 排除项：非 gate pattern FP（gate green）、非 binary detection 误杀（schema text 正常过）、非 artifact 缺失 / 非空检查失败、非 YAML / script 错误、非 upload ordering 错误、非真实 leak（AWS 公共文档示例，非真实凭证，P0=0）。
- Review decision：FAIL / FIRST-RUN-FIX REQUIRED。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`。
- FIRST-RUN-FIX 建议（doc-only）：把 `docs/current/TESTING.md` 内该 AWS 示例 access key id 改写为不可被 gitleaks 命中的形态（如 `AKIA…EXAMPLE` 省略中段或纯文字描述「`AKIA` 前缀 + 16 字符占位」），**不改 `ci.yml`、不改 gate、不放宽核心规则、不 broad allowlist**。修复后重跑 CI，确认 secret-scan green、postgres-flyway gate 仍 green。

#### Batch 4C-B first-run fix（FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN）

任务 `NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`，2026-06-17。

- 定位：`docs/current/TESTING.md` 内 4C-B 测试记录的「gate dry-run — fake secret」单元格曾写有一处完整 AWS access key id 形态字面量（`AKIA` 前缀 + 16 位大写字母/数字），被 gitleaks `aws-access-token` 默认规则命中（first run `27698183911` 的唯一 finding）。该串是 AWS 公共文档示例、非真实凭证（P0=0）。
- 修复（最小、doc-only）：把该单元格改写为 **shaped placeholder 文字描述**（`AKIA` 前缀 + 16 位占位，不写完整字面量；并标注「不写完整字面量以免触发 gitleaks `aws-access-token`」），同段内 `AKIA example` 文案一并改为 `AWS key 占位`。未改 `.github/workflows/ci.yml`、未改 gate、未改 gitleaks 规则 / 配置、未新增任何 allowlist、未关闭 default ruleset、未 broad allowlist、未 allowlist 整个 `TESTING.md`。
- 本地验证：`git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = **0 命中**（修复后 docs/current 已无完整 AWS-key 字面量）；docs/current 内亦无其它 `ASIA` / `sk-ant-` / `sk-proj-` / `github_pat_` / `gh[pousr]_{30,}` 完整凭证形态字面量。本地无 gitleaks 二进制（Windows 开发环境，与 Batch 4B 一致），gitleaks 层最终结果待 GitHub Actions second-pass run 确认。
- 状态：FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN。下一步只能是 second-pass first-run review（确认 secret-scan job green、postgres-flyway pre-upload gate 仍 green、其余 job 未回归），或失败则 second-pass fix，或暂停 CI 线。不得把 Batch 4C-B 写成 FIRST GREEN / FROZEN；不得混入 Batch 4C-C / 4F / Batch 5。

### Batch 4C-E: freeze review
- Status target: FROZEN / ACCEPTED。
- Scope: 冻结 artifact / log redaction baseline；同步 `docs/current`。
- Success: Batch 4C 成为当前 `dev` artifact / log redaction baseline；Batch 4F / Batch 5 仍未开始 / PENDING。

## P0/P1/P2/P3 findings

| Priority | Finding | Decision |
| --- | --- | --- |
| P0 | None for this planning-only baseline。 | P0 planning blockers = 0。 |
| P1 | None for this planning-only baseline。 | P1 planning blockers = 0。 |
| P2 | 当前无通用 pre-upload redaction gate（仅 schema artifact 专用）。 | Batch 4C-B 泛化为可复用 gate。 |
| P2 | schema-check credential pattern 集窄于 Batch 4B backstop。 | Batch 4C-B 收敛到统一更宽 pattern，不弱化。 |
| P2 | credential pattern 3 处同源漂移（schema-check / gitleaks config / backstop）。 | parity follow-up；4C-B 不引入第 4 处独立漂移。 |
| P2 | raw gitleaks JSON report 若误上传带 file 路径 / redacted 结构。 | 4C-B 明文禁止上传 raw report。 |
| P3 | disposable CI PostgreSQL 值平台级显示 / Spring Boot dev password。 | 继续标注 disposable / masked，非真实凭证；4C-C 复核。 |
| P3 | log redaction proof 依赖 review-time `gh run view --log`（CI 不自扫 streaming 日志）。 | 接受为 review-time 证据 + 静态 `printenv`/`env`/`set -x` 断言组合。 |

## Validation

本轮 planning / doc 验证（只读，已执行）：

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
rg "artifact|upload-artifact|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action" .github docs/current backend frontend research
```

执行结果摘要：

- `git status --short` clean；`git diff --check` clean；`git diff --stat` 仅本轮允许的 `docs/current` 文件。
- `git diff -- .github/workflows/ci.yml / backend / frontend / research / scripts / deploy / backend/**/db/migration` 均空（forbidden 区域无改动）。
- `.github/workflows/ci.yml` 仅 1 处 `upload-artifact`（schema artifacts），upload 前有专用 redaction check；无 `printenv` / `env` dump / `set -x` / `continue-on-error` / `id-token` / write perms；`permissions` 仅 `contents: read`。
- gitleaks JSON report 写 `RUNNER_TEMP` 未上传；`--redact` 生效。
- rg 命中均为 docs CI 事实源引用 + ci.yml 的 artifact/redaction/secret-scan 既有项，无真实 credential material（whole-tree gitleaks 0 findings + backstop 0 命中已在 Batch 4B 冻结证据中验证）。

rg 仅用于 tracked safe paths；未扩展到 `.env` / secrets / logs / dumps / backups / target / node_modules / dist / build / `.git`。本轮 docs-only / planning-only，未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。

## Boundary confirmation

- 未修改 `.github/workflows/ci.yml`。
- 未修改 Java / TypeScript / Python 代码与测试代码；未新增测试。
- 未新增 API；未新增 migration；未修改历史 migration。
- 未修改 backend production code / frontend / research / scripts / deploy。
- 未读取、打印、复制或输出真实 credential material；未把禁止目录作为数据源扫描；未上传 artifact。
- 未调用真实交易所；未开启 LIVE / AI / DH runtime；未实现 RealClient / real provider / real permission probe adapter。
- Batch 4C-A plan review ACCEPTED；4C-B pre-upload redaction gate IMPLEMENTED；4C-D first-run review FAIL（无关文档 gitleaks FP）；4C-B FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN。Batch 4C 整体仍 NOT FROZEN；4C-C log redaction proof NOT STARTED；Batch 4F 仍 OPTIONAL / NOT STARTED；Batch 5 仍 PENDING；Batch 4B 仍 FROZEN / ACCEPTED。

## Review decision

Batch 4C plan：PLAN READY FOR REVIEW。P0/P1 planning blockers = 0。

Batch 4C-A plan review：**PASS / ACCEPTED AS IMPLEMENTATION BASELINE**（`NQ-CI-SECURITY-GUARD-BATCH-4C-A-PLAN-REVIEW`，2026-06-17，P0/P1=0）。23 项评审 checklist 全部满足，artifact / log 风险盘点、可复用 pre-upload redaction gate 设计、credential pattern 收敛（复用 Batch 4B backstop + schema-check 既有项、避免第 4 套漂移）、artifact / log 输出边界与 Batch 4B / 4F / Batch 5 边界经 `.github/workflows/ci.yml` 只读复核确认；记录 2 项非阻断 P3 实现提示（二进制 / zip 产物扫描策略、PEM 规则取更宽者）。本轮只评审 + 改允许的 docs，未修改 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy，Batch 4C 仍 PLAN ONLY / NOT IMPLEMENTED。

Batch 4C-B pre-upload redaction gate implementation：**IMPLEMENTED**（commit `c734102d`，2026-06-17）。仅改 `.github/workflows/ci.yml` `postgres-flyway` job，把 `Check PostgreSQL schema artifacts` 改造为 upload 前 `Pre-upload redaction gate (PostgreSQL schema artifacts)`：binary 拒绝 + data-row 静默检查 + 收敛后 credential pattern（schema-check 全集 ∪ 4B backstop 高风险项），fail closed，finding 只输出 `rule | file`，`contents: read`、无 repository secret / write / id-token / continue-on-error、未上传 raw gitleaks report。inline 实现、未新增 tracked 脚本、pattern 源仍 3 处。

Batch 4C-D first-run review：**FAIL / FIRST-RUN-FIX REQUIRED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-D-FIRST-RUN-REVIEW`，first run `27698183911`，2026-06-17，completed / failure）。pre-upload gate 本身 first-run GREEN（gate step 在 upload 前执行并 success、postgres-flyway job 全绿、artifact 正常上传、无 finding、无值输出）；整体 run 失败仅因 secret-scan job 的 gitleaks default-ruleset 误报——4C-B 文档更新把 AWS 官方示例 access key id 写进 `docs/current/TESTING.md`（非真实凭证、非 gate 缺陷）。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`（doc-only：中和该示例串，不改 ci.yml / gate）。Batch 4C 整体仍 NOT FROZEN；不得写成 FIRST GREEN RUN CONFIRMED / FROZEN / ACCEPTED。

Batch 4C-B first-run fix：**FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN**（`NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`，2026-06-17）。doc-only：把 `docs/current/TESTING.md` 内 AWS 示例 access key id 字面量中和为 shaped placeholder 文字描述（`git grep AKIA[0-9A-Z]{16} docs/current` = 0）。未改 `.github/workflows/ci.yml`、未改 gate、未改 gitleaks 规则 / 配置、未新增 allowlist、未关闭 default ruleset。gate 本身仍 first-run green；gitleaks 层最终结果待 second-pass GitHub Actions run 确认。不得写成 FIRST GREEN / FROZEN。

本 plan 可作为 Batch 4C-B / 4C-C artifact / log redaction proof 的 implementation baseline。Batch 4C-B 已实现 pre-upload gate（gate first-run green）；4C-C log redaction proof 尚未实现。Batch 4B secret scan 仍 FROZEN / ACCEPTED（frozen baseline commit `31540de8`），不重复；Batch 4F dependency audit 仍 OPTIONAL / NOT STARTED；Batch 5 frontend E2E hardening 仍 PENDING，不得写成 started。

## Next concrete action

Next concrete action：Batch 4C-B **FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN**（doc-only 已中和 `TESTING.md` AWS 示例串）。下一步只能是 second-pass first-run review（确认重跑后 secret-scan job green、postgres-flyway pre-upload gate 仍 green、其余 job 未回归），或失败则 second-pass fix，或暂停 CI 线。不得混入 Batch 4C-C / 4F / Batch 5。Batch 4C 整体仍 NOT FROZEN；Batch 4F 仍 OPTIONAL / NOT STARTED；Batch 5 仍 PENDING；不得把 Batch 4C-B 写成 FIRST GREEN / FROZEN；不得把 4C-C / 4F / Batch 5 写成 started。
