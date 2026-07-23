package com.guidinglight.nexusquant.strategyrelease.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.AggregateDigestResult;
import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.ArtifactDescriptor;
import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.FindingCode;
import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.Status;
import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.VerificationRequest;
import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.VerificationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PRE-GATEX trusted-root artifact verification 的 test-only 安全回归矩阵。
 */
class TrustedRootArtifactVerifierPrototypeTest {

    private static final String LOGICAL_NAME = "evaluation-report";
    private static final long DEFAULT_MAX_BYTES = 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    void shouldVerifyRegularFileInsideTrustedRoot() throws Exception {
        Path root = createRoot();
        byte[] content = "synthetic-artifact".getBytes(StandardCharsets.UTF_8);
        write(root, "artifacts/report.json", content);

        VerificationResult result = verifier().verify(request(root, "artifacts/report.json", content));

        assertEquals(Status.VERIFIED, result.status());
        assertNull(result.findingCode());
        assertEquals(sha256(content), result.actualSha256());
        assertEquals(content.length, result.actualSizeBytes());
        assertTrue(result.verifiedPathIdentity().startsWith("file-key-"));
    }

    @Test
    void shouldRejectDigestMismatchWithoutReturningActualDigest() throws Exception {
        Path root = createRoot();
        byte[] content = "synthetic-artifact".getBytes(StandardCharsets.UTF_8);
        write(root, "report.json", content);
        VerificationRequest request = new VerificationRequest(
                root,
                LOGICAL_NAME,
                "report.json",
                "0".repeat(64),
                content.length,
                DEFAULT_MAX_BYTES
        );

        VerificationResult result = verifier().verify(request);

        assertRejected(result, FindingCode.DIGEST_MISMATCH);
        assertNull(result.actualSha256());
        assertNull(result.actualSizeBytes());
    }

    @Test
    void shouldRejectSizeMismatch() throws Exception {
        Path root = createRoot();
        byte[] content = "size-check".getBytes(StandardCharsets.UTF_8);
        write(root, "report.json", content);
        VerificationRequest request = new VerificationRequest(
                root,
                LOGICAL_NAME,
                "report.json",
                sha256(content),
                content.length + 1,
                DEFAULT_MAX_BYTES
        );

        assertRejected(verifier().verify(request), FindingCode.SIZE_MISMATCH);
    }

    @Test
    void shouldRejectMissingFile() throws Exception {
        Path root = createRoot();
        byte[] expected = "missing".getBytes(StandardCharsets.UTF_8);

        assertRejected(
                verifier().verify(request(root, "missing.json", expected)),
                FindingCode.ARTIFACT_NOT_FOUND
        );
    }

    @Test
    void shouldRejectDirectoryAsArtifact() throws Exception {
        Path root = createRoot();
        Files.createDirectories(root.resolve("directory"));
        byte[] expected = "directory".getBytes(StandardCharsets.UTF_8);

        assertRejected(
                verifier().verify(request(root, "directory", expected)),
                FindingCode.ARTIFACT_NOT_REGULAR_FILE
        );
    }

    @Test
    void shouldRejectArtifactLargerThanMaximumAllowedBytes() throws Exception {
        Path root = createRoot();
        byte[] content = "larger-than-limit".getBytes(StandardCharsets.UTF_8);
        write(root, "large.bin", content);
        VerificationRequest request = new VerificationRequest(
                root,
                LOGICAL_NAME,
                "large.bin",
                sha256(content),
                content.length,
                content.length - 1L
        );

        assertRejected(verifier().verify(request), FindingCode.ARTIFACT_TOO_LARGE);
    }

    @Test
    void shouldRejectParentTraversalSegment() throws Exception {
        Path root = createRoot();

        assertRejected(
                verifier().verify(request(root, "../outside.json", "outside".getBytes(StandardCharsets.UTF_8))),
                FindingCode.PATH_ESCAPES_TRUSTED_ROOT
        );
    }

    @Test
    void shouldRejectPathThatNormalizesOutsideTrustedRoot() throws Exception {
        Path root = createRoot();

        assertRejected(
                verifier().verify(request(
                        root,
                        "nested/../../outside.json",
                        "outside".getBytes(StandardCharsets.UTF_8)
                )),
                FindingCode.PATH_ESCAPES_TRUSTED_ROOT
        );
    }

    @Test
    void shouldRejectUnixAbsolutePath() throws Exception {
        Path root = createRoot();

        VerificationResult result = verifier().verify(
                request(root, "/var/tmp/outside.json", "outside".getBytes(StandardCharsets.UTF_8))
        );

        assertRejected(result, FindingCode.INVALID_RELATIVE_PATH);
        assertEquals("<invalid-relative-path>", result.relativePath());
    }

