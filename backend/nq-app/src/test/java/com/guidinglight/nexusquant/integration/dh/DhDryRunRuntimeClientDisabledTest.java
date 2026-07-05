package com.guidinglight.nexusquant.integration.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DhDryRunRuntimeClientDisabledTest 覆盖 feature flag、kill switch、endpoint 和 production gate。
 */
class DhDryRunRuntimeClientDisabledTest {

    @Test
    void featureFlagDisabledDoesNotCallTransport() {
        DhDryRunRuntimeProperties properties = new DhDryRunRuntimeProperties(
                false,
                true,
                DhDryRunRuntimeProperties.DEFAULT_SOURCE,
                DhDryRunTestSupport.ENDPOINT,
                false,
                false,
                1500,
                DhDryRunTestSupport.SIGNING_KEY,
                DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION);

        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();
        DhDryRunClientResult result =
                DhDryRunTestSupport.client(properties, transport, recorder).execute(DhDryRunTestSupport.command());

        assertDisabledNoCall(result, transport, recorder, "runtime_disabled");
    }

    @Test
    void clientFlagDisabledDoesNotCallTransport() {
        DhDryRunRuntimeProperties properties = new DhDryRunRuntimeProperties(
                true,
                false,
                DhDryRunRuntimeProperties.DEFAULT_SOURCE,
                DhDryRunTestSupport.ENDPOINT,
                false,
                false,
                1500,
                DhDryRunTestSupport.SIGNING_KEY,
                DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION);

        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();
        DhDryRunClientResult result =
                DhDryRunTestSupport.client(properties, transport, recorder).execute(DhDryRunTestSupport.command());

        assertDisabledNoCall(result, transport, recorder, "client_disabled");
    }

    @Test
    void killSwitchEnabledDoesNotCallTransport() {
        DhDryRunRuntimeProperties properties = new DhDryRunRuntimeProperties(
                true,
                true,
                DhDryRunRuntimeProperties.DEFAULT_SOURCE,
                DhDryRunTestSupport.ENDPOINT,
                false,
                true,
                1500,
                DhDryRunTestSupport.SIGNING_KEY,
                DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION);

        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();
        DhDryRunClientResult result =
                DhDryRunTestSupport.client(properties, transport, recorder).execute(DhDryRunTestSupport.command());

        assertDisabledNoCall(result, transport, recorder, "kill_switch_enabled");
    }

    @Test
    void endpointMissingFailsClosedAndDoesNotCallTransport() {
        DhDryRunRuntimeProperties properties = new DhDryRunRuntimeProperties(
                true,
                true,
                DhDryRunRuntimeProperties.DEFAULT_SOURCE,
                "",
                false,
                false,
                1500,
                DhDryRunTestSupport.SIGNING_KEY,
                DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION);

        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();
        DhDryRunClientResult result =
                DhDryRunTestSupport.client(properties, transport, recorder).execute(DhDryRunTestSupport.command());

        assertDisabledNoCall(result, transport, recorder, "endpoint_url_missing");
    }

    @Test
    void productionProfileIsDisabledByDefault() {
        DhDryRunRuntimeProperties properties =
                DhDryRunRuntimeProperties.enabledForTest(DhDryRunTestSupport.ENDPOINT, DhDryRunTestSupport.SIGNING_KEY);
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.client(properties, transport, recorder).execute(DhDryRunTestSupport.command(), true);

        assertDisabledNoCall(result, transport, recorder, "production_disabled");
    }

    @Test
    void nonCanonicalSourceCaseAndAliasFailClosedWithoutCallingTransport() {
        assertSourceDeniedNoCall("nq_dryrun");
        assertSourceDeniedNoCall("NQ-DRYRUN");
    }

    private static void assertSourceDeniedNoCall(String source) {
        DhDryRunRuntimeProperties properties = new DhDryRunRuntimeProperties(
                true,
                true,
                source,
                DhDryRunTestSupport.ENDPOINT,
                false,
                false,
                1500,
                DhDryRunTestSupport.SIGNING_KEY,
                DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION);
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.client(properties, transport, recorder).execute(DhDryRunTestSupport.command());

        assertTrue(result.failClosed());
        assertEquals(DhDryRunErrorCode.SOURCE_DENIED, result.record().errorCode());
        assertEquals("source_denied", result.record().failClosedReason());
        assertEquals(0, transport.callCount());
        assertEquals(1, recorder.records().size());
    }

    private static void assertDisabledNoCall(
            DhDryRunClientResult result,
            DhDryRunTestSupport.FakeDhDryRunTransport transport,
            InMemoryDhDryRunRecorder recorder,
            String reason) {
        assertTrue(result.failClosed());
        assertEquals(DhDryRunErrorCode.CLIENT_DISABLED, result.record().errorCode());
        assertEquals(reason, result.record().failClosedReason());
        assertEquals(0, transport.callCount());
        assertEquals(1, recorder.records().size());
    }
}
