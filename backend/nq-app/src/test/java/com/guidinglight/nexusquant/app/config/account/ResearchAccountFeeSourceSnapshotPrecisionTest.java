package com.guidinglight.nexusquant.app.config.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.service.AccountFeeModelService;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.FrozenAccountFeeModel;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

class ResearchAccountFeeSourceSnapshotPrecisionTest {
    private static final Instant AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final String NUMBER = "9007199254740993.123456789012345678";
    private static final UUID OBSERVATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String RAW =
            "{\"costSource\":\"CURRENT_ACCOUNT_OBSERVATION\",\"feeObservationId\":\""
                    + OBSERVATION
                    + "\",\"feeAccountId\":\"1\",\"feeTradeEnv\":\"SIM\",\"feeSymbol\":\"BTC-USDT\",\"number\":"
                    + NUMBER
                    + ",\"slippageBps\":0.123456789012345678,\"nested\":[1.234567890123456789e-40,\"1.234567890123456789\",true,null]}";

    @Test
    void selectedFeePreparationAndComposedConfigurationCreationPreserveNumbers() throws Exception {
        var mapper = new ObjectMapper();
        var source = source(mapper);
        assertExact(exact(mapper, source.prepareConfiguration(RAW)));
        var research =
                new ResearchConfig(
                        "rcf",
                        "str",
                        "{}",
                        "research",
                        null,
                        "{}",
                        "{}",
                        "{}",
                        AT,
                        AT,
                        "ACTIVE",
                        null,
                        null,
                        null);
        var researchService = mock(ResearchConfigService.class);
        when(researchService.getByResearchConfigId("rcf")).thenReturn(research);
        var service =
                new BacktestConfigService(
                        mock(BacktestConfigRepository.class),
                        researchService,
                        mapper,
                        Clock.systemUTC());
        var factory = new StaticListableBeanFactory();
        factory.addBean("feeSource", source);
        var setter =
                BacktestConfigService.class.getDeclaredMethod(
                        "bindFeeSource", ObjectProvider.class);
        setter.setAccessible(true);
        setter.invoke(service, factory.getBeanProvider(ResearchFeeSourcePort.class));
        var configuration =
                service.create(
                        new BacktestConfigCreateRequest(
                                "rcf",
                                "precision",
                                null,
                                AT,
                                AT.plusSeconds(60),
                                new BigDecimal("100"),
                                RAW,
                                "{}"));
        assertExact(exact(mapper, configuration.executionSpec()));
        assertExact(exact(mapper, configuration.configSnapshotJson()).get("executionSpec"));
        assertExact(exact(mapper, configuration.configSnapshot()).get("executionSpec"));
        assertTrue(mapper.readTree("1.234567890123456789").isDouble());
    }

    @Test
    void runFreezingRetainsPrecisionAndKeepsTheCanonicalFeeDigestValid() throws Exception {
        var mapper = new ObjectMapper();
        var source = source(mapper);
        String prepared = source.prepareConfiguration(RAW);
        String config = "{\"executionSpec\":" + prepared + "}";
        String dataset =
                "{\"exchangeCode\":\"OKX\",\"marketType\":\"SPOT\",\"symbol\":\"BTC-USDT\"}";
        JsonNode frozen =
                exact(mapper, source.freezeRunConfiguration(config, dataset, AT))
                        .get("executionSpec");
        assertExact(frozen);
        JsonNode fee = exact(mapper, frozen.get("feeSourceSnapshotJson").asText());
        assertEquals(
                FrozenAccountFeeModel.digest(fee), frozen.get("feeSourceSnapshotSha256").asText());
        source.validateFrozenExecution(frozen.toString(), dataset);
        assertEquals("0.001000000000", frozen.get("feeRate").asText());
        assertEquals("17", frozen.get("feeSourceOwnerUserId").asText());
    }

    @Test
    void retainsInvalidPayloadServerFieldAndRateRejections() {
        var source = source(new ObjectMapper());
        for (String value :
                new String[] {
                    "{bad-json",
                    "x".repeat(32769),
                    "{\"feeSourceOwnerUserId\":\"17\"}",
                    RAW.replace("\"slippageBps\":", "\"feeRate\":\"0.1\",\"slippageBps\":")
                })
            assertThrows(IllegalArgumentException.class, () -> source.prepareConfiguration(value));
        String manual = "{ \"slippageBps\":0.123456789012345678 }";
        assertEquals(manual, source.prepareConfiguration(manual));
    }

    private static ResearchAccountFeeSource source(ObjectMapper mapper) {
        var quote =
                new AccountFeeQuote(
                        new BigDecimal("-0.001"), new BigDecimal("-0.001"), "synthetic", AT, AT);
        var observation =
                new AccountFeeObservation(
                        OBSERVATION,
                        UUID.fromString("22222222-2222-4222-8222-222222222222"),
                        17,
                        1,
                        1,
                        "OKX",
                        "SIM",
                        "BTC-USDT",
                        "a".repeat(64),
                        quote,
                        AT.plusSeconds(3600),
                        "b".repeat(64));
        var model = new FrozenAccountFeeModel(observation, AT);
        var models = mock(AccountFeeModelService.class);
        when(models.select(
                        eq(17L),
                        eq(OBSERVATION),
                        eq(1L),
                        anyString(),
                        eq("SIM"),
                        eq("BTC-USDT"),
                        any()))
                .thenReturn(model);
        when(models.historical(
                        eq(17L),
                        eq(OBSERVATION),
                        eq(1L),
                        anyString(),
                        eq("SIM"),
                        eq("BTC-USDT"),
                        any()))
                .thenReturn(model);
        // 只提供合成主体和费用事实，正式准备、冻结及校验逻辑原样执行，不连接网络或数据库。
        return new ResearchAccountFeeSource(models, null, null, mapper) {
            @Override
            public Long currentReadOwner() {
                return 17L;
            }
        };
    }

    private static JsonNode exact(ObjectMapper mapper, String raw) throws Exception {
        return mapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(raw);
    }

    private static void assertExact(JsonNode node) {
        assertEquals(0, new BigDecimal(NUMBER).compareTo(node.get("number").decimalValue()));
        assertEquals(
                0,
                new BigDecimal("0.123456789012345678")
                        .compareTo(node.get("slippageBps").decimalValue()));
        assertEquals(
                0,
                new BigDecimal("1.234567890123456789e-40")
                        .compareTo(node.get("nested").get(0).decimalValue()));
        assertEquals("1.234567890123456789", node.get("nested").get(1).textValue());
        assertTrue(node.get("nested").get(2).booleanValue());
        assertTrue(node.get("nested").get(3).isNull());
    }
}
