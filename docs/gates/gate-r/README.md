# GateR Historical Archive

本目录保存 GateR（Shadow Run operation baseline）冻结、release tag 与证据归档。本文档是历史卷宗，不是当前实现入口；当前事实仍以 `docs/current/` 为准。

## Archive state

- Archive task: `NQ-GATER-FREEZE-CLOSEOUT`.
- Archive status: **PASS / COMPLETED / RELEASE TAG PUSHED**。
- GateR final state: **FROZEN / ACCEPTED / TAGGED**。
- Release tag: `nq-gater-freeze`.
- Tag message: `NexusQuant GateR freeze: shadow run local fact, runner, consistency, and read-only console baseline`.
- CI preflight evidence: GitHub Actions `NQ CI Baseline` run `28852212136`，`status=completed`、`conclusion=success`、`headSha=f2507cb2a061bfced5ea42554f75aba5ef879702`。
- Evidence scope: GateR-0 到 GateR-8、数据库本地事实源、只读 API、前端 list/detail/replay、边界声明。
- Current authority docs remain in `docs/current/*`.

## Archived documents

- [GATER_EVIDENCE_MATRIX.md](GATER_EVIDENCE_MATRIX.md)
- [GATER_TESTING_AND_CI_SUMMARY.md](GATER_TESTING_AND_CI_SUMMARY.md)
- [GATER_DATABASE_EVIDENCE.md](GATER_DATABASE_EVIDENCE.md)
- [GATER_BACKEND_EVIDENCE.md](GATER_BACKEND_EVIDENCE.md)
- [GATER_FRONTEND_EVIDENCE.md](GATER_FRONTEND_EVIDENCE.md)
- [GATER_BOUNDARY_STATEMENT.md](GATER_BOUNDARY_STATEMENT.md)

## GateR completed scope

- GateR-0：规划文档已建立。
- GateR-1：Shadow Run migration plan review 已通过。
- GateR-2：Shadow Run local fact model（V32）与 repository 已完成。
- GateR-3：Shadow Run runner skeleton 已完成（diagnostic local fact skeleton），非 scheduler / background runner。
- GateR-4：decision trace / risk snapshot / order intent preview 已完成。
- GateR-5：shadow consistency report service 已完成。
- GateR-6：Shadow Run read-only API 已完成。
- GateR-7：Shadow Run detail / replay frontend view 已完成。
- GateR-8：Shadow Run list / entrypoint 已完成。

## Boundary at freeze closeout

- LIVE：**DISABLED**（关闭）。
- AI：**NOT STARTED**（未开始）。
- DH runtime：**NOT INTEGRATED**（未集成）。
- RealClient：**NOT IMPLEMENTED**（未实现）。
- real provider：**NOT IMPLEMENTED**（未实现）。
- private trading adapter：**NOT IMPLEMENTED**（未实现）。
- real permission probe：**NOT IMPLEMENTED**（未实现）。
- order / cancel / transfer / withdraw：**none**（未实现）。
