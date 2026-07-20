# NQ-GATEW-SOAK-START-CONTRACT-REMEDIATION-AND-IMMUTABLE-RELEASE-REBUILD — Attempt 01

## Task classification

- 主类型：`GATEW_TOOLING_REMEDIATION`；辅助类型：
  `SECURITY_CONTRACT_FIX / ACCEPTANCE_CLOCK_FIX / SERVER_RUNTIME_CLEANUP / IMMUTABLE_RELEASE_REBUILD / FULL_OFFLINE_ACCEPTANCE`。
- 归属：NQ-only、L 级高风险任务；GateW 保持 `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- 本记录当前覆盖实现、本地验证、server candidate immutable release 与完整正式离线验收。Implementation commit、exact-head
  CI、final release、`current` 切换和 final 离线验收在 Commit A 前均为 `PENDING`。

## Scope and boundaries

- 只修复 GateW readonly soak 的模式隔离、kill-switch fail-close、acceptance clock create-once、脱敏 prerequisite
  readback、systemd runtime ownership 与相关回归。
- 不修改交易主链、`OrderCommandService`、`RiskGate`、ledger、migration、Controller/API、frontend、research 或 CI workflow。
- 不调用 OKX/任何交易所，不读取 credential material，不创建 Attempt-09/10，不启动真实 soak 或真实 168 小时 acceptance
  clock，不执行 freeze/archive/tag。

## Starting baseline and stale drop-in cleanup

- Branch/HEAD/origin：`dev / 0698f23df2fc395715b5599a7e22ab84f6cd3032`，`HEAD == origin/dev`。
- Exact-head CI：run `29749018941 / completed / success / 10 of 10`。
- Server current：`/opt/nexus-quant/releases/0698f23df2fc395715b5599a7e22ab84f6cd3032`。
- Inventory：17 个历史 run、34 个精确 GateW `offline.conf`；清理前 active units=`0`、MainPID=`0`、residual=`0`，文件均不是正式
  unit 或 current release artifact。
- 可恢复备份：`/var/backups/nexus-quant/gatew-offline-dropins/20260720T160412Z-17-runs-34-files.tar`；SHA-256=
  `9a07e46abcb50242bf27150c707bf9d30077872549f5630066c7e5785daed264`。
- 精确清理并 `systemctl daemon-reload` 后，所有 GateW instance `DropInPaths` 为空，active units/MainPID/residual 均为
  0。两次失败 candidate run 产生的各 2 个临时 drop-in 也按 runId 精确清理，没有泛化删除其他 systemd 路径。

## Implementation

### Kill-switch contract

- 显式区分 `REAL_READONLY_SOAK` 与 `OFFLINE_ISOLATED_ACCEPTANCE`；旧 `REAL` / `OFFLINE_ACCEPTANCE` launcher mode 被拒绝。
- REAL readonly bootstrap/sample 必须观察 `GLOBAL_TRADING=ENGAGED`，不调用 disengage、不修改 kill switch；任何
  `DISENGAGED/UNKNOWN` 均 fail-closed。
- OFFLINE fixture 只在隔离 schema/fixture 内使用 `DISENGAGED`，受控失败后独立 fail-close 必须恢复并读回 `ENGAGED`。

### Acceptance clock

- frozen config 升级为 `gatew-soak-frozen-config-v3`；prepare 固定写
  `acceptanceClockStarted=false / acceptanceStartAt=null / plannedAcceptanceAt=null`。
- 只有 config/balance 首个有效 PASS、fresh SSH、same MainPID、heartbeat advanced、valid hash chain、zero forbidden
  endpoint/secret exposure 全部满足后，才计算三者 UTC 最大值并精确 `+168h`。
- clock 使用 root-owned create-once/idempotent publish；相同请求返回 `NO_CHANGE`，不同值冲突必须失败；control directory=
  `root:nqgatew/0710`，clock=`root:nqgatew/0640`。
- PowerShell JSON timestamp helper 在支持 `DateKind` 时强制 `ConvertFrom-Json -DateKind String`，避免 Linux
  `Asia/Shanghai` 上 ISO timestamp 被隐式转换并前移 8 小时；旧 PowerShell 安全回退。

### Sanitized prerequisite readback

- `OkxPrivateReadonlyProbeService` 增加不解密、不调用 provider 的 prerequisite verifier，只允许输出：`killSwitchEngaged`、
  `credentialConfigured`、`activeCredentialCount`、`credentialType`、`credentialLocalStatus`、
  `tradePermissionExpectedDisabled`、`withdrawPermissionExpectedDisabled`、`postgresReachable`、`managementHealthy`。
- DB 连接复用正式 tooling 的既有本地连接路径，不假定 PostgreSQL role `postgres`，不从 shell 猜测密码；DB 不可达或 kill
  switch 非 `ENGAGED` 时 fail-closed。
- 不输出 JDBC password、secret-bearing connection value、encrypted/decrypted payload、API
  key、secret、passphrase、signature、raw SQL result 或 raw provider response。

## Files changed before Commit A

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakFailCloseTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakSupportTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadonlyProbeService.java`
-
`backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadonlyProbeServiceTest.java`
- `deploy/systemd/nq-gatew-soak@.service`
- `scripts/gatew/gatew-okx-readonly-soak-control.ps1`
- `scripts/gatew/gatew-okx-readonly-soak-failclose.ps1`
- `scripts/gatew/gatew-okx-readonly-soak.ps1`
- 本文件、Attempt-09 blocked evidence 与 `docs/current/evidence/gate-w/README.md`。

