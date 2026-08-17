# GateY-6F exact pilot binding and read-only verification implementation — attempt-03 / SERVER_RUNTIME

## 任务分类与结论

- Task classification：`SECURE_SSH_ACCESS / SERVER_RUNTIME_QUALIFICATION / REAL_READONLY_VERIFICATION / EXACT_PILOT_BINDING`；NQ-only、高风险运行任务。
- Final decision：`BLOCKED / SERVER_RUNTIME_RELEASE_NOT_READY / DEPLOYMENT_AUTHORIZATION_REQUIRED / NO_DEPLOYMENT_PERFORMED / NO_OKX_CALL / FIRST_REAL_ORDER_NOT_AUTHORIZED`（阻断 / server runtime release 未就绪 / 需要独立部署授权 / 未部署 / 未调用 OKX / 第一笔真实订单未授权）。
- SSH 与 `sudo -n` hard gate 已通过，并真正进入 server runtime qualification；current release 精确为 GateW source commit，Git ancestry 证明不包含 GateY-6E accepted capability。任务按 release hard gate 立即停止，没有继续 DB schema、SoR、operator-input、credential/JIT、OKX 或 durable control-plane 路径。

## Local baseline 与 CI

```text
branch=dev
worktree=clean
staged=0
HEAD=origin/dev=b2181a2000bd7d56eef392d6a016c6e059486433
CI=32039066358 / completed / success / 10 jobs

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

GitHub CI exact `headSha` 与本地 baseline 匹配，10/10 jobs success。`git ls-remote` 首次遇到 GitHub HTTP 500，限定重试成功并确认远端 `dev` 精确为 baseline；current authority checker errors=`0`。

## SSH identity resolution 与连接

初始 SSH agent 不可用；effective config 仅列出默认 `~/.ssh/id_ed25519`，该 identity 对任务限定的 `admin`/`root` principals 均被服务器拒绝，因此当时按 anti-churn 规则保持 repo no-diff。随后 operator 通过安全路径提供 exact identity reference：

```text
identity_reference=~/.ssh/nq_gatew_soak_ed25519_v2
exists=true
type=regular file
owner=LING\Lingyu
content_read=0
hash_computed=0
```

使用 exact OpenSSH binary 和以下固定选项：

```text
-F NUL
-i <operator-provided identity reference>
BatchMode=yes
IdentitiesOnly=yes
PreferredAuthentications=publickey
PasswordAuthentication=no
KbdInteractiveAuthentication=no
StrictHostKeyChecking=yes
ConnectTimeout=15
```

连接结果：

```text
connected_principal=admin
hostname=iZrj9gpab986sm4d0bb6agZ
server_utc=2026-08-17T15:06:28Z
sudo_noninteractive=PASS
ssh_exit=0
```

未输出 private key/public key material，未修改 known_hosts、authorized_keys、sshd 或任何远端资源。

## Server runtime qualification

通过 `sudo -n` 执行的只读 qualification 得到：

```text
/opt/nexus-quant/current_resolved=/opt/nexus-quant/releases/b103069d8bfcecccba0b4d590317ddccc66898b9
release_source_commit=b103069d8bfcecccba0b4d590317ddccc66898b9
release_owner=root
release_group=root
release_mode=755
release_type=directory
release_manifest=/opt/nexus-quant/releases/b103069d8bfcecccba0b4d590317ddccc66898b9/release-manifest.json
release_manifest_sha256=f5b891e0d5547f25077a165a636ca6b40600bc8deedfe78f1110f7bddb44e4cb
ntp_synchronized=yes
java=openjdk 21.0.11
```

Relevant systemd units：6 个历史 GateW soak/fail-close units，全部 `inactive/dead`；active NQ worker/unit count=`0`。Docker 中仅观察到 `nq-gatew-postgres / postgres:16 / running`；未读取容器 env、DB password 或 volume 内容。

## GateY capability release hard gate

Local Git ancestry verification：

```text
b103069d... is ancestor of 0708bd9d... = true
0708bd9d... is ancestor of b103069d... = false

