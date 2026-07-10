import {useQuery} from '@tanstack/react-query';

import {validationOperationsRuntimeEvidenceApi} from '@/api/validation-operations-runtime-evidence';
import {validationOperationsRuntimeEvidenceQueryKeys} from '@/api/query-keys';

/**
 * Validation Operations Runtime Evidence 的只读 aggregate query。
 *
 * Why: 聚合接口已经在后端固定了五来源顺序与 fail-closed 规则；前端只消费该 GET，不自行重算总状态或轮询来源。
 */
export function useValidationOperationsRuntimeEvidenceOverview() {
    return useQuery({
        queryKey: validationOperationsRuntimeEvidenceQueryKeys.overview(),
        queryFn: () => validationOperationsRuntimeEvidenceApi.getValidationOperationsRuntimeEvidenceOverview(),
        retry: false,
    });
}
