package com.jbooktrader.indicator.combo;

import com.jbooktrader.platform.marketbook.MarketBook;
import com.jbooktrader.platform.marketbook.MarketSnapshot;
import org.junit.Test;

import static org.junit.Assert.*;

public class TensorEqualizerTest {

    @Test
    public void reset_zeroes_state() {
        TensorEqualizer t = new TensorEqualizer(60, 100);
        MarketBook b = new MarketBook();
        t.setMarketBook(b);
        for (int i = 0; i < 50; i++) {
            b.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L + i * 1000L,
                    50 + i, 100, 101, 1));
            t.calculate();
        }
        t.reset();
        assertEquals(0, t.getTension(), 1e-9);
        // sigmaTension is reset on next calculate; reset itself doesn't.
    }

    @Test
    public void constant_balance_and_price_gives_zero_tension() {
        TensorEqualizer t = new TensorEqualizer(60, 100);
        MarketBook b = new MarketBook();
        t.setMarketBook(b);
        for (int i = 0; i < 200; i++) {
            b.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L + i * 1000L,
                    50.0, 100.0, 100.0, 1));
            t.calculate();
        }
        assertEquals("flat input → no tension", 0.0, t.getTension(), 0.5);
    }

    /**
     * After warmup (variance > 0), sigmaTension must not be NaN for any
     * finite input. The first few samples may be NaN since variance starts
     * at 0; see first_sample_produces_NaN_sigmaTension_today.
     */
    @Test
    public void calculate_does_not_produce_nan_after_warmup() {
        TensorEqualizer t = new TensorEqualizer(60, 100);
        MarketBook b = new MarketBook();
        t.setMarketBook(b);
        for (int i = 0; i < 200; i++) {
            b.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L + i * 1000L,
                    50 + (i % 10), 100.0 + 0.01 * (i % 5), 100.5, 1));
            t.calculate();
            if (i >= 10) {
                assertFalse("tension should be finite at step " + i,
                        Double.isNaN(t.getTension()));
                assertFalse("sigmaTension should be finite at step " + i,
                        Double.isNaN(t.getSigmaTension()));
            }
        }
    }

    /**
     * Document the current behavior: at the very first sample, variance is
     * exactly 0 and sigmaTension is NaN. Arguably its own bug; a defensive
     * implementation would clamp to 0 during warmup.
     */
    @Test
    public void first_sample_produces_NaN_sigmaTension_today() {
        TensorEqualizer t = new TensorEqualizer(60, 100);
        MarketBook b = new MarketBook();
        t.setMarketBook(b);
        b.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L, 50, 100, 101, 1));
        t.calculate();
        assertTrue("first sample of TensorEqualizer yields NaN sigmaTension today",
                Double.isNaN(t.getSigmaTension()));
    }
}
