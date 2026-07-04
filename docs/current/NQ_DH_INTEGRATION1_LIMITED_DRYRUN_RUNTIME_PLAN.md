# NQ-DH Integration-1 Limited Dry-run Runtime Plan

> 任务：`NQ-DH-I1-LIMITED-DRYRUN-RUNTIME-PLAN`
> 类型：`PLAN_ONLY + LIMITED_DRYRUN_RUNTIME_PLANNING + CROSS_REPO_RUNTIME_BOUNDARY_DESIGN + API_CONTRACT_REVIEW_PREP + NO_RUNTIME_IMPLEMENTATION + NO_LIVE`
> 日期：2026-07-04
> 仓库视角：NexusQuant dry-run worktree
> 状态：`CLOSED / ACCEPTED / PLAN_ONLY / NOT_IMPLEMENTED / NO_RUNTIME`

## 1. 结论

本文件从 NQ worktree 视角评估是否可以从 NQ-DH Integration-1 mock / test-support baseline 进入 limited dry-run runtime planning。结论是：**可以关闭本 planning 文档，并允许后续单独进入 mock baseline PR prep 与 runtime API / contract / security review；仍不允许 runtime implementation**。

本轮不写 NQ production code、不写测试代码、不新增 API / Controller / Client、不新增 migration、不改 contracts / golden_cases / fixture JSON、不真实 HTTP、不接 provider、不接 AI / LangGraph、不进入 LIVE。

Readiness decision：

```text
ALLOW_LIMITED_DRYRUN_RUNTIME_PLAN_CLOSE: YES
ALLOW_RUNTIME_IMPLEMENTATION: NO
ALLOW_RUNTIME_API_CONTRACT_REVIEW: YES
ALLOW_MOCK_BASELINE_PR_PREP: YES
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_API_CONTROLLER_CHANGE: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 2. 当前边界证据

本轮已核对：

```text
NQ worktree path: E:/Project/nexus-quant-i1-dryrun
NQ worktree branch: nq-dh-i1-dryrun
NQ worktree HEAD: f658f0ef3633e855d9b38ce5ef2ecda1020c4514
NQ worktree precheck: inherited docs/current diff present in allowed files only

