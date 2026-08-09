# GateX Implementation Plan

> 状态：`BASELINE ESTABLISHED / IMPLEMENTATION NOT STARTED`（基线已建立 / 实现尚未开始）
> 任务：`NQ-GATEX-PLAN-IMPLEMENTATION-RETRY-1`
> 范围：NQ-only
> 唯一下一动作：`NQ-GATEX-0A-ARCHITECTURE-BOUNDARY-GUARDRAILS-IMPLEMENTATION`

## 1. Current state

- GateW 已 `FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 与 release tag 仅是历史证据，不授权 GateX runtime。
- GateX 为 `IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）。本计划建立 implementation baseline，不表示 production capability 已落地。
- 写计划前 `HEAD=origin/dev=a373bf5944eb9b6e43a0fd31b3f3fdd3661bfe83`；exact-head `NQ CI Baseline` run `31314198260` 为 `completed / success`（已完成 / 成功）。
- Retry-1 与上轮审计使用同一 HEAD，production code delta 为 0，因此复用已验证 planning audit，不扩大仓库扫描。
- Pre-GateX trusted-root verifier、Strategy Release aggregate、publish anchor/digest contract 与 Release-to-Shadow admission 仍是 preparation prototype 或 test-only evidence，不是 production domain、API 或 persistence。
- LIVE=`DISABLED`（关闭），Shadow trading=`NOT ENABLED`（未启用），AI=`NOT_STARTED`（未开始），DH runtime=`NOT_INTEGRATED`（未集成）。

## 2. Goal

GateX 的目标链路为：

```text
Research Artifact
  → Strategy Release
  → Verified Release Binding
  → Release-to-Shadow Admission
```

本 Gate 必须：

1. 先关闭会影响 GateX 的 P1 架构边界问题和直接相关的低风险 P2，再进入 Strategy Release productionization。
2. 将 test-only prototype 提升为正式 production domain，保持 identity、provenance、verification 与 admission 规则单一且可审计。
3. 建立可信 artifact provenance，校验 trusted root、manifest/schema、digest、路径和资源上限。
4. 建立 Strategy Release 与 Shadow Run 的稳定绑定和幂等创建合同。
5. 保持 admission pure、deterministic、fail-closed，且无 runner、scheduler、credential 或 private endpoint 副作用。
6. 以有限批次完成后端事实模型、必要 schema、最小 API/UI 闭环与 freeze evidence。

## 3. Non-goals

GateX 不是真实交易 Gate。以下内容不在范围内：

- 真实下单、撤单、转账、提现、真钱包或 LIVE enable。
- private trading adapter、RealClient、real provider 或真实 permission expansion。
- AI 信号、AI 自动交易、AI Paper Trading、DH runtime 或 NQ-DH runtime integration。
- 多交易所扩张、美股/A 股、合约全量或高频交易。
- 修改交易核心状态机、策略核心算法或回测核心算法。
- 为治理而拆分 `nq-core` Maven module、微服务化、Kafka 或完整 Clean Architecture 改造。
- 将 prototype、mock、fixture 或 test-only evidence 描述为 production implementation。

## 4. Scope and boundaries

### 4.1 GateX 实施范围

- GateX-0：有限工程基线治理，仅处理 P1 和 GateX 触达区域的低风险 P2。
- GateX-1：Strategy Release domain 与 artifact verifier productionization。
- GateX-2：artifact provenance schema 与 persistence；Flyway 必须单独高风险审查。
- GateX-3：Release-to-Shadow admission productionization。
- GateX-4：后端事实模型稳定后的最小 API/UI 闭环。
- GateX-5：仅在前序批次暴露真实缺口时实施 operational hardening。
- GateX-FREEZE：完整 evidence、strict archive、authority 与 tag 流程。

### 4.2 共同禁止项

- 不开启 LIVE，不创建或调用真实交易副作用路径。
- 不读取、记录或持久化 credential、API key、secret、token、cookie 或原始签名材料。
- 不绕过 tenant/account context、validation approval、provenance、幂等或状态校验。
- 不修改历史 migration；schema 变化只能新增 forward-only migration。
- 不跨 NQ-only 边界修改或声明 DH current authority。
- 普通代码任务 docs 默认不改；确需记录时最多追加 `WORKLOG.md` 一行。

