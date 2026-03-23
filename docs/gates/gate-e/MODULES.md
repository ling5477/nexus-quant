# GateE MODULES
# GateE 模块职责与越界约束

本文档按“当前已有事实 + GateE 将新增的最小职责”描述模块，不做脱离仓库结构的抽象重组。

---

## 1. 直接涉及模块

- `nq-contracts`
- `nq-core`
- `nq-scheduler`
- `nq-infra`
- `nq-api`
- `nq-app`
- `nq-risk`
- `nq-ledger`
- `nq-adapter-api`
- `nq-adapter-binance`
- `nq-adapter-okx`
- `nq-observability`

---

## 2. 模块职责

### `nq-contracts`

负责：

- 冻结 StrategyDefinition / TriggerStrategyRun / StrategyRunResult 等对外 DTO
- 收口 `strategyId / strategyRunId / requestId / dedupKey` 字段语义

禁止：

- 直接依赖具体 adapter 实现
- 在 DTO 中塞入 venue 方言字段

### `nq-core`

负责：

- 接住策略层到执行层的映射
- 统一使用 `strategyRunId` 贯穿下单命令
- 保证策略层只能通过执行域稳定入口下单

当前事实：

- 已有 `PlaceOrderRequest`
- 已有 `OrderCommandService`
- 已有 `ExecutionCommandMapper`
- 已有 `JdbcOrderRepository`

禁止：

- 把调度窗口、cron、misfire 策略塞进执行服务
- 在 core 内写 venue 分支处理策略编排逻辑

### `nq-scheduler`

负责：

- Trigger 收敛
- ScheduleJob 管理
- 运行窗口判断
- 去重
- 串行化与并发保护
- GateE-0 的 Binance background reconcile 降噪

当前事实：

- 已有 `StrategyScheduler`
- 已有 `NoopStrategyScheduler`
- 已有 `GateBDemoStrategyRunner`
- 已有 reconcile / recovery / ws degrade 相关服务

禁止：

- 直接推进订单状态
- 直接写 ledger / position / account projection
- 把历史 GateB demo runner 升级成正式编排主链

### `nq-infra`

负责：

- 持久化 schema
- Flyway migration
- GateE 新表与索引的落库

当前事实：

- 现有 migration 为 `V1 -> V4`
- 现有 migration 为 `V1 -> V5`
- `strategy_runs` 已存在
- `strategy_definitions / strategy_schedules` 已落表

禁止：

- 在语义未冻结前空造 `V5+`
- 为迎合文档假设提前建立过度表结构

### `nq-api`

负责：

- StrategyDefinition 查询
- StrategyRun 查询
- 按 `strategyRunId` 反查订单与结果

当前事实：

- 现有查询面以 order / trade / position / account 为主
- 尚无正式策略查询视图

禁止：

- 在 API 层重复实现编排规则
- 在查询层直接调用 adapter 或恢复链逻辑

### `nq-app`

负责：

- Web / profile wiring
- GateE 最小管理入口
- 手动 trigger 入口

当前事实：

- 现有以 GateD 验收入口为主
- 尚无策略注册 / 启停 / trigger 控制器

禁止：

- 在 controller 中内嵌状态推进逻辑
- 用临时 endpoint 取代正式契约

### `nq-risk`

负责：

- 对所有策略下发的执行请求继续执行 GateD pre-trade 风控

禁止：

- 感知 scheduler / cron / trigger 类型细节

### `nq-ledger`

负责：

- 成交记账
- 仓位与账户投影
- 与 `strategyRunId` 相关的结果追踪辅助

禁止：

- 直接承担调度或策略状态机责任

### `nq-adapter-api` / `nq-adapter-*`

负责：

- 统一 adapter 请求 / 响应模型
- GateE-0 返回模型一致性收尾

禁止：

- 扩散交易所私货到 GateE 主契约
- 直接感知策略调度窗口与并发规则

### `nq-observability`

负责：

- `traceId` 贯穿
- GateE 增量日志字段约束

---

## 3. 依赖方向

固定依赖方向如下：

1. `nq-app / nq-api` -> `nq-core / nq-scheduler / nq-contracts`
2. `nq-scheduler` -> `nq-core / nq-contracts`
3. `nq-core` -> `nq-risk / nq-adapter-api / nq-contracts`
4. `nq-ledger` 独立消费执行事实，不反向控制 scheduler
5. `nq-adapter-*` 只实现 `nq-adapter-api` 契约

---

## 4. GateE 必须守住的越界点

- 不允许在 `nq-core`、`nq-risk`、`nq-ledger` 中出现“按 venue 决定策略编排”的分支
- 不允许在 `nq-scheduler` 中绕过 `OrderCommandService` 直接写执行表
- 不允许在 `nq-app` 或 `nq-api` 中直接拼装 adapter 请求
- 不允许把 StrategyRun 状态推进混进订单状态推进
- 不允许用 `strategyId` 代替 `strategyRunId` 写入 `orders.strategy_run_id`
