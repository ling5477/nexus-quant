# Current Architecture

本文只记录当前架构事实，不决定 current Gate。阶段状态必须读取 [STATUS.md](STATUS.md) 的机器可读 authority 区块；历史 Gate 文档只作为 archived context 读取。

## 固定能力边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter：`NOT IMPLEMENTED`（未实现）。受控真实 private read-only permission diagnostic 基础设施已存在：只允许在显式 diagnostic profile 与全部 fail-closed safety 配置精确满足时选择；默认 runtime 使用 `NoReal`，尚未完成本轮 GateY-6C 真实 smoke，不构成交易授权。

## 总体架构

NexusQuant 当前由 Java 后端多模块、React 前端控制台、Python 离线研究工具链和本地 PostgreSQL 组成。架构文档不维护 Gate 路线；下一允许动作只由 `STATUS.md` 与 `ROADMAP.md` 共同解释。

## 后端多模块结构

- `nq-app`：Spring Boot 启动与 composition root。
- `nq-api`：HTTP controller、DTO、web adapter、API contract。
- `nq-core`：domain model、policy、port、application service。
- `nq-infra`：JDBC、Flyway、repository adapter、query adapter、基础设施实现。
- `nq-adapter-api`：交易所 adapter contract。
- `nq-adapter-okx` / `nq-adapter-binance`：既有交易所适配实现；不表示 real provider 已启用。
- `nq-research` / `nq-backtest` / `nq-eval`：研究、回测、评估链路。
- `nq-ledger` / `nq-risk` / `nq-scheduler` / `nq-observability`：账本、风控、调度、观测。

## 前端结构

前端使用 React + TypeScript + Vite + React Router + TanStack Query + Axios + Zustand + Ant Design。正式 API 调用必须通过 `frontend/src/api/*` 封装。NQ Console 继续使用专业金融后台风格，不新增 AI / Agent / DH runtime 成熟页面 mock。

## Python Research

`research/py` 是独立离线研究工具链，不进入 live trading、auth、recovery、ledger 主链，也不作为 ML ready 或 live execution ready 的隐式入口。

## 禁止依赖规则

- `nq-api` 不写 SQL。
- `nq-core` 不依赖 JDBC。
- `nq-infra` 承载 JDBC 和基础设施实现。
- adapter 模块只做交易所适配，不把交易所模型泄漏为平台 application 主语义。
- 前端 API 调用统一走 `frontend/src/api/*`。

## 固定禁止边界

- 不新增 AI 模块、AI 信号、AI runtime 或 AI Paper Trading。
- 不实现 DH runtime integration。
- 不接 NQ RealClient，不接真实 Provider。
- 不启用 LIVE，不新增真实下单、撤单、转账、提现路径。
- 不实现 generic/mutating permission probe。既有 OKX private read-only diagnostic 只能显式人工触发 typed `GET` 请求，不允许 startup/scheduler 自动 probe，不允许 mutation，也不得解释为 RealClient、real provider、private trading 或 LIVE authorization；Binance real permission probe 仍未实现。
