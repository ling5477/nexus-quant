# NQ-GATEW-SOAK-START-CONTRACT-REMEDIATION-AND-IMMUTABLE-RELEASE-REBUILD — Attempt 01

## Task classification

- 主类型：`GATEW_TOOLING_REMEDIATION`；辅助类型：
  `SECURITY_CONTRACT_FIX / ACCEPTANCE_CLOCK_FIX / SERVER_RUNTIME_CLEANUP / IMMUTABLE_RELEASE_REBUILD / FULL_OFFLINE_ACCEPTANCE`。
- 归属：NQ-only、L 级高风险任务；GateW 保持 `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- 本记录覆盖实现、本地验证、server candidate/final immutable release、Commit A exact-head CI、`current` 原子切换与两轮完整正式离线验收。
- Commit B 仅同步本记录、evidence index、current authority 与 canonical Attempt-09 START 治理分类；服务器 runtime 继续固定 Commit A。

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

## Commit A and exact-head CI

- Implementation commit：`0e8e2c128c456542b3f7695c9620e4d170c3f4f6`；message=`fix(gatew): harden readonly soak start contract`。
- Commit A 已 push 到 `origin/dev`；`NQ CI Baseline` run `29766800343` 的 `headSha` 精确等于 Commit A，状态为
  `completed / success`，10/10 jobs 全部成功。
- Commit A 只包含 9 个实现/测试文件与 3 个 Commit A evidence/index 文件；没有 migration、frontend、research 或 CI workflow
  变更。

## Final immutable release and server activation

- ReleaseId/source commit/source tree mode：
  `0e8e2c128c456542b3f7695c9620e4d170c3f4f6 / 0e8e2c128c456542b3f7695c9620e4d170c3f4f6 / EXACT_COMMIT`。
- Manifest SHA-256：`5c0af5e006cf7db6ff507dd24d69fc04c6d8c1229adafbba2ab95319f760f5f1`；artifacts=`129`。
- Bundle tar：`49,690,451` bytes；SHA-256=
  `f781447d0a1a12b101f39919802eaf87fcc0b3fe06489d55ec60005f30ec4815`；136 个 tar paths，unsafe path=`0`。
- Local verifier：PowerShell 7 与 5.1 均 `PASS / IMMUTABLE_RELEASE_VERIFIED`；独立 tamper copy 修改 artifact 后 verifier
  exit=`2 / RELEASE_ARTIFACT_HASH_MISMATCH`，tamper copy 随后精确清理。
- 第一次 Windows PowerShell 5.1 exact bundle build 因 culture-aware artifact sort 与 verifier ordinal sort 不一致，返回
  `RELEASE_MANIFEST_ARTIFACT_ORDER_INVALID`；该失败产物未上传。使用目标 runtime PowerShell 7 从同一 clean detached Commit A
  重建后通过，未修改 Commit A。
- Server install：`/opt/nexus-quant/releases/0e8e2c128c456542b3f7695c9620e4d170c3f4f6`；release root=
  `root:root/0755`，manifest=`root:root/0644`，`nqgatewWritable=false`，server verifier=PASS。
- Server `pwsh 7.6.3`：control=`39`、worker=`59`、fail-close=`41` cases 与 installer self-test 全部 PASS；
  `credentialAccessed=false / networkCalled=false`。
- `systemd-analyze verify` exit=`0`；仅报告无关既存 `cloudmonitor.service` warning。Activation 前后 active units/MainPID/
  residual/drop-ins 均为 0；`/opt/nexus-quant/current` 与两个 formal unit template 原子绑定 Commit A release。

## Final formal offline acceptance

- RunId：`gatew-soak-20260720T183517Z-11881656`；mode=`OFFLINE_ISOLATED_ACCEPTANCE`；source tree mode=
  `EXACT_COMMIT`；不是 Attempt-09。
- Prepare：`PASS / FORMAL_SOAK_PREPARED`；clock=`false/null/null`、clock file absent、control dir=`root:nqgatew/0710`。
- Cycle 1/2：`PASS / PASS`；首个 config/balance 有效 PASS 均为 `2026-07-20T18:37:16.7062731Z`；唯一 MainPID=
  `3998508`；credential/network 均为 false。
- 首次 start SSH 在 sequence 1 后被远端关闭；systemd worker 独立继续运行。新 SSH 只读确认后，重复 start 被合同拒绝为
  `BLOCKED / RUN_NOT_STARTABLE`，没有第二个 worker；原 control 已把 lifecycle 推进到 `RUNNING`、sequence 2，同一 MainPID。
- Fresh SSH：heartbeat baseline `sequence=2 / 2026-07-20T18:38:27.6791422Z` 后继续推进；fresh verification=
  `2026-07-20T18:38:48.6987335Z`；MainPID 始终为 `3998508`。
- Offline clock simulation：首次 `PASS / ACCEPTANCE_CLOCK_STARTED`，start 精确等于 config/balance/fresh SSH 的最大值
  `2026-07-20T18:38:48.6987335Z`；planned=`2026-07-27T18:38:48.6987335Z`，精确 `+168h`；第二次调用=
  `NO_CHANGE / ACCEPTANCE_CLOCK_ALREADY_STARTED`；clock=`root:nqgatew/0640`。same PID/heartbeat/hash=true，forbidden/secret=`0/0`。
- Cycle 3：`CONTROLLED_FAILURE`；OnFailure terminal=`FAILURE_STOPPED`，reason=`SYSTEMD_WORKER_FAILURE_CONFIRMED`；recovery=
  `ENGAGE_SUCCEEDED`；kill switch=`ENGAGED`。独立 fail-close 重入返回 `NO_CHANGE / TERMINAL_ALREADY_EXISTS` 并重新验证 terminal。
- Durable evidence：sample count=`3`；hash chain=`PASS / HASH_CHAIN_VERIFIED`；manifest SHA-256=
  `16b0242aea3a0d196a561bcd7268153b2c51fa43b75017038410530edb7610a8`；final chain hash=
  `1807a63bc8c3f9abaf3e55993ddf0a337dca72caa7be8910e11c0552ce0314ab`；historical immutable=true。
- Terminal cleanup：worker/fail-close=`inactive/dead`，MainPID/residual=`0/0`，runtime absent，run/all GateW offline drop-in=`0/0`，
  active GateW units=`0`；`current` 仍固定 Commit A。
- Boundary facts：`credentialAccessed=false`、`networkCalled=false`、`OKXCalled=false`。Final run 的 clock 只属于已终止的隔离
  offline acceptance；Attempt-09=`NOT_CREATED / NOT_STARTED`，真实 acceptance clock=`NOT_STARTED`。

## Canonical next-action governance

- Commit B 对 `IMPLEMENTATION` 分类只增加精确 case-sensitive canonical action：
  `NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START`；不增加模糊 pattern、Attempt-10 或兼容分支。
- `work_batch=GateW-OKX-READONLY-SOAK-ATTEMPT-09`、`work_batch_status=NOT_STARTED`、commit=`NONE`、CI=`NOT_RUN`；唯一
  `next_action=NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START`。
- PowerShell 7/5.1 governance regression 均 PASS：canonical 正向分类/同 batch 通过；错拼、缺少 `START`、Attempt-10、大小写近似
  均为 `UNKNOWN` 且同 batch relation=false；既有 canonical actions 与全部 status mapping 继续通过。
- Governance lifecycle full regression、current authority checker 与 `git diff --check` 均 PASS；current links=
  `129 checked / 1 existing GateJ historical warning / 0 errors / PASS`。

## Findings

- P0：无。
- P1：无。candidate 已关闭 stale drop-in、REAL kill-switch、acceptance clock UTC/write-once 与独立 fail-close 结构性阻塞。
- P2：release 内置 helper self-test 按源码树布局需要可写 `target/`；candidate 使用 SHA-256 相同的受控 `/tmp` harness，结束后精确
  清理。Windows PowerShell 5.1 exact builder 还存在 culture/ordinal sort 差异；final 使用目标 runtime PowerShell 7 构建，并由
  PowerShell 7/5.1 双 verifier 复核。两项均不影响 Linux PowerShell 7 formal runtime，但属于后续 tooling hardening 清单。
- P3：`systemd-analyze verify` 同时输出无关既存 `cloudmonitor.service` 的 `KillMode=none`/legacy PIDFile warning；GateW 两个
  unit verify exit 仍为 0。

## Boundary confirmation

- 未调用 OKX/交易所，未读取、解密、复制或输出 credential material，未重跑真实 permission probe。
- 未启用 LIVE、真实下单/撤单/转账/提现、AI、DH runtime、real provider/client 或 public listener。
- 未创建 Attempt-09/10，未启动真实 soak；candidate clock 仅属于隔离 offline acceptance run，已 terminal failure-stop，不是正式
  168 小时 acceptance clock。
- 未执行 migration、frontend/research/CI workflow 修改、freeze/archive/tag。

## Final decision

`PASS / STALE_DROPINS_CLEANED / KILL_SWITCH_ENGAGED_CONTRACT_FIXED / ACCEPTANCE_CLOCK_WRITE_ONCE_FIXED / SANITIZED_PREREQUISITE_READBACK_PROVEN / IMMUTABLE_RELEASE_REBUILT / FULL_FORMAL_OFFLINE_ACCEPTANCE_PROVEN / COMMITTED / CI_GREEN / SERVER_DEPLOYED / ATTEMPT_09_NOT_CREATED / ACCEPTANCE_CLOCK_NOT_STARTED / READY_TO_RETRY_ATTEMPT_09`
（通过 / stale drop-in 已清理 / kill switch ENGAGED 合同已修复 / acceptance clock write-once 已修复 / 脱敏 prerequisite
readback 已证明 / immutable release 已重建 / 完整正式离线验收已证明 / 已提交 / CI 已通过 / 服务器已部署 / Attempt-09 未创建 /
真实 acceptance clock 未启动 / 可重新尝试 Attempt-09）。

Implementation commit=`0e8e2c128c456542b3f7695c9620e4d170c3f4f6`；implementation exact-head CI=
`29766800343 / completed / success / 10 of 10`；server runtime/current 固定 Commit A。唯一下一动作：
`NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START`。
