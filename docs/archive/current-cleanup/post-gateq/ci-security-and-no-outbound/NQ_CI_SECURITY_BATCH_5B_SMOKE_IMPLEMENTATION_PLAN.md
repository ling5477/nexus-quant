# NQ CI Security Batch 5B Smoke Implementation Plan

任务：NQ-CI-SECURITY-BATCH-5B-SMOKE-IMPLEMENTATION-PLAN
日期：2026-06-21
状态：**IMPLEMENTATION PLAN READY / READY FOR REVIEW**；**Batch 5B-ENV = FROZEN / ACCEPTED**；**Batch 5B-SMOKE-PREFLIGHT = REVIEWED / ACCEPTED**；**Batch 5B-SMOKE implementation = NOT STARTED**。

本文件只定稿下一轮 Batch 5B-SMOKE implementation 的最小计划、允许文件、禁止范围、验收命令和回滚边界。本轮不执行 implementation，不新增 CI job，不新增测试，不修改 workflow / backend / migration / frontend / research / scripts / deploy / .env.example，不运行或触发 GitHub Actions。

## 1. Current state

| Item | State |
| --- | --- |
| Batch 5B-ENV | **FROZEN / ACCEPTED**（freeze 卷宗 NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md；evidence run 27876451289 / headSha 8ba140d9 / 8 jobs success） |
| Batch 5B-SMOKE-PREFLIGHT | **REVIEWED / ACCEPTED**（planning source NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md） |
| Batch 5B-SMOKE implementation | **NOT STARTED** |
| 本轮计划状态 | **IMPLEMENTATION PLAN READY / READY FOR REVIEW** |

边界确认：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe；NoReal permission probe remains SKIPPED。

## 2. CI job name decision

选定下一轮 implementation job 名称：ci-security-smoke。

理由：

1. ci-security-smoke 明确表达该 job 是 CI 安全边界 smoke，覆盖 no-real / no-outbound / fail-closed / placeholder credential / forbidden env-name 断言。
2. env-smoke 过宽，容易被误读为仅验证 env profile 或泛化启动 smoke，不能充分表达 no-real credential、no-outbound guard、NoReal permission probe 的安全目标。
3. ci-security-smoke 不暗示真实交易所、LIVE、Paper trading runtime、DH runtime 或 AI runtime 已启动。

P3 job name drift 已关闭：后续 implementation 必须使用 ci-security-smoke，不得再在 ci-security-smoke / env-smoke 之间摇摆。

## 3. Minimal smoke scope

下一轮 implementation 的最小 smoke 范围限定为：

1. CI safety boundary：CI job 只注入非敏感控制变量与 placeholder-only 值，不注入真实 secret 或交易所凭证。
2. Minimal app startup：如需启动 app context，只能使用 CI/test 安全 profile 与 mock / fake / NoReal 路径；不得启动 Paper trading runtime 或 LIVE runtime。
3. Mock path validation：只验证 mock / fake / NoReal path 能维持安全边界，不证明真实 provider 连通性。
4. EnvSafety fail-closed：复用或补齐 EnvSafetyValidatorTest，确保 LIVE / real provider / real client / real exchange / AI / DH runtime 冲突仍 fail closed。
5. No-outbound guard enforced：复用或补齐 NoOutboundExchangeGuardTest，确保真实交易所 host denylist 与 forbidden env-name 规则未放宽。
6. Placeholder-only credential path：只允许 PLACEHOLDER_ONLY / DO_NOT_COMMIT_REAL_VALUE / REPLACE_WITH_LOCAL_* 等占位值；不得读取真实 .env。
7. Forbidden env names not injected：ci-security-smoke job env 不得注入交易所 credential env，也不得注入被 guard 视为 forbidden 的 live / real-provider / real-client env 名。
8. NoReal permission probe remains SKIPPED：默认 permission probe 必须继续为 NoRealExchangeCredentialPermissionProbePort，结果语义保持 SKIPPED / REAL_EXCHANGE_PROBE_DISABLED。

## 4. Next implementation allowlist

| Area | Allowed files |
| --- | --- |
| CI workflow | .github/workflows/ci.yml（只新增 ci-security-smoke job 或必要的 job-local assertion step） |
| Backend tests | backend 相关最小测试文件：EnvSafetyValidatorTest / NoOutboundExchangeGuardTest existing coverage review / reuse，以及必要时新增最小 NoReal / placeholder / CI env-name smoke test |
| Current docs | docs/current/STATUS.md、docs/current/TESTING.md、docs/current/WORKLOG.md、必要的 5B-SMOKE implementation / review 记录 |

任何超出上述 allowlist 的改动都必须在下一轮 implementation 开始前重新评审，不得顺手扩大。

## 5. Next implementation forbidden scope

