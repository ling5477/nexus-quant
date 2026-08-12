# GateX-4 Persistent Artifact Locator Required Blocked（attempt-01）

## Task classification

- 任务：`NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED`。
- 归属：NQ-only。
- 分类：`BLOCKER_RESOLUTION / POST_REVIEW_AUTHORITY_TRANSITION / FACT_SOURCE_SYNC`。
- 本轮只关闭设计阶段 blocker 并初始化 GateX-4B；不实施 migration 或 product code。

## Original blocker

```text
work_batch=GateX-4
work_batch_status=BLOCKED
reason=PERSISTENT_ARTIFACT_LOCATOR_REQUIRED
next_action=NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED
```

GateX-4 原 API/UI 无法安全取得 `publishRecordId → trusted root + manifest` 的 production 绑定。接受客户端 filesystem path、从 digest/publishRecordId 推导目录或发明 layout 均不安全。

## GateX-4A review result

- `NQ-GATEX-4A-PERSISTENT-ARTIFACT-LOCATOR-SCHEMA-REVIEW`：schema/security review=`PASS`（通过）。
- `SELECTED DESIGN = A`。
- P0=0。
- 设计结论：locator ownership、schema 形状、identity、security、immutability 与 historical compatibility 已唯一确定。
- 4A 最终 authority handoff 曾因 `GateX-4 / BLOCKED` 只能映射到 `BLOCKED` action 而停止；本任务不改 governance contract，而是按现有普通 lifecycle 初始化独立 remediation batch `GateX-4B / NOT_STARTED`。

## Selected design

未来由 GateX-4B 评审并实现：

```text
backtest_publish_records.artifact_storage_key VARCHAR(128) NULL
backtest_publish_records.manifest_storage_key VARCHAR(128) NULL
```

- nullable pair invariant。
- constrained opaque server-owned storage key。
- 禁止 absolute filesystem path、URL、客户端 path、working directory 与 trusted root 入库。
- locator=`where`；digest=`what`，不可互推。
- publish 后 immutable；修复走 new release 或显式 forward remediation。
- `NO FAKE BACKFILL`；历史 NULL pair 保持 `LEGACY_ARTIFACT_UNBOUND`。
- trusted root 只来自 future server-side typed configuration。

以上均是已接受的设计事实，不是当前数据库 capability；本轮没有创建字段或 V37。

## Design blocker resolution

原 blocker 要求先回答“locator 属于谁、存在哪里、允许什么值、如何与 trusted root/digest/历史行相处”。4A 已唯一回答这些问题，因此 `PERSISTENT_ARTIFACT_LOCATOR_REQUIRED` 的设计不确定性已关闭。

```text
DESIGN BLOCKER RESOLVED
!=
IMPLEMENTATION COMPLETE
```

## Why GateX-4 remains incomplete

- GateX-4 原 API/UI 未实现，仍为 `BLOCKED / WAITING_FOR_ARTIFACT_LOCATOR_REMEDIATION`。
- V37 migration 未创建。
- `BacktestPublishRecord`、repository/JDBC、publish write path 未修改。
- typed trusted-root configuration、server resolver、legacy fail-closed runtime 未实现。
- fresh/upgrade PostgreSQL、constraint 与 immutability regression 未执行。

## Why GateX-4B is required

GateX-4B 是插入到 GateX-4 内的 migration remediation implementation batch。它使用现有 `NOT_STARTED → IMPLEMENTATION` lifecycle，承接 4A 已确定的设计边界，同时保持 GateX-4 原 API/UI 未完成的事实。

## Authority transition

```text
accepted_batch=GateX-3
accepted_batch_status=ACCEPTED|CI_GREEN

work_batch=GateX-4B
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION
```

- `active_gate=GateX` 与 `active_gate_status=IN_PROGRESS|NOT_FROZEN` 不变。
- LIVE=`DISABLED` 不变。
- governance probe：`NOT_STARTED` 期望 action type=`IMPLEMENTATION`；候选 action 解析为 `IMPLEMENTATION`，且对 `GateX-4B` 的 work-batch mapping 返回 true。

## Governance contract impact

- `governance-workflow-contract.json`：未修改。
- `check-current-authority.ps1`：未修改。
- governance tests：未修改。
- 没有新增 `SKIPPED`、`REMEDIATED`、`DESIGN_ACCEPTED` 或 `UNBLOCKED` lifecycle status。

## GateX-4B implementation boundary

后续至少必须重新评审并实现：

- V37 migration，且不得修改 V1～V36。
- 两个 nullable storage key、pair invariant、syntax constraint、immutability enforcement、`NO BACKFILL`。
- `BacktestPublishRecord` model、repository/JDBC 与 publish write path。
- legacy rows compatibility 与 fail-closed runtime。
- real PostgreSQL fresh、V36→V37 upgrade、constraint、immutability regression。
- 重新评估 partial UNIQUE 是否必要、trigger immutability 方案以及 DDL lock/index scan 风险；不得机械照抄 4A 推荐 DDL。

禁止 frontend/Python/LIVE/真实交易/客户端 path API/普通 rebind/governance contract 扩改。

## Validation

- Git/authority preflight：`dev`；`HEAD == origin/dev == 5f4824eecaac5cffbbc314fb8f767bd6ba45c29f`；仅 9 个允许 staged baseline。
- governance mapping probe：`expected_type=IMPLEMENTATION`、`candidate_type=IMPLEMENTATION`、`candidate_valid_for_work_batch=True`。
- Windows PowerShell 5.1 authority checker：`errors=0 / PASS`；解析为 `GateX-4B / NOT_STARTED / ...-IMPLEMENTATION`。
- PowerShell 7 (`pwsh`) authority checker：`errors=0 / PASS`；与 Windows PowerShell 5.1 一致。
- `git diff --check`：通过；仅 LF→CRLF warning，无 whitespace error。
- `git diff --cached --check`：通过；10 个 staged 路径全部在允许清单，unexpected=0、unstaged=0、forbidden-scope changes=0。
- docs link checker：203 links checked、0 errors、1 个既有 `GATEJ_TEST_PLAN.md` historical-ledger warning。

## Findings

- P0：无。
- P1：无；原 authority mismatch 已通过独立 `GateX-4B / NOT_STARTED` mapping 解决，未修改 governance contract。
- P2：4B 仍需基于真实表规模重新评估 partial UNIQUE、trigger 与 Flyway lock/index scan 风险。
- P3：工程语义 MCP 未暴露，按规则降级到 PowerShell + Git/`rg`；直接读取 current authority、4A evidence 与 governance contract，结论可信度高。

## Rollback

若 final authority checker 失败，恢复本轮对 root/current README、STATUS、ROADMAP 的精确 diff并删除本 evidence；保留此前 9 个 staged baseline，不使用 `git reset --hard`。

## Next action

```text
NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION
```

## Final decision

```text
PASS /
GATEX_4_DESIGN_BLOCKER_RESOLVED /
GATEX_4B_MIGRATION_AUTHORIZED /
NO_GOVERNANCE_CHANGE /
READY_FOR_IMPLEMENTATION
```
