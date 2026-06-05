# Current Modules

本文记录当前模块 owner 和职责边界。后续 `GateH-PLAN` 与功能开发不得回退 RC1 / GateH-PRE 已冻结的依赖方向。

## 模块职责

| 模块 | 负责什么 | 不负责什么 |
| --- | --- | --- |
| `nq-app` | Spring Boot 启动、profile、Bean wiring、composition root | 不承载业务规则，不直接实现交易、行情、研究编排 |
| `nq-api` | HTTP controller、DTO、web adapter、API contract | 不写 SQL，不承载 research/backtest/eval 编排 |
| `nq-core` | domain model、policy、port、application service | 不包含 JDBC 实现，不直接依赖交易所 adapter 实现 |
| `nq-infra` | JDBC、Flyway、repository adapter、query adapter、基础设施实现 | 不定义业务主语义，不反向污染 core |
| `nq-ledger` | 账本应用服务与账本持久化协作 | 不负责交易所适配和订单主状态机 |
| `nq-ledger-contracts` | 跨模块账本契约 | 不放实现逻辑 |
| `nq-scheduler` | 调度、reconcile、recovery、instrument sync 等运行时编排 | 不作为 controller 直接依赖对象，不替代业务 owner |
| `nq-scheduler-contracts` | 跨模块调度契约 | 不放调度实现 |
| `nq-research` | research 配置、研究任务应用 owner | 不进入 live trading 主链 |
| `nq-backtest` | backtest 执行、dataset 消费、回测结果产出 | 不拥有平台级 marketdata owner |
| `nq-eval` | evaluation、backtest run API 编排 owner | 不负责交易执行 |
| `nq-observability` | 观测指标、健康、日志与运行可见性支撑 | 不承载业务决策 |
| `nq-adapter-api` | 交易所 adapter contract | 不实现具体交易所调用 |
| `nq-adapter-okx` | OKX 交易所适配实现 | 不定义平台交易主语义 |
| `nq-adapter-binance` | Binance 交易所适配实现 | 不定义平台交易主语义 |
| `frontend` | React 控制台、账户上下文、交易工作台、研究/回测/评估/行情页面入口 | 不散写 API 请求，不把历史 alias 当正式入口 |
| `research/py` | Python 离线研究工具链、CLI、pytest/mypy/ruff 验证 | 不进入 auth、recovery、ledger、live trading 主链 |

## 禁止依赖规则

- `nq-api` 不直接写 SQL。
- `nq-core` 不依赖 JDBC、不依赖 `nq-infra`。
- `nq-infra` 实现 core ports，但不反向定义业务语义。
- adapter 模块只做交易所适配，不把交易所模型泄漏为平台 application 主语义。
- scheduler contract 与 scheduler implementation 分离，controller 不直接依赖 scheduler 具体实现。
- 前端 API 调用统一走 `frontend/src/api/*` 封装。

## 当前阶段禁止新增

- 当前阶段不新增 AI 模块。
- 当前阶段不新增美股模块。
- 当前阶段不新增 A 股模块。
- 当前阶段不新增 GateH 业务实现。
