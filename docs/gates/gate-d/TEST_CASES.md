# GateD TEST_CASES
# GateD 验收用例

> 状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。  
> 当前状态基线：**截至 2026-03-15 的已实现与已验证事实**。

## UC-D1：Paper LIMIT -> Cancel
- 当前状态：`[x] 已完成`
- 前置：paper profile 可用
- 步骤：提交远离盘口 LIMIT 单，再发撤单
- 预期：
  - `orders` 终态为 `CANCELLED`
  - `trades=0`
  - `ledger_entries=0`
  - `event_store` 存在 place / ack / cancel / cancelAck
- 当前证据：`trace_id=trc-paper-ucd1-20260315-1024`，`recoveryRunOnce(processed_events=0, processed_ledger=0, invalid_transitions=0)`，未观察到重复成交、重复记账、状态回退

## UC-D2：Paper MARKET -> Fill
- 当前状态：`[x] 已完成`
- 前置：paper executor 可撮合
- 步骤：提交 MARKET 单
- 预期：
  - `orders` 终态为 `FILLED`
  - `trades>=1`
  - `ledger_entries>=2`
  - `positions` 更新可见
  - `GET /__gated/orders/{orderId}` 可读取最小订单视图
  - `GET /__gated/orders/{orderId}/trade` 可读取成交视图
  - `GET /__gated/positions/{accountId}/{symbol}` 可读取持仓视图
  - `GET /__gated/accounts/{accountId}` 可读取账户快照，不再返回 `404`

## UC-D3：精度非法拒绝
- 当前状态：`[x] 已完成`
- 步骤：提交不符合 tick / lot 的订单
- 预期：
  - 风控拒绝
  - `orders` 不进入真实执行
  - `audit_logs` 与 `event_store` 有拒绝记录

## UC-D4：最小名义金额不足拒绝
- 当前状态：`[x] 已完成`
- 步骤：提交小于 minNotional 的订单
- 预期：同 UC-D3

## UC-D5：重复 idempotency key 拦截
- 当前状态：`[x] 已完成`
- 步骤：重复发送同一 `idempotencyKey`
- 预期：第二次被拦截，且不进入 adapter

## UC-D6：Reconcile 修正非终态订单
- 当前状态：`[~] 部分完成`
- 步骤：制造一个停留在非终态的订单，再执行 reconcile
- 预期：状态与 fills 收敛到真实事实
- 当前说明：最小样本已证明 reconcile 不会引入异常副作用，但更强的一致性样本仍顺延治理

## UC-D7：Recovery 重启恢复
- 当前状态：`[~] 部分完成`
- 步骤：执行中重启应用，再执行 recovery
- 预期：
  - 非终态订单被重新确认
  - 未完成投影被补齐
  - 无重复成交、重复记账

## UC-D8：WS 断连降级
- 当前状态：`[~] 部分完成`
- 步骤：断开私有 WS，再观察 degrade + reconcile
- 预期：
  - 有 `WS_DISCONNECTED / WS_DEGRADE_COMPLETED / WS_RECONNECTED`
  - 订单与成交最终仍正确收敛

## UC-D9：OKX LIMIT -> Cancel
- 当前状态：`[x] 已完成`
- 步骤：真实或 demo 环境执行最小 LIMIT -> cancel
- 预期：
  - `ACCEPTED -> CANCELLED`
  - `external_order_id` 可见
  - `trades=0`
  - `ledger_entries=0`
- 当前证据：real OKX UseCase-A / C 与 place/cancel 两侧 query-confirm 样本均已收口

## UC-D10：Binance LIMIT -> Cancel
- 当前状态：`[x] 已完成`
- 步骤：真实或 testnet 环境执行最小 LIMIT -> cancel
- 预期：同 UC-D9
- 当前证据：`trace_id=trc-ucd10-binance-place-0315120330 / trc-ucd10-binance-cancel-0315120400`，最终 `orders=CANCELLED / trades=0 / ledger_entries=0`，手工 `reconcile / recovery` 无异常副作用
