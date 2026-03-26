# Modules（GateG 导航摘要）

> Top-Level Navigation Notice
> - 本文件是根级导航摘要，用于帮助快速定位 GateG 模块分工，不是当前阶段的 Source of Truth。
> - 当前阶段的模块职责事实以 `docs/current/*`、`docs/gates/gate-g/MODULES.md` 为准。

## 1. frontend 模块职责

### frontend
GateG 当前正式开工对象：
- React + TypeScript + Vite 前端工程骨架
- 登录页、鉴权守卫、基础布局、菜单路由
- 策略定义 / 调度 / 运行页面
- 研究配置 / 回测配置 / 回测运行页面
- 回测运行详情中的 `sim_* / evaluation / publish` 视图
- 交易验证操作页
- Playwright 关键链路回归

## 2. backend 模块职责

### nq-api
GateG 联调期继续提供：
- `/api/auth/**`
- `/api/strategies/**`
- `/api/strategy-schedules/**`
- `/api/strategy-runs/**`
- `/api/research-configs/**`
- `/api/backtest-configs/**`
- `/api/backtest-runs/**`
- `/api/trading/**`

### nq-app
- 继续负责运行装配、profile、过滤器链与安全配置
- 不承载前端页面逻辑

### nq-auth / nq-security / nq-gateway
- 继续提供最小真实认证链
- 作为 GateG 登录与鉴权守卫的后端基础

### nq-research / nq-backtest / nq-eval
- 作为 GateG 研究 / 回测 / 评估页面的数据来源
- 不在 GateG 中重写研究或回测主链

### nq-core / nq-ledger / nq-risk / nq-scheduler / nq-adapter-*
- 继续维持 GateD~GateF 已冻结边界
- 仅在联调缺口明确时补最小接口或字段

## 3. 当前不作为主改对象

- 数据库大改
- 执行域重构
- 新交易所接入
- 研究平台二次扩张
- 合约 / 杠杆 / 期货能力
