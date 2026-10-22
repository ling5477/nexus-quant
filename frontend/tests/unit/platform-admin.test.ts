import {afterEach, describe, expect, it, vi} from 'vitest';
import {clearPendingUserOperation, readPendingUserOperation, savePendingUserOperation, type PendingUserOperation} from '../../src/features/platform/pending-operation';

const operation: PendingUserOperation = {userId: 17, enabled: false, input: {operationId: '8cf283c5-a30c-467b-b58f-ab23e4e754a4', reason: 'Temporarily suspend this user'}};
afterEach(() => {vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.resetModules();});
describe('平台操作恢复', () => {
    it('未知结果保留原请求和动作，同一浏览器中的其他会话及目标不能借用该回执', () => {
        const storage = new Map<string, string>();
        vi.stubGlobal('sessionStorage', {getItem: (key: string) => storage.get(key) ?? null,
            setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key)});
        savePendingUserOperation('platform-session', operation);
        expect(readPendingUserOperation('platform-session', 17)).toEqual(operation);
        expect(readPendingUserOperation('other-session', 17)).toBeNull();
        expect(readPendingUserOperation('platform-session', 18)).toBeNull();
        clearPendingUserOperation('platform-session', 17);
        expect(readPendingUserOperation('platform-session', 17)).toBeNull();
    });
    it('损坏存储和浏览器禁止存储都不会生成可提交的恢复身份', () => {
        vi.stubGlobal('sessionStorage', {getItem: () => '{corrupted'});
        expect(readPendingUserOperation('session', 17)).toBeNull();
        vi.stubGlobal('sessionStorage', {getItem: () => JSON.stringify({...operation, userId: 99})});
        expect(readPendingUserOperation('session', 17)).toBeNull();
        vi.stubGlobal('sessionStorage', {getItem: () => {throw new Error('denied');}, setItem: () => {throw new Error('denied');}, removeItem: () => {throw new Error('denied');}});
        expect(readPendingUserOperation('session', 17)).toBeNull();
        expect(() => savePendingUserOperation('session', operation)).not.toThrow();
        expect(() => clearPendingUserOperation('session', 17)).not.toThrow();
    });
    it('平台登录首页与研究权限分离，Self-Hosted不产生公共入口', async () => {
        vi.stubEnv('VITE_DISTRIBUTION', 'PUBLIC_DEMO');
        let distribution = await import('../../src/utils/distribution');
        expect(distribution.sessionHome(['PLATFORM_ADMIN'])).toBe('/platform');
        expect(distribution.sessionHome(['DEMO_USER'])).toBe('/demo');
        expect(distribution.canWriteResearch(['PLATFORM_ADMIN'])).toBe(false);
        expect(distribution.loginDestination('/demo', ['PLATFORM_ADMIN'])).toBe('/platform');
        expect(distribution.loginDestination('/platform/tenants?enabled=false', ['DEMO_USER'])).toBe('/demo');
        expect(distribution.loginDestination('//external.test', ['DEMO_USER'])).toBe('/demo');
        expect(distribution.loginDestination('/platform/tenants', ['PLATFORM_ADMIN'])).toBe('/platform/tenants');
        vi.stubEnv('VITE_DISTRIBUTION', 'SELF_HOSTED'); vi.resetModules();
        distribution = await import('../../src/utils/distribution');
        expect(distribution.sessionHome(['PLATFORM_ADMIN'])).toBe('/dashboard');
        expect(distribution.canWriteResearch(['DEMO_USER'])).toBe(false);
    });
});
