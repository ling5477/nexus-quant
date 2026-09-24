import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Card} from 'antd';

import {PageHero} from '@/components/page/PageHero';
import {ValidationReviewWorkbench} from '@/features/validation/components/ValidationReviewWorkbench';

/**
 * 保留 validation review 的页面标题与既有 durable review workbench 组合，不接管 URL 或 review 状态。
 */
export function ValidationReviewSection() {
    useTranslation('pages');
    return (
        <>
            <Card className="page-card" variant="borderless">
                <PageHero
                    title={t('pages:validationOperationsWorkbench')}
                    description={t('pages:combinesLocalValidationEvidenceAndDurableReviewLifecyclesForReviewPrioritizationEvidenceInspectionAn')}
                    badge={t('pages:validationOperationsLocalManualReview')}
                    tip={t('pages:diagnosticReviewGrantsNoTradingAuthorizationTheReadOnlySectionsBelowDoNotStartLiveShadowTradingRunne')}
                />
            </Card>
            <ValidationReviewWorkbench/>
        </>
    );
}
