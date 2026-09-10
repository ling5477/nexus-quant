# B5 Stage Assets Root Cause Remediation

本任务：DELIVERY_COMPATIBILITY_REMEDIATION / RECURRING_ROOT_CAUSE / STAGE_ASSET_INTEGRITY / NQ-only。

状态：根因修复已通过独立候选审查，canonical validator errors=0；完整收尾见最后一节。未重开 B5 correctness，未运行 Full Maven、B1–B4 matrix 或 V51 review。stage=0 / commit=NONE / push=NONE。

## 冻结身份与来源

起点 HEAD / origin/audit/post-gatey-agent-baseline 均为 `86c8ad84542636364f6c21e78bc292a323cbdff7`；branch=`audit/post-gatey-agent-baseline`。既存改动逐字节保留，以本轮起点比较，不将 HEAD 的既有 diff 算成本轮修改。

[最终资格事实](GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md)确认 B5=CORRECTNESS_QUALIFIED / DELIVERY_BLOCKED；[V51 原独立结论](l4-b5-final-qualification-resume/reused-review-original.txt)与[已审完整 manifest](l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json)提供实际接受来源。[qualification candidate integrity](l4-b5-final-qualification-resume/candidate-integrity.json)区分已审生产与后续测试/exporter delta。STATUS 的机器阶段仍是旧 pre-B0，本任务按用户明确的局部整改授权工作，不修改它或推断发布权限。

下列 aggregate 算法为 sorted path + NUL + raw file SHA256 + LF，UTF-8；production 指全部 backend/src/main，migration 指 /db/migration/。起始全文件快照与原始命令输出临时保留于 backend/nq-app/target/b5-stage-assets-root-cause；持久证据由本页的manifest身份、原144条inventory与最终逐文件比较结果组成。

```json
{
  "startingHead": "86c8ad84542636364f6c21e78bc292a323cbdff7",
  "startFiles": 3860,
  "startAggregate": "2e61e805734623d92f7123c3cafd5915f78b9d5d1f2df99241f8340e47e7554f",
  "backendFiles": 1814,
  "backendAggregate": "52a461e7ef30902abb1946bec659e98a4e5c1d6074ff22b105ded1561a422cff",
  "productionFiles": 1334,
  "productionAggregate": "fa5c39fded99785619caee203128c13861cb3d244ce6f0956b6750362bc6eb4e",
  "migrationFiles": 51,
  "migrationAggregate": "63d3e0da588e2056b2ded73b9835243710167bf46964bfa5d7769c22b52c4d72",
  "migrations": [
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V10__gate_f5_publish_records.sql",
      "sha256": "64aecdd27af80f4decb519b0c408f5c5fbd0011b506ccf638a4609e17aadc695"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V11__gate_f_schema_comments_backfill.sql",
      "sha256": "764c7bc7a050617fb7174ccdc69f7c6c7d4d736fc9c57aa0ba411014829cec6a"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V12__rc1_account_and_credentials.sql",
      "sha256": "f01853087802d310c2a06858b02e5b76c1a2fd2aaee753acb5f2253d9e80aaf3"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V13__rc1_marketdata_bars.sql",
      "sha256": "a779bf0033f72864f40c43b0dea932eb0f25687cb3733fb95e2f6b616b29b55a"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V14__rc1_schema_comments_backfill.sql",
      "sha256": "7d7e1f8ff216c97d477b0b4a934b6930a027848ca1e78dc526925da076b14342"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V15__gateh_pre_instrument_catalog.sql",
      "sha256": "2e5f379dcf156d4ae14f0bc70d014d4e6cf2ab4d26b07b6c902c232dc9de2528"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V16__gate_h2_marketdata_ingestion.sql",
      "sha256": "37154b024df284ec34082f0fefc2ccd12877eea4352b772dde3b8bfb74b4aada"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V17__gate_h2_ingestion_created_by_width.sql",
      "sha256": "8cba8dcf9e0fe21dffa0a9e17e384769f24144de41e57038e301e3b29ce61fc7"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V18__gate_h3_marketdata_dataset_binding.sql",
      "sha256": "c70699c8c14a0194556cbaac9df26ff7554f4605cb1ef2b3a82bbb11fc00b790"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V19__gate_i1_strategy_versions.sql",
      "sha256": "f219f53f4ec5351160bcb94edc8add3a329a18b0ca514c366d24a8d883da93c5"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V1__init.sql",
      "sha256": "9335dd2b2b0c27f107d06f5d6840e04fa9d6263f3bbd4952c3215712105ab0db"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V20__gate_i2_backtest_traceability.sql",
      "sha256": "f99a53dcbc88ac1d6355b0cb7352d5d2d42eda15888d3d9afac2d34e29f0671a"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V21__gate_i3_paper_trading.sql",
      "sha256": "dd32596de8a3194b8a16fd2d9f3bffdbcfeef4f0cabe596477f87d27ba71f7fc"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V22__gate_i4_paper_trading_monitor.sql",
      "sha256": "cc98eb372d72aca37325723ab4adb5b5dc338fa25404bc640db432acb1e4ad48"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V23__gate_j1_paper_run_schedules.sql",
      "sha256": "54b57697f498e5cd76edf17f4b1ab5cabbdeafd57f83f77476b5eb69b5fbb7aa"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V24__gate_j2_paper_run_daily_reports_alerts.sql",
      "sha256": "12c2d797c4e975aed6cdfaee6ce5c9110f413900d7644ff52abc04036b9e49ef"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V25__gate_j3_paper_run_recovery_stability.sql",
      "sha256": "22e3fb4bd769ef3966f8ede8d4035e6806c6d3e0323e95162a57a40bf1a4a2ad"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V26__schema_comment_business_normalization.sql",
      "sha256": "15a1c64b3338394283e594de2519eed4d7fd054c6aeea22352b2d3c06f0ac712"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V27__schema_master_table_governance.sql",
      "sha256": "48ba5c6a1449913a2b271d0af7bd55e41b5438b0a3c66294b7be6063388a70ec"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V28__schema_research_backtest_config_governance.sql",
      "sha256": "786ed21715ff94aa3be014564a65ad3bb6df22d964f95ad991a96a7a7dcb6c72"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql",
      "sha256": "34d4d135aeaac99a8afa971871fe3476da15ef3703a5856979127d74fd4864df"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V2__gate_b_schema_hardening.sql",
      "sha256": "be92ad7f08b3aa4c744f7dac85e099e83c7c6178b24cd00f4f9f433d63dcb7b8"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V30__schema_credential_enable_audit_event.sql",
      "sha256": "626df2b5ab3bd23fb6c2c3f441d405ace2540cceecf354df6f13c0fbaa98a336"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V31__schema_credential_permission_probe.sql",
      "sha256": "76ef4db5c72f4523d7184f692371370e7f29313853cbb931df6a1ab04f4fae5d"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V32__gate_r_shadow_run_fact_model.sql",
      "sha256": "769b5a704c67b5745e24ea1508d7864aa5684fe968a0d4a59a52d84fa4bacf28"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V33__gate_v_validation_review_fact_model.sql",
      "sha256": "a7129642e972a40b0de1494830f8fdc788f6959aa01d7fb045c7347c0037ec43"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V34__gate_w3_venue_rule_facts.sql",
      "sha256": "a9148e1e4e8773fb4cbf899c1fd5d97a467f758f8e7e0200fc03268afb46024d"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V35__gate_w4_durable_kill_switch.sql",
      "sha256": "49bae9078e3ed5c41082e4470368e24b664c32cb0a88d938166f1f21695a72fc"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V36__gate_x2_shadow_run_provenance.sql",
      "sha256": "e213f620ec3f5ece0b43e5ffb327f927fa0241df89d35ede1ad20e43976b74d2"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V37__gate_x4b_persistent_artifact_locator.sql",
      "sha256": "f6628603fcf98dfb8a681d45d1b9c9d70710ce6a8526a9f9a165d4f5fe088d98"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V38__gate_x5a_admission_materialization_guard.sql",
      "sha256": "63ec9b4761cfa37c52c44a914f004444d8509e4a2b03fd5663cf4aeecc34af2a"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V39__gate_y2_live_session_fact_model.sql",
      "sha256": "bb39adae92b55246abda88150d5477c6333d465617e4f65fd2b4834ca4172e34"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V3__gate_c_adapter_router.sql",
      "sha256": "8c0b02ca223d3a81535e017ee2dfc8a6690fdf2e47a1b1c4d3c220a07257942c"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql",
      "sha256": "1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V41__gate_y6e_minimum_order_value_semantic_remediation.sql",
      "sha256": "e97f97f2ef79b9628e952a310170f10bba96899e82781656e5dedfac4c95cbc4"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V42__gate_y_minimal_live_pilot_execution_lease.sql",
      "sha256": "2f6252e85692c67081482237aed73bc6943336b48c7932317a8a34e7d0dd47f3"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V43__gate_y_current_market_snapshot.sql",
      "sha256": "f41dbb3008f00d75320b268488c7898667561c60485fbeb14a5f7bae8e1b25b1"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V44__gate_y_operator_pilot_authority.sql",
      "sha256": "dbf42d59cdb587beec38b47f3c5ca288d116f5e41026d1a138c2d4443af35b7f"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V45__gate_y_pre_place_zero_intent_recovery.sql",
      "sha256": "ffe71370fbd44a1da849fc5eb4f4d289081c6c0d67ffe96dc8b8bad4b775b293"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V46__gate_y_attempt_level_terminal_lease_regeneration.sql",
      "sha256": "fa0ccf7265841949ee77881c2e35c9f64065c1759fcaa3ac3f618cd4ca0b3ea1"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V47__order_state_optimistic_concurrency.sql",
      "sha256": "03e71d89e8ae6f74895982b37de9c3ba9c43b1174bbc8e990b72439b9e0d2d0f"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V48__reconciliation_scan_cursor.sql",
      "sha256": "5147c5b6dddc8b6bd05ce9f10620b2a2f9c9bd1b338d120d1df8da57c983b742"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V49__ordinary_place_authorities.sql",
      "sha256": "ebea77f85cae76d88e052a99b647573ec3b6ba5ea0464f9083012aac2a883b93"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V4__gate_c_trade_external_order_id_index.sql",
      "sha256": "06799e9ea2aed30f17887d8893246aec07cfb7269f94007a754ac7b2e4250b8d"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V50__strategy_window_admission.sql",
      "sha256": "ca87e2b7b0c739b8fae59d701bf0ff54336ee3d262eb5ae7dcd7292edfc45ae6"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V51__strategy_run_durable_execution.sql",
      "sha256": "afbc3211b824b8f717912707b584d2382f47fe9f8df86802e7c4a1c8a6cd9942"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V5__gate_e_schema_contract_alignment.sql",
      "sha256": "17a285342f18aa294e4f41c38ca4cc80b2524c50746e5227bbc96bea72f8d59e"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V6__schema_comments_backfill.sql",
      "sha256": "970f2e18033534c9ef64af66e95ebde765a20054c7f35e7d14ab1233e4cf4c9e"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V7__gate_f1_research_backtest_skeleton.sql",
      "sha256": "2bfc7644686328af20ef35b30a6b37c47a7976c0967cc199ea47eb3a4dd03eed"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V8__gate_f3_simulated_execution_facts.sql",
      "sha256": "4134ad39ef41674927976adffafc616e8fe4f8626d712b3b122577c477b74b02"
    },
    {
      "path": "backend/nq-infra/src/main/resources/db/migration/V9__gate_f4_evaluation_reports.sql",
      "sha256": "1d63d9e5ada06a1f349c057b41dd175f17005e352c0b1f85f8afeb5cff1d6f40"
    }
  ],
  "reviewedV51": "afbc3211b824b8f717912707b584d2382f47fe9f8df86802e7c4a1c8a6cd9942",
  "reviewedQualificationStartingFingerprint": "0502bc83ec532c6f8ec26fb7a21ffda03e986cd6d86018d4b0184eeb55032411"
}
```

