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

## DB Schema Credential Revocation Governance Batch 5-B 验证记录（2026-06-07）

本轮新增 `V29__schema_credential_revocation_governance.sql` 并同步 credential revocation / DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 新增 migration 范围检查 | 通过 | 本轮只新增 `backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql`，未修改历史 migration。 |
| 禁止范围扫描 | 通过 | 未修改 Java、Repository、Service、Controller、DTO、前端、Python 或部署脚本；未新增 API；未实现 revoke/rotate endpoint；未接 AI、DH、LIVE 或真实交易。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；该结果只证明当前后端测试和 Flyway 迁移装配通过，不代表 revoke/rotate 业务行为已实现。 |

## DB Schema Governance Batch 4-B 验证记录（2026-06-07）

本轮为 `research_configs` / `backtest_configs` 增加受控归档命令；未新增 migration，未修改历史 migration。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 禁止范围扫描 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff，未新增 migration；新增代码只触达 research/backtest config archive 命令、DTO、Repository、Service、Controller 和测试。文档中出现 credentials、positions、risk_events、orders/trades/ledger/audit、facts、marketdata timeseries、AI、DH、LIVE、真实交易等词均为禁止范围或历史边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 4-A 验证记录（2026-06-07）

本轮接管 `research_configs` / `backtest_configs` V28 status/archive 字段的 Repository 与 Service 语义；未新增 migration，未修改历史 migration。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 禁止范围扫描 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff，未新增 migration；代码改动只触达 research/backtest 配置 domain、Repository、Service、DTO 和测试。文档中出现 credentials、positions、risk_events、orders/trades/ledger/audit、facts、marketdata timeseries、AI、DH、LIVE、真实交易等词均为禁止范围或历史边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 3-B 验证记录（2026-06-06）

本轮新增 `V28__schema_research_backtest_config_governance.sql` 并同步 DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| V28 禁止范围扫描 | 通过 | 新 migration 未命中禁止表名、AI、DH、LIVE、真实交易、逻辑删除或 retention purge 相关结构变更；只命中两张目标配置表自身的约束名。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 3-A 验证记录（2026-06-06）

本轮新增 `V27__schema_master_table_governance.sql` 并同步 DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| V27 禁止范围扫描 | 通过 | 未命中禁止表、事件、时序、AI、DH、真实交易、逻辑删除或 retention 相关结构变更。 |
| `mvn -f backend/pom.xml test` | 初次失败后修复重跑通过 | 初次在 `nq-app` 暴露既有 package/path 不一致问题；已修复 `TradingMaintenanceService`、`ManualStrategyTriggerGateway`、`OrderCommandStrategyExecutionGateway` 的 package/import。 |
| `mvn -f backend/pom.xml clean test` | 通过 | 清理旧 package 残留 class 后，23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。 |
| `mvn -f backend/pom.xml test` | 通过 | 修复后按用户要求重跑原命令，23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。 |

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

## GateJ-FREEZE-FINAL-DOC 验证记录（2026-06-05）

本轮只做最终验收文档整理和 `docs/gates/gate-j` 冻结快照，不执行 build/deploy/restart，不修改后端/前端业务代码、API、migration、脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| GateJ-FREEZE 30m observation | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 1h acceptance | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 24h acceptance | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 7d acceptance | PASS | 7d checkpoint 为 2026-06-05 14:53:24 +08:00；health-loop 最新样本为 2026-06-05 15:40:58 +08:00。 |
| health-loop 样本数 | 2025 | 起点为 2026-05-29 14:53:20 +08:00。 |
| 168h nq-app 错误补扫 | 通过 | `docker compose logs --since=7d` 不被当前 Compose 识别，已补跑 `--since=168h`；`nq-app-error-scan-168h.txt` 的 `wc -l = 0`。 |
| 18888 health | UP | freeze 后端 health 正常。 |
| 5179 health | UP | freeze 前端 health 正常。 |
| nginx / nq-app / postgres | Up 7 days | postgres 为 healthy。 |
| after-7d.sql | 已生成 | 文件大小 266K；不进入 Git 冻结快照。 |
| 5179 安全组 | 通过 | 已确认只允许本人 IP 访问。 |
| UI/UX smoke review | Functional stability PASS；UI/UX professionalism FAIL | 不影响 GateJ-FREEZE 稳定性验收；登记为 post-freeze remediation。 |
| build/deploy/restart | 未执行 | 用户明确禁止，本轮只做文档冻结。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮未改业务代码、前端代码、API、migration、脚本或部署配置；不执行 build/deploy/restart。 |

