# NQ-GATEW-PLAN-IMPLEMENTATION Attempt 01

## Task Metadata

- Task ID：`NQ-GATEW-PLAN-IMPLEMENTATION`。
- Attempt：`01`。
- Task classification：`DOCUMENTATION / GATE_PLAN + FACT_SOURCE_SYNC + TASK_EVIDENCE_BOOTSTRAP`。
- Task ownership：`NQ-only`。
- Execution date：`2026-07-13`（Asia/Shanghai）。
- Branch：`dev`。
- Starting HEAD：`f764e7653cf92cabc3e0c1067ebd558f9373dc19`。
- origin/dev HEAD：`f764e7653cf92cabc3e0c1067ebd558f9373dc19`。
- Worktree：起始 `clean`；staged empty。

## Authority Before

```text
last_frozen_gate=GateV
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
accepted_batch=GateV-FREEZE
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-PLAN
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-PLAN-IMPLEMENTATION
```

安全状态：LIVE `DISABLED`、Shadow trading `NOT ENABLED`、AI `NOT STARTED`、DH runtime `NOT INTEGRATED`、Integration runtime `NOT STARTED`、RealClient/real provider/private trading/real permission probe `NOT IMPLEMENTED`、Python live execution ready `NO`。

## Scope

只建立 GateW planning baseline、current task evidence 和 authority/entry/ledger 最小同步；不修改代码、API、migration、checker、CI、部署、历史 archive 或 credential material。唯一 venue 为 OKX Spot。

## Files Inspected

- 规则与入口：`AGENTS.md`、`CLAUDE.md`、root `README.md`、`docs/current/README.md`。
- Current authority/capability：`STATUS.md`、`ROADMAP.md`、`API.md`、`DB_SCHEMA.md`、`ARCHITECTURE.md`、`MODULES.md`、`TESTING.md`、`WORKLOG.md`、`FACT_SOURCE_INDEX.md`、`GATEV_PLAN.md`。
- GateV archive：`docs/gates/gate-v/**` 的 12 个 role 文件。
- Governance：`docs/current/GOVERNANCE_WORKFLOW.md`、`scripts/docs/governance-workflow-contract.json`、`scripts/docs/gate-archive-manifest.json`、authority/archive/release checker 与 lifecycle regression。
- Code domains：附件列出的 adapter/core/infra/risk/scheduler/app 与 accounts/trading/runtime/api/hooks 目录；`backend/nq-trading` 和 `frontend/src/pages/adapter-readiness` 当前不存在。
- 关键代码样本：`AdapterCapability`、`DefaultAdapterReadinessService`、guarded adapter、`OkxExchangeAdapter`、`OkxRuntimeConfig`、`OkxPermissionProbeBoundary`、credential repository/service/NoReal port、Trading Preflight、Shadow preview、Kill Switch、OKX reconcile/recovery 与三类前端页面/API/hook。

## Files Created

- `docs/current/GATEW_PLAN.md`
- `docs/current/evidence/gate-w/NQ-GATEW-PLAN-IMPLEMENTATION.attempt-01.md`

## Files Changed

- `README.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/FACT_SOURCE_INDEX.md`
- `docs/current/evidence/gate-w/README.md`

## Planning Decisions

- GateW 定位：单交易所准实盘准备与 Shadow-to-Live 安全门槛；仍不可交易。
- 唯一 venue：OKX Spot；单账户、最多 3 个 allowlisted symbols/currencies。
- public/private client、contract、profile、transport、metrics、audit 必须分离；private default-deny。
- 允许的 private 候选只包括 permission readiness、account/balance read、reconciliation 所需 read、instrument/fee rule read；所有 order mutation 和资金操作永久禁止进入 GateW。
- credential 继续以 DB 密文为主数据源；只在窄 infrastructure boundary 解密，最小生命周期使用，全链路脱敏。
- real permission probe 是 read-only permission readiness，不是交易授权；unknown/partial/unexpected permission 全部 fail-closed。
- GateW-1 不新增 migration。durable snapshots/reconciliation/human-review evidence 需要独立 schema/security review。
- reconciliation 独立于既有会写订单/成交/ledger 的 `OkxRestReconcileService`。
- dry-run preview 不依赖 `TradingAdapter` 或 mutating port，不产生真实订单 ID。
- kill switch 在 LIVE disabled 时阻止 private read/soak/preview；human-review evidence 不复用 GateV review case 作为交易授权。
- read-only soak 为连续 7 天，24 小时 checkpoint；forbidden call/side effect/secret exposure 必须为 0。

## GateW Batches

1. GateW-1：OKX Spot typed capability、public/private isolation、endpoint allow/deny、profile/guard、fake transport/no network。
2. GateW-2：显式非默认 profile 的真实只读 permission probe 与 account/balance snapshot；独立 security/schema review。
3. GateW-3：read-only reconciliation、dry-run preview、venue/fee/notional/tick/lot/risk preflight；独立 security/risk review。
4. GateW-4：kill switch、human-review evidence、7-day soak、backup/restore/incident drill；独立 security/operations/persistence review。
5. GateW-FREEZE：汇总 exact CI、官方事实、reviews、soak/drill 与 archive；真实订单和资金操作仍关闭。

## First Implementation Slice

