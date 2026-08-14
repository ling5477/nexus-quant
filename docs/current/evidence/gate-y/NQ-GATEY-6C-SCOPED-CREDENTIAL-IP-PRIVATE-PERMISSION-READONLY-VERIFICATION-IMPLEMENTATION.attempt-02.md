# GateY-6C scoped credential/IP/private permission read-only verification implementation — attempt-02

## Task classification

- 类型：`HIGH_RISK_CREDENTIAL_BOUNDARY_IMPLEMENTATION`（高风险凭证边界实现重试）。
- 归属：NQ-only / GateY-6C。
- 结果：`BLOCKED / OKX_PERMISSION_MODEL_CONFLICT`（阻断 / OKX 权限模型冲突）。
- 日期：2026-08-15（Asia/Shanghai）。

## Starting baseline

- branch=`dev`，worktree/staged=`clean/empty`。
- `HEAD == origin/dev == e90c61528a144ea258e571fcb4b93ce13c30bf76`。
- exact-head CI=`31817056214 / NQ CI Baseline / completed / success`。
- authority checker=`PASS / CURRENT_AUTHORITY_CONSISTENT`。
- accepted batch=`GateY-6B / ACCEPTED|CI_GREEN`；work batch=`GateY-6C / NOT_STARTED / NONE / NOT_RUN`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`，real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`。

## Attempt-01 blocker

