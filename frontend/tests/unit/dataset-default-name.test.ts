import {describe, expect, it} from 'vitest';
import {datasetNameForSelection} from '../../src/features/marketdata/dataset-operations';

describe('D01 数据集名称跟随真实选项并保留手动草稿', () => {
    it('OKX BTC-USDT 1h 使用同一组选择及稳定后缀', () => {
        const suffix = '1792224000000';
        expect(datasetNameForSelection({exchangeCode: 'OKX', symbol: 'BTC-USDT', interval: '1h'}, suffix))
            .toBe(`OKX-BTC-USDT-1h-${suffix}`);
        expect(datasetNameForSelection({exchangeCode: 'BINANCE', symbol: 'ETH-USDT', interval: '1m'}, suffix))
            .toBe(`BINANCE-ETH-USDT-1m-${suffix}`);
    });
    it('目录缺项或交易所切换清除交易对后，不拿旧交易对补名称', () => {
        expect(datasetNameForSelection({exchangeCode: 'OKX', interval: '1h'}, 'stable')).toBe('');
        expect(datasetNameForSelection({symbol: 'BTC-USDT', interval: '1h'}, 'stable')).toBe('');
    });
    it('手动名称和主动清空均不被后续交易所、周期或交易对变化覆盖', () => {
        const changed = {exchangeCode: 'BINANCE', symbol: 'ETH-USDT', interval: '1m'};
        expect(datasetNameForSelection(changed, 'stable', '我的数据集')).toBe('我的数据集');
        expect(datasetNameForSelection(changed, 'stable', '')).toBe('');
    });
});
