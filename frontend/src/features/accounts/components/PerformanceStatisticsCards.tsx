import {Collapse} from 'antd';
import {useTranslation} from 'react-i18next';
import {NqMetricCard} from '@/components/nq/NqMetricCard';
import {decimalDisplay} from '@/utils/decimal-display';
import type {ClosedTradeOutcomes, PerformanceSharpe} from '../types/performance';

/** 精确数值与缺失原因同时显示；平仓计数只在成本基础完整时展示，不能把未知计数误当零。 */
export function PerformanceStatisticsCards({
    sharpe,
    outcomes,
}: {
    sharpe: PerformanceSharpe;
    outcomes: ClosedTradeOutcomes;
}) {
    const {t} = useTranslation();
    const reason = (status: string) =>
        t(`performanceStatistics.states.${status}`, {
            defaultValue: t('performanceStatistics.unavailable'),
        });
    const known = outcomes.status === 'AVAILABLE' || outcomes.status === 'NO_CLOSED_TRADES';
    return (
        <>
            <div className="sim-analysis-metrics" data-testid="performance-statistics">
                <NqMetricCard
                    label={t('performanceStatistics.sharpe')}
                    value={decimalDisplay(sharpe.value, 4)}
                    footer={
                        sharpe.status === 'AVAILABLE'
                            ? t('performanceStatistics.sharpeSamples', {
                                  count: sharpe.returnCount,
                                  seconds: sharpe.samplingSeconds,
                              })
                            : reason(sharpe.status)
                    }
                />
                <NqMetricCard
                    label={t('performanceStatistics.winRate')}
                    value={
                        outcomes.winRate === null
                            ? '—'
                            : decimalDisplay(outcomes.winRate, 4, 2) + '%'
                    }
                    footer={
                        known
                            ? t('performanceStatistics.closedCounts', {
                                  wins: outcomes.winningCount,
                                  losses: outcomes.losingCount,
                                  flats: outcomes.flatCount,
                              })
                            : reason(outcomes.status)
                    }
                />
                <NqMetricCard
                    label={t('performanceStatistics.profitLossRatio')}
                    value={decimalDisplay(outcomes.profitLossRatio, 4)}
                    footer={
                        outcomes.status === 'AVAILABLE' &&
                        outcomes.netLoss !== null &&
                        outcomes.profitLossRatio === null
                            ? t('performanceStatistics.noLoss')
                            : reason(outcomes.status)
                    }
                />
            </div>
            <Collapse
                size="small"
                style={{marginTop: 10}}
                items={[
                    {
                        key: 'method',
                        label: t('performanceStatistics.methodTitle'),
                        children: t('performanceStatistics.method'),
                    },
                ]}
            />
        </>
    );
}
