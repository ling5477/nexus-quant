import {apiClient} from '@/api/client';

export interface FactorPoint {
    index: number;
    windowStart: string;
    windowEnd: string;
    featureAvailableAt: string;
    factorValue: string | null;
    qualityStatus: string;
}
export interface StatisticalWindow {
    name: string;
    featureFrom: string;
    featureTo: string;
    lastLabelAvailableAt: string;
    candidateSamples: number;
    effectiveSamples: number;
    missingSamples: number;
    minimumSamples: number;
    correlation: string | null;
    confidenceInterval: {lower: string; upper: string; level: string} | null;
    status: string;
    blockLength: number;
}
export interface FactorReport {
    factorKey: string;
    factorVersion: string;
    parameters: {window: number; definition: string; ddof: number; annualized: boolean};
    points: FactorPoint[];
    statistics: {
        methodVersion: string;
        horizonBars: number;
        purgedTrainingSamples: number;
        bootstrap: {seed: number; repetitions: number};
        windows: StatisticalWindow[];
    };
}
export interface FactorArtifact {
    schemaVersion: string;
    artifactId: string;
    dataset: Record<string, string>;
    barCount: number;
    factors: FactorReport[];
    observationAssumptions: Record<string, string>;
    generatedAt: string;
    checksum: string;
    source: string;
    limitations: string[];
    warnings: string[];
}
const path = (id: string) => `/marketdata/datasets/${encodeURIComponent(id)}/factor-research`;
export const factorResearchApi = {
    async download(id: string, signal: AbortSignal): Promise<Blob> {
        const {data} = await apiClient.get<Blob>(`${path(id)}/input`, {signal, responseType: 'blob'});
        return data;
    },
    async preview(id: string, json: string, signal: AbortSignal): Promise<FactorArtifact> {
        const {data} = await apiClient.post<FactorArtifact>(`${path(id)}/preview`, json,
            {signal, headers: {'Content-Type': 'application/json'}});
        if (data.schemaVersion !== 'python-factor-research-artifact.v1' || data.dataset.datasetId !== id
            || !Array.isArray(data.factors) || data.factors.length !== 3) throw new Error('FACTOR_PREVIEW_IDENTITY_INVALID');
        return data;
    },
};
