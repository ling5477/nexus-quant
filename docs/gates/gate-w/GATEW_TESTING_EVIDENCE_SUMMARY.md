# GateW Testing and CI Evidence Summary

## Accepted Exact-head CI

- GateW-1：run `29219687588`，`completed / success`。
- GateW-2：run `29230512781`，`completed / success`。
- GateW-3 final acceptance：run `29332316101`，`completed / success / 10 jobs / bad=0`。
- GateW-4：run `29339016784`，`completed / success / 10 jobs / bad=0`。
- Attempt-13 acceptance/parser fix：run `31295184056`，`completed / success / 10 jobs / bad=0`。
- authority sync：run `31295604792`，`completed / success / 10 jobs / bad=0`。
- manifest remediation：run `31298470955`，`completed / success / 10 jobs / bad=0`，
  `headSha=ecd3b4397d51fd48260de2f7954df191541b101f`。

## Runtime Acceptance

Attempt-13 记录 656 条 sequence `1..656` 的唯一连续样本，elapsed=`604820.4973147s`；hash-chain=
`PASS / HASH_CHAIN_VERIFIED`，NRestarts=`0`，forbidden/fallback/raw/secret=`0/0/0/0`。seal 后 worker
inactive/dead、MainPID=0、residual=0。

## Freeze Validation Contract

pre-tag 已运行 authority、next-action、governance lifecycle、task-evidence policy、GateW strict archive、manifest regression
与 docs links，结果均 PASS；archive warnings/errors=`0/0`，links warnings/errors=`1/0`。唯一 link warning 位于 append-only
`docs/current/TESTING.md` 的历史 GateJ 链接，不阻断 GateW archive。JSON parse、PowerShell AST 与 Git whitespace checks 在
commit 前执行并记录。

`check-gate-release.ps1` 没有 pre-tag 模式，且其 contract 要求 local/remote annotated tag 与 exact-head CI，因此 pre-tag
release result 为 `NOT_APPLICABLE / TAG_PENDING`（不适用 / 等待 tag），不得用预期失败调用冒充验证。freeze commit 推送后必须等待
`NQ CI Baseline` exact-head 10/10 success；在取得该事实前本 archive 保持 `TAG PENDING`。

本次 docs/archive closeout 不重跑 Maven、frontend E2E 或 Python suite；这些代码路径未修改，完整跨栈验证由上述 exact-head CI
提供。生产 soak 不重跑。
