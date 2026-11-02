import {Alert, Descriptions, Typography} from 'antd';
import {useTranslation} from 'react-i18next';

export function frozenResearchInput(json?: string | null) {
    if (!json) return null;
    try {
        const dataset = JSON.parse(json);
        const input = dataset?.frozenInput;
        if (!input) return null;
        const legacy = input.version === 'HISTORICAL_RESEARCH_INPUT_V1';
        const versioned = input.version === 'HISTORICAL_RESEARCH_INPUT_V2';
        const count = legacy && Array.isArray(input.bars) ? input.bars.length : input.barCount;
        if ((!legacy && !versioned) || !Number.isInteger(count) || count < 1 || count > (legacy ? 720 : 4320)
            || (legacy && !Array.isArray(input.bars))
            || (versioned && (typeof input.datasetVersionId !== 'string'
                || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(input.datasetVersionId)
                || !/^[a-f0-9]{64}$/.test(input.manifestSha256)
                || !/^[a-f0-9]{64}$/.test(input.sourceSha256)
                || input.barCount !== dataset.barCount))
            || !/^[a-f0-9]{64}$/.test(input.sha256) || typeof input.frozenAt !== 'string'
            || !Number.isFinite(Date.parse(input.frozenAt))) return {invalid: true} as const;
        return {invalid: false, sha256: input.sha256 as string, frozenAt: input.frozenAt as string,
            source: typeof input.source === 'string' ? input.source : '—', count: count as number,
            version: input.version as string,
            datasetVersionId: versioned ? input.datasetVersionId as string : null,
            manifestSha256: versioned ? input.manifestSha256 as string : null,
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
            {key: 'version', label: t('researchDataset.frozenVersion'), children: input.version},
            ...(input.datasetVersionId ? [{key: 'datasetVersion', label: t('researchDataset.datasetVersion'), children: <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>{input.datasetVersionId}</Typography.Text>}] : []),
            {key: 'at', label: t('researchDataset.frozenAt'), children: input.frozenAt},
            {key: 'hash', label: 'SHA-256', children: <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>{input.sha256}</Typography.Text>},
            ...(input.manifestSha256 ? [{key: 'manifest', label: t('researchDataset.manifestChecksum'), children: <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>{input.manifestSha256}</Typography.Text>, span: 2}] : []),
            {key: 'visibility', label: t('researchDataset.visibility'), children: input.visibility, span: 2},
        ]} />
        <Typography.Paragraph type="secondary">{t('researchDataset.freezeHelp')}</Typography.Paragraph>
    </div>;
}
