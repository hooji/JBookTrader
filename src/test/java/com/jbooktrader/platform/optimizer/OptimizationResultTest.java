package com.jbooktrader.platform.optimizer;

import com.jbooktrader.platform.performance.PerformanceManager;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

/**
 * Tests for {@link OptimizationResult}.
 */
public class OptimizationResultTest {

    @Test
    public void exposes_all_metrics() {
        PerformanceManager pm = Mockito.mock(PerformanceManager.class);
        Mockito.when(pm.getNetProfit()).thenReturn(1000.0);
        Mockito.when(pm.getMaxDrawdown()).thenReturn(200.0);
        Mockito.when(pm.getMaxSingleLoss()).thenReturn(50.0);
        Mockito.when(pm.getTrades()).thenReturn(42);
        Mockito.when(pm.getOptimalGrowth()).thenReturn(3.0);
        Mockito.when(pm.getPI()).thenReturn(2.0);
        Mockito.when(pm.getAPD()).thenReturn(1.5);
        Mockito.when(pm.getAveDuration()).thenReturn(60.0);

        OptimizationResult r = new OptimizationResult(new StrategyParams(), pm);

        assertEquals(1000.0, r.get(PerformanceMetric.NetProfit), 0);
        assertEquals(200.0,  r.get(PerformanceMetric.MaxDD), 0);
        assertEquals(50.0,   r.get(PerformanceMetric.MaxSL), 0);
        assertEquals(42.0,   r.get(PerformanceMetric.Trades), 0);
        assertEquals(3.0,    r.get(PerformanceMetric.OG), 0);
        assertEquals(2.0,    r.get(PerformanceMetric.PI), 0);
        assertEquals(1.5,    r.get(PerformanceMetric.APD), 0);
        assertEquals(60.0,   r.get(PerformanceMetric.Duration), 0);
    }

    @Test
    public void NaN_propagates_via_get() {
        PerformanceManager pm = Mockito.mock(PerformanceManager.class);
        Mockito.when(pm.getNetProfit()).thenReturn(Double.NaN);
        OptimizationResult r = new OptimizationResult(new StrategyParams(), pm);
        assertTrue("NaN propagates from PerformanceManager into the result",
                Double.isNaN(r.get(PerformanceMetric.NetProfit)));
    }

    @Test
    public void params_are_stored() {
        PerformanceManager pm = Mockito.mock(PerformanceManager.class);
        StrategyParams params = new StrategyParams();
        params.add("a", 0, 10, 1, 5);
        OptimizationResult r = new OptimizationResult(params, pm);
        assertSame(params, r.getParams());
    }
}
