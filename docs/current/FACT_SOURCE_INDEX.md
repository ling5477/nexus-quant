# Current Fact Source Index

本索引定义 current authority 分层，不复制动态阶段值。

## 1. Current Authority

1. [STATUS.md](STATUS.md)：唯一 machine current authority。
2. Git、代码、测试与 CI：能力与验证事实。
3. [ROADMAP.md](ROADMAP.md)：下一允许动作，不覆盖 STATUS。
4. [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md)：通用 lifecycle/checker 说明，不决定 current Gate。

冲突时输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`；历史材料不得覆盖 current authority。

## 2. Capability Owners

- [API.md](API.md)：已实现 HTTP API 与边界。
- [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 schema/migration。
- [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md)：架构与模块职责。
- [RUNBOOK.md](RUNBOOK.md)：当前运行手册。
- [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md)：前端设计系统参考。

## 3. Evidence Ledgers

- [TESTING.md](TESTING.md)：append-only 验证证据。
- [WORKLOG.md](WORKLOG.md)：append-only 工作证据。

旧条目只表示历史执行，不参与 current stage 判定。

## 4. Agent / Governance

- 根 `AGENTS.md`：仓库级入口。
- `.agents/README.md` 与 `.agents/skills/**`：唯一 active Skill 集合。
- `scripts/docs/agent-workflow-policy.json`：machine routing policy。
- `scripts/docs/governance-workflow-contract.json`：machine lifecycle/authority/evidence/release contract。
- [Repository Audit Bootstrap Charter](../audit/AUDIT_BOOTSTRAP_CHARTER.md)：由 machine policy 声明的全仓审计中立入口。
- [GateAUDIT-0C R2 independent review acceptance evidence](../audit/evidence/GATEAUDIT_0C_R2_INDEPENDENT_REVIEW_ACCEPTANCE.md)：GateAUDIT-0C execution/review evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不属于 current machine authority。
- [GateAUDIT-0C R3 Skill capability completion evidence](../audit/evidence/GATEAUDIT_0C_R3_SKILL_CAPABILITY_COMPLETION.md)：GateAUDIT-0C implementation/capability evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不是 independent review，不属于 current machine authority。
- [GateAUDIT-0C R3 final independent review evidence](../audit/evidence/GATEAUDIT_0C_R3_FINAL_INDEPENDENT_REVIEW_ACCEPTANCE.md)：GateAUDIT-0C R3 final independent review evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不属于 current machine authority。

`.agents/history/**`、`.agents.audit-subject/**`、旧 Skill/checker 自我声明均为 non-authoritative audit/history input。

## 5. Frozen Evidence

- GateY：[../gates/gate-y/README.md](../gates/gate-y/README.md)。
- 其他 frozen Gate：`docs/gates/gate-*`。
- 通用历史：`docs/archive/**` 与 Gate 内 `source/**`。

这些内容只读追溯，不覆盖 `STATUS.md`，不得为 current 收口改写历史正文。
