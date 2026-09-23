# GateAUDIT Original Scope Traceability Reconciliation

结论：`BLOCKED / LOGGING_SENSITIVE_DATA_PROTECTION_IMPLEMENTATION_GAP`。本记录源于 2026-09-23 的有界静态追溯，并已在 clean remote-baseline worktree 复核 39 行及远端 identity；随后 [logging negative proof](GATEAUDIT_LOGGING_SENSITIVE_DATA_NEGATIVE_PROOF_AUDIT.md) 用 synthetic canary 确认 L07 为实现缺口。S10 仍是证据/签收缺口。既有 Phase7-C/D 接受事实与 GateAUDIT freeze authority 不变；`NQ-GATEAUDIT-PHASE7-E-CANDIDATE-DELIVERY-AND-TAG` 暂停，资格=`NOT_CONFIRMED`。未执行 Phase7-E、qualification、生产连接、真实交易或 tag。

## 1. Baseline and source boundary

- 2026-09-23 `git fetch origin --prune --tags` 后的起点：`origin/audit/post-gatey-agent-baseline`=`c00291003cd0bb03688e054b3c38d7a6cd1c320b`，tree=`c23eed148ba974c3ec3681d48c3c944c8a291d09`；其 `STATUS.md` machine block 为 Phase7-D `ACCEPTED|CI_GREEN`，接受身份=`4800ab1d9407eeb527182328263c0bae9e6c3087 / 35803472376`，GateAUDIT=`IN_PROGRESS|NOT_FROZEN`，原 next action=Phase7-E，LIVE=`DISABLED`，kill=`ENGAGED`。`origin/dev=4c19cb775ebb18b4288400a5a1a402145c2fe30a`。没有 `BASELINE_AUTHORITY_DRIFT`。
- 本地 checkout=`e0fa7f7ff4fa6d0201adf5147056ec0e8a25abdc`，落后同名远端 4 个提交；当前工作树另有用户已有的 `AGENTS.md`、agent policy 与 L6 evidence 改动。三个 `docs/gates/gate-audit/GATEAUDIT_*` archive 文件目前只存在于远端引用，已用 `git show` 读取。本记录没有将本地旧 `STATUS.md` 冒充当前 authority，也不触碰这些改动。
- 已读最新提交中的 `STATUS`、`ROADMAP`、`FACT_SOURCE_INDEX`、Audit Bootstrap Charter、Phase7 plan/A/B、GateAUDIT evidence/residual/runtime matrix；`TESTING`、`WORKLOG` 按相关历史条目检查，二者分别约 15k/20k 行，未把 append-only ledger 当 machine authority。静态代码、F007/F009、frontend catalog、Phase6/L6 与 Git history 另作交叉核对。
- 原始命名 `NQ-FULL-REPOSITORY-AUDIT-AND-CONSOLIDATION` 可从 GateY post-tag `ROADMAP` 和 Git `4c19cb7` 恢复；当时正式范围是全仓只读 inventory/audit，再分批收口死代码、重复实现、Gate 临时资产、架构、Spring composition、DB-model、tests/fixtures、scripts/deploy、docs/evidence/CI、frontend 结构债。仓库未找到两个精确任务名 `NQ-OBSERVABILITY-LOGGING-BASELINE`、`NQ-SQL-OWNERSHIP-AND-DUPLICATION-AUDIT` 的正式机器任务或接受 pair；本次任务书列出的细项作为待核销 scope，不倒推其当时全部为 P0/P1。L6 `ACTIVE_STABILITY.md` 明确把 log rotation 留给后续 logging baseline。

下面的 `Y(audit)` 表示当时必须审计/分类，不表示每个 P2/P3 都必须在 freeze 前重构。`U` 表示无法恢复当时单项 mandatory 身份。`BLOCK` 是**本次附加资格**的阻断，不改写既有 Phase7-C 的 P0/P1=0 结论。原始静态负证据仅限所列源树和配置；L07 的后续 synthetic runtime proof 单独见链接。Registry 字段映射：`ID=requirement_id`；`Original requirement / category` 包含原要求与类别；`Source` 是来源；`Mandatory then` 是 `mandatory_at_the_time`；`Final owner/disposition/evidence` 逐列给出；`Final evidence / current relevance` 同时记载现时相关性；`Gap / remaining action` 同时记载 `gap_status` 和补充说明。每行只有一个 final disposition。