## 精确重现与分类

原命令 `python scripts/docs/check-stage-assets.py` exit=1。expected=144 / actual=144；scanned=1879 / reviewed_exceptions=138。144 是诊断条数，唯一文件37。

| 诊断 | 条数 | 机制 |
| --- | ---: | --- |
| STAGE_SEMANTICS | 35 | 既有内容摘要未随合法import整理演进 |
| STALE_EXCEPTION | 35 | 同一个摘要失配未计入 used，从而产生第二条诊断 |
| UNAUTHORIZED_COMPATIBILITY_CALLER | 38 | import整理后声明签名表示与保守可见性改变 |
| STALE_COMPATIBILITY_CALLER | 36 | 相同合法演进留下旧签名边 |

按互斥主因分类：A=144；B=0；C=0；D=0；E=0；F=0（F是跨全部144条的共同生命周期根因，避免重复计数）；G=0。C中所说重复/过时注册不是这36条STALE_CALLER的独立来源，它们属于A的旧边；registry本身无重复entry。B/D的历史证据与已发布migration已经不在active contract内，不需要本轮新排除。E经检查validator和CI caller均读取同一POLICY_PATH，不存在两份canonical inventory。

代表路径：`MinimalLivePilotConfiguration.java` 同时出现旧/新签名边；`OperationalSafetyAssessmentService.java` 的 Set 导入改变包含泛型参数的签名；`BinanceHistoricalKlineAdapter.java`、`BacktestRunControllerTest.java` 是内容哈希绑定的 import 演进。完整路径与owner逐项见附录。

37/37 当前文件原始SHA256逐项匹配已独立审查的full manifest；独立reviewer再次交叉核对。额外比较HEAD与当前文件，移除import、按当前显式import将旧全限定类型归一化、去注释/代码空白且保留字面量，37/37等价。此检查仅用于说明注册漂移机制，不替代或重跑B5正确性。没有把UNAUTHORIZED诊断标签等同于已证实非法行为，亦没有未知来源条目被默许。

## 复发历史与因果链

| 最早已确认事件 | 当前Git直接证据 | 结论 |
| --- | --- | --- |
| 2026-09-06 17:12 +08 | `85d11984` 引入F009 active consolidation，checker声明无update/accept入口 | 显式注册与hash/caller快照建立 |
| 2026-09-06 17:21 +08 | `dbb8b9c6` 仅registry一增一删；此前CI `34024011663` 失败 | ROADMAP合法变化后只同步摘要 |
| 2026-09-07 | `eb9740b7` C1 delivery兼容整改 | 同类绑定须另行机械更新 |
| 2026-09-08 13:52 +08 | `f3cc63b9` instruction stage assets canonical policy | 处理动态清单但没有通用注册生命周期 |
| 2026-09-08 23:41 +08 | `d1cedb6d` 仅registry一增一删 | B1 secret-scan workflow文本变化再次只同步摘要 |
| 当前B5 | qualification resume02与final checks均记录相同144 | 明确保留交付blocker，不在交易整改中掩盖 |

首次可确认是本registry引入当日，不能据此声称更早历史不存在。检查当前脚本、policy、测试及CI显示：技术作者可合法修改代码；review绑定新source manifest；stage注册却没有对应可审查、可重放的更新操作。之前只能手工hash修补，因为工具有意禁止接受开关，但没有提供独立于validation的安全演进入口。代码签名又按源码表示绑定，因此无语义import演进也会改变AST边标识。

SYMPTOM（尾部144诊断）→ COMMON MECHANISM（合法候选与旧注册分离）→ ROOT CAUSE（受保护且可演进资产只有静态快照，没有候选审查到身份更新的生命周期）→ PERMANENT CONTROL（统一算法产生冻结提案，独立审查固定摘要，重验候选和旧注册后原子应用，CI继续独立拒绝漂移）。

原guard已经能在本地及CI较早检查；这次不是CI缺乏检查入口，而是技术整改/资格任务明确不准顺带同步registry，且没有把注册提案纳入候选生命周期，所以问题持续作为交付blocker。新owner规则要求候选稳定后、验收前生成和审查提案；CI不自动修复，保持尾部独立检查。

## 旧与新合同

- 唯一canonical registry owner保持 `scripts/docs/stage-asset-exceptions.json`；分类owner/reason/removalTrigger及18份兼容合同由原policy负责。本轮无第三份registry、无批次例外、无目录allowlist扩张。
- canonical validator仍为 `scripts/docs/check-stage-assets.py`；roots、扩展名、历史/生成/敏感分类与Java/Javascript可达性检查未变。历史stage命名不能成为active运行入口；fixture/domain词汇可由内容绑定的明确例外保留。它不负责证明全部交易正确性或历史evidence不可变性。
- immutable：既有frozen evidence与已发布migration遵守外部不可变合同；运行guard不将其误分类为可自动更新资产。versioned/evolve：受保护fixture、兼容合同调用、现有文档命令与配置，允许同一次候选审查明确批准演进。
- 新 `stage-asset-lifecycle.py` 提案读取同一policy，调用相同inspect、sources、executable_inputs和compatibility_edges，不复制算法。propose只输出临时review artifact，不能写registry；apply需要外部独立审查记录的完整提案SHA256，并重新计算全部候选与旧policy、工具输入和输出。
- 提案中列出完整动态active集合与外部可执行依赖的raw摘要、旧policy摘要、完整生成后policy。默认validation从磁盘读唯一policy，绝不因工作树dirty或review标签自动PASS。
- 对普通失效/消失注册，deterministic移除；重复注册仍拒绝。兼容合同退出/成员变化、新exception或安全数据分类变化必须单独policy review，生成器不授予。apply通过同卷原子替换保持文件完整性；工作区必须静止，不宣称全目录事务隔离。
- 原validator仅增加内存policy参数，供提案预验证使用；CLI无绕过参数，严格schema、路径/调用语义与未知输入拒绝未降级。
- CI caller改动=0。当前ci.yml的docs guard直接执行同一validator与 `unittest discover ... test_stage_assets.py`，新增回归已被现有入口覆盖；没有Maven/Java guard额外required path，JDK AST本身在stage suite和完整validation执行。

## 提案与变更范围

最终固定提案SHA256=`d1f1606c63cd61b42e47165e18e453a133dd2c5a249b14c671f9be2d9f56aa0c`，临时路径 `backend/nq-app/target/b5-stage-assets-root-cause/proposal-02.json`。该完整提案已独立接受；摘要本身不是授权来源。实际机械结果：35个已有摘要更新、36旧caller边移除、38新表示边加入；exceptions仍173、compatibilityContracts仍18、成员/owner/理由/退出条件不变。手工hash更新=0、新exception=0、删除实际例外=0、生产/迁移/B5测试语义修改=0。

预期本轮文件仅五个：validator、lifecycle generator、原stage测试、原engineering-lessons追加和本页；应用后加唯一原registry，共六个。所有原37个technical文件只读。

## 验证与独立审查

