package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分开证明 fixture 生命周期，故意的失败不计为生产 correctness finding。 */
@EnabledIfSystemProperty(named = "nq.l5.fill", matches = "true")
class L5FixtureCleanupTest {
    @Test void passPathReleasesEveryOwnedResource() throws Exception { execute("PASS"); }
    @Test void assertionFailureReleasesEveryOwnedResource() throws Exception { execute("ASSERTION"); }
    @Test void actorExceptionAndClosedStdinReleaseEveryOwnedResource() throws Exception { execute("EXCEPTION"); }
    @Test void setupFailureClosesChildrenAlreadyStarted() throws Exception { execute("SETUP"); }

    private void execute(String path) throws Exception {
        var dir = L5FillIdempotencyTest.directory("cleanup-" + path + "-");
        List<B0Processes.Child> children = new ArrayList<>();
        var all = new ArrayList<B0Processes.Child>();
        Class<?> entry = Boolean.getBoolean("nq.l5.kill") ? L5KillNqProcessMain.class : L5NqProcessMain.class;
        String container;
        try (var pg = B0Processes.Pg.startBounded()) {
            container = pg.ownedContainerId();
            assertTrue(!container.isBlank());
            try (var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(L5VenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                all.add(venue);
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = L5FillIdempotencyTest.environment(fixture, endpoint); fixture.initialize(true, endpoint, env);
                try {
                    children.add(new B0Processes.Child(entry, dir, "nq-first", env).awaitReady());
                    children.add(new B0Processes.Child(entry, dir, "nq-second", env).awaitReady());
                    all.addAll(children);
                    if (path.equals("ASSERTION")) {
                        throw new AssertionError("expected assertion failure");
                    }
                    if (path.equals("EXCEPTION")) {
                        children.getLast().startCommand("UNEXPECTED_COMMAND");
                        assertTrue(children.getLast().process.waitFor(15, TimeUnit.SECONDS), "failed actor sampler must stop");
                        assertTrue(children.getLast().process.exitValue() != 0);
                        children.getFirst().process.getOutputStream().close();
                        // 第一个正常 JVM 仍必须在第二个 closed-stdin 之后得到清理。
                        throw new IllegalStateException("expected actor failure");
                    }
                    if (path.equals("SETUP")) {
                        var invalid = B0Processes.cleanEnvironment();
                        var failed = new B0Processes.Child(entry, dir, "bad-setup", invalid);
                        all.add(failed);
                        assertThrows(AssertionError.class, failed::awaitReady);
                        throw new IllegalStateException("expected setup failure");
                    }
                } catch (AssertionError failure) {
                    assertEquals("ASSERTION", path); assertEquals("expected assertion failure", failure.getMessage());
                } catch (IllegalStateException failure) {
                    assertTrue(List.of("EXCEPTION", "SETUP").contains(path));
                    assertTrue(failure.getMessage().startsWith("expected"));
                } finally { B0Processes.closeChildren(children); }
            }
        }
        assertTrue(all.stream().noneMatch(c -> c.process.isAlive()));
        assertTrue(B0Processes.command("docker", "ps", "-a", "--filter", "id=" + container, "--format", "{{.ID}}").isBlank());
        Files.writeString(dir.resolve("cleanup-result.txt"), path + " ownedNq=0 ownedVenue=0 ownedPg=0\n");
        System.out.println("L5_FIXTURE_PASS " + path + " ownedNq=0 ownedVenue=0 ownedPg=0");
    }
}
