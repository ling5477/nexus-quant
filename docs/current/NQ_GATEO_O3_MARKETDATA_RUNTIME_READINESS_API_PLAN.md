# NQ-GATEO-O3-MARKETDATA-RUNTIME-READINESS-API-PLAN

## 1. 当前状态

任务名称：`NQ-GATEO-O3-MARKETDATA-RUNTIME-READINESS-API-PLAN`。

任务归属：NQ-only。

任务类型：`MARKETDATA_API_PLANNING` / `DATA_QUALITY_READINESS_MODELING` / `SECURITY_BOUNDARY_REVIEW` / `READ_ONLY_API_IMPLEMENTATION` / `DOCUMENTATION`。

原 O-3 planning 结论：`PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

O-3B backend implementation 当前结论：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自查）/ `READY TO COMMIT`（可进入提交前复核）。

GateO 当前状态：

- O-1 controlled public outbound guard：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- O-2 Data Quality Center：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- O-3 MarketData Runtime Readiness API：planning-only baseline 已完成；O-3B backend read-only API implementation 已完成为 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`；O-3E freeze review 仍 `NOT STARTED`（未开始）。
- O-4 MarketData Quality UI：`NOT STARTED`（未开始）。
- O-5 manual public outbound smoke：`NOT STARTED`（未开始）。
- GateO stage：`NOT COMPLETED`（未完成）。
- LIVE：`DISABLED`（已禁用）。
- AI：`NOT STARTED`（未启动）。
- DH runtime：`NOT_INTEGRATED`（未集成）。
- RealClient / real provider / real permission probe：`NOT_IMPLEMENTED`（未实现）。

本文件保留 O-3 planning baseline，并记录 O-3B backend read-only API implementation 的当前事实。O-3B 只扩展既有 `GET /api/marketdata/readiness` read model，不新增重复 endpoint，不新增 migration，不改 frontend，不执行真实 public outbound，不读取 credential。

### 1.1 O-3B implementation update（2026-07-03）

任务名称：`NQ-GATEO-O3B-MARKETDATA-READINESS-READ-ONLY-API-IMPLEMENTATION`。

实现范围：

- 扩展 `MarketdataReadinessSummary` 与 `MarketdataReadinessResponse`，保留旧字段并追加 `sourceCode / exchange / timeframe / dataOrigin / sourceStatus / sourceHealth / gapStatus / lastObservedAt / latencyMs / errorRate / errorCategory / missingFrom / missingTo / staleAfterSeconds / degradedReason / disabledReason / traceId / requestId / updatedAt`。
- 在 `MarketdataReadinessService` 内用本地 `marketdata_bars` 与 `marketdata_ingestion_jobs/runs` facts 做 fail-closed 映射；`dataOrigin` 当前为 `LOCAL_DB`，`sourceCode` 为本地诊断 source key。
- `exchange` 是 `exchangeCode` 的 alias，`timeframe` 是 `interval` 的 alias；alias 只用于展示兼容，不表示 provider、LIVE、permission probe 或 trading readiness。
- `errorRate`、`missingFrom`、`missingTo`、`traceId`、`requestId` 在当前缺少稳定事实时返回 `null`，不得伪造。
- 补充 core service tests、controller JSON contract / no-side-effect test、DTO enum vocabulary test。

边界保持：

- 未新增 `/api/marketdata/readiness/sources`、`/api/marketdata/readiness/gaps`、`/api/marketdata/readiness/quality/overview`。
- 未新增 migration、frontend、research、scripts、deploy、`.github` 或 CI workflow。
- 未调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken HTTP，未读取 credential，未触发 adapter、public outbound、private trading、permission probe、RealClient、real provider、LIVE、AI 或 DH runtime。
- Response 不得包含 `tradingAuthorized`、`liveReady`、`privateTradingReady`、`permissionGranted`、`realProviderReady`、`apiKey`、`secret`、`passphrase`、`credentialRef`、`rawRequest`、`rawResponse`、`rawHeaders`、`fullQueryString`、`encrypted_payload`、`decrypted_payload`。

## 2. 目标

O-3 的目标是把 O-2 Data Quality Center 的纯模型和规则接入现有 MarketData readiness API read model 的方案定清楚，形成后续最小实现批次和验收边界。

必须回答的问题：