边界确认：

- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- GateK not started；Next 仅为 GateK-PLAN。
- No new backend/frontend business code, API, migration, deployment, AI, DH, or real trading path was added in this final documentation stage。

## Codex Workflow 文档固化验证记录（2026-06-06）

本轮只新增和更新 Codex 插件路由、工作流、任务模板、Project Instructions 与索引文档，不修改后端/前端业务代码、API、migration、Python、脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| 同名文档存在性检查 | 已执行 | 目标 4 个新文档此前不存在，本轮新建；`docs/current/README.md` 已存在，本轮追加入口。 |
| `docs/current/README.md` 链接检查 | 已执行 | 已追加 `AGENTS.md`、插件工作流、Router Skill、任务模板、Project Instructions 的相对链接入口。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 禁止范围检查 | 已执行 | 明确禁止 LIVE trading、真实下单/撤单路径、真实 DH 接入、real provider、RealClient、credentials 泄露。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown 文档，未修改业务代码、API、migration、前端页面、Python 或部署配置。 |

## Codex Workflow 文档一致性小修验证记录（2026-06-06）

本轮只修复 Codex Workflow Router Skill 状态表述和 Project Instructions 前置规则，不修改后端/前端业务代码、API、migration、Python、部署脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 用于确认本轮 Markdown 文档变更范围。 |
| `git diff --check` | 已执行 | 用于检查空白错误。 |
| Router Skill 状态表述检查 | 已执行 | `NQ_DH_WORKFLOW_ROUTER_SKILL.md` 已写明 `nq-dh-workflow-router` 当前按 `AGENTS.md` 作为 active skill 使用。 |
| Project Instructions 前置规则检查 | 已执行 | `CODEX_PROJECT_INSTRUCTIONS.md` 已补充 `nq-dh-workflow-router` 前置分类、范围限定和固定输出字段。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。 |

## Codex Workflow 输出字段口径小修验证记录（2026-06-06）

本轮只统一 Codex Workflow 标准输出字段，将必填输出字段统一为 `Findings`，不再把 `Summary` 作为必填字段；不修改后端/前端业务代码、API、migration、Python、部署脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 用于确认本轮 Markdown / Skill 文档变更范围。 |
| `git diff --check` | 已执行 | 用于检查空白错误。 |
| 输出字段口径检查 | 已执行 | `AGENTS.md`、`.agents/skills/nq-dh-workflow-router/SKILL.md`、`NQ_DH_CODEX_PLUGIN_WORKFLOW.md`、`NQ_DH_WORKFLOW_ROUTER_SKILL.md`、`CODEX_PROJECT_INSTRUCTIONS.md` 的标准输出格式均使用 `Findings`。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown / Skill 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。 |

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

## GateJ-PLAN 验证记录

日期：2026-05-21

本轮只修改文档，不重新执行全量后端、前端、Python 测试。

沿用 GateI completed 验证基线：

- 后端 `mvn -f backend/pom.xml test`：35 tests / 0 failures。
- 前端 `npm run build`：通过。
- E2E `npm run test:e2e`：19 passed / 1 skipped。
- Python `pytest`、`mypy`、`ruff`：通过。

本轮只改文档，未跑全量测试原因：无业务代码变更、无 migration 变更、无 API 变更、无前端页面变更。

GateJ 测试规划入口为 [GATEJ_TEST_PLAN.md](./GATEJ_TEST_PLAN.md)。

