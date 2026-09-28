import {App, Button, Card, Descriptions, Form, Input, List, Space, Typography} from 'antd';
import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

import {showApiError} from '@/api/errors';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import type {ContinuousSimStatus, StrategySimDecision, StrategySimFacts, PaperTradingRunItem} from '@/features/paper-trading/types/paper-trading';
import type {AppApiError} from '@/types/api';

interface Props {
    selectedRun: PaperTradingRunItem | null;
    onCreated: (paperRunId: string) => void;
}

function isStrategySimRun(run: PaperTradingRunItem | null): boolean {
    if (!run?.configSnapshotJson) return false;
    try {
        return typeof JSON.parse(run.configSnapshotJson).barContentSha256 === 'string';
    } catch {
        return false;
    }
}

/** 预算与 publish 是唯一可输入的经济前提；方向和数量始终来自冻结策略与服务端 sizing。 */
export function StrategySimPanel({selectedRun, onCreated}: Props) {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const [busy, setBusy] = useState(false);
    const [decisions, setDecisions] = useState<StrategySimDecision[]>([]);
    const [facts, setFacts] = useState<StrategySimFacts | null>(null);
    const [continuous, setContinuous] = useState<ContinuousSimStatus | null>(null);
    const [form] = Form.useForm<{publishId: string; budget: string}>();
    const continuousEnabled = import.meta.env.VITE_CONTINUOUS_SIM_ENABLED === 'true';
    const selectedId = isStrategySimRun(selectedRun) ? selectedRun!.paperRunId : null;

    const refresh = async (paperRunId: string) => {
        const [nextDecisions, nextFacts, nextContinuous] = await Promise.all([
            paperTradingApi.strategySimDecisions(paperRunId),
            paperTradingApi.strategySimFacts(paperRunId),
            continuousEnabled ? paperTradingApi.continuousSimStatus(paperRunId) : Promise.resolve(null),
        ]);
        setDecisions(nextDecisions);
        setFacts(nextFacts);
        setContinuous(nextContinuous);
    };

    useEffect(() => {
        if (!selectedId) {
            setDecisions([]);
            setFacts(null);
            setContinuous(null);
            return;
        }
        let active = true;
        const load = () => Promise.all([
            paperTradingApi.strategySimDecisions(selectedId),
            paperTradingApi.strategySimFacts(selectedId),
            continuousEnabled ? paperTradingApi.continuousSimStatus(selectedId) : Promise.resolve(null),
        ]).then(([nextDecisions, nextFacts, nextContinuous]) => {
                if (active) {
                    setDecisions(nextDecisions);
                    setFacts(nextFacts);
                    setContinuous(nextContinuous);
                }
            }).catch((error) => { if (active) showApiError(error as AppApiError, message); });
        void load();
        const interval = continuousEnabled ? window.setInterval(() => { void load(); }, 30_000) : null;
        return () => {
            active = false;
            if (interval !== null) window.clearInterval(interval);
        };
    }, [selectedId, message, continuousEnabled]);

    const create = async (values: {publishId: string; budget: string}, continuousMode = false) => {
        setBusy(true);
        try {
            const created = continuousMode
                ? await paperTradingApi.startContinuousSim(values.publishId.trim(), values.budget.trim())
                : await paperTradingApi.createStrategySim(values.publishId.trim(), values.budget.trim());
            message.success(t('pages:strategySimCreated'));
            onCreated(created.paperRunId);
        } catch (error) {
            showApiError(error as AppApiError, message);
        } finally {
            setBusy(false);
        }
    };

    const changeContinuous = async (action: 'stop' | 'resume') => {
        if (!selectedId) return;
        setBusy(true);
        try {
            const next = action === 'stop'
                ? await paperTradingApi.stopContinuousSim(selectedId)
                : await paperTradingApi.resumeContinuousSim(selectedId);
            setContinuous(next);
            await refresh(selectedId);
        } catch (error) {
            showApiError(error as AppApiError, message);
        } finally {
            setBusy(false);
        }
    };

    const advance = async () => {
        if (!selectedId) return;
        setBusy(true);
        try {
            await paperTradingApi.advanceStrategySim(selectedId);
            await refresh(selectedId);
            message.success(t('pages:strategySimDecisionSaved'));
        } catch (error) {
            showApiError(error as AppApiError, message);
        } finally {
            setBusy(false);
        }
    };

    return <Card className="page-section" variant="borderless" title={t('pages:strategySimTitle')}>
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Typography.Text type="secondary">{t('pages:strategySimDescription')}</Typography.Text>
            <Form form={form} layout="inline" onFinish={(values) => { void create(values); }}>
                <Form.Item name="publishId" label={t('pages:publishId')}
                           rules={[{required: true, message: t('pages:strategySimPublishRequired')}]}>
                    <Input style={{width: 220}}/>
                </Form.Item>
                <Form.Item name="budget" label={t('pages:strategySimBudget')}
                           rules={[{required: true, pattern: /^\d+(\.\d{1,8})?$/,
                               message: t('pages:strategySimBudgetInvalid')}]}>
                    <Input inputMode="decimal" placeholder="10 / 100 / 1000" style={{width: 160}}/>
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={busy}>{t('pages:strategySimCreate')}</Button>
                {continuousEnabled && <Button disabled={busy} onClick={() => {
                    void form.validateFields().then((values) => create(values, true)).catch(() => undefined);
                }}>{t('pages:continuousSimStart')}</Button>}
            </Form>
            {selectedId && <>
                <Space wrap>
                    <Typography.Text>{t('pages:strategySimSelected')}: <Typography.Text code>{selectedId}</Typography.Text></Typography.Text>
                    <Button onClick={advance} disabled={selectedRun?.status !== 'RUNNING' || continuous !== null} loading={busy}>
                        {t('pages:strategySimAdvance')}
                    </Button>
                    <Button onClick={() => void refresh(selectedId)} disabled={busy}>{t('pages:strategySimRefresh')}</Button>
                </Space>
                {continuous && <>
                    <Space wrap>
                        <Typography.Text strong>{t('pages:continuousSimStatus')}: {continuous.status}</Typography.Text>
                        {continuous.status === 'STOPPED'
                            ? <Button disabled={busy || continuous.blockReason === 'DATA_REVISION_DETECTED'}
                                      onClick={() => { void changeContinuous('resume'); }}>{t('pages:continuousSimResume')}</Button>
                            : <Button disabled={busy} onClick={() => { void changeContinuous('stop'); }}>{t('pages:continuousSimStop')}</Button>}
                    </Space>
                    <Descriptions size="small" bordered column={{xs: 1, md: 2}}>
                        <Descriptions.Item label={t('pages:continuousSimLastBar')}>{continuous.lastProcessedBar}</Descriptions.Item>
                        <Descriptions.Item label={t('pages:continuousSimNextBar')}>{continuous.nextExpectedBar}</Descriptions.Item>
                        <Descriptions.Item label={t('pages:continuousSimLastAvailable')}>{continuous.lastObservedBar}</Descriptions.Item>
                        <Descriptions.Item label={t('pages:continuousSimGapStart')}>{continuous.gapStartBar ?? '—'}</Descriptions.Item>
                        <Descriptions.Item label={t('pages:continuousSimLastDecision')}>{continuous.lastDecisionId ?? '—'} / {continuous.lastDecisionStatus ?? '—'}</Descriptions.Item>
                        <Descriptions.Item label={t('pages:continuousSimReason')}>{continuous.blockReason ?? '—'}</Descriptions.Item>
                        <Descriptions.Item label={t('pages:continuousSimLastPoll')}>{continuous.lastPollAt ?? '—'}</Descriptions.Item>
                    </Descriptions>
                </>}
                {facts && <Descriptions size="small" bordered column={{xs: 1, md: 2, xl: 3}}>
                    <Descriptions.Item label={t('pages:strategySimVersion')}>{facts.strategyVersionId}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:strategySimInput')}>{facts.inputSha256}</Descriptions.Item>
                    <Descriptions.Item label={t('pages:strategySimCash')}>{facts.cash} USDT</Descriptions.Item>
                    <Descriptions.Item label={t('pages:strategySimPosition')}>{facts.positionQuantity} BTC</Descriptions.Item>
                    <Descriptions.Item label={t('pages:strategySimEquity')}>{facts.equity} USDT</Descriptions.Item>
                    <Descriptions.Item label={t('pages:strategySimPnl')}>{facts.pnl} USDT</Descriptions.Item>
                    <Descriptions.Item label={t('pages:strategySimCanonicalFacts')}>
                        {facts.orders.length} / {facts.trades.length} / {facts.ledgerEntries.length}
                        {' · '}<Link to="/trading">{t('pages:strategySimOpenTrading')}</Link>
                    </Descriptions.Item>
                </Descriptions>}
                <List size="small" bordered header={t('pages:strategySimDecisions')}
                      locale={{emptyText: t('pages:strategySimNoDecisions')}}
                      dataSource={decisions} renderItem={(decision) => <List.Item key={decision.decisionId}>
                          <Space direction="vertical" size={0}>
                              <Typography.Text>{decision.status} · {decision.reason} · {decision.side ?? '—'} {decision.quantity ?? '—'}</Typography.Text>
                              <Typography.Text type="secondary" copyable={Boolean(decision.orderId)}>
                                  {decision.orderId ?? decision.signalOpenTime}
                              </Typography.Text>
                          </Space>
                      </List.Item>}/>
            </>}
        </Space>
    </Card>;
}
