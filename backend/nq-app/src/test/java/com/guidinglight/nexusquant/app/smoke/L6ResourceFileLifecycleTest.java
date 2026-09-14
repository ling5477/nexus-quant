package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 控制删除恰好发生在metadata读取区间；锁不延伸到任何资源collector的外部等待。 */
class L6ResourceFileLifecycleTest {
    @TempDir Path directory;

    @Test void anotherCollectorCannotDeleteBetweenFileTypeAndSizeObservation() throws Exception {
        var lifecycle = new L6ResourceFileLifecycle();
        Path file = Files.writeString(directory.resolve("resource-command-owned.tmp"), "measured");
        var entered = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor(); var paths = Files.walk(directory)) {
            var delegate = paths.iterator();
            Iterator<Path> measuredPaths = new Iterator<>() {
                @Override public boolean hasNext() { assertTrue(Thread.holdsLock(lifecycle)); return delegate.hasNext(); }
                @Override public Path next() { assertTrue(Thread.holdsLock(lifecycle)); return delegate.next(); }
            };
            assertEquals(-1, lifecycle.nextFileBytes(measuredPaths));
            Future<?> deletion;
            synchronized (lifecycle) {
                deletion = executor.submit(() -> {
                    entered.countDown(); lifecycle.deleteTemporary(file); return null;
                });
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> deletion.get(100, TimeUnit.MILLISECONDS));
                assertEquals(8, lifecycle.nextFileBytes(measuredPaths));
            }
            deletion.get(2, TimeUnit.SECONDS);
            assertEquals(-1, lifecycle.fileBytes(file));
            assertEquals(L6ResourceFileLifecycle.END_OF_FILES, lifecycle.nextFileBytes(measuredPaths));
        }
    }
}
