import {describe, expect, it} from 'vitest';
import {resolveDefaultAccount} from '@/features/accounts/default-account';
import type {ExchangeAccountSummary} from '@/features/accounts/types/accounts';
const sim: ExchangeAccountSummary = {
    exchangeAccountId: 1,
    legacyAccountId: null,
    exchangeCode: 'OKX',
    tradeEnv: 'SIM',
    accountAlias: 'isolated',
    externalAccountRef: null,
    status: 'ACTIVE',
    isDefault: true,
};
const live = {...sim, exchangeAccountId: 2, tradeEnv: 'LIVE'};
describe('默认账户身份不静默漂移', () => {
    it('失效或不可见默认账户不会落到另一环境的默认账户', () => {
        expect(resolveDefaultAccount(1, [live])).toBeNull();
        expect(resolveDefaultAccount(1, [{...sim, status: 'DISABLED'}, live])).toBeNull();
        expect(resolveDefaultAccount(1, [sim, live])).toBe(sim);
    });
    it('未声明默认身份时也不取列表首项；只有唯一有效默认事实可初始化', () => {
        expect(resolveDefaultAccount(null, [{...sim, isDefault: false}])).toBeNull();
        expect(resolveDefaultAccount(null, [sim, live])).toBeNull();
        expect(resolveDefaultAccount(null, [sim])).toBe(sim);
    });
});
