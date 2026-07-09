# GateT-0 Plan Archive

状态：GateT-0 `COMPLETED`（已完成）。

## Source Durable Copy

GateT-0 Shadow Validation Operations plan 的完整历史 copy 已通过 current residual move 固化到 [source/GATET_PLAN.md](source/GATET_PLAN.md)。该 source copy 仅作为 historical evidence（历史证据），不作为 current authority（当前事实权威）扩写；当前状态仍以 `docs/current/` 的 status / roadmap / testing / worklog / API / DB facts 为准。

## 冻结摘要

- GateT-0 定义 Shadow Validation Operations / 策略验证运营闭环规划。
- GateT-0 只规划 GateT 批次、边界、候选 workflow、验证策略和 freeze 条件。
- GateT-0 不实现 API、migration、backend、frontend、Python、CI、runner、scheduler 或 runtime。
- GateT-0 不调用真实交易所、不读取 credential、不启动 LIVE、不接 AI / DH runtime。

## 批次路线

- GateT-1：Shadow Validation Workflow backend + frontend。
- GateT-2：Consistency Evidence backend + frontend。
- GateT-3：Incident / Replay Review backend + frontend。
- GateT-4：Evaluation Artifact Preview No-file baseline backend + frontend。
- GateT-5：Validation Operations Workbench。
- GateT-6：Runtime Scheduling Readiness Review，选择 readiness-review only。

GateT-0 完成后，GateT-1 到 GateT-6 均已完成并纳入 [GATET_BATCH_0_6_EVIDENCE_MATRIX.md](GATET_BATCH_0_6_EVIDENCE_MATRIX.md)。
