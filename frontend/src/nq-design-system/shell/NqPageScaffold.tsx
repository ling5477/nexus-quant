import type {ReactNode} from 'react';

interface NqPageScaffoldProps {
    children: ReactNode;
    className?: string;
}

/** 页面只编排业务分区；导航、权限与页级背景继续由共享产品壳负责。 */
export function NqPageScaffold({children, className}: NqPageScaffoldProps) {
    return <div className={['nq-page-scaffold', className].filter(Boolean).join(' ')}>{children}</div>;
}
