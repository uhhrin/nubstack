
package dev.nubstack.amber;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class SnapshotService {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path snapshotRoot;
    private final int retain;

    public SnapshotService(Path snapshotRoot, int retain) {
        this.snapshotRoot = snapshotRoot;
        this.retain = retain;
    }

    public Path create(String label, Path worldFolder) throws IOException {
        Files.createDirectories(snapshotRoot);
        String name = FORMAT.format(Instant.now()) + "-" + sanitize(label);
        Path target = snapshotRoot.resolve(name);
        copyRecursive(worldFolder, target);
        prune();
        return target;
    }

    public List<Path> list() throws IOException {
        if (!Files.isDirectory(snapshotRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(snapshotRoot)) {
            return stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
        }
    }

    public void prune() throws IOException {
        List<Path> all = new ArrayList<>(list());
        for (int i = retain; i < all.size(); i++) {
            deleteRecursive(all.get(i));
        }
    }

    private static String sanitize(String label) {
        return label.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static void copyRecursive(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
