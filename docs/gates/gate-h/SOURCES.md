# GateH SOURCES

GateH-PLAN 的判断依据来自以下已核对资料与代码事实。

## 1. 当前入口与冻结卷宗

- `README.md`
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/MODULES.md`
- `docs/current/GATEG_INPUTS.md`
- `docs/gates/gate-g/README.md`
- `docs/gates/gate-g/CONTRACTS.md`
- `docs/gates/gate-g/TEST_CASES.md`
- `docs/gates/gate-g/WORK.md`

## 2. 前端当前实现

- `frontend/src/pages/strategies/StrategiesPage.tsx`
- `frontend/src/pages/schedules/SchedulesPage.tsx`
- `frontend/src/pages/runs/RunsPage.tsx`
- `frontend/src/pages/research/ResearchPage.tsx`
- `frontend/src/pages/backtests/BacktestsPage.tsx`
- `frontend/src/pages/evaluations/EvaluationsPage.tsx`
- `frontend/src/pages/publishes/PublishesPage.tsx`
- `frontend/src/pages/trade-validation/TradeValidationPage.tsx`
- `frontend/src/api/strategies.ts`
- `frontend/src/api/schedules.ts`
- `frontend/src/api/runs.ts`
- `frontend/src/api/research.ts`
- `frontend/src/api/backtests.ts`
- `frontend/src/api/evaluations.ts`
- `frontend/src/api/publishes.ts`
- `frontend/src/api/trade-validation.ts`

## 3. E2E 与测试数据线索

- `frontend/tests/e2e/support.ts`
- `frontend/tests/e2e/strategies-detail.spec.ts`
- `frontend/tests/e2e/research-detail.spec.ts`
- `frontend/tests/e2e/trade-validation-query.spec.ts`
- `frontend/playwright.config.ts`
- `frontend/.env.example`

## 4. 后端已存在的正式接口

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/StrategyDefinitionController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/StrategyScheduleController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/BacktestRunController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/TradingVerificationController.java`

## 5. 本批原则

- 只基于当前仓库事实做规划
- 不凭空发明不存在的能力
- 不把 GateH-PLAN 扩成开发批
