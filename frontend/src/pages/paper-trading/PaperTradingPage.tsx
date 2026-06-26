import {
    App,
    Button,
    Card,
    Col,
    Collapse,
    Descriptions,
    Form,
    Input,
    Modal,
    Row,
    Segmented,
    Select,
    Space,
    Tabs,
    Tag,
    Timeline,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import React, {useEffect, useState, type ReactNode} from 'react';

import {formatApiError} from '@/api/errors';
import {
    NqAmountText,
    NqDangerConfirmButton,
    NqDataTable,
    NqDrawdownChart,
    NqEmptyState,
    NqEnvironmentBadge,
    NqEquityCurveChart,
    NqErrorState,
    NqFilterBar,
    NqLoadingState,
    NqMetricCard,
    NqPageHeader,
    NqPercentText,
    NqPortfolioDrawdownChart,
    NqPortfolioEquityChart,
    NqPriceText,
    NqRiskBanner,
    NqStatusTag,
    formatNqNumber,
    nqNumericColumn,
} from '@/components/nq';
import type {NqStatusTone} from '@/components/nq';
import {
    NqAlertPanel,
    NqHeartbeatPanel,
    NqRecoveryPanel,
    NqScheduleFirePanel,
    NqStabilityCheckPanel,
} from '@/components/paper';
import {
    EXCHANGE_OPTIONS,
    INTERVAL_OPTIONS,
    MARKET_TYPE_OPTIONS,
    PAPER_RUN_STATUS_OPTIONS,
    SYMBOL_OPTIONS,
    TRADE_ENV_OPTIONS,
} from '@/constants/filter-options';
import {
    useCreatePaperTradingRunMutation,
    useEmergencyStopMutation,
    useGenerateDailyReportMutation,
    usePaperAlertsQuery,
    usePaperDailyReportsQuery,
    usePaperAutoReviewsQuery,
    usePaperExecutionDiagnosticsQuery,
    usePaperHeartbeatsQuery,
    usePaperPortfolioSummaryQuery,
    usePaperRecoveryEventsQuery,
    usePaperStrategyEvaluationsQuery,
    usePaperRunSummaryQuery,
    usePaperSchedulesQuery,
    usePaperStabilityChecksQuery,
    usePaperTradingDetailQuery,
    usePaperTradingEmergencyStopsQuery,
    usePaperTradingEquityCurveQuery,
    usePaperTradingListQuery,
    usePaperTradingOrdersQuery,
    usePaperTradingPositionCurveQuery,
    usePaperTradingPositionsQuery,
    usePaperTradingReplayQuery,
    usePaperTradingRiskResultsQuery,
    usePaperTradingTradesQuery,
    useRunRiskOnceMutation,
    useStartPaperTradingRunMutation,
    useStopPaperTradingRunMutation,
} from '@/hooks/usePaperTradingQuery';
import {useBacktestDetailQuery} from '@/hooks/useBacktestsListQuery';
import {useEvaluationsListQuery} from '@/hooks/useEvaluationsListQuery';
import {usePublishDetailQuery} from '@/hooks/usePublishesListQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultPaperTradingListFilters,
    type EquityCurveSnapshotItem,
    type PaperAutoReviewSeverity,
    type PaperAutoReviewsResponse,
    type PaperIssueCluster,
    type PaperPublishAutoReview,
    type PaperRunAutoReview,
    type PaperStrategyAutoReview,
    type PaperExecutionCause,
    type PaperExecutionCauseConfidence,
    type PaperExecutionDiagnosticCauseDistribution,
    type PaperExecutionDiagnosticsResponse,
    type PaperExecutionGroupDiagnostic,
    type PaperExecutionRunDiagnostic,
    type PaperExecutionSeverity,
    type PaperBacktestDeviationLevel,
    type PaperPublishEvaluationItem,
    type PaperStrategyEvaluationConfidence,
    type PaperStrategyEvaluationItem,
    type PaperStrategyEvaluationsResponse,
    type PaperStrategyRatingLabel,
    type PaperPortfolioCurve,
    type PaperPortfolioCurvePoint,
    type PaperPortfolioGroup,
    type PaperPortfolioRunRef,
    type PaperPortfolioSummaryResponse,
    type PaperRiskCheckResultItem,
    type PaperRunDailyReportItem,
    type PaperRunSummaryResponse,
    type PaperTradingListFilters,
    type PaperTradingOrderItem,
    type PaperTradingPositionItem,
    type PaperTradingRunCreateRequest,
    type PaperTradingRunItem,
    type PaperTradingTradeItem,
} from '@/types/paper-trading';
import type {BacktestConfigListItem} from '@/types/backtests';
import type {BacktestEvaluationListItem} from '@/types/evaluations';
import type {BacktestPublishDetailItem} from '@/types/publishes';
import {appEnv} from '@/utils/env';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type PaperRunRow = PaperTradingRunItem;

const DEFAULT_CREATE_VALUES: PaperTradingRunCreateRequest = {
    publishId: '',
    tradeEnv: 'SIM',
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    intervalCode: '1m',
    configSnapshotJson: '',
};

/** 按时间倒序取最新一条，不依赖后端返回顺序。 */
function latestBy<T>(items: T[], getTime: (item: T) => string | null | undefined): T | null {
    return [...items]
        .filter((item) => Boolean(getTime(item)))
        .sort((left, right) => new Date(getTime(right) as string).getTime() - new Date(getTime(left) as string).getTime())[0]
        ?? null;
}

function sumNullableAmounts(...values: Array<string | number | null | undefined>): number | null {
    let total = 0;
    let hasValue = false;

    for (const value of values) {
        if (value === null || value === undefined || value === '') {
            continue;
        }
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            continue;
        }
        total += numeric;
        hasValue = true;
    }

    return hasValue ? total : null;
}

function pnlTone(value: number | null): 'up' | 'down' | 'muted' {
    if (value === null || value === 0) {
        return 'muted';
    }
    return value > 0 ? 'up' : 'down';
}

const TERMINAL_PAPER_RUN_STATUSES = new Set(['STOPPED', 'FAILED', 'CANCELLED']);

interface PaperTimelineEvent {
    key: string;
    type: string;
    status: string;
    time: string;
    description: ReactNode;
    color: 'blue' | 'green' | 'red' | 'gray';
}

interface PaperTimelineInput {
    run: PaperTradingRunItem;
    latestOrder: PaperTradingOrderItem | null;
    latestTrade: PaperTradingTradeItem | null;
    latestPosition: PaperTradingPositionItem | null;
    latestEquitySnapshot: EquityCurveSnapshotItem | null;
    latestRisk: PaperRiskCheckResultItem | null;
    latestLoopPnl: number | null;
}

interface PaperRunReview {
    finalStatus: string;
    runtimeDuration: string;
    orderCount: number;
    fillCount: number;
    positionCount: number;
    netPnl: number | null;
    riskResult: string;
    riskTone: 'success' | 'info' | 'neutral' | 'warning' | 'danger';
    conclusion: string;
    conclusionLevel: 'info' | 'warning' | 'danger';
}

interface PaperBacktestComparisonMetric {
    label: string;
    backtest: ReactNode;
    paper: ReactNode;
}

interface PaperBacktestComparisonResult {
    sourceAvailable: boolean;
    sourceChain: {
        strategyVersionId: string | null;
        publishId: string | null;
        backtestRunId: string | null;
        backtestConfigId: string | null;
        paperRunId: string;
    };
    metrics: PaperBacktestComparisonMetric[];
    diagnosis: {
        level: 'info' | 'warning' | 'danger';
        type: string;
        title: string;
        description: string;
    };
}

/**
 * Strategy → Publish → Backtest/Evaluation → Paper Run 链路视图模型（Loop-11）。
 * 纯前端组合已有只读查询（run / publish detail / backtest config / evaluations / paper review），
 * 把单个 Paper run 放回完整策略链路中，帮助用户理解来源是否完整、缺哪一环。
 * 只展示研究、发布与 Paper 模拟运行关系，不新增 API，不代表 LIVE 或真实交易表现。
 */
type PaperLineageNodeState = 'COMPLETE' | 'PARTIAL' | 'MISSING';

type PaperLineageCompleteness =
    | 'CHAIN_COMPLETE'
    | 'CHAIN_MISSING_BACKTEST'
    | 'CHAIN_MISSING_PUBLISH'
    | 'CHAIN_MISSING_STRATEGY_VERSION'
    | 'CHAIN_DATA_INSUFFICIENT';

interface PaperLineageNode {
    key: string;
    label: string;
    /** 该节点的主标识（strategyVersionId / publishId / backtestId / paperRunId 等）。 */
    id: string | null;
    /** 节点自身业务状态（发布状态 / 评估状态 / run 状态 / 风控结果等），无则为 null。 */
    nodeStatus: string | null;
    nodeStatusTone?: NqStatusTone;
    /** 链路可追踪性：已识别 / 来源不完整 / 缺失。 */
    state: PaperLineageNodeState;
    time: string | null;
    timeLabel: string;
    summary: ReactNode;
}

interface PaperLineageResult {
    nodes: PaperLineageNode[];
    diagnosis: {
        level: 'success' | 'info' | 'warning';
        type: PaperLineageCompleteness;
        title: string;
        description: string;
    };
}

/**
 * Paper 账户资产与收益率概览模型（Loop-12）。
 * 纯前端派生：仅消费已有 equity 快照 / 日报 / 来源 backtest 初始资金 / run 状态，
 * 不新增 API，不读取真实交易所账户余额，不代表 LIVE 或真实交易表现。
 * initialEquity / periodStartEquity <= 0 时不计算收益率，统一显示「数据不足」。
 */
interface PaperPeriodReturn {
    key: string;
    label: string;
    pnl: number | null;
    returnRatio: number | null;
    /** false 表示该周期缺少 baseline / 初始权益，统一显示「数据不足」，不外推。 */
    available: boolean;
}

interface PaperAccountPerformance {
    /** 是否拿到当前权益（无则展示账户级空态，不伪造任何指标）。 */
    hasData: boolean;
    /** 是否可计算累计收益率（initialEquity > 0 且当前权益可得）。 */
    canComputeReturn: boolean;
    initialEquity: number | null;
    currentEquity: number | null;
    availableBalance: number | null;
    positionMarketValue: number | null;
    realizedPnl: number | null;
    unrealizedPnl: number | null;
    totalPnl: number | null;
    totalReturn: number | null;
    peakEquity: number | null;
    maxDrawdown: number | null;
    currentDrawdown: number | null;
    periods: PaperPeriodReturn[];
    recentSnapshots: EquityCurveSnapshotItem[];
    dataQualityNotes: string[];
}

function formatTimelineAmount(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '-';
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric.toFixed(2) : String(value);
}

function formatRuntimeDuration(start: string | null | undefined, end: string | null | undefined): string {
    if (!start) {
        return '-';
    }
    const startTime = new Date(start).getTime();
    const endTime = end ? new Date(end).getTime() : Date.now();
    if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime < startTime) {
        return '-';
    }

    const totalSeconds = Math.floor((endTime - startTime) / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours > 0) {
        return `${hours} 小时 ${minutes} 分钟`;
    }
    if (minutes > 0) {
        return `${minutes} 分钟 ${seconds} 秒`;
    }
    return `${seconds} 秒`;
}

function classifyRiskResult(latestRisk: PaperRiskCheckResultItem | null): Pick<PaperRunReview, 'riskResult' | 'riskTone'> {
    if (!latestRisk) {
        return {riskResult: '无数据', riskTone: 'neutral'};
    }
    const status = latestRisk.status.toUpperCase();
    const severity = latestRisk.severity.toUpperCase();
    const riskBlocked = ['REJECTED', 'FAILED', 'BLOCKED'].includes(status) || ['HIGH', 'CRITICAL', 'BLOCK'].includes(severity);
    if (riskBlocked) {
        return {riskResult: '拦截', riskTone: 'danger'};
    }
    if (status === 'PASSED' && ['LOW', 'INFO', 'NONE'].includes(severity)) {
        return {riskResult: '通过', riskTone: 'success'};
    }
    if (status === 'PASSED') {
        return {riskResult: '通过', riskTone: 'success'};
    }
    return {riskResult: '警告', riskTone: 'warning'};
}

function buildPaperRunReview({
    run,
    orderCount,
    fillCount,
    positionCount,
    netPnl,
    latestRisk,
}: {
    run: PaperTradingRunItem;
    orderCount: number;
    fillCount: number;
    positionCount: number;
    netPnl: number | null;
    latestRisk: PaperRiskCheckResultItem | null;
}): PaperRunReview {
    const finalStatus = run.status || 'UNKNOWN';
    const normalizedStatus = finalStatus.toUpperCase();
    const durationEnd = run.stoppedAt ?? (normalizedStatus === 'RUNNING' ? undefined : run.updatedAt);
    const risk = classifyRiskResult(latestRisk);
    const hasExecution = orderCount > 0 || fillCount > 0;
    const riskBlocked = risk.riskResult === '拦截';

    let conclusion = '数据不足，待观察';
    let conclusionLevel: PaperRunReview['conclusionLevel'] = 'info';

    if (riskBlocked) {
        conclusion = '风控拦截';
        conclusionLevel = 'danger';
    } else if (normalizedStatus === 'FAILED' || normalizedStatus === 'CANCELLED') {
        conclusion = '异常停止';
        conclusionLevel = 'danger';
    } else if (normalizedStatus === 'RUNNING') {
        conclusion = '仍在运行';
        conclusionLevel = 'info';
    } else if (normalizedStatus === 'CREATED' && orderCount === 0) {
        conclusion = '尚未启动';
        conclusionLevel = 'info';
    } else if (normalizedStatus === 'STOPPED' && orderCount === 0) {
        conclusion = '无交易';
        conclusionLevel = 'warning';
    } else if (normalizedStatus === 'STOPPED' && hasExecution && risk.riskResult === '通过') {
        conclusion = '正常完成';
        conclusionLevel = 'info';
    }

    return {
        finalStatus,
        runtimeDuration: formatRuntimeDuration(run.startedAt, durationEnd),
        orderCount,
        fillCount,
        positionCount,
        netPnl,
        riskResult: risk.riskResult,
        riskTone: risk.riskTone,
        conclusion,
        conclusionLevel,
    };
}

/**
 * Paper run 异常原因聚合：把现有查询结果（run / orders / trades / positions / risk / alerts / recovery / pnl）
 * 归纳成可读的诊断原因，帮助用户理解“为什么没交易 / 为什么被风控拦截 / 为什么异常停止 / 为什么数据不足”。
 * 纯前端派生，不新增 API，不代表真实交易能力。
 */
type PaperDiagnosisType =
    | 'NO_ORDER'
    | 'NO_FILL'
    | 'RISK_BLOCKED'
    | 'RUN_FAILED'
    | 'RUN_CANCELLED'
    | 'DATA_INSUFFICIENT'
    | 'ALERT_PRESENT'
    | 'RECOVERY_PRESENT'
    | 'PNL_NEGATIVE'
    | 'HEALTHY';

type PaperDiagnosisSeverity = 'INFO' | 'WARNING' | 'BLOCKING';

interface PaperRunDiagnosis {
    key: string;
    type: PaperDiagnosisType;
    title: string;
    severity: PaperDiagnosisSeverity;
    description: string;
    /** 建议用户优先核对的对象（页面区块 / 事实表 / 面板）。 */
    checkTarget: string;
}

interface PaperDiagnosisInput {
    run: PaperTradingRunItem;
    orderCount: number;
    fillCount: number;
    netPnl: number | null;
    /** 风控是否处于拦截/高风险态（与运行结果复盘同一判定口径）。 */
    riskBlocked: boolean;
    /** 是否存在任何风控检查结果。 */
    hasRiskData: boolean;
    openAlertCount: number;
    recoveryCount: number;
}

// 诊断排序：阻断 > 警告 > 提示，让最严重的原因排在最前。
const DIAGNOSIS_SEVERITY_RANK: Record<PaperDiagnosisSeverity, number> = {
    BLOCKING: 0,
    WARNING: 1,
    INFO: 2,
};

function buildPaperRunDiagnoses({
    run,
    orderCount,
    fillCount,
    netPnl,
    riskBlocked,
    hasRiskData,
    openAlertCount,
    recoveryCount,
}: PaperDiagnosisInput): PaperRunDiagnosis[] {
    const normalizedStatus = (run.status ?? 'UNKNOWN').toUpperCase();
    const isTerminal = TERMINAL_PAPER_RUN_STATUSES.has(normalizedStatus);
    const diagnoses: PaperRunDiagnosis[] = [];

    // 风控拦截优先解释，并抑制 NO_ORDER / NO_FILL，避免重复噪声。
    if (riskBlocked) {
        diagnoses.push({
            key: 'risk-blocked',
            type: 'RISK_BLOCKED',
            title: '风控拦截',
            severity: 'BLOCKING',
            description: '风控拦截：订单可能未进入交易执行链路，请优先查看风控检查结果。',
            checkTarget: '风控状态卡片、风控结果 Tab',
        });
    }

    if (normalizedStatus === 'FAILED') {
        diagnoses.push({
            key: 'run-failed',
            type: 'RUN_FAILED',
            title: '运行异常结束',
            severity: 'BLOCKING',
            description: '运行异常结束：请检查告警、恢复事件和运行状态。',
            checkTarget: '告警面板、恢复事件、心跳与运行状态',
        });
    }

    if (normalizedStatus === 'CANCELLED') {
        diagnoses.push({
            key: 'run-cancelled',
            type: 'RUN_CANCELLED',
            title: '运行被取消',
            severity: 'WARNING',
            description: '本次 Paper run 已被取消，未形成完整执行闭环。',
            checkTarget: '运行状态、操作记录',
        });
    }

    const blockedOrFailed = riskBlocked || normalizedStatus === 'FAILED';
    if (!blockedOrFailed && orderCount === 0) {
        diagnoses.push({
            key: 'no-order',
            type: 'NO_ORDER',
            title: '未发现订单',
            // 终态仍无订单更值得警示；运行中或未启动则只作提示。
            severity: isTerminal ? 'WARNING' : 'INFO',
            description: isTerminal
                ? '未发现订单：本次 Paper run 结束时仍无任何订单事实。'
                : '未发现订单：本次 Paper run 可能尚未触发策略信号，或仍处于未启动状态。',
            checkTarget: '运行状态、调度触发、策略信号、订单事实表',
        });
    } else if (!blockedOrFailed && orderCount > 0 && fillCount === 0) {
        diagnoses.push({
            key: 'no-fill',
            type: 'NO_FILL',
            title: '有订单但暂无成交',
            severity: 'WARNING',
            description: '存在订单但暂无成交：请检查订单状态、价格条件与撮合结果。',
            checkTarget: '订单状态、成交事实表、价格条件',
        });
    }

    // 数据不足：无成交、无净 PnL、无风控数据，不足以形成复盘结论。
    if (fillCount === 0 && netPnl === null && !hasRiskData) {
        diagnoses.push({
            key: 'data-insufficient',
            type: 'DATA_INSUFFICIENT',
            title: '数据不足',
            severity: 'INFO',
            description: '数据不足：当前执行事实不足以形成完整复盘结论。',
            checkTarget: '订单、成交、净 PnL、风控结果',
        });
    }

    if (netPnl !== null && netPnl < 0) {
        diagnoses.push({
            key: 'pnl-negative',
            type: 'PNL_NEGATIVE',
            title: '净 PnL 为负',
            severity: 'WARNING',
            description: '净 PnL 为负：请复盘成交明细与持仓盈亏。',
            checkTarget: '成交事实、持仓事实、资金曲线',
        });
    }

    if (openAlertCount > 0) {
        diagnoses.push({
            key: 'alert-present',
            type: 'ALERT_PRESENT',
            title: '存在未处理告警',
            severity: 'WARNING',
            description: `存在 ${openAlertCount} 条未处理告警：请查看告警面板并确认处理。`,
            checkTarget: '告警面板',
        });
    }

    if (recoveryCount > 0) {
        diagnoses.push({
            key: 'recovery-present',
            type: 'RECOVERY_PRESENT',
            title: '存在恢复事件',
            severity: 'INFO',
            description: '存在恢复事件：本次 run 曾触发恢复或重试，请关注是否稳定。',
            checkTarget: '恢复事件面板',
        });
    }

    // 兜底：没有任何阻断 / 警告级别原因时给出健康结论。
    const hasNotable = diagnoses.some((item) => item.severity !== 'INFO');
    if (!hasNotable) {
        diagnoses.push({
            key: 'healthy',
            type: 'HEALTHY',
            title: '暂无明显异常',
            severity: 'INFO',
            description: '暂无明显异常：可继续查看时间线、订单、成交、持仓和 PnL。',
            checkTarget: '运行事件时间线、订单、成交、持仓、净 PnL',
        });
    }

    return [...diagnoses].sort(
        (left, right) => DIAGNOSIS_SEVERITY_RANK[left.severity] - DIAGNOSIS_SEVERITY_RANK[right.severity],
    );
}

function buildPaperTimelineEvents({
    run,
    latestOrder,
    latestTrade,
    latestPosition,
    latestEquitySnapshot,
    latestRisk,
    latestLoopPnl,
}: PaperTimelineInput): PaperTimelineEvent[] {
    const events: PaperTimelineEvent[] = [];
    const terminalStatus = run.status?.toUpperCase() ?? 'UNKNOWN';
    const terminalTime = run.stoppedAt ?? (TERMINAL_PAPER_RUN_STATUSES.has(terminalStatus) ? run.updatedAt : null);

    if (run.createdAt) {
        events.push({
            key: 'run-created',
            type: 'Paper run created',
            status: 'CREATED',
            time: run.createdAt,
            description: `创建于 ${run.tradeEnv}/Paper 环境，发布 ID ${run.publishId}。`,
            color: 'blue',
        });
    }
    if (run.startedAt) {
        events.push({
            key: 'run-started',
            type: 'Paper run started',
            status: 'RUNNING',
            time: run.startedAt,
            description: '运行已进入 SIM/Paper 生命周期，不触发 LIVE 或真实交易所。',
            color: 'green',
        });
    }
    if (terminalTime) {
        events.push({
            key: 'run-terminal',
            type: `Paper run ${terminalStatus.toLowerCase()}`,
            status: terminalStatus,
            time: terminalTime,
            description: '当前 run 已进入终态；历史订单、成交、持仓和风控事实仍可追溯。',
            color: terminalStatus === 'STOPPED' ? 'gray' : 'red',
        });
    }
    if (latestOrder) {
        events.push({
            key: 'latest-order',
            type: '最新订单状态事件',
            status: latestOrder.status,
            time: latestOrder.updatedAt,
            description: `${latestOrder.side} ${latestOrder.orderType} ${latestOrder.symbol}，数量 ${formatTimelineAmount(latestOrder.quantity)}，价格 ${formatTimelineAmount(latestOrder.price)}。`,
            color: latestOrder.status === 'FILLED' ? 'green' : latestOrder.status === 'REJECTED' ? 'red' : 'blue',
        });
    }
    if (latestTrade) {
        events.push({
            key: 'latest-trade',
            type: '最新成交事件',
            status: 'FILLED',
            time: latestTrade.tradedAt,
            description: `${latestTrade.side} ${latestTrade.symbol}，成交数量 ${formatTimelineAmount(latestTrade.quantity)}，成交价 ${formatTimelineAmount(latestTrade.price)}。`,
            color: 'green',
        });
    }
    if (latestPosition) {
        events.push({
            key: 'latest-position',
            type: '最新持仓更新时间',
            status: 'POSITION_UPDATED',
            time: latestPosition.updatedAt,
            description: `${latestPosition.symbol} 持仓 ${formatTimelineAmount(latestPosition.quantity)}，未实现盈亏 ${formatTimelineAmount(latestPosition.unrealizedPnl)}。`,
            color: 'blue',
        });
    }
    if (latestEquitySnapshot) {
        events.push({
            key: 'latest-equity',
            type: '最新净 PnL / equity snapshot',
            status: latestLoopPnl === null ? 'SNAPSHOT' : latestLoopPnl >= 0 ? 'PNL_UP' : 'PNL_DOWN',
            time: latestEquitySnapshot.snapshotTime,
            description: `总权益 ${formatTimelineAmount(latestEquitySnapshot.totalEquity)}，净 PnL ${formatTimelineAmount(latestLoopPnl)}，来源 ${latestEquitySnapshot.source}。`,
            color: latestLoopPnl === null || latestLoopPnl >= 0 ? 'green' : 'red',
        });
    }
    if (latestRisk) {
        events.push({
            key: 'latest-risk',
            type: '最新风控检查结果',
            status: latestRisk.status,
            time: latestRisk.createdAt,
            description: `${latestRisk.checkType} · ${latestRisk.severity}${latestRisk.message ? `：${latestRisk.message}` : ''}`,
            color: latestRisk.status === 'PASSED' ? 'green' : latestRisk.status === 'REJECTED' ? 'red' : 'blue',
        });
    }

    return events.filter((event) => Boolean(event.time));
}

function toNullableNumber(value: string | number | null | undefined): number | null {
    if (value === null || value === undefined || value === '') {
        return null;
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
}

function formatMetricNumber(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(Number(value))) {
        return '-';
    }
    return Number(value).toLocaleString('zh-CN', {maximumFractionDigits: 4});
}

function readableBacktestRiskResult(evaluation: BacktestEvaluationListItem | null): string {
    if (!evaluation) {
        return '无数据';
    }
    if (evaluation.failureCode || evaluation.evaluationStatus === 'FAILED') {
        const failureText = `${evaluation.failureCode ?? ''} ${evaluation.failureMessage ?? ''}`.toUpperCase();
        return failureText.includes('RISK') ? '风控拦截' : '评估失败';
    }
    if (evaluation.evaluationStatus === 'COMPLETED' || evaluation.evaluationStatus === 'SUCCESS') {
        return '未体现风控拦截';
    }
    return '数据不足';
}

