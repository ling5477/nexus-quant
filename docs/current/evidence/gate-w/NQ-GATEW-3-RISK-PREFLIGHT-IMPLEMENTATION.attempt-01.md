# NQ-GATEW-3 Risk Preflight Implementation — Attempt 01

## Task classification

`BACKEND_IMPLEMENTATION / INTERNAL_DIAGNOSTIC / SIDE_EFFECT_FREE_RISK_PREFLIGHT`。

## Frozen review contract

实现服从 [security/risk review attempt-01](NQ-GATEW-3-RISK-PREFLIGHT-SECURITY-RISK-REVIEW.attempt-01.md)：不调用完整 risk chain，不构造 `PlaceOrderCommand`，只消费 immutable results/snapshots。

## Architecture

- 新 package：`trading/application/riskpreflight`，仅位于 `nq-core`。
- `GateW3RiskPreflightService` 不是 Spring bean；唯一 runtime dependency 是 injected `Clock`。
- request/result/fact bundle 均为 immutable records；result constructor 强制安全 flags 与 finding groups 互斥。
- 不新增 Controller/API、port/adapter/repository、transaction、persistence、scheduler、runner 或 configuration。

## Fact bundle and status composition

- Preview：只读取 structural/venue status 与既有安全 flags；不调用 preview service。
- Reconciliation：只读取既有 result lists/assessment/safety flags；不调用 local/remote ports。
- Local account：只检查 configured/OKX/SPOT/environment/ACTIVE。
- Credential：只检查 metadata configured、active count 与 distinct type consistency；不包含 masked key/material。
- Marketdata：只映射 `OK/WARNING/BLOCKED/UNKNOWN`，不产生 trading-ready 结论。
- `MIN_NOTIONAL`、fee、remote permission 固定 UNKNOWN；stateful risk/balance/position 等固定 NOT_EVALUATED。
- execution readiness 永久 BLOCKED，trading authorization 永久 false。

## Files changed

- production：6 个 `backend/nq-core/.../riskpreflight/*.java`。
- tests：`GateW3RiskPreflightServiceTest.java`。
- current evidence/authority：仅附件 allowlist 内文件。
- `nq-risk` production、`nq-app`、`nq-infra`、`nq-api` 均无实现 diff。

## Tests and proofs

- focused suite：31 tests / 0 failures/errors/skips。
- required targeted reactor：`mvn -f backend/pom.xml -pl nq-core,nq-risk,nq-infra,nq-app -am test`，23/23 modules SUCCESS，BUILD SUCCESS。
- full Maven：`mvn -f backend/pom.xml test`，23/23 modules SUCCESS，BUILD SUCCESS。
- zero-call proof：reflection 验证 service 唯一非 static field 为 `Clock`；compiled class-byte regression 拒绝 `PlaceOrderCommand`、`PreTradeRiskService`、`RiskRuleRegistry`、stateful rules、network/JDBC/repository/write-port 类型。
- immutable proof：result/fact lists defensive copy 且不可修改；finding groups disjoint。
- deterministic proof：fixed Clock + 同一 request 重复结果相同，future evaluationTime fail-closed。

## No-side-effect and forbidden-boundary proof

- 无 network/OKX HTTP/private endpoint/credential material。
- 无 DB read/write、order write/state mutation、risk mutation、kill-switch/rate-limit/duplicate history mutation。
- 无 ledger/audit/event、Controller/API/frontend/migration/scheduler。
- LIVE/Shadow/AI/DH/Integration/real provider/private trading 状态均未改变。

## Rollback

在未 commit 前删除本轮 7 个新增 Java 文件与本轮新增 current evidence/authority diff；commit 后使用普通 revert commit 回滚，不修改历史 migration，不 reset shared branch。

## Result

`IMPLEMENTED / PENDING_INDEPENDENT_REVIEW`（已实现 / 待独立复核）。
