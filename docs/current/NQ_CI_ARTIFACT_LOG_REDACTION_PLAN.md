# NQ CI Artifact / Log Redaction Plan (Batch 4C)

任务：NQ-CI-SECURITY-GUARD-BATCH-4C-PLAN
日期：2026-06-17
状态：Batch 4C-A plan review **PASS / ACCEPTED AS IMPLEMENTATION BASELINE**（2026-06-17，P0/P1=0）；Batch 4C-B pre-upload artifact redaction gate **FROZEN / ACCEPTED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-E-PRE-UPLOAD-REDACTION-GATE-FREEZE-REVIEW`，2026-06-17，P0/P1/P2 blockers = 0）。frozen baseline = `.github/workflows/ci.yml` `postgres-flyway` job 的 `Pre-upload redaction gate (PostgreSQL schema artifacts)` step（ci.yml blob `4a40ef78`，由 commit `c734102d` 引入；doc-fix `66cb3d40` 与记录 commit `d1664406` 均未触碰 ci.yml，blob 不变），经 **immutable green run `27701669084`（commit `66cb3d40`，event push / branch dev，completed / success，7/7 jobs green）确认**。实现路径回顾：first run（`27698183911`，commit `c734102d`）的 pre-upload gate step 本身 GREEN（postgres-flyway job 全绿、gate 在 upload 前执行、artifact 正常上传），但整体 run FAILED——唯一失败为 secret-scan job 的 gitleaks default-ruleset 误报（`docs/current/TESTING.md` 内一处 AWS 文档示例 access key id，`AKIA` 前缀 + 16 字符，非真实凭证、非 gate 缺陷）；doc-only first-run fix（`66cb3d40`：把该示例串中和为 shaped placeholder，未改 `ci.yml` / gitleaks 规则 / allowlist）后 second-pass run `27701669084` = 7/7 jobs green（secret-scan `no leaks found`，postgres-flyway pre-upload gate 仍 green 且 artifact 在 gate 后正常上传）。详见「Batch 4C-B」/「Batch 4C-D」/「Batch 4C-E」段落。**Batch 4C 整体仍 NOT FROZEN**：本次只冻结 4C-B pre-upload artifact redaction gate 这一子基线，Batch 4C-C log redaction proof 仍 NOT STARTED，因此 Batch 4C 作为整体未冻结；Batch 4F dependency audit OPTIONAL / NOT STARTED；Batch 5 frontend E2E hardening PENDING。本次 freeze review 只评审 + 改 `docs/current`，未改业务代码 / 测试 / migration / frontend / research / scripts / deploy / `.github/workflows/ci.yml` / gitleaks 规则；未新增 allowlist、未关闭 security guard、未使用 repository secret / write / id-token / continue-on-error。Batch 4B minimal secret scan baseline 仍 FROZEN / ACCEPTED（frozen baseline commit `31540de8`，run `27674393780`）。

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
- Status: **FROZEN / ACCEPTED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-E-PRE-UPLOAD-REDACTION-GATE-FREEZE-REVIEW`，2026-06-17，P0/P1/P2=0；frozen baseline = `ci.yml` 的 `Pre-upload redaction gate (PostgreSQL schema artifacts)` step，blob `4a40ef78`，commit `c734102d` 引入、immutable run `27701669084` 确认，详见「Batch 4C-E: freeze review」段）。实现 → first green 路径：IMPLEMENTED（commit `c734102d`）→ first run `27698183911` gate step green 但整体 run 失败于无关文档 gitleaks FP（见「Batch 4C-D」）→ doc-only fix `66cb3d40`（见「Batch 4C-B first-run fix」）→ second-pass run `27701669084` = 7/7 jobs green（见「Batch 4C-B second-pass first-run review」）→ Batch 4C-E freeze。仅改 `.github/workflows/ci.yml` 的 `postgres-flyway` job，把既有 `Check PostgreSQL schema artifacts` step 改造为 **`Pre-upload redaction gate (PostgreSQL schema artifacts)`** step，位置仍在 `Generate ... artifacts` 与 `Upload ... artifacts` 之间（upload 前 fail closed）。inline 实现，未新增 tracked 脚本（优先 inline）。
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
- Status: **LOG PROOF COMPLETED / PENDING FREEZE REVIEW**（`NQ-CI-SECURITY-GUARD-BATCH-4C-C-LOG-REDACTION-PROOF-IMPL`，2026-06-18，P0/P1=0）。前序 plan **PASS / ACCEPTED AS PROOF / REVIEW BASELINE**（plan review 28 项满足）。基于 latest green run `27732660516`（commit `a6d4bf74`，7/7 jobs green，ci.yml blob `4a40ef78` 与 HEAD 一致）的 review-time per-job log proof：7 jobs 全复核，14 类 pattern 真实值命中 = 0，仅 disposable CI 值（`123456` / `nq_ci_password`）/ Spring ephemeral dev password / platform `***` mask / step-script 回显等非阻断 FP（逐项说明），proof 不输出 secret value / 完整匹配行。本轮**未改 ci.yml**（静态断言列为可选 future hardening）、未读本地 logs、未上传 logs artifact。详见 `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md`「Log redaction proof evidence」段。**Batch 4C-C 不写 FROZEN；Batch 4C 整体仍 NOT FROZEN**。
- Scope: review-time `gh run view --log` log proof（覆盖 7 jobs：Diff check / No-outbound guard / Backend Maven test / PostgreSQL / Flyway smoke / Frontend build / Research quality gate / Secret scan）+ 静态断言无 `printenv` / `env` dump / `set -x`；保留 `::add-mask::`；finding 只输出 job / category / rule / safe excerpt；FP 只逐条精确裁定、禁止 broad allowlist；不读取本地 logs、不上传 logs artifact、不自扫 streaming 日志。与 Batch 4C-B（artifact）扫描目标不同，不重复 artifact gate。
- Success target（4C-C 实现轮）: CI 日志无真实 credential / raw request / raw response / signature / encrypted_payload / decrypted_payload 真实值；已知 P3 residual（disposable CI DB 值含 `backend` job 未 mask 的 `123456`、Spring Boot generated dev password、平台 `***` mask）明确标注 disposable / masked。本轮只 planning，不得写成 implemented。

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
- 状态：FIRST-RUN-FIX APPLIED（commit `66cb3d40`）→ second-pass run 已确认（见下）。

