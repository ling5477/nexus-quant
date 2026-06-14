# Current Modules

本文记录当前模块 owner 和职责边界。当前阶段为 GateJ completed；Next: GateK-PLAN；GateK planning baseline 已 accepted；GateK implementation not started。

GateH / GateI / GateJ 属于 previous completed phase / archived history。后续 GateK planning 和 future implementation 不得回退 RC1 / GateH-PRE 已冻结的依赖方向，也不得把历史 GateH 语境当作 current state。

当前禁止误写的事实：

- AI not started。
- DH runtime not integrated / not connected to NQ。
- LIVE disabled。
- real exchange permission probe adapter not implemented。

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
| `nq-adapter-api` | 交易所 adapter contract | 不实现具体交易所调用，不代表 real adapter permission probe 已实现 |
| `nq-adapter-okx` | OKX 交易所适配实现；当前 permission probe 仍为 no-real baseline | 不定义平台交易主语义，不实现真实 permission probe adapter |
| `nq-adapter-binance` | Binance 交易所适配实现；当前 permission probe 仍为 no-real baseline | 不定义平台交易主语义，不实现真实 permission probe adapter |
| `frontend` | React / Vite / Ant Design / TanStack Query 控制台、账户上下文、交易工作台、研究/回测/评估/行情页面入口；NQ Console Design System v1 是当前基线 | 不散写 API 请求，不把历史 alias 当正式入口，不做 AI / Agent / DH runtime 完整页面 mock |
| `research/py` | Python 独立离线研究工具链、CLI、pytest/mypy/ruff 验证；应进入 GateK CI baseline 规划 | 不进入 auth、recovery、ledger、live trading 主链，不作为 GateK implementation 入口 |

## 禁止依赖规则

- `nq-api` 不直接写 SQL。
- `nq-core` 不依赖 JDBC、不依赖 `nq-infra`。
- `nq-infra` 实现 core ports，但不反向定义业务语义。
- adapter 模块只做交易所适配，不把交易所模型泄漏为平台 application 主语义。
- scheduler contract 与 scheduler implementation 分离，controller 不直接依赖 scheduler 具体实现。
- 前端 API 调用统一走 `frontend/src/api/*` 封装。

## 当前阶段禁止新增

- 当前阶段不启动 GateK implementation；GateK-PLAN 只代表 planning baseline。
- 当前阶段不新增 AI 模块，不实现 AI 信号、AI runtime 或 AI Paper Trading。
- 当前阶段不实现 DH runtime integration，不接 NQ RealClient，不接真实 Provider。
- 当前阶段不启用 LIVE，不新增真实下单、撤单、转账、提现路径。
- 当前阶段不实现真实 OKX / Binance permission probe adapter；permission probe 仍保持 no-real baseline。
- 当前阶段不新增美股模块。
- 当前阶段不新增 A 股模块。
- GateH 业务实现属于 previous completed phase / archived history，不作为当前新增项或当前未完成项描述。
