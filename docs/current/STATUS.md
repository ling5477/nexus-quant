# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateV
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatev-freeze
last_frozen_gate_commit=530ce4e2bde416aa61944262cbfbadca556656cb
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateW-ATTEMPT-12-PREREQUISITE-SCHEMA-REMEDIATION
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=e8c334886ae6614133b0bf3f0083bc1893a11e01
accepted_batch_acceptance_head=e8c334886ae6614133b0bf3f0083bc1893a11e01
accepted_batch_ci_run=30709995836
work_batch=GateW-OKX-READONLY-SOAK-ATTEMPT-13
work_batch_status=RUNNING|PENDING_168H
work_batch_commit=e8c334886ae6614133b0bf3f0083bc1893a11e01
work_batch_ci_run=30709995836
next_action=NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE
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
- GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX：attempt-02 为 `IMPLEMENTED / CI GREEN / RC REVIEW PENDING`（已实现 / CI 已通过 / 待 RC 审查）。Commit A `5a7e824e7e3edc470c55614523a12a2a84286856` 的 exact-head CI run `30632959743` 为 `completed / success / 10 of 10`。Verifier 现对每个 JAR entry 以固定 64 KiB buffer 读取到 EOF、独立计算并比对 CRC32，并以固定 contract 限制 entry 数、单 entry 与单 JAR 总解压量；34-case regression 永久覆盖非空重复目录、stale CRC、截断/非法压缩流、duplicate/path collision 与资源上限。Windows PowerShell 5.1/7 与 no-egress Linux exact builds 的 manifest `d82ae4fc453b3fbf8ed2d0e8ce3767c1d280a615d596f2bdf8f82eacb35a30c6`、bundle `9feda6a825af58d45c61572a4fc590f7ad231b80c45243562cf68390fa68add0` 及 131-artifact descriptors 完全一致；122 JAR 的 37,551 entries / 133,989,252 bytes 已全量读取，4 个合法空目录 duplicate 允许并计数。该新 RC 仅可进入独立审查，尚未接受或部署。
- GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW：attempt-03 为 `ACCEPTED / CI GREEN / DEPLOYMENT AUTHORIZED`（已接受 / CI 已通过 / 已授权进入受控部署准备）。Review/remediation commit `15ee2ee2774019f9abf4b238f989b4c7b30db04c` 的 exact-head CI run `30653141014` 为 `completed / success / 10 jobs / bad=0`。真实 RC 的 Windows/Linux exact build、122/122 JAR full-stream/CRC、duplicate/resource contract、tamper 与 focused Maven 均通过；fixture timestamp P1 经授权最小修复后三平台各连续 3 次 34/34。P0=0/P1=0/P2=0/P3=0；runtime RC source 仍为 `5a7e824e...`，本轮生产访问为 0。
- GateW-ATTEMPT-10-PREPARATION-AND-START：attempt-02 为 `BLOCKED / ATTEMPT CREATED / START CONTRACT FAILED / TERMINALIZED / ROLLED BACK`（阻断 / Attempt 已创建 / 启动合同失败 / 已终态化 / 已回滚）。Final release `f06a38f2...` 的 exact-head CI run `30694580482` 10/10 success，双引擎 exact build、服务器 immutable/root/POSIX verifier 与 persisted permission pre-create 均通过；唯一 RunId `gatew-soak-20260801T102353Z-932e26a4` 创建后发现九个 safety flags 为空而非精确 `false`。Worker、OKX、首 heartbeat 与 clock 均未启动；run 已 fail-close，current/unit links 已恢复 `c16f27c3...`。P0=0/P1=1/P2=2/P3=1。
- GateW-ATTEMPT-10-START-CONTRACT-REMEDIATION：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Commit `aeacfebd688c6329368d4e43140043fbf9688103` 将正式 REAL worker 的九项 safety flags 在 run 创建前固定为字面量 `false`，并使 pre-create Java 与 worker 共用同一 fail-closed helper；exact-head CI run `30697734316` 为 `completed / success / 10 jobs / bad=0`。双引擎 control `76`、remediation `35`、security `12`、worker `59`、fail-close `8`、release reproducibility `34` 及 focused Maven `50` 均通过。
- GateW-ATTEMPT-11-PREPARATION-AND-START：`BLOCKED / STARTUP FAILED / TERMINALIZED / ROLLED BACK`（阻断 / 启动失败 / 已终态化 / 已回滚）。Commit `bfc68b89e81213ad2b240bf26b4118676abfd75e` 的 exact-head CI run `30698530051` 为 `completed / success / 10 jobs / bad=0`；immutable release、生产 preflight 与 persisted permission fact 均通过，但唯一 worker 在首条有效 heartbeat 前因 operational runtime values 未冻结而退出。Attempt-11 已 fail-close，禁止修改、复用或自动重试。
- GateW-ATTEMPT-11-OPERATIONAL-SCOPE-REMEDIATION：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Commit `eb51fe5b3ac50215fec404e76edd113439ff5ce1` 冻结正式 worker 的四项 control-owned operational switches，并只从 root-owned `gatew-precreate-prerequisite-v2` 读取 owner/account/currencies；exact-head CI run `30703645365` 为 `completed / success / 10 jobs / bad=0`。Attempt-10/11 与历史 RunId 均未修改。
- GateW-ATTEMPT-12-PREPARATION-AND-START：`BLOCKED / STARTUP FAILED / TERMINALIZED / ROLLED BACK`（阻断 / 启动失败 / 已终态化 / 已回滚）。Release/source `d45fa921eccfe56e4c107037818749b971e28317` 的 exact-head CI run `30705301218` 为 `completed / success / 10 jobs / bad=0`；唯一 RunId `gatew-soak-20260801T164322Z-79ed8c0b`、worker PID `470754` 在首 heartbeat 前因 `prerequisite readback schema is invalid` 退出，lifecycle=`FAILURE_STOPPED`、exit=`exited/2`、samples/failures=`0/0`，acceptance clock 未启动。Current/unit links 已回滚到 `c16f27c3...`，Attempt-12 与 RunId 禁止复用。
- GateW-ATTEMPT-12-PREREQUISITE-SCHEMA-REMEDIATION：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Commit `e8c334886ae6614133b0bf3f0083bc1893a11e01` 将 worker 的 exact schema 校验与 Java `PrerequisiteMain` 的 23-field sanitized contract 对齐；exact-head CI run `30709995836` 为 `completed / success / 10 jobs / bad=0`。修复只发生在 credential/network/OKX 调用前的本地 readback contract，不扩大 endpoint、权限或运行范围。
- GateW-OKX-READONLY-SOAK-ATTEMPT-13：`RUNNING / PENDING 168H`（运行中 / 待满 168 小时）；Attempt-13=`RUNNING / PENDING_168H`; production deployment=`STARTED`。启动任务 `NQ-GATEW-ATTEMPT-13-PREPARATION-AND-START` 已 `PASS / STARTUP COMPLETE`；release/source=`b103069d8bfcecccba0b4d590317ddccc66898b9`，起始 exact-head CI run `30710943874 / completed / success / 10 jobs / bad=0`；唯一 RunId=`gatew-soak-20260801T180544Z-140bbcd1`，worker PID=`478613`，unit=`active/running`，`NRestarts=0`。首条有效 heartbeat/hash-chain、fresh-SSH 与 acceptance clock 均已验证；`acceptanceStartAt=2026-08-01T18:13:13.9139125Z`，`plannedAcceptanceAt=2026-08-08T18:13:13.9139125Z`。本启动任务已结束，不承担连续在线观察或期满验收。
- GateW-FREEZE：`NOT STARTED`（未开始）。GateW 尚未 archive、freeze 或 tag；Attempt-09 已拒绝，Attempt-10/11/12 均失败并已终态化，Attempt-13 正在自动采证且尚未完成 168h 验收；不得提前进入 freeze/archive/tag。
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
- GateW runtime release：`b103069d8bfcecccba0b4d590317ddccc66898b9`；服务器 `/opt/nexus-quant/current` 已原子指向该 root-owned immutable release，Attempt-13 worker 正以唯一 PID `478613` 运行。`c16f27c3...` 保留为上一 last-known-good rollback release；Attempt-10/11/12 失败 release 与 evidence 均保留。
- Attempt-09：`REJECTED / FAILED_INSUFFICIENT_DURATION`（已拒绝 / 有效时长不足）。初始 MainPID=`4074358`；事件窗口内 systemd 明确执行 stop、另一次 start（PID=`301042`）和第二次 stop，最终 worker unit inactive、MainPID=`0`，continuity 不可恢复。终止分类=`OPERATOR_OR_AUTOMATION_STOP`，精确发起者=`UNKNOWN`；finalizer 分类=`FINALIZER_SYSTEMD_TIMEOUT`，`terminal-status.json=false`。
- Attempt-10：`FAILED / STOPPED`（失败 / 已停止）；production deployment=`STOPPED`。唯一 RunId=`gatew-soak-20260801T102353Z-932e26a4` 已 terminalize；worker 实际从未启动，MainPID=`0`、NRestarts=`0`、residual=`0`，first heartbeat/hash chain/acceptance clock 均不存在，OKX calls=`0`。Kill switch=`ENGAGED`、RunId reuse=`FORBIDDEN`、auto retry=`DISABLED`、LIVE=`DISABLED`；禁止就地修改或复用该失败 run，Attempt-11 必须使用独立新 RunId。
- Attempt-11：`FAILED / STOPPED`（失败 / 已停止）；production deployment=`STOPPED`。唯一 RunId=`gatew-soak-20260801T125700Z-cb211abb` 已 terminalize 为 `FAILURE_STOPPED / WORKER_EXIT_WITHOUT_EXPLICIT_ACCEPTANCE`；worker MainPID=`456996`、NRestarts=`0`、exit=`exited/2`，首 heartbeat、unit-start snapshot、hash-chain 起点与 acceptance clock 均不存在，samples/failures=`0/0`。Credential/network/OKX calls=`0/0/0`；kill switch=`ENGAGED`、LIVE=`DISABLED`、RunId reuse=`FORBIDDEN`、auto retry=`DISABLED`。
- Attempt-12：`FAILED / STOPPED`（失败 / 已停止）；production deployment=`STOPPED`。唯一 RunId=`gatew-soak-20260801T164322Z-79ed8c0b` 已 terminalize；worker MainPID=`470754`、exit=`exited/2`，首 heartbeat/hash chain/acceptance clock 均不存在，samples/failures=`0/0`。Credential/network/OKX calls=`0/0/0`；kill switch=`ENGAGED`、LIVE=`DISABLED`、RunId reuse=`FORBIDDEN`、auto retry=`DISABLED`。
- Attempt-13 runtime：`SOAK_RUNNING`（soak 运行中）。唯一 RunId=`gatew-soak-20260801T180544Z-140bbcd1`；unit=`nq-gatew-soak@gatew-soak-20260801T180544Z-140bbcd1.service`，MainPID/initial MainPID=`478613/478613`，`NRestarts=0`，residual process count=`1`（唯一正式 worker）。首样本 count=`1`，hash-chain=`PASS / HASH_CHAIN_VERIFIED`，forbidden/fallback/raw/secret=`0/0/0/0`；acceptance clock=`STARTED`。LIVE=`DISABLED`、kill switch=`ENGAGED`、RunId reuse=`FORBIDDEN`、auto retry=`DISABLED`。
- Python ML readiness / Python live execution readiness：`NO`（否）。
- `acknowledge`、`escalate`、`resolve`、`close` 只表示本地人工诊断复核；不构成交易授权、LIVE/Shadow 放行，亦不批准下单、撤单、转账或提现。

## 4. 下一允许动作

治理 authority 中唯一下一动作精确为 `NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE`。它只能在 `plannedAcceptanceAt=2026-08-08T18:13:13.9139125Z` 到达后由独立任务执行；期间由现有 worker/systemd 自动采证，不要求 Codex 或人工连续在线观察。到期前不得运行 acceptance/finalize，不得把 `RUNNING|PENDING_168H` 写成 accepted/completed/frozen。该边界不允许重放 `start`、修改/复用 Attempt-10/11/12、开启 LIVE、下单、撤单、转账、提现或进入 freeze/archive/tag。
