# GateG SOURCES
# GateG 依据索引

本文档记录 GateG-DOC-1 结论依赖的仓库事实。

---

## 1. 当前入口文档

- `README.md`
- `AGENTS.md`
- `docs/README.md`
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/MODULES.md`
- `docs/current/WORK_TEMPLATE.md`
- `docs/current/GATEG_INPUTS.md`

---

## 2. 最近冻结 Gate 参考

- `docs/gates/gate-f/README.md`
- `docs/gates/gate-f/GATE_F_CHECKLIST.md`
- `docs/gates/gate-f/PR_SPLIT_PLAN.md`
- `docs/gates/gate-f/MODULES.md`
- `docs/gates/gate-f/CONTRACTS.md`
- `docs/gates/gate-f/TEST_CASES.md`
- `docs/gates/gate-f/WORK.md`

---

## 3. 代码与目录事实

- `frontend/package.json`
- `frontend/package-lock.json`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/AuthController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/StrategyDefinitionController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/StrategyScheduleController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/StrategyRunController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ResearchConfigController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/BacktestConfigController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/BacktestRunController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/TradingVerificationController.java`

---

## 4. 结论

- 当前仓库已经具备 GateG 首批联调所需后端基础
- 当前缺的是正式前端工程与关键链路回归
- GateG 不以前置数据库大改为条件
