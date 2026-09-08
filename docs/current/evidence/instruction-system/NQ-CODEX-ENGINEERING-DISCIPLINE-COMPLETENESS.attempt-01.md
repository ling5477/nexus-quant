# Engineering discipline completeness · attempt-01

本轮是 instruction consolidation 的追加整改，纳入既有 pre-B0 候选的同一次 Independent Review；不创建新 Skill、任务路由或独立审查流程。实现者自查不等于 Independent Review。

起始 HEAD=`f3cc63b95d305fb227f6a91adf5f9ccb9350289e`；canonical repo=`E:/Project/nexus-quant-gateaudit`。起始已存在的 F1/F2、F7/F8/F9 未提交实现保留，原 20 文件候选证据仍代表追加整改之前的快照。完整新候选身份另见本文件末尾的增量记录。

状态：`IMPLEMENTED / PENDING_INDEPENDENT_REVIEW / F1_REMEDIATED / F2_REMEDIATED / ENGINEERING_DISCIPLINE_COMPLETENESS_REMEDIATED / CURRENT_AUTHORITY_ALIGNED`。

## 来源与归属原则

原始来源是 neutral audit 的 `instruction-audit/sources/repository/.agents/skills/`，共 12 个 SKILL.md 及 Python 的 2 个 references；逐个读取原工程规则、旧 A–H 描述和已有规则引用，再与本轮开始时的 4 Skills / 4 references、Java standards、DOC_RULES、工程配置和现有 checker/test 实现对照。原快照不修改；中立工作区 `discipline-work/before.json` 固定原 12 Skills/supporting files SHA-256 与整改前 canonical 文件身份。

以下 canonical owner 是工程规则来源，不是“每种任务必须调用一个 Skill”的执行拓扑。已有标准继续拥有规则，reference 提供主题索引和缺失细则，普通任务可直接读参考而零 Skill。现有 policy、triggers、risk requirements、fixtures/checker 不变。完整默认上下文不加载下表；`.agents/README.md` 提供按需入口。

缩写与可读取路径：

- **J**：[通用 Java standard](../../../standards/java/common-java-engineering-standard.md)、[Spring profile](../../../standards/java/spring-platform-profile.md)、[Java profile](../../../standards/java/java-platform-profile.md)、[platform profile](../../../standards/java/platform-profile.json)。
- **A**：[architecture overlay](../../../standards/java/architecture-overlay.md)，事实来源为 [ARCHITECTURE](../../ARCHITECTURE.md) / [MODULES](../../MODULES.md)。
- **D**：[NQ Java domain overlay](../../../standards/java/nq-java-domain-overlay.md)。J/A/D 原文件未改，不触发通用规范双项目字节同步或 Shadow baseline 更新。
- **E**：[工程/安全 reference](../../../../.agents/skills/nq-trading-correctness-proof/references/engineering-boundaries.md)。
- **T**：[证明选择](../../../../.agents/skills/nq-trading-correctness-proof/references/proof-selection.md)，**V**：[回归/交付/Git/docs reference](../../../../.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md)。
- **DB**：[数据库证明](../../../../.agents/skills/nq-postgres-migration-review/references/database-proof.md)。
- **FE**：[前端工程](../../../../.agents/skills/nq-frontend-state-design/references/frontend-engineering.md)，**UI**：[页面状态](../../../../.agents/skills/nq-frontend-state-design/references/page-states.md)。
- **PY**：[Research/Python engineering](../../../../.agents/skills/nq-research-reproducibility/references/engineering.md)。
- **DOC**：[DOC_RULES](../../../DOC_RULES.md) / [FACT_SOURCE_INDEX](../../FACT_SOURCE_INDEX.md)；当前阶段、安全与 next action 仅由 [STATUS](../../STATUS.md) 的机器区块决定。

## Discipline ownership matrix

Status 中“补齐”指规则 owner/按需读取已补齐，仍 PENDING_INDEPENDENT_REVIEW；不是业务能力或 runtime 测试新验收。Checker 栏精确区分静态合同、定向行为测试与人工语义判断。

