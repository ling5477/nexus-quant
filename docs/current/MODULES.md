# Current Modules（RC1 冻结基线）

## 总体结论

当前模块边界已稳定成立，以下描述构成 **RC1 completed and frozen** 的正式基线。后续 `GateH-PLAN` 与任何增量开发，都不得回退这些边界。

## `frontend`

- 已完成正式账户上下文、header 账户入口与账户 / 凭证管理最小写侧。
- 当前不再承担 RC1 主线任务；后续若有前端增强，必须进入 `GateH-PLAN` 再规划。

## `nq-api`

- 正式 HTTP API 层。
- 承担 controller、request/response DTO 与 API contract。
- 不直接写 SQL，不承担持久化细节。

## `nq-core`

- 只保留业务核心、port、domain model 与 application service。
- 不再保留 JDBC 实现。

## `nq-infra`

- 正式 JDBC、Flyway、query adapter 与持久化适配层。
- 当前已承接账户、策略、交易、research、marketdata 等正式持久化实现。

## `nq-app / nq-auth / nq-security / nq-gateway`

- `nq-app` 只负责装配与 profile 入口。
- `nq-auth` 负责认证应用服务。
- `nq-security` 负责 token 与过滤器。
- `nq-gateway` 负责安全上下文桥接。
- DB-backed auth 已稳定成立。

## `nq-scheduler`

- 负责调度、reconcile、recovery 等运行时编排实现。
- 当前边界已冻结，后续如需扩展必须基于 RC1 基线增量规划。

## `nq-research / nq-backtest / nq-eval`

- 已作为 `research` 域内子能力稳定成立。
- 当前已具备 `research -> backtest -> eval` 最小 DB-backed happy path。
- `publish` 保持现状，不作为 RC1 冻结基线的必达扩展范围。

## `research/py`

- 研究子工程骨架已建立。
- 不构成 RC1 当前主线的一部分；后续是否继续扩展，由 `GateH-PLAN` 再决定。

## 冻结约束

- 不允许回退当前模块边界。
- 不允许回退当前业务域结构。
- 任何后续功能规划必须以 RC1 冻结基线为输入。
