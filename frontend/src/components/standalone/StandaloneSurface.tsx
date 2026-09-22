import type {ReactNode} from 'react';

import './StandaloneSurface.css';

interface StandaloneSurfaceProps {
    children: ReactNode;
    className?: string;
    ariaLabel?: string;
}

/** 登录页与异常页共用全局主题，仅负责独立页面的布局。 */
export function StandaloneSurface({children, className, ariaLabel}: StandaloneSurfaceProps) {
    return (
        <main className={className ? `nq-standalone ${className}` : 'nq-standalone'} aria-label={ariaLabel}>
            {children}
        </main>
    );
}
