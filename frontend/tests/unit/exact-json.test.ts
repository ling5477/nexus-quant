import {describe, expect, it} from 'vitest';
import {parseExactJson} from '@/api/exact-json';
import {formatNqNumber} from '@/nq-design-system/format/nqFormat';

describe('正式十进制响应不经浮点舍入', () => {
    const fields = new Set(['openPrice', 'closePrice', 'volume']);
    it('保留大额及极小价格词法值，身份和时间相关整数仍按原合同解析', () => {
        const [bar] = parseExactJson(
            '[{"id":42,"openPrice":12345678901234567890.12345678,"closePrice":0.000000000000000001,"volume":0,"tradeCount":100}]',
            fields,
        ) as Array<Record<string, unknown>>;
        expect(bar).toEqual({
            id: 42,
            openPrice: '12345678901234567890.12345678',
            closePrice: '0.000000000000000001',
            volume: '0',
            tradeCount: 100,
        });
        expect(formatNqNumber(bar.openPrice as string, {exact: true})).toBe(
            '12,345,678,901,234,567,890.12345678',
        );
    });
    it('字符串中的数字、转义文本与null不被重写，嵌套字段保持边界', () => {
        expect(
            parseExactJson(
                '{"note":"\\\"volume\\\":1.01","volume":null,"nested":{"closePrice":-1.00000001},"values":[1,2]}',
                fields,
            ),
        ).toEqual({
            note: '"volume":1.01',
            volume: null,
            nested: {closePrice: '-1.00000001'},
            values: [1, 2],
        });
        expect(parseExactJson('{"closePrice":1e-18}', fields)).toEqual({closePrice: '1e-18'});
    });
    it('无效JSON直接失败，不生成替代成功数据', () => {
        expect(() => parseExactJson('{"volume":NaN}', fields)).toThrow();
    });
});
