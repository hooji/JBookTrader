package com.jbooktrader.indicator.balance;

import com.jbooktrader.platform.marketbook.MarketBook;
import com.jbooktrader.platform.marketbook.MarketSnapshot;
import org.junit.Test;

import static org.junit.Assert.*;

public class BalanceVelocityTest {

    @Test
    public void constant_input_converges_to_zero_velocity() {
        BalanceVelocity v = new BalanceVelocity(5, 20);
        MarketBook b = new MarketBook();
        v.setMarketBook(b);
        for (int i = 0; i < 200; i++) {
            b.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L + i * 1000L, 25.0, 100, 101, 1));
            v.calculate();
        }
        assertEquals("flat balance → zero velocity", 0.0, v.getValue(), 0.01);
    }

    /**
     * BUG (minor): BalanceVelocity.reset() resets only its internal `fast`
     * and `slow` fields, not the inherited `value`. BalanceEMA, by
     * contrast, sets `value = 0` in its reset(). Inconsistent across
     * indicators. Expected to FAIL until reset() also clears value.
     */
    @Test
    public void reset_should_zero_observable_value() {
        BalanceVelocity v = new BalanceVelocity(5, 20);
        MarketBook b = new MarketBook();
        v.setMarketBook(b);
        b.setSnapshot(new MarketSnapshot("ES", 1L, 50.0, 100, 101, 1));
        v.calculate();
        v.reset();
        assertEquals("reset() should zero the publicly-visible value",
                0.0, v.getValue(), 0);
    }

    @Test
    public void after_reset_first_calculate_uses_fresh_state() {
        // Even if reset doesn't zero `value`, a subsequent calculate should.
        BalanceVelocity v = new BalanceVelocity(5, 20);
        MarketBook b = new MarketBook();
        v.setMarketBook(b);
        b.setSnapshot(new MarketSnapshot("ES", 1L, 50.0, 100, 101, 1));
        v.calculate();
        v.reset();
        b.setSnapshot(new MarketSnapshot("ES", 2L, 0.0, 100, 101, 1));
        v.calculate();
        // fast = (0 - 0) * fastMult = 0, slow = (0 - 0) * slowMult = 0,
        // value = 0 - 0 = 0
        assertEquals(0.0, v.getValue(), 1e-9);
    }
}
