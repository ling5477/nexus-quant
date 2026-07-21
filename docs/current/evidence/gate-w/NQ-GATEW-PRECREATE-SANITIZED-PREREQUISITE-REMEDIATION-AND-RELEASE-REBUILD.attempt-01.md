# NQ-GATEW-PRECREATE-SANITIZED-PREREQUISITE-REMEDIATION-AND-RELEASE-REBUILD — Attempt 01

## Task classification

- 主类型：`GATEW_TOOLING_REMEDIATION`；辅助类型：
  `PRECREATE_SECURITY_GATE / SANITIZED_DB_READBACK / IMMUTABLE_RELEASE_REBUILD /
  FULL_FORMAL_OFFLINE_ACCEPTANCE / COMMIT_AND_EXACT_HEAD_CI`。
- 归属：NQ-only、L 级高风险工具链任务；GateW 保持 `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- 本记录当前覆盖实现、本地回归与 candidate release/离线验收；Commit A、exact-head CI、final release、服务器
  `current` 切换、final offline acceptance 与 Commit B 尚未执行，不提前写成通过。

## Scope and boundaries

- 只修改 GateW control/installer helper 与既有 GateW test-support，建立 REAL run 创建前的独立 sanitized prerequisite。
- 不修改 migration、交易主链、RiskGate、ledger、Controller/API、frontend、research、`.github` 或 systemd unit 源文件。
- 不创建 Attempt-09/10，不启动真实 soak/真实 acceptance clock，不调用 OKX，不读取 exchange credential material，
  不执行 freeze/archive/tag。

## Starting baseline and RCA

- Branch/HEAD/origin：`dev / bacd4752781b73e5eebaca171f7047da69bc9b8d`，`HEAD == origin/dev`。
- Exact-head CI：`29782483798 / completed / success / 10 of 10`。
- Server current：`0e8e2c128c456542b3f7695c9620e4d170c3f4f6`；初始 active units/MainPID/residual/drop-ins、
  REAL runs、Attempt-09 matches 均为 0。
- RCA：旧顺序为 `prepare 创建 run/state/evidence -> systemd worker -> prerequisite`，无法在任何 run/runtime/unit/clock
  side effect 前完成 gate；DB URL/user 还依赖 operator 手工展开。

## Implementation

### Pre-create action and execution order

- control helper 新增独立 `precreate-prerequisite` action，无需 runId；固定失败 exit=`2`，成功 exit=`0`。
- REAL `prepare` 在 `Get-ReleaseIdentity`、`New-RunId` 和任何 `Ensure-Directory` 前执行同一 prerequisite；失败返回
  `BLOCKED / PRECREATE_PREREQUISITE_REQUIRED`，不创建 run。
- REAL prepare 拒绝 operator 提供 DB URL/user/password source，返回
  `BLOCKED / REAL_DATABASE_OPERATOR_INPUT_FORBIDDEN`。
- prerequisite 与后续 prepare 复用同一 descriptor 快照，避免 PASS 后二次读取的配置切换窗口。

### Normalized DB input

- installer 新增 `configure-precreate`，只解析固定 management authority 引用链并原子生成：
  `/etc/nexus-quant/gatew-soak/precreate-prerequisite.json`。
- descriptor 为 root:root/0600、固定九字段、未知字段拒绝，不含密码；secret reference 固定为 root-owned/0600
  encrypted systemd credential 文件。
- 拒绝未展开 `${...}`、command substitution、反引号、管道/重定向、路径越界、symlink、owner/mode 异常和
  operator 环境覆盖。
- `systemd-creds decrypt` 使用固定 `--name=db-password --newline=no`；密码不进入 argv、日志、JSON 或 evidence。

### Sanitized readback

- 复用既有 GateW Java test-support launcher，仅执行本地 PostgreSQL SELECT 与 loopback management health；显式设置
  connect/socket/login/query timeout。
- 全局选择唯一 active `OKX / LIVE / OKX_API_V5` credential metadata，不读取 master key、encrypted/decrypted payload、
  API key、secret 或 passphrase，不需要 account/owner 输入。
- action 只输出固定 result schema；临时结果仅写 `/run/nq-gatew-precreate-prerequisite-<32hex>.json`，owner-only，
  调用后删除。

## Files changed before Commit A

- `scripts/gatew/gatew-okx-readonly-soak-control.ps1`
- `scripts/gatew/install-gatew-release.ps1`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakSupportTest.java`
- 本文件、Attempt-09 blocked attempt-02 与 `docs/current/evidence/gate-w/README.md`

## Local validation

