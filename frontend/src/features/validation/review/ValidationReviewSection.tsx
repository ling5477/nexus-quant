import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Alert, Card} from 'antd';

import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {ValidationReviewWorkbench} from '@/features/validation/components/ValidationReviewWorkbench';

/**
 * 保留 validation review 的页面标题与既有 durable review workbench 组合，不接管 URL 或 review 状态。
 */
export function ValidationReviewSection() {
    useTranslation('pages');
    return (
        <>
            <Card className="page-card" variant="borderless">
                <NqPageHeader
                    title={t('pages:validationOperationsWorkbench')}
                    description={t('pages:combinesLocalValidationEvidenceAndDurableReviewLifecyclesForReviewPrioritizationEvidenceInspectionAn')}
                    badge={t('pages:validationOperationsLocalManualReview')}
                    tip={<Alert type="info" showIcon message={t('pages:diagnosticReviewGrantsNoTradingAuthorizationTheReadOnlySectionsBelowDoNotStartLiveShadowTradingRunne')}/>}
                />
            </Card>
            <ValidationReviewWorkbench/>
        </>
    );
}
