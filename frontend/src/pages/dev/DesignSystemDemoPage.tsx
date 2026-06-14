import {useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import {Button, ConfigProvider, Segmented} from 'antd';
import {BarChart} from 'echarts/charts';

import {echarts} from '@/components/nq/charts/echarts-core';
import {
    AppShell,
    ChangeCell,
    DataFreshness,
    EnvironmentBadge,
    MoneyCell,
    NumberCell,
    NQ_TABLE_DENSITY,
    PercentCell,
    RiskBanner,
    StatusCell,
    StatusTag,
    applyNqCssVars,
    marketColors,
    nqAntdTheme,
    nqCandleColors,
    nqTableClassName,
    registerNqEchartsTheme,
    type FreshnessState,
    type MarketConvention,
    type NqEnv,
    type NqTableDensity,
    type StatusTone,
} from '@/nq-design-system';

import '@/nq-design-system/table/nq-table.css';
import './DesignSystemDemoPage.css';

/**
 * DesignSystemDemoPage — B0(Design Tokens v2)自检页(非业务页面)。
 *
 * 职责:在真实工程内验证已审定的 NQ Console Design System v2 基线:
 * tokens / AntD 主题 / ECharts 主题 / 四件状态组件 / AppShell / 行情惯例开关。
 *
 * 接线范围(scoped):v2 的 ConfigProvider(nqAntdTheme)、applyNqCssVars、
 * registerNqEchartsTheme 仅在本路由内激活,不改全局 AppProviders、不动 GateJ 冻结的 v1 页面。
 * applyNqCssVars 向 :root 注入 --nq-*(与 v1 的 --nq-color-* 命名空间不冲突)。
 */

// echarts 核心已在 echarts-core 注册 LineChart 等;PnL 柱状演示需额外注册 BarChart(同一核心单例)。
echarts.use([BarChart]);

const STATUS_DEMOS: ReadonlyArray<{label: string; tone: StatusTone}> = [
    {label: 'RUNNING', tone: 'success'},
    {label: 'PENDING', tone: 'info'},
    {label: 'PAUSED', tone: 'neutral'},
    {label: 'DEGRADED', tone: 'warning'},
    {label: 'BLOCKED', tone: 'danger'},
    {label: 'SELECTED', tone: 'primary'},
];

const ENV_DEMOS: readonly NqEnv[] = ['PAPER', 'DEMO', 'LIVE', 'READONLY', 'AUDITED'];

const FRESHNESS_DEMOS: ReadonlyArray<{source: string; state: FreshnessState; detail?: string}> = [
    {source: 'OKX Market Data', state: 'fresh', detail: '2s ago'},
    {source: 'Binance Kline', state: 'stale', detail: '3m ago'},
    {source: 'Risk Engine', state: 'fresh', detail: '120ms'},
    {source: 'Strategy Scheduler', state: 'delayed', detail: 'last heartbeat 45s'},
    {source: 'Account Reconcile', state: 'degraded', detail: 'partial'},
    {source: 'Historical Backfill', state: 'very_stale', detail: '2h ago'},
    {source: 'Audit Stream', state: 'error', detail: 'connection refused'},
    {source: 'DH Agent Feedback', state: 'disabled'},
];

const NAV_DEMOS: ReadonlyArray<{label: string; active?: boolean}> = [
    {label: '控制台总览', active: true},
    {label: '交易工作台'},
    {label: '策略运行'},
    {label: '回测分析'},
    {label: '风控中心'},
    {label: '市场数据'},
    {label: '设置'},
];

// 合成样本数据(仅用于自检渲染,不接真实行情/接口)。
const SAMPLE_LABELS = ['09:30', '10:30', '11:30', '13:30', '14:30', '15:00'];
const SAMPLE_EQUITY = [100000, 100420, 100180, 100910, 101350, 101120];
const SAMPLE_PNL = [420, -240, 730, 440, -230, -180];

const TABLE_DENSITIES: readonly NqTableDensity[] = ['compact', 'standard', 'comfortable'];

interface SampleRow {
    symbol: string;
    status: string;
    last: number;
    qty: number;
    weight: number;
    pnl: number;
    changePct: number;
    updatedAt: string;
}

// 涨跌列(浮动盈亏 / 日涨跌幅)用 ChangeCell(行情方向色,随惯例翻转);仓位占比用 PercentCell(中性,不着色);其余数字等宽右对齐。
const SAMPLE_TABLE_ROWS: readonly SampleRow[] = [
    {symbol: 'BTC-USDT', status: 'RUNNING', last: 64231.5, qty: 0.8421, weight: 42.5, pnl: 1284.5, changePct: 2.13, updatedAt: '15:00:02'},
    {symbol: 'ETH-USDT', status: 'PENDING', last: 3120.42, qty: 12.5, weight: 28.0, pnl: -642.18, changePct: -1.27, updatedAt: '14:59:58'},
    {symbol: 'SOL-USDT', status: 'DEGRADED', last: 148.07, qty: 320, weight: 15.3, pnl: 0, changePct: 0, updatedAt: '14:59:41'},
    {symbol: 'BNB-USDT', status: 'BLOCKED', last: 588.9, qty: 5.2, weight: 9.1, pnl: 73.04, changePct: 0.42, updatedAt: '14:58:30'},
    {symbol: 'XRP-USDT', status: 'PAUSED', last: 0.5213, qty: 18000, weight: 5.1, pnl: -120.9, changePct: -0.88, updatedAt: '14:57:12'},
];

/**
 * MiniChart — 自检用最小 ECharts 容器。
 * 每次 convention 变化都重建实例,以应用按惯例重新注册的 'nq' 主题;卸载时 dispose 释放资源。
 */
function MiniChart({option, convention}: {option: Record<string, unknown>; convention: MarketConvention}) {
    const containerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        const element = containerRef.current;

        if (!element) {
            return;
        }

        // 防御性:确保 'nq' 主题已按当前惯例注册后再 init(与页面级注册幂等)。
        registerNqEchartsTheme(convention);
        const chart = echarts.init(element, 'nq');
        chart.setOption(option, {notMerge: true});

        const observer = new ResizeObserver(() => chart.resize());
        observer.observe(element);

        return () => {
            observer.disconnect();
            chart.dispose();
        };
    }, [option, convention]);

    return <div ref={containerRef} className="nq-ds-demo__chart"/>;
}

