package com.jbooktrader.platform.chart;

import com.jbooktrader.platform.optimizer.OptimizationResult;
import com.jbooktrader.platform.optimizer.OptimizerDialog;
import com.jbooktrader.platform.optimizer.PerformanceMetric;
import com.jbooktrader.platform.optimizer.StrategyParams;
import com.jbooktrader.platform.performance.PerformanceManager;
import com.jbooktrader.platform.util.TestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link OptimizationMap}. Tied to CODE_REVIEW.md §1.19.
 *
 * Most of these tests reproduce edge cases that crash the constructor /
 * createChart. The constructor needs an OptimizerDialog (mocked here).
 */
public class OptimizationMapTest {

    @BeforeClass
    public static void initDispatcher() {
        TestSupport.ensureInitialised();
    }

    private static OptimizerDialog mockDialog(PerformanceMetric metric) {
        OptimizerDialog dialog = Mockito.mock(OptimizerDialog.class);
        Mockito.when(dialog.getPerformanceMetric()).thenReturn(metric);
        return dialog;
    }

    /** OptimizationResult's constructor is package-private; reach in. */
    private static OptimizationResult makeResult(StrategyParams params, PerformanceManager pm) {
        try {
            Constructor<OptimizationResult> c = OptimizationResult.class
                    .getDeclaredConstructor(StrategyParams.class, PerformanceManager.class);
            c.setAccessible(true);
            return c.newInstance(params, pm);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * BUG CR §1.19: An empty results list crashes the constructor at
     * line 81 (`optimizationResults.get(0).getParams()`). The map should
     * either render an empty state or refuse construction with a clear
     * message — not throw an unguarded IOOBE.
     *
     * Expected to FAIL until the empty case is handled.
     */
    @Test
    public void empty_results_should_not_crash() {
        OptimizerDialog dialog = mockDialog(PerformanceMetric.OG);
        List<OptimizationResult> empty = Collections.emptyList();
        try {
            new OptimizationMap(dialog, empty);
        } catch (IndexOutOfBoundsException e) {
            fail("empty results should be handled gracefully: " + e);
        } catch (RuntimeException expected) {
            // Any well-described RuntimeException is acceptable.
        }
    }

    /**
     * BUG CR §1.19: a strategy with only one parameter crashes at
     * `verticalCombo.setSelectedIndex(1)`. The "Vertical" combo has 0
     * items in this case and setSelectedIndex(1) throws.
     *
     * This test cannot construct the dialog without an EDT, but it
     * documents the issue. Marked as a candidate-test for after the EDT
     * machinery is decoupled.
     */
    @Test
    public void single_param_strategy_should_not_crash() {
        OptimizerDialog dialog = mockDialog(PerformanceMetric.OG);
        StrategyParams oneParam = new StrategyParams();
        oneParam.add("only", 0, 10, 1, 5);

        PerformanceManager pm = Mockito.mock(PerformanceManager.class);
        Mockito.when(pm.getOptimalGrowth()).thenReturn(1.0);

        List<OptimizationResult> results = new ArrayList<>();
        results.add(makeResult(oneParam, pm));
        try {
            new OptimizationMap(dialog, results);
        } catch (IllegalArgumentException e) {
            fail("single-parameter strategy should not crash the map: " + e);
        } catch (RuntimeException expected) {
            // Other reasonable RuntimeException is OK.
        }
    }

    /**
     * BUG CR §1.19: when every result has the same metric value, the
     * paint-scale computation does `(value - min) / (max - min)` = NaN
     * and `new Color(NaN, NaN, NaN)` throws.  Currently the constructor
     * fully renders the chart — so this test will crash from inside the
     * color computation.
     */
    @Test
    public void uniform_metric_value_should_not_crash() {
        OptimizerDialog dialog = mockDialog(PerformanceMetric.OG);

        StrategyParams params = new StrategyParams();
        params.add("a", 0, 10, 1, 5);
        params.add("b", 0, 10, 1, 5);

        List<OptimizationResult> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            PerformanceManager pm = Mockito.mock(PerformanceManager.class);
            Mockito.when(pm.getOptimalGrowth()).thenReturn(42.0); // all equal
            StrategyParams copy = new StrategyParams(params);
            copy.get("a").setValue(i);
            results.add(makeResult(copy, pm));
        }
        try {
            new OptimizationMap(dialog, results);
        } catch (IllegalArgumentException e) {
            fail("uniform metric should not crash paint scale: " + e);
        } catch (RuntimeException expected) {
            // OK if some other reasonable exception.
        }
    }
}
