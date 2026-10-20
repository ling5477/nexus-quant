import {Alert, Button, Col, Form, Grid, Input, Modal, Row, Space, Table, Typography} from 'antd';
import type {InputProps} from 'antd';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {
    useStrategyByIdQuery,
    useStrategyListQuery,
} from '@/features/strategies/hooks/useStrategyListQuery';
import {
    defaultStrategyListFilters,
    type StrategyDefinitionListItem,
} from '@/features/strategies/types/strategies';
import {normalizeOptionalText} from '@/utils/formatters';

interface Filters {
    strategyName: string;
    strategyCode: string;
    strategyId: string;
}

const emptyFilters: Filters = {strategyName: '', strategyCode: '', strategyId: ''};

interface StrategyDefinitionPickerProps
    extends Pick<
        InputProps,
        'id' | 'disabled' | 'aria-describedby' | 'aria-invalid' | 'aria-required'
    > {
    value?: string;
    active: boolean;
    purpose?: 'research' | 'schedule' | 'version';
    onChange?: (value: string | undefined) => void;
}

/** 按正式定义身份选择研究或计划来源；停用定义可配置，选择本身不改变定义启停。 */
export function StrategyDefinitionPicker({
    value,
    active,
    purpose = 'research',
    onChange,
    ...inputProps
}: StrategyDefinitionPickerProps) {
    const {t} = useTranslation(['pages', 'common']);
    const screens = Grid.useBreakpoint();
    const compact = !screens.lg;
    const [open, setOpen] = useState(false);
    const [form] = useLocalizedForm<Filters>();
    const [filters, setFilters] = useState(emptyFilters);
    const [revision, setRevision] = useState(1);
    const [page, setPage] = useState(1);
    const showing = active && open;
    const selectedId = value?.trim() || null;
    const selected = useStrategyByIdQuery(selectedId, active);
    const results = useStrategyListQuery(
        {...defaultStrategyListFilters, ...filters, limit: 20, offset: (page - 1) * 20},
        showing ? revision : 0,
    );
    const selectedItem = selected.data;
    const selectedLabel =
        selectedItem && selectedItem.strategyId === selectedId
            ? `${selectedItem.strategyName} · ${selectedItem.strategyCode} · ${selectedItem.exchangeCode} / ${selectedItem.tradeEnv}`
            : value || '';
    const reset = () => {
        form.resetFields();
        setFilters(emptyFilters);
        setPage(1);
        setRevision((current) => current + 1);
    };
    const choose = () => {
        reset();
        setOpen(true);
    };
    const environment = (item: StrategyDefinitionListItem) =>
        `${item.exchangeCode} / ${item.tradeEnv}`;
    const enabled = (item: StrategyDefinitionListItem) =>
        t(item.enabled ? 'common:enabled' : 'common:disabled');
    return (
        <Space direction="vertical" size={8} style={{display: 'flex'}}>
            <Space.Compact block>
                <Input
                    {...inputProps}
                    readOnly
                    value={selectedLabel}
                    title={selectedLabel}
                    placeholder={t('pages:strategyPickerPlaceholder')}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && !inputProps.disabled) {
                            event.preventDefault();
                            choose();
                        }
                    }}
                />
                <Button disabled={inputProps.disabled} onClick={choose}>
                    {t('pages:strategyPickerChoose')}
                </Button>
                {value && (
                    <Button disabled={inputProps.disabled} onClick={() => onChange?.(undefined)}>
                        {t('pages:researchPickerClear')}
                    </Button>
                )}
            </Space.Compact>
            {active && selected.isError && (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:strategyPickerSelectedUnavailable')}
                    description={formatApiError(selected.error as AppApiError)}
                    action={
                        <Button onClick={() => void selected.refetch()}>{t('pages:retry')}</Button>
                    }
                />
            )}
            {active && selectedId && selected.isSuccess && selected.data === null && (
                <Alert type="warning" showIcon message={t('pages:strategyPickerMissing')} />
            )}
            {/* 独立选择窗口位于研究抽屉之上，取消不改原值；关闭后停止并释放目录读取。 */}
            <Modal
                open={showing}
                title={t(
                    purpose === 'version'
                        ? 'pages:versionParentStrategy'
                        : 'pages:strategyPickerTitle',
                )}
                width={900}
                zIndex={1100}
                footer={null}
                onCancel={() => setOpen(false)}
                destroyOnHidden
            >
                <Typography.Paragraph type="secondary">
                    {t(
                        purpose === 'version'
                            ? 'pages:versionParentStrategyHelp'
                            : purpose === 'schedule'
                              ? 'pages:scheduleCreation.strategyHelp'
                              : 'pages:strategyPickerHelp',
                    )}
                </Typography.Paragraph>
                <Form
                    name="source-strategy-picker"
                    form={form}
                    layout="vertical"
                    onFinish={(values) => {
                        setFilters({
                            strategyName: normalizeOptionalText(values.strategyName),
                            strategyCode: normalizeOptionalText(values.strategyCode),
                            strategyId: normalizeOptionalText(values.strategyId),
                        });
                        setPage(1);
                        setRevision((current) => current + 1);
                    }}
                >
                    <Row gutter={12}>
                        <Col xs={24} md={8}>
                            <Form.Item name="strategyName" label={t('pages:strategyName')}>
                                <Input maxLength={255} />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item name="strategyCode" label={t('pages:strategyCode')}>
                                <Input maxLength={128} />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item name="strategyId" label={t('pages:strategyId')}>
                                <Input maxLength={128} />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Space wrap style={{marginBottom: 16}}>
                        <Button type="primary" htmlType="submit">
                            {t('pages:search')}
                        </Button>
                        <Button onClick={reset}>{t('pages:reset')}</Button>
                    </Space>
                </Form>
                {results.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:strategyPickerLoadFailed')}
                        description={formatApiError(results.error as AppApiError)}
                        action={
                            <Button onClick={() => void results.refetch()}>
                                {t('pages:retry')}
                            </Button>
                        }
                    />
                ) : (
                    <Table<StrategyDefinitionListItem>
                        tableLayout="fixed"
                        size="small"
                        rowKey="strategyId"
                        loading={results.isFetching}
                        dataSource={results.data?.items ?? []}
                        columns={[
                            {
                                title: t('pages:strategyName'),
                                dataIndex: 'strategyName',
                                render: (_, item) => (
                                    <div style={{minWidth: 0}}>
                                        <Typography.Text
                                            style={{display: 'block', overflowWrap: 'anywhere'}}
                                        >
                                            {item.strategyName}
                                        </Typography.Text>
                                        {compact && (
                                            <>
                                                <Typography.Text
                                                    type="secondary"
                                                    style={{
                                                        display: 'block',
                                                        overflowWrap: 'anywhere',
                                                    }}
                                                >
                                                    {item.strategyCode}
                                                </Typography.Text>
                                                <Typography.Text
                                                    type="secondary"
                                                    style={{
                                                        display: 'block',
                                                        overflowWrap: 'anywhere',
                                                    }}
                                                >
                                                    {environment(item)} · {enabled(item)}
                                                </Typography.Text>
                                            </>
                                        )}
                                    </div>
                                ),
                            },
                            {
                                title: t('pages:strategyCode'),
                                dataIndex: 'strategyCode',
                                width: 220,
                                responsive: ['lg'],
                            },
                            {
                                title: t('pages:tradingEnvironment'),
                                key: 'environment',
                                width: 140,
                                responsive: ['lg'],
                                render: (_, item) => environment(item),
                            },
                            {
                                title: t('pages:enabledStatus'),
                                key: 'enabled',
                                width: 100,
                                responsive: ['lg'],
                                render: (_, item) => enabled(item),
                            },
                            {
                                title: t('pages:actions'),
                                key: 'select',
                                width: 80,
                                render: (_, item) => (
                                    <Button
                                        type="link"
                                        size="small"
                                        onClick={() => {
                                            onChange?.(item.strategyId);
                                            setOpen(false);
                                        }}
                                    >
                                        {t('pages:researchPickerUse')}
                                    </Button>
                                ),
                            },
                        ]}
                        pagination={{
                            current: page,
                            pageSize: 20,
                            total: results.data?.total ?? 0,
                            showSizeChanger: false,
                            showLessItems: true,
                            onChange: setPage,
                        }}
                        scroll={{y: 320}}
                        locale={{emptyText: t('pages:noStrategiesMatchTheseFilters')}}
                    />
                )}
            </Modal>
        </Space>
    );
}
