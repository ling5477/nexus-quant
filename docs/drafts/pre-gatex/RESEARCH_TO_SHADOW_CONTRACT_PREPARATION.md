# Research-to-Shadow Contract Preparation

状态：`DRAFT / PRE-GATEX / UNMERGED`（草案 / GateX 前准备 / 未合并）。

任务：`NQ-PRE-GATEX-RESEARCH-TO-SHADOW-CONTRACT-PREPARATION-ATTEMPT-02`。

## 1. 目标与边界

本草案只回答“离线研究事实如何以最小强契约进入现有 Shadow Run 之前的验证边界”。它不启动 GateX，
不修改 production code，不创建正式 migration，不导入 artifact，不启动 Shadow Run，更不产生 LIVE 或交易授权。

Authority 读取结果：

- GateW：`IN_PROGRESS / NOT_FROZEN`。
- GateW Attempt-09：`RUNNING / PENDING_168H`。
- `plannedAcceptanceAt` raw UTC：`2026-07-29T11:19:59.5201964Z`。
- GateX：`NOT_STARTED`。
- LIVE：`DISABLED`。

## 2. 审计方法与证据范围

审计只读取当前仓库，不读取 credential、环境密钥、真实账户数据或远端服务。核心证据：

- Dataset：`V18__gate_h3_marketdata_dataset_binding.sql`。
- Backtest：`V7__gate_f1_research_backtest_skeleton.sql`、`V20__gate_i2_backtest_traceability.sql`。
- Evaluation：`V9__gate_f4_evaluation_reports.sql`、`V20__gate_i2_backtest_traceability.sql`。
- Publish：`V10__gate_f5_publish_records.sql`、`BacktestPublishService.java`。
- Strategy Version：`V19__gate_i1_strategy_versions.sql`、`StrategyVersionService.java`。
- Python artifact：`research/py/src/nq_research/evaluation/artifacts.py`。
- Java preview：`PythonEvaluationArtifactBindingService.java`、`PythonEvaluationArtifactPreviewOverviewQueryService.java`。
- Shadow：`V32__gate_r_shadow_run_fact_model.sql`、`ShadowRunStateMachine.java`。

## 3. Existing chain audit matrix

| 链路 | 当前事实 | 已有强项 | 缺口 / 不能推导的语义 | 结论 |
|---|---|---|---|---|
| Dataset | `marketdata_datasets` 使用 UUID 主键；coverage 记录质量；backtest config/run 固化 dataset snapshot。 | 范围、质量、回测绑定与历史快照已有明确事实。 | 当前没有把 dataset content hash、feature definition 与 release manifest 统一绑定。 | 复用 dataset identity；在 manifest 增加 `datasetHash`、`dataWindow`、`featureDefinitionVersion`。 |
| Strategy Version | `strategy_versions` 保存参数、配置、来源快照与 checksum；应用生成 `sv-<UUID>`。 | 已有不可变版本语义和发布引用。 | `strategyVersionId` 不是裸 UUID；不能让新 schema 误判。也没有 artifact file manifest。 | `strategyVersionId` 保持 opaque domain ID，复用现有 identity。 |
| Backtest | `backtest_configs` / `backtest_runs` 绑定 strategy version、dataset 与多份 snapshot。 | 可追溯输入已落库，run 历史不随配置重绑而改变。 | 没有正式 release artifact file 列表与 aggregate digest。 | 复用 run 追溯事实，不复制回测算法或运行事实。 |
| Evaluation | `backtest_eval_reports` 绑定 run 并保存核心 metrics。 | 评估与 run 有稳定引用。 | 评估成功不等于 release verified，也不等于可启动 Shadow。 | `evaluationId` 进入 manifest，但保持 opaque domain ID。 |
| Publish | `backtest_publish_records` 以 backtest run 幂等；成功发布要求 backtest/evaluation 成功，可绑定 `ACTIVE` strategy version，并固化 publish/evaluation/version snapshots。 | 已承担“研究结果被发布”的主要事实，不能另建平行 publish 主链。 | 只有 `SUCCEEDED/FAILED` publish status；缺 manifest、逐文件 hash、真实 digest 重算、独立 release verification 和 lifecycle event。 | Strategy Release 采用 `EXTEND`，以现有 publish record 为 anchor。 |
| Python Artifact | `python-evaluation-artifact.v1` 使用 canonical JSON checksum，递归拒绝敏感字段，并固定 offline / diagnostic / not-authorization 边界。 | 已有安全字段、checksum、虚构 fixture 与纯离线实现。 | Python checksum 是 evaluation payload checksum，不等同于一组物理 artifact files 的 manifest digest。 | Strategy Artifact 采用 `EXTEND`，保留现有 contract，同时增加 release manifest 层。 |
| Java Binding Preview | request-body preview 比较 caller 提供的 expected checksum；No-file overview 明确不读取 path/manifest/file。 | fail-closed preview 和 not-authorization 语义清晰。 | 没有从受控根目录读取文件、重算 file SHA-256 或写 append-only verification fact。 | Artifact Verification 采用 `NEW`；现有 preview 不得被描述为 verification。 |
| Shadow Run | `shadow_runs` + `shadow_run_events` + `shadow_run_snapshots` + `shadow_consistency_reports`；主表绑定 strategy version、dataset、evaluation、publish、paper run，并有幂等 key、乐观锁和 no-side-effect flags。 | 已完整承担 session、事件、快照与一致性报告语义；终态 fail-closed。 | 尚未绑定正式 release manifest verification；完成也不能表示 LIVE authorized。 | Shadow Session 采用 `REUSE`；禁止新增重复的 `shadow_sessions`。 |
| Consistency Report | `shadow_consistency_reports` 表达 `CONSISTENT/DIVERGED/NOT_COMPARABLE/PARTIAL/FAILED`。 | 已有差异、限制与追踪事实。 | 一致性只用于复盘，不是 release approval 或 LIVE authorization。 | 直接复用，保持授权语义隔离。 |

