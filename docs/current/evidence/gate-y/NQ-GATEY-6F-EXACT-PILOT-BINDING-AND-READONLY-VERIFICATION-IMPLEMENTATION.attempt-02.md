# GateY-6F exact pilot binding and read-only verification implementation — attempt-02 / SERVER_RUNTIME

## 任务分类与结论

- Task classification：`CONTROLLED_SERVER_RUNTIME / REAL_READONLY_VERIFICATION / EXACT_PILOT_BINDING / DURABLE_CONTROL_PLANE_FACTS`；NQ-only、高风险运行任务。
- Final decision：`BLOCKED / SERVER_RUNTIME_NOT_VERIFIED / SERVER_SSH_ACCESS_REQUIRED / NO_DEPLOYMENT_PERFORMED / NO_CREDENTIAL_READ / NO_OKX_CALL / NO_OKX_MUTATION / FIRST_REAL_ORDER_NOT_AUTHORIZED`（阻断 / 服务器 runtime 未验证 / 需要既有安全 SSH 访问 / 未部署 / 未读取凭证 / 未调用 OKX / 无 OKX mutation / 第一笔真实订单未授权）。
- 阻断发生在 server hostname/release/schema 命令执行之前；服务器端口可达，但本会话没有可被接受的 non-interactive public-key identity。未尝试密码、交互登录、SSH key/config读取、部署、migration、symlink、systemd restart 或临时 raw HTTP/SQL。

## Local baseline 与 CI

```text
branch=dev
worktree=clean
staged=0
HEAD=origin/dev=b7c5a2e046de36522843c32ca726bc9850b1d4dc
CI=32037619314 / completed / success / 10 jobs

accepted_batch=GateY-6E
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-6F
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6F-EXACT-PILOT-BINDING-AND-READONLY-VERIFICATION-IMPLEMENTATION
LIVE=DISABLED
kill_switch=ENGAGED
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
GATEY_PILOT_SOAK=NOT_STARTED
```

GitHub 远端 `refs/heads/dev` 与 CI `headSha` 均精确匹配本地 baseline；只读核验没有触发 workflow 或外部写操作。Current authority checker baseline errors=`0`。

## Secure SSH discovery 与 hard gate

仓库历史 evidence 记录过两个正式 target：

- `root@47.251.74.35:22`；
- 后续 server tasks 使用 `admin@47.251.74.35:22`、用户当时指定的 private-key path reference 与 `sudo -n`。

本 attempt 没有附带或在仓库中记录可调用的 IdentityFile path。未查看用户 `.ssh`、private key、SSH agent public-key material 或 SSH config 内容。仅执行两次最小 `hostname` authentication probe：

```text
root target:
  BatchMode=yes
  PasswordAuthentication=no
  KbdInteractiveAuthentication=no
  StrictHostKeyChecking=yes
  result=Permission denied (publickey,password)
  exit=255

admin target:
  BatchMode=yes
  PasswordAuthentication=no
  KbdInteractiveAuthentication=no
  StrictHostKeyChecking=yes
  result=Permission denied (publickey,password)
  exit=255
```

两次 probe 均只尝试 public-key authentication；没有远端命令成功执行，没有修改 known_hosts、sshd、authorized_keys 或任何服务器资源。根据 server preflight hard gate，本轮在此停止。

## Server runtime release/schema preflight

由于 SSH authentication hard gate 未通过，下列事实全部为 `NOT_VERIFIED / NOT_RUN`：

```text
hostname=NOT_VERIFIED
utc_clock=NOT_VERIFIED
ntp=NOT_VERIFIED
current_release_commit=NOT_VERIFIED
/opt/nexus-quant/current_identity=NOT_VERIFIED
active_systemd_units=NOT_VERIFIED
postgresql_endpoint=NOT_VERIFIED
database_name=NOT_VERIFIED
schema_version=NOT_VERIFIED
flyway_highest_version=NOT_VERIFIED
application_profile=NOT_VERIFIED
server_live_state=NOT_VERIFIED
server_kill_state=NOT_VERIFIED
```

不得将历史 GateW release、DB V35、loopback端口或服务器状态复制成 GateY-6F current facts。也不能据此判定服务器 runtime release/schema ready 或 not ready；准确分类是 `SERVER_RUNTIME_NOT_VERIFIED / SERVER_SSH_ACCESS_REQUIRED`。

## Current SoR 与 operator inputs

Server DB connectivity、formal repository/service 和 accepted server config 均未进入，因此 current SoR exact-one requery 未运行：

```text
owner=UNRESOLVED
OKX_LIVE_account=UNRESOLVED
credential_metadata_reference=UNRESOLVED
strategy_release=UNRESOLVED
RiskLimitSet=UNRESOLVED
endpoint_policy_identity_digest=UNRESOLVED
provider_identity_digest=UNRESOLVED
worker_runtime_identity_digest=UNRESOLVED
```

