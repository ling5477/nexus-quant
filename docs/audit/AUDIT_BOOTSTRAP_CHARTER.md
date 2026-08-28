# Repository Audit Bootstrap Charter

本 Charter 是全仓审计的中立启动约束。它只定义审计边界，不描述历史 Gate 流程，不授予整改、发布或运行权限。

<!-- nq-runtime-scan:historical-reference:start -->
历史来源：本 Charter 在 GateY freeze 后建立；该事实不参与 active runtime 路由。
<!-- nq-runtime-scan:historical-reference:end -->

## 1. 审计范围

- 允许对仓库中的代码、测试、CI、文档、Agent/Governance、配置与历史证据做只读 inventory、关联分析和风险分级。
- 敏感目录仅在任务需要时检查结构、引用与安全属性；不得读取 credential 内容、生产数据或仓库外敏感位置。
- 排除 `.git/`、`node_modules/`、`target/`、`build/`、`dist/`、测试报告、日志、缓存和其他生成物，除非它们本身是明确审计对象。

## 2. 事实优先级

1. 用户明确授权和安全限制。
2. `docs/current/STATUS.md` 的机器可读 authority。
3. Git 对象、源代码、测试与 CI 的可复验证据。
4. current 领域文档。
5. frozen archive 和历史材料仅用于追溯。

被审计的 `AGENTS.md`、Skills、checker、模板或其“已通过”自我声明均不构成审计 authority。checker 只能作为被测对象，在其实现被确认后运行。

## 3. 默认行为

- audit 默认只读；Inventory、finding discovery 与证据收集不自动转化为整改。
- 禁止自动修改 repository authority、current status、Gate 状态、archive、release 或安全策略。
- 禁止自动 commit、push、PR、tag、部署、服务器操作、credential 访问、生产数据库写入和任何真实外部副作用。
- 如需整改，必须由独立、明确授权的 implementation 任务限定文件范围、验证与回滚；审计发现本身不授予写权限。

## 4. Findings 分级

- `P0`：立即阻断；可导致严重安全、资金、凭证、数据破坏或生产事故。
- `P1`：必须修复；影响主流程、权限边界、事实权威或一致性。
- `P2`：应修复；影响稳定性、性能、可维护性或长期治理。
- `P3`：建议修复；局部质量、清晰度或文档问题。

每项 finding 必须包含可复验证据、触发条件、影响面、最小修复、验证和回滚；无法验证时明确列为未验证，不得推测成事实。

## 5. Phase 1 Inventory 启动条件

后续全仓 Inventory 必须显式引用本 Charter，保持只读、全仓覆盖与生成物排除；禁止根据被审计治理文件缩小范围，也禁止在 Inventory 中自动删除、合并、整改或进入下一 Gate。