1. 当前是否已有 `/api/marketdata/readiness` 或类似 endpoint。
2. 当前 `MarketdataReadiness*` 模型与 O-2 `DataQualitySummary` 的关系。
3. 应扩展现有 API，还是新增最小 API。
4. API response 如何表达 `source_status` / `source_health` / `freshness_status` / `gap_status` / `data_origin`。
5. API 是否只读。
6. API 是否不得表达 trading authorization。
7. API 是否不得触发 public outbound。
8. API 是否不得读取 credential。
9. API 是否不得接 private trading / permission probe。
10. O-3 implementation 应拆成哪些小批次。

## 3. 非目标

本轮不做：

- 不修改 `backend/**`、`frontend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**`。
- 不新增 API 实现，不新增 controller/service/repository/DTO 代码。
- 不新增 migration，不修改历史 migration。
- 不修改测试代码，不修改 CI workflow。
- 不调用 OKX / Binance / Bybit / Gate / Coinbase / Kraken 真实 HTTP。
- 不执行 O-5 manual public outbound smoke。
- 不读取 `.env`、key、pem 或 credential material。
- 不开启 LIVE，不接 AI，不接 DH runtime。
- 不实现 RealClient、real provider、真实 permission probe、signed request 或 private WebSocket。
- 不访问 account / balance / order / cancel / amend / positions / wallet / transfer / withdraw / deposit / subaccount endpoint。
- 不把 public marketdata readiness 写成 trading authorization。
- 不混入 NQ-DH Integration-1 P2 fixtures plan。

## 4. 已有 API 与模型盘点

### 4.1 当前 endpoint

当前已有 `GET /api/marketdata/readiness`。

