# Testing

本文记录统一验证命令和当前基线验证结果。未执行的验证不能写成通过。

## 统一验证命令

### 后端验证

```powershell
mvn -f backend/pom.xml test
```

### 前端验证

```powershell
Set-Location frontend
npm ci
npm run build
npm run test:e2e
```

### Python 验证

首次本地验证前安装 dev 依赖：

```powershell
Set-Location research/py
python -m pip install -e ".[dev]"
```

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

### 本地启动验证

```powershell
docker compose up -d postgres
```

启动 `nq-app` local profile 后检查：

```powershell
Invoke-RestMethod http://localhost:18888/actuator/health
```

并检查：

- `POST /api/auth/login`
- `GET /api/auth/me`

## 本地 PostgreSQL 规则

- 本地 PostgreSQL 默认端口是 `5432`。
- 使用本机 PostgreSQL 时，不重复启动 `docker-compose postgres`。
- 使用 `docker-compose postgres` 时，确认本机 `5432` 未被占用。

## 本次实际验证记录

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含本次 docs/config 修改与 `git mv` 归档，详见 `WORKLOG.md` |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；local integration 日志确认连接 `jdbc:postgresql://localhost:5432/nexus_quant` |
| `npm ci` | 通过 | 首次因 `D:\Tool\NodeJs\node_cache` 写入权限/占用失败；提权重跑后成功安装 177 packages；`npm audit` 提示 4 个漏洞（2 moderate、2 high），本任务未执行 `npm audit fix` |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；Vite 提示 bundle chunk 超过 500 kB，属于既有构建体积风险 |
| `npm run test:e2e` | 通过 | BASELINE-FIX-2 后通过；8 个 Playwright 用例中 5 passed、3 skipped。E2E runner 会启动 Vite、设置外部 dev server 模式、运行 Playwright、最后停止 Vite |
| `python -m pip install -e ".[dev]"` | 未在当前环境完成 | 已在 `pyproject.toml` 补充 dev extras；当前本机 editable install 两次卡在 build/editable 阶段超时。为完成当前验证，使用等价工具安装命令补齐当前用户环境 |
| `python -m pip install pytest mypy ruff` | 通过 | 提权执行成功；下载较慢并发生断点续传，最终安装 `pytest-9.0.3`、`mypy-2.1.0`、`ruff-0.15.13` |
| `python -m pytest -q` | 通过 | `2 passed in 0.01s` |
| `python -m mypy src` | 通过 | `Success: no issues found in 8 source files` |
| `python -m ruff check .` | 通过 | `All checks passed!` |
| 本地启动验证 | 通过 | `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` 启动成功；`/actuator/health` 返回 `UP`；`POST /api/auth/login` 和 `GET /api/auth/me` 成功，当前默认账户恢复为 `rc1-admin-default / 900001` |

## 当前剩余风险

- 未执行 `docker compose up -d postgres`：当前本机已有 PostgreSQL `5432` 可用，后端测试和 local profile 均已连接该实例。
- `npm audit` 仍提示 4 个漏洞（2 moderate、2 high），后续单独处理。
- Vite build 仍提示 chunk 超过 500 kB，后续单独处理。
- E2E 中 3 个详情/交易链路用例按当前环境数据条件 skip，不代表对应业务链路已完整验证。

## GateH-1-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；新增 trading workspace 订单列表 controller 测试通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；E2E 后已停止监听 `18888` 的临时 Java 进程 |
| `npm run test:e2e` | 通过 | 10 个 Playwright 用例中 7 passed、3 skipped |

GateH-1 E2E 覆盖：

- `/trading` 正式交易工作台可进入。
- 页面显示正式账户上下文与 SIM / LIVE。
- 订单列表表格可加载，空态可见。
- 下单前检查抽屉展示风控摘要和服务端风控不可绕过状态。
- `/trade-validation` 旧路径仍可访问，并展示过渡入口提示。
- `E2E_TRADE_ORDER_ID` 未配置时，真实订单详情链路按原因 skip。

GateH-1 剩余验证风险：

