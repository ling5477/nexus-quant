# Current Modules

## `nq-api`

- 负责 HTTP API 层
- 承接 `@RestController`
- 承接 request / response DTO
- 承接仅服务于 Controller 的 API service、mapper、assembler
- 对外暴露正式 `/api/**` 路由下的查询、策略、研究、回测与交易运行接口
- 负责在 Controller 边界执行 Bean Validation 输入校验
- 负责统一全局异常处理与错误响应模型 `ApiErrorResponse / ApiFieldError`
- Controller 通过统一 `TraceIdContext` 读取当前 trace，不再手工解析 header 或包装 MDC
- 若后续存在面向前端或外部客户端的 WS/SSE endpoint，也由该模块承接

## `nq-app`

- 只负责 Spring Boot 启动入口
- 只负责模块装配、profile、bean wiring、运行时配置
- 通过组件扫描加载 `nq-api` 中的 Controller
- 负责把 `nq-core / nq-ledger / nq-scheduler` 所需 port 的 infra 实现收进最终运行时上下文
- 通过 `nq-observability` 提供的过滤器把正式 trace header、request attribute、MDC 串成同一链路
- 不再保留业务 Controller 与 API application service
- 不再保留运行时 WS 桥接实现与 smoke runner

## `nq-core`

- 负责执行域应用服务、状态机、风控编排、订单事实推进
- 只依赖执行域 port 与共享 contracts，不再直接依赖 `nq-infra`
- 当前通过共享端口 `EventPublisherPort` 追加命令/事件事实

## `nq-ledger`

- 负责成交记账、账本分录、持仓投影、账户快照编排
- 只依赖账本域 port 与共享 contracts，不再直接依赖 `nq-infra`
- 当前通过共享端口 `EventPublisherPort` 追加 ledger / position 相关事实

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

- 本文档当前覆盖到 GateF-Freeze-Fix Step 3
- 正式 HTTP API 已统一收口到 `/api/**`
- 旧 `/__gated/**` 只允许作为历史说明出现，不再是正式运行入口
- 参数校验、全局异常处理与统一错误模型已经落在 `nq-api`
- `nq-core` 与 `nq-ledger` 已不再直接依赖 `nq-infra`
- `nq-infra` 现作为 port 的基础设施实现层，`nq-app` 负责最终装配
- 正式 trace header 已统一为 `X-Trace-Id`
- 错误响应中的 `traceId`、日志 MDC 中的 `trace_id` 与响应头 `X-Trace-Id` 同源
- 不展开前端、鉴权替换或后续 GateG 工作
