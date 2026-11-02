import brandIcon from '@/assets/brand/nq-ribbon-icon-128.png';
import './brand.css';

interface BrandLockupProps {
    compact?: boolean;
    caption?: string;
}

/** 统一使用用户确认的丝带图标；图片为装饰，产品名称由容器提供。 */
export function BrandLockup({compact = false, caption}: BrandLockupProps) {
    return (
        <div className="nq-brand" aria-label="NexusQuant">
            <span className="nq-brand__monogram" aria-hidden="true">
                <img src={brandIcon} alt="" width={38} height={38}/>
            </span>
            {!compact && <span className="nq-brand__lockup">
                <span className="nq-brand__name">NEXUS QUANT</span>
                {caption && <span className="nq-brand__caption">{caption}</span>}
            </span>}
        </div>
    );
}
