# NQ-DH Integration-1 M3 Joint Mock Fixtures And Contract Tests Work Order（NQ）

> Task: NQ-DH-I1-M3-JOINT-MOCK-FIXTURES-AND-CONTRACT-TESTS-WO
> Status: COMPLETED / WORK_ORDER_ONLY / FINAL_WO_BEFORE_IMPLEMENTATION / NOT IMPLEMENTED
> Date: 2026-07-03
> Repository: NQ integration worktree `F:\worktrees\nexus-quant-i1-dryrun`
> DH dev: `F:\project\decision-hub`
> NQ dev: read-only only, no write
> Source: user work order attachment, M0/M1/M2 current docs, DH/NQ current facts

## 1. Boundary

本文件只定义 M3 future joint mock fixtures and contract tests 的 NQ 侧工作订单。M3 本轮不创建 fixture JSON，不写测试代码，不修改 backend/frontend/research/scripts/deploy/`.github`、schema、contracts、golden_cases、OpenAPI、Controller、Client、Repository、Service、migration、provider config 或 runtime wiring。

本轮不启动 DH runtime、NQ runtime、Integration-1 runtime、真实 HTTP、real provider、AI / Agent runtime、LangGraph runtime 或 LIVE。NQ dev `F:\project\nexus-quant` 仅只读检查，不做任何 Integration-1 正向修改。

## 2. Precheck Result

```text
DH dev path: F:\project\decision-hub
DH branch: dev
DH HEAD: c8166f2ff63933604808343db5e535bc3d9267a9
DH precheck: clean before M3 edits

NQ dry-run worktree path: F:\worktrees\nexus-quant-i1-dryrun
NQ dry-run branch: nq-dh-i1-dryrun
NQ dry-run HEAD: c651110890e79609ad1ac56f3b98955a4b4708e9
NQ dry-run precheck: clean before M3 edits

NQ dev path: F:\project\nexus-quant
NQ dev branch: dev
NQ dev HEAD: 78542b6032802553a00b61294cad2a6df052d154
NQ dev dirty classification: NQ_MAINLINE_DIRTY_ALLOWED, no NQ-DH / Integration-1 dirty diff
WORKSTREAM_MIXED_BLOCKED: NO
```

NQ dev 当前主线可存在 GateO / marketdata 等无关 dirty diff；只有 `docs/current/*NQ_DH*` 或 `docs/current/*INTEGRATION1*` dirty diff 才阻断本工单。本次预检未发现该类 diff。

## 3. Current Facts

```text
DH baseline: DH-STAGE4-DECISION-PIPELINE-MVP / ACCEPTED / CLOSED
NQ baseline for Integration-1 planning: GateN no-real public marketdata / exchange sandbox frozen baseline
NQ current mainline: GateO continues independently and is not modified by M3
Integration-1 implementation: NOT STARTED
Integration-1 runtime: NOT STARTED
Runtime integration: NOT STARTED
Real HTTP: NOT STARTED
Real provider: NOT STARTED
AI / Agent runtime: NOT STARTED
LangGraph runtime: NOT STARTED
LIVE: DISABLED
M0: COMPLETED / WORK_ORDER_ONLY
M1: COMPLETED / WORK_ORDER_ONLY
M2: COMPLETED / WORK_ORDER_ONLY
M3: COMPLETED / WORK_ORDER_ONLY / FINAL_WO_BEFORE_IMPLEMENTATION / NOT IMPLEMENTED
```

## 4. Field Boundary

`BASE_REQUEST` 表示现有 `DecisionRequest` schema 已支持的 request 字段：

```text
requestId / traceId / tenantId / source / decisionType / subject / contextSnapshot / requestedAt / schemaVersion
```

`BASE_OUTPUT` 表示现有 `DecisionOutput` schema 已支持的 response 字段：

```text
action / status / risk / policy / provider / forbiddenActions / reasonCodes / evidenceRefs / createdAt / schemaVersion
```

全局禁止字段或语义：

