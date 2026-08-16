# Roadmap

本文件只定义下一允许动作和路线。当前 Gate、release tag 与安全状态必须读取 [STATUS.md](STATUS.md) 的 `nq-current-authority` 机器可读区块。

## 当前路线

```text
GateV FROZEN / ACCEPTED / TAGGED
  ↓
GateW FROZEN / ACCEPTED / TAGGED
  ↓
GateW-ATTEMPT-13-168H-ACCEPTANCE ACCEPTED / CI GREEN
  ↓
GateW strict archive + freeze exact-head CI + annotated tag VERIFIED
  ↓
GateX IN PROGRESS / NOT FROZEN
  ↓
GateX-PLAN BASELINE ESTABLISHED / READY TO COMMIT
  ↓
GateX-0A ACCEPTED / CI GREEN
  ↓
GateX-0B ACCEPTED / CI GREEN
  ↓
GateX-0C ACCEPTED / CI GREEN
  ↓
GateX-0D ACCEPTED / CI GREEN
  ↓
GateX-0E AUDITED / IMPLEMENTATION NOT REQUIRED
  ↓
GateX-1 ACCEPTED / CI GREEN
  ↓
GateX-2 ACCEPTED / CI GREEN
  ↓
GateX-3 ACCEPTED / CI GREEN
  ↓
GateX-4 ACCEPTED / CI GREEN
  ↓
GateX-4A SCHEMA/SECURITY REVIEW PASS / DESIGN BLOCKER RESOLVED
  ↓
GateX-4B ACCEPTED / CI GREEN
  ↓
GateX-4C ACCEPTED / CI GREEN
  ↓
GateX-5 ACCEPTED / CI GREEN
  ↓
GateX FROZEN / ACCEPTED / TAGGED
  ↓
GateX strict archive + freeze exact-head CI + annotated tag VERIFIED
  ↓
GateY IN PROGRESS / NOT FROZEN
  ↓
GateY-PLAN ACCEPTED / CI GREEN
  ↓
GateY-1 ACCEPTED / CI GREEN
  ↓
GateY-2 ACCEPTED / CI GREEN
  ↓
GateY-3 ACCEPTED / CI GREEN
  ↓
GateY-4 ACCEPTED / CI GREEN
  ↓
GateY-5 ACCEPTED / CI GREEN
  ↓
GateY-6B ACCEPTED / CI GREEN / CONTRACT ONLY
  ↓
GateY-6C ACCEPTED / CI GREEN
  ↓
GateY-6D ACCEPTED / CI GREEN
  ↓
GateY-6E REVIEW ACCEPTED / READY TO COMMIT
```

## 下一允许动作

