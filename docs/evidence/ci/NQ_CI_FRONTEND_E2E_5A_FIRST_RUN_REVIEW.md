# NQ CI Batch 5A No-Backend E2E First-Run Review

任务：`NQ-CI-BATCH-5A-FIRST-RUN-REVIEW`

日期：2026-06-18

状态：**PASS / READY FOR FREEZE REVIEW**

> 本轮只读审查由 Batch 5A 实施提交触发的首次 GitHub Actions immutable run，确认 `frontend-no-backend-e2e` job 真实执行、仅跑 allowlist 四个 spec、未扩大范围、未启动禁止组件、无 artifact 上传、成功完成。不修改 workflow / 前端 / Playwright spec / 后端 / 依赖 / 测试 / migration。

---

## 1. 结论

**NQ-CI-BATCH-5A-FIRST-RUN-REVIEW：PASS / READY FOR FREEZE REVIEW**

- **Batch 5A = FIRST RUN PASSED / READY FOR FREEZE REVIEW**。
- **Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **NQ GateK CI mainline = IN PROGRESS**。

5A 绿跑不代表 authenticated / backend-required E2E 已覆盖；5B-ENV 仍为 P1 prerequisite。

---

## 2. Run 与提交一致性（immutable 证据）

| 项 | 值 |
| --- | --- |
| Workflow | `NQ CI Baseline` |
| Immutable run ID | `27750279096` |
| Run URL | https://github.com/ling5477/nexus-quant/actions/runs/27750279096 |
| Commit SHA | `861c3e78ddd1733292c5376a1f059532fd6dc846` |
| Commit subject | `ci(frontend): add Batch 5A no-backend E2E gate` |
| 触发分支 | `dev` |
| 触发事件 | `push` |
| 创建时间 | 2026-06-18T09:32:27Z |
| Run 结论 | **completed / success** |

一致性核验：`origin/dev` HEAD = `861c3e78…`，本地 `git rev-list --left-right --count origin/dev...HEAD` = `0 0`（同步）。该 run 的 `headSha` 与 Batch 5A implementation commit 完全一致；不是旧 run，也不是其他 commit 的 run。

### 5A job 结论

| 项 | 值 |
| --- | --- |
| Job 名称 | `Frontend no-backend E2E (Batch 5A)` |
| Job ID | `82098741200` |
| Job URL | https://github.com/ling5477/nexus-quant/actions/runs/27750279096/job/82098741200 |
| 结论 | **completed / success** |
| started → completed | 2026-06-18T09:32:30Z → 09:33:26Z（约 56s，远低于 15min timeout） |

同一 run 内其余 job（Diff check / No-outbound guard / Backend Maven test / PostgreSQL·Flyway smoke / Frontend build / Research / Secret scan）均 completed / success；与 5A job 相互独立。

---

## 3. Job 结构核验

实际执行步骤（来自 immutable job 日志）：

1. `Set up job` —— `GITHUB_TOKEN Permissions: Contents: read / Metadata: read`（`permissions: contents: read` 生效）；无 service 容器初始化（无 `Initialize containers`）。
2. `Checkout`（actions/checkout@v4）。
3. `Set up Node` —— Node `22.22.3`（`node-version: 22`，保持前端既有口径，未升 major）。
4. `Install frontend dependencies` —— `npm ci`（`added 183 packages`）。
5. `Install Playwright Chromium` —— `npx playwright install --with-deps chromium`，仅下载 `Chrome for Testing 145.0.7632.6 (playwright chromium v1208)`；日志中 Firefox / Webkit 下载提及数 = 0（Chromium only）。
6. `Build frontend (production)` —— `tsc -b && vite build`，`✓ built in 1.53s`，产出 `dist/index.html`。
7. `Run Batch 5A no-backend E2E (explicit 4-spec allowlist)` —— 见 §4。
8. `Cleanup Playwright temp output (no upload)` —— `rm -rf test-results-ci playwright-report test-results`（运行成功）。

