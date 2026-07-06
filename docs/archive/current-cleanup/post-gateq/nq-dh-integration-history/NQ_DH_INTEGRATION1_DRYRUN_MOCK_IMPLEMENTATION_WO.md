# NQ-DH Integration-1 Dry-run Mock Implementation Work Order（NQ）

> Task: NQ-DH-I1-DRYRUN-MOCK-IMPLEMENTATION-WO
> Status: COMPLETED / WORK_ORDER_ONLY / NOT IMPLEMENTED
> Date: 2026-07-03
> Repository: NexusQuant dry-run worktree
> Source of truth: docs/current

## 1. 目标与边界

本工单只把 NQ 侧参与 Integration-1 dry-run mock implementation 的后续批次拆清楚，并同步修正预检规则：NQ dev 不要求全局干净，只要求不存在 NQ-DH / Integration-1 相关 dirty diff。

本工单不是 implementation，不授权 NQ backend / frontend / API / migration / runtime / provider 改动，不授权真实 HTTP，不授权 Paper Run，不授权 LIVE。

```text
DH dev precheck: clean
NQ dry-run worktree branch: nq-dh-i1-dryrun
NQ dry-run worktree precheck: clean
NQ dev precheck: NQ_MAINLINE_DIRTY_ALLOWED
NQ dev NQ-DH / Integration-1 dirty diff: none
WORKSTREAM_MIXED_BLOCKED: NO
```

允许继续的 NQ dev dirty scope：

```text
marketdata
API
Gate mainline docs
```

必须阻断的 NQ dev dirty scope：

```text
docs/current/*NQ_DH*
docs/current/*INTEGRATION1*
任何 NQ-DH / Integration-1 related dirty diff
```

若触发阻断，必须停止并输出：

```text
WORKSTREAM_MIXED_BLOCKED
```

## 2. 当前事实

```text
NQ-DH-I1-P0-FACTSOURCE-REBASE-CONTINUE: CLOSED / ACCEPTED / DOCS-ONLY
NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN: COMPLETED / PLAN ONLY / NOT IMPLEMENTED
NQ-DH-I1-P2-CONTRACT-FIXTURES-PLAN: COMPLETED / PLAN ONLY / NOT IMPLEMENTED
NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN: COMPLETED / PLAN ONLY / NOT IMPLEMENTED
NQ-DH-I1-P4-IMPLEMENTATION-GATE-REVIEW-FIX: COMPLETED / DOCS-ONLY / GATE-FIX
NQ-DH-I1-DRYRUN-MOCK-IMPLEMENTATION-WO: COMPLETED / WORK_ORDER_ONLY / NOT IMPLEMENTED
NQ-DH-I1-M0-CONTRACT-GAP-CLOSE-WO: COMPLETED / WORK_ORDER_ONLY / CONTRACT_GAP_CLOSED / NOT IMPLEMENTED
NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK-WO: COMPLETED / WORK_ORDER_ONLY / DH_RESULT_CONSUMED / NOT IMPLEMENTED
NQ-DH-I1-M2-NQ-DRYRUN-STUB-RECORDER-WO: COMPLETED / WORK_ORDER_ONLY / NQ_DRYRUN_STUB_RECORDER_PLANNED / NOT IMPLEMENTED
NQ-DH-I1-M3-JOINT-MOCK-FIXTURES-AND-CONTRACT-TESTS-WO: COMPLETED / WORK_ORDER_ONLY / FINAL_WO_BEFORE_IMPLEMENTATION / NOT IMPLEMENTED
Next concrete action: NQ-DH-I1-IMP0-CONTRACT-GAP-TEST-SUPPORT-IMPLEMENTATION / NOT STARTED / CONTROLLED_IMPLEMENTATION_BATCH_ALLOWED
```

Contract reality：

```text
DecisionAction whitelist: EXISTS_NOW
Fixed ForbiddenAction list: EXISTS_NOW
dryRun / decisionId / confidence / traceSummary / replayRef / auditRef: DOC_ONLY_ALIAS
X-NQ-DH-Schema-Version: DOC_ONLY_ALIAS
source=NQ_DRYRUN: NEEDS_CONTRACT_REVIEW_BEFORE_CODE
canonical error code names: NEEDS_CONTRACT_REVIEW_BEFORE_CODE
dry-run endpoint shape: NEEDS_CONTRACT_REVIEW_BEFORE_CODE
BUY / SELL / quantity / price / leverage / order / account / credential / mutation: PROHIBITED
```

