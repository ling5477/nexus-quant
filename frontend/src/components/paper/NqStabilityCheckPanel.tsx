import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {App, Button, Card, Space, Typography} from 'antd';

import {showApiError} from '@/api/errors';
import {NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, NqPercentText, NqStatusTag, nqNumericColumn} from '@/components/nq';
import {useGenerateStabilityCheckMutation, usePaperStabilityChecksQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperRunStabilityCheckItem} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

/**
 * NqStabilityCheckPanel — Paper Run 稳定性验收面板。
 *
 * 职责：生成并展示稳定性验收（默认最近 24h 窗口）。
 * 边界：复用既有稳定性接口，不新增 API；第一版口径为有心跳 + 无 CRITICAL 未处理告警 + 无失败触发 = PASSED，
 * 24 小时检查不等同于正式 7 天稳定性验收，文案必须如实标注，不夸大为最终通过。
 */
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
            title={t('pages:stabilityCheck')}
            extra={(
                <Button
                    size="small"
                    type="primary"
                    ghost
                    loading={generateStabilityCheckMutation.isPending}
                    onClick={() => {
                        const end = new Date();
                        const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
                        generateStabilityCheckMutation.mutate(
                            {paperRunId, request: {checkWindowStart: start.toISOString(), checkWindowEnd: end.toISOString()}},
                            {onSuccess: () => message.success(t('pages:stabilityCheckGenerated')), onError: (err) => showApiError(err as AppApiError, message)},
                        );
                    }}
                >
                    {t('pages:generateAStabilityCheckForTheLast24Hours')}</Button>
            )}
        >
            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {t('pages:initialCriteriaHeartbeatPresentNoUnresolvedCriticalAlertsAndNoFailedTriggersMeansPassedThisIsNotForm')}</Typography.Text>
                {stabilityChecksQuery.isFetching && data.length === 0 ? (
                    <NqLoadingState/>
                ) : stabilityChecksQuery.error ? (
                    <NqErrorState error={stabilityChecksQuery.error as AppApiError} onRetry={() => stabilityChecksQuery.refetch()}/>
                ) : data.length === 0 ? (
                    <NqEmptyState description={t('pages:noStabilityChecksForThisPaperRun')}/>
                ) : (
                    <NqDataTable<PaperRunStabilityCheckItem>
                        rowKey="stabilityCheckId"
                        pagination={false}
                        dataSource={data}
                        scroll={{x: 920, y: 240}}
                        columns={[
                            {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'PASSED' ? 'success' : v === 'PARTIAL' ? 'warning' : 'danger'}/>},
                            nqNumericColumn({title: t('pages:uptimeRate'), dataIndex: 'uptimeRatio', key: 'uptimeRatio', width: 100, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                            nqNumericColumn({title: t('pages:heartbeat'), dataIndex: 'heartbeatCount', key: 'heartbeatCount', width: 80}),
                            nqNumericColumn({title: t('pages:alert'), dataIndex: 'alertCount', key: 'alertCount', width: 80}),
                            nqNumericColumn({title: t('pages:failedTriggers'), dataIndex: 'failedFireCount', key: 'failedFireCount', width: 100}),
                            nqNumericColumn({title: t('pages:recovery'), dataIndex: 'recoveryCount', key: 'recoveryCount', width: 80}),
                            nqNumericColumn({title: t('pages:dailyReport'), dataIndex: 'reportCount', key: 'reportCount', width: 80}),
                            {title: t('pages:windowStart'), dataIndex: 'checkWindowStart', key: 'checkWindowStart', width: 170, render: (v: string) => formatDateTime(v)},
                            {title: t('pages:windowEnd'), dataIndex: 'checkWindowEnd', key: 'checkWindowEnd', width: 170, render: (v: string) => formatDateTime(v)},
                        ]}
                    />
                )}
            </Space>
        </Card>
    );
}