代码事实：

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataController.java` 已定义 `@GetMapping("/readiness")`。
- 当前 query 包含 `exchangeCode`、`marketType`、`symbol` 或 `instrumentId`、`interval`、`from`、`to`。
- 当前 response DTO 为 `MarketdataReadinessResponse`。
- 当前 service 为 `MarketdataReadinessService`。
- 当前 repository 为 `MarketdataReadinessRepository` / `JdbcMarketdataReadinessRepository`。
- 当前 API 文档记录在 `docs/current/API.md` 的 GateM-2E Marketdata Readiness API。

结论：O-3 不应重复造主 endpoint。优先扩展现有 `GET /api/marketdata/readiness` read model。

### 4.2 当前 `MarketdataReadiness*` 模型

当前 `MarketdataReadinessSummary` 字段：

- `exchangeCode`
- `marketType`
- `instrumentId`
- `symbol`
- `interval`
- `status`
- `freshnessStatus`
- `sourceHealthStatus`
- `sourceHealthReason`
- `qualityStatusSummary`
- `barCount`
- `firstBarTime`
- `lastBarTime`
- `expectedBarCount`
- `gapCount`
- `unknownQualityCount`
- `lastSuccessAt`
- `lastFailureAt`
- `backendSupportLevel`
- `generatedAt`

当前 `MarketdataReadinessService` 只基于本地 DB facts 聚合：

- `marketdata_bars`
- `marketdata_ingestion_jobs`
- `marketdata_ingestion_runs`

当前模型优势：

- 已经是只读 read model。
- 已经 fail-closed 区分 `NO_DATA`、`UNKNOWN`、`STALE`、`GAP`、`ERROR`、`DISABLED`。
- 已经明确 `backendSupportLevel=NO_MIGRATION_MVP`，表示本地 DB no-migration MVP，不代表真实交易所 source health 全量完成。
- controller test 已断言 readiness response 不包含 `apiKey`、`secret`、`passphrase`，且不调用 bars / ingestion ports。

当前模型缺口：

- 没有直接消费 O-2 `DataQualitySummary`。
- 没有 `dataOrigin`。
- 没有 O-2 语义中的 `sourceStatus`、`sourceHealth` 独立字段。
- 没有 `latencyMs`、`errorRate`、`errorCategory`、`missingFrom`、`missingTo`、`staleAfterSeconds`、`degradedReason`、`disabledReason`、`traceId`、`requestId`、`updatedAt`。
- `sourceHealthStatus` 目前复用 overall `status`，不能完整表达 O-2 `SourceHealth`。

### 4.3 O-2 `DataQualitySummary` 模型

O-2 `DataQualitySummary` 字段：

- `sourceCode`
- `exchange`
- `symbol`
- `timeframe`
- `dataOrigin`
- `sourceStatus`
- `sourceHealth`
- `freshnessStatus`
- `gapStatus`
- `lastSuccessAt`
- `lastFailureAt`
- `latencyMs`
- `errorCategory`
- `gapCount`
- `degradedReason`
- `disabledReason`
- `traceId`
- `requestId`

O-2 enums：

- `DataOrigin`: `LOCAL_DB` / `FIXTURE` / `FAKE_SERVER` / `PUBLIC_CANDIDATE` / `UNKNOWN`
- `SourceStatus`: `ENABLED` / `DISABLED` / `DEGRADED` / `ERROR` / `RATE_LIMITED`
- `SourceHealth`: `HEALTHY` / `DEGRADED` / `RATE_LIMITED` / `TIMEOUT` / `ERROR` / `UNKNOWN`
- `FreshnessStatus`: `FRESH` / `STALE` / `VERY_STALE` / `NO_DATA` / `ERROR` / `DISABLED`
- `GapStatus`: `NONE` / `GAP` / `PARTIAL` / `UNKNOWN`
- `ErrorCategory`: `NONE` / `DISABLED` / `POLICY_DENIED` / `RATE_LIMITED` / `TIMEOUT` / `TEMPORARY_FAILURE` / `INVALID_RESPONSE` / `STALE` / `GAP` / `TRANSPORT_ERROR` / `UNKNOWN`

关键边界：

- O-2 是后端纯模型和规则 baseline，不是 API。
- O-2 不连接真实交易所，不读取 credential，不新增 migration。
- O-2 不包含 trading authorization 字段。
- O-2 将 O-1 `PUBLIC_OUTBOUND` 兼容降级映射为 `PUBLIC_CANDIDATE`，不表示真实 public outbound 已执行。

## 5. API 决策

### 5.1 主决策

O-3 应扩展现有 `GET /api/marketdata/readiness`，不新增重复主 endpoint。

理由：

- 现有 API 已承担 MarketData readiness read model 职责。
- 现有 API 已被前端 `/marketdata` Data Quality / Readiness 区域消费。
- 现有 API 已有 DB-only / no-egress / no-credential / diagnostic-only 边界。
- 新增重复主 endpoint 会造成 readiness 语义分裂，增加 O-4 UI 对接风险。

### 5.2 候选 endpoint 处理

| Candidate | O-3 决策 | 说明 |
| --- | --- | --- |
| `GET /api/marketdata/readiness` | `PRIMARY_EXTEND_EXISTING` | O-3 主入口，扩展 read model。 |
| `GET /api/marketdata/readiness/sources` | `DEFER / OPTIONAL_LATER` | 只有当 O-3B 证明多 source 列表必须独立分页/筛选时，才进入后续 contract review。 |
| `GET /api/marketdata/readiness/gaps` | `DEFER / OPTIONAL_LATER` | 只有当 gap detail 超出 readiness summary 且需要分页明细时，才进入后续 contract review。 |
| `GET /api/marketdata/readiness/quality/overview` | `DEFER / OPTIONAL_LATER` | 可作为 O-4 UI 聚合需求输入，但不得在本轮写成当前 API。 |

### 5.3 兼容策略

O-3B 后续实现必须保持向后兼容：

- 保留当前 `MarketdataReadinessResponse` 既有字段。
- 新增字段只能追加，不能重命名或删除现有字段。
- 新增 enum 值必须 fail-closed；前端未知值不得显示 ready。
- `backendSupportLevel=NO_MIGRATION_MVP` 保留，新增 `readModelVersion` 或 `dataQualitySupportLevel` 可作为可选字段，但不得把本地 DB readiness 写成真实 provider readiness。

## 6. O-2 到 O-3 read model 映射

### 6.1 字段映射建议

| O-3 API field | 来源 | 规则 |
| --- | --- | --- |
| `sourceCode` | O-2 `DataQualitySummary.sourceCode` 或 O-3 本地派生 | 建议格式为 `<exchange>_<marketType>_<symbol>_<timeframe>`；只用于诊断。 |
| `exchange` | O-2 `exchange` / query `exchangeCode` | 响应可同时保留现有 `exchangeCode`，新增 `exchange` 作为 source 语义别名需明确兼容策略。 |
| `symbol` | 现有 query / O-2 `symbol` | 保留现有语义。 |
| `timeframe` | O-2 `timeframe` / 现有 `interval` | 保留 `interval`，可新增 `timeframe` 或文档声明二者同义。 |
| `dataOrigin` | O-2 `DataOrigin` | 初始只允许 `LOCAL_DB`、`FIXTURE`、`FAKE_SERVER`、`PUBLIC_CANDIDATE`、`UNKNOWN`。 |
| `sourceStatus` | O-2 `SourceStatus` | 表示 source 开关与诊断状态，不表示交易授权。 |
| `sourceHealth` | O-2 `SourceHealth` | 表示行情源健康诊断，不表示 provider ready。 |
| `freshnessStatus` | O-2 `FreshnessStatus` + 当前 freshness | 保留现有 `freshnessStatus` 字段，扩展 enum 映射。 |
| `gapStatus` | O-2 `GapStatus` + 当前 `gapCount` | 新增字段，避免只靠数字解释缺口状态。 |
| `lastSuccessAt` | 当前 ingestion facts / O-2 | 保留现有字段。 |
| `lastFailureAt` | 当前 ingestion facts / O-2 | 保留现有字段。 |
| `lastObservedAt` | 当前 bars / ingestion facts | 建议取 max(`lastBarTime`, `lastSuccessAt`, `lastFailureAt`)；无证据为 null。 |
| `latencyMs` | 当前 latest run latency / O-2 | 可从现有 `MarketdataReadinessIngestionFacts.latestLatencyMs` 或 O-2 result 派生。 |
| `errorRate` | 后续 source facts | O-3B 若没有稳定窗口事实，先不实现或返回 null，不伪造。 |
| `errorCategory` | O-2 `ErrorCategory` | 统一错误分类，不包含 raw provider payload。 |
| `gapCount` | 当前 `gapCount` / O-2 | 保留现有字段。 |
| `missingFrom` | gap detail 派生 | 若 O-3B 无法从现有表稳定派生，先返回 null。 |
| `missingTo` | gap detail 派生 | 若 O-3B 无法从现有表稳定派生，先返回 null。 |
| `staleAfterSeconds` | O-2 freshness rule / 当前 threshold | 使用配置或规则集中定义，不硬编码在 UI。 |
| `degradedReason` | O-2 `degradedReason` / 当前 reason | 只允许脱敏诊断文本。 |
| `disabledReason` | O-2 `disabledReason` | disabled source 时填写脱敏原因。 |
| `traceId` | request trace | 来自当前 request trace，不携带敏感信息。 |
| `requestId` | request id | 若没有 request id，O-3B 可生成本地 diagnostic id；不得复用 credential/probe id。 |
| `updatedAt` | read model generated time | 建议取当前 `generatedAt` 语义；保留 `generatedAt` 兼容。 |

### 6.2 状态映射建议

| 当前 readiness status | O-3 `sourceStatus` | O-3 `sourceHealth` | O-3 `freshnessStatus` | O-3 `gapStatus` | 说明 |
| --- | --- | --- | --- | --- | --- |
| `FRESH` | `ENABLED` | `HEALTHY` | `FRESH` | `NONE` | 仅表示本地 read model fresh，不代表交易授权。 |
| `STALE` | `DEGRADED` | `DEGRADED` | `STALE` | `UNKNOWN` 或 `NONE` | 以 freshness 证据为主。 |
| `GAP` | `DEGRADED` | `DEGRADED` | 当前 freshness | `GAP` | gap 证据优先 fail-closed。 |
| `ERROR` | `ERROR` | `ERROR` | `ERROR` | `UNKNOWN` 或 `GAP` | 若错误来自 gap，则保留 `GAP`。 |
| `DISABLED` | `DISABLED` | `UNKNOWN` | `DISABLED` | `UNKNOWN` | disabled 不得写成 healthy。 |
| `UNKNOWN` | `DEGRADED` | `UNKNOWN` | `NO_DATA` 或 `ERROR` | `UNKNOWN` | 证据不足，不得显示 ready。 |
| `NO_DATA` | `DEGRADED` | `UNKNOWN` | `NO_DATA` | `UNKNOWN` | no data 不得显示 ready。 |

### 6.3 `dataOrigin` 规则

O-3 response 允许的 `dataOrigin`：

- `LOCAL_DB`
- `FIXTURE`
- `FAKE_SERVER`
- `PUBLIC_CANDIDATE`
- `UNKNOWN`

O-3 不引入 `PUBLIC_OUTBOUND` 作为已落地事实。真实 public outbound origin 是否需要新增枚举，必须留到 O-5 前单独审查。

`PUBLIC_CANDIDATE` 只表示 O-1/O-2 映射中的候选语义或兼容降级，不证明真实 public outbound 已执行。

## 7. 明确禁止响应字段

O-3 response 不得包含：

- `tradingAuthorized`
- `liveReady`
- `privateTradingReady`
- `permissionGranted`
- `realProviderReady`
- `apiKey`
- `secret`
- `passphrase`
- `credentialRef`
- `rawRequest`
- `rawResponse`
- `rawHeaders`
- `fullQueryString`

任何需要表达交易、权限、credential 或 private provider 的需求，均不属于 O-3 MarketData Runtime Readiness API。

## 8. 安全边界

O-3 API 必须满足：

- 只读：只查询 read model，不写库，不触发状态机。
- 不触发 public outbound：不得调用 O-1 outbound client，不发真实 HTTP，不执行 O-5 smoke。
- 不读取 credential：不得读取 `.env`、credential table material、process env secret 或 credential service。
- 不接 private trading：不得访问 order / balance / position / wallet / transfer / withdraw / account endpoint。
- 不接 permission probe：不得调用 credential permission probe port 或 latest probe write path。
- 不启用 LIVE：LIVE disabled 不影响 read-only readiness 查询，但 API 不得返回 LIVE-ready。
- 不接 AI / DH runtime：DH not integrated 不影响 read-only readiness 查询，但 API 不得返回 DH-connected。
- 不暴露 raw payload：response/log/test artifact 不得包含 raw request、raw response、raw headers、full query string 或 provider payload。
- public marketdata readiness 只表示 diagnostic，不等于 trading authorization。

## 9. 测试规划

O-3B/O-3C 后续实现测试必须覆盖：

- read-only endpoint 不触发 outbound。
- 不读取 credential。
- 不返回 trading authorization。
- `no data -> NO_DATA`。
- disabled source -> `DISABLED`。
- stale source -> `STALE`。
- gap source -> `GAP`。
- source error -> `ERROR`。
- fallback origin -> `LOCAL_DB` / `FIXTURE` / `FAKE_SERVER`。
- O-2 mapper 结果能被 API read model 消费。
- LIVE disabled 不影响 readiness read-only 查询。
- DH runtime not integrated 不影响 readiness read-only 查询。
- response 不包含 `tradingAuthorized`、`liveReady`、`privateTradingReady`、`permissionGranted`、`realProviderReady`、credential 或 raw payload 字段。

建议验证命令：

```powershell
mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra,nq-app,nq-adapter-api -am "-Dtest=*MarketdataReadiness*,*DataQuality*" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -f backend/pom.xml test
```

如果 O-3B 只追加 DTO/service read model 且不改 frontend，可不运行 frontend build / Playwright；如果同步 O-4 UI，则必须另起 O-4 并运行前端验证。

## 10. O-3 implementation 批次

| Batch | 名称 | 当前状态 | 目标 | 允许范围 | 禁止范围 | 成功标准 |
| --- | --- | --- | --- | --- | --- | --- |
| O-3A | API read model contract + DTO plan review | `CONSUMED BY O-3B`（已由 O-3B 消费） | 冻结字段、enum、兼容策略和测试矩阵 | docs/current plan/review | 不写代码 | 字段、enum、兼容策略已在 O-3B implementation 中落地并由测试覆盖 |
| O-3B | backend read-only endpoint implementation | `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自查 / 可进入提交前复核） | 扩展现有 `/api/marketdata/readiness` read model | `backend/nq-api`、`backend/nq-core`、必要 `backend/nq-adapter-api` 测试引用 | 不新增 migration、不新增真实 HTTP、不读 credential | scoped Maven 与后端全量 Maven 通过，response 兼容 |
| O-3C | controller/service tests | `COVERED IN O-3B`（已随 O-3B 覆盖） | 补齐 no-outbound、no-credential、no-authorization、status mapping 回归 | backend test | 不触发真实外联 | tests 覆盖 no-side-effect、forbidden fields、状态映射与 null stable-fact 语义 |
| O-3D | docs/API sync | `CURRENT DOCS SYNCED`（当前文档已同步） | 只把已实现的真实 endpoint 写入 `API.md` 和 current docs | docs/current | 不把计划写成 freeze 或 GateO completed | docs 与代码事实一致 |
| O-3E | O-3 freeze review | `NOT STARTED`（未开始） | 冻结 O-3 read-only API baseline | docs/current / freeze review | 不新增功能 | P0/P1=0 后才可作为 freeze evidence；GateO 仍未完成 |

