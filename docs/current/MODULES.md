# Current Modules

## `nq-api`

- 负责 HTTP API 层
- 承接 `@RestController`
- 承接 request / response DTO
- 承接仅服务于 Controller 的 API service、mapper、assembler
- 对外暴露正式 `/api/**` 路由下的查询、策略、研究、回测与交易运行接口
- Step 6 已补齐研究 / 回测查询面：
  - `GET /api/research-configs`
  - `GET /api/research-configs/{configId}`
  - `GET /api/backtest-configs`
  - `GET /api/backtest-configs/{configId}`
  - `GET /api/backtest-runs`
  - `GET /api/backtest-runs/{runId}`
  - `GET /api/backtest-runs/{runId}/sim-orders|sim-trades|sim-positions|pnl-snapshots|evaluation|publish`
- 负责在 Controller 边界执行 Bean Validation 输入校验
- 负责统一全局异常处理与错误响应模型 `ApiErrorResponse / ApiFieldError`
- Controller 通过统一 `TraceIdContext` 读取当前 trace，不再手工解析 header 或包装 MDC
- 新增正式认证接口：`POST /api/auth/login`、`GET /api/auth/me`
- 若后续存在面向前端或外部客户端的 WS/SSE endpoint，也由该模块承接

## `nq-app`

- 只负责 Spring Boot 启动入口
- 只负责模块装配、profile、bean wiring、运行时配置
- 通过组件扫描加载 `nq-api` 中的 Controller
- 负责把 `nq-core / nq-ledger / nq-scheduler` 所需 port 的 infra 实现收进最终运行时上下文
- 通过 `nq-observability` 提供的过滤器把正式 trace header、request attribute、MDC 串成同一链路
- 承担 Spring Security 最终装配，包括 `SecurityFilterChain`、JWT 过滤器与 401/403 错误写回
- 不再保留业务 Controller 与 API application service
- 不再保留运行时 WS 桥接实现与 smoke runner

## `nq-core`

- 负责执行域应用服务、状态机、风控编排、订单事实推进
- 只依赖执行域 port 与共享 contracts，不再直接依赖 `nq-infra`
- 当前通过共享端口 `EventPublisherPort` 追加命令/事件事实
- Step 6 已把 `OrderCommandService` 的本地数据库写阶段拆到独立事务 helper：
  - 新建订单、风控事件、状态推进、审计、事件事实链在本地数据库内原子提交
  - adapter 下单 / 撤单调用保持在事务外
  - adapter 成功后若本地确认写失败，订单停在 `SENT / CANCEL_REQUESTED` 等可恢复状态，交由 query-confirm / recovery 接管

## `nq-research`

- 承接 `ResearchConfig / BacktestConfig / BacktestRun / BacktestPublishRecord`
- Step 6 已补齐：
  - `ResearchConfigService.list(sourceStrategyId)`
  - `BacktestConfigService.list(researchConfigId)`
  - `BacktestRunService.list(researchConfigId, backtestConfigId)` 继续作为运行关联查询主入口

## `nq-backtest`

- 承接历史数据驱动的模拟执行、`sim_order / sim_trade / sim_position / sim_pnl_snapshot`
- Step 6 已把 `BacktestExecutionService` 收口成“两阶段”：
  - 先做历史数据读取与内存计算
  - 再由 `BacktestExecutionPersistenceService` 把 `RUNNING + sim_* + SUCCEEDED` 原子提交
- 成功提交失败时只回写 `FAILED`，不保留半套模拟事实

## `nq-eval`

- 承接回测评估报告与 evaluation 查询
- 继续通过 `BacktestEvaluationService` 提供 run 级显式 `evaluate` 与 `evaluation` 查询

## `nq-ledger`

- 负责成交记账、账本分录、持仓投影、账户快照编排
- 只依赖账本域 port 与共享 contracts，不再直接依赖 `nq-infra`
- 当前通过共享端口 `EventPublisherPort` 追加 ledger / position 相关事实

## `nq-auth`

- 提供配置驱动本地账户认证与登录服务
- 使用 BCrypt 校验 `username / password`
- 不再保留 `NoopAuthService`

## `nq-security`

- 提供 JWT access token 签发、解析、校验
- 提供 Bearer token 认证过滤器
- 不再保留 `StubTokenService`

## `nq-gateway`

- 提供对 `SecurityContext` 的薄封装，统一读取当前认证主体
- 不再保留 `NoopGatewayAuthFacade`

## `nq-scheduler`

- 承接运行时 WS 事件桥接
- 承接 WS 事件加速前的 event_store 入链与审计留痕
- 当前承接 `OkxWsEventStoreBridge` 与 `BinanceWsEventStoreBridge`
- 使用共享端口 `EventPublisherPort` 进行事实链追加，不直接耦合 `nq-infra` 实现类型

## `nq-observability`

- 承接正式 HTTP trace 过滤器与基础可观测性收口
- 统一读取/生成 `X-Trace-Id`
- 统一把 traceId 写入 request attribute、MDC 与响应头
- 保证 `ApiErrorResponse.traceId` 与日志 `trace_id` 同源

## `nq-infra`

- 保留为基础设施实现模块
- 提供 JDBC / event_store / research/backtest/eval 等基础设施实现
- 对业务模块暴露的是 port 实现，不再要求 `nq-core`、`nq-ledger` 直接 import infra 类型

## `nq-adapter-okx`

- 承接 OKX 协议实现
- 承接 `OkxWsSmokeRunner`

## `nq-adapter-binance`

- 承接 Binance 协议实现
- 承接 `BinanceWsSmokeRunner`

## 本步边界

- 本文档当前覆盖到 GateF-Freeze-Fix Step 6
- 正式 HTTP API 已统一收口到 `/api/**`
- 旧 `/__gated/**` 只允许作为历史说明出现，不再是正式运行入口
- 参数校验、全局异常处理与统一错误模型已经落在 `nq-api`
- `nq-core` 与 `nq-ledger` 已不再直接依赖 `nq-infra`
- `nq-infra` 现作为 port 的基础设施实现层，`nq-app` 负责最终装配
- 正式 trace header 已统一为 `X-Trace-Id`
- 错误响应中的 `traceId`、日志 MDC 中的 `trace_id` 与响应头 `X-Trace-Id` 同源
- 正式认证方式已统一为 `POST /api/auth/login` + `Authorization: Bearer <token>`
- 当前最小角色模型为 `ADMIN / OPERATOR / VIEWER`
- 研究配置、回测配置、回测运行与 `sim_* / evaluation / publish` 查询面已补齐到 GateG 联调前最低可用标准
- `OrderCommandService`、`BacktestExecutionService`、`TradeLedgerPostingService` 的事务边界已按当前批次目标收口
- 本地配置只提供示例 secret 与示例 BCrypt hash，不提交真实生产凭证
- 不展开前端、鉴权替换或后续 GateG 工作
