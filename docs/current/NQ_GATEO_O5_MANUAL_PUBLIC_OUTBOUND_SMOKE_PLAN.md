# NQ-GATEO-O5-MANUAL-PUBLIC-OUTBOUND-SMOKE-PLAN

## 1. 当前状态

任务名称：`NQ-GATEO-O5-MANUAL-PUBLIC-OUTBOUND-SMOKE-PLAN`。

任务归属：NQ-only。

任务类型：`PUBLIC_MARKETDATA_SMOKE_PLANNING` / `OUTBOUND_SAFETY_REVIEW` / `SECURITY_BOUNDARY_REVIEW` / `DOCUMENTATION`。

O-5 plan status：`COMPLETED`（已完成）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

O-5 execution status：`NOT STARTED`（未开始）。

O-FREEZE status：`NOT STARTED`（未开始）。

GateO stage：`NOT COMPLETED`（未完成）。

当前事实：

- O-1 controlled public outbound guard：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- O-2 Data Quality Center：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- O-3 MarketData Runtime Readiness API：`FROZEN / ACCEPTED`（已冻结 / 已接受）。
- O-4 MarketData Quality UI：`FROZEN / ACCEPTED`（已冻结 / 已接受）。
- 当前 `GET /api/marketdata/readiness` 仍为 DB-only / no-egress / no-credential / diagnostic-only。
- 当前 MarketData Quality UI 只消费 `GET /api/marketdata/readiness`。
- LIVE：`DISABLED`（已禁用）。
- AI：`NOT STARTED`（未启动）。
- DH runtime：`NOT_INTEGRATED`（未集成）。
- RealClient / real provider / real permission probe：`NOT_IMPLEMENTED`（未实现）。
- public marketdata readiness 不等于 trading authorization。

本文件只规划 O-5 manual public outbound smoke 的安全执行方案，不执行真实 HTTP，不新增 runner，不改代码，不改 CI，不新增 API，不新增 migration。

## 2. O-5 定位

O-5 是 manual public outbound smoke，中文定位为“手动公开行情只读外联 smoke”。

O-5 只允许验证公开行情只读 endpoint 的最小外联能力，且必须满足：

- 只允许 public REST / readonly marketdata。
- 只允许无 credential、无签名、无私有权限的公开行情路径。
- 只允许手动 profile 与显式 feature flag。
- 只允许人工触发，不进入默认 Maven、默认 CI、默认本地启动或自动化 release 流程。
- 只证明 public endpoint 可达、policy allowlist 生效、denylist fail-closed、结果可脱敏记录。

O-5 不是：

- 默认 CI。
- private trading。
- permission probe。
- LIVE readiness。
- trading authorization。
- provider-ready 或 RealClient-ready。
- API key validation。
- account / balance / order / cancel / amend / position / wallet / transfer / withdraw / deposit / subaccount 检查。

## 3. 本轮范围

本轮只做：

- 规划 O-5 smoke 前置条件。
- 规划 public endpoint allowlist 和 private endpoint denylist。
- 规划 profile / feature flag / env gate。
- 规划手动执行命令草案、预期输出、证据采集和回滚方式。
- 规划失败处理、降级策略、O-5 review / freeze 路线。
- 同步 GateO 当前状态文档。

本轮明确不做：

- 不执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken HTTP。
- 不运行 O-5 smoke。
- 不修改 `backend/**`、`frontend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**` 或 migration。
- 不新增 API、migration、测试代码、CI workflow、manual runner 或脚本。
- 不读取 `.env`、key、pem 或 credential material。
- 不开启 LIVE，不接 AI，不接 DH runtime。
- 不实现 RealClient、real provider、真实 permission probe、signed request 或 private WebSocket。
- 不把 public marketdata readiness 写成 trading authorization。

## 4. 前置条件

O-5B execution 不得开始，除非 O-5A review 逐项确认以下条件：

