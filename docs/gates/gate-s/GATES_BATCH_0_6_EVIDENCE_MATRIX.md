# GateS Batch 0-6 Evidence Matrix

状态：GateS-0..6 `COMPLETED`（已完成）

| Batch | Status | Commit evidence | Scope | Validation / CI evidence | Boundary |
| --- | --- | --- | --- | --- | --- |
| GateS-0 | `COMPLETED` | `325b5d48` / `801d705b` planning reconciliation commits | Plan / fact-source reconciliation | docs-only validation；CI success in GateS readiness chain | 不实现 API、migration、frontend、Python、CI 或 runtime |
| GateS-1 backend | `COMPLETED` | `4c029110` | `GET /api/shadow-runs/overview` backend read model | targeted Maven PASS；CI run `28872187369` success | GET-only、SELECT-only、not trading authorization |
| GateS-1 frontend | `COMPLETED` | `92080588` | Shadow Run list overview summary | `npm run build` PASS；CI run `28876338356` success | 只读 frontend panel；no route / no write action |
| GateS-2 backend | `COMPLETED` | `38216a9a` | `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}` | targeted Maven PASS；CI run `28878660031` success | GET-only、SELECT-only、不创建 report / snapshot / event |
| GateS-2 frontend | `COMPLETED` | `0b471503` | Shadow Run detail / replay consistency drilldown panel | `npm run build` PASS；CI run `28911668175` success | 只读诊断展示；no route / no write action |
| GateS-3 backend | `COMPLETED` | `d8c93662` / `2a0fde49` backend chain | `GET /api/strategy-validation/overview` | targeted Maven PASS；CI runs `28912967997` / `28916161151` success | `APPROVED` 仅为 validation 层语义；不是交易授权 |
| GateS-3 frontend | `COMPLETED` | `2a0fde49` | Strategy Validation overview panel | `npm run build` PASS；CI run `28916161151` success | 只读 validation facts；no write action |
| GateS-4 Python | `COMPLETED` | `b245e184` | offline evaluation artifact baseline | pytest PASS / mypy PASS / ruff PASS；CI run `28921479009` success | offline research diagnostic only；Python ML ready = `NO` |
| GateS-5 frontend | `COMPLETED` | `3bdd4d99` | Strategy Validation / Shadow Workbench | `npm run build` PASS；target Playwright smoke PASS；CI run `28924615933` success | 只读 Workbench；no backend API / no Python artifact UI / no write action |
| GateS-6 backend | `COMPLETED` | `0c8ab1a0` | `GET /api/incidents/replay/overview` | targeted Maven PASS after wording guard fix；CI run `28928226338` success | GET-only、SELECT-only、不创建 incident / alert / recovery / replay |
| GateS-6 frontend | `COMPLETED` | `128fa08e` | Incident / Replay overview panel | `npm run build` PASS；CI run `28931100943` success | 只读诊断展示；no route / no E2E / no automatic remediation |
| GateS readiness | `COMPLETED` | `5f0fcb9` | freeze readiness review | CI run `28932927935` success，`headSha=5f0fcb9d4dacab95202dc7a9fb78911e60c06afe` | `READY FOR FREEZE CLOSEOUT` 仅为 closeout 前置，不等于 release tag 本身 |

## Closeout 结果

GateS closeout 将以上证据冻结为 `FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag 为 `nq-gates-freeze`。
