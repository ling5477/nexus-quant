import {Alert, Button, Card, Empty, Popover, Space, Spin, Tabs, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {useSearchParams} from 'react-router-dom';
import {formatApiError} from '@/api/errors';
import {
    invalidLegacyFilters,
    legacyFilterDefinitions,
    updateLegacyParams,
} from '@/features/paper-trading/legacy-analysis';
import {useLegacyPaperAnalysis} from '@/features/paper-trading/hooks/useLegacyPaperAnalysis';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {LegacyAnalysisRelations} from './LegacyAnalysisControls';
import {PaperExecutionDiagnosticsDashboard} from './PaperExecutionDiagnosticsDashboard';
import {PaperStrategyEvaluationDashboard} from './PaperStrategyEvaluationDashboard';
import {PaperAutoReviewDashboard} from './PaperAutoReviewDashboard';
import './LegacyPaperAnalysisWorkspace.css';
import {PaperPortfolioDashboard} from './PaperPortfolioDashboard';
import {PaperRiskDrawdownDashboard} from './PaperRiskDashboard';
import {PaperStrategyRankingDashboard} from './PaperStrategyRankingDashboard';

/** 展示同一历史快照的诊断、评估与复盘；读取失败时隐藏旧事实，不能展示为成功空态。 */
export function LegacyPaperAnalysisWorkspace({
    kind,
}: {
    kind: 'diagnostics' | 'reviews' | 'portfolio';
}) {
    const {t, i18n} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    const query = useLegacyPaperAnalysis();
    const snapshot = query.isError ? undefined : query.data;
    const viewKey = kind === 'portfolio' ? 'legacyPortfolioView' : 'legacyView';
    const view = params.get(viewKey) ?? (kind === 'portfolio' ? 'overview' : 'reviews');
    const allowedViews =
        kind === 'portfolio' ? ['overview', 'risk', 'ranking'] : ['reviews', 'evaluations'];
    const invalid =
        invalidLegacyFilters(params).length > 0 ||
        (kind !== 'diagnostics' && !allowedViews.includes(view));
    const readState = {
        error: query.error,
        isFetching: query.isFetching,
        refetch: () => query.refetch(),
    };
    return (
        <Space
            direction="vertical"
            size={8}
            style={{width: '100%', minWidth: 0}}
            className="legacy-paper-analysis"
            data-testid={`legacy-analysis-${kind}`}
        >
            <div className="legacy-analysis-meta">
                <Space wrap size={[8, 6]}>
                    <Button
                        size="small"
                        loading={query.isFetching}
                        onClick={() => void query.refetch()}
                    >
                        {t('pages:legacyAnalysis.refresh')}
                    </Button>
                    {snapshot && (
                        <Typography.Text type="secondary">
                            {t('pages:legacyAnalysis.window', {
                                count: snapshot.includedRuns,
                                max: snapshot.maxRuns,
                            })}
                            {' · '}
                            {t('pages:legacyAnalysis.readAt', {
                                time: formatDateTime(snapshot.generatedAt),
                            })}
                        </Typography.Text>
                    )}
                    {snapshot?.hasMore && (
                        <Tag color="gold" title={t('pages:legacyAnalysis.windowLimited')}>
                            {t('pages:legacyPortfolio.earlierRuns')}
                        </Tag>
                    )}
                    <Popover
                        trigger="click"
                        title={t('pages:legacyPortfolio.scopeDetails')}
                        content={
                            <div style={{maxWidth: 360}}>
                                <Typography.Paragraph>
                                    {t('pages:legacyAnalysis.windowMeaning')}
                                </Typography.Paragraph>
                                <Typography.Paragraph>
                                    {t('pages:legacyPortfolio.sampleScope')}
                                </Typography.Paragraph>
                                {!i18n.resolvedLanguage?.startsWith('zh') && (
                                    <Typography.Paragraph>
                                        {t('pages:legacyAnalysis.sourceLanguage')}
                                    </Typography.Paragraph>
                                )}
                            </div>
                        }
                    >
                        <Button size="small" type="text">
                            {t('pages:legacyPortfolio.scopeDetails')}
                        </Button>
                    </Popover>
                </Space>
            </div>
            {snapshot && (
                <Alert
                    type="warning"
                    showIcon
                    data-testid="legacy-valuation-unavailable"
                    message={t('pages:legacyPortfolio.unitUnavailable')}
                />
            )}
            {query.isError && (
                <Alert
                    type="error"
                    showIcon
                    message={formatApiError(query.error as AppApiError)}
                    action={
                        <Button onClick={() => void query.refetch()}>{t('pages:retry')}</Button>
                    }
                />
            )}
            {query.isPending && <Spin />}
            {invalid ? (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:legacyAnalysis.invalidFilters')}
                    action={
                        <Button
                            onClick={() =>
                                setParams(
                                    updateLegacyParams(
                                        params,
                                        Object.fromEntries(
                                            [
                                                ...Object.keys(legacyFilterDefinitions),
                                                'legacyView',
                                                'legacyPortfolioView',
                                            ].map((key) => [key, null]),
                                        ),
                                    ),
                                )
                            }
                        >
                            {t('pages:legacyAnalysis.resetFilters')}
                        </Button>
                    }
                />
            ) : snapshot?.includedRuns === 0 ? (
                <Card className="page-section" variant="borderless">
                    <Empty description={t('pages:legacyAnalysis.empty')} />
                </Card>
            ) : (
                snapshot && (
                    <LegacyAnalysisRelations runs={snapshot.diagnostics.runDiagnostics}>
                        {kind === 'diagnostics' ? (
                            <PaperExecutionDiagnosticsDashboard
                                query={{...readState, data: snapshot.diagnostics}}
                            />
                        ) : (
                            <Tabs
                                activeKey={view}
                                onChange={(value) =>
                                    setParams(updateLegacyParams(params, {[viewKey]: value}))
                                }
                                items={
                                    kind === 'portfolio'
                                        ? [
                                              {
                                                  key: 'overview',
                                                  label: t('pages:legacyPortfolio.overview'),
                                                  children: (
                                                      <PaperPortfolioDashboard
                                                          query={{
                                                              ...readState,
                                                              data: snapshot.portfolio,
                                                          }}
                                                      />
                                                  ),
                                              },
                                              {
                                                  key: 'risk',
                                                  label: t('pages:legacyPortfolio.risk'),
                                                  children: (
                                                      <PaperRiskDrawdownDashboard
                                                          runs={snapshot.runs}
                                                          query={{
                                                              ...readState,
                                                              data: snapshot.portfolio,
                                                          }}
                                                      />
                                                  ),
                                              },
                                              {
                                                  key: 'ranking',
                                                  label: t('pages:legacyPortfolio.ranking'),
                                                  children: (
                                                      <PaperStrategyRankingDashboard
                                                          query={{
                                                              ...readState,
                                                              data: snapshot.portfolio,
                                                          }}
                                                      />
                                                  ),
                                              },
                                          ]
                                        : [
                                              {
                                                  key: 'reviews',
                                                  label: t('pages:paperAutomatedReview'),
                                                  children: (
                                                      <PaperAutoReviewDashboard
                                                          query={{
                                                              ...readState,
                                                              data: snapshot.reviews,
                                                          }}
                                                      />
                                                  ),
                                              },
                                              {
                                                  key: 'evaluations',
                                                  label: t('pages:paperStrategyEvaluation'),
                                                  children: (
                                                      <PaperStrategyEvaluationDashboard
                                                          query={{
                                                              ...readState,
                                                              data: snapshot.evaluations,
                                                          }}
                                                      />
                                                  ),
                                              },
                                          ]
                                }
                            />
                        )}
                    </LegacyAnalysisRelations>
                )
            )}
        </Space>
    );
}
