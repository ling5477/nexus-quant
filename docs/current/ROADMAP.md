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
GateY-4 NOT STARTED
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
- GateY-4：`NOT STARTED`（未开始）；下一批次只建立 scoped credential reference/capability、最小权限 policy、JIT material access boundary、OKX private read-only probe、endpoint/IP allowlist readiness、kill propagation、worker deployment identity、immutable release/process boundary、no-real/default-disabled profile、显式人工 read-only smoke 与审计证据。
- 当前唯一治理动作是 `NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-IMPLEMENTATION`；精确状态和安全边界服从 [STATUS.md](STATUS.md)。

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
- `LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE=CLOSED` 已由 GateY-3 独立 review 的真实 PostgreSQL 正反测试确认；`PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 与 `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED` 继续保留并阻断 production deployment、worker 与 first real order。真实 OKX/Binance HTTP、private endpoint、credential、真实 PLACE/CANCEL、production worker deployment、LIVE、transfer、withdraw 与真实资金均禁止。
- GateY-4 中 credential material ownership 不得进入 domain；control plane 只持 credential reference/capability，JIT decrypt 只能发生在受控 adapter/worker 边界；private provider DTO 不得泄漏 core domain，kill-switch owner 保持唯一，deployment tooling 不承载交易业务规则，read-only probe 不得解释为 trading authorization，fake provider 不得成为真实 provider fallback。若新增 module dependency，必须检查 ArchUnit。
- GateY-4 仍禁止 PLACE、CANCEL、transfer、withdraw、真实资金 mutation、LIVE activation、production worker 自动启动与真实 micro-live。真实 read-only smoke 必须为显式人工模式；缺少 OKX credential 时只允许 `BLOCKED / API_KEY_REQUIRED`，用户应在 NQ 本地安全 credential 管理路径配置，不得在聊天中粘贴明文 API Key、Secret 或 Passphrase。
- 不得把 GateW diagnostic/read-only/soak 证据推导为远端交易 permission、账户健康、余额充分、private trading 或 unattended execution readiness。
- 不得开启 LIVE、真实下单/撤单、转账/提现、AI trading、DH runtime、Integration runtime、RealClient 或 real provider。
- NQ-only 任务不得修改或声明 DH current authority；DH/Integration 状态继续只表达 NQ 侧 no-real 边界。
