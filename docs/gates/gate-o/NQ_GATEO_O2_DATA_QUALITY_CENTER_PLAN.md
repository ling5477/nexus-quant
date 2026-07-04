# NQ GateO O-2 Data Quality Center Baseline

任务：`NQ-GATEO-O2-DATA-QUALITY-CENTER-FREEZE-REVIEW`

冻结对象：`NQ-GATEO-O2-DATA-QUALITY-CENTER-IMPLEMENTATION`

结论：`PASS`（通过）/ `ACCEPTED`（已接受）/ `FROZEN`（已冻结）。

本文件记录 GateO O-2 Data Quality Center 的最小实现与冻结状态。O-2 只新增后端纯模型、mapper、freshness/gap/source health 规则和单元测试；不新增 API，不新增 migration，不改前端，不改 CI，不执行真实 public outbound smoke。

## 1. Current State

- O-1 controlled public outbound guard：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- O-2 Data Quality Center baseline：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- GateO stage：`NOT COMPLETED`（未完成）。
- O-3 / O-4 / O-5 / O-FREEZE：`PLANNED / NOT STARTED`（已规划 / 未开始）。
- O-5 manual real public smoke：`NOT STARTED`（未开始）。
- LIVE：`DISABLED`（已禁用）。
- AI：`NOT STARTED`（未启动）。
- DH runtime：`NOT_INTEGRATED`（未集成）。
- RealClient / real provider / real permission probe：`NOT_IMPLEMENTED`（未实现）。
- public marketdata readiness 只表示 diagnostic，不等于 trading authorization。

## 2. Scope

冻结基线范围：

- 新增 `backend/nq-adapter-api` 的 `dataquality` 纯模型和规则。
- 从 O-1 `PublicMarketDataOutboundResult` 映射到 O-2 `DataQualitySummary`。
- 实现 1m / 5m / 1h / 1d freshness baseline。
- 实现 expected candles vs actual candles gap rule。
- 新增 JUnit 单元测试覆盖 mapper、freshness、gap 和 no-trading-authorization 边界。
- 同步 current docs 状态和测试记录。

本轮未做：

- 未新增后端 API、DTO controller 或 HTTP route。
- 未新增或修改 Flyway migration。
- 未修改 frontend / research / scripts / deploy / `.github/workflows/**`。
- 未执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken HTTP。
- 未读取 `.env`、key、pem 或 credential material。
- 未开启 LIVE、AI、DH runtime、RealClient、real provider 或真实 permission probe。
- 未实现 signed request、private WebSocket、account / balance / order / cancel / amend / positions / wallet / transfer / withdraw / deposit / subaccount endpoint。

## 3. Implementation Summary

- `DataQualitySummary`：O-2 安全只读 summary，包含 `sourceCode`、`exchange`、`symbol`、`timeframe`、`dataOrigin`、`sourceStatus`、`sourceHealth`、`freshnessStatus`、`gapStatus`、`lastSuccessAt`、`lastFailureAt`、`latencyMs`、`errorCategory`、`gapCount`、`degradedReason`、`disabledReason`、`traceId` 和 `requestId`；不包含 trading authorization 字段。
- `DataQualitySourceHealthMapper`：把 O-1 result 映射成 O-2 source health / source status / freshness / gap / origin / error category。
- `DataQualityFreshnessRule`：集中维护 1m=3 分钟、5m=10 分钟、1h=2 小时、1d=2 天的 stale baseline；支持 `NO_DATA`、`DISABLED`、`ERROR`。
- `DataQualityGapRule`：基于 expected candles 与 actual candles 计算 `NONE / GAP / PARTIAL / UNKNOWN` 和 `gapCount`。
- `PUBLIC_OUTBOUND` 没有进入 O-2 `DataOrigin`；如兼容旧 O-1 enum 输入，会降级显示为 `PUBLIC_CANDIDATE`，不表示真实 public outbound 已执行。

## 4. Validation

