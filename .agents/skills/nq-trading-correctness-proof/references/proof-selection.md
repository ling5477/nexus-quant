# 证明选择

交易状态与副作用：断言外部操作前的风控、当前状态校验、唯一业务身份、持久化结果和可观察审计，避免只有 HTTP 200 或 mock 被调用。

超时、重复与并发：先说明预期唯一性和终态，再选择竞争、重复事件、accepted 后超时、乱序或补偿场景。不能为了简化测试忽略失败路径。

恢复：同一持久状态经真实进程退出与重新启动后继续处理，验证状态与副作用均不重复。仅重建对象不能替代重启证明。

架构与平台：跨模块变更检查依赖方向与相关 ArchUnit；版本升级读当前 POM、CI 和 runtime 配置。`docs/standards/java` 是按主题查询的参考，不要求每次加载整个标准库或扫描所有 Shadow 规则。

证据复用：记录代码候选、测试/fixture 身份、环境、命令和结论；代码、依赖、配置、fixture 或覆盖假设变化时评估失效范围。独立 reviewer 自主选择关键复现；发布验收仍遵守实际 exact-head CI 合同。

## 关键交易/数据库断言

- Order 身份、venue order/fill、Trade、Ledger 和 audit 关联必须来自实际可达 producer/consumer 路径；测试直接造出的 fill 只能证明该输入之后的行为，不能宣称完整交易因果成立。
- 对 reconciliation 保留重复回报、乱序、accepted 后 timeout、本地终态与 venue fill 冲突、账务部分失败后的恢复场景；断言成交/账务唯一性及相关数量、精度、关联、最终收敛，不将本地 CANCELLED 当作永无成交的证明。
- 并发更新验证 stale result 不覆盖新状态，必要时包含状态往返后的 version 冲突；唯一约束、锁或 CAS 拒绝必须可观察。事务失败时确认多表写入/审计一致性与可恢复事实，而不只断言异常。
- 扫描候选必须有界且可持续覆盖：按当前契约检查 filter-before-limit、稳定排序与分页/游标、重复旧前缀和跨批进展；当前 reconciliation 的 `limit` 是单次扫描总上限，环回也不能按 lane 各给一份预算。
- 数据库事务证明使用已提交 fixture 和隔离 schema/数据库，明确 isolation、锁竞争和独立事务可见性，避免外层测试回滚让 `REQUIRES_NEW` 读不到夹具。记录数据库/扩展配置；不修复共享数据库状态来通过测试。

工程/安全条目按需查 [工程边界](engineering-boundaries.md)，测试分层与 evidence identity 查 [回归与交付](regression-delivery.md)。这些是证明选择，不宣称现有测试覆盖所有风险。
