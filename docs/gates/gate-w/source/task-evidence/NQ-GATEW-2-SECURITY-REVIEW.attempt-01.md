# NQ-GATEW-2 Security Review Attempt 01

## Review Decision

```text
PASS / SECURITY_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED
```

该授权只批准按本文冻结基线编写 GateW-2 private read-only implementation；implementation 尚未开始。Security review baseline 必须先独立提交并取得 exact-HEAD CI green。

## Baseline and Authority Before

- branch：`dev`；起始 worktree/staged clean。
- HEAD/origin：`31c8171df26bc1eb9f93da19cf0576c0ac48116b`。
- exact-HEAD CI：`NQ CI Baseline` run `29219687588 / completed / success`。
- authority before：`accepted_batch=GateW-PLAN / ACCEPTED|CI_GREEN`；`work_batch=GateW-1 / IMPLEMENTED|SELF_REVIEWED / UNCOMMITTED / NOT_RUN`。这是文档滞后，不是代码或 CI 冲突。
- canonical action：`scripts/docs/governance-workflow-contract.json` 与 next-action regression 支持 `NOT_STARTED -> *-IMPLEMENTATION`，因此采用 `NQ-GATEW-2-IMPLEMENTATION`，未修改治理 contract。

## Scope and Inspected Facts

已阅读 `AGENTS.md`、`CLAUDE.md`、根 `README.md`、GateW/current governance 文档和 contract；只读检查 `nq-adapter-api`、`nq-adapter-okx`、`nq-core`、`nq-infra`、`nq-app`、`nq-risk` 的 credential、probe、OKX、profile 与测试路径。仓库不存在 `backend/nq-account` 独立 module，账户实现实际位于 core/infra/app；未以目录名推断能力。

关键事实：

- GateW-1 private allowlist 为空并 default-deny；mutating/funds movement 永久拒绝。
- V12 partial unique index 约束 active `(exchange_account_id, credential_type)`；repository 仍须显式检查 0/1/>1，不能用 `ORDER BY ... LIMIT 1` 模糊选择。
- 现有 JDBC material path 在 SQL 中解密并返回 plaintext JSON `String`；现有 core request 也携带该 `String`。
- 现有 `CredentialPermissionProbeService` 在事务中调用 port 并写 probe metadata；不适合本轮非持久化方案。
- 现有 `OkxHttpClient` 暴露通用 method/path、无响应上限、持有 credential String；现有 `OkxPermissionProbeBoundary` 使用字符串包含分类。二者均禁止复用为 GateW-2 production boundary。

## Official OKX Protocol Facts

- 官方标题：OKX API guide；入口：<https://www.okx.com/docs-v5/en/>；访问日期：2026-07-13。
- 官方标题：OKX API changelog；入口：<https://www.okx.com/docs-v5/log_en/>；访问日期：2026-07-13。
- 允许候选确认如下：`GET /api/v5/account/config` 与 `GET /api/v5/account/balance`，均为 private `Read` permission；前者 rate limit 为 5 requests / 2 seconds，后者为 10 requests / 2 seconds，维度为 User ID。
- private REST 使用 `OK-ACCESS-KEY`、`OK-ACCESS-SIGN`、`OK-ACCESS-TIMESTAMP`、`OK-ACCESS-PASSPHRASE`。签名 prehash 为 timestamp + uppercase method + requestPath（包括 query）+ body，经 HMAC-SHA256 和 Base64；GET 无 body。
- REST timestamp 采用 UTC ISO-8601、毫秒精度；超过 30 秒窗口会被拒绝。实施仅允许 injected `Clock`，不自行校时或自动重试。
- global REST 新建实现固定 `https://openapi.okx.com`；不允许任意 host、regional fallback 或跟随 redirect。demo 模式需显式 `x-simulated-trading: 1`；环境不得隐式切换。
- config 响应只解析 permission/account-mode allowlist 字段；`uid`、`mainUid`、`ip`、`label`、`kycLv` 等不进入 observation/log。balance 只解析 allowlisted currency 的 `ccy`、`cashBal`、`availBal`、`frozenBal`、`uTime`。
- 关键官方错误类别包括 rate limit `50011`、timestamp expiry `50102`、signature/auth failure `50113` 与 environment mismatch `50101`；未在实施当日从官方页面再次确认的 code 不得建立精确 production 映射。

