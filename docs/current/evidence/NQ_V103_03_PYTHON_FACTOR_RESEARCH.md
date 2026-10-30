# NQ-V103-03 Python 因子研究

日期：2026-10-30，Asia/Shanghai。本文记录本轮实现与本地证据；PR/合并/最终清理身份在仓库外 `E:/Project/NQ_V103_03_PYTHON_FACTOR_RESEARCH.md` 交付收尾记录，不能用本地PASS替代准确提交的CI。

## Baseline

初始分支dev、工作区干净，HEAD与fetch后的origin/dev均为 `df2d8057afc0e6ce7f742ccbacfe30ac2a99db46`，无前进差异。首次直连fetch连接重置，经命令级现有127.0.0.1代理重试成功；没有修改Git代理配置。任务分支 `codex/nq-v103-factor-research`。开发schema V52，没有新增或修改migration。正式发行v1.0.2及其资产不变。

原规划建议持久化因子表且不做相关性统计；本轮按用户明确要求实现无持久化、只读身份核验及最小时间序列统计，没有新增表、Python服务、Java subprocess、策略引擎或交易事实。LIVE=DISABLED、Kill Switch=ENGAGED；未访问私有provider或生产运行环境。

## Python Existing Capability / Java-Python Boundary

旧工具链提供CSV加载、manifest、实验元数据、基础价格诊断、canonical JSON与CLI。旧python-evaluation-artifact.v1要求strategyVersion、收益、成交和费用字段，不能准确表达因子研究。本轮新增独立 `python-factor-research-artifact.v1`，旧CLI与旧评价合同保持兼容；不虚构策略收益或交易事实。

Java仍是canonical trading authority。Python只读本地导出、计算和写本地Artifact，不持有Java DB凭据。Java仅验证当前用户的数据归属、冻结身份、摘要及报告格式，不重算统计、不落库、不激活或发布策略。界面明确披露本地统计并非Java验证过的科学结论。

## Research Export Contract

认证 `GET /api/marketdata/datasets/{datasetId}/factor-research/input` 只导出已存在的owner V2，未冻结则拒绝，不回读可变行情、不产生版本。复用原端口的REPEATABLE_READ、owner/tenant、Manifest/Chunk/Content/Source校验。导出schema `frozen-research-export.v1` 保存原Manifest与各块的完整规范字符串，Python能验证原摘要。最多9块、每块500根/512KiB、总块和导出各最多8MiB，支持OKX SPOT BTC/ETH 1h、721～4320根，超限明确失败。

保留实际sourceObservedAt与回放availableAt，两者分别进入Source及Content摘要。回放仍为 `CLOSED_HOURLY_BOUNDARY_V1 / EXPERIMENT_ASSUMPTION`，不冒充历史真实发布时间。Python还校验UTC连续性、唯一顺序、闭合、OHLCV、质量、必要观察时点、原Dataset/Version及全部摘要，非法输入不静默修正。

## Factor Implementations / Statistical Methods

三个因子版本1.0.0，窗口2～200、默认24：RETURN_MOMENTUM=close[t]/close[t-window]-1；REALIZED_VOLATILITY=最近window个对数收益的样本标准差（ddof1、不年化）；VOLUME_ANOMALY=当前量相对之前window根量的Z分数，参考不含当前或未来。暖机和零参考方差显式不可用。因子父对象的key/version/parameters/datasetVersionId/sourceChecksum适用于所有点，每点保存UTC窗口、featureAvailableAt、值和质量。

数值Context固定40位/HALF_EVEN与异常规则，最终统计为NumPy2.3.5 float64。70/30按时间先后切分，horizon1～48（默认6）小时的未来简单收益标签；训练尾部purge h根，训练标签末端严格早于OOS首特征。报告候选/有效/缺失/最低样本、窗口、标签截止、Pearson相关及OOS。

成对moving-block percentile Bootstrap保留局部自相关，块长max(window,horizon,ceil(窗口候选数^(1/3)))，PCG64固定seed，100～1000次、默认200，95%线性分位区间。最低样本max(60,5×块长)；不足INSUFFICIENT_SAMPLES，关系不明确INCONCLUSIVE。排除0仅EXPLORATORY_RELATIONSHIP，不表达可交易优势。没有成交、收益、费用模拟或多重检验/跨市场泛化结论。

