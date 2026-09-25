import {App, Button, Card, Descriptions, Form, Input, List, Space, Typography} from 'antd';
import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

import {showApiError} from '@/api/errors';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import type {StrategySimDecision, StrategySimFacts, PaperTradingRunItem} from '@/features/paper-trading/types/paper-trading';
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
    const selectedId = isStrategySimRun(selectedRun) ? selectedRun!.paperRunId : null;

    const refresh = async (paperRunId: string) => {
        const [nextDecisions, nextFacts] = await Promise.all([
            paperTradingApi.strategySimDecisions(paperRunId),
            paperTradingApi.strategySimFacts(paperRunId),
        ]);
        setDecisions(nextDecisions);
        setFacts(nextFacts);
    };

    useEffect(() => {
        if (!selectedId) {
            setDecisions([]);
            setFacts(null);
            return;
        }
        let active = true;
        Promise.all([paperTradingApi.strategySimDecisions(selectedId), paperTradingApi.strategySimFacts(selectedId)])
            .then(([nextDecisions, nextFacts]) => {
                if (active) {
                    setDecisions(nextDecisions);
                    setFacts(nextFacts);
                }
            })
            .catch((error) => { if (active) showApiError(error as AppApiError, message); });
        return () => { active = false; };
    }, [selectedId, message]);

    const create = async (values: {publishId: string; budget: string}) => {
        setBusy(true);
        try {
            const created = await paperTradingApi.createStrategySim(values.publishId.trim(), values.budget.trim());
            message.success(t('pages:strategySimCreated'));
            onCreated(created.paperRunId);
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
            <Form layout="inline" onFinish={create}>
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
            </Form>
            {selectedId && <>
                <Space wrap>
                    <Typography.Text>{t('pages:strategySimSelected')}: <Typography.Text code>{selectedId}</Typography.Text></Typography.Text>
                    <Button onClick={advance} disabled={selectedRun?.status !== 'RUNNING'} loading={busy}>
                        {t('pages:strategySimAdvance')}
                    </Button>
                    <Button onClick={() => void refresh(selectedId)} disabled={busy}>{t('pages:strategySimRefresh')}</Button>
                </Space>
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
