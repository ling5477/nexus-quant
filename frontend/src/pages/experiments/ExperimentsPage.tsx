import {useEffect, useState, type Key} from 'react';
import {useSearchParams, Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {Alert, App, Button, Card, Col, Collapse, Descriptions, Form, Input, Popconfirm, Radio, Row, Select, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation} from '@tanstack/react-query';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {useAuthStore} from '@/store/auth-store';
import {useExperiments} from '@/features/experiments/useExperiments';
import {experimentsApi, type ExperimentRequest, type ExperimentTrial} from '@/features/experiments/experimentsApi';
import {useBacktestsListQuery} from '@/features/backtests/useBacktestsListQuery';
import {applyNqCssVars, nqTableClassName} from '@/nq-design-system';
import '@/nq-design-system/table/nq-table.css';

interface Draft {templateConfigId: string; type: string; mode: string; windows: string; exits: string; exposures: string; explicit: string}
interface Pending {id: string; input: ExperimentRequest; rejected?: boolean}

/** 服务端状态由Query重读；本地只保留发送前的原请求，刷新/登录不会隐式发起写操作。 */
export function ExperimentsPage() {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const user = useAuthStore(s => s.currentUser);
    const writable = !!user?.roles.some(r => ['ADMIN', 'OPERATOR'].includes(r));
    const [params, setParams] = useSearchParams();
    const id = params.get('experimentId');
    const [offset, setOffset] = useState(0);
    const [templateSearch, setTemplateSearch] = useState('');
    const {list, detail, cancel, retry, refresh} = useExperiments(offset, id);
    const [form] = Form.useForm<Draft>();
    const mode = Form.useWatch('mode', form) ?? 'grid';
    const type = Form.useWatch('type', form) ?? 'SPOT_SMA_TARGET_V1';
    const [preview, setPreview] = useState<Record<string, number | string>[] | null>(null);
    const [pending, setPending] = useState<Pending | null>(null);
    const [storageError, setStorageError] = useState(false);
    const [selected, setSelected] = useState<Key[]>([]);
    const storageKey = `nq:experiment-creation:${user?.userId}`;
    const exactTemplateId = /^bcf-[a-f0-9-]{36}$/i.test(templateSearch);
    const templates = useBacktestsListQuery({limit: 50, offset: 0, researchConfigId: '', backtestConfigId: exactTemplateId ? templateSearch : '', name: exactTemplateId ? '' : templateSearch}, writable ? 1 : 0);
    useEffect(() => {applyNqCssVars();}, []);
    useEffect(() => {
        try {
            const raw = localStorage.getItem(storageKey);
            const saved: Pending | null = raw ? JSON.parse(raw) : null;
            if (saved && (!/^[a-f0-9-]{36}$/i.test(saved.id) || !saved.input?.templateConfigId)) throw new Error('invalid pending request');
            setPending(saved); setStorageError(false);
        } catch {setStorageError(true);}
    }, [storageKey]);
    const error = (value: unknown) => {void message.error(formatApiError(value as AppApiError));};
    const request = async (): Promise<ExperimentRequest> => {
        const values = await form.validateFields();
        if (values.mode === 'explicit') {
            const candidates: unknown = JSON.parse(values.explicit);
            if (!Array.isArray(candidates)) throw new Error(t('experiments.invalid'));
            return {templateConfigId: values.templateConfigId, candidates};
        }
        const integers = (raw: string) => raw.split(',').map(v => {
            if (!/^\s*\d+\s*$/.test(v)) throw new Error(t('experiments.invalid'));
            return Number(v.trim());
        });
        const exposures = values.exposures.split(',').map(v => v.trim());
        return {templateConfigId: values.templateConfigId, grid: values.type === 'SPOT_SMA_TARGET_V1'
            ? {window: integers(values.windows), investedExposure: exposures}
            : {entryLookbackBars: integers(values.windows), exitLookbackBars: integers(values.exits), investedExposure: exposures}};
    };
    const previewAction = useMutation({mutationFn: async () => experimentsApi.preview(await request()), onSuccess: setPreview, onError: error});
    const create = useMutation({mutationFn: async (original?: Pending) => {
        const owner = user?.userId;
        if (!original && localStorage.getItem(storageKey)) throw new Error(t('experiments.pending'));
        const saved: Pending = original ? {id: original.id, input: original.input} : {id: crypto.randomUUID(), input: await request()};
        // 存储失败时拒绝发送；超时仍保留相同输入和幂等身份供显式恢复。
        localStorage.setItem(storageKey, JSON.stringify(saved)); setPending(saved);
        let result;
        try {
            result = await experimentsApi.create(saved.id, saved.input);
        } catch (failure) {
            if ([400, 403, 422].includes((failure as AppApiError).status)) {
                const rejected = {...saved, rejected: true};
                localStorage.setItem(storageKey, JSON.stringify(rejected)); setPending(rejected);
            }
            throw failure;
        }
        if (useAuthStore.getState().currentUser?.userId !== owner) throw new Error(t('experiments.scopeChanged'));
        localStorage.removeItem(storageKey); setPending(null); return result;
    }, onSuccess: result => {setParams({experimentId: result.experimentId}); void refresh();}, onError: error});
    const view = detail.data;
    const rows = view?.trials ?? [];
    const latest = rows.filter(row => !rows.some(other => other.trialId === row.trialId && other.attempt > row.attempt));
    const shown = selected.length ? rows.filter(r => selected.includes(r.attemptId)) : rows;
    const metric = (v: unknown, reason?: string | null) => v == null ? <Typography.Text type="secondary">UNAVAILABLE · {reason || t('experiments.metricMissing')}</Typography.Text> : String(v);
    const columns: ColumnsType<ExperimentTrial> = [
        {title: t('experiments.parameters'), dataIndex: 'parameterIdentity', fixed: 'left', width: 260, render: v => <Typography.Text code>{v}</Typography.Text>},
        {title: t('experiments.attempt'), dataIndex: 'attempt', width: 85},
        {title: t('experiments.status'), dataIndex: 'status', width: 180, render: (v, row) => <Space direction="vertical"><Tag>{t(`experiments.states.${v}`, {defaultValue: v})}</Tag>{row.failureReason && <Typography.Text type="danger">{row.failureReason}</Typography.Text>}{!row.comparable && <Tag color="warning">{t('experiments.incomparable')}</Tag>}</Space>},
        ...(['netPnl', 'totalReturn', 'maxDrawdown', 'sharpe', 'tradeCount', 'fee', 'slippage'] as const).map(key => ({title: t(`experiments.${key}`), key, width: 155, render: (_: unknown, row: ExperimentTrial) => metric(row.metrics[key], row.failureReason)})),
        {title: 'Benchmark', width: 180, render: (_, row) => {const b = row.metrics.metrics?.researchValidity?.benchmark; return metric(b?.status === 'AVAILABLE' ? b.benchmarkReturn : null, b?.reason);}},
        {title: t('experiments.relative'), width: 160, render: (_, row) => metric(row.metrics.metrics?.researchValidity?.strategyVsBenchmarkDifference)},
        {title: t('experiments.details'), width: 180, render: (_, row) => <Space direction="vertical"><Link to={`/backtests/${row.backtestConfigId}?backtestRunId=${row.backtestRunId}`}>{t('experiments.backtest')}</Link>{row.evaluationId && <Link to={`/evaluations?evaluationId=${row.evaluationId}`}>{t('experiments.evaluation')}</Link>}{writable && row.status === 'FAILED' && row.attempt < 3 && !view?.cancelRequested && latest.includes(row) && <Button size="small" loading={retry.isPending} onClick={() => retry.mutate({trial: row.trialId}, {onError: error})}>{t('experiments.retry')}</Button>}<Typography.Text type="secondary" style={{fontSize: 11, overflowWrap: 'anywhere'}}>{row.strategyVersionId}</Typography.Text></Space>},
    ];
    return <Space direction="vertical" size={16} style={{width: '100%'}}>
        <NqPageHeader title={t('experiments.title')} description={t('experiments.description')} extra={<Button onClick={() => void refresh()}>{t('experiments.refresh')}</Button>}/>
        <Alert type="info" showIcon message={t('experiments.boundary')}/>
        {storageError && <Alert type="error" message={t('experiments.storageError')}/>}
        {pending && <Alert type="warning" message={t('experiments.pending')} description={pending.id} action={<Space><Button disabled={!writable || storageError} loading={create.isPending} onClick={() => create.mutate(pending)}>{t('experiments.recover')}</Button>{pending.rejected && <Button onClick={() => {localStorage.removeItem(storageKey); setPending(null);}}>{t('experiments.discardRejected')}</Button>}</Space>}/>}
        <Row gutter={[16, 16]}>
            <Col xs={24} xl={10}><Card title={t('experiments.create')}>
                <Form form={form} layout="vertical" disabled={!writable || !!pending || storageError || create.isPending} onValuesChange={() => setPreview(null)} initialValues={{type: 'SPOT_SMA_TARGET_V1', mode: 'grid', windows: '4,8', exits: '2', exposures: '0.5,1', explicit: '[{"window":4,"investedExposure":"0.5"},{"window":8,"investedExposure":"1"}]'}}>
                    <Form.Item name="templateConfigId" label={t('experiments.template')} rules={[{required: true}]}><Select showSearch filterOption={false} onSearch={setTemplateSearch} loading={templates.isLoading} options={templates.data?.items.filter(c => c.strategyVersionId && c.datasetId).map(c => ({value: c.backtestConfigId, label: `${c.name} · ${c.backtestConfigId}`}))}/></Form.Item>
                    {templates.error && <Alert type="error" message={formatApiError(templates.error as AppApiError)}/>}
                    <Form.Item name="type" label={t('experiments.strategy')}><Select options={['SPOT_SMA_TARGET_V1', 'SPOT_CHANNEL_BREAKOUT_V1'].map(value => ({value, label: value}))}/></Form.Item>
                    <Form.Item name="mode" label={t('experiments.mode')}><Radio.Group options={[{value: 'grid', label: t('experiments.grid')}, {value: 'explicit', label: t('experiments.explicit')}]}/></Form.Item>
                    {mode === 'grid' ? <>
                        <Form.Item name="windows" label={type === 'SPOT_SMA_TARGET_V1' ? 'window' : 'entryLookbackBars'} rules={[{required: true}]}><Input/></Form.Item>
                        {type !== 'SPOT_SMA_TARGET_V1' && <Form.Item name="exits" label="exitLookbackBars" rules={[{required: true}]}><Input/></Form.Item>}
                        <Form.Item name="exposures" label="investedExposure" rules={[{required: true}]}><Input/></Form.Item>
                        <Typography.Paragraph type="secondary">{t('experiments.csv')}</Typography.Paragraph>
                    </> : <Form.Item name="explicit" label={t('experiments.parameters')} rules={[{required: true}]}><Input.TextArea rows={5}/></Form.Item>}
                    <Space wrap><Button loading={previewAction.isPending} onClick={() => previewAction.mutate()}>{t('experiments.preview')}</Button><Button type="primary" disabled={!preview || !writable || !!pending || storageError || create.isPending} loading={create.isPending} onClick={() => create.mutate(undefined)}>{t('experiments.submit')}</Button></Space>
                </Form>
                {preview && <Alert style={{marginTop: 12}} type="success" message={t('experiments.count', {count: preview.length})} description={<pre style={{whiteSpace: 'pre-wrap'}}>{JSON.stringify(preview, null, 2)}</pre>}/>}
            </Card></Col>
            <Col xs={24} xl={14}><Card title={t('experiments.groups')}>
                {list.error && <Alert type="error" message={formatApiError(list.error as AppApiError)}/>}
                <Table rowKey="experimentId" loading={list.isLoading} dataSource={list.data} pagination={false} scroll={{x: 600}} columns={[
                    {title: t('experiments.details'), dataIndex: 'experimentId', render: value => <Button type="link" onClick={() => {setSelected([]); setParams({experimentId: value});}}>{value}</Button>},
                    {title: t('experiments.strategy'), dataIndex: 'strategyType'},
                    {title: t('experiments.status'), render: (_, row) => <Tag>{row.cancelRequested ? t('experiments.states.CANCELLED') : row.active ? t('experiments.states.RUNNING') : t('experiments.completed')}</Tag>},
                    {title: t('experiments.trials'), dataIndex: 'trialCount'},
                ]}/>
                <Space style={{marginTop: 12}}><Button disabled={!offset} onClick={() => setOffset(Math.max(0, offset - 20))}>{t('experiments.previous')}</Button><span>{offset / 20 + 1}</span><Button disabled={(list.data?.length ?? 0) < 20} onClick={() => setOffset(offset + 20)}>{t('experiments.next')}</Button></Space>
            </Card></Col>
        </Row>
        {detail.error && <Alert type="error" message={formatApiError(detail.error as AppApiError)}/>}
        {view && <Card title={<Space wrap><span>{t('experiments.results')}</span><Tag>{t(`experiments.states.${view.status}`, {defaultValue: view.status})}</Tag><Typography.Text>{latest.filter(r => ['SUCCEEDED', 'FAILED', 'CANCELLED', 'OUTCOME_UNKNOWN'].includes(r.status)).length}/{latest.length}</Typography.Text></Space>} extra={writable && !view.cancelRequested && ['QUEUED', 'RUNNING'].includes(view.status) && <Popconfirm title={t('experiments.cancelConfirm')} onConfirm={() => cancel.mutateAsync().catch(error)}><Button danger loading={cancel.isPending}>{t('experiments.cancel')}</Button></Popconfirm>}>
            <Descriptions size="small" column={{xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2}} items={[
                {key: 'id', label: t('experiments.details'), children: view.experimentId},
                {key: 'market', label: t('experiments.dataset'), children: `${view.frozenInput.datasetSnapshot.symbol} / ${view.frozenInput.datasetSnapshot.interval}`},
                {key: 'window', label: t('experiments.range'), children: `${view.frozenInput.startTime} → ${view.frozenInput.endTime}`},
                {key: 'capital', label: t('experiments.capital'), children: view.frozenInput.initialCapital},
                {key: 'hash', label: 'SHA-256', children: <Typography.Text style={{overflowWrap: 'anywhere'}}>{view.frozenInput.datasetSnapshot.frozenInput.sha256}</Typography.Text>},
                {key: 'cost', label: t('experiments.assumptions'), children: <Space wrap><span>{t('experiments.feeRate')}: {String(view.frozenInput.executionSpec.feeRate ?? 'UNAVAILABLE')}</span><span>{t('experiments.slippageBps')}: {String(view.frozenInput.executionSpec.slippageBps ?? 'UNAVAILABLE')}</span></Space>},
            ]}/>
            <Collapse size="small" style={{margin: '12px 0'}} items={[{key: 'frozen', label: t('experiments.frozenDetails'), children: <pre style={{maxHeight: 280, overflow: 'auto', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: 0}}>{JSON.stringify(view.frozenInput.executionSpec, null, 2)}</pre>}]}/>
            <Typography.Paragraph type="secondary">{t('experiments.comparisonNote')}</Typography.Paragraph>
            <Table className={nqTableClassName()} rowKey="attemptId" dataSource={shown} columns={columns} pagination={false} scroll={{x: 2300}}/>
            <Select mode="multiple" style={{width: '100%', marginTop: 12}} aria-label={t('experiments.compare')} placeholder={t('experiments.compare')} value={selected} onChange={setSelected} options={rows.map(r => ({value: r.attemptId, label: `${r.ordinal} / ${r.attempt} · ${r.parameterIdentity}`}))}/>
        </Card>}
    </Space>;
}