原55 tests通过，2个Windows symlink权限跳过。独立review复现一个本轮P2：apply最后仅逐个比较旧文件，漏掉中途新增路径；未导致validator漏检，但违反候选更新合同。现统一bound_inputs重新发现并比较source+external inputs，临时文件移出active roots；新增精确中途文件注入、外部输入变更与caller演进回归。旧失败与修复后独立复现分别保留：修复前apply成功但validator报STAGE_SEMANTICS；修复后CANDIDATE_CHANGED_BEFORE_APPLY拒绝且policy不变。

最终结果见末尾；中间失败不覆盖为PASS。

## 原144条机器可读inventory

每条列出actual/registered identity、owner和首个可推导来源；同一条caller的present布尔值是其新/旧边状态。元数据和完整原policy可从起始HEAD取得，本页保存本任务原错误而不修改任何旧evidence。

```json
{
  "originalFailureCount": 144,
  "uniquePaths": 37,
  "inventory": [
    {
      "id": 1,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-core / CredentialPermissionProbeService contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 2,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "CredentialPermissionProbeService"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "CredentialPermissionProbeService"
        },
        "present": true
      },
      "owner": [
        "nq-core / CredentialPermissionProbeService contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 3,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotRunner"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotRunner"
        },
        "present": true
      },
      "owner": [
        "nq-app / MinimalLivePilotConfiguration contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 4,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "<unit>#<type>",
          "contractMember": "OrderType"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "<unit>#<type>",
          "contractMember": "OrderType"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 5,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 6,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "ExactPilotBinding"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "ExactPilotBinding"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 7,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "Side"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "Side"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 8,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 9,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(AuthenticatedLiveControlActor,UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(AuthenticatedLiveControlActor,UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 10,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(AuthenticatedLiveControlActor,UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "Correlation"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(AuthenticatedLiveControlActor,UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "Correlation"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 11,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(AuthenticatedLiveControlActor,UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "ExactPilotBinding"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(AuthenticatedLiveControlActor,UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "ExactPilotBinding"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 12,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
          "callerMember": "<unit>#<type>",
          "contractMember": "OrderType"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
          "callerMember": "<unit>#<type>",
          "contractMember": "OrderType"
        },
        "present": true
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "fca69b49ee10659217c1af934735e42665095383269310ddcccbd8bb99978ffd",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 13,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 14,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 15,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 16,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 17,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,AuditLogRepository,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 18,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,SpotProviderResults.FillReference)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,SpotProviderResults.FillReference)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 19,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,SpotProviderResults.FillReference)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,SpotProviderResults.FillReference)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 20,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,SpotProviderResults.FillReference)",
          "contractMember": "PilotReconciliation"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,SpotProviderResults.FillReference)",
          "contractMember": "PilotReconciliation"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 21,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 22,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 23,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 24,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 25,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 26,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#completeReconciliation(OrderRecord,String,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#completeReconciliation(OrderRecord,String,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 27,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "MinimalLivePilotControlService"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "MinimalLivePilotControlService"
        },
        "present": true
      },
      "owner": [
        "nq-infra / MinimalLivePilotControlService contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 28,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java",
          "callerMember": "ReadOnlyProviderObservationConfiguration#readOnlyProviderObservationAuthority(OkxPrivateCredentialExecutor,KillSwitchService,InstrumentCatalogService,ReadOnlyProviderObservationRuntimeIdentity,String)",
          "contractMember": "OkxPilotPrerequisiteObservationAuthority"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java",
          "callerMember": "ReadOnlyProviderObservationConfiguration#readOnlyProviderObservationAuthority(OkxPrivateCredentialExecutor,KillSwitchService,InstrumentCatalogService,ReadOnlyProviderObservationRuntimeIdentity,String)",
          "contractMember": "OkxPilotPrerequisiteObservationAuthority"
        },
        "present": true
      },
      "owner": [
        "nq-infra / OkxPilotPrerequisiteObservationAuthority contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "97944c884fe7442db756b3b4cecaf4e8f62e16f872e67769013feb024dfd79ee",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 29,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 30,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_CONFLICT"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_CONFLICT"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 31,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_MISSING"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_MISSING"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 32,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_PRESENT"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_PRESENT"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 33,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_STALE"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_STALE"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 34,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_TYPE"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_TYPE"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 35,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_SUBJECT"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_SUBJECT"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 36,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidence"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidence"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 37,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidenceStatus"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidenceStatus"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 38,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "UNAUTHORIZED_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "OperationalSafetyAssessmentFactBundle"
        },
        "present": false
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "OperationalSafetyAssessmentFactBundle"
        },
        "present": true
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 39,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-core / CredentialPermissionProbeService contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 40,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "CredentialPermissionProbeService"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "CredentialPermissionProbeService"
        },
        "present": false
      },
      "owner": [
        "nq-core / CredentialPermissionProbeService contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 41,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotRunner"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotRunner"
        },
        "present": false
      },
      "owner": [
        "nq-app / MinimalLivePilotConfiguration contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 42,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 43,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "ExactPilotBinding"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "ExactPilotBinding"
        },
        "present": false
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 44,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "Side"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "Side"
        },
        "present": false
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 45,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 46,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor,java.util.UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor,java.util.UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 47,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor,java.util.UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "Correlation"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor,java.util.UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "Correlation"
        },
        "present": false
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 48,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor,java.util.UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "ExactPilotBinding"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#suspendOrFail(com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor,java.util.UUID,ExactPilotBinding.Correlation,String)",
          "contractMember": "ExactPilotBinding"
        },
        "present": false
      },
      "owner": [
        "nq-core / ExactPilotBinding contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 49,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 50,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotRunner(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,long,long,String,ExactPilotBinding.Side,BigDecimal)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 51,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 52,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 53,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#persistFills(TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 54,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 55,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 56,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference)",
          "contractMember": "PilotReconciliation"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#requireMatchingTrade(PaperTradeRecord,com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference)",
          "contractMember": "PilotReconciliation"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 57,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 58,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#<init>(MinimalLivePilotControlPlane,PilotExecutionLeaseControlPlane,OrderCommandService,OrderLifecycleService,OrderRepository,MinimalPilotTradingVenueGateway,TradeRepository,TradeLedgerGateway,com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository,ExchangeAccountRepository,ExactPilotBindingRepository,JdbcTemplate,ConfigurableApplicationContext,ObjectMapper,MinimalLivePilotCommand)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 59,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 60,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "MinimalPilotTradingVenueGateway"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 61,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#alignOrder(com.guidinglight.nexusquant.trading.domain.OrderRecord,MinimalPilotTradingVenueGateway.PilotReconciliation,String)",
          "contractMember": "PilotReconciliation"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 62,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#completeReconciliation(com.guidinglight.nexusquant.trading.domain.OrderRecord,String,String)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration.MinimalPilotRunner#completeReconciliation(com.guidinglight.nexusquant.trading.domain.OrderRecord,String,String)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalPilotTradingVenueGateway contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 63,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "MinimalLivePilotControlService"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
          "callerMember": "MinimalLivePilotConfiguration#minimalLivePilotControlPlane(ExchangeAccountRepository,InstrumentCatalogReadPort,CredentialPermissionProbeService,PilotScopeControlPlane,PilotScopeRepository,com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane,PilotExecutionLeaseControlPlane,CanonicalLegacyAccountBridgeService)",
          "contractMember": "MinimalLivePilotControlService"
        },
        "present": false
      },
      "owner": [
        "nq-infra / MinimalLivePilotControlService contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 64,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java",
          "callerMember": "ReadOnlyProviderObservationConfiguration#readOnlyProviderObservationAuthority(OkxPrivateCredentialExecutor,KillSwitchService,com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService,ReadOnlyProviderObservationRuntimeIdentity,String)",
          "contractMember": "OkxPilotPrerequisiteObservationAuthority"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ReadOnlyProviderObservationConfiguration.java",
          "callerMember": "ReadOnlyProviderObservationConfiguration#readOnlyProviderObservationAuthority(OkxPrivateCredentialExecutor,KillSwitchService,com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService,ReadOnlyProviderObservationRuntimeIdentity,String)",
          "contractMember": "OkxPilotPrerequisiteObservationAuthority"
        },
        "present": false
      },
      "owner": [
        "nq-infra / OkxPilotPrerequisiteObservationAuthority contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "97944c884fe7442db756b3b4cecaf4e8f62e16f872e67769013feb024dfd79ee",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 65,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "<typed-use>"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "<typed-use>"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 66,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_CONFLICT"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_CONFLICT"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 67,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_MISSING"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_MISSING"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 68,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_PRESENT"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_PRESENT"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 69,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_STALE"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_STALE"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 70,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_TYPE"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_EVIDENCE_TYPE"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 71,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_SUBJECT"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HUMAN_REVIEW_SUBJECT"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 72,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidence"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidence"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 73,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidenceStatus"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "HumanReviewEvidenceStatus"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 74,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
      "diagnostic": "STALE_COMPATIBILITY_CALLER",
      "registeredExpected": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "OperationalSafetyAssessmentFactBundle"
        },
        "present": true
      },
      "actual": {
        "edge": {
          "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentService.java",
          "callerMember": "OperationalSafetyAssessmentService#evaluateHumanReview(HumanReviewEvidence,java.time.Instant,Set<OperationalSafetyAssessmentFindingCode>)",
          "contractMember": "OperationalSafetyAssessmentFactBundle"
        },
        "present": false
      },
      "owner": [
        "nq-core / OperationalSafetyAssessmentFactBundle contract owner"
      ],
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5a94fae77321b0b48a81d0685789165c435b827152ba1ef2ca30e205569e888",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 75,
      "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "a1c3858ab7103caa213146b6bb0846def2f622e31f5c2d51e976b80350ad0445",
        "kind": "HISTORICAL_METADATA"
      },
      "actual": {
        "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "d12877d85d69d69d2a3e6024e9d947fda8392b395e5a09b35b241c1c1841ce3b",
        "matches": 3
      },
      "owner": "Module owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5d13cc10b7c298d95b8a247b1c98e5dfc5d775a008677a6f747df9c6c6f8704",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 76,
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "306913b33422239c8f84f3670b861db6352d8c1c274699490aa252cd65f0efac",
        "kind": "HISTORICAL_METADATA"
      },
      "actual": {
        "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "f1c916490bbd06fa584826ce95e928404b1e48f3e5590aa575059fa59e3c4075",
        "matches": 3
      },
      "owner": "Module owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d842c07463da27ae8465564ea738f7a24e436792ae4e83b06b78107a93952c5b",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 77,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestRunControllerTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "554b7ee630e206a88e799254806af34debc2f339988a6cd62bff1383f57e78fe",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestRunControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "cf023e4e81eb30772fe5c6e7dae5a13c0e8b58d842708c28ee098bbac97bc0b1",
        "matches": 12
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d857de499a5fd4ffd60cce1da92e8e94b85eac02eaadc379f72e5cbfba15c3c1",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 78,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/PythonEvaluationArtifactBindingPreviewControllerTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "6f428b6bd5d77987e5d3cf3b073f0cc9a4152dd262da550bcc1f5bbafbc8253f",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/PythonEvaluationArtifactBindingPreviewControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "60693a8b4352e1a9a3d9debd0067789139df4d3773c8d957c2230fe2bd954959",
        "matches": 5
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "3426e5a94f626a5069b4537cc8fea2f65add96f7e18ee62db368793a7757914e",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 79,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PythonEvaluationArtifactPreviewOverviewControllerTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "6e36c835e3a56d2a34d655c6cd6abb8bf9c320756548e2d371e32c1be63a38a2",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PythonEvaluationArtifactPreviewOverviewControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "380fea3af559cdc935435edf63734c819574a844989114dd18d8e39d9af77983",
        "matches": 7
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "596cdd513f06e86b161a2a5169f652d3ae80111736c29283696b836576a3ca08",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 80,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/trading/api/web/TradingPreflightControllerTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "26006243006ab28f74c5255859c8ba23814eea3bd21a14f5c0bcfd1d17cdbaed",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/trading/api/web/TradingPreflightControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "2852340cdff10410a9555584ef8be53929405c414db4bb000c042914855a038a",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "a91477ffd97aeafbe275f896e471177739362d63ce32a038ea16ce76b320e3b7",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 81,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "943adee7c274a19c13d1655d1a00a2335aee6b57412162a816308569fecdc240",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "71c747480f40bbc33f6057da6e90779c1e3ef4a944239c51c16ad1f40b50bd3e",
        "matches": 1
      },
      "owner": "nq-app / MinimalLivePilotConfiguration contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 82,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionGuardedMaterializationPostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "d4c6bc3be9e34b04b9cb34b48939af55fcd1237adde49c48ebee66db0fb34821",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionGuardedMaterializationPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "e983d10f76fa3c7d98fb58ff779376c1760b6cf418e7aea2b41a1c1fad9cfe62",
        "matches": 24
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "85e588afbb068ae5807fa3afeb5f30555cc8c5164a6cca66da1751d498d8a216",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 83,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionMaterializationGuardPostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "44c5bfe9db1892894f664292292db3992f9034a06a962e9fe95925dd88465a5c",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionMaterializationGuardPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "226e39c79159be50df0621a074ecd49ccfa44691f11e218a3c15ea8613ed10eb",
        "matches": 34
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "30f2f160fcc4376c5e55603ae582982bf68240cbbcf18d5d8cc42009cb0dccc2",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 84,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "4624d504a39f4b0eb4e036d456a0ceb98142017be58d0e8680607463ae47ce31",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "94a73c2a3d2ebaf35cbc5f3e657fbc9f7e6483825f458cee659b9d3442a1988c",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "7896fbc1f2e00a6b918e029d574adfd22db4f1a977e6ca7bdb4244389bc08543",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 85,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "d517384c4a3721a35cdf696b08c82a86ddeaea6c6aef8287d20ef13bda8ea08a",
        "kind": "NEGATIVE_REGRESSION"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "80390a9504fa8f5dd034a38eff70c7a09bb1904634800002298ead185e0f0000",
        "matches": 9
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "23d8e77e14165fa5fde0281197e981e2d1e05323e0b1460f88f1498c33655b90",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 86,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "1d89eb5d4667485baad3d4ba1e41c26fcc17256b0023c21e6dfeeca960caca9e",
        "kind": "NEGATIVE_REGRESSION"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "8b21dc6c169a62a50bc746923b763663f6ba2d2dbb09d579ba4b0ac77620f825",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "459c6edd7602ee46eb54c9b3d3f662f3e4ce42359ae261e4c0ac231d9c467f56",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 87,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfigurationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "a95bd49a16bf7bf2b2acd9f9317415c6ff22d2c0e5c3db3e5bec869e9e8bf154",
        "kind": "NEGATIVE_REGRESSION"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfigurationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "74c6d98efd1684f0f1498ef99661d65754b85105c0ec1b6a03a9ad27c8aa703a",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "153d580715399d921276832a224ebf367ffc8b551677eacabc079291af989ca2",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 88,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "f9b9c1545082fb3c95ee2c8d1b627228e12557fee304f7c6c9f18aa87dfa312b",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "1efe573b77a5e93a10a845ce10074fda06ca3454928bd528d56ebda7305dbacb",
        "matches": 61
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "fab3d78fd3c3a30973def8d3ee3b951a8f2b307a5ea6bf4dcaadd21f3265b60a",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 89,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/OperatorPilotAuthorityPostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "6539d1e21b80ea2164f735f24901afada53d60f4a33a1608c55a38eec0ec6a11",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/OperatorPilotAuthorityPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "00412fef8506bed491e398b0513d45a9bfd7bedf3cf12ffae724e14c2648b9ad",
        "matches": 7
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "320cfe630d5a100080ec2da9d025841da9bba38b77840702c0e0076ee863f513",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 90,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "99c366b8bcf9a09281a7d7bdb7e7366328dfc52d42ee1e5aaf1bfced1669dc92",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "5d5b9188f8fd5aaa23ef16daf59148df17b82f344447d60509034c2663e9fbd2",
        "matches": 18
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "711cdf7a098bd1da9ed1c834089ec39dd66e27e2cefe256375314c60cdc404b0",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 91,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/KillSwitchRestartDurabilityPostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "3c50ac404d415df7321dff4f9cd056aa2d4da63d2132d5adb4f4cbf0dce59c21",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/KillSwitchRestartDurabilityPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "022149e5aeb9c1273f7ce9aa5d94dc957f98103576f743010676e252a36622ba",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "52ea42a4571fada943b9f4579a5127e90253090c6b59ff05aec17462e51577a0",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 92,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/shadowrun/ShadowRunProvenancePostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "497df5e525bbb6aed37e11311b1247441afff8570f0594c8df4cd728a02b274d",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/shadowrun/ShadowRunProvenancePostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "0c5d4291229a0b17199d335e86c2e879930a56813d2d46d24c36e8c59a5e4893",
        "matches": 33
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "a3a17f38066f79c21cda08c1876fbf1b698b5e3fcf854e86d42b71dda44e9bb1",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 93,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "b6b053eb48f15348b2d35dd4a1c0618f857d1d3847498b6a6315f91a5191677d",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "5b3a8c642898126bdbac908a6dc72755b0df57425c309f7390349c90d0de77a7",
        "matches": 1
      },
      "owner": "nq-core / ExactPilotBinding contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "7c5509f2d8403c517db9e8546f1e957dbc6be13262c8e6306f102dcb61c06525",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 94,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentFactBundle.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "317ad4ac3d8c61000f9632f22c3af366f1a3285670f28ccd3d71b3306d4e1525",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentFactBundle.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "2d7ec73cbf4e5f7c6ca22ecdc206bccbfc61164dbbb576cd25dbf1a19c9a9620",
        "matches": 2
      },
      "owner": "nq-core / OperationalSafetyAssessmentFactBundle contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "728b5c3662cf62690670510aca524ab0c8360af72d443512a8b881cf6a737cd2",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 95,
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "97168ce61d450be52974c98552041a2a20418d45069eaa7cdb987e1ba35df8a0",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "34004a277fb475656ca904f64e4fe643cb96ff76f9cecb1d3be7817d3037fbaf",
        "matches": 8
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "33f9f80d795861d98aa499ce0992ea3b99cb16b9cd86745f878f234317d851b0",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 96,
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationServiceTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "2c7cc3be7524bde1822e7dec4c6a0c94366b386e87013be284b92158e1b1c83e",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationServiceTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "b4b4529127a1e9c144691cf1290ca039b9665acd9955ae4eac6def4e2a542d7d",
        "matches": 2
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "18bf4b8143c1c2012d76d9e28545dd6b447d4d89250dfaaafdd524efef66c007",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 97,
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentServiceTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "7f3203e208bfb0aa25f148ba9a2b3c51bd2b8247680ad0fc66848807ea52e761",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentServiceTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "39af614f146e8158eda1342359a3b2a9f6876e7cf15c77c1985818840d75557b",
        "matches": 12
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "cf340eb4c6067dfd9fa546bb43ab7721b50a673f5b9f571eaf34bec5c2898653",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 98,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "30ef0c19c9afa4eddd96525cc01205efae1a1aa72d3b9fdb53deffb9481da475",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "289dcd53100ef33029da8205442e420338732209d7ab210ec29672ebfa63773c",
        "matches": 2
      },
      "owner": "nq-infra / MinimalPilotTradingVenueGateway contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "fca69b49ee10659217c1af934735e42665095383269310ddcccbd8bb99978ffd",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 99,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "24113605c90cc4a12dad400b04779862b911f9bc1c1badd5f28f3995624b11e4",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "8b7f637b6055ac432cca2d35a7eae98af8bb605af77a854f9263979379897511",
        "matches": 1
      },
      "owner": "nq-infra / JdbcExecutionIntentRepository contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "c0cc7e693c7a65c541ecb4fd4166073787d2fede268192401ae0a7c510b11695",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 100,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/MinimalLivePilotControlService.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "64a151381acc549ac0c46be167bcb15967566d6bea6984372f081d74aa4bd819",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/MinimalLivePilotControlService.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "5d8fa29431e86f46b211c15a3147569cb24a1970ff1dc02190d3edb412a4c21e",
        "matches": 3
      },
      "owner": "nq-infra / MinimalLivePilotControlService contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "32acddc5c468c73c6e1e28b5dfc8952f2a53e4f046d24f9e91414f86797e273d",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 101,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "01f8ddc34d685aba2816a6c7b9550a41795a7181ca9a2c9de6e34e572f832701",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "2ad7c23cd7cf01cde6f98d935c7031348a0112584bdb4be6e314003d07bf5e1c",
        "matches": 1
      },
      "owner": "nq-infra / OkxPilotPrerequisiteObservationAuthority contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "92c730f6a23205b397dc07f51acdef9f3285b089c60b20801ee604924800f64c",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 102,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePortTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "496a627bfc0558027c7eead219822fa1158b5457a35e62e94dc7bdbc779e3f3c",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePortTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "39d84029fe4d700ccb0d9ab568528612900f647eb774df45faf0fef106c0bcc8",
        "matches": 10
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "fb4a77b90688113f3b71c6c3e3739ec62f9df9c54f3b5a8d81f28a03c3410626",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 103,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/OperatorPilotAuthorityMigrationContractTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "d841e7960e5a757e57acc9c287c57ad77f6cc9cc2280fed1164a845d3f8540fe",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/OperatorPilotAuthorityMigrationContractTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "7ed9d9917abb13813f7679c0bc449bc02a96d0b3ddad8645377995f69195e1ed",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "8bda7e6245d9a59f87bdd05f24e425c900330a600a683837eee894b86de33f0d",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 104,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/PrePlaceRecoveryMigrationContractTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "a8d1173eab00aa5f5684ccae7eb5096823d9a847807145a03b30650c96abc3a7",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/PrePlaceRecoveryMigrationContractTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "d2208d7764058d20ccd12c78f8139a250fa7a440b9a7eb27b3f515aa70a8c745",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "46617e0ae0514647b1edc53ee13caef38d77e5cee99a18531ba0f95ebc8542fe",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 105,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ReadOnlyQualificationObservationAuthorityTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "e3171f79bce7cf8def464027dffef7bfb25fab11a6cf23fa939b65f8bb2b0d0d",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ReadOnlyQualificationObservationAuthorityTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "caeab1fe4ba9106777974df71ecc4714671633932b8e56ce005afd78ed877ebc",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d065e06fc99846567f7f566afcfd63237dbf162361bf4701d076d107cb89a56a",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 106,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "c92981953740fbcbe76fcbe6d87ba7ac538fc9070f279dd003939cfc5cf7342a",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "aa3e1fcb38b9aeec096fd4d65dab81cfa8bcb15c2e6338c600c1b16bc63e83e4",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "8f54f0b221dad0a467c049942aada42bf6721098c30c458943b588ef48e6dd7f",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 107,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolverTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "1b0fc00ec2bbfa1f5feed2e8adfb2cd8aafc246b659cebe71bf8db1baf9da84f",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolverTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "4cd2df401ee9f9f0491c232dbd57bda15654af850fa78b15ed341587e03e370b",
        "matches": 17
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "8a5ee2862352df773be2214a8df4bd7b50f6cc9f73324dc1ae14906247ee863d",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 108,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepositoryTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "4b977a96505997d25ab2c41de291f0c8df9f95524e1c1657f66b016a8faa56f8",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepositoryTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "d30d20644e976b388ca24b062f8e32f7948cfac09525e73961ff2412d51d98b6",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "c78f46684ec21f55f26fe4132c5564a4ca4e974680a0864893e530e4f5eaf061",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 109,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/ValidationReviewRepositoryPostgresIntegrationTest.java",
      "diagnostic": "STAGE_SEMANTICS",
      "registeredExpected": {
        "sha256": "86a20545741e3c5f4d7e4098e5a3322e34d5d0397cb07e866dfc0460b051a538",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/ValidationReviewRepositoryPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "735c7c5fa98567b7540107d1b71f4eee7f45afdd1b658619da8fefd7f8398e5a",
        "matches": 4
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "44ad6d522a763d044d4fe320db5148e43f4c1709989269146522079119f75b61",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 110,
      "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "a1c3858ab7103caa213146b6bb0846def2f622e31f5c2d51e976b80350ad0445",
        "kind": "HISTORICAL_METADATA"
      },
      "actual": {
        "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "d12877d85d69d69d2a3e6024e9d947fda8392b395e5a09b35b241c1c1841ce3b",
        "matches": 3
      },
      "owner": "Module owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d5d13cc10b7c298d95b8a247b1c98e5dfc5d775a008677a6f747df9c6c6f8704",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 111,
      "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "306913b33422239c8f84f3670b861db6352d8c1c274699490aa252cd65f0efac",
        "kind": "HISTORICAL_METADATA"
      },
      "actual": {
        "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "f1c916490bbd06fa584826ce95e928404b1e48f3e5590aa575059fa59e3c4075",
        "matches": 3
      },
      "owner": "Module owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d842c07463da27ae8465564ea738f7a24e436792ae4e83b06b78107a93952c5b",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 112,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestRunControllerTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "554b7ee630e206a88e799254806af34debc2f339988a6cd62bff1383f57e78fe",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestRunControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "cf023e4e81eb30772fe5c6e7dae5a13c0e8b58d842708c28ee098bbac97bc0b1",
        "matches": 12
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d857de499a5fd4ffd60cce1da92e8e94b85eac02eaadc379f72e5cbfba15c3c1",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 113,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/PythonEvaluationArtifactBindingPreviewControllerTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "6f428b6bd5d77987e5d3cf3b073f0cc9a4152dd262da550bcc1f5bbafbc8253f",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/PythonEvaluationArtifactBindingPreviewControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "60693a8b4352e1a9a3d9debd0067789139df4d3773c8d957c2230fe2bd954959",
        "matches": 5
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "3426e5a94f626a5069b4537cc8fea2f65add96f7e18ee62db368793a7757914e",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 114,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PythonEvaluationArtifactPreviewOverviewControllerTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "6e36c835e3a56d2a34d655c6cd6abb8bf9c320756548e2d371e32c1be63a38a2",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PythonEvaluationArtifactPreviewOverviewControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "380fea3af559cdc935435edf63734c819574a844989114dd18d8e39d9af77983",
        "matches": 7
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "596cdd513f06e86b161a2a5169f652d3ae80111736c29283696b836576a3ca08",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 115,
      "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/trading/api/web/TradingPreflightControllerTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "26006243006ab28f74c5255859c8ba23814eea3bd21a14f5c0bcfd1d17cdbaed",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/trading/api/web/TradingPreflightControllerTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "2852340cdff10410a9555584ef8be53929405c414db4bb000c042914855a038a",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "a91477ffd97aeafbe275f896e471177739362d63ce32a038ea16ce76b320e3b7",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 116,
      "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "943adee7c274a19c13d1655d1a00a2335aee6b57412162a816308569fecdc240",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "71c747480f40bbc33f6057da6e90779c1e3ef4a944239c51c16ad1f40b50bd3e",
        "matches": 1
      },
      "owner": "nq-app / MinimalLivePilotConfiguration contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "b17cb58bebc43fe0d0c5d6759881d03c3be3b4802fb6e1a98085635b28ba5135",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 117,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionGuardedMaterializationPostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "d4c6bc3be9e34b04b9cb34b48939af55fcd1237adde49c48ebee66db0fb34821",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionGuardedMaterializationPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "e983d10f76fa3c7d98fb58ff779376c1760b6cf418e7aea2b41a1c1fad9cfe62",
        "matches": 24
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "85e588afbb068ae5807fa3afeb5f30555cc8c5164a6cca66da1751d498d8a216",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 118,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionMaterializationGuardPostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "44c5bfe9db1892894f664292292db3992f9034a06a962e9fe95925dd88465a5c",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionMaterializationGuardPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "226e39c79159be50df0621a074ecd49ccfa44691f11e218a3c15ea8613ed10eb",
        "matches": 34
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "30f2f160fcc4376c5e55603ae582982bf68240cbbcf18d5d8cc42009cb0dccc2",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 119,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "4624d504a39f4b0eb4e036d456a0ceb98142017be58d0e8680607463ae47ce31",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "94a73c2a3d2ebaf35cbc5f3e657fbc9f7e6483825f458cee659b9d3442a1988c",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "7896fbc1f2e00a6b918e029d574adfd22db4f1a977e6ca7bdb4244389bc08543",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 120,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "d517384c4a3721a35cdf696b08c82a86ddeaea6c6aef8287d20ef13bda8ea08a",
        "kind": "NEGATIVE_REGRESSION"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "80390a9504fa8f5dd034a38eff70c7a09bb1904634800002298ead185e0f0000",
        "matches": 9
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "23d8e77e14165fa5fde0281197e981e2d1e05323e0b1460f88f1498c33655b90",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 121,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "1d89eb5d4667485baad3d4ba1e41c26fcc17256b0023c21e6dfeeca960caca9e",
        "kind": "NEGATIVE_REGRESSION"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "8b21dc6c169a62a50bc746923b763663f6ba2d2dbb09d579ba4b0ac77620f825",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "459c6edd7602ee46eb54c9b3d3f662f3e4ce42359ae261e4c0ac231d9c467f56",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 122,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfigurationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "a95bd49a16bf7bf2b2acd9f9317415c6ff22d2c0e5c3db3e5bec869e9e8bf154",
        "kind": "NEGATIVE_REGRESSION"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfigurationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "74c6d98efd1684f0f1498ef99661d65754b85105c0ec1b6a03a9ad27c8aa703a",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "153d580715399d921276832a224ebf367ffc8b551677eacabc079291af989ca2",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 123,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "f9b9c1545082fb3c95ee2c8d1b627228e12557fee304f7c6c9f18aa87dfa312b",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "1efe573b77a5e93a10a845ce10074fda06ca3454928bd528d56ebda7305dbacb",
        "matches": 61
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "fab3d78fd3c3a30973def8d3ee3b951a8f2b307a5ea6bf4dcaadd21f3265b60a",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 124,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/OperatorPilotAuthorityPostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "6539d1e21b80ea2164f735f24901afada53d60f4a33a1608c55a38eec0ec6a11",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/OperatorPilotAuthorityPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "00412fef8506bed491e398b0513d45a9bfd7bedf3cf12ffae724e14c2648b9ad",
        "matches": 7
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "320cfe630d5a100080ec2da9d025841da9bba38b77840702c0e0076ee863f513",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 125,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "99c366b8bcf9a09281a7d7bdb7e7366328dfc52d42ee1e5aaf1bfced1669dc92",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "5d5b9188f8fd5aaa23ef16daf59148df17b82f344447d60509034c2663e9fbd2",
        "matches": 18
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "711cdf7a098bd1da9ed1c834089ec39dd66e27e2cefe256375314c60cdc404b0",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 126,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/KillSwitchRestartDurabilityPostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "3c50ac404d415df7321dff4f9cd056aa2d4da63d2132d5adb4f4cbf0dce59c21",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/KillSwitchRestartDurabilityPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "022149e5aeb9c1273f7ce9aa5d94dc957f98103576f743010676e252a36622ba",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "52ea42a4571fada943b9f4579a5127e90253090c6b59ff05aec17462e51577a0",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 127,
      "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/shadowrun/ShadowRunProvenancePostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "497df5e525bbb6aed37e11311b1247441afff8570f0594c8df4cd728a02b274d",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/shadowrun/ShadowRunProvenancePostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "0c5d4291229a0b17199d335e86c2e879930a56813d2d46d24c36e8c59a5e4893",
        "matches": 33
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "a3a17f38066f79c21cda08c1876fbf1b698b5e3fcf854e86d42b71dda44e9bb1",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 128,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "b6b053eb48f15348b2d35dd4a1c0618f857d1d3847498b6a6315f91a5191677d",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "5b3a8c642898126bdbac908a6dc72755b0df57425c309f7390349c90d0de77a7",
        "matches": 1
      },
      "owner": "nq-core / ExactPilotBinding contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "7c5509f2d8403c517db9e8546f1e957dbc6be13262c8e6306f102dcb61c06525",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 129,
      "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentFactBundle.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "317ad4ac3d8c61000f9632f22c3af366f1a3285670f28ccd3d71b3306d4e1525",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentFactBundle.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "2d7ec73cbf4e5f7c6ca22ecdc206bccbfc61164dbbb576cd25dbf1a19c9a9620",
        "matches": 2
      },
      "owner": "nq-core / OperationalSafetyAssessmentFactBundle contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "728b5c3662cf62690670510aca524ab0c8360af72d443512a8b881cf6a737cd2",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 130,
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "97168ce61d450be52974c98552041a2a20418d45069eaa7cdb987e1ba35df8a0",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "34004a277fb475656ca904f64e4fe643cb96ff76f9cecb1d3be7817d3037fbaf",
        "matches": 8
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "33f9f80d795861d98aa499ce0992ea3b99cb16b9cd86745f878f234317d851b0",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 131,
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationServiceTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "2c7cc3be7524bde1822e7dec4c6a0c94366b386e87013be284b92158e1b1c83e",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationServiceTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "b4b4529127a1e9c144691cf1290ca039b9665acd9955ae4eac6def4e2a542d7d",
        "matches": 2
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "18bf4b8143c1c2012d76d9e28545dd6b447d4d89250dfaaafdd524efef66c007",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 132,
      "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentServiceTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "7f3203e208bfb0aa25f148ba9a2b3c51bd2b8247680ad0fc66848807ea52e761",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentServiceTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "39af614f146e8158eda1342359a3b2a9f6876e7cf15c77c1985818840d75557b",
        "matches": 12
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "cf340eb4c6067dfd9fa546bb43ab7721b50a673f5b9f571eaf34bec5c2898653",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 133,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "30ef0c19c9afa4eddd96525cc01205efae1a1aa72d3b9fdb53deffb9481da475",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "289dcd53100ef33029da8205442e420338732209d7ab210ec29672ebfa63773c",
        "matches": 2
      },
      "owner": "nq-infra / MinimalPilotTradingVenueGateway contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "fca69b49ee10659217c1af934735e42665095383269310ddcccbd8bb99978ffd",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 134,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "24113605c90cc4a12dad400b04779862b911f9bc1c1badd5f28f3995624b11e4",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "8b7f637b6055ac432cca2d35a7eae98af8bb605af77a854f9263979379897511",
        "matches": 1
      },
      "owner": "nq-infra / JdbcExecutionIntentRepository contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "c0cc7e693c7a65c541ecb4fd4166073787d2fede268192401ae0a7c510b11695",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 135,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/MinimalLivePilotControlService.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "64a151381acc549ac0c46be167bcb15967566d6bea6984372f081d74aa4bd819",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/MinimalLivePilotControlService.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "5d8fa29431e86f46b211c15a3147569cb24a1970ff1dc02190d3edb412a4c21e",
        "matches": 3
      },
      "owner": "nq-infra / MinimalLivePilotControlService contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "32acddc5c468c73c6e1e28b5dfc8952f2a53e4f046d24f9e91414f86797e273d",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 136,
      "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "01f8ddc34d685aba2816a6c7b9550a41795a7181ca9a2c9de6e34e572f832701",
        "kind": "WIRE_COMPATIBILITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "2ad7c23cd7cf01cde6f98d935c7031348a0112584bdb4be6e314003d07bf5e1c",
        "matches": 1
      },
      "owner": "nq-infra / OkxPilotPrerequisiteObservationAuthority contract owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "92c730f6a23205b397dc07f51acdef9f3285b089c60b20801ee604924800f64c",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 137,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePortTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "496a627bfc0558027c7eead219822fa1158b5457a35e62e94dc7bdbc779e3f3c",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePortTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "39d84029fe4d700ccb0d9ab568528612900f647eb774df45faf0fef106c0bcc8",
        "matches": 10
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "fb4a77b90688113f3b71c6c3e3739ec62f9df9c54f3b5a8d81f28a03c3410626",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 138,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/OperatorPilotAuthorityMigrationContractTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "d841e7960e5a757e57acc9c287c57ad77f6cc9cc2280fed1164a845d3f8540fe",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/OperatorPilotAuthorityMigrationContractTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "7ed9d9917abb13813f7679c0bc449bc02a96d0b3ddad8645377995f69195e1ed",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "8bda7e6245d9a59f87bdd05f24e425c900330a600a683837eee894b86de33f0d",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 139,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/PrePlaceRecoveryMigrationContractTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "a8d1173eab00aa5f5684ccae7eb5096823d9a847807145a03b30650c96abc3a7",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/PrePlaceRecoveryMigrationContractTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "d2208d7764058d20ccd12c78f8139a250fa7a440b9a7eb27b3f515aa70a8c745",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "46617e0ae0514647b1edc53ee13caef38d77e5cee99a18531ba0f95ebc8542fe",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 140,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ReadOnlyQualificationObservationAuthorityTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "e3171f79bce7cf8def464027dffef7bfb25fab11a6cf23fa939b65f8bb2b0d0d",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ReadOnlyQualificationObservationAuthorityTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "caeab1fe4ba9106777974df71ecc4714671633932b8e56ce005afd78ed877ebc",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "d065e06fc99846567f7f566afcfd63237dbf162361bf4701d076d107cb89a56a",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 141,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "c92981953740fbcbe76fcbe6d87ba7ac538fc9070f279dd003939cfc5cf7342a",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "aa3e1fcb38b9aeec096fd4d65dab81cfa8bcb15c2e6338c600c1b16bc63e83e4",
        "matches": 1
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "8f54f0b221dad0a467c049942aada42bf6721098c30c458943b588ef48e6dd7f",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 142,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolverTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "1b0fc00ec2bbfa1f5feed2e8adfb2cd8aafc246b659cebe71bf8db1baf9da84f",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolverTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "4cd2df401ee9f9f0491c232dbd57bda15654af850fa78b15ed341587e03e370b",
        "matches": 17
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "8a5ee2862352df773be2214a8df4bd7b50f6cc9f73324dc1ae14906247ee863d",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 143,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepositoryTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "4b977a96505997d25ab2c41de291f0c8df9f95524e1c1657f66b016a8faa56f8",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepositoryTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "d30d20644e976b388ca24b062f8e32f7948cfac09525e73961ff2412d51d98b6",
        "matches": 3
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "c78f46684ec21f55f26fe4132c5564a4ca4e974680a0864893e530e4f5eaf061",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    },
    {
      "id": 144,
      "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/ValidationReviewRepositoryPostgresIntegrationTest.java",
      "diagnostic": "STALE_EXCEPTION",
      "registeredExpected": {
        "sha256": "86a20545741e3c5f4d7e4098e5a3322e34d5d0397cb07e866dfc0460b051a538",
        "kind": "FIXTURE_IDENTITY"
      },
      "actual": {
        "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/ValidationReviewRepositoryPostgresIntegrationTest.java",
        "kind": "STAGE_SEMANTICS",
        "sha256": "735c7c5fa98567b7540107d1b71f4eee7f45afdd1b658619da8fefd7f8398e5a",
        "matches": 4
      },
      "owner": "Capability test owner",
      "firstKnownSource": "HEAD to reviewed candidate explicit-import evolution; 37/37 import-expanded lexical equivalents; current bytes match reviewed full manifest",
      "reviewedSource": "docs/audit/evidence/l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json",
      "reviewedFileSha256": "44ad6d522a763d044d4fe320db5148e43f4c1709989269146522079119f75b61",
      "primaryCategory": "A",
      "secondaryMechanism": "F; stale counterpart is diagnostic duplication, not duplicate registry"
    }
  ]
}
```

