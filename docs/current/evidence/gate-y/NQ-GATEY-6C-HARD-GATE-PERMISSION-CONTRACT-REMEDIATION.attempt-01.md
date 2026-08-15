# GateY-6C hard-gate permission contract remediation — attempt-01

## Task classification

- 类型：`SAFETY_CONTRACT_REMEDIATION / DOCUMENTATION_RECONCILIATION`（安全合同修复 / 文档事实协调）。
- 归属：NQ-only / GateY-6C；风险等级 L。
- 日期：2026-08-15（Asia/Shanghai）。
- 范围：只修正GateY-6 work order与hard-gate manifest的permission语义，并同步current evidence ledger；不修改product code、scripts、governance或machine authority。

## Starting baseline and exact-head CI

- branch=`dev`，worktree/staged=`clean/empty`。
- starting `HEAD == origin/dev == 0b42a2e8b6a8bef5828a4c2523ba19ab57c02f0e`。
- exact-head CI=`31820717419 / NQ CI Baseline / completed / success`。
- authority checker=`PASS / CURRENT_AUTHORITY_CONSISTENT`。
- accepted batch=`GateY-6B / ACCEPTED|CI_GREEN`；work batch=`GateY-6C / NOT_STARTED / NONE / NOT_RUN`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`，real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`。

## Original blocker

GateY-6C attempt-02发现 `G08_TRADE_AND_G09_TRANSFER_REQUIREMENTS_NOT_SIMULTANEOUSLY_EXPRESSIBLE`：G08要求同一pilot credential具备minimum `TRADE`，旧G09却要求证明remote transfer/funding unavailable。OKX官方permission model中，`Trade`本身包含funding transfer；NQ endpoint deny只能证明应用不可表达/发送funds movement，不能把remote capability改写为absent。Attempt-02因此在credential lookup前以 `BLOCKED / OKX_PERMISSION_MODEL_CONFLICT` 停止。

## Official OKX permission evidence

只读取OKX官方API文档，核验日期为2026-08-15；未访问authenticated OKX API：