export function DesignSystemDemoPage() {
    const [convention, setConvention] = useState<MarketConvention>('INTL_CRYPTO');
    const [riskOpen, setRiskOpen] = useState(true);
    const [refreshTick, setRefreshTick] = useState(0);
    const [density, setDensity] = useState<NqTableDensity>('standard');

    // 页面级接线(scoped):注入 CSS 变量 + 注册 ECharts 主题。惯例切换"一处生效"。
    useLayoutEffect(() => {
        applyNqCssVars(convention);
        registerNqEchartsTheme(convention);
    }, [convention]);

    const equityOption = useMemo(
        () => ({
            grid: {left: 8, right: 12, top: 16, bottom: 24, containLabel: true},
            tooltip: {trigger: 'axis'},
            xAxis: {type: 'category', boundaryGap: false, data: SAMPLE_LABELS},
            yAxis: {type: 'value', scale: true},
            series: [
                {
                    name: '总权益',
                    type: 'line',
                    showSymbol: false,
                    smooth: true,
                    areaStyle: {opacity: 0.08},
                    data: SAMPLE_EQUITY,
                },
            ],
        }),
        [],
    );

    const pnlOption = useMemo(() => {
        const m = marketColors(convention);

        return {
            grid: {left: 8, right: 12, top: 16, bottom: 24, containLabel: true},
            tooltip: {trigger: 'axis'},
            xAxis: {type: 'category', data: SAMPLE_LABELS},
            yAxis: {type: 'value'},
            series: [
                {
                    name: '区间 PnL',
                    type: 'bar',
                    // 涨跌色随行情惯例翻转:盈利用 up、亏损用 down(独立于 success/danger)。
                    data: SAMPLE_PNL.map((value) => ({
                        value,
                        itemStyle: {color: value >= 0 ? m.up : m.down},
                    })),
                },
            ],
        };
    }, [convention]);

    const candle = nqCandleColors(convention);

    return (
        <ConfigProvider theme={nqAntdTheme}>
            <div className="nq-ds-demo nq-ds-demo--cjk">
                <AppShell
                    brand="NexusQuant"
                    nav={
                        <div style={{display: 'flex', flexDirection: 'column', gap: 2}}>
                            {NAV_DEMOS.map((item) => (
                                <div
                                    key={item.label}
                                    className={
                                        item.active
                                            ? 'nq-ds-demo__nav-item nq-ds-demo__nav-item--active'
                                            : 'nq-ds-demo__nav-item'
                                    }
                                >
                                    {item.label}
                                </div>
                            ))}
                        </div>
                    }
                    topRight={
                        <>
                            <EnvironmentBadge env="PAPER"/>
                            <EnvironmentBadge env="LIVE"/>
                            <DataFreshness source="OKX Market Data" state="fresh" detail="2s ago" inline/>
                        </>
                    }
                    pageHeader={
                        <>
                            <div style={{display: 'flex', alignItems: 'center', gap: 12}}>
                                <span style={{fontSize: 16, fontWeight: 600, color: 'var(--nq-text-primary)'}}>
                                    Design System v2 自检
                                </span>
                                <StatusTag label="B0 READY_NOW" tone="primary" variant="pill"/>
                            </div>
                            <div style={{display: 'flex', alignItems: 'center', gap: 12}}>
                                <span style={{fontSize: 12, color: 'var(--nq-text-tertiary)'}}>行情惯例</span>
                                <Segmented<MarketConvention>
                                    value={convention}
                                    onChange={setConvention}
                                    options={[
                                        {label: '绿涨红跌 (INTL_CRYPTO)', value: 'INTL_CRYPTO'},
                                        {label: '红涨绿跌 (CN_STOCK)', value: 'CN_STOCK'},
                                    ]}
                                />
                                <Button size="small" onClick={() => setRefreshTick((tick) => tick + 1)}>
                                    手动刷新
                                </Button>
                            </div>
                        </>
                    }
                    riskBanner={
                        riskOpen ? (
                            <RiskBanner
                                severity="danger"
                                message="演示:全局风险阻断生效,LIVE 下单链路保持 disabled(当前 Gate 不允许真实交易)。"
                                actions={
                                    <Button size="small" danger>
                                        查看规则
                                    </Button>
                                }
                                onDismiss={() => setRiskOpen(false)}
                            />
                        ) : undefined
                    }
                >
                    <div style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                        <section className="nq-ds-demo__section">
                            <h3 className="nq-ds-demo__section-title">StatusTag — 实体状态(dot / pill)</h3>
                            <div className="nq-ds-demo__row">
                                {STATUS_DEMOS.map((item) => (
                                    <StatusTag key={`dot-${item.label}`} label={item.label} tone={item.tone}/>
                                ))}
                            </div>
                            <div className="nq-ds-demo__row" style={{marginTop: 12}}>
                                {STATUS_DEMOS.map((item) => (
                                    <StatusTag
                                        key={`pill-${item.label}`}
                                        label={item.label}
                                        tone={item.tone}
                                        variant="pill"
                                    />
                                ))}
                            </div>
                        </section>

                        <section className="nq-ds-demo__section">
                            <h3 className="nq-ds-demo__section-title">
                                EnvironmentBadge — 环境/读写边界(LIVE 高危样式必须区别于 PAPER)
                            </h3>
                            <div className="nq-ds-demo__row">
                                {ENV_DEMOS.map((env) => (
                                    <EnvironmentBadge key={env} env={env}/>
                                ))}
                            </div>
                        </section>

                        <section className="nq-ds-demo__section">
                            <h3 className="nq-ds-demo__section-title">RiskBanner — 页级阻断/警报(常驻原因区)</h3>
                            <div style={{display: 'flex', flexDirection: 'column', gap: 8}}>
                                <RiskBanner severity="info" message="提示:数据回填进行中,部分指标稍后更新。"/>
                                <RiskBanner
                                    severity="warning"
                                    message="警告:Strategy Scheduler 心跳延迟,已降频轮询。"
                                    actions={<Button size="small">查看调度</Button>}
                                />
                                <RiskBanner severity="danger" message="阻断:账户对账失败,已暂停相关策略下单。"/>
                            </div>
                        </section>

                        <section className="nq-ds-demo__section">
                            <h3 className="nq-ds-demo__section-title">
                                DataFreshness — 数据源新鲜度(断流不静默,显式标状态)· 刷新计数 #{refreshTick}
                            </h3>
                            <div className="nq-ds-demo__grid">
                                {FRESHNESS_DEMOS.map((item) => (
                                    <div key={item.source} className="nq-ds-demo__section" style={{padding: 8}}>
                                        <DataFreshness source={item.source} state={item.state} detail={item.detail}/>
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className="nq-ds-demo__section">
                            <h3 className="nq-ds-demo__section-title">
                                行情方向 + 数字排版(独立于 success/danger;tabular-nums 等宽;惯例切换翻转)
                            </h3>
                            <div className="nq-ds-demo__row">
                                <span className="nq-num nq-up" style={{fontSize: 18, fontWeight: 600}}>
                                    +1,284.50
                                </span>
                                <span className="nq-num nq-down" style={{fontSize: 18, fontWeight: 600}}>
                                    −642.18
                                </span>
                                <span className="nq-num nq-flat" style={{fontSize: 18, fontWeight: 600}}>
                                    0.00
                                </span>
                            </div>
                            <div className="nq-ds-demo__row" style={{marginTop: 16}}>
                                <div className="nq-ds-demo__swatch">
                                    <span
                                        className="nq-ds-demo__swatch-chip"
                                        style={{background: candle.upColor}}
                                    />
                                    K 线上涨 (upColor)
                                </div>
                                <div className="nq-ds-demo__swatch">
                                    <span
                                        className="nq-ds-demo__swatch-chip"
                                        style={{background: candle.downColor}}
                                    />
                                    K 线下跌 (downColor)
                                </div>
                            </div>
                        </section>

                        <section className="nq-ds-demo__section">
                            <div
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    gap: 12,
                                    marginBottom: 12,
                                    flexWrap: 'wrap',
                                }}
                            >
                                <h3 className="nq-ds-demo__section-title" style={{margin: 0}}>
                                    表格密度 + 列格式(数字右对齐 tabular-nums;涨跌列随惯例翻转,独立于 success/danger)
                                </h3>
                                <Segmented<NqTableDensity>
                                    size="small"
                                    value={density}
                                    onChange={setDensity}
                                    options={TABLE_DENSITIES.map((item) => ({
                                        label: NQ_TABLE_DENSITY[item].label,
                                        value: item,
                                    }))}
                                />
                            </div>
                            <table className={nqTableClassName(density)} aria-label="表格密度自检">
                                <thead>
                                    <tr>
                                        <th>标的</th>
                                        <th className="nq-ds-col-nowrap">状态</th>
                                        <th className="nq-ds-col-num">最新价</th>
                                        <th className="nq-ds-col-num">持仓</th>
                                        <th className="nq-ds-col-num">仓位占比</th>
                                        <th className="nq-ds-col-num">浮动盈亏</th>
                                        <th className="nq-ds-col-num">日涨跌幅</th>
                                        <th className="nq-ds-col-num">更新时间</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {SAMPLE_TABLE_ROWS.map((row) => (
                                        <tr key={row.symbol}>
                                            <td>
                                                <span style={{fontFamily: 'var(--nq-font-mono)'}}>{row.symbol}</span>
                                            </td>
                                            <td className="nq-ds-col-nowrap">
                                                <StatusCell status={row.status}/>
                                            </td>
                                            <td className="nq-ds-col-num">
                                                <MoneyCell value={row.last} precision={2} currency="USDT"/>
                                            </td>
                                            <td className="nq-ds-col-num">
                                                <NumberCell value={row.qty} precision={4}/>
                                            </td>
                                            <td className="nq-ds-col-num">
                                                <PercentCell value={row.weight} signed={false}/>
                                            </td>
                                            <td className="nq-ds-col-num">
                                                <ChangeCell value={row.pnl} precision={2}/>
                                            </td>
                                            <td className="nq-ds-col-num">
                                                <ChangeCell value={row.changePct} percent arrow/>
                                            </td>
                                            <td className="nq-ds-col-num">{row.updatedAt}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </section>

                        <section className="nq-ds-demo__section">
                            <h3 className="nq-ds-demo__section-title">
                                ECharts 同色板(theme='nq')· 惯例切换一处生效
                            </h3>
                            <div
                                style={{
                                    display: 'grid',
                                    gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                                    gap: 16,
                                }}
                            >
                                <div>
                                    <div style={{fontSize: 12, color: 'var(--nq-text-tertiary)', marginBottom: 4}}>
                                        权益曲线(primary)
                                    </div>
                                    <MiniChart option={equityOption} convention={convention}/>
                                </div>
                                <div>
                                    <div style={{fontSize: 12, color: 'var(--nq-text-tertiary)', marginBottom: 4}}>
                                        区间 PnL(涨跌色随惯例翻转)
                                    </div>
                                    <MiniChart option={pnlOption} convention={convention}/>
                                </div>
                            </div>
                        </section>
                    </div>
                </AppShell>
            </div>
        </ConfigProvider>
    );
}