## 最终实施与独立审查

独立reviewer `/root/stage_lifecycle_review` 未参与实现/test/evidence编写；只读核对37个原文件与实际已审manifest、重新生成完整提案相等，核验35摘要更新+38新增/36移除精确caller边，173条exception路径、元数据及其余policy规则无扩权。接受并实际apply的proposal-02 SHA256=`d1f1606c63cd61b42e47165e18e453a133dd2c5a249b14c671f9be2d9f56aa0c`；apply exit=0。

最终作者测试58 / OK / skips2 / 18.353s；最终独立测试58 / OK / skips2 / 20.703s。两个skip是Windows符号链接创建权限不足，不宣称已覆盖。新增9项永久回归覆盖合法演进及未更新registry漂移、新语义/新caller、失效/重复注册、提案篡改、动态集合及中途新增/外部输入变化；已有Java AST与JS grammar负例保持有效。

历史失败保留：旧proposal-01摘要=`275cb91031225ccfd6b687b6a07c95a1d865650041fc8b33659ec59d5305f062`未应用；新增caller测试误用另一个测试类helper，独立58 / errors1 / skips2，AttributeError。修复后重新冻结提案，不复用旧候选结果。此前P2中途新增文件问题已独立复现并关闭：修复前apply成功但guard仍拒绝；修复后CANDIDATE_CHANGED_BEFORE_APPLY、policy不写入。

