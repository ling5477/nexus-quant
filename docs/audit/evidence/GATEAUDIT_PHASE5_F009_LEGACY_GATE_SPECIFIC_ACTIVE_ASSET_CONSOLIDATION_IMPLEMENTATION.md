# F009 all historical-stage active asset consolidation implementation

状态：`IMPLEMENTED / PENDING_FOCUSED_REVIEW`（已实施，待一次独立 focused review）。此记录为未提交 candidate 的本地证据，不是独立 review、exact-head CI 或 acceptance。分类：`NQ_ONLY / CI_CD / HIGH_RISK / NO_COMMIT / NO_PUSH`。

## 1. Baseline 与安全边界

- Repository：`E:/Project/nexus-quant-gateaudit`；branch=`audit/post-gatey-agent-baseline`。
- starting HEAD 与 fetched origin 均为 `21d14d9112364f3befcc77a1a370828ef22e72ed`；两次授权的 `git fetch origin --prune` 后一致。初始worktree=CLEAN，staged=0。
- 初始F007/F008=`ACCEPTED / CLOSED`，F009=`OPEN / NOT_IMPLEMENTED`。当前F009 machine status使用既有`IMPLEMENTED|PENDING_REVIEW`，commit=`NONE`，CI=`NOT_RUN`；不修改matcher。
- Phase6=`DEFERRED`；LIVE=`DISABLED`，kill switch=`ENGAGED`。未读取credential material，未调用真实交易所、生产DB、systemd或部署服务器。
- Phase5A immutable pair=`d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`；Phase5B=`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`。F007/F008 pair保持STATUS已有值。本轮不重新评审这些技术接受结果。

## 2. 方法与完整性口径

读取 repository machine policy 指定的 `docs/audit/AUDIT_BOOTSTRAP_CHARTER.md`；整改授权来自用户明确的F009 implementation附件及其全历史stage补充。primary Skill=`nq-dh-workflow-router`；supporting=`nq-docs-writer`、`python-ops-tooling`、`java-backend-maintenance`、`nq-java-engineering-standard`。后两者仅用于实际Spring wiring/caller变更；采用architecture overlay及Spring platform profile，不扩大到交易核心审计。

使用 `git ls-files`、`git grep`、`rg`；完整扫描基线tracked tree共3180文件，其中3138个允许的文本文件。敏感命名的env/pgpass/templates只检查路径与caller，不读取内容；binary/non-text不当作源码。第二次inventory通过`git cat-file --batch`读取同一个HEAD的允许文本，避免删除后的worktree污染before证据。stage从tracked路径、实际Gate index、current authority和内容推导，未从记忆硬编码。

全量模式包含`gate/phase/freeze/qualification/soak/pilot/readiness/release/deploy/rollback/restore/systemd/EnvironmentFile`。广义匹配不是删除依据：稳定的readiness、release、资金FREEZE、业务evaluation gate及历史注释均单独归类。`docs/gates`这个复数目录名不算GateS stage。

- 广义候选文件=1158；分类数=1158；unclassified=0。完整逐文件stage、path/type、runtime属性、reference候选、discovery caller、historical refs、replacement、disposition/reason/owner/trigger/validation见[机器inventory](GATEAUDIT_PHASE5_F009_ACTIVE_ASSET_INVENTORY.json)。
- 物理删除=65；capability路径迁移=24。全部删除文件都有基线Git blob，未覆写历史材料。
- 词法referenceCandidateCount只用于防漏；它不冒充真实运行caller数量。比如文档中的freeze、F008的被拒绝profile和签名scope中的旧identity不等于运行入口。下节给出实际链的解析。
- stage-name active executable/profile/service paths after=0；退役脚本/unit/profile的未解决active caller=0。现有签名/DB/wire compatibility残留有18个source文件，明确`KEEP_TEMPORARILY`，不隐瞒为repository关键词归零。

## 3. Discovered stage identifiers

下表是观察到的identifier集合。GateZ只出现在未来边界/草案与synthetic archive fixture，不宣称其已实施或结束；Phase6为未来阶段。Phase0～Phase5B及Phase4A是当前GateAUDIT的accepted/current audit事实，保留。A～Y历史Gate从实际tracked事实推导。

