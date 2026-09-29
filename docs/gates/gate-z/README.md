# GateZ canonical archive

GateZ 的冻结范围是 OKX Spot 单策略：公开已收盘行情、Dataset、冻结 StrategyVersion、Backtest、Evaluation、Publish、Continuous SIM、canonical Risk/Order/Trade/Ledger/Position/Cash/PnL、自动 Paper Matching、重启去重和 Paper Detail 投影。业务结果是同一策略版本可从研究进入自动 SIM 经济闭环，并由 Java Control Plane 持有交易事实。

本目录是 `PRETAG / TAG_PENDING` 的 strict archive 候选；`nq-gatez-freeze` 尚未由本快照证明存在。合并到 `dev`、exact-head CI、annotated tag 与远端回读后，最终冻结身份由 Git tag 和 `docs/current/STATUS.md` 表达。这里的历史结果不替代当时的失败、BLOCKED 或 UNKNOWN。

## 归档导航

- [冻结 closeout](GATEZ_FREEZE_CLOSEOUT.md)、[冻结就绪审查](GATEZ_FREEZE_READINESS_REVIEW.md)、[历史计划](GATEZ_PLAN.md)。
- [能力证据矩阵](GATEZ_EVIDENCE_MATRIX.md)、[测试与 CI](GATEZ_TESTING_AND_CI_SUMMARY.md)。
- [边界声明](GATEZ_BOUNDARY_STATEMENT.md)、[已知限制与残余](GATEZ_KNOWN_LIMITATIONS_AND_RESIDUALS.md)。
- [后端与数据库](GATEZ_BACKEND_DB_EVIDENCE.md)、[API](GATEZ_API_EVIDENCE.md)、[前端](GATEZ_FRONTEND_EVIDENCE.md)。
- [Python 边界](GATEZ_PYTHON_BOUNDARY_EVIDENCE.md)、[运行与调度](GATEZ_RUNTIME_SCHEDULING_EVIDENCE.md)。

这 13 个角色为 8 个 mandatory 与 5 个 conditional。原始 GateZ 只读资格、失败与 JSON 诊断，以及 2026-09-24 V1 设计输入快照按 blob 原文保存在 `docs/archive/gate-z/evidence/`；它们不成为新的 current authority。

GateZ 之外的第二策略、组合分配、其他交易所/衍生品、AI/DH、隔离执行 worker 与 LIVE 自动交易均留给后续独立决策。兼容字段和 Position row PnL 等残余按 [限制清单](GATEZ_KNOWN_LIMITATIONS_AND_RESIDUALS.md) 处理，不以清零作为本次 freeze 条件。LIVE 保持 DISABLED，kill 保持 ENGAGED。
