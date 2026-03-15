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

## E-003：不提前复制 GateD 全套文档到 GateE

- 日期：2026-03-15
- 状态：已决定
- 决策：GateE 只建立最小可开工文档骨架，不提前复制 `CONTRACTS / DB_SCHEMA / STATE_MACHINE / TEST_CASES` 等强依赖实现细节的文档。

---

## E-004：current 与 gate-e 的角色分工

- 日期：2026-03-15
- 状态：已决定
- 决策：
  - `docs/current/*` 负责当前阶段入口与摘要
  - `docs/gates/gate-e/*` 负责 GateE 阶段卷宗
  - `docs/gates/gate-d/*` 仅作冻结证据

---

## E-005：schema / metadata 收口必须排在 GateE 前两批

- 日期：2026-03-15
- 状态：已决定
- 决策：schema / metadata 收口必须排在 GateE 前两批，不得过度后置，以免后续策略接入与编排设计继续建立在历史噪音之上。