| 前置条件 | 必须状态 | 说明 |
| --- | --- | --- |
| O-1 guard | `PASS / ACCEPTED / FROZEN` | `PublicMarketDataOutboundPolicy`、manual profile、feature flag、denylist、redaction、bounded retry 已冻结。 |
| O-2 Data Quality | `PASS / ACCEPTED / FROZEN` | O-5 结果只能作为 Data Quality 后续决策输入，不能直接写成已落地 `PUBLIC_OUTBOUND`。 |
| O-3 readiness API | `FROZEN / ACCEPTED` | `GET /api/marketdata/readiness` 仍是 DB-only / no-egress / diagnostic-only。 |
| O-4 UI | `FROZEN / ACCEPTED` | `/marketdata` UI 仍只消费 readiness API，不消费 O-5 endpoint。 |
| 工作区 | clean | 执行前必须 `git status --short` 为空。 |
| 分支 | `dev` | 执行前必须 `git branch --show-current` 返回 `dev`。 |
| 默认 profile | no-egress | 默认 local/test/CI 不启用 outbound。 |
| 手动 profile | 显式启用 | 仅允许 `public-marketdata-manual`。 |
| feature flag | 显式启用 | 仅允许 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true`。 |
| credential | absent | 不读取、不要求、不校验、不打印 credential。 |
| signed request | forbidden | 任何签名请求直接拒绝。 |
| private endpoint | forbidden | allowlist/denylist review 通过后才允许执行。 |

执行前人工声明必须包含：

```text
this is public-readonly smoke
no credential
no private endpoint
no trading side effect
LIVE disabled
AI not started
DH runtime not integrated
```

## 5. Profile / Feature Flag 计划

沿用 O-1 既有约定：

```text
spring.profiles.active=public-marketdata-manual
NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true
```

默认配置必须保持关闭：

```text
NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=false
NQ_PUBLIC_MARKETDATA_BASE_URL=http://127.0.0.1:0
```

相关安全 gate：

- `NQ_LIVE_ENABLED=false`
- `NQ_AI_ENABLED=false`
- `NQ_DH_RUNTIME_ENABLED=false`
- `NQ_REAL_PROVIDER_ENABLED=false`
- `NQ_REAL_CLIENT_ENABLED=false`
- `NQ_REAL_EXCHANGE_ENABLED=false`

CI 规则：

- 默认 CI 不得设置 `spring.profiles.active=public-marketdata-manual`。
- 默认 CI 不得设置 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true`。
- O-5 smoke 只能使用人工执行记录，不能混入默认 `mvn test`、frontend E2E、Python pytest 或 release workflow。

## 6. Public Endpoint Allowlist 计划

O-5 只允许来自 O-1 已审查官方文档 / allowlist 的 public marketdata endpoint。不得临时新增未审查 endpoint，不得从第三方 SDK、博客或历史经验推断 endpoint。

最小 allowlist 继承 O-1 `PublicMarketDataEndpointCategory`：

| Category | O-5 是否允许 | 说明 |
| --- | --- | --- |
| `SERVER_TIME` | 允许 | 公开 server time，用于最小连通性和延迟观测。 |
| `INSTRUMENTS` | 允许 | 公开 instrument / symbol metadata。 |
| `TICKER` | 允许 | 公开 ticker snapshot。 |
| `OHLCV` | 允许 | 公开 kline / candlestick / OHLCV。 |
| `ORDER_BOOK` | 不纳入本轮 | 虽可能为 public endpoint，但 O-1 默认为后置，不得在 O-5B 默认带入。 |
| `RECENT_TRADES` | 不纳入本轮 | 虽可能为 public endpoint，但 O-1 默认为后置。 |
| `PUBLIC_WEBSOCKET` | 不纳入本轮 | O-5 只规划 public REST，不规划 WebSocket。 |

候选 endpoint family 必须保持 public readonly：

- OKX public server time / instruments / ticker / candles。
- Binance public server time / exchangeInfo / ticker / klines。
- exchange status 仅在 O-1 allowlist 和官方文档确认无需 auth 时可进入 O-5A review；当前不得绕过 category 约束新增。

## 7. Private Endpoint Denylist 计划

O-5 denylist 采用 fail-closed：任何未列入 allowlist 的 endpoint 默认禁止，任何需要 credential / signature / API key / secret / passphrase 的 endpoint 自动禁止。

明确禁止：

- account。
- balance。
- order。
- cancel。
- amend。
- position / positions。
- wallet。
- transfer。
- withdraw。
- deposit。
- subaccount。
- margin / leverage / loan。
- private WebSocket。
- signed request。
- permission probe。
- API key validation。
- passphrase validation。
- order preview that calls private API。
- user data stream / listenKey。
- any endpoint requiring API key / secret / passphrase / signature。

O-5B 执行前必须用 O-1 policy 和人工 review 双重确认：即使 endpoint 文档声称 public，只要 path / query / category 命中 denylist token，也必须拒绝。

