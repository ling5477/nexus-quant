# NexusQuant 文档入口

本目录只作为文档导航，不承载业务细节。当前事实以 `docs/current/` 为准；历史 Gate 与归档文档只读参考，不能作为当前开发依据。

## 当前入口

- 当前事实入口：`docs/current/README.md`
- 当前状态：`docs/current/STATUS.md`
- 当前架构：`docs/current/ARCHITECTURE.md`
- 当前模块：`docs/current/MODULES.md`
- 当前 API：`docs/current/API.md`
- 当前数据库：`docs/current/DB_SCHEMA.md`
- 当前验证：`docs/current/TESTING.md`
- 当前运行手册：`docs/current/RUNBOOK.md`
- 当前路线：`docs/current/ROADMAP.md`
- 当前工作日志：`docs/current/WORKLOG.md`

## 历史与规则

- 历史 Gate 卷宗：`docs/gates/`
  - `docs/gates/gate-h/`：GateH completed freeze snapshot（交易工作台、历史行情、dataset 绑定）
  - `docs/gates/gate-i/`：GateI completed freeze snapshot（虚拟币量化 V1 完整闭环）
- 归档文档：`docs/archive/`
- 文档规则：`docs/DOC_RULES.md`
- 模板：`docs/templates/`

## 当前边界

- GateH completed。
- GateI completed。
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- AI not started。
- GateJ 不是 AI 阶段。AI 最早 GateK 才允许进入信号层。

## 文档使用规则

- `docs/current` 是当前事实源，唯一开发入口。
- `docs/gates` 是历史 Gate 冻结卷宗，只读参考。
- `docs/gates/gate-i` 不是当前事实源，当前事实仍以 `docs/current/` 为准。
- `docs/archive` 只归档，不作为当前开发依据。
