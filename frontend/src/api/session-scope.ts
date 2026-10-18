/** 请求开始时捕获的认证身份，不应被随后登录的另一身份接收或触发退出。 */
export function responseBelongsToSession(
    sentAuthorization: unknown,
    token: string | null,
    tokenType = 'Bearer',
    url = '',
): boolean {
    if (url.split('?')[0].endsWith('/auth/login')) return true;
    return (
        (sentAuthorization == null ? null : String(sentAuthorization)) ===
        (token ? `${tokenType} ${token}` : null)
    );
}