## 4. REUSE / EXTEND / NEW / DEFER decisions

| 概念 | 决策 | 依据 | GateX 最小后续 |
|---|---|---|---|
| Strategy Release | `EXTEND` | version + publish 已覆盖 identity、成功事实与 snapshots；平行主链会产生双写和状态冲突。 | 在 publish anchor 上增加 manifest/lifecycle 扩展与 append-only event；正式 migration 另行 review。 |
| Strategy Artifact | `EXTEND` | Python artifact 已有离线 schema/checksum/安全边界，Java 已有 preview；应向 file manifest 扩展，而非重写。 | 固化 file list、relative path、file SHA-256、size/media type、aggregate digest。 |
| Shadow Session | `REUSE` | `shadow_runs` 四表已经覆盖 session aggregate、events、snapshots、report、幂等与终态。 | 通过现有 `publish_id` 追溯 release anchor；如需更强 FK 只做独立 schema review。 |
| Risk Limit Set | `DEFER` | 仓库没有 `risk_limit_sets`；当前没有版本共享、复用查询或独立生命周期的证据。 | GateX 先使用 manifest 中不可变 `riskBudget` snapshot；出现跨 release 复用需求后再评审。 |
| Artifact Verification | `NEW` | preview 不读文件也不重算 digest；必须区分“caller 值相等”与“受控文件完整性已验证”。 | 新增 append-only verification fact；验证通过仍固定 `diagnosticOnly/notTradingAuthorization`。 |

## 5. 是否需要新 Maven module

结论：不需要。

原因：现有 `nq-core` 已具备 Jackson 与 JUnit，contract test 可以放在 `src/test`；正式 GateX 的 domain contract 也可先沿现有
`nq-core` 边界实现。新 module 会增加 reactor、dependency direction、CI 与发布复杂度，但当前没有独立部署、独立 owner 或复用消费者证据。

## 6. Java 与 Python 之间缺失的最小强契约

### 6.1 Identity 与 traceability

Manifest 最少包含：

- `schemaVersion=strategy-release-manifest.v1`。
- opaque `strategyVersionId`、opaque `evaluationId`。
- UUID `datasetId`。
- `dataWindow.start/end`、`datasetHash`、`featureDefinitionVersion`。
- `parameters`、不可变 `riskBudget`、`signalOrWeightSummary`。
- `artifactFiles[]`、`artifactDigest`、`generatedAt`、`generatorVersion`。
- 固定安全边界：`noCredentialAccess=true`、`noPrivateEndpoint=true`、`diagnosticOnly=true`、
  `notTradingAuthorization=true`。

### 6.2 文件与路径合同

