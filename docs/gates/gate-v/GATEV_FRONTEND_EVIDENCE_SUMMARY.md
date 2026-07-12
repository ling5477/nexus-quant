# GateV Frontend Evidence Summary

GateV-4 在既有 `/strategies/validation` 页面接入 `ValidationReviewWorkbench`，包含 review queue、case drawer、event timeline 与四个有限 lifecycle actions；未新增 route、backend endpoint、migration 或 scheduler 行为。

API client、types 与 TanStack Query hooks 分离。ADMIN 可见 owner filter；OPERATOR 不发送 ownerId。选择态使用 `reviewCaseId` URL 参数恢复，未知 state 和不可见 case fail-closed。Mutation 发送真实 `Idempotency-Key` 与 `expectedVersion/reason`，pending 时防重复，不自动 retry。

成功后刷新 queue/detail/events；409/422 保守 refetch，403 禁用动作。loading、empty、API error、permission denied、404、conflict 与 UUID 生成失败均有显式状态，危险动作要求确认。

## Playwright 验证点

- ADMIN queue、filter、pagination、URL detail、events 与权限边界。
- mutation 的 header/body、pending 防重复和三类 query refresh。
- 403/404/409、loading/empty/error 与 OPERATOR owner boundary。

2026-07-12 production build PASS；`validation-review-workbench-smoke.spec.ts` 4 passed，覆盖精确 endpoint/header/body/network assertions；既有 `strategy-validation-paper-shadow-smoke.spec.ts` 2 passed，确认 `UNKNOWN / NOT_AVAILABLE` 不伪装为成功。

Workbench 只表达本地诊断与人工复核，持续显示 LIVE disabled / not trading authorization 边界；不包含真实交易、Shadow enable、AI、DH 或 Python execution 控件。