- GateW：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；durable archive 为 [../gates/gate-w/README.md](../gates/gate-w/README.md)，release tag 为 `nq-gatew-freeze`。
- GateW freeze commit=`16376de28be78eea58afbe1374847ee07ca2ccc7`；exact-head CI run=`31299729114 / completed / success / 10 jobs / bad=0`。
- GateW Attempt-13=`COMPLETED / ACCEPTED`（已完成 / 已接受）；production deployment=`STOPPED`；production soak=`COMPLETED`。656 条样本与 hash chain 已接受并 sealed，worker 已停止。
- GateW acceptance batch：`GateW-ATTEMPT-13-168H-ACCEPTANCE / ACCEPTED / CI GREEN`；acceptance head=`20cf7970dfb414868da3e42dddaefc5965246570`，CI run=`31295184056`。
- GateX：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；durable archive 为 [../gates/gate-x/README.md](../gates/gate-x/README.md)，release tag=`nq-gatex-freeze`，freeze commit=`299ab30bd2e243314be2dc609cb244cd5388027b`。
- GateX freeze exact-head CI：run `31565353974 / completed / success / 10 jobs / bad=0`；archive/release post-tag verification 均 errors=0。
- GateX-PLAN：`BASELINE ARCHIVED`（基线已归档）；实施基线见 [GATEX_PLAN.md](../gates/gate-x/GATEX_PLAN.md)。
- GateX-0A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；Strategy↔Trading 与 audit port ownership P1 已关闭，merge acceptance head 的 exact-head CI 已成功。
- GateX-0B：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head=`108a14d14906d6fa354349c66d35a2ae6967cebf`，exact-head CI run=`31321821962 / completed / success`。
- GateX-0C：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head=`46392213495652f6a09005148cc160fd2882adb9`，exact-head CI run=`31325824949 / completed / success`。
- GateX-0D：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`885ed23375d0d8a58d9d10d2c4768f390322af93`，exact-head CI run=`31344357225 / completed / success / 10 jobs`。
- GateX-0E：`AUDITED / IMPLEMENTATION NOT REQUIRED`（已审计 / 无需实施）；未发现 GateX-1 Query/cache/config blocker，条件项不进入 machine lifecycle，未新增 skip 状态。
- GateX-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`2655f5144ba27cc88c2786de7f76633df3df462d`，exact-head CI run=`31358676688 / completed / success`。production capability 只覆盖 Strategy Release aggregate、artifact verifier、只读 provenance service/JDBC adapter，不包含 migration、persistence write 或 admission。
- GateX-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`894e76bf69dbcf1574be6c993f18ca7913033564`，exact-head CI run=`31379536899 / completed / success`；保留 Flyway 单事务锁持有的部署 P2。
- GateX-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`5f4824eecaac5cffbbc314fb8f767bd6ba45c29f`，exact-head CI run=`31391541813 / completed / success`。production capability 只覆盖 fail-closed Release-to-Shadow admission 纯决策与不可变 `ShadowRunCreationPlan`，不创建 Shadow Run，不引入持久化、外部 IO 或交易副作用。
- GateX-4：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`7aaf6027644b2ba6cd7dc588536784be50ff1eff`，exact-head CI run=`31467397459 / completed / success / 10 jobs / bad=0`。`publishRecordId` 驱动的只读 admission preview API/UI 未创建或启动 Shadow Run，未产生交易写侧副作用。
- GateX-4A：`PASS / DESIGN BLOCKER RESOLVED`（通过 / 设计阻断已解决）；方案 A 固定在 `backtest_publish_records` 增加 nullable pair `artifact_storage_key` / `manifest_storage_key`，使用受限 opaque key、绑定后不可变、trusted root 只来自服务端配置且 `NO FAKE BACKFILL`。4B 已按当前 provider contract 重新判断并未增加缺乏正式 invariant 的 partial UNIQUE。
- GateX-4B：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`92043c37dad96d984d5e55a1e5170c97d335d6d4`，exact-head CI run=`31403529376 / completed / success`。Producer 仍为 `PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED`。
- GateX-4C：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`b4e5406fbb9de5432f79f9ef8ef76c95002e0e56`，exact-head CI run=`31409595743 / completed / success / 10 jobs / bad=0`；duplicate manifest identity key P1 与服务器受控 resolver trust boundary 已关闭。
- GateX-5：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；forward-remediation commit=`3336bd8153845d5368a0d65a9c72d3566dc9bd35`，acceptance head=`a383be750f51d063d429bc25fad80e60dffb7014`，exact-head CI run=`31512467501 / completed / success / 10 jobs / bad=0`。最终独立审查的 P0=0、产品 P1=0 与 `ADMISSION_MATERIALIZATION_FACT_TEAR=CLOSED` 保持成立；runner/scheduler/交易/外部网络调用均为 0。
- GateX-FREEZE：`ACCEPTED / CI GREEN / TAGGED`（已接受 / CI 已通过 / 已打 tag）；annotated tag object=`ef4deb25728601719d20b2c6c64af7905c73a92e`，peeled target 与 freeze commit 精确一致。
- GateY：`IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）；GateY-PLAN=`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。原计划 commit=`d86cea72485280f71001b87075deb3d2a0906fec`，forward remediation/acceptance head=`d7dcffad80cc4dc5089307bfa0e2a5439f37815c`，exact-head CI run=`31568447799 / completed / success / 10 jobs / bad=0`，基线见 [GATEY_PLAN.md](GATEY_PLAN.md)。
- GateY-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`76ef325f7b8a3d3325df63af2cb1b979309bd141`，exact-head CI run=`31581317959 / completed / success / 10 jobs / bad=0`。接受范围是候选数据模型、状态机、事务、幂等、并发、约束与 migration 验证合同，不表示 migration/runtime 已实现。
- GateY-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`19ac2d1cdc7a1982f97fb0e1b0e62c081d003018`，exact-head CI run=`31608725854 / completed / success / 10 jobs / bad=0`。接受范围为 V39 local schema、六张 control-plane fact 表、`LiveSession`/approval/risk domain、Repository/JDBC、PostgreSQL enforcement、事务/并发与 architecture hygiene baseline；不授权 production migration、worker、真实 exchange execution 或 micro-live。
- GateY-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`1f2ad2324166872a567a0420b71a8b4a5b68f7f1`，exact-head CI run=`31622259352 / completed / success / 10 jobs / bad=0`。`NO BLIND RETRY`、PostgreSQL concurrency、fake-provider isolation 与 `LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE=CLOSED` 均有独立 review 与回归证据；接受不包含真实 provider、credential、外联、production worker 或 LIVE。
- GateY-4：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；canonical implementation commit=`44ac9b3c014bcd7a46499c4180053742e64c7709`，final acceptance evidence head=`b3a6b1fd550d8ccb5132c7b16942a4b11b67f78e`，exact-head CI run=`31679311259 / completed / success / 10 jobs / bad=0`。44-path/44-blob addendum 与 ancestry reconciliation 已通过；Candidate B 因缺少 18 个 reviewed paths 被 supersede。Linux stable-handle closure 只适用于 supported Linux runtime；真实 smoke=`NOT_RUN / API_KEY_REQUIRED`，remote permission/IP allowlist 未验证。
- GateY-5：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit=`8d594f1a0000678e4817f3ec80de19ac975da992`，failed implementation CI=`31727172181 / failure`，失败归类为 `FALSE_POSITIVE_NON_SECRET_HASH_EVIDENCE` 并保留；forward-only remediation/acceptance head=`88f6f7f25a81f55fe17984df335546ad2033c61f`，exact-head CI run=`31761584826 / completed / success / bad=0`。Remediation 只修改 1 个 review evidence 文件，产品代码、CI workflow、allowlist 变更均为 0。
- GateY-6B：`ACCEPTED / CI GREEN / CONTRACT ONLY`（已接受 / CI 已通过 / 仅合同能力）；implementation/acceptance head=`990f8c5680c23d02dec059ca72e7355f88faa72e`，exact-head CI run=`31811302301 / completed / success / 10 jobs / bad=0`。接受范围不包含 production transport、credential wiring、real signing/HTTP、worker/runtime binding、private trading、pilot、`FIRST_REAL_ORDER`、micro-live 或 LIVE。
- GateY-6C：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit=`febf30adfbd2ac1d1c017b1185ed75fb30abd851`，failed feature CI=`31892305007 / failure / EOF_WHITESPACE_ONLY`，forward remediation/acceptance head=`696963a75d6a701a215bf0eb7ff94d4bed97d43f`，exact-head CI=`31893000098 / completed / success`。受控固定出口已完成唯一一次 `GET /api/v5/account/config`，retry=`0`，READ/TRADE=`VERIFIED / VERIFIED`、WITHDRAW=`ABSENT`、IP=`MATCHED`，exchange mutation 与 PLACE/CANCEL/TRANSFER/WITHDRAW 均为 0。Security Review P0/P1=`0/0`；管理密码事件在 defined containment scope 内已关闭；`TARGET_PERSISTED_FACTS_REQUERY_UNAVAILABLE` 保留为非阻断 P2 accepted residual。
- GateY-6D：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/review commit=`b56e68bdc45fd6a7f27e6e830447e995ff683bfb`，exact-head CI run=`31944962448 / completed / success`，Security Review P0/P1=`0/0`。接受仅覆盖 trusted-observation boundary、production fail-closed、forged-refresh denial、authorization regression 与 materialization control-plane capability；`EXACT_PILOT_SCOPE=NOT_MATERIALIZED`、`FIRST_REAL_ORDER`/micro-live=`NOT_AUTHORIZED`。
- GateY-6E：`REVIEW ACCEPTED / READY TO COMMIT`（审查已接受 / 可进入提交前复核）；production trusted prerequisite observation 与 typed OKX Spot real-provider transport capability 已通过独立安全审查，P0/P1=`0/0`。machine `real_provider/private_trading` 仍保持 `NOT_IMPLEMENTED`，表示真实 runtime 尚未接受或启用；real mutation runtime 仍为 `UNBOUND`。exact operator-controlled pilot inputs、exact PilotScope materialization、final fail-closed preflight 与 explicit first-order authorization 均未完成或授权。
- 当前唯一治理动作是 `NQ-GATEY-6E-COMMIT-AND-PUSH`；本动作只允许提交已接受 capability 并等待 exact-head CI，不授权真实 PLACE，也不允许把 capability acceptance 写成 accepted runtime 或真实 pilot readiness。
- GateY 保持 `IN PROGRESS / NOT FROZEN`；GateY-6D acceptance 不等于 GateY 或 GateY-6 overall accepted/frozen，也不授权 GateY-FREEZE。30 项 hard gates 仍为 `PASS=0 / NOT_MET=25 / NOT_VERIFIABLE=5`，gap candidates=`10`；real provider/private trading=`NOT_IMPLEMENTED`、`FIRST_REAL_ORDER`/micro-live=`NOT_AUTHORIZED`、LIVE=`DISABLED`、kill=`ENGAGED`。

