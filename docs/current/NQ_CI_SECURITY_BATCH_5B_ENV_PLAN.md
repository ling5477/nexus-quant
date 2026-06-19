# NQ CI Security Batch 5B-ENV Plan

任务：NQ-CI-SECURITY-BATCH-5B-ENV-PLAN
日期：2026-06-19
分支：dev
任务类型：CI_SECURITY_PLANNING + ENVIRONMENT_BOUNDARY_REVIEW + SECRET_GUARD_REVIEW + NO_OUTBOUND_BOUNDARY_REVIEW + DOCUMENTATION
状态：**Batch 5B-ENV = PLAN ONLY / READY FOR REVIEW**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。

> 本文是 planning-only 文档。本轮不创建 / 不修改任何 workflow，不改代码，不新增 API，不新增 migration，不启动 LIVE / AI / DH runtime / real provider，不做真实外联。所有 "implementation" 措辞均指**后续另起批次**才执行，不代表已实现。

主 skill：`nq-dh-workflow-router`（NQ / Gate / CI / security 边界分类与范围限定）。
辅助 skill / MCP：本轮无（纯只读盘点 + 文档登记，未触发前端 / 后端 / DB 修复类 skill；未使用 postgres / chrome-devtools / docker 等运行态 MCP，因为不允许真实联调）。

---

## 1. 当前状态盘点（read-only inventory）

只读检查范围：`.github/workflows/ci.yml`、`backend/nq-app/src/main/resources/application*.yml`、`frontend/playwright.ci.config.ts`、`research/py`、`.env.example`、`docs/current/*`。未读取任何真实 `.env` / secrets / credentials / logs / dumps / backups。

### 1.1 当前 CI workflow job 清单

`.github/workflows/ci.yml`（唯一 workflow，触发：`pull_request` / `push` 到 `dev`、`workflow_dispatch`；`permissions: contents: read`）共 8 个 job：

| Job | 名称 | 作用 | 关键边界 |
| --- | --- | --- | --- |
| `diff-check` | Diff check | `git diff --check` 空白检查 | 无 env，无网络 |
| `no-outbound-guard` | No-outbound guard | 校验未注入交易所凭证/LIVE env、denylist 覆盖、运行 `NoOutboundExchangeGuardTest` | 设 `NQ_NO_OUTBOUND_DENYLIST` host 清单（非敏感） |
| `backend` | Backend Maven test | `mvn -f backend/pom.xml test` + CI-only legacy account fixture | PostgreSQL service（disposable `123456`） |
| `postgres-flyway` | PostgreSQL / Flyway smoke | Flyway migrate+validate（期望 V31）、schema artifact、repo/app smoke | disposable `nq_ci_user`/`nq_ci_password`（`::add-mask::` 掩码）；上传前 redaction gate |
| `frontend` | Frontend build | `npm ci` + `npm run build` | 仅 `CI=true` |
| `frontend-no-backend-e2e` | Frontend no-backend E2E (Batch 5A) | 4-spec no-backend allowlist，loopback `vite preview` | 仅 `CI=true`；无 backend / token / API base URL |
| `research` | Research quality gate | `pytest` / `mypy` / `ruff` | 仅 `CI=true` + `PIP_DISABLE_PIP_VERSION_CHECK=1` |
| `secret-scan` | Secret scan | 固定版 gitleaks 8.18.4 + 自定义 regex backstop | `--redact`；排除 .env/secrets/credentials/logs/dumps/backups/target/node_modules/dist/build/.git |

### 1.2 CI 是否设置真实 secret

**否。** CI 仅注入非敏感控制值与一次性 disposable 值：

- `CI=true`、`PIP_DISABLE_PIP_VERSION_CHECK=1`、`GITLEAKS_VERSION=8.18.4`。
- disposable PostgreSQL 连接：`backend` job 用 `postgres`/`123456`/`nexus_quant`；`postgres-flyway` job 用 `nq_ci_user`/`nq_ci_password`/`nq_ci`，且通过 `echo "::add-mask::..."` 掩码。
- `NQ_NO_OUTBOUND_DENYLIST`：交易所域名 denylist（公开 host 名，非凭证）。
- 未发现任何 `secrets.*` 引用注入交易所 API key / secret / passphrase / private key / token。

### 1.3 backend 测试 profile 现状

