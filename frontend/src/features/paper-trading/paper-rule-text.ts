import {t} from '@/i18n';

/** 仅匹配当前正式引擎的完整文案；不翻译对象ID或推断未知规则，原始输出由页面保留。 */
const textKeys = new Map<string, string>([
    [
        'Run 处于 FAILED 终态，执行链路异常终止。',
        'theRunIsInTheFailedTerminalStateExecution_952e12',
    ],
    [
        '缺少可用权益/初始资金等事实，无法计算收益率与盈亏。',
        'availableEquityOrInitialCapitalFactsAreMissingSo_ab9c80',
    ],
    [
        '风控规则阻断执行或判定为高风险结果。',
        'riskRulesBlockedExecutionOrClassifiedTheResultAs_7e5827',
    ],
    [
        '已产生订单但未形成成交，可能是撮合条件、价格条件、流动性模拟或风控后置限制。',
        'ordersExistButNoFillsWereRecordedPossibleCauses_064717',
    ],
    [
        'Run 尚未启动或尚未产生执行事实（无订单、无成交）。',
        'theRunHasNotStartedOrHasNotProduced_7eb583',
    ],
    [
        '已运行但未产生任何订单，可能是策略信号未触发或数据/信号条件不足。',
        'theRunHasProducedNoOrdersSignalsMayNot_131a5c',
    ],
    [
        '已成交但当前 PnL 为负，需结合回撤、持仓与成交价格继续分析。',
        'fillsExistButCurrentProfitOrLossIsNegative_597eca',
    ],
    [
        '单 run 最大回撤已达到或超过 10%（首版规则阈值）。',
        'theMaximumDrawdownForThisRunHasReachedOr_fdd0ae',
    ],
    [
        'Run 仍在运行但暂无足够订单/成交/权益事实，结果尚未形成。',
        'theRunIsStillRunningButOrderFillOr_458388',
    ],
    [
        '已成交、当前非亏损、无高回撤、无风控拦截、数据充分。',
        'fillsExistCurrentProfitOrLossIsNonnegativeDrawdown_4d9aa3',
    ],
    [
        '现有事实不足以归因，需要进一步检查。',
        'availableFactsDoNotSupportAttributionFurtherInspectionIs_b49fb7',
    ],
    [
        '检查异常停机/告警/恢复事件，定位失败步骤后再决定是否重试。',
        'inspectAbnormalShutdownsAlertsAndRecoveryEventsLocateThe_2c1ce2',
    ],
    [
        '检查 equity 快照、日报与数据采集是否正常生成。',
        'checkWhetherEquitySnapshotsDailyReportsAndDataIngestion_f92899',
    ],
    [
        '检查最近一次风控检查结果与触发规则，确认是否为预期风控行为。',
        'inspectTheLatestRiskResultAndTriggeredRuleTo_831c68',
    ],
    [
        '复盘下单价格/类型与撮合（模拟成交）条件，确认订单是否长期挂单未成交。',
        'reviewOrderPriceTypeAndSimulatedMatchingConditionsCheck_1fcd81',
    ],
    [
        '确认 run 是否已启动，并复盘策略触发条件、参数与输入数据覆盖范围。',
        'confirmThatTheRunStartedThenReviewSignalConditions_8315ca',
    ],
    [
        '结合回撤曲线与成交明细复盘亏损来源（Paper 模拟结果，不代表真实交易表现）。',
        'reviewTheDrawdownCurveAndFillDetailsToLocate_5b3ca9',
    ],
    [
        '复盘仓位与回撤来源（Paper 模拟口径）。',
        'reviewPositionsAndTheSourcesOfDrawdownWithinThe_6089a2',
    ],
    [
        '稍后再观察，或确认执行器心跳与数据是否正常。',
        'observeLaterOrCheckTheExecutorHeartbeatAndData_fce9eb',
    ],
    [
        '无需处理；继续按 Paper 计划观察（不代表真实交易表现）。',
        'noActionIsIndicatedContinueThePaperObservationPlan_bcaa09',
    ],
    [
        '检查该 run 的订单/成交/风控/权益事实完整性。',
        'checkTheCompletenessOfThisRunSOrdersFills_99bb52',
    ],
    ['异常终态 run：执行链路失败。', 'failedRunTheExecutionPathFailed_05fe01'],
    [
        '数据不足 run：缺权益/初始资金，无法计算收益率。',
        'insufficientDataEquityOrInitialCapitalIsMissingSo_98b79f',
    ],
    [
        '风控拦截 run：执行被风控阻断或判为高风险。',
        'riskBlockedRunExecutionWasBlockedOrClassifiedAs_ce658d',
    ],
    [
        '有订单无成交 run：已下单但未撮合成交。',
        'ordersWithoutFillsOrdersWerePlacedButNoMatching_6cdc96',
    ],
    ['无订单 run：未产生任何订单与成交。', 'noOrderRunNoOrdersOrFillsWereProduced_78048e'],
    ['成交亏损 run：已成交但当前 PnL 为负。', 'lossAfterFillsFillsExistButCurrentProfitOr_fb4b7a'],
    [
        '高回撤 run：单 run 最大回撤达到/超过 10%。',
        'highDrawdownRunMaximumDrawdownReachedOrExceeded_1064a2',
    ],
    ['运行未出结果 run：仍在运行但事实不足。', 'pendingResultTheRunIsActiveButFactsAre_d9467e'],
    [
        '健康 run：已成交、非亏损、无高回撤、无风控拦截。',
        'healthyRunFillsExistCurrentProfitOrLossIs_4250bd',
    ],
    ['未归因 run：事实不足以判断。', 'unattributedRunFactsAreInsufficientToDetermineACause_122549'],
    [
        '无可比 Backtest 评估数据，无法计算 Paper 与 Backtest 偏差。',
        'noComparableBacktestEvaluationIsAvailableSoPaperVersus_e66888',
    ],
    [
        'Paper 与 Backtest 收益偏差较小（首版阈值 < 5%）。',
        'paperVersusBacktestReturnDeviationIsSmallBelowThe_9c3c53',
    ],
    [
        'Paper 与 Backtest 收益存在中等偏差（首版阈值 5%~15%），建议结合执行诊断复盘。',
        'paperVersusBacktestReturnDeviationIsModerateToUnder_5bc5ce',
    ],
    [
        'Paper 与 Backtest 收益偏差较大（首版阈值 > 15%），Paper 模拟与回测差异显著，需重点复盘。',
        'paperVersusBacktestReturnDeviationIsLargeAboveThe_b54ef4',
    ],
    [
        'Backtest 收益不可比，无法量化收益偏差（仅回撤可对照或均缺失）。',
        'backtestReturnsAreNotComparableReturnDeviationCannotBe_806c6c',
    ],
    ['是', 'yes_b5141d'],
    ['否', 'no_0c7066'],
    ['暂无足够 Paper 事实生成复盘。', 'thereAreNotEnoughPaperFactsToGenerateA_05e9af'],
    [
        '当前没有可用的 bounded Paper run 与策略评估事实，无法生成自动复盘。',
        'noUsablePaperRunsOrStrategyEvaluationsAreAvailable_a761f1',
    ],
    [
        'Paper 组合存在关键执行问题，需优先处理高风险 run。',
        'thePaperSampleHasCriticalExecutionIssuesPrioritizeThe_0f46af',
    ],
    [
        'Paper 与 Backtest 偏差显著，需复核策略稳定性。',
        'paperAndBacktestResultsDifferSubstantiallyReviewStrategyStability_d7375d',
    ],
    [
        'Paper 组合整体运行稳定，但仍需观察样本充足性。',
        'thePaperSampleIsBroadlyStableButSampleSufficiency_b3c30c',
    ],
    [
        '结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。',
        'conclusionsUseSimPaperSimulationFactsAndDoNot_18401b',
    ],
    [
        '复盘为规则化启发式结果，非真实投资评级、不构成投资建议。',
        'thisIsARuleBasedHeuristicReviewNotA_adbd9d',
    ],
    [
        '重点 run 清单已截断，完整明细见 runReviews 与 issueClusters。',
        'theHighlightedRunListIsTruncatedSeeRunreviewsAnd_fb25fb',
    ],
    [
        '未发现显著 Paper/Backtest 偏差（或缺少可比 Backtest 数据）。',
        'noSubstantialPaperVersusBacktestDeviationWasFoundOr_c5e534',
    ],
    [
        '继续积累 Paper 样本以提升评估置信度。',
        'continueCollectingPaperSamplesToImproveEvaluationConfidence_10b697',
    ],
    [
        '持续观察执行与风险指标，复核 Paper 与 Backtest 偏差。',
        'continueObservingExecutionAndRiskMetricsAndReviewPaper_bac2da',
    ],
    ['无订单：策略未产生任何订单', 'noOrdersTheStrategyProducedNoOrders_88c93d'],
    ['有订单无成交：订单未撮合成交', 'ordersWithoutFillsOrdersDidNotMatch_89b896'],
    ['成交亏损：已成交但当前为亏损', 'lossAfterFillsFillsExistButTheCurrentResult_eafd7d'],
    [
        '风控拦截：执行被风控阻断或判为高风险',
        'riskBlockExecutionWasBlockedOrClassifiedAsHigh_3ef6ce',
    ],
    ['数据不足：复盘置信度下降', 'insufficientDataReviewConfidenceIsReduced_8f106f'],
    [
        '高回撤：回撤超过首版高回撤阈值',
        'highDrawdownDrawdownExceedsTheCurrentHighDrawdownRule_252f70',
    ],
    ['异常终态：执行链路失败', 'failedTerminalStateTheExecutionPathFailed_7602a4'],
    ['健康：未发现关键异常', 'healthyNoCriticalAnomalyWasIdentified_95c498'],
    ['运行中：结果尚未形成', 'runningAResultHasNotYetFormed_30cf1b'],
    ['未归因：事实不足以判断', 'unattributedFactsAreInsufficientToDetermineACause_1e692a'],
    [
        '策略未产生订单，可能由信号未触发、数据不足或 run 尚未进入有效执行阶段导致。',
        'theStrategyProducedNoOrdersSignalsMayNotHave_a137a9',
    ],
    [
        '已有订单但未成交，可能由撮合条件、价格条件或流动性模拟导致。',
        'ordersExistWithoutFillsMatchingConditionsPricesOrSimulated_19cafe',
    ],
    [
        '已有成交但当前亏损，需要结合回撤、持仓和出入场条件复核。',
        'fillsExistButTheCurrentResultIsALoss_e0a3d9',
    ],
    ['风控阻断或高风险结果出现。', 'aRiskBlockOrHighRiskResultWasFound_c2c441'],
    ['数据不足导致复盘置信度下降。', 'insufficientDataReducesReviewConfidence_90569d'],
    ['回撤超过首版高回撤阈值。', 'drawdownExceedsTheCurrentHighDrawdownRuleThreshold_0ee0bd'],
    [
        '未发现关键异常，继续积累样本。',
        'noCriticalAnomalyWasIdentifiedContinueCollectingSamples_9ee486',
    ],
    ['策略触发条件可能未满足', 'strategyTriggerConditionsMayNotHaveBeenMet_098847'],
    ['行情/输入数据覆盖可能不足', 'marketOrInputDataCoverageMayBeInsufficient_ac4a3c'],
    ['run 可能尚未进入有效执行阶段', 'theRunMayNotYetHaveEnteredEffectiveExecution_a029d1'],
    ['订单价格可能偏离可成交区间', 'orderPricesMayBeOutsideTheFillableRange_65f0bb'],
    ['撮合参数可能限制成交', 'matchingParametersMayRestrictFills_6da909'],
    ['流动性模拟可能不足', 'simulatedLiquidityMayBeInsufficient_2536cd'],
    ['出入场条件可能不利', 'entryOrExitConditionsMayBeUnfavorable_c6b446'],
    ['止损规则可能缺失或过宽', 'stopLossRulesMayBeMissingOrTooLoose_5c6c40'],
    ['信号延迟可能影响成交价格', 'signalDelayMayAffectFillPrices_74133c'],
    ['风控规则可能触发', 'riskRulesMayHaveTriggered_8e4603'],
    ['仓位可能超限', 'positionsMayExceedLimits_0f7ab8'],
    ['风险阈值可能被突破', 'riskThresholdsMayHaveBeenBreached_17432b'],
    ['缺少 equity / 初始资金事实', 'equityOrInitialCapitalFactsAreMissing_342163'],
    ['订单 / 成交 / 日报事实可能缺失', 'orderFillOrDailyReportFactsMayBeMissing_c56a9e'],
    ['风险暴露可能过高', 'riskExposureMayBeTooHigh_1ab915'],
    ['策略参数可能过于激进', 'strategyParametersMayBeTooAggressive_981571'],
    ['止损 / 资金管理可能不足', 'stopLossOrCapitalManagementMayBeInsufficient_7009fd'],
    ['执行步骤可能异常中断', 'anExecutionStepMayHaveBeenInterruptedAbnormally_1c1946'],
    ['依赖事实 / 数据可能缺失', 'requiredFactsOrDataMayBeMissing_dfb220'],
    ['检查策略触发条件', 'checkStrategyTriggerConditions_0e70b2'],
    ['检查行情数据覆盖', 'checkMarketDataCoverage_e545c0'],
    ['检查调度窗口', 'checkTheSchedulingWindow_6edbe1'],
    ['检查订单价格', 'checkOrderPrices_88ea5a'],
    ['检查撮合参数', 'checkMatchingParameters_f92fe0'],
    ['检查市场数据与成交模拟规则', 'checkMarketDataAndSimulatedFillRules_b2e249'],
    ['检查亏损交易', 'inspectLosingTrades_1042b0'],
    ['检查止损规则', 'checkStopLossRules_418a49'],
    ['检查持仓时长与信号延迟', 'reviewHoldingDurationAndSignalDelay_fe2441'],
    ['检查风控规则', 'checkRiskRules_06de1b'],
    ['检查仓位限制', 'checkPositionLimits_f2b41d'],
    ['检查风险阈值', 'checkRiskThresholds_ac7278'],
    [
        '补齐 equity、order、trade、daily report 或 backtest 事实',
        'supplyTheMissingEquityOrderFillDailyReportOr_dc1c3c',
    ],
    ['检查风险暴露', 'checkRiskExposure_9fd3f1'],
    ['检查策略参数', 'checkStrategyParameters_a8aa22'],
    ['检查止损与资金管理', 'reviewStopLossAndCapitalManagement_402bd5'],
    ['检查异常停机/告警/恢复事件', 'inspectAbnormalShutdownsAlertsAndRecoveryEvents_ff4510'],
    ['定位失败步骤后再决定是否重试', 'locateTheFailedStepBeforeDecidingWhetherToRetry_54f865'],
    ['继续积累 Paper 样本', 'continueCollectingPaperSamples_79c99e'],
    ['稍后再观察执行器心跳与数据', 'observeTheExecutorHeartbeatAndDataAgainLater_98fb04'],
    ['检查该 run 的订单/成交/风控/权益事实完整性', 'checkThisRunSOrderFillRiskAndEquity_bd24cb'],
    [
        'Paper 内部表现较强（非投资推荐）',
        'strongInternalPaperPerformanceNotAnInvestmentRecommendation_d05c7e',
    ],
    ['观察中：需继续积累 Paper 样本', 'watchlistMorePaperSamplesAreNeeded_55b8d5'],
    ['高风险：存在回撤/风控/失败问题', 'highRiskDrawdownRiskControlOrFailureIssuesExist_dfecb8'],
    ['样本不足：评估置信度低', 'insufficientSamplesEvaluationConfidenceIsLow_99266a'],
    ['数据不足：缺少可比事实', 'insufficientDataComparableFactsAreMissing_77cd0e'],
    [
        '执行问题：无订单/有单无成交/成交质量问题',
        'executionIssueNoOrdersNoFillsOrFillQuality_69f515',
    ],
    ['未归因', 'unattributed_bfc62b'],
    [
        '该策略版本在 Paper 模拟内部表现较强，但这是 Paper 内部启发式评估，不代表真实交易表现，也不构成投资建议。',
        'thisStrategyVersionPerformsStronglyWithinThePaperSimulation_81d691',
    ],
    [
        '该策略版本表现中性，建议继续观察并积累更多 Paper 样本后再评估。',
        'thisStrategyVersionIsNeutralContinueObservingAndCollect_3bd1be',
    ],
    [
        '该策略版本存在较高回撤、风控拦截或失败终态等风险信号，需重点复核风险来源。',
        'thisStrategyVersionHasHighDrawdownRiskBlocksOr_5b9322',
    ],
    [
        '该策略版本可比 Paper 样本不足，评估置信度低，需增加 Paper 样本后再评估。',
        'thisStrategyVersionHasTooFewComparablePaperSamples_d56a37',
    ],
    [
        '该策略版本缺少可比 equity / 收益事实，暂无法形成可信评估。',
        'thisStrategyVersionLacksComparableEquityOrReturnFacts_615812',
    ],
    [
        '该策略版本存在无订单、有订单无成交或成交质量问题，需优先排查执行链路。',
        'thisStrategyVersionHasNoOrderUnfilledOrderOr_8a29cb',
    ],
    [
        '现有事实不足以评估该策略版本。',
        'availableFactsAreInsufficientToEvaluateThisStrategyVersion_4fe6b4',
    ],
    [
        '继续积累 Paper 样本以提升评估置信度',
        'continueCollectingPaperSamplesToImproveEvaluationConfidence_0362f9',
    ],
    ['持续复核 Paper 与 Backtest 偏差', 'continueReviewingPaperVersusBacktestDeviations_71a03c'],
    ['继续观察并增加 Paper 样本', 'continueObservingAndCollectMorePaperSamples_52b439'],
    ['复核执行与风险指标', 'reviewExecutionAndRiskMetrics_71a51b'],
    ['检查风控阈值与仓位限制', 'checkRiskThresholdsAndPositionLimits_e10168'],
    ['复核回撤来源与策略参数', 'reviewSourcesOfDrawdownAndStrategyParameters_5cdb15'],
    ['增加 Paper 样本', 'collectMorePaperSamples_2b25dd'],
    ['补齐 equity / 成交事实', 'supplyMissingEquityAndFillFacts_0b98d3'],
    [
        '补齐 equity、order、trade、daily report 事实',
        'supplyMissingEquityOrderFillAndDailyReportFacts_b8da98',
    ],
    ['检查策略触发条件与撮合参数', 'checkStrategyTriggersAndMatchingParameters_673d4e'],
    ['复核无订单 / 有单无成交 run', 'reviewRunsWithNoOrdersOrWithOrdersBut_7d9f8f'],
    ['检查该策略版本的 run / 事实完整性', 'checkThisStrategyVersionSRunsAndFactsFor_a4ece8'],
    ['收益评分偏低', 'lowReturnScore_857f2e'],
    ['风险评分偏低', 'lowRiskScore_78a566'],
    ['执行评分偏低', 'lowExecutionScore_6893c1'],
    ['样本不足', 'insufficientSamples_84c6e0'],
    ['Paper 与 Backtest 偏差偏大', 'largePaperVersusBacktestDeviation_4a6600'],
    ['数据不足', 'insufficientData_22e2dc'],
    ['多个 Paper run 处于 FAILED 终态。', 'multiplePaperRunsAreInTheFailedTerminalState_e8c52a'],
    [
        '检查异常停机/告警/恢复事件并定位失败步骤。',
        'inspectAbnormalShutdownsAlertsAndRecoveryEventsAndLocate_0702a0',
    ],
    [
        '多个 Paper run 被风控阻断或判为高风险。',
        'multiplePaperRunsWereRiskBlockedOrClassifiedAs_6f7769',
    ],
    ['检查风控规则、仓位限制与风险阈值。', 'checkRiskRulesPositionLimitsAndRiskThresholds_3fa5bc'],
    [
        '多个 Paper run 回撤达到/超过首版高回撤阈值。',
        'multiplePaperRunsReachedOrExceededTheCurrentHigh_3b25f5',
    ],
    [
        '检查风险暴露、策略参数与止损/资金管理。',
        'reviewRiskExposureStrategyParametersStopLossAndCapital_a04e9b',
    ],
    ['多个 Paper run 有订单但未成交。', 'multiplePaperRunsHaveOrdersButNoFills_445cf6'],
    [
        '检查订单价格、撮合参数与成交模拟规则。',
        'checkOrderPricesMatchingParametersAndSimulatedFillRules_43dca0',
    ],
    [
        '多个 Paper run 未产生任何订单，可能是信号未触发或数据不足。',
        'multiplePaperRunsProducedNoOrdersSignalsMayNot_5600c6',
    ],
    [
        '检查策略触发条件、行情数据覆盖与调度窗口。',
        'checkStrategyTriggersMarketDataCoverageAndSchedulingWindows_a24066',
    ],
    [
        '多个 Paper run 已成交但当前亏损。',
        'multiplePaperRunsHaveFillsButCurrentlyShowLosses_312fb3',
    ],
    [
        '检查亏损交易、止损规则与持仓时长。',
        'reviewLosingTradesStopLossRulesAndHoldingDuration_7ded06',
    ],
    [
        '多个 Paper run 缺少可比事实，复盘置信度下降。',
        'multiplePaperRunsLackComparableFactsReducingReviewConfidence_76e6ef',
    ],
    [
        '补齐 equity、order、trade、daily report 或 backtest 事实。',
        'supplyMissingEquityOrderFillDailyReportOrBacktest_fbeba6',
    ],
    [
        '多个策略版本 Paper 与 Backtest 偏差显著。',
        'multipleStrategyVersionsShowSubstantialPaperVersusBacktestDeviation_6e4bd3',
    ],
    [
        '复核策略稳定性与 Paper/Backtest 偏差来源。',
        'reviewStrategyStabilityAndSourcesOfPaperVersusBacktest_d988ce',
    ],
    [
        '多个策略版本可比 Paper 样本不足。',
        'multipleStrategyVersionsHaveInsufficientComparablePaperSamples_a688ac',
    ],
    ['增加 Paper 样本后再评估。', 'collectMorePaperSamplesBeforeReassessment_589a29'],
]);

