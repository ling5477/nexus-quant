import {apiClient} from '@/api/client';
import type {
    ValidationReviewCase,
    ValidationReviewEvent,
    ValidationReviewLifecycleCommand,
    ValidationReviewListRequest,
} from '@/types/validation-review';

const BASE_PATH = '/validation-review-cases';

/**
 * GateV-2 validation review API client。
 *
 * Why：所有 URL、query 和 Idempotency-Key 都集中在此处，页面组件不能构造额外 endpoint，
 * 也不能提交 actor、owner、tenant、requestId、traceId 或交易相关字段。
 */
export const validationReviewApi = {
    async list(request: ValidationReviewListRequest): Promise<ValidationReviewCase[]> {
        const {data} = await apiClient.get<ValidationReviewCase[]>(BASE_PATH, {params: request});
        return data;
    },

    async detail(caseId: string): Promise<ValidationReviewCase> {
        const {data} = await apiClient.get<ValidationReviewCase>(`${BASE_PATH}/${caseId}`);
        return data;
    },

    async events(caseId: string): Promise<ValidationReviewEvent[]> {
        const {data} = await apiClient.get<ValidationReviewEvent[]>(`${BASE_PATH}/${caseId}/events`);
        return data;
    },

    async transition(command: ValidationReviewLifecycleCommand): Promise<ValidationReviewCase> {
        const {data} = await apiClient.post<ValidationReviewCase>(
            `${BASE_PATH}/${command.caseId}/${command.action}`,
            command.payload,
            {headers: {'Idempotency-Key': command.idempotencyKey}},
        );
        return data;
    },
};
