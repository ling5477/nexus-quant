import {DatabaseOutlined, ExperimentOutlined} from '@ant-design/icons';
import {resolveExchangeVisual} from './exchange-visuals';
import './exchange.css';

interface ExchangeIconProps {
    code?: string | null;
}

/** 标识只做装饰；可访问名称由相邻原始交易所代码或调用方提供。 */
export function ExchangeIcon({code}: ExchangeIconProps) {
    const visual = resolveExchangeVisual(code);
    return (
        <span className="nq-exchange-icon" aria-hidden="true">
            {visual ? <img src={visual.icon} alt="" width={18} height={18}/>
                : code === 'PAPER' ? <ExperimentOutlined/> : <DatabaseOutlined/>}
        </span>
    );
}

export function ExchangeBadge({code}: ExchangeIconProps) {
    return <span className="nq-exchange-badge"><ExchangeIcon code={code}/><span>{code ?? '—'}</span></span>;
}
