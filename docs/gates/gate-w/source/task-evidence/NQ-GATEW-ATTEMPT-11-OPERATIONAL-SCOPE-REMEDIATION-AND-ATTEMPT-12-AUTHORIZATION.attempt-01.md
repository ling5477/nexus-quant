# GateW Attempt-11 operational scope 整改与 Attempt-12 授权证据

## Task classification

- 任务：`NQ-GATEW-ATTEMPT-11-OPERATIONAL-SCOPE-REMEDIATION-AND-ATTEMPT-12-AUTHORIZATION`。
- 类型：NQ-only / operational runtime scope remediation / governance authority sync。
- 目标：关闭 Attempt-11 在首 heartbeat 前暴露的 operational-scope P1，并只为全新 Attempt-12/RunId 建立 fail-closed 生产准备授权。

## Scope

- Runtime remediation：`scripts/gatew/gatew-okx-readonly-soak-control.ps1`、`scripts/gatew/install-gatew-release.ps1`。
- Permanent regression：`scripts/gatew/tests/run-gatew-soak-remediation-regression.ps1`。
- Governance：Attempt-12 exact next-action、runtime state/transition、cross-attempt/order/batch fail-close 与 current authority。
- 不包含生产 SSH、release 上传/安装/激活、systemd/current 变更、OKX 调用、credential material、Attempt-12/RunId 创建、worker 启动或 168h clock。

## Starting authority

- Authority sync 起始 `HEAD == origin/dev == eb51fe5b3ac50215fec404e76edd113439ff5ce1`，worktree/staged 均为空。
- 起始 accepted batch：`GateW-ATTEMPT-10-START-CONTRACT-REMEDIATION / ACCEPTED|CI_GREEN`。
- 起始 work batch：`GateW-ATTEMPT-11-PREPARATION-AND-START / BLOCKED`。
- Attempt-11 唯一 RunId `gatew-soak-20260801T125700Z-cb211abb` 已 terminalize；Attempt-10/11 与历史 evidence 均不可修改或复用。
- LIVE=`DISABLED`；kill switch=`ENGAGED`；Attempt-12、worker、heartbeat、hash chain 与 acceptance clock 均未创建或启动。

## Root cause 与 implementation

Attempt-11 的九项 safety flags 已冻结，但七项 operational runtime values 仍从执行 `prepare` 的 root Process environment 继承为空；Java prerequisite 因此在配置加载阶段退出，未产生 sanitized result。

整改将职责分为两类：

1. Control 固定四项字面量：
   - `SPRING_PROFILES_ACTIVE=gatew-okx-readonly-soak`
   - `NQ_GATEW_OKX_READONLY_SOAK_ENABLED=true`
   - `CI=false`
   - `NQ_NO_OUTBOUND=false`
2. Owner/account/currencies 不再读取 Process environment，只从 root-owned `gatew-precreate-prerequisite-v2` descriptor 读取。
3. Installer 从 `management.env` 读取并严格验证 owner/account 为 positive Int64；currencies 最多 3 个、canonical uppercase 且无重复。
4. Installer 仅允许通过 closed-schema 校验的 v1 descriptor 原子升级到 v2；control 直接拒绝 v1，保持 fail-close。
5. Pre-create Java 继续清空 scope 值，不扩大 credential、private endpoint 或 OKX 访问。

## Code commit and exact-head CI

- Runtime remediation commit：`eb51fe5b3ac50215fec404e76edd113439ff5ce1`。
- Message：`fix(gatew): freeze formal worker operational scope`。
- GitHub Actions：`NQ CI Baseline` run `30703645365`。
- 结果：`completed / success / 10 jobs / bad=0`，`headSha` 精确匹配。
- 本 authority-sync evidence 写入时，治理提交尚未创建，因此 authority-sync exact-head CI=`NOT_RUN`；提交推送后必须取得 10/10 GREEN 才允许连接生产。

## Runtime validation

| 验证 | 结果 |
| --- | --- |
| control self-test，PowerShell 5.1 / 7 | PASS；各 81 cases |
| installer self-test，PowerShell 5.1 / 7 | PASS |
| worker self-test，PowerShell 5.1 / 7 | PASS；各 59 cases |
| fail-close self-test，PowerShell 5.1 / 7 | PASS；各 8 cases |
| remediation regression，PowerShell 5.1 / 7 | PASS；各 36 cases |
| security regression，PowerShell 5.1 / 7 | PASS；各 12 cases |
| release reproducibility regression，PowerShell 5.1 / 7 | PASS；各 34 cases |
| builder self-test，PowerShell 5.1 / 7 | PASS |
| GateW PowerShell AST | PASS；12 files / 0 errors，双引擎 |
| focused Maven | PASS；50 tests / 0 failures / 0 errors / 1 skipped |
| wider focused Maven | PASS；72 tests |
| canonical offline Maven package | PASS；23 modules |
| IDEA problems / `git diff --check` | PASS；3 个 runtime 修改文件 0 errors/warnings |

