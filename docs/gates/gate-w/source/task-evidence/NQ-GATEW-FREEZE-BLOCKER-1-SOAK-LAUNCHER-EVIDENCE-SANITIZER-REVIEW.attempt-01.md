# NQ-GATEW-FREEZE-BLOCKER-1-SOAK-LAUNCHER-EVIDENCE-SANITIZER-REVIEW — Attempt 01

## Review target

- Task：`NQ-GATEW-FREEZE-BLOCKER-1-SOAK-LAUNCHER-EVIDENCE-SANITIZER-REMEDIATION`。
- 类型：`SECURITY_REMEDIATION / SOAK_LAUNCHER_FIX / EVIDENCE_SCHEMA_CONFORMANCE / SANITIZER_HARDENING`。
- 起始基线：`dev`，`HEAD == origin/dev == 4505c474679dbdf0b4f7f93cff413633734296a3`；`NQ CI Baseline` run `29509730259` 为 `completed / success / 10 jobs / bad=0`。
- Authority：GateW `IN_PROGRESS|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED`；next action `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`；LIVE `DISABLED`。
- 旧 blocked run：`gatew-soak-20260716T145410Z-230ae5be`；只作为 immutable BLOCKED evidence，不允许 resume、append、重写或计入 168 小时 acceptance clock。

## RCA and existing behavior

- 真实成功 cycle 必须按顺序执行 typed `OKX_ACCOUNT_CONFIGURATION_READ` 与 `OKX_ACCOUNT_BALANCE_READ`，旧 launcher 因而输出 `allowedEndpointCategory=ACCOUNT_CONFIG_AND_BALANCE_READ`。
- 旧 sanitizer 采用 payload substring denylist；合法 category 自身包含 `balance`，导致安全 cycle DTO 在落盘前被拒绝，`.cycle-*.json` 不生成。
- 旧 supervisor 只看到 launcher output 缺失，写入 `SOAK_LAUNCHER_FAILED` fallback；该 fallback 证明 fail-closed，但不能证明真实 cycle 的 endpoint/network/outcome。
- 旧 launcher DTO 还携带 DB/credential fingerprint、Flyway/allowlist version、authentication flag 与 kill-switch version；这些字段不属于 supervisor 判断所需最小合同，本轮删除而不迁移到 evidence。

## Frozen launcher-to-supervisor DTO

| 字段 | 来源 | 业务敏感 | 允许进入 launcher 临时输出 | 允许进入 sample evidence | 必须删除 | 原因 |
| --- | --- | --- | --- | --- | --- | --- |
| `schemaVersion` | 固定合同版本 | 否 | 是 | 是 | 否 | 区分 launcher v2 |
| `cycleId` | 本地随机 ID | 否 | 是 | 是 | 否 | 单 cycle 关联，不含账户事实 |
| `observedAt` | UTC clock | 否 | 是 | 是 | 否 | hash 与时序证据 |
| `durationMs` | 本地单调耗时结果 | 否 | 是 | 是 | 否 | 有界运行证据 |
| `resultStatus` | 固定结果枚举 | 否 | 是 | 是 | 否 | 区分 success/blocked/failed |
| `reasonCode` | 固定脱敏分类 | 否 | 是 | 是 | 否 | 保留真实失败/阻断原因 |
| `httpStatusCategory` | 固定 HTTP category | 否 | 是 | 是 | 否 | 不保存 status body/header |
| `permissionClassification` | 固定权限分类 | 否 | 是 | 是 | 否 | 不保存账户或 credential 数据 |
| `killSwitchObservedState` | kill-switch enum | 否 | 是 | 是 | 否 | fail-closed 边界证据 |
| `credentialAccessed` | typed transport provenance | 否 | 是 | 是 | 否 | 只保存布尔访问事实 |
| `networkCalled` | typed transport provenance | 否 | 是 | 是 | 否 | 只保存布尔调用事实 |
| `allowedEndpointCategory` | operation sequence mapping | 否 | 是 | 是 | 否 | 只保存 allowlisted endpoint category |
| `accountConfigProbeStatus` | config operation结果 | 否 | 是 | 是 | 否 | 只允许 `NOT_RUN/SUCCEEDED/BLOCKED/FAILED/UNKNOWN` |
| `balanceProbeStatus` | balance operation结果 | 否 | 是 | 是 | 否 | 只允许状态枚举，不保存余额值 |
| `traceId` | 本地随机安全 ID | 否 | 是 | 是 | 否 | 跨日志关联，不含 provider material |

必须删除且不得进入 launcher/sample 的旧字段：DB/credential fingerprint、Flyway/allowlist version、authentication flag、kill-switch version。余额值、币种/资产、账户标识、position/amount/size、raw request/response/header/body、完整 URL/query、signature、API key/secret/passphrase 一律不在 DTO 中。

## Review decision

- Sanitizer 必须采用 `exact allowlist + scalar/type/enum validation + semantic consistency + forbidden-network backstop`，不能再以字段名含 `balance` 为由整体拒绝。
- `PASSED_READ_ONLY` 必须同时证明 credential/network 已使用、endpoint category 精确为 config+balance、两个 probe status 均为 `SUCCEEDED`。
- 可解析且合同有效的 success/blocked/failed launcher DTO 均保留；只有缺失或不符合合同的 launcher output 才生成 `FAILED / LAUNCHER_OUTPUT_UNAVAILABLE / realCycleOutcomeProven=false`。
- 新 run 使用 `gatew-soak-evidence-v2`；旧 v1 hash 继续只读验证，禁止 append/resume/run-loop。
- Java 继续使用 Spring-managed `ObjectMapper`；不允许 `new ObjectMapper()` 或 raw provider DTO 序列化。

## Findings and boundary

- P0：0。
- P1：0；上述设计关闭 Attempt-06 两个 P1 根因。
- P2：0。
- P3：0。
- 不重跑 permission probe、不调用 OKX、不启动 soak、不读取/录入/轮换 credential；不改 endpoint allowlist、API、scheduler、migration、LIVE 或交易写侧。

## Decision

`PASS / SOAK_LAUNCHER_EVIDENCE_REMEDIATION_DESIGN_ACCEPTED / IMPLEMENTATION_AUTHORIZED`（通过 / launcher evidence remediation 设计已接受 / 允许按最小范围实现）。
