import {useEffect, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useSearchParams} from 'react-router-dom';
import {marketdataQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {CreateMarketdataDatasetRequest, DatasetOperation} from '@/types/marketdata';
import {
    datasetOperationChanged,
    datasetOperationReads,
    datasetOperations,
    readDatasetOperation,
    validDatasetOperationId,
} from './dataset-operations';

/** 表单、行操作及恢复区共享同一原请求；刷新和返回仅查询，不触发POST。 */
export function useDatasetOperationState(active: boolean) {
    const user = useAuthStore((state) => state.currentUser);
    const owner = user?.userId;
    const token = useAuthStore((state) => state.accessToken);
    const canWrite = Boolean(
        user?.authenticated &&
            !user.mustChangePassword &&
            user.roles.some((role) =>
                ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
            ),
    );
    const [params, setParams] = useSearchParams();
    const client = useQueryClient();
    const [storage, setStorage] = useState(() => ({owner, value: readDatasetOperation(owner)}));
    useEffect(() => {
        const refresh = () => setStorage({owner, value: readDatasetOperation(owner)});
        refresh();
        window.addEventListener(datasetOperationChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(datasetOperationChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    const saved = storage.owner === owner ? storage.value : readDatasetOperation(owner);
    const pending = saved.kind === 'saved' ? saved.pending : null;
    const rawId = params.get('datasetOperationId');
    const invalid = rawId !== null && !validDatasetOperationId(rawId);
    const selected = invalid ? null : (rawId ?? pending?.request.operationId ?? null);
    function select(id: string | null) {
        const current = useAuthStore.getState();
        if (current.currentUser?.userId !== owner || current.accessToken !== token) return;
        setParams((previous) => {
            const next = new URLSearchParams(previous);
            next.set('section', 'preparation');
            next.set('preparationTab', 'datasets');
            if (id) next.set('datasetOperationId', id);
            else next.delete('datasetOperationId');
            return next;
        });
    }
    const query = useQuery({
        queryKey: marketdataQueryKeys.datasetOperation(owner, selected),
        queryFn: ({signal}) => datasetOperationReads.one(selected!, signal),
        enabled: active && Boolean(owner && selected),
        retry: false,
        staleTime: 0,
        gcTime: 0,
    });
    const result = query.isSuccess && query.data.operationId === selected ? query.data : null;
    const currentSession = () => {
        const current = useAuthStore.getState();
        return current.currentUser?.userId === owner && current.accessToken === token;
    };
    async function received(value: DatasetOperation) {
        if (!currentSession()) return;
        const key = marketdataQueryKeys.datasetOperation(owner, value.operationId);
        // 取消首次登记期间的旧404查询，防止其晚到后覆盖POST返回的正式终态。
        await client.cancelQueries({queryKey: key, exact: true});
        if (!currentSession()) return;
        client.setQueryData(key, value);
        select(value.operationId);
    }
    const create = useMutation({
        mutationFn: (input: CreateMarketdataDatasetRequest) =>
            datasetOperations.create(input, select),
        onSuccess: received,
    });
    const refresh = useMutation({
        mutationFn: (id: string) => datasetOperations.refresh(id, select),
        onSuccess: received,
    });
    const resume = useMutation({mutationFn: datasetOperations.resume, onSuccess: received});
    const acknowledge = useMutation({mutationFn: datasetOperations.acknowledge});
    useEffect(() => {
        // 换登录会话后不沿用旧恢复证明；角色降为只读仍允许重新读取自己原请求。
        create.reset();
        refresh.reset();
        resume.reset();
        acknowledge.reset();
        if (active && owner && selected) void query.refetch();
    }, [owner, token]);
    useEffect(() => {
        if (result?.state === 'SUCCEEDED') {
            // 查询找回与POST成功具有相同刷新效果；不能让列表继续展示旧质量。
            void client.invalidateQueries({queryKey: marketdataQueryKeys.datasets()});
            void client.invalidateQueries({queryKey: marketdataQueryKeys.qualityOverviewAll()});
        }
    }, [client, owner, result?.operationId, result?.state, result?.completedAt]);
    const originalSelected = Boolean(pending && pending.request.operationId === selected);
    const terminal = Boolean(result && result.state !== 'PENDING');
    useEffect(() => {
        // 原请求已有正式终态后清除旧网络错误；确认阅读失败仍须保留反馈。
        if (originalSelected && terminal) {
            if (create.error) create.reset();
            if (refresh.error) refresh.reset();
            if (resume.error) resume.reset();
        }
    }, [
        originalSelected,
        terminal,
        create.error,
        create.reset,
        refresh.error,
        refresh.reset,
        resume.error,
        resume.reset,
    ]);
    const busy =
        create.isPending ||
        refresh.isPending ||
        resume.isPending ||
        acknowledge.isPending ||
        pending?.phase === 'SUBMITTING';
    const blocked = busy || !canWrite || saved.kind !== 'none';
    const notVisible = (query.error as AppApiError | null)?.status === 404;
    const continuation =
        originalSelected && !query.isFetching && (notVisible || result?.state === 'PENDING');
    return {
        canWrite,
        saved,
        pending,
        invalid,
        selected,
        select,
        query,
        result,
        create,
        refresh,
        resume,
        acknowledge,
        busy,
        blocked,
        originalSelected,
        terminal,
        notVisible,
        continuation,
    };
}
export type DatasetOperationState = ReturnType<typeof useDatasetOperationState>;
