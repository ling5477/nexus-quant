# NQ Documentation Governance - G4 CI Evidence Routing Freeze Review

任务：NQ-DOCS-GOVERNANCE-G4-FREEZE-REVIEW

日期：2026-06-19

任务类型：DOCUMENTATION_GOVERNANCE_FREEZE_REVIEW + CI_EVIDENCE_ROUTING_BASELINE_FREEZE + CURRENT_AUTHORITY_PROTECTION

> 本轮为 G4 CI evidence routing semantic / structural baseline freeze。冻结的是 source-to-canonical routing、一对一同名映射、old-path compatibility stub 模型、fragment 风险规则、2 个 current authority 保留规则，以及 CI evidence / baseline index 的导航职责；不是把 STATUS.md、TESTING.md、WORKLOG.md 锁成 immutable blob。后续真实、带日期的状态 / 验证 / 工作日志追加不自动使 G4 freeze 失效。

## 冻结结论

**结论：NQ-DOCS-GOVERNANCE-G4-FREEZE-REVIEW：PASS / ACCEPTED / FROZEN**

- G4 CI evidence routing = FROZEN / ACCEPTED。
- G5 = READY FOR IMPLEMENTATION。
- G6 = NOT STARTED。
- P0 = 0；P1 = 0；P2 = 0；P3 = 0。
- 本轮未修改 20 个 source stub、20 个 canonical evidence、2 个 current authority、docs/evidence/ci/README.md、docs/baselines/CI_BASELINE_INDEX.md、G1 五份冻结对象、G2/G3 冻结对象、docs/gates/**、docs/archive/**、.agents/**、templates/**、workflow、代码、测试、migration、依赖。

## 候选集合与计数

| 检查项 | 结果 |
| --- | --- |
| Migration Map §1D candidates | 22 |
| Routed historical CI evidence | 20 |
| Retained current authority | 2 |
| Routed + retained | 22 / 22 |
| docs/evidence/ci/NQ_CI_*.md | 20 |
| G4 集合外实际 routing 文件 | 0 |
| duplicate canonical evidence | 0 |

断言：22 个候选均来自 docs/current/NQ_DOCS_MIGRATION_MAP.md §1D；20 个 canonical evidence 均位于 docs/evidence/ci/；2 个 current authority 保留在 docs/current/NQ_CI_BASELINE_PLAN.md 与 docs/current/NQ_CI_SECURITY_GUARD_PLAN.md。

## 20 个 source to canonical target 映射与 blob 断言

基准：G4 implementation commit 783bfa68；source baseline = 783bfa68^:docs/current/<filename>。

| # | source compatibility path | canonical target | blob |
| --- | --- | --- | --- |
| 1 | docs/current/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md | docs/evidence/ci/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md | 0d002077076991a2d977ca19a2a83b36738c72d8 |
| 2 | docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md | docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_PLAN.md | 34575897cd471f1f968834b0ac1f343f58671c16 |
| 3 | docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md | docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md | b11431ec93346ef47e8ad3ced5154aa2591edadb |
| 4 | docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md | docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md | a703db8e719c529cf9a28d7edbfbe13f29193c8c |
| 5 | docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md | docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md | 48901f69c3d04c39cb17010fb4ebf233bff6586c |
| 6 | docs/current/NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md | docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md | ad7d818c67b3263b052efe1436f37b6c318c0b0e |
| 7 | docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md | docs/evidence/ci/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md | 264c6f719c2dd97fe74bd397807d3c8f1a551819 |
| 8 | docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md | docs/evidence/ci/NQ_CI_LOG_REDACTION_PROOF_PLAN.md | 668e3ccf7056ca263dd3ab33ae7f1158bf4856b4 |
| 9 | docs/current/NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md | docs/evidence/ci/NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md | afbbc1447b2ae4747fc2f7b542c1fabfc7cce94c |
| 10 | docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md | docs/evidence/ci/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md | 3966dfbf38918ff736dba385e348bdb71d2496d5 |
| 11 | docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md | docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PLAN.md | 3f236007c339da8c620767ee9a028534493e60a4 |
| 12 | docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md | docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md | d16ef096d937a5f85e0ad862cbb7ec11c9b729d6 |
| 13 | docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md | docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md | 457630fcae0b51d7a83ff1a471f7e5b854dc7c5b |
| 14 | docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md | docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md | 6c1fb2d9da69595743b551191f2a24285c83fa71 |
| 15 | docs/current/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md | docs/evidence/ci/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md | b5d3bfa6d58d7b751d24a771004daa3622b00311 |
| 16 | docs/current/NQ_CI_FRONTEND_E2E_PLAN.md | docs/evidence/ci/NQ_CI_FRONTEND_E2E_PLAN.md | 81dd8b8336b48469508a1981d15773407974c91d |
| 17 | docs/current/NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md | docs/evidence/ci/NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md | 8c57587da63e115092f2197e6d5d35e69ad9355b |
| 18 | docs/current/NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md | docs/evidence/ci/NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md | 82c08e3ef59efbc9eb19af78f6fcccb2d814d581 |
| 19 | docs/current/NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md | docs/evidence/ci/NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md | 5d34d1c40a8affc9b1fa9da51edb404403b24de3 |
| 20 | docs/current/NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md | docs/evidence/ci/NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md | 66733385d8c9a4b65b2298457ee3c11984562693 |

断言：20 / 20 canonical target blob 均等于 783bfa68^:docs/current/<filename> 的原始 source blob；未发现截断、摘要化、重写、额外结论或历史状态变化。

## Source compatibility stub 检查

20 个 docs/current/NQ_CI_*.md source path 均通过：

- 文件继续存在。
- 保留原始 H1。
- 包含 Historical CI evidence / non-authoritative compatibility path。
- 使用相对 canonical link：../evidence/ci/<filename>。
- 包含 NON_AUTHORITATIVE / SUPERSEDED_BY_CI_EVIDENCE_RECORD。
- 包含 G4 CI evidence routing 治理说明。
- stub 行数不超过 20 行，未保留旧全文、长摘要、冻结结论副本或第二份权威正文。
- 未使用 HTML redirect、JavaScript、meta refresh、脚本跳转或外链。
- 普通旧路径继续解析到 source stub。
- 20 个 source path 的 fragment 入链 = 0。

结论：无 PARTIAL / BLOCKED_PER_FILE / FRAGMENT_COMPATIBILITY_RISK。

## Current authority 保留核验

| 文件 | 当前职责 | blob |
| --- | --- | --- |
| docs/current/NQ_CI_BASELINE_PLAN.md | CURRENT_AUTHORITY | 1f72a52d77b7b6c38e47676a5e906bbe6b087faf |
| docs/current/NQ_CI_SECURITY_GUARD_PLAN.md | CURRENT_AUTHORITY | 50679045fdd65587980a16c5770f8414a140836d |

断言：

- 2 / 2 与 783bfa68^:docs/current/<filename> blob 一致。
- 未被 stub 化、移动、重命名、删除、降级、标记 SUPERSEDED 或误写为 historical-only。
- 未在 docs/evidence/ci/ 生成重复 authority copy。
- 其 authority 地位由 docs/baselines/CI_BASELINE_INDEX.md 导航，不被该 index 取代。

## CI 目录入口与 index 职责

docs/evidence/ci/README.md：

- 声明 docs/evidence/ci/ 是 canonical CI historical evidence。
- 明确不取代 docs/current/STATUS.md 的 current-status authority。
- 指向两份 current authority 与 baseline index。
- 保持 backlog/residual 状态真实：5B-ENV P1 SECURITY ENHANCEMENT / NOT STARTED，5B-SMOKE BLOCKED BY 5B-ENV，4F-B 至 4F-F OPTIONAL BACKLOG / NOT STARTED，static workflow assertion OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。

docs/baselines/CI_BASELINE_INDEX.md：

- 声明自身是 CI baseline / evidence navigation index，只链接、不复制冻结正文，不改变 current authority。
- 指向 ../current/STATUS.md、../current/NQ_CI_BASELINE_PLAN.md、../current/NQ_CI_SECURITY_GUARD_PLAN.md。
- 指向 20 个 canonical historical evidence。
- 未复制 freeze/review 正文，未取代 current status，未将 5B-ENV、5B-SMOKE、4F-B 至 4F-F 或 static workflow assertion 误写为 completed。

## 冻结对象与范围保护

G1 五份冻结对象 blob zero drift：

| 冻结对象 | blob |
| --- | --- |
| docs/current/NQ_DOCS_GOVERNANCE_PLAN.md | 0ee21735531d73e7fede68bcab635fbece4f3e1a |
| docs/current/NQ_DOCS_AUTHORITY_INDEX.md | 71e31b5d4ddc65c9ae1a6cae4b33ede82e747b60 |
| docs/current/NQ_DOCS_EVIDENCE_INDEX.md | 8b18e36dc5cba83b814bb0e1d5f7b010f0dbabf0 |
| docs/current/NQ_DOCS_MIGRATION_MAP.md | 6eb2706df4539e94a5b174342a33543f1e4a5793 |
| docs/current/NQ_DOCS_G1_IMPLEMENTATION.md | 4dece64e8b5822f9093c2528a8098a866cf82377 |

范围保护断言：

- G2 semantic baseline 未被恢复或削弱。
- G3 的 17 个 GateJ compatibility stub 存在且模板合规；RUNBOOK 未被纳入 supersede；9 份 DIVERGED 文件未被转换为 GateJ stub。
- docs/gates/**、docs/archive/**、.agents/**、templates/** zero drift。
- .github/workflows/ci.yml、backend、frontend、research、scripts、deploy、migration、测试与依赖 zero drift。
- 未删除、移动、重命名任何文件。
- G5、G6、Batch 5B-ENV、5B-SMOKE、4F-B 至 4F-F 未启动。
- LIVE、AI、DH runtime、RealClient、real provider、real exchange adapter 未开启、未接入、未实现。

注：STATUS.md、TESTING.md、WORKLOG.md 的本轮追加记录属于允许的 current-control 维护；不改变 G4 source/canonical routing baseline。

## Findings

### P0

- 无。

### P1

- 无。

### P2

- 无。

### P3

- 无。

## Validation

已执行：

~~~text
git status --short
Migration Map §1D candidate trace
20 routed pre-routing source blob vs target blob loop
20 old-path stub template loop
20 old-path source fragment grep
2 current authority blob/protection check
docs/evidence/ci NQ_CI file count check
CI_BASELINE_INDEX semantic check
docs/evidence/ci/README.md semantic check
G1 frozen object hash-object check
G3 17 stub / RUNBOOK / DIVERGED header check
git diff --name-status -- docs/gates docs/archive .agents templates .github/workflows/ci.yml backend frontend research scripts deploy
~~~

结果：

~~~text
MIGRATION_MAP_G4_CANDIDATES=22
ROUTED_COUNT=20
AUTHORITY_COUNT=2
EVIDENCE_NQ_CI_FILE_COUNT=20
ACTUAL_NQ_CI_DOCS_OUTSIDE_CURRENT_OR_EVIDENCE=0
CANONICAL_BLOB_OK=20
STUB_TEMPLATE_OK=20
FRAGMENT_HITS=0
AUTHORITY_RETAINED=2
G1_FROZEN_OBJECTS_OK=5
GATEJ_STUB_OK=17
PROTECTED_DIFF_EMPTY=true
~~~

未运行后端 / 前端 / Python 测试：本轮为 docs-only freeze review，不修改代码、workflow、migration、依赖或运行时逻辑。

## G4 freeze 失效条件

任一情况发生，G4 routing baseline 失效，须重新审查：

- 任一 20 个 source stub 被删除、移动、重命名或恢复完整历史正文。
- 任一 20 个 canonical evidence 被改写、删除、移动、降级或改为非 canonical。
- 任一 source to target 映射被修改。
- 任一 source stub 出现 #fragment 入链而未完成兼容性审查。
- 两份 current authority 被 stub 化、降级、迁移或误标 SUPERSEDED。
- CI_BASELINE_INDEX.md 被描述为 current authority 或替代 current status。
- 目录索引把 backlog/residual 写为 completed。
- 修改 G1 五份冻结对象、G2/G3 已冻结语义或 GateJ 兼容集合。

## 正常后续维护

以下动作不自动使 G4 freeze 失效：

- 在 STATUS.md、TESTING.md、WORKLOG.md 追加真实、带日期的状态记录。
- 在 current 文档中补充指向 canonical CI evidence 的普通导航链接。
- 在 docs/evidence/ci/README.md 增加未来被批准归位的证据目录导航，但不得改写已冻结 20 个 canonical evidence。
- 后续 G5、G6 的受控文档治理变更。
- 对两份 current authority 做独立、受控的 current-control 内容维护，但不得改变其 authority、路径或完整正文职责。

## 修改文件与回滚

本轮修改：

- 新增 docs/current/NQ_DOCS_G4_FREEZE_REVIEW.md。
- 更新 docs/current/STATUS.md。
- 更新 docs/current/TESTING.md。
- 更新 docs/current/WORKLOG.md。

回滚方式：删除 docs/current/NQ_DOCS_G4_FREEZE_REVIEW.md，并 revert STATUS.md / TESTING.md / WORKLOG.md 的本轮追加段即可；G4 implementation commit 783bfa68 与 20 个 canonical evidence / source stub / current authority 不受影响。

## 状态结论（原样）

~~~text
NQ Docs Governance Plan = FROZEN FOR G1 BASELINE
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = FROZEN / ACCEPTED
G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED
G4 CI evidence routing = FROZEN / ACCEPTED
G5 = READY FOR IMPLEMENTATION
G6 = NOT STARTED
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
~~~