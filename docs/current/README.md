# Current Stage

`docs/current/` 是 NexusQuant 当前事实入口。当前状态是 **RC1 completed and frozen；GateH-PRE completed；正在执行 DOC-CLEAN + BASELINE-FIX；下一步进入 GateH-PLAN**。

## 当前状态

- RC1 completed and frozen。
- GateH-PRE completed。
- 当前正在执行 `DOC-CLEAN + BASELINE-FIX`。
- 下一步是 `GateH-PLAN`。
- `GateH-PLAN` 完成前不开发 GateH 新业务功能。
- 当前本地 PostgreSQL 默认端口固定为 `5432`。

## 当前不是

- 当前不是完整虚拟币量化 V1。
- 当前不是 AI 自动交易阶段。
- 当前不允许 AI 直接下单。
- AI 接入必须等虚拟币 V1 和 Paper Trading 稳定后再进入。

## 项目路线

```text
DOC-CLEAN / BASELINE-FIX
  ↓
GateH-PLAN
  ↓
GateH：交易工作台 + 历史行情数据接入
  ↓
GateI：虚拟币量化 V1 完整闭环
  ↓
GateJ：Paper Trading 稳定运行
  ↓
GateK：AI 信号接入
  ↓
GateL：AI Paper Trading
  ↓
GateM：AI 小资金 LIVE
  ↓
GateN：美股适配
  ↓
GateO：A 股适配
```

## 当前事实文件

- `STATUS.md`：当前项目状态与未完成项。
- `ARCHITECTURE.md`：当前架构事实与依赖边界。
- `MODULES.md`：模块 owner、职责和禁止反向依赖。
- `API.md`：当前 API 分类入口。
- `DB_SCHEMA.md`：数据库事实入口。
- `TESTING.md`：统一验证命令与本次验证记录。
- `RUNBOOK.md`：本地启动与常见问题。
- `ROADMAP.md`：总路线。
- `PLAN_GATEH.md`：GateH 规划入口，只做规划，不开发功能。
- `WORKLOG.md`：本次 DOC-CLEAN + BASELINE-FIX 执行日志。
