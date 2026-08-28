---
name: nq-java-engineering-standard
description: Apply NexusQuant high-risk Java engineering constraints only for cross-module architecture, broad Spring wiring, core transaction/concurrency, trading/risk/ledger/audit core, Java/Spring/Maven upgrades, static architecture-rule changes, or repository-wide Java audits. Do not use for ordinary DTO, small Service, mapping, or local bug fixes.
---

# NQ High-Risk Java Engineering Standard

本 Skill 是风险触发的 supporting Skill，不是所有 Java 任务的默认入口。

## 触发条件

仅在以下任一范围存在时加载：

- 跨模块架构或大范围 Spring wiring；
- transaction/concurrency 核心；
- trading/risk/ledger/audit 核心状态与一致性；
- Java、Spring 或 Maven 主版本/基线升级；
- ArchUnit、Checkstyle、PMD、SpotBugs 等全局规则变化；
- 全仓 Java 审计。

普通 DTO、小 Service、局部 mapping、单点异常转换或一般回归不得固定加载本 Skill；它们分别使用 `java-backend-maintenance` 或 `java-backend-regression-tests`。

## 高风险约束

- 保持 `nq-api`、`nq-core`、`nq-infra`、adapter 的依赖方向与职责边界。
- 核心状态更新校验当前状态；明确事务、锁、幂等 key、失败状态与补偿。
- 外部调用必须有超时、有界重试、限流、错误转换和脱敏追踪，且不在长事务内等待。
- 线程池、队列、缓存与批量均有界；所有资源在成功/失败路径释放。
- 交易环境只接受 canonical `SIM / LIVE`；不得让 venue 或历史兼容命名改变领域语义。
- credential、LIVE、PLACE/CANCEL、transfer/withdraw 与生产副作用必须同时有 current authority 和用户显式授权；否则 fail-closed。

## 验证

按实际范围选择模块 Maven 测试、架构/静态规则和失败路径回归；不得用全量扫描替代与改动直接相关的测试，也不得把未执行结果写成通过。

## A. Role

- Role type: `SUPPORTING_CONSTRAINT`
- Primary responsibility: `HIGH_RISK_JAVA_CONSTRAINT_EVALUATION`

本 Skill 是 `SUPPORTING HIGH-RISK JAVA CONSTRAINT SKILL`：只在确定的高风险 Java trigger 下选择、评估并报告适用工程不变量。

## B. Trigger

- Positive trigger class：`ARCHITECTURE`、`TRANSACTION_CONCURRENCY`、`TRADING_CORE`、`PLATFORM_OR_DEPENDENCY_CHANGE`、`STATIC_RULE_CHANGE`、`FULL_JAVA_AUDIT`。
- Exclusion：普通 DTO、局部 Service/Controller/Repository 实现、mapping、单点 bug 和纯测试设计；这些分别由 `java-backend-maintenance` 或 `java-backend-regression-tests` primary ownership。

## C. Input / Context

必须先读取 `docs/standards/java/platform-profile.json`，再按 exact trigger 和 affected scope 选择相关 standards、代码、构建配置、规则配置与测试。除 `FULL_JAVA_AUDIT` 外禁止无差别加载 `docs/standards/java/**`。

## D. Required Actions

1. Identify the exact high-risk trigger.
2. Read `platform-profile.json`.
3. Select only standards relevant to the affected scope.
4. Evaluate every applicable engineering invariant.
5. Add risk-specific validation to the primary Skill plan.
6. Classify each check as `PASS`、`NOT_APPLICABLE`、`VIOLATION` 或 `EXCEPTION_WITH_REASON`.
7. Report applied standards, findings, exceptions and residual risk.

### Additional Checks by Trigger

- `ARCHITECTURE`：module dependency direction、core/infra direction、port/adapter ownership、Controller/Service/Repository responsibility、Spring dependency cycles、ArchUnit impact。
- `TRANSACTION_CONCURRENCY`：transaction boundary、locking、idempotency、duplicate execution、executor ownership、timeout、race conditions、external-call + transaction interaction。
- `TRADING_CORE`：state machine、risk-before-execution、idempotency、audit trail、ledger ownership、`SIM/LIVE` isolation、accepted+timeout/reconciliation、failure recoverability。
- `PLATFORM_OR_DEPENDENCY_CHANGE`：Java/Spring compatibility、Maven dependency graph、runtime support、test compatibility、rollback/migration impact。
- `STATIC_RULE_CHANGE`：ArchUnit、Checkstyle、PMD、SpotBugs、Shadow rule/baseline、exception policy。
- `FULL_JAVA_AUDIT`：允许扩大 standards 范围，但仍逐项判断是否 applicable。

## E. Validation

- Required：affected module tests 与 exact trigger 对应的风险验证。
- Conditional：`ARCHITECTURE` 增加 relevant ArchUnit；`TRANSACTION_CONCURRENCY` 增加 concurrency/integration regression；`STATIC_RULE_CHANGE` 运行 `scripts/java-standard/verify-java-engineering-standard.ps1`，仅在 rule/baseline scope 适用时运行 shadow scan；`FULL_JAVA_AUDIT` 才允许 broader Java validation。
- Not applicable：未命中的 checker、全量 scan 与无关模块测试；Skill 被加载本身不触发 shadow scan。

## F. Output Contract

输出 exact trigger、selected standards、逐类别 `PASS | NOT_APPLICABLE | VIOLATION | EXCEPTION_WITH_REASON`、追加验证及结果、exceptions、residual risk；禁止只写“已检查 Java standard”。

## G. Non-goals

不作为普通 Java implementation 或 test-design owner，不自动重构、不拥有全仓 architecture governance，也不把高风险约束变成所有 Java 任务的固定流水线。

## H. Overlap / Ownership

`java-backend-maintenance` 对生产实现是 `PRIMARY_OWNER`，`java-backend-regression-tests` 对测试设计和 regression proof 是 `PRIMARY_OWNER`；本 Skill 仅对命中的高风险约束是 `SUPPORTING_OWNER`。
