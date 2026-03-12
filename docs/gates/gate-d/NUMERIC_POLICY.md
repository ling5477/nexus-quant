# GateD NUMERIC_POLICY

> GateD 数值规范。  
> 目的：统一价格、数量、金额、手续费、平均价、最小名义金额、持仓与快照的计算与持久化规则，避免“都像对，但账就是对不上”的幽灵错误。

---

## 1. 总原则

- 价格、数量、金额、手续费、均价一律使用 `BigDecimal`
- 禁止 `float` / `double` 参与业务计算
- 展示值与内部计算值分离
- 交易所原始精度与系统内部精度必须明确区分
- 任何舍入、截断、比较规则都必须显式写出，禁止依赖默认行为

---

## 2. 统一类型策略

### 2.1 业务计算
以下字段统一使用 `BigDecimal`：

- price
- quantity
- quoteAmount / notional
- fee
- avgPrice
- balance
- available
- frozen
- positionQuantity
- avgCost

### 2.2 禁止事项
- 禁止 `new BigDecimal(double)`
- 禁止 `BigDecimal.equals()` 作为业务数值相等判断
- 禁止在 adapter / core / ledger 中混用不同 rounding mode 而不说明

推荐：

- 构造：`new BigDecimal(String)` 或数据库直接映射
- 比较：`compareTo()`

---

## 3. 舍入与 scale 规则

### 3.1 价格
- 价格精度优先以交易所 instrument / symbol 规则为准
- 内部存储允许高于展示精度，但对外下单前必须按交易所精度标准化
- 若需要舍入，默认使用 `RoundingMode.DOWN`，避免超出交易所允许范围

### 3.2 数量
- 数量精度优先以交易所 lot size / quantity step 规则为准
- 对外下单前必须按交易所数量步长标准化
- 默认使用 `RoundingMode.DOWN`

### 3.3 金额 / 名义金额
- 名义金额 = `price * quantity`
- 名义金额计算中间值不得提前截断
- 最终与最小名义金额比较前，可按统一业务 scale 规范化，但不得改变不利于交易所校验的语义

### 3.4 手续费
- 手续费按交易所原始回报精度保存
- 展示层可格式化，账本与审计层不得丢精度

---

## 4. 比较规则

### 4.1 一律使用 compareTo
- `x.compareTo(y) == 0`：数值相等
- `x.compareTo(y) > 0`：大于
- `x.compareTo(y) < 0`：小于

### 4.2 零值判断
统一使用：

- `value == null`：缺失
- `value.compareTo(BigDecimal.ZERO) == 0`：零
- `value.compareTo(BigDecimal.ZERO) < 0`：负数

禁止把 `null` 当作 `0`

---

## 5. 风控中的数值规则

### 5.1 最小名义金额
- `minNotionalCheck = normalizedPrice * normalizedQuantity`
- 参与比较的 `price / quantity` 必须先完成交易所精度归一
- 对市价单若没有最终成交价，按明确文档约定的参考价口径处理，不得临时猜

### 5.2 最大下单额
- 若规则按 quote 计量，则按 `price * quantity`
- 若规则按 base 计量，则按 `quantity`
- 规则定义必须在 `RISK_RULES.md` 中明确

### 5.3 重复请求
- 幂等判断不依赖金额数值相等；依赖 `idempotencyKey / clientOrderId / requestId`

---

## 6. 账本与均价规则

### 6.1 平均成交价
- `avgPrice = totalExecutedQuote / totalExecutedBase`
- 累积计算前不得逐笔提前四舍五入
- 最终展示可按展示规则格式化

### 6.2 持仓平均成本
- 现货阶段按明确投影算法计算
- 投影中间值保留足够精度，避免多笔成交累计后偏差放大

### 6.3 账本金额
- ledger posting 保留原始交易精度
- 对账时以账本存储值为准，不以展示值为准

---

## 7. 数据库存储建议

建议按现有 schema 规范统一为：

- price：`DECIMAL(38, 18)` 或等效
- quantity：`DECIMAL(38, 18)` 或等效
- amount / fee / balance：`DECIMAL(38, 18)` 或等效

如现有表已使用其他精度：

- GateD 不强推全库重构
- 但新增字段与新表应尽量按统一精度规范
- 精度不足的已知风险必须记录在 `WORK.md`

---

## 8. 交易所精度与内部精度

- 交易所精度：对外请求必须遵守
- 内部精度：用于计算、投影、账本与审计
- adapter 层负责把内部数值标准化到交易所要求
- core / risk 不直接依赖交易所原始字符串规则

---

## 9. 测试要求

以下场景必须覆盖测试：

- 精度合法 / 非法
- 步长合法 / 非法
- 最小名义金额临界值
- `compareTo` 与 `equals` 语义差异
- 多笔 fills 后平均价与累计金额正确
- 重复 fills 不重复记账
