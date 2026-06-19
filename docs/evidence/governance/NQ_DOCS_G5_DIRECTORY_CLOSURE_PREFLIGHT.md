# NQ Docs Governance G5 Directory Closure Preflight

任务：`NQ-DOCS-GOVERNANCE-G5-DIRECTORY-CLOSURE-PREFLIGHT`

日期：2026-06-19

状态：**IMPLEMENTED / READY FOR REVIEW**

本文只读核验 G5 directory closure 的候选、链接兼容性、target conflict、冻结边界和未来回滚设计。本轮只新增本文并追加 `STATUS.md` / `TESTING.md` / `WORKLOG.md` 记录；**未移动、删除、重命名、复制、归档、stub 化任何既有文档，未创建 target 目录或 canonical 文件**。

---

## 1. Scope And Frozen Boundary

### 1.1 当前冻结事实

```text
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = FROZEN / ACCEPTED
G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED
G4 CI evidence routing = FROZEN / ACCEPTED
G5 directory closure preflight = IMPLEMENTED / READY FOR REVIEW
G6 deletion batch = NOT STARTED / DEFAULT EMPTY
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```

### 1.2 允许与禁止范围

允许：

- 新增 `docs/current/NQ_DOCS_G5_DIRECTORY_CLOSURE_PREFLIGHT.md`。
- 追加 `docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`。
- 只读核验 `docs/current/NQ_DOCS_MIGRATION_MAP.md`、git 状态、候选 source / target / inbound link / fragment / protected diff。

禁止：

