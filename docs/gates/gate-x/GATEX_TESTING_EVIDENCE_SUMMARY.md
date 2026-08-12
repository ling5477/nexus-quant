# GateX Testing and CI Evidence Summary

## Exact-head CI

- GateX-5 acceptance head `a383be750f51d063d429bc25fad80e60dffb7014`：run `31512467501`，`completed / success / 10 jobs / bad=0`。
- generic freeze closeout governance head `9848ce24bf565d05d8cfdc7a248c3c0d98c68be8`：run `31559049270`，`completed / success / 10 jobs / bad=0`。
- PS5.1 compatibility head `f255e6b0914c3c6aa39708a269a20a3a17964450`：run `31560815042`，`completed / success / 10 jobs / bad=0`。

每个 `NQ CI Baseline` 包含 Diff check、Backend Maven test、PostgreSQL/Flyway smoke、Frontend build、backend/no-backend E2E、Research quality gate、No-outbound guard、Secret scan 与 CI security smoke。最新 starting HEAD 的 10 个 jobs 全部成功。

## GateX focused evidence

- Final independent review：PostgreSQL 17 mandatory matrix 3 suites/12 tests。
- Focused/full backend：exit 0；WebMvc 7/7；ArchUnit 两组 6/6。
- Frontend build：exit 0；targeted Playwright 11/11。
- Materialization：same-command replay、different-command legitimate rerun、audit failure rollback、run/event/revision atomicity 与 stale guard rejection 已覆盖。
- Artifact binding：trusted-root containment、Windows junction/root/target replacement、duplicate manifest identity、cross-release identity 与 bounded strict parser 已覆盖。

## Governance regression

Windows PowerShell 5.1 与 PowerShell 7 均通过 current authority、next-action、lifecycle/task-evidence policy 与 archive-manifest regressions。GateX/GateW/GateY generic closeout 正例一致，wrong Gate、lowercase、suffix、batch-embedded、unknown action 与非法 lifecycle 状态均 fail closed。

本 freeze 为 docs/archive 变更，未在本地重新运行业务全量套件；以已验证 exact-head CI 和归档的独立 review evidence 为 capability 事实源。Pre-tag checker、doc links、diff 与治理 regressions仍必须在 archive candidate 完成后重新执行。
