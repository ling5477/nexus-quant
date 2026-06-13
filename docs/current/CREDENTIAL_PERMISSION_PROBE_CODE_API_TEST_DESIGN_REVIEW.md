# Credential Permission Probe Code / API / Test Design Review

任务：NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-DESIGN-REVIEW
日期：2026-06-12
状态：code/API/test design review completed；minimal code/API/test implemented；freeze review accepted；no real exchange call performed。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. Scope

本轮原始审计是 L 级只读设计审计，覆盖 credential permission probe 后续 code/API/test 实现方案。后续实现批次已按本报告入场条件落地最小后端能力。

设计审计批次只修改 `docs/current` 文档和 README 索引；后续实现批次修改 Java 后端与后端测试、同步 `docs/current`，未新增 migration，未修改前端、Python 或部署脚本；未调用 OKX、Binance、Bybit、Gate 或任何真实交易所；未读取或输出真实密钥；未接 AI、DH runtime 或 LIVE。

## 2. Current State

- Credential governance 已完成并冻结。
- V31 schema-only migration 已完成：`permission_probe_status`、`last_permission_probe_at`、`last_permission_probe_error`、`ip_allowlist_probe_status`、`permission_scope=FUNDING`、permission probe audit events 已准备。
- Permission probe 最小 runtime / code / API 已实现：存在独立 `ExchangeCredentialPermissionProbePort`、`CredentialPermissionProbeService`、`POST /permission-probe`、`GET /permission-probe/latest` 和 Repository/JDBC V31 probe 字段写回方法。默认 port 为 no-real-exchange fake，未接真实交易所 adapter。
- 当前 `POST /api/exchange-accounts/{accountId}/credentials/verify` 仍是本地结构性校验，只复用 signer/credential 格式能力，不访问真实交易所，不证明权限可用。
- API response 当前通过 `ExchangeAccountCredentialSummaryResponse` 只返回非敏感 summary；create / rotate request 可接收 credential material，但 response 不返回 material。
- Repository active material 查询已要求 `is_active=true AND credential_status='ACTIVE'`，多 ACTIVE credential type 无 `credentialType` 时返回 conflict。
- OKX bootstrap no-outbound fix 已完成：`OkxInstrumentsCache` 构造期不再发起 public instruments HTTP；local Spring context no-outbound 回归测试已覆盖。

## 3. Schema Sufficiency

结论：V31 足够支撑下一步最小 code/API/test implementation；本轮不需要 schema 补丁。

V31 已支持最小实现所需字段：

- `permission_probe_status`：`NOT_PROBED / IN_PROGRESS / SUCCEEDED / FAILED / SKIPPED`。
- `last_permission_probe_at`：真实权限探活完成时间。
- `last_permission_probe_error`：脱敏错误摘要或错误分类。
- `ip_allowlist_probe_status`：`NOT_CHECKED / PASSED / FAILED / UNKNOWN / SKIPPED`。
- `permission_scope`：`READ_ONLY / TRADE / FUNDING / NULL`，其中 `NULL` 表示未确认权限，不等于 `TRADE`。
- `withdraw_enabled`：默认 false，作为治理元数据。
- `credential_audit_logs.event_type`：已支持 `PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED`。

暂不需要本轮或下一步最小实现前置 schema patch：

- 不需要新增 migration 才能写入 probe summary。
- 不需要新增独立 probe history 表才能做同步最小探活；append-only audit log 已可记录状态变化。
- 不需要修改历史 migration。

后续可单独评估的 schema-only 批次：

- `withdraw_enabled=false` hard CHECK：需要先做数据确认，证明既有行均为 false，或先清理异常行。
- 独立 `credential_permission_probe_attempts` / history 表：只有当产品需要多次 probe 历史、异步 probe、重试编排或幂等 request key 持久化时才需要。
- 更细粒度 permission metadata：如需要保存交易所原生权限位，只能保存脱敏、枚举化结果，不保存 raw response。

## 4. Findings

### P0

无。

未发现当前代码已经实现真实权限探活、真实交易所调用、LIVE probe、下单、撤单、转账、提现、AI/DH credential access 或 credential material 泄露路径。

### P1

#### P1-1：实现批次必须新增独立 probe port，否则真实 HTTP 边界会混入 Service

