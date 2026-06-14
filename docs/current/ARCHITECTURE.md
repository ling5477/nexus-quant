# Current Architecture

本文只记录当前架构事实。历史 Gate / GateH / V1 表述只作为 previous phase / archived context 保留，不作为 current state。

当前事实：

- GateJ completed。
- Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted，作为 GateK planning baseline。
- `GATEK_ARCHITECTURE_BASELINE_REVIEW.md` 已完成，结论为 ACCEPTED WITH P2 FOLLOW-UP；P0=0，P1=0。
- GateK implementation not started。
- AI not started。
- DH runtime not integrated / not connected to NQ。
- LIVE disabled。
- real exchange permission probe adapter not implemented。

## 总体架构

NexusQuant 当前由 Java 后端多模块、React 前端控制台、Python 离线研究工具链和本地 PostgreSQL 组成。当前主线是 GateJ completed 后的 GateK planning / architecture / productization / deployment / observability / security boundary 收口；虚拟币量化 V1 闭环已在 GateI 完成，GateJ Paper Trading 稳定运行验收已完成。

当前架构可以作为 GateK 后续 workstream 的 planning baseline，但不代表 GateK implementation 已启动。AI、DH runtime、LIVE、真实交易所 permission probe adapter 和真实交易所私有调用仍未实现 / 未启用。

## 后端多模块结构

- `nq-app` 是启动与 composition root。
- `nq-api` 是 HTTP API 层。
- `nq-core` 是核心 domain/application/port 层。
- `nq-infra` 承载 JDBC、Flyway 与基础设施实现。
- `nq-adapter-api` 定义交易所适配契约。
- `nq-adapter-okx`、`nq-adapter-binance` 实现具体交易所适配。
- `nq-research`、`nq-backtest`、`nq-eval` 形成研究、回测、评估链路。
- `nq-ledger`、`nq-risk`、`nq-scheduler`、`nq-observability` 分别承担账本、风控、调度、观测。

后端分层当前判断为可接受：`nq-api` / `nq-core` / `nq-infra` / `nq-adapter-*` 的依赖方向应继续保持；GateK 后续任务不得把业务规则回写到 `nq-app`，不得让 `nq-core` 依赖 JDBC 或具体 adapter 实现。

PAPER / LIVE 仍硬隔离。LIVE 当前 disabled，任何 LIVE 相关能力即使只读也必须另起安全审计。Credential permission probe 当前为 no-real baseline：默认实现不访问 OKX / Binance 或其他真实交易所；real exchange permission probe adapter not implemented。

## 前端结构

前端使用 React 19 + TypeScript + Vite 8 + React Router + TanStack Query + Axios + Zustand + Ant Design。正式页面入口包括账户上下文、交易工作台、策略、调度、运行、研究、回测、评估、发布、Instrument、Marketdata。API 调用必须通过 `frontend/src/api/*` 封装。

NQ Console Design System v1 是当前前端基线。GateK 前端产品化应继续基于 Ant Design 和既有 NQ components 做信息架构、状态表达、风险提示和回归验证；当前不做 AI / Agent / DH runtime 完整页面 mock，不引入新的 UI 体系来替代现有控制台栈。

## Python research 工具链

`research/py` 是独立离线研究工具链，提供 CLI、pytest、mypy、ruff 质量门禁。它不进入 live trading、auth、recovery、ledger 主链，不作为 GateK implementation 的隐式入口。

GateK CI baseline 应把 `python -m pytest -q`、`python -m mypy src`、`python -m ruff check .` 纳入规划和后续验证矩阵；本文件只记录架构事实，不新增 CI workflow。

## 核心业务域

- `auth/account`
- `trading`
- `marketdata`
- `strategy`
- `research/backtest/evaluation/publish`
- `scheduler`
- `risk`
- `ledger`
- `observability`

## 模块依赖方向

```text
api/app -> core ports/application -> infra implementations
adapter implementations -> adapter-api contracts
research -> backtest -> eval
frontend -> backend /api/**
```

## 禁止依赖规则

- `nq-api` 不写 SQL。
- `nq-core` 不依赖 JDBC。
- `nq-infra` 承载 JDBC 和基础设施实现。
- `adapter-okx` / `adapter-binance` 只处理交易所适配。
- AI 模块当前未进入交易主链。
- GateH / GateI / GateJ 均为 previous completed phase；相关历史行情、V1 闭环和 Paper Trading 稳定运行记录必须作为历史完成事实读取，不得写成当前未完成项。
- GateK-PLAN 是 planning baseline，不等于 GateK implementation started。
- DH 只能作为 contract / mock / documentation work line 描述，runtime not integrated。
- LIVE 当前 disabled；不得把 LIVE 相关环境字段、历史说明或未来规划写成 LIVE enabled。
- real exchange permission probe adapter not implemented；不得把 no-real baseline 写成真实 OKX / Binance probe adapter 已实现。

## 本地环境规则

- PostgreSQL 默认端口固定为 `5432`。
- `docker-compose.yml` 默认映射 `${NQ_DB_PORT:-5432}:5432`。
- `application-local.yml` 默认连接 `jdbc:postgresql://localhost:${NQ_DB_PORT:5432}/${NQ_DB_NAME:nexus_quant}`。