## 11. 风险分级

### P0

当前 P0：0。

P0 触发条件：

- O-3 规划允许真实交易所调用。
- O-3 规划允许 credential 读取。
- O-3 规划把 readiness 写成 trading authorization。
- O-3 规划混入 private trading / permission probe / LIVE。
- O-3 规划混入 NQ-DH Integration runtime。

### P1

当前 P1：0。

P1 触发条件：

- API 与 O-2 `DataQualitySummary` 关系不清。
- 新旧 readiness model 冲突。
- `DataOrigin` 语义误导。
- API 字段包含敏感信息或 raw payload。
- O-3/O-4/O-5 边界不清。

### P2

当前 P2：

1. `errorRate`、`missingFrom`、`missingTo` 当前无法从现有 DB facts 稳定派生；O-3B 已按计划返回 `null`，后续如需真实窗口指标必须另起 source facts / repository 设计，不得伪造。
2. 候选 `/sources`、`/gaps`、`/quality/overview` 继续后置，防止 API surface 膨胀。
3. O-3E freeze review 尚未执行；O-3B implemented 不等于 GateO completed。

### P3

当前 P3：

1. `exchangeCode` 与 `exchange`、`interval` 与 `timeframe` 以 alias 形式保留，后续 O-4 UI copy 需要避免把 alias 解释为 real provider readiness。
2. current docs 中 O-3 状态入口较多，后续 O-3E freeze 时需要集中同步，避免入口重复。

