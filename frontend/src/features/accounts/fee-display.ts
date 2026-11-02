import {decimalDisplay} from '@/utils/decimal-display';

/** 账户费率保留12位小数，转为百分比需10位；最小非零收费/返佣必须与真实零值区分。 */
export function formatAccountFeePercent(rate: string | null | undefined): string {
    if (rate == null) return '—';
    return decimalDisplay(rate, 10, 2, true) + '%';
}