- 禁止移动、删除、重命名、复制、归档、stub 化任何既有文档。
- 禁止创建 `docs/**` 新目录或新增 target location 文件。
- 禁止修改 G1 五份冻结对象：`NQ_DOCS_GOVERNANCE_PLAN.md`、`NQ_DOCS_AUTHORITY_INDEX.md`、`NQ_DOCS_EVIDENCE_INDEX.md`、`NQ_DOCS_MIGRATION_MAP.md`、`NQ_DOCS_G1_IMPLEMENTATION.md`。
- 禁止修改 G2 / G3 / G4 已冻结对象、compatibility stub、canonical evidence、current authority 或目录索引。
- 禁止修改 `docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**`、`.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试或依赖。

---

## 2. Migration Map 精确查询规则

G5 directory closure 候选必须同时满足：

```text
recommended_action = FUTURE_MOVE_CANDIDATE
migration_batch    = G5
```

只出现 `G5 可选`、`未来 G5`、target location 说明、或 `migration batch = NONE` 的条目，均不得视为 G5 候选。候选不得依据文件名、目录印象或重复感扩展。

### 2.1 结构化抽取结果

对 `docs/current/NQ_DOCS_MIGRATION_MAP.md` 分段读取 `recommended action` 与 `migration batch` 后，结果如下：

| 段落 | recommended action | migration batch | G5 future move? | 结论 |
| --- | --- | --- | --- | --- |
| §1A 当前唯一权威活文档 | `INDEX_AS_CURRENT_CONTROL` | `NONE` | No | 非候选，current authority，保留原位 |
| §1B 当前生效基线 | `INDEX_AS_CURRENT_CONTROL` | `NONE` | No | 非候选；行文虽出现 `G5 可选`，但 batch 明确为 `NONE` |
| §1C current 保留的历史证据 | `RETAIN_IN_PLACE` | `NONE` | No | 非候选；target 说明含未来 G5 可能性，但 action/batch 不满足 |
| §1D CI 过程 / 基线 / 冻结证据 | `FUTURE_MOVE_CANDIDATE` | `G4` | No | 非 G5；已由 G4 freeze 接受，受 G4 冻结边界保护 |
| §1E GateJ superseded 收敛候选 | `FUTURE_SUPERSEDE_CANDIDATE` | `G3` | No | 非 G5；已由 G3 freeze 接受，受 G3 冻结边界保护 |
| §1F 治理线 meta-docs | `INDEX_AS_CURRENT_CONTROL` | `NONE` | No | 非候选，治理 current-control 保留 |
| §2 `docs/current/frontend` | `RETAIN_IN_PLACE` | `NONE` | No | 非候选，前端当前基线保留 |
| §3 scattered 当前控制文档 | `INDEX_AS_CURRENT_CONTROL` / `RETAIN_IN_PLACE` | `NONE` | No | 非候选，仓库入口/规则/README 保留 |
| §4 gates/archive/.agents/templates | `RETAIN_IN_PLACE` | `NONE` | No | 非候选，且属于显式排除区域 |

核验摘要：

```text
G5_FUTURE_MOVE_COUNT = 0
FUTURE_MOVE_SECTIONS = 1
FUTURE_MOVE_SECTIONS_BATCH = G4 only
G5_TEXT_LINES = 4
```

行级证据：

| Migration Map 行 | 内容 | 预检解释 |
| --- | --- | --- |
| 79 | `target location：docs/current/（可选未来 docs/current/governance/，纯组织，G5 可选）` | 仅 target 说明；不是 candidate rule |
| 80 | `migration batch：NONE（G5 可选组织化，不移出 current）` | batch 是 `NONE`，不是 `G5` |
| 81 | `link compatibility strategy：N/A（如 G5 组织化则 authority index 同步）` | 仅兼容策略说明；不是 candidate rule |
| 105 | `target location：docs/current/（未来 G5 可选移入 docs/evidence/）` | 仅 target 说明；action 是 `RETAIN_IN_PLACE`，batch 是 `NONE` |
| 121 | `recommended action：FUTURE_MOVE_CANDIDATE` | 唯一 future-move 段落 |
| 123 | `migration batch：G4` | 唯一 future-move 段落属于 G4，不属于 G5 |

---

## 3. 全部 G5 候选清单

### 3.1 候选总数

| 指标 | 数量 | 说明 |
| --- | ---: | --- |
| Migration Map 中满足 `recommended_action = FUTURE_MOVE_CANDIDATE` 且 `migration_batch = G5` 的候选 | 0 | 与 Migration Map 精确抽取一致 |
| `ELIGIBLE_FOR_G5_IMPLEMENTATION` | 0 | 当前冻结 Migration Map 未授权任何 G5 move |
| `BLOCKED_PER_FILE` | 0 | 无候选文件可进入逐文件拒绝判定 |
| `RETAIN_IN_PLACE` | 0 | 无 G5 候选需要按该结论保留；非候选 retain 条目见 §2.1 |

### 3.2 逐文件矩阵

当前冻结 Migration Map 中没有 G5 `FUTURE_MOVE_CANDIDATE`，因此逐文件矩阵为空。空矩阵本身是本轮 preflight 的核心结论：**不得从 §1B / §1C 的“G5 可选”说明或其他目录印象扩展候选**。

| source path | frozen target location | 主分类 | domain tags | authority level | lifecycle | retention rationale | target directory exists? | source current authority? | historical frozen evidence? | already-frozen compatibility stub? | G1/G2/G3/G4 frozen object? | excluded path? | ordinary inbound links | fragment inbound links | target exists? | source/target blob relation | conclusion | reason |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: | --- | --- | --- | --- |
| _无 G5 candidate_ | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | 0 | 0 | N/A | N/A | N/A | Migration Map 未列出任何 G5 `FUTURE_MOVE_CANDIDATE` |

---

## 4. Link And Fragment Risk Audit

因为 G5 candidate set 为空，本轮没有 source path 可执行 ordinary inbound link 或 `#fragment` 入链展开。

| 项目 | 结果 |
| --- | --- |
| 普通入链总数 | 0（无候选 source） |
| `#fragment` 入链总数 | 0（无候选 source） |
| Markdown 相对链接风险 | N/A |
| 绝对仓库路径提及风险 | N/A |
| 纯文本路径提及风险 | N/A |
| fragment 是否导致阻断 | N/A；没有候选，不触发 `BLOCKED_PER_FILE` |

强制规则仍然保持：未来若出现任何 G5 候选，只要其 source 存在任意 `#fragment` 入链，默认标记 `BLOCKED_PER_FILE`，不得在 preflight 阶段修改入链、heading 或目标文件。

---

## 5. Directory And Target Conflict Audit

### 5.1 当前 G5 候选 target 结论

| 项目 | 结果 |
| --- | --- |
| G5 candidate target directory | N/A |
| G5 candidate target file | N/A |
| source / target blob compare | N/A |
| target exists and identical | N/A |
| target exists and different | N/A |
| prospective source stub relative link | N/A |

### 5.2 相关目录只读观察

以下目录只读观察不构成 G5 候选授权：

