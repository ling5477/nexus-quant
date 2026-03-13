# GateD SOURCES
# GateD 权威依据索引

> 本文件用于维护 GateD 的实现依据与追溯关系。  
> 当前按 **current source / top-level navigation / archive / code / external** 五层边界整理，避免历史文档与当前事实源混淆。

## 1. Current Source of Truth

以下文档是 GateD 当前实施与验收的唯一事实来源：

- `AGENTS.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/gates/gate-d/README.md`
- `docs/gates/gate-d/GATE_D_CHECKLIST.md`
- `docs/gates/gate-d/ARCHITECTURE.md`
- `docs/gates/gate-d/CONTRACTS.md`
- `docs/gates/gate-d/MODULES.md`
- `docs/gates/gate-d/DB_SCHEMA.md`
- `docs/gates/gate-d/STATE_MACHINE.md`
- `docs/gates/gate-d/RISK_RULES.md`
- `docs/gates/gate-d/COMPENSATION_SYNC.md`
- `docs/gates/gate-d/TEST_CASES.md`
- `docs/gates/gate-d/DECISIONS.md`
- `docs/gates/gate-d/EVOLUTION_RULES.md`
- `docs/gates/gate-d/NUMERIC_POLICY.md`
- `docs/gates/gate-d/PR_SPLIT_PLAN.md`
- `docs/gates/gate-d/RECOVERY_RUNBOOK.md`
- `docs/gates/gate-d/WORK.md`

## 2. Top-Level Navigation

以下文档保留顶层导航摘要角色，用于快速定位，不单独充当事实来源：

- `docs/README.md`
- `docs/ARCHITECTURE.md`
- `docs/MODULES.md`

> 若 top-level navigation 与 current source of truth 冲突，以后者为准。

## 3. Archive Reference

以下内容属于历史留档或冻结快照，只作追溯参考，不代表 GateD 当前事实：

- `docs/CONTRACTS.md`
- `docs/DB_SCHEMA.md`
- `docs/DECISIONS.md`
- `docs/EVOLUTION_RULES.md`
- `docs/GATE_A_CHECKLIST.md`
- `docs/NUMERIC_POLICY.md`
- `docs/RECOVERY_RUNBOOK.md`
- `docs/ROADMAP.md`
- `docs/WORK.md`
- `docs/gates/gate-a/**`
- `docs/gates/gate-b/**`
- `docs/gates/gate-c/**`

## 4. Code and Script Evidence

以下代码与脚本用于核对 GateD 当前实现现状：

- `backend/nq-core/**`
- `backend/nq-risk/**`
- `backend/nq-adapter-api/**`
- `backend/nq-adapter-okx/**`
- `backend/nq-adapter-binance/**`
- `backend/nq-scheduler/**`
- `backend/nq-ledger/**`
- `backend/nq-app/**`
- `backend/nq-infra/**`
- `backend/nq-observability/**`
- `backend/nq-api/**`
- `scripts/gated_okx_dome_verify.ps1`

## 5. External Sources

外部官方资料继续作为补充依据，在具体 PR 中补齐链接：

- OKX Spot REST / WS 官方文档
- Binance Spot REST / WS 官方文档
- Spring Boot / Flyway / PostgreSQL 官方文档
