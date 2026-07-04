package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.JdkPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataEndpointCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundDecision;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundErrorCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundPolicy;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundResult;
import com.guidinglight.nexusquant.app.config.PublicMarketDataOutboundConfiguration;
import com.guidinglight.nexusquant.app.config.env.EnvSafetyGuardConfiguration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * GateOManualPublicOutboundSmokeTest 是 O-5B 手动公开行情 outbound smoke 的 test-only 入口。
 *
 * <p>Why: O-5B 需要一个可审查的手动 runner，但该入口绝不能进入默认 Maven、默认 CI 或普通
 * Spring Boot runtime。类级 {@link EnabledIfSystemProperty} 先要求人工指定
 * {@code nq.gateo.o5.manualSmoke.required=true}；测试体再要求
 * {@code NQ_GATEO_O5_MANUAL_SMOKE=true}、{@code public-marketdata-manual} profile、
 * {@code NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true} 和 no LIVE / no AI / no DH / no real provider
 * / no credential。任何条件缺失都会在 HTTP client 调用前 skip 或 fail-closed。</p>
 *
 * <p>边界：本类只在 test scope 内装配 {@link PublicMarketDataOutboundConfiguration} 与
 * {@link EnvSafetyGuardConfiguration}，不启动完整应用、不读取 {@code .env}、不访问 DB、不新增 API、
 * 不写文件、不生成 raw response / headers / full URL / query evidence。未来真正手动执行时，endpoint
 * path 也只能来自本类内部固定的 OKX public REST category-to-path map，并且每个 request 必须再次经过
 * {@link PublicMarketDataOutboundPolicy}。</p>
 */
@Tag("manual-public-outbound")
@Tag("gateo-o5-manual")
@EnabledIfSystemProperty(named = "nq.gateo.o5.manualSmoke.required", matches = "true")
class GateOManualPublicOutboundSmokeTest {

    private static final String TASK_NAME =
            "NQ-GATEO-O5B-R1-MANUAL-PUBLIC-OUTBOUND-RUNNER-BINDING-IMPLEMENTATION";
    private static final String MANUAL_ENV_FLAG = "NQ_GATEO_O5_MANUAL_SMOKE";
    private static final String PROFILE_NAME = "public-marketdata-manual";
    private static final String FEATURE_FLAG = "NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED";
    private static final Provider REVIEWED_PROVIDER = Provider.OKX;
    private static final String REVIEWED_INSTRUMENT = "BTC-USDT";
    private static final Set<PublicMarketDataEndpointCategory> RUNNER_ALLOWED_CATEGORIES = Set.of(
            PublicMarketDataEndpointCategory.SERVER_TIME,
            PublicMarketDataEndpointCategory.INSTRUMENTS,
            PublicMarketDataEndpointCategory.TICKER,
            PublicMarketDataEndpointCategory.OHLCV
    );
    private static final List<SmokeEndpoint> SMOKE_ENDPOINTS = List.of(
            new SmokeEndpoint(
                    PublicMarketDataEndpointCategory.SERVER_TIME,
                    null,
                    "/api/v5/public/time"),
            new SmokeEndpoint(
                    PublicMarketDataEndpointCategory.INSTRUMENTS,
                    REVIEWED_INSTRUMENT,
                    "/api/v5/public/instruments?instType=SPOT"),
            new SmokeEndpoint(
                    PublicMarketDataEndpointCategory.TICKER,
                    REVIEWED_INSTRUMENT,
                    "/api/v5/market/ticker?instId=BTC-USDT"),
            new SmokeEndpoint(
                    PublicMarketDataEndpointCategory.OHLCV,
                    REVIEWED_INSTRUMENT,
                    "/api/v5/market/candles?instId=BTC-USDT&bar=1m&limit=1")
    );
    private static final List<PublicMarketDataEndpointCategory> EXPLICITLY_REJECTED_CATEGORIES = List.of(
            PublicMarketDataEndpointCategory.ORDER_BOOK,
            PublicMarketDataEndpointCategory.RECENT_TRADES,
            PublicMarketDataEndpointCategory.PUBLIC_WEBSOCKET,
            PublicMarketDataEndpointCategory.ACCOUNT,
            PublicMarketDataEndpointCategory.BALANCE,
            PublicMarketDataEndpointCategory.ORDER,
            PublicMarketDataEndpointCategory.CANCEL,
            PublicMarketDataEndpointCategory.AMEND,
            PublicMarketDataEndpointCategory.POSITIONS,
            PublicMarketDataEndpointCategory.WALLET,
            PublicMarketDataEndpointCategory.TRANSFER,
            PublicMarketDataEndpointCategory.WITHDRAW,
            PublicMarketDataEndpointCategory.DEPOSIT,
            PublicMarketDataEndpointCategory.SUBACCOUNT,
            PublicMarketDataEndpointCategory.PRIVATE_WEBSOCKET,
            PublicMarketDataEndpointCategory.SIGNED_REQUEST,
            PublicMarketDataEndpointCategory.API_KEY_VALIDATION,
            PublicMarketDataEndpointCategory.REAL_PERMISSION_PROBE,
            PublicMarketDataEndpointCategory.AUTHENTICATED,
            PublicMarketDataEndpointCategory.UNKNOWN
    );
    private static final Map<String, String> CREDENTIAL_ENV_TO_PROPERTY = credentialEnvToProperty();

