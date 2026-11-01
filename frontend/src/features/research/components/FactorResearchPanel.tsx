import {Alert, Button, Card, Collapse, Descriptions, Space, Table, Typography} from 'antd';
import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {MarketdataDatasetPicker} from '@/features/marketdata/components/MarketdataDatasetPicker';
import {factorResearchApi, type FactorArtifact, type StatisticalWindow} from '../api/factor-research';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {useAuthStore} from '@/store/auth-store';
import {factorResearchHandoff, type FactorResearchHandoff} from '../factor-research-handoff';

/** 报告只在服务端核验完成后显示；切换数据或会话时取消旧请求并清除旧报告。 */
export function FactorResearchPanel() {
    const {t} = useTranslation('pages');
    const [datasetId, setDatasetId] = useState<string>();
    const sessionId = useAuthStore(state => state.sessionId);
    const owner = useAuthStore(state => state.currentUser?.userId);
    return <Card title={t('factorResearch.title')} data-testid="factor-research-panel">
        <Space direction="vertical" style={{display: 'flex'}}>
            <Alert type="info" showIcon message="PYTHON_OFFLINE_RESEARCH · DIAGNOSTIC_ONLY"
                description={<>{t('factorResearch.boundary')}<br />NOT_TRADING_AUTHORIZATION · NO_AUTOMATIC_STRATEGY_APPROVAL</>} />
            <MarketdataDatasetPicker value={datasetId} active onChange={setDatasetId}
                aria-label={t('factorResearch.dataset')}
                constraints={{exchangeCode: 'OKX', marketType: 'SPOT', interval: '1h', status: 'READY', qualityStatus: 'OK', maximumGapCount: 0}} />
            <FactorResearchWorkflow key={`${owner}:${sessionId}:${datasetId ?? 'empty'}`} datasetId={datasetId} />
        </Space>
    </Card>;
}

