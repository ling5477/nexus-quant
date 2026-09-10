# B5 StrategyRun V51 durable execution implementation — Attempt01

任务：`NQ-GATEAUDIT-PHASE6-L4-B5-STRATEGY-RUN-DURABLE-EXECUTION-V51-IMPLEMENTATION`。日期：2026-09-10。

状态：`IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW`；`B5=NOT_QUALIFIED`。以下为实施者在隔离环境的实际证明，不是独立正确性验收、发布或运行授权变更。

## Authority 与候选边界

唯一实施 authority 为[已接受合同](GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_EXECUTION_CONTRACT_REVIEW.md)。未修改该合同，未重新设计 V49，未弱化 V50，未增加 lease/heartbeat/leader 或工作流框架。

- worktree：`E:/Project/nexus-quant-gateaudit`；branch：`audit/post-gatey-agent-baseline`；HEAD：`86c8ad84542636364f6c21e78bc292a323cbdff7`。
- 起点：3571 个非忽略 tracked/untracked 文件，302 个已有 status 项；aggregate SHA256 `c6ffe2627edaaf6af62ee25ca7d38ecd3ca96237eaec6739c732f60d07383560`。算法为排序后的 `path + NUL + fileSHA256 + LF`。
- 起点 index SHA256：`67d0e2a40f423491211b9b48b17ac7a43ceebb67c38f62929eacc759d31db106`。本轮不 stage、不 commit、不 push。
- 起点清单和原始输出保留在 `backend/nq-app/target/b5-v51-implementation-attempt01/`；按该起点计算本轮 delta，不把全部 HEAD diff 记为本轮修改。
- [历史生命周期失败](GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_LIFECYCLE_CRASH_RECOVERY_REMEDIATION_ATTEMPT01.md)及其原始 2 failures 保留。本文中的 PASS 均为本候选实施验证，不是独立 review 或 B5 qualification。

## Schema、work 和身份

[V51](../../../backend/nq-infra/src/main/resources/db/migration/V51__strategy_run_durable_execution.sql) 新增三个必要表：

| 表 | durable owner / 关键保证 |
| --- | --- |
| `strategy_run_dispatch_work` | run PK/FK，version=1、definition version、account/client、symbol/side/type、quantity/price、显式 TIF；typed CHECK、不可变、account/client UNIQUE；不从当前配置重算 |
| `ordinary_order_cancel_finality` | Order PK/FK、查询前 version、最终累计 executed quantity；write-once；与原 run 终态原子提交 |
| `strategy_run_recovery_scan_cursor` | singleton `(last_started_at,last_run_id)`；只表示检查位置，每批≤50，有序后区间+wrap，不表示执行权 |

原 run 拥有不可变 scope、request/trace、config snapshot、startedAt、V50 admission 身份。v1 client=`coid-`+原 requestId，idempotency=`account:client`，source=`strategy_manual`，executionScopeId=null；NUMERIC(38,8) 在 admission 前无损验证，拒绝舍入和未知版本。手动有效参数可与定义配置不同，仍只以首次 work 为经济指令 owner。

`orders(strategy_run_id) WHERE strategy_run_id IS NOT NULL` 全局 UNIQUE，保留既有 FK/client 唯一键。新 Order 必须在创建时与原 run/work 完整匹配；拒绝改绑、解绑、事后认领、经济/作用域修改、绑定身份删除和 TRUNCATE。普通无 run Order 不能抢占已接纳 work 的 client；同账户/client 的 admission 和普通 Order 插入使用事务 advisory lock 串行化。该锁没有持久 owner/lease 语义。

通用 `OrderCommandService.placeOrder` 拒绝 strategyRunId override；策略从 typed work 进入 `StrategyOrderExecutionService` / `StrategyOrderPreparationService`。原 non-OKX 首次调用行为保留；自动 resume 仅覆盖 ordinary OKX，已有 non-OKX binding 不授予重发权。

策略端口编排与 immutable intent 校验集中在原 `OrderCommandStrategyExecutionGateway`；交易 prepare 只依赖交易侧最小 `StrategyOrderBindingRepository`。它与策略生命周期端口由同一个 JDBC bean 实现，共用原 run/work 和事务锁，没有新增持久协议。首次 Full 暴露的新服务越界依赖已按此归属修正，未放宽 architecture test。

## 原子组与 commit unknown

