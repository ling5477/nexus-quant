# NQ-GATEN-5 Runtime UI Sandbox Source Display Implementation

## Status

**IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**

本文件记录 GateN-5 Runtime UI Sandbox Source Display 的最小前端实现结果。`IMPLEMENTED` 表示本轮已完成允许范围内实现；`SELF-REVIEWED` 表示已按 no-real / no-egress / forbidden wording 边界自检；`READY TO COMMIT` 表示当前工作区变更可进入提交前复核。

本轮没有实现 backend API、fake-server runtime、adapter skeleton、RealClient、real provider、真实 HTTP / WebSocket、private trading adapter、真实 permission probe、LIVE、AI runtime 或 DH runtime。

## 本轮实现结论

- GateN-5 最小实现已落在既有 `/marketdata` 页面 Data Quality / Readiness 区域内。
- 新增的是 compact sandbox/source display block，不是新页面、不是新菜单、不是 runtime readiness dashboard。
- UI 只显示 public marketdata 的 source / readiness / diagnostic 信息；public marketdata readiness 仍只是诊断，不是交易授权。
- 缺少后端明确字段时，UI 显示 `PENDING_BACKEND_SUPPORT`（等待后端支持），不推断 real provider、LIVE 或 trading state。
- GateN production adapter / API / runtime implementation 仍为 **NOT STARTED**。

## 实现范围

本轮改动只覆盖允许文件：

- `frontend/src/pages/marketdata/MarketdataPage.tsx`
  - 在既有 Data Quality / Readiness 卡片中加入 `SandboxSourceDisplay`。
  - 根据现有 bars / readiness / route query 状态生成 UI-only summary。
  - 显示 `sourceType`、`readiness`、`venue`、`capability` 和 `diagnostic` 字段。
- `frontend/src/types/marketdata.ts`
  - 增加 UI-safe source/readiness/capability 类型。
  - 没有新增 API contract，也没有引入真实 provider 字段。
- `frontend/tests/e2e/marketdata-quality-readiness-smoke.spec.ts`
  - 扩展既有 MarketData readiness smoke，断言 sandbox/source block 可见、字段可读、缺失能力显示等待后端支持，并保留“不代表交易授权”边界。
- `docs/current/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_IMPLEMENTATION_PLAN.md`
  - 将 GateN-5 implementation plan 更新为 implementation record。
