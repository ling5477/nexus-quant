# Credential Permission Probe Freeze Review

任务：NQ-CREDENTIAL-PERMISSION-PROBE-FREEZE-REVIEW
日期：2026-06-14
状态：permission probe guarded backend implementation FROZEN / ACCEPTED；P3 cleanup completed；real exchange permission probe adapter NOT IMPLEMENTED。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE disabled。

## 1. Scope

本轮为 L 级后端安全冻结审查，只冻结当前 no-real-exchange credential permission probe 最小后端实现基线。本轮只允许文档同步和 freeze review 记录，不修改 Java、测试、migration、API 语义、前端、Python 或部署脚本。

冻结对象是 `b473eec1` 中已经落地的 guarded backend implementation：独立 `ExchangeCredentialPermissionProbePort`、`CredentialPermissionProbeService`、默认 `NoRealExchangeCredentialPermissionProbePort`、POST / GET API、JDBC V31 字段读写和 no-real-exchange tests。注意：`b473eec1` commit subject 为 `docs(credential): review permission probe implementation design`，但实际内容包含 implementation 文件、API 和 tests；后续审计不得仅凭 subject 将其误判为 docs-only commit。

本轮明确不冻结真实交易所 permission probe adapter，不实现 OKX/Binance/Bybit/Gate 私有 HTTP 探活，不调用真实交易所，不下单、撤单、转账或提现。

## 2. Files Inspected

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeCredentialPermissionProbePort.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/command/CredentialPermissionProbeCommand.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/CredentialPermissionProbeSummary.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeCredentialPermissionProbeRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeCredentialPermissionProbeResult.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/probe/NoRealExchangeCredentialPermissionProbePort.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/CredentialPermissionProbeRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/CredentialPermissionProbeResponse.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java`
- `backend/nq-infra/src/main/resources/db/migration/V31__schema_credential_permission_probe.sql`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/NoRealExchangeCredentialPermissionProbePortTest.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPermissionProbeBoundary.java`
- `backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPermissionProbeBoundaryTest.java`
- `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinancePermissionProbeBoundary.java`
- `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinancePermissionProbeBoundaryTest.java`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`
- `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 3. Freeze Summary

