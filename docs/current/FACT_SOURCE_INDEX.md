# Current Fact Source Index

本索引定义 authority 分层，不复制独立的 current Gate 判定。当前阶段必须解析 [STATUS.md](STATUS.md) 顶部唯一的 `nq-current-authority` 区块；任何冲突必须输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。

## 1. NQ Current Authority

1. [STATUS.md](STATUS.md)：唯一阶段状态 authority，schema v2 分离最近冻结 Gate、active Gate、active batch、下一动作与 LIVE/AI/DH 等机器可读状态。
2. [ROADMAP.md](ROADMAP.md)：只定义下一允许动作；不得覆盖 STATUS。
3. [README.md](README.md) 与 root `README.md`：入口、短摘要和 archive pointer；不得复制独立阶段状态。
4. [GATEV_PLAN.md](GATEV_PLAN.md)：GateV 唯一 active plan，定义受控验证自动化、durable operator review 与后续 implementation batch；不决定 current Gate，也不把未启动的 GateV-3 写成已实现。

## 2. NQ Capability Authority

- [API.md](API.md)：已实现 HTTP API 能力与边界。
- [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema。
- [ARCHITECTURE.md](ARCHITECTURE.md)：当前架构事实，不决定 Gate。
- [MODULES.md](MODULES.md)：模块职责与依赖边界，不决定 Gate。
- [RUNBOOK.md](RUNBOOK.md)：当前运行手册。
- [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md) 与 `frontend/ref/nq-design-system/**`：当前设计系统参考。

能力文档与阶段状态冲突时，先以代码和实际验证确定能力事实，再以 `STATUS.md` 判定 current Gate；不得用 API、schema、architecture 或 module 文案推进 Gate。

## 3. Evidence Ledger

- [TESTING.md](TESTING.md)：append-only validation evidence ledger。
- [WORKLOG.md](WORKLOG.md)：append-only work evidence ledger。

两份 ledger 可保留历史 `PLAN / NOT STARTED`、`PENDING`、失败 run 或旧 Gate 状态；它们不参与当前阶段判定，也不得覆盖 STATUS。

## 4. NQ-DH Integration Boundary

- 本仓库只保存 NQ 侧 contract/mock/test-support 与 no-real boundary。
- Integration runtime：读取 `STATUS.md`；当前不得由 Integration 历史文档推导为 started。
- Integration 文档不得修改 NQ current Gate，也不得把 mock/test-support 写成 real HTTP、real provider 或 LIVE。

## 5. DH External Authority

- DH 当前状态不由 NexusQuant 仓库托管。
- NQ-only 任务不得修改或宣称 DH current authority。
- 本仓库出现的 DH 状态只表示 NQ 侧边界，例如 `DH runtime=NOT_INTEGRATED`。

## 6. Gate Archive

- GateU durable archive：[../gates/gate-u/README.md](../gates/gate-u/README.md)。
- GateT / GateS / GateR archive：[../gates/gate-t/README.md](../gates/gate-t/README.md)、[../gates/gate-s/README.md](../gates/gate-s/README.md)、[../gates/gate-r/README.md](../gates/gate-r/README.md)。
- 其他已完成 Gate：`docs/gates/gate-*`。

Gate archive 是 frozen historical evidence。Pre-tag archive 可以保留当时的 `TAG PENDING` 语境；tag 后 current 状态只在 STATUS 同步，不要求 tagged commit 预先记录尚未生成的 tag object SHA。

## 7. Historical Evidence

- `docs/archive/**`：通用历史归档。
- `docs/gates/*/source/**`：从 current 迁出的过程文档 durable copy。
- [NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md](NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md)：已执行的治理计划，仅保留 historical governance context，不是 active current plan。

Historical evidence 中的旧状态、旧路径和旧 next action 不覆盖 current authority。

## 8. Allowed Residual

- `NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md` 暂留 `docs/current` 作为治理兼容入口；分类为 allowed historical context。
- `TESTING.md`、`WORKLOG.md` 中的旧链接与旧状态属于 append-only evidence；link checker 可报告 warning，但不得据此改变 current Gate。
- 历史 source copy 的旧相对链接默认 warning；核心 archive entry/current authority 断链仍为 error。

## 9. Current Boundary Summary

本节只解释 `STATUS.md`，不形成第二 authority：

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateV-1、GateV-2、GateV-3A 已通过 CI acceptance，GateV-3 scheduler 未开始。
- LIVE 与 Shadow trading 未启用；AI、DH runtime、Integration runtime 未开始；real provider 与 private trading 未实现。
