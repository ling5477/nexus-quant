# GateD RECOVERY_RUNBOOK

> GateD 恢复与补偿操作手册。  
> 目标：当执行链路出现断连、漏回报、状态不一致、投影滞后等问题时，能按统一流程排查、确认和收敛。

---

## 1. 适用范围

适用于以下场景：

- 下单后长时间无终态
- WS 断连、订阅失败、登录失效
- 本地状态滞后于交易所状态
- fills 已到但 ledger / position / account 投影未完成
- 进程重启后存在未闭合订单
- 查询超时导致执行结果不确定

---

## 2. 总体原则

1. 先查事实，再做动作
2. 未确认前禁止盲重试下单
3. 先 `query-confirm`，再决定是否需要 reconcile / recovery
4. 手工介入优先补事件、补确认，不直接改业务表
5. 所有人工动作必须记录 trace_id、操作者、原因、时间

---

## 3. 关键定位键

排障时优先使用以下字段交叉定位：

- `trace_id`
- `request_id`
- `client_order_id`
- `external_order_id`
- `account_id`
- `symbol`
- `venue`

---

## 4. 常见场景处理

### 场景 A：下单后无终态

症状：

- 本地下单请求已发出
- 订单停留在 `PENDING_SUBMIT` / `SUBMITTED`
- 长时间未收到终态

处理步骤：

1. 用 `request_id` / `client_order_id` 查询本地日志
2. 查询 `orders`、`event_store`、`audit_logs`
3. 调用 adapter `query order`
4. 若交易所已有订单事实：
   - 绑定 `external_order_id`
   - 推进状态
   - 拉取 fills
   - 触发投影补偿
5. 若交易所无订单事实：
   - 标记为提交失败或待人工复核，按状态机规则处理
6. 记录补偿动作与结果

### 场景 B：WS 断连或消息丢失

症状：

- WS 客户端断连、认证失败、订阅失败
- 本地订单状态停滞
- REST 查询能查到更新事实

处理步骤：

1. 检查 WS 健康日志
2. 触发一次受限 REST 兜底扫描
3. 对非终态订单执行 `query order + pull fills`
4. 恢复订阅后继续由 WS 加速，但不覆盖已确认事实
5. 统计 degrade 命中次数并记录

### 场景 C：fills 已到，但 ledger / position / account 未更新

症状：

- trade / fill 已写入
- ledger_entries 或 position/account snapshot 未更新

处理步骤：

1. 以 `exchange_trade_id` / fill 唯一键定位
2. 检查 ledger posting 幂等键是否存在
3. 若 posting 未完成：
   - 触发投影补偿
   - 禁止重新拉取同一 fill 后重复记账
4. 若 posting 已完成但快照未更新：
   - 触发快照重建 / 局部重算
5. 验证补偿后状态一致

### 场景 D：进程重启后的恢复

处理步骤：

1. 启动后扫描非终态订单
2. 对每笔订单执行：
   - `query order`
   - `pull fills`
   - 状态推进
   - 投影补偿
3. 记录恢复批次 trace_id
4. 验证未闭合订单数量是否下降
5. 若仍有未知态，进入人工复核

---

## 5. 手工排查最小路径

最小查询顺序：

1. 查应用日志（trace_id / request_id）
2. 查 `orders`
3. 查 `event_store`
4. 查 `audit_logs`
5. 查 `trades`
6. 查 `ledger_entries`
7. 查 `position` / `account snapshot`
8. 最后查交易所 `query order / fills`

---

## 6. 禁止动作

- 禁止在未确认交易所事实前重复下单
- 禁止直接手改订单终态绕过状态机
- 禁止直接插入伪成交来“补平”
- 禁止修改已执行 Flyway
- 禁止仅凭肉眼日志判断成功而不核对表数据

---

## 7. 恢复完成判定

满足以下条件才视为恢复完成：

- 订单状态与交易所事实一致
- fills 去重无重复
- ledger posting 幂等完成
- position / account snapshot 已同步
- 关键日志与事件完整
- 本次恢复动作已写入审计或 runbook 记录

---

## 8. 需要同步更新 runbook 的触发条件

以下变更一旦发生，必须同步更新本手册：

- 新增恢复 job
- 修改 reconcile 扫描范围
- 修改 query-confirm 规则
- 修改幂等键设计
- 修改事件表或审计表语义
- 修改订单终态定义
