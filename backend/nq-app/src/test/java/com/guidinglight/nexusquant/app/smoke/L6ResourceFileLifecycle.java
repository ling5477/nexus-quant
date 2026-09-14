package com.guidinglight.nexusquant.app.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/** 仅协调单文件metadata读取与临时文件删除；任何外部采集或进程等待均不持有此锁。 */
final class L6ResourceFileLifecycle {
    static final long END_OF_FILES = -2;

    synchronized long nextFileBytes(Iterator<Path> paths) throws IOException {
        // Files.walk的advance也读取属性；它与随后type/size必须处于同一单文件临界区。
        return paths.hasNext() ? fileBytes(paths.next()) : END_OF_FILES;
    }

    synchronized long fileBytes(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IllegalStateException("L6_UNOWNED_PATH_LINK");
        return Files.isRegularFile(path) ? Files.size(path) : -1;
    }

    synchronized void deleteTemporary(Path path) throws IOException { Files.deleteIfExists(path); }
}
