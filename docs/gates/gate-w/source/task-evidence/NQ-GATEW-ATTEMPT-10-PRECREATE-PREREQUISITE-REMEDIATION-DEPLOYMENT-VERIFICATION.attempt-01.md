# NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION-DEPLOYMENT-VERIFICATION — Attempt 01

## 1. 结论

本轮结论为：

```text
FAIL /
PRECREATE_REMEDIATION_DEPLOYMENT_VERIFICATION_FAILED /
CODE_REMEDIATION_REQUIRED /
ROLLED_BACK /
ATTEMPT_10_NOT_CREATED
```

Commit A 的 release supply chain、Linux root/POSIX/ownership、systemd 静态合同与离线回归均通过，但生产 canonical
`precreate-prerequisite` 仍返回 `INTERNAL_SANITIZED_READBACK_FAILURE`，因此不能识别运营 credential、permission、IP
allowlist 或 account scope blocker，也不能重新申请 Attempt-10 启动授权。

新 release 已通过 canonical installer 安装并保留；读取 canonical status 后，因它的唯一生产诊断目的未达成且服务器无 active
GateW runtime，已使用旧 release 的 canonical `activate` 与 `install-units` 恢复 current/unit links。未删除或原地修改任一
release。

## 2. 任务与固定基线

- 归属：NQ-only。
- 类型：
  `LINUX_DEPLOYMENT_VERIFICATION / EXACT_COMMIT_RELEASE_BUILD / IMMUTABLE_RELEASE_INSTALLATION / SANITIZED_PRECREATE_READBACK_VERIFICATION / RELEASE_SUPPLY_CHAIN_AUDIT / SYSTEMD_STATIC_VERIFICATION / TASK_EVIDENCE / COMMIT_AND_EXACT_HEAD_CI`。
- 起始分支：`dev`；worktree/staged clean。
- 起始 `HEAD == origin/dev == ddba18432df6d8368740f28a95e96f87ceac8efb`。
- 起始 governance CI：`30560747192 / completed / success / 10 of 10`。
- release source：`1561eb60cd46dc1a4618fde6651426c41d7c4e20`。
- release source CI：`30559245227 / completed / success / 10 of 10`。
- `1561eb60...` 到 `ddba1843...` 在 `scripts/gatew`、`backend`、`deploy/systemd` 的 production code drift=`0`。
- 起始 current：`/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- GateW：`IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- Attempt-09：`REJECTED`（已拒绝）。
- Attempt-10：`NOT_CREATED / NOT_AUTHORIZED`（未创建 / 未授权）。

## 3. Local exact-commit release build

从同一 source commit 创建两个独立 detached worktree，分别使用 Windows PowerShell 5.1 与 PowerShell 7 执行 canonical
builder。构建路径不同、起始间隔大于 2 秒；两个 worktree 均在构建后精确移除，只保留一份正式上传 bundle。

| 检查                                                    | 结果                                                               |
|---------------------------------------------------------|--------------------------------------------------------------------|
| source tree                                             | `EXACT_COMMIT / 1561eb60cd46dc1a4618fde6651426c41d7c4e20`          |
| manifest SHA-256                                        | `32df4e3575c0c3a546c95a30d17f005cb5ba07a5dfb8ede04c4a71389d48cd55` |
| bundle SHA-256                                          | `b87e7109a24fcfc0c4f90ae802701f89f77a10ebe14c0af0d7ab4ffc787c5fc6` |
| bundle size                                             | `54,216,704` bytes                                                 |
| declared artifacts / USTAR entries                      | `131 / 132`                                                        |
| manifest bytes / bundle bytes                           | identical=`true`                                                   |
| artifact path/size/mode/hash descriptor                 | identical=`true`                                                   |
| entry set/order                                         | identical=`true`                                                   |
| missing/extra/undeclared/absolute/Git/sensitive/reparse | `0/0/0/0/0/0/0`                                                    |