## 2. Original Scope Traceability Matrix

| ID | Original requirement / category | Source | Mandatory then | Final owner | Final disposition | Final evidence / current relevance | Gap / remaining action | Release block |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| C01 | 全仓 inventory、AS-IS、finding/disposition | GateY `ROADMAP` at `4c19cb7`; Audit Charter | Y(audit) | GateAUDIT Phase1–3 | `COVERED_BY_ACCEPTED_WORK` | `STATUS` 与 Phase7-A matrix 将 Phase1/2/3 保留为 audit facts，不伪称 implementation CI | none | no |
| C02 | Order/Trade/Ledger 因果、重启与身份收口 | GateY residual; Phase3/4 ledger | Y | Phase4 F001–F004 | `COVERED_BY_ACCEPTED_WORK` | Phase7-A 21-row matrix 和 Phase4 immutable pairs | accepted scope 以外不外推 | no |
| C03 | CI、供应链、配置、deployment/restore | Phase4 closeout `ROADMAP` capability matrix | Y by Phase5 | Phase5A/B/F001/F008 | `COVERED_BY_ACCEPTED_WORK` | Phase7-A matrix；F005 platform attestation 单独 deferred | 只在 pair 失效时重开 | no |
| C04 | L4/L5/L6 bounded correctness/scale/stability | Phase7 plan/A | Y by Phase6 | Phase6 L4/L5/L6 | `COVERED_BY_ACCEPTED_WORK` | Phase6 final acceptance；L4 28/28、L5 20/20、L6-A 360/360、L6-B 1080/1080 | 不外推至通用 LIVE/无限 soak | no |
| C05 | frontend localization、错误目录与稳定 identity | Phase7 plan/A | Y by frontend batch | frontend/error owners | `COVERED_BY_ACCEPTED_WORK` | `docs/error-catalog/VERIFICATION.md`；旧 `STATE_CONFLICT` 保留、typed identity additive | optional warning 独立处理 | no |
| C06 | Phase7-A/B/C/D baseline、projection、readiness、pretag archive | Phase7 plan/A/B; current `STATUS` | Y by Phase7 | Phase7 owners | `COVERED_BY_ACCEPTED_WORK` | A/B/C/D 各自 accepted pair；B mandatory 1→0；D pretag 13 roles | E 尚未执行，不计完成 | no |
| C07 | 历史 Gate active runtime assets 的 DELETE/CONSOLIDATE/KEEP | 初始 `ROADMAP`; F009 inventory | Y(audit) | F009 | `COVERED_BY_ACCEPTED_WORK` | F009 1158 候选逐文件分类、65 删除、24 迁移、18 兼容合同保留；accepted pair `dbb8b9c6 / 34024427455` | 兼容合同按原 trigger 保留 | no |
| C08 | 重复 checker/Agent/Skill 治理层 | 初始 `ROADMAP`; Phase0/agent consolidation | Y(audit) | governance owner | `COVERED_BY_ACCEPTED_WORK` | `.agents/README.md`、machine policy、Phase0 evidence、F009 checker graph | 不宣称所有历史 checker 文本删除 | no |
| C09 | deployment/release/restore helper 合并 | 初始 `ROADMAP`; Phase5B/F009 | Y(audit) | canonical deployment owner | `COVERED_BY_ACCEPTED_WORK` | Phase5B canonical path；F009 旧 stage-specific callers 迁移或退役 | future trigger 才重评兼容路径 | no |
| C10 | current authority 与历史文档去重、UPDATE/ARCHIVE | 初始 `ROADMAP`; `FACT_SOURCE_INDEX` | Y(audit) | current docs owner | `COVERED_BY_ACCEPTED_WORK` | `STATUS` 唯一 machine owner；F013/Phase4 closeout、F009、Phase7 archive | 历史 ledgers 原位 append-only | no |
| C11 | deprecated/unreachable、dead Bean/DTO/repository/config、重复 helper/fixture 的对象级清理 | 初始 `ROADMAP` broad scope | Y(audit), N(all delete) | 各模块 owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | F009 仅覆盖历史 stage active assets；未找到这些一般对象的全量 delete proof | 触发：下一次相关模块修改时按 caller、Spring reachability、测试与兼容性逐对象判断 KEEP/UPDATE/DELETE | no |
| C12 | oversized class/component、重复前端抽象与 architectural subtraction | 初始 `ROADMAP` broad scope | Y(audit), N(all refactor) | backend/frontend owners | `DEFERRED_WITH_OWNER_AND_TRIGGER` | frontend Error UX 已收口一部分；未发现全量大类/组件专项接受证据 | 触发：对应模块改动或性能/维护性 finding 升级；先证明消费者与回归 | no |
| C13 | Phase3 `F-005` 原始 finding identity | Phase4 `TESTING`/`WORKLOG`; Git history | U; ledger P2/deferred | historical evidence owner | `SOURCE_IDENTITY_UNRECOVERABLE` | 仅可恢复 `P2 / DEFERRED`；原 title、source、owner、consumer 不可核验；不得与 GateF/P5 同号合并 | 找到 canonical source 时再审；不猜测语义 | no |
| C14 | Phase3 `F-011` 原始 finding identity | Phase4 `TESTING`/`WORKLOG`; Git history | U; ledger P2/deferred | historical evidence owner | `SOURCE_IDENTITY_UNRECOVERABLE` | 同 C13；Phase4 entry 曾要求恢复，后续全 refs/reflog/unreachable blob 搜索失败而 retired | 找到 canonical source 时再审 | no |
| C15 | GateY `Order.externalOrderId=NULL` 等残余 | GateY residual; Phase7-A/B matrix | N(current freeze) | trading identity owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | 17-row residual matrix 原位保留 | 新 canonical model 工作与独立授权时处理，禁止改生产事实清零 | no |
| L01 | structured JSON logging | 本次 task scope；L6 后续 logging baseline | U | observability owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | `application.yml` 仅文本 console pattern，无 JSON encoder；F007 不是此能力 | 触发：明确 logging-platform batch/部署输出合同 | no |
| L02 | console logging | `application.yml` | U | nq-app config | `COMPLETED` | 默认 console pattern 含时间、level、thread、trace_id、logger、message | 保持已实现文本格式 | no |
| L03 | rolling file / journald 边界 | 本次 task scope | U | deployment + observability | `DEFERRED_WITH_OWNER_AND_TRIGGER` | 源树未见 logback rolling config 或 journald retention 合同 | 触发：下一次部署日志保存方案明确时选唯一 owner | no |
| L04 | rotation | L6 `ACTIVE_STABILITY.md` | N(L6); U(original) | observability/deployment | `DEFERRED_WITH_OWNER_AND_TRIGGER` | `LOG_ROTATION_NOT_YET_QUALIFIED` 明示 | 触发：logging baseline；需证明实际输出后端与轮转边界 | no |
| L05 | retention | 本次 task scope | U | deployment owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | 无 app 级保留期配置/部署侧接受证据 | 触发：日志持久化部署合同 | no |
| L06 | totalSizeCap / bounded storage | 本次 task scope | U | deployment owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | L6 量测日志增长，未证明长期容量上限 | 触发：日志持久化部署合同 | no |
| L07 | Authorization/API key/JWT/password 全局敏感信息保护 | 本次 task scope；Charter secrets 边界 | Y(safety) | security + observability | `IMPLEMENTATION_GAP` | [prod console negative proof](GATEAUDIT_LOGGING_SENSITIVE_DATA_NEGATIVE_PROOF_AUDIT.md)：13/13 synthetic canary 在最终渲染输出中可见，包括 Throwable cause；普通 SLF4J logger 可绕过局部 mask | 下一独立 implementation task 修复共享输出边界并复验；不宣称已发生真实生产凭证泄露 | **BLOCK** |
| L08 | HTTP traceId / MDC | `TraceIdFilter`; `TraceIdContext` | Y(HTTP) | nq-observability/common | `COMPLETED` | Filter 生成/透传 `X-Trace-Id`、写 MDC、finally 清理；既有测试 | 未证明任意线程自动传播 | no |
| L09 | async MDC propagation | 本次 task scope | U | async owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | 未见统一 TaskDecorator/context copy；仅若干入口手工 MDC | 触发：新增 async executor 或跨线程 trace contract | no |
| L10 | scheduler/websocket/reconciliation trace | F007; static source | U | scheduler/adapters | `DEFERRED_WITH_OWNER_AND_TRIGGER` | validation scheduler、WS bridge/degrade coordinator 有手工 MDC；全入口贯通证明不足 | 触发：对应入口变更或跨线程 trace 作为验收条件 | no |
| L11 | ErrorCode/ErrorKey 与稳定错误身份 | `ErrorCode`; Error Catalog | Y(selected error UX) | common/api/frontend | `COVERED_BY_ACCEPTED_WORK` | common ErrorCode、API `ApiErrorIdentity`、frontend catalog；仅已登记错误具有稳定 typed key/id | 不能称所有错误已编号 | no |
| L12 | EventType convention | 本次 task scope | U | domain event owners | `DEFERRED_WITH_OWNER_AND_TRIGGER` | 有 domain-specific `ValidationReviewEventType`/`ShadowRunEventType`，无全局 logging EventType 合同 | 触发：跨模块事件 taxonomy 真正需要统一时 | no |
| L13 | log capacity/growth operational metrics | 本次 task scope；L6 sampler | U | observability/deployment | `DEFERRED_WITH_OWNER_AND_TRIGGER` | F007 指标为固定业务 operation；L6 sampler 的 `logBytes/logDeltaBytes` 属 test-only evidence | 触发：运行期容量告警或 logging deployment baseline | no |
| L14 | L6 log-growth evidence | L6 accepted evidence | Y(L6 bounded) | L6 qualification owner | `COVERED_BY_ACCEPTED_WORK` | accepted 60min 六窗口 log bytes 155772→872279；10s 采样；不证明轮转/保留 | 保留 bounded 解释 | no |
| S01 | JDBC/NamedParameterJdbcTemplate 使用 inventory | 初始 DB-model scope；本次 SQL task | Y(audit) | persistence owner | `COMPLETED` | 当前静态扫描 98 个生产 Java 文件引用 JDBC/RowMapper，其中 92 在 `nq-infra`、6 在 `nq-app` composition/limited pilot | 见 §4 例外 | no |
| S02 | inline SQL/runtime query ownership | 初始 DB-model scope；本次 SQL task | Y(audit) | persistence owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | `nq-infra` 约 90 个生产文件含 SQL literal；`MinimalLivePilotConfiguration` 有少量只读 recovery query | 触发：这些查询进入新的 canonical path/语义变更；移至明确 port/repository 并回归 | no |
| S03 | duplicate SQL patterns | 本次 SQL task | U | persistence owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | orders/trades/ledger 表被多个 read model 查询；未完成语义等价/重复清单 | 触发：同一 invariant 出现不一致或相关 query 变更 | no |
| S04 | duplicate RowMapper / canonical entity mapping | 本次 SQL task | U | persistence owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | 49 个生产 Java 文件引用 RowMapper；`JdbcOrderRepository` 映射 OrderRecord，`JdbcTradingQueryFacade` 映射 OrderQueryView，不能只凭同表判为重复 owner | 触发：字段漂移或单一 canonical entity 双写映射被证实 | no |
| S05 | cross-module persistence owner | 初始 architecture/DB scope | Y(audit) | nq-infra + app composition | `DEFERRED_WITH_OWNER_AND_TRIGGER` | JDBC 主体在 infra；app 例外主要 Bean 装配，limited pilot runner 使用只读 SQL | 触发：新 correctness mutation 绕过 repository 或 pilot recovery 成为新 canonical path | no |
| S06 | Order/Trade/Ledger correctness SQL | Phase4 F001–F004; Phase6 L4/L5 | Y | canonical repositories | `COVERED_BY_ACCEPTED_WORK` | `JdbcOrderRepository` OCC/scan lock、`JdbcTradeRepository` fill claim、`JdbcLedgerPostingRepository` account/position lock 与 idempotency；相关 accepted pairs | 不推广为全部 SQL audit | no |
| S07 | reconciliation/recovery query correctness | Phase6 L4/L5; Phase7-B | Y(selected) | scheduler/livecontrol/ledger repository owners | `COVERED_BY_ACCEPTED_WORK` | durable Trade→Ledger、Position/Snapshot 比较与 restart proofs 已接受；Phase7-B historical oracle 独立 | 新 query 仍需 owner 审查 | no |
| S08 | manual/fixture SQL scope | pre-B0 F6 residual | N(current freeze) | fixture/database tooling owner | `DEFERRED_WITH_OWNER_AND_TRIGGER` | residual 明确 `OPEN / P2 / NON_BLOCKING`；SQL 未执行 | future seed 前收窄，禁止生产或含非-fixture admin 的库 | no |
| S09 | Flyway functions/constraints/indexes inventory | current DB schema; migrations V1–V51 | Y(audit) | migration owner | `COMPLETED` | 51 个 forward-only migration；schema constraints/indexes 在 migration 中，未改写历史 | migration 变更时按 PostgreSQL review/proof 合同 | no |
| S10 | 完整 SQL ownership/duplication 专项签收 | 初始 DB-model broad scope；本次 SQL task | Y(this audit); original exact title U | persistence architecture owner | `EVIDENCE_GAP` | 未找到该精确任务的完整 inventory、跨表不变量多 owner 判定、重复映射逐对象处置及接受证据；L4/L5/L6 只覆盖选定 correctness SQL | 限定 SQL owner 审查并出具对象级 disposition；若发现真正 bypass，再单独最小实现整改 | **BLOCK** |

