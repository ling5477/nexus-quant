# L6 Minimal Calibration Harness Delivery Acceptance

PASS / L6_MINIMAL_CALIBRATION_HARNESS_READY / CALIBRATION_AND_FORMAL_SOAK_MODES_SEPARATED / HEALTHY_RATE_RAW_MEASUREMENT_READY / NOISE_BAND_RAW_MEASUREMENT_READY / MANDATORY_10S_SAMPLING_REUSED / PRODUCTION_DELTA_0 / EXACT_HEAD_CI_GREEN / P0_0 / P1_0 / FORMAL_CALIBRATION_NOT_RUN / L6_NOT_ACCEPTED / READY_TO_RUN_L6_15MIN_NO_FAULT_CALIBRATION

已交付 branch=`audit/post-gatey-agent-baseline`，HEAD=`3f2671bf9e5292d0d0194c0b4f830ae8c2ba8525`，tree=`d5ef2879f7e9e927e5872ef97ff813294c5acd48`；parent=`1208abd904c318b832af152c9c52f8a5f4b46df3`。远端分支、本地HEAD、upstream一致。18个提交文件逐一匹配staged-manifest，stage=0；16个test/tooling文件与2个closure文件，production/config/Flyway/.github/frontend/research/AGENTS/Skills delta=0。11份既有未提交evidence按原始SHA-256核对一致，未纳入提交。

新exact-head CI：[34750388230](https://github.com/ling5477/nexus-quant/actions/runs/34750388230)，绑定上述HEAD，9/9 SUCCESS；failed=0、cancelled=0、skipped=0。旧34739867074不作为本轮证明。当前audit分支不自动触发push CI，使用既有workflow_dispatch；首次HTTP 500且查询未发现run，有限重试成功，没有修改workflow。

本地定向16个Java测试通过（内含3个Python测试）；唯一真实短时calibration smoke通过：20个完整业务链、11个oracle checkpoint、5个10秒样本，mandatory missing=0、sampling violations=0、owned survivors=0。交付前只清理Python单元测试EOF多余空行，随后3个Python测试通过；测试时和最终hash区别已在closure记录。全部raw artifact/hash引用核验通过。SELF_REVIEWED / NO_INDEPENDENT_REVIEW；本轮P0=0、P1=0，历史P2/P3保持。

正式15min calibration、60min和180min均NOT_RUN。healthy sustained rate和25% arrival rate仍null，canonical noise bands与manifest尚未冻结，L6仍NOT_ACCEPTED。观测时间是共享持久化oracle检查后的保守时间上界；下一任务仍需审定候选公式及GC事件发生阶段，不能将当前短时观测当正式标定。

唯一下一任务：`NQ-GATEAUDIT-PHASE6-L6-15MIN-NO-FAULT-CALIBRATION`，届时在exact-head候选执行300s warmup + 600s measurement，并判断是否可以冻结manifest。本轮未启动该运行。

本文件及同名JSON是CI完成后的本地验收记录，未追加提交。已提交closure诚实保留其precommit LOCAL_PASS / PENDING_EXACT_HEAD_CI时点。