const codeKeys: Record<string, string> = {
    CREATED: 'paperRunCreatedStatus',
    RUNNING: 'paperRunRunningStatus',
    STOPPED: 'paperRunStoppedStatus',
    FAILED: 'paperRunFailedStatus',
    CANCELLED: 'paperRunCancelledStatus',
    FAILED_RUN: 'abnormalTerminalState',
    DATA_INSUFFICIENT: 'insufficientData',
    RISK_BLOCKED: 'riskBlocked',
    ORDER_NO_FILL: 'ordersWithoutFills',
    NO_ORDER: 'noOrders',
    FILLED_LOSS: 'tradingLoss',
    HIGH_DRAWDOWN: 'highDrawdown',
    RUNNING_NO_RESULT: 'runningWithoutResults',
    HEALTHY: 'paperAnalysisHealthy',
    UNKNOWN: 'unattributed',
    EXECUTION_NO_ORDER: 'noOrders',
    EXECUTION_ORDER_NO_FILL: 'ordersWithoutFills',
    EXECUTION_FILLED_LOSS: 'tradingLoss',
    BACKTEST_DEVIATION_HIGH: 'highBacktestDeviation',
    SAMPLE_INSUFFICIENT: 'insufficientSamples',
};
const codeLabel = (value: string) => {
    const key = Object.hasOwn(codeKeys, value) ? codeKeys[value] : null;
    return key ? t(`pages:${key}`) : value;
};
const copy = (key: string, values: Record<string, string>, source: string) =>
    t(`pages:paperRules.templates.${key}`, {...values, defaultValue: source});
