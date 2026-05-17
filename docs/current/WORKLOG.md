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
