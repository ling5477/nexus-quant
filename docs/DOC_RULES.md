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
