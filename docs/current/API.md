# Current API

当前 API 文档以代码实际 controller 为准。本文只建立统一分类入口，后续 `GateH-PLAN` 需要补齐正式 API 清单、请求响应样例、错误码与权限边界。

## API 分类

- Auth API：登录、当前用户、token 相关接口。
- Account API：用户账户、交易账户、默认账户上下文、凭证写侧接口。
- Trading API：订单、成交、持仓、交易工作台相关接口。
- Strategy API：策略配置、策略查询与策略运行前置数据。
- Schedule API：调度配置与调度状态。
- Run API：运行记录、执行状态、运行详情。
- Research API：研究配置与研究任务。
- Backtest API：回测配置、回测执行、回测结果。
- Evaluation API：评估任务、评估结果。
- Publish API：发布候选、发布状态。
- Instrument API：交易标的、交易所、市场类型、symbol catalog。
- Marketdata API：行情基础 ingest/query 能力。
- Actuator / Health：Spring Boot actuator、健康检查。

## 当前边界

- 正式 HTTP API 统一使用 `/api/**`。
- 旧 `/__gated/**` 只允许出现在历史文档说明中。
- AI 自动交易 API 当前不存在，也不允许在本次任务新增。
- GateH-1 只收口 Trading Workspace，不新增行情接入、dataset 绑定或 AI 自动交易接口。

## GateH-1 Trading Workspace API

当前已实现的 GateH-1 交易工作台读写入口：

- `GET /api/trading/orders`：按正式 `exchangeAccountId` 账户上下文查询订单列表，支持 `orderId`、`symbol`、`status`、`environment`、分页筛选。
- `GET /api/trading/orders/{orderId}`：查询单笔订单详情。
- `GET /api/trading/orders/{orderId}/trade`：查询订单最近一笔成交事实。
- `GET /api/trading/accounts/{accountId}`：查询账户余额快照；`accountId` 仍由后端兼容映射到 legacy trading account。
- `GET /api/trading/positions/{accountId}/{symbol}`：查询账户和交易对维度持仓快照。
- `POST /api/trading/orders`：触发既有下单编排，仍走服务端风控与状态机。
- `POST /api/trading/orders/cancel`：触发既有撤单编排。
- `POST /api/trading/reconciliation/run-once`：触发既有对账维护动作。
- `POST /api/trading/recovery/run-once`：触发既有恢复维护动作。

GateH-1 不新增历史行情抓取、marketdata ingestion、dataset 绑定、AI 下单或策略自动交易接口。
