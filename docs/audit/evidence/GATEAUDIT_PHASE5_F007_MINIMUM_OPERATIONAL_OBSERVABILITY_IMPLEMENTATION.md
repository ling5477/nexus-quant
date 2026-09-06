# Phase5 F007 最小运行观测实现

- Task：`NQ-GATEAUDIT-PHASE5-F007-MINIMUM-OPERATIONAL-OBSERVABILITY-IMPLEMENTATION`。
- Classification：`BACKEND_IMPLEMENTATION / MINIMUM_OPERATIONAL_OBSERVABILITY / TESTS / SELF_REVIEW`；跨模块 wiring 按高风险架构约束检查，用户明确指定本轮不另开独立 Review。
- Starting HEAD/origin：`6b1516537fdf2b14b92afc1a2aa3221a18f1c719`；branch：`audit/post-gatey-agent-baseline`；fetch 后一致、初始工作区干净、staged=0。
- F008 immutable technical acceptance pair 保持 `614359fc7f25227f736fbb1c11c7d584da1f0627 / 33978394774`。
- Primary Skill：`java-backend-maintenance`；supporting：`nq-java-engineering-standard`，触发点为跨模块依赖及 Spring composition。

## 1. 修改前 inventory 与最小矩阵

| Capability | Owner / 实际入口 | Current observable signal | Missing signal | Proposed metric / health signal | Mutation / side effect |
| --- | --- | --- | --- | --- | --- |
| scheduler / worker | `nq-scheduler`：`ValidationEvidenceScheduler.runOnce`，由专用 scheduler configuration 显式启用；Ledger/OKX scheduled 入口委托相应单轮方法 | 固定结构日志、lock execution status、refresh result；REUSE | 没有统一执行计数和最近执行/成功/失败时间 | `validation_refresh` attempt/success/failure/degraded/skipped 与时间戳；跳过不计成功 | 不增加调度、锁、线程、重试或查询 |
| reconciliation | `LedgerReconcileScheduler.reconcileOnce`、`OkxRestReconcileService.reconcileOnce` | 现有 ledger diff query、审计、adapter result；REUSE | 缺统一运行与 unresolved 信号 | `ledger_reconcile` 运行结果及最近差异快照；`okx_reconcile` 运行结果和累计 unresolved 观测事件 | 复用原查询结果，不在 scrape/health 中查库 |
| ledger recovery | `OkxRestReconcileService.ensureLedgerConvergence` 的 `recovery=true` 路径；调用 `TradeLedgerGateway`，最终由 `nq-ledger` 的既有 transaction 完成 | durable Trade 重放、`LedgerPostingResult`、`OKX_LEDGER_RECOVERY_COMPLETED/OKX_LEDGER_POST_FAILED` 审计；REUSE | 没有 recovery attempt/success/failure/time 信号 | `durable_trade_replay`；posted=false 计失败，幂等命中正常计成功，拒绝另计 unresolved 事件 | 不改 `nq-ledger` transaction、SQL、幂等、状态机或事实 owner |
| critical alert | `nq-research`：`PaperRunMonitorService.createAlert`，由已有 monitor run/API 调用 | `PaperRunAlertRepository.insert`、severity/status、已有告警 API；REUSE | 无 critical emitted 总量 | `critical_alert` 在 CRITICAL insert 成功后计 emitted | 不重复插入、发送通知、更新状态或执行 active-count 查询 |
| existing observability | `nq-observability` 的 trace filter；`nq-app` Actuator；monitoring/incident overview API | trace、health/info、read-only diagnostics、持久化告警/审计事实；REUSE | 无上述统一最小运行摘要 | Micrometer 与现有 health 的 `operational` 安全摘要 | HTTP exposure、F008、deployment ingress/config 均不变 |

其他已定位入口包括 `PaperMatchingService`、`BinanceRestReconcileService`、`OkxRecoveryService`、`BinanceRecoveryService` 及其现有日志/审计。它们不新增另一套调度框架；本次统一基线的固定 operation 集合限定为表中五项，不声称覆盖所有后台方法或所有 venue。OKX startup/recovery 调用既有 reconcile 时自动复用本次信号。

告警 repository 仅有按 run/date 范围的统计接口，没有可直接复用的全局 active critical 数；未新增 gauge。Ledger recovery 没有既有全局 pending query，未构造 pending/lag。现有监控和告警 API 继续拥有业务详情。

## 2. 指标与健康契约

