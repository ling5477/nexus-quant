import {describe, expect, it} from 'vitest';
import {
    shadowDetailSearch,
    shadowDetailSection,
} from '../../src/features/validation/shadow-detail-navigation';

describe('Shadow 详情深链接', () => {
    it('历史无分区链接仍进入概览，显式快照链接可刷新恢复', () => {
        expect(shadowDetailSection('?publishId=original')).toBe('overview');
        expect(shadowDetailSection('?view=snapshots&publishId=original')).toBe('snapshots');
    });
    it('拒绝未知、空和重复分区，不将歧义链接改读为概览', () => {
        for (const search of [
            '?view=trade',
            '?view=',
            '?view=events&view=report',
            '?view=events&view=events',
        ])
            expect(shadowDetailSection(search)).toBeNull();
    });
    it('切换和重置只改变分区，保留原发布及其他查询参数', () => {
        const search = shadowDetailSearch(
            '?publishId=pub%2Boriginal&view=events&note=keep',
            'source',
        );
        expect(new URLSearchParams(search).get('publishId')).toBe('pub+original');
        expect(new URLSearchParams(search).get('note')).toBe('keep');
        expect(shadowDetailSection(search)).toBe('source');
        expect(shadowDetailSection(shadowDetailSearch(search, 'overview'))).toBe('overview');
        expect(new URLSearchParams(shadowDetailSearch(search, 'overview')).has('view')).toBe(false);
    });
});
