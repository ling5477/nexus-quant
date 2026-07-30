# NQ-GATEW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-VERIFICATION — Attempt 02

## 1. 结论

本轮结论为：

```text
PASS /
GATEW_REMEDIATION_RELEASE_DEPLOYMENT_VERIFIED /
LINUX_ROOT_INSTALL_VERIFIED /
POSIX_AND_OWNERSHIP_VERIFIED /
SYSTEMD_CONTRACT_VERIFIED /
OFFLINE_SECURITY_REGRESSION_PASSED /
ATTEMPT_10_NOT_CREATED
```

GateW 继续保持 `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。Attempt-09 继续保持 `REJECTED`（已拒绝）；Attempt-10 继续保持 `NOT_CREATED / NOT_AUTHORIZED`（未创建 / 未授权）。本轮没有切换 `/opt/nexus-quant/current`，没有启动 worker、fail-close 或 acceptance finalizer unit，没有创建 acceptance clock，也没有触达 OKX、credential、数据库、LIVE、交易写侧、freeze、archive 或 tag。

## 2. 范围与固定基线

- 任务类型：`LINUX_DEPLOYMENT_VERIFICATION / IMMUTABLE_RELEASE_INSTALLATION / RELEASE_SUPPLY_CHAIN_AUDIT / OFFLINE_SECURITY_REGRESSION`。
- 起始分支与 HEAD：`dev / 9ec1bd0b39073946208ed3e8d80dabc66651377f`。
- 起始 exact-head CI：`30538600256 / completed / success / 10 of 10`。
- release source commit：`c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- canonical manifest SHA-256：`eaf83f95f51fc938d55c4c0235eee86e9de78c67990e142cf3d0b6c62c9e8977`。
- canonical bundle SHA-256：`60a11dde87a4cbfcff8adbd32966b3dd28463d3399b8ba25db01eb836ed0ec1b`。
- declared artifacts / USTAR entries：`131 / 132`。
- 旧 `61f0b94f... / f2ec7b...` 基线继续保持 `HISTORICAL_NON_REPRODUCIBLE_BUILD_OUTPUT / NOT_DEPLOYABLE_BASELINE`，未使用。

起始工作区中的 4 个 GateW release tooling 本地改动没有丢弃，也没有进入 `dev`：它们已隔离到 `wip/gatew-release-tooling-local-20260730`，commit `9ece62a4` 已 push，未创建 PR、未合并。

## 3. Canonical bundle 与传输

从 detached exact-commit worktree 构建得到：

| 检查 | 结果 |
| --- | --- |
| `sourceTreeMode / sourceCommit` | `EXACT_COMMIT / c16f27c3c68d2484ad140d0557b879de08b7c78f` |
| manifest / bundle | 精确等于固定 canonical SHA-256 |
| bundle size | `54,210,048` bytes |
| artifact / USTAR | `131 / 132` |
| missing / extra / undeclared | `0 / 0 / 0` |
| absolute path / server Git / sensitive / reparse | `0 / 0 / 0 / 0` |
| detached worktree | 已精确清理 |

bundle 上传到 `/home/admin/gatew-release-upload/c16f27c3-verification`。本地与远端 bundle size/hash、manifest hash、artifact/entry count 完全一致；staging owner 为 `admin:admin`、mode `0755`、symlink=`0`，`nqgatew` 不可写，顶层只包含 `bundle.tar` 与 `source/`。

## 4. Linux 安装、POSIX 与 ownership

正式 release：

```text
/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f
```

canonical installer 完成原子安装。安装 SSH 曾在返回前被远端关闭；重新建立独立连接后确认 release 完整、无 `.install-*` stage，并由 installed verifier 返回：

```text
PASS / IMMUTABLE_RELEASE_VERIFIED
posixVerified=true
artifactCount=131
```

