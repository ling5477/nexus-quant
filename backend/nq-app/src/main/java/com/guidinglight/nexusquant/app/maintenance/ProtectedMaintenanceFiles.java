package com.guidinglight.nexusquant.app.maintenance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** 沿固定目录句柄读取 root 专用文件，拒绝软链接、不安全父目录及不支持 POSIX 的平台。 */
final class ProtectedMaintenanceFiles {
    private static final Set<PosixFilePermission> SECRET_MODE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private ProtectedMaintenanceFiles() {
    }

    static byte[] read(Path path, int limit) throws IOException {
        if (!path.isAbsolute() || !path.equals(path.normalize()) || path.getNameCount() > 16
                || path.getNameCount() == 0 || limit < 1 || limit > 16384) {
            throw new IOException("UNSAFE_MAINTENANCE_SOURCE");
        }
        validate(Files.readAttributes(path.getRoot(), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS), false);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path.getRoot())) {
            if (!(stream instanceof SecureDirectoryStream<Path> secure)) {
                throw new IOException("SECURE_DIRECTORY_HANDLES_REQUIRED");
            }
            return readChild(secure, path, 0, limit);
        }
    }

    private static byte[] readChild(SecureDirectoryStream<Path> directory, Path path, int index, int limit)
            throws IOException {
        Path name = path.getName(index);
        boolean leaf = index == path.getNameCount() - 1;
        PosixFileAttributeView view = directory.getFileAttributeView(name, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("POSIX_FILE_ATTRIBUTES_REQUIRED");
        }
        PosixFileAttributes before = view.readAttributes();
        validate(before, leaf);
        if (!leaf) {
            try (SecureDirectoryStream<Path> child = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                return readChild(child, path, index + 1, limit);
            }
        }
        if (before.size() < 1 || before.size() > limit) {
            throw new IOException("MAINTENANCE_SOURCE_SIZE_INVALID");
        }
        ByteBuffer buffer = ByteBuffer.allocate(limit + 1);
        try (SeekableByteChannel channel = directory.newByteChannel(name,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // 文件大小上限同时限制读取量，不能由读取前的 stat 代替。
            }
            PosixFileAttributes after = view.readAttributes();
            validate(after, true);
            if (buffer.position() != before.size() || buffer.position() > limit
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw new IOException("MAINTENANCE_SOURCE_CHANGED");
            }
            return Arrays.copyOf(buffer.array(), buffer.position());
        } finally {
            Arrays.fill(buffer.array(), (byte) 0);
        }
    }

    static void validate(PosixFileAttributes attributes, boolean secretFile) throws IOException {
        if (!"root".equals(attributes.owner().getName()) || attributes.isSymbolicLink()) {
            throw new IOException("ROOT_OWNED_SOURCE_REQUIRED");
        }
        if (secretFile) {
            if (!attributes.isRegularFile() || !attributes.permissions().equals(SECRET_MODE)) {
                throw new IOException("SECRET_FILE_MODE_0600_REQUIRED");
            }
        } else if (!attributes.isDirectory()
                || attributes.permissions().contains(PosixFilePermission.GROUP_WRITE)
                || attributes.permissions().contains(PosixFilePermission.OTHERS_WRITE)) {
            throw new IOException("PROTECTED_PARENT_DIRECTORY_REQUIRED");
        }
    }
}
