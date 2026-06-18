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
