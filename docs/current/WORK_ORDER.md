# NexusQuant 当前工单

> NQ 当前主线：GateO current work line remains separate and is not overwritten by this P0 factsource rebase
> NQ-DH 集成实现线：NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-WO / CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_TEST_IMPLEMENTATION / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE
> 下一集成任务：NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-IMPLEMENTATION / NOT STARTED / TEST_ONLY / FAKE_TRANSPORT_ONLY / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE

## 1. 当前工单结论

`NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN`、`NQ-DH-I1-P2-CONTRACT-FIXTURES-PLAN` 与 `NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN` 已完成 planning-only 收口；`NQ-DH-I1-M0-CONTRACT-GAP-CLOSE-WO`、`NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK-WO`、`NQ-DH-I1-M2-NQ-DRYRUN-STUB-RECORDER-WO` 与 `NQ-DH-I1-M3-JOINT-MOCK-FIXTURES-AND-CONTRACT-TESTS-WO` 已完成 work-order-only 收口；`NQ-DH-I1-IMP0-CONTRACT-GAP-TEST-SUPPORT-IMPLEMENTATION`、`NQ-DH-I1-IMP1-DH-DRYRUN-TEST-SUPPORT-ENTRY`、`NQ-DH-I1-IMP2-NQ-STUB-RECORDER-NO-SIDE-EFFECT` 与 `NQ-DH-I1-IMP3-JOINT-MOCK-CONTRACT-TESTS` 已完成 test-support / mock-only 实现准备线，mock close review 已 `CLOSED / ACCEPTED / REVIEW_ONLY / NO_RUNTIME`。`NQ-DH-I1-LIMITED-DRYRUN-RUNTIME-PLAN`、`NQ-DH-I1-RUNTIME-API-CONTRACT-REVIEW`、`NQ-DH-I1-DH-RUNTIME-API-WO`、`NQ-DH-I1-DH-LIMITED-RUNTIME-ENDPOINT-IMPLEMENTATION` 与 `NQ-DH-I1-DH-LIMITED-RUNTIME-ENDPOINT-CLOSE-REVIEW` 已完成前置收口；DH endpoint 为 `POST /api/ai/decision-dry-runs`，scope 仅为 DH-only inbound limited dry-run。`NQ-DH-I1-NQ-RUNTIME-CLIENT-WO` 已关闭为 `CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_CLIENT_IMPLEMENTATION / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE`，并已由 `NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-IMPLEMENTATION` 消费。NQ limited client 已在 isolated integration package 中实现并通过 targeted tests；它只生成 signed dry-run request、解析 dry-run response、写 summary-only record，不覆盖 GateO/GateP 当前主线，不启动 Integration-1 runtime。`NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-CLOSE-REVIEW` 已 `PASS / CLOSED / ACCEPTED`；本轮 `NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-WO` 已关闭为 work-order-only，只冻结下一轮 joint test 的 test-only / fake-transport / no-real-http 边界。P1 canonical 计划为 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_CONTRACT_PLAN.md`；P2 canonical 计划为 `docs/current/NQ_DH_INTEGRATION1_CONTRACT_FIXTURES_PLAN.md`；P3 canonical 计划为 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_IMPLEMENTATION_READINESS_PLAN.md`；dry-run mock WO 为 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_MOCK_IMPLEMENTATION_WO.md`；M0 工单为 `docs/current/NQ_DH_INTEGRATION1_M0_CONTRACT_GAP_CLOSE_WO.md`；M1 影响记录为 `docs/current/NQ_DH_INTEGRATION1_M1_DH_DRYRUN_CONTRACT_ENTRY_MOCK_WO.md`；M2 工单为 `docs/current/NQ_DH_INTEGRATION1_M2_NQ_DRYRUN_STUB_RECORDER_WO.md`；M3 工单为 `docs/current/NQ_DH_INTEGRATION1_M3_JOINT_MOCK_FIXTURES_AND_CONTRACT_TESTS_WO.md`；limited runtime plan 为 `docs/current/NQ_DH_INTEGRATION1_LIMITED_DRYRUN_RUNTIME_PLAN.md`；NQ runtime client WO 为 `docs/current/NQ_DH_INTEGRATION1_NQ_RUNTIME_CLIENT_WO.md`；joint runtime dry-run test WO 为 `docs/current/NQ_DH_INTEGRATION1_JOINT_RUNTIME_DRYRUN_TEST_WO.md`；旧 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_PLAN.md` 仅作为 P1 初稿 / residual reference。

当前 Integration-1 dry-run 前置条件固定为：

```text
NQ GateN + DH Stage4 Decision Pipeline MVP CLOSED
```

不得写成：

```text
NQ GateN + DH GateK CLOSED
```

## 2. 当前状态