#### Batch 4C-B second-pass first-run review（FIRST GREEN RUN CONFIRMED AFTER DOC FIX）

任务 `NQ-CI-SECURITY-GUARD-BATCH-4C-B-SECOND-PASS-FIRST-RUN-REVIEW`，2026-06-17。second-pass run `27701669084`（commit `66cb3d40`，event push / branch dev）= **completed / success**。结论 **PASS / ACCEPTED FOR FIRST GREEN RUN AFTER FIX**；不得直接写 FROZEN（freeze 仍是 Batch 4C-E）。

per-job 结论（7/7 green）：

| Job | 结论 |
| --- | --- |
| Diff check | success |
| No-outbound guard | success |
| Backend Maven test | success |
| **PostgreSQL / Flyway smoke** | **success** |
| Frontend build | success |
| Research quality gate | success |
| **Secret scan** | **success** |

- secret-scan（job 证据）：`Contents: read`；`Installed gitleaks version: 8.18.4`（pinned，非 action / 无 `GITLEAKS_LICENSE`）；`tracked=1304 safe_scanned=1301 excluded=3`（排除恰为三个 `.env.example` 模板）；`gitleaks detect ... --redact` → `scan completed in 934ms` → `no leaks found` → `gitleaks: no leaks found in tracked working tree.`；custom backstop `no non-allowlisted matches`。**不再命中 `docs/current/TESTING.md` aws-access-token**（无 `RuleID=` finding 行，sanitized 失败分支未触发，未输出 secret value）。
- pre-upload redaction gate（postgres-flyway，仍 green）：step-level `✓ Generate PostgreSQL schema artifacts` → `✓ Pre-upload redaction gate (PostgreSQL schema artifacts)` → `✓ Upload PostgreSQL schema artifacts`（顺序正确）；gate 执行输出 `Pre-upload redaction gate: no high-risk credential pattern in artifacts/postgres-flyway (text-only, fail closed).`（只扫 `artifacts/postgres-flyway/`、required artifacts 存在/非空、无 data-row / credential finding、binary 未误杀、fail-closed 生效、未输出 secret value / matched line）；artifact `nq-postgres-flyway-schema-artifacts`（74664 bytes）成功上传，仍唯一 upload-artifact，未上传 raw gitleaks report，`if-no-files-found: error` / retention 有界不变，未新增 surefire / frontend / research artifact。
- 既有 baseline 未回归：Diff check / No-outbound guard / Backend Maven test / Frontend build / Research quality gate 全 green；secret-scan 边界（`contents: read`、无 repository secret / write / id-token / continue-on-error / `gitleaks-action` / `GITLEAKS_LICENSE`、`--no-git --redact`）不变。
- 状态：**FIRST GREEN RUN CONFIRMED AFTER DOC FIX**。下一步只能是 Batch 4C-B freeze review（4C-E）、Batch 4C-C planning，或暂停 CI 线。不得直接写 FROZEN / ACCEPTED；不得把 Batch 4C-C / 4F / Batch 5 写成 started。

