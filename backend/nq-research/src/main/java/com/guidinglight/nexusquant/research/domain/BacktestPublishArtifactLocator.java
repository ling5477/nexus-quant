package com.guidinglight.nexusquant.research.domain;

import java.util.regex.Pattern;

/**
 * publish pipeline 内部使用的 server-owned artifact locator pair。
 *
 * <p>storage key 是 opaque identifier，不是 filesystem path、URI、digest 或 trusted root。HTTP client
 * 不得构造或提交本类型；producer 未接入时必须使用 {@link #unbound()}，不能推导或伪造 key。
 */
public record BacktestPublishArtifactLocator(
        String artifactStorageKey,
        String manifestStorageKey
) {
    private static final Pattern STORAGE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    public BacktestPublishArtifactLocator {
        boolean artifactPresent = artifactStorageKey != null;
        boolean manifestPresent = manifestStorageKey != null;
        if (artifactPresent != manifestPresent) {
            throw new IllegalArgumentException("artifact and manifest storage keys must be provided together");
        }
        if (artifactPresent) {
            validateStorageKey(artifactStorageKey, "artifactStorageKey");
            validateStorageKey(manifestStorageKey, "manifestStorageKey");
        }
    }

    public static BacktestPublishArtifactLocator unbound() {
        return new BacktestPublishArtifactLocator(null, null);
    }

    public static BacktestPublishArtifactLocator bound(String artifactStorageKey, String manifestStorageKey) {
        return new BacktestPublishArtifactLocator(artifactStorageKey, manifestStorageKey);
    }

    public boolean isBound() {
        return artifactStorageKey != null;
    }

    private static void validateStorageKey(String value, String fieldName) {
        if (!STORAGE_KEY.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException(fieldName + " must be an opaque storage key");
        }
    }
}
