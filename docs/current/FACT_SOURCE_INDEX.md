# Current Fact Source Index

本索引定义 authority 分层，不复制独立的 current Gate 判定。当前阶段必须解析 [STATUS.md](STATUS.md) 顶部唯一的 `nq-current-authority` 区块；任何冲突必须输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。

## 1. NQ Current Authority

1. [STATUS.md](STATUS.md)：唯一阶段状态 authority，schema v3 分离最近冻结 Gate、active Gate、accepted batch、work batch、下一动作与 LIVE/AI/DH 等机器可读状态。
2. [ROADMAP.md](ROADMAP.md)：只定义下一允许动作；不得覆盖 STATUS。
3. [README.md](README.md) 与 root `README.md`：入口、短摘要和 archive pointer；不得复制独立阶段状态。
4. [GATEW_PLAN.md](GATEW_PLAN.md)：GateW historical compatibility residual；durable baseline 已进入 GateW archive，不决定 current Gate。
5. [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md)：Gate checker、lifecycle、evidence 与 release 执行规则；machine contract 位于 `scripts/docs/governance-workflow-contract.json`，两者均不决定 current Gate。
6. [GATEV_PLAN.md](GATEV_PLAN.md)：GateV historical planning context 的 allowed residual；不决定 current Gate 或独立接受 work batch。
7. [evidence/gate-w/README.md](evidence/gate-w/README.md)：GateW post-tag current evidence index；只记录 closeout/remediation 导航，不决定 current Gate 或 implementation acceptance。
8. [../gates/gate-x/GATEX_PLAN.md](../gates/gate-x/GATEX_PLAN.md)：GateX 已归档 implementation baseline；不决定 current Gate，pre-tag 阶段也不构成 frozen authority。

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
- [evidence/gate-w/README.md](evidence/gate-w/README.md)：GateW post-tag current evidence 索引；不参与阶段判定。

两份 ledger 的历史状态不参与当前阶段判定，也不得覆盖 STATUS。

## 4. NQ-DH Integration Boundary

- 本仓库只保存 NQ 侧 contract/mock/test-support 与 no-real boundary。
- Integration runtime：读取 `STATUS.md`；不得由 Integration 历史文档推导为 started。
- Integration 文档不得修改 NQ current Gate，也不得把 mock/test-support 写成 real HTTP、real provider 或 LIVE。

## 5. DH External Authority

- DH 当前状态不由 NexusQuant 仓库托管。
- NQ-only 任务不得修改或宣称 DH current authority。
- 本仓库出现的 DH 状态只表示 NQ 侧边界，例如 `DH runtime=NOT_INTEGRATED`。

## 6. Gate Archive

- GateX strict archive candidate：[../gates/gate-x/README.md](../gates/gate-x/README.md)，当前处于 pre-tag closeout；只有 freeze commit、exact-head CI、annotated tag 与 post-tag verification 均成功后才可成为 durable frozen archive。
- GateX task evidence：[../gates/gate-x/source/task-evidence/README.md](../gates/gate-x/source/task-evidence/README.md)，保存 GateX 全部 PASS/FAIL/BLOCKED/retry/remediation 过程证据；原 `docs/current/evidence/gate-x/**` 已按 hash-preserving move 收口。
- GateW durable archive：[../gates/gate-w/README.md](../gates/gate-w/README.md)，`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag=`nq-gatew-freeze`。
- GateW task evidence：[../gates/gate-w/source/task-evidence/README.md](../gates/gate-w/source/task-evidence/README.md)，保存 freeze snapshot 的不可覆盖 attempts 与索引。
- GateV durable archive：[../gates/gate-v/README.md](../gates/gate-v/README.md)，release tag=`nq-gatev-freeze`。
- GateU durable archive：[../gates/gate-u/README.md](../gates/gate-u/README.md)。
- 其他已完成 Gate：`docs/gates/gate-*`。

Gate archive 是 historical evidence，不覆盖本文件。

GateW 与 GateX archive 的 `source/task-evidence/**` 是 approved non-role evidence；它不参与 archive role 计数，也不能替代 mandatory/conditional role。unknown、empty、path traversal 与 symlink/reparse point 仍 fail-closed。

## 7. Historical Evidence

- `docs/archive/**`：通用历史归档。
- `docs/gates/*/source/**`：从 current 迁出的过程文档 durable copy。
- GateW archive 保留 Attempt-09 拒绝、Attempt-10/11/12 失败终态、Attempt-13 接受以及全部 remediation 历史，不得删除、覆盖或改写为全部首轮通过。

Historical evidence 中的旧状态、旧路径和旧 next action 不覆盖 current authority。

## 8. Allowed Residual

- `GATEW_PLAN.md`：GateW historical compatibility residual；durable baseline 已固化于 [../gates/gate-w/GATEW_IMPLEMENTATION_BASELINE.md](../gates/gate-w/GATEW_IMPLEMENTATION_BASELINE.md)。后续只在明确授权的 archive move batch 中决定 current copy 的归档路径。
- `GATEV_PLAN.md`：GateV historical planning context residual；后续只在明确授权的 archive move batch 中决定归档路径。
- `TESTING.md`、`WORKLOG.md` 中的旧链接与旧状态属于 append-only evidence；不得据此改变 current Gate。

## 9. Current Boundary Summary

- GateW：`FROZEN / ACCEPTED / TAGGED`；freeze commit=`16376de28be78eea58afbe1374847ee07ca2ccc7`，tag=`nq-gatew-freeze`，strict archive/release/post-tag verification 均通过。
- GateW-ATTEMPT-13-168H-ACCEPTANCE：`ACCEPTED / CI GREEN`；Attempt-13=`COMPLETED / ACCEPTED`，production soak=`COMPLETED`。
- GateX：`IN PROGRESS / NOT FROZEN`；GateX-5=`ACCEPTED / CI GREEN`，GateX-FREEZE=`ACCEPTED / CI GREEN / FREEZE READY`。strict archive candidate 已形成，但 freeze commit/tag/post-tag authority transition 尚未完成。
- LIVE=`DISABLED`，kill switch=`ENGAGED`；Shadow trading 未启用；AI、DH runtime、Integration runtime 未开始；real provider 与 private trading 未实现。
- 唯一下一动作从 [STATUS.md](STATUS.md) 与 [ROADMAP.md](ROADMAP.md) 读取；本索引不建立第二份 action authority。