```text
NQ Integration-1 rebase input: GateN no-real public marketdata / exchange sandbox frozen baseline.
DH prerequisite: DH Stage4 Decision Pipeline MVP CLOSED / ACCEPTED.
Integration-1 dry-run plan baseline: ACCEPTED.
I1-P0 factsource rebase: CLOSED / ACCEPTED.
I1-P1 contract dry-run plan: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
I1-P2 contract fixtures plan: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
I1-P3 dry-run implementation readiness plan: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
I1-P4 implementation gate review fix: COMPLETED / DOCS-ONLY / GATE-FIX.
I1 dry-run mock implementation work order: COMPLETED / WORK_ORDER_ONLY / NOT IMPLEMENTED.
I1-M0 contract gap close work order: COMPLETED / WORK_ORDER_ONLY / CONTRACT_GAP_CLOSED / NOT IMPLEMENTED.
I1-M1 DH dry-run contract entry mock work order: COMPLETED / WORK_ORDER_ONLY / DH_RESULT_CONSUMED / NOT IMPLEMENTED.
I1-M2 NQ dry-run stub recorder work order: COMPLETED / WORK_ORDER_ONLY / NQ_DRYRUN_STUB_RECORDER_PLANNED / NOT IMPLEMENTED.
I1-M3 joint mock fixtures and contract tests work order: COMPLETED / WORK_ORDER_ONLY / FINAL_WO_BEFORE_IMPLEMENTATION / NOT IMPLEMENTED.
I1-IMP0 contract gap test-support implementation: IMPLEMENTED / TEST_SUPPORT_ONLY / MOCK_ONLY / READY_FOR_REVIEW.
I1-IMP1 DH dry-run test-support entry: IMPLEMENTED / TEST_SUPPORT_ONLY / MOCK_ONLY / READY_FOR_VALIDATION.
I1-IMP2 NQ stub recorder no-side-effect: VERIFY PASS / TEST_SUPPORT_ONLY / MOCK_ONLY / READY_FOR_IMP3_JOINT_MOCK_CONTRACT_TESTS.
I1-IMP3 joint mock contract tests: IMPLEMENTED / TEST_SUPPORT_ONLY / MOCK_ONLY / READY_FOR_MOCK_CLOSE_REVIEW.
I1 limited dry-run runtime plan: CLOSED / ACCEPTED / PLAN_ONLY / NOT_IMPLEMENTED / NO_RUNTIME.
I1 runtime API contract review: CLOSED / ACCEPTED / REVIEW_ONLY / NO_RUNTIME.
I1 DH runtime API work order: CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_RUNTIME_IMPLEMENTATION.
I1 DH limited runtime endpoint implementation: CLOSED / ACCEPTED / DH_ONLY / DEFAULT_DISABLED / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE.
I1 NQ runtime client work order: CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_CLIENT_IMPLEMENTATION / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE.
I1 NQ limited runtime client implementation: IMPLEMENTED / TARGETED_TEST_PASS / DEFAULT_DISABLED / FAKE_TRANSPORT_ONLY.
I1 NQ limited runtime client close review: PASS / CLOSED / ACCEPTED / REVIEW_ONLY / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE.
I1 joint runtime dry-run test work order: CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_TEST_IMPLEMENTATION / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE.
Next concrete action: NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-IMPLEMENTATION / NOT STARTED / TEST_ONLY / FAKE_TRANSPORT_ONLY / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE.
Integration-1 implementation: LIMITED_CLIENT_IMPLEMENTED_ONLY / RUNTIME_NOT_STARTED.
Integration-1 runtime: NOT STARTED.
Runtime integration: NOT STARTED.
Real HTTP: NOT STARTED.
Real provider: NOT STARTED.
DH integrated: NO.
AI / Agent runtime: NOT STARTED.
LangGraph runtime: NOT STARTED.
LIVE: DISABLED.
```

## 3. 允许范围

```text
docs/current/README.md
docs/current/STATUS.md
docs/current/ROADMAP.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/WORK_ORDER.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_CONTRACT_PLAN.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_PLAN_REBASEN.md
docs/current/NQ_DH_INTEGRATION1_CONTRACT_FIXTURES_PLAN.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_IMPLEMENTATION_READINESS_PLAN.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_MOCK_IMPLEMENTATION_WO.md
docs/current/NQ_DH_INTEGRATION1_M0_CONTRACT_GAP_CLOSE_WO.md
docs/current/NQ_DH_INTEGRATION1_M3_JOINT_MOCK_FIXTURES_AND_CONTRACT_TESTS_WO.md
docs/current/NQ_DH_INTEGRATION1_LIMITED_DRYRUN_RUNTIME_PLAN.md
docs/current/NQ_DH_INTEGRATION1_NQ_RUNTIME_CLIENT_WO.md
backend/nq-app/src/main/java/com/guidinglight/nexusquant/integration/dh/**
backend/nq-app/src/test/java/com/guidinglight/nexusquant/integration/dh/**
backend/nq-app/src/main/resources/application.yml
backend/nq-app/src/main/resources/application-prod.yml
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration1/NqDhIntegration1ContractGapGuardTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration1/NqDhIntegration1StubRecorderNoSideEffectTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration1/NqDhIntegration1JointMockContractFixtureTest.java
backend/nq-app/src/test/resources/nq-dh/integration1/joint_mock_contract_fixtures.json
```

