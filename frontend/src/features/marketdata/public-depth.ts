import {apiClient} from '@/api/client';

export interface DepthLevel {
    price: string;
    quantity: string;
    cumulativeQuantity: string;
    orderCount: number;
}
/** 来源时点与检查时点分别保留；STALE可以携带旧档位，不能把本次检查解释为新盘口。 */
export interface PublicDepthSnapshot {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    status: 'AVAILABLE' | 'STALE' | 'DISABLED' | 'SOURCE_UNAVAILABLE' | 'BUSY';
    sourceTime: string | null;
    receivedAt: string | null;
    checkedAt: string;
    source: string;
    maxLevels: number;
    errorCode: string | null;
    bids: DepthLevel[];
    asks: DepthLevel[];
}
/** 只读正式OKX现货盘口；不根据参考图添加未支持市场，也不把来源失败转成示例深度。 */
export const publicDepthApi = {
    async read(symbol: string, signal?: AbortSignal): Promise<PublicDepthSnapshot> {
        return (
            await apiClient.get<PublicDepthSnapshot>('/marketdata/depth', {
                params: {exchangeCode: 'OKX', marketType: 'SPOT', symbol},
                signal,
            })
        ).data;
    },
};
