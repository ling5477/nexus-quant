# GateW 单交易所准实盘准备与 Shadow-to-Live 安全门槛计划

英文名：`Single-Venue Pre-Live Readiness & Shadow-to-Live Safety Gate`。

任务：`NQ-GATEW-PLAN-IMPLEMENTATION`。

状态：GateW planning baseline、GateW-1 与 GateW-2 均为 `ACCEPTED / CI_GREEN`（已接受 / CI 已通过）。GateW-3 bounded read-only reconciliation acceptance head `71e1ded5a9896996717549d2a96068356dea7288` 的 exact-head run `29324600871` 已成功，当前为 `COMMITTED / CI GREEN / CONTINUE REQUIRED`（已提交 / CI 已通过 / 需要继续）；GateW-3 尚未整体 accepted。

## 1. Current State

- 唯一阶段 authority 是 [STATUS.md](STATUS.md) 的 `nq-current-authority` schema v3 区块。
- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag 为 `nq-gatev-freeze`，peeled commit 为 `530ce4e2bde416aa61944262cbfbadca556656cb`。
- GateW-PLAN 是当前 accepted baseline：`ACCEPTED / CI_GREEN`；GateV-FREEZE 继续作为最近冻结 Gate 的历史证据，不覆盖 current authority。
- GateW：`IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- GateW-PLAN、GateW-1、GateW-2：`ACCEPTED / CI_GREEN`；GateW-2 `REAL_SMOKE=NOT_RUN`。GateW-3 read-only reconciliation：`COMMITTED / CI GREEN / CONTINUE REQUIRED`；仅接受 OKX Spot、最多 3 symbols、typed `Read` snapshots、bounded local SELECT 与 pure no-side-effect comparator。
- LIVE：`DISABLED`；Shadow trading：`NOT ENABLED`；AI：`NOT STARTED`；DH runtime：`NOT INTEGRATED`；Integration runtime：`NOT STARTED`。
- RealClient、real provider、private trading adapter：`NOT IMPLEMENTED`；GateW-2 private read-only diagnostic probe 已实现并获 CI 接受，但 real smoke/远端 permission verification 为 `NOT_RUN / UNKNOWN`；Python live execution ready：`NO`。

## 2. Goal

GateW 只为一个明确 venue 建立“准实盘但仍不可交易”的安全能力：以 **OKX Spot**、单账户、少量 symbol 为边界，逐步验证 private read-only capability、credential 隔离、permission readiness、账户/余额快照、reconciliation、dry-run order preview、风险前置、安全开关、人工复核证据、read-only soak 与 incident/restore drill。

GateW 的完成条件不是“可以真实下单”，而是证明系统在 LIVE 继续关闭时，能够以可审计、可停止、可恢复、fail-closed 的方式准备后续 GateX 的小资金交易评审。

## 3. Non-goals

- 不选择或并行规划 Binance、Bybit、Gate、Coinbase 或其他 venue 的 private 接入。
- 不启用 LIVE 或 Shadow trading，不实现真实下单、撤单、改单、转账、提现或资金划拨。
- 不把既有 `TradingAdapter`、OKX 历史实现、credential metadata、NoReal probe 或 readiness DTO 写成 real-ready。
- 不接 AI、DH runtime 或 Integration runtime，不改变 NQ 交易状态。
- 不把 risk preflight、dry-run preview、human approval evidence 或 kill switch 状态解释为 trading authorization。
- 本 planning task 不修改业务代码、API、migration、checker、CI workflow、部署或 credential material。

## Planning Scope and Change Fence

本轮允许变更仅限：GateW plan、GateW current task evidence/index，以及为 schema v3 authority、入口、路线、fact-source index 和 append-only ledger 所需的最小文档同步。根 `README.md` 只更新阶段入口摘要。

本轮明确禁止修改：`backend/**`、`frontend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**`、任何 migration、`docs/gates/**`、`docs/archive/**`、`.agents/**`、POM/package/lock files；禁止读取真实 credential 文件或调用真实交易所。

## 4. GateV / GateW Boundary

GateV 冻结的是本地诊断证据、durable operator review、受控只读 scheduler 与 review workbench。GateV 的 `acknowledge / escalate / resolve / close` 只表达本地诊断复核，不表达交易批准。

GateW 从该 no-trading baseline 出发，新增的重点是 **单 venue private read-only readiness 与 Shadow-to-Live safety gate**。GateW 可以评估和记录 OKX Spot private read-only 事实，但仍不得建立真实订单写路径，也不得复用 GateV review state 作为交易授权或 human approval 的主事实。

## 5. Single-Venue Decision: OKX Spot

GateW 唯一目标交易所为 **OKX Spot**，理由如下：

1. 仓库已有 `nq-adapter-okx`、OKX public/historical adapter、历史 REST/WS/reconciliation 代码与测试证据，可先做当前化隔离审计，不需要同时引入第二套 venue 方言。
2. 当前产品第一阶段是数字货币现货；Spot 避免合约、杠杆、借贷、资金费率、强平等额外状态和风险模型。
3. 单 venue、单账户、最多 3 个 allowlisted Spot symbol 可限制 credential、permission、rate limit、reconciliation 和故障恢复的组合复杂度。
4. 仓库也存在 Binance adapter，但 GateW 明确不扩展其 private capability；Binance 只作为防止跨 venue 误装配的负向测试对象。

该选择只是 planning scope，不证明 OKX private endpoint、credential、账户余额、交易权限或真实订单能力已经可用。

## 6. Verified Existing Capability Inventory

| 领域 | 当前事实 | GateW 处理 |
| --- | --- | --- |
| Adapter contract | `AdapterCapability` 已区分 public/private、order、account balance、permission probe 等能力，但尚不是 GateW typed endpoint policy | GateW-1 当前化为 OKX Spot capability matrix 与 default-deny endpoint policy |
| Public adapter | 已有受 policy/guard 约束的 public marketdata 与 historical path | 与 private client、credential、profile、transport、metrics 完全隔离 |
| OKX trading adapter | 历史代码包含 place/cancel/query/open-orders/fills 等路径；应用装配依赖 readiness fail-closed | GateW-1 先审计并确保 forbidden mutating capability 不可能从 GateW profile 可达 |
| Credential | DB 密文、active version、metadata/audit 与结构校验已存在；adapter runtime 默认使用 unconfigured placeholder | GateW 后续只允许 scoped loader 将最小 material 临时交给 read-only private client |
| Permission probe | Service/port/schema/API 已存在，默认 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED` | GateW-2 才允许显式非默认 profile 下的真实 read-only probe |
| Trading preflight | 已有 GET-only aggregate，固定 `RISK_PREFLIGHT_BLOCKED`，不调用 adapter/probe/risk/order | GateW-3 扩展候选仍须保持 diagnostic 与 authorization 分离 |
| Shadow preview | 已有 no-side-effect local preview/read model | GateW-3 建立 venue-rule-aware order preview，但不得依赖 `TradingAdapter` 写接口 |
| Kill switch | `nq-risk` 已有本地 kill switch risk rule/service | GateW-4 审计其作用域、持久化和 private-read/soak 停止语义；不宣称已满足 GateW |
| Reconciliation | 历史 OKX scheduler/reconcile/recovery 可查询并写本地 order/trade/ledger | GateW 不直接复用写侧；先建立独立 read-only comparison flow |
| Frontend | accounts、trading、runtime readiness 页面与 API/hook 已存在 | 只规划 GateW readiness/control evidence，不新增真实下单按钮 |

## 7. Official Fact-Source Rule

所有 OKX endpoint path、HTTP method、签名串、header、权限、rate limit、instrument 字段、fee、错误码和废弃状态，只能来自 implementation 当日重新打开的 OKX API v5 官方文档：<https://www.okx.com/docs-v5/en/>。

GateW-1 必须建立可审计的官方文档事实表，至少记录：官方 URL、页面/章节标题、核验日期、适用产品 `SPOT`、请求是否 private、是否只读、所需 permission、rate-limit 维度、错误码来源、文档版本/变更提示和 reviewer。历史 GateC/GateN/GateO 文档只能帮助定位，不能替代当日官方核验。

本计划不写任何具体 endpoint 结论，也不把仓库中现有 path 常量视为已核验事实。

## 8. Public / Private Adapter Isolation

1. public 与 private 使用不同 contract、client、configuration namespace、Spring profile、transport bean、metrics 与 audit category；public path 不接受 credential 参数或 authenticated signer。
2. private capability 采用 typed matrix 与 endpoint registry；未登记能力、未知 method/path、产品非 `SPOT`、账户或 symbol 越界一律 default-deny。
3. GateW private profile 必须显式、非默认且只允许 OKX；CI、default、local、paper、freeze profile 不得因配置缺省自动启用。
4. private read-only client 不实现或暴露 place/cancel/amend/transfer/withdraw 方法；不得通过 string path 或通用 raw request 绕过 registry。
5. GateW-1 的测试使用 fake transport，并断言真实网络调用次数为 0；Binance 与未知 venue 必须 fail-closed。
6. 既有 `OkxExchangeAdapter` 的 mutating/query 历史路径不得直接注入 GateW read-only service；若需要读取能力，使用新的窄 contract 隔离。

## 9. Allowed and Permanently Forbidden OKX Capabilities

### GateW 可进入候选集

以下是 capability 级候选，不是 endpoint 结论；每项都必须先通过官方文档核验、typed allowlist 和独立测试：

- `PERMISSION_READINESS_PROBE`：验证 credential 可认证及只读权限边界。
- `ACCOUNT_CONFIGURATION_READ`：读取 Spot 账户配置/模式的最小安全摘要。
- `BALANCE_SNAPSHOT_READ`：读取单账户、allowlisted currency 的余额快照。
- `OPEN_ORDER_READ`、`ORDER_STATUS_READ`、`FILL_HISTORY_READ`：仅供 GateW-3 reconciliation，且必须与 mutating trading client 物理隔离。
- `INSTRUMENT_RULE_READ` 与只读 fee/rate 元数据：仅供 tick size、lot size、min notional、fee estimate 和 preview 校验。

### GateW 永久禁止

- place、amend、cancel、batch order、algo order、trigger order 或任何会创建/改变/撤销订单的能力。
- transfer、withdraw、deposit-address management、sub-account transfer、funding movement 或资金产品操作。
- leverage、margin、borrow/repay、derivatives、options、futures、swap、position mode mutation。
- API key 创建/编辑/删除、permission mutation、IP allowlist mutation。
- 任意通用 raw private request、未分类 endpoint、private WebSocket order channel 或不能证明只读的 endpoint。

## 10. Credential Loading, Decryption, Use and Redaction

- 当前 encrypted payload 以 DB 为主数据源，repository 使用配置的 master key 解密；GateW 不改为 `.env` credential 文件或全局进程变量直读。
- 后续 loader 必须绑定 owner、tenant、exchange account、credential type、active version、OKX venue 和 read-only operation；缺少任何 scope、key、active 状态或 expected permission 都 fail-closed。
- 解密只发生在最窄 infrastructure boundary；material 不进入 Controller DTO、domain event、exception message、audit metadata、metrics tag、trace baggage、cache 或持久化 snapshot。
- private client 只在单次调用生命周期内使用最小字段；禁止输出 raw request/response/header/signature。Java 无法可靠保证不可变 `String` 立即清零，因此设计目标是减少复制、缩短存活期、避免 heap/cache 扩散，并优先使用可覆盖 buffer 的安全封装；不得伪称“已完成内存擦除”。
- 日志和 evidence 只允许 credential configured/unavailable、credential ID、type、version、masked fingerprint、probe category；任何疑似 secret 统一 `REDACTED`，并由负向测试验证。
- master key 缺失、解密失败、payload schema 不匹配、credential 非 active、venue/account 不匹配均阻止 probe，不降级为 NoReal 成功或匿名 public 请求。

## 11. Real Permission Probe Definition and Failure Semantics

`real permission probe` 是在显式 GateW read-only profile 下，使用 scoped OKX credential 调用一组经官方文档确认的最小 private read-only capability，以判断“认证可达且权限未超出 GateW 允许范围”。它不是下单测试、资金测试或 LIVE authorization。

结果模型至少包含：`NOT_RUN / IN_PROGRESS / PASSED_READ_ONLY / BLOCKED / FAILED / SKIPPED`，以及脱敏分类：credential unavailable、decrypt failure、auth rejected、IP allowlist mismatch、unexpected trade/funding/withdraw permission、rate limited、timeout、exchange 5xx、invalid response、clock/timestamp failure、unknown。只有所有 required read-only checks 明确通过、无 unexpected permission、无 forbidden call 时才可标记 `PASSED_READ_ONLY`；任何未知或部分成功均 fail-closed。

GateW-2 必须有 hard timeout、无自动重试或仅对安全只读且有 bounded policy 的明确重试、rate-limit backoff、concurrency=1、单账户 scope、request/trace ID 分离与脱敏 audit。真实 credential 或 raw provider response 不进入 task evidence。

## 12. Account / Balance Snapshot Persistence

结论：GateW read-only soak、reconciliation、incident drill 与 freeze 需要 durable snapshot evidence；仅返回瞬时 DTO 不足以审计差异和恢复。但 GateW-1 **不新增 migration**。

现有 legacy `account_snapshots` 与 `accounts` 绑定，不能在未做 schema/owner/retention 审查时直接作为 `exchange_accounts` 的 GateW 事实源。GateW-2 前必须独立 schema review，决定新建 scoped append-only snapshot 表还是经 forward-only migration 建立安全关联。快照只保存业务余额/可用/冻结等 allowlisted 数值、venue timestamp、capture timestamp、account/credential version reference、checksum 和脱敏状态；禁止 credential、raw payload、header、signature 和完整 provider response。

## 13. Reconciliation Fact Source and Difference States

- 远端 OKX read-only snapshot 是“交易所当时观察值”；本地 NQ DB 是“平台当时记录值”。两者均带 observation/capture time、scope 和 freshness，任何一方都不能无时间边界地覆盖另一方。
- reconciliation 只比较单 OKX Spot 账户、allowlisted symbols/currencies；不写订单、成交、余额、持仓或 ledger，不自动修复。
- 差异状态至少区分 `MATCHED / LOCAL_ONLY / VENUE_ONLY / VALUE_MISMATCH / STATUS_MISMATCH / STALE / PARTIAL / UNAVAILABLE / ERROR / UNKNOWN`；`UNKNOWN` 和 `PARTIAL` 不得折叠成 matched。
- 结果要保存 source versions、comparison window、tolerance/rounding policy、difference summary、evidence anchors 与脱敏 error category；raw provider body 禁止持久化。
- 既有 `OkxRestReconcileService` 会推进本地 order/trade/ledger，不可直接用于 GateW read-only reconciliation。

## 14. Dry-run Order Preview and Venue Rules

GateW-3 的 order preview 是纯计算/只读验证对象：输入显式 account、Spot symbol、side、order type、price/quantity candidate 与已核验 instrument/fee/risk facts，输出 normalized price/quantity、notional、fee estimate、venue-rule violations、risk blockers 和 evidence version。

保证绝不提交订单的结构性要求：

1. preview service 不依赖 `TradingAdapter`、order command、scheduler、HTTP raw client 或任何 mutating port。
2. GateW private client contract 不暴露 order mutation；preview 不产生或伪造真实 exchange order ID。
3. endpoint guard 将所有 order/cancel/amend/transfer/withdraw capability 永久 deny；测试断言 forbidden transport 调用为 0。
4. preview result 固定包含 `notTradingAuthorization=true`、`liveDisabled=true`、`orderSubmitted=false`，并有 TTL/expiry；过期 metadata 必须重新计算。
5. tick size、lot size、min/max size、min notional、fee tier、rounding mode、quote/base currency 与 instrument status 均来自经官方文档核验并带版本/时间的 facts；未知即 BLOCKED，不猜默认值。

## 15. Risk Preflight vs Trading Authorization

Risk preflight 回答“候选请求是否违反已知风险/venue/数据质量规则”，trading authorization 回答“是否被允许对真实账户执行”。二者必须是不同 contract 和状态机。

GateW 只允许前者保持 read-only/diagnostic；即使所有 risk checks 通过、permission probe 通过、human review 通过、soak 通过，也不能产生 trading authorization。真实授权、最小资金、订单提交和取消必须由后续 GateX 的独立 policy、审批、rollout 和 rollback 决定。

## 16. Kill Switch and Human Approval Evidence

- LIVE 未启用时，kill switch 仍用于一键阻止 private read probe、snapshot capture、reconciliation、preview refresh 和 soak scheduler；默认 engaged/deny，状态未知或存储不可用时 fail-closed。
- GateW-4 必须验证进程重启、并发任务、缓存、timeout 和 partial failure 下的停止传播，并记录触发者、原因、时间、scope、前后状态和 drill evidence；它不承担取消真实订单，因为 GateW 禁止真实订单。
- human approval evidence 需要 durable、append-only、可过期、可撤回且绑定具体 snapshot/reconciliation/preview/soak evidence version。它表达“安全门槛已人工复核”而不是“批准交易”。
- 不复用 GateV `validation_review_cases` 作为交易授权；GateW-4 前进行独立 schema/security review，必要时以 forward-only migration 建立 safety-gate review facts。

## 17. Read-only Soak and Exit Criteria

GateW-4 执行 **连续 7 天** read-only soak，并设置 24 小时早期 checkpoint；scope 固定 1 个 OKX Spot 账户、最多 3 个 allowlisted symbols/currencies，concurrency=1。

必须采集：probe/snapshot/reconciliation 成功率与 freshness、rate-limit/timeout/auth/error 分类、差异数量与收敛时间、scheduler gap/overlap、kill-switch propagation、credential/redaction violations、forbidden endpoint/network attempts、resource usage 和恢复事件。

退出标准：

- forbidden mutating call、真实订单 ID、资金/账户/订单/ledger side effect、credential 泄漏均为 0。
- P0/P1 finding 为 0；unexpected permission 为 0；所有 unknown/partial 状态均有明确 blocker，不被记作 pass。
- 计划内 read-only cycle 完成率至少 99.5%，无未解释的连续 gap；rate-limit/timeout/5xx 在既定预算内且恢复行为通过。
- snapshot freshness 与 reconciliation difference 满足经 review 冻结的阈值；阈值必须在 soak 前写入配置/evidence，不能事后调整以制造通过。
- kill-switch、backup/restore、incident drill 与 reviewer evidence 均通过，且最新 evidence 未过期。

任一 hard criterion 失败则 soak 重置或明确延期；不得以人工说明覆盖 forbidden call、secret exposure 或 state mutation。

## 18. Backup, Restore and Incident Drill

GateW-4 必须在隔离/可销毁环境验证：snapshot/reconciliation/approval evidence 的备份、校验、restore、schema version、retention 和重放顺序；credential payload/master key 不进入普通 evidence export，恢复后也不得通过日志或报告暴露。

Incident drill 至少覆盖：credential auth failure、rate limit、timeout/5xx、stale snapshot、reconciliation mismatch、unexpected permission、kill-switch trigger、scheduler overlap 与 DB unavailable。每个场景记录 detection、containment、stop、recovery、evidence integrity 和 operator handoff；不调用真实订单或资金接口。

## 19. DB / Migration Decision

- GateW-1：明确 `NO MIGRATION`；只实现 capability/guard/profile/fake-transport 测试。
- GateW-2 之前：对 permission evidence、exchange-account snapshot 做独立 schema review；只有 durable evidence 确有必要且现有表不能安全承载时才新增 forward-only migration。
- GateW-3/4：reconciliation result 与 human approval evidence 如需持久化，同样必须有独立 schema、安全、retention 与 restore review；禁止在普通实现中偷加 migration或修改历史 migration。
- 任何新表/字段必须有中文 comment、敏感信息禁入、owner/account/venue scope、状态 CHECK、append-only/retention 语义和 PostgreSQL/Testcontainers 证据。

## 20. Candidate API Plan

本轮不实现 API。后续只允许规划/实现 authenticated read-only 或 no-side-effect surface：

- permission readiness summary；
- account configuration snapshot；
- balance snapshot；
- reconciliation result；
- dry-run preview；
- safety-gate/kill-switch/human-review status。

禁止 place/cancel/amend/transfer/withdraw、enable LIVE、bypass risk、force approval 或通用 raw private endpoint。实际 path、method、request/response 与错误语义必须在对应 implementation 中基于官方文档和现有 `/api/**` 约定复核。

## 21. Frontend Plan

GateW 前端仅在既有专业金融后台中展示：OKX Spot readiness、credential configured/unavailable、permission probe、account/balance snapshot、reconciliation difference、dry-run preview、kill-switch、human-review evidence 与固定 `LIVE DISABLED` 风险提示。

所有 loading/empty/error/stale/expired/blocked/permission-denied/kill-switch-engaged 状态必须可见；风险操作需确认。不得新增成熟实盘终端、真实下单/撤单按钮、LIVE enable、funding/withdraw 控件，也不得把 green readiness 卡片解释为交易授权。

## 22. Implementation Batches

### GateW-1

任务：`NQ-GATEW-1-OKX-SPOT-CAPABILITY-AND-ENDPOINT-GUARD-IMPLEMENTATION`。

最小代码切片：审计现有 OKX adapter/credential path；建立 typed capability matrix、public/private 分离、private endpoint allowlist/denylist、显式非默认 profile 和 fail-closed guard；只用 fake transport；不访问网络、不读取真实 credential、不实现订单提交、不新增 API/migration。必须产出真实 Java 代码和测试，不能再是纯文档任务。

### GateW-2

实现显式非默认 profile 下的 OKX Spot 真实只读 permission probe 与 account/balance snapshot；禁止订单和资金权限，credential 全链路脱敏。进入实现前必须完成独立 security review；若持久化则先完成独立 schema review。

### GateW-3

实现 read-only reconciliation、dry-run order preview、venue rule/fee/notional/tick/lot 校验和 risk preflight；不提交订单、不产生真实订单 ID、不写 account/fund/position/order/ledger。必须完成独立 security + risk + no-side-effect review。

### GateW-4

实现 kill switch、durable human-review evidence、7-day read-only soak、backup/restore 与 incident drill；LIVE 继续关闭。必须完成独立 security + operations + persistence review。

### GateW-FREEZE

汇总 exact-HEAD CI、official-doc evidence、security/schema/risk reviews、soak/restore/incident evidence与 strict archive。Freeze 后真实订单提交、撤单、转账、提现仍关闭；真实小资金下单只能进入 GateX。

## 23. Risk-based Review Requirements

- GateW-1：capability/endpoint guard、profile/wiring、no-network 与 forbidden-path code review；P0/P1 关闭后才接受。
- GateW-2：独立 credential/private-read security review；存在 migration 时加 DB schema review和 PostgreSQL evidence。
- GateW-3：独立 security、risk、numeric precision、official venue rule 与 no-side-effect review。
- GateW-4：独立 security、operations、persistence/retention、backup/restore 与 incident response review。
- Freeze：复核所有 batch exact acceptance-head CI、跨 batch ancestry、官方事实新鲜度、P0/P1=0、LIVE/Shadow/real-order hard boundary 与 archive checker。

这些 review 是 implementation acceptance 的风险控制，不增加 `NQ-GATEW-PLAN-REVIEW`、plan freeze 或 planning addendum。

## 24. Task-evidence Archive Compatibility

结论：`SUPPORTED`（已支持）。

`governance-workflow-contract.json` 已明确允许 `docs/current/evidence/<line>/README.md`、两位 attempt 文件，以及未来 `docs/gates/<gate>/source/task-evidence/**`。Archive checker 将该目录识别为 approved non-role source evidence：不参与 mandatory/conditional role 计数，nested README 不占顶层 `archive-entry` role，approved path 不触发 `UNKNOWN_ARCHIVE_FILE`。

GateW Freeze 时只复制 accepted attempts 到 archive evidence root，并继续满足 GateW mandatory/conditional role、顶层 README、内容完整性、路径穿越、symlink/reparse point 与 unknown-file hard gate；task evidence 不能替代 plan、testing、boundary、known-limitations 等 archive roles。

## 25. Test and CI Strategy

- GateW-1：capability matrix、unknown/forbidden endpoint、venue/profile/wiring、no-network fake transport、credential-unavailable、Binance/unknown venue deny、mutating adapter unreachable 单元/装配测试。
- GateW-2：credential scope/redaction、permission state/error taxonomy、timeout/rate-limit/IP allowlist/unexpected permission、PostgreSQL snapshot repository 与 explicit-profile integration；真实 probe 必须是显式手工/安全受控测试，不进入默认 CI。
- GateW-3：numeric precision/rounding、instrument/fee fact version、reconciliation difference matrix、stale/partial/unknown、preview no-order dependency 与 zero forbidden call 回归。
- GateW-4：kill-switch propagation、restart/concurrency/failure、approval expiry/revoke、soak metrics、backup/restore checksum 与 incident drill。
- Freeze：全量 backend、必要 frontend build/E2E、fresh PostgreSQL、docs checkers、strict archive 与 exact-HEAD CI；所有未运行项如实记录。

## 26. Security Boundary

- NQ-only；不修改 DH authority，不启动 Integration runtime。
- 不读取 `.env`、key/pem、secrets、credential files；不把 credential、signature、header、raw provider payload 写入文档、日志或 evidence。
- LIVE、Shadow trading、real order submission、cancel、transfer、withdraw 均保持 disabled。
- public/private、diagnostic/authorization、risk/authorization、human review/authorization、preview/execution 必须是独立 contract。
- 任意 unknown capability、unknown endpoint、unknown permission、unknown freshness 或 missing evidence 均 fail-closed。

## 27. Findings and Risks

- P0：0。
- P1：0；GateW-1 已按本计划完成 capability/guard/profile 最小实现；real permission probe、private read client 与任何交易写侧仍未实现。
- P2：`CLAUDE.md` 仍硬编码 GateJ/GateK 历史阶段；它不属于 current authority，本任务不越过 allowlist 修改。现有 OKX trading/reconcile 历史实现包含真实写侧语义，GateW-1 必须证明其在 GateW profile 下不可达。
- P3：当前 broad audit 首轮 glob 未排除嵌套 `backend/*/target/**`，只读命中了 Maven metadata 列表；后续已改为 `!**/target/**`。未读取 credential 或生成任何变更，但后续任务应直接使用递归排除 glob。

## 28. GateW-1 Implementation Record

`NQ-GATEW-1-OKX-SPOT-CAPABILITY-AND-ENDPOINT-GUARD-IMPLEMENTATION` 已取得 `ACCEPTED / CI_GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，CI run `29219687588`。

- 新增 typed `ExchangeCapability`、`EndpointAccessClass`、`EndpointPolicyDecision`、`EndpointGuardReason`；decision 固定 `tradingAuthorization=false`。
- 新增 `OkxSpotCapabilityMatrix` 与 `OkxSpotEndpointGuard`：public path 仅精确 GET allowlist；private read runtime disabled；order/cancel、transfer/withdraw、unknown 与 URI 变体均 default-deny。具体 private endpoint allowlist 保持空，留待 GateW-2 在官方事实与独立 security review 后建立。
- `gatew` profile 不注册 OKX/Binance mutating trading Bean、private WebSocket Bean 或会构造 public HTTP client 的 `OkxHistoricalKlineAdapter`；未新增 HTTP client、scheduler、runner、credential loader 或 API。
- `PUBLIC_MARKET_DATA` 维持既有实现合同但 default runtime disabled；guard 的 `ALLOW_PUBLIC_READ` 仅表示可交给既有 public policy 继续裁决，不表示 default egress、provider readiness 或交易授权。
- 验证：最小 reactor、全量 `mvn -f backend/pom.xml test` 与治理检查均以本次 evidence 为准；full Maven 测试过程中现有 local Spring 测试对本地 DB 应用了既有 V33，未新增或修改 migration。

## 29. Final Decision and Next Task

历史 authority snapshot（GateW-2 implementation 前）：GateW `IN_PROGRESS / NOT_FROZEN`；`accepted_batch=GateW-1 / ACCEPTED|CI_GREEN`；`work_batch=GateW-2 / NOT_STARTED / NONE / NOT_RUN`。

当时唯一下一动作：

```text
NQ-GATEW-2-IMPLEMENTATION
```

执行该治理动作前，必须先完成 `NQ-GATEW-2-SECURITY-REVIEW-COMMIT-AND-PUSH` 并取得 review commit exact-HEAD CI green。GateW-2 implementation 不得偏离下方冻结安全基线。

## 30. GateW-2 Pre-implementation Security Baseline

GateW-2 只批准两个 typed operation：

| Operation | Method | Exact path | Query | Permission |
| --- | --- | --- | --- | --- |
| `OKX_ACCOUNT_CONFIGURATION_READ` | `GET` | `/api/v5/account/config` | 无 | `Read` |
| `OKX_ACCOUNT_BALANCE_READ` | `GET` | `/api/v5/account/balance` | 仅 server-side `ccy` allowlist；uppercase、排序、去重、最多 3 个 | `Read` |

协议事实源为 2026-07-13 访问的 OKX 官方 [API guide](https://www.okx.com/docs-v5/en/) 与 [API changelog](https://www.okx.com/docs-v5/log_en/)。private REST header、timestamp、query-in-signature、rate limit 与错误语义必须在实施当日继续以官方页面为准；未确认事实不得加入 production mapping。

### 30.1 Permanent Deny and Contract Shape

- production API 只接收 typed operation；禁止 arbitrary path/method/host/query 和 generic raw request。
- order submit/cancel/amend、transfer、withdraw、funds movement、unknown operation/path/method/host/query 永久拒绝；GateW-1 mutating/funds deny 不得弱化。
- config 必须先执行且 permission 集合只能为 read-only；发现 `trade` 或 `withdraw` 立即 blocked，不执行 balance。所有 decision 固定不构成 trading authorization。

### 30.2 Credential Selection and Decrypt Boundary

- selection key 固定 `(ownerId, exchangeAccountId, credentialType=OKX_API_V5)`；account owner、OKX、ACTIVE 与安全 environment 必须一致。
- 0 个 active credential 为 unavailable，1 个才可继续，>1 为 conflict；disabled/revoked/expired/rotated/inactive 全部排除。禁止 `ORDER BY ... LIMIT 1`、隐式账户选择、credential type fallback 或把 `permission_scope` 当成交易所权限。
- plaintext 只存在于最窄 infrastructure callback/executor；不得进入 domain、DTO、cache、singleton、日志、审计、evidence 或数据库。可清理 buffer 在 `finally` 清零。现有 immutable `String` decrypt contract 是 P2，GateW-2 不复用其跨层明文路径，也不在本批次做密码学重构。

### 30.3 Signer, Host and Transport Boundary

- signer 仅接 temporary secret context、typed operation、canonical query 与 injected `Clock`；GET body 为空。timestamp 使用官方 UTC ISO-8601 毫秒格式，query 进入 prehash。
- global host 精确固定为 `https://openapi.okx.com`；redirect 为 NEVER，3xx 失败；regional host 不 fallback，需另行 scope review。demo 仅在显式环境下加入 `x-simulated-trading: 1`，不得自动切换环境。
- local security limits：connect 2 秒（最大 5 秒）、request/read 5 秒（最大 10 秒）、response 256 KiB、concurrency 1、无自动 retry。禁止 raw request/response、authenticated header、signature、prehash 与 provider body 日志。
- timeout、network、redirect、oversize、malformed JSON、HTTP、provider code、auth/signature、permission、rate limit、clock skew、environment mismatch、partial response 分别映射 sanitized error taxonomy；任何错误均 fail-closed，不降级为 READY。

### 30.4 Profile, Observation and Persistence

- 必须同时满足 profile `gatew-okx-readonly`、feature flag `nq.gatew.okx-private-readonly.enabled=true`（默认 false）、LIVE false、显式 owner/account/type 与人工触发。
- default/local/test/CI 不创建真实 private transport，不读取/decrypt credential，不 outbound；禁止 scheduler、runner、`@PostConstruct` network、startup probe、后台轮询和 mutating Bean。
- 采用方案 A：config/balance 仅返回 in-memory diagnostic observation；固定 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`orderSubmitted=false`。缺失/blank 字段为 `PARTIAL/UNKNOWN`，不得补零。
- 不写既有 probe metadata、account、audit payload、ledger 或 snapshot；不做 reconciliation，不构成 durable snapshot evidence。本批次 `NO MIGRATION`；durable snapshot 必须另起 schema/migration review。

### 30.5 Manual Smoke and Test Baseline

- `REAL_SMOKE=NOT_RUN`。本 review 与 implementation 默认测试均不要求 API Key；用户不得在聊天、CLI 参数或 evidence 中粘贴明文。
- 后续 real smoke 只能通过 NQ 本地 credential 管理路径、CI 外人工显式执行并单独取证。Key 只允许 Read，Trade/Withdraw 必须关闭；缺失时 `BLOCKED / API_KEY_REQUIRED`。mock/unit/历史日志不得冒充真实联通。
- tests 必须覆盖 typed allowlist/canonical query/signer Clock fixture、0/1/>1 credential、owner/type/lifecycle、redaction、timeout/redirect/body limit/no retry、permission/partial/error taxonomy、negative profiles、no startup/no outbound/no persistence，以及 mutating/funds zero-call。

## 31. GateW-2 Security Review Decision

结论：`PASS / SECURITY_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED`。P0=0，P1=0。实现严格遵循 baseline、无 migration、无 real smoke 且无 P0/P1 时，完成后只做针对 diff 的精简 security conformance review，不重复完整方案审查；真实 smoke 始终单独记录。

## 32. GateW-2 Implementation Record

结论：`IMPLEMENTED / PENDING_REVIEW`（已实施 / 待复核），P0=0，P1=0。

- 仅新增 `OKX_ACCOUNT_CONFIGURATION_READ` 与 `OKX_ACCOUNT_BALANCE_READ` 两个 typed operation；method、global host、path 与 canonical `ccy` query 全部在 wrapper 内固定，GateW-1 raw/private、mutating 与 funds movement deny 保持不变。
- 新增 injected `Clock` 的 OKX V5 signer、redirect NEVER、无 retry、bounded timeout、256 KiB 接收上限、单并发 transport；只解析 allowlisted permission 与脱敏 asset count/completeness。
- credential 以 owner/account/`OKX_API_V5` 精确选择，0/1/>1 分流；仅 infrastructure 同步 callback 内临时解密，context 逃逸被拒绝，可清理 buffer 在 `finally` 覆盖。JDBC/JDK API 边界短暂存在不可可靠清零的 immutable plaintext/authenticated-header `String`，这是 P2；其生命周期限制在 executor/transport 内，未进入 core、DTO、cache、日志、evidence 或异步任务。
- probe 固定 config-before-balance；仅远端权限集合精确为 read-only 才继续，Trade、Withdraw、unknown、missing、partial 或异常均 fail-closed。observation 只在内存返回，固定 diagnostic/no-side-effect/not-authorization/LIVE-disabled/order-not-submitted。
- Spring 仅在 `gatew-okx-readonly` + feature flag true + LIVE false 时装配 read-only transport/executor/service；默认、local、test、CI 不装配，不自动 probe、不解密、不外联，且该 profile 排除既有 mutating/private WebSocket Bean。
- 本轮无 Controller、frontend、migration、dependency、scheduler、runner、持久化 observation 或 real smoke；所有 HTTP 测试使用 fake exchange，未访问 OKX。

## 33. GateW-2 Security Conformance Acceptance and Next Task

结论：`PASS / SECURITY_CONFORMANCE_ACCEPTED / READY_TO_COMMIT`（通过 / 安全符合性已接受 / 可进入提交前复核），P0=0，P1=0。

- 指定八项 P1 候选均已确认并最小关闭：LIVE 缺省 fail-closed、credential SQL account/exchange/lifecycle scope、credential/provider cause 脱敏、config 多条 fail-closed、header control-character 拒绝、generic callback 逃逸收口、unknown balance 不补零。
- 额外收紧 empty/multi/malformed balance、必须提供 server-side `ccy` allowlist、response buffer 清理、subscriber receive-time cap 与单并发竞争测试。
- 最终 targeted reactor 与全量 Maven 均为 23/23 modules `BUILD SUCCESS`；governance、authority、link、static 与 forbidden-scope 证据记录在 conformance review attempt。
- `REAL_SMOKE=NOT_RUN`，`API_KEY=NOT_REQUIRED`；无真实 OKX 调用，无 LIVE/交易授权、mutating endpoint、API/frontend/migration/scheduler/runner 或 observation persistence。

历史 authority snapshot（GateW-2 conformance review 完成、提交前）：GateW `IN_PROGRESS / NOT_FROZEN`；`accepted_batch=GateW-1 / ACCEPTED|CI_GREEN`；`work_batch=GateW-2 / REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`。

当时唯一下一动作：

```text
NQ-GATEW-2-COMMIT-AND-PUSH
```

该 action 符合 machine contract 的 `COMMIT_AND_PUSH` 类型；下一轮只精确提交已接受的实际 diff 并等待 exact-HEAD CI，不初始化 GateW-3。

## 34. GateW-2 Exact-HEAD Acceptance Normalization

GateW-2 implementation commit `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553` 已成为 `dev == origin/dev`，其 exact-HEAD `NQ CI Baseline` run `29230512781` 为 `completed / success`。因此 current authority 最小归一化为 GateW-2 `ACCEPTED / CI_GREEN`。

该接受不改变以下事实：`REAL_SMOKE=NOT_RUN`；未证明远端 permission；未开启 LIVE；未建立 private trading、下单、撤单或资金操作授权。

## 35. GateW-3 Dry-run Order Preview Security/Risk Review

结论：`BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE`。完整 evidence 为 [NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md](evidence/gate-w/NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md)。

### 35.1 Frozen no-side-effect architecture

```text
OrderPreviewRequest
→ OrderIntentNormalizer
→ LocalVenueRuleResolver
→ FeeEstimateCalculator
→ RiskPreflightPreview
→ DryRunOrderPreviewResult
```

- 该链必须物理隔离于 `TradingAdapter`、`TradingVenueGateway`、order command/write/lifecycle、order/trade/fill/event/audit/risk-event/ledger/account/position writer、GateW-2 private transport 和 credential executor。
- 不得通过 `dryRun=true` 调用真实下单方法，不得生成 order ID，不得写 preview，不得新增 scheduler/runner/startup hook/network client。
- 第一实现切片仅限 internal application service + tests；无 Controller、无 migration、无 persistence、无 credential、无 private/public runtime network。

### 35.2 Input/result boundary

- 输入必须显式提供 owner/account；固定 OKX Spot、最多 3 个 allowlisted symbol；首切片只允许 `BUY/SELL + LIMIT`，在缺少可靠本地 reference price/slippage contract 时后置 `MARKET`。禁止 margin/futures/options/leverage/transfer/withdraw、raw endpoint、credential 和 provider DTO。
- quantity/quoteAmount/limitPrice 使用 `BigDecimal`。quantity 仅允许向下计算展示候选，price 必须精确 tick 对齐；任何调整都必须显示 requested/normalized 差异且不得静默标记 ready。
- 状态只允许 `READY_FOR_REVIEW / BLOCKED / PARTIAL / UNKNOWN`；固定 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`orderSubmitted=false`、`realOrderId=null`。

### 35.3 Venue, fee and risk boundary

- 当前本地 `instrument_catalog` 只具备 symbol/base/quote、status、tick/step、minimum quantity、source/syncedAt 等子集；缺少 maximum quantity、minimum notional 与可返回的 rule version/freshness contract。`OkxInstrumentsCache`/bootstrap fallback 也只覆盖 tick/lot/min/state，且请求时 public refresh 不在允许范围。
- 不得以硬编码常见规则、risk defaults 或网络补齐。rule 缺失、过期、冲突或 disabled 必须 fail-closed。
- 仓库无可靠本地 OKX Spot fee schedule；缺失时固定 `feeEstimateStatus=UNKNOWN`，不得补零，且 estimate 不是结算、交易所承诺或最终成交费用。
- risk preview 只选择纯只读规则；不得直接调用会改变 recent-request/rate-limit state 的 rule，不写 risk event、不消费额度、不预留资金、不创建 approval。GateW-2 real smoke 未执行，permission readiness 必须为 UNKNOWN/blocker。

### 35.4 Unblock criteria and next action

解除阻断前必须以独立授权任务建立或证明完整本地 OKX Spot rule snapshot：symbol/base/quote、tick/step、min/max quantity、min notional、precision、status、source/version、`observedAt/freshUntil`，并对最多 3 个 GateW symbol 保持同版本、可追踪、过期 fail-closed。若需要 schema/migration，必须另经 DB/security review；本审查不授权 migration。

Security/risk review attempt-01 当时记录了 venue-rule facts 缺口；该 pre-implementation snapshot 已被后续 schema review、implementation commit 与 post-commit failed CI 事实取代，不参与 current authority。

唯一下一动作：

```text
NQ-GATEW-3-BLOCKED
```

不得执行 `NQ-GATEW-3-IMPLEMENTATION`，不得初始化 GateW-4。

## 36. GateW-3 Venue-rule Facts Schema/Security Review

结论：`PASS / VENUE_RULE_SCHEMA_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED`。完整 evidence 为 [NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW.attempt-01.md](evidence/gate-w/NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW.attempt-01.md)。该结论只授权 venue-rule fact implementation，不恢复 dry-run order preview implementation。

### 36.1 Official fact classification

- OKX Public Instruments 的 `tickSz/lotSz/minSz` 分别是价格步长、Spot base-currency 数量步长和 minimum order size；`minSz` 不是 minimum notional。
- LIMIT/MARKET 最大值分别由 `maxLmtSz/maxMktSz` 表示；`maxLmtAmt/maxMktAmt` 是 USD amount 上限，必须与 quantity 上限分开建模。
- `state` 只有精确 `live` 可用于 GateW；`suspend/preopen/test`、unknown 或 blank 全部阻断。
- OKX Spot Public Instruments 未提供可直接用作 venue minimum-notional 的字段；该事实固定为 `UNKNOWN`。若 NQ 配置 minimum notional，只能是内部 risk rule。
- provider response 不提供可依赖的 schema version 或 observation timestamp；`source_schema_version/observed_at/checksum/fresh_until` 属于 NQ derived facts。

### 36.2 Selected schema and migration

选择方案 A：扩展现有 `instrument_catalog`，保持单一 current fact source。方案 B 会重复 status/tick/lot/min facts并增加 active-version/join 冲突；immutable fixture 只允许 test/fake/sandbox，不能作为 GateW production venue truth。

Migration 决策：`MIGRATION REQUIRED / PLAN ACCEPTED`。当前最高版本 V33，候选为 `V34__gate_w3_venue_rule_facts.sql`；implementation 开始前必须重新查号，不修改历史 migration。

- 将 `tick_size/step_size/min_quantity` 扩为 `NUMERIC(38,18)`。
- 新增 nullable `max_limit_quantity`、`max_market_size`、`max_market_size_unit`、`max_limit_notional_usd`、`max_market_notional_usd`。
- 新增 nullable `source_schema_version`、`observed_at`、`next_rule_effective_at`、`rule_checksum`。
- 不为旧行补 0、当前时间、伪 version/checksum；缺失事实保持 unavailable/unknown。
- numeric 必须 `> 0`；market size/unit 成对且 GateW OKX Spot unit 只允许 `USDT`；checksum 为 lowercase SHA-256；timestamp 顺序受 CHECK 约束。
- 保留既有 `(exchange_code, exchange_symbol)` 与 `(exchange_code, internal_symbol)` unique constraints 和 status lookup index；不新建第二表或重复索引。
- `DB_SCHEMA.md` 继续只描述当前 V33，直到 migration 实际实现并通过验证。

### 36.3 Source, freshness and ingestion

- `source=OKX_PUBLIC_INSTRUMENTS`；`source_schema_version` 是 NQ parser contract，不冒充官方 API version。
- checksum 取 official venue facts + NQ schema version + relevant planned change 的 canonical JSON SHA-256；排除 observation/write/freshness timestamps。
- `observed_at` 在完整 public response 成功获取、解析、校验后且 DB write 前记录；`synced_at` 只表示 repository write time。
- `stale-after` 使用显式配置，建议默认 600 秒、允许 60..86400 秒；配置缺失或非法时 UNKNOWN。`freshUntil=min(observedAt+staleAfter,nextRuleEffectiveAt)`；过期、conflict、checksum/version mismatch、非 LIVE 全部 fail-closed。
- 复用 public metadata 能力但提取窄 public-only port；显式 operator-triggered，server-side allowlist 仅 1..3 个 OKX Spot symbols。保存所有 allowlisted state，禁止预过滤 non-live。
- preview 不触发 refresh、不在请求线程联网；无 credential/private endpoint、scheduler/runner/startup/background sync。失败保留旧 snapshot 且不更新 observedAt，使其自然 stale。
- bounded idempotent UPSERT；相同 checksum 不产生虚假 rule-change，变化只记录 sanitized checksum transition，不保存 raw payload/header/credential。

### 36.4 Implementation gate and next action

实现必须补 V34 migration contract、fresh PostgreSQL V1..V34、V33→V34 upgrade、constraint、repository precision/idempotency/freshness/non-live/sync-failure/no-egress 测试与 full Maven regression，并进入独立 migration/schema conformance review。

Implementation 开始前的历史 snapshot 只确认 GateW-2 是最近 accepted batch；该 snapshot 已被后续 implementation commit 与 failed CI 事实取代。

唯一下一动作：

```text
NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION
```

venue-rule implementation、schema conformance review 与 exact-head CI green 后，才执行 dry-run order preview security/risk review attempt-02；不得直接进入 order preview implementation 或 GateW-4。

## 37. GateW-3 Venue-rule Facts Implementation

结论：`IMPLEMENTED / PENDING_REVIEW`（已实现 / 待独立复核）。完整 evidence 为 [NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION.attempt-01.md](evidence/gate-w/NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION.attempt-01.md)。

- V34 扩展既有 `instrument_catalog`，保持单一 current fact source；旧行不回填 venue facts，新增列保持 null。
- OKX reader 只访问固定 Public Instruments endpoint，只接受 Spot，按 server-side allowlist bounded 处理 1..3 symbols，并保存 non-live 状态。
- canonical checksum 使用固定字段顺序、UTF-8、规范化 decimal、JSON null 与 lowercase SHA-256；`observedAt/syncedAt/freshUntil` 不进入 checksum。
- `upcChg` 的完整 canonical representation 当前无法随 row 持久化，因此按 review 规则明确后置；不得只保存 effective time 后伪称 planned changes 已纳入 checksum。
- freshness 使用注入 `Clock`；source/version/checksum、配置、观察时间、状态或必要 facts 缺失/冲突时 fail-closed。
- manual profile + flag 才装配 public reader/sync service；默认/test/CI 无 reader、无 startup/background/scheduled sync。无 Controller/API、credential/private endpoint、order preview 或 LIVE。
- 相关 reactor、full Maven 与 disposable PostgreSQL V1→V34 / V33→V34 均通过；随后 migration conformance review 已接受，implementation 已提交为 `8b54adc6952775dc1a939aad7b0ae849f20f42cf`，其 exact-head CI run `29241698510` 失败。

Implementation attempt-01 结束时的待 review snapshot 已被 migration conformance acceptance、implementation commit 与 post-commit failed CI 事实取代；current authority 见 §39。

唯一下一动作：

```text
NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW
```

该 review 只审查本轮实际 migration/schema/domain/repository/parser/sync/freshness diff。order preview 仍 blocked；不得创建 Controller、实现 preview、启动 GateW-4 或升级 LIVE/交易授权状态。

## 38. GateW-3 Venue-rule Facts Migration Conformance Review

结论：`PASS / MIGRATION_CONFORMANCE_ACCEPTED / READY_TO_COMMIT`（通过 / migration 符合性已接受 / 可进入提交前复核）。完整 evidence 为 [NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW.attempt-01.md](evidence/gate-w/NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW.attempt-01.md)。

- P0=0、P1=0；review 中无实现修复。
- V34 仍以 `instrument_catalog` 为唯一 current fact source；历史行保持 null，约束、precision、comments 与真实 PostgreSQL fresh/upgrade 执行通过。
- OKX Public Instruments 映射、public-only parser、canonical checksum、bounded batch UPSERT 与 fail-closed freshness 通过静态及回归审查。
- V34 在单行、73,728-byte disposable 样本发生 table rewrite，并请求 `AccessExclusiveLock`；目标表规模/维护窗口未验证，按 P2 保留。
- 无 credential/private endpoint、Controller/API/frontend、scheduler/runner、order preview、order submission 或 LIVE；未调用真实 OKX。
- 相关 reactor 与 full Maven 均 23/23 modules `BUILD SUCCESS`；forced disposable PostgreSQL 2 tests / 0 failure/error/skip；治理与 current authority 检查通过。

Review 完成时的 pre-commit snapshot 已被 commit `8b54adc6952775dc1a939aad7b0ae849f20f42cf` 与 failed run `29241698510` 取代；它不覆盖下方 post-commit CI failure current state。

Review 完成时的下一动作（已执行）：

```text
NQ-GATEW-3-VENUE-RULE-FACTS-COMMIT-AND-PUSH
```

该提交动作已由 commit `8b54adc6952775dc1a939aad7b0ae849f20f42cf` 执行；其 exact-head CI run `29241698510` 失败。不得把 review-era pre-commit snapshot 当作 current authority。

## 39. GateW-3 Post-commit CI Failure and Blocker Fix

Current authority：GateW `IN_PROGRESS / NOT_FROZEN`；`accepted_batch=GateW-2 / ACCEPTED|CI_GREEN`；`work_batch=GateW-3 / COMMITTED|CI_FAILED|FIX_REQUIRED / 54c7bdd2caee5602441ce983b33c4cd2466ee263 / 29253811976`。

- venue-rule implementation 已提交，migration conformance review 继续有效；failed CI 不撤销二者，也不表示代码已回滚。
- latest committed exact-head run `29253811976` 为 `completed / failure`：两个通用 Flyway helper 在成功迁移/校验到 V34 后固定比较 V33；Batch 5A 的 `npx playwright install --with-deps chromium` 在下载 Ubuntu 字体依赖期间达到 15 分钟 job 上限并被取消。该 run 的 Diff check 已通过，旧 run `29241698510` 的 EOF finding 未再出现。
- 本轮已将两个 helper 改为 `current version != null + pending migrations=0` 的动态合同，并将 Batch 5A job/install step timeout 分别设为 60/30 分钟；独立 review 已重跑 embedded Java、disposable PostgreSQL、frontend build/4-spec E2E、full Maven 与治理回归并接受该 fix。Ubuntu apt/mirror timeout 仍必须由 fix commit exact-head GitHub CI 最终证明。
- current next action 仅为 `NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH`。fix 仍未 commit/push；形成新 commit 后回到 `COMMITTED|CI_PENDING`，再由 fix commit exact-head CI 决定接受。
- 禁止从 failed 直接写成 `ACCEPTED|CI_GREEN`，禁止初始化 GateW-4、GateW Freeze 或 order preview attempt-02，禁止升级 LIVE/交易授权状态。

## 40. GateW-3 Post-fix CI Green Continuation

Canonical reconciliation 已验证以下 transition：

```text
COMMITTED|CI_FAILED|FIX_REQUIRED
→ COMMITTED|CI_GREEN|CONTINUE_REQUIRED
```

- failed snapshot 为 commit `54c7bdd2caee5602441ce983b33c4cd2466ee263` / run `29253811976` / `failure`。
- fix snapshot 为 commit `fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28` / run `29260881801` / `completed / success`，且 `headSha` exact match。
- governance enablement commit `ea58c34e44169e1a459750a0265017c622eea9b6` / run `29271620336` 为 `completed / success`；该 commit 只建立治理合同，不是 preview 或 venue-rule implementation commit。
- `accepted_batch` 继续为 GateW-2；GateW 继续 `IN_PROGRESS / NOT_FROZEN`；GateW-4 未初始化；LIVE 与 private trading 继续关闭。
- reconciliation 后的下一动作是 `NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02`，本轮已执行并通过；current authority 由下方 §41 覆盖。

## 41. GateW-3 LIMIT-only Dry-run Order Preview

Security/risk review attempt-02 结论：`PASS / LIMIT_ONLY_INTERNAL_PREVIEW_REVIEW_ACCEPTED`。完整 evidence 为 [NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-02.md](evidence/gate-w/NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-02.md)。

Implementation 完成并进入 `IMPLEMENTED|PENDING_REVIEW`；完整 evidence 为 [NQ-GATEW-3-LIMIT-ONLY-DRY-RUN-ORDER-PREVIEW-IMPLEMENTATION.attempt-01.md](evidence/gate-w/NQ-GATEW-3-LIMIT-ONLY-DRY-RUN-ORDER-PREVIEW-IMPLEMENTATION.attempt-01.md)。独立 review 重新检查真实 diff 后结论为 `PASS / LIMIT_ONLY_INTERNAL_ORDER_PREVIEW_ACCEPTED / READY_TO_COMMIT`；完整 evidence 为 [NQ-GATEW-3-LIMIT-ONLY-DRY-RUN-ORDER-PREVIEW-REVIEW.attempt-01.md](evidence/gate-w/NQ-GATEW-3-LIMIT-ONLY-DRY-RUN-ORDER-PREVIEW-REVIEW.attempt-01.md)。

- 实现只位于 `nq-core`，通过窄化 `InstrumentCatalogReadPort` 读取一个本地 symbol；无 network/provider/credential/account/risk/order write dependency。
- 输入只允许 OKX Spot、BUY/SELL、LIMIT、BigDecimal price/quantity 和显式 evaluation time；不静默舍入。
- 输出分离 structural/venue/risk/account/execution；minimum notional、fee、permission 为 UNKNOWN，balance/stateful risk 为 NOT_EVALUATED，execution 永久 BLOCKED。
- 37 个 preview tests 与 8 个 freshness tests 定向通过；P0=0、P1=0。GateW-3 未 accepted，GateW 未 frozen，LIVE/private trading 未开启。

唯一下一动作：

```text
NQ-GATEW-3-LIMIT-ONLY-DRY-RUN-ORDER-PREVIEW-COMMIT-AND-PUSH
```

只允许精确暂存本轮已接受的 `nq-core` 与 current docs/evidence diff。提交后必须等待 implementation commit exact-head CI，CI GREEN 前不得进入 post-CI authority sync。

## 42. GateW-3 Preview Implementation Exact-head CI Failure Catch-up

Preview implementation review 继续有效，implementation 已提交为 `eff79d7c7ea1b034de4e77c7ec64974c247027f5`。其 `NQ CI Baseline` exact-head run `29308652349` 为 `completed / failure`：实际 10 jobs 中 9 success、1 failure，唯一失败为 `Frontend backend E2E smoke / Run adapter readiness backend E2E`。

失败日志证明 runner 固定等待 `http://127.0.0.1:51888`，而该端口被占用后 Vite 自动切换到 `http://127.0.0.1:51889`；runner 未跟随实际 Vite endpoint，120 秒后超时。该失败不撤销 preview implementation review，不表示 preview business implementation 需要回滚，也不得通过 rerun 旧 job 将偶然通过写成修复。

Authority catch-up 使用既有 high-risk reconciliation contract：

```text
REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_FAILED|FIX_REQUIRED
```

- `authorityCatchUp=true`；`work_batch_commit` 从 `UNCOMMITTED` 追赶到 `eff79d7c7ea1b034de4e77c7ec64974c247027f5`，`work_batch_ci_run` 从 `NOT_RUN` 追赶到 `29308652349`。
- `accepted_batch` 继续为 GateW-2 / `ACCEPTED|CI_GREEN`；GateW 继续 `IN_PROGRESS|NOT_FROZEN`；GateW-3 尚未 accepted。
- 本修复只允许 frontend E2E runner tooling、确定性回归测试与 current evidence；preview implementation、E2E spec、Vite/Playwright config、package-lock、backend、workflow 与业务前端保持无 diff。

当前唯一下一动作：

```text
NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW
```

## 43. GateW-3 Preview CI Blocker Fix Review

Frontend E2E runner fix attempt-02 已完成，独立 conformance review 结论为 `PASS / CI_BLOCKER_FIX_ACCEPTED / READY_TO_COMMIT`。完整 evidence：

- [NQ-GATEW-3-CI-BLOCKER-FIX.attempt-02.md](evidence/gate-w/NQ-GATEW-3-CI-BLOCKER-FIX.attempt-02.md)
- [NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW.attempt-02.md](evidence/gate-w/NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW.attempt-02.md)

修复使用动态 loopback port、single endpoint、Vite `--strictPort`、Vite early-exit fail-fast 与有界 cleanup；runner unit tests、占用 51888 的真实 local backend E2E、targeted/full Maven 与治理检查均通过。`package-lock.json`、Vite/Playwright config、backend E2E spec、preview implementation 与其他 forbidden scope 无 diff。

Authority 在 Commit A 前继续保持 `COMMITTED|CI_FAILED|FIX_REQUIRED / eff79d7c7ea1b034de4e77c7ec64974c247027f5 / 29308652349`；不得提前写 green continuation。

当前唯一下一动作：

```text
NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH
```

## 44. GateW-3 Preview Post-fix CI Green Reconciliation

Commit A `abc5230c21ad37b3d01bc7df2cc825579bd3f7dc` 的 `NQ CI Baseline` exact-head run `29319269424` 已 `completed / success`：10 个实际 jobs 全部成功，`Frontend backend E2E smoke / Run adapter readiness backend E2E` 为 success。该 run 是 preview acceptance head 的 CI 证据，不是对旧 failed run 的 rerun。

治理 reconciliation 使用既有 contract：

```text
COMMITTED|CI_FAILED|FIX_REQUIRED
→ COMMITTED|CI_GREEN|CONTINUE_REQUIRED
```

- `mode=POST_FIX_CI_SUCCESS_RECONCILIATION`；`authorityCatchUp=true`；`exactHeadMatch=true`；`ciConclusion=success`。
- preview implementation commit 保持为 `eff79d7c7ea1b034de4e77c7ec64974c247027f5`，其 failed run 保持为 `29308652349`。
- `work_batch_commit` 指向 acceptance head `abc5230c21ad37b3d01bc7df2cc825579bd3f7dc`，`work_batch_ci_run` 指向成功 run `29319269424`；后续 docs-only authority sync commit 不替换该 acceptance head。
- `accepted_batch` 继续为 GateW-2 / `ACCEPTED|CI_GREEN`；GateW 继续 `IN_PROGRESS|NOT_FROZEN`；GateW-3 尚未整体 accepted。

当前唯一下一动作：

```text
NQ-GATEW-3-READ-ONLY-RECONCILIATION-SECURITY-RISK-REVIEW-ATTEMPT-01
```

## 45. GateW-3 Diagnostic Risk Preflight

Security/risk review attempt-01 已冻结 `PASS / GATEW_3_RISK_PREFLIGHT_REVIEW_ACCEPTED`：完整 `PreTradeRiskService`/registry 因 `PlaceOrderCommand` coupling、stateful rule 与 `ALLOW` 语义被拒绝；只允许组合 immutable preview/reconciliation result 与 credential-material-free local metadata snapshots。

Implementation 只在 `nq-core` 新增 internal evaluator；无 Spring 自动装配、Controller/API、DB/network/credential/write dependency。结果固定 `diagnosticOnly/readOnly/noSideEffect=true`、`orderSubmitted/tradingAuthorized=false`、`executionReadiness=BLOCKED`；minimum notional、fee、remote permission 为 UNKNOWN，stateful risk/balance/position 等为 NOT_EVALUATED。

独立 review 结论为 `PASS / GATEW_3_RISK_PREFLIGHT_ACCEPTED / READY_TO_COMMIT`；focused 31/31、required targeted 与 full Maven 均 23/23 modules SUCCESS。当前尚未 commit/push 或取得 implementation exact-head CI，accepted batch 仍为 GateW-2，GateW-3 尚未整体 accepted。

当前唯一下一动作：

```text
NQ-GATEW-3-RISK-PREFLIGHT-COMMIT-AND-PUSH
```

## 46. GateW-3 Risk Preflight Post-CI and Batch Acceptance

Risk preflight implementation/acceptance head `178b4951ba1406748170022c9940f84beaa8ab81` 的 `NQ CI Baseline` run `29332316101` 已 `completed / success`，10/10 actual jobs success、bad jobs=0。venue-rule facts、preview、reconciliation 与 risk preflight 四个 acceptance heads 均重新核验 exact-head CI green，所有冻结 review 为 P0=0/P1=0。

GateW-3 已整体 `ACCEPTED / CI GREEN`；authority 将 `accepted_batch` 投影为 GateW-3，并将 `work_batch` 初始化为 `GateW-4 / NOT_STARTED / NONE / NOT_RUN`。该 transition 没有持久化虚假的 `CI_PENDING` snapshot；`accepted_batch_acceptance_head` 保持指向 risk preflight implementation commit，不改指 docs-only authority sync commit。

GateW 继续 `IN_PROGRESS / NOT_FROZEN`；LIVE/Shadow/AI/DH/Integration/real provider/private trading 状态不变。GateW-4 尚未实现，其唯一下一 task `NQ-GATEW-4-IMPLEMENTATION` 必须先在内部通过 security、operations、persistence/retention、backup/restore、incident-drill 与 soak design review hard gates。
