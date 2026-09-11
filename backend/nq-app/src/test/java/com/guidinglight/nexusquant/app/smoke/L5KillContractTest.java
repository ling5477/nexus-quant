package com.guidinglight.nexusquant.app.smoke;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 专用入口只增加 ENGAGE，不打开解除、故障或任意控制命令。 */
class L5KillContractTest {
    @Test void killEntryHasNoReleaseOrFaultControl() {
        boolean before = L5QualificationControls.killTransition;
        try {
            L5QualificationControls.killTransition = false;
            assertThrows(IllegalArgumentException.class, () -> L5QualificationControls.validateCommand("L5_KILL_ENGAGE"));
            L5QualificationControls.killTransition = true;
            assertDoesNotThrow(() -> L5QualificationControls.validateCommand("L5_KILL_ENGAGE"));
            for (String command : List.of("ENGAGE", "DISENGAGE", "L5_KILL_DISENGAGE", "L5_TARGET_ARM",
                    "ARM_B4_TX ACK WIRE", "L5C_PAUSE", "L5_KILL_ENGAGE\nDISENGAGE")) {
                assertThrows(IllegalArgumentException.class, () -> L5QualificationControls.validateCommand(command));
            }
        } finally { L5QualificationControls.killTransition = before; }
    }
}
