# 测试、审查与交付合同

本文件唯一拥有跨任务的验证范围、审查触发、证据复用及交付选择；各领域参考只补充其特有 oracle。工程编码细则见[通用标准](../../../../docs/standards/java/common-java-engineering-standard.md)，文档事实规则见 [DOC_RULES](../../../../docs/DOC_RULES.md)。

## 测试范围

| 变更影响 | 最小有效验证 |
| --- | --- |
| 小型局部代码 | 目标测试；bug 断言原失败及相关边界 |
| 跨模块业务 | 受影响模块和调用链测试 |
| 交易正确性或关键并发 | 目标 integration/proof；持久化用隔离 PostgreSQL，重启用真实进程 |
| Flyway/schema | 隔离 PostgreSQL 升级、Flyway validate、约束与兼容正负例 |
| 前端 CSS/UI | 代表视口、焦点、可读性；行为或类型变化再加目标交互与已有 type/build |
| README typo | 修改文本、相关链接与 diff 检查 |
| release/deployment | 专项回归及 canonical exact-head CI |

Full Maven 仅在影响范围、失败证据或明确 hard gate 要求时执行。普通 service、docs 或前端 CSS 不默认全仓审计、完整 Maven 或 full qualification。

用业务状态、持久化关联与副作用证明行为，不仅断言 HTTP 200、日志、退出码或 mock 调用。失败路径不可删断言或放宽安全约束来变绿；测试命令从当前配置与入口推导。

## 审查范围

普通变更采用 implementation + self-review + relevant tests。交易/资金正确性、关键并发、migration/schema、credential/security boundary、release/deployment、LIVE、真实 provider 或真实资金执行路径的变更，需要一次真正独立的候选审查；跨模块架构改变按其影响纳入高风险。

审查者先核对候选与证据身份，自主复现关键风险。实现者切换 Skill 或角色不产生独立性；review-only 不修改候选。已接受能力不因邻接任务自动全量重审，不生成 review closure → acceptance review → acceptance closure 链。

## 证据与 Git

证据注明候选、fixture、环境、命令、真实结果和未覆盖范围。相同候选、环境与覆盖假设的有效证据可复用；新变更或风险使相应证据失效时再运行。失败和后续成功分别保留，不把局部 PASS 追认成先前全量 PASS。

commit、push、PR、merge 按用户各自授权执行；精确选择已检查文件，复核 staged diff。发布时区分 local PASS、独立接受、exact-head CI 和生产部署，不互相替代。文档只更新实际行为所需部分，不默认产生 WORKLOG、TESTING、计划链或空回滚栏目。

## Subagent

仅对真正独立且能并行推进的有界任务使用 subagent，例如独立代码路径检查、前后端调查、真正独立审查或无关失败定位。不得默认生成 architect/security/SQL/testing/reviewer 固定角色组；并行不能绕过只读、候选身份或授权边界。
