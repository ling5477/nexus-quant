# NQ-DH Integration-0 Contract Test Plan

> 任务：NQ-DH-INTEGRATION-0-CONTRACT-FREEZE
> 类型：DOCUMENTATION + CONTRACT DESIGN
> 日期：2026-06-11
> 仓库视角：NexusQuant（NQ）
> 配套：`NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`、`NQ_DH_INTEGRATION0_SECURITY_POLICY.md`

本文件**只写测试计划，不写测试代码**。所有用例在 Integration-0 阶段只允许 mock / stub / contract test 实现，**禁止真实 HTTP、禁止真实交易副作用、禁止真实凭证访问**。

---

## 1. 测试范围与原则

- 目标：验证**契约面**（schema / header / 签名 / replay / tenant / payload / forbidden field / 审计 / 无副作用），不验证实盘收益。
- 实现方式：mock server / stub / contract test（WireMock / MockWebServer 或等价），全部隔离，不连真实 NQ、不连真实 DH、不连真实交易所。
- 数据：deterministic 固定数据；tenant 使用 `t-test-*` 前缀；禁止真实账户、真实凭证。
- 每个用例字段：`testName / targetSystem / purpose / input / expectedResult / forbiddenSideEffect / whetherBlocksIntegration0`。

## 2. 必测用例（15 项，冻结）

### T1 禁止能力 contract test
- targetSystem：NQ
- purpose：验证 DH 无法触达下单/撤单/Paper 启停/策略状态/风控/凭证/DB 等禁止能力。
- input：mock 请求尝试调用禁止能力端点（契约层占位）。
- expectedResult：契约层标记为 forbidden，返回 403/423；无对应可达路径。
- forbiddenSideEffect：任何下单/撤单/状态变更/凭证读取/DB 写入。
- whetherBlocksIntegration0：是（必须通过才算冻结有效）。

### T2 可开放能力 contract test
- targetSystem：NQ / DH
- purpose：验证可开放只读/候选能力契约结构正确（schema/字段/枚举）。
- input：mock 合法候选信号 / 只读摘要请求。
- expectedResult：schema 校验通过，返回 candidate-only / 只读摘要。
- forbiddenSideEffect：进入交易执行路径。
- whetherBlocksIntegration0：是。

### T3 header 缺失测试
- targetSystem：NQ
- purpose：任一 required header 缺失必须拒绝。
- input：分别缺 Source / Tenant-Id / Request-Id / Trace-Id / Timestamp / Nonce / Signature。
- expectedResult：缺认证类 401；缺来源/租户类 403；缺幂等/追踪类 400。
- forbiddenSideEffect：任何写入或执行。
- whetherBlocksIntegration0：是。

### T4 HMAC 签名失败测试
- targetSystem：NQ
- purpose：错误/缺失签名必须拒绝。
- input：错误签名、空签名、用错 secret 的签名。
- expectedResult：401/403；审计落库但不记录签名原材料。
- forbiddenSideEffect：任何写入或执行；签名原材料落日志。
- whetherBlocksIntegration0：是。

### T5 timestamp 过期测试
- targetSystem：NQ
- purpose：超出 ±300 秒窗口必须拒绝。
- input：timestamp 早于/晚于窗口。
- expectedResult：401/403。
- forbiddenSideEffect：接受过期请求。
- whetherBlocksIntegration0：是。

### T6 nonce replay 测试
- targetSystem：NQ
- purpose：重复 nonce/requestId 必须拒绝。
- input：同 `Source+Nonce+RequestId` 两次提交。
- expectedResult：首次接受（mock），重放 409。
- forbiddenSideEffect：重复执行/重复写入。
- whetherBlocksIntegration0：是。

### T7 tenant mismatch 测试
- targetSystem：NQ
- purpose：header tenant 与认证主体不一致必须拒绝。
- input：认证 tenant 与 `X-NQ-DH-Tenant-Id` 不一致。
- expectedResult：403。
- forbiddenSideEffect：跨租户读写。
- whetherBlocksIntegration0：是。

### T8 payload 超 64 KiB 测试
- targetSystem：NQ
- purpose：超限拒绝，不截断。
- input：> 65536 bytes 的 body。
- expectedResult：413。
- forbiddenSideEffect：截断后接受。
- whetherBlocksIntegration0：是。