Operator business inputs也未进入 completeness 判定；`used_operator_inputs=NONE`。本 attempt 未生成机械 IDs (`Idempotency-Key`、`sessionId`、`pilotScopeId`、`approvalId`)；未计算或猜测 `expectedPilotScopeHash`。Attempt-01 的 sanitized input template 继续保留，但不能替代 server current SoR requery 或 operator authority。

## Credential、permission/IP 与 OKX

```text
credential_metadata_read=0
credential_material_read=0
permission_ip_verification=NOT_RUN
account/config_calls=0
account/instruments_calls=0
account/trade-fee_calls=0
account/balance_calls=0
public/time_calls=0
OKX_API_CALLS=0
retry=0
trusted_prerequisite_collection=NOT_RUN
```

未使用 mock，未输出 credential、Authorization/signature、raw private response、DB password 或 decrypted payload。READ/TRADE/WITHDRAW/IP current facts均未声称通过。

## Durable control-plane facts 与 preflight

```text
LiveSession=NOT_CREATED
PilotScope=NOT_MATERIALIZED
PilotObservationSet=NOT_CREATED
scope_approval=NOT_CREATED
stored_fact_requery=NOT_RUN
stored_fact_preflight=NOT_RUN
session_id=NONE
pilot_scope_id=NONE
pilot_scope_hash=NONE
approval_id=NONE
ExecutionIntent=0
ExecutionReceipt=0
```

不存在本轮 append-only server facts，也没有 server-side cleanup/rollback target。

## Mutation counters

```text
PLACE=0
CANCEL=0
TRANSFER=0
WITHDRAW=0
BORROW=0
LEVERAGE=0
ExecutionIntent=0
ExecutionReceipt=0
worker_mutation_start=0
exchange_mutation=0
deployment=0
migration=0
symlink_switch=0
systemd_restart=0
LIVE_enable=0
kill_disengage=0
soak_start=0
```

## Validation 与 findings

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| Git/origin baseline | PASS（通过） | `dev` clean；staged=`0`；local/remote exact head=`b7c5a2e...` |
| GitHub CI `32037619314` | PASS（通过） | `completed / success / 10 jobs`；只读核验 |
| current authority checker | PASS（通过） | baseline errors=`0`；GateY-6F `NOT_STARTED` |
| root BatchMode SSH hostname probe | BLOCKED（阻断） | exit=`255 / Permission denied`；remote command executed=`0` |
| admin BatchMode SSH hostname probe | BLOCKED（阻断） | exit=`255 / Permission denied`；remote command executed=`0` |
| server release/schema/LIVE/kill preflight | NOT RUN（未运行） | SSH authentication hard gate先失败 |
| focused no-mutation tests | NOT RUN（未运行） | 未进入server runtime；产品代码diff=`0`；exact-head CI已通过 |
| current SoR / operator inputs / JIT / OKX | NOT RUN（未运行） | 更早的server access hard gate阻断 |
| frontend/Python/full Maven | NOT RUN（未运行） | 产品代码diff=`0`，且不在本 blocker scope |
| final current authority checker | PASS（通过） | errors=`0`；GateY-6F保持`NOT_STARTED`，next action不变 |
| final doc links | PASS WITH HISTORICAL WARNINGS（通过并有历史 warning） | checked=`369`、warnings=`14`、errors=`0`；warning均来自既有append-only历史链接 |
| diff/allowlist/forbidden scope | PASS（通过） | `git diff --check` exit=`0`；allowlist expected/actual=`4/4`、missing/extra=`0/0`；staged=`0`；backend/frontend/research/scripts/deploy/`.github`/migration/STATUS/ROADMAP diff均为0；ledger removals=`0` |
| added-content safety guard | PASS（通过） | secret value/raw private payload/positive side-effect hits=`0/0/0`；仅检查本轮新增内容，避免历史ledger误报 |

- P0=`0`。
- P1=`1`：`SERVER_SSH_ACCESS_REQUIRED / SERVER_RUNTIME_NOT_VERIFIED`，阻断release/schema、SoR、operator-input、credential/OKX和durable control-plane路径。
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
docs/current/evidence/gate-y/NQ-GATEY-6F-EXACT-PILOT-BINDING-AND-READONLY-VERIFICATION-IMPLEMENTATION.attempt-02.md
```

- 本轮不 stage、commit、push、deploy，不修改产品代码、migration、CI、STATUS 或 ROADMAP。
- 回滚：提交前逐文件反向应用上述 4 个文档 diff；禁止使用整仓 reset/checkout。
- 建议 commit：`docs(gatey): record GateY-6F server access blocker`。
- 下一步：operator 通过安全附件或既有受控 secret manager 提供可由 `ssh` 直接引用的 private-key path reference，或预先把有效 identity 加载到本会话可用的 SSH agent；不要在聊天中粘贴密码或私钥。随后以 attempt-03 重跑同名 SERVER_RUNTIME task，不得覆盖 attempt-01/02。
