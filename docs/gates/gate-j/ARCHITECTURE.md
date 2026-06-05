# Current Architecture

本文只记录当前架构事实，不叙述历史 Gate 过程。

## 总体架构

NexusQuant 当前由 Java 后端多模块、React 前端控制台、Python 离线研究工具链和本地 PostgreSQL 组成。当前主线是虚拟币量化交易平台底座，尚未完成虚拟币 V1 闭环，尚未进入 AI 自动交易。

## 后端多模块结构

- `nq-app` 是启动与 composition root。
- `nq-api` 是 HTTP API 层。
- `nq-core` 是核心 domain/application/port 层。
- `nq-infra` 承载 JDBC、Flyway 与基础设施实现。
- `nq-adapter-api` 定义交易所适配契约。
- `nq-adapter-okx`、`nq-adapter-binance` 实现具体交易所适配。
- `nq-research`、`nq-backtest`、`nq-eval` 形成研究、回测、评估链路。
- `nq-ledger`、`nq-risk`、`nq-scheduler`、`nq-observability` 分别承担账本、风控、调度、观测。

## 前端结构

前端使用 React 19 + TypeScript + Vite 8 + React Router + TanStack Query + Axios + Zustand + Ant Design。正式页面入口包括账户上下文、交易工作台、策略、调度、运行、研究、回测、评估、发布、Instrument、Marketdata。API 调用必须通过 `frontend/src/api/*` 封装。

## Python research 工具链

`research/py` 是离线研究工具链，提供 CLI、pytest、mypy、ruff 质量门禁。它不进入 live trading、auth、recovery、ledger 主链。

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
- 历史行情接入属于 GateH 后续规划，不在本次任务实现。

## 本地环境规则

- PostgreSQL 默认端口固定为 `5432`。
- `docker-compose.yml` 默认映射 `${NQ_DB_PORT:-5432}:5432`。
- `application-local.yml` 默认连接 `jdbc:postgresql://localhost:${NQ_DB_PORT:5432}/${NQ_DB_NAME:nexus_quant}`。
