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
GateT frozen / accepted / tagged
  ↓
GateU PLAN / NOT STARTED
```

## 当前阶段

- GateT：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateT release tag：`nq-gatet-freeze`。
- GateT archive pointer：`docs/gates/gate-t/README.md`。
- GateT completed scope：Validation Operations、Shadow Validation Workflow、Consistency Evidence、Incident / Replay Review、Evaluation Artifact Preview No-file baseline、Validation Operations Workbench、Runtime Scheduling Readiness Review。
- GateU：`PLAN / NOT STARTED`（规划 / 未开始）。

## GateT Closeout Summary

| Batch | 状态 | 目标 |
| --- | --- | --- |
| GateT-0 | `COMPLETED`（已完成） | Shadow Validation Operations plan |
| GateT-1 | `COMPLETED` | Shadow Validation Workflow backend + frontend |
| GateT-2 | `COMPLETED` | Consistency Evidence backend + frontend |
| GateT-3 | `COMPLETED` | Incident / Replay Review backend + frontend |
| GateT-4 | `COMPLETED` | Evaluation Artifact Preview No-file baseline backend + frontend |
| GateT-5 | `COMPLETED` | Validation Operations Workbench |
| GateT-6 | `COMPLETED` | Runtime Scheduling Readiness Review；readiness-review only |
| GateT-FREEZE | `FROZEN / ACCEPTED / TAGGED` | freeze closeout、archive、release tag |

## 下一步规则

下一步只能是 GateU `PLAN / NOT STARTED`。GateU planning 必须另起任务，先确认范围、事实源、禁止边界和 docs budget；不得从 GateT closeout 自动进入 GateU implementation。

任何 backend / frontend / Python / CI / DB 变更、API 新增、migration 新增、runner / scheduler 启动、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter、real permission probe 或真实交易行为都不是 GateT closeout 的一部分，必须另起任务并重新做边界审查。

## 当前边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML readiness：`NO`（否）。
- Python live execution readiness：`NO`（否）。

## 当前不做

- 不启动 GateU implementation。
- 不新增后端、前端、research、scripts、deploy、workflow、migration、API、页面或测试。
- 不新增 Shadow Run 写接口或执行按钮。
- 不启动 scheduler、后台 runner 或 Shadow trading。
- 不开启 LIVE。
- 不接 AI runtime。
- 不接 DH runtime。
- 不实现 RealClient、real provider、private trading adapter 或 real permission probe。
- 不把 readiness、validation、consistency、incident、archive closeout 或 Python artifact 写成真实交易授权。
