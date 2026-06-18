# NQ CI Batch 5A No-Backend E2E Implementation

任务：`NQ-CI-BATCH-5A-NO-BACKEND-E2E-IMPL`

日期：2026-06-18

状态：**Batch 5A = IMPLEMENTED / READY FOR FIRST-RUN**（尚未经过 GitHub Actions first-run review）

> 本轮只把四个已批准的纯 loopback / no-backend Playwright spec 接入 GitHub Actions CI。不启动 backend / PostgreSQL / Flyway / 认证 / seed / 账户写入 / 外网业务调用 / 任何真实 provider，不上传任何 artifact。

---

## 1. 结论

**NQ-CI-BATCH-5A-NO-BACKEND-E2E-IMPL：PASS / READY FOR FIRST-RUN**

- **Batch 5A = IMPLEMENTED / READY FOR FIRST-RUN**。
- **Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4C = FROZEN / ACCEPTED**（redaction 规则未被弱化）。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**。
- **NQ GateK CI mainline = IN PROGRESS**。

所有 authenticated / backend-required spec 仍**不在** CI required gate。

---

## 2. 唯一 allowlist 与路径口径

唯一允许执行的四个 spec（仓库真实路径，以 `frontend/playwright.config.ts` 的 `testDir=./tests/e2e` 为准）：

```text
frontend/tests/e2e/login-page-smoke.spec.ts
frontend/tests/e2e/design-system-table-smoke.spec.ts
frontend/tests/e2e/design-system-live-query-smoke.spec.ts
frontend/tests/e2e/design-system-backtest-chart-smoke.spec.ts
```

> **路径口径说明**：任务书将 allowlist 写为 `frontend/e2e/...`，但仓库实际 E2E 目录是 `frontend/tests/e2e/`（Playwright `testDir=./tests/e2e`，且 `frontend/e2e/` 不存在）。本实现以仓库真实路径为准，未移动或重命名任何 spec（禁止修改 `frontend/tests/e2e/**`）。spec 集合与计划一致，无增减。

### 命令未扩大到其他 spec 的证明

- CI 运行命令**显式列出**上述四个 spec 文件，未使用 `e2e/**/*.spec.ts`、无参数 `playwright test` 或任何会自动发现全部 27 个 spec 的写法。
- `frontend/playwright.ci.config.ts` 通过 `testMatch` 把发现集合限定为同四个文件（设置 `testMatch` 会覆盖 Playwright 默认 `**/*.spec.ts`），构成第二道 fail-closed 过滤。
- 本地 `playwright test --config=playwright.ci.config.ts --list`（不带 CLI 参数）输出 **Total: 4 tests in 4 files**，证明即使省略 CLI 参数也不会扩大到其余 23 个 spec。

---

## 3. CI job 设计

新增 job：`frontend-no-backend-e2e`（`.github/workflows/ci.yml`，置于 `frontend` build job 之后）。

| 项 | 取值 |
| --- | --- |
| `runs-on` | `ubuntu-latest` |
| `permissions` | `contents: read`（job 级） |
| `timeout-minutes` | `15` |
| `env` | `CI: "true"`（不注入任何 API base / token / cookie / Authorization / exchange credential / DB 连接） |
| Node setup | 复用现有前端口径：`actions/setup-node@v4`，`node-version: "22"`，`cache: npm`，`cache-dependency-path: frontend/package-lock.json`（不升级 Node major） |
| working-directory | `frontend` |

steps：

1. `actions/checkout@v4`。
2. `actions/setup-node@v4`（Node 22 + npm cache）。
3. `npm ci`。
4. `npx playwright install --with-deps chromium`（只装 Chromium + 系统依赖；安装失败 = toolchain/环境失败，直接 fail job，绝不 skip-as-pass）。
5. `npm run build`（前端 production build，产出 `dist/`）。
6. 运行四个 allowlist spec（见下，显式列出，`--config=playwright.ci.config.ts`）。
7. `if: always()` cleanup：`rm -rf test-results-ci playwright-report test-results`（只清理临时 output，不上传，不会把测试失败洗成通过）。

运行命令（fail-closed，显式四 spec）：

```bash
npx playwright test \
  --config=playwright.ci.config.ts \
  tests/e2e/login-page-smoke.spec.ts \
  tests/e2e/design-system-table-smoke.spec.ts \
  tests/e2e/design-system-live-query-smoke.spec.ts \
  tests/e2e/design-system-backtest-chart-smoke.spec.ts
```

### 明确禁止（job 内不出现）

PostgreSQL service、backend 启动、Flyway、Docker、login seed、CI-only seed watcher、authenticated fixture、exchange account 创建/启用、真实交易所/真实 provider/外部业务 API、`upload-artifact`、测试报告下载或上传。job 内**无** `set -x` / `printenv` / 环境变量 dump；不打印 HTTP payload / request-response body / token / cookie。

---

## 4. Playwright CI config

新增 `frontend/playwright.ci.config.ts`（与本地全量回归用的 `playwright.config.ts` 分离，互不影响）。显式覆盖：

| 项 | 取值 |
| --- | --- |
| browser | Chromium only（`projects: [{name:'chromium', use: Desktop Chrome}]`） |
| `workers` | `1` |
| `retries` | `0` |
| `trace` | `'off'` |
| `screenshot` | `'off'` |
| `video` | `'off'` |
| reporter | `[['line']]`（仅 console，不生成 HTML report） |
| `baseURL` | `http://127.0.0.1:5179` |
| preview command | `node ./node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5179`（只绑定 `127.0.0.1`，不绑定 `0.0.0.0`） |
| `reuseExistingServer` | `false` |
| `webServer.timeout` | `120_000` ms |
| `storageState` | 不使用 |
| `forbidOnly` | `true`（CI 禁止 `test.only` 误入基线） |
| `testDir` / `testMatch` | `./tests/e2e` + 仅匹配四个 allowlist spec |
| `outputDir` | `./test-results-ci`（临时；job 结束前删除，绝不上传） |

