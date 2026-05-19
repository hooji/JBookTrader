package com.jbooktrader.platform.optimizer;

import com.jbooktrader.platform.performance.PerformanceManager;
import com.jbooktrader.platform.preferences.JBTPreferences;
import com.jbooktrader.platform.preferences.PreferencesHolder;
import com.jbooktrader.platform.util.TestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for the centroid / gradient optimizer's getCentroid() path.
 * Tied to CODE_REVIEW.md §5.2 and §5.3.
 *
 * Both runners have edge cases that produce NaN centroids: when
 * sumOfPerformance is 0 (no positive results), when range = max - min is 0
 * (all results have the same metric), and when the cutoff parameter
 * rounds to 0 (very few results).
 *
 * The runners' constructors are heavy (require a Strategy whose class has
 * a (StrategyParams) constructor, plus an OptimizerDialog). We use
 * reflection to call getCentroid() without going through optimize() — this
 * isolates the math from the orchestration.
 */
public class CentroidGradientOptimizerRunnerTest {

    private String savedCoverage;

    @BeforeClass
    public static void initDispatcher() {
        TestSupport.ensureInitialised();
    }

    @Before
    public void savePrefs() {
        savedCoverage = PreferencesHolder.getInstance().get(JBTPreferences.DivideAndConquerCoverage);
    }

    @After
    public void restorePrefs() {
        PreferencesHolder.getInstance().set(JBTPreferences.DivideAndConquerCoverage, savedCoverage);
    }

    /**
     * Build a fully-mocked CentroidOptimizerRunner without invoking its
     * constructor.  We then inject the necessary fields so that
     * getCentroid() can run on a controlled `optimizationResults` list.
     */
    private static CentroidOptimizerRunner makeCentroidWithoutInit() throws Exception {
        // Use sun.misc.Unsafe via reflection to bypass the constructor.
        Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
        Field f = unsafeCls.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method alloc = unsafeCls.getMethod("allocateInstance", Class.class);
        return (CentroidOptimizerRunner) alloc.invoke(unsafe, CentroidOptimizerRunner.class);
    }

    private static GradientOptimizerRunner makeGradientWithoutInit() throws Exception {
        Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
        Field f = unsafeCls.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method alloc = unsafeCls.getMethod("allocateInstance", Class.class);
        return (GradientOptimizerRunner) alloc.invoke(unsafe, GradientOptimizerRunner.class);
    }

