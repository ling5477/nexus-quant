# NQ Documentation Governance - G5 Directory Closure Preflight Review

任务：NQ-DOCS-GOVERNANCE-G5-DIRECTORY-CLOSURE-PREFLIGHT-REVIEW

日期：2026-06-19

任务类型：DOCUMENTATION_GOVERNANCE_REVIEW + DIRECTORY_CLOSURE_PREFLIGHT_AUDIT + MIGRATION_MAP_CANDIDATE_VERIFICATION

> 本轮为 G5 directory closure preflight 的只读审查与 review evidence 落盘。审查对象是 preflight 结论是否严格来源于冻结的 `docs/current/NQ_DOCS_MIGRATION_MAP.md`，不是 G5 implementation，不启动 G6 deletion batch。本轮仅新增本文并更新 `STATUS.md` / `TESTING.md` / `WORKLOG.md`；未移动、删除、重命名、复制、归档、stub 化任何文档，未创建 target 目录或 canonical 文件，未修改 G1～G4 冻结对象、workflow、代码、测试、migration、依赖。

## 审查结论

**结论：`NQ-DOCS-GOVERNANCE-G5-DIRECTORY-CLOSURE-PREFLIGHT-REVIEW：PASS / ACCEPTED`**

- G5 directory closure preflight = **ACCEPTED / READY FOR FREEZE REVIEW**。
- Migration Map 中可执行 G5 `FUTURE_MOVE_CANDIDATE` 候选 = **0**。
- 空逐文件矩阵是正确结果，不是漏填。
- P0 = 0；P1 = 0；P2 = 0；P3 = 0。
- 允许进入 **G5 freeze review**；不得据此进入 G5 implementation 或 G6 deletion batch。

## Migration Map 查询核验

G5 directory closure 可执行候选必须同时满足：

```text
recommended_action = FUTURE_MOVE_CANDIDATE
migration_batch    = G5
```

实际核验结果：

| 检查项 | 结果 | 结论 |
| --- | ---: | --- |
| `recommended_action = FUTURE_MOVE_CANDIDATE` 且 `migration_batch = G5` | 0 | 无可执行 G5 move 候选 |
| 实际 `FUTURE_MOVE_CANDIDATE` 段落 | 1 | 仅 §1D |
| §1D migration batch | G4 | 属 G4 CI evidence routing，不属于 G5 |
| §1B / §1C 的 `G5 可选` | 说明性文字 | batch 均为 `NONE`，不得扩展为 candidate |

行级证据：

- `NQ_DOCS_MIGRATION_MAP.md` §1B：recommended action = `INDEX_AS_CURRENT_CONTROL`，migration batch = `NONE`；其中 `G5 可选` 仅描述未来组织化可能，不移出 current。
- `NQ_DOCS_MIGRATION_MAP.md` §1C：recommended action = `RETAIN_IN_PLACE`，migration batch = `NONE`；其中未来 G5 仅是 target 说明，不满足 move 条件。
- `NQ_DOCS_MIGRATION_MAP.md` §1D：recommended action = `FUTURE_MOVE_CANDIDATE`，但 migration batch = **G4**；已由 G4 freeze 接受并受 G4 边界保护。

因此，`G5 可选` 不能作为候选来源；目录印象、文件名模式、重复感或 future target 文字也不能补选候选。

## 空矩阵核验

由于 G5 executable candidates = 0，以下矩阵为空均为正确结果：

| 审计对象 | 数量 | 结论 |
| --- | ---: | --- |
| `ELIGIBLE_FOR_G5_IMPLEMENTATION` | 0 | 正确 |
| `BLOCKED_PER_FILE` | 0 | 正确；无候选进入拒绝判定 |
| `RETAIN_IN_PLACE` for G5 candidates | 0 | 正确；非候选 retain 条目不计入 G5 |
| ordinary inbound link audit objects | 0 | 正确；无候选 source path |
| fragment inbound link audit objects | 0 | 正确；无候选 source path |
| target conflict audit objects | 0 | 正确；无候选 target path |
| redirect-first design objects | 0 | 正确；无候选需要设计 |

