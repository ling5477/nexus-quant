# Worklog: DOC-CLEAN + BASELINE-FIX

日期：2026-05-16

## 修改文件清单

- `.env.example`
- `README.md`
- `docker-compose.yml`
- `docs/README.md`
- `docs/DOC_RULES.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/ARCHITECTURE.md`
- `docs/current/MODULES.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/RUNBOOK.md`
- `docs/current/ROADMAP.md`
- `docs/current/PLAN_GATEH.md`
- `docs/current/WORKLOG.md`
- `docs/templates/WORK_ORDER.md`
- `docs/templates/GATE_PLAN.md`
- `docs/templates/CHECKLIST.md`
- `docs/templates/ADR.md`

## 归档文件清单

- `docs/archive/legacy-root-docs/ARCHITECTURE.md`
- `docs/archive/legacy-root-docs/CONTRACTS.md`
- `docs/archive/legacy-root-docs/DB_SCHEMA.md`
- `docs/archive/legacy-root-docs/DECISIONS.md`
- `docs/archive/legacy-root-docs/EVOLUTION_RULES.md`
- `docs/archive/legacy-root-docs/GATE_A_CHECKLIST.md`
- `docs/archive/legacy-root-docs/MODULES.md`
- `docs/archive/legacy-root-docs/NUMERIC_POLICY.md`
- `docs/archive/legacy-root-docs/RECOVERY_RUNBOOK.md`
- `docs/archive/legacy-root-docs/ROADMAP.md`
- `docs/archive/legacy-root-docs/WORK.md`
- `docs/archive/gate-inputs/GATEF_INPUTS.md`
- `docs/archive/gate-inputs/GATEG_INPUTS.md`

## 配置修复清单

- `docker-compose.yml`：PostgreSQL 默认映射修正为 `${NQ_DB_PORT:-5432}:5432`。
- `.env.example`：`NQ_DB_PORT` 默认修正为 `5432`。
- `backend/nq-app/src/main/resources/application-local.yml`：已确认默认连接 `localhost:${NQ_DB_PORT:5432}` 并支持 `NQ_DB_URL` 覆盖。

## 验证命令和结果

- `git status --short`：已执行，工作区包含本次 docs/config 修改与 `git mv` 归档。
- `rg` 检查：已确认 `docker-compose.yml`、`.env.example`、`application-local.yml` 命中 `5432` 默认配置。
- `Test-Path docs/DOC_RULES.md`、`docs/archive/legacy-root-docs`、`docs/archive/gate-inputs`、`docs/templates`：均存在。
- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`。
- `npm ci`：首次因 Node cache 权限/占用失败；提权重跑通过，提示 4 个 `npm audit` 漏洞。
- `npm run build`：通过，Vite 提示 bundle chunk 超过 500 kB。
- `python -m pytest -q`：BASELINE-FIX-2 后通过，`2 passed in 0.01s`。
- `python -m mypy src`：BASELINE-FIX-2 后通过，`Success: no issues found in 8 source files`。
- `python -m ruff check .`：BASELINE-FIX-2 后通过，`All checks passed!`。
- `mvn -f backend/pom.xml -pl nq-app spring-boot:run -Dspring-boot.run.profiles=local`：失败，缺少 `-am` 时无法解析 reactor module 依赖。
- `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local`：通过，后端启动在 `18888`，连接 `jdbc:postgresql://localhost:5432/nexus_quant`。
- `Invoke-RestMethod http://localhost:18888/actuator/health`：通过，返回 `UP`。
- `POST /api/auth/login` + `GET /api/auth/me`：通过，登录用户 `admin`，`/api/auth/me` 返回当前默认账户 alias。
- `npm run test:e2e`：BASELINE-FIX-2 后通过，8 个 Playwright 用例中 5 passed、3 skipped。

## BASELINE-FIX-2 执行记录

### E2E 修复

- 修改 `frontend/tests/e2e/support.ts`：每次 `loginToConsole` 前先通过 `/api/auth/login` 获取 token，再调用 `/api/exchange-accounts/900001/set-default`，把 admin 默认账户固定为 `rc1-admin-default`。
- 修改 `frontend/tests/e2e/account-credential-write-smoke.spec.ts`：先断言默认账户已切到 `rc1-admin-alt`，再模拟用户从 header 下拉显式选择 alt 账户，避免把“默认账户变更”误判成“当前已选账户必须被强制覆盖”。
- 修改 `frontend/playwright.config.ts`：增加 `actionTimeout`、`navigationTimeout`，并保留外部 dev server 模式。
- 新增 `frontend/tests/e2e/run-e2e.mjs` 并修改 `frontend/package.json`：`npm run test:e2e` 现在由 runner 启动 Vite、等待 `4173`、以外部 server 模式执行 Playwright、最后停止 Vite，避免 Windows 下 Playwright 内置 webServer 回收导致命令挂住。

