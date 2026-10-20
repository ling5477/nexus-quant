import type {MarketdataIngestionRun} from '@/types/marketdata';

type Notice = {
    type: 'warning' | 'error';
    message: 'ingestion.partialCapture' | 'ingestion.sourceDisabled' | 'ingestion.sourceFailed';
};

/** 部分结果按正式状态提示校验问题；只有FAILED才显示运行失败，原原因仍保留在跟踪详情中。 */
export function ingestionResultNotice(
    run: Pick<MarketdataIngestionRun, 'status' | 'errorMessage'>,
): Notice | null {
    if (run.status === 'PARTIAL') {
        return {type: 'warning', message: 'ingestion.partialCapture'};
    }
    if (run.status === 'FAILED') {
        return {
            type: 'error',
            message:
                run.errorMessage === 'PUBLIC_HISTORY_OUTBOUND_DISABLED'
                    ? 'ingestion.sourceDisabled'
                    : 'ingestion.sourceFailed',
        };
    }
    return null;
}
