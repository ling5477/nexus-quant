# GateX Backend, DB and Migration Evidence Summary

## Backend capability

GateX 建立 Strategy Release aggregate、artifact verification、provenance persistence、Release-to-Shadow admission、read-only preview API 与 guarded materialization。`nq-api` 保持无 SQL，`nq-core` 保持不依赖 JDBC，repository/JDBC 实现在 `nq-infra`。

Materialization 写侧只接受服务端解析的 release/admission facts。`AdmissionGuard` 绑定 publish/release/artifact/manifest/strategy/dataset/evaluation identity 与 admission revision；写入前重新验证，旧 revision 或事实变化 fail closed。

## Migrations

- GateX-2：artifact provenance persistence，idempotency provenance collision fail-closed。
- V37：`backtest_publish_records` 的 nullable opaque `artifact_storage_key` / `manifest_storage_key` pair、成对约束与绑定后不可变保护；历史记录坚持 `NO FAKE BACKFILL`。
- V38：admission revision/guard infrastructure，为 materialization command-time consistency 提供数据库保护。

历史 migration 未被修改；所有 schema 变更使用 forward-only migration，并包含表/字段业务注释。JSON/locator 不保存 credential、token、cookie 或 private exchange material。

## Transaction and idempotency

`shadow_runs + CREATED event + admission revision` 在同一事务中提交；audit/event 失败回滚全部写入。same-command 返回同一 run 与单一 event，different-command 必须重新评估后才能形成合法 rerun；无条件状态覆盖被禁止。

## Residual

`PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 保留：Flyway 单事务可能让前序 DDL 强锁持有至提交，未来部署必须按真实表规模、长事务、锁等待设置受控窗口、timeout 与 rollback。该 residual 不影响当前 non-LIVE correctness，也不授权生产部署。