## 5. Existing capability and audit evidence

### 5.1 Production code facts

- `BacktestPublishService` 与 `BacktestPublishRecord` 已形成现有 publish 主链；同一 backtest run 通过 repository upsert 幂等复用 publish record。当前状态只有 `SUCCEEDED/FAILED`，尚无 Strategy Release lifecycle、manifest 或 verification production model。
- `backtest_publish_records.publish_record_id` 是稳定 publish identity；新增第二套 Strategy Release UUID 会制造双身份漂移。
- `shadow_runs.publish_id` 已保存 publish anchor；`shadow_runs.idempotency_key` 已有唯一约束。GateX release-bound 创建必须额外校验冲突行的 immutable anchors 一致。
- `ShadowRunRunnerService` 会创建并推进 `shadow_runs`，具有 repository/runtime side effect，不能承担 pure admission。
- validation review 已有可持久化 approval/evidence 事实，可作为 admission 输入；UI 状态或未经批准的 evidence 不得替代后端事实。
- artifact preview/binding 是事实输入，不等同于可信 artifact verification。

### 5.2 Architecture findings absorbed into GateX-0

- `strategy.domain.port.StrategyExecutionGateway` 直接依赖 Trading application request/result，而 Trading adapter 又反向实现 Strategy port，ownership 与依赖方向不清晰。
- 通用 audit port 当前由 Trading bounded context 拥有，validation-review、scheduler 和 audit infra 均反向依赖该 contract。
- 现有 ArchUnit 覆盖部分 Spring/JDBC/adapter/runtime 约束，但缺少 `nq-core` bounded-context package dependency guard。
- `StrategyValidationPage.tsx` 约 6.4k 行，混合 types、mappers、columns、evidence、workflow、lifecycle、shadow、consistency panel 与本地 `StatusTag`。
- 前端同时存在 `NqStatusTag`、design-system `StatusTag` 和页面本地 `StatusTag`；涨跌语义与成功/危险语义尚未完整收敛。
- Strategy Validation 普通用户 UI 仍暴露 Gate 阶段标签；Gate 名应限制在 audit/evidence/admin/debug/historical metadata。

### 5.3 Preparation baseline

已复核并允许毕业评估的 preparation evidence：

- `docs/drafts/pre-gatex/**` 中的 identity、artifact digest、schema delta、admission 与 research-to-shadow contract。
- `backend/nq-core/src/test/java/**/strategyrelease/preparation/**` 中的 aggregate、verifier 和 admission regression。
- `backend/nq-core/src/test/resources/gatex/**` 中的 manifest/artifact fixtures。

这些内容只提供设计证据；production architecture 必须同时服从现有 publish、validation、shadow、audit、repository 和 runtime 代码事实。

## 6. GateX-0 Engineering Baseline Hardening

GateX-0 只能占 3～5 个代码任务、约 3～7 工程日。唯一顺序为：

```text
0A Architecture Boundary Guardrails
  ↓
0B Stage-semantic Naming Cleanup
  ↓
0C Validation Frontend Decomposition
  ↓
0D Frontend Semantic Unification
  ↓
0E Scoped Query / Configuration Hygiene（条件性小批）
```

允许在可审查前提下合并 0C/0D；0A 必须先行。

### 6.1 GateX-0A Architecture Boundary Guardrails

目标：只修依赖方向和 port ownership，不拆 Maven module。

- 将 Strategy execution request/result contract 归 Strategy 或明确的中立 application port owner；Trading adapter 只负责映射。
- 将通用 audit repository contract 归入 `audit` bounded context；Trading、validation-review、scheduler 和 infra 通过新 owner 依赖。
- 增加 `nq-core` bounded-context ArchUnit regression，至少阻止 Strategy domain 依赖 Trading application、通用 audit contract 被业务 context 反向拥有，以及 infra contract 泄漏进 core。
- 小步迁移 imports、composition 与 tests，不改变公开 API、业务行为、状态机或数据库。

验收：新 ArchUnit rule 对禁止依赖 fail-closed；相关和后端全量测试通过；无新 Maven module，无 `nq-core` 大搬迁。

### 6.2 GateX-0B Stage-semantic Naming Cleanup

至少覆盖：