### T9 forbidden field rejection 测试
- targetSystem：NQ / DH
- purpose：契约出现禁止字段必须拒绝。
- input：payload 含 apiKey/secret/token/privateKey/mnemonic 等任一。
- expectedResult：拒绝（NQDhContractError errorCategory=FORBIDDEN_FIELD）。
- forbiddenSideEffect：禁止字段落日志/落库/回显。
- whetherBlocksIntegration0：是。

### T10 raw prompt / context rejection 测试
- targetSystem：DH / NQ
- purpose：契约禁止携带 full prompt / full context / raw request / raw response。
- input：payload 含 fullPrompt/fullContext/rawRequest/rawResponse。
- expectedResult：拒绝。
- forbiddenSideEffect：prompt/context 落库或外发。
- whetherBlocksIntegration0：是。

### T11 candidate signal schema validation 测试
- targetSystem：NQ
- purpose：DHSignalCandidate schema 校验。
- input：合法/非法（缺字段、错枚举、confidence 越界、candidateOnly≠true）。
- expectedResult：合法接受为候选；非法 400。
- forbiddenSideEffect：非法信号进入执行。
- whetherBlocksIntegration0：是。

### T12 NQ feedback event schema validation 测试
- targetSystem：DH
- purpose：NQFeedbackEvent schema 校验。
- input：合法/非法（缺 eventId、错 eventType、sourceSystem≠NQ）。
- expectedResult：合法 ingest；非法 400。
- forbiddenSideEffect：DH 反写 NQ。
- whetherBlocksIntegration0：是。

### T13 audit log required test
- targetSystem：NQ / DH
- purpose：接收/拒绝/限流/重放/风控结果必须落审计。
- input：触发各类接收与拒绝路径。
- expectedResult：审计记录存在且字段脱敏（无密钥/签名原材料/raw payload）。
- forbiddenSideEffect：审计缺失或记录敏感值。
- whetherBlocksIntegration0：是。

### T14 no trading side-effect test
- targetSystem：NQ
- purpose：任何契约请求不得产生交易副作用。
- input：全部可开放能力请求（mock）。
- expectedResult：无订单/撤单/Paper 启停/策略状态/风控状态变更。
- forbiddenSideEffect：任何交易副作用。
- whetherBlocksIntegration0：是。

### T15 no credential access test
- targetSystem：NQ
- purpose：任何契约请求不得触达凭证/交易所 secret/DB。
- input：尝试触达 credential / exchange secret / DB（契约层占位）。
- expectedResult：无可达路径；契约层拒绝。
- forbiddenSideEffect：读取/输出任何凭证或 secret。
- whetherBlocksIntegration0：是。

## 3. 测试矩阵汇总

| 用例 | 目标 | 阻塞 Integration-0 | 禁止副作用核心 |
| --- | --- | --- | --- |
| T1 | NQ | 是 | 无执行/凭证/DB |
| T2 | NQ/DH | 是 | 不进执行路径 |
| T3 | NQ | 是 | 无写入/执行 |
| T4 | NQ | 是 | 无执行；签名材料不落日志 |
| T5 | NQ | 是 | 不接受过期 |
| T6 | NQ | 是 | 不重复执行 |
| T7 | NQ | 是 | 不跨租户 |
| T8 | NQ | 是 | 不截断接受 |
| T9 | NQ/DH | 是 | 禁止字段不落库 |
| T10 | DH/NQ | 是 | prompt/context 不外发 |
| T11 | NQ | 是 | 非法信号不进执行 |
| T12 | DH | 是 | DH 不反写 NQ |
| T13 | NQ/DH | 是 | 审计不漏不泄敏 |
| T14 | NQ | 是 | 无交易副作用 |
| T15 | NQ | 是 | 无凭证访问 |

## 4. 实现约束（冻结）

- 本轮**不实现**任何测试代码；仅冻结测试计划。
- 未来实现这些 contract test 时，必须：
  - 全部用 mock / stub / fake，禁止真实 HTTP、真实 NQ、真实 DH、真实交易所。
  - 使用 `t-test-*` tenant，禁止真实账户与真实凭证。
  - 任一阻塞 Integration-0 的用例不通过，则契约冻结视为无效，必须修订契约。

## 5. Integration-1 前置（不在本轮）

- DH P1-4 残留（rate limit / memory cap / replay nonce 持久化）必须先修复，才允许 Integration-1 的真实只读接入/真实通道 contract test。
- 真实通道测试必须隔离 staging / test cluster，Paper-only，LIVE 关闭。

