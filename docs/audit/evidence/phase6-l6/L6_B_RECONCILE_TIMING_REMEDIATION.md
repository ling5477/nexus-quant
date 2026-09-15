# L6-B reconcile 计时合同整改

## 根因与证据边界

基线候选为 `a6a9f5a739f4c82ceb7822e5ca2d2255ca1122bb`，基线 exact-head CI 为 `34953802498 / 9 of 9 SUCCESS`。历史 run `df9932ac-f419-41f5-a0a3-9d7afdf641f8` 保持 FAIL / NOT_ACCEPTED，最后采样为 169m20.112s、1017/1080。三次重启恢复已通过；最后完整业务证明为 715 Orders / 715 Trades / 2860 Ledger，不把最后采样的746单升级为完整账务证明。

分类：**QUALIFICATION_TIMING_SEMANTIC_DEFECT**。旧 `L6BRuntime.command` 对所有命令仅等5秒，比实际 child 执行合同更短；主循环按下一个绝对5秒边界再次调用，也不能保证完成后等待5秒。production `OkxRestReconcileService` 的 `fixedDelayString=5000` 表示前次完成后再等5秒，不是执行 SLA。

失败对象为 actor0/generation2/PID46412，调用栈为 `drive → reconcile → command → Child.resultBefore → await`。最后采样 command count=1263、completed=1262、active=1；Hikari active=1、pending=0，tickFailed=0、backlog=0。日志最终1263条结果，末条 `RECONCILE 0`。

历史未保存逐命令dispatch、上一成功reconcile时间/latency、精确deadline时进程/线程/PG锁等待状态。末条结果与失败请求的身份以及相对于5秒期限的可见时间均 **UNKNOWN**；正常慢调用、执行迟到和读取迟到无法从历史证据严格区分，永久hang未被证明。回归fixture保留这些null/UNKNOWN，不伪造精确时间或把末条结果追认为成功。完整历史与原始ZIP保留在本地归档目录 `docs/audit/evidence/phase6-l6/runs/L6_B_180MIN_20260915_a6a9f5a7/`，入口为 `formal-01-result.md`；这些正式证据按任务要求未提交Git。

## 当前权威与整改

| 层次 | 当前权威 | 整改后的行为 |
| --- | --- | --- |
| 生产调度 | `OkxRestReconcileService` 默认 fixed-delay 5000ms | 生产零修改；qualification双actor串行轮次完成后再等5秒，恢复循环共用同一门 |
| 候选扫描事务 | `JdbcOrderRepository.reserveReconciliationCandidates` 默认transaction timeout 10s | 零修改；真实PG排他锁测试观察约10s `QueryTimeoutException` |
| child命令执行 | 原 `L6QualificationControls.handle` Future.get(45s) | 提取相同45s常量；失败锁存、超时请求中断，禁止继续该代 |
| controller应答 | 原 `B0Processes.Child` 默认75s | L6-B复用75s；大于45s并允许child失败清理与结果观察，超时仍失败 |
| controller/sampler只读SQL | 原runner/collector statement timeout 2s | 零修改；独立10s sampler不受串行命令等待占用 |

Future.cancel只是中断请求，不证明任意JDBC或业务线程已经终止。child在45s失败后不再接受业务命令；controller在75s仍未观察到可靠结果时永久闭锁协议，最终通过已有owned-process cleanup强制回收。正常命令异常和child死亡仍然阻断。

每次L6-B reconcile携带run内单调command ID，验证返回ID；controller保留成对DISPATCHED/SUCCESS或FAILED事件。记录actor、generation、PID、phase、dispatch/completion/latency、最近重启距离、真实选中候选数、child执行起止以及执行前后连接池状态。DB/backlog/executor上下文引用最近独立sample并保留sampleIndex和年龄，明确不是命令时刻快照；失败另留child failure时间、类型、连接池与executor状态。

`l6_b_latency.py` 验证成对完整性、ID、执行/应答上限、串行与轮次间5秒间隔，输出p50/p95/p99/max、超过5秒数量、十分钟趋势、drain分布及后续成功命令/业务oracle。正式分析器将其作为新增计时合同验证。慢调用本身仅是observation，仍由既有业务、资源、backlog、重启与预算合同决定资格。

短probe仅在显式probe参数中启用一次6000ms延迟，原子领取记录使全部代际合计只注入一次；正式入口拒绝该组合，不改变Venue延迟语义。生产、pacing、时长、重启计划、PG模型、hard budgets和runner authority JSON均未修改。

## 验证记录

