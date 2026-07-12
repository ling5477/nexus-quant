# GateV Runtime Scheduling Boundary Summary

GateV-3A 提供 `SchedulerExecutionLock` contract 与 PostgreSQL 实现，使用 `pg_try_advisory_xact_lock(int,int)`。lock key 由 SHA-256、UTF-8、big-endian 规则稳定映射；执行位于 `REQUIRES_NEW` read-only transaction，并有 bounded timeout 与 contention safe-skip 语义。

GateV-3 新增独立 validation evidence scheduler，配置默认 `enabled=false`，CI 固定关闭。每次只在获得 lock 后调用一次本进程 GateU aggregate query；禁止 HTTP fan-out、retry storm、review case creation、lifecycle action、durable execution history 或业务表 mutation。

Scheduler 不调用 exchange adapter、credential、account、balance、order、ledger、Paper/Shadow mutation、real provider 或 private endpoint。失败只形成脱敏 operational audit/metrics；lock 未获得时安全跳过，不补跑、不并发重叠。

## 验收边界

- lock acquisition 失败或 contention 不触发 aggregate callback。
- callback 每次成功 lock 最多调用一次本地 aggregate query。
- 无 retry、无内部 HTTP、无 case/event lifecycle mutation。

测试覆盖 disabled/no-bean、property validation、lock acquired/contended/failed、bounded batch、timeout、single aggregate invocation 与 safe failure。Fresh PostgreSQL 的 advisory lock integration 为 1 passed；全量 backend suite 通过。

限制：transaction timeout 能约束 JDBC/transaction 操作，但无法主动终止不响应 interrupt 的任意非 JDBC 无限阻塞代码。当前 scheduler callback 是 bounded 本地只读调用；未来若扩展 callback 必须另轮评审，不能借 GateV freeze 获得运行授权。
