package dev.nubstack.spotlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LagReportTest {

    private static final ChunkWeights WEIGHTS = new ChunkWeights(1, 8, 4, 2, 2, 3);

    private static LagReport.ChunkCost cost(String world, int x, int z, ChunkCounts counts) {
        return new LagReport.ChunkCost(world, x, z, counts, counts.score(WEIGHTS));
    }

    private static ChunkCounts entities(int n) {
        return new ChunkCounts(n, 0, 0, 0, 0, 0);
    }

    @Test
    void ordersByScore() {
        LagReport report = new LagReport();
        report.add(cost("world", 0, 0, entities(1)));
        report.add(cost("world", 1, 1, new ChunkCounts(50, 10, 0, 0, 0, 0)));
        assertEquals(130, report.top(1).getFirst().score());
    }

    @Test
    void explainMatchesSpecExample() {
        ChunkCounts counts = new ChunkCounts(40, 6, 0, 0, 0, 0);
        assertEquals(88, counts.score(WEIGHTS));
        assertEquals("40 entities x1 + 6 hoppers x8 = 88", counts.explain(WEIGHTS));
    }

    @Test
    void explainIncludesExtraWeightsWhenNonZero() {
        ChunkCounts counts = new ChunkCounts(10, 1, 2, 3, 1, 4);
        String explain = counts.explain(WEIGHTS);
        assertTrue(explain.contains("10 entities x1"));
        assertTrue(explain.contains("1 hoppers x8"));
        assertTrue(explain.contains("2 minecarts x4"));
        assertTrue(explain.contains("3 redstone x2"));
        assertTrue(explain.contains("1 item frames x2"));
        assertTrue(explain.contains("4 villagers x3"));
        assertEquals(counts.score(WEIGHTS) + "", explain.substring(explain.lastIndexOf('=') + 2));
    }

    @Test
    void explainOmitsZeroExtras() {
        String explain = entities(3).explain(WEIGHTS);
        assertEquals("3 entities x1 = 3", explain);
    }

    @Test
    void zeroWeightContributesNothing() {
        ChunkWeights off = new ChunkWeights(1, 0, 0, 0, 0, 0);
        assertEquals(5, new ChunkCounts(5, 9, 9, 9, 9, 9).score(off));
    }

    @Test
    void topRespectsLimit() {
        LagReport report = new LagReport();
        for (int i = 0; i < 5; i++) {
            report.add(cost("world", i, 0, entities(i + 1)));
        }
        List<LagReport.ChunkCost> top = report.top(2);
        assertEquals(2, top.size());
        assertEquals(5, top.get(0).score());
        assertEquals(4, top.get(1).score());
    }

    @Test
    void topWithNonPositiveLimitIsEmpty() {
        LagReport report = new LagReport();
        report.add(cost("world", 0, 0, entities(1)));
        assertTrue(report.top(0).isEmpty());
        assertTrue(report.top(-3).isEmpty());
    }

    @Test
    void atRankIsOneBasedAndBounded() {
        LagReport report = new LagReport();
        report.add(cost("world", 0, 0, entities(1)));
        report.add(cost("world", 1, 0, entities(9)));
        assertEquals(1, report.atRank(1).chunkX());
        assertEquals(0, report.atRank(2).chunkX());
        assertNull(report.atRank(0));
        assertNull(report.atRank(3));
    }

    @Test
    void rankOfUsesFullOrdering() {
        LagReport report = new LagReport();
        LagReport.ChunkCost low = cost("world", 0, 0, entities(1));
        LagReport.ChunkCost high = cost("world", 1, 0, entities(9));
        report.add(low);
        report.add(high);
        assertEquals(1, report.rankOf(high));
        assertEquals(2, report.rankOf(low));
        assertEquals(-1, report.rankOf(cost("other", 5, 5, entities(1))));
    }

    @Test
    void nearFiltersByWorldAndChebyshevRadius() {
        LagReport report = new LagReport();
        report.add(cost("world", 0, 0, entities(1)));
        report.add(cost("world", 2, 2, entities(5)));
        report.add(cost("world", 3, 0, entities(9)));
        report.add(cost("nether", 0, 0, entities(20)));
        List<LagReport.ChunkCost> near = report.near("world", 0, 0, 2, 10);
        assertEquals(2, near.size());
        assertEquals(5, near.get(0).score());
        assertEquals(1, near.get(1).score());
    }
}
