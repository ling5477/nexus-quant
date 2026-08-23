# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要，不复制完整 machine authority。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateX：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-x/](../gates/gate-x/)。
- GateY：`IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）；GateY-6E=`ACCEPTED / CI GREEN`（已接受 / CI 已通过），GateY-6F=`REVIEW ACCEPTED / READY TO COMMIT`（定向复核已接受 / 可提交）；GateY 整体未接受、未冻结。
- Attempt-13=`COMPLETED / ACCEPTED`; production deployment=`STOPPED`；production soak=`COMPLETED`，worker=`STOPPED`。
- 当前唯一动作是 `NQ-GATEY-6F-MINIMAL-LIVE-PILOT-END-TO-END-COMMIT-AND-PUSH`；V42、durable lease、controlled kill window、one-shot command 与 scoped OKX execution source capability 已本地通过定向复核，但尚未 commit/CI/deploy。operator 七项参数未提供，credential/OKX/PLACE/CANCEL 均为 0；精确边界以 [STATUS.md](STATUS.md) 为准。
- 当前 runtime release、current work commit 与精确 runtime 标识只从 [STATUS.md](STATUS.md) 获取，本索引不复制其值。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| GateY 当前计划 | [GATEY_PLAN.md](GATEY_PLAN.md) | 否；规划候选能力、hard gate 与批次，不构成 runtime/LIVE 授权 |
| GateY-1 已接受 work order | [GATEY_1_LIVE_SESSION_DATA_MODEL_WORK_ORDER.md](GATEY_1_LIVE_SESSION_DATA_MODEL_WORK_ORDER.md) | 否；独立 review 与 exact-head CI 已接受候选 schema/state/transaction/idempotency，不表示 migration 或 runtime 已实现 |
| GateY 当前任务证据 | [evidence/gate-y/README.md](evidence/gate-y/README.md) | 否；只记录可复核证据 |
| GateX 已归档实施基线 | [GATEX_PLAN.md](../gates/gate-x/GATEX_PLAN.md) | 否；只保存批次、边界与验收历史，不决定 current 状态 |
| GateX strict archive | [../gates/gate-x/README.md](../gates/gate-x/README.md) | 否；已冻结历史证据，不决定 current 状态 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| Gate 治理 workflow | [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md) | 否；定义 checker、lifecycle、evidence 与 release contract |
| Post-tag current evidence | [evidence/gate-w/README.md](evidence/gate-w/README.md) | 否；只保留 current 导航与 closeout evidence |
| GateW strict archive | [../gates/gate-w/README.md](../gates/gate-w/README.md) | 否；已冻结历史证据 |
| API / Schema / 架构 | [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |

## Historical Evidence

- Gate archive： [../gates/](../gates/)；最近已冻结 Gate 的精确 tag 与 archive 入口从 [STATUS.md](STATUS.md) 获取。
- GateX 不可覆盖的 task 历史统一从 [archived task evidence](../gates/gate-x/source/task-evidence/README.md) 访问；PASS、FAIL、BLOCKED、retry 与 remediation 均保留。GateW archive 继续保留其历史 Attempt 证据。
- General archive： [../archive/](../archive/)。
- Historical evidence 不覆盖 [STATUS.md](STATUS.md)，也不授权新的 runtime、acceptance、freeze、archive 或 tag 操作。

## Current Is Not

- 本入口不判定 accepted/work batch；精确状态只读取 [STATUS.md](STATUS.md)。
- 不是 LIVE、Shadow trading、AI、DH 或 Integration runtime 已启动。
- 不是 RealClient、real provider、private trading adapter 或真实交易已获授权。
- GateX release/admission/materialization、CI 或本地验证事实均不得推导为 Shadow execution、远端 permission、账户健康、余额充分或交易授权。
