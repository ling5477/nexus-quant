import {useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {Alert, Button, Card, Collapse, Descriptions, Empty, Select, Space, Table, Tag, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {experimentQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {useAuthStore} from '@/store/auth-store';
import {publishesApi} from '@/features/publishes/api/publishes';
import {defaultPublishesListFilters} from '@/features/publishes/types/publishes';
import type {ValidationView} from './validationApi';
import {researchSimComparisonApi, type ComparisonDecision, type ComparisonPerformance} from './researchSimComparisonApi';

/** URL只保存显式选择，刷新仅重读；可信关系及选中资格始终由服务端重新校验。 */
export function ResearchSimComparisonPanel({validation}: {validation: ValidationView}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore(state => state.currentUser?.userId);
    const [params, setParams] = useSearchParams();
    const [publishOffset, setPublishOffset] = useState(0);
    const [simOffset, setSimOffset] = useState(0);
    const selected = validation.steps.find(step => step.number === 12)?.selection?.selected;
    const ready = validation.status === 'SUCCEEDED' && !!selected;
    const trialId = params.get('selectedTrialId') ?? selected?.trialId ?? '';
    const versionId = params.get('strategyVersionId') ?? selected?.versionId ?? '';
    const publishId = params.get('publishId') ?? '';
    const paperRunId = params.get('paperRunId') ?? '';
    const selectionMismatch = !!selected && (trialId !== selected.trialId || versionId !== selected.versionId);
    const candidate = validation.frozenPolicy.candidates.find(row => row.strategyVersionId === selected?.versionId);
    const update = (publication: string, sim: string) => {
        setParams(previous => {
            const next = new URLSearchParams(previous);
            next.set('validationId', validation.validationId);
            next.set('selectedTrialId', trialId);
            next.set('strategyVersionId', versionId);
            if (publication) next.set('publishId', publication); else next.delete('publishId');
            if (sim) next.set('paperRunId', sim); else next.delete('paperRunId');
            return next;
        });
    };
    const publications = useQuery({
        queryKey: experimentQueryKeys.comparisonPublishes(owner, versionId, publishOffset),
        queryFn: ({signal}) => publishesApi.page({...defaultPublishesListFilters, strategyVersionId: versionId, publishStatus: 'SUCCEEDED', limit: 10, offset: publishOffset}, signal),
        enabled: ready && !!versionId && !selectionMismatch,
        retry: false,
    });
    const sims = useQuery({
        queryKey: experimentQueryKeys.comparisonSims(owner, publishId, simOffset),
        queryFn: ({signal}) => researchSimComparisonApi.sims(publishId, simOffset, signal),
        enabled: ready && !!publishId && !selectionMismatch,
        retry: false,
    });
    const comparison = useQuery({
        queryKey: experimentQueryKeys.comparison(owner, validation.validationId, trialId, versionId, publishId, paperRunId),
        queryFn: ({signal}) => researchSimComparisonApi.get(validation.validationId, {
            selectedTrialId: trialId, strategyVersionId: versionId, publishId: publishId || undefined,
            paperRunId: paperRunId || undefined,
        }, signal),
        enabled: ready && !!trialId && !!versionId,
        retry: false,
    });
    const view = selectionMismatch ? undefined : comparison.data;
    const error = publications.error ?? sims.error ?? comparison.error;
    const state = (value: string) => t(`researchSim.states.${value}`, {defaultValue: value});
    const metric = (value: unknown) => value == null ? <Typography.Text type="secondary">UNAVAILABLE</Typography.Text> : String(value);
    const time = (from: string | null, to: string | null) => <span>{from ?? 'UNAVAILABLE'} → {to ?? 'UNAVAILABLE'} (UTC)</span>;
    const navigate = (row: ComparisonPerformance) => <Space direction="vertical">
        {row.backtestConfigId && row.backtestRunId && <Link to={`/backtests/${encodeURIComponent(row.backtestConfigId)}?backtestRunId=${encodeURIComponent(row.backtestRunId)}`}>{t('experiments.backtest')}</Link>}
        {row.evaluationId && <Link to={`/evaluations?evaluationId=${encodeURIComponent(row.evaluationId)}`}>{t('experiments.evaluation')}</Link>}
        {row.phase === 'CONTINUOUS_SIM_FORWARD' && view?.paperRunId && <Link to={`/paper-trading/runs?paperRunId=${encodeURIComponent(view.paperRunId)}`}>{t('researchSim.simDetail')}</Link>}
    </Space>;
    const forwardPath = view?.paperRunId ? '/paper-trading/reviews?' + new URLSearchParams({analysisSource: 'sim',
        paperRunId: view.paperRunId, experimentId: validation.sourceExperimentId, validationId: validation.validationId,
        selectedTrialId: trialId, strategyVersionId: versionId, publishId: view.publishId ?? publishId}).toString() : null;
    const refresh = () => {void Promise.all([publications.refetch(), ...(publishId ? [sims.refetch()] : []), comparison.refetch()]);};
    return <Card size="small" title={t('researchSim.title')} data-testid="research-sim-comparison">
        <Space direction="vertical" size={16} style={{width: '100%', minWidth: 0}}>
            <Alert showIcon type="info" message={t('researchSim.readOnly')} description={t('researchSim.periods')}/>
            {!ready ? <Alert type="warning" message={t('researchSim.selectionRequired')}/> : <>
                <Descriptions size="small" column={1} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                    {key: 'validation', label: t('walkForward.history'), children: validation.validationId},
                    {key: 'version', label: t('researchSim.selectedVersion'), children: selected?.versionId},
                    {key: 'trial', label: t('researchSim.selectedTrial'), children: selected?.trialId},
                    {key: 'parameters', label: t('experiments.parameters'), children: JSON.stringify(candidate?.parameters ?? {})},
                ]}/>
                <Typography.Paragraph type="secondary">{t('researchSim.eligibility')}</Typography.Paragraph>
                {selectionMismatch && <Alert showIcon type="error" message={t('researchSim.selectionMismatch')} action={<Button onClick={() => {
                    setParams(previous => {
                        const next = new URLSearchParams(previous);
                        for (const key of ['selectedTrialId', 'strategyVersionId', 'publishId', 'paperRunId']) next.delete(key);
                        return next;
                    });
                    setPublishOffset(0); setSimOffset(0);
                }}>{t('researchSim.restoreSelection')}</Button>}/>}
                {error && <Alert showIcon type="error" message={formatApiError(error as AppApiError)} action={<Button onClick={refresh}>{t('retry')}</Button>}/>}
                <Space direction="vertical" style={{width: '100%'}}>
                    <Typography.Text strong>{t('researchSim.publish')}</Typography.Text>
                    <Select aria-label={t('researchSim.publish')} allowClear placeholder={t('researchSim.choosePublish')} value={publishId || undefined}
                        style={{width: '100%'}} loading={publications.isFetching} onChange={value => {setSimOffset(0); update(value ?? '', '');}}
                        options={publications.data?.items.map(row => ({value: row.publishRecordId, label: `${row.publishName ?? row.publishRecordId} · ${row.publishRecordId}`}))}/>
                    {publications.data && publications.data.total === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('researchSim.noPublish')}/>}
                    <Space wrap><Button size="small" disabled={!publishOffset || publications.isFetching} onClick={() => setPublishOffset(Math.max(0, publishOffset - 10))}>{t('experiments.previous')}</Button><Typography.Text>{t('researchSim.window', {from: publishOffset + 1, total: publications.data?.total ?? 'UNAVAILABLE'})}</Typography.Text><Button size="small" disabled={publications.isFetching || !publications.data || publishOffset + 10 >= publications.data.total} onClick={() => setPublishOffset(publishOffset + 10)}>{t('experiments.next')}</Button></Space>
                </Space>
                {publishId && <Space direction="vertical" style={{width: '100%'}}>
                    <Link to={`/publishes?publishId=${encodeURIComponent(publishId)}`}>{t('researchSim.publishDetail')}</Link>
                    <Typography.Text strong>{t('researchSim.sim')}</Typography.Text>
                    <Select aria-label={t('researchSim.sim')} allowClear placeholder={t('researchSim.chooseSim')} value={paperRunId || undefined}
                        style={{width: '100%'}} loading={sims.isFetching} onChange={value => update(publishId, value ?? '')}
                        options={sims.data?.items.map(row => ({value: row.paperRunId, label: `${row.paperRunId} · ${row.status}`}))}/>
                    {sims.data && sims.data.total === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('researchSim.noSim')}/>}
                    <Space wrap><Button size="small" disabled={!simOffset || sims.isFetching} onClick={() => setSimOffset(Math.max(0, simOffset - 10))}>{t('experiments.previous')}</Button><Typography.Text>{t('researchSim.window', {from: simOffset + 1, total: sims.data?.total ?? 'UNAVAILABLE'})}</Typography.Text><Button size="small" disabled={sims.isFetching || !sims.data || simOffset + 10 >= sims.data.total} onClick={() => setSimOffset(simOffset + 10)}>{t('experiments.next')}</Button></Space>
                </Space>}
                <Button loading={comparison.isFetching} onClick={refresh}>{t('researchSim.refresh')}</Button>
                {view && <>
                    <Descriptions size="small" column={1} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                        {key: 'checksum', label: t('researchSim.checksum'), children: view.strategyChecksum},
                        {key: 'publish', label: t('publishId'), children: view.publishId ?? 'UNAVAILABLE'},
                        {key: 'sim', label: t('paperRunId'), children: view.paperRunId ?? 'UNAVAILABLE'},
                    ]}/>
                    <Space wrap>
                        <Link to={`/experiments?experimentId=${encodeURIComponent(validation.sourceExperimentId)}&validationId=${encodeURIComponent(validation.validationId)}`}>{t('researchSim.validationDetail')}</Link>
                        <Link to={`/backtests/${encodeURIComponent(view.backtestConfigId)}?backtestRunId=${encodeURIComponent(view.backtestRunId)}`}>{t('researchSim.fullBacktest')}</Link>
                        {view.paperRunId && <Link to={`/paper-trading/runs?paperRunId=${encodeURIComponent(view.paperRunId)}`}>{t('researchSim.simDetail')}</Link>}
                        {forwardPath && <Link to={forwardPath}>{t('forwardObservation.openReview')}</Link>}
                    </Space>
                    <Alert type="warning" message={`${view.status} · ${t('researchSim.metricBases')}`}/>
                    {view.performance.some(row => row.reason === 'HOLDOUT_NOT_EXPLICITLY_VIEWED') && <Alert type="info" message={t('researchSim.holdoutHidden')}/>}
                    <Typography.Title level={5}>{t('researchSim.performance')}</Typography.Title>
                    <Table<ComparisonPerformance> size="small" pagination={false} dataSource={view.performance} rowKey={row => `${row.phase}-${row.fold ?? ''}-${row.backtestRunId ?? 'forward'}`} scroll={{x: 1800}} columns={[
                        {title: t('researchSim.phase'), width: 230, fixed: 'left', render: (_, row) => <Space direction="vertical"><Typography.Text strong>{row.phase}</Typography.Text><span>{state(row.phase)}{row.fold != null ? ` · Fold ${row.fold}` : ''}</span></Space>},
                        {title: t('experiments.status'), width: 200, render: (_, row) => <Space direction="vertical"><Tag>{state(row.status)}</Tag>{row.reason && <Typography.Text type="secondary">{row.reason}</Typography.Text>}</Space>},
                        {title: t('experiments.range'), width: 290, render: (_, row) => time(row.from, row.to)},
                        {title: t('versionId'), width: 240, render: (_, row) => <Typography.Text style={{overflowWrap: 'anywhere'}}>{row.strategyVersionId}</Typography.Text>},
                        ...(['netPnl', 'totalReturn', 'maxDrawdown', 'tradeCount'] as const).map(key => ({title: t(`experiments.${key}`), key, width: 145, render: (_: unknown, row: ComparisonPerformance) => metric(row[key])})),
                        {title: t('experiments.fee'), width: 200, render: (_, row) => <Space direction="vertical"><span>{metric(row.fee)} {row.feeCurrency}</span><Typography.Text type="secondary">{row.feeStatus}</Typography.Text></Space>},
                        {title: t('experiments.details'), width: 150, render: (_, row) => navigate(row)},
                    ]}/>
                    <Typography.Title level={5}>{t('researchSim.forward')}</Typography.Title>
                    <Space wrap><Tag>{state(view.observation.state)}</Tag><Tag>{state(view.observation.status)}</Tag></Space>
                    {view.observation.reason && <Typography.Paragraph type="secondary">{view.observation.reason}</Typography.Paragraph>}
                    {time(view.observation.from, view.observation.to)}
                    {view.observation.cursor && <Collapse items={[{key: 'cursor', label: t('researchSim.cursor'), children: <pre style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxHeight: 240, overflow: 'auto'}}>{JSON.stringify(view.observation.cursor, null, 2)}</pre>}]}/>}
                    <Typography.Title level={5}>{t('researchSim.decisions')}</Typography.Title>
                    <Typography.Paragraph>{t('researchSim.trustedWindow')}</Typography.Paragraph>
                    <Alert type={view.decisions.status === 'AVAILABLE' ? 'info' : 'warning'} message={state(view.decisions.status)} description={view.decisions.reason}/>
                    <Typography.Paragraph type="secondary">{t('researchSim.decisionWindow', {limit: view.decisions.limit, total: view.decisions.total})}{view.decisions.windowLimited ? ` · ${t('researchSim.windowLimited')}` : ''}</Typography.Paragraph>
                    <Table<ComparisonDecision> size="small" rowKey="decisionId" dataSource={view.decisions.items} scroll={{x: 2550}} pagination={{pageSize: 10, showSizeChanger: false}} columns={[
                        {title: t('researchSim.signalOpen'), dataIndex: 'signalOpenTime', width: 210, fixed: 'left'},
                        {title: t('researchSim.signalAvailable'), dataIndex: 'signalAvailableAt', width: 210, render: metric},
                        {title: t('versionId'), dataIndex: 'strategyVersionId', width: 230, render: value => <Typography.Text style={{overflowWrap: 'anywhere'}}>{value}</Typography.Text>},
                        {title: t('researchSim.expectedAction'), dataIndex: 'expectedAction', width: 130, render: metric},
                        {title: t('researchSim.actualDecision'), dataIndex: 'actualDecision', width: 130, render: metric},
                        {title: t('researchSim.decisionStatus'), dataIndex: 'decisionStatus', width: 150},
                        {title: t('researchSim.executionOpen'), dataIndex: 'executionOpenTime', width: 210, render: metric},
                        {title: t('researchSim.orderStatus'), dataIndex: 'orderStatus', width: 150, render: metric},
                        {title: t('researchSim.fillStatus'), dataIndex: 'fillStatus', width: 150, render: metric},
                        {title: t('experiments.fee'), width: 145, render: (_, row) => <span>{metric(row.fee)} {row.feeCurrency}</span>},
                        {title: t('researchSim.slippage'), dataIndex: 'slippage', width: 155, render: metric},
                        {title: t('researchSim.attribution'), width: 380, render: (_, row) => <Space direction="vertical"><Tag>{state(row.status)}</Tag>{row.attribution.map(value => <span key={value}>{t(`researchSim.attributions.${value}`, {defaultValue: value})}</span>)}{row.reason && <Typography.Text type="secondary">{row.reason}</Typography.Text>}</Space>},
                    ]}/>
                    <Typography.Paragraph type="secondary">{t('researchSim.noRecommendation')}</Typography.Paragraph>
                </>}
            </>}
        </Space>
    </Card>;
}
