import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it} from 'vitest';
import {
    formatNqMoney,
    formatNqNumber,
    formatNqPercent,
    nqDirectionOf,
} from '../../src/nq-design-system/format/nqFormat';
import {NqAmountText, NqPercentText} from '../../src/components/nq/NqNumericText';

describe('shared monetary display preserves source decimals', () => {
    it('rounds large positive and negative monetary boundaries without IEEE754', () => {
        expect(formatNqNumber('9007199254740993.005')).toBe('9,007,199,254,740,993.01');
        expect(formatNqMoney('-9007199254740993.005', {currency: 'USDT'})).toBe(
            '-9,007,199,254,740,993.01 USDT',
        );
    });
    it('retains fixed precision, grouping and signed display behavior', () => {
        expect(formatNqNumber('1234.5', {precision: 4, thousands: false, signed: true})).toBe(
            '+1234.5000',
        );
        expect(formatNqNumber('1.5', {precision: 0})).toBe('2');
        expect(formatNqNumber('-0.0001')).toBe('0.00');
        expect(formatNqNumber(1.25, {precision: 2})).toBe('1.25');
    });
    it('scales precise ratios by decimal position and honors exact percentage display', () => {
        expect(formatNqPercent('0.123456789012345678', {ratio: true, exact: true})).toBe(
            '+12.3456789012345678%',
        );
        expect(formatNqPercent('0.01005', {ratio: true})).toBe('+1.01%');
        expect(formatNqPercent('-0.01005', {ratio: true})).toBe('-1.01%');
    });
    it('keeps zero, missing and unavailable distinct and honors exact money options', () => {
        for (const value of [null, undefined, '']) expect(formatNqPercent(value)).toBe('-');
        expect(formatNqPercent('UNKNOWN')).toBe('UNKNOWN');
        expect(formatNqPercent('0', {ratio: true})).toBe('0.00%');
        expect(formatNqMoney('1234.000000000000000001', {exact: true, thousands: false})).toBe(
            '1234.000000000000000001',
        );
        expect(nqDirectionOf('-0.0')).toBe('flat');
        expect(nqDirectionOf('-1e-100')).toBe('down');
    });
    it('renders the actual shared components without losing digits between parser and text', () => {
        expect(
            renderToStaticMarkup(createElement(NqAmountText, {value: '9007199254740993.005'})),
        ).toContain('9,007,199,254,740,993.01');
        expect(
            renderToStaticMarkup(
                createElement(NqPercentText, {
                    value: '0.123456789012345678',
                    ratio: true,
                    exact: true,
                }),
            ),
        ).toContain('+12.3456789012345678%');
    });
    it('keeps the exact value accessible when the visible cell uses a rounded display', () => {
        expect(
            renderToStaticMarkup(createElement(NqAmountText, {value: '9007199254740993.005'})),
        ).toContain('title="9,007,199,254,740,993.005"');
        expect(
            renderToStaticMarkup(
                createElement(NqPercentText, {value: '0.123456789012345678', ratio: true}),
            ),
        ).toContain('title="+12.3456789012345678%"');
    });
});