GateJ 规划 E2E 矩阵：

- paper-schedule-smoke
- paper-heartbeat-smoke
- paper-daily-report-smoke
- paper-alert-smoke
- paper-recovery-smoke
- paper-stability-check-smoke

GateJ 规划连续运行验收：

- 1 小时短验收
- 24 小时中验收
- 7 天稳定性验收

## GateJ-1-WO 验证记录

日期：2026-05-21

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests / 0 failures；含 PaperRunScheduleServiceTest 11 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `23` |
| `npm run test:e2e` | 通过 | 20 passed / 1 skipped；新增 paper-trading-schedule-smoke 通过 |

GateJ-1 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 调度计划 Tab 可展示空态。
- 可创建调度计划（ENABLED 状态）。
- 可执行一次调度（run-once），fire 记录为 SUCCEEDED。
- 可查看触发记录。
- 可禁用调度（DISABLED）。
- 心跳 Tab 可展示空态。
- 可执行心跳检查（run-once），heartbeat 状态为 OK。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。

GateJ-1 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-1 主链。

GateJ-1 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。

GateJ-1 边界确认：

- 未进入 GateJ-2/3/FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增日报、告警、恢复、稳定性验收。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateJ-2-WO 验证（2026-05-21）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests / 0 failures；含 PaperRunMonitorServiceTest 12 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `24` |
| `npm run test:e2e` | 通过 | 22 passed / 1 skipped；新增 paper-trading-daily-report-smoke / paper-trading-alert-smoke 通过 |

GateJ-2 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 日报 Tab 可展示空态。
- 可生成今日日报（status = GENERATED）。
- 可重复生成同一日期日报（幂等）。
- 告警 Tab 可展示空态。
- 可创建测试告警（SYSTEM_NOTICE / LOW / OPEN）。
- 可确认告警（OPEN → ACKED，acknowledgedBy 写入）。
- 可解决告警（ACKED → RESOLVED，resolvedAt 写入）。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。

GateJ-2 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-2 主链。

GateJ-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning。

GateJ-2 边界确认：

- 未进入 GateJ-3 / GateJ-FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增恢复、稳定性验收、外部通知（邮件、Slack、钉钉）。
- 未引入图表库。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateJ-3-WO 验证（2026-05-22）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS；新增 PaperRunRecoveryServiceTest 9 用例、PaperRunStabilityCheckServiceTest 10 用例、PaperRunMonitorRunServiceTest 8 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `25` |
| `npm run test:e2e` | 通过 | 24 passed / 1 skipped；新增 paper-trading-recovery-smoke / paper-trading-stability-check-smoke 通过 |

GateJ-3 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 恢复事件 Tab 可展示空态。
- 可执行恢复（MANUAL_RECOVER），写入 recovery event。
- 可执行重试失败步骤（RETRY_FAILED_STEP），写入 recovery event。
- 可执行监控守护一次（HEARTBEAT_LAG 自动告警最小落库）。
- 告警 Tab 可看到 HEARTBEAT_LAG 自动告警。
- 稳定性验收 Tab 可展示空态。
- 可生成最近 24h 稳定性验收（无心跳 → FAILED，验证第一版口径）。
- 同窗口重复生成幂等。
- 不依赖外网交易所，不调用真实 LIVE 下单。

GateJ-3 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-3 主链。

GateJ-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning。
- 未执行 GateJ-FREEZE 的 1h/24h/7d 连续运行验收（属 GateJ-FREEZE 范围）。

GateJ-3 边界确认：

- 未进入 GateJ-FREEZE 正式验收归档。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）。
- 未做自动恢复策略引擎。
- 未调用真实 LIVE 下单接口。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未引入图表库。

