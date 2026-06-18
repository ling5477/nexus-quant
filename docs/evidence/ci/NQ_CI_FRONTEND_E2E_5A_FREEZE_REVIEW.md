# NQ CI Batch 5A No-Backend E2E Freeze Review

任务：`NQ-CI-BATCH-5A-FREEZE-REVIEW`

日期：2026-06-18

状态：**PASS / ACCEPTED / FROZEN**

> 本轮只读审查 Batch 5A 的第二个 immutable GitHub Actions green run，在两次一致绿跑 + 工作流/配置零 drift 证据下冻结 no-backend frontend E2E gate。不修改 workflow / `frontend/playwright.ci.config.ts` / 前端 / spec / helper / 后端 / 依赖 / 测试 / migration；不上传 artifact。

---

## 1. 结论

**NQ-CI-BATCH-5A-FREEZE-REVIEW：PASS / ACCEPTED / FROZEN**

- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4C = FROZEN / ACCEPTED**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**。
- **NQ GateK CI mainline = IN PROGRESS**。

无 workflow / config drift；无 backend / DB / Flyway / 认证 / `/api` / artifact / 业务外部调用。5A 冻结不代表 authenticated E2E、backend E2E、交易链路或真实 provider 已覆盖。

---

## 2. 两次 immutable green run（冻结证据）

| 项 | Run 1（首跑 / implementation） | Run 2（freeze） |
| --- | --- | --- |
| Workflow | `NQ CI Baseline` | `NQ CI Baseline` |
| Immutable run ID | `27750279096` | `27750976632` |
| Commit SHA | `861c3e78ddd1733292c5376a1f059532fd6dc846` | `3d26c84d`（`docs(ci): review Batch 5A frontend E2E first run`） |
| Commit 性质 | Batch 5A implementation | first-run-review **docs-only**（impl commit 的直接后继） |
| 触发分支 / 事件 | dev / push | dev / push |
| Run 结论 | completed / success | completed / success |
| 5A Job 名 | `Frontend no-backend E2E (Batch 5A)` | `Frontend no-backend E2E (Batch 5A)` |
| 5A Job ID | `82098741200` | `82101090359` |
| 5A Job 结论 | success（约 56s） | success（09:45:18→09:46:16，约 58s） |
| Playwright summary | `Running 4 tests using 1 worker` / `4 passed (7.3s)` | `Running 4 tests using 1 worker` / `4 passed (6.8s)` |
| 4 tests / 4 files | 是 | 是 |

Run 2 URL：https://github.com/ling5477/nexus-quant/actions/runs/27750976632
5A job 2 URL：https://github.com/ling5477/nexus-quant/actions/runs/27750976632/job/82101090359

### 身份一致性

- Run 2 `headSha = 3d26c84d`，等于 `origin/dev` HEAD（`git rev-list --left-right --count origin/dev...HEAD` = `0 0`）。
- Run 2 为 `push` 到 `dev` 触发，completed / success，是 implementation 首跑之后、freeze review 提交之前的第二个完整 immutable green run；非旧 run、非他分支 run、非本地运行、非未完成 run。

---

## 3. Workflow / config 一致性（零 drift）

对比 implementation commit `861c3e78` 与 Run 2 commit `3d26c84d` 的 git blob：

| 文件 | blob（两 commit 相同） | 判定 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | `6941d60ade2bfce456e203f708b633e595285178` | **IDENTICAL ✓** |
| `frontend/playwright.ci.config.ts` | `d039fe82fbf7db6f55c3e6fc089bac59a2fe9014` | **IDENTICAL ✓** |

两 commit 间改动文件（`git diff --name-only 861c3e78 3d26c84d`）= 仅 5 个 `docs/current` 文件（`NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md` / `README.md` / `STATUS.md` / `TESTING.md` / `WORKLOG.md`），**docs-only**；无 workflow / frontend / backend / 依赖 / 测试 / migration 改动。