---

# 详细 mock / contract test 设计（NQ-DH-INTEGRATION0-MOCK-CONTRACT-TEST-DESIGN）

> 本节为 2026-06-11 增补：将第 2 节的 15 项冻结计划拆成可执行测试矩阵。
> 仍然**只做设计，不写测试代码**；所有 `futureCodeLocationSuggestion` 只是建议路径，不得创建代码文件。
> 全部用例 mock / stub / fake 隔离，禁止真实 HTTP / 真实 NQ / 真实 DH / 真实交易所 / 真实凭证。

## 6. 公共约定

- canonical headers：`X-NQ-DH-Source / X-NQ-DH-Tenant-Id / X-NQ-DH-Request-Id / X-NQ-DH-Trace-Id / X-NQ-DH-Timestamp / X-NQ-DH-Nonce / X-NQ-DH-Signature` + `Content-Type: application/json`。
- canonical timestamp：`X-NQ-DH-Timestamp` 固定为 RFC3339 / ISO-8601 UTC `Z`，示例 `2026-06-15T12:34:56Z`；epoch seconds、epoch milliseconds、数字时区偏移（如 `+08:00`）均不是 canonical wire format。
- 拒绝码：`400 schema/字段` `401 认证` `403 权限/tenant/source` `409 幂等/replay` `413 payload>64KiB` `423 gate disabled` `429 限流`。
- 审计事件命名（mock 断言用）：`REQUEST_RECEIVED / REQUEST_REJECTED / SIGNATURE_FAILED / REPLAY_REJECTED / TENANT_MISMATCH / PAYLOAD_TOO_LARGE / FORBIDDEN_FIELD_REJECTED / RATE_LIMITED / IDEMPOTENCY_CONFLICT / RISK_RESULT_RECORDED`。
- tenant 固定用 `t-test-*`；secret 用测试占位（绝不用真实密钥）。
- `blocksIntegration0=true` 表示该用例不通过则契约冻结无效。
- `blocksIntegration1=true` 表示该用例是进入/通过 Integration-1（真实只读通道）的强制门禁，实现真实通道时必须以真实模式重跑。

## 7. NQ Contract Test Matrix（NQ 为受测主权方）

下列用例 `targetSystem` 聚焦 NQ 入站校验与无副作用；`implementationOwner` 指主要负责实现方。

### INT0-T01 禁止能力 contract test
- testName：forbidden_capability_contract
- targetSystem：NQ
- testType：SECURITY / NEGATIVE
- purpose：验证 DH 无法触达下单/撤单/Paper 启停/改策略·风控状态/读凭证/读写 NQ DB 等禁止能力。
- inputFixture：`FX-FORBIDDEN-CALLS`（一组指向禁止能力的伪请求，契约层占位）。
- requiredHeaders：全 7 个 + Content-Type。
- payload：尝试调用禁止能力的最小 body。
- expectedStatus：403 或 423（无对应可达路径）。
- expectedResult：契约层标记 forbidden，返回 `NQDhContractError`；无能力路由命中。
- expectedAuditEvent：`REQUEST_REJECTED`。
- forbiddenSideEffect：下单/撤单/状态变更/凭证读取/DB 写入。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion（建议，不创建）：`backend/nq-app/src/test/java/.../integration0/contract/ForbiddenCapabilityContractTest.java`

### INT0-T02 可开放能力 contract test
- testName：allowed_capability_contract
- targetSystem：NQ
- testType：CONTRACT
- purpose：验证可开放只读/候选能力契约结构正确（schema/字段/枚举）。
- inputFixture：`FX-CANDIDATE-VALID`、`FX-READONLY-QUERY`。
- requiredHeaders：全 7 个 + Content-Type。
- payload：合法 `DHSignalCandidate` / 只读摘要查询。
- expectedStatus：候选写入 202/200（mock）；只读 200。
- expectedResult：返回 candidate-only / 只读摘要；不进入执行路径。
- expectedAuditEvent：`REQUEST_RECEIVED`。
- forbiddenSideEffect：进入交易执行路径。
- blocksIntegration0：true ｜ blocksIntegration1：false（结构在 Int-0 已锁定，Int-1 回归即可）
- implementationOwner：NQ
- futureCodeLocationSuggestion：`backend/nq-app/src/test/java/.../integration0/contract/AllowedCapabilityContractTest.java`