## Governance contract

- 新增 Attempt-12 preparation/running/blocked exact triples。
- 新增独立 `attempt12Runtime`；Attempt 创建事件必须精确为 `ATTEMPT_12_CREATED`。
- 只允许以下 authority transitions：
  - Attempt-11 `BLOCKED` → Attempt-12 `ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED`；
  - Attempt-12 authorized → `RUNNING|PENDING_168H`；
  - Attempt-12 authorized → same-batch `BLOCKED` / `STARTUP_FAILED`。
- Wrong attempt event、wrong work batch、缺事件、错 ordinal、LIVE enabled、kill switch disengaged 与 non-exact-head CI evidence 均永久 fail-closed。
- Contract schema 保持 `1.3.0`，authority schema 保持 `3`。

## Validation RCA

- 初次 installer patch 曾把 legacy/current descriptor validator 语义对调；完成 RCA 与最小修复后，双引擎 installer/remediation 全部重跑通过。
- 首次 GateW AST 嵌套 `-Command` 因外层变量展开失败，AST 未启动；改用 UTF-16LE `-EncodedCommand` 后得到双引擎 `12/0`。
- 首次 focused Maven 运行了较宽 72-test 范围；随后按既有精确 50-test 基线重跑通过。两组结果分别记录，不互相替代。
- 本次 authority sync 的首次 link checker 调用遗漏 mandatory `-Roots`，exit 1 且未开始扫描；必须以 `-Roots docs/current` 正确重跑，不把调用错误记为首轮通过。
- Formatter 回滚后 current Markdown 使用 CRLF，cross-document ROADMAP fixture 的行尾正则只接受 LF，导致 PS5.1/PS7 均返回 `CROSS_DOCUMENT_ROADMAP_FIXTURE_INVALID`；仅将 fixture 行尾修正为显式兼容 LF/CRLF 后双引擎重跑通过，未修改 authority/checker 语义。

## Authority after

- Accepted batch：`GateW-ATTEMPT-11-OPERATIONAL-SCOPE-REMEDIATION / ACCEPTED|CI_GREEN`。
- Work batch：`GateW-ATTEMPT-12-PREPARATION-AND-START / ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED`。
- Attempt-10/11：继续 `FAILED / STOPPED`，历史 evidence 与 RunId 不变。
- Attempt-12：`NOT_CREATED / AUTHORIZED`，production deployment=`NOT_STARTED`。
- 唯一下一动作：`NQ-GATEW-ATTEMPT-12-PREPARATION-AND-START`。

## Boundary confirmation

- Production SSH/deployment/systemd/current/DB read-write/OKX call=`0`。
- Credential material/raw private response exposure=`0`。
- Order/cancel/transfer/withdraw call=`0`。
- LIVE=`DISABLED`；kill switch=`ENGAGED`。
- 未进入 freeze/archive/tag；未修改 Attempt-10/11 或历史 evidence。

## Findings and decision

- P0：0。
- P1：0；Attempt-11 operational-scope P1 已由 exact-head CI green remediation 关闭。
- P2：Attempt-11 记录的 SSH/SCP reset/timeout 与脱离 systemd 上下文的手工 `unit-preflight` 可诊断性继续作为已知 residual；不允许为绕过它们而放宽 hard gate。
- P3：生产 journal 必须继续使用精确 unit/time/error-code selector，禁止 broad grep。

Final decision：`PASS / OPERATIONAL_SCOPE_REMEDIATED / DESCRIPTOR_V2_FROZEN / RUNTIME_EXACT_HEAD_CI_GREEN / ATTEMPT_10_11_IMMUTABLE / ATTEMPT_12_AUTHORIZED / PRODUCTION_NOT_ACCESSED / LIVE_DISABLED / KILL_SWITCH_ENGAGED`。

下一步只能从 clean exact authority commit 构建并验证新的 immutable release，再执行 production preflight。任一 hard gate 失败必须停止且不得创建 Attempt-12；Attempt 创建后首次验证失败必须 fail-close、terminalize，并为任何后续重试重新授权全新 Attempt/RunId 与完整 168h。