| path | exists? | 说明 |
| --- | --- | --- |
| `docs/current/` | Yes | 当前事实源目录；§1B / §1C batch 为 `NONE` |
| `docs/evidence/` | Yes | G4 后已有 evidence 根目录；当前无 G5 target 文件 |
| `docs/evidence/ci/` | Yes | G4 canonical CI evidence 目录，受 G4 freeze 边界保护 |
| `docs/baselines/` | Yes | G4 CI baseline index 所在目录，受 G4 freeze 边界保护 |

本轮未创建 `docs/current/governance/`，也未向 `docs/evidence/` 或 `docs/baselines/` 新增任何文件。

---

## 6. Rejection Rules Applied

由于 G5 candidate set 为空，没有文件进入逐文件 `BLOCKED_PER_FILE`。但以下拒绝条件已作为抽取守门规则执行：

| 拒绝条件 | 本轮结果 |
| --- | --- |
| Migration Map 未明确指定 G5 | 所有潜在文本均不满足：§1B/§1C batch = `NONE`；§1D batch = `G4` |
| source 是 current authority | §1A/§1B/§3 均为 current-control/current-baseline，不作为 G5 candidate |
| source 属于 G1/G2/G3/G4 冻结对象或兼容路径 | G1 五份冻结对象、G3 17 stub、G4 CI evidence/stub/current authority 均排除 |
| source 位于 `docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**` | §4 全部 `RETAIN_IN_PLACE / NONE`，且属于显式排除区域 |
| 存在任意 fragment 入链 | 无候选 source，未触发；未来候选若命中则默认 BLOCKED |
| target 已存在且内容不同 | 无候选 target，未触发 |
| target 目录或相对链接策略无法保持兼容 | 无候选 target，未触发；未来必须逐文件验证 |
| source 不具备清晰、可回滚的 redirect-first 方案 | 无候选 source，未触发；未来必须先给出 stub 相对链接与回滚命令 |

---

## 7. Future Redirect-First Design

### 7.1 当前 G5 implementation 结论

当前冻结 Migration Map 没有任何 `ELIGIBLE_FOR_G5_IMPLEMENTATION` 文件。因此 **G5 implementation 不应执行移动/目录收口动作**；若后续仍要开启目录收口，必须先有单独、受控、可审查的候选授权，不得在本 preflight 结论上自行扩展。

### 7.2 未来候选的最低方案模板

仅当后续存在明确 G5 candidate 时，每个文件必须满足以下 redirect-first 设计：

| 项目 | 要求 |
| --- | --- |
| source -> canonical target | 来自已接受的候选清单，不从文件名或目录印象推断 |
| old path compatibility stub | 旧路径保留 H1、非权威说明、canonical 相对链接、冻结批次和回滚说明 |
| canonical 保留规则 | target 成为唯一 canonical 全文；旧路径只保留 redirect-first stub |
| target directory / README | 目标目录存在或由单独实施任务显式创建；preflight 不创建 |
| link validation | 普通入链可由 stub 兼容；fragment 入链必须为 0 |
| conflict validation | target 不存在，或 target 存在且 blob identical；不同则 BLOCKED |
| batchability | 只有相互独立、无 fragment、无 target conflict 的候选才可同批 |
| split rule | current authority、冻结对象、兼容路径、跨域高入链文件必须拆为独立子批或保留原位 |

### 7.3 当前批次可执行性

| 项目 | 结论 |
| --- | --- |
| 可与其他候选同批执行 | N/A，候选数为 0 |
| 必须拆为独立子批 | N/A，候选数为 0 |
| 需要新增 target 目录或 README | No，本轮禁止且无候选需要 |
| G6 是否因 G5 产生删除任务 | No，**G6 deletion batch = NOT STARTED / DEFAULT EMPTY** |

---

## 8. Rollback Boundary

### 8.1 本轮 preflight 回滚

本轮只新增/追加文档记录，不触碰代码或冻结对象。若需回滚本轮 preflight：

```powershell
git restore -- docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md
Remove-Item -LiteralPath docs/current/NQ_DOCS_G5_DIRECTORY_CLOSURE_PREFLIGHT.md -Force
```

如已提交，应使用普通 revert：

```powershell
git revert <g5-preflight-commit>
```

### 8.2 未来逐文件回滚设计

当前无 eligible 文件，因此没有实际 source -> target 回滚命令。未来若存在候选，单文件回滚必须至少包含：

```powershell
# 示例模板，当前不执行
git mv <canonical-target> <old-source-path>
git restore -- <old-source-path>
git diff --check
```

