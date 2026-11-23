import {useQuery} from '@tanstack/react-query';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {schedulerApi} from '@/features/scheduler/api/scheduler';
import {readRuntimeHealth} from '@/features/system/api/runtime-health';
import {selectSessionIdentity, useAuthStore} from '@/store/auth-store';
import {continuousJobs} from '../continuous-operations-view';

export function useContinuousSimOperations(run: string, enabled: boolean) {
    const owner = useAuthStore(state => state.currentUser?.userId);
    const session = useAuthStore(selectSessionIdentity);
    const options = {enabled: enabled && !!owner && !!session, retry: false, gcTime: 0, refetchInterval: 30000};
    const jobs = useQuery({...options, queryKey: paperTradingQueryKeys.operations(owner, session, run, 'jobs'),
        queryFn: async ({signal}) => continuousJobs(await schedulerApi.list({signal}))});
    const health = useQuery({...options, queryKey: paperTradingQueryKeys.operations(owner, session, run, 'health'),
        queryFn: ({signal}) => readRuntimeHealth(signal)});
    return {jobs, health};
}