const knownText = (source: string) => {
    const key = textKeys.get(source);
    return key ? t(`pages:paperRules.messages.${key}`, {defaultValue: source}) : null;
};

/** 数值从原文逐字取出，不重新算分、转浮点或改比例单位；未来未知模板原样可见。 */
export function paperRuleText(value: string | null | undefined): string {
    if (value == null || value === '') return '—';
    const translated = knownText(value);
    if (translated !== null) return translated;
    if (value.length > 8192) return value;
    let match: RegExpExecArray | null;
    match = /^(状态|订单数|成交数|收益率|最大回撤|风控拦截)=(.+)$/.exec(value);
    if (match) {
        const field = match[1],
            raw = match[2];
        const fields: Record<string, string> = {
            状态: 'state',
            订单数: 'orders',
            成交数: 'fills',
            收益率: 'returnRatio',
            最大回撤: 'drawdownRatio',
            风控拦截: 'riskBlocked',
        };
        const valid =
            field === '状态'
                ? /^[A-Z_]{1,32}$/.test(raw)
                : field === '风控拦截'
                  ? ['是', '否'].includes(raw)
                  : ['订单数', '成交数'].includes(field)
                    ? /^\d{1,10}$/.test(raw)
                    : raw === '数据不足' || /^-?\d+(?:\.\d+)?$/.test(raw);
        if (valid) return copy(fields[field], {value: knownText(raw) ?? codeLabel(raw)}, value);
    }
    match = /^次要原因: ([A-Z_]+(?:, [A-Z_]+)*)$/.exec(value);
    if (match) {
        const codes = match[1].split(', ');
        if (codes.every((code) => Object.hasOwn(codeKeys, code)))
            return copy('secondary', {value: codes.map(codeLabel).join(', ')}, value);
    }
    match =
        /^(Paper 收益|风险|执行|样本|Backtest 偏差)评分较高（(returnScore|riskScore|executionScore|sampleScore|backtestDeviationScore)=(0|[1-9]\d?|100)）$/.exec(
            value,
        );
    if (match) {
        const fields: Record<string, string> = {
            'Paper 收益': 'returnScore',
            风险: 'riskScore',
            执行: 'executionScore',
            样本: 'sampleScore',
            'Backtest 偏差': 'backtestDeviationScore',
        };
        if (fields[match[1]] === match[2]) return copy(match[2], {value: match[3]}, value);
    }
    match = /^主要短板: (.+)$/.exec(value);
    if (match) {
        const meaning = knownText(match[1]);
        if (meaning) return copy('weakness', {value: meaning}, value);
    }
    match =
        /^(收益评分偏低|风险评分偏低|执行评分偏低|样本不足|Paper 与 Backtest 偏差偏大)（得分 (0|[1-9]\d?|100)）$/.exec(
            value,
        );
    if (match) return copy('lowScore', {dimension: knownText(match[1])!, score: match[2]}, value);
    match =
        /^共 (\d{1,10}) 个 bounded Paper run，其中 (\d{1,10}) 个存在问题、(\d{1,10}) 个健康；关键问题 run (\d{1,10}) 个、警告 run (\d{1,10}) 个；已评估策略 (\d{1,10}) 个、发布 (\d{1,10}) 个。结论为规则化 Paper 复盘，不构成投资建议。$/.exec(
            value,
        );
    if (match)
        return copy(
            'portfolioSummary',
            {
                total: match[1],
                issues: match[2],
                healthy: match[3],
                critical: match[4],
                warnings: match[5],
                strategies: match[6],
                publishes: match[7],
            },
            value,
        );
    match = /^共 (\d{1,10}) 个 Paper run：问题 (\d{1,10}) 个、健康 (\d{1,10}) 个。$/.exec(value);
    if (match)
        return copy('sampleCounts', {total: match[1], issues: match[2], healthy: match[3]}, value);
    match = /^关键问题 run (\d{1,10}) 个，建议优先排查。$/.exec(value);
    if (match) return copy('criticalCount', {value: match[1]}, value);
    match = /^最集中问题类型: ([A-Z_]+)。$/.exec(value);
    if (match && Object.hasOwn(codeKeys, match[1]))
        return copy('topCause', {value: codeLabel(match[1])}, value);
    match = /^策略最常见短板: (.+)。$/.exec(value);
    if (match) {
        const meaning = knownText(match[1]);
        if (meaning) return copy('topWeakness', {value: meaning}, value);
    }
    match = /^([A-Z_]+): (\d{1,10}) 项 —— (.+)$/.exec(value);
    if (match && Object.hasOwn(codeKeys, match[1])) {
        const meaning = knownText(match[3]);
        if (meaning)
            return copy(
                'cluster',
                {cause: codeLabel(match[1]), count: match[2], summary: meaning},
                value,
            );
    }
    match = /^Paper 内部表现较强策略 (\d{1,10}) 个（非投资推荐）。$/.exec(value);
    if (match) return copy('strongCount', {value: match[1]}, value);
    match = /^高风险策略 (\d{1,10}) 个，需复核风险来源。$/.exec(value);
    if (match) return copy('highRiskCount', {value: match[1]}, value);
    match = /^样本不足策略 (\d{1,10}) 个，需增加 Paper 样本。$/.exec(value);
    if (match) return copy('insufficientCount', {value: match[1]}, value);
    match =
        /^(\d{1,10}) 个策略版本 Paper 与 Backtest 偏差显著（HIGH），需重点复核策略稳定性。$/.exec(
            value,
        );
    if (match) return copy('highDeviationCount', {value: match[1]}, value);
    return value;
}