| Discipline | Canonical Owner | Detailed Reference | Checker | Load Trigger | Status |
| --- | --- | --- | --- | --- | --- |
| Architecture / module ownership | NQ AGENTS invariant + A canonical standard | A 的 module/owner/adapter/composition 条目；E Architecture | ModuleBoundaryArchTest / PackageBoundaryArchTest 仅编码的边界；verifier 验证 scope/config，语义 ownership 需分析 | 跨模块依赖、port/adapter、Service 职责或 wiring 变化，直接读命中标准 | KEEP + MOVE_TO_REFERENCE；补齐 |
| Java / Spring | J canonical standards | J 命名/数值/异常/资源/依赖；E Java/Spring 索引与装配细节 | verify-java-engineering-standard.ps1 保留 F1 修复后的标准/平台合同；Shadow finding 非阻断 | 普通 Java 只读相关条目；平台升级读 profile/POM；规则变更才查 scanner/baseline | KEEP + CHECKER_ENFORCED（配置合同）+ MOVE_TO_REFERENCE；补齐 |
| PostgreSQL / Flyway | nq-postgres-migration-review + J §5 | DB 结构、COMMENT、追踪、驱动、锁、回填与恢复 | Flyway validate/目标 PostgreSQL tests；历史 V40 blob 合同仅保护特定历史文件，非所有迁移语义 | schema/migration/backfill 评审触发 Skill；普通 SQL/JDBC 可直接查 DB | KEEP + MOVE_TO_REFERENCE；补齐 |
| transactions / concurrency / idempotency | J §4–5 + Spring profile + D；交易证明由 nq-trading-correctness-proof 承接 | E 代理/隔离/锁/version/key/有界资源；T 并发、失败与恢复 | 目标 transaction/JDBC/TradingChainPostgresIntegrationTest；static verifier 不证明竞态正确性 | 状态写、关键事务/并发、async、副作用顺序变化 | KEEP + MOVE_TO_REFERENCE；补齐 |
| trading correctness / reconciliation / ledger | NQ AGENTS invariant + nq-trading-correctness-proof + D | T 因果/终态冲突/重复/收敛/扫描上限；E risk/side effect；D 精度/ledger/audit | TradingChainPostgresIntegrationTest、相关 reconciliation/TradeLedger tests 仅各自场景；实际可达性与遗漏分析保留 | 交易状态、账务、reconciliation、恢复或因果证明 | KEEP + MOVE_TO_REFERENCE；补齐 |
| risk / credential / LIVE security | NQ AGENTS invariant + J §7 + D security/risk | E Risk/credential/LIVE；PY 输入/外调安全 | Test-DeliveryArtifactSafety.ps1（F2 支持的内容/归档）；risk/credential/no-real-adapter tests；授权不是单一 checker PASS | 安全输入、权限、tenant/account、风控、provider/credential/LIVE 操作边界 | KEEP + CHECKER_ENFORCED（限定范围）+ MOVE_TO_REFERENCE；补齐 |
| testing / CI / delivery | J §6 + V reference + 当前 delivery/checker contract | V 回归分层/golden/failure/identity/exact-head；FE/PY 领域验证 | agent-workflow fixtures 校验 inventory/risk 标签；Java verifier、Test-DeliveryEvidence 与实际 CI 合同各管其范围 | 目标测试/fixture、CI/制品、发布验收变化；无默认 Full Maven | KEEP + MOVE_TO_REFERENCE + CHECKER_ENFORCED；补齐 |
| frontend engineering | NQ AGENTS state invariant + FE reference；复杂状态由 nq-frontend-state-design 拥有 | FE API/Axios/types/query/cache/routes/form/visual/QA；UI 状态表达 | frontend/package.json 的 tsc/Vite build；受影响 Playwright；纯视觉人工/浏览器检查 | 普通接线/bug 直接 FE；复杂状态/权限流程才触发原 Skill | KEEP + MOVE_TO_REFERENCE；补齐 |
| Git / evidence / docs | NQ AGENTS invariant + DOC canonical document + V reference | V 精确 diff/staging/候选 identity/证据复用；DOC owner/中文/事实 | diff --check、check-doc-links、check-current-authority；delivery/provenance 按实际合同 | 有事实/文档变化才更新；发布/验收才查 authority/交付合同 | KEEP + MOVE_TO_REFERENCE + CHECKER_ENFORCED（可机器验证部分）；补齐 |

## 原 12 Skills discipline gap mapping

