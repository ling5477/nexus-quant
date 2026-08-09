# NQ-GATEW-IMMUTABLE-RELEASE-BUNDLE-AND-ROOT-OWNED-DEPLOYMENT — Attempt 01

## Task classification

- 主类型：`DEPLOYMENT`；辅助类型：`SECURITY_AUDIT / CODE_CHANGE / SYSTEMD_RUNTIME_HARDENING`。
- 归属：NQ-only、GateW `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- 本记录只覆盖 implementation candidate 的 root-owned 部署与正式 systemd 离线验收；implementation commit、exact-head CI、final release 与 final server acceptance 在本记录形成时仍为后续 hard gate。

## Scope

- 不可变 release bundle、manifest、SHA-256、owner/mode、symlink containment 与 runtime user 只读合同。
- `nq-gatew-soak@.service`、`nq-gatew-soak-failclose@.service` 正式 unit 的 OFFLINE_ACCEPTANCE（离线验收）全链。
- 双成功周期、fresh SSH、同 MainPID、cycle 3 受控失败、`OnFailure` 独立 fail-close、kill switch 自动接合与 terminal authority。
- 明确不涉及真实 OKX、真实 exchange credential、Attempt-09、168 小时 acceptance clock、LIVE、订单/撤单/转账/提现、AI、DH runtime、migration、frontend、research 或正式 `current` 切换。

## Candidate release

- Base/source commit：`ff92c3bf3f1fbbbf9af087f9e27e3a030652c1a8`；本地 `HEAD == origin/dev`，staged area 为空。
- Candidate ID：`candidate-ff92c3bf3f1f-d9a31e99dde93dcd-20260720T104748Z`。
- Candidate diff SHA-256：`d9a31e99dde93dcd65c8649705086315cec715a6098ed266e5fdb9af0baa60df`。
- Manifest SHA-256：`8ff73d611ce8dae17f10bd4a99f0172fdfe366b84f8b4aded4eca5dc2f695eab`；artifacts=`129`。
- 本地 bundle verifier：`PASS / IMMUTABLE_RELEASE_VERIFIED`；与前一份相同 candidate diff 的 bundle 相比，仅两个绑定固定 releaseId 的 systemd unit hash 发生预期变化，其余 127 个 artifacts 一致。
- Server path：`/opt/nexus-quant/releases/candidate-ff92c3bf3f1f-d9a31e99dde93dcd-20260720T104748Z`。
- Installer：`PASS / ROOT_OWNED_RELEASE_INSTALLED`；post-install/post-run 均为 `PASS / ROOT_OWNED_RELEASE_VERIFIED`。
- POSIX/containment：root directory 与全部子目录 `root:root / 0755`；manifest `root:root / 0644`；executables按 manifest为 `0755`；release symlink count=`0`；bad directory owner/mode count=`0`；`nqgatew` write test exit=`1`。
- Formal units：两个 template 均固定指向 candidate release，`systemd-analyze verify` exit=`0`，enablement=`linked`；`/opt/nexus-quant/current` 始终不存在，未发生正式切换。

## Formal offline acceptance

- RunId：`gatew-soak-20260720T134751Z-4d122864`；run mode=`OFFLINE_ACCEPTANCE`；starting CI metadata=`29673707164`；acceptance clock=`false`。
- Prepare：`PASS / FORMAL_SOAK_PREPARED`；historical evidence snapshot count=`250`。
- Cycle 1/2：`PASS / PASS`；formal unit `active/running`；MainPID=`3964307`；heartbeat sequence=`2`。
- Fresh SSH：新连接确认同 MainPID=`3964307`；heartbeat observedAt 从 `2026-07-20T21:49:44.6984029+08:00` 推进到 `2026-07-20T21:50:16.886634+08:00`。
- Cycle 3：`CONTROLLED_FAILURE`；control 返回 `PASS / CONTROLLED_OFFLINE_FAILURE_CLOSED`。
- Fail-close：journal 记录 `PASS / INDEPENDENT_FAILCLOSE_FINALIZED`；terminal=`FAILURE_STOPPED`，reason=`SYSTEMD_WORKER_FAILURE_CONFIRMED`。
- Kill switch：`ENGAGE_SUCCEEDED`；readback=`ENGAGED`。
- Durable evidence：sample count=`3`；hash chain=`PASS / HASH_CHAIN_VERIFIED`；final chain hash=`caff9bcde31b6bae4b99c8cd7445401dd223dc95bca9c98c37c1f3553492e59b`；raw response/secret exposure=`0/0`。
- Boundary facts：`credentialAccessed=false`、`networkCalled=false`、`acceptanceClockStarted=false`、historical evidence immutable=`true`。
- Terminal cleanup：worker/fail-close均 `inactive/dead`；MainPID=`0`；residual=`0`；runtime directory absent；offline drop-ins absent；active GateW units=`0`。
- Host health：public non-SSH listeners=`0`；management loopback health HTTP=`200`；PostgreSQL loopback=`accepting connections`。

## Validation

| Command / evidence | Result | Scope / environment |
| --- | --- | --- |
| bundle内置 `verify-gatew-release.ps1`（本地/staging/installed/post-run） | PASS | manifest schema、size/SHA-256、LF/CR、secret-field、owner/mode、symlink与containment |
| root installer `install -InstallUnits` | PASS | 原子 root-owned release、runtime user不可写、固定 candidate unit binding |
| `systemd-analyze verify` | PASS | exit 0；仅有无关 `cloudmonitor.service` 既存 warning |
| control `prepare/start/status/offline-fail/verify` | PASS | cycle1/2、fresh SSH same PID、cycle3、fail-close与安全终态 |
| durable worker `evidence-verify` | PASS | 3 samples、hash chain、network/credential false、raw/secret 0 |
| listener/health/state audit | PASS | public non-SSH 0、management/PostgreSQL loopback、active units 0、MainPID/residual 0 |
| Maven full test | 本次未重跑 | 用户明确要求不重跑已经通过的 Maven 全量测试；沿用本任务断点前已通过结果，不将本轮未执行写成新 PASS |

## Findings

- P0：无。
- P1：无。
- P2：`verify -CleanupOfflineDropIn` 在完成全量 PASS 后会删除本 run 的临时 network-policy drop-in；清理后再次对历史 run 执行完整 control verify 会因当前 `systemctl show` 不再包含该 override 而返回 `FAIL / OFFLINE_NETWORK_POLICY_INVALID`。运行期间的 start/status/首次 final verify均已在 drop-in 存在时通过，durable evidence与terminal仍可独立复核；后续可考虑保存不含敏感值的运行时 unit policy snapshot。
- P3：systemd journal对同一 fail-close target同时作为 `OnFailure`/`OnSuccess` 报告 exit-status propagation candidate warning；独立 fail-close仍由 root exit fact完成并返回 PASS，未影响本次安全闭环。

## Boundary confirmation

- 未调用 OKX，未访问或输出 exchange credential，未重跑 permission probe。
- 未创建 Attempt-09/10，未启动真实 soak或168小时 acceptance clock。
- 未切换 `/opt/nexus-quant/current`，未启用 LIVE/Shadow/AI/DH/real provider/private trading，未触达交易写侧。
- 本阶段只新增 candidate root-owned release、固定 candidate formal unit links、独立 offline run state/evidence；结束时所有 GateW units stopped，kill switch=`ENGAGED`。

## Decision

`PASS / ZERO_LENGTH_CREATE_ONCE_FIXED / IMMUTABLE_RELEASE_BUNDLE_PROVEN / ROOT_OWNED_CANDIDATE_DEPLOYMENT_PROVEN / FORMAL_GIT_DEPENDENCY_REMOVED / CANDIDATE_FULL_FORMAL_OFFLINE_ACCEPTANCE_PROVEN / READY_TO_COMMIT`（通过 / 零长度 create-once 已修复 / 不可变 bundle 已证明 / root-owned candidate 部署已证明 / 正式运行时 Git 依赖已移除 / candidate 正式离线全链已证明 / 可提交）。

下一动作：精确复核并暂存 task allowlist，形成唯一 implementation commit，push `dev`，等待 exact-head `NQ CI Baseline` 10/10 GREEN；只有 CI GREEN 后才允许从 clean exact-head 构建、部署并切换 final release。
