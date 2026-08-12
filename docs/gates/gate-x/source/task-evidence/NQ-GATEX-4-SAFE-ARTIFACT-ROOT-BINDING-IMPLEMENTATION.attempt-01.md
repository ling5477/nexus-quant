# GateX-4 Safe Artifact Root Binding Implementation（attempt-01）

## 1. Task classification 与执行状态

- 任务：`NQ-GATEX-4-SAFE-ARTIFACT-ROOT-BINDING-IMPLEMENTATION`。
- 分类：NQ-only、P1 security remediation / backend implementation preflight / server-controlled artifact binding audit。
- 执行状态：`BLOCKED / PERSISTENT_ARTIFACT_LOCATOR_REQUIRED / NO_UNSAFE_PATH_FALLBACK`（阻断 / 需要持久化 locator / 无不安全路径回退）。
- 原 `SAFE_ARTIFACT_ROOT_BINDING_MISSING` 未关闭；本轮把缺失能力的根因收敛为必须先建立可审查的持久化 artifact locator 事实。

## 2. Starting baseline

- branch=`dev`。
- starting `HEAD == origin/dev == 5f4824eecaac5cffbbc314fb8f767bd6ba45c29f`。
- authority before：GateX-3=`ACCEPTED|CI_GREEN / 5f4824e... / 31391541813`；GateX-4=`BLOCKED / NONE / NOT_RUN`；LIVE=`DISABLED`。
- 特殊 staged baseline：仅有上一轮允许的 7 个 blocker/current docs；无其他 dirty/staged path。
- 原 blocker evidence 保留：[NQ-GATEX-4-MINIMAL-API-UI-CLOSURE-IMPLEMENTATION.attempt-01.md](NQ-GATEX-4-MINIMAL-API-UI-CLOSURE-IMPLEMENTATION.attempt-01.md)。

## 3. Artifact storage inventory

### 3.1 五项必答

1. 当前是否存在 server-owned artifact locator：不存在。
2. publish record 是否已有稳定 artifact location metadata：不存在。
3. Python/research 是否已有固定 artifact 输出目录：不存在。
4. 是否存在可复用的 production manifest 文件命名/layout：不存在；仅存在 test-only golden/schema。
5. 是否存在 server configuration 定义 trusted artifact root：不存在。

### 3.2 精确证据

- `BacktestPublishRecord` 只包含 publish/run/config/strategy/evaluation identity、三类 snapshot、状态/失败与时间字段，无 locator、URI、storage key、artifact path 或 manifest path。
- `JdbcBacktestPublishRecordRepository` 的 INSERT/SELECT/mapper 与表字段一致，没有被隐藏或未映射的 artifact location metadata。
- `BacktestPublishService.publishSnapshotJson(...)` 只写 `sourceBacktestRunId`、`publishName`、`targetStrategyDefinitionId`、strategy code/type 与 snapshot version；version/evaluation snapshot 同样不携带 artifact location。
- Python `write_evaluation_artifact(path, ...)` 的 `Path` 来自 caller，并执行 `path.parent.mkdir(...)`；这证明是通用离线 writer，不是 server-owned fixed root/layout，也没有 publish identity binding。
- `strategy-release-manifest.golden.json` 与 schema 只位于 `nq-core/src/test/resources/gatex`；production source/resources 没有固定 manifest filename 或目录 layout。
- production `@ConfigurationProperties` 仅覆盖账户凭证、安全和 validation scheduler；application profiles 没有 artifact storage/path/root 配置。
- `PythonEvaluationArtifactBindingPreviewController` 明确只校验 request JSON，不读本地 artifact file、不导入或写库，不能作为 production locator。

## 4. Selected implementation path

选择：`C / PERSISTENCE REQUIRED`。

- A 不成立：无 production locator/metadata 可复用。
- B 不成立：无稳定 filesystem convention、fixed manifest filename 或 server root 可证明；本任务禁止先发明 layout 再宣称已有 artifact 可用。
- C 成立：安全建立 `publishRecordId → artifact source` 必须新增 `artifact_locator`、`manifest_locator`、`artifact_uri` 或 storage key 等持久化事实，并通过 migration/schema review 固化唯一性、immutability、identity binding 与回滚。

任务明确禁止本轮偷加 migration，因此在任何 product code 写入前 fail-fast。

## 5. Security design status

- Server-controlled root design：`NOT IMPLEMENTED`（未实现）；缺少 locator fact 前不能安全选择 root/location。
- Configuration design：`NOT IMPLEMENTED`；没有添加 working-directory、user.home、temp 或 current-directory fallback。
- Locator contract：`NOT IMPLEMENTED`；避免创建无法由生产事实支持的抽象。
- Manifest loading contract：`NOT IMPLEMENTED`；没有把客户端 JSON/path/digest 提升为 production truth。
- Release identity binding：`BLOCKED`；没有把相同 digest 当 locator，也没有允许 release A 读取 release B 目录。
- Path security / symlink / reparse：未声称实现；没有 unsafe path surface 被新增。
- Sensitive-path protection：通过不新增 locator/path/logging code 保持；文档不记录任何本机 absolute artifact path。