完整validator实测 `STAGE_ASSET_CHECK scanned=1880 reviewed_exceptions=173 errors=0`，exit0；unexpected/missing诊断0。扫描数只因新增generator从1879增至1880。生成的35摘要如下；调用边old/new已逐条存于原144inventory。手工hash更新0，新exception0，成员/owner/退出条件不变。

synthetic exporter 8 tests / OK。pinned Gitleaks 8.18.4 archive SHA256=`ba6dbb656933921c775ee5a2d1c13a91046e7952e9d919f9bac4cec61d628e7d`实际匹配当前lock。六种secret negatives各exit2/findings1；包括三种旧例外边界、apiKey、token、Authorization，字段保持原值。扫描使用当前CI config和safe-file规则，额外纳入当前候选未跟踪文件，不改变scanner和allowlist。首次完整扫描因临时wrapper猜错Git安装路径未完成；改为Get-Command得到D:/Tool/Git后exit0/findings0。最终扫描封口另记。

links在两份本轮Markdown范围checked16/warnings0/errors0。首次CLI把Roots数组传成单个字符串触发ROOT_NOT_FOUND，改为真实PowerShell array后通过；不改变校验规则。git diff --check、py_compile通过。一次临时证据更新因Windows默认GBK读取UTF8失败，未写文件；改为显式UTF8。Full Maven/B5 qualification/V51 correctness/B1–B4矩阵均未重跑。

