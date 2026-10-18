package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.research.application.paper.command.PaperAlertOperationCommand;

import org.junit.jupiter.api.Test;

/** 身份必须覆盖完整输入，不能以编码替换、空值或字段拼接绕过原请求冲突。 */
class PaperAlertOperationCommandTest {
    private PaperAlertOperationCommand create(String title, String message, String source) {
        return new PaperAlertOperationCommand(
                PaperAlertOperationCommand.Type.CREATE,
                null,
                "SYSTEM_NOTICE",
                "LOW",
                title,
                message,
                source,
                null);
    }

    @Test
    void fullInputActorAndParentHaveDistinctFingerprints() {
        var command = create("abc", "def", "MANUAL");
        assertEquals(command.fingerprint(7, "ptr-one"), command.fingerprint(7, "ptr-one"));
        assertNotEquals(command.fingerprint(7, "ptr-one"), command.fingerprint(8, "ptr-one"));
        assertNotEquals(command.fingerprint(7, "ptr-one"), command.fingerprint(7, "ptr-two"));
        assertNotEquals(
                command.fingerprint(7, "ptr-one"),
                create("ab", "cdef", "MANUAL").fingerprint(7, "ptr-one"));
    }

    @Test
    void nullEmptyAndUnicodeAreNotCollapsed() {
        assertNotEquals(
                create("提示", null, null).fingerprint(7, "ptr-one"),
                create("提示", "", null).fingerprint(7, "ptr-one"));
        assertThrows(IllegalArgumentException.class, () -> create("\ud800", null, null).validate());
        assertThrows(IllegalArgumentException.class, () -> create("\udc00", null, null).validate());
        create("提示\ud83d\ude80", null, null).validate();
    }

    @Test
    void boundsAndStateOperationFieldsAreValidatedBeforeMutation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> create("x".repeat(256), null, null).validate());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PaperAlertOperationCommand(
                                        PaperAlertOperationCommand.Type.ACK,
                                        "alt-one",
                                        null,
                                        null,
                                        "not allowed",
                                        null,
                                        null,
                                        null)
                                .validate());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PaperAlertOperationCommand(
                                        PaperAlertOperationCommand.Type.RESOLVE,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)
                                .validate());
    }

    @Test
    void invalidSnapshotAndDatabaseNulAreRejectedBeforeMutation() {
        assertThrows(
                IllegalArgumentException.class, () -> create("bad\0title", null, null).validate());
        for (String snapshot : new String[] {"", "{", "{} {}"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new PaperAlertOperationCommand(
                                            PaperAlertOperationCommand.Type.CREATE,
                                            null,
                                            "SYSTEM_NOTICE",
                                            "LOW",
                                            "notice",
                                            null,
                                            null,
                                            snapshot)
                                    .validate());
        }
        new PaperAlertOperationCommand(
                        PaperAlertOperationCommand.Type.CREATE,
                        null,
                        "SYSTEM_NOTICE",
                        "LOW",
                        "notice",
                        null,
                        null,
                        "[1,2]")
                .validate();
    }
}