| Command | Result | Scope |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-adapter-api,nq-app -am "-Dtest=*DataQuality*,*Freshness*,*Gap*,PublicMarketData*" "-Dsurefire.failIfNoSpecifiedTests=false" test` | `PASS / BUILD SUCCESS`（通过 / 构建通过） | O-2 Data Quality + O-1 PublicMarketData 窄口回归；`nq-adapter-api` 33 tests，`nq-app` 4 tests，0 failures / 0 errors / 0 skipped。 |
| `mvn -f backend/pom.xml test` | `PASS / BUILD SUCCESS`（通过 / 构建通过） | 后端 23 个 reactor module 全量回归；全部 `SUCCESS`。保留既有 SLF4J / Mockito dynamic agent / unchecked / deprecation warning，非阻断；`nq-app` 86 tests 中 2 skipped 为既有跳过项。 |

## 5. Boundary Confirmation

- O-2 mapper 只消费已脱敏 O-1 request/result record，不创建 HTTP client，不读取环境变量，不读取 credential。
- O-2 freshness/gap 规则是纯函数，不接 DB、不改 ingestion job、不做真实补洞。
- `DataQualitySummary` 不包含 raw request、raw response、raw headers、full query string、credential、signature、token、cookie 或交易授权字段。
- `DataQualitySummary` 不暴露 trading authorization 字段；测试通过 record component 断言防止字段回流。
- source health、freshness、gap、data origin 只能用于 public marketdata diagnostic，不能解释为 provider ready、LIVE ready 或 trading authorization。

## 6. P0 / P1 / P2 / P3 Findings

- P0：0。
- P1：0。
- P2：1，当前 O-2 只提供后端纯模型和规则，尚未接入现有 `/api/marketdata/readiness` read model；该接线应留到 O-3 API plan/review 后处理。
- P3：1，`NQ_GATEO_O2_DATA_QUALITY_CENTER_PLAN.md` 在本地仓库中此前不存在，本轮按附件要求创建为 current O-2 状态入口；后续 review 可决定是否保留为长期 current doc 或并入 `GATEO_PLAN.md`。

## 7. Freeze Review

Freeze verdict：`PASS / ACCEPTED`。

Frozen baseline：commit `4d659d72 feat(marketdata): add data quality center baseline` 中的 O-2 Data Quality Center baseline。冻结对象仅覆盖 `DataQualitySummary`、`DataQualitySourceHealthMapper`、`DataQualityFreshnessRule`、`DataQualityGapRule` 及 mapper / freshness / gap JUnit 测试；不新增功能、不改代码、不新增 API、不新增 migration、不执行真实 public outbound smoke。

Accepted evidence：

1. O-2 commit 存在：`4d659d72 feat(marketdata): add data quality center baseline`。
2. freeze review 前 `git status --short` 为空；`git diff --check` 与 `git diff --stat` 为空。
3. O-2 implementation review 已 `PASS`，允许进入 freeze review。
4. `DataQualitySummary` 不包含 credential、raw request、raw response、raw headers、full query string 或 trading authorization 字段；测试通过 record component 断言防止 authorization 字段回流。
5. `DataQualitySourceHealthMapper` 覆盖 success、high latency、429、timeout、5xx、malformed response、disabled、fallback、stale data、gap 与 `PUBLIC_OUTBOUND -> PUBLIC_CANDIDATE` 兼容映射。
6. `DataQualityFreshnessRule` 集中维护 1m / 5m / 1h / 1d stale baseline，支持 `NO_DATA`、`DISABLED`、`ERROR`，unsupported timeframe fail-closed。
7. `DataQualityGapRule` 基于 expected candles vs actual candles 计算 `NONE / GAP / PARTIAL / UNKNOWN` 与 `gapCount`，未知 expected 不折叠为无缺口。
8. O-2 窄口 Maven 与 backend 全量 Maven 均已重新运行并通过；保留既有 SLF4J / Mockito dynamic agent / unchecked / deprecation warning，非阻断。
9. commit diff 未触达 frontend / research / scripts / deploy / `.github` / migration；dataquality 包内未发现 Spring MVC API 注解、`/api/`、HTTP client、JDBC / Repository 或 Flyway / migration。
10. 未执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken public outbound smoke；未读取 credential material。

Post-freeze rules：

- 不得把 O-2 Data Quality summary 写成 trading authorization、provider ready、LIVE ready 或 real permission probe ready。
- 不得弱化 `DataOrigin.PUBLIC_OUTBOUND -> PUBLIC_CANDIDATE` 的兼容降级语义；O-5 manual real public smoke 前如需新增真实 public origin，必须单独 review。
- 不得在未执行 O-3 API plan/review 前把 O-2 接入现有 `/api/marketdata/readiness` read model。
- 后续 O-3 / O-4 / O-5 / O-FREEZE 必须单独 plan/review；O-5 manual public outbound smoke 仍不得提前执行。

## 8. Decision

O-2 final status：`PASS / ACCEPTED / FROZEN`。

Remaining GateO status：

- O-1 controlled public outbound guard：`PASS / ACCEPTED / FROZEN`。
- O-2 Data Quality Center baseline：`PASS / ACCEPTED / FROZEN`。
- O-3 MarketData Runtime Readiness API：`PLANNED / NOT STARTED`。
- O-4 MarketData Quality UI：`PLANNED / NOT STARTED`。
- O-5 Manual Public Outbound Smoke：`PLANNED / NOT STARTED`。
- O-FREEZE：`PLANNED / NOT STARTED`。
- GateO stage：`NOT COMPLETED`。

## 9. Recommended Next Task

下一步只能进入：`NQ-GATEO-O3-MARKETDATA-RUNTIME-READINESS-API-PLAN-REVIEW`。

Commit recommendation：允许提交本轮 docs-only freeze review 记录；不得把 GateO 写成 completed，不得执行 O-5 manual public smoke。
