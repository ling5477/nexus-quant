import {useQuery} from '@tanstack/react-query';

import {adapterReadinessApi} from '@/api/adapter-readiness';
import {adapterReadinessQueryKeys} from '@/api/query-keys';

/**
 * 读取 adapter readiness 只读快照。
 *
 * Why:
 * readiness 是安全边界状态：请求失败时必须 fail-closed 显示「不可用」，绝不回退成 ready。
 * 这里关闭重试（retry:false），让错误态确定、即时呈现，由页面渲染 "readiness API unavailable"。
 */
export function useAdapterReadinessQuery() {
    return useQuery({
        queryKey: adapterReadinessQueryKeys.status(),
        queryFn: adapterReadinessApi.getReadiness,
        retry: false,
    });
}