### INT0-T03 header 缺失测试
- testName：missing_header_rejection
- targetSystem：NQ
- testType：NEGATIVE / SECURITY
- purpose：任一 required header 缺失必须拒绝。
- inputFixture：`FX-HEADER-MATRIX`（逐个移除 7 个 header）。
- requiredHeaders：逐项缺失变体。
- payload：合法 `DHSignalCandidate`。
- expectedStatus：缺认证类 401；缺 Source/Tenant 403；缺 Request-Id/Trace-Id 400。
- expectedResult：返回 `NQDhContractError`，errorCategory 对应。
- expectedAuditEvent：`REQUEST_REJECTED`。
- forbiddenSideEffect：任何写入或执行。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/contract/HeaderPresenceContractTest.java`

### INT0-T04 HMAC 签名失败测试
- testName：hmac_signature_failure
- targetSystem：NQ
- testType：SECURITY / NEGATIVE
- purpose：错误/缺失/错 secret 签名必须拒绝，且签名原材料不落日志。
- inputFixture：`FX-BAD-SIGNATURE`（错签名 / 空签名 / 错 secret 三变体）。
- requiredHeaders：全 7 个，Signature 为非法值。
- payload：合法 `DHSignalCandidate`。
- expectedStatus：401 或 403。
- expectedResult：拒绝；审计落库但不含签名原材料。
- expectedAuditEvent：`SIGNATURE_FAILED`。
- forbiddenSideEffect：执行；签名原材料 / canonical string 落日志或落库。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/security/HmacSignatureContractTest.java`

### INT0-T05 timestamp 过期测试
- testName：timestamp_window_rejection
- targetSystem：NQ
- testType：SECURITY / NEGATIVE
- purpose：验证 `X-NQ-DH-Timestamp` 必须使用 RFC3339 / ISO-8601 UTC `Z`，且超出 ±300 秒窗口必须拒绝。
- inputFixture：`FX-TIMESTAMP`（合法 `2026-06-15T12:34:56Z`、窗口前 / 窗口后 / 边界值、epoch seconds、epoch milliseconds、数字时区偏移如 `+08:00`）。
- requiredHeaders：全 7 个，Timestamp 使用 canonical UTC `Z` 或非法格式 / 越界变体。
- payload：合法 `DHSignalCandidate`。
- expectedStatus：401 或 403。
- expectedResult：接受窗口内 RFC3339 UTC `Z`；拒绝过期请求；拒绝 epoch seconds / epoch milliseconds / 数字时区偏移。
- expectedAuditEvent：`REQUEST_REJECTED`。
- forbiddenSideEffect：接受过期请求。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/security/TimestampWindowContractTest.java`

### INT0-T06 nonce replay 测试
- testName：nonce_replay_rejection
- targetSystem：NQ
- testType：SECURITY / NEGATIVE
- purpose：重复 `Source+Nonce+RequestId` 必须拒绝。
- inputFixture：`FX-REPLAY`（同组合两次提交）。
- requiredHeaders：全 7 个，第二次 Nonce/RequestId 复用。
- payload：合法 `DHSignalCandidate`。
- expectedStatus：首次接受（mock），重放 409。
- expectedResult：重放拒绝。
- expectedAuditEvent：`REPLAY_REJECTED`。
- forbiddenSideEffect：重复执行/重复写入。
- blocksIntegration0：true ｜ blocksIntegration1：true（Int-1 必须以**持久化 nonce** 重跑，见 §12 P1-4）
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/security/NonceReplayContractTest.java`

### INT0-T07 tenant mismatch 测试
- testName：tenant_binding_mismatch
- targetSystem：NQ
- testType：SECURITY / NEGATIVE
- purpose：header tenant 与认证主体不一致必须拒绝。
- inputFixture：`FX-TENANT-MISMATCH`。
- requiredHeaders：全 7 个，`X-NQ-DH-Tenant-Id` 与认证 tenant 不一致。
- payload：合法 `DHSignalCandidate`。
- expectedStatus：403。
- expectedResult：拒绝；不跨租户读写。
- expectedAuditEvent：`TENANT_MISMATCH`。
- forbiddenSideEffect：跨租户读写。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/security/TenantBindingContractTest.java`

### INT0-T08 payload 超 64 KiB 测试
- testName：payload_size_limit
- targetSystem：NQ
- testType：NEGATIVE / SECURITY
- purpose：超 65536 bytes 拒绝，不截断。
- inputFixture：`FX-OVERSIZE-PAYLOAD`（65537+ bytes）。
- requiredHeaders：全 7 个。
- payload：超限 body。
- expectedStatus：413。
- expectedResult：拒绝，不截断后接受。
- expectedAuditEvent：`PAYLOAD_TOO_LARGE`。
- forbiddenSideEffect：截断后接受。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/contract/PayloadSizeContractTest.java`

