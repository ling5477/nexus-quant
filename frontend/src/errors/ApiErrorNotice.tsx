import {Alert} from 'antd';
import {useTranslation} from 'react-i18next';
import {describeApiError, formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import type {ErrorPresentation} from '@/errors/catalog';

export function ApiErrorNotice({error, presentation, className}: {
    error: Partial<AppApiError>; presentation?: ErrorPresentation; className?: string;
}) {
    useTranslation('errors');
    const view = describeApiError(error);
    return <Alert className={className} showIcon type={view.catalog.severity}
        data-error-presentation={presentation ?? view.catalog.presentation}
        message={view.title} description={formatApiError(error)}/>;
}
