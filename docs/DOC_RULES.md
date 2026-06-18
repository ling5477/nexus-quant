# Documentation Rules

1. `docs/current` 只放当前事实。
2. `docs/gates` 只放历史 Gate 冻结卷宗。
3. `docs/archive` 只归档，不作为当前开发依据。
4. `docs` 根目录不放业务细节文档。
5. 新 Gate 开始前必须先写 PLAN。
6. Gate 完成后必须把 current 快照归档到 `docs/gates/gate-x`。
7. 文档描述必须与代码和测试状态一致。
8. 未执行验证不能写成通过。
9. AI 自动交易必须在虚拟币量化 V1 和 Paper Trading 稳定后才能进入交易主链。
10. 本地 PostgreSQL 默认端口固定为 `5432`。

## 文档治理规则（G1 新增）

> 本节仅新增治理入口与 retain-first 原则；不修订上文既有规则，不处理“不重复 vs 迁移或复制”的既有表述矛盾（留待 G2）。

11. 文档治理事实源：权威入口见 `docs/current/NQ_DOCS_AUTHORITY_INDEX.md`，历史证据入口见 `docs/current/NQ_DOCS_EVIDENCE_INDEX.md`，逐文件迁移映射见 `docs/current/NQ_DOCS_MIGRATION_MAP.md`。
12. 每个领域只有一个“当前唯一权威”；历史证据可多个但不替代 current control。
13. retain-first：先建索引/映射，历史链接先 redirect 兼容，再移动或目录收口。
14. `docs/gates/**`、`docs/archive/**`、`.agents/**`、`docs/templates/**` 一律 RETAIN_IN_PLACE，不在文档收口中移动或删除。
15. 删除必须独立、显式、可审计、逐文件审查，默认不删除；不通过删除或压缩历史冻结证据改变历史事实。

## 规则冲突收敛（G2 固定）

16. 关于本文规则 6（“Gate 完成后必须把 current 快照归档到 `docs/gates/gate-x`”）与 `docs/README.md` 既有“已完成 Gate 计划文档不在 `docs/current/` 重复” / “迁移**或复制**到 `docs/gates/gate-x/`” 之间的表述张力，按以下**优先级**收敛（高优先级覆盖低优先级）：
    1. **冻结证据保留优先**：`docs/gates/**` 内的冻结副本是权威，任何收口都不得删除或改写它。
    2. **current control 单一权威优先**：每个领域 current 侧只保留唯一权威；已完成 Gate 的过程/计划文档在 current 侧属 superseded duplicate，应收敛到唯一权威（gate-x 副本）。
    3. **迁移前先建 index / redirect / compatibility mapping**：在 `docs/current/` 移除任何 superseded 副本前，必须先建立权威索引与 redirect 兼容入口（见规则 11~13）。
    4. **复制（duplicate）仅限过渡导航或必要快照**：允许的“复制”只用于明确的过渡期导航或冻结快照，且复制处必须标注 authority 指向（哪个是权威、哪个是只读副本）；不得让复制副本成为并列权威。
    5. **不得为减少文件数删除或改写历史证据**：精简只能通过“先 redirect、再移除 current 重复”实现，且 gate-x / archive 权威副本永久保留。
    - 据此，“不重复”指 current 侧不长期并存 superseded 重复，“迁移或复制”指 Gate 冻结时在 gate-x 留权威快照；二者不矛盾：current 重复的实际移除属 G3，须 redirect-first（见 `NQ_DOCS_MIGRATION_MAP.md` §1E、G3 边界）。本轮 G2 不移除任何文件。
