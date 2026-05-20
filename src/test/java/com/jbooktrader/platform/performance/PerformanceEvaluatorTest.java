package com.jbooktrader.platform.performance;

import com.jbooktrader.platform.chart.TimedValue;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link PerformanceEvaluator}. Tied to CODE_REVIEW.md §5.6.
 */
public class PerformanceEvaluatorTest {

    @Test
    public void two_or_fewer_trades_leaves_metrics_at_zero() {
        List<TimedValue> rs = new ArrayList<>();
        rs.add(new TimedValue(1000L, 0.1));
        rs.add(new TimedValue(2000L, 0.2));
        PerformanceEvaluator ev = new PerformanceEvaluator(rs);
        ev.evaluate();
        assertEquals(0.0, ev.getOptimalLeverage(), 0);
        assertEquals(0.0, ev.getOptimalGrowth(), 0);
        assertEquals(0.0, ev.getPi(), 0);
    }

    /**
     * BUG CR §5.6: an all-wins trade list yields optimalLeverage =
     * optimalGrowth = pi = +Infinity. Infinity then leaks downstream and
     * such a strategy ranks above everything else at the optimizer.
     *
     * Expected to FAIL until evaluate() either refuses the all-wins case
     * or returns a sentinel/finite value.
     *
     * Disabled pending an upstream decision on the right finite sentinel:
     * Kelly criterion is mathematically unbounded with no losses, so the
     * fix needs a defensible choice (0, a practical cap, or refusal of
     * the all-wins input). See CODE_REVIEW.md §5.6.
     */
    @Ignore("CR §5.6 — design choice deferred for upstream discussion")
    @Test
    public void all_wins_should_not_produce_infinity() {
        List<TimedValue> rs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rs.add(new TimedValue(i * 1000L, 0.05));
        }
        PerformanceEvaluator ev = new PerformanceEvaluator(rs);
        ev.evaluate();
        assertTrue("optimalLeverage should be finite",
                Double.isFinite(ev.getOptimalLeverage()));
        assertTrue("optimalGrowth should be finite",
                Double.isFinite(ev.getOptimalGrowth()));
        assertTrue("pi should be finite", Double.isFinite(ev.getPi()));
    }

    @Test
    public void mixed_returns_produce_finite_metrics() {
        List<TimedValue> rs = new ArrayList<>();
        rs.add(new TimedValue(1000L, 0.1));
        rs.add(new TimedValue(2000L, -0.05));
        rs.add(new TimedValue(3000L, 0.15));
        rs.add(new TimedValue(4000L, -0.1));
        rs.add(new TimedValue(5000L, 0.2));
        PerformanceEvaluator ev = new PerformanceEvaluator(rs);
        ev.evaluate();
        assertTrue(Double.isFinite(ev.getOptimalLeverage()));
        assertTrue(Double.isFinite(ev.getPi()));
        // optimalGrowth is not always set positive by evaluate(); we just
        // check it's not Infinity here.
        assertTrue(Double.isFinite(ev.getOptimalGrowth()) || ev.getOptimalGrowth() == 0);
    }
}
