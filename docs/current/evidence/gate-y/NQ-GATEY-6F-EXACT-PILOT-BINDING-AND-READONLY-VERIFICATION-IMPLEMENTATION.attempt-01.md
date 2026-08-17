# GateY-6F exact pilot binding and read-only verification implementation — attempt-01

## 任务分类与结论

- Task classification：`CONTROLLED_RUNTIME_READONLY / EXACT_PILOT_BINDING / CREDENTIAL_BOUNDARY / DURABLE_PILOT_MATERIALIZATION`；NQ-only、高风险任务。
- Final decision：`BLOCKED / EXPLICIT_PILOT_SCOPE_INPUT_REQUIRED / NO_CREDENTIAL_READ / NO_OKX_CALL / NO_PILOT_MATERIALIZATION / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED`（阻断 / 需要明确 pilot scope 输入 / 未读取凭证 / 未调用 OKX / 未物化 pilot / 第一笔真实订单未授权 / LIVE 关闭）。
- 阻断发生在 credential access、permission/IP probe、OKX call、durable write 与 preflight 之前；未把历史 evidence ID 当成 current SoR 事实，也未猜测 symbol、金额、窗口、operator、approver、release 或 risk set。

## Baseline 与 CI

```text
branch=dev
worktree=clean
staged=0
HEAD=origin/dev=7582e6e999eb9cb7f46f35efc852a62af103f5a4
CI=32034307622 / completed / success / 10 jobs

accepted_batch=GateY-6E
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-6F
work_batch_status=NOT_STARTED
next_action=NQ-GATEY-6F-EXACT-PILOT-BINDING-AND-READONLY-VERIFICATION-IMPLEMENTATION
LIVE=DISABLED
kill_switch=ENGAGED
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
GATEY_PILOT_SOAK=NOT_STARTED
```

GitHub 远端 `refs/heads/dev` 与 CI `headSha` 均精确匹配 baseline；GitHub 只读核验，没有触发 workflow 或外部写操作。

## 既有控制面与 architecture hygiene

已读取并确认复用以下唯一 production 定义：

- `PilotScopeControlPlaneController` / `PilotScopeMaterializationRequest` / `PilotScopeApprovalRequest`；
- `PilotScopeControlPlaneService` / `PilotScopeFactTransactionService`；
- `PilotPrerequisiteObservationAuthority` 与 `OkxPilotPrerequisiteObservationAuthority`；
- `JdbcPilotScopeAuthorityResolver`；
- `JdbcOkxPrivateCredentialExecutor`；
- `OkxJdkRealClient` / `OkxPrivateRequestSigner`；
- 既有 LiveSession、PilotScope、RiskLimitSet、approval、stored-fact preflight Repository/SoR。

本 attempt 新增 PilotScope API、credential resolver、signer/HTTP client、session/approval SoR、provider/worker wiring 与产品代码修改均为 0。Core 未依赖 infra，Controller 仍只做 typed mapping 与认证 identity 传递，credential material 仍只允许在既有 JIT callback 内短暂存在。

## Server-owned facts requery

仓库安全配置只证明本地默认候选为 `127.0.0.1:5432/nexus_quant`。使用 PostgreSQL 17 client、`psql -w -X` 和显式 read-only 身份探针时，连接在执行 SQL 前返回：

```text
exit=2
classification=NO_PASSWORD_SUPPLIED
sql_executed=0
```

没有读取 `.env`、环境 secret、`.pgpass` 内容、credential payload 或生产连接信息；Docker daemon 不可用，也未启动容器。因此以下 current SoR facts 均为 `UNRESOLVED / NOT REQUERIED`，不能从历史 evidence 复制：

- owner；
- eligible OKX/LIVE exchange account；
- active `OKX_API_V5` credential metadata reference；
- accepted/admitted immutable strategy release identity/digest/revision；
- immutable RiskLimitSet identity/digest/version/facts；
- endpoint policy identity/digest；
- provider identity/artifact digest；
- worker/release identity/digest。

这不是“0 个候选”的数据库结论，而是 current SoR 身份与只读连接均未建立；因此 exact-one 判定未运行。

## Missing operator-controlled exact inputs

