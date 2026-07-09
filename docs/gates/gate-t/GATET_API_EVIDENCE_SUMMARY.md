# GateT API Evidence Summary

状态：GateT API evidence `FROZEN / ACCEPTED`（已冻结 / 已接受）。

## GET-only API

GateT 新增 API 均为 GET-only / read-only / no-side-effect / not trading authorization：

| GateT slice | Endpoint | Evidence summary | Boundary |
| --- | --- | --- | --- |
| GateT-1 Shadow Validation Workflow | `GET /api/shadow-validation/workflow/overview` | 派生 Shadow Validation Workflow operator items、workflowState、validationDecision、severity、freshness、blockers / warnings / nextSteps 和 evidence anchors | derived / deterministic；不持久化 operator item；不读取 credential；不启动 runner / scheduler |
| GateT-2 Consistency Evidence | `GET /api/paper-shadow/consistency/evidence/overview` | 派生 consistency evidence items、severity / freshness summary、metricDelta 摘要和 evidence anchors | 不创建 consistency report；不返回 raw JSONB payload；不生成交易建议 |
| GateT-3 Incident / Replay Review | `GET /api/incidents/replay/review/overview` | 派生 Incident / Replay review items、reviewState、reviewDecision、severity、freshness、blockers / warnings / nextSteps 和 evidence anchors | recommendations only；不创建 review / acknowledge / escalation / closeout / incident / alert / replay 记录 |
| GateT-4 Evaluation Artifact Preview | `GET /api/strategy-validation/evaluation-artifacts/preview/overview` | 返回 Python Evaluation Artifact Preview No-file baseline、安全 flags、schema / checksum / metric coverage、warnings / nextSteps 和 evidence anchors | 不读取 artifact 文件或 manifest；不执行 Python；不导入 DB；不表示 ML ready 或 live execution ready |

## 未新增的 API

GateT-5 未新增 API；只在现有 `/strategies/validation` 页面复用既有 GET-only hooks。GateT-6 未新增 API；只做 runtime scheduling readiness review。

GateT 未新增 POST / PUT / PATCH / DELETE，也未新增 review、acknowledge、approve、reject、escalate、closeout、start、stop、execute、trade、order、cancel、withdraw 或 transfer endpoint。

## 安全边界

所有 GateT API 证据均保持：

- LIVE：`DISABLED`（关闭）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Not trading authorization：任何 validation / consistency / review / artifact preview 状态都不构成交易授权。
