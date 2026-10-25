import {
    Alert,
    Button,
    Col,
    Collapse,
    Form,
    Grid,
    Input,
    Modal,
    Row,
    Space,
    Table,
    Typography,
} from 'antd';
import type {InputProps} from 'antd';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {
    useDatasetDetailQuery,
    useDatasetPageQuery,
} from '@/features/marketdata/hooks/useDatasetQueries';
import {formatDateTimeMilliseconds, normalizeOptionalText} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import type {DatasetPageRequest, MarketdataDataset} from '@/types/marketdata';

type Filters = Pick<
    DatasetPageRequest,
    'datasetId' | 'datasetName' | 'exchangeCode' | 'symbol' | 'interval' | 'qualityStatus'
>;
type Constraints = Omit<DatasetPageRequest, 'limit' | 'offset' | 'datasetId' | 'datasetName'>;
interface Props
    extends Pick<
        InputProps,
        'id' | 'disabled' | 'aria-label' | 'aria-describedby' | 'aria-invalid' | 'aria-required'
    > {
    value?: string;
    active: boolean;
    onChange?: (id: string | undefined) => void;
    onSelect?: (dataset: MarketdataDataset) => void;
    constraints?: Constraints;
}

/** 使用正式分页目录与精确详情；质量和来源保持可见，取消不修改已选身份或外层草稿。 */
export function MarketdataDatasetPicker({
    value,
    active,
    onChange,
    onSelect,
    constraints,
    ...inputProps
}: Props) {
    const {t} = useTranslation('pages');
    const compact = !Grid.useBreakpoint().lg;
    const [open, setOpen] = useState(false);
    const [form] = useLocalizedForm<Filters>();
    const [filters, setFilters] = useState<Filters>({});
    const [revision, setRevision] = useState(1);
    const [page, setPage] = useState(1);
    const showing = active && open;
    const selected = useDatasetDetailQuery(value, active);
    const results = useDatasetPageQuery(
        {...filters, ...constraints, limit: 20, offset: (page - 1) * 20},
        showing,
        revision,
    );
    const label =
        selected.data && selected.data.datasetId === value
            ? `${selected.data.datasetName} · ${selected.data.exchangeCode} ${selected.data.symbol} / ${selected.data.interval} · ${selected.data.qualityStatus}`
            : value || '';
    const reset = () => {
        form.resetFields();
        setFilters({});
        setPage(1);
        setRevision((current) => current + 1);
    };
    const choose = () => {
        reset();
        setOpen(true);
    };
    const dimensions: [keyof Filters, string, number][] = [
        ['datasetName', t('datasetName'), 255],
        ['datasetId', t('datasetId'), 36],
        ['exchangeCode', t('exchange'), 64],
        ['symbol', t('symbol'), 64],
        ['interval', t('interval'), 32],
        ['qualityStatus', t('qualityStatus'), 64],
    ];
    const visibleDimensions = dimensions.filter(([key]) => !constraints || !(key in constraints));
    const renderFilters = (fields: typeof dimensions) => (
        <Row gutter={12}>
            {fields.map(([key, title, maxLength]) => (
                <Col xs={24} md={8} key={key}>
                    <Form.Item name={key} label={title}>
                        <Input maxLength={maxLength} />
                    </Form.Item>
                </Col>
            ))}
        </Row>
    );
    return (
        <Space direction="vertical" size={8} style={{display: 'flex'}}>
            <Space.Compact block>
                <Input
                    {...inputProps}
                    readOnly
                    value={label}
                    title={label}
                    placeholder={t('datasetPickerPlaceholder')}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && !inputProps.disabled) {
                            event.preventDefault();
                            choose();
                        }
                    }}
                />
                <Button disabled={inputProps.disabled} onClick={choose}>
                    {t('datasetPickerChoose')}
                </Button>
                {value && (
                    <Button disabled={inputProps.disabled} onClick={() => onChange?.(undefined)}>
                        {t('researchPickerClear')}
                    </Button>
                )}
            </Space.Compact>
            {active && selected.isError && (
                <Alert
                    type="error"
                    showIcon
                    message={t('datasetPickerSelectedUnavailable')}
                    description={formatApiError(selected.error as AppApiError)}
                    action={<Button onClick={() => void selected.refetch()}>{t('retry')}</Button>}
                />
            )}
            {/* 第二层选择窗必须高于业务抽屉；关闭后取消请求，不保留整份目录缓存。 */}
            <Modal
                open={showing}
                title={t('datasetPickerTitle')}
                width={1100}
                zIndex={1100}
                footer={null}
                destroyOnHidden
                onCancel={() => setOpen(false)}
            >
                <Typography.Paragraph type="secondary">
                    {t('datasetPickerHelp')}
                </Typography.Paragraph>
                {constraints && (
                    <Typography.Paragraph type="secondary">
                        {t('datasetPickerFixedScope')}:{' '}
                        {Object.entries(constraints)
                            .map(
                                ([key, item]) =>
                                    `${key === 'maximumGapCount' ? t('gapCount') + ' ≤' : ''} ${item}`,
                            )
                            .join(' · ')}
                    </Typography.Paragraph>
                )}
                <Form
                    name="marketdata-dataset-picker"
                    form={form}
                    layout="vertical"
                    onFinish={(values) => {
                        const next: Filters = {};
                        for (const [key] of dimensions)
                            next[key] = normalizeOptionalText(values[key]);
                        setFilters(next);
                        setPage(1);
                        setRevision((current) => current + 1);
                    }}
                >
                    {renderFilters(compact ? visibleDimensions.slice(0, 2) : visibleDimensions)}
                    {/* 手机先保留对象查找和结果，其他筛选按需展开，避免六个输入挤出整个首屏。 */}
                    {compact && visibleDimensions.length > 2 && (
                        <Collapse
                            style={{marginBottom: 12}}
                            items={[
                                {
                                    key: 'more',
                                    label: t('datasetPickerMoreFilters'),
                                    children: renderFilters(visibleDimensions.slice(2)),
                                    forceRender: true,
                                },
                            ]}
                        />
                    )}
                    <Space style={{marginBottom: 16}}>
                        <Button type="primary" htmlType="submit">
                            {t('search')}
                        </Button>
                        <Button onClick={reset}>{t('reset')}</Button>
                    </Space>
                </Form>
                {results.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('datasetPickerLoadFailed')}
                        description={formatApiError(results.error as AppApiError)}
                        action={
                            <Button onClick={() => void results.refetch()}>{t('retry')}</Button>
                        }
                    />
                ) : (
                    <Table<MarketdataDataset>
                        tableLayout="fixed"
                        size="small"
                        rowKey="datasetId"
                        loading={results.isFetching}
                        dataSource={results.data?.items ?? []}
                        columns={[
                            {
                                title: t('dataset'),
                                key: 'dataset',
                                render: (_, row) => (
                                    <div style={{minWidth: 0}}>
                                        <Typography.Text
                                            style={{display: 'block', overflowWrap: 'anywhere'}}
                                        >
                                            {row.datasetName}
                                        </Typography.Text>
                                        <Typography.Text
                                            type="secondary"
                                            style={{display: 'block', overflowWrap: 'anywhere'}}
                                        >
                                            {row.datasetId}
                                        </Typography.Text>
                                        <Typography.Text
                                            type="secondary"
                                            style={{display: 'block', overflowWrap: 'anywhere'}}
                                        >
                                            {row.exchangeCode} / {row.marketType} · {row.symbol} /{' '}
                                            {row.interval}
                                        </Typography.Text>
                                        {compact && (
                                            <Typography.Text
                                                style={{display: 'block', overflowWrap: 'anywhere'}}
                                            >
                                                {row.status} / {row.qualityStatus} ·{' '}
                                                {t('datasetPickerBars', {count: row.barCount})} ·{' '}
                                                {t('gapCount')}: {row.gapCount} · {row.source}
                                            </Typography.Text>
                                        )}
                                    </div>
                                ),
                            },
                            {
                                title: t('datasetPickerTimeRange'),
                                key: 'range',
                                width: 230,
                                responsive: ['lg'],
                                render: (_, row) => (
                                    <div>
                                        {formatDateTimeMilliseconds(row.startTime)}
                                        <br />
                                        {formatDateTimeMilliseconds(row.endTime)}
                                    </div>
                                ),
                            },
                            {
                                title: t('qualityStatus'),
                                key: 'quality',
                                width: 190,
                                responsive: ['lg'],
                                render: (_, row) => (
                                    <div>
                                        {row.status} / {row.qualityStatus}
                                        <br />
                                        {t('datasetPickerBars', {count: row.barCount})} ·{' '}
                                        {t('gapCount')}: {row.gapCount}
                                        <br />
                                        {row.source}
                                    </div>
                                ),
                            },
                            {
                                title: t('actions'),
                                key: 'select',
                                width: 80,
                                render: (_, row) => (
                                    <Button
                                        type="link"
                                        size="small"
                                        onClick={() => {
                                            onChange?.(row.datasetId);
                                            onSelect?.(row);
                                            setOpen(false);
                                        }}
                                    >
                                        {t('researchPickerUse')}
                                    </Button>
                                ),
                            },
                        ]}
                        pagination={{
                            current: page,
                            pageSize: 20,
                            total: results.data?.total ?? 0,
                            showSizeChanger: false,
                            onChange: setPage,
                        }}
                        scroll={{y: compact ? 320 : 420}}
                        locale={{emptyText: t('datasetPickerEmpty')}}
                    />
                )}
            </Modal>
        </Space>
    );
}
