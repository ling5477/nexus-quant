/** 保留指定十进制字段的JSON词法值；图表坐标可显式转number，事实明细不得经过浮点。 */
export function parseExactJson(text: string, decimalFields: ReadonlySet<string>): unknown {
    let field: string | null = null;
    const protectedJson = text.replace(
        /"(?:\\.|[^"\\])*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}\[\]:,]/g,
        (token: string, offset: number) => {
            if (token.startsWith('"')) {
                field = /^\s*:/.test(text.slice(offset + token.length))
                    ? (JSON.parse(token) as string)
                    : null;
                return token;
            }
            if (token === ':') return token;
            const exact = field !== null && decimalFields.has(field) && /^-?\d/.test(token);
            field = null;
            return exact ? JSON.stringify(token) : token;
        },
    );
    return JSON.parse(protectedJson);
}
