package com.guidinglight.nexusquant.app.config.livecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeAuthorizationCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlResult;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class ExactPilotScopeCliConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void closedInputInvokesSinglePurposeControlPlaneAndClosesContext() throws Exception {
        ObjectMapper mapper = mapper();
        ExactPilotScopeCliInput input = input();
        Path path = tempDirectory.resolve("exact-scope.json");
        Files.write(path, mapper.writeValueAsBytes(input));
        ExactPilotScopeControlPlane controlPlane = mock(ExactPilotScopeControlPlane.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(controlPlane.materializeAndBind(any(), any(), any())).thenReturn(result(input));
        var runner = new ExactPilotScopeCliConfiguration().exactPilotScopeCliRunner(
                controlPlane, mapper, context, path.toString());

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(controlPlane).materializeAndBind(any(), any(), any());
        verify(context).close();
    }

    @Test
    void unknownOrSecretShapedFieldIsRejectedBeforeControlPlane() throws Exception {
        ObjectMapper mapper = mapper();
        String json = mapper.writeValueAsString(input());
        String tampered = json.substring(0, json.length() - 1) + ",\"apiKey\":\"forbidden\"}";
        Path path = tempDirectory.resolve("tampered.json");
        Files.writeString(path, tampered);
        ExactPilotScopeControlPlane controlPlane = mock(ExactPilotScopeControlPlane.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        var runner = new ExactPilotScopeCliConfiguration().exactPilotScopeCliRunner(
                controlPlane, mapper, context, path.toString());

        assertThrows(Exception.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));
        verify(context).close();
    }

    @Test
    void inputRequiresIndependentPrincipalsAndLimitOrder() {
        ExactPilotScopeCliInput value = input();
        assertThrows(IllegalArgumentException.class, () -> new ExactPilotScopeCliInput(
                value.creatorPrincipal(), value.creatorPrincipal(), value.pilotScope(),
                value.pilotApproval(), value.binding(), value.exactScopeApproval()));
        assertEquals(ExactPilotBinding.OrderType.LIMIT, value.binding().orderType());
        assertEquals(ExactPilotScopeAuthorizationCommand.REQUIRED_REASON,
                value.exactScopeApproval().reason());
    }

    private static ExactPilotScopeControlResult result(ExactPilotScopeCliInput input) {
        return new ExactPilotScopeControlResult(
                input.pilotScope().sessionId(), input.pilotScope().pilotScopeId(), UUID.randomUUID(),
                input.pilotScope().expectedPilotScopeHash(), "c".repeat(64), input.binding().bindingId(),
                "d".repeat(64), ExactPilotBinding.Lifecycle.VERIFIED, false, false, false);
    }

    private static ExactPilotScopeCliInput input() {
        UUID sessionId = UUID.randomUUID();
        UUID pilotScopeId = UUID.randomUUID();
        Instant start = NOW.minusSeconds(60);
        Instant end = NOW.plusSeconds(600);
        ExactPilotScopeCliInput.RiskInput risk = new ExactPilotScopeCliInput.RiskInput(
                UUID.randomUUID(), "a".repeat(64), 1, decimal("25"), decimal("20"),
                decimal("25"), decimal("5"), decimal("10"), 1, 2, List.of("BTC-USDT"),
                900, decimal("10"), decimal("10"), 1_000, 9_000);
        ExactPilotScopeCliInput.PilotScopeInput scope = new ExactPilotScopeCliInput.PilotScopeInput(
                sessionId, pilotScopeId, 21L, 31L, "release-record", "b".repeat(64), 1,
                risk, List.of("BTC-USDT"), decimal("25"), start, end, "c".repeat(64),
                correlation("pilot"));
        ExactPilotScopeCliInput.PilotApprovalInput approval =
                new ExactPilotScopeCliInput.PilotApprovalInput(
                        UUID.randomUUID(), pilotScopeId, "c".repeat(64), "exact scope reviewed",
                        NOW.minusSeconds(1), NOW.plusSeconds(300));
        ExactPilotScopeCliInput.BindingInput binding = new ExactPilotScopeCliInput.BindingInput(
                UUID.randomUUID(), 101L, "BTC-USDT", ExactPilotBinding.Side.BUY,
                ExactPilotBinding.OrderType.LIMIT, decimal("100"), decimal("0.1"), decimal("10"),
                start, end, correlation("binding"), NOW.plusSeconds(300));
        ExactPilotScopeCliInput.ScopeApprovalInput exactApproval =
                new ExactPilotScopeCliInput.ScopeApprovalInput(
                        correlation("creator"), correlation("approver"),
                        ExactPilotScopeAuthorizationCommand.REQUIRED_REASON,
                        NOW.minusSeconds(1), NOW.plusSeconds(300));
        return new ExactPilotScopeCliInput(11L, 22L, scope, approval, binding, exactApproval);
    }

    private static ExactPilotBinding.Correlation correlation(String suffix) {
        return new ExactPilotBinding.Correlation(
                "request-" + suffix, "trace-" + suffix, "idempotency-" + suffix);
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }
}
