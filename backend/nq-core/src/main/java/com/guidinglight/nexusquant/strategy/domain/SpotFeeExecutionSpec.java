package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.fee.FrozenAccountFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.FrozenDirectionalFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;

import java.math.BigDecimal;
import java.util.UUID;

/** 从已由正式来源端口复核的执行合同重建数学模型；本类的摘要检查不能替代持久观察和所有者核验。 */
public final class SpotFeeExecutionSpec {
    private SpotFeeExecutionSpec() {}

    /** 旧合同返回null；方向模型须完整冻结支付条件，优惠分支还须绑定独立价格来源，不能回退到默认零费率。 */
    public static SpotFeeModel directional(JsonNode spec, ObjectMapper mapper) {
        if (!FrozenDirectionalFeeModel.VERSION.equals(spec.path("feeAssumptionVersion").asText()))
            return null;
        if (!FrozenAccountFeeModel.MODE.equals(spec.path("costSource").asText())
                || spec.has("feeRate")
                || !"BINANCE".equals(spec.path("feeExchangeCode").asText())) throw invalid();
        try {
            Policy policy = Policy.valueOf(spec.path("feePaymentPolicy").asText());
            if (policy != Policy.RECEIVED_ASSET && policy != Policy.DISCOUNT_ASSET_IF_FUNDED)
                throw invalid();
            JsonNode snapshot = mapper.readTree(spec.path("feeSourceSnapshotJson").asText());
            if (!FrozenDirectionalFeeModel.digest(snapshot)
                            .equals(spec.path("feeSourceSnapshotSha256").asText())
                    || !"TAKER".equals(snapshot.path("liquidityRole").asText())
                    || !policy.name().equals(snapshot.path("paymentPolicy").asText())
                    || !spec.path("feeSymbol").asText().equals(snapshot.path("symbol").asText())
                    || !"8".equals(snapshot.path("baseFeeScale").asText())
                    || !"8".equals(snapshot.path("quoteFeeScale").asText())
                    || !"8".equals(snapshot.path("discountAssetFeeScale").asText()))
                throw invalid();
            SpotCommissionTerms terms =
                    SpotCommissionTerms.fromSnapshot(snapshot.path("commissionTerms"));
            if (policy == Policy.DISCOUNT_ASSET_IF_FUNDED) {
                // 摘要仅绑定输入；真实数据集、所有者和可见时间仍由来源服务核验，不能接受客户端价格。
                UUID.fromString(spec.path("discountPriceDatasetId").asText());
                UUID.fromString(spec.path("discountPriceSnapshotId").asText());
                BigDecimal fraction =
                        new BigDecimal(spec.path("discountAssetBudgetFraction").asText());
                if (!spec.path("discountPriceSnapshotSha256").asText().matches("[a-f0-9]{64}")
                        || fraction.signum() <= 0
                        || fraction.compareTo(BigDecimal.ONE) >= 0
                        || fraction.scale() > 8
                        || !spec.path("feeSymbol").asText().endsWith("-USDT")
                        || terms.discount().asset().equals("USDT")
                        || spec.path("feeSymbol")
                                .asText()
                                .startsWith(terms.discount().asset() + "-")) throw invalid();
            }
            return SpotFeeModel.commission(terms, policy, Precision.canonical());
        } catch (JsonProcessingException ex) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("FEE_DIRECTIONAL_EXECUTION_SPEC_INVALID");
    }
}