## PRE-FREEZE-CODE-AUDIT 验证记录（2026-05-22）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 docs/current 与根目录入口文档变更，无业务代码、migration、API 实现、前端页面实现变更 |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，0 failures / 0 errors（archunit ModuleBoundaryArchTest 6 用例 + PackageBoundaryArchTest 1 用例通过；nq-app suite 35 全通过；Paper 单元测试 PaperTradingRunService 4 + PaperTradingMonitorService 5 + PaperRunScheduleService 11 + PaperRunMonitorService 12 + PaperRunRecoveryService 9 + PaperRunStabilityCheckService 10 + PaperRunMonitorRunService 8 全部通过）|
| `npm run build` | 通过 | `tsc -b && vite build` 成功；dist/index.js ≈ 1.48 MB（gzip 446 kB）；仍有 chunk > 500 kB 警告 |
| `npm run test:e2e` | 本轮未实际执行 | 沿用 GateJ-3-WO 24 passed / 1 skipped 通过基线；P1-1 要求 GateJ-FREEZE 入场前补跑（启动后端 local profile + 5432 + 种子 `account_id=3001` 后执行）|
| `python -m pytest -q` | 本轮未实际执行 | 当前 shell `python.exe` 仅 Windows App Execution Alias stub，调用 exit 49；沿用 BASELINE-FIX-2 / GateJ-3 通过基线；P1-2 要求 GateJ-FREEZE 入场前在真实 Python 环境补跑 |
| `python -m mypy src` | 本轮未实际执行 | 同上；P1-2 |
| `python -m ruff check .` | 本轮未实际执行 | 同上；P1-2 |

未跑验证不写成通过：本轮未执行的 E2E 与 Python 三件套均明确标记为「未在本轮重跑」，并通过 PRE_FREEZE_AUDIT_FIX_PLAN.md P1-1 / P1-2 列入 GateJ-FREEZE 入场前必做项。

PRE-FREEZE-CODE-AUDIT 结论：

- 后端单元测试全部通过；前端 build 通过。
- 文档、代码、DB、API、前端、E2E spec、Python 模块、Paper/LIVE 隔离、AI 边界、模块边界一致。
- 无 P0 阻塞性问题。
- P1 共 4 条：P1-1 / P1-2 是 GateJ-FREEZE 入场前必做的验证补跑；P1-3 不阻塞；P1-4 已闭环。
- 允许进入 GateJ-FREEZE，但 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## PRE-FREEZE-CODE-AUDIT second pass 验证记录（2026-05-22）

