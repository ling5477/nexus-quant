# B5 Precise Delivery Manifest

任务：HIGH_RISK / PRECISE_DELIVERY / EXACT_HEAD_CI / TECHNICAL_ACCEPTANCE / NQ-only。

本页在交付前冻结精确清单和来源，不预先声明CI通过。最终接受以这次交付commit自己的canonical CI headSha、九个required jobs完成成功、remote/HEAD一致和clean worktree为准；最终commit/CI pair记录在本交付任务结果，避免CI后追加docs commit改变exact-head身份。

## 候选推导与授权

用户明确授权一次性提交完整已接受B5 capability cluster并push当前branch；没有production/LIVE操作授权。本轮技术delta=0，不重新运行B5 qualification、Independent Reviews、Full Maven或B1–B4 matrices。

起点branch=`audit/post-gatey-agent-baseline`，HEAD、origin tracking及GitHub实际remote均为`86c8ad84542636364f6c21e78bc292a323cbdff7`，stage=0。STATUS机器区块仍为旧pre-B0；本轮依据用户明确发布授权，不修改current authority或把旧区块当新的技术事实。

唯一清单从以下现有canonical事实逐层派生：

1. [effective quantity starting inventory](l4-b5-effective-quantity-remediation-attempt01/starting-files.json)包含先前V49/venue/recovery/V50/V51与import整理的候选及历史证据；叠加[最终scope](l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json)及[final identity](l4-b5-effective-quantity-remediation-attempt01/final-identity.json)列出的107项最终证据。
2. [qualification candidate integrity](l4-b5-final-qualification-resume/candidate-integrity.json)的64项delta与两项明确metadata exclusions，包含最终qualification harness、exporter及证明；[独立V51接受原答复](l4-b5-final-qualification-resume/reused-review-original.txt)和[新增证明独立审查](l4-b5-final-qualification-resume/independent-proof-review.md)只复用接受事实。
3. [stage-assets根因整改](GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md)的最终五文件摘要加该证据自身；覆盖本会话上一轮独立接受的六文件整改。

合成3862个当前非忽略文件身份，missing/unexpected/mismatch均0；取其中相对HEAD的差异，得到727个既有交付文件（268 tracked modified、459 untracked intended）。加本页这一交付证据后，唯一清单为728文件（268+460）。并非以git status的所有dirty文件自动授权：每项都先由上述canonical来源绑定，再与当前工作区交叉验证。AGENTS、前端venue映射和广泛import整理均已在已审候选链内，本轮不新增修改。

## 技术身份

[最终已审Full manifest](l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json)1818项，最终qualification的1631项production/config/frontend manifest逐文件mismatch0；Full manifest只有两项已记录qualification exporter/test delta，生产无差异。原绿色Full Maven为1898/0/0/121 conditional skips，仅复用，不代替exact-head CI。

V1–V48与起始HEAD一致；V49/V50/V51同时与最终已审production manifest一致。TradingVenue、ordinary authority、recovery、admission、durable work、same-run Order与effective quantity的全部当前生产文件均在该逐文件核验中。

## 本地预检事实

- stage-assets：scanned1880 / reviewed_exceptions173 / errors0；生命周期重新生成policy与现有registry完全相同，未apply、未更新hash/exception。manual workaround0 / new batch exception0。
- synthetic exporter8/8 PASS；只读检查387个B5 JSON、40963个canonical已知identity/reference字段，UUID/长hex身份残留0；最终51份indexed proof的SHA256与inverseEquivalent/uuidLeaks记录一致。
- pinned Gitleaks、secret negatives、staged candidate检查在提交前实际执行；未运行前不把本页当成功结论。采用当前lock/config和safe-file规则，不新增B5例外。
- Git按本页JSON files精确暂存，禁止全目录add；staged路径必须与清单一致，并比较raw bytes或仅Git CRLF/LF归一化后的字节。技术文件不修复，只有staged diff检查发现的evidence/docs纯空白问题可按用户授权规范化且另存前后身份。
- canonical CI来自`.github/workflows/ci.yml`，required九项由`Test-CanonicalDeliveryWorkflow.ps1`的requiredJobs map声明。当前push trigger仅dev，因此本audit branch发布后需对准确ref执行现有workflow_dispatch；不改workflow。

P2 ordinary concurrent INSERT loser、P3 wildcard-import均保持OPEN / NON_BLOCKING，不在交付顺手修复。B0–B4沿用ACCEPTED；B5当前CORRECTNESS_QUALIFIED/DELIVERY_READY，仅exact-head required CI全绿后接受。下一动作B6 aggregate qualification acceptance，未授权在本任务重跑新矩阵或生产开发。

## 唯一机器可读exact delivery manifest

sha256为交付开始时原始worktree字节；此页自身sha256=null仅避免递归，最终Git tree绑定本页实际字节，不是绕过staged路径/内容检查。临时raw/log/snapshot全部位于已有ignored backend/nq-app/target，不进入提交。

