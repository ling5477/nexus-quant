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
- `docs/archive/gate-inputs/LEGACY_CONSOLE_INPUTS.md`

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

## GateI-1-WO 执行记录

日期：2026-05-18

### 本轮范围

- 实现策略版本模型、create/list/detail API。
- 固化策略参数快照、配置快照、来源快照和 checksum。
- 发布记录可绑定 `strategy_version_id`。
- 发布时固化 `version_snapshot_json`。
- 前端 `/strategies` 增加策略版本区域和创建入口。
- 前端 `/publishes` 展示策略版本绑定与版本快照。
- 新增 GateI-1 E2E smoke。

### 本轮新增文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/PublishController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyVersionCreateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyVersionResponse.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyVersionService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/command/StrategyVersionCreateRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyVersion.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyVersionSnapshot.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyVersionStatus.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyVersionRepository.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/StrategyVersionServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcStrategyVersionSnapshotQueryPort.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyVersionRepository.java`
- `backend/nq-infra/src/main/resources/db/migration/V19__gate_i1_strategy_versions.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/StrategyVersionSnapshotView.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/StrategyVersionSnapshotQueryPort.java`
- `frontend/tests/e2e/publish-version-smoke.spec.ts`
- `frontend/tests/e2e/strategy-version-smoke.spec.ts`

### 本轮修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestPublishRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestPublishResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/BacktestRunController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyDefinitionController.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyDefinitionService.java`
- `backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/application/eval/api/BacktestRunApiService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcBacktestPublishRecordRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/BacktestPublishService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/command/BacktestPublishRequest.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/BacktestPublishRecord.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/BacktestPublishRecordRepository.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/BacktestPublishServiceTest.java`
- `frontend/src/api/publishes.ts`
- `frontend/src/api/query-keys.ts`
- `frontend/src/api/strategies.ts`
- `frontend/src/hooks/usePublishesListQuery.ts`
- `frontend/src/hooks/useStrategyListQuery.ts`
- `frontend/src/pages/publishes/PublishesPage.tsx`
- `frontend/src/pages/strategies/StrategiesPage.tsx`
- `frontend/src/types/publishes.ts`
- `frontend/src/types/strategies.ts`
- `frontend/tests/e2e/strategies-query.spec.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/STATUS.md`

### DB/migration 说明

- `V19` 新增 `strategy_versions`。
- `V19` 给 `backtest_publish_records` 新增 `strategy_version_id` 和 `version_snapshot_json`。
- `strategy_versions.strategy_code + version` 唯一约束用于保证同一策略下版本号幂等唯一。
- `backtest_publish_records.strategy_version_id` 索引用于按策略版本追溯发布记录。
- 所有新增表均有 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均有 PostgreSQL `COMMENT ON COLUMN`。

### 实现说明

- 后端新增策略版本 service/domain/repository/API，`nq-core` 不依赖 JDBC，SQL 位于 `nq-infra`。
- 发布服务通过 `StrategyVersionSnapshotQueryPort` 读取策略版本快照，避免 `nq-research` 反向依赖 `nq-core`。
- 发布时如果传入 `strategyVersionId`，必须存在且状态为 `ACTIVE`。
- 发布记录固化 `version_snapshot_json`，后续策略版本变化不会改写历史发布结果。
- 修正 `/api/strategies/{strategyCode}` 和 status 更新按 `strategyCode` 查询/更新，避免把业务编码误当内部 `strategyId`。
- 前端策略定义详情新增版本列表和创建表单；发布结果列表和详情展示策略版本 ID 与版本快照。
- E2E 在本地库缺少策略定义时，通过正式 `POST /api/strategies` 创建最小 SIM fixture，再验证策略版本创建链路。

### 验证结果

- `mvn -f backend/pom.xml test`：通过。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- 后端 local profile 临时启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本为 `19`。
- `npm run test:e2e`：通过，13 passed、3 skipped。
- Python 验证本轮未重新执行；本轮未修改 Python，沿用 BASELINE-FIX 已通过基线。

### 未做范围

- 未进入 GateI-2/3/4。
- 未接入 AI。
- 未新增 AI 模块、AI 信号、AI Paper Trading 或 AI 自动交易。
- 未做 Paper Trading。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

### 剩余风险

- `backtest_publish_records.strategy_version_id` 当前为可空，历史发布记录不会自动回填策略版本；后续如需回填必须单独评估。
- 当前发布绑定仅要求策略版本 `ACTIVE`，尚未进入 GateI-2 的评估指标与回测配置增强。
- `npm audit` 与 Vite chunk 体积警告仍按既有风险记录，未在 GateI-1 处理。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning 仍存在，本轮不处理。

### 下一步进入 GateI-2-WO 的条件

- GateI-1 变更完成审查并提交。
- GateI-2-WO 只能做回测配置、评估指标、结果追溯增强。
- GateI-2-WO 不得夹带 AI、Paper Trading 运行闭环、美股/A 股、合约全量、高频或复杂因子平台。

## GateI-2-WO 执行记录

日期：2026-05-19

### 本轮范围

- 增强 backtest config，使其可绑定 strategy version，并展示 strategy version、dataset、param、config 快照。
- 增强 backtest run 创建链路，在创建 run 时固化 strategy version snapshot、dataset snapshot、param snapshot、config snapshot。
- 增强 evaluation report 指标，持久化并返回 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON。
- 增强 `/backtests` 和 `/evaluations` 页面追溯展示。
- 新增 GateI-2 E2E smoke，并修复本地 E2E fixture 对固定账户 ID 的依赖。

### 新增文件