结论：**NO WORKFLOW OR TEST CONFIG DRIFT**。两次绿跑使用完全相同的 5A workflow 与 Playwright CI 配置。

---

## 4. Job / allowlist 一致性（Run 2）

- `frontend-no-backend-e2e` job 存在、success。
- committed workflow（blob `6941d60a…`）：`timeout-minutes: 15`、`permissions: contents: read`、`node-version: "22"`、无 `services:`、无 `upload-artifact`。
- Run 2 日志：`GITHUB_TOKEN Permissions: Contents: read / Metadata: read`（最小化）；Node `22.22.3`；`npm ci` `added 183 packages`；`npx playwright install --with-deps chromium` 下载 `Chrome for Testing 145.0.7632.6 (playwright chromium v1208)`，Firefox / Webkit 提及 = 0（Chromium only）；`tsc -b && vite build` `✓ built in 1.52s`。
- E2E 命令显式列出四个 allowlist spec（与首跑逐字一致）：
  ```bash
  npx playwright test \
    --config=playwright.ci.config.ts \
    tests/e2e/login-page-smoke.spec.ts \
    tests/e2e/design-system-table-smoke.spec.ts \
    tests/e2e/design-system-live-query-smoke.spec.ts \
    tests/e2e/design-system-backtest-chart-smoke.spec.ts
  ```
- Playwright summary：`Running 4 tests using 1 worker` → `4 passed (6.8s)`。
- 日志仅出现四个 allowlist spec 文件名；其余 23 个 spec（marketdata / paper-trading / account- / strategies / research / trading-workbench / publish / evaluation / backtest-dataset / backtest-detail / smoke 等）= 0 次出现，无 skip-as-pass，无宽泛 glob 副作用。

---

## 5. 输出 / artifact 边界（Run 2）

- `trace=off` / `screenshot=off` / `video=off`（committed config），line reporter only。
- 无 `upload-artifact` 执行（关键词命中 0）；日志 “upload” 仅出现在步骤名 `Cleanup Playwright temp output (no upload)`。
- 未上传 HTML report / HAR / storageState / browser profile / test-results / raw backend logs / 浏览器输出；`test-results` 仅出现在 cleanup 的 `rm -rf test-results-ci playwright-report test-results`。
- cleanup 步骤运行；`Complete job` 阶段 `Cleaning up orphan processes`（preview 进程清理）。
- 无环境变量 dump、无 token/cookie/Authorization 明文、无请求/响应 body、无 DB 连接串、无交易所凭证、无完整本地路径扩散。GitHub 自动 mask 的 `***`（checkout extraheader / `token: ***`）不视为泄露；Node `DEP0169` 文案中的 “URL API” 不视为 `/api` 调用或业务出站。

---

## 6. 无 backend / 无认证边界（Run 2）

Run 2 5A job 全量日志关键词扫描，命中均为 0：

| 指标 | 命中 |
| --- | --- |
| `/api/` | 0 |
| `jdbc` / `postgres` / `flyway` | 0 / 0 / 0 |
| `docker` / service 容器初始化 | 0 / 无 |
| `loginToConsole` / `seed` / `storageState` | 0 / 0 / 0 |
| `okx` / `binance` | 0 / 0 |
| `upload-artifact` | 0 |

- 未启动 PostgreSQL / Docker / backend / Flyway / seed / CI-only seed watcher / 认证 fixture / `loginToConsole()` / storageState。
- 测试运行时仅访问 loopback preview（`baseURL=http://127.0.0.1:5179`，committed config webServer `vite preview --host 127.0.0.1 --port 5179`）；preview 无 `/api` proxy，与 0 次 `/api` 命中一致。

### bootstrap vs 业务出站

- CI bootstrap（允许）：checkout 克隆、setup-node 下载 Node 22.22.3、`npm ci` 拉 npm registry（183 包）、`npx playwright install` 从 `cdn.playwright.dev` 下载 Chromium 1208。
- 业务运行时出站（必须 0，实测 0）：`/api`、交易所（okx/binance）、真实 provider、外部业务 API。
- bootstrap 下载不得判定为测试运行时业务外部调用。