| 名称 | 类型 | 含义 |
| --- | --- | --- |
| `nq.operational.executions` | Counter | `result=attempt/success/failure/skipped/degraded`；按 operation 固定区分 |
| `nq.operational.last.execution` | Gauge | 最近开始执行的 epoch seconds |
| `nq.operational.last.success` | Gauge | 最近成功完成的 epoch seconds |
| `nq.operational.last.failure` | Gauge | 最近执行失败的 epoch seconds |
| `nq.operational.last.degraded` | Gauge | 最近降级或 unresolved 事件的 epoch seconds |
| `nq.operational.unresolved.snapshot` | Gauge | 仅 `ledger_reconcile` 最近一次成功完成查询/审计后的差异数 |
| `nq.operational.unresolved` | Counter | `okx_reconcile` / `durable_trade_replay` 观测到的未解决事件累计次数；重复扫描可重复计数，不是唯一订单数或实时 backlog |
| `nq.operational.alerts` | Counter | `critical_alert/result=emitted`，进程内成功写入的 critical 告警次数 |

- 所有数值均为进程内观测，重启重置。初始 timestamp/snapshot=`-1` 表示未知；health 的 `observed=false` 表示未观测，不意味着成功或组件已启用。
- 首次记录时延迟注册相关 meter；该 operation 的结果计数从零开始。固定 `EnumMap` 状态、原子数值及每个 operation 的同步边界保证并发安全，无动态 key/cache。
- `success` 表示相应调用正常完成。Ledger reconcile 存在 diff 记 degraded；OKX reconcile 正常返回仍可能含 unresolved，其独立计数与最近降级时间必须一起解释。Recovery 按 `posted` 判断成功/失败，不能由新 Trade 数量推断。
- health 使用相同 meter 的计数及时间状态，只返回固定摘要；业务失败仍为 `UP`，不取代基础设施健康、readiness 或交易授权。摘要读取异常仅返回 `observation=unavailable`，不包含异常信息。
- exposure 仍为原有 `health,info`，未添加 HTTP metrics endpoint；生产诊断可使用现有 health 摘要。Actuator `MetricsEndpoint` 能从实际 MeterRegistry 读取相同指标，已由上下文测试验证。

## 3. 依赖、安全及无副作用自审

- `nq-observability` 拥有 typed port、NoOp、失败隔离 adapter 与 Micrometer implementation；`nq-app` 只负责 Bean/HealthIndicator 装配。
- 仅 scheduler/research 新增对 `nq-observability` 的依赖；后者不依赖业务模块。`nq-core`、`nq-ledger` 没有反向依赖 app，也未修改这两个模块实现。
- Micrometer compile dependency 仅在 `nq-observability` 新增，版本由现有 Spring Boot BOM 管理，实际库已由 Actuator 使用，无新监控平台。research 新增已有仓库测试栈 `mockito-core` 的 test scope 依赖，用于证明 insert 失败及异常身份；版本仍由 BOM 管理，无依赖升级。Micrometer/Mockito 均为 Apache-2.0；无新运行时网络客户端。
- 标签只允许固定 enum 生成的 `component/operation/result`，没有 request/trace/order/trade/account/run/symbol、exception text 或 payload。观测 port 本身不接受任意字符串。
- 原构造入口保留 NoOp；Spring 使用观测构造入口，Validation scheduler 的隔离装配通过 ObjectProvider 在独立上下文回退 NoOp。
- 只隔离观测记录的 RuntimeException，固定脱敏告警每个调用方最多一次；不吞业务异常，不传递异常对象至指标，不重试业务。资源耗尽等 JVM 致命 Error 不作为普通可恢复 telemetry failure 吞掉。
- 调度、retry、audit 顺序、业务返回值、异常身份、ledger transaction、Trade/Order 状态机均保持原语义；真实 provider、credential、LIVE、migration、frontend、F009 与 frozen history 均未触达。
- 架构标准适用项：module direction / owner / Spring composition / relevant ArchUnit=`PASS`；transaction mutation、executor changes、platform upgrade、全仓 static rule change=`NOT_APPLICABLE`。

## 4. 验证与失败记录

最终验证结果在本证据末尾登记。

- 初始 Python helper 默认 GBK 解码失败；仅已完成 POM 插入，随后使用显式 UTF-8 重跑剩余修改。
- 初始 Maven 参数未引号保护，PowerShell 将 `.failIfNoSpecifiedTests=false` 解析为 lifecycle；退出 1，随后修正参数引用。
- 新测试编译期间修正 SimpleMeterRegistry 非 AutoCloseable、research 缺 Mockito 测试依赖；失败 stub 重设改用 doReturn，未削弱任何业务断言。
- 首次模块完整测试默认连接已有本地 PostgreSQL，在 V45 的 `exchange_accounts` 外键约束处失败，nq-app 13 个上下文 errors。未修改历史 migration 或修复共享数据，不把这次运行记为通过。
- 随后按现有 CI 的 `BackendCiLegacyAccountFixture`，使用锁定 digest 的 disposable PostgreSQL，仅绑定随机 loopback 端口；只创建测试数据库/fixture。Full Maven 自带的 PostgreSQL 测试使用该隔离库，不新增 F007 DB query 或额外 PG16 qualification。
- 本地日志位于 ignored `artifacts/f007-*.log`，不提交生成物或测试连接材料。

