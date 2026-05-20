import {apiClient} from '@/api/client';
import type {
    PaperTradingOrderItem,
    PaperTradingPositionItem,
    PaperTradingRunCreateRequest,
    PaperTradingRunItem,
    PaperTradingTradeItem,
} from '@/types/paper-trading';

export interface PaperTradingListRequest {
    publishId?: string;
    status?: string;
}

export const paperTradingApi = {
    async list(request: PaperTradingListRequest): Promise<PaperTradingRunItem[]> {
        const {data} = await apiClient.get<PaperTradingRunItem[]>('/paper-trading/runs', {
            params: request,
        });
        return data;
    },
    async detail(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.get<PaperTradingRunItem>(`/paper-trading/runs/${paperRunId}`);
        return data;
    },
    async create(request: PaperTradingRunCreateRequest): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>('/paper-trading/runs', request);
        return data;
    },
    async start(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>(`/paper-trading/runs/${paperRunId}/start`);
        return data;
    },
    async stop(paperRunId: string): Promise<PaperTradingRunItem> {
        const {data} = await apiClient.post<PaperTradingRunItem>(`/paper-trading/runs/${paperRunId}/stop`);
        return data;
    },
    async orders(paperRunId: string): Promise<PaperTradingOrderItem[]> {
        const {data} = await apiClient.get<PaperTradingOrderItem[]>(`/paper-trading/runs/${paperRunId}/orders`);
        return data;
    },
    async trades(paperRunId: string): Promise<PaperTradingTradeItem[]> {
        const {data} = await apiClient.get<PaperTradingTradeItem[]>(`/paper-trading/runs/${paperRunId}/trades`);
        return data;
    },
    async positions(paperRunId: string): Promise<PaperTradingPositionItem[]> {
        const {data} = await apiClient.get<PaperTradingPositionItem[]>(`/paper-trading/runs/${paperRunId}/positions`);
        return data;
    },
};