## Artifact Schema / Read-only Preview

Artifact包含Dataset/Version与Content/Source/Manifest SHA、3因子及全点、观察假设、统计窗口/样本/方法/结果/置信区间、限制/警告、Source/GeneratedAt/实现及数值运行时、artifactId/checksum和三个固定安全布尔值。UTF8排序键紧凑JSON；统计实数用有限十进制字符串。artifactId是无id/checksum内容的SHA；checksum覆盖含id且不含checksum的内容。固定数据/参数/方法/seed/时间/数值运行时完整文件一致。

认证 `POST .../factor-research/preview` 有界16MiB、最多3×4320点，拒绝未知字段、重复字段、尾随JSON、非法时间、格式/窗口/样本数/身份/摘要漂移；继承现有POST角色门禁，无任意服务器文件读取。Java不持久化本地产物。React原research页增加选择Dataset→下载→显示离线命令→选择本地Artifact→核验和只读报告；支持中英和窄屏、分组报告及10点分页。错误或身份/会话切换清除旧报告。

## Cross-language Identity Proof / Python Tests

正式共用样本 `research/py/fixtures/java-frozen-v2.json` 由隔离PG中的Java原冻结器及新增导出器实际生成，721根合成受控输入，实际观察2026-10-29、回放从2026-04-01开始。不是独立Python合成CSV。`java-factor-artifact.json`由同输入经真实CLI生成，Java单测验证全部规范checksum及artifactId；Python验证原Manifest/分块/双内容摘要。浏览器另使用本轮新生成的BTC4320及ETH2160身份，并独立重建全部产物一致。

`python -m pytest -q`：53/53 PASS，含3因子独立oracle、未来扰动、坏输入/原V2摘要拒绝、purge、样本不足、Bootstrap重复、完整Artifact/CLI重复及旧CLI测试。mypy src --no-sqlite-cache、ruff check . --no-cache PASS。本机Python3.14.2，声明支持>=3.11，NumPy固定2.3.5；准确功能接受CI 41048377808已在Python3.11验证pytest/mypy/ruff通过。CLI显式使用LF，Windows完整输出0个CR/1个末尾LF，与共享fixture完整字节一致。旧pytest缓存目录权限警告不影响测试结果，原日志保留。

## PostgreSQL Tests / Browser E2E

任务PG16容器 `nq-v103-factor-pg16`，loopback55465，镜像ID `9e2cf6287f25`；各测试使用随机专属DB及非superuser运行角色。没有修改其他Docker资源。

- FactorResearchPostgresIntegrationTest 1/1，0 skip：721/2160/4320完整导出、实际/回放时间区别、重复字节、非owner/跨tenant/不存在身份拒绝、损坏拒绝、业务表无写及Flyway52 validate。
- FactorResearchServiceTest、FactorResearchControllerTest共2/2：真实双语言fixture规范摘要、重签名的坏身份/时点/边界负例、重复/尾随/非法JSON和16MiB上限、非法时间400。
- 既有LongHistoricalResearchPostgresIntegrationTest的upgradeFreezeReplayOwnershipCorruptionAndLegacy720RemainCompatible 1/1，验证4320正式回测/重复经济事实、V1兼容和归属/损坏；StrategySimPostgresIntegrationTest的channelReplayAccepts720BarsAndRejectsOverflowBeforeFunding单独补跑1/1、0skip通过。组合命令因缺少SIM专用启用property跳过1项，原日志保留，不将skip算PASS。
- `playwright.factor-research.config.ts` +真实Spring/PG/React/Chrome：BTC4320、ETH2160，2/2 PASS（18.8秒），实际页面下载→外部测试编排手动CLI→上传预览200、摘要错误400/旧报告消失、跨owner404、390px中英文、刷新无业务请求。
- 浏览器任务初末54张相关业务表完整行内容指纹一致，两个文件SHA256均 `87333a787e2daf5627d0b15a1c400990301dbaca3d95db13afcc8006a5bc8c1b`。只允许登录、显式语言偏好与只读preview请求，无策略/回测/SIM/交易写入。所有行情标注 `SYNTHETIC_CONTROLLED_JAVA_V2`，不冒充真实市场观察。

