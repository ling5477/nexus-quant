# NQ-DH Integration-1 M1 DH Dry-run Contract Entry Mock Work Order（NQ）

> Task: NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK-WO
> Status: COMPLETED / WORK_ORDER_ONLY / DH_RESULT_CONSUMED / NOT IMPLEMENTED
> Date: 2026-07-03
> Repository: NexusQuant dry-run worktree
> Source of truth: docs/current

## 1. 目标与边界

本文件记录 DH M1 dry-run contract entry mock work order 对 NQ dry-run worktree 的只读影响，以及后续 NQ M2 stub recorder work order 的前置条件。

本轮不是 NQ implementation，不授权 backend、frontend、research、scripts、deploy、`.github`、migration、contracts、golden cases、fixture JSON、API、Controller、runtime、真实 HTTP、real provider、AI / LangGraph 或 LIVE。

NQ dev 主线 `F:\project\nexus-quant` 本轮只读，不允许写入。NQ-DH 相关 NQ 侧后续任务只能在 `F:\worktrees\nexus-quant-i1-dryrun` / `nq-dh-i1-dryrun` 执行。

## 2. 前置边界确认

```text
DH repository: F:\project\decision-hub
DH branch: dev
DH HEAD: a1fe4b50661a08c21b79be8597ac14d90968d11f
DH precheck: clean

NQ dry-run worktree: F:\worktrees\nexus-quant-i1-dryrun
NQ dry-run branch: nq-dh-i1-dryrun
NQ dry-run HEAD: 39b58d7f3a4594c8091765f1faf9b151f0a70d1f
NQ dry-run precheck: clean

NQ dev repository: F:\project\nexus-quant
NQ dev branch: dev
NQ dev HEAD: f69b5cc0e621ed77a50bbf5d5047219ca50888e9
NQ dev precheck: NQ_MAINLINE_DIRTY_ALLOWED
NQ dev dirty scope: non NQ-DH / non Integration-1 mainline changes only
NQ dev NQ-DH / Integration-1 dirty diff: none
WORKSTREAM_MIXED_BLOCKED: NO
```

NQ dev dirty diff 属 GateO / marketdata / API 主线文档范围；`docs/current/*NQ_DH*` 与 `docs/current/*INTEGRATION1*` dirty diff 为空，因此不触发 `WORKSTREAM_MIXED_BLOCKED`。

## 3. DH M1 work order 结论对 NQ 的影响

```text
RECOMMENDED_ENTRY_SHAPE: Option C / test-support mock-only / no runtime endpoint
NQ role in M1: consume DH M1 result only
M2 allowed: YES, only as work-order-only
NQ implementation code allowed by M1: NO
NQ runtime integration allowed: NO
Real HTTP allowed: NO
Real provider allowed: NO
LIVE allowed: NO
```

NQ 侧在 M1 中不新增代码，不创建 fixture，不新增 API / Controller，不接 DH runtime，不实现真实 HTTP client。M1 只为后续 M2 设计提供输入。

## 4. M2 前置条件

后续如果进入 `NQ-DH-I1-M2-NQ-DRYRUN-STUB-RECORDER-WO`，必须先满足：

- DH M1 已明确 no runtime endpoint，NQ 不能先写真实 client。
- `source=NQ_DRYRUN` 仍为 `NEEDS_SECURITY_CONTRACT_CHANGE`；M2 只能按 future source plan 设计，不得写成已实现 allowlist。
- canonical error taxonomy 尚未全部实现；M2 只能规划 normalization / recorder 行为，不得新增 enum / schema / fixture。
- `dryRun / decisionId / confidence / traceSummary / replayRef / auditRef / X-NQ-DH-Schema-Version` 仍为 `DOC_ONLY_ALIAS` 或 future envelope planning，不得作为 required fixture field。
- M2 必须在 dry-run worktree 执行，不得在 NQ dev 主线执行。

## 5. NQ M2 默认允许与禁止范围

M2 work order 可规划的默认候选范围：

```text
docs/current/**
backend/nq-app/src/test/**
backend/nq-app/src/test/resources/**
```

M2 work order 默认禁止：

```text
backend/*/src/main/**
frontend/**
research/**
scripts/**
deploy/**
.github/**
contracts/**
golden_cases/**
backend/**/db/migration/**
API / Controller
real HTTP client
provider
exchange adapter
runtime integration
```

M2 只能规划 stub / recorder 行为：

```text
NQ constructs mock dry-run request
NQ consumes mock DecisionOutput
NQ records summary / trace / blocked reason
NQ never orders, cancels, starts Paper Run, reads credential, reads NQ DB private state, or mutates risk / ledger / strategy state
```

## 6. NQ 接收失败结果的规则

- 任一 DH validation failure 都只能记录，不得触发交易。
- `LONG_BIAS` / `SHORT_BIAS` 是只读倾向，不是 `BUY` / `SELL`。
- unknown error 不得升级为 directional bias。
- failed response 只能进入 blocked / rejected summary。
- NQ 不得基于 DH output 绕过自身风控、订单状态机、账务、持仓或 Paper Run 控制。

## 7. Readiness decision

```text
ALLOW_M1_WO_CLOSE: YES
ALLOW_I1_M2_NQ_DRYRUN_STUB_RECORDER_WO: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_FIXTURE_IMPLEMENTATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_API_CONTROLLER_CHANGE: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 8. 验证要求

本轮 NQ worktree 验证：

```powershell
git status --short
git diff --check
git diff --stat
git diff --name-only -- backend frontend research scripts deploy .github "backend/**/db/migration"
mvn -ntp -f backend/pom.xml test
mvn -ntp -f backend/pom.xml -pl nq-app -am "-Dtest=*Integration0*" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

NQ dev 只执行：

```powershell
git status --short
git diff --stat
git diff --name-only -- docs/current/*NQ_DH* docs/current/*INTEGRATION1*
git diff --name-only --cached -- docs/current/*NQ_DH* docs/current/*INTEGRATION1*
```

## 9. 回滚要求

本轮回滚只需 revert NQ dry-run worktree 的 M1 docs-current 变更，并重跑：

```powershell
git diff --check
git diff --name-only -- backend frontend research scripts deploy .github "backend/**/db/migration"
```

NQ dev 不应出现任何回滚动作，因为本轮不修改 NQ dev。

## 10. 下一步

```text
NQ-DH-I1-M2-NQ-DRYRUN-STUB-RECORDER-WO / NOT STARTED / WORK_ORDER_ONLY
```

下一步只能输出 NQ worktree 侧 M2 work order；不得直接进入 NQ backend implementation。