### Python dev 环境修复

- 修改 `research/py/pyproject.toml`：新增 `[project.optional-dependencies].dev`，包含 `pytest`、`mypy`、`ruff`。
- 修改 `research/py/README.md`：补充 `python -m pip install -e ".[dev]"` 和统一验证命令。
- 当前环境中 `python -m pip install -e ".[dev]"` 两次超时；为完成验证，提权执行 `python -m pip install pytest mypy ruff` 并成功安装工具。

### BASELINE-FIX-2 最终验证

- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- `npm run test:e2e`：通过，5 passed、3 skipped。
- `python -m pytest -q`：通过，2 passed。
- `python -m mypy src`：通过。
- `python -m ruff check .`：通过。
- `POST /api/auth/login` + `GET /api/auth/me`：通过，默认账户为 `rc1-admin-default / 900001`。

## 未完成项

- GateH 正式功能开发未启动。
- 虚拟币量化 V1 未完成。
- AI 自动交易未进入开发。
- `npm audit` 存在 4 个漏洞提示，本任务未做依赖升级或修复。
- Vite chunk > 500 kB 警告仍存在，本任务未处理构建体积。
- E2E 中 3 个用例因当前环境缺少对应预置数据或环境变量而 skip。

## 下一步进入 GateH-PLAN 的条件

- 文档入口与当前状态无冲突。
- PostgreSQL `5432` 本地基线稳定。
- 后端、前端、Python 验证结果已如实记录。
- BASELINE-FIX-2 已修复 E2E 默认账户不幂等和 Python 工具缺失问题。
- GateH scope、API、DB、前端、测试矩阵、回滚边界形成正式计划。

## GateH-PLAN 执行记录

日期：2026-05-17

### 本轮修改文件

- `docs/current/PLAN_GATEH.md`
- `docs/current/GATEH_API_PLAN.md`
- `docs/current/GATEH_DB_PLAN.md`
- `docs/current/GATEH_FRONTEND_PLAN.md`
- `docs/current/GATEH_TEST_PLAN.md`
- `docs/current/GATEH_WORK_ORDER.md`
- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/WORKLOG.md`

### 本轮新增文件

- `docs/current/GATEH_API_PLAN.md`
- `docs/current/GATEH_DB_PLAN.md`
- `docs/current/GATEH_FRONTEND_PLAN.md`
- `docs/current/GATEH_TEST_PLAN.md`
- `docs/current/GATEH_WORK_ORDER.md`

### 本轮执行内容

- 重写 GateH 总计划，明确 GateH 背景、目标、不做范围、GateH-1/2/3 拆分、验收标准、规划入口、风险与回滚策略。
- 新增 API 规划文档，覆盖 Trading Workspace、Instrument、Marketdata Bar、Ingestion Job、Dataset、Backtest Dataset Binding。
- 新增 DB 规划文档，明确 `instrument_catalog`、`marketdata_bars`、ingestion jobs/runs、datasets、backtest dataset binding 的规划边界。
- 新增前端规划文档，规划 `/trading`、`/instruments`、`/marketdata`、`/marketdata/ingestion`、`/backtests`。
- 新增测试规划文档，保留当前验证基线并规划 GateH E2E 矩阵。
- 新增 GateH work order 草案，拆分 GateH-1-WO、GateH-2-WO、GateH-3-WO。
- 同步 `STATUS.md` 和 `ROADMAP.md`，将当前状态更新为 `GateH-PLAN`，未将 GateH 写成 completed。

### 本轮未执行内容

- 未开发 GateH 功能代码。
- 未新增 API 实现。
- 未新增 DB migration。
- 未新增前端页面实现。
- 未新增历史行情抓取代码。
- 未接入 AI。
- 未修改交易、策略、账户业务逻辑。
- 未处理 `npm audit`。
- 未处理 Vite chunk 警告。

### 验证记录

- 本轮只修改文档，未重新执行全量 `mvn`、`npm`、Python 测试。
- 沿用当前验证基线：后端 `mvn test` 通过，前端 `npm run build` 通过，E2E 5 passed / 3 skipped，Python pytest/mypy/ruff 通过。
- 已规划执行文件存在性、状态文案和禁止项检查。

### 下一步进入 GateH-1-WO 的条件

- GateH-PLAN 文档完成审阅。
- 确认 GateH-1 只做交易工作台正式化，不夹带 GateH-2/3 实现。
- 为 GateH-1 单独开 work order，并按 API、DB、前端、测试矩阵拆解可验收任务。
- GateH-1 开工前再次确认不接入 AI、不新增历史行情抓取、不修改策略核心逻辑。

## GateH-1-WO 执行记录

日期：2026-05-17

### 本轮范围

- 正式化 `/trading` 交易工作台。
- 增加 `GET /api/trading/orders` 订单列表查询。
- 订单详情继续展示订单、最新成交、账户余额快照和持仓快照。
- 强化账户上下文校验：交易工作台列表查询必须使用已登记的 exchange account。
- 显示 SIM / LIVE 边界。
- 下单前展示风控摘要与“服务端风控不可绕过”的明确状态。
- `/trade-validation` 保持兼容，并在页面内标记为过渡入口。
- 更新 E2E smoke。

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/OrderView.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/OrderListResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/TradingVerificationController.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/query/OrderQueryView.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/query/TradingQueryFacade.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/query/JdbcTradingQueryFacade.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/TradingVerificationControllerLocalTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/AuthSecurityWebMvcTest.java`
- `frontend/src/api/trading-workbench.ts`
- `frontend/src/api/query-keys.ts`
- `frontend/src/hooks/useTradingWorkbench.ts`
- `frontend/src/types/trading-workbench.ts`
- `frontend/src/pages/trading/TradingWorkbenchPage.tsx`
- `frontend/src/router/routes.tsx`
- `frontend/tests/e2e/account-context-smoke.spec.ts`
- `frontend/tests/e2e/account-credential-write-smoke.spec.ts`
- `frontend/tests/e2e/trading-workbench-query.spec.ts`
- `docs/current/API.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 未做范围

- 未开发 GateH-2 历史行情接入。
- 未新增 marketdata ingestion。
- 未开发 GateH-3 dataset 绑定。
- 未新增 DB migration。
- 未接入 AI。
- 未新增美股/A 股、合约全量、高频、复杂因子平台。
- 未修改策略核心逻辑。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`。
- `npm run test:e2e`：通过，7 passed、3 skipped。