不应要求凭空生成逐文件候选矩阵，也不应从 `docs/current`、`docs/gates`、`docs/archive` 或文件名模式中补选候选。

## 范围保护核验

最新 G5 preflight commit `8917d99d docs(governance): preflight G5 directory closure` 只触达允许的 4 个文件：

| 状态 | 文件 |
| --- | --- |
| A | `docs/current/NQ_DOCS_G5_DIRECTORY_CLOSURE_PREFLIGHT.md` |
| M | `docs/current/STATUS.md` |
| M | `docs/current/TESTING.md` |
| M | `docs/current/WORKLOG.md` |

禁止范围核验通过：G1 五份冻结对象 diff 为空；G2/G3/G4 冻结对象、stub、canonical evidence、current authority 未被修改；`docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**`、workflow、backend、frontend、research、scripts、deploy、migration diff 均为空；未创建 `docs/evidence/**`、`docs/baselines/**` 或其他 target/canonical 文件。

## 工具降级审查

preflight commit 的最终文件清单证明，即使上一轮曾因 `apply_patch` 失败降级到 PowerShell `Set-Content` / `Add-Content`，实际落盘也只触达允许的 4 个文档。结合 `git diff --check` exit 0、新增 preflight 文件 trailing whitespace = 0、single LF at EOF、禁止范围 diff 为空，该工具降级不构成 finding。

本轮 review 使用 `apply_patch` 新增本文；更新 `STATUS.md` / `TESTING.md` / `WORKLOG.md` 时，因 Windows sandbox helper 连续取消 `apply_patch`，降级为 PowerShell 定点插入。降级写入仅触达这 3 份允许的 current-control 文档，并由后续 `git diff --name-status`、`git diff --check` 和 forbidden-area diff 复核。

## Findings

- P0：无。
- P1：无。
- P2：无。
- P3：无。

## Validation

已执行并通过：

```text
git status --short
git branch --show-current
git log --oneline -20
rg -n "migration_batch.*G5|G5.*migration_batch|FUTURE_MOVE_CANDIDATE" docs/current/NQ_DOCS_MIGRATION_MAP.md
rg -n "FUTURE_MOVE_CANDIDATE|migration_batch|G5|§1B|§1C|§1D|NONE|G4" docs/current/NQ_DOCS_G5_DIRECTORY_CLOSURE_PREFLIGHT.md
git diff --check
git diff --name-status
git diff -- docs/current/NQ_DOCS_GOVERNANCE_PLAN.md docs/current/NQ_DOCS_AUTHORITY_INDEX.md docs/current/NQ_DOCS_EVIDENCE_INDEX.md docs/current/NQ_DOCS_MIGRATION_MAP.md docs/current/NQ_DOCS_G1_IMPLEMENTATION.md
git diff -- docs/gates docs/archive .agents templates
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
git show --name-status --oneline --stat --no-renames HEAD
```

结果：G5 executable candidates = 0；future-move section batch = G4 only；G5 preflight matrix empty = expected；P0/P1/P2/P3 = 0；G1 frozen objects diff empty；forbidden scope diff empty；`git diff --check` PASS；latest preflight commit allowlist only。

未运行后端 / 前端 / Python 测试：本轮为 docs-only review，不修改代码、workflow、migration、依赖或运行时逻辑。

## 风险与回滚

- 风险：低。review 只新增本文并追加 current-control 记录；不改 Migration Map，不改 preflight 结论来源，不改任何冻结对象或运行时代码。
- 回滚：删除 `docs/current/NQ_DOCS_G5_PREFLIGHT_REVIEW.md`，并 revert `STATUS.md` / `TESTING.md` / `WORKLOG.md` 的本轮 review 追加段即可；G5 preflight commit `8917d99d` 不受影响。

## 状态结论（原样）

```text
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = FROZEN / ACCEPTED
G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED
G4 CI evidence routing = FROZEN / ACCEPTED
G5 directory closure preflight = ACCEPTED / READY FOR FREEZE REVIEW
G5 executable candidates = 0
G6 deletion batch = NOT STARTED / DEFAULT EMPTY
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
