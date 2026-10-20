import {Alert, Button, Collapse, Input, Select, Space, Typography} from 'antd';
import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {configurationFields, serializeConfigurationFields, type ConfigurationField} from './object-configuration';

function read(value?: string) {
    try {return {fields: configurationFields(value), invalid: false};}
    catch {return {fields: [] as ConfigurationField[], invalid: true};}
}

/** 普通配置按字段编辑；高级原文始终可用，外部reset与本地未完成输入明确区分。 */
export function ObjectConfigurationInput({value, onChange, label, disabled = false}: {
    value?: string;
    onChange?: (value: string) => void;
    label: string;
    disabled?: boolean;
}) {
    const {t} = useTranslation('product');
    const [model, setModel] = useState(() => read(value));
    const [draftError, setDraftError] = useState(false);
    const emitted = useRef(value);
    useEffect(() => {
        if (value === emitted.current) return;
        emitted.current = value;
        setModel(read(value));
        setDraftError(false);
    }, [value]);
    const update = (fields: ConfigurationField[]) => {
        setModel({fields, invalid: false});
        let next: string;
        try {next = serializeConfigurationFields(fields); setDraftError(false);}
        catch {next = 'INCOMPLETE_CONFIGURATION'; setDraftError(true);}
        // 未完成的字段必须使父表单校验失败，不能悄悄提交上一个有效配置。
        emitted.current = next;
        onChange?.(next);
    };
    const patch = (index: number, field: Partial<ConfigurationField>) =>
        update(model.fields.map((row, i) => i === index ? {...row, ...field} : row));
    return <Space direction="vertical" style={{width: '100%'}} data-testid="object-configuration-input">
        {model.invalid ? <Alert type="warning" showIcon message={t('configAdvancedRequired')} /> : <>
            {!model.fields.length && <Typography.Text type="secondary">{t('configNoExtra')}</Typography.Text>}
            {model.fields.map((field, index) => <div key={index} style={{display: 'grid', gridTemplateColumns: 'minmax(0,1fr)', gap: 8, border: '1px solid var(--nq-border-color, #27465c)', padding: 12, borderRadius: 6}}>
                <Input aria-label={t('configFieldName', {index: index + 1})} placeholder={t('configKey')} value={field.name} disabled={disabled} onChange={event => patch(index, {name: event.target.value})} />
                <Select aria-label={t('configFieldType', {index: index + 1})} value={field.type} disabled={disabled}
                    options={(['string', 'number', 'boolean', 'null', 'object', 'array'] as const).map(type => ({value: type, label: t(`configType_${type}`)}))}
                    onChange={type => patch(index, {type, text: type === 'boolean' ? 'false' : type === 'null' ? 'null' : type === 'object' ? '{}' : type === 'array' ? '[]' : ''})} />
                {field.type === 'boolean' ? <Select aria-label={t('configFieldValue', {index: index + 1})} value={field.text} disabled={disabled} options={[{value: 'true', label: t('configTrue')}, {value: 'false', label: t('configFalse')}]} onChange={text => patch(index, {text})} />
                    : field.type === 'null' ? <Typography.Text type="secondary">{t('configNull')}</Typography.Text>
                    : <Input.TextArea aria-label={t('configFieldValue', {index: index + 1})} autoSize={{minRows: 1, maxRows: 5}} value={field.text} disabled={disabled} placeholder={field.type === 'number' ? t('configExactNumber') : undefined} onChange={event => patch(index, {text: event.target.value})} />}
                <Button size="small" disabled={disabled} onClick={() => update(model.fields.filter((_, i) => i !== index))}>{t('configRemove', {index: index + 1})}</Button>
            </div>)}
            <Button disabled={disabled || model.fields.length >= 100} onClick={() => update([...model.fields, {name: '', type: 'string', text: ''}])}>{t('configAddField')}</Button>
        </>}
        {draftError && <Alert type="error" showIcon message={t('configIncomplete')} />}
        <Collapse style={{width: '100%'}} items={[{key: 'raw', label: t('configAdvancedJson'), children: <Input.TextArea aria-label={label + ' JSON'} value={value} disabled={disabled} rows={5} placeholder="{}" onChange={event => {
            const next = event.target.value;
            emitted.current = next;
            setModel(read(next));
            setDraftError(false);
            onChange?.(next);
        }} />}]} />
    </Space>;
}