分类针对规则而非整个 Skill：DELETE=退役入口或冗余 recipe；KEEP=当前规则已有 owner；MOVE_TO_REFERENCE=实质细则补入按需 reference；CHECKER_ENFORCED=已有机器合同继续执行。不能因标记 CHECKER_ENFORCED 就省略其无法证明的行为分析。以下列出每个旧 Skill 的规则组和前后归属，包含原 Python references。

| 原 Skill / 规则组 | 整改前归属或 gap | Disposition / 当前 canonical owner |
| --- | --- | --- |
| java-backend-maintenance：core/infra、Repository、Controller/Service、配置职责 | AGENTS/A 保留模块方向；普通维护细节入口弱 | KEEP A；MOVE_TO_REFERENCE E Architecture 的鉴权上下文、SQL/HTTP边界、Service安全收口、循环装配/启动/配置校验 |
| java-backend-maintenance：契约保护、最小 bug 修复、回归/失败证据 | Global/AGENTS/J 有原则；回归设计细节不足 | KEEP 根约束；MOVE_TO_REFERENCE V；DELETE 固定七步/默认 Maven/旧 primary-owner recipe |
| java-backend-regression-tests：unit/slice/DB/integration 分层 | J/Spring 保留，不易从 current Skill 发现 | KEEP J；MOVE_TO_REFERENCE V 的选层入口及 HTTP/serialization/DB/event/audit/outbox 断言 |
| java-backend-regression-tests：golden case、clock/seed/ID、顺序、JSON字段、失败/非法/重复 | T/PY 只有部分原则 | MOVE_TO_REFERENCE V 确定性、失败/副作用、不过度 mock/不削弱断言；DELETE 固定报告和全量命令 recipe |
| nq-java-engineering-standard：架构/platform/rule 变更、profile 与 scoped standards | A/J/D 仍完整；旧 Skill 删除导致 F1 依赖已整改 | KEEP A/J/D；MOVE_TO_REFERENCE E 精确主题入口；CHECKER_ENFORCED 现有 verifier 的配置/hash/platform/映射，绝不恢复 retired Skill 依赖 |
| nq-java-engineering-standard：核心事务/锁/并发/idempotency/executor/外调 | J/D 保留；T 很短 | KEEP J/D；MOVE_TO_REFERENCE E/T：代理有效性、当前状态/version、稳定key、timeout/有限重试、资源/背压、accepted-timeout恢复 |
| nq-java-engineering-standard：SIM/LIVE、risk-before-execution、ledger/audit/credential授权 | AGENTS/D/T 已保留关键不变量 | KEEP AGENTS/D；MOVE_TO_REFERENCE E/T 解释风险拒绝无副作用、因果关联与恢复；DELETE supporting-owner分类、固定检查报告/旧路由 |
| db-schema-migration-review：PK/UK/FK/CHECK、default/null/index/comment、追踪 | DB 保留泛化DDL判断，COMMENT/追踪未明示 | MOVE_TO_REFERENCE DB 关键状态 CHECK、表/关键字段 COMMENT、交易风控恢复审计的幂等/追踪字段；KEEP J 数据完整性 |
| db-schema-migration-review：JSONB/TIMESTAMPTZ、精度与兼容调用方 | DB 已要求驱动实证，旧固定写法不应恢复 | KEEP 驱动实证；MOVE_TO_REFERENCE DB 写法适用性、UTC/offset往返、精度；DELETE 将 cast/Timestamp 作为唯一绑定规范 |
| db-schema-migration-review：forward-only/锁/回填/恢复/生产边界 | AGENTS/current Skill/DB 已有核心约束；限流/可观测细节不足 | KEEP forward/history/隔离库；MOVE_TO_REFERENCE DB 兼容窗口/分批/限流/可观测/重跑；DELETE 历史阶段入口、固定严重度报告 recipe |
| frontend-antd-page-builder：技术栈/API/types/Axios/query/hooks/Zustand | AGENTS 保留技术栈/状态；API工程细则丢失 | MOVE_TO_REFERENCE FE 的统一HTTP、集中query key、显式types、URL边界、分页筛选排序契约；KEEP 实际版本配置为准 |
| frontend-antd-page-builder：状态/反馈、风险、复用、联调与验证 | UI/current Skill 保留状态，工程验证不全 | KEEP UI；MOVE_TO_REFERENCE FE 的受影响 build/type/interaction、缓存与权限/路由；DELETE 固定列表/Drawer/列序、全站E2E recipe |
| frontend-product-ui-design：对象/信息层级、SIM/LIVE、拒绝/超时、危险影响范围、追踪 | current Skill/UI 有原则 | KEEP current Skill/UI；MOVE_TO_REFERENCE FE 的业务追踪字段和错误/过期/未配置可观察性；DELETE 固定PageHero/七块模板/固定九项报告与绝对文案黑名单 |
| frontend-quality-regression：root cause、routes/auth/forms/types/query、长文/null、依赖 | UI 仅浏览器检查原则 | MOVE_TO_REFERENCE FE API/state/QA；KEEP 最小修复；DELETE 全站默认命令与强制跨Skill QA handoff |
| frontend-quality-regression：role/label、AntD名称、fixtures、错误不白屏 | UI 已保留 locator/fixture 原则 | KEEP UI；MOVE_TO_REFERENCE FE accessible-name、登录/筛选/详情/提交/确认/失败链路及不削弱断言 |
| ui-visual-system-polish：token/typography/spacing/color/density/motion/a11y/responsive/i18n | UI 有主要视觉判断；生产数据韧性细节不足 | KEEP UI；MOVE_TO_REFERENCE FE 长字段/空值/异常/i18n、语义颜色/键盘和风险；DELETE 固定审查顺序/组件清单/报告/四Skill交接 |
| python-ops-tooling：入口/参数/输入/副作用/dry-run/重跑/大文件 | PY 有批量和写保护概要 | MOVE_TO_REFERENCE PY helper 小节的main/exit/参数、输入边界/缺文件/权限/外部失败、限流与重跑保护；DELETE standalone/package 固定二选一路由 |
| python-project-development：package/API/domain-adapter、typing、dependencies/lock | PY 有复用/工具概要，原细则未归属 | MOVE_TO_REFERENCE PY 结构与typed model、公开契约/避免import side effect、官方工具lockfile/安装打包；KEEP 沿用现有工具/不强制框架 |
| python-project-development 原 engineering-guardrails：异常/with/async/cancel、mutable state/iterator、UTC/Decimal/dtype、path/SSRF/shell/deserialize | 关键Python安全/资源细节不全 | MOVE_TO_REFERENCE PY 对应错误/并发/数据/安全小节；DELETE 强制新项目布局、工具名单或固定分类流程 |
| python-project-development 原 testing-and-delivery：确定性、真实adapter/隔离、package/CLI、research-to-production | PY/current Skill 已保留数据/时间/seed/结果；packaging与失败处理细节不足 | KEEP research Skill；MOVE_TO_REFERENCE PY 受影响验证与产物/入口、fixture/不得削弱断言；DELETE 固定七步/报告格式/默认全套工具 |
| nq-docs-writer：verified facts/中文/术语、doc owner/current/history冲突 | DOC/AGENTS 已保留主体 | KEEP DOC；MOVE_TO_REFERENCE V 文档类型/事实层次/证据复用；DELETE 普通修改默认WORKLOG、固定文档流水线与全量输出 |
| nq-docs-writer：authority/link/archive/release保护 | 现有 checker/contract 继续拥有机器规则 | CHECKER_ENFORCED check-doc-links/check-current-authority 与适用archive/release合同；KEEP 历史不可变；DELETE 将历史lifecycle复制到Skill/通用任务入口 |
| nq-dh-workflow-router：repo/scope/用户授权/事实边界 | Global/NQ根已保留稳定约束 | KEEP 根约束；MOVE_TO_REFERENCE V 精确Git/候选/证据 identity；DELETE 强制前读STATUS/分类/唯一primary/supporting/仓库自述授权 |
| nq-dh-workflow-router：active inventory/risk/proof floor | 当前 policy/fixture 已独立于旧Skill实现 | CHECKER_ENFORCED 现有4-Skill inventory/风险不降级合同；DELETE retired router/旧A–H所有权结构，不增加第5个Skill |

