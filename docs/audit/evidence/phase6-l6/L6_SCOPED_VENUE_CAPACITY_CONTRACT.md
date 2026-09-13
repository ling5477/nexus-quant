# L6 Scoped Venue Capacity Contract

**LOCAL_PASS / PENDING_NEW_EXACT_HEAD_CI**。本记录描述提交前验证，交付后的exact-head CI另记。SELF_REVIEWED / NO_INDEPENDENT_REVIEW，production/config/Flyway/.github/frontend/research/AGENTS/Skills delta=0。

## Authority与历史

起始HEAD/upstream=`3f2671bf9e5292d0d0194c0b4f830ae8c2ba8525`，branch=`audit/post-gatey-agent-baseline`；旧CI34750388230仅绑定原baseline。本轮用户明确处置：L5的300不是全局Venue authority，L6独立上限3000；冻结规划不修改。此前Decision B反映当时授权边界，保留原文。

300原owner是test-only B0SyntheticVenueMain的boundedWorkload map准入；L5VenueProcessMain启用。它不是queue容量。600来自现有calibration的run预算、evidence admission/backlog检查和oracle分支；本次将其统一到test resource budget.json供Java/Python读取，未改healthy-rate公式。

失败run `3408f2f2-b017-4239-b005-a721a278bb54` 的58/60采样、300全链、2未收敛、120秒超时仍为BLOCKED / CALIBRATION_INFRASTRUCTURE_FAILURE，未成为rate或noise baseline。前一CALIBRATION_DESIGN_INCOMPATIBLE_WITH_ACCEPTED_VENUE_CAP报告保持历史事实；本轮授权使其作用域歧义得到处置，不追认原attempt成功。

## 模式和推导

QualificationCapacity显式Mode为L5 / L6_CALIBRATION / L6_FORMAL；不按时长、预算大小或property是否存在猜测。L6 run summary输出qualificationMode，calibration原mode=CALIBRATION保留以兼容既有oracle。默认L5=300/300/300；正式L6保留当前run预算240，专用Venue容量240、阶段上限3000；calibration独立推导。

固定正式calibration包络：300秒warmup、600秒measurement、每5秒一轮；两个phase各自最多60/120轮，不把phase起始轮遗漏。每轮两个actor各scan两条schedule，每schedule每scan最多接纳一个distinct identity，故未施加producer总预算的保守上界=(60+120)×2×2=720。这个上界故意不假定两个并发scanner一定命中同一个dueAt；覆盖边界、catch-up和scanner内部manual-trigger业务调用。

独立producer guard在每轮scan发出前按最多4个新身份预留，全生命周期共享已创建订单总数；上轮checkpoint先确认所有StrategyRun成功，无未创建订单的待执行work遗留。正常recovery/replay仅复用已接纳身份；没有独立新增identity的手动命令，cleanup不scan。故maximumPossibleDistinctOrdersForRun=min(600,720)=600，Venue容量由该结果赋值，headroom=0；600≤3000。smoke使用同一正式包络与factory，只缩短执行时长，不用smoke数据推导正式容量或rate。

runOrderBudget、maximumPossibleDistinctOrdersForRun、venueLogicalOrderCapacity、globalStageSafetyCap四项独立校验：budget≤maximum≤capacity≤stage cap。L6 maximum>3000单独报L6_RUN_ORDER_BUDGET_EXCEEDS_FROZEN_SAFETY_CAP，其余不兼容/unknown mode报QUALIFICATION_BUDGET_CAPACITY_CONTRACT_INVALID。L6入口用start(callback)在创建输出/runtime和正式计时前validate；负例证明callback未执行，runtime/timer/order均未开始。

容量不授权producer增加订单。即便构造capacity600、budget100的合法合同，第101个身份仍被producer guard拒绝。L6正式入口也在runtime前检查自己的合同，scan前独立校验producer剩余额度；原10/40/10 timing和240预算保持。本轮不执行正式soak。

## Venue语义与回归

只将bounded logical-order map拒绝阈值参数化。L5/default值仍300；只有显式L6 launcher赋值自身派生容量。B0非bounded分支和B2入口不变。PLACE/client/external/fill identity、重复处理、ACK、request counters、facts export、故障/延迟路径、4 worker和queue16没有改动；专用L6 Venue仍使用256MiB JVM上限。

20个定向Java测试通过，内含3个Python反例测试。真实独立Venue测试证明默认第300单接受、第301单429 L5_ORDER_BUDGET，L6显式入口接受第301单；旧身份重放保留同一ordId且增加原request计数，不新增logical order。FILL、tradeId、fillQueries、queue16与executor拒绝计数验证通过。重复Trade/Event/Ledger拒绝继续由原共享业务oracle反例覆盖，不把Venue原有幂等重放改成新拒绝语义。

负例覆盖L5 budget301、L6 budget3001、600/599不兼容、unknown/null mode和容量不扩张producer权限；相等600边界通过，producer预留596+4通过、597+4拒绝。原10秒sampler/模式/时钟/零进度反例回归均通过。

## 唯一短时smoke

run=`d5950cb5-e216-4636-9347-6bb681a98caa`，explicit L6_CALIBRATION、preflight PASS、derived capacity600、global3000；PG16/V51、2 NQ+Venue+controller。一次10s warmup/40s measurement，11 checkpoints，20全链/20 Trade/20 TradeExecuted/80 Ledger，Position/Snapshot BTC2；duplicates/orphans=0。

5个统一10秒样本，mandatory missing=0、cadence violations=0，final backlog=0、oldestCandidateAge=NONE、owned survivors=0，cleanup PASS。healthyRateCandidate和最终L6 arrival rate仍null；不生成canonical calibration manifest。smoke输入所有文件SHA-256起止相同，raw/test日志与XML索引见同名JSON。

## 交付与限制

仅精确提交本轮13个test/tooling文件与本报告/机器汇总，既有未提交证据不纳入。stage-assets检查1945项、173 exceptions、errors=0。P0=0、P1=0仅限本轮修复范围；历史P2/P3保留。原始失败证据及入口其他文件哈希保持。

新提交的9/9 CI必须另验，不使用34750388230。成功后唯一下一任务为NQ-GATEAUDIT-PHASE6-L6-15MIN-NO-FAULT-CALIBRATION，从T=0执行300/600秒；不续跑旧58/60样本。该新run正式通过并冻结manifest后才可进入60min。当前FORMAL_CALIBRATION_NOT_ACCEPTED / L6_NOT_ACCEPTED。
