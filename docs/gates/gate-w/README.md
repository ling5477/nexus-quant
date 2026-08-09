# GateW 冻结证据归档入口

本目录是 GateW `168h OKX read-only soak` 的 strict pre-tag archive。它固化 GateW 已接受实现、Attempt-09 至 Attempt-13
历史、release/runtime、安全边界与全部 task evidence；最终 release tag 必须在本 archive 所在 freeze commit 的 exact-head CI
成功后创建。

## Release Handoff

- acceptance commit：`20cf7970dfb414868da3e42dddaefc5965246570`；CI run `31295184056` 为 `completed / success / 10 of 10`。
- authority-sync commit：`9a90379196ce4fe0cefe3e737b354a5b94f27fa5`；CI run `31295604792` 为
  `completed / success / 10 of 10`。
- archive-manifest remediation commit：`ecd3b4397d51fd48260de2f7954df191541b101f`；CI run `31298470955` 为
  `completed / success / 10 of 10`。
- pre-tag validation：strict archive、authority、next-action、lifecycle/task-evidence policy、manifest regression 与 docs
  links 均通过；links 仅保留 1 个 historical ledger warning，errors=0。
- release tag：`nq-gatew-freeze`；pre-tag 阶段为 `TAG PENDING`（等待打 tag），不得提前写成 remote tag 已存在。
- tag target：必须是本 archive 的 freeze commit，不得指向 runtime release、acceptance commit 或 authority-sync commit。

## 归档导航

- [冻结 closeout](GATEW_FREEZE_CLOSEOUT.md)
- [冻结 readiness review](GATEW_FREEZE_READINESS_REVIEW.md)
- [重建实现 baseline](GATEW_IMPLEMENTATION_BASELINE.md)
- [GateW evidence matrix](GATEW_BATCH_1_4_EVIDENCE_MATRIX.md)
- [测试与 CI 证据](GATEW_TESTING_EVIDENCE_SUMMARY.md)
- [后端、DB 与 migration 证据](GATEW_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md)
- [API 证据](GATEW_API_EVIDENCE_SUMMARY.md)
- [前端证据](GATEW_FRONTEND_EVIDENCE_SUMMARY.md)
- [runtime 与 scheduling 边界](GATEW_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md)
- [边界声明](GATEW_BOUNDARY_STATEMENT.md)
- [已知限制与 residual](GATEW_KNOWN_LIMITATIONS_AND_RESIDUALS.md)
- [全部 task evidence 索引](source/task-evidence/README.md)

本卷覆盖 manifest 的 8 个 mandatory roles，以及 GateW strict override 要求的 `backend-db-evidence`、`api-evidence`、
`frontend-evidence`、`runtime-scheduling-evidence`。`source/task-evidence/**` 是 non-role 历史证据，不参与 role 计数；nested
README 不占用顶层唯一 `archive-entry` role。起始 source 与 archived snapshot 均为 96 个 attempt + 1 个 README，
missing/unexpected 均为 0；1 个 archive copy 仅规范化了 EOF 多余空白行，以满足 `git diff --cached --check`。
