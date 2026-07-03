# NQ-DH Integration-1 M0 Contract Gap Close Work Order（NQ）

> Task: NQ-DH-I1-M0-CONTRACT-GAP-CLOSE-WO
> Status: COMPLETED / WORK_ORDER_ONLY / CONTRACT_GAP_CLOSED / NOT IMPLEMENTED
> Date: 2026-07-03
> Repository: NexusQuant dry-run worktree
> Source of truth: docs/current

## 1. 目标与边界

本工单同步 NQ 侧对 Integration-1 dry-run mock 前 contract gap 的裁决结果：`NQ_DRYRUN` source allowlist、canonical error taxonomy、dry-run endpoint shape、schema alias / envelope 字段边界，以及 NQ 后续 M2 stub recorder 的入场条件。

本工单不是 NQ implementation，不授权 backend、frontend、research、scripts、deploy、`.github`、migration、contracts、golden cases、fixture JSON、API、Controller、runtime、真实 HTTP、real provider、AI / LangGraph 或 LIVE。

## 2. 前置边界确认

```text
DH repository: F:\project\decision-hub
DH branch: dev
DH HEAD: 6a806a7148712b06b8a4712ed1050da7bebcba0c
DH precheck: clean

NQ dry-run worktree: F:\worktrees\nexus-quant-i1-dryrun
NQ dry-run branch: nq-dh-i1-dryrun
NQ dry-run HEAD: 752f228abe3e4e4a7e6d223211291e10a894d5c7
NQ dry-run precheck: clean

NQ dev repository: F:\project\nexus-quant
NQ dev branch: dev
NQ dev HEAD: 294de92df73668a77b449b7da8318e220f7b8f5c
NQ dev precheck: clean
NQ dev NQ-DH / Integration-1 dirty diff: none
WORKSTREAM_MIXED_BLOCKED: NO
```

NQ dev 本轮只读；未修改、未覆盖、未回滚任何 NQ dev 文件。

## 3. NQ 侧 contract gap close 结论

```text
NQ_DRYRUN source allowlist decision: NEEDS_SECURITY_CONTRACT_CHANGE
Error taxonomy decision: PARTIAL_EXISTS_NOW + DOC_MAPPING_ONLY + REVIEW_GATED_GAPS
Dry-run endpoint shape: RECOMMENDED_SHAPE = Option C / test-support mock-only, no runtime endpoint
Schema alias / envelope fields: DOC_ONLY_ALIAS unless future schema contract review accepts wire change
NQ role before M2: consume M1 review result only
M0 close: YES
M1 work order allowed: YES
M2 implementation allowed by M0: NO
```

## 4. Source allowlist decision

`NQ_DRYRUN` 当前不属于已实现 dry-run source allowlist。NQ 侧不能把该 source 当作已经可用的 runtime authorization，也不能据此启动 DH runtime 或真实 HTTP。

| Item | Decision | NQ 侧约束 |
| --- | --- | --- |
| `source` 字段 | EXISTS_NOW | 只说明合同字段存在，不说明 `NQ_DRYRUN` 已可用。 |
| 现有 fail-closed source 语义 | EXISTS_NOW | 可作为 future security contract review 输入。 |
| `NQ_DRYRUN` source 值 | NEEDS_SECURITY_CONTRACT_CHANGE | 只能由后续安全合同 review 接受；M0 不改代码。 |
| `dryRun` 语义 | DOC_ONLY_ALIAS | 不得写入 NQ fixture required field 或 runtime DTO。 |
| 未列入 source | FAIL_CLOSED | NQ 未来只能记录 fail-closed result，不得继续交易链路。 |

NQ 侧未来若消费 `source=NQ_DRYRUN`，必须仍保持：

- 不执行 order，不撤单，不启动 Paper Run。
- 不读取 account、position、ledger、credential 或 NQ DB private state。
- 不访问交易所 private API。
- 不把 DH 输出送入 risk mutation、order mutation、ledger mutation 或 strategy state mutation。

## 5. Error taxonomy decision

| Canonical code | Decision | NQ 侧处理 |
| --- | --- | --- |
| `SIGNATURE_INVALID` | EXISTS_NOW | 可作为拒绝记录语义；不得重试交易动作。 |
| `PAYLOAD_TOO_LARGE` | EXISTS_NOW | 可作为拒绝记录语义；不得截断后继续。 |
| `RATE_LIMITED` | EXISTS_NOW | 可作为受限记录语义；不得无限重试。 |
| `FORBIDDEN_FIELD` | EXISTS_NOW | 可作为合同拒绝语义。 |
| `TENANT_MISMATCH` | EXISTS_NOW | 可作为边界拒绝语义。 |
| `PROVIDER_DISABLED` | EXISTS_NOW | 可作为 provider unavailable 语义。 |
| `PROVIDER_TIMEOUT` | EXISTS_NOW | 可作为 provider timeout 语义；不得在 NQ 侧升级为交易动作。 |
| `PROVIDER_BUDGET_EXCEEDED` | EXISTS_NOW | 可作为 provider budget fail-closed 语义。 |
| `RISK_BLOCKED` | EXISTS_NOW | 只可记录；不得绕过 NQ 风控。 |
| `TIMESTAMP_SKEW` | DOC_MAPPING_ONLY | 当前映射到 `TIMESTAMP_INVALID` / `TIMESTAMP_OUT_OF_WINDOW`。 |
| `NONCE_REPLAY` | DOC_MAPPING_ONLY | 当前映射到 `REPLAY` / `REPLAY_REJECTED`。 |
| `SOURCE_DENIED` | DOC_MAPPING_ONLY | 当前映射到 `SOURCE_NOT_ALLOWED`。 |
| `AUTH_FAILED` | NEEDS_CONTRACT_REVIEW_BEFORE_CODE | DH/NQ 命名未完全归一，不能直接写 code/fixture。 |
| `CONTRACT_INVALID` | NEEDS_CONTRACT_REVIEW_BEFORE_CODE | schema validation 命名需独立 review。 |
| `INTERNAL_FAIL_CLOSED` | NEEDS_CONTRACT_REVIEW_BEFORE_CODE | 内部失败归一需独立 review。 |