## Critical completeness 与限制

关键规则沿明确 owner 保留：金额/精度与时间确定性（J/D/PY）、状态/稳定幂等身份与风险前置（D/E/T）、交易/账务/audit因果与部分失败恢复（T/V）、forward-only/兼容/约束/追踪/驱动/锁/回填（DB/J）、租户账户环境隔离与credential/LIVE fail-closed（AGENTS/E/D/PY）。本轮还用当前实现契约补明 stale/version、reconciliation终态冲突与单次limit总预算，不把这些新细化伪称为旧12 Skills逐字规则。

F1/F2 是既有实现与测试证据，本轮未改任何 checker、测试、Java standards/hash input、policy/fixture、业务代码、Flyway、frontend production、.github 或 global/root AGENTS。Java Shadow 的非阻断finding与阻断配置错误分开记录；文档归属完整不代表形式验证或全量业务回归通过。

现有 instruction consolidation 与 closeout 是历史接受快照，追加 evidence 不覆写原 benchmark/acceptance 结论；本轮未重新做模型行为 benchmark，无法据此宣称新的运行时 token/latency 改善。只报告静态体积与明确的按需入口。

## Validation and candidate

独立审查保持 PENDING，发布未授权，stage/commit/push=NONE。以下为本轮真实检查结果。