本轮已实现 NQ limited dry-run runtime client，但仅限 isolated package、disabled-by-default config、fake transport tests 与 summary-only recorder；仍必须保持 default disabled、dev/test only、production disabled、kill switch fail-closed、no LIVE、no trading side effect、no provider、no credential forwarding。当前 readiness：

```text
ALLOW_LIMITED_DRYRUN_RUNTIME_PLAN_CLOSE: YES
ALLOW_NQ_RUNTIME_CLIENT_WO_CLOSE: YES
ALLOW_NQ_LIMITED_RUNTIME_CLIENT_IMPLEMENTATION_CLOSE: YES / TARGETED_TEST_PASS
ALLOW_NQ_LIMITED_RUNTIME_CLIENT_CLOSE: YES / PASS / CLOSED / ACCEPTED
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_WO: YES / CLOSED / ACCEPTED / WORK_ORDER_ONLY
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_IMPLEMENTATION: NO
ALLOW_REAL_DH_CALL_NOW: NO
ALLOW_REAL_HTTP_NOW: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_FORMALIZATION_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION_NOW: NO
ALLOW_DH_CODE_CHANGE_NOW: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 4. 禁止范围

```text
backend/**/src/main/**（除本轮已授权的 `com/guidinglight/nexusquant/integration/dh/**` 与 disabled-by-default application 配置）
backend/**/db/migration/**
backend/**/src/test/**（除已授权的 integration1 / integration/dh tests）
frontend/**
research/**
scripts/**
deploy/**
.github/**
contracts/**
golden_cases/**
任何 migration
任何 API / Controller
任何真实 HTTP / WebSocket
任何真实 DH runtime 或 NQ runtime integration
任何 RealClient / real provider / real permission probe
任何 credential / token / cookie / API secret / passphrase 读取或输出
任何 LIVE、下单、撤单、转账、提现、订单状态、风控、账本或 Paper Run mutation
```

## 5. Readiness decision

```text
ALLOW_I1_P0_CLOSE: YES
ALLOW_I1_P1_CONTRACT_PLAN: YES / COMPLETED / PLAN ONLY
ALLOW_I1_P2_CONTRACT_FIXTURES_PLAN_CLOSE: YES
ALLOW_I1_P3_DRYRUN_IMPLEMENTATION_READINESS_PLAN_CLOSE: YES
ALLOW_I1_P4_IMPLEMENTATION_GATE_REVIEW_FIX_CLOSE: YES
ALLOW_I1_P4_RETRY: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_WORK_ORDER: YES
ALLOW_I1_M0_CONTRACT_GAP_CLOSE_WO: YES / COMPLETED
ALLOW_I1_M1_DH_DRYRUN_CONTRACT_ENTRY_MOCK_WO: YES / COMPLETED
ALLOW_I1_M2_NQ_DRYRUN_STUB_RECORDER_WO: YES / COMPLETED / WORK_ORDER_ONLY
ALLOW_M3_WO_CLOSE: YES
ALLOW_I1_IMP0_CONTRACT_GAP_TEST_SUPPORT_IMPLEMENTATION: YES / COMPLETED / READY_FOR_REVIEW
ALLOW_IMP0_CLOSE: YES
ALLOW_I1_IMP1_DH_DRYRUN_TEST_SUPPORT_ENTRY: YES
ALLOW_IMP2_NQ_STUB_RECORDER_NO_SIDE_EFFECT: YES / VERIFY_PASS
ALLOW_I1_IMP3_JOINT_MOCK_CONTRACT_TESTS: YES / NEXT_ONLY
ALLOW_LIMITED_DRYRUN_RUNTIME_PLAN_CLOSE: YES
ALLOW_NQ_RUNTIME_CLIENT_WO_CLOSE: YES
ALLOW_NQ_LIMITED_RUNTIME_CLIENT_IMPLEMENTATION_CLOSE: YES / TARGETED_TEST_PASS
ALLOW_NQ_LIMITED_RUNTIME_CLIENT_CLOSE: YES / PASS / CLOSED / ACCEPTED
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_WO: YES / WORK_ORDER_ONLY
ALLOW_MORE_PLANNING_WO: NO
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE: NO
ALLOW_P1_IMPLEMENTATION_FROM_THIS_TASK: NO
ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION: LIMITED_CLIENT_ONLY / RUNTIME_NOT_STARTED
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_FORMALIZATION_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION_NOW: NO
ALLOW_DH_CODE_CHANGE_NOW: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 6. 下一步

```text
NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-IMPLEMENTATION / NOT STARTED / TEST_ONLY / FAKE_TRANSPORT_ONLY / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE
```

`NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-WO` 已关闭为 work-order-only 文档。下一步只能在用户单独授权后进入 `NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-IMPLEMENTATION`；该 implementation 仍必须 test-only / fake-transport / no-real-http，不得真实调用 DH，不得真实 HTTP，不得接 provider，不得修改 schema/contracts/golden_cases/API/Controller/migration，也不得触碰 order / execution / risk / ledger / account / paper / live 生产链路。