## Frozen Implementation Baseline

### Typed Operations and Endpoint Allowlist

production caller 只能传以下 typed operation，不能传 raw private path、method、host 或任意 query map：

| Operation | Method | Exact path | Query schema | Result |
| --- | --- | --- | --- | --- |
| `OKX_ACCOUNT_CONFIGURATION_READ` | `GET` | `/api/v5/account/config` | 无 | allow only when all runtime gates pass |
| `OKX_ACCOUNT_BALANCE_READ` | `GET` | `/api/v5/account/balance` | 仅服务端 configured `ccy` allowlist，uppercase、排序、去重，最多 3 个 | allow only after config confirms read-only permission |

所有 order submit/cancel/amend、transfer、withdraw、funds movement、unknown operation/path/method/host/query 均永久拒绝。config 若包含 `trade` 或 `withdraw`，立即返回 blocked，不继续 balance request。public marketdata path 不得被 private guard 接管。

### Credential Selection and Lifecycle

- selection key 固定为 `(ownerId, exchangeAccountId, credentialType=OKX_API_V5)`。
- 先核验 account owner、`exchange_code=OKX`、account `ACTIVE`、GateW safety environment；不得隐式选择账户或降级到其他 credential type。
- active candidate 为 0：`CREDENTIAL_UNAVAILABLE`；为 1：才可进入窄解密 callback；大于 1：`CREDENTIAL_CONFLICT`，即使 DB unique constraint 理论上应阻止该状态也必须 fail-closed。
- disabled、revoked、expired、rotated/inactive candidate 全部排除；`permission_scope` 仅为本地 governance metadata，不能当作交易所真实权限或 trading authorization。

### Decrypt and Sensitive-memory Boundary

- 新实现必须在 infrastructure-scoped executor/callback 内选择并临时解密；domain、application DTO、cache、singleton、audit、evidence 和日志不得持有 plaintext、signature 或完整 authenticated header。
- transport 返回前即销毁临时 secret context；可清理的 `byte[]`/`char[]` 在 `finally` 覆盖。不得写回数据库，异常只能携带 sanitized internal category。
- 现有 decryptor/material/request 使用 immutable `String`，无法可靠清零，是 P2 限制。GateW-2 不复用该明文跨层 contract；本轮也不扩张为密码学重构。

### Signer and Transport

- signer 输入限定为 temporary secret context、typed operation、canonical query、injected `Clock`；GET body 固定为空。
- exact global host allowlist：`https://openapi.okx.com`；`HttpClient.Redirect.NEVER`，任何 3xx 均失败。regional account/domain 不在本轮范围，必须 scope review 后另行支持。
- security control 默认值：connect timeout 2 秒（上限 5 秒）、request/read timeout 5 秒（上限 10 秒）、response body 上限 256 KiB、单 probe 并发 1、无自动 retry。它们是 NQ 本地安全上限，不冒充 OKX 官方 rate-limit 值。
- 禁止 generic `send(method,path)`、raw body/header logging、未知 redirect、错误降级为 READY。网络、timeout、oversize、malformed JSON、HTTP、OKX non-zero code、auth、permission、rate-limit、clock-skew、environment、partial response 分别映射 sanitized taxonomy，全部 fail-closed。

### Observation and Persistence

选择方案 A：非持久化最小 probe。config/balance 仅形成 in-memory diagnostic observation；不写现有 probe columns、账户表、ledger、audit payload 或 snapshot 表，不做 reconciliation，不构成 GateW durable snapshot 完成证据。缺失/blank numeric 或 timestamp 字段映射为 `PARTIAL/UNKNOWN`，不得补零。

observation 必须固定 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`orderSubmitted=false`；只允许 `PASSED_READ_ONLY / BLOCKED / PARTIAL / UNKNOWN / NOT_READY` 等窄语义，不输出 general `READY`。

