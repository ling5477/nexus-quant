# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateV
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatev-freeze
last_frozen_gate_commit=530ce4e2bde416aa61944262cbfbadca556656cb
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateW-4
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c
accepted_batch_acceptance_head=07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c
accepted_batch_ci_run=29339016784
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION
work_batch_status=REVIEW_REJECTED|REMEDIATION_REQUIRED
work_batch_commit=ef803568ed56905cb9969477e1ad777d5a01faf6
work_batch_ci_run=30616271884
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
nq-current-authority:end -->

`docs/current/STATUS.md` 是 NexusQuant 当前阶段状态的唯一 authority。其他 current 文档只能引用或解释本文件，不得复制独立的 current Gate / next Gate 判定。

## 1. 当前阶段

- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。release tag 为 `nq-gatev-freeze`；annotated tag object 为 `06d5fea2af1765f143f277b111358b3abd8171ce`；peeled commit 为 `530ce4e2bde416aa61944262cbfbadca556656cb`。
- GateV-FREEZE：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；freeze candidate、implementation commit 与 acceptance head 均为 `7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`，`NQ CI Baseline` run `29191014596` 为 `completed / success`。
- GateV release closeout exact-HEAD CI：`NQ CI Baseline` run `29191677441`，`completed / success`，`headSha=530ce4e2bde416aa61944262cbfbadca556656cb`。
- GateV durable archive：[../gates/gate-v/README.md](../gates/gate-v/README.md)。它是历史证据，不覆盖本 authority。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateW-4 operational safety implementation/acceptance head `07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c` 的 exact-head run `29339016784` 已 `completed / success`，10 个实际 jobs 全部成功。GateW-4 已整体 `ACCEPTED / CI GREEN`（已接受 / CI 已通过）；GateW 尚未冻结。
- GateW-PLAN：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，`NQ CI Baseline` run `29199785253` 为 `completed / success`。
- GateW-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，`NQ CI Baseline` run `29219687588` 为 `completed / success`。该批次只建立 typed capability matrix、default-deny endpoint guard 与 GateW profile Bean 边界。
- GateW-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553`，`NQ CI Baseline` run `29230512781` 为 `completed / success`。该接受只覆盖两个 typed private read-only diagnostic operation；`REAL_SMOKE=NOT_RUN`，不表示远端 permission 已验证、LIVE 或交易授权。
- GateW-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。venue-rule facts、LIMIT-only order preview、bounded read-only reconciliation 与 diagnostic risk preflight 的独立 review 均 P0=0/P1=0，四个 acceptance heads 的 exact-head CI 均成功。implementation/acceptance head 为 `178b4951ba1406748170022c9940f84beaa8ab81`，run `29332316101`。
- GateW-4：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Blocker-1、operations、persistence/retention、human-review evidence binding、disposable backup/restore、11 场景 incident drill 与 10,000 次 local no-egress soak hard gates 均通过；internal-only assessment 不产生交易授权。Implementation/acceptance head 为 `07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c`，CI run `29339016784` 为 exact-head `completed / success`。
- GateW soak start contract remediation：`PASS / COMMITTED / CI GREEN / SUPERSEDED BY PRECREATE REMEDIATION`（通过 / 已提交 / CI 已通过 / 已由 pre-create remediation 替代）。Implementation commit `0e8e2c128c456542b3f7695c9620e4d170c3f4f6` 的 exact-head CI run `29766800343` 为 `completed / success / 10 of 10`；该 release 保留为已验证回滚点，不再是服务器 current。
- GateW pre-create sanitized prerequisite remediation：`PASS / IMPLEMENTATION COMMITTED / IMPLEMENTATION CI GREEN / SERVER DEPLOYED`（通过 / 实现已提交 / 实现 CI 已通过 / 服务器已部署）。Implementation commit `1b501488076fae79e15b84579a02f5c580fa51b3` 的 exact-head CI run `29837563573` 为 `completed / success / 10 of 10`；服务器 `/opt/nexus-quant/current` 固定到该 `EXACT_COMMIT` immutable release，129 artifacts、manifest/POSIX、root owner/mode、`nqgatewWritable=false`、systemd verify、sanitized pre-create 与完整 final offline acceptance 均通过。
- GateW-OKX-READONLY-SOAK-ATTEMPT-09：`FAILED / ACCEPTANCE REJECTED / INCIDENT REVIEW COMPLETED`（失败 / 验收已拒绝 / 事件复盘已完成）。唯一 run `gatew-soak-20260722T111144Z-ac00f878` 的最后有效样本为 `2026-07-27T22:23:14.5722391Z`，worker 于 `2026-07-27T22:25:46.8916254Z` 被 systemd stop transaction 以 `TERM` 终止；有效时长 `471795.0520427s < 604800s`，短缺 `133004.9479573s`。`PASS / FORMAL_SOAK_VERIFIED` 只证明现有 verifier 覆盖的 evidence integrity，不构成 168 小时 acceptance。
- GateW-ATTEMPT-09-FAILURE-REMEDIATION：`SECURITY REVIEW ACCEPTED / CI GREEN / DEPLOYMENT PENDING`（安全审查已接受 / CI 已通过 / 待部署验证）。Implementation commit `92adff7e55c2200692e892db2189132c243a1ac5` 的 exact-head CI run `30474856153` 为 `completed / success / 10 of 10`；security review/P1 minimal-fix commit `61f0b94fadbc87b883a7365eaacc4e8f63829a88` 的 exact-head CI run `30515021689` 为 `completed / success / 10 of 10`。两个 P1 已关闭：completion marker 改由 root control 在其他 acceptance hard gates 全部通过后写入并绑定 release/PID/evidence hash；stop intent 使用精确 reason allowlist 与 stale/no-exit 可审计退休恢复。Clean Commit A `EXACT_COMMIT` bundle 为 130 artifacts，manifest SHA-256 `f2ec7b00238cb2b718a82d298edc549d41833975ff42f2c8e5412e4db8b704fd`，closed set/hash/LF/mode/篡改拒绝通过；Windows `posixVerified=false`，新 release 未部署服务器。
- GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT：`DEPLOYMENT VERIFICATION FAILED / REMEDIATION REQUIRED`（部署验证失败 / 需要整改）。同一 source commit `61f0b94fadbc87b883a7365eaacc4e8f63829a88` 在不同时间重建得到不同 manifest hash：原记录 `f2ec7b00238cb2b718a82d298edc549d41833975ff42f2c8e5412e4db8b704fd`、rebuild-1 `b25b065c...ed12`、rebuild-2 `9c904671...a7d2`。根因为 hashed manifest 的 `createdAt` 使用实际构建时间，且 runtime-dependent ZIP/JAR 与 culture-sensitive sorting 未形成正式 canonical contract。旧 hash 仅为 `HISTORICAL_NON_REPRODUCIBLE_BUILD_OUTPUT / NOT_DEPLOYABLE_BASELINE`（历史不可复现构建输出 / 不可作为部署基线）；服务器变更为 0，Attempt-10 未创建且未授权。
- GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-FIX：`IMPLEMENTED / CI GREEN / DEPLOYMENT RETRY PENDING`（已实现 / CI 已通过 / 待重新部署验证）。Commit A `c16f27c3c68d2484ad140d0557b879de08b7c78f` 的 exact-head CI run `30537845010` 为 `completed / success / 10 of 10`。同一 Commit A 的两份 detached worktree 分别使用 PowerShell 5.1 / 7 构建，间隔 `35.582s`；manifest bytes、131 个 artifact 的 path/size/mode/SHA-256、canonical USTAR bytes 均完全一致。唯一正式新基线为 manifest SHA-256 `eaf83f95f51fc938d55c4c0235eee86e9de78c67990e142cf3d0b6c62c9e8977`、bundle SHA-256 `60a11dde87a4cbfcff8adbd32966b3dd28463d3399b8ba25db01eb836ed0ec1b`；tamper 精确返回 `BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH`。该 release 尚未上传、安装或部署。
- GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT：`DEPLOYMENT VERIFIED / CI GREEN / ATTEMPT-10 PREPARATION PENDING`（部署已验证 / release source CI 已通过 / 待 Attempt-10 准备）。Commit `c16f27c3c68d2484ad140d0557b879de08b7c78f` 的 canonical 131-artifact release 已上传、安装并于 Attempt-10 preparation attempt-01 中由 canonical installer 原子激活；bundle/manifest、root ownership、POSIX mode、worker write denial、trusted runtime path、systemd contract、offline remediation/security、fixture 与 tamper rejection均通过。Canonical `precreate-prerequisite` 随后返回 `readyForAttemptCreation=false`，因此 RunId/state/runtime/clock 均未创建，units started=`0`，OKX calls=`0`。
- GateW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION：`DEPLOYMENT VERIFICATION FAILED / CODE REMEDIATION REQUIRED`（部署验证失败 / 需要代码整改）。Commit A `1561eb60cd46dc1a4618fde6651426c41d7c4e20` 的 exact-head CI run `30559245227` 为 `completed / success / 10 of 10`；其 131-artifact immutable release 已通过双构建可复现、Linux root/POSIX/ownership、systemd 与离线回归验证，但生产 canonical `precreate-prerequisite` 仍返回 `INTERNAL_SANITIZED_READBACK_FAILURE`，无法建立 management/PostgreSQL readback。Attempt-10、RunId、state/runtime、clock 与 worker 均未创建或启动；服务器已通过 canonical action 回滚 current/unit links 到 `c16f27c3...`，新 release 保留但未运行。
- GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION：`REVIEW REJECTED / REMEDIATION REQUIRED`（审查已拒绝 / 需要整改）。固定 RC `5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6` 的 exact-head CI run `30576297678` 虽为 `completed / success / 10 of 10`，但独立 review 发现 4 个 P1：Java 21 source 与 release `javaMajor=17` 合同冲突、launcher 无进程级 timeout、固定 RC 无法重建声明的 manifest/bundle hashes、null/type mapping 未进入 `RESULT_MAPPING_FAILED`。另有 1 个 P2：JAR duplicate-entry policy 未形成显式 hard gate。本轮重建 manifest `5d946407...40c`、bundle `475a7037...3a`，与声明 `cbc2c0c4...bce7b` / `84446b2d...e952d3` 不同；RC 不得部署。
- GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION（attempt-02）：`REVIEW REJECTED / REMEDIATION REQUIRED`（审查已拒绝 / 需要整改）。整改 RC source `ef803568ed56905cb9969477e1ad777d5a01faf6` 的 exact-head CI run `30616271884` 为 `completed / success / 10 of 10`；Windows PowerShell 5.1/7 clean exact-build 的 manifest/bundle/artifact descriptors 与预期值一致，Linux non-Git regression 亦通过。但独立 attempt-02 review 发现 P1：release verifier 将带非空载荷的同名重复目录 entry 放行，且不读取 JAR entry 数据触发 CRC 校验。合成 duplicate-directory 与 stale-CRC probes 均错误返回 `PASS / IMMUTABLE_RELEASE_VERIFIED`；因此该 RC 不得部署，必须先在新 source commit 修复并重新审查。
- GateW-FREEZE：`NOT STARTED`（未开始）。GateW 尚未 archive、freeze 或 tag；Attempt-09 已拒绝。Attempt-10=`NOT_CREATED / NOT_AUTHORIZED`（未创建 / 未授权）；新 RC 的独立 review 与后续独立生产授权通过前不得进入部署或重试。
- GateW-3 dry-run order preview：只包含 OKX Spot、BUY/SELL、LIMIT、internal application、local persisted facts、read-only diagnostic；minimum notional、fee、远端 permission 与 runtime balance/risk 继续保持显式 UNKNOWN / NOT_EVALUATED，`executionReadiness=BLOCKED`，不得推导交易授权。
- GateW-3 read-only reconciliation：只包含 OKX Spot、最多 3 个 allowlisted symbols、1 page/100 records/24h typed private `Read` snapshot、bounded local SELECT 与 pure comparator；默认不装配，无 real smoke/credential/network/repair/persistence/scheduler，`executionReadiness=BLOCKED`。CI acceptance 只接受该 side-effect-free contract，不证明真实 permission 或账户健康。
- GateW-3 risk preflight：只消费 immutable preview/reconciliation result 与显式 local metadata snapshots；不调用 `PreTradeRiskService`/registry/stateful rules，不构造 `PlaceOrderCommand`，无 DB/network/write。minimum notional、fee、remote permission 保持 UNKNOWN，stateful risk/balance/position 等保持 NOT_EVALUATED，`executionReadiness=BLOCKED`、`tradingAuthorized=false`。

## 2. Archive Compatibility Verification

以下三项只供已冻结 archive checker 校验 GateV tag 事实，不属于 `nq-current-authority` schema，也不将 GateW 写成 tagged：

```text
current_gate_status=FROZEN|ACCEPTED|TAGGED
current_gate_tag=nq-gatev-freeze
updated_commit=530ce4e2bde416aa61944262cbfbadca556656cb
```

## 3. 安全与运行边界

- LIVE：`DISABLED`（关闭）。
- Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration runtime：`NOT STARTED`（未开始）。
- RealClient / private trading adapter：`NOT IMPLEMENTED`（未实现）；GateW-2 private read-only diagnostic transport/probe 为 `ACCEPTED / CI GREEN`，默认不装配且未做 real smoke，不属于交易适配器或交易授权。
- GateW runtime release：`c16f27c3c68d2484ad140d0557b879de08b7c78f`；该值是服务器已由 canonical installer 激活的 immutable runtime Commit A。Attempt-10 preparation attempt-01 的 governance/docs/evidence commit 不部署到服务器。
- Attempt-09：`REJECTED / FAILED_INSUFFICIENT_DURATION`（已拒绝 / 有效时长不足）。初始 MainPID=`4074358`；事件窗口内 systemd 明确执行 stop、另一次 start（PID=`301042`）和第二次 stop，最终 worker unit inactive、MainPID=`0`，continuity 不可恢复。终止分类=`OPERATOR_OR_AUTOMATION_STOP`，精确发起者=`UNKNOWN`；finalizer 分类=`FINALIZER_SYSTEMD_TIMEOUT`，`terminal-status.json=false`。
- Attempt-10：`NOT_CREATED / NOT_AUTHORIZED`（未创建 / 未授权）。旧 RC 已因 4 个 P1 与 1 个 P2 拒绝；整改 RC `ef803568...` 又因 JAR duplicate-entry/CRC P1 被独立 attempt-02 review 拒绝。本轮未连接或修改生产，没有 RunId、clock、unit 或 OKX call。不得直接重试、用新 Attempt/重置 clock/重跑 finalizer或其他服务器修改绕过 hard gate、RC review 与 Attempt-09 拒绝事实。
- Python ML readiness / Python live execution readiness：`NO`（否）。
- `acknowledge`、`escalate`、`resolve`、`close` 只表示本地人工诊断复核；不构成交易授权、LIVE/Shadow 放行，亦不批准下单、撤单、转账或提现。

## 4. 下一允许动作

治理 authority 中唯一下一动作精确为 `NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX`。该动作只允许最小修复 `ef803568...` review 发现的 `UNSAFE_DUPLICATE_JAR_ENTRY`：安全重复目录判定、逐 entry CRC/readback 与对应回归；修复必须产生新的固定 source commit 并重新独立审查。不得连接生产、部署 release、重试 Attempt-10、创建 RunId/clock/worker，亦不得修改 credential、permission、IP allowlist 或生产数据库；不授权扩大 OKX endpoint、LIVE、交易写侧或进入 freeze/archive/tag。