- permission probe guarded backend implementation：FROZEN / ACCEPTED。
- real exchange permission probe adapter：NOT IMPLEMENTED。
- default runtime behavior：`NoRealExchangeCredentialPermissionProbePort` -> `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。
- LIVE credential probe：DISABLED / REJECTED by Service gate before port call。
- AI / DH / LIVE：NOT STARTED。
- no-real-exchange tests：REQUIRED BASELINE。
- future real adapter：must be separate task + separate security review + fake-server/no-egress tests。

## 4. Required Review Checklist

| # | 审查项 | 结论 |
| --- | --- | --- |
| 1 | implementation review 是否 P0/P1=0 | 是。P0=0，P1=0。 |
| 2 | full Maven test 是否通过 | 是。`mvn -f backend/pom.xml test` 通过，23 个 reactor module `SUCCESS`，`BUILD SUCCESS`。 |
| 3 | no-real-exchange 证据是否充分 | 是。默认 port 不创建 HTTP client；NoReal test 使用 `ProxySelector` guard；Service/Web/adapter boundary tests 覆盖 no-real-exchange 边界。 |
| 4 | 默认 NoReal port 是否仍为默认 bean | 是。`AccountModuleConfiguration.exchangeCredentialPermissionProbePort()` 返回 `new NoRealExchangeCredentialPermissionProbePort()`。 |
| 5 | Service 是否仍不直接写 HTTP | 是。Service 只依赖 `ExchangeCredentialPermissionProbePort`，未创建 HTTP client，未写交易所请求。 |
| 6 | API response 是否脱敏 | 是。`CredentialPermissionProbeResponse` 只返回 status/scope/IP/failed count/requestId/traceId 等 summary。 |
| 7 | request body 是否拒绝 credential material | 是。`CredentialPermissionProbeRequestBody` 只允许 `reason / dryRun / mode / paperSafetyConfirmed`，`@JsonAnySetter` 拒绝未知字段。 |
| 8 | audit metadata 是否脱敏 | 是。metadata 只写 account/credential/status/scope/IP/error category/requestId/traceId，不写 raw request、raw response、headers、signature 或 credential material。 |
| 9 | LIVE probe 是否默认拒绝 | 是。`tradeEnv=LIVE` 走 `LIVE_CREDENTIAL_BLOCKED`，不调用 port。 |
| 10 | Paper safety gate 是否存在 | 是。要求 `paperSafetyConfirmed=true`、`dryRun=true`、`mode=PAPER`。 |
| 11 | latest summary GET 是否不触发 port | 是。`latest()` 只读 summary；`CredentialPermissionProbeServiceTest.latestShouldNotCallPortOrReadMaterialAgain` 覆盖 port calls = 0。 |
| 12 | JDBC V31 字段读写是否已覆盖 | 是。Repository 读写 `permission_probe_status / permission_scope / ip_allowlist_probe_status / failed_auth_count / last_permission_probe_at / last_permission_probe_error`，JDBC test 覆盖写回 SQL。 |
| 13 | 是否无新增 migration | 是。本轮 freeze review 未新增 migration；`git diff -- backend/nq-infra/src/main/resources/db/migration` 输出为空。 |
| 14 | 是否未改 frontend / Python / deploy | 是。本轮检查 `git diff -- frontend`、`git diff -- research`、`git diff -- scripts` 输出为空。 |
| 15 | 是否仍禁止真实 OKX/Binance permission probe adapter | 是。当前 OKX/Binance 仅有 boundary classifier / forbidden endpoint tests，没有真实 adapter bean 或 HTTP probe 实现。 |
| 16 | P3 遗留是否已完成或限定 | 是。NoReal port requestId/traceId 混同已在 P3 cleanup 中修复；文档 gate 顺序与实现顺序轻微差异已降级为历史设计口径差异，不阻塞冻结。 |

## 5. P0 / P1 / P2 / P3 Findings

### P0

无。

未发现真实交易所 HTTP permission probe、LIVE probe、下单、撤单、转账、提现、AI/DH credential access、credential material 泄露或 API/audit 返回敏感材料。

### P1

无。

冻结基线已满足独立 port、Service 不写 HTTP、no-real-exchange 默认 bean、LIVE blocked、Paper safety gate、脱敏 response/audit 和 no-real-exchange tests。

### P2

无阻塞项。

`withdraw_enabled=false` 仍未做 DB hard CHECK，这是 V31 已记录的 schema-hardening 候选；当前 Service 对 `withdraw_enabled=true` 直接 `SKIPPED / WITHDRAW_ENABLED_RISK`，足够支撑 no-real-exchange guarded baseline 冻结。

### P3

- P3-CLOSED：NoReal port requestId / traceId 混同已修复。NoReal fake result 现在生成本地脱敏 `noreal-probe-<uuid>` requestId，traceId 仍来自请求链路；该 requestId 不包含 credential material、endpoint、headers 或签名。
- P3-DOCUMENTED：文档 gate 顺序与实现顺序轻微差异已降级为历史设计口径差异。当前权威状态以本 freeze review、`API.md` 和 `DB_SCHEMA.md` 为准；实现仍保证 LIVE / Paper safety / withdraw risk 在 port 调用前阻断真实交易所调用。

## 6. Accepted Baseline

本轮接受并冻结以下 no-real-exchange / guarded backend baseline：

- `ExchangeCredentialPermissionProbePort` 作为唯一 permission probe adapter boundary。
- `CredentialPermissionProbeService` 只做 owner/account/credential gate、ACTIVE/is_active gate、LIVE blocked、withdraw risk、Paper safety gate、IN_PROGRESS claim、V31 writeback 和 audit。
- `NoRealExchangeCredentialPermissionProbePort` 作为默认 runtime bean，返回 `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe` 只接受非敏感控制字段。
- `GET /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe/latest` 只读 latest summary，不触发 port。
- API response 与 audit metadata 只返回脱敏 summary。
- JDBC V31 字段读写与 `failed_auth_count` 策略已纳入测试基线。
- OKX/Binance 当前只冻结 boundary classifier 和 forbidden endpoint guard，不冻结真实 adapter。

## 7. Explicitly Not Frozen

- 真实 OKX permission probe adapter。
- 真实 Binance permission probe adapter。
- Bybit / Gate / 任意其他真实交易所 permission probe adapter。
- 真实私有 HTTP 探活。
- LIVE credential probe enablement。
- 任何下单、撤单、转账、提现或资金变动路径。
- AI / Agent / DH credential access。
- 前端 permission probe 页面或操作入口。
- 新 migration、history table、异步 probe、idempotency key 持久化、rate limit 持久化。

## 8. Validation

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 开始审查前为空；文档修改后仅包含允许的 docs/current / README 文档变更。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app,nq-adapter-okx,nq-adapter-binance -am test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `git diff --check` | 通过 | 无 whitespace error；如出现 LF/CRLF 提示，仅为 Git 行尾转换提示。 |
| `git diff --stat` | 已执行 | 仅统计本轮允许的文档变更。 |
| `git diff -- backend/nq-infra/src/main/resources/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `git diff -- frontend` | 通过 | 输出为空，未修改前端。 |
| `git diff -- research` | 通过 | 输出为空，未修改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未修改脚本或部署入口。 |

