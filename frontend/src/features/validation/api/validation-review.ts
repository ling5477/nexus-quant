import {apiClient} from '@/api/client';
import type {
    ValidationReviewCase,
    ValidationReviewEvent,
    ValidationReviewLifecycleCommand,
    ValidationReviewListRequest,
    ValidationReviewAction,
    ValidationReviewState,
} from '@/features/validation/types/validation-review';

const BASE_PATH = '/validation-review-cases';

const reviewActionStates: Record<ValidationReviewAction, ValidationReviewState> = {
    acknowledge: 'ACKNOWLEDGED',
    escalate: 'ESCALATED',
    resolve: 'RESOLVED',
    close: 'CLOSED',
};

/** 原对象、动作和版本共同限定恢复范围；不把人工备注保存为另一份客户端业务事实。 */
export function reviewOperationTarget(
    command: Omit<ValidationReviewLifecycleCommand, 'idempotencyKey'>,
): string {
    const target = `${command.caseId}:${command.action}:${command.payload.expectedVersion}`;
    parseReviewOperationTarget(target);
    return target;
}

/** 损坏或不受支持的范围不得进入查询，尤其不能把已舍入的大版本号用于确认。 */
export function parseReviewOperationTarget(target: string) {
    const [caseId, action, version, ...extra] = target.split(':');
    const expectedVersion = Number(version);
    if (
        extra.length ||
        !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(caseId ?? '') ||
        !['acknowledge', 'escalate', 'resolve', 'close'].includes(action) ||
        !/^(0|[1-9]\d*)$/.test(version ?? '') ||
        !Number.isSafeInteger(expectedVersion)
    )
        throw new Error('Invalid review recovery identity');
    return {
        caseId,
        action: action as ValidationReviewAction,
        expectedVersion,
        targetState: reviewActionStates[action as ValidationReviewAction],
    };
}

/** 原事件是确认依据；当前 case 后续状态及另一操作者的事件都不能替代它。 */
export function confirmOriginalReviewEvent(
    event: ValidationReviewEvent,
    target: string,
    owner: number,
) {
    const scope = parseReviewOperationTarget(target);
    if (
        typeof event?.id !== 'string' ||
        !/^[a-f0-9-]{36}$/i.test(event.id) ||
        event.caseId !== scope.caseId ||
        event.actorId !== owner ||
        event.eventType !== scope.targetState ||
        event.toState !== scope.targetState ||
        !Number.isSafeInteger(event.caseVersion) ||
        event.caseVersion !== scope.expectedVersion + 1 ||
        !Number.isFinite(Date.parse(event.createdAt))
    )
        throw new Error('Unconfirmed original review result');
    return event;
}

/**
 * 人工复核 API 客户端。
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
        const {data} = await apiClient.get<ValidationReviewEvent[]>(
            `${BASE_PATH}/${caseId}/events`,
        );
        return data;
    },

    /** 只读原请求事件，404 保留未知；幂等键只发往请求头，不进入 URL 或响应。 */
    async originalResult(caseId: string, idempotencyKey: string): Promise<ValidationReviewEvent> {
        const {data} = await apiClient.get<ValidationReviewEvent>(
            `${BASE_PATH}/${caseId}/action-result`,
            {headers: {'Idempotency-Key': idempotencyKey}},
        );
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
