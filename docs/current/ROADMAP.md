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
GateS frozen / accepted / tagged
  ↓
GateT current work orders and limited implementation slices; not frozen / accepted / tagged
```

## 当前阶段

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateS release tag：`nq-gates-freeze`。
- GateS archive pointer：`docs/gates/gate-s/README.md`。
- GateS completed scope：strategy validation、Shadow diagnostics、Paper vs Shadow consistency、Incident / Replay overview、Python offline evaluation artifact baseline。
- GateT：已完成 GateT-0 planning、GateT-1 backend / frontend 最小只读切片和 GateT-2 work order；仍不是 `FROZEN`（已冻结）、`ACCEPTED`（已接受）或 `TAGGED`（已打 tag）。
- GateT-0 planning entry：`docs/current/GATET_PLAN.md`，`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。
- GateT-1 implementation：`GET /api/shadow-validation/workflow/overview` backend read model 与现有 `/strategies/validation` frontend overview 最小只读消费均已进入 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）。
- GateT-2 work order entry：`docs/current/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md`，`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。

## 下一步规则

下一步只能是 GateT plan。GateT implementation、backend / frontend / Python / CI / DB 变更、API 新增、migration 新增、runner / scheduler 启动、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter、real permission probe 或真实交易行为都不是 GateS closeout 的一部分，必须另起任务并重新做边界审查。

## GateS Closeout Summary

| Batch | 状态 | 目标 |
| --- | --- | --- |
| GateS-0 | `COMPLETED`（已完成） | Plan / fact-source reconciliation |
| GateS-1 | `COMPLETED` | Shadow Run overview backend read model + frontend overview summary |
| GateS-2 | `COMPLETED` | Paper vs Shadow consistency drilldown backend + frontend |
| GateS-3 | `COMPLETED` | Strategy Evaluation Gate overview backend + frontend |
| GateS-4 | `COMPLETED` | Python offline evaluation artifact baseline |
| GateS-5 | `COMPLETED` | Strategy Validation / Shadow Workbench frontend |
| GateS-6 | `COMPLETED` | Incident / Replay overview backend + frontend |
| GateS-FREEZE | `FROZEN / ACCEPTED / TAGGED` | freeze closeout、archive、release tag |

## GateT Planning Route

| Batch | 状态 | 目标 |
| --- | --- | --- |
| GateT-0 | `PLAN READY / NOT IMPLEMENTED`（规划已就绪 / 未实现） | Shadow Validation Operations plan / fact-source reconciliation |
| GateT-1 | `IMPLEMENTED / SELF-REVIEWED`（已实现 / 已自审） | Shadow Validation Workflow read model / operator model backend + frontend overview 最小只读消费 |
| GateT-2 | `PLAN READY / NOT IMPLEMENTED`（规划已就绪 / 未实现） | Consistency Evidence Refinement work order；候选 `GET /api/paper-shadow/consistency/evidence/overview`，尚未实现 |
| GateT-3 | `PLANNED / NOT STARTED` | Incident / Replay Review Workflow plan |
| GateT-4 | `PLANNED / NOT STARTED` | Python Evaluation Artifact read-only binding preview plan |
| GateT-5 | `PLANNED / NOT STARTED` | Frontend Validation Operations Workbench plan |
| GateT-6 | `PLANNED / NOT STARTED` | Runtime scheduling readiness review，仍不启动真实交易 |
| GateT-FREEZE | `PLANNED / NOT STARTED` | GateT closeout 条件复核 |

## 当前边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## 当前不做

- 不启动 GateT implementation。
- 不新增后端、前端、research、scripts、deploy、workflow、migration、API、页面或测试。
- 不新增 Shadow Run 写接口或执行按钮。
- 不启动 scheduler、后台 runner 或 Shadow trading。
- 不开启 LIVE。
- 不接 AI runtime。
- 不接 DH runtime。
- 不实现 RealClient、real provider、private trading adapter 或 real permission probe。
- 不把 readiness、validation、consistency、incident、archive closeout 或 Python artifact 写成真实交易授权。
