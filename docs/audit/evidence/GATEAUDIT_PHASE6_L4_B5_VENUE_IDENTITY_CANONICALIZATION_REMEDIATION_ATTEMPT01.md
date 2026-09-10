# B5 venue identity canonicalization remediation attempt01

任务：`NQ-GATEAUDIT-PHASE6-L4-B5-VENUE-IDENTITY-CANONICALIZATION-REMEDIATION`。2026-09-10，NQ-only。本地实现与最终完整回归通过；本轮是 implementation，不是独立接受。B5 保持 `NOT_QUALIFIED`，未继续剩余资格矩阵。

## 根因与旧失败保留

前次 review 在真实 Spring / PreTradeRiskService / PostgreSQL16.15 / V49 / Synthetic Venue 上复现：`venue=okx`、Order=`ACCEPTED`、authority=0、PLACE=1。`PlaceOrderRequest` 仅 trim，application 按精确大写选择 authority，gateway 独自 uppercase 后选择 OKX adapter。同一个输入在两个正确性边界形成不同 identity，属于 `P1 / AUTHORITY_BYPASS_PATH`。

保留[前次 review 正文及行内意见](l4-b5-venue-canonicalization-attempt01/review-attempt01-original.md)、[原 bypass 事实及 raw 文件索引](l4-b5-venue-canonicalization-attempt01/original-bypass.json)。既有 V48 红色、旧 remediation STOP、Contract B 及 V49 implementation evidence 均不改写。前次 Full 的绿色记录仍是原候选的回归事实，不能证明没有这条旁路；本轮以新冻结候选的 Full 为准。

## Canonical owner 与输入合同

新增 `nq-core` trading domain 的 `TradingVenue`，职责只有 `parse external identity → validate supported venue → canonical enum`。已存在的 `SpotProviderRequests.Venue` 仅接受 dedicated provider 的 `OKX_SPOT`，语义不同，未扩充或复用它。

- 支持当前 ordinary adapters 的 `OKX / BINANCE / PAPER`；沿用 Java `trim` 的首尾空白规则，再以 `Locale.ROOT` 处理大小写。`OKX / okx / Okx / oKx` 都为同一 `OKX`。
- null、空串、纯空白、`foo / okx-test / unsupported / OKX_SPOT / SIM` 均拒绝；未新增 alias、registry 或 provider framework。SIM/LIVE 仍是独立 trade environment，不是 ordinary venue。
- `PlaceOrderRequest` 构造即解析，所以直接 application 调用与 HTTP 构造相同；拒绝发生在 Order、authority、command event 和外部 API 调用前。
- `CancelOrderRequest` 省略 venue 时继续从 Order 解析；显式值使用同一 parser。reconcile/recovery 只有省略 venue 时默认 OKX，显式空白不再作为默认值。

新 Order 的 `venue / exchange_code` 写入 canonical 表示：application request 是第一边界，JDBC 普通 INSERT 与 authority create helper 调用再复用同一 owner 防御。`OrderRecord.canonicalVenue()` 解释读取到的 identity，但保留 `venue()` 原始持久值，避免偷偷重写旧事实或掩盖 Trade/Order 原始 identity 失配。

`ExecutionIntent / ExecutionIntentDraft / execution_intents` 当前没有独立 venue 字段，仍绑定 canonical Order，未引入额外事实源或改写 dedicated provider/session 合同。

## 有限相邻搜索与边界

已沿 ordinary application、gateway、repository、recovery/reconciliation、cancel 搜索大小写比较、normalization、raw switch 和 adapter mutation 调用。

