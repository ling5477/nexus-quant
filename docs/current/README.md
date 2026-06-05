# Current Stage

`docs/current/` 是 NexusQuant 当前事实入口。当前状态是 **GateJ completed；Next: GateK-PLAN；AI not started**。

## 当前状态

- GateH completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- GateJ completed。
- Next: GateK-PLAN。
- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- UI/UX professionalism remains post-freeze remediation。
- 当前本地 PostgreSQL 默认端口固定为 `5432`。

## 当前不是

- 当前不是 AI 自动交易阶段。
- 当前不允许 AI 直接下单。
- 当前不允许真实 LIVE 下单。
- AI 接入必须等 Paper Trading 稳定后再进入（最早 GateK）。
- GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。
- GateJ-FREEZE 已完成连续运行验收与冻结，不夹带 AI、不夹带新业务功能。
- 当前不是 GateK 实现阶段；Next 只是 GateK-PLAN。
- 当前不代表 UI/UX 专业化已完成。
- 当前不应描述为面向公开用户的生产就绪。

## 项目路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI：虚拟币量化 V1 完整闭环 completed
  ↓
GateJ：Paper Trading 稳定运行 completed
  ↓
GateK：AI 信号接入规划（NEXT）
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

## 当前 GateJ 规划文件

- `PLAN_GATEJ.md`：GateJ 阶段规划。
- `GATEJ_API_PLAN.md`：GateJ API 规划。
- `GATEJ_DB_PLAN.md`：GateJ DB 规划。
- `GATEJ_FRONTEND_PLAN.md`：GateJ 前端规划。
- `GATEJ_TEST_PLAN.md`：GateJ 测试规划。
- `GATEJ_WORK_ORDER.md`：GateJ 工作单（含 GateJ-FREEZE 范围）。
- `GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`：GateJ-FREEZE 最终验收报告。
- `DOC_CLEAN_REPORT.md`：最近一次文档清理报告。

## 历史 Gate 冻结卷宗

已完成 Gate 的计划文档不在 `docs/current/` 重复，统一保存在 `docs/gates/`：

- `docs/gates/gate-h/`：GateH 冻结卷宗。
- `docs/gates/gate-i/`：GateI 冻结卷宗。
- `docs/gates/gate-j/`：GateJ 冻结卷宗。
- `docs/gates/gate-a..g/`：早期 Gate 历史卷宗。
