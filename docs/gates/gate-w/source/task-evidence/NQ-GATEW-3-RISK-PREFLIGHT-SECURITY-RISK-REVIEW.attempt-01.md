# NQ-GATEW-3 Risk Preflight Security/Risk Review — Attempt 01

## Review target

- Task：`NQ-GATEW-3-RISK-PREFLIGHT-SECURITY-RISK-REVIEW-ATTEMPT-01`。
- 起始 HEAD：`435c78fd06acba9cfe308404b1b49138bda373ff`。
- 起始 exact-head CI：`NQ CI Baseline / 29325047414 / completed / success / 10 jobs success`。
- 起始 authority：`GateW-2 / ACCEPTED|CI_GREEN`；`GateW-3 / COMMITTED|CI_GREEN|CONTINUE_REQUIRED / 71e1ded5... / 29324600871`。
- 审查目标：冻结一个 OKX Spot、LIMIT BUY/SELL、internal-only、side-effect-free risk preflight 合同；不得复用真实 order gate。

## Evidence checked

- `trading/application/preflight/**`、`orderpreview/**`、`reconciliation/**`。
- `nq-risk` 的 `RiskContext`、`PreTradeRiskService`、`RiskRuleRegistry`、八条既有 rule 与 tests。
- account metadata/credential summary ports、marketdata quality read model、相关 `nq-infra`/`nq-app`/`nq-api` wiring。
- current authority、GateW plan、preview/reconciliation frozen evidence 与 baseline CI。

## Existing-rule matrix

