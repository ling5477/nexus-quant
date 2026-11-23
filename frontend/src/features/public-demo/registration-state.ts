import type {DemoRegistrationResult} from './api';

const knownStatuses = new Set(['PENDING_EMAIL_VERIFICATION', 'INITIALIZING', 'INITIALIZED', 'SIM_NOT_BOUND', 'SIM_RUNNING', 'SIM_STOPPED', 'INITIALIZATION_FAILED']);
const readyStatuses = new Set(['INITIALIZED', 'SIM_NOT_BOUND', 'SIM_RUNNING', 'SIM_STOPPED']);

/** 状态和持久身份必须一致；不完整响应不得显示为已初始化或运行成功。 */
export function registrationStatus(result: DemoRegistrationResult | undefined): string {
    if (!result || !knownStatuses.has(result.status)) return 'UNKNOWN';
    if (!readyStatuses.has(result.status)) return result.status;
    if (!result.tenantId || !result.exchangeAccountId || !result.accountId || !result.reservedPaperRunId
        || result.currency !== 'USDT' || !/^10000(?:\.0{1,8})?$/.test(result.initialCapital ?? '')) return 'UNKNOWN';
    if (result.status === 'SIM_RUNNING' || result.status === 'SIM_STOPPED')
        return result.boundPaperRunId === result.reservedPaperRunId ? result.status : 'UNKNOWN';
    return result.boundPaperRunId === null ? result.status : 'UNKNOWN';
}
