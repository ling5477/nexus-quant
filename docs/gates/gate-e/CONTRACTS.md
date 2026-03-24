# GateE CONTRACTS
# GateE schema / metadata / contract 统一口径

本文档记录 GateE-0.2 收口后的最终字段语义。重点不是再发明对象，而是把定义级、运行级、请求级、去重级、订单级和交易所级字段一次写死。

---

## 1. 最终身份口径

### 1.1 `strategy_id`

- 层级：定义级
- 含义：策略定义身份
- 落点：
  - `strategy_definitions.strategy_id`
  - `strategy_schedules.strategy_id`
  - `strategy_runs.strategy_id`

### 1.2 `strategy_run_id`

- 层级：运行级
- 含义：单次策略运行身份
- 落点：
  - `strategy_runs.strategy_run_id`
  - `orders.strategy_run_id`
  - `trades.strategy_run_id`

### 1.3 `request_id`

- 层级：请求级
- 含义：一次执行请求或触发请求的身份
- 落点：
  - `orders.request_id`
  - `strategy_runs.request_id`

硬规则：

- `request_id` 可以被记录到 run 行上，但它仍是“首次请求身份”，不等同于 `strategy_run_id`

### 1.4 `dedup_key`

- 层级：去重级
- 含义：去重键，不是运行主键，也不是订单主键
- 落点：
  - `orders.dedup_key`
  - `strategy_schedules.dedup_scope` 负责描述调度侧去重范围

---

## 2. 交易所、账户、环境维度

### 2.1 `exchange_code`

- canonical 字段
- 取值口径：`OKX` / `BINANCE` / `PAPER`

兼容债务：

- `orders.venue` 保留为历史兼容列
- `trades.exchange` 保留为历史兼容列

结论：

- 从 GateE-0.2 开始，数据库 canonical 字段是 `exchange_code`
- Java DTO / service 当前仍广泛使用 `venue`，属于后续代码迁移债务

### 2.2 `account_id`

- canonical 字段
- 表示账户维度

### 2.3 `trade_env`

- canonical 字段
- 固定枚举：`SIM` / `LIVE`

说明：

- GateE-0.2 已落库
- 兼容阶段默认值为 `SIM`
- GateE-1 起由注册与触发入口显式写入

---

## 3. 最终订单口径

### 3.1 `order_id`

- 内部订单主键

### 3.2 `client_order_id`

- 客户端订单号 / 幂等业务号

### 3.3 `exchange_order_id`

- 交易所订单号

兼容债务：

- `external_order_id` 继续保留为兼容列
- 通过数据库 trigger 与 `exchange_order_id` 保持同步

---

## 4. 新增表 contract

### 4.1 `strategy_definitions`

最小字段：

- `strategy_id`
- `strategy_code`
- `strategy_name`
- `strategy_type`
- `exchange_code`
- `account_id`
- `trade_env`
- `enabled`
- `config_snapshot`
- `version`
- `created_at`
- `updated_at`

唯一键口径：

- `strategy_id` 是主键
- `strategy_code` 是注册业务唯一键
- 一个启用中的策略注册项按 `strategy_code` 唯一识别

### 4.2 `strategy_schedules`

最小字段：

- `schedule_job_id`
- `strategy_id`
- `schedule_type`
- `cron_expr`
- `timezone`
- `enabled`
- `window_config`
- `dedup_scope`
- `exchange_code`
- `account_id`
- `trade_env`
- `last_triggered_at`
- `created_at`
- `updated_at`

关系口径：

- `schedule_job_id` 是调度级主键
- `strategy_id` 指向定义级主键
- 当前仍不引入 `trigger_id` 表

---

## 4.3 GateE-1.1 最小管理能力

本阶段“策略注册”固定解释为：

- 创建一条 `StrategyDefinition`
- 可查询列表
- 可查询详情
- 可启用
- 可停用

不是：

- 调度注册
- 运行实例注册
- trigger 注册

当前内部管理接口最小集合为：

- `POST /__gated/strategies`
- `GET /__gated/strategies`
- `GET /__gated/strategies/{strategyId}`
- `POST /__gated/strategies/{strategyId}/enable`
- `POST /__gated/strategies/{strategyId}/disable`

当前最小服务对象：

- `StrategyDefinition`
- `StrategyDefinitionStatus`
- `StrategyDefinitionRepository`
- `StrategyDefinitionService`

---

## 4.4 GateE-1.2 手动 trigger 最小主链

本阶段新增最小对象：

- `StrategyManualTriggerRequest`
- `StrategyManualTriggerResult`
- `StrategyRun`
- `StrategyRunStatus`
- `StrategyRunRepository`
- `StrategyManualTriggerService`

当前最小 trigger 入口：

- `POST /__gated/strategies/{strategyId}/trigger`

当前最小请求字段：

- `requestId`
- `symbol`
- `side`
- `orderType`
- `quantity`
- `price`

当前最小响应字段：

- `strategyId`
- `strategyRunId`
- `requestId`
- `orderId`
- `orderStatus`
- `strategyRunStatus`
- `idempotentHit`

主链固定为：

- 手动 trigger 请求
- 生成 `strategyRunId`
- 写入 `strategy_runs`
- 映射到现有 `PlaceOrderRequest`
- 进入 `PlaceOrderCommand -> AdapterOrderRequest -> OrderCommandService`
- 订单落库绑定 `orders.strategy_run_id`

---

## 5. 收口后的现有表 contract

### 5.1 `strategy_runs`

最终字段语义：