| 组 | 同事务事实 / 顺序 | death 或未知提交后的 owner |
| --- | --- | --- |
| A | definition→schedule；锁内重查 config/active/effective cursor；原 run CREATED + typed work + V50 admission + dueAt 水位 | 下一 JVM 读取原窗口或 work 身份；已提交沿原 run，未提交在同锁/唯一键下重做同逻辑 admission |
| B | run→Order；CREATED→DISPATCHING + 唯一 Order + 初始 V49 + 原 RiskGate/事件 | 原 run 锁与 UNIQUE 决定 winner；已绑定只复用该 Order；回滚不遗留 DISPATCHING/半个 pair |
| 原 V49 arm | 原 Kill→Order→authority，独立事务；HTTP 在 B/arm 提交后 | 仅明确确认的 caller 可发送；MAY 或 arm unknown 不可凭读回获得发送权 |
| C | run→Order；等锁后读 durable Trade/Order/authority；必要 cancel finality + CAS 原 run + finishedAt + 审计 | 重读原终态/事实，replay no-op；不写 cursor，不建新 run/Order |
| R | 锁 singleton 扫描游标、预留≤50身份、先提交检查位置，再逐 run B/C | actor 死亡后循环可再次遇到候选；检查位置无 mutation 权限 |

新本地事务使用 Spring 独立 bean/public 入口及 5s timeout，SQL 受限入口固定 search_path 和 lock_timeout。网络查询/PLACE/CANCEL 不进入 A/B/C。unique/deadlock/连接失败退出失败事务，不在污染事务中推断或重造下一业务身份。

## 终态与取消证明

唯一新增成功状态为 `SUCCEEDED`。完整执行需 Order=FILLED，唯一 durable fills 累计=原 qty，并校验 account/symbol/venue/env/external ID/fill ID/strategyRun 血缘。缺 fill、overfill、scope mismatch 或 MAY 未决保持非终态。Ledger 继续由原 owner 独立幂等恢复，不是 StrategyRun 成功的额外条件。

`FAILED` 来自确定风险/下单拒绝、既有 REVOKED_BEFORE_SEND no-order 事实，或已证明的最终取消；它不表示零成交。`OkxRestReconcileService` 对 CANCELLED 原 Order 在事务外 getOrder，保留查询前 identity/version，再补 fills。仅明确最终 CANCELLED、完整 scope/原数量、0≤q<原 qty、durable fills=q、原版本仍匹配时，受限 C writer 原子写 finality 与 FAILED。cancel ACK、空页或一般 CANCELLED 字面值均不能终结 run；完整取消成交仍先走原 B2 Order 纠正。

若后来实际 Trade 与已有 finality 矛盾，V51 的 Trade AFTER INSERT observer 保留真实成交并追加 `CANCEL_FINALITY_CONTRADICTION` 审计；不丢 Trade、不重开 run、不授予重发。该分支通过独立 PostgreSQL 诊断验证；真实 cancel-zero/partial 场景另由 Synthetic Venue 证明。

## Cursor、legacy 与恢复 owner

`last_triggered_at` 的新写入为 admitted dueAt，不是 scan now 或 finishedAt。scanner 与锁内 admission 共用 `StrategyScheduleTiming`，effective reference 为 max(旧水位, 已有结构化 V50 最大 due)，NULL 使用 createdAt−1s。每次 scan 每个 schedule 最多一个按 dueAt 排序的积压窗口。原 blind cursor callback 已退出 canonical 路径。

`StrategyRunRecoveryTick` 在 trading-components 启用且 scheduling 非 false 时启动独立单线程固定延迟检查；没有全局启用其它定时器。每批使用持久循环位置，独立于 manual/schedule、enabled/window/due。默认 5s，配置范围 1–60s，关闭时停止 owned executor。scan-once 也先执行一批恢复，不再按每个 schedule 重复全表扫描。

legacy 无 work 不回填、不猜参数。已有唯一且 scope 一致的 Order 可按真实终结事实结束原 run；缺 work/binding/发送协议继续明确 unresolved。V50 旧多 Order 会使整个 V51 回滚，保留原两条 Order 和 V50 schema；不清洗数据以通过升级。

## Crash-window inventory 与实际 proof