## 12. 回滚方式

本轮包含 backend read-only API implementation 与 docs sync。回滚方式：

```powershell
git restore --worktree -- backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/dto/MarketdataReadinessResponse.java backend/nq-api/src/test/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataControllerTest.java backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/MarketdataReadinessService.java backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataReadinessStatus.java backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataReadinessSummary.java backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/application/MarketdataReadinessServiceTest.java README.md docs/current/README.md docs/current/API.md docs/current/GATEO_PLAN.md docs/current/STATUS.md docs/current/ROADMAP.md docs/current/TESTING.md docs/current/WORKLOG.md docs/current/NQ_GATEO_O3_MARKETDATA_RUNTIME_READINESS_API_PLAN.md
```

新增文件回滚：先执行 `git status --short` 复核只包含本任务新增文件，再逐一删除 `backend/nq-api/src/test/java/com/guidinglight/nexusquant/marketdata/api/dto/MarketdataReadinessResponseTest.java`、`backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/MarketdataReadinessDataOrigin.java`、`MarketdataReadinessErrorCategory.java`、`MarketdataReadinessGapStatus.java`、`MarketdataReadinessSourceHealth.java`、`MarketdataReadinessSourceStatus.java`。不得使用批量 `git clean -fd` 处理本任务回滚。

