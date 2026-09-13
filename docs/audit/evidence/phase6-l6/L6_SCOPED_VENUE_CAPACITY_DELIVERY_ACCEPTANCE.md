# L6 Scoped Venue Capacity Delivery Acceptance

**PASS / L6_SCOPED_VENUE_CAPACITY_CONTRACT_ACCEPTED / L5_300_ORDER_SAFETY_BOUNDARY_PRESERVED / L6_3000_ORDER_SAFETY_AUTHORITY_ENFORCED / CALIBRATION_CAPACITY_DERIVED_FROM_RUN_CONTRACT / BUDGET_CAPACITY_PREFLIGHT_FAIL_FAST_ACCEPTED / PRODUCTION_DELTA_0 / EXACT_HEAD_CI_GREEN / P0_0 / P1_0 / FORMAL_CALIBRATION_NOT_ACCEPTED / L6_NOT_ACCEPTED / READY_TO_RERUN_L6_15MIN_NO_FAULT_CALIBRATION**

HEAD=`d94f8512108fd7f8e9cbdeee14cfc8b064f2d5d8`，tree=`0c010b7d80327f5e8ec994cd6a7073e6853fe3ea`，branch=`audit/post-gatey-agent-baseline`；本地、upstream及远端一致。精确15文件提交逐blob匹配暂存manifest，stage=0，production/config等受保护输入无变化，历史evidence原字节保持。

新exact-head CI [34753536151](https://github.com/ling5477/nexus-quant/actions/runs/34753536151)：9/9 SUCCESS，failed/cancelled/skipped=0。没有使用原34750388230证明新候选。20个定向Java测试（含3个Python测试）及一次真实calibration smoke通过；smoke run=d5950cb5-e216-4636-9347-6bb681a98caa，20全链、11 checkpoints、5个10s样本、missing/violations/survivors=0，final backlog=0、oldest age=NONE。

L5默认300保留，L6 ceiling3000独立执行；calibration从run合同推导capacity600，producer权限仍受独立600总预算和pacing限制，queue16不变。SELF_REVIEWED / NO_INDEPENDENT_REVIEW，本轮P0/P1=0，历史P2/P3与失败attempt保持原结论。

本轮正式15min/60min/180min均未运行；formal calibration未接受、manifest尚未冻结、L6未接受。唯一下一任务：`NQ-GATEAUDIT-PHASE6-L6-15MIN-NO-FAULT-CALIBRATION`，重新T=0、300秒warmup+600秒measurement，不复用失败样本。

本文件与JSON是CI完成后的本地验收记录，未另行提交。实现与precommit证据已包含在上述交付commit。