### Batch 4C-E: freeze review
- Status: **PASS / FROZEN / ACCEPTED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-E-PRE-UPLOAD-REDACTION-GATE-FREEZE-REVIEW`，2026-06-17，P0/P1/P2 blockers = 0）。
- Scope: 基于 immutable run `27701669084` 冻结 **Batch 4C-B pre-upload artifact redaction gate** 这一子基线；同步 `docs/current` current facts 与 next action。本 freeze 只冻结 pre-upload artifact redaction gate，不冻结 Batch 4C 整体（4C-C log redaction proof 未开始），不进入 Batch 4C-C / 4F / Batch 5，不改 workflow / 代码 / 测试 / migration / gitleaks 规则。
- Frozen baseline：`.github/workflows/ci.yml` `postgres-flyway` job 的 `Pre-upload redaction gate (PostgreSQL schema artifacts)` step（ci.yml blob `4a40ef78`，由 commit `c734102d` 引入）。已校验 `git rev-parse HEAD:.github/workflows/ci.yml` == `66cb3d40:` == `c734102d:` == `4a40ef78`，即 green-confirmed 的 gate 与当前 `dev` HEAD 字节一致；doc-fix `66cb3d40` 与记录 commit `d1664406` 均未触碰 ci.yml。
- GitHub Actions evidence（immutable run `27701669084`，commit `66cb3d40`，event push / branch dev，completed / success）：
  - 7/7 jobs green：Diff check / No-outbound guard / Backend Maven test / PostgreSQL / Flyway smoke / Frontend build / Research quality gate / Secret scan 全 success。
  - Secret scan green：`Installed gitleaks version: 8.18.4`（pinned，非 action / 无 `GITLEAKS_LICENSE` / 无 `gitleaks-action`），`tracked=1304 safe_scanned=1301 excluded=3`，gitleaks `INF no leaks found` + `gitleaks: no leaks found in tracked working tree.`，custom backstop `no non-allowlisted matches`。`docs/current` 不再含完整 `AKIA[0-9A-Z]{16}` 字面量（`git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = 0）。
  - PostgreSQL / Flyway smoke green，step 顺序 `9 Generate PostgreSQL schema artifacts` → `10 Pre-upload redaction gate (PostgreSQL schema artifacts)` → `11 Upload PostgreSQL schema artifacts`：**gate 仍在 upload 前执行**。
  - gate step 输出 `Pre-upload redaction gate: no high-risk credential pattern in artifacts/postgres-flyway (text-only, fail closed).`：required artifacts 存在 / 非空校验通过、binary/zip text-only guard 通过且未误杀 schema artifacts、data-row 检查通过、credential pattern 检查通过、finding 未输出 secret value / matched line / raw content。
  - Upload step：`Artifact nq-postgres-flyway-schema-artifacts has been successfully uploaded! Final size is 74664 bytes`；run artifacts API `total_count=1`，唯一 artifact = `nq-postgres-flyway-schema-artifacts`。未上传 raw gitleaks JSON report（仍只留 `RUNNER_TEMP`），未新增 surefire / frontend / research artifact 上传。
