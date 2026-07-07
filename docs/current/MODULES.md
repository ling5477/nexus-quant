# Current Modules

本文记录当前模块 owner 和职责边界。当前阶段为 GateR `FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；下一阶段唯一推荐主线是 GateS，GateS-0 为 `PLAN / NOT IMPLEMENTED`（规划 / 未实现），GateS-1 为 `NEXT / NOT IMPLEMENTED`（下一实施候选 / 未实现）。

## 当前禁止误写的事实

- LIVE `DISABLED`（关闭）。
- AI `NOT STARTED`（未开始）。
- DH runtime `NOT INTEGRATED`（未集成）。
- Integration-1 `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe `NOT IMPLEMENTED`（未实现）。
- GateS backend read model / API / frontend page / Python offline evaluation artifact binding 仍 `NOT IMPLEMENTED`（未实现）。

## 模块职责

| 模块 | 负责什么 | 不负责什么 |
| --- | --- | --- |
| `nq-app` | Spring Boot 启动、profile、Bean wiring、composition root | 不承载业务规则，不直接实现交易、行情、研究编排 |
| `nq-api` | HTTP controller、DTO、web adapter、API contract | 不写 SQL，不承载 research/backtest/eval 编排 |
| `nq-core` | domain model、policy、port、application service | 不包含 JDBC 实现，不直接依赖交易所 adapter 实现 |
| `nq-infra` | JDBC、Flyway、repository adapter、query adapter、基础设施实现 | 不定义业务主语义，不反向污染 core |
| `nq-ledger` | 账本应用服务与账本持久化协作 | 不负责交易所适配和订单主状态机 |
| `nq-risk` | 风控规则与风控检查 | 不绕过账户、订单、账本边界 |
| `nq-scheduler` | 调度、reconcile、recovery、instrument sync 等运行时编排 | 不作为 controller 直接依赖对象，不替代业务 owner |
| `nq-research` | research 配置、研究任务应用 owner | 不进入 live trading 主链 |
| `nq-backtest` | backtest 执行、dataset 消费、回测结果产出 | 不拥有平台级 marketdata owner |
| `nq-eval` | evaluation、backtest run API 编排 owner | 不负责交易执行 |
| `nq-observability` | 观测指标、健康、日志与运行可见性支撑 | 不承载业务决策 |
| `nq-adapter-api` | 交易所 adapter contract | 不实现具体交易所调用，不代表 real adapter permission probe 已实现 |
| `nq-adapter-okx` | OKX 交易所适配实现；当前不得解释为 real provider 已启用 | 不定义平台交易主语义，不实现真实 permission probe adapter |
| `nq-adapter-binance` | Binance 交易所适配实现；当前不得解释为 real provider 已启用 | 不定义平台交易主语义，不实现真实 permission probe adapter |
| `frontend` | React / Vite / Ant Design / TanStack Query 控制台 | 不散写 API 请求，不做 AI / Agent / DH runtime 成熟页面 mock，不提供 LIVE / real trading 按钮 |
| `research/py` | Python 独立离线研究工具链、CLI、pytest/mypy/ruff 验证 | 不进入 auth、recovery、ledger、live trading 主链，不表示 ML ready 或 live execution ready |

## 禁止依赖规则

- `nq-api` 不直接写 SQL。
- `nq-core` 不依赖 JDBC、不依赖 `nq-infra`。
- `nq-infra` 实现 core ports，但不反向定义业务语义。
- adapter 模块只做交易所适配。
- scheduler contract 与 scheduler implementation 分离。
- 前端 API 调用统一走 `frontend/src/api/*` 封装。
