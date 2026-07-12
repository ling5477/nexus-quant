# GateV Backend / DB / Migration Evidence Summary

GateV-1 使用 forward-only `V33__gate_v_validation_review_fact_model.sql` 创建独立 `validation_review_cases` 与 append-only `validation_review_events`，包含中文 comments、state/event checks、owner/source indexes、非负 version、idempotency unique constraint 与 legal-transition DB constraint；未修改历史 migration。

Domain 位于 `nq-core`，由 `ValidationReviewStateMachine`、case/event model、sensitive-data guard 与 repository port 组成。JDBC 实现位于 `nq-infra`，查询和 mutation 绑定 `tenant_key`、owner/case scope、expected version，case update 与 accepted event append 保持事务一致性。

## 真实数据库验证

- PostgreSQL version：16.14，与 CI 使用的 major 一致。
- Flyway current version：33；共应用 33 个 migration。
- CI-style legacy PAPER/ACTIVE account fixture：1；exchange account/credential fixture：0。

Fresh PostgreSQL 16.14 disposable database 从 V1 迁移至 V33，public/random-schema 两种 CI 路径均通过，共 33 migrations。GateV `ValidationReviewRepositoryPostgresIntegrationTest` 为 1 passed；migration contract、state machine、并发幂等、optimistic conflict、owner isolation 与 sensitive guard 由全量 backend suite 覆盖。

长期本地 `localhost:5432/nexus_quant` 存在历史 V33 checksum drift（Applied `-1276170491`，Resolved `1421368418`），因此初次全量命令 fail-closed。未执行 Flyway repair；改用 disposable DB 复核当前 migration 和代码，验证结束后容器已删除。

该 schema 只持久化本地诊断和人工复核事实，不保存 credential、真实账户余额、订单 payload 或交易授权；无 backfill、无 down migration、无交易表变更。
