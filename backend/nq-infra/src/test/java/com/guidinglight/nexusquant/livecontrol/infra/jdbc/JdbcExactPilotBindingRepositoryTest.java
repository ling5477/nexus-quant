package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcExactPilotBindingRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-22T01:00:00.000000Z");

    @Test
    void persistsCanonicalBindingAndRejectsSecondConsumption() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LiveControlRepository liveRepository = mock(LiveControlRepository.class);
        List<String> createMetadata = new ArrayList<>();
        List<String> consumeMetadata = new ArrayList<>();
        List<LiveSessionEvent> events = new ArrayList<>();
        ExactPilotBinding binding = binding();
        LiveSession session = session(binding);

        when(jdbc.queryForList(anyString(), eq(String.class), any(), any()))
                .thenAnswer(invocation -> List.copyOf(createMetadata));
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any(), any()))
                .thenAnswer(invocation -> List.copyOf(createMetadata));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenAnswer(invocation -> consumeMetadata.size());
        when(liveRepository.appendSessionEvent(any(LiveSessionEvent.class))).thenAnswer(invocation -> {
            LiveSessionEvent event = invocation.getArgument(0);
            events.add(event);
            if (JdbcExactPilotBindingRepository.CREATE_COMMAND.equals(event.command())) {
                createMetadata.add(event.metadataJson());
            } else if (JdbcExactPilotBindingRepository.CONSUME_COMMAND.equals(event.command())) {
                consumeMetadata.add(event.metadataJson());
            }
            return event;
        });

        JdbcExactPilotBindingRepository repository = new JdbcExactPilotBindingRepository(
                jdbc, new ObjectMapper(), liveRepository);

        assertEquals(binding, repository.createOrGet(binding, session));
        assertEquals(binding, repository.find(binding.sessionId(), binding.id()).orElseThrow());
        assertEquals(1, events.size());
        assertTrue(events.getFirst().metadataJson().contains(binding.bindingDigest()));
        assertTrue(events.getFirst().metadataJson().contains("canonicalBinding"));
        assertFalse(events.getFirst().metadataJson().contains("tradingAuthorized\":true"));

        var consumed = repository.consume(binding, session, correlation("consume"), NOW.plusSeconds(1));
        assertFalse(consumed.tradingAuthorized());
        assertFalse(consumed.exchangeMutation());
        assertTrue(repository.isConsumed(binding.sessionId(), binding.id()));
        assertTrue(events.get(1).metadataJson().contains("tradingAuthorized\":false"));
        assertTrue(events.get(1).metadataJson().contains("exchangeMutation\":false"));

        LiveControlException second = assertThrows(LiveControlException.class,
                () -> repository.consume(binding, session, correlation("consume-2"), NOW.plusSeconds(2)));
        assertEquals("EXACT_PILOT_BINDING_ALREADY_CONSUMED", second.code());
    }

    @Test
    void rejectsTamperedStoredCanonicalPayload() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LiveControlRepository liveRepository = mock(LiveControlRepository.class);
        ExactPilotBinding binding = binding();
        String tampered = "{\"schemaVersion\":\"exact-pilot-binding-event.v1\"," +
                "\"lifecycle\":\"VERIFIED\",\"bindingId\":\"" + binding.id() + "\"," +
                "\"bindingDigest\":\"" + binding.bindingDigest() + "\"," +
                "\"canonicalBinding\":\"{}\"}";
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any(), any()))
                .thenReturn(List.of(tampered));
        JdbcExactPilotBindingRepository repository = new JdbcExactPilotBindingRepository(
                jdbc, new ObjectMapper(), liveRepository);

        LiveControlException exception = assertThrows(LiveControlException.class,
                () -> repository.find(binding.sessionId(), binding.id()));
        assertEquals("EXACT_PILOT_BINDING_FACT_CORRUPTED", exception.code());
    }

    private static ExactPilotBinding binding() {
        BigDecimal price = decimal("100");
        BigDecimal quantity = decimal("0.1");
        return ExactPilotBinding.verified(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ExactPilotBinding.DeploymentIdentity(
                        "1".repeat(40), "1".repeat(40), "a".repeat(64), "server-a",
                        ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE),
                new ExactPilotBinding.AccountIdentity("OKX", "LIVE", 11L, 21L, 31L),
                new ExactPilotBinding.OrderEnvelope(
                        101L, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                        price, quantity, price.multiply(quantity)),
                new ExactPilotBinding.ObservationIdentities(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                new ExactPilotBinding.RiskPolicyIdentity(
                        UUID.randomUUID(), 1, "b".repeat(64), "ENGAGED"),
                NOW.minusSeconds(60), NOW.plusSeconds(600), correlation("create"),
                NOW, NOW.plusSeconds(300));
    }

    private static LiveSession session(ExactPilotBinding binding) {
        return LiveSession.create(
                binding.sessionId(), binding.account().ownerId(), binding.account().exchangeAccountId(),
                "release-record", "c".repeat(64), 1, binding.riskPolicy().riskLimitSetId(),
                binding.riskPolicy().riskPolicyDigest(), binding.account().credentialReferenceId(),
                List.of(binding.order().exchangeInstrumentId()), decimal("25"), binding.pilotWindowStart(),
                binding.pilotWindowEnd(), binding.account().ownerId(), NOW.minusSeconds(120));
    }

    private static ExactPilotBinding.Correlation correlation(String suffix) {
        return new ExactPilotBinding.Correlation(
                "request-" + suffix, "trace-" + suffix, "idempotency-" + suffix);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }
}
