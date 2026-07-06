# NexusQuant

NexusQuant 是通用量化交易平台。当前事实入口以 `docs/current/` 为准；`docs/gates/` 保存已完成 Gate 的冻结卷宗；`docs/archive/` 保存历史归档和本轮 current cleanup 归档。

## 当前状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag：`nq-gateq-freeze`。
- GateQ archive：`docs/gates/gate-q/README.md`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateP release tag：`nq-gatep-freeze`。
- GateO 及更早 Gate：以 `docs/gates/**` 或 `docs/archive/**` 作为历史证据。
- GateR：`NQ-GATER-PLAN-SHADOW-RUN-OPERATIONALIZATION：PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。

## Current Docs

- `docs/current/README.md`：当前事实入口。
- `docs/current/STATUS.md`：当前状态。
- `docs/current/ROADMAP.md`：当前路线。
- `docs/current/FACT_SOURCE_INDEX.md`：事实源优先级。
- `docs/current/GATER_PLAN.md`：GateR Shadow Run operationalization planning。
- `docs/current/TESTING.md`：验证记录。
- `docs/current/WORKLOG.md`：工作记录。
- `docs/current/API.md`：当前 API 事实。
- `docs/current/DB_SCHEMA.md`：当前 DB schema 事实。
- `docs/current/ARCHITECTURE.md` / `docs/current/MODULES.md`：当前架构和模块边界摘要。
- `docs/current/RUNBOOK.md`：当前本地运行手册。

## Historical Archives

- GateQ archive: `docs/gates/gate-q/README.md`。
- GateP archive: `docs/gates/gate-p/README.md`。
- GateO archive: `docs/gates/gate-o/README.md`。
- GateM / GateN archives: `docs/gates/gate-m/README.md`, `docs/gates/gate-n/README.md`。
- Post-GateQ current cleanup archive: `docs/archive/current-cleanup/post-gateq/README.md`。

## Boundary

GateQ archive 不代表真实交易授权，不开启 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter、real permission probe，也不启动 Shadow Live runner 或 Shadow run 写侧 fact source。GateR 当前只完成 Shadow Run operationalization planning；不得把 GateR 写成 started 或 implemented，不得把 Shadow Run 写成 table created、record created、runner started、trading authorization 或 live-ready。
