import {FrozenFeeSourceSummary} from '@/features/backtests/FrozenFeeSourceSummary';
import {CreationRecoveryPanel} from '@/features/research/components/CreationRecoveryPanel';
import {Alert, App, Button, Card, Collapse, Form, Input, Select, Space, Typography} from 'antd';
import {useSearchParams} from 'react-router-dom';
import {useLayoutEffect, useRef, useState} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {paperTradingQueryKeys, publishesQueryKeys} from '@/api/query-keys';
import {CanonicalSimFacts} from './CanonicalSimFacts';
import {ContinuousSimStatusPanel} from './ContinuousSimStatusPanel';
import {continuousStateMatches} from '../continuous-state';
import {
    ContinuousOperationRecovery,
    usePendingContinuousOperation,
} from './ContinuousOperationRecovery';
import {
    canOperateContinuous,
    continuousOperations,
    continuousOperationErrorKey,
    type ConfirmedContinuousOperation,
} from '../continuous-operations';
import {useAuthStore} from '@/store/auth-store';

import {useTranslation} from 'react-i18next';

import {showApiError} from '@/api/errors';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import type {PaperTradingRunItem} from '@/features/paper-trading/types/paper-trading';
import type {AppApiError} from '@/types/api';
import {publishesApi} from '@/features/publishes/api/publishes';
import {formatApiError} from '@/api/errors';

interface Props {
    selectedRun: PaperTradingRunItem | null;
    onCreated: (paperRunId: string) => void;
}

