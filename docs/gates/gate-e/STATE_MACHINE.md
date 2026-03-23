# GateE STATE_MACHINE
# GateE 状态机与推进事件

GateE 新增的是“策略定义、调度作业、触发请求、策略运行”四层状态机。订单状态机继续以 GateD 为准。

---

## 1. 总原则

- `strategyId` 级状态与 `strategyRunId` 级状态必须分离
- 调度状态机不能直接推进订单状态
- 已终态的 `strategyRunId` 不允许回退到运行态
- 重试必须先确认是否已触发过，不能把 retry 写成重复下单

---

## 2. StrategyDefinition 状态机

对象：`strategyId`

状态：

- `DRAFT`
- `ACTIVE`
- `PAUSED`
- `DISABLED`

推进：

- 注册成功：`null -> DRAFT`
- 启用：`DRAFT -> ACTIVE`
- 暂停：`ACTIVE -> PAUSED`
- 恢复：`PAUSED -> ACTIVE`
- 禁用：`DRAFT|ACTIVE|PAUSED -> DISABLED`

硬规则：

- `DISABLED` 后不再自动调度
- `PAUSED` 不产生新的 scheduled trigger
- 手动 trigger 是否允许命中 `PAUSED`，需在 GateE-1 明确；默认不允许

---

## 3. ScheduleJob 状态机

对象：`scheduleJobId`

当前仓库尚无此表或类，但 GateE-2 必须有清晰语义。

状态：

- `CREATED`
- `SCHEDULED`
- `PAUSED`
- `BLOCKED`
- `DISABLED`

推进：

- 创建调度配置：`null -> CREATED`
- 注册到调度器：`CREATED -> SCHEDULED`
- 暂停：`SCHEDULED -> PAUSED`
- 恢复：`PAUSED -> SCHEDULED`
- 因依赖异常阻断：`SCHEDULED -> BLOCKED`
- 人工恢复：`BLOCKED -> SCHEDULED`
- 彻底禁用：`CREATED|SCHEDULED|PAUSED|BLOCKED -> DISABLED`

---

## 4. TriggerRequest 状态机

对象：`requestId`

说明：

- 当前不单独落表，但需要明确逻辑状态
- 触发请求统一覆盖 manual / scheduler / recovery

状态：

- `RECEIVED`
- `DEDUPED`
- `REJECTED`
- `ACCEPTED`
- `EXPIRED`

推进：

- 请求到达：`null -> RECEIVED`
- 去重命中：`RECEIVED -> DEDUPED`
- 窗口 / 状态不满足：`RECEIVED -> REJECTED`
- 通过校验并生成 `strategyRunId`：`RECEIVED -> ACCEPTED`
- 超过允许生效时间：`RECEIVED -> EXPIRED`

硬规则：

- `DEDUPED` 不再生成新的 `strategyRunId`
- `REJECTED` 必须记录原因
- `ACCEPTED` 后必须能通过 `requestId` 找到对应 `strategyRunId`

---

## 5. StrategyRun 状态机

对象：`strategyRunId`

当前事实：

- `strategy_runs.status` 已存在，但状态值尚未冻结

GateE 冻结的最小状态：

- `CREATED`
- `READY`
- `DISPATCHING`
- `RUNNING`
- `SUCCEEDED`
- `PARTIAL_SUCCESS`
- `FAILED`
- `CANCELLED`
- `SKIPPED`

推进：

- 触发请求通过并建运行：`null -> CREATED`
- 参数 / 窗口 /并发检查通过：`CREATED -> READY`
- 开始投递执行请求：`READY -> DISPATCHING`
- 首个执行请求被接受：`DISPATCHING -> RUNNING`
- 全部动作成功：`RUNNING -> SUCCEEDED`
- 部分成功部分失败：`RUNNING -> PARTIAL_SUCCESS`
- 运行失败：`CREATED|READY|DISPATCHING|RUNNING -> FAILED`
- 人工取消或恢复终止：`CREATED|READY -> CANCELLED`
- 去重命中或窗口不满足时显式记录：`CREATED -> SKIPPED`

硬规则：

- `SUCCEEDED / PARTIAL_SUCCESS / FAILED / CANCELLED / SKIPPED` 都是终态
- 终态不允许回退到 `RUNNING`
- 是否创建 `SKIPPED` 运行必须在实现里保持一致，不能一部分静默拒绝、一部分写记录

---

## 6. ExecutionRequest 逻辑状态

对象：当前文档层概念，对应仓库中的 `PlaceOrderRequest -> PlaceOrderCommand`

状态：

- `PREPARED`
- `RISK_CHECKING`
- `READY_TO_SUBMIT`
- `SUBMITTING`
- `SUBMITTED`
- `REJECTED`
- `FAILED`

关系：

- 该状态机不替代 GateD 订单状态机
- 只是说明 StrategyRun 如何观察执行请求阶段

映射：

- `SUBMITTED` 之后，订单终态继续由 GateD 管理

---

## 7. 主链流转

### 7.1 手动触发

`StrategyDefinition.ACTIVE -> TriggerRequest.RECEIVED -> ACCEPTED -> StrategyRun.CREATED -> READY -> DISPATCHING -> RUNNING -> terminal`

### 7.2 定时触发

`ScheduleJob.SCHEDULED -> TriggerRequest.RECEIVED -> dedup/window guard -> ACCEPTED|DEDUPED|REJECTED -> StrategyRun`

### 7.3 retry / recovery

GateE 只允许受控 retry：

- 先根据 `requestId / dedupKey / strategyRunId` 判断是否已接受
- 若运行未生成执行请求，可在同一运行内恢复
- 若执行链已出单，必须先 `query-confirm`，不得直接重放下单

---

## 8. 与 GateD 的分工

- GateD 管 `OrderStatus`
- GateE 管 `StrategyDefinition / ScheduleJob / TriggerRequest / StrategyRun`

任何把二者混成一套状态的实现，都违反当前阶段边界
