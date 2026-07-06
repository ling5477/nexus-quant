# NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-CLOSE-REVIEW

日期：2026-07-05

## 1. Review scope

本轮只做 NQ limited dry-run runtime client close review。审查对象限定为 NQ integration worktree `E:/Project/nexus-quant-i1-dryrun` 中已实现的 isolated `com.guidinglight.nexusquant.integration.dh` package、disabled-by-default config、fake transport tests、summary-only recorder 与 docs/current 状态同步。

本轮未修改 Java 生产代码、测试代码、DH Java、NQ dev、contracts、OpenAPI、json-schema、golden_cases、fixture JSON 或 migration；未真实调用 DH；未真实 HTTP；未接 provider；未开启 LIVE；未接 Agent / LangGraph。

## 2. Source facts

```text
DH endpoint close review: CLOSED / ACCEPTED
DH endpoint: POST /api/ai/decision-dry-runs
NQ runtime client WO: CLOSED / ACCEPTED
NQ limited runtime client implementation: IMPLEMENTED / VALIDATION PASS
NQ client package: backend/nq-app/src/main/java/com/guidinglight/nexusquant/integration/dh
Transport: disabled / fake test boundary only
Runtime integration: NOT STARTED
Real DH call: NO
Real HTTP: NO
Real provider: NO
LIVE: DISABLED
contracts/OpenAPI/json-schema/golden_cases formalization: NOT DONE
migration: NONE
```

## 3. Review result

结论：**PASS / CLOSED / ACCEPTED / REVIEW_ONLY / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE**。

允许关闭：

```text
ALLOW_NQ_LIMITED_RUNTIME_CLIENT_CLOSE: YES
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_WO: YES
```

不允许直接实现：