互斥计数：`TOTAL_REQUIREMENTS=39 / COMPLETED=4 / COVERED=14 / DEFERRED=17 / HISTORICAL=0 / NOT_APPLICABLE=0 / UNRECOVERABLE=2 / REAL_GAPS=2 / BLOCKING_GAPS=2 / UNCLASSIFIED=0`。L07 已由 synthetic 最终输出证明为 `IMPLEMENTATION_GAP`，S10 仍为 `EVIDENCE_GAP`；未证实真实生产凭证泄露或 SQL 正确性失效，不冒充已确认 `P0/P1` 事故。

## 3. Logging reconciliation

| Capability | State | Implemented / tested / qualified or deferred |
| --- | --- | --- |
| JSON structured output | `MISSING` | 文本 console pattern；未发现 JSON encoder 或测试。L01 deferred。 |
| Console output | `IMPLEMENTED` | `nq-app/application.yml` 文本 pattern；L02。 |
| Rolling file / journald boundary | `MISSING` | 无已接受持久化 owner 合同；L03 deferred。 |
| Rotation / retention / totalSizeCap | `MISSING` | 分别为 L04/L05/L06；L6 明示 rotation 未资格。 |
| Global sanitization of Authorization/API key/JWT/password | `IMPLEMENTATION_GAP` | 局部 producer mask 存在；prod console 13/13 synthetic canary 最终输出泄漏，L07 保持阻断。没有读取或打印真实凭证。 |
| HTTP traceId | `IMPLEMENTED` | Filter/MDC/finally 和既有 test；L08。 |
| Async MDC; scheduler/WS/reconcile | `PARTIAL` | 手工入口可见，无全域跨线程证明；L09/L10。 |
| ErrorCode/ErrorKey | `PARTIAL` | common enum、API selected identity、frontend catalog 已接受；非全错误编号；L11。 |
| Global EventType convention | `PARTIAL` | 域内 enum 存在，无全局日志 EventType；L12。 |
| Runtime log-capacity metrics | `MISSING` | F007 的业务操作指标不等于日志容量监控；L13。 |
| L6 log-growth measurement | `IMPLEMENTED` | 60min accepted evidence 六窗口及 test-only 10s sampler；L14；不证明轮转或长期上限。 |

