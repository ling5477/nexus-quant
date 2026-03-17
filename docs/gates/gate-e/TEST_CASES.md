# GateE TEST_CASES
# GateE 验收用例

> 状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。  
> 当前状态基线：**截至 2026-03-16，文档基线已完成，业务实现未开始**。

---

## UC-E0-1：Binance background reconcile 降噪
- 当前状态：`[ ] 未完成`
- 前置：Binance WS / reconcile 能触发当前噪音场景
- 步骤：制造 credential 缺失、timestamp 漂移、cooldown 内重复触发场景
- 预期：
  - 不再连续刷屏式报错
  - 同一窗口内具备去抖
  - 审计与日志能区分真正失败与降噪跳过

## UC-E0-2：schema / metadata 收口
- 当前状态：`[ ] 未完成`
- 步骤：检查 `strategyId / strategyRunId / source / requestId / idempotencyKey` 在 contracts/core/api/schema 中的命名与语义
- 预期：
  - 文档与代码口径一致
  - 不再出现同名不同义 / 同义不同名

## UC-E0-3：返回模型一致性收尾
- 当前状态：`[ ] 未完成`
- 步骤：对比 Paper / OKX / Binance 在 place / cancel / reconcile / recovery 下的响应
- 预期：
  - 上层不再需要 venue 分支补丁
  - 未成交、成交、失败、未知态口径一致

---

## UC-E1-1：注册策略
- 当前状态：`[ ] 未完成`
- 步骤：创建一条策略定义并查询回读
- 预期：
  - `strategyId` 唯一
  - 状态、参数、调度配置可回读
  - 审计链可追踪

## UC-E1-2：人工触发策略运行
- 当前状态：`[ ] 未完成`
- 步骤：对一个已激活策略发起手动触发
- 预期：
  - 创建 `strategyRunId`
  - 运行状态从 `CREATED/READY` 推进到 `RUNNING`
  - 产生的订单带有 `strategy_run_id`

## UC-E1-3：执行血缘回传
- 当前状态：`[ ] 未完成`
- 步骤：触发运行后完成一笔订单闭环
- 预期：
  - 能从 `strategyRunId` 反查订单、成交、账本结果
  - 能从订单反查到 `strategyRunId`

---

## UC-E2-1：调度窗口控制
- 当前状态：`[ ] 未完成`
- 步骤：设置窗口内 / 窗口外触发条件
- 预期：
  - 窗口内正常运行
  - 窗口外明确 `SKIPPED`
  - 不产生脏运行记录

## UC-E2-2：去重与串行化
- 当前状态：`[ ] 未完成`
- 步骤：对同一策略在短窗口内重复触发两次
- 预期：
  - 第二次被去重或排队
  - 不产生重复订单
  - 不出现并发双跑

## UC-E2-3：运行失败与恢复
- 当前状态：`[ ] 未完成`
- 步骤：模拟运行中断或部分下单失败
- 预期：
  - `strategyRunId` 状态可落到 `FAILED / PARTIAL_SUCCESS`
  - 运行结果摘要可见
  - 不破坏 GateD 执行闭环恢复规则
