# NQ-V102-04-WALK-FORWARD-AND-ROBUSTNESS

Decision: `IMPLEMENTED / TARGETED_VALIDATION_PASS`（本地定向范围，远程本候选 CI 为 `NOT_RUN`）。

## 基线与身份

| 字段 | 真实身份 |
| --- | --- |
| Task | NQ-V102-04-WALK-FORWARD-AND-ROBUSTNESS |
| Baseline | dev / origin/dev，V48 |
| Branch | codex/nq-v102-walk-forward |
| Starting HEAD / final HEAD | 91ddeb8162297254ff5af19a6ed4e9bc4974f80e（未提交） |
| Previous acceptance | PR #85，候选 146fc68264898deda60e2e8dff8206ecaf78dd03 / CI 40444305427；合并 91ddeb8162297254ff5af19a6ed4e9bc4974f80e / CI 40445368734，均9/9成功。本轮在线核实，非本候选CI。 |
| Final Staged Tree | 本报告纳入 index 后执行 git write-tree；准确值记录于最终交付答复及本地 artifacts/nq-v102-walk-forward-staged-delivery.json，避免报告内容自身形成哈希自引用。 |
| Independent candidate | 22个技术文件，最终manifest SHA256=6886ca8458fddae7365eeaa403de97b11798452ae126c3e902992a871540ed78；另有本报告和STATUS两份文档。 |

开始前已 fetch，确认分支、HEAD、origin/dev 相同且工作区干净；从 origin/dev 创建用户指定分支。没有覆盖已有工作。

## 实现

| 项目 | 合同与结果 |
| --- | --- |
| Validation Policy | EXPANDING_WALK_FORWARD_V1；最多3个WF折，每折候选沿用原实验集合、最多16个。最后另有一次最终训练。全局实际执行仍串行，复用原Worker的全局执行锁。 |
| Fold Generation | 固定 Train/Test 为 [0,240)/[240,360)、[0,360)/[360,480)、[0,480)/[480,600)。最终训练 [0,600)，Holdout [600,720)。索引左闭右开，预览同时保存UTC起点、排他终点、数量。数据不足720根记录INSUFFICIENT_DATA，零步骤。 |
| Training Selection | 仅训练成本后收益降序、最大回撤升序、稳定源候选ordinal升序。全部训练Trial/Attempt/Run/Evaluation保留；选择保存全部合格排名、源Trial、原版本、训练Run/Evaluation及冻结时间。没有合格候选记录NO_ELIGIBLE_CANDIDATE，不创建Test。 |
| Independent Test | 新增FLAT_WINDOW_V1冻结配置；完整Dataset先校验，计算只读截至窗口终点的历史前缀。窗口起点资金10000、持仓与策略状态FLAT；之前bar只用于指标，不产生预热订单、成交或权益快照。严格NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT规则未放宽。Benchmark与正式评价仅使用窗口区间。 |
| Final Holdout | 所有WF折结束后，全候选在前600根重新训练，冻结选择后才创建最后120根的Run；训练选择时间与评价时间持久关联。保留显式查看UUID/时间、验证历史和同数据集验证次数。 |
| Cost Sensitivity | 每个Test/Holdout分别以原成本、手续费压力和滑点压力进入正式执行器，选择参数保持不变。标量成本压力=max(原值×2, 原值+0.001费率或10bps)，上限分别0.1/1000bps；本次实际0.001→0.002 FRACTION、10→20 BPS。来源RESEARCH_HYPOTHESIS_V1，保存具体基线/压力/单位。私有或不足以可信比较的输入为UNAVAILABLE。输出净收益、收益率/回撤/交易数变化、Benchmark相对差及不可比原因；没有在净收益上二次扣滑点。 |
| Parameter Stability | 全部候选的四段训练排名、每折选中候选/版本、仅一个参数轴相邻的候选收益差、独立OOS结果、三折收益算术均值和最大单窗口绝对收益占比。占比≥80%标记高度依赖；零绝对收益或未完成有明确UNAVAILABLE原因。均值不声称是连续组合收益。 |
| Persistence | 仅新增V49，新增验证、步骤和显式Holdout查看记录；原Trial/Attempt/Run/Evaluation不复制。步骤引用原实验引擎派生组和原冻结版本，不新建调度器。政策/步骤身份/已冻结选择不可变、源/子组关联与FORCE RLS、每源最多一个RUNNING验证、步骤有界。 |
| Recovery / Cancel | 原创建回执处理相同UUID重放和并发；原Run事实与Attempt终态原子提交。未知既有执行结果保留OUTCOME_UNKNOWN，不换Run重试。取消与编排共享锁，只取消未领取工作，当前执行事务可完成；已提交经济事实保留。派生组拒绝单独取消/重试，父源有验证时要求显式取消验证。 |
| Frontend | 在/experiments现有详情增加验证视图；预览、冻结政策、状态、训练/Test/成本对比、排名/相邻候选、Holdout与原详情链接。保留Ant Design/NQ tokens、中英文和窄屏。查询用TanStack Query；创建发送前保存UUID，响应丢失只能显式沿原身份恢复；刷新/重新登录不隐式写入。 |

