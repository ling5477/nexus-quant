package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 仅为 OKX 现货小时级连续 SIM 提供受限公开观察；不携带账户或交易能力。 */
public interface ClosedBarMarketFeed {
    Observation observe(Instant firstOpen, int maximumBars);

    /** 末次估值只需要确认收盘事实。生产实现必须为完整响应设置短超时，以免阻塞人工结束。 */
    default Observation observeFinalBar(Instant open) {
        return observe(open, 1);
    }

    record Observation(Instant serverTime, Instant observedAt, List<HistoricalBar> bars,
                       Rule rule, Quote quote, String responseBodySha256) {
        public Observation(Instant serverTime, Instant observedAt, List<HistoricalBar> bars,
                           Rule rule, Quote quote) {
            this(serverTime, observedAt, bars, rule, quote, null);
        }
    }

    int MAX_RESPONSE_BYTES = 128_000;

    enum ResponseKind { COMPLETE_RESPONSE, INCOMPLETE_RESPONSE, TRANSPORT_INTERRUPTED, PARSE_FAILED }

    /** 响应未能完整解析。已验证的闭合行仅用于比对修订，不能作为可推进策略的行情页。 */
    final class IncompleteObservation extends IllegalStateException {
        private final List<HistoricalBar> verifiedBars;
        private final ResponseKind kind;
        private final byte[] receivedPrefix;

        public IncompleteObservation(List<HistoricalBar> verifiedBars, IllegalStateException cause) {
            this(verifiedBars, ResponseKind.INCOMPLETE_RESPONSE, new byte[0], cause);
        }

        public IncompleteObservation(List<HistoricalBar> verifiedBars, ResponseKind kind,
                                     byte[] receivedPrefix, IllegalStateException cause) {
            super(cause.getMessage(), cause);
            if (kind == ResponseKind.COMPLETE_RESPONSE || receivedPrefix.length > MAX_RESPONSE_BYTES) {
                throw new IllegalArgumentException("INVALID_INCOMPLETE_RESPONSE_EVIDENCE");
            }
            this.verifiedBars = List.copyOf(verifiedBars);
            this.kind = kind;
            this.receivedPrefix = receivedPrefix.clone();
        }

        public ResponseKind kind() { return kind; }

        public byte[] receivedPrefix() { return receivedPrefix.clone(); }

        public List<HistoricalBar> verifiedBars() {
            return verifiedBars;
        }
    }

    /** 当前公开成交价只用于下一可交易时点；它不是已收盘策略输入 bar。 */
    record Quote(Instant observedAt, BigDecimal price) { }

    record Rule(Instant observedAt, String state, BigDecimal tickSize,
                BigDecimal lotSize, BigDecimal minimumSize) { }
}
