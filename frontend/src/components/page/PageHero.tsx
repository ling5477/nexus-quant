import {Alert} from 'antd';

import {NqPageHeader} from '@/components/nq';

interface PageHeroProps {
    title: string;
    description: string;
    badge?: string;
    tip?: string;
}

/**
 * PageHero — 兼容旧页面的头部适配层。
 *
 * Why:
 * Design System v1 引入 NqPageHeader 后，存量页面仍引用 PageHero；
 * 这里收敛为薄适配（同一 heading 语义、同一排版），避免一次性改动所有页面。
 * 新页面请直接使用 NqPageHeader。
 */
export function PageHero({title, description, badge, tip}: PageHeroProps) {
    return (
        <NqPageHeader
            title={title}
            description={description}
            badge={badge}
            tip={tip ? <Alert type="info" showIcon message={tip}/> : undefined}
        />
    );
}
