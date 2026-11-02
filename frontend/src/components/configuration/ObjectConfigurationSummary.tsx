import {Collapse, Descriptions, Space, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {configurationFields} from './object-configuration';

/** 概览按业务字段读取，冻结原文只在高级区展示，绝不重写服务端快照。 */
export function ObjectConfigurationSummary({value}: {value?: string}) {
    const {t} = useTranslation('product');
    let fields;
    try {fields = configurationFields(value);}
    catch {fields = null;}
    const labels: Record<string, string> = {
        window: t('pages:smaWindow'), investedExposure: t('pages:smaExposure'),
        exchangeCode: t('pages:exchange'), marketType: t('pages:marketType'),
        symbol: t('pages:symbol'), interval: t('pages:interval'), datasetId: t('pages:dataset'),
    };
    return <Space direction="vertical" style={{width: '100%', minWidth: 0}}>
        {fields?.length ? <Descriptions size="small" column={1} items={fields.map(field => ({
            key: field.name,
            label: Object.hasOwn(labels, field.name) ? labels[field.name] : field.name,
            children: <Typography.Text style={{overflowWrap: 'anywhere'}}>{field.type === 'object' || field.type === 'array'
                ? t('configNestedInspect') : field.type === 'boolean' ? t(field.text === 'true' ? 'configTrue' : 'configFalse')
                : field.type === 'null' ? t('configNull') : field.text}</Typography.Text>,
        }))} /> : <Typography.Text type="secondary">{t(fields ? 'configNoExtra' : 'configAdvancedRequired')}</Typography.Text>}
        <Collapse style={{width: '100%'}} items={[{key: 'raw', label: t('configRawSnapshot'), children:
            <pre style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxHeight: 280, overflow: 'auto', margin: 0}}>{value || '{}'}</pre>,
        }]} />
    </Space>;
}
