import {
    App,
    Button,
    Card,
    Col,
    Descriptions,
    Form,
    Input,
    Modal,
    Row,
    Select,
    Space,
    Tabs,
    Tag,
    Timeline,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useState, type ReactNode} from 'react';

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
    NqPriceText,
    NqRiskBanner,
    NqStatusTag,
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
    usePaperHeartbeatsQuery,
    usePaperPortfolioSummaryQuery,
    usePaperRecoveryEventsQuery,
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
