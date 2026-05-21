import {apiClient} from '@/api/client';
import type {
    EmergencyStopEventItem,
    EmergencyStopRequest,
    EquityCurveSnapshotItem,
    PaperRiskCheckResultItem,
    PaperTradingOrderItem,
    PaperTradingPositionItem,
    PaperTradingRunCreateRequest,
    PaperTradingRunItem,
    PaperTradingTradeItem,
    PositionCurveSnapshotItem,
    TradeReplayRecordItem,
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
    async riskResults(paperRunId: string): Promise<PaperRiskCheckResultItem[]> {
        const {data} = await apiClient.get<PaperRiskCheckResultItem[]>(`/paper-trading/runs/${paperRunId}/risk-results`);
        return data;
    },
    async runRiskOnce(paperRunId: string): Promise<PaperRiskCheckResultItem> {
        const {data} = await apiClient.post<PaperRiskCheckResultItem>(`/paper-trading/runs/${paperRunId}/risk-results/run-once`);
        return data;
    },
    async equityCurve(paperRunId: string): Promise<EquityCurveSnapshotItem[]> {
        const {data} = await apiClient.get<EquityCurveSnapshotItem[]>(`/paper-trading/runs/${paperRunId}/equity-curve`);
        return data;
    },
    async positionCurve(paperRunId: string): Promise<PositionCurveSnapshotItem[]> {
        const {data} = await apiClient.get<PositionCurveSnapshotItem[]>(`/paper-trading/runs/${paperRunId}/position-curve`);
        return data;
    },
    async replay(paperRunId: string): Promise<TradeReplayRecordItem[]> {
        const {data} = await apiClient.get<TradeReplayRecordItem[]>(`/paper-trading/runs/${paperRunId}/replay`);
        return data;
    },
    async emergencyStop(paperRunId: string, request: EmergencyStopRequest): Promise<EmergencyStopEventItem> {
        const {data} = await apiClient.post<EmergencyStopEventItem>(`/paper-trading/runs/${paperRunId}/emergency-stop`, request);
        return data;
    },
    async emergencyStops(paperRunId: string): Promise<EmergencyStopEventItem[]> {
        const {data} = await apiClient.get<EmergencyStopEventItem[]>(`/paper-trading/runs/${paperRunId}/emergency-stops`);
        return data;
    },
};
