import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useEChart} from '@/nq-design-system/charts/useEChart';
import {buildNqLineChartBaseOption} from '@/nq-design-system/theme/nqChartTheme';
import {nqColor} from '@/theme/tokens';
import {formatDateTime} from '@/utils/formatters';
import {decimalDisplay} from '@/utils/decimal-display';
import type {AccountEquitySample} from '../types/performance';

/** 一张有界权益图；时间轴保留实际间隔，缺测不插值，精确明细由相邻表格提供。 */
export function AccountEquityChart({samples}: {samples: AccountEquitySample[]}) {
    return <EquityCurveChart samples={samples} />;
}

/** 图形仅消费有来源的时间和值，账户与独立SIM各自传入覆盖，不在组件内合并或生成序列。 */
export function EquityCurveChart({
    samples,
    gapSeconds = 1800,
    labelKey = 'accountPerformance.chartLabel',
}: {
    samples: Array<{sampledAt: string; equity: string | null}>;
    gapSeconds?: number;
    labelKey?: string;
}) {
    const {t, i18n} = useTranslation();
    const option = useMemo(() => {
        const base = buildNqLineChartBaseOption();
        const points: Array<[number, number | null]> = [];
        samples.forEach((sample, index) => {
            const at = Date.parse(sample.sampledAt);
            if (index > 0 && at - Date.parse(samples[index - 1].sampledAt) > gapSeconds * 1000)
                points.push([at - 1, null]);
            points.push([at, sample.equity === null ? null : Number(sample.equity)]);
        });
        return {
            ...base,
            animation: false,
            grid: {...base.grid, top: 20, bottom: 12},
            tooltip: {
                ...base.tooltip,
                renderMode: 'richText',
                confine: true,
                hideDelay: 0,
                alwaysShowContent: false,
                backgroundColor: 'rgba(11,23,42,.18)',
                formatter: (items: {data: [number, number | null]}[]) => {
                    const sample = samples.find(
                        (s) => Date.parse(s.sampledAt) === items[0]?.data[0],
                    );
                    return sample
                        ? `${formatDateTime(sample.sampledAt)}\n${t('accountPerformance.equity')}: ${sample.equity ?? '—'} USDT`
                        : '';
                },
            },
            xAxis: {
                ...base.xAxis,
                type: 'time',
                axisLabel: {
                    ...base.xAxis.axisLabel,
                    // 时间刻度分行并隐藏重叠项；小屏保留日期与时分，完整秒值仍由悬停及明细提供。
                    hideOverlap: true,
                    formatter: (value: number) =>
                        [
                            new Date(value).toLocaleDateString(i18n.resolvedLanguage, {
                                month: '2-digit',
                                day: '2-digit',
                            }),
                            new Date(value).toLocaleTimeString(i18n.resolvedLanguage, {
                                hour: '2-digit',
                                minute: '2-digit',
                                hour12: false,
                            }),
                        ].join('\n'),
                },
            },
            yAxis: {
                ...base.yAxis,
                axisLabel: {
                    ...base.yAxis.axisLabel,
                    formatter: (value: number) => decimalDisplay(String(value), 2),
                },
            },
            series: [
                {
                    name: t('accountPerformance.equity'),
                    type: 'line',
                    data: points,
                    connectNulls: false,
                    showSymbol: true,
                    symbolSize: 4,
                    lineStyle: {width: 2, color: nqColor.primary},
                    itemStyle: {color: nqColor.primary},
                    areaStyle: {color: nqColor.primary, opacity: 0.07},
                },
            ],
        };
    }, [samples, gapSeconds, t, i18n.resolvedLanguage]);
    const ref = useEChart(option);
    return (
        <div
            ref={ref}
            className="nq-chart"
            role="img"
            aria-label={t(labelKey)}
            style={{height: 260}}
        />
    );
}
