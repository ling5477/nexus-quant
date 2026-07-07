# Roadmap

## 总路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH completed
  ↓
GateI completed
  ↓
GateJ completed
  ↓
GateK finalized / frozen / tagged
  ↓
GateL completed as No-Real Exchange / MarketData Readiness
  ↓
GateM finalized / frozen / accepted / tagged
  ↓
GateN finalized / frozen / accepted / closed / tagged
  ↓
GateO frozen / accepted
  ↓
GateP frozen / accepted / tagged / archived
  ↓
GateQ frozen / accepted / tagged / archived
  ↓
GateR frozen / accepted / tagged
  ↓
GateS-0 plan / fact-source reconciliation
  ↓
GateS-1 read-model work order plan ready
  ↓
GateS-1 minimal backend read model implementation（future / not started）
```

## 当前阶段

- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateR release tag：`nq-gater-freeze`。
- GateR archive pointer：`docs/gates/gate-r/README.md`。
- GateS：下一阶段唯一推荐主线。
- GateS recommended definition：策略验证运营化与 Shadow 诊断闭环阶段。
- GateS core object：`Strategy Validation Runtime Baseline`（策略验证运行时基线）。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现）。
- GateS-1 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）；GateS-1 implementation 仍 `NOT IMPLEMENTED`（未实现）。

## 下一步规则

下一步推荐在 GateS-1 work order 提交后，另起 `NQ-GATES-1-READ-MODEL-IMPLEMENTATION`，只实现最小 backend read model：future `GET /api/shadow-runs/overview`，聚合 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。该下一步当前仍 `NOT STARTED`（未开始），不允许本轮新增 API、migration、controller、DTO、domain、repository、SQL、frontend page、Playwright / E2E test、Python research code、CI workflow 或真实外部行为。

## GateS Batch Plan

| Batch | 状态 | 目标 |
| --- | --- | --- |
| GateS-0 | `PLAN / NOT IMPLEMENTED` | Plan / fact-source reconciliation |
| GateS-1 | work order `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`; implementation `NOT IMPLEMENTED` | Shadow Run operational dashboard / backend read model |
| GateS-2 | `NOT STARTED`（未开始） | Paper vs Shadow consistency enhancement |
| GateS-3 | `NOT STARTED` | Strategy Evaluation Gate runtime baseline |
| GateS-4 | `NOT STARTED` | Python parameter sweep / evaluation artifact baseline |
| GateS-5 | `NOT STARTED` | Frontend Strategy Validation / Shadow Workbench |
| GateS-6 | `NOT STARTED` | Monitoring / Incident / Replay read-only baseline |
| GateS-FREEZE | `NOT STARTED` | freeze、docs、regression、boundary audit |

## 当前边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。
- Shadow Run local fact table / record：`IMPLEMENTED AS LOCAL DIAGNOSTIC FACT ONLY`（仅作为本地诊断事实已实现）。
- Shadow Run read-only API：`IMPLEMENTED`（已实现）；写接口仍禁止。
- Shadow Run frontend list / detail / replay view：`IMPLEMENTED`（已实现）；执行按钮仍禁止。
- Shadow Run runner skeleton：`IMPLEMENTED`（已实现）；scheduler、后台 runner 和 Shadow Live runner 仍未启动。

## 当前不做

- 不把 GateS 写成 implemented / frozen / accepted。
- 不把 GateR 写成 `PLAN / NOT STARTED` 或 implementation pending。
- 不把 Shadow Run 写成真实交易、Shadow Live execution 或 trading approved。
- 不把 Strategy Validation 的 `APPROVED` 写成交易授权。
- 不新增 GateR-9 或 GateS implementation。
- 不新增 Shadow Run 写接口或执行按钮。
- 不启动 scheduler、后台 runner 或 Shadow Live runner。
- 不开启 LIVE。
- 不接 AI runtime。
- 不接 DH runtime。
- 不实现 RealClient、real provider、private trading adapter 或 real permission probe。
- 不把 readiness / preview / comparison / archive closeout / Data Quality / permission readiness / risk preflight 写成 trading authorization。
- 不新增后端、前端、research、scripts、deploy、workflow、migration、API、页面或测试。
