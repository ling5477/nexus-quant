import {useQuery} from '@tanstack/react-query';
import {marketdataApi} from '@/api/marketdata';
import {marketdataQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import type {DatasetPageRequest} from '@/types/marketdata';

/** 窗口和当前主体共同参与读取身份，关闭选择窗后取消请求并释放目录缓存。 */
export function useDatasetPageQuery(request: DatasetPageRequest, active: boolean, revision = 0) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        // 仅enabled=false不会卸下旧查询观察者；切换身份才能在关闭瞬间取消已消费signal的请求。
        queryKey: marketdataQueryKeys.datasetPage(owner, request, active ? revision : -1),
        queryFn: ({signal}) => marketdataApi.pageDatasets(request, signal),
        enabled: active && owner !== null,
        gcTime: 0,
    });
}

/** 选中对象直接走正式详情，不依赖它是否仍在目录首页或当前过滤窗口。 */
export function useDatasetDetailQuery(datasetId: string | null | undefined, active = true) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    const id = datasetId?.trim() || null;
    return useQuery({
        queryKey: marketdataQueryKeys.datasetDetail(owner, active ? id : null),
        queryFn: ({signal}) => marketdataApi.getDataset(id!, signal),
        enabled: active && Boolean(id) && owner !== null,
        gcTime: 0,
    });
}
