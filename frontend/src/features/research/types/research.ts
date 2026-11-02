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
    /** 兼容旧读取；状态缺失时不允许发起归档，最终生命周期以正式接口为准。 */
    status?: string;
    archivedAt?: string | null;
    archivedBy?: string | null;
    archiveReason?: string | null;
}

export interface ResearchListFilters {
    sourceStrategyId: string;
    researchConfigId: string;
    name: string;
}

export interface ResearchPageRequest extends ResearchListFilters {
    limit: number;
    offset: number;
}

/** 总数由服务端按同一筛选口径返回，不能从当前页推算。 */
export interface ResearchConfigPage {
    items: ResearchConfigListItem[];
    total: number;
    limit: number;
    offset: number;
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
