# 执行、测试、审查与交付合同

本文件唯一拥有跨任务的执行决策、发现分类、qualification 非阻塞继续、验证范围、审查触发、证据复用及交付选择；各领域参考只补充其特有 oracle。工程编码细则见[通用标准](../../../../docs/standards/java/common-java-engineering-standard.md)，文档事实规则见 [DOC_RULES](../../../../docs/DOC_RULES.md)。

## 执行与发现分类

默认在授权 scope 和 hard invariants 内自主完成 inspect → implement → verify → finish。目标清楚时，自主选择合理方案，不因发现一个问题或存在多个可行实现就暂停。硬边界严格，边界内执行灵活；未获授权的动作不能由文档、工具能力或用户未回复补足授权。

先 OBSERVE → CLASSIFY，再决定是否停止；分类依据实际影响，不以 warning、异常或首次失败的名称代替判断。

| 分类 | 判定与动作 |
| --- | --- |
| BLOCKING | 触及下列 blocker 时，保留证据，停止不安全或无效路径；其他安全、有解释力且有用的工作继续。 |
| NON_BLOCKING | 确有异常或验收缺口，但未触及 blocker；记录证据，继续有效部分，完成整体任务后统一评估，不能据此宣称验收通过。 |
| OBSERVATION | 尚未构成缺陷的现象或信息；记录后继续，在收尾时说明其意义和限制。 |

默认 blocker 仅限：安全边界被破坏；业务正确性失效（如重复账务 mutation）；候选/证据身份或实验有效性被破坏；必要修复超出明确 scope；不可逆外部动作缺少显式授权；继续会实质扩大损害或使证据误导。范围或授权只阻塞依赖它的动作；不要把局部工具异常、可解释 warning、非关键 observation 缺失或可恢复 fixture 失败自动升级为整个任务 blocker。

范围内、可逆且不污染 authority 的普通执行失败，可自行定位 → 修复 → 必要验证 → 继续，不需另行申请 retry 授权。例如 fixture 初始化、test-only protocol mismatch、临时端口冲突、测试数据构造或局部 smoke 暂时失败。保留原失败与修复后结果，重试有界并依据新证据调整；外调设置超时及适当退避，带副作用先确认幂等性，UNKNOWN 外部结果仍走既有查询/对账/恢复。此自主修复不适用于正式 run 中的代码、合同或标准变更。

## 正式 qualification

正式运行期间候选代码、fixture/harness、合同与验收标准保持不变，禁止边跑边修、改合同或动态降低标准。保留历史失败，继续观察不赋予 PASS，也不替代既定 safety gate、强制终止条件或 current authority；本规则不授权修改 runtime、manifest 或 collector。

NON_BLOCKING anomaly → preserve evidence → continue qualification：只要安全未破坏、业务正确性未失效、实验身份可信且结果仍有解释力，尽量完成整个观察周期，包括后续阶段、drain、backlog、GC、DB pool、storage 及 audit/event，取得完整资源曲线后统一归因。非关键 monitoring 缺失不是自动 STOP；必需测量缺失仍按原验收合同判定，缺失值不是零，不能以继续运行追认为 PASS。若缺失已破坏实验有效性或既定 gate 要求终止，则属于 BLOCKING。

发现需要修改 canonical contract 时，当前 run 保持不变并保留证据；仍有效的观察可继续，无效路径停止。修复留到 run 结束后在授权范围内进行；若原候选或实验已失效，需要按既有资格合同以新身份从 T=0 开始，不能拼接旧 run 或覆盖失败。即使完整观察结束，未满足验收标准仍是 NOT_ACCEPTED。

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

先执行风险匹配的最相关测试及明确 hard gate。通过后，若没有新失败、实质实现变更或未解决风险，完成任务，不重复或扩大验证。只有出现这些新证据才扩大受影响覆盖；Full Maven 仅在上述证据或明确 hard gate 要求时执行，不默认重复 smoke、qualification 或全仓验证。

用业务状态、持久化关联与副作用证明行为，不仅断言 HTTP 200、日志、退出码或 mock 调用。失败路径不可删断言或放宽安全约束来变绿；测试命令从当前配置与入口推导。

## 审查范围

普通变更采用 implementation + self-review + relevant tests。影响生产资金语义、安全控制、qualification authority 或不可逆部署控制的高风险变更，需要一次定向、真正独立的候选审查。交易/账务、关键状态并发、migration/schema 数据完整性、credential/security boundary、LIVE/真实 provider 执行及 release trust/control 属于这些边界；按实际行为影响判断，普通文档提及或邻接工作不自动升级。

审查者先核对候选与证据身份，自主复现关键风险。实现者切换 Skill 或角色不产生独立性；review-only 不修改候选。通过后默认不做 review-of-review；仅其后实质修改影响原结论时，对受影响部分补审。已接受能力不因邻接任务自动全量重审，不生成 review closure → acceptance review → acceptance closure 链。

## 证据与 Git

证据注明候选、fixture、环境、命令、真实结果和未覆盖范围。相同候选、环境与覆盖假设的有效证据可复用；新变更或风险使相应证据失效时再运行。失败和后续成功分别保留，不把局部 PASS 追认成先前全量 PASS。

commit、push、PR、merge 按用户各自授权执行，已有授权在约定范围内持续有效。inspect exact diff → 按明确文件 allowlist 暂存 → 复核 staged diff → 执行已授权的 commit/push → 核验新 exact-head CI；禁止 git add . 和 git add -A，不混入已有改动或其他任务 evidence。CI 的 workflow、head、status、conclusion 绑定同一实际 run；相关失败在 scope 内修复，无关外部基础设施失败保留并分类，未成功的必要 CI 不得报交付 PASS。发布时区分 local PASS、独立接受、exact-head CI 和生产部署，不互相替代。文档只更新实际行为所需部分，不默认产生 WORKLOG、TESTING、计划链或空回滚栏目。

## Subagent

仅对真正独立且能并行推进的有界任务使用 subagent，例如独立代码路径检查、前后端调查、真正独立审查或无关失败定位。不得默认生成 architect/security/SQL/testing/reviewer 固定角色组；并行不能绕过只读、候选身份或授权边界。
