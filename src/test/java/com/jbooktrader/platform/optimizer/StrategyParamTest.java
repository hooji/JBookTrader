package com.jbooktrader.platform.optimizer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link StrategyParam}. Tied to CODE_REVIEW.md §5.5.
 *
 * Several tests document missing validation in setters: setting step=0
 * should be rejected, setMin > existing max should be rejected. Today
 * none of these are checked; expected to FAIL.
 */
public class StrategyParamTest {

    @Test
    public void construction_sets_all_fields() {
        StrategyParam p = new StrategyParam("foo", 1, 10, 2, 5);
        assertEquals("foo", p.getName());
        assertEquals(1, p.getMin());
        assertEquals(10, p.getMax());
        assertEquals(2, p.getStep());
        assertEquals(5, p.getValue());
    }

    @Test
    public void copy_constructor_copies_all_fields() {
        StrategyParam src = new StrategyParam("foo", 1, 10, 2, 5);
        StrategyParam dst = new StrategyParam(src);
        assertNotSame(src, dst);
        assertEquals(src.getName(), dst.getName());
        assertEquals(src.getMin(), dst.getMin());
        assertEquals(src.getMax(), dst.getMax());
        assertEquals(src.getStep(), dst.getStep());
        assertEquals(src.getValue(), dst.getValue());
    }

    @Test
    public void getRange_returns_max_minus_min() {
        StrategyParam p = new StrategyParam("foo", 5, 12, 1, 10);
        assertEquals(7, p.getRange());
    }

    @Test
    public void getMiddle_returns_midpoint() {
        StrategyParam p = new StrategyParam("foo", 10, 20, 1, 15);
        assertEquals(15.0, p.getMiddle(), 0);
    }

    /**
     * BUG CR §5.5: setStep(0) is accepted silently. The optimizer's
     * `for (value = min; value <= max; value += step)` loop then never
     * terminates. Expected to FAIL until validation is added.
     */
    @Test
    public void setStep_zero_should_be_rejected() {
        StrategyParam p = new StrategyParam("foo", 1, 10, 1, 5);
        try {
            p.setStep(0);
            fail("setStep(0) should throw — would cause an infinite loop in the optimizer");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    /**
     * BUG CR §5.5: setStep(-1) — same.
     */
    @Test
    public void setStep_negative_should_be_rejected() {
        StrategyParam p = new StrategyParam("foo", 1, 10, 1, 5);
        try {
            p.setStep(-5);
            fail("setStep(<0) should throw");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    /**
     * BUG CR §5.5: setMin(value) where value > current max should be
     * rejected. Today it silently produces a param with min > max,
     * which yields negative getRange() and an empty cartesian product
     * downstream.
     */
    @Test
    public void setMin_above_max_should_be_rejected() {
        StrategyParam p = new StrategyParam("foo", 1, 10, 1, 5);
        try {
            p.setMin(20);
            fail("setMin > current max should throw");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    /**
     * BUG CR §5.5: setMax(value) where value < current min — same.
     */
    @Test
    public void setMax_below_min_should_be_rejected() {
        StrategyParam p = new StrategyParam("foo", 5, 10, 1, 7);
        try {
            p.setMax(1);
            fail("setMax < current min should throw");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    /**
     * BUG CR §5.5: getMiddle() of (Integer.MAX_VALUE, 1) overflows because
     * int addition happens before promotion to double. Should be calculated
     * in double-precision space.
     */
    @Test
    public void getMiddle_does_not_overflow_for_large_min_max() {
        StrategyParam p = new StrategyParam("foo", 1, Integer.MAX_VALUE, 1, 100);
        double expected = (1.0 + Integer.MAX_VALUE) / 2.0;
        assertEquals("getMiddle should not overflow for extreme ranges",
                expected, p.getMiddle(), 1.0);
    }

    @Test
    public void toString_is_legible() {
        StrategyParam p = new StrategyParam("foo", 1, 10, 2, 5);
        String s = p.toString();
        assertTrue("toString should include name", s.contains("foo"));
        assertTrue("toString should include min", s.contains("1"));
        assertTrue("toString should include max", s.contains("10"));
    }
}
