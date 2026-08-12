# GateX-0E Scoped Query / Configuration Hygiene 条件实施审计（attempt-01）

## 1. 任务与基线

- 任务：`NQ-GATEX-0E-SCOPED-QUERY-CONFIG-HYGIENE-CONDITIONAL-IMPLEMENTATION`。
- 分类：NQ-only、post-CI authority reconciliation、定向代码审计、条件实施。
- 分支：`dev`。
- 起始与远端 HEAD：`885ed23375d0d8a58d9d10d2c4768f390322af93`，两者一致，工作区起始 clean。
- GateX-0D commit：`885ed23375d0d8a58d9d10d2c4768f390322af93`。
- exact-head CI：GitHub Actions `NQ CI Baseline` run `31344357225`，`completed / success`（已完成 / 成功）。
- 安全边界：LIVE=`DISABLED`，Shadow trading=`NOT_ENABLED`；未访问生产、credential、private endpoint 或交易写侧。

## 2. Query findings

定向检查范围：

- `frontend/src/features/validation/**`
- `frontend/src/pages/strategies/**`
- `frontend/src/api/query-keys.ts`
- `frontend/src/hooks/useStrategyValidationQueries.ts`
- `frontend/src/hooks/useShadowValidationWorkflowQueries.ts`
- `frontend/src/hooks/usePublishesListQuery.ts`
- `frontend/src/hooks/useStrategyListQuery.ts`

结论：未发现会被 GateX-1/3/4 继续复制的实质 Query 缺口。

- Strategy、validation、shadow、publish/release 相关资源已使用 `frontend/src/api/query-keys.ts` 的集中 query-key factory。
- 页面未直接拼装 GateX 相关 raw query key；相关 server state 由 feature-level hooks 消费。
- query filter/request 已进入对应 key；未发现同一资源的冲突 key 或不稳定 object 导致的 cache correctness 风险。
- strategy/publish mutation 已按资源族执行 invalidation；未发现本轮范围内 mutation 后漏 invalidation 的证据。
- 未发现需要在 GateX-1 前修复的重复 fetch、key collision 或 hook ownership 问题。

## 3. Configuration findings

定向检查范围：backend 中与 `strategy|validation|shadow|release|artifact` 同时命中的 `@Value`、`@ConfigurationProperties` 与 `nq.*` 使用点。

结论：未发现 GateX-1 前必须实施的 configuration ownership 或类型安全缺口。

- `nq.validation-operations.scheduler.*` 已由 `ValidationEvidenceSchedulerProperties` typed `@ConfigurationProperties` 持有，并通过 Spring configuration 装配。
- `ValidationEvidenceScheduler` 的 `@Scheduled` 表达式仍直接引用相同 fixed-delay/initial-delay key 与默认值；这是既有 scheduler annotation 绑定限制下的局部重复，但不属于 GateX-1 Strategy Release / Artifact productionization 将复制的配置读取路径。
- PostgreSQL smoke test properties 属于测试环境检查，不是 GateX production configuration ownership。
- 未发现同一 GateX capability 多套 key、字符串配置缺类型约束、配置解析歧义或会扩大 release/shadow coupling 的证据。

## 4. IMPLEMENT / SKIP decision

代码判定：`SKIP / NOT_REQUIRED`（跳过 / 无需实施）。

理由：现有发现仅属于非阻断的既有 scheduler annotation hygiene，不满足以下 IMPLEMENT 条件：GateX-1 会复制错误模式、真实 cache correctness 风险、GateX configuration ownership/类型安全问题，或 release/shadow coupling 将明显扩大。

实际业务代码修复：无。未制造 query/config 重构，未改变 API、DTO、cache 语义、Spring assembly、配置 key/default/profile、migration 或 runtime。

## 5. Authority blocker

最终治理状态：`BLOCKED / AUTHORITY_SKIP_TRANSITION_REQUIRED`（阻断 / 需要 authority skip transition）。

- `scripts/docs/governance-workflow-contract.json` schema `1.4.0` 的 `workBatchStatuses` 不包含 `SKIPPED|NOT_REQUIRED`，也没有 GateX-0E skip 到 GateX-1 的合法 lifecycle/next-action mapping。
- 任务明确禁止自行修改 governance contract，因此不得为本轮补造 transition。
- 当前任务 allowlist 未包含 `docs/current/ROADMAP.md`、根 `README.md` 与 `docs/current/README.md`；单独修改 `STATUS.md` 会使 current authority 与入口/roadmap 互相矛盾并导致 checker 失败。
- 因此 GateX-0D exact-head CI 接受事实已核验，但 machine authority 未在本轮半同步；GateX-1 未获授权。

## 6. Deferred hygiene backlog

- 非阻断：后续自然触达 validation operations scheduler 时，可评估如何消除 `@Scheduled` key/default 表达式与 typed properties 的重复；不得为此单独扩大 GateX-0E。
- 不纳入：全仓 raw query key、MarketData/Accounts/Backtest hooks、全仓 `@Value`、GateW key naming、通用 data-fetch framework。

## 7. Validation

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git fetch origin`、HEAD/branch/worktree preflight | PASS（通过） | `dev` clean；`HEAD == origin/dev` |
| GateX-0D exact-head CI | PASS（通过） | run `31344357225 / completed / success` |
| Query 定向 inventory | PASS（通过） | 无 GateX-1 阻断性缺口 |
| Configuration 定向 inventory | PASS（通过） | 无 GateX-1 阻断性缺口 |
| 初始 authority checker | PASS（通过） | 仅证明旧 `GateX-0D / UNCOMMITTED / NOT_RUN` authority 内部一致 |
| frontend build / smoke | NOT RUN（未运行） | 无业务代码修改，按任务无需人为制造回归 |
| backend Maven | NOT RUN（未运行） | 无 backend 修改 |
| 最终 authority checker | 见任务收尾命令 | authority 不推进，预期保持旧状态内部一致 |

## 8. GateX-1 readiness

设计复核通过，但 production-code authorization 被 authority blocker 阻断：

- `publishRecordId = releaseAnchorId = backtest_publish_records.publish_record_id`。
- `shadow_runs.publish_id` 继续保存 release anchor。
- binding mode 为 `LEGACY_UNBOUND`、`LEGACY_PUBLISH_ONLY`、`RELEASE_BOUND`；只有 `RELEASE_BOUND` 可继续 admission 评估。
- `backend/nq-core/src/test/java/**/strategyrelease/preparation/**` 与 fixtures 仍为 test-only preparation evidence；正式 productionization 尚未开始。

## 9. Findings 与结论

- P0：无。
- P1：1 项——治理合同缺少合法 skip transition，且任务禁止修改合同；阻断 GateX-1 authorization。
- P2：1 项——validation scheduler annotation 保留 key/default 局部重复，不阻断 GateX-1。
- P3：无。

最终结论：`BLOCKED / AUTHORITY_SKIP_TRANSITION_REQUIRED / NO_ARTIFICIAL_REFACTOR / GATEX_1_NOT_AUTHORIZED`。

下一动作：先以独立治理任务为 GateX-0E 增加受测试保护的 `SKIPPED|NOT_REQUIRED` transition，并同步完整 current-control allowlist；完成并取得 exact-head CI 后，才可初始化 `NQ-GATEX-1-STRATEGY-RELEASE-ARTIFACT-PRODUCTIONIZATION-IMPLEMENTATION`。
