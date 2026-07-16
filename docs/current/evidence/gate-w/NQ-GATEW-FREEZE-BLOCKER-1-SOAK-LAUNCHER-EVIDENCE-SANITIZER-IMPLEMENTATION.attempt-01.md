# NQ-GATEW-FREEZE-BLOCKER-1-SOAK-LAUNCHER-EVIDENCE-SANITIZER-IMPLEMENTATION — Attempt 01

## Task classification and current fact

- 类型：`SECURITY_REMEDIATION / CODE_CHANGE / REGRESSION / COMMIT_AND_PUSH / EXACT_HEAD_CI / SERVER_DEPLOYMENT`。
- 当前本地结论：`PASS / SOAK_LAUNCHER_EVIDENCE_CONTRACT_REMEDIATED / SANITIZER_CONFORMANCE_PROVEN / READY_TO_COMMIT`（通过 / launcher evidence 合同已修复 / sanitizer 一致性已证明 / 可进入提交前复核）。
- Commit：`UNCOMMITTED`；remediation exact-head CI：`NOT_RUN`；server deployment：`PENDING`。
- 真实 OKX calls：`0`；permission probe：`NOT_RERUN`；真实 soak：`NOT_STARTED`；credential material access：`0`。

## Implementation

### Java launcher and sanitizer

- `CycleResult` 收口为固定 15 字段 `gatew-soak-launcher-v2` DTO，并用 `@JsonPropertyOrder` 固定序列化顺序。
- 新增 `ProbeStatus`：`NOT_RUN / SUCCEEDED / BLOCKED / FAILED / UNKNOWN`；`CountingTransport` 只记录 typed operation 与状态，不保留 asset count、余额值或 raw response。
- `EvidenceSanitizer` 在序列化前验证 DTO，在序列化后再次验证 exact fields、字段顺序、scalar type、固定枚举与 endpoint/probe/network/credential 语义；未知、大小写/下划线变体与嵌套字段全部拒绝。
- `PASSED_READ_ONLY` 必须同时具备 config/balance 两个 `SUCCEEDED`；blocked/failed 保留真实 `reasonCode`，不伪造 PASS。
- `ObjectMapper` 来自最小 Spring Jackson test context，并覆盖 `Instant / OffsetDateTime / LocalDateTime / enum / boolean / null`；目标 launcher 不再创建裸 mapper。
- sanitized JSON 先写同目录临时文件再 atomic move；序列化 byte array 在 `finally` 清零，临时文件尽力删除。
- 删除 supervisor 无消费者的 DB/credential fingerprint 等旧 DTO 字段；无 Spring production 自动装配、远端合同或默认 runtime 行为变化。

### PowerShell supervisor and evidence

- 新 run manifest 标记 `launcherSchemaVersion=gatew-soak-launcher-v2`、`evidenceSchemaVersion=gatew-soak-evidence-v2`。
- v2 sample 仅在 15 个 launcher 字段外增加 `sequence / realCycleOutcomeProven / previousRecordHash / recordHash`。
- 合同有效的 success/blocked/failed DTO 都原样映射为 supervisor cycle；只有 launcher output 缺失或不符合合同才生成 `FAILED / LAUNCHER_OUTPUT_UNAVAILABLE / realCycleOutcomeProven=false`。
- fallback 的 endpoint/probe provenance 固定为 `NONE / UNKNOWN / UNKNOWN`，不能计入 `validRealPassSamples`；真实 launcher cycle 不能使用 fallback reason，也不能携带 UNKNOWN probe outcome。
- v1 sample 字段顺序、canonical hash input 与算法保持不变，只读 verifier 继续支持；v1 与 terminal v2 run 均拒绝 resume/run-loop，v1 拒绝 append。
- UTF-8 no-BOM、UTC timestamp、固定属性顺序、CRLF/LF 与 locale-independent hash 由同一 supervisor/verifier 路径验证。

## Files created

- 本任务三份 `docs/current/evidence/gate-w/*.attempt-01.md` evidence。

## Files changed

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakSupportTest.java`
- `scripts/gatew/gatew-okx-readonly-soak.ps1`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/evidence/gate-w/README.md`

## Local validation

安全环境：`CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。

| Command | Result | Scope |
| --- | --- | --- |
| focused `GateWOkxReadonlySoakSupportTest` | `PASS` | 35 tests；0 failures/errors/skips；23/23 reactor modules SUCCESS |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app,nq-adapter-okx -am test` | `PASS` | 23/23 modules SUCCESS；BUILD SUCCESS；`nq-app` 196 tests、8 existing skipped |
| `mvn -f backend/pom.xml test` | `PASS` | 23/23 modules SUCCESS；BUILD SUCCESS；同一 no-outbound 环境 |
| Windows PowerShell 5.1 `-Action self-test` | `PASS` | 36 cases；unsafe fixture rejection=15 |
| PowerShell 7 `-Action self-test` | `PASS` | 36 cases；unsafe fixture rejection=15 |
| cross-engine canonical fixture hash | `PASS` | 两个引擎均为 `0127615a5334312d890e2d563f787268498f4e9e99f60cab35671a486d4caa59` |

已知非阻断 warning：既有 Mockito dynamic-agent/JDK future warning、SLF4J NOP、unchecked/deprecation 与 checkout EOL warning。第一次 focused 命令因 PowerShell 未引用 `-Dsurefire.failIfNoSpecifiedTests=false` 被 Maven 当成 lifecycle token，未进入编译；加引号后已重跑并通过。

## Old blocked run and post-commit gates

- 本地实现不读取或改写旧 run。服务器部署阶段必须在部署前后比较旧 run durable files 与 permission metadata 的 count/hash，并证明不变。
- 旧 run 必须继续为 `BLOCKED / SOAK_LAUNCHER_FAILED`、valid real PASS=0、supervisor/run-loop=0、`final-summary.json` absent、kill switch ENGAGED。
- Post-commit 尚待：remediation commit/push、exact-head `NQ CI Baseline` 10/10 GREEN、服务器 artifact hash 一致、Linux offline self-test/safe fixture/unsafe rejection/hash verification/loopback health。
- 服务器阶段仍禁止 permission probe、`/api/v5/account/config`、`/api/v5/account/balance`、credential读取与 soak start。

## Boundary and next action

无 API、Controller、scheduler、migration、endpoint allowlist、frontend、research、deploy、`.github`、Gate archive、LIVE、order/cancel/transfer/withdraw diff。Authority 不变。

下一动作：精确暂存、commit `fix(gatew): align soak evidence sanitizer contract`、push `dev` 并等待 exact-head CI；CI GREEN 后只执行服务器离线部署验证。