| 状态/边界 | 实际覆盖 | disposition |
| --- | --- | --- |
| CREATED entry commit / 下一个 durable fact 前 | 原始死亡、paused owner、两个 successor、manual override、disabled startup/tick、A commit 前后断连 | `RESTART_SAFE_RECOVERY`，同 run/work |
| B 内 run 状态/Order/初始 V49 | binding rollback、B 显式 rollback、B commit 前后断连；已提交 B 后暂停与死亡 | `ATOMIC_NEXT_STEP` + 原 binding 恢复 |
| DISPATCHING + NOT_ARMED | successor 复用原 Order 进入原 V49；旧 owner 恢复后不能建第二 Order/再次 PLACE | `RESTART_SAFE_RECOVERY` |
| DISPATCHING/RUNNING + REVOKED | 原 no-send query 事实、旧 owner 暂停恢复、terminal replay | `RESTART_SAFE_RECOVERY`，FAILED |
| MAY + 原 owner 暂停或死亡 | successor 查询/恢复不发送；存活持许可 owner 最多一次；死亡且无 venue 事实保持未决 | `CORRECTNESS_REQUIRED_UNRESOLVED` |
| RUNNING + 完整 Order/Trade | 原始死亡、双 JVM terminal、replay、C commit 前后断连、独立 tick | `RESTART_SAFE_RECOVERY`，SUCCEEDED |
| CANCELLED 未最终证明 / 最终证明 | ACK negative、零成交与部分成交 finality，保留原数量和版本 | 未证明时 unresolved；证明后 C→FAILED |
| 新窗口 | 原始 CREATED/RUNNING 两条回归均实际触发后续窗口；V50 同窗口双 JVM 3 次；恢复与 scanner 并发 | 不重建原窗口；可收敛 run 不永久阻塞未来工作 |
| legacy unresolved prefix | V50→V51 70 行保留、重建 Spring repository 后继续循环，每批50不重复、两批覆盖70 | 公平检查，缺 work 不造指令 |

核心环境为自有 PostgreSQL 16.15 / V51、真实 Spring/RiskGate/adapter path、独立 NQ/Venue JVM。控制器只使用隔离 fixture、真实业务命令、代理 cut-point 与独立数据库 reader；SQL 结构/legacy 诊断单独标识，不冒充真实发送证明。

## 验证记录与历史失败

原始日志目录：`backend/nq-app/target/b5-v51-implementation-attempt01/`。80份 canonical proof 的 hash、来源、场景与逐份结果见 [proof-index](l4-b5-v51-implementation-attempt01/proof-index.json)，全部通过完整树逆映射等价校验。其中77份 payload PASS、1份历史 Kill fixture FAIL、2份无结果字段；包含重复验证和历史尝试，**不能解释为80个不同场景全部成功**。原始身份和完整日志继续单独保留在 target；suite/日志 hash 见 [validation-log-index](l4-b5-v51-implementation-attempt01/validation-log-index.json)。

