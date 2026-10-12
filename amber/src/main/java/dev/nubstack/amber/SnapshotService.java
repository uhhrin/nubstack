package dev.nubstack.amber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class SnapshotService {

    static final String META_FILE = "amber-meta.properties";
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path snapshotRoot;
    private final int retain;
    private final Set<String> excludeNames;
    private final Set<String> busyWorlds = ConcurrentHashMap.newKeySet();
    private final Set<Path> restoreLocks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public SnapshotService(Path snapshotRoot, int retain, Set<String> excludeNames) {
        this.snapshotRoot = Objects.requireNonNull(snapshotRoot, "snapshotRoot");
        this.retain = retain;
        this.excludeNames = Set.copyOf(Objects.requireNonNull(excludeNames, "excludeNames"));
        if (retain < 1) {
            throw new IllegalArgumentException("retain must be >= 1");
        }
    }

    public static String stamp(Instant instant) {
        return FORMAT.format(instant);
    }

    /**
     * Cheap directory listing for tab completion.
     * shortcut: runs on the main thread, one directory read, no metadata parsing.
     */
    public List<String> snapshotNames() {
        if (!Files.isDirectory(snapshotRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(snapshotRoot)) {
            return stream.filter(Files::isDirectory).map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Path snapshotRoot() {
        return snapshotRoot;
    }

    public boolean isSnapshotInProgress(String worldName) {
        return busyWorlds.contains(worldName);
    }

    public boolean isIdle() {
        return busyWorlds.isEmpty() && restoreLocks.isEmpty();
    }

    /**
     * Requests cancellation of every running snapshot.
     * shortcut: cancel is global, not per world; add a world argument if parallel snapshots become common.
     *
     * @return false when nothing was running
     */
    public boolean cancel() {
        if (busyWorlds.isEmpty()) {
            return false;
        }
        cancelRequested.set(true);
        return true;
    }

    public void beginRestore(Path snapshotPath) {
        restoreLocks.add(snapshotPath.toAbsolutePath().normalize());
    }

    public void endRestore(Path snapshotPath) {
        restoreLocks.remove(snapshotPath.toAbsolutePath().normalize());
    }

    public SnapshotRef create(
            String worldName,
            Path worldFolder,
            String label,
            AutosaveControl autosave,
            FreeSpace freeSpace,
            Consumer<Progress> onProgress)
            throws IOException {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(worldFolder, "worldFolder");
        Objects.requireNonNull(autosave, "autosave");
        Objects.requireNonNull(freeSpace, "freeSpace");
        Objects.requireNonNull(onProgress, "onProgress");
        if (!busyWorlds.add(worldName)) {
            throw new IllegalStateException("Snapshot already running for world " + worldName);
        }
        boolean previous = autosave.isAutoSave(worldName);
        autosave.setAutoSave(worldName, false);
        Path target = null;
        try {
            Files.createDirectories(snapshotRoot);
            long totalBytes = measureBytes(worldFolder);
            long usable = freeSpace.usableBytes(snapshotRoot);
            if (usable < totalBytes) {
                throw new IOException(
                        "Insufficient disk space for snapshot (need " + totalBytes + " bytes, usable " + usable + ")");
            }
            String slug = FORMAT.format(Instant.now()) + "-" + sanitize(worldName);
            if (label != null && !label.isBlank()) {
                slug = slug + "-" + sanitize(label);
            }
            target = snapshotRoot.resolve(slug);
            if (Files.exists(target)) {
                throw new IOException("Snapshot path already exists: " + slug);
            }
            copyRecursive(worldFolder, target, totalBytes, onProgress);
            writeMeta(target, worldName, label == null ? "" : label.trim(), Instant.now(), totalBytes);
            prune();
            return info(target.getFileName().toString());
        } catch (IOException | RuntimeException e) {
            if (target != null) {
                deleteRecursive(target);
            }
            throw e;
        } finally {
            autosave.setAutoSave(worldName, previous);
            busyWorlds.remove(worldName);
            if (busyWorlds.isEmpty()) {
                cancelRequested.set(false);
            }
        }
    }

    /**
     * Moves the live world folder aside to a sibling {@code pre-restore-<stamp>-<folder>} and copies the
     * snapshot in its place. On failure the original folder is put back. The caller must have unloaded the world.
     *
     * @return the folder holding the replaced world
     */
    public Path swapIn(Path snapshot, String worldName, Path worldFolder, String stamp) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(worldFolder, "worldFolder");
        if (!busyWorlds.add(worldName)) {
            throw new IllegalStateException("Snapshot already running for world " + worldName);
        }
        try {
            if (!hasContent(snapshot)) {
                throw new IOException("Snapshot is missing or empty: " + snapshot.getFileName());
            }
            Path moved = worldFolder.resolveSibling("pre-restore-" + sanitize(stamp) + "-" + worldFolder.getFileName());
            if (Files.exists(moved)) {
                throw new IOException("Path already exists: " + moved.getFileName());
            }
            Files.move(worldFolder, moved);
            try {
                copyDirectory(snapshot, worldFolder);
            } catch (IOException | RuntimeException e) {
                deleteRecursive(worldFolder);
                Files.move(moved, worldFolder);
                throw e;
            }
            return moved;
        } finally {
            busyWorlds.remove(worldName);
        }
    }

    private static boolean hasContent(Path snapshot) throws IOException {
        if (!Files.isDirectory(snapshot)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(snapshot)) {
            return stream.anyMatch(path -> !META_FILE.equals(path.getFileName().toString()));
        }
    }

    public List<SnapshotRef> list() throws IOException {
        return list(null);
    }

    public List<SnapshotRef> list(String worldFilter) throws IOException {
        if (!Files.isDirectory(snapshotRoot)) {
            return List.of();
        }
        List<SnapshotRef> refs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(snapshotRoot)) {
            for (Path path : stream.filter(Files::isDirectory).toList()) {
                SnapshotRef ref = readRef(path);
                if (worldFilter == null || worldFilter.equalsIgnoreCase(ref.world())) {
                    refs.add(ref);
                }
            }
        }
        refs.sort(Comparator.comparing((SnapshotRef ref) -> ref.path().getFileName().toString()).reversed());
        return refs;
    }

    public SnapshotRef info(String filename) throws IOException {
        Path path = SnapshotPaths.resolve(snapshotRoot, filename);
        if (!Files.isDirectory(path)) {
            throw new IOException("Snapshot not found: " + filename);
        }
        return readRef(path);
    }

    public void delete(String filename) throws IOException {
        Path path = SnapshotPaths.resolve(snapshotRoot, filename);
        if (restoreLocks.contains(path.toAbsolutePath().normalize())) {
            throw new IllegalStateException("Cannot delete snapshot while restore is in progress");
        }
        if (!Files.isDirectory(path)) {
            throw new IOException("Snapshot not found: " + filename);
        }
        deleteRecursive(path);
    }

    public long diskUsedBytes() throws IOException {
        if (!Files.isDirectory(snapshotRoot)) {
            return 0L;
        }
        final long[] total = {0L};
        Files.walkFileTree(snapshotRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                total[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    public void prune() throws IOException {
        List<SnapshotRef> all = new ArrayList<>(list());
        int kept = 0;
        for (SnapshotRef ref : all) {
            Path abs = ref.path().toAbsolutePath().normalize();
            if (restoreLocks.contains(abs)) {
                continue;
            }
            kept++;
            if (kept > retain) {
                deleteRecursive(ref.path());
            }
        }
    }

    private SnapshotRef readRef(Path path) throws IOException {
        Properties props = readMeta(path);
        String world = props.getProperty("world", path.getFileName().toString());
        String label = props.getProperty("label", "");
        Instant created = Instant.parse(props.getProperty("created", Instant.EPOCH.toString()));
        long size = Long.parseLong(props.getProperty("sizeBytes", "0"));
        if (size <= 0) {
            size = measureBytes(path);
        }
        return new SnapshotRef(path, world, label, created, size);
    }

    private static Properties readMeta(Path snapshot) throws IOException {
        Properties props = new Properties();
        Path meta = snapshot.resolve(META_FILE);
        if (!Files.isRegularFile(meta)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(meta)) {
            props.load(in);
        }
        return props;
    }

    private static void writeMeta(Path snapshot, String world, String label, Instant created, long sizeBytes)
            throws IOException {
        Properties props = new Properties();
        props.setProperty("world", world);
        props.setProperty("label", label == null ? "" : label);
        props.setProperty("created", created.toString());
        props.setProperty("sizeBytes", Long.toString(sizeBytes));
        try (OutputStream out = Files.newOutputStream(snapshot.resolve(META_FILE))) {
            props.store(out, "amber snapshot metadata");
        }
    }

    private long measureBytes(Path root) throws IOException {
        final long[] total = {0L};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && excludeNames.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (excludeNames.contains(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                if (META_FILE.equals(file.getFileName().toString()) && file.getParent().equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                total[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    private void copyRecursive(Path source, Path target, long totalBytes, Consumer<Progress> onProgress)
            throws IOException {
        final long[] copied = {0L};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                checkCancel();
                if (!dir.equals(source) && excludeNames.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                checkCancel();
                if (excludeNames.contains(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path dest = target.resolve(source.relativize(file));
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                copied[0] += attrs.size();
                onProgress.accept(new Progress(copied[0], Math.max(totalBytes, copied[0])));
                return FileVisitResult.CONTINUE;
            }
        });
        if (copied[0] == 0L) {
            onProgress.accept(new Progress(0L, Math.max(totalBytes, 0L)));
        }
    }

    private void checkCancel() throws IOException {
        if (cancelRequested.get()) {
            throw new SnapshotCancelledException();
        }
    }

    static String sanitize(String label) {
        return label.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    static void deleteRecursive(Path root) throws IOException {
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

    static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (META_FILE.equals(file.getFileName().toString()) && file.getParent().equals(source)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public record SnapshotRef(Path path, String world, String label, Instant createdAt, long sizeBytes) {
    }

    public record Progress(long copiedBytes, long totalBytes) {
        public float fraction() {
            if (totalBytes <= 0L) {
                return 1f;
            }
            return Math.min(1f, (float) copiedBytes / (float) totalBytes);
        }
    }

    public interface AutosaveControl {
        boolean isAutoSave(String worldName);

        void setAutoSave(String worldName, boolean enabled);
    }

    @FunctionalInterface
    public interface FreeSpace {
        long usableBytes(Path path) throws IOException;
    }
}
