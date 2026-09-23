# GateAUDIT backend and database evidence

Java 21 / Spring Boot control plane 是 canonical trading authority。`nq-api`、`nq-core`、infra/JDBC 与 PostgreSQL 保持既有模块边界；Order、Trade、Ledger、reconciliation、risk、execution 和 recovery 不存在第二事实源。Canonical architecture 见 [ARCHITECTURE](../../current/ARCHITECTURE.md)，schema 入口见 [DB_SCHEMA](../../current/DB_SCHEMA.md)。

## Accepted backend and accounting facts

- Phase4 固定 L3 causal proof、forked-JVM restart foundation、ordinary Order execution identity 与 Trade/Ledger convergence。
- Phase5B 接受 immutable release、PostgreSQL restore 与 deployment boundary；该历史 restore 绑定 schema V46。
- Phase6 L4 接受 B0–B6 real-process correctness，eligible matrix=`28/28`、missing/invalid=`0/0`。
- Phase6 L5 接受 bounded concurrency/backlog/repeated-fault/accounting，20 行 accepted/reused，mandatory=`0`。
- Phase6 L6-A/L6-B 分别接受 60 分钟与 180 分钟稳定性范围，未把更长 optional soak 写成已执行。
- Phase7-B 使用 PostgreSQL 16 offline restore 与 source-only Decimal oracle，Position/latest Snapshot exact comparison 一致，repair=`NOT_REQUIRED`。

Canonical acceptance locator 为 [Phase6 final acceptance](../../audit/evidence/GATEAUDIT_PHASE6_L5_L6_FINAL_ACCEPTANCE.md) 与 [Phase7-B verification](../../audit/evidence/GATEAUDIT_PHASE7_B_HISTORICAL_PROJECTION_BASELINE_VERIFICATION.md)。Raw dump、production export 与 qualification ZIP 未复制到 archive。

## Schema and migration boundary

Repository migration inventory 当前到 V51；这只描述 repository schema，不能推断 production 当前 schema=V51。历史 minimal pilot/production source 是 V46 fact。所有 Flyway migration 保持 forward-only，既有 V1–V51 文件及其历史证据未在 Phase7-D 修改或重写。

本 archive 不执行 migration、restore、repair、backfill、SQL correction 或生产连接。任何 future schema/data change 都需要独立授权、隔离 PostgreSQL 证明、兼容正反例与 exact candidate review；Phase7-D 不把 clean CI 或 qualification DB 当作 production data correctness proof。