- `application.yml`：`spring.profiles.active=${NQ_PROFILE:local}`，默认 `local`。
- 现存 Spring profile：`local` / `test` / `prod` / `freeze` / `gated-verify`。
- **不存在** `ci` profile、`paper` profile、`live` profile。CI `backend` job 直接 `mvn test`，profile 由测试类 `@ActiveProfiles` 或默认解析决定，没有统一的 `ci` 收口 profile。
- 安全默认（已存在且良好）：`okx.adapter.stub-on-bootstrap-failure` 默认 `true`（local/freeze）、`okx.recovery.enabled` local/freeze 为 `false`、`okx.ws.enabled` / `binance.ws.enabled` 默认 `false`、`instrument.catalog-sync.enabled` freeze 为 `false`、`gated.verify.enabled` 默认 `false`。

### 1.4 frontend E2E 环境变量现状

- `playwright.ci.config.ts`：仅 `CI=true`；`HOST=127.0.0.1`、`PORT=5179`、`baseURL=http://127.0.0.1:5179`；`trace/screenshot/video=off`；`reporter=line`；不用 `storageState`；`webServer` 用 `vite preview` 只绑 `127.0.0.1`。
- 不注入 API base URL / token / cookie / Authorization / 交易所凭证 / DB 连接。

### 1.5 research 测试是否依赖外部环境

- `research` job 仅 `CI=true` + `PIP_DISABLE_PIP_VERSION_CHECK=1`，安装 `.[dev]` 后跑 `pytest -q` / `mypy src` / `ruff check .`，未发现注入网络 / 凭证依赖。

### 1.6 是否存在 `.env.example` / `.env.template`

- 存在 `.env.example`（仓库根）。敏感项一律用 `REPLACE_WITH_LOCAL_*` 占位，**无真实凭证值**（与 secret-scan allowlist 一致）。
- 占位规范约定（文件头注释）：每项中文注释、非敏感固化默认、敏感仅占位、不混 dome/real、切换重启。

### 1.7 是否存在误导性 LIVE / real provider / exchange credential 配置

- P2 级误导项：`application.yml` 中 `nq.okx.base-url` 默认 `https://www.okx.com`、`nq.okx.env` 默认 `dome`；`.env.example` 含 `NQ_OKX_REAL_BASE_URL=https://www.okx.com`、`NQ_BINANCE_REAL_BASE_URL=https://api.binance.com` 及 real WS URL。这些是**真实交易所 host 默认值**（非凭证），在未来 5B-ENV 实施时需要明确：CI / test profile 必须**不**加载这些 real base-url，且 no-outbound denylist 已覆盖这些 host。
- 未发现仓库内存在真实交易所凭证值；未发现 `NQ_LIVE_ENABLED` / `NQ_REAL_PROVIDER_ENABLED` / `NQ_REAL_CLIENT_ENABLED` 被设为 `true` 的配置（no-outbound-guard 已把这些 env 名列为 forbidden）。

### 1.8 no-real-exchange / mock / paper / test profile 边界是否清晰

- **部分清晰，但分散**。安全默认值散落在 `application.yml` / `application-local.yml` / `application-freeze.yml` 各处；`NoRealExchangeCredentialPermissionProbePort` 是默认 port（返回脱敏 `SKIPPED` / `REAL_EXCHANGE_PROBE_DISABLED`）；`NoOutboundExchangeGuardTest` 在 CI 强制。
- 缺口（5B-ENV 要解决）：没有单一权威的"环境分层 + provider 隔离"事实源，没有 `ci`/`paper` 显式 profile，没有 fail-closed 的 env 冲突校验。

### 1.9 文档是否把 5B-SMOKE 写成可直接启动

- **否（当前状态正确）**。`NQ_CI_BASELINE_PLAN.md`、`ROADMAP.md`、`STATUS.md` 均写明 `5B-ENV = NOT STARTED`、`5B-SMOKE = BLOCKED BY 5B-ENV`。
- P3 级文档漂移（仅登记，不在本轮修复）：`README.md` / `ROADMAP.md` 称 "Batch 5A FROZEN / ACCEPTED" 与 "CI mainline COMPLETED / ACCEPTED"，而 `NQ_CI_BASELINE_PLAN.md` Batch 5 段仍写 "Batch 5A IMPLEMENTED / READY FOR FIRST-RUN"。本任务以任务事实 `Batch 5A = FROZEN / ACCEPTED` 为准登记，不改写 baseline plan 的 5A 措辞。

---

## 2. 环境变量分层规范（forward-looking design）

> 以下分层为 5B-ENV-A 文档冻结 + 5B-ENV-B 实施目标。除 `NQ_PROFILE` 外，多数控制变量为**新增规范**，本轮只规划、不落地。

### 2.1 CI required env（仅非敏感控制变量）

```text
NQ_ENV=ci
NQ_PROFILE=ci
NQ_TRADE_MODE=paper
NQ_LIVE_ENABLED=false
NQ_REAL_EXCHANGE_ENABLED=false
NQ_AI_ENABLED=false
NQ_DH_ENABLED=false
NQ_NO_OUTBOUND=true
```

