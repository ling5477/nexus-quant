package com.guidinglight.nexusquant.integration.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DhDryRunNoSideEffectBoundaryTest 验证 client 不触碰交易、provider、HTTP client 或敏感日志边界。
 */
class DhDryRunNoSideEffectBoundaryTest {

    @Test
    void recordOnlyClientDoesNotCallMutationBoundaries() {
        SideEffectProbe probe = new SideEffectProbe();
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

        assertEquals(1, transport.callCount());
        assertEquals(1, recorder.records().size());
        assertEquals(result.record(), recorder.records().get(0));
        assertNoSideEffects(probe);
    }

    @Test
    void productionPackageDoesNotIntroduceRealHttpClientApis() throws IOException {
        Path packageRoot = Path.of("src/main/java/com/guidinglight/nexusquant/integration/dh");
        try (Stream<Path> files = Files.walk(packageRoot)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("WebClient"), file.toString());
                assertFalse(source.contains("RestTemplate"), file.toString());
                assertFalse(source.contains("OkHttp"), file.toString());
                assertFalse(source.contains("java.net.http.HttpClient"), file.toString());
            }
        }
    }

    @Test
    void recordsDoNotContainCredentialOrExecutablePayload() {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

        String recordText = result.record().toString().toLowerCase(Locale.ROOT);
        for (String token : new String[] {
            "signing", "apikey", "api_secret", "apisecret", "passphrase", "credential", "cookie", "token",
            "executableorder", "quantity", "leverage", "orderprice"
        }) {
            assertFalse(recordText.contains(token), token);
        }
        assertFalse(recordText.contains("buy"));
        assertFalse(recordText.contains("sell"));
    }

    private static void assertNoSideEffects(SideEffectProbe probe) {
        assertEquals(0, probe.orderMutationCalls);
        assertEquals(0, probe.executionCalls);
        assertEquals(0, probe.riskMutationCalls);
        assertEquals(0, probe.ledgerMutationCalls);
        assertEquals(0, probe.paperRunStartCalls);
        assertEquals(0, probe.liveRunStartCalls);
        assertEquals(0, probe.exchangeAdapterCalls);
        assertEquals(0, probe.providerCalls);
    }

    /**
     * SideEffectProbe 只用于证明 client 未被注入任何 mutation/provider 边界。
     */
    private static final class SideEffectProbe {
        private int orderMutationCalls;
        private int executionCalls;
        private int riskMutationCalls;
        private int ledgerMutationCalls;
        private int paperRunStartCalls;
        private int liveRunStartCalls;
        private int exchangeAdapterCalls;
        private int providerCalls;
    }
}
