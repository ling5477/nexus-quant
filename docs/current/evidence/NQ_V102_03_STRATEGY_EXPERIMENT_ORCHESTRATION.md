# NQ-V102-03 策略参数实验编排实施报告

- Task: `NQ-V102-03-STRATEGY-EXPERIMENT-ORCHESTRATION`
- Baseline / Starting HEAD: `origin/dev` / `855e64be4bcd7db417985ffb5c1ae39d550a91ea`
- Branch: `codex/nq-v102-strategy-experiments`
- 基线获取：两次 `git fetch origin` 因 GitHub 443 连接失败；GitHub 连接器实时查询远端 `dev`，确认与本地 `origin/dev`、起始 HEAD 一致。初始工作区干净。
- Final Staged Tree：以最终答复中的 `git write-tree` 实际输出为准；报告不内嵌自身的 tree hash。

## 实现

| 范围 | 实际实现 |
| --- | --- |
| Experiment Management | 实验、稳定试验、独立 attempt 三张表。数据内容及 SHA-256、标的、周期、时间、资金、成本、规则、候选和预算创建后冻结；RLS、父链校验和不可变触发器保护输入。 |
| Parameter Generation | SMA `window/investedExposure`、通道 `entryLookbackBars/exitLookbackBars/investedExposure`；显式集合或有限笛卡尔积；沿用 `SpotTargetStrategy` 校验，规范化数值后拒绝重复，最多 16 个候选。 |
| Batch Execution | 接入唯一 `SchedulerDispatcher`，没有新 Scheduler 或线程池。每 tick 最多处理一条试验；全局执行锁限制一次一条，实际每组并发 1，低于建议上限 2。原有任务默认开关不变，新增实验任务首次 materialize 时默认启用，已有控制不被覆盖。 |
| Formal Services | 版本通过 `StrategyVersionService` 创建；配置通过 `BacktestConfigService` 从可信持久模板派生；Run 通过 `BacktestRunService` 创建；执行和评价分别调用原 `BacktestExecutionService/BacktestEvaluationService`。未直接批量插入这些业务表。 |
| Frozen Assumptions | 首 Run 通过原正式服务冻结公开规则与费用来源；其余候选及重试通过新正式服务入口引用原 Run 假设，校验相同研究父对象、原配置口径和冻结数据。不会逐候选读取不同的当前规则。 |
| Idempotency | 复用 `ResearchCreationOperationService`；同操作 UUID、所有者和输入返回原实验，输入冲突为 409。每次试验绑定唯一版本、配置、Run；retry 的 UUID 和 `(trial, attempt)` 均唯一。 |
| Restart Recovery | 领取持久化后再执行；正式执行、结果和 attempt 终态在同一事务提交。重启先查原 Run，确认 CREATED 才执行；SUCCEEDED/FAILED 读取已有事实；其他未决状态标记 OUTCOME_UNKNOWN，不自动产生新 Run。 |
| Failure / Retry | 正式执行已保存的 FAILED 与失败原因提交保留；只有失败终态允许显式新 attempt，最多 3 次。原失败版本、Run 和结果保留。SQL/未知异常不伪装成已保存的业务失败。 |
| Cancellation | 与领取共用实验父锁；取消尚未领取任务，不破坏已领取执行事务。组状态由最新 attempt 事实计算，区分部分失败、失败、取消、未知和全部成功。 |
| Result Comparison | 有界 SQL 连接读取正式 Run 和最新 Evaluation，展示净收益、收益率、回撤、非年化 Sharpe、交易数、成本、Benchmark 和相对差。缺失展示 UNAVAILABLE；口径不符标记不可比，不做自动排名、选优或发布。 |
| Frontend | `/experiments`，研究导航和工作流入口；创建、候选数量预览、进度、失败、取消、attempt 历史、选择比较、回测/评价跳转。中英文、窄屏、真实认证。刷新/重新登录只读恢复，不隐式写入。创建与 retry 发送前保存原 UUID；未知结果保持原身份，明确拒绝才允许放弃。 |

## 验证与证据

本地日志位于任务忽略目录 `artifacts/`，不提交运行时数据库凭证或合成环境连接身份。

