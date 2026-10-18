# NQ V1 M2 当前验收结果（2026-10-18）

当前结果：`M2_FINAL_EVIDENCE_CLOSURE_COMPLETE_WITH_EXTERNAL_LIMITS`。105 VERIFIED、4 BLOCKED_EXTERNAL、5 SUPERSEDED_BY_CANONICAL_SIM、2 OUT_OF_SCOPE；27/31页完整验收，另外4页保留准确外部来源限制。最新统一候选18178/dist-r64 +18299/candidate-r58.jar +PG16.15/Flyway33。

正式矩阵是当前功能状态；本次最终报告及证据位于 `E:/Project/nq-production-reconstruction-output/m2-final-d21305c2a13d4f0c91177bdef3ee9ac6/REPORT_M2_FINAL.md`。M2只验Web更新交接逻辑；真实安装包、安装与回滚属于 `M3_RELEASE_IDENTITY_PREREQUISITE`。未Git提交、未Push/部署、未启动M3，不宣称生产发行完成。

以下为保留的历史实施记录，各“当前/最新”表述仅指其记录时点，不覆盖以上本次结果。

---

# NQ V1 正式全站重构实施记录

当前状态：`PARTIAL / IMPLEMENTATION_OR_VERIFICATION_GAPS_REMAIN`。这是持续实施记录，尚未形成完整交付或最终用户验收。

最新源码验证（2026-10-16）：完整 Maven 2854 项报告测试、350 项原条件跳过、0 失败/错误；450 前端单测通过。新 canonical SIM 的72点权益、22笔买卖成交、90条账务经正式API/PG及两JVM重启核对；中英文1536/390页面通过，手机权益刻度重叠已修复。均为临时隔离候选，未替换下述V22/R9用户验收环境。详见末节及功能账本，不能据此宣称全站完成。

最新本地验收：V22 后端与目录页布局 r9 前端已安装（18128 / 18280，保留两类 SIM 入口）。34登记路由在R8完成68项入口检查，原目录页390溢出由R9的1536/390/320正式只读复查闭合；规范UUID的Shadow404负例另验。95旧表全部原列、原净值多重集和48订单/48成交/196账务保持。全站完成与最终全任务格式门槛仍未关闭。

实施目录：`C:/Users/Lingyu/.codex/worktrees/nq-frontend-production/nexus-quant`。
分支：`codex/v1-frontend-production-reconstruction`；起始 SHA：`ba3e157ce8063f6a90893ce0cfc1e05dd94fe585`。当前所有修改尚未提交；Git commit、Push、PR、Merge、Tag、远程部署和正式发布均未执行。

## 输入及范围

沿用用户确认的正式全站实施范围和现有真实业务合同。参考图仅决定视觉语言；现有能力不得遗漏，新能力不能以模拟成功冒充。原型、18099 Demo、C4及其数据保持独立。

两张批准参考图为原型目录 `reference-input/references/01-dashboard-master.png` 与 `02-page-family-master.png`；SHA256分别为 `5f5a3325b2f2d7bbf581ba24f9bc05217d8c87618750ff151bae90fffbd74ab2`、`eb89af78a71824e8d6f0618f80a6e9c17a004a3142f8e8027bfe4eaa2a10e41c`。最新原型反馈持续生效，不能回退早期R2简化稿。

本任务已读取正式任务书、原始问题清单、归并v2、R2复核、原型实现报告及清单。原Word标的目录截图已定位至 `original-instruments-image15.png`：红框包含说明文案和Demo预览的筛选、SPOT、DATA_SOURCE区域。该定位不是L01高清问题关闭，也不是所有红框已整改。

`PRODUCTION_FUNCTION_MATRIX.csv`为唯一当前功能账本。初次对账415行；当前648行，原身份和顺序保留，继续发现本范围遗漏时补充，不能以此作为最终覆盖上限。原始路由与需求记录保留。

## 已实际实现

- 正式深色外壳、全宽布局、分组导航、侧栏连续山景渐变、紧凑控件和自动时区；保留旧正式入口。
- 认证用户偏好正式持久化：语言、声明提示、自选有序列表、默认查看交易对及通知已读时间字段；默认账户继续复用账户模块的正式set-default。
- 设置与登录声明：默认每次登录提示，刷新不重复；保存隐藏偏好成功才关闭。中文/英文风险正文分区显示，完整canonical双语原文和第三方原文仍可主动访问。行情页仅保留设置法律入口，图表attribution保留。
- 自选新增、删除、上下排序、默认查看和失效标的处理。新增或改选由服务端核对正式现货标的目录，最多20条；旧版本409不自动覆盖；失效项仍可移除。
- 行情页默认市场自动查询、当前支持周期补4h、主副图缩放联动、重置、透明悬停信息、离开关闭、键盘选择/取消、MA窗口和开关。MA不跨缺段、异常或未收盘窗口；不足窗口不画线。
- 行情价格/数量读取保留原始JSON十进制词法值，事实明细使用精确字符串；图表几何显式转换number。此处还不是全站经济字段精度验收。
- K线来源和可见时间从同一市场、同一bar身份的已有数据库事实批量读取；不修改冻结HistoricalBar身份，不新增行情事实源。来源缺失保持UNKNOWN。
- 行情查询、数据集、采集任务、公开捕获、回测创建的时间控件明确秒，逆序或相等范围不能提交。公开捕获原有整点/已关闭/数量限制保留。
- 旧会话响应取消、换身份清缓存、错误诊断凭据字段及嵌套拒绝值脱敏；读取连接失败与写操作未知结果分别表达。已声明默认账户失效时不静默换账户，临时查看失效需明确重新选择。
- 每用户唯一默认账户：V3保留旧唯一有效默认，清除停用或跨scope歧义；用户行锁和部分唯一索引原子切换。跨venue、并发、重登、停用不回退和重启持久化已有真实PG/API/UI证据。
- 账户表现新增真实派生测量、采样器、读取API和首页。仅统计NQ管理投影、USDT计价，快照已经包含持仓；保留快照/资金分录/估值bar来源。净收益扣除净注资，收益率以期初权益为分母，非TWR。缺估值/流量不补假零；最多1000次采样且明确实际覆盖，不重建无事实的过去曲线。尚未形成真实账户完整收益序列及正值图表的浏览器验收。
- 顶栏业务对象搜索和正式详情定位。账户、策略和版本的权限过滤、字面通配符、分页约束、桌面/窄屏定位、无效身份与读取失败已有证据。SIM归属沿发布及冻结版本追溯，不能依赖常为system的createdBy。RUN、SIM及canonical订单已沿正式新链验证，500条窗口外原订单的精确详情和刷新亦通过；普通账户订单定位分支及其余状态矩阵仍待。
- 标的目录的中文业务说明、市场类型/目录状态、来源、筛选、详情和URL已有实现；这些明确功能不能证明U01无文字红框已修复。U01按用户决定保留PENDING_USER_CLARIFICATION/NON_BLOCKING；L01最新显示证据按NEXT-14矩阵与专项记录核对。
- 正式公共捕获成功保存8根真实OKX历史K线及源响应/身份。正式采集联调发现请求时间提前作为可见时间、排他游标漏掉首根两项问题：分别保留红例，最小修复后16项core及8项adapter目标测试通过；当前缓存完整公共来源的重新验证仍继续，不能把旧测试行情补位当成采集完整。

## 数据及接口

新增认证模块接口：`GET /api/user/preferences`、`PUT /api/user/preferences`。用户身份只来自当前认证主体；不接受客户端指定另一用户。CAS版本保证并发保存只有一个赢家；Viewer可写自身偏好，原账户业务写权限不放宽。

当前源码候选最高迁移为V27，已执行的V1—V27保持冻结，后续schema修复只能新增V28。用户验收环境仍停留V22，未安装本轮候选。新闻、盘口、四价时点和费用已按各专题实施，真实来源及未完成范围以当前功能账本为准。prod仍默认关闭自动迁移；隔离脚本显式执行和validate，不改变正式部署默认行为。

`GET /api/exchange-accounts/{accountId}/performance`先校验自身账户，查询区间有序且最多366天，GET不采样。内部采样每批最多50个账户、每账户约15分钟一次，估值最多100种资产且只使用可见已收盘且2小时内的现货价格。派生测量保留366天；不删除账务、持仓或订单来源。收益与回撤需要至少2个完整采样；最大回撤金额与率分别取区间最大值，可能对应不同回撤段，均为采样统计。

`GET /api/search`只读，2至80字，每页20条、最多1000条；自身账户及关联业务身份过滤，不输出凭据、配置或账务金额。定位普通订单只临时切换已确认的自身ACTIVE查看账户，不变更默认值，不执行交易。

`GET /api/marketdata/bars`增加来源与可见时间字段，原OHLCV数值字段及查询合同保留；补充只读查询最多500根，按完整市场身份匹配。正式构建不注册原型model或Mock服务。

## 当前验证

全部证据位于独立目录 `E:/Project/nq-production-reconstruction-output`，不得携带该目录的私有运行文件进入源码/包。

| 验证 | 实际结果与边界 |
|---|---|
| 正式前端单测 | 78项通过；`account-performance-frontend-unit.log`；新增精确金额/比例展示，旧74项同时执行 |
| 类型及Production构建 | 通过；`frontend-market-final-build.log`；已有大chunk警告仍在 |
| 偏好服务 | 5项通过；新增/失效/顺序/约束，不依赖真实交易所 |
| 真实PG偏好与升级/CAS | 2项通过；fresh、V1→V2、持久化重读、双用户和并发 |
| 正式Spring/JWT/PG API | 4项通过；自身偏好、旧版本、目录校验、跨会话、权限、行情来源身份；清理修正后的 `preferences-api-final-pg-test.log` |
| 行情Controller定向测试 | 8项通过；原读取/采集合同保持 |
| 真实prod HTTP偏好 | 首批7项通过；新行情端到端来源验证继续单列 |
| 浏览器设置/声明 | 4项通过，2560/1366/390；`foundation-ui-results.json`；使用真实API |
| 浏览器自选/MA/时间/读失败 | 4项通过，1366/390；`market-ui-results.json`；真实正式API与PG16，行情来自明确FIXTURE；只在失败用例阻断读取连接 |
| 法律全文Production UI | 2项通过；`legal-production-e2e.log`；此补充用例隔离替换认证/只读API，加载后断网展开原文，不证明离线冷启动 |
| 每用户默认账户 | API/并发6项和偏好PG3项通过；`user-default-pg-test.log`、`user-default-upgrade-pg-test.log`；升级保留有效唯一默认、歧义重选 |
| 账户表现及搜索PG/API | `object-search-ownership-pg-test.log` 5项通过；正式注资服务、无重复资产、可见估值/缺失、用户隔离、GET无写副作用、正式创建策略/版本后搜索。受控投影和行情fixture仅用于估值边界，不冒充成交经济资格 |
| 默认/时间/目录UI | `account-catalog-ui-results.json` 5项通过，1366/390，真实API与PG；目录明确FIXTURE；首轮窄屏标签定位失败及修正后结果分别保留 |
| 搜索及重启UI | `object-search-ui-results.json` 3项通过，1366/390；真实创建元数据、准确版本与账户定位、非法/不存在身份、断连无示例。默认值及NO_BALANCE采样重启保留，不是正值资产曲线或完整经济证明 |
| 公共捕获 | 已取得并采集100根真实OKX公开历史及规则，冻结数据集`9911d532-5a13-4f70-8888-23168dfbfe2b`；原窗口400和8根证据分别保留。没有私有调用或凭据读取 |
| 行情观察时间及起始游标 | 两个确定性红例分别证明提前可见5秒和首根丢失；`market-ingestion-visibility-green.log`16项、`okx-historical-range-green.log`8项通过；真实8根重验和MA3/悬停退出`real-market-ui-results.json`2/2通过，之后正式采集100根。旧混入FIXTURE失败不追认为通过 |

浏览器先在IAB检查；重复回归使用项目Playwright。系统缺少对应Chromium headless shell时，使用已安装Edge，记录原失败与实际channel。所有失败及修复后结果分别保留，未降低断言或增加skip。

## 仍需实施与验证

继续验证账户表现正值序列/估值/图表和统一统计、目录真实同步；盘口、新闻、四价实际发生时间、实际适用费用读取与来源冻结；搜索的全部浏览器定位/异常组合、通知、版本与检查更新、L01高清资源；主线的后续版本、未知结果与部分失败恢复、持续SIM，以及账户完整管理、调度、Shadow、资产/复盘、恢复/诊断和全部异常状态。不能因当前新增片段已有证据而停止全站实施。

当前已有嵌套组件动作102条补入账本，多数仍待明确接口链和真实操作验证。最新完整 Maven 已通过，350 项原条件跳过仍是未执行；定向高风险候选审查按各专题已有独立证据。全站正式E2E、集中独立复核、正式release-source双导出、安装升级、受影响备份恢复、新发行包资格及最终全部代码格式化仍未完成，不能以局部通过替代。

三项进度独立记录：整页全内容迁移尚无完整页验收，旧正式路由分母33、另有新增设置入口1；功能逐行状态以账本为准，NOT_STARTED不表示源码能力不存在；真实验证仅覆盖上表的局部链路。最终分母待嵌套Tab/Drawer/操作逐项分类完成，当前不报虚假的全站百分比。

## 隔离运行与后续边界

用户验收候选：`http://127.0.0.1:18128`，后端 `http://127.0.0.1:18280`，独立PG16.15 `127.0.0.1:15442/nq_frontend_acceptance`。后端明确监听127.0.0.1并已核对，当前沿正式prod/public-marketdata-manual模式进行授权公共只读联调；私有provider/client/LIVE/AI/DH仍关闭。本模式只开放正式公共行情接口，持续SIM轮询目前关闭；普通prod默认no-outbound可用外部启动脚本恢复。库内测试目录与部分旧测试行情明确标注，与真实公共捕获分开，均不代表策略成绩。凭据仅保存在外部私有运行文件，报告不输出。

LIVE、真实交易所私有调用、provider和真实client关闭，无真实交易操作。保留原型18127、Demo18099、C4及历史证据。C4原证据例外保留，不安排72小时重跑。

最终Production用户验收、集中独立复核、完整本地包资格均为PENDING。无直接Git提交/发布授权，相关动作尚未执行。

## 正式研究与SIM主线续接（2026-10-13）

正式API主线复用100根公共捕获的身份，完成策略/版本、研究、回测、评估及发布，见`formal-research-chain-result.json`。随后创建独立SIM `ptr-71bd98a4-e10f-48f9-a7dd-e60fdce75fca`，账户4、预算100 USDT；保持全局ENGAGED，经正式PAPER_MATCHING推进100决策，23 BUY＋23 SELL、46唯一成交及184成交账务分录。`formal-sim-persisted-facts-result.json`包含真实后端重启后的PG复读、资金/持仓/唯一性/风控/关联检查，相关违规计数均为0。费用和滑点是明确冻结的SIM模型输入，不能关闭NEXT-09实际费率来源缺口。

首次推进遇到尚未撮合的订单被409拒绝，实例身份保持；续接采用正式手动撮合，不修改DB交易状态或关闭停止保护。失败与后续证据分别保留。搜索已修正独立SIM的PAPER账户关联，支持真实SIM/订单/执行身份。新增精确订单详情不依赖总览500条窗口；`sim-object-search-order-pg-posting-test.log`6项PG/API包含第501单、所有权、404、GET无写证明；该窗口夹具不冒充真实成交。

IAB正式GUI另建可追溯身份，完成策略创建、单均线窗口/敞口版本、研究与数据集选择、秒/毫秒区间和捕获规则/明确成本输入、版本与数据分别绑定、创建/启动/评估/发布、独立SIM创建/启动/推进、BUY/SELL及停止确认。`formal-ui-chain-progress.json`保留服务端对象复读；GUI实例`ptr-73fad6ab-3e2b-47a4-b253-5c2fda418229`只推进7窗口，2成交后STOPPED，不作为100窗口完整GUI证明。BUY撮合由正式任务UI执行，SELL由正式撮合API续接；尚未完成全站E2E。

界面补上canonical SIM启动/停止入口；已成功创建身份立即保留URL，详情读取失败不重复创建。移除重复SIM页头，收起创建表单和技术身份；订单详情保留精确成交、现金本金/费用与对手分录用途。研究/回测对象选择及高级管理合同均保留，未删掉JSON管理入口。评估区间/基准时间及曲线轴显示自动时区；金额词法值传至展示层，图表坐标才转浮点；81前端单测通过。最新构建见`sim-execution-status-ui-confirmed-build.log`，3.2MB主chunk警告尚未消除。

GUI停止后最后一条已FILLED的SELL子策略执行仍是RUNNING，未前端伪装终态。已新增受用户/角色/实例过滤的显式刷新执行状态入口，仅委托canonical投影端口；`sim-execution-status-pg-first.log`3目标单测＋6PG/API通过，真实成功/重复投影和最终浏览器验证仍待完成。全局未迁移Recovery仍保持原边界。

功能账本现为532条、532唯一ID，新增动作和状态缺口计入同一分母。所有旧行保留；既有行号仍为起始源码索引，不作为最终验收行号或整页完成证明。全站和本地发行包仍为PARTIAL。


## 独立 SIM 资产、复盘与子执行续接（2026-10-13）

子执行刷新已真实GUI/API完成，重复投影返回projected=false并保留SUCCEEDED；2订单/2成交/10账务保持不变，GLOBAL_TRADING为ENGAGED且LIVE订单0。定向独立审查`sim-execution-status-independent-review.md`为PASS_SCOPED，6个文件首尾hash一致，3单测/6PG/API独立重跑。此证据替代上文“投影仍待验证”的当前状态，历史失败记录保留。

资产与收益和复盘与评估已接入新的正式只读分析。每页50、最多1000个可见canonical SIM实例；必须按用户创建归属或来源账户授权，不混入legacy Paper，不自动选择另一条记录。资产页只展示当前实例真实预算/资金/持仓与各事件时点；资金分录明确现金本金、现金手续费、对手及预算注资用途。复盘展示完整决策/订单/成交/风控计数，费用按原币种保留符号与数据覆盖，不从500条明细窗口推算总数；逐条决策可打开精确订单详情，冻结回测与发布有来源链接。发布新增精确深链接读取，失败不显示另一条记录。

正式API/PG复读`sim-analysis-formal-api-result.json`核对账户5：7决策、2订单/成交、10账务、费用0.19974482 USDT，现金/权益99.74643775 USDT、持仓0、父STOPPED；读取前后经济行数相同。API未mock，仅读取NQ正式数据；不是新的撮合或收益资格证明。

验证：`sim-analysis-pg-fourth.log`2项真实PG/API通过，覆盖501订单完整计数、分页与用户隔离、GET无写；`sim-analysis-independent-review.md`为PASS_SCOPED、3文件首尾hash一致、2PG/API独立重跑。`sim-analysis-ledger-build.log`Production build通过，`sim-analysis-ledger-unit.log`84单测通过；其中精确科学计数法直接移动小数点，0E-8显示0，不经浮点改写经济值。早期编译错误、测试环境键名错误、翻译namespace错误及只读脚本表名错误保留原日志，未减少断言或修改冻结迁移。

Codex IAB真实GUI验证1536×1024/390：资产/复盘切换保留实例；两个实例现金分别99.74643775和91.76030544，不混合；2笔成交费用与PG一致；决策→SELL详情FILLED/SUCCEEDED；发布来源精确对应；不存在实例和单接口连接阻断显示真实失败，未显示旧统计或示例。截图在`sim-analysis-ui`。复盘与冻结回测/发布来源后退已验证，窄屏导航无页面横向越界。网络阻断仅用于失败验证并已清除；未修改正式运行权限。

当前仍为PARTIAL。本次只完成单实例资产快照、完整计数、费用与决策复盘；权益/回撤历史、Sharpe/胜率等统一统计、legacy Paper分析分离、诊断/运维全动作和全站尺寸/双语矩阵仍未闭合。没有将正确缺失状态当作能力完成。参考资产页与正式截图已直接查看；业务适配保留密度/层次，但真实历史图尚缺来源，不能宣称整页视觉和功能验收。其余必做、全站E2E/Full Maven/安装升级/备份恢复/发行包/集中最终独立复核继续未完成。

当前隔离后端PID18672，JAR`runtime/nq-app-20261013-060721.jar`；正式dist仍为18128，API18280，PG16.15/V4。未提交、未Push、无发布。功能矩阵只追加/更新状态，原532项全部保留。


## 公开盘口与账户管理续接（2026-10-13）

NEXT-06最小公开盘口已实现并验证：JWT保护的 `/api/marketdata/depth`，仅OKX SPOT固定BTC/ETH/SOL与每侧20档。默认未启用；生产bean同时要求manual profile、公共外联和独立盘口开关。无凭据/签名/私有头、禁止重定向，固定公开资源、3秒连接及完整8秒响应deadline、64KiB上限、3标的缓存与非阻塞合并、5秒缓存/2秒失败冷却/15秒过期。返回源时点、接收时点、精确十进制与BigDecimal累计；失败保留有明确STALE标记的真实旧快照，不伪造零流动性。

原冻结盘口候选被独立审查判为REVISION_REQUIRED：PD-001证明JDK21响应头到达后正文可无限等待。保留 `public-depth-independent-review.md` 与body-stall红例；新候选以明确总deadline取消future和body订阅，12文件新hash补审 `public-depth-deadline-independent-review.md` 为PASS_SCOPED，25目标测试独立通过。原GET/POST兼容和取消前后订阅顺序已证明；不追认旧失败。

正式隔离API读取真实OKX20档，`public-depth-real-api-result.json` 核对精确累计/排序/时间、401和SWAP400；Order/Trade/Ledger在读取前后不变，GLOBAL_TRADING ENGAGED、LIVE0。此前直接供应方响应只作为来源观察，不替代此正式API证明。一次普通package产物未含启动manifest导致隔离启动失败，原日志保留；随后使用本项目spring-boot:repackage恢复，外部启动脚本增加可执行manifest/端口检查。

IAB实际1536×1024与390×844：真实BTC/ETH目录切换、买卖20档、轴币种/零起点累计/阶梯不平滑、精确明细、标签与透明Tooltip。已修正真实目录TRADING/LIVE状态未识别导致选项为空，以及轴标题裁切。刷新URL保持、非法衍生品拒绝、断连保留旧快照STALE、恢复真实读取、中英文已检查。固定真实旧快照在Network阻断期间完成悬浮进入/离开，截图 `final-tooltip-visible.png` 与 `final-tooltip-hidden.png` 具有同一sourceTime并证明隐藏；DOM样式读取存在缓存差异，未仅据该输出判断。所有网络阻断已清除，切换其他业务Tab暂停盘口轮询的实现已加入；更长时间资源/后台行为及全站矩阵仍待最终检查。

账户管理补独立只读详情深链接、别名优先列表、兼容身份保留在详情、启停状态双语、停用确认影响范围、写入期间按钮约束、别名64/引用128上限及纯空白校验。凭据抽屉区分加载/空/读取失败；失败不显示“未配置”且禁用保存，可显式重新读取；保存及关闭清除敏感表单，关闭reset后mutation gcTime=0。结构校验的边界明确，不宣称真实连接/权限成功。

正式GUI创建exchangeAccountId=4、legacyAccountId=6，alias为隔离GUI账户-1791846231667-已编辑；编辑、设默认、停用清默认、再启用及恢复原默认1均完成，API/PG三阶段复读及经济48订单/48成交/196账务不变见 `account-management-before/default/disabled/after.json` 和 `account-management-final-persisted.log`。详情4及不存在999999/非法身份、凭据空/单接口网络阻断/恢复、小屏及英文详情已验证，截图 `account-management-ui`。没有输入或读取真实交易所凭据，没有私有连接、LIVE或真实交易。完整凭据保存/轮换验证及稳定写命令身份/UNKNOWN结果查询仍未闭合，账户整页保持pending。

前端构建 `depth-account-qa-build.log` 通过；`account-management-final-unit.log` 84通过，最后新增文案由 `depth-account-i18n-final.log` 3项再次核对。先前一轮4处缺失detail翻译失败已修复，失败日志保留。主chunk约3.28MB警告尚待性能处理。后端完整reactor `full-maven-isolated-confirmed.log` 为2292 tests/0 failures/0 errors/214 conditional skips，470唯一测试类，仅覆盖盘口之前候选；临时隔离回归库独立于验收库，未repair默认库或改V1–V4迁移，跳过项不计新资格。最新盘口候选目标25测试通过，但不冒充已重新完成最终Full Maven。

当前整体PARTIAL。账户命令未知恢复、完整凭据链路、统计历史、持续SIM、调度/Shadow全动作、新闻、费用、四价实际时点、通知、更新、高清登录、全站E2E及最终发行/安装资格仍在实施；原有未完成行全部保留。当前后端PID58228，runtime/nq-app-20261013-065037.jar，prod/public-marketdata-manual、Strategy SIM及PublicDepth启用，Continuous SIM关闭；18128/18280/15442，PG16.15与V4，GLOBAL_TRADING ENGAGED。原工作区、原型、Demo/C4保留。没有Git提交或发布。
## 公开新闻与资源趋势续接（2026-10-13）

NEXT-08正式新闻已完成最小只读范围：JWT保护的`GET /api/news`，固定CoinDesk官方公开RSS，最多读取100项/返回20项；五分钟缓存、三十秒失败冷却，失败保留标记STALE的真实旧快照。来源需要manual公共外联及独立新闻开关，默认关闭；禁止重定向、完整八秒响应deadline、256KiB/XML深度上限、禁DTD/外部实体。只展示原文标题、来源、发布时间和链接，无文章正文或交易写入。

原候选独立审查发现长标题正则处理可阻塞CPU及编码路径改写问题，已保留红例并修复；`public-news-fixed-independent-review.md`独立12测试通过。真实联调发现尾斜杠地址308，仅改为观察到的同域HTTPS正式端点，不放宽重定向；`public-news-canonical-independent-review.md`为PASS_SCOPED，11文件hash一致、独立12测试通过。`public-news-real-api-result.json`在正式API取得20篇真实标题，核对日期/排序/去重/链接/401及PG交易事实不变；此前失败和仅供应方诊断未追认为正式通过。

IAB总览验证五条分页、真实发布时间/来源、断连保留旧标题与明确失败状态、恢复、390窄屏/英文，截图在`public-news-ui`。直接视觉检查发现总览网格把运行摘要放在独立列，造成新闻宽度过窄及大片空白，已将运行摘要和活动区域铺满行，保留事件与新闻并列。当前截图`desktop-layout-corrected.png`已查看，仍不代表整个总览的最终功能和视觉验收。

NEXT-07最小真实资源趋势已实现：`nq-observability`持有`GET /api/runtime/resources`，按15秒固定延迟采样，当前进程内最多120点；CPU用累计进程CPU时间差/单调时钟间隔/可用处理器数归一化，首次、不可用、计数回退和配额变化留空。堆/非堆内存、线程及启动以来累计GC来自JVM MXBeans，不读环境变量、启动参数、线程名称/堆栈，不查数据库或供应方。GET不增加采样。宿主、容器、数据库、磁盘及健康百分比未纳入此最小范围。

资源页两个有界ECharts时间图使用自动时区、秒级轴、零起点CPU/内存、明确单位/系列及断点；采样明细提供精确字节和键盘/触摸访问。URL页签保留刷新/前后退，非资源页签停止轮询；断连保留过期标记的旧采样。后端13模块测试、前端88单测及Production build通过，见`jvm-resources-module-test.log`、`resource-news-unit.log`、`resource-news-confirmed-build.log`。主chunk约3.29MB警告仍在。

正式API`jvm-resources-real-api-result.json`、跨进程重启`jvm-resources-after-restart-result.json`证明新采样身份和窗口、401保护、读取前后48订单/48成交/196账务不变、GLOBAL_TRADING ENGAGED、LIVE0。未支持POST首次被既有通用异常处理误报500，保留`jvm-resources-real-api.log`；已增加405/Allow映射，2目标测试及正式接口重验通过，没有放宽断言。IAB验证1536与390、双语、精确字节、键盘展开、断连/恢复、URL刷新、后台页签零资源请求、重启新窗口；`jvm-resources-ui/tooltip-final-visible.png`与`tooltip-final-hidden.png`同一19点窗口确认临时提示离开关闭。临时网络阻断、视口和语言已恢复。

功能账本556行/556唯一ID，旧553项全保留。NEXT-07/NEXT-08仅按以上最小范围关闭，原路由及嵌套未完成动作不删除。整站、完整凭据/UNKNOWN恢复、统计历史、持续SIM/调度/Shadow、费用/四价时点、通知/更新、高清登录、全站E2E、最终Full Maven及本地发行包资格继续为PARTIAL/PENDING。当前后端PID32712，`runtime/nq-app-20261013-081845.jar`，18128/18280/15442与PG16.15/V4；Strategy SIM/公共盘口/新闻开启，Continuous SIM关闭。无Git提交或发布。

## 账户元数据稳定命令与结果恢复（2026-10-13）

新增正式 `POST/GET /api/exchange-accounts/commands/{UUID}`；CREATE/UPDATE/ENABLE/DISABLE/SET_DEFAULT/RESOLVE_SIM委托原账户服务。owner+UUID事务锁、完整输入指纹、业务变化与不可变成功回执同事务；成功重放返回历史摘要而不重施。V5仅增量新增约束/回执表，V1–V4未改。GET404表示尚无成功回执，不能证明请求失败；恢复沿用原身份和输入。

`account-command-independent-review.md`为PASS_SCOPED，23文件首尾hash一致，独立18项后端（10真实PG）与5项前端恢复测试通过。实现者前端93项及typecheck通过，`account-command-reviewed-build.log`生产构建通过；未冒称最新Full Maven或全站验收。

现有隔离验收库由V4升级V5，不清库。`account-command-real-api-result.json`核对创建/编辑/默认/停用清默认/启用、同身份重放、身份改绑409、401/非法UUID/404；恢复原默认1。真实进程重启后7个成功命令逐项重新查询完全一致，见`account-command-after-restart-result.json`。

IAB真实GUI使用CDP仅故障注入、不返回模拟成功：响应阶段拦截真实200后中止，PG只有1回执/1新账户6；页面保留操作c305be19-d069-4638-9d80-c5db84ea7ca4，禁重复写入，刷新及后端重启后原查询打开真实详情6。请求发送前中止另一次创建，GET404明确不等于失败，用户确认后同操作0d560c20-b555-42cb-9e10-1527d57f6a06成功创建账户7，PG同样只有1回执/1账户。中英文、390窄屏、确认弹层和恢复结果截图在`account-command-ui`。网络拦截、视口及中文恢复已清理，原请求/响应阶段区分保留在JSON证据。

`account-command-gui-persisted.log`最终复读48订单/48成交/196账务不变，GLOBAL_TRADING ENGAGED、LIVE0、默认账户1。凭据未读取/写入，交易操作未执行。sessionStorage仅保留非凭据元数据身份，支持同浏览器会话刷新；跨浏览器/session丢失找回未纳入此范围。凭据保存/轮换UNKNOWN仍保留缺口，ACCOUNT-WRITE-UNKNOWN-RECOVERY不整项关闭。

账本560行/560唯一ID，原556全保留。整体仍PARTIAL，继续其余全站和发行资格工作。当前后端PID25776、runtime/nq-app-20261013-090358.jar，18128/18280/15442，PG16.15/V5；Strategy SIM/公共盘口/新闻启用，Continuous SIM关闭。未Git提交或发布。

## 设置页账户恢复与业务页面按需加载（2026-10-13）

默认账户设置复用新稳定命令后，设置页也增加AccountCommandRecovery；待确认期间暂停默认账户重复提交，成功原查询后读取当前账户/用户事实并清除原错误。IAB真实200响应中止59b7cd7d-8655-4967-80cb-68a1068548f2，恢复面板原查询确认账户2，再显式恢复原默认1。`settings-recovery-persisted.log`证明默认1、48/48/196、ENGAGED、LIVE0保持；截图/身份在`account-command-ui`，不是模拟成功。

24个业务页面改为React lazy，ConsoleLayout在内容区保持统一加载反馈，路由加载异常走既有双语异常页；登录/认证异常入口保持即时。Production build `settings-recovery-route-build.log`通过，实际入口index-COBHVnvH.js为475819 bytes（此前单入口约3.29MB）。最大共享client chunk仍约585KB，不能把入口缩小说成全部下载或全站性能已通过。

IAB实际请求证明登录未预加载MarketdataPage或BacktestDetailPage，设置只加载SettingsPage；受控阻断MarketdataPage脚本时显示中文系统异常、无堆栈，清除阻断后reload恢复真实行情页。证据`route-splitting-ui`，未改变旧路径和业务对象身份。包加载检查不替代全部旧路由/动态详情验收。

对当前源码的12个时间输入核对，行情查询、采集任务、公共采集范围、数据集、账户表现与回测均已有时分秒配置和endAfterStartRule。IAB行情开始2026-10-14 09:00:01晚于结束2026-10-13 09:00:00时显示结束必须晚于开始，并且提交产生0个bars请求；原区间已恢复。时分秒弹层截图尝试被当前通知候选未启用所致的全局错误提示遮挡，保留为`market-time-picker-attempt-obscured.png`，不作为弹层通过证明；全部页面时间控件实测矩阵仍待完成。

独立通知审查NTF-001发现合法用户名system可误取得系统actor创建记录所有权。原15文件候选与REVISION_REQUIRED报告保留；已修复本任务新增通知、SIM分析、精确订单及搜索四处同根条件，禁止system哨兵参与用户名授权而保留来源账户授权。新增真实PG回归先红；第一次修后尝试不完整冻结研究夹具的full analysis读取而409，保留失败；改用真实catalog自身身份与隐藏详情404、自己订单200及越权refresh404后，12项受影响PG测试通过。新18文件冻结正在定向补审，未部署到验收后端，不追认旧候选通过。

账本563项、原560全保留，整体PARTIAL。L01已重新核对无固定4K像素门槛；两次1672×941衍生资源尚无实际高清显示通过证明，继续开放，不将工具尺寸限制当作唯一业务阻碍。

## 正式通知与所有权哨兵修复（2026-10-13）

新增owner保护GET /api/user/notifications，单次有界SQL读取已有SIM告警与恢复事件，区分独立SIM/历史Paper；每页50、最近最多1000、5秒查询/事务超时，不读取message/reason/request_json或凭据。按最新业务变更时间和稳定种类ID排序，无逐实例轮询。全局抽屉和最近50条未读徽章接入现有UserPreferences.notificationsReadAt，保留亚毫秒比较精度；标为已读只更新个人偏好，不处理业务告警。页码与范围明确，来源深链接保留准确paperRunId。其余Shadow/系统等事件通知归并仍待实施，NEXT-17保持partial。

原15文件候选审查为REVISION_REQUIRED：NTF-001证明真实用户名system、零账户VIEWER可误取他人系统创建运行的通知。原报告/探针/失败均保留。新候选排除system actor哨兵参与用户名授权，同名真人通过实际来源账户仍可见自身记录；本任务新增的通知、SIM分析、对象搜索及精确订单读取四处同步修复，执行刷新也通过修正后的订单可见性检查。新18文件定向补审notifications-fixed-independent-review.md为PASS_SCOPED，12PG/API独立重跑，原探针复测system越权0、真实owner1、无关viewer0，首尾hash一致；NTF-001只在新候选关闭，不追认原候选或全仓安全通过。

正式隔离API见notifications-real-api-result.json：401/非法页码400、当前owner范围、排序/字段和精确来源。2条明确标为隔离验收的告警经原正式创建API写入，未模拟服务成功；第一条已读后，第二条新事件恢复未读。IAB验证200偏好提交响应丢失，界面保留未读和UNKNOWN并禁重新提交；重新GET正式偏好后才移除未读。正常保存第二条水位、刷新、后端真实重启后均保留，notifications-read-after-restart-result.json复读两告警仍OPEN、ack/resolved为空，48订单/48成交/196账务、GLOBAL_TRADING ENGAGED、LIVE0、默认账户1与V5保持。

IAB截图notifications-ui覆盖1536/390、双语、来源运行、未读/未知/确认、读取失败保留旧列表及恢复。读取失败采用明确500前端负向夹具（2个受控错误响应），只证明错误呈现，不冒充后端业务证据；原误在刷新loading帧继续点击的测试尝试保留，没有据此宣称多轮maxCount实测。AppProviders增加同路径/方法/状态错误key更新和maxCount=3，避免此前未部署通知API造成重复提示积累；原真实错误和受控失败信息均仍在页面可见。通知来源运行Segmented曾让390页面宽度变509，现限制在导航区域内滚动，实际document宽度390。动画前截图保留为motion-frame，最终mobile-english-confirmed才是抽屉排版证明。拦截、临时视口/DPR和中文已恢复。

前端notification-source-mobile-build.log通过，notifications-final-unit.log为96项通过。最新Full Maven、全站E2E和最终集中审查仍未执行。新版本未做Git提交/发布。

L01接入已有image_gen第一次细节衍生素材login-earth-kline-hd.png，保留原批准资源，1536正式显示已查看，星空/地球/山脉方向保持。原生1672×941不是4K。2560 CSS/DPR2布局元数据已观察，但首次截图只取得1280×720JPEG裁切；完整截图捕获失败/超时。失败保留在login-detail-ui，不能将布局元数据、文件名或1536普通截图当作完整高DPR清晰度通过；L01继续pending。U01文字位置仍待用户补充。

账本570项、原563全部保留。整体继续PARTIAL；账户凭据完整链/UNKNOWN、实际费用与冻结、统计与历史、持续SIM/调度/Shadow/高级诊断、版本与更新、其余通知、全站尺寸/双语/异常矩阵、最终Full Maven/发行双导出/安装升级/备份恢复/本地包资格仍需完成。当前后端PID63164、runtime/nq-app-20261013-103246.jar，18128/18280/15442，PG16.15/V5；Strategy SIM/公共盘口/新闻启用，Continuous SIM关闭。无真实凭据、LIVE、真实交易或Git发布操作。

## U01 用户决定与追踪纠正（2026-10-13）

用户明确 U01=UNRESOLVED_SOURCE_ANNOTATION / NON_BLOCKING，最终状态=PENDING_USER_CLARIFICATION。原始无文字红框不能唯一确认页面、字段、按钮或预期行为。账本保留原U01身份和分母，撤回此前将image15与标的目录控件关联、声称“原红框语义已适配正式目录”的推断；已有目录功能验收只证明对应明确需求，不证明U01已修复。不得据此新增或修改功能。

G01—G10、D01及其他明确需求继续实施，不等待U01。相关页面继续正常功能和交互验收；后续若有页面名称、字段/按钮及预期行为的明确证据再补充。U01不影响其他已确认需求的验收，亦不虚报关闭。

## 凭据版本、稳定操作与窄屏时间控件（2026-10-13）

账户凭据管理已接入正式有界版本历史与脱敏详情，支持CREATE/ROTATE/VERIFY/DISABLE/ENABLE/EXPIRE/REVOKE。新POST/GET `credentials/operations/{UUID}`委托原正式服务，以owner/account/UUID事务锁和账户行锁串行；完整输入采用masterKey分域HMAC，版本变化、审计和不可变成功回执同事务。指定版本VERIFY不因并发轮换改验另一个版本；成功重放只返回历史回执。V6增量新增回执、关联约束和有界索引，V1—V5未改；下一迁移必须V7。

原32文件独立审查为REVISION_REQUIRED，CO-001证明未配对UTF-16代理字符在UTF-8编码时被替换，可能使不同材料误重放同一回执。保留原PG反例及失败候选；现于指纹计算前拒绝非法代理字符，合法中文/重音/emoji/U+FFFD保持。`credential-operation-fixed-independent-review.md`为PASS_SCOPED，CO-001仅在新候选关闭，独立51项后端测试含13凭据PG与7账户PG通过，原反例拒绝及正常Unicode往返由独立探针确认。

补审后普通UI修改涉及CredentialWorkspace及两语言pages.json：UNKNOWN期间确认guard/disabled、加载空态、成功后不写已卸载表单、VERIFY恢复确认原对象文案。`credential-post-review-ui-deltas.json`保存3个变更文件的前后hash，后端/迁移29文件仍匹配；不宣称32文件全部未变。普通UI自查后102前端单测和Production build通过，见`credential-target-copy-unit.log`/`credential-target-copy-build.log`。

真实隔离API合成账户8完成材料创建、轮换和全部生命周期、同身份重放、不同输入409、撤销后启用409及401/404/非法UUID负例。真实进程重启后原8回执逐一复读一致，见`credential-operation-real-api-result.json`与`credential-operation-after-restart-result.json`。业务库V5升级V6前备份和旧dist已保留，不能据此宣称完整备份恢复资格通过。

IAB真实VERIFY响应200被持有，客户端超时后保留UUID `9974bf00-6a71-418d-846e-4fd4f69d2280`；刷新、后端重启和正常重登后GET原成功回执解除暂停。准确证据类型是REAL_COMMITTED_RESPONSE_HELD_CLIENT_TIMEOUT，没有执行手动响应中止。第二UUID `e1503bda-9a17-4696-a426-de054a046890`在XHR发送前中止；正式GET404仍显示未知，确认原对象后同UUID恢复成功。两UUID各一个持久回执，UNKNOWN期间行操作及提交禁用，390确认弹层374×199且frontmost=true。材料保存/轮换完整GUI输入链和真实权限/连接探活未执行，不整页报验收完成。

时间控件此前390宽的日期/时间并列撑宽到474，第一次上下布局又导致顶部越界，两个失败均保留。workspace.css仅在575以下将日期/时间上下排、弹层置于视口中央并限定高度滚动，时间列132px。`time-controls-ui/time-picker-390-matrix.json`实测六组12控件完整在390×844视口内，时/分/秒24/60/60选项，回测另有10项毫秒；实际采集结束选定2026-10-13 10:11:12。尚未完成六组全部先后/相等GUI负例矩阵，既有源校验及单测不冒充该完整实测。Escape/闭合等待超时、桌面截图裁切及中间记录问题均保留，不算键盘或完整桌面通过。

唯一账本576行/576唯一ID，原570项全部保留；原账户动作的过期NOT_STARTED和凭据恢复缺口已按证据纠正，U01继续PENDING_USER_CLARIFICATION / NON_BLOCKING。当前48订单/48成交/196账务、LIVE0、GLOBAL_TRADING ENGAGED、默认账户1；合成账户8有3版本/10成功回执/1ACTIVE。后端PID20680、`runtime/nq-app-20261013-122253.jar`，前端preview PID41184，18128/18280/15442，PG16.15/V6。整体仍PARTIAL；费用与冻结、统计/历史、持续SIM/调度/Shadow、高级诊断、其余通知/更新、高清实际显示、全站E2E、最终Full Maven和发行资格仍待完成。无真实凭据、私有查询、LIVE交易或Git提交/发布。

## 全站时间交互与实际高清显示续接（2026-10-13）

凭据历史补充390中文/英文与1536英文真实页面检查：3版本的ACTIVE/ROTATED/REVOKED及结构/权限状态分别显示，详情不含材料，菜单仅列对应生命周期动作。中文/英文截图在`credential-operation-ui/history-*`。首次菜单截图为旧帧，桌面CDP首次截图因clip未加页面滚动偏移而错位，旧图保留；`history-en-desktop-viewport.png`按实际pageY=255重新捕获并查看。一次Escape已同时关闭抽屉，随后关闭按钮不存在的超时保留，不能据此声称完整键盘交互通过。当前3版本没有真实GUI翻到第二页，游标边界由原PG证明覆盖。

六组时间范围完成逆序、相等和合法秒级输入检查，回测另验1毫秒，原始26行记录在`time-controls-ui/time-range-gui-evidence.json`。采集/数据集补齐正式OKX/BTC-USDT对象后重测，防止其他必填缺失掩盖范围检查。负例明确显示“结束时间必须晚于开始时间”，显式提交观察窗口没有目标请求。最初仅Tab失焦的3个行情记录仍见旧错误，不作为正例；后续Enter确认和等待错误消失才形成有效正例。公开捕获的秒级正例只证明表单范围合法，供应商整小时、已收盘及最大500根合同继续由正式后端执行，不冒称捕获成功。

发现真实交互缺陷：回测日期框按Enter确认时触发浏览器隐式表单提交，在隔离验收库创建了`bcf-4c6a6e31-eedc-489a-a501-5fb3cfee4ecb`，名称“时间范围验收 · 不提交”，起止为`.123Z`/`.124Z`。保留该红例，不删除或启动它；正式PG复读证明策略/数据集未绑定、运行0、订单/成交/账务48/48/196。第一份SQL使用不存在的start_time字段而回滚，正确字段从真实schema核对后读取config_json，失败日志保留。

现以`confirmTimeWithoutSubmittingForm`阻止Enter默认表单行为，rc-picker仍确认日期；12个DatePicker统一接入。完整回测输入的逆序/相等拒绝、1毫秒合法确认期间零请求且创建表单保持打开，`time-enter-gui-evidence.json`覆盖六组输入。前四组和账户收益复验当前构建；公开捕获另按实际`/marketdata/public-captures`重测，错误过滤路径的早期记录不作准确端点证明。回测复用同handler的完整正负例证据，后续只有显示精度和绑定选择框CSS变化。输入期间与显式提交窗口分开记录；修复前其他表单早期记录不证明Enter期间全程零请求。

回测列表和两处详情使用`formatDateTimeMilliseconds`，自动浏览器时区及当前语言保留，避免同秒不同Instant显示成相等。真实配置详情已显示10:11:12.123/10:11:12.124。390实测原绑定选择器越过抽屉并造成内部横向滚动，现仅在575以下将两绑定表单标签/字段上下排列，选择框宽306、右边343，抽屉body client/scrollWidth同为380。`backtest-precision-binding-fixed-390.jpg`已查看，无选择器越界；前图保留。

`time-enter-unit-confirmed.log`102单测通过；增加两项实际时间语义回归后`time-precision-unit.log`104项通过，Production build和`git diff --check`通过，最终CSS构建见`backtest-binding-mobile-build.log`。第一次npm在仓库根执行未找到package.json，失败保留，后在frontend正确入口执行。当前IAB页面身份为18128正式构建、无白屏/框架overlay，最近50条warn/error为空；视口及DPR已清理为默认，后端/迁移未修改，凭据定向独立审查未被重复扩成全站审查。临时测试函数曾因闭包保留旧失焦动作而点击抽屉外关闭表单，原失败保留；重新在抽屉内完成确认后复验，不拼接为一次通过。

L01此前完整高DPI截图缺口已补：实际2560×1440 CSS/DPR2取得5120×2880 PNG及1024×768原生细节截图，已与原批准资源和当前衍生资源一起查看。星空/地球/装饰K线/山脉及渐隐方向保留，UI文字独立渲染；1536原代表证据及390当前显示复用/补充。素材仍为1672×941，截图像素不是素材原生分辨率，DPR2背景受原像素限制；未宣称4K母版。当前scope的显示验证关闭，不等于用户最终全站视觉验收。截图全图的工具显示有缩放，原生细节另行查看，不用布局元数据冒充视觉证据。登录过期真实提示已观察，既有隔离账号正常重登并按现有流程展示声明，无改密或凭据更改。

账本579行/579唯一ID，原576全保留。账户动作按原路由核对发现ACTION-0016实际属于/trading，已撤回误套账户证据并恢复该项待验；账户其余动作更新到实际稳定命令API。U01保持PENDING_USER_CLARIFICATION / NON_BLOCKING，NEXT-14含U01而不整项报关闭。最终PG `time-enter-final-persisted.log`：旧红例配置1、新候选额外配置0、旧红例运行0、48订单/48成交/196账务、LIVE0、schema V6。无Git提交或发布。

整体仍PARTIAL。实际费率读取/同步及回测/SIM冻结本次仅调查既有OKX私有费率事实与授权护栏，没有新增FeeService/V7或完成费用能力；统计/历史、持续SIM/调度/Shadow、高级诊断、其余通知/更新、全站E2E、最终Full Maven、双导出及安装升级/备份恢复/本地包资格仍待完成。真实凭据和私有联调未授权，不以合成凭据或本次普通UI通过替代它们。


## 中文注释、短类名及账户费率观察候选

按用户要求，正式新增和修改Java正文采用显式import与短类名；package/import和必要配置字符串仍保留真实身份。已在63个现有本轮文件插入135处中文Javadoc/说明，覆盖公共端口、身份重放、404未知结果、凭据HMAC、账户估值、费率正负号、事务/锁顺序、分页、缓存、SIM统计和测试边界；注释说明原因、不变量与失败模式，不添加作者/日期样板。`source-comment-completion.json`逐文件记录纯注释插入及前后hash。后续新增费用API、组件和测试同样带业务边界注释。

`java-source-style-audit.json`检查126个本轮新增/修改Java文件：正文全限定类型0、正式代码通配符import0、正式文件无Javadoc0；这是有界风格检查，不证明每条注释充分或业务正确。原独立审查冻结输入与证据保留；短名/import及注释使文件字节身份变化，最新`source-style-review-deltas.json`重新列出实际差异，不能继续宣称旧32文件或全部后端仍逐字节匹配。纯注释和类型名调整由自查/编译覆盖，新增费用能力不套用旧凭据或通知审查。

账户当前费率观察新增AccountFeeService、正式history/result/sync API、受原安全profile与显式owner/账户/凭据/标的配置限制的OKX只读来源、不可变V7以及账户费率抽屉。保存供应方原符号与NQ成本符号、精确12位费率、来源/观察时点、五分钟过期身份和摘要；负成本为返佣，不能取绝对值。当前观察不是历史费率或成交已发生费用。同稳定UUID重放原回执不再访问供应方；来源关闭仍可读旧记录。前端按正式标的目录选择，保留未知身份、404待确认、原目标恢复、来源未启用/过期/空历史/失败状态；会话只存非敏感身份。

最初费用PG测试夹具把actor字符串误传给keyVersion导致编译失败，修正为既有int版本，保留`account-fees-pg-first.log`。`account-fees-pg-fixture-corrected.log`21项通过、0失败/错误/跳过，含3数值、9来源护栏、9真实隔离PG/API/事务/约束及V6→V7升级测试；FixtureSource没有解密或连接真实账户，不能证明实际私有来源。`source-comments-final-test-compile.log`reactor编译通过。前端`account-fees-ui-final-unit.log`114项通过，包含10项费用恢复/精度边界；Production构建写到外部`account-fees-ui-candidate-dist`，没有替换18128当前dist。仍有603.61kB等chunk体积警告；没有性能最终验收。

翻译helper首次使用Windows默认CRLF引入换行差异，失败保存`source-style-diff-check-before-newline-fix.log`；修正为既有LF后`source-style-diff-check-confirmed.log`通过，翻译值未变。费用24文件候选已冻结到`review-account-fee-input.json`，正在进行一次定向真正独立审查，未取得审查结论前不报接受或部署。

费用仍PARTIAL：回测/SIM费用来源冻结及消费尚未实现，EXPERIMENT_ASSUMPTION与正费率限制仍在；收费/返佣/零费率、现金预留及恢复须补完整证明。BINANCE实际费率端口未补，已作为NQ需要的能力明确登记。当前验收JAR仍旧候选、验收库V6，V7仅在临时测试库执行；未执行费用桌面/390/双语真实GUI、真实凭据读取、私有联调或交易。未修改V1—V6，未做Git提交/发布。账本新增5项，584行/584唯一ID，原579全部保留；U01仍PENDING_USER_CLARIFICATION/NON_BLOCKING。其他全站、最终Full Maven/发行安装升级/备份恢复资格继续未完成，整体保持PARTIAL。


## 费率百分比显示定向修复与独立补审

原24文件候选`account-fees-independent-review.md`为REQUIRES_CHANGES：AF-001指出列表和主要详情共用8位百分比显示，把合法正/负1e-12费率均显示成0%。原冻结、hash和`account-fees-independent-display-probe.log`红例保留，不追认为成功。独立21后端及13前端目标通过，另在真实隔离PG验证另一事务已提交的凭据变化导致同步拒绝且无回执、外部变化仍持久；原身份重放不再访源、重开读取、零/12位返佣、disabled历史读取及临时库清理通过。验收库仍V6，无经济行写入或私有查询。

新`formatAccountFeePercent`采用10位百分比精度，保留12位原费率：正/负最小非零分别为+0.0000000001%/-0.0000000001%，真实0或0E-12为0%。列表与主要详情都委托同一函数；正文中文注释解释换算原因及零值边界。只改一个旧组件并新增格式化函数/测试，原其余23文件包括后端/SQL逐字节不变。

实现者`account-fees-ui-display-fixed-unit.log`117单测通过，含3项显示边界，`account-fees-ui-display-fixed-build.log`在外部独立dist构建通过。新26文件冻结为`review-account-fee-display-fixed-input.json`；补审`account-fees-display-fixed-independent-review.md`为PASS_SCOPED，AF-001在新候选关闭，16项目标测试及11个真实源码显示断言通过，26文件首尾hash一致。未重复后台PG或扩大全站审查，复用相同后台/SQL候选的有效证据。前端117覆盖不是最新Full Maven或真实业务GUI证明。

账本仍584行/584唯一ID，所有旧行保留，三项费用观察能力更新为局部验证通过、GUI/私有来源未覆盖。`account-fee-consumption-next-scope.md`记录运行冻结与资金预留的真实源码边界；`fee-sizing-consumption-gap-probe.log`调用当前编译领域对象确认负费率仍被拒绝，结果CONFIRMED_UNIMPLEMENTED，不是费用消费PASS。回测/SIM冻结、返佣/零费率全链经济证明、BINANCE来源和全站其他工作继续未完成。未部署新候选、未把V7应用到验收库、未做Git提交/发布或读取真实凭据，整体仍PARTIAL。


## 费用观察V7实际验收与费用符号消费授权续接

原费用观察26文件审查候选已完成Boot重打包并装入隔离验收，V7真实应用；后端PID59288、runtime/nq-app-20261013-175405.jar，前端PID41184、18128/18280/15442，私有来源DISABLED。安装前JAR/dist及hash留在account-fee-runtime-install.json。普通package第一次未生成Boot可执行JAR，后续显式repackage完成；原运行helper观测超时没有作为重启理由。正式8自有账户history与原结果读取10次，未同步或私有读取。

实际IAB tab4完成1536/390及中英文空历史、当前观察口径、来源关闭、目录BTC/ETH选择及BINANCE缺口表达。390抽屉最终宽390、页面不横向溢出，表格自身允许横向滚动。对account8历史XHR的有界网络中断只证明错误呈现，页面明确读取失败不能判断空历史；恢复正式HTTP200后清除拦截。过渡帧截图保留，不将动画瞬态宽度当作最终CSS缺陷。当前控制台warn/error记录为空。

会话过期后以既有隔离资料正常重登，pending测试身份仍保留。明确的本地夹具UUID9986e2ef-5e69-4da7-b9db-e2a904af139c锁定原账户8/凭据3/BTC；正式GET404不判失败，显式恢复弹层374宽且frontmost=true，同UUID正式POST409后身份保持UNKNOWN。没有私有读取、同步成功或观察持久化。证据类型LOCAL_PENDING_IDENTITY_FIXTURE_PLUS_REAL_API_REJECTION；只按UUID匹配清除此条夹具，语言、视口与网络恢复。升级及GUI后正式经济计数仍orders48/trades48/ledger196、LIVE0、非终态0、GLOBAL ENGAGED、凭据回执10/版本3。实际私有费率正值历史/详情GUI仍未验证。

用户已回复“授权完整费用符号消费”，覆盖signed-fee-consumption-decision.md中的代码、预留、双边记账、完整来源冻结和隔离验证/独立审查，不扩大到真实凭据、私有联网、LIVE、交易或发布。新增SpotFeeReserve统一本金加正费用的八位上取整预留；Sizer与在途返佣不提供未到账购买力，结果仍保留正/零/负费用。Backtest原公开规则SHA/可见性检查保留，显式费用模型允许零及幅度小于1的返佣，研究预留仍使用十八位精度。

TradeLedgerPostingService沿用FEE_1/FEE_2稳定身份与原币种：收费扣款、返佣反向成对入账，零费用不造额外分录。base费用与仓位使用同一八位舍入，首次买入均价按扣费/返佣后的实际数量分摊本金。原事务、币种/仓位锁、环境断言、残缺事实拒绝、事件和审计边界保留。V8仅扩展策略SIM决策费率CHECK及中文列说明，限时DDL；V1—V7未修改，当前验收运行仍V7，V8只在新临时测试库执行。

原signed-fee-core-ledger-first.log保留错误夹具中ALREADY_AT_TARGET与INSUFFICIENT_CASH预期冲突；修正夹具保留严格现金断言。signed-fee-pg-first.log保留错误EventStoreAppender import，signed-fee-pg-fixture-corrected.log保留对JDK事务代理按实现类查找失败；修正测试通过正式端口取bean，没有修改产品代理策略。signed-fee-economic-confirmed.log最终27项通过、0失败/错误/跳过：7成本/数量、6账务单测、8既有回测、6隔离PG；PG涵盖正/零/负及base/quote买卖、精度、币种守恒、原身份幂等、真实两writer等待锁、提交失败整体回滚、两新JVM重放原已提交结果、V7→V8升级/validate与CHECK正负例。重放的UNKNOWN是已提交结果夹具，不是实际交易所UNKNOWN事件。

signed-fee-formal-sim-first.log另1项通过：合成已闭盘数据由正式目录/版本/研究/回测/评估/发布进入原Risk/Execution、canonical SIM、Trade/Ledger，正/零/负三条费用路径均有真实PG经济复读及ALLOW风险事实；在途不重复买入，重复撮合不增成交，GLOBAL始终ENGAGED、LIVE0。供应方和真实账户材料没有参与。12文件冻结在review-signed-fee-consumption-input.json，正在一次定向独立审查，尚不报告接受或安装新候选。

新增及修改代码持续补简体中文业务注释、显式import和短类名；当前java-source-style-audit.json覆盖137文件，正文全限定类型0、正式通配符import0、无Javadoc0。该扫描不代表所有方法注释都充分或业务正确。费用授权不是整个费用目标完成：实际账户观察选择、后端owner/账户/环境/标的/新鲜度解析及完整来源冻结仍需补齐；BINANCE来源、全站其他项目、Full Maven、全站E2E及发行安装升级/备份恢复资格继续未完成。唯一账本586项、旧584全部保留，U01持续PENDING_USER_CLARIFICATION/NON_BLOCKING，整体PARTIAL；没有Git提交/发布。


## 本金预留精度反例修复与中文恢复注释补齐

原12文件费用符号消费候选独立审查signed-fee-independent-review.md为REVISION_REQUIRED：P2 SFC-001。两笔合法八位price/qty生成本金499.999999995与500.000000005，原本金按高精度预留合计1000，canonical AMOUNT逐单八位HALF_UP实际扣1000.00000001；rate=-1e-12的费用都落库为零。独立真实PG现金及USDT快照均为-0.00000001、币种仍双边平衡。原报告、冻结hash及数值/PG反例保留，不能用28项原测试通过追认为资金边界通过；原候选未装入验收运行。

候选释放后新增原输入回归，signed-fee-sfc001-regression-red.log先红。SpotFeeReserve按每单正式AMOUNT八位HALF_UP归一化本金，正费用仍对原乘积CEILING八位，返佣仍不预支；Sizer与在途共用合同，不先合计本金再舍入。新增中文注释明确舍入原因和资金边界。signed-fee-sfc001-fixed.log30项目标通过、0失败/错误/跳过。随后仅加强PG回归夹具为正式fund2000→BUY1@1000，持久化仓位1/现金1000，按原在途0.5及第二价格再计算；拒绝第二单，原单完成后的现金/最新快照500、费用0、两成交。signed-fee-sfc001-exact-state-confirmed.log12目标通过，其余相同产品/未变测试证据复用。

新review-signed-fee-consumption-fixed-input.json仍12文件，其中只SpotFeeReserve及两份回归测试变化，其余9、V8和V1—V7不变。signed-fee-fixed-independent-review.md为PASS_SCOPED，SFC-001=CLOSED_ON_NEW_CANDIDATE：独立12目标、72个有界符号/舍入组合通过，rate0与-1e-12的原PG边界均拒第二单，现金/快照500、0费用分录/不平衡/LIVE。12+7首尾hash及HEAD匹配，临时库清理，验收库仍V7。补审只检查影响部分，不扩大全仓或覆盖原红例。

按用户的注释要求，在费用API和抽屉再补6处简体中文说明，解释提交前保存身份、请求前后会话核对、UUID匹配清理、恢复拒绝仍未知以及404不判失败。fee-recovery-comment-completion.json和fee-recovery-comment-delta-proof.log证明移除这六条注释即可逐字节恢复原源码。费用观察旧26文件中24仍相同、2只加注释，见fee-comment-review-identity-deltas.json；运行bundle未变，不把当前2文件字节身份伪称旧审查完全匹配。Java本轮137文件风格检查仍正文全限定类型0、正式通配符import0、无Javadoc0；这不代替注释质量和业务证明。

账本586项/586唯一ID、全部旧项保留，整体仍PARTIAL；U01仍PENDING_USER_CLARIFICATION/NON_BLOCKING。费用观察来源的选取、后端owner/账户/环境/标的/新鲜度解析、完整来源冻结、实际provider费用符号转换（现有MinimalLivePilotConfiguration仍有abs消费路径）、BINANCE来源及全站其他剩余工作继续未完成。当前18280运行JAR/18128 bundle/V7库未因signed候选或注释修改而替换；无真实凭据、私有联网、LIVE、交易、Git提交或发布。


## 当前账户费用来源冻结与权限闭合（2026-10-13）

当前费用专项状态：来源模型与所有者隔离已 `PASS_SCOPED`；OKX成交费用符号消费新增候选正独立审查。全站状态仍 `PARTIAL`，不是发布、真实私有来源或LIVE资格。

正式配置只接受观察引用。组合根依据当前认证解析所有者，校验账户/所选凭据ACTIVE、账户/环境/标的与原观察一致、原摘要和有效期；创建run再次校验，冻结完整文本来源及独立摘要，两份配置别名使用同一快照。执行前对照原持久化观察，不接受删除费率、改率、改字段后自行重算摘要；SIM继承原回测模型，不重选账户最新率。保留原公开规则SHA/可见性/滑点及名义金额合同。当前观察的TAKER率按报价币种模型被采用，不宣称真实历史费率或交易所真实结算币种。

`frozen-fee-pg-reopen.log`实际15项目标通过；正/零/负观察经过正式研究、评估、发布和canonical SIM消费。六个新JVM重读同一来源、回测和SIM快照；凭据撤销后原模型不变，来源读取次数不增加。新选择的过期和不同owner/账户/环境/标的被拒；模拟经济事实仅存在夹具拥有的新数据库。

研究私有范围在SQL中限制配置、回测、评估、发布及关联Paper读取；旧无私有字段的研究假设保留原可见性。独立原25文件候选为 `REVISION_REQUIRED`：`FFS-001`实证其他用户可通过日报/稳定性子ID绕过根详情隔离，并真实修改告警；错误父URL也未阻止写入。原报告、探针、exact红例保留，未改写失败。

新29文件候选只加强一份PG测试并增加四份子对象服务/API/Controller。子对象按实际父run检查权限，同时校验请求父路径，在读取、幂等返回或写入前拒绝。`frozen-fee-child-fixed.log`及 `frozen-fee-fixed-independent-review.md`独立28目标通过；独立新探针的12次他人HTTP、12次旧服务、10次owner错误父路径拒绝均保持告警整行不变。错误路径包括同owner另一真实可见run；合法原路径完成OPEN→ACKED→RESOLVED并保持重试幂等。`FFS-001=CLOSED_ON_NEW_CANDIDATE`。原49来源字段变体和15项证据按不变身份复用，不虚称重跑；搜索/Continuous本次没有新增动态资格。

补审释放后，两处旧风格改为显式ChronoUnit import/短名及中文Controller类Javadoc。`frozen-fee-style-review-deltas.json`证明逆变换逐字节恢复原已审文件，不把当前字节身份冒充29文件冻结SHA。当前167个新增/修改Java文件扫描正文全限定类型0、正式通配符import0、无Javadoc0；注释解释来源、权限、精度和失败边界。这是风格证据，不能代替业务验证。

前端新增费用来源选择、引用/率精度保护及原run冻结来源详情，保留高级规则及旧假设。所选市场来自已注册的辅助数据集字段；该字段不发送给配置API，原数据集/版本绑定仍为独立动作。`frozen-fee-ui-unit.log`6项目标通过；外置生产构建通过，1536×1050/390×844及中英文已验证空历史、来源提示和费率/版本只读。UI在18129独立候选连接原V7做读取，有效观察业务GUI全链尚未验。首次构建误写现有18128 dist，已立即保存候选并恢复此前已审版本；`frozen-fee-ui-build-output-restoration.json`逐文件0差异，index为02F7BA661691BA2E36A4A9DDCD42E242F8D0C1BE11F5046404101112CA4C9568。后续构建全部外置，旧失败与候选目录均保留。

OKX provider新增8文件候选（`review-okx-signed-fee-consumption-input.json`）把原始负收费/正返佣转成NQ正成本/负返佣，并按既有NumericPolicy.FEE八位HALF_UP比较、保存和重放。缺失fee拒绝而非补零，原币种保留；旧绝对值历史不一致则拒绝，不能改写旧Trade或追加替代成交。`okx-signed-fee-final-oracles.log`26目标通过，本地HTTP解码及两币种七金额共14 PG经济案例、同原身份重放/冲突拒绝成立。该PG种子是预置FILLED/SIM订单，其审计adapter为mock，不能声称新Risk/Execution、真实审计或真实provider资格；独立审查结论待回收。

验收只读复查仍V7、orders48/trades48/ledger196、LIVE0、非终态0、GLOBAL ENGAGED、凭据回执10/版本3、相关临时库0。18280后端JAR及18128已审bundle保留；没有安装V8或新后端候选。矩阵590项/590唯一ID，原586项全部保留；NEXT-09仍部分完成。真实私有来源、BINANCE费用、有效来源完整public-capture与GUI、运行安装、统计/历史、持续SIM恢复、调度/Shadow/发布验证、全站E2E及最终发行验收等仍未闭合。U01继续PENDING_USER_CLARIFICATION/NON_BLOCKING，不新增推测控件。


## OKX费用消费、恢复读取与有效来源GUI闭合（2026-10-13）

本节更新前述provider审查及有效来源GUI待验证状态；历史失败和原报告保持不变。当前全站仍 `PARTIAL / IMPLEMENTATION_OR_VERIFICATION_GAPS_REMAIN`。没有安装新候选到18280/V7，也没有Git提交或发布。

OKX费用消费8＋2候选已 `PASS_SCOPED / CANDIDATE_RELEASED`（`okx-signed-fee-independent-review.md`）；独立26项、loopback decoder→REST→PG的10费用经济案例、10原身份重放、4缺失金额拒绝和4真正旧abs历史冲突拒绝成立，原Trade整行及经济事实不变。主代理在恢复修改前复核10文件SHA全部匹配；`MinimalLivePilotConfiguration.before-fee-recovery.java`保留已审源码。当前配置文件包含下一项已补审增量，不能称其现有字节SHA仍等于原8文件候选。

恢复观察 `OKX-OBS-001` 已修复为逐笔按持久化八位费用判断：零费用及舍入零只需本金双边2分录，收费/返佣需4分录，NULL明确拒绝。原RECONCILED、确定回执、订单终态与LIVE范围门槛保留，查询不写事实。原回归2失败保留；作者及独立补审分别26目标通过，新2JUnit方法内52读取场景覆盖费用边界、混合成交缺口、NULL、UNKNOWN、非终态、错误环境及整行不变。`pilot-fee-recovery-independent-review.md`结论 `PASS_SCOPED / FIXED_AND_VERIFIED_READ_PREDICATE`。该新查询测试明确采用缩减PG读取表及未调用的构造端口mock，不是完整Flyway、startup、lease、provider、LIVE或offline恢复资格。

有效来源GUI在独立18281真实Servlet＋新PG库执行；18130用于原外置候选，随后18131使用启用手动Strategy SIM、关闭Continuous的外置正式构建。原构建关闭手动Strategy SIM的状态保留，不修改18128。供应方仅在测试类路径装配：费用为明确合成返佣，市场数据为既有录制OKX公共响应，经loopback HTTP和正式捕获服务生成不可变public-capture数据集；没有mock NQ主API、真实凭据、私有联网或真实provider。本验证不计为真实私有源资格。

浏览器通过实际控件选择研究与数据集、采用精确观察率-0.001000000000，费率/版本只读；首次观察过期提交收到明确BAD_REQUEST，保留截图。通过正式测试费用API生成新观察后，GUI创建配置、分别绑定版本和数据集、创建并启动回测、评估、发布，结果页显示原观察/冻结时点、72点曲线、Buy & Hold、IS/OOS和负费用。运行为brn-fd2e8021-efd1-4f0f-b67c-fe2dd5f2e74d，观察f288e500-0a61-4e48-b1f3-0340aa0c5dce，回测总费用-2.26861317。

源观察过期后，GUI从同一发布创建ptr-82242965-9bf9-4755-9146-3a2ca3627568并启动，SIM仍继承原完整费用快照。六次显式策略决策经过正式Risk/Execution产生买单；来源所有者对调度只能读取，预置测试管理员从正式调度页执行一次PAPER_MATCHING，切回所有者显示真实FILLED与负费用。`frozen-fee-gui-verification.json`独立Decimal oracle验证1 Order/1 Trade/6 Ledger（含初始资金双边）、fee=-0.09999972 USDT、cash=0.10027613 USDT、position=0.00124093 BTC、逐币种平衡及持久Risk ALLOW。重复撮合后订单、成交、分录、持仓、风险与现金全部相同；GUI停止后服务端STOPPED，原回测/SIM费用快照相同。没有手写Order/Trade/Ledger种子填页面。

有效来源的1536×1024与390×844 CSS视口、停止状态、来源及返回/刷新已检查；控制台error/warn为空。浏览器旧tab.screenshot产生缩略/错误范围的图像以及CDP截屏超时不能作为完整视口证据，保留其文件并另用getScreenshot获得确认图。确认图实际像素为1526×1017、1536×1024、380×822，和CSS视口分开记录；不冒充原生缩放或真实手机验证。主要证据为frozen-fee-gui/result-1536-confirmed.jpg、sim-rebate-native.jpg、sim-rebate-390-confirmed.jpg及result-dom.txt。GUI成交表暂未单列费用币种，正式事实中的USDT已经核对；该呈现改进及全站完整视觉矩阵仍待补齐。

临时测试服务器和其拥有的数据库在保存事实后按自身清理路径关闭，既有18280/18128、18127原型、18099 Demo和C4保持不变。矩阵591唯一ID，原590项全保留。真实私有来源、BINANCE、正/零观察GUI、完整public-capture跨进程/SIM恢复及启动恢复、新运行安装、持续SIM恢复、其他全站功能/E2E/Full Maven/发行与升级验收仍未闭合。U01保持PENDING_USER_CLARIFICATION/NON_BLOCKING。


## 本地V8安装与成交费用币种呈现（2026-10-13）

本节更新之前“V8未安装”的状态；全站仍PARTIAL。Docker Linux引擎曾不可连接，原验收Java/preview进程也已不存在；官方docker desktop start恢复引擎，不执行reset/restore或停止共享容器。恢复后原库V7、48 Order/48 Trade/196 Ledger、LIVE0、非终态0、ENGAGED及凭据10回执/3版本保持。没有重跑C4或取得真实运行权限。

安装前保存V7完整本地dump及SHA，恢复到本次拥有的nq_fee_upgrade命名副本。原指纹探针的scope_id列名错误，以及对待应用V8提前validate失败分别保留；改为正式scope列排序及target7原迁移身份校验后，fee-runtime-v8-upgrade-confirmed.log证明V7→V8、正式8迁移validate、经济/冻结/kill业务事实SHA256不变、5合法费率/6非法费用或滑点拒绝、重复migrate无操作。升级副本已清理，原dump保留用于后续恢复与发行验证；当前不宣称完整旧代码回滚资格。

后端以当前源码reactor install及可执行JAR repackage构建，命令明确-DskipTests，因此这两次BUILD SUCCESS只是构建；业务证明复用此前匹配范围的实际测试/独立审查。fee-runtime-v8-install.json记录新JAR/PID/摘要；59312进程健康UP，原验收库通过正式启动迁移到V8。CLI明确关闭LIVE/真实provider/私有费率诊断、恢复、WS和Continuous，仅保留既有公共只读与手动SIM。正式API10次只读验证8个归属账户的历史为空且同步关闭，以及未知结果404；没有执行私有读取/同步。最终原库业务计数48/48/196、LIVE0、ENGAGED保持。

前端成交表增加原fee_currency，金额仍使用exact_fee及原精确组件；不据交易对或冻结模型猜币种。业务字段先显示，内部Trade/Order标识移到其后并保留复制；React行key仍取trade_id，两个同symbol成交不会共用行身份。中文注释说明币种边界与稳定身份。两个外置生产构建通过；已关闭的18128本任务preview使用外置fee-currency-business-first-dist重新启动，当前PID26000连接正式18280/V8，不覆盖旧已审dist。

实际浏览器沿已持久化ptr-73fad6ab-3e2b-47a4-b253-5c2fda418229的正式事实读取，STOPPED、BUY/SELL两笔均保留；显示原费用0.09989932/0.0998455和USDT、数量及身份不变。中英文列、刷新与新列排序已验，console error/warn为空；语言恢复原English，视口override已reset。截图/DOM在fee-runtime-v8-ui。没有新增或改写该实例的Order/Trade/Ledger；其他共享组件调用页及窄屏新增列尚未新增验证。

旧verify-account-fee-runtime-api.ps1使用固定输出名，本次重跑覆盖了account-fee-runtime-api-result.json的旧V7摘要。已另存fee-runtime-v8-api-result.json，并记录fee-runtime-api-evidence-regeneration-note.json；旧摘要原字节NOT_PRESERVED，不用当前V8文件冒充原V7读取证据。原独立报告、代码候选身份和原日志未改动。脚本增加有界OutputName参数，后续采用新名称保存结果。

592项/592唯一ID，原591项全保留。真实私有来源、BINANCE、正/零来源GUI、完整startup/lease/offline恢复、统计/历史、Continuous撤单恢复、调度/Shadow/发布验证、全站E2E/Full Maven、完整发行/升级/备份恢复仍未闭合；本次JAR与preview是本地实现候选，不是FULL_SCOPE或正式发行包资格。Git commit/push/PR/merge/发布仍NONE，U01继续NON_BLOCKING/PENDING_USER_CLARIFICATION。


## 同对象同期间统计与真实SIM历史（2026-10-14）

本节更新统计/历史的当前状态，全站继续ACTIVE / PARTIAL。既有研究冻结报告保持原身份；账户和独立SIM新增正式只读统计，不能把不同对象或期间的数据拼接。收益/回撤沿期初权益扣净资金流口径，非TWR。Sharpe为零无风险利率、总体方差、非年化观察收益，至少三点且采样桶连续；缺测/重复/非正权益/零方差返回null及原因。胜率按加权买入成本和每笔卖出净结果计算，部分平仓计一笔，平仓结果统计(起点,终点]，与两端权益之差对齐；保留正成本/负返佣，成交价已含滑点不重复扣除。第三币种费用、缺开仓成本和超卖不裁剪后伪算；无亏损时盈亏比未定义。接口上限1000个估值点和10000条成本成交，超限不输出伪完整指标。

账户沿用正式所有者检查、真实测量、币种与采样覆盖，历史成交从起点建立成本。LIVE没有完整开仓成本时不可用，不凭局部成交估算胜率。独立SIM新读取仍先复用原实例与费用来源归属，再在一致事务快照读取canonical成交、正式账户分录与已处理信号。历史回放按模拟执行事件重建，持续SIM分支按实际成交时间且排除真实注资之前暖机时点；两种时钟在页面明确声明，实际账务更新时间另列。执行行情只取确有canonical成交的已保存报价/执行bar开盘价，同一时点优先该行情；未成交NO_SIGNAL中预存的未来bar不得成为已发生的估值。现金不合计对手分录，费用/双边账务/预算来源不完整时拒绝重建。查询不推进、撮合、注资或写经济事实，也不读取冻结数据集未来价格补曲线。

实际执行25项后端目标测试（core11、scheduler4、真实PG/Spring/JWT/MockMvc10），全部通过；PG采用每类自建完整Flyway临时库，包含所有者/401/404、范围400、精准经济公式、负返佣、晚于覆盖的成交、未成交未来行情排除、1000点超限及缩小范围恢复、费用币种/未知资金拒绝、查询无经济副作用。模拟关联/行情/部分账户测量和成交是明确的测试夹具；SIM注资与双边记账走正式服务，不能冒充完整Risk/撮合E2E。首次幂等身份不符、活动策略争用、生成夹具SQL括号及二次注资拒绝分别保留原失败日志；修正的是夹具，不削弱正式约束。测试导入随后整理为显式import，业务测试复用上述25项，另执行test-compile；不把-DskipTests的编译/构建计作测试。前端14项目标单元通过，type/build通过；既存大chunk提示保留。

生产隔离Servlet和既有验收库读取验证见performance-formal-api-final.json。原持久化SIM仍STOPPED、BUY/SELL两笔；历史8点覆盖2026-10-08T16:00:00Z至23:00:00Z，净收益-0.25356225 USDT与当前快照一致，最后卖出完整纳入，晚于覆盖成交0。GUI显示Sharpe -0.4421（7段、3600秒桶）、胜率0%（盈利0/亏损1/持平0），原已发生费用仍0.19974482 USDT。窄范围3点不带入后续两笔成交；零方差Sharpe及无平仓胜率显示—与原因，不能替代成0。没有创建或修改这个实例的订单/成交/账务。

新增资产历史/复盘统计界面保留账户与独立SIM分工；时分秒24/60/60选项、逆序表单拦截、秒级URL/范围、草稿重置已实际检查。浏览器发现的“全部范围不清草稿”与“跨页丢期间”已修复，失败和修复分别保留；导航仅传递实例/统计期间，页内订单与tab不混带。中英文、1536×960与390×844代表视口的曲线、来源、精确表格和统计已检查，移动弹层完整可读，稳定后页面宽度380（390 CSS视口含滚动条）。初次桌面截图只捕获Restoring状态，未用作验收；confirmed图片经实际查看。截图/DOM在performance-ui，检查记录见performance-ui-results.json。控制台error/warn为空；原English及视口override已恢复。

正式账户在本次GUI检查时88次采样仍缺完整估值，GUI的权益/收益/回撤/Sharpe保持—并明确原因；不把独立SIM预算合计为账户资产，不以此缺失状态冒充正值账户图表验收。正值账户GUI、持续SIM真实时钟完整运行GUI、历史Paper分离及全站尺寸/双语/异常矩阵仍未验证/未闭合。

本地后端PID14552，nq-app-statistics-20261014-011738.jar，SHA256=6A5B9C5BE515DC04379810F0DCEA1A95AC5E732BFB7F9D1AA5665D768F59D0F4，18280健康UP；preview PID30080，18128使用外置performance-delivery-dist，不覆盖旧已审dist。V8无新增migration；原库48订单/48成交/196账务、LIVE0、非终态0、ENGAGED和凭据10回执/3版本保持。真实/private/WS/recovery/Continuous关闭，既有公共只读和手动SIM保持。新JAR/preview是本地实现候选，不是正式发行或完整恢复资格。

矩阵596项/596唯一ID，原592全部保留。NEXT-19有界读取及回放GUI已落实，但不标全站完成。四价实际时点、多交易所能力映射、默认币种/主题/版本更新、其余通知、Continuous撤单恢复、调度/Shadow/发布验证、全站E2E/Full Maven及完整发行/升级/备份恢复继续推进；真实私有源等依旧未授权。Git commit/push/PR/merge/发布NONE，U01继续NON_BLOCKING/PENDING_USER_CLARIFICATION。


## NEXT-10 四价来源元数据接入与隔离验证（2026-10-14）

全站仍 ACTIVE/PARTIAL。本项已补正式14字段来源接入、精确版本读取及只读原请求核验；当前OHLCV没有四价发生时点，不使用openTime/closeTime/availableAt代填。原研究HistoricalBar/冻结输入和行情raw响应不改变。来源记录与bar范围/四价/来源/质量版本绑定，保存后不可更新/删除；只标SOURCE_PROVIDED，不宣称供应方认证或完整逐笔证明。来源名称不保存本机路径，作者来自正式认证。

新增V9，未改写任何已执行migration。新临时PG先V8再完整Spring升V9，validate、重复migrate NOOP、约束/事务/并发、父bar更新后的旧时点隐藏和冻结研究摘要保持已验证。最新实现测试13项（5 codec、8 PG/Spring/JWT/MockMvc）通过；前端目标7项通过，type/build通过。单独调用repackage缺少同生命周期package的原失败保留，改用package spring-boot:repackage构建成功；构建不计测试。大chunk警告仍存在。

独立新审查者冻结27文件并校验前后hash，PASS_SCOPED，P0/P1/P2/P3=0；自主后端13/13、前端7/7、真实PG边界17项、双标签页槽9项通过。报告的两项观察保留：直接SQL可将超微秒文本cast舍入（正式codec拒绝）；父bar改变后旧submit冲突而旧完整verify仍可确认。没有由此扩大writer或改V9。审查后仅两项视觉修改：本地/ISO时间上下排列；DataFreshness允许长时间文字小屏换行，未变业务状态/请求/存储。差异哈希另存price-event-post-review-visual-deltas.json，未重开费用链审查。

真实V8验收备份restore到自建副本后升V9，经济和行情摘要相同，48 orders/48 trades/196 ledger、LIVE0、非终态0、ENGAGED保持。正式Servlet/Production UI在该副本验证明确合成JSON的保存、精确微秒、刷新和真正进程重启读取、同版冲突拒绝、网络断开UNKNOWN、跨bar恢复原对象、只读等价MATCH_FOUND及NOT_FOUND不清理/不盲写。断网只改变浏览器网络条件，不拦截或mock NQ业务API；已恢复。旧CORS跨端口预览失败及停止/后台启动组合命令的自动审批拒绝保留，使用同源前台预览完成验证。截图捕获到旧帧/未稳定视口时保留但不作为最终证明；final-confirmed图和DOM实际查看。1536×1050英文桌面、390×844中文手机及默认1280×720原验收库已检查，修正前399px横溢出、修正后390px；控制台error/warn为空。视口override已reset，原环境English保持。临时API/preview已停止，副本/备份和证据保留。

已安装本任务原隔离验收环境V9：后端PID21964、18280健康UP，前端PID22700、18128外置price-event-delivery-dist。精确JAR/前端hash与备份见price-event-runtime-install.json和price-event-delivery-ui-preview.json。原库经济摘要0a0eb71456721964c745edaef97feb0a、行情摘要4e8fae14a771b8567636336678e2256e升级前后相同，来源表0条；不把临时合成时点迁入原库。真实供应记录仍未取得，NEXT-10保持IMPLEMENTED_PENDING_VERIFICATION，不把正确空态或合成正值当真实来源完成。

矩阵599项，原596全部保留并新增3项读取/接入/核验动作。默认币种/主题/版本更新、多交易所映射、其余通知、调度/Shadow/发布验证、正值账户及持续SIM完整GUI、全站回归与发行包资格继续推进。Git commit/push/PR/merge/发布NONE，U01保持NON_BLOCKING/PENDING_USER_CLARIFICATION。


## NEXT-16 按任务书保留深色范围（2026-10-14）

核对任务书第5节后修正此前矩阵的泛化描述：当前HEAD没有明暗切换，正式任务明确没有既有浅色能力时不新造。中途新增的浅色候选已单独归档appearance-future-candidate（23文件、SHA256 manifest及相对恢复后源码的增量patch），其6项逻辑测试、构建和真实GUI草稿/选中K线保留验证均属于外部未来候选，不计为本轮正式新增能力验收，也不将它带入正式重构。

仅逆向本轮主题新增内容，没有git reset/checkout/clean或覆盖此前用户/全站改动。正式源码无appearance store/切换控件，重新type/build通过。恢复后bundle与较早price-event-delivery-dist存在71个文件名/hash差异，记录在appearance-scope-restoration.json，不声称逐字节回滚一致。正式实际页面已检查背景rgb(5,15,27)、没有主题开关，仍为已确认深色；后端V9/PID21964/18280保持，preview现在PID24584/18128、外置appearance-scope-restored-dist。主题范围的全站视觉验证仍待完成。矩阵599项无删除；NEXT-13保留既有venue能力、NEXT-15默认交易对/资产币种（不新增无来源法币折算）、NEXT-18实际版本及复用安装器的检查更新继续推进。

当前续接点见price-event-final-checkpoint.json。范围恢复后四价目标7项再次通过，20个后端已审文件哈希保持；当前正式深色GUI截图/DOM为formal-dark-final-confirmed.jpg及formal-dark-final-dom.txt，控制台error/warn为空。7项重复验证不累加为14个不同测试。


## 多交易所能力、默认计价与版本读取续接（2026-10-14）

全站仍 ACTIVE/PARTIAL。NEXT-13已保留正式策略接口的45项记录（5 venue×9 capability），筛选/9行分页/独立Drawer仅改善呈现。全部当前allowed=false、liveAuthorized=false；原始状态及全部原因留在绑定点击时快照的详情。未知能力/状态/原因不静默删行，不把静态策略当实际公共历史行情或盘口覆盖。行内展开造成的空白已改为独立Drawer；顶栏桌面搜索文字在1700px以下折叠为有访问名称的图标，避免退出按钮孤立换行。

NEXT-15默认查看交易对与默认账户资产计价分离。资产计价从已确认ACTIVE默认账户的正式表现summary读取，范围60秒有界，响应账户和USDT必须匹配；无默认/停用/读取失败不猜币种。正式能力固定USDT，未新增可变计价偏好、汇率或法币折算。顶部环境已使用所选账户真实SIM/LIVE；没有以旧静态PAPER标签代替实际上下文，也不授予执行权。

新增GET /api/system/version和实际Maven build-info。制品版本0.1.0-SNAPSHOT与产品包版本分别呈现；当前本地JAR无安装器包身份，正式接口返回null和UNAVAILABLE，不由根VERSION=1.0.0、旧HEAD或示例数据代填。发行compose向后端传入安装器已有核验包字段；其状态只为INSTALLER_DECLARED，不代表本轮发行验收或签名验证。服务只读取白名单元数据，不执行宿主命令。build-info排除构建时钟，不在确定性制品中加入变化时间。

设置页接入版本读取、原始摘要详情及检查/更新步骤Drawer。步骤与现有Windows/POSIX安装器实际目录和参数核对，check-update只读核验目标包；update仍要求用户输入UPDATE，没有自动确认参数。当前尚未打通浏览器调用宿主安装器，也没有真实新包的检查/确认升级证明；NEXT-18继续IMPLEMENTED_PENDING_VERIFICATION，不能以步骤说明、版本空态或fixture算完整更新交付。

定向验证：能力纯逻辑4项、版本后端4项（含MockMvc JSON）、最新Production Build上的E2E11项通过（3正式直连，8明确边界fixture）；既有安装器check-update/确认前不变更2项SIMULATED fixture通过。这些fixture不证明真实安装/升级或跨平台资格。首轮空数组通知fixture、AntD隐藏输入选择器、双字按钮空格及后端测试缺JSON converter失败已保留；修正fixture/测试合同并补测试域JSON依赖后通过，没有关闭权限断言、放宽超时或假造正式业务响应。

只读helper发现顶栏语言选择会持久化偏好，撤回此前“只切会话”的错误注释与行为；现改随已有中英文偏好断言，恢复原英文。最终11项测试前后正式偏好HTTP摘要2F65B70A42998D6F2CD9FD0DFCC0490A93925910E25DB23B995399E6FF1BF980相同，不创建账户、不重置默认或改写自选。早期测试引起的语言切换及恢复不称为从未写偏好，CAS版本正常推进。正式账户与经济事实摘要保持。

IAB实际查看1536×1050桌面和390×844窄屏，中文能力/计价及英文版本卡片、原始详情和更新步骤，稳定截图与DOM已保存；早期捕获动画旧帧不计验收。移动卡片document380/viewport390，可见详情Drawer390px；控制台error/warn为0。viewport override已reset，当前英文设置页保留用于验收。GUI和测试保留正式源与fixture区别，未执行更新、真实凭据、私有联调或LIVE。

当前后端PID31112/18280（jar/hash见runtime-version-runtime-install.json），前端PID11508/18128、runtime-version-initial-dist。无新增migration，V9/48订单/48成交/196账务、LIVE0、非终态0、ENGAGED保持，经济MD5=0a0eb71456721964c745edaef97feb0a、行情MD5=4e8fae14a771b8567636336678e2256e与替换前相同。JAR构建是实现候选，不是发行包或Full Maven证明。

矩阵603唯一项，原599全部保留；新增能力详情、默认计价读取、正式版本读取和安装器步骤4条。NEXT-13/15现有范围已验证；NEXT-18完整流程仍待闭合。源码候选清单见adapter-currency-version-source-manifest.json，续接状态见adapter-currency-version-progress.json。BINANCE费用来源、调度/发布验证/Shadow/Continuous SIM、正值账户GUI、其余通知、全站E2E/Full Maven、集中独立复核、用户指南及真实新包本地资格继续推进。Git commit/push/PR/merge/发布NONE；U01仍NON_BLOCKING/PENDING_USER_CLARIFICATION。


## 系统任务间隔与可见草稿校验（2026-10-14）

继续正式全站范围，整体仍ACTIVE/PARTIAL。系统调度页面发现统一1000—86400000ms的控件范围与正式目录不一致：Continuous SIM至少30000ms、Paper matching最多60000ms、Ledger reconciliation至少5000ms。旧正式页面的Continuous控件min1000已实际复现并保存截图/DOM。现GET/list/detail/PATCH响应保留全部原控制字段，通过只读扁平DTO追加同jobKey的minFixedDelayMs/maxFixedDelayMs；范围取原ScheduledJobRegistry，不修改目录、持久控制、执行资格、交易路径、数据库或migration。读取不调用eligible/runOnce，原JSON字段不变，API为兼容增加字段。

前端读取正式范围；旧服务/缺失/小数/反向等异常元数据不给宽泛fallback。范围外输入进一步暴露AntD行为：不触发onChange，旧合法值保持，使Save仍可点。已保留raw29999/Save enabled的正式失败截图和失败E2E，最小补独立可见草稿：onInput保存原文，仅完整安全整数且符合该任务范围才允许提交；提交解析后的当前草稿，不能以旧值或隐式舍入替代。现在正式GUI raw29999保存禁用、raw30000可保存（本次取消），Paper raw60001在390px保存禁用；控件与红色范围说明清楚可读，没有超屏或弹层遮盖。

最新单元9项通过（5间隔/草稿＋4能力映射复用，不累计之前8项）；后端定向18项通过（1响应JSON契约＋17完整AuthSecurityWebMvcTest），原ADMIN/读角色和审计主体失败关闭断言保留。最新Production bundle上的15项E2E通过（4正式直连＋11明确fixture），其中系统任务fixture验证启停/保存/run-once与禁止恢复任务、只读角色、失败保留旧列表及30000边界。正式GUI/API只读检查，不启用、执行或保存原验收任务；fixture/MVC mock不算真实PG操作或经济证明。测试前后偏好摘要2F65B70A42998D6F2CD9FD0DFCC0490A93925910E25DB23B995399E6FF1BF980一致。初始测试脚本括号错误、前一范围修正仍提交旧草稿的失败均保留，未削弱断言或延长超时。

版本/默认计价/能力页面的相关11项在新候选上复验，未重新审计已接受费用链。最新正式8任务API约束及状态见scheduler-delay-formal-api.json；全部enabled=false。控制台error/warn为0，默认视口已恢复，英文原偏好保持。1536/390的其他全站矩阵仍待继续，不以本次代表控件检查替代整站视觉验收。

当前后端PID17172/18280、nq-app-scheduler-limits候选（摘要见scheduler-delay-runtime-install.json），前端PID36164/18128、scheduler-delay-draft-fixed-dist。无新增migration，V9仍为当前schema；jar构建不是Full Maven或发行包验证。矩阵604唯一追踪项，原603全部保留，系统任务动作按读取/表单与真实写链分开记录；真实启停/运行/保存及未知恢复继续待验，不能整页报完成。候选与续接点见scheduler-delay-source-manifest.json和scheduler-delay-progress.json，Git commit/push/PR/merge/发布NONE，U01仍NON_BLOCKING/PENDING_USER_CLARIFICATION。

最终只读复核：8项系统任务控制/API原字段与开始核对时完全相同，enabled=0；经济/行情摘要与替换前相同，V9/48/48/196/LIVE0/非终态0/ENGAGED保持。192个本轮新增/修改Java文件的正文全限定类型0、正式通配符import0、正式无类级Javadoc0；该扫描不证明每个方法注释充分或代码正确。当前可接续候选见production-reconstruction-current-checkpoint.json，所有既有运行身份记录保留为历史，不将旧PID当当前环境。

## NEXT-09 Binance完整费用来源候选（2026-10-14）

原生reader独立补审PASS_SCOPED，两项原发现（TESTNET /sapi缺失与Unicode symbol编码）已闭合；19/19作者与19/19独立目标通过。完整来源现保留standard/tax/special十二分量、优惠条件、原生响应摘要与供应响应时间依据；不伪造VIP档位、费率变更时间、全局maker/taker成本或优惠余额。

新增V10仅在随机临时PG执行，完整JSON及来源符号/缺失合同、旧观察升级、V1摘要兼容和不可变约束已验证。精确owner/account/credential/type的JIT桥不导出材料或session；HMAC及内存Ed25519经正式密文和本地HTTP验证，同一ENGAGED状态、权限、类型/环境边界拒绝不留成功回执。主网只读来源对应LIVE账户，执行开关仍关闭；TESTNET不借用主网。IP限制只报告启用标志，未伪造字面名单证明。默认关闭composition root与多源唯一匹配路由已验证。

35项受影响后端回归、3项装配测试、20项前端单测、5项明示fixture E2E及type/build通过。原失败（测试编译依赖、E2E括号/翻译键、旧冻结fixture仅V8而新仓储需V10）均保留并修正，没有放宽业务断言。前端使用有名称的凭据类型选择、完整分量表和优惠/缺失边界，1440与390中英均验证；这些UI正例不是真实账户来源。

38文件接入候选已冻结，独立来源审查尚待返回。原验收仍V9/48订单/48成交/196账务、LIVE0、非终态0、ENGAGED，经济与行情摘要相同；18280后端及18128预览未替换。运行V1只有单一quote费用，Binance方向/支付模型尚未实现，明确拒绝强压成标量；此需求继续PENDING。真实凭据/私有联调未授权，费用及全站仍PARTIAL。账本609唯一项、原604全保留；无Git提交/发布，U01持续NON_BLOCKING/PENDING_USER_CLARIFICATION。

Binance费用来源接入终态：独立PASS_SCOPED，无新增finding，38/38冻结文件hash一致。独立35 PG/兼容、3配置、20UI单测及外部锁探针通过；原生19独立协议证据按同hash复用。其后只实施隔离安装及读取验证，不改已审源代码。

V9验收数据库已有备份；两份随机快照副本仅用于升级验证并已删除。第一工具会话时区使JSON文本摘要不能直接与psql相比，保留该证据；新V9副本固定UTC JVM后，升级V10、validate、重复migrate=0及独立进程重读均保持原经济/行情MD5。此证据只证明原快照升级/重开，不能代替新Binance正值观察的实际进程重启。

首次普通package生成非可执行226478字节JAR，安装前漏检导致本任务隔离后端短暂停止；没有应用migration，失败包和stderr保留。显式package spring-boot:repackage后验证Boot结构，37MB新候选已正常启动并升级V10。后端PID34472/18280，runtime/nq-app-binance-fee-v10-20261014-071349.jar，SHA256 30BFA75A745EEBE928F68F221B15DD1741ECE9D60C365D75EBDCFB442FB76CF5；前端PID60968/18128，binance-fee-observation-dist。私有费用来源与LIVE执行均关闭。

正式8账户费率history均为空且synchronizationAvailable=false；15项安装后回归通过（4正式、11fixture），加此前同bundle的5个原生页面fixture共20项相关E2E证据（4正式、16fixture），不等于全站E2E。用户偏好摘要前后相同。IAB旧页更新后出现过失效懒加载资源，已刷新并直接进入/accounts；正式既有Binance SIM费用抽屉清楚显示类型、无活动版本、测试网真实限制及禁用同步，键盘切换Ed25519成功；未据此宣称全菜单导航验收。

安装后V10/48订单/48成交/196账务、LIVE0、非终态0、ENGAGED，经济与行情摘要仍分别为0a0eb71456721964c745edaef97feb0a和4e8fae14a771b8567636336678e2256e。原V1—V9未修改，临时18129预览关闭，所有失败和备份保留。609唯一项保留全部旧项。Binance方向/支付消费及新原生观察重启、真实私有源、其余全站任务/Full Maven/发行资格仍未完成；无Git提交/发布，U01持续NON_BLOCKING/PENDING_USER_CLARIFICATION。最新接续状态在production-reconstruction-current-checkpoint.json。

## 方向费用域层与原生观察重启（2026-10-14）

新原生观察的实际Servlet/HTTP重启缺口已完成有界证明：HMAC与Ed25519各在两个独立JVM之间同步、正常关闭、关闭来源后重新登录并查询/重放。作者2项与独立2项均通过，完整12分量、来源摘要及原回执一致；新UUID拒绝，重开进程providerGets=0。测试仅使用随机隔离PG和合成loopback供应方，订单/成交/账务行均为0，ENGAGED不变；不是实际账户私有联调。证据为binance-fee-http-restart-first.log及directional-fee-domain-review-http.log。

新增SpotFeeModel、FrozenDirectionalFeeModel及方向sizing，明确BUY收到base、SELL收到quote；原币费用与quote估值分开，优惠只作用standard并要求明确资金/换算依据，足额失败回落到未优惠的收到资产费率。V2冻结完整来源、支付条件、精度、缺失值和独立嵌套摘要，旧V1冻结合同保持不变。优惠资金sizing及正式方向消费仍未接通，不能冒用旧全局feeRate。

首次独立审查发现F1/P2：连续费率反解后再舍入费用可能让净base超过目标，初始候选与反例完整保留于directional-fee-domain-initial-candidate及原REVISION_REQUIRED报告。新候选拒绝费用精度不能精确表示的lot，并对量化净量最多128步整数二分；新增原失败、细持仓、高费率平台及带符号精度组合回归。作者51项、独立51项受影响目标通过；独立1512组外部枚举没有净量/现金越界，补审PASS_SCOPED / DOMAIN_ONLY，F1闭合，8/8哈希匹配，无新增finding。正式来源38文件仍同哈希，原来源和HTTP审查按相同事实复用，不形成重复审查链。

本次8个代码/测试文件使用短类名，Java正文全限定类型审计0，关键资金、精度、来源、进程边界有简体中文注释。隔离验收仍运行原V10候选，域层未安装、未开放正式入口；尚需回测原币费用落库、净持仓与Buy & Hold、canonical SIM决策/撮合/预留、源市场范围及恢复经济证明。方向/支付消费、真实私有联调与全站剩余任务仍PENDING，整体ACTIVE/PARTIAL。无Git提交/发布，U01继续NON_BLOCKING/PENDING_USER_CLARIFICATION；接续细项见directional-fee-domain-progress.json。

## 原币回测、胜率与年化边界续接（2026-10-14）

方向费用已接入研究回测，BUY原币base费用影响净持仓，SELL quote费用影响现金；原币数量与quote估值分别落库、返回与展示，旧记录缺少原币字段仍明确显示未提供。原币成交、Buy & Hold、冻结来源和研究有效性使用同一观察身份，不能把本结果当作canonical SIM方向费用已经实现。

独立原币回测审查发现微亏被报告为100%胜率，初始候选和失败已保留。修复按净base和实际现金本金分摊闭合成本，完整平仓直接消费剩余本金；已计入成交价的滑点不重复扣除。23文件补审PASS_SCOPED / F1_FIX_ONLY / BACKTEST_ONLY；独立原1m反例正确记录一笔亏损、胜率0，239个外部经济场景及6个期望拒绝通过。新增真实Servlet/JWT、随机PG、两JVM恢复作者3项通过，重开来源关闭且providerGets=0；该test-only恢复证据不在23文件独立审查范围内。

随后修复分钟正收益年化超出既有numeric(36,18)使评估保存失败的问题。真实总收益和盈亏保持原值；年化不可计算或超范围返回null，在指标JSON中持久化明确原因，页面直接显示。零收益仍为0。独立补审发现接近全亏损时先转double丢失正增长因子的新反例；保留原失败后修复，一年直接使用十进制，接近全亏先求精确增长因子，接近零使用log1p。新11文件的F1及受影响数学补审PASS_SCOPED；作者28目标、独立8目标与21边界断言通过，不重新打开原胜率审查。没有修改任何已执行migration。

小时/分钟×正/零/负费用×两档滑点12组实际PG经济场景通过，连同旧冻结和signed SIM回归共6个JUnit方法/参数调用；分钟正收益由正式查询读取明确OUT_OF_RANGE。最终无人工GUI等待的真实Servlet双JVM分钟正收益测试1项BUILD SUCCESS，持久收益及缺失原因跨进程相同，延迟冻结运行也可在来源关闭后执行。

正式构建与真实Servlet的1536×1024、390×950中英文页面已查看。评估详情改为窄屏单列，ID/标签不再竖排，年化原因无需悬停；document/Drawer均390px，控制台error/warn0。两次含GUI等待的Java套件被外部环境中断，截图和实际查询证据保留，但整份套件不报PASS；终态恢复证明来自上述独立无GUI等待运行。精确随机库的0订单/0成交/0账务、ENGAGED与报告事实已保存后仅清理这些自建临时库，未触及原验收数据。

当前611唯一追踪项，原609全部保留。原隔离验收仍为固定V10制品及原前端bundle，新V11原币/年化候选未安装。Docker及验收进程恢复只执行Start，无Reset/RestoreBaseline；最新进程及健康以production-reconstruction-current-checkpoint.json为准，不将历史PID当当前身份。canonical SIM方向决策/在途资金预留/撮合/原币账务、优惠资产预算/换算/结算、剩余全站功能与最终发行资格继续未完成；全站仍ACTIVE/PARTIAL。Git提交、Push、PR、合并与发布NONE；U01保持NON_BLOCKING/PENDING_USER_CLARIFICATION。

## canonical SIM方向费用与取消恢复（2026-10-14，作者验证）

正式独立SIM已消费冻结方向费用合同，来源可保留BINANCE/OKX身份，执行仍是canonical PAPER/SIM。BUY原币base费用影响净收到持仓，SELL quote费用影响现金；费用原币不与quote估值重复扣除。在途订单按本金和正费用预留，返佣不得提前提供现金或可卖持仓；完整在途集合超过500项明确拒绝，不能截断后遗漏预算。冻结方向合同与旧标量费率互斥，原币模型的空feeRate不补零。连续运行仍仅保留当前正式OKX公开来源，未伪造BINANCE连续来源。

旧numeric(12,8)会把微小正费率0.000000001舍入成零，反例已保留。新增V13扩展为numeric(38,18)，不改V1—V12，不回算历史已丢失费率或账务。V11→V12→V13随机库升级、validate、重复migrate=0、旧writer及旧事实兼容已通过；V12/V13在隔离证明中已执行，不得原地改写。

六组真实PG场景覆盖正/零/负费用×两档滑点，从原生loopback观察、冻结、回测、评估和发布进入正式Risk/Execution/Order/Trade/Ledger。实际JDBC分录中途失败时整体回滚，两个并发撮合事务只提交一笔成交和一套账务；重复匹配不增加事实。净base、USDT现金、逐币双边平衡及闭仓胜负按独立现金收支核对。共享统计已适配原币base费用，未知第三资产不伪装可评估。

真实Servlet/JWT/随机PG的两JVM七项测试通过：三种费用符号的在途买单停机恢复、三种费用符号的撤单预留释放、一次准入成功后绑定失败恢复。第二JVM来源关闭，providerGets=0；跨owner和VIEWER操作被拒绝；未知取消响应通过原订单查询和稳定身份重复确认，绑定失败只恢复原决策/请求/run/order，无重复经济事实。

撤单反例曾出现订单CANCELLED而策略执行仍占活动状态，下一次推进500；原日志保留。修复只核对PAPER/SIM的canonical CancelAck、取消审计、原订单版本和零durable fills，再委托既有取消终态受限函数。现有本地SIM adapter查单固定返回ACCEPTED，不能作为取消证明；实际交易所和LIVE取消规则没有放宽。页面增加自身订单撤单确认、未知结果查询和取消执行刷新。新增修改正文使用显式import/短类名和中文业务边界注释，冻结36文件审计全限定类型0。

作者目标32项、上述七项HTTP与相关前端构建通过；六组PG及升级证据的有效范围明确记录。30变更文件与6关联文件已冻结到review-canonical-directional-integration-input.json，独立审查进行中。当前617唯一追踪项，原611全部保留。真实GUI、独立接受及隔离安装尚未完成，原验收仍为固定V10候选。优惠资产预算/换算/预留/结算继续是必要待实现内容，不永久缩为RECEIVED_ASSET。全站ACTIVE/PARTIAL、真实私有联调与全站/发行资格未完成；Git及发布NONE，U01仍NON_BLOCKING/PENDING_USER_CLARIFICATION。


## 格式整理与优惠资产独立复核续接（2026-10-14）

全站继续 ACTIVE/PARTIAL，当前隔离验收仍为固定 V13 后端和已验证的详情全宽前端，18280 health 与 18128 页面重新读取均200。优惠资产候选没有安装，也不代表发行资格。

按用户新增要求建立440个本任务新增/修改代码与配置的精确清单及完整备份，424个实际格式变化，16个无需变化；25个排除文件全保持。274 Java编译器AST前后相等并通过google-java-format AOSP稳定检查，166前端/配置语法树及重复格式稳定检查相等。保留中文注释、字符串与显式import，受影响正式Java类中文Javadoc无缺，全限定类型正文0；不格式化已执行V1—V17、冻结证据、文档、二进制和锁文件。国际化静态检查曾误把局部pages命名空间当common，原161通过/1失败日志保留；按词法声明修正检查后162 unit与Production build通过，未削弱缺词条和机器身份断言。收尾仍须对后续新增/修改再次格式化，再运行受影响回归和独立补审；提交需要另行明确授权。

同一格式化候选16类完整套件105项通过（75 unit、30 PG/HTTP，0失败/错误/跳过），293 backend输入前后SHA漂移0。它包含正零负、足额/不足/恰好、原币余额、初始化、在途预留、撮合并发/回滚、撤单及跨进程绑定失败恢复。最初尾零CHECK、facts409及SQL括号500分别保留，最终通过不追认旧轮。见外置task-code-format-verification.json与discount-asset-formatted-suite-final-report.md。

真正独立review-only审查结论REVISION_REQUIRED：合法同availableAt延迟行情使BUY→SELL→BUY在同开盘执行，正式持久成交却按随机ID重排；第五个真实HTTP run回测SUCCEEDED而evaluate409 TRADE_OUTCOME_POSITION_INCONSISTENT，JDBC重读复现相同失败。邻接PnL守卫对相同availableAt仍拒绝RESEARCH_BAR_FACT_TIME_MISMATCH，因此只修成交排序不能宣布研究→评估全链闭合。报告discount-asset-formatted-independent-review.md与原PG/HTTP反例全部保留，不修改原V1—V17。现以新forward V18补真实执行/估值序列；原时间不伪装，旧缺失证据不由UUID或BUY/SELL重排补造。

API和前端新增精确字符串的executionSequence/valuationSequence，成交与优惠币历史直接呈现正式顺序，旧记录缺失保持空值。UI消费同时间及超JavaScript安全整数身份的测试新增后，163项前端unit及新SIM-enabled/Continuous-disabled构建通过；新后端和完整链仍在实现与验证，不借UI通过宣布修复闭合。

原冻结候选真实Servlet/新随机PG的中文1536×1024、390×950确认了原币余额和撤单确认可见、未被遮盖或裁切。完整中英文GUI未通过：先有AntD按钮空格、隐藏辅助option和键盘选择器问题，随后既有十分钟GUI等待到期；整份可选GUI Java运行BUILD FAILURE，晚到浏览器连接关闭fixture得到502。中文局部截图保留，不冒充完整GUI；自有临时proxy13160已核对命令行后停止，原V13验收保持。修复候选收敛后再验证完整GUI。

矩阵由617增至619唯一项，原项全保留，新增真实成交顺序和估值顺序两项IN_PROGRESS。初始未收敛状态不作为实际未实现数量或完成百分比。全站剩余调度/发布验证/Shadow/Continuous SIM、账户及通知GUI、全站E2E/Full Maven、集中复核、指南与真实新包安装/升级/备份恢复资格继续未完成。无commit/push/PR/merge/发行或真实私有交易；U01继续NON_BLOCKING/PENDING_USER_CLARIFICATION。

## 优惠资产与研究顺序隔离安装续接（2026-10-14）

原独立REVISION_REQUIRED与全部失败身份保持。新79业务/393上下文候选的补审PASS_SCOPED，关闭F1/O1；三独立run保留同时间12成交/24PnL，六种顺序故障经正式409拒绝且成功报告完整GET与整行MD5保持；V18升级、旧记录不回填、约束与事务回滚、精确字符串DTO/UI探针通过。作者23类140项0失败/错误/跳过，2124后端输入漂移0；163前端unit及生产构建通过。

真实Servlet/PG与同一Production bundle的cancel/settled两阶段、中英、1536×1024与390×950 GUI完整通过；交易业务写入0、相关控制台错误0，语言保存仅使用新合成用户PUT /api/user/preferences。两轮选择器/偏好计数夹具失败已终止并保留，新随机库第三轮完整Java invocation BUILD SUCCESS，不能追认旧轮。部分即时Tab截图仍可能包含前一个墨迹位置，DOM费用/原币余额断言与窄屏确认截图是联合证据。

现有V13验收库仅备份读取；新随机副本恢复后前向执行V14—V18恰好五项、validate、NOOP和不同JVM重读通过，原90业务表语义摘要全部保持，新增两表为空。随后以已审JAR安装本任务18280/V18及18128生产预览，原48订单/48成交/196账务、账户、凭据与偏好保持，LIVE0、非终态订单0、kill ENGAGED。安装后11项E2E通过，其中3正式接口、8边界fixture，正式偏好SHA前后相等。旧V13制品/备份保留；V18库重启仅用restart-pinned-v18-acceptance.ps1，禁止旧V13包回写新schema。

账本由619增至623唯一项，全部旧项保留，明确补入优惠预算、换算来源、研究原币结算及canonical结算能力。当前279 Java/167前端与配置只读格式稳定检查通过、源码漂移0；功能收敛后仍须再统一格式化所有本任务新增修改代码并验证。全站剩余调度/发布验证/Shadow/Continuous SIM、账户及通知GUI、研究顺序完整浏览器页、全站E2E/最终Full Maven、指南与真实发行包安装/升级/备份恢复资格未完成。整体ACTIVE/PARTIAL；U01保持NON_BLOCKING/PENDING_USER_CLARIFICATION。未执行Git提交、Push、PR、合并、生产发布、真实私有查询或真实交易。

## 调度稳定操作与恢复修复续接（2026-10-14）

新增ADMIN与原用户范围的POST /api/scheduler/operations、GET原UUID回执；配置更新冻结完整输入与原结果快照，单次运行仍消费原调度器/代码门槛/锁。发送前保存原UUID，超时与UNKNOWN只查询；404/STARTING才可明确确认继续原输入，损坏存储与用户切换关闭新提交。旧jobs接口保持。V19在随机隔离证明中已执行，禁止就地改写。

原冻结审查REVISION_REQUIRED的两项真实反例及全部快照保留：完成审计异常误记FAILED；自动不可用跳过清除UNKNOWN原领取身份。修复把业务异常与完成持久化分开，并保护未收敛运行不被skip清除。后续回归又观测同UUID并发409，原失败日志和随机库保留；新候选在冲突事务回滚后只核对已提交原身份，不换编号。

最终14项目标检查全部通过（10真实PG/Servlet、1迁移、3目录），包含八轮每轮六个并发请求、一次性完成审计故障、Clock前移的过期UNKNOWN、迟到原完成，以及真实SQL23505故障后原身份/不同用户/参数校验。新测试库canonical订单/成交/账务均0。16Java/12前端格式AST等价与稳定检查通过；前端源码仍与原171单测/构建的冻结身份相同，可按有效范围复用。原UPDATE_CONTROL回程持有超时与双JVM通过目录保留，不当作新RUN_ONCE补审或正式GUI。

矩阵623→626唯一项，全部原ID保留；启停/运行/间隔写入口映射更新到稳定操作接口。当前是IMPLEMENTED_PENDING_INDEPENDENT_REVIEW_AND_GUI；补审、完整实际GUI、V18备份副本升级V19/重启保持及安装仍未完成。原18280/V18与18128候选保持；全站继续ACTIVE/PARTIAL，剩余发布验证/Shadow/Continuous SIM、账户/通知GUI、研究顺序页、全站回归/Full Maven、指南与发行安装资格继续保留。提交前仍统一格式化所有新增修改代码并验证；无Git提交、发布、真实私有查询或交易权限。U01保持非阻断待澄清。

## 调度定向补审与实际页面续接（2026-10-14）

新修订29业务/467上下文的独立PASS_SCOPED，F1/F2 CLOSED_SCOPED；17真实PG/Servlet场景，4×8同UUID并发各一副作用/审计，14次真实SQL23505故障分支，起止1984次身份检查0漂移。退出后持久重读12COMPLETED/0pending/6效果/0重复。业务抛错、三种pending状态、无pending的旧skip以及owner/input拒绝均按实际范围验证。旧失败与原REVISION_REQUIRED报告保留，不重开已接受优惠资产范围。

同一新候选的UPDATE_CONTROL再执行真实回程持有超时、原GET、原UUID重放与双JVM重读，通过目录scheduler-response-restart-e12d913495094919b79c283d2296319a。实际GUI使用既有正式bundle、当前Servlet/V19新随机PG、仓库Playwright与已安装Edge（Browser插件skill未提供），中英×1536×1024/390×950四链通过：登录声明、编辑保存、启停、单次运行、15s客户端超时后只GET、未发送404后确认继续原UUID/输入。没有伪造业务响应；24持久调度操作、0订单/成交/账务、最终启用0；8条有意网络/404控制台消息已标明，额外框架/console问题0，document宽度均等于视口。临时Servlet与18136预览已关闭。

GUI目录scheduler-operation-gui-47199ddd6cf34b228b8e158a0c34c0bb，20截图及完整事件/持久事实保留。首轮声明弹窗挡住与次轮旧Popover退场定位错误各为独立失败，原截图、脚本和数据库重读已保存，不追认为PASS。已逐图查看代表桌面与窄屏：恢复编号可换行，警告和查询操作可见；发现中文Enabled残留，以及表格横移时名称和动作分离，作为后续真实呈现问题修正。截图即时语言下拉可能仍在退场，不把静态帧作为唯一功能证据。

当前调度状态是LOCAL_SCOPED_ACCEPTED_API_ADMIN_GUI_NOT_INSTALLED，非全页/全站完成。OPERATOR/VIEWER页面、上述布局/中文修正、V18备份副本升级V19/重启保持及安装仍待执行。原V18/18280与18128不变。全站ACTIVE/PARTIAL、Full Maven/全站E2E/发行安装资格和其他已列业务仍未完成；最终统一格式化、注释/import核对与相关验证保留为提交前要求。Git/真实私有/LIVE/交易/生产发布NONE，U01仍非阻断待澄清。

## 调度V19实际隔离安装、恢复与最终格式要求（2026-10-15）

调度当前为LOCAL_SCOPED_ACCEPTED_V19_INSTALLED_REAL_GUI_VERIFIED，全站继续ACTIVE/PARTIAL。响应式和本地化修正复验171单测/正式构建；同一制品12真实GUI链覆盖ADMIN/OPERATOR/VIEWER、中英及1536/390视口，24持久操作、0订单/成交/账务、最终启用0，额外应用console问题0。后端与原17场景定向独立接受的实现保持一致；普通UI修正未重开后端审查。临时预览/Servlet已关闭；环境重启中断的IAB helper只记录持久重读，不虚报完整生命周期通过。

第一次安装前检查失败保留。只读归因证明原1111净值样本没有修改或丢失，运行中的十五分钟采样合法新增派生记录，另外91表摘要保持；不跳过原比较或删除样本。精确暂停本任务V18进程后建立新随机完整备份，副本V18→V19只执行一条迁移，validate/重复NOOP/另一JVM读取通过，再用同SHA可执行JAR在该副本两次完整启动保持原92表。实际安装也逐表保持全部92原表，新操作表为空。证据以scheduler-v19-stable-fc82b5cc774f4a2f9c8e9329c909977a开头；旧失败、旧备份与旧副本保留。V1—V19均已执行，后续仅可新增V20及之后迁移。

安装时暂缓采样以核对不可变快照，核对后已用固定V19制品恢复原采样。新验收启动记录v19-acceptance-start-31bfd0678d8a44cc8a43a8be18460558.json：后端54928、前端52360，18128/18280/15442，schema19；JAR SHA CADC155B38ED260A73C8FCB4145197C41B5E5A8EFF3ACF7E3CF2142A6266E0D9。原1125样本全部保持，真实下一个采样桶新增7条；48订单/48成交/196账务、操作0、启用0、LIVE0、GLOBAL_TRADING ENGAGED保持。先于下一个时间桶的无新增检查原记录保留，不改时钟或手写样本制造通过。restart-pinned-v19-acceptance.ps1只启动同SHA固定制品，不重置数据；旧V18恢复脚本不可用于V19库。

安装后的首轮4项回归有两个失败：误用默认登录信息，及fixture仍断言旧ON文本。原日志和快照保留；改用既有合成验收账号，并把fixture断言更新为本地化状态及键盘展开的原始状态。未修改产品代码或接口。随后15项全部通过（4正式、11fixture），用户偏好保持；恢复采样后1条正式调度只读链再次通过。source输入与已安装制品、后端审查及GUI证据分别绑定，测试适配不是重新实现后端。

用户再次明确：全部开发完成后、提交前，统一格式化本任务新增/修改代码。当前只读检查292 Java/172前端，前端格式稳定，AuthSecurityWebMvcTest.java与BacktestEvaluationService.java仍待最终整理；并非全代码格式已完成。最终按最新精确清单备份、格式化、检查语义/差异/中文注释与显式import，并完成相应验证；执行过的SQL迁移和冻结证据不就地重写。Git提交仍需单独授权。

626唯一映射及全部原ID保持。本轮只更新有实际证据的调度条目；ACTION-0139的正式链接/真实目标页面/0写请求和ACTION-0140的503旧行保持→显式重试200恢复/0操作写入已补验，4目标用例全部通过；重试为边界fixture，不冒充业务成功。首轮重试选择器未处理Ant Design中文按钮间距的失败保留。其余发布验证、Shadow、Continuous SIM、账户凭据完整GUI、通知、研究顺序浏览器页、全站E2E/最终Full Maven、指南/源码双导出、实际发行包安装/更新/恢复资格继续实施。真实私有来源未授权/未验证，不能冒充已验证。U01继续PENDING_USER_CLARIFICATION/NON_BLOCKING；无Git提交/发布、真实私有查询、LIVE或交易操作。

## Shadow只读列表、详情返回与真实空态（2026-10-15）

当前为LOCAL_UI_SCOPED_VERIFIED_INSTALLED_REAL_EMPTY_AND_FIXTURE_PENDING_FORMAL_CREATED_RUN，全站仍ACTIVE/PARTIAL。真实缺口是固定前50条/无分页、详情返回丢失筛选及焦点、原始状态横排和满屏技术说明；现已接正式status/limit/offset分页与URL查询，非法状态/页码明确拒绝，不读另一组记录。桌面收紧为记录/版本/复核提示/时间/行操作，窄屏同一对象保留全字段和操作；原始状态、数据集/SIM关系、trace、边界和全部时间在可展开来源明细中保留。复制使用已脱敏显示值，展开和复制不因事件冒泡误跳详情。返回/浏览器后退保持筛选、页码、实际滚动和UUID焦点。

正式V19零Shadow事实的UNAVAILABLE表示没有诊断证据；已沿当前Java查询服务定义识别这一精确分支并显示“尚无诊断证据”，读取失败仍为错误，原始元数据保留。IAB实际核对当前认证、1536×1024和390×950同一空库页面，应用console问题0；原页面与新页面同尺寸比较及图片保留。首次resize采到菜单退场动画，等待完成后的confirmed图才作为窄屏证明；未把尝试关闭已退场按钮的错误当产品缺陷。当前真实库不插入假Shadow记录。

3项相关浏览器fixture链通过，覆盖61条/第2页、返回滚动/焦点、390文档宽度、无效URL零列表GET、原状态/来源、读错误/404、事件/快照/一致性及敏感字段。复制提交在测试页拦截，既未读取也未覆盖用户剪贴板，验证复制UUID和不误导航；不冒充真实Shadow生命周期。原虚拟下拉窗口无法重新定位ALL的失败保留，固定11项选项改为完整可访问DOM后通过。174前端单测及构建通过；9文件范围格式稳定，最后的测试格式整理AST等价。格式比较器首版误把SourceFile.text当语义的失败及原helper保留，纠正后验证；全任务最终格式化仍未完成。

冻结shadow-list-ui-verified-input.json，9源文件/93 bundle文件；已安装18128，前端PID58376，后端仍54928/V19/JAR CADC155B38ED260A73C8FCB4145197C41B5E5A8EFF3ACF7E3CF2142A6266E0D9，不重启后端或迁移。48订单/48成交/196账务、Shadow0、操作0、启用0、LIVE0、GLOBAL_TRADING ENGAGED保持。安装后15项相关回归通过（4正式/11fixture）且偏好完整摘要保持；恢复脚本使用最新冻结UI pin并实际Start-only核验无新进程。临时18135及本轮创建的IAB页已关闭，视口已恢复，REPL内合成登录材料已清除；实际安装页再次检查通过。途中原18128会话正常过期，按正式登录和声明流程恢复，不重设密码；生命周期证据shadow-list-ui-lifecycle.json。该检查不是发行安装资格。

矩阵626→629唯一项，原ID全部保留；只登记实际覆盖的只读页面/动作及新增分页/返回/源状态。STATE-0145—0150完整状态分支仍独立待验。发布准入/真实CREATED创建及业务事实链、创建命令的正式结果查询和刷新恢复、Continuous SIM、其余页面、全站E2E/最终Full Maven/用户指南和双源码导出/发行安装更新恢复继续未完成。U01非阻断待澄清；没有Git提交/发布、私有联网、真实凭据或LIVE交易授权。


## Shadow 原创建结果查询候选与提交前格式要求（2026-10-15）

全站仍 ACTIVE/PARTIAL。本轮继续用户既有目标，新增 GET /api/strategy-releases/{publishRecordId}/shadow-run-creation-result，原编号继续使用 Idempotency-Key 请求头；创建和查询共用原长度前缀 SHA-256 算法，有效 ASCII/Unicode 固定向量保持，拒绝不完整 UTF-16 编码碰撞。查询只读原 Shadow 主事实及最多两条 CREATED 审计，核对原发布、原操作人、来源和无副作用证据；当前诊断状态与原 CREATED 结果分开。当前准入变化不重新生成命令，其他操作人（包括管理员）无权读取；缺失/重复/不一致审计返回不可用，404 不证明命令从未执行。新增路径保持 ADMIN/OPERATOR 过滤器边界与服务端主体检查，不新增事实表或 migration。

本批 93 项定向测试全部通过：新应用单测40、有界SQL交互2、MVC/过滤器7，以及44项受影响回归。原首轮 core 无 Mockito 依赖和第二轮误用 SUCCEEDED 枚举的编译失败保留；已改为仅允许两项读取的 test port，并使用正式 COMPLETED 状态，没有新增依赖或削弱断言。13文件经既有 Java AOSP 规范整理，格式稳定、diff --check 通过，正文全限定类型0，新增注释正文使用简体中文。正则扫描不支持 lookbehind 的工具失败未视作通过，修正扫描后才记录结果。

这是 BACKEND_ONLY_LOCAL_TARGET_TESTS_VERIFIED_NOT_INSTALLED，不能当作真实 PG/HTTP/重启证明或创建恢复已完成。下一步继续隔离 PostgreSQL、原正式创建事实、跨进程/HTTP证明，再接前端原编号/原输入/原主体持久恢复与一次独立定向候选审查。当前 V19 后端54928、前端58376保持运行；新候选未安装，未改数据库或现有验收事实。

用户要求的最终统一格式化保持待完成：全部开发结束、提交前，对本任务最新精确清单备份并统一整理缩进、换行、import、长参数、TSX/CSS及中文注释，检查语义与差异并完成必要验证；已执行SQL迁移、冻结证据与无关用户改动不就地重写。AuthSecurityWebMvcTest.java和BacktestEvaluationService.java的已知最终格式欠项仍保留。当前按剩余工作初步预留2—4个完整工作日，尚不能承诺具体交付时刻；629条是功能/状态追踪而非完成数量。Git NONE，U01 非阻断待澄清。

## Shadow原创建结果、刷新恢复和关联详情局部接受（2026-10-15）

当前 LOCAL_SCOPED_ACCEPTED_REAL_GUI_VERIFIED_NOT_INSTALLED，全站仍 ACTIVE/PARTIAL。原创建编号、发布和主体在提交前可靠保存；刷新不自动POST，未知结果先查询，404不能证明未执行，显式再次确认才复用原编号。Web Locks协调同主体跨标签；身份/权限变化、损坏存储和503保持原记录并关闭另起创建。正式GET只读原Shadow主事实及最多两条CREATED审计，REPEATABLE_READ/只读事务；原创建状态与当前诊断状态分开，不重新跑当前准入或启动运行，无新迁移。

作者93目标后端测试及真实Servlet/PG/两个JVM证明通过；前端196单测与16相关浏览器fixture通过，fixture不冒充正式业务。独立审查的59后端、1键向量、22恢复单测、原生跨标签中断及真实双JVM通过；原REVISION_REQUIRED只发现结果详情链接404。保留失败报告后，仅两文件修复并补实际点击/回退；新Production bundle独立补审 ACCEPTED_SCOPED_F1_SUPPLEMENT，F1 CLOSED，另外23候选与2584原上下文不变，原安全恢复证据按身份复用。

修复后真实GUI使用新随机库、正式登录和Servlet：已提交POST丢响应→刷新→原GET→点击UUID→真实详情200，id/publish/CREATED/startedAt=null匹配→返回原发布上下文。全链只有一POST、一主事实和一CREATED，订单/成交/账务/启动均0；新浏览器证据目录shadow-result-link-formal-gui-c24ad3c92b274ccab1bf58047826b186。额外应用错误0，只有有意丢响应的ERR_FAILED和无报告接口404；截图部分保留前一滚动位置，仅用于证实真实内容，不能当作详情布局全验收。完整详情呈现与STATE-0145—0150分支仍待继续。

新可执行本地JAR已构建并固定在runtime/nq-app-shadow-recovery-v19-20261015-091200.jar，包含新查询Controller；skipTests package只是制品构建，不是Full Maven。本轮新候选尚未替换18128/18280：仍为58376/54928、schema19。临时Servlet/18137、作者18136/18138和独立18144/18145均已关闭；当前原验收环境UP/HTTP200。下一步安装已审新制品、验证原事实保持，再继续其余正式页面。

矩阵629→633，全部原ID保留；新增原结果查询、持久恢复、结果导航及未修纳秒窗口问题，原四个组件映射到当前源位置。纳秒输入超出PG微秒而首次CREATE冲突的原反例不虚报修复；当前正向仅证明已确认的时分秒输入。全站E2E、最终Full Maven、发行安装更新恢复、指南与源码双导出仍待完成。

用户要求保持：全部开发结束后、提交前，统一格式化本任务全部新增/修改代码，检查显式import、短类型名和中文解释性注释，完成语义及相关回归。最新只读清单305 Java/187前端与配置，前端稳定；AuthSecurityWebMvcTest.java、BacktestEvaluationService.java仍待最终整理。不能以局部格式或本轮两文件整理代替最终全任务格式化；执行过的迁移和冻结证据不就地重写。完成时间仍粗估2—4个完整工作日，未承诺具体时刻。Git NONE，U01非阻断待澄清；真实私有/LIVE/真实交易/生产发布未授权。

## Shadow恢复候选V19隔离安装与原数据保持（2026-10-15）

已按review-shadow-result-link-revision-input.json固定的25源文件、93 bundle文件和可执行JAR安装至18128/18280。新后端56200、前端20316，数据库仍V19；本轮零迁移。短暂停止原采样进程后建立稳定完整备份，在自有随机副本恢复并逐表精确比对；新JAR两个不同JVM启动后均保持全部93业务表及Flyway历史（合计94表），实际安装后暂停采样时再次精确比对通过，之后恢复原真实采样。备份、旧制品和全部证据保留，成功副本按精确身份删除。

证据目录shadow-recovery-v19-install-67cac9ff15ac4c7f94f869238fefef65。原48订单/48成交/196账务、Shadow0、操作0、启用0、LIVE0、非终态订单0和GLOBAL_TRADING ENGAGED保持。新JAR SHA a769b9243b9656ca975a64947701f162cdd76d4bbc588711715359d2bac64c24，UI index SHA da884fa713dcad847e5968bb9b1e16f715be5c5951f982159f3b5f54d1f327f7。只读摘要不改数值/空字段；采样恢复后的合法新增不伪装成历史快照完全不变。

安装后15项相关E2E通过（4正式只读、11边界fixture），正式偏好完整HTTP摘要前后相等。新查询路径实际匿名401、缺原编号400、原主体不存在结果404并no-store，无POST；正向恢复/详情导航仍按同源已冻结候选的真实随机PG/Servlet证据复用，不能把当前Shadow0的验收库写成正向创建已发生。新后端和UI pin已更新；restart-pinned-v19-acceptance.ps1校验新制品SHA和schema，实际Start-only核验startedBackend/startedFrontend均false，不退回旧V19界面或后台。

当前为LOCAL_SCOPED_ACCEPTED_V19_INSTALLED_RELATED_UI_VERIFIED，全站继续ACTIVE/PARTIAL。矩阵633项、全部ID保持；完整Shadow详情呈现和六种诊断分支、纳秒窗口反例、Continuous SIM、其余页面、全站E2E/最终Full Maven及正式发行安装更新恢复资格仍待继续。最终全任务格式化、中文注释和import检查继续保留；U01非阻断待澄清，Git与真实私有/LIVE/真实交易/生产发布均未执行。

## Shadow详情业务分区、真实CREATED与隔离界面更新（2026-10-15）

当前LOCAL_UI_SCOPED_VERIFIED_V19_INSTALLED，全站仍ACTIVE/PARTIAL。详情不再把全部技术字段和说明纵向铺满首屏：当前运行状态在标题旁，概览呈现运行/评估/版本/数据集/发布/SIM与观察窗口，诊断摘要只使用同一运行的正式读模型。桌面左右布局，390先显示诊断结论；事件、快照、报告与全部原始字段/追踪/边界分别可进入，原脱敏、排序和六分支规则保持。缺失/串对象摘要不补零；能力true/false/unknown按真实字段显示，异常标记不能被静态“已禁用/未实现”掩盖。

view分区写入URL，刷新恢复；切换保留原查询、hash和列表返回state。非法/重复分区明确报错且不请求事实，大小写UUID仍核对同一身份。原首轮28/29中的键盘精确名称失败保留；Ant Design附加可访问序号后按真实焦点确认，未删除键盘断言。随后用旧制品复现hash丢失，保留反例后修复。最终199单测、类型/构建及30相关浏览器fixture全部通过；这些不冒充真实业务状态全部形成。

新随机PG、正式登录和Servlet的实际GUI通过全部六分区：正式POST提交后丢响应→刷新→原GET→同UUID/发布/CREATED/startedAt=null→六区读取→返回原发布上下文。只有1POST/1主事实/1CREATED，订单/成交/账务/启动均0；中文1536×1024/1366×768/1920×1080/2560×1440/390×950和英文1536/390共7检查，文档宽度均不超过视口。新随机用户中英文各保存一次，正式偏好PUT均200，未改原验收用户。证据shadow-detail-workspace-formal-gui-bounded-a4c2fed7db9041d78a2a6fded6c93cf8，成功临时库与进程已清理。截图使用已结束布局，部分截图关闭有限动画以避免过渡帧，不把DPR当原生缩放。

两次容器CLI只读调用卡住分别保留记录；精确结束各自客户端，未重启Docker、容器或重置数据。正式GUI改用仅允许新随机库的有界JDBC创建/读取/清理，服务端业务路径不变。恢复脚本原schema/LIVE/非终态订单/调度/kill条件保持，改用固定目标、只读事务、有连接和查询超时的JDBC；安全类hash错误负例拒绝，真实Start-only成功且两个started标记false。该工具修正不是交易能力或发布授权，Docker CLI根因未诊断。

冻结8源/94 bundle，已替换18128前端为PID38080；后端仍56200、同SHA固定Shadow恢复JAR，数据库仍V19，无后端改动/迁移。UI index SHA A4BEEB78E55711A2238924F590F414C0C770CC5445B1FA10625409DD254BA759。安装后15回归通过（4正式/11fixture），原用户偏好摘要相等；14张经济/账户凭据/偏好/调度/Shadow关键表的行数和精确摘要与稳定安装前相同，含48订单/48成交/196账务/Shadow0，采样保持启用。所有本轮18138/18139/18140临时预览已关闭，新UI pin及恢复目标正确。

矩阵633→636，全部原ID保留，STATE-0145—0150纠正为服务端诊断分支而非用户手动选择状态。详情刷新按钮的独立正向点击、英文390溢出菜单直接操作、原生125%/150%与真机触摸仍待补；纳秒窗口缺口、Continuous SIM及其余功能、全站E2E/最终Full Maven、最终联合独立复核、指南、源码双导出及发行安装更新恢复资格继续未完成。当前UI属普通定向自查和相关验证，不冒充新一轮独立全站接受；既有后端审查按2249未变上下文复用。

最终格式要求继续待完成：本批8文件已稳定格式化，最新全任务只读清单305 Java/190前端与配置，前端稳定、源码漂移0；AuthSecurityWebMvcTest.java和BacktestEvaluationService.java仍待最终整理。全部开发结束、提交前仍须按最新完整清单统一格式化、中文注释/import核对与必要回归。U01非阻断待澄清，Git NONE，无真实私有、LIVE、真实交易或生产发布。

## Shadow独立刷新、窄屏菜单补验与格式化约定（2026-10-15）

当前仍ACTIVE_PARTIAL。本轮仅新增测试和证据，7项未变源、94 bundle及2249后端上下文核对一致，不更换18128/18280制品或V19 schema。两项fixture通过：详情刷新原5项GET/同一UUID/零写；英文390实际更多菜单Sources→Overview。原r1误点已溢出标签、r2误认为默认概览保留view=overview的测试失败分别保留；按正式shadowDetailSearch合同验证默认概览移除view，无强制点击或产品修改。目标测试稳定格式、diff检查通过。

新随机PG/正式登录/Servlet补验shadow-detail-interaction-formal-gui-fb2a653160f041549c72bf089920361d：发布准入“刷新预览”恰好1GET并返回原发布，详情“刷新只读数据”恰好5GET并返回同UUID/发布/CREATED/未启动。英文390通过原生更多菜单进入来源、返回概览；中英文7个既有尺寸检查通过。全创建恢复链保留1POST、1主记录、1CREATED，订单/成交/账务/启动0，应用异常0（有意丢响应与缺失报告404单独保留）。新主体语言偏好正式PUT各200，未动原验收主体。成功临时库及Servlet/18137 preview已清理，原验收UP/HTTP200、PID56200/38080保持。

636映射及全部原ID保留，只更新ACTION-0152和原184刷新预览组件；不把这两项完成写成全站完成。后续仍有纳秒窗口精度、Continuous SIM、其余完整GUI路径、全站E2E/最终Full Maven、独立联合复核、指南/源码双导出/发行安装升级重启恢复。U01继续非阻断待澄清，无Git/私有/LIVE/真实交易或发布操作。

用户再次要求提交前统一格式化：全部开发完成后，按最新精确范围备份并整理全部新增/修改代码，检查中文解释性注释、显式import/短类型名、语义和差异，完成相关回归；已执行迁移和冻结证据不就地重写。最近只读范围305 Java/190前端与配置；已知两项Java整理欠项继续保留，最终全任务格式尚未完成。当前粗估仍需2—4个完整工作日，受剩余实际缺陷和发行验证影响，不能承诺具体交付时刻。Git提交需单独明确授权。

## Shadow观察窗口纳秒精度候选（2026-10-15）

当前LOCAL_SCOPED_22_TESTS_VERIFIED_PENDING_INDEPENDENT_REVIEW_NOT_INSTALLED，全站ACTIVE_PARTIAL。根因为Java冻结Instant保留纳秒，PG timestamptz仅微秒；严格provenance首次回读会误报冲突。新增V20保存开始/结束微秒以下余数（0—999），写入前取微秒，详情/概览/一致性三个JDBC读取方统一还原。完整Instant幂等比较不放宽，1ns差异继续冲突；null/equal旧窗口兼容，原列和V1—V19不改。新应用须先迁移V20，旧应用不能消费新纳秒窗口，不宣称混合版本或退回旧JAR兼容。

22定向全部通过：15单测（含正负纪元全部余数）、6真实PG（fresh、V19升级、旧列逐值保持、全部读取方、1ns冲突、约束负例、既有原子/并发/来源矩阵）、1正式Servlet跨两个JVM（原纳秒输入创建、重放、丢响应后原GET、准入变化/来源离线后恢复、权限与损坏审计拒绝、持久摘要零查询副作用）。证据shadow-window-precision-proof-1bc51fe4bdd34884ba9d508fce877fd9，成功临时库已清理。首轮旧夹具V19断言和schema与基线public函数不兼容的失败保留；仅测试环境改新随机数据库与V20，未改V1。原始纳秒失败仍保留，不把后续修复追认为旧候选通过。

冻结review-shadow-window-precision-input.json的10候选/2256上下文后进行一次真正独立审查，期间不改候选。8 Java稳定AOSP格式、显式import与中文约束注释；这不是最终全任务格式完成。原18128/18280/15442验收保持V19、同固定制品，没有迁移或安装本候选。发行安装升级重启恢复、全站完整GUI/E2E、最终Full Maven与其余交付继续待完成；U01非阻断待澄清，Git/真实私有/LIVE/交易/生产发布均未执行。

## Shadow精度候选独立接受与固定JAR界面验证（2026-10-15）

LOCAL_SCOPED_ACCEPTED_V20_CANDIDATE_NOT_INSTALLED。独立报告shadow-window-precision-independent-review.md接受同一10候选/2256上下文，BLOCKING/NON_BLOCKING均0；独立22目标通过，另1自有PG探针通过真实锁超时5056ms/事务回滚无半升级、升级validate与旧列/历史逐值保持、relfilenode保持、8并发同编号仅1行、1ns冲突、取消及陈旧版本拒绝后三正式读取方完整窗口和partial-null兼容。全部成功自有随机库清理，未接原验收业务库；源/补丁/19份历史迁移收尾身份保持。

主代理同一reactor构建可执行JAR nq-app-shadow-window-v20-85cdfd6b0d9e.jar，SHA256 39ff5a93b21c05a356fef43ec55f0e1a8ed254ed23cf1776b225cd49c9a66c1f，核对Boot launcher、四个变更类及V20嵌入资源。构建为skipTests包装，不替代Full Maven。实际JAR的新随机库GUI证据shadow-window-precision-jar-gui-62a0f8800ec74b3485e5f4a91ce443b2：准备合成输入的夹具先停止，随后正式登录/创建丢响应/原GET/同对象详情/刷新/六分区/英文390菜单由固定JAR处理。start与end精确保留567325500和999999999纳秒，1POST/1主事实/1CREATED，订单成交账务启动0，应用额外异常0；新主体语言偏好各200。成功临时库、18146 JAR和18137 preview已清理，已查看桌面与英文窄屏代表图。这是作者包装/GUI补证，不冒充独立重跑或发行资格。

当前原验收仍V19/PID56200和38080，schema19、LIVE订单0、非终态订单0、启用调度0、ENGAGED有本轮有界只读证据；未安装V20，旧V19脚本不得被当作V20恢复入口。下一步按已授权隔离安装推进，先备份和新副本恢复/升级/重启证明，再更新固定运行与恢复pin；不因PG17主机工具存在就假定兼容PG16，后续须匹配版本或真实验证。当前只读格式清单310 Java/190前端，前端稳定、源码漂移0；Java仍两处已知欠项。最终全任务格式与相关回归仍待全部开发后完成，U01非阻断待澄清，Git NONE；全站ACTIVE_PARTIAL。

## 固定V20本地安装、PG16备份恢复与原事实保持（2026-10-15）

当前LOCAL_SCOPED_ACCEPTED_V20_INSTALLED_RELATED_UI_VERIFIED，全站ACTIVE_PARTIAL。固定JAR nq-app-shadow-window-v20-85cdfd6b0d9e.jar（SHA 39ff5a93b21c05a356fef43ec55f0e1a8ed254ed23cf1776b225cd49c9a66c1f）已装18280，后端PID18880；前端PID38080和原94文件bundle保持，数据库由19升级20，仅新增V20。636映射及原ID保持，只将精度项更新到本地安装验证；V1—V20已执行不可改写，之后新增迁移从V21起。

工具先行证明保留：Docker CLI及引擎管道只读查询有界超时，未重启Docker或容器。主机PG17→PG16恢复因transaction_timeout真实失败，未忽略错误；改用EDB公开HTTPS来源、固定SHA的任务目录PG16.11便携客户端，两个新随机PG16库实际备份恢复/数据比对0退出，通过后才碰原验收。无系统安装。PG17失败库/日志及原失败证据保留。

首轮安装在副本成功迁移后，no-outbound guard发现继承的真实公共URL而关闭应用；原库保持V19并固定恢复，原前端未动。仅终止恢复完成后仍等待继承stdout的本轮Python收集器，不终止恢复JVM。新运行身份修正副本URL为disabled，保持禁止外联校验；恢复日志用文件收集避免等待后台JVM管道EOF；健康验收增加正式readiness，不降低安全规则。旧候选和失败不追认为通过。

成功证据shadow-window-v20-install-0302d5396ff546069eb00015bef53109：原后端静止后捕获94表完整旧字段及逐条原净值样本指纹，PG16 custom备份SHA 8fe5a4278c452582e02e5e71d696b381636cdb123f54f9cf3a71610b3250f18a。新随机副本恢复后完整V19摘要相同；固定新JAR两个不同JVM升V20并validate，94表旧字段及19条旧Flyway记录一致，新列/唯一新迁移单独核对。修改副本一个旧偏好值的比对器负例被拒绝，副本按精确随机身份删除。原库随后升级，先停采样证明全部旧事实保持，再恢复采样；安装后15浏览器回归（4正式/11fixture）与偏好完整HTTP摘要保持，回归后再读94表旧事实相等。原样本1447条全部保持，当前1447条；允许真实采样追加，不把新样本删除或补值制造静态相等。

原48订单/48成交/196账务、Shadow0、调度操作0、启用0、LIVE0、未终态订单0、ENGAGED保持。新的backend/UI pin指向实际安装输入与同一兼容UI，旧输入、备份、制品不覆盖。restart-pinned-v20-acceptance.ps1实际Start-only通过、两个started标记false；错误backend pin SHA及错误安全读取器SHA两个负例均启动前拒绝，原pin和进程不变。旧V19恢复脚本不适用于V20；不得回退旧JAR消费新纳秒窗口。

这不是全站完成或正式发行资格。后续继续Continuous SIM、其余账户凭据/通知/研究GUI、全站E2E/最终Full Maven、联合独立复核、指南/双源码导出/实际发行安装升级重启恢复。最终全任务格式化仍待开发结束后执行；最新310 Java/190前端检查的两处Java欠项保持，代码上下文未变可复用。U01非阻断待澄清，Git NONE，无真实私有/LIVE/交易或生产发布。


## Continuous SIM只读工作区进展、剩余时间与最终格式要求（2026-10-15）

全站仍ACTIVE_PARTIAL。当前源码开始独立只读进度工作区：关闭连续执行后仍走原游标/最近决策读取，先检查原运行可见性；无游标、失败、未知状态和串对象分开。UI呈现原已处理/下一预期/最新观察K线、轮询/成功/缺口/失败次数及可折叠来源；父SIM运行与连续游标状态不混同，能力标记不代表调度启用或用户授权。新源码尚未独立审查和安装，原固定V20后端及既有UI继续使用。

4项目标后端单测、前端202单测和type/build通过。本轮新增1项真实PG/Servlet/双JVM证明，目录continuous-readonly-http-proof-c7bca860fad141d8a4914914c7c0b7ee：正式研究→回测→评估→发布→普通canonical SIM，连续执行关闭时owner空游标查询成功，匿名/其他owner被拒绝，暂停/恢复明确拒绝，七张业务表完整行指纹在读取/拒绝命令前后相等；重开第二JVM再次证明。原正费用canonical撮合/资金/账务断言继续通过；新随机控制与业务库清理，V1—V20摘要不变，无原验收业务库连接。该证明不代表已保存真实连续游标、创建/暂停/恢复未知结果或完整浏览器通过。

稳定命令编号、持久回执与UNKNOWN恢复、已保存真实连续游标的读取、完整浏览器及定向独立候选审查仍待完成；不能因只读面板或模拟成功把Continuous计为全部完成。功能矩阵636→637，全部旧ID保留，原3项连续动作从未定位改为真实接口/状态映射且明确IN_PROGRESS。全站账户/凭据等完整GUI、全站E2E/最终Full Maven、发行包fresh/升级/重启/备份恢复、指南与双源码导出也继续待完成。

用户再次强调全部开发完成后、提交前统一格式化。最新只读范围314 Java/194前端与配置；前端格式稳定、源码漂移0，Java仍有AuthSecurityWebMvcTest.java和BacktestEvaluationService.java两个工具检查欠项；这不等于最终全任务格式化完成。收尾按最新精确范围备份后整理缩进、换行、import、短类型名、长参数、TSX/CSS和中文解释性注释，做格式稳定/语义/差异检查并完成相关回归；已执行迁移和冻结证据不就地重写。当前剩余粗估2—4个完整工作日，仅为工作量估计，取决于实际集成与发行验证发现的问题，不承诺具体交付时刻。Git NONE；U01非阻断待澄清；无真实私有、LIVE、真实交易或生产发布授权。


## Continuous SIM稳定命令、原结果及V21隔离证明（2026-10-15）

全站仍ACTIVE_PARTIAL。新本地候选为BACKEND_SCOPED_VERIFIED_FRONTEND_RECOVERY_PENDING，尚未独立审查和安装。POST /api/paper-trading/strategy-sim/continuous-operations由原认证主体提交UUID、CREATE/PAUSE/RESUME及原发布/预算或运行；GET /{operationId}只给原用户查询原完成快照，其他用户含管理员不可读。旧创建/暂停/恢复URL和Status响应保留，但必须携带UUID Idempotency-Key；无编号400，损坏编号400，不进入业务。前端旧handler和可选C3测试旧HTTP helper仍需接新合同，因此此后端候选不得与旧UI组合安装。

原业务修改、不可变回执和完成审计在同一事务提交；只使用既有SIM、独立资金与游标，不另造交易事实。请求按主体、动作、完整输入及规范十进制预算取长度前缀SHA256，同编号不同输入/主体拒绝；并发编号使用事务锁。暂停/恢复的运行锁保持至事务提交，原轮询session锁与其互斥。原CREATE/PAUSE快照不随后来的恢复、轮询或执行开关变化改写；查询不重跑准入。404不是未执行证明，网络未知须保留原输入/编号，先查询，显式继续才可重用原编号；前端持久恢复仍待实现。

continuous-operation-http-proof-6ebdf3d2c90748f2a6adadd6241714ef通过13目标单测及3项真实PG/Servlet/双JVM与受影响费用证明；另外11项错误身份/方法/敏感校验合同回归通过，共27定向目标。命令证明包含8并发同UUID只1运行/回执、原结果重放、另一owner/管理员拒绝、原暂停快照与当前已恢复状态分开、回执写入故障后账户/资金/父运行/游标/回执摘要保持、提交前真实锁等待及轮询互斥、不可变UPDATE/DELETE拒绝。第二JVM关闭执行后仍读取原回执和已保存真实游标，重复原CREATE仅返回原结果，新恢复命令拒绝；订单/成交0、ENGAGED保持。合成冻结研究记录仅是命令证明的前置，不能当作上游研究/回测/发布GUI已验证。

V20→V21另用原Java服务建立真实连续游标与独立资金后停止原JVM。持有users真实排他锁时V21约5218ms失败并完整回滚、无新回执表；释放后只执行1条新迁移，validate及重复0迁移通过。94张旧表所有旧业务列/原20条Flyway记录逐行指纹保持；新JVM关闭执行后仍返回原游标，相关经济事实不变。成功随机库及子进程清理，V1—V20源码摘要不变；当前原验收仍固定V20、18880/38080、UP/HTTP200。V21仅在隔离候选库执行，未迁移原验收库。

原失败证据分别保留：aa44b5916f094af682f8c986c385b7fb为缺必填header被通用异常误报500，新增精确400处理与绑定前零业务调用测试；72cf7919abf54066bc4b0c9b94531381及92d8642500474174ab323548675e3202为合成前置缺少dataset市场身份，补齐测试输入，未放宽SIM_SCOPE_OR_VERSION_INVALID正式guard。原失败不追认为通过。错误合同和现有正费用canonical撮合/账务回归在新候选通过，不冒充全量正/零/负费用重跑。

矩阵637→638，原ID全部保留，连续3动作明确为后端局部通过/前端恢复待完成，新增原结果查询映射。之后继续前端发送前持久保存、刷新只查、主体变化/跨标签、404/503及显式同编号恢复、完整中英/窄屏GUI，再对完整高风险候选做一次定向独立审查；全站其余页面、Full Maven/E2E、交付指南/源码双导出/正式发行资格及最终全任务格式仍未完成。最新只读格式323 Java/194前端与配置，前端稳定、源码漂移0，仍两项原Java整理欠项；本轮新增代码已按现有AOSP整理并使用显式import/中文约束注释，不等于最终全任务格式完成。Git NONE，U01非阻断待澄清，无真实私有/LIVE/真实交易或生产发布。


## Continuous SIM持久恢复前端局部验证与最终格式要求（2026-10-15）

全站仍ACTIVE_PARTIAL；当前候选RECOVERY_UI_SCOPED_VERIFIED_FORMAL_GUI_REVIEW_INSTALL_PENDING。原V20后端与已装UI未替换。前端已接正式连续操作合同：发送前按owner持久保存并回读原UUID及完整输入，原生Web Locks协调跨标签；存储不可用/损坏/静默写失败关闭提交。重开只GET原结果，核对原主体/输入/结果身份；404不证明未执行，只有同会话查询后明确确认才允许继续原编号，503或会话变化撤销此前许可。原完成快照只促使重读当前事实，不覆盖当前游标；换对象不抢焦点，未确认命令存在时关闭另起相关动作。

continuous-recovery-unit-final.log为44文件223单测通过；continuous-recovery-build-final.log为type/build通过，生成continuous-recovery-r2-dist，未安装。continuous-recovery-query-e2e-r3.log为8项API夹具浏览器通过：只GET恢复、原CREATE不覆盖已暂停状态、404取消/同编号继续/丢响应/503、原生跨标签互斥、主体与角色/坏存储、进度分支、英文390完整时间与UUID、既有状态隔离及损坏通知边界。此8项不能当作正式Servlet/PG连续业务GUI证明。既存AntD弃用/React19兼容及构建大chunk警告保留，不能宣称console零警告。

真实浏览器发现StrictMode中effect触发mutation会丢失错误观察/保持pending，已改为可取消、有15秒总期限、无自动重试/窗口重查的useQuery；原失败和诊断目录保留。另发现损坏通知分页使items.map崩溃，正式读取边界现校验分页/50项上限/来源/时间/重复ID，失败显示读取错误，不用空列表伪装无通知；新增3单测及1浏览器边界用例，不代替通知完整GUI。可选C3 HTTP消费者已接稳定header，continuous-c3-header-test-compile.log仅证明编译，未运行其GUI。

矩阵638旧ID全部保留，连续3动作、只读区及原结果区更新为前端局部验证、正式GUI/独立审查/安装待完成。接下来用当前Production bundle与正式Servlet/新随机PG库验证丢响应查询恢复及真实资金/游标/回执事实，然后定向独立审查、隔离升级安装；全站其余功能、发行资格和最终全任务格式仍未完成。最新只读格式范围324 Java/200前端及配置，前端稳定，源码漂移0；Java待整理AuthSecurityWebMvcTest.java、BacktestEvaluationService.java、StrategySimPostgresIntegrationTest.java。全部开发结束后、提交前按最新精确范围统一格式化，并核对显式import/短类型名、中文说明性注释、语义差异和受影响回归；历史迁移/冻结证据不重写。Git NONE，U01非阻断待澄清，无真实私有/LIVE/真实交易或生产发布。


## Continuous SIM正式浏览器与双JVM局部证明（2026-10-15）

continuous-recovery-formal-gui-eae3412ea1d64bbab7905446941ffcc1使用固定continuous-recovery-r2-dist、正式登录/Servlet和新随机PG16库通过。真实CREATE事务完成后仅在浏览器端丢弃响应，首次查询也模拟网络失败；重开只GET原编号恢复，创建POST全链仅一次。实际pause/resume各用新的UUID；PAUSE后GET原CREATE仍与原快照完全相同。英文390与中文1536呈现、完整时分秒、来源展开和无整页横向溢出通过；Product bundle没有开发版AntD告警，create仅有刻意abort网络错误，重启读取阶段console为空。

持久事实为1父运行/1原连续游标/3不可变回执/3同事务完成审计/2初始资金账务，订单0、成交0；此计数与原编号、owner和运行逐项关联。停止第一JVM后关闭连续执行启动第二JVM，真实GUI仍可GET原结果及保存进度，暂停入口禁用；数据库游标所有字段完全保持、回执与审计仍各3、无新订单/成交。成功随机库已删除，临时Servlet/预览均退出，候选与bundle摘要不变。此证明的冻结研究输入为合成前置，不代表上游研究/回测/评估/发布GUI验收。

四次原失败保留在同名前缀b8357ec9c77a4005a007ebba1af3980c、e5d8c41f95334d8a8beb93c84f9ca64c、788a90b70421474f82cadea2d7a2edaa和9e46b7e9fc21463c906fa17d23dc6f0a：分别为选择器点击已被业务标签覆盖的搜索输入、语言弹层离场动画未结束即缩屏，以及折叠控件实际角色与脚本不符。仅改测试harness，未修改正式候选或bundle；原失败数据库/日志保持，不追认为通过。

完整33文件候选已冻结到独立源码副本csop-e4f996b617b6；review-continuous-operation-input.json SHA256 7edeebaaff6ef2c0cfba50ebd73a685f0bd7a72bf00ca7a2c2f8508d405e8915。真正独立定向审查进行中，作者不修改候选。原验收仍固定V20，未安装V21或新UI；矩阵638原ID保持，仅更新5处连续功能证据状态。全站仍ACTIVE_PARTIAL，最终全任务格式化、Full Maven/全站E2E、其余GUI、发行资格/指南/双源码导出仍待完成。Git NONE。


## 连续操作独立接受与三种凭据材料正式GUI（2026-10-15）

continuous-operation-independent-review.md（SHA256 b2506b26081a7116a675a138fd14b6189fd69f383c80ba8f3c1076992a7ce452）为真正独立审查ACCEPTED_SCOPED_INDEPENDENT_REVIEW，无阻断或新增可操作finding。实际15后端目标、24前端单测、8 API-fixture浏览器及新增HTTP/PG探针通过，4829源文件hash前后一致；额外证明最后audit写入失败回滚、旧新URL共享稳定身份及私有来源other/ADMIN拒绝。公开来源仍遵循原共享运维合同，回执则仅原主体可读。独立浏览器不是Production包；作者正式GUI证据仅复核复用，C3仅源码/编译，格式gate仍待最终执行。

credential-material-formal-gui-e388cceacb834db3b8a5069899d8ea87以同一Production包、正式Servlet/JWT和新随机PG完成2账户/4凭据/11操作：OKX添加→结构校验→轮换→停用→启用→过期→撤销，Binance HMAC和内存新生成Ed25519各添加/结构校验。CREATE与ROTATE事务成功后丢响应，表单材料清空，刷新保留非敏感身份、手动GET原结果，不重复POST；localStorage/sessionStorage无合成材料。第二JVM仍可确认原CREATE回执，同时当前历史维持ROTATED/REVOKED，所有选定持久事实完全保持。权限探活0、订单0/成交0/账务0、ENGAGED；成功库删除，临时进程退出。合成材料只验证正式加密与本地结构，不声明真实交易所权限。

实际检查中文1536和英文390截图；表格按原合同内部横向滚动，不能把代表截图扩大为双语所有角色/动作或真机触摸/原生缩放验收。两份原失败9adf0952a0df408fa19cca80889481f8、17901650f97d4a1ebb7b21dbe8860143保留，原因是测试未处理AntD两字中文按钮插空格；仅修harness，产品源码未变。

矩阵638旧ID保持，仅关闭材料GUI/凭据UNKNOWN两处特定缺口；账户全页实际费率和其他状态仍待完成。连续候选正在原验收备份副本验证V20→V21；首轮比较器将4 CHECK+3 FK+1 PK的8项误写成7，已保留失败、固定V20原服务恢复UP；原库未升级。R2按冻结DDL及实际元数据修工具，对保留失败副本重新确认94旧表完整保持后才进入新身份重做。正式代码/迁移/JAR不因此改变。


## V21连续操作候选本地安装与原事实保持（2026-10-15）

已审候选33文件hash保持。显式package spring-boot:repackage构建可执行JAR runtime/nq-app-continuous-v21-594cad217e7b.jar，SHA256 594cad217e7b259fbb1bb6752aa52733b19db96c6c29ad4377d6e606e3efd901；9个生产类与V21迁移字节匹配。普通package首轮为thin JAR，已保存continuous-v21-thin-package.jar，未启动或替换验收；嵌套模块匹配初版误把scheduler-contracts计入scheduler，修核对工具后复用同一次成功重打包结果，不重构产品或把构建当测试。

continuous-v21-install-8f94dbe6cab34d25a85932ab5f6ad6b4从静止V20原库捕获94表及全部净值样本指纹，PG16 custom备份并恢复新随机副本；副本以两个真实JVM升级/重启V21，94旧表全列、20条原Flyway记录完全保持。新表及8个约束单独核对；副本改一个旧偏好值时比较器明确拒绝，未删或归一化旧事实。副本成功后才升级原库，先停采样比对全部旧表，再恢复采样验证原样本多重集保持、允许追加。成功副本已删除；R1工具错误计数副本及备份保留。

已安装后端PID46212/18280与配套continuous-recovery-r2-dist前端PID60020/18128；index SHA256 6a4c0fd8945100d63a70ac8d9c0ab76c0bda6dca03f381dec23b32b9e508d8a5，94文件全部匹配正式GUI冻结输入。current backend/UI pin均改为实际V21，旧pin/制品/恢复入口保留且不适用于V21。restart-pinned-v21-acceptance.ps1实际Start-only通过，两个started均false；错误backend SHA、错误安全读取器SHA两个独立副本负例均启动前拒绝，当前pin未修改。初次负例收集器遇Windows stderr代码页解码错误，仅改取ASCII原因字节并复用已完成旧事实读证据，原失败工具保留。

安装后相关15浏览器用例通过（4正式/11fixture），偏好HTTP完整摘要相同。另1项纯正式只读GUI证明：原真实SIM无连续游标返回空成功、页面准确显示未建立、后端能力false时连续创建禁用且有说明、陌生原编号GET404；1536/390无整页溢出、无业务写请求、偏好保持。该脚本初版把空成功正文强行json解析失败已保留，修为与正式parser相同的null语义后通过；产品源码未改。全部16项之后再核对94旧表与原样本保持。原48订单/48成交/196账务、Shadow0、调度操作0、启用0、LIVE0、未终态订单0、GLOBAL_TRADING ENGAGED保持；连续driver仍false，净值采样true。

这只是本地局部候选安装，未完成真实发行包或全站资格。Continuous命令/恢复已在正式Production/PG双JVM局部证明；真实公共行情轮询推进、通知完整流程、研究顺序等其余GUI、全站E2E/最终Full Maven/联合复核、用户和管理员指南、确定性双源码导出/发行fresh升级重启备份恢复仍待完成。638矩阵旧ID全部保留，5连续项更新已安装局部接受；最终全任务格式仍PENDING（324 Java/200前端及配置，3项已知Java工具欠项），U01非阻断待澄清，Git NONE。旧V20恢复脚本不得对当前V21库使用。


## 告警稳定操作候选V22：后端局部证明、前端待完成（2026-10-15）

通知来源检查发现canonical SIM页面遗漏原告警处理面板；原ACK可能覆盖已RESOLVED状态，且原写入缺持久回执。候选补CREATE/ACK/RESOLVE稳定身份、原主体与完整输入指纹、不可变结果和同事务审计；旧URL保留但须UUID Idempotency-Key，确认人来自认证主体。条件更新防止ACK逆转解决状态。V22只在隔离证明库执行，原验收仍固定V21，不能安装未接好前端的组合。

paper-alert-operation-formatted-unit.log为25目标测试通过。paper-alert-operation-http-proof-80bc05d81072413683f5cb466d5ba6ce/result.json为正式HTTP/PG双JVM局部通过：8同编号并发仅1告警、owner/父路径/role/header边界、旧新URL共享原回执、OPEN/ACKED历史快照与当前RESOLVED分开、真实行锁排队下较晚ACK409、最后审计失败完整回滚、回执UPDATE被拒和重启逐份复读。成功随机库删除，临时进程退出，订单/成交0。它不是GUI或升级证明；早期密码环境/空JSON失败保留。

已写前端稳定客户端但尚未测试接入；告警面板/UNKNOWN恢复、通知准确来源定位、正式GUI、私有费用来源回归、V21→V22升级并保留95张旧表、锁超时、独立审查与备份安装仍待完成。矩阵新增1条稳定操作映射并保留638旧ID。全站ACTIVE_PARTIAL；最终全部新增修改代码统一格式化、相关回归及发行验证仍待完成，Git NONE。


## 告警与通知正式GUI及原生跨标签恢复（2026-10-15）

告警面板已接回canonical SIM，旧Paper诊断中的原告警能力移至可直达区域；通知携带具体alertId，独立读取并展开当前告警，不依赖列表窗口。确认含原运行/告警/动作与影响，操作者来自服务端认证；VIEWER可读取而不能写。新客户端发送前保存并回读主体、UUID、父运行和完整输入，原生Web Locks协调多标签；刷新只GET原结果，真实404仍保留UNKNOWN，显式确认后才继续原编号。原OPEN/ACKED回执只促使重读当前事实，不能覆盖当前RESOLVED。窄屏展开详情保留明确状态，定位滚动避开顶部导航。

paper-alert-ui-unit-full-final.log为45文件242项前端单测通过；paper-alert-ui-build-r3.log为type/build通过，候选paper-alert-ui-r3-dist未安装。13个局部文件经paper-alert-ui-r2-format-prettier-ast.json证明格式前后语法树一致、重复格式稳定；布局单测原失败No QueryClient保留，修正测试隔离后通过。Java原25目标及80bc05d81072413683f5cb466d5ba6ce正式HTTP/PG证据按相同后端实现复用，未冒充新跑Full Maven。

paper-alert-formal-gui-c3df14ad8ccb43faa62a5dcba88d81c3为正式Production bundle、Servlet/JWT、PG16及两个真实JVM局部通过。实际CREATE和ACK提交后丢响应，只查询原结果恢复；阅读边界PUT提交后丢响应，经显式偏好复读确认，告警仍OPEN；ACK/RESOLVE后的更新再次未读。通知直达原告警且展开当前事实。真实原结果GET404后取消确认零写入，再明确继续同一编号/完整输入一次；第二标签原生GET等待写锁，原标签关闭后仅GET恢复，无重复POST。首次历史CREATE与ACK回执保持原快照，当前告警仍RESOLVED。中文1536、英文390和VIEWER读取/禁用写入已检查，无整页溢出，窄屏明确显示解决状态。

持久事实为1父run、2告警（OPEN/RESOLVED各1）、4不可变回执、4关联审计、2正式初始资金账务，订单0/成交0、ENGAGED。第二JVM逐份复读原回执及当前告警、阅读边界和语言偏好，全部选定事实保持。成功随机库删除，临时Servlet/预览退出，候选与bundle摘要前后相同。控制台只有刻意abort和已核对原编号的真实GET404负例，不能报零console错误。父run由正式API建立、上游冻结研究为合成前置，不代表上游完整GUI；旧Paper额外GUI和真机/所有视口未完成。

扩展阶段5份原失败目录d3d67f6e199440618a390d92f4a65791、debe13e3b8194b73b356d32384050c88、507ea9e70d3842a2836f8b049e42ecfb、78e68df226454a508b38767a67bdd30d、d6b914bcc76b47c0aa8c66c48581855c全部保留，产品源与bundle不变：测试原先以加载中按钮消失代替事务完成、未处理新标签声明/异步等待拒绝、重复状态文字选择器不唯一、漏计刻意404控制台输出，以及重启手动点击未等首次失败查询结束。每项按具体证据修harness，不把历史失败追认为成功。较早2eaaee06e86d471e9183c04dc79aff82是前一呈现版本的核心流程证明，最新结论以本段c3df候选为准。

矩阵639唯一行保持（638旧ID加本次1条）；3通知项和原操作项更新为局部正式GUI已验证、待V22审查安装。原验收18128和18280健康HTTP200、固定V21未变。V21→V22升级须保留95张旧表（含V21连续回执表），锁超时、私有来源回归、独立完整候选审查和备份安装仍未完成；全站ACTIVE_PARTIAL。最新全任务只读格式范围335 Java/206前端及配置，前端稳定且源码漂移0，Java仍3项待整理；全部开发结束后再完成最终统一格式、中文说明性注释/短类型import/语义差异检查及相关回归，finalPass=false。Git NONE，U01非阻断待澄清。


## 告警V22真实升级、私有来源回归与完整候选待独立审查（2026-10-15）

paper-alert-upgrade-private-proof-4be56bc009cf4077bd2e308988de2be0为29个后端目标通过（research24、API2、app3），包含原FrozenAccountFee正式观察→冻结回测→评估发布→独立SIM及私有子对象读写隔离，以及新增V21→V22真实升级。V21先经原Java服务建立连续回执、父运行/真实初始账务和OPEN/ACKED/RESOLVED三条旧告警，停第一JVM后逐表比较95张旧表与全旧列；锁users使V22在5086ms超时，新增表与复合唯一约束全部回滚，旧事实保持。解锁后只执行1次迁移、validate成功、重复执行0；7项真实约束及唯一/FK/状态/缺字段/大小/时间/不可变正负例在探针事务内回滚，不伪造历史回执。第二JVM读取原连续回执与三条旧告警完全一致，全部95旧表、用户/角色和经济事实保持。成功新随机库删除，旧V1—V22迁移内容前后不变。

原paper-alert-private-source-r1.log失败因夹具仍固定V12，当前仓储新增费用换算列未到位，测试尚未触及权限路径；保留失败并将随机库目标改V22，原资金和权限断言不变。随后7d50d22510464a47934ebf56d4a540a4同时暴露：新Controller把不可见来源转换为400而原合同要求404；local种子用户在重启时改写旧users/user_roles。修正为输入先校验仍400、来源/子对象不可见沿用404，并增加单测；只在测试夹具抑制默认authSeedRunner，显式测试用户仍按原服务创建。未改生产种子合同或放宽旧表比较。原失败库、脱敏日志与断言保留；HTTP header/响应中的合成JWT不进入公共证据。

修正后paper-alert-formal-gui-542cd0954a3841be98358739c1c326fe重新通过Production bundle/正式Servlet/PG及双JVM完整告警通知闭环；paper-alert-operation-http-proof-092b861fd89e4650b528d552579c6c0c重新通过正式HTTP并发、最后审计失败回滚、原生行锁下较晚ACK拒绝和不可变回执恢复。前端源码与paper-alert-ui-r3-dist未变，242单测/type/build和13文件格式AST证据按相同前端候选复用。这些分别证明局部流程，不代表全站GUI、Full Maven或发行资格。

639矩阵ID保持，当前待真正独立完整候选审查、PG16备份副本升级和安装；原V21验收未改。全站ACTIVE_PARTIAL，最终全任务格式仍待开发完成后执行；最新只读范围336 Java/206前端及配置、前端稳定、源码漂移0，3个原Java格式欠项保留，Git NONE、U01非阻断待澄清。


## 告警 V22 与保留 SIM 功能的生产构建已完成局部安装（2026-10-15）

告警源码独立审查 `paper-alert-operation-independent-review.json` 接受，原 30 候选及 4814 上下文前后 hash 一致，P0—P3 finding 均为零；同步注入监听器反例仍为生产可达性未建立的观察，不据此修改功能。JSONB 转义 NUL/孤立代理项的真实 HTTP 探针均为 400、原回执 GET404、零副作用，未制造新缺陷。

旧 r3 构建关闭两个 SIM 入口，保留为不具备功能保留安装资格的历史包。现已在 Vite 配置中固定：标准 production build 默认保留人工与连续 SIM，开发模式默认关闭，显式 false 可单独关闭，非法值终止构建；示例配置与 API 文档同步。该开关不能启用服务端驱动或扩大角色、风控与真实交易权限。默认与受限 r5 包各 94 文件，新 8 配置组合及真实 GUI 均通过；人工和连续两种表单各创建一个独立运行。第二 JVM 服务端连续能力关闭时，人工按钮仍可用、连续按钮禁用，旧持久事实保持。`paper-alert-ui-r5-artifact-independent-review.json` 独立补验只接受此精确默认 r5 前端的局部安装，未授予后端迁移或全站资格。

本地安装证据：`paper-alert-v22-install-e993061c54e84a4cb8abeb8d714360fd/state.json`。PG16 备份 SHA256 `1e0229e17a10b2c11f0b63992ac4b2577abe3900dd97dc024b10aedca53c1828`，恢复随机副本并精确比较 95 旧表及所有旧列，V22 双 JVM 读取保持；故意修改副本旧偏好时，比对器拒绝。原验收库随后只向前升级一次，暂停采样时旧列精确保持，恢复采样后原样本多重集完整保留。所有原经济事实不变，连续驱动仍关闭、LIVE 与私有源关闭、kill switch ENGAGED，成功副本已删。后端可执行 JAR SHA256 `562e2cd5e799d060f25406cdd3cc42e69d74d35454ca6fee47928c071e6f22bc`，UI index SHA256 `d3c0e49568607c3d8170e18fd3df6bc975f6501f86e2592563adde4486c7dc3e`。

安装后 `paper-alert-v22-installed-readonly.log` 11 项，加正式连续/告警工作区 1 项，共 12 项（4 正式 / 8 fixture）通过；用户偏好保持且无业务写入。全部浏览器结束后再次复读 95 旧表和原净值样本通过。`restart-pinned-v22-acceptance.ps1` start-only 未重复启动服务，两个错误身份负例在启动前拒绝。V21 及更早恢复器不得用于 V22 数据库。

新 GUI 首次补测因检查创建页后未返回选定父运行而失败，保留 `23c55b2c187b4b39b221a31347896c9f` 原证据；只修正测试导航，新随机库复验通过，源码与包未变。首次构建 r4 的全文件守卫因现有 tsc 生成 vite.config.js/d.ts 报漂移，原包和失败保留；仅将这两个编译输出作为产物单独记录，其他源码守卫不变，新 r5 成功。

639 唯一矩阵 ID 保持。全任务最终格式仍待全部开发完成后执行：最新只读范围 336 Java / 207 前端及配置，前端稳定、源码漂移零，仍有 3 个既知 Java 排版欠项；根目录 Vite 配置已纳入最终清单，生成 .js/.d.ts 不作为人工源码处理。Full Maven、剩余全站 GUI、连续公共行情实际推进、指南/双源码导出/发行包资格仍未完成。U01 保持非阻断待澄清，Git NONE。


## 研究顺序与创建主线 GUI、详情布局 r6 及发布刷新缺口（2026-10-15）

`research-event-order-formal-gui-0545dd3612c548d888f546b87d02f32f` 使用当时当前正式 r5 包、Servlet/JWT 与独立 PG22。合成迟到来源下，三运行持久化 12 研究成交、24 估值、3 报告；前 3 笔成交同一真实时间，顺序 1—4，估值 2—4 同时点仍按 1—8 原序。真实 GUI 验证中文1536、英文390、跨所选运行、原币BNB及换算SHA、权益图精确提示和移出关闭；第二 JVM 冻结报告和全部选定事实复读一致。原始 BigDecimal 尾零与UI去尾零词法不同造成的首次测试失败保留，仅纠正比较器无意义精度位，不改业务值、时序或源码。

`research-mainline-formal-gui-b486062898f54f92a2b82450da4746fd` 首次完成真实表单研究配置→回测配置→独立版本与数据集绑定→冻结→执行→评估→发布→独立SIM创建；费用观察是正式API调用合成loopback源的前置，策略版本和迟到数据集为显式合成前置，未冒充真实私有来源或上游策略/数据创建GUI。随后截图发现长ID挤窄标签，修复仅涉及研究/回测详情的响应式单列、标签不换行与长值换行，原业务字段和动作保留。三个源文件局部格式AST一致，React检查未引入新状态、订阅、网络或组件边界。

当前 r6 包 94 文件、index SHA256 `44e526adb7174736d62efa3e9870661538c8eeebd33d54be2204f49b9afdbe7c`；`research-mainline-layout-r6-026457c57bbb4c90b2b7ff43054659c6` 重新完成上述完整主线和双 JVM，中文桌面/390及英文390六项详情标签布局均单行且无文档横向溢出。事实为1研究执行、4研究成交、8估值、1成功报告、1发布及1独立SIM，0 canonical订单/成交、4初始资金账务，ENGAGED。1笔观察API前置、9项页面业务写入和1笔当前新合成用户语言偏好写入分别记录；不会称为真实撮合或自动运行。成功随机库删除、临时进程退出，原失败的四个下拉框测试定位/异步时序身份完整保留，没有把它们追认为通过。

r6 已安装18128，后端45964/V22未切换；当前前端31880。`research-detail-layout-7f66b99e40eb47e99cdf6ac82c7ea663/installed-browser-proof.json` 在保留的原业务对象上验证研究/回测详情各桌面与390，共4项只读布局；偏好不变、业务写入0、应用错误/控制台警告0。再读95旧表、全旧列与原净值样本通过，恢复器当前pin start-only通过，无重复启动。这里只证明这些页面与主线，不代替全站角色、查询/重置/错误/取消及发行资格。

发布刷新缺口已实际复现并保持待修：`research-detail-layout-7f66b99e40eb47e99cdf6ac82c7ea663/published-recovery-gap-proof.json` 中原发布状态SUCCEEDED、既有 `GET /api/backtest-runs/{runId}/publish` 返回原记录200，但回测页刷新后继续到SIM的链接数为0，原因是组件只保留当页发布回执。分类NON_BLOCKING_WORKFLOW_RECOVERY_GAP；下一步以既有精确查询恢复原身份及链接，不重新创建发布。相关两组件矩阵行明确为刷新恢复待完成，未虚报闭合。

639 唯一ID保持，本次精确更新 15 条正向动作/路由及主线记录，不把未点击的工具栏创建按钮、查询/重置或其他状态借正向流程标绿。全站ACTIVE_PARTIAL；连续真实公共行情推进、其余全部矩阵状态、最终Full Maven/E2E/联合复核/指南与源码及发行包资格继续执行。最终全任务格式、中文注释/import与相关回归门槛仍未完成，Git NONE，U01非阻断待澄清。


## 发布刷新继续入口恢复 r7（2026-10-15）

发布成功后刷新丢失继续入口的缺口已修复。页面按当前所有者与精确原运行调用既有 `GET /api/backtest-runs/{runId}/publish`，使用持久化原发布恢复独立 SIM 入口；读错误可手动重读，错父对象或损坏身份不渲染链接，恢复过程不自动重新发布。没有新增后端、迁移或持久意图协议。

`published-result-recovery-formal-gui-772319d4c4c543be8b6b0cd8e245b668/result.json` 完成正式 Servlet/PG22、实际提交后丢响应、GET 丢响应与手动重读、错父对象负例、再次刷新及双 JVM 原身份验证。一次发布 POST、一个发布记录；中文1536/390及英文390原主线与六项布局继续通过。受控错误请求产生预期 net::ERR_FAILED，未宣称该隔离测试控制台零错误；合成前置和 loopback 费用源也不等于真实私有来源。

`published-result-recovery-a034b86aa1024026a032dcfdf8fa7b43/installed-browser-proof.json` 在18128已安装 r7 包中，使用原失败的同一运行 `brn-50ba4bdd-72a9-4ac3-bb07-a0382d689cbe`、同一发布 `pub-e38d24af-9079-435f-a95e-c4f3a1620049`，确认刷新后桌面和390均恢复原入口；四项研究/回测布局、原偏好、零业务写入及零应用/控制台错误通过。前端40828，后端45964/V22未改变，94文件 index SHA256 `f53ff7825b46d7e01d088ab867b9f0c8df5ee84c3bb2beb035099960b64faede`。旧缺口文件保留，当前修复只按此范围闭合。

249前端单测、type/build、六文件局部格式及 AST 等价通过；`published-recovery-record-f498a9c7a3dc43858ecf775316f6de04/post-browser-original-facts.json` 新跑95旧表/原列与原净值多重集复读通过。整体639唯一ID保留，仅精确更新两个已验证动作。未覆盖尚未提交/未抵达时刷新、全部失败发布/角色组合及全站UNKNOWN资格。

实际公共行情新跑正式 `OkxClosedBarMarketFeed` 仍为DNS不可用，证据 `continuous-canonical-public-preflight-8e56848fd8b14cdc8dbe60b9c078c98b/result.json`。未修改DNS/hosts/代理、未关闭TLS、无私有请求或交易写入；该子项为外部依赖阻断，其他验收继续，不以受控来源代替真实公网证明。

全任务格式只读核查现为336 Java /210前端；前端稳定，三个已登记Java欠项与最终全任务格式门槛仍待，不能把局部格式通过写成最终完成。按用户要求，全部实现完成后、单独授权提交前，会备份精确清单并统一格式，检查语义差异、中文注释、显式import/短类型及相关回归。整体ACTIVE_PARTIAL、Git NONE、U01非阻断待澄清。


## 研究四页查询、取消与详情排版 r8（2026-10-15）

当前英文1536桌面/390窄屏下，研究、回测、评估、发布四个页面八组真实交互已验：手动查询初态、精确原对象筛选、行内详情和同对象GET刷新、重置后待查询、无匹配结果；研究/回测实际点击工具栏新建、输入草稿并取消，零业务写入，原偏好保持。这里的无匹配筛选可复用新鲜的正式GET缓存，不强求本地字段每次联网。详情可读性截图发现长标签挤窄金额与ID，修复为标签在上、值在下，明确全部响应断点并均分列宽；版本快照独占整行，原业务字段、操作和数据均保留。

仅修改评估、发布、回测三个页面的Descriptions呈现与限定CSS，未引入新Hook、状态、HTTP或业务判断。四文件格式AST一致且幂等，type/build通过。原script缓存/折叠身份/初态文案定位失败与早期布局候选的样式优先级、桌面默认三列、遗漏第二发布详情表等证据分别保留；没有将早期功能通过追认为全部视觉验收通过。最终候选 `research-lists-candidate-a7d5bafe4cd741099510d0f0e5c9247b/result.json` 与已安装 `research-lists-installed-8d85367fa7a64790aa9119777af137e5/result.json` 均完成八组只读和六组排版，所有受检事实值单元格宽度至少306px，应用错误/控制台警告/业务写入均0。

前端15900，后端45964/V22不变；r8e包94文件，index SHA256 `124ac98b51d59b97d3070f10fd96a0d4cf5012f1c9129af4b8a5189a0d93cd09`。`research-fact-layout-9a0480c982d14d98bc50e9ac9020229b/installed-publish-recovery-regression.json` 在原失败同一运行/发布上重新验证桌面与390刷新继续入口，同一原身份保留；四项原研究/回测标签布局亦通过。`research-fact-layout-9a0480c982d14d98bc50e9ac9020229b/post-browser-original-facts.json` 新跑95旧表/原列/原净值多重集复读通过。`v22-acceptance-start-a48459c54b7d4f74a1d60f5253a20e4b.json` 的当前pin恢复检查通过，startedBackend/Frontend均false，没有重复启动。

639唯一ID全部保持，31条动作/组件/路由获得本轮局部证据，剩余409条仍为NOT_STARTED；这不是未实现功能数量，也不是全站完成百分比。未点过的可视化/重试、其他角色/语言、其余筛选字段、写入校验和全部生命周期继续独立追踪。没有新跑249单测或Full Maven：本次变化仅呈现，原业务单测证据保留，验证采用type/build、真实目标GUI和差异自查；全站最终回归另行完成。

最新全任务只读格式336Java/210前端，前端稳定、源码漂移0，原三个Java欠项仍保留；最终统一格式化、中文注释/import与回归仍未完成。整体ACTIVE_PARTIAL、Git NONE、U01非阻断待澄清；公共行情DNS子项仍待外部依赖恢复。


## 四页筛选与读取恢复、表单时间边界和可视化路径（2026-10-15）

当前R8候选未改变，使用已安装18128/正式V22后端验证英文1536/390。`research-list-boundaries-462385f37c294d8890fdcf7137ba0542/result.json` 包含8组全部可见字段组合、30项逐字段负例：研究的源策略/配置ID/名称，回测的研究/配置ID/名称，评估的研究/配置/运行/状态，发布的研究/配置/源策略/版本/状态；去首尾空白正例保持原对象。不存在的研究父对象按既有回测列表合同实际404，页面保留错误；其他无匹配字段得到真实200空结果，没有把404当空成功。

8组错误重试采用明确浏览器503响应夹具，仅在GET边界注入；既有一次有界自动重试停止后，点击页面重试得到真实服务端GET200和原对象。16次503夹具、4条实际404控制台记录分别分类，未宣称控制台零错误；`console-classification.json` 纠正旧输出字段expected503Console曾合计两类错误的元数据，原raw证据保留。还验证研究/回测的必填源对象/名称阻止空提交，结束时间早于或等于开始时间拒绝、晚1ms清除区间错误、开始时间变化会触发依赖校验，时分秒/毫秒面板可见。其他必填字段仍为空，因此这不是成功提交或后端全部校验证明。

`backtest-visualization-installed-4bf2c305e7e142c5b0ecbe6add10e1df/result.json` 在同一真实原运行上，实际点击列表行可视化、手动刷新与返回列表；桌面/390各一组，并对明确运行深链接重载，两个Canvas曲线、悬停提示移出关闭、同运行正式pnl-snapshots/评估/成交读取和序列原响应SHA保持。配置读取错误为显式503夹具，重试恢复为正式200；不冒充真实停机或全部图表数值/触摸/缩放/导出资格。截图已查看，未发现本轮路径的白屏、页面横向溢出或新增应用异常。

本轮两套浏览器业务写入0、原偏好不变、源代码和当前UI/backend pin均不变；`research-boundary-progress-14c75e92d8584f618e397031e5a98521/post-browser-original-facts.json` 复读95旧表原列和原净值多重集通过。原选择框遮挡定位和日期弹层退出动画造成的脚本失败保留，修正脚本后新身份通过，不修改产品实现或降低业务断言。没有重复运行构建、249单测或Full Maven；复用同候选有效证据。

639唯一ID全部保持，本轮精确追加23条动作/表单/路由证据，NOT_STARTED现为399；其余动作不能因邻接页面通过而标绿。查询初态仍含有界分页技术措辞，列为下一次UI修改的非阻断文案整理；后端分页能力缺口仍在技术追踪，不以改文案伪装实现。全站ACTIVE_PARTIAL，其他路由/角色/写入/异常组合、最终格式与回归、指南/源码/发行包继续；Git NONE，U01非阻断待澄清，真实公共行情DNS限制仍未消除。


## 路由入口检查与目录页布局 r9（2026-10-15）

`route-entry-installed-8540a03838ab433ab402b39ad52aa531/result.json` 用R8已安装制品完成34个已登记路由的68项英文1536/390入口检查，含独立未登录会话的登录页和受保护账户页回跳。66项认证/公开入口中63项正常渲染、2项Shadow非法ID读取错误状态、1项目录页窄屏溢出。业务写入0、应用pageerror0、原偏好及候选保持。入口可见不等于业务按钮、正常数据/异常组合或整页全功能验收，所有未点过的动作仍独立追踪。

目录页390的标题与同步按钮将文档撑到416px，原失败截图保留。R9只改目录页Form呈现、限定CSS及中英文查询初态文案：完整标题后放操作，筛选控件按宽度四列/两列/单列，不隐藏按钮或表格字段；查询、同步方法、API/状态/权限保持。分页技术说明改为用户操作提示，后端查询有界/分页缺口没有因此完成。四个文件格式AST与幂等检查、type/build通过；`catalog-layout-gui-candidate-3db478bd63914657851cfed2ee7221c3/result.json` 和已安装 `catalog-layout-gui-installed-11c841a1f69540629b25e6c7a50a6ab1/result.json` 均验证1536/390/320宽度、关键词大小写/URL/无匹配/清空、原BTC行详情、按钮键盘焦点及三处当前英文新文案；原目录响应SHA和用户偏好保持，控制台/pageerror/业务写入均0。实际同步POST未执行，不能把按钮可见当同步成功，也没有新资格声称源于真实公网。

Shadow原入口脚本使用shr前缀ID，真实detail与drilldown均400，原脚本标注意图404不正确；已保留并更正为非法格式负例。`shadow-missing-uuid-installed-e73f3c16b682466ba6f6602aed79231f/result.json` 后续在当前R9中使用规范UUID，桌面和390真实GET404并显示对应未找到状态，无400、无业务写入。它不证明正常成功Shadow详情或生命周期；此前V19成功详情证据独立保留，不被这个负例替代。

当前前端8004，后端45964/V22未换；r9包95文件，index SHA256 `fc2037a7d7a0d4f64398b6d9e2259ec3cab1acb18eb9282d58220ced9d4c4586`。`v22-acceptance-start-37ff57285da64f68bdb1fefe4cb8d2b1.json` start-only核对新pin通过，未重复启动任何进程。`catalog-layout-69e4878d274045b093a4252da3a72dd1/post-browser-original-facts.json` 新跑95旧表/原列与原净值样本多重集复读通过。没有重跑249单测、Full Maven或全站业务E2E；本轮仅呈现和文案，复用未改变业务路径的有效证据，新增真实目标GUI/type/build和差异自查。

639唯一ID保持，34路由及3个实际目录读取动作补充局部证据；NOT_STARTED现为375，这不是尚未实现功能数或全站完成率。同步动作仍未通过。本次全任务只读格式336Java/211前端，前端稳定、漂移0，原三Java欠项及最终统一格式门槛保持待办。全站ACTIVE_PARTIAL，其他角色、全部业务/异常/写入、有界查询、真实公开源、回归/复核/文档/源码导出/发行资格继续；Git NONE，U01非阻断待澄清。


## 研究列表正式服务端分页（2026-10-15，候选未安装）

新增GET `/api/research-configs/page`，复用正式研究事实和既有认证权限，无schema/migration变化。源策略精确匹配，配置ID和名称不区分大小写的普通文本子串匹配，`% _ !`转义为字面量；排除ARCHIVED而保留DISABLED。SQL先筛选再COUNT/LIMIT/OFFSET，按创建时间与稳定ID倒序；只读REPEATABLE_READ、5秒事务超时使总数和当前页使用同一读取快照。limit1—100、offset非负int，过滤长度及原始控制字符拒绝。旧GET数组兼容保留且未静默截断，回测研究选择器和其他列表仍待迁移，不能宣称全站有界查询缺口已关闭。

前端研究页使用正式分页和真实total，筛选/重置/改变条数回首页；查询身份包含当前用户、全部筛选、窗口及手动查询版本，过期读取signal可取消、离页列表缓存gcTime为0。初态不自动请求完整目录。新候选R10b只在独立18147预览，原18128/R9和18280/V22未切换，禁止把新前端单独安装到没有此接口的旧JAR。

`research-paging-5b1db8eb352342f2a3017faea6388d14/proof-d1e9075b671046a58e68f066883f789c/result.json` 通过真实PG16/完整Spring Servlet/JWT读取：525条明确SQL合成目录前置，524条可见、主源521条同刻记录；43项HTTP检查涵盖未登录401、既有OPERATOR/VIEWER读取、最大页/遍历无重无漏、offset520原0001末页、组合筛选、大小写、字面通配符与注入样式普通文本、归档排除和DISABLED保留、20项非法窗口/长度/控制字符400及原DTO/trace、旧数组524条兼容。39次真实读取观察到read-only/repeatable-read；未证明并发业务修改下的所有情况。目录、策略及canonical订单/成交/账务事实复读保持；成功随机库已删除，进程停止。

中文1536×1024及390×1024正式GUI都通过：手动初态、521真实总数、页2/53、末页详情、末页修改组合筛选回首页、字面字符筛选、空态/重置、100条容量/页6末21条、文档无横向撑宽。列表没有调用旧数组，应用异常/控制台警告错误0，只有两次正式登录POST，无业务写入。原首次脚本中文按钮/选项定位失败保留；随后窄屏确证AntD默认隐藏条数选择，新增限定研究表格CSS恢复可操作性，新R10b再次完整通过，两张末页截图已人工查看。Browser plugin不在当前技能列表，按Build Web Apps分支使用已有Playwright/Edge。

研究新3项后端单测与23模块受影响reactor编译通过；前端47文件253项单测通过（窄屏仅CSS/class修正前，非业务单测路径保持），修正后type/build与上述两宽度GUI通过。`format-r3/formatted-source.json` 的9Java/7前端精确范围均格式稳定/AST相等；这是局部整理，全部开发结束后的全任务统一格式、中文注释/显式import/短类型名核对和相关回归仍未完成。当前build-proof-r3冻结4853源文件、95制品文件，验证期间源/制品/编译class均未漂移。

639唯一矩阵ID全部保持，仅5条现有研究入口/查询/详情映射补充证据；未借邻接动作追认新接口503重试、其他角色GUI、其他语言、并发修改及所有写入/生命周期。全站ACTIVE_PARTIAL，最终Full Maven/全站E2E/联合独立审查/指南与源码双导出/发行fresh升级重启备份恢复资格仍待。Git NONE；U01非阻断待澄清。用户本次再次要求全部任务完成后、提交前统一格式化，继续作为最终必需项。


## 研究分页补充状态与回测配置正式分页（2026-10-15，R11候选未安装）

研究原R10b代码/制品未变，补充 `research-paging-5b1db8eb352342f2a3017faea6388d14/proof-4bcd8932bb944256be59079f71d6f4af/result.json`：operator中文1536、viewer英文390的新分页接口明确503浏览器夹具（两次尝试，即一次自动重试）后显示失败；用户重试读取正式GET200恢复。原服务端字段校验400可见；延迟已取回的真实旧GET，随后新筛选total=1，旧请求实际ERR_ABORTED，释放旧响应后新行不被覆盖。只有合成登录及viewer英文偏好PUT，无业务写入；原目录/策略/canonical事实保持，成功随机库已清理。预期400/503控制台及取消单独记录，不把夹具当自然服务故障。

新增GET `/api/backtest-configs/page`：`researchConfigId`精确匹配，`backtestConfigId/name`为不区分大小写字面子串，转义 `% _ !`；ID128/名称255、控制字符与limit1—100/非负int offset校验。原研究父对象不存在仍404，不回退全表；COUNT与页记录复用同一 `ResearchFeeReadScope.configuration(config_json)` 条件，并在read-only REPEATABLE_READ/5秒事务内执行。沿原配置mapper/DTO和精确JSON解析器，不改变冻结费用来源、写操作、旧数组端点或schema/migration。停用仍展示，归档默认隐藏，稳定按创建时间与ID倒序。

回测页消费真实items/total，筛选/重置/条数变化回首页，owner、全部筛选、窗口和查询版本进缓存key，传递signal且离页gcTime为0。保留100条容量及窄屏选择器，未隐藏旧字段或工具栏动作。研究选择器/源策略及运行等旧数组消费者仍待迁移；本切片不等于全站有界读取完成。

`backtest-paging-26c83ace0fcd45a28a4af2fd08d2d0ae` 下的 `proof-872d44d7b3a04c568b5c34128ffa91c5/result.json` 和 `proof-36426753471a43e89d3269d48fab3eba/result.json` 均为真实PG16/完整Servlet/JWT/Production bundle验证。显式528条SQL合成目录前置：每个读者525可见、同一主父522；51项HTTP通过，覆盖完整遍历/排序无重漏、offset520的0001及私有末页、双主体私有items/total隔离、malformed owner不可见、字面通配符和注入样式文本、归档/停用、精确超安全整数金额 `9007199254740993.123456789012345678`、401/404/20非法400及原DTO/trace、旧数组525兼容。目录/研究/策略/canonical事实前后保持。两个成功库已删除、进程停止；原密码协议和折叠详情定位脚本失败库/证据保留。

主GUI为中文1536/390，验证手动查询、真实522总数、页2/53、末页配置详情与展开原冻结身份、组合筛选回首页、字面字符、空态/重置、100条/页6末22条及无文档横向撑宽；列表未调用旧回测数组。扩展GUI为operator中文1536/viewer英文390，补充显式503夹具到正式GET恢复、真实400、另一主体私有范围无匹配与本主体私有行、迟到旧真实GET取消后新结果保持。应用pageerror零，预期失败响应/取消单独记录；只有合成登录和viewer偏好PUT，业务写入零。

受影响23模块reactor编译及12项后端目标（4新分页/3研究分页/5原Controller）、256项前端单测、type/Production build通过。9Java/7前端局部格式稳定且AST相等；4862源文件、95包与运行class在作者测试和独立审查前后保持。原安装18128/R9及18280/V22未改，新前端不得单独指向缺少接口的旧JAR。

`backtest-paging-independent-review.md`（SHA256 `30c46d60f563475f426ccf324ae2d91a77ea50d003a793b0b39b173849df053f`）结论ACCEPTED_SCOPED_INDEPENDENT_REVIEW，无阻断/新增可操作finding。审查者独立运行40次业务GET、2次合成登录及3项前端目标单测，证明伪造owner参数无效、双主体范围、边界、兼容和金额精度；受控COUNT后并发归档一行/改变另一行私有归属，原请求仍total3/items3，下次owner1/viewer2，验证真实PG MVCC一致快照。七表纯读指纹保持，新随机库已清理；未运行Maven或修改候选/共享class。作者GUI只复核复用，没有虚报为独立浏览器重跑，也不把一组受控交错当持续高并发负载资格。

639唯一ID及顺序保持，仅6条原回测映射与3条原研究状态补充证据。全任务只读格式清单现为353Java/217前端及配置：前端全部稳定、源漂移零，原3项Java欠项保留；最终统一格式、中文说明性注释/import/短类型名及相关回归仍须全部开发结束后完成，finalPass=false。全站ACTIVE_PARTIAL，未安装R11、Git NONE、U01非阻断待澄清。评估/发布列表、其余选择器/完整业务状态、Full Maven/全站E2E/联合审查/指南/双源码导出/发行包资格继续，不借本局部独立接受追认全部任务。


## 评估与发布正式分页及状态提示修正（2026-10-15，R12/R12b未安装）

新增 `GET /api/evaluations/page`、`GET /api/publishes/page`，沿原DTO和冻结运行费用来源授权范围。评估研究/回测父ID精确、运行ID字面子串；发布研究/回测/源策略字面子串、版本ID精确；真实SUCCEEDED/FAILED状态、limit1—100/非负int offset、长度/控制字符和字面 `% _ !` 校验。COUNT与页面共享条件，read-only REPEATABLE_READ/5秒；稳定时间与正式ID排序，配置归档不隐藏历史结果。原数组、详情与写入口保留，无schema/migration变化。前端key包含owner/全部筛选/窗口/查询版本，传递signal、gcTime0，评估成功失效新page缓存；评估运行筛选字段正名backtestRunId并进入正式SQL，表格按evalReportId识别行。

`result-list-paging-93cb3a344fba454a96fb5e9f638e9e1b/build-proof.json` SHA256 `6d46cdafe1535412aa5619ba6a631e91236340573d36883915d0d51def3d4ad4` 冻结4876源文件/96包文件；14Java/12前端局部格式稳定、AST相等。作者 `proof-b660e4d6d29941de9428f2019f565287/result.json` 为真实PG16/完整Servlet/JWT/Production bundle：每族528条明确SQL合成输入，每读者526可见/主父523；118项HTTP验证遍历不重漏、私有COUNT与items、伪造owner无效、归档历史、FAILED/NULL时间、精确与字面筛选、401/400、原数组兼容及金额 `9007199254740993.123456789012345678`。104次实际只读RR事务观察；九项后端目标、262项前端单测及type/build通过。

作者四组GUI为评估/发布各OPERATOR中文1536与VIEWER英文390：手动查询、页2/53、原0001末页详情开关、全部字段组合、FAILED、重置、100条/页6末23条、显式503浏览器夹具后用户重试正式GET恢复、真实400、迟到旧真实GET取消后新结果保持；无旧数组调用、无document横向溢出、pageerror零。只有合成登录与viewer英文偏好PUT，无业务写入；canonical及目录事实保持。成功随机库清理、进程停止；首轮发布初态文案脚本错误失败证据保持，不能追认该轮成功。

`result-list-paging-independent-review.md/.json` 为ACCEPTED_SCOPED_INDEPENDENT_REVIEW，无阻断或新增可操作finding。审查者独立92次业务GET/六项前端目标，分别在两条真实SQL的COUNT后暂停，让另一连接提交冻结run私有归属变化；原请求仍total3/items3，下一owner1/viewer2，证明真实MVCC一致快照。当前父配置/发布快照owner副本不能替代冻结run范围。4876源/96包/26候选及共享class审查前后无漂移；未改候选、未运行Maven。作者118HTTP/四组GUI仅复核复用，未声称独立重跑。独立首轮并发夹具漏填publish_name失败及其随机库保留；新身份修正输出夹具后通过，不是候选修复。

独立结束后新R12b仅修改两页状态Form.Item，将原空字符串映射为空Select值，使初态及清除后显示“全部状态”，提交仍按原表单归一化。两份中文原因注释、Prettier稳定和AST相等、type/Production build与六项目标单测通过。`build-proof-status-r2.json` 保留原R12业务候选，不覆盖旧manifest；后端/接口/SQL未变。新 `proof-953cd91193d444e0be5fe3d59d081295/result.json` 四组真实GUI均通过，明确断言初态placeholder、FAILED清除后提示恢复且正式查询从1条恢复523条；原分页/异常/取消行为同测。此轮只重做认证前置和新GUI，118业务HTTP明确复用，不虚报重跑。成功库清理、服务停止，源/包/class保持；两张代表截图已人工查看。

639唯一ID及原顺序保持，只补十二条入口/查询/筛选表单/重置/详情开关/重试的真实证据，另两条既有详情刷新只同步读取映射，未提升验证状态。IS/OOS、执行评估/发布、精确发布深链等未覆盖行不追认。原18128/R9与18280/V22保持，新分页候选均未安装；不得把新前端单独接缺少新接口的旧JAR。

最新全任务只读格式范围365Java/222前端及配置，原三项Java欠项仍保留；本次两页再格式稳定/AST相等。用户要求的全部开发完成后、提交前统一格式化仍是最终必需项，须备份精确任务范围、核对语义/中文说明性注释/显式import与短类型名并完成相关回归，finalPass=false。全站ACTIVE_PARTIAL；其他列表/选择器、完整业务生命周期、全站E2E/Full Maven/联合审查/指南与确定性双源码导出/实际发行包fresh升级重启备份恢复仍待。Git NONE；U01非阻断待澄清。


## 研究配置有界选择与关联对象精确读取（2026-10-15，R13b未安装）

回测新建表单的研究配置选择不再请求全量数组，改用正式 `/research-configs/page` 的名称/ID组合筛选和每页20条选择窗口，明确显示真实总页数，支持末页选取、清除与取消保留原值；选定值使用原正式ID，由详情接口获取名称，不把第一页当全部选项。弹窗位于新建抽屉之上，保留原Form.Item必填与字段身份/无障碍关联。回测详情也改为按关联researchConfigId读取精确对象，继续定位源策略版本；失败显式告警并可手动重试。查询包含当前用户，传递AbortSignal、gcTime0，关闭选择窗口切换禁用key并取消旧请求；无后端/schema/权限/命令变化，原研究数组接口兼容保留。策略/版本/数据集等旧数组缺口继续追踪。

`research-selector-e7687f27035d49fe89c86b4f985c0b87/build-proof-row-action-r2.json` 冻结4877源文件/96包文件，六份前端精确修改范围含一个新组件。原R13全前端262项单测通过；初次type检查发现可能同时为undefined的可选比较，修为显式非空检查后，重新262单测/type/build通过，原失败日志在build-attempt-1保留。随后截图发现窄屏英文行按钮截断，建立新R13b，改为中文“选用”/英文“Use”，三项i18n目标/type/build及六份局部稳定格式/AST相等通过，原R13制品/成功GUI保持。R13b没有把旧全单测写成新一轮全量运行。

`proof-b41ebdba73cd4daa8ec1c6c3f227e86f/result.json` 为新随机PG16/完整Servlet/JWT/Production bundle的operator中文1536和viewer英文390两组GUI：明确525条SQL合成目录、524可见，每页20、第二页及第27页末4条，实际选中原0001并精确GET读取原名称；名称与ID组合、字面%_!、空态、重置、取消保留、清除、显式503浏览器夹具后真实GET恢复，关闭取消被延迟的真实旧GET、重新打开当前20行不被旧响应覆盖。真实elementFromPoint确认弹窗高于抽屉，窄屏行按钮完整位于单元格，无document横向撑宽；无旧研究数组读取，pageerror零。GUI首轮只因脚本把合成名称写成Research而非Synthetic research断言失败，保留失败及随机库；改用原正式响应名称、新身份通过，候选未改。

`proof-df589c39f4654da09432a92e2631559e/result.json` 另用明确合成关联配置补验两组详情：指定研究详情和对应源策略版本GET、关联读取503时配置详情保持、通过真实通知关闭按钮后用户重试正式GET恢复、关闭详情。未加载研究页或研究全数组。首轮desktop通过，viewer窄屏因通知覆盖重试而脚本等待超时，原失败/库保留；新脚本先真实关闭通知，不强制穿透点击，新随机身份两组通过。通知覆盖观察只证明可真实关闭后继续，不宣称全站通知布局已完成。两类成功库均删除、进程停止，源/制品/class保持，目录及canonical事实复读一致；业务写入零，只有合成登录和viewer语言偏好PUT。代表选择/关联截图已人工查看。

矩阵639唯一ID及顺序保留，仅三条回测动作补证；新组件的整表创建提交仍明确待补验，未追认旧成功主线为新候选完整写生命周期。当前安装仍18128/R9、18280/V22；R10—R13b分页与选择候选均未安装。全任务只读格式快照365Java/223前端及配置，检查期间前端稳定、源码漂移零，快照仍是原三项Java欠项。随后发现ScheduledJobOperationController由本轮外其他写入产生缩进/注释换行变化；与精确冻结源码对照并运行Java编译器AST比较1/1相等，保留当前文件及前后副本，不改写原manifest。此时当前树与冻结候选多一处纯格式差异，不能声称当前全树哈希仍匹配；将该文件也纳入最终排版复核。最终全任务格式化/语义核对/中文注释/显式import与短类型名/相关回归尚未执行，finalPass=false。全站ACTIVE_PARTIAL，其他业务闭环与全站/发行包资格继续，Git NONE，U01非阻断待澄清。


## 新研究选择器参与正式创建的补验（2026-10-15，R13b同包未安装）

`research-selector-e7687f27035d49fe89c86b4f985c0b87/proof-da2d3ff2329e4b5ba6f301fb8ca49144/result.json` 通过新随机PG16/完整Servlet/JWT/原R13b Production bundle：OPERATOR中文1536及英文390从实际工具栏打开新建、在有界窗口选择原0001，手动填写时间并按Enter确认，完整提交正式POST，各创建一条配置；VIEWER英文390完整表单由正式安全边界403拒绝并保留输入。实际请求与持久化逐列核对研究ID、startTime=`2026-10-01T00:00:01.123Z`、endTime=`2026-10-02T00:00:02.456Z`、资金123.4567和明确EXPERIMENT_ASSUMPTION执行合同。两条配置均未隐式绑定版本/数据集，无backtest_run；成功后精确URL和重载原详情可读。明确400浏览器拒绝夹具一次且不自动重试，表单保留所选对象与输入，提交状态结束后用户再提交由真实服务创建，夹具不是服务端自然失败。纯目录/策略及canonical订单/成交/账务事实保持；成功库删除、进程停止，零private/LIVE/真实交易。

没有重建前端或重新编译后端；`submission-validation-input.json` 新验证身份显式继承原制品，源码仅纳入两份进度文件和已证明AST相等的调度Controller排版变化，不覆盖原manifest。运行前后源/包/class无漂移。首轮日期面板按钮定位失败、第二轮脚本在首个拒绝尚未结算时过快再次点击导致等待超时，两轮失败及随机库保留；改用真实日期Enter确认、等待loading结束后，新身份三组通过，候选代码未改。原后端及全量单测是复用证据，没有虚报新运行。

仅补两条原回测创建映射并保持639唯一ID与顺序。新选择组件的整表创建缺口关闭，但独立版本/数据集绑定、冻结运行、后续执行/评估/发布仍按此前证据和其他行动作范围验收；不以两个配置创建证明全站闭环。当前安装R9/V22保持，全站ACTIVE_PARTIAL，最终全任务格式/注释/import/语义及相关回归仍待，Git NONE。


## 策略定义服务端分页与源策略精确关联（2026-10-16，R14未安装）

新增 `GET /api/strategies/page`，沿用既有定义读取范围、原mapper/DTO及单独的启停/创建/版本接口。正式ID精确，编码/名称/类型/交易所/环境为不区分大小写的字面子串，转义 `% _ !`；enabled布尔，limit1—100、offset非负int，原始控制字符与字段长度校验。COUNT和页记录共享谓词，read-only REPEATABLE_READ/5秒，排序沿原created_at及strategy_id倒序，无schema/migration变化。旧数组和详情兼容保留；分页查询不开放执行能力。

策略页五个已有筛选迁移到服务端，真实items/total驱动分页，筛选/重置/条数变更回首页；owner、全部筛选/精确ID/名称、窗口和查询版本进入缓存key，AbortSignal/gcTime0处理离页与旧响应。三项空字符串Select映射为空值，初态显示全部类型/交易所/环境；补齐NQ已支持的SPOT_SMA_TARGET_V1筛选与中英文业务名称，保留历史类型选项。回测详情原全策略数组查找改为按研究的sourceStrategyId、limit1正式读取，再沿原策略编码读取版本；错误显式呈现与重试，其他版本/研究源选择器等旧数组仍待。

`strategy-paging-644d1eebf7f840f785288d539b5dc8ee/build-proof.json` 冻结4886源文件/97包文件。精确20文件为9Java/11前端，局部格式稳定，Java编译器AST及Prettier AST相等；受影响23模块编译、六项目标测试（新策略Query3+原结果分页Query3）、266项前端单测及type/Production build通过。未运行全Maven、未把不存在的旧Controller测试名计为已运行测试。

`proof-37ab77b8625d434b98659ec2b218a5bc/result.json` 使用新随机PG16/完整Servlet/JWT/原生产包：明确527条同刻合成策略，匹配两个正式SIM账户的交易所；另有一条明确合成研究/回测关联前置。48项HTTP检查覆盖双读者完整遍历/排序无重漏、全部筛选/组合/启停布尔、普通通配符与注入样式文本、精确ID与大小写差异、最大offset/空尾页、13项非法400及trace、未登录401、原数组527与详情兼容。读观察核对每次分页实际read-only/repeatable-read，但未把事务模式观察当作已证明所有并发交错。

OPERATOR中文1536及VIEWER英文390两组GUI通过：手动初态、全部Select提示、真实527总数/页2与53末7条、原0001详情开关、完整五字段组合、现货均线目标类型选项、字面%_!、重置、100条/第6页末27条、明确503浏览器夹具（两次尝试）后用户重试正式GET、真实400、迟到旧实际GET取消后新结果保持；回测关联只发limit1策略定位及正确编码的版本GET，无策略全量数组。pageerror零，预期错误/取消单独分类，无document横向撑宽；成功截图已人工查看。只有合成登录和viewer语言偏好PUT，没有策略/版本/交易业务写入。目录、关联配置及canonical事实复读一致，源/包/class无漂移，成功库删除、进程停止。

保留前几轮失败：首次输出脚本继承旧合成密码，认证401；随后两轮已通过48HTTP，分别因脚本相对定位placeholder及猜测中文“禁用”标签失败。修正输出脚本为角色组合框定位和正式common.disabled文案，新随机身份全通过，候选始终未改；原失败和随机库保持。上述故障不追认为候选缺陷或先前PASS。

矩阵639唯一ID及顺序保持，仅六条策略读取动作及一条回测详情关联补证，创建/启停/新版本/研究创建等原行动作继续保留。全任务只读格式现为374Java/227前端及配置，前端稳定、源漂移零，原四项Java排版复核仍待；最终全部新增/修改代码统一格式、中文注释、显式import/短类型名、语义核对及相关回归finalPass=false。当前安装18128/R9与18280/V22不变，新分页候选不得只安装前端接旧JAR。全站ACTIVE_PARTIAL，剩余业务闭环/其他列表选择器/全站E2E与Full Maven/联合审查/指南与双源码导出/实际发行包资格继续；Git NONE，U01非阻断待澄清。


## 策略管理真实操作与响应式详情（2026-10-16，R14c未安装）

R14共享类型列表补齐后，创建表单仍另行前置SPOT选项，形成重复value。新R14b让创建和筛选使用同一份真实选项；七项前端目标、type/build及单页格式稳定/AST相等通过。随后窄屏实际操作发现常量双列Descriptions把右侧字段挤出抽屉，新R14c采用窄屏单列/桌面双列及既有长身份换行样式，两份中文原因注释。R14b/R14c只改StrategiesPage，无后端/DTO/权限/schema/命令变化；原R14读证明与原制品不覆盖，`build-proof-operations-layout-r3.json` 为新UI身份，七项目标复用R14b，R14c新type/build与局部AST通过。原全量266前端单测及六项后端目标也是复用证据，不写作本次重跑。

`proof-1d2c7ce13f9d4befba10f70067eddeb8/result.json` 为新随机PG16/完整Servlet/JWT/正式R14c包三组GUI：OPERATOR中文1536与英文390从无账户前提打开并取消、在顶栏选择真实SIM账户后创建两条定义，默认停用；中文案例真实重复编码409只尝试一次、保留输入后改码成功。按未启用过滤进入详情，实际启用/停用使后台分页total1→0→1，详情和版本手动刷新；两条定义各创建DRAFT/ACTIVE两个SMA版本，版本号由服务器分配，字符串敞口`0.125000000000000001`、参数/默认配置/来源快照及checksum核对，按创建响应快照独立重算SHA256。敞口0本地拒绝、清空恢复默认、精确版本深链高亮和实际研究就近入口携原ID/名称/编码后取消均通过。VIEWER创建受账户前提限制，实际停用与版本POST为正式403，原定义/版本保持。详情表宽度明确落在抽屉阅读宽度内，代表窄屏截图已人工查看。

该组三组共13次命令尝试：10个成功写（2定义、4启停、4版本）、1个重复409、2个权限403；不是模拟成功。持久化逐列核对新增定义/版本/快照/校验值，原527策略目录全行指纹保持，原研究/回测及canonical订单/成交/账务事实保持，strategy_run为零。成功库删除、进程停止，源/包/class保持；无真实交易、私有联网或LIVE行为。原R14b两轮未完整通过分别保留：首轮已创建两个版本，等待提交按钮的可访问名随loading图标变化而失配；下一轮桌面完整通过，窄屏真实发现布局外溢及同类按钮等待问题。输出脚本改用稳定提交按钮/文本定位并等待真实loading结束，新UI按新身份完整通过，没有强制穿透点击或修改候选运行中的标准。

`proof-a3c4d67d39cb4f45a2f116384457bb6f/result.json` 另用新随机库从分页列表进入普通GRID元数据表单，中文1536/英文390：清空还原默认、ARCHIVED状态、显式参数/配置/来源JSON，中文真实无效JSON400保留草稿后修正创建；再补SMA高级配置数组本地拒绝及显式对象覆盖。三条版本按正式POST产生，逐列持久化、checksum和18位小数字符串保持；源定义及canonical事实未变，未授予旧类型执行资格。两次分页入口实际观察到只读RR。前一轮相同GUI/SQL已通过，但直接详情入口没有分页读取，继承脚本错误要求计数>0导致最终化失败；原失败、库及独立最终化记录保留。新身份从实际分页行进入后完整通过，未放宽断言，原包/代码未改。

两套成功环境共五组GUI、两条新定义、七个新版本、17次命令尝试，其中13个成功业务写与4个明确拒绝。矩阵639唯一ID和顺序保持，只补九条现有动作/单均线表单；原普通版本与SMA分支均真实验证，不用一条分支替代另一条。研究就近入口仅回显/取消，研究提交、源策略/数据集/版本等其他选择器、所有旧类型/并发与完整运行生命周期仍分别保留。此前48分页HTTP只复用，没有虚报在命令组重跑。

当前验收安装仍18128/R9及18280/V22，新R14c未安装。最近全任务只读格式374Java/227前端及配置，四项旧Java排版复核仍待；R14c单页再格式稳定/AST相等，最终所有任务新增修改代码统一格式/中文注释/import/短类型名/语义及相关回归finalPass=false。全站ACTIVE_PARTIAL，完整全站E2E/Full Maven/联合独立审查/指南与确定性双源码导出/实际发行包fresh升级重启备份恢复继续待办；Git NONE，U01非阻断待澄清。


## 研究源策略有界选择与正式提交（2026-10-16，R15b未安装）

研究创建用正式`GET /api/strategies/page`按20条选择，名称/编码字面子串及完整ID组合筛选；URL或已选身份另以limit1精确读取，不再加载策略全目录。停用定义仍可研究，保持定义启停与运行状态的业务区分。取消保留原值、清除、错误重试、关闭取消及释放列表读取，移动布局在同一单元格展示编码/环境/启停。新选择窗位于研究抽屉之上。正式详情新增默认折叠的创建时源策略快照，直接展示持久化原字符串；空历史值显示破折号，不用当前定义回填。中英文文案同步，未改后端、schema、权限或研究命令合同。

`proof-fa4029107eb8413ca089f099a4e198b6/result.json`在新随机PG16/完整Servlet/JWT/R15b生产包完成两组交互：OPERATOR中文1536、VIEWER英文390。527条明确合成策略，真实20条窗口、页2/27末7条、0001末页选择、名称/编码/ID组合及停用0003、取消保留/清除、字面`%_!`、空态/重置、明确503浏览器夹具两次尝试后用户正式GET恢复、延迟真实旧GET关闭时ERR_ABORTED且新打开结果不被覆盖、无效URL来源可见并重新选择恢复。弹窗层级、操作按钮容纳、手机环境文字和无文档横向外溢通过；没有调用旧策略数组，业务写入0。桌面和手机截图已查看。

`proof-f7d950c39b264b6aaedea53845d27b65/result.json`另建新随机库完成三组实际提交：OPERATOR中文1536从工具栏经末页选择0001、英文390从停用0003策略详情的就近入口进入，两条研究均经正式POST成功；VIEWER英文390正式POST403。两次无效JSON均由真实接口400拒绝并保留草稿，更正后提交，5次GUI命令尝试中2成功、2个400、1个403。原strategyId、schema/defaults、`9007199254740993.123456789012345678`字符串、数据规格逐列核对，创建后打开精确详情、折叠快照展开、刷新/重载及再开空表单均通过。响应JSON按内容比较，PG JSONB排版和时间存储精度分别处理，不要求创建响应与持久化文本字节相同。

数据集前置由正式API创建，barCount=0、status=INVALID且quality非OK，明确没有行情bars、没有公共采集/回测资格或实际执行回测的证明。GUI后经正式PATCH将源0001由启用改为停用，只有其enabled/updated_at改变；正式研究GET及PG原冻结快照仍分别保留true/false，其他源行、原研究/回测和canonical订单/成交/账务、strategy_runs/backtest_runs均保持。业务成功写合计4次：1空数据集前置、2研究创建、1源状态变更；不把前置写计入GUI成功数。两个成功库均删除、进程停止，源/包/共享class前后未变。首轮仅脚本textbox可访问名定位超时，未创建研究；其证据/库保留。改用实际label关联，并记录无aria-hidden祖先后完整通过；成功GUI包含3条访问性诊断记录和3个完整场景，不把诊断记录算成6组测试。

R15原构建266前端单测通过；R15b新增快照后新执行7项策略分页/i18n目标、type/build，原266项为复用；4文件Prettier格式稳定与AST相等，无新后端测试声称。当前候选4887源文件/96包文件，验证前后身份保持。矩阵639唯一ID及顺序不变，仅6条相关动作补证。数据集/版本/运行等旧数组消费者和其余生命周期继续待办。原18128/R9与18280/V22安装未改，不把新前端单独接旧JAR。

用户本轮再次要求全部开发完成后、提交前统一格式，仍为最终强制门槛：按届时全部任务新增修改文件整理缩进/换行/TSX/CSS/配置、显式import和短类型名、中文原因/边界注释；核对语义差异并运行相关回归。目前finalPass=false，四项旧Java排版复核仍待，不因本轮4文件格式通过宣称全任务完成。全站ACTIVE_PARTIAL，Full Maven/全站E2E/联合独立审查/指南与确定性双源码导出/实际发行fresh升级重启备份恢复仍待。Git NONE；U01非阻断待澄清。


## 数据集有界目录、六处消费迁移与正式创建绑定（2026-10-16，R16f未安装）

新增`GET /api/marketdata/datasets/page`，沿用共享行情目录读取范围和原完整单行DTO。精确UUID、名称字面子串、交易所/市场/交易对/周期/状态/质量/来源精确过滤、非负缺段数量上界，limit1—100/非负int offset，拒绝控制字符和超长字段。COUNT与页记录共用只读REPEATABLE_READ/5秒事务，按原updated_at与UUID倒序稳定读取。没有schema/migration或数据集写入/绑定/费用消费合同变更，旧数组及详情保持兼容。

六处现行数组消费者全部迁移：行情数据集页为真实分页；研究选择、回测覆盖和绑定使用共享20条选择窗；可视化详情和已选优惠换算来源按ID读取。页面筛选、身份、窗口和当前主体进入query key，传递signal、gcTime0。优惠来源目录继续使用原交易所/SPOT/BNB-USDT/1m/READY/OK/marketdata_bars/缺段0条件，已选详情仍经原eligibleDiscountDatasets判断；仅迁移读取，没有改变任何优惠预算或记账语义。优惠分支的新选择窗实际GUI及后续费用消费仍待，不能沿用其他表单通过代替该分支验收。

`proof-93f2caa9938b457c96cd1665b866a1d3/result.json`完成52项真实PG16/完整Servlet/JWT HTTP，明确527条SQL合成数据集，双角色遍历无重漏、ID精确/末页/全部维度组合/名称字面通配符/注入样式普通文本、17项非法输入400及trace、旧数组527兼容与原单对象读取；实际观察到只读RR。OPERATOR中文1536/VIEWER英文390覆盖行情页数据集页签、10条页2与53、重复同条件查询、列表503夹具到正式GET恢复、筛选空态与重置；共享选择窗20条页2与27/原0001、完整六维组合及INVALID/INCOMPLETE可见、取消保留/清除datasetSpec、503两次尝试后用户正式GET恢复、关闭取消迟到旧真实GET、弹窗层级和单元格按钮、回测覆盖的毫秒时间回填。没有请求旧数据集数组，无业务写入。手机前两项查找常驻，其余过滤折叠，桌面全部直接显示，代表截图已人工查看。

`proof-a7111df40ee64c83b02d81dbae48aa71/result.json`另建新随机库三组GUI：两个OPERATOR各创建1研究、1回测配置并独立绑定数据集，共6成功写；VIEWER研究POST与原配置绑定PATCH均403，8次命令尝试。正式POST数据规格原ID/市场维度、创建响应datasetId仍null（覆盖选择不等于绑定）、毫秒区间、资金123.4567、执行参数，以及绑定后服务端完整快照逐列落库；关联名称刷新与直接可视化页精确读取同一datasetId、显示BINANCE BNB-USDT均通过。新研究/回测关联完整，原配置及数据集全行/覆盖记录/行情bars、原策略和canonical订单/成交/账务、strategy_runs/backtest_runs保持。数据集是合成只读目录输入，未冒充公共采集或执行回测；可视化卡片继续沿用既有目录元数据读取，不宣称本轮完成所有历史运行冻结展示。两个最终成功随机库删除，进程停止；源/包/共享class在各次证明内保持。

历史失败保留：首轮脚本把创建名称文本框当作搜索框；第二轮交互已完成但夹具关闭trading-components导致正式标的目录Controller未注册。新fixture只启用正式控制组件注册，并显式关闭LIVE/real exchange/client/provider/outbound、WS/恢复/同步/模拟驱动/资产采样，正式目录真实GET返回，无浏览器mock目录成功或跳过500断言。手机六输入挤出结果经目标布局修改后新候选重跑；随后补同条件手动查询和主列表显式重试、过滤空态，不覆盖原候选。绑定组前两轮分别为必填控件可访问名定位、脚本误要求旧可视化卡片显示数据集名称；均有实际部分写入且失败库保留，新身份改用正式label及真实字段后完整通过，没有把原失败改成PASS。

13项后端目标和受影响23模块reactor编译通过；当前fixture注册修正后另行test-compile通过，13目标是复用并非重跑。新增前端测试初放src被项目扫描范围排除，移到正式tests/unit后新执行51文件269项通过；后续手机布局/同条件查询/过滤空态每次各执行6项dataset/i18n目标及type/build，原269项复用。10Java/13前端精确范围格式/AST通过，后续fixture/3文件手机/单页查询与空态分别保持格式稳定和AST相等；1份API说明仅移除尾部空行，内容trim相等另记。既有Vite大chunk提示保留，不伪装性能验收。

R16f冻结4899源文件/96制品，当前只在独立18147验证；18128/R9及18280/V22未切换。矩阵639唯一ID及顺序不变，仅8行补证，不借数据集读取追认行情创建/采集/质量刷新、版本选择或全部生命周期。全站ACTIVE_PARTIAL，优惠来源新选择分支/其他旧数组/全站E2E/Full Maven/联合独立审查/指南与确定性源码双导出/实际发行包fresh升级重启备份恢复仍待。用户要求的最终全部新增修改代码格式、中文注释、显式import/短类型名、语义核对与相关回归finalPass=false；Git NONE，U01非阻断待澄清。


## 数据集关闭即时取消补强（2026-10-16，R16h未安装）

R16f的延迟读取脚本在关闭后重新打开，再检查旧GET已ERR_ABORTED，不能单独证明关闭瞬间取消。复核确认仅enabled=false不会卸下旧观察者，useDatasetPageQuery现把active=false映射到独立缓存身份-1，离开目录/选择窗即卸下旧请求并消费AbortSignal；打开时恢复真实revision，不扩大缓存或更改HTTP/绑定/费用合同。中文注释记录原因。

新build-proof-close-cancel-r8与dataset-paging-r16h-dist完成单hook格式稳定/AST相等、6项dataset/i18n目标及type/build；13后端和269全前端单测复用，不写作新跑。proof-34e76a7b6f9b4fdf841082ab3dc78703在新随机PG16/正式Servlet/JWT/R16h包重跑52HTTP及OPERATOR中文1536/VIEWER英文390完整目录/选择/覆盖读取链；更强断言在关闭后、任何重新打开前观察到旧请求ERR_ABORTED，然后重新打开527/20结果保持。目录/策略/研究/回测/canonical事实不变，无业务写，源/包/class保持，成功库删除、进程停止。代表手机截图已查看。

R16f实际两研究、两回测创建与两数据集绑定的三组GUI和持久化证明复用：本次变化只涉及读取窗口关闭身份，没有更改表单命令或费用语义，未重复业务写。原证明和旧候选均保留，矩阵仅在同8行补证，639唯一ID和顺序保持。当前进度后快照另存source-after-progress-r9；只有矩阵和交付说明漂移，不覆盖R16h正式测试身份。全站ACTIVE_PARTIAL，优惠来源新选择窗GUI及其他未完成项仍待；18128/R9、18280/V22未切换，最终全任务格式finalPass=false，Git NONE。


## 优惠价格来源选择与数据集管理正式GUI补证（2026-10-16，R17，同一R16h代码未安装）

本轮没有修改生产业务代码、schema、费用或资金合同；当前`source-after-progress-r10`的4899源文件/96包文件在每个运行前后及共享class核对一致。补充的是新数据集控件在优惠分支和数据集管理正式动作的真实PG16/完整Servlet/JWT/Production bundle验证。13后端目标、269前端单测和R16h后续6目标/type/build/局部格式证据复用，没有写作本轮重跑；最终全任务格式和Full Maven继续待。

`proof-19685ab0b68044d786daa18c898f8d5d/result.json`复用既有DiscountAssetStrategySimHttpFixtureMain positive：回环合成供应方经过正式费用操作生成观察，实际3次只读GET对应时钟、权限和费率；使用假凭据、合成BTC/BNB bars，不访问真实交易所。另加明确目录副本使135行中128行符合原优惠来源条件，6个不合格项被排除。OPERATOR中文1536/英文390实际从第7页末项选回原数据集；固定交易所、SPOT、BNB-USDT、1m、READY/OK、marketdata_bars与缺段0，取消保留、清除后本地拒绝且无POST、切换收到资产政策清除优惠字段、503浏览器夹具两次尝试后用户正式GET恢复、市场切换不匹配提示及恢复、无整表数组读取和文档横向溢出均通过。代表手机来源窗口已查看。

两份回测配置经正式表单保存原观察/来源ID，保存时没有价格快照；显式绑定版本与行情数据集后经按钮创建两条CREATED运行，未点击启动。运行configSnapshotJson.executionSpec含正式价格快照ID/摘要，PG完整source_json重新计算SHA256相符、owner与原datasetId相符，运行started_at为空。另两条正式HTTP反例分别准备配置和两种绑定（不是GUI），无效换算来源允许保存为配置，但创建运行时400拒绝；最终4配置、2未启动运行和2价格快照，没有孤立快照/额外运行。GUI业务成功写8次（2保存/4绑定/2运行创建）；反例准备成功写6次，2冻结拒绝；1费用观察是独立前置。原数据集/bars/研究/策略及canonical订单、成交、账务、strategy_run保持。成功库删除，进程停止；不宣称重新验证成交支付/预算/记账或真实费率。

优惠验证历史失败均保留：AntD readonly input被显示项拦截需用原键盘选择合同；高级JSON在原生来源下只读，不能据错误脚本要求编辑；市场反例需保持相同交易对才能继续展示同条观察；配置保存与运行创建的冻结时点不同；详情ID在折叠信息中；必填版本控件需按真实label定位；重新加载需等待正式对象GET及可见字段后判断对话框，实际两种语言的aria-labelledby均正常且无隐藏祖先。完整GUI/PG首次通过后，驱动错误将供应方总请求计数期望为1而非当前读取器实际3，导致收尾失败，该轮库/证据及finalization-counter-failure保留为失败；按当前源码三路径修正驱动后新身份完整通过，没有改原失败结果为PASS或放宽业务断言。

`proof-7bb57b46ff97471fa50350708e52c29a/result.json`另建随机库，沿用正式控制器/服务并提供1条明确合成标的目录行供真实表单选择；没有mock目录响应。OPERATOR中文1536/英文390各创建完整与缺段两个数据集并各点一次刷新质量：4创建/4刷新成功，三组GUI共10命令尝试，VIEWER英文390的创建与原数据集刷新均正式403。8根合成BTC bars完整区间返回READY/OK/barCount8/gap0，延长两分钟返回INVALID/GAP_DETECTED/barCount8/gap2。四条新记录和8条新coverage逐列核对，原两个数据集及bars/标的/研究/策略/canonical事实保持。客户端逆序时间拒绝并保留输入、日期Enter不产生提交、UTC秒值、时分秒三列及390手机弹层完整可见均通过；同秒与逆序两个正式HTTP400另计。没有费用观察/价格快照/回测或SIM创建执行；成功库删除，进程停止。初两轮脚本误取校验词条命名空间、后一轮英文表单使用Trading pair而非Symbol，均保留；校正为实际common词条和表单label后新身份完整通过，原生产代码未变。

矩阵639唯一ID和顺序保持，本轮仅10行补证；优惠资金和结算既有V18证据保持独立，不借新选择器追认重新验收。18128/R9与18280/V22未改，新界面仍未整体安装；下一步版本/运行等旧数组、调度/诊断/复盘剩余操作及全站E2E/Full Maven/联合独立审查/指南与双源码导出/实际发行fresh升级重启备份恢复仍待。公开数据实际联调依旧有既存外部连接缺口。本任务最终全部新增修改代码格式、中文注释、显式import/短类型名、语义与相关回归finalPass=false；Git NONE，U01非阻断待澄清。


## 策略版本有界目录、选择绑定与冻结数值精度（2026-10-16，R18/R5未安装）

新增父策略精确限定的`GET /api/strategies/{strategyCode}/versions/page`，返回元信息及真实总数；名称字面子串、完整版本ID、正版本号、DRAFT/ACTIVE/ARCHIVED组合筛选，limit1—100/非负int offset。COUNT与记录同一只读REPEATABLE_READ/5秒事务，按版本号、创建时间、ID倒序。三份JSON不随列表传输，展开单版本才精确读取。旧数组、详情、创建和绑定合同兼容，状态不解释为运行或LIVE权限。

策略详情保留旧字段和5条窗口，增加筛选/重置/显式刷新与按需快照；创建成功回第一页且清除旧筛选。回测绑定改为固定父策略的20条选择窗，名称/ID查找常驻、手机其余条件折叠；保留取消、清除、错误重试、层级和关闭即时取消。已绑定名称及URL深链独立按ID读取，不依赖当前列表窗口。原生JSON字符串直接展示，页面不解析重排数值；切换配置只重置版本绑定草稿。

最终`proof-fb639c397eb94f4ab3aa9d09e4bd2f18/result.json`以4917源文件/97包和新随机PG16/真实Servlet/JWT完成41HTTP及3组GUI：OPERATOR中文1536、英文390和VIEWER英文390。527条明确合成版本及另一父策略对照，末页原对象、四筛选/字面通配符/空态/同条件查询/显式刷新、原快照小数、503夹具两次尝试后正式GET恢复、关闭后且重开前ERR_ABORTED、弹窗层级/单元格容纳/无文档横向溢出通过。2次正式版本创建与4次DRAFT/ARCHIVED绑定成功，VIEWER的创建和绑定各403；PG新数值及绑定快照用Decimal核对，原策略/版本/研究和canonical订单/成交/账务、strategy/backtest运行保持。成功库删除、临时进程停止，代表手机截图已查看。没有调用版本旧数组，旧端点另经真实HTTP证明兼容。

落库核对发现版本创建与绑定的旧double舍入；R2专用Reader修复在独立审查中仍被F1拒绝，因为账户费用准备在其前丢精度。原拒绝报告和实际9007199254740994反例保留。R3补费用准备读取后，真实PG又因原夹具只迁移V11缺优惠列失败；只将该随机库夹具改为当前冻结V22，没有修改迁移。随后完整执行暴露执行readJson舍入引发RESEARCH_FACT_IDENTITY_MISMATCH；R5修复执行和评估校验读取，保留原研究一致性及所有金额/费用算法和舍入断言，未绕过校验。各轮原精度失败、独立F1及所有后续失败分别保留，不追认PASS或回写历史。

最终44项后端目标、受影响reactor编译通过；`precision-execution-r5/pg-proof-b653062438534900a5a91ecf2da0c82a/result.json`是1个完整AppContext/真实PG/MockMvc测试，内部包含正、零、负3组原生回环费用来源，覆盖配置保存、费用冻结、持久化运行重读、实际回测、Buy & Hold、评估及原经济断言；源/包/class保持。假凭据和回环供应方只为隔离证明，不访问真实私有接口。前端273项单测、type/build在R18完整新跑，后续只有后端精度变化，前端证据复用。37个业务/测试/说明文件的分层范围中代码按Google Java AOSP/Prettier格式化并核对AST；这不是全任务最终格式门槛。

GUI另有验证驱动问题保留：响应Promise未统一收敛，以及使用声明异步出现前count=0导致跳过确认，合法弹窗遮住分页。现驱动显式等待声明、实际点击确认并等待隐藏，使用Promise.all收敛动作/响应后以新身份完整通过；未改产品声明合同。R3单测初误期望费率字符串0.001，已按原12位合同断言0.001000000000，旧失败保留。

独立数值冻结补审报告另存`strategy-version-snapshot-precision-f1-fixed-independent-review`，原NOT_ACCEPTED报告不覆盖。矩阵仍639唯一ID及原顺序，仅7行补证。现安装仍18128/R9和18280/V22，未用新前端连接旧JAR。运行/调度/诊断/复盘剩余操作、全站E2E/Full Maven/联合复核/指南与确定性双源码导出/实际发行fresh升级重启备份恢复仍待；公开数据连接缺口另保留。全站ACTIVE_PARTIAL，全部新增修改代码的最终统一格式、中文注释、显式import和短类型名、语义与回归finalPass=false；Git NONE，U01非阻断待澄清。


## 运行历史有界查询与计划关联（2026-10-16，R19未安装）

新增`GET /api/strategy-runs/page`，保持策略ID/计划ID二选一的读取边界；状态及触发来源在完整历史中筛选，返回真实总数和窗口，limit1—100/非负int offset。COUNT/items同一只读REPEATABLE_READ、5秒事务，started_at/strategy_run_id倒序；不投影配置JSON或关联子数组，不开放无范围全局列表。策略和计划身份按完整字面值相等，不把`%_!`作为通配符。旧数组仍最近20条，精确详情保留。

计划关系优先读取既有admission_schedule_id；缺少该事实时才沿用旧请求的window/request/strategy标记解析。正式准入来源保持SCHEDULER，不因请求前缀误标历史SCHEDULED。读取端附加字段及旧构造器兼容，不改变准入、交易、状态迁移或数据库合同。页面增加服务端分页、四种真实触发方式含义、范围校验、同条件查询/刷新、错误重试及请求取消；空字符串转undefined使两个下拉占位文字正常显示。缓存包括主体、范围、筛选、窗口及查询版本，关闭/重置后即时取消。

最终`proof-973af837aace4dfb9e97c46ad0ffa1fc/result.json`：4928源文件/97包保持，随机PG16/真实Servlet/JWT完成63HTTP检查与3组GUI（OPERATOR中文1536/英文390、VIEWER英文390）。535条明确合成历史中本策略534条，覆盖全窗口去重、末页、历史失败筛选、精确计划与字面通配符对照、旧接口兼容、5种详情关系对照、同条件查询/刷新、空态、503夹具后正式GET恢复、深链及重置/详情关闭重开前6次ERR_ABORTED。CREATED与规范化拒绝FAILED是持久化正例；其余3状态本次仅验证空筛选，不冒充生命周期执行证明。535条配套work与原run/strategy/订单/成交/账务保持；后3者均零。新随机库删除、进程停止；手机列表及桌面详情截图已查看，故意503的暂态通知在部分截图中仍可见。

22项后端目标通过，278项前端单测/type/build在ui-r3新跑；R4只改后端来源优先规则，前端源/包哈希相同后复用。13个Java与9个前端文件局部格式化、AST相等，正文全限定类型引用0。第一次核心测试误用了当前模块未提供的Mockito，改为已有无额外依赖的内存端口并保留失败；夹具先后因终态直插和缺少计划外键被数据库拒绝，后以CREATED/work同事务初始化、既有规范化拒绝函数得到FAILED并先创建计划，未修改或禁用guard。初GUI使用AntD只读输入点击被覆盖，改用正常键盘聚焦展开；失败均独立保留。

矩阵仍639唯一ID及原顺序，仅8行更新。详情子列表/金额精度/用户化能力缺口表达、刷新详情按钮单独点击及全局搜索GUI仍待，不能据当前读验证标整页完成。18128/R9与18280/V22保持，候选未安装；全站ACTIVE_PARTIAL，最终Full Maven/全站E2E/联合复核/指南/确定性源码导出/实际发行fresh升级重启备份恢复、全任务格式/中文注释/import/语义与回归仍待。Git NONE；U01非阻断待澄清。


## 运行详情、关联订单成交与原始精度（2026-10-16，R20未安装）

新增精确`/api/strategy-runs/{id}/overview`、`/orders/page`、`/trades/page`只读接口，仍使用原运行读取范围。概览直接返回运行摘要、真实订单/成交数量、运行trace与当前未提供的运行级直接关联能力，不加载配置JSON或子数组。子页均按canonical strategy_run_id精确限定，limit1—100/非负int offset；存在性、COUNT和页记录同一只读REPEATABLE_READ/5秒事务。未知运行404与已存在的空子页区分，错误DTO和trace保留。沿用既有run与order/trade索引，无schema改动。旧聚合端点保留公开兼容，前端无界聚合调用及旧错字段类型已移除。

新增订单/成交明细对应正式字段，价格、数量和手续费由BigDecimal.toPlainString传输，不先经double；费用正、零、负与null、原币种各自保留。未改变任何费用/资金/账务算法或交易状态路径。列表与概览共享元信息映射，计划准入来源与R19一致。桌面有界表格、手机卡片及折叠追踪字段保留所有新字段；概览改为紧凑横向字段，避免手机每个名称和值各占一行。刷新读取概览及当前子页，关闭或切页签即时取消；URL身份、返回后的筛选/列表保持。账本/风险/系统事件暂不支持运行级新查询，普通区简洁说明，原技术追踪表名与ref/scope/trace说明下沉折叠。该缺口并不证明不能通过正式Order/Trade稳定关系实现，仍需继续完成真实关联能力梳理及最小实现。

最终`proof-6bdc5fc422164693b14d78c794ccd0b4/result.json`以4947源/97包和随机PG16/真实Servlet/JWT完成42HTTP及3组GUI：OPERATOR中文1536、英文390，VIEWER英文390。2条合成Order与530笔合成Trade中，本次运行1订单/527成交，另一运行3成交不混入；样本先CREATED/work同事务建立，再按既有准备函数得到DISPATCHING和对应Order，不禁用guard。合成成交只证明读取，不是执行、Risk、Ledger或结算验证。全页去重/次页/末页、9007199254740993.12345678价格与0.00000001数量、四种费用情形和币种、404/空态、旧聚合和元信息兼容、错误窗口、503夹具后正式GET恢复均通过。详情刷新当前末页、技术展开、返回保留筛选/3列表行、换页签和关闭重开前6次ERR_ABORTED、手机完整金额卡片与无文档横向溢出通过。原run/work/order/trade/account哈希及零Ledger保持，成功库删除、临时进程停止，代表桌面与手机截图已查看。没有交易写入HTTP，除登录/偏好外均读取；没有真实外联或LIVE。

18项后端目标及受影响reactor编译在R1通过，后续后端源哈希相同后复用；UI-R4新跑283前端单测及type/build。29文件中的18Java与11前端按既有格式工具核对AST不变，Java正文全限定类型引用0；新增注释中文。Vite大chunk警告保留，不作功能失败或全站性能验收结论。初次夹具保留了账户表名误用导致的事实指纹失败；GUI折叠/刷新控件名称定位超时保留，最终按实际控件文本范围定位并记录ARIA名称，没有强制点击或改产品合同。R3手机概览偏高，R4仅改紧凑字段排版并重新完成全部局部GUI。

矩阵639唯一ID与顺序保持，本轮4行补证；R19查询/重置/筛选等证据按未变业务合同保留。当前安装仍18128/R9与18280/V22。运行风险/账本的正式间接关联、策略调度创建/查询/启停和其他诊断复盘、全站E2E/Full Maven/联合复核/指南/确定性双源码导出/发行fresh升级重启备份恢复及全任务最终格式/中文注释/import/语义与回归仍待。全站ACTIVE_PARTIAL；Git NONE，U01非阻断待澄清。


## 运行订单风控、成交账务与所有者读取边界（2026-10-16，R21未安装）

新增`GET /api/strategy-runs/{runId}/risk-events/page`和`/ledger-entries/page`，复用已持久化的canonical关联：风险仅取`scope=ORDER AND scope_id=order_id`；分录仅取`ref_type=TRADE AND ref_id=trade_id AND ledger.account_id=trade.account_id`。run/order/trade账户、SIM/LIVE与交易所交叉限定，不按共享trace、ID前缀或错误scope/ref类型猜归属。没有修改Risk决策、交易状态、费用/资金预留、Ledger posting、余额算法或schema。页码窗口、存在性、COUNT/items在同一次只读REPEATABLE_READ/5秒事务内读取；未知和不可见均404，拥有者真实空页200。原币delta、nullable balanceAfter及账户ID保持字符串，不把分录记录余额称为当前资产；现金/对手分录不能相加成资金流。

新关联读取从正式认证主体解析正数owner，不接受query/body中的用户身份。普通账户按exchange_accounts.owner及原账户/环境/交易所；PAPER复用独立SIM已有创建者或源策略owner规则，并叠加发布回测的ResearchFeeReadScope。普通账户分支明确排除PAPER，不设ADMIN旁路。共享运行概览不授予关联账务权限，R20公开兼容读取范围未改。前端新增订单风控/成交账务页签，简洁范围说明、真实总数、桌面有界表格、手机完整卡片和可展开追踪字段；沿用正式幂等键显示现金/对手用途，未知用途保持未知。概览relationshipScopes标明ORDER_EVENTS/TRADE_POSTINGS，当前只剩EVENT_RUN_LINK未提供。

`proof-cfae0321d27f424381b88d7cdd3ae417/result.json`在4960源/97包、新随机PG16、完整Servlet/JWT与生产包完成124HTTP及3组GUI：OPERATOR中文1536/英文390、VIEWER英文390读取自己的账户；每个主运行527订单级风险与1582分录，全窗口原始事实逐条比较/去重、次页/末页、正负零/nullable金额、原币、现金/对手及未知用途、刷新当前窗口均通过。另一运行、错误scope/ref/type/account、前缀及共享trace控制不混入；两个用户分别检验8组PAPER创建者/源策略owner/公开与私有费用范围/畸形来源描述，伪造owner参数不能越权。PAPER账户别名被既有数据库guard以23514拒绝，单列为拒绝证据，不伪称存在可落库别名。PAPER合成发布均为FAILED/回测CREATED，只用于读取权限，不作为冻结或发布资格证明。

GUI完成两类关系503夹具后正式GET恢复、12次切页签/关闭后且重开前取消、拥有者空页与他人404明确区分、手机更多菜单导航及无文档横向溢出。业务请求除登录/偏好外均GET；原run/work/order/trade/risk/ledger/account及研究/发布描述逐表哈希保持，成功随机库已删除、临时进程停止，源/包/共享class保持。前序SQL UNION时间类型、中文按钮空格定位、手机直接点击裁剪页签的驱动失败保留，新身份分别按真实类型/可访问名称/实际更多菜单恢复，不禁用guard或强制点击。

owner-r2候选15后端目标通过且后端哈希保持；role-r3新执行289前端单测及type/build通过，12Java+10前端局部格式AST通过，正文全限定类型引用0。一次独立审查报告`run-relations-owner-independent-review.json`以96真实HTTP/124只读RR观察接受owner-r2账户关联安全边界。该报告另有F1/P2：非正式嵌套幂等键误标用途；原失败保留，role-r3只修两前端文件为四种完整键精确判定，新增16个键反例及真实PG/Servlet中每个主运行一条嵌套键，三组GUI均显示未知；后端权限源码未改，复用其定向接受，不声称新候选经过第二轮独立审查。作者旧class清单额外相对项的基准解释见author-class-manifest-resolution.json，R3驱动跳过空classpath项并明确绝对runtime身份。范围不扩大为交易资金或全站资格。矩阵639身份及顺序保持，仅3行补证。当前安装仍18128/R9与18280/V22；系统事件正式归属、策略调度与其他诊断复盘操作、全站E2E/Full Maven/联合复核/指南/双源码导出/发行fresh升级重启备份恢复继续待办。本任务最终全体新增修改代码格式化、中文注释/import、语义与相关回归finalPass=false；全站ACTIVE_PARTIAL，Git NONE，U01非阻断待澄清。


## 调度计划有界目录、详情和既有状态操作（2026-10-16，R22未安装）

新增`GET /api/strategy-schedules/page`，仍要求明确strategyId，支持计划ID精确值及ENABLED/DISABLED筛选；limit1—100、offset非负int，总数和页记录同一只读REPEATABLE_READ/5秒事务。按created_at/schedule_job_id倒序，窗口JSON不进列表，账户ID字符串。最近触发时间沿用正式仓储的`greatest(last_triggered_at,max(admission_due_at))`，既有扫描listAll/启停/创建服务与时区算法不改；无schema改动，旧数组/精确详情兼容。

页面改用真实分页与总数，尚未取得结果时不显示伪零；手机卡片保留状态、Cron、时区、最近触发和详情操作。正式类型目前仅CRON，旧“状态/启用状态”同一enabled事实的重复筛选合并为一个明确本地化选项，类型和原字段仍完整保留在详情。详情中窗口JSON/去重范围折叠，原始JSON字符串不解析重排，旧Long账户字段在普通JSON.parse前保留词法字符串。计划启停沿用既有PATCH和权限，不做乐观成功或自动重试；回执不明时禁重复操作，显式GET当前状态后才能按当前状态操作，不把GET当作原命令回执。查看者可读、操作按钮禁用，服务端403另外验证。新增计划到运行记录链接，RunsPage自动读取URL中的唯一策略/计划范围，runId变化不清除原筛选。关闭详情、重置列表立即取消请求，关闭详情保留原筛选与当前页。

`proof-04c0cdb0c0764800a1dac6f0fe12be09/result.json`以4973源/97包、新随机PG16、真实Servlet/JWT/生产包完成55HTTP读取及3组GUI：OPERATOR中文1536/英文390、VIEWER英文390。527主策略计划和另一策略3条不混入，完整窗口去重/次页/末7条、状态与精确ID组合、未知策略空页/错误参数400、无认证401、旧接口兼容、2条明确合成CREATED历史准入的最近02:00Z与关联运行范围均通过；这些样本不代表实际扫描或调度交易。窗口JSON原字符串中的9007199254740993.123456789012345678保持。3组GUI完成筛选/重置/当前末页刷新、503夹具后正式GET恢复、详情/高级配置/来源运行导航、关闭与重置前置取消、手机卡片和无文档横向溢出。

两组操作员通过正式PATCH共8次状态提交：6次正常回执，2次服务端200已写后在浏览器丢弃回执，页面显示未确认、没有自动重发，显式GET读取当前状态后再手动停用。最终所有enabled和配置恢复原值，只有sch-page-0527.updated_at发生预期变化，其余529条完整行和run/work/account及零Order/Trade/Ledger保持；VIEWER正式PATCH403另计，不作为成功写。成功库删除、临时进程停止，源/包/共享class无漂移，桌面与手机截图已查看。前序驱动的Form name/id与AntD加载图标影响可访问名称/保留DOM的错误均原样保留；最终按真实form id、控件可见文字与加载状态定位，没有强制点击或放宽业务断言。

R1新执行14后端目标和reactor编译，UI-R3后端精确哈希保持后复用；UI-R3新执行294前端单测/type/build。21文件的11Java/10前端局部格式AST与正文短类型检查通过，最终全任务格式门槛仍false，Vite既有大chunk警告保留。未改变生产资金语义、权限或调度写入合同，本轮未触发额外独立审查；R21账户关联定向接受保持其原范围。矩阵639身份及顺序保持，仅9行补证。

计划创建存在真实POST但没有前端入口，也缺稳定客户端创建身份/原结果查询，回执丢失后不能安全地直接重新提交；该项继续待办，下一步先补齐正式幂等/恢复合同，再接创建表单和可按名称选择策略的控件。系统事件正式归属及其他调度/诊断/复盘、全站E2E/Full Maven/联合复核/指南/双源码导出/发行fresh升级重启备份恢复仍待。当前安装仍18128/R9与18280/V22；全站ACTIVE_PARTIAL，Git NONE，U01非阻断待澄清。


## 调度计划创建、不可变原回执与刷新恢复（2026-10-16，R23未安装）

新增正式稳定UUID创建与creator限定的原结果查询，继续委托既有StrategyScheduleService；当前ADMIN/OPERATOR共享策略/计划创建合同不变，新原回执只归creator，其他操作员与管理员不可读取。计划、冻结输入/快照与完成审计同一事务提交，失败全部回滚；不持久化STARTING。相同编号与完整规范化输入返回原回执，冲突及忙有独立409代码。原窗口字符串参与指纹，避免原文、高精度数值或身份被重排；当前计划启停不改变原回执，同号重放不重置当前状态。

前端新增按名称选择真实策略、常用/自定义Cron、自动浏览器时区、默认停用、每日时分秒/跨午夜/星期与完整JSON窗口、真实去重范围含义。停用定义可以配置计划，但创建不启用策略、不证明运行。发送前保存原编号与完整输入，跨标签锁失败关闭；刷新不自动POST。结果不明先GET，404仍保留未知，明确同号同输入继续；成功保留原回执至查看当前计划或继续新建。补回历史INTERVAL/MANUAL读取筛选及真实总数，未增加其创建/扫描能力。时间输入Enter误触发隐式创建已由真实手机GUI发现并修复，原失败和持久化反例保留。

作者证据：24个后端目标测试及受影响编译；307前端单测，最终type/build。V22旧计划/高精度JSON的真实PG升级证明锁超时55P03整事务回滚、解锁后V23迁移/validate成功、旧表行哈希保持和新约束正负例。V23已执行冻结，后续不得重写。40组真实HTTP/PG检查涵盖8并发同号只建1计划/回执/审计、不同输入竞争唯一赢家、busy+暂时404、完成审计故障全回滚再同号成功、owner/角色/伪造主体、非法时间/类型/UTF8界限、原回执不随启停变化，以及第二JVM无重新seed/reset后的原结果完全相同。

最终ui-r3为4992源码/99包文件，真实生产包/Servlet/随机PG的14组读取/落库核对及3组GUI覆盖OPERATOR中文1536、英文390与VIEWER英文390。桌面服务端已提交后丢回执，刷新查回原结果；手机原请求未送达后GET404，再明确同号同输入继续，最终2条唯一创建及2条审计。每日23:59:58至00:00:02与星期、完整小数原文、按名称选择、错误JSON前置拒绝、当前计划深链、查看者创建禁用及正式403通过。原3条历史类型行、策略/账户保持，Order/Trade/Ledger/Run/Work为0。成功随机库删除，临时进程停止。局部代码按现有AOSP/Prettier工具核对AST相同；全任务最终格式仍未完成。

失败保留：初HTTP驱动错误期待审计故障500，实际沿用原409/STATE_CONFLICT而未改生产全局映射；GUI声明异步等待、提交尚未结束就读夹具回执等驱动错误独立保留。ui-r2的时间事件泛型构建失败在ui-r3按正式HTMLElement签名修复；307单测仅在随后类型签名修正后复用。backend-r1到最终ui-r3有5个历史类型读取及测试文件变化，早期40组HTTP/迁移证据仅覆盖未变的创建/V23逻辑，不代表整个后端相同；最终读取另有ui-r1的24项目标与14组HTTP/GUI以及独立审查。未新跑Full Maven或全部R22列表GUI。桌面表单截图捕获过输入纠错校验转换时刻，不能用作最终无错误表单截图。

独立结论见以下对应报告，作者证据不替代独立结论。矩阵仍639唯一ID及原顺序，创建覆盖并入既有调度路由行。系统事件正式归属、其他调度/诊断/复盘、全站E2E/Full Maven/联合复核/指南/确定性双源码导出/发行fresh升级重启备份恢复、公开数据链路及全部新增修改代码最终格式/中文注释/import/语义与回归仍待。安装保持18128/R9与18280/V22；全站ACTIVE_PARTIAL，Git NONE，U01非阻断待澄清。

独立候选审查实际结果：`PASS_SCOPED_INDEPENDENT_REVIEW_NO_FINDINGS`；报告`E:\Project\nq-production-reconstruction-output\schedule-creation-independent-review.md`及同名JSON。审查者与作者独立，reviewSourceOperationsFinished=true；仅按报告接受其定向覆盖，不将其扩大为全站验收、安装或发布资格。作者未在审查期间修改源码、包或当前compiled output。

## 运行正式业务事件关联（2026-10-16，R24未安装）

新增正式business-events分页读取及桌面表格/手机卡片，复用现有运行可见性和canonical Order/Trade身份；明确区分命令、原历史回报与当前状态，发生/发布/写入时间分别展示。详情只投影白名单字段，不传原始payload或任意reason。订单、成交、风控、回报和ledger触发持仓事件按正式producer合同归属；共享trace、冲突客户端身份、非唯一外部号、跨账户/环境/交易所及未知版本/类型不混入。PositionUpdated说明为订单触发的账户整体快照，不冒充运行专有持仓。无明确运行身份的系统消息继续明确未提供。

V24增加五个有界业务身份partial indexes。隔离PG的V23升级证明保留旧事件及高精度/超长未知载荷；ACCESS EXCLUSIVE造成55P03时五索引全部回滚，解锁后升级及Flyway validate通过，原行哈希保持，超长新事件仍可写。V24已执行冻结。最终候选ui-r2为5000源码/99包，13后端目标及312前端单测在ui-r1新执行，ui-r2仅调整长拒绝码Tag换行，复用13/312并新执行最终type/build；build-proof继承的两个R23 *Reused路径是旧元数据，实际本轮日志为ui-r1/backend-target.log和frontend-unit.log。

最终作者证明http-6a236838fad44593b0af88fa5bd278b8/result.json为PASS_SCOPED_RUN_EVENT_HTTP_PG_GUI_RESTART：28组真实HTTP/PG检查、544主运行事件、23身份反例、12000背景事件及两真实Servlet JVM。正式登录、只读分页、归属/投影/隐私、精度、外国owner404与真实空页、重启后持久化事实保持通过。未强制planner设置，实际选择全部五个新增索引，当前测试规模COUNT执行约12.129ms；不是生产规模性能承诺。

正式生产包GUI3覆盖OPERATOR中文1536、英文390及VIEWER英文390：首/次/末页、刷新当前窗口、约200字符拒绝码、详情展开、切换运行订单/成交页签、503后恢复正式GET、切页签/关闭时取消、真实空页与不可见404。相关运行API请求全GET；登录与语言偏好写入仅属认证/偏好。已查看桌面列表和手机长拒绝码代表截图，没有截断或横向溢出。Browser plugin未列出，按已用Build Web Apps技能回退Playwright＋installed Edge，未冒称使用不可用插件。成功随机库删除，自有JVM/preview/浏览器进程停止，源码与共享compiled保持。未将本次合成fixture事件当作正式交易执行或资金结算证明。

保留先前失败：初HTTP的跨账户collision fixture违反既有client_order_id/request_id guard，后续使用合法身份重验，未弱化guard；首次UI翻译引用不存在的runTradeId已改用已有tradeId。后续typed-state投影已由最终候选联合证明覆盖，不以早期backend-r1结果替代。Windows长路径导致审查冻结助手首轮读取失败在写入候选前停止，改用长路径后冻结成功，未改源码或测试标准。

独立审查实际结果、范围及是否释放源码由相应报告补充。当前只完成局部格式与语义核对，不等于全任务最终格式门槛。最新全任务只读检查为470Java/257前端配置，前端无差异，三Java待最终调整，源码无漂移；还须复核三个修改pom的文本格式。全站仍ACTIVE_PARTIAL，其他调度/诊断/复盘及剩余页面功能验收、Full Maven、全站E2E、联合复核、指南、双源码导出、真实发行包fresh/升级/重启/备份恢复和提交前全部代码最终格式化仍待。当前安装保持18128/R9与18280/V22；R23/R24未安装，Git NONE，U01非阻断待用户澄清。

独立候选审查实际结果：`PASS_SCOPED_INDEPENDENT_REVIEW_NO_FINDINGS`，零finding，`reviewSourceOperationsFinished=true`；报告`E:\Project\nq-production-reconstruction-output\run-events-independent-review.md`及同名JSON。仅接受报告中的定向范围，不扩展为全站、安装或发行通过。


## canonical SIM实例执行诊断入口（2026-10-16，R25未安装）

原执行诊断路由的说明/内部ID表单已接入现有可见实例目录与正式analysis读取，按策略/版本/状态选择同一独立SIM。显示完整决策/订单/成交/风控总数，明确不由各500条明细窗口倒算。正常CREATED初始状态、无决策、零订单、已有订单无成交、明确RISK_REJECTED、FAILED/REJECTED、一般风控记录及已有成交分别表达；风控记录数不冒充拒绝数，成交不冒充健康/盈利/全部订单完成。非法/非整数/不安全整数计数保留不可确认，不补零。

提示直接打开同一实例的订单/决策/风控Tab，运行管理保留精确paperRunId；Tab写入URL，刷新及前后退保持身份。收益和回撤仍由已有资产/复盘合同负责。决策状态与原因补中英文业务译名，原始代码保留于title和可访问名称，未知代码明示原文。ACCEPTED显示为“决策已接纳”，不宣称订单已成交或远端已成功。

ui-r1新执行325单测/type/build及两组GUI；根据实际截图发现决策代码直出后，ui-r2新执行326单测/type/build及两组GUI。ui-r3按实际手机截图压缩诊断页标题区，显式保留SIM/LIVE边界，其他范围说明改为可点击弹出层，减少首屏说明堆积；新执行326单测/type/build并完成最终两组GUI，包含范围说明点击开关。8个前端文件Prettier AST及稳定性通过，没有Java、schema或业务权限/资金/风控写入变化，按统一合同不额外触发独立高风险审查。此前R24独立接受仅保持其原范围，不扩大为诊断或全站接受。

最终GUI证据见下述路径，1536中文与390英文均使用临时18154生产包预览，连接当前已安装18280/V22的未变analysis合同；这是新前端对已有正式读取的局部验证，不是当前V24后端与新包的最终成套安装资格，现有18128/R9及18280/V22未替换。所选实例来自既有正式产品主线，当前真实接口为7决策/2订单/2成交/2风控记录，并经操作前后正式API重读保持完整run/metrics/facts相同。测试没有手写Order/Trade/Ledger，也没有拦截成功业务响应。

GUI核对计数、正常页面、无overlay、订单Tab定位/前后退、刷新/重载、503故障时不显示旧统计、恢复正式GET、离开实例时先取消挂起GET、无选择与真实404分别展示。相关paper-trading请求全GET；认证与语言偏好另列，原locale在结束时通过正式偏好合同恢复，偏好版本正常递增。无JS runtime error；故障注入503及真实404控制台错误是对应失败场景的预期证据。临时preview和浏览器已关闭。Browser plugin未提供，复用本任务已授权的Playwright＋installed Edge路径。

保留三次驱动失败：首轮瞬时isVisible误跳过异步登录声明，第二轮未处理AntD两字中文按钮的可访问空格，ui-r3首轮在URL变化后立即检查尚未提交的Tab aria-selected；均保留原DOM/截图/日志，以新GUI身份按实际声明状态、按钮名称和Tab渲染完成等待重验，未改产品来迎合驱动，也未减少断言。

本次是诊断能力的增量，不等于全部旧Paper执行诊断已迁移。策略/发布分组、历史Paper诊断及其原有筛选/归因功能仍须按真实源梳理接入和验证，不能以本页替代或从全站任务删除。所有者/角色/空/拒绝/生命周期的更广组合及当前成套候选全站验证继续待；639唯一ID及顺序保持。全站ACTIVE_PARTIAL，全部新增修改代码最终格式/中文注释/import/语义与回归门槛仍false，Git NONE。

最终GUI：`E:\Project\nq-production-reconstruction-output\sim-diagnostics-ui-d7c1afab8bb743ddb985c89efcf097b7\gui-7ec36457f5594849b284f6ac9541a0e3\gui-result.json`；完整结果：`E:\Project\nq-production-reconstruction-output\sim-diagnostics-ui-d7c1afab8bb743ddb985c89efcf097b7\gui-7ec36457f5594849b284f6ac9541a0e3\result.json`。


## 历史Paper诊断、评估与复盘恢复（2026-10-16，R26未安装）

诊断和复盘页面增加明确的“独立SIM实例 / 历史Paper运行”来源。历史视图恢复执行归因、策略/发布分组、评分与回测偏差、排名、规则摘要、重点运行及问题聚类；独立SIM仍使用原canonical正式事实。历史三类视图读取新的同源快照，不再分别消费可能混入canonical空旧表的旧聚合。用户query key包含认证userId，页面切换共享30秒快照；切离来源取消挂起读取，失败隐藏旧统计。范围和截断明确显示；生成时间不冒充经济事实时间。未知来源/筛选/视图显示错误并可重置，真实空源与失败分别处理。

既有筛选保存在URL，刷新/返回可恢复，多条件重置只执行一次导航。下拉框补业务可访问名称。收益/回撤/胜率按有界精确十进制比较，缺失/无效恒末尾；计数和内部评分仍整数。运行与发布链接使用真实身份，版本分组由快照中的真实非空版本ID展开相关运行，缺失身份的分组键保留文本，不冒充版本ID或猜strategyCode。历史刷新按钮最终明确为“刷新历史分析”。

后端新执行21目标测试及真实PG16证明，另用真实Servlet/PG验证owner与VIEWER各自费用来源、public共享、限量前排除foreign/tampered/canonical/LIVE、三类同窗、GET-only、原私有载荷不泄漏、完整旧订单/成交计数、规则结果、受控跨表并发RR和第二新JVM重启重读。没有schema/migration或交易、资金、费用消费算法变化；V1—V24保持冻结。合成历史经济记录仅为测试域读取反例，不当作真实执行、结算或正式主线资格证明。

最终生产包新执行338前端单测、type/build，17个本轮前端文件Prettier语法树及稳定性通过。真实新PG/Servlet与临时18155生产预览覆盖真实空历史、1536中文owner、390英文owner及1280英文VIEWER；正式窗口200行逐身份一致，诊断筛选/原子重置/历史/重载、版本组/旧运行链接、相邻视图共享快照、复盘维度/评估排序、503隐藏旧数据后正式GET恢复、切换来源先取消读取、非法来源/筛选可恢复。相关业务请求全GET，0页面运行异常；故障注入503的console error单列为预期失败证据。成功随机库已删除，临时浏览器、预览和Servlet均退出，未替换18128/R9、18280/V22。Browser plugin缺失，沿用Playwright＋installed Edge。

独立审查绑定ui-r3冻结候选，实际执行和复用范围以报告为准。审查释放后最终ui-r4只修改历史刷新标签的3个前端文件，后端、解析、筛选及owner边界源码逐hash相同；作者重新构建并执行最终GUI。其独立安全/读取结论仅按原范围复用，不虚称ui-r4经过第二轮独立GUI审查。保留初次mock record身份问题、负零比较失败以及所有自动化驱动/fixture失败的原日志、DOM和截图；按真实控件、200全量与50重点子集合同、路由提交状态修正驱动，未删产品断言或放宽权限。

本次没有完成历史Paper组合概览/风险驾驶舱/策略排行三个旧区域，下一步必须继续承接，不能以新的独立SIM或本次三类分析替代。英文控件/枚举已覆盖，但规则引擎叙述仍中文并在英文页明确标识，尚不能声称全英文正文完成。更广角色、全部生命周期、全站E2E、Full Maven、联合独立复核、指南、源码导出、发行/安装和最终全任务格式仍待。639唯一ID和顺序保持，全站ACTIVE_PARTIAL，Git NONE。

独立审查另发现：发布引用已是有界IN读取，但既有BacktestEvaluationQueryPort批量方法的默认实现仍逐distinct backtest来源读取，最多200个来源时可能产生200条评估SELECT。其性能缺口继续待补齐，不能把方法名当整条链无N+1的证明；下一步与历史组合/风险/排行一起处理。

最终候选：`E:\Project\nq-production-reconstruction-output\legacy-paper-analysis-eae52867b4744aa7a98c443d7cf13f4c\ui-r4\build-proof.json`；GUI：`E:\Project\nq-production-reconstruction-output\legacy-paper-analysis-eae52867b4744aa7a98c443d7cf13f4c\gui-350c97a8c4fc4b67aee7c2872bb4df4f\result.json`；独立报告：`E:\Project\nq-production-reconstruction-output\legacy-paper-analysis-independent-review.json`（SHA256 `39cf56a7414bced27f6b0f7596218c824216e6035a9b073f0641755bce1ed29b`）。


## 历史Paper组合、风险与排行（2026-10-16，读取保护通过、计价来源能力未完成，未安装）

现有组合概览、风险驾驶舱、策略版本与发布排行已接回同一个历史快照。完整运行窗口最多 200 条，重点复盘清单仍最多 50 条，二者不能互相替代。运行、版本分组、发布及旧运行详情沿真实身份关联；筛选、排序、返回和重载保留 URL 状态。来源切换与独立 SIM 分离，不混入顶部账户资金。

历史权益表和日报没有记录计价币种，真实创建接口也不强制同一计价单位。反例在原实现得到未经证明的金额合计 `610`；在新历史读取合同中，计价状态明确返回 `UNIT_NOT_RECORDED`。组合金额、资金加权收益、按金额挑选的赢家、组合资金曲线及相应组级比较不再返回误导数值，无法判定的盈利/亏损策略数量返回 null。保留每次运行原始金额、单次收益率、回撤、执行计数与风险统计；均值类单次收益率仍是无量纲样本统计，不能改称资金收益。

必须新增能力账本记录：在真实历史事实生产或受控导入链路冻结计价币种、估值来源、适用范围和一致口径；按真实单位分组后才恢复跨运行金额与资金曲线。缺少历史来源的记录保持未知，禁止按 symbol 文本、当前 instrument catalog 或前端名称追填。当前只完成了读取保护和缺口表达，未完成这项来源能力。

此前发现的评估 N+1 已由单次有界参数化 IN 查询替代。真实 PostgreSQL 对 200 个来源实测 1 条评估 SELECT，与逐项读取的投影值相同；owner/public/disabled/tampered/注入边界随目标证明保留，不能单凭方法名宣称无 N+1。

共享数字展示改为有界十进制四舍五入和百分比换算，颜色按十进制符号判断，原始精确值保留在 title。已保留修复前 `9007199254740993.005` 被显示成错误值的反例。这只改变展示，不参与订单、资金或账务运算。

首屏改为五项运行/风险指标，资金字段集中到紧凑区；重复范围解释可点击展开，SIM/LIVE 与未知计价边界持续可见。手机沿用现有诊断壳的紧凑标题，首屏完整显示至少一项业务指标，没有另起设计方案。

作者证据：backend-r3 59 项目标测试（含真实 PG）；ui-r10 351 前端单测、类型检查、生产构建；gui-67ddcdb42fed4671bdcd2ff9e6696879 验证真实 PG/Servlet 与生产包，覆盖 1536 中文 owner、390 英文 owner、1280 英文 VIEWER、真实空源、200 条窗口、精确原值与显示舍入、键盘阈值、来源说明、真实对象链接、筛选返回重载、503 隐藏旧值后恢复正式 GET、取消挂起读取，以及第二新 JVM 不重置/不重种的精确十进制文本与业务指纹重读。相关业务请求均为 GET，页面异常为零；503 对应控制台错误是预期失败证据。合成旧经济样本只证明读取与展示，不证明正式经济执行或结算。

没有 schema/migration 或交易写入变化。未安装到 18128/R9、18280/V22；全站验收、Full Maven、真实发行包资格与最终全任务格式化仍未完成。Git NONE，U01 仍 NON_BLOCKING / PENDING_USER_CLARIFICATION。

独立报告：`E:\Project\nq-production-reconstruction-output\legacy-portfolio-independent-review.json`，SHA256 `698cbee847c3423937755e70af6ae9614de3ba4970f841786b6824618a965bb6`；MD SHA256 `de34f2acef591bee01aad685ee24f3ca78d07dae0aeeed00e0e95fa6de1aa519`。结论仅为报告声明的精确范围，不扩大为历史计价能力或全站接受。原639身份及顺序保留，另补4项真实功能/缺口，当前643唯一ID。

独立审查释放后，实际手机截图发现资产/复盘页仍沿用“诊断范围”名称，只修改路由壳说明与中英文目录三处展示文件，改为“范围与边界 / Scope and limits”；没有改变Java、费用范围、快照、数值、解析、筛选和排序行为。最终ui-r12重新执行351单测/type/build及真实PG/Servlet/两JVM GUI，并增加中英文评估页NULL盈利/亏损计数的实际展示与范围说明开关验证。原ui-r10独立结论仅在未变业务范围复用，不虚称ui-r12经过第二轮独立GUI审查。最终GUI：`E:\Project\nq-production-reconstruction-output\legacy-portfolio-622802d42dff492788fa3854e131898e\gui-c75a7dafdc7d401782e7b0ee350d6985\result.json`。


## 真实首次登录、改密、声明偏好与应用重启（2026-10-16，作者局部验证，未安装）

直接运行当前正式可执行JAR和生产前端，启用prod与正式空库管理员初始化；每次使用新的PG16数据库，不SQL植入用户、不注入测试业务Bean。首次只有一个需改密管理员，业务/偏好/运行就绪直读均被PASSWORD_CHANGE_REQUIRED拒绝，改路由和刷新不能绕过。实际浏览器验证返回登录清本地会话、确认密码不一致不发请求、错当前密码401、成功后清令牌并重新登录；同密码及75个UTF-8字节的非法密码由正式API拒绝，响应不回显密码。auth_version从正式初值1变为2，旧默认密码和改密前令牌失效。

保留一次真实产品失败：启用“不再提示”的登录未记录已处理结果，设置里重新开启会立即弹登录声明。仅修改LoginDisclosures的本次登录处理状态，兼容旧read标记，按持久化偏好跳过时保存suppressed；这些本地提示标记不是法律接受事实或服务端权威。设置重新开启后，当前登录及刷新不弹，下次真正成功登录再弹。认证、授权、服务端偏好并发版本、schema和canonical法律文档均未改。

真实DOM逐字核对当前中文/英文金融声明、完整双语DISCLAIMER及THIRD_PARTY_NOTICES。未勾选时每次成功登录提示，菜单切换和刷新不重复；勾选保存的明确503故障注入保留弹窗/勾选且正式持久化偏好和版本不变，解除注入后真实PUT200只递增一次并生效。设置页可主动查阅两项声明，正式保存英文并以390px实际渲染。浏览器中其余NQ主业务接口使用正式服务，合成随机密码不写入证据。

停止第一个应用进程并启动同一库的第二个新JVM，正式bootstrap仍开启；用户/角色/偏好完整指纹保持，改后密码可用，旧默认密码仍被拒绝。第二JVM浏览器验证偏好保持、重新开启只影响下一次登录，并在确认后刷新不重复。最终orders/trades/ledger_entries/paper_trading_runs/strategy_runs均为0。这里的重启是持久化后的应用进程重新启动，不声称graceful、PostgreSQL重启、发行安装器重跑或备份恢复。

ui-r2新执行351前端单测、类型检查、生产构建；本轮单文件Prettier语法树等价和稳定性通过。复用身份未变的正式后端JAR，未声称新执行Full Maven。Browser plugin缺失，使用既有Playwright和installed Edge，覆盖1536中文与390英文；故障路径401/503控制台消息按预期错误记录，页面运行异常为0。保留此前验证驱动初始化默认值、选择器、动画、展开状态及401导航竞争等原失败，不能追认为先前成功。成功隔离库删除，自有浏览器/前端预览/两后端进程停止；旧失败库保留，既有18128/R9与18280/V22未替换。

本轮为作者定向证明，认证和服务端安全代码没有变化，不冒称新增独立审查；最终集中独立复核仍待。原643唯一ID和顺序保持，仅更新11项已证明范围，不把设置整页、全角色或全站标完成。全部开发结束后、任何提交前仍须统一整理本任务全部新增修改代码，检查语义、显式import与短类型、中文注释和相关回归。全站ACTIVE_PARTIAL，未安装，Git NONE，U01仍NON_BLOCKING/PENDING_USER_CLARIFICATION。

候选：`E:\Project\nq-production-reconstruction-output\auth-lifecycle-f5b495917b234199b03f9078f7d3cc49\ui-r2\build-proof.json`；真实证明：`E:\Project\nq-production-reconstruction-output\auth-lifecycle-f5b495917b234199b03f9078f7d3cc49\proof-ac06d83cb8d94ef9a6efb4b2640d0acb\result.json`。


## 历史行情采集创建、执行与原结果恢复（2026-10-16，本机协议源局部验证，后续真源证据见补充）

采集任务页面已使用稳定jobId/runId的正式创建、执行和原结果查询接口。创建只保存任务范围；执行由用户另行触发。原输入在提交前持久保存，未知结果只能查询或明确按原编号继续，不能换号创建。失败运行仍保留其已确认结果，不自动执行为成功。服务端复用现有任务/运行事实、当前角色和事务行锁；没有新增schema或订单/资金路径。开始严格早于结束，断点按上一根closeTime后一毫秒继续，旧历史缺段不因此自动补齐。

所有创建、运行、目录与结果来自正式服务。任务目录和运行窗口各最多100项，数量不冒充全库总数；页面保留jobId/runId、市场、来源、创建人及创建/运行时间的详情，当前任务状态与所选历史运行结果明确区分。所选对象/页码进入URL，支持返回/重载、非法身份和越界窗口恢复；读取取消、错误与空态分开。交易对由真实有效目录与具体适配器支持交集决定。手机使用列表和完整时分秒控件，日期弹层第一次Esc只收起自身；创建结果未知时转到可操作的恢复面板，避免抽屉遮住原查询。

后端23目标测试含真实PG、Spring事务代理、观察到的行锁等待、唯一主键、回滚和精确重读，本轮UI阶段按未变Java源码/编译器AST复用。最新新执行368前端单测、类型检查和生产构建，七个本轮前端文件Prettier语法树等价且稳定。真实可执行JAR由正式主类启动prod+public-marketdata-manual，不打包测试入口/Bean；正式空库初始化后，通过真实登录/改密/偏好接口和显式角色夹具准备ADMIN/VIEWER。目录由正式同步接口经本机Binance协议测试源形成，K线由实际历史适配器、正式采集服务与Repository写入，不SQL植入行情或Order/Trade/Ledger。

作者浏览器实际覆盖1536中文ADMIN、390中文ADMIN日期表单和390英文VIEWER：逆序时间不发请求、完整时分秒、Enter不隐式提交；真实创建/运行POST200后明确丢响应，刷新不重发，再由原结果GET确认；请求在到达服务前被丢弃时，真实GET404后按原编号继续；真实协议源503形成持久化FAILED，不能假成功；对象选择返回/重载、非法ID无请求、越界窗口重置、明确读取503隐藏旧目录后正式GET200恢复、任务身份/来源跟踪字段及只读角色禁写/正式403。故障注入的响应丢失与读取503单列，不冒称NQ接口被模拟成功。Provider为明确的本机协议测试源，不是真实交易所联调。

最终两新JVM使用同一精确JAR、同一配置与同一PG库，中间不reset/reseed；任务/运行/K线完整持久化指纹保持。共有两任务、三运行、两根正式采集写入的合成来源K线；经济权威表均零。成功随机库已删除，临时浏览器、预览、后端和本机协议源退出；旧失败库和原日志/DOM/截图保留。既有18128/R9与18280/V22未替换。Browser plugin缺失，使用已有Playwright与installed Edge；控制台网络故障消息按预期注入记录，页面运行异常为零。

本轮修复的真实界面反例为日期Esc一并关闭创建抽屉、未知结果恢复按钮被抽屉遮挡；还保留了自动化选择器重复文本/重试按钮作用域、时间弹层退出动画的原失败。最终成功不追认旧失败为通过。只更新643项账本中的六个既有身份，仍标IMPLEMENTED_PENDING_VERIFICATION：当前GUI只覆盖BINANCE/BTC与ETH/1m及上述角色/视口组合，未声称所有市场周期、100项边界、触摸/原生缩放或所有异常已验。公开捕获、其他行情/质量动作、真实公开来源、独立复核、全站E2E/Full Maven、指南、发行安装/升级/恢复和最终全部代码格式化继续待。全站ACTIVE_PARTIAL，未安装，Git NONE，U01仍非阻断待澄清。

精确候选：`E:\Project\nq-production-reconstruction-output\ingestion-operation-a738f7cc464344db88a87c0379d635eb\app-ui-r5\build-proof.json`（SHA256 `9694fd1a096376a8f03ab6b852cd35f05227198a42918cab8bbc8e7c98cb7f1c`）；真实浏览器/两JVM证明：`E:\Project\nq-production-reconstruction-output\ingestion-operation-a738f7cc464344db88a87c0379d635eb\gui-8a6944e1ee574ca4a18aec731d95db9b\result.json`（SHA256 `cb345f661875f9aaf09cc8e5f5f49b83a1c1008db3c0a6f45806375dbab88c48`）。

## 真实公开目录与历史行情增量验证（2026-10-16，局部完成，未安装）

公开目录读取保留全交易对及精度规则，省略未消费权限集合并增加gzip、完整正文超时取消、8MiB传输及24MiB解压边界。本轮27目标测试通过，五个Java文件格式化前后编译器AST等价；独立审查另执行13探针、零finding，观察项是CPU解压/解析没有单独计时期限。未变前端368测试/type/build仅按原范围复用，旧死客户端包装清理已完成，最终全任务格式化仍待。

新生产JAR从官方公开域名实际同步3729条目录；通过正式稳定任务与运行接口持久化三根BTC-USDT/1m真实K线，并与独立公开GET逐项Decimal比对OHLC及成交量。实际状态为PARTIAL，计数为{'fetchedBars': 4, 'insertedBars': 3, 'updatedBars': 0, 'skippedBars': 1}；原因是来源多返回边界K线并被正式闭区间检查跳过。该历史结果保持原样；后续窗口修复及提示验证见下节。数据集只声明实际返回范围quality OK/gap0。

真实生产GUI覆盖中文1536与英文390：原任务/运行详情及跟踪、K线/成交量、调整一分钟开始时间后查询返回两根、数据集列表。两新JVM同配置同库精确重读/指纹保持，无SQL填充行情或经济事实；账户/订单/成交/账务/运行权威事实均零。成功临时库删除，临时进程停止；网络超时和自动化选择器失败原件保留，不将后续通过追认为原失败通过。Browser plugin缺失，使用已有Playwright及installed Edge。

精确候选：`E:\Project\nq-production-reconstruction-output\catalogue-read-4b4b3307b29c48c6a741f1cec684fc00\app-r2\build-proof.json`（SHA256 `732d6e5a39c3a948e8fa6e5a6f915bcd130e62862f8aede06e13d6c032dccb48`）；真源API/GUI/PG/两JVM：`E:\Project\nq-production-reconstruction-output\catalogue-read-4b4b3307b29c48c6a741f1cec684fc00\real-8f04066fa18a4fb1a48886634025cf54\result.json`（SHA256 `ebb45971695e982568b0929b183dc498a0cdfe67f23d15e57897f75a2ad60837`）；独立源码审查：`E:\Project\nq-production-reconstruction-output\catalogue-read-4b4b3307b29c48c6a741f1cec684fc00\independent-review-r2.json`（SHA256 `7f82b7bfa8d186ff4270a36dd037917a8d0e67fd95f7d3ec54a28dc364be9ccd`）。

全站仍ACTIVE_PARTIAL。公开捕获的稳定身份与未知结果恢复、其余行情/质量动作、历史估值来源、其余页面、全站E2E/Full Maven/集中独立复核、指南、确定性导出、真实发行资格与最终全部新增修改代码格式化仍待。当前18128/R9及18280/V22未替换，Git NONE，U01继续非阻断待澄清。

## 历史窗口、部分结果与手机刻度修复（2026-10-16，局部验证，未安装）

Binance历史请求现按完整K线闭区间换算为交易所开盘筛选边界，支持原六周期；开始向上取整至毫秒，结束减周期再加1毫秒，保留原桶和数值精度。不足完整K线的窗口不外联，逆序拒绝；旧运行与冻结来源保持原样，真实无效返回继续按质量校验进入PARTIAL。原先多取边界一根、结束少一毫秒仍多取、亚毫秒开始向下取整、空范围和逆序的失败反例已保留。

页面将PARTIAL显示为校验警告，FAILED显示为失败或外联禁用；原原因和实际计数/范围仍可查。空采集不推断来源已被访问。时间轴为现有带日期的标签预留16字符宽度，手机由拥挤三标签变为有间距刻度；K线数量、自动时区、详情时分秒和缩放保留。

新执行38适配器目标、2分发目标、372前端单测及type/build通过；2 Java＋6前端局部格式化AST等价。真源新身份为58bad7e8-c14e-4ba7-b7a5-59d4de4f5bc6/2c9f1b14-fd5e-48db-955d-7eb570ac9b14，三分钟窗口fetched3/inserted3/updated0/skipped0、SUCCEEDED，正式服务/API/PG写入并与独立公开GET逐项Decimal比较OHLC、volume、quoteVolume。正式所建实际范围数据集quality OK/gap0。中文1536桌面和英文390手机、两新JVM同配置同库精确重读/指纹保持通过；成功随机库删除，临时进程停止。

PARTIAL黄色与FAILED红色的中英文响应式验证是独立标记的只读响应夹具，并恢复正式SUCCEEDED再核对数据库指纹；这些夹具不证明真实部分失败业务。正式真源和经济表零事实依据不依赖夹具。未新做独立审查声明；原公开目录读取的独立审查仅按其未变文件范围保留，全站集中审查仍待。

精确候选：`E:\Project\nq-production-reconstruction-output\market-window-a4fcf5f56654496c98571c966e995f5f\app\build-proof.json`（SHA256 `ff1ce6ddc72577975ae3dbf35d545b54e9a3b0b7478ea77ec0ab2af91bcb1397`）；真源/GUI/两JVM：`E:\Project\nq-production-reconstruction-output\market-window-a4fcf5f56654496c98571c966e995f5f\real-438d72ddde554a9c949285ff8e748064\result.json`（SHA256 `f6a3654ca18d6ffeb0230d8bb31ac11a3cbcc446d9b3f2c5d307b15618d17660`）。旧PARTIAL、旧拥挤截图、原测试失败均保留，后续通过不追认旧失败。

接着完成公开捕获的稳定身份/未知结果恢复及其余行情质量动作，继续历史估值来源和其余全站页面。全站E2E、Full Maven、集中独立复核、指南、确定性导出、真实发行资格及最终全部新增修改代码格式化仍待。18128/R9、18280/V22未替换；Git NONE，U01仍非阻断待澄清。


## 公开历史捕获稳定请求与恢复（2026-10-16，作者验证及后端独立审查通过，正式GUI待验）

新增稳定UUID请求、原owner读取、冻结窗口与原结果回执；服务端先登记，成功捕获/数据集/回执同事务提交，同号重放终态，PENDING明确继续。来源失败与未知数据库结果分开；V25已在隔离PG执行冻结，历史迁移未改。正文8秒/256000字节、行锁3秒/单JVM2次/事务65秒边界保持。旧POST兼容入口保留。

页面已接入原请求保存、原结果查询、按原编号继续与确认阅读；刷新不自动POST，跨用户/旧token回执不消费，404不推断未执行。固定显示实际支持的OKX现货BTC-USDT/1h，完整时分秒与UTC整小时校验保留；修正不存在日期自动顺延、亚毫秒截断及选中编号变化时旧结果泄漏。摘要、规则来源、回放假设保留于折叠详情，数据集入口指向正式列表。

后端作者14项（包含真实隔离PG）通过；9文件独立审查findings=0，独立PG42项及传输7项通过，结论仅限精确后端候选。新增前端33项目标、全量405项单测、类型检查和生产构建通过；本批10个前端文件已格式化并证明AST等价/格式稳定。生产JAR构建成功，构建命令跳过测试，不称为Full Maven通过。来源夹具仅本机协议测试，不声明新真实OKX联调。本候选正式Servlet、浏览器和两新JVM证明仍待。

证据目录：`E:\Project\nq-production-reconstruction-output\public-capture-ca4347f6c7d34683b42b8c2c415728a9`；独立审查`independent-backend-review.json`、`ui-all-unit.log`、`ui-build.log`及`ui-format-prettier-ast.json`。18128/R9、18280/V22未替换，Git NONE。全站仍ACTIVE_PARTIAL；全部新增修改代码的最终统一格式化、显式import/短类型、中文注释检查与相关回归继续作为提交前必做项，当前未关闭。


## 公开捕获正式恢复、响应式及原失败收敛（2026-10-16，局部功能验证完成，真实OKX成功仍不可用）

正式生产JAR/Servlet/JWT与随机PG库已验证无认证拒绝、完整错误DTO、逆序/非零亚纳秒/过长窗口、同号同输入原结果重放、跨owner 404/身份冲突409、真实429失败冻结、并发时提交可见的PENDING及实际PG锁等待。杀死正在读取来源的第一个JVM后，独立登记保持PENDING、数据集未创建；第二实际JVM同库精确指纹保持，以原编号继续成功。来源是明确的本机OKX协议夹具，不能用作真实OKX采集证明。

浏览器实际发现并修复过期错误：查询已找回终态时，先前提交/继续的未知网络提示应清除，确认阅读后不能重新出现；本机确认失败仍保留。当前R2桌面中文1536验证响应提交后丢失、查询直接找回、刷新只GET、请求未到达后404及同号明确继续、查询失败隐藏旧成功、明确确认、失败状态/无效链接/正式数据集跳转。新候选type/build通过，10前端文件局部格式AST等价且稳定；405单测证据复用同一未变单测覆盖代码，不声明新运行。

相同R2候选的独立新owned clone补验英文390手机、中文当前VIEWER及关闭来源后原成功/失败回执读取和同号重放；新关闭来源请求冻结OUTBOUND_DISABLED，来源调用0，原数据指纹保持。普通账户/订单/成交/账务/SIM/策略运行权威表均为0。成功clone删除、临时进程停止；原失败数据库保持。初次手机夹具未等待正式账户语言偏好落定、随后只读夹具误删唯一管理员触发认证前提，均按夹具失败保留；不改变产品守卫，也不将后续通过追认为原运行通过。

精确R2候选：`E:\Project\nq-production-reconstruction-output\public-capture-ca4347f6c7d34683b42b8c2c415728a9\ui-r2-candidate.json`。桌面/正式接口/两JVM范围来自`servlet-379d0158761f48938e8da2d0bddb147f`；该运行后来手机夹具失败，使用其中已通过的明确范围，不称整轮通过。手机/VIEWER/来源关闭补验为`servlet-b0537d9c9c70411face3dc2ab5747558/result.json`，完整保留两份证据关系。

另一次直接真实OKX正式Java捕获为`servlet-8d0ee356b6dd4694a6f2d8e70cee39fc/real-source-result.json`，结果FAILED/PUBLIC_MARKET_TRANSPORT_FAILURE，原GET及同号终态重放一致，无数据集或经济事实；独立解析`www.okx.com`为gaierror/11004。状态明确为UNAVAILABLE_REAL_OKX_SOURCE_WITH_FORMAL_FROZEN_FAILURE，保留隔离库，不使用本机夹具替换这个事实。

18128/R9及18280/V22未替换，Git NONE；全站仍ACTIVE_PARTIAL。继续数据集创建/质量动作的未知结果与事务闭环、历史估值及剩余功能；全站E2E、Full Maven、集中独立复核、指南/发行包验证与最终全部新增修改代码格式化仍待。U01非阻断待澄清保持。


### 数据集原请求与事务恢复进展（2026-10-16）

数据集创建和行内质量刷新改用稳定原编号，保留表单、实际目录品种、名称搜索、分页、质量及完整时间。POST前保存原输入；未知结果只读恢复，明确继续同号；终态须确认阅读后才能发起下一次。当前只读角色及公开捕获不可变限制有可见原因；操作成功与数据质量合格分开显示，原次覆盖不被后续刷新替换。

修改前真实PG反例留下dataset=1/coverage=0，已保留；现在数据集、覆盖、主状态和成功回执同事务，PENDING独立登记。V26已执行冻结，不就地重写。后端14目标通过；独立原审查发现写入器跨事务边界DQ-IR-001，修复及真实PG补审关闭，原反例保留。正式JAR/Servlet/JWT六组HTTP证明包含真实锁等待和两新JVM恢复；中文1536桌面、英文390手机及当前VIEWER三组GUI已分别完成。它们来自相同精确候选的有效分项与补验，不把早先因夹具失败的整轮追认为通过。GUI仅有明确目录元数据夹具，行情表为空，不声明真源或数据质量合格。

证据：dataset-operations-b45c5630aa8d48e3914db4492b24fb59/resolution.json；ui-candidate.json；target-writer-fix.log；frontend-unit-r2.log（436项）；frontend-build-r2.log；ui-format-r2-prettier-ast.json。局部Java与前端格式化语义等价且稳定；全任务全部新增修改代码在开发完成后、提交前仍须统一格式化，检查显式import/短类型、中文注释与相关回归，当前未关闭。18128/R9及18280/V22未替换，Git NONE；全站仍ACTIVE_PARTIAL，历史资产/概览及其余全站收尾、Full Maven/E2E、联合独立复核、指南和确定性发行包资格未完成。


### 历史规则说明、聚类正文及原输出（2026-10-16）

原React渲染反例确认：聚类说明与排查建议误用状态枚举翻译器，正文显示成未知值。现在自由正文按156条当前正式文案及确定动态模板本地化，未知新模板原样显示；数字、单位、评分、对象ID及后端结论保持。复盘事实清单的比例明确标注比例单位。新增各区域“原始规则输出”按需展开，键盘及精确回执等值验证通过。实际英文手机截图中未绑定版本占位仍为中文，另行修正并补验；实际命中的对象ID不改名。没有恢复缺失计价单位的金额合计，也没有声称历史权益来源能力完成。

前端446项测试、类型和生产构建通过；十文件局部格式化AST等价。初始GUI中英各1536/390的诊断、复盘、评估共12组，通过后针对新缺失标签修正补验4组；控制台和pageerror为0，业务请求均GET、原元数据指纹保持。后端复用相同V26 JAR，夹具只含明确历史元数据，不插入经济事实，不替代正式执行/结算证明。证据：paper-analysis-copy-64cbe524874f4df5a2631f8b4e61bfd6/resolution.json、ui-r2-candidate.json、frontend-unit-r3.log、frontend-build-r2.log及format-r3-prettier-ast.json。全站仍ACTIVE_PARTIAL；历史估值来源、全站E2E/Full Maven/联合复核/发行及全部新增修改代码最终格式化未完成。未安装，Git NONE。


### 统一币种基础与旧Paper来源决定（2026-10-16）

用户明确决定旧Paper历史来源为UNVERIFIED：保留全部原金额、时间与source，不推断USDT、不覆盖历史，不进入需要统一计价单位的账户汇总；该历史调查非阻断且不重复索取未经证实的来源。V27新增可扩展currency_definitions和权益快照六项估值元数据，V26→V27迁移/validate证明原11列指纹不变、币种NULL及UNKNOWN。已执行V27冻结，不修改V1—V26或就地重写V27。

新增只读GET /api/reference/currencies（需认证），首批USDT、USDC、USD、BTC、ETH、CNY。valuationSupported仅表示该币种可作为权益统一计价目标：当前只有USDT；不表示目录内其他原币资产有来源时不能折成USDT。注册不授予交易或结算能力。displayConversionSupported当前全部false；CNY只预留展示用途，无人民币结算或无来源切换。不同币种永不默认固定1:1。

账户表现推荐使用valuationCurrency参数与响应属性；原quoteCurrency参数/属性保留兼容，其原含义就是账户权益计价单位，不是交易对报价币。双参数冲突返回400，不支持的目标返回400，无权账户仍404。正式采样复用同一余额投影与canonical资金分录，保存原币金额、源bar身份、观察/可见时间、转换价格与来源；未来、过期或缺失价格不生成完整金额。数量已含仓位，不重复加持仓。精确乘积在最终总额按16位HALF_UP舍入一次；比例仍无币种单位。sourceJson追加估值时间、来源、方法、状态、逐资产换算及精度依据。账户资金流不完整仍不输出收益。新Paper继续使用既有canonical SIM的持久化决策、成交和账务重建表现，新增明确计价属性、原价时间与决策来源，不在GET中复制或创建账务。

独立审查实际复现旧Paper仓储的伪造VERIFIED来源及numeric分别舍入破坏分项合计两个反例。由于旧表没有已证实的正式producer，已移除新增的声明式验证/插入能力：该旧insert接口现在一律在SQL/连接前拒绝，机器码LEGACY_PAPER_EQUITY_PRODUCER_UNVERIFIED。旧记录单条/批量读取继续正常；新metadata SQL往返只以明确PARTIAL夹具测试，不能当可信生成链。两个finding经独立定向补审关闭，原反例库保留。旧Paper可信新采样器仍未实现，不能把关闭风险写成该能力完成。

本轮30后端目标测试（含真实隔离PG/完整AppContext/Servlet/JWT）、449前端单测/type/build通过，局部20 Java及12前端文件格式化AST一致且稳定。生产包与正式JAR/Servlet配合随机隔离PG，在中英文各1536/390验证六币能力、键盘展开/关闭、账户明确计价、空金额不作0、旧Paper保护及无横溢出；控制台/pageerror为0。GUI夹具只有历史元数据，经济表全0，不代表交易或新权益生成验收。临时GUI进程停止、成功库删除。18128/R9与18280/V22未替换，Git NONE；最终全任务统一格式化及全站验收仍待。

完整mvn -B -ntp test已在预迁移V27的随机owned PG15442库执行，未改测试列表或削弱断言；首次因PaperRunCanonicalProjectionTest未先提供新只读存在性校验所需run夹具而失败，其他后续模块SKIPPED，不报Full Maven通过。该轮960报告测试、13条件跳过、1错误；收集器长路径失败已单独回收报告，原失败与库保留。后续按真实失败修复夹具并重新执行完整命令。


### 完整后端回归收敛与新SIM计价复验（2026-10-16）

完整 `mvn.cmd -B -ntp test` 在新建随机PG15442库完成：590份本轮fresh Surefire报告，2854项报告测试、350原条件跳过、0失败、0错误。原四轮失败分别保留，未排除测试或放松业务断言。第二轮后来回收的XML混入目标测试，已标明无效；该轮原Maven日志仍为失败依据。独立目标重启测试另证明两场景均实际跨两个JVM，进程间持久化、唯一成交/账务、部分成交恢复和原DB身份断言通过。

实际修复包括：Paper路由测试先提供真实存在的run夹具并补缺失对象不访问事实源；OKX盘口注册改为两个开关均显式true，非法文本不再进入SpEL；币种注册表延迟到首次实际估值才读取并缓存，恢复既有零DB启动合同；恢复测试精确核对当前V27且仍执行Flyway validate/pending=0及全部双JVM断言。两个生产修复已分别由独立审查复现边界并接受。账户估值来源JSON新增的amount/value/conversionRate/rate以精确词法读取；大金额、尾零、整数身份及接近1的真实汇率用例通过。四文件本地格式化AST等价且稳定，最终全任务统一格式化仍待。

新的正式SIM由账户、策略/版本、公共捕获、研究、回测、评估、发布和SIM创建/启动/推进/撮合/停止接口形成，行情为明确标识的录制OKX协议本机夹具，费用为冻结实验输入，无真实私有来源。所得72点权益、22订单/22成交、90条账务在两实际JVM间重读完全一致；逐点明确USDT、原价观察/可见时点、估值方法和decision来源，独立十进制复算权益=现金+数量×价格。读取前后经济表指纹不变，旧权益表仍为0，未新建第二套账本。不能据此冒充真实交易所联调或旧Paper可信新采样器完成。

新生产包与同一正式JAR在中英文各1536/390验证资产与收益及复盘与评估、精确首行、刷新身份、非空曲线和无横溢出，控制台/pageerror为0。最初结构检查通过后人工发现390权益图时间刻度重叠，已在共享账户/SIM权益图改为日期/时分分行并隐藏碰撞标签；重新构建及真实四组合GUI、实际截图复看通过。UTC事实、自动时区、估值、完整秒Tooltip及明细不变。夹具步骤和GUI断言错误各有原失败库/日志，不追认为原轮通过。

证据：`full-maven-078e82884c0f40259bb91e057c075196/result.json`、`full-maven-remediation-ce11020ed42f4b66bf96a5368e4c8a43/resolution.json`、`equity-axis-b48cc063f5294eaa95159b3a5abd9a94/resolution.json`（均在外部实施输出根）。成功临时库与进程已清理，原失败保留；18128/R9、18280/V22未替换。Git NONE，整体ACTIVE_PARTIAL；继续其他功能/状态、全站E2E/视觉、联合独立复核、指南、确定性源码导出、发行包本地资格及最终全部新增修改代码格式化。


### 对象搜索的新运行链与焦点交接（2026-10-16）

本人正式账户、策略、版本、运行、SIM和订单六类搜索均经正式Servlet/JWT读取，另一身份对六类均无结果、对原订单详情返回404。搜索仍是2—80字模糊匹配，不把一位账户ID作为合法查询，也不把发布衍生策略包含原ID的两个合法命中误判为重复身份。每个目标命中唯一，定位按该命中的正式ID读取详情。

真实目标订单、22笔买卖成交、90条账务均由正式新业务链生成。只为列表边界额外加入500条明确REJECTED分页元数据，不生成假成交或账务；有界facts仅返回前500条且不含目标，搜索仍能精确打开原FILLED订单及其真实关联成交/账务。两实际JVM重读及GUI后经济表指纹保持，GET没有推进、撮合、撤单或修改默认账户。不能把500条元数据计作交易能力验证。

真实GUI复现搜索关闭后将焦点抢回顶栏按钮、导致新运行详情Esc失效；ObjectSearch现仅在成功定位时关闭触发焦点恢复，目标抽屉持有焦点；普通取消仍返回搜索按钮。中英文1536/390共12次RUN/SIM/ORDER定位、窗口外订单刷新、详情Esc及普通取消焦点返回通过，8次实际DOM焦点均在目标抽屉根内。控制台/pageerror为0，无页面横溢出；实际复看手机订单及桌面运行截图。动画时序、文本oracle和分页夹具预期错误各保留原失败，未将其追认为整轮通过。

证据：外部`search-chain-a17cee70c6e645e5b9c6c6b156cabf5d/resolution.json`和`focus-ui-candidate.json`。一个前端文件本地格式化AST等价、type/build及真实四组合GUI通过；后端未变，复用已通过的完整Maven，不声称新执行。普通账户订单搜索定位及全站其他状态仍待；18128/R9、18280/V22未替换，Git NONE，整体ACTIVE_PARTIAL，最终全部新增修改代码格式化仍未完成。


### 普通账户订单精度、人工提交与搜索定位（2026-10-16）

普通交易五个GET保留原JSON十进制词法，金额/价格/数量/费用用精确文本展示，空费用、零和负返佣不混淆；账户ID及计数仍为整数。人工委托价格和数量采用stringMode，正式POST继续由原DTO、Risk及Execution校验。真实页面发现并修复查询/下单表单控件ID重复，以及未填策略运行ID被传成空串而遭策略入口guard拒绝；现在只省略空白身份，真实非空身份仍保留并受正式策略入口限制。

新执行453前端单测、type/build及局部格式AST等价验证。正式生产JAR/Servlet、随机PG及两实际JVM核对价格9007199254740993.12345678和数量12345678.12345678完整保持，同客户端ID仅一条订单。中英文1536/390四组合从普通ORDER搜索进入临时账户，精确详情/刷新、唯一标签关联及实际字符串表单POST通过。最终5单均为RISK_REJECTED，0成交/账务，默认账户和kill switch未变；这不是成功成交、账务或全站资格。独立原审及实质请求映射补审均PASS、0 findings。前两类GUI脚本/标签问题和原400响应分别保留，不追认历史失败通过。

证据：E:\Project\nq-production-reconstruction-output\ordinary-trading-precision-59b496848d8a4066b25d6ed234c4f1bd\resolution.json。旧Paper来源仍未知，可信新采样器未实现；只读检查与正式下单命名、拒绝反馈、手机排版及其他工作台功能继续处理。18128/R9及18280/V22未替换，Git NONE；全站ACTIVE_PARTIAL，最终全部新增修改代码统一格式化/短类型及中文注释检查仍待。


### 工作台只读检查、真实分页及拒绝反馈（2026-10-16）

“下单前检查”现在单独读取既有GET /api/trading/preflight/readiness，明确本地诊断、主要真实交易阻断范围及不授予交易权限；刷新也是GET。“下单/提交委托”继续POST原正式接口。回执按唯一已知状态展示：RISK_REJECTED/REJECTED为拒绝，FAILED为失败，部分成交与全部成交分开；未知或歧义状态不推断成功，原始detail、action与trace可展开核对。当前人工请求映射及后端/JAR未变。

订单列表接回正式分页，筛选使用当前15个正式状态及可理解的中英文名称，去除不存在的CREATED；翻页沿用已提交筛选。原订单深链接可用正式订单交易对读取持仓；缺失仍明确为空。手机输入和详情改为单列/响应式，完整大数价格及小数保留；内部账户兼容身份收进详情，业务账户范围在主区说明。

R3全量458单测/70文件通过；R4只增加焦点容器，另新跑8个相关目标测试及type/build，原未变测试覆盖复用。10文件及焦点修复1文件局部格式AST等价且稳定。正式JAR/Servlet、随机PG和两实际JVM，25条正式Risk拒绝订单验证第二页及orderId/symbol/status筛选、重置；中英文1536/390四组验证只读检查GET、刷新、明确503响应夹具后正式GET重试、拒绝警示、原回执、精确输入、详情刷新和无横溢出。共29条订单仍为RISK_REJECTED，0成交/账务，默认账户及kill switch未变；不证明成功撮合或其他写动作。503为前端响应夹具，不宣称真实后端故障。先前实际焦点掉到BODY导致Esc失效，已以持久容器只接回丢失焦点；四组失败状态和恢复成功后的Esc均实际通过。原失败及脚本loading点击竞争分别保留。

证据：E:\Project\nq-production-reconstruction-output\ordinary-workbench-5804fa3e230d45e69b8b641cc306c227\resolution.json、ui-r4-candidate.json。原精度/请求映射独立审查按未变范围保留，本轮不冒充全页独立接受。普通委托未知结果恢复、其他交易动作/角色及全站收尾继续；旧Paper来源未知与可信新采样器缺口不变。18128/R9及18280/V22未替换，Git NONE；全任务最终统一格式化、中文注释与显式import/短类型复查仍未关闭。


### 普通交易归属与原客户端身份查询（2026-10-16）

普通交易列表、详情、成交、账户与持仓读取以及下单/撤单入口，使用正式认证主体和账户owner过滤，订单按真实legacy桥接、交易所与SIM/LIVE范围校验；不存在和无权均404。缺少legacy桥接时拒绝，不把相同数字当作映射。新增只读GET /api/trading/orders/by-client?accountId&clientOrderId：accountId是当前用户规范账户；client身份逐字匹配，不裁剪或改大小写，404不能解释成未执行。

两种撤单locator先解析并共同检查目标归属、LIVE禁写与可选请求账户范围，再将稳定orderId及已验证account/venue/symbol传给原canonical命令，禁止命令按无范围组合键重选目标。全局维护接口、风险执行路径与账务未改。本轮51目标测试通过；正式Servlet/JWT/完整V27隔离PG及双JVM验证跨owner拒绝、VIEWER自有读取/写入403、三个同client不同账户/用户原订单，以及重启后的精确金额和经济指纹。两条明确SQL合成异常LIVE/错venue存量行在两种locator和读取均404；不声称正常API会产生它们。0成交/账务，默认账户和kill switch未变。

独立R1确认OWNERSHIP-001并拒绝，原报告与反例保留；R2仅两文件修复经38独立检查补审通过、0新finding，44项未变原检查按范围复用，未实际执行取消或私有gateway。九文件初始及两文件R2局部格式AST等价稳定。证据：E:\Project\nq-production-reconstruction-output\ordinary-order-recovery-eb2b7967558c4664b07f1f9325dd65f9\ownership-resolution.json。普通前端自动编号/未知结果恢复尚待实现，成功取消、其他动作/角色、全站验收及最终全任务格式化仍待；当前源码全量Maven待重新验证，旧全量通过不覆盖新增权限代码。18128/R9和18280/V22未替换，Git NONE。旧Paper来源未核实、币种UNKNOWN、禁止无依据回填及可信新采样器缺口保持。


### 人工委托自动身份与未知结果恢复（2026-10-16）

人工表单只要求交易对、方向、限价/市价、价格和数量，当前规范账户提供账户/venue，系统生成稳定manual身份；不再要求填写内部订单、账户和策略运行ID，策略委托仍从策略运行进入。普通金额写入前按numeric(38,8)校验，stringMode/JSON文本保留精度；市价省略遗留隐藏价格。每主体最多保留一份原请求，以Web Locks协调标签页，POST前保存并读回owner、canonical/legacy映射、venue、精确输入及原client；存储或协调不可用时不发送。正式账户再查归属映射/启用及SIM。回执文本不作成功事实，正式GET原账户/原client返回的订单须逐项身份/精确数值一致才确认。

响应不明保留UNKNOWN；刷新/重进不自动GET或POST，不能换号。404不能证明未执行；只在本会话原账户查询404后，由用户在确认弹窗明确继续原编号、原输入。原订单确认后可查看正式详情，明确已查看后才释放本地记录允许下一笔；确认记录存在不代表成交。切用户、账户和同用户重登录后的迟到响应均不能确认或污染另一记录。正式响应允许Jackson真实的1E-8、1.0E-7等有界精确展开；手工输入规则不变，不使用Number金额或静默舍入。未知/拒绝/记录存在与成交区分。

R2全量509单测/71文件通过，本模块51条；R3仅一条已确认后隐藏旧404说明的呈现条件，金融与请求源码字节未变，复用R2单测，新执行R3 type/build及完整GUI。7文件三版局部格式AST等价稳定。独立R1发现SUBMISSION-001并拒绝，原反例保留；R2对修复168断言补审通过，0新finding，64项未变正常guard按范围复用。正式Servlet/JWT/完整V27隔离PG、实际生产bundle和两JVM验证中英1536/390四组、共8笔人工委托：四次正式POST落库后运输响应丢失，四次发送前abort、正式GET404后用户明确原号继续；每原身份恰好一单，刷新没有自动写入，确认框取消不POST，大额和最小价格/小额数量精确读取，旧404说明确认后消失，所有8次抽屉焦点inside=true/tag=DIV且Esc可关闭。0未预期页面/控制台错误，无横向溢出；真实缺失资源404及8次运输abort分开记录。

数据库共9单（1正式种子＋8GUI）：5 RISK_REJECTED、4 SENT，0成交/账务；SENT是real/outbound provider禁用时的canonical记录状态，不证明真实私有发送或成交。重启后身份、精确数值、状态与经济指纹一致，默认账户/kill switch未变。成功随机库已删，临时进程停止；原失败库和证据保留。证据：E:\Project\nq-production-reconstruction-output\ordinary-order-submission-8ffdd505e8c340598dd2cb8924521425\resolution.json及ui-r3-candidate.json。

其他结果、成功取消/外部gateway、剩余角色/故障及全站验收待完成；当前Java增量后的全量Maven仍待，旧通过保留原候选身份。旧Paper来源未知/币种UNKNOWN与可信新producer缺口不变。18128/R9及18280/V22未替换，未执行Git或发布；最终全部新增修改代码的统一格式化、中文注释与显式import/短类型复查仍未关闭。


### 当前候选收敛与保留失败（2026-10-16）

本轮状态：`CONSOLIDATION_BLOCKED / WITH_EXACT_EVIDENCE`。唯一候选见外部 `E:\Project\nq-production-reconstruction-output\consolidation-7f314d662698474ba2fe233c4ec6be56\candidate-final.json`，阶段报告 `E:\Project\nq-production-reconstruction-output\consolidation-7f314d662698474ba2fe233c4ec6be56\REPORT.md`。全站仍为 ACTIVE_PARTIAL，未提交、未发布，最终全任务格式化与集中独立验收仍待。

撤单R3正式HTTP/PG/双JVM/中英桌面手机GUI已通过。原失败由AntD加载图标改变可访问名称引发，仅修外部驱动定位；UNKNOWN仍只查原单，未把CANCEL_REQUESTED当成交易所最终撤单。原失败证据保留。只修5个既有PG测试夹具，生产Java/前端/迁移字节与构建时一致；不重构撤单流程。

651行旧账本和全部唯一ID保留，175 ACTION归并70动作功能，完整功能目录114项（112项V1、2项明确排除）：13 VERIFIED、92 IMPLEMENTED待验、4 BLOCKED_EXTERNAL、3 PENDING必做未实现。新增的第114项是原账本已记录的旧Paper可信权益新生成器缺口，单列以免被历史来源未知掩盖，不是新增任务范围。其余必做缺口为通知来源归并与前端更新流程衔接。31页面已实现及读取冒烟、8手机读取冒烟；整页全动作/角色/异常/未知状态验收0/31，不能把入口通过视为完成。

本轮集中一次Full Maven报2865条（350原条件跳过、0失败/错误）、前端550项/72文件与生产构建通过。Full Maven在5个测试夹具修正之前执行，不宣称当前测试字节全量再通过。显式资金PG专项R2与R3补验组合为51通过、1失败、1原条件跳过；持续SIM自动调度任务过滤断言expected1/actual0仍失败，达到两轮修复上限后停止。它没有走到自动调度经济闭环后续断言，不能报该路径通过。费用正零负、优惠资产及手动推进SIM的通过仅按明确分项复用。

新候选前端http://127.0.0.1:18160、后端http://127.0.0.1:18290，独立V27库。真实正式API主线形成72权益点/22订单/22买卖成交/90账务，重启重读及逐点十进制复算通过；公共行情为明确录制loopback协议、费用为冻结实验来源，不能冒充真实外部联调。无公开feed时continuous-sim关闭，strategy-sim可用，LIVE/私有provider/outbound均关闭。当前不是正式发行包，安装升级与备份恢复未重新资格验证。原18128/R9、18280/V22、C4、18099和历史证据均保留。本轮后停止，等待下一阶段。


### M2-A 定向功能收尾（2026-10-17）

本批停止于 `M2A_PARTIAL / EXACT_BLOCKERS_RETAINED`。报告与唯一新前端候选见外部 `E:\Project\nq-production-reconstruction-output\m2a-functional-ed97d7d3dde949cb90f745ed1c98ab50\REPORT.md` 及 `candidate-final.json`。当前18161使用新的正式前端Production Build，复用未变的18290 Java及15442隔离PG；18160、18128、18280、C4、18099未覆盖。

持续SIM expected1/actual0根因为Spring 6.2任务包装导致测试instanceof误过滤，已精确解包并保留目标bean/方法/数量1断言。单一失败用例及相邻35通过/1原条件跳过；核心路径PG原3失败仅修V27预期、维护库夹具及专用驱动环境后补验3通过。组合58个不同后端方法当前分项通过、1条件跳过，不冒充一次全量Maven通过。旧失败记录保留。

唯一生产行为修复为回测启动/评估/发布错误后重读原运行与相关查询；409可能已将服务端运行置FAILED，页面不再滞留CREATED。16目标前端测试及type/build通过，18161真实GUI只发1次启动POST，409后原GET为FAILED、启动禁用，刷新不重发。其他成功GUI链在18160执行，相关组件字节未变；两份不完整输入失败保留，成功完整实验配置由正式API准备，不能冒充纯GUI完整建配验收。

114功能仍按原分母：14 VERIFIED、91 IMPLEMENTED待验、4 BLOCKED_EXTERNAL、3 PENDING必做未实现、2 OUT_OF_SCOPE。仅JOB-RUN按当前GUI/正式API/10项PG及未变恢复单测证据升级；其余新正常子路径补证据，不批量升级。31整页验收仍0。三必做为通知来源归并、前端确认更新衔接、旧Paper可信权益新生成器；四外部依赖保持真实公共采集、私有费用来源、四价时点、历史Paper来源。空instrument目录是本地夹具缺口，不新增外部阻断类别。

本批没有修改Java生产、Ledger/Paper撮合、风险、订单状态机、schema/Flyway；没有Git提交/发布、真实交易或LIVE。最终全任务统一格式化、集中整体验证、安装升级/回滚资格及最终独立接受仍待；不自动进入下一批。


## M2-B1 三业务页面验收（2026-10-17）

`M2B1_PARTIAL / EXACT_BLOCKERS_RETAINED`。本批仅策略、研究、回测及必要既有结果路由，未进入下一批。最终候选 `m2b1-pages-e49ceaf2f24044949cb4c2abf525cff4-r5`，前端 http://127.0.0.1:18166，复用18290正式Java与15442隔离PG。19个前端文件已作限定格式化，AST相等且格式稳定；未展开全任务最终格式化。

主账本保留原651条和历史状态，新增两个既有归档能力映射，现653条、177 ACTION、116去重功能。互斥统计：16 VERIFIED、85 IMPLEMENTED_PENDING_VERIFICATION、9 REQUIRED_NOT_IMPLEMENTED（含6项主线存在但必要分支未闭合）、4 BLOCKED_EXTERNAL、2 OUT_OF_SCOPE；全页0/31。3业务页均PARTIALLY_VERIFIED。

真实研究/回测归档均400：archivedBy长度超过128，原对象仍ACTIVE，不能报归档成功；创建UNKNOWN持久恢复和策略关联呈现仍缺。新GUI回测已成功运行、评估、发布；草稿改绑第二版后，旧运行首版快照不变。44目标单测在R3执行通过，R4/R5只复用对应不变范围；最终type/build与三对象读取冒烟通过，18组中英/视口证据来自R4并以限定格式AST相等关联。未重跑完整Maven、前端全套或全站E2E。

详见 `E:/Project/nq-production-reconstruction-output/m2b1-pages-e49ceaf2f24044949cb4c2abf525cff4/REPORT.md`、FUNCTIONS.csv、ACTIONS.csv、PAGES.csv、REMAINING.csv、NEXT.csv 和 candidate-final.json。无Git提交、远程发布、真实交易或LIVE；原环境与证据保留。


## M2-B1 定向闭合（2026-10-17）

`M2B1_CLOSURE_PARTIAL / EXACT_BLOCKERS_RETAINED`。当前候选为 m2b1-closure-6d90ac41757640b0b24b4d33bd5e8ef3-r4，正式前端 http://127.0.0.1:18170，R3正式Java 18293，PG16/15442上的本批独立克隆库V28。原18166/18290/V27及受保护环境保留。

两种归档JWT操作者400已修复，真实正负例/重复/刷新/列表详情及R4失败保留原输入后重试成功有证据。五类创建新增最小稳定操作回执，与既有正式创建服务同事务；原UUID/输入、owner、原对象查询及UNKNOWN刷新恢复已实现。策略关联按具体版本→发布→冻结回测/研究/配置/数据集/评估→SIM窗口读取，不用当前草稿替代历史。

116去重功能：18 VERIFIED、89 IMPLEMENTED_PENDING_VERIFICATION、3 REQUIRED_NOT_IMPLEMENTED、4 BLOCKED_EXTERNAL、2 OUT_OF_SCOPE。M2-B1的新增6项是已有功能必要分支下调，不是新增6个独立功能；本批补实现但不批量标验收通过。三个业务页均PARTIALLY_VERIFIED，全页仍0/31。剩余3项原V1未实现能力为通知来源归并、正式更新衔接、旧Paper可信新权益生成链，本批未扩展实施。

新执行43前端目标测试、type/build、68次正式HTTP/PG定向请求及最终13次正式读取，分项通过；两Controller目标9测试按未变逻辑复用。未跑全量Maven/前端全套/全站E2E。陈旧运行GUI409夹具精确定位GET后出现Invalid InterceptionId，未发送start POST，不冒充通过。全部测试按候选与复用范围见报告；保留R1/R2失败。

详见 `E:/Project/nq-production-reconstruction-output/m2b1-closure-6d90ac41757640b0b24b4d33bd5e8ef3/REPORT.md`、FUNCTIONS.csv、PAGES.csv、REMAINING.csv、NINE_REQUIRED_GAPS.csv、candidate-final.json。没有Git提交、发布、LIVE、真实交易或修改历史Flyway；本批到此停止，等待下一阶段。


## M2-B2 账户、行情与数据页面验收（2026-10-17）

M2B2_PARTIAL / EXACT_BLOCKERS_RETAINED。最终候选 http://127.0.0.1:18173/ ，独立PG V28，复用精确不变的正式JAR。修复D01默认名称、质量诊断路由及目录只读入口；8 ACTION新闭合。116功能：22 VERIFIED、84 IMPLEMENTED_PENDING_VERIFICATION、4 REQUIRED_NOT_IMPLEMENTED、4 BLOCKED_EXTERNAL、2 OUT_OF_SCOPE。原3项新增能力保留；第4项是既有INSTRUMENT-SYNC缺原请求结果查询分支，来源为本批明确验收要求，未新增独立功能。DATASET-CREATE因D01完整GUI正例受空目录限制由VERIFIED下调PENDING。3目标页仍PARTIALLY_VERIFIED，全站完整页0/31。未执行Git提交/发布或真实交易。报告：E:\Project\nq-production-reconstruction-output\m2b2-acceptance-82dd5144adf3473f8602df5af3ac72e3\REPORT.md。本轮结束，不进入M2-B3。
