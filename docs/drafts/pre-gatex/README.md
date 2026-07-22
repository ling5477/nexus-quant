# PRE-GATEX Research-to-Shadow Contract Preparation

状态：`PRE-GATEX PREPARATION / UNMERGED`（GateX 前准备 / 未合并）。

本目录只保存 `NQ-PRE-GATEX-RESEARCH-TO-SHADOW-CONTRACT-PREPARATION-ATTEMPT-02` 的候选合同、
非 Flyway schema proposal、依赖评估和自审记录。它不是 `docs/current` authority，不启动 GateX，
也不改变 GateW soak、Freeze、LIVE 或交易授权。

## Authority snapshot

读取来源：`docs/current/STATUS.md` 顶部 `nq-current-authority` 区块与正文；本目录不修改该来源。

- GateW：`IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- Attempt-09：`RUNNING / PENDING_168H`（运行中 / 待满 168 小时）。
- `plannedAcceptanceAt` raw UTC：`2026-07-29T11:19:59.5201964Z`。
- GateX：`NOT_STARTED`（未开始）。
- LIVE：`DISABLED`（已禁用）。

## 文件索引

| 文件 | 角色 | 可执行性 |
|---|---|---|
| `RESEARCH_TO_SHADOW_CONTRACT_PREPARATION.md` | 真实链路审计、五项决策、最小强契约与双状态机设计 | 只读草案 |
| `STRATEGY_RELEASE_SCHEMA_PROPOSAL.sql` | 候选 schema 评审文本 | 全部 DDL 位于块注释中；不是 migration，不得执行 |
| `PYTHON_DEPENDENCY_ASSESSMENT.md` | 六项 Python 依赖的分阶段决策 | 未安装、未改 `pyproject.toml` 或 lock |
| `NQ-PRE-GATEX-RESEARCH-TO-SHADOW-CONTRACT-PREPARATION-ATTEMPT-02.md` | 本 attempt 的证据、自审与验证结果 | preparation branch 记录 |
| `backend/nq-core/src/test/resources/gatex/*.json` | JSON Schema 与虚构 golden fixture | test-only |
| `backend/nq-core/src/test/java/**/strategyrelease/preparation/*.java` | manifest、双生命周期、敏感字段原型测试 | test-only |

## 决策摘要

| 概念 | 决策 | 摘要 |
|---|---|---|
| Strategy Release | `EXTEND` | 扩展既有 `strategy_versions` + `backtest_publish_records` 主链，不另建平行 publish 主链。 |
| Strategy Artifact | `EXTEND` | 复用 Python evaluation artifact 与 Java binding preview 的安全语义，补正式 manifest、file digest 和路径合同。 |
| Shadow Session | `REUSE` | `shadow_runs`、events、snapshots、consistency reports 已承担 session 语义；不新增 `shadow_sessions`。 |
| Risk Limit Set | `DEFER` | GateX 先固化 manifest 内不可变 `riskBudget` snapshot；尚无独立持久化集合的必要性证据。 |
| Artifact Verification | `NEW` | 需要 append-only 的实际文件 digest 重算事实；它只证明完整性，不表达审批或交易授权。 |

## 固定边界

- `release verified != shadow started`。
- `shadow completed != LIVE authorized`。
- manifest / checksum valid 只表示合同与完整性自洽，不表示收益真实、策略批准或 execution ready。
- 不读取 credential material，不调用 private endpoint，不访问真实交易所，不下单、不撤单、不转账、不提现。
- 不修改 production Java、Python、migration、POM、frontend、deploy、CI 或 `docs/current/**`。

## 验证入口

在 preparation worktree 根目录执行：

```powershell
python -m json.tool backend/nq-core/src/test/resources/gatex/strategy-release-manifest.schema.json > $null
python -m json.tool backend/nq-core/src/test/resources/gatex/strategy-release-manifest.golden.json > $null

Set-Location backend
mvn -pl nq-core -am "-Dtest=StrategyReleaseManifestPrototypeTest,StrategyReleaseLifecyclePrototypeTest,SensitiveFieldPolicyPrototypeTest" -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl nq-core -am test
Set-Location ..
```

## Handoff

完成准备与自审后仍保持：`PREPARATION_BRANCH_HOLD / NO_DEV_MERGE`。只有 GateW 按 current authority 完成、
GateX 获得独立授权、schema/security review 通过后，才可把本目录的候选项拆成正式实施任务；不得直接执行 SQL 草案。
