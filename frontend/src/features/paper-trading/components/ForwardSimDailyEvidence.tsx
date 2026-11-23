import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Alert, Button, Card, Descriptions, Form, Input, Space, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {selectSessionIdentity, useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {NqAmountText} from '@/components/nq/NqNumericText';
import {dailyEvidenceExport, dailyEvidenceWindow, firstEvidenceDate} from '../daily-evidence';
import {observationsApi, observationMethod, type EvaluationWindow, type Observation} from '../api/observations';
import type {SimAnalysisRun} from '../api/analysis';

interface Props {run: SimAnalysisRun; observation?: Observation; available: boolean; fixedAsOf?: string}

/** 登出、重登录及切换目标时销毁本地选择，旧响应不能成为另一身份的可下载证据。 */
export function ForwardSimDailyEvidence(props: Props) {
    const owner = useAuthStore(state => state.currentUser?.userId);
    const session = useAuthStore(selectSessionIdentity);
    return <DailyEvidence key={`${owner}/${session}/${props.run.paperRunId}/${props.observation?.observationId}/${observationMethod(props.observation)}/${props.available}/${props.fixedAsOf}`}
        {...props} owner={owner} session={session}/>;
}

function DailyEvidence({run, observation, available, fixedAsOf, owner, session}: Props & {owner?: number; session: string | null}) {
    const {t} = useTranslation('pages');
    const offset = useAuthStore(state => state.serverTimeOffsetMs);
    const [date, setDate] = useState(() => observation ? firstEvidenceDate(observation.observationFrom)
        : new Date(Date.now() + offset).toISOString().slice(0, 10));
    const [asOf, setAsOf] = useState(() => fixedAsOf ?? new Date(Date.now() + offset).toISOString());
    const [selected, setSelected] = useState<EvaluationWindow | null>(null);
    const [invalid, setInvalid] = useState(false);
    const range = dailyEvidenceWindow(date, asOf, observation);
    const method = observationMethod(observation);
    const query = useQuery({queryKey: paperTradingQueryKeys.dailyEvidence(owner, session, run.paperRunId, observation?.observationId ?? null, selected, method),
        queryFn: ({signal}) => observationsApi.evaluateEvidence(run, selected!, signal, method!),
        enabled: available && !!owner && !!session && !!selected && !!method, retry: false, gcTime: 0});
    const result = available && !query.isError && !query.isFetching && selected ? query.data : undefined;
    const state = (value: string) => t(`forwardObservation.states.${value}`, {defaultValue: value});
    const clear = () => {setSelected(null); setInvalid(false);};
    const download = () => {
        if (!result || !selected || !owner || !available) return;
        const blob = new Blob([dailyEvidenceExport(run, owner, selected, result, observation)], {type: 'application/json;charset=utf-8'});
        const url = URL.createObjectURL(blob), anchor = document.createElement('a');
        anchor.href = url; anchor.download = `sim-daily-${run.paperRunId}-${date}.json`;
        anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 0);
    };
    return <Card size="small" title={t('dailySimEvidence.title')} data-testid="forward-daily-evidence">
        <Space direction="vertical" style={{width: '100%', minWidth: 0}}>
            <Typography.Paragraph type="secondary">{t('dailySimEvidence.meaning')}</Typography.Paragraph>
            {!available && <Alert type="warning" showIcon message={t('dailySimEvidence.unavailable')}/>}
            {observation && <Typography.Text style={{overflowWrap: 'anywhere'}}>{t('dailySimEvidence.target')} {observation.observationFrom} → {observation.observationTo}</Typography.Text>}
            <Form layout="vertical" onFinish={() => {
                if (!range || Date.parse(asOf) > Date.now() + offset) {setInvalid(true); setSelected(null); return;}
                setInvalid(false);
                if (selected?.from === range.from && selected.to === range.to && selected.asOf === range.asOf) void query.refetch();
                else setSelected(range);
            }}>
                <Form.Item label={t('dailySimEvidence.date')}><Input type="date" aria-label={t('dailySimEvidence.date')} value={date}
                    onChange={event => {clear(); setDate(event.target.value);}}/></Form.Item>
                <Form.Item label={t('dailySimEvidence.asOf')}><Input aria-label={t('dailySimEvidence.asOf')} value={asOf} maxLength={64}
                    onChange={event => {clear(); setAsOf(event.target.value);}}/></Form.Item>
                <Space wrap><Button htmlType="submit" disabled={!available || !owner || !method} loading={query.isFetching}>{t('dailySimEvidence.read')}</Button>
                    <Button disabled={!available} onClick={() => {clear(); setAsOf(new Date(Date.now() + offset).toISOString());}}>{t('forwardObservation.now')}</Button>
                    <Button disabled={!result || !method} onClick={download}>{t('dailySimEvidence.download')}</Button></Space>
            </Form>
            {invalid && <Alert type="error" showIcon message={t('dailySimEvidence.invalid')}/>}
            {query.isError && <Alert type="error" showIcon message={formatApiError(query.error as AppApiError)}/>}
            {result && <div data-testid="daily-evidence-result">
                <Space wrap><Tag>{state(result.evaluation.status)}</Tag><Tag>{result.evaluation.timeBasis}</Tag><Typography.Text>{result.evaluation.reason ?? '—'}</Typography.Text></Space>
                <Descriptions size="small" layout="vertical" column={{xs: 1, md: 2}} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                    {key: 'window', label: t('forwardObservation.requestedRange'), children: `${result.evaluation.observationFrom} → ${result.evaluation.observationTo}`},
                    {key: 'asOf', label: t('forwardObservation.asOf'), children: result.evaluation.observedAt},
                    {key: 'received', label: t('dailySimEvidence.receivedAt'), children: result.receivedAt},
                    {key: 'samples', label: t('dailySimEvidence.samples'), children: result.evaluation.timeBasis === 'ACTUAL_EVENT_TIME' ? `${result.evaluation.samples.expected} / ${result.evaluation.samples.observed} / ${result.evaluation.samples.missing} / ${result.evaluation.samples.unknown}` : '—'},
                    {key: 'pnl', label: t('common:accountPerformance.pnl'), children: <NqAmountText exact value={result.evaluation.performance.pnl}/>},
                    {key: 'risk', label: t('forwardObservation.riskCount'), children: `${result.evaluation.metrics.riskEventCount ?? '—'} · ${state(result.evaluation.metrics.riskStatus)}`},
                    {key: 'method', label: t('dailySimEvidence.method'), children: result.evaluation.methodVersion},
                    {key: 'checksum', label: t('forwardObservation.eventChecksum'), children: result.evaluation.checksum},
                ]}/>
            </div>}
        </Space>
    </Card>;
}
