# GateV Testing and CI Evidence Summary

验证日期：2026-07-12。

## Freeze Candidate CI

- freeze candidate：`7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`。
- `NQ CI Baseline` run `29191014596`：`completed / success`，`headSha=7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`。
- 该 CI 是 GateV release closeout 的硬前置，不替代 closeout commit 的 exact-HEAD CI。

## 已归档稳定基线

- fresh PostgreSQL 16.14 上 V1..V33 Flyway migration、GateV repository/lock integration、repository smoke 与 Spring app-context PostgreSQL smoke 均通过。
- disposable fresh DB 全量 backend 验证为 23/23 modules `BUILD SUCCESS`；长期本地 DB 的 V33 checksum drift 已披露且未 repair。
- `npm run build` 通过；`validation-review-workbench-smoke.spec.ts` 为 4 passed，`strategy-validation-paper-shadow-smoke.spec.ts` 为 2 passed。
- 既有 Vite large-chunk 与 Ant Design v5 / React 19 compatibility warning 不影响当时 build/test exit code。

## Release Validation Entry

- `scripts/docs/test-gate-archive-manifest.ps1`、`check-gate-archive.ps1 -Gate gate-v -PreTag`、next-action regression、authority checker 与 doc link checker 必须在 pre-tag authority 下通过。
- release closeout commit 推送后，实际 `NQ CI Baseline` run、tag object、peeled target、remote tag 与 post-tag checkers 以 Git/GitHub 现场结果为准；本文件不预写未知 run ID 或 tag object。
- GateV tag 后，不重跑 Maven、frontend build 或 Playwright，因为本 release closeout 只改变 archive/current metadata；代码基线由 candidate CI 和已归档稳定证据覆盖。

GateV 证据仅证明 durable review、受控只读 scheduler 和 review workbench；不表示 LIVE、Shadow trading、AI、DH/Integration runtime、real provider、private trading、Python ML 或 Python live execution ready。
