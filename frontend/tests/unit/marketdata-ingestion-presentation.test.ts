import {describe, expect, it} from 'vitest';
import {ingestionResultNotice} from '@/features/marketdata/ingestion-result-presentation';

describe('ingestion original result presentation', () => {
    it('keeps a partial validation result distinct from an external request failure', () => {
        expect(
            ingestionResultNotice({
                status: 'PARTIAL',
                errorMessage: 'some bars failed historical kline quality validation',
            }),
        ).toEqual({type: 'warning', message: 'ingestion.partialCapture'});
        expect(ingestionResultNotice({status: 'PARTIAL', errorMessage: null})?.type).toBe(
            'warning',
        );
    });
    it('still reports a failed result even when no raw error reason is available', () => {
        expect(ingestionResultNotice({status: 'FAILED', errorMessage: null})).toEqual({
            type: 'error',
            message: 'ingestion.sourceFailed',
        });
    });
    it('explains the explicit outbound-disabled failure without treating it as an empty source', () => {
        expect(
            ingestionResultNotice({
                status: 'FAILED',
                errorMessage: 'PUBLIC_HISTORY_OUTBOUND_DISABLED',
            }),
        ).toEqual({type: 'error', message: 'ingestion.sourceDisabled'});
    });
    it('does not turn stale raw text into a failure for an otherwise successful or running result', () => {
        expect(
            ingestionResultNotice({status: 'SUCCEEDED', errorMessage: 'old diagnostic'}),
        ).toBeNull();
        expect(ingestionResultNotice({status: 'RUNNING', errorMessage: null})).toBeNull();
    });
});
