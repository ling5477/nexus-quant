# GateS Frontend Evidence Summary

GateS frontend 变更均为既有页面内的只读诊断面板，不新增交易动作、新 route 家族、Dashboard v2、Incident Center、AI 决策中心或后端写侧 client。

## 面板

| Area | Existing page | Evidence | Boundary |
| --- | --- | --- | --- |
| Shadow Run overview summary | `/strategies/shadow-runs` | consumes `GET /api/shadow-runs/overview` | 只读 overview；no route / no write action |
| Paper vs Shadow consistency drilldown | `/strategies/shadow-runs/:shadowRunId` | consumes `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}` | 只读 drilldown；not trading authorization |
| Strategy Validation overview | `/strategies/validation` | consumes `GET /api/strategy-validation/overview` | validation-only；`APPROVED` 不表示真实交易授权 |
| Strategy Validation / Shadow Workbench | `/strategies/validation` | aggregates validation overview、Shadow overview、Paper vs Shadow drilldown | 只读 Workbench；no backend API / no Python artifact UI |
| Incident / Replay overview | `/strategies/validation` | consumes `GET /api/incidents/replay/overview` | 只读 incident / replay diagnostics；no automatic remediation |

## 验证证据

- GateS-1 frontend：`npm run build` PASS；CI run `28876338356` success。
- GateS-2 frontend：`npm run build` PASS；CI run `28911668175` success。
- GateS-3 frontend：`npm run build` PASS；CI run `28916161151` success。
- GateS-5 frontend：`npm run build` PASS；target Playwright smoke PASS / 2 passed；CI run `28924615933` success。
- GateS-6 frontend：`npm run build` PASS；CI run `28931100943` success。

## 边界

- 不新增交易按钮、执行按钮、真实交易 client 或写侧 API 调用。
- 不把 validation、consistency、incident severity 或 replay diagnostics 写成真实交易授权。
- 不存储 credential material，不读取 token / secret / passphrase，不输出敏感值。
