# L6 Calibration Venue Capacity Contract Remediation

**BLOCKED / CALIBRATION_DESIGN_INCOMPATIBLE_WITH_ACCEPTED_VENUE_CAP — Decision B**。

本次未交付容量修复或fail-fast preflight。最终production/test/harness/config delta=0、stage=0、commit/push=NONE，没有正式15min、calibration smoke、60min或180min运行。L6仍NOT_ACCEPTED。下述阻塞来自本任务Decision B，不是Skill要求额外审批。

## 300的owner和含义

`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0SyntheticVenueMain.java:140-141` 是该订单容量条件和 `L5_ORDER_BUDGET` 的唯一Java实现位置：仅boundedWorkload且新client identity、map已有300订单时返回429。`L5VenueProcessMain.java:6` 是设置boundedWorkload=true的唯一已交付入口；L5各run和L6ActiveStabilityTest/L6CalibrationTest复用该launcher。B0非bounded默认分支未加此上限，B2使用自己的Venue入口。

300属于test-only实现，约束单run保留/接受的distinct logical orders；旧身份不消耗新槽，但请求仍按原协议计数。它不是物理map构造容量，也不是生产限制或PLACE/fill协议不变量。4 workers、executor queue16与订单容量独立。

不能因此归类为“仅fixture convenience”：`GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md` §4、75行明确列出 **Hard upper bound（单run）=300 distinct orders，重复请求另计**；79行reconciliation candidates上限也为300。L5 bounded acceptance引用该计划并保留SHA-256 `80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`，L5 aggregate同样保持该规划并据其处理mandatory scope。代码上限随L5交付commit23548b75加入。因此有证据将300视作L5安全预算的实现，而非仅由本次失败才发现的任意常量。

§6另行规定L6每run3000 distinct orders、fills/order≤4、raw≤1GiB。这个范围区别必须保留：**不宣称300是普遍L6或生产上限**。但用户本任务Decision A要求不是L5 safety invariant，Decision B又明确“如果300属于已冻结安全合同，DO NOT INCREASE IT”。在这一条件下，本轮不能自行将复用fixture的L5边界重新解释为可提高的纯storage配置；需要明确处置calibration容量authority与这项边界的关系。

## 已接受负载是否证明300必须最大

S1/S2=120、S3=252（240普通+12策略）；C1/C2/C3分别120/240/240，9个fault run各240，Kill证明121。这些run均小于300，证明的是各自有限覆盖，不证明系统/算法最大吞吐必为300，也没有通过动态容量极限测试建立300。这里的300是规划安全边界，而非性能极限。不能用“历史run没碰到300”取消该边界。

## 600及全生命周期预算

600来自已交付L6CalibrationTest的orderBudget、L6CalibrationEvidence的admission/backlog限制，以及l6_oracle.py的CALIBRATION分支。warmup和measurement使用同一Venue进程/map和同一evidence对象，预算不按phase重置；cleanup只收敛已有业务，不能回收已用槽后继续制造新身份。

不改输入时，两策略每5秒的名义轨迹为warmup120单、measurement240单，总计360，已超过300。该数只用于展示冲突，不当作严格maximumPossibleOrdersForRun上界；双scanner、phase边界、schedule catch-up/手动trigger及distinct replay上界还需正式统一合同。本次失败原始数据证实warmup120、之后180完整链后到300，随后的2单未收敛。

在≤300限制下，保留warmup输入后只剩180槽，无法覆盖现有完整measurement轨迹。降低频率或提前停产会改变冻结输入；现有accepted evidence未证明这样得到的healthy rate不受人为pacing限制。本轮不能自行设计一个偏低速率定义、降低load或以失败的58/60样本做rate/noise基线。因此选择用户给定的Decision B阻塞结果，而不是盲目300→600。

## 本轮草稿和撤回

初步检查只看到实现位置与历史run均小于300，曾作出过早的Decision A判断并形成未提交草稿：共享budget resource、calibration专用Venue入口、pre-resource preflight和scan前预留，以及protocol边界测试。完整追溯规划后发现§4的明确hard bound，纠正判断并撤回本轮所有代码改动。

草稿定向Maven通过19个Java测试（内含3个Python测试）；其中独立Venue protocol测试验证旧入口第301单拒绝、草稿calibration入口接受第301单、旧身份计数/逻辑身份保持以及queue16不变。这只是被撤回候选的测试，不是当前baseline修复证明，不据此交付或冻结任何参数。没有启动calibration-mode smoke。target中的编译产物可能仍属于撤回草稿，不能作为baseline复用；未来获授权运行前必须重新构建所选候选。草稿patch、源码副本、Maven日志及XML保留在ignored `backend/nq-app/target/l6-capacity-remediation-20260913/`，机器汇总附SHA-256。

撤回仅覆盖本轮自建/自改文件；对入口3990个文件逐字节SHA-256核对全部一致，既有未提交evidence和上一失败run原始文件全部未变。没有git reset/clean/stash、没有改写历史提交。当前旧预算冲突和fail-fast缺口仍OPEN，不把撤回称为remediation PASS。

## 失败保全和交付状态

上一正式attempt `3408f2f2-b017-4239-b005-a721a278bb54` 保持 **BLOCKED / CALIBRATION_INFRASTRUCTURE_FAILURE**：warmup完成，measurement58/60、300全链、2未收敛、120s超时。没有改成PARTIAL_PASS、RATE_SAMPLE或NOISE_BASELINE。

HEAD/upstream仍 `3f2671bf9e5292d0d0194c0b4f830ae8c2ba8525`，tree=`d5ef2879f7e9e927e5872ef97ff813294c5acd48`；34750388230仅是原baseline CI，不作新candidate证明。实现未获成功处置，故不进入本任务以实现成功为前提的commit/push和新CI步骤。

新增生产P0/P1未发现；既有calibration P1 blocker保持OPEN，历史P2/P3保留。本轮没有宣称P0_0/P1_0整体验收。唯一后续依赖是明确调和calibration容量授权与L5 fixture安全边界，或提供在≤300下仍保持测量有效性的已接受设计；在此之前不重跑正式标定，不启动60min/180min。
