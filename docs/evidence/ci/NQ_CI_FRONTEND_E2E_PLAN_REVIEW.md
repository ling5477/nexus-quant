# NQ CI Batch 5 Frontend E2E Plan Review

任务：`NQ-CI-BATCH-5-FRONTEND-E2E-PLAN-REVIEW`

日期：2026-06-18

> 本文是对 `NQ_CI_FRONTEND_E2E_PLAN.md` 的只读评审。本轮不修改 `.github/workflows/ci.yml`，不新增 CI job，不运行 Playwright/backend/PostgreSQL/Flyway/浏览器安装，不修改 spec/helper/前端/后端/seed/migration/测试/POM/package/lockfile/pyproject，不生成或上传任何 artifact。仅新增/更新 `docs/current` 文档。

---

# 审查结论

结论：**通过（PASS / ACCEPTED）**

- **Batch 5 plan = ACCEPTED AS IMPLEMENTATION BASELINE**。
- **Batch 5A = READY FOR IMPLEMENTATION**（4 个 no-backend spec allowlist 经源码核实，纯 loopback / 无后端 / 无 token / 无账户写入 / 无外网）。
- **Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-A = FROZEN / ACCEPTED**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**。
- **Batch 4C = FROZEN / ACCEPTED**。
- **NQ GateK CI mainline = IN PROGRESS**。

runtime no-outbound P1 仅阻断 5B（backend-required），**不阻断纯 no-backend 5A**。

---

## 范围

- 已审查：
  - `frontend/playwright.config.ts`（Chromium / workers=1 / retries=0 / trace=retain-on-failure / webServer 启动 Vite dev server）。
  - 4 个候选 5A spec 源码：`login-page-smoke`、`design-system-table-smoke`、`design-system-live-query-smoke`、`design-system-backtest-chart-smoke`。
  - `/dev/design-system` 路由注册（`frontend/src/router/routes.tsx`）。
  - `DesignSystemDemoPage.tsx`、`useLiveQuery.ts`、`BacktestCurveChart.tsx`、`AppProviders.tsx`、`main.tsx`。
  - `frontend/vite.config.ts`（`/api` proxy 仅在 `server`，`preview` 无 proxy）。
  - `frontend/package.json` scripts（`build` = `tsc -b && vite build`；`preview` = `vite preview`；`test:e2e` = `run-e2e.mjs`）。
  - `marketdata-ingestion-smoke.spec.ts`（外网失败容忍语义）。
  - 计划文档 `NQ_CI_FRONTEND_E2E_PLAN.md` 全文。
- 未审查（本轮明确不执行）：实际 CI 运行、backend 启动、PostgreSQL/Flyway 运行、浏览器安装、Playwright 执行、`run-e2e.mjs` 运行时行为。
- 明确不涉及：workflow 修改、CI job 新增、Batch 5 implementation、Batch 5B-ENV、Batch 5B-SMOKE、Batch 4F-B 至 4F-F、LIVE/AI/DH runtime/RealClient/real provider。

---

## 1. 5A no-backend allowlist 验证（核心）

结论：**4 个候选全部通过验证，可作为最终 5A allowlist**。

### 公共前提（已源码核实）

- `/dev/design-system` 在 `appRouter` 中是**顶层路由**（`routes.tsx`），位于 `RequireAuth` 块之外，不进入 `ConsoleLayout`，**无鉴权守卫**。
- `/login` 同样是顶层公开路由。
- `AppProviders` 在 app 根包裹所有路由，其 `AuthBootstrap` 的 `currentUserQuery` 为 `enabled: Boolean(accessToken)`。Playwright 默认每个 test 用**隔离 browser context**，且本配置**未设置 `storageState`**，新 context 中 `localStorage` 为空 → `accessToken` 不存在 → `getCurrentUser` 查询 disabled → **不触发任何 `/api` 请求**。前提是不调用 `loginToConsole()`、不注入 token。

### 逐 spec 结论

| Spec | 路由 | 数据源 | 网络 | 判定 |
| --- | --- | --- | --- | --- |
| `login-page-smoke.spec.ts` | `/login` | 静态渲染断言 | 不提交表单、不写 token、无 API | **PASS** |
| `design-system-table-smoke.spec.ts` | `/dev/design-system` | 组件内合成样本数据 | 无 API | **PASS** |
| `design-system-live-query-smoke.spec.ts` | `/dev/design-system` | `LiveQueryDemo` 本地 `queryFn`（`setTimeout` + 随机延迟，`simulateError` 抛已脱敏错误） | 无 fetch / 无 WebSocket / 无 SSE / 无后端 fallback | **PASS** |
| `design-system-backtest-chart-smoke.spec.ts` | `/dev/design-system` | `BacktestCurveChart` 接收静态 `points` props（含 `null` unavailable 用例） | 组件无 fetch/axios/useQuery（已 grep 核实） | **PASS** |