```text
credential / token / apiKey / apiSecret / secret / passphrase / privateKey
signature raw material / raw secret
accountId / subAccountId / orderId / clientOrderId / positionId
quantity / price / leverage / BUY / SELL
placeOrder / cancelOrder / paperRunStart / liveRunStart
mutateRisk / mutateLedger
realUrl / http:// / https://
raw provider response / raw prompt
```

测试可使用 synthetic header signature placeholder，但不得包含 HMAC secret、原始签名串、credential material 或可复用真实值。

## 5. Fixture Family Plan

以下 23 类仅为 future fixture plan。`Contract review required` 为 `YES` 时，后续 implementation 不得直接创建文件，必须先确认 source allowlist、error taxonomy、schema alias 或 fixture hygiene guard。

| No | Family | Future fixture path | Owner | Purpose | Required fields | Prohibited fields | Expected outcome | Schema support | Contract review required |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01 | valid dry-run request | `dh-domain/src/test/resources/integration1/m3/01-valid-dry-run-request.json` | DH canonical, NQ mirror | 验证 mock-only dry-run request 基础形状 | `BASE_REQUEST`, `source=NQ_DRYRUN`, UTC `requestedAt` | 全局禁止字段 | accepted by mock validator only | `SUPPORTED_NOW` for shape; `source=NQ_DRYRUN` review-gated | YES |
| 02 | valid readonly response | `dh-domain/src/test/resources/integration1/m3/02-valid-readonly-response.json` | DH canonical, NQ mirror | 验证 DH read-only output 可被 NQ recorder 读取 | `BASE_OUTPUT`, `action=NO_ACTION/ABSTAIN`, `status`, evidence refs | order/account/quantity/price/leverage/BUY/SELL | readonly consumed, no side effect | `SUPPORTED_NOW` | NO |
| 03 | invalid signature | `dh-domain/src/test/resources/integration1/m3/03-invalid-signature.json` | DH canonical | 验证签名失败 fail-closed | `BASE_REQUEST`, canonical headers, synthetic bad signature marker | raw secret, raw signature material | reject as auth/signature failure | `DOC_MAPPING_ONLY / REVIEW_REQUIRED` | YES |
| 04 | missing signature | `dh-domain/src/test/resources/integration1/m3/04-missing-signature.json` | DH canonical | 验证缺 `X-NQ-DH-Signature` fail-closed | `BASE_REQUEST`, canonical headers except signature | raw secret, credential | reject as missing canonical signature | `DOC_MAPPING_ONLY / REVIEW_REQUIRED` | YES |
| 05 | timestamp skew | `dh-domain/src/test/resources/integration1/m3/05-timestamp-skew.json` | DH canonical | 验证 RFC3339 UTC Z 超窗口失败 | `BASE_REQUEST`, `requestedAt`, header timestamp UTC Z outside window | epoch seconds/millis, numeric offset | reject as timestamp skew | `DOC_MAPPING_ONLY / REVIEW_REQUIRED` | YES |
| 06 | nonce replay | `dh-domain/src/test/resources/integration1/m3/06-nonce-replay.json` | DH canonical | 验证重复 nonce fail-closed | `BASE_REQUEST`, canonical nonce placeholder | raw secret, reusable production nonce | reject as nonce replay | `DOC_MAPPING_ONLY / REVIEW_REQUIRED` | YES |
| 07 | source denied | `dh-domain/src/test/resources/integration1/m3/07-source-denied.json` | DH canonical | 验证未 allowlist source fail-closed | `BASE_REQUEST`, denied `source` | source bypass flag | reject as source denied | `DOC_MAPPING_ONLY / REVIEW_REQUIRED` | YES |
| 08 | payload too large | `dh-domain/src/test/resources/integration1/m3/08-payload-too-large.json` | DH canonical | 验证 payload size gate | `BASE_REQUEST`, bounded oversize marker, no huge blob | real large blob, raw provider payload | reject as payload too large | `SUPPORTED_NOW` for code family; fixture size guard needed | YES |
| 09 | rate limited | `dh-domain/src/test/resources/integration1/m3/09-rate-limited.json` | DH canonical | 验证 rate-limit normalization | `BASE_REQUEST`, tenant/source/route marker | credential, real IP | reject as rate limited | `SUPPORTED_NOW` for error family | NO |
| 10 | tenant mismatch | `dh-domain/src/test/resources/integration1/m3/10-tenant-mismatch.json` | DH canonical | 验证 header/body tenant binding | `BASE_REQUEST`, body tenant, header tenant mismatch marker | tenant override flag | reject as tenant mismatch | `SUPPORTED_NOW` for error family | NO |
| 11 | forbidden credential field | `backend/nq-app/src/test/resources/nq-dh-i1/m3/11-forbidden-credential-field.json` | NQ canonical, DH mirror if needed | 验证 request builder 永不输出凭证字段 | `BASE_REQUEST`, sanitized forbidden-field marker | credential/token/apiKey/apiSecret/secret/passphrase/privateKey actual value | fail-closed before send/parse | fixture hygiene guard, no schema change | YES |
| 12 | forbidden order/account field | `backend/nq-app/src/test/resources/nq-dh-i1/m3/12-forbidden-order-account-field.json` | NQ canonical | 验证订单/账户字段禁止进入 DH request | `BASE_REQUEST`, forbidden-field marker | accountId/subAccountId/orderId/clientOrderId/positionId | fail-closed before send/parse | fixture hygiene guard, no schema change | YES |
| 13 | forbidden quantity/price/leverage/BUY/SELL | `backend/nq-app/src/test/resources/nq-dh-i1/m3/13-forbidden-trade-action-field.json` | NQ canonical | 验证交易动作语义禁止 | `BASE_REQUEST`, forbidden-field marker | quantity/price/leverage/BUY/SELL/placeOrder/cancelOrder | fail-closed, no trading signal | fixture hygiene guard, no schema change | YES |
| 14 | provider disabled | `dh-domain/src/test/resources/integration1/m3/14-provider-disabled.json` | DH canonical | 验证 provider disabled 转 readonly fail-closed | `BASE_OUTPUT`, `provider`, reason code marker | real provider URL, credential | return fail-closed output | `SUPPORTED_NOW` for output fields | NO |
| 15 | provider timeout | `dh-domain/src/test/resources/integration1/m3/15-provider-timeout.json` | DH canonical | 验证 provider timeout normalization | `BASE_OUTPUT`, provider timeout reason | real HTTP diagnostic body | return fail-closed output | `SUPPORTED_NOW` for output fields | NO |
| 16 | provider budget exceeded | `dh-domain/src/test/resources/integration1/m3/16-provider-budget-exceeded.json` | DH canonical | 验证预算耗尽时只读失败输出 | `BASE_OUTPUT`, budget exceeded reason | token/cost secret, raw prompt | return fail-closed output | `SUPPORTED_NOW` for output fields | NO |
| 17 | risk blocked | `dh-domain/src/test/resources/integration1/m3/17-risk-blocked.json` | DH canonical | 验证 risk blocked 只读表达，不执行风控修改 | `BASE_OUTPUT`, `risk`, `forbiddenActions`, reason code | mutateRisk, order/account fields | readonly risk rejection recorded | `SUPPORTED_NOW` | NO |
| 18 | no evidence fail-closed | `dh-domain/src/test/resources/integration1/m3/18-no-evidence-fail-closed.json` | DH canonical | 验证 evidence 缺失时拒绝输出 | `BASE_OUTPUT`, empty/missing evidence marker | fabricated evidence, raw prompt | fail-closed / no action | `SUPPORTED_NOW` | NO |
| 19 | internal fail-closed | `dh-domain/src/test/resources/integration1/m3/19-internal-fail-closed.json` | DH canonical | 验证内部异常规范化为 fail-closed | `BASE_OUTPUT`, internal fail reason | stack trace, SQL, secret path | fail-closed without sensitive leak | `DOC_MAPPING_ONLY / REVIEW_REQUIRED` | YES |
| 20 | long/short bias readonly | `dh-domain/src/test/resources/integration1/m3/20-long-short-bias-readonly.json` | DH canonical, NQ mirror | 验证 `LONG_BIAS/SHORT_BIAS` 只是只读 bias | `BASE_OUTPUT`, readonly bias marker, forbiddenActions | BUY/SELL/quantity/price/leverage | NQ records bias only, no order | `SUPPORTED_NOW` if encoded as reason/policy; alias review if new enum | YES |
| 21 | no real URL | `backend/nq-app/src/test/resources/nq-dh-i1/m3/21-no-real-url.json` | NQ canonical | 验证 fixture/request 不含真实 URL | `BASE_REQUEST`, no-url marker | realUrl/http:// / https:// | hygiene scan passes, no outbound target | fixture hygiene guard, no schema change | NO |
| 22 | no credential | `backend/nq-app/src/test/resources/nq-dh-i1/m3/22-no-credential.json` | NQ canonical | 验证 fixture/request 不含 credential | `BASE_REQUEST`, no-credential marker | credential/token/apiKey/apiSecret/secret/passphrase/privateKey | hygiene scan passes | fixture hygiene guard, no schema change | NO |
| 23 | no outbound | `backend/nq-app/src/test/resources/nq-dh-i1/m3/23-no-outbound.json` | NQ canonical | 验证 joint mock tests 不触发网络出口 | `BASE_REQUEST` or recorder summary marker | URL, HTTP client config, real provider id | no outbound attempts, no runtime HTTP | fixture hygiene guard, no schema change | NO |