- `GateW3RiskPreflight*`
- `GateW4OperationalSafety*`
- `GateWOkxVenueRuleConfiguration`
- `GateWOkxPrivateReadonlyConfiguration`
- `account.infra.gatew`
- `nq.gatew.*`

规则：

- production 类、package 与新配置 key 使用 capability/domain semantics；历史 tests/evidence/archive/migration 中的 Gate 名可保留。
- 配置迁移必须同时提供 new capability key、legacy alias 和 deprecation warning，并明确移除窗口。
- 新旧 key 同时出现时必须有确定优先级；冲突值 fail-closed，不得静默选择可能扩大权限的值。
- order/transfer/withdraw 等安全开关继续 default-deny；alias 不得改变默认值。

验收：旧配置仍可启动并产生 warning；新配置生效；冲突值、非法值和 default-deny regression 通过；不批量改历史文件。

### 6.3 GateX-0C Validation Frontend Decomposition

采用渐进提取，不重写页面：

```text
frontend/src/features/validation/
  overview/
  evidence/
  review/
  lifecycle/
  shadow/
  consistency/
```

- route、API contract、query behavior、权限与业务行为不变。
- 按稳定业务区域提取 types、mappers、columns、panels 与 hooks consumer，每一步保持可构建、可回归。
- 不在 render/list loop 发请求，不把 server state 迁入 Zustand，不引入新 UI framework。
- loading、empty、error、disabled、risk 与 retry 状态不得丢失。

验收：页面收敛为 composition page；前端 build、E2E 与 Browser/Chrome 关键流程通过；无 API/backend/migration 变化。

### 6.4 GateX-0D Frontend Semantic Unification

- 收敛 `NqStatusTag`、design-system `StatusTag` 与页面本地 `StatusTag`；迁移期允许薄 adapter，不允许长期三套并存。
- `up/down` 只表达市场涨跌，`success/danger` 表达操作或系统状态，两组语义不得互换。
- 统一消费既有涨跌 token 与 metric tone。
- 从普通用户 UI 移除 `GateR/GateT/GateV` 等阶段标签；仅 audit、evidence、admin/debug、historical metadata 可显示 Gate 名。
- 风控拒绝、失败、停止、恢复、重试、过期、未配置和无权限必须显式可见。

验收：语义 mapping 有测试；关键页面视觉/交互回归通过；风险状态未被 neutral/success tone 掩盖。

### 6.5 GateX-0E Scoped Query / Configuration Hygiene

决定：保留为条件性小批，不进行全仓机械迁移。

允许范围只有 `strategy/validation/shadow/release`：

- GateX release server state 使用集中 query-key factory 与 feature-level hooks。
- 0C 中只修复真实发现的局部 key/hook 重复；现有 validation/shadow hooks 不重复重写。
- GateX 触达配置和 0B 新 key 使用 typed `ConfigurationProperties`，不扩展到无关模块。

进入条件：0A～0D 后仍有 key collision、cache invalidation、重复 fetch 或安全配置解析歧义的具体 evidence；否则以 `NOT REQUIRED / EVIDENCE RECORDED`（无需实施 / 已记录证据）关闭。

## 7. Deferred engineering backlog

以下内容不阻塞 GateX，不得纳入 GateX-0：

- `nq-paper` Maven module extraction、`nq-core` 全量拆分。
- scheduler 大重构、persistence adapters 全量归域。
- exception/null/broad-catch 全仓治理。
- DB ID naming migration。
- 微服务、Kafka。

只有独立 evidence、收益、风险和回滚计划才能重新排期。

## 8. Prototype graduation decisions

### 8.1 Canonical Strategy Release identity

```text
publishRecordId = releaseAnchorId
                = backtest_publish_records.publish_record_id
```

- 不新增独立 Strategy Release UUID，不建立平行 publish/release 主链。
- production aggregate 直接使用 `releaseAnchorId`；prototype 中 `releaseId + publishId` 双字段不得原样毕业。
- API、repository、audit 和 Shadow binding 使用同一 identity；同时携带两个身份时必须验证相等，否则 fail-closed。

### 8.2 Artifact provenance and schema candidate

`shadow_runs.publish_id` 继续作为 release anchor。GateX-2 唯一允许评审的 schema delta candidate 为：

