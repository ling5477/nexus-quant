# GateY-6D exact pilot scope prerequisite materialization implementation — attempt-01

## 结论

`PASS / GATEY_6D_MATERIALIZATION_CAPABILITY_IMPLEMENTED / V40_REUSED / SERVER_SIDE_EXACT_BINDING / CANONICAL_PILOT_SCOPE_HASH / INDEPENDENT_APPROVAL_BOUNDARY / EXACT_PILOT_SCOPE_NOT_MATERIALIZED / EXPLICIT_PILOT_SCOPE_INPUT_REQUIRED / PREREQUISITE_OBSERVATION_INPUT_REQUIRED / NO_VALUES_INVENTED / EXECUTION_INTENT_0 / EXCHANGE_MUTATION_0 / FIRST_REAL_ORDER_NOT_AUTHORIZED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / KILL_ENGAGED / PENDING_INDEPENDENT_SECURITY_REVIEW`

本报告只证明 authenticated control-plane capability 已实现并通过本地验证，不表示 pilot ready、交易授权、LIVE enable 或任何真实订单能力。

## 基线与范围

- task：`NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-IMPLEMENTATION`。
- classification：NQ-only / L级 / LIVE control-plane；不涉及 DH。
- branch：`dev`；起始 working tree/staged=`clean/empty`。
- `HEAD == origin/dev == bc35edb60370aee367ab40853201e1f249179b83`。
- exact-head CI：`31933158234 / completed / success`。
- authority before：accepted=`GateY-6C / ACCEPTED|CI_GREEN`；work=`GateY-6D / NOT_STARTED / NONE / NOT_RUN`；next action 与本任务一致。
- hard boundary：LIVE=`DISABLED`；kill switch=`ENGAGED`；real provider/private trading=`NOT_IMPLEMENTED`。

## V40 复用与实现

未修改 migration/schema，直接复用 accepted V40：

- `PilotScopeBinding`、`PilotObservationSet`、四类 typed prerequisite facts；
- canonical scope/observation encoder 与 freshness/preflight policy；
- `PilotScopeRepository`、`JdbcPilotScopeRepository`；
- `PilotScopeFactTransactionService` 的 materialization/approval/preflight 事务边界。

新增的最小 typed boundary：

- `POST /api/live-control/pilot-sessions`：创建 session + canonical scope + complete typed observation set；
- `POST /api/live-control/pilot-sessions/{sessionId}/approval`：独立 `LIVE_APPROVER` 对 exact `pilotScopeId + pilotScopeHash` 审批；
- `POST /api/live-control/pilot-sessions/{sessionId}/preflight`：只返回 stored-fact eligibility，不创建订单或 intent。

Controller 不接收 creator、approver、role、credential material、generic JSON 或 SQL；actor 只从 `GatewayAuthFacade + CurrentUserProfileService` 获取。

## Exact authority binding

服务端重新查询并 fail-close：

- account：认证 owner 下 exact account ID，且 `OKX / LIVE / ACTIVE`；
- credential：exact credential/account/type，`ACTIVE / VERIFIED / SUCCEEDED / TRADE`，withdraw=`false`，IP=`PASSED`，无 revoke/rotate；
- release：exact publish identity、admission revision 与 artifact digest；
- risk：exact `RiskLimitSet` ID/canonical digest，所有 caps、symbol allowlist 与 freshness limits 必须与 stored SoR 一致；
- runtime：instrument/fee/balance/clock source，endpoint policy，provider contract 与 worker release 必须与 server-owned `nq.live-control.pilot-materialization.*` exact config 一致；缺失时 `PILOT_RUNTIME_AUTHORITY_NOT_CONFIGURED`，不信任客户端值；
- `latest/current/HEAD` 等漂移引用一律拒绝。

服务端重建 canonical `pilotScopeHash` 和四类 observation payload hash；source/schema 与 recorder identity 绑定到已校验 runtime authority。supplied/stored/hash/symbol/time 任一 mismatch、missing、unknown、future 或 stale 均 fail closed。

## Approval 与 preflight

