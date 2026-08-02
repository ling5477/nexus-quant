# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要，不复制完整 machine authority。

## 当前摘要

<!-- nq-current-summary:start -->
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateW 当前 Attempt-13=`RUNNING / PENDING_168H`；production deployment=`STARTED`。
- 当前唯一动作是 `NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE`；执行条件和时间窗口以 [STATUS.md](STATUS.md) 为准。
- 当前 runtime release、current work commit 与精确 runtime 标识只从 [STATUS.md](STATUS.md) 获取，本索引不复制其值。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| Gate 治理 workflow | [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md) | 否；定义 checker、lifecycle、evidence 与 release contract |
| Current task evidence | [evidence/gate-w/README.md](evidence/gate-w/README.md) | 否；保存不可覆盖 Attempt evidence |
| GateW active plan | [GATEW_PLAN.md](GATEW_PLAN.md) | 否；定义计划与安全边界 |
| API / Schema / 架构 | [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |

## Historical Evidence

- Gate archive： [../gates/](../gates/)；最近已冻结 Gate 的精确 tag 与 archive 入口从 [STATUS.md](STATUS.md) 获取。
- GateW 不可覆盖的 Attempt 历史统一从 [GateW evidence index](evidence/gate-w/README.md) 访问，包括 [Attempt-09 evidence](evidence/gate-w/NQ-GATEW-ATTEMPT-09-FAILURE-INCIDENT-REVIEW-AND-REMEDIATION-DESIGN.attempt-01.md)。
- General archive： [../archive/](../archive/)。
- Historical evidence 不覆盖 [STATUS.md](STATUS.md)，也不授权新的 runtime、acceptance、freeze、archive 或 tag 操作。

## Current Is Not

- 本入口不判定 accepted/work batch；精确状态只读取 [STATUS.md](STATUS.md)。
- 不是 LIVE、Shadow trading、AI、DH 或 Integration runtime 已启动。
- 不是 RealClient、real provider、private trading adapter 或真实交易已获授权。
- GateW diagnostic、read-only、CI 或本地验证事实均不得推导为远端 permission、账户健康、余额充分或交易授权。
