# GateY-6F Exact Pilot — attempt-04

## Attempt-04 首次执行：生产 hard gate BLOCKED

结论：`BLOCKED / GATEY_6F_EXACT_PILOT_ATTEMPT_04_NOT_QUALIFIED / EXACT_PILOT_SCOPE_NOT_AUTHORIZED / RUNTIME_EXACT_HEAD_NOT_ALIGNED / NO_REAL_ORDER`（阻断 / exact pilot scope 未获授权 / runtime 未对齐）。

首次执行基线：

```text
local exact HEAD=1169fdbc1522fe14141083d8043ac184e30fbd3b
local exact-head CI=32551067133 / completed / success / 11 of 11
production release/source=2cee199081bc338b4dd5c05d2aff867b7a418202
production health=UP
MainPID=1028560
NRestarts=0
Flyway=V41 / failed=0
LIVE=false
kill=ENGAGED / version=1 / eventCount=1
mutationRuntimeBound=false
```

Production DB 显式 `READ ONLY` 查询：

```text
live_sessions=0
pilot_scope_bindings=0
prerequisite_observations=0
pilot approvals=0
exact binding create/consume events=0/0
execution intents/receipts=0/0
```

因此不存在可复用的 operator exact values、PilotScope、trusted observation 或 independent approval；未部署、未读取 credential material、未调用 OKX、未写 DB、未创建 binding。首次 DB 命令有两次非事实型失败：一次 remote shell quoting 未进入 psql；一次只读事务猜错 kill 表名后中止。随后按 V35 canonical 表名重跑并取得上述 V41/kill 事实，历史失败未删除或伪写为 PASS。

## Resume baseline 与 control-surface implementation

- Resume source baseline：`HEAD == origin/dev == 1169fdbc...`，worktree 起始 clean，Authority errors=0。
- 主 skill：`java-backend-maintenance`；`nq-dh-workflow-router` 固定 NQ/Gate/LIVE/credential 边界；`nq-java-engineering-standard` 与 `java-backend-regression-tests` 约束实现和回归；`nq-docs-writer` 仅记录已验证事实。
- 当前阶段结论：`IMPLEMENTED / EXACT_PILOT_SCOPE_CONTROL_SURFACE_READY / EXACT_OPERATOR_INPUT_CLOSED_SCHEMA / CREATOR_APPROVER_SEPARATION_ENFORCED / EXACT_SCOPE_DIGEST_APPROVAL_ENFORCED / BINDING_CREATE_ONLY / NO_CONSUME_SURFACE / NO_MIGRATION / NO_PROVIDER_IO_IN_TESTS / P0_0 / P1_0 / PENDING_EXACT_HEAD_CI`（控制面已实现 / 等待 exact-head CI）。

### 正式 operator control surface

- 新增 `scripts/gatey/invoke-gatey-exact-pilot-scope.ps1`，仅支持 `ValidateInput/Invoke` 两个 single-purpose action。
- 输入为 root-controlled、non-secret closed JSON schema；必须显式提供 creator/approver、account/credential reference、release/risk、instrument/side/LIMIT price/quantity/notional、window 与 correlations。
- unknown/top-level/nested secret-shaped field、self approval、MARKET fallback、missing input 全部 fail closed。
- Linux Invoke 要求 root，input ownership 仅允许 `root:nq-gatey-readonly:640` 或 `root:root:600`；release 必须由 canonical verifier确认并等于 current pointer。
- CLI 以 non-web Spring mode运行；只装配 single-purpose `ExactPilotScopeControlPlane`，没有 debug HTTP、raw SQL、JShell、reflection、generic bean executor 或 consume入口。
- root environment 在进入 service user 前按 GateY runtime/secret allowlist清理，避免继承无关 root环境。
- 该 control script 已加入 immutable release closed set，artifact count由13增至14；installer/runtime contract设计未改。

### Exact scope authorization 与 independent approval

- `exact-pilot-scope-authorization.v1` canonical digest绑定 exact runtime/release/manifest/server、owner/account/credential、single instrument/side/LIMIT price/quantity/notional、risk/kill、pilot window、creator/approver 与 binding correlation。
- creator必须是 session owner并当前持有`OPERATOR`；approver必须不同且当前持有`LIVE_APPROVER`。角色 preflight发生在 trusted provider collection 前，之后在 persistence transaction 内再次验证。
- 复用 V39 `live_session_events`，按 sequence原子追加 `AUTHORIZE_EXACT_PILOT_SCOPE` 与 `APPROVE_EXACT_PILOT_SCOPE`；未新增 table/migration或第二套 account/risk fact。
- metadata、canonical scope、event payload hash、principal、reason、approvedAt/expiresAt全部在读取时重算复核；任一 scope field变化、tamper或expiry使旧 approval失效。
- `ExactPilotBindingService.create/validate` 现在强制存在匹配且未过期的 exact scope approval；control surface只调用 create+validate，API中不存在 consume。
- 现有 PilotScope materialization继续拥有 trusted prerequisite collection，随后复用现有 pilot approval；任何中间失败只留下可审计 control-plane facts，不产生交易 mutation。

