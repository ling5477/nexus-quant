# L6-A hard-budget reconciliation

状态：候选已实现并通过本地定向验证；独立审查与exact-head CI作为独立交付证据，不由本文自行授予。L6-A / L6 均未接受；本任务没有启动正式60分钟运行。

## 基线与根因

- Branch：`audit/post-gatey-agent-baseline`；本轮起点 HEAD/upstream：`7e85cc7816687aa05a0deb75da7671a47d47537d`，CI `34858905184` 9/9 SUCCESS，entry stage=0。
- 最近失败 run：`0f2f3be9-5d96-421a-a000-eb3e1c19e808`。原始122文件、792,189,372 bytes以及失败报告保持原样。
- 原门实现检查 `pg_stat_database` 累计绝对数大于1,000,000，而planning表述为run delta。T=3530秒失败值没有序列化，精确超限值未知。最终无FINAL oracle，不能追认为完整60分钟。
- 主要增长是资格控制器每5秒对两个actor调用真实对账，以及checkpoint前再次调用对账。FILLED仍是生产合法恢复候选，每个终态fill重放产生Trade/Event检查、Ledger幂等、audit和JDBC session事务；这些调用由qualification发起，不能归为新业务订单。
- 旧配置仅靠count上限，在启动前没有证明完整DRAIN可容纳。本次不修改生产实现或弱化业务oracle。

## 事务分解与observer effect

可复算数据见 [reconciliation JSON](L6_HARD_BUDGET_RECONCILIATION.json)，脚本为 `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/l6_budget_reconciliation.py`。

统一可比端点使用最后完整PG sample（T=3520.006秒），不是T=3524.199秒的最后checkpoint：start absolute=349，last absolute=997,507，delta=997,158。

| 互斥组 | 重建事务数 | 依据 |
| --- | ---: | --- |
| Business emit | 3,367 | 259 × 13 |
| 人工对账：首次fill | 2,849 | 259 × 11 |
| 人工对账：终态重放 | 961,275 | 两actor实测137,325次 × 7 |
| 人工对账：游标 | 3,256 | T0后1,628次 × 2 |
| 生产recovery scheduler | 1,923 | 1,405 ticks + 每完成订单2个事务 |
| Validation | 24 | 两actor各12次；整个聚合处于实际事务内 |
| 10秒PG sampler | 5,984 | 相同collector内位置端点，352 × 17 |
| 10秒actor age sampler | 704 | 352 × 2 |
| scheduler observation | 2,816 | 保留scheduler行与端点对齐 × 2 |
| checkpoint/oracle | 202 | 110次已知attempt + 92次成功额外事务 |
| monitor/report（T0后） | 0 | 21事务正对照发生在T0前；不重复计入delta |
| 未分类及采集偏移 | 14,758 | 1.4800%；含controller、pool检查/轮换与跨source采集偏移 |
| 合计 | 997,158 | 与PG delta一致 |

分类部分98.5200%。旧raw没有逐事务标签，因此这是经真实单位成本校准的重建，不是旧run逐事务精确追踪。qualification归因977,086（97.9871%）；其中首次fill同时完成业务落账，不能把这些事务全称为可删除的开销。新订单emit加首次fill和scheduler completion合计6,734；Order/Trade/Ledger成本与“由谁触发”分两维记录，不重复求和。

独立PG16.15 / Spring / 两NQ JVM / Venue单位成本实验使用1、2、10个已完成订单。每组6次终态对账，严格断言 `6 × (7N + 2)`：每fill为Trade 3、Ledger 1、audit 2、JDBC隔离查询1；每scan为Order游标1、JDBC隔离查询1。生产timer、validation、Paper正对照及完整关系oracle真实执行。

新instrumentation实验PG delta=1,962；actor分类1,822、controller分类106、未分类34（1.733%）。未分类保留为非零，不填充为业务或安全零值。独立连接差分额外证明PG JDBC 42.7.9空连接2事务、100次autocommit SELECT=100、100次显式事务内SELECT=1、rollback=1、100次isolation SHOW=100、100次Hikari isValid空协议查询=100。

