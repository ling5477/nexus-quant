---
name: nq-release-deployment
description: 用于 NQ 发布验收、部署变更或恢复验证；历史说明中提到 deploy 不触发。
---
# 发布与部署

以明确发布目标、候选和既有 canonical delivery contract 为起点；Skill 不授予 commit、push、部署或真实操作权限，也不改变 release semantics。

按需读取[发布参考](references/delivery.md)，核对候选、制品、环境和恢复能力。验证及独立审查统一采用[回归与交付](../nq-trading-correctness-proof/references/regression-delivery.md)。

授权、current authority 或候选身份缺失/冲突时停止相应发布动作，继续不依赖这些事实的检查；不得用旧 CI 代替当前 head。