首次构建 A 的外层本地命令因短超时返回 `124`，但 canonical builder 子进程继续完成并生成完整 bundle；未重放构建。随后对 A/B
分别执行 canonical verifier，均返回 `PASS / IMMUTABLE_RELEASE_VERIFIED`，因此该命令编排问题不改变可复现性证据。

## 4. Server preflight

- hostname：`iZrj9gpab986sm4d0bb6agZ`。
- 审计时间：`2026-07-30T16:44:40Z`。
- NTP：`yes`。
- boot：`2026-05-15 21:33:54`。
- `sudo -n id`：root。
- current before：`c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- active GateW units / timers / jobs / `pwsh` worker：`0 / 0 / 0 / 0`。
- Attempt-09 worker：`inactive/dead`；MainPID=`0`；NRestarts=`0`。
- Attempt-09 之后的新 state directory：`0`。
- Attempt-10 / 新 acceptance clock：不存在。
- 历史 failed units 与旧 clock 文件保留；未执行 `reset-failed`。

宽泛 GateW process 匹配只看到管理 Java 所持有的本地 PostgreSQL backend，安全进程视图为 `postgres`，不是 GateW worker
residual。

## 5. Upload、install 与 release contract

上传目录：

```text
/home/admin/gatew-release-upload/1561eb60-precreate-verification/
```

本地/远端 bundle SHA-256、bundle size、manifest SHA-256 与 132 个 USTAR entries 完全一致。Staging canonical verifier 返回
`PASS / IMMUTABLE_RELEASE_VERIFIED`。

正式安装路径：

```text
/opt/nexus-quant/releases/1561eb60cd46dc1a4618fde6651426c41d7c4e20
```

Canonical installer/verifier 结果：

```text
PASS / ROOT_OWNED_RELEASE_INSTALLED
PASS / ROOT_OWNED_RELEASE_VERIFIED
PASS / IMMUTABLE_RELEASE_VERIFIED
posixVerified=true
nqgatewWritable=false
artifactCount=131
```

`.git=0`、symlink=`0`、server Git dependency=`0`。新 `PrerequisiteMain` launcher class 在 installed closed set 中精确存在 1
个。

## 6. Activation、systemd 与离线回归

Canonical activation 曾将 current 从 `c16f27c3...` 原子切换到 `1561eb60...`；随后新 release 的 canonical `install-units`
将 worker/fail-close unit links 固定到同一 release。未手工修改 symlink 或 unit。

`systemd-analyze verify` exit `0`。Worker 保持 `User=nqgatew / Restart=no / NoNewPrivileges=true / empty capabilities`
；fail-close 保持 `Type=oneshot / TimeoutStartSec=30s / Restart=no / PrivateNetwork=true / AF_UNIX / empty capabilities`
。既有 `cloudmonitor.service` warning 与 GateW 无关。

| 验证                                     | 结果                                 |
|------------------------------------------|--------------------------------------|
| installed verifier / installer self-test | `PASS / PASS`                        |
| control self-test                        | `PASS / 57 cases`                    |
| fail-close root self-test                | `PASS / 8 cases / 1415ms`            |
| remediation regression                   | `PASS / 32 cases / fail-close 878ms` |
| security regression                      | `PASS / 12 cases`                    |
| release reproducibility regression       | `PASS / 16 cases`                    |

首轮 remediation runner 以 `admin` 运行时，fail-close self-test 因 root ownership 命令 fail closed；按真实 root 权限复跑后通过。官方
security runner 在 Linux 上因 `Start-Process -WindowStyle Hidden` 不受支持而在 6 cases 后停止；仅在临时 runner 中删除该
UI-only 参数后 12/12 通过，installed release artifacts 未修改。所有本任务临时 harness residue=`0`；3 个其他既有
`/tmp/nq-gatew-*` 路径未触碰。

## 7. Canonical pre-create result

唯一允许的生产调用：

```text
sudo -n pwsh -NoProfile \
  -File /opt/nexus-quant/current/bin/gatew-okx-readonly-soak-control.ps1 \
  -Action precreate-prerequisite