committed workflow 校验（`git show 861c3e78:.github/workflows/ci.yml`）：`frontend-no-backend-e2e` job `timeout-minutes: 15`、`contents: read`、`node-version: "22"`，job 内无 `services:`、无 `upload-artifact`。

---

## 4. Allowlist 证明（仅四个 spec，未扩大）

执行命令（日志逐行回显，显式列出四个 spec，无宽泛 glob / 无无参数 `playwright test`）：

```bash
npx playwright test \
  --config=playwright.ci.config.ts \
  tests/e2e/login-page-smoke.spec.ts \
  tests/e2e/design-system-table-smoke.spec.ts \
  tests/e2e/design-system-live-query-smoke.spec.ts \
  tests/e2e/design-system-backtest-chart-smoke.spec.ts
```

Playwright summary（日志）：

```text
Running 4 tests using 1 worker
[1/4] … design-system-backtest-chart-smoke.spec.ts
[2/4] … design-system-live-query-smoke.spec.ts
[3/4] … design-system-table-smoke.spec.ts
[4/4] … login-page-smoke.spec.ts
4 passed (7.3s)
```

- 日志中仅出现四个 allowlist spec 文件名，未出现其余 23 个 spec（marketdata / paper-trading / account- / strategy / research / trading-workbench / publish / evaluation / backtest-dataset / backtest-detail / smoke 等均 0 次）。
- 无任何 spec 被 skip-as-pass；总计 **4 tests / 4 files / 4 passed / 0 failed / 0 skipped**。
- config `testMatch` 二次限定与命令显式列举构成双重 fail-closed（本地 `--list` 亦为 Total: 4 tests in 4 files）。

---

## 5. 无 backend / 无认证 / 无外部业务调用边界

immutable 日志关键词扫描（5A job 全量日志）：

| 指标 | 命中 | 判定 |
| --- | --- | --- |
| `/api/` | 0 | 无业务 API 请求 |
| `postgres` / `jdbc` / `flyway` | 0 / 0 / 0 | 无 DB / 无 Flyway |
| `docker` / service 容器初始化 | 0 / 无 | 无 Docker / 无 service |
| `loginToConsole` / `seed` / `storageState` | 0 / 0 / 0 | 无认证 fixture / 无 seed / 无 storageState |
| `okx` / `binance` | 0 / 0 | 无交易所 / 无真实 provider |
| `Authorization` | 1 | 仅 `actions/checkout` 的 git `http.extraheader AUTHORIZATION`，值由 GitHub 自动 mask（CI bootstrap，非业务） |
| `token` | 3 | `GITHUB_TOKEN Permissions` 组头 + checkout/setup-node 的 `token: ***`（已 mask，非业务、非泄露） |
| `api`（非 `/api/`） | 1 | Node `DEP0169` 警告中的 “WHATWG URL API” 文案，非网络调用 |

- preview 绑定：`playwright.ci.config.ts`（committed）webServer 命令为 `node ./node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5179`，`baseURL=http://127.0.0.1:5179`。日志未回显 `127.0.0.1` 字面串（webServer 命令不在 line reporter 输出中），但四个 spec 全部基于 `baseURL` loopback 导航成功，证明 preview 在 loopback 提供服务；preview 无 `/api` proxy（`vite.config.ts` 仅 `server` 段代理），与 0 次 `/api` 命中一致。
- 测试运行期间无 token / cookie / Authorization business header / exchange credential / DB 连接串被注入或打印。

---

## 6. bootstrap 下载 vs 业务运行时出站

明确区分（与 5A 边界一致）：

- **CI bootstrap / toolchain 网络访问（允许）**：`actions/checkout`（GitHub 克隆）、`setup-node` 下载 Node 22.22.3、`npm ci` 拉取 npm registry（183 packages）、`npx playwright install` 从 CDN 下载 Chromium 1208。这些发生在 build/install 阶段，属 CI 引导，不是应用层/业务层出站。
- **应用 / 业务运行时出站（必须为 0，实测为 0）**：`/api`、交易所（okx/binance）、真实 provider、外部业务 API。测试期间仅访问 loopback preview，未发生任何业务出站调用。