| 调用 | 如实结果 |
| --- | --- |
| compile-01 / compile-02 | PASS |
| schema-01 | Maven dotted property 被 PowerShell 拆分，测试未执行；后续使用完整引号 |
| schema-02 | 1 failure：旧共享 fixture 期望50、实际51；业务断言前失败 |
| schema-03 | 1 test，0 failure/error/skip |
| lifecycle-01 | 清理 import 后遗漏 Collection，编译失败；已修复 |
| lifecycle-02 | 原始 CREATED/RUNNING 两场景 + schema，3 tests，0 failure/error/skip |
| matrix-01 | 同窗口3次 PASS；新矩阵前10场景 PASS；Kill 用例缺测试 ENGAGE 权限导致本调用1 failure，后2场景未执行 |
| matrix-02 | 修正 Kill fixture 后剩余3场景 PASS；legacy/fairness 和迁移拒绝2 tests PASS；共3 tests，0 failure/error/skip |
| commit-tick-01 | A BEFORE_DROP 和 disabled manual tick 行为断言通过，但两个调用的 evidence batch 名过长被 exporter 拒绝；2 errors，未记为有效整套 PASS |
| commit-tick-02 | A/B/C × COMMIT 前后断连6场景，及独立 manual startup/tick；2 tests，0 failure/error/skip |
| targeted-01 | 显式 import 遗漏 Comparator，编译失败；已修复 |
| targeted-02 | 策略/扫描/普通 Order/取消对账与 schema 目标测试 PASS |
| b1-b4-v49-01 | 显式 import 生成时误引入本地 assert helper，编译失败；已修复 |
| b1-b4-v49-02 | 5 tests，0 failure/error/skip；B1 两种 query-first，B2 4种 OCC/分笔与环境场景，B3 2种 Kill/restart，B4 healthy/process-death，V49 11种发送/撤销/竞争/commit 场景 |
| final-lifecycle-01 | 5 tests，0 failure/error/skip；原两项 P1、stale/restart、取消零/部分成交、schema/version/contradiction guard；使用最终 SQL |
| full-01 | **FAIL**：1891 tests / 1 failure / 0 errors / 115 skips；新 execution/preparation 服务依赖策略端口，违反 `only_order_command_adapter_should_depend_on_strategy_port`，共15条依赖违规；属于候选架构回归，不能归为环境阻塞 |
| exporter-tests-01 | Python 7 tests PASS；完整证据树逆映射、命名空间及 protected secret 字段保持 |
| architecture-process-01 | PASS；模块/包架构与 gateway mapping，完整13行进程矩阵 + stale/restart、A/B/C六个断连场景、disabled manual startup/tick；原 B/C 代理切点保持 |
| secret-preflight-01 | WSL Git 无法解析 Windows worktree 的 gitdir，扫描器尚未执行；保留失败输出，后续改为 Windows Git 明确清单，不更改 Git 配置 |
| secret-preflight-02 | pinned Gitleaks 8.18.4，108个本轮变更文件、0 findings；经 exporter 的 protected secret negative control 被 github-pat 规则拒绝，exit=2/1 finding |
| secret-final-01 | FAIL：304条 generic-api-key 告警均定位为4份 metadata 清单的 `path: SHA256` 行；逐条格式核验304/304，没有其它位置。原始清单/失败扫描保留，证据 owner 改为显式 path/sha256 字段；未改 scanner/allowlist |
| final-lifecycle-02 | 8 tests / 0 failures / 0 errors / 0 skips；最终服务归属下的原P1/未来窗口2 tests、V50同窗口3次、schema1、legacy/fairness/多Order升级拒绝2 tests |
| full-02 | **PASS**：最终候选1891 tests / 0 failures / 0 errors / 115 skips；测试前后 candidate manifest 一致，owned PostgreSQL 已清理 |

所有编译、fixture、导出错误与真实业务断言分开；历史失败日志不覆盖。故意种出的 V50 两 Order 是 migration 拒绝的正向 negative control，不是 V51 放行两个 Orders。

B1–B4 中的环境覆盖包括 SIM 与 synthetic LIVE 标签 fixture；两者都只使用本机 Synthetic Venue，没有真实 LIVE 或远程交易连接。此前未选择的旧 B5 opt-in suites 仍保留其历史 V50/旧生命周期前提，不记为本次 V51 PASS；本次是否执行以日志 suite 和 proof index 为准。

