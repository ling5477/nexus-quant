import {readFileSync, readdirSync} from 'node:fs';
import {resolve, join} from 'node:path';
import ts from 'typescript';
import {expect, it} from 'vitest';
import i18n from '@/i18n';
import {BOOLEAN_FILTER_OPTIONS, TRADE_ENV_OPTIONS, EXCHANGE_OPTIONS} from '@/constants/filter-options';

function files(root: string): string[] {
    return readdirSync(root, {withFileTypes: true}).flatMap((entry) =>
        entry.isDirectory() ? files(join(root, entry.name)) : /\.tsx?$/.test(entry.name) ? [join(root, entry.name)] : []);
}

it('every literal translation call resolves directly in both languages', () => {
    const missing: string[] = [];
    for (const file of files(resolve('src'))) {
        const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
        function visit(node: ts.Node) {
            const translationArgument = ts.isCallExpression(node) && node.expression.getText(source) === 't'
                && node.arguments[0] && ts.isStringLiteral(node.arguments[0]) ? node.arguments[0] : undefined;
            const storedKey = ts.isStringLiteral(node) && /^(pages|errors|common):.+/.test(node.text) ? node : undefined;
            const literal = translationArgument ?? storedKey;
            if (literal) {
                const key = literal.text;
                const [namespace, path] = key.includes(':') ? key.split(':', 2) : ['common', key];
                for (const locale of ['zh-CN', 'en-US']) {
                    if (typeof i18n.getResource(locale, namespace, path) !== 'string') missing.push(`${file}: ${locale} / ${key}`);
                }
            }
            ts.forEachChild(node, visit);
        }
        visit(source);
    }
    expect(missing).toEqual([]);
});

it('locale changes option labels without changing machine values', async () => {
    const values = () => [BOOLEAN_FILTER_OPTIONS, TRADE_ENV_OPTIONS, EXCHANGE_OPTIONS].map((options) => options.map(({value}) => value));
    const before = values();
    await i18n.changeLanguage('en-US');
    expect(BOOLEAN_FILTER_OPTIONS[0].label).toBe('All');
    expect(values()).toEqual(before);
    await i18n.changeLanguage('zh-CN');
    expect(BOOLEAN_FILTER_OPTIONS[0].label).toBe('全部');
    expect(values()).toEqual(before);
});

it('translation calls never supply machine identity properties', () => {
    const violations: string[] = [];
    const machineProperties = new Set(['queryKey', 'mutationKey', 'capability', 'sourceType',
        'reasonCode', 'errorId', 'code', 'expectedVersion', 'idempotencyKey', 'clientOrderId']);
    for (const file of files(resolve('src'))) {
        const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
        function containsTranslation(node: ts.Node): boolean {
            return (ts.isCallExpression(node) && node.expression.getText(source) === 't')
                || Boolean(ts.forEachChild(node, containsTranslation));
        }
        function visit(node: ts.Node) {
            if (ts.isPropertyAssignment(node) && machineProperties.has(node.name.getText(source).replace(/['"]/g, ''))
                && containsTranslation(node.initializer)) {
                violations.push(`${file}: ${node.getText(source)}`);
            }
            ts.forEachChild(node, visit);
        }
        visit(source);
    }
    expect(violations).toEqual([]);
});