| 验证 | 本轮结果与覆盖 |
| --- | --- |
| test-agent-workflow-fixtures.ps1 | PS5.1 / PS7 各 18 positive、17 negative PASS；canonical=filesystem=4；legacy-active/duplicate/unknown=0；动态第5个Skill fixture PASS（实际canonical仍4） |
| verify-java-engineering-standard.ps1 | PS7 PASS；319 Huangshan rules、103 documented project rules；V40 blob PASS；configuration hash 与F1原证据相同 |
| check-current-authority.ps1 | PS5.1 / PS7 PASS；原 work batch 与 REVIEW action 保留，仅叙述追加审查范围 |
| test-current-authority-next-action.ps1 | PS7 PASS；7 positive actions、4 ambiguous、9 safety negative、4 schema negative、5 whitespace negative，failed=0 |
| check-doc-links.ps1 | 对本次 .agents、instruction evidence、pre-B0 evidence、STATUS/ROADMAP：72 checked / 0 warnings / 0 errors |
| check-stage-assets.py | scanned=1800 / reviewed_exceptions=173 / errors=0；未新增或更新例外 |
| git diff --check / scope verification | PASS；14个本轮增量文件，未触及allowlist外任何文件；原12 Skills快照hash不变，原evidence正文逐字节前缀保留 |
| F1/F2 证据复用 | scripts/ci 与 scripts/java-standard 共30个原有脚本/测试文件逐字节不变；Java standards/config/hash inputs与 .github 不变。复用 pre-b0-remediation 原F1 2 positive/10 negative、F2双host各7 positive/20 negative结果；本轮未重跑这些 suite |

本轮 helper 初次使用系统默认 GBK 读 UTF-8 STATUS 失败，已仅修正中立验证脚本的显式编码并重跑 PASS；未更改项目文件去规避检查。

Full Maven、PostgreSQL业务回归、Playwright、remote CI、生产与真实交易均 NOT_RUN；本任务只新增标准引用/文档，不据此声称任何新的业务runtime qualification。已有F1/F2证据在相同实现/配置/环境范围复用；Independent Review 自行决定需复现的关键场景。

静态上下文口径沿原 consolidation：Global AGENTS + NQ AGENTS + 4个SKILL.md，4114 → 4293 characters（仅两个Skill根新增reference链接，共179字符）；Global/NQ AGENTS原始字节均不变。四Skill frontmatter（name/description）逐字相同，policy/fixtures与所有checker不变，references由4→7份、合计11410字符，按需读取。索引README不是新的router；仅给主题路径，不复制详细标准。静态字符不是token测量，新的模型行为benchmark NOT_RUN。

## 最终候选身份与结论

本轮增量文件14个；合并原pre-B0实现后待审候选共31个文件（含本evidence）。原20文件fingerprint只适用于原快照，最终审查使用下述新fingerprint。算法保持 `sorted(path + NUL + lowercase SHA256(raw file bytes) + LF)` 的UTF-8 SHA-256，排除本evidence避免自引用；其余30个文件全部纳入，包括既有F1/F2及更新过的旧evidence。