本轮由 Codex 执行二次审查与实际验证。未修业务代码，未新增 API / migration / 前端页面实现，未接 AI，未执行 GateJ-FREEZE 1h/24h/7d 连续运行验收。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` suite `35 tests / 0 failures / 0 errors / 0 skipped`；Paper 相关 service 测试均通过 |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；`dist/assets/index-CLLFLWD4.js` 约 1,478.51 kB（gzip 446.09 kB）；Vite chunk > 500 kB 警告仍存在，作为 P2 |
| `cd frontend && npm run test:e2e` | 通过 | 后端 local profile 启动成功，`/actuator/health` 返回 `UP`，Flyway 当前版本 `25`；完整 Playwright 25 tests total，24 passed / 1 skipped / 0 failed |
| `cd research/py && python -m pytest -q` | 通过 | 使用真实 Python 解释器执行；`2 passed in 0.03s` |
| `cd research/py && python -m mypy src` | 通过 | `Success: no issues found in 8 source files` |
| `cd research/py && python -m ruff check .` | 通过 | `All checks passed!` |

E2E skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有订单详情链路，不影响 GateJ 主链。
- GateJ 主链 smoke 已全部执行并通过：schedule/heartbeat、daily report、alert、recovery、stability check、monitor run-once。

环境说明：

- 默认 shell `python` 指向 `C:\Users\lingy\AppData\Local\Microsoft\WindowsApps\python.exe`，不是可用解释器；本轮使用 workspace bundled Python 临时置于 `PATH` 首位后执行同样的 `python -m ...` 命令。
- 首次 E2E 启动后端时遇到 Maven 本地仓库目录冲突；提权重跑后该问题消失。随后一次 PowerShell 参数引用错误导致 Maven 将 profile 参数误识别为 lifecycle phase；修正引用后后端启动与完整 E2E 均通过。上述两次失败未进入业务 E2E 断言，不计为业务功能失败。

PRE-FREEZE-CODE-AUDIT second pass 结论：

- 后端、前端 build、完整 E2E、Python pytest/mypy/ruff 均已实际执行并通过。
- API / DB / Paper-LIVE 隔离 / AI 边界二次抽查未发现 P0/P1。
- 允许进入 GateJ-FREEZE，但 GateJ-FREEZE 必须单独开工，只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## AUDIT-FIX 验证记录（2026-05-26）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 AUDIT-FIX 范围文件变更，外加上一轮新增安全审查报告 |
| `git diff --stat` | 已执行 | 用于确认变更范围 |
| `git diff -- scripts/gated_okx_dome_verify.ps1 docs/archive/scripts/gated_okx_dome_verify.ps1 frontend/playwright.config.ts frontend/tests/e2e/run-e2e.mjs docs/current/API.md docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md` | 已执行 | 用于确认 P1 stub / 归档、E2E 端口与文档事实源变更 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 chunk > 500 kB 警告 |
| `cd frontend && npm run test:e2e` | 通过 | 首次在后端未启动时失败于 `127.0.0.1:18888 ECONNREFUSED`；启动后端 local profile 后复跑通过，Vite 监听 `127.0.0.1:5179`，结果 24 passed / 1 skipped / 0 failed |

端口修复说明：

- `4173` 位于当前 Windows TCP excluded range `4141-4240` 内，会导致 Vite 监听 `127.0.0.1:4173` 返回 `EACCES`。
- E2E/Vite 端口统一调整为 `5179`，Playwright `baseURL`、run-e2e 启动参数、Vite dev / preview 默认端口和 `.env.example` 保持一致。
- 唯一 skipped 用例仍为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，不影响 GateJ 主链。

## GateJ-FREEZE-FIX 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 Vite chunk > 500 kB 警告 |
| `rg -n "<redacted-local-test-password>\|18888\|legacy console gate\|/api/auth/login\|<redacted-authorization-header-prefix>" frontend/dist` | 通过 | 无命中；`rg` 返回 1 表示未找到匹配项 |
| `rg -n "/api/auth/me" frontend/dist` | 通过 | 无命中；额外确认登录页不再暴露当前用户接口路径 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 首次因沙箱无法写入本机 Maven repository tracking file 失败；提权重跑成功，生成 `release/nq-gatej-freeze-release.zip` |
| `jar tf backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar \| Select-String application-freeze.yml` | 通过 | jar 内包含 `BOOT-INF/classes/application-freeze.yml` |

脚本语法说明：

- 当前 Windows 环境只有 `C:\WINDOWS\system32\bash.exe`，调用 `bash -n` 会进入 WSL 未安装提示，未能在本机执行 bash 语法检查。
- `seed-freeze-user.sh` 已通过文本审查、release 包纳入检查和服务器执行流程文档约束；最终 shell 运行需在 Linux ECS 上随重新部署验证。

本轮未执行：

- 未重新执行 `npm run test:e2e`：本轮改动限定在登录页展示、freeze profile、部署脚本与 freeze 文档；按任务验收要求执行了后端测试、前端 build、dist 敏感串扫描和 release 打包。
- 未执行 Python `pytest/mypy/ruff`：本轮未修改 `research/py`。

## GateJ-FREEZE-FIX-SECOND-PASS 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含 GateJ-FREEZE-FIX 与本轮 second pass 文档/注释/测试描述清理；未提交 release/dist/env/jar/zip/dump/log/evidence |
| 源码敏感词扫描 | 已执行 | 阻塞残留已修复；剩余命中均为允许项或历史文档记录，详见 `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md` |
| `rg ... frontend/dist` | 通过 | 无敏感/旧联调关键词命中 |
| release zip 解压后 `rg ... release/second-pass-scan` | 通过 | 除允许的 `18888` 部署端口配置外，无敏感/旧联调关键词命中 |
| `.gitignore` 检查 | 通过 | release/dist/target/env/log/dump/evidence 已覆盖 |
| `git ls-files` 污染检查 | 通过 | 未发现不该追踪的 release/dist/env/jar/zip/dump/log/evidence |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip` |