| Component / Rule | Input facts | Pure/deterministic? | Reads local DB? | Reads mutable memory? | Mutates state? | Writes DB/event/audit? | Calls network? | Uses `PlaceOrderCommand`? | Safe for GateW-3? | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TradingPreflightReadinessService` | account metadata、credential summary、marketdata quality | 否，含 local reads 与 injected/system Clock | 是 | 否 | 否 | 否 | 否 | 否 | 不直接复用 | 保持公开 `RISK_PREFLIGHT_BLOCKED`；使用独立 immutable snapshots |
| `DryRunOrderPreviewResult` | 已生成 structural/venue facts | 是，immutable result | 否 | 否 | 否 | 否 | 否 | 否 | 是 | 只消费 result，不调用 order path |
| `DryRunOrderPreviewService` | request + bounded instrument catalog read | 给定 snapshot/时间时确定 | 是 | 否 | 否 | 否 | 否 | 否 | 不由 risk preflight 调用 | 上游先生成 result |
| `ReconciliationResult` | 已生成 bounded snapshot comparison | 是，immutable result | 否 | 否 | 否 | 否 | 否 | 否 | 是 | 只消费 result；clean contract 才 PASS |
| `ReadOnlyOrderReconciliationService` | local/remote read ports + Clock | comparator 纯；orchestration 可读 remote port | 是 | 否 | 否 | 否 | 端口实现可能有 | 否 | 不由 risk preflight 调用 | 上游先生成 result；缺失时 NOT_EVALUATED |
| `PreTradeRiskService` + registry | `RiskContext` + ordered rules | 否，混合 pure/stateful | 否 | 是 | 部分 rule 会 | 否 | 否 | 是 | 否 | `DO_NOT_CALL_FROM_GATEW3_PREFLIGHT` |
| `OrderPrecisionRule` | command price/quantity + platform scales | 数学部分确定 | 否 | 否 | 否 | 否 | 否 | 是 | 否 | 不替代 venue tick/step；preview 已覆盖 |
| `MinNotionalRule` | command + configured minimum | 数学部分确定 | 否 | 否 | 否 | 否 | 否 | 是 | 否 | 配置不能冒充 OKX minimum；保持 UNKNOWN |
| `MaxOrderAmountRule` | command + configured maximum | 数学部分确定 | 否 | 否 | 否 | 否 | 否 | 是 | 否 | internal policy 来源/单位未冻结；NOT_EVALUATED |
| `SymbolEnabledRule` | command symbol + settings | 给定 settings 时确定 | 否 | 否 | 否 | 否 | 否 | 是 | 否 | 仅可视为 local metadata，不能代表远端授权 |
| `AccountTradingEnabledRule` | accountId + settings | 给定 settings 时确定 | 否 | 否 | 否 | 否 | 否 | 是 | 否 | 仅可视为 local metadata，不能代表远端授权 |
| `KillSwitchRiskRule` | `AtomicBoolean` kill-switch | 否 | 否 | 是 | evaluate 不写；service 可写 | 否 | 否 | 是 | 否 | GateW-4；`KILL_SWITCH_NOT_EVALUATED` |
| `DuplicateRequestRule` | account + idempotency key + time history | 否 | 否 | 是 | 写 `ConcurrentHashMap` | 否 | 否 | 是 | 否 | `DUPLICATE_REQUEST_NOT_EVALUATED` |
| `RateLimitRule` | account/symbol/side + time window | 否 | 否 | 是 | 写 deque/map | 否 | 否 | 是 | 否 | `RATE_LIMIT_NOT_EVALUATED` |
| local account snapshot | exists/exchange/market/env/status | 是 | 否 | 否 | 否 | 否 | 否 | 否 | 是 | 只表达 `LOCAL_METADATA_STATUS` |
| credential metadata snapshot | configured/count/type/verification/probe metadata | 是 | 否 | 否 | 否 | 否 | 否 | 否 | 是 | 不包含 credential material |
| marketdata quality snapshot | `OK/WARNING/BLOCKED/UNKNOWN` | 是 | 否 | 否 | 否 | 否 | 否 | 否 | 是 | diagnostic only；OK 不等于 trading ready |

## Allowed and rejected reuse

- Allowed：`DryRunOrderPreviewResult`、`ReconciliationResult`、显式 immutable account/credential/marketdata snapshots。
- Rejected：`PreTradeRiskService.evaluate()`、完整 `RiskRuleRegistry`、`RiskContext`、全部既有 rule execution、`PlaceOrderCommand`。
- Existing readiness decision：策略 B；不调用、不修改公开 `TradingPreflightReadinessService`/Controller/API，避免 GateP blocker 语义与 GateW-3 contract 混合。

## Frozen input contract

- `traceId`、显式 `evaluationTime`、`diagnosticEnvironment`。
- nullable `DryRunOrderPreviewResult` 与 nullable `ReconciliationResult`；null 精确表示 `NOT_EVALUATED`。
- `LocalAccountMetadataSnapshot`、`CredentialMetadataSummary`、`MarketdataQualitySnapshot`。
- 禁止 `PlaceOrderCommand`、credential material、private header、raw provider payload、URL/path 与 mutable entity。

## Frozen output contract

- 独立状态：`structuralStatus`、`venueFactStatus`、`reconciliationStatus`、`localAccountStatus`、`credentialMetadataStatus`、`marketdataQualityStatus`、`pureRiskStatus`、`statefulRiskStatus`、`balanceStatus`、`permissionStatus`、`executionReadiness`。
- 状态枚举：`PASS`（通过）、`BLOCKED`（阻断）、`UNKNOWN`（未知）、`NOT_EVALUATED`（未评估）。
- 固定安全字段：`diagnosticOnly=true`、`readOnly=true`、`noSideEffect=true`、`orderSubmitted=false`、`tradingAuthorized=false`、`executionReadiness=BLOCKED`。
- finding 必须互斥分入 `blockers`、`warnings`、`unknowns`、`notEvaluated`。

## Fact combination rules

- Preview structural/venue 任一 BLOCKED，则 `ORDER_PREVIEW_BLOCKED`；缺失或未评估则 `ORDER_PREVIEW_NOT_EVALUATED`。
- Reconciliation 仅在 immutable result 的 differences/warnings/unknowns/notEvaluated 为空、唯一 blocker 是 `EXECUTION_NOT_AUTHORIZED`、assessment 为 `SNAPSHOT_MATCHED_AT_EVALUATION_TIME` 且 evaluatedAt 非 future 时 PASS。
- 任一 mismatch/partial/stale/future/unknown 使 reconciliation BLOCKED；不 repair、不写回。
- Local account 只检查 configured、OKX、SPOT、requested diagnostic environment、ACTIVE。
- 多个不同 credential type 可以是合法 metadata；active count/type 数量冲突或重复 type 才是 conflict。
- Marketdata quality 仅是 diagnostic fact；任何状态都不改变 execution blocked。

## Unknown and not-evaluated taxonomy

- UNKNOWN：`MIN_NOTIONAL_UNKNOWN`、`FEE_UNKNOWN`、`REMOTE_PERMISSION_UNKNOWN`；API key validity 与 IP allowlist 不被评估或推断。
- NOT_EVALUATED：balance、position、daily loss、open orders、kill switch、duplicate request、rate limit、完整 stateful pipeline；缺少 reconciliation 时另含 `RECONCILIATION_NOT_EVALUATED`。
- 所有结果固定含 `EXECUTION_NOT_AUTHORIZED`。

## Module scope

- 允许且采用：`backend/nq-core/**` 与本 evidence/current authority。
- 未采用：`nq-app`/`nq-infra` local fact port，因为 caller-provided immutable snapshot 已足够且可完全避免隐式 IO。
- 禁止且保持无 diff：`nq-risk` production、`nq-api`、scheduler、adapter、migration、frontend、workflow、research、deploy。

## Findings

- P0：无。
- P1：无。
- P2：无。
- P3：既有 `CLAUDE.md` 仍硬编码 GateJ 历史状态；`STATUS.md` 是明确唯一 current authority，本轮不扩大 scope 修改该入口。

## Final decision

`PASS / GATEW_3_RISK_PREFLIGHT_REVIEW_ACCEPTED`（通过 / GateW-3 risk preflight review 已接受）。

允许进入附件限定的 conditional implementation；该接受不表示 risk preflight 已实现、已提交、CI green、GateW-3 accepted、GateW frozen 或交易获授权。
