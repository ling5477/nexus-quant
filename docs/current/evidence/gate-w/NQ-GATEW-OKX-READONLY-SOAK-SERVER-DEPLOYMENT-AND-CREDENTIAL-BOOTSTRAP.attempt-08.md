# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 08

## 1. Task classification

- 执行模式：`ATTEMPT_08 / START_ONLY_AFTER_LINUX_DETACHMENT_REMEDIATION`。
- 类型：`REAL_OKX_READONLY_SOAK_START / FIXED_RUNTIME_VERIFICATION / EXISTING_ENCRYPTED_CREDENTIAL_REUSE / SYSTEMD_TRANSIENT_SUPERVISION / FIRST_REAL_SAMPLE_VERIFICATION / TASK_EVIDENCE`。
- 等级：L 级高风险 deployment / credential / real exchange read-only 运行任务。
- 范围：NQ-only；真实 endpoint 只允许 `GET /api/v5/account/config` 与 `GET /api/v5/account/balance`。
- 执行日期：`2026-07-18`。
- 最终结论：`BLOCKED / FIRST_REAL_SAMPLE_VERIFIED / SYSTEMD_DETACHMENT_INITIAL_VERIFIED / SOAK_DATABASE_NOT_LOCAL / AUTOMATIC_KILL_SWITCH_ENGAGE_FAILED / MANUAL_FAILURE_STOP_RECOVERED / REAL_OKX_READONLY_SOAK_NOT_STARTED / SEVEN_DAY_ACCEPTANCE_NOT_STARTED`（阻断 / 首条真实只读样本已验证 / systemd 脱离初始验证已通过 / soak 数据库本地性校验失败 / 自动 kill switch 接合失败 / 人工 failure-stop 已恢复 / 真实 OKX 只读 soak 未启动 / 七天验收未开始）。

首个真实 cycle 和 fresh SSH reconnect 一度满足启动后的初始观察条件，但第二周期在任何 credential 或 OKX 网络访问前命中 `SOAK_DATABASE_NOT_LOCAL`。自动 fail-close 随后因相同运行环境不能取得有效 DB 连接而上报 `KILL_SWITCH_ENGAGE_FAILED`；人工 `failure-stop` 已停止并收集 transient unit、清除残留进程并把持久化 kill switch 恢复为 `ENGAGED`。因此此前计算的候选 acceptance 时间全部失效，不得宣称 soak started、七天 acceptance clock started、GateW frozen、LIVE ready 或 trading authorized。

## 2. Fixed baseline, CI and authority

| Item | Verified result |
| --- | --- |
| Repository / branch | `E:\\Project\\nexus-quant` / `dev` |
| Local `HEAD` / `origin/dev` | `408bb739c84c9852d8ec3bd437bdcc645d3728da` / exact match |
| Fixed runtime commit | `408bb739c84c9852d8ec3bd437bdcc645d3728da` |
| Fixed runtime CI | `NQ CI Baseline` run `29595921755` |
| CI result | `completed / success`；`headSha` exact match；10 jobs；bad=0 |
| Supervisor Git blob | `c4eaf95ebf03145f7e49b620509915656cc094c2` |
| Supervisor SHA-256 | `8c174b8045e45b862518f36dce0ee6e000dda597bc4ea4d64b05afe50997f635`；LF；CR count=0 |
| Management JAR SHA-256 | `02bd70fb7127747a1e729bca934d616ca4218dd9f84034f640bd1939cc6ff761` |
| Current authority | GateW `IN_PROGRESS|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED` |
| LIVE | `DISABLED` |

`docs/current/STATUS.md` 继续作为唯一 current authority。本 attempt 不修改 `STATUS.md` 或 `ROADMAP.md`；运行失败不改变 Gate 或 release authority。

## 3. Pre-run control invocations

在真实 run 创建前共发生 4 次 `-Action start` 控制调用，均由 preflight fail-closed 拒绝：

