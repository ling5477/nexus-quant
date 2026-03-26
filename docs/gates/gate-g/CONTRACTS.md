# GateG 输入 / 输出 / 页面联调口径

## 1. 认证接口

- `POST /api/auth/login`
- `GET /api/auth/me`

用途：

- 登录页提交用户名密码
- 应用初始化时恢复当前用户
- 路由守卫判定登录态

---

## 2. 策略与调度接口

- `POST /api/strategies`
- `GET /api/strategies`
- `GET /api/strategies/{strategyCode}`
- `POST /api/strategies/{strategyCode}/trigger`
- `POST /api/strategy-schedules`
- `GET /api/strategy-schedules`
- `GET /api/strategy-schedules/{scheduleId}`
- `POST /api/strategy-schedules/scan-once`
- `GET /api/strategy-runs`
- `GET /api/strategy-runs/{runId}`

页面归属：

- 策略列表 / 详情 / 创建 / 触发
- 调度列表 / 详情 / 创建 / scan-once
- 运行列表 / 详情

---

## 3. 研究 / 回测 / 评估 / 发布接口

- `GET /api/research-configs`
- `GET /api/research-configs/{configId}`
- `POST /api/research-configs`
- `GET /api/backtest-configs`
- `GET /api/backtest-configs/{configId}`
- `POST /api/backtest-configs`
- `GET /api/backtest-runs`
- `GET /api/backtest-runs/{runId}`
- `POST /api/backtest-runs`
- `POST /api/backtest-runs/{runId}/start`
- `POST /api/backtest-runs/{runId}/evaluate`
- `POST /api/backtest-runs/{runId}/publish`
- `GET /api/backtest-runs/{runId}/sim-orders`
- `GET /api/backtest-runs/{runId}/sim-trades`
- `GET /api/backtest-runs/{runId}/sim-positions`
- `GET /api/backtest-runs/{runId}/pnl-snapshots`
- `GET /api/backtest-runs/{runId}/evaluation`
- `GET /api/backtest-runs/{runId}/publish`

页面归属：

- 研究配置列表 / 详情 / 创建
- 回测配置列表 / 详情 / 创建
- 回测运行列表 / 详情 / 创建 / start / evaluate / publish
- 运行详情 tab：`sim-* / evaluation / publish`

---

## 4. 交易验证接口

- `GET /api/trading/orders/{orderId}`
- `GET /api/trading/orders/{orderId}/trade`
- `GET /api/trading/positions/{accountId}/{symbol}`
- `GET /api/trading/accounts/{accountId}`
- `POST /api/trading/orders`
- `POST /api/trading/orders/cancel`
- `POST /api/trading/reconciliation/run-once`
- `POST /api/trading/recovery/run-once`

页面归属：

- 交易验证操作页
- 下单 / 撤单 / 对账 / 恢复 / 查询

---

## 5. 角色口径

- `VIEWER`：可查看列表与详情，不做写操作
- `OPERATOR`：可执行大多数业务操作
- `ADMIN`：可执行全部 GateG 操作

---

## 6. 错误与 trace 口径

- 正式 trace header：`X-Trace-Id`
- 前端错误展示以统一错误模型为准
- 所有写操作结果页必须展示关键 id：`traceId / requestId / runId / orderId`（以接口返回为准）
