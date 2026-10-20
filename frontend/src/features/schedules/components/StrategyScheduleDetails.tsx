import {Alert, App, Button, Collapse, Descriptions, Space, Tag, Typography} from 'antd';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router-dom';
import {formatApiError} from '@/api/errors';
import {
    useScheduleDetailQuery,
    useUpdateScheduleStatusMutation,
} from '@/features/schedules/hooks/useScheduleListQuery';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';

/** 计划启停只反映调度配置；未知回执先读当前状态，不能自动重发或冒充原命令成功。 */
export function StrategyScheduleDetails({scheduleId}: {scheduleId: string}) {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const query = useScheduleDetailQuery(scheduleId);
    const mutation = useUpdateScheduleStatusMutation();
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const canWrite = roles.some((role) => role === 'ADMIN' || role === 'OPERATOR');
    const [unconfirmed, setUnconfirmed] = useState(false);
    const [failure, setFailure] = useState<AppApiError | null>(null);
    const refresh = async () => {
        const result = await query.refetch();
        if (result.isSuccess) {
            setUnconfirmed(false);
            setFailure(null);
        }
    };
    const change = (enabled: boolean) => {
        setFailure(null);
        mutation.mutate(
            {scheduleId, request: {enabled}},
            {
                onSuccess: () => {
                    setUnconfirmed(false);
                    void message.success(t(enabled ? 'scheduleEnabled' : 'scheduleDisabled'));
                },
                onError: (error) => {
                    const apiError = error as AppApiError;
                    setFailure(apiError);
                    setUnconfirmed(!apiError.status || apiError.status >= 500);
                },
            },
        );
    };
    if (query.isPending) return <Typography.Text>{t('loadingScheduleDetails')}</Typography.Text>;
    if (query.isError)
        return (
            <Alert
                type="error"
                showIcon
                message={t('failedToLoadScheduleDetails')}
                description={formatApiError(query.error as AppApiError)}
                action={<Button onClick={() => void refresh()}>{t('retry')}</Button>}
            />
        );
    const item = query.data;
    const identity = (value: string | null) =>
        value ? (
            <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                {value}
            </Typography.Text>
        ) : (
            '—'
        );
    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Space wrap>
                <Tag color={item.enabled ? 'success' : 'default'}>
                    {t(item.enabled ? 'enabled' : 'disabled')}
                </Tag>
                <Button loading={query.isFetching} onClick={() => void refresh()}>
                    {t('refreshDetails')}
                </Button>
                <Link to={`/runs?scheduleId=${encodeURIComponent(item.scheduleJobId)}`}>
                    {t('scheduleOpenRuns')}
                </Link>
            </Space>
            <Descriptions
                size="small"
                bordered
                column={{xs: 1, md: 2}}
                styles={{label: {whiteSpace: 'nowrap'}}}
            >
                <Descriptions.Item label={t('scheduleId')}>
                    {identity(item.scheduleJobId)}
                </Descriptions.Item>
                <Descriptions.Item label={t('strategyId')}>
                    {identity(item.strategyId)}
                </Descriptions.Item>
                <Descriptions.Item label={t('scheduleType')}>{item.scheduleType}</Descriptions.Item>
                <Descriptions.Item label="Cron">{item.cronExpr}</Descriptions.Item>
                <Descriptions.Item label={t('timeZone')}>{item.timezone}</Descriptions.Item>
                <Descriptions.Item label={t('lastTriggered')}>
                    {formatDateTime(item.lastTriggeredAt)}
                </Descriptions.Item>
                <Descriptions.Item label={t('exchange')}>{item.exchangeCode}</Descriptions.Item>
                <Descriptions.Item label={t('account')}>
                    {identity(item.accountId)}
                </Descriptions.Item>
                <Descriptions.Item label={t('tradingEnvironment')}>
                    {item.tradeEnv}
                </Descriptions.Item>
                <Descriptions.Item label={t('updatedAt')}>
                    {formatDateTime(item.updatedAt)}
                </Descriptions.Item>
                <Descriptions.Item label={t('createdAt')}>
                    {formatDateTime(item.createdAt)}
                </Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary">{t('scheduleStateMeaning')}</Typography.Text>
            {failure && (
                <Alert
                    type={unconfirmed ? 'warning' : 'error'}
                    showIcon
                    message={t(
                        unconfirmed ? 'scheduleStatusUnconfirmed' : 'scheduleStatusRejected',
                    )}
                    description={formatApiError(failure)}
                    action={<Button onClick={() => void refresh()}>{t('refreshDetails')}</Button>}
                />
            )}
            <Space wrap>
                <Button
                    type="primary"
                    disabled={
                        !canWrite ||
                        item.enabled ||
                        unconfirmed ||
                        mutation.isPending ||
                        query.isFetching
                    }
                    loading={mutation.isPending}
                    onClick={() => change(true)}
                >
                    {t('enableSchedule')}
                </Button>
                <Button
                    danger
                    disabled={
                        !canWrite ||
                        !item.enabled ||
                        unconfirmed ||
                        mutation.isPending ||
                        query.isFetching
                    }
                    loading={mutation.isPending}
                    onClick={() => change(false)}
                >
                    {t('disableSchedule')}
                </Button>
            </Space>
            {!canWrite && (
                <Typography.Text type="secondary">{t('scheduleReadOnlyRole')}</Typography.Text>
            )}
            <Collapse
                items={[
                    {
                        key: 'technical',
                        label: t('scheduleTechnicalDetails'),
                        children: (
                            <Descriptions size="small" column={1}>
                                <Descriptions.Item label={t('deduplicationScope')}>
                                    {item.dedupScope}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('windowConfiguration')}>
                                    <Typography.Text
                                        style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}
                                    >
                                        {item.windowConfig}
                                    </Typography.Text>
                                </Descriptions.Item>
                            </Descriptions>
                        ),
                    },
                ]}
            />
        </Space>
    );
}