提示词原始 Commit A allowlist 未列出两个既有 test-support 测试、`nq-infra` helper 生产/测试文件；这些文件是承载 launcher
mode 合同与九字段脱敏 prerequisite readback 的最小必要扩展，未扩大到其他业务模块。

## Local validation

| Command / evidence                                            | Result | Scope / environment                                                                            |
|---------------------------------------------------------------|--------|------------------------------------------------------------------------------------------------|
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app -am test` | PASS   | reactor success；`nq-app` 218 tests、0 failures/errors、9 skipped                              |
| `mvn -f backend/pom.xml test`                                 | PASS   | 全 backend reactor success；`nq-app` 218 tests、0 failures/errors、9 skipped                   |
| `GateWOkxReadonlySoakFailCloseTest` targeted                  | PASS   | 13 tests、0 failures/errors、1 annotation skip                                                 |
| Windows PowerShell 5.1 / PowerShell 7 三份 helper self-test   | PASS   | control/worker/fail-close；timestamp string、hash chain、create-once、mode/fail-close contract |
| bundle builder/installer self-test（PowerShell 5.1 / 7）      | PASS   | immutable manifest、installer 与 tamper contract                                               |
| manifest tamper test                                          | PASS   | 修改 artifact 后 verifier exit=`2`，`RELEASE_ARTIFACT_HASH_MISMATCH`                           |
| `git diff --check`                                            | PASS   | 仅 Git 行尾转换提示，无 whitespace error                                                       |

Maven 环境固定：`CI=true`、`NQ_NO_OUTBOUND=true`、`NQ_AI_ENABLED=false`、`NQ_DH_RUNTIME_ENABLED=false`、
`NQ_REAL_EXCHANGE_ENABLED=false`。未运行 frontend/Python 验证，因为本任务没有修改对应范围。

## Candidate immutable release

- ReleaseId：`candidate-0698f23df2fc-279357954b3524c3-20260720T174844Z`。
- Candidate diff SHA-256：`279357954b3524c3f509cc1f6d85ccb259192b4243c81a5e6d0b6749008ba6e8`。
- Manifest SHA-256：`2d97a9f13f1cf5a28ac501eb49acf4208a626ff5fefff2307efe0276912d9af4`；artifacts=`129`。
- Bundle tar SHA-256：`fcc73a545a8efe29302bee50ead09a20fb0ae767f48d7b79f8d24a46a5dd940c`；size=`49,692,108` bytes。
- Server path：`/opt/nexus-quant/releases/candidate-0698f23df2fc-279357954b3524c3-20260720T174844Z`；installer=
  `PASS / ROOT_OWNED_RELEASE_INSTALLED`。
- Owner/mode：release root=`root:root/0755`，`nqgatewWritable=false`，无 `current` 切换。
- Server `pwsh 7.6.3`：control=`39`、worker=`59`、fail-close=`41` cases 全部 PASS；
  `credentialAccessed=false / networkCalled=false`。
- `systemd-analyze verify` exit=`0`；仅报告无关既存 `cloudmonitor.service` warning。安装后 active GateW units=`0`、offline
  drop-in=`0`。

## Candidate formal offline acceptance

- RunId：`gatew-soak-20260720T175626Z-5112631c`；mode=`OFFLINE_ISOLATED_ACCEPTANCE`；source tree mode=`CANDIDATE`。
- Prepare：`PASS / FORMAL_SOAK_PREPARED`；frozen config clock 为 `false/null/null`，clock file absent；control directory=
  `root:nqgatew/0710`。
- Cycle 1/2：`PASS / PASS`；首个 config/balance 有效 PASS 均为 `2026-07-20T17:58:44.9721418Z`；MainPID=`3991261`
  ；credential/network 均为 `false`。
- Fresh SSH：同 MainPID=`3991261`，heartbeat 从 `2026-07-20T18:01:41.6076831Z` 推进到 `2026-07-20T18:02:01.6636556Z`；fresh
  verification=`2026-07-20T18:02:02.7280759Z`。
- Clock：首次返回 `PASS / ACCEPTANCE_CLOCK_STARTED`；`acceptanceStartAt=2026-07-20T18:02:02.7280759Z`，精确等于三个
  prerequisite 的最大值；`plannedAcceptanceAt=2026-07-27T18:02:02.7280759Z`，精确 `+168h`。第二次调用返回
  `NO_CHANGE / ACCEPTANCE_CLOCK_ALREADY_STARTED`；clock=`root:nqgatew/0640`。
- Cycle 3：`CONTROLLED_FAILURE`；control=`PASS / CONTROLLED_OFFLINE_FAILURE_CLOSED`。
- OnFailure/independent fail-close：terminal=`FAILURE_STOPPED`，reason=`SYSTEMD_WORKER_FAILURE_CONFIRMED`；recovery=
  `ENGAGE_SUCCEEDED`；kill switch observed=`ENGAGED`。
- Durable evidence：sample count=`3`；hash chain=`PASS / HASH_CHAIN_VERIFIED`；final chain hash=
  `ac0dad9a71256d65d10c918cb3342cda35c4646f0c6d7bb8849b30584ac6d431`；historical evidence immutable=`true`。
- Terminal cleanup：worker/fail-close 均 `inactive/dead`、MainPID=`0`、residual=`0`、runtime absent、run/all offline drop-in=
  `0`、active GateW units=`0`。
- Boundary facts：`credentialAccessed=false`、`networkCalled=false`、`OKXCalled=false`；没有创建 Attempt-09/10，也没有启动真实
  acceptance clock。

## Findings

- P0：无。
- P1：无。candidate 已关闭 stale drop-in、REAL kill-switch、acceptance clock UTC/write-once 与独立 fail-close 结构性阻塞。
- P2：release 内置 helper self-test 按源码树布局需要可写 `target/`；immutable release 原地执行被权限正确拒绝。服务器验证使用
  SHA-256 相同的受控 `/tmp` harness，并把 `NQ_GATEW_RELEASE_ROOT` 固定到 candidate，结束后精确清理；不影响 formal runtime 或
  artifact immutability。
- P3：`systemd-analyze verify` 同时输出无关既存 `cloudmonitor.service` 的 `KillMode=none`/legacy PIDFile warning；GateW 两个
  unit verify exit 仍为 0。

## Boundary confirmation

- 未调用 OKX/交易所，未读取、解密、复制或输出 credential material，未重跑真实 permission probe。
- 未启用 LIVE、真实下单/撤单/转账/提现、AI、DH runtime、real provider/client 或 public listener。
- 未创建 Attempt-09/10，未启动真实 soak；candidate clock 仅属于隔离 offline acceptance run，已 terminal failure-stop，不是正式
  168 小时 acceptance clock。
- 未执行 migration、frontend/research/CI workflow 修改、freeze/archive/tag。

## Decision before Commit A

`PASS / STALE_DROPINS_CLEANED / KILL_SWITCH_ENGAGED_CONTRACT_FIXED / ACCEPTANCE_CLOCK_WRITE_ONCE_FIXED / SANITIZED_PREREQUISITE_READBACK_PROVEN / CANDIDATE_IMMUTABLE_RELEASE_VERIFIED / CANDIDATE_FULL_FORMAL_OFFLINE_ACCEPTANCE_PROVEN / READY_TO_COMMIT`
（通过 / stale drop-in 已清理 / kill switch ENGAGED 合同已修复 / acceptance clock write-once 已修复 / 脱敏 prerequisite
readback 已证明 / candidate immutable release 已验证 / candidate 完整正式离线验收已证明 / 可提交）。

Implementation commit=`PENDING`；implementation exact-head CI=`NOT_RUN`；final release/current switch/final offline
acceptance=`PENDING`。只有 Commit A push 且 exact-head `NQ CI Baseline` 10/10 GREEN 后，才允许构建和部署 final release。