不生成、上传或引用 HTML report、HAR、trace、video、screenshot、browser profile、storageState、test-results artifact。preview 无 `/api` proxy（`vite.config.ts` 仅在 `server` 段配置代理），正符合 5A 不接 backend 的边界。

---

## 5. 本地验证（真实执行结果）

执行环境：本机已安装 Playwright Chromium（`chromium-1208`）。

| 步骤 | 命令 | 结果 |
| --- | --- | --- |
| 干跑列举 | `playwright test --config=playwright.ci.config.ts --list` | **Total: 4 tests in 4 files**（仅四个 allowlist spec） |
| 依赖安装 | `npm ci` | 成功（本机原 node_modules 缺 `echarts`，clean install 后补齐；未改 package.json/lockfile） |
| 前端构建 | `npm run build` | 成功（`tsc -b && vite build`，built in ~1.6s） |
| E2E 实跑 | 显式四 spec + CI config（已 build，preview loopback，无 backend） | **4 passed (10.2s)** |
| artifact 检查 | 运行后检查 + 清理 | `test-results` / `test-results-ci` 为空临时目录，已删除；未生成 HTML report / trace / video / screenshot；本轮未上传任何 artifact |

说明：

- 本地 E2E 未启动 backend / PostgreSQL / Flyway / 认证 / seed；未调用 `loginToConsole()`；未运行其余 23 个 spec。
- 未为本地通过而修改任何业务页面、组件、路由、spec 断言或 helper。
- GitHub Actions first-run（含 `npx playwright install --with-deps chromium` 的真实安装、ubuntu runner 构建与执行）仍待 CI 首跑确认。

---

## 6. Findings

### P0

- 无。本轮无 backend/DB/Flyway 启动，无认证/seed/账户写入，无外网业务调用，无 artifact 上传，无真实 provider。

### P1

- （继承自 plan，未消除，正确保留，仅阻断 5B）no-outbound guard 为 JUnit/test-scope，不自动覆盖单独启动的 E2E backend；backend-required E2E（5B）变 required 前必须证明 runtime enforcement。该 P1 与纯 no-backend 的 5A 解耦，不阻断 5A。

### P2

- GitHub Actions 上 `npx playwright install --with-deps chromium` 的真实安装/缓存行为尚未首跑验证；浏览器安装失败按 toolchain/环境失败 fail job（已在 step 注释固定语义），但首跑前不能宣称 CI 已通过。
- `dist` 体积告警（单 chunk > 500 kB）属既有前端打包现状，不在本轮范围（禁止改 `frontend/src/**`、`frontend/package.json`），仅记录。

### P3

- 当前 5A 未做 Playwright 浏览器版本/缓存的 first-class pin（依赖 `npx playwright install` 取与本地 `playwright` 包匹配的 Chromium）；后续如需更强可复现性，可在单独批次评审 browser 缓存键，不在 5A 改 package.json。
- CI config 的 `outputDir` 仅作临时占位（capture 全关时通常为空）；cleanup 已覆盖 `test-results-ci` / `playwright-report` / `test-results`。

---

## 7. 变更文件与禁止范围

允许修改范围内的实际改动：

- 修改：`.github/workflows/ci.yml`（新增 `frontend-no-backend-e2e` job，+56 行）。
- 新增：`frontend/playwright.ci.config.ts`。
- 新增/更新：`docs/current/**`（本文件 + plan/baseline/README/STATUS/TESTING/WORKLOG）。

禁止范围 diff 校验结果（全部为空，未触碰）：

```text
git diff -- backend frontend/src frontend/tests frontend/package.json frontend/package-lock.json research scripts deploy pom.xml pyproject.toml
→ (empty)
git diff --check → (no whitespace errors)
```

未使用独立 CI config 之外的兜底：**未**修改 `frontend/playwright.config.ts`（独立 CI config 已满足全部要求，无需改动本地全量回归配置）。

---

## 8. 边界与禁止事项确认

- 未进入 5B-ENV / 5B-SMOKE / 5C / 5D / 5E。
- 未修改或恢复 seed watcher。
- 未启动 PostgreSQL / backend / Flyway。
- 未修改 Playwright spec、helper、页面组件、路由或认证逻辑。
- 未上传任何 Playwright 或 backend artifact。
- 未修改 Batch 4F-B 至 4F-F。
- 未开启 LIVE / AI / DH runtime。
- 未实现 RealClient / real provider / real exchange adapter。
- Batch 4C redaction 规则未被弱化（本轮不新增任何 upload 路径；`postgres-flyway` 既有 pre-upload gate 不变）。

---

## 9. 后续

- Next：等待 GitHub Actions `frontend-no-backend-e2e` first-run，按 first-run review 确认绿；连续两次 immutable green 后方可考虑纳入 required gate。
- 5B-ENV 仍为 P1 prerequisite（isolated PostgreSQL/Flyway/同步 fixture/backend readiness/runtime no-outbound enforcement），未启动；5B-SMOKE 被 5B-ENV 阻断。
- 文档治理：first-run 真实证据出现后再更新 run id / 状态；不在本轮把 5A 写成 FROZEN / ACCEPTED。