- Order 创建、sender arm admission 与 no-order finality 使用 `TradingVenue`；gateway 的 adapter map 键改为 enum，删除其自有 normalizeVenue，并用 canonical 值构造 adapter PLACE/CANCEL/QUERY 请求。
- gateway 的不确定 PLACE 回执分类同样改用 canonical identity，避免 legacy 小写 Order 的损坏 ACK 又被当成确定拒单。
- OKX recovery、REST/WS 消费、相邻 Paper/Binance ordinary 分支，以及 maintenance 路由改为 canonical 比较；没有重构 provider 或 scheduler。
- cancel 的目标 venue 校验使用 canonical identity；内部 reconciliation request 复用同一 parser，其他 symbol/environment 规则未扩大修改。
- 现有 durable reconciliation cursor 的请求 venue 先 canonicalize，避免小写调用创建第二个 cursor identity；SQL 的历史事实选择、状态/版本/Trade 身份证明未放宽。
- 实际 ordinary PLACE 仍由 `OrderCommandService → TradingVenueGateway → AdapterBackedTradingVenueGateway → OkxExchangeAdapter → OkxHttpClient → JDK HttpClient.send` 执行。发现的唯一 ordinary gateway 调用仍在已提交成功 arm 之后。dedicated live pilot gateway 保持其自身授权边界，未启用真实运行。

## Legacy-row 决策与 V49

历史 API 确实允许小写入库，不能声称旧库不存在此类行。此次在自有 PG16/V49 中通过明确标注的 pre-start SQL fixture 构造旧 `okx / SENT / v2 / missing authority`，再启动两个真实 Spring JVM。重复 lowercase/canonical PLACE 均复用原 Order；recovery 按 parser 识别其为 OKX 并查询 absent，authority helper 拒绝 grant/revoke，保留 SENT/v2 和原小写值，产生 `MANUAL_RESOLUTION_REQUIRED`；PLACE/CANCEL、Trade/Ledger 新增均为0。

安全结论不依赖历史数据迁移：不能把缺失 authority 补为 NOT_ARMED，不能从小写旧行恢复发送资格。既有 cursor SQL 仍选择 canonical 持久行，未声称本轮恢复所有历史非 canonical 行的自动成交/账务收敛；旧行正向事实及必要数据修复须单独核验/授权，不能用 normalization 掩盖既存 Trade/Order identity 矛盾。本轮只证明旧表示不能再次绕过 authority，并维持保守查询/人工处理。

没有连接生产或未知共享数据库、没有核验生产非 canonical 行数量，没有执行存量修复。此次 mutation safety 无需 V50；migration delta=0。V49 SHA-256 始终为 `ebea77f85cae76d88e052a99b647573ec3b6ba5ea0464f9083012aac2a883b93`。三列、三态、one-shot CAS、commit-unknown 与 finality 原子性合同不变，没有 lease/epoch/generation。

## 验证

目标日志与逐场景原始/规范化 proof 见 [test-runs](l4-b5-venue-canonicalization-attempt01/test-runs.json) 和 [proof-index](l4-b5-venue-canonicalization-attempt01/proof-index.json)。关键进程证明使用自有 disposable PG16/V49、真实 Spring/RiskGate、独立 Synthetic Venue JVM；运行 controller 只读业务最终事实，单测和架构检查单独计数。

