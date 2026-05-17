# docs/current/GATEG_INPUTS.md
# GateG 输入清单（前端控制台与联调）

> 历史输入参考：RC1 当前阶段不再以此文档作为执行入口。

本文档整理 GateG-DOC-1 的输入依据与开工边界。

---

## 1. GateF 已交付输入资产

### 认证与安全

- `POST /api/auth/login`
- `GET /api/auth/me`
- Bearer token 认证链
- 最小角色模型：`ADMIN / OPERATOR / VIEWER`

### 策略与调度

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

### 研究 / 回测 / 评估 / 发布

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

### 交易验证

- `GET /api/trading/orders/{orderId}`
- `GET /api/trading/orders/{orderId}/trade`
- `GET /api/trading/positions/{accountId}/{symbol}`
- `GET /api/trading/accounts/{accountId}`
- `POST /api/trading/orders`
- `POST /api/trading/orders/cancel`
- `POST /api/trading/reconciliation/run-once`
- `POST /api/trading/recovery/run-once`

---

## 2. GateG 当前仓库事实

- `frontend/` 当前只有 `package.json`、`package-lock.json` 与 Playwright 依赖
- 前端工程骨架尚未正式建立
- 后端 `/api/**` 已完成正式路由收口
- 认证链已不是 stub / noop
- 研究 / 回测 / 交易验证接口已具备首批联调条件

---

## 3. GateG 前置约束

- GateG 不以前置数据库大改为条件
- GateG 不回头重写 GateF 主链
- GateG 不回头承接 GateE / GateD 历史执行债务
- GateG 只在联调中补最小前端向接口
- 旧 `/__gated/**` 不进入新前端契约

---

## 4. GateG 必做范围

- 前端工程骨架
- 登录页与鉴权守卫
- 布局、菜单、路由
- 策略定义 / 调度 / 运行页面
- 研究配置 / 回测配置 / 回测运行页面
- 回测详情中的 `sim_* / evaluation / publish` 视图
- 交易验证操作页
- Playwright 关键链路回归

---

## 5. 当前不纳入 GateG-DOC-1 的项

- 数据库重构
- 新交易所接入
- 合约 / 杠杆 / 期货扩展
- 研究平台二次扩张
- 多实例严格一致编排硬化

---

## 6. 对应卷宗

- `docs/gates/gate-g/README.md`
- `docs/gates/gate-g/ARCHITECTURE.md`
- `docs/gates/gate-g/CONTRACTS.md`
- `docs/gates/gate-g/PR_SPLIT_PLAN.md`
- `docs/gates/gate-g/TEST_CASES.md`
