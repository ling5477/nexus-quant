# NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE — Attempt 01

## 1. Task classification 与结论

- Task classification：`PRODUCTION_READONLY_SOAK_ACCEPTANCE / CONTINUITY_VERIFICATION / HASH_CHAIN_VERIFICATION / SECURITY_ACCEPTANCE / SOAK_SEAL / CANONICAL_AUTHORITY_TRANSITION`。
- NQ-only；starting HEAD=`2fdeadfdc988bbdac9a858466948ccfa0a4acce1`；starting exact-head CI=`31292449178 / completed / success / 10 jobs / bad=0`。
- Attempt=`13`；RunId=`gatew-soak-20260801T180544Z-140bbcd1`。
- Final decision：`PASS / ATTEMPT_13_168H_ACCEPTED / CONTINUOUS_RUNTIME_VERIFIED / HASH_CHAIN_VERIFIED / ZERO_FORBIDDEN_ENDPOINT / ZERO_SECRET_EXPOSURE / LIVE_DISABLED / KILL_SWITCH_ENGAGED / SOAK_SEALED / READY_TO_COMMIT`（通过 / Attempt-13 168 小时已接受 / 连续运行已验证 / hash chain 已验证 / 禁止端点为零 / 敏感暴露为零 / LIVE 关闭 / kill switch 已启用 / soak 已封存 / 可进入提交）。

本任务未修改治理合同/checker，未创建 Attempt-14，未执行 freeze/archive/tag，也未增加 endpoint、权限或交易写侧能力。提交、push 与本提交 exact-head CI 在本文写入时仍为 `NOT_RUN`（未执行）。

