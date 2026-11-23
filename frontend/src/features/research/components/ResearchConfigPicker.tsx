import {Alert, Button, Col, Form, Input, Modal, Row, Space, Table, Typography} from 'antd';
import type {InputProps} from 'antd';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {
    useResearchListQuery,
    useResearchSelectionDetailQuery,
} from '@/features/research/hooks/useResearchListQuery';
import {
    defaultResearchListFilters,
    type ResearchListFilters,
} from '@/features/research/types/research';
import {normalizeOptionalText} from '@/utils/formatters';

interface ResearchConfigPickerProps
    extends Pick<
        InputProps,
        'id' | 'disabled' | 'aria-describedby' | 'aria-invalid' | 'aria-required'
    > {
    value?: string;
    active: boolean;
    onChange?: (value: string | undefined) => void;
}

/** 通过真实分页选择研究配置；逐页替换选项，已选对象始终按其正式身份读取。 */
export function ResearchConfigPicker({
    value,
    active,
    onChange,
    ...inputProps
}: ResearchConfigPickerProps) {
    const disabled = inputProps.disabled;
    const {t} = useTranslation(['pages', 'common']);
    const [open, setOpen] = useState(false);
    const [form] = useLocalizedForm<ResearchListFilters>();
    const [filters, setFilters] = useState(defaultResearchListFilters);
    const [revision, setRevision] = useState(1);
    const [page, setPage] = useState(1);
    const showing = active && open;
    const selected = useResearchSelectionDetailQuery(value || null, active);
    // 关闭时切换到禁用的独立key，使离开的请求取消，不继续保留整批选项。
    const results = useResearchListQuery(
        {...filters, limit: 20, offset: (page - 1) * 20},
        showing ? revision : 0,
    );
    const select = () => {
        form.resetFields();
        setFilters(defaultResearchListFilters);
        setPage(1);
        setRevision((current) => current + 1);
        setOpen(true);
    };
    return (
        <Space direction="vertical" size={8} style={{display: 'flex'}}>
            <Space.Compact block>
                <Input
                    {...inputProps}
                    readOnly
                    disabled={disabled}
                    value={
                        selected.data && selected.data.researchConfigId === value
                            ? `${selected.data.name} · ${value}`
                            : value || ''
                    }
                    placeholder={t('pages:researchPickerPlaceholder')}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && !disabled) {
                            event.preventDefault();
                            select();
                        }
                    }}
                />
                <Button disabled={disabled} onClick={select}>
                    {t('pages:researchPickerChoose')}
                </Button>
                {value && (
                    <Button disabled={disabled} onClick={() => onChange?.(undefined)}>
                        {t('pages:researchPickerClear')}
                    </Button>
                )}
            </Space.Compact>
            {active && selected.isError && (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:researchPickerSelectedUnavailable')}
                    description={formatApiError(selected.error as AppApiError)}
                    action={
                        <Button onClick={() => void selected.refetch()}>{t('pages:retry')}</Button>
                    }
                />
            )}
            {/* 研究选择位于新建抽屉之上，取消只关闭选择窗口，不提交外层表单。 */}
            <Modal
                open={showing}
                title={t('pages:researchPickerTitle')}
                width={760}
                zIndex={1100}
                footer={null}
                onCancel={() => setOpen(false)}
                destroyOnHidden
            >
                <Typography.Paragraph type="secondary">
                    {t('pages:researchPickerHelp')}
                </Typography.Paragraph>
                <Form
                    name="research-config-picker"
                    form={form}
                    layout="vertical"
                    onFinish={(values) => {
                        setFilters({
                            sourceStrategyId: '',
                            name: normalizeOptionalText(values.name) ?? '',
                            researchConfigId: normalizeOptionalText(values.researchConfigId) ?? '',
                        });
                        setPage(1);
                        setRevision((current) => current + 1);
                    }}
                >
                    <Row gutter={12}>
                        <Col xs={24} sm={12}>
                            <Form.Item name="name" label={t('pages:name')}>
                                <Input maxLength={255} />
                            </Form.Item>
                        </Col>
                        <Col xs={24} sm={12}>
                            <Form.Item
                                name="researchConfigId"
                                label={t('pages:researchConfigurationId')}
                            >
                                <Input maxLength={128} />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Space wrap style={{marginBottom: 16}}>
                        <Button type="primary" htmlType="submit">
                            {t('pages:search')}
                        </Button>
                        <Button
                            onClick={() => {
                                form.resetFields();
                                setFilters(defaultResearchListFilters);
                                setPage(1);
                                setRevision((current) => current + 1);
                            }}
                        >
                            {t('pages:reset')}
                        </Button>
                    </Space>
                </Form>
                {results.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:researchPickerLoadFailed')}
                        description={formatApiError(results.error as AppApiError)}
                        action={
                            <Button onClick={() => void results.refetch()}>
                                {t('pages:retry')}
                            </Button>
                        }
                    />
                ) : (
                    <Table
                        size="small"
                        rowKey="researchConfigId"
                        loading={results.isFetching}
                        dataSource={results.data?.items ?? []}
                        columns={[
                            {title: t('pages:name'), dataIndex: 'name', width: 240},
                            {
                                title: t('pages:researchConfigurationId'),
                                dataIndex: 'researchConfigId',
                                width: 240,
                            },
                            {
                                title: t('pages:actions'),
                                key: 'select',
                                width: 100,
                                fixed: 'right',
                                render: (_, item) => (
                                    <Button
                                        type="link"
                                        onClick={() => {
                                            onChange?.(item.researchConfigId);
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
                        scroll={{x: 580, y: 320}}
                        locale={{emptyText: t('pages:noResearchConfigurationsMatchTheseFilters')}}
                    />
                )}
            </Modal>
        </Space>
    );
}
