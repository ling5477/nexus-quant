# GateQ API And Frontend Evidence Index

本文归档 GateQ 四个 API 与 `/strategies/validation` 前端只读视图的证据索引。所有条目均是 GateQ `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `TAGGED`（已打 tag）后的历史证据，不代表真实交易授权。

## API evidence

| GateQ batch | API | Evidence meaning | Safety boundary |
| --- | --- | --- | --- |
| GateQ-1 | `GET /api/strategies/evaluation-gate` | Strategy Evaluation Gate read-only baseline。聚合 strategy version、dataset quality、evaluation、publish trace 与 SIM Paper evidence。 | 只读、fail-closed；`READY_FOR_SHADOW_REVIEW` 不是交易授权，不启动 Shadow Live runner。 |
| GateQ-2 | `GET /api/strategies/paper-shadow/comparison` | Paper vs Shadow Comparison read-only baseline。聚合 strategy version、dataset、evaluation、publish、SIM Paper evidence 与 Shadow 未实现状态。 | 只读、fail-closed；Shadow runner / fact source 未实现时返回 `BLOCKED_SHADOW_NOT_IMPLEMENTED` / `NOT_IMPLEMENTED`。 |
| GateQ-3 | `GET /api/strategies/shadow-live/preview` | Shadow Live no-side-effect preview skeleton。组合 GateQ-1 / GateQ-2 只读结果，返回 preview readiness、trace status、side-effect policy 与 blockers。 | 不写库、不外联、不读 credential、不启动 runner；`READY_FOR_NO_SIDE_EFFECT_PREVIEW` 只表示可生成只读预览。 |
| GateQ-4 | `POST /api/research/evaluation-artifacts/binding-preview` | Python Evaluation Artifact Binding Preview Contract。只校验 request body artifact JSON 的 schema、checksum、hash、metrics、offline boundary 与 traceability。 | 不导入、不上传、不持久化、不写 Java fact source；`VALID_FOR_BINDING_PREVIEW` 不是 ML ready、live execution ready 或 strategy approval。 |

`docs/current/API.md` 是这些 endpoint 的当前 API 事实入口；本 archive 只保存 GateQ freeze 证据索引。

## Frontend evidence

| Page / file | Evidence meaning | Safety boundary |
| --- | --- | --- |
| `/strategies/validation` | Strategy Validation / Paper Shadow Comparison 只读页面。 | 只消费 GateQ-1 / GateQ-2 / GateQ-3 GET API，不新增后端能力。 |
| Strategy Lifecycle Trace | 展示 `strategyVersion -> dataset -> evaluation gate -> publish -> paper run -> Paper / Shadow Comparison -> Shadow Live Preview -> Python Artifact Binding Preview`。 | 缺失环节显示 incomplete / blocked / pending，不伪造成通过。 |
| Evidence Matrix | 聚合 requiredEvidence / missingEvidence / blockers / warnings / nextSteps。 | 阻断、警告、`NOT_IMPLEMENTED`、`NOT_AVAILABLE`、`UNKNOWN`、`PENDING_FRONTEND_SUPPORT` 均不得显示成成功。 |
| `strategy-validation-paper-shadow-smoke.spec.ts` | Mock/no-backend smoke 覆盖页面渲染、Evidence Matrix、状态解释、forbidden wording 和 GateQ-4 endpoint 不被调用。 | 不依赖真实后端，不调用真实交易所，不启动 Shadow Live runner。 |

## Forbidden interpretation

- 不得把 `READY_FOR_SHADOW_REVIEW` 写成 trading authorization。
- 不得把 `READY_FOR_COMPARISON` 写成 trading authorization。
- 不得把 `READY_FOR_NO_SIDE_EFFECT_PREVIEW` 写成 Shadow Live trading enabled。
- 不得把 `VALID_FOR_BINDING_PREVIEW` 写成 Python ML ready、Python live execution ready、Java fact 写入或 strategy approval。
- 不得把 `/strategies/validation` 写成实盘交易台、AI 决策中心、Shadow Live runner 控制台或真实交易入口。
