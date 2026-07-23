# NQ-PRE-GATEX-STRATEGY-RELEASE-AGGREGATE-IDEMPOTENCY-PROTOTYPE-ATTEMPT-01

## Task classification

`NQ-only / TEST_ONLY_DOMAIN_PROTOTYPE / PRE-GATEX PREPARATION / UNMERGED`

## Execution status

`PREPARED / SELF_REVIEWED / READY_TO_COMMIT_ON_PREP_BRANCH`

## Preparation baseline

- Preparation branch：`prep/gatex-research-to-shadow`
- Preparation worktree：开始时 clean，staged empty
- Starting HEAD：`928d5b81d67f7d7042d2c18fdeb5a7b470f603a2`
- `origin/dev` HEAD：`557980eaf5e6302d9a46d718b124f0f530aa74f1`
- `origin/dev` ancestry：通过；ahead/behind 为 `0 2`
- Preparation remote SHA：`928d5b81d67f7d7042d2c18fdeb5a7b470f603a2`
- Previous verifier commit：`928d5b81 test(gatex): prototype trusted-root artifact verification`
- 主工作区：`dev`、clean、HEAD 等于 `origin/dev`
- Authority checker：errors `0`
- GateW：`IN_PROGRESS / NOT_FROZEN`
- Attempt-09：`RUNNING / PENDING_168H`
- Soak server：未访问

## Files inspected

- `docs/current/STATUS.md`
- `docs/drafts/pre-gatex/RESEARCH_TO_SHADOW_CONTRACT_PREPARATION.md`
- `docs/drafts/pre-gatex/STRATEGY_RELEASE_SCHEMA_PROPOSAL.sql`
- `docs/drafts/pre-gatex/ARTIFACT_VERIFICATION_SECURITY_PROTOTYPE.md`
- `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.schema.json`
- `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.golden.json`
- `StrategyReleaseLifecyclePrototypeTest.java`
- `TrustedRootArtifactVerifierPrototype.java`
- `TrustedRootArtifactVerifierPrototypeTest.java`
- production `BacktestPublishRecord`、`BacktestPublishService`、repository/JDBC 与 V10 migration
- production `StrategyVersion`、`StrategyVersionService`
- production dataset、evaluation report domain
- production validation-review request hash/idempotency service
- production Shadow Run aggregate、repository、optimistic lock exception、event 与 JDBC adapter

## Existing publish/release findings

- 未发现等价 production Strategy Release aggregate、repository 或 service。
- 现有 `backtest_publish_records` 是唯一 publish anchor。
- Strategy Release 必须扩展该 anchor，禁止创建平行 publish 主链。
- production publish 按既有业务事实做幂等复用；Shadow Run 使用 idempotency key、expected version、
  conditional update 与 append-only event。

## Design decisions

- Aggregate boundary：不可变保存 release、publish、strategy version、dataset、evaluation、schema、
  artifact digest 与 created time anchors。
- Business identity：`publishId`，对应现有 `publish_record_id`。
- Lifecycle：直接复用 `DRAFT / CANDIDATE / VERIFIED / PUBLISHED / REJECTED / RETIRED`。
- Artifact verification gate：只消费不可变 result；`VERIFIED` + digest exact match + supported schema +
  positive verified size + no blocking findings 才能进入 release `VERIFIED`。
- Idempotency：repository-instance scoped global `actionId`；same action/fingerprint 返回首次 result。
- Completed-state payload：按 `releaseId + state` 保存首次成功 payload fingerprint；不同 action 仅在
  payload 完全一致时返回幂等成功，否则 `STATE_PAYLOAD_CONFLICT`。
- Request fingerprint：长度前缀 canonical fields 的 UTF-8 SHA-256，创建覆盖全部既定 anchors 与
  `expectedVersion`。
- Optimistic version：成功状态变化严格 `expectedVersion + 1`；conflict 不变更、不追加 event。
- Events：成功变化 append-only；非法转换与 verification gate 拒绝保留脱敏 rejection event；
  version/idempotency/business conflicts 不追加 event。