    private final PublicMarketDataOutboundPolicy policy = new PublicMarketDataOutboundPolicy();

    /**
     * 手动执行 O-5B public outbound smoke，并在所有 gate 缺失时 skip 或 fail before HTTP。
     *
     * <p>Why: 本轮只绑定 runner，不执行真实 HTTP；默认验证命令不会设置 class-level system property
     * 或 manual env flag，因此该测试会被 JUnit 标记为 skipped。未来 O-5B-R2 review 通过后，如人工同时
     * 设置所有 gate，本测试才会装配 O-1 JDK client 并调用固定 OKX public REST endpoint。</p>
     */
    @Test
    void shouldRunOnlyWhenAllManualPublicOutboundGatesAreExplicitlyEnabled() {
        assumeTrue(
                flagEnabled(MANUAL_ENV_FLAG),
                "O-5B manual public outbound smoke skipped before HTTP: "
                        + MANUAL_ENV_FLAG + " is not true");

        ManualGate gate = ManualGate.fromProcess();
        gate.assertSafeBeforeHttp();
        assertEndpointBindingIsPolicyBacked();

        String runId = "gateo-o5b-r1-" + UUID.randomUUID();
        Instant startedAt = Instant.now();
        List<SmokeEvidence> evidence = new ArrayList<>();

        manualContextRunner(gate).run(context -> {
            ApplicationRunner envSafetyGuard = context.getBean(ApplicationRunner.class);
            envSafetyGuard.run(new DefaultApplicationArguments());

            PublicMarketDataOutboundClient client = context.getBean(PublicMarketDataOutboundClient.class);
            assertInstanceOf(
                    JdkPublicMarketDataOutboundClient.class,
                    client,
                    "manual profile + feature flag must construct only the O-1 policy-protected JDK client");

            for (SmokeEndpoint endpoint : SMOKE_ENDPOINTS) {
                PublicMarketDataOutboundRequest request = endpoint.toRequest(runId);
                PublicMarketDataOutboundResult result = client.fetch(request);
                SmokeEvidence summary = SmokeEvidence.from(runId, startedAt, Instant.now(), endpoint, result, gate);
                assertRedactedSummary(summary);
                System.out.println(summary.toSummaryLine());
                evidence.add(summary);

                if (result.statusCode() == 401 || result.statusCode() == 403) {
                    fail("AUTH_BOUNDARY_UNEXPECTED before credential use; endpointCategory="
                            + endpoint.category());
                }
                assertEquals(
                        PublicMarketDataOutboundErrorCategory.NONE,
                        result.errorCategory(),
                        () -> "manual public outbound smoke did not pass endpointCategory="
                                + endpoint.category()
                                + ", errorCategory="
                                + result.errorCategory());
            }
        });

        assertEquals(SMOKE_ENDPOINTS.size(), evidence.size(), "manual smoke must produce one redacted summary per endpoint");
    }

