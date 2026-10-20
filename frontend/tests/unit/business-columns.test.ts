import {describe, expect, it} from 'vitest';
import {businessColumns} from '../../src/components/layout/business-columns';

describe('业务表格的信息层级', () => {
    it('前移名称与状态，同时保留每个身份、未知列及原操作对象', () => {
        const input = ['researchConfigId', 'sourceStrategyId', 'name', 'description', 'updatedAt', 'status', 'action'].map(key => ({key}));
        const result = businessColumns(input);
        expect(result.map(column => column.key)).toEqual(['name', 'status', 'description', 'updatedAt', 'researchConfigId', 'sourceStrategyId', 'action']);
        expect(new Set(result)).toEqual(new Set(input));
        expect(input[0].key).toBe('researchConfigId');
    });
});
