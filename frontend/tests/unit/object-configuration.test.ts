import {describe, expect, it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {ObjectConfigurationSummary} from '../../src/components/configuration/ObjectConfigurationSummary';
import {configurationFields, configurationObject, configurationSchema, serializeConfigurationFields} from '../../src/components/configuration/object-configuration';

describe('配置字段与正式JSON合同', () => {
    it('可选配置留空时提交空对象，拒绝数组、标量和未完成输入', () => {
        expect(configurationObject(' ')).toBe('{}');
        for (const value of ['[]', 'null', 'false', '12', '"text"', 'INCOMPLETE_CONFIGURATION'])
            expect(() => configurationObject(value)).toThrow();
    });
    it('编辑其他字段仍保留大整数、精确小数、指数和嵌套未知扩展', () => {
        const raw = '{"large":900719925474099312345,"fee":0.000000000000000000123,"scientific":1e-40,"extension":{"nested":[900719925474099312345,{"price":0.12345678901234567890}]},"note":"old"}';
        const fields = configurationFields(raw);
        const updated = serializeConfigurationFields(fields.map(field => field.name === 'note' ? {...field, text: 'new'} : field));
        expect(updated).toBe(raw.replace('"old"', '"new"'));
    });
    it('文本转义、布尔、空值和空结构保持类型', () => {
        const raw = '{"quoted\\\"key":"comma, braces{} and quote\\\"","yes":true,"no":false,"nothing":null,"object":{},"list":[]}';
        expect(serializeConfigurationFields(configurationFields(raw))).toBe(raw);
        expect(configurationFields('{}')).toEqual([]);
    });
    it('不允许空名称、重复名称或未完成数值悄悄提交旧配置', () => {
        expect(() => serializeConfigurationFields([{name: '', type: 'string', text: 'x'}])).toThrow();
        expect(() => configurationFields('{"same":1,"same":2}')).toThrow();
        for (const text of ['-', '01', 'Infinity'])
            expect(() => serializeConfigurationFields([{name: 'x', type: 'number', text}])).toThrow();
        expect(() => serializeConfigurationFields([{name: 'x', type: 'boolean', text: '1'}])).toThrow();
        expect(() => serializeConfigurationFields([{name: 'x', type: 'object', text: '[]'}])).toThrow();
        expect(() => serializeConfigurationFields([{name: 'x', type: 'array', text: '{}'}])).toThrow();
    });
    it('特殊字段名保留，不能污染原型', () => {
        const raw = '{"__proto__":{"polluted":true},"constructor":"kept"}';
        expect(serializeConfigurationFields(configurationFields(raw))).toBe(raw);
        expect(({} as Record<string, unknown>).polluted).toBeUndefined();
    });
    it('详情概览不能将特殊名称当作原型对象，也不能舍入只读数值', () => {
        const rendered = renderToStaticMarkup(createElement(ObjectConfigurationSummary, {
            value: '{"__proto__":{"example":1},"constructor":"kept","large":900719925474099312345}',
        }));
        expect(rendered).toContain('__proto__');
        expect(rendered).toContain('constructor');
        expect(rendered).toContain('900719925474099312345');
    });
    it('只生成研究默认值的类型说明，不制造参数范围和执行合同', () => {
        expect(JSON.parse(configurationSchema('{"window":20,"exposure":"0.3","enabled":true}'))).toEqual({
            type: 'object', properties: {window: {type: 'integer'}, exposure: {type: 'string'}, enabled: {type: 'boolean'}},
        });
    });
    it('大配置使用高级入口，不能静默截断', () => {
        const raw = JSON.stringify(Object.fromEntries(Array.from({length: 101}, (_, i) => ['k' + i, i])));
        expect(configurationObject(raw)).toBe(raw);
        expect(() => configurationFields(raw)).toThrow('CONFIGURATION_USE_ADVANCED_EDITOR');
    });
});
