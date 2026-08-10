import {Card} from 'antd';

import {PageHero} from '@/components/page/PageHero';
import {ValidationReviewWorkbench} from '@/components/validation-review/ValidationReviewWorkbench';

/**
 * 保留 validation review 的页面标题与既有 durable review workbench 组合，不接管 URL 或 review 状态。
 */
export function ValidationReviewSection() {
    return (
        <>
            <Card className="page-card" variant="borderless">
                <PageHero
                    title="Validation Operations Workbench"
                    description="整合本地 validation evidence 与 durable review lifecycle，用于人工复核排序、证据检查和有限状态流转。"
                    badge="验证运营 · 本地人工复核"
                    tip="诊断审查不构成交易授权；下方保留只读运营复核 sections，本页不会启动 LIVE、Shadow trading、runner、Python 或交易入口。"
                />
            </Card>
            <ValidationReviewWorkbench/>
        </>
    );
}