### E2E skipped 原因

- `research-detail`：当前环境缺少对应预置 detail 条件，沿用既有 skip。
- `strategies-detail`：当前环境缺少对应预置 detail 条件，沿用既有 skip。
- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，跳过真实订单详情查询链路。

### 下一步进入 GateH-2-WO 的条件

- GateH-1 变更完成审查并提交。
- 若需要更强验收，配置 `E2E_TRADE_ORDER_ID` 后补跑真实订单详情 E2E。
- GateH-2 开工前再次确认范围只包含 OKX / Binance SPOT 历史 K 线接入，不夹带 dataset 绑定或 AI。

## GateH-2-WO 执行记录

日期：2026-05-17

### 本轮范围

- 实现 OKX / Binance SPOT 历史 OHLCV K 线接入最小闭环。
- 增强 `marketdata_bars`，新增 `market_type`、`quote_volume`、`trade_count`、`quality_status`、`raw_payload_json`。
- 新增 `marketdata_ingestion_jobs` 与 `marketdata_ingestion_runs`。
- 新增接入任务创建、列表、详情、运行记录与 run-once API。
- 增强 `/marketdata` 页面，展示 K 线查询、接入任务、运行结果。
- 新增 marketdata E2E smoke。

### 修改文件

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/BarInterval.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/HistoricalBar.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/HistoricalMarketDataQuery.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataBarUpsertStats.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/MarketdataBarRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/MarketdataIngestionService.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiExceptionHandler.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataBarRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcHistoricalMarketDataPort.java`
- `frontend/src/api/marketdata.ts`
- `frontend/src/types/marketdata.ts`
- `frontend/src/pages/marketdata/MarketdataPage.tsx`
- `frontend/tests/e2e/marketdata-bars-query-smoke.spec.ts`
- `frontend/tests/e2e/marketdata-ingestion-smoke.spec.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/STATUS.md`
- `docs/current/WORKLOG.md`

### 新增文件

- `backend/nq-infra/src/main/resources/db/migration/V16__gate_h2_marketdata_ingestion.sql`
- `backend/nq-infra/src/main/resources/db/migration/V17__gate_h2_ingestion_created_by_width.sql`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataIngestionStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataIngestionJob.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataIngestionRun.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/command/CreateMarketdataIngestionJobCommand.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/HistoricalKlineProvider.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/MarketdataIngestionJobRepository.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/HistoricalKlineRequest.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/HistoricalKlineBar.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/service/HistoricalKlineAdapter.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/service/HistoricalKlineAdapterException.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java`
- `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/adapter/AdapterHistoricalKlineProvider.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataIngestionJobRepository.java`

