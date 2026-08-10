# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要，不复制完整 machine authority。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateW：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-w/](../gates/gate-w/)。
- GateX：`IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）；GateX-0A/0B/0C/0D/1/2/3/4B=`ACCEPTED / CI GREEN`（已接受 / CI 已通过），GateX-0E=`AUDITED / IMPLEMENTATION NOT REQUIRED`（已审计 / 无需实施），GateX-4 原 API/UI=`BLOCKED / WAITING FOR SERVER-CONTROLLED ARTIFACT BINDING`（阻断 / 等待服务端受控 artifact 绑定），GateX-4A schema review=`PASS`（通过），GateX-4C=`REVIEW ACCEPTED / READY TO COMMIT`（审查已接受 / 可进入提交前复核）。
- Attempt-13=`COMPLETED / ACCEPTED`; production deployment=`STOPPED`；production soak=`COMPLETED`，worker=`STOPPED`。
- 当前唯一动作是 `NQ-GATEX-4C-COMMIT-AND-PUSH`；GateX-4C security review 已接受但仍为 `UNCOMMITTED / CI NOT RUN`，artifact producer、API/UI 与 GateX-4 仍未完成，exact transition 以 [STATUS.md](STATUS.md) 为准。
- 当前 runtime release、current work commit 与精确 runtime 标识只从 [STATUS.md](STATUS.md) 获取，本索引不复制其值。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| GateX 实施基线 | [GATEX_PLAN.md](GATEX_PLAN.md) | 否；只定义批次、边界与验收，不代表 capability 已实现 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| Gate 治理 workflow | [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md) | 否；定义 checker、lifecycle、evidence 与 release contract |
| Post-tag current evidence | [evidence/gate-w/README.md](evidence/gate-w/README.md) | 否；只保留 current 导航与 closeout evidence |
| GateW strict archive | [../gates/gate-w/README.md](../gates/gate-w/README.md) | 否；已冻结历史证据 |
| API / Schema / 架构 | [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |

## Historical Evidence

- Gate archive： [../gates/](../gates/)；最近已冻结 Gate 的精确 tag 与 archive 入口从 [STATUS.md](STATUS.md) 获取。
- GateW 不可覆盖的 Attempt 历史统一从 [archived task evidence](../gates/gate-w/source/task-evidence/README.md) 访问；PASS、FAIL、BLOCKED、REJECTED 与 remediation 均保留。
- General archive： [../archive/](../archive/)。
- Historical evidence 不覆盖 [STATUS.md](STATUS.md)，也不授权新的 runtime、acceptance、freeze、archive 或 tag 操作。

## Current Is Not

- 本入口不判定 accepted/work batch；精确状态只读取 [STATUS.md](STATUS.md)。
- 不是 LIVE、Shadow trading、AI、DH 或 Integration runtime 已启动。
- 不是 RealClient、real provider、private trading adapter 或真实交易已获授权。
- GateW diagnostic、read-only、CI 或本地验证事实均不得推导为远端 permission、账户健康、余额充分或交易授权。