## GateW 已冻结边界

- 168h OKX read-only soak 已完成并接受，但 `read-only soak ≠ 真实资金交易授权`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`；order/cancel/transfer/withdraw/LIVE execution=`0/0/0/0/0`。
- P2 known limitation 保留：maximum gap=`1797s / sequence 1→2`。
- Attempt-09 的拒绝、Attempt-10/11/12 的失败终态与全部 remediation evidence 均已进入 GateW archive，不得删除、覆盖或复用。
- `nq-gatew-freeze` 不得删除、移动、覆盖或 force update；问题只能通过 forward remediation 或 superseding tag 处理。

## GateX frozen / GateY implementation 边界

- 唯一实施顺序为 0A → 0B → 0C → 0D → 条件性 0E；GateX-0 总量控制在 3～5 个代码任务、3～7 工程日。
- GateX-0 只处理 P1 与 GateX 触达区域的低风险 P2；不得演化为 Maven 全量拆分或全仓架构重构。
- GateX-1～5 与 FREEZE 的历史前置、范围和验收以已归档的 [GATEX_PLAN.md](../gates/gate-x/GATEX_PLAN.md) 为准；计划不得被解释为额外 capability 或 runtime 授权。
- `nq-gatex-freeze` 不得删除、移动、覆盖或 force update；问题只能通过 forward remediation 或 superseding tag 处理。
- GateY plan 已完成事实核对和 security/architecture/database/operations self-review，并由 forward-fix exact-head green CI 接受；历史失败 CI 必须保留，但不再作为 current work batch。
- GateY-1 work order 已通过独立 migration/security review 与 exact-head CI 接受；六表最小集合、事实所有权、约束/索引、append-only、锁窗口、stable-handle、安全与 forward remediation 合同已冻结。GateY-2 使用 `V39__gate_y2_live_session_fact_model.sql`，未修改 V1～V38；独立 review 已接受 local/disposable baseline，但不构成生产部署、micro-LIVE 或真实交易授权。
- GateY-3 中 `ExecutionIntent` owner 不得成为第二 `orders` 主事实，`ExecutionReceipt` 不得成为 `fills`/`trades` 主事实；exchange port 由 control-plane/application 拥有，fake adapter 位于 adapter/infra 边界，provider DTO 不得泄漏到 domain，worker orchestration 不得进入 JDBC，reconciliation 必须复用 `orders`/`trades`/`positions` 事实；新增跨模块依赖时检查 ArchUnit。
- `LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE=CLOSED` 已由 GateY-3 独立 review 确认；GateY-4 将 `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED` 在 Linux + `SecureDirectoryStream` supported runtime 下处置为 `SUPPORTED_RUNTIME_CLOSED`，Windows/其他 filesystem 不获 production authorization。GateY-5 独立 review 将 lock-window blocker 处置为 `CLOSED_FOR_REVIEWED_SYNTHETIC_DISPOSABLE_GATEY_SCALE`；这不是 production SLA，真实 PLACE/CANCEL、production worker start、LIVE、transfer、withdraw 与真实资金继续禁止。
- GateY-4 中 credential material ownership 不得进入 domain；control plane 只持 credential reference/capability，JIT decrypt 只能发生在受控 adapter/worker 边界；private provider DTO 不得泄漏 core domain，kill-switch owner 保持唯一，deployment tooling 不承载交易业务规则，read-only probe 不得解释为 trading authorization，fake provider 不得成为真实 provider fallback。若新增 module dependency，必须检查 ArchUnit。
- GateY-4 仍禁止 PLACE、CANCEL、transfer、withdraw、真实资金 mutation、LIVE activation、production worker 自动启动与真实 micro-live。真实 read-only smoke 必须为显式人工模式；缺少 OKX credential 时只允许 `BLOCKED / API_KEY_REQUIRED`，用户应在 NQ 本地安全 credential 管理路径配置，不得在聊天中粘贴明文 API Key、Secret 或 Passphrase。
- GateY-5 只允许 isolated worker fake-only dry-run、intent/receipt durable boundary、restart/replay、UNKNOWN reconciliation、NO BLIND RETRY、kill propagation、heartbeat/resource limits、immutable rollback、disposable backup/restore、incident drill、最小 approval/risk visibility 与 production-like V39 lock-window measurement；继续禁止 real OKX mutation、real PLACE/CANCEL、real credential requirement、production worker/deployment、micro-live 与 LIVE。
- GateY-5 architecture hygiene：worker 不拥有 strategy admission、risk-rule authoring、session authorization 或 credential lifecycle；PostgreSQL intent/receipt 保持唯一 durable execution boundary；orders/trades/positions/ledger owner 不变；fake provider 不得成为 real fallback；dashboard 只展示事实；restart/deployment tooling 不承载业务决策；新增跨 module dependency 必须检查 ArchUnit。禁止 microservice rewrite 或 second execution ledger。
- GateY-6 frozen candidate scope：仅 OKX Spot、单 venue、单 pilot account、单 owner、单 strategy release、单 execution window、1～2 个高流动性现货 symbol、LIMIT-only、micro capital；明确禁止 Binance fallback、第二 venue、cross-venue routing、market order、margin、leverage、futures、options、borrow、transfer、withdraw、funding API、AI/LLM execution、DH runtime execution 与 unattended execution。
- GateY-6 explicit authorization 必须绑定 exact immutable pilot scope：`sessionId`、OKX account reference、strategy release digest、risk-limit-set digest、credential reference、symbol allowlist、capital cap、single-order notional cap、daily-loss cap、execution window、approval expiry 与 scope hash。任一字段变化均使 authorization 失效并回到 `APPROVAL_PENDING`；历史口头授权、GateY-6 初始化或 CI green 均不能替代 exact pilot 的新授权。
- GateY-6 candidate soak 为 120h、manual start、continuous reconciliation、kill available。发现 credential permission drift、withdraw/transfer permission、IP allowlist mismatch、kill inconsistency、unresolved unknown order、reconciliation blocked、ledger/position divergence、release/worker mismatch、risk violation、unexpected endpoint、external fallback 或 secret leakage 时，pilot 必须 terminal/frozen，禁止 auto restart。
- 不得把 GateW diagnostic/read-only/soak 证据推导为远端交易 permission、账户健康、余额充分、private trading 或 unattended execution readiness。
- 不得开启 LIVE、真实下单/撤单、转账/提现、AI trading、DH runtime、Integration runtime、RealClient 或 real provider。
- NQ-only 任务不得修改或声明 DH current authority；DH/Integration 状态继续只表达 NQ 侧 no-real 边界。
