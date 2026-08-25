package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单次人工最小 pilot 的显式 authority；它不是 strategy、risk engine 或通用 LIVE 授权。
 */
public record OperatorPilotAuthority(
        UUID id,
        long ownerUserId,
        long exchangeAccountId,
        long credentialReferenceId,
        String instrument,
        Side side,
        OrderType orderType,
        BigDecimal maxNotional,
        int maxPlaceCount,
        int maxCancelCount,
        boolean transferAllowed,
        boolean withdrawAllowed,
        Instant validFrom,
        Instant expiresAt,
        Status status,
        long createdBy,
        Instant createdAt,
        String canonicalDigest
) {
    public static final String DIGEST_SCHEMA = "operator-pilot-authority.v1";
    public static final String REQUIRED_INSTRUMENT = "BTC-USDT";
    public static final BigDecimal HARD_CAP = new BigDecimal("10.00000000");
    private static final String ZERO_DIGEST = "0".repeat(64);

    public OperatorPilotAuthority {
        Objects.requireNonNull(id, "id must not be null");
        require(ownerUserId > 0 && exchangeAccountId > 0 && credentialReferenceId > 0 && createdBy > 0,
                "authority references must be positive");
        require(ownerUserId == createdBy, "authority creator must be the owner");
        require(REQUIRED_INSTRUMENT.equals(instrument), "BTC-USDT is the only operator pilot instrument");
        require(side == Side.BUY, "BUY is the only operator pilot side");
        require(orderType == OrderType.LIMIT, "LIMIT is the only operator pilot order type");
        maxNotional = CanonicalDigestSupport.money(maxNotional, "maxNotional");
        require(maxNotional.signum() > 0 && maxNotional.compareTo(HARD_CAP) <= 0,
                "maxNotional exceeds the 10 USDT hard cap");
        require(maxPlaceCount == 1 && maxCancelCount == 1,
                "operator pilot permits exactly one PLACE and at most one CANCEL");
        require(!transferAllowed && !withdrawAllowed, "transfer and withdraw must be disabled");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        require(expiresAt.isAfter(validFrom), "authority window must be non-empty");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireDigest(canonicalDigest, "canonicalDigest");
    }

    public static OperatorPilotAuthority active(
            UUID id,
            long ownerUserId,
            long exchangeAccountId,
            long credentialReferenceId,
            String instrument,
            Side side,
            OrderType orderType,
            BigDecimal maxNotional,
            Instant validFrom,
            Instant expiresAt,
            long createdBy,
            Instant createdAt
    ) {
        OperatorPilotAuthority draft = new OperatorPilotAuthority(
                id, ownerUserId, exchangeAccountId, credentialReferenceId, instrument, side, orderType,
                maxNotional, 1, 1, false, false, validFrom, expiresAt, Status.ACTIVE,
                createdBy, createdAt, ZERO_DIGEST);
        return draft.withDigest(OperatorPilotAuthorityCanonicalEncoder.digest(draft));
    }

    public boolean activeAt(Instant decisionAt) {
        return status == Status.ACTIVE
                && !decisionAt.isBefore(validFrom)
                && decisionAt.isBefore(expiresAt);
    }

    public boolean hasCanonicalDigest() {
        return ExactPilotBindingCanonicalEncoder.constantTimeEquals(
                canonicalDigest, OperatorPilotAuthorityCanonicalEncoder.digest(this));
    }

    public void requireScope(
            long owner,
            long account,
            long credential,
            String candidateInstrument,
            Side candidateSide,
            OrderType candidateOrderType,
            BigDecimal notional,
            Instant decisionAt
    ) {
        require(activeAt(Objects.requireNonNull(decisionAt, "decisionAt must not be null")),
                "operator pilot authority is not active");
        require(ownerUserId == owner && exchangeAccountId == account && credentialReferenceId == credential,
                "operator pilot account authority mismatch");
        require(instrument.equals(candidateInstrument) && side == candidateSide && orderType == candidateOrderType,
                "operator pilot order scope mismatch");
        BigDecimal exactNotional = CanonicalDigestSupport.money(notional, "notional");
        require(exactNotional.signum() > 0 && exactNotional.compareTo(maxNotional) <= 0,
                "operator pilot notional exceeds authority");
    }

    private OperatorPilotAuthority withDigest(String digest) {
        return new OperatorPilotAuthority(
                id, ownerUserId, exchangeAccountId, credentialReferenceId, instrument, side, orderType,
                maxNotional, maxPlaceCount, maxCancelCount, transferAllowed, withdrawAllowed,
                validFrom, expiresAt, status, createdBy, createdAt, digest);
    }

    private static void requireDigest(String value, String name) {
        require(value != null && value.matches("[0-9a-f]{64}"), name + " must be lowercase SHA-256");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public enum Side {
        BUY,
        SELL
    }

    public enum OrderType {
        LIMIT,
        MARKET
    }

    public enum Status {
        ACTIVE,
        CLOSED,
        EXPIRED
    }
}