下一轮 implementation 明确禁止：不读取真实凭证；不访问真实 provider / exchange endpoint；不做 outbound call；不启动 LIVE；不启动 Paper trading runtime；不启动 DH runtime；不启动 AI runtime；不触碰 RealClient；不触碰 real provider；不触碰 real exchange adapter；不执行 real permission probe；不修改 migration；不修改 frontend / research / scripts / deploy；不修改 .env.example，除非单独证明必须修改，默认不允许；不新增真实 adapter / provider / exchange 测试；不把 skipped / failed 写成 passed。

禁止触达真实交易所或真实 provider host：OKX；Binance；Bybit；Bitget；Gate；Coinbase；Kraken；Crypto.com；Hyperliquid。

## 6. Candidate tests

| Candidate | Plan decision |
| --- | --- |
| EnvSafetyValidatorTest existing coverage review / reuse | 必须复用；只有发现缺口才补最小 case，不放宽 fail-closed 矩阵 |
| NoOutboundExchangeGuardTest existing coverage review / reuse | 必须复用；不得删除 forbidden env-name / host denylist 断言 |
| Minimal NoReal smoke coverage | 允许新增或补齐，目标是证明 NoRealExchangeCredentialPermissionProbePort remains SKIPPED / REAL_EXCHANGE_PROBE_DISABLED |
| Placeholder credential smoke | 允许新增或补齐，目标是证明 placeholder-only 值不触发真实 credential path，且不读取真实 .env |
| CI env-name assertion | 允许新增或补齐，目标是证明 ci-security-smoke job 不注入 forbidden env names |
| Real adapter / provider / exchange tests | 禁止新增 |

P2 已转化为 implementation execution checklist：不再是 plan blocker，但下一轮 implementation review 必须逐项核对并报告。

建议的后端最小相关测试命令（下一轮 implementation 执行时再运行）：

    mvn -f backend/pom.xml -pl nq-app -am test "-Dtest=EnvSafetyValidatorTest,NoOutboundExchangeGuardTest,<FutureNoRealSmokeTest>" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnq.no-outbound.guard.required=true"

## 7. Acceptance commands for implementation

下一轮 implementation 必须执行并逐条回报：

    git status --short
    git diff --check
    git diff --stat
    git diff -- .github/workflows/ci.yml
    git diff -- backend
    git diff -- "backend/**/db/migration"
    git diff -- frontend research scripts deploy
    git diff -- .env.example
    mvn -f backend/pom.xml -pl nq-app -am test "-Dtest=EnvSafetyValidatorTest,NoOutboundExchangeGuardTest,<FutureNoRealSmokeTest>" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnq.no-outbound.guard.required=true"

如果不运行完整 CI，必须说明原因。下一轮 implementation 不得由本地命令替代 first green review；不得由本轮计划触发 GitHub Actions。

## 8. Rollback plan

下一轮 implementation 回滚方式：删除新增 ci-security-smoke job 或回退 .github/workflows/ci.yml diff；删除新增/修改的 backend smoke test diff；回退 docs/current 状态入口与 implementation/review 记录；确认无 DB / runtime / credential / provider / exchange 副作用。

本轮 docs-only plan 回滚方式：删除本文件，并还原 NQ_CI_BASELINE_PLAN.md、README.md、STATUS.md、ROADMAP.md、TESTING.md、WORKLOG.md 中本任务新增或更新的状态入口。

## 9. Risk list

| Priority | Result |
| --- | --- |
| P0 | 0。计划不允许真实凭证读取、outbound call、LIVE、AI、DH runtime、RealClient、real provider、real exchange adapter、real permission probe。 |
| P1 | 0。Batch 5B-ENV 已 FROZEN / ACCEPTED；Batch 5B-SMOKE-PREFLIGHT 已 REVIEWED / ACCEPTED；implementation 仍 NOT STARTED。 |
| P2 | 0 as plan blocker。原 P2 已转化为下一轮 implementation execution checklist：复用 EnvSafety / no-outbound coverage，补齐最小 NoReal / placeholder / CI env-name smoke。 |
| P3 | 0。job name 已定稿为 ci-security-smoke。 |

## 10. Final state

NQ-CI-SECURITY-BATCH-5B-SMOKE-IMPLEMENTATION-PLAN = IMPLEMENTATION PLAN READY / READY FOR REVIEW。
Batch 5B-ENV = FROZEN / ACCEPTED。
Batch 5B-SMOKE-PREFLIGHT = REVIEWED / ACCEPTED。
Batch 5B-SMOKE implementation = NOT STARTED。
Next CI job name = ci-security-smoke。
P2 = implementation execution checklist, not plan blocker。
P3 = closed; job name finalized。
NoReal permission probe remains SKIPPED。

Next concrete action：进入 NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN_REVIEW，只读复核本 implementation plan；不得直接执行 implementation。
