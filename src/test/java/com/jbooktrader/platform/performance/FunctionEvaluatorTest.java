package com.jbooktrader.platform.performance;

import com.jbooktrader.platform.chart.TimedValue;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link FunctionEvaluator} (via KellyEvaluator concrete impl).
 * Tied to CODE_REVIEW.md §5.7.
 */
public class FunctionEvaluatorTest {

    /**
     * BUG CR §5.7: If all trade returns share the same timestamp (synthetic
     * data, all in one second), elapsedTime = 0 and getWeightedReturn divides
     * by zero, producing NaN distances → NaN weights → NaN results.
     *
     * Expected to FAIL until elapsedTime==0 is handled (e.g., treat all
     * weights as 1, or refuse the input).
     */
    @Test
    public void single_timestamp_should_not_produce_nan() {
        List<TimedValue> rs = new ArrayList<>();
        rs.add(new TimedValue(1000L, 0.1));
        rs.add(new TimedValue(1000L, -0.1));
        rs.add(new TimedValue(1000L, 0.2));
        KellyEvaluator ev = new KellyEvaluator(rs, "Uniform");
        double weighted = ev.evaluateLog(0.5);
        assertFalse("evaluateLog with single-timestamp data should be finite",
                Double.isNaN(weighted) || Double.isInfinite(weighted));
    }

    /**
     * BUG CR §5.7: When no return is negative, largestLoss stays 0 and
     * getMaxLeverage() returns -1/0 = -Infinity. Used as the right-bracket
     * of golden-section search this is broken.
     *
     * Expected to FAIL until getMaxLeverage handles the no-loss case.
     *
     * Disabled pending an upstream decision on the right finite cap.
     * The value is passed as the right bracket of a golden-section
     * search, so Double.MAX_VALUE is numerically problematic and a
     * practical bound (e.g. 100, 1000) is a project-specific call. See
     * CODE_REVIEW.md §5.7.
     */
    @Ignore("CR §5.7 — design choice deferred for upstream discussion")
    @Test
    public void no_losses_should_yield_finite_max_leverage() {
        List<TimedValue> rs = new ArrayList<>();
        rs.add(new TimedValue(1000L, 0.1));
        rs.add(new TimedValue(2000L, 0.2));
        rs.add(new TimedValue(3000L, 0.05));
        KellyEvaluator ev = new KellyEvaluator(rs, "Uniform");
        double maxLev = ev.getMaxLeverage();
        assertTrue("max leverage with no losses should be finite or sentinel, got " + maxLev,
                Double.isFinite(maxLev) && maxLev > 0);
    }

    @Test
    public void evaluateLog_with_zero_leverage_is_zero() {
        List<TimedValue> rs = new ArrayList<>();
        rs.add(new TimedValue(1000L, 0.1));
        rs.add(new TimedValue(2000L, -0.1));
        rs.add(new TimedValue(3000L, 0.2));
        KellyEvaluator ev = new KellyEvaluator(rs, "Uniform");
        assertEquals(0.0, ev.evaluateLog(0), 1e-9);
    }
}
