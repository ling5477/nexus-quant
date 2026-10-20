package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.account.domain.AccountCommand;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * 验证命令字段边界及指纹对null、空串和分隔符的区分；非法输入在触及业务或持久化前拒绝。
 */
class AccountCommandInputTest {
    @Test
    void fingerprintSeparatesNullEmptyFieldsAndDelimiterBearingText() {
        var first = new AccountCommand(AccountCommand.Type.CREATE, null, "OKX", "SIM", "a:b", "c");
        var second = new AccountCommand(AccountCommand.Type.CREATE, null, "OKX", "SIM", "a", "b:c");
        assertNotEquals(
                AccountCommandService.fingerprint(first),
                AccountCommandService.fingerprint(second));
        var nullRef = new AccountCommand(AccountCommand.Type.CREATE, null, "OKX", "SIM", "a", null);
        var emptyRef = new AccountCommand(AccountCommand.Type.CREATE, null, "OKX", "SIM", "a", "");
        assertNotEquals(
                AccountCommandService.fingerprint(nullRef),
                AccountCommandService.fingerprint(emptyRef));
        assertEquals(
                AccountCommandService.fingerprint(first), AccountCommandService.fingerprint(first));
    }

    @Test
    void invalidFieldCombinationsAreRejectedBeforeTouchingRepositoryOrBusiness() {
        var service = new AccountCommandService(null, null);
        for (var request :
                new AccountCommand[] {
                    new AccountCommand(AccountCommand.Type.CREATE, 1L, "OKX", "SIM", "a", null),
                    new AccountCommand(AccountCommand.Type.UPDATE, 1L, "BINANCE", null, "a", null),
                    new AccountCommand(
                            AccountCommand.Type.DISABLE, 1L, null, null, "unexpected", null),
                    new AccountCommand(AccountCommand.Type.ENABLE, null, null, null, null, null),
                    new AccountCommand(AccountCommand.Type.CREATE, null, "OKX", "SIM", " ", null)
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.execute(1L, UUID.randomUUID(), request));
        }
    }
}
