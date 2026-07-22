# NQ-PRE-GATEX-RESEARCH-TO-SHADOW-CONTRACT-PREPARATION — Attempt 02

## Task classification

- 归属：NQ-only。
- 模式：`RESUME_AFTER_CONTEXT_LOSS`。
- 类型：`WORKTREE_SETUP / EXISTING_CHAIN_AUDIT / CONTRACT_PROTOTYPE / TEST_ONLY_IMPLEMENTATION / NON_FLYWAY_SCHEMA_PROPOSAL / PYTHON_DEPENDENCY_ASSESSMENT / SELF_REVIEW`。
- 风险等级：L；涉及未来 release/shadow/schema/security contract，但本轮严格限制为 preparation branch、test-only 与草案。
- Router：`nq-dh-workflow-router`；主 skill：`java-backend-regression-tests`；辅助 skill：`nq-docs-writer`。

## Execution status

`PREPARED / SELF_REVIEWED / READY_TO_COMMIT_ON_PREP_BRANCH`（已准备 / 已自审 / 可在准备分支进入提交前复核）。

## Main worktree

- 路径：`E:\Project\nexus-quant`。
- 分支：`dev`。
- 前置检查：working tree clean、staged empty。
- 本任务写入：0；任务结束前复核仍应为 clean。

## Preparation worktree

`E:\Project\nexus-quant-pre-gatex`；独立 worktree 已创建并复用，所有任务产物只存在于此 worktree。

## Preparation branch

`prep/gatex-research-to-shadow`。

## Starting HEAD

`ea28c9e4a3664e89cba35bb7126d765647f34845`。

## origin/dev HEAD

`ea28c9e4a3664e89cba35bb7126d765647f34845`；preparation 起点与 `origin/dev` 对齐。

## GateW status