## GateJ-FREEZE-FIX-3 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 指向未安装 Linux 发行版的 WSL stub；本机未安装 Git Bash，Docker daemon 也未运行，无法获得可用 Bash。脚本已按 Bash 语法静态审查，需在 Linux ECS 或可用 Bash 环境复跑该命令。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`，release info 已包含禁止 `source .env.freeze` 与交互式 seed 密码说明。 |

GateJ-FREEZE-FIX-3 变更限定在 seed 脚本、freeze env 模板、freeze 部署文档、release info 和当前事实源文档；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

## GateJ-FREEZE-FIX-4 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 仍指向未安装 Linux 发行版的 WSL stub；本机未安装 Git Bash，Docker daemon 未运行，无法获得可用 Bash。ECS 或可用 Bash 环境必须复跑。 |
| `unset NQ_FREEZE_ADMIN_PASSWORD` 后交互式执行 seed | 待 ECS 复验 | 本轮修复点是 `read -s -p` 后的视觉换行改写 stderr，避免命令替换捕获换行并误判多行；需在 Linux ECS 上用真实 TTY 复验。 |
| 进程环境方式执行 seed | 待 ECS 复验 | 当前本机无运行中的 freeze PostgreSQL 容器，需在 ECS 上复验。 |
| `hash_prefix` 为 `$2a$` 或 `$2b$` | 待 ECS 复验 | 需在 ECS PostgreSQL 容器内查询，禁止输出完整 hash。 |
| `curl` 登录 200 且不打印 token | 待 ECS 复验 | 需在 ECS 本机验证并只输出 HTTP status。 |

ECS 建议复验命令：

```bash
cd /opt/nexus-quant
bash -n scripts/seed-freeze-user.sh

unset NQ_FREEZE_ADMIN_PASSWORD
# 确保 .env.freeze 中 NQ_FREEZE_ADMIN_PASSWORD 缺失、注释或保留 CHANGE_ME 占位符，再交互式输入验收密码。
bash scripts/seed-freeze-user.sh

NQ_FREEZE_ADMIN_PASSWORD='<single-line-password>' bash scripts/seed-freeze-user.sh

docker compose --env-file .env.freeze -f docker-compose.freeze.yml exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT substring(password_hash from 1 for 4) AS hash_prefix FROM users WHERE username = '${NQ_FREEZE_ADMIN_USERNAME}' AND enabled = TRUE;"

status="$(
  curl -sS -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"${NQ_FREEZE_ADMIN_USERNAME}\",\"password\":\"<single-line-password>\"}" \
    'http://127.0.0.1:18888/api/auth/login'
)"
test "$status" = "200"
```

本轮本地可验证项：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |

## GateJ-FREEZE-FIX-5 验证记录（2026-05-29）