| 证明 | 已执行边界 |
| --- | --- |
| 原 lowercase bypass / case matrix | 5个独立库/JVM，4种大小写加首尾空白；每组8种非法输入无Order/authority/event/venue API调用；合法输入持久OKX、authority1/MAY、PLACE1、重复调用新增0 |
| Recovery wins / stale resume | A 使用 lowercase input，prepare后暂停；B原子REVOKED+CANCELLED/v4；A恢复PLACE0 |
| Sender wins | A使用lowercase input，confirmed MAY后暂停；B absent不能false CANCELLED，A原调用1PLACE、重复0；最终FILLED/Trade1/TradeExecuted1/Ledger4 |
| 近同时、Kill、死亡、提交不确定 | lowercase A 的11个场景：SENDER_WINS、MALFORMED_ACK、DEATH、KILL_BEFORE_ARM、RACE1-3、ROLLBACK、REJECT、BEFORE_DROP、AFTER_DROP |
| PostgreSQL V49 | 6轮sender/sender及sender/revoke竞争单赢家，权限、PK/FK、不可逆/删除、回填回滚与missing保守语义 |
| Legacy lowercase | 原SENT/v2及小写值不改；两个JVM重复命令+recovery只查询、manual、0PLACE/0CANCEL |
| B1直接回归 | accepted-timeout、lost ACK各一轮，同client query-first、无blind retry |
| B2直接回归 | STALE_PLACE、MULTI_PARTIAL，SIM/LIVE合成domain环境；OCC、clientOrderId、Trade环境与每fill唯一事实 |
| B3直接回归 | PRE_ACCEPT、RESTART_PRE_ACK，Kill admission与query/recovery保持 |
| B4直接回归 | ATOMIC_DEATH，SIM/LIVE合成domain环境，Trade事件/账务跨进程恢复；非完整B4 matrix |
| 架构 | ModuleBoundaryArchTest、PackageBoundaryArchTest |

SIM/LIVE 是上述隔离 fixture 的 domain 字段覆盖；所有进程 real-exchange/LIVE execution 关闭，没有真实 provider、真实资金或 credential 操作。

失败历史：`targeted-01` 因旧分类单测 mock 整个 OrderRecord，新增 canonicalVenue 方法返回 null，得到 REMOTE_UNAVAILABLE 而不是 DEFERRED，1 failure。修为真实 lowercase OrderRecord，保留原不确定回执断言；`targeted-02` 通过。不存在为通过测试改变生产事务/authority 约束。

## 最终冻结、Full Maven 与 disposition

| 运行 | Tests | Failures | Errors | Skipped | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| targeted-01 | 57 | 1 | 0 | 0 | FAIL，旧 mock fixture 已修复，原日志保留 |
| targeted-02 | 61 | 0 | 0 | 0 | PASS |
| affected-03 | 29 | 0 | 0 | 0 | PASS，含两个架构执行引擎的实际计数 |
| final-observation-04 | 21 | 0 | 0 | 0 | PASS，含最终全 API 调用计数与 legacy 证明 |
| full-01 | 1873 | 1 | 0 | 98 | FAIL，Controller 旧错误文案断言不符合统一 parser 合同 |
| full-02 | 1873 | 0 | 0 | 98 | PASS，最终冻结候选 |

[full-01](l4-b5-venue-canonicalization-attempt01/full-01.json) 完整保留失败，不归类为环境阻断：`TradingVerificationControllerLocalTest.shouldReturnUnifiedIllegalArgumentError` 期待旧 `unsupported recovery venue: UNKNOWN`，实际在 maintenance 调用前得到 `unsupported trading venue`。仅更新测试为 canonical 错误，并增加 `verifyNoInteractions(tradingMaintenanceService)`；生产实现未再变化。另有汇总脚本在 Maven exit=1 后因 Windows 混合编码触发 `UnicodeDecodeError`；原字节日志未重写，追加 observed-result 解析 ASCII summary，后续 runner 使用容错解码。

[full-02](l4-b5-venue-canonicalization-attempt01/full-02.json) 于 `2026-09-10T03:15:38Z` 至 `03:17:30Z` 执行原命令 `mvn -f backend/pom.xml test`，exit=0 / BUILD SUCCESS。Java21、PostgreSQL16.15/V49、固定镜像 digest、自有 loopback/tmpfs 容器；使用 canonical CI legacy PAPER account fixture，exchange account/credential 行数为0，清除继承 profiles 与 Java/Maven overrides，显式绑定本地 datasource。测试后核对容器所有权并清理完成。日志 SHA-256=`11964498688555bff7cdc0547eedde741a085edea2205f462252ab2e27b31ecd`。

