import {App, Button, Card, Space, Typography} from 'antd';

import {formatApiError} from '@/api/errors';
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
    const {message} = App.useApp();
    const stabilityChecksQuery = usePaperStabilityChecksQuery(paperRunId);
    const generateStabilityCheckMutation = useGenerateStabilityCheckMutation();

    const data = stabilityChecksQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title="稳定性验收"
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
                            {onSuccess: () => message.success('稳定性验收已生成。'), onError: (err) => message.error(formatApiError(err as AppApiError))},
                        );
                    }}
                >
                    生成最近 24h 稳定性验收
                </Button>
            )}
        >
            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    第一版口径：有心跳 + 无 CRITICAL 未处理告警 + 无失败触发 = PASSED；不等同于正式 7 天稳定性验收。
                </Typography.Text>
                {stabilityChecksQuery.isFetching && data.length === 0 ? (
                    <NqLoadingState/>
                ) : stabilityChecksQuery.error ? (
                    <NqErrorState error={stabilityChecksQuery.error as AppApiError} onRetry={() => stabilityChecksQuery.refetch()}/>
                ) : data.length === 0 ? (
                    <NqEmptyState description="当前 Paper run 暂无稳定性验收。"/>
                ) : (
                    <NqDataTable<PaperRunStabilityCheckItem>
                        rowKey="stabilityCheckId"
                        pagination={false}
                        dataSource={data}
                        scroll={{x: 920, y: 240}}
                        columns={[
                            {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'PASSED' ? 'success' : v === 'PARTIAL' ? 'warning' : 'danger'}/>},
                            nqNumericColumn({title: '在线率', dataIndex: 'uptimeRatio', key: 'uptimeRatio', width: 100, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                            nqNumericColumn({title: '心跳', dataIndex: 'heartbeatCount', key: 'heartbeatCount', width: 80}),
                            nqNumericColumn({title: '告警', dataIndex: 'alertCount', key: 'alertCount', width: 80}),
                            nqNumericColumn({title: '失败触发', dataIndex: 'failedFireCount', key: 'failedFireCount', width: 100}),
                            nqNumericColumn({title: '恢复', dataIndex: 'recoveryCount', key: 'recoveryCount', width: 80}),
                            nqNumericColumn({title: '日报', dataIndex: 'reportCount', key: 'reportCount', width: 80}),
                            {title: '窗口开始', dataIndex: 'checkWindowStart', key: 'checkWindowStart', width: 170, render: (v: string) => formatDateTime(v)},
                            {title: '窗口结束', dataIndex: 'checkWindowEnd', key: 'checkWindowEnd', width: 170, render: (v: string) => formatDateTime(v)},
                        ]}
                    />
                )}
            </Space>
        </Card>
    );
}
