# 恢复与回放 Runbook（RECOVERY_RUNBOOK）

> 目标：当服务崩溃、事件乱序、或投影表损坏时，能够通过**账本/事件**重建核心状态。

---

## 1. 关键概念

- **事实（Facts）**：不可变事件（event_store）与不可变账本（ledger_entries）
- **投影（Projections）**：orders/positions 等可重建表（允许丢失）
- **恢复（Recovery/Rebuild）**：从事实重算投影，校验一致性

---

## 2. 触发场景

- 服务异常重启后状态不一致
- positions/订单状态投影损坏或出现不可解释跳变
- 对账发现余额/仓位与 ledger 不一致
- 事件消费落后/重复导致状态异常

---

## 3. 恢复步骤（建议流程）

1. **冻结写入**
   - 启用 Kill Switch（阻止新下单）
   - 暂停事件消费（若使用 MQ）
2. **选择恢复范围**
   - 全量恢复：从 genesis 重算
   - 增量恢复：从最近快照点重算（如有 snapshot）
3. **重建投影**
   - orders：按事件序列重放
   - positions：按 trades/ledger 聚合重算
   - accounts：按 ledger 计算余额与可用
4. **一致性校验**
   - ledger 平衡校验（sum(delta) + initial = current）
   - positions 与 trades 聚合对齐
   - orders 状态机合法性校验（无非法跃迁）
5. **解除冻结**
   - 恢复事件消费
   - 解除 Kill Switch

---

## 4. 必须具备的校验输出（Gate A：文档冻结）

- 恢复报告（JSON 或 Markdown）至少包含：
  - 时间范围、账户范围、处理事件数、处理账本条数
  - 发现的问题计数（非法状态跃迁/余额不平/重复事件等）
  - 修复动作（重建了哪些投影表）

---

## 5. 最小演练（建议纳入 CI）

- 构造用例：
  - 重复事件（同 client_order_id）
  - 乱序 trade 回执
  - 中途崩溃（投影写一半）
- 预期：
  - 重建后余额/仓位一致
  - 不产生额外“重复成交/重复扣款”

