import {useQuery} from '@tanstack/react-query';
import {Alert, Button, Card, Descriptions, Space, Spin, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {NqAmountText} from '@/components/nq/NqNumericText';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {SimAnalysisRun} from '../api/analysis';
import {observationsApi, observationEventWindow, type ForwardEvaluation} from '../api/observations';

/** 只读诊断保持原目标可见，所有指标仍由同一canonical查询在固定asOf下提供。 */
export function ForwardSimEventDiagnostics({run, evaluation, open, onToggle}: {
    run: SimAnalysisRun; evaluation: ForwardEvaluation; open: boolean; onToggle: () => void;
}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore(state => state.currentUser?.userId);
    const range = observationEventWindow(evaluation);
    const query = useQuery({queryKey: paperTradingQueryKeys.forwardEvaluation(owner, run.paperRunId, range),
        queryFn: ({signal}) => observationsApi.evaluate(run, range!, signal), enabled: !!owner && open && !!range,
        retry: false});
    const result = !query.isError ? query.data : undefined;
    const state = (value: string) => t(`forwardObservation.states.${value}`, {defaultValue: value});
    return <Card size="small" title={t('forwardObservation.eventDiagnostics')} data-testid="forward-event-diagnostics">
        <Space direction="vertical" style={{width: '100%', minWidth: 0}}>
            <Typography.Paragraph>{t('forwardObservation.eventDiagnosticsMeaning')}</Typography.Paragraph>
            <Descriptions size="small" layout="vertical" column={1} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                {key: 'events', label: t('forwardObservation.eventRange'), children: `${evaluation.coverageStart ?? '—'} → ${evaluation.coverageEnd ?? '—'}`},
                {key: 'asOf', label: t('forwardObservation.asOf'), children: evaluation.observedAt},
            ]}/>
            {!range && <Alert type="info" showIcon message={t('forwardObservation.eventRangeUnavailable')}/>}
            <Button disabled={!open && !range} onClick={onToggle}>{t(open ? 'forwardObservation.returnToTarget' : 'forwardObservation.inspectEvents')}</Button>
            {open && range && <>
                <Alert type="warning" showIcon message={t('forwardObservation.eventOnly')} description={t('forwardObservation.eventOnlyMeaning')}/>
                {query.isFetching && <Spin/>}
                {query.isError && <Alert type="error" showIcon message={formatApiError(query.error as AppApiError)}/>}
                {result && <div data-testid="forward-event-result">
                    <Space wrap><Tag>{state(result.status)}</Tag><Typography.Text>{result.reason ?? '—'}</Typography.Text></Space>
                    <Descriptions size="small" layout="vertical" column={1} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                        {key: 'requested', label: t('forwardObservation.eventRange'), children: `${result.observationFrom} → ${result.observationTo}`},
                        {key: 'asOf', label: t('forwardObservation.asOf'), children: result.observedAt},
                        {key: 'status', label: t('forwardObservation.eventMetricStatus'), children: state(result.performance.status)},
                        {key: 'pnl', label: t('common:accountPerformance.pnl'), children: <NqAmountText exact value={result.performance.pnl}/>},
                        {key: 'drawdown', label: t('common:accountPerformance.drawdown'), children: <NqAmountText exact value={result.performance.maxDrawdown}/>},
                        {key: 'checksum', label: t('forwardObservation.eventChecksum'), children: result.checksum},
                    ]}/>
                    {result.reason && <Typography.Paragraph type="secondary">{t('forwardObservation.eventStillUnavailable')}</Typography.Paragraph>}
                </div>}
            </>}
        </Space>
    </Card>;
}
