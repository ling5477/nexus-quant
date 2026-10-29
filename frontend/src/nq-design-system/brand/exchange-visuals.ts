import okxIcon from '@/assets/exchanges/okx-official.png';
import binanceIcon from '@/assets/exchanges/binance-official.ico';

/** 仅映射视觉素材，不表示交易所已启用、账户权限或 LIVE 能力。 */
const exchangeVisuals = new Map<string, Readonly<{label: string; icon: string}>>([
    ['OKX', {label: 'OKX', icon: okxIcon}],
    ['BINANCE', {label: 'BINANCE', icon: binanceIcon}],
]);

export function resolveExchangeVisual(code?: string | null) {
    return code ? exchangeVisuals.get(code) : undefined;
}