release root、manifest、关键 executable、contract module 与 systemd template 的 owner/group/mode 均符合 manifest；bad directory/file=`0`、symlink=`0`、`.git`=`0`。9 个关键 artifact 对 `nqgatew` 均不可写，release closed set 中没有 upload 临时文件、runtime evidence 或 credential。

`currentSymlinkBefore == currentSymlinkAfter`：

```text
/opt/nexus-quant/releases/1b501488076fae79e15b84579a02f5c580fa51b3
```

## 5. 运行事实目录信任边界

使用不含正式 RunId 的 `.deployment-verification-*` fixture 执行实际权限测试：

- worker 写 `stop-intent`、`completion-marker`、`acceptance-verification`、`terminal-status` 与 lock 路径全部被拒绝。
- worker 仅对 evidence/runtime 合同要求的最小路径具备写权限。
- release 对 worker 全部只读。
- fixture 由 root 精确清理，残留=`0`。
- 路径逃逸、绝对路径、symlink/reparse、错误 RunId、错误 release binding、未知 stop reason、错误 checksum、stale stop intent、临时文件冒充 terminal/completion marker均由 control self-test、remediation/security regression 与权限 fixture fail-closed 拒绝。

## 6. Systemd 合同

unit links 已通过 canonical installer 指向新 release 内模板并执行 `daemon-reload`，但没有 start/restart：

| 检查 | 结果 |
| --- | --- |
| `systemd-analyze verify` | exit `0` |
| worker | `User=nqgatew / Restart=no / RuntimeMaxUSec=infinity / NoNewPrivileges=yes` |
| fail-close | `Type=oneshot / TimeoutStartSec=30s / Restart=no / PrivateNetwork=yes / RestrictAddressFamilies=AF_UNIX` |
| capabilities | Ambient 与 bounding set 为空 |
| active units / timers / jobs / MainPID | `0 / 0 / 0 / 0` |

`systemd-analyze` 只报告既有 `cloudmonitor.service` 的 `KillMode=none` 与 legacy `/var/run` warning；它们不属于 GateW unit，不影响本轮 exit 0。

## 7. Linux 离线验证

全部被测 runtime artifact 来自正式安装路径。由于 131-artifact closed set 不包含仓库级 test runner，remediation/security runner 在一次性 `/tmp` harness 内执行：control、worker、fail-close、module、verifier 与 systemd template 从 installed release 复制并逐项确认 SHA-256 一致；test runner、Attempt-09 fixture 与 builder 静态样本来自同一 `c16f27c3...` source tree。服务器 Git checkout 未使用，harness 全部精确清理。

| 验证 | 结果 |
| --- | --- |
| control self-test | `PASS / FORMAL_CONTROL_SELF_TEST / 50 cases` |
| fail-close self-test | `PASS / LIGHTWEIGHT_FAILCLOSE_SELF_TEST / 8 cases / 1344ms` |
| worker self-test | `PASS / SUPERVISOR_SELF_TEST / 59 cases` |
| installer self-test | `PASS / RELEASE_INSTALLER_SELF_TEST` |
| installed verifier | `PASS / IMMUTABLE_RELEASE_VERIFIED / posixVerified=true` |
| remediation regression | `PASS / 32 cases`；fail-close bounded=`909ms` |
| security regression | `PASS / 12 cases` |
| positive 168h fixture | `PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED` |
| Attempt-09 rejected fixture | evidence=`PASS`、acceptance=`FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED`、finalizer=`BLOCKED / ACCEPTANCE_VERIFY_REQUIRED` |
| legacy `verify` | exit `2 / BLOCKED / VERIFY_ACTION_SPLIT_REQUIRED` |
| tamper copy | exit `2 / BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH` |

worker self-test 第一次直接从 immutable release 路径执行时，其默认 test root 指向 release 同级 `target/` 并因 `nqgatew` 无写权限失败；这证明 release 只读。复跑使用一次性 test-root copy，copy 与正式 worker artifact SHA-256 均为 `52b5aa75b0088188d6770c2a976c1ba173e4bdd6d51da040ef3536c835acc102`，并通过 59 cases，未修改 release owner/mode。

