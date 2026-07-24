# NQ-PRE-GATEX-RELEASE-TO-SHADOW-ADMISSION-PROTOTYPE-ATTEMPT-02

状态：`PRE-GATEX PREPARATION / UNMERGED`（GateX 前准备 / 未合并）。

## 范围

本任务只新增 `nq-core` test-source admission contract 与两份草案文档。它不修改 production code、
Flyway、数据库、GateW authority、runner、scheduler、交易所或任何交易写侧。

## 决策

- release anchor 固定为 `backtest_publish_records.publish_record_id`，并复用 `shadow_runs.publish_id`；
- 仅 `RELEASE_BOUND + PUBLISHED + VERIFIED + APPROVED` 可生成无副作用 Creation Plan；
- `BLOCKED`、`UNKNOWN` 都不创建 Shadow Run 或其他本地事实；
- `shadowRunIdempotencyKey` 是 deterministic SHA-256，排除 action/trace；
- GateX 前正式 migration 仍未实现，当前 production schema 不能执行 plan。

## 验证

执行环境为 Windows / Java 21。以下命令均真实通过：

```text
StrategyReleaseToShadowAdmissionPrototypeTest
11 tests / 0 failures / 0 errors / 0 skipped

*PrototypeTest
71 tests / 0 failures / 0 errors / 3 skipped

nq-core -am test
417 tests / 0 failures / 0 errors / 3 skipped
```

三个 skipped 均为既有 `TrustedRootArtifactVerifierPrototypeTest` 的 Windows symbolic-link 权限限制；
新增 admission 测试没有 skipped。未执行数据库、soak、runner、交易所或任何私有网络验证。

## 边界

`diagnosticOnly=true`、`notTradingAuthorization=true`、LIVE=`DISABLED`。本任务不授权 Shadow Run 启动、
真实下单、凭证访问、私有 endpoint、AI 或 DH runtime。
