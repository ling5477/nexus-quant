package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.service.AccountFeeModelService;
import com.guidinglight.nexusquant.account.domain.fee.FrozenAccountFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.FrozenDirectionalFeeModel;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.marketdata.application.service.DiscountAssetPriceService;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.app.marketdata.PublicMarketReplayCaptureService;
import com.guidinglight.nexusquant.app.marketdata.ResearchPublicRules;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 在组合根连接认证、账户来源与研究合同；客户端只选观察身份，不能提交完整“实际费用”事实。 */
@Component
public class ResearchAccountFeeSource implements ResearchFeeSourcePort {
    private static final Set<String> SEALED =
            Set.of(
                    "feeSourceOwnerUserId",
                    "feeSourceSnapshotJson",
                    "feeSourceSnapshotSha256",
                    "discountPriceSnapshotId",
                    "discountPriceSnapshotSha256");
    private static final Set<String> REFERENCES =
            Set.of(
                    "feeObservationId",
                    "feeAccountId",
                    "feeTradeEnv",
                    "feeSymbol",
                    "feeExchangeCode",
                    "feePaymentPolicy",
                    "discountPriceDatasetId",
                    "discountAssetBudgetFraction");
    private final AccountFeeModelService models;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;
    private final ObjectMapper mapper;
    private final ObjectReader snapshotReader;
    private final Clock clock = Clock.systemUTC();
    private DiscountAssetPriceService discountPrices;
    private PublicMarketReplayCaptureService publicRules;

    @Autowired
    void bindPublicRules(ObjectProvider<PublicMarketReplayCaptureService> provider) {
        publicRules = provider.getIfAvailable();
    }

    public ResearchAccountFeeSource(
            AccountFeeModelService models,
            GatewayAuthFacade auth,
            CurrentUserProfileService users,
            ObjectMapper mapper) {
        this.models = models;
        this.auth = auth;
        this.users = users;
        this.mapper = mapper;
        // 费用准备位于配置冻结之前，必须先保留所有执行参数精度；不修改共享解析器或旧来源摘要。
        this.snapshotReader =
                mapper.reader()
                        .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
    }

    /** 缺少价格来源装配时仅拒绝依赖优惠的路径，原收到资产合同继续可用。 */
    @Autowired
    void bindDiscountPrices(ObjectProvider<DiscountAssetPriceService> provider) {
        discountPrices = provider.getIfAvailable();
    }

    /** 创建配置可保存来源引用及服务端所有者；快照和摘要保留给创建运行，不接受客户端伪造。 */
    @Override
    public String prepareConfiguration(String executionSpec) {
        JsonNode parsed = read(executionSpec);
        if (parsed.has("researchRuleSnapshotJson"))
            throw invalid("RESEARCH_RULE_SERVER_FIELDS_FORBIDDEN");
        if (SEALED.stream().anyMatch(parsed::has))
            throw invalid("FEE_MODEL_SERVER_FIELDS_FORBIDDEN");
        if (!selected(parsed)) {
            if (REFERENCES.stream().anyMatch(parsed::has))
                throw invalid("FEE_MODEL_REFERENCE_WITHOUT_SOURCE");
            return executionSpec;
        }
        ObjectNode spec = object(parsed);
        long owner = requireOwner();
        if ("BINANCE".equals(exchange(spec))) {
            FrozenDirectionalFeeModel model = selectDirectional(spec, owner, clock.instant());
            discountReference(spec, model);
            spec.put("feeSourceOwnerUserId", Long.toString(owner));
            spec.put("feeAssumptionVersion", FrozenDirectionalFeeModel.VERSION);
            return spec.toString();
        }
        rejectDirectionalFields(spec);
        FrozenAccountFeeModel model = select(spec, owner, clock.instant());
        if (spec.has("feeRate")) checkRate(spec, model.observation().quote().takerCostRate());
        spec.put("feeSourceOwnerUserId", Long.toString(owner));
        spec.put("feeRate", model.observation().quote().takerCostRate().toPlainString());
        spec.put("feeAssumptionVersion", FrozenAccountFeeModel.VERSION);
        return spec.toString();
    }

