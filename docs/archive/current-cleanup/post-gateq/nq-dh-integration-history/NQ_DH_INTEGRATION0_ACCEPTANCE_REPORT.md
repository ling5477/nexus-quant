# NQ-DH Integration-0 Acceptance Report

> 任务：NQ-DH-INTEGRATION0-SAFETY-GATE-CLOSE
> 类型：DOCUMENTATION + ACCEPTANCE_REPORT
> 日期：2026-06-12
> 仓库视角：NexusQuant（NQ，交易事实源 / 主权执行方）
> 对端：Decision Hub（DH，AI Agent 决策能力层）
> 配套：`NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md` / `NQ_DH_INTEGRATION0_SECURITY_POLICY.md` / `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`

---

## 1. Acceptance decision

```text
Decision:             PASS
Integration-0 safety gate: CLOSED / ACCEPTED
Runtime integration:  NOT STARTED
Integration-1:        NOT STARTED
LIVE:                 DISABLED
AI:                   NOT STARTED
DH integration:       NOT INTEGRATED
```

Integration-0 是 contract / mock / documentation work line，本次验收只关闭 **Integration-0 契约与 contract test 安全门**，**不代表真实集成开始**。NQ 侧仍无 DH 入站端点、无 DH client、无 feedback outbox。

## 2. Completed work chain

```text
1. NQ 第一轮全仓只读审计              completed
2. DH 第二轮全仓只读审计              completed
3. NQ-DH 第三轮联合边界审计           completed
4. 三轮审计汇总                       completed
5. DOC-SYNC-GATEK-PRE-AND-INT0-REGISTRATION  completed
6. Integration-0 契约冻结             completed
7. Integration-0 mock / contract test 设计   completed（15 项 × 16 字段矩阵）
8. Integration-0 contract test 代码实现       completed（NQ 16 + DH 16）
9. Integration-0 contract test implementation review / safety gate review  PASS
10. Integration-0 safety gate close / acceptance report  本文件
```

## 3. Contract scope accepted

以下 10 个契约已冻结并由 contract test 保护，状态：**contract-only / mock-only / test-protected / not runtime integration**。

| 契约 | 方向 | 状态 |
| --- | --- | --- |
| DHSignalCandidate | DH_TO_NQ | contract-only / mock-only / test-protected |
| DHResearchReport | DH_TO_NQ | contract-only / mock-only / test-protected |
| DHRiskReview | DH_TO_NQ | contract-only / mock-only / test-protected |
| DHDecisionSummary | DH_TO_NQ | contract-only / mock-only / test-protected |
| NQFeedbackEvent | NQ_TO_DH | contract-only / mock-only / test-protected |
| NQPaperResultSummary | NQ_TO_DH | contract-only / mock-only / test-protected |
| NQStrategyMetadata | NQ_TO_DH | contract-only / mock-only / test-protected |
| NQBacktestSummary | NQ_TO_DH | contract-only / mock-only / test-protected |
| NQErrorResponse | NQ_TO_DH | contract-only / mock-only / test-protected |
| NQDhContractError | NQ_TO_DH | contract-only / mock-only / test-protected |

说明：这些契约**没有**对应真实入站端点 / 真实 DH client / 真实 HTTP；仅由 test-only 内存校验器与脱敏 fixture 保护。

## 4. Test coverage accepted

| 用例 | 名称 | NQ | DH | negative path |
| --- | --- | --- | --- | --- |
| INT0-T01 | 禁止能力 | ✓ | ✓ | ✓ |
| INT0-T02 | 可开放能力 | ✓ | ✓ | n/a（正向 + 无副作用） |
| INT0-T03 | header 缺失 | ✓ | ✓ | ✓（401/403/400 映射） |
| INT0-T04 | HMAC 失败 | ✓ | ✓ | ✓ |
| INT0-T05 | timestamp 过期 | ✓ | ✓ | ✓（过去+未来） |
| INT0-T06 | nonce replay | ✓ | ✓ | ✓ |
| INT0-T07 | tenant mismatch | ✓ | ✓ | ✓ |
| INT0-T08 | payload > 64 KiB | ✓ | ✓ | ✓ |
| INT0-T09 | forbidden field | ✓ | ✓ | ✓ |
| INT0-T10 | raw prompt/context | ✓ | ✓ | ✓ |
| INT0-T11 | candidate schema | ✓ | ✓ | ✓ |
| INT0-T12 | feedback schema | ✓ | ✓ | ✓ |
| INT0-T13 | audit required | ✓ | ✓ | ✓（audit shape + 不泄敏） |
| INT0-T14 | no trading side-effect | ✓ | ✓ | n/a（副作用计数=0） |
| INT0-T15 | no credential access | ✓ | ✓ | n/a（凭证访问=0） |