| Sequence | Decision | RunId | Credential / OKX access |
| --- | --- | --- | --- |
| 1 | `BLOCKED / HARNESS_WORKTREE_NOT_CLEAN` | empty | `false / false` |
| 2 | `BLOCKED / API_KEY_REQUIRED` | empty | `false / false` |
| 3 | `BLOCKED / EXACT_HEAD_CI_NOT_VERIFIABLE` | empty | `false / false` |
| 4 | `BLOCKED / EXACT_HEAD_CI_NOT_VERIFIABLE` | empty | `false / false` |

这些调用没有创建 run、没有 acceptance clock、没有 credential access、没有网络调用，也没有触发 permission probe。不得把它们计为 4 个 soak attempts 或真实 OKX cycles。

## 4. SSH, server isolation and fixed runtime

- Server：`root@47.251.74.35:22`；hostname=`iZrj9gpab986sm4d0bb6agZ`；public-key/BatchMode SSH=`PASS`。
- NTP、资源、磁盘、swap 和 GitHub Actions read authentication hard gate 均通过。
- 公网 listener：仅 `22/tcp`；public non-SSH listener count=`0`。
- Management app：仅 loopback `127.0.0.1:18889`；health/readiness=`UP`。
- PostgreSQL：仅 loopback `127.0.0.1:55432`；database=`nq_gatew_okx_readonly_soak`；container status=`running`；restart count=`0`。
- 旧 `sub2api*`、`nq-freeze-*`、`sing-box` workloads 保持 stopped/retained；`iperf3` 保持 inactive/disabled。
- Server checkout：detached fixed commit；tracked dirty=0；server-side exact-head CI、artifact blob/hash 和 JAR hash 均精确匹配。

Server isolation：`PASS / SERVER_ISOLATED`。Artifact verification：`PASS / RUNTIME_ARTIFACT_MATCHED`。

## 5. Historical run immutability

两个历史 run 均保持 terminal、PID 0、acceptance clock未开始、`final-summary.json` absent；本 attempt 未 resume、append、rewrite 或 delete。

| Run | File | Reverified SHA-256 |
| --- | --- | --- |
| `gatew-soak-20260716T145410Z-230ae5be` | `manifest.json` | `ff3c5c868a65cc61d86064f98eff82ab5cfad0884b76e276fbdbebb7648c7996` |
| 同上 | `heartbeat.json` | `ae534d7e3a5b495fb84222ddcef45cd914d9c27d8e138c3a0c573e627bf40768` |
| 同上 | `samples.jsonl` | `eab0a6a85eb3477f661eb8c4a7a8e121136dd0b2ad7e9766afde1048d984e65b` |
| 同上 | `failures.jsonl` | `eab0a6a85eb3477f661eb8c4a7a8e121136dd0b2ad7e9766afde1048d984e65b` |
| `gatew-soak-20260717T122834Z-eb5ef11c` | `manifest.json` | `654f3c2c866798e9f9a74915c97e0054445ccfb30222c39ca3dc32151b4e71c2` |
| 同上 | `heartbeat.json` | `6555ced4ad69cc2a07961b3a097c18fa627fe8e2a45c9890511b56658c9344bc` |
| 同上 | `samples.jsonl` | `3c7bef142d30875c433b7cf92c7921d78ae36b949bd7f333f8925f5d6a16ea12` |
| 同上 | `failures.jsonl` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| 同上 | `stop-request.json` | `88c56ba5b160066a7d6b433c8042a9f67ba3d8cb9cfe9861f7c000a45df3efa9` |

Historical-run hash verification：`PASS / HISTORICAL_RUNS_IMMUTABLE`。Historical acceptance clocks：`NOT_STARTED / NOT_STARTED`。

## 6. Credential and permission metadata reuse

仅执行聚合、布尔和引用一致性核验；未输出 credential ID、owner/account ID、encrypted payload、master key、provider response、余额或账户数据。

