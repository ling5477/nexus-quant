# NQ-DH-I1-INTEGRATION1-MOCK-RUNTIME-CLOSE-REVIEW

日期：2026-07-05

## 1. 任务分类

```text
Task classification:
REVIEW_ONLY + MILESTONE_CLOSE_REVIEW + WORKSTREAM_DISCIPLINE_RESET + CROSS_REPO_SECURITY_BOUNDARY_REVIEW + NO_CODE_CHANGE + NO_REAL_DH_CALL + NO_REAL_HTTP + NO_REAL_PROVIDER + NO_LIVE

Repository:
NQ worktree: E:/Project/nexus-quant-i1-dryrun
DH dev: E:/Project/decision-hub
NQ dev: E:/Project/nexus-quant read-only

Result:
PASS / CLOSED / ACCEPTED / REVIEW_ONLY / MOCK_RUNTIME_MILESTONE_CLOSED / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE
```

本 review 只关闭 Integration-1 mock runtime / test-only 里程碑，并复位后续工作纪律。不继续实现功能，不新增 WO，不修改 Java 生产代码，不修改测试代码，不修改 contracts / OpenAPI / JSON Schema / golden_cases / migration，不真实调用 DH，不真实 HTTP，不访问 localhost 真实服务，不访问外网，不接 provider，不接 Agent / LangGraph，不开启 LIVE。

## 2. Discipline Review

结论：**PASS / DISCIPLINE_RESET**。

```text
commit hygiene: PASS
branch hygiene: PASS / RECORDED
docs/current hygiene: PASS
review cadence reset: PASS
NQ dev isolation: PASS / READ_ONLY_SCOPED_EMPTY
next task boundary: PASS / PR_PREP_ONLY
```

- NQ worktree `git status --short` 在本轮写入前无输出；当前分支为 `nq-dh-i1-joint-runtime-dryrun-test-impl`。
- DH dev `git status --short` 在本轮写入前无输出；当前分支为 `dev`。
- NQ dev 本轮只读；`git status --short` 显示 unrelated backend untracked 文件；`docs/current/*NQ_DH*` 与 `docs/current/*INTEGRATION1*` unstaged / staged scoped diff 为空；NQ dev 不作为全仓无改动 gate。
- close review commit 边界清晰：上一轮 joint close review 为 docs-only commit；blocker fix 为独立 test / code alignment commit。
- review cadence 复位：下一步只能做 PR preparation / milestone PR review，不新增 implementation WO，不继续每个小 batch 追加 standalone review。

## 3. Phase Chain Completeness

结论：**PASS / COMPLETED_AND_SUBMITTED**。

已确认以下阶段均已完成并提交：

```text
Runtime API contract review: CLOSED / ACCEPTED / REVIEW_ONLY / committed
DH runtime API WO: CLOSED / ACCEPTED / WORK_ORDER_ONLY / committed
DH limited endpoint implementation: IMPLEMENTED / CLOSED_BY_REVIEW / committed
DH endpoint close review: CLOSED / ACCEPTED / REVIEW_ONLY / committed
NQ runtime client WO: CLOSED / ACCEPTED / WORK_ORDER_ONLY / committed
NQ limited runtime client implementation: IMPLEMENTED / TARGETED_TEST_PASS / committed
NQ client close review: PASS / CLOSED / ACCEPTED / committed
joint runtime dry-run test WO: CLOSED / ACCEPTED / WORK_ORDER_ONLY / committed
joint runtime dry-run test implementation: IMPLEMENTED / FULL_VALIDATION_PASS / TEST_ONLY / committed
blocker fix: IMPLEMENTED / FULL_VALIDATION_PASS / committed
joint runtime dry-run test close review: PASS / CLOSED / ACCEPTED / committed
```

未发现未提交阶段、未提交 close-review 文档、或本轮新增 Java / test diff。

## 4. Mock Runtime Scope Review

结论：**PASS / TEST_ONLY / FAKE_TRANSPORT_ONLY**。

当前阶段只能解释为：

```text
test-only / fake transport / MockMvc / in-memory validation
```

不得解释为：

```text
real runtime integration started
DH integrated
NQ integrated
LIVE ready
production ready
real HTTP ready
```

实际边界：

- NQ limited client 默认关闭，production disabled，kill switch 默认阻断，测试只使用 fake / disabled transport。
- DH endpoint `POST /api/ai/decision-dry-runs` 是 DH-only inbound limited dry-run endpoint；默认关闭，dev/test 可显式启用，production disabled / kill switch fail-closed。
- Joint runtime dry-run test 证据只覆盖 fake transport / in-memory / MockMvc / DH-style verifier / record-only。
- Runtime integration 仍 `NOT STARTED`；DH integrated 仍 `NO`；LIVE 仍 `DISABLED`。

## 5. Security Boundary Review

结论：**PASS / FAIL_CLOSED / NO_SIDE_EFFECT**。

已确认：