function buildBacktestPaperComparison({
    run,
    publish,
    backtest,
    evaluation,
    paperReview,
}: {
    run: PaperTradingRunItem;
    publish: BacktestPublishDetailItem | null;
    backtest: BacktestConfigListItem | null;
    evaluation: BacktestEvaluationListItem | null;
    paperReview: PaperRunReview | null;
}): PaperBacktestComparisonResult {
    const sourceChain = {
        strategyVersionId: run.strategyVersionId ?? publish?.strategyVersionId ?? backtest?.strategyVersionId ?? null,
        publishId: run.publishId || publish?.publishRecordId || null,
        backtestRunId: publish?.backtestRunId ?? null,
        backtestConfigId: publish?.backtestConfigId ?? backtest?.backtestConfigId ?? null,
        paperRunId: run.paperRunId,
    };

    if (!publish?.backtestRunId && !publish?.backtestConfigId) {
        return {
            sourceAvailable: false,
            sourceChain,
            metrics: [],
            diagnosis: {
                level: 'info',
                type: 'NO_BACKTEST_SOURCE',
                title: '无法对照：缺少来源 backtest',
                description: '当前 Paper run 暂无来源 backtest / publish 追溯信息，详情区仍可继续查看 Paper 执行事实。',
            },
        };
    }

    const backtestOrderCount = evaluation?.orderCount ?? null;
    const backtestTradeCount = evaluation?.tradeCount ?? null;
    const backtestNetPnl = toNullableNumber(evaluation?.netPnl);
    const backtestRiskResult = readableBacktestRiskResult(evaluation);
    const paperRiskResult = paperReview?.riskResult ?? '无数据';
    const paperOrderCount = paperReview?.orderCount ?? null;
    const paperFillCount = paperReview?.fillCount ?? null;
    const paperNetPnl = paperReview?.netPnl ?? null;
    const hasBacktestMetrics = evaluation !== null;
    const hasPaperMetrics = paperReview !== null;

    let diagnosis: PaperBacktestComparisonResult['diagnosis'] = {
        level: 'info',
        type: 'NO_OBVIOUS_DEVIATION',
        title: '暂无明显偏差',
        description: '当前 Backtest 与 Paper 的核心指标未发现明显偏差；仍需结合行情窗口、撮合假设和风控配置复核。',
    };

    if (!hasBacktestMetrics || !hasPaperMetrics) {
        diagnosis = {
            level: 'warning',
            type: 'DATA_INSUFFICIENT',
            title: '数据不足，无法对比',
            description: '缺少 backtest 或 paper 指标，暂不能形成可靠偏差结论。',
        };
    } else if ((paperOrderCount ?? 0) === 0 && (paperFillCount ?? 0) === 0 && ((backtestOrderCount ?? 0) > 0 || (backtestTradeCount ?? 0) > 0)) {
        diagnosis = {
            level: 'warning',
            type: 'PAPER_NO_TRADE',
            title: 'Paper 未交易：回测有交易但 Paper 无订单/无成交',
            description: '请优先核对策略信号触发、调度窗口、账户上下文和 Paper 风控条件。',
        };
    } else if (paperRiskResult === '拦截' && backtestRiskResult !== '风控拦截') {
        diagnosis = {
            level: 'danger',
            type: 'PAPER_RISK_BLOCKED',
            title: 'Paper 风控拦截：回测未体现该拦截',
            description: 'Paper 被风控拦截，实盘前必须复核风控阈值、账户状态和策略发布参数。',
        };
    } else if (backtestNetPnl !== null && paperNetPnl !== null && backtestNetPnl > 0 && paperNetPnl <= backtestNetPnl * 0.5) {
        diagnosis = {
            level: 'warning',
            type: 'PNL_DEVIATION',
            title: '收益偏差：Paper PnL 明显低于 backtest',
            description: 'Paper 净 PnL 低于回测净 PnL 的 50%，请复核滑点、手续费、撮合、样本窗口和运行时风控。',
        };
    }

    return {
        sourceAvailable: true,
        sourceChain,
        metrics: [
            {
                label: '状态',
                backtest: evaluation ? <NqStatusTag status={evaluation.evaluationStatus}/> : <NqStatusTag status="NO_EVALUATION" tone="neutral"/>,
                paper: <NqStatusTag status={paperReview?.finalStatus ?? run.status}/>,
            },
            {
                label: '订单数',
                backtest: <span className="nq-num">{formatMetricNumber(backtestOrderCount)}</span>,
                paper: <span className="nq-num">{formatMetricNumber(paperOrderCount)}</span>,
            },
            {
                label: '成交数',
                backtest: <span className="nq-num">{formatMetricNumber(backtestTradeCount)}</span>,
                paper: <span className="nq-num">{formatMetricNumber(paperFillCount)}</span>,
            },
            {
                label: '净 PnL',
                backtest: <NqAmountText value={backtestNetPnl} signed colorBySign/>,
                paper: <NqAmountText value={paperNetPnl} signed colorBySign/>,
            },
            {
                label: '风控结果',
                backtest: <NqStatusTag status={backtestRiskResult} tone={backtestRiskResult === '风控拦截' ? 'danger' : backtestRiskResult === '无数据' ? 'neutral' : 'info'}/>,
                paper: <NqStatusTag status={paperRiskResult} tone={riskResultTone(paperRiskResult)}/>,
            },
            {
                label: '运行时间 / 样本区间',
                backtest: <span className="nq-num">{backtest ? `${formatDateTime(backtest.startTime)} ~ ${formatDateTime(backtest.endTime)}` : '-'}</span>,
                paper: <span className="nq-num">{paperReview?.runtimeDuration ?? '-'}</span>,
            },
            {
                label: '策略版本 / 发布版本',
                backtest: <span className="nq-mono">{backtest?.strategyVersionId ?? publish?.strategyVersionId ?? '-'}</span>,
                paper: <span className="nq-mono">{run.strategyVersionId ?? run.publishId}</span>,
            },
        ],
        diagnosis,
    };
}

function riskResultTone(riskResult: string): NqStatusTone {
    switch (riskResult) {
        case '拦截':
            return 'danger';
        case '通过':
            return 'success';
        case '警告':
            return 'warning';
        default:
            return 'neutral';
    }
}

/**
 * 组合已有只读查询，构建 Strategy Version → Publish → Backtest/Evaluation → Paper Run → 复盘/诊断 链路。
 * 全部字段来自现有 run / publish detail / backtest config / evaluation / paper review，不新增任何 API 或副作用。
 * 链路完整性诊断按「链路头到尾」顺序报告第一处断点，便于用户从源头排查。
 */
function buildPaperLineage({
    run,
    publish,
    backtest,
    evaluation,
    paperReview,
}: {
    run: PaperTradingRunItem;
    publish: BacktestPublishDetailItem | null;
    backtest: BacktestConfigListItem | null;
    evaluation: BacktestEvaluationListItem | null;
    paperReview: PaperRunReview | null;
}): PaperLineageResult {
    const strategyVersionId = run.strategyVersionId ?? publish?.strategyVersionId ?? backtest?.strategyVersionId ?? null;
    const publishId = run.publishId || publish?.publishRecordId || null;
    const publishResolved = Boolean(publish);
    const backtestId = publish?.backtestRunId ?? publish?.backtestConfigId ?? backtest?.backtestConfigId ?? null;
    const backtestResolved = Boolean(backtestId);
    const hasEvalMetrics = evaluation !== null
        && (evaluation.netPnl !== null || evaluation.orderCount !== null || evaluation.tradeCount !== null);
    const hasPaperMetrics = paperReview !== null;

    const strategyNode: PaperLineageNode = {
        key: 'strategy-version',
        label: '策略版本',
        id: strategyVersionId,
        nodeStatus: strategyVersionId ? '已绑定' : null,
        nodeStatusTone: strategyVersionId ? 'info' : undefined,
        state: strategyVersionId ? 'COMPLETE' : 'MISSING',
        time: null,
        timeLabel: '',
        summary: (
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {strategyVersionId
                    ? '策略版本可追踪，作为本次发布与 Paper run 的版本基线。'
                    : '缺少 strategy version：策略版本信息不可追踪。'}
            </Typography.Text>
        ),
    };

    const publishNode: PaperLineageNode = {
        key: 'publish',
        label: '策略发布',
        id: publishId,
        nodeStatus: publish?.publishStatus ?? null,
        state: publishResolved ? 'COMPLETE' : publishId ? 'PARTIAL' : 'MISSING',
        time: publish?.publishedAt ?? null,
        timeLabel: '发布于',
        summary: (
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {publishResolved
                    ? `发布记录：${publish?.publishName ?? '未命名发布'}。`
                    : publishId
                        ? 'Paper run 引用了发布 ID，但发布详情暂不可解析，来源不完整。'
                        : '缺少 publish：Paper run 来源不完整。'}
            </Typography.Text>
        ),
    };

    const backtestNode: PaperLineageNode = {
        key: 'backtest',
        label: '回测 / 评估',
        id: backtestId,
        nodeStatus: evaluation?.evaluationStatus ?? null,
        state: backtestResolved ? (hasEvalMetrics ? 'COMPLETE' : 'PARTIAL') : 'MISSING',
        time: evaluation?.evaluatedAt ?? null,
        timeLabel: '评估于',
        summary: backtestResolved ? (
            <Space size={12} wrap>
                <Typography.Text type="secondary" style={{fontSize: 12}}>评估 ID {evaluation?.evalReportId ?? '-'}</Typography.Text>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    净 PnL <NqAmountText value={toNullableNumber(evaluation?.netPnl)} signed colorBySign/>
                </Typography.Text>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    订单 {formatMetricNumber(evaluation?.orderCount ?? null)} · 成交 {formatMetricNumber(evaluation?.tradeCount ?? null)}
                </Typography.Text>
            </Space>
        ) : (
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                缺少 backtest：Paper run 可查看，但无法做完整回测对照。
            </Typography.Text>
        ),
    };

    const paperNode: PaperLineageNode = {
        key: 'paper-run',
        label: 'Paper 运行',
        id: run.paperRunId,
        nodeStatus: paperReview?.finalStatus ?? run.status,
        state: 'COMPLETE',
        time: run.startedAt ?? run.createdAt,
        timeLabel: run.startedAt ? '启动于' : '创建于',
        summary: (
            <Space size={12} wrap>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {run.symbol} · {run.intervalCode} · {run.exchangeCode}
                </Typography.Text>
                {run.stoppedAt ? (
                    <Typography.Text type="secondary" style={{fontSize: 12}}>停止于 {formatDateTime(run.stoppedAt)}</Typography.Text>
                ) : null}
            </Space>
        ),
    };

    const summaryNode: PaperLineageNode = {
        key: 'summary',
        label: '复盘 / 诊断',
        id: null,
        nodeStatus: paperReview?.riskResult ?? null,
        nodeStatusTone: paperReview ? riskResultTone(paperReview.riskResult) : undefined,
        state: hasPaperMetrics ? 'COMPLETE' : 'PARTIAL',
        time: null,
        timeLabel: '',
        summary: (
            <Space size={12} wrap>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {paperReview ? `复盘结论：${paperReview.conclusion}` : '复盘数据不足，待运行产生事实后形成结论。'}
                </Typography.Text>
                {paperReview ? (
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        净 PnL <NqAmountText value={paperReview.netPnl} signed colorBySign/>
                    </Typography.Text>
                ) : null}
            </Space>
        ),
    };

    const nodes = [strategyNode, publishNode, backtestNode, paperNode, summaryNode];

    let diagnosis: PaperLineageResult['diagnosis'];
    if (!strategyVersionId) {
        diagnosis = {
            level: 'warning',
            type: 'CHAIN_MISSING_STRATEGY_VERSION',
            title: '链路不完整：缺少策略版本',
            description: '缺少 strategy version：策略版本信息不可追踪，无法将本次 Paper run 绑定到确定的版本基线。',
        };
    } else if (!publishResolved) {
        diagnosis = {
            level: 'warning',
            type: 'CHAIN_MISSING_PUBLISH',
            title: '链路不完整：缺少 publish 来源',
            description: '缺少 publish：Paper run 来源不完整，发布详情暂不可解析，无法追溯回测与评估。',
        };
    } else if (!backtestResolved) {
        diagnosis = {
            level: 'info',
            type: 'CHAIN_MISSING_BACKTEST',
            title: '链路不完整：缺少 backtest',
            description: '缺少 backtest：Paper run 可查看，但无法做完整回测对照。',
        };
    } else if (!hasEvalMetrics || !hasPaperMetrics) {
        diagnosis = {
            level: 'info',
            type: 'CHAIN_DATA_INSUFFICIENT',
            title: '链路存在但数据不足',
            description: '数据不足：链路节点可识别，但回测或 Paper 摘要指标不足，暂不能形成完整对照结论。',
        };
    } else {
        diagnosis = {
            level: 'success',
            type: 'CHAIN_COMPLETE',
            title: '链路完整：Strategy Version → Publish → Backtest → Paper Run 均可识别',
            description: '链路完整：策略版本、发布、回测 / 评估、Paper run 均可识别，可结合下方对照卡片复核研究到模拟运行的偏差。',
        };
    }

    return {nodes, diagnosis};
}

// 周期收益窗口（自然日近似）；日优先取后端日报，其余按 equity 快照窗口派生 baseline。
const PAPER_PERIOD_WINDOWS: Array<{key: string; label: string; ms: number}> = [
    {key: 'daily', label: '日', ms: 24 * 60 * 60 * 1000},
    {key: 'weekly', label: '周', ms: 7 * 24 * 60 * 60 * 1000},
    {key: 'monthly', label: '月', ms: 30 * 24 * 60 * 60 * 1000},
    {key: 'yearly', label: '年', ms: 365 * 24 * 60 * 60 * 1000},
];

/** start <= 0 或缺值时不计算收益率（返回 null），交由展示层显示「数据不足」。 */
function safeReturnRatio(end: number | null, start: number | null): number | null {
    if (end === null || start === null || start <= 0) {
        return null;
    }
    return (end - start) / start;
}

/**
 * 组合已有 equity 快照 / 日报 / 来源 backtest 初始资金，派生 Paper 模拟账户资产、收益率与回撤。
 * 全部为前端只读派生，不新增请求、不触发交易，不代表 LIVE 或真实交易表现。
 */
function buildPaperAccountPerformance({
    snapshots,
    dailyReport,
    backtestInitialCapital,
    runStatus,
    fillCount,
}: {
    snapshots: EquityCurveSnapshotItem[];
    dailyReport: PaperRunDailyReportItem | null;
    backtestInitialCapital: number | null;
    runStatus: string;
    fillCount: number;
}): PaperAccountPerformance {
    // 仅保留有合法时间与数值权益的快照，按时间升序，作为账户资产与回撤的唯一派生源。
    const numericSnapshots = snapshots
        .map((item) => ({
            raw: item,
            time: new Date(item.snapshotTime).getTime(),
            equity: toNullableNumber(item.totalEquity),
        }))
        .filter((item) => Number.isFinite(item.time) && item.equity !== null)
        .sort((left, right) => left.time - right.time);

    const latest = numericSnapshots[numericSnapshots.length - 1] ?? null;
    const currentEquity = latest?.equity ?? toNullableNumber(dailyReport?.totalEquity);
    const latestRaw = latest?.raw ?? null;
    const realizedPnl = toNullableNumber(latestRaw?.realizedPnl);
    const unrealizedPnl = toNullableNumber(latestRaw?.unrealizedPnl);
    const availableBalance = toNullableNumber(latestRaw?.cashBalance);
    const positionMarketValue = toNullableNumber(latestRaw?.positionValue);

    // 初始资金优先取来源 backtest 初始资金；否则用「当前权益 - 累计已实现/未实现 PnL」回推；再退化到最早快照权益。
    let initialEquity: number | null = null;
    if (backtestInitialCapital !== null && backtestInitialCapital > 0) {
        initialEquity = backtestInitialCapital;
    } else if (currentEquity !== null && realizedPnl !== null && unrealizedPnl !== null) {
        const inferred = currentEquity - realizedPnl - unrealizedPnl;
        initialEquity = inferred > 0 ? inferred : null;
    }
    if (initialEquity === null) {
        const earliestEquity = numericSnapshots[0]?.equity ?? null;
        initialEquity = earliestEquity !== null && earliestEquity > 0 ? earliestEquity : null;
    }

    // 回撤与峰值：遍历升序权益维护 running peak，maxDrawdown 取最小回撤；currentDrawdown 用全局峰值。
    let peakEquity: number | null = null;
    let maxDrawdown: number | null = null;
    if (numericSnapshots.length > 0) {
        let runningPeak = Number.NEGATIVE_INFINITY;
        let minDrawdown = 0;
        for (const point of numericSnapshots) {
            const equity = point.equity as number;
            if (equity > runningPeak) {
                runningPeak = equity;
            }
            if (runningPeak > 0) {
                const drawdown = (equity - runningPeak) / runningPeak;
                if (drawdown < minDrawdown) {
                    minDrawdown = drawdown;
                }
            }
        }
        peakEquity = Number.isFinite(runningPeak) ? runningPeak : null;
        maxDrawdown = minDrawdown;
    } else {
        // 无快照但日报有最大回撤时退化展示（口径以日报为准）。
        maxDrawdown = toNullableNumber(dailyReport?.maxDrawdown);
    }

    const currentDrawdown = currentEquity !== null && peakEquity !== null && peakEquity > 0
        ? (currentEquity - peakEquity) / peakEquity
        : null;

    const totalReturn = safeReturnRatio(currentEquity, initialEquity);
    const totalPnl = currentEquity !== null && initialEquity !== null
        ? currentEquity - initialEquity
        : sumNullableAmounts(realizedPnl, unrealizedPnl);

    const periods: PaperPeriodReturn[] = PAPER_PERIOD_WINDOWS.map((period) => {
        // 日收益优先消费后端日报（已含 dailyPnl / dailyReturn），口径与最新日报摘要一致。
        if (period.key === 'daily' && dailyReport) {
            const pnl = toNullableNumber(dailyReport.dailyPnl);
            const ratio = toNullableNumber(dailyReport.dailyReturn);
            return {key: period.key, label: period.label, pnl, returnRatio: ratio, available: pnl !== null || ratio !== null};
        }
        if (latest === null || currentEquity === null) {
            return {key: period.key, label: period.label, pnl: null, returnRatio: null, available: false};
        }
        // baseline = 截止时间点之前（含）最近的一条快照；不存在则该周期数据不足，不外推。
        const cutoff = latest.time - period.ms;
        const baseline = [...numericSnapshots].reverse().find((point) => point.time <= cutoff) ?? null;
        if (baseline === null || baseline.equity === null || baseline.equity <= 0) {
            return {key: period.key, label: period.label, pnl: null, returnRatio: null, available: false};
        }
        return {
            key: period.key,
            label: period.label,
            pnl: currentEquity - baseline.equity,
            returnRatio: (currentEquity - baseline.equity) / baseline.equity,
            available: true,
        };
    });
    periods.push({
        key: 'total',
        label: '累计',
        pnl: totalPnl,
        returnRatio: totalReturn,
        available: totalReturn !== null,
    });

    const recentSnapshots = [...numericSnapshots].reverse().slice(0, 5).map((point) => point.raw);

    const dataQualityNotes: string[] = [];
    if (numericSnapshots.length === 0) {
        dataQualityNotes.push('无 equity snapshot：资金曲线与回撤暂不可计算。');
    }
    if (fillCount === 0) {
        dataQualityNotes.push('无成交：PnL 主要来自持仓浮动或暂无。');
    }
    if ((runStatus ?? '').toUpperCase() === 'CREATED') {
        dataQualityNotes.push('run 尚未启动：尚无运行期资产快照。');
    }

    return {
        hasData: currentEquity !== null,
        canComputeReturn: totalReturn !== null,
        initialEquity,
        currentEquity,
        availableBalance,
        positionMarketValue,
        realizedPnl,
        unrealizedPnl,
        totalPnl,
        totalReturn,
        peakEquity,
        maxDrawdown,
        currentDrawdown,
        periods,
        recentSnapshots,
        dataQualityNotes,
    };
}

/** 把后端 summary 时间线条目映射为前端时间线展示模型（title 作为粗体事件名）。 */
function summaryTimelineColor(type: string, status: string): PaperTimelineEvent['color'] {
    switch (type) {
        case 'RUN_CREATED':
            return 'blue';
        case 'RUN_STARTED':
            return 'green';
        case 'RUN_TERMINAL':
            return status === 'STOPPED' ? 'gray' : 'red';
        case 'LATEST_ORDER':
            return status === 'FILLED' ? 'green' : status === 'REJECTED' ? 'red' : 'blue';
        case 'LATEST_TRADE':
            return 'green';
        case 'LATEST_EQUITY':
            return status === 'PNL_DOWN' ? 'red' : 'green';
        case 'LATEST_RISK':
            return status === 'PASSED' ? 'green' : status === 'REJECTED' ? 'red' : 'blue';
        default:
            return 'blue';
    }
}

function mapSummaryReview(summary: PaperRunSummaryResponse): PaperRunReview {
    const review = summary.resultReview;
    return {
        finalStatus: review.finalStatus,
        runtimeDuration: review.runtimeDurationText,
        orderCount: summary.counts.orderCount,
        fillCount: summary.counts.fillCount,
        positionCount: summary.counts.positionCount,
        netPnl: toNullableNumber(review.netPnl),
        riskResult: review.riskResult,
        riskTone: riskResultTone(review.riskResult),
        conclusion: review.conclusion,
        conclusionLevel: review.conclusionLevel,
    };
}

function mapSummaryDiagnoses(summary: PaperRunSummaryResponse): PaperRunDiagnosis[] {
    return summary.diagnoses.map((item) => ({
        key: item.type,
        type: item.type as PaperDiagnosisType,
        title: item.title,
        severity: item.severity as PaperDiagnosisSeverity,
        description: item.description,
        checkTarget: item.checkTarget,
    }));
}

function mapSummaryTimeline(summary: PaperRunSummaryResponse): PaperTimelineEvent[] {
    return summary.timeline.map((item) => ({
        key: `${item.type}-${item.occurredAt}`,
        type: item.title,
        status: item.status,
        time: item.occurredAt,
        description: item.description,
        color: summaryTimelineColor(item.type, item.status),
    }));
}

