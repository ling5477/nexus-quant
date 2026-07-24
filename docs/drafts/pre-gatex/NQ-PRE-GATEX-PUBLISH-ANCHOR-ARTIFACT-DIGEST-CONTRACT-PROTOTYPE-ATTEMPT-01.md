# NQ-PRE-GATEX Publish Anchor Artifact Digest Contract Prototype Attempt-01

状态：`PREPARED / SELF-REVIEWED / READY TO COMMIT ON PREP BRANCH`（已准备 / 已自审 / 可在 preparation 分支提交）。

## 范围

- NQ-only、test-only、pre-GateX preparation。
- 冻结 `publish_record_id` 为 Strategy Release 与 Shadow Run 的唯一 release anchor。
- 仅新增 test-source contract 和非 Flyway SQL / 文档草案；不修改 production schema 或 runtime。

## 设计结论

```text
publishRecordId = releaseAnchorId = backtest_publish_records.publish_record_id
shadow_runs.publish_id = 唯一 Shadow Run 发布/release anchor
artifact_digest = nullable 的冻结 artifact provenance 补充字段
```

三种 binding mode 为 `LEGACY_UNBOUND`、`LEGACY_PUBLISH_ONLY` 与 `RELEASE_BOUND`。只有
`RELEASE_BOUND` 可作为未来 admission 的 provenance 前置；这不是交易授权。

## 禁止与兼容

- 不新增 `strategy_releases` UUID 表、`release_id` 或第二个 publish/release ID。
- 不创建或启动 Shadow Run，不写数据库，不执行 DDL，不做 backfill。
- legacy 行保持原值，禁止从 snapshot checksum 或其他事实伪造 digest。
- `idempotency_key` 保持 Shadow Run 创建去重边界；同一 publish/digest 可对应多个 run。

## 验证记录

已实际执行，均为 `PASS`（通过）：

```text
mvn --% -pl nq-core -am -Dtest=ShadowRunReleaseBindingPrototypeTest -Dsurefire.failIfNoSpecifiedTests=false test
8 tests / 0 failures / 0 errors / 0 skipped

mvn --% -pl nq-core -am -Dtest=*PrototypeTest -Dsurefire.failIfNoSpecifiedTests=false test
60 tests / 0 failures / 0 errors / 3 skipped

mvn -pl nq-core -am test
406 tests / 0 failures / 0 errors / 3 skipped
```

三个 skipped 均来自既有 `TrustedRootArtifactVerifierPrototypeTest` 的 Windows symbolic-link 权限限制；
本轮新增 binding contract 的 8 项测试没有 skipped。

## 边界

GateW 继续 `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结），GateX 继续 `NOT_STARTED`（未开始），
LIVE 继续 `DISABLED`（关闭）。本草案不触达 soak、credential、交易所、AI 或 DH runtime。
