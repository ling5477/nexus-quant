# GateX 冻结证据归档入口

本目录是 GateX `Strategy Release → guarded Shadow materialization` 的 strict pre-tag archive。它固化 GateX-0A～5 的实现、审查、失败与 remediation 证据；本归档是 historical evidence，不覆盖 `docs/current/STATUS.md` 的 current authority。

## Release handoff

- archive starting HEAD：`f255e6b0914c3c6aa39708a269a20a3a17964450`。
- starting exact-head CI：`NQ CI Baseline` run `31560815042`，`completed / success / 10 jobs / bad=0`。
- GateX-5 acceptance head：`a383be750f51d063d429bc25fad80e60dffb7014`；CI run `31512467501` 为 `completed / success / 10 jobs / bad=0`。
- technical hard gates：`18/18 PASS`；P0=0、产品 P1=0，`ADMISSION_MATERIALIZATION_FACT_TEAR=CLOSED`。
- canonical annotated tag：`nq-gatex-freeze`；pre-tag 阶段为 `TAG PENDING`，只能在 archive freeze commit exact-head CI 成功后创建。
- freeze commit、tag object、peeled commit 与 remote tag 结果由后续真实 Git/CI 事实确定；本归档不预言自身 commit SHA 或未来 tag object。

## 归档导航

- [冻结 closeout](GATEX_FREEZE_CLOSEOUT.md)
- [冻结 readiness review](GATEX_FREEZE_READINESS_REVIEW.md)
- [GateX 实施计划与 scope](GATEX_PLAN.md)
- [GateX batch evidence matrix](GATEX_BATCH_0_5_EVIDENCE_MATRIX.md)
- [测试与 CI 证据](GATEX_TESTING_EVIDENCE_SUMMARY.md)
- [后端、DB 与 migration 证据](GATEX_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md)
- [API 证据](GATEX_API_EVIDENCE_SUMMARY.md)
- [前端证据](GATEX_FRONTEND_EVIDENCE_SUMMARY.md)
- [runtime 与 scheduling 边界](GATEX_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md)
- [边界声明](GATEX_BOUNDARY_STATEMENT.md)
- [已知限制与 residual](GATEX_KNOWN_LIMITATIONS_AND_RESIDUALS.md)
- [全部 task evidence 索引](source/task-evidence/README.md)

本卷覆盖 manifest 的 8 个 mandatory roles，以及 GateX strict override 要求的 `backend-db-evidence`、`api-evidence`、`frontend-evidence`、`runtime-scheduling-evidence`。`source/task-evidence/**` 仅保存不可覆盖历史 evidence，不参与 role 计数；顶层 `README.md` 是唯一 `archive-entry`。

Pre-tag verification 已通过：12 个 required roles 独立，archive warnings/errors=`0/0`，current authority 在 PowerShell 5.1/7 下均为 errors=0，document links errors=0，governance lifecycle/next-action/archive-manifest regressions 均 exit 0。该事实只表示 archive candidate 可进入 freeze commit，不表示 tag 已存在。