Maven 输出中的 SLF4J no-provider warning、Mockito dynamic agent warning 和测试内预期异常栈日志为既有测试运行噪音；本轮未因此出现 test failure。

## 9. Boundary Confirmation

- 未修改 Java。
- 未修改测试代码。
- 未新增 API。
- 未新增 migration。
- 未修改历史 migration。
- 未修改前端。
- 未修改 Python。
- 未修改部署脚本。
- 未调用 OKX / Binance / Bybit / Gate 或任何真实交易所。
- 未真实 HTTP 探活。
- 未下单、撤单、转账或提现。
- 未读取、打印、复制或输出真实 API key、secret、token、私钥、助记词、passphrase、cookie 或 credential material。
- 未把真实交易所 permission probe 写成已完成。
- 未把 OKX/Binance adapter 写成已接通。
- 未把 GateK-PLAN 写成 GateK implementation。
- 未把 DH not integrated 写成 DH integrated。
- 未把 LIVE disabled 写成 LIVE enabled。

## 10. P3 Cleanup Record

2026-06-14 P3 cleanup 完成以下收口：

- NoReal port requestId 与 traceId 已分离；NoReal 仍不创建 HTTP client，不解析真实 endpoint，不访问 OKX/Binance。
- NoReal unit test 已断言 `requestId != traceId`、status 仍为 `SKIPPED`、error category 仍为 `REAL_EXCHANGE_PROBE_DISABLED`，并保留真实 host 禁访 guard。
- 文档层级已固定：本文件是当前冻结结论；`API.md` 是 API 对外语义；`DB_SCHEMA.md` 是字段语义；设计审计与 code/API/test review 保留为历史证据。
- Future real adapter：必须另起任务，先做安全设计审查、fake-server / no-egress tests、endpoint allowlist、short timeout、raw response/log/audit 脱敏、LIVE gate 和 rollback plan；不得在本冻结基线内补做。

## 11. Risks

- 当前冻结只证明 no-real-exchange guarded backend baseline 可接受，不证明任何真实交易所 credential 权限可用。
- `permission_scope=READ_ONLY / TRADE / FUNDING` 是脱敏 summary 字段；在真实 adapter 未实现前，不得被交易链路、AI、DH 或 LIVE 逻辑使用为授权依据。
- `withdraw_enabled=false` 仍未被 DB hard CHECK 强制；当前依赖 Service gate，后续如要 harden 必须另起数据确认 + schema-only 批次。
- NoReal fake port requestId 已与 traceId 分离；剩余风险不来自 fake port 字段混同，而来自 future real adapter 尚未设计和验证。

## 12. Freeze Decision

Freeze decision：ACCEPTED。

Credential permission probe guarded backend implementation 可以作为 no-real-exchange / guarded implementation baseline 冻结。P0/P1=0，full Maven test 通过，默认 runtime 行为仍为 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`，LIVE credential probe 默认拒绝，API/audit 脱敏，latest GET 不触发 port，本轮只修改文档。

Next concrete action：如需真实 OKX/Binance permission probe adapter，必须另起任务并执行 separate security review + fake-server/no-egress tests；不得在当前冻结基线内追加实现。
