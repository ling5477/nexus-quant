/** 只解释canonical分录用途，不在前端合计现金或重算账务事实。 */
export function ledgerPostingRole(row: Record<string, unknown>): string {
    if (row.ref_type === 'SIM_FUNDING_CASH') return 'CASH_FUNDING';
    if (row.ref_type === 'SIM_FUNDING_CONTRA') return 'CONTRA_FUNDING';
    const key = String(row.idempotency_key ?? '');
    return key.endsWith(':LEDGER:FEE_1')
        ? 'CASH_FEE'
        : key.endsWith(':LEDGER:FEE_2')
          ? 'CONTRA_FEE'
          : key.endsWith(':LEDGER:1')
            ? 'CASH_PRINCIPAL'
            : key.endsWith(':LEDGER:2')
              ? 'CONTRA_PRINCIPAL'
              : 'UNKNOWN';
}