## 2. Authority before

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-OKX-READONLY-SOAK-ATTEMPT-13
work_batch_status=RUNNING|PENDING_168H
next_action=NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE
live=DISABLED
```

Governance contract=`scripts/docs/governance-workflow-contract.json` schema `1.4.0`；本轮只执行已冻结的 `SOAK_RUNNING -> SOAK_COMPLETED` 与 `RUNNING|PENDING_168H -> ACCEPTED|READY_TO_COMMIT` exact transition。

## 3. 时间与 acceptance clock

| 项目 | 结果 |
| --- | --- |
| serverNow | `2026-08-09T03:40:25.791470105Z` |
| NTP | `NTP=yes / NTPSynchronized=yes` |
| acceptanceStartAt | `2026-08-01T18:13:13.9139125Z` |
| plannedAcceptanceAt | `2026-08-08T18:13:13.9139125Z` |
| actualAcceptanceAt | `2026-08-09T03:55:25.1396887Z`（acceptance proof verifiedAt） |
| final valid sample | `2026-08-08T18:13:34.4112272Z` |
| elapsed | `604820.4973147s >= 604800s` |
| clock binding | checksum=`a4dab5f2d5d3be26762e4b876c6695d16427a1311071277488308446f6285200`；无 rollback/jump evidence |

## 4. Runtime / release / credential / allowlist identity

| 项目 | 结果 |
| --- | --- |
| runtime release/source | `b103069d8bfcecccba0b4d590317ddccc66898b9` / `EXACT_COMMIT` |
| release path/current target | `/opt/nexus-quant/releases/b103069d8bfcecccba0b4d590317ddccc66898b9`；current 精确指向同一路径 |
| manifest SHA-256 | `f5b891e0d5547f25077a165a636ca6b40600bc8deedfe78f1110f7bddb44e4cb`；期满 canonical immutable verifier PASS |
| bundle baseline | `e4e0264e78d0cc35598af7dddd4f41c59da44cba452abdce6814ab44cd3e79d9`，bytes=`61,236,224`；来自启动 evidence，服务器 installed artifact set 以同一 manifest 验证 |
| starting runtime CI | `30710943874` |
| cadence / duration | `900s / 168h` |
| endpoint allowlist | `gatew-okx-private-readonly-v1`；656/656=`ACCOUNT_CONFIG_AND_BALANCE_READ` |
| evidence schema | `gatew-soak-evidence-v2` |
| soak database | descriptor/frozen-config identity match；reachable；schema match；Flyway `35` present |
| credential reference | systemd encrypted reference present；acceptance fingerprint SHA-256=`90fd78720828886b676ff2f5d03b18913daeba9bdb51aa8470ff297de4b37bc6`；不包含 credential material |

同一 worker PID 与 immutable systemd credential reference 持续整个窗口。生产 DB 只读聚合确认 active OKX credential count=`1`，窗口内 credential row lifecycle change=`0`、`ROTATED` audit=`0`、其他 lifecycle audit=`0`；credential status=`ACTIVE`，rotated/revoked marker 均为空，permission=`READ_ONLY`，withdraw=`false`，IP allowlist required/probe=`true/PASSED`，permission probe=`SUCCEEDED`，failed auth count=`0`。`verification_status=PENDING` 是既有非 acceptance 字段；冻结 pre-create/acceptance contract 以 `credential_status` 与 persisted permission facts 判定，本轮未改写该状态。

## 5. Systemd / 进程连续性

- Unit=`nq-gatew-soak@gatew-soak-20260801T180544Z-140bbcd1.service`。
- Starting/final worker PID=`478613/478613`；worker-start、unit-start、fresh-SSH、acceptance clock 与 acceptance proof 均绑定同一 PID。
- `ExecMainStartTimestampMonotonic=6755802950269` 与启动 snapshot 精确一致；`NRestarts=0`。
- Journal window=`2026-08-01T18:09:07.9569850Z .. 2026-08-08T18:13:16.5133560Z`；records=`699`；distinct invocation=`1`；distinct boot=`1`。
- `Restart=no`；无 main-process exit、restart、stop/start、replacement worker、OOM、crash、uncaught exception 或第二正式 worker evidence。
- Seal 前 unit=`active/running`、residual=`1`（唯一正式 worker）；seal 后 unit=`inactive/dead`、MainPID=`0`、residual=`0`、Result=`success`、NRestarts=`0`。

结论：`PASS / CONTINUOUS_RUNTIME_VERIFIED`。

## 6. 完整 heartbeat/sample stream

| 项目 | 结果 |
| --- | --- |
| heartbeat/sample count | `656` |
| first | sequence=`1`；`2026-08-01T18:13:13.9139125Z` |
| final | sequence=`656`；`2026-08-08T18:13:34.4112272Z` |
| sequence | `1..656` 连续、唯一、无 reset/replay/backward/cross-attempt |
| timestamp | 严格单调；final >= plannedAcceptanceAt |
| maximum gap | `1797s`；sequence range=`1 -> 2` |
| outcomes | `PASSED_READ_ONLY=656`；failures=`0`；realCycleOutcomeProven false=`0` |
| probes | account config=`SUCCEEDED 656`；balance=`SUCCEEDED 656` |

最大 gap 发生于冻结启动顺序的首样本、fresh-SSH/acceptance-clock create-once 与首个 `Wait-ForCadenceOrControl(900)` 之间；启动 evidence 已记录该 clock wrapper 阶段。冻结代码把 `900s` 定义为 clock 建立后的等待时长，没有定义样本 `observedAt` 的额外 max-gap/grace 数值。这里不新造 tolerance；sequence、timestamp、systemd invocation、PID 与 hash chain 均连续。该可观测性限制列为 P2，不改写为失败或隐藏。

Continuity verdict：`PASS / HEARTBEAT_CONTINUITY_VERIFIED_WITH_EXPLICIT_FROZEN_CADENCE_SEMANTICS`。

## 7. Hash chain 与安全计数

- Canonical `verify-evidence`：`PASS / FORMAL_EVIDENCE_VERIFIED`。
- Hash chain：从 genesis/sequence 1 全量重算至 sequence 656，`PASS / HASH_CHAIN_VERIFIED`。
- Final chain hash=`1debcf6c4af234430dfccaf1bcc8276d503394ddc9f12f1bbfc8f08b6477249b`。
- Evidence manifest SHA-256=`3ec42822fc2ff5b015f999b0ceb62b152c5179c9431cd17c584d59e3d2eaf003`。
- missing/broken/duplicate/replay/out-of-order=`0/0/0/0/0`。
- forbidden/fallback/raw/secret=`0/0/0/0`。
- order/cancel/transfer/withdraw/LIVE execution=`0/0/0/0/0`；全部 656 条记录只属于冻结的 account config/balance read-only endpoint category。
- 全部样本 permission classification=`READ_ONLY_WITH_IP_ALLOWLIST`，kill switch=`ENGAGED`。
- Worker 进程九项 safety flags 全部为字面量 `false`；`NQ_GATEW_OKX_READONLY_SOAK_ENABLED=true` 只允许冻结的 read-only soak。

## 8. Runtime anomalies 与 DB/evidence integrity

- systemd journal 与 worker log metadata：OOM/crash/restart/auth failure/clock anomaly/hash error/write failure/DB integrity error/forbidden/fallback/sensitive exposure 均为 `0`；worker log directory 存在且 file count=`0`。
- Attempt-13 只有一个 RunId；samples 无缺口、无 duplicate identity、无其他 Attempt 混入、无截断；hash chain 与存储一致。
- 生产 PostgreSQL reachable，database/schema identity 与 frozen config 一致，Flyway 35 成功记录存在；credential lifecycle/permission 只读聚合无窗口漂移。
- 未补写、修复、删除或重算覆盖历史 heartbeat；canonical completion marker、acceptance proof、stop intent 与 terminal 均为 root create-once/CAS 路径。

## 9. Acceptance proof 与 seal

| 项目 | 结果 |
| --- | --- |
| verify-acceptance | `PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED` |
| acceptance proof checksum | `0cbb037cbf31a38583399203c72368081a85404428eb4f07dc92efd77bb4a1d8` |
| finalize-acceptance | `PASS / ACCEPTANCE_RESULT_FINALIZED` |
| terminal result | `ACCEPTED_168H_READONLY_SOAK` |
| terminal checksum | `d61c18a6115733f31e90928aad5980a6cb08cc307e9459e81c6d9030b7a06734` |
| stop intent checksum | `a1ebdea97046467f3ef68ccb7986dfcd6dc079d192794cad4ae1083ec6e73fc9` |
| finalizedAt | `2026-08-09T03:56:59.4765120Z` |
| verify-terminal | `PASS / FORMAL_TERMINAL_VERIFIED`；stop classification=`AUTHORIZED_CONTROLLED_STOP` |
| post-seal | worker/fail-close=`inactive/dead`；MainPID=`0`；active formal worker=`0`；residual=`0`；auto restart=`0` |

Finalizer 本身 credential/network/OKX calls=`false/false/false`。Immutable release、soak DB、journal、heartbeat/evidence 与 credential reference 均保留；未创建 Attempt-14。

## 10. Findings

- P0：0。
- P1：0。
- P2：1。冻结 acceptance checker 不定义独立 maximum-gap threshold；本轮按冻结 loop 语义解释并完整披露 sequence `1 -> 2` 的 `1797s` gap。后续若要把 sample timestamp 最大间隔变为 hard gate，必须由独立治理/实现任务定义，不能在本验收临时追加。
- P3：0。

Known limitations：服务器不保留 transport bundle 作为期满重哈希输入；本轮使用启动 evidence 的 bundle hash，并由 installed immutable release verifier 对同一 manifest/artifact closed set 复核。该限制不改变已冻结 acceptance contract，也不表示 bundle 未在启动任务验证。

## 11. Authority after（pre-commit）

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-13-168H-ACCEPTANCE
work_batch_status=ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
attempt13Runtime=SOAK_COMPLETED
attemptStatus=COMPLETED|ACCEPTED
productionDeployment=STOPPED
productionSoak=COMPLETED
worker=STOPPED
acceptanceClock=COMPLETED
live=DISABLED
killSwitch=ENGAGED
next_action=NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE-COMMIT-AND-PUSH
```

只有 acceptance commit/push 与 exact-head CI `completed / success / 10 jobs / bad=0` 后，才允许按 schema `1.4.0` 同步为 `ACCEPTED|CI_GREEN|FREEZE_READY`。GateW 仍为 `IN_PROGRESS|NOT_FROZEN`，本轮没有 freeze/archive/tag 授权。