## Validation

| Command / check | Result |
| --- | --- |
| focused Java | PASS（通过）；core/infra/app 22 tests，failures/errors/skipped=`0/0/0`；最终 role-preflight增量8 tests再次通过 |
| full Maven final | PASS（通过）；23 modules、330 reports、1581 tests、failures/errors/skipped=`0/0/48` |
| exact control script regression | PASS（通过）；7/7，provider/mutation/consume=`0/0/0` |
| GateY release regression | PASS（通过）；29 cases，14-artifact closed set，server mutation=0 |
| GateY runtime deployment regression | PASS（通过）；51 cases，contract self-test46，server mutation=0 |
| Java governance | PASS（通过）；release21 / Boot3.5.10 / Framework6.2.15 |
| Java Shadow | `VIOLATION_FOUND`（仅 Shadow）；existing=144、ruleset expansion=14、new-code=0 |
| Authority / migration | PASS（通过）；authority errors=0；V40 git-blob contract PASS；V1～V41 unchanged |
| Linux installer regression | `BLOCKED / DISPOSABLE_ROOT_LINUX_REQUIRED`；Windows主机未进入测试，非产品finding；后续 exact-head CI/production verifier必须覆盖 |

执行历史保留：首次 test-compile因既有 test 未注入新 authorization port失败，修正 fake并新增负向回归后通过；首次 PowerShell nested-secret case因 fixture未序列化额外字段错误返回0，修正 fixture后7/7通过；首次 Shadow发现`System.out` P2，改为结构化 SLF4J marker后new-code=0；临时 Shadow worktree只用于绕过既有不可读 artifacts，结束后移除。

## 当前边界与后续 resume

```text
implementation commit=PENDING
implementation exact-head CI=PENDING
runtime exact-head alignment=NOT RUN
operator exact scope input=NOT PROVIDED
credential JIT=0
OKX read-only GET=0
scope/approval/binding production writes=0
binding consumed=false / binding not materialized
PLACE/CANCEL/transfer/withdraw=0/0/0/0
ExecutionIntent/Receipt/Order/Fill/Ledger mutation=0/0/0/0/0
LIVE enable=0
kill disengage=0
```

下一步仍在本 Attempt-04 内：精确提交 implementation并等待 exact-head CI GREEN；随后按 accepted immutable contract对齐 runtime。若此时安全输入源仍没有全部 operator exact values，必须返回 `BLOCKED / OPERATOR_EXACT_SCOPE_INPUT_REQUIRED / NO_REAL_ORDER`，不得自动选择任何交易参数。

## Attempt-04 第二次 resume：runtime 已对齐，operator exact input 仍缺失

最终结论：

```text
BLOCKED /
GATEY_6F_EXACT_PILOT_ATTEMPT_04_NOT_QUALIFIED /
OPERATOR_EXACT_SCOPE_INPUT_REQUIRED /
RUNTIME_EXACT_HEAD_ALIGNED /
NO_CREDENTIAL_JIT /
NO_OKX_CALL /
NO_PROVIDER_MUTATION /
NO_PILOT_MATERIALIZATION /
NO_REAL_ORDER /
FIRST_REAL_ORDER_NOT_AUTHORIZED /
MICRO_LIVE_NOT_AUTHORIZED /
LIVE_DISABLED /
KILL_ENGAGED /
P0_0 /
P1_0
```

### Commit 与 CI

- Control-surface implementation commit=`6c732fe26c8e1aef2d9f83150200ec740fff9b4d`；exact-head CI run=`32571525343 / completed / success / 11 of 11`。
- 首次 deployment 发现 previous GateY rollback release 的 13-artifact closed set 无法被新增 14th exact-pilot artifact 的 verifier 验证，canonical `Activate` 在 pointer mutation 前返回 `BLOCKED / RELEASE_REQUIRED_ARTIFACT_MISSING`。
- Forward remediation commit=`1292b0e49d62ebb5f3f3809be75c6216b66277f8`；只允许 previous GateY release 缺少新增 exact-pilot control artifact，当前 release 仍强制 14 artifacts，其他 legacy 必需 artifact 缺失仍拒绝。
- Remediation exact-head CI run=`32572645294 / completed / success / 11 of 11`。本地 release/runtime/ExactPilot regressions=`31/51/7` cases，deployment boundary PASS，authority errors=0；本机 WSL 因无 `pwsh` 未执行两项 root-Linux disposable regression，当前 CI workflow 也未直接调用这两份脚本。生产 canonical installer/POSIX verifier 与 runtime activation另行真实通过，不把未运行的 disposable scripts 写成 PASS。

