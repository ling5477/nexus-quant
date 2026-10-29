import {Alert, Descriptions, Typography} from 'antd';
import {useTranslation} from 'react-i18next';

export function frozenResearchInput(json?: string | null) {
    if (!json) return null;
    try {
        const dataset = JSON.parse(json);
        const input = dataset?.frozenInput;
        if (!input) return null;
        if (input.version !== 'HISTORICAL_RESEARCH_INPUT_V1' || !Array.isArray(input.bars)
            || !/^[a-f0-9]{64}$/.test(input.sha256) || typeof input.frozenAt !== 'string'
            || !Number.isFinite(Date.parse(input.frozenAt))) return {invalid: true} as const;
        return {invalid: false, sha256: input.sha256 as string, frozenAt: input.frozenAt as string,
            source: typeof input.source === 'string' ? input.source : '—', count: input.bars.length,
            visibility: `${input.replayVisibilityVersion} · ${input.replayVisibilitySource}`} as const;
    } catch {return {invalid: true} as const;}
}

/** 只显示配置或运行自身的冻结身份，不读取目录或共享行情来补充历史事实。 */
export function FrozenResearchInputSummary({datasetSnapshot}: {datasetSnapshot?: string | null}) {
    const {t} = useTranslation('pages');
    const input = frozenResearchInput(datasetSnapshot);
    if (!input) return null;
    if (input.invalid) return <Alert type="warning" showIcon message={t('researchDataset.frozenUnavailable')} />;
    return <div style={{marginBlock: 12}} data-testid="frozen-research-input">
        <Typography.Text strong>{t('researchDataset.frozenTitle')}</Typography.Text>
        <Descriptions size="small" column={{xs: 1, sm: 2}} items={[
            {key: 'count', label: t('barCount'), children: input.count},
            {key: 'source', label: t('source'), children: input.source},
            {key: 'at', label: t('researchDataset.frozenAt'), children: input.frozenAt},
            {key: 'hash', label: 'SHA-256', children: <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>{input.sha256}</Typography.Text>},
            {key: 'visibility', label: t('researchDataset.visibility'), children: input.visibility, span: 2},
        ]} />
        <Typography.Paragraph type="secondary">{t('researchDataset.freezeHelp')}</Typography.Paragraph>
    </div>;
}
