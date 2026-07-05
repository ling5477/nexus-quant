# NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-CLOSE-REVIEW

日期：2026-07-05

## 1. 任务分类

```text
Task classification:
REVIEW_ONLY + JOINT_RUNTIME_DRYRUN_TEST_SECURITY_REVIEW + CROSS_REPO_CONTRACT_ALIGNMENT_REVIEW + NO_CODE_CHANGE + NO_REAL_DH_CALL + NO_REAL_HTTP + NO_REAL_PROVIDER + NO_LIVE

Repository:
NQ worktree: E:/Project/nexus-quant-i1-dryrun
DH dev: E:/Project/decision-hub
NQ dev: E:/Project/nexus-quant read-only

Result:
PASS / CLOSED / ACCEPTED / REVIEW_ONLY / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE
```

本 review 只审查已经实现的 joint runtime dry-run test 与 blocker fix 是否可关闭；不继续实现功能，不修改 Java 生产代码，不修改测试代码，不修改 contracts / OpenAPI / JSON Schema / golden_cases / migration，不真实调用 DH，不真实 HTTP，不访问 localhost 真实服务，不访问外网，不接 provider，不开启 LIVE。

## 2. 审查结论

```text
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_CLOSE: YES
ALLOW_INTEGRATION1_MOCK_RUNTIME_CLOSE_REVIEW: YES
ALLOW_REAL_DH_CALL_NOW: NO
ALLOW_REAL_HTTP_NOW: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_FORMALIZATION_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION_NOW: NO
ALLOW_DH_PRODUCTION_CODE_CHANGE_NOW: NO
ALLOW_NQ_PRODUCTION_CODE_CHANGE_NOW: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

本 close review 通过只表示 fake / in-memory / MockMvc / test-only transport 级别的 joint dry-run 测试证据与 blocker fix 已可关闭。它不表示 Integration-1 runtime started，不表示 DH integrated，不允许 real DH call / real HTTP / real provider / schema formalization / contracts modification / golden cases modification / Agent / LangGraph / LIVE。

## 3. Blocker Fix Review

结论：**PASS / FIXED / CLOSED**。

审查确认：

- `SIGNATURE_MATERIAL_SOURCE_NORMALIZATION_MISMATCH` 已修复。
- NQ signing material 与 DH HMAC verifier 均使用 wire-level canonical source value `NQ_DRYRUN`。
- `source=NQ_DRYRUN` 不被 lowercase / alias / fallback 重写。
- DH source allowlist 在验签后 exact match。
- tenant/source pair 使用 wire source 校验。
- lowercase source denied。
- alias source denied。
- signature material mismatch 返回 `SIGNATURE_INVALID`。
- `SCHEMA_VERSION_MISMATCH` 已修复。
- NQ `DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION = "1.0.0"`。
- NQ 接受 DH endpoint 实际 response `schemaVersion=1.0.0`。
- invalid schemaVersion 仍 fail-closed。
- 未修改 contracts / OpenAPI / JSON Schema / golden_cases / migration。

## 4. Joint Test Boundary Review

结论：**PASS / TEST_ONLY / FAKE_TRANSPORT_ONLY**。

审查确认：

- 测试仅使用 fake transport、in-memory helper、MockMvc / DH-style verifier 与 test-only vector。
- 未真实调用 DH。
- 未真实 HTTP。
- 未访问 localhost 真实服务。
- 未访问外网。
- 未接 provider。
- 未触碰 order / execution / risk / ledger / account / paper / live。
- 未开启 LIVE。
- 未引入 Agent / LangGraph。

## 5. Success Path Review

结论：**PASS / COVERED**。

已覆盖并通过：

- NQ 生成 signed dry-run request。
- 使用 canonical `X-NQ-DH-*` headers。
- 不输出 legacy `X-DH-NQ-*` headers。
- timestamp 为 RFC3339 UTC `Z`。
- nonce unique。
- `dryRun=true`。
- `source=NQ_DRYRUN`。
- `forbiddenCapabilities` present。
- HMAC value-based material 与 DH 一致。
- DH MockMvc / DH-style verifier 接受有效请求。
- DH 返回 readonly decision envelope。
- NQ 接受 `OBSERVE` record-only。
- NQ 接受 `NO_TRADE` record-only。
- NQ 接受 `LONG_BIAS` bias-only。
- NQ 接受 `SHORT_BIAS` bias-only。
- 不输出 `BUY / SELL / PLACE_ORDER / CANCEL_ORDER`。
- 不触发交易状态变更。

## 6. Fail-closed Review

结论：**PASS / COVERED**。

已覆盖并保持 fail-closed：

- missing signature。
- invalid signature。
- epoch seconds timestamp。
- epoch milliseconds timestamp。
- non-UTC-Z timestamp。
- timestamp out of `+/-300s`。
- replay nonce。
- source denied。
- tenant mismatch。
- `dryRun=false` request。
- forbidden `BUY / SELL / executableOrder` request material。
- payload too large。
- rate limit。
- memory cap。
- audit failure fail-closed。
- client disabled。
- kill switch enabled。
- endpoint url missing。
- timeout。
- parse failure。
- DH error envelope。
- `dryRun=false` response。
- missing decisionId。
- invalid schemaVersion。
- `BUY` response。
- `SELL` response。
- `PLACE_ORDER` response。
- executable quantity response。
- leverage response。
- order price response。

## 7. No-side-effect Review

结论：**PASS / NO SIDE EFFECT**。

审查确认：

- no real HTTP。
- no real DH call。
- no provider call。
- no order mutation。
- no execution call。
- no risk mutation。
- no ledger mutation。
- no account mutation。
- no paper run start。
- no live run start。
- no exchange adapter call。
- no credential logging。
- no secret exposure。
- no LIVE enablement。

## 8. Audit / Trace / Record Review

结论：**PASS / TRACEABLE / REDACTED**。

审查确认：

- DH 写 `auditRef`。
- DH 写 `replayRef`。
- DH 写 `traceSummary`。
- NQ 记录 `requestId`。
- NQ 记录 `traceId`。
- NQ 记录 `tenantId`。
- NQ 记录 `decisionId`。
- NQ 记录 `auditRef`。
- NQ 只记录 dry-run result。
- fail-closed reason 可追踪。
- 不记录 HMAC secret。
- 不记录 token / cookie / apiKey / apiSecret / passphrase。
- 不记录 raw credential。
- 不记录 executable order payload。

## 9. Validation

### 9.1 NQ worktree

```text
Path: E:/Project/nexus-quant-i1-dryrun
Branch: nq-dh-i1-joint-runtime-dryrun-test-impl
```

| Command | Result | Notes |
| --- | --- | --- |
| `git status --short` | PASS | close review 文档写入前为空；本轮文档写入后仅允许 docs/current diff。 |
| `git branch --show-current` | PASS | `nq-dh-i1-joint-runtime-dryrun-test-impl`。 |
| `git diff --check` | PASS | exit 0；无 whitespace error。 |
| `git diff --stat` | REVIEWED | close review 前无 tracked diff；本轮后续只允许 docs/current。 |
| forbidden-scope diff | PASS / EMPTY | `backend/**/src/main`、`backend/**/db/migration`、`frontend`、`research`、`scripts`、`deploy`、`.github`、`contracts`、`golden_cases` 无 diff。 |
| boundary `rg` scan | REVIEWED | broad scan 命中约 8899 行，为既有 docs/backend 业务词、历史/禁止语境或测试断言；结合 forbidden-scope diff 与 targeted implementation scan，未发现本轮真实 HTTP、provider、order/risk/ledger/paper/live 越界。 |
| `mvn -ntp -f backend/pom.xml test` | BUILD SUCCESS | 23/23 reactor SUCCESS；`nq-app` 129 tests / 0 failures / 0 errors / 3 skipped。 |
| `mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration0*" "-Dsurefire.failIfNoSpecifiedTests=false" test` | BUILD SUCCESS | 17 tests / 0 failures / 0 errors / 0 skipped。 |
| `mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration1*" "-Dsurefire.failIfNoSpecifiedTests=false" test` | BUILD SUCCESS | 18 tests / 0 failures / 0 errors / 0 skipped。 |
| `mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=DhDryRun*Test,NqDhIntegration1StubRecorderNoSideEffectTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | BUILD SUCCESS | 30 tests / 0 failures / 0 errors / 0 skipped。 |
| `mvn -ntp -f backend/pom.xml -Pquality validate` | PROFILE MISSING / NOT EFFECTIVE QUALITY GATE | Maven returned `BUILD SUCCESS`，但 requested profile `quality` does not exist；不得写成 NQ quality gate PASS。 |

### 9.2 DH dev

```text
Path: E:/Project/decision-hub
Branch: dev
```

| Command | Result | Notes |
| --- | --- | --- |
| `git status --short` | PASS | close review 文档写入前为空；本轮文档写入后仅允许 docs/current diff。 |
| `git diff --check` | PASS | exit 0；无 whitespace error。 |
| `git diff --stat` | REVIEWED | close review 前无 tracked diff；本轮后续只允许 docs/current。 |
| forbidden-scope diff | PASS / EMPTY | `dh-domain/src/main`、`dh-usecase/src/main`、`dh-api/src/main`、`dh-app/src/main`、`dh-infra/src/main`、contracts、golden_cases、migration 无 diff。 |
| boundary `rg` scan | REVIEWED | broad scan 命中约 1615 行，为既有 docs 禁令、测试占位、denylist、endpoint token 或本轮边界说明；未发现本轮真实 HTTP、provider、Agent/LangGraph、LIVE 或交易实现。 |
| `mvn -ntp -pl dh-api -am test` | BUILD SUCCESS | 11/11 reactor SUCCESS；`dh-api` 55 tests / 0 failures / 0 errors / 0 skipped。 |
| `mvn -ntp -pl dh-usecase -am test` | BUILD SUCCESS | 9/9 reactor SUCCESS；`dh-usecase` 179 tests / 0 failures / 0 errors / 0 skipped。 |
| `mvn -ntp -Pquality validate` | BUILD SUCCESS | 19/19 reactor SUCCESS；Checkstyle / Spotless gate 通过。 |

### 9.3 NQ dev read-only

```text
Path: E:/Project/nexus-quant
Mode: read-only
```

NQ dev 不作为 clean gate。任务输入声明该 worktree 存在非本轮 unrelated dirty；本 review 只确认未修改 NQ dev，且 NQ-DH / Integration-1 scoped unstaged 与 staged diff 为空。

## 10. Boundary Confirmation

```text
NQ Java production code changed: NO
DH Java production code changed: NO
Test code changed in this review: NO
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

## 11. Risks

- NQ `-Pquality validate` 不是有效质量门禁，因为 `quality` profile missing；后续 PR preparation 不得把该命令写成 quality PASS。
- broad `rg` 使用了 `order / token / credential / ledger` 等高噪声关键词，命中需要结合 scoped diff 与 targeted implementation scan 解释；不能把历史/否定语境误判为新增越界。
- contracts / OpenAPI / JSON Schema / golden_cases 尚未 formalize；本 close review 不授权现在修改。
- real DH call、real HTTP、provider、LIVE、Agent / LangGraph 均仍为后续独立 gate；当前不得启动。
- NQ dev unrelated dirty 不属于本 worktree 交付范围；后续 PR preparation 必须继续使用 `E:/Project/nexus-quant-i1-dryrun`。

## 12. Next Concrete Action

```text
NQ-DH-I1-INTEGRATION1-MOCK-RUNTIME-CLOSE-REVIEW
```

下一步仍是 review-only / mock-runtime close review，不允许 real DH call、real HTTP、real provider、schema/contracts/golden_cases modification、production code change、Agent / LangGraph 或 LIVE。