- [`API key permissions`](https://www.okx.com/docs-v5/en/#overview-api-key-creation)：`Read`可读取账户信息；`Trade`可place/cancel order、执行funding transfer并修改需要write permission的设置；`Withdraw`可提现。该官方文本证明 `TRADE` 与 remote funding-transfer capability不可拆开。
- [`Get account configuration`](https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-account-configuration)：`GET /api/v5/account/config`返回requesting key的`ip`与`perm`；`perm`枚举为 `read_only / trade / withdraw`。本任务只核对公开文档，未调用endpoint。
- [`Funds transfer`](https://www.okx.com/docs-v5/en/#funding-account-rest-api-funds-transfer)：`POST /api/v5/asset/transfer`明确要求API key具有 `Trade` privilege；文档另行描述master/sub-account transfer policy，证明account-level restriction必须与API-key permission分层观察。本任务未调用endpoint。

## G08/G09 semantics remediation

### Old G08

`scoped pilot credential minimum TRADE permission`，但没有显式记录 `READ`、`WITHDRAW forbidden` 或 `TRADE` 固有funding-transfer capability。

### New G08

- Remote credential requires：`READ + TRADE`。
- Remote credential forbids：`WITHDRAW`。
- `IP_BINDING=REQUIRED`。
- Known residual：OKX `TRADE` permission固有包含funding-transfer capability；该remote capability不构成NQ funds-movement授权。

状态保持 `capabilityStatus=NOT_MET / pilotBindingStatus=NOT_VERIFIABLE / finalGateStatus=NOT_MET`，因为本任务没有读取exact pilot credential。

### Old G09

`transfer and withdraw disabled proof`，并要求从sanitized remote facts证明transfer/funding unavailable；该语义错误否认了 `TRADE` 的官方能力。

### New G09

`funds-movement containment and withdraw-deny proof`证明：remote `WITHDRAW` absent；NQ TRANSFER/WITHDRAW operation不可达；provider contract和worker不暴露funds movement；arbitrary private endpoint不可用；typed endpoint policy default-deny；适用的account-level transfer restriction单独观察。Remote `TRADE`保留funding-transfer capability是已知exchange permission属性，不是NQ application containment失败。

状态保持 `capabilityStatus=PASS / pilotBindingStatus=NOT_VERIFIABLE / finalGateStatus=NOT_VERIFIABLE`。

## Three-layer safety model

| Layer | Contract |
| --- | --- |
| Remote API Key | `READ=REQUIRED / TRADE=REQUIRED / WITHDRAW=FORBIDDEN / IP_BINDING=REQUIRED`；`TRADE`包含funding transfer |
| Exchange/account policy | exact account/sub-account restriction只单独记录 `VERIFIED / NOT_VERIFIABLE / NOT_APPLICABLE`，不得从局部restriction推导所有transfer unavailable |
| NQ application/runtime | `FUNDS_MOVEMENT=DENY`；`SpotExecutionProviderPort`、reviewed worker/transport surface均无transfer/withdraw；typed endpoint policy default-deny；raw/arbitrary private path不可表达 |

固定不等式：`remote permission capability != application authorization != FIRST_REAL_ORDER authorization != LIVE authorization`。

## Inherent residual and defense in depth

已知残余记录为 `INHERENT_OKX_TRADE_PERMISSION_RESIDUAL`。未来pilot继续依赖既有防御纵深：dedicated credential、IP allowlist、WITHDRAW absent、single account、tiny capital、single-order/daily-loss caps、typed provider、NQ funds-movement unreachable、kill engaged与manual approval；本任务只建立引用，不新增重复hard gate。

## Manifest and authority invariance

- `schemaVersion`保持 `gatey6-first-real-order-hard-gate.v1`；本轮只修改现有G08/G09字段语义，不需要schema bump，也未修改reader/governance scripts。
- hard-gate状态变化：`0`；G08/G09三层status均不变。
- final counts保持 `PASS=0 / NOT_MET=25 / NOT_VERIFIABLE=5`；total gates=`30`；gap candidates=`10`。
- `firstRealOrder=NOT_AUTHORIZED`、`microLive=NOT_AUTHORIZED`、`explicitAuthorization=NOT_GRANTED`、`live=DISABLED`、`killSwitch=ENGAGED`。
- `realProvider=NOT_IMPLEMENTED`、`privateTrading=NOT_IMPLEMENTED`。
- machine authority保持 GateY-6B accepted、GateY-6C not started，canonical next action不变。

## Calls, credentials and mutation

- credential reference/material lookup/access=`0/0/0`。
- authenticated OKX API calls=`0`；公开OKX docs读取不计为OKX API call。
- mutation=`0`；PLACE/CANCEL/transfer/withdraw=`0/0/0/0`。
- worker start、production operation、LIVE enable、kill disengage=`0/0/0/0`。

## Validation

| Command / check | Result | Scope / warnings |
| --- | --- | --- |
| Git/origin/CI baseline | PASS（通过） | clean `dev`；`HEAD == origin/dev == 0b42a2e8...f0e`；CI `31820717419 / completed / success` |
| hard-gate manifest reader regression | PASS（通过） | JSON parse；schema v1；reader fields=8；gates=`30`；final=`0/25/5`；gaps=`10`；logic/evidence/count/safety errors=`0` |
| G08/G09 invariance | PASS（通过） | G08=`NOT_MET/NOT_VERIFIABLE/NOT_MET`；G09=`PASS/NOT_VERIFIABLE/NOT_VERIFIABLE` |
| current authority | PASS（通过） | errors=`0`；accepted/work/next action/LIVE/kill均未变化 |
| next-action regression | PASS（通过） | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION`；未修改scripts/governance |
| doc links | PASS WITH HISTORICAL WARNINGS（通过并有历史warning） | final=`306 checked / 14 historical warnings / 0 errors`；warning均为 `TESTING.md` 既有GateJ/GateX append-only历史路径 |
| nested link-check wrapper diagnostic | FAILED BEFORE SCAN / RECOVERED（扫描前失败 / 已恢复） | 子 `powershell -File` 将 `-Roots` 数组拆成位置参数并返回 `PositionalParameterNotFound`；改用当前PowerShell直接数组调用后通过，不是文档链接失败，无写副作用 |
| diff/scope | PASS（通过） | `git diff --check` exit=`0`；仅6个allowlisted docs/manifest paths；forbidden areas与STATUS/ARCHITECTURE/MODULES/governance diff=`0` |
| Maven/frontend/Python | NOT RUN（未运行） | docs/contract-only，product/migration/workflow diff=`0`；使用已核验exact-head CI，非阻断 |

最终结果：`PASS / GATEY_6C_PERMISSION_CONTRACT_REMEDIATED / OKX_TRADE_TRANSFER_SEMANTICS_ACKNOWLEDGED / G08_G09_LAYERED / REMOTE_WITHDRAW_FORBIDDEN / REMOTE_TRANSFER_CAPABILITY_NOT_MISREPRESENTED / NQ_FUNDS_MOVEMENT_DENY_PRESERVED / HARD_GATE_COUNTS_UNCHANGED / FIRST_REAL_ORDER_NOT_AUTHORIZED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`。

## Boundary and rollback

- 未触达backend、frontend、research、migration、scripts、deploy、`.github`、STATUS、ARCHITECTURE、MODULES或governance contract。
- 未commit/push/tag/deploy。
- 未提交时仅恢复本任务列出的6个文件；提交后使用独立revert commit。禁止使用破坏性Git命令覆盖其他改动。