起点全部backend文件与当前集合逐文件比较，新增/删除/修改0；production delta0、migration delta0、B5测试语义delta0，V1–V51不变。index字节一致，stagedPaths=[]，HEAD未变。临时制品最终保存在backend/nq-app/target的已忽略目录，不作为另一份canonical registry或evidence。原始全部既存文件除下列授权文件外无变化。

### 本轮精确文件清单

下列5文件及本页，共6文件；本页自身不自引用摘要。

```json
[
  {
    "path": ".agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md",
    "sha256": "19afabe58080949b6db82830a5bfb1b3926db3717d100e4bb3a23fa578b0f9a1"
  },
  {
    "path": "scripts/docs/check-stage-assets.py",
    "sha256": "6909e86621198b3ae9e13db46f177662e0b95756b91531a92862cea9d42fbb64"
  },
  {
    "path": "scripts/docs/stage-asset-exceptions.json",
    "sha256": "bb71d107e8ecea333f67e6ca9b06dec3c9bb26427b2882dce5e142b3b227be09"
  },
  {
    "path": "scripts/docs/stage-asset-lifecycle.py",
    "sha256": "66a00f075223a0f32ba5cf1b6e620576f4c4a6160eb992fd615cd9af58bad3d2"
  },
  {
    "path": "scripts/docs/tests/test_stage_assets.py",
    "sha256": "9334ba95b87303d716be233eaef6f52a7e57fd2a341fee79f0252910372d588c"
  }
]
```

