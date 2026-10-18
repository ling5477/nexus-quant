import {describe, expect, it} from 'vitest';
import {
    shadowListLocation,
    shadowListReturn,
} from '../../src/features/validation/shadow-navigation';

const recordId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

describe('Shadow 列表的查询与返回上下文', () => {
    it('书签保留状态与第二页，非法页码和未知状态不会读取另一组数据', () => {
        expect(shadowListLocation('?status=COMPLETED&page=2')).toEqual({
            status: 'COMPLETED',
            page: 2,
            error: null,
        });
        for (const page of ['0', '-1', '1.5', '1e3', '42949674', '9007199254740992']) {
            expect(shadowListLocation('?page=' + page).error).toBe('page');
        }
        expect(shadowListLocation('?status=TRADE_APPROVED').error).toBe('status');
    });

    it('返回只保留列表筛选和页码，不能携带外部地址或无关对象定位', () => {
        expect(
            shadowListReturn({
                search: '?status=COMPLETED&page=2&redirect=https://example.com&accountId=8',
                scrollY: 480,
                focusId: recordId,
            }),
        ).toEqual({search: '?status=COMPLETED&page=2', scrollY: 480, focusId: recordId});
        expect(
            shadowListReturn({search: 'https://example.com', scrollY: 480, focusId: recordId}),
        ).toBeNull();
    });

    it('损坏的导航状态不能移动页面或构造任意DOM焦点选择器', () => {
        for (const value of [
            null,
            'bad',
            {search: '', scrollY: Infinity, focusId: recordId},
            {search: '', scrollY: -1, focusId: recordId},
            {search: '', scrollY: 1, focusId: '[data-secret]'},
        ]) {
            expect(shadowListReturn(value)).toBeNull();
        }
    });
});