## 3. NQ 全局禁止项

本工单及后续批次默认禁止：

```text
真实 NQ runtime connection
真实 HTTP
RealClient
real provider
NQ DB access
NQ mutation
order / trade / position / account / credential access
Paper Run 启动
LIVE
AI / Agent runtime
LangGraph runtime
production profile
真实 exchange API
真实密钥读取或输出
```

本工单本身还禁止：

```text
backend production code
frontend code
test code
fixture JSON
contracts/** 修改
golden_cases/** 修改
OpenAPI 修改
Controller 修改
migration
workflow
research
scripts
deploy
dependency change
```

## 4. 批次顺序

```text
M0 -> M1 -> M2 -> M3 -> M4
```

M0 未关闭前不得进入 NQ M2 implementation。M2 必须在 M0 之后单独 review；M3 必须在 M1 / M2 之后；M4 只做 close review。

## 5. M0 - Contract Gap Close WO

```text
Batch: NQ-DH-I1-M0-CONTRACT-GAP-CLOSE-WO
Type: CONTRACT_REVIEW_WO + DOCS_ONLY + NO_CODE
Status: COMPLETED / WORK_ORDER_ONLY / CONTRACT_GAP_CLOSED / NOT IMPLEMENTED
Artifact: docs/current/NQ_DH_INTEGRATION1_M0_CONTRACT_GAP_CLOSE_WO.md
```

目标：

- 关闭 `source=NQ_DRYRUN`、canonical error taxonomy、dry-run endpoint shape 的 before-code review。
- 明确 NQ stub 是否只能使用 test-support mock entry。
- 明确是否需要独立 schema / contract review；如需要，不得混入 M2 / M3。

允许修改：

```text
docs/current/**
```

禁止修改：

```text
backend/**
frontend/**
contracts/**
golden_cases/**
research/**
scripts/**
deploy/**
.github/**
pom.xml
```

安全边界：

```text
docs-only
no backend code
no API / Controller
no schema / fixture
no runtime
no real HTTP
no provider
no LIVE
```

测试要求：

```text
git diff --check
git diff --stat
targeted rg for forbidden scope
docs/current term consistency check
```

验收标准：

```text
NQ_DRYRUN source decision recorded
error taxonomy decision recorded
dry-run endpoint shape decision recorded
M2 entry condition recorded
schema / contract review need recorded
```

回滚方式：

```text
revert M0 docs/current changes
rerun git diff --check and forbidden-scope diff
```

M0 close decision：