### `design-system-live-query-smoke.spec.ts` 专项审查

- `LiveQueryDemo` 的 `queryFn` 是纯本地 `async`：`await new Promise(setTimeout)` 后返回随机价格，`simulateError` 时 `throw new Error('SIMULATED_SOURCE_ERROR')`。**无任何网络出口**。
- `useLiveQuery` 仅在 TanStack Query 之上包装 polling / manual refresh / enabled 与状态归一化，hook 自身**不发起网络请求**，传输完全由调用方 `queryFn` 决定（hook 注释明确"不接 WebSocket / SSE"）。
- mock 漏洞 / 后端 fallback / 未拦截请求：**未发现**。该页所有数据均为组件内合成常量或本地 promise；无 axios client、无 `/api` 调用、无 query hook 指向后端。
- 由于本 spec 不调用 `loginToConsole()`、不带 storageState，`AuthBootstrap` 不会触发 `getCurrentUser`。
- 判定：**纯 loopback / no-backend 成立**，保留在 5A allowlist。

### 最终 5A allowlist

```
login-page-smoke.spec.ts
design-system-table-smoke.spec.ts
design-system-live-query-smoke.spec.ts
design-system-backtest-chart-smoke.spec.ts
```

无存疑 spec 需移出。

---

## 2. 5A 边界要求确认

计划已明确以下 5A 约束，评审认可：

- 仅 `npm run build` + loopback `vite preview --host 127.0.0.1 --port 5179`（§3.1 / §7.7）。
- 不调用 `loginToConsole()`（5A 不含任何 backend-required spec）。
- 不带 storageState（配置无 storageState，依赖隔离 context）。
- 不上传任何 artifact（§9 初始 policy = console sanitized summary only）。
- trace / screenshot / video 实施时**必须显式设为 `off`**（计划要求覆盖 `trace=retain-on-failure` 为 `off`，screenshot/video 保持 `off`）。
- 不因页面功能失败而顺带修改页面、组件或 spec（§11「No batch may include frontend feature/page development」；rollback「retain frontend build」）。

补充实施约束（评审追加，仍为 plan-level，不修改实现）：
- 实施 5A 时若发现 spec 失败，只能作为 product/test regression 失败 CI，不得在 5A 内编辑 spec/页面"修绿"。

---

## 3. 5B-ENV P1 前置确认

计划 §5 / §6 / §11(5B-ENV) 完整保留以下 P1 前置，评审认可：

- 独立 `postgres:16` service，job-scoped，**不复用** `backend` / `postgres-flyway` job 的数据库或容器（仅可复用 Batch 2 service pattern/version/health check，不复用数据）。
- fresh DB；Flyway migrate + validate（无 `clean` / 无 `baselineOnMigrate` / 不改 migration / 不 skip）。
- Flyway 后**同步、显式、fail-closed** CI fixture；**不得恢复已删除的 seed watcher / polling loop**。
- 仅 seed auth user + legacy `accounts` row，**不创建 credential material**、不写 `exchange_account_credentials`。
- backend readiness（loopback-only health check）。
- runtime no-outbound enforcement（见 §4）。
- 进程树清理与 `if: always()` cleanup，但 cleanup 不得把 test failure 变绿。

---

## 4. runtime no-outbound 与 5B-SMOKE 阻断确认

- 计划 §6 明确：`ExchangeNoOutboundGuard` 是 **JUnit / test-scope JVM 代码**，由 JUnit smoke 安装，**不会自动保护单独启动的 E2E Spring Boot 进程**。这是 P1 实现缺口。
- 在 runtime no-outbound enforcement 被证明前（推荐 job-level egress deny + 受控 negative probe，或单独评审的 runtime guard；"未观察到外联日志"不算证明），**任何 authenticated backend-required spec 不得设为 required gate**。
- `loginToConsole()` 相关 spec **不得提前进入 CI 基线**（5B-SMOKE 被 5B-ENV 阻断）。
- `marketdata-ingestion-smoke.spec.ts` 已源码核实：其用例标题与注释明确"外网失败时保留可查询失败状态 / run-once 容忍 FAILED"，与 fail-closed no-outbound 冲突，**必须持续排除**，评审认可。

**runtime no-outbound P1 仅阻断 5B，不阻断 5A**：5A 不启动 backend、不产生任何出站交易所调用，因此 P1 与 5A 解耦。

---

## 5. artifact 与日志安全策略确认

计划 §9 完整，评审认可：

