import {apiClient} from '@/api/client';
import type {
    AccountView,
    OperationTriggerResponse,
    OrderCancelRequestBody,
    OrderSubmitRequest,
    OrderView,
    PositionView,
    ReconcileRunOnceRequest,
    RecoveryRunOnceRequest,
    TradeView,
} from '@/types/trade-validation';

export const tradeValidationApi = {
    async getOrder(orderId: string): Promise<OrderView> {
        const {data} = await apiClient.get<OrderView>(`/trading/orders/${orderId}`);
        return data;
    },
    async getLatestTrade(orderId: string): Promise<TradeView> {
        const {data} = await apiClient.get<TradeView>(`/trading/orders/${orderId}/trade`);
        return data;
    },
    async getPosition(accountId: number, symbol: string): Promise<PositionView> {
        const {data} = await apiClient.get<PositionView>(`/trading/positions/${accountId}/${symbol}`);
        return data;
    },
    async getAccount(accountId: number): Promise<AccountView> {
        const {data} = await apiClient.get<AccountView>(`/trading/accounts/${accountId}`);
        return data;
    },
    async placeOrder(request: OrderSubmitRequest): Promise<OperationTriggerResponse> {
        const {data} = await apiClient.post<OperationTriggerResponse>('/trading/orders', request);
        return data;
    },
    async cancelOrder(request: OrderCancelRequestBody): Promise<OperationTriggerResponse> {
        const {data} = await apiClient.post<OperationTriggerResponse>('/trading/orders/cancel', request);
        return data;
    },
    async runReconcile(request?: ReconcileRunOnceRequest): Promise<OperationTriggerResponse> {
        const {data} = await apiClient.post<OperationTriggerResponse>('/trading/reconciliation/run-once', request);
        return data;
    },
    async runRecovery(request?: RecoveryRunOnceRequest): Promise<OperationTriggerResponse> {
        const {data} = await apiClient.post<OperationTriggerResponse>('/trading/recovery/run-once', request);
        return data;
    },
};
