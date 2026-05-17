import {useMutation, useQuery} from '@tanstack/react-query';

import {tradingWorkbenchApi} from '@/api/trading-workbench';
import {tradingWorkbenchQueryKeys} from '@/api/query-keys';
import type {AppApiError} from '@/types/api';
import type {
    OperationTriggerResponse,
    OrderCancelRequestBody,
    OrderSubmitRequest,
    ReconcileRunOnceRequest,
    RecoveryRunOnceRequest,
    TradingOrderListRequest,
    TradingOrderListResponse,
    TradingWorkbenchLookupRequest,
    TradingWorkbenchLookupResult,
} from '@/types/trading-workbench';

async function swallowNotFound<T>(promise: Promise<T>): Promise<T | null> {
    try {
        return await promise;
    } catch (error) {
        const appError = error as AppApiError;

        if (appError.status === 404) {
            return null;
        }

        throw error;
    }
}

export function useTradingWorkbenchLookupQuery(request: TradingWorkbenchLookupRequest | null, searchVersion: number) {
    return useQuery<TradingWorkbenchLookupResult>({
        queryKey: tradingWorkbenchQueryKeys.lookup(request ?? {orderId: ''}, searchVersion),
        queryFn: async () => {
            const lookupRequest = request as TradingWorkbenchLookupRequest;
            const order = await tradingWorkbenchApi.getOrder(lookupRequest.orderId);
            const [latestTrade, account, position] = await Promise.all([
                swallowNotFound(tradingWorkbenchApi.getLatestTrade(lookupRequest.orderId)),
                lookupRequest.accountId ? swallowNotFound(tradingWorkbenchApi.getAccount(lookupRequest.accountId)) : Promise.resolve(null),
                lookupRequest.accountId && lookupRequest.symbol
                    ? swallowNotFound(tradingWorkbenchApi.getPosition(lookupRequest.accountId, lookupRequest.symbol))
                    : Promise.resolve(null),
            ]);

            return {
                order,
                latestTrade,
                account,
                position,
            };
        },
        enabled: Boolean(request?.orderId) && searchVersion > 0,
    });
}

export function useTradingOrderListQuery(request: TradingOrderListRequest | null, searchVersion: number) {
    return useQuery<TradingOrderListResponse>({
        queryKey: tradingWorkbenchQueryKeys.orders(request ?? {}, searchVersion),
        queryFn: () => tradingWorkbenchApi.listOrders(request as TradingOrderListRequest),
        enabled: Boolean(request?.accountId) && searchVersion > 0,
    });
}

function createOperationMutation<TRequest>(
    mutationFn: (request: TRequest) => Promise<OperationTriggerResponse>,
) {
    return useMutation({
        mutationFn,
    });
}

export function usePlaceOrderMutation() {
    return createOperationMutation<OrderSubmitRequest>((request) => tradingWorkbenchApi.placeOrder(request));
}

export function useCancelOrderMutation() {
    return createOperationMutation<OrderCancelRequestBody>((request) => tradingWorkbenchApi.cancelOrder(request));
}

export function useReconcileMutation() {
    return createOperationMutation<ReconcileRunOnceRequest | undefined>((request) => tradingWorkbenchApi.runReconcile(request));
}

export function useRecoveryMutation() {
    return createOperationMutation<RecoveryRunOnceRequest | undefined>((request) => tradingWorkbenchApi.runRecovery(request));
}
