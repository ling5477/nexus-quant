import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it} from 'vitest';
import {resolveExchangeVisual} from '../../src/nq-design-system/brand/exchange-visuals';
import {ExchangeBadge} from '../../src/nq-design-system/brand/ExchangeBadge';

describe('交易所视觉身份与业务能力隔离', () => {
    it('已知交易所只提供原始身份和本地素材', () => {
        for (const code of ['OKX', 'BINANCE']) {
            const visual = resolveExchangeVisual(code);
            expect(visual?.label).toBe(code);
            expect(Object.keys(visual ?? {}).sort()).toEqual(['icon', 'label']);
            expect(visual?.icon).toMatch(/official\.(png|ico)/);
        }
    });

    it('未知或空值不回退成某个真实交易所，也不改写大小写', () => {
        for (const code of ['UNKNOWN', 'okx', '__proto__', 'PAPER', '', null, undefined]) {
            expect(resolveExchangeVisual(code)).toBeUndefined();
        }
    });

    it('保留未知身份的文本并安全转义，不隐藏成空值', () => {
        const html = renderToStaticMarkup(createElement(ExchangeBadge, {code: '<unknown-venue>'}));
        expect(html).toContain('&lt;unknown-venue&gt;');
        expect(html).not.toContain('src=');
    });

    it('官方图像仅作装饰，原始身份仍是可读文本', () => {
        const html = renderToStaticMarkup(createElement(ExchangeBadge, {code: 'BINANCE'}));
        expect(html).toContain('aria-hidden="true"');
        expect(html).toContain('alt=""');
        expect(html).toContain('<span>BINANCE</span>');
    });
});
