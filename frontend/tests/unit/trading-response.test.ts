import {describe, expect, it} from 'vitest';
import {parseTradingWorkbenchResponse} from '@/features/trading/trading-response';

describe('普通交易十进制边界', () => {
    it('列表保留大额价格和数量的原始小数，身份与分页继续是整数', () => {
        const raw =
            '{"items":[{"orderId":"ordinary-order","accountId":7,"price":9007199254740993.12345678,"quantity":12345678.12345678}],"page":0,"size":20,"total":1}';
        expect(parseTradingWorkbenchResponse(raw)).toEqual({
            items: [
                {
                    orderId: 'ordinary-order',
                    accountId: 7,
                    price: '9007199254740993.12345678',
                    quantity: '12345678.12345678',
                },
            ],
            page: 0,
            size: 20,
            total: 1,
        });
        expect(JSON.parse(raw).items[0].price.toString()).not.toBe('9007199254740993.12345678');
    });
    it('余额与持仓详情不经过浮点，也不把可用和冻结字段与总额混合', () => {
        expect(
            parseTradingWorkbenchResponse(
                '{"balance":1234567890123456.123456789012345678,"available":1.000000000000000001,"frozen":0.000000000000000001,"quantity":0.123456789012345678,"availableQuantity":0.00000001,"avgPrice":81234.123456789012345678}',
            ),
        ).toEqual({
            balance: '1234567890123456.123456789012345678',
            available: '1.000000000000000001',
            frozen: '0.000000000000000001',
            quantity: '0.123456789012345678',
            availableQuantity: '0.00000001',
            avgPrice: '81234.123456789012345678',
        });
    });
    it('实际费用的零、缺失、原币和负返佣保持不同含义', () => {
        expect(
            parseTradingWorkbenchResponse(
                '[{"fee":0,"feeCurrency":"USDT"},{"fee":null,"feeCurrency":null},{"fee":-0.000000000000000001,"feeCurrency":"BTC"}]',
            ),
        ).toEqual([
            {fee: '0', feeCurrency: 'USDT'},
            {fee: null, feeCurrency: null},
            {fee: '-0.000000000000000001', feeCurrency: 'BTC'},
        ]);
    });
});