security runner 原始 Linux 执行在 6 cases 后因 PowerShell on Linux 不支持 `Start-Process -WindowStyle Hidden` 停止；只在临时 test runner 中移除该 UI-only 参数后 12/12 通过，被测 installed artifacts 未改。

## 8. Attempt-09 历史零写入

真实 run `gatew-soak-20260722T111144Z-ac00f878` 在执行前后均为：

```text
files=15
directories=3
aggregateContentHash=43c5e8f21a5f7e5659a187de313bf3b7a419e30986a1fa876a8cc1023fdee570
```

按 frozen config 将新 control 的 `NQ_GATEW_RELEASE_ROOT` 绑定到历史 immutable release `1b501488...` 后，`verify-evidence` 返回 `PASS / FORMAL_EVIDENCE_VERIFIED`：sampleCount=`6146`、hash chain PASS、forbidden/fallback/raw-response/secret-exposure=`0/0/0/0`。

真实历史 acceptance 没有补造整改后新增的受控文件，因而 fail-closed 返回 `BLOCKED / REQUIRED_CONTROL_FILE_MISSING`；finalizer 返回 `BLOCKED / ACCEPTANCE_VERIFY_REQUIRED`。这没有改变 Attempt-09 已因有效时长不足而 `REJECTED` 的历史结论。独立冻结 fixture 精确返回 `FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED`，并包含 `OBSERVED_DURATION_INSUFFICIENT`。

## 9. 最终不变量

- hostname：`iZrj9gpab986sm4d0bb6agZ`；NTP=`yes`；boot=`2026-05-15 21:33:54`，未重启。
- `current` 未变化；new release 仅安装、未激活。
- latest state run 仍为 Attempt-09；`2026-07-30` 新 state directory=`0`。
- active GateW units/timers/jobs/runtime process=`0/0/0/0`。
- runtime entries/drop-in/temp harness residue=`0/0/0`。
- Attempt-10=`NOT_CREATED / NOT_AUTHORIZED`；acceptance clock 未创建。
- OKX calls=`0`；credential access=`0`；database access/change=`0`。
- P0=`0`；P1=`0`。

## 10. Findings、限制与回滚

- P0：无。
- P1：无。
- P2：无。
- P3：官方 security regression runner 的 `-WindowStyle Hidden` 不是 Linux PowerShell 兼容参数；本轮只对临时 runner 做兼容性去除，未修改生产代码。
- 已知限制：本轮没有启动 Attempt-10，也没有验证真实 Attempt-10 pre-create、真实 OKX permission 或 168h runtime acceptance；这正是下一独立任务的范围。
- 回滚：`current` 未切换，因此 runtime 回滚不需要动作。若需撤销本轮 unit template 安装，只允许使用旧 release `1b501488...` 的 canonical `install-units` action 恢复两个 unit link，再执行 `daemon-reload` 与 `systemd-analyze verify`；不得手工编辑 unit 或删除新 release。WIP 脚本分支保持不合并。

## 11. Authority 决定

成功后治理状态：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT
work_batch_status=DEPLOYMENT_VERIFIED|CI_GREEN|ATTEMPT_10_PREPARATION_PENDING
work_batch_commit=c16f27c3c68d2484ad140d0557b879de08b7c78f
work_batch_ci_run=30537845010
next_action=NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START
```

这里的 commit/CI 绑定已安装 release source baseline；本轮 docs/evidence commit 不部署服务器。唯一下一动作只授权独立的 Attempt-10 preparation/start hard-gate 任务，不表示 Attempt-10 已创建或可绕过 pre-create、release、systemd、credential metadata、permission、kill-switch、zero-residual 与新 acceptance clock 的全部前置检查。