Candidate fingerprint=`2093d6277e5256cb67fb0e4a00031f275281103e4fd9b57d2c7230aa49341e16`。HEAD=`f3cc63b95d305fb227f6a91adf5f9ccb9350289e`，staged=0。完整逐文件hash及本evidence独立hash保存在中立工作区 `discipline-work/candidate-manifest.json`；源码快照/范围/体积/原始命令输出位于同目录，不作为运行时instruction入口。

增量清单：

- `.agents/README.md`
- `.agents/skills/nq-frontend-state-design/SKILL.md`
- `.agents/skills/nq-frontend-state-design/references/frontend-engineering.md`
- `.agents/skills/nq-postgres-migration-review/references/database-proof.md`
- `.agents/skills/nq-research-reproducibility/references/engineering.md`
- `.agents/skills/nq-trading-correctness-proof/SKILL.md`
- `.agents/skills/nq-trading-correctness-proof/references/engineering-boundaries.md`
- `.agents/skills/nq-trading-correctness-proof/references/proof-selection.md`
- `.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md`
- `docs/audit/evidence/GATEAUDIT_PHASE6_PRE_B0_CI_SAFETY_CURRENT_AUTHORITY_REMEDIATION.md`
- `docs/current/ROADMAP.md`
- `docs/current/STATUS.md`
- `docs/current/evidence/instruction-system/NQ-CODEX-ENGINEERING-DISCIPLINE-COMPLETENESS.attempt-01.md`
- `docs/current/evidence/instruction-system/NQ-CODEX-SKILLS-AND-AGENTS-INSTRUCTION-CONSOLIDATION.attempt-01.md`

最终状态：`IMPLEMENTED / PENDING_INDEPENDENT_REVIEW / F1_REMEDIATED / F2_REMEDIATED / ENGINEERING_DISCIPLINE_COMPLETENESS_REMEDIATED / CURRENT_AUTHORITY_ALIGNED`。由未参与实现的reviewer在既有同一次审查中验证，不恢复旧Skill、历史workflow或普通任务独立review。


## 用户确认追加 NQ 根入口与关键底线（2026-09-08，后续修订）

本节更新前文“Global/NQ AGENTS 字节不变”和31文件候选的适用时点：它们是根文件补充之前的已验证快照。用户随后明确要求补充两段，现仅修改 NQ 根 AGENTS：明确金融精度/舍入、风险前置、状态迁移与并发保护、稳定幂等及账务一致性、结果不确定时按既有查询/对账/恢复契约收敛；并直接链接 `.agents/README.md`，说明普通实现同样适用相关标准，按命中主题读取且无需先调用 Skill。

Global AGENTS、四Skill identity/description/routing、references、F1/F2、checkers、标准、业务代码、Flyway、frontend、.github 与此次补充前逐字节相同。没有新Skill、默认Full Maven、普通任务独立review或历史workflow。根入口仅新增两段，共229字符；六文件静态口径4293 → 4522字符，完整详细标准仍按需读取。

本次验证：PS7 inventory为4，18 positive/17 negative及动态第5Skill fixture PASS；current-authority errors=0；AGENTS/索引链接6 checked/0 warnings/0 errors；stage-assets scanned=1800/reviewed_exceptions=173/errors=0；diff-check PASS。范围哈希验证除AGENTS和本evidence追加外无新变化，staged=0。未重跑不受影响的F1/F2或业务suite，独立审查仍PENDING。

前一候选manifest已保留为中立工作区 `discipline-work/candidate-manifest.before-root-addition.json`；旧fingerprint仅适用于前一候选。现在合并候选共32文件，排除本evidence自引用后的31文件fingerprint=`ce806dd4ae37c8a412eea40c09cc78ff68b2bd2f2e134bdf24547a46a9064a5e`，算法同前。最新逐文件hash与本evidence独立hash在 `discipline-work/candidate-manifest.json`；本次范围回执为 `discipline-work/root-addition-verification.json`。根文件未改变current machine authority，仍由既有同一次Independent Review验收。

状态保持：`IMPLEMENTED / PENDING_INDEPENDENT_REVIEW / F1_REMEDIATED / F2_REMEDIATED / ENGINEERING_DISCIPLINE_COMPLETENESS_REMEDIATED / CURRENT_AUTHORITY_ALIGNED`。stage/commit/push=NONE。