- 边界（冻结时复核）：`permissions` 仅顶层 + secret-scan 两处 `contents: read`；ci.yml 无 `continue-on-error` / `id-token` / write perms / repository secret 引用；未扫描 `.env` / secrets / dumps / logs / backups / `.git` / target / node_modules / dist / build；未读取或输出真实 credential material；未调用真实交易所；未开启 LIVE / AI / DH runtime；未实现 RealClient / real provider / real permission probe adapter。
- Success: Batch 4C-B pre-upload artifact redaction gate 成为当前 `dev` 的 pre-upload artifact redaction baseline；**Batch 4C 整体仍 NOT FROZEN**（4C-C log redaction proof 未开始）；Batch 4C-C / 4F / Batch 5 仍 NOT STARTED / PENDING。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-C`（log redaction proof planning）、Batch 4F later plan、Batch 5 planning，或暂停 CI 线；不得把 Batch 4C 整体写成 FROZEN，不得把 4C-C / 4F / Batch 5 写成 started。

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
- Batch 4C-A plan review ACCEPTED；4C-B pre-upload redaction gate IMPLEMENTED（`c734102d`）；4C-D first-run review FAIL（无关文档 gitleaks FP）；4C-B FIRST-RUN-FIX APPLIED（`66cb3d40`）；4C-B second-pass run `27701669084` 7/7 green → FIRST GREEN RUN CONFIRMED AFTER DOC FIX；4C-E freeze review **PASS / FROZEN / ACCEPTED**（基于 immutable run `27701669084`，P0/P1/P2=0）→ **Batch 4C-B pre-upload artifact redaction gate FROZEN / ACCEPTED**。Batch 4C 整体仍 NOT FROZEN（4C-C log redaction proof 未开始）；4C-C log redaction proof NOT STARTED；Batch 4F 仍 OPTIONAL / NOT STARTED；Batch 5 仍 PENDING；Batch 4B 仍 FROZEN / ACCEPTED。

## Review decision

Batch 4C plan：PLAN READY FOR REVIEW。P0/P1 planning blockers = 0。

Batch 4C-A plan review：**PASS / ACCEPTED AS IMPLEMENTATION BASELINE**（`NQ-CI-SECURITY-GUARD-BATCH-4C-A-PLAN-REVIEW`，2026-06-17，P0/P1=0）。23 项评审 checklist 全部满足，artifact / log 风险盘点、可复用 pre-upload redaction gate 设计、credential pattern 收敛（复用 Batch 4B backstop + schema-check 既有项、避免第 4 套漂移）、artifact / log 输出边界与 Batch 4B / 4F / Batch 5 边界经 `.github/workflows/ci.yml` 只读复核确认；记录 2 项非阻断 P3 实现提示（二进制 / zip 产物扫描策略、PEM 规则取更宽者）。本轮只评审 + 改允许的 docs，未修改 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy，Batch 4C 仍 PLAN ONLY / NOT IMPLEMENTED。

Batch 4C-B pre-upload redaction gate implementation：**IMPLEMENTED**（commit `c734102d`，2026-06-17）。仅改 `.github/workflows/ci.yml` `postgres-flyway` job，把 `Check PostgreSQL schema artifacts` 改造为 upload 前 `Pre-upload redaction gate (PostgreSQL schema artifacts)`：binary 拒绝 + data-row 静默检查 + 收敛后 credential pattern（schema-check 全集 ∪ 4B backstop 高风险项），fail closed，finding 只输出 `rule | file`，`contents: read`、无 repository secret / write / id-token / continue-on-error、未上传 raw gitleaks report。inline 实现、未新增 tracked 脚本、pattern 源仍 3 处。

Batch 4C-D first-run review：**FAIL / FIRST-RUN-FIX REQUIRED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-D-FIRST-RUN-REVIEW`，first run `27698183911`，2026-06-17，completed / failure）。pre-upload gate 本身 first-run GREEN（gate step 在 upload 前执行并 success、postgres-flyway job 全绿、artifact 正常上传、无 finding、无值输出）；整体 run 失败仅因 secret-scan job 的 gitleaks default-ruleset 误报——4C-B 文档更新把 AWS 官方示例 access key id 写进 `docs/current/TESTING.md`（非真实凭证、非 gate 缺陷）。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`（doc-only：中和该示例串，不改 ci.yml / gate）。Batch 4C 整体仍 NOT FROZEN；不得写成 FIRST GREEN RUN CONFIRMED / FROZEN / ACCEPTED。

Batch 4C-B first-run fix：**FIRST-RUN-FIX APPLIED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`，commit `66cb3d40`，2026-06-17）。doc-only：把 `docs/current/TESTING.md` 内 AWS 示例 access key id 字面量中和为 shaped placeholder 文字描述（`git grep AKIA[0-9A-Z]{16} docs/current` = 0）。未改 `.github/workflows/ci.yml`、未改 gate、未改 gitleaks 规则 / 配置、未新增 allowlist、未关闭 default ruleset。