- 首轮仅允许 **console 的 sanitized summary**（spec/test title + 脱敏 error category + exit code + 有界脱敏摘录）。
- 禁止上传：raw backend logs、raw request/response、HAR、storageState、browser profile、HTML report、`test-results`、trace、video、screenshot。
- 未来 upload（单独 Batch 5C 评审）必须满足：单一有界 staging 目录、拒绝 symlink/绝对路径/未知归档/profile/HAR/storageState/source map/raw logs、路径归一化为 repo-relative、结构化解析并移除 headers/cookies/query/payload/env、过 Batch 4C pre-upload gate、命中 token/cookie/Authorization/JDBC/env/真实账户/绝对路径时 fail-closed（只报 rule + relative file，不报命中文本）、`if-no-files-found: error` + 有界 retention（PR 3 天 / `dev` push ≤ 7 天）。
- trace/video/screenshot 作为二进制内容：Batch 4C gate 当前 reject binary/zip 而非安全脱敏，故**当前禁止上传**，认可。

---

## 6. failure taxonomy 确认

计划 §8 分类合理，评审认可下列「可 fail CI」与「环境配置阻断」划分：

| 类别 | 计划分类 | 可否 fail CI |
| --- | --- | --- |
| assertion / route-API contract / auth-account regression | Product/test regression | fail required job |
| Flyway migrate/validate / forbidden fixture assertion | Environment/data contract regression | fail required job |
| backend 起后即退 / health 不 UP / seed 缺失 | `ENVIRONMENT_CONFIGURATION_FAILURE` | fail job（标类别，非 test failure） |
| 浏览器安装/校验/可执行失败 | `TOOLCHAIN_ENVIRONMENT_FAILURE` | fail job（凭证据可手动 rerun） |
| preview 启动/端口冲突 | `ENVIRONMENT_CONFIGURATION_FAILURE` | fail job |
| runtime no-outbound enforcement 缺失 | 5B 前置缺口（P1） | 阻断 5B 进入 required，不属于 5A |
| unsafe output / artifact hygiene 违规 | 安全违规 | fail job（禁止上传违规内容） |
| 条件 fixture 缺失 / 非 required case skip | Not executed | **不得计 passed** |

`continue-on-error` / retry masking / Flyway clean / baseline escape 一律禁止，认可。

---

## 7. 范围纯净性确认

计划未混入以下任一项（已逐项核实 §11「No batch may include …」与全文）：

- 页面重构：无。
- backend 业务修改：无。
- migration：无。
- 依赖升级：无。
- Batch 4F-B 至 4F-F：无（保持 OPTIONAL BACKLOG / NOT STARTED）。
- LIVE / AI / DH runtime / RealClient / real provider：无（保持 DISABLED / NOT STARTED / NOT INTEGRATED / NOT IMPLEMENTED）。

---

## 8. 实施顺序与前置条件（最终口径）

```
5A (no-backend allowlist)
  └─ 前置：build + loopback preview；trace/screenshot/video=off；console summary only；无 backend；不调用 loginToConsole
     └─ 验收：连续两次 CI 绿；0 skip；无 artifact；Batch 4C log proof 复检

5B-ENV  [P1 PREREQUISITE / NOT STARTED]
  └─ 前置：isolated PostgreSQL 16 + fresh DB；Flyway migrate+validate；同步 fail-closed fixture；
           backend readiness；runtime no-outbound enforcement 证明（egress deny + negative probe）
     └─ 验收：readiness 确定性；forbidden env/credential rows 缺席；negative outbound probe fail-closed

5B-SMOKE  [BLOCKED BY 5B-ENV]
  └─ 前置：5B-ENV 验收通过后，方可加入 4 个 authenticated low-side-effect spec
     └─ 验收：0 skip；fresh-DB repeat 绿；data assertion 有界

5C (optional artifact upload)
  └─ 前置：5B 稳定；过 Batch 4C gate + 结构化 sanitize + path normalization + 有界 retention

5D (incremental page-level，逐 domain)
  └─ 前置：每个 spec 有确定性 fixture/cleanup/fresh-DB repeat 证据

5E (freeze review)
  └─ 前置：连续两次不可变绿；P0/P1=0；状态文档同步
```

runtime no-outbound P1 是 5B-ENV 的硬前置，对 5A 不构成阻断。

---

## Findings

### P0

- 无。本轮未实现 Batch 5，无任何已执行写入、外联、artifact 上传或真实交易。

### P1

- （继承自 plan，未消除，正确保留）no-outbound guard 为 JUnit/test-scope，不自动覆盖单独启动的 E2E backend；5B backend-required E2E 变 required 前必须证明 runtime enforcement。该 P1 仅阻断 5B。
- （继承自 plan）`loginToConsole()` 写 exchange-account 状态并在内存/localStorage 持有 token；5B 必须 job-local fresh DB，禁止上传 trace/storageState/raw logs。
- （继承自 plan）`marketdata-ingestion-smoke.spec.ts` 与 fail-closed no-outbound 语义冲突，必须持续排除。

### P2