| Identifier | Matched files | Active-tree matches | Historical-only files | Role |
|---|---:|---:|---:|---|
| GATEA | 67 | 27 | 40 | HISTORICAL_STAGE |
| GATEAUDIT | 40 | 10 | 30 | CURRENT_OR_ACCEPTED_AUDIT |
| GATEB | 60 | 14 | 46 | HISTORICAL_STAGE |
| GATEC | 89 | 46 | 43 | HISTORICAL_STAGE |
| GATED | 131 | 44 | 87 | HISTORICAL_STAGE |
| GATEE | 76 | 26 | 50 | HISTORICAL_STAGE |
| GATEF | 85 | 43 | 42 | HISTORICAL_STAGE |
| GATEG | 33 | 0 | 33 | HISTORICAL_STAGE |
| GATEH | 117 | 43 | 74 | HISTORICAL_STAGE |
| GATEI | 96 | 44 | 52 | HISTORICAL_STAGE |
| GATEJ | 258 | 40 | 218 | HISTORICAL_STAGE |
| GATEK | 158 | 32 | 126 | HISTORICAL_STAGE |
| GATEL | 87 | 25 | 62 | HISTORICAL_STAGE |
| GATEM | 118 | 63 | 55 | HISTORICAL_STAGE |
| GATEN | 59 | 17 | 42 | HISTORICAL_STAGE |
| GATEO | 55 | 17 | 38 | HISTORICAL_STAGE |
| GATEP | 38 | 16 | 22 | HISTORICAL_STAGE |
| GATEQ | 173 | 44 | 129 | HISTORICAL_STAGE |
| GATER | 63 | 36 | 27 | HISTORICAL_STAGE |
| GATES | 91 | 52 | 39 | HISTORICAL_STAGE |
| GATET | 76 | 48 | 28 | HISTORICAL_STAGE |
| GATEU | 33 | 5 | 28 | HISTORICAL_STAGE |
| GATEV | 79 | 32 | 47 | HISTORICAL_STAGE |
| GATEW | 371 | 106 | 265 | HISTORICAL_STAGE |
| GATEX | 146 | 54 | 92 | HISTORICAL_STAGE |
| GATEY | 241 | 113 | 128 | HISTORICAL_STAGE |
| GATEZ | 8 | 3 | 5 | DEFERRED_PROPOSAL_OR_SYNTHETIC_FIXTURE |
| PHASE0 | 10 | 4 | 6 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE1 | 7 | 4 | 3 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE2 | 6 | 3 | 3 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE3 | 5 | 3 | 2 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE4 | 7 | 5 | 2 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE4A | 3 | 1 | 2 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE5 | 19 | 4 | 15 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE5A | 18 | 6 | 12 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE5B | 25 | 8 | 17 | CURRENT_OR_ACCEPTED_AUDIT |
| PHASE6 | 15 | 4 | 11 | DEFERRED_PROPOSAL_OR_SYNTHETIC_FIXTURE |

## 4. Caller graph 与canonical binding

