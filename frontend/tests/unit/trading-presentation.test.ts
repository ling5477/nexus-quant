import {describe, expect, it} from 'vitest';
import {
    tradingOperationFeedback,
    tradingOrderStatuses,
} from '@/features/trading/trading-presentation';

describe('正式委托回执展示', () => {
    const feedback = (detail: string, action = 'placeOrder') =>
        tradingOperationFeedback({action, detail, traceId: 'trace'});
    it('风控拒绝不能显示为成功成交或操作成功', () => {
        expect(feedback('order_id=ord-1, status=RISK_REJECTED, idempotent_hit=false')).toEqual({
            tone: 'warning',
            key: 'rejected',
            status: 'RISK_REJECTED',
        });
        expect(feedback('status=REJECTED').tone).toBe('warning');
        expect(feedback('status=FAILED').tone).toBe('error');
    });
    it('已接收和部分成交均不显示全部成交', () => {
        for (const state of ['NEW', 'RISK_PASSED', 'SENT', 'ACCEPTED', 'ACKED', 'SUBMITTING']) {
            expect(feedback(`status=${state}`)).toEqual({
                tone: 'info',
                key: 'pending',
                status: state,
            });
        }
        expect(feedback('status=PARTIALLY_FILLED')).toEqual({
            tone: 'info',
            key: 'partial',
            status: 'PARTIALLY_FILLED',
        });
        expect(feedback('status=FILLED')).toEqual({
            tone: 'success',
            key: 'filled',
            status: 'FILLED',
        });
    });
    it('缺失、未知、重复和嵌在其他字段中的状态都必须待核对', () => {
        for (const detail of [
            '',
            'status=FUTURE_STATUS',
            'status=FILLED, status=RISK_REJECTED',
            'external_status=FILLED',
            'reason=provider status=FILLED',
        ]) {
            expect(feedback(detail)).toEqual({tone: 'info', key: 'pending', status: null});
        }
    });
    it('维护或撤单回执不能因邻接状态被当作新委托成交', () => {
        expect(feedback('status=FILLED', 'cancelOrder')).toEqual({
            tone: 'info',
            key: 'received',
            status: 'FILLED',
        });
        expect(feedback('processed=0', 'reconcile')).toEqual({
            tone: 'info',
            key: 'received',
            status: null,
        });
    });
    it('筛选覆盖当前15个正式状态，不提交不存在的CREATED', () => {
        expect(tradingOrderStatuses).toHaveLength(15);
        expect(new Set(tradingOrderStatuses).size).toBe(15);
        expect(tradingOrderStatuses).toContain('RISK_REJECTED');
        expect(tradingOrderStatuses).not.toContain('CREATED');
    });
});