    @Test
    void shouldRejectWindowsDrivePathOnEveryPlatform() throws Exception {
        Path root = createRoot();

        VerificationResult result = verifier().verify(
                request(root, "C:/temp/outside.json", "outside".getBytes(StandardCharsets.UTF_8))
        );

        assertRejected(result, FindingCode.INVALID_RELATIVE_PATH);
        assertEquals("<invalid-relative-path>", result.relativePath());
    }

    @Test
    void shouldRejectUncPath() throws Exception {
        Path root = createRoot();

        VerificationResult result = verifier().verify(
                request(root, "\\\\server\\share\\outside.json", "outside".getBytes(StandardCharsets.UTF_8))
        );

        assertRejected(result, FindingCode.INVALID_RELATIVE_PATH);
        assertEquals("<invalid-relative-path>", result.relativePath());
    }

    @Test
    void shouldRejectBackslashPath() throws Exception {
        Path root = createRoot();

        assertRejected(
                verifier().verify(request(
                        root,
                        "artifacts\\report.json",
                        "outside".getBytes(StandardCharsets.UTF_8)
                )),
                FindingCode.INVALID_RELATIVE_PATH
        );
    }

    @Test
    void shouldRejectControlCharactersWithoutEchoingThem() throws Exception {
        Path root = createRoot();

        VerificationResult result = verifier().verify(
                request(root, "artifacts/report\n.json", "outside".getBytes(StandardCharsets.UTF_8))
        );

        assertRejected(result, FindingCode.INVALID_RELATIVE_PATH);
        assertEquals("<invalid-relative-path>", result.relativePath());
        assertFalse(result.toString().contains("\n"));
    }

    @Test
    void shouldRejectSymbolicLinkPointingOutsideTrustedRoot() throws Exception {
        Path root = createRoot();
        Path outside = tempDir.resolve("outside.bin");
        byte[] content = "outside-content".getBytes(StandardCharsets.UTF_8);
        Files.write(outside, content);
        createSymbolicLinkOrAbort(root.resolve("outside-link.bin"), outside);

        assertRejected(
                verifier().verify(request(root, "outside-link.bin", content)),
                FindingCode.SYMLINK_NOT_ALLOWED
        );
    }

    @Test
    void shouldRejectSymbolicLinkPointingInsideTrustedRoot() throws Exception {
        Path root = createRoot();
        byte[] content = "inside-content".getBytes(StandardCharsets.UTF_8);
        Path target = write(root, "target.bin", content);
        createSymbolicLinkOrAbort(root.resolve("inside-link.bin"), target);

        assertRejected(
                verifier().verify(request(root, "inside-link.bin", content)),
                FindingCode.SYMLINK_NOT_ALLOWED
        );
    }

    @Test
    void shouldRejectTrustedRootThatIsSymbolicLink() throws Exception {
        Path realRoot = createRoot();
        Path rootLink = tempDir.resolve("root-link");
        createSymbolicLinkOrAbort(rootLink, realRoot);
        byte[] content = "inside-content".getBytes(StandardCharsets.UTF_8);
        write(realRoot, "report.bin", content);

        assertRejected(
                verifier().verify(request(rootLink, "report.bin", content)),
                FindingCode.SYMLINK_NOT_ALLOWED
        );
    }

    @Test
    void shouldRejectFileModifiedAfterPreReadAttributes() throws Exception {
        Path root = createRoot();
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        write(root, "report.bin", original);
        TrustedRootArtifactVerifierPrototype verifier = new TrustedRootArtifactVerifierPrototype(
                target -> Files.writeString(
                        target,
                        "modified-content-is-longer",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING
                )
        );

        assertRejected(
                verifier.verify(request(root, "report.bin", original)),
                FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION
        );
    }

    @Test
    void shouldRejectFileReplacedAfterPreReadAttributes() throws Exception {
        Path root = createRoot();
        byte[] original = "original-file".getBytes(StandardCharsets.UTF_8);
        write(root, "report.bin", original);
        TrustedRootArtifactVerifierPrototype verifier = new TrustedRootArtifactVerifierPrototype(target -> {
            Files.delete(target);
            Files.writeString(target, "replacement-file-with-different-size", StandardCharsets.UTF_8);
        });

        assertRejected(
                verifier.verify(request(root, "report.bin", original)),
                FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION
        );
    }

    @Test
    void shouldNotLeakTrustedRootAbsolutePathInRejectedResult() throws Exception {
        Path root = createRoot();
        byte[] expected = "missing".getBytes(StandardCharsets.UTF_8);

        VerificationResult result = verifier().verify(request(root, "missing.json", expected));

        assertRejected(result, FindingCode.ARTIFACT_NOT_FOUND);
        assertFalse(result.toString().contains(root.toAbsolutePath().toString()));
        assertFalse(result.safeReason().contains(root.toAbsolutePath().toString()));
    }