## 6. Contract Test Batch Plan

| Batch | Owner | Future target | Purpose | Minimum assertions | Boundary |
| --- | --- | --- | --- | --- | --- |
| CT-01 DH contract validator shape tests | DH | `dh-domain/src/test/**` or test-support only | 校验 `DecisionRequest` / `DecisionOutput` 当前 schema 支持字段 | required fields, forbidden fields, fail-closed shape | no Controller/API/runtime |
| CT-02 NQ request builder shape tests | NQ worktree | `backend/nq-app/src/test/**` | 校验 NQ builder 只生成脱敏只读 request | no credential/order/account/trade fields | no HTTP client |
| CT-03 NQ recorder no-side-effect tests | NQ worktree | `backend/nq-app/src/test/**` | 校验 recorder 只记录 summary | no order/risk/ledger/paper/live mutation | no DB mutation unless test-local in-memory |
| CT-04 joint fixture parse tests | DH + NQ | future test resources | 双仓均能解析 canonical mock fixture | schema fields, UTC timestamp, trace/request binding | fixture creation requires IMP0/review |
| CT-05 forbidden field fail-closed tests | DH + NQ | test-support | 任一 forbidden field 出现即 fail-closed | credential/order/account/trade field list | no weakening validator |
| CT-06 source denied tests | DH | test-support | `NQ_DRYRUN` review 前或 denied source fail-closed | source allowlist binding | no source bypass |
| CT-07 timestamp UTC Z tests | DH + NQ | test-support | 只接受 RFC3339 UTC `Z`，拒绝 epoch/offset | ±300s window remains | no timestamp format drift |
| CT-08 HMAC signature material tests | DH + NQ | test-support | value-based signing material remains deterministic | header name not signed, no raw secret | synthetic only |
| CT-09 tenant/requestId/traceId binding tests | DH + NQ | test-support | header/body authority binding 不被覆盖 | mismatch fail-closed | no header override |
| CT-10 error taxonomy mapping tests | DH + NQ | test-support | canonical error mapping 统一到 readonly fail-closed | mapped reason/status, no stack leak | taxonomy review first |
| CT-11 no-order/no-risk/no-ledger/no-paper/no-live scan tests | NQ worktree | test-support scan | 确认 M3 tests 不触碰执行路径 | no order/risk/ledger/paper/live references | no NQ mutation |
| CT-12 no real HTTP / no outbound tests | NQ worktree | no-egress test-support | 确认 mock path 不创建 outbound target | no `http://`/`https://`, no client send | no runtime |
| CT-13 no credential logging/persistence tests | DH + NQ | test-support scan | 日志/record/output 不含 credential | redaction/hygiene checks | no secret read |
| CT-14 golden_cases compatibility smoke | DH only | `golden_cases` read-only smoke | 仅证明 DH internal deterministic baseline 不被 M3 破坏 | no NQ trading signal interpretation | no golden_cases modification in M3 |