## 最小观测整改

- 保留正式5秒对账、10秒统一资源采样、7117650000ns pacing、600/2400/600秒、422-order envelope。
- 正式checkpoint前不再额外对账；已完成事实直接接受原有完整oracle，未完成仍等待既有5秒真实恢复，不伪造终态。
- 正式完整checkpoint间隔从完成后30秒改为60秒；每10分钟仍可取得多个完整oracle。最终全量oracle、每10秒资源事实、所有Venue请求事件均保留。storage calibration保持30秒合同。
- PG资源collector将17次独立只读查询合并到同一短事务，再加隔离级别SET；同一批必测字段仍逐项读取，finally结束事务，不跨10秒窗口占用snapshot。每sample PG事务由17降至2；含两actor age总开销由19降至4（约78.9%下降）。新增fills计数处在同一事务内。
- 全部事务标签和计数只在test目录；不保存SQL或参数，生产及L5使用原driver。

去掉checkpoint额外重放的完整运行确定性上界为240次scan、24,000次终态重放，即168,480事务；这不是声称旧run精确节省该值。旧run实际checkpoint数量和当时候选数量不同。新的必要5秒恢复仍有1,440次全局调用上界。

## 完整600/2400/600事务预算

`L6HardBudgets`在formal T0前构造一次，所有预算字段final，无运行时提升入口。预算从最大422订单、每scan最多100候选、两个actor和固定周期推导，不由旧上限乘比例得到。

| 预算项 | 全60分钟保守上界 |
| --- | ---: |
| qualification reconciliation | 1,010,880 = 1,440 × (2 + 100 × 7) |
| 首次业务全链（含完成恢复） | 21,100 = 422 × 50；实测基础成本26，留同链分支空间 |
| production scheduler tick | 1,440 |
| scheduler observation | 2,880 |
| validation | 96；实测每refresh 1，按4保守预留 |
| sampler | 1,440 |
| controller | 3,867；observer/pacer/checkpoint/phase各唤醒上界 × 3 |
| checkpoint | 183 = 61 × 3 |
| pool health及建连 | 144,080；20连接、默认500ms bypass window、两轮物理建连 |
| projected total | 1,185,966 |
| reserve | 25,342；完整双游标绕行7,020 + 一分钟双actor对账16,848 + 9条新链450 + bootstrap/未分类1,024 |
| frozen hard cap | 1,212,000（向上取整到千） |

此数是正常完整run的保守容量上界，不是声称下一run一定发生该事务数。尤其pool health采用所有20连接每500ms都需要检查的极端正常上界；实际前缀明显低于此量。必要对账DRAIN预算明确为 `240 × 702 = 168,480`，另含DRAIN的timer、sampling、pool和oracle成本。10秒采样使短窗口guard在约50–60秒检出持续异常放大；统计与collector偏移使用一整个10秒余量，所有阈值在T0前固定。

总cap防止持续累积，滚动rate门防止异常放大提前消耗额度；PG storage projection、连接/队列和timeout门继续独立fail-closed。FINAL与sampler各自保持时钟，终检核总量但不回写sampler时序，避免并发观测产生假的计数回退。

## 全部现行hard caps