| Item | Verified result |
| --- | --- |
| Credential rows / active / encrypted | `1 / 1 / 1` |
| Active conflict | `0` |
| Key version | `1` |
| Credential reference changed | `false` |
| Permission probe status | `SUCCEEDED` |
| Permission scope | `READ_ONLY` |
| Read / Trade / Withdraw | `true / false / false` |
| IP allowlist required / status | `true / PASSED` |
| Expected IP | `47.251.74.35` |
| Residual `IN_PROGRESS` | `0` |
| Subsequent permission / lifecycle events | `0 / 0` |
| Direct OKX secret fields / secret exposure | `0 / 0` |

仓库未定义额外数值 TTL；现有 metadata、credential reference、key version、expected IP 和 permission facts 均未变化，freshness hard gate通过。本 attempt未重新录入、轮换、展示或解密 credential，也未执行独立 permission probe。

## 7. Runtime environment bootstrap and rollback

为 transient systemd worker在固定 owner-only环境文件中补齐 4 个非 secret运行引用：

- `NQ_GATEW_SOAK_OWNER_ID`
- `NQ_GATEW_SOAK_ACCOUNT_ID`
- `SPRING_PROFILES_ACTIVE`
- `NQ_GATEW_SOAK_CURRENCIES`

修改文件：`/opt/nexus-quant/gatew-soak/config/management.env`；owner/group/mode=`nqgatew:nqgatew/600`。未加入 direct OKX key/secret/passphrase。

回滚备份：`/opt/nexus-quant/gatew-soak/runtime/attempt-08-backup/management.env.before-soak-runtime-refs`；owner/group/mode=`nqgatew:nqgatew/600`。本 attempt未自动执行回滚，因为该文件仍是 management/runtime配置事实；后续若授权回滚，应以 owner-only原子恢复、loopback health验证和禁止真实 OKX调用为 hard gate，不得删除 blocked run evidence。

## 8. Supervisor self-test and start hard gate

- Deployed supervisor PowerShell self-test：`55 / 55 PASS`。
- Linux residual enumeration、launcher schema v2、evidence schema v2、sanitizer、hash-chain、systemd transient contract、unsafe rejection均通过。
- Self-test：`networkCalled=false`、`credentialAccessed=false`、`acceptanceClockStarted=false`；未创建 persistent real run。
- 启动前 fixed commit/CI/artifacts/server isolation/credential reference/permission metadata/Flyway V35/historical hashes/endpoint allowlist/LIVE false全部通过。
- 启动前 kill switch：`GLOBAL_TRADING / ENGAGED / version=5`。

Final start hard gate：`PASS / START_FINAL_HARD_GATE`。

## 9. New run and first real sample

新 run：

| Field | Verified result |
| --- | --- |
| RunId | `gatew-soak-20260718T035039Z-dd0be612` |
| Run manifest evidence schema | `gatew-soak-evidence-v2` |
| Sample `schemaVersion` | `gatew-soak-launcher-v2` |
| StartedAt | `2026-07-18T03:51:59.6612682Z` |
| PlannedEndAt | `2026-07-25T04:51:59.6612682Z` |
| Duration / cadence | `169h / 900s` |
| Unit | `nq-gatew-soak-gatew-soak-20260718T035039Z-dd0be612.service` |
| Initial MainPID | `3810293` |
| Runtime user/group | `nqgatew / nqgatew` |

首条真实 sample：

| Field | Verified result |
| --- | --- |
| Sequence / observedAt | `1 / 2026-07-18T03:53:18.3288427Z` |
| Result / reason | `PASSED_READ_ONLY / READ_ONLY_SAMPLE_ACCEPTED` |
| Config / balance probe | `SUCCEEDED / SUCCEEDED` |
| Permission classification | `READ_ONLY_WITH_IP_ALLOWLIST` |
| Credential accessed / network called | `true / true` |
| Endpoint category | `ACCOUNT_CONFIG_AND_BALANCE_READ` |
| Real outcome proven | `true` |
| Record hash | `2ba859538d84722c578c55af7d07a2806186de6eb4b437e9aeb17895f7b68e87` |
| Failure / fallback / raw response / secret exposure | `0 / 0 / 0 / 0` at that observation |

