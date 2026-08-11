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
GateX-5 IMPLEMENTED / PENDING REVIEW
```

## 下一允许动作

- GateW：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；durable archive 为 [../gates/gate-w/README.md](../gates/gate-w/README.md)，release tag 为 `nq-gatew-freeze`。
- GateW freeze commit=`16376de28be78eea58afbe1374847ee07ca2ccc7`；exact-head CI run=`31299729114 / completed / success / 10 jobs / bad=0`。
- GateW Attempt-13=`COMPLETED / ACCEPTED`（已完成 / 已接受）；production deployment=`STOPPED`；production soak=`COMPLETED`。656 条样本与 hash chain 已接受并 sealed，worker 已停止。
- GateW acceptance batch：`GateW-ATTEMPT-13-168H-ACCEPTANCE / ACCEPTED / CI GREEN`；acceptance head=`20cf7970dfb414868da3e42dddaefc5965246570`，CI run=`31295184056`。
- GateX：`IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）；GateX-0A/0B/0C/0D/1/2/3/4/4C 已接受，GateX-0E 条件项已审计且无需实施；GateX-5 受控 Shadow materialization 已实现并待独立审查。
- GateX-PLAN：`BASELINE ESTABLISHED / READY TO COMMIT`（基线已建立 / 可进入提交前复核）；实施基线见 [GATEX_PLAN.md](GATEX_PLAN.md)。
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
- GateX-5：`IMPLEMENTED / PENDING REVIEW`（已实现 / 待独立审查）；受控 POST 仅从 `publishRecordId` 重评估 admission，并原子、幂等地创建 `CREATED / RELEASE_BOUND` Shadow Run。真实一次性 PostgreSQL 已覆盖单次、重放、并发、provenance conflict 与审计失败回滚；runner/scheduler/交易/外部网络调用均为 0。
- 当前唯一治理动作是 `NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW`；只允许独立审查当前 staged scope，不得启动 Shadow Run、开启 LIVE 或触达交易写侧。

## GateW 已冻结边界

- 168h OKX read-only soak 已完成并接受，但 `read-only soak ≠ 真实资金交易授权`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`；order/cancel/transfer/withdraw/LIVE execution=`0/0/0/0/0`。
- P2 known limitation 保留：maximum gap=`1797s / sequence 1→2`。
- Attempt-09 的拒绝、Attempt-10/11/12 的失败终态与全部 remediation evidence 均已进入 GateW archive，不得删除、覆盖或复用。
- `nq-gatew-freeze` 不得删除、移动、覆盖或 force update；问题只能通过 forward remediation 或 superseding tag 处理。

## GateX implementation 边界

- 唯一实施顺序为 0A → 0B → 0C → 0D → 条件性 0E；GateX-0 总量控制在 3～5 个代码任务、3～7 工程日。
- GateX-0 只处理 P1 与 GateX 触达区域的低风险 P2；不得演化为 Maven 全量拆分或全仓架构重构。
- GateX-1～5 与 FREEZE 的前置、范围和验收以 [GATEX_PLAN.md](GATEX_PLAN.md) 为准；计划不得被解释为 capability 已实现。
- 不得把 GateW diagnostic/read-only/soak 证据推导为远端交易 permission、账户健康、余额充分、private trading 或 unattended execution readiness。
- 不得开启 LIVE、真实下单/撤单、转账/提现、AI trading、DH runtime、Integration runtime、RealClient 或 real provider。
- NQ-only 任务不得修改或声明 DH current authority；DH/Integration 状态继续只表达 NQ 侧 no-real 边界。