```text
NQ_DRYRUN source allowlist: NEEDS_SECURITY_CONTRACT_CHANGE
dry-run endpoint shape: Option C / test-support mock-only, no runtime endpoint
DOC_ONLY_ALIAS fields: dryRun / decisionId / confidence / traceSummary / replayRef / auditRef / X-NQ-DH-Schema-Version
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

Review：

```text
M0 requires separate review: YES
implementation code allowed: NO
schema / contracts / golden_cases / fixture JSON allowed: NO
API / Controller allowed: NO
real HTTP / runtime / provider / LIVE allowed: NO
```

## 6. M1 - DH Dry-run Contract Entry Mock

```text
Batch: NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK
Type: DH_TEST_SUPPORT_OR_MOCK_ONLY
Status: COMPLETED / WORK_ORDER_ONLY / DH_RESULT_CONSUMED / NOT IMPLEMENTED
NQ role: consume review result only
```

目标：

- NQ 不在 M1 中改代码，只消费 DH M1 review 结果。
- 确认 DH entry mock 的安全校验顺序可以支撑后续 NQ M2 stub recorder。
- 保持 NQ 侧无 runtime、无 provider、无 HTTP、无交易副作用。

M1 的 DH 安全校验顺序必须覆盖：

```text
payload size
required canonical headers
source allowlist
timestamp UTC Z window
tenant / requestId / traceId binding
rate limit
HMAC signature
nonce replay guard
schema and forbidden action validation
mock-only orchestration
```

允许修改：

```text
NQ: docs/current/** only for consuming M1 review result
NQ backend/frontend: not allowed in M1
```

禁止修改：

```text
backend/**
frontend/**
contracts/**
golden_cases/**
backend/**/db/migration/**
API / Controller
provider
real HTTP client
```

安全边界：

```text
NQ only consumes DH M1 review output
no NQ code
no NQ runtime
no NQ order / risk / ledger / Paper Run mutation
```

测试要求：

```text
git diff --check
git diff --stat
forbidden-scope diff remains empty for NQ code paths
```

验收标准：

```text
M1 review result recorded
NQ changed files limited to docs/current if any
M2 remains blocked until M0 closes
```

回滚方式：

```text
revert M1-related docs/current notes if added
```

M1 close decision：

```text
RECOMMENDED_ENTRY_SHAPE: Option C / test-support mock-only / no runtime endpoint
NQ role: consume DH M1 result only
ALLOW_M1_WO_CLOSE: YES
ALLOW_I1_M2_NQ_DRYRUN_STUB_RECORDER_WO: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_FIXTURE_IMPLEMENTATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_API_CONTROLLER_CHANGE: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

Review：

```text
M1 requires separate review: YES
NQ implementation code allowed: NO
NQ schema / contracts / golden_cases / fixture JSON allowed: NO
NQ API / Controller allowed: NO
real HTTP / runtime / provider / LIVE allowed: NO
```

## 7. M2 - NQ Dry-run Stub Recorder

```text
Batch: NQ-DH-I1-M2-NQ-DRYRUN-STUB-RECORDER
Type: NQ_TEST_SUPPORT_OR_MOCK_ONLY
Status: BLOCKED_BY_M1
```

目标：

- 构造 NQ dry-run request。
- 接收 mock `DecisionOutput`。
- 记录 summary / trace / blocked reason。
- 证明没有 order / position / account / credential / Paper Run / mutation 副作用。

默认候选范围：

```text
backend/nq-app/src/test/**
backend/nq-app/src/test/resources/**
docs/current/**
```

禁止修改：

```text
backend/*/src/main/**
frontend/**
contracts/**
golden_cases/**
backend/**/db/migration/**
API / Controller
real HTTP client
provider
exchange adapter
```

安全边界：

```text
no real HTTP
no order
no risk mutation
no ledger mutation
no Paper Run start
no LIVE
no credential read/log/persist
LONG_BIAS / SHORT_BIAS are read-only and not BUY / SELL
failed response only records blocked summary and does not trigger trading
```

测试要求：

```text
mvn -ntp -f backend/pom.xml test
mvn -ntp -f backend/pom.xml -pl nq-app -am -Dtest=*Integration0* -Dsurefire.failIfNoSpecifiedTests=false test
targeted tests for dry-run summary, prohibited action rejection, no order side effect, no credential access
```

验收标准：

```text
NQ only records mock dry-run summary
no mutation
no order / position / account / credential read
no Paper Run
no real HTTP
no provider
```

回滚方式：

```text
revert M2 NQ worktree files
run targeted NQ test commands
```

Review：

```text
M2 requires separate review: YES
implementation code allowed: YES, only after M1 review, only NQ mock/test-support scope
schema / contracts / golden_cases allowed: NO
fixture JSON allowed: NO, unless M3 separately authorizes fixture files
API / Controller allowed: NO
real HTTP / runtime / provider / LIVE allowed: NO
```

## 8. M3 - Joint Mock Fixtures And Contract Tests

```text
Batch: NQ-DH-I1-M3-JOINT-MOCK-FIXTURES-CONTRACT-TESTS
Type: CROSS_REPO_MOCK_CONTRACT_TESTS
Status: BLOCKED_BY_M1_M2
```

目标：

- 在 M1 与 M2 review 通过后，补齐最小跨仓 mock fixtures / contract tests。
- fixtures 必须脱敏、无真实账户、无真实凭证、无真实交易数据。
- 不引入 runtime endpoint，不引入 provider。

默认候选范围：

```text
backend/nq-app/src/test/resources/**
backend/nq-app/src/test/**
docs/current/**
```

禁止修改：

```text
backend/*/src/main/**
frontend/**
OpenAPI path
Controller
migration
runtime client
provider
real HTTP
LIVE
```

安全边界：

```text
mock-only fixtures
no real URL
no credential material
no outbound call
no production endpoint
```

测试要求：

```text
mvn -ntp -f backend/pom.xml test
targeted Integration0 / NQ-DH contract tests
fixture secret scan
forbidden term scan
no outbound scan
```

验收标准：

```text
valid request / response covered
invalid signature / timestamp skew / nonce replay / source denied covered
tenant mismatch and forbidden credential/order/account/quantity/side covered
provider disabled / timeout / budget exceeded fail closed
no real URL or credential material
```

回滚方式：

```text
revert M3 fixture and test-support files
run NQ targeted validation
```

Review：

```text
M3 requires separate review: YES
implementation code allowed: YES, only test code after M1/M2 review
schema / contracts / golden_cases allowed: NO unless separately authorized by M0 follow-up
fixture JSON allowed: YES, only if M3 explicitly authorizes mock-only fixture files
API / Controller allowed: NO
real HTTP / runtime / provider / LIVE allowed: NO
```

## 9. M4 - Close Review

```text
Batch: NQ-DH-I1-M4-DRYRUN-MOCK-CLOSE-REVIEW
Type: CLOSE_REVIEW + DOCS_ONLY
Status: BLOCKED_BY_M1_M2_M3
```

目标：

- 复核 M0-M3 是否完成且没有越界。
- 复核 NQ 没有生产 runtime、API、provider、Paper Run、订单、账户、凭证、LIVE 副作用。
- 决定是否允许进入下一轮 limited dry-run runtime planning。
- 不启动 runtime planning 本身。

允许修改：

```text
docs/current/**
docs/gates/** only if closeout archive is separately authorized
```

禁止修改：

```text
production code
test code
contracts/**
golden_cases/**
API / Controller
migration
runtime
provider
```

测试要求：

```text
git diff --check
mvn -ntp -f backend/pom.xml test
targeted forbidden-scope diff scan
```

验收标准：

```text
M0-M3 evidence complete
no forbidden runtime / real HTTP / provider
no NQ mutation / order / risk / ledger / Paper Run side effect
no credential material
next planning decision explicit
```

回滚方式：

```text
revert M4 docs/current changes
retain previous accepted M0-M3 state
```

Review：

```text
M4 requires separate review: YES
implementation code allowed: NO
schema / contracts / golden_cases / fixture JSON allowed: NO
API / Controller allowed: NO
real HTTP / runtime / provider / LIVE allowed: NO
```

## 10. 验收清单

```text
ALLOW_WORK_ORDER_CLOSE: YES
ALLOW_I1_M0_CONTRACT_GAP_CLOSE_WO: YES / COMPLETED
ALLOW_I1_M1_DH_DRYRUN_CONTRACT_ENTRY_MOCK_WO: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE_THIS_TURN: NO
ALLOW_SCHEMA_CHANGE_THIS_TURN: NO
ALLOW_FIXTURE_JSON_THIS_TURN: NO
ALLOW_CONTRACTS_MODIFICATION_THIS_TURN: NO
ALLOW_GOLDEN_CASES_MODIFICATION_THIS_TURN: NO
ALLOW_API_CONTROLLER_THIS_TURN: NO
ALLOW_RUNTIME_THIS_TURN: NO
ALLOW_REAL_HTTP_THIS_TURN: NO
ALLOW_REAL_PROVIDER_THIS_TURN: NO
ALLOW_AI_AGENT_RUNTIME_THIS_TURN: NO
ALLOW_LANGGRAPH_RUNTIME_THIS_TURN: NO
ALLOW_LIVE_THIS_TURN: NO
```

## 11. 下一步

M3 已完成 work-order-only 收口；旧 M4 close review / M5 大规划路线不再继续创建文档，后续由 M3 readiness decision 与 IMP0 implementation acceptance 承接。

唯一下一步：

```text
NQ-DH-I1-IMP0-CONTRACT-GAP-TEST-SUPPORT-IMPLEMENTATION / NOT STARTED / CONTROLLED_IMPLEMENTATION_BATCH_ALLOWED
```

IMP0 只允许 test-support / mock-only source handling、canonical error mapping test-support 与 fixture schema support guard；不得在 NQ dev 主线执行，不得启动 runtime、真实 HTTP、real provider、AI / LangGraph 或 LIVE。
