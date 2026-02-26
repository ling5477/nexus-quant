# Gate B NUMERIC POLICY（数值与精度）

> Gate B 原则：继承 Gate A 的数值策略（nq-common 中的归一化占位实现与约束）。
> Gate B 在交易/记账场景补充“价格/数量/手续费/舍入”的最小口径。

---

## 1. 基线引用（Gate A）
- 价格/数量的存储类型：遵循 Gate A 既定策略（BigDecimal 或 scaled long 的占位）
- 禁止浮点（double/float）参与金额计算
- 在此引用 Gate A 的 NUMERIC_POLICY.md 关键结论 (docs/gates/gate-a/NUMERIC_POLICY.md)
> TODO: 在此引用 Gate A 的 NUMERIC_POLICY.md 关键结论（或直接写“以 GateA 文件为准”）

---

## 2. Gate B 补充口径

### 2.1 价格（price）
- 建议 scale：8（币圈常见）
- 舍入：HALF_UP（或按交易所规则，GateB 先统一）

### 2.2 数量（quantity）
- 建议 scale：8 或按交易对最小下单单位（GateB 可先统一 8）

### 2.3 手续费（fee）
- GateB 可先固定为 0（但字段/契约保留）
- 后续 GateC 接入真实交易所规则再实现

### 2.4 记账平衡
- 平衡校验按“同币种同事件”聚合
- 若出现因舍入导致差异：
    - GateB 选择：直接 fail 并记录 risk_event（推荐）
    - 或：引入 rounding adjustment entry（GateC 再考虑）

---

## 3. 禁止事项
- 禁止用 double 表示 price/qty/fee
- 禁止跳过统一归一化入口写 DB