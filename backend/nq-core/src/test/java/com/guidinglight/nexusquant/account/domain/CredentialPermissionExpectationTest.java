package com.guidinglight.nexusquant.account.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CredentialPermissionExpectationTest {
    @Test
    void stableModesPreservePermissionPolicy() {
        for (String value : new String[]{null, "", "PAPER", "READ_ONLY_DIAGNOSTIC"}) {
            assertEquals(CredentialPermissionExpectation.READ_ONLY_DIAGNOSTIC,
                    CredentialPermissionExpectation.fromRequestedMode(value));
        }
        assertEquals(CredentialPermissionExpectation.SCOPED_TRADE_READINESS,
                CredentialPermissionExpectation.fromRequestedMode(" scoped_trade_readiness "));
    }

    @Test
    void retiredAndUnknownModesAreRejected() {
        for (String value : new String[]{"LIVE", "UNKNOWN"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CredentialPermissionExpectation.fromRequestedMode(value));
        }
    }
}
