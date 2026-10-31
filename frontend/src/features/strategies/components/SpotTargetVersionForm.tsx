import {ObjectConfigurationInput} from '@/components/configuration/ObjectConfigurationInput';
import {configurationObject} from '@/components/configuration/object-configuration';
import {
    Button,
    Col,
    Collapse,
    Form,
    Input,
    InputNumber,
    Row,
    Select,
    Space,
    Typography,
} from 'antd';
import {useTranslation} from 'react-i18next';
import type {StrategyVersionCreateRequest} from '@/features/strategies/types/strategies';

interface Values {
    versionName: string;
    status: string;
    window: number;
    entryLookbackBars: number;
    exitLookbackBars: number;
    investedExposure: string;
    configSnapshotJson?: string;
}

/** 正式现货策略的版本化参数；版本号和冻结校验值由服务端生成。 */
export function SpotTargetVersionForm({
    onCreate,
    strategyType,
    busy,
    disabled = false,
}: {
    onCreate: (request: StrategyVersionCreateRequest) => Promise<boolean>;
    strategyType: 'SPOT_SMA_TARGET_V1' | 'SPOT_CHANNEL_BREAKOUT_V1';
    busy: boolean;
    disabled?: boolean;
}) {
    const {t} = useTranslation('pages');
    const channel = strategyType === 'SPOT_CHANNEL_BREAKOUT_V1';
    const [form] = Form.useForm<Values>();
    return (
        <Form
            disabled={disabled}
            name="spot-target-version"
            form={form}
            layout="vertical"
            initialValues={{status: 'DRAFT', window: 20, entryLookbackBars: 20, exitLookbackBars: 10, investedExposure: '1'}}
            onFinish={async (values) => {
                const created = await onCreate({
                    versionName: values.versionName,
                    status: values.status,
                    paramSnapshotJson: JSON.stringify({
                        ...(channel ? {
                            entryLookbackBars: values.entryLookbackBars,
                            exitLookbackBars: values.exitLookbackBars,
                        } : {window: values.window}),
                        investedExposure: values.investedExposure.trim(),
                    }),
                    sourceSnapshotJson: JSON.stringify({executable: strategyType, ...(channel ? {timeframe: '1h', confirmation: 'BAR_CLOSE', currentBarExcluded: true, pyramiding: false, allowShort: false} : {})}),
                    configSnapshotJson: values.configSnapshotJson,
                });
                if (created) form.resetFields();
            }}
        >
            <Row gutter={16}>
                <Col xs={24} md={16}>
                    <Form.Item
                        label={t('pages:versionName')}
                        name="versionName"
                        rules={[{required: true}]}
                    >
                        <Input maxLength={255} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                    <Form.Item label={t('pages:versionStatus')} name="status">
                        <Select
                            options={['DRAFT', 'ACTIVE', 'ARCHIVED'].map((value) => ({
                                value,
                                label: t(`pages:versionState${value}`),
                            }))}
                        />
                    </Form.Item>
                </Col>
                {channel ? <>
                    <Col xs={24} md={12}>
                        <Form.Item label={t('pages:channelEntryLookback')} name="entryLookbackBars"
                            dependencies={['exitLookbackBars']}
                            rules={[{required: true}, {type: 'integer', min: 3, max: 200},
                                ({getFieldValue}) => ({validator: async (_, value) => {
                                    if (value <= getFieldValue('exitLookbackBars')) throw new Error(t('pages:channelLookbackInvalid'));
                                }})]}>
                            <InputNumber min={3} max={200} precision={0} style={{width: '100%'}} />
                        </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                        <Form.Item label={t('pages:channelExitLookback')} name="exitLookbackBars"
                            dependencies={['entryLookbackBars']}
                            rules={[{required: true}, {type: 'integer', min: 2, max: 199},
                                ({getFieldValue}) => ({validator: async (_, value) => {
                                    if (value >= getFieldValue('entryLookbackBars')) throw new Error(t('pages:channelLookbackInvalid'));
                                }})]}>
                            <InputNumber min={2} max={199} precision={0} style={{width: '100%'}} />
                        </Form.Item>
                    </Col>
                </> : <Col xs={24} md={12}>
                    <Form.Item
                        label={t('pages:smaWindow')}
                        name="window"
                        extra={t('product:configWindowHelp')}
                        rules={[{required: true}, {type: 'integer', min: 2, max: 200}]}
                    >
                        <InputNumber min={2} max={200} precision={0} style={{width: '100%'}} />
                    </Form.Item>
                </Col>}
                <Col xs={24} md={12}>
                    <Form.Item
                        label={t('pages:smaExposure')}
                        name="investedExposure"
                        extra={t('product:configExposureHelp')}
                        rules={[
                            {required: true},
                            {
                                validator: async (_, value: string) => {
                                    if (
                                        !/^(?:0\.\d*[1-9]\d*|1(?:\.0+)?)$/.test(value?.trim() ?? '')
                                    )
                                        throw new Error(t('pages:smaExposureInvalid'));
                                    if (channel && (value.trim().split('.')[1]?.replace(/0+$/, '').length ?? 0) > 8)
                                        throw new Error(t('pages:channelExposurePrecision'));
                                },
                            },
                        ]}
                    >
                        <Input inputMode="decimal" />
                    </Form.Item>
                </Col>
            </Row>
            <Typography.Paragraph type="secondary">{t(channel ? 'pages:channelBehavior' : 'pages:smaBehavior')}</Typography.Paragraph>
            <Collapse
                items={[
                    {
                        key: 'advanced',
                        label: t('product:configVersionExtra'),
                        children: (
                            <Form.Item
                                name="configSnapshotJson"
                                label={t('product:configVersionConfiguration')}
                                rules={[
                                    {
                                        validator: async (_, value?: string) => {
                                            try {configurationObject(value);} catch {throw new Error(t('product:configIncomplete'));}
                                        },
                                    },
                                ]}
                            >
                                <ObjectConfigurationInput label={t('product:configVersionConfiguration')} disabled={disabled} />
                            </Form.Item>
                        ),
                    },
                ]}
            />
            <Space style={{marginTop: 16}}>
                <Button type="primary" htmlType="submit" loading={busy}>
                    {t('pages:createVersion')}
                </Button>
                <Button onClick={() => form.resetFields()}>{t('pages:clear')}</Button>
            </Space>
        </Form>
    );
}
