import {useMutation, useQuery} from '@tanstack/react-query';

import {tradeValidationApi} from '@/api/trade-validation';
import {tradeValidationQueryKeys} from '@/api/query-keys';
import type {AppApiError} from '@/types/api';
import type {
    OperationTriggerResponse,
    OrderCancelRequestBody,
    OrderSubmitRequest,
    ReconcileRunOnceRequest,
    RecoveryRunOnceRequest,
    TradeValidationLookupRequest,
    TradeValidationLookupResult,
} from '@/types/trade-validation';

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

export function useTradeValidationLookupQuery(request: TradeValidationLookupRequest | null, searchVersion: number) {
    return useQuery<TradeValidationLookupResult>({
        queryKey: tradeValidationQueryKeys.lookup(request ?? {orderId: ''}, searchVersion),
        queryFn: async () => {
            const lookupRequest = request as TradeValidationLookupRequest;
            const order = await tradeValidationApi.getOrder(lookupRequest.orderId);
            const [latestTrade, account, position] = await Promise.all([
                swallowNotFound(tradeValidationApi.getLatestTrade(lookupRequest.orderId)),
                lookupRequest.accountId ? swallowNotFound(tradeValidationApi.getAccount(lookupRequest.accountId)) : Promise.resolve(null),
                lookupRequest.accountId && lookupRequest.symbol
                    ? swallowNotFound(tradeValidationApi.getPosition(lookupRequest.accountId, lookupRequest.symbol))
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

function createOperationMutation<TRequest>(
    mutationFn: (request: TRequest) => Promise<OperationTriggerResponse>,
) {
    return useMutation({
        mutationFn,
    });
}

export function usePlaceOrderMutation() {
    return createOperationMutation<OrderSubmitRequest>((request) => tradeValidationApi.placeOrder(request));
}

export function useCancelOrderMutation() {
    return createOperationMutation<OrderCancelRequestBody>((request) => tradeValidationApi.cancelOrder(request));
}

export function useReconcileMutation() {
    return createOperationMutation<ReconcileRunOnceRequest | undefined>((request) => tradeValidationApi.runReconcile(request));
}

export function useRecoveryMutation() {
    return createOperationMutation<RecoveryRunOnceRequest | undefined>((request) => tradeValidationApi.runRecovery(request));
}
