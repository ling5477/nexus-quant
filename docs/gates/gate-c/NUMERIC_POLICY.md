# docs/gates/gate-c/NUMERIC_POLICY.md
# Gate C NUMERIC POLICY（CEX 精度与归一）

- 继承 GateB：禁止 double/float；价格/数量/金额用 BigDecimal。
- 下单前必须按 instruments 规则 trim：
  - tick size：价格 px 按最小价格单位截断
  - lot size/min size：数量 sz 按最小单位截断并校验最小下单量
- trades：
  - fee/fee_currency 必须按交易所返回写入
- TIMESTAMPTZ：
  - JDBC 入参统一 Timestamp.from(Instant)