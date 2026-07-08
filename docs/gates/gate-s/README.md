# GateS Archive

GateS 是 NexusQuant 的 strategy validation、Shadow diagnostics、Paper vs Shadow consistency、Incident / Replay overview 和 Python offline evaluation artifact baseline 阶段。

## 最终状态

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- Release tag：`nq-gates-freeze`。
- Tag message：`NexusQuant GateS freeze: strategy validation, shadow diagnostics, and incident replay baseline`。
- GateS-0..6：`COMPLETED`（已完成）。
- Next：GateT `PLAN / NOT STARTED`（规划 / 未开始）；不得从本归档推断 GateT implementation started。

## 归档内容

| 文件 | 用途 |
| --- | --- |
| [GATES_FREEZE_CLOSEOUT.md](GATES_FREEZE_CLOSEOUT.md) | GateS freeze closeout 归档 |
| [GATES_FREEZE_READINESS_REVIEW.md](GATES_FREEZE_READINESS_REVIEW.md) | readiness review 归档索引 |
| [GATES_0_PLAN.md](GATES_0_PLAN.md) | GateS-0 plan 归档索引 |
| [GATES_BATCH_0_6_EVIDENCE_MATRIX.md](GATES_BATCH_0_6_EVIDENCE_MATRIX.md) | GateS-0 到 GateS-6 evidence matrix |
| [GATES_API_EVIDENCE_SUMMARY.md](GATES_API_EVIDENCE_SUMMARY.md) | 四个 GET-only API 证据摘要 |
| [GATES_FRONTEND_EVIDENCE_SUMMARY.md](GATES_FRONTEND_EVIDENCE_SUMMARY.md) | frontend overview / drilldown / workbench / incident panel 证据摘要 |
| [GATES_PYTHON_RESEARCH_EVIDENCE_SUMMARY.md](GATES_PYTHON_RESEARCH_EVIDENCE_SUMMARY.md) | Python offline evaluation artifact 证据摘要 |
| [GATES_BOUNDARY_STATEMENT.md](GATES_BOUNDARY_STATEMENT.md) | GateS 边界声明 |

## 边界

GateS archive 不代表真实交易授权，不开启 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter 或 real permission probe，不启动 Shadow trading，也不表示 Python ML ready 或 Python live execution ready。