可复核入口（使用隔离 PostgreSQL/Synthetic Venue；PowerShell 中 dotted property 须完整引用）：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=PackageBoundaryArchTest,ModuleBoundaryArchTest,OrderCommandStrategyExecutionGatewayTest,B5V51RecoveryProcessTest,B5V51CommitProcessTest,B5V51RecoveryTickProcessTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.b5.v51=true'
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=B5DurableLifecycleCrashRecoveryTest,B5StrategyScanConcurrencyTest,B5V51SchemaPostgresTest,B5V51LegacyPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.b5.v51=true' '-Dnq.b5.lifecycle=true' '-Dnq.b5.scan=true'
```

Full Maven 为原样 `mvn -f backend/pom.xml test`。本地 runner 使用锁定镜像 PostgreSQL 16.15/V51、随机 owned 容器、loopback 端口、tmpfs 与 canonical `BackendCiLegacyAccountFixture`。预检为 schema51、1个合成 legacy account、0 exchange_accounts、0 exchange_account_credentials；显式隔离 datasource，不继承额外 Java/Maven options 或 profiles。默认115个 skips 未计作 PASS，已选择的 B5 opt-in 证明见上方单独调用。

- [full-01 原始结果](l4-b5-v51-implementation-attempt01/full-01-result.json)：日志 SHA256 `12f67a4612ae5e9520c0cb1a802fff403a16347e39776be889021bf7404bbb5d`；[当时的候选清单导出](l4-b5-v51-implementation-attempt01/full-01-tested-manifest.json) 对应源清单 SHA256 `dc64d9aab7fb7dae5637f0e890d0da36f923888656800dabd7c3a9674d4d0551`。
- [full-02 原始结果](l4-b5-v51-implementation-attempt01/full-02-result.json)：日志 SHA256 `1cb60c680bb5a1f8442fc8582a351feb4e59f44916bbcd4c7c770acd5719571c`；[最终测试候选清单导出](l4-b5-v51-implementation-attempt01/full-02-tested-manifest.json) 对应源清单 SHA256 `af4773a4bcb128577d6164c209ead858443dd955bd70c1743f3c39f3d7e7e076`。Full 后未修改 production 或 key fixture。

四份含路径/hash字典的 metadata 使用显式 `path` 与 `sha256` 行导出，避免将包含 key/token 的文件名误当密钥字段；没有替换/删减任何路径或 hash。原始字节保留在 target，导出记录源格式（缩进、ASCII、CRLF/LF）和源 SHA256；[manifest-export-validation](l4-b5-v51-implementation-attempt01/manifest-export-validation.json) 证明4份导出可逆恢复到逐字节相同的源清单。这只影响证据表示，不改变 Full 测试候选或 synthetic proof 事实。

## 收尾与未授予权限

stage-assets：最终 `scanned=1873 / reviewed_exceptions=138 / errors=144 / error delta=0`，exit=1；仍为独立的 `DELIVERY_COMPATIBILITY_BLOCKER`。未修144 hashes、registry、exception 或 validator。

精确起点在 [starting-files](l4-b5-v51-implementation-attempt01/starting-files.json)；[scope-manifest](l4-b5-v51-implementation-attempt01/scope-manifest.json) 列出本轮52个非 evidence 子目录文件的前后 hash，[protected-manifest](l4-b5-v51-implementation-attempt01/protected-manifest.json) 保留 V1–V50、已接受合同、旧证据和禁止修改文件的起终点一致性。新增 evidence 文件由 [final-identity](l4-b5-v51-implementation-attempt01/final-identity.json) 单独索引；聚合算法明确排除该索引自身，避免循环 hash。它还确认最终候选仍匹配 full-02 manifest、HEAD/index/branch 均未变且 staged paths 为空。

秘密扫描使用经 supply-chain lock 验证的 Gitleaks 8.18.4 和原 CI TOML；不改 allowlist。完整树逆映射、protected-field negative control、文档链接、diff 和实际检查记录见 [checks](l4-b5-v51-implementation-attempt01/checks.json)。扫描器的原始结果与逐文件扫描 hash 保留于 `backend/nq-app/target/b5-v51-implementation-attempt01/secret-final-02/`，该终检在报告/索引固化后执行。

正确性限制保持接受合同：MAY 未决、不可证明的 legacy work 与非 OKX 自动重发协议缺失继续明确 unresolved；这些状态不获得新发送许可。隔离 PG 证明不代表生产规模的 migration 锁耗时、生产升级或旧新 strategy writer 混跑已经接受。Full Maven 和本地多 JVM 证明不替代独立 review 或 exact-head CI。

实施结论仅适用于上述候选和实际覆盖范围：两个原始 P1 的同 run 恢复、唯一 Order/V49/PLACE、暂停旧 owner、双 successor、重启幂等和未来窗口均已通过；已知本轮 correctness P0=0、LOCAL_P1=0。历史 P1/FAIL 接受事实不改写，独立验收尚未完成。

```text
IMPLEMENTED /
PENDING_INDEPENDENT_CORRECTNESS_REVIEW /
B5_STRATEGY_RUN_DURABLE_EXECUTION_V51_IMPLEMENTED /
STRATEGY_CREATED_OWNER_DEATH_PERMANENT_ORPHAN_P1_REMEDIATED /
STRATEGY_RUNNING_OWNER_DEATH_PERMANENT_BUSY_P1_REMEDIATED /
SAME_RUN_ORDER_IDEMPOTENCY_PROVEN /
DURABLE_STATE_CRASH_WINDOW_COMPLETENESS_PROVEN /
FUTURE_WINDOW_PROGRESS_PROVEN /
V49_V50_INVARIANTS_PRESERVED /
P0_0 /
LOCAL_P1_0

B5 = NOT_QUALIFIED
stage = 0
commit = NONE
push = NONE
```

本轮不独立 review、不继续 B5 qualification。完成后下一动作仅为 `NQ-GATEAUDIT-PHASE6-L4-B5-STRATEGY-RUN-DURABLE-EXECUTION-V51-INDEPENDENT-CORRECTNESS-REVIEW`。
