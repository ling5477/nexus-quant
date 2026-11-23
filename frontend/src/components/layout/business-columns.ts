import type {ColumnsType} from 'antd/es/table';

/** 首屏先呈现业务名称与状态；原始身份列后移而不删除，复制及详情操作保持原对象。 */
export function businessColumns<T extends object>(columns: ColumnsType<T>): ColumnsType<T> {
    const rank = (key: string) => {
        if (['name', 'strategyName', 'datasetName', 'title'].includes(key)) return 0;
        if (['strategyCode', 'symbol'].includes(key)) return 1;
        if (['status', 'enabled', 'tradeEnv', 'strategyType'].includes(key)) return 2;
        if (/Id$/.test(key)) return 8;
        if (key === 'action' || key === 'actions') return 9;
        return 4;
    };
    return [...columns].sort((left, right) => rank(String(left.key ?? '')) - rank(String(right.key ?? '')));
}
