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
9. [GATEY_PLAN.md](GATEY_PLAN.md)：GateY 当前 planning baseline；定义候选目标、hard gate、控制面和批次，不表示 API/schema/runtime 已实现，也不授权 micro-live。

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
- [evidence/gate-y/README.md](evidence/gate-y/README.md)：GateY planning、work-order review 与 post-CI acceptance evidence 索引；不参与阶段判定或 implementation acceptance。

两份 ledger 的历史状态不参与当前阶段判定，也不得覆盖 STATUS。

### 3.1 GateY-6 正式子批次 lifecycle

- GateY-6A～6F 使用 `accepted_batch` / `work_batch` 表达可独立接受的正式子批次；子批次接受不等于 GateY 或 GateY-6 overall accepted/frozen。
- 普通子批次复用 `NOT_STARTED -> IMPLEMENTATION -> IMPLEMENTED|PENDING_REVIEW -> REVIEW_ACCEPTED|READY_TO_COMMIT -> commit -> exact-head CI -> next sub-batch`，高风险代码仍必须经过独立 review 与 exact-head CI。
- Governance contract `1.5.0` 进入 maintenance mode；不再为 GateY-6C、6D、6E、6F 增加 task-ID-specific override，也不再为单个 `next_action` 修改 matcher。
- 连续两个 governance/docs-only 任务未推进核心 capability 时，第三个任务必须触发 engineering drift review；治理变更仅在解决可复用系统性缺陷时允许，不得再以 checker compatibility 作为业务任务主线。
- 本轮 authority-sync commit 取得 exact-head CI green 后，必须直接进入 GateY-6C 代码/安全能力实现，不再插入 route validation、matcher hardening、plan review 或 authority-model review。

## 4. NQ-DH Integration Boundary

- 本仓库只保存 NQ 侧 contract/mock/test-support 与 no-real boundary。
- Integration runtime：读取 `STATUS.md`；不得由 Integration 历史文档推导为 started。
- Integration 文档不得修改 NQ current Gate，也不得把 mock/test-support 写成 real HTTP、real provider 或 LIVE。

## 5. DH External Authority

- DH 当前状态不由 NexusQuant 仓库托管。
- NQ-only 任务不得修改或宣称 DH current authority。
- 本仓库出现的 DH 状态只表示 NQ 侧边界，例如 `DH runtime=NOT_INTEGRATED`。

## 6. Gate Archive

- GateX durable strict archive：[../gates/gate-x/README.md](../gates/gate-x/README.md)，`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag=`nq-gatex-freeze`，freeze commit=`299ab30bd2e243314be2dc609cb244cd5388027b`。
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
- GateX：`FROZEN / ACCEPTED / TAGGED`；freeze commit=`299ab30bd2e243314be2dc609cb244cd5388027b`，tag=`nq-gatex-freeze`，strict archive/release/post-tag verification 均通过。
- GateX-5：`ACCEPTED / CI GREEN`；最终 `ADMISSION_MATERIALIZATION_FACT_TEAR=CLOSED`，但不授权 Shadow execution、trading 或 LIVE。
- GateY：`IN PROGRESS / NOT FROZEN`；GateY-6B typed provider contract=`ACCEPTED / CI GREEN / CONTRACT ONLY`，implementation/acceptance head=`990f8c5680c23d02dec059ca72e7355f88faa72e`，exact-head CI run=`31811302301`；GateY-6C=`NOT_STARTED`。该子批次接受不构成 GateY/GateY-6 overall acceptance、真实 provider 实现或 GateY-FREEZE 授权。30 项 hard gates 继续为 `0 PASS / 25 NOT_MET / 5 NOT_VERIFIABLE`、gap candidates=`10`；`FIRST_REAL_ORDER`、micro-live、credential访问、OKX network、真实 PLACE/CANCEL 与 LIVE 仍均未授权。
- LIVE=`DISABLED`，kill switch=`ENGAGED`；Shadow trading 未启用；AI、DH runtime、Integration runtime 未开始；real provider 与 private trading 未实现。
- 唯一下一动作从 [STATUS.md](STATUS.md) 与 [ROADMAP.md](ROADMAP.md) 读取；本索引不建立第二份 action authority。
