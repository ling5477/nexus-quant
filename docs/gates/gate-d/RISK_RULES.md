# GateD RISK_RULES
# GateD 风控规则说明

## 1. 目标

GateD 风控只做 pre-trade 硬规则，不做复杂组合风控。目的很单纯：在请求进入真实执行通道之前，把明显不合法、不允许、会重复、会爆炸的请求挡住。

---

## 2. 规则列表

### RISK-001：账户交易开关
- 说明：账户被禁用或未开交易权限时拒绝下单
- 输入：`accountId, venue`
- 失败结果：`ACCOUNT_TRADING_DISABLED`
- hard reject：是

### RISK-002：Symbol 允许校验
- 说明：symbol 不在允许清单或当前 venue 不支持时拒绝
- 输入：`venue, symbol`
- 失败结果：`SYMBOL_NOT_ALLOWED`
- hard reject：是

### RISK-003：精度校验
- 说明：价格或数量不符合 tick / lot / minQty 规则时拒绝
- 输入：`price, quantity, instrument metadata`
- 失败结果：`INVALID_PRECISION`
- hard reject：是

### RISK-004：最小名义金额
- 说明：价格 * 数量小于最小名义金额时拒绝
- 输入：`price, quantity, minNotional`
- 失败结果：`MIN_NOTIONAL_NOT_MET`
- hard reject：是

### RISK-005：最大下单额
- 说明：超过当前账户或系统设定的单笔上限时拒绝
- 输入：`accountId, symbol, notional`
- 失败结果：`MAX_ORDER_NOTIONAL_EXCEEDED`
- hard reject：是

### RISK-006：重复请求拦截
- 说明：相同 `idempotencyKey` 或相同业务请求重复提交时拒绝
- 输入：`idempotencyKey, clientOrderId`
- 失败结果：`DUPLICATE_REQUEST`
- hard reject：是

### RISK-007：限频拦截
- 说明：在配置窗口内同账户、同 symbol、同方向请求过多时拒绝
- 输入：`accountId, symbol, side, window`
- 失败结果：`RATE_LIMIT_EXCEEDED`
- hard reject：是

### RISK-008：Kill Switch
- 说明：系统或账户进入紧急停止状态时拒绝
- 输入：`accountId, venue`
- 失败结果：`KILL_SWITCH_TRIGGERED`
- hard reject：是

---

## 3. 规则顺序

推荐执行顺序：
1. Kill Switch
2. 账户交易开关
3. Symbol 允许校验
4. 重复请求拦截
5. 限频拦截
6. 精度校验
7. 最小名义金额
8. 最大下单额

说明：先挡掉明显非法和重复请求，再做计算型规则，省得 CPU 白忙活。

---

## 4. 审计要求

每次风控检查必须记录：
- `traceId`
- `requestId`
- `clientOrderId`
- `accountId`
- `symbol`
- `venue`
- `ruleCode`
- `passed`
- `rejectReason`

写入：
- `audit_logs`
- `event_store`（例如 `risk.event.v1`）

