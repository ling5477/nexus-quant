package com.guidinglight.nexusquant.strategy.infra.artifact;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseArtifactBindingResolver;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 使用服务端 trusted root 将两把 opaque key 解析为 direct child，并安全加载 bounded manifest。
 *
 * <p>实现不接受 request path，不猜 filename/layout，不输出绝对路径或原始 manifest。Java NIO 无法提供
 * POSIX openat 风格的目录句柄绑定，因此使用 NOFOLLOW_LINKS、real-path containment 与前后 file identity
 * 对比尽量缩小 TOCTOU 窗口；任何无法证明的状态均 fail-closed。
 */
public final class ServerControlledStrategyArtifactBindingResolver
        implements StrategyReleaseArtifactBindingResolver {

    static final long MAX_MANIFEST_BYTES = 1024L * 1024L;
    private static final int BUFFER_SIZE = 8192;
    private static final Pattern STORAGE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final Runnable NOOP_HOOK = () -> { };

    private final String configuredTrustedRoot;
    private final ObjectReader manifestReader;
    private final Runnable afterManifestRead;

    public ServerControlledStrategyArtifactBindingResolver(
            String configuredTrustedRoot,
            ObjectMapper objectMapper
    ) {
        this(configuredTrustedRoot, objectMapper, NOOP_HOOK);
    }

    ServerControlledStrategyArtifactBindingResolver(
            String configuredTrustedRoot,
            ObjectMapper objectMapper,
            Runnable afterManifestRead
    ) {
        this.configuredTrustedRoot = configuredTrustedRoot;
        this.manifestReader = Objects.requireNonNull(objectMapper, "objectMapper must not be null")
                .readerFor(StrategyArtifactManifest.class)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.afterManifestRead = Objects.requireNonNull(afterManifestRead, "afterManifestRead must not be null");
    }

    @Override
    public ArtifactBindingResolution resolve(String artifactStorageKey, String manifestStorageKey) {
        if (artifactStorageKey == null && manifestStorageKey == null) {
            return rejected(FindingCode.ARTIFACT_LOCATION_UNBOUND, "<artifact-binding>");
        }
        if (!validKey(artifactStorageKey) || !validKey(manifestStorageKey)) {
            return rejected(FindingCode.ARTIFACT_LOCATION_UNSAFE, "<storage-key>");
        }
        if (configuredTrustedRoot == null || configuredTrustedRoot.isBlank()) {
            return rejected(FindingCode.ARTIFACT_ROOT_NOT_CONFIGURED, "<root>");
        }

        try {
            RootContext root = resolveRoot();
            ResolvedLocation artifact = resolveDirectChild(
                    root,
                    artifactStorageKey,
                    true,
                    FindingCode.ARTIFACT_LOCATION_NOT_FOUND
            );
            ResolvedLocation manifestLocation = resolveDirectChild(
                    root,
                    manifestStorageKey,
                    false,
                    FindingCode.ARTIFACT_MANIFEST_NOT_FOUND
            );

            byte[] manifestBytes = readManifest(manifestLocation.realPath());
            afterManifestRead.run();
            if (!sameLocation(root, artifact, manifestLocation)) {
                return rejected(FindingCode.ARTIFACT_LOCATION_UNSAFE, "<artifact-binding>");
            }

            StrategyArtifactManifest manifest;
            try {
                manifest = manifestReader.readValue(manifestBytes);
            } catch (IOException | RuntimeException exception) {
                return rejected(FindingCode.ARTIFACT_MANIFEST_INVALID, manifestStorageKey);
            }
            return ArtifactBindingResolution.resolved(artifact.realPath(), manifest);
        } catch (SafeResolutionException exception) {
            return rejected(exception.reasonCode(), exception.safeIdentifier());
        } catch (InvalidPathException exception) {
            return rejected(FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
        } catch (UnsupportedOperationException exception) {
            return rejected(FindingCode.ARTIFACT_LOCATION_UNSAFE, "<artifact-binding>");
        } catch (IOException | SecurityException exception) {
            return rejected(FindingCode.ARTIFACT_LOCATION_UNSAFE, "<artifact-binding>");
        }
    }
    private RootContext resolveRoot() throws SafeResolutionException {
        try {
            Path configured = Path.of(configuredTrustedRoot.trim());
            if (!configured.isAbsolute()) {
                throw failure(FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
            }
            Path normalized = configured.normalize();
            inspectConfiguredRootComponents(normalized);
            BasicFileAttributes attributes = readAttributes(normalized);
            rejectLinkOrSpecial(normalized, attributes, FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
            if (!attributes.isDirectory()) {
                throw failure(FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
            }
            Path real = normalized.toRealPath();
            BasicFileAttributes realAttributes = readAttributes(real);
            rejectLinkOrSpecial(real, realAttributes, FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
            if (!realAttributes.isDirectory()) {
                throw failure(FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
            }
            return new RootContext(normalized, real, identity(realAttributes));
        } catch (SafeResolutionException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
        }
    }

    private void inspectConfiguredRootComponents(Path root) throws IOException, SafeResolutionException {
        Path current = root.getRoot();
        for (Path component : root) {
            current = current == null ? component : current.resolve(component);
            BasicFileAttributes attributes = readAttributes(current);
            rejectLinkOrSpecial(current, attributes, FindingCode.ARTIFACT_ROOT_INVALID, "<root>");
        }
    }

    private ResolvedLocation resolveDirectChild(
            RootContext root,
            String storageKey,
            boolean directory,
            FindingCode missingCode
    ) throws IOException, SafeResolutionException {
        Path target = root.configuredPath().resolve(storageKey).normalize();
        if (!root.configuredPath().equals(target.getParent())) {
            throw failure(FindingCode.ARTIFACT_LOCATION_UNSAFE, "<storage-key>");
        }
        BasicFileAttributes attributes;
        try {
            attributes = readAttributes(target);
        } catch (NoSuchFileException exception) {
            throw failure(missingCode, storageKey);
        }
        rejectLinkOrSpecial(target, attributes, FindingCode.ARTIFACT_LOCATION_UNSAFE, storageKey);
        if ((directory && !attributes.isDirectory()) || (!directory && !attributes.isRegularFile())) {
            throw failure(FindingCode.ARTIFACT_LOCATION_UNSAFE, storageKey);
        }
        Path real = target.toRealPath();
        if (!real.startsWith(root.realPath()) || !root.realPath().equals(real.getParent())) {
            throw failure(FindingCode.ARTIFACT_LOCATION_UNSAFE, storageKey);
        }
        BasicFileAttributes realAttributes = readAttributes(real);
        rejectLinkOrSpecial(real, realAttributes, FindingCode.ARTIFACT_LOCATION_UNSAFE, storageKey);
        return new ResolvedLocation(target, real, identity(realAttributes), storageKey);
    }

    private byte[] readManifest(Path manifest) throws IOException, SafeResolutionException {
        BasicFileAttributes before = readAttributes(manifest);
        if (before.size() > MAX_MANIFEST_BYTES) {
            throw failure(FindingCode.ARTIFACT_MANIFEST_INVALID, "<manifest>");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(before.size(), BUFFER_SIZE));
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = Files.newByteChannel(manifest, options)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            long total = 0;
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    buffer.clear();
                    continue;
                }
                total = Math.addExact(total, read);
                if (total > MAX_MANIFEST_BYTES) {
                    throw failure(FindingCode.ARTIFACT_MANIFEST_INVALID, "<manifest>");
                }
                buffer.flip();
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                output.write(chunk);
                buffer.clear();
            }
        }
        BasicFileAttributes after = readAttributes(manifest);
        if (!sameIdentity(identity(before), identity(after))) {
            throw failure(FindingCode.ARTIFACT_LOCATION_UNSAFE, "<manifest>");
        }
        return output.toByteArray();
    }

    private boolean sameLocation(
            RootContext root,
            ResolvedLocation artifact,
            ResolvedLocation manifest
    ) throws IOException {
        BasicFileAttributes rootAfter = readAttributes(root.realPath());
        BasicFileAttributes artifactAfter = readAttributes(artifact.realPath());
        BasicFileAttributes manifestAfter = readAttributes(manifest.realPath());
        return root.configuredPath().toRealPath().equals(root.realPath())
                && artifact.configuredPath().toRealPath().equals(artifact.realPath())
                && manifest.configuredPath().toRealPath().equals(manifest.realPath())
                && sameIdentity(root.identity(), identity(rootAfter))
                && sameIdentity(artifact.identity(), identity(artifactAfter))
                && sameIdentity(manifest.identity(), identity(manifestAfter));
    }

    private static boolean validKey(String value) {
        return value != null && STORAGE_KEY.matcher(value).matches() && !value.contains("..");
    }

    private static void rejectLinkOrSpecial(
            Path path,
            BasicFileAttributes attributes,
            FindingCode code,
            String identifier
    ) throws IOException, SafeResolutionException {
        if (attributes.isSymbolicLink() || attributes.isOther() || Files.isSymbolicLink(path)) {
            throw failure(code, identifier);
        }
    }

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static FileIdentity identity(BasicFileAttributes attributes) {
        return new FileIdentity(
                attributes.isDirectory(),
                attributes.isRegularFile(),
                attributes.size(),
                attributes.lastModifiedTime().toMillis(),
                attributes.fileKey()
        );
    }

    private static boolean sameIdentity(FileIdentity before, FileIdentity after) {
        return before.equals(after);
    }

    private static ArtifactBindingResolution rejected(FindingCode code, String identifier) {
        return ArtifactBindingResolution.rejected(code, identifier);
    }

    private static SafeResolutionException failure(FindingCode code, String identifier) {
        return new SafeResolutionException(code, identifier);
    }

    private record RootContext(Path configuredPath, Path realPath, FileIdentity identity) {
    }

    private record ResolvedLocation(
            Path configuredPath,
            Path realPath,
            FileIdentity identity,
            String storageKey
    ) {
    }

    private record FileIdentity(
            boolean directory,
            boolean regularFile,
            long size,
            long lastModifiedMillis,
            Object fileKey
    ) {
    }

    private static final class SafeResolutionException extends Exception {
        private final FindingCode reasonCode;
        private final String safeIdentifier;

        private SafeResolutionException(FindingCode reasonCode, String safeIdentifier) {
            super(reasonCode.name());
            this.reasonCode = reasonCode;
            this.safeIdentifier = safeIdentifier;
        }

        private FindingCode reasonCode() {
            return reasonCode;
        }

        private String safeIdentifier() {
            return safeIdentifier;
        }
    }
}
