import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {describe, expect, it} from 'vitest';

import {nqColor, nqRadius, nqSpace} from '../../src/theme/tokens';
import {marketColors, nqTokens} from '../../src/nq-design-system/tokens/nq-tokens';
import {nqCssVars} from '../../src/nq-design-system/tokens/nq-css-vars';

const css = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8');
const cssValues = Object.fromEntries([...css.matchAll(/(--nq-[\w-]+):\s*([^;]+);/g)].map(([, key, value]) => [key, value.trim()]));
const kebab = (key: string) => key.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`);

describe('共享视觉主题契约', () => {
    it('CSS 与 TypeScript 的全部颜色、间距、圆角保持镜像', () => {
        for (const [key, value] of Object.entries(nqColor)) {
            expect(cssValues[`--nq-color-${kebab(key)}`], key).toBe(value);
        }
        for (const [prefix, values] of [['space', nqSpace], ['radius', nqRadius]] as const) {
            for (const [key, value] of Object.entries(values)) {
                expect(cssValues[`--nq-${prefix}-${key}`], `${prefix}.${key}`).toBe(`${value}px`);
            }
        }
    });

    it('旧组件与图表兼容层派生自同一主题', () => {
        expect(nqTokens.bg.panel).toBe(nqColor.bgPanel);
        expect(nqTokens.semantic.primary).toBe(nqColor.primary);
        expect(nqCssVars()['--nq-bg-panel']).toBe(nqColor.bgPanel);
        expect(nqCssVars()['--nq-text-primary']).toBe(nqColor.text);
    });

    it('行情惯例只翻转涨跌色，不改变风险和成功语义', () => {
        expect(marketColors().up).toBe(nqColor.up);
        expect(marketColors('INTL_CRYPTO').up).toBe(nqColor.down);
        expect(marketColors('INTL_CRYPTO').down).toBe(nqColor.up);
        expect(nqCssVars('INTL_CRYPTO')['--nq-danger']).toBe(nqColor.danger);
        expect(nqCssVars('INTL_CRYPTO')['--nq-success']).toBe(nqColor.success);
    });
});