每个 `artifactFiles` 项必须包含：`logicalName`、`relativePath`、`sha256`、`sizeBytes`、`mediaType`。

路径规则：

- 只允许 `/` 分隔的相对路径。
- 拒绝 `/` 根路径、Windows drive prefix、UNC、反斜杠、空 segment、`.` 与 `..`。
- 正式 reader 必须在 allowlisted root 下做 normalized containment check，并禁止 symlink/reparse-point escape；
  本轮没有实现 reader。

### 6.3 Digest canonicalization

1. 按 `logicalName`，再按 `relativePath` 做升序排序。
2. 每项按以下字段顺序拼接：`logicalName`、`relativePath`、lowercase `sha256`、十进制 `sizeBytes`、`mediaType`。
3. 字段分隔符为 U+001F，记录分隔符为 LF；末尾不增加 LF。
4. 对 UTF-8 bytes 计算 SHA-256，输出 64 位小写十六进制 `artifactDigest`。

逐文件 SHA-256 绑定文件内容；aggregate digest 绑定文件索引与 metadata。它们都不证明策略质量、收益、审批或授权。

### 6.4 Fail-closed JSON policy

顶级与固定对象 `additionalProperties=false`。开放 `parameters` 只允许 bounded scalar values，并递归拒绝大小写、下划线等变体的：

`apiKey`、`api_key`、`secret`、`passphrase`、`token`、`accessToken`、`privateKey`、`credentialMaterial`、
`decryptedPayload`、`rawPrivateRequest`、`rawPrivateResponse`、`cookie`、`authorization`。

仅显式允许边界字段：`noCredentialAccess`、`noPrivateEndpoint`、`diagnosticOnly`、`notTradingAuthorization`。

## 7. Strategy Release Lifecycle

| 状态 | 合法前置 | 合法后继 | Terminal | 触发动作 | 幂等语义 | 审计事件 | 非法流转结果 |
|---|---|---|---|---|---|---|---|
| `DRAFT` | 创建 | `CANDIDATE`, `REJECTED` | 否 | `CREATE_DRAFT` | create/action ID 返回首次结果 | `RELEASE_DRAFT_CREATED` | 未声明目标返回 `RELEASE_ILLEGAL_STATE_TRANSITION`，状态不变 |
| `CANDIDATE` | `DRAFT` | `VERIFIED`, `REJECTED` | 否 | `SUBMIT_CANDIDATE` | action ID 绑定同一目标；重复返回首次结果 | `RELEASE_CANDIDATE_SUBMITTED` | 跳过验证发布返回 `RELEASE_NOT_VERIFIED` |
| `VERIFIED` | `CANDIDATE` | `PUBLISHED`, `REJECTED` | 否 | `VERIFY_MANIFEST` | 重复 verification action 返回首次 digest/result | `RELEASE_MANIFEST_VERIFIED` | manifest/digest 不自洽则 fail-closed，状态不变 |
| `PUBLISHED` | `VERIFIED` | `RETIRED` | 否 | `PUBLISH_RELEASE` | publish action ID 重放不重复发布 | `RELEASE_PUBLISHED` | 未 `VERIFIED` 返回 `RELEASE_NOT_VERIFIED` |
| `REJECTED` | `DRAFT`, `CANDIDATE`, `VERIFIED` | 无 | 是 | `REJECT_RELEASE` | 重放返回首次 rejection | `RELEASE_REJECTED` | 返回 `RELEASE_TERMINAL_STATE_LOCKED` |
| `RETIRED` | `PUBLISHED` | 无 | 是 | `RETIRE_RELEASE` | 重放返回首次 retire result | `RELEASE_RETIRED` | 返回 `RELEASE_TERMINAL_STATE_LOCKED` |

同一 action ID 若绑定不同目标，返回 `RELEASE_ACTION_ID_CONFLICT`，不改变状态、不覆盖首次结果。非法流转也必须产生脱敏审计事件。

## 8. Shadow Run Lifecycle（复用现有状态机）

