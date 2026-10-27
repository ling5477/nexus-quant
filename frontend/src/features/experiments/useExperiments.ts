import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {experimentQueryKeys} from '@/api/query-keys';
import {experimentsApi} from './experimentsApi';
import {useAuthStore} from '@/store/auth-store';

export function useExperiments(offset: number, id: string | null) {
    const owner = useAuthStore(s => s.currentUser?.userId);
    const cache = useQueryClient();
    const refresh = () => cache.invalidateQueries({queryKey: experimentQueryKeys.all});
    const list = useQuery({queryKey: experimentQueryKeys.list(owner, offset), queryFn: () => experimentsApi.list(offset), enabled: !!owner, refetchInterval: 3000});
    const detail = useQuery({queryKey: experimentQueryKeys.detail(owner, id), queryFn: () => experimentsApi.get(id!), enabled: !!owner && !!id, refetchInterval: q => ['QUEUED', 'RUNNING'].includes(q.state.data?.status ?? '') ? 2000 : false});
    const cancel = useMutation({mutationFn: () => experimentsApi.cancel(id!), onSuccess: refresh});
    const retry = useMutation({mutationFn: async ({trial}: {trial: string}) => {
        const key = `nq:experiment-retry:${owner}:${id}:${trial}`;
        const attempt = localStorage.getItem(key) ?? crypto.randomUUID();
        if (!/^[a-f0-9-]{36}$/i.test(attempt)) throw new Error('Invalid stored attempt identity');
        localStorage.setItem(key, attempt);
        const result = await experimentsApi.retry(id!, trial, attempt);
        if (useAuthStore.getState().currentUser?.userId === owner) localStorage.removeItem(key);
        return result;
    }, onSuccess: refresh});
    return {list, detail, cancel, retry, refresh};
}
