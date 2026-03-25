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
- 显式 `start / evaluate / publish` 前三阶段中的 `start / evaluate` 已完成
- run detail / run list / sim_* / evaluation 查询面已形成最小闭环

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
- 本批到 Step 4 即停止，不展开 Step 5、前端接入或后续 GateG 设计