未知错误必须 fail-closed；NQ 不得输出 credential、token、signature、passphrase、raw provider response、内部异常栈、SQL、包名或路径。

## 6. Dry-run endpoint shape decision

| Option | Decision | NQ 侧说明 |
| --- | --- | --- |
| A. 新增 DH dry-run endpoint | BLOCKED | 需要 DH API / contract / security review；NQ 不先写 client。 |
| B. 复用现有 secured entry | BLOCKED | DH `POST /api/ai/feedback/nq` 是 feedback ingest，不是 decision dry-run endpoint。 |
| C. test-support / mock-only，无 runtime endpoint | RECOMMENDED_SHAPE | M1 / M2 优先按 mock-only / test-support 规划。 |
| D. 延后 API review | FUTURE_IF_NEEDED | 如未来必须 HTTP endpoint，另起双仓 review。 |

NQ M2 在 M1 之前不得启动；即使进入 M2，也只能是 stub recorder / test-support scope，不得真实 HTTP、不得 runtime integration、不得读取真实账户或 credential。

## 7. Schema alias / envelope gap decision

| Field | Decision | NQ 侧约束 |
| --- | --- | --- |
| `dryRun` | DOC_ONLY_ALIAS | 不得作为 NQ fixture required field。 |
| `decisionId` | DOC_ONLY_ALIAS；wire 需 schema review | 可作为本地记录概念，不得假定 DH wire 已返回。 |
| `confidence` | DOC_ONLY_ALIAS；wire 需 schema review | 不得作为 trading confidence 或执行权重。 |
| `traceSummary` | DOC_ONLY_ALIAS；future envelope planning | 不得作为已实现 cross-repo field。 |
| `replayRef` | DOC_ONLY_ALIAS；wire 需 schema review | 不得作为已实现 replay API reference。 |
| `auditRef` | DOC_ONLY_ALIAS；wire 需 schema review | 不得作为已实现 audit API reference。 |
| `X-NQ-DH-Schema-Version` | DOC_ONLY_ALIAS；header 需 review | 不得新增 header 签名或校验实现。 |

已存在的安全边界仍以现有 `DecisionRequest` / `DecisionOutput` schema、固定 action vocabulary 和固定 forbiddenActions 为准。

## 8. M1 / M2 入场条件

```text
ALLOW_M0_WO_CLOSE: YES
ALLOW_I1_M1_DH_DRYRUN_CONTRACT_ENTRY_MOCK_WO: YES
ALLOW_NQ_M2_STUB_RECORDER_IMPLEMENTATION_FROM_M0: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_FIXTURE_IMPLEMENTATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_BACKEND_CODE: NO
ALLOW_FRONTEND_CODE: NO
ALLOW_API_CONTROLLER: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

NQ 下一步只消费 DH M1 work order 结果；NQ M2 仍未开始，且必须等待 M1 review 关闭后另起 M2 工单。

## 9. 禁止项确认

```text
BUY / SELL / quantity / price / leverage: PROHIBITED
order / account / credential / mutation: PROHIBITED
NQ DB read/write: PROHIBITED
real provider / RealClient / real HTTP: PROHIBITED
Paper Run / LIVE / exchange private API: PROHIBITED
AI / Agent runtime / LangGraph runtime: PROHIBITED
```

## 10. 验证要求

```text
git status --short
git diff --check
git diff --stat
git diff --name-only -- backend frontend research scripts deploy .github "backend/**/db/migration"
mvn -ntp -f backend/pom.xml test
mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration0*" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

NQ dev 只做 pathspec boundary check，不能写文件。

## 11. 下一步

```text
NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK-WO / COMPLETED / WORK_ORDER_ONLY / DH_RESULT_CONSUMED / NOT IMPLEMENTED
```

M1 已完成 work-order-only planning 并由 NQ worktree 记录只读影响；不允许 NQ backend implementation。后续唯一允许动作是 `NQ-DH-I1-M2-NQ-DRYRUN-STUB-RECORDER-WO / NOT STARTED / WORK_ORDER_ONLY_ALLOWED`。