规则：CI 只允许以上无敏感值的控制开关 + disposable DB 连接（已掩码）+ denylist。禁止在 CI 注入任何交易所凭证、私钥、token。

### 2.2 Local dev env

- 只允许本地开发非敏感默认值（端口、DB 名、disposable 本地口令、`REPLACE_WITH_LOCAL_*` 占位）。
- 真实密钥**只能写在本机 `.env`**，不得进入仓库 / CI / 文档示例。
- `.env.example` 是唯一规范样板；新增配置先写样板，再同步本地。

### 2.3 Test env（默认安全口径）

```text
LIVE=false
real provider=false
real exchange=false
AI=false
DH=false
no-outbound=true
```

- test profile 必须默认这些值；测试不得创建真实 HTTP client，不得真实探活。

### 2.4 Forbidden env（CI / test / docs 示例中禁止出现真实值）

```text
API_KEY
SECRET
PASSPHRASE
TOKEN
PRIVATE_KEY
MNEMONIC
REAL_EXCHANGE_CREDENTIAL
```

- 只允许以明确占位形式出现，并标注：

```text
DO_NOT_COMMIT_REAL_VALUE
PLACEHOLDER_ONLY
```

- 现状对齐项（5B-ENV-A 决策）：仓库现有占位规范为 `REPLACE_WITH_LOCAL_*` / `CHANGE_ME` / `FAKE-PLACEHOLDER`（已在 secret-scan allowlist）。5B-ENV-A 需决定是统一迁移到 `DO_NOT_COMMIT_REAL_VALUE` / `PLACEHOLDER_ONLY`，还是把这两个新标记**追加**进 allowlist 与占位规范，二选一并记录原因；本轮不改 `.env.example`，仅登记选项。

---

## 3. Profile / provider 隔离规范

- 明确边界：`local` / `test` / `ci` / `paper` / `live`。
  - `local`：本地开发，走 DB 密文路径但默认不真实外网探活（`STRUCTURAL` 校验、recovery/ws 关闭、stub-on-bootstrap-failure=true）。
  - `test`：单测 / 集成，默认 fail-closed 安全口径（§2.3）。
  - `ci`（新增目标）：CI 专用，等价 test 安全口径 + `NQ_NO_OUTBOUND=true`，不加载真实 provider。
  - `paper`（新增目标）：Paper / mock / no-real-exchange 默认安全路径。
  - `live`（保留，禁用态）：只能在**未来单独安全 proposal** 中启用，当前必须 disabled。
- 默认 profile 不得进入 LIVE：`active` 默认 `local`，`live` 不在任何默认链路。
- CI profile 不得加载真实 provider；test profile 不得创建真实 HTTP client。
- Paper / mock / no-real-exchange provider 必须是默认安全路径；`NoRealExchangeCredentialPermissionProbePort` 继续作为默认装配。
- Real provider 只能在未来单独安全 proposal 启用，不在 5B-ENV / 5B-SMOKE 范围。
- **fail-closed 冲突规则（规划目标，5B-ENV-B 实施）**：
  - `LIVE=true` 且 `NO_OUTBOUND=true` → 视为非法配置，启动 fail closed。
  - CI 检测到 real credential material（API key/secret/passphrase/private key/mnemonic）→ fail closed。
  - `NQ_REAL_EXCHANGE_ENABLED=true` 或 `NQ_LIVE_ENABLED=true` 出现在 CI / test → fail closed。

---

## 4. No-outbound 约束规划

后续 5B-ENV-C 实施目标（本轮只规划）：

- CI 和 test 默认禁止外联（`NQ_NO_OUTBOUND=true` 作为默认安全态）。
- 真实交易所 host / endpoint 不应在测试中被访问；denylist 已覆盖 OKX / Binance / Bybit / Bitget / Gate / Coinbase / Kraken / Crypto / Hyperliquid（含 testnet / ws / stream 子域）。
- `NoRealExchangeCredentialPermissionProbePort` 继续作为默认；permission probe 默认返回 `SKIPPED` / `REAL_EXCHANGE_PROBE_DISABLED`，不创建 HTTP client、不下单 / 撤单 / 转账 / 提现。
- 不得真实访问 OKX / Binance；不得借 public market API（exchangeInfo / instruments / ticker）偷偷外联。
- 未来若某测试确需网络，必须**单独标记并默认 skip**，不得进入普通 CI baseline；任何放开必须单独 proposal + review。
- 现状衔接：当前 no-outbound 在 **CI env-name 校验 + test-scope `NoOutboundExchangeGuardTest`** 层面强制；5B-ENV-C 目标是把"运行态 context smoke 下的 no-outbound 断言"补齐为可在 5B-SMOKE 复用的前置能力，但本轮不实现。