- GateW：`IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- Work batch：`GateW-OKX-READONLY-SOAK-ATTEMPT-09`。
- Attempt-09：`RUNNING / PENDING_168H`（运行中 / 待满 168 小时）。
- GateW-FREEZE：`NOT_STARTED`（未开始）。
- GateX：`NOT_STARTED`（未开始）。

## plannedAcceptanceAt raw UTC

`2026-07-29T11:19:59.5201964Z`，原样读取自 `docs/current/STATUS.md`；未硬编码或替换为本地时间。

## Authority modifications

无。未修改 `docs/current/**`、GateW evidence、GateW runtime、soak、Freeze、archive、tag 或 next action。

## Files inspected

- 规则与 authority：`AGENTS.md`、`CLAUDE.md`、`README.md`、`docs/current/README.md`、`docs/current/STATUS.md` 及 Router/skill 指定入口。
- Dataset / traceability：`V18__gate_h3_marketdata_dataset_binding.sql`、`V19__gate_i1_strategy_versions.sql`、`V20__gate_i2_backtest_traceability.sql`。
- Backtest / evaluation / publish：`V7__gate_f1_research_backtest_skeleton.sql`、`V9__gate_f4_evaluation_reports.sql`、`V10__gate_f5_publish_records.sql`、`BacktestPublishService.java`。
- Strategy version：`StrategyVersionService.java` 与现有 tests。
- Python artifact：`research/py/pyproject.toml`、`research/py/src/nq_research/evaluation/artifacts.py` 与相关 tests。
- Java preview：`PythonEvaluationArtifactBindingService.java`、`PythonEvaluationArtifactPreviewOverviewQueryService.java` 与相关 tests。
- Shadow：`V32__gate_r_shadow_run_fact_model.sql`、`ShadowRunStatus.java`、`ShadowRunStateMachine.java` 与现有 tests。
- Maven：`backend/pom.xml`、`backend/nq-core/pom.xml`。

工具降级披露：`idea-mcp` 不识别新 worktree 为已打开项目，因此在同一 starting HEAD 的主项目使用 `idea-mcp` 做结构定位，
并在 `E:\Project\nexus-quant-pre-gatex` 使用 PowerShell/`rg` 读取实际文件。检索范围仅 preparation worktree 与任务附件；
可信度高，因为两个 worktree 起始 HEAD 相同，且所有写后验证都在 preparation worktree 实际执行。

## Existing chain audit matrix

| 链路 | 当前事实 | 缺失的最小合同 | 决策 |
|---|---|---|---|
| Dataset | UUID dataset、coverage、backtest dataset snapshot 已存在。 | release 级 `dataWindow/datasetHash/featureDefinitionVersion` 绑定。 | 复用 identity，manifest 扩展。 |
| Strategy Version | `strategy_versions` 与 `sv-<UUID>` opaque ID 已存在。 | 不把 domain ID 误写成 UUID；绑定 artifact manifest。 | `REUSE` identity。 |
| Backtest | config/run 固化 version、dataset、params/config snapshots。 | artifact file index/digest。 | 复用追溯事实。 |
| Evaluation | report/metrics 与 run 绑定。 | `evaluationId` 进入 manifest，但评估成功不等于 release verified。 | 复用 evaluation fact。 |
| Publish | 按 run 幂等、成功前置与 version/evaluation/publish snapshots 已存在。 | release lifecycle、manifest、实际 file verification。 | Strategy Release `EXTEND`。 |
| Python Artifact | `python-evaluation-artifact.v1`、canonical checksum、安全字段与 offline boundary 已存在。 | 多文件 manifest/path/hash/aggregate digest。 | Strategy Artifact `EXTEND`。 |
| Java Preview | caller expected checksum 比较与 No-file baseline 已存在。 | 受控文件读取、真实 digest 重算、append-only verification。 | Artifact Verification `NEW`。 |
| Shadow Run | `shadow_runs` 四表、状态机、幂等、乐观锁、no-side-effect flags 已存在。 | 仅需后续绑定 verified release fact；不能复制 session。 | Shadow Session `REUSE`。 |
| Consistency Report | 本地 Paper vs Shadow 差异/限制事实已存在。 | 保持 review-only，不产生 authorization。 | `REUSE`。 |

完整证据和逐项判断见 `RESEARCH_TO_SHADOW_CONTRACT_PREPARATION.md`。

## REUSE / EXTEND / NEW / DEFER decisions

| 概念 | 决策 | 结论 |
|---|---|---|
| Strategy Release | `EXTEND` | 扩展 `strategy_versions` + `backtest_publish_records` 主链，不建立平行 publish source of truth。 |
| Strategy Artifact | `EXTEND` | 复用 Python artifact / Java preview 安全语义，增加正式 file manifest contract。 |
| Shadow Session | `REUSE` | 复用 `shadow_runs`、events、snapshots、consistency reports；不新增 `shadow_sessions`。 |
| Risk Limit Set | `DEFER` | GateX 先使用 immutable `riskBudget` snapshot；不新增 `risk_limit_sets`。 |
| Artifact Verification | `NEW` | 新 append-only 实际 file/digest verification fact；验证不是授权。 |

## Manifest contract summary

- `schemaVersion=strategy-release-manifest.v1`。
- `strategyVersionId/evaluationId` 为 opaque domain ID；`datasetId` 为 UUID。
- UTC 时间只接受 `Z`；SHA-256 为 64 位小写十六进制；金额、权重、精度值使用十进制字符串。
- `artifactFiles` 固定 `logicalName/relativePath/sha256/sizeBytes/mediaType`；路径只允许 `/` 相对路径。
- aggregate digest 按 `logicalName + relativePath` 排序，U+001F 分字段、LF 分记录，UTF-8 SHA-256。
- 顶级和固定对象 `additionalProperties=false`；开放参数有字段名、数量、类型与长度限制。
- 固定 `noCredentialAccess/noPrivateEndpoint/diagnosticOnly/notTradingAuthorization=true`。

## Schema proposal summary

- SQL 文件首部包含四条 mandatory warning。
- 全部候选 DDL 位于块注释内，执行文件不会产生 DDL side effect。
- `[REUSE]`：`strategy_versions` 与 Shadow 四表。
- `[EXTEND]`：`backtest_publish_records` 作为唯一 publish/release anchor。
- `[NEW]`：release lifecycle event、artifact file index、artifact verification 候选。
- `[DEFER]`：`risk_limit_sets`。
- 未创建 `Vxx__*.sql`、正式 migration、`live_sessions`、execution intent/receipt 或 scoped LIVE credential。

## Lifecycle decision

- Strategy Release：`DRAFT -> CANDIDATE -> VERIFIED -> PUBLISHED -> RETIRED`；`REJECTED/RETIRED` terminal。
- Shadow Run：直接复用 `CREATED/PRECHECKING/READY/RUNNING/STOP_REQUESTED` 与既有 terminal 状态。
- 每个 release action ID 缓存首次结果；同 ID 同请求返回首次对象，同 ID 不同目标返回 `RELEASE_ACTION_ID_CONFLICT`。
- 非法 release 流转返回明确 reason 且状态不变；Shadow 非法流转复用现有 reason-coded exception。
- 固定：`release verified != shadow started`，`shadow completed != LIVE authorized`。

## Python dependency decisions

| 依赖 | 推荐结论 |
|---|---|
| pandas | `INTRODUCE_IN_GATEX`（条件性、offline-only） |
| polars | `DO_NOT_INTRODUCE` |
| DuckDB | `DEFER_TO_GATEY` |
| statsmodels | `DEFER_TO_GATEY` |
| scikit-learn | `DEFER_TO_GATEZ` |
| PyPortfolioOpt | `DEFER_TO_GATEZ` |

本轮 installation=0、`pyproject.toml` modifications=0、lock modifications=0；Java 只接受 JSON/file digest contract，
不加载 Python object，不将任何候选依赖放入在线路径。

## Prototype implementation

- JSON Schema + 虚构 golden sample。
- 无新增依赖的 test-only manifest validator 与 deterministic digest calculator。
- 相对路径、UUID、UTC、SHA-256、必填/未知字段、digest match/mismatch tests。
- test-only Strategy Release 状态机；复用 production Shadow Run 状态机做隔离和 terminal contract tests。
- recursive sensitive-field policy，覆盖大小写/下划线变体并允许四个安全边界字段。
- 未新增 Maven module；未修改任何 POM。

## Files created

1. `docs/drafts/pre-gatex/README.md`
2. `docs/drafts/pre-gatex/RESEARCH_TO_SHADOW_CONTRACT_PREPARATION.md`
3. `docs/drafts/pre-gatex/STRATEGY_RELEASE_SCHEMA_PROPOSAL.sql`
4. `docs/drafts/pre-gatex/PYTHON_DEPENDENCY_ASSESSMENT.md`
5. `docs/drafts/pre-gatex/NQ-PRE-GATEX-RESEARCH-TO-SHADOW-CONTRACT-PREPARATION-ATTEMPT-02.md`
6. `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.schema.json`
7. `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.golden.json`
8. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseManifestPrototypeTest.java`
9. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseLifecyclePrototypeTest.java`
10. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/SensitiveFieldPolicyPrototypeTest.java`

## Files changed

无既有文件修改；仅新增上列 10 个 allowlist 文件。

## Tests executed

| 命令 | 结果 |
|---|---|
| `scripts/docs/check-current-authority.ps1`（主仓库前置） | PASS / `CURRENT_AUTHORITY_CONSISTENT` |
| 两个 `python -m json.tool ... > $null` | PASS，exit 0 |
| 首次定向 Maven（第二个 `-D` 未整体引号） | FAIL，exit 1；PowerShell 参数转义导致 `Unknown lifecycle phase`，未进入编译/测试 |
| 修正参数引号后的定向 Maven | PASS；13 tests，0 failures/errors/skipped |
| 消除新增 Jackson deprecation warning 后再次定向 Maven | PASS；13 tests，0 failures/errors/skipped |
| `mvn -pl nq-core -am test` | PASS；reactor BUILD SUCCESS |
| allowlist 精确比较 | PASS；expected=10、actual=10 |
| `git diff --cached --check` + cached name/scope 复核 | PASS；仅 10 个 allowlist 文件，unstaged/untracked 均为空 |

## Test results

- 定向 prototype：13 tests，0 failures，0 errors，0 skipped。
- 模块回归：`nq-contracts` 1 + `nq-risk` 11 + `nq-core` 359 = 371 tests，0 failures，0 errors，0 skipped。
- 构建：5-module reactor 全部 `SUCCESS`。
- 非阻断既有运行提示：SLF4J 未发现 provider，回退 NOP logger；不影响测试结论。

## Security boundary verification

- 敏感字段 required list 及大小写/下划线变体均有负向测试；递归遍历 object/array。
- golden 只含虚构 ID、hash、金额和描述；未读取或写入任何真实 credential value。
- 路径负向覆盖 `..`、Unix absolute、Windows drive、UNC 与反斜杠。
- 顶级 unknown field 与 missing required field fail-closed。
- 本轮没有 file reader、network client、DB connection、subprocess integration 或外部写接口。

## Trading boundary verification

- 未访问真实交易所、private endpoint、账户或 credential。
- 未下单、撤单、转账、提现，未启动 Paper/Shadow/soak，未操作 GateW 服务器。
- `PrototypeTradingBoundary.liveAuthorized(...)` 对所有 release/shadow 组合固定 false。
- LIVE 保持 `DISABLED`；GateX 保持 `NOT_STARTED`。

## P0 findings

无。

## P1 findings

无。

## P2 findings

1. 当前 Java preview 不读取真实 artifact files，也不重算 digest；正式 GateX 若没有独立 trusted-root verifier，不能把 preview 升级为 verification。
2. SQL 仅为未执行 proposal；历史行语义、锁表/backfill、索引规模、retention 与 rollback 尚需正式 schema/security review。
3. artifact reader 的 symlink/reparse-point escape、TOCTOU、总大小/文件数、原子读取与 cleanup 尚未实现；在实现前必须保持 No-file baseline。

这些是后续 GateX hardening 项，不阻断本次 preparation 交付，但阻断直接转为 production implementation。

## P3 findings

1. Python 许可证为已知上游口径，选定实际版本后仍需联网或使用内部制品元数据复核 LICENSE/NOTICE、SBOM 与 transitive solver 条款。
2. 全量测试存在既有 SLF4J NOP logger 提示；本任务未新增 runtime logging dependency，不在本轮范围。

## Known limitations

- JSON Schema 未由 production JSON Schema engine 执行；test-only validator 只覆盖本任务规定合同。
- 没有 runtime import、trusted filesystem root、artifact catalog 或 DB persistence。
- 没有验证任意真实策略收益、数据集内容或文件；golden 全部为 synthetic fixture。
- 未执行数据库 DDL；proposal 不代表 migration 可接受。
- 未运行全后端 `mvn -f backend/pom.xml test`；按任务要求运行 `nq-core -am` 范围，原因是生产代码未修改且 scope 固定。
- 未运行 frontend/Python test suite；本轮未修改对应代码，且任务明确禁止扩大范围。

## Production code status

`UNCHANGED`。`backend/**/src/main/**`、frontend、research Python source、deploy、CI 均未修改。

## Migration status

`NO MIGRATION`。未修改历史 migration，未创建 `Vxx__*.sql`；唯一 `.sql` 是块注释包裹的非 Flyway proposal，未执行。

## GateW impact

无。GateW remains `IN_PROGRESS / NOT_FROZEN`；Attempt-09 remains `RUNNING / PENDING_168H`；
本任务未做最终验收、Freeze、archive、tag、runtime/soak/authority/evidence 修改。

## Commit recommendation

允许人工在 preparation branch 完成 cached diff 复核后提交；建议 commit message：

```text
test(gatex): 准备 research-to-shadow 合同原型
```

本任务不自动 commit。

## Push recommendation

`DO NOT PUSH YET`（暂不推送）。先保持 preparation branch hold，由人工审查 10 文件与后续 GateX authority。

## Merge recommendation

`NO DEV MERGE`。GateW 尚未冻结，GateX 尚未开始；不得创建面向 `dev` 的 PR 或合并。

## Next action

`PREPARATION_BRANCH_HOLD / NO_DEV_MERGE`。

## Final decision

`PREPARED / SELF_REVIEWED / READY_TO_COMMIT_ON_PREP_BRANCH`。

- GateW remains `IN_PROGRESS / NOT_FROZEN`。
- GateX remains `NOT_STARTED`。
- Preparation branch remains `UNMERGED`。
- `dev` remains unchanged。
- LIVE remains `DISABLED`。
