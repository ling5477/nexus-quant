import {Alert, Button, Card, Descriptions, Popconfirm, Space, Tag, Typography} from 'antd';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {experimentQueryKeys, strategyQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {validationApi} from './validationApi';

/** 查询可自动恢复准确状态；激活仅来自人工确认，不在挂载、刷新或错误恢复中提交。 */
export function SelectedVersionActivation({validationId, succeeded, writable}: {
    validationId: string; succeeded: boolean; writable: boolean;
}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore(s => s.currentUser?.userId);
    const cache = useQueryClient();
    const key = experimentQueryKeys.selectedVersion(owner, validationId);
    const selected = useQuery({queryKey: key, enabled: succeeded && owner != null, staleTime: 0,
        queryFn: () => validationApi.selectedVersion(validationId)});
    const activation = useMutation({retry: false, mutationFn: async () => {
        const result = await validationApi.activateSelectedVersion(validationId);
        if (useAuthStore.getState().currentUser?.userId !== owner || result.validationId !== validationId)
            throw new Error(t('experiments.scopeChanged'));
        return result;
    }, onSuccess: value => {
        cache.setQueryData(key, value);
        void cache.invalidateQueries({queryKey: strategyQueryKeys.versions(value.strategyCode)});
        void cache.invalidateQueries({queryKey: strategyQueryKeys.scopedVersionDetail(owner ?? null, value.strategyCode, value.strategyVersionId)});
        void cache.invalidateQueries({queryKey: experimentQueryKeys.comparisonsForValidation(owner, validationId)});
    }, onError: () => {
        // 响应丢失只发GET核对结果；即使服务端已提交也不能重复触发写动作。
        if (useAuthStore.getState().currentUser?.userId === owner)
            void cache.invalidateQueries({queryKey: key});
    }});
    const current = selected.data?.validationId === validationId ? selected.data : undefined;
    const active = current?.status === 'ACTIVE';
    const error = selected.error ?? (!active ? activation.error : null);
    const disabled = !writable || !succeeded || !current?.eligible || active
        || selected.isFetching || !!selected.error || activation.isPending;
    return <Card size="small" title={t('selectedVersion.title')} data-testid="selected-version-activation">
        <Space direction="vertical" size={12} style={{width: '100%'}}>
            <Alert showIcon type="info" message={t('selectedVersion.boundary')}/>
            {!succeeded && <Alert type="warning" message={t('selectedVersion.validationRequired')}/>}
            {!writable && <Typography.Text type="secondary">{t('selectedVersion.writeRequired')}</Typography.Text>}
            {error && <Alert type="error" message={formatApiError(error as AppApiError)} description={t('selectedVersion.recover')}/>}
            {current && <Descriptions column={1} size="small" items={[
                {key: 'id', label: t('walkForward.selected'), children: <Typography.Text style={{overflowWrap: 'anywhere'}}>{current.strategyVersionId}</Typography.Text>},
                {key: 'status', label: t('experiments.status'), children: <Tag>{current.status}</Tag>},
                {key: 'parameters', label: t('experiments.parameters'), children: <Typography.Text style={{overflowWrap: 'anywhere'}}>{JSON.stringify(current.parameters)}</Typography.Text>},
                {key: 'checksum', label: 'Checksum', children: <Typography.Text style={{overflowWrap: 'anywhere'}}>{current.checksum}</Typography.Text>},
            ]}/>}
            <Space wrap>
                <Popconfirm disabled={disabled} title={t('selectedVersion.confirm')}
                    description={t('selectedVersion.boundary')} okText={t('selectedVersion.activate')}
                    cancelText={t('selectedVersion.cancel')} onConfirm={() => activation.mutateAsync()}>
                    <Button type="primary" disabled={disabled} loading={activation.isPending}>
                        {active ? t('selectedVersion.active') : t('selectedVersion.activate')}
                    </Button>
                </Popconfirm>
                <Button disabled={!succeeded || selected.isFetching || activation.isPending}
                    onClick={() => void selected.refetch()}>{t('selectedVersion.refresh')}</Button>
                {active && <Link to={`/backtests?backtestConfigId=${encodeURIComponent(current.backtestConfigId)}&backtestRunId=${encodeURIComponent(current.backtestRunId)}`}>
                    {t('selectedVersion.publishLink')}
                </Link>}
            </Space>
            {active && <Alert type="success" message={t('selectedVersion.confirmed')}/>}
        </Space>
    </Card>;
}