/** 预算与 publish 是唯一可输入的经济前提；方向和数量始终来自冻结策略与服务端 sizing。 */
export function StrategySimPanel({selectedRun, onCreated}: Props) {
    const {t} = useTranslation('pages');
    const {message, modal} = App.useApp();
    const [busy, setBusy] = useState(false);
    const queryClient = useQueryClient();
    const [form] = Form.useForm<{publishId: string; budget: string}>();
    const [params] = useSearchParams();
    const strategyEnabled = import.meta.env.VITE_STRATEGY_SIM_ENABLED === 'true';
    const continuousEnabled = import.meta.env.VITE_CONTINUOUS_SIM_ENABLED === 'true';
    const currentUser = useAuthStore((state) => state.currentUser);
    const canOperate = canOperateContinuous();
    const selectedId = Boolean(selectedRun?.canonicalAccountId) ? selectedRun!.paperRunId : null;
    const pendingOperation = usePendingContinuousOperation();
    const commandBlocked = pendingOperation.kind !== 'none';
    const [lastOperation, setLastOperation] = useState<ConfirmedContinuousOperation | null>(null);
    const activeScope = useRef({owner: currentUser?.userId, selectedId});
    useLayoutEffect(() => {
        activeScope.current = {owner: currentUser?.userId, selectedId};
    }, [currentUser?.userId, selectedId]);
    const publications = useQuery({
        queryKey: publishesQueryKeys.list({}, 0),
        queryFn: () => publishesApi.list({}),
        enabled: strategyEnabled,
        retry: false,
    });
    const publicationOptions = (publications.data ?? [])
        .filter((p) => p.publishStatus === 'SUCCEEDED')
        .map((p) => ({
            value: p.publishRecordId,
            label:
                (p.publishName || p.publishRecordId) +
                ' · ' +
                (p.strategyVersionId ?? t('pages:notBound')),
        }));

    const continuousQuery = useQuery({
        queryKey: paperTradingQueryKeys.continuous(selectedId ?? ''),
        queryFn: ({signal}) => paperTradingApi.continuousSimStatus(selectedId!, signal),
        enabled: Boolean(selectedId),
        refetchInterval: 30_000,
        retry: false,
    });
    const capabilities = useQuery({
        queryKey: paperTradingQueryKeys.continuousCapabilities,
        queryFn: paperTradingApi.continuousSimCapabilities,
        enabled: strategyEnabled,
        staleTime: 30_000,
        retry: false,
    });
    const identityMismatch = !continuousStateMatches(continuousQuery.data, selectedId ?? '');
    const continuous = identityMismatch ? null : (continuousQuery.data ?? null);
    const continuousActionsAvailable =
        capabilities.isError || !capabilities.data
            ? null
            : continuousEnabled && capabilities.data.commandsAvailable && canOperate;
    const refresh = async (_paperRunId: string) => {
        await queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all});
    };

    const confirmOperation = async (result: ConfirmedContinuousOperation) => {
        if (useAuthStore.getState().currentUser?.userId !== result.ownerUserId) return;
        setLastOperation(result);
        // 原回执可能早于后来的暂停或恢复；仅刷新当前事实，不把历史快照写成当前游标。
        await refresh(result.paperRunId);
    };
    const operationError = (failure: unknown) => {
        const problem = failure as AppApiError;
        const key = continuousOperationErrorKey(problem.code);
        if (key) message.error(t(key));
        else showApiError(problem, message);
    };

    const create = async (values: {publishId: string; budget: string}, continuousMode = false) => {
        const submittedScope = activeScope.current;
        setBusy(true);
        try {
            if (continuousMode) {
                const result = await continuousOperations.submit({
                    type: 'CREATE',
                    publishId: values.publishId.trim(),
                    budget: values.budget.trim(),
                });
                await confirmOperation(result);
                if (
                    activeScope.current.owner === result.ownerUserId &&
                    activeScope.current.selectedId === submittedScope.selectedId
                )
                    onCreated(result.paperRunId);
                return;
            }
            const created = await paperTradingApi.createStrategySim(
                values.publishId.trim(),
                values.budget.trim(),
            );
            message.success(t('pages:strategySimCreated'));
            onCreated(created.paperRunId);
        } catch (error) {
            if (continuousMode) operationError(error);
            else showApiError(error as AppApiError, message);
        } finally {
            setBusy(false);
        }
    };

    const changeContinuous = async (action: 'stop' | 'resume') => {
        if (!selectedId) return;
        setBusy(true);
        try {
            const result = await continuousOperations.submit({
                type: action === 'stop' ? 'PAUSE' : 'RESUME',
                paperRunId: selectedId,
            });
            await confirmOperation(result);
        } catch (error) {
            operationError(error);
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

    const lifecycle = async (action: 'start' | 'stop') => {
        if (!selectedId) return;
        setBusy(true);
        try {
            await paperTradingApi[action](selectedId);
            await refresh(selectedId);
        } catch (error) {
            showApiError(error as AppApiError, message);
        } finally {
            setBusy(false);
        }
    };

    return (
        <Card className="page-section" variant="borderless" title={t('pages:strategySimTitle')}>
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <CreationRecoveryPanel kinds={['SIM_RUN']} />
                <ContinuousOperationRecovery
                    canSubmit={
                        capabilities.isError || !capabilities.data
                            ? null
                            : continuousEnabled && capabilities.data.commandsAvailable
                    }
                    onResult={confirmOperation}
                />
                {lastOperation && lastOperation.ownerUserId === currentUser?.userId && (
                    <Alert
                        type="success"
                        showIcon
                        message={t('continuousOperations.completed', {
                            action: t(`continuousOperations.${lastOperation.type}`),
                        })}
                        description={
                            <Space wrap>
                                <Typography.Text>
                                    {t('continuousOperations.originalResult')}
                                </Typography.Text>
                                <Button
                                    size="small"
                                    onClick={() => onCreated(lastOperation.paperRunId)}
                                >
                                    {t('continuousOperations.viewRun')}
                                </Button>
                            </Space>
                        }
                    />
                )}
                {selectedRun && (
                    <FrozenFeeSourceSummary configuration={selectedRun.configSnapshotJson} />
                )}
                {!strategyEnabled && (
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:workflowSimUnavailable')}
                        description={
                            params.get('publishId') ? (
                                <Typography.Text copyable>
                                    {params.get('publishId')}
                                </Typography.Text>
                            ) : undefined
                        }
                    />
                )}
                {publications.isError && (
                    <Alert
                        type="error"
                        message={formatApiError(publications.error as AppApiError)}
                        action={
                            <Button onClick={() => void publications.refetch()}>
                                {t('pages:retry')}
                            </Button>
                        }
                    />
                )}
                {strategyEnabled && (
                    <Collapse
                        key={selectedId ?? 'new'}
                        defaultActiveKey={selectedRun ? [] : ['create']}
                        items={[
                            {
                                key: 'create',
                                label: t('pages:strategySimCreate'),
                                children: (
                                    <>
                                        <Typography.Paragraph type="secondary">
                                            {t('pages:strategySimDescription')}
                                        </Typography.Paragraph>
                                        <Typography.Paragraph type="secondary">
                                            {t('pages:discountFee.simInheritance')}
                                        </Typography.Paragraph>
                                        <Form
                                            name="strategy-sim-create"
                                            form={form}
                                            layout="vertical"
                                            initialValues={{
                                                publishId: params.get('publishId') ?? '',
                                            }}
                                            onFinish={(values) => {
                                                void create(values);
                                            }}
                                        >
                                            <Form.Item
                                                name="publishId"
                                                label={t('pages:publishedStrategy')}
                                                rules={[
                                                    {
                                                        required: true,
                                                        message: t(
                                                            'pages:strategySimPublishRequired',
                                                        ),
                                                    },
                                                ]}
                                            >
                                                <Select
                                                    showSearch
                                                    optionFilterProp="label"
                                                    loading={publications.isFetching}
                                                    options={publicationOptions}
                                                    style={{width: 'min(460px, 80vw)'}}
                                                    placeholder={t('pages:selectPublishedStrategy')}
                                                />
                                            </Form.Item>
                                            <Form.Item
                                                name="budget"
                                                label={t('pages:strategySimBudget')}
                                                rules={[
                                                    {
                                                        required: true,
                                                        pattern: /^\d+(\.\d{1,8})?$/,
                                                        message: t(
                                                            'pages:strategySimBudgetInvalid',
                                                        ),
                                                    },
                                                ]}
                                            >
                                                <Input
                                                    inputMode="decimal"
                                                    placeholder="10 / 100 / 1000"
                                                    style={{width: 160}}
                                                />
                                            </Form.Item>
                                            <Button
                                                type="primary"
                                                htmlType="submit"
                                                loading={busy}
                                                disabled={commandBlocked || !canOperate}
                                            >
                                                {t('pages:strategySimCreate')}
                                            </Button>
                                            <Button
                                                disabled={
                                                    busy ||
                                                    commandBlocked ||
                                                    continuousActionsAvailable !== true
                                                }
                                                onClick={() => {
                                                    void form
                                                        .validateFields()
                                                        .then((values) => create(values, true))
                                                        .catch(() => undefined);
                                                }}
                                            >
                                                {t('pages:continuousSimStart')}
                                            </Button>
                                            {continuousActionsAvailable !== true && (
                                                <Typography.Paragraph type="secondary">
                                                    {t(
                                                        continuousActionsAvailable === false
                                                            ? 'continuousWorkspace.commandsOff'
                                                            : 'continuousWorkspace.commandsUnknown',
                                                    )}
                                                </Typography.Paragraph>
                                            )}
                                        </Form>
                                    </>
                                ),
                            },
                        ]}
                    />
                )}
                {selectedId && (
                    <>
                        <Space wrap>
                            <Button
                                type="primary"
                                disabled={
                                    busy || commandBlocked || selectedRun?.status !== 'CREATED'
                                }
                                onClick={() => void lifecycle('start')}
                            >
                                {t('pages:startPaperRun')}
                            </Button>
                            <Button
                                danger
                                disabled={
                                    busy || commandBlocked || selectedRun?.status !== 'RUNNING'
                                }
                                onClick={() =>
                                    modal.confirm({
                                        title: t('pages:stopPaperRun'),
                                        content: t('pages:simStopMeaning'),
                                        onOk: () => lifecycle('stop'),
                                    })
                                }
                            >
                                {t('pages:stopPaperRun')}
                            </Button>
                            <Button
                                onClick={advance}
                                disabled={
                                    !strategyEnabled ||
                                    commandBlocked ||
                                    continuousQuery.isPending ||
                                    continuousQuery.isError ||
                                    identityMismatch ||
                                    selectedRun?.status !== 'RUNNING' ||
                                    continuous !== null
                                }
                                loading={busy}
                            >
                                {t('pages:strategySimAdvance')}
                            </Button>
                            <Button onClick={() => void refresh(selectedId)} disabled={busy}>
                                {t('pages:strategySimRefresh')}
                            </Button>
                        </Space>
                        <ContinuousSimStatusPanel
                            status={continuous}
                            loading={continuousQuery.isFetching}
                            error={
                                continuousQuery.isError
                                    ? (continuousQuery.error as AppApiError)
                                    : null
                            }
                            identityMismatch={identityMismatch}
                            commandsAvailable={continuousActionsAvailable}
                            parentStatus={selectedRun?.status ?? ''}
                            busy={busy || commandBlocked}
                            onRefresh={() => {
                                void continuousQuery.refetch();
                                void capabilities.refetch();
                            }}
                            onAction={(action) => void changeContinuous(action)}
                        />
                        <CanonicalSimFacts paperRunId={selectedId} details />
                    </>
                )}
            </Space>
        </Card>
    );
}
