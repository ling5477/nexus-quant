# NQ-DH Integration-1 Joint Runtime Dry-run Test Work Order

> 任务：`NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-WO`
> 类型：`WORK_ORDER_ONLY + JOINT_RUNTIME_DRYRUN_TEST_PLAN + CROSS_REPO_TEST_BOUNDARY_FREEZE + NO_TEST_IMPLEMENTATION + NO_REAL_DH_CALL + NO_REAL_HTTP + NO_REAL_PROVIDER + NO_LIVE`
> 日期：2026-07-05
> 仓库视角：NexusQuant integration worktree
> 状态：`CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_TEST_IMPLEMENTATION / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE`

## 1. 目标

本 work order 只冻结下一轮 joint runtime dry-run test implementation 的测试边界、测试方式、允许文件范围、禁止项、测试矩阵、验收标准和回滚要求。

本轮不实现测试，不修改 Java 生产代码，不修改测试代码，不新增 fixture，不修改 contracts / OpenAPI / JSON Schema / golden_cases，不真实调用 DH，不真实 HTTP，不接 provider，不接 Agent / LangGraph，不开启 LIVE。

当前事实：

```text
DH limited dry-run endpoint: CLOSED / ACCEPTED
DH endpoint: POST /api/ai/decision-dry-runs
NQ limited dry-run client: CLOSED / ACCEPTED
NQ client package: isolated integration/dh
NQ client default: disabled
NQ production: disabled
NQ kill switch: blocks by default
real DH call: NO
real HTTP: NO
real provider: NO
contracts/OpenAPI/json-schema/golden_cases formalization: NOT DONE
Runtime integration: NOT STARTED
DH integrated: NO
LIVE: DISABLED
Agent / LangGraph: NO
LONG_BIAS / SHORT_BIAS: bias only, never BUY / SELL
NQ behavior: record only, never execute
```

## 2. Joint Test 目标

下一轮 implementation 只允许验证：

```text
NQ limited dry-run client
  -> fake / in-memory / MockMvc / test-only transport
  -> DH POST /api/ai/decision-dry-runs
  -> DH readonly decision envelope
  -> NQ response validation
  -> NQ dry-run record-only result
```

不得验证：

```text
真实网络
真实 DH 地址
真实 provider
真实交易
真实 NQ runtime integration
LIVE
```

## 3. 测试方式

下一轮只能使用以下方式之一，或按同等 no-real 语义组合使用：

```text
fake transport
in-memory adapter
MockMvc
test-only request/response vector
isolated test support module
```

禁止：

```text
WebClient real outbound
RestTemplate real outbound
OkHttp real outbound
java.net.http.HttpClient real outbound
WireMock 绑定真实网络端口
访问真实 DH 服务
访问 localhost 真实运行服务
访问外网
```

## 4. 下一轮允许文件范围

NQ 下一轮 implementation 可允许修改：

```text
backend/nq-app/src/test/java/com/guidinglight/nexusquant/integration/dh/**
backend/nq-app/src/test/resources/**（仅限 Integration-1 test-only resources）
backend/nq-app/src/main/java/com/guidinglight/nexusquant/integration/dh/**（仅限为测试暴露必要 package-private seam，不得改变生产行为）
docs/current/**
```

DH 下一轮 implementation 可允许修改：

```text
dh-api/src/test/**
dh-usecase/src/test/**
dh-security/src/test/**
dh-app/src/test/**
dh-app/src/test/resources/**（仅限 Integration-1 test-only resources）
docs/current/**
```

仍然禁止：

```text
生产 runtime wiring
contracts/OpenAPI/json-schema
golden_cases
migration
NQ order / execution / risk / ledger / paper / live
DH provider / Agent / LangGraph
```

## 5. 成功测试矩阵

下一轮必须覆盖：

1. NQ 生成有效 signed dry-run request。
2. header 使用 canonical `X-NQ-DH-*`。
3. timestamp 为 RFC3339 UTC `Z`。
4. nonce 唯一。
5. `dryRun=true`。
6. `source=NQ_DRYRUN`。
7. `forbiddenCapabilities` 存在。
8. DH 接受有效请求。
9. DH 返回 readonly decision envelope。
10. NQ 接受 `OBSERVE`，record-only。
11. NQ 接受 `NO_TRADE`，record-only。
12. NQ 接受 `LONG_BIAS`，bias-only。
13. NQ 接受 `SHORT_BIAS`，bias-only。
14. 全链路不输出 `BUY / SELL / PLACE_ORDER / CANCEL_ORDER`。
15. 全链路不触发交易状态变更。

## 6. 失败测试矩阵

DH side fail-closed 必须覆盖：

```text
missing signature
invalid signature
epoch seconds timestamp
epoch milliseconds timestamp
non-UTC-Z timestamp
timestamp out of ±300s
replay nonce
source denied
tenant mismatch
dryRun=false
forbidden BUY / SELL / executableOrder
payload too large
rate limit
memory cap
audit failure fail-closed
```

NQ side fail-closed 必须覆盖：

```text
client disabled
kill switch enabled
endpoint url missing
timeout
parse failure
DH error envelope
dryRun=false response
missing decisionId
invalid schemaVersion
BUY response
SELL response
PLACE_ORDER response
executable quantity response
leverage response
order price response
```

## 7. No-side-effect 测试矩阵

下一轮必须证明：

```text
no real HTTP
no real DH call
no provider call
no order mutation
no execution call
no risk mutation
no ledger mutation
no account mutation
no paper run start
no live run start
no exchange adapter call
no credential logging
no secret exposure
no LIVE enablement
```

