import {Alert, App, Button, Collapse, Descriptions, Space, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatDateTime} from '@/utils/formatters';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import type {ContinuousSimStatus} from '../types/paper-trading';
import {continuousAction, continuousStatusKey} from '../continuous-state';
import './ContinuousSimStatusPanel.css';

interface Props {
    status: ContinuousSimStatus | null;
    loading: boolean;
    error: AppApiError | null;
    identityMismatch: boolean;
    commandsAvailable: boolean | null;
    parentStatus: string;
    busy: boolean;
    onRefresh: () => void;
    onAction: (action: 'stop' | 'resume' | 'end') => void;
}

/** 展示原游标与最近轮询事实；不计算假百分比，也不把暂停轮询表述为结束父SIM。 */
export function ContinuousSimStatusPanel(props: Props) {
    const {t} = useTranslation('pages');
    const {modal} = App.useApp();
    const {
        status,
        loading,
        error,
        identityMismatch,
        commandsAvailable,
        parentStatus,
        busy,
        onRefresh,
        onAction,
    } = props;
    const available = !identityMismatch ? status : null;
    const action = available ? continuousAction(available, parentStatus) : null;
    const bounded = available?.bounded;
    const time = (value: string | null) =>
        value ? (
            <time dateTime={value} title={value}>
                {formatDateTime(value)}
            </time>
        ) : (
            <span>{t('continuousWorkspace.notRecorded')}</span>
        );
    return (
        <section
            className="continuous-workspace"
            data-testid="continuous-sim-status"
            aria-label={t('continuousWorkspace.title')}
        >
            <div className="continuous-workspace__header">
                <Typography.Text strong>{t('continuousWorkspace.title')}</Typography.Text>
                <Space wrap>
                    {available && (
                        <Tag
                            color={
                                available.status === 'RUNNING'
                                    ? 'processing'
                                    : available.status === 'STALLED'
                                      ? 'warning'
                                      : 'default'
                            }
                            title={bounded?.lifecycleStatus ?? available.status}
                        >
                            {bounded ? t(`boundedSim.states.${bounded.lifecycleStatus}`, {defaultValue: bounded.lifecycleStatus}) : t(
                                `continuousWorkspace.states.${continuousStatusKey(available.status)}`,
                            )}
                        </Tag>
                    )}
                    {available && error && (
                        <Typography.Text type="warning">
                            {t('continuousWorkspace.stale')}
                        </Typography.Text>
                    )}
                    <Button
                        size="small"
                        loading={loading}
                        disabled={loading}
                        aria-busy={loading}
                        aria-label={t('continuousWorkspace.refresh')}
                        onClick={onRefresh}
                    >
                        {t('continuousWorkspace.refresh')}
                    </Button>
                </Space>
            </div>
            {identityMismatch && (
                <Alert type="error" showIcon message={t('continuousWorkspace.identityMismatch')} />
            )}
            {!identityMismatch && error && (
                <Alert
                    type="error"
                    showIcon
                    message={t('continuousWorkspace.readFailed')}
                    description={formatApiError(error)}
                />
            )}
            {!identityMismatch && !error && !available && (
                <Typography.Text type="secondary">
                    {t(
                        loading
                            ? 'continuousWorkspace.loading'
                            : 'continuousWorkspace.notContinuous',
                    )}
                </Typography.Text>
            )}
            {available && (
                <>
                    <Typography.Paragraph type="secondary">
                        {t(bounded ? 'boundedSim.meaning' : 'continuousWorkspace.meaning')}
                    </Typography.Paragraph>
                    <div className="continuous-workspace__progress">
                        <div>
                            <span>{t('continuousSimLastBar')}</span>
                            {time(available.lastProcessedBar)}
                        </div>
                        {!bounded && <div>
                            <span>{t('continuousSimNextBar')}</span>
                            {time(available.nextExpectedBar)}
                        </div>}
                        <div>
                            <span>{t('continuousSimLastAvailable')}</span>
                            {time(available.lastObservedBar)}
                        </div>
                    </div>
                    {bounded && <Descriptions size="small" column={{xs: 1, md: 2}} styles={{content: {overflowWrap: 'anywhere'}}}>
                        <Descriptions.Item label={t('boundedSim.deadline')}>
                            <time dateTime={bounded.endAt}>{bounded.endAt}</time>
                        </Descriptions.Item>
                        {bounded.manualEndAt && <Descriptions.Item label={t('boundedSim.manualEndAt')}>
                            <time dateTime={bounded.manualEndAt}>{bounded.manualEndAt}</time>
                        </Descriptions.Item>}
                        {bounded.terminatedAt && <Descriptions.Item label={t('boundedSim.terminatedAt')}>
                            <time dateTime={bounded.terminatedAt}>{bounded.terminatedAt}</time>
                        </Descriptions.Item>}
                        <Descriptions.Item label={t('boundedSim.finalValuation')}>
                            {t(`boundedSim.valuation.${bounded.finalValuationStatus}`, {defaultValue: bounded.finalValuationStatus})}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('boundedSim.lastValuation')}>
                            {bounded.lastValuationBoundary ?? t('continuousWorkspace.notRecorded')}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('boundedSim.pendingOrders')}>
                            {bounded.pendingOrders ?? t('continuousOperationsView.unknown')}
                        </Descriptions.Item>
                        {bounded.reason && <Descriptions.Item label={t('forwardObservation.reason')}>
                            <Typography.Text code>{bounded.reason}</Typography.Text>
                        </Descriptions.Item>}
                    </Descriptions>}
                    {available.blockReason && available.blockReason !== bounded?.reason && (
                        <Alert
                            type={available.status === 'STALLED' ? 'warning' : 'info'}
                            showIcon
                            message={t(`continuousWorkspace.reasons.${available.blockReason}`, {
                                defaultValue: t('continuousWorkspace.reasonUnknown'),
                            })}
                            description={
                                <Typography.Text code>{available.blockReason}</Typography.Text>
                            }
                        />
                    )}
                    <Descriptions size="small" column={{xs: 1, md: 2}}>
                        <Descriptions.Item label={t('continuousWorkspace.lastPoll')}>
                            {time(available.lastPollAt)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('continuousWorkspace.lastSuccess')}>
                            {time(available.lastSuccessfulPollAt)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('continuousSimGapStart')}>
                            {time(available.gapStartBar)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('continuousWorkspace.failures')}>
                            {available.consecutivePollFailures}
                        </Descriptions.Item>
                    </Descriptions>
                    <Space wrap>
                        {action && (
                            <Button
                                disabled={
                                    commandsAvailable !== true || busy || loading || Boolean(error)
                                }
                                danger={action === 'end'}
                                onClick={() => {
                                    if (action === 'end') {
                                        modal.confirm({title: t('boundedSim.end'), content: t('boundedSim.endMeaning'),
                                            okText: t('boundedSim.end'), cancelText: t('cancel'), onOk: () => onAction('end')});
                                    } else onAction(action === 'pause' ? 'stop' : 'resume');
                                }}
                            >
                                {t(
                                    action === 'end' ? 'boundedSim.end' : action === 'pause'
                                        ? 'continuousWorkspace.pause'
                                        : 'continuousWorkspace.resume',
                                )}
                            </Button>
                        )}
                        {commandsAvailable !== true && (
                            <Typography.Text type="secondary">
                                {t(
                                    commandsAvailable === false
                                        ? 'continuousWorkspace.commandsOff'
                                        : 'continuousWorkspace.commandsUnknown',
                                )}
                            </Typography.Text>
                        )}
                        {parentStatus !== 'RUNNING' && (
                            <Typography.Text type="secondary">
                                {t('continuousWorkspace.parentNotRunning')}
                            </Typography.Text>
                        )}
                    </Space>
                    <Collapse
                        size="small"
                        items={[
                            {
                                key: 'source',
                                label: t('continuousWorkspace.source'),
                                children: (
                                    <Descriptions size="small" column={1}>
                                        <Descriptions.Item label={t('simContinuousIdentity')}>
                                            <Typography.Text
                                                copyable
                                                style={{overflowWrap: 'anywhere'}}
                                            >
                                                {available.paperRunId}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('continuousWorkspace.started')}>
                                            {time(available.startedAt)}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('continuousSimLastDecision')}>
                                            <Typography.Text
                                                style={{overflowWrap: 'anywhere'}}
                                                copyable={Boolean(available.lastDecisionId)}
                                            >
                                                {available.lastDecisionId ??
                                                    t('continuousWorkspace.notRecorded')}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('continuousWorkspace.decisionStatus')}
                                        >
                                            {available.lastDecisionStatus ??
                                                t('continuousWorkspace.notRecorded')}
                                        </Descriptions.Item>
                                        <Descriptions.Item label={t('continuousSimStatus')}>
                                            <Typography.Text code>
                                                {available.status}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                    </Descriptions>
                                ),
                            },
                        ]}
                    />
                </>
            )}
        </section>
    );
}