    private ApplicationContextRunner manualContextRunner(ManualGate gate) {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        PublicMarketDataOutboundConfiguration.class,
                        EnvSafetyGuardConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(PROFILE_NAME))
                .withPropertyValues(
                        "nq.public-marketdata.outbound.enabled=true",
                        "nq.public-marketdata.outbound.base-url=" + REVIEWED_PROVIDER.baseUrl(),
                        "nq.env-safety.live-enabled=" + gate.liveEnabled(),
                        "nq.env-safety.ai-enabled=" + gate.aiEnabled(),
                        "nq.env-safety.dh-runtime-enabled=" + gate.dhRuntimeEnabled(),
                        "nq.env-safety.real-provider-enabled=" + gate.realProviderEnabled(),
                        "nq.env-safety.real-client-enabled=" + gate.realClientEnabled(),
                        "nq.env-safety.real-exchange-enabled=" + gate.realExchangeEnabled());
    }

    private void assertEndpointBindingIsPolicyBacked() {
        for (SmokeEndpoint endpoint : SMOKE_ENDPOINTS) {
            assertTrue(
                    RUNNER_ALLOWED_CATEGORIES.contains(endpoint.category()),
                    () -> "runner endpoint category is not in O-5B allowlist: " + endpoint.category());
            PublicMarketDataOutboundDecision decision = policy.evaluate(endpoint.toRequest("policy-check"));
            assertTrue(
                    decision.allowed(),
                    () -> "reviewed O-5B endpoint must remain allowed by O-1 policy: "
                            + endpoint.category());
        }
        for (PublicMarketDataEndpointCategory category : EXPLICITLY_REJECTED_CATEGORIES) {
            PublicMarketDataOutboundDecision decision = policy.evaluate(
                    PublicMarketDataOutboundRequest.publicGet(
                            REVIEWED_PROVIDER.name(),
                            category,
                            "/api/v5/private/order?apiKey=forbidden"));
            assertFalse(
                    decision.allowed(),
                    () -> "O-5B runner must not broaden O-1 denied category: " + category);
        }
    }

    private static void assertRedactedSummary(SmokeEvidence summary) {
        String line = summary.toSummaryLine();
        String lower = line.toLowerCase(Locale.ROOT);
        List<String> forbiddenFragments = List.of(
                "http://",
                "https://",
                "?",
                "apikey=",
                "api_key=",
                "secret=",
                "passphrase=",
                "token=",
                "signature=",
                "cookie=",
                "rawresponse",
                "rawheaders",
                "fullurl",
                "fullquery");
        for (String fragment : forbiddenFragments) {
            assertFalse(
                    lower.contains(fragment),
                    () -> "manual smoke summary must stay redacted; forbidden fragment=" + fragment);
        }
    }

    private static boolean flagEnabled(String envName) {
        return "true".equalsIgnoreCase(System.getenv(envName));
    }

    private static boolean featureFlagEnabled() {
        return "true".equalsIgnoreCase(System.getenv(FEATURE_FLAG));
    }

    private static boolean unsafeEnabled(String envName, String propertyName) {
        return "true".equalsIgnoreCase(System.getenv(envName))
                || "true".equalsIgnoreCase(System.getProperty(envName))
                || "true".equalsIgnoreCase(System.getProperty(propertyName));
    }

    private static Set<String> activeProfiles() {
        return Arrays.stream(value("SPRING_PROFILES_ACTIVE", "spring.profiles.active").split(","))
                .map(String::trim)
                .filter(profile -> !profile.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String value(String envName, String propertyName) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return Objects.toString(System.getProperty(propertyName), "");
    }

    private static Map<String, String> credentialEnvToProperty() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("NQ_OKX_API_KEY", "nq.okx.api-key");
        values.put("NQ_OKX_API_SECRET", "nq.okx.api-secret");
        values.put("NQ_OKX_API_PASSPHRASE", "nq.okx.api-passphrase");
        values.put("NQ_OKX_DOME_API_KEY", "nq.okx.dome.api-key");
        values.put("NQ_OKX_DOME_API_SECRET", "nq.okx.dome.api-secret");
        values.put("NQ_OKX_DOME_API_PASSPHRASE", "nq.okx.dome.api-passphrase");
        values.put("NQ_OKX_REAL_API_KEY", "nq.okx.real.api-key");
        values.put("NQ_OKX_REAL_API_SECRET", "nq.okx.real.api-secret");
        values.put("NQ_OKX_REAL_API_PASSPHRASE", "nq.okx.real.api-passphrase");
        values.put("NQ_BINANCE_DOME_API_KEY", "nq.binance.dome.api-key");
        values.put("NQ_BINANCE_DOME_API_SECRET", "nq.binance.dome.api-secret");
        values.put("NQ_BINANCE_DOME_PRIVATE_KEY", "nq.binance.dome.private-key");
        values.put("NQ_BINANCE_DOME_PRIVATE_KEY_PATH", "nq.binance.dome.private-key-path");
        values.put("NQ_BINANCE_REAL_API_KEY", "nq.binance.real.api-key");
        values.put("NQ_BINANCE_REAL_API_SECRET", "nq.binance.real.api-secret");
        values.put("NQ_BINANCE_REAL_PRIVATE_KEY", "nq.binance.real.private-key");
        values.put("NQ_BINANCE_REAL_PRIVATE_KEY_PATH", "nq.binance.real.private-key-path");
        values.put("NQ_PUBLIC_MARKETDATA_API_KEY", "nq.public-marketdata.api-key");
        values.put("NQ_PUBLIC_MARKETDATA_API_SECRET", "nq.public-marketdata.api-secret");
        values.put("NQ_PUBLIC_MARKETDATA_PASSPHRASE", "nq.public-marketdata.passphrase");
        values.put("NQ_PUBLIC_MARKETDATA_TOKEN", "nq.public-marketdata.token");
        return Map.copyOf(values);
    }

    private enum Provider {
        OKX("https://www.okx.com");

        private final String baseUrl;

        Provider(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        String baseUrl() {
            return baseUrl;
        }
    }

    /**
     * SmokeEndpoint 把 O-5B 允许的 category 固定映射到 reviewed OKX public REST path。
     *
     * <p>Why: runner 不接收 raw URL、path 或 query 参数，避免人工执行时绕过 O-1 policy 或把 private
     * endpoint 伪装成 public ticker。path 可包含固定 query，但 evidence 不允许输出该 path。</p>
     */
    private record SmokeEndpoint(
            PublicMarketDataEndpointCategory category,
            String instrument,
            String endpointPath
    ) {
        PublicMarketDataOutboundRequest toRequest(String runId) {
            return new PublicMarketDataOutboundRequest(
                    REVIEWED_PROVIDER.name(),
                    category,
                    endpointPath,
                    false,
                    false,
                    runId,
                    runId + "-" + category.name().toLowerCase(Locale.ROOT),
                    instrument == null ? null : "instrument=" + instrument);
        }
    }

    /**
     * ManualGate 只记录脱敏 gate 事实，并在 HTTP 前统一 fail-closed。
     *
     * <p>Why: 手动 runner 不能依赖外部说明文档来保证安全；profile、feature flag、LIVE/AI/DH/real
     * provider 和 credential absence 必须在代码里集中判断。错误只输出变量名或 gate 名称，不输出值。</p>
     */
    private record ManualGate(
            boolean manualFlagEnabled,
            boolean manualProfileActive,
            boolean featureFlagConfirmed,
            boolean liveEnabled,
            boolean aiEnabled,
            boolean dhRuntimeEnabled,
            boolean realProviderEnabled,
            boolean realClientEnabled,
            boolean realExchangeEnabled,
            List<String> configuredCredentialNames
    ) {
        static ManualGate fromProcess() {
            return new ManualGate(
                    flagEnabled(MANUAL_ENV_FLAG),
                    activeProfiles().contains(PROFILE_NAME),
                    featureFlagEnabled(),
                    unsafeEnabled("NQ_LIVE_ENABLED", "nq.env-safety.live-enabled"),
                    unsafeEnabled("NQ_AI_ENABLED", "nq.env-safety.ai-enabled"),
                    unsafeEnabled("NQ_DH_RUNTIME_ENABLED", "nq.env-safety.dh-runtime-enabled"),
                    unsafeEnabled("NQ_REAL_PROVIDER_ENABLED", "nq.env-safety.real-provider-enabled"),
                    unsafeEnabled("NQ_REAL_CLIENT_ENABLED", "nq.env-safety.real-client-enabled"),
                    unsafeEnabled("NQ_REAL_EXCHANGE_ENABLED", "nq.env-safety.real-exchange-enabled"),
                    findConfiguredCredentialNames());
        }

        void assertSafeBeforeHttp() {
            List<String> violations = new ArrayList<>();
            if (!manualFlagEnabled) {
                violations.add(MANUAL_ENV_FLAG + " must be true");
            }
            if (!manualProfileActive) {
                violations.add("spring profile must include " + PROFILE_NAME);
            }
            if (!featureFlagConfirmed) {
                violations.add(FEATURE_FLAG + " must be true");
            }
            if (liveEnabled) {
                violations.add("LIVE must be disabled");
            }
            if (aiEnabled) {
                violations.add("AI runtime must be disabled");
            }
            if (dhRuntimeEnabled) {
                violations.add("DH runtime must be disabled");
            }
            if (realProviderEnabled || realClientEnabled || realExchangeEnabled) {
                violations.add("real provider/client/exchange must be disabled");
            }
            if (!configuredCredentialNames.isEmpty()) {
                violations.add("credential-like configuration present: " + String.join(",", configuredCredentialNames));
            }
            if (!violations.isEmpty()) {
                fail("O-5B manual public outbound smoke blocked before HTTP: " + String.join("; ", violations));
            }
        }

        private static List<String> findConfiguredCredentialNames() {
            return CREDENTIAL_ENV_TO_PROPERTY.entrySet().stream()
                    .filter(entry -> hasConfiguredValue(entry.getKey(), entry.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }

        private static boolean hasConfiguredValue(String envName, String propertyName) {
            String envValue = System.getenv(envName);
            String propertyValue = System.getProperty(propertyName);
            String envPropertyValue = System.getProperty(envName);
            return (envValue != null && !envValue.isBlank())
                    || (propertyValue != null && !propertyValue.isBlank())
                    || (envPropertyValue != null && !envPropertyValue.isBlank());
        }
    }

    /**
     * SmokeEvidence 是允许输出到 surefire/stdout 的脱敏摘要。
     *
     * <p>Why: O-5B 后续 review 只需要 run id、时间、provider、category、HTTP status、latency 和安全
     * gate 事实；不能输出 raw URL、query、headers、response body、credential 或 provider raw payload。</p>
     */
    private record SmokeEvidence(
            String runId,
            Instant startedAt,
            Instant finishedAt,
            String provider,
            PublicMarketDataEndpointCategory endpointCategory,
            String instrument,
            int httpStatus,
            long latencyMs,
            String resultStatus,
            PublicMarketDataOutboundErrorCategory errorCategory,
            String redactedError,
            boolean noCredentialUsed,
            boolean noSignedRequest,
            boolean noPrivateEndpoint,
            boolean noTradingSideEffect,
            boolean liveDisabled,
            boolean aiDisabled,
            boolean dhRuntimeNotIntegrated
    ) {
        static SmokeEvidence from(
                String runId,
                Instant startedAt,
                Instant finishedAt,
                SmokeEndpoint endpoint,
                PublicMarketDataOutboundResult result,
                ManualGate gate
        ) {
            return new SmokeEvidence(
                    runId,
                    startedAt,
                    finishedAt,
                    REVIEWED_PROVIDER.name(),
                    endpoint.category(),
                    endpoint.instrument(),
                    result.statusCode(),
                    result.latency().toMillis(),
                    result.errorCategory() == PublicMarketDataOutboundErrorCategory.NONE ? "SUCCESS" : "FAILED",
                    result.errorCategory(),
                    result.errorCategory() == PublicMarketDataOutboundErrorCategory.NONE ? "NONE" : result.message(),
                    gate.configuredCredentialNames().isEmpty(),
                    true,
                    true,
                    true,
                    !gate.liveEnabled(),
                    !gate.aiEnabled(),
                    !gate.dhRuntimeEnabled());
        }

        String toSummaryLine() {
            return "taskName=" + TASK_NAME
                    + " | runId=" + runId
                    + " | startedAt=" + startedAt
                    + " | finishedAt=" + finishedAt
                    + " | provider=" + provider
                    + " | endpointCategory=" + endpointCategory
                    + " | instrument=" + (instrument == null ? "NONE" : instrument)
                    + " | httpStatus=" + httpStatus
                    + " | latencyMs=" + latencyMs
                    + " | resultStatus=" + resultStatus
                    + " | errorCategory=" + errorCategory
                    + " | redactedError=" + redactedError
                    + " | noCredentialUsed=" + noCredentialUsed
                    + " | noSignedRequest=" + noSignedRequest
                    + " | noPrivateEndpoint=" + noPrivateEndpoint
                    + " | noTradingSideEffect=" + noTradingSideEffect
                    + " | liveDisabled=" + liveDisabled
                    + " | aiDisabled=" + aiDisabled
                    + " | dhRuntimeNotIntegrated=" + dhRuntimeNotIntegrated;
        }
    }
}
