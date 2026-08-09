# GateW Known Limitations and Residuals

## Accepted Residuals

1. P2：Attempt-13 maximum gap=`1797s`，发生于 sequence `1→2`。该 limitation 原样保留，不改写 acceptance；后续若调整采样
   SLA，必须另行立项。
2. GateW-2 的 `REAL_SMOKE=NOT_RUN` 历史口径保留；后续 Attempt-13 的受控 read-only soak 不等于全部 remote
   permission、账户健康或交易 readiness。
3. GateW preview/reconciliation/risk preflight 中 minimum notional、fee、balance、stateful risk 与 remote trading
   permission 仍为 `UNKNOWN / NOT_EVALUATED`；`executionReadiness=BLOCKED`。
4. Attempt-09 被拒绝，Attempt-10/11/12 失败并终态化；这些记录与 release/RunId 不得删除、覆盖、复用或自动重试。
5. `CLAUDE.md` 仍含历史 GateJ/GateK 口径，但不属于 current authority，且不在本任务 allowlist 内；以
   `docs/current/STATUS.md` 为唯一阶段事实源。

## Not Authorized

GateW freeze 不关闭或放宽 LIVE、交易写侧、转账/提现、credential、AI、DH runtime、Integration runtime、real provider 或 private
trading 禁止边界。GateX 只能处于 `PLAN / NOT_STARTED`，不得由本 archive 推导为 implementation started。

## Rollback

freeze commit 问题使用 forward revert。annotated tag 一旦推送不得静默删除、覆盖或 force update；应进入 post-freeze
remediation 或 superseding tag 流程，并保留本卷全部历史 evidence。
