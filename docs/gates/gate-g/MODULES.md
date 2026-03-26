# GateG 模块职责与越界约束

## 1. 主改模块

### `frontend`
- 前端工程骨架
- 登录态与路由守卫
- 页面、表单、列表、详情、操作流
- Playwright 回归

## 2. 联调来源模块

### `nq-api`
- 提供正式 HTTP API
- 作为前端唯一后端入口

### `nq-auth / nq-security / nq-gateway`
- 提供登录、鉴权、主体读取能力

### `nq-research / nq-backtest / nq-eval`
- 提供研究 / 回测 / 评估数据来源

### `nq-core / nq-ledger / nq-risk / nq-scheduler / nq-adapter-*`
- 提供交易验证与策略运行所需能力
- 只做最小联调补口，不做重构

## 3. 越界约束

- GateG 不把前端开发变成后端大重写
- GateG 不要求数据库先大改
- GateG 不重写 GateF 研究 / 回测 / 评估主链
- GateG 不重写 GateE 策略接入与调度主链
- GateG 不新增第二套认证协议