- `strategy_run_id`：运行级主键
- `strategy_id`：所属定义级身份
- `trigger_type`：`MANUAL / SCHEDULER / RECOVERY`
- `exchange_code`
- `account_id`
- `trade_env`
- `config_snapshot`
- `request_id`
- `started_at`
- `finished_at`
- `error_message`

兼容说明：

- 历史 `run_id` 已收口为 `strategy_run_id`
- 历史 `ended_at` 已收口为 `finished_at`

### 5.2 `orders`

最终字段语义：

- `strategy_run_id`：运行血缘
- `request_id`：执行请求身份
- `dedup_key`：去重键
- `exchange_code`
- `account_id`
- `trade_env`
- `client_order_id`
- `exchange_order_id`

兼容说明：

- `venue` 仍在代码层广泛使用，数据库中视为 `exchange_code` 的兼容列
- `external_order_id` 仍在代码层广泛使用，数据库中视为 `exchange_order_id` 的兼容列

### 5.3 `trades`

最终字段语义：

- `strategy_run_id`：运行血缘直达字段
- `exchange_code`
- `account_id`
- `trade_env`
- `exchange_trade_id`
- `exchange_order_id`

兼容说明：

- `exchange` 是 `exchange_code` 兼容列
- `external_order_id` 是 `exchange_order_id` 兼容列

---

## 6. DTO / 代码兼容债务

### 6.1 `PlaceOrderRequest`

- 现状：使用 `strategyRunId`、`requestId`、`clientOrderId`
- 结论：语义与 GateE-0.2 口径一致

### 6.2 `AdapterOrderRequest`

- 现状：使用 `strategyRunId`、`requestId`
- 结论：语义与 GateE-0.2 口径一致

### 6.3 `PlaceOrderCommand`

- 现状：仍保留 `strategyId`
- 事实：执行血缘语义应对应 `strategy_run_id`
- 本批决策：
  - 先不大改主逻辑
  - 在文档与 schema 层写死迁移方向
  - 后续 GateE-1 做字段迁移或兼容桥接

### 6.4 GateE-1.1 不引入 `strategyInstanceId`

- 当前策略管理能力只管理定义级对象
- 启停粒度固定为策略定义级
- 运行级对象和手动 trigger 顺延到 GateE-1.2

---

## 7. 不做项

本批明确不做：

- `strategy_instances`
- `strategy_triggers`
- 策略注册 API
- schedule job 运行逻辑
- 手动 trigger 主链
- GateF 研究 / 回测表

---

## 8. GateE-0.3 adapter 返回层 canonical 口径

### 8.1 canonical 输出字段

从 GateE-0.3 开始，adapter 返回层以以下字段作为 canonical 输出：

- `exchange_code`
- `exchange_order_id`
- `client_order_id`
- `exchange_trade_id`
- `account_id`
- `trade_env`

兼容策略：

- `venue()` 继续保留为兼容访问器，目标迁移到 `exchangeCode()`
- `externalOrderId()` 继续保留为兼容访问器，目标迁移到 `exchangeOrderId()`

### 8.2 统一结果分类

adapter 返回层统一使用以下结果分类：

- `SUCCESS`
- `ACCEPTED`
- `NOT_FOUND`
- `DEFERRED`
- `RETRYABLE_FAILURE`
- `FATAL_FAILURE`
- `THROTTLED`
- `AUTH_FAILURE`
- `REMOTE_UNAVAILABLE`

说明：

- `SUCCESS`：远端明确返回业务结果，例如查单成功、查到成交
- `ACCEPTED`：远端接受了提交或撤单请求
- `NOT_FOUND`：远端明确返回“对象不存在”
- `DEFERRED`：对象暂时不可见或 query-confirm 仍不确定
- `RETRYABLE_FAILURE`：可重试失败，例如 timeout / timestamp drift
- `FATAL_FAILURE`：明确业务拒绝或不可恢复失败
- `THROTTLED`：限流
- `AUTH_FAILURE`：鉴权或权限失败
- `REMOTE_UNAVAILABLE`：远端不可用或客户端通信失败

### 8.3 adapter 错误结构

`AdapterError` 当前固定为：

- `code`
- `message`
- `category`
- `retryable`

要求：

- 下游禁止继续只靠原始 message 文本做分流
- reconcile / recovery / query-confirm 优先看 `resultCategory`，其次看 `AdapterError.category`

### 8.4 统一 trade report

Binance / OKX 的 fills 现统一映射到 `AdapterTradeReport`，不再让 reconcile 直接消费交易所私有 fill DTO 作为主返回语义。

---

## 9. GateE-2.1 schedule job / 计划配置

当前最小对象为：

- `StrategySchedule`
- `StrategyScheduleStatus`
- `StrategyScheduleRepository`
- `StrategyScheduleService`
- `StrategyScheduleScanService`

当前最小接口为：

- `POST /__gated/strategies/{strategyId}/schedules`
- `GET /__gated/strategies/{strategyId}/schedules`
- `GET /__gated/strategy-schedules/{scheduleJobId}`
- `POST /__gated/strategy-schedules/{scheduleJobId}/enable`
- `POST /__gated/strategy-schedules/{scheduleJobId}/disable`
- `POST /__gated/strategy-schedules/scanOnce`

当前最小 due 判断为：

- 只支持 `scheduleType = CRON`
- 使用 `cronExpr + timezone + lastTriggeredAt` 判断是否应触发

当前与 GateE-1.2 的复用关系：

- schedule 命中后不复制 run 创建与下单链
- `StrategyScheduleScanService` 通过 `StrategyTriggerGateway -> StrategyManualTriggerService` 复用 GateE-1.2 主链

当前与 GateE-2.2 的边界：

- `windowConfig`：只存不执行
- `dedupScope`：只存不执行
- `serialization`：不做
