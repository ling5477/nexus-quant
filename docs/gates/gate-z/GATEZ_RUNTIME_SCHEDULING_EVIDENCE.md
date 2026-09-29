# GateZ runtime and scheduling evidence

当前后台调度 owner 是 `SchedulerDispatcher`、`ScheduledJobRegistry` 与 V57 `scheduled_job_controls`。固定 registry 包含 `CONTINUOUS_SIM_POLL` 和 `PAPER_MATCHING`；启停、下一次运行、结果及 actor 有持久化/审计，不从页面伪造后台执行。

- Dispatcher 按持久化控制状态触发已注册任务。
- Registry 限定固定 job key，不接受任意类名或脚本。
- V57 初始八个 job 均 disabled；启用需管理动作。
- `CONTINUOUS_SIM_POLL` 只消费可信 closed bar 并维护 per-run 游标。
- `PAPER_MATCHING` 将已接受 SIM Order 收敛到 canonical Trade/Ledger。
- 乐观版本与执行锁限制管理竞争及重复运行。

PR #52 建立 Control Plane，PR #53 提供管理 UI，PR #54 修复真实 JWT actor 并在 Spring Boot + PG16/V57 上观察 `CONTINUOUS_SIM_POLL` OFF→ON→OFF、自动 SUCCESS 和 RUN_ONCE 审计。自然 Continuous SIM order 与 Paper Matching 的历史运行证据用于证明后台经济链；重复推进与重启必须保持同一 logical run 的去重身份。

旧 `@Scheduled` ownership、custom executor 与 scoped `ScheduledAnnotationBeanPostProcessor` 只作为迁移历史；当前 owner 以上述 dispatcher/registry 为准，不恢复第二套 scheduler。V57 默认 `enabled=false`，freeze 不启动生产任务，也不授权 LIVE 或真实 provider。
