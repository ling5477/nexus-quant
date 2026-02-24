# 数值精度与舍入策略（NUMERIC_POLICY）

> 目标：避免“同一笔交易在不同模块/语言下结果不一致”，保证可回放与可对账。

---

## 1. 统一原则

1. **所有金额/数量/价格在领域层统一使用 BigDecimal**（Java）或 Decimal（其他语言）。
2. **禁止在业务逻辑中使用 double/float**。
3. **每个数值类型必须定义：scale 与 rounding**，并在入站（API/事件/适配器）统一归一化。

---

## 2. 类型与默认精度（基线）

> 说明：真实精度还需结合交易所 `tickSize/lotSize`，本基线提供“默认口径”，并要求在适配器层按交易所规则再做约束/截断。

- **Price（价格）**
  - 默认：`scale = 8`
  - rounding：`RoundingMode.DOWN`（防止报出交易所不接受的价格）
- **Qty（数量）**
  - 默认：`scale = 8`
  - rounding：`RoundingMode.DOWN`
- **Amount（金额/名义价值）**
  - 默认：`scale = 8`
  - rounding：`RoundingMode.HALF_UP`
- **Fee（手续费）**
  - 默认：`scale = 8`
  - rounding：`RoundingMode.HALF_UP`
- **PnL（盈亏）**
  - 默认：`scale = 8`
  - rounding：`RoundingMode.HALF_UP`

---

## 3. 归一化策略（必须）

- 入站（HTTP/事件/交易所回执）→ 统一调用 `Numeric.normalize(type, value)`
- 出站（下单请求）→ 适配器基于交易所规则进一步：
  - `price = floor_to_tick(price, tickSize)`
  - `qty   = floor_to_step(qty, lotSize)`

---

## 4. 交易所规则对齐（Gate B/C）

Gate B 接入 OKX/Binance 后，必须补充：
- 每个 symbol 的 `tickSize/lotSize/minNotional` 拉取与缓存策略
- 对齐函数的单元测试（给定 tick/step 的边界用例）

---

## 5. 测试要求（Gate A 也要写）

- 归一化的确定性：同输入同输出（含边界）
- 截断规则：DOWN 的行为必须被测试覆盖
- 账本重算：累计误差不得跨越最小单位（scale 一致）