另修复原Worker的并发锁顺序：领取先获取与执行相同的全局锁，避免领取事务持父锁等Attempt、执行事务持Attempt等父锁形成死锁。没有新增并行执行能力或改变Canonical Order/Trade/Ledger。

## 真实业务样本

测试使用上一轮留存的真实公开BTC-USDT 1h完整720根，2026-09-26T00:00:00Z 至 2026-10-25T23:59:59.999Z；冻结内容SHA256=`cb333d1a0f742fe149e2e030818858e50b49c5143b3c964d77c8fc5b6d6f5bcb`。资源在新数据库内经正式Dataset服务重新绑定、复核同摘要；策略、版本、源实验、验证、配置、Run与Evaluation均为新身份。公开规则使用现有已捕获响应的本地受控服务，执行不访问真实provider。

| 策略与候选 | 三折选中候选 | 三折独立OOS收益率 | 最终Holdout收益率 |
| --- | --- | --- | --- |
| SMA，window=4/20，exposure=1 | #2 / #2 / #2 | -0.023901601984040800 / -0.042911672738858300 / -0.049287393016589600 | -0.035425198093629200 |
| 通道，entry=10/20，exit=4，exposure=1 | #2 / #1 / #2 | -0.020686521650076100 / -0.022033579240730800 / -0.014514372851605300 | -0.008436427538692000 |

最终600根训练两类都选#2。三折独立收益均值分别-0.038700222579829567、-0.019078157914137400；没有把折利润相加或声称盈利。两类每个完整验证均为16步骤、20正式Run（4×2训练候选、4独立评价、8成本压力）。每次Test/Holdout独立资本与FLAT重置，不代表真实连续账户。

源实验已经对全部720根做过回测，因此最后120根**不是从未观察的独立样本**；它们仅与本轮算法的训练选择隔离。政策与页面明确披露这一点及重复使用记录，不能通过重新运行消除已有暴露。

## 验证

原始日志、运行JSON、截图、审查oracle及manifest在任务忽略目录artifacts/中，不提交数据库凭据或运行认证状态。