| Command / evidence | Result | Scope |
| --- | --- | --- |
| `GateWOkxReadonlySoak*Test` targeted | PASS | 60 tests、0 failures/errors、2 个既有 annotation skip |
| `GateWOkxReadonlySoakSupportTest` | PASS | 46 tests、0 failures/errors |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app -am test` | PASS | 23/23 reactor modules success；`nq-app` 220 tests、0 failures/errors、9 skipped |
| `mvn -f backend/pom.xml test` | PASS | 全 backend reactor success；`nq-app` 220 tests、0 failures/errors、9 skipped |
| PowerShell 7 / 5.1 control self-test | PASS | 49 cases；pre-create 位于 runId/directory 前，闭合 schema |
| PowerShell 7 / 5.1 worker/fail-close | PASS | 59 / 41 cases；hash、offline fixture、独立 fail-close |
| PowerShell 7 / 5.1 builder/installer | PASS | immutable builder 与 fixed descriptor reference chain |
| Local verifier | PASS | PowerShell 7/5.1，129 artifacts |
| Manifest tamper | PASS | artifact 修改后 exit=`2 / RELEASE_ARTIFACT_HASH_MISMATCH` |
| `git diff --check` | PASS | 无 whitespace error；仅 Git 行尾提示 |

首次全 Maven 在本地 compose fresh DB 因缺少既有 `accounts` test fixture 失败；RCA 与仓库既有 CI 记录一致。仅插入
CI 同款 `PAPER / ACTIVE` fixture 后两条要求命令通过，随后已精确删除该 fixture；未清库或删除 volume。

## Candidate release

- Accepted candidate：`candidate-bacd4752781b-7ce93fdca1127bb3-20260721T133556Z`。
- Candidate diff SHA-256：`7ce93fdca1127bb3cb9142a1d1d44c6af5692571403aa677586f28b92e54c033`。
- Manifest SHA-256：`a8014a6e9dc4f411f72c423c44747bbf64968381b7bf2b4ca46b10815cfe2206`；artifacts=`129`。
- Bundle tar：size=`49,694,634` bytes，SHA-256=
  `d1b1f6af0ba4ed3c4542cfb21ecf262426cf8649e0a6aa0f0a61824ae51cd11d`，paths=`136`，unsafe paths=`0`。
- Server install：root-owned、`nqgatewWritable=false`、systemd verify exit=`0`；candidate 未切换
  `/opt/nexus-quant/current`。验收后 unit links 已恢复到 current release `0e8e2c12...`，active units=`0`。
- 首个 candidate 暴露目标机 `systemd-creds` credential-name 推断差异并 fail-closed；修复为固定
  `--name=db-password` 后重建上述 accepted candidate。失败 candidate 未激活、未提交。

## Candidate pre-create verification

- 正式 action：exit=`0`，`postgresReachable/managementHealthy/killSwitchEngaged/credentialConfigured=true`，
  `activeCredentialCount=1`，type=`OKX_API_V5`，local status=`ACTIVE`，trade/withdraw expected-disabled=`true`，
  `readyForAttemptCreation=true`，`credentialMaterialExposed=false`。
- 执行前后历史 run count `25 -> 25`，Attempt-09 matches `0 -> 0`，临时 result `0 -> 0`，active units=`0`。
- mode、owner、unknown field、DB unavailable 四类 tamper 均 exit=`2` 且 `readyForAttemptCreation=false`；descriptor
  精确恢复后 action 再次 PASS。
- descriptor 无效时 REAL prepare 在 run 创建前拒绝；operator DB override 同样拒绝，run count 不变。
- kill switch 非 `ENGAGED` 由 Java regression 与隔离 offline cycle/fail-close 覆盖；没有为了测试修改真实 kill switch。

## Candidate formal offline acceptance

- 明确验收 run：`gatew-soak-20260721T135315Z-90c2e510`，mode=`OFFLINE_ISOLATED_ACCEPTANCE`，不是 Attempt-09。
- prepare PASS；cycle 1/2 PASS；MainPID=`4038391`；fresh SSH 证明同 PID、heartbeat advanced。
- clock 首次 `PASS / ACCEPTANCE_CLOCK_STARTED`，start=`2026-07-21T13:55:35.3388794Z`；planned 精确 `+168h`；
  第二次 `NO_CHANGE / ACCEPTANCE_CLOCK_ALREADY_STARTED`。
- cycle 3=`CONTROLLED_FAILURE`；OnFailure terminal=`FAILURE_STOPPED`；独立 finalizer 重入=
  `NO_CHANGE / TERMINAL_ALREADY_EXISTS`；kill switch recovery=`ENGAGE_SUCCEEDED`，observed=`ENGAGED`。
- Final verify：`PASS / FORMAL_SOAK_VERIFIED`；sample count=`3`，hash chain PASS，historical evidence immutable=true，
  credential/network=`false/false`，MainPID/residual=`0/0`，runtime absent，offline drop-ins=`0`。
- candidate 收尾 pre-create 再次 PASS；active units/nonzero MainPID/drop-ins/Attempt-09/REAL runs/REAL clocks 均为 0。

## Pending Commit A and final stages

- Implementation commit：`UNCOMMITTED`；Commit A exact-head CI：`NOT_RUN`。
- Final EXACT_COMMIT release、服务器 `current` 切换、final pre-create、final formal offline acceptance：`NOT_RUN`。
- Evidence/authority Commit B 与 exact-head CI：`NOT_RUN`。

## Findings

- P0：无。
- P1：无；candidate 已关闭 pre-create 顺序、自包含 DB 输入、脱敏 readback、secret reader compatibility 与 fail-closed
  hard gates。
- P2：SSH 长命令需 `ServerAliveInterval` 才能稳定保留超过 60 秒的 stdout；不影响 systemd worker 独立运行。
- P3：无。

## Boundary confirmation

- 未调用 OKX/其他交易所；未读取、解密或输出 exchange credential material；DB password 仅由 root helper 从固定 encrypted
  systemd credential 注入短生命周期 Java process，未输出。
- 未创建 Attempt-09/10，未启动真实 soak/真实 acceptance clock；candidate clocks 仅属于已终止的隔离 offline run。
- 未启用 LIVE、交易写、AI、DH runtime、real provider/client；未修改 migration、frontend、research、`.github`。

## Current decision

`PASS / PRECREATE_SANITIZED_PREREQUISITE_PROVEN / SELF_CONTAINED_DB_INPUT_PROVEN /
CANDIDATE_FULL_FORMAL_OFFLINE_ACCEPTANCE_PROVEN / READY_TO_COMMIT_A / ATTEMPT_09_NOT_CREATED /
ACCEPTANCE_CLOCK_NOT_STARTED`。