## 8. Audit / trace / record 验证

下一轮必须验证：

```text
DH 写 auditRef
DH 写 replayRef
DH 写 traceSummary
NQ 记录 requestId / traceId / tenantId / decisionId / auditRef
NQ 只记录 dry-run result
fail-closed reason 可追踪
不记录 HMAC secret
不记录 token / cookie / apiKey / apiSecret / passphrase
不记录 raw credential
不记录 executable order payload
```

## 9. Error taxonomy 对齐

下一轮必须覆盖：

```text
SIGNATURE_INVALID
TIMESTAMP_INVALID
TIMESTAMP_OUT_OF_WINDOW
NONCE_REPLAY
TENANT_MISMATCH
SOURCE_DENIED
PAYLOAD_TOO_LARGE
RATE_LIMITED
MEMORY_LIMIT_EXCEEDED
POLICY_DENIED
PROVIDER_DISABLED
PROVIDER_TIMEOUT
BUDGET_EXCEEDED
UNKNOWN_ERROR
CLIENT_DISABLED
CLIENT_TIMEOUT
CLIENT_PARSE_ERROR
RESPONSE_POLICY_VIOLATION
```

规则：

```text
UNKNOWN_ERROR fail-closed
security failure 不得 fallback 成功
DH error 不得转为 NQ trading signal
NQ client failure 不得触发交易行为
```

## 10. 验收门槛

下一轮 implementation 完成后，必须满足：

```text
DH targeted tests PASS
NQ targeted tests PASS
NQ backend full test PASS，除非明确 timeout，并如实说明
DH quality validate PASS
NQ quality profile 如不存在，必须说明 profile missing，不得写 quality PASS
forbidden-scope diff EMPTY
boundary rg reviewed
无真实 HTTP
无真实 DH call
无 contracts/golden_cases/migration 修改
无 LIVE
无 Agent / LangGraph
无交易副作用
```

## 11. Validation commands

NQ worktree：

```powershell
Set-Location -LiteralPath 'E:\Project\nexus-quant-i1-dryrun'

git status --short
git branch --show-current
git diff --check
git diff --stat

git diff --name-only -- `
  "backend/**/db/migration" `
  frontend `
  research `
  scripts `
  deploy `
  .github `
  contracts `
  golden_cases

rg -n "NQ_DRYRUN|decision-dry-runs|WebClient|RestTemplate|OkHttp|HttpClient|RealClient|apiKey|apiSecret|passphrase|credential|token|cookie|BUY|SELL|PLACE_ORDER|CANCEL_ORDER|placeOrder|cancelOrder|paperRunStart|liveRunStart|mutateRisk|mutateLedger|ledger|execution|order" `
  docs/current backend

mvn -ntp -f backend/pom.xml test
mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration0*" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration1*" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=DhDryRun*Test,NqDhIntegration1StubRecorderNoSideEffectTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -ntp -f backend/pom.xml -Pquality validate
```

DH dev：

```powershell
Set-Location -LiteralPath 'E:\Project\decision-hub'

git status --short
git diff --check
git diff --stat

git diff --name-only -- `
  contracts `
  golden_cases `
  "dh-*/src/main/resources/db/migration"

rg -n "decision-dry-runs|NQ_DRYRUN|BUY|SELL|PLACE_ORDER|CANCEL_ORDER|LangGraph|AutoGen|CrewAI|WebClient|RestTemplate|OkHttp|HttpClient|apiKey|apiSecret|passphrase|credential|token|cookie|live|placeOrder|cancelOrder" `
  dh-* docs/current contracts golden_cases

mvn -ntp -pl dh-api -am test
mvn -ntp -pl dh-usecase -am test
mvn -ntp -Pquality validate
```

NQ dev 只读：

```powershell
Set-Location -LiteralPath 'E:\Project\nexus-quant'

git status --short
git branch --show-current
git diff --stat
git diff --name-only -- docs/current/*NQ_DH* docs/current/*INTEGRATION1*
git diff --name-only --cached -- docs/current/*NQ_DH* docs/current/*INTEGRATION1*
```

## 12. Readiness decision

```text
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_WO_CLOSE: YES
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_IMPLEMENTATION: NO
ALLOW_REAL_DH_CALL_NOW: NO
ALLOW_REAL_HTTP_NOW: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_FORMALIZATION_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION_NOW: NO
ALLOW_DH_CODE_CHANGE_NOW: NO
ALLOW_NQ_PRODUCTION_CODE_CHANGE_NOW: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 13. 回滚要求

本轮是 docs-only work order。回滚方式：

```text
删除 docs/current/NQ_DH_INTEGRATION1_JOINT_RUNTIME_DRYRUN_TEST_WO.md
还原本轮修改的 docs/current/README.md
还原本轮修改的 docs/current/STATUS.md
还原本轮修改的 docs/current/ROADMAP.md
还原本轮修改的 docs/current/WORK_ORDER.md
还原本轮修改的 docs/current/API.md
还原本轮追加的 docs/current/TESTING.md 条目
还原本轮追加的 docs/current/WORKLOG.md 条目
```

回滚不涉及 Java、测试、contracts、golden_cases、migration、runtime、provider、credential 或 LIVE 副作用。

## 14. 下一步

只有在本 WO 关闭后，才允许用户单独授权下一轮：

```text
NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-IMPLEMENTATION
```

该下一轮仍必须是 `test-only / fake-transport / no-real-http` 联合验证，不得真实联调。
