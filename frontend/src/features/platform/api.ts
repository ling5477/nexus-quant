import {apiClient} from '@/api/client';

export interface TenantItem {
    tenantId: string | null;
    userId: number;
    username: string;
    emailVerified: boolean;
    registeredAt: string;
    enabled: boolean;
    simAccountStatus: string;
    simStatus: string;
}
export interface TenantSummary extends TenantItem {
    suspended: boolean | null;
    requiresManualResume: boolean | null;
    executionEpoch: number | null;
}
export interface PlatformOverview {
    registeredUsers: number;
    enabledUsers: number;
    disabledUsers: number;
    runningSimCount: number;
    capacity: number;
    asOf: string;
    source: string;
}
export interface TenantPage {
    items: TenantItem[];
    total: number;
    page: number;
    size: number;
    overview: PlatformOverview;
}
export interface SimHistoryPoint {id: string; at: string; equity: string | null; cash: string | null; realizedPnl: string | null; unrealizedPnl: string | null; drawdown: string | null; valuationSource: string | null; valuationMethod: string | null; valuationStatus: string | null;}
export interface PlatformPerformance {
    status: string;
    reason: string | null;
    source: string;
    asOf: string | null;
    coverageStart: string | null;
    coverageEnd: string | null;
    initialCapital: string | null;
    currentCash: string | null;
    equity: string | null;
    realizedPnl: string | null;
    unrealizedPnl: string | null;
    netPnl: string | null;
    returnRate: string | null;
    drawdown: string | null;
    orderCount: number | null;
    tradeCount: number | null;
    simStatus: string;
    history: SimHistoryPoint[];
}
export interface UserOperationInput {operationId: string; reason: string;}
export interface UserOperation {
    operationId: string;
    targetUserId: number;
    targetTenantId: string | null;
    action: string;
    beforeEnabled: boolean;
    afterEnabled: boolean;
    beforeAuthVersion: number;
    afterAuthVersion: number;
    beforeExecutionEpoch: number | null;
    afterExecutionEpoch: number | null;
    createdAt: string;
}
export interface TenantFilters {page: number; size: number; search: string; enabled?: boolean; sort: string;}
export const platformApi = {
    async tenants(filters: TenantFilters, signal?: AbortSignal): Promise<TenantPage> {
        return (await apiClient.get<TenantPage>('/platform/tenants', {params: filters, signal})).data;
    },
    async summary(tenantId: string, signal?: AbortSignal): Promise<TenantSummary> {
        return (await apiClient.get<TenantSummary>(`/platform/tenants/${encodeURIComponent(tenantId)}/summary`, {signal})).data;
    },
    async performance(tenantId: string, signal?: AbortSignal): Promise<PlatformPerformance> {
        return (await apiClient.get<PlatformPerformance>(`/platform/tenants/${encodeURIComponent(tenantId)}/sim-performance`, {signal})).data;
    },
    async setEnabled(userId: number, enabled: boolean, input: UserOperationInput): Promise<UserOperation> {
        return (await apiClient.post<UserOperation>(`/platform/users/${userId}/${enabled ? 'enable' : 'disable'}`, input)).data;
    },
};
