package com.jbooktrader.platform.optimizer;

import org.junit.Test;

import static org.junit.Assert.*;

public class PerformanceMetricTest {

    @Test
    public void getColumn_known_names() {
        assertEquals(PerformanceMetric.NetProfit, PerformanceMetric.getColumn("Net Profit"));
        assertEquals(PerformanceMetric.OG, PerformanceMetric.getColumn("OG"));
        assertEquals(PerformanceMetric.PI, PerformanceMetric.getColumn("PI"));
        assertEquals(PerformanceMetric.APD, PerformanceMetric.getColumn("APD"));
        assertEquals(PerformanceMetric.MaxSL, PerformanceMetric.getColumn("MSL"));
        assertEquals(PerformanceMetric.MaxDD, PerformanceMetric.getColumn("MDD"));
        assertEquals(PerformanceMetric.Trades, PerformanceMetric.getColumn("Trades"));
        assertEquals(PerformanceMetric.Duration, PerformanceMetric.getColumn("Duration"));
    }

    @Test
    public void getColumn_unknown_name_returns_null() {
        // Today: silent null. A defensive implementation might throw.
        assertNull(PerformanceMetric.getColumn("Bogus"));
    }

    @Test
    public void each_metric_has_a_display_name() {
        for (PerformanceMetric pm : PerformanceMetric.values()) {
            assertNotNull(pm + " has null name", pm.getName());
            assertFalse(pm + " has empty name", pm.getName().isEmpty());
        }
    }
}