- creator 固定为 authenticated `OPERATOR`；approver 固定为 authenticated `LIVE_APPROVER`。
- `creator == approver` 拒绝；approval 固定为 `pilot-scope.v1` 并绑定 exact scope；future/expired/expiry 超 window 拒绝。
- legacy `approval-scope.v1` 不能满足 pilot preflight。
- preflight 在 `REPEATABLE READ` 下读取 exact valid approval 与最新 complete observation set，再执行 V40 freshness/balance/clock/instrument/fee 检查；结果仅为 eligibility fact。

## Operator 输入与实际 materialization

本任务未获得以下 exact authority，Codex 未猜值：

- strategy release ID/digest/admission revision；
- RiskLimitSet ID/digest 与 capital/order/position/loss/open-order/intraday-order limits；
- exchange account ID、credential reference、1～2 个 approved OKX Spot symbols；
- execution window、approval expiry、独立 creator/approver identities；
- server runtime source/policy/provider/worker exact identities/digests/ages；
- instrument、fee、fresh balance、clock 四类 typed observations。

因此：

```text
CAPABILITY_IMPLEMENTED
EXACT_PILOT_SCOPE_NOT_MATERIALIZED
PILOT_SCOPE_NOT_MATERIALIZED
EXPLICIT_PILOT_SCOPE_INPUT_REQUIRED
PREREQUISITE_OBSERVATION_INPUT_REQUIRED
NO_VALUES_INVENTED
```

实际 LiveSession/PilotScope/observation set/approval rows=`0/0/0/0`；preflight=`NOT_RUN_NO_SCOPE`。

## 验证证据

| Validation | Result |
| --- | --- |
| compile | `nq-api,nq-infra -am` 20/20 modules `BUILD SUCCESS` |
| GateY-6D focused | 新增 11 tests，failures/errors/skipped=`0/0/0` |
| V40 PostgreSQL + GateY-2/4/6C + ArchUnit | disposable PostgreSQL 17.7；89 tests，`0/0/0`；23/23 modules success |
| V39→V40 / V1→V40 | PASS；V39→V40=`99ms`；no-fake-backfill、canonical parity、幂等/并发与 approval compatibility PASS |
| V40 lock rollback | expected bounded failure=`5065ms`；migration transaction rollback PASS |
| GateY-4 script | 6/6 cases PASS：delegate-release/linux-root/identity/no-start/no-secret/no-network |
| full backend | 23/23 modules `BUILD SUCCESS`；1506 tests，failures/errors/skipped=`0/0/47`；50.843s |

首次 focused 命令未引用 `-Dsurefire.failIfNoSpecifiedTests=false`，PowerShell/Maven 将其解析为 lifecycle phase，退出码 1；引用参数后相同 focused tests 通过。该编排失败不是代码/test failure，仍在此保留。既有 Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked 与 conditional skip warnings 非阻断。

## Architecture 与绝对安全边界

- `livecontrol` 拥有 pilot materialization/approval；strategy/account/risk 保持原 SoR owner；JDBC 只在 `nq-infra`；`nq-api` 只依赖 core application interface。
- ArchUnit 16/16 PASS；`nq-api` SQL literal/JDBC dependency guard PASS。
- V40 与全部历史 migration diff=`0`；frontend/research/scripts/deploy/CI diff=`0`。
- 本轮未调用真实 materialization API、credential store、OKX 或任何 exchange network。
- task-created `ExecutionIntent/ExecutionReceipt/PLACE/CANCEL/TRANSFER/WITHDRAW/worker start/real-provider wiring/exchange mutation`=`0/0/0/0/0/0/0/0/0`。
- `LIVE=DISABLED`、kill=`ENGAGED`、`FIRST_REAL_ORDER=NOT_AUTHORIZED`、`MICRO_LIVE=NOT_AUTHORIZED`。

## Findings 与 authority after

- P0/P1/P2/P3=`0/0/0/0`；本结论是 implementation self-check，不代替下一轮独立 Security Review。
- work batch=`GateY-6D`。
- work status=`IMPLEMENTED|PENDING_REVIEW`。
- work commit=`UNCOMMITTED`；CI run=`NOT_RUN`。
- next action=`NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-SECURITY-REVIEW`。
- 未 stage、commit、push、deploy；未连接生产。