未来批次回滚必须以单独 commit/revert 为边界，不得跨批混合 G5 与 G6。

---

## 9. Findings

| Severity | Findings |
| --- | --- |
| P0 | 0 |
| P1 | 0 |
| P2 | 0 |
| P3 | 0 |

非 finding 观察：Migration Map 中有 `G5 可选` 说明性文字，但所有这些位置均不是 `migration_batch = G5` 且不是 `FUTURE_MOVE_CANDIDATE`。因此 G5 preflight 的正确结果是 **zero candidate / no-op implementation boundary**。

---

## 10. Validation Plan

本轮是 docs-only governance preflight，未运行 backend/frontend/Python 测试；原因是无代码、workflow、migration、依赖或运行时逻辑变更。验证以文档、git diff、候选抽取和 forbidden-area diff 为准。

建议和本轮实际验证命令：

```powershell
git status --short
git branch --show-current
git log --oneline -20

Select-String -LiteralPath docs/current/NQ_DOCS_MIGRATION_MAP.md -Pattern 'G5','FUTURE_MOVE_CANDIDATE'

git diff --check
git diff --name-status

git diff -- docs/current/NQ_DOCS_GOVERNANCE_PLAN.md docs/current/NQ_DOCS_AUTHORITY_INDEX.md docs/current/NQ_DOCS_EVIDENCE_INDEX.md docs/current/NQ_DOCS_MIGRATION_MAP.md docs/current/NQ_DOCS_G1_IMPLEMENTATION.md
git diff -- docs/gates docs/archive .agents templates
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
```

---

## 11. Final Preflight Decision

```text
NQ-DOCS-GOVERNANCE-G5-DIRECTORY-CLOSURE-PREFLIGHT：PASS / READY FOR REVIEW
```

Decision details:

- Migration Map exact G5 `FUTURE_MOVE_CANDIDATE` candidates: **0**.
- `ELIGIBLE_FOR_G5_IMPLEMENTATION`: **0**.
- `BLOCKED_PER_FILE`: **0**.
- `RETAIN_IN_PLACE`: **0** for G5 candidates; non-candidate retain sections remain untouched.
- Ordinary inbound links: **0**, because there are no candidate source paths.
- Fragment inbound links: **0**, because there are no candidate source paths.
- Target conflicts: **0**, because there are no candidate target paths.
- No source files were moved, deleted, renamed, copied, archived, or stubbed.
- No new target directory or canonical target file was created.
- G1-G4 frozen objects and excluded directories remain out of scope.
- G6 remains **NOT STARTED / DEFAULT EMPTY**.

---

## 12. Actual Validation Results

本轮实际执行并通过的验证摘要：

| Command / check | Result | Notes |
| --- | --- | --- |
| `git status --short` | PASS | 仅 `STATUS.md`、`TESTING.md`、`WORKLOG.md` 修改，新增 `NQ_DOCS_G5_DIRECTORY_CLOSURE_PREFLIGHT.md` |
| `git branch --show-current` | PASS | `dev` |
| `git log --oneline -20` | PASS | HEAD = `b57d7351 docs(governance): freeze G4 CI evidence routing` |
| structured Migration Map extraction | PASS | `G5_FUTURE_MOVE_COUNT=0`；`FUTURE_MOVE_SECTIONS=1`；future-move section batch = G4 |
| `Select-String ... 'G5','FUTURE_MOVE_CANDIDATE'` | PASS | G5 text only appears on explanatory lines 79/80/81/105；future-move appears at line 121 with line 123 batch G4 |
| `git diff --check` | PASS | exit 0；仅 LF/CRLF warning，非 whitespace error |
| `git diff --name-status` | PASS | tracked diff only: `STATUS.md` / `TESTING.md` / `WORKLOG.md` |
| new preflight file whitespace check | PASS | trailing whitespace lines = 0；single LF at EOF |
| G1 frozen objects diff | PASS | empty |
| `docs/gates docs/archive .agents templates` diff | PASS | empty |
| `.github/workflows/ci.yml` diff | PASS | empty |
| `backend frontend research scripts deploy` diff | PASS | empty |
| `backend/**/db/migration` diff | PASS | empty |

未运行 backend/frontend/Python 测试；原因同 §10：本轮为 docs-only governance preflight，无代码、workflow、migration、依赖或运行时逻辑变更。