## 5. Authority 与交付边界

- 实现结论目标为 `IMPLEMENTED / SELF_REVIEWED / PENDING_EXACT_HEAD_CI`，不是 `ACCEPTED/CLOSED`。
- 现有 governance 只允许 `IMPLEMENTED|SELF_REVIEWED` 配对 COMMIT next action，且 commit=NONE/CI=NOT_RUN。实现提交中的 machine authority 保留这个合法提交前快照；不修改 matcher，不用占位 SHA 假冒已提交事实。
- 用户已授权精确暂存、commit、push 与 exact-head CI；CI 结果通过 GitHub run 的 `headSha` 与远端 HEAD 绑定，报告在任务交付中给出，正式写回 acceptance 留给下一任务。
- 下一任务：`NQ-GATEAUDIT-PHASE5-F007-MINIMUM-OPERATIONAL-OBSERVABILITY-POST-CI-AUTHORITY-ACCEPTANCE`，仅在 exact-head CI 成功后执行。F009 仍保持未开始。
- 回滚：针对本次精确文件集合生成并审查反向补丁，运行相关回归；不 reset/rebase、不回写 migration/数据库，也不改变 F008 acceptance pair。
- 自审 findings：P0=0、P1=0；本次范围内 P2/P3=0。全 venue instrumentation、外部 exporter/持久化趋势与真实全局 backlog 不属于本次最小基线。

## 6. 最终本地验证结果

| 验证 | 命令 / 证据 | 结果 |
| --- | --- | --- |
| 最终针对性测试 | `mvn -f backend/pom.xml -pl nq-observability,nq-scheduler,nq-research,nq-app -am test -Dtest=OperationalReconciliationMetricsTest,OperationalSchedulerMetricsTest,OperationalCriticalAlertMetricsTest,OperationalObservationConfigurationTest,MicrometerOperationalObservationTest -Dsurefire.failIfNoSpecifiedTests=false` | PASS；10 tests，0 failures/errors/skips；exit 0 |
| 受影响模块完整测试 | `mvn -f backend/pom.xml -pl nq-observability,nq-scheduler,nq-ledger,nq-core,nq-research,nq-app -am test`，隔离CI fixture | PASS；23/23 modules；1782 tests，0 failures/errors，53既有条件跳过；exit 0 |
| Full Maven | `mvn -f backend/pom.xml test`，同一隔离CI fixture | PASS；23/23 modules；1783 tests，0 failures/errors，53既有条件跳过；exit 0。比上一轮新增1个health失败读取用例 |
| PostgreSQL | Full Maven既有测试使用disposable PostgreSQL；没有新增query/qualification | 既有测试通过；测试容器已按已知ID核验并删除，残留0 |
| Frontend | 未触达frontend | NOT_REQUIRED / NOT_RUN；远端既有required job仍照常执行 |
| Authority | PS5.1与PS7分别运行 `scripts/docs/check-current-authority.ps1` | PASS；errors=0，exit 0 |
| Next action | `scripts/docs/test-current-authority-next-action.ps1` | PASS；failed=0 |
| Lifecycle | `scripts/docs/test-governance-workflow-lifecycle.ps1` | PASS；20 passed，0 failed |
| Agent workflow | `scripts/docs/test-agent-workflow-fixtures.ps1` | PASS；12/12 fixtures，6/6 malicious mutations rejected，drift=0 |
| Doc links | `scripts/docs/check-doc-links.ps1` | PASS；273 checked，123历史warnings，0 errors |
| Diff | `git diff --check`，逐文件scope review | PASS；无whitespace错误，非本任务实现变更0 |

测试报告数字按该次Maven日志逐个test-class结果汇总；跳过项没有作为通过的业务证明。最终针对性复验额外断言账本拒绝同时产生reconciliation/recovery unresolved计数。

工具声明：本地PowerShell、Python UTF-8 helper、Git、Maven/JDK、Docker与GitHub CLI；MCP未使用；Skills如开头所列。网络用于origin fetch、授权push与GitHub CI dispatch/查询；本地PostgreSQL使用缓存锁定镜像，未拉取新镜像、未连接生产/provider。写操作仅本任务22个受审查文件、ignored测试生成物及已清理的隔离测试容器；不提交连接材料。