export function PaperTradingPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<PaperTradingListFilters>();
    const [createForm] = Form.useForm<PaperTradingRunCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<PaperTradingListFilters>(defaultPaperTradingListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedRow, setSelectedRow] = useState<PaperRunRow | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    // Loop-9：底部「运行事实」明细 Tab 的当前激活页；明细查询按激活 Tab 懒加载，首屏只走 detail + summary。
    // 默认 'snapshots'（仅读 run 快照，无网络），使 orders/trades/positions/risk/曲线/复盘 在首屏均不请求。
    const [factTab, setFactTab] = useState('snapshots');

    const listQuery = usePaperTradingListQuery(
        {
            publishId: submittedFilters.publishId || undefined,
            status: submittedFilters.status || undefined,
        },
        searchVersion,
    );

    // Loop-13：Paper 组合看板首屏即加载（单请求后端聚合），不随 run 选择或筛选变化。
    const portfolioQuery = usePaperPortfolioSummaryQuery();
    // GateK K2：Paper 执行诊断独立加载，失败不连累组合看板/风险驾驶舱/策略排行等其他模块。
    const diagnosticsQuery = usePaperExecutionDiagnosticsQuery();
    // GateK K3B：Paper 策略评估独立加载，失败不连累其他模块。
    const strategyEvaluationsQuery = usePaperStrategyEvaluationsQuery();
    // GateK K4B：Paper 规则化自动复盘独立加载，失败不连累组合看板/诊断/评估/排行等其他模块。
    const autoReviewsQuery = usePaperAutoReviewsQuery();

    const focusRunId = selectedRow?.paperRunId ?? null;
    const detailQuery = usePaperTradingDetailQuery(focusRunId);
    const focusPublishId = detailQuery.data?.publishId ?? selectedRow?.publishId ?? null;
    const publishQuery = usePublishDetailQuery(focusPublishId);
    const publishDetail = Array.isArray(publishQuery.data) ? null : (publishQuery.data ?? null);
    const comparisonBacktestConfigId = publishDetail?.backtestConfigId ?? null;
    const backtestDetailQuery = useBacktestDetailQuery(comparisonBacktestConfigId);
    const backtestDetail = Array.isArray(backtestDetailQuery.data) ? null : (backtestDetailQuery.data ?? null);
    const evaluationsQuery = useEvaluationsListQuery(
        {backtestConfigId: comparisonBacktestConfigId ?? undefined},
        comparisonBacktestConfigId ? 1 : 0,
    );
    // Loop-8：后端聚合事实源；详情区优先消费 summary 渲染复盘 / 诊断 / 时间线 / 关键指标。
    const summaryQuery = usePaperRunSummaryQuery(focusRunId);
    // 底部明细 Tab 懒加载：仅在对应 Tab 激活时启用查询，避免首屏一次性拉全量明细。
    const ordersQuery = usePaperTradingOrdersQuery(focusRunId, factTab === 'orders');
    const tradesQuery = usePaperTradingTradesQuery(focusRunId, factTab === 'trades');
    const positionsQuery = usePaperTradingPositionsQuery(focusRunId, factTab === 'positions');
    const riskResultsQuery = usePaperTradingRiskResultsQuery(focusRunId, factTab === 'risk-results');
    // 权益曲线仍随中部「权益与回撤曲线」图卡首屏加载（图表能力不回退）；底部资金曲线 Tab 复用同一缓存。
    const equityCurveQuery = usePaperTradingEquityCurveQuery(focusRunId);
    const positionCurveQuery = usePaperTradingPositionCurveQuery(focusRunId, factTab === 'position-curve');
    const replayQuery = usePaperTradingReplayQuery(focusRunId, factTab === 'replay');
    const emergencyStopsQuery = usePaperTradingEmergencyStopsQuery(focusRunId);
    const dailyReportsQuery = usePaperDailyReportsQuery(focusRunId);

    // 顶部状态条所需读查询；与右侧/中部面板共享 React Query 缓存键，不重复请求。
    const heartbeatsQuery = usePaperHeartbeatsQuery(focusRunId);
    const schedulesQuery = usePaperSchedulesQuery(focusRunId);
    const alertsQuery = usePaperAlertsQuery(focusRunId);
    const stabilityChecksQuery = usePaperStabilityChecksQuery(focusRunId);
    // 异常原因聚合所需；与右侧「恢复事件」面板共享 React Query 缓存键（默认参数），不重复请求。
    const recoveryEventsQuery = usePaperRecoveryEventsQuery(focusRunId);

    // 切换到另一个 run 时把明细 Tab 复位到无网络的「快照」页，确保新 run 首屏只走 detail + summary；
    // 同一 run 的启停刷新不复位（focusRunId 不变），避免打断用户当前查看的明细 Tab。
    useEffect(() => {
        setFactTab('snapshots');
    }, [focusRunId]);

    const createMutation = useCreatePaperTradingRunMutation();
    const startMutation = useStartPaperTradingRunMutation();
    const stopMutation = useStopPaperTradingRunMutation();
    const riskOnceMutation = useRunRiskOnceMutation();
    const emergencyStopMutation = useEmergencyStopMutation();
    const generateDailyReportMutation = useGenerateDailyReportMutation();

    const hasSearched = searchVersion > 0;
    const visibleItems = listQuery.data ?? [];

    // 焦点 run 优先用 detailQuery 的最新数据（mutation 后会失效重取），回退到列表快照，
    // 使顶部状态条 / 操作可用性在启停、紧急停机后反映最新运行态。
    const focusRun = detailQuery.data ?? selectedRow;
    const focusStatus = focusRun?.status ?? selectedRow?.status ?? '';

    // 焦点 run 派生状态（顶部状态条 / 中部摘要）
    const latestHeartbeat = latestBy(heartbeatsQuery.data ?? [], (item) => item.heartbeatTime);
    const latestFireTime = latestBy(schedulesQuery.data ?? [], (item) => item.lastFireTime)?.lastFireTime ?? null;
    const openAlertCount = (alertsQuery.data ?? []).filter((alert) => alert.status === 'OPEN').length;
    const latestStability = latestBy(stabilityChecksQuery.data ?? [], (item) => item.checkWindowEnd);
    const latestRisk = latestBy(riskResultsQuery.data ?? [], (item) => item.createdAt);
    const latestDailyReport = [...(dailyReportsQuery.data ?? [])]
        .sort((left, right) => right.reportDate.localeCompare(left.reportDate))[0] ?? null;
    const paperOrders = ordersQuery.data ?? [];
    const paperTrades = tradesQuery.data ?? [];
    const paperPositions = positionsQuery.data ?? [];
    const equitySnapshots = equityCurveQuery.data ?? [];
    const latestOrder = latestBy(paperOrders, (item) => item.updatedAt);
    const latestTrade = latestBy(paperTrades, (item) => item.tradedAt);
    const latestPosition = latestBy(paperPositions, (item) => item.updatedAt);
    const latestEquitySnapshot = latestBy(equitySnapshots, (item) => item.snapshotTime);
    const latestLoopPnl = latestEquitySnapshot
        ? sumNullableAmounts(latestEquitySnapshot.realizedPnl, latestEquitySnapshot.unrealizedPnl)
        : latestPosition
            ? sumNullableAmounts(latestPosition.realizedPnl, latestPosition.unrealizedPnl)
            : sumNullableAmounts(latestDailyReport?.dailyPnl);
    // summary 优先；加载失败、未就绪或响应结构异常时回退到明细查询的前端派生，保证详情区与明细表格不崩。
    const summaryData = summaryQuery.data;
    const summary: PaperRunSummaryResponse | null = summaryData && !Array.isArray(summaryData) && summaryData.counts
        ? summaryData
        : null;

    const orderCount = summary?.counts.orderCount ?? paperOrders.length;
    const fillCount = summary?.counts.fillCount ?? paperTrades.length;
    const positionCount = summary?.counts.positionCount ?? paperPositions.length;
    const netPnl = summary ? toNullableNumber(summary.resultReview.netPnl) : latestLoopPnl;
    const openAlertCountView = summary?.counts.openAlertCount ?? openAlertCount;

    // 首屏关键指标优先取 summary.latest（订单/成交/持仓/权益/风控明细已懒加载，不再依赖其首屏请求）；
    // summary 缺失时回退到已加载明细派生（懒加载下多为空，属于可控回退）。
    const effLatestOrder = summary?.latest.order ?? latestOrder;
    const effLatestTrade = summary?.latest.trade ?? latestTrade;
    const effLatestPosition = summary?.latest.position ?? latestPosition;
    const effLatestEquitySnapshot = summary?.latest.equitySnapshot ?? latestEquitySnapshot;
    const effLatestRisk = summary?.latest.riskResult ?? latestRisk;

    const paperTimelineEvents = summary
        ? mapSummaryTimeline(summary)
        : focusRun
            ? buildPaperTimelineEvents({
                run: focusRun,
                latestOrder,
                latestTrade,
                latestPosition,
                latestEquitySnapshot,
                latestRisk,
                latestLoopPnl,
            })
            : [];
    const paperRunReview = summary
        ? mapSummaryReview(summary)
        : focusRun
            ? buildPaperRunReview({
                run: focusRun,
                orderCount: paperOrders.length,
                fillCount: paperTrades.length,
                positionCount: paperPositions.length,
                netPnl: latestLoopPnl,
                latestRisk,
            })
            : null;
    const comparisonEvaluations = (evaluationsQuery.data ?? []).filter((item) => (
        publishDetail?.backtestRunId ? item.backtestRunId === publishDetail.backtestRunId : true
    ));
    const latestComparisonEvaluation = latestBy(
        comparisonEvaluations,
        (item) => item.evaluatedAt,
    ) ?? comparisonEvaluations[0] ?? null;
    const backtestPaperComparison = focusRun
        ? buildBacktestPaperComparison({
            run: focusRun,
            publish: publishDetail,
            backtest: backtestDetail,
            evaluation: latestComparisonEvaluation,
            paperReview: paperRunReview,
        })
        : null;
    // Loop-11：Strategy → Publish → Paper 链路视图，复用与对照卡片相同的已加载查询，不触发新请求。
    const paperLineage = focusRun
        ? buildPaperLineage({
            run: focusRun,
            publish: publishDetail,
            backtest: backtestDetail,
            evaluation: latestComparisonEvaluation,
            paperReview: paperRunReview,
        })
        : null;
    // Loop-12：Paper 账户资产与收益率，复用首屏已加载的 equity 快照 / 日报 / 来源 backtest 初始资金，不触发新请求。
    const paperAccountPerformance = focusRun
        ? buildPaperAccountPerformance({
            snapshots: equitySnapshots,
            dailyReport: latestDailyReport,
            backtestInitialCapital: toNullableNumber(backtestDetail?.initialCapital),
            runStatus: focusRun.status,
            fillCount,
        })
        : null;
    const accountPerformanceLoading = (equityCurveQuery.isFetching && equitySnapshots.length === 0)
        || (dailyReportsQuery.isFetching && (dailyReportsQuery.data ?? []).length === 0);
    const backtestComparisonLoading = publishQuery.isFetching
        || backtestDetailQuery.isFetching
        || evaluationsQuery.isFetching;
    // 复用运行结果复盘的风控判定口径（'拦截' 即 riskBlocked），让诊断与复盘结论一致。
    const paperRunDiagnoses = summary
        ? mapSummaryDiagnoses(summary)
        : focusRun
            ? buildPaperRunDiagnoses({
                run: focusRun,
                orderCount: paperOrders.length,
                fillCount: paperTrades.length,
                netPnl: latestLoopPnl,
                riskBlocked: classifyRiskResult(latestRisk).riskResult === '拦截',
                hasRiskData: Boolean(latestRisk),
                openAlertCount,
                recoveryCount: (recoveryEventsQuery.data ?? []).length,
            })
            : [];

    const columns: ColumnsType<PaperRunRow> = [
        {
            title: 'Paper Run',
            dataIndex: 'paperRunId',
            key: 'paperRunId',
            render: (value: string, record) => (
                <Space direction="vertical" size={2} style={{width: '100%'}}>
                    {/* 渲染完整 paperRunId 文本（E2E 以 hasText 全量 id 定位行），视觉溢出交由纯 CSS 省略，
                        不使用 AntD JS ellipsis，避免 DOM 文本被截断破坏 hasText 定位 */}
                    <span className="nq-mono nq-run-id" title={value}>{value}</span>
                    <Space size={6}>
                        <NqStatusTag status={record.status}/>
                        <NqEnvironmentBadge env={record.tradeEnv}/>
                    </Space>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {record.symbol} · {record.intervalCode} · {record.exchangeCode}
                    </Typography.Text>
                    <Typography.Text type="secondary" className="nq-num" style={{fontSize: 11}}>
                        更新 {formatDateTime(record.updatedAt)}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: '操作',
            key: 'action',
            width: 96,
            render: (_, record) => (
                <Space direction="vertical" size={2}>
                    <Button type="link" size="small" style={{paddingInline: 0}} onClick={() => setSelectedRow(record)}>
                        查看详情
                    </Button>
                    <Button
                        type="link"
                        size="small"
                        style={{paddingInline: 0}}
                        disabled={record.status !== 'CREATED'}
                        onClick={() => handleStart(record.paperRunId)}
                    >
                        启动
                    </Button>
                    <Button
                        type="link"
                        size="small"
                        danger
                        style={{paddingInline: 0}}
                        disabled={record.status !== 'RUNNING'}
                        onClick={() => handleStop(record.paperRunId)}
                    >
                        停止
                    </Button>
                </Space>
            ),
        },
    ];

    const handleSearch = (values: PaperTradingListFilters) => {
        setSubmittedFilters({
            publishId: normalizeOptionalText(values.publishId),
            status: normalizeOptionalText(values.status),
        });
        setSearchVersion((v) => v + 1);
    };

    const handleReset = () => {
        queryForm.resetFields();
        setSubmittedFilters(defaultPaperTradingListFilters);
        setSearchVersion(0);
    };

    const handleStart = (paperRunId: string) => {
        startMutation.mutate(paperRunId, {
            onSuccess: (run) => {
                message.success('Paper run 已启动。');
                setSelectedRow(run);
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    const handleStop = (paperRunId: string) => {
        stopMutation.mutate(paperRunId, {
            onSuccess: (run) => {
                message.success('Paper run 已停止。');
                setSelectedRow(run);
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    const handleCreate = (values: PaperTradingRunCreateRequest) => {
        const payload: PaperTradingRunCreateRequest = {
            publishId: values.publishId.trim(),
            tradeEnv: values.tradeEnv?.trim() || 'SIM',
            exchangeCode: values.exchangeCode?.trim() || 'BINANCE',
            marketType: values.marketType?.trim() || 'SPOT',
            symbol: values.symbol?.trim() || 'BTC-USDT',
            intervalCode: values.intervalCode?.trim() || '1m',
            configSnapshotJson: normalizeOptionalText(values.configSnapshotJson) || undefined,
        };
        createMutation.mutate(payload, {
            onSuccess: (run) => {
                message.success('Paper run 已创建。');
                setSelectedRow(run);
                setCreateOpen(false);
                createForm.resetFields();
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    return (
        <>
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title="模拟交易"
                        description="Paper Trading 运行控制台：聚焦运行状态、心跳、调度、告警、恢复、稳定性验收与权益/回撤曲线。基于已发布策略版本创建 SIM/Paper run 并固化全链路快照。"
                        badge="Paper Trading Console"
                        tip={(
                            <NqRiskBanner
                                level="info"
                                message="当前为 PAPER（SIM）模拟环境，LIVE 交易未开启。"
                                description="本页所有下单、撤单、紧急停机均只作用于 SIM/Paper Trading，不会触发真实交易所下单或撤单；不存在一键实盘全平能力。"
                            />
                        )}
                    />
                </Card>

                <PaperPortfolioDashboard query={portfolioQuery}/>

                <PaperRiskDrawdownDashboard query={portfolioQuery}/>

                <PaperExecutionDiagnosticsDashboard query={diagnosticsQuery}/>

                <PaperStrategyEvaluationDashboard query={strategyEvaluationsQuery}/>

                <PaperAutoReviewDashboard query={autoReviewsQuery}/>

                <PaperStrategyRankingDashboard query={portfolioQuery}/>

                <NqFilterBar
                    actions={(
                        <Space>
                            <Button type="primary" onClick={() => queryForm.submit()}>
                                查询
                            </Button>
                            <Button onClick={handleReset}>
                                重置
                            </Button>
                            <Button type="primary" ghost onClick={() => setCreateOpen(true)}>
                                创建 Paper Run
                            </Button>
                        </Space>
                    )}
                >
                    <Form
                        form={queryForm}
                        layout="vertical"
                        initialValues={defaultPaperTradingListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="发布 ID" name="publishId">
                                    <Input placeholder="按发布记录 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="状态" name="status">
                                    <Select allowClear placeholder="全部状态" options={PAPER_RUN_STATUS_OPTIONS}/>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </NqFilterBar>

                <Row gutter={[12, 12]} align="top">
                    {/* 左侧：Paper Run 列表（焦点选择入口） */}
                    <Col xs={24} xl={7} xxl={6}>
                        <Card
                            className="page-section"
                            bordered={false}
                            title="Paper Run 列表"
                            styles={{body: {padding: 0}}}
                            extra={hasSearched ? (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>共 {visibleItems.length} 条记录</Typography.Text>
                            ) : null}
                        >
                            {!hasSearched ? (
                                <div style={{padding: 16}}>
                                    <NqEmptyState description="点击查询后加载 Paper Trading run 列表。"/>
                                </div>
                            ) : listQuery.error ? (
                                <div style={{padding: 16}}>
                                    <NqErrorState
                                        title="Paper Trading run 列表查询失败"
                                        error={listQuery.error as AppApiError}
                                        onRetry={() => setSearchVersion((v) => v + 1)}
                                    />
                                </div>
                            ) : (
                                <NqDataTable<PaperRunRow>
                                    rowKey="paperRunId"
                                    columns={columns}
                                    dataSource={visibleItems}
                                    loading={listQuery.isFetching}
                                    showHeader={false}
                                    pagination={{pageSize: 10, showSizeChanger: false, simple: true}}
                                    rowClassName={(record) => (record.paperRunId === focusRunId ? 'nq-row-active' : '')}
                                    // 列表内部滚动：让 Playwright/用户定位某行时滚动表体而非窗口，
                                    // 避免目标行被粘性页头遮挡导致点击被拦截。
                                    scroll={{y: 420}}
                                    locale={{emptyText: '当前筛选条件下没有 Paper Trading run。'}}
                                />
                            )}
                        </Card>
                    </Col>

                    {/* 焦点 run 控制台主体 */}
                    <Col xs={24} xl={17} xxl={18}>
                        {!selectedRow ? (
                            <Card className="page-section" bordered={false}>
                                <NqEmptyState description="从左侧选择一个 Paper Run，查看运行控制台（状态、曲线、告警、恢复、调度、事实表）。"/>
                            </Card>
                        ) : (
                            <section aria-label="Paper Trading 详情">
                                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                    {/* 顶部状态区 */}
                                    <Card className="page-section" bordered={false} styles={{body: {paddingBottom: 12}}}>
                                        <Space size={8} wrap style={{marginBottom: 12}}>
                                            <Typography.Text strong>运行控制台</Typography.Text>
                                            <NqStatusTag status={focusStatus}/>
                                            <NqEnvironmentBadge env={selectedRow.tradeEnv}/>
                                            <NqEnvironmentBadge env={appEnv.envLabel}/>
                                            <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 12}}>
                                                {selectedRow.paperRunId}
                                            </Typography.Text>
                                        </Space>
                                        <div className="nq-status-strip">
                                            <NqMetricCard label="运行状态" value={<NqStatusTag status={focusStatus}/>}/>
                                            <NqMetricCard
                                                label="心跳"
                                                value={latestHeartbeat ? <NqStatusTag status={latestHeartbeat.status} tone={latestHeartbeat.status === 'STOPPED' ? 'danger' : undefined}/> : '-'}
                                                footer={latestHeartbeat ? formatDateTime(latestHeartbeat.heartbeatTime) : '暂无心跳'}
                                                loading={heartbeatsQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="最近调度触发"
                                                value={<span className="nq-num" style={{fontSize: 13}}>{latestFireTime ? formatDateTime(latestFireTime) : '-'}</span>}
                                                footer={latestFireTime ? undefined : '暂无调度触发'}
                                                loading={schedulesQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="未处理告警"
                                                value={String(openAlertCountView)}
                                                tone={openAlertCountView > 0 ? 'warning' : 'muted'}
                                                loading={summary ? false : alertsQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="稳定性验收"
                                                value={latestStability ? <NqStatusTag status={latestStability.status} tone={latestStability.status === 'PASSED' ? 'success' : latestStability.status === 'PARTIAL' ? 'warning' : 'danger'}/> : '-'}
                                                footer={latestStability ? `窗口至 ${formatDateTime(latestStability.checkWindowEnd)}` : '暂无验收'}
                                                loading={stabilityChecksQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="风控状态"
                                                value={effLatestRisk ? <NqStatusTag status={effLatestRisk.status} tone={effLatestRisk.status === 'PASSED' ? 'success' : effLatestRisk.status === 'REJECTED' ? 'danger' : 'warning'}/> : '-'}
                                                footer={effLatestRisk ? effLatestRisk.checkType : '暂无风控检查'}
                                                loading={summary ? false : (factTab === 'risk-results' && riskResultsQuery.isPending)}
                                            />
                                            <NqMetricCard label="交易环境" value={<NqEnvironmentBadge env={selectedRow.tradeEnv}/>} footer="LIVE 未开启"/>
                                        </div>
                                        <Space size={8} wrap style={{marginTop: 12}}>
                                            <Button
                                                type="primary"
                                                size="small"
                                                disabled={focusStatus !== 'CREATED'}
                                                loading={startMutation.isPending}
                                                onClick={() => handleStart(selectedRow.paperRunId)}
                                            >
                                                启动 Paper Run
                                            </Button>
                                            <Button
                                                danger
                                                size="small"
                                                disabled={focusStatus !== 'RUNNING'}
                                                loading={stopMutation.isPending}
                                                onClick={() => handleStop(selectedRow.paperRunId)}
                                            >
                                                停止 Paper Run
                                            </Button>
                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                生命周期操作仅作用于当前 SIM/Paper run；LIVE 未开启，不会触发真实交易所。
                                            </Typography.Text>
                                        </Space>
                                        {detailQuery.error ? (
                                            <div style={{marginTop: 12}}>
                                                <NqErrorState title="Paper run 详情加载失败" error={detailQuery.error as AppApiError}/>
                                            </div>
                                        ) : null}
                                    </Card>

                                    {paperAccountPerformance ? (
                                        <PaperAccountPerformanceCard
                                            performance={paperAccountPerformance}
                                            loading={accountPerformanceLoading}
                                        />
                                    ) : null}

                                    <Card
                                        className="page-section"
                                        bordered={false}
                                        title="Paper 执行闭环"
                                        extra={<Typography.Text type="secondary" style={{fontSize: 12}}>订单 → 成交 → 持仓 / PnL → 风控</Typography.Text>}
                                    >
                                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                            <NqRiskBanner
                                                level="info"
                                                message="只读聚合当前 Paper run 的执行事实。"
                                                description="该摘要优先消费后端 summary 聚合事实源（复盘、诊断、时间线、关键指标），不新增交易动作，不触发真实交易所或 LIVE。"
                                            />
                                            {summaryQuery.isError ? (
                                                <Typography.Text type="warning" style={{fontSize: 12}}>
                                                    运行摘要聚合接口加载失败，已回退到明细查询派生展示；下方明细表格不受影响。
                                                </Typography.Text>
                                            ) : null}
                                            {paperRunReview ? <PaperRunReviewCard review={paperRunReview}/> : null}
                                            {paperLineage ? (
                                                <PaperLineageCard
                                                    lineage={paperLineage}
                                                    loading={backtestComparisonLoading}
                                                />
                                            ) : null}
                                            {backtestPaperComparison ? (
                                                <BacktestPaperComparisonCard
                                                    comparison={backtestPaperComparison}
                                                    loading={backtestComparisonLoading}
                                                />
                                            ) : null}
                                            {paperRunDiagnoses.length > 0 ? <PaperRunDiagnosisCard diagnoses={paperRunDiagnoses}/> : null}
                                            <div className="nq-status-strip">
                                                <NqMetricCard
                                                    label="订单事实"
                                                    value={String(orderCount)}
                                                    footer={effLatestOrder ? `${effLatestOrder.status} · ${formatDateTime(effLatestOrder.updatedAt)}` : '暂无订单'}
                                                    loading={summaryQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="成交事实"
                                                    value={String(fillCount)}
                                                    footer={effLatestTrade ? formatDateTime(effLatestTrade.tradedAt) : '暂无成交'}
                                                    loading={summaryQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="持仓事实"
                                                    value={String(positionCount)}
                                                    footer={effLatestPosition ? formatDateTime(effLatestPosition.updatedAt) : '暂无持仓'}
                                                    loading={summaryQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="净 PnL"
                                                    value={<NqAmountText value={netPnl} signed colorBySign/>}
                                                    footer={effLatestEquitySnapshot ? `权益快照 ${formatDateTime(effLatestEquitySnapshot.snapshotTime)}` : effLatestPosition ? '持仓实时汇总' : '暂无 PnL'}
                                                    tone={pnlTone(netPnl)}
                                                    loading={summaryQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="风控闭环"
                                                    value={effLatestRisk ? <NqStatusTag status={effLatestRisk.status} tone={effLatestRisk.status === 'PASSED' ? 'success' : effLatestRisk.status === 'REJECTED' ? 'danger' : 'warning'}/> : '-'}
                                                    footer={effLatestRisk ? `${effLatestRisk.checkType} · ${effLatestRisk.severity}` : '暂无风控检查'}
                                                    loading={summaryQuery.isPending}
                                                />
                                            </div>
                                            <Card
                                                size="small"
                                                title="运行事件时间线"
                                                extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
                                                styles={{body: {paddingBottom: 0}}}
                                            >
                                                <PaperRunTimeline events={paperTimelineEvents}/>
                                            </Card>
                                        </Space>
                                    </Card>

                                    <Row gutter={[12, 12]} align="top">
                                        {/* 中间主区域 */}
                                        <Col xs={24} xl={15}>
                                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                <Card className="page-section" bordered={false} title="权益与回撤曲线">
                                                    {equityCurveQuery.isFetching && (equityCurveQuery.data ?? []).length === 0 ? (
                                                        <NqLoadingState/>
                                                    ) : equityCurveQuery.error ? (
                                                        <NqErrorState error={equityCurveQuery.error as AppApiError} onRetry={() => equityCurveQuery.refetch()}/>
                                                    ) : (equityCurveQuery.data ?? []).length === 0 ? (
                                                        <NqEmptyState description="暂无权益曲线数据，运行产生快照后自动绘制。"/>
                                                    ) : (
                                                        <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                            <NqEquityCurveChart data={equityCurveQuery.data ?? []}/>
                                                            <NqDrawdownChart data={equityCurveQuery.data ?? []}/>
                                                        </Space>
                                                    )}
                                                </Card>

                                                <Card
                                                    className="page-section"
                                                    bordered={false}
                                                    title="最新日报摘要"
                                                    extra={(
                                                        <Button
                                                            size="small"
                                                            type="primary"
                                                            ghost
                                                            loading={generateDailyReportMutation.isPending}
                                                            onClick={() => generateDailyReportMutation.mutate(
                                                                {paperRunId: selectedRow.paperRunId, request: {}},
                                                                {
                                                                    onSuccess: () => message.success('日报已生成。'),
                                                                    onError: (err) => message.error(formatApiError(err as AppApiError)),
                                                                },
                                                            )}
                                                        >
                                                            生成今日日报
                                                        </Button>
                                                    )}
                                                >
                                                    {dailyReportsQuery.isFetching && (dailyReportsQuery.data ?? []).length === 0 ? (
                                                        <NqLoadingState/>
                                                    ) : dailyReportsQuery.error ? (
                                                        <NqErrorState error={dailyReportsQuery.error as AppApiError} onRetry={() => dailyReportsQuery.refetch()}/>
                                                    ) : !latestDailyReport ? (
                                                        <NqEmptyState description="当前 Paper run 暂无日报。"/>
                                                    ) : (
                                                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                            <div className="nq-status-strip">
                                                                <NqMetricCard label="总权益" value={<NqAmountText value={latestDailyReport.totalEquity}/>}/>
                                                                <NqMetricCard
                                                                    label="日盈亏"
                                                                    value={<NqAmountText value={latestDailyReport.dailyPnl} signed colorBySign/>}
                                                                    tone={Number(latestDailyReport.dailyPnl ?? 0) > 0 ? 'up' : Number(latestDailyReport.dailyPnl ?? 0) < 0 ? 'down' : 'default'}
                                                                />
                                                                <NqMetricCard label="日收益率" value={<NqPercentText value={latestDailyReport.dailyReturn} ratio colorBySign/>}/>
                                                                <NqMetricCard label="最大回撤" value={<NqPercentText value={latestDailyReport.maxDrawdown} ratio signed={false}/>} tone="warning"/>
                                                            </div>
                                                            <NqDataTable
                                                                rowKey="reportId"
                                                                pagination={false}
                                                                dataSource={dailyReportsQuery.data ?? []}
                                                                scroll={{x: 1100, y: 240}}
                                                                columns={[
                                                                    {title: '日期', dataIndex: 'reportDate', key: 'reportDate', width: 110, className: 'nq-num'},
                                                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'GENERATED' ? 'success' : v === 'PARTIAL' ? 'warning' : 'danger'}/>},
                                                                    nqNumericColumn({title: '总权益', dataIndex: 'totalEquity', key: 'totalEquity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '日盈亏', dataIndex: 'dailyPnl', key: 'dailyPnl', width: 120, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                                                    nqNumericColumn({title: '日收益率', dataIndex: 'dailyReturn', key: 'dailyReturn', width: 100, render: (v) => <NqPercentText value={v as string} ratio colorBySign/>}),
                                                                    nqNumericColumn({title: '最大回撤', dataIndex: 'maxDrawdown', key: 'maxDrawdown', width: 100, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                                                                    nqNumericColumn({title: '订单数', dataIndex: 'orderCount', key: 'orderCount', width: 80}),
                                                                    nqNumericColumn({title: '成交数', dataIndex: 'tradeCount', key: 'tradeCount', width: 80}),
                                                                    nqNumericColumn({title: '告警数', dataIndex: 'alertCount', key: 'alertCount', width: 80}),
                                                                    {title: '生成时间', dataIndex: 'generatedAt', key: 'generatedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </Space>
                                                    )}
                                                </Card>

                                                <NqStabilityCheckPanel paperRunId={selectedRow.paperRunId}/>
                                            </Space>
                                        </Col>

                                        {/* 右侧：告警 / 恢复 / 心跳 / 调度 / 操作 */}
                                        <Col xs={24} xl={9}>
                                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                <Card className="page-section" bordered={false} title="操作区">
                                                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                            紧急停机仅作用于当前 SIM/Paper run，会写入审计事件，不触发真实 LIVE 下单或撤单。
                                                        </Typography.Text>
                                                        <NqDangerConfirmButton
                                                            size="small"
                                                            block
                                                            disabled={focusStatus !== 'RUNNING'}
                                                            loading={emergencyStopMutation.isPending}
                                                            confirmTitle="确认紧急停机"
                                                            confirmContent="此操作将立即停止当前 Paper run。紧急停机只作用于 SIM/Paper Trading，不会触发真实 LIVE 下单或撤单。确认执行？"
                                                            okText="确认停机"
                                                            onConfirm={() => emergencyStopMutation.mutate(
                                                                {
                                                                    paperRunId: selectedRow.paperRunId,
                                                                    request: {triggerType: 'MANUAL', reason: '手动紧急停机', triggeredBy: 'console-user'},
                                                                },
                                                                {
                                                                    onSuccess: () => {
                                                                        message.success('紧急停机已执行。');
                                                                        setSearchVersion((v) => v + 1);
                                                                    },
                                                                    onError: (err) => message.error(formatApiError(err as AppApiError)),
                                                                },
                                                            )}
                                                        >
                                                            紧急停机
                                                        </NqDangerConfirmButton>
                                                        {(emergencyStopsQuery.data ?? []).length > 0 ? (
                                                            <NqDataTable
                                                                rowKey="emergencyStopId"
                                                                pagination={false}
                                                                dataSource={emergencyStopsQuery.data ?? []}
                                                                scroll={{y: 180}}
                                                                columns={[
                                                                    {title: '触发类型', dataIndex: 'triggerType', key: 'triggerType', width: 110},
                                                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'APPLIED' ? 'danger' : v === 'RESOLVED' ? 'success' : 'warning'}/>},
                                                                    {title: '触发时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        ) : null}
                                                    </Space>
                                                </Card>

                                                <NqAlertPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqRecoveryPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqHeartbeatPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqScheduleFirePanel paperRunId={selectedRow.paperRunId}/>
                                            </Space>
                                        </Col>
                                    </Row>

                                    {/* 底部事实表 */}
                                    <Card className="page-section" bordered={false} title="运行事实">
                                        <Descriptions bordered column={3} size="small" style={{marginBottom: 12}}>
                                            <Descriptions.Item label="Paper Run ID">
                                                <span className="nq-mono">{selectedRow.paperRunId}</span>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="发布 ID">
                                                <span className="nq-mono">{selectedRow.publishId}</span>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="策略版本 ID">
                                                <span className="nq-mono">{selectedRow.strategyVersionId || '-'}</span>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="Symbol">{selectedRow.symbol}</Descriptions.Item>
                                            <Descriptions.Item label="周期">{selectedRow.intervalCode}</Descriptions.Item>
                                            <Descriptions.Item label="市场类型">{selectedRow.marketType}</Descriptions.Item>
                                            <Descriptions.Item label="启动时间">{formatDateTime(selectedRow.startedAt)}</Descriptions.Item>
                                            <Descriptions.Item label="停止时间">{formatDateTime(selectedRow.stoppedAt)}</Descriptions.Item>
                                            <Descriptions.Item label="创建人">{selectedRow.createdBy}</Descriptions.Item>
                                        </Descriptions>
                                        <Tabs
                                            activeKey={factTab}
                                            onChange={setFactTab}
                                            items={[
                                                {
                                                    key: 'orders',
                                                    label: '订单',
                                                    children: (
                                                        <PaperFactSection
                                                            query={ordersQuery}
                                                            emptyText="当前 Paper run 暂无订单事实。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="paperOrderId"
                                                                pagination={false}
                                                                dataSource={ordersQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '订单 ID', dataIndex: 'paperOrderId', key: 'paperOrderId', className: 'nq-mono'},
                                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                                    {title: '类型', dataIndex: 'orderType', key: 'orderType', width: 80},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '价格', dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
                                                                    {title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'trades',
                                                    label: '成交',
                                                    children: (
                                                        <PaperFactSection
                                                            query={tradesQuery}
                                                            emptyText="当前 Paper run 暂无成交事实。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="paperTradeId"
                                                                pagination={false}
                                                                dataSource={tradesQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '成交 ID', dataIndex: 'paperTradeId', key: 'paperTradeId', className: 'nq-mono'},
                                                                    {title: '订单 ID', dataIndex: 'paperOrderId', key: 'paperOrderId', className: 'nq-mono'},
                                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '价格', dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '手续费', dataIndex: 'fee', key: 'fee', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    {title: '成交时间', dataIndex: 'tradedAt', key: 'tradedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'positions',
                                                    label: '持仓',
                                                    children: (
                                                        <PaperFactSection
                                                            query={positionsQuery}
                                                            emptyText="当前 Paper run 暂无持仓事实。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="paperPositionId"
                                                                pagination={false}
                                                                dataSource={positionsQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 120},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '均价', dataIndex: 'avgPrice', key: 'avgPrice', width: 120, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '已实现盈亏', dataIndex: 'realizedPnl', key: 'realizedPnl', width: 140, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                                                    nqNumericColumn({title: '未实现盈亏', dataIndex: 'unrealizedPnl', key: 'unrealizedPnl', width: 140, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                                                    {title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'snapshots',
                                                    label: '快照',
                                                    children: (
                                                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                            <SnapshotBlock title="Publish Snapshot" content={selectedRow.publishSnapshotJson}/>
                                                            <SnapshotBlock title="Strategy Version Snapshot" content={selectedRow.strategyVersionSnapshotJson}/>
                                                            <SnapshotBlock title="Dataset Snapshot" content={selectedRow.datasetSnapshotJson}/>
                                                            <SnapshotBlock title="Param Snapshot" content={selectedRow.paramSnapshotJson}/>
                                                            <SnapshotBlock title="Config Snapshot" content={selectedRow.configSnapshotJson}/>
                                                        </Space>
                                                    ),
                                                },
                                                {
                                                    key: 'risk-results',
                                                    label: '风控结果',
                                                    children: (
                                                        <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                            <Button
                                                                size="small"
                                                                loading={riskOnceMutation.isPending}
                                                                onClick={() => riskOnceMutation.mutate(selectedRow.paperRunId, {
                                                                    onSuccess: () => message.success('风控检查已执行。'),
                                                                    onError: (err) => message.error(formatApiError(err as AppApiError)),
                                                                })}
                                                            >
                                                                执行风控检查
                                                            </Button>
                                                            <PaperFactSection
                                                                query={riskResultsQuery}
                                                                emptyText="当前 Paper run 暂无风控检查结果。"
                                                            >
                                                                <NqDataTable
                                                                    rowKey="riskResultId"
                                                                    pagination={false}
                                                                    dataSource={riskResultsQuery.data ?? []}
                                                                    scroll={{x: 900}}
                                                                    columns={[
                                                                        {title: '检查类型', dataIndex: 'checkType', key: 'checkType', width: 180},
                                                                        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'PASSED' ? 'success' : v === 'REJECTED' ? 'danger' : 'warning'}/>},
                                                                        {title: '严重程度', dataIndex: 'severity', key: 'severity', width: 100},
                                                                        {title: '消息', dataIndex: 'message', key: 'message'},
                                                                        {title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    ]}
                                                                />
                                                            </PaperFactSection>
                                                        </Space>
                                                    ),
                                                },
                                                {
                                                    key: 'equity-curve',
                                                    label: '资金曲线',
                                                    children: (
                                                        <PaperFactSection
                                                            query={equityCurveQuery}
                                                            emptyText="当前 Paper run 暂无资金曲线数据。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="equitySnapshotId"
                                                                pagination={false}
                                                                dataSource={equityCurveQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '时间', dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    nqNumericColumn({title: '总权益', dataIndex: 'totalEquity', key: 'totalEquity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '现金', dataIndex: 'cashBalance', key: 'cashBalance', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '持仓市值', dataIndex: 'positionValue', key: 'positionValue', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '回撤', dataIndex: 'drawdown', key: 'drawdown', width: 100, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                                                                    {title: '来源', dataIndex: 'source', key: 'source', width: 100},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'position-curve',
                                                    label: '持仓曲线',
                                                    children: (
                                                        <PaperFactSection
                                                            query={positionCurveQuery}
                                                            emptyText="当前 Paper run 暂无持仓曲线数据。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="positionSnapshotId"
                                                                pagination={false}
                                                                dataSource={positionCurveQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 120},
                                                                    {title: '时间', dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '均价', dataIndex: 'avgPrice', key: 'avgPrice', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '标记价', dataIndex: 'markPrice', key: 'markPrice', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '市值', dataIndex: 'positionValue', key: 'positionValue', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    {title: '来源', dataIndex: 'source', key: 'source', width: 100},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'replay',
                                                    label: '交易复盘',
                                                    children: (
                                                        <PaperFactSection
                                                            query={replayQuery}
                                                            emptyText="当前 Paper run 暂无交易复盘记录。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="replayRecordId"
                                                                pagination={false}
                                                                dataSource={replayQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '时间', dataIndex: 'replayTime', key: 'replayTime', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    {title: '事件类型', dataIndex: 'eventType', key: 'eventType', width: 140},
                                                                    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 120},
                                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                                    nqNumericColumn({title: '价格', dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    {title: '原因', dataIndex: 'reason', key: 'reason'},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                            ]}
                                        />
                                    </Card>
                                </Space>
                            </section>
                        )}
                    </Col>
                </Row>
            </Space>

            <Modal
                open={createOpen}
                title="创建 Paper Trading run"
                onCancel={() => setCreateOpen(false)}
                onOk={() => createForm.submit()}
                confirmLoading={createMutation.isPending}
                destroyOnClose
            >
                <Form
                    form={createForm}
                    layout="vertical"
                    initialValues={DEFAULT_CREATE_VALUES}
                    onFinish={handleCreate}
                >
                    <Form.Item
                        label="发布 ID"
                        name="publishId"
                        rules={[{required: true, message: '请输入发布 ID'}]}
                    >
                        <Input placeholder="发布记录 ID（publishId）"/>
                    </Form.Item>
                    <Form.Item label="交易环境" name="tradeEnv" rules={[{required: true}]}>
                        <Select options={TRADE_ENV_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="交易所" name="exchangeCode" rules={[{required: true}]}>
                        <Select options={EXCHANGE_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="市场类型" name="marketType" rules={[{required: true}]}>
                        <Select options={MARKET_TYPE_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="Symbol" name="symbol" rules={[{required: true}]}>
                        <Select showSearch options={SYMBOL_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="周期" name="intervalCode" rules={[{required: true}]}>
                        <Select options={INTERVAL_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="运行配置快照 JSON（可空）" name="configSnapshotJson">
                        <Input.TextArea rows={3} placeholder='{"feeRate":"0.001","slippageBps":"10"}'/>
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
}

/**
 * PaperFactSection — 事实表三态包装（加载 / 错误 / 空 / 内容）。
 * 统一底部事实表的状态表达，空态文案由调用方按业务口径传入（E2E 依赖原文）。
 */
interface PaperFactSectionProps {
    query: {isFetching: boolean; error: unknown; data?: unknown[]};
    emptyText: string;
    children: ReactNode;
}

function PaperFactSection({query, emptyText, children}: PaperFactSectionProps) {
    const data = query.data ?? [];
    if (query.isFetching && data.length === 0) {
        return <NqLoadingState/>;
    }
    if (query.error) {
        return <NqErrorState error={query.error as AppApiError}/>;
    }
    if (data.length === 0) {
        return <NqEmptyState description={emptyText}/>;
    }
    return <>{children}</>;
}

function PaperRunTimeline({events}: {events: PaperTimelineEvent[]}) {
    if (events.length === 0) {
        return <NqEmptyState description="暂无执行事件，创建或启动 Paper run 后将在此展示。"/>;
    }

    return (
        <Timeline
            items={events.map((event) => ({
                key: event.key,
                color: event.color,
                children: (
                    <Space direction="vertical" size={2} style={{display: 'flex'}}>
                        <Space size={8} wrap>
                            <Typography.Text strong>{event.type}</Typography.Text>
                            <NqStatusTag status={event.status}/>
                            <Typography.Text type="secondary" className="nq-num" style={{fontSize: 12}}>
                                {formatDateTime(event.time)}
                            </Typography.Text>
                        </Space>
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {event.description}
                        </Typography.Text>
                    </Space>
                ),
            }))}
        />
    );
}

function PaperRunReviewCard({review}: {review: PaperRunReview}) {
    return (
        <Card
            size="small"
            title="运行结果复盘"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={10} style={{display: 'flex'}}>
                <NqRiskBanner
                    level={review.conclusionLevel}
                    message={review.conclusion}
                    description="该复盘只基于当前 Paper run 的查询结果，用于判断模拟运行质量，不代表真实交易能力。"
                />
                <div className="nq-status-strip">
                    <NqMetricCard label="最终状态" value={<NqStatusTag status={review.finalStatus}/>}/>
                    <NqMetricCard label="运行时长" value={<span className="nq-num">{review.runtimeDuration}</span>} footer="startedAt 到 stoppedAt / updatedAt / now"/>
                    <NqMetricCard label="订单数" value={String(review.orderCount)}/>
                    <NqMetricCard label="成交数" value={String(review.fillCount)}/>
                    <NqMetricCard label="持仓数" value={String(review.positionCount)}/>
                    <NqMetricCard
                        label="净 PnL"
                        value={<NqAmountText value={review.netPnl} signed colorBySign/>}
                        tone={pnlTone(review.netPnl)}
                    />
                    <NqMetricCard
                        label="风控结果"
                        value={<NqStatusTag status={review.riskResult} tone={review.riskTone}/>}
                    />
                </div>
            </Space>
        </Card>
    );
}

function BacktestPaperComparisonCard({
    comparison,
    loading,
}: {
    comparison: PaperBacktestComparisonResult;
    loading: boolean;
}) {
    return (
        <Card
            size="small"
            title="Backtest → Paper 结果对照"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper comparison · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={10} style={{display: 'flex'}}>
                <NqRiskBanner
                    level={comparison.diagnosis.level}
                    message={(
                        <Space size={8} wrap>
                            <Tag>{comparison.diagnosis.type}</Tag>
                            <Typography.Text strong>{comparison.diagnosis.title}</Typography.Text>
                        </Space>
                    )}
                    description={(
                        <Space direction="vertical" size={2} style={{display: 'flex'}}>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                {comparison.diagnosis.description}
                            </Typography.Text>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                Backtest 与 Paper 均为模拟结果，不代表 LIVE 或真实交易表现。
                            </Typography.Text>
                        </Space>
                    )}
                />

                <Descriptions bordered size="small" column={{xs: 1, sm: 2, xl: 4}}>
                    <Descriptions.Item label="Strategy Version">
                        <span className="nq-mono">{comparison.sourceChain.strategyVersionId ?? '-'}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Publish ID">
                        <span className="nq-mono">{comparison.sourceChain.publishId ?? '-'}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Backtest ID / Trace ID">
                        <span className="nq-mono">
                            {comparison.sourceChain.backtestRunId ?? comparison.sourceChain.backtestConfigId ?? '-'}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="Paper Run ID">
                        <span className="nq-mono">{comparison.sourceChain.paperRunId}</span>
                    </Descriptions.Item>
                </Descriptions>

                {loading ? (
                    <NqLoadingState/>
                ) : !comparison.sourceAvailable ? (
                    <NqEmptyState description="暂无来源 backtest / 无法对照；当前 Paper run 详情仍可独立查看。"/>
                ) : (
                    <NqDataTable
                        rowKey="label"
                        pagination={false}
                        dataSource={comparison.metrics}
                        scroll={{x: 760}}
                        columns={[
                            {title: '对比项', dataIndex: 'label', key: 'label', width: 180},
                            {title: 'Backtest', dataIndex: 'backtest', key: 'backtest', width: 280, render: (value: ReactNode) => value},
                            {title: 'Paper', dataIndex: 'paper', key: 'paper', width: 280, render: (value: ReactNode) => value},
                        ]}
                    />
                )}
            </Space>
        </Card>
    );
}

// 链路节点可追踪性 → 展示标签 / 色调 / 时间线颜色映射。
const LINEAGE_STATE_META: Record<PaperLineageNodeState, {label: string; tone: NqStatusTone; color: PaperTimelineEvent['color']}> = {
    COMPLETE: {label: '已识别', tone: 'success', color: 'green'},
    PARTIAL: {label: '来源不完整', tone: 'warning', color: 'blue'},
    MISSING: {label: '缺失', tone: 'neutral', color: 'gray'},
};

function PaperLineageCard({lineage, loading}: {lineage: PaperLineageResult; loading: boolean}) {
    return (
        <Card
            size="small"
            title="Strategy → Publish → Paper 链路"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper lineage · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={10} style={{display: 'flex'}}>
                <NqRiskBanner
                    level={lineage.diagnosis.level}
                    message={(
                        <Space size={8} wrap>
                            <Tag>{lineage.diagnosis.type}</Tag>
                            <Typography.Text strong>{lineage.diagnosis.title}</Typography.Text>
                        </Space>
                    )}
                    description={(
                        <Space direction="vertical" size={2} style={{display: 'flex'}}>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>{lineage.diagnosis.description}</Typography.Text>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                该链路仅展示研究、发布与 Paper 模拟运行关系，不代表 LIVE 或真实交易表现。
                            </Typography.Text>
                        </Space>
                    )}
                />
                {loading ? (
                    <NqLoadingState/>
                ) : (
                    <Timeline
                        items={lineage.nodes.map((node) => {
                            const meta = LINEAGE_STATE_META[node.state];
                            return {
                                key: node.key,
                                color: meta.color,
                                children: (
                                    <Space direction="vertical" size={2} style={{display: 'flex'}}>
                                        <Space size={8} wrap>
                                            <Typography.Text strong>{node.label}</Typography.Text>
                                            <NqStatusTag status={meta.label} tone={meta.tone}/>
                                            {node.nodeStatus ? <NqStatusTag status={node.nodeStatus} tone={node.nodeStatusTone}/> : null}
                                        </Space>
                                        <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 12}}>
                                            {node.id ?? '-'}
                                        </Typography.Text>
                                        {node.summary}
                                        {node.time ? (
                                            <Typography.Text type="secondary" className="nq-num" style={{fontSize: 12}}>
                                                {node.timeLabel} {formatDateTime(node.time)}
                                            </Typography.Text>
                                        ) : null}
                                    </Space>
                                ),
                            };
                        })}
                    />
                )}
            </Space>
        </Card>
    );
}

function PaperAccountPerformanceCard({performance, loading}: {performance: PaperAccountPerformance; loading: boolean}) {
    return (
        <Card
            className="page-section"
            bordered={false}
            title="Paper 账户资产与收益率"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="info"
                    message="只读汇总当前 Paper 模拟账户的资产与收益率。"
                    description="该收益统计仅基于 Paper 模拟账户与本地执行事实，不代表 LIVE 或真实交易表现。"
                />
                {loading ? (
                    <NqLoadingState/>
                ) : !performance.hasData ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="暂无 Paper 账户资产数据，运行产生 equity 快照或日报后自动汇总。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，无法计算收益率</Typography.Text>
                    </Space>
                ) : (
                    <Space direction="vertical" size={12} style={{display: 'flex'}}>
                        <div className="nq-status-strip">
                            <NqMetricCard label="当前总资产" value={<NqAmountText value={performance.currentEquity}/>}/>
                            <NqMetricCard
                                label="总 PnL"
                                value={<NqAmountText value={performance.totalPnl} signed colorBySign/>}
                                tone={pnlTone(performance.totalPnl)}
                            />
                            <NqMetricCard
                                label="累计收益率"
                                value={performance.totalReturn !== null
                                    ? <NqPercentText value={performance.totalReturn} ratio colorBySign/>
                                    : <Typography.Text type="secondary">数据不足</Typography.Text>}
                            />
                            <NqMetricCard
                                label="最大回撤"
                                value={performance.maxDrawdown !== null
                                    ? <NqPercentText value={performance.maxDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="warning"
                            />
                            <NqMetricCard
                                label="当前回撤"
                                value={performance.currentDrawdown !== null
                                    ? <NqPercentText value={performance.currentDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="warning"
                            />
                            <NqMetricCard label="资金峰值" value={<NqAmountText value={performance.peakEquity}/>}/>
                            <NqMetricCard label="初始资金" value={<NqAmountText value={performance.initialEquity}/>}/>
                            <NqMetricCard label="可用余额" value={<NqAmountText value={performance.availableBalance}/>}/>
                            <NqMetricCard label="持仓市值" value={<NqAmountText value={performance.positionMarketValue}/>}/>
                            <NqMetricCard
                                label="已实现 PnL"
                                value={<NqAmountText value={performance.realizedPnl} signed colorBySign/>}
                                tone={pnlTone(performance.realizedPnl)}
                            />
                            <NqMetricCard
                                label="未实现 PnL"
                                value={<NqAmountText value={performance.unrealizedPnl} signed colorBySign/>}
                                tone={pnlTone(performance.unrealizedPnl)}
                            />
                        </div>

                        {!performance.canComputeReturn ? (
                            <Typography.Text type="warning" style={{fontSize: 12}}>
                                数据不足，无法计算收益率（缺少有效初始资金或当前权益）。
                            </Typography.Text>
                        ) : null}

                        {performance.dataQualityNotes.length > 0 ? (
                            <Space direction="vertical" size={2} style={{display: 'flex'}}>
                                {performance.dataQualityNotes.map((note) => (
                                    <Typography.Text key={note} type="secondary" style={{fontSize: 12}}>{note}</Typography.Text>
                                ))}
                            </Space>
                        ) : null}

                        <Card size="small" title="周期收益（日 / 周 / 月 / 年 / 累计）">
                            <NqDataTable<PaperPeriodReturn>
                                rowKey="key"
                                pagination={false}
                                dataSource={performance.periods}
                                columns={[
                                    {title: '周期', dataIndex: 'label', key: 'label', width: 90},
                                    {
                                        title: '收益',
                                        dataIndex: 'pnl',
                                        key: 'pnl',
                                        width: 160,
                                        render: (_value, row) => (row.available
                                            ? <NqAmountText value={row.pnl} signed colorBySign/>
                                            : <Typography.Text type="secondary">数据不足</Typography.Text>),
                                    },
                                    {
                                        title: '收益率',
                                        dataIndex: 'returnRatio',
                                        key: 'returnRatio',
                                        width: 120,
                                        render: (_value, row) => (row.available
                                            ? <NqPercentText value={row.returnRatio} ratio colorBySign/>
                                            : <Typography.Text type="secondary">数据不足</Typography.Text>),
                                    },
                                ]}
                            />
                        </Card>

                        <Card size="small" title="最近 equity snapshot">
                            {performance.recentSnapshots.length === 0 ? (
                                <NqEmptyState description="暂无 equity snapshot，运行产生权益快照后自动汇总。"/>
                            ) : (
                                <NqDataTable<EquityCurveSnapshotItem>
                                    rowKey="equitySnapshotId"
                                    pagination={false}
                                    dataSource={performance.recentSnapshots}
                                    scroll={{x: 720}}
                                    columns={[
                                        {title: '时间', dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170, render: (v: string) => formatDateTime(v)},
                                        nqNumericColumn({title: '总权益', dataIndex: 'totalEquity', key: 'totalEquity', width: 130, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: '现金', dataIndex: 'cashBalance', key: 'cashBalance', width: 130, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: '持仓市值', dataIndex: 'positionValue', key: 'positionValue', width: 130, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: '回撤', dataIndex: 'drawdown', key: 'drawdown', width: 100, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                                        {title: '来源', dataIndex: 'source', key: 'source', width: 100},
                                    ]}
                                />
                            )}
                        </Card>
                    </Space>
                )}
            </Space>
        </Card>
    );
}

// 严重程度到展示色调 / 横幅级别的映射；HEALTHY 单独走 success。
const DIAGNOSIS_SEVERITY_META: Record<PaperDiagnosisSeverity, {tone: NqStatusTone; level: 'info' | 'warning' | 'danger'}> = {
    INFO: {tone: 'info', level: 'info'},
    WARNING: {tone: 'warning', level: 'warning'},
    BLOCKING: {tone: 'danger', level: 'danger'},
};

function diagnosisBannerLevel(item: PaperRunDiagnosis): 'success' | 'info' | 'warning' | 'danger' {
    if (item.type === 'HEALTHY') {
        return 'success';
    }
    return DIAGNOSIS_SEVERITY_META[item.severity].level;
}

function PaperRunDiagnosisCard({diagnoses}: {diagnoses: PaperRunDiagnosis[]}) {
    return (
        <Card
            size="small"
            title="异常原因聚合"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={10} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    该诊断仅基于当前 Paper run 的查询结果，不代表真实交易能力，不触发 LIVE 或真实交易所。
                </Typography.Text>
                {diagnoses.map((item) => (
                    <NqRiskBanner
                        key={item.key}
                        level={diagnosisBannerLevel(item)}
                        message={(
                            <Space size={8} wrap>
                                <Tag>{item.type}</Tag>
                                <Typography.Text strong>{item.title}</Typography.Text>
                                <NqStatusTag status={item.severity} tone={DIAGNOSIS_SEVERITY_META[item.severity].tone}/>
                            </Space>
                        )}
                        description={(
                            <Space direction="vertical" size={2} style={{display: 'flex'}}>
                                <Typography.Text type="secondary" style={{fontSize: 12}}>{item.description}</Typography.Text>
                                <Typography.Text type="secondary" style={{fontSize: 12}}>建议检查：{item.checkTarget}</Typography.Text>
                            </Space>
                        )}
                    />
                ))}
            </Space>
        </Card>
    );
}

/** 组合看板的 run 引用文案：mono 截短 id + 状态标签，缺失时返回占位。 */
function renderRunRefTags(runs: PaperPortfolioRunRef[]): ReactNode {
    if (runs.length === 0) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>无</Typography.Text>;
    }
    return (
        <Space size={[6, 6]} wrap>
            {runs.slice(0, 12).map((run) => (
                <Tag key={run.paperRunId} className="nq-mono" style={{fontSize: 11}}>{run.paperRunId}</Tag>
            ))}
            {runs.length > 12 ? (
                <Typography.Text type="secondary" style={{fontSize: 12}}>等 {runs.length} 个</Typography.Text>
            ) : null}
        </Space>
    );
}

function portfolioGroupColumns(keyTitle: string): ColumnsType<PaperPortfolioGroup> {
    return [
        {title: keyTitle, dataIndex: 'key', key: 'key', width: 200, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run 数', dataIndex: 'runCount', key: 'runCount', width: 80}),
        nqNumericColumn({title: '当前权益', dataIndex: 'currentEquity', key: 'currentEquity', width: 130, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({title: '总 PnL', dataIndex: 'totalPnl', key: 'totalPnl', width: 130, render: (v) => <NqAmountText value={v as string | number | null} signed colorBySign/>}),
        nqNumericColumn({
            title: '累计收益率',
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: '最大回撤',
            dataIndex: 'worstDrawdown',
            key: 'worstDrawdown',
            width: 110,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '风控拦截', dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 90}),
        nqNumericColumn({title: '未处理告警', dataIndex: 'openAlertCount', key: 'openAlertCount', width: 100}),
        {title: '最近运行', dataIndex: 'lastRunTime', key: 'lastRunTime', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

/**
 * PaperPortfolioDashboard —— Paper 组合看板（GateJ 后产品化 Loop-13）。
 * 只读消费后端 /paper-trading/portfolio/summary 单请求聚合结果：组合总览、策略/发布排行、Run 排行与数据质量。
 * 仅代表 SIM/Paper 模拟运行表现，不代表 LIVE 或真实交易；数据不足时不伪造收益率。
 */
function PaperPortfolioDashboard({query}: {query: ReturnType<typeof usePaperPortfolioSummaryQuery>}) {
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    return (
      <section aria-label="Paper 组合看板">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 组合看板"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="info"
                    message="跨多个 Paper run 只读汇总组合表现。"
                    description="该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 组合看板加载失败"
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="暂无 Paper run，创建并运行后自动汇总组合表现。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，无法计算组合收益率</Typography.Text>
                    </Space>
                ) : (
                    <PaperPortfolioDashboardBody portfolio={portfolio}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperPortfolioDashboardBody({portfolio}: {portfolio: PaperPortfolioSummaryResponse}) {
    const {overview, strategyGroups, publishGroups, highlights, dataQuality} = portfolio;
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* 组合总览指标 */}
            <div className="nq-status-strip">
                <NqMetricCard label="Paper run 总数" value={String(overview.totalRuns)} footer={`可比 ${overview.returnEligibleRunCount} 个`}/>
                <NqMetricCard label="当前总资产" value={<NqAmountText value={overview.totalCurrentEquity}/>}/>
                <NqMetricCard
                    label="总 PnL"
                    value={<NqAmountText value={overview.totalPnl} signed colorBySign/>}
                    tone={pnlTone(toNullableNumber(overview.totalPnl))}
                />
                <NqMetricCard
                    label="累计收益率"
                    value={overview.totalReturn !== null
                        ? <NqPercentText value={overview.totalReturn} ratio colorBySign/>
                        : <Typography.Text type="secondary">数据不足</Typography.Text>}
                />
                <NqMetricCard
                    label="最大回撤"
                    value={overview.worstRunDrawdown !== null
                        ? <NqPercentText value={overview.worstRunDrawdown} ratio signed={false}/>
                        : '-'}
                    tone="warning"
                    footer="按单 run 最大回撤统计"
                />
                <NqMetricCard label="初始总资金" value={<NqAmountText value={overview.totalInitialEquity}/>}/>
                <NqMetricCard label="RUNNING 数量" value={String(overview.runningCount)}/>
                <NqMetricCard
                    label="风控拦截"
                    value={String(overview.riskBlockedRunCount)}
                    tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}
                />
                <NqMetricCard
                    label="未处理告警"
                    value={String(overview.openAlertCount)}
                    tone={overview.openAlertCount > 0 ? 'warning' : 'muted'}
                />
            </div>
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                状态分布：RUNNING {overview.runningCount} · STOPPED {overview.stoppedCount} · FAILED {overview.failedCount}
                {' '}· CANCELLED {overview.cancelledCount} · CREATED {overview.createdCount}
            </Typography.Text>

            {/* 策略 / 发布维度排行 */}
            <Card size="small" title="Strategy Version 收益排行">
                <NqDataTable<PaperPortfolioGroup>
                    rowKey="key"
                    pagination={false}
                    dataSource={strategyGroups}
                    columns={portfolioGroupColumns('策略版本')}
                    scroll={{x: 1020, y: 240}}
                    locale={{emptyText: '暂无可分组的策略版本数据。'}}
                />
            </Card>
            <Card size="small" title="Publish 收益排行">
                <NqDataTable<PaperPortfolioGroup>
                    rowKey="key"
                    pagination={false}
                    dataSource={publishGroups}
                    columns={portfolioGroupColumns('发布')}
                    scroll={{x: 1020, y: 240}}
                    locale={{emptyText: '暂无可分组的发布数据。'}}
                />
            </Card>

            {/* Run 排行 / 风险清单 */}
            <Card size="small" title="Run 排行 / 风险清单">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div className="nq-status-strip">
                        <NqMetricCard
                            label="收益最高"
                            value={highlights.topWinner ? <NqAmountText value={highlights.topWinner.totalPnl} signed colorBySign/> : '-'}
                            footer={highlights.topWinner ? highlights.topWinner.paperRunId : '暂无数据'}
                            tone={highlights.topWinner ? pnlTone(toNullableNumber(highlights.topWinner.totalPnl)) : 'muted'}
                        />
                        <NqMetricCard
                            label="回撤最大"
                            value={highlights.worstDrawdown && highlights.worstDrawdown.maxDrawdown !== null
                                ? <NqPercentText value={highlights.worstDrawdown.maxDrawdown} ratio signed={false}/>
                                : '-'}
                            footer={highlights.worstDrawdown ? highlights.worstDrawdown.paperRunId : '暂无数据'}
                            tone="warning"
                        />
                        <NqMetricCard
                            label="风险最高"
                            value={highlights.highestRisk
                                ? <NqStatusTag status={highlights.highestRisk.riskBlocked ? '风控拦截' : '告警'} tone={highlights.highestRisk.riskBlocked ? 'danger' : 'warning'}/>
                                : '-'}
                            footer={highlights.highestRisk ? highlights.highestRisk.paperRunId : '暂无数据'}
                        />
                        <NqMetricCard
                            label="最近活跃"
                            value={highlights.mostRecent
                                ? <span className="nq-num" style={{fontSize: 13}}>{formatDateTime(highlights.mostRecent.lastActivityAt)}</span>
                                : '-'}
                            footer={highlights.mostRecent ? highlights.mostRecent.paperRunId : '暂无数据'}
                        />
                    </div>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label="风控拦截 run">{renderRunRefTags(highlights.riskBlockedRuns)}</Descriptions.Item>
                        <Descriptions.Item label="无交易 run">{renderRunRefTags(highlights.noTradeRuns)}</Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 数据质量提示 */}
            <Card size="small" title="数据质量提示">
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        数据不足的 run 不参与组合收益率计算，避免伪造收益。
                    </Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`缺 equity（${dataQuality.missingEquityRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingEquityRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`数据不足（${dataQuality.dataInsufficientRuns.length}）`}>
                            {renderRunRefTags(dataQuality.dataInsufficientRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 backtest 来源（${dataQuality.missingBacktestSourceRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingBacktestSourceRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 publish 来源（${dataQuality.missingPublishSourceRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingPublishSourceRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>
        </Space>
    );
}

/**
 * 汇总组合看板里出现过的「风险相关 run 引用」（highlights + dataQuality 去重）。
 * 注意：组合 summary 不下发全量 run 清单，本池为风险相关子集（含 top/worst/highestRisk/mostRecent
 * 与无交易 / 风控拦截 / 数据质量清单），用于回撤排行与阈值分布派生；展示层会显式标注口径，避免误读为全量。
 */
function collectRiskRunPool(portfolio: PaperPortfolioSummaryResponse): PaperPortfolioRunRef[] {
    const {highlights, dataQuality} = portfolio;
    const byId = new Map<string, PaperPortfolioRunRef>();
    const push = (run: PaperPortfolioRunRef | null | undefined) => {
        if (run && !byId.has(run.paperRunId)) {
            byId.set(run.paperRunId, run);
        }
    };
    push(highlights.topWinner);
    push(highlights.worstDrawdown);
    push(highlights.highestRisk);
    push(highlights.mostRecent);
    highlights.noTradeRuns.forEach(push);
    highlights.riskBlockedRuns.forEach(push);
    dataQuality.missingEquityRuns.forEach(push);
    dataQuality.dataInsufficientRuns.forEach(push);
    dataQuality.missingBacktestSourceRuns.forEach(push);
    dataQuality.missingPublishSourceRuns.forEach(push);
    return Array.from(byId.values());
}

/** 单 run 最大回撤（比例值，<=0）落桶；null 视为数据不足，不参与回撤分桶（不伪造回撤）。 */
const RISK_DRAWDOWN_BUCKETS: ReadonlyArray<{key: string; match: (dd: number) => boolean}> = [
    {key: '0% ~ -5%', match: (dd) => dd > -0.05},
    {key: '-5% ~ -10%', match: (dd) => dd <= -0.05 && dd > -0.1},
    {key: '-10% ~ -20%', match: (dd) => dd <= -0.1 && dd > -0.2},
    {key: '< -20%', match: (dd) => dd <= -0.2},
];

/** 无交易 run 的可能原因（基于组合 summary 可得字段派生，不臆测策略内部行为）。 */
function deriveNoTradeCause(
    run: PaperPortfolioRunRef,
    dataInsufficientIds: Set<string>,
    missingEquityIds: Set<string>,
): {label: string; tone: NqStatusTone} {
    if (run.riskBlocked) {
        return {label: '风控拦截', tone: 'danger'};
    }
    if (run.status === 'FAILED' || run.status === 'CANCELLED') {
        return {label: '异常结束', tone: 'danger'};
    }
    if (run.status === 'CREATED') {
        return {label: '尚未启动', tone: 'neutral'};
    }
    if (dataInsufficientIds.has(run.paperRunId) || missingEquityIds.has(run.paperRunId)) {
        return {label: '数据不足', tone: 'warning'};
    }
    return {label: '策略未触发', tone: 'info'};
}

/**
 * 无交易 run 的执行进度细分（Loop-18）：基于后端 run 级 noOrder / orderNoFill 标记，
 * 区分「无订单」与「有订单无成交」；旧后端缺该标记时回退到「无成交」泛标签，不臆测。
 */
function deriveExecProgress(run: PaperPortfolioRunRef): {label: string; tone: NqStatusTone; hint: string} {
    if (run.orderNoFill) {
        return {label: '有订单无成交', tone: 'warning', hint: '撮合 / 价格条件未满足或流动性模拟不足'};
    }
    if (run.noOrder) {
        return {label: '无订单', tone: 'info', hint: '策略未触发 / 尚未启动 / 数据不足'};
    }
    // 旧后端无 order 拆分字段（noOrder/orderNoFill 均缺失）：泛标签兜底，不伪造拆分。
    return {label: '无成交', tone: 'neutral', hint: '需查看单 run 订单与成交明细'};
}

/** 风险 run 表通用列：run / 状态 / 策略版本+发布 / 当前权益 / 总 PnL / 最大回撤 / 未处理告警 / 最近活跃。 */
function riskRunColumns(): ColumnsType<PaperPortfolioRunRef> {
    return [
        {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <NqStatusTag status={v}/>},
        {
            title: '策略版本 / 发布',
            key: 'lineage',
            width: 200,
            render: (_: unknown, run: PaperPortfolioRunRef) => (
                <Space direction="vertical" size={0}>
                    <span className="nq-mono" style={{fontSize: 11}}>{run.strategyVersionId ?? '(未绑定策略版本)'}</span>
                    <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 11}}>{run.publishId || '(未知发布)'}</Typography.Text>
                </Space>
            ),
        },
        nqNumericColumn({title: '当前权益', dataIndex: 'currentEquity', key: 'currentEquity', width: 120, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({
            title: '总 PnL',
            dataIndex: 'totalPnl',
            key: 'totalPnl',
            width: 120,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqAmountText value={v as string | number} signed colorBySign/>),
        }),
        nqNumericColumn({
            title: '最大回撤',
            dataIndex: 'maxDrawdown',
            key: 'maxDrawdown',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '未处理告警', dataIndex: 'openAlertCount', key: 'openAlertCount', width: 100}),
        {title: '最近活跃', dataIndex: 'lastActivityAt', key: 'lastActivityAt', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

/** 组合曲线采样点表列：时间 / 组合权益 / 组合 PnL / 收益率 / 回撤 / 在册 run / 缺失 run。 */
function portfolioCurveColumns(): ColumnsType<PaperPortfolioCurvePoint> {
    return [
        {title: '时间', dataIndex: 'timestamp', key: 'timestamp', width: 170, render: (v: string) => formatDateTime(v)},
        nqNumericColumn({title: '组合权益', dataIndex: 'totalEquity', key: 'totalEquity', width: 130, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({
            title: '组合 PnL',
            dataIndex: 'totalPnl',
            key: 'totalPnl',
            width: 130,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqAmountText value={v as string | number} signed colorBySign/>),
        }),
        nqNumericColumn({
            title: '收益率',
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: '回撤',
            dataIndex: 'drawdown',
            key: 'drawdown',
            width: 110,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '在册 run', dataIndex: 'sourceRunCount', key: 'sourceRunCount', width: 90}),
        nqNumericColumn({title: '缺失 run', dataIndex: 'missingRunCount', key: 'missingRunCount', width: 90}),
    ];
}

/**
 * PortfolioEquityCurveCard —— 组合级 equity / drawdown 时间序列卡（Loop-15）。
 * 优先消费后端 portfolioCurve（真实组合时间序列：当前回撤 / 最大回撤 / 资金峰值 / 最新组合 equity + 采样点）；
 * 不可用（数据不足或旧版本响应缺字段）时回退提示，由上层「回撤分析」继续以单 run 最大回撤口径兜底。
 * 仅代表 SIM/Paper 模拟、简化组合资金合计曲线，不代表真实时间加权组合收益，也不代表 LIVE 或真实交易。
 */
function PortfolioEquityCurveCard({curve}: {curve: PaperPortfolioCurve | null | undefined}) {
    const points: PaperPortfolioCurvePoint[] = curve?.points ?? [];
    const hasCurve = Boolean(curve) && points.length > 0;

    return (
        <Card
            size="small"
            title="组合资金曲线与回撤"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                {hasCurve && curve ? (
                    <>
                        <div className="nq-status-strip">
                            <NqMetricCard label="最新组合 equity" value={<NqAmountText value={curve.latestEquity}/>}/>
                            <NqMetricCard label="资金峰值" value={<NqAmountText value={curve.peakEquity}/>}/>
                            <NqMetricCard
                                label="组合当前回撤"
                                value={curve.currentDrawdown !== null
                                    ? <NqPercentText value={curve.currentDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="warning"
                                footer="组合时间序列口径"
                            />
                            <NqMetricCard
                                label="组合最大回撤"
                                value={curve.maxDrawdown !== null
                                    ? <NqPercentText value={curve.maxDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="danger"
                            />
                            <NqMetricCard
                                label="可比 run"
                                value={String(curve.coverage.comparableRunCount)}
                                footer={`缺 equity ${curve.coverage.missingEquityRunCount} · 不完整点 ${curve.coverage.incompletePointCount}`}
                            />
                        </div>

                        {/* 组合资金曲线图（复用 Design System ECharts 主题；hover 见每点组合权益/PnL/收益率/在册·缺失 run） */}
                        <div>
                            <Typography.Text strong style={{fontSize: 13}}>组合资金曲线</Typography.Text>
                            <NqPortfolioEquityChart points={points}/>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                实线为组合资金合计，虚线为可比 run 初始资金合计基线；hover 查看每点组合权益 / PnL / 收益率 / 在册·缺失 run。
                            </Typography.Text>
                        </div>

                        {/* 组合回撤曲线图（y 轴反向、回撤向下；hover 见回撤/资金峰值/组合权益） */}
                        <div>
                            <Typography.Text strong style={{fontSize: 13}}>组合回撤曲线</Typography.Text>
                            <NqPortfolioDrawdownChart points={points}/>
                        </div>

                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            覆盖度：可比 run {curve.coverage.comparableRunCount} · 缺 equity {curve.coverage.missingEquityRunCount}
                            {' '}· 不完整点 {curve.coverage.incompletePointCount}（共 {curve.pointCount} 个采样点）。
                            每个时间点 sourceRunCount 为已在册 run 数，missingRunCount 为尚未起跑的可比 run 数。
                        </Typography.Text>
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            该组合资金曲线仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。
                            该曲线是组合资金合计曲线，不等同于严格时间加权收益。
                        </Typography.Text>

                        {/* 采样点表保留为可折叠辅助展示，保持数据透明度（默认折叠，避免与图表重复占屏） */}
                        <Collapse
                            size="small"
                            items={[{
                                key: 'curve-points',
                                label: `组合曲线采样点（共 ${curve.pointCount}，展开查看最近 ${Math.min(points.length, 12)} 条）`,
                                children: (
                                    <NqDataTable<PaperPortfolioCurvePoint>
                                        rowKey="timestamp"
                                        pagination={false}
                                        dataSource={[...points].slice(-12).reverse()}
                                        columns={portfolioCurveColumns()}
                                        scroll={{x: 900, y: 240}}
                                        locale={{emptyText: '暂无组合曲线采样点。'}}
                                    />
                                ),
                            }]}
                        />
                    </>
                ) : (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="组合 equity curve 暂不可用（数据不足或旧版本响应），回退按单 run 最大回撤统计口径展示。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，不展示组合时间序列回撤</Typography.Text>
                    </Space>
                )}
            </Space>
        </Card>
    );
}

/**
 * PaperRiskDrawdownDashboard —— Paper 风险与回撤驾驶舱（GateJ 后产品化 Loop-14）。
 * 复用 Loop-13 组合 summary 单请求结果，把「风险面」从组合看板中独立出来只读派生：
 * 风险总览、回撤分析（阈值分布 + 单 run 最大回撤排行）、风控与异常清单、无交易 / 数据不足清单、数据质量。
 * 仅代表 SIM/Paper 模拟运行，不读真实交易所账户余额，不代表 LIVE 或真实交易风险；数据不足不伪造回撤。
 */
function PaperRiskDrawdownDashboard({query}: {query: ReturnType<typeof usePaperPortfolioSummaryQuery>}) {
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    return (
      <section aria-label="Paper 风险与回撤驾驶舱">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 风险与回撤驾驶舱"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="warning"
                    message="聚焦组合内最高风险、最大回撤、风控拦截、无交易与数据不足的 Paper run。"
                    description="该风险看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易风险。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 风险与回撤驾驶舱加载失败"
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="暂无 Paper 风险数据，创建并运行 Paper run 后自动汇总风险与回撤。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，不展示回撤 / 风险数值</Typography.Text>
                    </Space>
                ) : (
                    <PaperRiskDrawdownBody portfolio={portfolio}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

// ---- Loop-19：风险 Run 清单筛选（合并 highlights/dataQuality 去重后按条件筛选），纯前端只读派生 ----

type RiskRunFilter =
    'all' | 'riskBlocked' | 'noOrder' | 'orderNoFill' | 'hasFill'
    | 'dataInsufficient' | 'terminal' | 'highDrawdown';

const RISK_RUN_FILTER_OPTIONS: ReadonlyArray<{label: string; value: RiskRunFilter}> = [
    {label: '全部', value: 'all'},
    {label: '风控拦截', value: 'riskBlocked'},
    {label: '无订单', value: 'noOrder'},
    {label: '有订单无成交', value: 'orderNoFill'},
    {label: '有成交', value: 'hasFill'},
    {label: '数据不足', value: 'dataInsufficient'},
    {label: '异常终态', value: 'terminal'},
    {label: '高回撤', value: 'highDrawdown'},
];

/** 高回撤阈值：单 run 最大回撤 ≤ -10%（与回撤分桶 danger 区间一致）。 */
const RISK_RUN_HIGH_DRAWDOWN_THRESHOLD = -0.1;

/**
 * 风险 Run 清单筛选：noOrder/orderNoFill/hasFill 按后端 run 级标记（旧后端缺失 → 不命中，不伪造）；
 * dataInsufficient 以 dataQuality.dataInsufficientRuns 为准；terminal 取 FAILED/CANCELLED；highDrawdown 取深回撤。
 */
function filterRiskRuns(
    pool: PaperPortfolioRunRef[],
    filter: RiskRunFilter,
    dataInsufficientIds: Set<string>,
): PaperPortfolioRunRef[] {
    switch (filter) {
        case 'riskBlocked': return pool.filter((r) => r.riskBlocked);
        case 'noOrder': return pool.filter((r) => r.noOrder === true);
        case 'orderNoFill': return pool.filter((r) => r.orderNoFill === true);
        case 'hasFill': return pool.filter((r) => r.hasFill === true);
        case 'dataInsufficient': return pool.filter((r) => dataInsufficientIds.has(r.paperRunId));
        case 'terminal': return pool.filter((r) => r.status === 'FAILED' || r.status === 'CANCELLED');
        case 'highDrawdown': return pool.filter((r) => {
            const dd = toNullableNumber(r.maxDrawdown);
            return dd !== null && dd <= RISK_RUN_HIGH_DRAWDOWN_THRESHOLD;
        });
        case 'all':
        default: return pool;
    }
}

/** Run 执行进度标记（通用，含有成交）：旧后端缺 order/fill 标记时回退「无成交」泛标签，不伪造。 */
function runExecTag(run: PaperPortfolioRunRef): {label: string; tone: NqStatusTone} {
    if (run.hasFill) {
        return {label: '有成交', tone: 'success'};
    }
    if (run.orderNoFill) {
        return {label: '有订单无成交', tone: 'warning'};
    }
    if (run.noOrder) {
        return {label: '无订单', tone: 'info'};
    }
    return {label: '无成交', tone: 'neutral'};
}

/**
 * 可点击筛选指标卡包装器（Loop-21 统一 affordance）。
 * - cursor pointer + 键盘可访问（Enter / Space）
 * - 激活态：2px primary outline，aria-pressed=true
 * - NqMetricCard 不支持 onClick，通过包装层实现，不修改共享组件
 */
function ClickableMetricCard({
    children,
    onClick,
    ariaLabel,
    testId,
    isActive,
}: {
    children: ReactNode;
    onClick: () => void;
    ariaLabel: string;
    testId: string;
    isActive: boolean;
}) {
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' || e.key === ' ') onClick();
    };
    return (
        <div
            role="button"
            tabIndex={0}
            aria-label={ariaLabel}
            aria-pressed={isActive}
            data-testid={testId}
            onClick={onClick}
            onKeyDown={handleKeyDown}
            style={{
                cursor: 'pointer',
                borderRadius: 'var(--nq-radius-lg)',
                outline: isActive
                    ? '2px solid var(--nq-color-primary)'
                    : '2px solid transparent',
                outlineOffset: '2px',
            }}
        >
            {children}
        </div>
    );
}

function PaperRiskDrawdownBody({portfolio}: {portfolio: PaperPortfolioSummaryResponse}) {
    const {overview, highlights, dataQuality} = portfolio;

    // Loop-19：风险 Run 清单筛选状态（默认全部）。
    const [riskFilter, setRiskFilter] = useState<RiskRunFilter>('all');

    const pool = collectRiskRunPool(portfolio);
    const dataInsufficientIds = new Set(dataQuality.dataInsufficientRuns.map((r) => r.paperRunId));
    const missingEquityIds = new Set(dataQuality.missingEquityRuns.map((r) => r.paperRunId));

    // 回撤排行：池内有最大回撤的 run 按最负优先排序；无回撤的 run 单列「数据不足」，不伪造回撤。
    const drawdownRanked = pool
        .filter((r) => toNullableNumber(r.maxDrawdown) !== null)
        .sort((a, b) => (toNullableNumber(a.maxDrawdown) ?? 0) - (toNullableNumber(b.maxDrawdown) ?? 0));
    const drawdownInsufficient = pool.filter((r) => toNullableNumber(r.maxDrawdown) === null);

    // 回撤阈值分布：仅对有回撤的 run 分桶；数据不足单独计数。
    const bucketCounts = RISK_DRAWDOWN_BUCKETS.map((bucket) => ({
        key: bucket.key,
        count: drawdownRanked.filter((r) => bucket.match(toNullableNumber(r.maxDrawdown) ?? 0)).length,
    }));

    // 异常 / 风险细分清单（均来自组合 summary 已下发的风险相关子集）。
    const openAlertRuns = pool.filter((r) => r.openAlertCount > 0);
    const failedCancelledRuns = pool.filter((r) => r.status === 'FAILED' || r.status === 'CANCELLED');
    const missingPnlRuns = pool.filter((r) => toNullableNumber(r.totalPnl) === null);

    // 高风险 run 数：风控拦截 + 异常终态（按 overview 权威计数合计）。
    const failedCancelledCount = overview.failedCount + overview.cancelledCount;

    // Loop-18：把「无交易」按后端精确口径拆为「无订单」与「有订单无成交」。
    // 旧后端缺该拆分字段时 footer 退化为提示「单 run 查看」，不伪造拆分计数。
    const hasOrderSplit = overview.noOrderRunCount !== undefined && overview.orderNoFillRunCount !== undefined;
    const noTradeSplitFooter = hasOrderSplit
        ? `无订单 ${overview.noOrderRunCount} · 有订单无成交 ${overview.orderNoFillRunCount}`
        : '无订单 / 有订单无成交需查看单 run';

    // Loop-19：统一风险 Run 清单（合并去重的 pool）按筛选条件展示。
    const riskRunFiltered = filterRiskRuns(pool, riskFilter, dataInsufficientIds);
    const riskFilterLabel = RISK_RUN_FILTER_OPTIONS.find((o) => o.value === riskFilter)?.label ?? '全部';
    // Loop-20：高回撤 run 数（用于 click-to-filter 指标卡，阈值与 filterRiskRuns 保持一致）。
    const highDrawdownCount = pool.filter((r) => {
        const dd = toNullableNumber(r.maxDrawdown);
        return dd !== null && dd <= RISK_RUN_HIGH_DRAWDOWN_THRESHOLD;
    }).length;

    /** 点击指标卡直接切换风险 Run 清单筛选（Loop-20 click-to-filter）。 */
    const handleRiskCardClick = (filter: RiskRunFilter) => setRiskFilter(filter);

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* 1) 风险总览指标（Loop-20：可点击卡片直接联动下方风险 Run 清单筛选） */}
            <div className="nq-status-strip">
                <NqMetricCard
                    label="最大单 run 回撤"
                    value={overview.worstRunDrawdown !== null
                        ? <NqPercentText value={overview.worstRunDrawdown} ratio signed={false}/>
                        : '-'}
                    tone="warning"
                    footer={highlights.worstDrawdown ? `当前最大回撤 run：${highlights.worstDrawdown.paperRunId}` : '按单 run 最大回撤统计'}
                />
                <ClickableMetricCard
                    ariaLabel="筛选风控拦截风险 Run"
                    testId="risk-filter-card-risk-blocked"
                    isActive={riskFilter === 'riskBlocked'}
                    onClick={() => handleRiskCardClick('riskBlocked')}
                >
                    <NqMetricCard
                        label="风控拦截 run"
                        value={String(overview.riskBlockedRunCount)}
                        tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}
                        footer="点击筛选"
                    />
                </ClickableMetricCard>
                <NqMetricCard
                    label="未处理告警"
                    value={String(overview.openAlertCount)}
                    tone={overview.openAlertCount > 0 ? 'warning' : 'muted'}
                />
                {hasOrderSplit ? (
                    <>
                        <ClickableMetricCard
                            ariaLabel="筛选无订单风险 Run"
                            testId="risk-filter-card-no-order"
                            isActive={riskFilter === 'noOrder'}
                            onClick={() => handleRiskCardClick('noOrder')}
                        >
                            <NqMetricCard
                                label="无订单"
                                value={String(overview.noOrderRunCount ?? 0)}
                                tone={(overview.noOrderRunCount ?? 0) > 0 ? 'warning' : 'muted'}
                                footer="点击筛选"
                            />
                        </ClickableMetricCard>
                        <ClickableMetricCard
                            ariaLabel="筛选有订单无成交风险 Run"
                            testId="risk-filter-card-order-no-fill"
                            isActive={riskFilter === 'orderNoFill'}
                            onClick={() => handleRiskCardClick('orderNoFill')}
                        >
                            <NqMetricCard
                                label="有订单无成交"
                                value={String(overview.orderNoFillRunCount ?? 0)}
                                tone={(overview.orderNoFillRunCount ?? 0) > 0 ? 'warning' : 'muted'}
                                footer="点击筛选"
                            />
                        </ClickableMetricCard>
                        <ClickableMetricCard
                            ariaLabel="筛选有成交风险 Run"
                            testId="risk-filter-card-has-fill"
                            isActive={riskFilter === 'hasFill'}
                            onClick={() => handleRiskCardClick('hasFill')}
                        >
                            <NqMetricCard
                                label="有成交"
                                value={String(overview.filledRunCount ?? '-')}
                                tone={(overview.filledRunCount ?? 0) > 0 ? 'success' : 'muted'}
                                footer="点击筛选"
                            />
                        </ClickableMetricCard>
                    </>
                ) : (
                    <NqMetricCard
                        label="无交易 run"
                        value={String(overview.noTradeRunCount)}
                        tone={overview.noTradeRunCount > 0 ? 'warning' : 'muted'}
                        footer={noTradeSplitFooter}
                    />
                )}
                <ClickableMetricCard
                    ariaLabel="筛选数据不足风险 Run"
                    testId="risk-filter-card-data-insufficient"
                    isActive={riskFilter === 'dataInsufficient'}
                    onClick={() => handleRiskCardClick('dataInsufficient')}
                >
                    <NqMetricCard
                        label="数据不足 run"
                        value={String(overview.dataInsufficientRunCount)}
                        tone={overview.dataInsufficientRunCount > 0 ? 'warning' : 'muted'}
                        footer="点击筛选"
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="筛选异常终态风险 Run"
                    testId="risk-filter-card-terminal"
                    isActive={riskFilter === 'terminal'}
                    onClick={() => handleRiskCardClick('terminal')}
                >
                    <NqMetricCard
                        label="FAILED / CANCELLED"
                        value={String(failedCancelledCount)}
                        tone={failedCancelledCount > 0 ? 'danger' : 'muted'}
                        footer={`FAILED ${overview.failedCount} · CANCELLED ${overview.cancelledCount}`}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="筛选高回撤风险 Run"
                    testId="risk-filter-card-high-drawdown"
                    isActive={riskFilter === 'highDrawdown'}
                    onClick={() => handleRiskCardClick('highDrawdown')}
                >
                    <NqMetricCard
                        label="高回撤 run"
                        value={String(highDrawdownCount)}
                        tone={highDrawdownCount > 0 ? 'danger' : 'muted'}
                        footer="回撤 ≤ -10%，点击筛选"
                    />
                </ClickableMetricCard>
            </div>

            {/* 1.5) 统一风险 Run 清单（Loop-19）：合并 highlights/dataQuality 去重，按条件筛选快速定位 */}
            <Card
                size="small"
                title={riskFilter !== 'all'
                    ? `风险 Run 清单 · 当前筛选：${riskFilterLabel}（${riskRunFiltered.length} 条）`
                    : '风险 Run 清单'}
                extra={riskFilter !== 'all' ? (
                    <Button size="small" type="link" onClick={() => setRiskFilter('all')}>查看全部</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label="风险 Run 筛选"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>风险筛选</Typography.Text>
                        <Select<RiskRunFilter>
                            size="small"
                            value={riskFilter}
                            onChange={setRiskFilter}
                            options={RISK_RUN_FILTER_OPTIONS as Array<{label: string; value: RiskRunFilter}>}
                            style={{width: 150}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            「{riskFilterLabel}」命中 {riskRunFiltered.length} 个 run
                        </Typography.Text>
                    </div>
                    {riskRunFiltered.length > 0 ? (
                        <div role="region" aria-label="风险 Run 清单表">
                            <NqDataTable<PaperPortfolioRunRef>
                                rowKey="paperRunId"
                                pagination={false}
                                dataSource={riskRunFiltered}
                                columns={[
                                    ...riskRunColumns(),
                                    {
                                        title: '执行进度',
                                        key: 'exec',
                                        width: 120,
                                        render: (_: unknown, run: PaperPortfolioRunRef) => {
                                            const t = runExecTag(run);
                                            return <NqStatusTag status={t.label} tone={t.tone}/>;
                                        },
                                    },
                                ]}
                                scroll={{x: 1220, y: 260}}
                                locale={{emptyText: '暂无匹配的风险 Run。'}}
                            />
                        </div>
                    ) : (
                        <NqEmptyState description={`当前筛选「${riskFilterLabel}」下暂无匹配的风险 Run。`}/>
                    )}
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        风险 Run 清单合并 highlights 与数据质量清单去重后按筛选条件展示。
                    </Typography.Text>
                </Space>
            </Card>

            {/* 2) 组合资金曲线与回撤（Loop-15：真实组合时间序列口径，不可用时回退单 run 口径） */}
            <PortfolioEquityCurveCard curve={portfolio.portfolioCurve}/>

            {/* 3) 回撤分析（单 run 最大回撤口径，与上方组合时间序列口径互补） */}
            <Card size="small" title="回撤分析">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div className="nq-status-strip">
                        {bucketCounts.map((bucket) => (
                            <NqMetricCard
                                key={bucket.key}
                                label={bucket.key}
                                value={String(bucket.count)}
                                tone={bucket.count > 0 && (bucket.key === '-10% ~ -20%' || bucket.key === '< -20%') ? 'danger' : 'default'}
                            />
                        ))}
                        <NqMetricCard
                            label="数据不足"
                            value={String(drawdownInsufficient.length)}
                            tone={drawdownInsufficient.length > 0 ? 'warning' : 'muted'}
                            footer="无 equity / 无法计算回撤"
                        />
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        回撤阈值分布与排行按单 run 最大回撤统计；组合层真实时间序列回撤见上方「组合资金曲线与回撤」。
                        单 run 口径与组合曲线口径互补，数据不足的 run 单列「数据不足」，不伪造回撤。
                    </Typography.Text>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={drawdownRanked}
                        columns={riskRunColumns()}
                        scroll={{x: 1100, y: 260}}
                        locale={{emptyText: '暂无可计算最大回撤的 Paper run。'}}
                    />
                    {drawdownInsufficient.length > 0 ? (
                        <Descriptions bordered size="small" column={1}>
                            <Descriptions.Item label={`数据不足（无回撤，${drawdownInsufficient.length}）`}>
                                {renderRunRefTags(drawdownInsufficient)}
                            </Descriptions.Item>
                        </Descriptions>
                    ) : null}
                </Space>
            </Card>

            {/* 3) 风控与异常清单 */}
            <Card size="small" title="风控与异常清单">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        风控拦截与未处理告警优先处理；FAILED / CANCELLED 为异常终态，需复盘运行原因。
                    </Typography.Text>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={highlights.riskBlockedRuns}
                        columns={riskRunColumns()}
                        scroll={{x: 1100, y: 220}}
                        locale={{emptyText: '暂无被风控拦截的 Paper run。'}}
                    />
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`未处理告警 run（${openAlertRuns.length}）`}>
                            {renderRunRefTags(openAlertRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`FAILED / CANCELLED run（共 ${failedCancelledCount}）`}>
                            {failedCancelledRuns.length > 0 ? renderRunRefTags(failedCancelledRuns) : (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                    {failedCancelledCount > 0 ? '异常终态 run 未在风险清单样本中，详见下方 Paper run 列表。' : '无'}
                                </Typography.Text>
                            )}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 4) 无交易 / 数据不足清单 */}
            <Card size="small" title="无交易 / 数据不足清单">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={highlights.noTradeRuns}
                        columns={[
                            {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
                            {title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <NqStatusTag status={v}/>},
                            {
                                title: '可能原因',
                                key: 'cause',
                                width: 120,
                                render: (_: unknown, run: PaperPortfolioRunRef) => {
                                    const cause = deriveNoTradeCause(run, dataInsufficientIds, missingEquityIds);
                                    return <NqStatusTag status={cause.label} tone={cause.tone}/>;
                                },
                            },
                            {
                                // Loop-18：执行进度细分（无订单 / 有订单无成交），基于后端 run 级标记，附原因提示。
                                title: '执行进度',
                                key: 'execProgress',
                                width: 160,
                                render: (_: unknown, run: PaperPortfolioRunRef) => {
                                    const prog = deriveExecProgress(run);
                                    return (
                                        <Space direction="vertical" size={0}>
                                            <NqStatusTag status={prog.label} tone={prog.tone}/>
                                            <Typography.Text type="secondary" style={{fontSize: 11}}>{prog.hint}</Typography.Text>
                                        </Space>
                                    );
                                },
                            },
                            {
                                title: '策略版本 / 发布',
                                key: 'lineage',
                                width: 200,
                                render: (_: unknown, run: PaperPortfolioRunRef) => (
                                    <Space direction="vertical" size={0}>
                                        <span className="nq-mono" style={{fontSize: 11}}>{run.strategyVersionId ?? '(未绑定策略版本)'}</span>
                                        <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 11}}>{run.publishId || '(未知发布)'}</Typography.Text>
                                    </Space>
                                ),
                            },
                            {title: '最近活跃', dataIndex: 'lastActivityAt', key: 'lastActivityAt', width: 170, render: (v: string | null) => formatDateTime(v)},
                        ]}
                        scroll={{x: 940, y: 220}}
                        locale={{emptyText: '暂无无交易的 Paper run。'}}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        无交易已按执行进度细分为「无订单」（策略未触发 / 尚未启动 / 数据不足）与「有订单无成交」（撮合 / 价格条件未满足或流动性模拟不足）；
                        旧后端响应缺该拆分字段时回退为「无成交」泛标签，详细仍可查看单 run。
                    </Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`数据不足 run（${dataQuality.dataInsufficientRuns.length}）`}>
                            {renderRunRefTags(dataQuality.dataInsufficientRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 5) 数据质量分析 */}
            <Card size="small" title="风险数据质量">
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        缺 equity / 初始资金 / PnL / 来源的 run 无法参与回撤与收益风险评估，已明确标注，不以缺省值伪造风险。
                    </Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`缺 equity snapshot（${dataQuality.missingEquityRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingEquityRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 PnL（${missingPnlRuns.length}）`}>
                            {renderRunRefTags(missingPnlRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 backtest 来源（${dataQuality.missingBacktestSourceRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingBacktestSourceRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 publish 来源（${dataQuality.missingPublishSourceRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingPublishSourceRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>
        </Space>
    );
}

/** 策略 / 发布维度排行行：组合 summary 的 group 字段 + 无交易 / 数据不足 / 异常终态计数与风险调整分。 */
interface PaperStrategyRankingRow {
    key: string;
    runCount: number;
    currentEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    worstDrawdown: string | number | null;
    riskBlockedCount: number;
    openAlertCount: number;
    lastRunTime: string | null;
    noTradeCount: number;
    dataInsufficientCount: number;
    failedCancelledCount: number;
    // Loop-18：把无交易拆为无订单 / 有订单无成交，并显式有成交。无订单 / 有订单无成交在旧后端无法拆分时为 null（显示「-」）；
    // 有成交可由 runCount - noTradeCount 稳定派生，恒为 number。
    noOrderCount: number | null;
    orderNoFillCount: number | null;
    filledRunCount: number;
    score: number | null;
}

/**
 * 风险调整排序分（Paper 内部排序分，非真实投资评级）：
 * score = totalReturn - |maxDrawdown| - riskBlockedCount*0.05 - openAlertCount*0.01 - dataInsufficientCount*0.02。
 * totalReturn 或 maxDrawdown 缺失时返回 null（展示「数据不足」，不伪造排序）。
 */
function riskAdjustedScore(group: PaperPortfolioGroup, dataInsufficientCount: number): number | null {
    const totalReturn = toNullableNumber(group.totalReturn);
    const maxDrawdown = toNullableNumber(group.worstDrawdown);
    if (totalReturn === null || maxDrawdown === null) {
        return null;
    }
    return totalReturn
        - Math.abs(maxDrawdown)
        - group.riskBlockedCount * 0.05
        - group.openAlertCount * 0.01
        - dataInsufficientCount * 0.02;
}

/** 按 strategyVersionId / publishId 维度对 run 引用计数（用于派生每组无交易 / 数据不足 run 数）。 */
function countRunsByKey(runs: PaperPortfolioRunRef[], keyOf: (run: PaperPortfolioRunRef) => string | null): Map<string, number> {
    const map = new Map<string, number>();
    runs.forEach((run) => {
        const key = keyOf(run);
        if (key) {
            map.set(key, (map.get(key) ?? 0) + 1);
        }
    });
    return map;
}

/**
 * 组装某一维度（strategy / publish）的排行行：
 * Loop-17 起优先用后端 group 精确计数（noTradeCount / dataInsufficientCount / failedCount / cancelledCount，
 * 基于完整 bounded runs）；旧后端缺字段时回退到 highlights / dataQuality 截断子集派生（异常终态无法派生时记 0）。
 * 按风险调整分降序（null 分末尾），同分按总 PnL 降序，得到稳定的「风险调整后排行」。
 */
function buildRankingRows(
    groups: PaperPortfolioGroup[],
    noTradeRuns: PaperPortfolioRunRef[],
    dataInsufficientRuns: PaperPortfolioRunRef[],
    keyOf: (run: PaperPortfolioRunRef) => string | null,
): PaperStrategyRankingRow[] {
    const noTradeByKey = countRunsByKey(noTradeRuns, keyOf);
    const dataInsufficientByKey = countRunsByKey(dataInsufficientRuns, keyOf);
    const rows = groups.map((group) => {
        // `?? 派生`：后端字段缺失（undefined）才回退；后端精确 0 会被尊重（0 非 nullish）。
        const noTradeCount = group.noTradeCount ?? (noTradeByKey.get(group.key) ?? 0);
        const dataInsufficientCount = group.dataInsufficientCount ?? (dataInsufficientByKey.get(group.key) ?? 0);
        const failedCancelledCount = (group.failedCount ?? 0) + (group.cancelledCount ?? 0);
        // Loop-18：无订单 / 有订单无成交需后端拆分字段，缺失则为 null（列显示「-」，不臆造拆分）；
        // 有成交可由 runCount - noTradeCount 稳定派生（noTradeCount == 无订单 + 有订单无成交）。
        const noOrderCount = group.noOrderCount ?? null;
        const orderNoFillCount = group.orderNoFillCount ?? null;
        const filledRunCount = group.filledRunCount ?? Math.max(0, group.runCount - noTradeCount);
        return {
            key: group.key,
            runCount: group.runCount,
            currentEquity: group.currentEquity,
            totalPnl: group.totalPnl,
            totalReturn: group.totalReturn,
            worstDrawdown: group.worstDrawdown,
            riskBlockedCount: group.riskBlockedCount,
            openAlertCount: group.openAlertCount,
            lastRunTime: group.lastRunTime,
            noTradeCount,
            dataInsufficientCount,
            failedCancelledCount,
            noOrderCount,
            orderNoFillCount,
            filledRunCount,
            score: riskAdjustedScore(group, dataInsufficientCount),
        };
    });
    return rows.sort((left, right) => {
        const leftScore = left.score === null ? Number.NEGATIVE_INFINITY : left.score;
        const rightScore = right.score === null ? Number.NEGATIVE_INFINITY : right.score;
        if (rightScore !== leftScore) {
            return rightScore - leftScore;
        }
        const leftPnl = toNullableNumber(left.totalPnl) ?? Number.NEGATIVE_INFINITY;
        const rightPnl = toNullableNumber(right.totalPnl) ?? Number.NEGATIVE_INFINITY;
        return rightPnl - leftPnl;
    });
}

/** 取某指标的极值行（preferMax=true 取最大，false 取最小）；指标为 null 的行跳过，避免伪造排名。 */
function topRankingRow(
    rows: PaperStrategyRankingRow[],
    valueOf: (row: PaperStrategyRankingRow) => number | null,
    preferMax = true,
): PaperStrategyRankingRow | null {
    let best: PaperStrategyRankingRow | null = null;
    let bestValue: number | null = null;
    for (const row of rows) {
        const value = valueOf(row);
        if (value === null) {
            continue;
        }
        if (bestValue === null || (preferMax ? value > bestValue : value < bestValue)) {
            best = row;
            bestValue = value;
        }
    }
    return best;
}

/** 风险调整分展示：null → 「数据不足」，否则带符号 4 位小数（明确为排序分，不是收益率）。 */
function renderScore(score: number | null): ReactNode {
    return score === null
        ? <Typography.Text type="secondary">数据不足</Typography.Text>
        : <span className="nq-num">{formatNqNumber(score, {precision: 4, signed: true})}</span>;
}

/** 排行表列：策略表与发布表均展示无交易 / 数据不足 / 异常终态（FAILED+CANCELLED）计数（Loop-17 精确口径）。 */
function rankingColumns(keyTitle: string): ColumnsType<PaperStrategyRankingRow> {
    return [
        {title: keyTitle, dataIndex: 'key', key: 'key', width: 190, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run 数', dataIndex: 'runCount', key: 'runCount', width: 76}),
        nqNumericColumn({title: '当前权益', dataIndex: 'currentEquity', key: 'currentEquity', width: 120, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({title: '总 PnL', dataIndex: 'totalPnl', key: 'totalPnl', width: 120, render: (v) => <NqAmountText value={v as string | number | null} signed colorBySign/>}),
        nqNumericColumn({
            title: '累计收益率',
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 104,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: '最大回撤',
            dataIndex: 'worstDrawdown',
            key: 'worstDrawdown',
            width: 104,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '风控拦截', dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 84}),
        nqNumericColumn({title: '告警', dataIndex: 'openAlertCount', key: 'openAlertCount', width: 72}),
        nqNumericColumn({title: '无交易', dataIndex: 'noTradeCount', key: 'noTradeCount', width: 76}),
        nqNumericColumn({title: '无订单', dataIndex: 'noOrderCount', key: 'noOrderCount', width: 76, render: (v) => (v === null || v === undefined ? '-' : String(v))}),
        nqNumericColumn({title: '有单无成交', dataIndex: 'orderNoFillCount', key: 'orderNoFillCount', width: 96, render: (v) => (v === null || v === undefined ? '-' : String(v))}),
        nqNumericColumn({title: '有成交', dataIndex: 'filledRunCount', key: 'filledRunCount', width: 76}),
        nqNumericColumn({title: '数据不足', dataIndex: 'dataInsufficientCount', key: 'dataInsufficientCount', width: 84}),
        nqNumericColumn({title: '异常终态', dataIndex: 'failedCancelledCount', key: 'failedCancelledCount', width: 84}),
        nqNumericColumn({title: '风险调整分', dataIndex: 'score', key: 'score', width: 110, render: (v) => renderScore(v as number | null)}),
        {title: '最近运行', dataIndex: 'lastRunTime', key: 'lastRunTime', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

// ---- Loop-19：排行控件（排序维度 / 方向 / 数据过滤），纯前端只读派生，不改后端契约 ----

type RankingSortDim =
    'score' | 'totalReturn' | 'totalPnl' | 'worstDrawdown' | 'riskBlocked'
    | 'noOrder' | 'orderNoFill' | 'dataInsufficient' | 'lastRun';
type RankingSortDir = 'desc' | 'asc';
type RankingFilter = 'all' | 'hasReturn' | 'dataInsufficient' | 'riskBlocked' | 'noOrder' | 'orderNoFill' | 'abnormalTerminal';

const RANKING_SORT_OPTIONS: ReadonlyArray<{label: string; value: RankingSortDim}> = [
    {label: '风险调整分', value: 'score'},
    {label: '累计收益率', value: 'totalReturn'},
    {label: '总 PnL', value: 'totalPnl'},
    {label: '最大回撤', value: 'worstDrawdown'},
    {label: '风控拦截', value: 'riskBlocked'},
    {label: '无订单', value: 'noOrder'},
    {label: '有单无成交', value: 'orderNoFill'},
    {label: '数据不足', value: 'dataInsufficient'},
    {label: '最近运行', value: 'lastRun'},
];

const RANKING_FILTER_OPTIONS: ReadonlyArray<{label: string; value: RankingFilter}> = [
    {label: '全部', value: 'all'},
    {label: '仅有收益率', value: 'hasReturn'},
    {label: '仅数据不足', value: 'dataInsufficient'},
    {label: '仅有风控拦截', value: 'riskBlocked'},
    {label: '仅无订单', value: 'noOrder'},
    {label: '仅有单无成交', value: 'orderNoFill'},
    {label: '仅异常终态', value: 'abnormalTerminal'},
];

/**
 * 排序值提取：统一约定「值越大越靠前（降序）」。最大回撤取绝对值（回撤越深值越大），
 * 收益率 / PnL / 风险调整分按原值；无订单 / 有单无成交在旧后端为 null（排末尾，不伪造顺序）；
 * 最近运行取时间戳毫秒。返回 null 表示该维度不可比，恒排末尾。
 */
function rankingSortValue(row: PaperStrategyRankingRow, dim: RankingSortDim): number | null {
    switch (dim) {
        case 'score': return row.score;
        case 'totalReturn': return toNullableNumber(row.totalReturn);
        case 'totalPnl': return toNullableNumber(row.totalPnl);
        case 'worstDrawdown': {
            const dd = toNullableNumber(row.worstDrawdown);
            return dd === null ? null : Math.abs(dd);
        }
        case 'riskBlocked': return row.riskBlockedCount;
        case 'noOrder': return row.noOrderCount;
        case 'orderNoFill': return row.orderNoFillCount;
        case 'dataInsufficient': return row.dataInsufficientCount;
        case 'lastRun': {
            if (!row.lastRunTime) {
                return null;
            }
            const ms = Date.parse(row.lastRunTime);
            return Number.isNaN(ms) ? null : ms;
        }
        default: return null;
    }
}

/** 按维度 + 方向排序；null 值的行恒排末尾（与方向无关，不伪造排名），同值按风险调整分降序兜底。 */
function sortRankingRows(rows: PaperStrategyRankingRow[], dim: RankingSortDim, dir: RankingSortDir): PaperStrategyRankingRow[] {
    const factor = dir === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
        const av = rankingSortValue(a, dim);
        const bv = rankingSortValue(b, dim);
        if (av === null && bv === null) {
            return 0;
        }
        if (av === null) {
            return 1;
        }
        if (bv === null) {
            return -1;
        }
        if (av !== bv) {
            return (av - bv) * factor;
        }
        const as = a.score ?? Number.NEGATIVE_INFINITY;
        const bs = b.score ?? Number.NEGATIVE_INFINITY;
        return bs - as;
    });
}

/** 数据过滤：null/缺失字段按「不满足」处理（旧后端无订单/有单无成交计数为 null → 不计入，不伪造）。 */
function filterRankingRows(rows: PaperStrategyRankingRow[], filter: RankingFilter): PaperStrategyRankingRow[] {
    switch (filter) {
        case 'hasReturn': return rows.filter((r) => toNullableNumber(r.totalReturn) !== null);
        case 'dataInsufficient': return rows.filter((r) => r.dataInsufficientCount > 0);
        case 'riskBlocked': return rows.filter((r) => r.riskBlockedCount > 0);
        case 'noOrder': return rows.filter((r) => (r.noOrderCount ?? 0) > 0);
        case 'orderNoFill': return rows.filter((r) => (r.orderNoFillCount ?? 0) > 0);
        case 'abnormalTerminal': return rows.filter((r) => r.failedCancelledCount > 0);
        case 'all':
        default: return rows;
    }
}

// ---- GateK K2：Paper 执行诊断展示映射与筛选（消费 K1 endpoint，纯前端只读展示）----

/** cause 中文展示名（保留枚举值用于筛选与审计，展示层映射为业务可读名）。 */
const EXECUTION_CAUSE_LABEL: Record<PaperExecutionCause, string> = {
    NO_ORDER: '无订单',
    ORDER_NO_FILL: '有订单无成交',
    FILLED_LOSS: '成交亏损',
    RISK_BLOCKED: '风控拦截',
    DATA_INSUFFICIENT: '数据不足',
    HIGH_DRAWDOWN: '高回撤',
    FAILED_RUN: '异常终态',
    RUNNING_NO_RESULT: '运行未出结果',
    HEALTHY: '健康',
    UNKNOWN: '未归因',
};

const EXECUTION_CAUSE_TONE: Record<PaperExecutionCause, NqStatusTone> = {
    NO_ORDER: 'warning',
    ORDER_NO_FILL: 'warning',
    FILLED_LOSS: 'warning',
    RISK_BLOCKED: 'danger',
    DATA_INSUFFICIENT: 'warning',
    HIGH_DRAWDOWN: 'danger',
    FAILED_RUN: 'danger',
    RUNNING_NO_RESULT: 'info',
    HEALTHY: 'success',
    UNKNOWN: 'neutral',
};

const EXECUTION_SEVERITY_TONE: Record<PaperExecutionSeverity, NqStatusTone> = {
    INFO: 'neutral',
    WARNING: 'warning',
    CRITICAL: 'danger',
};

const EXECUTION_CONFIDENCE_TONE: Record<PaperExecutionCauseConfidence, NqStatusTone> = {
    HIGH: 'success',
    MEDIUM: 'info',
    LOW: 'neutral',
};

type ExecutionCauseFilter = 'all' | PaperExecutionCause;
type ExecutionSeverityFilter = 'all' | PaperExecutionSeverity;

/** 诊断 cause 筛选项（全部 + 各归因；顺序与后端 primaryCause 优先级一致，最紧急在前）。 */
const EXECUTION_CAUSE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: ExecutionCauseFilter}> = [
    {label: '全部原因', value: 'all'},
    {label: '异常终态 FAILED_RUN', value: 'FAILED_RUN'},
    {label: '数据不足 DATA_INSUFFICIENT', value: 'DATA_INSUFFICIENT'},
    {label: '风控拦截 RISK_BLOCKED', value: 'RISK_BLOCKED'},
    {label: '有订单无成交 ORDER_NO_FILL', value: 'ORDER_NO_FILL'},
    {label: '无订单 NO_ORDER', value: 'NO_ORDER'},
    {label: '成交亏损 FILLED_LOSS', value: 'FILLED_LOSS'},
    {label: '高回撤 HIGH_DRAWDOWN', value: 'HIGH_DRAWDOWN'},
    {label: '运行未出结果 RUNNING_NO_RESULT', value: 'RUNNING_NO_RESULT'},
    {label: '健康 HEALTHY', value: 'HEALTHY'},
];

const EXECUTION_SEVERITY_FILTER_OPTIONS: ReadonlyArray<{label: string; value: ExecutionSeverityFilter}> = [
    {label: '全部严重度', value: 'all'},
    {label: 'CRITICAL', value: 'CRITICAL'},
    {label: 'WARNING', value: 'WARNING'},
    {label: 'INFO', value: 'INFO'},
];

/** cause 标签（中文名 + 语义色），缺省回退原始枚举值，不伪造。 */
function executionCauseTag(cause: PaperExecutionCause) {
    return <NqStatusTag status={EXECUTION_CAUSE_LABEL[cause] ?? cause} tone={EXECUTION_CAUSE_TONE[cause] ?? 'neutral'}/>;
}

/**
 * PaperExecutionDiagnosticsDashboard —— Paper 执行诊断（GateK Batch K2）。
 * 消费 K1 只读 endpoint /paper-trading/execution-diagnostics，把规则化归因（cause / severity / confidence /
 * explanation / suggestedAction）展示出来，让用户从「事实筛选」升级为「原因诊断」。
 * 独立 query：加载 / 错误 / 空 / 兼容回退均限定在本区域，不连累组合看板、风险驾驶舱与策略排行。
 * 仅 Paper-only 规则化归因，不是 AI 投资建议，也不构成真实交易建议。
 */
function PaperExecutionDiagnosticsDashboard({query}: {query: ReturnType<typeof usePaperExecutionDiagnosticsQuery>}) {
    const raw = query.data;
    const diagnostics: PaperExecutionDiagnosticsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperExecutionDiagnosticsResponse).overview
            ? (raw as PaperExecutionDiagnosticsResponse)
            : null;

    return (
      <section aria-label="Paper 执行诊断">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 执行诊断"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · Rules-based diagnostics</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    基于 Paper 执行事实的规则化归因，不代表 LIVE 或真实交易建议。
                </Typography.Text>
                <NqRiskBanner
                    level="info"
                    message="对每个 Paper run 做规则化执行归因：为什么无订单 / 有订单无成交 / 成交亏损 / 风控拦截 / 数据不足。"
                    description="该诊断仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。诊断结果为规则化归因，不是 AI 投资建议，也不构成真实交易建议。"
                />
                {query.error ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqErrorState
                            title="Paper 执行诊断加载失败"
                            error={query.error as AppApiError}
                            description="执行诊断不可用（旧后端可能尚未提供该接口）；其余 Paper 模块不受影响。"
                            onRetry={() => query.refetch()}
                        />
                    </Space>
                ) : query.isFetching && !diagnostics ? (
                    <NqLoadingState message="加载 Paper 执行诊断中..."/>
                ) : !diagnostics ? (
                    <NqEmptyState description="暂无 Paper 执行诊断数据（接口未返回诊断结构）。"/>
                ) : diagnostics.overview.totalRuns === 0 ? (
                    <NqEmptyState description="暂无 Paper 执行诊断数据，创建并运行 Paper run 后自动生成执行归因。"/>
                ) : (
                    <PaperExecutionDiagnosticsBody diagnostics={diagnostics}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperExecutionDiagnosticsBody({diagnostics}: {diagnostics: PaperExecutionDiagnosticsResponse}) {
    const {overview, causeDistribution, runDiagnostics, strategyDiagnostics, publishDiagnostics} = diagnostics;

    const [causeFilter, setCauseFilter] = useState<ExecutionCauseFilter>('all');
    const [severityFilter, setSeverityFilter] = useState<ExecutionSeverityFilter>('all');

    // 筛选只作用于 Run Diagnostics 表（cause 按 primaryCause；severity 按 run severity）；分组表保持完整。
    const filteredRuns = runDiagnostics.filter((r) =>
        (causeFilter === 'all' || r.primaryCause === causeFilter)
        && (severityFilter === 'all' || r.severity === severityFilter));
    const filtered = causeFilter !== 'all' || severityFilter !== 'all';

    const runColumns: ColumnsType<PaperExecutionRunDiagnostic> = [
        {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 150, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
        {title: '主因', key: 'primaryCause', width: 120, render: (_: unknown, r) => executionCauseTag(r.primaryCause)},
        {
            title: '辅助原因', key: 'secondaryCauses', width: 200,
            render: (_: unknown, r) => r.secondaryCauses.length > 0
                ? <Space size={4} wrap>{r.secondaryCauses.map((c) => <span key={c}>{executionCauseTag(c)}</span>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '严重度', key: 'severity', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.severity} tone={EXECUTION_SEVERITY_TONE[r.severity]}/>},
        {title: '可信度', key: 'causeConfidence', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.causeConfidence} tone={EXECUTION_CONFIDENCE_TONE[r.causeConfidence]}/>},
        nqNumericColumn({title: '订单', dataIndex: 'orderCount', key: 'orderCount', width: 70}),
        nqNumericColumn({title: '成交', dataIndex: 'tradeCount', key: 'tradeCount', width: 70}),
        nqNumericColumn({
            title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperExecutionRunDiagnostic) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-',
        }),
        nqNumericColumn({
            title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperExecutionRunDiagnostic) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-',
        }),
        {
            title: '诊断说明 / 建议', key: 'explanation', width: 340,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text style={{fontSize: 12}}>{r.explanation}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>建议：{r.suggestedAction}</Typography.Text>
                </Space>
            ),
        },
    ];

    const groupColumns = (dimensionTitle: string): ColumnsType<PaperExecutionGroupDiagnostic> => [
        {title: dimensionTitle, dataIndex: 'key', key: 'key', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run 数', dataIndex: 'runCount', key: 'runCount', width: 80}),
        {title: '主因', key: 'primaryCause', width: 120, render: (_: unknown, g) => executionCauseTag(g.primaryCause)},
        {
            title: 'Top 原因', key: 'topCauses', width: 220,
            render: (_: unknown, g) => g.topCauses.length > 0
                ? <Space size={4} wrap>{g.topCauses.map((c) => <span key={c}>{executionCauseTag(c)}</span>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '严重度', key: 'severity', width: 100, render: (_: unknown, g) => <NqStatusTag status={g.severity} tone={EXECUTION_SEVERITY_TONE[g.severity]}/>},
        {title: '可信度', key: 'causeConfidence', width: 100, render: (_: unknown, g) => <NqStatusTag status={g.causeConfidence} tone={EXECUTION_CONFIDENCE_TONE[g.causeConfidence]}/>},
        nqNumericColumn({title: '无订单', dataIndex: 'noOrderCount', key: 'noOrderCount', width: 80}),
        nqNumericColumn({title: '有单无成交', dataIndex: 'orderNoFillCount', key: 'orderNoFillCount', width: 100}),
        nqNumericColumn({title: '成交亏损', dataIndex: 'filledLossCount', key: 'filledLossCount', width: 90}),
        nqNumericColumn({title: '风控拦截', dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 90}),
        nqNumericColumn({title: '数据不足', dataIndex: 'dataInsufficientCount', key: 'dataInsufficientCount', width: 90}),
        nqNumericColumn({title: '高回撤', dataIndex: 'highDrawdownCount', key: 'highDrawdownCount', width: 80}),
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 诊断总览（按事实独立计数，桶可重叠） */}
            <div className="nq-status-strip">
                <NqMetricCard label="纳入诊断 run" value={String(overview.totalRuns)} footer="bounded Paper run"/>
                <NqMetricCard label="无订单" value={String(overview.noOrderRunCount)} tone={overview.noOrderRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="有订单无成交" value={String(overview.orderNoFillRunCount)} tone={overview.orderNoFillRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="成交亏损" value={String(overview.filledLossRunCount)} tone={overview.filledLossRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="风控拦截" value={String(overview.riskBlockedRunCount)} tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="数据不足" value={String(overview.dataInsufficientRunCount)} tone={overview.dataInsufficientRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="高回撤" value={String(overview.highDrawdownRunCount)} tone={overview.highDrawdownRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="异常终态" value={String(overview.failedRunCount)} tone={overview.failedRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="运行中" value={String(overview.runningRunCount)} tone={overview.runningRunCount > 0 ? 'success' : 'muted'}/>
            </div>

            {/* B) Cause Distribution（主因分布） */}
            <Card size="small" title="主因分布（按 run primaryCause 聚合）">
                <div role="region" aria-label="Paper 执行诊断主因分布表">
                    <NqDataTable<PaperExecutionDiagnosticCauseDistribution>
                        rowKey="cause"
                        pagination={false}
                        dataSource={causeDistribution}
                        columns={[
                            {title: '原因', key: 'cause', width: 140, render: (_: unknown, d) => executionCauseTag(d.cause)},
                            nqNumericColumn({title: 'Run 数', dataIndex: 'count', key: 'count', width: 90}),
                            {title: '严重度', key: 'severity', width: 110, render: (_: unknown, d) => <NqStatusTag status={d.severity} tone={EXECUTION_SEVERITY_TONE[d.severity]}/>},
                            {title: '代表可信度', key: 'confidence', width: 120, render: (_: unknown, d) => <NqStatusTag status={d.confidence} tone={EXECUTION_CONFIDENCE_TONE[d.confidence]}/>},
                            {title: '说明', dataIndex: 'description', key: 'description', render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
                        ]}
                        scroll={{x: 720}}
                        locale={{emptyText: '暂无主因分布。'}}
                    />
                </div>
            </Card>

            {/* C) Run Diagnostics（单 run 诊断，受 cause / severity 筛选） */}
            <Card
                size="small"
                title={filtered
                    ? `Run 执行诊断 · 当前筛选命中 ${filteredRuns.length} 条`
                    : 'Run 执行诊断'}
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setCauseFilter('all'); setSeverityFilter('all');}}>查看全部</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label="Paper 执行诊断筛选"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>原因筛选</Typography.Text>
                        <Select<ExecutionCauseFilter>
                            size="small"
                            value={causeFilter}
                            onChange={setCauseFilter}
                            options={EXECUTION_CAUSE_FILTER_OPTIONS as Array<{label: string; value: ExecutionCauseFilter}>}
                            style={{width: 230}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>严重度筛选</Typography.Text>
                        <Select<ExecutionSeverityFilter>
                            size="small"
                            value={severityFilter}
                            onChange={setSeverityFilter}
                            options={EXECUTION_SEVERITY_FILTER_OPTIONS as Array<{label: string; value: ExecutionSeverityFilter}>}
                            style={{width: 150}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>命中 {filteredRuns.length} / {runDiagnostics.length} 个 run</Typography.Text>
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        confidence 表示该诊断原因由事实直接判断或推断得到，不代表真实交易结论。HIGH=明确事实，MEDIUM=推断归因，LOW=信息不足。
                    </Typography.Text>
                    <div role="region" aria-label="Paper 执行诊断 Run 表">
                        <NqDataTable<PaperExecutionRunDiagnostic>
                            rowKey="paperRunId"
                            pagination={false}
                            dataSource={filteredRuns}
                            columns={runColumns}
                            scroll={{x: 1560, y: 320}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的 Run 诊断。'}}
                        />
                    </div>
                </Space>
            </Card>

            {/* D) Strategy Diagnostics（strategyVersionId 维度聚合） */}
            <Card size="small" title="Strategy Version 执行诊断聚合">
                <div role="region" aria-label="Paper 执行诊断 Strategy 表">
                    <NqDataTable<PaperExecutionGroupDiagnostic>
                        rowKey="key"
                        pagination={false}
                        dataSource={strategyDiagnostics}
                        columns={groupColumns('策略版本')}
                        scroll={{x: 1360, y: 280}}
                        locale={{emptyText: '暂无可聚合的策略版本执行诊断。'}}
                    />
                </div>
            </Card>

            {/* E) Publish Diagnostics（publishId 维度聚合） */}
            <Card size="small" title="Publish 执行诊断聚合">
                <div role="region" aria-label="Paper 执行诊断 Publish 表">
                    <NqDataTable<PaperExecutionGroupDiagnostic>
                        rowKey="key"
                        pagination={false}
                        dataSource={publishDiagnostics}
                        columns={groupColumns('发布')}
                        scroll={{x: 1360, y: 280}}
                        locale={{emptyText: '暂无可聚合的发布执行诊断。'}}
                    />
                </div>
            </Card>

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                诊断总览按事实独立计数（一个 run 可同时计入多个桶）；主因 / 辅助原因按规则优先级归因。仅 Paper 模拟口径，不构成真实投资建议。
            </Typography.Text>
        </Space>
    );
}

// ---- GateK K3B：Paper 策略评估展示映射、筛选与排序（消费 K3 endpoint，纯前端只读展示）----

const RATING_LABEL_TEXT: Record<PaperStrategyRatingLabel, string> = {
    STRONG_PAPER_PERFORMER: '稳健表现',
    WATCHLIST: '观察',
    HIGH_RISK: '高风险',
    SAMPLE_INSUFFICIENT: '样本不足',
    DATA_INSUFFICIENT: '数据不足',
    EXECUTION_PROBLEM: '执行问题',
    UNKNOWN: '未知',
};

const RATING_LABEL_TONE: Record<PaperStrategyRatingLabel, NqStatusTone> = {
    STRONG_PAPER_PERFORMER: 'success',
    WATCHLIST: 'info',
    HIGH_RISK: 'danger',
    SAMPLE_INSUFFICIENT: 'warning',
    DATA_INSUFFICIENT: 'warning',
    EXECUTION_PROBLEM: 'warning',
    UNKNOWN: 'neutral',
};

const EVAL_CONFIDENCE_TONE: Record<PaperStrategyEvaluationConfidence, NqStatusTone> = {
    HIGH: 'success',
    MEDIUM: 'info',
    LOW: 'neutral',
};

const DEVIATION_LEVEL_TONE: Record<PaperBacktestDeviationLevel, NqStatusTone> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    UNAVAILABLE: 'neutral',
};

type EvalRatingFilter = 'all' | PaperStrategyRatingLabel;
type EvalConfidenceFilter = 'all' | PaperStrategyEvaluationConfidence;
type EvalDeviationFilter = 'all' | PaperBacktestDeviationLevel;
type EvalSortDim =
    'compositeScore' | 'totalReturn' | 'maxDrawdown' | 'winRate'
    | 'sampleScore' | 'riskScore' | 'executionScore' | 'backtestDeviationScore' | 'latestRunTime';
type EvalSortDir = 'desc' | 'asc';

const EVAL_RATING_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalRatingFilter}> = [
    {label: '全部评级', value: 'all'},
    {label: '稳健表现 STRONG_PAPER_PERFORMER', value: 'STRONG_PAPER_PERFORMER'},
    {label: '观察 WATCHLIST', value: 'WATCHLIST'},
    {label: '高风险 HIGH_RISK', value: 'HIGH_RISK'},
    {label: '样本不足 SAMPLE_INSUFFICIENT', value: 'SAMPLE_INSUFFICIENT'},
    {label: '数据不足 DATA_INSUFFICIENT', value: 'DATA_INSUFFICIENT'},
    {label: '执行问题 EXECUTION_PROBLEM', value: 'EXECUTION_PROBLEM'},
    {label: '未知 UNKNOWN', value: 'UNKNOWN'},
];

const EVAL_CONFIDENCE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalConfidenceFilter}> = [
    {label: '全部可信度', value: 'all'},
    {label: 'HIGH', value: 'HIGH'},
    {label: 'MEDIUM', value: 'MEDIUM'},
    {label: 'LOW', value: 'LOW'},
];

const EVAL_DEVIATION_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalDeviationFilter}> = [
    {label: '全部偏差', value: 'all'},
    {label: 'LOW', value: 'LOW'},
    {label: 'MEDIUM', value: 'MEDIUM'},
    {label: 'HIGH', value: 'HIGH'},
    {label: 'UNAVAILABLE', value: 'UNAVAILABLE'},
];

const EVAL_SORT_OPTIONS: ReadonlyArray<{label: string; value: EvalSortDim}> = [
    {label: '综合分', value: 'compositeScore'},
    {label: '收益率', value: 'totalReturn'},
    {label: '最大回撤', value: 'maxDrawdown'},
    {label: '胜率', value: 'winRate'},
    {label: '样本分', value: 'sampleScore'},
    {label: '风险分', value: 'riskScore'},
    {label: '执行分', value: 'executionScore'},
    {label: 'Backtest 偏差分', value: 'backtestDeviationScore'},
    {label: '最近运行', value: 'latestRunTime'},
];

function ratingTag(rating: PaperStrategyRatingLabel) {
    return <NqStatusTag status={RATING_LABEL_TEXT[rating] ?? rating} tone={RATING_LABEL_TONE[rating] ?? 'neutral'}/>;
}

/** 取评估行某排序维度的数值；不可比 / 缺失返回 null（恒排末尾，不伪造）。 */
function evalSortValue(row: PaperStrategyEvaluationItem, dim: EvalSortDim): number | null {
    switch (dim) {
        case 'compositeScore': return row.compositeScore;
        case 'sampleScore': return row.sampleScore;
        case 'riskScore': return row.riskScore;
        case 'executionScore': return row.executionScore;
        case 'backtestDeviationScore': return row.backtestDeviationScore;
        case 'totalReturn': return toNullableNumber(row.totalReturn);
        case 'maxDrawdown': return toNullableNumber(row.maxDrawdown);
        case 'winRate': return toNullableNumber(row.winRate);
        case 'latestRunTime': return row.latestRunTime ? Date.parse(row.latestRunTime) : null;
        default: return null;
    }
}

/** 排序：非空按方向排序，null 恒排末尾。 */
function sortStrategyEvals(rows: PaperStrategyEvaluationItem[], dim: EvalSortDim, dir: EvalSortDir): PaperStrategyEvaluationItem[] {
    const decorated = rows.map((r) => ({r, v: evalSortValue(r, dim)}));
    const nonNull = decorated.filter((x) => x.v !== null) as Array<{r: PaperStrategyEvaluationItem; v: number}>;
    const nulls = decorated.filter((x) => x.v === null);
    nonNull.sort((a, b) => (dir === 'desc' ? b.v - a.v : a.v - b.v));
    return [...nonNull.map((x) => x.r), ...nulls.map((x) => x.r)];
}

/** 分数单元：可空分数（如 backtestDeviationScore）缺失时显示「数据不足」，不伪造 0。 */
function scoreCell(score: number | null) {
    if (score === null || score === undefined) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>数据不足</Typography.Text>;
    }
    return <span className="nq-num">{score}</span>;
}

/**
 * PaperStrategyEvaluationDashboard —— Paper 策略评估（GateK Batch K3B）。
 * 消费 K3 只读 endpoint /paper-trading/strategy-evaluations，把 strategy / publish 评分、ratingLabel、warnings、
 * Paper-vs-Backtest 偏差、compositeScore 展示出来，让用户从「策略排行」升级为「策略评估」。
 * 独立 query：加载 / 错误 / 空 / 兼容回退均限定本区域，不连累其他模块。评分为 Paper 内部启发式分、非真实投资评级、不构成投资建议。
 */
function PaperStrategyEvaluationDashboard({query}: {query: ReturnType<typeof usePaperStrategyEvaluationsQuery>}) {
    const raw = query.data;
    const evaluation: PaperStrategyEvaluationsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperStrategyEvaluationsResponse).overview
            ? (raw as PaperStrategyEvaluationsResponse)
            : null;

    return (
      <section aria-label="Paper 策略评估">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 策略评估"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · Internal evaluation</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    基于 Paper 表现、执行质量、样本充足性与 Backtest 偏差的内部评估。
                </Typography.Text>
                <NqRiskBanner
                    level="info"
                    message="从 strategyVersionId / publishId 维度评估 Paper 模拟表现、Paper vs Backtest 偏差、样本充足性与风险调整评分。"
                    description="该评分是 Paper 内部启发式评估，不是真实投资评级。该结果不代表 LIVE 或真实交易表现，也不构成投资建议。Backtest 偏差用于比较 Paper 与回测表现差异，缺失数据不会被伪造。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 策略评估加载失败"
                        error={query.error as AppApiError}
                        description="策略评估不可用（旧后端可能尚未提供该接口）；其余 Paper 模块不受影响。"
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !evaluation ? (
                    <NqLoadingState message="加载 Paper 策略评估中..."/>
                ) : !evaluation ? (
                    <NqEmptyState description="暂无 Paper 策略评估数据（接口未返回评估结构）。"/>
                ) : evaluation.overview.strategyCount === 0 ? (
                    <NqEmptyState description="暂无 Paper 策略评估数据，创建并运行 Paper run 后自动生成策略评估。"/>
                ) : (
                    <PaperStrategyEvaluationBody evaluation={evaluation}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperStrategyEvaluationBody({evaluation}: {evaluation: PaperStrategyEvaluationsResponse}) {
    const {overview, strategyEvaluations, publishEvaluations, rankings} = evaluation;

    const [ratingFilter, setRatingFilter] = useState<EvalRatingFilter>('all');
    const [confidenceFilter, setConfidenceFilter] = useState<EvalConfidenceFilter>('all');
    const [deviationFilter, setDeviationFilter] = useState<EvalDeviationFilter>('all');
    const [sortDim, setSortDim] = useState<EvalSortDim>('compositeScore');
    const [sortDir, setSortDir] = useState<EvalSortDir>('desc');

    // 筛选只作用于 Strategy Evaluation 表；deviation 以 backtestDeviation.deviationLevel（缺失视为 UNAVAILABLE）为准。
    const filteredStrategies = strategyEvaluations.filter((s) => {
        const level: PaperBacktestDeviationLevel = s.backtestDeviation?.deviationLevel ?? 'UNAVAILABLE';
        return (ratingFilter === 'all' || s.ratingLabel === ratingFilter)
            && (confidenceFilter === 'all' || s.evaluationConfidence === confidenceFilter)
            && (deviationFilter === 'all' || level === deviationFilter);
    });
    const strategyRowsView = sortStrategyEvals(filteredStrategies, sortDim, sortDir);
    const filtered = ratingFilter !== 'all' || confidenceFilter !== 'all' || deviationFilter !== 'all';
    const sortDimLabel = EVAL_SORT_OPTIONS.find((o) => o.value === sortDim)?.label ?? '综合分';

    const subScoreColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        nqNumericColumn({title: '样本分', dataIndex: 'sampleScore', key: 'sampleScore', width: 80}),
        nqNumericColumn({title: '收益分', dataIndex: 'returnScore', key: 'returnScore', width: 80}),
        nqNumericColumn({title: '风险分', dataIndex: 'riskScore', key: 'riskScore', width: 80}),
        nqNumericColumn({title: '执行分', dataIndex: 'executionScore', key: 'executionScore', width: 80}),
        nqNumericColumn({title: 'Backtest 偏差分', key: 'backtestDeviationScore', width: 130,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => scoreCell(r.backtestDeviationScore)}),
    ];

    const strategyColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run', dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({title: '可比', dataIndex: 'comparableRunCount', key: 'comparableRunCount', width: 70}),
        nqNumericColumn({title: '发布', dataIndex: 'publishCount', key: 'publishCount', width: 70}),
        nqNumericColumn({title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '胜率', key: 'winRate', width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.winRate != null
                ? <NqPercentText value={r.winRate as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        ...subScoreColumns,
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => ratingTag(r.ratingLabel)},
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}/>},
        {title: '主要短板', dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: '警告', key: 'warnings', width: 220,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '最近运行', dataIndex: 'latestRunTime', key: 'latestRunTime', width: 170, render: (v: string | null) => v ? formatDateTime(v) : '-'},
    ];

    const publishColumns: ColumnsType<PaperPublishEvaluationItem> = [
        {title: '发布', dataIndex: 'publishId', key: 'publishId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 150, render: (v: string | null) => v ? <span className="nq-mono">{v}</span> : '-'},
        nqNumericColumn({title: 'Run', dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({title: '可比', dataIndex: 'comparableRunCount', key: 'comparableRunCount', width: 70}),
        nqNumericColumn({title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '胜率', key: 'winRate', width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.winRate != null
                ? <NqPercentText value={r.winRate as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        nqNumericColumn({title: '样本分', dataIndex: 'sampleScore', key: 'sampleScore', width: 80}),
        nqNumericColumn({title: '风险分', dataIndex: 'riskScore', key: 'riskScore', width: 80}),
        nqNumericColumn({title: '执行分', dataIndex: 'executionScore', key: 'executionScore', width: 80}),
        nqNumericColumn({title: 'Backtest 偏差分', key: 'backtestDeviationScore', width: 130,
            render: (_: unknown, r: PaperPublishEvaluationItem) => scoreCell(r.backtestDeviationScore)}),
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => ratingTag(r.ratingLabel)},
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}/>},
        {
            title: '警告', key: 'warnings', width: 200,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '最近运行', dataIndex: 'latestRunTime', key: 'latestRunTime', width: 170, render: (v: string | null) => v ? formatDateTime(v) : '-'},
    ];

    // Paper vs Backtest 偏差表：每个策略一行；无 backtest 时 level=UNAVAILABLE、数值显示「-」。
    const deviationColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Backtest 收益', key: 'backtestReturn', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.backtestReturn != null
                ? <NqPercentText value={r.backtestDeviation.backtestReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: 'Paper 收益', key: 'paperReturn', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.paperReturn != null
                ? <NqPercentText value={r.backtestDeviation.paperReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: '收益偏差', key: 'returnDeviation', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.returnDeviation != null
                ? <NqPercentText value={r.backtestDeviation.returnDeviation as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: 'Backtest 回撤', key: 'backtestMaxDrawdown', width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.backtestMaxDrawdown != null
                ? <NqPercentText value={r.backtestDeviation.backtestMaxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: 'Paper 回撤', key: 'paperMaxDrawdown', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.paperMaxDrawdown != null
                ? <NqPercentText value={r.backtestDeviation.paperMaxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '回撤偏差', key: 'drawdownDeviation', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.drawdownDeviation != null
                ? <NqPercentText value={r.backtestDeviation.drawdownDeviation as string | number} ratio colorBySign/> : '-'}),
        {title: '偏差等级', key: 'deviationLevel', width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => {
                const level: PaperBacktestDeviationLevel = r.backtestDeviation?.deviationLevel ?? 'UNAVAILABLE';
                return <NqStatusTag status={level} tone={DEVIATION_LEVEL_TONE[level]}/>;
            }},
        {title: '说明', key: 'deviationExplanation', width: 320,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => (
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {r.backtestDeviation?.deviationExplanation ?? 'Backtest 不可用，无法计算偏差。'}
                </Typography.Text>
            )},
    ];

    const rankingItems: Array<{label: string; keys: string[]}> = [
        {label: '综合分最高', keys: rankings.topCompositeStrategies},
        {label: '综合分最低', keys: rankings.worstCompositeStrategies},
        {label: '收益最高', keys: rankings.topReturnStrategies},
        {label: '回撤最大', keys: rankings.worstDrawdownStrategies},
        {label: '样本不足', keys: rankings.sampleInsufficientStrategies},
        {label: '高偏差', keys: rankings.highDeviationStrategies},
        {label: '高风险', keys: rankings.highRiskStrategies},
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 评估总览 */}
            <div className="nq-status-strip">
                <NqMetricCard label="策略数" value={String(overview.strategyCount)} footer="strategyVersionId"/>
                <NqMetricCard label="发布数" value={String(overview.publishCount)}/>
                <NqMetricCard label="纳入评估 run" value={String(overview.evaluatedRunCount)}/>
                <NqMetricCard label="可比 run" value={String(overview.comparableRunCount)}/>
                <NqMetricCard label="样本不足策略" value={String(overview.sampleInsufficientStrategyCount)} tone={overview.sampleInsufficientStrategyCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="盈利策略" value={String(overview.profitableStrategyCount)} tone={overview.profitableStrategyCount > 0 ? 'success' : 'muted'}/>
                <NqMetricCard label="亏损策略" value={String(overview.lossStrategyCount)} tone={overview.lossStrategyCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="高风险策略" value={String(overview.highRiskStrategyCount)} tone={overview.highRiskStrategyCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="高偏差策略" value={String(overview.backtestDeviationStrategyCount)} tone={overview.backtestDeviationStrategyCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="最高综合分" value={overview.topCompositeScore != null ? String(overview.topCompositeScore) : '-'}/>
                <NqMetricCard label="最低综合分" value={overview.worstCompositeScore != null ? String(overview.worstCompositeScore) : '-'}/>
            </div>

            {/* B) Strategy Evaluation 表（受评级 / 可信度 / 偏差筛选 + 排序控件） */}
            <Card
                size="small"
                title={filtered
                    ? `策略评估 · 当前筛选命中 ${strategyRowsView.length} 条`
                    : 'Strategy Version 策略评估'}
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setRatingFilter('all'); setConfidenceFilter('all'); setDeviationFilter('all');}}>查看全部</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label="Paper 策略评估筛选"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>评级</Typography.Text>
                        <Select<EvalRatingFilter>
                            size="small" value={ratingFilter} onChange={setRatingFilter}
                            options={EVAL_RATING_FILTER_OPTIONS as Array<{label: string; value: EvalRatingFilter}>}
                            style={{width: 240}} virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>可信度</Typography.Text>
                        <Select<EvalConfidenceFilter>
                            size="small" value={confidenceFilter} onChange={setConfidenceFilter}
                            options={EVAL_CONFIDENCE_FILTER_OPTIONS as Array<{label: string; value: EvalConfidenceFilter}>}
                            style={{width: 140}} virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>Backtest 偏差</Typography.Text>
                        <Select<EvalDeviationFilter>
                            size="small" value={deviationFilter} onChange={setDeviationFilter}
                            options={EVAL_DEVIATION_FILTER_OPTIONS as Array<{label: string; value: EvalDeviationFilter}>}
                            style={{width: 150}} virtual={false}
                        />
                    </div>
                    <div
                        role="group"
                        aria-label="Paper 策略评估排序"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>排序维度</Typography.Text>
                        <Select<EvalSortDim>
                            size="small" value={sortDim} onChange={setSortDim}
                            options={EVAL_SORT_OPTIONS as Array<{label: string; value: EvalSortDim}>}
                            style={{width: 160}} virtual={false}
                        />
                        <Segmented
                            size="small" value={sortDir}
                            onChange={(v) => setSortDir(v as EvalSortDir)}
                            options={[{label: '降序', value: 'desc'}, {label: '升序', value: 'asc'}]}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            当前：{sortDimLabel} · {sortDir === 'desc' ? '降序' : '升序'} · 命中 {strategyRowsView.length} / {strategyEvaluations.length}（null 分恒排末尾）
                        </Typography.Text>
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        综合分为 0~100 Paper 内部启发式分（样本/收益/风险/执行/Backtest 偏差加权）；缺 Backtest 偏差分时显示「数据不足」，不伪造 0。
                    </Typography.Text>
                    <div role="region" aria-label="Paper 策略评估表">
                        <NqDataTable<PaperStrategyEvaluationItem>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyRowsView}
                            columns={strategyColumns}
                            scroll={{x: 1980, y: 320}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的策略评估。'}}
                        />
                    </div>
                </Space>
            </Card>

            {/* C) Publish Evaluation 表 */}
            <Card size="small" title="Publish 策略评估">
                <div role="region" aria-label="Paper 发布评估表">
                    <NqDataTable<PaperPublishEvaluationItem>
                        rowKey="publishId"
                        pagination={false}
                        dataSource={publishEvaluations}
                        columns={publishColumns}
                        scroll={{x: 1760, y: 280}}
                        locale={{emptyText: '暂无可聚合的发布评估。'}}
                    />
                </div>
            </Card>

            {/* D) Paper vs Backtest 偏差表 */}
            <Card size="small" title="Paper vs Backtest 偏差">
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        Backtest 偏差用于比较 Paper 与回测表现差异，缺失数据显示 UNAVAILABLE，不会被伪造。
                    </Typography.Text>
                    <div role="region" aria-label="Paper 回测偏差表">
                        <NqDataTable<PaperStrategyEvaluationItem>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyEvaluations}
                            columns={deviationColumns}
                            scroll={{x: 1310, y: 260}}
                            locale={{emptyText: '暂无可对照的 Backtest 偏差。'}}
                        />
                    </div>
                </Space>
            </Card>

            {/* E) Rankings */}
            <Card size="small" title="策略评估榜单">
                <div role="region" aria-label="Paper 策略评估榜单">
                    <Descriptions bordered size="small" column={1}>
                        {rankingItems.map((item) => (
                            <Descriptions.Item key={item.label} label={item.label}>
                                {item.keys.length > 0 ? (
                                    <Space size={4} wrap>{item.keys.map((k) => <Tag key={k} className="nq-mono">{k}</Tag>)}</Space>
                                ) : (
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>无</Typography.Text>
                                )}
                            </Descriptions.Item>
                        ))}
                    </Descriptions>
                </div>
            </Card>

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                该评分是 Paper 内部启发式评估，不是真实投资评级；不代表 LIVE 或真实交易表现，也不构成投资建议。
            </Typography.Text>
        </Space>
    );
}

// ---- GateK K4B：Paper 规则化自动复盘展示映射与筛选（消费 K4 endpoint，纯前端只读展示）----

/** 聚类专属 cause（执行 / 评估维度聚类）补充中文名；run primaryCause 复用执行诊断 cause 映射。 */
const AUTO_REVIEW_EXTRA_CAUSE_LABEL: Record<string, string> = {
    BACKTEST_DEVIATION_HIGH: 'Backtest 偏差大',
    SAMPLE_INSUFFICIENT: '样本不足',
};

function autoReviewCauseLabel(cause: string): string {
    return EXECUTION_CAUSE_LABEL[cause as PaperExecutionCause]
        ?? AUTO_REVIEW_EXTRA_CAUSE_LABEL[cause] ?? cause;
}

function autoReviewCauseTone(cause: string): NqStatusTone {
    return EXECUTION_CAUSE_TONE[cause as PaperExecutionCause]
        ?? (cause === 'BACKTEST_DEVIATION_HIGH' ? 'danger' : cause === 'SAMPLE_INSUFFICIENT' ? 'warning' : 'neutral');
}

/** cause 标签（中文名 + 语义色），缺省回退原始枚举值，不伪造。 */
function autoReviewCauseTag(cause: string) {
    return <NqStatusTag status={autoReviewCauseLabel(cause)} tone={autoReviewCauseTone(cause)}/>;
}

/** ratingLabel 标签（复用策略评估评级中文名与语义色）。 */
function autoReviewRatingTag(rating: string) {
    return (
        <NqStatusTag
            status={RATING_LABEL_TEXT[rating as PaperStrategyRatingLabel] ?? rating}
            tone={RATING_LABEL_TONE[rating as PaperStrategyRatingLabel] ?? 'neutral'}
        />
    );
}

type AutoReviewSeverityFilter = 'all' | PaperAutoReviewSeverity;
type AutoReviewDimensionFilter = 'all' | 'run' | 'strategy' | 'publish' | 'cluster';
type AutoReviewCauseFilter = string;

const AUTO_REVIEW_SEVERITY_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewSeverityFilter}> = [
    {label: '全部严重度', value: 'all'},
    {label: 'CRITICAL', value: 'CRITICAL'},
    {label: 'WARNING', value: 'WARNING'},
    {label: 'INFO', value: 'INFO'},
];

const AUTO_REVIEW_DIMENSION_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewDimensionFilter}> = [
    {label: '全部维度', value: 'all'},
    {label: 'Run', value: 'run'},
    {label: 'Strategy', value: 'strategy'},
    {label: 'Publish', value: 'publish'},
    {label: 'Cluster', value: 'cluster'},
];

const AUTO_REVIEW_CAUSE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewCauseFilter}> = [
    {label: '全部原因', value: 'all'},
    {label: '无订单 NO_ORDER', value: 'NO_ORDER'},
    {label: '有订单无成交 ORDER_NO_FILL', value: 'ORDER_NO_FILL'},
    {label: '成交亏损 FILLED_LOSS', value: 'FILLED_LOSS'},
    {label: '风控拦截 RISK_BLOCKED', value: 'RISK_BLOCKED'},
    {label: '数据不足 DATA_INSUFFICIENT', value: 'DATA_INSUFFICIENT'},
    {label: '高回撤 HIGH_DRAWDOWN', value: 'HIGH_DRAWDOWN'},
    {label: '异常终态 FAILED_RUN', value: 'FAILED_RUN'},
    {label: 'Backtest 偏差大 BACKTEST_DEVIATION_HIGH', value: 'BACKTEST_DEVIATION_HIGH'},
    {label: '样本不足 SAMPLE_INSUFFICIENT', value: 'SAMPLE_INSUFFICIENT'},
    {label: '健康 HEALTHY', value: 'HEALTHY'},
];

/** 字符串清单渲染为 tag 列表；空时显示给定空文案（如 suggestedActions 的「暂无建议动作」）。 */
function autoReviewTagList(items: string[] | undefined, emptyText: string, color?: string) {
    if (!items || items.length === 0) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>{emptyText}</Typography.Text>;
    }
    return <Space size={4} wrap>{items.map((t, i) => <Tag key={`${t}-${i}`} color={color}>{t}</Tag>)}</Space>;
}

/** 字符串清单渲染为紧凑 bullet 列表；空时显示给定空文案。 */
function autoReviewBullets(items: string[] | undefined, emptyText: string) {
    if (!items || items.length === 0) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>{emptyText}</Typography.Text>;
    }
    return (
        <ul style={{margin: 0, paddingLeft: 18}}>
            {items.map((t, i) => <li key={`${t}-${i}`}><Typography.Text style={{fontSize: 12}}>{t}</Typography.Text></li>)}
        </ul>
    );
}

/**
 * PaperAutoReviewDashboard —— Paper 规则化自动复盘（GateK Batch K4B）。
 * 消费 K4 只读 endpoint /paper-trading/auto-reviews，把组合复盘、重点 run 复盘、策略 / 发布复盘与问题聚类展示出来，
 * 让用户从「诊断 + 评分」升级为「可读复盘」。复盘由规则引擎生成，不接 AI / DH runtime。
 * 独立 query：加载 / 错误 / 空 / 兼容回退均限定本区域，不连累组合看板、诊断、评估与排行。
 * 仅 Paper-only 规则化复盘，不代表 LIVE 或真实交易表现，也不构成投资建议。
 */
function PaperAutoReviewDashboard({query}: {query: ReturnType<typeof usePaperAutoReviewsQuery>}) {
    const raw = query.data;
    const review: PaperAutoReviewsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperAutoReviewsResponse).overview
            ? (raw as PaperAutoReviewsResponse)
            : null;
    const empty = review
        && review.overview.totalRuns === 0
        && review.overview.strategyReviewedCount === 0
        && review.overview.publishReviewedCount === 0;

    return (
      <section aria-label="Paper 自动复盘">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 自动复盘"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · Rules-based review</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    基于 Paper 执行事实、诊断与策略评估的规则化复盘摘要。
                </Typography.Text>
                <NqRiskBanner
                    level="info"
                    message="把执行诊断与策略评估结果规则化归纳为组合 / 重点 run / 策略 / 发布的可读复盘摘要与问题聚类。"
                    description="该复盘仅基于 Paper 模拟运行、诊断与策略评估结果。复盘由规则引擎生成，不接 AI / DH runtime。内容不代表 LIVE 或真实交易表现，也不构成投资建议。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 自动复盘加载失败"
                        error={query.error as AppApiError}
                        description="自动复盘不可用（旧后端可能尚未提供该接口）；其余 Paper 模块不受影响。"
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !review ? (
                    <NqLoadingState message="加载 Paper 自动复盘中..."/>
                ) : !review ? (
                    <NqEmptyState description="暂无 Paper 自动复盘数据（接口未返回复盘结构）。"/>
                ) : empty ? (
                    <NqEmptyState description="暂无 Paper 自动复盘数据，创建并运行 Paper run 后自动生成规则化复盘。"/>
                ) : (
                    <PaperAutoReviewBody review={review}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperAutoReviewBody({review}: {review: PaperAutoReviewsResponse}) {
    const overview = review.overview;
    const portfolioReview = review.portfolioReview;
    const runReviews = review.runReviews ?? [];
    const strategyReviews = review.strategyReviews ?? [];
    const publishReviews = review.publishReviews ?? [];
    const issueClusters = review.issueClusters ?? [];

    const [severityFilter, setSeverityFilter] = useState<AutoReviewSeverityFilter>('all');
    const [causeFilter, setCauseFilter] = useState<AutoReviewCauseFilter>('all');
    const [dimensionFilter, setDimensionFilter] = useState<AutoReviewDimensionFilter>('all');

    // 筛选优先作用于 Run Reviews（按 primaryCause / severity）与 Issue Clusters（按 cause / severity）。
    const filteredRuns = runReviews.filter((r) =>
        (severityFilter === 'all' || r.severity === severityFilter)
        && (causeFilter === 'all' || r.primaryCause === causeFilter));
    const filteredClusters = issueClusters.filter((c) =>
        (severityFilter === 'all' || c.severity === severityFilter)
        && (causeFilter === 'all' || c.cause === causeFilter));
    const filtered = severityFilter !== 'all' || causeFilter !== 'all';

    const showRuns = dimensionFilter === 'all' || dimensionFilter === 'run';
    const showStrategies = dimensionFilter === 'all' || dimensionFilter === 'strategy';
    const showPublishes = dimensionFilter === 'all' || dimensionFilter === 'publish';
    const showClusters = dimensionFilter === 'all' || dimensionFilter === 'cluster';

    const runColumns: ColumnsType<PaperRunAutoReview> = [
        {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 150, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
        {title: '主因', key: 'primaryCause', width: 120, render: (_: unknown, r) => autoReviewCauseTag(r.primaryCause)},
        {title: '严重度', key: 'severity', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.severity} tone={EXECUTION_SEVERITY_TONE[r.severity]}/>},
        {title: '可信度', key: 'confidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.confidence} tone={EXECUTION_CONFIDENCE_TONE[r.confidence as PaperExecutionCauseConfidence] ?? 'neutral'}/>},
        nqNumericColumn({
            title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperRunAutoReview) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-',
        }),
        nqNumericColumn({
            title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperRunAutoReview) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-',
        }),
        {
            title: '复盘', key: 'review', width: 320,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: '关键事实', key: 'keyFacts', width: 220, render: (_: unknown, r) => autoReviewTagList(r.keyFacts, '-')},
        {title: '可能原因', key: 'likelyReasons', width: 240, render: (_: unknown, r) => autoReviewBullets(r.likelyReasons, '-')},
        {title: '建议排查动作', key: 'suggestedActions', width: 240, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, '暂无建议动作', 'blue')},
        {title: '标签', key: 'tags', width: 180, render: (_: unknown, r) => autoReviewTagList(r.tags, '-')},
    ];

    const strategyReviewColumns: ColumnsType<PaperStrategyAutoReview> = [
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => autoReviewRatingTag(r.ratingLabel)},
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90, render: (_: unknown, r: PaperStrategyAutoReview) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence as PaperStrategyEvaluationConfidence] ?? 'neutral'}/>},
        {title: '主要短板', dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: '复盘', key: 'review', width: 300,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: '优势', key: 'strengths', width: 220, render: (_: unknown, r) => autoReviewTagList(r.strengths, '暂无突出优势', 'green')},
        {title: '短板', key: 'weaknesses', width: 240, render: (_: unknown, r) => autoReviewBullets(r.weaknesses, '-')},
        {
            title: '警告', key: 'warnings', width: 200,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '建议排查动作', key: 'suggestedActions', width: 240, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, '暂无建议动作', 'blue')},
    ];

    const publishReviewColumns: ColumnsType<PaperPublishAutoReview> = [
        {title: '发布', dataIndex: 'publishId', key: 'publishId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 150, render: (v: string | null) => v ? <span className="nq-mono">{v}</span> : '-'},
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => autoReviewRatingTag(r.ratingLabel)},
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90, render: (_: unknown, r: PaperPublishAutoReview) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence as PaperStrategyEvaluationConfidence] ?? 'neutral'}/>},
        {title: '主要短板', dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: '复盘', key: 'review', width: 300,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: '优势', key: 'strengths', width: 200, render: (_: unknown, r) => autoReviewTagList(r.strengths, '暂无突出优势', 'green')},
        {title: '短板', key: 'weaknesses', width: 220, render: (_: unknown, r) => autoReviewBullets(r.weaknesses, '-')},
        {
            title: '警告', key: 'warnings', width: 180,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '建议排查动作', key: 'suggestedActions', width: 220, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, '暂无建议动作', 'blue')},
    ];

    const clusterColumns: ColumnsType<PaperIssueCluster> = [
        {title: '聚类', dataIndex: 'clusterKey', key: 'clusterKey', width: 200, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '原因', key: 'cause', width: 140, render: (_: unknown, c) => autoReviewCauseTag(c.cause)},
        {title: '严重度', key: 'severity', width: 110, render: (_: unknown, c) => <NqStatusTag status={c.severity} tone={EXECUTION_SEVERITY_TONE[c.severity]}/>},
        nqNumericColumn({title: '数量', dataIndex: 'count', key: 'count', width: 80}),
        {
            title: '受影响 Run / 策略 / 发布', key: 'affected', width: 280,
            render: (_: unknown, c) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        Run {c.affectedRunIds.length} · 策略 {c.affectedStrategyVersionIds.length} · 发布 {c.affectedPublishIds.length}
                    </Typography.Text>
                    {c.affectedRunIds.length > 0
                        ? <Space size={4} wrap>{c.affectedRunIds.map((id) => <Tag key={id} className="nq-mono">{id}</Tag>)}</Space>
                        : c.affectedStrategyVersionIds.length > 0
                            ? <Space size={4} wrap>{c.affectedStrategyVersionIds.map((id) => <Tag key={id} className="nq-mono">{id}</Tag>)}</Space>
                            : null}
                </Space>
            ),
        },
        {title: '摘要', dataIndex: 'summary', key: 'summary', width: 300, render: (v: string) => <Typography.Text style={{fontSize: 12}}>{v}</Typography.Text>},
        {title: '建议排查动作', dataIndex: 'suggestedAction', key: 'suggestedAction', width: 260, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 复盘总览 */}
            <div className="nq-status-strip">
                <NqMetricCard label="纳入复盘 run" value={String(overview.totalRuns)} footer="bounded Paper run"/>
                <NqMetricCard label="已复盘 run" value={String(overview.reviewedRunCount)}/>
                <NqMetricCard label="问题 run" value={String(overview.issueRunCount)} tone={overview.issueRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="健康 run" value={String(overview.healthyRunCount)} tone={overview.healthyRunCount > 0 ? 'success' : 'muted'}/>
                <NqMetricCard label="关键问题" value={String(overview.criticalIssueCount)} tone={overview.criticalIssueCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="警告问题" value={String(overview.warningIssueCount)} tone={overview.warningIssueCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="已复盘策略" value={String(overview.strategyReviewedCount)}/>
                <NqMetricCard label="已复盘发布" value={String(overview.publishReviewedCount)}/>
                <NqMetricCard label="最集中问题" value={overview.topIssueCause != null ? autoReviewCauseLabel(overview.topIssueCause) : '-'}/>
                <NqMetricCard label="最常见短板" value={overview.topWeakness ?? '-'}/>
                <NqMetricCard label="生成时间" value={overview.generatedAt ? formatDateTime(overview.generatedAt) : '-'}/>
            </div>

            {/* B) Portfolio Review 摘要区 */}
            <Card size="small" title="组合复盘摘要">
                {portfolioReview ? (
                    <div role="region" aria-label="Paper 自动复盘组合摘要">
                        <Space direction="vertical" size={8} style={{display: 'flex'}}>
                            <Typography.Text strong style={{fontSize: 14}}>{portfolioReview.headline}</Typography.Text>
                            <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 0}}>{portfolioReview.summary}</Typography.Paragraph>
                            <Descriptions bordered size="small" column={1}>
                                <Descriptions.Item label="关键发现">{autoReviewBullets(portfolioReview.keyFindings, '无')}</Descriptions.Item>
                                <Descriptions.Item label="风险亮点">{autoReviewTagList(portfolioReview.riskHighlights, '无', 'red')}</Descriptions.Item>
                                <Descriptions.Item label="执行亮点">{autoReviewTagList(portfolioReview.executionHighlights, '无', 'orange')}</Descriptions.Item>
                                <Descriptions.Item label="策略亮点">{autoReviewTagList(portfolioReview.strategyHighlights, '无', 'geekblue')}</Descriptions.Item>
                                <Descriptions.Item label="Backtest 偏差">{autoReviewTagList(portfolioReview.backtestDeviationHighlights, '无', 'purple')}</Descriptions.Item>
                                <Descriptions.Item label="建议排查动作">{autoReviewTagList(portfolioReview.suggestedNextActions, '暂无建议动作', 'blue')}</Descriptions.Item>
                                <Descriptions.Item label="复盘局限">{autoReviewBullets(portfolioReview.limitations, '无')}</Descriptions.Item>
                            </Descriptions>
                        </Space>
                    </div>
                ) : (
                    <NqEmptyState description="暂无组合复盘摘要。"/>
                )}
            </Card>

            {/* 复盘筛选：severity / cause 影响 Run Reviews 与 Issue Clusters；dimension 控制展示维度。 */}
            <Card
                size="small"
                title="复盘筛选"
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setSeverityFilter('all'); setCauseFilter('all'); setDimensionFilter('all');}}>查看全部</Button>
                ) : null}
            >
                <div
                    role="group"
                    aria-label="Paper 自动复盘筛选"
                    style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                >
                    <Typography.Text type="secondary" style={{fontSize: 12}}>严重度</Typography.Text>
                    <Select<AutoReviewSeverityFilter>
                        size="small" value={severityFilter} onChange={setSeverityFilter}
                        options={AUTO_REVIEW_SEVERITY_FILTER_OPTIONS as Array<{label: string; value: AutoReviewSeverityFilter}>}
                        style={{width: 150}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>原因</Typography.Text>
                    <Select<AutoReviewCauseFilter>
                        size="small" value={causeFilter} onChange={setCauseFilter}
                        options={AUTO_REVIEW_CAUSE_FILTER_OPTIONS as Array<{label: string; value: AutoReviewCauseFilter}>}
                        style={{width: 280}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>展示维度</Typography.Text>
                    <Select<AutoReviewDimensionFilter>
                        size="small" value={dimensionFilter} onChange={setDimensionFilter}
                        options={AUTO_REVIEW_DIMENSION_FILTER_OPTIONS as Array<{label: string; value: AutoReviewDimensionFilter}>}
                        style={{width: 150}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        命中 Run {filteredRuns.length} / {runReviews.length} · 聚类 {filteredClusters.length} / {issueClusters.length}
                    </Typography.Text>
                </div>
            </Card>

            {/* C) Issue Clusters（受 severity / cause 筛选） */}
            {showClusters ? (
                <Card size="small" title={filtered ? `问题聚类 · 当前筛选命中 ${filteredClusters.length} 条` : '问题聚类'}>
                    <div role="region" aria-label="Paper 自动复盘问题聚类">
                        <NqDataTable<PaperIssueCluster>
                            rowKey="clusterKey"
                            pagination={false}
                            dataSource={filteredClusters}
                            columns={clusterColumns}
                            scroll={{x: 1290, y: 320}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的问题聚类。'}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* D) Run Reviews（受 severity / cause 筛选） */}
            {showRuns ? (
                <Card size="small" title={filtered ? `重点 Run 复盘 · 当前筛选命中 ${filteredRuns.length} 条` : '重点 Run 复盘'}>
                    <div role="region" aria-label="Paper 自动复盘 Run 表">
                        <NqDataTable<PaperRunAutoReview>
                            rowKey="paperRunId"
                            pagination={false}
                            dataSource={filteredRuns}
                            columns={runColumns}
                            scroll={{x: 2020, y: 360}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的 Run 复盘。'}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* E) Strategy Reviews */}
            {showStrategies ? (
                <Card size="small" title="策略复盘">
                    <div role="region" aria-label="Paper 自动复盘 Strategy 表">
                        <NqDataTable<PaperStrategyAutoReview>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyReviews}
                            columns={strategyReviewColumns}
                            scroll={{x: 1900, y: 320}}
                            locale={{emptyText: '暂无可复盘的策略版本。'}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* F) Publish Reviews */}
            {showPublishes ? (
                <Card size="small" title="发布复盘">
                    <div role="region" aria-label="Paper 自动复盘 Publish 表">
                        <NqDataTable<PaperPublishAutoReview>
                            rowKey="publishId"
                            pagination={false}
                            dataSource={publishReviews}
                            columns={publishReviewColumns}
                            scroll={{x: 1960, y: 320}}
                            locale={{emptyText: '暂无可复盘的发布。'}}
                        />
                    </div>
                </Card>
            ) : null}

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                该复盘由规则引擎生成，不接 AI / DH runtime；仅 Paper 模拟口径，不代表 LIVE 或真实交易表现，也不构成投资建议。
                建议动作均为工程排查动作（检查数据 / 触发条件 / 撮合参数 / 风控阈值 / 增加样本 / 复核 Backtest 偏差）。
            </Typography.Text>
        </Space>
    );
}

/**
 * PaperStrategyRankingDashboard —— Paper 策略表现排行（GateJ 后产品化 Loop-16）。
 * 复用 Loop-13 组合 summary 单请求结果（strategyGroups / publishGroups + highlights / dataQuality），
 * 从 strategyVersionId / publishId 维度只读派生表现排行与风险调整排序分（Paper 内部排序分，非真实投资评级）。
 * 仅代表 SIM/Paper 模拟，不读真实交易所账户余额，不代表 LIVE 或真实交易；数据不足不伪造排名。
 */
function PaperStrategyRankingDashboard({query}: {query: ReturnType<typeof usePaperPortfolioSummaryQuery>}) {
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    const hasGroups = Boolean(portfolio)
        && (portfolio!.strategyGroups.length > 0 || portfolio!.publishGroups.length > 0);

    return (
      <section aria-label="Paper 策略表现排行">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 策略表现排行"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="info"
                    message="按 strategyVersionId / publishId 维度横向比较多个 Paper run 的模拟表现。"
                    description="该策略排行仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 策略表现排行加载失败"
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 || !hasGroups ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="暂无可分组的 Paper 策略 / 发布数据，创建并运行 Paper run 后自动汇总排行。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，不做排行</Typography.Text>
                    </Space>
                ) : (
                    <PaperStrategyRankingBody portfolio={portfolio}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperStrategyRankingBody({portfolio}: {portfolio: PaperPortfolioSummaryResponse}) {
    const {strategyGroups, publishGroups, highlights, dataQuality} = portfolio;

    // Loop-19：排行控件状态（默认风险调整分降序、不过滤），同一套控件同时作用于 Strategy / Publish 两张表。
    const [sortDim, setSortDim] = useState<RankingSortDim>('score');
    const [sortDir, setSortDir] = useState<RankingSortDir>('desc');
    const [rankFilter, setRankFilter] = useState<RankingFilter>('all');

    const strategyRows = buildRankingRows(
        strategyGroups, highlights.noTradeRuns, dataQuality.dataInsufficientRuns, (run) => run.strategyVersionId);
    const publishRows = buildRankingRows(
        publishGroups, highlights.noTradeRuns, dataQuality.dataInsufficientRuns, (run) => run.publishId);

    // 控件作用于「展示」：先过滤再排序；榜单概览仍基于完整 strategyRows（保持总览稳定，不随过滤/排序变化）。
    const strategyRowsView = sortRankingRows(filterRankingRows(strategyRows, rankFilter), sortDim, sortDir);
    const publishRowsView = sortRankingRows(filterRankingRows(publishRows, rankFilter), sortDim, sortDir);
    const sortDimLabel = RANKING_SORT_OPTIONS.find((o) => o.value === sortDim)?.label ?? '风险调整分';
    const sortDirLabel = sortDir === 'desc' ? '降序' : '升序';
    const rankFilterLabel = RANKING_FILTER_OPTIONS.find((o) => o.value === rankFilter)?.label ?? '全部';
    const filtered = rankFilter !== 'all';
    const filterEmptyText = '当前筛选条件下暂无匹配的数据。';

    /** 点击榜单概览卡直接切换排行过滤（Loop-20 click-to-filter）。 */
    const handleRankingCardClick = (filter: RankingFilter) => setRankFilter(filter);

    // 榜单概览基于策略维度（口径与表格一致）；无交易 / 数据不足 / 异常终态均用后端 group 精确计数。
    const topReturn = topRankingRow(strategyRows, (row) => toNullableNumber(row.totalReturn), true);
    const topScore = topRankingRow(strategyRows, (row) => row.score, true);
    const worstDrawdown = topRankingRow(strategyRows, (row) => toNullableNumber(row.worstDrawdown), false);
    const mostRiskBlocked = topRankingRow(strategyRows, (row) => row.riskBlockedCount, true);
    const mostNoTrade = topRankingRow(strategyRows, (row) => row.noTradeCount, true);
    const mostDataInsufficient = topRankingRow(strategyRows, (row) => row.dataInsufficientCount, true);
    const mostFailedCancelled = topRankingRow(strategyRows, (row) => row.failedCancelledCount, true);

    const totalGroupRuns = strategyRows.reduce((sum, row) => sum + row.runCount, 0);
    const lowSample = totalGroupRuns < 3;

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* 1) 榜单概览（footer 为对应 strategyVersionId） */}
            <div className="nq-status-strip">
                <NqMetricCard
                    label="收益最高"
                    value={topReturn ? <NqPercentText value={topReturn.totalReturn as string | number} ratio colorBySign/> : '-'}
                    footer={topReturn ? topReturn.key : '暂无可比数据'}
                    tone={topReturn ? pnlTone(toNullableNumber(topReturn.totalReturn)) : 'muted'}
                />
                <NqMetricCard
                    label="风险调整后最高"
                    value={topScore ? <span className="nq-num">{formatNqNumber(topScore.score as number, {precision: 4, signed: true})}</span> : '-'}
                    footer={topScore ? topScore.key : '暂无可比数据'}
                />
                <NqMetricCard
                    label="回撤最大"
                    value={worstDrawdown ? <NqPercentText value={worstDrawdown.worstDrawdown as string | number} ratio signed={false}/> : '-'}
                    footer={worstDrawdown ? worstDrawdown.key : '暂无可比数据'}
                    tone="warning"
                />
                <ClickableMetricCard
                    ariaLabel="过滤仅有风控拦截的策略"
                    testId="ranking-filter-card-risk-blocked"
                    isActive={rankFilter === 'riskBlocked'}
                    onClick={() => handleRankingCardClick('riskBlocked')}
                >
                    <NqMetricCard
                        label="风控拦截最多"
                        value={mostRiskBlocked ? String(mostRiskBlocked.riskBlockedCount) : '0'}
                        footer={mostRiskBlocked && mostRiskBlocked.riskBlockedCount > 0 ? mostRiskBlocked.key : '暂无风控拦截'}
                        tone={mostRiskBlocked && mostRiskBlocked.riskBlockedCount > 0 ? 'danger' : 'muted'}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="过滤仅无订单的策略"
                    testId="ranking-filter-card-no-order"
                    isActive={rankFilter === 'noOrder'}
                    onClick={() => handleRankingCardClick('noOrder')}
                >
                    <NqMetricCard
                        label="无交易最多"
                        value={mostNoTrade ? String(mostNoTrade.noTradeCount) : '0'}
                        footer={mostNoTrade && mostNoTrade.noTradeCount > 0 ? mostNoTrade.key : '暂无无交易'}
                        tone={mostNoTrade && mostNoTrade.noTradeCount > 0 ? 'warning' : 'muted'}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="过滤仅数据不足的策略"
                    testId="ranking-filter-card-data-insufficient"
                    isActive={rankFilter === 'dataInsufficient'}
                    onClick={() => handleRankingCardClick('dataInsufficient')}
                >
                    <NqMetricCard
                        label="数据不足最多"
                        value={mostDataInsufficient ? String(mostDataInsufficient.dataInsufficientCount) : '0'}
                        footer={mostDataInsufficient && mostDataInsufficient.dataInsufficientCount > 0 ? mostDataInsufficient.key : '暂无数据不足'}
                        tone={mostDataInsufficient && mostDataInsufficient.dataInsufficientCount > 0 ? 'warning' : 'muted'}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="过滤仅异常终态的策略"
                    testId="ranking-filter-card-abnormal-terminal"
                    isActive={rankFilter === 'abnormalTerminal'}
                    onClick={() => handleRankingCardClick('abnormalTerminal')}
                >
                    <NqMetricCard
                        label="异常终态最多"
                        value={mostFailedCancelled ? String(mostFailedCancelled.failedCancelledCount) : '0'}
                        footer={mostFailedCancelled && mostFailedCancelled.failedCancelledCount > 0 ? mostFailedCancelled.key : '暂无异常终态'}
                        tone={mostFailedCancelled && mostFailedCancelled.failedCancelledCount > 0 ? 'danger' : 'muted'}
                    />
                </ClickableMetricCard>
            </div>
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                风险调整分为 Paper 内部排序分，仅用于模拟结果横向比较，不代表真实投资评级。
                {lowSample ? ' 当前可比 run 数较少，排行仅供参考。' : ''}
            </Typography.Text>

            {/* 1.5) 排行控件：排序维度 / 方向 / 数据过滤（同一套控件同时作用于 Strategy / Publish 两张表） */}
            <div
                role="group"
                aria-label="策略排行排序控制"
                style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
            >
                <Typography.Text type="secondary" style={{fontSize: 12}}>排序维度</Typography.Text>
                <Select<RankingSortDim>
                    size="small"
                    value={sortDim}
                    onChange={setSortDim}
                    options={RANKING_SORT_OPTIONS as Array<{label: string; value: RankingSortDim}>}
                    style={{width: 132}}
                    virtual={false}
                />
                <Segmented
                    size="small"
                    value={sortDir}
                    onChange={(v) => setSortDir(v as RankingSortDir)}
                    options={[{label: '降序', value: 'desc'}, {label: '升序', value: 'asc'}]}
                />
                <Typography.Text type="secondary" style={{fontSize: 12}}>数据过滤</Typography.Text>
                <Select<RankingFilter>
                    size="small"
                    value={rankFilter}
                    onChange={setRankFilter}
                    options={RANKING_FILTER_OPTIONS as Array<{label: string; value: RankingFilter}>}
                    style={{width: 160}}
                    virtual={false}
                />
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    当前：{sortDimLabel} · {sortDirLabel}
                    {filtered ? ` · ${rankFilterLabel}（命中 Strategy ${strategyRowsView.length} · Publish ${publishRowsView.length}）` : ''}
                </Typography.Text>
            </div>

            {/* 2) Strategy Version 排行表（受排行控件控制） */}
            <Card size="small" title={`Strategy Version 排行（按${sortDimLabel}·${sortDirLabel}）`}>
                <div role="region" aria-label="策略版本排行表">
                    <NqDataTable<PaperStrategyRankingRow>
                        rowKey="key"
                        pagination={false}
                        dataSource={strategyRowsView}
                        columns={rankingColumns('策略版本')}
                        scroll={{x: 1610, y: 280}}
                        locale={{emptyText: filtered ? filterEmptyText : '暂无可分组的策略版本数据。'}}
                    />
                </div>
            </Card>

            {/* 3) Publish 排行表（受同一套排行控件控制） */}
            <Card size="small" title={`Publish 排行（按${sortDimLabel}·${sortDirLabel}）`}>
                <div role="region" aria-label="发布排行表">
                    <NqDataTable<PaperStrategyRankingRow>
                        rowKey="key"
                        pagination={false}
                        dataSource={publishRowsView}
                        columns={rankingColumns('发布')}
                        scroll={{x: 1610, y: 280}}
                        locale={{emptyText: filtered ? filterEmptyText : '暂无可分组的发布数据。'}}
                    />
                </div>
            </Card>

            {/* 4) 数据质量提示 */}
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                数据质量：累计收益率 / 最大回撤缺失的策略不参与风险调整排序（显示「数据不足」）；
                缺 equity / 缺 publish / 缺 backtest 来源详见上方风险驾驶舱与组合看板的数据质量提示。
            </Typography.Text>
        </Space>
    );
}

function SnapshotBlock({title, content}: {title: string; content: string | null | undefined}) {
    return (
        <Card size="small" title={title}>
            <Typography.Paragraph
                className="nq-mono"
                copyable={Boolean(content)}
                style={{margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all'}}
            >
                {content || '-'}
            </Typography.Paragraph>
        </Card>
    );
}