## 8. Smoke 执行计划草案

本节只规划，不执行。

### 8.1 执行环境

- 本地开发机或受控测试机。
- 分支：`dev`。
- 工作区：clean。
- profile：`public-marketdata-manual`。
- feature flag：`NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true`。
- LIVE / AI / DH / real provider / RealClient / real exchange：全部显式 false。
- credential：不得配置、不得读取、不得打印。

### 8.2 命令草案

当前仓库已存在 O-1 client、policy、profile 与配置，但本轮未确认独立 O-5B manual smoke runner 是否存在。因此下列命令是 O-5B review 前的命令形态草案，不是本轮可执行承诺。

PowerShell 草案：

```powershell
$env:SPRING_PROFILES_ACTIVE = "public-marketdata-manual"
$env:NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED = "true"
$env:NQ_LIVE_ENABLED = "false"
$env:NQ_AI_ENABLED = "false"
$env:NQ_DH_RUNTIME_ENABLED = "false"
$env:NQ_REAL_PROVIDER_ENABLED = "false"
$env:NQ_REAL_CLIENT_ENABLED = "false"
$env:NQ_REAL_EXCHANGE_ENABLED = "false"
$env:NQ_PUBLIC_MARKETDATA_BASE_URL = "<official-public-base-url-reviewed-in-O-5A>"

mvn -f backend/pom.xml -pl nq-app -am `
  "-Dtest=<O5ManualPublicMarketDataOutboundSmokeRunnerOrTest>" `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  test
```

O-5A 必须先决定 `<O5ManualPublicMarketDataOutboundSmokeRunnerOrTest>` 是否已有可复用入口；若没有，不得在 O-5B 假装已可执行，必须另起最小 runner 设计审查或改为人工工具化步骤。

### 8.3 预期输出

成功输出必须只包含脱敏摘要：

- provider。
- endpoint category。
- symbol / instrument。
- response status。
- latency。
- dataOrigin decision。
- no credential used。
- no signed request。
- no private endpoint。
- no trading side effect。

失败输出必须只包含脱敏错误：

- provider。
- endpoint category。
- sanitized error category。
- status code 或 timeout category。
- latency。
- retry count。
- redacted error。

不得输出 raw response body、raw headers、完整 URL query string、credential、API key、secret、passphrase、token 或签名串。

### 8.4 No-Write / DB 边界

当前 O-5 plan 不允许新增 DB 写入。若 O-5B 使用现有 O-1 client 执行 smoke，默认应为 no-write，仅采集文本证据与脱敏 summary。

如果后续提出写 audit / summary，必须先单独 review：

- 写入表是否已存在。
- 是否无需 migration。
- 是否只写脱敏字段。
- 是否有 traceId / requestId。
- 是否不会写 raw response / headers / query / credential。
- 是否不会把 smoke success 写成 readiness API trading-ready。

在没有单独 review 前，本计划按 no-write 执行。

## 9. Evidence / Audit 计划

O-5B 证据记录必须包含：

- run started at。
- run finished at。
- endpoint category。
- provider。
- symbol / instrument。
- response status。
- latency。
- retry count。
- redacted error。
- dataOrigin。
- no credential used。
- no signed request。
- no private endpoint。
- no trading side effect。
- LIVE disabled。
- manual profile and feature flag confirmed。

禁止记录：

- raw response body。
- raw headers。
- full URL query string。
- credential。
- API key。
- secret。
- passphrase。
- token。
- signature。
- cookie。
- private key。
- raw provider payload。

证据保存方式：

- O-5B 只允许保存 text-only redacted summary。
- 默认位置建议为 `docs/current/WORKLOG.md` 摘要和后续 O-5C review 文档。
- 如需 artifacts，必须放入仓库可追踪目录并先审查文件内容；不得写入 `logs/`、临时目录、用户桌面、下载目录或凭证目录。

## 10. DataQuality / Readiness 后续接线

O-5 smoke 成功后，不得自动更新 `DataQualitySummary` 或 readiness API 语义。

是否引入 `PUBLIC_OUTBOUND` 作为 `DataOrigin` 必须单独进入 O-5D review。当前规则：

- O-5 plan 不得把 `PUBLIC_OUTBOUND` 写成已落地事实。
- O-2 / O-3 当前 `dataOrigin` 仍以 `LOCAL_DB / FIXTURE / FAKE_SERVER / PUBLIC_CANDIDATE / UNKNOWN` 为诊断语义。
- readiness API 不得因为 smoke 成功而显示 trading-ready、LIVE-ready、permission-granted、provider-ready 或 private trading ready。
- O-5C 只能复核 smoke 结果，不能直接改 API / DTO / DB / UI。