### DB/migration 说明

- `V16` 新增 GateH-2 marketdata ingestion 结构，并为新增表和新增字段补齐 PostgreSQL COMMENT。
- `V17` 将 `marketdata_ingestion_jobs.created_by` 扩展为 `VARCHAR(512)`，兼容 Spring Security principal 审计名，并补充字段 COMMENT。
- `marketdata_bars` 唯一约束升级为 `exchange_code + market_type + symbol + interval + open_time`，用于幂等 upsert。
- 新增索引用于 bars 范围查询、job 列表和 run 列表。

### 未做范围

- 未进入 GateH-3。
- 未新增 dataset/backtest 绑定。
- 未接入 AI。
- 未新增 AI 模块或 AI 自动交易接口。
- 未接合约、资金费率、深度、逐笔成交、链上数据、新闻资讯。
- 未新增美股/A 股适配。
- 未修改交易核心状态机或策略核心逻辑。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`。
- `npm run test:e2e`：通过，9 passed、3 skipped。
- Python 验证本轮未重新执行；本轮未修改 Python，沿用 BASELINE-FIX 已通过基线。

### E2E 说明

- 新增 `marketdata-bars-query-smoke`。
- 新增 `marketdata-ingestion-smoke`。
- E2E 不依赖外网交易所稳定性；目标是验证页面、API、job/run 状态查询闭环。

### 剩余风险

- 真实 OKX/Binance 大范围历史数据回填未在本轮执行。
- 当前 run-once 在本地网络条件下可能返回空 bars，但会记录明确统计。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning 仍存在，本轮不处理。
- `npm audit` 与 Vite chunk 体积警告仍按既有风险记录，未在 GateH-2 处理。

### 下一步进入 GateH-3-WO 的条件

- GateH-2 变更完成审查并提交。
- GateH-3 只能做行情数据质量、dataset、backtest config 绑定与结果追溯。
- GateH-3 不得夹带 AI、交易核心重构、策略核心逻辑、美股/A 股适配或合约全量接入。

## GateH-3-WO 执行记录

日期：2026-05-17

### 本轮范围

- 新增 marketdata dataset 定义。
- 新增 dataset 覆盖范围与质量统计。
- 新增 backtest config 绑定 dataset。
- 新增 backtest run 创建时的 dataset 快照。
- 前端增强 `/marketdata` dataset 区域和 `/backtests` dataset 绑定入口。
- 新增 GateH-3 E2E smoke。

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/BacktestConfigController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestConfigResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestRunResponse.java`
- `backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/application/backtest/BacktestExecutionService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/**`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/**`
- `frontend/src/api/backtests.ts`
- `frontend/src/api/marketdata.ts`
- `frontend/src/hooks/useBacktestsListQuery.ts`
- `frontend/src/pages/backtests/BacktestsPage.tsx`
- `frontend/src/pages/marketdata/MarketdataPage.tsx`
- `frontend/src/types/backtests.ts`
- `frontend/src/types/marketdata.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/STATUS.md`
- `docs/current/WORKLOG.md`

### 新增文件

- `backend/nq-infra/src/main/resources/db/migration/V18__gate_h3_marketdata_dataset_binding.sql`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/MarketdataDatasetService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/command/CreateMarketdataDatasetCommand.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataDataset.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataDatasetCoverage.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataDatasetStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataQualityStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/port/MarketdataDatasetRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataDatasetRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/dto/CreateMarketdataDatasetRequest.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/dto/MarketdataDatasetResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestDatasetBindingRequestBody.java`
- `frontend/tests/e2e/marketdata-dataset-smoke.spec.ts`
- `frontend/tests/e2e/backtest-dataset-binding-smoke.spec.ts`

### DB/migration 说明

- `V18` 新增 `marketdata_datasets` 和 `marketdata_dataset_coverage`。
- `V18` 给 `backtest_configs` 新增 `dataset_id` 和 `dataset_snapshot_json`。
- `V18` 给 `backtest_runs` 新增 `dataset_snapshot_json`。
- 所有新增表均有 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均有 PostgreSQL `COMMENT ON COLUMN`。
- `marketdata_datasets` 唯一约束用于避免同名同范围重复 dataset。
- `backtest_runs.dataset_snapshot_json` 在 run 创建时从 config 固化，保证历史 run 可追溯。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本为 `18`。
- `npm run test:e2e`：通过，10 passed、4 skipped。
- Python 验证本轮未重新执行；本轮未修改 Python，沿用 BASELINE-FIX 已通过基线。

### E2E 说明

- 新增 `marketdata-dataset-smoke`，通过。
- 新增 `backtest-dataset-binding-smoke`，当前本地库没有可绑定 backtest config 种子，按明确原因 skip。
- 绑定 API 已通过后端 controller 测试覆盖。

### 未做范围

- 未接入 AI。
- 未新增 AI 模块或 AI 自动交易接口。
- 未新增合约全量、资金费率、深度、逐笔成交、链上数据、新闻资讯。
- 未新增美股/A 股适配。
- 未修改交易核心状态机。
- 未修改策略核心逻辑。
- 未修改回测引擎核心算法。

### 剩余风险

- 当前 E2E 绑定 UI 链路依赖本地存在 backtest config 种子；当前种子为空，因此该用例 skip。
- dataset 质量统计第一版只做 expected/actual/missing/invalid/duplicate 聚合，不做复杂连续缺口区间明细。
- `npm audit` 与 Vite chunk 体积警告仍按既有风险记录，未在 GateH-3 处理。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning 仍存在，本轮不处理。

### 下一步进入 GateI-PLAN 的条件

- GateH-3 变更完成审查并提交。
- GateI-PLAN 只能规划虚拟币量化 V1 完整闭环。
- GateI-PLAN 不得夹带 AI 接入；AI 只能在虚拟币 V1 和 Paper Trading 稳定后进入后续 Gate。

## GateI-PLAN 执行记录

日期：2026-05-18

### 本轮范围

- 只做 GateI 规划文档。
- 规划虚拟币量化 V1 完整闭环。
- 明确 GateI-1 / GateI-2 / GateI-3 / GateI-4 拆分。
- 同步当前状态、路线、API、DB、测试与工作日志入口。

### 本轮新增文件

- `docs/current/PLAN_GATEI.md`
- `docs/current/GATEI_API_PLAN.md`
- `docs/current/GATEI_DB_PLAN.md`
- `docs/current/GATEI_FRONTEND_PLAN.md`
- `docs/current/GATEI_TEST_PLAN.md`
- `docs/current/GATEI_WORK_ORDER.md`

### 本轮修改文件

- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

### 本轮执行内容

- 新增 GateI 总计划，明确背景、目标、不做范围、四个子 Gate、规划入口、风险、回滚策略和进入 GateJ 条件。
- 新增 GateI API 规划，覆盖 Strategy Version、Publish Version、Backtest Config Enhanced、Evaluation Report、Paper Trading Run、Risk Result、Equity Curve、Position Curve、Trade Replay、Emergency Stop。
- 新增 GateI DB 规划，覆盖策略版本、发布版本、回测增强、评估报告、Paper run、风控结果、资金曲线、持仓曲线、复盘和异常停机事件。
- 新增 GateI 前端规划，覆盖 `/strategies`、`/publishes`、`/backtests`、`/evaluations`、`/paper-trading`、`/risk`、`/portfolio/equity-curve`、`/portfolio/position-curve`、`/replay`、`/emergency-stop`。
- 新增 GateI 测试规划，规划后端单元测试、集成测试、API smoke、前端 build、E2E 矩阵、本地启动、migration 验证和冻结标准。
- 新增 GateI work order 草案，拆分 GateI-1-WO 到 GateI-4-WO。
- 同步 `STATUS.md` 和 `ROADMAP.md`，写清 GateH completed、当前执行 GateI-PLAN、AI 仍未开始。

### 本轮未执行内容

- 未开发 GateI 功能代码。
- 未新增 API 实现。
- 未新增 DB migration。
- 未新增前端页面实现。
- 未接入 AI。
- 未新增 AI 模块、AI 信号、AI Paper Trading 或 AI 自动交易。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心逻辑。
- 未修改回测核心算法。
- 未处理 `npm audit`。
- 未处理 Vite chunk 警告。

### 验证记录

- 本轮只修改文档，未重新执行全量 `mvn`、`npm`、Python 测试。
- 已执行 `git status --short --branch`。
- 已检查 GateI 六份规划文档存在。
- 已检查 `STATUS.md` 写清 GateH completed、当前执行 GateI-PLAN、AI 仍未开始。
- 已检查本轮变更未新增业务代码、migration、API 实现或前端页面实现。

### 下一步进入 GateI-1-WO 的条件

- GateI-PLAN 文档完成审查。
- GateI-1-WO 单独开工，并只做策略版本与发布链路正式化。
- GateI-1-WO 不得夹带 GateI-2/3/4 实现。
- GateI-1-WO 不得接入 AI，不得修改策略核心算法，不得新增美股/A 股或合约全量能力。
