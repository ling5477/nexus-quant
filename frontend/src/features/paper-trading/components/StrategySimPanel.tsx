import {App, Button, Card, Form, Input, Space, Typography} from 'antd';
import {useState} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {CanonicalSimFacts} from './CanonicalSimFacts';

import {useTranslation} from 'react-i18next';

import {showApiError} from '@/api/errors';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import type {PaperTradingRunItem} from '@/features/paper-trading/types/paper-trading';
import type {AppApiError} from '@/types/api';

interface Props {
    selectedRun: PaperTradingRunItem | null;
    onCreated: (paperRunId: string) => void;
}

/** 预算与 publish 是唯一可输入的经济前提；方向和数量始终来自冻结策略与服务端 sizing。 */
export function StrategySimPanel({selectedRun, onCreated}: Props) {
    const {t} = useTranslation('pages');
    const {message} = App.useApp();
    const [busy, setBusy] = useState(false);
    const queryClient = useQueryClient();
    const [form] = Form.useForm<{publishId: string; budget: string}>();
    const strategyEnabled = import.meta.env.VITE_STRATEGY_SIM_ENABLED === 'true';
    const continuousEnabled = import.meta.env.VITE_CONTINUOUS_SIM_ENABLED === 'true';
    const selectedId = Boolean(selectedRun?.canonicalAccountId) ? selectedRun!.paperRunId : null;

    const continuousQuery = useQuery({queryKey: paperTradingQueryKeys.continuous(selectedId ?? ''),
        queryFn: () => paperTradingApi.continuousSimStatus(selectedId!), enabled: Boolean(selectedId), retry: false});
    const continuous = continuousQuery.isError ? null : continuousQuery.data ?? null;
    const refresh = async (_paperRunId: string) => {
        await queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
    };

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
            queryClient.setQueryData(paperTradingQueryKeys.continuous(selectedId), next);
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
            {strategyEnabled && <Form form={form} layout="inline" onFinish={(values) => { void create(values); }}>
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
            </Form>}
            {selectedId && <>
                <Space wrap>
                    <Typography.Text>{t('pages:strategySimSelected')}: <Typography.Text code>{selectedId}</Typography.Text></Typography.Text>
                    <Button onClick={advance} disabled={!strategyEnabled || continuousQuery.isPending || continuousQuery.isError || selectedRun?.status !== 'RUNNING' || continuous !== null} loading={busy}>
                        {t('pages:strategySimAdvance')}
                    </Button>
                    <Button onClick={() => void refresh(selectedId)} disabled={busy}>{t('pages:strategySimRefresh')}</Button>
                </Space>
                {continuous && <>
                    <Space wrap>
                        <Typography.Text strong>{t('pages:continuousSimStatus')}: {continuous.status}</Typography.Text>
                        {continuous.status === 'STOPPED'
                            ? <Button disabled={!continuousEnabled || busy || continuous.blockReason === 'DATA_REVISION_DETECTED'}
                                      onClick={() => { void changeContinuous('resume'); }}>{t('pages:continuousSimResume')}</Button>
                            : <Button disabled={!continuousEnabled || busy} onClick={() => { void changeContinuous('stop'); }}>{t('pages:continuousSimStop')}</Button>}
                    </Space>

                </>}
                <CanonicalSimFacts paperRunId={selectedId} details/>
            </>}
        </Space>
    </Card>;
}