---

## 5. Secret / log / artifact redaction 规划

延续 Batch 4B / 4C 已冻结基线，5B-ENV-D 目标是对齐而非弱化：

- CI 日志不得打印 env；Maven / npm / pytest 输出不得包含 secret。
- 后端日志不得输出 credential payload；API response 不得返回 credential material；audit metadata 不得写入 secret。
- artifact upload 前必须过滤敏感文件（沿用 4C-B pre-upload redaction gate：仅扫 CI 生成目录、拒绝 binary、命中 fail closed、只报 rule + file 不报值）。
- secret scan 失败必须阻塞 merge（secret-scan job 已 fail closed）。
- false positive 白名单必须最小化并记录原因（现有 allowlist：4 个 Binance fake-key/protocol 文件 + 1 个冻结 GateC 诊断记录 + 3 个 placeholder 值标记）。
- 新增控制变量（§2.1）均为非敏感布尔/枚举，可安全出现在日志，但其**取值仍需经 secret-scan 不命中**校验。

---

## 6. Fail-closed 规则（汇总）

| 触发条件 | 期望行为 |
| --- | --- |
| `LIVE=true` 且 `NO_OUTBOUND=true` | 启动 fail closed，拒绝运行 |
| `NQ_LIVE_ENABLED=true` 出现在 CI / test | fail closed |
| `NQ_REAL_EXCHANGE_ENABLED=true` 出现在 CI / test | fail closed |
| CI 检测到 real credential material | fail closed（no-outbound-guard env-name 校验 + secret-scan） |
| permission probe 被要求真实探活 | 默认 port 返回 `SKIPPED` / `REAL_EXCHANGE_PROBE_DISABLED` |
| denylist 缺失任一必需 host | no-outbound-guard fail（已实现） |
| artifact 含 binary 或命中 credential pattern | pre-upload gate fail closed（已实现） |
| secret-scan 命中非 allowlisted | 阻塞 merge（已实现） |

原则：任何环境/凭证/外联的不确定状态一律按"拒绝运行"处理，绝不 skip-as-pass、绝不把失败洗成通过。

---

## 7. Batch 5B-SMOKE 前置条件

Batch 5B-SMOKE **只有在 5B-ENV 完成（5B-ENV-A..D 落地并 freeze）后**才能启动。

5B-SMOKE 允许范围（不接真实交易所）：

- backend context smoke（基于 disposable DB，不接 real provider）。
- frontend build / smoke。
- mock / paper path。
- no-real-exchange path。
- fail-closed env validation。
- basic API health（如 actuator health probe，不返回 credential）。
- no secret / no outbound assertion。

5B-SMOKE 禁止：

- LIVE；real provider；real exchange adapter；AI runtime；DH runtime；real permission probe；下单 / 撤单 / 转账 / 提现。

启动门槛（全部满足才解除 BLOCKED）：

1. §2 env 分层 + §3 profile/provider 隔离已文档冻结（5B-ENV-A）。
2. fail-closed env 校验已实现并有测试（5B-ENV-B）。
3. no-outbound env assertion 能力就绪（5B-ENV-C）。
4. secret/log/artifact guard 对齐确认（5B-ENV-D）。
5. 5B-SMOKE readiness review 通过（5B-ENV-E）。

---

## 8. 后续 implementation 批次拆分

```text
5B-ENV-A：环境变量与 profile 边界文档冻结
5B-ENV-B：CI env guard / fail-closed implementation
5B-ENV-C：no-outbound env assertion
5B-ENV-D：secret/log/artifact guard 对齐
5B-ENV-E：5B-SMOKE readiness review
```

- 每个子批次独立 plan → implement → first green → freeze，串行推进，前一批未 freeze 不得开下一批。
- 不得把 5B-ENV 和 5B-SMOKE 混成一个实现任务。
- 不得把任一子批次写成 implemented，除非对应 GitHub Actions 证据 + freeze review 存在。
- 批次调整说明：保留任务建议的 A..E 五段拆分；唯一明确约束是 **5B-ENV-A 必须最先**（边界事实源是后续校验/断言的依据），**5B-ENV-E 必须最后**（readiness review 收口）。中段 B/C/D 顺序可按实现耦合微调，但任一调整必须在对应子批次 plan 中写明原因，且不得跨越 LIVE / AI / DH / real provider 边界。

---

## 9. 禁止事项

