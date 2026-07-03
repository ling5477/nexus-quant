# NQ-DH Integration-1 Dry-run Implementation Readiness Plan

> 任务：`NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN`
> 类型：`PLAN_ONLY + DRYRUN_IMPLEMENTATION_READINESS + NQ_STUB_PLAN + DH_ENTRY_PLAN + JOINT_MOCK_VALIDATION_PLAN + SECURITY_BOUNDARY + NO_RUNTIME + NO_LIVE`
> 日期：2026-07-03
> 仓库视角：NexusQuant（NQ）
> 状态：`COMPLETED / PLAN ONLY / NOT IMPLEMENTED`

## 1. 结论

本文件合并原计划中的 `I1-P3-NQ-DRYRUN-STUB-TEST-PLAN`、`I1-P4-DH-DRYRUN-ENTRY-PLAN` 与 `I1-P5-JOINT-MOCK-VALIDATION-PLAN`，只输出 dry-run implementation readiness plan。`COMPLETED` 只表示 readiness 规划完成；`PLAN ONLY` 表示不实现；`NOT IMPLEMENTED` 表示本轮没有新增 API、Controller、dispatcher、Service、Repository、Client、Provider、migration、测试代码、fixture JSON、schema、golden case、真实 HTTP、AI / Agent runtime、LangGraph runtime 或 LIVE 能力。

```text
NQ current main line: GateO.
NQ rebase input: GateN no-real public marketdata / exchange sandbox baseline.
DH baseline: DH-STAGE4-DECISION-PIPELINE-MVP / ACCEPTED / CLOSED.
NQ-DH-I1-P0-FACTSOURCE-REBASE-CONTINUE: CLOSED / ACCEPTED / DOCS-ONLY.
NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
NQ-DH-I1-P2-CONTRACT-FIXTURES-PLAN: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
Integration-1 implementation: NOT STARTED.
Integration-1 runtime: NOT STARTED.
Runtime integration: NOT STARTED.
Real HTTP: NOT STARTED.
Real provider: NOT STARTED.
AI / Agent runtime: NOT STARTED.
LangGraph runtime: NOT STARTED.
LIVE: DISABLED.
```

下一步只允许进入：

```text
NQ-DH-I1-DRYRUN-MOCK-IMPLEMENTATION-WO / NOT STARTED
```

P4 gate-fix 已完成：

```text
NQ-DH-I1-P4-IMPLEMENTATION-GATE-REVIEW-FIX / COMPLETED / DOCS-ONLY / GATE-FIX
```

P4 fix 只关闭 gate review 的文档提交状态阻塞和 schema gap review 阻塞；本 P3 仍保持 `COMPLETED / PLAN ONLY / NOT IMPLEMENTED`，不代表 implementation started。

## 2. 范围与边界

允许范围：

```text
docs/current readiness plan
NQ dry-run stub / no-outbound / no-order test plan
DH dry-run entry review plan
joint mock validation plan
security boundary checklist
implementation gate checklist
TESTING / WORKLOG / STATUS / ROADMAP / WORK_ORDER 状态同步
```

禁止范围：

```text
backend/**
frontend/**
research/**
scripts/**
deploy/**
.github/**
contracts/** 或 golden_cases/** 修改
fixture JSON 创建
OpenAPI / API path / Controller / migration
NQ client / DH client / RealClient / real provider
真实 HTTP / WebSocket / runtime wiring
AI / Agent runtime / LangGraph runtime
NQ DB 读写
order / cancel / risk mutation / ledger mutation / Paper Run / LIVE
credential / token / cookie / API secret / passphrase 读取或输出
```

## 3. NQ dry-run stub readiness plan

P4 gate review 前，NQ 侧未来 implementation 只能被规划为 test-support / mock / stub，不得写生产 dispatcher 或真实 client。

候选位置只作为后续 review 输入，本轮不创建：

```text
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/nqdh/i1/dryrun/**
backend/nq-app/src/test/resources/nqdh/i1/dryrun/**
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/nqdh/i1/support/**
```

最低 readiness 要求：

- stub harness 必须默认 no-outbound；不得连接真实 DH、NQ runtime、交易所 host 或任何 real provider。
- `LONG_BIAS / SHORT_BIAS` 只能记录为 read-only directional bias，不能映射为 `BUY / SELL`、订单 side、下单建议或 Paper Run 输入。
- NQ 只允许记录 DH `DecisionOutput` summary、`reasonCodes`、`evidenceRefs`、计划中的 `auditRef / replayRef` 引用和 no-side-effect assertion；不得保存 raw prompt、raw provider response、signature material 或 credential。
- 失败响应只记录 error category、requestId、traceId、tenantId、source、timestamp window、payload size class 和脱敏 reason；不得重试到真实 DH，也不得触发交易补偿。
- duplicate `requestId` 必须有幂等语义：同 payload 可返回同一记录，payload 变化必须 fail-closed。
- tenant / requestId / traceId 必须在 header、body、auth binding 和 audit record 中一致；不一致必须 fail-closed。
- no-side-effect guard 必须覆盖 order state machine、place/cancel、risk mutation、ledger mutation、Paper Run start/stop、strategy state mutation、LIVE flag、private trading endpoint。