### 确定性生成的内容身份

```json
[
  {
    "path": "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceHistoricalKlineAdapter.java",
    "beforeSha256": "a1c3858ab7103caa213146b6bb0846def2f622e31f5c2d51e976b80350ad0445",
    "afterSha256": "d12877d85d69d69d2a3e6024e9d947fda8392b395e5a09b35b241c1c1841ce3b"
  },
  {
    "path": "backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java",
    "beforeSha256": "306913b33422239c8f84f3670b861db6352d8c1c274699490aa252cd65f0efac",
    "afterSha256": "f1c916490bbd06fa584826ce95e928404b1e48f3e5590aa575059fa59e3c4075"
  },
  {
    "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/BacktestRunControllerTest.java",
    "beforeSha256": "554b7ee630e206a88e799254806af34debc2f339988a6cd62bff1383f57e78fe",
    "afterSha256": "cf023e4e81eb30772fe5c6e7dae5a13c0e8b58d842708c28ee098bbac97bc0b1"
  },
  {
    "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/PythonEvaluationArtifactBindingPreviewControllerTest.java",
    "beforeSha256": "6f428b6bd5d77987e5d3cf3b073f0cc9a4152dd262da550bcc1f5bbafbc8253f",
    "afterSha256": "60693a8b4352e1a9a3d9debd0067789139df4d3773c8d957c2230fe2bd954959"
  },
  {
    "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/PythonEvaluationArtifactPreviewOverviewControllerTest.java",
    "beforeSha256": "6e36c835e3a56d2a34d655c6cd6abb8bf9c320756548e2d371e32c1be63a38a2",
    "afterSha256": "380fea3af559cdc935435edf63734c819574a844989114dd18d8e39d9af77983"
  },
  {
    "path": "backend/nq-api/src/test/java/com/guidinglight/nexusquant/trading/api/web/TradingPreflightControllerTest.java",
    "beforeSha256": "26006243006ab28f74c5255859c8ba23814eea3bd21a14f5c0bcfd1d17cdbaed",
    "afterSha256": "2852340cdff10410a9555584ef8be53929405c414db4bb000c042914855a038a"
  },
  {
    "path": "backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfiguration.java",
    "beforeSha256": "943adee7c274a19c13d1655d1a00a2335aee6b57412162a816308569fecdc240",
    "afterSha256": "71c747480f40bbc33f6057da6e90779c1e3ef4a944239c51c16ad1f40b50bd3e"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionGuardedMaterializationPostgresIntegrationTest.java",
    "beforeSha256": "d4c6bc3be9e34b04b9cb34b48939af55fcd1237adde49c48ebee66db0fb34821",
    "afterSha256": "e983d10f76fa3c7d98fb58ff779376c1760b6cf418e7aea2b41a1c1fad9cfe62"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/admission/AdmissionMaterializationGuardPostgresIntegrationTest.java",
    "beforeSha256": "44c5bfe9db1892894f664292292db3992f9034a06a962e9fe95925dd88465a5c",
    "afterSha256": "226e39c79159be50df0621a074ecd49ccfa44691f11e218a3c15ea8613ed10eb"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java",
    "beforeSha256": "4624d504a39f4b0eb4e036d456a0ceb98142017be58d0e8680607463ae47ce31",
    "afterSha256": "94a73c2a3d2ebaf35cbc5f3e657fbc9f7e6483825f458cee659b9d3442a1988c"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java",
    "beforeSha256": "d517384c4a3721a35cdf696b08c82a86ddeaea6c6aef8287d20ef13bda8ea08a",
    "afterSha256": "80390a9504fa8f5dd034a38eff70c7a09bb1904634800002298ead185e0f0000"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java",
    "beforeSha256": "1d89eb5d4667485baad3d4ba1e41c26fcc17256b0023c21e6dfeeca960caca9e",
    "afterSha256": "8b21dc6c169a62a50bc746923b763663f6ba2d2dbb09d579ba4b0ac77620f825"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/MinimalLivePilotConfigurationTest.java",
    "beforeSha256": "a95bd49a16bf7bf2b2acd9f9317415c6ff22d2c0e5c3db3e5bec869e9e8bf154",
    "afterSha256": "74c6d98efd1684f0f1498ef99661d65754b85105c0ec1b6a03a9ad27c8aa703a"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java",
    "beforeSha256": "f9b9c1545082fb3c95ee2c8d1b627228e12557fee304f7c6c9f18aa87dfa312b",
    "afterSha256": "1efe573b77a5e93a10a845ce10074fda06ca3454928bd528d56ebda7305dbacb"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/OperatorPilotAuthorityPostgresIntegrationTest.java",
    "beforeSha256": "6539d1e21b80ea2164f735f24901afada53d60f4a33a1608c55a38eec0ec6a11",
    "afterSha256": "00412fef8506bed491e398b0513d45a9bfd7bedf3cf12ffae724e14c2648b9ad"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java",
    "beforeSha256": "99c366b8bcf9a09281a7d7bdb7e7366328dfc52d42ee1e5aaf1bfced1669dc92",
    "afterSha256": "5d5b9188f8fd5aaa23ef16daf59148df17b82f344447d60509034c2663e9fbd2"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/KillSwitchRestartDurabilityPostgresIntegrationTest.java",
    "beforeSha256": "3c50ac404d415df7321dff4f9cd056aa2d4da63d2132d5adb4f4cbf0dce59c21",
    "afterSha256": "022149e5aeb9c1273f7ce9aa5d94dc957f98103576f743010676e252a36622ba"
  },
  {
    "path": "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/shadowrun/ShadowRunProvenancePostgresIntegrationTest.java",
    "beforeSha256": "497df5e525bbb6aed37e11311b1247441afff8570f0594c8df4cd728a02b274d",
    "afterSha256": "0c5d4291229a0b17199d335e86c2e879930a56813d2d46d24c36e8c59a5e4893"
  },
  {
    "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java",
    "beforeSha256": "b6b053eb48f15348b2d35dd4a1c0618f857d1d3847498b6a6315f91a5191677d",
    "afterSha256": "5b3a8c642898126bdbac908a6dc72755b0df57425c309f7390349c90d0de77a7"
  },
  {
    "path": "backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentFactBundle.java",
    "beforeSha256": "317ad4ac3d8c61000f9632f22c3af366f1a3285670f28ccd3d71b3306d4e1525",
    "afterSha256": "2d7ec73cbf4e5f7c6ca22ecdc206bccbfc61164dbbb576cd25dbf1a19c9a9620"
  },
  {
    "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java",
    "beforeSha256": "97168ce61d450be52974c98552041a2a20418d45069eaa7cdb987e1ba35df8a0",
    "afterSha256": "34004a277fb475656ca904f64e4fe643cb96ff76f9cecb1d3be7817d3037fbaf"
  },
  {
    "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationServiceTest.java",
    "beforeSha256": "2c7cc3be7524bde1822e7dec4c6a0c94366b386e87013be284b92158e1b1c83e",
    "afterSha256": "b4b4529127a1e9c144691cf1290ca039b9665acd9955ae4eac6def4e2a542d7d"
  },
  {
    "path": "backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/safety/OperationalSafetyAssessmentServiceTest.java",
    "beforeSha256": "7f3203e208bfb0aa25f148ba9a2b3c51bd2b8247680ad0fc66848807ea52e761",
    "afterSha256": "39af614f146e8158eda1342359a3b2a9f6876e7cf15c77c1985818840d75557b"
  },
  {
    "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/MinimalPilotTradingVenueGateway.java",
    "beforeSha256": "30ef0c19c9afa4eddd96525cc01205efae1a1aa72d3b9fdb53deffb9481da475",
    "afterSha256": "289dcd53100ef33029da8205442e420338732209d7ab210ec29672ebfa63773c"
  },
  {
    "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java",
    "beforeSha256": "24113605c90cc4a12dad400b04779862b911f9bc1c1badd5f28f3995624b11e4",
    "afterSha256": "8b7f637b6055ac432cca2d35a7eae98af8bb605af77a854f9263979379897511"
  },
  {
    "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/MinimalLivePilotControlService.java",
    "beforeSha256": "64a151381acc549ac0c46be167bcb15967566d6bea6984372f081d74aa4bd819",
    "afterSha256": "5d8fa29431e86f46b211c15a3147569cb24a1970ff1dc02190d3edb412a4c21e"
  },
  {
    "path": "backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java",
    "beforeSha256": "01f8ddc34d685aba2816a6c7b9550a41795a7181ca9a2c9de6e34e572f832701",
    "afterSha256": "2ad7c23cd7cf01cde6f98d935c7031348a0112584bdb4be6e314003d07bf5e1c"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePortTest.java",
    "beforeSha256": "496a627bfc0558027c7eead219822fa1158b5457a35e62e94dc7bdbc779e3f3c",
    "afterSha256": "39d84029fe4d700ccb0d9ab568528612900f647eb774df45faf0fef106c0bcc8"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/OperatorPilotAuthorityMigrationContractTest.java",
    "beforeSha256": "d841e7960e5a757e57acc9c287c57ad77f6cc9cc2280fed1164a845d3f8540fe",
    "afterSha256": "7ed9d9917abb13813f7679c0bc449bc02a96d0b3ddad8645377995f69195e1ed"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/PrePlaceRecoveryMigrationContractTest.java",
    "beforeSha256": "a8d1173eab00aa5f5684ccae7eb5096823d9a847807145a03b30650c96abc3a7",
    "afterSha256": "d2208d7764058d20ccd12c78f8139a250fa7a440b9a7eb27b3f515aa70a8c745"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ReadOnlyQualificationObservationAuthorityTest.java",
    "beforeSha256": "e3171f79bce7cf8def464027dffef7bfb25fab11a6cf23fa939b65f8bb2b0d0d",
    "afterSha256": "caeab1fe4ba9106777974df71ecc4714671633932b8e56ce005afd78ed877ebc"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java",
    "beforeSha256": "c92981953740fbcbe76fcbe6d87ba7ac538fc9070f279dd003939cfc5cf7342a",
    "afterSha256": "aa3e1fcb38b9aeec096fd4d65dab81cfa8bcb15c2e6338c600c1b16bc63e83e4"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolverTest.java",
    "beforeSha256": "1b0fc00ec2bbfa1f5feed2e8adfb2cd8aafc246b659cebe71bf8db1baf9da84f",
    "afterSha256": "4cd2df401ee9f9f0491c232dbd57bda15654af850fa78b15ed341587e03e370b"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepositoryTest.java",
    "beforeSha256": "4b977a96505997d25ab2c41de291f0c8df9f95524e1c1657f66b016a8faa56f8",
    "afterSha256": "d30d20644e976b388ca24b062f8e32f7948cfac09525e73961ff2412d51d98b6"
  },
  {
    "path": "backend/nq-infra/src/test/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/ValidationReviewRepositoryPostgresIntegrationTest.java",
    "beforeSha256": "86a20545741e3c5f4d7e4098e5a3322e34d5d0397cb07e866dfc0460b051a538",
    "afterSha256": "735c7c5fa98567b7540107d1b71f4eee7f45afdd1b658619da8fefd7f8398e5a"
  }
]
```

