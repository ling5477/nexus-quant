# 数据库证明参考

根据差异选择验证，以下是判断点而非逐项流水线：

- schema：类型、默认值、null、唯一键、外键、CHECK 与已存数据是否兼容。
- query/index：受影响条件、排序、选择性、分页和数据量假设；必要时查询计划或并发证明。
- migration：用项目当前 Flyway 配置确认版本顺序；在可丢弃库验证目标升级路径，禁止修复共享 Flyway 状态。
- backfill：批次边界、重复执行、处理中断、失败恢复、锁持续时间；没有回填就跳过。
- JDBC：按实际驱动和绑定方式验证 JSONB、时间和精度，不统一强制某个 Java 转换写法。
- recovery：区分事务回滚、前向修复与恢复备份；不能把未经验证的 destructive down migration 当作安全回滚。

验证证据注明候选、数据库版本、关键配置、输入、结果及未覆盖情况。命令从现有构建与测试入口推导，不在此冻结版本和全量命令。

## 结构完整性与兼容

- 新表/字段命名表达业务语义；主键、唯一键、外键和关键状态 CHECK 与领域约束一致。交易、风控、恢复、审计表保留幂等和关联追踪字段；表和关键字段有准确 COMMENT。不能为单次页面展示随意加表或省略核心关系约束。
- 检查新增/历史数据的默认值、nullability、空值映射，以及旧/新应用共存期间读写兼容；字段删除/改名/语义变化须说明调用方和兼容窗口。版本 migration 按 Flyway 顺序执行，不要求版本 DDL 任意重复运行；回填/helper 重跑须单独证明幂等或明确不安全边界。
- JSONB 使用当前 JDBC 驱动实际支持的显式类型绑定或 SQL cast；TIMESTAMPTZ 验证 UTC、offset、精度及往返。旧 `CAST(? AS jsonb)` / `Timestamp.from(Instant)` 是候选写法，不是可替代真实驱动证明的唯一规范。金额/数量按领域精度与 rounding 验证。
- 索引与查询的过滤、排序、锁、分页和数据分布一致；避免 N+1、无上限扫描/批量。回填需有分批边界、限流、进度/失败可观测性与中断续跑，检查大表锁时长和事务范围。
- 在可丢弃 PostgreSQL 上检查实际升级、Flyway validate 和受影响查询/约束的正负例；唯一键冲突、非法状态/null、并发写、事务回滚或独立事务可见性按风险选择。禁止改已执行 migration 或用 repair 改写共享 history。

事务模型还应按问题查 [Spring profile](../../../../docs/standards/java/spring-platform-profile.md) 与 [通用数据库/SQL标准](../../../../docs/standards/java/common-java-engineering-standard.md)。静态 DDL review 不等于 runtime PASS，恢复方案区分事务回滚、forward fix 和经验证备份恢复。
