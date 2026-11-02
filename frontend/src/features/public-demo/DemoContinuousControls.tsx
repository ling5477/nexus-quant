import {Alert, Space} from 'antd';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useAuthStore} from '@/store/auth-store';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import {simAnalysisApi} from '@/features/paper-trading/api/analysis';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {continuousStateMatches} from '@/features/paper-trading/continuous-state';
import {canOperateContinuous, continuousOperations} from '@/features/paper-trading/continuous-operations';
import {ContinuousSimStatusPanel} from '@/features/paper-trading/components/ContinuousSimStatusPanel';
import {ContinuousOperationRecovery, usePendingContinuousOperation} from '@/features/paper-trading/components/ContinuousOperationRecovery';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {demoRegistrationKeys} from './api';

/** 暂停、恢复沿用正式操作回执；UNKNOWN 未收敛前不能提交另一条命令。 */
export function DemoContinuousControls({paperRunId}: {paperRunId: string}) {
    const userId = useAuthStore((state) => state.currentUser?.userId ?? 0);
    const client = useQueryClient();
    const pending = usePendingContinuousOperation();
    const continuous = useQuery({queryKey: paperTradingQueryKeys.continuous(paperRunId),
        queryFn: ({signal}) => paperTradingApi.continuousSimStatus(paperRunId, signal), retry: false, refetchInterval: 30000});
    const analysis = useQuery({queryKey: paperTradingQueryKeys.analysis(userId, paperRunId),
        queryFn: ({signal}) => simAnalysisApi.read(paperRunId, signal), retry: false});
    const capability = useQuery({queryKey: paperTradingQueryKeys.continuousCapabilities,
        queryFn: paperTradingApi.continuousSimCapabilities, retry: false, staleTime: 30000});
    const available = capability.isError || !capability.data || !analysis.isSuccess || analysis.data.run.paperRunId !== paperRunId ? null
        : capability.data.commandsAvailable && canOperateContinuous() && import.meta.env.VITE_CONTINUOUS_SIM_ENABLED === 'true';
    const refresh = async () => {await Promise.all([
        client.invalidateQueries({queryKey: paperTradingQueryKeys.all}),
        client.invalidateQueries({queryKey: demoRegistrationKeys.result(userId)}),
    ]);};
    const command = useMutation({mutationFn: (action: 'stop' | 'resume') => continuousOperations.submit({
        type: action === 'stop' ? 'PAUSE' : 'RESUME', paperRunId}), retry: false, onSuccess: refresh});
    return <Space direction="vertical" size={12} style={{width: '100%'}}>
        <ContinuousOperationRecovery canSubmit={available} onResult={refresh} />
        {(command.isError || analysis.isError) && <Alert type="error" showIcon message={formatApiError((command.error ?? analysis.error) as AppApiError)} />}
        <ContinuousSimStatusPanel status={continuous.data ?? null} loading={continuous.isFetching}
            error={continuous.error as AppApiError | null}
            identityMismatch={!continuousStateMatches(continuous.data, paperRunId)}
            commandsAvailable={available} parentStatus={analysis.isSuccess && analysis.data.run.paperRunId === paperRunId ? analysis.data.run.status : 'UNKNOWN'}
            busy={command.isPending || pending.kind !== 'none'} onRefresh={() => void refresh()}
            onAction={(action) => command.mutate(action)} />
    </Space>;
}