- 禁止修改 `.github/workflows/**`（本轮）。
- 禁止修改 Java / TypeScript / Python 代码。
- 禁止新增 API；禁止新增 migration；禁止修改历史 migration。
- 禁止修改 backend 生产逻辑 / frontend 页面 / research 逻辑 / scripts / deploy。
- 禁止读取 / 打印 / 复制 / 输出真实 API key、secret、token、private key、助记词、凭证文件。
- 禁止扫描 `.env` / `secrets` / `credentials` / `logs` / `dump` / `backup` / `target` / `node_modules` / `dist` / `build` / `.git`。
- 禁止调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken / Crypto / Hyperliquid；禁止真实 HTTP 探活；禁止下单 / 撤单 / 转账 / 提现。
- 禁止开启 LIVE；禁止接 AI；禁止接 DH runtime；禁止实现 RealClient；禁止实现真实 provider。
- 禁止把 Batch 5B-ENV 写成 implemented；禁止启动 Batch 5B-SMOKE。

---

## 10. 风险清单（P0/P1/P2/P3）

| 级别 | 风险 | 影响 | 缓解 |
| --- | --- | --- | --- |
| P1 | 无统一 `ci`/`paper` profile，安全默认分散在多个 `application*.yml` | 未来 env 漂移、CI 误用 profile | 5B-ENV-A 收口为单一事实源；5B-ENV-B fail-closed 校验 |
| P1 | 无运行态 env 冲突 fail-closed（`LIVE=true`+`NO_OUTBOUND=true` 等）| 误配置可能进入不一致状态 | 5B-ENV-B 实现启动期 fail closed + 测试 |
| P2 | `application.yml` / `.env.example` 含真实交易所 base-url / real WS 默认值 | 误读为"已接 real provider" | 5B-ENV-A 明确 ci/test 不加载 real base-url；denylist 已覆盖 host |
| P2 | no-outbound 当前仅 test-scope + env-name 校验，非网络层 egress block | 理论上未覆盖的代码路径可外联 | 5B-ENV-C 补 context smoke no-outbound 断言（5B-SMOKE 复用）|
| P2 | 占位标记不统一（`REPLACE_WITH_LOCAL` vs 任务要求的 `DO_NOT_COMMIT_REAL_VALUE`/`PLACEHOLDER_ONLY`）| allowlist 维护复杂 | 5B-ENV-A 二选一并记录；同步 secret-scan allowlist |
| P3 | `README.md`/`ROADMAP.md`（Batch 5A FROZEN）与 `NQ_CI_BASELINE_PLAN.md`（5A IMPLEMENTED/READY）措辞漂移 | 状态口径不一致 | 登记于本文 §1.9；以任务事实 5A=FROZEN/ACCEPTED 为准；baseline plan 5A 措辞收口留待单独 doc 轮 |
| P3 | 控制变量多为新增，落地需改 yml/代码，超出本轮 | 规划与实现间存在时间窗 | 严格串行批次，前批 freeze 才开后批 |

P0：本轮无。

---

## 11. 验收标准

本轮（plan-only）验收：

- 新增 `docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md`，包含 §1~§12 全部要求章节。
- 同步更新 `NQ_CI_BASELINE_PLAN.md` / `README.md` / `ROADMAP.md` / `TESTING.md` / `WORKLOG.md`，仅登记 plan 状态，未写成 implemented。
- `git diff -- .github/workflows` 为空；`backend` / `frontend` / `research` / `scripts` / `deploy` diff 为空；migration diff 为空。
- 仅 `docs/current` 相关文档变更。
- `git diff --check` 无空白错误。

后续子批次验收（未来）：每批以对应 GitHub Actions first green + freeze review 为准，证据不足不得标 FROZEN。

---

## 12. 回滚边界

- 本轮为纯文档新增 / 追加。回滚方式：`git revert` 或 `git checkout -- docs/current/<file>` 还原本轮文档变更即可，无运行态副作用。
- 不涉及 workflow / 代码 / migration / 依赖，回滚不影响 CI 行为、不影响 backend / frontend / research 构建与测试。
- 回滚后 CI 状态回到：Batch 5A = FROZEN / ACCEPTED；5B-ENV = NOT STARTED；5B-SMOKE = BLOCKED BY 5B-ENV（与回滚前一致，因为本轮未改变实现态）。

---

## 边界声明

```text
Batch 5B-ENV = PLAN ONLY / READY FOR REVIEW
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
No workflow changed
No code changed
No migration changed
No real credential read
No outbound call
No LIVE
No AI
No DH runtime
No RealClient
No real provider
NQ GateK CI/security boundary = planning continues
```