- 问题：当前只有 `ExchangeAccountCredentialVerifier` 结构性校验 port，尚无独立 `ExchangeCredentialPermissionProbePort`。
- 影响：如果直接在 credential Service 中写 HTTP，会破坏分层，难以 mock/fake，增加凭证泄露和真实交易所调用越界风险。
- 证据文件：`backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeAccountCredentialVerifier.java`；`backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationService.java`。
- 建议处理：下一步 code implementation 必须新增独立 port，Service 只编排校验、状态写回和审计，真实 HTTP 只在 adapter 层。
- 是否阻塞下一步 code implementation：不阻塞进入实现批次；但若实现批次不包含该 port，则阻塞验收。

#### P1-2：实现批次必须内置 no-real-exchange 测试护栏

- 问题：permission probe 的目标是触发交易所私有只读权限探活，测试若没有 fake server / mock port / socket guard，容易误连真实 OKX/Binance。
- 影响：CI、本地 full Maven test 或 Spring context test 可能访问真实交易所，违反测试隔离和凭证安全边界。
- 证据文件：`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/OkxBootstrapNoOutboundLocalContextTest.java` 已提供 OKX no-outbound 模式；当前尚无 permission probe no-real-exchange tests。
- 建议处理：实现批次必须新增 unit / adapter / web / full context no-real-exchange 测试，覆盖 `www.okx.com`、`api.binance.com` 禁访和 order/cancel/transfer/withdraw endpoint 禁用。
- 是否阻塞下一步 code implementation：不阻塞进入实现批次；但若实现批次缺失该测试矩阵，则阻塞验收。

#### P1-3：LIVE probe 必须默认拒绝，Paper safety gate 必须先于 port 调用

- 问题：当前系统处于 GateJ completed / GateK-PLAN，LIVE disabled；permission probe 未来如直接允许 LIVE credential 或缺少 Paper safety context，会越过当前阶段边界。
- 影响：即使探活只访问只读 endpoint，也会把真实 credential 私有接口调用带入非 LIVE 阶段。
- 证据文件：`AGENTS.md`；`docs/current/README.md`；`docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`。
- 建议处理：Service 必须先执行 owner/account/credential 校验、ACTIVE 状态校验、Paper safety gate、LIVE 禁止，再决定是否调用 port；LIVE 默认写 `PERMISSION_PROBE_SKIPPED` 或返回拒绝，不调用 adapter。
- 是否阻塞下一步 code implementation：不阻塞进入实现批次；但缺少该 gate 会阻塞验收。

### P2

#### P2-1：V31 未强制 `withdraw_enabled=false`，实现批次需代码层拒绝 true

- 问题：V31 出于数据确认原因没有新增 `CHECK (withdraw_enabled = FALSE)`。
- 影响：如历史或异常数据出现 `withdraw_enabled=true`，不能被误读为提现能力或可接受生产状态。
- 证据文件：`backend/nq-infra/src/main/resources/db/migration/V31__schema_credential_permission_probe.sql`；`docs/current/DB_SCHEMA.md`。
- 建议处理：实现批次在 Service 层把 `withdraw_enabled=true` 视为风险或拒绝 probe；后续单独 schema-only 批次评估 hard CHECK。
- 是否阻塞下一步 code implementation：不阻塞；属于实现验收条件和后续 schema hardening 候选。

#### P2-2：同步最小实现可以不新增 probe history 表，但幂等和并发策略必须明确

- 问题：V31 只保存 latest summary 和 append-only audit log，没有单独 probe attempt 表或幂等 key。
- 影响：并发 probe 同一 credential 时，可能出现重复外部调用、状态覆盖、audit event 顺序复杂。
- 证据文件：`V31__schema_credential_permission_probe.sql`；`ExchangeAccountCredentialRepository` 当前已有 `FOR UPDATE` 模式可参考。
- 建议处理：下一步最小实现使用 credential row lock 或同等并发控制；同一 credential 同时 probe 时返回 409 / reuse in-progress，不在无锁状态下重复调用 adapter。
- 是否阻塞下一步 code implementation：不阻塞；实现批次必须给出明确策略。

#### P2-3：GET latest 是可选 API，不应扩大首批实现面

