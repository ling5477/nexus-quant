# GateF SOURCES
# GateF 依据索引

本文档记录 GateF-DOC-1 结论依赖的仓库事实。

---

## 1. 当前入口文档

- `README.md`
- `AGENTS.md`
- `docs/README.md`
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/WORK_TEMPLATE.md`
- `docs/current/GATEF_INPUTS.md`

---

## 2. 最近冻结 Gate 参考

- `docs/gates/gate-e/README.md`
- `docs/gates/gate-e/ARCHITECTURE.md`
- `docs/gates/gate-e/MODULES.md`
- `docs/gates/gate-e/CONTRACTS.md`
- `docs/gates/gate-e/DB_SCHEMA.md`
- `docs/gates/gate-e/STATE_MACHINE.md`
- `docs/gates/gate-e/TEST_CASES.md`
- `docs/gates/gate-e/PR_SPLIT_PLAN.md`
- `docs/gates/gate-e/DECISIONS.md`
- `docs/gates/gate-e/WORK.md`
- `docs/gates/gate-e/GATE_E_CHECKLIST.md`
- `docs/gates/gate-e/SOURCES.md`

---

## 3. 代码与 schema 证据

- `backend/nq-infra/src/main/resources/db/migration/V5__gate_e_schema_contract_alignment.sql`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/repository/JdbcStrategyDefinitionRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/repository/JdbcStrategyRunRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/repository/JdbcOrderRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/service/StrategyRunQueryService.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/AdapterResultCategory.java`

---

## 4. 结论

- 当前仓库没有 GateF 实装代码
- GateF-DOC-1 的所有结论都建立在 GateE 冻结资产与当前模块结构之上
