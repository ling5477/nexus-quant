# GateE ARCHITECTURE
# GateE 阶段架构摘要

冻结状态：**GateE 已完成并冻结；本文档只保留冻结架构结论。**

本文档只回答三件事：GateE 在系统里放在哪里、和 GateD 怎么衔接、后续实现应该沿哪条主链推进。

---

## 1. 系统位置

GateE 位于 GateD 执行闭环之上、GateF 研究能力之前。

逻辑分层如下：

1. 策略层：负责产出 `StrategySignal`
2. 编排层：负责触发、窗口、去重、串行化，生成 `strategyRunId`
3. 执行层：复用 GateD `PlaceOrderRequest -> PlaceOrderCommand -> AdapterOrderRequest`
4. 风控层：继续由 `nq-risk` 在执行链内裁决
5. 账本层：继续由 `nq-ledger` 处理 trade / ledger / position / account
6. 查询层：对外提供 StrategyDefinition / StrategyRun / RunOrders 读侧

---

## 2. 当前仓库事实与 GateE 关系

### 2.1 已可复用资产

- `strategy_runs` 表已经能记录最小运行事实
- `strategy_definitions` 表已经能承接定义层
- `strategy_schedules` 表已经能承接调度配置层
- `orders.strategy_run_id` 已经能把运行与订单关联起来
- `GateBDemoStrategyRunner` 已经证明定时触发后可带着 `strategy_run_id` 进入下单链
- `StrategyScheduler` 已经给出“调度入口位于 `nq-scheduler`”这一方向

### 2.2 明确不能直接复用为主链的部分

- `GateBDemoStrategyRunner` 是 GateB 演示触发器，不具备注册、启停、窗口、去重、串行化和结果回传能力
- `NoopStrategyScheduler` 只是装配占位，不代表调度模型已存在
- `PlaceOrderCommand.strategyId` 不是可继续沿用的最终语义，它只是兼容期字段

---

## 3. GateE 主链架构

### 3.1 注册与配置阶段

`StrategyDefinition` 写入后，形成策略定义事实：

- `strategyId`
- `strategyType`
- `accountId`
- `venue`
- `paramsSnapshot`
- `scheduleConfig`
- `status`

当前仓库已有该表，但还没有对应 API 与 service，这是 GateE-1 的首个业务落点。

### 3.2 触发与编排阶段

触发来源统一收口为三类：

- `manual`
- `scheduler`
- `recovery`

触发流程固定为：

`TriggerRequest(requestId) -> dedup/window/serialization guard -> StrategyRun(strategyRunId) -> StrategySignal`

说明：

- `requestId` 是“请求身份”
- `strategyRunId` 是“被接受并开始运行后的事实身份”
- 调度器只能推进触发和运行，不直接推进订单状态

### 3.3 执行与回传阶段

`StrategySignal` 经过映射后进入当前执行闭环：

`StrategySignal -> PlaceOrderRequest -> OrderCommandService -> RiskGate -> Adapter -> Order / Trade -> Ledger / Position / Account`

执行域必须回传的最小信息：

- `strategyRunId`
- `orderId`
- `clientOrderId`
- `externalOrderId`
- `finalOrderStatus`
- `tradeCount`
- `ledgerPosted`
- `traceId`

GateE-0.3 收口后，adapter 返回层 canonical 字段固定为：

- `exchange_code`
- `exchange_order_id`
- `client_order_id`
- `exchange_trade_id`
- `account_id`
- `trade_env`

### 3.4 结果归档阶段

GateE 结果层只聚合，不改写 GateD 事实：

`orders / trades / ledger_entries / audit_logs / event_store -> StrategyRunResult`

---

## 4. 关键边界

### 4.1 策略层与调度层

- 策略层负责“产出什么信号”
- 调度层负责“何时触发、能不能触发、同一时间能跑几次”

### 4.2 调度层与执行层

- 调度层只生成 `StrategySignal` 和 `strategyRunId`
- 执行层继续使用 GateD 统一入口
- 不允许 scheduler 直接写 `orders`、`trades`、`ledger_entries` 投影

### 4.3 执行层与账本层

- 仍按 GateD 既有事实链推进
- GateE 只新增血缘与结果汇总，不改变账本主责任

---

## 5. 并发与保护策略

GateE 当前基线写死以下默认规则：

- 默认串行粒度为 `strategyId + accountId`
- 同一粒度在同一时刻只允许一个 `RUNNING` / `DISPATCHING` 运行
- 重复触发先看 `dedupKey`，命中则不再创建新运行
- 窗口不满足时可直接拒绝创建运行，或显式落一条 `SKIPPED` 运行，二选一必须实现一致
- 外部执行异常时，先 `query-confirm`，不得直接把 retry 变成重复下单

---

## 6. GateE-0 与主体关系

GateE-0 不新增主链角色，只做开路治理：

- Binance background reconcile 降噪
- 命名 / schema / metadata / contract 对齐
- adapter 返回模型一致性

只有清掉这些噪音后，GateE-1 / GateE-2 的策略和调度实现才不会边写边返工。

---

## 7. 当前实施落点

- `nq-core`：StrategySignal 到 ExecutionRequest 的映射与运行血缘
- `nq-scheduler`：Trigger、ScheduleJob、窗口、串行化、去重
- `nq-contracts`：策略定义 / 触发 / 运行结果 DTO
- `nq-infra`：定义表、调度表、运行增强字段的 migration
- `nq-api`：策略定义与运行查询面
- `nq-app`：策略注册、启停、手动触发入口
