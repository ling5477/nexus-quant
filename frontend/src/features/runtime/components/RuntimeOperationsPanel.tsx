import {Alert, Card, Empty, List, Space, Tag, Timeline, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {schedulerApi} from '@/features/scheduler/api/scheduler';
import {schedulerQueryKeys} from '@/api/query-keys';
import {formatDateTime} from '@/utils/formatters';
import type {ScheduledJobControl} from '@/features/scheduler/types';
import {StatusTag, type StatusTone} from '@/nq-design-system';
import './RuntimeOperationsPanel.css';

/** 每个任务只展示最近一次持久事件；不是完整审计历史，也不把缺失心跳推断为健康。 */
export function SchedulerActivity({jobs}: {jobs: ScheduledJobControl[]}) {
    const {t} = useTranslation('product');
    const events = jobs.filter(job => job.lastFinishedAt || job.lastStartedAt)
        .sort((a, b) => (b.lastFinishedAt ?? b.lastStartedAt ?? '').localeCompare(a.lastFinishedAt ?? a.lastStartedAt ?? ''));
    return <Card size="small" title={t('lastTaskEvents')}>
        <Typography.Paragraph type="secondary">{t('lastTaskEventsLimit')}</Typography.Paragraph>
        {events.length === 0 ? <Empty description={t('noTaskEvents')} /> : <Timeline items={events.map(job => ({
            color: job.lastStatus === 'FAILED' ? 'red' : job.lastStatus === 'SUCCESS' ? 'green' : 'blue',
            children: <Space direction="vertical" size={2}>
                <Link to="/system/scheduler">{job.jobKey}</Link>
                <Typography.Text>{t(`pages:schedulerStatus_${job.lastStatus}`)}</Typography.Text>
                <Typography.Text type="secondary">{formatDateTime(job.lastFinishedAt ?? job.lastStartedAt)}</Typography.Text>
                {job.lastErrorCode && <Typography.Text type="danger">{job.lastErrorCode}</Typography.Text>}
                {job.activeRunId && <Typography.Text copyable>{job.activeRunId}</Typography.Text>}
            </Space>,
        }))} />}
    </Card>;
}

interface BoundaryItem {key: string; area: string; status: string; safeState: string; tone: StatusTone; reasonCode: string; reason: string}

/** 安全边界与任务事件分区；所有状态来自已有只读DTO，无合成健康评分。 */
export function RuntimeOperationsPanel({items}: {items: BoundaryItem[]}) {
    const {t} = useTranslation('product');
    const jobs = useQuery({queryKey: schedulerQueryKeys.list(), queryFn: schedulerApi.list, retry: false, refetchInterval: 30_000});
    return <div className="nq-runtime-operations">
        <section className="nq-runtime-boundaries" aria-label={t('boundaryStatus')}>
            {items.map(item => <Card size="small" key={item.key} title={item.area}
                extra={<StatusTag label={item.status} tone={item.tone} variant="pill" />}>
                <Typography.Paragraph>{item.reason}</Typography.Paragraph>
                <Tag>{item.reasonCode}</Tag>
            </Card>)}
        </section>
        <aside className="nq-runtime-events">
            <Card size="small" title={t('taskSnapshot')} loading={jobs.isPending}>
                {jobs.isError ? <Alert type="error" message={t('taskSnapshotUnavailable')} /> : <>
                    <div className="nq-monitor-counts">
                        <span>{t('taskEnabled')} <strong>{jobs.data?.filter(job => job.enabled).length ?? '—'}</strong></span>
                        <span>{t('taskFailures')} <strong>{jobs.data?.filter(job => job.lastStatus === 'FAILED').length ?? '—'}</strong></span>
                    </div>
                    <List size="small" dataSource={(jobs.data ?? []).filter(job => job.lastStatus === 'FAILED')} locale={{emptyText: t('noTaskFailures')}}
                        renderItem={job => <List.Item><Link to="/system/scheduler">{job.jobKey}</Link><Tag color="error">{job.lastErrorCode ?? t('statusUnknown')}</Tag></List.Item>} />
                </>}
                <Link to="/system/scheduler">{t('openTaskControls')} →</Link>
            </Card>
            {!jobs.isError && <SchedulerActivity jobs={jobs.data ?? []} />}
        </aside>
    </div>;
}