实际 sample字段是 launcher DTO schema `gatew-soak-launcher-v2`；run manifest才声明 `evidenceSchemaVersion=gatew-soak-evidence-v2`。两者不得混写。

## 10. Initial systemd detachment verification

首样本后关闭启动 SSH并从 fresh SSH复核：

- `detachmentVerifiedAt=2026-07-18T03:55:41Z`；
- unit当时为 `loaded / active / running`；
- fresh reconnect后 MainPID仍为 `3810293`；
- runtime owner、unit/runId/sentinel/evidence identity一致；
- 公网非 SSH listener count=`0`。

初始 detachment观察为 `PASS / SYSTEMD_DETACHMENT_INITIAL_VERIFIED`，但它只证明当时 worker已脱离 SSH存活。第二周期 hard blocker和随后 fail-close已使持续运行合同终止，因此该观察不得保留为 acceptance clock成功事实。

候选 `acceptanceStartAt=max(first sample observedAt, detachmentVerifiedAt)` 曾为 `2026-07-18T03:55:41Z`，现已标记 `INVALIDATED / NOT_STARTED`。任何基于该候选值的 planned acceptance时间均不再有效。

## 11. Second-cycle blocker and automatic fail-close failure

第二周期：

| Field | Verified result |
| --- | --- |
| Sequence / observedAt | `2 / 2026-07-18T04:09:35.9073634Z` |
| Result / reason | `BLOCKED / SOAK_DATABASE_NOT_LOCAL` |
| Credential accessed / network called | `false / false` |
| Endpoint category | `NONE` |
| Config / balance probe | `NOT_RUN / NOT_RUN` |
| Real outcome proven | `true` |
| Record hash | `4435a3e9427de260198fcb822dda4779931a02394d2d721acaef76ed7ddf3631` |

`SOAK_DATABASE_NOT_LOCAL`属于 immediate-stop reason。worker随后执行自动 `Stop-FailClosed`，但 `engage` cycle未返回 `resultStatus=ENGAGED / killSwitchObservedState=ENGAGED`，heartbeat进入：

```text
STOP_FAILURE / KILL_SWITCH_ENGAGE_FAILED
```

这不是安全成功状态；必须人工恢复。本 attempt未 restart、resume、重跑 sample或重跑 permission probe。

## 12. Manual failure-stop and final safety state

立即执行一次人工：

```text
failure-stop / gatew-soak-20260718T035039Z-dd0be612
```

恢复结果：

| Item | Verified result |
| --- | --- |
| Stop result | `STOP_COMPLETED / kind=failure` |
| Heartbeat | `FAILURE_STOPPED / OPERATOR_FAILURE_STOP` |
| Heartbeat observedAt | `2026-07-18T04:13:05.2795047Z` |
| Unit state | `LoadState=not-found / ActiveState=inactive / SubState=dead` |
| MainPID / residual process | `0 / 0` |
| Public non-SSH listener | `0` |
| Kill switch current state | `ENGAGED / version=7` |
| Kill-switch max event version | `7` |
| Sample / failure count | `2 / 1` |
| Valid real PASS / fallback / raw / secret | `1 / 0 / 0 / 0` |
| Hash-chain | `PASS / HASH_CHAIN_VERIFIED` |
| `final-summary.json` | absent |

本次 kill-switch事件序列与运行合同一致：version 5为先前 `ENGAGED`；bootstrap把隔离 fixture变为 version 6 `DISENGAGED`；人工 recovery把它恢复为 version 7 `ENGAGED`。自动 fail-close没有生成错误的成功事件。

Current run terminal file hashes：

