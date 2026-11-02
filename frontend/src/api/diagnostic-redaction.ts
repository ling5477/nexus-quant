const sensitiveFields = new Set([
    'password',
    'currentpassword',
    'newpassword',
    'passwordhash',
    'accesstoken',
    'refreshtoken',
    'authorization',
    'apikey',
    'secretkey',
    'passphrase',
    'privatekeypem',
    'jwtsecret',
    'masterkey',
    'credentialskey',
]);

function isSensitivePath(path: string): boolean {
    return path
        .replace(/\[['"]?([^'"\]]+)['"]?\]/g, '.$1')
        .split('.')
        .some((part) => sensitiveFields.has(part.replace(/[_-]/g, '').toLowerCase()));
}

/** 错误详情是可展示数据；不复制请求配置中的认证头、凭据表单或不受限的对象。 */
export function redactDiagnosticData(value: unknown, depth = 0): unknown {
    if (depth > 8) return '[depth limit]';
    if (Array.isArray(value))
        return value.slice(0, 100).map((item) => redactDiagnosticData(item, depth + 1));
    if (value && typeof value === 'object') {
        const input = value as Record<string, unknown>,
            result: Record<string, unknown> = {};
        const secretField = typeof input.field === 'string' && isSensitivePath(input.field);
        for (const [key, child] of Object.entries(input).slice(0, 100)) {
            result[key] =
                isSensitivePath(key) || (secretField && key === 'rejectedValue')
                    ? '[redacted]'
                    : redactDiagnosticData(child, depth + 1);
        }
        return result;
    }
    return typeof value === 'string' ? value.slice(0, 8192) : value;
}
