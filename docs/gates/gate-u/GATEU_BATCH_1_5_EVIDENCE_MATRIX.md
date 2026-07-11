# GateU Batch 1-5 Evidence Matrix

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

| Batch | Commit | 后端 / API / 前端范围 | Tests | CI | Availability / freshness | Safety flags | No-side-effect 边界 | 已知限制 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GateU-1 | `c276d0ea` | backend metadata model/calculator；两个 overview response；Shadow Validation 与 Shadow Runs UI metadata | calculator、query、controller/response 与 targeted E2E | success 已确认；exact run id 未纳入本次重建证据 | 缺少有效时间或阈值时 fail-closed，不伪造 fresh | 四项固定 `true` | 只读 metadata，不写库、不执行 | 只覆盖两个既有来源 |
| GateU-2 | `14f18cba` | Consistency Evidence backend/response/UI metadata | query/controller 与 targeted E2E | success 已确认；exact run id 未纳入本次重建证据 | 继承统一 availability/freshness | 四项固定 `true` | SELECT-only 本地事实，不生成 report | 不提供 durable operator action |
| GateU-3 | `006b8ff9` | Incident / Replay Review backend/response/UI metadata | query/controller 与 targeted E2E | success 已确认；exact run id 未纳入本次重建证据 | 继承统一 availability/freshness | 四项固定 `true` | 不创建 incident，不启动 replay | review recommendation 不是自动处置 |
| GateU-4 | `0db719f2` | Evaluation Artifact Preview No-file backend/response/UI metadata | query/controller 与 targeted E2E | success 已确认；exact run id 未纳入本次重建证据 | `LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW` 为 `UNAVAILABLE / UNKNOWN` | 四项固定 `true` | 不读文件、不执行 Python、不导入 DB | Python artifact 未接入 |
| GateU-5 | `9f278583` | backend 五来源 aggregate、GET API、Strategy Validation 总览、TanStack Query hook/client | aggregate query/controller、frontend build、targeted Playwright | `29108265105` / `completed / success` | 全 `AVAILABLE` 才 available；全 `AVAILABLE / FRESH` 才 fresh；否则 fail-closed | aggregate 与来源四项固定 `true` | 每来源一次；GET-only；不重算、不启动 runtime | aggregate 是诊断总览，不是 scheduler/runner/runtime |

## Freeze Verdict

GateU-1～5 的实现、测试和 CI 证据足以支持 `FREEZE READY`；tag 尚不存在，因此只能是 `TAG PENDING`。矩阵不把旧 batch 的 exact CI run id 作为本次重建事实，也不把 No-file baseline 写成 Python artifact 已接入。