| 验证项 | 证据与实际结果 |
| --- | --- |
| Backend | nq-v102-walk-forward-targeted-second.log：53 tests，0 failures/errors/skips，BUILD SUCCESS。覆盖原实验、参数展开、单次回测/评价、ResearchValidity、SMA/通道策略、冻结输入、租户回归及本轮PG主链。列表一行修复之后由下面最新PG和浏览器结果补充覆盖。 |
| PostgreSQL 16 | 任务专属postgres:16.15容器nq-v102-walk-forward-pg16；每例新随机数据库、NOLOGIN owner、受限runtime。实际V1→V48、带既存用户/租户数据V48→V49、Flyway validate及不可变/RLS正负例。未使用共享数据库代替。 |
| Latest negative / idempotency | nq-v102-walk-forward-final-negatives.log：最终PG主用例通过，零跳过。覆盖HTTP重放/并发同身份、列表排除派生组、同租户其他owner和跨租户拒绝、政策/选择更新拒绝、取消前及执行中取消、持久PREPARING未知结果不自动重试、受控FAILED训练无合格候选、32bar不足且零步骤。 |
| Temporal Leakage | 固定边界断言、训练最优与Test最优相反的显式得分、修改Test得分不改变训练选择、固定tie-break。真正独立oracle另执行8组未来OHLC×3反事实，训练cash/equity/fee/slippage/tradeCount/decisionReasons/消费摘要全相同。 |
| Independent Test | 两类真实数据的持久订单/成交/PnL均限定区间；首条资金10000和持仓市值0、PnL数量等于正式bar数量、无预热订单/成交、最早成交满足严格next-open。新Run经正式执行器独立复算Test，净收益/收益率一致。 |
| Cost Sensitivity | 两类各8个压力场景均实际重算。独立oracle逐笔以已含滑点成交价格和手续费重建现金，最终现金一致；没有第二次扣减滑点。不能提供可信比较的成本来源按UNAVAILABLE合同处理。 |
| Restart Recovery | 真实sim_pnl_snapshots INSERT数据库锁切点强杀执行JVM：原Run回滚为CREATED、Attempt保留RUNNING、零半套快照；另一新JVM继续原验证和Run，最终20Run，重复推进无新增交易。不是只重建Spring Bean。 |
| Tenant Isolation | 普通HTTP其他owner404、另一租户读不到验证/步骤/关联Run；原TenantIsolationReviewRegressionIntegrationTest 4项通过。独立PG只读检查三张新表均ENABLE/FORCE RLS。 |
| SMA / Channel / SIM regression | nq-v102-walk-forward-final-pg-sim.log：6 tests零跳过，包括本轮3个政策单元、PG主用例，以及原continuousClosedBarPersistsCursorAndRecoversLostAcknowledgmentWithoutDuplicateFacts和channelContinuousRestoresIntentAfterRollingWindowAndFreshJvm两项真实PG/新JVM回归。 |
| Frontend | nq-v102-walk-forward-frontend-final-build.log：tsc -b与Vite构建通过。保留既有bundle体积/Vite配置提示，未弱化检查。 |
| Playwright / new flow | 新JVM真实Servlet+专属数据库，经UI完成SMA和通道两条三折→最终Holdout→全部成本重算链。ui-results-complete/channel-results日志各20Run/8压力场景；原详情链接、Holdout显式查看、刷新业务写入0。ui-replay-and-cancel证明真实POST已提交但响应丢失后两次POST URL完全相同、同验证身份；ui-cancel-complete记录CANCELLED且刷新写入0。没有mock成功结果。 |
| Playwright / old flow | nq-v102-walk-forward-existing-e2e-chrome.log：现有strategy-experiments.spec.ts三项全通过（48.8s），含两类四候选创建、执行、比较、取消、详情、390px只读恢复及原请求响应丢失恢复。 |
| Visual / i18n | 1440×900桌面、390×844窄屏及英文截图已实际查看；截图nq-v102-walk-forward-desktop.png、mobile-final.png、English.png。小屏表格有界横向滚动，状态由文本表达。 |
| Independent review | 独立subagent未参与实现且只读候选；核对22文件，24次正式内存执行、8组未来扰动对照，以及PG16只读16步骤/20Run/4次冻结选择核验，5类经济/时间错误和选择关联错误均0。报告nq-v102-walk-forward-independent-review.md，SHA256=36878e505b4e57b491b63ce834abacd90db8a665921975621edb08e8e8ff5958。未关闭actionable finding=0；无review-of-review链。 |

复现后端主用例（凭据仅指向可丢弃PG16）：

