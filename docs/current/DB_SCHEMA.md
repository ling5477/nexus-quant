# Current DB Schema

数据库结构以 Flyway migrations 为准。本文只记录当前数据库事实入口，不复制完整 DDL。

## 本地数据库规则

- 本地 PostgreSQL 默认端口：`5432`。
- 本地 JDBC 默认地址：`jdbc:postgresql://localhost:5432/nexus_quant`。
- `application-local.yml` 支持 `NQ_DB_URL` 覆盖。
- `application-local.yml` 支持 `NQ_DB_PORT` 覆盖，默认 `5432`。

## 当前已有表域

当前数据库已包含用户、账户、凭证、订单、成交、持仓、策略、调度、研究、回测、评估、发布、行情基础表。具体字段、索引、约束以 `backend/**/db/migration` 下的 Flyway migration 为准。

## GateH 后续重点

- 强化 `instrument_catalog`。
- 强化 `marketdata_bars`。
- 强化 marketdata ingestion 相关结构。
- 明确真实历史行情数据质量、去重、完整性状态。

## 本次任务边界

- 当前任务不新增 migration。
- 当前任务不改业务表结构。
- 当前任务不开发历史行情抓取实现。
