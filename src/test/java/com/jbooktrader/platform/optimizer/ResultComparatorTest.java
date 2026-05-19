package com.jbooktrader.platform.optimizer;

import com.jbooktrader.platform.performance.PerformanceManager;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link ResultComparator}.
 *
 * Notable: the comparator currently sorts every metric descending. For
 * "lower is better" metrics like MaxDD and MaxSL that's the wrong direction;
 * the optimizer surfaces the worst parameter sets at the top. The
 * "lowerIsBetter" tests below are expected to FAIL today; they document the
 * fix that is needed.
 *
 * Tied to CODE_REVIEW.md §1.1.
 */
public class ResultComparatorTest {

    private static OptimizationResult resultWith(PerformanceMetric metric, double value) {
        PerformanceManager pm = Mockito.mock(PerformanceManager.class);
        // Build with mocked manager — the constructor reads many getters; default
        // mocks return 0 for primitives which is fine for tests that override
        // the relevant getter via the OptimizationResult's `get(metric)` method.
        OptimizationResult r = new OptimizationResult(new StrategyParams(), pm);
        // We can't post-mutate r's fields (they're final). Instead use the
        // PerformanceManager getter that maps to the chosen metric.
        switch (metric) {
            case NetProfit:
                Mockito.when(pm.getNetProfit()).thenReturn(value);
                break;
            case OG:
                Mockito.when(pm.getOptimalGrowth()).thenReturn(value);
                break;
            case PI:
                Mockito.when(pm.getPI()).thenReturn(value);
                break;
            case APD:
                Mockito.when(pm.getAPD()).thenReturn(value);
                break;
            case MaxSL:
                Mockito.when(pm.getMaxSingleLoss()).thenReturn(value);
                break;
            case MaxDD:
                Mockito.when(pm.getMaxDrawdown()).thenReturn(value);
                break;
            case Trades:
                Mockito.when(pm.getTrades()).thenReturn((int) value);
                break;
            case Duration:
                Mockito.when(pm.getAveDuration()).thenReturn(value);
                break;
        }
        // Rebuild after the mock is set so the fields are populated from the
        // configured mock.
        return new OptimizationResult(new StrategyParams(), pm);
    }

    @Test
    public void netProfit_sorts_descending() {
        ResultComparator cmp = new ResultComparator(PerformanceMetric.NetProfit);
        List<OptimizationResult> results = new ArrayList<>();
        results.add(resultWith(PerformanceMetric.NetProfit, 100));
        results.add(resultWith(PerformanceMetric.NetProfit, 300));
        results.add(resultWith(PerformanceMetric.NetProfit, 200));
        results.sort(cmp);
        assertEquals(300d, results.get(0).get(PerformanceMetric.NetProfit), 0);
        assertEquals(200d, results.get(1).get(PerformanceMetric.NetProfit), 0);
        assertEquals(100d, results.get(2).get(PerformanceMetric.NetProfit), 0);
    }

    @Test
    public void og_sorts_descending() {
        ResultComparator cmp = new ResultComparator(PerformanceMetric.OG);
        List<OptimizationResult> results = new ArrayList<>();
        results.add(resultWith(PerformanceMetric.OG, 1.0));
        results.add(resultWith(PerformanceMetric.OG, 5.0));
        results.add(resultWith(PerformanceMetric.OG, 3.0));
        results.sort(cmp);
        assertEquals(5.0, results.get(0).get(PerformanceMetric.OG), 0);
    }

    /**
     * BUG CR §1.1: MaxDD should sort ascending — small drawdown is better
     * than a big one. Today's comparator sorts descending for every metric,
     * so the worst strategies bubble to the top. Expected to FAIL until
     * the comparator (or a per-metric direction hint) is added.
     */
    @Test
    public void maxDD_should_sort_ascending_lowerIsBetter() {
        ResultComparator cmp = new ResultComparator(PerformanceMetric.MaxDD);
        List<OptimizationResult> results = new ArrayList<>();
        results.add(resultWith(PerformanceMetric.MaxDD, 100));
        results.add(resultWith(PerformanceMetric.MaxDD, 50));
        results.add(resultWith(PerformanceMetric.MaxDD, 500));
        results.sort(cmp);
        assertEquals("smallest drawdown should be ranked first",
                50d, results.get(0).get(PerformanceMetric.MaxDD), 0);
        assertEquals(100d, results.get(1).get(PerformanceMetric.MaxDD), 0);
        assertEquals(500d, results.get(2).get(PerformanceMetric.MaxDD), 0);
    }

    /**
     * BUG CR §1.1: MaxSL (max single loss) — same idea.
     */
    @Test
    public void maxSL_should_sort_ascending_lowerIsBetter() {
        ResultComparator cmp = new ResultComparator(PerformanceMetric.MaxSL);
        List<OptimizationResult> results = new ArrayList<>();
        results.add(resultWith(PerformanceMetric.MaxSL, 1000));
        results.add(resultWith(PerformanceMetric.MaxSL, 100));
        results.add(resultWith(PerformanceMetric.MaxSL, 10000));
        results.sort(cmp);
        assertEquals("smallest single loss should be ranked first",
                100d, results.get(0).get(PerformanceMetric.MaxSL), 0);
    }

    @Test
    public void equal_values_compare_as_zero() {
        ResultComparator cmp = new ResultComparator(PerformanceMetric.NetProfit);
        OptimizationResult a = resultWith(PerformanceMetric.NetProfit, 42);
        OptimizationResult b = resultWith(PerformanceMetric.NetProfit, 42);
        assertEquals(0, cmp.compare(a, b));
    }

    @Test
    public void NaN_handled_deterministically() {
        ResultComparator cmp = new ResultComparator(PerformanceMetric.OG);
        OptimizationResult nan = resultWith(PerformanceMetric.OG, Double.NaN);
        OptimizationResult one = resultWith(PerformanceMetric.OG, 1);
        // Double.compare treats NaN as the largest value. With our descending
        // ordering, NaN should appear before 1 (i.e. compare returns negative).
        int sign = (int) Math.signum(cmp.compare(nan, one));
        assertEquals(-1, sign);
    }
}
