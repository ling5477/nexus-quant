import type {ResultPage} from '@/api/result-page';

export interface StrategyDefinitionListItem {
    strategyId: string;
    strategyCode: string;
    strategyName: string;
    strategyType: string;
    exchangeCode: string;
    accountId: number | null;
    tradeEnv: string;
    enabled: boolean;
    status: string;
    configSnapshot: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface StrategyListFilters {
    strategyCode: string;
    strategyType: string;
    exchangeCode: string;
    tradeEnv: string;
    enabled: 'all' | 'true' | 'false';
}

export const defaultStrategyListFilters: StrategyListFilters = {
    strategyCode: '',
    strategyType: '',
    exchangeCode: '',
    tradeEnv: '',
    enabled: 'all',
};

/** ID用于关联对象精确定位；名称筛选预留给有界业务选择器，不改变旧字段。 */
export interface StrategyPageRequest extends StrategyListFilters {
    strategyId?: string;
    strategyName?: string;
    limit: number;
    offset: number;
}

export type StrategyDefinitionPage = ResultPage<StrategyDefinitionListItem>;

export interface StrategyStatusUpdateRequest {
    enabled: boolean;
}

export interface StrategyVersionItem {
    strategyVersionId: string;
    strategyCode: string;
    version: number;
    versionName: string;
    status: string;
    paramSnapshotJson: string;
    configSnapshotJson: string;
    sourceSnapshotJson: string;
    checksum: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

/** 目录仅传元信息，三份冻结快照通过所属策略和版本身份按需读取。 */
export type StrategyVersionListItem = Omit<
    StrategyVersionItem,
    'paramSnapshotJson' | 'configSnapshotJson' | 'sourceSnapshotJson'
>;

export interface StrategyVersionPageRequest {
    strategyVersionId?: string;
    versionName?: string;
    version?: number;
    status?: string;
    limit: number;
    offset: number;
}

export type StrategyVersionPage = ResultPage<StrategyVersionListItem>;

export interface StrategyVersionCreateRequest {
    versionName: string;
    status?: string;
    paramSnapshotJson?: string;
    configSnapshotJson?: string;
    sourceSnapshotJson?: string;
}

export interface StrategyDefinitionCreateRequest {
    strategyCode: string;
    strategyName: string;
    strategyType: string;
    exchangeCode: string;
    accountId: number;
    tradeEnv: 'SIM' | 'LIVE';
    configSnapshot: string;
}
