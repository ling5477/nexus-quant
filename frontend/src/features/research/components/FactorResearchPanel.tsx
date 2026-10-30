import {Alert, Button, Card, Collapse, Descriptions, Space, Table, Typography} from 'antd';
import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {MarketdataDatasetPicker} from '@/features/marketdata/components/MarketdataDatasetPicker';
import {factorResearchApi, type FactorArtifact, type StatisticalWindow} from '../api/factor-research';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {useAuthStore} from '@/store/auth-store';

/** 报告只在服务端核验完成后显示；切换数据或会话时取消旧请求并清除旧报告。 */
export function FactorResearchPanel() {
    const {t} = useTranslation('pages');
    const [datasetId, setDatasetId] = useState<string>();
    const sessionId = useAuthStore(state => state.sessionId);
    return <Card title={t('factorResearch.title')} data-testid="factor-research-panel">
        <Space direction="vertical" style={{display: 'flex'}}>
            <Alert type="info" showIcon message="PYTHON_OFFLINE_RESEARCH · DIAGNOSTIC_ONLY"
                description={<>{t('factorResearch.boundary')}<br />NOT_TRADING_AUTHORIZATION · NO_AUTOMATIC_STRATEGY_APPROVAL</>} />
            <MarketdataDatasetPicker value={datasetId} active onChange={setDatasetId}
                aria-label={t('factorResearch.dataset')}
                constraints={{exchangeCode: 'OKX', marketType: 'SPOT', interval: '1h', status: 'READY', qualityStatus: 'OK', maximumGapCount: 0}} />
            <FactorResearchWorkflow key={`${sessionId}:${datasetId ?? 'empty'}`} datasetId={datasetId} />
        </Space>
    </Card>;
}

function FactorResearchWorkflow({datasetId}: {datasetId?: string}) {
    const {t} = useTranslation('pages');
    const [report, setReport] = useState<FactorArtifact>();
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
        if (!request.signal.aborted) setError(formatApiError(failure as AppApiError));
    };
    const download = async () => {
        if (!datasetId) return;
        const request = begin();
        try {
            const blob = await factorResearchApi.download(datasetId, request.signal);
            if (request.signal.aborted) return;
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url; anchor.download = 'frozen-input.json'; anchor.click();
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
        <Space wrap>
            <Button disabled={!datasetId || busy} loading={busy} onClick={() => void download()}>{t('factorResearch.download')}</Button>
            <Button disabled={!datasetId || busy} onClick={() => fileInput.current?.click()}>{t('factorResearch.preview')}</Button>
            <input ref={fileInput} type="file" accept=".json,application/json" hidden aria-label={t('factorResearch.file')}
                onChange={event => {const file = event.target.files?.[0]; event.target.value = ''; void preview(file);}} />
        </Space>
        <Typography.Paragraph code copyable style={{overflowWrap: 'anywhere'}}>
            python -m nq_research --frozen-input frozen-input.json --factor-output factor-artifact.json --factor-window 24 --horizon-bars 6 --seed 42
        </Typography.Paragraph>
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
