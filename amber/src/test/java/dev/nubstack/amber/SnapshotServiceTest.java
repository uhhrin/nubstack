package dev.nubstack.amber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotServiceTest {
    @TempDir Path temp;

    @Test
    void createsAndPrunes() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(1);
        service.create("world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        service.create("world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        assertEquals(1, service.list().size());
        assertTrue(Files.exists(service.list().getFirst().path().resolve("level.dat")));
    }

    @Test
    void disablesAutosaveAroundCopyAndRestoresEvenOnFailure() throws Exception {
        Path world = worldWith("level.dat", "data");
        List<String> events = new ArrayList<>();
        SnapshotService.AutosaveControl autosave = new SnapshotService.AutosaveControl() {
            private boolean enabled = true;

            @Override
            public boolean isAutoSave(String worldName) {
                return enabled;
            }

            @Override
            public void setAutoSave(String worldName, boolean value) {
                enabled = value;
                events.add(value ? "enable" : "disable");
            }
        };
        SnapshotService.FreeSpace tight = path -> 0L;
        SnapshotService service = service(5);
        assertThrows(IOException.class, () ->
                service.create("world", world, null, autosave, tight, ignored -> {}));
        assertEquals(List.of("disable", "enable"), events);
        assertTrue(autosave.isAutoSave("world"));
    }

    @Test
    void excludesSessionLockByDefault() throws Exception {
        Path world = worldWith("level.dat", "data");
        Files.writeString(world.resolve("session.lock"), "locked");
        SnapshotService service = service(5);
        Path created = service.create(
                        "world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {})
                .path();
        assertTrue(Files.exists(created.resolve("level.dat")));
        assertFalse(Files.exists(created.resolve("session.lock")));
    }

    @Test
    void refusesWhenDiskSpaceInsufficient() throws Exception {
        Path world = worldWith("level.dat", "abcdef");
        SnapshotService service = service(5);
        IOException error = assertThrows(IOException.class, () -> service.create(
                "world",
                world,
                null,
                recordingAutosave("world"),
                path -> 1L,
                ignored -> {}));
        assertTrue(error.getMessage().toLowerCase().contains("disk"));
    }

    @Test
    void rejectsConcurrentSnapshotOfSameWorld() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(5);
        AtomicBoolean hold = new AtomicBoolean(true);
        Thread worker = new Thread(() -> {
            try {
                service.create(
                        "world",
                        world,
                        null,
                        recordingAutosave("world"),
                        plentySpace(),
                        progress -> {
                            while (hold.get()) {
                                Thread.onSpinWait();
                            }
                        });
            } catch (IOException ignored) {
                // test only needs the lock held
            }
        });
        worker.start();
        awaitBusy(service, "world");
        assertThrows(IllegalStateException.class, () -> service.create(
                "world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {}));
        hold.set(false);
        worker.join(5000);
    }

    @Test
    void cancelStopsRunningCopy() throws Exception {
        Path world = temp.resolve("world");
        Files.createDirectories(world);
        for (int i = 0; i < 40; i++) {
            Files.writeString(world.resolve("chunk-" + i + ".dat"), "x".repeat(8192));
        }
        SnapshotService service = service(5);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                service.create(
                        "world",
                        world,
                        null,
                        recordingAutosave("world"),
                        plentySpace(),
                        progress -> service.cancel());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        worker.start();
        worker.join(10000);
        assertTrue(failure.get() instanceof SnapshotCancelledException);
        assertEquals(0, service.list().size());
        assertFalse(service.isSnapshotInProgress("world"));
    }

    @Test
    void cancelWithNothingRunningReturnsFalseAndDoesNotPoisonNextSnapshot() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(5);
        assertFalse(service.cancel());
        service.create("world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        assertEquals(1, service.list().size());
    }

    @Test
    void backToBackSnapshotsOfSameWorldGetDistinctNames() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(5);
        for (int i = 0; i < 3; i++) {
            service.create("world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        }
        assertEquals(3, service.list().size());
    }

    @Test
    void deleteRefusedWhileRestoreLocked() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(5);
        SnapshotService.SnapshotRef ref = service.create(
                "world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        service.beginRestore(ref.path());
        assertFalse(service.isIdle());
        assertThrows(IllegalStateException.class, () -> service.delete(ref.path().getFileName().toString()));
        service.endRestore(ref.path());
        assertTrue(service.isIdle());
        service.delete(ref.path().getFileName().toString());
        assertEquals(0, service.list().size());
    }

    @Test
    void deleteRejectsTraversal() {
        SnapshotService service = service(5);
        assertThrows(IllegalArgumentException.class, () -> service.delete("../world"));
    }

    @Test
    void swapInReplacesWorldAndKeepsPreRestoreCopy() throws Exception {
        Path world = worldWith("level.dat", "before");
        SnapshotService service = service(5);
        SnapshotService.SnapshotRef ref = service.create(
                "world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        Files.writeString(world.resolve("level.dat"), "griefed");
        Files.writeString(world.resolve("extra.dat"), "new");

        Path moved = service.swapIn(ref.path(), "world", world, "20260101-000000");

        assertEquals("before", Files.readString(world.resolve("level.dat")));
        assertFalse(Files.exists(world.resolve("extra.dat")));
        assertFalse(Files.exists(world.resolve(SnapshotService.META_FILE)));
        assertEquals("pre-restore-20260101-000000-" + world.getFileName(), moved.getFileName().toString());
        assertEquals("griefed", Files.readString(moved.resolve("level.dat")));
        assertTrue(Files.exists(ref.path().resolve("level.dat")));
    }

    @Test
    void swapInRollsBackWhenCopyFails() throws Exception {
        Path world = worldWith("level.dat", "current");
        Path snapshot = temp.resolve("snaps").resolve("broken");
        Files.createDirectories(snapshot);
        Files.writeString(snapshot.resolve("level.dat"), "snap");
        Files.createSymbolicLink(snapshot.resolve("dangling.dat"), temp.resolve("does-not-exist"));
        SnapshotService service = service(5);

        assertThrows(IOException.class, () -> service.swapIn(snapshot, "world", world, "20260101-000000"));

        assertEquals("current", Files.readString(world.resolve("level.dat")));
        assertFalse(Files.exists(world.resolveSibling("pre-restore-20260101-000000-" + world.getFileName())));
    }

    @Test
    void swapInRefusesEmptyOrMissingSnapshotAndLeavesWorldAlone() throws Exception {
        Path world = worldWith("level.dat", "current");
        Path empty = temp.resolve("snaps").resolve("empty");
        Files.createDirectories(empty);
        SnapshotService service = service(5);
        assertThrows(IOException.class, () -> service.swapIn(empty, "world", world, "t"));
        assertThrows(IOException.class, () ->
                service.swapIn(temp.resolve("snaps").resolve("missing"), "world", world, "t"));
        assertEquals("current", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void swapInRefusedWhileSnapshotOfWorldRuns() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(5);
        SnapshotService.SnapshotRef ref = service.create(
                "world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        AtomicBoolean hold = new AtomicBoolean(true);
        Thread worker = new Thread(() -> {
            try {
                service.create("world", world, "again", recordingAutosave("world"), plentySpace(), progress -> {
                    while (hold.get()) {
                        Thread.onSpinWait();
                    }
                });
            } catch (IOException ignored) {
                // test only needs the lock held
            }
        });
        worker.start();
        awaitBusy(service, "world");
        assertThrows(IllegalStateException.class, () -> service.swapIn(ref.path(), "world", world, "t"));
        hold.set(false);
        worker.join(5000);
    }

    @Test
    void pruneSkipsSnapshotLockedForRestore() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(1);
        SnapshotService.SnapshotRef first = service.create(
                "world", world, null, recordingAutosave("world"), plentySpace(), ignored -> {});
        service.beginRestore(first.path());
        SnapshotService.SnapshotRef second = service.create(
                "world", world, "two", recordingAutosave("world"), plentySpace(), ignored -> {});
        List<SnapshotService.SnapshotRef> listed = service.list();
        assertEquals(2, listed.size());
        assertTrue(listed.stream().anyMatch(ref -> ref.path().equals(first.path())));
        assertTrue(listed.stream().anyMatch(ref -> ref.path().equals(second.path())));
        service.endRestore(first.path());
        service.prune();
        assertEquals(1, service.list().size());
    }

    @Test
    void storesLabelMetadata() throws Exception {
        Path world = worldWith("level.dat", "data");
        SnapshotService service = service(5);
        SnapshotService.SnapshotRef ref = service.create(
                "world", world, "pre-event", recordingAutosave("world"), plentySpace(), ignored -> {});
        assertEquals("pre-event", ref.label());
        assertEquals("world", ref.world());
        SnapshotService.SnapshotRef loaded = service.info(ref.path().getFileName().toString());
        assertEquals("pre-event", loaded.label());
        assertEquals("world", loaded.world());
    }

    @Test
    void listFiltersByWorldNewestFirst() throws Exception {
        Path worldA = worldWith("level.dat", "a");
        Path worldB = temp.resolve("other");
        Files.createDirectories(worldB);
        Files.writeString(worldB.resolve("level.dat"), "b");
        SnapshotService service = service(10);
        service.create("alpha", worldA, null, recordingAutosave("alpha"), plentySpace(), ignored -> {});
        Thread.sleep(5);
        service.create("beta", worldB, null, recordingAutosave("beta"), plentySpace(), ignored -> {});
        Thread.sleep(5);
        service.create("alpha", worldA, "later", recordingAutosave("alpha"), plentySpace(), ignored -> {});
        List<SnapshotService.SnapshotRef> alpha = service.list("alpha");
        assertEquals(2, alpha.size());
        assertTrue(alpha.get(0).path().getFileName().toString()
                .compareTo(alpha.get(1).path().getFileName().toString()) > 0);
    }

    @Test
    void reportsProgressByBytes() throws Exception {
        Path world = worldWith("level.dat", "0123456789");
        SnapshotService service = service(5);
        List<Long> copied = new ArrayList<>();
        service.create(
                "world",
                world,
                null,
                recordingAutosave("world"),
                plentySpace(),
                progress -> copied.add(progress.copiedBytes()));
        assertFalse(copied.isEmpty());
        assertEquals(copied.getLast(), copied.stream().mapToLong(Long::longValue).max().orElse(0));
    }

    private SnapshotService service(int retain) {
        return new SnapshotService(temp.resolve("snaps"), retain, Set.of("session.lock"));
    }

    private Path worldWith(String name, String content) throws IOException {
        Path world = temp.resolve("world-" + name.hashCode());
        Files.createDirectories(world);
        Files.writeString(world.resolve(name), content);
        return world;
    }

    private static SnapshotService.AutosaveControl recordingAutosave(String world) {
        return new SnapshotService.AutosaveControl() {
            private boolean enabled = true;

            @Override
            public boolean isAutoSave(String worldName) {
                assertEquals(world, worldName);
                return enabled;
            }

            @Override
            public void setAutoSave(String worldName, boolean value) {
                enabled = value;
            }
        };
    }

    private static SnapshotService.FreeSpace plentySpace() {
        return path -> Long.MAX_VALUE / 4;
    }

    private static void awaitBusy(SnapshotService service, String world) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (service.isSnapshotInProgress(world)) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("snapshot did not start");
    }
}