1. **Freeze链（早期GateJ）**：`build-freeze-release.ps1` → compose/env/nginx与`deploy-freeze.sh` → `backup-db.sh`、`health-check.sh`、`freeze-health-loop.sh`、`seed-freeze-user.sh`。原profile仅用于该旧部署模型及no-egress负向测试。整个操作链退役；current CI/RUNBOOK没有该部署调用，API的GateD阻断stub说明同步退役。replacement为已接受的canonical build→external admission→verify→install→activate，以及PG16 backup/restore；不把seed-user脚本复制进canonical。
2. **GateW链**：builder → release contract/verifier/installer → worker与failclose unit → control/run-loop/finalizer → `GateWOkxReadonlySoakCycleTest`及`GateWOkxReadonlySoakFailCloseTest`的standalone main。专用Java main、PrerequisiteMain、support tests和disposable旧restore prepare随整条已完成soak链退役，不将其systemd credential规则改造成prod规则。构建/安装/回滚的长期invariant由canonical已有合同保护；通用kill-switch实现及测试未修改。
3. **GateY部署链**：builder → release contract/installer/deployment orchestrator → qualification unit + readonly YAML/env/target → exact/minimal pilot helpers。旧部署/pilot入口及其专用fixture/regression链整体退役；GateY5旧schema/worker drill与仅由它调用的`IsolatedFakeExecutionWorkerLauncher`/`DisposableWorkerReleaseVerifier`退役。Phase4 current restart/causal proof和canonical current-schema restore没有这些caller，保持原实现。
4. **持续有效的readonly capability**：旧Y YAML与component-scan测试 → 既有`scoped-okx-private-readonly` profile；数据库字段转为`NQ_READONLY_DB_*`，release元数据为`NQ_RELEASE_ID / NQ_SOURCE_COMMIT / NQ_RELEASE_MANIFEST_SHA256`。保留readonly feature flags、禁止mutating adapter、zero startup side effect与loopback边界。没有新增unit、installer或第二生产部署路径；canonical prod只允许prod的既有合同不变。
5. **旧Spring profile与flag**：GateD fallback alias、GateW diagnostics/venue-rule aliases移除；原正式caller使用既有稳定capability identity。旧key仅保留deny-only识别；legacy-only、mixed/conflicting/matching key均不能激活capability。新增BeanFactoryPostProcessor在runtime bean创建前拒绝historical stage profile，覆盖active和default profile，避免删除YAML后落入普通adapter graph。F008 initializer及其回归源码完全不变。
6. **可复用测试**：GateN sandbox fixtures、GateX release manifest fixtures按能力迁移，fixture原字节保持；GateO manual public smoke移除stage开关、tag与runner身份，保持显式手动授权/无默认网络运行。readonly observation tests改为capability命名。JUnit/POM discovery→fixture/resource调用均已迁移，旧路径仅剩历史证据或被拒绝输入。
7. **早期stage当前指针**：删除无consumer的`nq.gated.verify` YAML块；将current GateM/future GateS/GateQ之类runtime指引改为当前endpoint/capability边界。旧GateT work-order失效路径改指`docs/current/API.md`，说明该anchor不授予运行权限。保留公开response code和状态，未新增业务能力。
8. **CI/governance链**：current CI→现有canonical validators/scripts；新增独立blocking step→`check-stage-assets.py`→离线mutation tests。既有required jobs、permissions、pinned actions、canonical validator/matcher/acceptance语义不改。AGENTS/CLAUDE/active Skills与current事实源使用动态authority，historical Gate plans保留原位。

删除前后的unit caller（installer/runbook/deployment/recovery/CI）通过全tracked basename/path与profile/control命令扫描解析；所有旧installer/deployment/recovery caller随其封闭工具链删除。未把历史文件的相对链接或负向fixture算作active caller。replacement provenance统一绑定前述Phase5B immutable pair；当前新的F009 diff不冒充那个accepted release。

## 5. 处置明细

Disposition统计：`{'KEEP_CANONICAL': 1022, 'REPOINT_CALLER': 29, 'KEEP_TEMPORARILY': 18, 'DELETE_ACTIVE_DUPLICATE': 65, 'RENAME_TO_CAPABILITY': 24}`。下表为删除/迁移/当前caller变更；KEEP_CANONICAL与其逐文件理由见机器inventory。

