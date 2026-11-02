package com.guidinglight.nexusquant.account.domain.fee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/** 完整现货commission条件；分量是供应方原生正成本符号，不等于某笔成交或单一全局费率。 */
public record SpotCommissionTerms(
        String nativeSymbol,
        Rates standard,
        Rates tax,
        Rates special,
        Discount discount,
        String responseSha256) {
    public SpotCommissionTerms {
        Objects.requireNonNull(standard);
        Objects.requireNonNull(tax);
        Objects.requireNonNull(special);
        Objects.requireNonNull(discount);
        if (nativeSymbol == null
                || !nativeSymbol.matches("[\\p{L}\\p{N}]{2,40}")
                || responseSha256 == null
                || !responseSha256.matches("[a-f0-9]{64}")) throw invalid();
    }

    /** 买卖及流动性角色由消费模型明确指定；优惠需要足额资产假设，两个开关不能证明余额足额。 */
    public BigDecimal costRate(Side side, LiquidityRole role, PaymentCondition payment) {
        Objects.requireNonNull(side);
        Objects.requireNonNull(role);
        Objects.requireNonNull(payment);
        BigDecimal normal = standard.rate(side, role);
        if (payment == PaymentCondition.DISCOUNT_ASSET_SUFFICIENT) {
            if (!discount.enabledForAccount() || !discount.enabledForSymbol())
                throw new IllegalArgumentException("FEE_DISCOUNT_UNAVAILABLE");
            normal = normal.multiply(discount.multiplier());
        }
        return normal.add(tax.rate(side, role)).add(special.rate(side, role));
    }

    /** 数值保存为十进制字符串，避免JSON/JavaScript浮点失真；字段顺序固定用于来源摘要。 */
    public ObjectNode snapshot(ObjectMapper mapper) {
        ObjectNode value = mapper.createObjectNode();
        value.put("nativeSymbol", nativeSymbol);
        value.set("standard", standard.snapshot(mapper));
        value.set("tax", tax.snapshot(mapper));
        value.set("special", special.snapshot(mapper));
        ObjectNode offer = mapper.createObjectNode();
        offer.put("enabledForAccount", discount.enabledForAccount());
        offer.put("enabledForSymbol", discount.enabledForSymbol());
        offer.put("asset", discount.asset());
        offer.put("multiplier", discount.multiplier().toPlainString());
        value.set("discount", offer);
        value.put("responseSha256", responseSha256);
        return value;
    }

    /** 持久化重读也检查完整schema；不忽略未来未知分量、缺字段或数值JSON。 */
    public static SpotCommissionTerms fromSnapshot(JsonNode node) {
        keys(
                node,
                Set.of("nativeSymbol", "standard", "tax", "special", "discount", "responseSha256"));
        JsonNode offer = node.path("discount");
        keys(offer, Set.of("enabledForAccount", "enabledForSymbol", "asset", "multiplier"));
        if (!offer.path("enabledForAccount").isBoolean()
                || !offer.path("enabledForSymbol").isBoolean()) throw invalid();
        return new SpotCommissionTerms(
                text(node, "nativeSymbol"),
                rates(node.path("standard")),
                rates(node.path("tax")),
                rates(node.path("special")),
                new Discount(
                        offer.path("enabledForAccount").booleanValue(),
                        offer.path("enabledForSymbol").booleanValue(),
                        text(offer, "asset"),
                        decimal(offer, "multiplier")),
                text(node, "responseSha256"));
    }

    private static Rates rates(JsonNode node) {
        keys(node, Set.of("maker", "taker", "buyer", "seller"));
        return new Rates(
                decimal(node, "maker"),
                decimal(node, "taker"),
                decimal(node, "buyer"),
                decimal(node, "seller"));
    }

    private static void keys(JsonNode node, Set<String> expected) {
        if (node == null
                || !node.isObject()
                || node.size() != expected.size()
                || expected.stream().anyMatch(key -> !node.has(key))) throw invalid();
    }

    private static String text(JsonNode node, String key) {
        if (!node.path(key).isTextual()) throw invalid();
        return node.path(key).textValue();
    }

    private static BigDecimal decimal(JsonNode node, String key) {
        String value = text(node, key);
        if (!value.matches("-?[0-9]{1,3}(\\.[0-9]{1,12})?")) throw invalid();
        return new BigDecimal(value);
    }

    private static BigDecimal rate(BigDecimal value) {
        if (value == null || value.scale() > 12 || value.abs().compareTo(BigDecimal.ONE) > 0)
            throw invalid();
        return value.setScale(12);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("FEE_COMMISSION_TERMS_INVALID");
    }

    /** 每类费率同时保留maker/taker和buyer/seller；不能遗漏方向附加费。 */
    public record Rates(BigDecimal maker, BigDecimal taker, BigDecimal buyer, BigDecimal seller) {
        public Rates {
            maker = SpotCommissionTerms.rate(maker);
            taker = SpotCommissionTerms.rate(taker);
            buyer = SpotCommissionTerms.rate(buyer);
            seller = SpotCommissionTerms.rate(seller);
        }

        BigDecimal rate(Side side, LiquidityRole role) {
            return (role == LiquidityRole.MAKER ? maker : taker)
                    .add(side == Side.BUY ? buyer : seller);
        }

        ObjectNode snapshot(ObjectMapper mapper) {
            ObjectNode value = mapper.createObjectNode();
            value.put("maker", maker.toPlainString());
            value.put("taker", taker.toPlainString());
            value.put("buyer", buyer.toPlainString());
            value.put("seller", seller.toPlainString());
            return value;
        }
    }

    /** 优惠只乘standard，tax/special保持原值；asset是条件资产，不是已经发生的feeCurrency。 */
    public record Discount(
            boolean enabledForAccount,
            boolean enabledForSymbol,
            String asset,
            BigDecimal multiplier) {
        public Discount {
            multiplier = SpotCommissionTerms.rate(multiplier);
            if (multiplier.signum() < 0 || asset == null || !asset.matches("[A-Z0-9]{1,20}"))
                throw invalid();
        }
    }

    public enum Side {
        BUY,
        SELL
    }

    public enum LiquidityRole {
        MAKER,
        TAKER
    }

    public enum PaymentCondition {
        RECEIVED_ASSET,
        DISCOUNT_ASSET_SUFFICIENT
    }

    @Override
    public String toString() {
        return "SpotCommissionTerms[REDACTED]";
    }
}
