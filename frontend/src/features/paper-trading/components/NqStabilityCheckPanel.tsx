import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n/index';
import {App, Button, Card, Space, Typography} from 'antd';

import {showApiError} from '@/api/errors';
import {
    NqDataTable,
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    nqNumericColumn,
} from '@/components/nq/index';
import {
    useGenerateStabilityCheckMutation,
    usePaperStabilityChecksQuery,
} from '@/features/paper-trading/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunStabilityCheckItem} from '@/features/paper-trading/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/** 保留旧运行的心跳与元数据摘要；历史启发式标记不等于实测在线率或稳定性资格，真实证据来自canonical SIM。 */
interface NqStabilityCheckPanelProps {
    paperRunId: string;
}

export function NqStabilityCheckPanel({paperRunId}: NqStabilityCheckPanelProps) {
    useTranslation('pages');
    const {message} = App.useApp();
    const stabilityChecksQuery = usePaperStabilityChecksQuery(paperRunId);
    const generateStabilityCheckMutation = useGenerateStabilityCheckMutation();

    const data = stabilityChecksQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title={t('pages:legacyMonitor.stabilityTitle')}
            extra={
                <Button
                    size="small"
                    type="primary"
                    ghost
                    loading={generateStabilityCheckMutation.isPending}
                    onClick={() => {
                        const end = new Date();
                        const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
                        generateStabilityCheckMutation.mutate(
                            {
                                paperRunId,
                                request: {
                                    checkWindowStart: start.toISOString(),
                                    checkWindowEnd: end.toISOString(),
                                },
                            },
                            {
                                onSuccess: () =>
                                    message.success(t('pages:legacyMonitor.recorded')),
                                onError: (err) => showApiError(err as AppApiError, message),
                            },
                        );
                    }}
                >
                    {t('pages:legacyMonitor.recordStability')}
                </Button>
            }
        >
            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {t('pages:legacyMonitor.stability')}
                </Typography.Text>
                {stabilityChecksQuery.isFetching && data.length === 0 ? (
                    <NqLoadingState />
                ) : stabilityChecksQuery.error ? (
                    <NqErrorState
                        error={stabilityChecksQuery.error as AppApiError}
                        onRetry={() => stabilityChecksQuery.refetch()}
                    />
                ) : data.length === 0 ? (
                    <NqEmptyState description={t('pages:noStabilityChecksForThisPaperRun')} />
                ) : (
                    <NqDataTable<PaperRunStabilityCheckItem>
                        rowKey="stabilityCheckId"
                        pagination={false}
                        dataSource={data}
                        scroll={{x: 920, y: 240}}
                        columns={[
                            {
                                title: t('pages:legacyMonitor.recordState'),
                                dataIndex: 'status',
                                key: 'status',
                                width: 100,
                                render: (v: string) => (
                                    <StatusTag
                                        title=""
                                        variant="pill"
                                        status={v}
                                        tone="neutral"
                                    />
                                ),
                            },
                            nqNumericColumn({
                                title: t('pages:legacyMonitor.measuredUptime'),
                                dataIndex: 'uptimeRatio',
                                key: 'uptimeRatio',
                                width: 100,
                                render: () => (
                                    <Typography.Text type="secondary">
                                        {t('pages:legacyMonitor.unavailableUptime')}
                                    </Typography.Text>
                                ),
                            }),
                            nqNumericColumn({
                                title: t('pages:heartbeat'),
                                dataIndex: 'heartbeatCount',
                                key: 'heartbeatCount',
                                width: 80,
                            }),
                            nqNumericColumn({
                                title: t('pages:alert'),
                                dataIndex: 'alertCount',
                                key: 'alertCount',
                                width: 80,
                            }),
                            nqNumericColumn({
                                title: t('pages:failedTriggers'),
                                dataIndex: 'failedFireCount',
                                key: 'failedFireCount',
                                width: 100,
                            }),
                            nqNumericColumn({
                                title: t('pages:recovery'),
                                dataIndex: 'recoveryCount',
                                key: 'recoveryCount',
                                width: 80,
                            }),
                            nqNumericColumn({
                                title: t('pages:dailyReport'),
                                dataIndex: 'reportCount',
                                key: 'reportCount',
                                width: 80,
                            }),
                            {
                                title: t('pages:windowStart'),
                                dataIndex: 'checkWindowStart',
                                key: 'checkWindowStart',
                                width: 170,
                                render: (v: string) => formatDateTime(v),
                            },
                            {
                                title: t('pages:windowEnd'),
                                dataIndex: 'checkWindowEnd',
                                key: 'checkWindowEnd',
                                width: 170,
                                render: (v: string) => formatDateTime(v),
                            },
                        ]}
                    />
                )}
            </Space>
        </Card>
    );
}