## 7. IMP0 Transition

M3 关闭后，不继续创建 M4/M5 大规划文档。下一步进入受控 implementation batch：

```text
Next concrete action: NQ-DH-I1-IMP0-CONTRACT-GAP-TEST-SUPPORT-IMPLEMENTATION
ALLOW_M3_WO_CLOSE: YES
ALLOW_I1_IMP0_CONTRACT_GAP_TEST_SUPPORT_IMPLEMENTATION: YES
ALLOW_MORE_PLANNING_WO: NO
```

IMP0 只能覆盖：

```text
test-support / mock-only source handling
canonical error mapping test-support
fixture schema support guard
no runtime
no real HTTP
no provider
no LIVE
no Agent/LangGraph
```

IMP0 启动前必须确认 M0/M1/M2/M3 全部 CLOSED / COMPLETED。IMP0 不等于 runtime / real integration；不允许 Controller / API / schema / contracts / golden_cases 变更，除非另起独立 review 并获得授权。早期 dry-run mock WO 中的 M4 close review 路线由本 M3 readiness decision 与后续 implementation acceptance 取代，不再继续生成 M4/M5 大规划文档。

## 8. Readiness Decision

```text
ALLOW_M3_WO_CLOSE: YES
ALLOW_I1_IMP0_CONTRACT_GAP_TEST_SUPPORT_IMPLEMENTATION: YES
ALLOW_MORE_PLANNING_WO: NO
ALLOW_I1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_FIXTURE_IMPLEMENTATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_API_CONTROLLER_CHANGE: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 9. Boundary Confirmation

本轮未修改 production code、test code、contracts、golden_cases、schema、OpenAPI/API implementation、Controller、Client、Repository、Service、migration、CI、provider config、credential、runtime wiring 或 NQ dev 文件。

本轮未读取或输出 token、cookie、API key、API secret、exchange secret、private key、mnemonic、keystore password、2FA backup code 或 production `.env`。

## 10. Risks And Follow-up

- `source=NQ_DRYRUN` 仍未进入 allowlist；IMP0 只能做 test-support / mock-only source handling，不得绕过 source review。
- canonical error taxonomy 仍有 `DOC_MAPPING_ONLY / REVIEW_REQUIRED` 项；IMP0 只能补 test-support mapping，不得直接改生产错误枚举或对外 API。
- dry-run endpoint shape 仍保持 `Option C / test-support mock-only, no runtime endpoint`；任何 Controller/API 变更必须另起 review。
- schema alias 如 `decisionId / confidence / traceSummary / replayRef / auditRef / X-NQ-DH-Schema-Version / dryRun` 仍为 doc-only / future envelope planning，不得进入 required fixture。

## 11. Rollback

如需回滚本工单文档：

```powershell
git restore --worktree -- docs/current/NQ_DH_INTEGRATION1_M3_JOINT_MOCK_FIXTURES_AND_CONTRACT_TESTS_WO.md docs/current/README.md docs/current/STATUS.md docs/current/ROADMAP.md docs/current/WORK_ORDER.md docs/current/NQ_DH_INTEGRATION1_M2_NQ_DRYRUN_STUB_RECORDER_WO.md docs/current/NQ_DH_INTEGRATION1_DRYRUN_MOCK_IMPLEMENTATION_WO.md docs/current/TESTING.md docs/current/WORKLOG.md
```