```json
{
  "baseHead": "86c8ad84542636364f6c21e78bc292a323cbdff7",
  "canonicalCandidateFiles": 3862,
  "deliveryFiles": 728,
  "trackedModified": 268,
  "untrackedIntended": 460,
  "unexpected": [],
  "missing": [],
  "mismatch": [],
  "reviewedProductionFiles": 1631,
  "reviewedProductionMismatch": [],
  "reviewedFullFiles": 1818,
  "documentedQualificationTestDelta": [
    "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/synthetic_evidence.py",
    "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_synthetic_evidence.py"
  ],
  "v1ToV48Unchanged": 48,
  "migrationIdentities": [
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V49__ordinary_place_authorities.sql",
      "sha256": "ebea77f85cae76d88e052a99b647573ec3b6ba5ea0464f9083012aac2a883b93"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V50__strategy_window_admission.sql",
      "sha256": "ca87e2b7b0c739b8fae59d701bf0ff54336ee3d262eb5ae7dcd7292edfc45ae6"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V51__strategy_run_durable_execution.sql",
      "sha256": "afbc3211b824b8f717912707b584d2382f47fe9f8df86802e7c4a1c8a6cd9942"
    }
  ],
  "files": [
    {
      "path": ".agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md",
      "sha256": "19afabe58080949b6db82830a5bfb1b3926db3717d100e4bb3a23fa578b0f9a1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md"
    },
    {
      "path": "AGENTS.md",
      "sha256": "9039e3c8b15f4c6fa636bdc5e74b9597b4a35dd0a0e2a21e8dff6bbb3f3e142a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/AdapterOrderNormalization.java",
      "sha256": "7f723cbaff8d70cb4e63032819d8810c99779948a6b4680b8837a18aca80c3d1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/service/TradingAdapter.java",
      "sha256": "0bf384e9c332e3b79a9efd7a66ce6d51110236d663c3d19579b3cba20465e51d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-adapter-api/src/test/java/com/guidinglight/nexusquant/adapter/api/publicmarketdata/JdkPublicMarketDataOutboundClientTest.java",
      "sha256": "35de3a2e93d00105aee9eab3968f20d6accaa12ec684924a0c54f3d52797884b",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-api/src/test/java/com/guidinglight/nexusquant/adapter/api/publicmarketdata/PublicMarketDataOutboundPolicyTest.java",
      "sha256": "9a49ddf6ac2a5bafe0a99d83d643a791bc65fd013c87ec687f2092e654dab3a7",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-api/src/test/java/com/guidinglight/nexusquant/adapter/api/service/DefaultAdapterReadinessServiceTest.java",
      "sha256": "53c35efa7df7afac506fcad501fe6da9226163c36c5e6b9825106e1a7faaff8d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-api/src/test/java/com/guidinglight/nexusquant/adapter/api/service/ReadinessGuardWiringTest.java",
      "sha256": "4a54a2ae283585b9234c34b16e8f90f0af5dc619e8fe296cf343ea6145eaf802",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceExchangeAdapter.java",
      "sha256": "05b83587dbc4f47781442f5bb1534c40ff307ebafbc1522978d1232fad90fbe6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java",
      "sha256": "d5d13cc10b7c298d95b8a247b1c98e5dfc5d775a008677a6f747df9c6c6f8704",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClient.java",
      "sha256": "0eb1f3efc4f3be3ccf33bd3bc41d17cabff136386d9e876d6da2d1e4e3c8cda3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceExchangeAdapterTest.java",
      "sha256": "d05f03560da994e23a657d9d587279bac56450441666eb56d63f08a64f9316bb",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceExchangeInfoClientTest.java",
      "sha256": "26cbc0a0e06357823759b6e759ef5f3a2774a491ae7da67cd46c2a269b3a47e8",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceOrderTrimmerTest.java",
      "sha256": "72498008db8c467da8566400adb42f843edc035d92ffa061f6481c2d22e24e46",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceSynchronizedTimestampProviderTest.java",
      "sha256": "2356971ad06360ff8695c17c3035c8739d045de668d6ae04d7a50a79d42acb5a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClientLiveDiagnosticTest.java",
      "sha256": "3d1130f07b07d9a5447cbed9dd94a619ba7c91d19171994261e6c16f58721571",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransport.java",
      "sha256": "cc059f496967b4736c2cf6d95393343271570e5924add1672796ee13e3e331a0",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapter.java",
      "sha256": "4c18746488f3514fb546df16acc80cd9b057c42c086f2ede6ddd22cfd08a0194",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java",
      "sha256": "d842c07463da27ae8465564ea738f7a24e436792ae4e83b06b78107a93952c5b",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSigner.java",
      "sha256": "f20a78b219ac8c04168255f909bbbd82c9eccf41b3735cd1753c4988d7817e95",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderAdapter.java",
      "sha256": "4f04495c0ca71240d31ace09c3a8ae39cba48110f1f04ee853e9bfd15d7e2f86",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderTransport.java",
      "sha256": "8ee4802ef95e1f4bc2b7e2b3066381d291101f8a531eee7098ecb2ef7c2193ab",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxVenueStateTranslator.java",
      "sha256": "e1b453a5b2ada4040b1b7843adf1515d384d67475141055b2a1305d9ea462417",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxWsClient.java",
      "sha256": "643b52370f732de0ed436edeeaa60ebf09172ef01f9da703cdbe52a20fc21d4c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransportTest.java",
      "sha256": "50e7e938961bab2d48ad13cae449277400109cb7ce686c5f884ae4832d0d5fbc",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxRealTransportTest.java",
      "sha256": "769d89da50009bb6fbdaa0eb835ea127c3a0b906952dc8e0d7ea3aa917d26cbb",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxEffectiveExecutionContractTest.java",
      "sha256": "1eb0bb8637fb965eaf918f3a98fc3e3f9087617aeb85d3675106315831a2c737",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapterBootstrapNoOutboundTest.java",
      "sha256": "42c0cb2b40d7f94c5c03224be85a344433721a0744c4482469c9a2814a22e7cf",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxInstrumentsCacheTest.java",
      "sha256": "e04a03b9d86b0ee176a5d8a730efc55140ed22d5c8d0339bada870512106f25e",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderAdapterContractTest.java",
      "sha256": "5d8f5c7f8d53ff99d3396f8cb35b6aaf9184bade3b321279bc5cf68fd5d940af",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/BacktestConfigController.java",
      "sha256": "cd12b8c4f7d21afe42808702f8ebb4df6ce87252261e0b2be717e48c2e5745e8",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/OperationalReadinessService.java",
      "sha256": "67edd32fa4e79d7de548aa13057d488c92fd5b60d717f9c7e655dbaf5bd55c4e",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/web/OperationalReadinessController.java",
      "sha256": "f059ab3749cbb36fd43e81d3df0e064f7e0c0071e105c92a6c74e6a0867e6a2a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/TradingVerificationController.java",
      "sha256": "dda922ec772218b0a68464b538140012b11588a69ac1c8596a053d451e63fe33",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountControllerWebMvcTest.java",
      "sha256": "15964816169b437880d0e107782f462244f2f03b628553533af673f0ee2162f4",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java",
      "sha256": "e92c0c1876c3366ac0d874f92301b0afcf025968ae491e1fd8bdeb3acd54938f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/adapters/api/web/AdapterReadinessControllerTest.java",
      "sha256": "41e1ed06af4aa7226c30b761f786f1874a76fcc3c2497ace58194dfd5f99895f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeControlPlaneControllerTest.java",
      "sha256": "59d0a0ed410dfd6e0cd2a196070b36513211dee2c14db20ea9eeaf1e3c502869",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/marketdata/api/web/InstrumentCatalogControllerTest.java",
      "sha256": "20e237653668cf96321b56965e687f1ebc5f9373e9c620391e09ae7232649421",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataControllerTest.java",
      "sha256": "bf5cb8e6210e2edabbe7f8d3cf8b7083cdca8a1e272040806d6ffab50f351203",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/monitoring/api/web/IncidentReplayOverviewControllerTest.java",
      "sha256": "1330f1079865d87ddfc3609e4e17d1e4d6a7db1fd853c3c1e541be5d582fb86c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/monitoring/api/web/IncidentReplayReviewOverviewControllerTest.java",
      "sha256": "36c88f36159fc543a819170a20581b7fac5f03c738f8c1b6bf8c152830bd8607",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/paper/api/dto/PaperAutoReviewResponseTest.java",
      "sha256": "76e91dd9ceef962e48384871031bba13e5c1d46d04e50837d3383eb476d01922",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/paper/api/web/PaperAutoReviewControllerTest.java",
      "sha256": "ee644cc2383841a18f94b4788b2d77fe5b4cdc00cdadecd5e51adc80f68ef951",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/paper/api/web/PaperExecutionDiagnosticsControllerTest.java",
      "sha256": "ad8e7ec37aeae80e5509ecc0e8e8b8376e2d662fc8c3808422bbfc230abeb97f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/paper/api/web/PaperStrategyEvaluationControllerTest.java",
      "sha256": "badbe37756a8d597510a4fcdf66e5efc4fefbc8e091227c6ff7f796d1e4eed87",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestConfigControllerTest.java",
      "sha256": "543a6335052bea4a446a2fea864bde88016caea1906874606a36cdec6654057c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestRunControllerTest.java",
      "sha256": "d857de499a5fd4ffd60cce1da92e8e94b85eac02eaadc379f72e5cbfba15c3c1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/PythonEvaluationArtifactBindingPreviewControllerTest.java",
      "sha256": "3426e5a94f626a5069b4537cc8fea2f65add96f7e18ee62db368793a7757914e",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/ResearchConfigControllerTest.java",
      "sha256": "494accefc864153375d09637bb5a7b1e7a129618383eec3ac38e72f4644ec974",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/runtime/api/web/OperationalReadinessControllerTest.java",
      "sha256": "0461627b7ce0f0650c26c4a0c14a69833cb959ff4464858d32e4a97d246b6a8b",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ConsistencyEvidenceOverviewControllerTest.java",
      "sha256": "ee2fa8f77f035dff6110a453976d4033b8d6eafc3edabaff17982533d12441c7",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PaperShadowComparisonControllerTest.java",
      "sha256": "bfd2cca61212ab1ad8a37f1099258c3bd86ce076d6a616a3f7efab0fab74fb8f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PaperShadowConsistencyDrilldownControllerTest.java",
      "sha256": "d50729a899000fe04166cc741f51cc20173b7482e28e77566ef27bd1dce71174",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PythonEvaluationArtifactPreviewOverviewControllerTest.java",
      "sha256": "596cdd513f06e86b161a2a5169f652d3ae80111736c29283696b836576a3ca08",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowLivePreviewControllerTest.java",
      "sha256": "8af4d5d61431acfd1d3c7fb17b0ddd85928aed3e262fb6087c6e59e225db0215",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyControllerTest.java",
      "sha256": "d24e1ec64aa27a5496fbbc534f97634d2770638164054b6dd90297e3fc891e15",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyResponseTest.java",
      "sha256": "97d57d6cb26f3f1b66545153a5107390b713ddd535bcba5d00d7beb403bf4213",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowValidationWorkflowOverviewControllerTest.java",
      "sha256": "5ed59e514bb2edb30038d7b3dea5e6e8bfe1c4db27159241db6779eb883c01a8",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/StrategyEvaluationGateControllerTest.java",
      "sha256": "27a9b6f8a794e788cda4b331ff82d3c5e265aea170c9f51f6ad779dad91384af",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/StrategyReleaseAdmissionPreviewControllerTest.java",
      "sha256": "69e5fddb9bc2a38e42217c5967983bd2eea2afe751142d2dab22f94cc5c16d5c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/StrategyValidationOverviewControllerTest.java",
      "sha256": "50de3db3b847dabc2e7ba432271e9621a159e25bb94e9ab439c6c0528f6d08f2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/validationoperations/runtimeevidence/ValidationOperationsRuntimeEvidenceOverviewControllerTest.java",
      "sha256": "1aca749be021a6717b8eaf8d7bf006d210d00935702c13c0f323522fbff91434",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/trading/api/web/TradingPreflightControllerTest.java",
      "sha256": "a91477ffd97aeafbe275f896e471177739362d63ce32a038ea16ce76b320e3b7",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/validationreview/api/web/ValidationReviewControllerTest.java",
      "sha256": "5e939663054776c4b2840b88870e8e5ca7c70d630fecf4b92aa40282e647c16c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/SecurityConfiguration.java",
      "sha256": "7e82d9a5bc4c3722dbdf324a682b2094ce74998bf0a86989c6b156db97321780",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/EnvSafetyGuardConfiguration.java",
      "sha256": "09bdfb9adc05a6c91f2052d2eef9587568494f7f22b660e249ff41b20932f985",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ExactPilotScopeCliConfiguration.java",
      "sha256": "1a9775c6a214c278a6bda61ef5d85cd55b8ed163701ab724fc3998956578820d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "sha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java",
      "sha256": "97944c884fe7442db756b3b4cecaf4e8f62e16f872e67769013feb024dfd79ee",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/trading/TradingRuntimeConfiguration.java",
      "sha256": "01ea04baeb26cc16205eabbca6b19039624d4fa9ec1a31f2fd381b79210a75bf",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/DisposableFakeVenueLauncher.java",
      "sha256": "7f20de2fc1eadfee4866560174d2a6129d7040efbc464c38751bb4ec52527a53",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionGuardedMaterializationPostgresIntegrationTest.java",
      "sha256": "85e588afbb068ae5807fa3afeb5f30555cc8c5164a6cca66da1751d498d8a216",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionMaterializationGuardPostgresIntegrationTest.java",
      "sha256": "30f2f160fcc4376c5e55603ae582982bf68240cbbcf18d5d8cc42009cb0dccc2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java",
      "sha256": "7896fbc1f2e00a6b918e029d574adfd22db4f1a977e6ca7bdb4244389bc08543",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/OperationalObservationConfigurationTest.java",
      "sha256": "c4cdc11781d12bb154fbc3f4a13387c260e296f4114cb423596fce896f423e0c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java",
      "sha256": "23d8e77e14165fa5fde0281197e981e2d1e05323e0b1460f88f1498c33655b90",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/auth/SecurityConfigurationContextTest.java",
      "sha256": "fc6b901db2536056ac070d956309dc2aa2f499bd84faf30a07dbc43214ce7291",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializerTest.java",
      "sha256": "fd5c5adc64c8cf6e52ed9746668de3c72353daef2007ab1109b47114bdf61323",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java",
      "sha256": "459c6edd7602ee46eb54c9b3d3f662f3e4ce42359ae261e4c0ac231d9c467f56",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfigurationTest.java",
      "sha256": "153d580715399d921276832a224ebf367ffc8b551677eacabc079291af989ca2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration0/NqDhIntegration0ContractValidationTest.java",
      "sha256": "62f370c1ec2048650360ed840ae1bc2fce733137c5258874bd3842f32730b3d3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration0/NqDhIntegration0SecurityContractTest.java",
      "sha256": "096a642789c0a4048c1ad7b122e831109f90e66f733bc1af3e044efb34e0aec8",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration0/support/Int0Signing.java",
      "sha256": "448d9299c09886807655c70f42027f144740e1c20dfe78697ceab7df003147c2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java",
      "sha256": "fab3d78fd3c3a30973def8d3ee3b951a8f2b307a5ea6bf4dcaadd21f3265b60a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/OperatorPilotAuthorityPostgresIntegrationTest.java",
      "sha256": "320cfe630d5a100080ec2da9d025841da9bba38b77840702c0e0076ee863f513",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java",
      "sha256": "711cdf7a098bd1da9ed1c834089ec39dd66e27e2cefe256375314c60cdc404b0",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/KillSwitchRestartDurabilityPostgresIntegrationTest.java",
      "sha256": "52ea42a4571fada943b9f4579a5127e90253090c6b59ff05aec17462e51577a0",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/shadowrun/ShadowRunProvenancePostgresIntegrationTest.java",
      "sha256": "a3a17f38066f79c21cda08c1876fbf1b698b5e3fcf854e86d42b71dda44e9bb1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0Fixture.java",
      "sha256": "383ca2c20c26de314bc8bdeb61a18374d335b2b9a8c4ab7270c9399958f58b30",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0FixtureSafetyTest.java",
      "sha256": "7e29b4b1135d77e8e60a2595fbba70503f1a0f662fe031311687b0cddc480dfc",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0NqProcessMain.java",
      "sha256": "75df6853a4fdc5278fa9702eb01f5c49d5c72078cf927846e499d1987acbc3de",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0Processes.java",
      "sha256": "f5af9d311db9fbeeeea24ecaaddc4727b1824eaf7a9b2cd8ad2734a8589057a5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0RealProcessHarnessTest.java",
      "sha256": "7a76643f7ec31a21345b0dc5ecbc5ba61ad72f8adb010768963c3bfd4c3094da",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0SyntheticVenueMain.java",
      "sha256": "e51809fc1bb2a1e6b8b26a4ff646d940c7e0c838d3ea5dab90e436bbde88935f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B1RealProcessProofTest.java",
      "sha256": "449761eeb5f03d10d13ab3eb471c25b94099491776d4a765452a78ecb428da12",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2RealProcessProofTest.java",
      "sha256": "7a7f36f90aaff9c3efe313543103a44f25ba9f0e52685d7a6dab1cee9543d22e",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2SyntheticVenueMain.java",
      "sha256": "fdb2fb0ff30be352176330986923c818d71f6505524bd46930b4d59bcb8e71fa",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2TerminalCorrectionPostgresIntegrationTest.java",
      "sha256": "ff4cee50a5a94191fdab131ae35922c1f817c096d80bd1c8a72e1a6a481650d6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B3RealProcessProofTest.java",
      "sha256": "36ba529f3639a6eff7be4e3dda4f223987e70b506531cd45dd68fbd9560b21bf",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B4PgWireProxyMain.java",
      "sha256": "b8f9540c059dde7b067d75053219459d0a935d6d42263fe36e78c373799dd3d3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B4ProcessFaults.java",
      "sha256": "18ba59565f9fdf80c70c267e146acbde07b8b5fac5870b5b8fa798f952b9409c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B4QualificationResumeTest.java",
      "sha256": "6e4efb44e5b9733fc1d5ddde1aba381fa19aa6433b78d775350edaf66385a376",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B4RealProcessProofTest.java",
      "sha256": "079f21821ffce2ca6e39f5f4f091dc3d7d61cbc060bc4339e3b1728a093bd3d2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B4TradeEventPostgresTest.java",
      "sha256": "20816af2eb1f8267bc8c9d9784d74013b519d392822bc355c62ed464283bff62",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B4TradeEventRemediationTest.java",
      "sha256": "fa5cbfad6b8f525d93c645086e856cb2603bba18dc776ab68457da5586ae8774",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B4TransactionFaults.java",
      "sha256": "68adc2eaac4804816a2d2eb4254c554dac943497788c7b4fc6ea3981b62d389b",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5AdmissionPostgresTest.java",
      "sha256": "0b615d839c9a9d315649eb1206cc60348b38963da86cfef93409ace6d238cbfc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5AdmissionProcessTest.java",
      "sha256": "97b6d075d5f077bec146f8aa559517a3f0d54cf1fbd43b283a656c1c8b3141ce",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5AuthorityPostgresTest.java",
      "sha256": "f315004801ac25c1cf07ea3533d007da4b2041f7138811e9215fcd9b71905220",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5AuthorityProcessTest.java",
      "sha256": "c7a07c244a0c42c28298cc94e084b24499d8b01c1f239bc3b4b9f2fe569d777b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5DurableLifecycleCrashRecoveryTest.java",
      "sha256": "18a9e25eae6571d7be4e9a1e0938675e5c3d5cd913e65353beaf677ebb02b219",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5EffectiveQuantityPostgresTest.java",
      "sha256": "6b58a25b5adc3356143f7ab8c803caa49383f7cc0a3edc23743e21ac75fc616d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5EffectiveQuantityProcessTest.java",
      "sha256": "ebfb65772809d1d7f5d1ffda0792bb6e425fd761aa4345ec6e0fcdb9b2c7638d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5FinalQualificationInteractionTest.java",
      "sha256": "4986dd36809d2e8ddec5dcda1eab0bd25c6ee8b5225e5dfe84fb7a9aa8912310",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5LegacyVenuePostgresTest.java",
      "sha256": "4c3948dd3b4d4bd4429ad53c1a256c513a4e855da324f79df4876f20cde02bf6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5PreSendBarrier.java",
      "sha256": "438942d6819f85b3aa3203e90aa97da74852609bf9bbf303d2cc9177d83fe04e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5QualificationControls.java",
      "sha256": "8bcb5406633f0cbd956a7f87e4d3d1d43841d2d88826d9ae990bf35aa2d86f8f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5QualificationResumeTest.java",
      "sha256": "ce5a08aaf068ef81fcc80001f8bad59e5b46b165b97ea88a4bd400f2d124979d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5RealProcessProofTest.java",
      "sha256": "f2fe6dac19fc2113d83c2b186cc3734e153ebe566d49f523a869137d55e4c958",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5SchedulerNqProcessMain.java",
      "sha256": "64cdd2ee68c4169d9c6f8b28f43e8c15bc2a441e504437dacef0125312d8eb06",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5StrategyOwnerDeathTest.java",
      "sha256": "b6d14b3e8df6e7f5e19ee26ebfb1654d523f5dae9c436cdc69e3f3572dcd0040",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5StrategyRunRecoveryPostgresTest.java",
      "sha256": "e79ab8a0ecac162ff2b70a2c3f3fb0b645002ecd918c6bff528a804270fa61c7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5StrategyRunRecoveryProcessTest.java",
      "sha256": "64ad4e8609a7bf961b10824cc78d83e6ed0a18e11b353b40b7407c5e1df26e00",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5StrategyScanConcurrencyTest.java",
      "sha256": "f5f5f805cfcc3ab1810c1c1ae54ee7580c7d6d404fb1d5f954b5f47e1b13972e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5V51CommitProcessTest.java",
      "sha256": "ff38e2858a2513a5f098e9262eff8f2379a7c5fb0b79e1642516b204f790c67e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5V51LegacyPostgresTest.java",
      "sha256": "fd43d758e8a0e17e97cd5535118293f4f3321a33af4436d94137a543d1abfad9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5V51NqRecoveryMain.java",
      "sha256": "96d93945f41c100e037e183cb2d531d92768f2cc847b0648e2ecbf47c39eefd9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5V51RecoveryProcessTest.java",
      "sha256": "45e6f9ce5ac3b4022732b9f54ccd9bef07f1860935743f3167b2d6d0c67cb1a8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5V51RecoveryTickProcessTest.java",
      "sha256": "b297f2352c4b152d70a43a1855d5f39c7fdd6942e09f05055550863529e11abe",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5V51SchemaPostgresTest.java",
      "sha256": "ef57b5433f8e7d9b8196c5747ffd5db98f08ebadfb246c4f0f28ba9ce722db11",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5VenueIdentityProcessTest.java",
      "sha256": "7f50ba0a20e3c373c8dcc7b866f3bc56b5c7784ea08e9cfb9ef4ec46e997f72e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L4PlanBlockerPostgresIntegrationTest.java",
      "sha256": "d22c4598e6d5430517f9f443f78ffc4fceb8cd3e33168778963271b716105b59",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java",
      "sha256": "7ecc20debb3e5d2a72c9ae658862a607ed9a5c4e9c51f185f2f58dd2635c26a5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/ReconciliationCursorPostgresIntegrationTest.java",
      "sha256": "7fcad1640a75cd4b8e94258fcda3f262465080ef22b2478e060aa761652ecac2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/SyntheticEvidenceExport.java",
      "sha256": "08a8c4805388433cebeb86868e5b05bdbdf26820a8333f228854279cdb9ed209",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/SyntheticEvidenceExportTest.java",
      "sha256": "ab5971e5d6f834ce8ea73dead94a1f8ca920cb04f9fcc1a75663928491c0e9b6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingChainPostgresIntegrationTest.java",
      "sha256": "23edc2f3f98b636aa36dfd40145c1114c2fae2f78939960d89c62f56fc973913",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingRestartRecoveryPostgresIntegrationTest.java",
      "sha256": "32086eeece79f32ad396855ba649c4361e028ba26a191d31b1e9555ba9f71c20",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingRestartRecoveryProcessMain.java",
      "sha256": "89b6b669d08d7f3e678d41d84e6fe03ff43428adb8a9daff427dd74230e938e0",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/synthetic_evidence.py",
      "sha256": "6fefcefb570bf1cbe55c5c596350e4230e283e93e014e28a09b0913a51a0e86a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_synthetic_evidence.py",
      "sha256": "47deb03854dbe5eed6b804f4b6cfd223e159dcae6c9feb03d1c9237cd3bd7e6f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/AuthSecurityWebMvcTest.java",
      "sha256": "aa17813661b33289e6d666b3d8b3475353a2b69e82ff9e37d2229b485a925a81",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/MarketdataControllerLocalIntegrationTest.java",
      "sha256": "5b2e9c2a4c9088bab6d824200fb7e6afbca4f332862feeb46fb3c21a9cc28a1c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/ResearchBacktestHappyPathLocalTest.java",
      "sha256": "56f47fa584e191872d38442edbc3782fd200ed4c745969e5d6bcaade5243dfc0",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/StrategyReleaseAdmissionPreviewSecurityWebMvcTest.java",
      "sha256": "0c49db5729c950643bf5cb179e87095922c261fa5ff1a869159e33f73bbacede",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/TradingVerificationControllerLocalTest.java",
      "sha256": "8b7a33ee6073bdf0701a2d96609b1925d6d97d43f494272b01c7f6fb218c32b6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-auth/src/test/java/com/guidinglight/nexusquant/auth/application/CurrentUserProfileServiceTest.java",
      "sha256": "784dcbc0d49811ea0340e85f583107a7385ed3631222b8550f154de1b388b7fb",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/domain/backtest/BacktestExecutionRequest.java",
      "sha256": "14eebd68622fdb20e1a64c90b497ef0dc757628f5d0e843bbcee56c5e6bf41ae",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-contracts/src/main/java/com/guidinglight/nexusquant/contracts/event/TradeExecuted.java",
      "sha256": "bb471f525f35cf30d197430b9ae9de3f531aee4b05770cbf5e944ae7270fddba",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/LiveSessionControlService.java",
      "sha256": "a2085f2ddeaf1280b21579e51418c9c3d5e0261d1faf7dc22640fe3b02252fe5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotPrerequisiteObservationAuthority.java",
      "sha256": "805b5bd37702caf80d9489242bbf2a4188b355027a3289cf88c370543d437501",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeAuthorityResolver.java",
      "sha256": "ccba7d6de0e7ef1865a8d564ae2cd896278f34c24d811bc5b8907e89831c3ea9",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeControlPlane.java",
      "sha256": "d9f48eac4a55025cf3b00b04ee943d01e4a1053780b16b76c062493a72f6e938",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/CanonicalDigestSupport.java",
      "sha256": "516a0c25bc78cadc6c0b650aa5604a31f2eb9198f9548146b78992b5f5e54067",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java",
      "sha256": "7c5509f2d8403c517db9e8546f1e957dbc6be13262c8e6306f102dcb61c06525",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/PilotObservationCanonicalEncoder.java",
      "sha256": "eba7b9f1167549e87994ba993456fcd3db3ca0b3fbab825c31d678546a333c6a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/port/LiveControlRepository.java",
      "sha256": "53ea7b2720244e417034bb8bb7f2a92a2596c14c77d41df350ec723a2b766269",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/port/PilotPrePlaceRecoveryRepository.java",
      "sha256": "69626d5d344a9e3198ea1800778cf56f2a87b9d7b37628e0a6f7d098a447e992",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/ExecutionIntentService.java",
      "sha256": "2ce58ce12b1434261ed0a373974b21eccb7eb5264b329b058ed4eb883ef164b3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/SpotProviderResults.java",
      "sha256": "f9ae5e64b84c405e92949322f241ce9dcc3cb52549c13d9a0ac0f141d19cfc73",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/domain/ExecutionReceiptCanonicalEncoder.java",
      "sha256": "9d0bf0b929ec4d6f319d482a8bb0fbe7e39ae9b20fce5961d92b60408007f146",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyManualTriggerService.java",
      "sha256": "c83d4ea48691a044760c21cfcd1cb93adbbed565bbbc9ebd6d28ff023bf030b7",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyRunRecoveryService.java",
      "sha256": "aa4757d3f19516e00e998711c772d497c24d511c811283c25a988b00348052aa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyScheduleScanService.java",
      "sha256": "1a6e1e9dfc05c365c6df14ef58701158efdaa24ecebf6b3ca8ac960ef64cd7f5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyScheduleTiming.java",
      "sha256": "3a9fd8c7f0317516d7c22762421ceb629584c7cf417f7a7ee45db7b03305f306",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/command/StrategyManualTriggerRequest.java",
      "sha256": "8486710c979c979603905e499e32829a635462dfc319908f4fd5cea1a23ff0f6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/result/StrategyManualTriggerResult.java",
      "sha256": "a9b25e6e26f4421f6e160a91a62c1619693b16961a11f2dfed5e202a8c420883",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowlivepreview/ShadowLivePreviewService.java",
      "sha256": "f6fd8101b3bc1b3346ba51bc7ea264d9f38f2c9c0ef96eadb7f4ce721e34189c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunOverviewQueryService.java",
      "sha256": "794ea571579baa30ea0a2f253bcfd6497e573afb45d9bfec9aff30bf43cd0458",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerCommand.java",
      "sha256": "b06564a9f74f8b5c8e6147f1c9dfa19e41e2c9253a79fe020d45012cae9734c3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyDispatchIdentity.java",
      "sha256": "6eb78c90a5b7b42a6f22ff1848398858eb2978b628785d9ccf096578c187696e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyDispatchWork.java",
      "sha256": "acd8e41d4dd706851f76ae72cda7c1e9ab141e4d1c9351054ebfecf19a8aca7c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyRunAdmission.java",
      "sha256": "3406779b4d0b6c3ef356d96b423b5d7ad00ce22dc43a3ca2bd32064e409715b8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/StrategyRunStatus.java",
      "sha256": "babc79a903117217ca68b9cba244d7cc4bc5042b4a5928bd86bf7ef50be6b9c3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/ConsistencyEvidenceOverviewFacts.java",
      "sha256": "74558e127a0d0a31310e79d10cecb139a9791119037528e8376d937c9d6b2cfa",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/ShadowValidationWorkflowOverviewFacts.java",
      "sha256": "dad0d1b61fce09ba46646265ca95cdf13f77ebaa4b8210b03e951694cf194ba8",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyExecutionGateway.java",
      "sha256": "47764a16943b302f646610791309575a0e8439796599902d2a759f30c28841e7",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyExecutionIntent.java",
      "sha256": "704ccde1b59284e89ed01ee471ce6d7c011a73a43ad8cc4c1ce700a69d44f1c6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyRunExecutionRepository.java",
      "sha256": "42637f3fd8053475021f9877b00c44606d4bd194f82dc94c2461329f132ae1ff",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyRunRecoveryRepository.java",
      "sha256": "ed9364695ab29ce8616d3f7349bb9de043e7aba89f7ec884d981dc5e61ec008b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyRunRepository.java",
      "sha256": "7e881e256cf3804e1ee6d77786dbc953f6913afd262b5ccc7756ed21c19f1c84",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyValidationOverviewFacts.java",
      "sha256": "e12005ad34aa97cf10aa06314e6cd083475aecc9729ac746aedd375104da9e0c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ReleaseToShadowAdmissionService.java",
      "sha256": "76bb7ec0d5d5a00c0754e0f8718f9dd42da1d6e7dfebbc594a69d0d5ac36dfbe",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseAdmissionPreviewService.java",
      "sha256": "567c6db0d318d7f87e83e800ac31117b2a07b93740e278f8d334da9c97b3540c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/VerifiedStrategyReleaseIdentity.java",
      "sha256": "7e9a827bf898762b69e917bc84360b74a03e843d9026d49bc7a3e0ab111d430d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyReleaseManifestFingerprinter.java",
      "sha256": "ae2fbd7fad076df1fa2a72f10ccab2672465ebec8c28ffacae425edd29897647",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactReader.java",
      "sha256": "7f141ef1df31ec914294697669e6dc0df06c7aee8d0b874f610ce5cf92f4851c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java",
      "sha256": "3328fe45b9c50e3c70b36829b4ded680dd27fa867dfebacca008a6cc9f059493",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java",
      "sha256": "99025d32dc6610e7a1c692c3ca73c954151fe3c6af099d0bbd980dcc8e42095b",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/StrategyOrderExecutionService.java",
      "sha256": "a2433705b5613c765fb0fe89e23ff960e00736854763015f67fb7f860b0911ff",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/StrategyOrderPreparationService.java",
      "sha256": "706f1ed77991502c31f2e26c2221eed4fd86cd00eed98084b3739fd1fc3a765a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/command/CancelOrderRequest.java",
      "sha256": "3a890d81fd7297106ee4935c3ca7fde1757f9cd84e1f251343bf33920fe30e9d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/command/PlaceOrderRequest.java",
      "sha256": "c3b359b1b4405aa4b313668f2a9545b0f9143ec541f26e236335b9cfc22c8261",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/port/OrderCommandStrategyExecutionGateway.java",
      "sha256": "1ccbe511ad437f369cd2ee61ef76b9eebcc4f50e9647e8ac79a0448d1ba01972",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/port/TradingVenueGateway.java",
      "sha256": "15482274cf7c829f017d7f92508055ca2e31558bbb100345bae91f6bb1100af1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/reconciliation/ReconciliationRequest.java",
      "sha256": "7e614ab33ef913a2b197039c681cd631a378e495941f6c3b678c904e1d626a02",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/riskpreflight/DiagnosticOrderRiskPreflightRequest.java",
      "sha256": "d43f072545bf0a99b8162d3644a65d81e5be9db47988ec3e0335187766a48623",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentFactBundle.java",
      "sha256": "728b5c3662cf62690670510aca524ab0c8360af72d443512a8b881cf6a737cd2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "sha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/EffectiveOrderParameters.java",
      "sha256": "21b73d971c72b2194e90b1e2bf13f027819a4bc6ca53336444f06257b7f9ccaa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/OrderCancelFinality.java",
      "sha256": "ea516516d28b0347c7b17f4637dd0546e011d3472a6ba125e4d8b4ceb689f792",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/OrderRecord.java",
      "sha256": "fb94a98cbb74dfcc7e593a6376764e194200a8dddf6b873227fae1b5d54222eb",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/OrdinaryPlaceAuthorityState.java",
      "sha256": "cd9b44f0693008bc7be932a65d9cb2070339f55e3d5082e5cb8d8c7e8a286668",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/TradingVenue.java",
      "sha256": "2173a48ba3744e0f8c0c7ef90ce6e6387c9adae8b687c2db5a13fab7aa2df579",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/port/OrderCancelFinalityRepository.java",
      "sha256": "592863bf957dfb1b571a1eb1394a15992071a7eeb4a10a50f43f4542e97f9efe",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/port/OrderRepository.java",
      "sha256": "d778c7cf6123c051da948db38da46d415d69291aa248798677b46267bd51141b",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/port/OrdinaryPlaceAuthorityRepository.java",
      "sha256": "e158b570b960c4603a31c8d4bfaf4c0df61f58804c827793a6f1140132b7708c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/port/StrategyOrderBindingRepository.java",
      "sha256": "852d08db6e0c66831a0576c66d95c4f4b75c32b24ff9c926dfc6b46822d878e7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/validationreview/application/ValidationReviewOperationsService.java",
      "sha256": "c801005d7fbfb8f9bacdceabae5a7c2c9b59533306bf05247ce4f209229542c3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java",
      "sha256": "33f9f80d795861d98aa499ce0992ea3b99cb16b9cd86745f878f234317d851b0",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandServiceTest.java",
      "sha256": "a5f085824a85d50c8f658db74a7ff77eb5f5bdd1f025c5d9528707e30736f821",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/domain/OperatorPilotAuthorityTest.java",
      "sha256": "06463f34ecf592f57075ae6de1be8b9475b95bb00fcd0c6af4e3ec0ebadf8b66",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/ExecutionIntentRuntimeTest.java",
      "sha256": "ef6a7a25a4a6c67ded38509e99bead9144bc490c12134c9add7015f56e6a9a35",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/StrategyManualTriggerServiceTest.java",
      "sha256": "6f2181f7d81412e48b01abc9318020b5523dde4b605afb250a545d764d73b458",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/StrategyScheduleScanServiceTest.java",
      "sha256": "bef06c82a784d87d24851687ff9597cd07f9140af7606b7d3d75fc384f354dc5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/pyartifactpreview/PythonEvaluationArtifactPreviewOverviewQueryServiceTest.java",
      "sha256": "bcf85028f42ac4a131afb7d1e85ada3ccdaa0e22ac1ad88211a31cc0b19adee9",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowvalidation/ShadowValidationWorkflowOverviewQueryServiceTest.java",
      "sha256": "dda39a6db74b42703f59fc952f00acc8dcde41a9b6f77159c0a2bb9825998ae5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseAdmissionPreviewServiceTest.java",
      "sha256": "9228117f130b84792e50018717081a474ef9d7d9ba5088e16c24d3aa760cec6c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationServiceTest.java",
      "sha256": "18bf4b8143c1c2012d76d9e28545dd6b447d4d89250dfaaafdd524efef66c007",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/OrderCommandServiceTest.java",
      "sha256": "2ba4d0c9734425617fb68fe5d6bf835b7832ec482aed125d1cfb53ea75fb082d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/preflight/TradingPreflightReadinessServiceTest.java",
      "sha256": "2c528b209fe3b198a4b6669a36ebfaf1def1589827a53a82453cc83fb3fa9836",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/riskpreflight/DiagnosticOrderRiskPreflightServiceTest.java",
      "sha256": "d33a020a350992ce80fc91b66de9b7053576521d4260b3cc3ba97d72741184b9",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentServiceTest.java",
      "sha256": "cf340eb4c6067dfd9fa546bb43ab7721b50a673f5b9f571eaf34bec5c2898653",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/domain/TradingVenueTest.java",
      "sha256": "d05d33ea5342d8bf7484c9deb3411230efdfd56f5b3e19323341a7927d2cc94f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/validationreview/domain/ValidationReviewStateMachineTest.java",
      "sha256": "27948abeca9ee3fccbca927cc6e6cc9d7dc51942dbd8e24e28fb19b93d899110",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/application/eval/BacktestEvaluationService.java",
      "sha256": "65324c6fcc180967db0bf7ff1009da1c76ddfeaa19073868e419e0a47ece81b2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/CanonicalLegacyAccountBridgeService.java",
      "sha256": "01344a5fd390feecfb426deb3463cf9af58759be8cb4629ac97accef8099fe2d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeService.java",
      "sha256": "3c410fdbb8b3a642a13d9763b9accb94a1caf136152cb7122f089c234d345b59",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/infra/okx/OkxPrivateReadonlyEndpointPolicyEvidenceFactory.java",
      "sha256": "cf36e64bf3ca77f490ff7eee93aa4b317adedce86167758586678bb5795377f7",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
      "sha256": "fca69b49ee10659217c1af934735e42665095383269310ddcccbd8bb99978ffd",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/fake/LoopbackFakeExchangeHttpClient.java",
      "sha256": "a52fc4604c23e73ddc43c5aa94bf07e1dcdcf854334cf43a09b10c1123891e2f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java",
      "sha256": "c0cc7e693c7a65c541ecb4fd4166073787d2fede268192401ae0a7c510b11695",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQuery.java",
      "sha256": "f099315eef296aa561237c4679c89079c5b22d524e0a00935e04282e8515ca96",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/MinimalLivePilotControlService.java",
      "sha256": "32acddc5c468c73c6e1e28b5dfc8952f2a53e4f046d24f9e91414f86797e273d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/PilotExecutionLeaseService.java",
      "sha256": "948978b0188aff821ff26095dfcb38ea6ce26f7d2b8b5cbd5548692e16e2d113",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/PilotScopeControlPlaneService.java",
      "sha256": "92895b095f54454ea171ff26b6b7bb0efe1dc8f8f2564f3a947b7428a45ea4cd",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java",
      "sha256": "de4353ecff2abb4e61b958107cb12bbce19e24a494e9b19ffa5909a3b12bcbde",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcOperatorPilotAuthorityRepository.java",
      "sha256": "53ed581a721e9c4786747ada7d90d0f0fd9dc8a9b30c4ffdc2328d60dd8a9799",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotExecutionLeaseRepository.java",
      "sha256": "f75d307328a9fad1adb7c5d6dc2aaa59e8c1bbf6dcc4c50fd23fcf1beb6d0b3c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotPrePlaceRecoveryRepository.java",
      "sha256": "0c64f25ec108af1f02f5beec1fffbae0cf62879a614d1e493ea0808ffea282f8",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java",
      "sha256": "92c730f6a23205b397dc07f51acdef9f3285b089c60b20801ee604924800f64c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/adapter/AdapterHistoricalKlineProvider.java",
      "sha256": "3d7538c91425cddf5801889e866e1dc038a9fc60165930c3e9c53423f4038398",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataIngestionJobRepository.java",
      "sha256": "de0674049d651c261c223e8eff88725b458b688e5ed4724ce129cf9130006a6c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcBacktestRunRepository.java",
      "sha256": "52ef00d59941947a78ef56d999b70439642e2cbdd70c923a006e3be02219cd32",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcSimOrderRepository.java",
      "sha256": "88485bf2d92ecc30d3f970ebdfb4f23a42abb9146bd9dce54a3509538967bfaf",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/eval/jdbc/JdbcBacktestEvaluationReportRepository.java",
      "sha256": "2ee003fd83df273eedcf0f897cd4f02469b2c7e8840d2d96a4468d80a896b99c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcBacktestPublishRecordRepository.java",
      "sha256": "a60ec20877b1066b708b6ec899f37caeba2263380fe2c1d81d812fe5968f8d03",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcResearchConfigRepository.java",
      "sha256": "96055112eb274c159424c59d5dbb78d584d35f45bdf9096619846dbe62c2c59e",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunDailyReportRepository.java",
      "sha256": "4b38a4312e9239371de995f4b0528dc53dca800d3a50001cdd7a401283efdb14",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/scheduler/infra/jdbc/JdbcTradeRepository.java",
      "sha256": "9a12261800aa3f155d49d262650d5a87d0c6e3f3f61e169fad932f6bbf6a2ff5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunIllegalTransitionAuditWriter.java",
      "sha256": "87e7a5131ab6c9467f44e8afe1ca0ad5835724417ae1cb509bd34b9bf94765fa",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseAdmissionPreviewFactsRepository.java",
      "sha256": "36717f9748c01d06936d88307c14724d01551df4e6026b1a3c0b5d25ddf2956d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepository.java",
      "sha256": "a1d8e14c348d473c3a2491dc7922ae183e46f520cd58ccff8da12bfc06dc80b6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunExecutionRepository.java",
      "sha256": "fa7c56eda7850052e664faa6c3efcc0460665e740be2afda6034bbc1fe1e0765",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunRecoveryRepository.java",
      "sha256": "418d90de4a096c500ec1caf723a74ed56a297ab1e8308de10e3726d39e3a2c9f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunRepository.java",
      "sha256": "e510dc8b80b65eac8db717956eb5c835bd1f2c6a568e634d62ad9021ba560e3d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyScheduleRepository.java",
      "sha256": "32bda31e7290fbcbbed68795657a8bc1ea5e8044aabbff1bcc9df51b0d3e0dad",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderCancelFinalityRepository.java",
      "sha256": "9e703f6803df42c1fdc04b55fe8ea5c748e3c419ce13083d5475b2394a9aa3f6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java",
      "sha256": "b33f58520830deb12e7c74f25559e471fb84af635ab695cf319654668688f060",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrdinaryPlaceAuthorityRepository.java",
      "sha256": "4bef30b15b08a1633d972ab743fecb8f97e7fab4cc6ccde5482443c00c865e59",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/query/JdbcTradingQueryFacade.java",
      "sha256": "b009044c929ecc6d2e346c2cf2a80f53cb08f363cee8abddcac2b1a59de04ee8",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/JdbcValidationReviewRepository.java",
      "sha256": "28c2239e2d2749c005dcdb90e18a6931bdf8ba70c660932594dad1907f6bc2aa",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V49__ordinary_place_authorities.sql",
      "sha256": "ebea77f85cae76d88e052a99b647573ec3b6ba5ea0464f9083012aac2a883b93",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V50__strategy_window_admission.sql",
      "sha256": "ca87e2b7b0c739b8fae59d701bf0ff54336ee3d262eb5ae7dcd7292edfc45ae6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V51__strategy_run_durable_execution.sql",
      "sha256": "afbc3211b824b8f717912707b584d2382f47fe9f8df86802e7c4a1c8a6cd9942",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/okx/readonly/JdbcOkxPrivateCredentialExecutorTest.java",
      "sha256": "873a7dfa86207ed318b7b4fb46c70d61a68545773a3a0dd97141b50cf62b93be",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/NoRealExchangeCredentialPermissionProbePortTest.java",
      "sha256": "50978bdf9546712a423144ff9136e51a11d0515d73b65185ad20313324e356da",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePortTest.java",
      "sha256": "fb4a77b90688113f3b71c6c3e3739ec62f9df9c54f3b5a8d81f28a03c3410626",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/JdbcRepositoryPostgresSmokeTest.java",
      "sha256": "5bcb85a08adb070a92e6878976e3748364242c0e39bbe2d62ba056b649f8cf7a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/OperatorPilotAuthorityMigrationContractTest.java",
      "sha256": "8bda7e6245d9a59f87bdd05f24e425c900330a600a683837eee894b86de33f0d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/PrePlaceRecoveryMigrationContractTest.java",
      "sha256": "46617e0ae0514647b1edc53ee13caef38d77e5cee99a18531ba0f95ebc8542fe",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGatewayTest.java",
      "sha256": "316f88dac4fcfa32751710f356d6d27395309ad76d4d489fa727869ee2d3d0e1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQueryTest.java",
      "sha256": "2174d5f4b07c3b4c5ea97102239018f58370e0ff2f7e71f0447e15f9d07f4e9c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ExactPilotBindingServiceTest.java",
      "sha256": "209e7a1619eef998b5a364197b5f33e9a08be3885b7e381575103f037bd98b16",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ReadOnlyQualificationObservationAuthorityTest.java",
      "sha256": "d065e06fc99846567f7f566afcfd63237dbf162361bf4701d076d107cb89a56a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcExactPilotScopeAuthorizationRepositoryTest.java",
      "sha256": "57974eadee693121eb7bd0636b367462670e53277aba8afa2ae5dbb1f963a3b1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/CredentialScopedOkxSpotProviderTransportTest.java",
      "sha256": "f9aa3075ec443035968bcf59054cbc4ba982312d316ae333c0428f68cbf29fe4",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java",
      "sha256": "8f54f0b221dad0a467c049942aada42bf6721098c30c458943b588ef48e6dd7f",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolverTest.java",
      "sha256": "8a5ee2862352df773be2214a8df4bd7b50f6cc9f73324dc1ae14906247ee863d",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunFactRepositoryTest.java",
      "sha256": "e95f00b7f216b074fbc5ffa1a6ad46b28c7efafeebcb70d57241030a4ddc8ace",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepositoryTest.java",
      "sha256": "c78f46684ec21f55f26fe4132c5564a4ca4e974680a0864893e530e4f5eaf061",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteServiceTest.java",
      "sha256": "8cb38edc6f742d0eebcd852762644054286242a73ed33bcd933c9fe614708f2e",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepositoryTest.java",
      "sha256": "c4a2bccf8b9a91869b79204497b3c020b573efebe0b9ab688124521fb5c1b695",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/ValidationReviewRepositoryPostgresIntegrationTest.java",
      "sha256": "44ad6d522a763d044d4fe320db5148e43f4c1709989269146522079119f75b61",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-observability/src/main/java/com/guidinglight/nexusquant/observability/operational/MicrometerOperationalObservation.java",
      "sha256": "a3370ac1ada4e45fde919057cf9ff51365ed010a68818c09eaebf3b3b15d4b02",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-observability/src/test/java/com/guidinglight/nexusquant/observability/operational/MicrometerOperationalObservationTest.java",
      "sha256": "8b70a1ef63184485ceb65f597d10ca3b4ae0f26bf2069256dd59fa7a3ecf31cd",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/BacktestPublishService.java",
      "sha256": "c71e49934bee6eb581a784c655047e5bad576570d755c21e743fc5f80caeadb0",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/config/BacktestConfigService.java",
      "sha256": "11a8a64843a69a04356055889127750c7e676bba207e55e73a47181208201f7c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperPortfolioAssembler.java",
      "sha256": "26e38ef0099c37bc08f6a2c551d24566d0b153d5d259398a89693bca3d04f097",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperStrategyEvaluationAssembler.java",
      "sha256": "0ddd1edc173dcc374d8c3e204c99e8d8a1e7ac908746e0ab4214d4b63da4c2f5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/PaperStrategyEvaluationService.java",
      "sha256": "0a59d9c2729abdb776247bdfb2ddec47c749f0ff21633026e8cd8109f7a74b5c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/BacktestConfigRepository.java",
      "sha256": "40926eff3e3dda17e4bd5ca6166914b6b7227414557c7854a4d791659d18cfd9",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/OperationalCriticalAlertMetricsTest.java",
      "sha256": "591a340dec8088772628c4da7a0ddb2f77fcce371aaefebb592240220cf5acb1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperAutoReviewServiceTest.java",
      "sha256": "0a7b3ab94fd5493c1f5aaabb21b5e80a125b9c7054682d93a64d7dbdc452176c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperRunScheduleServiceTest.java",
      "sha256": "89c090fe74ccf0eaff682e42ea42492ae13d530947d421f27a3d578f98cbc3a5",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperRunStabilityCheckServiceTest.java",
      "sha256": "847733810000922cb4d682d59f187c57df17e0b2629d12c1865814dd0ee900e3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/AdapterBackedTradingVenueGateway.java",
      "sha256": "e4a0f40581bcec37cd3ce0fad6fe6f31d9ed49ef9c84df7fb821abc96b738813",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/BinanceRecoveryService.java",
      "sha256": "a04d8788b8304ddbb1dd40b4916b34e872846ad2df620430851f47624cdd3021",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/BinanceRestReconcileService.java",
      "sha256": "0125babbec8f4c024b391e212bf2f8ba0680ae0c49fef56aaacbbf57e57b9906",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRecoveryService.java",
      "sha256": "68c0a731402e89e21ce2507b961ab1901c594ad969bba101ed3e6fe334ee8052",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java",
      "sha256": "698c9b898941194c6efd0e377398a8ebbc2e401123fcccf11d7e7a95e3b4b9b1",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxWsOrderAccelerationService.java",
      "sha256": "8a3257b69769f37cfdb9f0ff862c1b98e243f709a7e3fdfc0c66ac4edb68a02c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/PaperMatchingService.java",
      "sha256": "225e96e96226d1064a5f238310d83d07776033f46a1276fe1c34991fa4c342a2",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/SchedulerTradingMaintenanceService.java",
      "sha256": "751d6b241df20390a45844ac4f148bab55a94b83b5c22353a87b417513f6e8e9",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/StrategyRunRecoveryTick.java",
      "sha256": "6307026871fff6a5be944bbbb916cf9ce9085f392bca134b224bec1dc118537d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/BinanceRestReconcileServiceTest.java",
      "sha256": "10d923230d6b41b4bdfe0a737ffbda781f9571eee786af5bad077c20d4ac22ef",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/OkxRecoveryServiceTest.java",
      "sha256": "60cfc3b40c5f1de35d283b973004942d038921c2558e022f0672c130ee5c66a3",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileServiceTest.java",
      "sha256": "1e9ad80110cd6c3d9033ab7e3c54ade06ca23249086b0fd08b8895b382588935",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/OperationalReconciliationMetricsTest.java",
      "sha256": "bd899266fd8e398924de7ca9ed06ebe1eebc6648e75cc502c89c5a3965e11adf",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/OrdinaryPlaceResultClassificationTest.java",
      "sha256": "350ddfd22399fa71b3bba5ad3ddc089bf99876542179eedcfbe1594ce11560b6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/validationevidence/OperationalSchedulerMetricsTest.java",
      "sha256": "b9b59290ba248c1f74f8cfa0c66216e3675b951c3bfc8addc39b82042c7fdf82",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/validationevidence/ValidationEvidenceSchedulerPropertiesTest.java",
      "sha256": "718bb8d5dcedf7b0ef20142b171a6013ed2ad8234b549e153616f0409631c33a",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/validationevidence/ValidationEvidenceSchedulerTest.java",
      "sha256": "b018d418b6ce9578c338887c3ffa7b763ad95cae6b7f541e5e8715e10531a866",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/trading/application/CancelledExecutionCorrectionTest.java",
      "sha256": "3b6d60e56a6af19b20f8c84bd5a6a3b2a8fa4ab615bec973029feb49be035992",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md",
      "sha256": "f35525ac5df56c135bbfc259568ceba88f602f6a2664f25134baa90c52d9569f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_DURABLE_MUTATION_AUTHORITY_AND_EXECUTION_FENCING_CONTRACT_REVIEW.md",
      "sha256": "f3f3fbb952df901e63cd1cedbd86b6f922be16939f10949c3219d15ce9315a1a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_EFFECTIVE_EXECUTION_QUANTITY_CONTRACT_REMEDIATION_ATTEMPT01.md",
      "sha256": "dca2749ef5084bea60f477a2fd6d636ab8a9ad14b0dde06e2dbd264575642f86",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_FAIL_CLOSED_MUTATION_AUTHORITY_V49_IMPLEMENTATION_ATTEMPT01.md",
      "sha256": "df6f08e3380b6dccdaff6d839c9c4012f9fe41aede69543888436987ab2e2244",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_ORDINARY_SENDER_RECOVERY_FINALITY_REMEDIATION_ATTEMPT01.md",
      "sha256": "67796e395a3b79c29b6d667ce8be5add411bbccf3c861860f47fcc389d743880",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_PRECISE_DELIVERY.md",
      "sha256": null,
      "change": "ADDED",
      "canonicalSource": "This delivery manifest; self excluded from content hash to avoid recursive identity"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md",
      "sha256": "97bb089a42fb2c369ce87467fb3d038cfc53cea87aa8ae24deb3f4acd3ff07e4",
      "change": "ADDED",
      "canonicalSource": "accepted stage-assets remediation evidence"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_EXECUTION_CONTRACT_REVIEW.md",
      "sha256": "2d513733ff8a16489f14bbf6119cd277efe5254b4318fe78823338ccc1ad0a3d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_EXECUTION_V51_IMPLEMENTATION_ATTEMPT01.md",
      "sha256": "f351e4e97a5a4ade5a835b0926ce99396f0fa0baba37ac62c33553541ea8e369",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_LIFECYCLE_CRASH_RECOVERY_REMEDIATION_ATTEMPT01.md",
      "sha256": "1f533e25f33e3b8bafb27de4b73ab1fc77f7bf71d4d9e3efb7230ed5d14d18dd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_OWNER_DEATH_RECOVERY_REMEDIATION_ATTEMPT01.md",
      "sha256": "1b56fe4bd6e7bb9692bc3306ce7a6d186cf1572e7969984c1363522bbfc179a8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_SAME_WINDOW_DISPATCH_ADMISSION_REMEDIATION.md",
      "sha256": "4a4f2e83f04a7fabee60fb5846111292aad596a1c94fd901d7b4dbfdd6c4ac73",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_VENUE_IDENTITY_CANONICALIZATION_REMEDIATION_ATTEMPT01.md",
      "sha256": "15bc78f53afa7b4f2a1d9abd37f6e9834550b60a435704213c25435dc85babed",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/AFTER_ADMISSION_DEATH.json",
      "sha256": "4d382a4a8c2842638a87b52a1d4cb83a3298ca8a2c9c6355723b3c7cfe13e215",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/BEFORE_ADMISSION_DEATH.json",
      "sha256": "94185ccd0d7f3feb06ec8f5f9301aa28351a182a03ad8bf0da7dc9875554eaed",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/MAY.json",
      "sha256": "83abd6ddad95775260f67321042632b65cbe6a0befdc903ce72e1ca125283516",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/ORIGINAL-R10.json",
      "sha256": "a32ab498dd92661079a85b2faeee916a5443eece2d66f62a70705ab63bf3600a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/RACE-1.json",
      "sha256": "6f6b8639c47bab91773eb92d758b178c338edfb746087356dc4d7d1365bf0c56",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/RACE-2.json",
      "sha256": "94461f43d90069aad5e42eda8f8d200bd1f1753e532b829c8f287879e7b305e0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/RACE-3.json",
      "sha256": "7c540321ea9eb37aa00dc40595aa07f2c25d41c7717020ca89a41e0e0e62a568",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/RECOVERY_SCAN.json",
      "sha256": "7b46df69d4080309cd52a513bb89db7a8aab234ff26b2f0dd72d7b8de70b7b2e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/STALE.json",
      "sha256": "83816687d87bbf8100e6ab31e5e30e1396bb8dff022aaf81db7c6f62e6e97668",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/candidate-integrity.json",
      "sha256": "123ef48e97416ea63cf0066d35e684698b2ee350f2792305e545e4ad6241e25c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/checks.json",
      "sha256": "25daa8252e6839df2389f8b4f4dce6cdea645d6708644a814e7ed84fdb4ed08f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/full-01-before-exporter-correction.json",
      "sha256": "e3b93ea0cca49fbfdcf5f5ff095d1f8845bfbe229f814878d9b515169c51cc25",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/full-maven.json",
      "sha256": "19d3a7397212471ff5cb1c03cfa37cc6419cd645f77687d23944665339f3f543",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/full-skips.json",
      "sha256": "633d45eb116f0505ee0abc50b3dff78a375d33e4685c23619d9e4764df8b88c6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/proof-index.json",
      "sha256": "3003c4cb7ce7dafcf3ed18d487a5b8c06c1242722abb7cda8758bd30559130e3",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/source-manifest.json",
      "sha256": "0eac400e9942a774943abab91c05738e3492f3f224e94daa0139434123e6f4d5",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/test-runs.json",
      "sha256": "50e917561819b7a0cdb4eee2c0ff3d93b00d7facdafedff19fcda64bce8772fa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-admission-remediation-attempt01/tested-backend-manifest.json",
      "sha256": "68e41ad05b449d73e58e850d104cf54a24b9eec9baa5d96de45a792b67452362",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/execution-helpers.json",
      "sha256": "3af016eead1a309fc35c16406819e91ba90c1ecff3f68027fa47e58b8e4cac7a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json",
      "sha256": "90de21835bd5d90639f2cc01a0969d67bf3bd724eb1b1d7e1fb7a916341a7e00",
      "change": "ADDED",
      "canonicalSource": "accepted final identity metadata"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-result.json",
      "sha256": "eefba066a56fdc28bfcccb3f17dd672369bbd9018df050ecab1e216e573c6ce6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "sha256": "7520b88953b237fd1a7c663c4e371458523e73150c0560f143b05f64fa7357fa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/manifest-export-validation.json",
      "sha256": "32ac184fb68cb6865926009b7abc440e99ecf2c818387dcb1db70daad8d6a3fa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proof-index.json",
      "sha256": "ed1be16fe5619953a1ccfe72d45548de0e51417b3d7f294d0e1457e18fbd7c9e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/001-affected-01.json",
      "sha256": "786e325de5ea141703cd125bd7286cab26e9ce1966ef58fba7de72f6c9caee5a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/002-affected-01.json",
      "sha256": "7554f5bcb5e01dc94178ffe2e17a632ea8081ce5184b473124f699c0811237fc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/003-affected-01.json",
      "sha256": "21aec2e0e22cc8657b83174626d178a81b939eb93b80914e46dfe053061962ca",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/004-affected-01.json",
      "sha256": "3e216a3c466dd41681a967496e6b51b352d698ba56554bffe99d92571ec40539",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/005-affected-01.json",
      "sha256": "4411f531a4156d503b84782e8739557567acb6b85042db18229267ddeda95f29",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/006-affected-01.json",
      "sha256": "7c0d4bad08ff6a5b200a56ea2c0439c578d300bbaf7bc0ded9e9533212685a99",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/007-affected-01.json",
      "sha256": "a4c2d9ec2822088ad66959b75375388af945edf937d69b0f4e5e76b384cda75b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/008-affected-01.json",
      "sha256": "e484af272b45cbae3324d5ba291b16a57c3623c714f373ae989d004f133c4cc4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/009-affected-01.json",
      "sha256": "2e6aa256996d479c2e04d08e313f14f303a072cc79e5b18bd9256e7e2d758d49",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/010-affected-01.json",
      "sha256": "76b8f0d900e9082004efd0d2ce37a1906a2102d84ec10994762e4c9221bed09e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/011-affected-01.json",
      "sha256": "77ffca8aa13ffb5a7d6fb24042bd89aa5e02ace54026e8a2464e7f9b63ab4996",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/012-affected-01.json",
      "sha256": "328f82364b0eb06ece18db943cd27c030bb53c51477e7237cd0057e0f690a51d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/013-affected-01.json",
      "sha256": "b043adf70dd34c95feb3f054c5cc0139363b657b3aa24fbf8f8252359f3be4fb",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/014-affected-01.json",
      "sha256": "a10f211610b1abe4822a4e7ac68e39ef621860d8455b3e7db161f2a156f53eaf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/015-affected-01.json",
      "sha256": "fbe52a56114fd998797e48270b1581c2a9f4006b0075d059d81e9235b6615302",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/016-affected-01.json",
      "sha256": "09c43c9dbafc119057f90e33374a3448711aabeccc256207acbadaed971aeb51",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/017-affected-01.json",
      "sha256": "2abac7a290ce90b9cedc6325616e796c57305fca34fb041f6eaa59fbc0b23f10",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/018-affected-01.json",
      "sha256": "b5b48ac9b61e3c85af830a88703738fb55cdf9ecf10652f8cc3945289bd67acb",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/019-affected-01.json",
      "sha256": "afb0073bcadc55978ff6e91320220da928d6380600b09cc9ab80313cc2381047",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/020-affected-01.json",
      "sha256": "16ebcb4dec818196063e164b4a27e8262c714e28fe7ac1b8535405f31478a342",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/021-affected-01.json",
      "sha256": "71df49d64c38927246aeae900b4602535ff5df95a0793dcc904f41523be737ef",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/022-affected-01.json",
      "sha256": "f3021113612bf219ebcd7507a25eefd614ef728b93c7dc30ac612361ab0a314f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/023-affected-01.json",
      "sha256": "2329abc266ffeb9c306268bb439bbb2e206c5fb21b8b539ed2a9c38b7bf6fbf5",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/024-affected-01.json",
      "sha256": "4846b0839ffe2f33ee5282fbcbb4fbaef503c513741b81bfc795d70ba6c5bae8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/025-affected-01.json",
      "sha256": "fd6b7b6ff9de5dc1e8b4302de01a46bcce3d776c57be98ed5efb63518d701954",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/026-affected-01.json",
      "sha256": "c432c2ae1b7355462d064f29a84beb9c60aa868a616f325f04b489a6f68b8763",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/027-affected-01.json",
      "sha256": "dd4ba4ecaee1a43f2485a816c398d3536ef7753e7369271cb20edffa1284fd27",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/028-affected-01.json",
      "sha256": "6e30c9fcda3a3782689cd40f68d48e4b3b37ad7732344770ba3c8df509b97f38",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/029-affected-01.json",
      "sha256": "0648df18bfb12fe2d133348c3059eb1a0d9c0ba19edee29468194eb32a1dea4b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/030-final-regression-01.json",
      "sha256": "b946c22ab26f6ff036ea5094031d12006873d0c1582374485381e8b309f0a6e7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/031-final-regression-01.json",
      "sha256": "8b985bc662f2bf1a9ba592bec0d047bd4cd6dbdb844f32d5b10fe5a5a78c4eaa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/032-final-regression-01.json",
      "sha256": "cb5c083e5ac0b1e7f808500e00fb16473594d7925c6be95f5c4fe95d4c6874db",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/033-final-regression-01.json",
      "sha256": "c487be9c55203e681939b054145012a4ab4f6be05d9f8166ed78bf61951490bc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/034-final-regression-01.json",
      "sha256": "183c81edcc5f11f434b784d3559797896335bc03a7d199433dea34c5212f1c6d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/035-final-regression-01.json",
      "sha256": "2cdc39a9f1c7ea3c90c99475753c17b91dd056bf10d842201a39e8dad5ebeee8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/036-final-regression-01.json",
      "sha256": "bcf8f12e8f3861e3e24c5f9e102acce3d4f77d96b6f3cc915eaeca16944a97bf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/037-final-regression-01.json",
      "sha256": "a4b13311017d7c1022dc7a3a57376260836a80b100015556c5910f281d423055",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/038-final-regression-01.json",
      "sha256": "e17035903f2c5948271253ddaae4313efbe1990cd28dd34c9f3c0a7254c57e8b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/039-final-regression-01.json",
      "sha256": "67e8a34ee727eab96e247354098a92f9878454fdaacda8e73ad1b6f12125c78b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/040-final-regression-01.json",
      "sha256": "bdccbec3d5814ee33403e3a2e55244535e993e4fc9ae9ba5195cf85a5690245e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/041-final-regression-01.json",
      "sha256": "a06ce0754fe2f5135da0500b1099cbd58c7a584e11e90a5671f6dc0e4847a5e1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/042-final-targeted-01.json",
      "sha256": "284fd39de6f2c2d64f8b82079391d68ce42590de512f54320057fdf44a81eee0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/043-final-targeted-01.json",
      "sha256": "0b0c4e2f3bc11729c9b4dcb1e56103547fe14bea4bde70eba09c31c8304a316b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/044-final-targeted-01.json",
      "sha256": "227734cfbdc27f816cc8018a86ebb6ba69c7be2f293359023f4d5c58b0912274",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/045-final-targeted-01.json",
      "sha256": "43bc757905971b2712190d6c53e0fe528d85b7813835b1ec16fe4244391e5d70",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/046-final-targeted-01.json",
      "sha256": "d77e293eb6258e8b34a9224d554e42b2a1a9c28d1a4b86f5d4738fc611f33f13",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/047-final-targeted-01.json",
      "sha256": "8a5e2953868a27900a0571da7e868a5cb09b64a9ccc872430893214069e5b434",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/048-final-targeted-01.json",
      "sha256": "14ff8d0af533f1ee8790342c7ee78b979b0d7d694be3b59dd402724399459292",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/049-final-targeted-01.json",
      "sha256": "72d1cb8d6f0533f86b091734e4904b0e16c24db39c7663fd1cd765dc2a08477b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/050-final-targeted-01.json",
      "sha256": "a97b95fdd0d11ea3a899bd82ce402239a1e037f1b3b93df5c2be8ae698f6e0dc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/051-final-targeted-01.json",
      "sha256": "2a1abda2c371199bc2fe90e5827db8ee10ce04c94c38d6fa8aedebbb5dc72d11",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/052-final-targeted-01.json",
      "sha256": "c76603c28a51127da2d79c9f31577b390f78d8ef5f7e393278c4060a6e159667",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/053-final-targeted-01.json",
      "sha256": "35c90ab1381ac7a1e30bcb2dce2a67838a33559e848137458c506934dd26ebc7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/054-final-targeted-01.json",
      "sha256": "485f135b26e86248d305259cae854779eaacd7b54d8f7a0d93b8303816e34083",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/055-final-targeted-01.json",
      "sha256": "403856ef017b18b953cb564f8d5e505740f226d9139767397120d242f32980db",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/056-final-targeted-01.json",
      "sha256": "cccf8b28d632625521a9f1f6a5841438a74922ce89e5dfd457d945cbdbab5671",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/057-final-targeted-01.json",
      "sha256": "5a612755c683ee4a245e841947976d3d98693957a697cd16c54b03c67ea673d8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/058-final-targeted-01.json",
      "sha256": "c9f2e4070d6c907c956df624cf3163eabfbe4abeccb4e735390e0fce1461f286",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/059-final-targeted-01.json",
      "sha256": "8f8687167d318acc42ea894e8d615f09b64c958fd1ed23147ffbdb8477d46d96",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/060-final-targeted-01.json",
      "sha256": "6fbabf04ee4f415c20ea5bb931af8d56fdc5ca970839d4df3e6c29cf1bb32060",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/061-final-targeted-01.json",
      "sha256": "13152ff299a27854b6097187e5de7abdef4fda5e46ac8348e3cf2ec7daf38f98",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/062-final-targeted-01.json",
      "sha256": "c6909dcc032f6124bde656db72cd644f423ac78ff80e93aa0c808e4917c67cbc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/063-final-targeted-01.json",
      "sha256": "bc4f76d033ae4ac2834c1bd6f3743f492c4cfc7bc259cadf10e418705db1702e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/064-final-targeted-01.json",
      "sha256": "e653ba371c143d294b0d15c20110cf17f03be0101f5b80cb3f64c428eecb3fcc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/065-final-targeted-01.json",
      "sha256": "3918e59779bd3deb07fbb868cb9bed406eea9f5a4d3114630112a2efbf24b8fd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/066-final-targeted-01.json",
      "sha256": "df58ddd85cd4664d647006a53646ae5637551397ba771a71bec9ad13b154c681",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/067-final-targeted-01.json",
      "sha256": "4fe22c27314c212377548f4441d4b317304ce879306e6316a350f9580d617608",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/068-final-targeted-01.json",
      "sha256": "3eaedd6be55efc880f40b7d15510969c9b6643c3ad3807c385e895f3933bf5aa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/069-final-targeted-01.json",
      "sha256": "3f910bafeca2fda43b13822fb2e66d703bc1090743b9063aa018b36b56930a39",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/070-final-targeted-01.json",
      "sha256": "3eb1c83584f1b8446d30d258aae37298727e05979c3f3ad98776ba4f8596ea28",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/071-green-01.json",
      "sha256": "4f97b1a9f19ccec24b0d7ad6431102ba0149c62d9a3df9ef9f630f948c06d80c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/072-precision-lock-01.json",
      "sha256": "c1c4163dcf797f58ac401f0f60014b7388779e502926bb48c075e32ce651fa66",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/073-precision-lock-01.json",
      "sha256": "f95a0fc100712e9d448ed37fd58b0fae2b5472bc14376948e38e195a5d89b031",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/074-precision-red-01.json",
      "sha256": "2b3826acd0cdd9793fa51561d02506168e2f364d9ed5966044f5670283f808dc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/075-precision-red-02.json",
      "sha256": "d8d4443685ea747db7b056ec65d03f989e528cc4fc126d1af27110fcc81134b7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/076-quantity-01.json",
      "sha256": "9b0073b3f9919ead7123f965ebb74fe1c66deb6598b5f977b87683e892d9d1a9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/077-quantity-01.json",
      "sha256": "7fc04702cb6a00697b690190de9e105b80e10c3791640264e649a67dd28c1c19",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/078-quantity-01.json",
      "sha256": "a51a3df004ba0bcc860537cb8471dd6a23d4abd443adfa7db8118593905e3c63",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/079-quantity-01.json",
      "sha256": "d9959f6f21be49e96aa45cd8cf58379ab8dd4a89791c1d1d905f20e5bc8b5569",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/080-quantity-01.json",
      "sha256": "23fd5f22e49cb0bafedc8e3c4f990a92fc3ede08812bfe1ab9ec387ed0318334",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/081-quantity-01.json",
      "sha256": "0a6c136dc2f5b2b4dd8938fbee0a3179a7c6a994333659223b619114c766cfff",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/082-quantity-01.json",
      "sha256": "5dd6143e1bd25deb65052165faae4b8ce01578105e313d3d52b5deed4f6482b0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/083-quantity-01.json",
      "sha256": "67659aa4f4c6c510f21d80d19881833c40674709df4ac7f2e45dc6ee9ccbd9ca",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/084-quantity-01.json",
      "sha256": "b838029bcbc60237c70a16ba72d7d5ce60e8780568fe1f00083901698f1e355b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/085-quantity-01.json",
      "sha256": "35fdc9432f2ed6cccd87b9fed84d048519df1fd8f61c64e91d1ab82e23a83416",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/086-quantity-01.json",
      "sha256": "bcfc98eaa8a9a390f6c170e2cb94ad93dfa6d45623d9006b6467b75fff7a2a65",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/087-quantity-final-01.json",
      "sha256": "160df0c3bab21638e48009be12d8586ff854fa4d26b328a12c326155709fc556",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/088-quantity-final-01.json",
      "sha256": "7e6eb9c9584730f9cf5df95899c6c1914b48a4287e4a64146f2f4b2ad013c1b7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/089-quantity-final-01.json",
      "sha256": "cb564c4cdc9a9f6cb246bbf505fdbd7b57f2089d147d90599009b64659309faf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/090-quantity-final-01.json",
      "sha256": "7ef52ef2712a5fb58cc36eae6576588e6944bcc24a5cf8bfed0e6430df46faf9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/091-quantity-final-01.json",
      "sha256": "ad88ecd97bffdb9701ad283d22ea0220717be701d832b0d1fbc8052df6f2be82",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/092-quantity-final-01.json",
      "sha256": "79dcebd2d5d0f81aee11e337d0b4c163ee1b8bd3e250f26d2f670033243339d6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/093-quantity-final-01.json",
      "sha256": "f56f31e1234c284bff142074d438cefd38a42d92e2d65767931ba06e1238969e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/094-quantity-final-01.json",
      "sha256": "cc8b04b6459126242ede2ff4dcb6470cac3ff30452183a4ee502ade951749dae",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/095-quantity-final-01.json",
      "sha256": "55dd8f5ee76fd7cc317851df18319d7a075f835a40553b1ff36c710173d06df1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/096-quantity-final-01.json",
      "sha256": "05dd533aabfe4546b7a78d4a4155e2e80b5c7aa0658b20d2025b620f9be37c9a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/097-quantity-final-01.json",
      "sha256": "c0ed569ca3d84de7b97c4e08db4a1a15a5dcc6f8d2f5464c01653e5e63fcbfea",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/proofs/098-red-01.json",
      "sha256": "8d70794fdf967d4e33736cc05c28635faebee0c2f5728dd417a1220741fc666d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/protected-manifest.json",
      "sha256": "63f19c5cc67d9ed5f6fe6a529bcb3f1b48d37f0acfcaefad8acdf06fdb2b1449",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json",
      "sha256": "0eb30746efa7a5d5e308d127aef3c1d202151b2aba3afba3df144f18143dcd76",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json",
      "sha256": "27894e1839bbb8dc8044ed8f18830a4d5ad305fa9b6d2ae84535e97e7e32bd45",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/validation-log-index.json",
      "sha256": "beeb03361750d6dfcda25d3588c9d9895c9fb9dec9b74fe0f9e164d840709a69",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/final-identity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R01.json",
      "sha256": "41b5d2c91eb7430787f00c4bbdd9ca9ac344b318e26d306060f3a4bacf4173d9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R02.json",
      "sha256": "bdd4871380c9eae30eff7809718fcc2c9c8e243895de80845307886b64508a40",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R03.json",
      "sha256": "14a88efbbe9042ec68ff984b70fdc83e4a02cc30f5dbdc5e1b5af8b7d9a203bc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R04.json",
      "sha256": "021720e7fa7a2f97d9158e8d47f34f73c20d7232ec63b5bfbdb3e33acbb470e4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R05.json",
      "sha256": "63c261b0e62734530e10a459f5b3316ef97b920f6634facc38955c711a9b80f6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R06.json",
      "sha256": "ef5fe2ceb3d6b535e297d42cfea91f1ba6eab2307ad36c4c16dcb88b59913200",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R07.json",
      "sha256": "3def6541e4a3941e8782da92d71e690a9b831e4b065209f52f8bbb43038ebbd1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R08.json",
      "sha256": "fe4600db5fee2468bcc873f923bfeef07556154cfcc870c19021b514a78a0903",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R09.json",
      "sha256": "f028f037ec250f937aeb040db594b3e007c2c6cfee18dae58fbe2e3f39dac3fb",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R10.json",
      "sha256": "56aa3b13edebc92674683dd7305f628ad761d4878f2754c0f475bc995ef80131",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R11.json",
      "sha256": "15b119d66d13cca72cbfef34a3969a7aabc27bad5c6e465eb0ebf0db4aaa71fd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R12.json",
      "sha256": "6a1d03412ada5da422d963c8aecd911840f8fa4902b1088ad516da463d6afa03",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/R13.json",
      "sha256": "485bf7e7aa7fe027943acd67a6905c53cab53a90a95aefd88df3285bf8fbee77",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json",
      "sha256": "ddf27207dfbc35c833710253311da45f84937abae0cdc69b2779e9ec43f84233",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json metadataExclusions"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/checks.json",
      "sha256": "15219a1e3e0150a1c6e08f7e8ea3dbe89d0b97039e8e94024ce679e22bc5bee4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json metadataExclusions"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/independent-proof-review.md",
      "sha256": "800936b1b5c23caf5676d8eabf7ead7717244af6fef43c9182dcbf18e6dfa262",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/interaction-01.json",
      "sha256": "dab59bd1030efdcdfe485a291b6be9623dcec1c6abaa5e5344e8d3e81fb4131a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/interaction-02-result.json",
      "sha256": "357b3c4aab5e298eb08c937693d6b785f90fe4ea7b7dc84c3e097510200dc248",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/interaction-02.json",
      "sha256": "de8e8b14555d0aec05525bf2e67c768c5a9a4a63cc2c2a16d4cc6fedbfd4adac",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/interaction-03.json",
      "sha256": "673fc5aac06d6f3df0407e21c0aa3454ffb67c088388530738fc440753f905d1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/interaction-04.json",
      "sha256": "ca8c69e1dea67a5da288f3381eed1ade3e6120305e35dd4c763eeaf6d2a2cb79",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/interaction-index.json",
      "sha256": "ab26997c954e1b96104f5de9e8147da211946660466837f689ec3330533288bc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/new-run-index.json",
      "sha256": "3c1f3a785dbb6a7a3298b6ec28134456f9826e7bd6375ec49bb3ad4ba31e2cd7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reuse-provenance.json",
      "sha256": "6bf80308e2325269eb13f4d601bdbd3a714f13456ee42502dbf553653f65996b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-01.json",
      "sha256": "3e6ebfe9a1873486625d19df680dab67003014150fe9cccde35f326b00eaa67f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-02.json",
      "sha256": "0141d3ba96ea2d3ae770e2af52871a96091846814459a4020230f27f201e2c67",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-03.json",
      "sha256": "ec6190b4b8737d49eccf1a741e03422e48634ef10105e2bbe11d8eb2be787dc9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-04.json",
      "sha256": "c5c0478eb6594863b83dd51ca504118ee5a60965db05115df39682a45b6c5258",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-05.json",
      "sha256": "4fabb63811f451b89a864b9044cbe73c73bab9632c3bd82e9b5396924250a1c6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-06.json",
      "sha256": "e12db7ee42a322fb6bd044ad4c762ccf9c30ab19ffdf30a21503da9bbf8cf479",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-07.json",
      "sha256": "ff5e78d9829989fa74286a1bf40a37d9b59f8d884cb38b4771d2b5c8842b0178",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-08.json",
      "sha256": "902b7a46ef6d3bc88fec01687ce00c306895d48eecfbec5da9d2d7840472e804",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-09.json",
      "sha256": "66812306a54113a3c2ffbe56743f7b16da440c0d3cc1836443f9c5b8c289bfda",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-10.json",
      "sha256": "911ed845fd06892097f67f44450bdf39d4dbd61e8a9deaf7b43f642e588cb51a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-11.json",
      "sha256": "63dad83e1039b6e7fb6f491986819d0bcc5ed314b266c79a55f09c4262449519",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-12.json",
      "sha256": "b6b3cd5bf7a668f804c0d9618c69554f084c7c5cedd5b008ca3697f3fd356fda",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-13.json",
      "sha256": "4957e181023948285c089367836366dfad3ca7c9599cd47f6611b705601c73a1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-14.json",
      "sha256": "17d69d089cc1fb055bb30bf4721e4baa2c31b4bbeb4443cced1e56f97f5fc072",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-15.json",
      "sha256": "17aebc680da3552f2a5c3044d4fea6a8ba50aff9917e425893e1541207c34d2a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-16.json",
      "sha256": "e93a1b2fd657af07dda80116588a184ebfefc511955608a52fcba00af80ee37f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-17.json",
      "sha256": "18e20bbfd1283dfbf1608c2e5f0bc82aff645b347ca6ce320c0911beecf6740d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-18.json",
      "sha256": "bc0bf2d0a3dfc5f7d2bf43a7967ccfce8f175aaa64c1d41eb455d53833b753b5",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-19.json",
      "sha256": "b8ecfa02bdf3086eef53683033bcd0650a22cc04c6e4325f38f9fdb9784b94b1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-20.json",
      "sha256": "8a1906720e79ae75d9c004740231c0a7c53297f9924bd36943bc8d59ad407983",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-21.json",
      "sha256": "89956f0692e7e0492d10025cbe39f1a9701eaaffce38b8f812152408b738ce81",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-22.json",
      "sha256": "6ef59ca7615ef6de631957aadeec91d5217c66cee916121728b94a713f0be2c4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-23.json",
      "sha256": "90d8c7b977fa5bce53d76b228b720a57341fed0173aed8c06f1e703b11cc534b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-24.json",
      "sha256": "f63de6242d074cead564bc6ebc562154e16d34b01e03c697e8c0eaeb040cda6c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-25.json",
      "sha256": "e6481ba9fb7e012b2a788cf27a43170bc2ec1d7efa9e455a9a729d2390935da1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-26.json",
      "sha256": "6aea2885247143d8f87e3c5651a71ac5b2a891932dd5bee6dd107cc232f8367f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-27.json",
      "sha256": "ecb9d113781bc6535e77646d4df2eb6301e4ff10ddac90ba793df19f7ccc9e66",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-28.json",
      "sha256": "5c354b932ae2ce11f86909db9ae4df05df8e62ff90bceb562f1990e09d7aec0d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-29.json",
      "sha256": "37c041dc76cf041bd13fa00372641b93264d95a62ccaccc342c9d0b09d860122",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-30.json",
      "sha256": "d31a34b486a959fe3b2d2c261808cfd8a3476f17264b45786a2d066675918740",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-31.json",
      "sha256": "fa965670f0e74d7fc836ea40ff213391bdd5bb13464b7976b4823677835af15a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-32.json",
      "sha256": "93063fbae15446a30146aa131259f022daea30a4b1d18e31b78d469a1deeece2",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-33.json",
      "sha256": "3d474af90bc4eafe8f253b08242681c8bc83ed6654456d400493f7946f9d64f0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-34.json",
      "sha256": "9199579e72e2c9fa180aacb58072cd75d102be77f6276c983837c58d47cf2358",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-proof-index.json",
      "sha256": "27a463816adc832dcb58ef634015857bb21a30ffbd288cbab0e3461f298849b3",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/reused-review-original.txt",
      "sha256": "01159bbf438289d9e76e412b3c84109641a6b7d5f728f1f0f0a32a1cf548727f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json",
      "deliverySha256": "2f32823195a167401bedced72a45018fa4174cff2e968cb264186741f3be5e26",
      "normalization": "Removed only terminal blank line after staged diff --check; original source SHA retained"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/targeted-01-result.json",
      "sha256": "eefada6d1e1534077ddbc086ff4604e1cf8c22b2145db9f1cab5ca9105115bce",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-final-qualification-resume/verify-oracle.py",
      "sha256": "607e8db216d71c0508b1f8440c97284914d9bfc353ead6760a6f672290f87f95",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-final-qualification-resume/candidate-integrity.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-lifecycle-remediation-attempt01/BINDING_DIAGNOSTIC.json",
      "sha256": "8b8d22d9d0cde77702475181b4bb6824f74dabf2e545c5fbad1541f4ee902712",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-lifecycle-remediation-attempt01/CREATED.json",
      "sha256": "3da69108f9ba54e678c9fa87051b55031fa055e5a4bb6c0efda6387457aefd46",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-lifecycle-remediation-attempt01/ORIGINAL_REVIEW_CREATED.json",
      "sha256": "4c175693e46932b2f6d40d77016f0cc684cd631d75a37dce532c7abbd43eb11f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-lifecycle-remediation-attempt01/RUNNING.json",
      "sha256": "3beb0563edd7d960a8eff0035b02ce757f945a24894158254592528d4ec3c1a6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-lifecycle-remediation-attempt01/SchemaBindingProbe.java",
      "sha256": "501d42087ec1aef7cb4319bd10d5abd357688eb51a2febbb97caa5a43f45b3e0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-lifecycle-remediation-attempt01/checks.json",
      "sha256": "089243422dbfc16fe28218ad83c349ffc31b61f3fad37395d1e9488a514fbedf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-lifecycle-remediation-attempt01/proof-index.json",
      "sha256": "37a1ea971c051c9f55becf75bd7867dd6a42341308f7e1ee3a0b8713fadabaed",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-attempt01/proof.json",
      "sha256": "2bf0996d84b128a42d84e61e04fafee7ac872f306a24c450ce951f5c7fd47446",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R01.json",
      "sha256": "d3f2897d5450bd2106bed132a1bdede96dddb1a93d64abb171cfa6c8afd6e7ab",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R02.json",
      "sha256": "461ae12c2aa5aa01cfb5029c8bfbd5095051ad024ec101aea35daf3e91fddd8a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R03.json",
      "sha256": "b49e2a8f18f1381032c83ea6e6e1f610bbe261581bca9a23379bb120e3082d71",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R04.json",
      "sha256": "a99ddf8dfa5814a9736aeae5c9146b4a610c97a0c9f518a357bf8d9250f94e55",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R05.json",
      "sha256": "be7ff31cacc884248e308d9889bba410cabae1918dde97aa189e5e50b11c4883",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R06.json",
      "sha256": "657e6124c2204516a20f7a8809996aa68223c6dd8d6542fc28073ee6a372c619",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R07.json",
      "sha256": "ae54b08960ed8967b0fb58951b9dff05805fa765d2b405fa2db5d51e7eac1548",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R08.json",
      "sha256": "c5955bb62df196880699d2ab54b1bae69ce603760314c3fb99ef49eafdad4c20",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R09.json",
      "sha256": "7d62954b781f46f4e15c34cb6b2cf501233e428f904d5dec675a7f968ee37898",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/R10.json",
      "sha256": "b43a7cc7153f3e4eb8658b0806f70921e3392b303d553830dbbd8269606dea55",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/candidate-integrity.json",
      "sha256": "5b9aafb05236c8c62c94ec9f97119f358ef5460d50ed1d0250ba479a127b4683",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/proof-index.json",
      "sha256": "09fd7d014a996372bf71d97628cd7f19b2c3392d36f3f2995379e1a2c1f1b9a4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt01/test-runs.json",
      "sha256": "6bf2b7ad99c7427d588025de2b7806eb4fff24696e184bf4c23f8837a15462f0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt02/R01.json",
      "sha256": "931339f169e1f84fb9a017fda6beac1e5236a3407dc7d01fd1116f015b0ea4da",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt02/candidate-integrity.json",
      "sha256": "31da81e37804caff7aac1edf97bd17d6f3279fa52c82e13b304139513d6eff44",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt02/checks.json",
      "sha256": "31455b3914cf9e24f53fa7bdb0166f07e208d549ee697fbdc85a25f7d408d494",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt02/reused-proof-index.json",
      "sha256": "52804f165a781daa38c72cea497eed9e34b582230ddf25ae0baeba96903d226f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt02/reused-review-original.txt",
      "sha256": "adf936f587cd28251efd196d5edb39cff7b47ca4a5056d8668da5f6995e7db2c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt02/reviewed-backend-manifest.json",
      "sha256": "72eeecc45b2f49de16fbfc423e2b16a74b1caa4ea84252c0910f7ecda4c00c4d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-qualification-resume-attempt02/run-index.json",
      "sha256": "a200a86fe2fbcaaa3262813aec8bcce36bbf01896ae932bfcade048e3ef7c049",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/ACTIVE.json",
      "sha256": "94f9550ef526f8e9d840df2d51ec5c76a2bb5e2c44b8c57a2e7c51f903a34fda",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/AFTER_DROP.json",
      "sha256": "3a886c8627632dd89856b12ae048f6ba25a049decfd9c2291b410409258bc071",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/BEFORE_DROP.json",
      "sha256": "0c790aa7518dcbcea6698603a8b24a9c5558b6cec3a9112b98964af964c37300",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/LATE.json",
      "sha256": "1eaf02a99080b6f30c43d93985c01dd3625e189372659d6380d2b94a4ef53b03",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/MAY.json",
      "sha256": "7e9be0f44014361840a5cb5349fa166798009beedb603f409d1d58abb6c18977",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/R10.json",
      "sha256": "5f12b3ccd49728a9dca8cddf118c29258a57212fa8e18c053107e4741c9fb768",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/RACE.json",
      "sha256": "c8fa898c84ee31f927b0f9574b6d804ef106118f46640b4be9ca9807f1183d12",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-20.json",
      "sha256": "58fe626600c752ca6ad4a9c257a09b7b8265206faa1d551374ff64c65a3298f8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-21.json",
      "sha256": "a0bdab26d6575ec92f68807dc785b7d27d41f28d307bac79c050310a3c2223b2",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-22.json",
      "sha256": "6f58b2e8a505bfc61803e2c678ce9b83daff69631b7d0e408000dbc9241af4cf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-23.json",
      "sha256": "93dbeb70d30732565671a41a32463e8a8c5ec68b9f9bf1b28ee73c0496ee333b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-24.json",
      "sha256": "46f23c944efbd4c6eb0eeadc8805b9520212763ca2528cd45e343c214e9a16ac",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-25.json",
      "sha256": "d38c9a82346cee4fd4b8f8d9f75d41b68177cceb6bcb6d2c4f7127a9f2d43190",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-26.json",
      "sha256": "3284e23925c21975fa775d671b30657dd9d4bc276da2c5327a5a47b88a7a73b3",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-27.json",
      "sha256": "dbeedd62f88c0daf4f472a9828db245b51276528140f197ad75b9f745faef052",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-28.json",
      "sha256": "f9d4bdcabebd7e6537c3b3173c336431c57d4fb6cd0f3210c54974d0d7ebff86",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/affected-29.json",
      "sha256": "10df4ce64906db17772c6e11c055e956757b6967fc403aa8671fca5439d4a7c9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/candidate-integrity.json",
      "sha256": "e345db7581228039d036622b332c60bf5028a5bfefda8cc579a360f894634e3d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/checks.json",
      "sha256": "39e68b458d04e9659dc6a9a90e4c1a9d43e50252c8d4d9cfe7e452acbf05fc8e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/full-maven.json",
      "sha256": "5285c2efbd8063b274972a1d783bf8204ff42b77987110adac75a69649423a5c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/full-skips.json",
      "sha256": "f279ab48bdf7a657613b93474f2a0d2856392b0cf6ca0d62595e0e08a05e3a57",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/proof-index.json",
      "sha256": "96b075ad39ef4a27084212f7d0899a458e6307e28d4cf1763649234a271f13b5",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/secret-validation.json",
      "sha256": "d7439e626c2b63b6a3c4ec855098114fcb0d6bf437229febf3b5faa7338f6c6a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/test-runs.json",
      "sha256": "2cdf35d014fd3d3e17c860c48f34b1c4ea9115f70310efa26993bd81d146e599",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-strategy-run-recovery-attempt01/tested-manifest.json",
      "sha256": "563225520328ed702f1c8201a6166d9fad69eb9e63f7cd6607ee5a894403dcd5",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/AFTER_DROP.json",
      "sha256": "56db4e118ef7e99c7b14a9a760f83e189c96a552e42a5099db0e10eca90250b6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/BEFORE_DROP.json",
      "sha256": "e41d15cd8c83f2b2c4869aa2177d41f9395aab0a0a2462be8129ff83c5886fd7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/DEATH.json",
      "sha256": "aee5062617093150a81ed79d62efb9e5bec30c239cbe75e610e865d42daefb33",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/KILL_BEFORE_ARM.json",
      "sha256": "13b3cacc1ab38e7f3313804a40e8411e0d823546e8710046899fa3171b48aa0a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/MALFORMED_ACK.json",
      "sha256": "3f215c790fdf14c34b5b853817a74701a9dd586bc1920e245447ba495f4f1647",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/ORIGINAL_P1.json",
      "sha256": "8683daf0372d7ddab25dafc3d30717ef7dbba6793da4fe6171764a5cf1027743",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/RACE1.json",
      "sha256": "09c12e527170bf0a459a602f57a891348c5414057e8e13f4ebf253747256cdc9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/RACE2.json",
      "sha256": "0b3bf6dc2b53bc93a67f76fbcb39dde4c104513a20723404a5a185b0d2c9e9be",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/RACE3.json",
      "sha256": "961650234393929511b878b483165aff0629f8ef58def3def5a27e9b84b34224",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/REJECT.json",
      "sha256": "d8d5c7c24932c1c477d21ce28007df2f62d4f944cfd2523744a502e33114400d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/ROLLBACK.json",
      "sha256": "2de9fc207f587af8962489559ad1690a9ec010ce73b4a82049d04a575752bd37",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/SENDER_WINS.json",
      "sha256": "7e2230bf0f14d212f9238b92b1a8291915ecf4c5ef7de4ed9a2a25ce974b2714",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/affected-regressions.json",
      "sha256": "2c4f72cd20b49798ed53b86df7bf528e908a81407f12da3a804c95d78d6e65d6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/candidate-manifest.json",
      "sha256": "d1be8d6022fe52ed6eafc24274eda43e0a270e0f357a916c59ba2b9a0e39d9be",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/full-maven-01.json",
      "sha256": "7f6b1ace7c32748a467187c58e4a89f4c92fa61ed90618cafe67a0fcfcfa0962",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/full-maven-02-skips.json",
      "sha256": "182b8b1822cc9ad2313f73ab04bf51c854cdcf723207a0c810a29461af9c77c3",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/full-maven-02-tested-manifest.json",
      "sha256": "d1be8d6022fe52ed6eafc24274eda43e0a270e0f357a916c59ba2b9a0e39d9be",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/full-maven-02.json",
      "sha256": "362c2fa539ad4bb40ce3ae2d29197bbac3008889a3673fb897501056426fa70c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/full-maven-tested-manifest.json",
      "sha256": "d83cb4e6d103fa88d5fad7e915224a93f490e35ffc40b87be15237569e0f88bf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/proof-index.json",
      "sha256": "f0e4b040f634fd0614a0358733da17bde93e021a138df356ad4558cfea5e4f48",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v49-implementation-attempt01/test-runs.json",
      "sha256": "98d23fe5a683e4263b68b2500e5875b7b9897aa013729a01497886f81382ec18",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/checks.json",
      "sha256": "8b9e684a3d215353d9137ef42104ed0ebaa03d3e2f14bed1452f1d40b06cd892",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/final-identity.json",
      "sha256": "5fcba53e4c48f4c87d36233418ab63cdc46aa452233c03538bf9c468b23ded36",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/full-01-result.json",
      "sha256": "cb5031bf551a03317369314e193d52c4df81f450eb06fda05167b74c9b290b0b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/full-01-tested-manifest.json",
      "sha256": "95a05b30226247c1dea396a57fd3a4e5252cfce29f5f51277c56b752aab54e25",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/full-02-result.json",
      "sha256": "998ec8dd14c297207f9921710a47b5c32606c4833b917732348bfb73a62301e7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/full-02-tested-manifest.json",
      "sha256": "1397f0312bccb7101746676ac4057149b3f85f38a44b01b7134d9e93b72a214d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/manifest-export-validation.json",
      "sha256": "16c2455266a28d362baa1e2a88176244ebd2e56c5d6607caca741b0fbf486dfa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proof-index.json",
      "sha256": "e07d4f8b64f97c838258c4948ba5fc300f1a6ef80b4c155e831a447b5473403b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/001-lifecycle-02-5b62aa03-4534-4f81-b935-0f088bc333ce.json",
      "sha256": "765b8e1a30fd2d2f8bb31cb14cbc6b643a2fa5e74cfeedc3e1b1bfae86a839d4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/002-lifecycle-02-0bb4ac39-53a3-432b-ab02-aec982af7ba2.json",
      "sha256": "29cd2d14c754f11db113e41bec0bf97f793208faf1919deaca4d5947a4f5cc7d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/003-matrix-01-f2ea3317-8b2e-40bb-9b95-331f2777a19d.json",
      "sha256": "1bc551bbb8e41dbef9a9db82e8c928edb6963f6f61b0b95aa0e92809fb3a51b4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/004-matrix-01-5aabb075-9eb5-41cc-8d20-b8b84878fe9d.json",
      "sha256": "52f2fe24ed78ddfa73faa95dedde069d17b825c6284049892a8a87ae0870b56c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/005-matrix-01-d1a8afbe-cd14-4e57-b717-a8bfde55392a.json",
      "sha256": "0e8bf209ca92fb95dd78d1f452112a5c1a74db4d7fc73095df3d5febf5a9a384",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/006-matrix-01-B_DEATH.json",
      "sha256": "dd6f092407ca3c8baed125fdce7165299a607f2146a721e0d39865df0818590b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/007-matrix-01-B_PAUSE.json",
      "sha256": "6295bf2ea84f1d6292b2d0f9bae0e73fd2948046f8b297a9a65847ceb2083ab9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/008-matrix-01-CANCEL_PARTIAL.json",
      "sha256": "f9c30da3971d166083c3c69cdbead6234da8f55546fc79c6f3060718864c16d0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/009-matrix-01-CANCEL_ZERO.json",
      "sha256": "3f63d47779e37452b857613a9d2c2c90efb1e68ca06cd3c1f9188d352e4be9c2",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/010-matrix-01-CREATED_PAUSE.json",
      "sha256": "1ca2b3b86b4ddf364069827350ed0c1615e71fdade480077de493c8a6cc78e5f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/011-matrix-01-CREATED_TWO.json",
      "sha256": "f8abefeea6e3884dbb978c68003e1354280f17fd8c6dccb9e503aeabaccb81ff",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/012-matrix-01-KILL_CREATED.json",
      "sha256": "37925f64cb1afc95e9afded896758c08bd4639373ccb905a0fb3db29b76bccbf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/013-matrix-01-MAY_DEATH.json",
      "sha256": "c7d28931688a3d42b8e5d0c223f62e3cf71c69ac63a3aba7cbc1ee6aca53b40b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/014-matrix-01-MAY_PAUSE.json",
      "sha256": "783f7cc6095c17c3084c521c35133bc2c718a41d2651e6f11880474ce59021a9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/015-matrix-01-REVOKED_PAUSE.json",
      "sha256": "9816e33b372757cedfa288e4f447ff736b4bc6e7a5d8ad030e9b3b25ef999a09",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/016-matrix-01-TERMINAL_TWO.json",
      "sha256": "4c287bb17e11f74b18f1baec6bea10628c4e46cfce0cf2777ba2fd1751d00c5c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/017-matrix-02-B_ROLLBACK.json",
      "sha256": "2312455ade01c925792fcaa306415ddee7b8a745656e60abbd24a952cf13ad6a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/018-matrix-02-KILL_CREATED.json",
      "sha256": "87303d4efcce3583bf9aa2cdb6ac3c907e3fc4d013cd2cb52188448d3e52c7d4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/019-matrix-02-RECOVERY_SCAN.json",
      "sha256": "c8cd2e5f4065ca4cbdf96eff3370a9acd14a32943da877fd0e14c29c8600d80a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/020-commit-tick-01-A_BEFORE_DROP.json",
      "sha256": "b7432e1721dfaacdaaa63ee85a31c5672d648f94256b5d8d7481f1498dad2ae5",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/021-commit-tick-01-597da9e2-cbac-4ea6-ad5d-6487590b9de7.json",
      "sha256": "f6c5a542e6f74b1134c287a573255a1111b8e0c331f90f9805fcf7d6f624cd9d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/022-commit-tick-02-A_AFTER_DROP.json",
      "sha256": "36eeacbd7fad0ee54ae1506d0fd8b38ee85b14e8212de3922cb883342f68d2cc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/023-commit-tick-02-A_BEFORE_DROP.json",
      "sha256": "2497949a2128aef8123035267aa90ad5c392b4ced2b23da85edbb431215249e4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/024-commit-tick-02-B_AFTER_DROP.json",
      "sha256": "592a5c463f440d79cbfc168c0a51d53dcf91e80680262053fd92c85701d7dd27",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/025-commit-tick-02-B_BEFORE_DROP.json",
      "sha256": "05d72e381bc0f0c187bf1008b105fb3246c1f245296f640e70ce324289ec2a54",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/026-commit-tick-02-C_AFTER_DROP.json",
      "sha256": "e0f72e4f44ef29fcba0361ae9b3208a08f945a02bf0cef1779dc0ba08f9f7b26",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/027-commit-tick-02-C_BEFORE_DROP.json",
      "sha256": "774c428bfdb0b2f644b4d5a8f275a6a573c767f12645c23c63d3d1975526bfec",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/028-commit-tick-02-40b3ceb6-3a46-4e68-bea9-488ca95728c4.json",
      "sha256": "70f6029bba50c35015fd9fee6cc10e8be8fd63fb6f0cd18e05389034ff2111b1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/029-b1-b4-v49-02-L4-AT-01-1.json",
      "sha256": "cc2dcba81271491de38ae8cc6ae27be89908826ed06a277c8b6dfa557c2c34fd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/030-b1-b4-v49-02-L4-LA-02-1.json",
      "sha256": "699a03b926c9ff7798f7278bb512fb0c5aaf58e6d2dddb7b0492ddbd24ff0381",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/031-b1-b4-v49-02-LIVE-MULTI_PARTIAL.json",
      "sha256": "c80ba2d3e3f760c3bc6816fb805fedcc8e54b40508727052f266a45e9ffed26f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/032-b1-b4-v49-02-LIVE-STALE_PLACE.json",
      "sha256": "efce3e41b1243fc6905fee30fe64ecb48d206dcc58c75388bfa54acb3f1e41cd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/033-b1-b4-v49-02-SIM-MULTI_PARTIAL.json",
      "sha256": "475dde2910eab4c39a64bf05e542457a094a5061a2bdebe4a6cba87c60ab287f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/034-b1-b4-v49-02-SIM-STALE_PLACE.json",
      "sha256": "03e3ef5c66c6ac04034875e3c26f92c86820a7b772ee5e16c2fa5b4753d020e6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/035-b1-b4-v49-02-PRE_ACCEPT.json",
      "sha256": "87c6590c7f4dab2e98e6ec8d55856db6b208b6f49a5e2dfff210f912c89ea6f4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/036-b1-b4-v49-02-RESTART_PRE_ACK.json",
      "sha256": "91f705e69fe8d164619df7cb2606d4450a601887f14267473455edf8170443c4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/037-b1-b4-v49-02-1a0c262c-970d-4a88-bb3d-acc4a60d67b0.json",
      "sha256": "ad868ce07409602f22e8725ba4b6ee0483a26f59fd2f604fc10b28a502930582",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/038-b1-b4-v49-02-41870ba4-5bde-4eb3-95a3-22bfcf4a1fe3.json",
      "sha256": "a782801da73a1a8e74c0faefba75581fd2d2872b5f867f8e4d7c4dd225d80d8c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/039-b1-b4-v49-02-AFTER_DROP.json",
      "sha256": "61b203d8290a0b69bf467b611d53bf3cac6d6250ec2f336bc4898ee78e4ceeda",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/040-b1-b4-v49-02-BEFORE_DROP.json",
      "sha256": "87d40c4d8b0a8d1c3c91e94d10a924967e79e8db05a18975f57fd56f43a0643d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/041-b1-b4-v49-02-DEATH.json",
      "sha256": "91856c8a394d1189c5cd220cf092777e28b7926314634e7bd40922026c0b16fd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/042-b1-b4-v49-02-KILL_BEFORE_ARM.json",
      "sha256": "4498a811a230759f94ab17c1cdfc7528198136ddba11a440e93d042a720e34ba",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/043-b1-b4-v49-02-MALFORMED_ACK.json",
      "sha256": "53be216da3766a8d1da50a79b9d85fcef689df7d4d7c20baf6036c29b3591a44",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/044-b1-b4-v49-02-RACE1.json",
      "sha256": "55265df7f4f9b83538c0ec62385f6cc010dcfec0465d504015550166f25f22e3",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/045-b1-b4-v49-02-RACE2.json",
      "sha256": "3adb642fc4711a2c98c6d45cb0606cc4da183e8fa82bb04280038084cd70ac7e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/046-b1-b4-v49-02-RACE3.json",
      "sha256": "0bbac810e74c721277ea2a6a44152ed5c70e01b3dda108ffb82b41ca6278fa42",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/047-b1-b4-v49-02-REJECT.json",
      "sha256": "a063a3be336b68f2358b8c534f83cad771b4cc46126291a0aa09e60f8a5e5174",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/048-b1-b4-v49-02-ROLLBACK.json",
      "sha256": "132d7b13a9440420d6225d91e298e67cf7bf00ecd1041fb2674a90e970883e25",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/049-b1-b4-v49-02-SENDER_WINS.json",
      "sha256": "c60eb165270536cd8c15a07b0288374eddce84a3b959df37bd4abad588d92bc4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/050-final-lifecycle-01-e225e447-7b81-47cf-a112-8186d8585af7.json",
      "sha256": "16e1e71c1bbfeffe333bf353c00150860f2b01e09d449a852c62c31c0d9c50c0",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/051-final-lifecycle-01-0056032c-ca49-4223-b1f4-8e2d51df826b.json",
      "sha256": "a43b918126f7db55bbbff62dfef3cff870feecdc5529a4d757d698a4711a2f49",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/052-final-lifecycle-01-2ca0e2e4-41e0-4e82-8be1-da0c37988729.json",
      "sha256": "03c15966401331791f2bafdeb87548070d9b93c38f20c72dfeaef0f33da7db8a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/053-final-lifecycle-01-CANCEL_PARTIAL.json",
      "sha256": "9b4ad03a6f5884377413d99efd566cb86fd5f968eef0345c1cdd9c22de3281a7",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/054-final-lifecycle-01-CANCEL_ZERO.json",
      "sha256": "d0ae8c61f6324579bc1bd224a7831093f6649ba697371286c5fc323f3c5d244b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/055-architecture-process-01-A_AFTER_DROP.json",
      "sha256": "5e90f378b5dda00ac1e305217693713b21038187277e02a484e9e27d1d0a6105",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/056-architecture-process-01-A_BEFORE_DROP.json",
      "sha256": "765945e7854fbe3866ca66c78103cbb884ac2b04445d5def6aeb9d8cbe4f30fa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/057-architecture-process-01-B_AFTER_DROP.json",
      "sha256": "47c67f61c5f607f7e2244098a0ab8036905bb62f894d77c929e15c6b54885be2",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/058-architecture-process-01-B_BEFORE_DROP.json",
      "sha256": "0992b739a357f5b9f0507e9885f294d0451ae894e92a1d85e84669c7b0b65397",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/059-architecture-process-01-C_AFTER_DROP.json",
      "sha256": "c7f1a4cb4871bc030cd546f5e496fc5d40014aa5c9af07429b845624d1969391",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/060-architecture-process-01-C_BEFORE_DROP.json",
      "sha256": "ff5a279d50d163ba66aab41804b5dbb285a977714155ef6408782cddd01797b8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/061-architecture-process-01-28939251-0186-46b5-bdc0-b032e62499ba.json",
      "sha256": "496fabd4f21f5cbf1d3b1984c180d07ae203e41ab336ab2476cb42c8d82b1b26",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/062-architecture-process-01-B_DEATH.json",
      "sha256": "e7559f95390048b1c5487b3baa803a21ff97c9acf9a59a6db23961e40057a0fc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/063-architecture-process-01-B_PAUSE.json",
      "sha256": "432944ebfbce8055b23cdcffbdfceedb304a7c7b8fde737266cb6a3b49db7fbe",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/064-architecture-process-01-B_ROLLBACK.json",
      "sha256": "bf01dd11cea2dd410e642b345f16a5a40f91cc7bef4539b6639115fdc5d14374",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/065-architecture-process-01-CANCEL_PARTIAL.json",
      "sha256": "6cf51282de077493ecde8cc699bcc4d9f5bb8c432cffbb2c26ff04b0898d9b08",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/066-architecture-process-01-CANCEL_ZERO.json",
      "sha256": "20da3e04e4305d314b926bf968f5192d21378c51d9b67736516351ca9f82771d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/067-architecture-process-01-CREATED_PAUSE.json",
      "sha256": "ea43bfafef7e421952683ffbbf9ebf8f271e39ed9da7ecd278d2344744b0eccf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/068-architecture-process-01-CREATED_TWO.json",
      "sha256": "1ac38e3f3d8042818d451b810fba26e8c94351fb190961d55f870335ceee5696",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/069-architecture-process-01-KILL_CREATED.json",
      "sha256": "faae5763014e3c5fb1eab3adb0102700f7b72ee7f2acd88725cfa6cda2d21dbc",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/070-architecture-process-01-MAY_DEATH.json",
      "sha256": "02da4eb9ecc3f46f940bb69e0bb73f57a0b4e45579833ac7a8b81a4986173e94",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/071-architecture-process-01-MAY_PAUSE.json",
      "sha256": "53014c5c1519a3c4cf26fb76e7a0dfc85c9d4da96bdea58d32ea4d20ba046e7e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/072-architecture-process-01-RECOVERY_SCAN.json",
      "sha256": "92cc7fe0c9d5ba15c72ed8ee967ab8362e0f63910aa6ba0d688d67b7ded756d1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/073-architecture-process-01-REVOKED_PAUSE.json",
      "sha256": "0ef9cd9dacc43adb583c2814a1cb86bd7aa3699676b77bb7edb37acf1ddbbadd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/074-architecture-process-01-TERMINAL_TWO.json",
      "sha256": "63eb0b305dca55499d60084de91efdf9791299a1506784e62ef12c7c70b84954",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/075-architecture-process-01-a3077145-b5ed-42c1-9394-25dc1d973626.json",
      "sha256": "daf0f8cef7374dd1659584456506c507933aecc6e1d574fe01ba0cc9883043b2",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/076-final-lifecycle-02-8b1ea13d-4022-407e-835b-cbd84a2d94ea.json",
      "sha256": "7a1a14512bd85daef8293affab91c25ef1b4c12679ca7eb5d4d0b7d1cb3c8649",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/077-final-lifecycle-02-29a293bf-ab4f-446e-b5e4-049f43f67a27.json",
      "sha256": "625a83c3416f0fe684941a7269ad5bb84a34bb53c778cb59f5681faab7034d53",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/078-final-lifecycle-02-30048040-fec4-45bf-b488-2a9dd3450629.json",
      "sha256": "439c3773cd659ba69d4afaff06e5460992de5c776a88a27f7ba0c75fe5e3be39",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/079-final-lifecycle-02-ee05314e-c9f9-4667-ac76-b23115fa1be8.json",
      "sha256": "9c34e078b226eeb488352ba093bec4bf449c42ad13068ec12a65249ff9b634fd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/proofs/080-final-lifecycle-02-ca53a025-7eab-492a-8c68-ea79bbb74b15.json",
      "sha256": "14d3d3a72d0825143d60b8cc9c367914aa896da7f67d4255c184c70834c61c44",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/protected-manifest.json",
      "sha256": "3706ff785dba83820e4af1e2aa9c068b7edb72fe4a5266f1aa6a0b1bbb0dde73",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/scope-manifest.json",
      "sha256": "9a2ad94ba8fbc8d7dac31ad5aa01149743a3b8a718047a0350618ad0f5912ab4",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/secret-preflight-02-result.json",
      "sha256": "dfbf95d1d648041613947f62432aa90648ea241a4fd260a346035e8fc8e966fb",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/starting-files.json",
      "sha256": "d14744bac4764a866b5f64de2adaf691a9e8f04c7b4a534165d33535ec98d7f6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-v51-implementation-attempt01/validation-log-index.json",
      "sha256": "4e53e9e38aaa893569b0c8b56b7c65bfabe874b68b11b9ba7f4348f409353612",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B1-V49-R01.json",
      "sha256": "78c2e3003b73e01d4fcbebb2842fe9207fdca3b613124c6e128b918ae6e0d799",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B1-V49-R02.json",
      "sha256": "9a973f4769252b577e7c98cb419931e73bc828e39e9108a808d7752dfeab9f8c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B2-R06.json",
      "sha256": "6288d8174568f6089395d9cad6db6a609f7e83dc6c561a218f59b20f960f61df",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B2-R08.json",
      "sha256": "7aaa0a2252c8ccadf50fc34dc0ce287c18b6e7ddb6b168f23156407cc53cdc36",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B2-R30.json",
      "sha256": "308b197acc3b498b54e7ae7c3e9c881660bfc0df15fbe70ecfde925e55a9141a",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B2-R32.json",
      "sha256": "e26dfc8aae9c9f5042ab19a86cd49fd0e656eb0985a68c7a31cdc713f2c78b42",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B3-R01.json",
      "sha256": "a2eabaa260407aec4b4978442ae34efd6c77733241f3ef544c80c4e37295af5e",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B3-R04.json",
      "sha256": "78b9d0ea92ebb33f2280420da4a0cfa5fa11f6db716a51d9cbde2520a14771ab",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B4-R1-R01.json",
      "sha256": "57685da792450bde4181db2f39c026a6386eb38e23b79ddbe6c1d37794c6de42",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B4-R1-R02.json",
      "sha256": "d0e67ab5be9492f9029e745982154d3d57657df4247cdaf8d516f68babc5cf7d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R01.json",
      "sha256": "868773ac2d6849725a723e71e3fa1808323cd53ebd888aa11fe3adda01c94d68",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R02.json",
      "sha256": "818fe0a76dc68857888ed4d47af6c95ea2a6b582a2e58c1f27c23c2f2ca2392b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R03.json",
      "sha256": "b028c5ecd167fe1ad46d88e5900592b79ab827b8b18162babbc09393e1c2096f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R04.json",
      "sha256": "f7174c9b58da887e0678bf858e4089a0bbbfc3ebfed5dc1a47d9d2966b4cd94d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R05.json",
      "sha256": "f074850e46d11cd4c8ce568e60a050c5ddeb09307da660c424b633895fee3b35",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R06.json",
      "sha256": "90609c9052638a8b3f7f76d72b69fdc7ae882829faa6c3be0b1cd2164bcf5513",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R07.json",
      "sha256": "8e2b8d5e34969c782715349e14a663c5a295832ebc664f31805b1eca4ffbff67",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R08.json",
      "sha256": "75b9d48870095b58f5145bdc0fa47cfd72ae04886534bf6d986d428c035b20e2",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R09.json",
      "sha256": "d76a11194d24eb73a0d93f8bea911ebc9ace05168cb1be78330d4d0256f587b9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R10.json",
      "sha256": "9fc2f1d3e1a7790d2346ff17771361080cef87ac8df04e5e68c8b5ca6b39462d",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-IMPL-R11.json",
      "sha256": "f05e0dfca60dfff851baf6df08bcfad39c84bd0a3a6501e06efff1b05e8278b9",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-LEGACY-R01.json",
      "sha256": "32f8dd7f6167d7c15f0195610939932abcc521aab4ba1c552d4b2fdddb3afbaa",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-R01.json",
      "sha256": "d4f340bcd71b0e676f7ab2faaa99b9dbc27a5ab62c1bec69a9fb115fde3e54b1",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-VENUE-R01.json",
      "sha256": "175431cafa34d32ef04cdc823756c0248ea546f5481698734a3a532d38caa03c",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-VENUE-R02.json",
      "sha256": "d2f369f0f41f13d1f88a798ac78beb806529214da9ecda9a1a0c3e2719dd1fd6",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-VENUE-R03.json",
      "sha256": "c3144ee605e62c70ffc4457e7f5ff2981df95a350a9561665231112d786ba727",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-VENUE-R04.json",
      "sha256": "94a7ac7d8f0cbcee9c98dbfb0c117046ee7997714630f5cf682b7852edc8165b",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/B5-VENUE-R05.json",
      "sha256": "a6d9581bb456624213939fb26fcc60099ce12eea350d4853643d9959c8776961",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/candidate-manifest.json",
      "sha256": "096c5a9a0165e39c447609fd1cba9a78f78af1f7e0c40a158ccf92efedf2bd1f",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/full-01.json",
      "sha256": "dbfb8eece9956efb56f17652c85f403028fc78da8b90def50503b207d563c063",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/full-02-skips.json",
      "sha256": "84af8ca9bd76c675294fe741a655bafb1f7db8f822545cc28d5be20eef5c4e24",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/full-02-tested-manifest.json",
      "sha256": "3c046024ee4d8c395530a38c0dd0173d81ca6a2b506f517ce37008177384a9cd",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/full-02.json",
      "sha256": "4737a4e9adbce029522a771911638ecc406947e708e28652a4fc3c3e8802c813",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/hygiene.json",
      "sha256": "cd1c1b9814b22d5cf9d0c57b8f39eeb7ef4b7bb7747633d7bc26774821dd1891",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/original-bypass.json",
      "sha256": "19ae548d26a880e3b6083c35fe9aed4df55f37ebb2fdbe61c727d03e255763e8",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/proof-index.json",
      "sha256": "d1cdb7ddc18140a5e1174c0226ce3232ad4a73ef65507aa3cf7c03a93b37bbed",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/review-attempt01-original.md",
      "sha256": "6146e6f8bfc0eaacc36798c8455f30f76f4c5cf774db184534b130234b7a88cf",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/audit/evidence/l4-b5-venue-canonicalization-attempt01/test-runs.json",
      "sha256": "464deae611a58ae8f8244374e0740dc295aa656a8fb7358262e2046523ef43be",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "docs/current/API.md",
      "sha256": "0dc7bd8976bab5ff8709a28efd2fcea719fe62422cd54fc94745e21bcac19618",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "docs/current/DB_SCHEMA.md",
      "sha256": "091ddbd7e93dcbe0de6d0e12a26b7cb4fd794b28bb7b906a654e64b30b0b96df",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json"
    },
    {
      "path": "frontend/src/constants/filter-options.ts",
      "sha256": "6d8a97d3da45ef379fde53b4ff826da3b5ce5eb40e280acf2d7ae653671f60e6",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/starting-files.json"
    },
    {
      "path": "scripts/docs/check-stage-assets.py",
      "sha256": "6909e86621198b3ae9e13db46f177662e0b95756b91531a92862cea9d42fbb64",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md"
    },
    {
      "path": "scripts/docs/stage-asset-exceptions.json",
      "sha256": "bb71d107e8ecea333f67e6ca9b06dec3c9bb26427b2882dce5e142b3b227be09",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md"
    },
    {
      "path": "scripts/docs/stage-asset-lifecycle.py",
      "sha256": "66a00f075223a0f32ba5cf1b6e620576f4c4a6160eb992fd615cd9af58bad3d2",
      "change": "ADDED",
      "canonicalSource": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md"
    },
    {
      "path": "scripts/docs/tests/test_stage_assets.py",
      "sha256": "9334ba95b87303d716be233eaef6f52a7e57fd2a341fee79f0252910372d588c",
      "change": "MODIFIED",
      "canonicalSource": "docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md"
    }
  ]
}
```

