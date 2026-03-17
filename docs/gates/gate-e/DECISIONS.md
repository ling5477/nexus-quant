# GateE DECISIONS

> 记录 GateE 实施过程中的中粒度工程决策。

---

## E-001：GateE 主定义固定为 v1.4（策略接入与调度编排）

- 日期：2026-03-15
- 状态：已决定
- 决策：GateE 主目标固定为“策略接入与调度编排”，不得被前置治理批改写。

---

## E-002：当前治理项降级为 GateE-0 前置治理

- 日期：2026-03-15
- 状态：已决定
- 决策：
  - Binance background reconcile 噪音治理
  - schema / metadata 收口
  - 返回模型一致性收尾
  统一归到 GateE-0。
- 说明：这些项只为 GateE 主体开路，不构成 GateE 主目标本身。

---

## E-003：GateE 文档从“骨架”升级到“可开工版”

- 日期：2026-03-16
- 状态：已决定
- 决策：GateE 不再停留在最小骨架文档；基于当前项目文件，补齐 `CONTRACTS / DB_SCHEMA / STATE_MACHINE / TEST_CASES / SOURCES / EVOLUTION_RULES`，作为正式开工基线。

---

## E-004：`strategyId` 与 `strategyRunId` 必须分义

- 日期：2026-03-16
- 状态：已决定
- 决策：
  - `strategyId` 表示策略定义身份
  - `strategyRunId` 表示策略运行实例身份
- 说明：当前代码存在兼容期混用，GateE 实现批次必须收口，不允许继续模糊。

---

## E-005：`strategy_runs` 继续作为 GateE 最小运行事实表

- 日期：2026-03-16
- 状态：已决定
- 决策：GateE 第一阶段继续复用现有 `strategy_runs`，先冻结运行语义；是否新增 `strategy_definitions / strategy_schedules` 等表，等 GateE-1 契约落定后再发 migration。

---

## E-006：`GateBDemoStrategyRunner` 只保留历史参考角色

- 日期：2026-03-16
- 状态：已决定
- 决策：`GateBDemoStrategyRunner` 只作为历史验证入口参考，不作为 GateE 正式调度编排主链。

---

## E-007：GateE scheduler 只负责编排，不接管执行域业务

- 日期：2026-03-16
- 状态：已决定
- 决策：GateE 中 `nq-scheduler` 只负责触发、窗口、去重、串行化与运行状态推进；订单状态、账本、持仓仍由 GateD 冻结能力负责。
