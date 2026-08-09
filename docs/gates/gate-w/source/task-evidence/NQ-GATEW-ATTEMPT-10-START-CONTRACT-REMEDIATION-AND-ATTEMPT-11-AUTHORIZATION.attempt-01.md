# GateW Attempt-10 启动合同整改与 Attempt-11 授权证据

## Task classification

- 任务：`NQ-GATEW-ATTEMPT-10-START-CONTRACT-REMEDIATION-AND-ATTEMPT-11-AUTHORIZATION`。
- 类型：NQ-only / production read-only start contract remediation / governance authority sync。
- 目标：关闭 Attempt-10 暴露的 pre-start safety flag P1，并只为全新 Attempt-11/RunId 建立受控生产准备授权。

## Scope

- Runtime remediation：`scripts/gatew/gatew-okx-readonly-soak-control.ps1`。
- Permanent regression：`scripts/gatew/tests/run-gatew-soak-remediation-regression.ps1`。
- Governance：Attempt-11 exact next-action、runtime state/transition、cross-attempt fail-closed 与 current authority。
- 不包含生产 SSH、release 上传/安装/激活、systemd 变更、OKX 调用、credential material、Attempt 创建、worker 启动或 168h clock。

## Starting authority

- 起始 `HEAD == origin/dev == ab5be223f45daaf71d7c8a0f9cd1e57d77d5d267`。
- 起始 work batch：`GateW-ATTEMPT-10-PREPARATION-AND-START / BLOCKED`。
- 失败 RunId：`gatew-soak-20260801T102353Z-932e26a4`；已 terminalize，禁止修改或复用。
- LIVE=`DISABLED`；kill switch=`ENGAGED`；worker/heartbeat/hash chain/acceptance clock 均未启动。

## Implementation

1. 新增单一 `Get-FormalRealSafetyEnvironmentValues` helper，九项 safety flags 固定为大小写精确的字面量 `false`。
2. Pre-create Java 环境与正式 worker environment 共用该 helper；worker 不再从可污染的 Process scope 继承九项开关。
3. Self-test 先把九项 Process env 临时设为 `true`，确认 frozen worker values 仍全部为 `false`，并在 `finally` 恢复原环境。
4. 永久 remediation regression 新增 case 35，约束 safety helper、worker helper 与 `Prepare-FormalRun` 的调用关系。
5. Acceptance clock 继续精确以 first valid heartbeat 为起点；为 Windows PowerShell 5.1 兼容改用 ASCII 合同注释和显式 `$plannedAcceptance` 变量，语义不变。

## Code commit and exact-head CI

- Commit：`aeacfebd688c6329368d4e43140043fbf9688103`。
- Message：`fix(gatew): freeze formal worker safety flags`。
- GitHub Actions：`NQ CI Baseline` run `30697734316`。
- 结果：`completed / success / 10 jobs / bad=0`，`headSha` 精确匹配 commit。

## Validation

| 验证 | 结果 |
| --- | --- |
| control self-test，PowerShell 5.1 / 7 | PASS；各 76 cases |
| worker self-test，PowerShell 5.1 / 7 | PASS；各 59 cases |
| fail-close self-test，PowerShell 5.1 / 7 | PASS；各 8 cases |
| remediation regression，PowerShell 5.1 / 7 | PASS；各 35 cases |
| security regression，PowerShell 5.1 / 7 | PASS；各 12 cases |
| release reproducibility regression，PowerShell 5.1 / 7 | PASS；各 34 cases |
| builder / installer self-test，PowerShell 5.1 / 7 | PASS |
| GateW PowerShell AST | 12 files / 0 errors，双引擎 PASS |
| focused Maven | 50 tests / 0 failures / 0 errors / 1 skipped |
| canonical offline Maven package | PASS |
| exact-head CI | 10 jobs / bad=0 |

本地 `mvn --offline -f backend/pom.xml test` 首次因 `localhost:5432` 未运行而有 3 个 `nq-app` local integration context errors；不是代码断言失败。Exact-head CI 的 Backend Maven 与 PostgreSQL/Flyway jobs 均在受控 PostgreSQL service 下通过。AST 聚合命令、focused Maven 参数与 doc-link 命令各有一次调用封装错误，均保留 RCA 并以正确参数重跑；不得记为首轮通过。

## Governance decision

- Accepted batch：`GateW-ATTEMPT-10-START-CONTRACT-REMEDIATION / ACCEPTED / CI GREEN`。
- Work batch：`GateW-ATTEMPT-11-PREPARATION-AND-START / ACCEPTED / CI GREEN / DEPLOYMENT AUTHORIZED`。
- Attempt-10：继续 `FAILED / STOPPED`，历史 evidence 与 RunId 不变。
- Attempt-11：`NOT_CREATED / AUTHORIZED`，production deployment=`NOT_STARTED`。
- 唯一下一动作：`NQ-GATEW-ATTEMPT-11-PREPARATION-AND-START`。

Attempt-11 contract 使用独立 `attempt11Runtime`，创建事件必须是 `ATTEMPT_11_CREATED`；Attempt-10 event、错误 ordinal、错误 work batch、不完整事件序列、LIVE enabled 或 kill switch disengaged 均 fail-closed。

## Boundary confirmation

- Production SSH/deployment/systemd/current/DB write/OKX call=`0`。
- Credential material/raw private response exposure=`0`。
- order/cancel/transfer/withdraw call=`0`。
- LIVE=`DISABLED`；kill switch=`ENGAGED`。
- 未进入 freeze/archive/tag；未修改 Attempt-10 或历史 evidence。

## Findings and decision

- P0：0。
- P1：0；pre-start safety flag P1 已关闭。
- P2：Attempt-10 历史 taxonomy/no-exit attribution 保留为已终态 historical residual，不允许就地修补；不阻断全新 Attempt-11 的 fail-closed 路线。
- P3：0。

Final decision：`PASS / START_CONTRACT_REMEDIATED / EXACT_HEAD_CI_GREEN / ATTEMPT_10_IMMUTABLE / ATTEMPT_11_AUTHORIZED / PRODUCTION_NOT_ACCESSED / LIVE_DISABLED / KILL_SWITCH_ENGAGED`。

下一步只能从 clean exact commit 构建、验证新的 immutable release，再执行 production preflight。任一 hard gate 失败必须停止，不创建或不启动 Attempt-11，并保留证据。
