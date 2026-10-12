package dev.nubstack.cairn.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraveRegistryTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void keysByDimensionAndPos_doNotCollideAcrossWorlds() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(newGrave("a", "minecraft:overworld", 10, 64, 10, 100));
        registry.add(newGrave("b", "minecraft:the_nether", 10, 64, 10, 100));
        assertEquals(2, registry.all().size());
        assertTrue(registry.find(new GraveKey("minecraft:overworld", 10, 64, 10)).isPresent());
        assertTrue(registry.find(new GraveKey("minecraft:the_nether", 10, 64, 10)).isPresent());
    }

    @Test
    void tickDimension_expiresOnlyMatchingWorld() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(newGrave("ow", "minecraft:overworld", 1, 64, 1, 1));
        registry.add(newGrave("ne", "minecraft:the_nether", 1, 64, 1, 50));
        List<GraveRecord> expired = registry.tickDimension("minecraft:overworld", 1);
        assertEquals(1, expired.size());
        assertEquals("ow", expired.getFirst().id());
        assertTrue(registry.find(new GraveKey("minecraft:the_nether", 1, 64, 1)).isPresent());
        assertTrue(registry.find(new GraveKey("minecraft:overworld", 1, 64, 1)).isEmpty());
    }

    @Test
    void enforceMaxGraves_evictsOldest() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(newGrave("g1", "minecraft:overworld", 1, 64, 1, 100));
        registry.add(newGrave("g2", "minecraft:overworld", 2, 64, 2, 100));
        registry.add(newGrave("g3", "minecraft:overworld", 3, 64, 3, 100));
        List<GraveRecord> evicted = new ArrayList<>();
        registry.enforceMaxGraves(OWNER, 2, evicted::add);
        assertEquals(1, evicted.size());
        assertEquals("g1", evicted.getFirst().id());
        assertEquals(2, registry.listFor(OWNER).size());
    }

    @Test
    void snapshotAndLoad_roundTrips() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(newGrave("g1", "minecraft:overworld", 5, 70, -3, 40));
        List<GraveRecord> snap = registry.snapshot();
        GraveRegistry restored = new GraveRegistry();
        restored.load(snap);
        Optional<GraveRecord> found = restored.find(new GraveKey("minecraft:overworld", 5, 70, -3));
        assertTrue(found.isPresent());
        assertEquals("g1", found.get().id());
        assertEquals(40, found.get().ticksRemaining());
    }

    @Test
    void remove_clearsLocationIndex() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(newGrave("g1", "minecraft:overworld", 0, 64, 0, 10));
        assertTrue(registry.remove("g1"));
        assertFalse(registry.remove("g1"));
        assertTrue(registry.find(new GraveKey("minecraft:overworld", 0, 64, 0)).isEmpty());
    }

    @Test
    void nextId_isOnePastHighestNumericId() {
        GraveRegistry registry = new GraveRegistry();
        assertEquals("1", registry.nextId());
        registry.add(newGrave("7", "minecraft:overworld", 1, 64, 1, 10));
        registry.add(newGrave("custom", "minecraft:overworld", 2, 64, 2, 10));
        assertEquals("8", registry.nextId());
    }

    @Test
    void expire_forcesNextTickToExpireOnlyThatGrave() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(newGrave("1", "minecraft:overworld", 1, 64, 1, 500));
        registry.add(newGrave("2", "minecraft:overworld", 2, 64, 2, 500));
        assertTrue(registry.expire("1"));
        assertFalse(registry.expire("nope"));
        List<GraveRecord> expired = registry.tickDimension("minecraft:overworld", 1);
        assertEquals(List.of("1"), expired.stream().map(GraveRecord::id).toList());
    }

    @Test
    void listForName_ignoresCase() {
        GraveRegistry registry = new GraveRegistry();
        registry.add(newGrave("1", "minecraft:overworld", 1, 64, 1, 10));
        assertEquals(1, registry.listForName("sTeVe").size());
        assertTrue(registry.listForName("Alex").isEmpty());
    }

    private static GraveRecord newGrave(String id, String dimension, int x, int y, int z, int ticks) {
        return new GraveRecord(id, OWNER, "Steve", dimension, x, y, z, ticks);
    }
}