```text
mvn -f backend/pom.xml -pl nq-app -am test
 -Dtest=WalkForwardPolicyTest,WalkForwardPostgresIntegrationTest,StrategyExperimentPostgresIntegrationTest
 -Dsurefire.failIfNoSpecifiedTests=false -Dnq.tenant.pg.required=true
 -Dnq.tenant.pg.url=<disposable-loopback-url> -Dnq.tenant.pg.user=<installer> -Dnq.tenant.pg.password=<local-only>
```

浏览器夹具使用WalkForwardBrowserFixtureMain，输入TASK_EXPERIMENT_PG_URL/PASSWORD和fresh ready/stop文件；原E2E使用既有StrategyExperimentBrowserFixtureMain与playwright.experiments.config.ts。本机安装Chrome，回归设置E2E_BROWSER_CHANNEL=chrome，通过现有Playwright runner执行；新增流程使用Playwright CLI的具名会话和真实UI操作。夹具结束用stop文件正常关闭应用和专属数据库。

## 失败记录、范围与收尾

- 保留首次死锁失败与锁序修复；测试调度控制禁用时next_run_at需同步NULL、测试证据ObjectMapper需注册JavaTime模块，均为夹具修正。
- 报告投影SQL括号优先级错误和JDBC将JSON问号操作符识别为占位符的问题均已修复，分别由PG和HTTP列表/浏览器补测覆盖。旧失败不追认为PASS。
- 浏览器开发中的按钮名称/唯一locator与过早要求全部压力场景完成的失败保留；修正自动化脚本后重新取得实际完成结果。旧E2E首次缺少默认headless二进制、runner重复端口未进入业务；改用已安装Chrome与任务专属服务后3/3通过，没有放宽业务断言。
- Migration仅新增V49__experiment_validation.sql；历史V1–V48未修改。CI仅将本轮测试加入原PostgreSQL job，远程CI未运行。
- 最终暂存检查发现新增PG测试与720根JSON fixture的CRLF被严格whitespace检查拒绝，随后只将这两个任务文件转换为LF。逐字节验证after=before.replace(CRLF,LF)，JSON解析内容也完全相等；原审查manifest及报告保留原身份，换行前后SHA256与等价证明另存nq-v102-walk-forward-lf-equivalence.json。最终staged候选以交付回执及tree为准；无业务语义或数据内容变动，不将先前检查失败冒称PASS。
- Changes / Files：22个技术文件及STATUS、本报告，共24个文件。修改原实验服务/Worker/Controller、回测配置/Run/执行器，新增窗口/政策/验证服务、V49、目标测试/真实公开数据fixture、前端面板/API/query keys/翻译及既有CI测试入口；没有第二套实验/回测引擎或调度器。
- Remaining Findings：无未关闭定向finding。仅30天样本，不实现DSR/PBO，不证明长期稳健性或自动实盘资格。真实私有费用的新窗口压力不冒充可比较；非本轮支持来源保留UNAVAILABLE。
- Canonical SIM没有实现新FLAT窗口合同；独立检查确认现有SIM消费者对startBar>0的研究Run在注资前拒绝（SIM_SCOPE_OR_VERSION_INVALID），训练Run只读实际训练前缀。本轮没有发布、创建SIM、生产部署、真实provider或真实交易。
- STATUS仅同步PR #85的真实接受身份和本轮本地准备状态；历史Gate事实不变。精确暂存后停止，不进入V102-05。Final Staged Tree由最终git write-tree回执给出，本报告不伪造提交身份。
- 任务浏览器会话、Vite和Servlet夹具已关闭，夹具专属数据库正常销毁；CLI输出归入任务artifacts。任务专属PG16容器停止并保留，可恢复检查，没有触碰共享容器或执行全局清理。

- Commit: `NOT_COMMITTED`
- Push: `NOT_PUSHED`
- PR / Merge: `NOT_CREATED / NOT_MERGED`
- Release: `NOT_CREATED`
- LIVE: `DISABLED`
