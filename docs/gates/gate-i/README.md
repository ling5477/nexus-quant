# GateI Freeze Snapshot

本目录是 GateI completed 后的只读历史快照，用于保留 GateI 完成时的规划、API、DB、前端、测试、状态和工作记录参考。

## 状态

- GateI completed。
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- AI not started。
- 本目录不代表当前开发入口，当前事实仍以 `docs/current/` 为准。

## GateI 范围

GateI 是虚拟币量化 V1 完整闭环阶段，覆盖以下已完成范围：

- GateI-1：策略版本与发布记录绑定。
- GateI-2：回测追溯与评估指标增强。
- GateI-3：SIM/Paper Trading 运行闭环（创建/启动/停止/订单/成交/持仓）。
- GateI-4：Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘、异常停机。

## GateI 不包含

- 不包含 AI。
- 不包含 AI 信号。
- 不包含 AI 自动交易。
- 不包含 AI Paper Trading。
- 不包含真实 LIVE 下单。
- 不包含美股/A 股。
- 不包含合约全量。
- 不包含高频。
- 不包含复杂因子平台。
- 不包含 GateJ 的 Paper Trading 稳定运行。

## 归档文件

本次 GateI freeze snapshot 复制自 `docs/current/` 中与 GateI 完成事实相关的文档：

- `PLAN_GATEI.md`
- `GATEI_API_PLAN.md`
- `GATEI_DB_PLAN.md`
- `GATEI_FRONTEND_PLAN.md`
- `GATEI_TEST_PLAN.md`
- `GATEI_WORK_ORDER.md`
- `API.md`
- `DB_SCHEMA.md`
- `TESTING.md`
- `STATUS.md`
- `ROADMAP.md`
- `WORKLOG.md`
- `FREEZE_SUMMARY.md`

## 使用规则

- 只读参考，不在本目录继续推进 GateJ。
- 不从本目录恢复开发任务。
- 不在本目录创建 GateJ 文档。
- 当前事实以 `docs/current/` 为唯一入口。