| 状态 | 合法前置 | 合法后继 | Terminal | 触发动作 | 幂等语义 | 审计事件 | 非法流转结果 |
|---|---|---|---|---|---|---|---|
| `CREATED` | 幂等创建 | `PRECHECKING`, `FAILED`, `CANCELLED` | 否 | create/precheck | `shadow_runs.idempotency_key` 返回同一 run | `CREATED` / `PRECHECK_STARTED` | `SHADOW_RUN_ILLEGAL_STATE_TRANSITION` |
| `PRECHECKING` | `CREATED` | `READY`, `BLOCKED`, `FAILED` | 否 | precheck | expected version 防并发覆盖 | `PRECHECK_PASSED` / `PRECHECK_BLOCKED` | fail-closed，主表不更新 |
| `READY` | `PRECHECKING` | `RUNNING`, `FAILED`, `CANCELLED` | 否 | start | request/action ID 与 expected version 去重 | `RUN_STARTED` | fail-closed，主表不更新 |
| `RUNNING` | `READY` | `STOP_REQUESTED`, `COMPLETED`, `BLOCKED`, `FAILED` | 否 | run/stop/complete | version + append-only event | `STOP_REQUESTED` / `COMPLETED` / `FAILED` | fail-closed，记录非法尝试 |
| `STOP_REQUESTED` | `RUNNING` | `STOPPED`, `FAILED` | 否 | stop | 重复 stop 不重复推进 | `STOPPED` / `FAILED` | fail-closed |
| `STOPPED` | `STOP_REQUESTED` | 无 | 是 | 无 | 重放返回终态事实 | `STOPPED` | `SHADOW_RUN_TERMINAL_STATE_LOCKED` |
| `COMPLETED` | `RUNNING` | 无 | 是 | 无 | 重放返回终态事实 | `COMPLETED` | `SHADOW_RUN_TERMINAL_STATE_LOCKED` |
| `BLOCKED` | `PRECHECKING`, `RUNNING` | 无 | 是 | 无 | 重放返回终态事实 | `PRECHECK_BLOCKED` 或阻断事件 | `SHADOW_RUN_TERMINAL_STATE_LOCKED` |
| `FAILED` | 多个非终态 | 无 | 是 | 无 | 重放返回终态事实 | `FAILED` | `SHADOW_RUN_TERMINAL_STATE_LOCKED` |
| `CANCELLED` | `CREATED`, `READY` | 无 | 是 | cancel | 重放返回终态事实 | `CANCELLED` | `SHADOW_RUN_TERMINAL_STATE_LOCKED` |

固定不变量：

- Release 与 Shadow 是两个 aggregate、两个状态机、两套审计事件。
- `release verified != shadow started`；验证 release 不得隐式创建或推进 `shadow_runs`。
- `shadow completed != LIVE authorized`；任何 Shadow 终态都不产生订单、账户或授权状态。
- terminal 状态不得恢复；retry 必须创建新 action / 新 run，并保留旧事实。

## 9. Schema proposal 摘要

`STRATEGY_RELEASE_SCHEMA_PROPOSAL.sql` 仅提出：

- `REUSE`：`strategy_versions`、`shadow_runs` 四表。
- `EXTEND`：以 `backtest_publish_records` 为唯一 publish anchor，候选增加 manifest/lifecycle 字段。
- `NEW`：append-only release event、artifact file index、artifact verification facts。
- `DEFER`：`risk_limit_sets`。

所有 DDL 都处于块注释中，不是 Flyway migration，不得执行。正式 GateX 需要独立 DDL、锁表/backfill、security、retention、rollback review。

## 10. 风险与已知限制

- 当前 JSON Schema 没有接入 production validator；test-only helper 不是正式 runtime implementation。
- 本轮没有读取实际 artifact 文件，也没有实现 trusted root、symlink/reparse-point 或 TOCTOU 防护。
- 当前 Python checksum 与本草案 aggregate digest 是两个层级，正式实现必须明确两者都保留且不可互相冒充。
- SQL 只表达候选结构，未在任何数据库运行；索引、表规模、历史 backfill 与锁风险未实测。
- Python 依赖许可证为已知上游口径，正式引入前仍需在 GateX 复核版本、NOTICE、SBOM 与漏洞。
- GateW soak 尚未接受；本 preparation 不能成为 GateX 开始、dev merge 或 LIVE 的依据。

## 11. 下一动作

`PREPARATION_BRANCH_HOLD / NO_DEV_MERGE`。

只有 current authority 允许 GateX 后，才可创建独立 implementation work order，优先顺序为：manifest/security review →
artifact reader threat review → forward-only migration review → production implementation → contract/integration tests。每一步都不得把 verification 或 Shadow 结果升级为交易授权。
