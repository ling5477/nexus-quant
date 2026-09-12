# 可复用故障机制

重复故障或 workaround 增长时，先以原失败和实际调用链确认共同机制，再在授权范围内修复 owner；无需固定八项填空或新增 closure 文档。原失败、相邻变体和正常对照按影响保留，测试及审查统一见[回归与交付](regression-delivery.md)。

- 重放分类：在同一持久化边界先识别 NEW / ALREADY_APPLIED，再累计数量或账务；同 key 内容冲突必须拒绝。
- 并发投影：完整读写边界需要数据库串行化或 version/CAS，原子增量也需 exactly-once application。来源时间不是发布顺序，独立重建业务 oracle，不能只比较行数。
- 同态收敛：只跳过已完成的状态迁移，不跳过 fill、事件和账务恢复；不放开全局状态机。
- durable fan-out：必需派生事实与 source 原子提交，或在重启后依赖持久身份幂等重建。中间态需要 durable lifecycle writer 或明确的 unresolved 语义。
- 外部最终性：本地 OCC 不等于网络副作用 fencing。核对 sender 与 finalizer 的共享仲裁边界，证明旧 actor 恢复、owner death 与在途请求；lease、PID 或 timeout 不能证明外部动作已停止。
- admission 与执行：逻辑窗口唯一性由数据库原子 admission 保证；规范化后的有效执行参数在发送前成为不可变事实，恢复不得读取新配置重算。
- fixture 生命周期：失败、异常、超时和 setup failure 均回收所拥有的进程与资源；数据库前置数据按真实事务可见性准备。
- 导出身份：运行时随机身份与 tracked 表示分离，按字段保持稳定双射；不能通用清洗秘密、改变运行幂等性或扩大 scanner allowlist。格式取现有导出 contract；raw identity 及其 hash/base64/hex 替身不进入 Git。验证关系等价、原值无泄漏及秘密负例。
- checker 漂移：先区分真实违规、输入演进与过期注册；复用 canonical lifecycle 工具及既有授权，不以盲目刷新 hash 或新例外隐藏问题。

历史具体任务记录留在 history 中，按追溯问题另行查找，不从这里递归加载。