- targeted-01：7项Java测试通过。旧5秒期限复现提前abort；迟到结果出现后，下一命令和读取均被拒绝；6秒调用在现有75秒应答合同内成功，下一命令身份一致；child死亡阻断。
- failure-regression-01：保留首次测试失败。真实扫描约10秒被既有事务上限取消，测试错误期待至少45秒；修正断言以区分事务上限与整条命令上限，不修改任何生产/执行超时。
- targeted-02：18项Java测试通过，包括真实PG扫描锁超时、真实reconcile读权限异常、独立进程45秒命令执行超时、非重叠/无补跑与既有admission/budget回归。
- Python：9项分析器测试通过，包括缺失事件、迟到结果误配、提前重入、真实超时和FAILED均拒绝。
- 最新fixture绑定原proof SHA256 `0033c5fba5cdd79d67b4a3d1dee9f49d2ec0d72fde5f105445161476b62f8325` 与原raw ZIP SHA256 `abd742e525a7959fd4ea9fe87787c8ed38599c2abe6c81f7a038093c8e55f0a1`。

本文件后续仅追加本轮probe、独立审查与交付的真实结果；正式180分钟证据单独保留，stage=0 / commit=NONE / push=NONE。L6-A ACCEPTED保留，整改交付本身不授予L6-B或L6整体接受。

## 定向审查闭合与最终短probe

一次真正独立审查在初始候选发现P0=0/P1=2：新增latency分析依赖未显式校验SHA，以及恢复成功路径可能跨过原总deadline。最小修复后，分析器逐文件绑定新增依赖；运行器在metrics完成和完整checkpoint完成后检查原截止时间，并保存实际被校验的完成时刻。分析器重新计算原恢复bound并核验完成时间严格早于deadline。原时间额度和restart schedule没有扩大。

补充Java边界测试接受deadline前1ns，拒绝恰好deadline和迟到45s；Python10项通过，覆盖依赖篡改、迟到成功和恢复bound篡改。初次XML收集目录混入旧结果，原目录保留；`targeted-02-selected-index.json`和`targeted-02-selected-xml`只绑定本次4类/18tests，失败/错误/skips均0。

首轮真实probe `0c0b4032-bf4e-4483-97b8-63535b1aa599`：300.0078347秒、30samples、100reconcile、23/23/92完整链；故意慢调用6125.6302ms。最终修复候选probe `7d658644-cc27-4ab0-bfc0-082cd2d22f97`：300.0086999秒、30samples、100reconcile、22 Orders /22 Trades /88 Ledger、position2.2、duplicates/orphans0；三次restart全部RECOVERED，原20秒恢复期限全部满足。二者candidateUnchanged=true、cleanup PASS、owned survivors0；Maven均真实exit0。最后一次命令包含新Java边界测试和probe，2tests/0failures/0errors/0skips。

最终probe latency分析PASS：p50=213.3012ms、p95=219.4697ms、p99=327.3334ms、max=6125.3226ms；超过5秒仅故意注入的1条。该调用后下一命令成功，业务继续新增完整链；drain21次reconcile、max=219.4697ms、超过5秒=0。全部轮次串行、完成后间隔及ID关联通过。短probe只证明整改能力，不替代180分钟正式资格。

入场4847个文件经SHA复核，授权候选之外漂移0。stage-assets扫描2021项/errors0；本报告链接检查errors0。正式重跑将绑定整改交付的新HEAD与新的9/9 CI，不复用基线CI接受新候选。

独立REVIEW_ONLY最终结论：**PASS / 两项P1 CLOSED / P0=0 / P1=0**。14个代码/fixture文件start=end指纹为 `030e98d8ce115a04ec5c8077e87db8b799c61c003341ef2fb4e8aa0b8978751c`，审查HEAD始终为上述基线、stage0、production delta0；审查者未修改任何候选、证据或编译输出。审查接受限本轮整改，交付CI和完整180min结论单独保存。

首个交付候选 `028f1b27f863186c67771fbe0d17a5187fab8bc5` 的CI `34978387345` 在治理检查发现本报告链接指向仅本地保留的正式证据。该失败保留；后续只将引用明确为本地归档路径，未提交正式证据、未调整链接检查规则、未修改已审查代码。必须以文档修正后的新exact-head CI接受最终交付。

正式T=0之前，发现新增latency parser的固定128MiB上限未由现有预算推导：100次probe产生约3.2MB计时证据，正式合法call cap投影可能超过该固定值。因此仅将分析读取边界改为原冻结 `rawHardCap` / `reconciliationCallsCap`，逐行读取且严格校验事件总数；原计时、ID、失败和恢复判定不变，Java及全部运行预算不变。Python最终11项PASS，原最终probe重放结果完全一致。同一独立审查仅补验此读取边界并PASS/P0=0/P1=0；两个文件start=end SHA分别为 `b4df602694dbd44a2f927f3885bdadfccab45ed9f23679a8e5fdb188522bfc21`、`9fea29f725054c00900d23db569df514c3a1dd3e5a0a85ac5011c36680d82165`，stage0。原Java/fixture审查及真实probe证据继续有效；正式运行必须等待最终postprocessing候选的新exact-head CI。
