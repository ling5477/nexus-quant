package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotScopeAuthorization;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcExactPilotScopeAuthorizationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00.000000Z");
    private static final long CREATOR = 11L;
    private static final long APPROVER = 22L;

    private final List<LiveSessionEvent> events = new ArrayList<>();
    private JdbcExactPilotScopeAuthorizationRepository repository;
    private ExactPilotBinding.AuthoritativeFacts facts;
    private ExactPilotBindingCommand command;
    private ExactPilotScopeAuthorization authorization;
    private LiveSession session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LiveControlRepository liveRepository = mock(LiveControlRepository.class);
        facts = facts();
        command = command(facts);
        authorization = ExactPilotScopeAuthorization.approved(facts, command, CREATOR, APPROVER);
        session = LiveSession.create(
                facts.sessionId(), CREATOR, facts.account().exchangeAccountId(), "release-record",
                "c".repeat(64), 1, facts.riskPolicy().riskLimitSetId(), facts.riskPolicy().riskPolicyDigest(),
                facts.account().credentialReferenceId(), List.of("BTC-USDT"), decimal("25"),
                facts.pilotWindowStart(), facts.pilotWindowEnd(), CREATOR, NOW.minusSeconds(60));
        when(liveRepository.appendSessionEvent(any(LiveSessionEvent.class))).thenAnswer(invocation -> {
            LiveSessionEvent event = invocation.getArgument(0);
            events.add(event);
            return event;
        });
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    List<Object> values = new ArrayList<>();
                    for (int index = 0; index < events.size(); index++) {
                        values.add(mapper.mapRow(resultSet(events.get(index)), index));
                    }
                    return values;
                });
        repository = new JdbcExactPilotScopeAuthorizationRepository(
                jdbc, new ObjectMapper(), liveRepository);
    }

    @Test
    void appendsCreatorThenIndependentApprovalAndAcceptsExactCurrentFacts() {
        repository.recordApproved(
                authorization, session, correlation("creator"), correlation("approver"),
                NOW.minusSeconds(1), NOW.plusSeconds(120));

        assertEquals(2, events.size());
        assertEquals(JdbcExactPilotScopeAuthorizationRepository.AUTHORIZE_COMMAND, events.get(0).command());
        assertEquals(CREATOR, events.get(0).actorId());
        assertEquals(JdbcExactPilotScopeAuthorizationRepository.APPROVE_COMMAND, events.get(1).command());
        assertEquals(APPROVER, events.get(1).actorId());
        repository.requireApproved(CREATOR, command, facts, NOW);
    }

    @Test
    void changedOrderOrExpiredApprovalCannotAuthorizeBinding() {
        repository.recordApproved(
                authorization, session, correlation("creator"), correlation("approver"),
                NOW.minusSeconds(1), NOW.plusSeconds(120));
        ExactPilotBinding.OrderEnvelope changedOrder = new ExactPilotBinding.OrderEnvelope(
                101L, "BTC-USDT", ExactPilotBinding.Side.SELL, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("10"));
        ExactPilotBindingCommand changedCommand = new ExactPilotBindingCommand(
                command.bindingId(), command.sessionId(), command.pilotScopeId(), command.observationSetId(),
                changedOrder, command.pilotWindowStart(), command.pilotWindowEnd(), command.correlation(),
                command.bindingExpiresAt());
        ExactPilotBinding.AuthoritativeFacts changedFacts = new ExactPilotBinding.AuthoritativeFacts(
                facts.sessionId(), facts.pilotScopeId(), facts.observationSetId(), facts.deployment(),
                facts.account(), changedOrder, facts.observations(), facts.riskPolicy(),
                facts.pilotWindowStart(), facts.pilotWindowEnd());

        assertRejected(() -> repository.requireApproved(CREATOR, changedCommand, changedFacts, NOW));
        assertRejected(() -> repository.requireApproved(
                CREATOR, command, facts, NOW.plusSeconds(120)));
    }

    @Test
    void tamperedCanonicalMetadataFailsClosed() {
        repository.recordApproved(
                authorization, session, correlation("creator"), correlation("approver"),
                NOW.minusSeconds(1), NOW.plusSeconds(120));
        LiveSessionEvent first = events.getFirst();
        events.set(0, new LiveSessionEvent(
                first.id(), first.sessionId(), first.sequence(), first.fromState(), first.toState(),
                first.command(), first.actorId(), first.requestId(), first.traceId(), first.reasonCode(),
                first.idempotencyKey(), first.commandPayloadHash(),
                first.metadataJson().replace("BTC-USDT", "ETH-USDT"), first.createdAt()));

        assertRejected(() -> repository.requireApproved(CREATOR, command, facts, NOW));
    }

    private static ResultSet resultSet(LiveSessionEvent event) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("command")).thenReturn(event.command());
        when(row.getLong("actor_id")).thenReturn(event.actorId());
        when(row.getString("request_id")).thenReturn(event.requestId());
        when(row.getString("trace_id")).thenReturn(event.traceId());
        when(row.getString("reason_code")).thenReturn(event.reasonCode());
        when(row.getString("idempotency_key")).thenReturn(event.idempotencyKey());
        when(row.getString("command_payload_hash")).thenReturn(event.commandPayloadHash());
        when(row.getString("metadata")).thenReturn(event.metadataJson());
        when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(event.createdAt()));
        return row;
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable executable) {
        LiveControlException exception = assertThrows(LiveControlException.class, executable);
        assertEquals("EXACT_PILOT_SCOPE_APPROVAL_REJECTED", exception.code());
    }

    private static ExactPilotBinding.AuthoritativeFacts facts() {
        ExactPilotBinding.OrderEnvelope order = new ExactPilotBinding.OrderEnvelope(
                101L, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("10"));
        return new ExactPilotBinding.AuthoritativeFacts(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ExactPilotBinding.DeploymentIdentity(
                        "1".repeat(40), "1".repeat(40), "a".repeat(64), "server-a",
                        ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE),
                new ExactPilotBinding.AccountIdentity("OKX", "LIVE", CREATOR, 21L, 31L), order,
                new ExactPilotBinding.ObservationIdentities(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                new ExactPilotBinding.RiskPolicyIdentity(
                        UUID.randomUUID(), 1, "b".repeat(64), "ENGAGED"),
                NOW.minusSeconds(60), NOW.plusSeconds(600));
    }

    private static ExactPilotBindingCommand command(ExactPilotBinding.AuthoritativeFacts facts) {
        return new ExactPilotBindingCommand(
                UUID.randomUUID(), facts.sessionId(), facts.pilotScopeId(), facts.observationSetId(),
                facts.order(), facts.pilotWindowStart(), facts.pilotWindowEnd(), correlation("binding"),
                NOW.plusSeconds(300));
    }

    private static ExactPilotBinding.Correlation correlation(String suffix) {
        return new ExactPilotBinding.Correlation(
                "request-" + suffix, "trace-" + suffix, "idempotency-" + suffix);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }
}