`F007 operational metrics != complete logging baseline`。F007 的五类 operation 和 health 摘要已由其技术 pair 接受；不能扩展为 JSON、脱敏、轮转或持久化平台验收。L6-B 的 180min 接受也未提供轮转/保留合同。

## 4. SQL ownership reconciliation

本次完成**有界静态 inventory**，没有找到已执行并接受的**完整专项 SQL ownership audit**。生产 Java 中引用 JDBC/RowMapper 的 98 个文件主要位于 `nq-infra` (92)；`nq-app` 六个引用主要为 Spring wiring，其中 `MinimalLivePilotConfiguration` 内有 pilot recovery 的只读 SQL。`nq-api`、`nq-core` 的少量 SQL 文本命中并非由 JDBC 执行，不能据字符串计数推断 persistence owner。`JdbcOrderRepository`、`JdbcTradeRepository`、`JdbcLedgerPostingRepository` 等是选定 canonical correctness SQL owner；Phase4/6 及 Phase7-B 的接受证据覆盖其对应不变量。

同表多 read model（如 `OrderRecord` 与 `OrderQueryView`）不自动等于双 canonical entity owner；inline SQL 和多 RowMapper 是维护债线索，未证明 release-blocking duplicate mutation。Pilot runner 的 SQL 会影响 recovery 是否再次执行 reconciliation，需要在 S10 对象级核对与 repository owner/完整性语义；本轮没有证据证明它已允许账务绕行或重复交易，故没有擅自标 P1。manual seed SQL 的 P2 既有边界继续生效。51 个 Flyway migration 仅作路径/模式 inventory；没有改写或运行。