```sql
shadow_runs.artifact_digest VARCHAR(64) NULL
```

约束候选：

```text
artifact_digest IS NULL OR artifact_digest ~ '^[0-9a-f]{64}$'
artifact_digest IS NULL OR publish_id IS NOT NULL
```

- digest 是 64 位 lowercase hex；禁止 uppercase、前缀或截断表达。
- 历史行不从其他字段推断 digest，不做 fake/silent backfill。
- 禁止 `UNIQUE(publish_id, artifact_digest)`；同一 release/artifact 可对应多个 Shadow Run。
- `idempotency_key` 继续是创建去重边界。
- `publish_id`、`artifact_digest` 与 binding mode 创建后不可变；冲突行必须验证 immutable anchors 全部一致。
- 本文只冻结候选，不声明 schema 已落地；GateX-2 必须独立 schema review、forward-only migration 和 PostgreSQL 验证。

Binding modes：

| Mode | `publish_id` | `artifact_digest` | Admission |
| --- | --- | --- | --- |
| `LEGACY_UNBOUND` | `NULL` | `NULL` | 拒绝 |
| `LEGACY_PUBLISH_ONLY` | 非空 | `NULL` | 拒绝 |
| `RELEASE_BOUND` | 非空 | 非空 | 可继续评估，不等于自动通过 |

只有 `RELEASE_BOUND` 可进入 admission。

### 8.3 Production artifact verifier

正式 verifier 必须覆盖：

- trusted root、canonical normalization、path containment 与 allowlist。
- Windows symlink/reparse point 与 POSIX symlink 合同。
- manifest/schema version、必需文件 closed set、单文件和 aggregate digest。
- 文件数量、单文件大小、总大小和读取上限。
- forbidden sensitive fields、credential-like payload 和绝对路径泄漏。
- 稳定 error code、日志脱敏与 fail-closed。

Prototype 尚未关闭的 Windows reparse、稳定句柄/TOCTOU 与跨平台验证必须在 GateX-1 形成正式决定和 regression；无法证明验证期间文件身份稳定时，不得返回 `VERIFIED`。

### 8.4 Release-to-Shadow admission

Admission 必须是 pure/deterministic function，只消费已持久化或不可变输入，不访问 runner、repository、scheduler、network、private endpoint 或 credential。

只有全部条件满足才可生成 `ShadowRunCreationPlan`：

```text
release state = PUBLISHED
artifact verification = VERIFIED
binding mode = RELEASE_BOUND
publish/digest provenance valid
validation evidence = APPROVED and same-anchor bound
side-effect policy complete and no-real/default-deny
requested window and immutable anchors valid
```

- `CreationPlan != ShadowRunCreated`。
- admission 不调用 `ShadowRunRunnerService`，不写 repository，不创建 scheduler job，不推进 Shadow lifecycle。
- 正式窗口合同采用严格 `end > start`。
- idempotency key 从 canonical request fields 确定性生成，适配现有 `VARCHAR(160)`，不得包含敏感内容。
- 同一 idempotency key 只有在 release anchor、digest、window、policy、tenant/account 等 immutable anchors 全部一致时才能返回既有 run，否则返回冲突。

## 9. Proposed production flow

```text
Backtest publish record
  └─ canonical releaseAnchorId
       ↓
Strategy Release domain
  ├─ PUBLISHED state
  ├─ immutable manifest reference
  └─ artifact digest
       ↓
Trusted-root Artifact Verifier
  └─ VERIFIED or fail-closed findings
       ↓
APPROVED validation evidence with same anchor
       ↓
Pure Release-to-Shadow Admission
  └─ ShadowRunCreationPlan only
       ↓ explicit application creation boundary
Shadow Run persistence
  └─ publish_id + artifact_digest + idempotency_key atomically written
```

Runner、scheduler 与 runtime progression 位于 creation boundary 之后，不属于 admission。

## 10. GateX batch plan

### GateX-PLAN — 本任务

- 建立 implementation baseline、current authority 与 roadmap。
- 状态：`BASELINE ESTABLISHED / READY TO COMMIT`（基线已建立 / 可进入提交前复核）。
- 不产生 production code、migration、API、UI 或 runtime side effect。

### GateX-0 — Engineering Baseline Hardening

