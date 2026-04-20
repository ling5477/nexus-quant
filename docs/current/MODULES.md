# Current Modules（RC1 + GateH-PRE 冻结基线）

## 总体结论

当前模块边界已稳定成立，以下描述构成 **RC1 completed and frozen + GateH-PRE completed** 的正式基线。后续 `GateH-PLAN` 与任何增量开发，都不得回退这些 owner 与依赖方向。

## `frontend`

- 正式前端控制台，技术栈固定为 React 19 + TypeScript + Vite + React Router + TanStack Query + Axios + Zustand + Ant Design。
- 正式交易入口是 `/trading`，页面域为 `frontend/src/pages/trading` 与 `trading-workbench` API/types/hooks。
- `/trade-validation` 仅保留历史路由 alias，不是正式页面入口。
- 已包含账户上下文、Accounts、Trading Workbench、Strategies/Schedules/Runs、Research/Backtests/Evaluations/Publishes、Instruments、Marketdata 等页面域。

## `nq-api`

- 正式 HTTP API 层。
- 只承担 controller、request/response DTO、web adapter 与 API contract。
- 不直接写 SQL，不承担 research/backtest/eval 编排层。
- research orchestration 已从 `nq-api` 移出，回到正式 application owner。

## `nq-core`

- 业务核心模块，承载 domain model、policy、port 与 application service。
- `trading` anti-corruption 已成立，core 不再直接依赖 adapter API model/service 作为 application 主语义。
- `marketdata` application/domain owner 已收口到正式主链，不再挂在 `nq-backtest` 附属路径。
- 不保留 JDBC 实现。

## `nq-infra`

- domain-first infra 实现模块，承载 JDBC、Flyway、query adapter、repository adapter 与基础设施实现。
- 当前 namespace 已收敛，不再保留 `account.infra.*` 与 `infra.account.*` 双轨命名。
- `infra.config` 只保留横切基础设施配置，不承载业务 owner。

## `nq-app`

- Spring Boot 启动与 composition root。
- 只负责启动、profile、Bean wiring 与顶层装配。
- verifier、stub policy、runtime concrete strategy 已下移到更合理 owner，不再把业务实现语义留在 app。

## `nq-auth / nq-security / nq-gateway`

- `nq-auth` 负责认证应用服务与认证领域编排。
- `nq-security` 负责 token、filter、安全配置与认证基础设施。
- `nq-gateway` 负责安全上下文桥接。
- DB-backed auth 已稳定成立。

## `nq-scheduler / nq-scheduler-contracts`

- `nq-scheduler` 负责调度、reconcile、recovery、instrument sync 等运行时编排实现。
- `nq-scheduler-contracts` 只保留跨模块调度契约。
- scheduler 不应重新承担 adapter contract 泄漏到 core 的职责。

## `nq-research / nq-backtest / nq-eval`

- `nq-research` 承担 research 配置与研究应用 owner。
- `nq-backtest` 承担 backtest 执行与 dataset 消费，不再拥有平台级 marketdata owner。
- `nq-eval` 承担 evaluation 与 backtest run API 编排 owner。
- 当前已具备 `research -> backtest -> eval` 最小 DB-backed happy path。

## `nq-ledger / nq-ledger-contracts`

- `nq-ledger` 承担账本应用与持久化协作。
- `nq-ledger-contracts` 只保留跨模块账本 contract。
- `NoopLedgerService` 仅作为明确的 local/test fallback，不是正式账本主路径。

## `nq-risk`

- 风控域模块。
- `NoopRiskGate` 仅作为明确的 local/test fallback，不是正式风控主路径。

## `nq-adapter-api / nq-adapter-okx / nq-adapter-binance`

- `nq-adapter-api` 只定义交易所 adapter contract。
- `nq-adapter-okx` 与 `nq-adapter-binance` 承担具体交易所 adapter 实现。
- adapter contract 不应重新进入 `nq-core` application orchestration。

## `research/py`

- 离线研究工具链子工程。
- 正式入口是 `py -m nq_research` 与 `nq-research` script。
- 已通过 `pytest` / `mypy` / `ruff` / CLI smoke。
- 不进入 live trading / auth / recovery / ledger 主链，不做 Java/Python runtime bridge。

## 冻结约束

- 不允许回退当前模块边界。
- 不允许回退当前业务域结构。
- 不允许重新把 historical alias 当作正式入口。
- 不允许绕过 `GateH-PLAN` 直接恢复 GateH 开发。