NQ dev path: E:/Project/nexus-quant
NQ dev branch: dev
NQ dev HEAD: 2919f29e4d530e314d7484f2fd340bb5c8626abd
NQ dev scoped NQ-DH / Integration-1 dirty diff: none
WORKSTREAM_MIXED_BLOCKED: NO
```

NQ worktree mock baseline：

```text
IMP0 contract gap guard: CLOSED / TEST_SUPPORT_ONLY / MOCK_ONLY
IMP1 DH dry-run test-support entry: CLOSED / TEST_SUPPORT_ONLY / MOCK_ONLY
IMP2 stub recorder no-side-effect: CLOSED / VERIFY_PASS / TEST_SUPPORT_ONLY / MOCK_ONLY
IMP3 joint mock contract test: CLOSED / TEST_SUPPORT_ONLY / MOCK_ONLY
Mock close review: CLOSED / ACCEPTED / REVIEW_ONLY / NO_RUNTIME
Mock / test-support baseline: CLOSED
Limited dry-run runtime planning: ALLOWED
Runtime implementation: NOT ALLOWED
```

NQ test-support evidence：

```text
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration1/NqDhIntegration1ContractGapGuardTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration1/NqDhIntegration1StubRecorderNoSideEffectTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration1/NqDhIntegration1JointMockContractFixtureTest.java
backend/nq-app/src/test/resources/nq-dh/integration1/joint_mock_contract_fixtures.json
```

NQ production boundary：

- `backend/**/src/main/**` 未命中 `NQ_DRYRUN`、`NqDhIntegration1`、DH runtime client 或 `/dry-run` runtime token。
- `backend/**/src/main/**` 仅命中既有 credential permission probe DTO 的普通 `dryRun` 字段；该字段不属于 NQ-DH Integration-1 runtime client。
- 现有 NQ trading / risk / ledger / Paper / LIVE 代码属于 NQ 自身主权能力，不是 DH runtime integration。
- 本轮没有修改 NQ dev。

## 3. Option Evaluation

```text
Option A: continue test-support only -> SAFE BUT NOT RECOMMENDED AS NEXT
Option B: add DH dry-run endpoint with feature flag disabled -> REVIEW REQUIRED / NOT IMPLEMENTED
Option C: add NQ runtime client disabled by default -> REVIEW REQUIRED / NOT IMPLEMENTED
Option D: prepare mock baseline PR first, then separate runtime API/contract review -> RECOMMENDED
```

推荐结论：

```text
RECOMMENDED_OPTION: Option D
WHY: NQ worktree mock/test-support baseline 已可作为 PR prep 的审查对象；runtime API/client/security 授权必须单独 review，不能混入 baseline 合并。
BLOCKERS: NQ_DRYRUN production allowlist; canonical runtime error enum/schema; endpoint shape; schema alias/envelope; no-real-http client policy; rollback/kill-switch design.
REQUIRED_REVIEWS_BEFORE_IMPLEMENTATION: API/contract/security review; source allowlist review; error taxonomy/schema review; no-side-effect review; PR pre-merge review; rollback/kill-switch review.
PROHIBITED_CAPABILITIES: real HTTP; real provider; NQ mutation; order/risk/ledger/Paper/LIVE mutation; credential access; Agent/LangGraph runtime.
```

## 4. NQ Runtime Boundary

即使未来进入 limited dry-run runtime，NQ 也只能记录 DH summary，不得执行 DH output。

NQ 侧必须保持：

```text
no-order
no-cancel-order
no-risk-mutation
no-ledger-mutation
no-paper-run-start
no-live
no-credential-read
no-real-http-by-default
no-real-provider
```

只读 bias 规则：

```text
LONG_BIAS / SHORT_BIAS = read-only analytical bias
LONG_BIAS / SHORT_BIAS != BUY / SELL
LONG_BIAS / SHORT_BIAS != order side
LONG_BIAS / SHORT_BIAS != Paper Run input
LONG_BIAS / SHORT_BIAS != LIVE input
```

若未来需要 NQ runtime client：

- 必须 feature flag off by default。
- 默认 no-outbound。
- 必须有 no-real-http / no-credential / no-order / no-risk / no-ledger / no-paper / no-live tests。
- 必须单独通过 API / contract / security review。

## 5. PR / Branch Strategy

推荐并允许后续单独启动：

```text
ALLOW_MOCK_BASELINE_PR_PREP: YES
NEXT_PR_TASK: NQ-DH-I1-MOCK-BASELINE-PR-PREP
```

后续 PR prep 必须满足：

- worktree clean。
- merge / rebase latest dev。
- full backend test pass。
- Integration0 targeted tests pass。
- no real HTTP / no provider / no runtime / no LIVE。
- no order / risk / ledger / Paper / LIVE mutation。
- diff scope only expected docs/test-support unless explicitly reviewed。
- NQ dev scoped NQ-DH / Integration-1 dirty diff remains empty before merge。

NQ-DH 相关 NQ 侧工作继续只在：

```text
E:/Project/nexus-quant-i1-dryrun
branch: nq-dh-i1-dryrun
```

NQ dev 继续承载 GateO / NQ mainline；本轮不修改 NQ dev。

## 6. Runtime API / Contract Review Boundary

允许后续单独启动：

```text
ALLOW_RUNTIME_API_CONTRACT_REVIEW: YES
NEXT_REVIEW_TASK: NQ-DH-I1-RUNTIME-API-CONTRACT-REVIEW
```

该 review 只允许评审：

- DH endpoint shape / OpenAPI / auth / rate limit / status code。
- request / response envelope、canonical error enum、error schema、schema version。
- `NQ_DRYRUN` source allowlist 是否生产化。
- NQ runtime client 是否需要，以及 feature flag / no-outbound / no-real-http policy。
- kill-switch、rollback、environment isolation 和 fail-closed audit。

该 review 不得直接实现 API、Controller、Client、schema、contracts、golden_cases、runtime wiring、real HTTP 或 provider。

## 7. Rollback / Kill-switch Planning

未来 limited runtime 前必须设计：

- DH endpoint kill-switch。
- NQ runtime client kill-switch。
- `NQ_DRYRUN` source allowlist kill-switch。
- feature flag default disabled。
- environment isolation for test-support / local / CI / manual dry-run / runtime。
- no-outbound default。
- rollback to test-support-only baseline without touching trading path。

## 8. Next Concrete Action

当前推荐：

```text
NEXT_ACTION: NQ-DH-I1-MOCK-BASELINE-PR-PREP
```

PR prep 后再进入：

```text
NEXT_ACTION_AFTER_PR_PREP: NQ-DH-I1-RUNTIME-API-CONTRACT-REVIEW
```

两者均不得启动 runtime implementation。

## 9. Boundary Confirmation

```text
NQ dev modified: NO
NQ production code changed: NO
NQ test code changed: NO
contracts changed: NO
golden_cases changed: NO
fixture JSON changed: NO
API / Controller added: NO
migration added: NO
runtime started: NO
real HTTP started: NO
real provider connected: NO
credential read/output: NO
order/risk/ledger/Paper/LIVE mutation: NO
AI / LangGraph runtime started: NO
LIVE enabled: NO
```