本轮修复 release 包内 `.sh` CRLF 换行导致 ECS Bash 解析 `set -euo pipefail` 失败的问题。修复范围限定在换行策略、release 打包脚本和当前事实源文档；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| 仓库 `scripts/*.sh` CRLF 字节检查 | 通过 | `backup-db.sh`、`deploy-freeze.sh`、`freeze-health-loop.sh`、`health-check.sh`、`seed-freeze-user.sh` 均为 `HasCRLF=False`。 |
| `git diff --check` | 通过 | 无空白错误；仅有 `build-freeze-release.ps1` 将按 `.gitattributes` 维持 CRLF 的 Git 提示。 |
| `mvn -f backend/pom.xml test` | 通过 | 首次 120s 超时未得出测试失败结论；提高超时后复跑通过，Reactor `BUILD SUCCESS`，23 个 backend module `SUCCESS`，`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`；打包脚本在 zip 前对 staging `scripts/*.sh` 做 LF 归一化兜底。 |
| release zip 解压后 CRLF 检查 | 通过 | 解压到本机临时目录后，zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`30,979,533` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
bash scripts/backup-db.sh before-freeze
nohup bash scripts/freeze-health-loop.sh > /opt/nexus-quant/freeze-evidence/health/freeze-health-loop.out 2>&1 &
grep -n '"status":"UP"\|UP' /opt/nexus-quant/freeze-evidence/health/health-check-7d.log | tail
```

结论：本地 release 可复现性已修复；ECS 尚未在本轮环境执行，未完成 ECS 复验前不得进入 GateJ-FREEZE 首次启动验收。

## GateJ-FREEZE-FIX-6 验证记录（2026-05-29）

本轮修复 ECS freeze 控制台点击 Instrument Catalog “同步 Catalog”后因 Binance `exchangeInfo` 返回 451 被抛成 500 的问题，并清理生产/freeze 可见页面中的旧阶段与本地环境文案。修复范围限定在 freeze 验收阻塞问题；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-api,nq-scheduler -am test` | 通过 | 覆盖 `/api/instruments/sync` 409 受控错误与 `AdapterInstrumentCatalogSyncService` 禁用/外部异常转换测试。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |
| `frontend/dist` 禁止串扫描 | 通过 | 未命中 `GateG`、`GateH-PRE`、`ChangeMe123`、`admin / ChangeMe123`、`/api/auth/login`、`/api/auth/me`、`Authorization: Bearer`。 |
| release zip 解压后禁止串扫描 | 通过 | 解压目录未命中上述禁止串。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`30,980,280` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml restart nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
# 浏览器进入 Instrument Catalog：查询允许为空；点击同步 Catalog 不得显示 internal server error。
# 后端日志不得出现：api_unhandled_exception path=/api/instruments/sync
```

结论：本地已修复 freeze release 中 Instrument Catalog sync 的 500 风险与前端旧文案残留；ECS 尚未在本轮环境执行，未完成 ECS 复验前不得进入 GateJ-FREEZE 首次启动验收。

## GateJ-FREEZE-FIX-7 验证记录（2026-05-29）

本轮修复 freeze 控制台旧 Gate 文案、开发接口说明和不专业筛选控件。修复范围限定在前端 UI 展示与筛选控件；未新增 API、migration 或后端业务流程，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd frontend && npm run build` | 通过 | 首次因 `PaperTradingPage` 漏加 `Select` import 失败，补齐后通过；仍有既有 Vite chunk > 500 kB 警告。 |
| `frontend/dist` 残留扫描 | 通过 | 大小写敏感扫描未命中 `GateH-1`、`GateH-2`、`GateI-3`、`GateH-PRE`、`GateG`、`LOCAL`、`Gate-3`、`GET /api`、`POST /api`、`publishId 过滤`、`本地筛选字段`、`真实请求参数`。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 首次因沙箱无法写入本机 Maven repository tracking file 失败；提权重跑通过并重新生成 release zip。 |
| release zip 解压后 frontend/dist 残留扫描 | 通过 | 解压目录 `frontend/dist` 未命中上述旧 Gate / LOCAL / 开发接口说明残留。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`31,014,538` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml up -d --force-recreate nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
curl -fsS http://127.0.0.1:5179/actuator/health
```

浏览器复验：

- 页面不再出现旧 Gate / LOCAL / API 开发说明残留。
- 重点页面枚举筛选项为 Select，时间字段为 DatePicker。
- Instrument Catalog “同步 Catalog” 仍显示受控提示，不显示 internal server error。
- 后端日志不得出现 `ERROR` / `Exception` / `api_unhandled_exception path=/api/instruments/sync`。

结论：本地 release 已可上传 ECS 复验；ECS 浏览器与日志复验通过前不得进入 GateJ-FREEZE 首次启动验收。
