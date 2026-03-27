export interface BacktestConfigListItem {
    backtestConfigId: string;
    researchConfigId: string;
    name: string;
    description: string;
    startTime: string;
    endTime: string;
    initialCapital: number | null;
    executionSpec: string;
    evaluationSpec: string;
    configSnapshot: string;
    createdAt: string;
    updatedAt: string;
}

export interface BacktestsListFilters {
    researchConfigId: string;
    backtestConfigId: string;
    name: string;
}

export const defaultBacktestsListFilters: BacktestsListFilters = {
    researchConfigId: '',
    backtestConfigId: '',
    name: '',
};

export interface BacktestConfigCreateRequest {
    researchConfigId: string;
    name: string;
    description?: string;
    startTime: string;
    endTime: string;
    initialCapital: number;
    executionSpec: string;
    evaluationSpec: string;
}