因此 migration decision 为 `NO MIGRATION`。如后续需要 durable snapshot/reconciliation，必须先独立 schema/migration review，不能在 implementation 中偷加 migration。

### Spring/Profile and Invocation

- 同时满足显式 profile `gatew-okx-readonly`、feature flag `nq.gatew.okx-private-readonly.enabled=false` 被人工改为 true、LIVE false、显式 owner/account/type、人工触发，才允许装配/调用。
- default/local/test/CI 不得创建真实 private transport、读取/decrypt credential 或产生 outbound。GateW profile 不注册 mutating trading Bean。
- 禁止 `@Scheduled`、`ApplicationRunner`、`CommandLineRunner`、`@PostConstruct` network、startup probe、后台轮询或自动 credential decrypt。

### Logging and Redaction

只记录 operation、sanitized outcome、elapsed bucket、HTTP/OKX category、request correlation 与本地非敏感 account reference。禁止 API key/secret/passphrase、signature/prehash、authenticated headers、raw request/response、balance value、remote UID、credential plaintext/fragment 和 provider error body。异常 message 进入日志前必须重新映射，不直接透传 provider text。

### Manual Real Smoke and API Key Rule

`REAL_SMOKE=NOT_RUN`。本 review 不需要也不接收 API Key。未来 smoke 必须是 CI 外、人工显式、单独 evidence 的安全受控步骤；用户只能通过 NQ 本地 credential 管理路径配置 Key/Secret/Passphrase，禁止在聊天、命令行参数或 evidence 中粘贴明文。Key 只允许 `Read`，`Trade` 与 `Withdraw` 必须关闭；缺失时为 `BLOCKED / API_KEY_REQUIRED`。mock/unit test/历史日志不得冒充真实联通。

### Required Tests and Post-implementation Review

implementation 至少覆盖 typed allowlist、query canonicalization、signature fixture/Clock、0/1/>1 credential、owner/type/lifecycle exclusions、secret redaction、timeout/redirect/body limit/no retry、config permission、partial balance、all error categories、Spring negative profiles、no startup/no outbound/no persistence，以及 order/cancel/transfer/withdraw zero-call regression。

实现严格遵循本 baseline、无 P0/P1、无 migration、无 real smoke 后，只需针对 diff 的精简 security conformance review；不重复完整方案审查。真实 smoke 始终单独记录。

## Findings

- P0：0。
- P1：0。以上强制 baseline 消除了继续复用 unsafe generic client/plaintext cross-layer/persistent probe 的实施路径。
- P2：现有 decrypt contract 返回 immutable plaintext `String`，不能可靠清零；GateW-2 通过新窄 infrastructure callback 隔离，不在本轮做密码学重构。现有 generic OKX client、persistent probe service 和 substring classifier 仅作为历史事实，禁止复用。
- P3：历史 OKX runtime 使用 legacy environment naming；新 GateW-2 namespace 必须使用 canonical safety semantics，不沿用该命名。current docs 的 GateW-1 未提交描述已按真实 commit/CI 纠正。

## Validation and Boundary

- preflight、exact-HEAD CI、governance lifecycle、next-action regression 与 authority checker 已核验；最终 post-edit 结果以本 task 收尾命令为准。
- 本轮未运行 Maven/frontend/Python：允许范围为 docs/evidence only，未修改对应代码。
- 未读取/选择/解密真实 credential，未调用 OKX API，未创建 client/transport/probe，未执行 real smoke，未修改 API/DB/migration/frontend/CI。

## Authority After and Next Actions

```text
accepted_batch=GateW-1
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateW-2
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-2-IMPLEMENTATION
```

操作顺序：先执行 `NQ-GATEW-2-SECURITY-REVIEW-COMMIT-AND-PUSH`；review baseline commit exact-HEAD CI green 后，执行重新编写的 `NQ-GATEW-2-OKX-SPOT-PRIVATE-READONLY-PROBE-IMPLEMENTATION`。
