# GateAUDIT frontend evidence

Frontend accepted baseline 由 localization/Error Catalog technical pair 与 NQ Console Visual System V3 pair 共同组成。Current visual rules 见 [Frontend Design System](../../current/FRONTEND_DESIGN_SYSTEM.md)，accepted identities 见 [Error Catalog verification](../../error-catalog/VERIFICATION.md) 和 [UI V3 acceptance](../../audit/evidence/GATEAUDIT_FRONTEND_CONSOLE_VISUAL_SYSTEM_V3_ACCEPTANCE.md)。

## Localization and error UX

- `zh-CN` 是 canonical/default locale，`en-US` 是 secondary locale；Ant Design locale 与产品 locale 同步。
- Error Catalog precedence 为 `errorId → errorKey → code → HTTP category → UNKNOWN_ERROR`。
- Machine identity 与 display 分离；未知错误保留 code/traceId，不把 raw backend message 作为主 UX。
- `NQ-TRD-1001 / ORDER_VERSION_CONFLICT` 与 legacy `STATE_CONFLICT` 保持兼容；mutation conflict 不自动 retry。
- Technical pair=`1b4c87129f2a79e13e379aa56501042ddd5bd42f / 35684433673 / 9 of 9 SUCCESS`。

## Visual System V3

- AppShell、Ant Design theme/tokens、page scaffold、登录、Dashboard 与 primary pages 已在固定 pair 接受。
- Dashboard 对 missing、failed、refreshing 或 unknown data 保持诚实：未知计数不伪装为零，不从不完整查询推断实时或全局健康。
- Technical pair=`07453f8b16e798bd580070a3727aa9eb7e88a193 / 35720426791 / 9 of 9 SUCCESS`，P0/P1=`0/0`。

AntD deprecation warning 与 Vite/JS bundle-size warning 保持 `OBSERVATION`，未静默关闭。Phase7-D 不修改 React/TypeScript/CSS、不运行生产 UI，也不把 UI 状态当作交易授权；前端不能绕过 backend RBAC、risk、environment、state machine 或 idempotency contract。
