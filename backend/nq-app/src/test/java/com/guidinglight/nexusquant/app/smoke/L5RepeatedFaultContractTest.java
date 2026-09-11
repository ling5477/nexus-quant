package com.guidinglight.nexusquant.app.smoke;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 故障入口不能扩展普通L5、LIVE或任意旧控制命令的权限。 */
class L5RepeatedFaultContractTest {
    @Test void onlyDedicatedEntryEnablesExactControls() {
        boolean previous = L5QualificationControls.repeatedFault;
        try {
            L5QualificationControls.repeatedFault = false;
            assertFalse(L5QualificationControls.isRepeatedFaultCommand("ARM_B4_TRADE_COMMIT"));
            L5QualificationControls.repeatedFault = true;
            for (String command : List.of("ARM_B4_TRADE_COMMIT", "ARM_V51_B", "ARM_B4_TX ACK WIRE", "ARM_B4_TX LEDGER WIRE")) {
                assertTrue(L5QualificationControls.isRepeatedFaultCommand(command));
            }
            for (String command : List.of("ARM_B4_TX TRADE WIRE", "ARM_B4_TX ACK REJECT", "ENGAGE", "PLACE_B2_LIVE", "ARM_V51_B\nENGAGE", "")) {
                assertFalse(L5QualificationControls.isRepeatedFaultCommand(command));
            }
            assertFalse(L5QualificationControls.isRepeatedFaultCommand(null));
        } finally { L5QualificationControls.repeatedFault = previous; }
    }
}
