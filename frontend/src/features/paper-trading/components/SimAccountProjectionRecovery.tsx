import {useState} from 'react';
import {useMutation, useQuery} from '@tanstack/react-query';
import {Alert, App, Button, Descriptions, Space, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {paperTradingQueryKeys} from '@/api/query-keys';
import type {AppApiError} from '@/types/api';
import {
    projectionRecoveryApi,
    type FinalizationRequest,
    type ProjectionRecoveryRequest,
} from '../api/projection-recovery';

interface Props {
    run: string;
    owner?: number;
    session: string | null;
    eligible: boolean;
    onUpdated: () => void;
}

export function SimAccountProjectionRecovery({run, owner, session, eligible, onUpdated}: Props) {
    const {t} = useTranslation('pages');
    const {modal, message} = App.useApp();
    const [opened, setOpened] = useState(false);
    const [recoveryRequest, setRecoveryRequest] = useState<ProjectionRecoveryRequest>();
    const [finalizationRequest, setFinalizationRequest] = useState<FinalizationRequest>();
    const preview = useQuery({
        queryKey: paperTradingQueryKeys.operations(owner, session, run, 'projection-recovery'),
        queryFn: ({signal}) => projectionRecoveryApi.preview(run, signal),
        enabled: false,
        retry: false,
    });
    const recover = useMutation({
        mutationFn: (request: ProjectionRecoveryRequest) => projectionRecoveryApi.recover(run, request),
        retry: false,
        onSuccess: () => {
            void preview.refetch();
            onUpdated();
        },
    });
    const recheck = useMutation({
        mutationFn: (request: FinalizationRequest) => projectionRecoveryApi.recheck(run, request),
        retry: false,
        onSuccess: () => {
            void message.success(t('projectionRecovery.verified'));
            onUpdated();
        },
    });
    const data = preview.data;
    const differences = data?.differences;
    const busy = preview.isFetching || recover.isPending || recheck.isPending;
    const failure = recheck.error ?? recover.error ?? preview.error;
    const recoveryId = recover.data?.operationId ?? data?.recoveryOperationId;

    const load = () => {
        setOpened(true);
        setRecoveryRequest(undefined);
        recover.reset();
        void preview.refetch();
    };
    const confirm = () => {
        if (!data?.endOperationId || busy) return;
        const request = recoveryRequest ?? {
            operationId: crypto.randomUUID(),
            endOperationId: data.endOperationId,
            ledgerWatermark: data.ledgerWatermark.sha256,
            projectionVersion: data.projectionVersion,
        };
        modal.confirm({
            title: t('projectionRecovery.authorize'),
            content: t('projectionRecovery.confirm', {account: data.accountId}),
            okText: t('projectionRecovery.authorize'),
            cancelText: t('cancel'),
            onOk: () => {
                setRecoveryRequest(request);
                recover.mutate(request);
            },
        });
    };
    const finalize = () => {
        if (!data?.endOperationId || !recoveryId || busy) return;
        const request = finalizationRequest ?? {
            operationId: crypto.randomUUID(),
            endOperationId: data.endOperationId,
            recoveryOperationId: recoveryId,
        };
        setFinalizationRequest(request);
        recheck.mutate(request);
    };

    if (!eligible && !opened) return null;
    const hasDifference = differences && (!differences.cashConsistent || !differences.positionConsistent);
    return (
        <Space direction="vertical" size="small" role="group" aria-label={t('projectionRecovery.preview')}>
            {!recheck.isSuccess && (
                <Button onClick={load} disabled={busy || Boolean(finalizationRequest)}>
                    {t('projectionRecovery.preview')}
                </Button>
            )}
            {opened && failure && (
                <Alert type="error" showIcon message={formatApiError(failure as AppApiError)} />
            )}
            {opened && differences && !preview.isError && !recheck.isSuccess && (
                <Descriptions size="small" column={1}>
                    <Descriptions.Item label={t('projectionRecovery.account')}>{data.accountId}</Descriptions.Item>
                    <Descriptions.Item label={t('projectionRecovery.cash')}>
                        {differences.actualCash?.balance ?? '—'} → {differences.expectedCash.balance}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('projectionRecovery.holding')}>
                        {differences.actualPosition?.qty ?? '—'} → {differences.expectedPosition?.qty ?? '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('projectionRecovery.average')}>
                        {differences.actualPosition?.avgPrice ?? '—'} → {differences.expectedPosition?.avgPrice ?? '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('projectionRecovery.watermark')}>
                        {data.ledgerWatermark.postedThrough}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('projectionRecovery.difference')}>
                        {t(differences.cashConsistent ? 'projectionRecovery.cashConsistent' : 'projectionRecovery.cashMismatch')}
                        {' · '}
                        {t(differences.positionConsistent ? 'projectionRecovery.positionConsistent' : 'projectionRecovery.positionMismatch')}
                    </Descriptions.Item>
                </Descriptions>
            )}
            {opened && eligible && hasDifference && !preview.isError && !recover.isSuccess && (
                <Button onClick={confirm} loading={recover.isPending} disabled={busy}>
                    {t('projectionRecovery.authorize')}
                </Button>
            )}
            {opened && recoveryId && !recheck.isSuccess
                && ((differences && !hasDifference && !preview.isError) || finalizationRequest) && (
                <Space wrap>
                    <Typography.Text copyable>{recoveryId}</Typography.Text>
                    <Button onClick={finalize} loading={recheck.isPending} disabled={busy}>
                        {t('projectionRecovery.finalize')}
                    </Button>
                </Space>
            )}
            {recheck.isSuccess && <Alert type="success" showIcon message={t('projectionRecovery.verified')} />}
        </Space>
    );
}