## 已执行的交付预检补充

Pinned Gitleaks 8.18.4全量当前候选scan exit0/findings0；六类secret negatives各exit2/findings1。完整原始结果保留于ignored `backend/nq-app/target/b5-precise-delivery/secrets-prestage/secret-validation.json`。

第一次精确stage后，git diff --cached --check唯一错误为reused-review-original.txt:92的new blank line at EOF。按用户第8节授权只移除末尾空行；保留原始字节于ignored交付目录。正文完全相同，production/migration/Java tests/stage-assets logic未改。清单中sha256保持原接受来源，deliverySha256记录规范化后字节；本页自身仍由最终staged tree绑定。

## 精确staged内容验证

暂存文件728，unexpected/missing=0/0；逐项读取Git blob与预期worktree比较，613文件只有Git CRLF/LF转换，超出换行归一化的内容差异0。原始接受hash仍保留；单一review文本末尾空行调整另外明确记录。本轮技术候选未改变。

Windows长路径使首个深目录archive快照无法完整提取；改短Temp路径后又发现git archive受core.autocrlf=true影响，导出CODEOWNERS为CRLF，导致该副本2条stage诊断。HEAD/index/worktree三者CODEOWNERS字节均一致且匹配原policy；没有stage-assets根因回归，也没有刷新hash。最终改用git ls-tree与cat-file直接逐字节materialize全部3863个staged blobs，canonical validator实测scanned1880 / reviewed_exceptions173 / errors0。失败副本与原始结果均在Temp/ignored目录保留，不交付。

完整安全预检实际扫描3861个safe候选文件，findings0；规范化后的两份docs再次扫描findings0，六类负例仍全部REJECT。staged diff check通过，文档链接8/0/0。后续commit/push/exact-head CI尚待执行，本页不抢先记ACCEPTED。