## 6. GateX integration boundary

- GateX-1 verifier：未修改；`TrustedRootStrategyArtifactVerifier` 仍是唯一 content/path verification contract。
- GateX-3 admission：未修改；没有构造 server-resolved `StrategyRelease`，没有调用 admission 生成 plan。
- `ELIGIBLE != ShadowRunCreated != TradingAuthorized` 继续成立。
- HTTP API/UI、Shadow Run、scheduler、runner、repository create、交易/LIVE 均未新增。

## 7. Files inspected

- `backend/nq-core/**/strategyrelease/**` production verifier/service/model 与 tests/resources。
- `backend/nq-research/**/BacktestPublishRecord.java`、`BacktestPublishService.java`。
- `backend/nq-infra/**/JdbcBacktestPublishRecordRepository.java` 与 publish migrations/comments。
- `backend/nq-api/**/PythonEvaluationArtifactBindingPreviewController.java`。
- `backend/nq-app/src/main/resources/application-*.yml` 与 production `@ConfigurationProperties`。
- `research/py/src/nq_research/evaluation/artifacts.py`。
- `docs/current/**` 与 `docs/drafts/pre-gatex/**` 相关 artifact contract/evidence。

## 8. Files created / changed

- 新增本 evidence。
- 追加 `TESTING.md`、`WORKLOG.md`。
- 将 `STATUS.md`、`ROADMAP.md`、root/current README 的 GateX-4 blocker 从一般 root binding 缺失收敛为 `PERSISTENT_ARTIFACT_LOCATOR_REQUIRED`。
- Backend/API/frontend/research/migration product code changes：0。

## 9. Validation

| Command / Check | Result | Scope / Environment / Warning |
| --- | --- | --- |
| Git + special staged baseline | PASS（通过） | 仅允许的 staged blocker/current docs；`HEAD == origin/dev` |
| current authority preflight | PASS（通过） | `errors=0`；GateX-3 accepted、GateX-4 blocked、LIVE disabled |
| required `rg` storage/config/manifest audit | BLOCKED（阻断） | 选择 C；未找到支持 A/B 的 production fact |
| focused Maven | NOT RUN（未运行） | product code diff=0；选择 C 后按任务立即停止 |
| full backend regression | NOT RUN（未运行） | 同上 |
| PackageBoundaryArchTest / ModuleBoundaryArchTest | NOT RUN（未运行） | 同上 |
| authority checker | PASS（通过） | GateX-4=`BLOCKED`、next action 为 persistent-locator blocker；`errors=0` |
| docs link checker | PASS WITH WARNING（通过但有警告） | 202 links checked、0 errors、1 个既有 `GATEJ_TEST_PLAN.md` warning |
| tracked diff check | PASS（通过） | whitespace errors=0；仅既有 LF→CRLF warning |

## 10. Impact 与 findings

- Persistence requirement：`YES / SCHEMA REVIEW REQUIRED`（需要 / 需要 schema review）；本轮 migration impact=0。
- API impact：0。
- Frontend impact：0。
- Shadow impact：0。
- Trading/LIVE impact：0；LIVE=`DISABLED`。
- Credential/private endpoint impact：0。
- P0：无。
- P1：1 个未关闭——安全 artifact binding 需要持久化 locator；阻断本轮实现与 review/commit authorization。
- P2：无新增。
- P3：工程语义 MCP 未暴露，按规则降级到 PowerShell + `rg`；直接读取 source/schema/config，结论可信度高。

## 11. Authority after 与 evidence chain

```text
accepted_batch=GateX-3
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateX-4
work_batch_status=BLOCKED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED
```

- Original blocker status：未关闭，不回写为成功。
- Remediation result：`BLOCKED / PERSISTENT_ARTIFACT_LOCATOR_REQUIRED / NO_UNSAFE_PATH_FALLBACK`。
- Evidence chain：GateX-4 minimal API/UI attempt-01 blocker → 本 safe-root remediation attempt-01 root-cause refinement。
- Independent review requirement：先单独执行 GateX-4A schema review；本任务没有达到 `IMPLEMENTED|PENDING_REVIEW`，不能进入原安全 review action。
- Staged scope：保留原 7 个 blocker/current docs，并追加本 evidence/current refinement；无 product code。
- Commit recommendation：当前仍有 P1 blocker，不建议 commit；若未来 schema review/implementation 合并完成，再使用 `feat(strategy): bind releases to server-controlled artifacts`。
- Rollback：删除本 evidence，移除本轮 ledger 追加，并将 current blocker 文案/next action恢复到上一轮 staged baseline；不得用 `git reset --hard`。
- Next action：另行发起 `NQ-GATEX-4A-PERSISTENT-ARTIFACT-LOCATOR-SCHEMA-REVIEW`，但 machine work batch 继续保持 GateX-4 BLOCKED，未初始化新 batch。
- Final decision：`BLOCKED / PERSISTENT_ARTIFACT_LOCATOR_REQUIRED / NO_UNSAFE_PATH_FALLBACK`。
