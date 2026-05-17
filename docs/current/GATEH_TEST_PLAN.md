# GateH Test Plan

本文件只规划 GateH 测试，不新增测试代码。

## 当前验证基线

- 后端：`mvn -f backend/pom.xml test` 已通过。
- 前端 build：`npm run build` 已通过。
- E2E：`npm run test:e2e` 已通过，结果为 5 passed / 3 skipped。
- Python：`python -m pytest -q`、`python -m mypy src`、`python -m ruff check .` 已通过。

## 后端单元测试计划

- Trading workspace service：账户上下文、SIM / LIVE 边界、订单查询参数校验。
- Instrument service：交易对同步、唯一键、状态映射。
- Marketdata bar service：K 线入库幂等、质量状态、时间范围校验。
- Ingestion job service：任务创建幂等、run once 参数校验、状态流转。
- Dataset service：数据集创建、质量策略、范围校验。
- Backtest config binding：dataset 绑定、权限校验、重复绑定。

## 后端集成测试计划

- Controller -> Service -> Repository -> PostgreSQL 闭环。
- Flyway migration 在空库可执行。
- `marketdata_bars` 唯一约束阻止重复 K 线。
- `marketdata_ingestion_runs` 正确记录 inserted、skipped、conflict、gap。
- backtest config 能绑定 dataset 并被 run 追溯。

## API smoke 测试计划

- `GET /api/trading/orders`
- `GET /api/trading/orders/{orderId}`
- `GET /api/instruments`
- `POST /api/instruments/sync`
- `GET /api/marketdata/bars`
- `POST /api/marketdata/ingestion-jobs`
- `GET /api/marketdata/ingestion-jobs`
- `GET /api/marketdata/ingestion-jobs/{jobId}`
- `POST /api/marketdata/ingestion-jobs/{jobId}/run-once`
- `GET /api/marketdata/datasets`
- `POST /api/marketdata/datasets`
- `PATCH /api/backtest-configs/{configId}/dataset`

## 前端 build 测试

命令：

```powershell
Set-Location frontend
npm run build
```

验收：

- TypeScript 编译通过。
- Vite build 通过。
- 已知 Vite chunk > 500 kB 警告继续作为独立风险记录，不在 GateH-PLAN 处理。

## E2E 测试矩阵

- `trading-workspace-smoke`：登录、账户上下文、订单列表、订单详情入口、SIM / LIVE 展示。
- `instruments-query-smoke`：交易对目录筛选、表格字段、空态、错误态入口。
- `marketdata-bars-query-smoke`：K 线查询、时间范围、质量状态、空态。
- `marketdata-ingestion-smoke`：接入任务列表、创建入口、run once、状态展示。
- `backtest-dataset-binding-smoke`：dataset 查询、绑定、重复绑定、绑定结果展示。

命令：

```powershell
Set-Location frontend
npm run test:e2e
```

## Python 是否参与 GateH 验证

Python research 工具链继续作为回测与研究基线验证项参与 GateH 冻结：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

GateH 不在 Python 中新增 AI 自动交易逻辑。

## 本地启动验证

```powershell
docker compose up -d postgres
mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local
Invoke-RestMethod http://localhost:18888/actuator/health
```

补充验证：

- `POST /api/auth/login`
- `GET /api/auth/me`
- GateH API smoke 链路

## 数据库 migration 验证

GateH 正式开发时需要执行：

```powershell
mvn -f backend/pom.xml test
```

验收：

- 空库 migration 成功。
- PostgreSQL `5432` 本地基线可启动。
- 新增表和字段有唯一约束、索引和回滚说明。

## 回归测试命令

```powershell
mvn -f backend/pom.xml test
Set-Location frontend
npm run build
npm run test:e2e
Set-Location ../research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

## 冻结标准

- 后端单元测试和集成测试通过。
- API smoke 通过。
- 前端 build 通过。
- E2E 矩阵通过。
- Python pytest/mypy/ruff 通过。
- 本地 PostgreSQL `5432` 启动和后端 local profile 验证通过。
- GateH 文档与代码状态一致。
- 未执行验证不得写成通过。