`RELEASE_BLOCKING_SQL_CORRECTNESS_DEFECT_CONFIRMED=NO`；`FULL_SQL_OWNERSHIP_AUDIT_ACCEPTED=NO`；`SQL_TRACEABILITY_BLOCKER_FOR_THIS_TASK=YES`。若 S10 证明相同 correctness invariant 多 owner 或业务层绕过 canonical mutation repository，必须立即重新分类并停止依赖该路径的 release。

## 5. F-005/F-011 recovery and eligibility

Recovery boundary：current `ROADMAP`/Phase7 archive → Phase4 `TESTING`/`WORKLOG` → Phase4 closeout 前后 `git show` → `git log -G`/`git grep` → historical gate docs。Phase4 原 ledger 仅支持两个 ID 为 `P2 / DEFERRED`，没有可验证 title、原始 evidence、consumer 或 owner；`WORKLOG` 记录先前对 tracked refs、all refs、reflog、unreachable blobs 的负结果。本次未从 GateF `F-005` 或 Phase5 `P5-F005` 借用语义。两行维持 `SOURCE_IDENTITY_UNRECOVERABLE`；原 severity 以 ledger 的 P2 为限，原 mandatory 身份未知，后续 `RETIRED / ARCHIVE_ONLY` 属现行处置。

本次矩阵所有行已分类，L07 负例已证明实现缺口，S10 对象级签收未完成。因此结果只能是：

```text
BLOCKED / LOGGING_SENSITIVE_DATA_PROTECTION_IMPLEMENTATION_GAP
ORIGINAL_SCOPE_FULLY_ACCOUNTED_FOR=NO
PHASE7_E_ELIGIBLE_BY_THIS_RECONCILIATION=NO
CONFIRMED_CURRENT_P0=0
CONFIRMED_CURRENT_P1=0
BLOCKING_GAPS=2
```

下一次处理顺序：先单独修复 L07 的共享日志输出边界并用本 synthetic reproducer 复验；之后才对 S10 的跨层/关键查询做对象级 owner 审查。仅在两行有可复验证据和最终处置后重新计算矩阵，不需要重新执行 Phase4–6 qualification。既有 immutable archive 保持不变。
