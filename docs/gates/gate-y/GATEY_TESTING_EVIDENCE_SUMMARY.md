# GateY Testing and CI Evidence Summary

## Exact-head CI

- Production pilot release `8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`：run `32978280738`，`completed / success / 10 jobs`。
- Final authority/document baseline `65caaf7fd3038658b0f4f24566efd2960e606d43`：run `32981327378`，`completed / success / 10 jobs / bad=0`。
- 10 jobs 包括 Diff check、Backend Maven test、PostgreSQL/Flyway smoke、Frontend build、backend/no-backend E2E、Research quality gate、No-outbound guard、Secret scan 与 CI security smoke。

## Accepted validation baseline

- Full Maven：23/23 modules PASS。
- `nq-app`：315 tests / 0 failures / 0 errors；与运行时条件相关的 skip 已按原 evidence 保留，不伪写为执行通过。
- GateY minimal frozen regression：100/100 PASS。
- GateW frozen regressions：PASS。
- Authority checker：errors=0。
- Java governance：PASS。
- Shadow new-code violations：0。
- Gitleaks：PASS。
- P0=0、P1=0。

## Historical failures

GateY 的失败 CI、Gitleaks false positives、EOF whitespace failure、security review rejection、server/bootstrap/deployment blocker 与 query-only remediation 均保存在 `source/task-evidence/**`。最终 green 不覆盖历史失败，也不把未运行的生产 pilot 重演写成 PASS。

本 freeze 为 docs/archive/governance 变更，不再次执行生产 pilot。Freeze candidate 仍需运行 archive、authority、link、GateY/GateW frozen regression、governance regression、diff 与 secret scan；freeze commit 的 GitHub exact-head CI 是 tag 准入的最终证据。