任务消息没有提供当前 accepted API 所需的 exact non-secret values。缺失字段如下。

Materialization request / header：

```text
Idempotency-Key
sessionId
pilotScopeId
exchangeAccountId
credentialReference
strategyReleaseId
releaseDigest
releaseAdmissionRevision
risk.riskLimitSetId
risk.riskLimitSetDigest
risk.version
risk.capitalCap
risk.maxOrderNotional
risk.maxSymbolPositionNotional
risk.maxDailyRealizedLoss
risk.maxDailyTotalLoss
risk.maxOpenOrders
risk.maxIntradayOrders
risk.symbolAllowlist
risk.maxSessionDurationSeconds
risk.spreadLimitBps
risk.slippageLimitBps
risk.maxMarketDataAgeMs
risk.minDataCoverageBps
symbolAllowlist
capitalCap
executionWindowStart
executionWindowEnd
expectedPilotScopeHash
creator/operator authenticated identity
```

Approval request / independent authentication：

```text
approvalId
pilotScopeId
expectedPilotScopeHash
reason
approvedAt
expiresAt
independent LIVE_APPROVER authenticated identity
```

`exchangeAccountId`、`credentialReference`、release fields 与完整 `risk.*` 必须先从 current SoR 精确 requery；若 exact-one 无法成立，再由 operator 在脱敏候选中明确选择。Creator/operator 与 approver identity 均不允许放入 request body，必须来自两个不同的认证上下文。

## Sanitized input template

以下模板仅列 non-secret typed fields；不得在聊天或 evidence 中加入 API key、secret、passphrase、Authorization header、signature 或 encrypted/decrypted payload。

```text
creator authentication: <existing authenticated OPERATOR context>
Idempotency-Key: <unique non-secret key>

materialization:
  sessionId: <uuid>
  pilotScopeId: <uuid>
  exchangeAccountId: <server-requeried non-secret id>
  credentialReference: <server-requeried non-secret id>
  strategyReleaseId: <exact admitted id>
  releaseDigest: <lowercase sha256>
  releaseAdmissionRevision: <positive integer>
  risk:
    riskLimitSetId: <uuid>
    riskLimitSetDigest: <lowercase sha256>
    version: <positive integer>
    capitalCap: <exact stored value>
    maxOrderNotional: <exact stored value>
    maxSymbolPositionNotional: <exact stored value>
    maxDailyRealizedLoss: <exact stored value>
    maxDailyTotalLoss: <exact stored value>
    maxOpenOrders: <exact stored value>
    maxIntradayOrders: <exact stored value>
    symbolAllowlist: [<1-2 explicitly selected OKX Spot USDT symbols>]
    maxSessionDurationSeconds: <exact stored value>
    spreadLimitBps: <exact stored value>
    slippageLimitBps: <exact stored value>
    maxMarketDataAgeMs: <exact stored value>
    minDataCoverageBps: <exact stored value>
  symbolAllowlist: [<same exact sorted symbols>]
  capitalCap: <same exact risk capitalCap>
  executionWindowStart: <ISO-8601 instant>
  executionWindowEnd: <ISO-8601 instant>
  expectedPilotScopeHash: <server-contract canonical lowercase sha256>

approval under a different authenticated LIVE_APPROVER context:
  approvalId: <uuid>
  pilotScopeId: <same exact uuid>
  expectedPilotScopeHash: <same exact sha256>
  reason: <non-empty non-secret reason>
  approvedAt: <ISO-8601 instant>
  expiresAt: <ISO-8601 instant not after executionWindowEnd>
```

## Credential、permission/IP 与 OKX boundary

```text
credential_metadata_reference=UNRESOLVED
credential_material_read=0
account/config_calls=0
permission_ip_verification=NOT_RUN
account/instruments_calls=0
account/trade-fee_calls=0
account/balance_calls=0
public/time_calls=0
OKX_API_CALLS=0
retry=0
trusted_prerequisite_collection=NOT_RUN
```

未退化到 mock，未使用 GateY-6C 历史 credential ID，未声称 READ/TRADE/WITHDRAW/IP current facts 已验证。

## Durable materialization、requery 与 preflight