因此 bootstrap 下载不得被判定为业务外部调用；本 run 的业务层出站为 0。

---

## 7. 输出与 artifact 卫生

- `trace=off` / `screenshot=off` / `video=off`（committed config），line reporter only。
- 无 `actions/upload-artifact` 步骤；日志中 “upload” 字样仅出现在步骤名 `Cleanup Playwright temp output (no upload)`（×6 均为该步骤标题），无任何 artifact 上传。
- 未生成或上传 HTML report / HAR / storageState / test-results / browser profile / backend raw logs；`test-results` 字样仅出现在 cleanup 的 `rm -rf` 命令中。
- cleanup 步骤运行成功；`Complete job` 阶段 `Cleaning up orphan processes`（preview 进程被清理）。
- 日志无环境变量 dump、无 `set -x`、无 token/cookie/请求响应 body、无 credential-like material 扩散；CI bootstrap 的 token 均为 `***`（GitHub mask）。

---

## 8. 失败分类

本 run 5A job = success，无失败。逐类确认均未触发：

- browser/bootstrap toolchain failure：无（Chromium 1208 正常下载）。
- frontend build failure：无（vite build 成功）。
- loopback preview failure：无（四 spec loopback 导航成功）。
- test assertion failure：无（4 passed）。
- allowlist/config violation：无（仅四 spec，testMatch + 显式命令双控）。
- output hygiene violation：无（无 artifact、无 dump、无泄露）。

无因安装/环境问题被标 success/skip-as-pass 的情况。

---

## 9. Findings

### P0

- 无。无 backend / DB / Flyway / Docker / 认证 / seed / 业务出站 / artifact 上传 / 凭证泄露。

### P1

- （继承自 plan，未消除，正确保留，仅阻断 5B）no-outbound runtime enforcement 仍是 5B backend-required E2E 的前置；本 5A run 不启动 backend，故不受影响。5A 绿跑不得据此宣称 authenticated E2E 已覆盖。

### P2

- 无影响本轮结论的 P2。

### P3

- GitHub runner 级 deprecation 警告：`actions/checkout@v4` / `actions/setup-node@v4` 的 JS action wrapper 被强制在 Node.js 24 上运行（Node 20 在 runner 弃用）。这是 action 包装运行时层，**不影响**应用构建所用的 Node 22.22.3，也不影响 run 结论；如需消除可在单独批次升级 action major，不在 5A first-run-review 范围。
- preview 的 `127.0.0.1` 绑定在 line reporter 日志中无字面回显，证据来自 committed config + loopback 导航成功；后续如需更强可观测性可单独评审（不改本轮配置）。

---

## 10. 状态结论

- **Batch 5A = FIRST RUN PASSED / READY FOR FREEZE REVIEW**（immutable run `27750279096` / commit `861c3e78` / job `82098741200` success）。
- **Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4C = FROZEN / ACCEPTED**（redaction 规则未弱化；本轮未新增 upload 路径）。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**。
- **NQ GateK CI mainline = IN PROGRESS**。

Next：可进入 `NQ-CI-BATCH-5A-FREEZE-REVIEW`（需第二次 immutable green run 作为冻结证据）；不得进入 5B-ENV / 5B-SMOKE / 5C / 5D / 5E，不得启动 Batch 4F-B 至 4F-F，不得开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。

---

## 11. 审查方法与可信度

- 工具：`gh`（run/job 元数据与 immutable 日志只读）、`git`（commit / origin 一致性）。
- 证据来源：GitHub Actions immutable run `27750279096` 的 job `82098741200` 日志（已脱敏摘录，未粘贴 mask 后的 token 真值）。
- 未使用本地 `4 passed` 替代 GitHub Actions 首跑证据；本文所有结论基于该 immutable run。
- 可信度：高（run/commit/job 三者一致，日志直接佐证步骤、计数与边界）。