## 11. 测试与验证计划

默认 test 仍必须 no-egress：

- `mvn -f backend/pom.xml test` 不得执行真实 public outbound。
- `npm run build` / `npm run test:e2e` 不得执行真实 public outbound。
- 默认 CI 不得执行 O-5 smoke。

O-5 smoke 必须使用单独手动命令，且结果只能由 O-5C review 引用。失败时只能进入 O-5-FIX 或 O-5B re-run review，不得扩大到 private trading、credential、permission probe、LIVE 或 real provider。

本轮 docs-only 验证命令：

```powershell
git status --short
git branch --show-current
git log --oneline -5
git diff --check
git diff --stat
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- "backend/**/db/migration"
```

## 12. 失败处理与降级策略

O-5B 执行失败时：

- `403 / 401`：视为 endpoint 或 auth 边界异常，停止执行并进入 review；不得补 credential。
- `429`：记录 `RATE_LIMITED`，停止或按 O-1 bounded retry 上限执行；不得提高 retry 上限。
- `5xx`：记录 `TEMPORARY_FAILURE`，不得把 stale / empty / fallback 写成 success。
- timeout：记录 `TIMEOUT`，不得无限重试。
- policy denied：记录 `POLICY_DENIED`，不得绕过 policy。
- malformed response：记录 `INVALID_RESPONSE`，不得保存 raw body。

降级策略：

- 关闭 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED`。
- 退出 `public-marketdata-manual` profile。
- 回到 DB-only / local facts / fixture / no-egress diagnostic。
- 不触发交易、不触发 permission probe、不访问 credential、不写 LIVE 状态。

## 13. O-5 后续路线

O-5 必须拆分，不得从 plan 直接跳到 O-FREEZE：

| Batch | 状态 | 目标 |
| --- | --- | --- |
| O-5A | `NOT STARTED` | manual public outbound smoke plan review。 |
| O-5B | `NOT STARTED` | manual public outbound smoke execution。 |
| O-5C | `NOT STARTED` | first smoke result review。 |
| O-5D | `NOT STARTED` | DataOrigin / `PUBLIC_OUTBOUND` decision review。 |
| O-5E | `NOT STARTED` | O-5 freeze review。 |
| O-FREEZE | `NOT STARTED` | GateO freeze。 |

O-5A review may start only if this plan has P0/P1=0 and current docs sync is clean.

## 14. Security / No-Trading Boundary

O-5 保持以下边界：

- no credential。
- no signed request。
- no private endpoint。
- no private WebSocket。
- no account / balance / order / cancel / amend / position / wallet / transfer / withdraw / deposit / subaccount。
- no API key validation。
- no permission probe。
- no order preview that calls private API。
- no LIVE。
- no AI。
- no DH runtime。
- no RealClient。
- no real provider / private trading adapter。
- no trading side effect。

public marketdata readiness 只能表示行情数据诊断，不得表示交易授权。

## 15. Findings

### P0

无。

### P1

无。

### P2

1. 当前计划未确认 O-5B 是否已有独立 manual smoke runner；O-5A 必须先绑定可审查执行入口，否则 O-5B 不得开始。
2. 是否引入 `PUBLIC_OUTBOUND` 作为 `DataOrigin` 仍需 O-5D 单独 review；本计划不把它写成当前事实。

### P3

1. current docs 中历史任务较多，入口需要保持聚焦，避免 O-5 plan 与 O-5 execution 状态混淆。

## 16. Final Decision

O-5 plan：`COMPLETED / PLAN ONLY / NOT IMPLEMENTED`。

O-5 execution：`NOT STARTED`。

O-FREEZE：`NOT STARTED`。

GateO stage：`NOT COMPLETED`。

是否允许执行 O-5B：否。必须先进入 O-5A manual public outbound smoke plan review。

是否允许 O-5A review start：允许，前提是本轮 diff 验证显示只修改允许文档，且 P0/P1=0。

推荐下一步：

```text
NQ-GATEO-O5A-MANUAL-PUBLIC-OUTBOUND-SMOKE-PLAN-REVIEW
```

Commit recommendation：

```text
docs(gateo): plan manual public outbound smoke
```
