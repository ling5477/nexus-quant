# NQ-GATEY-6F Server Runtime Deployment attempt-01

## 1. Deployment decision

`BLOCKED / DEPLOYMENT_IMPLEMENTATION_DEFECT / DEPLOYMENT_TARGET_NOT_RESOLVED / GATEY_QUALIFICATION_SYSTEMD_CONTRACT_MISSING / CANONICAL_DATABASE_TARGET_MISSING / NO_SERVER_MUTATION / NO_PILOT`（阻断 / 部署实现合同不完整 / 未执行服务器变更 / 未进入 pilot）。

当前 committed GateY release contract 能构建、验证、安装并原子切换不可变 release，但没有 committed GateY qualification systemd unit、unit/env installer、canonical database target 或 runtime start/stop/rollback orchestration。任务要求在接触服务器前从当前仓库唯一解析这些事实；因此本轮在本地 hard gate 停止，不允许用临时 unit、手写命令或 GateW unit 代替。

## 2. Task classification 与 baseline

- Task ID：`NQ-GATEY-6F-SERVER-RUNTIME-DEPLOYMENT`。
- Classification：`PRODUCTION_DEPLOYMENT + IMMUTABLE_RELEASE_INSTALLATION + DATABASE_PREDEPLOYMENT_GATE + LOOPBACK_RUNTIME_ACTIVATION + DEPLOYMENT_EVIDENCE`；NQ-only、L 级。
- Branch：`dev`；worktree clean；staged=`0`。
- Starting exact HEAD：`506b38549a139bafb25bf2ab5820aecac3792f1b`。
- `origin/dev`：`506b38549a139bafb25bf2ab5820aecac3792f1b`；`git fetch origin` 成功。
- Exact-head CI：`NQ CI Baseline` run=`32389011832`，`completed / success`，headSha 与 exact HEAD 一致；10 个 jobs 全部 success。
- Authority：GateY-6F=`NOT_STARTED`（未开始）、FIRST_REAL_ORDER/MICRO_LIVE=`NOT_AUTHORIZED`（未授权）、soak=`NOT_STARTED`、LIVE=`DISABLED`（关闭）、kill switch=`ENGAGED`（已接合）。

## 3. Scope 与 files inspected

已读取并核对：

- `scripts/gatey/build-gatey-readonly-release.ps1`
- `scripts/gatey/gatey-readonly-release-contract.psm1`
- `scripts/gatey/install-gatey-readonly-release.ps1`
- `scripts/gatey/invoke-gatey-readonly-deployment-contract.ps1`
- `scripts/gatey/tests/run-gatey-readonly-release-contract-regression.ps1`
- `scripts/gatey/tests/run-gatey-readonly-linux-installation-regression.ps1`
- `backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml`
- `deploy/systemd/nq-gatew-soak@.service` 与 `nq-gatew-soak-failclose@.service`，仅作为 frozen reference
- GateY-6F implementation、remediation、Security Review attempt-04 与 server runtime attempt-03 evidence
- `docs/current/STATUS.md`、root/current README 与项目 deployment boundary

未读取 `.env`、credential material、database password、SSH private key content、server logs、backup 或 raw provider response。

## 4. Exact-head CI

Run `32389011832`：

```text
workflow=NQ CI Baseline
status=completed
conclusion=success
headSha=506b38549a139bafb25bf2ab5820aecac3792f1b
jobs=10/10 success
```

成功 jobs 包含 CI security smoke、no-outbound guard、Backend Maven test、PostgreSQL/Flyway smoke、Frontend build/E2E、Secret scan、Research quality gate 与 Diff check。

## 5. Deployment target resolution

从 accepted evidence 与 committed config 可解析：

```text
server=<canonical GateW/GateY host, address redacted>
expected hostname=iZrj9g...m4d0bb6agZ
ssh principal=admin
ssh port=22
release root=/opt/nexus-quant/releases/<releaseId>
current pointer=/opt/nexus-quant/current
service user=nq-gatey-readonly
qualification profile=gatey-readonly-qualification
runtime address=127.0.0.1
runtime port=18890
management ingress=loopback only
```

无法从 current committed repository 唯一解析：

```text
GateY qualification systemd unit name/path
systemd ExecStart/WorkingDirectory/User/Restart/timeout contract
unit environment source and permissions
canonical PostgreSQL endpoint/database identity
safe injection path for NQ_GATEY_QUALIFICATION_DB_URL/USER/PASSWORD
runtime start/stop/rollback orchestration
post-activation log/counter evidence source
```

结果：`BLOCKED / DEPLOYMENT_TARGET_NOT_RESOLVED`。

## 6. Deployment implementation defect evidence

### 6.1 Release artifact closed set

Canonical builder只打包：

```text
app/nq-app.jar
config/application-gatey-readonly-qualification.yml
bin/gatey-readonly-release-contract.psm1
bin/invoke-gatey-readonly-deployment-contract.ps1
bin/install-gatey-readonly-release.ps1
release-manifest.json
```

没有 GateY systemd unit、unit installer、environment contract 或 runtime launcher artifact。

### 6.2 Installer capability

`install-gatey-readonly-release.ps1` 只支持：

```text
install
verify
activate
```

