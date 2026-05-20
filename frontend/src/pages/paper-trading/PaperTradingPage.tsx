import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    Modal,
    Row,
    Space,
    Table,
    Tabs,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {
    useCreatePaperTradingRunMutation,
    usePaperTradingDetailQuery,
    usePaperTradingListQuery,
    usePaperTradingOrdersQuery,
    usePaperTradingPositionsQuery,
    usePaperTradingTradesQuery,
    useStartPaperTradingRunMutation,
    useStopPaperTradingRunMutation,
} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultPaperTradingListFilters,
    type PaperTradingListFilters,
    type PaperTradingRunCreateRequest,
    type PaperTradingRunItem,
} from '@/types/paper-trading';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type PaperRunRow = PaperTradingRunItem;

const STATUS_COLOR: Record<string, string> = {
    CREATED: 'default',
    RUNNING: 'processing',
    STOPPED: 'warning',
    FAILED: 'error',
};

const DEFAULT_CREATE_VALUES: PaperTradingRunCreateRequest = {
    publishId: '',
    tradeEnv: 'SIM',
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    intervalCode: '1m',
    configSnapshotJson: '',
};

export function PaperTradingPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<PaperTradingListFilters>();
    const [createForm] = Form.useForm<PaperTradingRunCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<PaperTradingListFilters>(defaultPaperTradingListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedRow, setSelectedRow] = useState<PaperRunRow | null>(null);
    const [createOpen, setCreateOpen] = useState(false);

    const listQuery = usePaperTradingListQuery(
        {
            publishId: submittedFilters.publishId || undefined,
            status: submittedFilters.status || undefined,
        },
        searchVersion,
    );
    const detailQuery = usePaperTradingDetailQuery(selectedRow?.paperRunId ?? null);
    const ordersQuery = usePaperTradingOrdersQuery(selectedRow?.paperRunId ?? null);
    const tradesQuery = usePaperTradingTradesQuery(selectedRow?.paperRunId ?? null);
    const positionsQuery = usePaperTradingPositionsQuery(selectedRow?.paperRunId ?? null);
    const createMutation = useCreatePaperTradingRunMutation();
    const startMutation = useStartPaperTradingRunMutation();
    const stopMutation = useStopPaperTradingRunMutation();

    const hasSearched = searchVersion > 0;
    const visibleItems = listQuery.data ?? [];

    const columns: ColumnsType<PaperRunRow> = [
        {
            title: 'Paper Run ID',
            dataIndex: 'paperRunId',
            key: 'paperRunId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '发布 ID',
            dataIndex: 'publishId',
            key: 'publishId',
            width: 220,
            render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => <Tag color={STATUS_COLOR[value] ?? 'default'}>{value}</Tag>,
        },
        {
            title: '交易环境',
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 100,
        },
        {
            title: '交易所',
            dataIndex: 'exchangeCode',
            key: 'exchangeCode',
            width: 100,
        },
        {
            title: 'Symbol',
            dataIndex: 'symbol',
            key: 'symbol',
            width: 120,
        },
        {
            title: '周期',
            dataIndex: 'intervalCode',
            key: 'intervalCode',
            width: 80,
        },
        {
            title: '启动时间',
            dataIndex: 'startedAt',
            key: 'startedAt',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: '更新时间',
            dataIndex: 'updatedAt',
            key: 'updatedAt',
            width: 180,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 240,
            render: (_, record) => (
                <Space>
                    <Button type="link" size="small" onClick={() => setSelectedRow(record)}>
                        查看详情
                    </Button>
                    <Button
                        type="link"
                        size="small"
                        disabled={record.status !== 'CREATED'}
                        onClick={() => handleStart(record.paperRunId)}
                    >
                        启动
                    </Button>
                    <Button
                        type="link"
                        size="small"
                        danger
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
            onSuccess: () => {
                message.success('Paper run 已启动。');
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    const handleStop = (paperRunId: string) => {
        stopMutation.mutate(paperRunId, {
            onSuccess: () => {
                message.success('Paper run 已停止。');
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
            onSuccess: () => {
                message.success('Paper run 已创建。');
                setCreateOpen(false);
                createForm.resetFields();
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    return (
        <>
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <PageHero
                        title="模拟交易"
                        description="基于已发布策略版本创建 SIM/Paper Trading run，固化 publish/strategy version/dataset/param/config 快照，支持启动、停止与最小事实查询。"
                        badge="GateI-3"
                    />
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="查询区"
                    extra={(
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
                                    <Input placeholder="按 publishId 过滤，可空"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="状态" name="status">
                                    <Input placeholder="CREATED/RUNNING/STOPPED/FAILED"/>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
                <Card
                    className="page-section"
                    bordered={false}
                    title="Paper Trading 运行列表"
                    extra={hasSearched ?
                        <Typography.Text type="secondary">共 {visibleItems.length} 条记录</Typography.Text> : null}
                >
                    {!hasSearched ? (
                        <Empty description="点击查询后加载 Paper Trading run 列表。"/>
                    ) : listQuery.error ? (
                        <Alert
                            type="error"
                            showIcon
                            message="Paper Trading run 列表查询失败"
                            description={formatApiError(listQuery.error as AppApiError)}
                            action={(
                                <Button size="small" onClick={() => setSearchVersion((v) => v + 1)}>
                                    重试
                                </Button>
                            )}
                        />
                    ) : (
                        <Table
                            rowKey="paperRunId"
                            columns={columns}
                            dataSource={visibleItems}
                            loading={listQuery.isFetching}
                            pagination={{pageSize: 10, showSizeChanger: false}}
                            scroll={{x: 1500}}
                            locale={{emptyText: '当前筛选条件下没有 Paper Trading run。'}}
                        />
                    )}
                </Card>
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
                        <Input placeholder="SIM"/>
                    </Form.Item>
                    <Form.Item label="交易所" name="exchangeCode" rules={[{required: true}]}>
                        <Input placeholder="OKX 或 BINANCE"/>
                    </Form.Item>
                    <Form.Item label="市场类型" name="marketType" rules={[{required: true}]}>
                        <Input placeholder="SPOT"/>
                    </Form.Item>
                    <Form.Item label="Symbol" name="symbol" rules={[{required: true}]}>
                        <Input placeholder="BTC-USDT"/>
                    </Form.Item>
                    <Form.Item label="周期" name="intervalCode" rules={[{required: true}]}>
                        <Input placeholder="1m / 5m / 15m / 1h / 4h / 1d"/>
                    </Form.Item>
                    <Form.Item label="运行配置快照 JSON（可空）" name="configSnapshotJson">
                        <Input.TextArea rows={3} placeholder='{"feeRate":"0.001","slippageBps":"10"}'/>
                    </Form.Item>
                </Form>
            </Modal>

            <Drawer
                open={Boolean(selectedRow)}
                width={840}
                title="Paper Trading 详情"
                onClose={() => setSelectedRow(null)}
                destroyOnClose
            >
                {!selectedRow ? null : (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label="Paper Run ID">{selectedRow.paperRunId}</Descriptions.Item>
                            <Descriptions.Item label="发布 ID">{selectedRow.publishId}</Descriptions.Item>
                            <Descriptions.Item label="策略版本 ID">{selectedRow.strategyVersionId || '-'}</Descriptions.Item>
                            <Descriptions.Item label="状态">
                                <Tag color={STATUS_COLOR[selectedRow.status] ?? 'default'}>{selectedRow.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="交易环境">{selectedRow.tradeEnv}</Descriptions.Item>
                            <Descriptions.Item label="交易所">{selectedRow.exchangeCode}</Descriptions.Item>
                            <Descriptions.Item label="市场类型">{selectedRow.marketType}</Descriptions.Item>
                            <Descriptions.Item label="Symbol">{selectedRow.symbol}</Descriptions.Item>
                            <Descriptions.Item label="周期">{selectedRow.intervalCode}</Descriptions.Item>
                            <Descriptions.Item label="启动时间">{formatDateTime(selectedRow.startedAt)}</Descriptions.Item>
                            <Descriptions.Item label="停止时间">{formatDateTime(selectedRow.stoppedAt)}</Descriptions.Item>
                            <Descriptions.Item label="创建人">{selectedRow.createdBy}</Descriptions.Item>
                        </Descriptions>

                        {detailQuery.isLoading ? (
                            <Alert type="info" showIcon message="正在加载 Paper run 详情..."/>
                        ) : detailQuery.error ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="Paper run 详情加载失败"
                                description={formatApiError(detailQuery.error as AppApiError)}
                            />
                        ) : null}

                        <Tabs
                            items={[
                                {
                                    key: 'orders',
                                    label: '订单',
                                    children: (
                                        <PaperListSection
                                            isLoading={ordersQuery.isFetching}
                                            error={ordersQuery.error as AppApiError | null}
                                            isEmpty={(ordersQuery.data ?? []).length === 0}
                                            emptyText="当前 Paper run 暂无订单事实。"
                                        >
                                            <Table
                                                rowKey="paperOrderId"
                                                size="small"
                                                pagination={false}
                                                dataSource={ordersQuery.data ?? []}
                                                columns={[
                                                    {title: '订单 ID', dataIndex: 'paperOrderId', key: 'paperOrderId'},
                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                    {title: '类型', dataIndex: 'orderType', key: 'orderType', width: 80},
                                                    {title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100},
                                                    {title: '价格', dataIndex: 'price', key: 'price', width: 100},
                                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100},
                                                    {
                                                        title: '创建时间',
                                                        dataIndex: 'createdAt',
                                                        key: 'createdAt',
                                                        width: 180,
                                                        render: (value: string) => formatDateTime(value),
                                                    },
                                                ]}
                                            />
                                        </PaperListSection>
                                    ),
                                },
                                {
                                    key: 'trades',
                                    label: '成交',
                                    children: (
                                        <PaperListSection
                                            isLoading={tradesQuery.isFetching}
                                            error={tradesQuery.error as AppApiError | null}
                                            isEmpty={(tradesQuery.data ?? []).length === 0}
                                            emptyText="当前 Paper run 暂无成交事实。"
                                        >
                                            <Table
                                                rowKey="paperTradeId"
                                                size="small"
                                                pagination={false}
                                                dataSource={tradesQuery.data ?? []}
                                                columns={[
                                                    {title: '成交 ID', dataIndex: 'paperTradeId', key: 'paperTradeId'},
                                                    {title: '订单 ID', dataIndex: 'paperOrderId', key: 'paperOrderId'},
                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                    {title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100},
                                                    {title: '价格', dataIndex: 'price', key: 'price', width: 100},
                                                    {title: '手续费', dataIndex: 'fee', key: 'fee', width: 100},
                                                    {
                                                        title: '成交时间',
                                                        dataIndex: 'tradedAt',
                                                        key: 'tradedAt',
                                                        width: 180,
                                                        render: (value: string) => formatDateTime(value),
                                                    },
                                                ]}
                                            />
                                        </PaperListSection>
                                    ),
                                },
                                {
                                    key: 'positions',
                                    label: '持仓',
                                    children: (
                                        <PaperListSection
                                            isLoading={positionsQuery.isFetching}
                                            error={positionsQuery.error as AppApiError | null}
                                            isEmpty={(positionsQuery.data ?? []).length === 0}
                                            emptyText="当前 Paper run 暂无持仓事实。"
                                        >
                                            <Table
                                                rowKey="paperPositionId"
                                                size="small"
                                                pagination={false}
                                                dataSource={positionsQuery.data ?? []}
                                                columns={[
                                                    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 120},
                                                    {title: '数量', dataIndex: 'quantity', key: 'quantity', width: 120},
                                                    {title: '均价', dataIndex: 'avgPrice', key: 'avgPrice', width: 120},
                                                    {title: '已实现盈亏', dataIndex: 'realizedPnl', key: 'realizedPnl', width: 140},
                                                    {title: '未实现盈亏', dataIndex: 'unrealizedPnl', key: 'unrealizedPnl', width: 140},
                                                    {
                                                        title: '更新时间',
                                                        dataIndex: 'updatedAt',
                                                        key: 'updatedAt',
                                                        width: 180,
                                                        render: (value: string) => formatDateTime(value),
                                                    },
                                                ]}
                                            />
                                        </PaperListSection>
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
                            ]}
                        />
                    </Space>
                )}
            </Drawer>
        </>
    );
}

interface PaperListSectionProps {
    isLoading: boolean;
    error: AppApiError | null;
    isEmpty: boolean;
    emptyText: string;
    children: React.ReactNode;
}

function PaperListSection({isLoading, error, isEmpty, emptyText, children}: PaperListSectionProps) {
    if (isLoading) {
        return <Alert type="info" showIcon message="加载中..."/>;
    }
    if (error) {
        return (
            <Alert type="error" showIcon message="查询失败" description={formatApiError(error)}/>
        );
    }
    if (isEmpty) {
        return <Empty description={emptyText}/>;
    }
    return <>{children}</>;
}

function SnapshotBlock({title, content}: {title: string; content: string | null | undefined}) {
    return (
        <Card size="small" title={title}>
            <Typography.Paragraph
                copyable={Boolean(content)}
                style={{margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all'}}
            >
                {content || '-'}
            </Typography.Paragraph>
        </Card>
    );
}