| File | SHA-256 |
| --- | --- |
| `manifest.json` | `f1400346b1ae0535abc60592aeee9b5d8da7c6e40bd69f1a5858c6c7e7323b73` |
| `heartbeat.json` | `eb29ef89bd055fd98ac9985fd3e64329faedc9e2e72be7be6e252312b7fe59a9` |
| `samples.jsonl` | `f247170f6a94a09839134d3db30f3a3931981160acc7483c87fc96b610ebd3b7` |
| `failures.jsonl` | `15743ddb5a9093ccce6d74e27b9739e1a2029d2b57b7fbfd29f52e0d5b9b5935` |
| `stop-request.json` | `0d87f63b8124f3b2b0d791342de783ef6843dad4eec6cc6ece6391a188d859de` |
| `supervisor.json` | `1df54f8d35d9858eff36bd686562f6f4330d1eba7639ec587240fa49198409be` |

## 13. Read-only RCA

### 13.1 `SOAK_DATABASE_NOT_LOCAL`

1. `management.env`中 `NQ_GATEW_SOAK_DB_URL`、`NQ_GATEW_SOAK_DB_USER`、`NQ_GATEW_SOAK_DB_PASSWORD`均存在，但其 RHS 是变量引用，不是 self-contained literal；本检查只输出 shape布尔事实，没有输出任何值。
2. 前台 start进程使用已展开运行环境。首个 bootstrap/sample能通过 `SafetyConfig.safeDatabaseTarget()`、Flyway V35和DB本地性检查，是该展开环境有效的运行证据。
3. transient unit通过固定 `EnvironmentFile=/opt/nexus-quant/gatew-soak/config/management.env`加载环境。systemd EnvironmentFile不执行 shell变量展开，worker因此取得字面量引用。
4. `GateWOkxReadonlySoakCycleTest.SafetyConfig.safeDatabaseTarget()`只接受 literal `jdbc:postgresql://127.0.0.1...`或`localhost`且DB name含 gatew/soak；字面量变量引用失败并安全分类为 `SOAK_DATABASE_NOT_LOCAL`。
5. 该校验发生在 DB credential lookup和OKX transport之前；sequence 2精确证明 `credentialAccessed=false / networkCalled=false / endpoint=NONE`。

RCA结论：`CONFIRMED / SYSTEMD_ENVIRONMENT_FILE_VARIABLE_REFERENCE_NOT_EXPANDED`。

### 13.2 `KILL_SWITCH_ENGAGE_FAILED`

1. `Run-SoakLoop`收到 immediate-stop blocker后调用 `Stop-FailClosed`。
2. `Stop-FailClosed`的 `engage` cycle复用同一 systemd worker环境；它在进入持久化 kill-switch逻辑前再次被 DB target校验阻断。
3. supervisor因此没有把失败伪装为 `ENGAGED`，而是写入 `STOP_FAILURE / KILL_SWITCH_ENGAGE_FAILED`并退出。
4. 人工 `failure-stop`在已展开的控制环境中成功停止/收集 unit并执行 `DISENGAGED → ENGAGED`，最终DB authority为 version 7。

RCA结论：`CONFIRMED / AUTOMATIC_ENGAGE_SHARED_INVALID_DB_ENVIRONMENT`。

### 13.3 Terminal `status` limitation

人工 recovery后，原始 `systemctl`、PID/residual检查和 heartbeat均证明 terminal safe state；`evidence-verify`返回 `PASS / HASH_CHAIN_VERIFIED`。但 supervisor `-Action status`对已 `--collect`为 `not-found`的 unit返回：

```text
FAIL / SUPERVISOR_RECONNECT_STATUS_FAILED
```

该结果不推翻 unit/PID/DB/evidence的独立安全事实，但表示 terminal status contract仍有诊断缺口。本任务禁止代码修改，因此只记录，不修复。

## 14. Findings

- P0：无。LIVE、真实下单/撤单/转账/提现均未启用或调用；无 credential/raw response写盘；人工 recovery已把 kill switch恢复为 `ENGAGED`。
- P1：真实 systemd worker的固定 EnvironmentFile含 DB变量引用，systemd不展开，导致第二周期必然 `SOAK_DATABASE_NOT_LOCAL`；169h运行合同未建立。
- P1：自动 fail-close的 `engage`依赖同一无效DB环境，不能自动恢复此前由 bootstrap创建的 `DISENGAGED` fixture；本次依靠人工 `failure-stop`恢复。任何新 attempt前必须关闭该故障模式并证明自动恢复。
- P2：55-case self-test和offline systemd smoke未覆盖“真实 EnvironmentFile变量展开语义 + 第二周期 + 自动 engage”组合路径。
- P2：unit collect后 terminal `status`返回 generic reconnect failure；当前只能用 systemd/PID/heartbeat/evidence-verify组合完成终态复核。
- P3：无。

