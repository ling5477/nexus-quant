import {readFileSync, readdirSync} from 'node:fs';
import {resolve, join} from 'node:path';
import ts from 'typescript';
import {expect, it} from 'vitest';
import i18n from '@/i18n';
import '@/features/public-demo/i18n';
import {
    BOOLEAN_FILTER_OPTIONS,
    TRADE_ENV_OPTIONS,
    EXCHANGE_OPTIONS,
} from '@/constants/filter-options';

function files(root: string): string[] {
    return readdirSync(root, {withFileTypes: true}).flatMap((entry) =>
        entry.isDirectory()
            ? files(join(root, entry.name))
            : /\.tsx?$/.test(entry.name)
              ? [join(root, entry.name)]
              : [],
    );
}

/** 按词法作用域读取页面自己选择的命名空间，避免把 pages 词条误判为 common 缺失。 */
function translationNamespace(node: ts.Node): string {
    for (let scope = node.parent; scope; scope = scope.parent) {
        if (!ts.isBlock(scope) && !ts.isSourceFile(scope)) continue;
        for (const statement of scope.statements) {
            if (!ts.isVariableStatement(statement)) continue;
            for (const declaration of statement.declarationList.declarations) {
                if (
                    !ts.isObjectBindingPattern(declaration.name) ||
                    !declaration.name.elements.some(
                        (element) =>
                            element.name.getText() === 't' &&
                            (element.propertyName?.getText() ?? 't') === 't',
                    )
                )
                    continue;
                const call = declaration.initializer;
                if (
                    !call ||
                    !ts.isCallExpression(call) ||
                    call.expression.getText() !== 'useTranslation'
                )
                    continue;
                const namespace = call.arguments[0];
                if (namespace && ts.isStringLiteral(namespace)) return namespace.text;
                if (
                    namespace &&
                    ts.isArrayLiteralExpression(namespace) &&
                    namespace.elements[0] &&
                    ts.isStringLiteral(namespace.elements[0])
                )
                    return namespace.elements[0].text;
                return 'common';
            }
        }
    }
    return 'common';
}

it('every literal translation call resolves directly in both languages', () => {
    const missing: string[] = [];
    for (const file of files(resolve('src'))) {
        const source = ts.createSourceFile(
            file,
            readFileSync(file, 'utf8'),
            ts.ScriptTarget.Latest,
            true,
        );
        function visit(node: ts.Node) {
            const translationArgument =
                ts.isCallExpression(node) &&
                node.expression.getText(source) === 't' &&
                node.arguments[0] &&
                ts.isStringLiteral(node.arguments[0])
                    ? node.arguments[0]
                    : undefined;
            const storedKey =
                ts.isStringLiteral(node) && /^(pages|errors|common):.+/.test(node.text)
                    ? node
                    : undefined;
            const literal = translationArgument ?? storedKey;
            if (literal) {
                const key = literal.text;
                const [namespace, path] = key.includes(':')
                    ? key.split(':', 2)
                    : [translationNamespace(node), key];
                for (const locale of ['zh-CN', 'en-US']) {
                    if (typeof i18n.getResource(locale, namespace, path) !== 'string')
                        missing.push(`${file}: ${locale} / ${key}`);
                }
            }
            ts.forEachChild(node, visit);
        }
        visit(source);
    }
    expect(missing).toEqual([]);
});

it('locale changes option labels without changing machine values', async () => {
    const values = () =>
        [BOOLEAN_FILTER_OPTIONS, TRADE_ENV_OPTIONS, EXCHANGE_OPTIONS].map((options) =>
            options.map(({value}) => value),
        );
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
    const machineProperties = new Set([
        'queryKey',
        'mutationKey',
        'capability',
        'sourceType',
        'reasonCode',
        'errorId',
        'code',
        'expectedVersion',
        'idempotencyKey',
        'clientOrderId',
    ]);
    for (const file of files(resolve('src'))) {
        const source = ts.createSourceFile(
            file,
            readFileSync(file, 'utf8'),
            ts.ScriptTarget.Latest,
            true,
        );
        function containsTranslation(node: ts.Node): boolean {
            return (
                (ts.isCallExpression(node) && node.expression.getText(source) === 't') ||
                Boolean(ts.forEachChild(node, containsTranslation))
            );
        }
        function visit(node: ts.Node) {
            if (
                ts.isPropertyAssignment(node) &&
                machineProperties.has(node.name.getText(source).replace(/['"]/g, '')) &&
                containsTranslation(node.initializer)
            ) {
                violations.push(`${file}: ${node.getText(source)}`);
            }
            ts.forEachChild(node, visit);
        }
        visit(source);
    }
    expect(violations).toEqual([]);
});