- 当前本地没有配置 `E2E_TRADE_ORDER_ID`，因此订单详情真实数据链路未在本次 E2E 中执行，通过 skip 明确记录。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning 和 Vite chunk > 500 kB 警告仍存在，本轮不处理。

## GateH-2-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateH-2 migration、API、adapter bridge 与既有 local integration 均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `17` |
| `npm run test:e2e` | 通过 | 12 个 Playwright 用例中 9 passed、3 skipped；新增 `marketdata-bars-query-smoke` 与 `marketdata-ingestion-smoke` 均通过 |

GateH-2 E2E 覆盖：

- `/marketdata` 可打开。
- 页面展示 GateH-2 固定查询维度：OKX/BINANCE、SPOT、BTC-USDT、1m。
- K 线查询不报错，并展示 Bars 表格空态/数据态。
- 可通过页面创建 `marketdata_ingestion_jobs`。
- 可通过页面触发 `run-once`。
- 页面可查询 job/run 状态与运行结果。

GateH-2 交易所访问说明：

- 本轮 E2E 不依赖外网交易所稳定性。
- `run-once` 走本地后端真实 API 与 adapter 路径；当交易所接口返回空数据或外网不可用时，运行记录仍保存明确状态和统计。
- 本轮未执行真实生产交易所长时间回填或大范围历史数据下载。

GateH-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

## GateH-3-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateH-3 migration、dataset API、backtest dataset binding API、run snapshot 字段和既有回测链路均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `18` |
| `npm run test:e2e` | 通过 | 14 个 Playwright 用例中 10 passed、4 skipped；新增 `marketdata-dataset-smoke` 通过，`backtest-dataset-binding-smoke` 因当前本地库没有可绑定 backtest config 种子而 skip |

GateH-3 E2E 覆盖：

- `/marketdata` 可创建 dataset。
- dataset 可展示覆盖范围、状态、质量状态、bar/gap 统计。
- dataset 可触发 `refresh-quality`。
- `/backtests` 已提供 dataset 绑定入口。
- 当前本地库没有 `research_configs/backtest_configs` 种子，`backtest-dataset-binding-smoke` 未执行 UI 绑定提交；后端 controller 测试已覆盖 `PATCH /api/backtest-configs/{configId}/dataset`。

GateH-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

## GateI-PLAN 验证记录

日期：2026-05-18

本轮只修改文档，不重新执行全量后端、前端、Python 测试。

必须检查项：

- `git status --short --branch`：已执行，当前仅规划文档变更。
- `docs/current/PLAN_GATEI.md`：存在。
- `docs/current/GATEI_API_PLAN.md`：存在。
- `docs/current/GATEI_DB_PLAN.md`：存在。
- `docs/current/GATEI_FRONTEND_PLAN.md`：存在。
- `docs/current/GATEI_TEST_PLAN.md`：存在。
- `docs/current/GATEI_WORK_ORDER.md`：存在。
- `docs/current/STATUS.md`：已写清 GateH completed、当前执行 GateI-PLAN、AI 仍未开始。
- 未新增业务代码、migration、API 实现或前端页面实现。
- 未接入 AI。

沿用当前验证基线：

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- Python `pytest`、`mypy`、`ruff` 已通过。

## GateI-1-WO 验证记录

日期：2026-05-18

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；新增策略版本 service 测试、发布绑定 service 测试、既有 local integration 测试均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `19` |
| `npm run test:e2e` | 通过 | 16 个 Playwright 用例中 13 passed、3 skipped；新增 `strategy-version-smoke` 与 `publish-version-smoke` 均通过 |

GateI-1 E2E 覆盖：

- `/strategies` 可打开并查询策略定义。
- 当本地库缺少策略定义时，E2E 通过正式 `POST /api/strategies` 创建最小 SIM 策略定义 fixture。
- 策略详情可展示“策略版本”和“创建策略版本”区域。
- 可创建 `ACTIVE` 策略版本，并展示参数快照、配置快照和状态。
- `/publishes` 可展示策略版本 ID 与版本快照入口。

GateI-1 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

GateI-1 边界确认：

- 未进入 GateI-2/3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未修改策略核心算法、交易核心状态机或回测核心算法。