## 4. DH dry-run entry readiness plan

DH 侧是否需要新 dry-run entry 仍为 `REVIEW_REQUIRED`。现有 `POST /api/ai/feedback/nq` 是 NQ feedback ingest，不得在未做 API review 前复用为 decision dry-run request endpoint。若未来需要新 endpoint，必须先单独执行 API / OpenAPI / Controller / security review；本 P3 不新增 path。

候选 entry review 必须先回答：

- 是否复用已有 internal usecase contract，还是新增 public HTTP entry。
- 是否需要 `X-NQ-DH-Schema-Version`；若需要，必须定义 header/body `schemaVersion` 一致性和签名材料。
- `source=NQ_DRYRUN` 是否进入 allowlist；未 review 前不得写成已实现。
- response 是否需要 envelope 承载 `decisionId / confidence / traceSummary / replayRef / auditRef / dryRun`；当前 JSON Schema 不支持这些字段。

建议 fail-closed 校验顺序：

1. route / method / content-type / payload size gate；超限直接拒绝，不解析为业务请求。
2. canonical `X-NQ-DH-*` 必需 header 存在性、格式与 source allowlist。
3. timestamp 必须为 RFC3339 / ISO-8601 UTC `Z`，并校验 +/-300s window。
4. header/body/auth 的 tenantId、requestId、traceId binding。
5. tenant + source + route rate limit；超限不得消耗 nonce / replay store。
6. HMAC value-based signature 校验；signatureMaterial 至少包含 source、tenantId、requestId、traceId、timestamp、nonce 与 `sha256(body)`。
7. nonce replay mark-on-accepted-auth；认证失败不得烧掉 nonce。
8. JSON Schema / enum / required 字段校验。
9. forbidden field / forbidden capability scan。
10. DecisionOrchestrator mock/disabled provider guard、audit / trace / replay 记录。

DH 执行边界：

- DecisionOrchestrator 不得调用真实 provider、LangGraph、LLM、NQ DB、NQ runtime 或真实 HTTP。
- provider disabled、timeout、budget exceeded、no evidence、high risk、audit failure 均必须 fail-closed 到 `ABSTAIN` / `BLOCKED` / reject。
- audit / trace / replay 只写 DH-owned 脱敏摘要；不得保存 credential、raw prompt、raw provider payload、raw signature material。
- 任何 executable output、order intent、quantity、price、side、leverage、accountId、orderId、clientOrderId 必须 fail-closed。

## 5. Joint mock validation readiness plan

联合 mock validation 只能使用共享合同语义、stub、mock 和脱敏 fixture plan；不得真实启动 NQ/DH runtime 或真实 HTTP。

未来最小 case family：

| Family | 覆盖点 | 必须保持 |
| --- | --- | --- |
| valid request / valid response | request/output schema 当前字段可互认。 | no runtime, no HTTP, no side effect |
| missing / invalid signature | HMAC fail-closed。 | 不泄漏 signature material |
| timestamp skew / non-UTC | UTC `Z` 与 +/-300s window。 | 拒绝 epoch seconds / millis / offset |
| nonce replay / duplicate requestId | replay 与幂等。 | 认证失败不消耗 nonce |
| source denied | allowlist fail-closed。 | `NQ_DRYRUN` 未 review 前不得当作已实现 |
| payload too large / rate limited | size gate 与 limiter。 | 不进入 provider / decision path |
| tenant mismatch | header/body/auth binding。 | header 不覆盖权威来源 |
| forbidden field / capability | credential、order、account、quantity、side、mutation。 | fail-closed before provider |
| provider disabled / timeout / budget | DH provider guard。 | response `ABSTAIN` |
| no evidence / high risk | risk / evidence fail-closed。 | 不输出 directional bias |
| schema gap fields | `dryRun / decisionId / confidence / replayRef / auditRef`。 | 当前 schema 必须拒绝或标记 gap |
| no-side-effect scan | NQ order/risk/ledger/Paper/LIVE。 | 只记录 summary |

## 6. P4 implementation gate checklist

只有以下全部满足，才允许进入 P4 gate review；P4 也只判断是否允许后续 implementation，不直接授权 runtime 或 LIVE：