- 问题：只读 summary endpoint 有价值，但不是触发 probe 的必要条件。
- 影响：若首批同时实现过多 API，容易扩大 DTO、权限和前端契约面。
- 证据文件：当前 `ExchangeAccountCredentialController` 已有 active / verify / lifecycle API，但无 probe endpoint。
- 建议处理：首批优先实现 `POST .../permission-probe`；`GET .../permission-probe/latest` 可作为同批只读 summary，或后续单独批次。
- 是否阻塞下一步 code implementation：不阻塞。

### P3

#### P3-1：文档索引需区分 design completed 与 implemented

- 问题：permission probe 相关文档已有 design review 和 V31 schema-only 记录；新增 code/API/test design review 后，索引必须避免把其写成 runtime implemented。
- 影响：阶段状态可能被误读。
- 证据文件：`README.md`；`docs/current/README.md`；`docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`。
- 建议处理：索引统一写成 code/API/test design review completed；permission probe runtime not implemented。
- 是否阻塞下一步 code implementation：不阻塞。

## 5. Recommended API Design

### POST trigger

推荐 endpoint：

```text
POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe
```

请求设计：

- 请求体不得接收 credential material。
- 不接收 `apiKey`、`secretKey`、`passphrase`、`privateKeyPem`、token、cookie、signature、headers 或 raw payload。
- `credentialType` 从 `credentialId` 对应 credential 派生，不由请求体传入。
- `actor` 从认证主体派生，不由请求体传入。
- 可选字段只允许脱敏控制项，例如 `reason`、`requestedScope`、`paperRunId` 或 `paperContextId`。
- `requestedScope` 初版建议默认 `READ_ONLY`；`TRADE / FUNDING` 必须要求显式请求和额外风险提示，但仍不得下单、撤单、转账或提现。

响应设计：

- 只返回脱敏 summary。
- 建议字段：`accountId`、`credentialId`、`credentialType`、`exchange`、`permissionProbeStatus`、`permissionScope`、`withdrawEnabled=false`、`ipAllowlistProbeStatus`、`failedAuthCount`、`lastPermissionProbeAt`、`sanitizedErrorCategory`、`sanitizedErrorMessage`、`traceId`。
- 不返回 raw exchange response。
- 不返回 headers、signature、request body。
- 不返回 `encrypted_payload`、`decrypted_payload`、API key、secret、private key、passphrase、token、cookie。

状态码建议：

- `200`：同步 probe 完成并返回 latest summary。
- `202`：如未来改成异步，只表示 accepted，不表示成功。
- `400`：请求 scope 非法或 payload 含禁止字段。
- `401 / 403`：未认证或无 owner/account 权限。
- `404`：account/credential 不存在或不属于 owner。
- `409`：credential 非 ACTIVE、inactive、LIVE 禁止、Paper gate 缺失、并发 IN_PROGRESS 或状态冲突。
- `429 / 503`：如服务端自身限流或探活暂不可用。

### Optional GET latest

可选 endpoint：

```text
GET /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe/latest
```

该 endpoint 只读 latest summary，不读取 credential material，不调用 adapter，不触发真实 HTTP。

## 6. Recommended Service Design

推荐新增 `CredentialPermissionProbeService`，职责仅限编排：

1. 从认证主体解析 actor / owner，不接受请求体 actor。
2. 校验 account 属于 owner。
3. 校验 credential 属于 account + owner。
4. 校验 `credential_status='ACTIVE'` 且 `is_active=true`。
5. 校验 credential material 可解密但不输出、不日志、不进入 audit metadata。
6. 默认拒绝 LIVE credential probe；如未来允许 LIVE，必须单独 Gate、人工审批、feature flag、强提示和审计。
7. 校验 Paper safety gate；缺失时返回拒绝并写 `PERMISSION_PROBE_SKIPPED`，不调用 port。
8. 对 `withdraw_enabled=true` 直接风险拒绝或写 `SKIPPED`，不把 true 当作可接受状态。
9. 设置 `permission_probe_status=IN_PROGRESS` 或在同事务内记录 STARTED 证据；并发同 credential probe 应被锁定或返回 409。
10. 调用 `ExchangeCredentialPermissionProbePort`。
11. 根据 `ProbeResult` 写回 `permission_probe_status`、`permission_scope`、`withdraw_enabled=false`、`ip_allowlist_probe_status`、`last_permission_probe_at`、`last_permission_probe_error`。
12. 根据错误分类增加或不增加 `failed_auth_count`。
13. 写 append-only audit log。

