import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {Alert, Button, Card, Collapse, Descriptions, Popconfirm, Select, Space, Table, Tag, Typography} from 'antd';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {experimentQueryKeys} from '@/api/query-keys';
import {validationApi, type ValidationResult, type ValidationView} from './validationApi';

/** 写动作只由显式点击发起；未知提交继续沿用发送前保存的UUID。 */
export function WalkForwardPanel({source, completed, writable}: {source: string; completed: boolean; writable: boolean}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore(s => s.currentUser?.userId);
    const cache = useQueryClient();
    const [selected, setSelected] = useState<string | null>(null);
    const [revealed, setRevealed] = useState(false);
    const storageKey = `nq:validation-creation:v1:${owner}:${source}`;
    const [hasPending, setHasPending] = useState(() => {try {return !!localStorage.getItem(storageKey);} catch {return false;}});
    const root = experimentQueryKeys.validations(owner, source);
    const policy = useQuery({queryKey: [...root, 'policy'], queryFn: () => validationApi.policy(source)});
    const list = useQuery({queryKey: root, queryFn: () => validationApi.list(source), refetchInterval: 3000});
    const id = selected ?? list.data?.[0]?.validationId;
    const detail = useQuery({queryKey: experimentQueryKeys.validation(owner, id), queryFn: () => validationApi.get(id!), enabled: !!id, refetchInterval: q => q.state.data?.status === 'RUNNING' ? 2000 : false});
    const refresh = async () => {await Promise.all([cache.invalidateQueries({queryKey: root}), cache.invalidateQueries({queryKey: experimentQueryKeys.validation(owner, id)})]);};
    const created = (view: ValidationView) => {
        setSelected(view.validationId); setRevealed(false);
        cache.setQueryData(experimentQueryKeys.validation(owner, view.validationId), view);
        void cache.invalidateQueries({queryKey: root});
    };
    const create = useMutation({mutationFn: async () => {
        const key = storageKey;
        const operation = localStorage.getItem(key) ?? crypto.randomUUID();
        if (!/^[a-f0-9-]{36}$/i.test(operation)) throw new Error(t('walkForward.invalidIdentity'));
        localStorage.setItem(key, operation);
        setHasPending(true);
        const view = await validationApi.create(source, operation);
        if (useAuthStore.getState().currentUser?.userId !== owner) throw new Error(t('experiments.scopeChanged'));
        localStorage.removeItem(key); setHasPending(false); return view;
    }, onSuccess: created});
    const cancel = useMutation({mutationFn: () => validationApi.cancel(id!), onSuccess: () => void refresh()});
    const reveal = useMutation({mutationFn: () => validationApi.holdout(id!, crypto.randomUUID()), onSuccess: view => {
        if (useAuthStore.getState().currentUser?.userId !== owner) return;
        cache.setQueryData(experimentQueryKeys.validation(owner, id), view); setRevealed(true);
    }});
    const view = detail.data;
    const error = policy.error ?? list.error ?? detail.error ?? create.error ?? cancel.error ?? reveal.error;
    const value = (v: unknown) => v == null ? 'UNAVAILABLE' : String(v);
    const shown = view?.results.filter(r => r.step < 13 || revealed) ?? [];
    return <Card title={t('walkForward.title')}>
        <Space direction="vertical" size={16} style={{width: '100%'}}>
            <Alert showIcon type="warning" message={t('walkForward.shortSample')} description={t('walkForward.exposure')}/>
            <Alert type="info" message={t('walkForward.reset')}/>
            {error && <Alert type="error" message={formatApiError(error as AppApiError)}/>}
            <Typography.Paragraph>{t('walkForward.selection')}</Typography.Paragraph>
            {policy.data && <>
                <Table size="small" rowKey={r => `${r.fold}-${r.role}`} pagination={false} scroll={{x: 560}} dataSource={policy.data.steps.filter(s => !s.role.endsWith('STRESS'))} columns={[
                    {title: 'Fold', dataIndex: 'fold'}, {title: t('walkForward.role'), dataIndex: 'role', render: role => <Tag>{role}</Tag>},
                    {title: t('walkForward.range'), render: (_, r) => <Space direction="vertical"><span>[{r.startBar}, {r.endBar}) · {r.endBar - r.startBar} bars</span><Typography.Text type="secondary">{r.startTime ?? 'INSUFFICIENT_DATA'} → {r.endTimeExclusive ?? 'UNAVAILABLE'} (UTC)</Typography.Text></Space>},
                ]}/>
                {policy.data.barCount !== 720 && <Alert type="warning" message={`INSUFFICIENT_DATA · ${policy.data.barCount}/720`}/>}
                <Collapse items={[{key: 'policy', label: t('walkForward.frozenPolicy'), children: <pre style={{maxHeight: 300, overflow: 'auto', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{JSON.stringify(view?.frozenPolicy ?? policy.data, null, 2)}</pre>}]}/>
            </>}
            <Space wrap>
                <Button type="primary" disabled={!writable || !completed || policy.isPending || list.isPending || (!hasPending && list.data?.some(v => v.status === 'RUNNING'))} loading={create.isPending} onClick={() => create.mutate()}>{hasPending ? t('experiments.recover') : t('walkForward.submit')}</Button>
                <Button onClick={() => void refresh()}>{t('experiments.refresh')}</Button>
                <Select aria-label={t('walkForward.history')} style={{width: 'min(360px, 70vw)'}} value={id} onChange={value => {setSelected(value); setRevealed(false);}} options={list.data?.map(v => ({value: v.validationId, label: `${v.createdAt} · ${v.status}`}))}/>
            </Space>
            {!completed && <Typography.Text type="secondary">{t('walkForward.completedRequired')}</Typography.Text>}
            {view && <>
                <Space wrap><Tag>{view.status}</Tag><Typography.Text style={{overflowWrap: 'anywhere'}}>{view.validationId}</Typography.Text>
                    {view.status === 'RUNNING' && writable && <Popconfirm title={t('experiments.cancelConfirm')} onConfirm={() => cancel.mutateAsync()}><Button danger loading={cancel.isPending}>{t('walkForward.cancel')}</Button></Popconfirm>}
                </Space>
                {view.failureReason && <Alert type="warning" message={`${view.status} · ${view.failureReason}`}/>}
                <Table size="small" rowKey="number" pagination={false} scroll={{x: 900}} dataSource={view.steps} columns={[
                    {title: 'Fold', dataIndex: 'fold'}, {title: t('walkForward.role'), dataIndex: 'role'},
                    {title: t('walkForward.range'), render: (_, r) => `[${r.startBar}, ${r.endBar})`},
                    {title: t('experiments.status'), render: (_, r) => r.unavailableReason ? `UNAVAILABLE · ${r.unavailableReason}` : [...new Set(view.results.filter(x => x.step === r.number).map(x => x.status))].join(', ')},
                    {title: t('walkForward.selected'), render: (_, r) => r.selection?.selected ? `#${r.selection.selected.ordinal} · ${r.selection.selected.versionId}` : r.role === 'IN_SAMPLE' ? 'UNAVAILABLE' : t('walkForward.frozenBeforeTest')},
                    {title: t('walkForward.selectedAt'), dataIndex: 'selectedAt'},
                ]}/>
                <Typography.Title level={5}>{t('walkForward.results')}</Typography.Title>
                <Table size="small" rowKey="backtestRunId" dataSource={shown} pagination={{pageSize: 16}} scroll={{x: 1900}} columns={[
                    {title: 'Fold / Role', width: 180, render: (_, r: ValidationResult) => `${Math.floor(r.step / 4) + 1} · ${view.steps.find(s => s.number === r.step)?.role}`},
                    {title: t('experiments.parameters'), width: 200, render: (_, r) => JSON.stringify(view.frozenPolicy.candidates.find(c => c.strategyVersionId === r.strategyVersionId)?.parameters) ?? r.strategyVersionId},
                    {title: t('experiments.status'), render: (_, r) => `${r.status}${r.failureReason ? ` · ${r.failureReason}` : ''}`},
                    ...(['totalReturn', 'netPnl', 'maxDrawdown', 'tradeCount'] as const).map(key => ({title: t(`experiments.${key}`), key, render: (_: unknown, r: ValidationResult) => value(r[key])})),
                    {title: 'Benchmark', render: (_, r) => value(r.researchValidity?.benchmark?.benchmarkReturn)},
                    {title: t('experiments.relative'), render: (_, r) => value(r.researchValidity?.strategyVsBenchmarkDifference)},
                    {title: t('experiments.assumptions'), width: 230, render: (_, r) => <span>{t('experiments.feeRate')}: {value(r.executionSpec.feeRate)} · {t('experiments.slippageBps')}: {value(r.executionSpec.slippageBps)}{r.step % 4 > 1 && <Tag>RESEARCH_HYPOTHESIS_V1</Tag>}</span>},
                    {title: t('experiments.details'), render: (_, r) => <Space direction="vertical"><Link to={`/backtests/${r.backtestConfigId}?backtestRunId=${r.backtestRunId}`}>{t('experiments.backtest')}</Link>{r.evaluationId && <Link to={`/evaluations?evaluationId=${r.evaluationId}`}>{t('experiments.evaluation')}</Link>}</Space>},
                ]}/>
                <Typography.Title level={5}>{t('walkForward.costSensitivity')}</Typography.Title>
                <Table size="small" rowKey={r => `${r.fold}-${r.scenario}`} pagination={false} scroll={{x: 1100}} dataSource={view.costSensitivity.filter(r => r.fold < 4 || revealed)} columns={[
                    {title: 'Fold / Scenario', render: (_, r) => `${r.fold} · ${r.scenario}`},
                    {title: t('experiments.status'), render: (_, r) => `${r.status}${r.reason ? ` · ${r.reason}` : ''}`},
                    ...(['baselineNetPnl', 'stressNetPnl', 'returnChange', 'maxDrawdownChange', 'tradeCountChange', 'benchmarkRelative'] as const).map(key => ({title: t(`walkForward.${key}`), key, render: (_: unknown, r: ValidationView['costSensitivity'][number]) => value(r[key])})),
                ]}/>
                <Typography.Title level={5}>{t('walkForward.stability')}</Typography.Title>
                <Descriptions column={1} items={[
                    {key: 'mean', label: t('walkForward.mean'), children: value(view.parameterStability.meanIndependentOosReturn)},
                    {key: 'dependence', label: t('walkForward.dependence'), children: value(view.parameterStability.largestWindowAbsoluteReturnShare ?? view.parameterStability.windowDependence)},
                ]}/>
                <Table size="small" rowKey={r => r.candidate.ordinal} pagination={false} scroll={{x: 550}} dataSource={view.parameterStability.rankChanges} columns={[
                    {title: t('experiments.parameters'), render: (_, r) => `#${r.candidate.ordinal} · ${JSON.stringify(r.candidate.parameters)}`},
                    {title: t('walkForward.ranks'), render: (_, r) => r.foldRanks.map(x => `${x.fold}: ${x.rank ?? x.status}`).join(' / ')},
                ]}/>
                <Collapse items={[{key: 'neighbors', label: t('walkForward.neighbors'), children: <Table size="small" rowKey={r => `${r.fold}-${r.a}-${r.b}`} dataSource={view.parameterStability.adjacentCandidateDifferences} columns={[
                    {title: 'Fold', dataIndex: 'fold'}, {title: 'A → B', render: (_, r) => `${r.a} → ${r.b}`},
                    {title: t('walkForward.returnDifference'), render: (_, r) => value(r.trainingReturnDifferenceBMinusA ?? r.status)},
                ]}/>}]}/>
                <Typography.Title level={5}>FINAL_HOLDOUT</Typography.Title>
                <Typography.Paragraph>{t('walkForward.holdoutUsage', {uses: view.datasetValidationUses, views: view.holdoutViews.length})}</Typography.Paragraph>
                <Button disabled={!view.results.some(r => r.step === 13 && r.status === 'SUCCEEDED')} loading={reveal.isPending} onClick={() => reveal.mutate()}>{t('walkForward.reveal')}</Button>
                {revealed && <Typography.Paragraph>{view.holdoutViews.map(v => v.viewedAt).join(' / ')}</Typography.Paragraph>}
            </>}
        </Space>
    </Card>;
}
