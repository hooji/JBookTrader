package com.jbooktrader.indicator.balance;

import com.jbooktrader.platform.marketbook.MarketBook;
import com.jbooktrader.platform.marketbook.MarketSnapshot;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link BalanceEMA}. Tied to CODE_REVIEW.md §5.8.
 */
public class BalanceEMATest {

    @Test
    public void length_one_tracks_latest_value_exactly() {
        BalanceEMA e = new BalanceEMA(1); // multiplier = 1
        e.setMarketBook(book(50));
        e.calculate();
        assertEquals(50.0, e.getValue(), 1e-9);
        e.setMarketBook(book(100));
        e.calculate();
        // multiplier=1 means EMA = latest
        assertEquals(100.0, e.getValue(), 1e-9);
    }

    @Test
    public void constant_input_converges_to_constant() {
        BalanceEMA e = new BalanceEMA(10);
        MarketBook book = new MarketBook();
        e.setMarketBook(book);
        for (int i = 0; i < 200; i++) {
            book.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L + i * 1000L, 42.0, 100, 101, 1));
            e.calculate();
        }
        assertEquals("EMA should converge to the constant input", 42.0, e.getValue(), 0.001);
    }

    @Test
    public void reset_zeroes_value() {
        BalanceEMA e = new BalanceEMA(5);
        e.setMarketBook(book(100));
        e.calculate();
        e.reset();
        assertEquals(0.0, e.getValue(), 0);
    }

    /**
     * BUG CR §5.8: length=0 gives multiplier = 2/(0+1) = 2.0. Each
     * calculate() updates value as `value + (balance - value) * 2`,
     * which is equivalent to `2*balance - value` — this oscillates and
     * diverges for any non-trivial input.  Should be rejected at
     * construction time.
     */
    @Test
    public void length_zero_should_be_rejected() {
        try {
            new BalanceEMA(0);
            fail("BalanceEMA(0) should throw — multiplier 2.0 diverges");
        } catch (IllegalArgumentException expected) {
            // pass
        } catch (RuntimeException expected) {
            // also pass — any reasonable rejection
        }
    }

    /**
     * BUG CR §5.8: length=-1 gives multiplier = 2/0 = Infinity → NaN
     * after the first calculate(). Should be rejected.
     */
    @Test
    public void length_negative_should_be_rejected() {
        try {
            new BalanceEMA(-1);
            fail("BalanceEMA(-1) should throw — multiplier is Infinity");
        } catch (IllegalArgumentException expected) {
            // pass
        } catch (RuntimeException expected) {
            // also pass
        }
    }

    private MarketBook book(double balance) {
        MarketBook b = new MarketBook();
        b.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L, balance, 100, 101, 1));
        return b;
    }
}