它能执行 root ownership/POSIX/link/hash/no-overwrite/service-user-write-denial 与 atomic current pointer，但不会安装/reload/start/stop systemd unit，也不会验证或注入 qualification DB runtime configuration。

### 6.3 Runtime profile

`application-gatey-readonly-qualification.yml` 明确要求：

```text
NQ_GATEY_QUALIFICATION_DB_URL
NQ_GATEY_QUALIFICATION_DB_USER
NQ_GATEY_QUALIFICATION_DB_PASSWORD
NQ_GATEY_RELEASE_ID
NQ_GATEY_SOURCE_COMMIT
```

其中 DB target和安全注入位置没有 committed deployment owner。临时读取服务器 env、复用 GateW container env、手写 environment file 或在命令行传递 password 都违反本任务边界。

### 6.4 Existing systemd files

仓库仅存在 frozen GateW soak/fail-close template units。它们属于历史 GateW runtime，不能被重命名、复用或修改来启动 GateY qualification runtime。

结果：`BLOCKED / DEPLOYMENT_IMPLEMENTATION_DEFECT`。

## 7. Immutable release

```text
Source commit=506b38549a139bafb25bf2ab5820aecac3792f1b
Release ID=NOT_BUILT
Manifest SHA-256=NOT_COMPUTED
Application artifact SHA-256=NOT_COMPUTED
Artifact verification=NOT_RUN
Migration inventory=NOT_REBUILT
```

目标解析 hard gate 先失败，因此没有运行 canonical builder，也没有在 `target/gatey-readonly-releases` 创建 release。未把 CI artifact或历史 manifest当作本轮 deployable release。

## 8. Server preflight、Flyway 与 rollback

```text
Server SSH preflight=NOT_RUN
Server safety state=NOT_VERIFIED
Previous runtime=NOT_REQUERIED
Server Java=NOT_REQUERIED
Flyway history before=NOT_READ
Pending migrations=NOT_COMPUTED
Rollback/database recovery hard gate=NOT_RUN
Predeployment evaluator=NOT_RUN
```

本轮没有用本地 `STATUS.md` 或历史 GateW事实替代服务器 current facts。由于未接触服务器，不能声明当前 server LIVE/kill、Flyway、process、disk或current pointer状态通过。

## 9. Installation、activation 与 health

```text
Release upload=0
Release installation=0
POSIX verification=NOT_RUN
Atomic current activation=0
Production migration=0
Systemd/runtime activation=0
Loopback health=NOT_RUN
Runtime identity=NOT_RUN
Flyway history after=NOT_READ
Rollback state=current unchanged by this task
```

没有执行 mkdir/cp/scp/chown/chmod/ln/systemctl/DB write/backup/migration或server-side build。

## 10. Startup and mutation side effects

因为 runtime 未启动，以下全部为0：

```text
Credential metadata read=0
Credential material read=0
Decrypt=0
OKX GET/POST=0/0
PLACE/CANCEL=0/0
Transfer/Withdraw=0/0
ExecutionIntent/ExecutionReceipt delta=0/0
Order/Ledger delta=0/0
LIVE enable=0
Kill disengage=0
```

未调用 `observePrerequisites()`、permission probe、pilot、first real order 或 soak。

## 11. Findings

### P0

- 无。

### P1

- `DEPLOYMENT_RUNTIME_CONTRACT_INCOMPLETE`：缺少 committed GateY qualification systemd unit、env ownership、canonical DB target和启动/停止/回滚编排，主部署链不可安全执行。

### P2

- 无。本轮不把尚未执行的 production rollback/health验证升级为额外 finding；它们被更早的 P1 implementation defect阻断。

## 12. Side-effect counters

```text
Server SSH read = 0
Server SSH write = 0

Release upload = 0
Release installation = 0
Atomic current switch = 0
Systemd start/restart = 0

Production Migration = 0
Production Backup = 0
Production Restore Drill = 0
Production Restore = 0

Credential metadata read = 0
Credential material read = 0
Decrypt = 0

OKX GET = 0
OKX POST = 0
PLACE = 0
CANCEL = 0
Transfer = 0
Withdraw = 0

ExecutionIntent delta = 0
ExecutionReceipt delta = 0
Order delta = 0
Ledger delta = 0

LIVE enable = 0
Kill disengage = 0
```

## 13. Final decision 与 next action

- Deployment decision：`BLOCKED / DEPLOYMENT_IMPLEMENTATION_DEFECT / DEPLOYMENT_TARGET_NOT_RESOLVED / GATEY_QUALIFICATION_SYSTEMD_CONTRACT_MISSING / CANONICAL_DATABASE_TARGET_MISSING / NO_SERVER_MUTATION / NO_PILOT`。
- Commit recommendation：`DO NOT COMMIT / PENDING DEPLOYMENT CONTRACT REMEDIATION`（不要提交 / 等待部署合同整改）。
- Next concrete action：`NQ-GATEY-6F-SERVER-RUNTIME-DEPLOYMENT-CONTRACT-REMEDIATION`。
- Remediation必须新增并独立审查 committed systemd unit、safe env ownership、canonical DB target、start/stop/rollback/health/counter contract；exact-head CI成功后重新执行本 deployment attempt，不得边部署边修复。
- 本轮未执行 `git add`、commit、push或tag；STATUS/ROADMAP不变。
