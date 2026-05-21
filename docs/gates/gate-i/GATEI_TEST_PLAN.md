# GateI Test Plan

本文件规划 GateI 测试矩阵。本轮只写规划，不执行全量测试，不新增测试代码。

## 当前基线

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- Python `python -m pytest -q`、`python -m mypy src`、`python -m ruff check .` 已通过。
- 本地 PostgreSQL 默认端口固定为 `5432`。

## 后端单元测试计划

- Strategy version domain：版本状态、冻结、归档、参数快照校验。
- Publish version service：发布前置条件、幂等创建、状态流转。
- Backtest config enhanced service：dataset、strategy version、parameter snapshot 绑定。
- Evaluation metrics service：收益率、最大回撤、胜率、盈亏比、交易次数。
- Paper Trading run service：创建、启动、暂停、停止状态流转。
- Risk result service：风控结果写入、查询、幂等。
- Emergency stop service：触发、重复触发、解除、状态校验。

## 后端集成测试计划

- Flyway migration 从空库执行到 GateI 目标版本。
- Strategy version -> publish version -> backtest config -> backtest run -> eval report 链路。
- Publish version -> Paper Trading run -> paper order/trade -> risk result 链路。
- Equity curve 与 position curve snapshot 写入和查询。
- Trade replay record 聚合查询。
- Emergency stop active 时阻止 Paper run start 或 order generation。

## API smoke 测试计划

- `GET /api/strategies/{strategyId}/versions`
- `POST /api/strategies/{strategyId}/versions`
- `POST /api/strategy-versions/{versionId}/freeze`
- `GET /api/publishes`
- `POST /api/publishes`
- `GET /api/backtest-configs`
- `PATCH /api/backtest-configs/{configId}/strategy-version`
- `GET /api/evaluations/reports`
- `GET /api/evaluations/reports/{reportId}`
- `POST /api/paper-trading/runs`
- `POST /api/paper-trading/runs/{runId}/start`
- `GET /api/risk/results`
- `GET /api/portfolio/equity-curve`
- `GET /api/portfolio/position-curve`
- `GET /api/replay/trades/{tradeId}`
- `POST /api/emergency-stop/events`
- `POST /api/emergency-stop/events/{eventId}/resolve`

## 前端 build 测试

统一命令：

```powershell
Set-Location frontend
npm run build
```

成功标准：

- TypeScript 编译通过。
- Vite build 通过。
- 既有 Vite chunk > 500 kB 警告继续记录为风险，不在 GateI-PLAN 中处理。

## E2E 测试矩阵

GateI 至少规划以下 E2E：

- `strategy-version-smoke`：策略版本页面打开、创建版本、冻结版本、查看快照。
- `publish-version-smoke`：发布版本页面打开、创建发布、查看发布状态。
- `backtest-config-enhanced-smoke`：回测配置绑定 dataset、strategy version 和参数快照。
- `evaluation-report-smoke`：评估报告页面查看核心指标和输入快照。
- `paper-trading-run-smoke`：创建 Paper run、启动、查看状态。
- `risk-result-smoke`：查看风控结果列表和详情。
- `equity-curve-smoke`：查看资金曲线空态/数据态。
- `position-curve-smoke`：查看持仓曲线空态/数据态。
- `trade-replay-smoke`：查看单笔交易复盘链路。
- `emergency-stop-smoke`：触发并解除 Paper scope emergency stop。

E2E 要求：

- 不依赖外网交易所。
- 不依赖 AI。
- 不直接操作 LIVE。
- 对必要种子数据提供 deterministic fixture。
- skipped 必须有明确原因，不能把 skipped 写成通过。

## 本地启动验证

后端 local profile：

```powershell
mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:18888/actuator/health
```

成功标准：

- Flyway migration 成功。
- `/actuator/health` 返回 `UP`。
- `POST /api/auth/login` 和 `GET /api/auth/me` 可用。
- Paper run、risk、portfolio、replay、emergency stop smoke API 可用。

## 数据库 migration 验证

GateI 后续实现时必须验证：

- 从空库执行全部 migration 成功。
- 从 GateH 当前版本升级到 GateI 目标版本成功。
- 新增表和字段均有 COMMENT。
- 唯一约束和索引存在。
- JSONB 默认值和可空性符合设计。
- 不修改历史 migration。

## 回归测试命令

后端：

```powershell
mvn -f backend/pom.xml test
```

前端：

```powershell
Set-Location frontend
npm run build
npm run test:e2e
```

Python：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

## GateI 冻结标准

- GateI-1 / GateI-2 / GateI-3 / GateI-4 work order 全部完成。
- 后端全量测试通过。
- 前端 build 通过。
- E2E 主链通过，skipped 均有明确且不影响主链的原因。
- Python 验证未被破坏。
- DB migration 注释、约束、索引检查通过。
- 文档同步 API、DB、TESTING、WORKLOG、STATUS。
- GateI 冻结前不得接入 AI。