## GateI-2-WO 验证记录

日期：2026-05-19

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateI-2 migration V20、回测配置绑定、run 快照固化、evaluation 指标增强和既有 local integration 均通过 |
| `npm ci` | 通过 | 恢复前端依赖；原因是本地 `node_modules/typescript` 目录不完整导致首次 build 找不到 `typescript/bin/tsc`；命令完成后仍有 4 个 npm audit 告警，本轮不处理 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run '-Dspring-boot.run.profiles=local'` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `20` |
| `npm run test:e2e` | 通过 | 全量 Playwright 17 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateI-2 backtest/evaluation 主链 |

GateI-2 E2E 变更：

- 新增 `frontend/tests/e2e/backtest-config-enhanced-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/evaluation-report-enhanced-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/gatei2-fixtures.ts`，通过正式 API 导入本地 fixture bars、创建 dataset、strategy version、research config、backtest config、run 和 evaluation，不依赖外网交易所。
- 更新 `frontend/tests/e2e/support.ts`，按账户 alias 解析真实 `exchangeAccountId`，避免本地自增 ID 漂移导致登录前置失败。
- 本地验证库补入 E2E legacy strategy account 种子 `accounts.account_id=3001`，用于满足既有 `strategy_definitions.account_id` 外键；该操作不是 migration，不进入产品数据结构。

GateI-2 E2E 已覆盖：

- `/backtests` 页面展示 strategy version / dataset 追溯信息。
- 回测配置详情展示 strategy version snapshot、param snapshot、dataset snapshot、config snapshot。
- 回测运行详情展示 run 级 strategy version snapshot、dataset snapshot、param snapshot、config snapshot。
- `/evaluations` 页面展示 total return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON。
- 无数据时页面保留明确 empty 状态。

GateI-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未配置 `E2E_TRADE_ORDER_ID`，既有交易订单详情 E2E 仍按明确原因 skip；不影响 GateI-2 主链。

GateI-2 边界确认：

- 未进入 GateI-3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增 SIM/Paper Trading 运行闭环。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateI-3-WO 验证记录

日期：2026-05-19

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateI-3 Flyway V21 编译通过；新增 `PaperTradingRunServiceTest` 4 个用例覆盖创建、启动、停止、状态拒绝；既有 35 个 nq-app suite 测试全通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |

GateI-3 E2E 说明：

- 新增 `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`，覆盖：Paper Trading 页面打开、列表查询、创建 Paper run、启动 Paper run、停止 Paper run、查看 orders/trades/positions 空态、查看快照标签。
- 新增 `frontend/tests/e2e/paper-trading-fixtures.ts`，通过正式 API 完整链路准备 fixture：fixture bars 导入 → strategy → strategy version → research config → backtest config → strategy version 绑定 → backtest run → start → evaluate → publish；最终返回可用的 `publishId`。
- E2E 不依赖外网交易所；不调用真实 LIVE 下单接口。
- E2E 需要后端 local profile 启动且 Flyway 到 V21；本轮提交前未在干净本地 5432 实例上执行该完整 E2E（具体执行需要先启动后端、确保 fixture 账户种子 3001 存在）。

GateI-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未在本轮启动后端 local profile 并执行 `npm run test:e2e`；E2E spec 已就绪，等待 GateI-3-FIX 或下次完整本地验证窗口执行。

GateI-3 边界确认：

- 未进入 GateI-4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未调用真实交易所下单接口。

## GateI-3-FIX 验证记录

日期：2026-05-20

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests，0 failures |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway V21 已应用 |
| `npm run test:e2e` | 通过 | 18 passed / 1 skipped |

GateI-3-FIX 修复内容：

- `paper-trading-run-smoke.spec.ts`：`getByLabel('发布 ID')` → `getByPlaceholder('发布记录 ID（publishId）')`，修复 Ant Design Form.Item label 关联问题。
- `paper-trading-run-smoke.spec.ts`：Modal OK 按钮从 `getByRole('button', {name: '确 定'})` → `getByRole('button', {name: 'OK', exact: true})`，修复无中文 locale 时按钮文本为 "OK" 且与 "OKX" 冲突。
- `paper-trading-run-smoke.spec.ts`：移除 `waitForResponse` 对 GET 列表刷新的显式等待，改用 `await expect(row).toBeVisible({timeout: 15_000})` 等待 UI 更新。
- `paper-trading-run-smoke.spec.ts`：Drawer 内断言从 `page.getByText('Paper Run ID')` → `page.getByLabel('Paper Trading 详情').getByText('Paper Run ID')`，避免与表头重复元素冲突。
- `paper-trading-run-smoke.spec.ts`：按钮选择器使用 `.or()` 兼容 `getByRole('link')` 和 `getByRole('button')`，适配 Ant Design Table 内 `type="link"` 按钮的实际 role。

GateI-3-FIX E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run（POST /api/paper-trading/runs 返回 CREATED + 快照绑定）。
- 可启动 Paper run（POST .../start 返回 RUNNING）。
- 可停止 Paper run（POST .../stop 返回 STOPPED）。
- 详情抽屉可打开，展示 Paper Run ID、状态、快照。
- 订单/成交/持仓标签页展示明确空态。
- 快照标签页展示 Publish Snapshot 和 Strategy Version Snapshot。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。
- 使用本地 account_id=3001 种子。

GateI-3-FIX skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI-3 主链。

GateI-3-FIX 结论：

- GateI-3-WO + GateI-3-FIX 已完成。
- 后端测试通过、前端 build 通过、E2E 18 passed / 1 skipped。
- 允许进入 GateI-4-WO，但只能在本轮变更审查/提交后单独开工。
- GateI-4 只能做风控回写、资金曲线、持仓曲线、交易复盘与异常停机，不能夹带 AI。

## GateI-4-WO 验证记录

日期：2026-05-20

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；35 tests / 0 failures，含 PaperTradingMonitorServiceTest 5 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `npm run test:e2e` | 未执行 | 本轮未启动本地后端 local profile；spec 已扩展，等待 GateI-4-FIX 窗口执行 |

GateI-4 新增测试覆盖：

- `PaperTradingMonitorServiceTest`：5 个用例覆盖 runRiskCheckOnce 正常写入、listRiskResults 空态、emergencyStop APPLIED（RUNNING → STOPPED）、emergencyStop FAILED（非 RUNNING）、listEmergencyStops 空态。
- E2E spec 已扩展 GateI-4 链路（风控检查 / 5 个新 Tab / 紧急停机），待本地后端启动后执行。

GateI-4 skipped 说明：

- E2E 未执行：本轮未启动本地后端 local profile + Flyway V22，spec 已就绪。

GateI-4 结论：

- 后端测试通过、前端 build 通过。
- E2E 待 GateI-4-FIX 窗口执行。
- GateI 仍未整体完成；不创建 `docs/gates/gate-i`。

## GateI-4-FIX 验证记录

日期：2026-05-21

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，35 tests / 0 failures |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `22` |
| 5 张 GateI-4 表存在 | 通过 | `paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events` 全部存在 |
| `npm run test:e2e` | 通过 | 19 passed / 1 skipped；新增 GateI-4 monitor smoke 用例通过 |

GateI-4-FIX 修复内容：

- 改 GateI-4 E2E 用例：从 `request` fixture 调用 API（不共享 token）改为通过 UI 操作完成全链路。
- 改 PaperTradingPage：将"执行风控检查"和"紧急停机"按钮从 `PaperListSection` children 移到外层（空态时仍可见）。
- 改 Modal 调用方式：`Modal.confirm` → `App.useApp().modal.confirm`，确保在 App context 下正确渲染。
- 修复 PASSED 文本断言：使用 `.first()` 避免多元素冲突。

GateI-4-FIX skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI 主链。

GateI-4-FIX 结论：

- GateI-4-WO + GateI-4-FIX 已完成。
- GateI 全部子阶段已完成：GateI-1-WO → GateI-2-WO → GateI-3-WO → GateI-3-FIX → GateI-4-WO → GateI-4-FIX。
- **GateI completed。**
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- GateJ 不是 AI 阶段；AI 最早 GateK 才允许进入信号层。