```text
NQ 侧 16 tests passed；DH 侧 16 tests passed。
两侧均覆盖 T01-T15。
negative path 已覆盖（NQ 22 / DH 24 个 assertFalse；各 11 个拒绝码断言 401/403/409/413/400）。
audit event shape 已覆盖（T13 + T04 SIGNATURE_FAILED + T06 REPLAY_REJECTED）。
forbidden side-effect 已覆盖（T14 side-effect=0 + T15 credential access=0）。
```

## 5. Validation evidence

NQ（本轮 review 与上一轮实现复核结果，本验收引用）：

```text
mvn -f backend/pom.xml test   BUILD SUCCESS
nq-app                        51 tests / 0 failures / 0 errors（原 35 + Integration-0 16）
Integration-0                 16 tests passed（ContractValidation 6 + Security 8 + NoSideEffect 2）
ArchUnit                      ModuleBoundaryArchTest / PackageBoundaryArchTest 全绿
git status --short            clean
git diff --check              clean
git show HEAD                 仅 src/test/** + docs/current/**（无 src/main / 无 pom）
```

DH（引用，详见 DH 仓库 acceptance report）：

```text
mvn test                      BUILD SUCCESS
dh-domain                     86 tests / 0 failures（含 Integration-0 16）
ArchitectureTest              12 条全绿
PostgresContainerSmokeTest    Docker 不可用自动 skip（既有环境性 skip，非本轮引入，不阻塞）
git status / git diff         clean
git show HEAD                 仅 src/test/** + docs/current/**（无 src/main / 无 pom）
```

## 6. Accepted boundaries

```text
无生产代码改动（无 src/main）
无 API 改动
无 migration 改动
无 RealClient（NQ/DH 均无）
无真实 Provider
无真实 HTTP
无真实交易所调用
无真实 NQ 调用
无 AI runtime
无 LIVE
无凭证读取（fixtures 仅 FAKE-PLACEHOLDER 与脱敏摘要）
无 NQ DB 读写
无交易副作用
```

## 7. Still forbidden after Integration-0 close

```text
真实联调
Integration-1 直接启动
NQ RealClient
DH RealClient
真实 Provider
真实 HTTP
真实 NQ endpoint
真实交易所调用
下单 / 撤单
Paper Run 启停
策略状态修改
风控状态修改
读写 NQ DB
读取凭证
LIVE
AI 自动交易
```

## 8. Integration-1 blockers

### DH P1-4 residual（不阻塞 Integration-0 close，阻塞 Integration-1）

```text
rate limit 缺失
memory cap 缺失
replay nonce persistence 缺失
```

要求：

```text
Integration-1 前必须修复上述三项。
修复后必须重跑 contract tests。
T06 必须以持久化 / 集中缓存 nonce 重跑（替换 test-only 内存 nonce store）。
必须新增 rate limit 429 测试。
必须新增 memory cap / bounded store 测试。
```

### Header alignment

```text
X-DH-NQ-* 与 X-NQ-DH-* 需要在 Integration-1 前统一。
统一后需更新 contract fixtures。
统一后需重跑 NQ / DH contract tests。
```

### Real channel safety

```text
Integration-1 必须单独开工。
必须先做设计审计。
必须 staging / paper-only。
必须 LIVE disabled。
必须无凭证落日志。
必须 no trading side-effect。
必须通过安全审查。
```

## 9. Next allowed actions

只允许：

```text
1. Integration-0 acceptance commit
2. Integration-0 archive / tag / gate close docs
3. Integration-1 planning-only audit
4. DH P1-4 residual fix planning
5. GateK-PLAN 文档规划
```

禁止：

```text
1. 直接 Integration-1 implementation
2. 真实只读通道
3. 真实 HTTP
4. RealClient
5. Provider
6. LIVE
7. AI 自动交易
```

## 10. Final decision

```text
Integration-0:        PASS / CLOSED / ACCEPTED
Integration-1:        NOT STARTED
Runtime integration:  NOT STARTED
LIVE:                 DISABLED
AI:                   NOT STARTED
DH integration:       NOT INTEGRATED
```