```

返回的 closed-schema 脱敏结果：

```text
schemaVersion=gatew-precreate-prerequisite-result-v1
checkedAt=2026-07-30T17:02:10.3032262+00:00
releaseBindingVerified=true
postgresReachable=false
managementHealthy=false
killSwitchEngaged=false
credentialConfigured=false
activeCredentialCount=0
credentialType=UNKNOWN
credentialLocalStatus=UNKNOWN
permissionFactPresent=false
permissionFactFresh=false
readPermissionStatus=UNKNOWN
tradePermissionExpectedDisabled=false
withdrawPermissionExpectedDisabled=false
ipAllowlistStatus=UNKNOWN
blockerCodes=[INTERNAL_SANITIZED_READBACK_FAILURE]
diagnosticId=gatew-precreate-ee1781b5b8614ff69a2c68b4acd618b5
readyForAttemptCreation=false
diagnosticOnly=true
noSideEffect=true
credentialMaterialExposed=false
```

Action exit=`2`。`releaseBindingVerified=true`，但 management/PostgreSQL readback 未建立，根因分类为
`INTERNAL_SANITIZED_READBACK_FAILURE`。其他 false/UNKNOWN 字段是 internal fallback projection，不能解释为已经识别
`CREDENTIAL_NOT_CONFIGURED`、permission、IP allowlist 或 account scope 运营 blocker。

未读取 raw exception、stack trace、SQL、JDBC URL/password、owner/account 私密值或 raw provider response。

## 8. Canonical status 与 rollback

读取 Attempt-09 canonical status：

```text
activeState=inactive
subState=dead
mainPid=0
nRestarts=0
residualProcessCount=0
completionMarkerExists=false
acceptanceVerified=false
exitFactExists=true
```

Status 中 lifecycle/heartbeat 仍保留历史 `RUNNING` 记录，不覆盖 governance 中 Attempt-09 已拒绝的事实。因为新 release
的唯一生产诊断目的未达成、active runtime=`0`、Attempt-10 未创建，使用旧 release `c16f27c3...` 的 canonical `verify`、
`activate` 与 `install-units` 完成回滚。

最终：

```text
current=/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f
worker/fail-close unit links=c16f27c3...
systemd verify=PASS
active units/timers/jobs/worker=0/0/0/0
new release installed=true
```

## 9. Side-effect boundary

```text
credential material read=0
raw provider response read=0
manual SQL=0
OKX/private endpoint calls=0
production database writes=0
credential/permission/IP allowlist changes=0
Attempt-10 created=false
acceptance clock created=false
worker started=false
GateW units started=0
LIVE/trading writes=0
```

未执行 `prepare`、`start`、`start-acceptance-clock`、正式 fail-close/finalizer、Attempt-09 清理、旧 release 删除、WIP
合并、freeze/archive/tag。

## 10. Findings 与下一动作

- P0：无。
- P1：production canonical readback 仍为 `INTERNAL_SANITIZED_READBACK_FAILURE`，因此本次代码 remediation
  未达到部署验收；Attempt-10 必须继续阻断。
- P2：canonical response 的字段名为 `managementHealthy`，任务摘要使用过 `managementReachable`；实际 closed schema 由
  installed helper 自校验，本轮按真实字段记录。
- P3：Linux security regression runner 仍包含 Windows-only `-WindowStyle Hidden`；只影响临时测试编排，不影响 installed
  runtime。

当前 authority：

```text
work_batch=GateW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION
work_batch_status=DEPLOYMENT_VERIFICATION_FAILED|CODE_REMEDIATION_REQUIRED
work_batch_commit=1561eb60cd46dc1a4618fde6651426c41d7c4e20
work_batch_ci_run=30559245227
next_action=NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-INTERNAL-READBACK-FAILURE-RCA-AND-FIX
```

下一动作只允许脱敏代码级 RCA、最小修复与回归；不得直接 retry Attempt-10，不得修改生产 credential、permission、IP allowlist
或数据库，不得手工 SQL/ad-hoc 调用，也不授权再次部署、LIVE、交易写侧或 freeze。
