# GateV 冻结证据归档入口

本目录是 GateV pre-tag strict archive，记录 `Durable Review Fact Model`、`Operator Review Lifecycle API`、`PostgreSQL Advisory Scheduler Lock`、`Controlled Read-only Scheduler` 与 `Validation Review Workbench` 的已接受证据。

当前归档状态为 `IMPLEMENTED / PENDING_REVIEW`（已实施 / 待复核），不是 `FROZEN`、`ACCEPTED` 或 `TAGGED`。预期 tag `nq-gatev-freeze` 尚不存在；只有 archive commit exact-HEAD CI 成功且 freeze closeout review 接受后，才可另轮创建 tag。

## 归档导航

- [冻结 closeout implementation](GATEV_FREEZE_CLOSEOUT.md)
- [冻结 readiness review 前置](GATEV_FREEZE_READINESS_REVIEW.md)
- [实现 baseline](GATEV_IMPLEMENTATION_BASELINE.md)
- [GateV-1 至 GateV-4 evidence matrix](GATEV_BATCH_1_4_EVIDENCE_MATRIX.md)
- [测试与 CI 证据](GATEV_TESTING_EVIDENCE_SUMMARY.md)
- [后端、DB 与 migration 证据](GATEV_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md)
- [API 证据](GATEV_API_EVIDENCE_SUMMARY.md)
- [前端证据](GATEV_FRONTEND_EVIDENCE_SUMMARY.md)
- [runtime scheduling 边界](GATEV_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md)
- [边界声明](GATEV_BOUNDARY_STATEMENT.md)
- [已知限制与 residual](GATEV_KNOWN_LIMITATIONS_AND_RESIDUALS.md)

归档角色由 `scripts/docs/gate-archive-manifest.json` 决定。本卷覆盖全部 mandatory roles，以及 GateV strict override 要求的 `backend-db-evidence`、`api-evidence`、`frontend-evidence`、`runtime-scheduling-evidence`。Python preview 为 `No-file residual / NOT IMPLEMENTED`，因此不伪造 Python role 文件。
