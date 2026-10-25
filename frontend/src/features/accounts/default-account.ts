import type {ExchangeAccountSummary} from './types/accounts';

/** 已声明默认身份失效时返回空，不能换成列表中的另一账户或跨环境回退。 */
export function resolveDefaultAccount(
    id: number | null | undefined,
    accounts: readonly ExchangeAccountSummary[],
): ExchangeAccountSummary | null {
    const explicit =
        id != null ? accounts.find((account) => account.exchangeAccountId === id) : null;
    if (id != null) return explicit?.status === 'ACTIVE' ? explicit : null;
    const defaults = accounts.filter((account) => account.isDefault && account.status === 'ACTIVE');
    return defaults.length === 1 ? defaults[0] : null;
}