Attempt-01 因 `ARCHITECTURE.md` 仍将真实 permission probe 作为 blanket prohibition 而返回 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`；未修改文件、未请求或读取 credential material、未访问 OKX、mutation=`0`。本文件只记录该历史结论，不反向伪造 attempt-01 evidence 文件。

## Current architecture fact reconciliation

当前代码证明以下能力事实：

- `OkxRealReadonlyPermissionProbePort` 已存在，唯一 outbound operation 固定为 typed `GET /api/v5/account/config`。
- `JdbcOkxPrivateCredentialExecutor` 复用既有 exact credential reference/JIT callback，callback 后失效并在 `finally` 清理 material；没有第二套 credential store/resolver/signer/HTTP client。
- `AccountModuleConfiguration` 默认选择 `NoRealExchangeCredentialPermissionProbePort`；只有显式 diagnostic profile、exact safety flags、expected egress IP 与 read-only mutation flags全部满足时才选择 real read-only port。
- Spring composition test 只构造 bean，不在 startup 调用 probe；不存在 scheduler/auto-probe 证据。
- `OkxPrivateReadRequest` 与 `OkxSpotEndpointGuard` 封闭 method/path/query schema；调用方不能传 raw URL 或 arbitrary private path。

因此最小修正 `ARCHITECTURE.md` 与 `MODULES.md`：受控真实 private read-only diagnostic 基础设施已存在，但默认 `NoReal`、本轮真实 smoke 未执行、generic/mutating probe 与交易执行仍禁止。

## Reuse audit

| 分类 | 组件 | 结论 |
| --- | --- | --- |
| `REUSE` | credential reference/JIT、`JdbcOkxPrivateCredentialExecutor` | exact reference、同步 callback、fail-closed selection、material cleanup 已存在 |
| `REUSE` | typed account/config request、private GET transport、signer、IP normalizer/parser | 不创建第二套实现 |
| `REUSE` | `OkxRealReadonlyPermissionProbePort` | GateW `READ_ONLY_DIAGNOSTIC` 语义保持不变 |
| `EXTEND` | GateY pilot permission policy | 只有 hard-gate contract 与 OKX 权限模型兼容后才可实现独立 policy |
| `NOT_NEEDED` | generic permission framework | 当前 typed operation 足够且 generic path 会扩大攻击面 |
| `FORBIDDEN` | arbitrary method/path、mutation transport、provider/worker runtime wiring | 本轮全部禁止 |

## Official OKX protocol audit

来源：OKX 官方 API 文档（2026-08-15 读取）：

- [API key permissions](https://www.okx.com/docs-v5/en/#overview-api-key-creation)：`Read` 可读取账户信息；`Trade` 可 place/cancel orders、执行 **funding transfer** 和修改需要 write permission 的设置；`Withdraw` 可提现。
- [Get account configuration](https://www.okx.com/docs-v5/en/#rest-api-account-get-account-configuration)：`GET /api/v5/account/config` 返回 requesting key 的 `ip`、`perm`、`uid`、`mainUid`、`acctLv` 与 safe label 等配置事实；`perm` 枚举包含 `read_only`、`trade`、`withdraw`。
- [Funds transfer](https://www.okx.com/docs-v5/en/#funding-account-rest-api-funds-transfer)：`POST /api/v5/asset/transfer` 明确仅允许具有 `Trade` privilege 的 API key 调用，并覆盖 funding/trading account 之间及部分主/子账户转账。

## G08/G09 compatibility

- G08 要求 exact pilot key 具备 minimum `TRADE` permission。
- G09 当前要求 `transfer and withdraw disabled proof`，并要求通过 sanitized remote fact 证明 transfer/funding unavailable。
- OKX 当前 `Trade` permission 本身包含 funding transfer；因此同一个 remote credential 无法同时满足“具备 `TRADE`”和“remote transfer/funding capability unavailable”。
- NQ application endpoint policy 永久拒绝 `FUNDS_MOVEMENT`，只能证明 NQ 不会表达或发送 transfer，不能把 remote credential 的实际 capability 改写为 absent。
- sub-account transfer-out policy 只约束特定跨账户路径，不能消除 `Trade` 对 funding/trading account transfer 的通用能力，也不能由 `account/config.perm` 单独证明 G09。

结论：`G08_TRADE_AND_G09_TRANSFER_REQUIREMENTS_NOT_SIMULTANEOUSLY_EXPRESSIBLE`。按照任务 hard blocker，本轮在读取 credential 前停止，未执行真实 probe，未修改 hard-gate manifest。

## Required contract remediation

后续独立 hard-gate contract remediation 必须选择并明确接受一种真实语义，不能继续写“Trade key 没有 transfer capability”：

1. 将 remote credential 条件精确写为：`READ present + TRADE present + WITHDRAW absent`；并明确 `TRADE` 在 OKX 远端包含 funding transfer capability。
2. 将 funds-movement 控制拆为独立分层事实：
   - `REMOTE_API_KEY_CAPABILITY`: `TRADE` 已包含 funding transfer，不能标记为 disabled；
   - `ACCOUNT_LEVEL_TRANSFER_POLICY`: 仅记录 OKX 可独立观察且确实适用的主/子账户 transfer-out 限制，unknown 必须 fail closed；
   - `NQ_APPLICATION_ENDPOINT_POLICY`: `FUNDS_MOVEMENT` typed capability 永久 `DENIED`，worker/provider 无 transfer operation 与 runtime wiring。
3. 将 G09 的 exact wording 从笼统的 `transfer and withdraw disabled proof` 改为不会否认官方权限模型的条件，例如：`remote WITHDRAW permission absent + NQ funds-movement endpoint/runtime unreachable + applicable account-level transfer-out restriction independently verified`；如果风险接受要求 remote credential 绝对无 transfer capability，则必须放弃 G08 `TRADE`，GateY 当前方案不可继续。

本 attempt 不修改 work order、manifest或 governance contract。

## Calls, credentials and mutations

- credential reference lookup/access=`0/0`。
- credential material exposure=`0`。
- authenticated OKX endpoint calls=`0`。
- retry=`0`。
- mutation=`0`；PLACE/CANCEL/transfer/withdraw=`0/0/0/0`。
- startup/scheduler probe=`0/0`。
- real provider/worker transport binding=`0/0`。

## Validation

- baseline authority checker：`PASS`。
- exact-head CI：`completed/success`。
- backend focused/GateW/GateY-6B/ArchUnit/full Maven：`NOT RUN`（协议 hard blocker 在代码修改和 credential 读取前触发）。
- current authority checker：`PASS / errors=0`。
- doc links：`PASS / 304 checked / 14 historical warnings / 0 errors`；warning 均为 `TESTING.md` 既有 GateJ/GateX append-only 历史路径。
- `git diff --check`：exit=`0`。

## Findings

- P0：无 mutation、credential exposure 或 runtime reachability 已发生。
- P1：G08/G09 与 OKX 官方 permission model 不可同时表达，阻断 GateY-6C credential读取与真实 probe。
- P2：原 `ARCHITECTURE.md`/`MODULES.md` capability statement 已漂移，本 attempt 仅按当前代码事实最小修正。
- P3：无。

## Authority and boundary

- authority after 保持不变：GateY-6C=`NOT_STARTED`，next action不变。
- `FIRST_REAL_ORDER=NOT_AUTHORIZED`，micro-live=`NOT_AUTHORIZED`，LIVE=`DISABLED`，kill switch=`ENGAGED`。
- real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`。
- 最终结论：`BLOCKED / OKX_PERMISSION_MODEL_CONFLICT / NO_SECRET_REQUESTED / NO_REMOTE_PROBE / NO_MUTATION / GATEY_HARD_GATE_CONTRACT_REMEDIATION_REQUIRED`。

## Rollback

本轮未提交。可按精确 diff 恢复 `ARCHITECTURE.md`、`MODULES.md`、GateY evidence index、本 evidence及 append-only ledger新增段；禁止使用破坏性 Git 命令覆盖其他改动。
