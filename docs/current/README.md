# Current Stage

`docs/current/` 是 NexusQuant 的当前事实入口，只保留当前控制文档、状态、路线、验证/工作记录、API、DB schema、运行手册、架构/模块摘要和必要的 archive pointer。GateT 过程型证据已冻结到 `docs/gates/gate-t/`，current 只保留摘要和 archive pointer。

## 当前状态

- GateT：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateT release tag：`nq-gatet-freeze`。
- GateT archive pointer：[../gates/gate-t/README.md](../gates/gate-t/README.md)。
- GateT closeout：[../gates/gate-t/GATET_FREEZE_CLOSEOUT.md](../gates/gate-t/GATET_FREEZE_CLOSEOUT.md)。
- GateT evidence matrix：[../gates/gate-t/GATET_BATCH_0_6_EVIDENCE_MATRIX.md](../gates/gate-t/GATET_BATCH_0_6_EVIDENCE_MATRIX.md)。
- GateT-0..6：`COMPLETED`（已完成）。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gates-freeze`；archive pointer：[../gates/gate-s/README.md](../gates/gate-s/README.md)。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；archive pointer：[../gates/gate-r/README.md](../gates/gate-r/README.md)。
- GateQ / GateP / GateO 及更早 Gate：只作为历史证据读取，入口为 `docs/gates/**` 或 `docs/archive/**`。
- 下一阶段：GateU `PLAN / NOT STARTED`（规划 / 未开始）。GateU 实现未启动。

## Current Authority

| 用途 | 当前文件 |
| --- | --- |
| 当前状态 | [STATUS.md](STATUS.md) |
| 当前路线 | [ROADMAP.md](ROADMAP.md) |
| 当前事实源优先级 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) |
| 当前验证记录 | [TESTING.md](TESTING.md) |
| 当前工作记录 | [WORKLOG.md](WORKLOG.md) |
| 当前 API 事实 | [API.md](API.md) |
| 当前 DB schema 事实 | [DB_SCHEMA.md](DB_SCHEMA.md) |
| 当前架构/模块摘要 | [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md) |
| 当前运行手册 | [RUNBOOK.md](RUNBOOK.md) |
| 当前 Codex workflow | [CODEX_PROJECT_INSTRUCTIONS.md](CODEX_PROJECT_INSTRUCTIONS.md), [NQ_DH_CODEX_PLUGIN_WORKFLOW.md](NQ_DH_CODEX_PLUGIN_WORKFLOW.md), [NQ_DH_WORKFLOW_ROUTER_SKILL.md](NQ_DH_WORKFLOW_ROUTER_SKILL.md), [NQ_DH_CODEX_TASK_TEMPLATES.md](NQ_DH_CODEX_TASK_TEMPLATES.md) |
| Archive governance hardening | [NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md](NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md) |
| GateT archive | [../gates/gate-t/README.md](../gates/gate-t/README.md) |

GateT-0 plan、GateT-1 到 GateT-6 work order / implementation evidence、GateT freeze readiness review 和 GateT closeout 不再作为 current authority 扩写；它们的冻结证据以 [../gates/gate-t/README.md](../gates/gate-t/README.md) 为入口。

GateR / GateS / GateT 过程型 residual 的后续迁移计划见 [NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md](NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md)。该计划只定义 move batch，不移动文件，不启动 GateU。

## Historical Evidence

- GateT archive：[../gates/gate-t/README.md](../gates/gate-t/README.md)。
- GateS archive：[../gates/gate-s/README.md](../gates/gate-s/README.md)。
- GateR archive：[../gates/gate-r/README.md](../gates/gate-r/README.md)。
- GateQ archive：[../gates/gate-q/README.md](../gates/gate-q/README.md)。
- GateP archive：[../gates/gate-p/README.md](../gates/gate-p/README.md)。
- GateO archive：[../gates/gate-o/README.md](../gates/gate-o/README.md)。
- GateM / GateN archives：[../gates/gate-m/README.md](../gates/gate-m/README.md), [../gates/gate-n/README.md](../gates/gate-n/README.md)。

## Current Is Not

- 不是 GateU 已启动或已实现。
- 不是 LIVE enable。
- 不是真实交易授权。
- 不是 Shadow trading 已启用。
- 不是 AI runtime 已启动。
- 不是 DH runtime 已集成。
- 不是 Integration-1 runtime 已启动。
- 不是 RealClient、real provider、private trading adapter 或 real permission probe 已实现。
- 不是 Python ML readiness 或 Python live execution readiness。

## Current Cleanup Rule

GateT 已完成 freeze closeout，GateT-0 plan、GateT-1 到 GateT-6 过程证据、readiness review 和 freeze closeout 证据均以 `docs/gates/gate-t/` 为历史归档入口。`docs/current` 后续只维护当前状态、路线、验证、工作记录和 still-current API / DB / architecture facts；不得把 GateT archive closeout 写成 GateU implementation、LIVE、AI/DH runtime、real provider、private trading 或真实交易路径。