| Cap | full-run需求及储备 | disposition / 执行 |
| --- | --- | --- |
| distinct orders | ceil(3000s / 7.11765s)=422；Venue=422，global L6=3000 | SUFFICIENT；capacity preflight + 每次emit reserve |
| fills/order | L6-A合成单fill=1；原安全线4 | SUFFICIENT；每10秒计数及完整oracle仍要求精确1 |
| fault/restart | L6-A=0；L6一般上限6 | SUFFICIENT；formal没有注入入口，PID generations持续校验；L6-B未运行 |
| DB transaction delta | 1,185,966 + 25,342 <= 1,212,000 | SUFFICIENT；T0 baseline、immutable总量及rolling rate |
| raw artifact | 825,348,096 + 38,666,240 = 864,014,336 < 1,073,741,824 bytes | SUFFICIENT；checkpoint60s，完整内容保留，原1GiB运行门不变 |
| disk free | run投影加reserve后仍留max(2GiB, entry free的20%) | SUFFICIENT须本机entry实测；同一floor用于runtime |
| recovery callbacks | 每actor720，加entry实际ticks及60 reserve <=1000 | SUFFICIENT；原实际callback硬门不变 |
| actor commands | 每actor保守1,926，加entry count <=4000 | SUFFICIENT；原command门不变 |
| resource samples | 360 <500 | SUFFICIENT；仍10秒，missing/stale/overrun均拒绝 |
| files | 不超过256 <10000 | SUFFICIENT；61快照、固定append文件、子进程args/log、准备目录；temporary文件及时清理 |
| queues / connections | command executor capacity1；Venue queue16；Hikari每actor poolMax10，PG app连接<=20 | SUFFICIENT；没有增加并发，现有测量及拒绝保持；poolMax漂移阻断成本模型 |
| relation collector | 固定V51，无schema mutation，关系数不随订单增长；查询上限512 | SUFFICIENT；PG collector仍对513行结果拒绝 |
| host memory | 既有动态PG容量合入全部组件预算；<= entry available60% | SUFFICIENT须既有两次真实entry gate；模型/60%完全未改 |
| PG storage | 原动态容量模型与各阶段投影、reserve、allocation burst | SUFFICIENT须原准备/正式entry确认及10秒projection guard；模型完全未改 |
| helper临时残留 | 原deferred32 files /16MiB，owned生命周期及timeouts | SUFFICIENT；4 helpers/sample，加最多61 oracle；无新增helper常驻进程 |

raw推导按全历史QUERY事件每条128 bytes（旧raw实测最大119）、每checkpoint每订单8192 bytes（账务/身份/新业务Venue事件/pacing）、全程32MiB其他输出；61 checkpoint时点对40 fill-query/s做累积和，再留完整末次快照及16MiB额外储备。全量重复的30秒快照在max-envelope下不能满足同样充分性证明，因此需要本轮60秒checkpoint整改；不删除原始事件、不靠压缩逃避raw预算。

## 验证与边界

具体命令、attempt失败与后续结果保留在本地 `docs/audit/evidence/phase6-l6/runs/L6_HARD_BUDGET_RECONCILIATION_20260915/`。原失败/动态容量/正式前缀不混入本轮Git allowlist。

- 已通过：真实单位成本与完整业务oracle；新旧PG sampler事务测量；逐JDBC边界；完整3600秒数学投影；纯模型放大拒绝；已有cadence、pacing、scoped capacity、PG容量及projection边界回归。
- 启动接线验证使用独立60秒capacity probe，parameters明确 `capacityProbe=true`、`formalTimerStarted=false`；没有用短探针冒充60分钟资格。
- attempt-02的Maven heap未显式约束被原host gate拒绝，随后使用 `MAVEN_OPTS=-Xmx512m`；gate及PG模型未修改。
- attempt-03发现FINAL核算与独立sampler时钟写入竞争，已修复并新增时序负例/正例；原失败保留。
- 最终本地回归：`targeted-04` BUILD SUCCESS，7 tests / 0 failures / 0 errors / 0 skipped。真实PG放大在50.012秒/30,012事务处拒绝，远低于总cap；短启动探针为 `DYNAMIC_CAPACITY_PROBE_PASS`，owned清理PASS。其余已通过的定向覆盖和XML哈希见JSON，不重复运行无实质变化的测试。
- 本文不接受L6-A或L6、不触发下一正式run。新exact-head CI与审查结论在交付时记录。
