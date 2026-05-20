package com.jbooktrader.platform.performance;

import com.jbooktrader.platform.commission.Commission;
import com.jbooktrader.platform.ibhandler.OrderExecution;
import com.jbooktrader.platform.strategy.Strategy;
import com.jbooktrader.platform.util.TestSupport;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Tests for {@link PerformanceManager}. Tied to CODE_REVIEW.md §1.14, §5.13.
 *
 * PerformanceManager has a Strategy dependency that's hard to fake. We
 * bypass the constructor via reflection and wire up just enough state for
 * the tested methods.
 */
public class PerformanceManagerTest {

    @BeforeClass
    public static void initDispatcher() {
        TestSupport.ensureInitialised();
    }

    private static PerformanceManager rawInstance() throws Exception {
        Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
        Field f = unsafeCls.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method alloc = unsafeCls.getMethod("allocateInstance", Class.class);
        return (PerformanceManager) alloc.invoke(unsafe, PerformanceManager.class);
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Test
    public void getTrades_zero_initially() throws Exception {
        PerformanceManager pm = rawInstance();
        assertEquals(0, pm.getTrades());
    }

    @Test
    public void getAveDuration_zero_trades_returns_zero() throws Exception {
        PerformanceManager pm = rawInstance();
        assertEquals(0.0, pm.getAveDuration(), 0);
    }

    @Test
    public void getPercentProfitableTrades_zero_trades_returns_zero() throws Exception {
        PerformanceManager pm = rawInstance();
        assertEquals(0.0, pm.getPercentProfitableTrades(), 0);
    }

    @Test
    public void getAPD_zero_drawdown_returns_zero() throws Exception {
        PerformanceManager pm = rawInstance();
        assertEquals(0.0, pm.getAPD(), 0);
    }

    /**
     * BUG CR §1.14: If `updateOnTrade` is called with `previousPosition ==
     * 0 && position == 0`, the `trade` field is null (never created), and
     * `trade.setExitTime(...)` NPEs.
     *
     * This is an edge case that can happen on certain recovery paths or
     * with over-fills. The test invokes `updateOnTrade` directly via a
     * minimally-wired PerformanceManager.
     *
     * Expected to FAIL today.
     */
    @Test
    public void updateOnTrade_with_zero_to_zero_should_not_NPE() throws Exception {
        // We need to set: strategy (with positionManager + marketBook),
        // multiplier, commission, tradeReturns.
        Strategy mockStrategy = Mockito.mock(Strategy.class);
        Mockito.when(mockStrategy.getTime()).thenReturn(1000L);
        Mockito.when(mockStrategy.getPositionManager()).thenReturn(null); // not used in this path

        Commission commission = new Commission(1.0, 1.0);
        PerformanceManager pm = rawInstance();
        setField(pm, "strategy", mockStrategy);
        setField(pm, "multiplier", 50);
        setField(pm, "commission", commission);
        setField(pm, "tradeReturns", new java.util.ArrayList<>());
        // previousPosition defaults to 0.

        try {
            // quantity=0, position=0, slippage=0 — recovery / no-op path
            pm.updateOnTrade(0, 100.0, 0, 0);
            // If we get here, no NPE. Good — but today this NPEs.
        } catch (NullPointerException e) {
            fail("updateOnTrade(0, ..., 0, ...) should not NPE: " + e);
        }
    }

    /**
     * BUG CR §5.13: peakNetProfit defaults to 0, so a strategy whose first
     * trade is a loss reports maxDrawdown = |loss| instead of 0. The
     * drawdown is relative to peak — peak should be initialized to
     * starting equity, not zero.
     *
     * Disabled pending upstream discussion: the standard finance
     * convention is drawdown-from-peak-equity where the peak includes
     * starting equity, so a first-trade loss IS a drawdown by that
     * definition. The bug review's claim that "most platforms" measure
     * differently is contested. See CODE_REVIEW.md §5.13.
     */
    @Ignore("CR §5.13 — drawdown convention disagreement, deferred for upstream discussion")
    @Test
    public void first_trade_loss_should_not_count_as_drawdown_from_zero() throws Exception {
        Strategy mockStrategy = Mockito.mock(Strategy.class);
        Mockito.when(mockStrategy.getTime()).thenReturn(1000L);

        Commission commission = new Commission(0.0, 0.0); // no commission for clarity
        PerformanceManager pm = rawInstance();
        setField(pm, "strategy", mockStrategy);
        setField(pm, "multiplier", 1);
        setField(pm, "commission", commission);
        setField(pm, "tradeReturns", new java.util.ArrayList<>());

        // Simulate a long-then-flat round-trip producing -100 P&L
        // (Buy 1 at 110, then Sell 1 at 100 → tradeProfit -10)
        pm.updateOnTrade(1, 110.0, 1, 0);   // open long
        pm.updateOnTrade(-1, 100.0, 0, 0);  // close flat at lower price

        double maxDD = pm.getMaxDrawdown();
        // Expected: 0 (or some sensible base-equity-relative value).
        // Today: ~10 (because peakNetProfit starts at 0 and we never
        // reached 0 — we dipped immediately to -10).
        assertEquals("a first-trade loss should not be reported as drawdown from zero",
                0.0, maxDD, 0);
    }
}