    /**
     * Reflectively set a final field on an object.
     */
    private static void setField(Object obj, String name, Object value) throws Exception {
        Class<?> c = obj.getClass();
        Field f = null;
        while (c != null && f == null) {
            try {
                f = c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        if (f == null) throw new NoSuchFieldException(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private static OptimizationResult result(double metricValue, PerformanceMetric pm, StrategyParams params) {
        PerformanceManager performanceManager = Mockito.mock(PerformanceManager.class);
        Mockito.when(performanceManager.getOptimalGrowth()).thenReturn(pm == PerformanceMetric.OG ? metricValue : 0);
        Mockito.when(performanceManager.getNetProfit()).thenReturn(pm == PerformanceMetric.NetProfit ? metricValue : 0);
        return new OptimizationResult(params, performanceManager);
    }

    private static StrategyParams threeParams(int aMid, int bMid, int cMid) {
        StrategyParams p = new StrategyParams();
        p.add("a", 0, 100, 1, aMid);
        p.add("b", 0, 100, 1, bMid);
        p.add("c", 0, 100, 1, cMid);
        return p;
    }

    private static double[] callGetCentroid(Object runner) throws Exception {
        Method m = runner.getClass().getDeclaredMethod("getCentroid");
        m.setAccessible(true);
        return (double[]) m.invoke(runner);
    }

    /**
     * BUG CR §5.2: For Centroid, if no result has performanceValue > 0,
     * sumOfPerformance stays 0 and `centroid[i] /= sumOfPerformance` yields
     * NaN for every parameter. Expected to FAIL until guarded.
     */
    @Test
    public void centroid_with_all_zero_or_negative_metrics_should_not_be_NaN() throws Exception {
        CentroidOptimizerRunner r = makeCentroidWithoutInit();
        setField(r, "dimensions", 3);
        setField(r, "startingParams", threeParams(50, 50, 50));
        setField(r, "performanceMetric", PerformanceMetric.OG);
        setField(r, "strictParameterBounds", false);

        // Make optimizationResults non-empty with all-zero metric values.
        List<OptimizationResult> results = new ArrayList<>();
        results.add(result(0, PerformanceMetric.OG, threeParams(10, 20, 30)));
        results.add(result(0, PerformanceMetric.OG, threeParams(40, 50, 60)));
        results.add(result(0, PerformanceMetric.OG, threeParams(70, 80, 90)));
        setField(r, "optimizationResults", new java.util.concurrent.CopyOnWriteArrayList<>(results));

        double[] centroid = callGetCentroid(r);
        for (int i = 0; i < centroid.length; i++) {
            assertFalse("centroid[" + i + "] should not be NaN; got " + centroid[i],
                    Double.isNaN(centroid[i]));
        }
    }

    /**
     * BUG CR §5.2 (cutoff): If `optimizationResults.size() * (2 - goldenRatio)`
     * rounds to 0 (e.g. 1 result), the inner loop is skipped and
     * sumOfPerformance stays 0 → NaN centroid. Expected to FAIL.
     */
    @Test
    public void centroid_with_one_result_should_not_be_NaN() throws Exception {
        CentroidOptimizerRunner r = makeCentroidWithoutInit();
        setField(r, "dimensions", 3);
        setField(r, "startingParams", threeParams(50, 50, 50));
        setField(r, "performanceMetric", PerformanceMetric.OG);
        setField(r, "strictParameterBounds", false);
        List<OptimizationResult> results = new ArrayList<>();
        results.add(result(123.4, PerformanceMetric.OG, threeParams(10, 20, 30)));
        setField(r, "optimizationResults", new java.util.concurrent.CopyOnWriteArrayList<>(results));

        double[] centroid = callGetCentroid(r);
        for (int i = 0; i < centroid.length; i++) {
            assertFalse("centroid[" + i + "] should not be NaN with one result; got " + centroid[i],
                    Double.isNaN(centroid[i]));
        }
    }

    /**
     * BUG CR §5.3: For Gradient, if max == min (all results have the same
     * metric value), `range` is 0 and `x = (value - min) / range` yields
     * NaN. Expected to FAIL until guarded.
     */
    @Test
    public void gradient_with_uniform_metric_should_not_be_NaN() throws Exception {
        GradientOptimizerRunner r = makeGradientWithoutInit();
        setField(r, "dimensions", 3);
        setField(r, "startingParams", threeParams(50, 50, 50));
        setField(r, "performanceMetric", PerformanceMetric.OG);
        setField(r, "strictParameterBounds", false);

        List<OptimizationResult> results = new ArrayList<>();
        results.add(result(5.0, PerformanceMetric.OG, threeParams(10, 20, 30)));
        results.add(result(5.0, PerformanceMetric.OG, threeParams(40, 50, 60)));
        results.add(result(5.0, PerformanceMetric.OG, threeParams(70, 80, 90)));
        setField(r, "optimizationResults", new java.util.concurrent.CopyOnWriteArrayList<>(results));

        double[] centroid = callGetCentroid(r);
        for (int i = 0; i < centroid.length; i++) {
            assertFalse("gradient centroid[" + i + "] should not be NaN with uniform metric; got " + centroid[i],
                    Double.isNaN(centroid[i]));
        }
    }

    /**
     * BUG CR §5.3: With no positive-and-above-min metric values,
     * sumOfPerformance stays 0 in the gradient runner. Expected to FAIL.
     */
    @Test
    public void gradient_with_all_zero_metrics_should_not_be_NaN() throws Exception {
        GradientOptimizerRunner r = makeGradientWithoutInit();
        setField(r, "dimensions", 3);
        setField(r, "startingParams", threeParams(50, 50, 50));
        setField(r, "performanceMetric", PerformanceMetric.OG);
        setField(r, "strictParameterBounds", false);

        List<OptimizationResult> results = new ArrayList<>();
        results.add(result(0, PerformanceMetric.OG, threeParams(10, 20, 30)));
        results.add(result(0, PerformanceMetric.OG, threeParams(40, 50, 60)));
        setField(r, "optimizationResults", new java.util.concurrent.CopyOnWriteArrayList<>(results));

        double[] centroid = callGetCentroid(r);
        for (int i = 0; i < centroid.length; i++) {
            assertFalse("gradient centroid[" + i + "] should not be NaN with zero metrics; got " + centroid[i],
                    Double.isNaN(centroid[i]));
        }
    }

    /**
     * Sanity check: empty results yields a centroid at the midpoints of
     * the starting params (well-defined fallback path).
     */
    @Test
    public void centroid_with_no_results_uses_midpoint_of_starting_params() throws Exception {
        CentroidOptimizerRunner r = makeCentroidWithoutInit();
        setField(r, "dimensions", 3);
        setField(r, "startingParams", threeParams(50, 50, 50));
        setField(r, "performanceMetric", PerformanceMetric.OG);
        setField(r, "strictParameterBounds", false);
        setField(r, "optimizationResults", new java.util.concurrent.CopyOnWriteArrayList<>());

        double[] centroid = callGetCentroid(r);
        for (int i = 0; i < centroid.length; i++) {
            assertEquals(50.0, centroid[i], 0.5);
        }
    }
}
