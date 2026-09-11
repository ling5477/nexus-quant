package com.guidinglight.nexusquant.app.smoke;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 拒绝进入旧故障控制与超出已接受身份范围的命令；不需要数据库即可验证协议边界。 */
class L5DriverContractTest {
    @Test void frozenIdentityAndCleanCommandsAreAccepted() {
        for (String command : List.of("L5_PLACE 1", "L5_PLACE 120", "L5_PLACE 240", "L5_SCAN",
                "L5_RECONCILE", "L5_PROJECT", "L5_OBSERVE", "L5C_BEGIN", "L5C_STOP", "L5C_STEP")) {
            assertDoesNotThrow(() -> L5QualificationControls.validateCommand(command));
        }
    }

    @Test void faultsMalformedAndOutOfBudgetCommandsAreRejected() {
        for (String command : List.of("L5_PLACE 0", "L5_PLACE 241", "L5_PLACE -1", "L5_PLACE 999999999999",
                "L5_PLACE 1\nENGAGE", "L5C_BEGIN 8", "L5C_KILL", "ENGAGE", "ARM_B4_TX TRADE WIRE", "BEGIN_PLACE_B2_LIVE", "STOP", "")) {
            assertThrows(IllegalArgumentException.class, () -> L5QualificationControls.validateCommand(command));
        }
        assertThrows(IllegalArgumentException.class, () -> L5QualificationControls.validateCommand(null));
    }
}