### 结论

ROOT_CAUSE_IDENTIFIED / ROOT_CAUSE_FIXED / MANUAL_PER_FILE_WORKAROUND=0 / NEW_BATCH_EXCEPTION=0 / CURRENT_B5_TECHNICAL_CANDIDATE_UNCHANGED / PROJECT_LESSON_CAPTURED。

本轮P0=0/P1=0/P2=0/P3=0；原项目P2/P3未重审，不用此计数清零既有残余。B5=CORRECTNESS_QUALIFIED / DELIVERY_READY，尚不是ACCEPTED；下一任务为NQ-GATEAUDIT-PHASE6-L4-B5-PRECISE-DELIVERY。stage=0 / commit=NONE / push=NONE。

摘要参数只证明提案内容身份，不认证reviewer。独立审查记录与静止工作区是调用方合同；末次检查与os.replace不是全目录事务。validator仍独立拒绝后续漂移，不能将proposal生成或tests green当审查授权。

### 验证封口

最终全量候选扫描包含 3860 个符合当前CI safe-file规则的tracked/untracked文件，pinned Gitleaks exit0/findings0；六种秘密负例均exit2/findings1，secretFieldsUnchanged=true。当前CI配置SHA256=`34c0a7e8f13035f6a464198058da21fb675a7a5ccf44c22f72421921b0ad1d36`。原始机器结果保留于已忽略的 `backend/nq-app/target/b5-stage-assets-root-cause/secrets-final/secret-validation.json`。

最终比对：本轮6文件；1814个backend文件新增/删除/修改0，1334个src/main文件不变，51个migration不变；HEAD/index不变、stagedPaths为空。links16/0/0、diff check通过。最终封口文字追加后单独复扫该证据，不重新扫描未变候选。

PASS / B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATED / STAGE_ASSETS_ERRORS_144_TO_0 / CANONICAL_ASSET_LIFECYCLE_RESTORED / NO_BATCH_SPECIFIC_EXCEPTIONS / UNAUTHORIZED_DRIFT_DETECTION_PRESERVED / B5_TECHNICAL_CANDIDATE_UNCHANGED / READY_FOR_PRECISE_DELIVERY