- `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md`
- `docs/current/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `README.md`

未修改 `frontend/src/api/marketdata.ts`，因为既有 `/api/marketdata/readiness` 与 `/api/marketdata/bars` 已足够支持本轮 UI-only display；不需要新增 API。

## UI 展示行为

Source taxonomy（来源分类）：

- `LOCAL_DB`：当前 UI 使用既有 MarketData readiness / bars 本地事实时显示。
- `FIXTURE`：本轮不从运行时读取 fixture 结果；后续如需展示必须由单独 API plan 批准。
- `FAKE_SERVER`：fake-server runtime 未实现，本轮不展示为当前运行来源。
- `NO_EGRESS_SANDBOX`：本轮仅以 UI 标签说明浏览器侧不触发交易所外联，不把它写成后端已确认的运行状态。
- `PUBLIC_SANDBOX_CANDIDATE`：仅保留未来候选语义，本轮不显示为 real provider readiness。

Readiness taxonomy（就绪/健康诊断分类）：

- `FRESH`：现有 readiness / source-health 表示新鲜可用时映射。
- `STALE`：现有 freshness/source-health 表示过期时映射。
- `GAP`：现有质量统计存在 gap 时映射。
- `ERROR`：现有 readiness error / loading failure / backend unavailable 时映射。
- `DISABLED`：现有状态明确 disabled 时映射。
- `PENDING_BACKEND_SUPPORT`：既有 API 没有明确字段、per-capability diagnostics 或 no-egress fact 时显示。

Capability（能力项）显示：

- `bars`：使用既有 bars/readiness 事实映射。
- `instrument metadata`：本轮显示 `PENDING_BACKEND_SUPPORT`。
- `ticker`：本轮显示 `PENDING_BACKEND_SUPPORT`。
- `exchange status`：本轮显示 `PENDING_BACKEND_SUPPORT`。

Diagnostic（诊断）字段：

- `reasonCode`：优先使用现有 readiness / source-health / freshness / gap / error 事实，否则为 `PENDING_BACKEND_SUPPORT`。
- `reasonText`：只描述 local DB / sandbox source 的诊断原因，不描述 real provider authorization。
- `checkedAt`：使用现有 `generatedAt` 或查询上下文时间；缺失时为 `PENDING_BACKEND_SUPPORT`。
- `noEgress`：显示为 `PENDING_BACKEND_SUPPORT`，因为当前后端 contract 没有显式 no-egress 字段；UI 同时标注本前端块不触发交易所外联。
- `sourceLabel`：使用 `Local DB marketdata readiness` 或 `Sandbox source pending backend support`。

## 数据来源行为

本轮只消费既有数据源：

- `marketdataApi.getReadiness`，对应既有 `/api/marketdata/readiness`。
- `marketdataApi.listBars`，对应既有 `/api/marketdata/bars`。
- 当前 `/marketdata` 页面查询上下文，例如 venue、symbol、interval。
- 既有前端错误、loading、empty、quality summary 状态。

本轮没有：

- 新增 API。
- 新增 backend DTO。
- 暴露 GateN-4 fixture smoke 动态结果。
- 读取 fake-server runtime 状态。
- 读取 credential。
- 调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken host。
- 调用 account、balance、order、cancel、transfer、withdraw、user-data-stream 或 signed endpoint。

## 文案边界

允许 UI 文案：

- `Sandbox`
- `Fixture`
- `No-egress`
- `Public candidate`
- `Local DB`
- `Pending backend support`
- `Public marketdata candidate`

禁止 UI 文案仍保持禁止：

- `ready for live`
- `live ready`
- `real-ready`
- `provider ready`
- `trading authorized`
- `account authorized`
- `permission verified`
- `private ready`
- `LIVE_READY`
- `TRADING_AUTHORIZED`
- `REAL_PROVIDER_READY`
- `PRIVATE_READY`
- `ACCOUNT_AUTHORIZED`
- `PERMISSION_VERIFIED`

历史 live-0 只能留在历史证据或 reconciliation 文档中，不进入当前 UI readiness badge。

## 验证结果

已执行前端构建：

```powershell
Set-Location frontend
npm run build
```

结果：PASS。`tsc -b && vite build` 成功；仅保留既有 Vite chunk size warning。

已执行最小 smoke：

```powershell
Set-Location frontend
npm run test:e2e -- marketdata-quality-readiness-smoke.spec.ts --project=chromium
```

结果：PASS。`marketdata-quality-readiness-smoke.spec.ts` 1 passed。运行中出现既有 non-blocking warning：`NO_COLOR` 被 `FORCE_COLOR` 覆盖、Ant Design `Card.bordered` deprecation、Ant Design React 19 compatibility warning。

收尾阶段继续执行 `git status --short`、`git diff --check`、`git diff --stat`、forbidden wording scan、sensitive keyword scan、real-host scan 和 forbidden-scope diff。

## Boundary Confirmation

- `backend/**` 未修改。
- `research/**` 未修改。
- `scripts/**` 未修改。
- `deploy/**` 未修改。
- `.github/**` 未修改。
- `backend/**/db/migration/**` 未修改。
- 未新增 API。
- 未新增页面。
- 未新增 migration。
- 未改 CI workflow。
- 未实现真实 HTTP client。
- 未实现真实 WebSocket。
- 未实现 adapter skeleton。
- 未实现 fake server runtime。
- 未实现 RealClient / real provider。
- 未读取或输出 credential material。
- 未开启 LIVE。
- 未接 AI runtime。
- 未接 DH runtime。
- 未执行真实 permission probe。
- 未下单、撤单、转账或提现。

## P0/P1/P2/P3 Findings

- P0：无。
- P1：无。
- P2：现有 backend readiness contract 仍没有显式 `sourceType`、`noEgress` 与 per-capability diagnostics；本轮按边界显示 `PENDING_BACKEND_SUPPORT`，未伪造 provider facts。
- P3：本轮构建和 smoke 仍暴露既有前端 warning（Vite chunk size、Ant Design deprecated/React 19 compatibility），不阻断 GateN-5 no-real UI slice。

## Final Decision

**PASS / IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**

GateN-5 Runtime UI Sandbox Source Display 已完成最小前端实现。当前状态仍不是 LIVE ready、不是 real provider ready、不是 trading authorization、不是 private trading authorization。GateN-FREEZE 尚未开始，需单独任务确认 GateN-0 到 GateN-5 状态一致后才能进入。

## Recommended Next Task

`NQ-GATEN-FREEZE`

该任务应只冻结 GateN-0 到 GateN-5 已完成状态与 no-real 边界；不得开启 LIVE / AI / DH runtime / private trading / real provider。

推荐 commit message：

```text
feat(gaten): add marketdata sandbox source display
```