---

## 7. 现有 residual（保留）

- **Batch 5B-ENV P1（保留）**：runtime no-outbound enforcement 仅存在于 JUnit/test scope，不覆盖独立启动的 E2E backend 进程；backend-required E2E 变 required 前必须先证明 runtime enforcement。5A 不启动 backend，故 P1 不阻断 5A 冻结，但仍是 5B 前置。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV（保留）**。
- 不得因 5A 冻结声称 authenticated E2E、backend E2E、交易链路或真实 provider 已覆盖。

---

## 8. Findings

### P0

- 无。两次绿跑均无 backend / DB / Flyway / Docker / 认证 / seed / 业务出站 / artifact 上传 / 凭证泄露。

### P1

- （继承，未消除，正确保留，仅阻断 5B）no-outbound runtime enforcement 仍是 5B backend-required E2E 的前置；与纯 no-backend 的 5A 解耦，不阻断本次冻结。

### P2

- 无影响冻结结论的 P2。

### P3

- GitHub runner 级 deprecation 警告：`actions/checkout@v4` / `actions/setup-node@v4` 的 JS action wrapper 被强制在 Node.js 24 上运行（runner 弃用 Node 20）。属 action 包装运行时层，不影响应用构建所用 Node 22.22.3，也不影响 run 结论；如需消除可在单独批次升级 action major，不在 5A 冻结范围。
- preview `127.0.0.1` 绑定未在 line reporter 日志字面回显，证据为 committed config（blob `d039fe82…`）+ loopback 导航成功。

---

## 9. 冻结基线（immutable baseline）

- Batch 5A frozen workflow blob：`.github/workflows/ci.yml` = `6941d60ade2bfce456e203f708b633e595285178`。
- Batch 5A frozen Playwright CI config blob：`frontend/playwright.ci.config.ts` = `d039fe82fbf7db6f55c3e6fc089bac59a2fe9014`。
- 冻结证据 run：首跑 `27750279096`（commit `861c3e78`，job `82098741200`）+ freeze run `27750976632`（commit `3d26c84d`，job `82101090359`），两次 `4 passed`。
- 允许的 spec（不得增减）：
  ```text
  frontend/tests/e2e/login-page-smoke.spec.ts
  frontend/tests/e2e/design-system-table-smoke.spec.ts
  frontend/tests/e2e/design-system-live-query-smoke.spec.ts
  frontend/tests/e2e/design-system-backtest-chart-smoke.spec.ts
  ```

任何对上述两个 blob 或 allowlist 的后续修改都使本冻结失效，需重新走 first-run + freeze review。

---

## 10. 检查文件 / 修改文件 / validation

- 检查（只读）：GitHub Actions run `27750976632` 与 job `82101090359` 元数据及 immutable 日志；`git rev-parse`/`git diff --name-only` 对 `861c3e78` 与 `3d26c84d` 的 `ci.yml` 与 `playwright.ci.config.ts` blob 比对。
- 修改：新增 `docs/current/NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md`；更新 `docs/current/README.md` / `STATUS.md` / `TESTING.md` / `WORKLOG.md`。
- validation：`gh run view` / `gh api …/jobs`（run/job success）、blob 比对（IDENTICAL）、`git diff --name-only`（docs-only）、日志关键词扫描（4 passed、其余 spec 0、边界 0、无 upload）、`git diff --check`（无空白错误）、禁止范围 `git diff` 为空。

---

## 11. 状态结论

- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4C = FROZEN / ACCEPTED**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**。
- **NQ GateK CI mainline = IN PROGRESS**。

Next：5B-ENV 仍为 P1 prerequisite（isolated PostgreSQL/Flyway/同步 fixture/backend readiness/runtime no-outbound enforcement），未启动；不得进入 5B-ENV / 5B-SMOKE / 5C / 5D / 5E，不得启动 Batch 4F-B 至 4F-F，不得开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。
