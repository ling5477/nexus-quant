# NQ-V103-02 Forward SIM Evaluation

## Baseline 与范围

- 开始及再次 fetch 的 `dev == origin/dev`：`96e011cb3b7edbba3e62dd85ad0b7fc2469a74b7`，初始工作区干净。
- 分支：`codex/nq-v103-forward-sim-evaluation`；需求依据：`E:/Project/NQ_V103_PRODUCT_ROADMAP.md` 5.3、6.2及本轮任务指令。
- 开发 schema V51→V52；正式 v1.0.2 / V49 的资产、Tag、Release 不变。LIVE DISABLED，Kill Switch ENGAGED。
- 本文先记录实现及本地证据；PR、CI、Merge 与清理绑定实际完成身份后补充。未完成部分不视为接受。

## Actual Existing Capabilities / New Functional Delta

复用原 `StrategySimPerformanceQuery` 的成交、账务、估值、费用及持仓成本重建，原 `PerformanceStatistics` 的收益、完整路径回撤和统计。原 `/performance` 默认1000点和公式保持。原 `StrategySimAnalysisQuery` 负责运行归属与来源，原 Research/SIM Comparison 负责研究血缘及不同市场时期不可比。

新增原 canonical analysis 路由的 `/runs/{paperRunId}/forward-evaluation`，以及研究领域的观察目标和追加人工复盘。没有新的绩效引擎、账本、执行器或调度器；没有 Python 变更。Legacy daily report 的409与 canonical/legacy 分流不变。Continuous SIM 的720根冻结准入和500根滚动边界不变。

## Observation Contract / Coverage Semantics

- 观察目标为整小时UTC `[from,to)`，1～90日；RETROSPECTIVE明确标注回溯，FORWARD必须从登记后下一完整UTC小时及以后开始。
- 绑定 owner/tenant、paperRunId、canonicalAccountId、strategyVersionId、publishId、validationId、selectedTrialId及原研究来源。延长需另建观察保留 predecessor，目标不可变。
- 响应分别披露目标、已到期完整小时、实际估值覆盖、asOf、方法版本、watermark和checksum。每小时预期分母保留暂停/缺口；合法HOLD也是样本。缺失、未知、修订及有界gapRanges单列。
- asOf不晚于服务端Clock；run创建晚于asOf时返回不可得。未开始窗口没有期间经济投影。新入口对REPLAY返回不可比的空经济结果，不读取其成交/账务/估值；旧performance语义保持。
- 保存事实同时满足事件时间与入库可见时间。窗口之后更新的订单末态不能冒充过去已知状态；无法重建的历史原因明确UNKNOWN。
- 每批keyset最多500，估值事件与成交各最多10000，超限明确不可用；图表最多1000点，统计使用完整有效路径。SQL 5秒、查询事务10～15秒；无无限缓存或无界扫描。

## Metric Sources / Risk and Execution Evaluation

期间收益的起点权益与期前持仓来自原canonical成交、账务及估值；from同刻成交费用计入期间，to同刻成交排除。期末仅使用asOf可见且确实存在的价格锚，不前填缺价。缺费用、缺价或估值覆盖外成交使相应指标不可用，不填0。

成交频率按已到期自然日，双边换手按期间绝对成交名义额/同期间可信小时权益均值；分母排除期前opening anchor。`methodDetails`披露秒数、自然日、完整UTC日、分母样本数/均值、原始/展示点数及降采样。最大回撤是保存估值路径的采样回撤，不是盘中真实最大回撤。

费用按原币分组，不无汇率直接相加。冻结SIM费用/滑点配置只投影白名单，标注为SIM模型假设和已记模型收费，不是交易所真实已付费用，也不冒充原研究成本。风险事件、拒单、未决订单和执行异常复用原事实。样本与每项指标分别返回AVAILABLE / INSUFFICIENT_SAMPLES / UNAVAILABLE / NOT_COMPARABLE，质量与策略好坏分开；不计算跨期收益相减或自动评分。

## Database Changes / Manual Review

仅新增 `V52__strategy_observation_reviews.sql`，V1～V51不变。`strategy_observations`保存目标与身份，`strategy_observation_reviews`保存服务端重新生成的评价快照、评价人、时间、决定及理由；不是经济事实源。

复用 `ResearchCreationOperationService` 稳定UUID与payload幂等；owner/tenant RLS、目标不可变、review仅追加、CAS和延迟revision一致性约束共同防止覆盖与并发漂移。客户端仅提交asOf/checksum确认，不能提交指标JSON。快照读取保留BigDecimal精度。

人工三决定为CONTINUE_OBSERVATION、STOP_OBSERVATION、RETURN_TO_RESEARCH。后两项只关闭研究记录，不暂停SIM、不撤单、不平仓、不激活或发布策略。

## Frontend Workflow

