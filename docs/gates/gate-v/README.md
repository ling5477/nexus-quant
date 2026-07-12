# GateV 冻结证据归档入口

本目录是 GateV 的 strict pre-tag archive，记录 `Durable Review Fact Model`、`Operator Review Lifecycle API`、`PostgreSQL Advisory Scheduler Lock`、`Controlled Read-only Scheduler` 与 `Validation Review Workbench` 的已接受证据。

## Release Handoff

- freeze candidate commit：`7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`。
- candidate CI：`NQ CI Baseline` run `29191014596`，`completed / success`，`headSha` 与 candidate 精确一致。
- release tag：`nq-gatev-freeze`；仅在 release closeout commit 的 exact-HEAD CI 通过后创建 annotated tag。
- release closeout commit：本 archive closeout 文件所在提交；提交前不伪造尚未知的自身 SHA。
- release tag target：创建后通过 `nq-gatev-freeze^{}` 解析，必须等于 release closeout commit。

GateV 的正式 `FROZEN / ACCEPTED / TAGGED` 状态、tag object、peeled target、remote tag 与 exact tagged-commit CI 只在 post-tag current authority 中登记；pre-tag archive 不把 `TAG PENDING` 写成已打 tag。

## 归档导航

- [冻结 closeout](GATEV_FREEZE_CLOSEOUT.md)
- [冻结 readiness review](GATEV_FREEZE_READINESS_REVIEW.md)
- [实现 baseline](GATEV_IMPLEMENTATION_BASELINE.md)
- [GateV-1 至 GateV-4 evidence matrix](GATEV_BATCH_1_4_EVIDENCE_MATRIX.md)
- [测试与 CI 证据](GATEV_TESTING_EVIDENCE_SUMMARY.md)
- [后端、DB 与 migration 证据](GATEV_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md)
- [API 证据](GATEV_API_EVIDENCE_SUMMARY.md)
- [前端证据](GATEV_FRONTEND_EVIDENCE_SUMMARY.md)
- [runtime scheduling 边界](GATEV_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md)
- [边界声明](GATEV_BOUNDARY_STATEMENT.md)
- [已知限制与 residual](GATEV_KNOWN_LIMITATIONS_AND_RESIDUALS.md)

归档角色由 `scripts/docs/gate-archive-manifest.json` 决定。本卷覆盖全部 mandatory roles，以及 GateV strict override 要求的 `backend-db-evidence`、`api-evidence`、`frontend-evidence`、`runtime-scheduling-evidence`。GateV 是证据新流程启用前的过渡 Gate；严格 manifest 不允许额外非 role 文件，因此本轮不创建 `source/task-evidence/**` 或 `docs/current/evidence/gate-v/**`。Python preview 为 `No-file residual / NOT IMPLEMENTED`，因此不伪造 Python role 文件。
