export type ConfigurationValueType = 'string' | 'number' | 'boolean' | 'null' | 'object' | 'array';
export interface ConfigurationField {
    name: string;
    type: ConfigurationValueType;
    text: string;
}

/** 只验证结构，返回原字符串；不得通过解析再序列化舍入配置中的大整数与小数。 */
export function configurationObject(value?: string): string {
    const raw = value?.trim() || '{}';
    const object: unknown = JSON.parse(raw);
    if (!object || typeof object !== 'object' || Array.isArray(object))
        throw new Error('CONFIGURATION_OBJECT_REQUIRED');
    return raw;
}

/** 顶层字段保留各自JSON原文，嵌套扩展及精确数字不会因编辑另一字段而丢失。 */
export function configurationFields(value?: string): ConfigurationField[] {
    const raw = configurationObject(value);
    const fields: ConfigurationField[] = [];
    let cursor = 1;
    const skip = () => {while (/\s/.test(raw[cursor] ?? '') && cursor < raw.length) cursor++;};
    const stringEnd = (start: number) => {
        let end = start + 1;
        while (end < raw.length) {
            if (raw[end] === '\\') end += 2;
            else if (raw[end++] === '"') return end;
        }
        throw new Error('INVALID_CONFIGURATION');
    };
    skip();
    while (raw[cursor] !== '}') {
        const end = stringEnd(cursor);
        const name = JSON.parse(raw.slice(cursor, end)) as string;
        if (fields.some(field => field.name === name)) throw new Error('DUPLICATE_CONFIGURATION_KEY');
        cursor = end;
        skip();
        cursor++;
        skip();
        const start = cursor;
        let depth = 0;
        while (cursor < raw.length) {
            const char = raw[cursor];
            if (char === '"') {cursor = stringEnd(cursor); continue;}
            if (depth === 0 && (char === ',' || char === '}')) break;
            if (char === '{' || char === '[') depth++;
            if (char === '}' || char === ']') depth--;
            cursor++;
        }
        const token = raw.slice(start, cursor).trim();
        const type: ConfigurationValueType = token.startsWith('"') ? 'string'
            : token.startsWith('{') ? 'object' : token.startsWith('[') ? 'array'
            : token === 'null' ? 'null' : ['true', 'false'].includes(token) ? 'boolean' : 'number';
        fields.push({name, type, text: type === 'string' ? JSON.parse(token) as string : token});
        if (fields.length > 100) throw new Error('CONFIGURATION_USE_ADVANCED_EDITOR');
        if (raw[cursor] === ',') cursor++;
        skip();
    }
    return fields;
}

export function serializeConfigurationFields(fields: ConfigurationField[]): string {
    const seen = new Set<string>();
    const entries = fields.map(field => {
        if (!field.name.trim() || seen.has(field.name)) throw new Error('CONFIGURATION_KEY_REQUIRED_UNIQUE');
        seen.add(field.name);
        let token = field.type === 'string' ? JSON.stringify(field.text) : field.text.trim();
        if (field.type === 'null') token = 'null';
        if (field.type === 'number' && !/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(token))
            throw new Error('CONFIGURATION_NUMBER_REQUIRED');
        if (field.type === 'boolean' && !['true', 'false'].includes(token)) throw new Error('CONFIGURATION_BOOLEAN_REQUIRED');
        if (field.type === 'object' || field.type === 'array') {
            const parsed: unknown = JSON.parse(token);
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) !== (field.type === 'array'))
                throw new Error('CONFIGURATION_TYPE_MISMATCH');
        }
        return `${JSON.stringify(field.name)}:${token}`;
    });
    return `{${entries.join(',')}}`;
}

/** 仅生成研究元数据的类型描述，不赋予参数执行语义，也不替代冻结策略版本。 */
export function configurationSchema(defaults?: string): string {
    const fields = configurationFields(defaults);
    return JSON.stringify({type: 'object', properties: Object.fromEntries(fields.map(field =>
        [field.name, {type: field.type === 'number' && /^-?\d+$/.test(field.text) ? 'integer' : field.type}],
    ))});
}
