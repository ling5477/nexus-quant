export interface ResearchConfigListItem {
    researchConfigId: string;
    sourceStrategyId: string;
    strategySnapshot: string;
    name: string;
    description: string;
    parameterSchema: string;
    parameterDefaults: string;
    datasetSpec: string;
    createdAt: string;
    updatedAt: string;
}

export interface ResearchListFilters {
    sourceStrategyId: string;
    researchConfigId: string;
    name: string;
}

export const defaultResearchListFilters: ResearchListFilters = {
    sourceStrategyId: '',
    researchConfigId: '',
    name: '',
};

export interface ResearchConfigCreateRequest {
    sourceStrategyId: string;
    name: string;
    description?: string;
    parameterSchema: string;
    parameterDefaults: string;
    datasetSpec: string;
}