`failed_auth_count` 策略：

- 认证失败、签名失败、API key 无效、passphrase 错误、IP allowlist failed：增加。
- 超时、连接失败、429、5xx、unsupported exchange、Paper gate blocked、LIVE blocked、payload invalid：不增加。
- 成功不自动清零；如未来需要重置，必须单独设计审批、审计和测试。

状态写回语义：

- `NOT_PROBED`：默认状态，尚未探活。
- `IN_PROGRESS`：探活已开始但未完成；必须有并发保护和超时恢复策略。
- `SUCCEEDED`：安全 endpoint 成功返回，且结果已脱敏归类；不代表 LIVE enabled，不代表提现可用。
- `FAILED`：外部探活失败，保存脱敏错误分类/摘要。
- `SKIPPED`：策略阻止调用，例如 LIVE credential、Paper gate 缺失、unsupported exchange、credential 非 ACTIVE。

事务边界：

- 状态检查、STARTED audit、IN_PROGRESS 写入应在短事务内完成。
- 外部 HTTP 不应长时间占用数据库事务；如需要强一致状态，可采用 claim / finish 两阶段。
- finish 阶段单独事务写 latest summary 和 final audit。
- 不得在事务中无限重试或等待长耗时外部调用。

超时、限流、重试：

- Adapter 必须设置短超时。
- 不允许无限重试。
- 认证失败、签名失败、IP allowlist failed 不重试。
- 429 / 5xx 可有限重试或直接分类为 retryable failure，但不得刷 `failed_auth_count`。
- Service 层应有每 credential / account 的调用频率限制或最小间隔策略。

幂等与并发：

- 同一 credential 已 `IN_PROGRESS` 时，拒绝新 probe 或返回当前 summary。
- 如未来支持 idempotency key，需要单独 schema 或缓存设计；当前 V31 不要求。

## 7. Recommended Port / Adapter Design

建议新增独立 port：

```text
ExchangeCredentialPermissionProbePort
```

职责：

- 只做权限探活。
- 不下单。
- 不撤单。
- 不转账。
- 不提现。
- 不读写 NQ DB。
- 不访问其他 NQ Service。
- 不输出 raw response。
- 返回结构化 `ProbeResult`。

`ProbeResult` 建议字段：

- `exchange`
- `credentialType`
- `permissionProbeStatus`
- `detectedPermissionScope`
- `withdrawEnabledDetected`
- `ipAllowlistProbeStatus`
- `sanitizedErrorCategory`
- `sanitizedErrorMessage`
- `requestId`
- `traceId`
- `startedAt`
- `finishedAt`

Adapter 限制：

- OKX / Binance probe 只允许安全 read-only endpoint。
- 禁止 order / cancel / transfer / withdraw endpoint。
- 禁止把 request body、signature、headers 写入日志。
- 禁止把 raw response 写入 result、audit metadata 或 DB。
- 禁止构造 adapter bean 时触发 probe；probe 只能由显式 API/Service 调用触发。
- 测试必须使用 fake server / mock port / socket guard，不访问真实 OKX/Binance。

建议 endpoint allowlist 在 adapter test 中固化：

- 禁止 OKX：`/api/v5/trade/order`、`/api/v5/trade/cancel-order`、`/api/v5/asset/withdrawal`、transfer 类 endpoint。
- 禁止 Binance：`POST /api/v3/order`、`DELETE /api/v3/order`、`/sapi/*transfer*`、`/sapi/*withdraw*`。
- 允许 endpoint 必须单独列入测试 allowlist，且说明只读、不产生订单或资金变动。

## 8. Recommended Audit Behavior

Audit event：

- `PERMISSION_PROBE_STARTED`：通过 owner/account/credential/Paper gate 后、调用 port 前写入。
- `PERMISSION_PROBE_SUCCEEDED`：probe 成功、summary 写回后写入。
- `PERMISSION_PROBE_FAILED`：probe 调用失败或权限失败后写入。
- `PERMISSION_PROBE_SKIPPED`：策略阻止调用时写入，例如 LIVE blocked、Paper gate missing、credential inactive、unsupported exchange。

