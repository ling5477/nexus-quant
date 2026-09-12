# Agent Instruction Baseline Consolidation

## Baseline / neutral independence

任务：NEUTRAL_INSTRUCTION_AUDIT + INSTRUCTION_CONSOLIDATION + SKILL_ROUTING_SIMPLIFICATION + GOVERNANCE_REGRESSION + NO_BUSINESS_RUNTIME_CHANGE。

工作目录 E:/Project/nexus-quant-instruction-audit-neutral-v2；起始 branch=audit/agent-instruction-baseline；HEAD=b733b75b7d5b018dc2f72f508ba11d1450a3bddb。实际执行 status、branch、rev-parse、diff --check、cached inventory：clean、staged=0，分支/SHA 完全匹配。

审计依据是本轮用户附件合同及补充。仓库 AGENTS/CLAUDE/Skills/references/router/templates/policy 均为 REVIEW SUBJECT，读取不会使其成为本轮执行 authority。较早用户注入的项目指令按后续明确中立合同处理；不以被审计对象的自述证明中立性。记忆仅帮助定位历史路径，不继承旧 4-Skill 数量或 Gate 授权。本轮未执行被审计 Skill，未模拟独立 reviewer。修改、commit、push、现有 exact-head CI 授权来自附件；无业务执行授权。

用户后续明确要求审查 C:/Users/Lingyu/.codex/AGENTS.md，已全文审查，保持用户级只读。

## Inventory / dispositions

包含隐藏目录的文件发现确认：nested AGENTS=0，仅根 AGENTS 与 CLAUDE；无项目 .codex、.claude、根 references 或 AGENTS.override.md；E:/AGENTS.md、E:/Project/AGENTS.md 未发现。实际参考位于 Skill references 和 docs/standards/java。不存在另一份活跃任务 router；policy 是可选能力映射。