复用 `/paper-trading/reviews?analysisSource=sim&paperRunId=...`，从原实验比较及SIM运行页导航。URL携带simFrom/simTo/simAsOf/observationId/reviewId及研究上下文；切换运行清理scope。无完整研究血缘的合法Run仍可只读评价。

用户显式登记目标、检查覆盖/指标/风险、填写理由并冻结人工决定；历史revision刷新可恢复。未知提交保留原UUID和payload，不自动换号重试。显式窗口错误不回退无截止绩效。GET、导航和刷新不创建业务事实。中英文及窄屏使用原组件体系。

## PostgreSQL Validation / Unit Tests

任务隔离PG16容器 `nq-v103-forward-pg16`，127.0.0.1:55464，镜像ID `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97`。所有业务证明在随机专属数据库，其他运行资源不变。

- `artifacts/nq-v103-forward/postgres-final.log`：BUILD SUCCESS；31项定向单测与 `ForwardSimEvaluationPostgresIntegrationTest` 4/4 PASS、0 skip。
- 非空V51→V52升级及Flyway validate；正式研究→WF→发布→Continuous SIM→撮合事实；独立现金/持仓/期间收益/回撤/换手oracle。
- owner/tenant/Run/version/publish/Trial拒绝、同UUID并发、CAS、三种决定、真实新JVM读取历史；STOP前后核心事实不变。
- HOLD/无成交、未到期、缺口/STALLED、风险/执行异常、缺费用/未知币/缺价/覆盖外成交、1000/1001/10000/10001防御边界。
- 真正推进的canonical REPLAY已有成交，旧performance非空不变；新forward/asOf不查询经济事实，返回空经济投影，GET前后指纹一致。
- 前端4文件30项Vitest及tsc/Vite build PASS，日志 `frontend-unit.log`、`frontend-build.log`。

此前失败日志保留，成功不追认旧attempt。受控行情及时间推进均为SYNTHETIC_CONTROLLED_PUBLIC_FEED，不是长期真实世界观察；大容量投影夹具只证明有界读取，不冒充90日实际运行。

## Browser E2E / Historical Regression / Review Findings

真实Spring Boot / PG / Vite / Chrome的两项E2E通过（`browser-verified.log`，19.4秒）：真实身份/24小时覆盖及指标、重复固定asOf读取、登记、CONTINUE→STOP、历史快照刷新、390px中英文、返回实验和其他owner 404；业务写入只有三次显式研究操作，无执行控制请求。未到期窗口显示样本不足和观察尚未开始。截图与结构化facts保存在 `artifacts/nq-v103-forward/playwright-verified/`。

`historical-regression.log`：ResearchSimComparisonPostgresIntegrationTest 2/2、LongHistoricalResearchPostgresIntegrationTest 3/3、StrategySimPostgresIntegrationTest的720/721准入1/1，合计6/6 PASS、0 skip，BUILD SUCCESS。原comparison口径、4320研究输入/冻结/WF及SIM720准入保持。development schema回归27项PASS，动态迁移inventory为52。最终前端30项测试和构建再次PASS（`frontend-unit-final.log`、`frontend-build-final.log`）。浏览器早期按钮locator、未到期code预期和文案缺失失败均保留，不改后端语义来通过测试。

同一次独立定向审查已发现并修复REPLAY/asOf未来事实回退问题；独立SQL探针证明只有身份查询，经济查询0。独立经济oracle42598断言PASS。方法披露和未开始窗口误计期前挂单已修复；同一次独立审查最终PASS，remaining blockers NONE。19个runtime文件最终清单SHA256 `c3ac2e5a9b4709ac32c7b2cd25ce65a5a1742de40ce4896c5c41af5da5045f05`，独立报告SHA256 `d84443b653d7659a31ca62c47d51a17c50da6d19f8e0dbcf5a0fd9fc1dad32cd`。浏览器全部attempt前后17张canonical/研究事实表指纹一致（`browser-fingerprint-verification.json`）；只有显式观察/复盘/operation元数据变化。证据 `artifacts/nq-v103-forward/independent-review.md`。

## PR / CI / Merge Commit / Cleanup

独立审查完成，准备PR交付；远端CI尚未执行。现有stage-asset登记只刷新ci.yml旧例外摘要，已按独立核对提案SHA256 `64c4b401eada6fdf4f7c4a46c63ad2283180d261903d3fda42867f524f65ddcd` apply，guard与边界不变。必要CI必须准确head 9/9，普通合并后再次准确merge head 9/9，之后精确清理任务分支及专属临时资源。完整最终身份在仓库外 `E:/Project/NQ_V103_02_FORWARD_SIM_EVALUATION.md` 记录，避免自引用CI身份。

## Known Limitations / Rollback

没有90日真实世界观察、真实交易所实付费或长期统计优势证明。历史未知状态不补造。若需要回退，通过受控Revert PR停用新增研究写入口，保留V52及原目标/历史快照，不down迁移、不改SIM运行或历史经济事实。
