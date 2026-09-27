package com.guidinglight.nexusquant.adapter.okx.privateread.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 独立的外部仓位事实；合约张数与保证金币种均不能与现货 BTC 余额相加。
 * 不保留账户标识、仓位 ID、认证信息或原始响应。
 */
public record OkxPrivatePositionFact(
        String instrumentType,
        String instrumentId,
        String marginMode,
        String positionSide,
        BigDecimal positionQuantity,
        String positionCurrency,
        String marginCurrency,
        Instant providerUpdatedAt
) {
    private static final Set<String> INSTRUMENT_TYPES = Set.of("MARGIN", "SWAP", "FUTURES", "OPTION", "EVENTS");
    private static final Set<String> MARGIN_MODES = Set.of("cross", "isolated");
    private static final Set<String> POSITION_SIDES = Set.of("net", "long", "short");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z0-9]{2,12}");
    private static final String PAIR = "[A-Z0-9]{2,12}-[A-Z0-9]{2,12}";

    public OkxPrivatePositionFact {
        Objects.requireNonNull(instrumentType);
        Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(marginMode);
        Objects.requireNonNull(positionSide);
        Objects.requireNonNull(positionQuantity);
        Objects.requireNonNull(marginCurrency);
        Objects.requireNonNull(providerUpdatedAt);
        if (!INSTRUMENT_TYPES.contains(instrumentType) || !validInstrument(instrumentType, instrumentId)
                || !MARGIN_MODES.contains(marginMode) || !POSITION_SIDES.contains(positionSide)
                || !CURRENCY.matcher(marginCurrency).matches()
                || positionQuantity.scale() < 0 || positionQuantity.scale() > 18 || positionQuantity.precision() > 38
                || !providerUpdatedAt.isAfter(Instant.EPOCH)
                || (!"net".equals(positionSide) && positionQuantity.signum() < 0)) {
            throw new IllegalArgumentException("invalid OKX position fact");
        }
        if ("MARGIN".equals(instrumentType)) {
            String[] currencies = instrumentId.split("-");
            if (!"net".equals(positionSide) || positionQuantity.signum() < 0 || positionCurrency == null
                    || !(positionCurrency.equals(currencies[0]) || positionCurrency.equals(currencies[1]))) {
                throw new IllegalArgumentException("invalid OKX margin position fact");
            }
        } else if (positionCurrency != null) {
            throw new IllegalArgumentException("position currency is only applicable to margin positions");
        }
    }

    private static boolean validInstrument(String type, String id) {
        return switch (type) {
            case "MARGIN" -> id.matches(PAIR);
            case "SWAP" -> id.matches(PAIR + "-SWAP");
            case "FUTURES" -> id.matches(PAIR + "-[0-9]{6}");
            case "OPTION" -> id.matches(PAIR + "-[0-9]{6}-[0-9]+(?:\\.[0-9]+)?-[CP]") && id.length() <= 80;
            // EVENTS 名称合同独立于期货；只接受有界、不能注入路径的 instrument 标识。
            case "EVENTS" -> id.matches("[A-Z0-9][A-Z0-9.-]{1,79}");
            default -> false;
        };
    }

    @Override
    public String toString() {
        return "OkxPrivatePositionFact[REDACTED]";
    }
}
