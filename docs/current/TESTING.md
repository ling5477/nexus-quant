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