### INT0-T09 forbidden field rejection 测试
- testName：forbidden_field_rejection
- targetSystem：NQ
- testType：SECURITY / NEGATIVE
- purpose：契约出现禁止字段必须拒绝。
- inputFixture：`FX-FORBIDDEN-FIELDS`（apiKey/secret/token/privateKey/mnemonic/dbDsn/authorizationHeader/fullPrompt 等逐项）。
- requiredHeaders：全 7 个。
- payload：含任一禁止字段的 body。
- expectedStatus：400（`NQDhContractError` errorCategory=FORBIDDEN_FIELD）。
- expectedResult：拒绝；禁止字段不落库、不回显。
- expectedAuditEvent：`FORBIDDEN_FIELD_REJECTED`。
- forbiddenSideEffect：禁止字段落日志/落库/回显。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：BOTH
- futureCodeLocationSuggestion：`.../integration0/security/ForbiddenFieldContractTest.java`

### INT0-T10 raw prompt / context rejection 测试
- testName：raw_prompt_context_rejection
- targetSystem：NQ
- testType：SECURITY / NEGATIVE
- purpose：契约禁止携带 fullPrompt/fullContext/rawRequest/rawResponse。
- inputFixture：`FX-RAW-PROMPT`。
- requiredHeaders：全 7 个。
- payload：含 raw prompt/context 字段。
- expectedStatus：400（FORBIDDEN_FIELD）。
- expectedResult：拒绝；prompt/context 不落库、不外发。
- expectedAuditEvent：`FORBIDDEN_FIELD_REJECTED`。
- forbiddenSideEffect：prompt/context 落库或外发。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：BOTH
- futureCodeLocationSuggestion：`.../integration0/security/RawPromptRejectionContractTest.java`

### INT0-T11 candidate signal schema validation 测试
- testName：candidate_signal_schema
- targetSystem：NQ
- testType：CONTRACT / NEGATIVE
- purpose：`DHSignalCandidate` schema 校验（合法 + 非法变体）。
- inputFixture：`FX-CANDIDATE-VALID`、`FX-CANDIDATE-INVALID`（缺字段/错枚举/confidence 越界/candidateOnly≠true）。
- requiredHeaders：全 7 个。
- payload：合法/非法 `DHSignalCandidate`。
- expectedStatus：合法 202/200；非法 400。
- expectedResult：合法落候选；非法拒绝且不进入执行。
- expectedAuditEvent：合法 `REQUEST_RECEIVED`；非法 `REQUEST_REJECTED`。
- forbiddenSideEffect：非法信号进入执行路径。
- blocksIntegration0：true ｜ blocksIntegration1：false
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/contract/CandidateSignalSchemaContractTest.java`

### INT0-T12 NQ feedback event schema validation 测试
- testName：nq_feedback_event_schema
- targetSystem：DH（NQ 侧产出 envelope）
- testType：CONTRACT / NEGATIVE
- purpose：`NQFeedbackEvent` schema 校验。
- inputFixture：`FX-FEEDBACK-VALID`、`FX-FEEDBACK-INVALID`（缺 eventId/错 eventType/sourceSystem≠NQ）。
- requiredHeaders：全 7 个（NQ→DH 方向同一 header 族）。
- payload：合法/非法 `NQFeedbackEvent`。
- expectedStatus：合法 202；非法 400。
- expectedResult：合法 ingest；非法拒绝；DH 不反写 NQ。
- expectedAuditEvent：合法 `REQUEST_RECEIVED`；非法 `REQUEST_REJECTED`。
- forbiddenSideEffect：DH 反写 NQ。
- blocksIntegration0：true ｜ blocksIntegration1：false
- implementationOwner：DH
- futureCodeLocationSuggestion（DH 仓库）：`dh-domain/src/test/java/.../integration0/NqFeedbackEventSchemaContractTest.java`

### INT0-T13 audit log required test
- testName：audit_log_required
- targetSystem：BOTH
- testType：SECURITY / CONTRACT
- purpose：接收/拒绝/限流/重放/风控结果必须落审计且脱敏。
- inputFixture：`FX-AUDIT-PATHS`（覆盖各类接收与拒绝路径）。
- requiredHeaders：按各子用例。
- payload：按各子用例。
- expectedStatus：随触发路径。
- expectedResult：审计记录存在；字段脱敏；无密钥/签名原材料/raw payload。
- expectedAuditEvent：对应事件均生成（见 §6 命名）。
- forbiddenSideEffect：审计缺失或记录敏感值。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：BOTH
- futureCodeLocationSuggestion：`.../integration0/security/AuditLogRequiredContractTest.java`

### INT0-T14 no trading side-effect test
- testName：no_trading_side_effect
- targetSystem：NQ
- testType：SECURITY
- purpose：任何契约请求不得产生交易副作用。
- inputFixture：`FX-ALL-ALLOWED`（全部可开放能力请求，mock）。
- requiredHeaders：全 7 个。
- payload：各可开放能力合法 body。
- expectedStatus：200/202。
- expectedResult：无订单/撤单/Paper 启停/策略状态/风控状态变更（mock 计数器为 0）。
- expectedAuditEvent：`REQUEST_RECEIVED`（无执行类事件）。
- forbiddenSideEffect：任何交易副作用。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/security/NoTradingSideEffectContractTest.java`