- P0 / P1 / P2 / P3 均 `COMPLETED` 或 `CLOSED`，且状态未漂移。
- NQ current main line 仍为 GateO；GateN 仅作为 no-real rebase input。
- DH Stage4 Decision Pipeline MVP 仍 `ACCEPTED / CLOSED`。
- `dryRun / decisionId / confidence / traceSummary / replayRef / auditRef / X-NQ-DH-Schema-Version / NQ_DRYRUN source` 均已有 review decision，未关闭前不得实现。
- error taxonomy 已决定 canonical name 或 alias：`AUTH_FAILED / SIGNATURE_INVALID / TIMESTAMP_SKEW / NONCE_REPLAY / SOURCE_DENIED / PAYLOAD_TOO_LARGE / RATE_LIMITED / CONTRACT_INVALID / FORBIDDEN_FIELD / TENANT_MISMATCH`。
- header canonical、timestamp UTC `Z`、HMAC value-based、nonce replay、payload 64 KiB、rate limit、tenant/request/trace binding 均有测试计划。
- NQ no-side-effect guard 覆盖 order、cancel、risk、ledger、Paper Run、strategy state、LIVE、private trading。
- DH no-runtime guard 覆盖 NQ DB、NQ mutation、real provider、RealClient、AI / LangGraph、真实 HTTP。
- `contracts/**`、`golden_cases/**`、fixture JSON、API、migration 的变更权限已被单独确认；否则继续禁止。
- rollback plan 明确：删除新增 test-support/mock/stub 与 docs 状态回滚，不影响生产 runtime。

## 7. Merged tasks

| 原任务 | P3 处置 | 后续状态 |
| --- | --- | --- |
| `I1-P3-NQ-DRYRUN-STUB-TEST-PLAN` | 已合并进本 `NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN` 的 NQ stub readiness 章节。 | `MERGED_INTO_NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN` |
| `I1-P4-DH-DRYRUN-ENTRY-PLAN` | 已合并进本 P3 的 DH entry readiness 章节。 | `MERGED_INTO_NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN` |
| `I1-P5-JOINT-MOCK-VALIDATION-PLAN` | 已合并进本 P3 的 joint mock validation readiness 章节。 | `MERGED_INTO_NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN` |
| `I1-P6-IMPLEMENTATION-GATE-REVIEW` | 重新编号为 `NQ-DH-I1-P4-IMPLEMENTATION-GATE-REVIEW-FIX` 并已完成 docs-only gate-fix。 | `COMPLETED / DOCS-ONLY / GATE-FIX` |

## 7.1 P4 gate-fix outcome（2026-07-03）

`NQ-DH-I1-P4-IMPLEMENTATION-GATE-REVIEW-FIX` 已复核双仓 P3 docs 提交状态、当前工作区边界和 P2/P3 schema gap。结论只允许进入下一步 work order，不授权 code implementation、schema change、fixture implementation、contracts/golden_cases modification、runtime、real HTTP、real provider、AI/LangGraph 或 LIVE。

Schema gap 结论：

| 项 | P4 分类 | 后续 WO 处理 |
| --- | --- | --- |
| `dryRun / decisionId / confidence / traceSummary / replayRef / auditRef / X-NQ-DH-Schema-Version` | `DOC_ONLY_ALIAS` | 只能作为文档 alias 或 future envelope planning；不得进入 required fixture、schema、API 或 code。 |
| `NQ_DRYRUN` source allowlist | `NEEDS_CONTRACT_REVIEW_BEFORE_CODE` | `source` 字段存在，但 value 未落地 allowlist；WO 只能规划 mock/test-support，不能改 runtime allowlist。 |
| canonical error code names | `NEEDS_CONTRACT_REVIEW_BEFORE_CODE` | 既有 code 可引用；gap code 保留 alias，不改 enum/code/schema。 |
| dry-run endpoint shape | `NEEDS_CONTRACT_REVIEW_BEFORE_CODE` | 不新增 API path、Controller、OpenAPI 或 migration。 |
| `DecisionAction` whitelist / `ForbiddenAction` fixed list | `EXISTS_NOW` | 后续 mock planning 必须使用当前固定枚举和五项 forbiddenActions。 |
| executable trading fields and mutations | `PROHIBITED` | 继续 fail-closed；不得写入 output、fixture 或 mock action。 |

Gate-fix readiness：

```text
ALLOW_I1_P4_IMPLEMENTATION_GATE_REVIEW_FIX_CLOSE: YES
ALLOW_I1_P4_RETRY: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_WORK_ORDER: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_FIXTURE_IMPLEMENTATION: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 8. 验证计划

P3 close validation 必须验证 docs-only scope、forbidden scope diff、状态词和 no-runtime 边界。建议命令：

```powershell
git status --short
git diff --check
git diff --stat
git diff --name-only -- backend frontend research scripts deploy .github "backend/**/db/migration"
mvn -ntp -f backend/pom.xml test
mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration0*" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## 9. Readiness decision

```text
ALLOW_I1_P3_DRYRUN_IMPLEMENTATION_READINESS_PLAN_CLOSE: YES
ALLOW_I1_P4_IMPLEMENTATION_GATE_REVIEW: YES / COMPLETED AS FIX
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_WORK_ORDER: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_FIXTURE_IMPLEMENTATION: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 10. 边界确认

本 P3 未修改生产代码；未修改测试代码；未修改 `contracts/**`；未修改 `golden_cases/**`；未创建 fixture JSON；未新增 API；未新增 migration；未新增 Controller / Client / Repository / Service；未真实 HTTP；未启动 runtime；未读取 credential；未接 real provider；未接 AI / LangGraph；未进入 Paper Run；未进入 LIVE；未让 DH 输出进入 order、risk mutation、paper/live trading 或 private trading 路径。
