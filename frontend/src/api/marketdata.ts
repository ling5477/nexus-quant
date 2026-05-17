import {apiClient} from '@/api/client';
import type {
    CreateMarketdataIngestionJobRequest,
    MarketdataBar,
    MarketdataBarsQuery,
    MarketdataIngestionJob,
    MarketdataIngestionRun,
} from '@/types/marketdata';

export const marketdataApi = {
    async listBars(query: MarketdataBarsQuery): Promise<MarketdataBar[]> {
        const {data} = await apiClient.get<MarketdataBar[]>('/marketdata/bars', {
            params: query,
        });
        return data;
    },
    async createIngestionJob(request: CreateMarketdataIngestionJobRequest): Promise<MarketdataIngestionJob> {
        const {data} = await apiClient.post<MarketdataIngestionJob>('/marketdata/ingestion-jobs', request);
        return data;
    },
    async listIngestionJobs(): Promise<MarketdataIngestionJob[]> {
        const {data} = await apiClient.get<MarketdataIngestionJob[]>('/marketdata/ingestion-jobs');
        return data;
    },
    async runIngestionJobOnce(jobId: string): Promise<MarketdataIngestionRun> {
        const {data} = await apiClient.post<MarketdataIngestionRun>(`/marketdata/ingestion-jobs/${jobId}/run-once`);
        return data;
    },
    async listIngestionRuns(jobId: string): Promise<MarketdataIngestionRun[]> {
        const {data} = await apiClient.get<MarketdataIngestionRun[]>(`/marketdata/ingestion-jobs/${jobId}/runs`);
        return data;
    },
};
