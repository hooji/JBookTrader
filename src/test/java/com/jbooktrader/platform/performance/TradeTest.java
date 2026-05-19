package com.jbooktrader.platform.performance;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link Trade}. Tied to CODE_REVIEW.md §1.13, §5.1.
 */
public class TradeTest {

    @Test
    public void updateTotalBought_accumulates_quantity_and_total() {
        Trade t = new Trade(50);
        t.updateTotalBought(2, 100.0, 0.0);
        t.updateTotalBought(3, 110.0, 0.0);
        assertEquals(5, t.getQuantityBought());
        // total = 2*100 + 3*110 = 530, avg = 530/5 = 106
        assertEquals(106.0, t.getAverageBoughtPrice(), 1e-9);
    }

    @Test
    public void updateTotalSold_accumulates_quantity_and_total() {
        Trade t = new Trade(50);
        t.updateTotalSold(4, 200.0, 0.0);
        t.updateTotalSold(1, 220.0, 0.0);
        assertEquals(5, t.getQuantitySold());
        // total = 4*200 + 1*220 = 1020, avg = 1020/5 = 204
        assertEquals(204.0, t.getAverageSoldPrice(), 1e-9);
    }

    /**
     * BUG CR §5.1: Trade.getAverageBoughtPrice() divides by quantityBought.
     * For a short-only trade (no buys), quantityBought == 0 → NaN.
     * Expected to FAIL until guarded.
     */
    @Test
    public void getAverageBoughtPrice_with_no_buys_should_not_be_NaN() {
        Trade t = new Trade(50);
        t.updateTotalSold(5, 100.0, 0.0);
        double avg = t.getAverageBoughtPrice();
        assertFalse("Trade with no buys should not produce NaN average price",
                Double.isNaN(avg));
    }

    /**
     * BUG CR §5.1: Trade.getAverageSoldPrice() divides by quantitySold.
     * For a long-only trade (no sells), quantitySold == 0 → NaN.
     * Expected to FAIL until guarded.
     */
    @Test
    public void getAverageSoldPrice_with_no_sells_should_not_be_NaN() {
        Trade t = new Trade(50);
        t.updateTotalBought(5, 100.0, 0.0);
        double avg = t.getAverageSoldPrice();
        assertFalse("Trade with no sells should not produce NaN average price",
                Double.isNaN(avg));
    }

    /**
     * BUG CR §1.13: slippage is overwritten by each updateTotalBought call,
     * not accumulated. Across multi-leg trades (partial fills), only the
     * last leg's slippage is recorded — getSlippageAmount() understates the
     * true slippage. Expected to FAIL until slippage is accumulated.
     */
    @Test
    public void slippage_should_be_accumulated_across_partial_fills() {
        Trade t = new Trade(50);
        t.updateTotalBought(2, 100.0, 0.5);
        t.updateTotalBought(3, 100.0, 0.7);
        double slipPts = t.getSlippagePoints();
        // We expect the sum of the two slippages, weighted by quantity perhaps,
        // but at minimum the total slippage points should reflect both fills,
        // not just the last one. Current behavior is to keep only 0.7.
        assertNotEquals("slippage should reflect both partial fills, not only the last",
                0.7, slipPts, 1e-9);
    }

    @Test
    public void getTimeInMarket_returns_exit_minus_entry() {
        Trade t = new Trade(50);
        t.setEntryTime(1000L);
        t.setExitTime(2500L);
        assertEquals(1500L, t.getTimeInMarket());
    }

    /**
     * Trade.getTimeInMarket() with no exit time returns -entryTime (a huge
     * negative).  Document the current behavior; ideally the unclosed-trade
     * case would return 0 or throw IllegalStateException.
     */
    @Test
    public void getTimeInMarket_with_no_exit_time_is_negative() {
        Trade t = new Trade(50);
        t.setEntryTime(1000L);
        long t_in_market = t.getTimeInMarket();
        // Today: 0 - 1000 = -1000.
        // A defensive implementation would return >= 0 or throw.
        assertTrue("unclosed trade should not report a sensible duration",
                t_in_market < 0 || t_in_market == 0);
    }

    @Test
    public void getSlippageAmount_scales_by_multiplier_and_quantity() {
        Trade t = new Trade(50);
        t.updateTotalBought(2, 100.0, 0.5);
        t.updateTotalSold(2, 105.0, 0.3);
        // multiplier * (qBought*slipBought + qSold*slipSold) = 50 * (2*0.5 + 2*0.3) = 50 * 1.6 = 80
        assertEquals(80.0, t.getSlippageAmount(), 1e-9);
    }
}