允许 metadata 字段：

- `credentialId`
- `accountId`
- `credentialType`
- `exchange`
- `fromStatus`
- `toStatus`
- `probeStatus`
- `detectedScope`
- `ipAllowlistStatus`
- `errorCategory`
- `retryCount`
- `policyDecision`
- `reasonPresent`
- `requestId`
- `traceId`
- `failedAuthCountIncremented`

禁止 metadata 字段：

- API key
- API secret
- token
- cookie
- passphrase
- private key
- mnemonic
- signature
- headers
- raw request
- raw response
- decrypted payload
- encrypted payload
- full prompt
- full context
- exchange credential material

脱敏规则：

- `last_permission_probe_error` 只保存错误分类和短摘要，例如 `AUTH_FAILED`、`IP_ALLOWLIST_FAILED`、`TIMEOUT`、`RATE_LIMITED`、`EXCHANGE_5XX`。
- 不保存交易所原始错误全文；如需要原生 code，只保存 allowlisted code，不保存 raw message。
- audit reason 只记录 `reasonPresent=true/false` 或经过敏感词拒绝后的短文本；不得把用户输入 reason 原样信任为安全文本。

## 9. Recommended State Semantics

`permission_probe_status`：

- `NOT_PROBED`：从未探活或历史记录默认值。
- `IN_PROGRESS`：探活已被 Service claim，尚未完成；必须有超时恢复策略。
- `SUCCEEDED`：探活成功并写回脱敏结果；不代表 LIVE、提现或 AI/DH 可用。
- `FAILED`：探活执行失败或交易所返回认证/权限/IP/网络类失败。
- `SKIPPED`：策略拒绝调用，未访问交易所。

`ip_allowlist_probe_status`：

- `NOT_CHECKED`：默认值，尚未确认。
- `PASSED`：只读探活未触发 IP allowlist 拒绝。
- `FAILED`：交易所明确返回 IP allowlist 拒绝或等价错误分类。
- `UNKNOWN`：endpoint 无法判断，或 timeout/5xx 导致不能确认。
- `SKIPPED`：策略拒绝 probe，未访问交易所。

`permission_scope`：

- `READ_ONLY`：只读权限确认。
- `TRADE`：交易权限被安全只读方式确认；仍不代表下单已允许。
- `FUNDING`：资金类权限被识别为存在；不得隐含提现能力。
- `NULL`：未确认权限，不等于 `TRADE`，不等于 `READ_ONLY`。

`withdraw_enabled`：

- 必须保持 false。
- true 视为安全风险。
- 本轮不新增 hard CHECK；V31 已选择不做 `withdraw_enabled=false` CHECK。
- 后续如需强约束，必须单独数据确认 + schema hardening 批次。

## 10. Recommended Test Matrix

### Unit tests

- Service 拒绝 LIVE credential probe，且不调用 port。
- Service 拒绝 inactive credential。
- Service 拒绝 non-ACTIVE `credential_status`。
- Service 写 `PERMISSION_PROBE_STARTED` / `PERMISSION_PROBE_SUCCEEDED` audit。
- Service 写 `PERMISSION_PROBE_FAILED` audit。
- Service 写 `PERMISSION_PROBE_SKIPPED` audit。
- `failed_auth_count` 不因成功被错误清零。
- 认证失败 / IP allowlist failed 增加 `failed_auth_count`。
- timeout / 429 / 5xx 不增加 `failed_auth_count`。
- `permission_scope=NULL` 不被当作 `TRADE`。
- `withdraw_enabled=true` 被识别为风险或拒绝。
- `last_permission_probe_error` 脱敏，不包含 raw response、header、signature、secret。
- 并发同 credential probe 返回 409 或保持单一 claim。

### Adapter tests

- OKX adapter mock probe 不访问真实 OKX。
- Binance adapter mock probe 不访问真实 Binance。
- order / cancel / transfer / withdraw endpoint 不被调用。
- raw response 不出现在 `ProbeResult`、logs、audit metadata。
- timeout 分类正确。
- 429 分类正确。
- 5xx 分类正确。
- auth failed 分类正确。
- IP allowlist failed 分类正确。
- request signer 不把 signature / headers 写入日志。

### Web/API tests