```text
ALLOW_JOINT_RUNTIME_DRYRUN_TEST_IMPLEMENTATION_NOW: NO
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

下一步只允许进入 work-order-only：

```text
NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-WO / NOT STARTED / WORK_ORDER_ONLY / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE
```

## 4. Client boundary

审查结论：**PASS**。

- client 隔离在 `backend/nq-app/src/main/java/com/guidinglight/nexusquant/integration/dh/**`。
- production scope search 未发现 `DhDryRunRuntimeClient` 挂入 order / execution / risk / ledger / account / paper / live。
- 未进入 strategy scheduler execution path。
- 未调用 exchange adapter。
- 未调用 provider。
- 未新增 `WebClient` / `RestTemplate` / `OkHttp` / `java.net.http.HttpClient` 真实 transport。
- 默认 transport 为 disabled，测试使用 fake transport。
- 结果只进入 record-only summary，不执行交易动作。

## 5. Feature flag / config

审查结论：**PASS**。

- `application.yml` 默认 `runtime-enabled=false`、`client-enabled=false`、endpoint empty、`production-enabled=false`、`kill-switch-enabled=true`。
- `application-prod.yml` 显式保持 runtime disabled、client disabled、production disabled、kill switch enabled。
- missing endpoint URL、missing signing secret、runtime disabled、client disabled、kill switch、production profile not allowed 均 fail-closed 为 `CLIENT_DISABLED`。
- timeout 可配置。
- 不存在 fallback 放行。

## 6. Request generation

审查结论：**PASS**。

- 生成 `requestId`、`traceId`、`tenantId`、`source=NQ_DRYRUN`、UTC `Z` timestamp、unique nonce、`schemaVersion` 与 `dryRun=true`。
- timestamp 使用 `Instant` ISO-8601 / RFC3339 UTC `Z` 表达，不产生 epoch seconds 或 epoch milliseconds。
- `forbiddenCapabilities` 存在。
- `decisionContext` 仅保留 dry-run / record-only 安全字段。
- 过滤 credential、apiKey、apiSecret、passphrase、token、cookie、accountSecret 等敏感字段。
- 不生成 `BUY`、`SELL`、`PLACE_ORDER`、`CANCEL_ORDER`、`executableOrder`、executable quantity、leverage 或 order price。

## 7. Header / HMAC

审查结论：**PASS**。

- 生成 canonical `X-NQ-DH-*` headers：`X-NQ-DH-Request-Id`、`X-NQ-DH-Trace-Id`、`X-NQ-DH-Tenant-Id`、`X-NQ-DH-Source`、`X-NQ-DH-Timestamp`、`X-NQ-DH-Nonce`、`X-NQ-DH-Schema-Version`、`X-NQ-DH-Signature`。
- HMAC material 为 value-based：method、path、source、tenantId、requestId、traceId、timestamp、nonce、schemaVersion、body SHA-256。
- header name 不进入 signature material。
- tenantId / source / requestId / traceId 参与安全绑定。
- NQ HMAC material 与 DH endpoint authenticator material shape 一致。
- legacy `X-DH-NQ-*` 未作为 NQ client header 生成。
- anonymous source / source fallback 不存在。
- HMAC secret 不进入日志或 recorder summary。

## 8. Response validation

审查结论：**PASS**。

- `decisionId` 必填。
- `dryRun` 必须为 `true`。
- `schemaVersion` 必须有效并匹配。
- action whitelist 仅允许 `OBSERVE` / `NO_TRADE` / `LONG_BIAS` / `SHORT_BIAS`。
- `LONG_BIAS` / `SHORT_BIAS` 只记录为 bias-only，不映射 `BUY` / `SELL`。
- `BUY`、`SELL`、`PLACE_ORDER`、`CANCEL_ORDER` 拒绝。
- executable quantity、leverage、order price 拒绝。
- missing `decisionId`、`dryRun=false`、invalid `schemaVersion`、error envelope、parse failure、response policy violation 均 fail-closed。

## 9. Error taxonomy

审查结论：**PASS**。

NQ enum 已覆盖：

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

`UNKNOWN_ERROR`、`CLIENT_TIMEOUT`、`CLIENT_PARSE_ERROR`、`RESPONSE_POLICY_VIOLATION` 均 fail-closed；security failure 不 fallback 成功；DH error 不转换为 NQ trading signal。

## 10. Audit / recorder / logging

审查结论：**PASS**。

- recorder 记录 `requestId`、`traceId`、`tenantId`、`decisionId`（如存在）、`auditRef`（如存在）、dry-run result 与 fail-closed reason。
- recorder 为 in-memory summary-only / record-only。
- 不记录 HMAC secret、token、cookie、apiKey、apiSecret、passphrase、raw credential 或 executable order payload。
- record-only 不改变交易状态。

## 11. No-side-effect boundary

审查结论：**PASS**。

未发现：

```text
order mutation
execution call
risk mutation
ledger mutation
account mutation
paper run start
live run start
exchange adapter call
provider call
real external HTTP in tests
real DH call
```

## 12. Test coverage review

审查结论：**PASS / COVERAGE SUFFICIENT FOR CLOSE REVIEW**。

已覆盖：

- feature flag disabled -> no call。
- kill switch enabled -> no call。
- endpoint URL missing -> fail-closed。
- production profile -> disabled。
- timestamp UTC `Z`。
- nonce unique。
- HMAC generated。
- `X-NQ-DH-*` headers present。
- legacy headers absent。
- `dryRun=true`。
- `source=NQ_DRYRUN`。
- no credential fields。
- `forbiddenCapabilities` included。
- valid `OBSERVE` / `NO_TRADE` accepted record-only。
- `LONG_BIAS` / `SHORT_BIAS` accepted bias-only。
- `BUY` / `SELL` / `PLACE_ORDER` / `CANCEL_ORDER` rejected。
- executable quantity rejected。
- `dryRun=false` rejected。
- missing `decisionId` rejected。
- invalid `schemaVersion` rejected。
- error envelope fail-closed。
- no order / execution / risk / ledger / paper / live / provider / real HTTP side effect。

## 13. Validation

| Command | Result | Notes |
| --- | --- | --- |
| `git status --short` | PASS / DOCS CHANGES AFTER REVIEW | close review 后只允许 docs/current diff；Java/test/contract/golden/migration 未改。 |
| `git branch --show-current` | PASS | `nq-dh-i1-nq-runtime-client-impl`。 |
| `git diff --check` | PASS | 无 whitespace error。 |
| `git diff --stat` | REVIEWED | close review 后 diff 限于允许的 docs/current 文件。 |
| forbidden-scope diff | PASS / EMPTY | `backend/**/db/migration`、frontend、research、scripts、deploy、`.github`、contracts、golden_cases 无 diff。 |
| boundary `rg` scan | PASS / REVIEWED | 命中包含 historical docs、既有交易模块、禁止语境与本轮 integration/dh package；未发现新增真实 HTTP/client/provider/order side effect。 |
| `mvn -ntp -f backend/pom.xml test` | PASS / BUILD SUCCESS | 23 个 backend reactor module SUCCESS；`nq-app` 123 tests，0 failures，0 errors，3 skipped。 |
| `mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration0*" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS / BUILD SUCCESS | Integration0 scoped tests 17 tests，0 failures，0 errors，0 skipped。 |
| `mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration1*" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS / BUILD SUCCESS | Integration1 scoped tests 18 tests，0 failures，0 errors，0 skipped。 |
| `mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=DhDryRun*Test,NqDhIntegration1StubRecorderNoSideEffectTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS / BUILD SUCCESS | 24 tests，0 failures，0 errors，0 skipped。 |
| `mvn -ntp -f backend/pom.xml -Pquality validate` | NO_QUALITY_PROFILE | Maven 返回 BUILD SUCCESS，但明确警告 requested profile `quality` does not exist；不能记为额外 quality profile gate 通过。 |
| NQ dev read-only guard | PASS / SCOPED EMPTY | `docs/current/*NQ_DH*` 与 `docs/current/*INTEGRATION1*` staged/unstaged diff 为空；本轮未改 NQ dev。 |
| DH dev read-only guard | PASS / SCOPED EMPTY | DH Java / contracts / golden_cases scoped diff 为空；本轮未改 DH Java。 |

## 14. Known residuals before joint runtime dry-run test WO

- contracts / OpenAPI / json-schema / golden_cases 尚未 formalize，本轮不允许修改。
- `NQ_DRYRUN` 仍为 review-gated source，不进入 production allowlist。
- 下一步只能写 joint runtime dry-run test work order；不得直接实现、不得真实调用 DH、不得真实 HTTP。
- `-Pquality validate` 因 NQ quality profile 不存在，未形成额外质量门禁。

## 15. Boundary confirmation

未改 NQ Java 生产代码；未改 NQ 测试代码；未改 NQ dev；未改 DH Java；未改 contracts；未改 golden_cases；未新增 migration；未真实调用 DH；未真实 HTTP；未读取密钥或 credential；未接 provider；未接 AI / LangGraph；未开启 LIVE；未触碰 order / execution / risk / ledger / account / paper / live。
