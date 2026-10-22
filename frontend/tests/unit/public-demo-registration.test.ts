import {describe, expect, it, vi} from 'vitest';
import {registrationStatus} from '../../src/features/public-demo/registration-state';
import type {DemoRegistrationResult} from '../../src/features/public-demo/api';

const initialized: DemoRegistrationResult = {status: 'SIM_NOT_BOUND', tenantId: 'tenant-demo',
    exchangeAccountId: 21, accountId: 31, reservedPaperRunId: 'ptr-reserved', boundPaperRunId: null,
    initialCapital: '10000.00000000', currency: 'USDT'};

describe('Public Demo 初始化状态', () => {
    it('只对完整账户身份显示已初始化，不把缺失事实显示为成功', () => {
        expect(registrationStatus(initialized)).toBe('SIM_NOT_BOUND');
        for (const field of ['tenantId', 'exchangeAccountId', 'accountId', 'reservedPaperRunId', 'initialCapital', 'currency'] as const)
            expect(registrationStatus({...initialized, [field]: null})).toBe('UNKNOWN');
        expect(registrationStatus({...initialized, initialCapital: '100000'})).toBe('UNKNOWN');
        expect(registrationStatus({...initialized, status: 'NEW_UNRECOGNIZED_STATUS'})).toBe('UNKNOWN');
        expect(registrationStatus(undefined)).toBe('UNKNOWN');
    });
    it('运行状态必须绑定原预留身份，初始化中和失败状态不混同', () => {
        expect(registrationStatus({...initialized, status: 'SIM_RUNNING', boundPaperRunId: 'ptr-other'})).toBe('UNKNOWN');
        expect(registrationStatus({...initialized, status: 'SIM_RUNNING', boundPaperRunId: 'ptr-reserved'})).toBe('SIM_RUNNING');
        expect(registrationStatus({...initialized, status: 'SIM_STOPPED', boundPaperRunId: 'ptr-reserved'})).toBe('SIM_STOPPED');
        expect(registrationStatus({...initialized, status: 'INITIALIZING'})).toBe('INITIALIZING');
        expect(registrationStatus({...initialized, status: 'PENDING_EMAIL_VERIFICATION'})).toBe('PENDING_EMAIL_VERIFICATION');
        expect(registrationStatus({...initialized, status: 'INITIALIZATION_FAILED'})).toBe('INITIALIZATION_FAILED');
    });
    it('Demo 研究能力只在公共发行构建存在，Self-Hosted 保留原角色权限', async () => {
        vi.stubEnv('VITE_DISTRIBUTION', 'SELF_HOSTED'); vi.resetModules();
        const selfHosted = await import('../../src/utils/distribution');
        expect(selfHosted.canWriteResearch(['DEMO_USER'])).toBe(false);
        expect(selfHosted.canWriteResearch(['VIEWER'])).toBe(false);
        expect(selfHosted.canWriteResearch(['OPERATOR'])).toBe(true);
        vi.stubEnv('VITE_DISTRIBUTION', 'PUBLIC_DEMO'); vi.resetModules();
        const demo = await import('../../src/utils/distribution');
        expect(demo.canWriteResearch(['DEMO_USER'])).toBe(true);
        expect(demo.canWriteResearch(['PLATFORM_ADMIN'])).toBe(false);
        expect(demo.canWriteResearch(undefined)).toBe(false);
        vi.unstubAllEnvs(); vi.resetModules();
    });
});