- HMAC source wire value 已对齐；NQ `DhDryRunSigning` 与 DH `HmacNqDryRunAuthenticator` 均使用 wire-level canonical `NQ_DRYRUN` 参与 signature material。
- `source=NQ_DRYRUN` 不被 lowercase / alias / fallback 重写。
- source allowlist 在验签后 exact match。
- tenant/source pair 在验签后 exact match。
- lowercase source denied。
- alias source denied。
- signature material mismatch 返回 `SIGNATURE_INVALID`。
- schemaVersion 已对齐：NQ `DEFAULT_SCHEMA_VERSION = 1.0.0`，与 DH endpoint 实际 response `schemaVersion=1.0.0` 对齐。
- invalid schemaVersion fail-closed。
- `BUY / SELL / PLACE_ORDER / CANCEL_ORDER` fail-closed。
- `LONG_BIAS / SHORT_BIAS` 仅 bias-only，不映射为 `BUY / SELL`。
- no real HTTP。
- no real DH call。
- no provider。
- no LIVE。
- no trading side effect。

## 6. Test Evidence Review

结论：**PASS / PRIOR_EVIDENCE_ACCEPTED / MAVEN_NOT_RERUN_THIS_REVIEW**。

本 close review 沿用上一轮已记录并接受的验证证据，本轮未重跑 Maven。

NQ 已记录证据：

- backend full test：`BUILD SUCCESS`。
- Integration0 scoped：`BUILD SUCCESS`。
- Integration1 scoped：`BUILD SUCCESS`。
- dry-run targeted：`BUILD SUCCESS`。
- NQ `-Pquality validate`：`PROFILE MISSING / NOT EFFECTIVE QUALITY GATE`；不得写为 quality gate PASS。

DH 已记录证据：

- `mvn -ntp -pl dh-api -am test`：`BUILD SUCCESS`。
- `mvn -ntp -pl dh-usecase -am test`：`BUILD SUCCESS`。
- `mvn -ntp -Pquality validate`：`BUILD SUCCESS`。

## 7. Documentation Hygiene Review

结论：**PASS / CURRENT_DOCS_SYNCED**。

本轮同步 current docs 口径：

- `NQ-DH-I1-INTEGRATION1-MOCK-RUNTIME-CLOSE-REVIEW` 关闭为 `PASS / CLOSED / ACCEPTED / REVIEW_ONLY`。
- 下一步只允许 `NQ-DH-I1-MOCK-RUNTIME-PR-PREP`。
- 未把 Runtime integration 写成 started。
- 未把 DH integrated 写成 YES。
- 未把 LIVE 写成 enabled。
- 未把 real HTTP 写成 allowed。
- 未把 provider 写成 enabled。
- 未把 schema formalization 写成 completed。
- 未把 contracts / golden_cases 写成 modified。
- 未把 NQ quality profile missing 写成 quality PASS。
- 未把 NQ dev 作为全仓无改动 gate；只记录 scoped diff 状态。

## 8. Readiness Decision

```text
ALLOW_INTEGRATION1_MOCK_RUNTIME_CLOSE: YES
ALLOW_MOCK_RUNTIME_PR_PREP: YES
ALLOW_REAL_DH_CALL_NOW: NO
ALLOW_REAL_HTTP_NOW: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_FORMALIZATION_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION_NOW: NO
ALLOW_DH_PRODUCTION_CODE_CHANGE_NOW: NO
ALLOW_NQ_PRODUCTION_CODE_CHANGE_NOW: NO
ALLOW_DH_TEST_CODE_CHANGE_NOW: NO
ALLOW_NQ_TEST_CODE_CHANGE_NOW: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 9. Boundary Confirmation

```text
NQ Java production code changed: NO
DH Java production code changed: NO
NQ test code changed: NO
DH test code changed: NO
NQ dev changed: NO
contracts/OpenAPI/json-schema changed: NO
golden_cases changed: NO
migration changed: NO
real DH call: NO
real HTTP: NO
localhost real service access: NO
external network access: NO
real provider: NO
credential read: NO
secret output: NO
Agent / LangGraph: NO
LIVE enabled: NO
order/execution/risk/ledger/account/paper/live touched: NO
LONG_BIAS / SHORT_BIAS mapped to BUY / SELL: NO
Runtime integration started: NO
DH integrated: NO
```

## 10. Risks

- NQ `quality` profile missing，PR preparation 不得把 `-Pquality validate` 写成 NQ quality gate PASS。
- contracts / OpenAPI / JSON Schema / golden_cases 仍未 formalize；本 milestone close 不授权修改。
- real DH call、real HTTP、provider、LIVE、Agent / LangGraph 均仍需后续独立 gate；当前不得启动。
- broad boundary `rg` 会命中历史禁令、测试负向断言和现有业务词；必须结合 scoped diff 与 current docs 解释。
- NQ worktree 分支当前为 `nq-dh-i1-joint-runtime-dryrun-test-impl`；PR prep 需要继续确认目标分支与 merge boundary。

## 11. Next Concrete Action

```text
NQ-DH-I1-MOCK-RUNTIME-PR-PREP
```

该下一步只能准备 PR / merge boundary，不得实现新功能，不得真实调用 DH，不得真实 HTTP，不得接 provider，不得修改 schema / contracts / golden_cases，不得进入 Agent / LangGraph 或 LIVE。