```text
NQ-GATEW-1-OKX-SPOT-CAPABILITY-AND-ENDPOINT-GUARD-IMPLEMENTATION
```

必须产出 Java 代码和测试；不再做纯文档 plan review；不访问真实网络、不读取真实 credential、不新增 API/migration、不实现订单提交。

## Commands Executed

- `git fetch origin`
- `git status --short`、`git diff --cached --name-only`、`git branch --show-current`
- `git rev-parse HEAD`、`git rev-parse origin/dev`、`git log --oneline -10`
- `git cat-file -t nq-gatev-freeze`、`git rev-parse "nq-gatev-freeze^{}"`
- `gh run list --commit <HEAD> --limit 5`
- `scripts/docs/test-current-authority-next-action.ps1`
- `scripts/docs/test-governance-workflow-lifecycle.ps1`
- `scripts/docs/check-current-authority.ps1`
- `scripts/docs/check-doc-links.ps1 -Roots docs/current`
- `rg --files` 与受限 `rg -n` 审计 current docs、GateV archive、指定代码域和 archive governance。
- 收尾验证命令见下方 Validation Results；未运行 Maven/frontend/Playwright/Python。

## Validation Results

- Preflight：PASS；branch `dev`，worktree/staged clean，`HEAD == origin/dev == f764e7653cf92cabc3e0c1067ebd558f9373dc19`。
- Current exact-HEAD CI：PASS；`NQ CI Baseline` run `29199297388` 为 `completed / success`。
- GateV release evidence：PASS；`nq-gatev-freeze` 为 annotated tag，peeled target 为 `530ce4e2bde416aa61944262cbfbadca556656cb`。
- Pre-edit authority/next-action checker：PASS。
- Governance lifecycle regression：PASS；包含 authority lifecycle、current/archive evidence、path traversal/symlink 与 disposable release positive/negative fixtures，输出 `TASK_EVIDENCE_POLICY_VALID`。
- Post-edit authority：PASS；schema v3 为 GateW-PLAN `REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`，next action 为 `NQ-GATEW-PLAN-COMMIT-AND-PUSH`。
- Doc links：PASS；`62 checked / 1 historical warning / 0 errors`。warning 为既有 `TESTING.md:8479 -> ./GATEJ_TEST_PLAN.md`。
- `git diff --check`、新增文件 trailing-whitespace、evidence path/content policy 与 forbidden-scope diff：PASS；staged empty。
- Maven/frontend build/Playwright/Python：`NOT RUN`；本轮 docs-only，且附件明确禁止运行。

## P0 / P1 / P2 / P3 Findings

- P0：0。
- P1：0。
- P2：`CLAUDE.md` 仍硬编码 GateJ/GateK 历史阶段，但不属于 current authority；现有 OKX adapter/reconcile 含历史 mutating/write-side 语义，GateW-1 必须证明 GateW profile 下不可达。
- P3：首轮 broad `rg` 使用 `!target/**`，在 Windows nested module 下仍命中少量 `backend/*/target/**` Maven metadata；后续改为 `!**/target/**`。该偏差为只读、未触及 credential，但不应在后续任务重复。

## Known Limitations

- 本计划未在线重验任何具体 OKX endpoint、签名、rate limit、instrument 字段或错误码；GateW-1 必须在 implementation 当日重新打开官方文档并形成事实表。
- real permission probe、private read client、durable snapshot/reconciliation/approval、7-day soak 均未实现或执行。
- GateW archive 尚未创建；当前只验证 governance contract 已支持 future task evidence path，不能据此预写 GateW archive 已通过。
- current `GATEV_PLAN.md` 仍是历史 allowed residual；本任务不移动/改写 archive。

## Security and Trading Boundary

未读取 `.env`、key/pem、secrets 或真实 credential 文件；未调用交易所、未启动 probe、未启用 LIVE/Shadow、未实现 RealClient/provider/private adapter、未修改 account/order/ledger/position/fund。GateV operator actions、GateW readiness、preview、risk 和 human review 均不构成 trading authorization。

## Task-evidence Archive Compatibility

`SUPPORTED`。machine contract 已允许 current/archive task evidence path，Archive checker 将 `source/task-evidence/**` 视为 approved non-role evidence；nested README 不占 archive-entry role，unknown/empty/traversal/symlink 仍 fail-closed。GateW Freeze 仍必须独立满足全部 archive roles，task evidence 不替代 role 文件。

## Authority After

```text
accepted_batch=GateV-FREEZE
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-PLAN
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-PLAN-COMMIT-AND-PUSH
```

GateW-1 未初始化。

## Rollback Method

本轮未 commit/stage。回滚只需删除 2 个新增文件，并用反向 patch 恢复 8 个 current/entry/ledger 文档的本轮追加或状态更新；不得使用 `git reset --hard`，不得改 GateV tag/archive。

## Final Decision

`IMPLEMENTED / SELF-REVIEWED / READY_TO_COMMIT`。本结论只覆盖 planning baseline 和 current fact sync。

## Commit Recommendation

`docs(gatew): establish GateW pre-live readiness plan`

## Next Action

`NQ-GATEW-PLAN-COMMIT-AND-PUSH`。本计划取得 exact-HEAD CI green 后，直接进入 GateW-1，不新增 plan review/freeze/addendum。
