# Current Stage

`docs/current/` 是 NexusQuant 当前事实入口。当前状态是 **GateI completed；Next: GateJ-PLAN（Paper Trading 稳定运行）；AI not started**。

## 当前状态

- GateH completed。
- GateI completed。
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- AI not started。
- 当前本地 PostgreSQL 默认端口固定为 `5432`。

## 当前不是

- 当前不是 AI 自动交易阶段。
- 当前不允许 AI 直接下单。
- 当前不允许真实 LIVE 下单。
- AI 接入必须等 Paper Trading 稳定后再进入（最早 GateK）。
- GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。

## 项目路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI：虚拟币量化 V1 完整闭环 completed
  ↓
GateJ：Paper Trading 稳定运行 ← NEXT
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
- `WORKLOG.md`：执行日志。
