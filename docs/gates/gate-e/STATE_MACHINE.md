# GateE STATE_MACHINE
# GateE 状态机说明

## 1. 目标

GateE 要新增的是**策略定义状态机**和**策略运行状态机**，不是去篡改 GateD 已冻结的订单状态机。

一句话：
- GateD 管订单怎么活
- GateE 管策略何时跑、跑成什么样

---

## 2. 状态机分层

### 2.1 策略定义状态（`strategyId` 级）
建议最小状态：
- `DRAFT`
- `ACTIVE`
- `PAUSED`
- `DISABLED`

### 2.2 策略运行状态（`strategyRunId` 级）
建议最小状态：
- `CREATED`
- `READY`
- `DISPATCHING`
- `RUNNING`
- `SUCCEEDED`
- `PARTIAL_SUCCESS`
- `FAILED`
- `CANCELLED`
- `SKIPPED`

### 2.3 订单状态（GateD 既有）
- 继续沿用 GateD `NEW / ACCEPTED / FILLED / CANCELLED / REJECTED ...`
- GateE 不重写、不并表、不偷改语义

---

## 3. 基本流转

### 3.1 策略定义
`DRAFT -> ACTIVE -> PAUSED -> ACTIVE -> DISABLED`

规则：
- `DISABLED` 视为不可再自动调度
- `PAUSED` 可恢复到 `ACTIVE`

### 3.2 策略运行
`CREATED -> READY -> DISPATCHING -> RUNNING -> SUCCEEDED | PARTIAL_SUCCESS | FAILED | CANCELLED | SKIPPED`

说明：
- `SKIPPED` 用于窗口不满足、去重命中、调度条件不成立
- `PARTIAL_SUCCESS` 用于一次运行产生多笔动作，其中部分成功、部分失败

---

## 4. 推进来源

### 4.1 定义层推进
- 注册策略
- 启用策略
- 暂停策略
- 禁用策略

### 4.2 运行层推进
- 手动触发
- scheduler 触发
- 恢复触发
- 执行结果回传

### 4.3 执行域反馈
- 下单成功 / 拒绝 / 失败
- 订单终态回传
- 成交与账本完成情况

---

## 5. 硬规则

- `strategyId` 状态与 `strategyRunId` 状态必须分层
- 已终态的 `strategyRunId` 不允许回退到 `RUNNING`
- 调度窗口不满足时，不创建伪运行；若已创建，必须显式进入 `SKIPPED`
- 一次运行是否允许再次触发，必须由去重 / 串行化规则决定
- scheduler 不允许直接推进订单状态，它只能推进策略运行状态

---

## 6. 与当前代码的对应关系

当前代码里：
- `strategy_runs.status` 已存在，但状态值语义未冻结
- `StrategyScheduler` 只有 `start/stop/restart`，不足以表达完整 GateE 运行状态机
- `GateBDemoStrategyRunner` 只证明过“定时触发”能力，不等于 GateE 状态机实现

结论：
- GateE 第一批必须先把状态值和推进动作收口，然后再写正式实现
