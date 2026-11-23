import {Alert, Button, Descriptions, Space, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router-dom';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {continuousJobKeys, continuousPollFreshness} from '../continuous-operations-view';
import {useContinuousSimOperations} from '../hooks/useContinuousSimOperations';
import type {ContinuousSimStatus} from '../types/paper-trading';

/** 调度、健康和游标各自说明来源；共享job成功不能覆盖单run的阻断事实。 */
export function ContinuousSimOperationsPanel({run, status, readable, parentStatus, commandsAvailable, checkedAt}: {
    run: string; status: ContinuousSimStatus | null; readable: boolean; parentStatus: string;
    commandsAvailable: boolean | null; checkedAt: number;
}) {
    const {t} = useTranslation('pages');
    const {jobs, health} = useContinuousSimOperations(run, !!run && (status !== null || !readable));
    if (readable && !status) return null;
    const rows = !jobs.isError ? jobs.data : undefined;
    const poll = rows?.get('CONTINUOUS_SIM_POLL');
    const freshness = continuousPollFreshness(status, poll?.fixedDelayMs ?? null, Date.now());
    const time = (at: string | null | undefined) => at ?? t('continuousOperationsView.unknown');
    return <section className="continuous-workspace" data-testid="continuous-sim-operations" aria-label={t('continuousOperationsView.title')}>
        <div className="continuous-workspace__header"><Typography.Text strong>{t('continuousOperationsView.title')}</Typography.Text>
            <Button aria-label={t('continuousWorkspace.refresh')} loading={jobs.isFetching || health.isFetching} onClick={() => {void jobs.refetch(); void health.refetch();}}>{t('continuousWorkspace.refresh')}</Button></div>
        <Typography.Paragraph type="secondary">{t('continuousOperationsView.meaning')}</Typography.Paragraph>
        <Descriptions size="small" column={1} styles={{content: {overflowWrap: 'anywhere'}}} items={[
            {key: 'parent', label: t('continuousOperationsView.parent'), children: parentStatus},
            {key: 'cursor', label: t('continuousSimStatus'), children: readable ? status?.status ?? t('continuousOperationsView.unknown') : t('continuousOperationsView.unknown')},
            {key: 'commands', label: t('continuousOperationsView.capability'), children: t(commandsAvailable === null ? 'continuousOperationsView.unknown' : commandsAvailable ? 'continuousOperationsView.present' : 'continuousOperationsView.absent')},
            {key: 'checked', label: t('continuousOperationsView.cursorReadAt'), children: checkedAt ? new Date(checkedAt).toISOString() : t('continuousOperationsView.unknown')},
            {key: 'health', label: t('continuousOperationsView.health'), children: health.isError ? t('continuousOperationsView.unknown') : health.data?.status ?? t('continuousOperationsView.unknown')},
            {key: 'healthTime', label: t('continuousOperationsView.healthAt'), children: !health.isError ? time(health.data?.checkedAt) : '—'},
        ]}/>
        <Alert type={freshness === 'STALE' ? 'warning' : 'info'} showIcon message={t(`continuousOperationsView.freshness.${freshness}`)} description={t('continuousOperationsView.freshnessMeaning')}/>
        {jobs.isError && <Alert type="error" showIcon message={t('continuousOperationsView.jobsFailed')} description={formatApiError(jobs.error as AppApiError)}/>}
        {health.isError && <Alert type="error" showIcon message={t('continuousOperationsView.healthFailed')} description={formatApiError(health.error as AppApiError)}/>}
        {continuousJobKeys.map(key => {
            const job = rows?.get(key);
            return <div key={key} className="continuous-workspace" data-testid={`continuous-job-${key}`}>
                <Space wrap><Typography.Text strong>{t(`continuousOperationsView.jobs.${key}`)}</Typography.Text>
                    <Tag color={job?.enabled ? 'processing' : 'default'}>{t(!job ? 'continuousOperationsView.unknown' : job.enabled ? 'continuousOperationsView.enabled' : 'continuousOperationsView.disabled')}</Tag></Space>
                <Descriptions size="small" column={{xs: 1, md: 2}} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                    {key: 'delay', label: t('continuousOperationsView.delay'), children: job ? `${job.fixedDelayMs} ms` : '—'},
                    {key: 'status', label: t('continuousOperationsView.lastResult'), children: job?.lastStatus ?? '—'},
                    {key: 'start', label: t('continuousOperationsView.lastStart'), children: time(job?.lastStartedAt)},
                    {key: 'finish', label: t('continuousOperationsView.lastFinish'), children: time(job?.lastFinishedAt)},
                    {key: 'next', label: t('continuousOperationsView.nextRun'), children: time(job?.nextRunAt)},
                    {key: 'failures', label: t('continuousOperationsView.failures'), children: job?.consecutiveFailures ?? '—'},
                    {key: 'reason', label: t('forwardObservation.reason'), children: job?.lastErrorCode ?? '—'},
                ]}/>
                {job?.enabled === false && <Alert type="warning" showIcon message={t('continuousOperationsView.jobOff')}/>}
            </div>;
        })}
        <Typography.Text type="secondary">{t('continuousOperationsView.jobsReadAt')} {jobs.dataUpdatedAt ? new Date(jobs.dataUpdatedAt).toISOString() : '—'}</Typography.Text>
        <Space wrap><Link to="/system/scheduler">{t('continuousOperationsView.scheduler')}</Link>
            <Link to={`/paper-trading/diagnostics?paperRunId=${encodeURIComponent(run)}`}>{t('continuousOperationsView.facts')}</Link>
            <Link to={`/paper-trading/reviews?paperRunId=${encodeURIComponent(run)}`}>{t('continuousOperationsView.evaluation')}</Link></Space>
    </section>;
}