- 0A：architecture ownership 与 ArchUnit guardrails。
- 0B：stage-semantic naming 与配置 compatibility migration。
- 0C/0D：Validation page 渐进拆分与前端语义收敛，可合并为一个可审查任务。
- 0E：仅在 evidence 证明必要时实施。
- 总量：3～5 个代码任务、3～7 工程日；完成后才能开始 GateX-1。

### GateX-1 — Strategy Release / Artifact Productionization

- 建立 production Strategy Release domain、canonical identity、lifecycle invariant 与 trusted-root verifier。
- 将 preparation regression 提升到 production package，不直接以 test package 充当 runtime 实现。
- 关闭 reparse/symlink、TOCTOU、manifest/schema/digest、资源上限、敏感字段和错误脱敏合同。

### GateX-2 — Artifact Provenance Schema + Persistence

- 单独评审 nullable `artifact_digest` 与两个 CHECK candidate。
- 新增 forward-only migration、中文 COMMENT、repository mapping、atomic create 与 immutable-anchor conflict validation。
- 不 fake backfill，不增加 `(publish_id, artifact_digest)` unique，不修改历史 migration。

### GateX-3 — Release-to-Shadow Admission Productionization

- 实现 pure/deterministic admission、稳定 finding/error taxonomy、approved validation binding 与 no-side-effect policy。
- 只生成 creation plan；creation application service 与 runner 明确分离。
- 覆盖 identity mismatch、legacy binding、unverified artifact、rejected/stale evidence、policy incomplete、zero window 和 idempotency conflict。

### GateX-4 — API / Minimal UI

- 只有 GateX-1～3 后端事实模型稳定后才能开始。
- API 只暴露已实现 release、verification、admission facts；不暴露 Entity、内部路径、异常栈或 raw manifest。
- UI 只提供最小 release/provenance/verification/admission 可见性，覆盖 loading/empty/error/disabled/risk。
- 不新增交易按钮、LIVE 开关、private permission 或 runner 自动启动入口。

### GateX-5 — Conditional operational hardening

- 只有前序 integration/replay/故障注入暴露真实缺口才实施。
- 可处理 bounded replay、并发幂等、可审计恢复和资源上限；不得扩展 scheduler 或交易范围。
- 无缺口时以 evidence 关闭为 `NOT REQUIRED`，不制造 docs/code churn。

### GateX-FREEZE

- 汇总 implementation、API、frontend、backend/DB、runtime boundary、testing、CI、安全与 known limitations evidence。
- 按 `scripts/docs/gate-archive-manifest.json` 一次性形成完整 pre-tag strict archive。
- archive commit exact-head CI 成功后才能创建 annotated tag；tag 后另行同步 current authority，不改写 tag-bound history。

## 11. Testing strategy

| Batch | Minimum validation |
| --- | --- |
| 0A | ArchUnit、相关 unit tests、`mvn -f backend/pom.xml test` |
| 0B | new/legacy key、conflict、default-deny、deprecation tests；后端全量测试 |
| 0C/0D | `npm run build`、`npm run test:e2e`、Browser/Chrome 关键流程与状态语义 tests |
| 0E | query-key/cache 与 typed config regression，仅在实施时运行 |
| 1 | verifier golden/negative/cross-platform/resource/sensitive-field tests；后端全量测试 |
| 2 | migration contract、repository tests、本地 PostgreSQL constraint/comment/idempotency 验证 |
| 3 | pure admission golden cases 与 identity/provenance/policy/window/idempotency failure matrix |
| 4 | API contract/controller tests、前端 build/E2E 与 Browser/Chrome 回归 |
| 5 | bounded replay、并发、故障注入、资源与恢复 evidence，仅适用时 |
| FREEZE | 按实际触达范围运行全量验证、authority/archive/link checkers 与 exact-head CI |

所有测试默认 no-real/no-egress，不依赖真实交易所、真实 credential 或生产用户数据。未运行的验证必须写 `NOT RUN`，不得推断通过。

## 12. Security boundary

