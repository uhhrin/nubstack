
package dev.nubstack.spotlight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LagReportTest {
    @Test
    void ordersByScore() {
        LagReport report = new LagReport();
        report.add(new LagReport.ChunkCost("world", 0, 0, 1, 0, 1));
        report.add(new LagReport.ChunkCost("world", 1, 1, 50, 10, 150));
        assertEquals(150, report.top(1).getFirst().score());
    }
}
