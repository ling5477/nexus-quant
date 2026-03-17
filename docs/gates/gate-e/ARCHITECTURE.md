# GateE ARCHITECTURE
# GateE 阶段架构摘要

> 本文档是 GateE 阶段架构摘要，不是最终实现细节文档。它的作用是把当前项目文件里的现状与 GateE 目标之间的桥梁搭起来。

---

## 1. GateE 预计触达的模块边界

- `nq-core`：策略接入契约、策略运行状态、与执行链路的边界
- `nq-scheduler`：调度编排主链、运行窗口、去重与触发协调
- `nq-adapter-api / nq-adapter-binance / nq-adapter-okx`：GateE-0 返回模型与 Binance reconcile 噪音治理相关边界
- `nq-infra / nq-api / nq-ledger`：schema / metadata 与查询面收口
- `nq-app`：阶段入口与最小运行支撑

---

## 2. 当前代码里的 GateE 前置资产

### 2.1 已有可复用资产
- `strategy_runs` 表
- `orders.strategy_run_id`
- `PlaceOrderRequest`
- `PlaceOrderCommand`
- `AdapterOrderRequest`
- `StrategyScheduler`
- `NoopStrategyScheduler`
- `GateBDemoStrategyRunner`

### 2.2 当前明显空洞
- 策略定义模型缺失
- 调度计划模型缺失
- 策略运行状态机缺失
- 策略读侧缺失
- 调度窗口 / 串行化 / 去重规则缺失

---

## 3. GateE 目标结构（逻辑分层）

### 3.1 策略定义层
负责：
- `strategyId` 级别的身份与配置
- 启停状态
- 调度配置
- 参数快照

### 3.2 策略运行层
负责：
- `strategyRunId` 级别的一次运行实例
- 触发来源（manual / scheduler / recovery）
- 运行窗口
- 运行状态
- 结果摘要

### 3.3 执行复用层
负责：
- 继续使用 GateD 的 `OrderCommandService -> Risk -> Adapter -> Ledger` 闭环
- 订单、成交、账本、持仓、账户仍由 GateD 执行域负责

### 3.4 调度编排层
负责：
- 选择哪些策略该跑
- 决定何时跑、是否能跑、是否重复
- 生成 `strategyRunId`
- 跟踪本次运行的开始、完成、失败与结果

---

## 4. 核心数据血缘

### 4.1 身份分层
- `strategyId`：策略定义身份
- `strategyRunId`：策略运行实例身份
- `orderId`：订单身份
- `tradeId`：成交身份

### 4.2 血缘要求
- 一个 `strategyId` 可以对应多次 `strategyRunId`
- 一个 `strategyRunId` 可以产生 0~N 笔订单
- 订单通过 `orders.strategy_run_id` 反查到策略运行
- GateE 不把策略运行状态塞进订单状态字段里，那会把面条煮成浆糊

---

## 5. 当前推荐实现方向

### Top 1：先冻结语义，再做表
- 当前已有 `strategy_runs`，先把它的字段语义冻结
- 新表是否新增，等 GateE-1 契约定稿后再发 migration

### Top 2：先把旧占位角色摘清楚
- `NoopStrategyScheduler` 保留为占位装配
- `GateBDemoStrategyRunner` 定义为历史演示触发器，不升格为正式主链

### Top 3：调度只做编排
- GateE 新加的调度能力只能调用 core 的稳定入口
- 不允许 scheduler 直接写订单状态、账本或持仓