- LIVE 始终 `DISABLED`，release/admission 状态不改变 kill switch 或 no-real boundary。
- Verifier 只读 allowlisted trusted root，拒绝 traversal、root escape、symlink/reparse、超限和 sensitive payload。
- API、audit 和日志只输出稳定 ID、脱敏 finding/error code 与必要 trace metadata，不输出 credential、raw manifest、绝对主机路径或异常栈。
- Admission 不访问 network、private endpoint、credential、runner、scheduler 或 repository。
- Tenant/account/release/artifact/validation binding 必须由后端验证；前端展示不构成授权。
- PAPER/Shadow 与 LIVE 继续隔离；GateX 只建立 admission，不启用 Shadow runtime。
- NQ-only 任务不修改 DH 状态，不宣称 Integration runtime 或 real provider readiness。

## 13. Risks

### P0

- Admission/creation 触发 order/cancel/transfer/withdraw、LIVE、private endpoint 或 credential access：立即阻断。
- Path escape、symlink/reparse bypass 或 digest verification 绕过，使不可信 artifact 获得 `VERIFIED`：立即阻断。

### P1

- Strategy↔Trading 或 audit ownership 未修复即引入 production release：必须先完成 0A。
- 独立 release UUID 与 publish identity 漂移：以 `publishRecordId=releaseAnchorId` 消除。
- publish/digest/validation 未绑定同一 immutable anchor，或 idempotency collision 返回不一致 run：fail-closed 并补 atomic regression。
- Admission 调用 runner/repository/scheduler 或把 creation plan 当作 created run：职责隔离与 tests 阻断。
- Schema 缺 format/anchor CHECK、中文 COMMENT、forward-only review：GateX-2 阻断。

### P2

- Windows reparse、稳定句柄/TOCTOU 或跨平台 verifier 行为未证明：GateX-1 不得接受 production `VERIFIED` contract。
- Validation 页面拆分丢失 error/risk 状态：0C 渐进提取与 E2E/Browser regression 控制。
- Legacy config alias 冲突或长期滞留：deprecation、移除窗口与 conflict tests 控制。
- up/down 与 success/danger 混用：0D 统一 token、mapping 与视觉回归。

### P3

- 历史 Gate 名残留于 archive/evidence/test fixture：允许保留，不做无收益 churn。
- 非关键文案或目录命名不一致：自然触达时修正，不创建独立 docs-only 批次。

## 14. GateX freeze criteria

1. 0A～0D 已完成并接受；0E 已实施通过或以 evidence 关闭为 `NOT REQUIRED`。
2. Strategy Release 使用唯一 canonical identity，preparation-only package 不承担 production runtime。
3. Verifier 的 trusted root、path/reparse、manifest/schema、digest、resource、sensitive-field、TOCTOU 和 fail-closed regression 全部通过。
4. Provenance schema（如实施）通过独立 review、migration、PostgreSQL 和 repository 验证，历史行无 fake backfill。
5. Admission pure/deterministic，只生成 creation plan；eligibility、legacy binding、identity、provenance、validation、policy、window 和 idempotency failure paths 有测试。
6. 最小 API/UI 与 production facts 一致，无 raw path/manifest/secret 泄漏，无 LIVE/private trading 操作入口。
7. 按实际触达范围完成全量验证和 exact-head CI；P0=0、P1=0，P2/P3 有 owner、风险和接受决定。
8. LIVE=`DISABLED`、Shadow trading=`NOT_ENABLED`、AI=`NOT_STARTED`、DH runtime=`NOT_INTEGRATED`，真实交易副作用为 0。
9. Strict archive 满足 machine manifest，authority/archive/link checkers 通过；tag 流程遵守 archive commit CI → annotated tag → post-tag current sync。

## 15. Final decision and next task

GateX implementation baseline 已建立，但必须先完成工程基线治理，不能直接进入 Strategy Release productionization。

```text
PASS /
GATEX_IMPLEMENTATION_BASELINE_ESTABLISHED /
ENGINEERING_HARDENING_REQUIRED /
READY_TO_COMMIT
```

唯一下一任务：

```text
NQ-GATEX-0A-ARCHITECTURE-BOUNDARY-GUARDRAILS-IMPLEMENTATION
```

该任务 docs 默认不改；如确需记录，只允许在 `docs/current/WORKLOG.md` 追加一行。不得修改 GateX plan、STATUS、ROADMAP、API、DB_SCHEMA 或 README，除非另有明确授权。
