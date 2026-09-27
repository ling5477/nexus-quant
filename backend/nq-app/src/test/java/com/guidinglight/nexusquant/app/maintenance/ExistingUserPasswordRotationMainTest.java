package com.guidinglight.nexusquant.app.maintenance;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExistingUserPasswordRotationMainTest {
    @Test
    void missingActionAndExtraArgumentsCannotStartMaintenanceOrEchoInput() {
        for (String[] args : new String[][]{new String[0], {"synthetic-secret"},
                {"--execute-existing-user-password-rotation", "/safe/request.json", "synthetic-secret"}}) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            assertEquals(2, ExistingUserPasswordRotationMain.run(args, new PrintStream(buffer)));
            String result = buffer.toString(StandardCharsets.UTF_8);
            assertEquals("{\"status\":\"EXPLICIT_MAINTENANCE_ACTION_REQUIRED\"}" + System.lineSeparator(), result);
            assertFalse(result.contains("synthetic-secret"));
        }
    }

    @Test
    void unsafeFileFailureDoesNotExposePathOrCause() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        assertEquals(4, ExistingUserPasswordRotationMain.run(new String[]{
                "--execute-existing-user-password-rotation", "synthetic-secret-relative-path"}, new PrintStream(buffer)));
        assertEquals("{\"status\":\"MAINTENANCE_INPUT_OR_RUNTIME_FAILURE\"}" + System.lineSeparator(),
                buffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void secretFilesRequireRootRegularFileAndExactly0600() {
        PosixFileAttributes attributes = mock(PosixFileAttributes.class);
        when(attributes.owner()).thenReturn(() -> "root");
        when(attributes.isRegularFile()).thenReturn(true);
        when(attributes.permissions()).thenReturn(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        assertDoesNotThrow(() -> ProtectedMaintenanceFiles.validate(attributes, true));
        when(attributes.owner()).thenReturn(() -> "operator");
        assertThrows(Exception.class, () -> ProtectedMaintenanceFiles.validate(attributes, true));
        when(attributes.owner()).thenReturn(() -> "root");
        when(attributes.isSymbolicLink()).thenReturn(true);
        assertThrows(Exception.class, () -> ProtectedMaintenanceFiles.validate(attributes, true));
        when(attributes.isSymbolicLink()).thenReturn(false);
        when(attributes.permissions()).thenReturn(Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ));
        assertThrows(Exception.class, () -> ProtectedMaintenanceFiles.validate(attributes, true));
    }

    @Test
    void parentDirectoriesCannotBeWritableByOtherPrincipals() {
        PosixFileAttributes attributes = mock(PosixFileAttributes.class);
        when(attributes.owner()).thenReturn(() -> "root");
        when(attributes.isDirectory()).thenReturn(true);
        when(attributes.permissions()).thenReturn(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        assertDoesNotThrow(() -> ProtectedMaintenanceFiles.validate(attributes, false));
        when(attributes.permissions()).thenReturn(Set.of(PosixFilePermission.OTHERS_WRITE));
        assertThrows(Exception.class, () -> ProtectedMaintenanceFiles.validate(attributes, false));
    }

    @Test
    void requestRejectsExternalDatabaseAndPasswordInJdbcUrlOrDuplicateSource() {
        for (String url : new String[]{"jdbc:postgresql://example.test:5432/db",
                "jdbc:postgresql://127.0.0.1:5432/db?password=synthetic-secret"}) {
            var request = new ExistingUserPasswordRotationMain.RotationRequest(
                    url, "postgres", "/secure/database", "/secure/new-password", 2, "owner", "0".repeat(64));
            assertThrows(IllegalArgumentException.class, () -> request.validate(Path.of("/secure/request")));
        }
        var request = new ExistingUserPasswordRotationMain.RotationRequest(
                "jdbc:postgresql://127.0.0.1:5432/db", "postgres", "/secure/same", "/secure/same", 2, "owner", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> request.validate(Path.of("/secure/request")));
    }
}
