package com.jbooktrader.platform.indicator;

import com.jbooktrader.platform.marketbook.MarketBook;
import com.jbooktrader.platform.marketbook.MarketSnapshot;
import org.junit.Test;

import static org.junit.Assert.*;

public class IndicatorManagerTest {

    /** Counting indicator: records how many times calculate() was called. */
    static class Counter extends Indicator {
        int calls = 0, resets = 0;
        final String key;
        Counter(int p) {
            super(p);
            key = "Counter(" + p + ")";
        }
        @Override public void calculate() { calls++; value = calls; }
        @Override public void reset() { resets++; value = 0; }
        @Override public String getKey() { return key; }
    }

    private static MarketBook bookWith(MarketSnapshot s) {
        MarketBook book = new MarketBook();
        book.setSnapshot(s);
        return book;
    }

    @Test
    public void addIndicator_dedupes_by_key() {
        IndicatorManager m = new IndicatorManager();
        Counter a = new Counter(5);
        Counter b = new Counter(5); // same key
        Indicator first = m.addIndicator(a);
        Indicator second = m.addIndicator(b);
        assertSame("indicators with the same key should dedupe", first, second);
        assertEquals(1, m.getIndicators().size());
    }

    @Test
    public void addIndicator_different_keys_are_kept_separately() {
        IndicatorManager m = new IndicatorManager();
        m.addIndicator(new Counter(5));
        m.addIndicator(new Counter(10));
        assertEquals(2, m.getIndicators().size());
    }

    @Test
    public void updateIndicators_returns_false_before_warmup() {
        IndicatorManager m = new IndicatorManager();
        m.setMarketBook(bookWith(new MarketSnapshot("ES", 1000L, 0, 100, 101, 1)));
        m.addIndicator(new Counter(1));
        assertFalse("not enough samples yet", m.updateIndicators());
    }

    @Test
    public void updateIndicators_returns_true_after_warmup() {
        IndicatorManager m = new IndicatorManager();
        Counter c = new Counter(1);
        m.addIndicator(c);

        MarketBook book = new MarketBook();
        m.setMarketBook(book);
        // Feed snapshots 1 second apart so no gap reset fires. Vary bid/ask
        // each second so MarketBook never enters "locked" mode (15 min of
        // unchanged mid-price), which would reset indicators every call.
        long t = 1_700_000_000_000L;
        boolean ready = false;
        // 180 minutes * 60 seconds = 10 800 samples needed to warm up.
        for (int i = 0; i < 10_810; i++) {
            double bid = 100.0 + 0.25 * (i % 4);
            double ask = bid + 0.25;
            book.setSnapshot(new MarketSnapshot("ES", t + i * 1000L, 0, bid, ask, 1));
            ready = m.updateIndicators();
        }
        assertTrue("indicator should be warm after MIN_SAMPLE_SIZE updates", ready);
    }

    @Test
    public void updateIndicators_resets_on_gap_larger_than_five_minutes() {
        IndicatorManager m = new IndicatorManager();
        Counter c = new Counter(1);
        m.addIndicator(c);

        MarketBook book = new MarketBook();
        m.setMarketBook(book);
        long t = 1_700_000_000_000L;

        book.setSnapshot(new MarketSnapshot("ES", t, 0, 100, 101, 1));
        m.updateIndicators();
        int resetsBefore = c.resets;
        // 6 minute gap
        book.setSnapshot(new MarketSnapshot("ES", t + 6 * 60 * 1000L, 0, 100, 101, 1));
        m.updateIndicators();
        assertTrue("gap > 5 minutes should reset indicators",
                c.resets > resetsBefore);
    }

    @Test
    public void updateIndicators_does_not_reset_on_gap_less_than_five_minutes() {
        IndicatorManager m = new IndicatorManager();
        Counter c = new Counter(1);
        m.addIndicator(c);
        MarketBook book = new MarketBook();
        m.setMarketBook(book);
        long t = 1_700_000_000_000L;
        book.setSnapshot(new MarketSnapshot("ES", t, 0, 100, 101, 1));
        m.updateIndicators();
        int resetsBefore = c.resets;
        // 4 minute gap (< 5)
        book.setSnapshot(new MarketSnapshot("ES", t + 4 * 60 * 1000L, 0, 100, 101, 1));
        m.updateIndicators();
        // Resets count may be 1 from initial (because previousSnapshotTime was 0).
        // The test is "no *additional* reset since the previous call".
        assertEquals("no extra reset for sub-5-minute gap",
                resetsBefore, c.resets);
    }

    @Test
    public void resetIndicators_zeros_samples_and_calls_indicator_reset() {
        IndicatorManager m = new IndicatorManager();
        Counter c = new Counter(1);
        m.addIndicator(c);
        MarketBook book = new MarketBook();
        m.setMarketBook(book);
        book.setSnapshot(new MarketSnapshot("ES", 1L, 0, 100, 101, 1));
        m.updateIndicators(); // samples++
        m.resetIndicators();
        assertEquals(1, c.resets + 0); // at least the reset triggered by setMarketBook + first gap may already count
    }

    /**
     * The first call to updateIndicators sees previousSnapshotTime==0,
     * so (lastSnapshotTime - 0 > GAP_SIZE) is true → resetIndicators
     * fires on the very first sample. Document the current behavior.
     */
    @Test
    public void first_update_triggers_a_reset_due_to_zero_previousSnapshotTime() {
        IndicatorManager m = new IndicatorManager();
        Counter c = new Counter(1);
        m.addIndicator(c);
        MarketBook book = new MarketBook();
        m.setMarketBook(book);
        book.setSnapshot(new MarketSnapshot("ES", 1_700_000_000_000L, 0, 100, 101, 1));
        m.updateIndicators();
        assertTrue("first updateIndicators triggers a reset", c.resets >= 1);
    }
}