### INT0-T15 no credential access test
- testName：no_credential_access
- targetSystem：NQ
- testType：SECURITY
- purpose：任何契约请求不得触达凭证/交易所 secret/DB。
- inputFixture：`FX-CRED-PROBE`（尝试触达 credential / exchange secret / DB，契约层占位）。
- requiredHeaders：全 7 个。
- payload：探测性 body。
- expectedStatus：403/423（无可达路径）。
- expectedResult：契约层拒绝；无凭证/secret 读取。
- expectedAuditEvent：`REQUEST_REJECTED`。
- forbiddenSideEffect：读取/输出任何凭证或 secret。
- blocksIntegration0：true ｜ blocksIntegration1：true
- implementationOwner：NQ
- futureCodeLocationSuggestion：`.../integration0/security/NoCredentialAccessContractTest.java`

## 8. Shared Fixture List（共享 fixture，仅数据契约，不含真实值）

| fixtureId | 用途 | 关键内容 | 敏感性 |
| --- | --- | --- | --- |
| FX-HEADERS-VALID | 合法 header 集 | 7 header + Content-Type，测试占位 secret | 无真实密钥 |
| FX-CANDIDATE-VALID | 合法候选信号 | `DHSignalCandidate` candidateOnly=true | 脱敏 |
| FX-CANDIDATE-INVALID | 非法候选信号 | 缺字段/错枚举/confidence 越界/candidateOnly=false | 脱敏 |
| FX-READONLY-QUERY | 只读查询 | strategyMetadata / backtestSummary / paperResultSummary 查询 | 脱敏 |
| FX-FEEDBACK-VALID | 合法 feedback | `NQFeedbackEvent` sourceSystem=NQ | 脱敏 |
| FX-FEEDBACK-INVALID | 非法 feedback | 缺 eventId/错 eventType/sourceSystem≠NQ | 脱敏 |
| FX-HEADER-MATRIX | header 缺失矩阵 | 逐个移除 7 header | 无 |
| FX-BAD-SIGNATURE | 签名失败 | 错签名/空签名/错 secret | 测试占位 secret |
| FX-TIMESTAMP | 时间窗口 | 窗口前/后/边界 | 无 |
| FX-REPLAY | 重放 | 同 Source+Nonce+RequestId 两次 | 无 |
| FX-TENANT-MISMATCH | 租户不一致 | header tenant ≠ 认证 tenant | t-test-* |
| FX-OVERSIZE-PAYLOAD | 超限 payload | 65537+ bytes | 无 |
| FX-FORBIDDEN-FIELDS | 禁止字段 | apiKey/secret/token/privateKey/mnemonic/dbDsn/authHeader 等 | 占位假值 |
| FX-RAW-PROMPT | raw prompt/context | fullPrompt/fullContext/rawRequest/rawResponse | 占位 |
| FX-FORBIDDEN-CALLS | 禁止能力调用 | 指向下单/撤单/Paper/凭证/DB 的伪请求 | 无 |
| FX-CRED-PROBE | 凭证探测 | 触达 credential/exchange secret/DB 探测 | 无真实凭证 |
| FX-ALL-ALLOWED | 全可开放能力 | 各可开放能力合法 body 集合 | 脱敏 |
| FX-AUDIT-PATHS | 审计路径 | 覆盖接收/拒绝/replay/限流/风控 | 脱敏 |

