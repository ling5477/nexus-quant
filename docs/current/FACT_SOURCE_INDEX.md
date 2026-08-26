# Current Fact Source Index

本索引定义 authority 分层，不复制独立 current Gate 判定。当前阶段必须解析 [STATUS.md](STATUS.md) 顶部唯一的 `nq-current-authority` 区块；任何冲突必须输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。

## 1. NQ Current Authority

1. [STATUS.md](STATUS.md)：唯一阶段状态 authority。
2. [ROADMAP.md](ROADMAP.md)：只定义下一允许动作，不得覆盖 STATUS。
3. [README.md](README.md) 与 root `README.md`：入口、短摘要和 archive pointer。
4. [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md)：checker、lifecycle、evidence 与 release contract；不决定 current Gate。

## 2. NQ Capability Authority

- [API.md](API.md)：已实现 HTTP API 能力与边界。
- [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema。
- [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md)：当前架构与模块职责。
- [RUNBOOK.md](RUNBOOK.md)：当前运行手册。
- [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md)：当前设计系统参考。

能力文档与阶段状态冲突时，先以代码和实际验证确定能力事实，再以 `STATUS.md` 判定 current Gate。

## 3. Evidence Ledger

- [TESTING.md](TESTING.md)：append-only validation evidence ledger。
- [WORKLOG.md](WORKLOG.md)：append-only work evidence ledger。

历史记录中的旧路径与旧状态不参与 current 阶段判定，也不得被重写为全部首轮通过。

## 4. NQ-DH Integration Boundary

- 本仓库只保存 NQ 侧 contract/mock/test-support 与 no-real boundary。
- Integration runtime 状态只读取 `STATUS.md`；NQ-only 任务不声明 DH current authority。
- Integration 历史文档不能推导为 real HTTP、real provider、LIVE 或交易授权。

## 5. Gate Archive

- GateY strict archive candidate：[../gates/gate-y/README.md](../gates/gate-y/README.md)。它保存 GateY plan、两份 work order、全部 PASS/FAIL/BLOCKED/retry/remediation task evidence、V43～V46、deployment 与 minimal live pilot 证据；pre-tag 阶段不决定 current status。
- GateX durable archive：[../gates/gate-x/README.md](../gates/gate-x/README.md)，tag=`nq-gatex-freeze`。
- GateW durable archive：[../gates/gate-w/README.md](../gates/gate-w/README.md)，tag=`nq-gatew-freeze`。
- 其他已完成 Gate：`docs/gates/gate-*`。

`docs/gates/**` 与 `docs/archive/**` 都是 historical evidence，不覆盖 `docs/current/STATUS.md`。GateY `source/task-evidence/**` 是 approved non-role evidence，不参与 archive role 计数。

## 6. Historical Evidence

- `docs/archive/**`：通用历史归档。
- `docs/gates/*/source/**`：从 current 迁出的 durable process/task evidence。
- GateY archive 保留全部失败、阻断、retry、remediation、credential correction、trusted bootstrap、release reproducibility、operator authority、lease recovery、legacy bridge、真实订单与 reconciliation 历史。

## 7. Allowed Residual

- `GATEW_PLAN.md`、`GATEV_PLAN.md`：历史 compatibility residual；后续只在明确授权 archive move batch 中处理。
- `TESTING.md`、`WORKLOG.md` 中的旧链接属于 append-only evidence；不得据此改变 current Gate。
- `ORDER_VENUE_IDENTITY_MODEL_CONSISTENCY_RESIDUAL`：后续全仓审计检查 Order/ExecutionReceipt/Trade venue identity ownership；本 freeze 不修改生产事实或代码。

## 8. Current cleanup result

GateY process-oriented PLAN / WORK_ORDER / task evidence 已移入 `docs/gates/gate-y/`；`docs/current` 只保留 current authority、capability docs、evidence ledgers与下一路线。GateY archive 不依赖 current 历史 process copies 作为核心证据。