function FactorResearchWorkflow({datasetId}: {datasetId?: string}) {
    const {t} = useTranslation('pages');
    const [report, setReport] = useState<FactorArtifact>();
    const [handoff, setHandoff] = useState<FactorResearchHandoff>();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string>();
    const controller = useRef<AbortController | null>(null);
    const fileInput = useRef<HTMLInputElement>(null);
    useEffect(() => () => controller.current?.abort(), []);
    const begin = () => {
        controller.current?.abort();
        controller.current = new AbortController();
        setBusy(true); setError(undefined);
        return controller.current;
    };
    const fail = (failure: unknown, request: AbortController) => {
        if (!request.signal.aborted) setError(failure instanceof Error && failure.message === 'FACTOR_INPUT_IDENTITY_INVALID'
            ? t('factorResearch.invalidInputIdentity') : formatApiError(failure as AppApiError));
    };
    const download = async () => {
        if (!datasetId) return;
        setHandoff(undefined);
        const request = begin();
        try {
            const blob = await factorResearchApi.download(datasetId, request.signal);
            if (request.signal.aborted) return;
            const nextHandoff = await factorResearchHandoff(blob, datasetId);
            if (request.signal.aborted) return;
            setHandoff(nextHandoff);
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url; anchor.download = nextHandoff.inputName; anchor.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (failure) { fail(failure, request); }
        finally { if (!request.signal.aborted) setBusy(false); }
    };
    const preview = async (file?: File) => {
        setReport(undefined);
        if (!file || !datasetId) return;
        const request = begin();
        try {
            if (file.size > 16 * 1024 * 1024) throw new Error(t('factorResearch.tooLarge'));
            const json = await file.text();
            if (request.signal.aborted) return;
            const result = await factorResearchApi.preview(datasetId, json, request.signal);
            if (!request.signal.aborted) setReport(result);
        } catch (failure) { fail(failure, request); }
        finally { if (!request.signal.aborted) setBusy(false); }
    };
    const windows = [
        {title: t('factorResearch.period'), dataIndex: 'name', render: (value: string) => t(`factorResearch.${value}`, {defaultValue: value})},
        {title: t('factorResearch.samples'), key: 'samples', render: (_: unknown, row: StatisticalWindow) => `${row.effectiveSamples} / ${row.candidateSamples} (${t('factorResearch.missing')}: ${row.missingSamples}; min: ${row.minimumSamples})`},
        {title: t('factorResearch.correlation'), dataIndex: 'correlation', render: (value: string | null) => value ?? '—'},
        {title: t('factorResearch.confidence'), key: 'ci', render: (_: unknown, row: StatisticalWindow) => row.confidenceInterval ? `[${row.confidenceInterval.lower}, ${row.confidenceInterval.upper}]` : '—'},
        {title: t('factorResearch.status'), dataIndex: 'status', render: (value: string) => t(`factorResearch.${value}`, {defaultValue: value})},
        {title: t('factorResearch.utc'), key: 'time', render: (_: unknown, row: StatisticalWindow) => `${row.featureFrom} → ${row.featureTo}; label ≤ ${row.lastLabelAvailableAt}; block=${row.blockLength}`},
    ];
    return <Space direction="vertical" style={{display: 'flex', minWidth: 0}}>
        <Typography.Paragraph>{t('factorResearch.instructions')}</Typography.Paragraph>
        <Alert type="info" showIcon message={t('factorResearch.inputPrerequisite')}/>
        <Collapse items={[{key: 'install', label: t('factorResearch.installTitle'), children: <Space direction="vertical" style={{width: '100%'}}>
            <Typography.Paragraph>{t('factorResearch.installMeaning')}</Typography.Paragraph>
            <Typography.Link href="https://github.com/ling5477/nexus-quant/releases" target="_blank" rel="noopener noreferrer">{t('factorResearch.companionRelease')}</Typography.Link>
            <Typography.Link href="https://github.com/ling5477/nexus-quant/blob/dev/research/py/INSTALL.md" target="_blank" rel="noopener noreferrer">{t('factorResearch.installDocument')}</Typography.Link>
            <Typography.Paragraph code copyable style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{'py -3.11 -m venv .venv\n.\\.venv\\Scripts\\python.exe -m pip --isolated install --no-index --no-cache-dir --only-binary=:all: --find-links .\\wheelhouse --require-hashes -r .\\requirements.lock\n.\\.venv\\Scripts\\python.exe -m pip check'}</Typography.Paragraph>
        </Space>}]}/>
        <Space wrap>
            <Button disabled={!datasetId || busy} loading={busy} onClick={() => void download()}>{t('factorResearch.download')}</Button>
            <Button disabled={!datasetId || busy} onClick={() => fileInput.current?.click()}>{t('factorResearch.preview')}</Button>
            <input ref={fileInput} type="file" accept=".json,application/json" hidden aria-label={t('factorResearch.file')}
                onChange={event => {const file = event.target.files?.[0]; event.target.value = ''; void preview(file);}} />
        </Space>
        {!handoff && <Typography.Paragraph type="secondary">{t('factorResearch.downloadFirst')}</Typography.Paragraph>}
        {handoff && <div data-testid="factor-research-handoff">
            <Descriptions size="small" layout="vertical" column={1} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                {key: 'dataset', label: t('factorResearch.dataset'), children: handoff.datasetId},
                {key: 'version', label: t('factorResearch.inputVersion'), children: handoff.datasetVersionId},
                {key: 'directory', label: t('factorResearch.researchDirectory'), children: handoff.directory},
                {key: 'input', label: t('factorResearch.inputFilename'), children: handoff.inputName},
                {key: 'time', label: t('factorResearch.fixedTime'), children: handoff.createdAt ?? '—'},
                {key: 'output', label: t('factorResearch.outputFilename'), children: handoff.outputName ?? '—'},
            ]}/>
            <Typography.Paragraph>{t('factorResearch.directoryMeaning')}</Typography.Paragraph>
            {handoff.command ? <Typography.Paragraph code copyable data-testid="factor-research-command" style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{handoff.command}</Typography.Paragraph>
                : <Alert type="warning" showIcon message={t('factorResearch.frozenTimeUnavailable')}/>}
            <Typography.Paragraph type="secondary">{t('factorResearch.repeatMeaning')}</Typography.Paragraph>
            <Typography.Paragraph type="secondary">{t('factorResearch.manualNotes')}</Typography.Paragraph>
        </div>}
        {error && <Alert type="error" showIcon message={error} />}
        {report && <>
            <Alert type="success" message={t('factorResearch.verified')} description={t('factorResearch.untrustedStatistics')} />
            <Descriptions bordered size="small" column={1} style={{overflowWrap: 'anywhere'}} items={[
                {key: 'source', label: t('factorResearch.source'), children: `${report.source} · ${report.dataset.exchange} ${report.dataset.symbol} / ${report.dataset.interval} · ${report.barCount} bars · ${report.dataset.qualityStatus}`},
                ...Object.entries(report.dataset).map(([key, value]) => ({key, label: key, children: value})),
                {key: 'artifact', label: 'Artifact ID / SHA256', children: `${report.artifactId} / ${report.checksum}`},
                {key: 'time', label: t('factorResearch.generated'), children: report.generatedAt},
            ]} />
            <Typography.Paragraph style={{overflowWrap: 'anywhere'}}>{t('factorResearch.assumptions')}<br />{JSON.stringify(report.observationAssumptions)}</Typography.Paragraph>
            <Collapse items={report.factors.map(factor => ({key: factor.factorKey,
                label: `${t(`factorResearch.${factor.factorKey}`)} · ${factor.factorKey} v${factor.factorVersion}`,
                children: <Space direction="vertical" style={{display: 'flex'}}>
                    <Typography.Paragraph style={{overflowWrap: 'anywhere'}}>{JSON.stringify(factor.parameters)}<br />
                        {factor.statistics.methodVersion} · horizon={factor.statistics.horizonBars}h · purge={factor.statistics.purgedTrainingSamples} · bootstrap={JSON.stringify(factor.statistics.bootstrap)}</Typography.Paragraph>
                    <Table rowKey="name" size="small" pagination={false} dataSource={factor.statistics.windows} columns={windows} scroll={{x: 1300}} />
                    <Table rowKey="index" size="small" dataSource={factor.points} scroll={{x: 850}}
                        pagination={{pageSize: 10, showSizeChanger: false}} columns={[
                            {title: '#', dataIndex: 'index'},
                            {title: t('factorResearch.utc'), key: 'range', render: (_, point) => `${point.windowStart} → ${point.windowEnd}`},
                            {title: t('factorResearch.available'), dataIndex: 'featureAvailableAt'},
                            {title: t('factorResearch.value'), dataIndex: 'factorValue', render: value => value ?? '—'},
                            {title: t('factorResearch.status'), dataIndex: 'qualityStatus', render: value => t(`factorResearch.${value}`, {defaultValue: value})},
                        ]} />
                </Space>}))} />
            <Typography.Title level={5}>{t('factorResearch.limitations')}</Typography.Title>
            <ul>{report.limitations.map(value => <li key={value}>{value}</li>)}</ul>
        </>}
    </Space>;
}
