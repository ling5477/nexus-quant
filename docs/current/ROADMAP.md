# Roadmap

本文件只定义下一允许动作和路线。当前 Gate、release tag 与安全状态必须读取 [STATUS.md](STATUS.md) 的 `nq-current-authority` 机器可读区块。

## 当前路线

```text
GateU FROZEN / ACCEPTED / TAGGED
  ↓
GateV FROZEN / ACCEPTED / TAGGED
  ↓
GateW IN PROGRESS / NOT FROZEN
  ↓
GateW-1 ACCEPTED / CI GREEN
  ↓
GateW-2 ACCEPTED / CI GREEN
  ↓
GateW-3 ACCEPTED / CI GREEN
  ↓
GateW-4 ACCEPTED / CI GREEN
  ↓
GateW soak start contract remediation PASS / COMMIT A CI GREEN / SERVER DEPLOYED
  ↓
GateW pre-create sanitized prerequisite remediation PASS / COMMIT A CI GREEN / SERVER DEPLOYED
  ↓
GateW-OKX-READONLY-SOAK-ATTEMPT-09 FAILED / ACCEPTANCE REJECTED / INCIDENT REVIEW COMPLETED
  ↓
GateW Attempt-09 failure remediation SECURITY REVIEW ACCEPTED / CI GREEN / DEPLOYMENT PENDING
  ↓
GateW immutable release DEPLOYMENT VERIFICATION FAILED / REMEDIATION REQUIRED
  ↓
GateW reproducible release fix IMPLEMENTED / CI GREEN / DEPLOYMENT RETRY PENDING
  ↓
GateW remediation release DEPLOYMENT VERIFIED / CI GREEN / ATTEMPT-10 PREPARATION PENDING
  ↓
NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START attempt-01 BLOCKED / PRECREATE FAILED / ATTEMPT-10 NOT CREATED
  ↓
GateW Attempt-10 pre-create prerequisite remediation IMPLEMENTED / CI GREEN / DEPLOYMENT PENDING
  ↓
GateW Attempt-10 pre-create remediation DEPLOYMENT VERIFICATION FAILED / CODE REMEDIATION REQUIRED
  ↓
GateW Attempt-10 internal readback RCA/fix IMPLEMENTED / CI GREEN
  ↓
GateW Attempt-10 release candidate stabilization IMPLEMENTED / CI GREEN / DISPOSABLE LINUX VALIDATION PASSED
  ↓
GateW Attempt-10 release candidate review REJECTED / REMEDIATION REQUIRED
  ↓
NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
  ↓
GateW-FREEZE NOT STARTED / FUTURE
```

## 下一允许动作

- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag 为 `nq-gatev-freeze`，历史证据入口为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateV-FREEZE：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；candidate/acceptance head 为 `7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`，CI run `29191014596` 为 `completed / success`。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateW-PLAN：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head `5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，CI run `29199785253`。
- GateW-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，CI run `29219687588`。
- GateW-2：`ACCEPTED / CI GREEN`；implementation/acceptance head `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553`，CI run `29230512781`。`REAL_SMOKE=NOT_RUN`，不表示远端 permission、LIVE 或交易授权。
- GateW-3 venue-rule facts：implementation commit 为 `8b54adc6952775dc1a939aad7b0ae849f20f42cf`，migration conformance review 已通过；CI blocker fix commit `fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28` 的 exact-head CI run `29260881801` 已 `completed / success`。LIMIT-only internal preview implementation commit `eff79d7c7ea1b034de4e77c7ec64974c247027f5` 的 exact-head run `29308652349` 为 `completed / failure`；acceptance head `abc5230c21ad37b3d01bc7df2cc825579bd3f7dc` 的 exact-head run `29319269424` 为 `completed / success`。失败 run 保留为历史事实，venue-rule facts 与 preview 均已纳入 GateW-3 accepted baseline。
- GateW-3 read-only reconciliation：implementation/acceptance head `71e1ded5a9896996717549d2a96068356dea7288`，exact-head CI run `29324600871 / completed / success`，10/10 jobs success；该 slice 已纳入 GateW-3 accepted baseline。
- GateW-3 risk preflight：implementation/acceptance head `178b4951ba1406748170022c9940f84beaa8ab81`，exact-head run `29332316101 / completed / success / 10 jobs / bad=0`；GateW-3 已 `ACCEPTED|CI_GREEN`。
- GateW-4：`ACCEPTED / CI GREEN`；implementation/acceptance head `07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c`，exact-head CI run `29339016784 / completed / success / 10 jobs / bad=0`。Blocker-1、operations、persistence/retention、human-review binding、restore、incident 与 local no-egress soak hard gates 均通过。
- GateW soak start contract remediation：implementation commit `0e8e2c128c456542b3f7695c9620e4d170c3f4f6`，exact-head CI run `29766800343 / completed / success / 10 of 10`；该 release 保留为已验证回滚点，已由后续 pre-create remediation release 替代。
- GateW pre-create sanitized prerequisite remediation：implementation commit `1b501488076fae79e15b84579a02f5c580fa51b3`，exact-head CI run `29837563573 / completed / success / 10 of 10`；final `EXACT_COMMIT` immutable release 已部署，独立 pre-create、self-contained DB input、sanitized metadata readback 与完整 final offline acceptance 均通过，服务器 current 固定 Commit A。
- GateW-OKX-READONLY-SOAK-ATTEMPT-09：`FAILED / ACCEPTANCE REJECTED / INCIDENT REVIEW COMPLETED`；run `gatew-soak-20260722T111144Z-ac00f878` 的有效时长为 `471795.0520427s`，距离 `604800s` 短缺 `133004.9479573s`。Worker 分类=`OPERATOR_OR_AUTOMATION_STOP`、精确发起者=`UNKNOWN`；finalizer 分类=`FINALIZER_SYSTEMD_TIMEOUT`，terminal result 缺失；`FORMAL_SOAK_VERIFIED` 仅覆盖 evidence integrity，不能推导 acceptance。
- GateW Attempt-09 failure remediation：`SECURITY REVIEW ACCEPTED / CI GREEN / DEPLOYMENT PENDING`；implementation commit `92adff7e55c2200692e892db2189132c243a1ac5` 的 exact-head CI run `30474856153` 与 security review/P1 fix commit `61f0b94fadbc87b883a7365eaacc4e8f63829a88` 的 exact-head CI run `30515021689` 均为 `completed / success / 10 of 10`。两个 P1 已关闭；clean Commit A canonical `EXACT_COMMIT` bundle 为 130 artifacts，manifest `f2ec7b00238cb2b718a82d298edc549d41833975ff42f2c8e5412e4db8b704fd`，closed set/hash/LF/mode/篡改拒绝通过。Windows `posixVerified=false`；未部署服务器。
- GateW immutable release deployment verification：`DEPLOYMENT VERIFICATION FAILED / REMEDIATION REQUIRED`（部署验证失败 / 需要整改）。同一 exact commit 的 manifest 因动态 `createdAt` 在不同时间重建为不同 hash；旧 `f2ec7b...` 及两个 rebuild 值只能记为 `HISTORICAL_NON_REPRODUCIBLE_BUILD_OUTPUT / NOT_DEPLOYABLE_BASELINE`，不得继续作为部署基线。服务器变更为 0。
- GateW reproducible release fix：`IMPLEMENTED / CI GREEN / DEPLOYMENT RETRY PENDING`。Commit A `c16f27c3c68d2484ad140d0557b879de08b7c78f` 的 exact-head CI run `30537845010` 为 `completed / success / 10 of 10`；两份 detached worktree、PowerShell 5.1/7、间隔 `35.582s` 的 exact build 得到相同 manifest `eaf83f95f51fc938d55c4c0235eee86e9de78c67990e142cf3d0b6c62c9e8977`、bundle `60a11dde87a4cbfcff8adbd32966b3dd28463d3399b8ba25db01eb836ed0ec1b` 与 131-artifact closed set。该值是下一轮唯一部署基线。
- GateW remediation immutable release deployment：`DEPLOYMENT VERIFIED / CI GREEN / ATTEMPT-10 PREPARATION PENDING`。`c16f27c3...` release 已完成独立 Linux root install、POSIX/ownership、worker write denial、trusted path、systemd、offline remediation/security、Attempt-09 rejected fixture、positive 168h fixture与tamper rejection验证，并已由 canonical installer 原子激活；units started=`0`。
- GateW Attempt-10 preparation attempt-01：`BLOCKED / CREDENTIAL_OR_PERMISSION_PRECHECK_FAILED / ATTEMPT_10_NOT_CREATED`。Canonical pre-create 返回 `readyForAttemptCreation=false`；RunId/state/runtime/clock均未创建，OKX未调用。
- GateW Attempt-10 pre-create prerequisite remediation：`DEPLOYMENT VERIFICATION FAILED / CODE REMEDIATION REQUIRED`。Commit A `1561eb60cd46dc1a4618fde6651426c41d7c4e20` 的 release supply chain、Linux install、systemd 与离线回归均通过，但生产 canonical readback 仍返回 `INTERNAL_SANITIZED_READBACK_FAILURE`；current/unit links 已 canonical 回滚到 `c16f27c3...`，Attempt-10 未创建。
- GateW Attempt-10 internal readback RCA/fix 与 release candidate stabilization：固定 RC `5e7a9c4e...` 的实现与 CI 历史证据保留，但独立 review 结论为 `REVIEW REJECTED / REMEDIATION REQUIRED`（审查已拒绝 / 需要整改）。阻断项是 Java 21/17 runtime contract 冲突、launcher 无进程 timeout、声明 release hashes 无法由 fixed RC 重建，以及 null/type mapping 未进入明确 failure taxonomy；JAR duplicate-entry policy 另记 P2。该 RC 不得进入生产部署。
- GateW-FREEZE：`NOT_STARTED / FUTURE`；GateW 尚未 freeze、archive 或 tag。Attempt-09 已不可恢复且已拒绝；Attempt-10=`NOT_CREATED / NOT_AUTHORIZED`。
- 当前唯一治理动作是 `NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX`；只允许关闭本轮 review 的 4 个 P1 与 1 个 P2并重新建立可审查 release，不得顺带扩大功能或边界。不得部署或直接重试、恢复 Attempt-09、复用旧 clock、修改生产 credential/permission/IP allowlist、手工 SQL、扩大 OKX endpoint、触碰交易写侧或进入 freeze/archive/tag。

## 路线边界

- GateV tag 是历史 release 事实；不得重打、移动、覆盖或 force update `nq-gatev-freeze`。
- GateW-2 只接受两个冻结的 OKX private read-only typed operation；禁止 raw path、mutating/funds movement、自动 credential 访问、startup/background probe、migration 和把 mock/CI 写成真实 smoke。LIVE、交易授权与订单写侧继续关闭。
- GateW-3 venue-rule facts 仍只覆盖 public metadata 的显式、最多 3 个 OKX Spot symbol 同步和 `instrument_catalog` migration；preview 仅增加 bounded local read 与 pure diagnostic，不扩大同步范围。GateW-3 已接受，但不构成交易授权。
- GateW-3 preview 只允许本地 deterministic diagnostics；其 acceptance-head exact-head CI 已成功。禁止 `TradingAdapter`、order command/write/lifecycle、credential/private transport、实时 network、任何 preview persistence，以及通过 `dryRun=true` 复用真实下单链。
- GateW-3 reconciliation 只允许 OKX Spot、最多 3 symbols、每类每 symbol 1 page/100 records、24h window 的显式 typed `Read` snapshot；无 controller/scheduler/repair/persistence，默认不装配。即使全量 matched，也仅表示 `SNAPSHOT_MATCHED_AT_EVALUATION_TIME`，`executionReadiness=BLOCKED`。
- GateW-3 risk preflight 仅组合 immutable results/snapshots；不得调用完整 risk chain、stateful rule、order command、network、credential 或任何 write。UNKNOWN/NOT_EVALUATED 必须保留，execution readiness 永久 BLOCKED。
- GateW-4 accepted 不等于 GateW frozen；GateW-FREEZE `NOT_STARTED` 不等于 freeze implementation 已开始。Freeze readiness review、archive manifest、authority、links 与 known residual 裁决必须先行。
- 修复版 release `c16f27c3...` 仍是服务器 current 与 unit links 的 last-known-good immutable release；失败 release `1561eb60...` 已安装并保留但未运行。固定 RC `5e7a9c4e...` 已被独立 review 拒绝，未上传或部署。Attempt-10 只有在独立修复、重新审查、独立生产部署授权和全部 hard gates 后才可能重新申请启动，不得手工切换或直接重试。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 的状态由 `STATUS.md` 统一定义。
