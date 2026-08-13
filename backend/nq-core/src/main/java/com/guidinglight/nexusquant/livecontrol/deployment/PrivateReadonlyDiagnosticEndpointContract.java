package com.guidinglight.nexusquant.livecontrol.deployment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/**
 * Private read-only diagnostic capability 的稳定 operation-name contract。
 *
 * <p>本类不定义 provider method/path，也不替代唯一 {@code OkxSpotEndpointGuard}；它只定义 deployment
 * admission 可接受的诊断 operation 子集，使 infra evidence factory 与 core admission 不再各自复制名单。</p>
 */
public final class PrivateReadonlyDiagnosticEndpointContract {

    private static final Set<String> OPERATIONS = Set.of(
            "OKX_ACCOUNT_CONFIGURATION_READ",
            "OKX_ACCOUNT_BALANCE_READ"
    );
    private static final String POLICY_DIGEST = digest(OPERATIONS);

    private PrivateReadonlyDiagnosticEndpointContract() {
    }

    public static Set<String> allowedOperations() {
        return OPERATIONS;
    }

    public static String policyDigest() {
        return POLICY_DIGEST;
    }

    public static boolean matches(Set<String> operations, String digest) {
        return OPERATIONS.equals(operations)
                && MessageDigest.isEqual(
                POLICY_DIGEST.getBytes(StandardCharsets.US_ASCII),
                digest == null ? new byte[0] : digest.getBytes(StandardCharsets.US_ASCII));
    }

    private static String digest(Set<String> operations) {
        String canonical = String.join("\n", new TreeSet<>(operations));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