    /** 新运行再次检查新鲜度，冻结完整原来源；旧配置复制不能隐式改用账户最新观察。 */
    @Override
    public String freezeRunConfiguration(
            String configurationSnapshot, String datasetSnapshot, Instant at) {
        ObjectNode config = object(read(configurationSnapshot));
        JsonNode researchDataset = read(datasetSnapshot);
        if (researchDataset.has("frozenInput")) {
            if (publicRules == null) throw invalid("RESEARCH_RULE_SOURCE_UNAVAILABLE");
            ObjectNode execution = object(config.path("executionSpec"));
            ObjectNode rules = publicRules.researchRules(researchDataset.path("symbol").asText());
            execution.put("researchRuleSnapshotJson", rules.toString());
            execution.put("rulePolicy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
            execution.put("priceTick", rules.path("rule").path("tickSize").asText());
            execution.put("quantityStep", rules.path("rule").path("lotSize").asText());
            execution.put("minimumQuantity", rules.path("rule").path("minimumSize").asText());
            config.set("executionSpec", execution);
        }
        JsonNode parsed = config.path("executionSpec");
        if (!selected(parsed)) {
            rejectUnexpectedSource(parsed);
            return config.toString();
        }
        ObjectNode spec = object(parsed);
        long owner = requireOwner();
        if (!Long.toString(owner).equals(spec.path("feeSourceOwnerUserId").asText())
                || spec.has("feeSourceSnapshotJson")
                || spec.has("feeSourceSnapshotSha256")
                || spec.has("discountPriceSnapshotId")
                || spec.has("discountPriceSnapshotSha256")) {
            throw invalid("FEE_MODEL_CONFIGURATION_SCOPE_INVALID");
        }
        checkMarket(spec, read(datasetSnapshot));
        if ("BINANCE".equals(exchange(spec))) {
            checkDirectionalVersion(spec);
            FrozenDirectionalFeeModel model = selectDirectional(spec, owner, at);
            ObjectNode snapshot = model.snapshot(mapper);
            spec.put("feeSourceSnapshotJson", snapshot.toString());
            spec.put("feeSourceSnapshotSha256", FrozenDirectionalFeeModel.digest(snapshot));
            if (model.paymentPolicy() == Policy.DISCOUNT_ASSET_IF_FUNDED) {
                discountReference(spec, model);
                JsonNode dataset = read(datasetSnapshot);
                DiscountAssetPriceSnapshot prices =
                        priceSource()
                                .freeze(
                                        owner,
                                        UUID.fromString(text(spec, "discountPriceDatasetId")),
                                        "BINANCE",
                                        model.observation()
                                                .quote()
                                                .commissionTerms()
                                                .discount()
                                                .asset(),
                                        "USDT",
                                        Instant.parse(text(dataset, "startTime")),
                                        Instant.parse(text(dataset, "endTime")),
                                        model.frozenAt());
                // 初始预算只能使用开始时点已可见的价格；不能用后续首个价格补造起始资产。
                prices.priceAt(Instant.parse(text(dataset, "startTime")));
                spec.put("discountPriceSnapshotId", prices.snapshotId().toString());
                spec.put("discountPriceSnapshotSha256", prices.sourceSha256());
            }
            config.set("executionSpec", spec);
            return config.toString();
        }
        rejectDirectionalFields(spec);
        FrozenAccountFeeModel model = select(spec, owner, at);
        checkRate(spec, model.observation().quote().takerCostRate());
        ObjectNode snapshot = model.snapshot(mapper);
        // 完整来源以规范JSON字符串进入原标量执行合同，独立摘要绑定其内容；不改变旧公开规则SHA编码。
        spec.put("feeSourceSnapshotJson", snapshot.toString());
        spec.put("feeSourceSnapshotSha256", FrozenAccountFeeModel.digest(snapshot));
        config.set("executionSpec", spec);
        return config.toString();
    }

    /** 回测/SIM执行只复核被冻结原观察；过期或凭据后续轮换不会访问供应方或换掉费率。 */
    @Override
    public void validateFrozenExecution(String executionSpec, String datasetSnapshot) {
        JsonNode parsed = read(executionSpec);
        ResearchPublicRules.validate(parsed, read(datasetSnapshot), mapper);
        if (!selected(parsed)) {
            rejectUnexpectedSource(parsed);
            return;
        }
        ObjectNode spec = object(parsed);
        long owner = positive(spec, "feeSourceOwnerUserId");
        Long reader = currentReadOwner();
        if (reader != null && reader != owner) throw invalid("FEE_MODEL_OWNER_MISMATCH");
        checkMarket(spec, read(datasetSnapshot));
        String raw = text(spec, "feeSourceSnapshotJson");
        JsonNode snapshot = read(raw);
        if ("BINANCE".equals(exchange(spec))) {
            checkDirectionalVersion(spec);
            Policy policy = paymentPolicy(spec);
            if (!FrozenDirectionalFeeModel.digest(snapshot)
                    .equals(text(spec, "feeSourceSnapshotSha256")))
                throw invalid("FEE_MODEL_SNAPSHOT_DIGEST_INVALID");
            FrozenDirectionalFeeModel expected =
                    models.historicalDirectional(
                            owner,
                            UUID.fromString(text(spec, "feeObservationId")),
                            positive(spec, "feeAccountId"),
                            "BINANCE",
                            text(spec, "feeTradeEnv"),
                            text(spec, "feeSymbol"),
                            Instant.parse(text(snapshot, "frozenAt")),
                            policy);
            if (!expected.snapshot(mapper).equals(snapshot))
                throw invalid("FEE_MODEL_SNAPSHOT_SOURCE_MISMATCH");
            discountReference(spec, expected);
            if (policy == Policy.DISCOUNT_ASSET_IF_FUNDED) {
                DiscountAssetPriceSnapshot prices =
                        priceSource()
                                .read(
                                        owner,
                                        UUID.fromString(text(spec, "discountPriceSnapshotId")),
                                        text(spec, "discountPriceSnapshotSha256"));
                JsonNode dataset = read(datasetSnapshot);
                if (!prices.datasetId().toString().equals(text(spec, "discountPriceDatasetId"))
                        || !prices.exchangeCode().equals("BINANCE")
                        || !prices.quoteCurrency().equals("USDT")
                        || !prices.assetCurrency()
                                .equals(
                                        expected.observation()
                                                .quote()
                                                .commissionTerms()
                                                .discount()
                                                .asset())
                        || !prices.from().equals(Instant.parse(text(dataset, "startTime")))
                        || !prices.to().equals(Instant.parse(text(dataset, "endTime")))
                        || !prices.frozenAt().equals(Instant.parse(text(snapshot, "frozenAt"))))
                    throw invalid("FEE_DISCOUNT_SOURCE_SCOPE_MISMATCH");
            }
            return;
        }
        rejectDirectionalFields(spec);
        if (!FrozenAccountFeeModel.digest(snapshot).equals(text(spec, "feeSourceSnapshotSha256")))
            throw invalid("FEE_MODEL_SNAPSHOT_DIGEST_INVALID");
        FrozenAccountFeeModel expected =
                models.historical(
                        owner,
                        UUID.fromString(text(spec, "feeObservationId")),
                        positive(spec, "feeAccountId"),
                        "OKX",
                        text(spec, "feeTradeEnv"),
                        text(spec, "feeSymbol"),
                        Instant.parse(text(snapshot, "frozenAt")));
        // 比较整份来源，而非仅验证调用方能自行重算的摘要；删除字段、换费率或改身份一律拒绝。
        if (!expected.snapshot(mapper).equals(snapshot))
            throw invalid("FEE_MODEL_SNAPSHOT_SOURCE_MISMATCH");
        checkRate(spec, expected.observation().quote().takerCostRate());
        if (!FrozenAccountFeeModel.VERSION.equals(spec.path("feeAssumptionVersion").asText()))
            throw invalid("FEE_MODEL_VERSION_INVALID");
    }

    /** HTTP范围始终从当前认证解析；没有HTTP请求的调度/恢复读取可复核已经冻结的内部事实。 */
    @Override
    public Long currentReadOwner() {
        var actor = auth.currentUser();
        if (actor.isPresent())
            return users.findByUsername(actor.get().username())
                    .map(value -> value.userId())
                    .orElse(-1L);
        return RequestContextHolder.getRequestAttributes() == null ? null : -1L;
    }

    private long requireOwner() {
        Long owner = currentReadOwner();
        if (owner == null || owner <= 0)
            throw new AuthenticationCredentialsNotFoundException(
                    "authentication required for account fee model");
        return owner;
    }

    private FrozenAccountFeeModel select(ObjectNode spec, long owner, Instant at) {
        String env = text(spec, "feeTradeEnv"), symbol = text(spec, "feeSymbol");
        if (!Set.of("SIM", "LIVE").contains(env)
                || !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}"))
            throw invalid("FEE_MODEL_TARGET_INVALID");
        return models.select(
                owner,
                UUID.fromString(text(spec, "feeObservationId")),
                positive(spec, "feeAccountId"),
                "OKX",
                env,
                symbol,
                at);
    }

    /** 新V2引用明确交易所与支付条件；不从客户端的全局费率或优惠开关推断方向费用。 */
    private FrozenDirectionalFeeModel selectDirectional(ObjectNode spec, long owner, Instant at) {
        if (spec.has("feeRate")) throw invalid("FEE_MODEL_DIRECTIONAL_SCALAR_FORBIDDEN");
        if (spec.has("feeAssumptionVersion")) checkDirectionalVersion(spec);
        String env = text(spec, "feeTradeEnv"), symbol = text(spec, "feeSymbol");
        if (!Set.of("SIM", "LIVE").contains(env)
                || !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}"))
            throw invalid("FEE_MODEL_TARGET_INVALID");
        return models.selectDirectional(
                owner,
                UUID.fromString(text(spec, "feeObservationId")),
                positive(spec, "feeAccountId"),
                "BINANCE",
                env,
                symbol,
                at,
                paymentPolicy(spec));
    }

    private static Policy paymentPolicy(JsonNode spec) {
        String policy = spec.path("feePaymentPolicy").asText();
        if (Policy.RECEIVED_ASSET.name().equals(policy)) return Policy.RECEIVED_ASSET;
        if (Policy.DISCOUNT_ASSET_IF_FUNDED.name().equals(policy))
            return Policy.DISCOUNT_ASSET_IF_FUNDED;
        throw invalid("FEE_MODEL_PAYMENT_POLICY_INVALID");
    }

    private static void discountReference(JsonNode spec, FrozenDirectionalFeeModel model) {
        if (model.paymentPolicy() != Policy.DISCOUNT_ASSET_IF_FUNDED) {
            if (Set.of(
                            "discountPriceDatasetId",
                            "discountAssetBudgetFraction",
                            "discountPriceSnapshotId",
                            "discountPriceSnapshotSha256")
                    .stream()
                    .anyMatch(spec::has)) throw invalid("FEE_DISCOUNT_REFERENCE_WITHOUT_POLICY");
            return;
        }
        UUID.fromString(text(spec, "discountPriceDatasetId"));
        BigDecimal fraction = new BigDecimal(text(spec, "discountAssetBudgetFraction"));
        String asset = model.observation().quote().commissionTerms().discount().asset();
        if (fraction.signum() <= 0
                || fraction.compareTo(BigDecimal.ONE) >= 0
                || fraction.scale() > 8
                || !text(spec, "feeSymbol").endsWith("-USDT")
                || asset.equals("USDT")
                || text(spec, "feeSymbol").startsWith(asset + "-"))
            throw invalid("FEE_DISCOUNT_BUDGET_INVALID");
    }

    private DiscountAssetPriceService priceSource() {
        if (discountPrices == null) throw invalid("FEE_DISCOUNT_PRICE_SOURCE_DISABLED");
        return discountPrices;
    }

    private static void checkDirectionalVersion(JsonNode spec) {
        if (!FrozenDirectionalFeeModel.VERSION.equals(spec.path("feeAssumptionVersion").asText())
                || spec.has("feeRate")) throw invalid("FEE_MODEL_VERSION_INVALID");
    }

    private static void rejectDirectionalFields(JsonNode spec) {
        if (spec.has("feePaymentPolicy")
                || spec.has("discountPriceDatasetId")
                || spec.has("discountAssetBudgetFraction")
                || spec.has("discountPriceSnapshotId")
                || spec.has("discountPriceSnapshotSha256")
                || FrozenDirectionalFeeModel.VERSION.equals(
                        spec.path("feeAssumptionVersion").asText()))
            throw invalid("FEE_MODEL_VERSION_INVALID");
    }

    private static String exchange(JsonNode spec) {
        // 缺字段只兼容已保存的旧OKX合同；新Binance选择必须显式提供，不改写旧快照和SHA。
        String exchange = spec.has("feeExchangeCode") ? text(spec, "feeExchangeCode") : "OKX";
        if (!Set.of("OKX", "BINANCE").contains(exchange))
            throw invalid("FEE_MODEL_EXCHANGE_INVALID");
        return exchange;
    }

    private static void checkMarket(JsonNode spec, JsonNode dataset) {
        if (!exchange(spec).equals(dataset.path("exchangeCode").asText())
                || !"SPOT".equals(dataset.path("marketType").asText())
                || !spec.path("feeSymbol").asText().equals(dataset.path("symbol").asText()))
            throw invalid("FEE_MODEL_DATASET_SCOPE_MISMATCH");
    }

    private static void checkRate(JsonNode spec, BigDecimal rate) {
        if (rate.compareTo(new BigDecimal(text(spec, "feeRate"))) != 0)
            throw invalid("FEE_MODEL_RATE_MISMATCH");
    }

    private static boolean selected(JsonNode spec) {
        return FrozenAccountFeeModel.MODE.equals(spec.path("costSource").asText());
    }

    private static void rejectUnexpectedSource(JsonNode spec) {
        if (SEALED.stream().anyMatch(spec::has) || REFERENCES.stream().anyMatch(spec::has))
            throw invalid("FEE_MODEL_UNEXPECTED_SOURCE_FIELDS");
    }

    private JsonNode read(String raw) {
        if (raw != null && raw.length() > 2_000_000) throw invalid("FEE_MODEL_JSON_TOO_LARGE");
        try {
            return snapshotReader.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (JsonProcessingException ex) {
            throw invalid("FEE_MODEL_JSON_INVALID");
        }
    }

    private static ObjectNode object(JsonNode node) {
        if (!node.isObject()) throw invalid("FEE_MODEL_JSON_OBJECT_REQUIRED");
        return ((ObjectNode) node).deepCopy();
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isValueNode() || value.isNull() || value.asText().isBlank())
            throw invalid("FEE_MODEL_FIELD_INVALID");
        return value.asText();
    }

    private static long positive(JsonNode node, String field) {
        String value = text(node, field);
        if (!value.matches("[1-9][0-9]{0,18}")) throw invalid("FEE_MODEL_ID_INVALID");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw invalid("FEE_MODEL_ID_INVALID");
        }
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException(value);
    }
}