    @Test
    void shouldComputeSameAggregateDigestRegardlessOfInputOrder() {
        ArtifactDescriptor first = descriptor(
                "evaluation-report",
                "artifacts/evaluation.json",
                "1".repeat(64),
                2048,
                "application/json"
        );
        ArtifactDescriptor second = descriptor(
                "strategy-parameters",
                "artifacts/parameters.json",
                "2".repeat(64),
                512,
                "application/json"
        );

        AggregateDigestResult forward = TrustedRootArtifactVerifierPrototype.computeAggregateDigest(
                List.of(first, second)
        );
        AggregateDigestResult reverse = TrustedRootArtifactVerifierPrototype.computeAggregateDigest(
                List.of(second, first)
        );

        assertEquals(Status.VERIFIED, forward.status());
        assertEquals(forward.digest(), reverse.digest());
        assertTrue(forward.digest().matches("^[0-9a-f]{64}$"));
    }

    @Test
    void shouldChangeAggregateDigestWhenAnyCanonicalFieldChanges() {
        ArtifactDescriptor baseline = descriptor(
                "evaluation-report",
                "artifacts/evaluation.json",
                "1".repeat(64),
                2048,
                "application/json"
        );
        String baselineDigest = aggregateDigest(baseline);
        List<ArtifactDescriptor> mutations = List.of(
                descriptor(
                        "evaluation-report-v2",
                        baseline.relativePath(),
                        baseline.sha256(),
                        baseline.sizeBytes(),
                        baseline.mediaType()
                ),
                descriptor(
                        baseline.logicalName(),
                        "artifacts/evaluation-v2.json",
                        baseline.sha256(),
                        baseline.sizeBytes(),
                        baseline.mediaType()
                ),
                descriptor(
                        baseline.logicalName(),
                        baseline.relativePath(),
                        "2".repeat(64),
                        baseline.sizeBytes(),
                        baseline.mediaType()
                ),
                descriptor(
                        baseline.logicalName(),
                        baseline.relativePath(),
                        baseline.sha256(),
                        baseline.sizeBytes() + 1,
                        baseline.mediaType()
                ),
                descriptor(
                        baseline.logicalName(),
                        baseline.relativePath(),
                        baseline.sha256(),
                        baseline.sizeBytes(),
                        "application/octet-stream"
                )
        );

        mutations.forEach(mutation -> assertNotEquals(baselineDigest, aggregateDigest(mutation)));
    }

    @Test
    void shouldNeverIncludeArtifactContentInErrorResult() throws Exception {
        Path root = createRoot();
        String sensitiveFixture = "synthetic-private-artifact-content-marker";
        byte[] content = sensitiveFixture.getBytes(StandardCharsets.UTF_8);
        write(root, "report.bin", content);
        VerificationRequest request = new VerificationRequest(
                root,
                LOGICAL_NAME,
                "report.bin",
                "f".repeat(64),
                content.length,
                DEFAULT_MAX_BYTES
        );

        VerificationResult result = verifier().verify(request);

        assertRejected(result, FindingCode.DIGEST_MISMATCH);
        assertFalse(result.toString().contains(sensitiveFixture));
        assertFalse(result.safeReason().contains(sensitiveFixture));
    }

    private TrustedRootArtifactVerifierPrototype verifier() {
        return new TrustedRootArtifactVerifierPrototype();
    }

    private Path createRoot() throws IOException {
        return Files.createDirectories(tempDir.resolve("trusted-root"));
    }

    private Path write(Path root, String relativePath, byte[] content) throws IOException {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        return Files.write(target, content);
    }

    private VerificationRequest request(Path root, String relativePath, byte[] expectedContent)
            throws NoSuchAlgorithmException {
        return new VerificationRequest(
                root,
                LOGICAL_NAME,
                relativePath,
                sha256(expectedContent),
                expectedContent.length,
                DEFAULT_MAX_BYTES
        );
    }

    private void createSymbolicLinkOrAbort(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "SYMLINK_PRIVILEGE_UNAVAILABLE");
        }
    }

    private void assertRejected(VerificationResult result, FindingCode expectedFinding) {
        assertEquals(Status.REJECTED, result.status());
        assertEquals(expectedFinding, result.findingCode());
        assertNull(result.actualSha256());
        assertNull(result.actualSizeBytes());
        assertNull(result.verifiedPathIdentity());
    }

    private ArtifactDescriptor descriptor(
            String logicalName,
            String relativePath,
            String sha256,
            long sizeBytes,
            String mediaType
    ) {
        return new ArtifactDescriptor(logicalName, relativePath, sha256, sizeBytes, mediaType);
    }

    private String aggregateDigest(ArtifactDescriptor descriptor) {
        AggregateDigestResult result = TrustedRootArtifactVerifierPrototype.computeAggregateDigest(
                List.of(descriptor)
        );
        assertEquals(Status.VERIFIED, result.status());
        return result.digest();
    }

    private String sha256(byte[] content) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