fixture 约束：全部为占位/脱敏数据；禁止包含真实 API key/secret/token/凭证；tenant 一律 `t-test-*`；签名 secret 为测试专用占位值。

## 9. Forbidden Side-Effect Checklist（统一副作用检查）

每个用例运行后必须断言以下副作用计数为 0 / 未发生：

```text
[ ] 无下单（place order）
[ ] 无撤单（cancel order）
[ ] 无订单状态变更
[ ] 无策略状态变更（启停/发布）
[ ] 无 Paper Run 启动
[ ] 无 Paper Run 停止
[ ] 无风控状态变更
[ ] 无交易所凭证读取
[ ] 无 NQ credential 读取
[ ] 无 NQ DB 读/写
[ ] 无真实 HTTP 出站
[ ] 无真实交易所调用
[ ] 无 LIVE 触发
[ ] 无禁止字段落库/落日志/回显
[ ] 无签名原材料/raw payload/prompt/context 落库
[ ] 无跨租户数据访问
```

## 10. Integration-0 Acceptance Checklist

```text
[ ] T01..T15 全部设计完整（16 字段齐全）
[ ] 所有 blocksIntegration0=true 用例在未来实现后必须全绿，否则契约冻结无效
[ ] 全部用例均为 mock/stub/fake，无真实 HTTP/NQ/DH/交易所/凭证
[ ] shared fixture 全部脱敏、无真实密钥、tenant=t-test-*
[ ] forbidden side-effect checklist 16 项纳入每个用例断言
[ ] 审计事件命名统一（§6）
[ ] 本轮 docs-only，未写测试代码、未改业务代码/API/migration
```

## 11. Integration-1 Blocker Checklist

进入 Integration-1（真实只读通道）前，除上述安全/副作用用例需以真实模式重跑外，必须额外满足（DH P1-4 residual，本轮不修复）：

```text
[ ] rate limit：新增 429 限流 contract test（租户/能力级），Int-0 mock 未覆盖真实限流
[ ] memory cap：DH InMemory 仓储上限/外部存储就绪后补容量/退化测试
[ ] replay nonce persistence：T06 必须以持久化/集中缓存 nonce 在多实例场景重跑
[ ] header 命名 X-DH-NQ-* 与 X-NQ-DH-* 对齐验证
[ ] 真实通道隔离 staging/test cluster，Paper-only，LIVE 关闭
```

## 12. Next Implementation Task Draft（草案，本轮不执行）

> 仅作为后续“写测试代码”任务的输入材料，本轮不创建任何代码文件。

```text
任务名（草案）：NQ-DH-INTEGRATION0-CONTRACT-TEST-IMPL
类型：CODE_CHANGE（测试代码）+ CONTRACT TEST
前置：本设计文档冻结
范围：
  - NQ 仓库：在建议的 backend/nq-app/src/test/.../integration0/{contract,security} 下实现
    T01..T15 的 NQ 侧 contract/security test，全部用 MockMvc / WireMock / stub，
    禁止真实 NQ 启动交易副作用、禁止真实交易所、禁止真实凭证。
  - DH 仓库：在 dh-domain / dh-api / dh-security src/test 下实现 T12 feedback schema、
    T04/T06/T07 安全用例的 DH 侧镜像，使用既有 Fake / Disabled client。
硬约束：
  - 不新增 API/Controller/Service/Repository/DTO/migration；只加测试与测试 fixture。
  - 不接真实 HTTP / RealClient / 真实 Provider / 真实交易所 / LIVE。
  - 全部 blocksIntegration0=true 用例必须通过。
验收：
  - NQ mvn -f backend/pom.xml test 全绿（含新增 contract test）。
  - DH mvn test 全绿（含新增 contract test）。
  - forbidden side-effect checklist 全部断言为 0。
不包含：
  - rate limit / memory cap / persistent replay nonce 的真实实现（属 Integration-1）。
```
