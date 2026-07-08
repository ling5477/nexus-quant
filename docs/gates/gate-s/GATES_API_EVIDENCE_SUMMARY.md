# GateS API Evidence Summary

GateS 新增 API 均为 GET-only / read-only / no-side-effect / not trading authorization。

| Endpoint | Purpose | Read model source | Explicit non-goals |
| --- | --- | --- | --- |
| `GET /api/shadow-runs/overview` | Shadow Run overview 聚合 | `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` | 不创建 / 启动 / 停止 run；不提供写接口；不触发 runner / scheduler |
| `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}` | 单个 Shadow Run 的 Paper vs Shadow consistency drilldown | Shadow Run / event / snapshot / consistency 本地事实 | 不创建 report / snapshot / event；不写库；不触发交易 |
| `GET /api/strategy-validation/overview` | Strategy Evaluation Gate overview | strategy / backtest / publish / Paper / Shadow 本地事实 | `APPROVED` 仅为 validation 层语义；不表示 strategy approval 或真实交易授权 |
| `GET /api/incidents/replay/overview` | Incident / Replay 诊断概览 | Shadow / consistency / Paper alert / recovery / trade replay 本地诊断事实 | 不创建 incident / alert / recovery / replay；不启动自动处置 |

## Boundary

- 不提供 `POST` / `PUT` / `PATCH` / `DELETE`。
- 不提供 start / stop / execute / trade / placeOrder / cancelOrder / withdraw / transfer。
- 不调用真实交易所，不读取 credential material，不修改 account / ledger / order / position。
- 不开启 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter 或 real permission probe。