- `POST /permission-probe` response 脱敏。
- request body 不接受 credential material。
- unauthorized 正确返回。
- forbidden / owner mismatch 正确返回。
- account / credential mismatch 拒绝。
- credentialType 从 credentialId 派生，请求体无法覆盖。
- LIVE probe 默认拒绝。
- Paper safety gate 缺失拒绝。
- payload size 限制如存在则覆盖。
- `GET /permission-probe/latest` 如实现，只读 latest summary，不调用 port，不读取 material。

### No-real-exchange tests

- Service tests 使用 mock port。
- Adapter tests 使用 fake server。
- Full Spring context 使用 `ProxySelector` / socket guard / test double 证明不访问 `www.okx.com` 或 `api.binance.com`。
- full Maven test 不依赖真实网络。
- 日志 / surefire 报告扫描不出现 raw secret、raw response、signature、headers 或真实交易所 permission probe URL。

## 11. Relationship With OKX No-Outbound Fix

- OKX bootstrap no-outbound fix 已把 `OkxInstrumentsCache` 构造期外联消除。
- Permission probe future code 不得重新引入构造期外联。
- Probe 只能在显式 API / Service 调用时发生。
- 测试必须 mock/fake，不访问真实 OKX。
- 生产首次显式 probe 与测试 no-outbound 是两回事：生产可在显式授权路径调用 adapter；测试必须证明默认启动、full Maven、web/API tests 不访问真实交易所。

## 12. Implementation Readiness

结论：允许进入下一步 code implementation 批次，但必须是单独任务、最小范围、可测试、默认 no-real-exchange，并满足以下入场条件：

1. 不新增 migration，除非另开 schema-only 批次。
2. 新增独立 `ExchangeCredentialPermissionProbePort`。
3. Service 只编排 owner/account/credential 校验、ACTIVE 校验、Paper safety gate、LIVE 禁止、状态写回、`failed_auth_count` 和 audit log。
4. 真实 HTTP 只允许在 adapter 层。
5. 所有 adapter tests 使用 fake/mock，不访问真实交易所。
6. API response 和 audit metadata 必须脱敏。
7. full context tests 必须证明启动期和测试路径不访问 `www.okx.com` / `api.binance.com`。
8. 不接 AI、DH runtime、LIVE，不下单、撤单、转账或提现。

建议下一步任务名：

```text
NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-IMPLEMENTATION
```

建议范围：

- Java code/API/test only for permission probe。
- 不新增 migration。
- 不修改前端、Python、部署脚本。
- 不调用真实交易所。

## 13. Validation

## 13A. Freeze Review Update

2026-06-14 freeze review 已接受当前 guarded backend implementation 作为 no-real-exchange baseline。冻结口径见 `CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md`：

- permission probe guarded backend implementation：FROZEN / ACCEPTED。
- real exchange permission probe adapter：NOT IMPLEMENTED。
- default runtime behavior：`NoRealExchangeCredentialPermissionProbePort` -> `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。
- LIVE credential probe：DISABLED / REJECTED。
- AI / DH / LIVE：NOT STARTED。
- future real adapter：must be separate task + separate security review + fake-server/no-egress tests。

P3 遗留项仅作为 freeze 后 cleanup：NoReal port requestId / traceId 混同；文档 gate 顺序与实现顺序轻微差异。

本轮只读设计审计 + 文档同步应执行：

- `git status --short`
- `git diff --check`
- `git diff --stat`

本轮不执行 Maven / frontend / Python 测试；原因：未修改 Java、测试、配置、migration、前端、Python 或部署脚本，不把未执行测试写成通过。

## 14. Boundary Confirmation

- 未修改 Java。
- 未新增 API。
- 未新增 migration。
- 未修改历史 migration。
- 未修改前端。
- 未修改 Python。
- 未修改部署脚本。
- 未调用 OKX / Binance / Bybit / Gate 或任何真实交易所。
- 未实现 permission probe。
- 未真实 HTTP 探活。
- 未下单、撤单、转账或提现。
- 未读取或输出真实密钥、API key、secret、token、cookie、passphrase、private key、助记词或交易所凭证。
- 未接 AI。
- 未接 DH runtime。
- 未开启 LIVE。
- 未把 GateK-PLAN 写成 GateK implementation。