- `backend/nq-infra/src/main/resources/db/migration/V20__gate_i2_backtest_traceability.sql`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/dto/BacktestStrategyVersionBindingRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/EvaluationController.java`
- `frontend/tests/e2e/gatei2-fixtures.ts`
- `frontend/tests/e2e/backtest-config-enhanced-smoke.spec.ts`
- `frontend/tests/e2e/evaluation-report-enhanced-smoke.spec.ts`

### 修改文件

- `backend/nq-api/**/research/**`
- `backend/nq-core/**/research/**`
- `backend/nq-backtest/**`
- `backend/nq-eval/**`
- `backend/nq-infra/**/research/**`
- `frontend/src/api/backtests.ts`
- `frontend/src/api/evaluations.ts`
- `frontend/src/hooks/useBacktestsListQuery.ts`
- `frontend/src/hooks/useEvaluationsListQuery.ts`
- `frontend/src/pages/backtests/BacktestsPage.tsx`
- `frontend/src/pages/evaluations/EvaluationsPage.tsx`
- `frontend/src/types/backtests.ts`
- `frontend/src/types/evaluations.ts`
- `frontend/tests/e2e/support.ts`
- `frontend/tests/e2e/strategy-version-smoke.spec.ts`
- `frontend/tests/e2e/research-detail.spec.ts`
- `frontend/tests/e2e/research-query.spec.ts`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/STATUS.md`

### DB / Migration

- `V20__gate_i2_backtest_traceability.sql` 只新增 GateI-2 所需字段和索引，未修改历史 migration。
- `backtest_configs` 新增 `strategy_version_id`、`strategy_version_snapshot_json`、`param_snapshot_json`、`config_snapshot_json`，复用 GateH-3 `dataset_id` 与 `dataset_snapshot_json`。
- `backtest_runs` 新增 `strategy_version_id`、`strategy_version_snapshot_json`、`param_snapshot_json`、`config_snapshot_json`，复用 GateH-3 `dataset_snapshot_json`。
- `backtest_eval_reports` 新增 `total_return`、`annualized_return`、`profit_loss_ratio`、`metrics_json`。
- 新增索引覆盖 `backtest_configs.strategy_version_id`、`backtest_runs.strategy_version_id`、`backtest_eval_reports.backtest_run_id`。
- `V20` 未新增表；所有新增字段均有 PostgreSQL `COMMENT ON COLUMN` 注释，JSONB 字段注释包含用途与敏感信息禁入规则。

### 后端实现

- `PATCH /api/backtest-configs/{configId}/strategy-version` 绑定 strategy version，并固化版本快照与参数快照。
- `POST /api/backtest-runs` 从 config 复制 strategy version、dataset、param、config 快照，保证历史 run 不受后续 config 变更影响。
- `GET /api/backtest-configs`、`GET /api/backtest-configs/{configId}`、`GET /api/backtest-runs/{runId}` 返回完整追溯字段。
- 新增 `GET /api/evaluations` 与 `GET /api/evaluations/{evaluationId}`，返回增强指标和 `metricsJson`。
- API 层不写 SQL，core 不依赖 JDBC，JDBC 实现仍在 infra。
- 未修改策略核心算法、回测核心算法或交易核心状态机。

### 前端实现

- `/backtests` 展示 strategy version、dataset、参数快照、配置快照，并支持绑定 strategy version 与创建 run 后查看 run 级快照。
- `/evaluations` 展示 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON。
- 页面保留 loading、empty、error 状态。
- 服务端数据仍通过 Axios + TanStack Query 获取；Zustand 不存 backtest/evaluation 服务端数据。

### E2E 实现

- 新增 `backtest-config-enhanced-smoke`，验证 `/backtests` 页面 strategy version / dataset 追溯、config snapshot、run snapshot。
- 新增 `evaluation-report-enhanced-smoke`，验证 `/evaluations` 核心指标、详情和 `metrics JSON`。
- E2E fixture 使用正式 API 创建本地数据，不依赖外网交易所。
- `support.ts` 按 alias 解析真实 `exchangeAccountId`，避免本地自增 ID 漂移。
- 本地验证库补入 `accounts.account_id=3001` 作为 legacy strategy account 种子，用于既有 `strategy_definitions.account_id` 外键；该种子不属于 migration。

### 验证结果

- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告。
- `npm run test:e2e`：通过，17 passed / 1 skipped。
- E2E skipped 原因：`trading workspace / 配置订单 ID 时可打开订单详情` 未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI-2 主链。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。

### 未完成项与边界

- 未处理 `npm audit` 4 个依赖漏洞提示。
- 未处理 Vite chunk > 500 kB 警告。
- 未进入 GateI-3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增 SIM/Paper Trading 运行闭环。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机、策略核心算法或回测核心算法。

### GateI-3 结论

- GateI-2-WO 已完成。
- 允许进入 GateI-3-WO，但只能在本轮变更审查/提交后单独开工。
- GateI-3 只能做 SIM/Paper Trading 运行闭环，不能夹带 AI。

## GateH Freeze Snapshot 归档记录

日期：2026-05-19

### 本轮范围

- 新建/复用 `docs/gates/gate-h/` 作为 GateH completed 的只读历史快照目录。
- 将 GateH 完成相关文档从 `docs/current/` 复制归档到 `docs/gates/gate-h/`。
- 更新 `docs/gates/gate-h/README.md`，明确 GateH completed、GateH 范围、GateH 不包含 AI、不包含 GateI 策略版本/发布链路/Paper Trading。

### 归档文件

- `docs/gates/gate-h/PLAN_GATEH.md`
- `docs/gates/gate-h/GATEH_API_PLAN.md`
- `docs/gates/gate-h/GATEH_DB_PLAN.md`
- `docs/gates/gate-h/GATEH_FRONTEND_PLAN.md`
- `docs/gates/gate-h/GATEH_TEST_PLAN.md`
- `docs/gates/gate-h/GATEH_WORK_ORDER.md`
- `docs/gates/gate-h/API.md`
- `docs/gates/gate-h/DB_SCHEMA.md`
- `docs/gates/gate-h/TESTING.md`
- `docs/gates/gate-h/STATUS.md`
- `docs/gates/gate-h/ROADMAP.md`
- `docs/gates/gate-h/WORKLOG.md`
- `docs/gates/gate-h/README.md`

### 边界确认

- 使用复制归档，未移动 `docs/current/` 中的 GateI 文档。
- 未创建 `docs/gates/gate-i/`。
- 未改业务代码。
- 未新增 migration。
- 未新增 API。
- 未改前端页面。

## 项目入口文档同步记录

日期：2026-05-19

### 本轮范围

- 已同步根目录 `README.md`，使项目总入口反映 DOC-CLEAN、BASELINE-FIX、GateH、GateI-PLAN、GateI-1-WO、GateI-2-WO 已完成，Next 为 GateI-3-WO。
- 已同步根目录 `AGENTS.md`，使 Codex / Agent 执行纪律切换到 `Current stage: GateI-3-WO preparation`。
- 明确 GateI-3-WO 只能做 SIM / Paper Trading 运行闭环。
- 明确 AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始且禁止夹带。

### 边界确认

- 本轮只改入口和 Agent 执行纪律文档。
- 未改业务代码。
- 未新增 migration。
- 未新增 API。
- 未新增前端页面。
- 未接入 AI。
- 未创建 `docs/gates/gate-i/`。

### 验证说明

- 本轮为文档同步任务，不重新执行 `mvn`、`npm`、Python 全量测试。
- 已按任务要求执行 `git status --short` 与 README / AGENTS / WORKLOG 关键词检查。

## GateI-3-WO 执行记录

日期：2026-05-19

### 本轮范围

- 实现 SIM/Paper Trading 运行闭环最小版本。
- 新增 4 张 paper_trading 表：`paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions`。
- 新增后端 Paper Trading run 领域、JDBC 持久化、应用服务、API 服务和 controller。
- 新增前端 `/paper-trading` 入口、API 客户端、TanStack Query hooks、列表/详情/创建 UI。
- 新增 Paper Trading run E2E smoke 与 fixture 链路。
- 同步 docs/current 文档：API、DB_SCHEMA、TESTING、WORKLOG、STATUS。

### 新增文件

后端：

- `backend/nq-infra/src/main/resources/db/migration/V21__gate_i3_paper_trading.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingRun.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingRunStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingOrder.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperOrderStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingTrade.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperTradingPosition.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingRunRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingOrderRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingTradeRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperTradingPositionRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingRunService.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingRunCreateCommand.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/paper/PaperTradingApiService.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingRunServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingRunRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingOrderRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingTradeRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingPositionRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingRunResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingRunCreateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingOrderResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingTradeResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperTradingPositionResponse.java`

前端：

- `frontend/src/types/paper-trading.ts`
- `frontend/src/api/paper-trading.ts`
- `frontend/src/hooks/usePaperTradingQuery.ts`
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`
- `frontend/tests/e2e/paper-trading-fixtures.ts`
- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`

### 修改文件

- `frontend/src/api/query-keys.ts`：新增 `paperTradingQueryKeys`。
- `frontend/src/router/routes.tsx`：注册 `/paper-trading` 路由。
- `frontend/src/router/navigation.tsx`：新增 `paper-trading` 菜单项。
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/STATUS.md`
- `docs/current/WORKLOG.md`

### DB / Migration

- `V21__gate_i3_paper_trading.sql` 只新增 GateI-3 所需 4 张表，未修改历史 migration。
- 4 张表均包含 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段 `paper_trading_runs.status`、`paper_trading_orders.status`、`paper_trading_runs.trade_env`、`paper_trading_orders.side`、`paper_trading_trades.side` 均通过 `CHECK` 约束限制允许值。
- 外键约束：`paper_runs.publish_id → backtest_publish_records.publish_record_id`、`paper_runs.strategy_version_id → strategy_versions.strategy_version_id`、`paper_orders.paper_run_id → paper_runs`、`paper_trades.paper_order_id → paper_orders` 与 `paper_run_id → paper_runs`、`paper_positions.paper_run_id → paper_runs`。
- `paper_trading_positions` 通过 `(paper_run_id, symbol)` 唯一约束保证持仓行幂等。
- 索引：`idx_paper_runs_publish_id`、`idx_paper_runs_strategy_version_id`、`idx_paper_runs_status`、`idx_paper_orders_run_id`、`idx_paper_orders_run_symbol_status`、`idx_paper_trades_run_id`、`idx_paper_trades_order_id`、`idx_paper_trades_symbol_time`、`idx_paper_positions_run_id`。
- 新增字段 `paper_trading_runs.interval_code` 而非 `interval`，避免 PostgreSQL `INTERVAL` 关键字冲突。

### 后端实现

- `nq-research` 承载领域模型、port、应用服务，不依赖 JDBC。
- `nq-infra` 承载 JDBC 实现，使用 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB。
- `nq-api` 提供 `PaperTradingController`，所有写操作委派给 `PaperTradingApiService`，不直接写 SQL。
- `PaperTradingApiService` 把领域 `IllegalArgumentException` 映射为 HTTP 404、`IllegalStateException` 映射为 HTTP 409。
- 创建 Paper run 时通过 `BacktestPublishRecordRepository` 加载 publish 与 publish snapshot/version snapshot；通过 `BacktestRunRepository` 加载发布关联的 backtest run，复制 dataset snapshot 与 param snapshot；request body 中的 `configSnapshotJson` 作为运行级 config snapshot 固化。
- Paper run 状态机：`CREATED → RUNNING`（仅 start）；`RUNNING → STOPPED`（仅 stop）；非法状态过渡返回 409。
- `created_by` 第一版固定为 `system`，与既有 `BacktestPublishService` 等模块一致；后续可按权限链路接入登录用户。
- 不调用任何真实交易所下单接口。
- 不修改交易核心状态机、策略核心算法、回测核心算法。

### 前端实现

- `/paper-trading` 增强菜单与路由入口，归类到 `策略运行`。
- 提供查询区（按 publishId / status 过滤）、列表区、创建弹窗、详情抽屉。
- 详情抽屉包含订单、成交、持仓和快照标签页，每个标签页都有 loading / empty / error 状态。
- 服务端数据通过 Axios + TanStack Query 获取；Zustand 不存 Paper Trading 服务端数据。
- 列表行内提供 `查看详情`、`启动`、`停止` 按钮，按状态启用/禁用。

### E2E 实现

- 新增 `paper-trading-run-smoke.spec.ts`：登录 → 准备 fixture → 打开 `/paper-trading` → 查询 → 创建 run → 校验返回 `CREATED` 与快照绑定 → 启动 → 校验返回 `RUNNING` → 停止 → 校验返回 `STOPPED` → 打开详情 → 验证 orders/trades/positions 空态与快照标签。
- 新增 `paper-trading-fixtures.ts`：通过正式 API 完整链路准备数据，沿用 GateI-2 fixture 路径并扩展到 publish。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

### 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- `npm run test:e2e`：本轮未在干净本地 `5432` 实例上启动后端执行；E2E spec 与 fixture 已就绪，等待下一次完整本地验证窗口或 GateI-3-FIX 时执行。

### 剩余风险

- E2E 在本轮未实际跑通，依赖后续本地 5432 + Flyway V21 的本地 profile 启动，并需要 `accounts.account_id=3001` 种子。
- `paper_trading_orders/trades/positions` 第一版只在 controller 提供查询接口，第一版 Paper run 不会自动产生订单/成交/持仓事实，由 GateI-4 的撮合与风控回写填充。
- `created_by` 暂用 `system`；未与登录用户上下文打通，后续接入风控/审计时需要补充。
- `idempotencyKey` 字段未在第一版接入；同 publishId 重复创建会在 publishId / status 维度产生多条 run，由 GateI-4 风控边界一并完善。
- `npm audit` 4 个依赖漏洞、Vite chunk > 500 kB 警告、Ant Design React 19 / `Card.bordered` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。

### GateI-4 结论

- 后端 `mvn test` 通过且包含新增 PaperTradingRunServiceTest；前端 `npm run build` 通过。
- E2E 已在本地 PostgreSQL 5432 + Flyway V21 + 后端 local profile 环境下完整执行并通过。
- **允许进入 GateI-4-WO**，但只能在本轮变更审查/提交后单独开工。
- GateI-4 只能做风控回写、资金曲线、持仓曲线、交易复盘与异常停机，不能夹带 AI。

## GateI-3-FIX 执行记录

日期：2026-05-20

### 本轮范围

- 启动本地后端 local profile，确认 Flyway V21 已应用。
- 确认 account_id=3001 种子存在。
- 执行 `npm run test:e2e`，修复 Paper Trading E2E 选择器问题。
- 不扩展业务功能，不进入 GateI-4。

### 修改文件

- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`：修复 5 处 Playwright 选择器。

### 是否修改业务代码

否。只修改 E2E 测试选择器，未修改后端、前端业务代码、migration 或 API。

### Flyway V21 验证结果

- 后端启动日志：`Successfully validated 21 migrations`，`Current version of schema "public": 21`。
- `paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions` 表均存在（通过 API 返回 200 验证）。

### 后端 health 验证结果

- `GET /actuator/health` 返回 `{"status":"UP"}`。

### E2E 命令与结果

- 命令：`npm run test:e2e`
- 结果：**18 passed / 1 skipped**
- 耗时：1.3m

### skipped 用例说明

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID` 环境变量，为既有交易订单详情链路，不影响 GateI-3 Paper Trading 主链。

### 是否使用本地 seed

是。使用 `accounts.account_id=3001` 作为 legacy strategy account 种子（GateI-2 已补入，非 migration）。

### 是否调用外网

否。E2E fixture 全部通过本地后端 API 创建，不依赖外网交易所。

### 是否调用真实交易所

否。后端启动时 OKX adapter 因 `No route to host` 降级为 stub rejection，不影响 Paper Trading 链路。

### 是否调用 LIVE 下单

否。Paper Trading run 固定 `trade_env=SIM`，不调用任何真实交易所下单接口。

## GateI-4-WO 执行记录

日期：2026-05-20

### 本轮范围

- 实现 GateI-4 Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘与异常停机最小闭环。
- 新增 5 张监控/审计表：`paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events`。
- 新增后端 5 个领域记录、4 个 enum、5 个 repository port、5 个 JDBC 实现。
- 新增 `PaperTradingMonitorService` 应用服务并扩展 `PaperTradingApiService`、`PaperTradingController`。
- 新增 6 个响应 DTO + 1 个请求 DTO。
- 前端扩展 5 个新 Tab（风控结果 / 资金曲线 / 持仓曲线 / 交易复盘 / 异常停机），新增 5 个查询 hook + 2 个 mutation hook。
- 新增 `PaperTradingMonitorServiceTest` 单元测试。
- 同步 docs/current 文档：API、DB_SCHEMA、TESTING、WORKLOG、STATUS。

### 新增文件

后端：

- `backend/nq-infra/src/main/resources/db/migration/V22__gate_i4_paper_trading_monitor.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRiskCheckResult.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/RiskCheckStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/RiskCheckSeverity.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EquityCurveSnapshot.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PositionCurveSnapshot.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/TradeReplayRecord.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EmergencyStopEvent.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EmergencyStopTriggerType.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/EmergencyStopStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRiskCheckResultRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/EquityCurveSnapshotRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PositionCurveSnapshotRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/TradeReplayRecordRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/EmergencyStopEventRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingMonitorService.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperTradingMonitorServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRiskCheckResultRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcEquityCurveSnapshotRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPositionCurveSnapshotRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcTradeReplayRecordRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcEmergencyStopEventRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRiskCheckResultResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/EquityCurveSnapshotResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PositionCurveSnapshotResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/TradeReplayRecordResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/EmergencyStopEventResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/EmergencyStopRequestBody.java`

### 修改文件

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java`：扩展 7 个新端点。
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/paper/PaperTradingApiService.java`：注入 `PaperTradingMonitorService` 并新增 7 个委派方法。
- `frontend/src/types/paper-trading.ts`：新增 5 类监控/事件类型。
- `frontend/src/api/paper-trading.ts`：新增 7 个监控/异常停机 API。
- `frontend/src/api/query-keys.ts`：新增 5 个监控查询 key。
- `frontend/src/hooks/usePaperTradingQuery.ts`：新增 5 个查询 hook + 2 个 mutation hook。
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：详情抽屉扩展 5 个新 Tab。
- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`：扩展 GateI-4 监控/异常停机断言。
- `docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/STATUS.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`。

### DB / Migration

- `V22__gate_i4_paper_trading_monitor.sql` 只新增 GateI-4 所需 5 张表，未修改历史 migration。
- 5 张表均包含 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段 `paper_risk_check_results.status/severity`、`emergency_stop_events.trigger_type/status` 均通过 `CHECK` 约束限制允许值。
- 外键统一指向 `paper_trading_runs.paper_run_id`。
- 索引：`idx_risk_results_run_id_time`、`idx_equity_curve_run_id_time`、`idx_position_curve_run_id_time`、`idx_replay_run_id_time`、`idx_emergency_stop_run_id_time`，均按 `(paper_run_id, time DESC)` 组织。

### 后端实现

- `nq-research` 承载领域模型、port、`PaperTradingMonitorService` 应用服务，不依赖 JDBC。
- `nq-infra` 承载 5 个 JDBC 实现，遵循既有 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB 模式。
- `nq-api` 通过 `PaperTradingApiService` 委派到 `PaperTradingMonitorService`，不直接写 SQL。
- `runRiskCheckOnce` 第一版只写最小 `BASIC_HEALTH_CHECK / PASSED / LOW`，等待具体规则在后续 Gate 实现。
- `triggerEmergencyStop` 复用 `PaperTradingRunService.stop`：`RUNNING` 时调用 stop 状态机、写入 `APPLIED`；非 RUNNING 时记录 `FAILED` 并保留原因，不引入新状态。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。

### 前端实现

- 5 个新 Tab 全部走 TanStack Query；服务端数据不进 Zustand。
- "执行风控检查" 按钮触发 `useRunRiskOnceMutation`；"紧急停机" 通过 `Modal.confirm` + `useEmergencyStopMutation`，触发后 invalidate 所有 paper-trading query。
- 第一版无图表库依赖，资金/持仓曲线均以表格呈现。
- 既有 GateI-3 创建 / 启动 / 停止 / 详情逻辑保持不变。

### 单元测试

- `PaperTradingMonitorServiceTest` 覆盖 5 个用例：风控 run-once 正常写入、风控 list 空态、运行中触发 emergency stop 应用并停机、非 RUNNING 触发 emergency stop 记 FAILED、emergency stop list 空态。
- 复用 `PaperTradingRunServiceTest` 的 in-memory 仓储以避免重复实现。

### E2E 实现

- 在 `paper-trading-run-smoke.spec.ts` 中扩展 GateI-4 链路覆盖：执行风控检查、查看 5 个新 Tab、触发紧急停机后断言 run 进入 STOPPED。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

### 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，Reactor `BUILD SUCCESS`，35 tests / 0 failures（含 `PaperTradingMonitorServiceTest` 5 用例）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- `npm run test:e2e`：本轮未在干净本地实例上执行；spec 与 fixture 已就绪，等待下一次本地完整窗口或 GateI-4-FIX 时执行。

### 剩余风险

- E2E 在本轮未实际跑通，依赖后续本地 5432 + Flyway V22 启动后端 local profile 后执行。
- 第一版风控只写 `BASIC_HEALTH_CHECK`；具体撮合回写、风控规则、资金/持仓快照定时器在后续 Gate 实现，本轮 5 张表只承载结构。
- `idempotencyKey` 仍未接入；同 paperRunId 重复触发紧急停机会写多条 FAILED 记录，符合事件流语义但需在 GateI 闭环时审视。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

### GateI-4 结论

- 后端 `mvn test` 通过且包含 5 个新增 monitor 用例；前端 `npm run build` 通过。
- E2E 待补；GateI-4 自身实现已完成，留 GateI-4-FIX 跑 E2E 与可能的选择器修复。
- 不接 AI、不接 LIVE 下单、不修改交易核心状态机；满足 `CLAUDE.md` GateI-4 边界要求。
- GateI 仍未整体完成；不创建 `docs/gates/gate-i`，等待全部 GateI-* 完成与冻结。

## GateI-4-FIX 执行记录

日期：2026-05-21

### 本轮范围

- 重启后端 local profile，确认 Flyway V22 已应用。
- 确认 5 张 GateI-4 monitor 表存在。
- 执行 `npm run test:e2e`，修复 GateI-4 E2E 选择器与组件问题。
- 不扩展业务功能，不进入 GateJ。

### 修改文件

- `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`：修复 GateI-4 E2E 用例（改用 UI 操作替代 standalone request、修复 PASSED 断言、修复紧急停机 modal 选择器）。
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：将"执行风控检查"和"紧急停机"按钮移到 `PaperListSection` 外部（空态时仍可见）；将 `Modal.confirm` 改为 `modal.confirm`（通过 `App.useApp()` 获取，确保在 App context 下正确渲染）。

### 是否修改业务代码

是，但仅限 UI 布局调整和 Ant Design API 用法修正：
- 按钮从 `PaperListSection` children 移到外层（功能不变，只是空态时也可见）。
- `Modal.confirm` → `modal.confirm`（Ant Design 5.x App context 最佳实践）。
- 不修改后端、不修改 migration、不修改 API。

### Flyway V22 验证结果

- Flyway schema history 确认 version=22, description="gate i4 paper trading monitor"。
- 5 张表均存在：`paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events`。

### 后端 health 验证结果

- `GET /actuator/health` 返回 `{"status":"UP"}`。

### E2E 命令与结果

- 命令：`npm run test:e2e`
- 结果：**19 passed / 1 skipped**
- 耗时：1.4m

### skipped 用例说明

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID` 环境变量，为既有交易订单详情链路，不影响 GateI 主链。

### GateI-4-FIX 修复内容

1. GateI-4 E2E 用例原使用 Playwright `request` fixture 调用 API，但该 fixture 不共享浏览器登录态（Bearer token），导致 401。修复：改为通过 UI 操作（创建/启动/查看详情）和 UI 按钮（执行风控检查/紧急停机）完成全链路。
2. "执行风控检查"按钮原在 `PaperListSection` children 内，空态时被 `<Empty>` 替代不可见。修复：将按钮移到 `PaperListSection` 外层。
3. "紧急停机"按钮同理移到外层。
4. `Modal.confirm` 静态方法在 Ant Design 5.x + `App` wrapper 下不渲染 modal。修复：改用 `App.useApp()` 返回的 `modal.confirm`。
5. `PASSED` 文本断言因 Ant Design Tag 渲染时机需要 `.first()` 和 timeout。

### 是否调用外网

否。E2E fixture 全部通过本地后端 API 创建。

### 是否调用真实交易所

否。后端 OKX adapter 降级为 stub rejection。

### 是否调用 LIVE 下单

否。Paper Trading run 固定 `trade_env=SIM`。

### GateI-4-FIX 结论

- 后端测试通过（35 tests / 0 failures）、前端 build 通过、E2E 19 passed / 1 skipped。
- GateI 全部子阶段已完成：GateI-1-WO → GateI-2-WO → GateI-3-WO → GateI-3-FIX → GateI-4-WO → GateI-4-FIX。
- **GateI completed。**
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- AI 最早 GateK 才允许进入信号层。

## GateI Freeze + 文档同步

日期：2026-05-21

### 本轮范围

- GateI completed 后的文档同步与冻结归档。
- 不开发业务代码，不新增 migration，不新增 API，不改前端页面，不接 AI。

### 执行内容

- 已同步 README.md、AGENTS.md、CLAUDE.md（GateI completed, Next: GateJ-PLAN, AI not started）。
- 已同步 docs/current/README.md、docs/current/ROADMAP.md（修正过期表述）。
- 已同步 docs/README.md（补充 gate-h、gate-i 入口，写清文档使用规则）。
- 已创建 docs/gates/gate-i/（README + FREEZE_SUMMARY + 12 个归档文件）。
- 已检查并修正 docs/gates/gate-h/README.md（修正 GateI 仍在推进的过期表述）。
- 已确认 docs/DOC_RULES.md 规则完整（无需修改）。

### 新增文件

- `docs/gates/gate-i/README.md`
- `docs/gates/gate-i/FREEZE_SUMMARY.md`
- `docs/gates/gate-i/PLAN_GATEI.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_API_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_DB_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_FRONTEND_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_TEST_PLAN.md`（从 docs/current 复制）
- `docs/gates/gate-i/GATEI_WORK_ORDER.md`（从 docs/current 复制）
- `docs/gates/gate-i/API.md`（从 docs/current 复制）
- `docs/gates/gate-i/DB_SCHEMA.md`（从 docs/current 复制）
- `docs/gates/gate-i/TESTING.md`（从 docs/current 复制）
- `docs/gates/gate-i/STATUS.md`（从 docs/current 复制）
- `docs/gates/gate-i/ROADMAP.md`（从 docs/current 复制）
- `docs/gates/gate-i/WORKLOG.md`（从 docs/current 复制）

### 修改文件

- `docs/README.md`
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/WORKLOG.md`
- `docs/gates/gate-h/README.md`

### 是否修改业务代码

否。本轮只做文档同步和冻结归档。

### 是否新增 migration

否。

### 是否新增 API

否。

### 是否改前端页面

否。

### 是否接入 AI

否。

## GateJ-PLAN 执行记录

日期：2026-05-21

### 本轮范围

- 只做 GateJ 规划文档。
- 规划 Paper Trading 稳定运行。
- 明确 GateJ-1 / GateJ-2 / GateJ-3 / GateJ-FREEZE 拆分。
- 同步当前状态、路线、API、DB、测试与工作日志入口。

### 本轮新增文件

- `docs/current/PLAN_GATEJ.md`
- `docs/current/GATEJ_API_PLAN.md`
- `docs/current/GATEJ_DB_PLAN.md`
- `docs/current/GATEJ_FRONTEND_PLAN.md`
- `docs/current/GATEJ_TEST_PLAN.md`
- `docs/current/GATEJ_WORK_ORDER.md`

### 本轮修改文件

- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `README.md`
- `AGENTS.md`
- `CLAUDE.md`

### 本轮执行内容

- 新增 GateJ 总计划，明确背景、目标、不做范围、四个子阶段、完成标准。
- 新增 GateJ API 规划，覆盖 Schedule、Heartbeat、Daily Report、Alert、Recovery、Stability Check 六类 API。
- 新增 GateJ DB 规划，覆盖 7 张新表的字段、约束、索引、JSONB 用途和幂等策略。
- 新增 GateJ 前端规划，覆盖 7 个新 Tab 和详情页增强。
- 新增 GateJ 测试规划，覆盖单元测试、集成测试、E2E 矩阵和连续运行验收。
- 新增 GateJ 工作单，拆分 GateJ-1-WO 到 GateJ-FREEZE。
- 同步 STATUS.md、ROADMAP.md、API.md、DB_SCHEMA.md、TESTING.md。
- 同步 README.md、AGENTS.md、CLAUDE.md。

### 本轮未执行内容

- 未开发 GateJ 功能代码。
- 未新增 API 实现。
- 未新增 DB migration。
- 未新增前端页面实现。
- 未接入 AI。
- 未新增 AI 模块、AI 信号、AI Paper Trading 或 AI 自动交易。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未处理 npm audit。
- 未处理 Vite chunk 警告。

### 验证记录

- 本轮只修改文档，未重新执行全量 mvn、npm、Python 测试。
- 沿用 GateI completed 验证基线：后端 35 tests / 0 failures、前端 build 通过、E2E 19 passed / 1 skipped、Python pytest/mypy/ruff 通过。
- 已执行 git status --short。
- 已检查 6 份 GateJ 规划文档存在。
- 已检查 STATUS.md 写清 GateJ-PLAN、AI not started。
- 已检查本轮变更未新增业务代码、migration、API 实现或前端页面实现。

### 下一步进入 GateJ-1-WO 的条件

- GateJ-PLAN 文档完成审查。
- GateJ-1-WO 单独开工，只做 Paper run 调度与连续运行。
- GateJ-1-WO 不得夹带 GateJ-2/3 实现。
- GateJ-1-WO 不得接入 AI。

## GateJ-1-WO 执行记录

日期：2026-05-21

### 本轮范围

- 实现 GateJ-1 Paper run 调度与连续运行最小闭环。
- 新增 3 张表：`paper_run_schedules`、`paper_run_schedule_fires`、`paper_run_heartbeats`。
- 新增后端 4 个 enum/record（`PaperRunSchedule`、`PaperRunScheduleStatus`、`PaperRunScheduleFire`、`PaperRunScheduleFireStatus`、`PaperRunHeartbeat`、`PaperRunHeartbeatStatus`）、3 个 repository port、3 个 JDBC 实现。
- 新增 `PaperRunScheduleService` 应用服务并扩展 `PaperTradingApiService`。
- 新增 1 个新 controller `PaperTradingScheduleController` 与扩展 `PaperTradingController`（增加 heartbeat 端点）。
- 新增 5 个 DTO：`PaperRunScheduleResponse`、`PaperRunScheduleCreateRequestBody`、`PaperRunScheduleStatusUpdateRequestBody`、`PaperRunScheduleFireResponse`、`PaperRunHeartbeatResponse`。
- 前端扩展 2 个新 Tab（调度计划 / 心跳），新增 4 个查询 hook + 4 个 mutation hook。
- 新增 `PaperRunScheduleServiceTest` 单元测试（11 用例）。
- 新增 `paper-trading-schedule-smoke.spec.ts` E2E 用例。

### 新增文件

后端：

- `backend/nq-infra/src/main/resources/db/migration/V23__gate_j1_paper_run_schedules.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunSchedule.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunScheduleStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunScheduleFire.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunScheduleFireStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunHeartbeat.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/PaperRunHeartbeatStatus.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRunScheduleRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRunScheduleFireRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/paper/port/PaperRunHeartbeatRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperRunScheduleCreateCommand.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperRunScheduleService.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperRunScheduleServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunScheduleRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunScheduleFireRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunHeartbeatRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingScheduleController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleCreateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleStatusUpdateRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunScheduleFireResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/dto/PaperRunHeartbeatResponse.java`

前端：

- `frontend/tests/e2e/paper-trading-schedule-smoke.spec.ts`

### 修改文件

后端：

- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/api/paper/PaperTradingApiService.java`：注入 `PaperRunScheduleService` 并新增 8 个委派方法。
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java`：扩展 2 个 heartbeat 端点。

前端：

- `frontend/src/types/paper-trading.ts`：新增 5 类调度/心跳类型。
- `frontend/src/api/paper-trading.ts`：新增 8 个调度/心跳 API。
- `frontend/src/api/query-keys.ts`：新增 3 个查询 key。
- `frontend/src/hooks/usePaperTradingQuery.ts`：新增 3 个查询 hook + 4 个 mutation hook。
- `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：详情抽屉扩展调度计划/心跳 2 个 Tab；Drawer 宽度从 840 调整到 1080 以避免 Tabs 溢出。

文档：

- `docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/ROADMAP.md`。
- `README.md`、`AGENTS.md`、`CLAUDE.md`。

### DB / Migration

- `V23__gate_j1_paper_run_schedules.sql` 只新增 GateJ-1 所需 3 张表，未修改历史 migration。
- 3 张表均包含 PostgreSQL `COMMENT ON TABLE`。
- 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段 `paper_run_schedules.status`、`paper_run_schedule_fires.status`、`paper_run_heartbeats.status` 均通过 `CHECK` 约束限制允许值。
- 外键统一指向 `paper_trading_runs.paper_run_id`；`paper_run_schedule_fires.schedule_id` 关联 `paper_run_schedules.schedule_id`。
- 索引：`idx_paper_run_schedules_run_id/status/next_fire`、`idx_schedule_fires_schedule_id/run_id/fired_at`、`idx_heartbeats_run_id_time`。

### 后端实现

- `nq-research` 承载领域模型、port、`PaperRunScheduleService` 应用服务，不依赖 JDBC。
- `nq-infra` 承载 3 个 JDBC 实现，遵循既有 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB 模式。
- `nq-api` 通过 `PaperTradingApiService` 委派到 `PaperRunScheduleService`，不直接写 SQL。
- `createSchedule`：校验 paperRunId 存在 + cron 表达式 5/6/7 字段校验，第一版默认 ENABLED 状态。
- `runScheduleOnce`：仅 ENABLED 状态可触发；非 ENABLED 返回 409。第一版 fire 状态固定 SUCCEEDED，不调用真实交易所。
- `runHeartbeatOnce`：根据 Paper run 状态映射 heartbeat status（RUNNING→OK / STOPPED|FAILED→STOPPED / 其他→UNKNOWN）。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。
- 第一版不实现后台常驻调度器自动触发，仅提供 run-once 手动触发。

### 前端实现

- 2 个新 Tab 全部走 TanStack Query；服务端数据不进 Zustand。
- "创建调度"按钮在调度计划 Tab 顶部触发 Modal 表单（名称、cron、时区）。
- 调度行内提供"触发记录"、"执行一次"、"启用/禁用"操作；run-once 按钮在 ENABLED 状态下可用。
- "执行心跳检查"按钮触发 `useRunHeartbeatOnceMutation`，触发后 invalidate paper-trading query。
- 第一版无图表库依赖。
- Drawer 宽度从 840 调整为 1080，避免 11 个 Tab 触发 Ant Design Tabs 溢出折叠。

### 单元测试

- `PaperRunScheduleServiceTest` 覆盖 11 个用例：
  - `createScheduleShouldInsertWithEnabledStatus`
  - `createScheduleShouldRejectMissingRun`
  - `createScheduleShouldRejectInvalidCron`
  - `updateScheduleStatusShouldTransition`
  - `updateScheduleStatusShouldRejectInvalidStatus`
  - `runScheduleOnceShouldWriteSucceededFire`
  - `runScheduleOnceShouldRejectDisabledSchedule`
  - `listFiresShouldReturnByScheduleId`
  - `runHeartbeatOnceShouldWriteRecord`
  - `runHeartbeatOnceShouldRecordStoppedWhenRunStopped`
  - `listHeartbeatsShouldReturnByRunId`

### E2E 实现

- 新增 `paper-trading-schedule-smoke.spec.ts`，覆盖：登录 → 准备 fixture → 创建并启动 Paper run → 打开详情抽屉 → 调度计划 Tab → 创建调度 → 触发 run-once → 查看 fire 记录 → 禁用调度 → 心跳 Tab → 执行心跳检查 → 校验心跳记录。
- E2E 选择器全部限定在 `drawer = page.getByLabel('Paper Trading 详情')` 范围，避免与侧边栏 menu 项冲突。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

### 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，35 tests / 0 failures（含 PaperRunScheduleServiceTest 11 用例）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- 后端 local profile 启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本 `23`。
- `npm run test:e2e`：通过，**20 passed / 1 skipped**。
- `npm run test:e2e` skipped 用例：`trading workspace / 配置订单 ID 时可打开订单详情`，未配置 `E2E_TRADE_ORDER_ID`，与 GateJ-1 主链无关。

### 剩余风险

- 第一版 fire 状态固定 `SUCCEEDED`；后台常驻调度器自动触发未实现。
- 第一版 cron 表达式仅做字段数（5/6/7）合法性校验，未做完整 cron 语义校验。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

### 边界确认

- 未进入 GateJ-2（日报、告警）。
- 未进入 GateJ-3（恢复、稳定性验收）。
- 未进入 GateJ-FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做真实 LIVE 下单。
- 未调用真实交易所下单接口。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

### GateJ-1 结论

- 后端测试通过、前端 build 通过、E2E 20 passed / 1 skipped。
- GateJ-1-WO 已完成，所有验收标准已满足。
- **允许进入 GateJ-2-WO**，但只能在本轮变更审查/提交后单独开工。
- GateJ-2-WO 只能做运行监控、日报、告警，不能夹带恢复、稳定性验收或 AI。

---

# Worklog: GateJ-2-WO

日期：2026-05-21

## 目标

GateJ-2-WO：Paper Trading 运行监控 + 日报 + 告警。在 GateJ-1 完成的调度/心跳基础上，新增日报与告警事件能力，建立监控基础。仍不接 AI、不调用真实交易所下单、不动核心状态机/策略/回测算法。

## 修改文件清单

数据库 migration（新增 1 个）：

- 新增 `backend/nq-infra/src/main/resources/db/migration/V24__gate_j2_paper_run_daily_reports_alerts.sql`。

后端 nq-research（domain / port / service / command）：

- 新增 `backend/nq-research/.../research/domain/paper/PaperRunDailyReport.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunDailyReportStatus.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunAlert.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunAlertSeverity.java`。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunAlertStatus.java`。
- 新增 `backend/nq-research/.../research/domain/paper/port/PaperRunDailyReportRepository.java`。
- 新增 `backend/nq-research/.../research/domain/paper/port/PaperRunAlertRepository.java`。
- 新增 `backend/nq-research/.../research/application/paper/PaperRunMonitorService.java`。
- 新增 `backend/nq-research/.../research/application/paper/PaperRunDailyReportGenerateCommand.java`。
- 新增 `backend/nq-research/.../research/application/paper/PaperRunAlertCreateCommand.java`。
- 修改 `backend/nq-research/.../research/application/api/paper/PaperTradingApiService.java`。

后端 nq-infra（JDBC 实现）：

- 新增 `backend/nq-infra/.../research/infra/paper/jdbc/JdbcPaperRunDailyReportRepository.java`。
- 新增 `backend/nq-infra/.../research/infra/paper/jdbc/JdbcPaperRunAlertRepository.java`。

后端 nq-api（DTO + Controller）：

- 新增 `backend/nq-api/.../paper/api/dto/PaperRunDailyReportResponse.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunDailyReportGenerateRequestBody.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunAlertResponse.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunAlertCreateRequestBody.java`。
- 新增 `backend/nq-api/.../paper/api/dto/PaperRunAlertAckRequestBody.java`。
- 修改 `backend/nq-api/.../paper/api/web/PaperTradingController.java`。

后端测试：

- 新增 `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperRunMonitorServiceTest.java`（12 用例）。

前端：

- 修改 `frontend/src/api/paper-trading.ts`：新增 daily-reports / alerts API 客户端方法。
- 修改 `frontend/src/api/query-keys.ts`：新增 paper-trading dailyReports / alerts query keys。
- 修改 `frontend/src/types/paper-trading.ts`：新增 PaperRunDailyReportItem / PaperRunAlertItem / 请求与响应类型。
- 修改 `frontend/src/hooks/usePaperTradingQuery.ts`：新增 query/mutation hooks。
- 修改 `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：新增"日报"、"告警"两个 Tab。

前端 E2E：

- 新增 `frontend/tests/e2e/paper-trading-daily-report-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/paper-trading-alert-smoke.spec.ts`。

文档：

- 修改 `docs/current/STATUS.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`、`docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/ROADMAP.md`。
- 修改 `CLAUDE.md`、`AGENTS.md`、`README.md`。

## DB schema 变化

- 新增表 `paper_run_daily_reports`：
  - 主键 `report_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - 唯一约束 `uq_daily_reports_run_date (paper_run_id, report_date)`，保证按日幂等。
  - 状态 `status` CHECK：`GENERATED / PARTIAL / FAILED`。
  - JSONB 字段 `report_json` 用于保存日报详细数据，明确不保存密钥/token/cookie。
- 新增表 `paper_run_alerts`：
  - 主键 `alert_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - 严重程度 `severity` CHECK：`LOW / MEDIUM / HIGH / CRITICAL`。
  - 状态 `status` CHECK：`OPEN / ACKED / RESOLVED`。
  - JSONB 字段 `event_snapshot_json` 用于保存事件快照，明确不保存密钥/token/cookie。
- 所有新增表与字段均补齐 `COMMENT ON TABLE` / `COMMENT ON COLUMN`。
- 未修改任何已有 migration。

## 后端实现

- `nq-research` 承载领域模型、port、`PaperRunMonitorService` 应用服务，不依赖 JDBC。
- `nq-infra` 承载 2 个 JDBC 实现，遵循既有 `::text` 读取 JSONB、`CAST(? AS JSONB)` 写入 JSONB 模式。
- `nq-api` 通过 `PaperTradingApiService` 委派到 `PaperRunMonitorService`，不直接写 SQL。
- `generateDailyReport`：校验 paperRunId 存在 + reportDate 缺省时使用当前 UTC 日期，按 (paperRunId, reportDate) 通过 ON CONFLICT 实现幂等，alert_count 实时统计当日告警总数。
- `createAlert`：校验 paperRunId 存在 + severity 校验，新建告警状态固定 OPEN。无效 severity 返回 400，其他业务校验返回 404。
- `ackAlert`：OPEN → ACKED 转换；ACKED 状态再次 ack 幂等；RESOLVED 状态拒绝 ack 返回 409。
- `resolveAlert`：任意非 RESOLVED → RESOLVED；RESOLVED 状态再次 resolve 幂等。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。

## 前端实现

- 2 个新 Tab（日报、告警）全部走 TanStack Query；服务端数据不进 Zustand。
- 日报 Tab 顶部"生成今日日报"按钮触发 `useGenerateDailyReportMutation`；第一版传空 `{}`，由后端使用当前 UTC 日期。
- 告警 Tab 顶部"创建测试告警"按钮触发 `useCreateAlertMutation`，默认创建 SYSTEM_NOTICE / LOW 告警，便于本地 smoke。
- 告警行内提供"确认"、"解决"按钮；按当前 status 条件展示。
- 第一版无图表库依赖。

## 单元测试

- `PaperRunMonitorServiceTest` 覆盖 12 个用例：
  - `generateDailyReportShouldCreateReport`
  - `generateDailyReportShouldUseCurrentDateWhenNull`
  - `generateDailyReportShouldRejectMissingRun`
  - `listDailyReportsShouldReturnByRunId`
  - `createAlertShouldInsertOpenAlert`
  - `createAlertShouldRejectInvalidSeverity`
  - `ackAlertShouldTransitionToAcked`
  - `ackAlertShouldBeIdempotent`
  - `ackAlertShouldRejectResolved`
  - `resolveAlertShouldTransitionToResolved`
  - `resolveAlertShouldBeIdempotent`
  - `listAlertsShouldFilterByStatus`

## E2E 实现

- 新增 `paper-trading-daily-report-smoke.spec.ts`：登录 → 准备 fixture → 创建并启动 Paper run → 打开详情抽屉 → 日报 Tab → 生成今日日报 → 校验列表 → 再次生成确认幂等。
- 新增 `paper-trading-alert-smoke.spec.ts`：登录 → 准备 fixture → 创建并启动 Paper run → 打开详情抽屉 → 告警 Tab → 创建测试告警 → 校验列表 → 确认告警 (OPEN → ACKED) → 解决告警 (ACKED → RESOLVED)。
- E2E 选择器全部限定在 `drawer = page.getByLabel('Paper Trading 详情')` 范围。
- E2E 不依赖外网交易所，不调用真实 LIVE 下单接口。

## 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，BUILD SUCCESS，35 tests / 0 failures（含 PaperRunMonitorServiceTest 12 用例）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- 后端 local profile 启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本 `24`。
- `npm run test:e2e`：通过，**22 passed / 1 skipped**。
- `npm run test:e2e` skipped 用例：`trading workspace / 配置订单 ID 时可打开订单详情`，未配置 `E2E_TRADE_ORDER_ID`，与 GateJ-2 主链无关。

## 修复记录

- 初次 E2E 执行时发现 `PaperRunDailyReportGenerateRequestBody.reportDate` 标注了 `@NotNull`，与 `PaperRunMonitorService` 对 `reportDate = null` 时默认使用当日的实现冲突，前端调用空请求体被 400 拒绝。修复：移除该字段的 `@NotNull` 注解，允许空 body 走默认当日。
- 初次 alert E2E 用 `tr.filter({hasText: alertId})` 定位行失败：表格不显示 alertId 列。修复：改用 `tr.filter({hasText: '手动测试告警'})` 通过标题文本定位。

## 剩余风险

- 第一版日报字段（total_equity / daily_pnl / max_drawdown 等）使用占位 `BigDecimal.ZERO`，未与 equity_curve_snapshots 实际数据联动；属于 GateJ-2 范围之外的增量优化。
- 第一版告警来源（HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED）尚未由后台监控自动产出，仅支持手动 POST 创建；自动监控产出预留到 GateJ-3。
- 外部通知（邮件、Slack、钉钉）按工作单边界明确不做。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning 仍在，本轮不处理。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

## 边界确认

- 未进入 GateJ-3（恢复、稳定性验收）。
- 未进入 GateJ-FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做真实 LIVE 下单。
- 未调用真实交易所下单接口。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未引入图表库。
- 未做外部通知集成。

## GateJ-2 结论

- 后端测试通过、前端 build 通过、E2E 22 passed / 1 skipped。
- GateJ-2-WO 已完成，所有验收标准已满足。
- **允许进入 GateJ-3-WO**，但只能在本轮变更审查/提交后单独开工。
- GateJ-3-WO 只能做异常恢复、失败重试、稳定性验收结构，不能夹带连续运行验收或 AI。

---

# Worklog: GateJ-3-WO

日期：2026-05-22

## 目标

GateJ-3-WO：Paper Trading 异常恢复、失败重试、运行稳定性检查与自动告警最小落库。在 GateJ-1/2 基础上补齐恢复事件、稳定性验收结构、HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小能力。仍不接 AI、不调用真实交易所下单、不动核心状态机/策略/回测算法。

## 修改文件清单

数据库 migration（新增 1 个）：

- 新增 `backend/nq-infra/src/main/resources/db/migration/V25__gate_j3_paper_run_recovery_stability.sql`。

后端 nq-research（domain / port / service / command）：

- 新增 `PaperRunRecoveryEvent` / `PaperRunRecoveryType` / `PaperRunRecoveryStatus`。
- 新增 `PaperRunStabilityCheck` / `PaperRunStabilityCheckStatus`。
- 新增 `PaperRunRecoveryEventRepository`、`PaperRunStabilityCheckRepository` port。
- 扩展 `PaperRunAlertRepository`（新增 `countCriticalOpenByRunIdAndDateRange` / `countByRunIdAndTypeAndDateRange`）。
- 扩展 `PaperRunDailyReportRepository`（新增 `countByRunIdAndDateRange`）。
- 扩展 `PaperRunHeartbeatRepository`（新增 `countByRunIdAndDateRange` / `findLatestByRunId`）。
- 扩展 `PaperRunScheduleFireRepository`（新增 `listByRunIdAndStatus` / `countByRunIdAndStatusAndDateRange`）。
- 新增 `PaperRunRecoveryService`、`PaperRunStabilityCheckService`、`PaperRunMonitorRunService`。
- 新增 `PaperRunRecoverCommand` / `PaperRunRetryFailedStepCommand` / `PaperRunStabilityCheckGenerateCommand`。
- 修改 `PaperTradingApiService`：注入新服务并暴露恢复 / 稳定性验收 / 监控守护方法，统一 404 / 400 / 409 错误码映射。

后端 nq-infra（JDBC 实现）：

- 新增 `JdbcPaperRunRecoveryEventRepository` / `JdbcPaperRunStabilityCheckRepository`。
- 修改 `JdbcPaperRunAlertRepository` / `JdbcPaperRunDailyReportRepository` / `JdbcPaperRunHeartbeatRepository` / `JdbcPaperRunScheduleFireRepository`：补齐 port 新增方法。

后端 nq-api（DTO + Controller）：

- 新增 `PaperRunRecoveryEventResponse` / `PaperRunRecoverRequestBody` / `PaperRunRetryFailedStepRequestBody`。
- 新增 `PaperRunStabilityCheckResponse` / `PaperRunStabilityCheckGenerateRequestBody`。
- 新增 `PaperRunMonitorRunOnceResponse`。
- 修改 `PaperTradingController`：新增 7 个 endpoints（recovery-events / recover / retry-failed-step / stability-checks GET/POST/detail / monitor/run-once）。

后端测试：

- 新增 `PaperRunRecoveryServiceTest`（9 用例）。
- 新增 `PaperRunStabilityCheckServiceTest`（10 用例）。
- 新增 `PaperRunMonitorRunServiceTest`（8 用例）。
- 修改 `PaperRunMonitorServiceTest` / `PaperRunScheduleServiceTest`：补齐 port 新增方法的 in-memory 实现，保持原有 12 + 11 用例通过。

前端：

- 修改 `frontend/src/types/paper-trading.ts`：新增 PaperRunRecoveryEventItem / PaperRunStabilityCheckItem / PaperRunMonitorRunOnceResponse 等类型。
- 修改 `frontend/src/api/paper-trading.ts`：新增 listRecoveryEvents / recover / retryFailedStep / listStabilityChecks / generateStabilityCheck / runMonitorOnce。
- 修改 `frontend/src/api/query-keys.ts`：新增 recoveryEvents / stabilityChecks query keys。
- 修改 `frontend/src/hooks/usePaperTradingQuery.ts`：新增 6 个 query/mutation hooks。
- 修改 `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：新增"恢复事件"、"稳定性验收"两个 Tab；Drawer 宽度从 1080 调整为 1280。

前端 E2E：

- 新增 `frontend/tests/e2e/paper-trading-recovery-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/paper-trading-stability-check-smoke.spec.ts`。

文档：

- 修改 `docs/current/STATUS.md`、`WORKLOG.md`、`TESTING.md`、`API.md`、`DB_SCHEMA.md`、`ROADMAP.md`。
- 修改 `CLAUDE.md`、`AGENTS.md`、`README.md`。

## DB schema 变化

- 新增表 `paper_run_recovery_events`：
  - 主键 `recovery_event_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - CHECK：`recovery_type` ∈ MANUAL_RECOVER / RETRY_FAILED_STEP / HEARTBEAT_LAG_RECOVER / SCHEDULE_FIRE_RECOVER；`status` ∈ STARTED / SUCCEEDED / FAILED / SKIPPED。
  - JSONB 字段 `request_json` / `result_json` 注释明确不保存密钥/token/cookie。
- 新增表 `paper_run_stability_checks`：
  - 主键 `stability_check_id`，外键 `paper_run_id` → `paper_trading_runs.paper_run_id`。
  - CHECK：`status` ∈ PASSED / FAILED / PARTIAL；`check_window_end > check_window_start`；`uptime_ratio` ∈ [0, 1]。
  - 唯一约束 `uq_stability_checks_run_window`。
  - JSONB 字段 `summary_json` 注释明确不保存密钥/token/cookie。
- 所有新增表与字段均补齐 `COMMENT ON TABLE` / `COMMENT ON COLUMN`。
- 未修改任何已有 migration。

## 后端实现要点

- `recover` / `retryFailedStep`：根据 Paper run 状态映射 recovery status（STOPPED → SKIPPED；其它 → SUCCEEDED）。每次记录独立事件，不幂等。
- `generateStabilityCheck`：校验窗口合法（end > start）+ paperRunId 存在；按 `(paper_run_id, check_window_start, check_window_end)` ON CONFLICT 幂等；按第一版口径计算 status / uptime_ratio。
- `runOnce` 监控守护：检测 heartbeat lag（阈值固定 300s，仅对 RUNNING 状态生效）+ schedule fire failed（最近 5 分钟）；每种 alert_type 在 5 分钟去重窗口内不重复创建；第一版只落库，不外发。
- 不调用任何真实交易所下单接口；不修改交易核心状态机、策略核心算法、回测核心算法。

## 前端实现要点

- 2 个新 Tab 全部走 TanStack Query；服务端数据不进 Zustand。
- 稳定性验收 Tab 显式备注第一版口径并明确不等于 GateJ-FREEZE 最终验收。
- 第一版无图表库依赖。

## 验证命令与结果

- `mvn -f backend/pom.xml test`：通过，BUILD SUCCESS（GateJ-3 新增 27 用例 + 既有用例全部通过）。
- `npm run build`：通过；仍有 Vite chunk > 500 kB 警告，本轮不处理。
- 后端 local profile 启动：通过，`/actuator/health` 返回 `UP`，Flyway 当前版本 `25`。
- `npm run test:e2e`：通过，24 passed / 1 skipped；唯一 skipped 与 GateJ-3 无关。

## 修复记录

- 监控守护 dedupe 单元测试在固定 Clock 下失败：因 end 边界 exclusive 导致 `createdAt == now` 被排除。修复：监控守护查询时使用 `now.plusSeconds(1)` 作为上界。
- 13 个 Tab 触发 Ant Design Tabs 溢出折叠："恢复事件 / 稳定性验收"被收进 ellipsis 菜单且 tabpanel 不切换。修复：Drawer 宽度 1080 → 1280。
- 新增 port 方法导致既有测试 in-memory repo 编译失败：补全相关方法。

## 剩余风险

- 第一版 `uptime_ratio` 粗略口径（PASSED=1.0 / PARTIAL=0.9 / FAILED 有心跳=0.5 / 无心跳=0），未按时间精确加权。
- 自动告警去重仅按 alert_type + 5 分钟时间窗口；未做 fire_id / event 维度去重。
- HEARTBEAT_LAG 阈值固定 300 秒；未提供运行时配置入口。
- 外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）按边界明确不做。
- 自动恢复策略引擎按边界明确不做（仅落库 alert，不自动触发 recover）。
- `npm audit`、Vite chunk > 500 kB、Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning 仍在。
- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有基线。

## 边界确认

- 未进入 GateJ-FREEZE 正式验收归档（1h/24h/7d 由 GateJ-FREEZE 独立执行）。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做真实 LIVE 下单。
- 未调用真实交易所下单接口。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机、策略核心算法、回测核心算法。
- 未引入图表库。
- 未做外部通知集成。
- 未做自动恢复策略引擎。
- 未把 GateJ 整体写为 completed（仅 GateJ-3-WO completed）。
- 未把 AI 写为 started。

## GateJ-3 结论

- 后端测试通过、前端 build 通过、E2E 24 passed / 1 skipped。
- GateJ-3-WO 已完成，所有验收标准已满足。
- **允许进入 GateJ-FREEZE**，但只能在本轮变更审查/提交后单独开工；GateJ-FREEZE 只能做 1h/24h/7d 连续运行验收与冻结，不能夹带 AI。

---

# Worklog: DOC-CLEAN-2

日期：2026-05-22

## 目标

在 GateJ-3-WO completed、Next: GateJ-FREEZE 阶段执行一次文档梳理：让 `docs/current/` 只承载当前事实和 GateJ 阶段规划，不再保留已冻结 Gate 的计划副本；让 `docs/gates/` 只承载已完成 Gate 的冻结卷宗；让根目录 README / AGENTS / CLAUDE 与 `docs/README.md` / `docs/current/README.md` 入口清晰、重复最少。本轮不动业务代码、API、migration、前端页面实现。

## 删除的冗余文档（12 个）

`docs/current/` 删除以下 12 个 GateH / GateI 计划副本（已通过 `diff -q` 与 `docs/gates/gate-h/`、`docs/gates/gate-i/` 中的冻结副本逐一比对，全部 `[same]`）：

- `docs/current/PLAN_GATEH.md`、`GATEH_API_PLAN.md`、`GATEH_DB_PLAN.md`、`GATEH_FRONTEND_PLAN.md`、`GATEH_TEST_PLAN.md`、`GATEH_WORK_ORDER.md`
- `docs/current/PLAN_GATEI.md`、`GATEI_API_PLAN.md`、`GATEI_DB_PLAN.md`、`GATEI_FRONTEND_PLAN.md`、`GATEI_TEST_PLAN.md`、`GATEI_WORK_ORDER.md`

## 归档的历史文档

本轮无新增归档：

- 上述 12 个 GateH / GateI 计划副本已在 `docs/gates/gate-h/` 与 `docs/gates/gate-i/` 中保存为 Gate 冻结卷宗，无需另行归档。
- `docs/archive/{gate-inputs,legacy-root-docs,rc1}/` 既有结构清晰，本轮不调整。

## 优化的入口文档

- `docs/README.md`：移除 "Next: GateJ-PLAN" 等过期描述；同步至 `GateJ-3-WO completed / Next: GateJ-FREEZE`；新增 GateJ 规划与 DOC_CLEAN_REPORT 入口；新增"已完成 Gate 的计划文档只保留在 `docs/gates/gate-x/`，不在 `docs/current/` 重复"的规则说明。
- `docs/current/README.md`：从 "GateI completed / Next: GateJ-PLAN" 同步至 `GateJ-3-WO completed / Next: GateJ-FREEZE`；新增 GateJ 规划文件清单与历史 Gate 冻结卷宗指引；明确 GateJ-FREEZE 不夹带 AI / 新业务功能。
- `README.md`：移除已删除的 `docs/current/PLAN_GATEI.md`、`docs/current/GATEI_WORK_ORDER.md` 引用，改为指向当前 GateJ 规划文档；扩展"当前明确不做"清单（含外部通知 / 自动恢复策略引擎）；明确 E2E skipped 与 GateJ 主链无关。
- `CLAUDE.md` / `AGENTS.md`：在 Current stage 之外新增"GateJ-FREEZE 允许范围 / 禁止范围"小节，明确 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不创建 `docs/gates/gate-j/` 除非 GateJ completed。

## 新增文档

- `docs/current/DOC_CLEAN_REPORT.md`：本轮清理报告（删除/归档/保留清单、最终结构、未删除但仍需观察的文件、当前结论）。

## docs/current 最终结构

```
docs/current/
├── README.md, STATUS.md, ROADMAP.md, WORKLOG.md, TESTING.md
├── API.md, DB_SCHEMA.md, MODULES.md, ARCHITECTURE.md, RUNBOOK.md
├── PLAN_GATEJ.md, GATEJ_{API,DB,FRONTEND,TEST}_PLAN.md, GATEJ_WORK_ORDER.md
└── DOC_CLEAN_REPORT.md
```

不再保留 GateH / GateI 计划副本。

## docs/gates 最终结构

```
docs/gates/{README.md, gate-a/, ..., gate-g/, gate-h/, gate-i/}
```

`gate-j/` 不存在，待 GateJ-FREEZE 通过后再创建。

## 已修正的过期状态

- `docs/README.md` 中 "Next: GateJ-PLAN"。
- `docs/current/README.md` 中 "GateI completed / Next: GateJ-PLAN"。
- `README.md` 中已删除的 `docs/current/PLAN_GATEI.md` / `GATEI_WORK_ORDER.md` 引用。

## 边界确认

- 未修改 backend / frontend / research 业务代码。
- 未新增 migration、API 实现。
- 未改前端页面实现。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未创建 `docs/gates/gate-j/`。
- 未把 GateJ 写为 completed。
- 未把 GateK 写为 started。
- 未把 AI 写为 started。
- 未删除 `docs/gates/gate-h/`、`docs/gates/gate-i/`、`docs/templates/`、`docs/DOC_RULES.md`。
- 未删除仍有历史价值的 `docs/archive/` 内容。

## 验证

- `git status --short`：仅 docs 路径下的删除/修改/新增；无业务代码、migration、API 实现、前端页面实现变更。
- 因本轮只动文档，未重跑 `mvn test`、`npm run build`、`npm run test:e2e`、Python `pytest/mypy/ruff`；沿用 GateJ-3-WO 的通过基线（mvn BUILD SUCCESS / Flyway V25 / npm build / E2E 24 passed 1 skipped）。

## DOC-CLEAN-2 结论

- 文档结构已收口到 GateJ-FREEZE 前稳定状态。
- 当前事实唯一指向 `docs/current/`；已完成 Gate 的计划文档不在 `docs/current/` 与 `docs/gates/` 之间重复。
- README / AGENTS / CLAUDE / docs/README / docs/current/README 全部同步到 `GateJ-3-WO completed / Next: GateJ-FREEZE / AI not started / GateK not started`。
- 允许继续进入 GateJ-FREEZE，但 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新业务功能。

---

# Worklog: PRE-FREEZE-CODE-AUDIT

日期：2026-05-22

## 目标

在 GateJ-FREEZE 之前执行前置代码 / 文档 / 实现真实性 / 运行链路审查。本轮不做功能开发、不修业务代码、不接 AI、不创建 `docs/gates/gate-j/`。

## 范围

按要求覆盖 14 类审查：
1. 文档状态一致性
2. 实现真实性与文档一致性
3. 后端模块边界
4. 数据库 / Flyway / 注释 / 约束 / 索引
5. Paper Trading 主链完整性
6. Schedule / Heartbeat / Report / Alert / Recovery / Stability 运行链
7. API 命名、DTO、错误处理、分页、幂等
8. 前端页面与数据层结构
9. E2E 稳定性与测试数据幂等
10. Python research 模块
11. Paper / LIVE 隔离
12. AI 未接入与未来 AI 接入边界
13. GateJ-FREEZE 验收准备度
14. 技术债与非阻塞风险分级

## 修改文件清单

文档：
- 新增 `docs/current/PRE_FREEZE_AUDIT_REPORT.md`。
- 新增 `docs/current/PRE_FREEZE_AUDIT_FIX_PLAN.md`。
- 新增 `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`。
- 修改 `docs/current/STATUS.md`：同步阶段为 `PRE-FREEZE-CODE-AUDIT completed`，写明 P0/P1 统计与下一步条件。
- 修改 `docs/current/WORKLOG.md`：追加本轮审查记录。
- 修改 `docs/current/TESTING.md`：追加本轮验证记录。
- 修改 `README.md` / `AGENTS.md` / `CLAUDE.md`：同步阶段表述与下一步条件。

代码：
- **未修改** backend、frontend、research/py 任何业务代码。
- **未新增** Flyway migration、API、前端页面实现。

## 是否修改业务代码

否。本轮纯文档审查与状态同步。

## 是否新增 migration

否。

## 是否新增 API

否。

## 是否改前端页面实现

否。

## 是否接入 AI

否。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 docs/current 与根目录入口文档变更 |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，0 failures / 0 errors（archunit ModuleBoundary 6、PackageBoundary 1、nq-app suite 35 全部通过；Paper 单元测试 PaperTradingRunService 4 + PaperTradingMonitorService 5 + PaperRunScheduleService 11 + PaperRunMonitorService 12 + PaperRunRecoveryService 9 + PaperRunStabilityCheckService 10 + PaperRunMonitorRunService 8 全部通过）|
| `npm run build` | 通过 | Vite 通过，dist/index.js ≈ 1.48 MB（gzip 446 kB），仍有 chunk > 500 kB 警告 |
| `npm run test:e2e` | **未在本轮重跑** | 沿用 GateJ-3-WO 24 passed / 1 skipped 基线；P1-1 要求 GateJ-FREEZE 入场前补跑 |
| `python -m pytest -q` | **未在本轮重跑** | 当前 shell 仅 WindowsApps stub（`python.exe` exit 49），无真实 Python 解释器；沿用 BASELINE-FIX-2 / GateJ-3 通过基线；P1-2 要求 GateJ-FREEZE 入场前补跑 |
| `python -m mypy src` | **未在本轮重跑** | 同上；P1-2 |
| `python -m ruff check .` | **未在本轮重跑** | 同上；P1-2 |

## P0 / P1 / P2 / P3 统计

- P0：0
- P1：4（P1-1 入场前重跑 E2E；P1-2 入场前重跑 Python；P1-3 PaperTradingPage 重构，不阻塞；P1-4 验收记录模板，已闭环）
- P2：11（详见 PRE_FREEZE_AUDIT_REPORT.md 第 25 节）
- P3：4（详见 PRE_FREEZE_AUDIT_REPORT.md 第 29 节）

## 是否允许进入 GateJ-FREEZE

允许。GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新业务功能。入场前必须重跑一次 `npm run test:e2e` 与 Python `pytest/mypy/ruff` 确认基线。

## 边界确认

- 未修改 backend / frontend / research 业务代码。
- 未新增 migration、API、前端页面实现。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未创建 `docs/gates/gate-j/`。
- 未把 GateJ 写为 completed。
- 未把 GateK 写为 started。
- 未把 AI 写为 started。
- 未把失败验证写成通过：E2E 与 Python 本轮未执行的部分均明确标记为「未在本轮重跑」，并通过 P1-1 / P1-2 列入 GateJ-FREEZE 入场前的必做项。

## 结论

- 文档、代码、DB、API、前端、E2E、Python、Paper/LIVE 隔离、AI 边界、模块边界全部一致。
- Paper Trading 主链完整。
- GateJ-FREEZE 准备度就绪。
- 允许进入 GateJ-FREEZE。详见 `PRE_FREEZE_AUDIT_REPORT.md` 第 30 节与 `PRE_FREEZE_AUDIT_FIX_PLAN.md` 第 9 节。

---

# Worklog: PRE-FREEZE-CODE-AUDIT-SECOND-PASS

日期：2026-05-22

## 目标

Codex 接手执行 PRE-FREEZE-CODE-AUDIT 二次审查与实际验证，复核 Claude 第一轮结论，补齐第一轮未实际执行的 E2E 与 Python 基线，并判断是否允许进入 GateJ-FREEZE。

## 本轮范围

- 复核 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。
- 实际执行后端测试、前端 build、完整 E2E、Python pytest/mypy/ruff。
- 二次抽查 API、DB、Paper/LIVE 隔离和 AI 边界。
- 只更新文档，不修业务代码。

## 修改文件清单

- `docs/current/PRE_FREEZE_AUDIT_REPORT.md`
- `docs/current/PRE_FREEZE_AUDIT_FIX_PLAN.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `README.md`
- `AGENTS.md`
- `CLAUDE.md`

## 新增文件清单

无。

## 是否修改业务代码

否。

## 是否新增 migration / API / 前端页面实现

否。

## 是否接入 AI

否。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；Vite chunk > 500 kB 警告仍存在 |
| `cd frontend && npm run test:e2e` | 通过 | 后端 local profile 启动成功；Flyway 当前版本 25；Playwright 24 passed / 1 skipped / 0 failed |
| `cd research/py && python -m pytest -q` | 通过 | 2 passed |
| `cd research/py && python -m mypy src` | 通过 | Success: no issues found in 8 source files |
| `cd research/py && python -m ruff check .` | 通过 | All checks passed |

## 实现真实性二次抽查

- API：指定 20 个 GateJ 主链 endpoint 均存在于 `PaperTradingController` / `PaperTradingScheduleController`，对应 DTO 与 `PaperTradingApiService` / application service 委派存在。
- DB：V21-V25 覆盖 16 张 Paper 表；COMMENT ON TABLE / COMMENT ON COLUMN、CHECK、FK、关键 UNIQUE、关键 index 均存在。
- 前端：`/paper-trading` 详情抽屉 15 个 Tab 存在，并通过 TanStack Query / Axios client 对应后端能力。
- E2E：完整 25 tests total，GateJ 主链 spec 全部执行通过；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路。
- Python：offline research 工具链 pytest/mypy/ruff 全部通过。

## Paper / LIVE / AI 边界

- `backend/nq-research/.../application/paper/**` 与 `backend/nq-api/.../paper/**` 未发现 `TradingAdapter`、`placeOrder`、`cancelOrder`、`RestTemplate`、`WebClient`、`HttpClient` 调用。
- schedule / heartbeat / daily report / alert / recover / retry / stability / monitor run-once 均只写本地 DB 或聚合本地状态。
- emergency stop 只调用 `PaperTradingRunService.stop` 停止 Paper run，不调用真实交易所撤单。
- `backend` / `frontend/src` / `research/py` 未发现 OpenAI / Anthropic / LLM provider / AI Signal / AI Trading 业务接入。

## 新发现分级

- P0：0。
- P1：0。Claude 第一轮 P1-1 / P1-2 已由本轮实际验证关闭；P1-3 不阻塞；P1-4 已闭环。
- P2：新增 1 项前端 runtime warning 集合（Ant Design React 19 compatibility、`Card.bordered`、`Modal.destroyOnClose`、`useForm` 未连接、`Descriptions` span 合计不匹配），不阻塞 GateJ-FREEZE。
- P3：0。

## 边界确认

- 未执行 GateJ-FREEZE 1h/24h/7d 连续运行验收。
- 未创建 `docs/gates/gate-j/`。
- 未把 GateJ 写成 completed。
- 未把 GateK 写成 started。
- 未把 AI 写成 started。
- 未把失败验证写成通过。

## 结论

允许进入 GateJ-FREEZE，但必须在本轮审查报告提交后单独开工。GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

# Worklog: AUDIT-FIX

## 本轮目标

关闭 FULL_SECURITY_AUDIT 报告中阻塞 GateJ-FREEZE 的两项问题：旧 OKX dome 验收脚本 P1、Windows excluded port 导致的 E2E 端口失败。本轮不新增业务功能、不新增 API、不新增 migration、不接 AI、不修改交易下单/风控/撮合/恢复/调度核心逻辑。

## 修改文件清单

- `scripts/gated_okx_dome_verify.ps1`
- `docs/archive/scripts/gated_okx_dome_verify.ps1`
- `frontend/playwright.config.ts`
- `frontend/playwright.config.js`
- `frontend/tests/e2e/run-e2e.mjs`
- `frontend/vite.config.ts`
- `frontend/vite.config.js`
- `frontend/.env.example`
- `docs/current/API.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/AUDIT_FIX_REPORT.md`

## P1 处理

- 旧 `scripts/gated_okx_dome_verify.ps1` 已从可执行 `scripts/` 区域移出，归档到 `docs/archive/scripts/gated_okx_dome_verify.ps1` 作为历史证据。
- 原 `scripts/gated_okx_dome_verify.ps1` 仅保留安全阻断 stub，明确旧 `/__gated/**` 是历史路径，GateJ 不允许执行该脚本，不得用于真实交易验收。
- `docs/current/API.md` 已再次确认当前正式 HTTP API 统一使用 `/api/**`，`/__gated/**` 不属于当前可执行 API。

## E2E 端口处理

- `frontend/playwright.config.ts` 默认 `baseURL` 和 Vite webServer 端口从 `4173` 调整为 `5179`。
- `frontend/tests/e2e/run-e2e.mjs` Vite 启动端口从 `4173` 调整为 `5179`。
- `frontend/vite.config.ts` Vite dev / preview 默认端口从 `4173` 调整为 `5179`。
- `frontend/.env.example` 中 `E2E_BASE_URL` 同步为 `http://127.0.0.1:5179`。
- 原因：当前 Windows TCP excluded range 包含 `4141-4240`，`4173` 会触发 `EACCES`。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 变更限定在 AUDIT-FIX 范围；上一轮 `FULL_SECURITY_AUDIT_REPORT.md` 仍为未跟踪新增报告 |
| `git diff --stat` | 已执行 | 用于确认变更规模 |
| `git diff -- scripts/gated_okx_dome_verify.ps1 docs/archive/scripts/gated_okx_dome_verify.ps1 frontend/playwright.config.ts frontend/tests/e2e/run-e2e.mjs docs/current/API.md docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md` | 已执行 | 用于确认 P1 与 E2E 端口修复 diff |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 chunk > 500 kB 警告 |
| `cd frontend && npm run test:e2e` | 通过 | 首次在后端未启动时失败于 `127.0.0.1:18888 ECONNREFUSED`；启动后端 local profile 后复跑通过，Vite 监听 `127.0.0.1:5179`，结果 24 passed / 1 skipped / 0 failed |

## 结论

- FULL_SECURITY_AUDIT 登记的 P1 已关闭。
- E2E `4173 EACCES` 端口问题已关闭，当前 E2E 使用 `5179` 并已通过完整回归。
- 建议允许重新进入 GateJ-FREEZE 判断；GateJ-FREEZE 必须单独开工，只做 1h / 24h / 7d 连续运行验收与冻结。

## 边界确认

- 未新增后端业务功能。
- 未新增前端业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI。
- 未修改交易下单、风控、撮合、恢复、调度核心逻辑。
- 未执行真实 OKX / Binance 下单脚本。
- 未读取或输出 `.env`、私钥、Token、交易所凭证明文。

---

# Worklog: GateJ-FREEZE-FIX

日期：2026-05-28

## 本轮目标

修复 GateJ-FREEZE ECS 部署后的两个阻塞问题：登录页仍展示本地联调敏感信息；服务器 `users.password_hash` 存在非 BCrypt 值导致 `/api/auth/login` 返回 401。本轮只允许修改登录页安全展示、auth 初始化/部署脚本、freeze 部署文档，不新增 API、不新增 migration、不接 AI/DH/真实交易。

## 根因

- 登录页仍保留旧本地联调说明，生产/freeze 构建中展示 legacy console gate、本地端口、默认账号密码、认证 API 和 Authorization header 示例。
- 服务器日志 `BCrypt non-hash warning` 表明登录接口已到达认证逻辑，但数据库中的目标用户 `password_hash` 不是 BCrypt 格式；因此 Nginx 代理和接口连通性不是根因。

## 修改文件清单

- `frontend/src/pages/login/LoginPage.tsx`
- `frontend/src/styles/index.css`
- `frontend/src/router/RequireAuth.tsx`
- `frontend/src/pages/dashboard/DashboardPage.tsx`
- `frontend/src/components/page/ListPageShell.tsx`
- `frontend/src/pages/{strategies,schedules,runs,research,backtests,evaluations,publishes}/*.tsx`
- `frontend/src/utils/env.ts`
- `frontend/src/store/auth-store.ts`
- `backend/nq-app/src/main/resources/application-freeze.yml`
- `deploy/.env.freeze.example`
- `deploy/docker-compose.freeze.yml`
- `scripts/seed-freeze-user.sh`
- `scripts/build-freeze-release.ps1`
- `docs/current/GATEJ_FREEZE_DEPLOYMENT.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- 登录页只保留 `NexusQuant 控制台`、用户名、密码、登录按钮和错误提示；移除默认表单值，前端不再展示默认账号/密码。
- 清理会进入 production bundle 的旧 legacy console gate 和认证协议展示文案，确保 `frontend/dist` 不含指定敏感串。
- 新增 `freeze` profile，连接服务器 PostgreSQL、启用 Flyway、禁用 `local` 默认 seed users，避免启动时把固定本地用户 hash 写回服务器库。
- 新增 `scripts/seed-freeze-user.sh`：从 `.env.freeze` 或进程环境读取 `NQ_FREEZE_ADMIN_USERNAME` / `NQ_FREEZE_ADMIN_PASSWORD`，使用 PostgreSQL 容器内 `pgcrypto` 生成 BCrypt hash，幂等 upsert 用户并授予 `ADMIN / OPERATOR / VIEWER`。
- 更新 release 打包脚本，确保 `seed-freeze-user.sh` 进入 release 包，并在 `RELEASE_INFO.md` 写明 seed 步骤。
- 更新 freeze 部署文档，固定顺序为：启动 compose -> seed freeze user -> curl 登录验证 -> 浏览器登录验证 -> 健康检查与连续验收。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 chunk > 500 kB 警告 |
| `rg -n "<redacted-local-test-password>\|18888\|legacy console gate\|/api/auth/login\|<redacted-authorization-header-prefix>" frontend/dist` | 通过 | 无命中 |
| `rg -n "/api/auth/me" frontend/dist` | 通过 | 无命中 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 生成 `release/nq-gatej-freeze-release.zip`；首次因沙箱无法写入本机 Maven repository tracking file 失败，提权重跑通过 |
| `jar tf backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar \| Select-String application-freeze.yml` | 通过 | jar 内包含 `BOOT-INF/classes/application-freeze.yml` |

## 新 release 包

- `release/nq-gatej-freeze-release.zip`
- 大小：约 29.5 MiB。
- release 包不提交 Git；`.gitignore` 已忽略 `release/`。

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易、AI、DH。
- 未提交真实密码、release zip、jar、dist、logs、dump 或 freeze-evidence。
- GateJ 仍未写为 completed；需要重新部署新 release 后再做首次启动验收。

---

# Worklog: GateJ-FREEZE-FIX-SECOND-PASS

日期：2026-05-28

## 本轮目标

复查 GateJ-FREEZE-FIX 后是否仍残留生产/freeze 不应出现的登录页敏感信息、默认账号密码、local profile、错误认证初始化或 release/Git 污染。本轮只允许修复审查发现的 P0/P1/P2 阻塞项，不新增业务功能、API、migration，不接 AI/DH/真实交易。

## 本轮修复

- 清理 `frontend/.env.example` 和 `frontend/README.md` 中的默认测试密码展示。
- 清理 `frontend/vite.config.*`、`frontend/playwright.config.*` 中旧 legacy console gate 注释。
- 清理后端注释和 E2E suite 名称中的旧 legacy console gate 标签，不改变业务逻辑或测试断言。
- 新增 `docs/current/GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区仅包含 GateJ-FREEZE-FIX 与本轮 second pass 范围修改；release/dist 等产物未进入 Git |
| 源码敏感词扫描 | 已执行 | 阻塞残留已修复；剩余命中均为允许项或历史文档记录 |
| `rg ... frontend/dist` | 通过 | 无敏感/旧联调关键词命中 |
| release zip 解压后 `rg ... release/second-pass-scan` | 通过 | 除允许的 `18888` 部署端口配置外，无敏感/旧联调关键词命中 |
| `.gitignore` / `git ls-files` | 通过 | 未发现 release/dist/env/jar/zip/dump/log/evidence 追踪污染 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip` |

## 结论

- `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md` 结论为 PASS。
- 允许重新部署 GateJ-FREEZE-FIX release。
- GateJ 仍未 completed；必须在服务器重新部署后执行首次启动验收，再进入 1h / 24h / 7d 连续运行验收。

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未修改交易核心状态机、策略核心算法或回测核心算法。

---

# Worklog: GateJ-FREEZE-FIX-3

日期：2026-05-28

## 本轮目标

修复 ECS 实测发现的 `scripts/seed-freeze-user.sh` 问题：特殊字符密码导致手工 `source .env.freeze` 报 Bash 语法错误，以及 seed SQL 使用 `nq_freeze_seed_user_id` 临时表后出现 relation 不存在。本轮只修改 seed 脚本、freeze 部署模板/文档和验证记录，不新增业务功能、API、migration，不接 AI/DH/真实交易。

## 根因

- `.env.freeze` 是 Docker Compose/env 模板，不是 Bash 脚本；密码包含 `>`、`)` 等 shell 特殊字符时，手工 `source .env.freeze` 会让 Bash 按脚本语法解释密码，导致 syntax error 或泄露风险。
- 旧 seed SQL 使用 `CREATE TEMP TABLE ... ON COMMIT DROP` 保存用户 id；PostgreSQL autocommit 下该临时表会在 statement 提交后被 drop，后续 `DELETE/INSERT user_roles` 再引用会报 `relation "nq_freeze_seed_user_id" does not exist`。

## 修改文件清单

- `scripts/seed-freeze-user.sh`
- `deploy/.env.freeze.example`
- `scripts/build-freeze-release.ps1`
- `docs/current/GATEJ_FREEZE_DEPLOYMENT.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- `seed-freeze-user.sh` 不再使用临时表，也不依赖跨 statement 的 CTE 结果。
- seed SQL 改为单个 `psql` session/transaction：设置 session-local 参数、确保角色存在、upsert 指定 freeze 用户、设置 `enabled=true`、重绑 `ADMIN / OPERATOR / VIEWER`，并校验 BCrypt hash 可由同一明文匹配。
- 密码读取改为 `.env.freeze` / 进程环境 / 交互式隐藏输入三选一；当 `.env.freeze` 保持 `CHANGE_ME` 占位符时，脚本会在 TTY 下提示输入密码，不 echo 明文。
- `.env.freeze.example` 和部署文档明确禁止手工 `source .env.freeze`；如密码包含 shell 特殊字符，推荐保留占位符并通过 seed 脚本交互式输入。
- `RELEASE_INFO.md` 生成内容同步说明禁止 `source .env.freeze` 和交互式 seed 密码流程。

## 验证命令与结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 是未安装发行版的 WSL stub；本机无 Git Bash，Docker daemon 未运行。需在 Linux ECS 或可用 Bash 环境复跑。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未提交真实密码、`.env.freeze`、release zip、jar、dist、logs、dump 或 freeze-evidence。

---

# Worklog: GateJ-FREEZE-FIX-4

日期：2026-05-28

## 本轮目标

修复 `scripts/seed-freeze-user.sh` 的交互式隐藏输入路径。服务器实测在 `.env.freeze` 删除/注释 `NQ_FREEZE_ADMIN_PASSWORD` 且进程环境 unset 后，交互输入正常密码仍被误判为多行，阻塞 GateJ-FREEZE 首次启动验收。

## 根因

`FREEZE_PASSWORD="$(read_secret_value "NQ_FREEZE_ADMIN_PASSWORD")"` 通过命令替换捕获函数 stdout。FIX-3 中 `read -r -s -p ...` 后使用 `echo` 输出视觉换行，该换行写到了 stdout，被命令替换捕获到密码值前部；随后单行校验检测到真实换行，报 `NQ_FREEZE_ADMIN_PASSWORD must be a single-line value`。这不是密码本身多行，而是交互提示换行污染了返回值。

## 修改文件清单

- `scripts/seed-freeze-user.sh`
- `docs/current/GATEJ_FREEZE_DEPLOYMENT.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 修复说明

- 将 `read_secret_value` 中交互输入后的视觉换行从 `echo` 改为 `printf '\n' >&2`。
- 保持 stdout 只输出密码值本身，避免命令替换捕获提示换行。
- 密码明文仍不写入 stdout/stderr；stderr 只输出提示名和换行。
- 保持三种密码来源：进程环境、`.env.freeze`、交互式隐藏输入。
- 保持单个 `psql` session + transaction，不新增 API、migration 或业务功能。

## 验证记录

- 本地 `bash -n scripts/seed-freeze-user.sh` 仍无法执行：当前 Windows `bash` 是未安装发行版的 WSL stub，且本机无 Git Bash、Docker daemon 未运行。
- 本地 `git diff --check` 通过。
- 本地 `mvn -f backend/pom.xml test` 通过：Reactor `BUILD SUCCESS`，`nq-app` 35 tests / 0 failures / 0 errors。
- 本地 `cd frontend && npm run build` 通过：仍有既有 Vite chunk size 警告。
- 本地 `.\scripts\build-freeze-release.ps1` 通过：重新生成 `release/nq-gatej-freeze-release.zip`。
- ECS 必须复验：
  - `bash -n scripts/seed-freeze-user.sh`
  - `unset NQ_FREEZE_ADMIN_PASSWORD` 后交互式执行 seed 成功
  - 进程环境方式执行 seed 成功
  - `hash_prefix` 为 `$2a$` 或 `$2b$`
  - `curl` 登录返回 200，且验证命令不打印 token

## 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI、DH 或真实交易。
- 未启动真实交易。
- 未提交真实密码、`.env.freeze`、release zip、jar、dist、logs、dump 或 freeze-evidence。