server release subject=ops(gatew): authorize Attempt-13 readonly soak
GateY-6E implementation subject=feat(gatey): implement first real order prerequisites
```

因此 server current release 明确不包含 GateY-6E trusted observation/control-plane capability，不能安全执行 GateY-6F。该事实满足：

```text
SERVER_RUNTIME_RELEASE_NOT_READY
DEPLOYMENT_AUTHORIZATION_REQUIRED
```

本任务没有部署授权，故未执行 git pull/build/upload/install、未替换 `/opt/nexus-quant/current`、未切 symlink、未 restart/reload systemd，也未使用本地 checkout 或临时 one-shot 绕过正式 runtime。

## Schema、LIVE/kill、SoR 与 operator inputs

Release hard gate 要求立即停止，因此以下后续事实没有查询或推导：

```text
application_profile=NOT_VERIFIED
postgresql_endpoint=NOT_VERIFIED
database_name=NOT_VERIFIED
flyway_highest_version=NOT_VERIFIED
server_schema_v41_compatibility=NOT_VERIFIED
server_live_state=NOT_VERIFIED
server_kill_state=NOT_VERIFIED
current_SoR_requery=NOT_RUN
operator_input_gate=NOT_RUN
used_operator_inputs=NONE
mechanical_ids_generated=0
expectedPilotScopeHash=NOT_COMPUTED
```

不能把 GateW PostgreSQL容器存在、历史V35/端口或本地`STATUS.md`的LIVE/kill事实写成server current GateY runtime事实。本地 authority继续保持`LIVE=DISABLED / kill_switch=ENGAGED`，但server持久化状态本轮未进入验证。

## Credential、permission/IP 与 OKX

```text
credential_metadata_reference=UNRESOLVED
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

未使用 mock，未输出 credential、Authorization/signature、raw private response、DB password 或 decrypted payload。

## Durable PilotScope 与 mutation counters

```text
LiveSession=NOT_CREATED
PilotScope=NOT_MATERIALIZED
PilotObservationSet=NOT_CREATED
scope_approval=NOT_CREATED
stored_fact_requery=NOT_RUN
stored_fact_preflight=NOT_RUN
ExecutionIntent=0
ExecutionReceipt=0

PLACE=0
CANCEL=0
TRANSFER=0
WITHDRAW=0
BORROW=0
LEVERAGE=0
worker_mutation=0
exchange_mutation=0
deployment=0
migration=0
symlink_switch=0
systemd_reload_restart=0
LIVE_enable=0
kill_disengage=0
soak_start=0
```

服务器写操作、append-only control-plane facts与远端rollback target均为0。

## Validation 与 findings

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| Git/origin baseline | PASS（通过） | `dev` clean；staged=`0`；local/remote exact head=`b2181a2...` |
| GitHub CI `32039066358` | PASS（通过） | `completed / success / 10 jobs`；只读核验 |
| current authority checker | PASS（通过） | baseline errors=`0`；GateY-6F `NOT_STARTED` |
| exact identity reference existence/owner/type | PASS（通过） | exists、regular file、owner verified；content/hash未读取 |
| admin BatchMode SSH | PASS（通过） | exact identity、public-key only、hostname/UTC返回；exit=`0` |
| `sudo -n true` | PASS（通过） | non-interactive privilege hard gate通过 |
| current release/manifest/NTP/Java/unit audit | PASS（通过） | 只读；release=`b103069d...`；manifest hash固定；NTP yes；Java 21；active NQ unit=`0` |
| GateY-6E release ancestry gate | BLOCKED（阻断） | GateY-6E implementation不在server current release中；需要独立部署授权 |
| DB schema/LIVE/kill/SoR/input/JIT/OKX | NOT RUN（未运行） | release hard gate先失败 |
| focused/full Maven、frontend、Python | NOT RUN（未运行） | 产品代码diff=`0`；exact-head CI已通过；不在本 blocker scope |
| final current authority checker | PASS（通过） | errors=`0`；GateY-6F保持`NOT_STARTED`，next action不变 |
| final doc links | PASS WITH HISTORICAL WARNINGS（通过并有历史 warning） | checked=`372`、warnings=`14`、errors=`0`；warning均来自既有append-only历史链接 |
| diff/allowlist/forbidden scope | PASS（通过） | `git diff --check` exit=`0`；allowlist expected/actual=`4/4`、missing/extra=`0/0`；staged=`0`；backend/frontend/research/scripts/deploy/`.github`/migration/STATUS/ROADMAP diff均为0；ledger removals=`0` |
| added-content safety guard | PASS（通过） | secret value/raw private payload/positive side-effect hits=`0/0/0`；仅检查本轮新增内容，避免历史ledger误报 |

- P0=`0`。
- P1=`1`：`SERVER_RUNTIME_RELEASE_NOT_READY / DEPLOYMENT_AUTHORIZATION_REQUIRED`，阻断schema、SoR、credential/OKX与durable control-plane路径。
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
docs/current/evidence/gate-y/NQ-GATEY-6F-EXACT-PILOT-BINDING-AND-READONLY-VERIFICATION-IMPLEMENTATION.attempt-03.md
```

- 本轮不 stage、commit、push、deploy，不修改产品代码、migration、CI、STATUS或ROADMAP。
- 回滚：提交前逐文件反向应用上述4个文档diff；服务器没有写操作，无远端rollback。
- 建议commit：`docs(gatey): record GateY-6F server release blocker`。
- 下一步：需要独立、明确的server deployment authorization与部署计划，绑定exact GateY release artifact、schema/migration策略、health/rollback/stop conditions；本task不得自行部署。部署与exact-head CI完成后，以attempt-04重新执行server runtime/schema hard gate，不得覆盖attempt-01/02/03。
