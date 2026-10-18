import {describe, expect, it} from 'vitest';
import {responseBelongsToSession} from '../../src/api/session-scope';
import {redactDiagnosticData} from '../../src/api/diagnostic-redaction';
import {CanceledError} from 'axios';
import {shouldRetryQuery} from '../../src/errors/retry-policy';
import {describeApiError} from '../../src/api/errors';

describe('认证范围与错误展示', () => {
    it('读取连接失败不宣称写入结果未知；写操作仍保留未知结果语义', () => {
        const read = describeApiError({code: 'NETWORK_ERROR', status: 0, method: 'GET'});
        const write = describeApiError({code: 'NETWORK_ERROR', status: 0, method: 'POST'});
        expect(read.message).toContain('读取失败');
        expect(read.message).not.toContain('尚未确认');
        expect(write.message).toContain('尚未确认');
    });
    it('旧会话成功或401响应不能被下一用户接收，退出后的旧响应同样失效', () => {
        expect(
            responseBelongsToSession(
                'Bearer synthetic-a',
                'synthetic-b',
                'Bearer',
                '/exchange-accounts',
            ),
        ).toBe(false);
        expect(
            responseBelongsToSession('Bearer synthetic-a', null, 'Bearer', '/exchange-accounts'),
        ).toBe(false);
        expect(responseBelongsToSession(undefined, 'synthetic-b', 'Bearer', '/auth/me')).toBe(
            false,
        );
        expect(
            responseBelongsToSession(
                'Bearer synthetic-b',
                'synthetic-b',
                'Bearer',
                '/exchange-accounts',
            ),
        ).toBe(true);
    });
    it('新登录响应可建立新会话，查询字符串不能冒充登录端点', () => {
        expect(responseBelongsToSession(undefined, null, 'Bearer', '/auth/login')).toBe(true);
        expect(
            responseBelongsToSession(
                'Bearer synthetic-a',
                'synthetic-b',
                'Bearer',
                '/accounts?next=/auth/login',
            ),
        ).toBe(false);
    });
    it('旧会话被取消的读取不在新会话重放', () => {
        expect(shouldRetryQuery(0, new CanceledError('synthetic session changed'))).toBe(false);
    });
    it('可展示诊断保留业务原因与费用，清除嵌套认证和凭据拒绝值', () => {
        const diagnostic = {
            code: 'STATE_CONFLICT',
            feeCurrency: 'USDT',
            fee: '-0.000001',
            authorization: 'synthetic-token',
            nested: {apiKey: 'synthetic-key', maskedAccessKey: 'abc***xyz'},
            fieldErrors: [
                {field: 'newPassword', rejectedValue: 'synthetic-password', message: 'invalid'},
                {field: 'quantity', rejectedValue: '-1'},
            ],
        };
        const safe = redactDiagnosticData(diagnostic) as typeof diagnostic;
        expect(safe.code).toBe('STATE_CONFLICT');
        expect(safe.fee).toBe('-0.000001');
        expect(safe.nested.maskedAccessKey).toBe('abc***xyz');
        expect(safe.fieldErrors[1].rejectedValue).toBe('-1');
        expect(JSON.stringify(safe)).not.toContain('synthetic-token');
        expect(JSON.stringify(safe)).not.toContain('synthetic-key');
        expect(JSON.stringify(safe)).not.toContain('synthetic-password');
    });
    it('带表单前缀及数组路径的拒绝值同样脱敏', () => {
        const safe = redactDiagnosticData({
            fieldErrors: [
                {field: 'credentials.apiKey', rejectedValue: 'synthetic-prefixed-key'},
                {field: 'accounts[0]["secretKey"]', rejectedValue: 'synthetic-array-secret'},
                {field: 'order.quantity', rejectedValue: '-1'},
            ],
        }) as {fieldErrors: Array<{rejectedValue: string}>};
        expect(safe.fieldErrors.map((item) => item.rejectedValue)).toEqual([
            '[redacted]',
            '[redacted]',
            '-1',
        ]);
    });
});
