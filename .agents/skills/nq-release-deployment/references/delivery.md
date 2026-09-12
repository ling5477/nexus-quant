# 发布参考入口

发布与部署任务才读取 [RUNBOOK](../../../../docs/current/RUNBOOK.md)、[治理合同入口](../../../../docs/current/GOVERNANCE_WORKFLOW.md)及 STATUS 的相关机器 authority。根据具体动作查现有 scripts/deployment 与 scripts/ci 对应实现和测试；不预读全部工具。

核对候选 commit、制品/fixture 身份、目标环境、部署前提、恢复条件及必要回归。exact-head CI 的 workflow、head、status、conclusion 必须来自同一实际 run；本地通过不代表已部署。执行步骤仅在顺序影响安全或恢复正确性时固定。

现有 release branch/tag/ancestry、安全 profile、evidence 和 stage-asset lifecycle 由原合同及其 validator 拥有，不在 Skill 中复制或另定义。

交易能力按既有 Paper → Shadow → Limited Live 的受控验证顺序推进；该顺序不是当前阶段声明，也不授予 LIVE 权限。
