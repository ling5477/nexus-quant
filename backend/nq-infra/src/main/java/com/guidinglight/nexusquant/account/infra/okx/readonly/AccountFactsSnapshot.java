package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateBalanceFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateFeeFact;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 单次显式私有 GET 观察的非持久化、脱敏账户事实。 */
public record AccountFactsSnapshot(
        UUID observationId,
        String venue,
        long exchangeAccountId,
        long credentialReference,
        Instant observedAt,
        Fact<String> credentialStatus,
        Fact<String> accountMode,
        Fact<List<String>> permissions,
        Map<String, Fact<OkxPrivateBalanceFact>> balances,
        Fact<String> positions,
        Fact<BigDecimal> spotBtcExposure,
        Fact<Integer> openOrderCount,
        Fact<OkxPrivateFeeFact> fee,
        Fact<Instant> exchangeTime,
        Fact<String> publicInstrumentRuleIdentity,
        Fact<String> divergence,
        Status status,
        String reason
) {
    public AccountFactsSnapshot {
        Objects.requireNonNull(observationId);
        Objects.requireNonNull(observedAt);
        balances = Map.copyOf(balances);
        Objects.requireNonNull(status);
    }

    public enum Status {
        OBSERVED, UNKNOWN, UNAVAILABLE, STALE, NOT_APPLICABLE, REJECTED
    }

    public record Fact<T>(Status status, T value, Instant observedAt, Instant expiresAt,
                          String source, String reason) {
        public Fact {
            Objects.requireNonNull(status);
            Objects.requireNonNull(observedAt);
            Objects.requireNonNull(source);
            if (status == Status.OBSERVED && value == null) {
                throw new IllegalArgumentException("observed fact requires value");
            }
        }

        /** 消费时重新检查期限，避免缓存的旧 snapshot 获得后续 admission 权限。 */
        public Status statusAt(Instant now) {
            return status == Status.OBSERVED && expiresAt != null && now.isAfter(expiresAt)
                    ? Status.STALE : status;
        }
    }

    @Override
    public String toString() {
        return "AccountFactsSnapshot[REDACTED]";
    }
}