[最终 tested manifest](l4-b5-venue-canonicalization-attempt01/full-02-tested-manifest.json) 冻结1779个 backend/CI相关文件，原始 manifest SHA-256=`b0b2895682c15af9b0eb9abd1d45118436f518eecebfab6dd0a16cb6a675f0b3`，Full 起止及归档时 hash mismatch=0。导出版使用 `path / sha256` 记录结构，可逆还原原始映射；该原始 digest 不是导出版文件本身的 digest。98个跳过项的具体条件见 [skips](l4-b5-venue-canonicalization-attempt01/full-02-skips.json)，包括未启用的专用PG/进程集及平台条件，不计作通过；B5及直接相关B1–B4 opt-in 场景已在前述独立目标运行中执行。

[本轮候选 delta manifest](l4-b5-venue-canonicalization-attempt01/candidate-manifest.json) 相对进入整改前 raw-byte 工作树快照比较，共29个 backend生产/测试文件；不是相对 HEAD 覆盖原 V49候选。排序 `path + 空格 + sha256`、LF连接且末尾无LF的指纹为 `65006b496f6c992de5b6595bdf537c843268a166856b75219ac1ee5f083e8f29`。367个受保护既有文件、Git HEAD/index 均未变化。原 V49实现文件和本轮delta的最终完整内容均包含在 Full tested manifest 中。

28份逐场景 proof 已归档，并验证 raw→canonical 及整棵 JSON 逆向还原相等。B1旧证据格式由本地归档脚本仅映射已知 `db / databaseIdentity / dbUrl / placeResult` 身份引用；其余复用现有 `synthetic_evidence.py`，无 exporter source 修改；原日志/原始proof继续留在 target，索引记录路径与 SHA-256。上述进程证据在 Full前最后只增加 enum单测期望值、全API计数观测和Controller拒绝边界断言，未改变生产逻辑，原 authority 与 B1–B4 场景证据仍有效。

本地 disposition：

```text
IMPLEMENTED /
PENDING_INDEPENDENT_CORRECTNESS_REVIEW /
B5_VENUE_CANONICALIZATION_IMPLEMENTED /
AUTHORITY_BYPASS_PATH_REMEDIATED /
V49_AUTHORITY_ENFORCED_FOR_CANONICAL_OKX /
STALE_SENDER_MUTATION_P1_REMEDIATED /
P0_0 /
LOCAL_P1_0
```

该结论限本轮有限 reachable-path 搜索及已执行证明。独立接受尚未获得，B5=`NOT_QUALIFIED`。下一动作：`NQ-GATEAUDIT-PHASE6-L4-B5-MUTATION-AUTHORITY-INDEPENDENT-CORRECTNESS-REVIEW-ATTEMPT02`。没有 exact-head CI、交付或运行授权推进。

当前候选基线 HEAD=`86c8ad84542636364f6c21e78bc292a323cbdff7`，branch=`audit/post-gatey-agent-baseline`。本轮 stage=0 / commit=NONE / push=NONE；输入已有改动保留，AGENTS、Skills、.github、历史 evidence 和迁移无本轮修改。

证据卫生：固定 gitleaks8.18.4、固定下载digest、复用原CI配置，未扩 allowlist。第一次加入全量路径键 manifest 后出现82个 `generic-api-key` 命中，全部位于该manifest的路径键/内容hash组合；保留 `backend/nq-app/target/b5-venue-remediation/secret-scan-{result,findings}.json`。改为显式 `path / sha256` 记录后可逆校验通过，扫描68个候选/API/证据文件为0 findings，未改写 raw manifest 或 Full 日志。最终证据卫生与完整性检查结果见 [hygiene](l4-b5-venue-canonicalization-attempt01/hygiene.json)。

工程经验评估：当前已证实的是同一个 venue 的两处不一致入口（authority与ACK分类），已纳入同一 parser 修复。没有为其他 identity 建立“两次以上相同 normalization 根因”的新证据；既有 Trade environment 持久化漏字段不是同一个 normalization 机制，因此未新增治理文档或修改 Skills。