如果已提交，使用普通 revert：

```powershell
git revert <commit>
```

不得使用 `git reset --hard` 或 `git clean -fd` 处理本任务回滚。

## 13. Final decision

O-3 planning-only baseline 结论：`PASS / PLAN ONLY / NOT IMPLEMENTED`。

O-3B backend implementation 结论：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`。

是否扩展现有 API：是。优先扩展现有 `GET /api/marketdata/readiness` read model。

是否新增最小 API：否。`/readiness/sources`、`/readiness/gaps`、`/readiness/quality/overview` 只作为后置候选，不是 O-3 当前事实。

是否只读：是。O-3 API 必须只读。

是否允许 trading authorization：否。

是否允许 public outbound：否。O-3 不触发真实 public outbound，O-5 才能单独规划手动 smoke。

是否允许 credential 读取：否。

是否允许 private trading / permission probe：否。

O-3B implementation 是否完成：已完成本地 DB read-only API 扩展、状态映射、controller/service/DTO 回归测试与 current docs sync。

推荐下一步：

```text
NQ-GATEO-O3E-MARKETDATA-READINESS-READ-ONLY-API-FREEZE-REVIEW
```

该下一步仍不得新增功能或执行 O-5 manual public outbound smoke，不得触碰 LIVE、AI、DH runtime、RealClient、real provider、real permission probe 或 private trading。