## 15. Validation, boundary and next action

实际运行验证：

```text
status -> FAIL / SUPERVISOR_RECONNECT_STATUS_FAILED
evidence-verify -> PASS / HASH_CHAIN_VERIFIED
systemctl -> not-found / inactive / dead / MainPID=0
residual supervisor -> 0
public non-SSH listener -> 0
kill switch DB authority -> ENGAGED / version=7
```

本地文档验证：`git diff --check`通过；`check-current-authority.ps1`为`PASS / CURRENT_AUTHORITY_CONSISTENT`；`check-doc-links.ps1 -Roots docs/current`检查124个引用、0 errors、1个既有GateJ historical warning；修改路径allowlist、forbidden-scope、`STATUS.md`/`ROADMAP.md`、高置信credential assignment扫描均通过；IDEA对新增evidence和index errors=0。纯文档收口未运行Maven、frontend或Python测试。

边界确认：

- 未修改 backend/frontend/research/scripts/deploy/.github/migration、supervisor、endpoint allowlist、credential、permission metadata、Gate archive、`STATUS.md`或`ROADMAP.md`。
- 未重新录入、轮换、展示或解密 credential；未重跑 permission probe。
- 真实 OKX只出现sequence 1合同允许的 config+balance typed `GET`；sequence 2在 credential/network前阻断。无 POST/PUT/PATCH/DELETE、order/cancel/transfer/withdraw或其他endpoint。
- LIVE/order submission/transfer/withdraw/AI/DH/real provider/client/exchange写侧均保持 disabled。
- 新 run已terminal failure-stop；禁止 restart、resume、手工启动 run-loop、追加/编辑服务器运行evidence或生成 PASS final summary。

状态命令：

```text
sudo -u nqgatew -H /usr/bin/pwsh -NoProfile -File /opt/nexus-quant/gatew-soak/app/repo/scripts/gatew/gatew-okx-readonly-soak.ps1 -Action status -RunId gatew-soak-20260718T035039Z-dd0be612
```

Evidence验证命令：

```text
sudo -u nqgatew -H /usr/bin/pwsh -NoProfile -File /opt/nexus-quant/gatew-soak/app/repo/scripts/gatew/gatew-okx-readonly-soak.ps1 -Action evidence-verify -RunId gatew-soak-20260718T035039Z-dd0be612
```

Stop状态：`ALREADY_FAILURE_STOPPED / UNIT_NOT_FOUND / PID_0 / RESIDUAL_0 / KILL_SWITCH_ENGAGED`；不得重复 start或resume。

本地文档回滚：删除本 attempt文件，移除evidence index对应行，并反向移除本轮`TESTING.md`/`WORKLOG.md` append；不得使用`git reset --hard`。服务器配置回滚只能在独立授权下，从固定 owner-only backup原子恢复`management.env`并做loopback health/no-network验证；服务器blocked run evidence不得删除或重写。

下一具体动作：独立 remediation必须把systemd worker所需DB环境转换为无shell展开依赖的owner-only literal合同，补真实EnvironmentFile第二周期no-OKX fixture、automatic engage recovery和terminal status回归；完成commit/push、exact-head CI、服务器部署与离线/受控验证后，只能由新的attempt创建全新run。不得进入`NQ-GATEW-FREEZE-BLOCKER-1-REAL-OKX-READONLY-SOAK-ACCEPTANCE`。

Evidence docs commit：`PENDING`。Evidence commit exact-head CI：`NOT_RUN`；只能在本文件与index/ledger精确提交并push后验证。
