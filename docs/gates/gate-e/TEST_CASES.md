# GateE TEST_CASES
# GateE 测试与验收清单

冻结状态：**GateE 已完成并冻结；本文档保留冻结时的验收结论。**

状态约定：

- `[x]` 已完成
- `[~]` 已有底座但未完成
- `[ ]` 未开始

当前基线日期：`2026-03-23`

---

## 1. GateE-DOC-2 文档收口验收

- 当前状态：`[x]`
- 验证点：
  - `docs/current/*` 与 `docs/gates/gate-e/*` 口径一致
  - `strategyId / strategyRunId / requestId / dedupKey` 已明确
  - PR 顺序、状态机、schema、测试清单可直接指导实施

---

## 2. GateE-0 前置治理

### UC-E0-1 Binance background reconcile 降噪

- 当前状态：`[x]`
- 目标：
  - credential 缺失不再刷屏
  - `-1021` 时间漂移有统一抑制
  - cooldown 内重复触发被降噪
- 最小验证：
  - 构造重复成交命中，确认不再写 `BINANCE_FILL_DEDUP_HIT`
  - 构造 `connect_failed` 阈值内观察与 cooldown 跳过，确认不再写审计事件
  - 构造 `-2013 order not found`，确认按 deferred 处理
- 预期：
  - 日志可区分真实失败与跳过
  - 同窗口内不重复打爆日志

### UC-E0-2 schema / metadata / contract 收口

- 当前状态：`[x]`
- 最小验证：
  - 对比 `PlaceOrderRequest.strategyRunId`
  - 对比 `AdapterOrderRequest.strategyRunId`
  - 对比 `PlaceOrderCommand.strategyId`
  - 对比 `orders.strategy_run_id`
- 预期：
  - 不再出现同名不同义或同义不同名
  - 兼容债务被明确记录并落到具体 PR

### UC-E0-3 返回模型一致性收尾

- 当前状态：`[x]`
- 最小验证：
  - `BinanceExchangeAdapterTest` 覆盖 success / fatal_failure
  - `BinanceErrorClassifierTest` 覆盖 deferred / retryable_failure / auth_failure
  - `OkxErrorClassifierTest` 覆盖 not_found / throttled / auth_failure
  - `BinanceRestReconcileServiceTest` 与 `OkxRestReconcileServiceTest` 覆盖统一 trade report 消费
  - `OkxRecoveryServiceTest` 覆盖 not_found 统一解释
- 预期：
  - adapter 返回层统一使用 canonical 字段
  - reconcile / recovery / query-confirm 不再各自发明 not_found / deferred 解释

---

## 3. GateE-1 策略接入

### UC-E1-1 注册策略定义

- 当前状态：`[x]`
- 步骤：
  - 创建一条 `StrategyDefinition`
  - 查询该定义
- 预期：
  - `strategyId` 唯一
  - 状态、参数、调度配置可回读
  - 审计链可追踪

### UC-E1-2 启停策略

- 当前状态：`[x]`
- 步骤：
  - `DRAFT -> ACTIVE`
  - `ACTIVE -> PAUSED -> ACTIVE`
  - `ACTIVE -> DISABLED`
- 预期：
  - 状态流转符合文档
  - `DISABLED` 后不能再被调度

### UC-E1-3 手动触发运行

- 当前状态：`[x]`
- 步骤：
  - 对 `ACTIVE` 策略发起 manual trigger
- 预期：
  - 生成 `requestId`
  - 生成 `strategyRunId`
  - `strategy_runs` 记录状态从 `CREATED/READY` 推进

### UC-E1-4 执行血缘贯穿

- 当前状态：`[x]`
- 步骤：
  - 手动触发后下发一笔订单
- 预期：
  - 订单带 `strategy_run_id`
  - 能由 `strategyRunId` 反查订单
  - 能由订单回查 `strategyRunId`

### UC-E1-5 结果回传

- 当前状态：`[x]`
- 步骤：
  - 完成一次 manual 或 schedule trigger
  - 通过 `strategyRunId` 反查运行详情
- 预期：
  - `StrategyRunDetail` 可见
  - 订单 / 成交摘要可按 run 聚合
  - 当前未直接纳入的 ledger / risk / event 会返回明确限制说明

---

## 4. GateE-2 调度编排

### UC-E2-1 定时触发

- 当前状态：`[x]`
- 步骤：
  - 注册一条启用的调度作业
  - 等待 scheduler 触发
- 预期：
  - 触发请求进入 `requestId`
  - 运行被接受后生成 `strategyRunId`

### UC-E2-2 运行窗口控制

- 当前状态：`[x]`
- 步骤：
  - 分别构造窗口内与窗口外触发
- 预期：
  - 窗口内正常创建运行
  - 窗口外被 `REJECTED` 或 `SKIPPED`
  - 实现方式前后一致

### UC-E2-3 去重

- 当前状态：`[x]`
- 步骤：
  - 对同一 `strategyId`、同一窗口、同一 `dedupKey` 连续触发两次
- 预期：
  - 第二次落到 `DEDUPED`
  - 不产生第二个有效运行

### UC-E2-4 串行化与并发保护

- 当前状态：`[x]`
- 步骤：
  - 在已有 `RUNNING` 运行时再次触发相同策略
- 预期：
  - 第二次被阻塞、排队或拒绝
  - 不出现并发双跑

### UC-E2-5 受控 retry / recovery

- 当前状态：`[~]`
- 步骤：
  - 模拟运行在 `DISPATCHING` 或 `RUNNING` 阶段中断
  - 触发 recovery
- 预期：
  - 先做请求确认
  - 不发生重复下单
  - 运行结果最终可收敛到终态

说明：

- 该项不作为 GateE 冻结门禁
- 相关执行恢复能力主要继续依赖 GateD 已冻结执行闭环

---

## 5. 测试层级建议

### 单元测试

- 状态机推进
- dedupKey 生成
- 窗口判断
- 运行串行化

### 集成测试

- 注册 / 启停 / 手动 trigger
- `strategyRunId` 到订单血缘
- 调度任务到执行链打通

### 回归测试

- `PlaceOrderCommand.strategyId` 兼容债务收口
- Binance reconcile 降噪不破坏现有恢复链
- 返回模型统一不破坏 GateD 闭环
