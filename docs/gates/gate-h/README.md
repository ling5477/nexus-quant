# GateH Freeze Snapshot

本目录是 GateH completed 后的只读历史快照，用于保留 GateH 完成时的规划、API、DB、前端、测试、状态和工作记录参考。

## 状态

- GateH completed。
- GateI 也已 completed（详见 `docs/gates/gate-i/`）。
- 本目录不代表当前开发入口，当前事实仍以 `docs/current/` 为准。
- 当前阶段为 GateJ-PLAN（Paper Trading 稳定运行），AI 仍未开始。

## GateH 范围

GateH 覆盖以下已完成范围：

- 交易工作台正式化。
- OKX / Binance SPOT 历史 OHLCV K 线接入。
- marketdata dataset 管理。
- backtest config dataset 绑定。
- backtest run dataset snapshot 固化。

## GateH 不包含

- 不包含 AI。
- 不包含 AI 信号。
- 不包含 AI 自动交易。
- 不包含 AI Paper Trading。
- 不包含 GateI 的策略版本主链。
- 不包含 GateI 的发布链路。
- 不包含 GateI-3 的 SIM / Paper Trading 运行闭环。
- 不包含 GateI-4 的风控回写、资金曲线、持仓曲线、交易复盘或异常停机闭环。

## 归档文件

本次 GateH freeze snapshot 复制自 `docs/current/` 中与 GateH 完成事实相关的文档：

- `PLAN_GATEH.md`
- `GATEH_API_PLAN.md`
- `GATEH_DB_PLAN.md`
- `GATEH_FRONTEND_PLAN.md`
- `GATEH_TEST_PLAN.md`
- `GATEH_WORK_ORDER.md`
- `API.md`
- `DB_SCHEMA.md`
- `TESTING.md`
- `STATUS.md`
- `ROADMAP.md`
- `WORKLOG.md`

目录中早期 GateH paused 草稿文件继续保留为历史参考，不作为当前入口。

## 使用规则

- 只读参考，不在本目录继续推进 GateI。
- 不从本目录恢复开发任务。
- 不在本目录创建 GateI 文档。
- 当前事实以 `docs/current/` 为唯一入口。