| 验证 | 结果与证据 |
| --- | --- |
| Backend / PostgreSQL 16 | `nq-v102-experiments-targeted.log`：45 项通过、0 skipped。PG 16.15 专属容器 `nq-v102-experiments-pg16`，每例独立随机数据库、NOLOGIN owner 和受限 runtime 角色。实际执行 V1→V48、Flyway validate。 |
| SMA / Channel batch | 两类各四个不同参数候选，通过正式 Java 主链，持久化准确版本/Run/Evaluation；另建独立实验复算净收益、收益率、回撤、Sharpe、交易数和费用一致。 |
| Replay / Concurrency | 同操作重复 HTTP 返回同一对象；两线程并发创建同操作仅产生一组事实；不同输入同操作为 409。 |
| Failure / Retry | 实际非法执行假设产生 FAILED，三次不同尝试保留三个失败 Run；重放同 retry 身份不新增 attempt，超出预算拒绝。 |
| Interruption / Restart | 在真实 `sim_pnl_snapshots` INSERT 处用数据库锁建立切点，强制终止执行 JVM；确认无半套结果、原 Run 为 CREATED、已领取 attempt 为 RUNNING；新 JVM 恢复同四个 Run，完成后再次推进无重复结果。 |
| Final backend / cancellation | `nq-v102-experiments-access-final2.log`：最终版本核心 PG 用例通过，包含并发推进、执行中取消（当前事务完成，其余三条取消）、执行进程强杀/恢复，以及越权取消/重试 404。`nq-v102-experiments-scheduler-final.log`：默认启用任务具有首次到期时间，调度控制回归通过。 |
| Tenant Isolation | 另一用户及另一租户不能读实验；跨租户版本/Run 读取拒绝。现有 `TenantIsolationReviewRegressionIntegrationTest` 4 项通过，保留 ACCOUNT 只读对账和显式 `pg_temp` 解析合同。 |
| Single backtest / Evaluation | 原研究创建、回测执行、评价、策略版本以及 SMA/通道单元回归通过。 |
| SIM Regression | `nq-v102-experiments-sim-regression3.log`：研究服务 9 项、正式 SIM/调度控制/双 Dispatcher 锁 3 项通过，0 skipped。 |
| Frontend build | `nq-v102-experiments-frontend-final3-build.log`：`tsc -b && vite build` 通过；保留既有 bundle 大小提示。 |
| Playwright | `nq-v102-experiments-e2e11.log`：2 项通过（46.5s），真实调度器自动执行两类四候选、比较/详情、取消、刷新/重新登录及响应丢失恢复。最终折叠完整来源后，`nq-v102-experiments-e2e12.log` 业务链补跑 1 项通过（36.2s）；`nq-v102-experiments-e2e13.log` 390px 只读恢复 1 项通过，桌面及窄屏截图已检查。使用专属随机数据库和真实 Servlet，不以 mock 成功代替批量回测。 |
| CI | 已接入现有 PostgreSQL/Flyway job。未 Commit/Push，因此远程本候选 CI 为 NOT_RUN，不声称远程交付通过。 |

复现后端核心测试（连接参数必须指向可丢弃 PG16）：

```text
mvn -f backend/pom.xml -pl nq-app -am test
  -Dtest=ExperimentParametersTest,StrategyExperimentPostgresIntegrationTest,TenantIsolationReviewRegressionIntegrationTest
  -Dsurefire.failIfNoSpecifiedTests=false -Dnq.tenant.pg.required=true
  -Dnq.tenant.pg.url=<disposable-loopback-url> -Dnq.tenant.pg.user=<installer> -Dnq.tenant.pg.password=<local-only>
```

浏览器夹具：使用 test classpath 启动 `StrategyExperimentBrowserFixtureMain <fresh-ready-path> <fresh-stop-path>`，传入 `TASK_EXPERIMENT_PG_URL/PASSWORD`。将 ready JSON 的端口作为 `VITE_API_PROXY_TARGET`，设置 `NQ_EXPERIMENT_E2E_READY`，运行 `npm run test:e2e -- --config=playwright.experiments.config.ts`。结束时写 stop 文件，应用关闭并清理本夹具数据库。

## 审查、迁移与限制

- 完成一次真正独立的定向审查及受影响修复补审。初审 5 项问题均按定位修复；补审指出的旧拒绝标记在重新发送前清除，补充响应丢失测试。审查没有修改候选，没有扩大成全量审计。
- Migration：仅新增 `V48__strategy_experiments.sql`；历史 migration 未修改。V48 扩展既有后台工作身份，保留 V44 对账例外及 SECURITY DEFINER 解析保护，不回填或重写旧经济事实。
- 第一批复用现有 `FrozenHistoricalInput`：完整小时、OKX SPOT BTC-USDT/ETH-USDT、最多 720 根 bar；不接受缺少内容冻结的数据目录。模板选择支持服务端按名称或准确 ID 搜索；完整冻结规则折叠展示并限制滚动高度。首 Run 的公开规则来源须按既有运行配置可用；测试使用隔离合成公开响应，不连接真实 provider。
- 不包含 Walk-Forward、DSR/PBO、Factor Library、AI、自动发布、Continuous SIM 创建或 LIVE 操作。Python 没有新增 Java 运行时依赖。
- 初次回归失败与修复后成功分别保留。新增任务使调度控制总数由 8 变为 9，相应集成断言已同步并验证新任务间隔。开发中浏览器使用声明/Ant Design Select 夹具问题不计为业务 PASS。
- Remaining Findings：远程 CI 尚未运行；本地定向验收不代表发布或生产部署。

Decision: `IMPLEMENTED / TARGETED_VALIDATION_PASS`（本地定向范围；远程 CI 为 NOT_RUN）

Commit: `NOT_COMMITTED`
Push: `NOT_PUSHED`
Release: `NOT_CREATED`
LIVE: `DISABLED`
