# Current Stage（当前阶段入口）

当前阶段：**GateG（待启动）**

当前状态：**GateF 已完成并冻结；下一阶段 GateG 待启动。**

---

## 1. 当前阶段结论

- GateD 已冻结
- GateE 已冻结
- GateF 已完成并冻结
- current 目录不再代表 GateF 开发中状态
- current 目录现在承载 GateG 待启动入口

---

## 2. GateF 最终完成事实

- `nq-research`
- `nq-backtest`
- `nq-eval`
- `research_configs / backtest_configs / backtest_runs`
- `sim_orders / sim_trades / sim_positions / sim_pnl_snapshots`
- `backtest_eval_reports`
- 显式 `start / evaluate / publish` 前三阶段中的 `start / evaluate` 已完成，`publish` 查询呈现已补齐
- GateF-Freeze-Fix Step 6 已完成：
  - `GET /api/research-configs`
  - `GET /api/research-configs/{configId}`
  - `GET /api/backtest-configs`
  - `GET /api/backtest-configs/{configId}`
  - `GET /api/backtest-runs`
  - `GET /api/backtest-runs/{runId}`
  - `GET /api/backtest-runs/{runId}/sim-orders`
  - `GET /api/backtest-runs/{runId}/sim-trades`
  - `GET /api/backtest-runs/{runId}/sim-positions`
  - `GET /api/backtest-runs/{runId}/pnl-snapshots`
  - `GET /api/backtest-runs/{runId}/evaluation`
  - `GET /api/backtest-runs/{runId}/publish`
- 研究配置、回测配置、回测运行及其子资源查询面已达到 GateG 前端联调前最低可用标准

---

## 3. 当前不再继续承载

- GateF 的研究 / 回测 / 评估主体实现
- GateE 的执行债务清理

---

## 4. 下一阶段说明

- GateG 仅标记为下一阶段入口
- 本批不展开 GateG 设计正文
- 当前批次属于 GateF-Freeze-Fix 冻结收口修复，不属于 GateG 功能开发
- GateG 具体边界以后续主卷宗为准

---

## 5. 当前模块职责收口

- `nq-api` 负责 HTTP API 层，包括 Controller、HTTP DTO 与仅服务于 Controller 的 API service
- `nq-app` 只负责 Spring Boot 启动、模块装配、profile 与运行时 wiring
- `nq-scheduler` 承接运行时 WS 事件桥接与加速前入链
- `nq-adapter-okx` / `nq-adapter-binance` 承接各自交易所 smoke runner
- GateF-Freeze-Fix Step 2 已完成正式 HTTP 路由收口：正式 API 统一使用 `/api/**`
- 旧 `/__gated/**` 已退出正式运行链路，不再作为运行时入口保留
- `nq-api` 现已具备统一参数校验、全局异常处理与统一错误响应模型 `ApiErrorResponse`
- GateF-Freeze-Fix Step 3 已完成依赖方向收口：`nq-core` 与 `nq-ledger` 不再直接依赖 `nq-infra`
- `nq-infra` 现作为 port 的基础设施实现模块，`EventStoreAppender` 通过共享端口 `EventPublisherPort` 向业务模块提供能力
- `nq-app` 继续承担最终装配职责；业务模块注入的是 port，具体实现来自 infra 组件
- GateF-Freeze-Fix Step 4 已完成 trace 语义收口：正式 HTTP trace header 统一为 `X-Trace-Id`
- 历史别名 `X-NQ-TRACE-ID` 仅在过滤器入口兼容读取；正式文档与响应头只使用 `X-Trace-Id`
- `ApiErrorResponse.traceId`、日志 MDC 与响应头 trace 均来自同一过滤器链路，Controller 不再手工处理 trace
- GateF-Freeze-Fix Step 5 已完成最小真实认证鉴权链：`POST /api/auth/login`、Bearer access token、`GET /api/auth/me`
- `StubTokenService`、`NoopAuthService`、`NoopGatewayAuthFacade` 已退出正式运行链
- 正式 `/api/**` 默认受保护：`GET /api/**` 需已认证，非 `GET /api/**` 需 `ADMIN` 或 `OPERATOR`
- GateF-Freeze-Fix Step 6 已完成研究 / 回测查询面收口：
  - 研究配置支持按 `sourceStrategyId` 过滤的列表与详情查询
  - 回测配置支持全量列表、按 `researchConfigId` 关联查询与详情查询
  - 回测运行继续支持按 `researchConfigId / backtestConfigId` 过滤的列表、详情、`sim_*`、`evaluation`、`publish` 查询
- GateF-Freeze-Fix Step 6 已完成关键写链事务边界收口：
  - `OrderCommandService` 把本地数据库写阶段拆到独立事务 helper，adapter 调用保持在事务外；外部确认写失败时，订单停在 `SENT / CANCEL_REQUESTED` 等可恢复状态，不伪装成功
  - `BacktestExecutionService` 先完成历史数据读取与内存计算，再把 `RUNNING + sim_* + SUCCEEDED` 作为单个本地事务提交；成功事务失败时仅回写 `FAILED`，不留下半套模拟事实
  - `TradeLedgerPostingService` 继续以既有 `@Transactional` 作为单笔成交记账、账本事件、持仓与账户快照的原子边界
- 本地开发仅使用示例 secret 与示例 BCrypt hash，仓库中不提交真实凭证
- 当前后端主线已收口到 Step 6，不展开前端接入或后续 GateG 设计正文