### Immutable deployment 与 runtime identity

首次 candidate：

```text
release/source=6c732fe26c8e1aef2d9f83150200ec740fff9b4d
manifestSha256=342510edbe48382e9b19eff88614e5e789b5d15fb98bf457420f885846d1912e
transportSha256=4af696336e86d6f7e77b2488071eedd7e87a33862aa48c8f5e7d8de14f587798
installedVerified=true
activation=BLOCKED / RELEASE_REQUIRED_ARTIFACT_MISSING
pointerMutation=false
databaseMutation=false
```

失败后已恢复旧 `runtime.env` 与 release `2cee199081bc338b4dd5c05d2aff867b7a418202`；恢复态 health=UP、MainPID=`1058544`、NRestarts=0。该历史 blocker 未删除或改写为 PASS。

最终 remediation release：

```text
releaseId=1292b0e49d62ebb5f3f3809be75c6216b66277f8
sourceCommit=1292b0e49d62ebb5f3f3809be75c6216b66277f8
manifestSha256=e5895f4730cf05b492f566ca1718301c5a8779a1c9d48efbc2aa92fbe12cee3f
transportSha256=22e5e5cc2ba8fd4efa443ea7e835de6440c76f474716f84b38fc79058d7f35df
transportBytes=36754944
artifactCount=14
schemaTarget=V41
currentPointer=/opt/nexus-quant/releases/1292b0e49d62ebb5f3f3809be75c6216b66277f8
health=UP
MainPID=1060386
NRestarts=0
LIVE=false
kill=ENGAGED
mutationRuntimeBound=false
diagnosticOnly=true
tradingAuthorization=false
```

Canonical deployment evidence：`gatey-readonly-deployment-20260822T123118Z-attempt-04-resume.json`，decision=`PASS / GATEY_READONLY_RELEASE_ACTIVATED_HEALTHY`，rollbackRequired=false。未运行 migration，Flyway 保持 V41 / failed=0 / pending=0。

执行中 immutable install=`2`，超过任务预期的 `0/1`：第一份为未激活的失败 candidate，第二份为 forward remediation exact-head；两份均 root-owned immutable、未覆盖或删除，作为真实 deployment history 保留。Atomic pointer activation实际成功=`1`。

### Operator input hard gate 与 side effects

仅检查正式配置目录的文件元数据；目录中只有既有 runtime/secret/DB reference 文件及 deployment env 备份，不存在 operator exact-scope JSON。未读取 credential bytes，未从聊天选择或推导 owner/account/credential、release/risk、instrument/side、price/quantity/notional、window、creator或approver。

因此未运行 `ValidateInput` 或 `Invoke`，并在 credential JIT、OKX、scope/approval/binding write 之前停止。最终 production `READ ONLY` 聚合结果：

```text
live_sessions=0
pilot_scope_bindings=0
pilot_prerequisite_observations=0
operator_approvals=0
live_session_events=0
execution_intents=0
execution_receipts=0
flyway_failed=0
flyway_current=V41
```

一次只读计数命令因本地 PowerShell/SSH quoting 错误未进入 `psql`，意外打印远端 shell 的非 secret 进程环境；没有读取 runtime/secret/pgpass 内容，随后使用单条 `BEGIN TRANSACTION READ ONLY` 聚合查询成功。该失败历史保留，不表述为 PASS。

最终 side-effect boundary：

```text
credential JIT=0
OKX GET/POST=0/0
unexpected/fallback endpoint=0/0
PLACE/CANCEL/transfer/withdraw=0/0/0/0
scope/approval/binding create/consume=0/0/0/0
ExecutionIntent/ExecutionReceipt=0/0
real Order/Fill/trading Ledger delta=0/0/0
LIVE enable=0
kill disengage=0
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
SOAK=NOT_STARTED
```

恢复条件：operator 通过服务器外部安全渠道创建一份满足 closed JSON schema、root ownership 与独立 creator/approver要求的 non-secret exact scope input，并仅提供其服务器路径 reference；不得在聊天中提供 API key、secret 或 passphrase。恢复后继续同一 Attempt-04，不创建 Attempt-05。