- （继承自 plan）当前 `run-e2e.mjs` 启动 Vite **dev server** 而非 preview；`vite.config.ts` 的 `/api` proxy 仅在 `server`，`preview` 无 proxy。5A 用 preview 可行；5B 不可假设 preview 自动代理 `/api`，需单独评审 routing。已源码核实，认可计划的处理。
- （继承自 plan）full suite 含环境型 skip（`research-detail` / `strategies-detail` / `backtest-dataset-binding` / `trading-workbench-query` order-detail）与硬编码 `http://127.0.0.1:18888`；不是确定性 CI allowlist。
- （继承自 plan）helper/error 文本可能含 raw API response body；脱敏前不得打印/上传。
- （继承自 plan）重型 backtest/paper-trading fixture 写持久行且无 suite-level teardown 契约。

### P3

- （继承自 plan）reporter/output/screenshot/video 当前依赖默认值；CI 实施时须显式固定（首轮 reporter=line/等价、screenshot/video/trace=off、清理 test-results）。
- （继承自 plan）`run-e2e.mjs` 仅 kill 直接 Vite 子进程，未文档化后代进程清理语义；5B 须补 Windows/Linux 进程树终止。

评审未新增超出 plan 的 P0/P1/P2/P3 阻断项；plan 自身风险登记完整、分类正确。

---

## 证据

- 文件：`frontend/src/router/routes.tsx:46-158` — `/dev/design-system` 与 `/login` 为顶层公开路由，`RequireAuth` 仅包裹业务路由树。
- 文件：`frontend/src/app/providers/AppProviders.tsx:45-55` — `currentUserQuery` `enabled: Boolean(accessToken)`，无 token 不发请求。
- 文件：`frontend/src/pages/dev/DesignSystemDemoPage.tsx:149-216` — `LiveQueryDemo` 本地 `queryFn`，无网络。
- 文件：`frontend/src/hooks/useLiveQuery.ts:91-117` — hook 仅包装 TanStack Query，传输由 `queryFn` 决定，不发起请求。
- 命令：`grep fetch|axios|useQuery|api\.|http` on `BacktestCurveChart.tsx` → No matches（纯展示组件）。
- 文件：`frontend/vite.config.ts:23-42` — `/api` proxy 仅在 `server`，`preview` 无 proxy。
- 文件：`frontend/playwright.config.ts:12-41` — Chromium / workers=1 / retries=0 / trace=retain-on-failure / 无 storageState。
- 文件：`frontend/tests/e2e/marketdata-ingestion-smoke.spec.ts:16-41` — run-once 容忍外网失败，冲突 fail-closed。
- 命令：`node -e package.scripts` → `build` = `tsc -b && vite build`、`preview` = `vite preview`。

---

## 风险

- 影响面：仅文档（`docs/current`）；不触碰 workflow、代码、测试、依赖、migration。
- 触发条件：本评审不触发任何运行时行为。
- 最坏结果：文档口径错误。已通过逐项源码核实与状态同步降低该风险。

---

## 修复建议

- 最小修复：本轮无需修复实现；接受 plan 作为实施基线。实施 5A 时按 §2 边界显式固定 `trace/screenshot/video=off`、reporter=line、清理且不上传 `test-results`。
- 验证方式：5A 实施后以连续两次 `dev` immutable green run + 0 skip + 无 artifact + Batch 4C log proof 复检为准。
- 回滚方式：5A 仅移除 E2E job/step，保留 frontend build；5B 移除 backend E2E path，Batch 2/3 冻结 job 不变。

---

## 未验证项

- 原因：本轮 planning-review only，禁止运行 Playwright/backend/PostgreSQL/Flyway/浏览器安装。
- 后续验证命令（仅在 Batch 5A/5B implementation 阶段、单独评审后执行，不在本轮）：
  ```powershell
  Set-Location frontend
  npm run build
  npx vite preview --host 127.0.0.1 --port 5179
  npx playwright test login-page-smoke design-system-table-smoke design-system-live-query-smoke design-system-backtest-chart-smoke
  ```

---

## 最终状态声明

- **Batch 5 plan = ACCEPTED AS IMPLEMENTATION BASELINE**。
- **Batch 5A = READY FOR IMPLEMENTATION**。
- **Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-A = FROZEN / ACCEPTED**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**。
- **Batch 4C = FROZEN / ACCEPTED**。
- **NQ GateK CI mainline = IN PROGRESS**。

> **工具声明**
>
> - 外部工具：未使用。
> - MCP：未使用。
> - Skills：未使用（任务为只读 CI/E2E plan review，直接用内置只读工具完成；未触发前端/后端/DB skill 实现路径）。
> - 网络访问：未使用。
> - 写操作：修改了 `docs/current/NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md`、`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/README.md`；未执行任何命令的写操作（仅只读检查命令）。
