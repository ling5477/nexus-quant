import {
    Alert,
    Button,
    Card,
    Col,
    Drawer,
    Empty,
    Form,
    Grid,
    Input,
    List,
    Row,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {formatApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {SCHEDULE_STATUS_OPTIONS} from '@/constants/filter-options';
import {useScheduleListQuery} from '@/features/schedules/hooks/useScheduleListQuery';
import {useAuthStore} from '@/store/auth-store';
import {StrategyDefinitionPicker} from '@/features/strategies/components/StrategyDefinitionPicker';
import {ScheduleCreationWorkspace} from '@/features/schedules/components/ScheduleCreationWorkspace';
import {StrategyScheduleDetails} from '@/features/schedules/components/StrategyScheduleDetails';
import {
    defaultStrategyScheduleListFilters,
    type StrategyScheduleListFilters,
    type StrategyScheduleSummary,
} from '@/features/schedules/types/schedules';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';

/** 计划目录使用服务端筛选与真实总数；详情返回后保留当前查询，手机保留主要调度信息。 */
export function SchedulesPage() {
    const {t} = useTranslation('pages');
    const screens = Grid.useBreakpoint();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [form] = useLocalizedForm<StrategyScheduleListFilters>();
    const [filters, setFilters] = useState(defaultStrategyScheduleListFilters);
    const [revision, setRevision] = useState(0);
    const [page, setPage] = useState(1);
    const [params, setParams] = useSearchParams();
    const selectedId = params.get('scheduleId');
    const source = params.get('strategyId');
    useEffect(() => {
        if (source) {
            const next = {...defaultStrategyScheduleListFilters, strategyId: source};
            form.setFieldsValue(next);
            setFilters(next);
            setPage(1);
            setRevision((value) => value + 1);
        }
    }, [source, form]);
    const query = useScheduleListQuery({...filters, limit: 10, offset: (page - 1) * 10}, revision);
    const open = (id: string | null) => {
        const next = new URLSearchParams(params);
        if (id) next.set('scheduleId', id);
        else next.delete('scheduleId');
        setParams(next);
    };
    const status = (row: StrategyScheduleSummary) => (
        <Tag color={row.enabled ? 'success' : 'default'}>
            {t(row.enabled ? 'enabled' : 'disabled')}
        </Tag>
    );
    const columns: ColumnsType<StrategyScheduleSummary> = [
        {
            title: t('scheduleId'),
            dataIndex: 'scheduleJobId',
            width: 250,
            render: (id: string) => <Typography.Text copyable>{id}</Typography.Text>,
        },
        {title: t('status'), key: 'status', width: 110, render: (_, row) => status(row)},
        {
            title: t('scheduleType'),
            dataIndex: 'scheduleType',
            width: 130,
            render: (value: string) => t(`scheduleCreation.type.${value}`, {defaultValue: value}),
        },
        {title: 'Cron', dataIndex: 'cronExpr', width: 160},
        {title: t('timeZone'), dataIndex: 'timezone', width: 160},
        {
            title: t('lastTriggered'),
            dataIndex: 'lastTriggeredAt',
            width: 180,
            render: formatDateTime,
        },
        {title: t('updatedAt'), dataIndex: 'updatedAt', width: 180, render: formatDateTime},
        {
            title: t('actions'),
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, row) => (
                <Button type="link" onClick={() => open(row.scheduleJobId)}>
                    {t('viewDetails')}
                </Button>
            ),
        },
    ];
    const reset = () => {
        form.resetFields();
        setFilters(defaultStrategyScheduleListFilters);
        setPage(1);
        setRevision(0);
    };
    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title={t('schedules')}
                        description={t('scheduleWorkspaceHint')}
                        badge={t('schedules')}
                    />
                </Card>
                <Card className="page-section" bordered={false}>
                    <ScheduleCreationWorkspace
                        key={owner ?? 'anonymous'}
                        onView={(receipt) => {
                            const next = new URLSearchParams(params);
                            next.set('strategyId', receipt.request.strategyId);
                            next.set('scheduleId', receipt.result.scheduleJobId);
                            setParams(next);
                            const nextFilters = {
                                ...defaultStrategyScheduleListFilters,
                                strategyId: receipt.request.strategyId,
                            };
                            form.setFieldsValue(nextFilters);
                            setFilters(nextFilters);
                            setPage(1);
                            setRevision((value) => value + 1);
                        }}
                    />
                </Card>
                <Card className="page-section workspace-filter-panel" bordered={false} title={t('filters')}>
                    <Form
                        name="schedule-query"
                        form={form}
                        layout="vertical"
                        initialValues={defaultStrategyScheduleListFilters}
                        onFinish={(values) => {
                            setFilters({
                                strategyId: normalizeOptionalText(values.strategyId),
                                scheduleId: normalizeOptionalText(values.scheduleId),
                                status: normalizeOptionalText(values.status),
                                scheduleType: normalizeOptionalText(values.scheduleType),
                            });
                            setPage(1);
                            setRevision((value) => value + 1);
                        }}
                    >
                        <Row gutter={16}>
                            <Col xs={24} lg={9}>
                                <Form.Item
                                    name="strategyId"
                                    label={t('strategy')}
                                    rules={[{required: true, message: t('enterStrategyid')}]}
                                >
                                    <StrategyDefinitionPicker active purpose="schedule" />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} lg={5}>
                                <Form.Item name="scheduleId" label={t('scheduleExactIdentity')}>
                                    <Input maxLength={128} placeholder={t('optional')} />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} lg={5}>
                                <Form.Item name="status" label={t('scheduleConfigurationStatus')}>
                                    <Select
                                        allowClear
                                        options={SCHEDULE_STATUS_OPTIONS.map((option) => ({
                                            ...option,
                                            label: t(
                                                option.value === 'ENABLED' ? 'enabled' : 'disabled',
                                            ),
                                        }))}
                                        placeholder={t('allStatuses')}
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} lg={5}>
                                <Form.Item name="scheduleType" label={t('scheduleType')}>
                                    <Select
                                        allowClear
                                        placeholder={t('scheduleCreation.allTypes')}
                                        options={['CRON', 'INTERVAL', 'MANUAL'].map((value) => ({
                                            value,
                                            label: t(`scheduleCreation.type.${value}`),
                                        }))}
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                        <Space wrap>
                            <Button type="primary" htmlType="submit">
                                {t('search')}
                            </Button>
                            <Button onClick={reset}>{t('reset')}</Button>
                            <Button
                                disabled={!revision}
                                loading={query.isFetching}
                                onClick={() => void query.refetch()}
                            >
                                {t('refresh')}
                            </Button>
                        </Space>
                    </Form>
                </Card>
                <Card
                    className="page-section workspace-results-panel"
                    bordered={false}
                    title={t('results')}
                    extra={
                        revision && query.data ? (
                            <Typography.Text type="secondary">
                                {t('scheduleRecordCount', {total: query.data.total})}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {!revision ? (
                        <Empty description={t('scheduleCreation.selectAndSearch')} />
                    ) : query.isError ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('failedToQuerySchedules')}
                            description={formatApiError(query.error as AppApiError)}
                            action={
                                <Button onClick={() => void query.refetch()}>{t('retry')}</Button>
                            }
                        />
                    ) : !screens.md ? (
                        <List
                            loading={query.isFetching}
                            dataSource={query.data?.items ?? []}
                            rowKey="scheduleJobId"
                            locale={{emptyText: t('noSchedulesMatchTheseFilters')}}
                            pagination={{
                                current: page,
                                pageSize: 10,
                                total: query.data?.total ?? 0,
                                showSizeChanger: false,
                                onChange: setPage,
                            }}
                            renderItem={(row) => (
                                <List.Item data-schedule-id={row.scheduleJobId}>
                                    <Card
                                        size="small"
                                        style={{width: '100%'}}
                                        title={
                                            <Typography.Text
                                                copyable
                                                style={{overflowWrap: 'anywhere'}}
                                            >
                                                {row.scheduleJobId}
                                            </Typography.Text>
                                        }
                                    >
                                        <Space direction="vertical" style={{display: 'flex'}}>
                                            {status(row)}
                                            <Typography.Text>
                                                {t('scheduleType')}:{' '}
                                                {t(`scheduleCreation.type.${row.scheduleType}`, {
                                                    defaultValue: row.scheduleType,
                                                })}
                                            </Typography.Text>
                                            <Typography.Text>
                                                Cron: {row.cronExpr || '—'}
                                            </Typography.Text>
                                            <Typography.Text>
                                                {t('timeZone')}: {row.timezone}
                                            </Typography.Text>
                                            <Typography.Text>
                                                {t('lastTriggered')}:{' '}
                                                {formatDateTime(row.lastTriggeredAt)}
                                            </Typography.Text>
                                            <Button block onClick={() => open(row.scheduleJobId)}>
                                                {t('viewDetails')}
                                            </Button>
                                        </Space>
                                    </Card>
                                </List.Item>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="scheduleJobId"
                            size="small"
                            columns={columns}
                            dataSource={query.data?.items ?? []}
                            loading={query.isFetching}
                            pagination={{
                                current: page,
                                pageSize: 10,
                                total: query.data?.total ?? 0,
                                showSizeChanger: false,
                                onChange: setPage,
                            }}
                            scroll={{x: 1270}}
                            locale={{emptyText: t('noSchedulesMatchTheseFilters')}}
                        />
                    )}
                </Card>
            </Space>
            <Drawer
                open={Boolean(selectedId)}
                width={900}
                title={t('scheduleDetails')}
                onClose={() => open(null)}
                destroyOnHidden
            >
                {selectedId && <StrategyScheduleDetails key={selectedId} scheduleId={selectedId} />}
            </Drawer>
        </>
    );
}
