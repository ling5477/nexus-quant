# GateE DB_SCHEMA
# GateE 数据模型说明

## 1. 目标

GateE 数据模型的目标不是立刻疯狂加表，而是先确认：
- 当前已有哪些表和字段已经能承接 GateE 起步
- 哪些缺口必须在 GateE-1 / GateE-2 才新增 migration
- 哪些语义必须先冻结，避免表一建就歪

---

## 2. 当前已存在的 GateE 相关事实

### 2.1 `strategy_runs`
当前已存在字段：
- `run_id`
- `strategy_id`
- `account_id`
- `status`
- `started_at`
- `ended_at`
- `trace_id`
- `created_at`

当前已存在索引：
- `idx_strategy_runs_strategy_started`
- `idx_strategy_runs_account_started`

### 2.2 `orders`
当前已存在 GateE 关键字段：
- `strategy_run_id`
- `venue`
- `client_order_id`
- `external_order_id`
- `trace_id`

当前已存在索引：
- `idx_orders_strategy_run_id`
- `uq_orders_account_client_order`
- `idx_orders_venue_external_order_id`

### 2.3 结论
当前 schema 已经能支撑“策略运行 -> 下单 -> 反查订单”这条最小血缘链，但还不够支撑“策略定义 / 调度计划 / 运行窗口 / 结果摘要”这几层 GateE 主体能力。

---

## 3. 当前明确缺口

以下能力当前表结构还没有正式承接：

### 3.1 策略定义层
缺：
- 策略定义主表（例如 `strategy_definitions`）
- 状态、参数、调度配置、更新时间等字段

### 3.2 调度配置层
缺：
- 调度计划主表（例如 `strategy_schedules`）
- `trigger_type / cron_expr / timezone / next_fire_at / misfire_policy`

### 3.3 策略运行层增强
`strategy_runs` 当前建议后续补充但未落库的字段：
- `trigger_source`
- `window_start`
- `window_end`
- `request_id`
- `idempotency_key`
- `result_summary_json`
- `updated_at`
- `version`
- `error_code / error_message`

---

## 4. GateE 迁移策略

### 4.1 当前规则
- GateE 文档完善批**不新增 migration**
- 只有在 GateE-1 / GateE-2 代码实现真正需要时，才新增 `V5+`
- 禁止为了“看起来完整”造空 migration

### 4.2 新 migration 触发条件
满足以下任一条件时，允许新增 GateE migration：
- 需要持久化策略定义与启停状态
- 需要持久化调度计划与运行窗口
- 需要把当前文档已冻结的 `strategyRunId` 运行语义落到表字段
- 需要建立策略运行读侧必须依赖的新索引

### 4.3 设计顺序
1. 先冻结契约与状态机
2. 再决定表结构
3. 再写 migration
4. 最后更新查询面与测试

---

## 5. 建议的最小新增表（候选，不是当前事实）

### 5.1 `strategy_definitions`
建议字段：
- `strategy_id` PK
- `strategy_type`
- `account_id`
- `venue`
- `status`
- `params_json`
- `schedule_config_json`
- `trace_id`
- `created_at`
- `updated_at`

### 5.2 `strategy_schedules`
建议字段：
- `schedule_id` PK
- `strategy_id`
- `trigger_type`
- `cron_expr`
- `timezone`
- `next_fire_at`
- `misfire_policy`
- `enabled`
- `trace_id`
- `created_at`
- `updated_at`

---

## 6. 结论

- 当前 GateE 的 schema 起点不是 0，而是“已有运行血缘、没有定义与编排层”。
- `strategy_runs` 继续作为 GateE 的最小运行事实表。
- GateE 真正的 schema 扩展必须从策略定义层和调度配置层开始，而不是先乱给订单表塞新字段。