Batch 4C-B second-pass first-run review：**PASS / ACCEPTED FOR FIRST GREEN RUN AFTER FIX**（`NQ-CI-SECURITY-GUARD-BATCH-4C-B-SECOND-PASS-FIRST-RUN-REVIEW`，second-pass run `27701669084`，commit `66cb3d40`，2026-06-17，completed / success）。7/7 jobs green：secret-scan `no leaks found`（gitleaks 8.18.4 / `--redact` / `contents: read`，不再命中 `TESTING.md` aws-access-token、无 finding 行、无值输出，backstop 0 命中），postgres-flyway pre-upload gate 仍 green（gate 在 upload 前执行、`no high-risk credential pattern`、artifact 74664 bytes 正常上传、仍唯一 upload-artifact、未上传 raw gitleaks report），其余 5 job 未回归。Batch 4C-B 推进为 FIRST GREEN RUN CONFIRMED AFTER DOC FIX。

Batch 4C-E freeze review：**PASS / FROZEN / ACCEPTED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-E-PRE-UPLOAD-REDACTION-GATE-FREEZE-REVIEW`，2026-06-17，P0/P1/P2 blockers = 0）。基于 immutable run `27701669084` 冻结 **Batch 4C-B pre-upload artifact redaction gate** 子基线：frozen baseline = `ci.yml` 的 `Pre-upload redaction gate (PostgreSQL schema artifacts)` step（blob `4a40ef78`，commit `c734102d` 引入；已校验 HEAD / `66cb3d40` / `c734102d` 三处该文件 blob 一致），green 证据见「Batch 4C-E: freeze review」段。**Batch 4C 整体仍 NOT FROZEN**（4C-C log redaction proof 未开始）。本次 freeze 只评审 + 改 docs，未改 workflow / 代码 / 测试 / migration / gitleaks 规则，未新增 allowlist、未关闭 security guard。

本 plan 仍是 Batch 4C-B / 4C-C artifact / log redaction proof 的 implementation baseline。Batch 4C-B pre-upload gate 已 FROZEN / ACCEPTED；4C-C log redaction proof 尚未实现。Batch 4B secret scan 仍 FROZEN / ACCEPTED（frozen baseline commit `31540de8`），不重复；Batch 4F dependency audit 仍 OPTIONAL / NOT STARTED；Batch 5 frontend E2E hardening 仍 PENDING，不得写成 started。

## Next concrete action

Next concrete action：Batch 4C-B pre-upload artifact redaction gate **FROZEN / ACCEPTED**（4C-E freeze review，基于 immutable run `27701669084`，P0/P1/P2=0）。Batch 4C-C log redaction proof **planning 已完成**（`NQ_CI_LOG_REDACTION_PROOF_PLAN.md`，PLAN ONLY / NOT IMPLEMENTED）。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-C-PLAN-REVIEW`（对 4C-C plan 做 plan review）、Batch 4C-C plan fix、`NQ-CI-SECURITY-GUARD-BATCH-4C-C` 实现轮、`NQ-CI-SECURITY-GUARD-BATCH-4F`（dependency audit later plan）、Batch 5 planning，或暂停 CI 线。**Batch 4C 整体仍 NOT FROZEN**（只冻结了 4C-B pre-upload gate 子基线，4C-C 仅完成 planning、未实现）；Batch 4F 仍 OPTIONAL / NOT STARTED；Batch 5 仍 PENDING；不得把 Batch 4C-C 写成 implemented；不得把 Batch 4C 整体写成 FROZEN / ACCEPTED；不得把 4F / Batch 5 写成 started。