原始证据 `artifacts/nq-v103-factor/`：postgres-third.log、java-unit-second.log、compatibility-regression.log、browser-e2e-03.log、playwright-03/、business-fingerprint-{initial,final}.json。初始fixture编译/破坏注入、Python反例及浏览器重复表头locator失败均保留，修复后PASS不追认此前失败。前端18项目标测试及tsc/Vite build PASS，既有chunk警告保留。

## Resource Measurements

721/2160/4320导出字节分别324899/969086/1935891，单次导出58.6/63.6/79.0ms。浏览器真实CLI（200次Bootstrap）BTC4320耗时913ms、Artifact2598295字节；ETH2160耗时536ms、Artifact1300562字节。浏览器后仅将CLI末尾CRLF固定为LF，最终同输入Artifact分别减少1字节，统计/身份不变且有定向完整字节证明，原浏览器证据仍有效。最终Windows4320测量含tracemalloc开销2.37秒、Python分配峰值23602154字节、进程peak working set75218944字节、Artifact2598294字节。Java浏览器JVM `-Xmx512m`，无OOM；不宣称整套低内存安装或长期稳定性资格。JSON及原资源文件均保留。

## Independent Review

一次真正独立定向审查覆盖跨语言身份、时间泄漏、数值、租户与授权边界，最终PASS、未关闭finding0。17个runtime/依赖/i18n文件清单聚合SHA256 `0b7a31eb99ccf3ae90b5916fe71a9906ac8c994c8c85c58832e8f2c27317055f`。独立stdlib oracle最大误差4.45e-16，12个Bootstrap区间端点误差1.11e-16。全局Decimal精度依赖及格式错误500已修复并定向复核；独立重建两个实际浏览器产物、核对54表零差异及窄屏截图。证据 `independent-review.md`、`reviewed-runtime-manifest.json`。

## Changed Files / PR / CI / Merge Commit / Cleanup

变更仅限原ResearchDatasetVersionPort及JDBC导出、app因子只读服务/控制器和目标测试、research/py新数据读取/3因子/统计/Artifact/CLI/依赖/样本/文档/测试、原research页面及新只读Panel/API/双语/浏览器配置与测试。既有stage注册只刷新两条DOMAIN_VOCABULARY摘要，未改guard或增加例外。准确文件allowlist在 `artifacts/nq-v103-factor/staged-allowlist.json`。

- Implementation / 功能接受head：`fe206edffd937c871323278891532b694a47f3ab`；Tree=`db85f4ac7481256d784311a2974e8ff85940e523`，29个文件精确暂存，工作区无既有改动。
- [PR #96](https://github.com/ling5477/nexus-quant/pull/96)；[CI 41048377808](https://github.com/ling5477/nexus-quant/actions/runs/41048377808)，event=pull_request、head=上述功能接受head、completed/success、9/9 SUCCESS。Python3.11、Backend、前端全部单测/关键E2E、PG/Flyway/备份恢复、安全、架构、秘密扫描及交付证据门禁通过。
- 本次仅文档更新后的最终PR head仍须其准确CI；合并SHA、post-merge9/9和清理必须实际完成，不用旧功能pair替代。外部最终报告在完成时补足这些身份，避免自引用提交/CI。
- 本地浏览器Spring/Vite均已停止，54表初末完全一致；关键测试/审查证据和任务PG保留，不影响既有服务。合并后按准确任务分支及可再生产物清理。

功能实现与本地验证COMPLETE、功能候选CI GREEN；最终Git交付尚待最终head/合并后CI与清理。没有构建或发布v1.0.3，完成后停止，下一任务仅NQ-V103-FUNCTIONAL-CLOSEOUT。

## Limitations

本轮证明工程兼容、数值因果和完整流程，不证明真实数据的统计优势或长期盈利；Bootstrap平稳性/块长及回放时间是假设。Java不验证报告作者可信度或重新计算科学结论。无需数据库回滚；若撤回已合并功能，使用受控Revert PR，保留原Dataset及历史产物，禁止改写历史migration或v1.0.2资产。