- Conflict taxonomy：`IDEMPOTENCY_CONFLICT`、`BUSINESS_IDENTITY_CONFLICT`、
  `RELEASE_ID_CONFLICT`、`VERSION_CONFLICT`、lifecycle 与 verification 明确原因码。

## Files created

1. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseAggregatePrototype.java`
2. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseRepositoryPrototype.java`
3. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseServicePrototype.java`
4. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseAggregateIdempotencyPrototypeTest.java`
5. `docs/drafts/pre-gatex/STRATEGY_RELEASE_AGGREGATE_IDEMPOTENCY_PROTOTYPE.md`
6. `docs/drafts/pre-gatex/NQ-PRE-GATEX-STRATEGY-RELEASE-AGGREGATE-IDEMPOTENCY-PROTOTYPE-ATTEMPT-01.md`

## Files changed

除上述 6 个新增 allowlist 文件外，无。

## Tests

已执行：

```powershell
mvn -pl nq-core -am "-Dtest=StrategyReleaseAggregateIdempotencyPrototypeTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：

```text
17 tests / 0 failures / 0 errors / 0 skipped
BUILD SUCCESS
exit code 0
```

```powershell
mvn -pl nq-core -am "-Dtest=*PrototypeTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

```text
52 tests / 0 failures / 0 errors / 3 skipped
BUILD SUCCESS
exit code 0
```

```powershell
mvn -pl nq-core -am test
```

```text
398 tests / 0 failures / 0 errors / 3 skipped
BUILD SUCCESS
exit code 0
```

3 个 skipped 均来自既有 `TrustedRootArtifactVerifierPrototypeTest` 的 Windows symbolic-link
权限 assumption：`NOT_RUN / SYMLINK_PRIVILEGE_UNAVAILABLE`（未运行 / symbolic link 权限不可用）。
本轮新增的 17 个测试全部执行、无跳过。

## Security and trading boundary

- 无 credential、API key、secret、token、cookie、private key 或私有 payload。
- 无真实 artifact IO、绝对路径或仓库外 artifact 访问。
- 无 Spring、数据库、JDBC、API、scheduler 或 runner。
- 无 Shadow Run 状态修改、账户、订单、余额或 ledger。
- 所有 result 固定 `diagnosticOnly=true`、`notTradingAuthorization=true`、`liveDisabled=true`。
- LIVE 仍为 `DISABLED`（已禁用）。

## Findings

- P0：无。
- P1：无。
- P2：正式 GateX 仍需数据库并发原子性、Windows reparse/trusted-root 与 production security review。
- P3：当前为单进程 test-only repository，不证明跨进程持久化行为。

## Known limitations

- in-memory repository 只验证合同，不验证数据库 transaction/unique constraint/conditional update。
- event ID 使用 repository-instance sequence，不是 production ID 策略。
- verifier result 是不可变测试输入；service 不执行真实 artifact 验证。
- 未实现 tenant/RBAC/API，因为均在本轮禁止范围。

## Scope status

- Production code status：未修改；Strategy Release production repository/service 仍 `NOT IMPLEMENTED`。
- Migration status：未修改；无 Flyway/DDL 执行。
- Authority modifications：无。
- GateW impact：无；GateW remains `IN_PROGRESS / NOT_FROZEN`。
- GateX remains `NOT_STARTED`。
- Preparation branch remains `UNMERGED`。
- `dev` remains unchanged。
- LIVE remains `DISABLED`。

## Recommendation

- Commit recommendation：建议 `test(gatex): prototype strategy release aggregate semantics`
- Push recommendation：本任务不自动 push。
- Merge recommendation：`NO DEV MERGE`。
- Next action：`PREPARATION_BRANCH_HOLD / NO_DEV_MERGE`

## Final decision

`PREPARED / SELF_REVIEWED / READY_TO_COMMIT_ON_PREP_BRANCH`