| Baseline asset | Disposition | Replacement/current path |
|---|---|---|
| `.github/workflows/ci.yml` | REPOINT_CALLER | `.github/workflows/ci.yml` |
| `backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/OperationalReadinessService.java` | REPOINT_CALLER | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/OperationalReadinessService.java` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java` | REPOINT_CALLER | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/LocalTestFallbackConfiguration.java` | REPOINT_CALLER | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/LocalTestFallbackConfiguration.java` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/OkxVenueRuleSyncConfiguration.java` | REPOINT_CALLER | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/OkxVenueRuleSyncConfiguration.java` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java` | REPOINT_CALLER | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfiguration.java` | REPOINT_CALLER | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfiguration.java` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java` | REPOINT_CALLER | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/DisposableWorkerReleaseVerifier.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/IsolatedFakeExecutionWorkerLauncher.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/main/resources/application-freeze.yml` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/main/resources/application-gated-verify.yml` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/main/resources/application-gatew-okx-readonly-soak.yml` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml` | RENAME_TO_CAPABILITY | `backend/nq-app/src/main/resources/application-scoped-okx-private-readonly.yml` |
| `backend/nq-app/src/main/resources/application-local.yml` | REPOINT_CALLER | `backend/nq-app/src/main/resources/application-local.yml` |
| `backend/nq-app/src/main/resources/application.yml` | REPOINT_CALLER | `backend/nq-app/src/main/resources/application.yml` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/CapabilityPropertyResolverTest.java` | REPOINT_CALLER | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/CapabilityPropertyResolverTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfigurationReadinessTest.java` | REPOINT_CALLER | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfigurationReadinessTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/OkxVenueRuleSyncConfigurationTest.java` | REPOINT_CALLER | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/OkxVenueRuleSyncConfigurationTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfigurationTest.java` | REPOINT_CALLER | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfigurationTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java` | REPOINT_CALLER | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/GateYReadonlyQualificationConfigurationTest.java` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfigurationTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/GateYReadonlyQualificationProductionContextTest.java` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationProductionContextTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyRuntimeDiagnosticEndpointTest.java` | REPOINT_CALLER | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyRuntimeDiagnosticEndpointTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gaten/marketdata/GateNMarketdataSandboxFixtureSmokeTest.java` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/marketdata/MarketdataSandboxFixtureSmokeTest.java` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakFailCloseTest.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakSupportTest.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWPrerequisiteResultMappingTest.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/DisposableWorkerReleaseVerifierTest.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/GateW4RestoreDrillPreparePostgresIntegrationTest.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/GateOManualPublicOutboundSmokeTest.java` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/ManualPublicOutboundSmokeTest.java` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/binance/public/exchange_status_pending_backend_support.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/binance/public/exchange_status_pending_backend_support.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/binance/public/instrument_btc_usdt_spot.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/binance/public/instrument_btc_usdt_spot.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/binance/public/ohlcv_btc_usdt_1m_fresh.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/binance/public/ohlcv_btc_usdt_1m_fresh.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/binance/public/ticker_pending_backend_support.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/binance/public/ticker_pending_backend_support.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/exchange_status_available.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/exchange_status_available.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/fake_server_unavailable.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/fake_server_unavailable.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/instrument_btc_usdt_spot.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/instrument_btc_usdt_spot.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/malformed_missing_timestamp.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/malformed_missing_timestamp.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/ohlcv_btc_usdt_1m_fresh.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/ohlcv_btc_usdt_1m_fresh.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/ohlcv_btc_usdt_1m_gap.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/ohlcv_btc_usdt_1m_gap.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/ohlcv_btc_usdt_1m_stale.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/ohlcv_btc_usdt_1m_stale.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/rate_limit_429_simulated.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/rate_limit_429_simulated.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/source_disabled.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/source_disabled.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/ticker_btc_usdt_fresh.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/ticker_btc_usdt_fresh.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/timeout_simulated.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/timeout_simulated.json` |
| `backend/nq-app/src/test/resources/gaten/marketdata/fixture-smoke/okx/public/unsupported_symbol.json` | RENAME_TO_CAPABILITY | `backend/nq-app/src/test/resources/marketdata/fixture-smoke/okx/public/unsupported_symbol.json` |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/evaluationgate/StrategyValidationOverviewQueryService.java` | REPOINT_CALLER | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/evaluationgate/StrategyValidationOverviewQueryService.java` |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/papershadowcomparison/PaperShadowComparisonService.java` | REPOINT_CALLER | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/papershadowcomparison/PaperShadowComparisonService.java` |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/PaperShadowConsistencyDrilldownQueryService.java` | REPOINT_CALLER | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/PaperShadowConsistencyDrilldownQueryService.java` |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowvalidation/ShadowValidationWorkflowOverviewQueryService.java` | REPOINT_CALLER | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowvalidation/ShadowValidationWorkflowOverviewQueryService.java` |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/preflight/TradingPreflightReadinessService.java` | REPOINT_CALLER | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/preflight/TradingPreflightReadinessService.java` |
| `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/PaperShadowConsistencyDrilldownQueryServiceTest.java` | REPOINT_CALLER | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/PaperShadowConsistencyDrilldownQueryServiceTest.java` |
| `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/SensitiveFieldPolicyPrototypeTest.java` | REPOINT_CALLER | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/SensitiveFieldPolicyPrototypeTest.java` |
| `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseManifestPrototypeTest.java` | REPOINT_CALLER | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseManifestPrototypeTest.java` |
| `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.golden.json` | RENAME_TO_CAPABILITY | `backend/nq-core/src/test/resources/strategyrelease/strategy-release-manifest.golden.json` |
| `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.schema.json` | RENAME_TO_CAPABILITY | `backend/nq-core/src/test/resources/strategyrelease/strategy-release-manifest.schema.json` |
| `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/GateYReadonlyQualificationObservationAuthorityTest.java` | RENAME_TO_CAPABILITY | `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ReadOnlyQualificationObservationAuthorityTest.java` |
| `deploy/.env.freeze.example` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/docker-compose.freeze.yml` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/gatey/gatey-readonly-db.pgpass.example` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/gatey/gatey-readonly-runtime-target.json` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/gatey/gatey-readonly-runtime.env.example` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/gatey/gatey-readonly-runtime.secrets.env.example` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/nginx/default.conf` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/systemd/nq-gatew-soak-failclose@.service` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/systemd/nq-gatew-soak@.service` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `deploy/systemd/nq-gatey-readonly-qualification.service` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `docs/current/API.md` | REPOINT_CALLER | `docs/current/API.md` |
| `docs/current/README.md` | REPOINT_CALLER | `docs/current/README.md` |
| `docs/current/ROADMAP.md` | REPOINT_CALLER | `docs/current/ROADMAP.md` |
| `docs/current/RUNBOOK.md` | REPOINT_CALLER | `docs/current/RUNBOOK.md` |
| `docs/current/STATUS.md` | REPOINT_CALLER | `docs/current/STATUS.md` |
| `scripts/backup-db.sh` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/build-freeze-release.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/deploy-freeze.sh` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/freeze-health-loop.sh` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gated_okx_dome_verify.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/build-gatew-release-bundle.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/gatew-okx-readonly-soak-control.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/gatew-okx-readonly-soak-failclose.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/gatew-okx-readonly-soak.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/gatew-release-contract.psm1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/gatew-soak-remediation-contract.psm1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/install-gatew-release.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/run-gatew4-disposable-restore-drill.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/tests/fixtures/attempt-09-rejected.json` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/tests/run-gatew-release-reproducibility-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/tests/run-gatew-soak-remediation-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/tests/run-gatew-soak-remediation-security-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatew/verify-gatew-release.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/GateY5FlywayLauncher.java` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/build-gatey-readonly-release.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/gatey-readonly-release-contract.psm1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/gatey5-post-fixture.sql` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/gatey5-pre-fixture.sql` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/gatey5-worker-fixture.sql` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/install-gatey-readonly-release.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/invoke-gatey-exact-pilot-scope.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/invoke-gatey-minimal-live-pilot.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/invoke-gatey-readonly-deployment-contract.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/invoke-gatey-readonly-runtime-deployment.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/run-gatey5-isolated-worker-drill.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/run-gatey5-lock-window-drill.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/run-gatey5-post-restore-drill.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey-exact-pilot-scope-control-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey-minimal-live-pilot-contract-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey-readonly-linux-installation-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey-readonly-release-contract-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey-readonly-runtime-deployment-contract-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey-readonly-runtime-deployment-linux-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey5-lock-window-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/tests/run-gatey5-post-restore-regression.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/gatey/verify-gatey4-worker-deployment-boundary.ps1` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/health-check.sh` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |
| `scripts/seed-freeze-user.sh` | DELETE_ACTIVE_DUPLICATE | `scripts/deployment/New-NqCanonicalRelease.ps1; scripts/deployment/Install-NqCanonicalRelease.ps1; scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` |

## 6. KEEP_TEMPORARILY 的实际边界

以下source仍有当前domain/test caller。它们承载已持久化/签名/公开的policy、receipt、permission或SQL function identity，不是仍可加载的旧profile或第二部署路径。对应的当前caller通过其接口/configuration/repository/DTO以及Maven回归消费；完整candidate列表和模块discovery链在机器inventory中。新旧runtime不会通过这些字符串进行双路部署。

| Retained contract source | Current type callers | Canonical owner | Removal trigger |
|---|---:|---|---|
| `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/AdapterReadinessReason.java` | 5 | nq-adapter-api / AdapterReadinessReason contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/publicmarketdata/PublicMarketDataOutboundPolicy.java` | 6 | nq-adapter-api / PublicMarketDataOutboundPolicy contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/CredentialPermissionProbeRequestBody.java` | 1 | nq-api / CredentialPermissionProbeRequestBody contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java` | 2 | nq-app / MinimalLivePilotConfiguration contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeService.java` | 8 | nq-core / CredentialPermissionProbeService contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/CredentialPermissionExpectation.java` | 9 | nq-core / CredentialPermissionExpectation contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java` | 44 | nq-core / ExactPilotBinding contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/monitoring/application/incidentreview/IncidentReplayReviewOverviewQueryService.java` | 4 | nq-core / IncidentReplayReviewOverviewQueryService contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/pyartifactpreview/PythonEvaluationArtifactPreviewOverviewQueryService.java` | 4 | nq-core / PythonEvaluationArtifactPreviewOverviewQueryService contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerService.java` | 1 | nq-core / ShadowRunRunnerService contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/AdmissionGuard.java` | 8 | nq-core / AdmissionGuard contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunMaterializationWriter.java` | 4 | nq-core / ShadowRunMaterializationWriter contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentFactBundle.java` | 3 | nq-core / OperationalSafetyAssessmentFactBundle contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePort.java` | 4 | nq-infra / OkxRealReadonlyPermissionProbePort contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java` | 3 | nq-infra / MinimalPilotTradingVenueGateway contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java` | 1 | nq-infra / JdbcExecutionIntentRepository contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/MinimalLivePilotControlService.java` | 2 | nq-infra / MinimalLivePilotControlService contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |
| `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java` | 2 | nq-infra / OkxPilotPrerequisiteObservationAuthority contract owner | Separately authorized versioned contract migration with compatibility and stored-fact proof; no unversioned replacement. |

具体约束：`ExactPilotBinding.ClosedPilotScope.RUNTIME_PROFILE`是历史签名scope的一部分；`GATEY_PILOT_READINESS`是既有permission API/事实值；`gate_y43_market_snapshot_digest`是历史migration创建且current repository查询的SQL primitive；Shadow/Admission policy版本参与已有证据/guard identity；operational safety的历史review subject属于证据contract。不能用文本rename改变这些不可变事实。移除其版本兼容需要另行授权的合同/forward migration方案。本轮没有增加compatibility shim或stage runtime分支。

历史API/构建说明、负向regression、固定synthetic fixture、资金FREEZE属于KEEP_CANONICAL的合法语义，不作为KEEP_TEMPORARILY债务混计。旧capability key现在只会拒绝，不再兼容启用。

## 7. Anti-regression contract

Checker：`scripts/docs/check-stage-assets.py`；reviewed exception manifest：`scripts/docs/stage-asset-exceptions.json`；fixtures：`scripts/docs/tests/test_stage_assets.py`。

- filesystem扫描active scripts/deploy/.github/backend、active Skills、current documents与root governance，包含untracked/ignored的runtime输入；排除target/node_modules/build等生成物。
- `retiredPaths`显式登记65个删除路径和24个迁移旧路径；精确退役路径及其中性basename/Java type caller不可使用exception放行，防止中性freeze helper重新引入。
- stage文件路径不允许exception；中性文件名中stage语义同样检查。Java注释、历史叙述不当作运行入口；current文档中的可执行命令/路径纳入检查。
- exceptions逐文件固定非注释内容SHA256、类型、理由、owner和trigger；不是按整个目录或关键词放行。无自动update/accept选项。缺失root、symlink、非法encoding/schema、缺少owner、重复/越界exception、stale exception均fail-closed。
- frozen archive、既有audit evidence、current历史plan及append-only ledgers保持合法。current GateAUDIT/Phase5 authority不被当作旧runtime。
- 当前179项reviewed exception是分类结果；其中旧wire identity保留范围如上。它们不能在保持旧literal时增加新的runtime代码，否则内容pin失效。

## 8. 验证记录

- 初始及当前authority：PASS；最终PowerShell 5.1与PowerShell 7均通过。
- 第一组offline targeted Maven：BUILD SUCCESS；nq-app=150 tests、0 failures/errors/skips；同时执行了core manifest与infra observation相关测试。使用clean避免已删除profile/launcher的target残留；没有Full Maven或真实DB测试。
- 第二组current-caller targeted Maven：首次1个失败（`PaperShadowConsistencyDrilldownQueryServiceTest:127`仍期待旧future GateS文案）；修正同一精确断言后BUILD SUCCESS，未放宽业务状态或副作用断言。
- canonical release contract：PASS / 66 cases，覆盖release verifier、external admission、immutable installation、activation/rollback/recovery和并发/故障边界，使用synthetic temp fixtures。
- canonical delivery workflow validator：PASS / `-ContractOnly`，包含systemd static contract；full admission=`NOT_EVALUATED_CONTRACT_ONLY`。新增CI step后再次通过。
- stage checker：PASS；Windows fixtures=15 tests、1个因本机symlink权限跳过；WSL/Linux同15项全部PASS、0 skips，补齐实际symlink拒绝证据。
- Full Maven、PG16 full restore qualification、frontend、真实systemd、release E2E deployment均NOT_REQUIRED/未运行；生产remote mutation=FORBIDDEN/未执行。

执行命令与具体日志位于本轮`artifacts/f009-*.log`。已有F008源码、F007实现、canonical deployment实质逻辑、Flyway与frontend没有修改。无独立review、无远端exact-head CI，不能声明这些阶段已完成。

## 9. Historical integrity / rollback / next action

历史evidence、docs/gates、archives、published tags和migration不修改；TESTING/WORKLOG只追加。canonical release/install/activation/rollback/restore实现与unit的内容保持基线；仅current readonly capability与治理防回归/callers发生变化。

Rollback：reviewer可针对本轮列出的tracked修改应用相对于`21d14d9112364f3befcc77a1a370828ef22e72ed`的文件级反向补丁，并仅移除本轮新增路径；不要全仓reset/clean，也不要改动baseline之后用户新增文件。本轮未执行rollback。所有删除项仍可从该基线或原published tag读取，恢复不需要改写Git历史。

staged=0；commit=NONE；push=NONE。本轮未运行独立focused review。下一动作仅为`NQ-GATEAUDIT-PHASE5-F009-LEGACY-GATE-SPECIFIC-ACTIVE-ASSET-CONSOLIDATION-FOCUSED-REVIEW`；review通过前不进行提交或接受。

工具声明：使用本地PowerShell/Python/Maven/Git/WSL；functions工具用于命令与文件patch；未使用外部应用MCP。网络只用于授权的origin fetch；无credential/server/真实交易访问。写操作限定本轮candidate文件与本地验证artifacts。

## 10. 最终数量口径与验证汇总

- Discovered identifiers=37：25个历史Gate A～Y；另含GateZ草案/fixture、当前GateAUDIT及10个Phase标识。它们不是37个已结束阶段。
- 广义active-tree候选=1158，classified=1158，unclassified=0。Disposition：KEEP_CANONICAL=1022、REPOINT_CALLER=29、DELETE_ACTIVE_DUPLICATE=65、RENAME_TO_CAPABILITY=24、KEEP_TEMPORARILY=18。
- 退役旧路径=89→0；其中control-plane资产=39→0，包含3个被入口依赖的psm1合同模块。去除这3个模块，profile/compose/unit/script/standalone-main入口=36→0；完整路径集合见机器inventory的controlPlaneAssetsBefore。其余退役项为配套模板、fixture、专用测试或support类型。
- 基线exact path/basename/Java-type引用：49个active caller文件、195条caller→asset边；包括packaging、regression与current API指针，未解决after=0。逐边resolution见retiredPathCallerGraph。另有1条shadow-baseline历史静态记录，明确保留，不作为可执行caller。此口径不把Spring profile selector、命令运行次数或通用freeze词法命中混计；profile选择器、deny-only输入与signed scope的处理见第4、6节。
- 全部11个基线application YAML已单独归类：common=1；local/test/ci/prod稳定环境=4；paper稳定模式=1；manual public-market-data显式capability=1；旧stage profile=4（删除3、迁移1）。当前8个application YAML；其他稳定profile未重构。
- KEEP_TEMPORARILY=18个既有版本合同source；它们仍有真实current caller，不宣称所有stage字符串归零。每项owner、移除触发及无第二部署路径证明见第6节与sidecar。
- Windows/Linux active checker：scanned=1797、reviewed_exceptions=179、errors=0；15项负向/边界fixture结果见第8节。canonical release=66 PASS；canonical workflow ContractOnly PASS，full admission仍为NOT_EVALUATED_CONTRACT_ONLY。
- targeted Maven第一组共160 tests，第二组成功重跑共143 tests；均0 failures/errors/skips，均为offline targeted执行。第二组首次精确文案断言失败保留在失败日志，修复后的rerun是最终证据。
- Governance：PS5.1/PS7 authority PASS；next-action failed=0；agent workflow fixtures=12/12及恶意/负向用例拒绝；lifecycle=20/20；doc links checked=287、warnings=123、errors=0。warning来自历史/ledger链接，未改写历史以消除warning。
- Protected diff：既有docs/gates、archive、audit evidence、Flyway migrations、frontend、canonical release/deploy/restore脚本与unit修改=0；F007实现与F008 initializer/regression/prod YAML/spring.factories/validator修改=0。18个迁移fixture资源在统一CRLF后逐字节一致；TESTING/WORKLOG保留基线完整前缀，仅追加。
- historical evidence modified=0；canonical path duplication=0；staged=0、commit=NONE、push=NONE。P0/P1未发现（implementation自检口径），不构成独立review通过；唯一后续为FOCUSED-REVIEW。

可重复验证命令：

```powershell
python scripts/docs/check-stage-assets.py
python -m unittest discover -s scripts/docs/tests -p test_stage_assets.py -v
pwsh -NoProfile -File scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1
pwsh -NoProfile -File scripts/ci/Test-CanonicalDeliveryWorkflow.ps1 -ContractOnly
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1
pwsh -NoProfile -File scripts/docs/check-current-authority.ps1
pwsh -NoProfile -File scripts/docs/test-current-authority-next-action.ps1
pwsh -NoProfile -File scripts/docs/test-agent-workflow-fixtures.ps1
pwsh -NoProfile -File scripts/docs/test-governance-workflow-lifecycle.ps1
pwsh -NoProfile -File scripts/docs/check-doc-links.ps1
git diff --check
```

工具环境记录：证据更新helper首次因Windows默认GBK读取UTF-8失败（exit=1），显式UTF-8后完成；最终PS5.1复验首次因本机ExecutionPolicy拒绝加载（exit=1），使用仅当前进程的`-ExecutionPolicy Bypass`重跑，不修改系统policy。退役caller模式合并并缓存，保持相同路径/类型边界，避免每个source重复编译。

最终边界补充：新增第15项回归，证明小写GateS的`application-gates.yml`、neutral Java中的`@Profile("gates")`以及脚本模式分支均拒绝；只有真实`docs/gates`历史namespace被豁免，避免将复数目录特例扩大成小写stage旁路。

Targeted Maven等价复跑命令（suite列表从本轮成功日志提取；不执行manual outbound测试）：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am "-Dtest=com.guidinglight.nexusquant.app.architecture.PackageBoundaryArchTest,com.guidinglight.nexusquant.app.config.CapabilityPropertyResolverTest,com.guidinglight.nexusquant.app.config.ExchangeAdapterConfigurationReadinessTest,com.guidinglight.nexusquant.app.config.OkxVenueRuleSyncConfigurationTest,com.guidinglight.nexusquant.app.config.RetiredRuntimeProfileGuardConfigurationTest,com.guidinglight.nexusquant.app.config.account.OkxPrivateReadOnlyDiagnosticsConfigurationTest,com.guidinglight.nexusquant.app.config.account.OkxPrivateReadOnlyPermissionProbeSpringContextTest,com.guidinglight.nexusquant.app.config.env.ProductionSecretProfileRegressionTest,com.guidinglight.nexusquant.app.config.livecontrol.ReadOnlyProviderObservationConfigurationTest,com.guidinglight.nexusquant.app.config.livecontrol.ReadOnlyProviderObservationProductionContextTest,com.guidinglight.nexusquant.app.config.livecontrol.ReadOnlyRuntimeDiagnosticEndpointTest,com.guidinglight.nexusquant.app.marketdata.MarketdataSandboxFixtureSmokeTest,com.guidinglight.nexusquant.livecontrol.infra.ReadOnlyQualificationObservationAuthorityTest,com.guidinglight.nexusquant.strategyrelease.preparation.SensitiveFieldPolicyPrototypeTest,com.guidinglight.nexusquant.strategyrelease.preparation.StrategyReleaseManifestPrototypeTest" -Dsurefire.failIfNoSpecifiedTests=false clean test
mvn -o -f backend/pom.xml -pl nq-app -am "-Dtest=com.guidinglight.nexusquant.app.config.RetiredRuntimeProfileGuardConfigurationTest,com.guidinglight.nexusquant.app.config.env.ProductionSecretProfileRegressionTest,com.guidinglight.nexusquant.app.config.livecontrol.ReadOnlyProviderObservationProductionContextTest,com.guidinglight.nexusquant.runtime.api.OperationalReadinessBoundaryTest,com.guidinglight.nexusquant.runtime.api.OperationalReadinessServiceTest,com.guidinglight.nexusquant.runtime.api.web.OperationalReadinessControllerTest,com.guidinglight.nexusquant.strategy.api.web.PaperShadowConsistencyDrilldownControllerTest,com.guidinglight.nexusquant.strategy.api.web.PythonEvaluationArtifactPreviewOverviewControllerTest,com.guidinglight.nexusquant.strategy.api.web.ShadowValidationWorkflowOverviewControllerTest,com.guidinglight.nexusquant.strategy.api.web.StrategyValidationOverviewControllerTest,com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryServiceTest,com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonServiceTest,com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewQueryServiceTest,com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownQueryServiceTest,com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewQueryServiceTest,com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReadinessServiceTest" -Dsurefire.failIfNoSpecifiedTests=false test
```
