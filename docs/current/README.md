# docs/current/README.md
# Current Stage（当前阶段入口）

当前阶段：**GateF（研究 / 回测 / 评估能力）**

当前状态：**GateE 已完成并冻结；GateF-DOC-1 已完成；后续进入 GateF-1。**

---

## 1. 当前阶段结论

- GateD 已冻结，只作历史执行域证据
- GateE 已完成并冻结，不再继续扩展功能
- current 目录不再代表 GateE 开发中状态
- current 目录现在承载 GateF 当前入口

---

## 2. GateE 冻结结论

已确认的 GateE 最终完成事实：

- `strategy_definitions`、`strategy_schedules` 已落表
- `strategy_runs`、`orders`、`trades` 已完成 GateE 语义收口
- adapter 返回层 canonical 字段与结果分类已统一
- 策略定义管理已完成
- 手动 trigger / `strategyRunId` 主链已完成
- schedule config / `scanOnce` 已完成
- `windowConfig / dedupScope / serialization` 已完成最小执行语义
- run 结果查询面已完成最小闭环

GateE 的最终能力边界固定为：

- 策略定义管理
- 手动 trigger
- schedule 管理与 `scanOnce`
- window / dedup / serialization
- run 结果查询

GateE 明确不再继续承载：

- `trigger_id` 事实表扩张
- 多实例严格一致 dedup / serialization
- ledger / risk / event / audit 的稳定 run 级完全聚合
- GateF 的研究 / 回测 / 评估能力

---

## 3. GateE 遗留债务与后续归属

- `PlaceOrderCommand.strategyId`
  - 仍是兼容债务
  - 后续归属：**非 GateF**，应放在执行域契约清理 / v1.x 演进中处理
- `trigger_id` 事实表未落
  - 后续归属：**非 GateF 默认项**，仅在事实链确有需要时再单独决策
- `ledger / risk / event / audit` 未形成稳定 run 级直接聚合
  - 后续归属：**非 GateF 默认项**，应放在事实链与查询模型演进中处理
- 多实例严格一致的 dedup / serialization 未解决
  - 后续归属：**非 GateF**，应放在生产编排硬化阶段处理
- 兼容字段 / 兼容访问器仍存在
  - 如 `venue / exchange / external_order_id`
  - 后续归属：**非 GateF**，应放在 canonical 字段清理批次中处理

---

## 4. GateF 当前只允许做什么

当前已完成：

- GateF 输入清单
- GateF 启动前约束
- GateF 主卷宗
- GateF 待决策问题清单

当前明确不允许：

- 编写 GateF 主体设计正文
- 编写 GateF 业务代码
- 回头重写 GateE 主链

---

## 5. GateF 输入清单

详见：`docs/current/GATEF_INPUTS.md`

当前已经确认的输入资产：

- `strategy_definitions`
- `strategy_schedules`
- `strategy_runs`
- `orders` / `trades`
- canonical adapter 结果模型
- GateE run 查询面
- GateE 冻结卷宗

---

## 6. 当前入口跳转

- GateF 输入清单：`docs/current/GATEF_INPUTS.md`
- GateF 主卷宗：`docs/gates/gate-f/README.md`
- 当前阶段摘要：`docs/current/GATE_CHECKLIST.md`
- 最近已冻结 Gate：`docs/gates/gate-e/README.md`
- GateE 冻结 checklist：`docs/gates/gate-e/GATE_E_CHECKLIST.md`