```text
LiveSession=NOT_CREATED
PilotScope=NOT_MATERIALIZED
PilotObservationSet=NOT_CREATED
independent_scope_approval=NOT_CREATED
stored_fact_preflight=NOT_RUN
durable_requery=NOT_RUN
session_id=NONE
pilot_scope_id=NONE
pilot_scope_hash=NONE
approval_id=NONE
ExecutionIntent=0
ExecutionReceipt=0
```

没有 durable control-plane facts 可供清理或删除；authority 仍保持 GateY-6F `NOT_STARTED`。

## Mutation counters

```text
PLACE=0
CANCEL=0
TRANSFER=0
WITHDRAW=0
BORROW=0
ExecutionIntent=0
ExecutionReceipt=0
worker_mutation_start=0
exchange_mutation=0
LIVE_enable=0
kill_disengage=0
soak_start=0
```

## Validation 与 findings

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| Git/origin baseline | PASS（通过） | `dev` clean；staged=`0`；local/remote/CI exact head=`7582e6e...` |
| GitHub CI `32034307622` | PASS（通过） | `completed / success / 10 jobs`；只读核验 |
| current authority checker | PASS（通过） | baseline errors=`0`；GateY-6F `NOT_STARTED` |
| accepted DTO/control-plane audit | PASS（通过） | exact field names、认证 actor ownership、creator != approver、stored-fact-only preflight 与 canonical components 已确认 |
| Docker current SoR candidate | NOT AVAILABLE（不可用） | Docker daemon 未运行；未启动容器 |
| PostgreSQL read-only identity probe | BLOCKED（阻断） | `psql -w` exit=`2 / NO_PASSWORD_SUPPLIED`；SQL executed=`0`；未读取 secret |
| focused Maven safety tests | NOT RUN（未运行） | explicit input hard gate 已在 credential/OKX 前阻断；不会进入真实调用路径；产品代码 diff=`0` |
| frontend/Python | NOT RUN（未运行） | 不在任务范围 |
| final current authority checker | PASS（通过） | errors=`0`；GateY-6F 仍为 `NOT_STARTED`，next action不变 |
| final doc links | PASS WITH HISTORICAL WARNINGS（通过并有历史 warning） | checked=`366`、warnings=`14`、errors=`0`；warning均来自既有 append-only历史链接 |
| diff/allowlist/forbidden scope | PASS（通过） | `git diff --check` exit=`0`；allowlist expected/actual=`4/4`、missing/extra=`0/0`；staged=`0`；backend/frontend/research/scripts/deploy/`.github`/migration/STATUS/ROADMAP diff均为0；ledger removals=`0` |
| added-content secret guard | PASS（通过） | secret value/raw private payload hits=`0/0`；只检查本轮新增内容，避免历史 ledger 误报 |

- P0=`0`。
- P1=`1`：`EXPLICIT_PILOT_SCOPE_INPUT_REQUIRED`，且 current SoR exact-one requery unavailable；阻断 credential/OKX/materialization。
- P2=`0`。
- P3=`0`。

## Authority、变更与下一步

Authority after 保持：

```text
accepted_batch=GateY-6E
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-6F
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6F-EXACT-PILOT-BINDING-AND-READONLY-VERIFICATION-IMPLEMENTATION
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
GATEY_PILOT_SOAK=NOT_STARTED
LIVE=DISABLED
kill_switch=ENGAGED
```

Exact changed files：

```text
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/evidence/gate-y/README.md
docs/current/evidence/gate-y/NQ-GATEY-6F-EXACT-PILOT-BINDING-AND-READONLY-VERIFICATION-IMPLEMENTATION.attempt-01.md
```

- 本轮不 stage、commit、push、deploy，不修改产品代码、migration、CI、STATUS 或 ROADMAP。
- 回滚：提交前逐文件反向应用上述 4 个文档 diff；禁止使用整仓 reset/checkout。
- 建议 commit：`docs(gatey): record GateY-6F explicit input blocker`。
- 下一步：operator 通过既有认证/安全运行环境提供 non-secret exact scope inputs，并建立可验证的 current SoR read-only connection；不要在聊天中提供任何 credential material。随后重跑同名任务并创建 `attempt-02`，不得覆盖本 blocker evidence。