| Source | Scope | Always Loaded | Trigger | Responsibility | Duplicate/Conflict | Disposition |
| --- | --- | --- | --- | --- | --- | --- |
| `.agents/README.md` | 按需参考 | 否 | 命中主题 | 能力路由/owner 索引 | CSS 排除、缺发布入口 | UPDATE |
| `.agents/history/AGENTS.frontend-skill-routing.md` | 历史 | 否 | 明确追溯 | 非当前指令 | 无 | KEEP |
| `.agents/history/MERGE_MAP.md` | 历史 | 否 | 明确追溯 | 非当前指令 | 无 | KEEP |
| `.agents/history/engineering-lessons-baseline.md` | 历史 | 否 | 明确追溯 | 非当前指令 | 无 | ARCHIVE |
| `.agents/skills/nq-frontend-state-design/SKILL.md` | 能力 | 发现时 description 可见，正文否 | nq-frontend-state-design | 窄触发/按需参考 | 无 | UPDATE |
| `.agents/skills/nq-frontend-state-design/references/frontend-engineering.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | UPDATE |
| `.agents/skills/nq-frontend-state-design/references/page-states.md` | 按需参考 | 否 | 命中主题 | 兼容指针 | 重复状态规则或默认阶段流程 | CONSOLIDATE |
| `.agents/skills/nq-postgres-migration-review/SKILL.md` | 能力 | 发现时 description 可见，正文否 | nq-postgres-migration-review | 窄触发/按需参考 | 无 | UPDATE |
| `.agents/skills/nq-postgres-migration-review/references/database-proof.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | UPDATE |
| `.agents/skills/nq-release-deployment/SKILL.md` | 能力 | 发现时 description 可见，正文否 | nq-release-deployment | 窄触发/按需参考 | 无 | UPDATE |
| `.agents/skills/nq-release-deployment/references/delivery.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | UPDATE |
| `.agents/skills/nq-research-reproducibility/SKILL.md` | 能力 | 发现时 description 可见，正文否 | nq-research-reproducibility | 窄触发/按需参考 | 无 | KEEP |
| `.agents/skills/nq-research-reproducibility/references/engineering.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | KEEP |
| `.agents/skills/nq-trading-correctness-proof/SKILL.md` | 能力 | 发现时 description 可见，正文否 | nq-trading-correctness-proof | 窄触发/按需参考 | 无 | UPDATE |
| `.agents/skills/nq-trading-correctness-proof/references/engineering-boundaries.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | UPDATE |
| `.agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md` | 按需参考 | 否 | 命中主题 | 故障机制 | 混入历史 Task/Gate 状态、命令、固定根因闭环 | CONSOLIDATE |
| `.agents/skills/nq-trading-correctness-proof/references/proof-selection.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | KEEP |
| `.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | UPDATE |
| `.github/CODEOWNERS` | GitHub 元数据 | 否 | PR owner review | 路径 ownership | 本地 CRLF 不匹配原始字节 pin；Git blob 正确 | KEEP |
| `.github/pull_request_template.md` | 模板 | 否 | 明确使用模板 | 任务结果/验证 | 无关测试/启动/安装/authority 更新 | UPDATE |
| `.github/workflows/ci.yml` | CI | 否 | dev push/PR 或 dispatch | 现有治理与 exact-head 验证 | 无 | KEEP |
| `AGENTS.md` | 项目 | 是 | 所有任务 | 项目宪法 | 实现细则重复及 canonical authority 表述不足 | CONSOLIDATE |
| `CLAUDE.md` | Claude 适配 | Claude 是 | 项目任务 | 复用根规则 | 每轮 STATUS 预读 | UPDATE |
| `docs/DOC_RULES.md` | 按需参考 | 否 | 命中主题 | 领域规则 | 无 | KEEP |
| `docs/current/GOVERNANCE_WORKFLOW.md` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `docs/templates/ADR.md` | 模板 | 否 | 明确使用模板 | 任务结果/验证 | ADR 可选独立决策 | KEEP |
| `docs/templates/CHECKLIST.md` | 模板 | 否 | 明确使用模板 | 任务结果/验证 | 无关测试/启动/安装/authority 更新 | UPDATE |
| `docs/templates/GATE_PLAN.md` | 按需参考 | 否 | 命中主题 | 兼容指针 | 重复状态规则或默认阶段流程 | CONSOLIDATE |
| `docs/templates/WORK_ORDER.md` | 模板 | 否 | 明确使用模板 | 任务结果/验证 | 无关测试/启动/安装/authority 更新 | UPDATE |
| `scripts/docs/agent-workflow-fixtures.json` | 能力治理 | 否 | 显式回归/CI | inventory/风险/语义事实映射 | 仅 capability 标签样本 | UPDATE |
| `scripts/docs/agent-workflow-policy.json` | 能力治理 | 否 | 显式回归/CI | inventory/风险/语义事实映射 | 仅 capability 标签样本 | UPDATE |
| `scripts/docs/check-current-authority.ps1` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/check-doc-links.ps1` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/check-stage-assets.py` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/governance-workflow-contract.json` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/governance-workflow-lib.ps1` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/stage-asset-exceptions.json` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/test-agent-workflow-fixtures.ps1` | 能力治理 | 否 | 显式回归/CI | inventory/风险/语义事实映射 | 仅 capability 标签样本 | UPDATE |
| `scripts/docs/test-current-authority-next-action.ps1` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/test-gate-archive-manifest.ps1` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/test-gate-release-ci-runs.ps1` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |
| `scripts/docs/test-governance-workflow-lifecycle.ps1` | 专项治理 | 否 | 相关 authority/archive/release 检查 | 现有合同与正负例 | 阶段/发布语义属于专项合同，不进入默认预读 | KEEP |

补充完整读取 docs/standards/java 的 README、common-java-engineering-standard、java-platform-profile、spring-platform-profile、architecture-overlay、nq-java-domain-overlay 和 docs/DOC_RULES，均 KEEP：按主题工程标准，不是当前阶段 authority 或普通任务全仓预读要求。platform-profile、mapping、Shadow baseline 为已有 verifier 输入，未改动。其他 CI 业务 jobs 只核对调度边界；未审计其业务实现。stage/authority/archive/release 脚本按 instruction governance 关联部分检查，不将整个业务验收系统重构。

## 用户级 .codex/AGENTS.md 专项审查

| 条款组 | 结论 | Disposition |
| --- | --- | --- |
| 中文、指令优先级、项目文件不能扩权 | 全局长期有效；与本轮中立合同一致 | KEEP |
| 按复杂度计划、普通任务不固定流程、必要澄清 | 无强制 Skill、审查或填空；没有“不确定即全停” | KEEP |
| 行动持续到完成、阻碍如实说明 | 与本轮一次性收敛一致 | KEEP |
| 保护已有改动、最小完整变更、必要数据 | 全局工程边界，项目只补充 NQ 专有 invariant | KEEP |
| 生产/资金/外发/删除及 Git 分动作授权 | commit/push/PR/merge 不互相隐含；附件已授权本次 commit/push | KEEP |
| 按影响测试、失败/并发证明、不能削弱约束 | 无 Full Maven、repo-wide audit 或独立 review 默认要求 | KEEP |
| 外调超时、幂等重试、资源边界、事务并发 | 跨项目原则；具体细则保留领域 owner | KEEP |
| 公开契约、中文原因注释、真实证据与简洁收尾 | 无任务 ID 或历史阶段绑定 | KEEP |

用户级文件全文与项目重复主要在工作方式、测试及 Git；没有实质冲突。全局拥有跨项目方式，项目根只保留项目宪法，项目细化测试/审查由单一 reference 拥有。不需要修改用户级文件。

只读配置检查：config.toml 仅查看 model/instruction/project 相关声明，声明 gpt-6-astra；不把配置当作服务端实际模型证明。rules/default.rules 仅核对工具权限文件身份，不输出历史命令参数、不把匹配规则当业务授权。用户级/系统/plugin Skills 是宿主能力目录，未批量加载正文；备份不是当前指令。未审计未提供的 Claude 用户级设置，不推断隐藏宿主规则。

用户级 SHA256（本轮不变）：

- AGENTS.md：3895c43994ef3e6ac2cf86bfd9a2c7b4b58d89d4f12882d7b14a115f654504d7。
- config.toml：365e166e15886b5b5f147e0272edb0ba35859a4498537fb6494b77f7968d6664。
- rules/default.rules：c1f0c1b9adfd443878b7f370fa4a41125fb7f71086db891a16a719d7c1648faf。

## Effective stack before / after

共同前缀为宿主 system/developer/user、用户级指导和项目根；Claude 再读取 CLAUDE adapter。Skill description 在宿主发现时可见，正文仅按需；GPT-6 Astra 无需另一份项目流程。

| 任务 | Before | After |
| --- | --- | --- |
| 普通 Java | 根 + 工程索引/相关标准，零 Skill；Claude 仍每轮 STATUS | 根 + 命中标准，零 Skill、目标测试，无额外 STATUS |
| trading correctness | 交易 Skill/proof-selection，可进入混有具体 L4/L5/L6 状态与命令的 lessons | 交易 Skill + 对应证明；历史流程退出活跃参考，一次独立审查 |
| migration | migration-review + database-proof | 明确实现/审查触发；PostgreSQL + Flyway validate 均有回归 |
| frontend | 复杂状态设计才触发，Login CSS 排除 | 页面/组件/CSS 触发；局部 CSS 只视觉验证 |
| README typo | 零 Skill，但 checklist 带全栈测试/安装/启动/authority；Claude 额外 STATUS | 零 Skill，文本/相关链接/diff，模板无无关流程 |

## Findings / disposition

| ID | 级别 | 问题 | 处置 |
| --- | --- | --- | --- |
| F1 | P2 | CLAUDE 每轮 STATUS 与按需加载冲突 | UPDATE adapter |
| F2 | P2 | 根混合实现细则，Java Control Plane/第二事实源/Python LIVE 不够显式 | CONSOLIDATE 宪法；import 移到工程索引，前端细则留领域参考 |
| F3 | P2 | lessons 混有旧 OPEN/NOT_ACCEPTED、Task/Gate 命令、固定根因/closure 流程 | ARCHIVE 旧参考；活跃版本仅故障机制 |
| F4 | P2 | capability 标签测试不足，CSS 路由不符 | UPDATE description/policy，增加语义事实映射、正负例 |
| F5 | P2 | 模板默认无关测试/安装/启动/authority/回滚栏目 | CONSOLIDATE 八项合同、按影响自查、Gate 兼容指针 |
| F6 | P3 | page-states/database-proof/工程/交付规则重复 | CONSOLIDATE 单一主题 owner |
| F7 | P3 | 缺独立发布 discovery 入口 | 新增轻量 release/deployment Skill，保持原发布语义 |

未发现 P0/P1 instruction 缺陷；以上 P2/P3 已处置。Research 的数据/时间/seed/数值可复现性有独立职责，KEEP。最终 5 个 Skills，不以数量或文本压缩比例验收。

## Canonical ownership / contracts

- AGENTS：技术边界、Java canonical authority、唯一 Order/Trade/Ledger 事实源、Risk/Execution 路径、隔离/精度/幂等/账务、UNKNOWN 不盲重试、历史不可变、秘密、相关代码与最小改动、证据诚实。
- global AGENTS：跨项目工作方式与授权；项目不再复制完整通用操作流程。
- regression-delivery：唯一项目测试范围、审查触发、证据复用、Git 交付选择、subagent 边界 owner；领域文件只补充特有 oracle。
- engineering-boundaries：Java import 及 canonical standards 索引；frontend-engineering：API/types/state/视觉/浏览器；database-proof：schema/兼容/锁/回填/真实 PG/Flyway。
- proof-selection 与 engineering-lessons：交易证明选择与可复用机制；没有当前 Gate 状态或 qualification 命令。
- 发布 reference：定位原 RUNBOOK、治理合同、相关工具；branch/tag/ancestry、安全 profile、stage lifecycle 不复制、不改动。
- 测试：小改 targeted；跨模块 relevant modules；交易/并发 targeted integration/proof；迁移 PG+Flyway；发布专项+exact-head CI；Full Maven 仅影响、失败证据或明确 hard gate 要求。
- 审查：ordinary=实现+自查+相关测试；交易/资金、关键并发、migration/schema、安全/凭证、release/deployment、LIVE/真实 provider/真实资金路径，以及高风险跨模块架构，一次真正独立候选审查。已接受能力不因邻接任务全量重审，不生成 review/acceptance closure 链。
- Prompt：Goal、Baseline、Scope、Invariants、Allowed / Forbidden、Tests、Done、Git；仅顺序影响安全/正确性时固定步骤。
- Subagent：真正独立可并行的有界任务才使用，不默认 architect/security/SQL/testing/reviewer 固定组。本轮未派生 subagent。

## Routing regression

prompt→facts 由本次审计逐项核对；自动化从 changeKind/effects 推导能力、风险和测试范围，expected 不参与解析。不是字符串包含测试，也不声称已运行 Codex/Claude 自然语言 E2E。矛盾事实（docs 同时声称交易行为改变）和未知事实拒绝；删除语义规则、削弱风险或删 Flyway proof 有负例。

| Case | 请求语义 | Skills | 验证范围 | Review | 结果 |
| --- | --- | --- | --- | --- | --- |
| R1-java | 修复 Java DTO 空值导致的异常；不改变交易和事务。 | 零 Skill | TARGETED | 自查 | PASS |
| R2-reconciliation | 修复 reconciliation 多进程并发重复应用，保留恢复语义。 | nq-trading-correctness-proof | TARGETED_PROOF | 一次独立审查 | PASS |
| R3-flyway | 新增 Flyway migration，检查旧数据升级和约束。 | nq-postgres-migration-review | POSTGRESQL_FLYWAY | 一次独立审查 | PASS |
| R4-login-css | 调整 Login 页面 CSS 间距和焦点样式。 | nq-frontend-state-design | VISUAL | 自查 | PASS |
| R5-readme | 修正 README 拼写。 | 零 Skill | MINIMAL | 自查 | PASS |
| N1-strategy-copy | 修改 strategy 帮助文案拼写，没有执行逻辑变化。 | 零 Skill | MINIMAL | 自查 | PASS |
| N2-database-history | 修正文档中 database 历史说明，不修改 schema。 | 零 Skill | MINIMAL | 自查 | PASS |
| N3-deploy-history | 修正历史 deploy 说明，不执行部署。 | 零 Skill | MINIMAL | 自查 | PASS |
| N4-strategy-java | 修复 strategy 展示 DTO 的空值，不改变交易、配置或调度。 | 零 Skill | TARGETED | 自查 | PASS |
| R6-release | 验证发布候选制品和 exact-head CI，不执行生产写入。 | nq-release-deployment | RELEASE_REGRESSION_EXACT_HEAD | 一次独立审查 | PASS |
| R7-cross-module | 修改跨模块普通查询契约。 | 零 Skill | RELEVANT_MODULES | 自查 | PASS |
| R8-research | 修复 backtest seed 和时间输入可复现性。 | nq-research-reproducibility | TARGETED | 自查 | PASS |

所有样本不默认 repo-wide audit/Full Maven；另有关键词改写但事实不变的正例、3 个非法事实负例，以及 inventory 增至 6/合理收敛到 4 的正例。checker 不固定历史数量/文件名；retired ID 仅为拒绝集合，不是活跃路由。

## 验证与既有失败

实际执行：

- pwsh 7.6.5 和 Windows PowerShell 5.1.26100.9444：test-agent-workflow-fixtures.ps1，20 capability、19 mutation negatives、12 semantic、1 paraphrase、3 invalid facts、dynamic extension/consolidation，PASS。
- 两 host：test-governance-workflow-lifecycle.ps1，20 passed；test-current-authority-next-action.ps1，7 positive actions、4 ambiguous、9 safety、4 schema、5 whitespace negatives，failed=0；test-gate-release-ci-runs.ps1，同一 run 正例及 split-run/array negatives PASS。
- pwsh：test-gate-archive-manifest.ps1，6 passed；check-current-authority.ps1，errors=0。
- check-doc-links.ps1 全量：checked=912、warnings=123、errors=0。warnings 全在未修改的 TESTING/WORKLOG 历史链接。目标 AGENTS/CLAUDE/.agents/templates/PR 检查：checked=62、warnings=0、errors=0。
- git diff --check PASS。未运行 Full Maven、业务测试或 L6 harness。

保留本地既存差异：check-stage-assets.py 返回 1，UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT / STALE_SAFE_INPUT 均指未改动的 .github/CODEOWNERS，scanned=1919、reviewed_exceptions=173、errors=2。Git LF blob SHA256=d727fbd33cce8a9c4d68a9185841968333334e9acb34bac27e66da1b839cec42，与登记相等；本地 CRLF SHA256=bf5be319a98638a7ea2dce02ccc48b12f950c17049b409d90ed697643b651f36，14 个 CRLF，只转换换行后逐字节等于 Git blob。未改文件、登记或 checker，不把该本地检查写 PASS；由现有 Linux exact-head CI 核实仓库 blob。

主证据初次生成遇到 Python 默认 GBK 解码错误，尚未写入文件；改为显式 UTF-8 后生成。不涉及候选/业务验证失败，没有丢弃任何失败测试结果。

## Files / before-after / candidate identity

Files deleted=0：删除了重复规则正文，保留仍可能被历史文档引用的兼容路径。Files archived：.agents/history/engineering-lessons-baseline.md，保留旧正文并调整相对链接，明确 NON_AUTHORITATIVE。Files consolidated：根规则、工程边界、lessons、交付、数据库、page-states 和模板。新增发布 Skill 及 reference；其余更新完整见表。

行数仅作结构展示，SHA256 统一 LF；本证据不自引用 hash，最终 commit 绑定全部内容与本文件。

| Changed asset | Before lines | After lines | SHA256 (LF) |
| --- | --- | --- | --- |
| `.agents/README.md` | 9 | 19 | `ab05d0924f0105e21b8d057570492e06c8a113f315fbe2c7850509ec8a524791` |
| `.agents/history/engineering-lessons-baseline.md` | 0 | 210 | `e00d82121a696b0bdd971a057030ad7159d1036cdb0b613890356d076bb5af1d` |
| `.agents/skills/nq-frontend-state-design/SKILL.md` | 15 | 11 | `75b3684bf1b5794d8f267cbe0157bd3195d48defe17c7d1c00ce5e20b8d732b2` |
| `.agents/skills/nq-frontend-state-design/references/frontend-engineering.md` | 24 | 24 | `fbd7c27547463e7315fe29f255b53c79b776eea26bd41bdb469710c3f9b27d51` |
| `.agents/skills/nq-frontend-state-design/references/page-states.md` | 9 | 3 | `3dd4b40d642f35cbf5ba3148757d09de720560afb479c324a1f9e9ce8b185b9a` |
| `.agents/skills/nq-postgres-migration-review/SKILL.md` | 13 | 13 | `73319dc462fba77f1ba8d5bea7f190b20f14be93e656d346b4588cdbde0cad80` |
| `.agents/skills/nq-postgres-migration-review/references/database-proof.md` | 22 | 13 | `eae09ab8f2339d92a6b7ae4d99d24aded0d65c674fb8d4d57a76359ba43738c3` |
| `.agents/skills/nq-release-deployment/SKILL.md` | 0 | 11 | `68c58803c682e4d2cddd2436a04811d042671b618552f337274a653db96f3a8f` |
| `.agents/skills/nq-release-deployment/references/delivery.md` | 0 | 9 | `ddbee2291ebaa3d6ac9b4baba07858c901ccc7473b84903216f6ac927d18fb3e` |
| `.agents/skills/nq-trading-correctness-proof/SKILL.md` | 15 | 15 | `1d0b6638552e7a7b71f9c6f891ef16d117ebb8217d167e073c122d54f7e7c1f1` |
| `.agents/skills/nq-trading-correctness-proof/references/engineering-boundaries.md` | 37 | 17 | `22c9201a1114399fc38822ee5cf89fe9812c95abee5c5408f2d44fa7c22bdd5f` |
| `.agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md` | 206 | 15 | `a4314b0604ae69baef13e3d8d44d5fbe667063eabde4e9b6f486fa0692499f24` |
| `.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md` | 37 | 35 | `f1d1e685f5bdf5930b3b4ce3a568802f1529135c26981edec44586a96745fc09` |
| `.github/pull_request_template.md` | 21 | 7 | `45948e3785760b7a2516f081ffb082efb61925765ed2b09405fab3a86cc92fc0` |
| `AGENTS.md` | 25 | 14 | `085279d26865edfd87f22377c42bd4f0b547d590937944e603c1635dc3bd91fd` |
| `CLAUDE.md` | 8 | 5 | `562efb111492b9d6bf6a9ba72c1e5c44971ebf8080d1813a6a94622cd971ecf4` |
| `docs/templates/CHECKLIST.md` | 44 | 9 | `38830ece083f02e7405756469042f50b9df165c62c9a6cfddc81a8f27753f371` |
| `docs/templates/GATE_PLAN.md` | 37 | 3 | `dc2bf87aff37871aaed05be464c33c53bd0a651c3115d7dccab46bdb053d8b7b` |
| `docs/templates/WORK_ORDER.md` | 39 | 25 | `0b35c9affbbedd9205c84db304ae6d106610999ed7b25d9c771af938a7bea490` |
| `scripts/docs/agent-workflow-fixtures.json` | 408 | 690 | `1fae8b88fb05414a98fdb078a6ea1a77eb60a85619f17aa9f6b11c36125b2fe6` |
| `scripts/docs/agent-workflow-policy.json` | 143 | 269 | `42c0dd80fac2774d1c5bc30e28a569e1c829a3839b47d5ea8e04424f04bb65c9` |
| `scripts/docs/test-agent-workflow-fixtures.ps1` | 205 | 283 | `d98dbb6d613f8f496fc047aa64a88d5f0838847bed1e007e8c3afe1e2be1c23d` |

## Business/runtime delta / delivery / residuals

BUSINESS_RUNTIME_DELTA=0。差异严格限于 instruction、Skill/reference/history、模板、agent policy/fixtures/validator 和唯一主证据。backend、frontend product、research、Flyway、database、deployment/runtime、release semantics、STATUS、ROADMAP、Phase6/L6 evidence/harness 无改动。

本文件记录提交前实际验证；最终 commit、remote SHA、exact-head CI 由 Git/GitHub run 身份及任务最终答复提供，不预写未来 CI success，也不为嵌入自身 SHA 产生第二个治理提交。

下一交付步骤：精确 staged diff 复核 → 单一 commit → push audit/agent-instruction-baseline → dispatch 现有 NQ CI Baseline（原 push 仅 dev）→ 核对同一 head 的实际结论。

未处置 P0/P1/P2=0。限制：本地 Windows 原始换行 pin 差异、既有账本 warnings、未进行宿主自然语言 E2E；新的 Skill catalog 需宿主下次发现，文件更新不追溯替换当前会话已注入的 metadata。用户级文件保持只读。本地结论 INSTRUCTION_CONSOLIDATION_LOCAL_PASS；最终任务 PASS 以 remote exact-head CI GREEN 为条件。

全部完成后治理主线结束，不追加 review/freeze。唯一后续方向为用户指定：将单一提交安全合入 audit/post-gatey-agent-baseline，再回到既有 L6 freshness disposition → remaining mandatory measurements → readiness → 60min。本轮不执行该合入或 L6 操作。
