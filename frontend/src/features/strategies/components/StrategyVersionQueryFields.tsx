import {Button, Col, Collapse, Form, Grid, Input, InputNumber, Row, Select, Space} from 'antd';
import {useTranslation} from 'react-i18next';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import type {StrategyVersionPageRequest} from '@/features/strategies/types/strategies';
import {normalizeOptionalText} from '@/utils/formatters';

export type VersionFilters = Omit<StrategyVersionPageRequest, 'limit' | 'offset'>;

/** 手机保留名称和精确身份查找；状态筛选不代表运行状态或交易权限。 */
export function StrategyVersionQueryFields({
    onSearch,
    onReset,
}: {
    onSearch: (values: VersionFilters) => void;
    onReset: () => void;
}) {
    const {t} = useTranslation('pages');
    const compact = !Grid.useBreakpoint().lg;
    const [form] = useLocalizedForm<VersionFilters>();
    const details = (
        <>
            <Col xs={24} lg={6}>
                <Form.Item name="version" label={t('versionNumber')}>
                    <InputNumber min={1} max={2147483647} precision={0} style={{width: '100%'}} />
                </Form.Item>
            </Col>
            <Col xs={24} lg={6}>
                <Form.Item name="status" label={t('versionStatus')}>
                    <Select
                        allowClear
                        options={['DRAFT', 'ACTIVE', 'ARCHIVED'].map((value) => ({
                            value,
                            label: value,
                        }))}
                    />
                </Form.Item>
            </Col>
        </>
    );
    return (
        <Form
            form={form}
            layout="vertical"
            onFinish={(values) =>
                onSearch({
                    versionName: normalizeOptionalText(values.versionName),
                    strategyVersionId: normalizeOptionalText(values.strategyVersionId),
                    version: values.version ?? undefined,
                    status: values.status || undefined,
                })
            }
        >
            <Row gutter={12}>
                <Col xs={24} lg={6}>
                    <Form.Item name="versionName" label={t('versionName')}>
                        <Input maxLength={128} />
                    </Form.Item>
                </Col>
                <Col xs={24} lg={6}>
                    <Form.Item name="strategyVersionId" label={t('versionId')}>
                        <Input maxLength={128} />
                    </Form.Item>
                </Col>
                {!compact && details}
            </Row>
            {compact && (
                <Collapse
                    style={{marginBottom: 12}}
                    items={[
                        {
                            key: 'more',
                            label: t('versionPickerMoreFilters'),
                            forceRender: true,
                            children: <Row gutter={12}>{details}</Row>,
                        },
                    ]}
                />
            )}
            <Space style={{marginBottom: 16}}>
                <Button htmlType="submit" type="primary">
                    {t('search')}
                </Button>
                <Button
                    onClick={() => {
                        form.resetFields();
                        onReset();
                    }}
                >
                    {t('reset')}
                </Button>
            </Space>
        </Form>
    );
}
