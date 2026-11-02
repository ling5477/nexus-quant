import {apiClient} from '@/api/client';
import {parseTradingWorkbenchResponse, validateAccountFactPage} from '../trading-response';
import type {
    TradingPreflightRequest,
    TradingPreflightReadiness,
    AccountView,
    OperationTriggerResponse,
    OrderCancelRequestBody,
    TradingOrderListRequest,
    TradingOrderListResponse,
    OrderSubmitRequest,
    OrderView,
    PositionView,
    TradeView,
    AccountFactPage,
} from '@/features/trading/types/trading-workbench';

const exactResponse = {transformResponse: [parseTradingWorkbenchResponse]};

export const tradingWorkbenchApi = {
    async listAccountTrades(request: {accountId: number; symbol?: string; page: number; size: number}): Promise<AccountFactPage<TradeView>> {
        const {data} = await apiClient.get<AccountFactPage<TradeView>>('/trading/trades', {params: request, ...exactResponse});
        return validateAccountFactPage(data, request, 'LOCAL_CANONICAL_TRADES');
    },
    async listAccountPositions(request: {accountId: number; symbol?: string; page: number; size: number}): Promise<AccountFactPage<PositionView>> {
        const {data} = await apiClient.get<AccountFactPage<PositionView>>('/trading/positions', {params: request, ...exactResponse});
        return validateAccountFactPage(data, request, 'LOCAL_CANONICAL_POSITION_PROJECTION');
    },
    /** 只读检查与提交订单分开，读取不隐式探活或调用任何交易命令。 */
    async readPreflight(request: TradingPreflightRequest): Promise<TradingPreflightReadiness> {
        const {data} = await apiClient.get<TradingPreflightReadiness>(
            '/trading/preflight/readiness',
            {params: request},
        );
        return data;
    },
    async listOrders(request: TradingOrderListRequest): Promise<TradingOrderListResponse> {
        const {data} = await apiClient.get<TradingOrderListResponse>('/trading/orders', {
            params: request,
            ...exactResponse,
        });
        return data;
    },
    async getOrder(orderId: string): Promise<OrderView> {
        const {data} = await apiClient.get<OrderView>(`/trading/orders/${orderId}`, exactResponse);
        return data;
    },
    /** 恢复只读原账户/原客户端身份；404交给调用方保留未知，不触发下单。 */
    async getOrderByClientIdentity(accountId: number, clientOrderId: string): Promise<OrderView> {
        const {data} = await apiClient.get<OrderView>('/trading/orders/by-client', {
            params: {accountId, clientOrderId},
            ...exactResponse,
        });
        return data;
    },
    async getLatestTrade(orderId: string): Promise<TradeView> {
        const {data} = await apiClient.get<TradeView>(
            `/trading/orders/${orderId}/trade`,
            exactResponse,
        );
        return data;
    },
    async getPosition(accountId: number, symbol: string): Promise<PositionView> {
        const {data} = await apiClient.get<PositionView>(
            `/trading/positions/${accountId}/${symbol}`,
            exactResponse,
        );
        return data;
    },
    async getAccount(accountId: number): Promise<AccountView> {
        const {data} = await apiClient.get<AccountView>(
            `/trading/accounts/${accountId}`,
            exactResponse,
        );
        return data;
    },
    async placeOrder(request: OrderSubmitRequest): Promise<OperationTriggerResponse> {
        const {data} = await apiClient.post<OperationTriggerResponse>('/trading/orders', request);
        return data;
    },
    async cancelOrder(request: OrderCancelRequestBody): Promise<OperationTriggerResponse> {
        const {data} = await apiClient.post<OperationTriggerResponse>(
            '/trading/orders/cancel',
            request,
        );
        return data;
    },
};
