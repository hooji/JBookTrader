package com.jbooktrader.platform.marketbook;

import org.junit.Test;

import static org.junit.Assert.*;

public class MarketBookTest {

    @Test
    public void newly_constructed_book_is_empty() {
        MarketBook book = new MarketBook();
        assertTrue(book.isEmpty());
        assertNull(book.getSnapshot());
    }

    @Test
    public void after_setSnapshot_book_is_not_empty() {
        MarketBook book = new MarketBook();
        book.setSnapshot(new MarketSnapshot("ES", 1000L, 0, 100, 101, 1));
        assertFalse(book.isEmpty());
        assertEquals("ES", book.getContract());
    }

    @Test
    public void isGapping_true_when_next_snapshot_more_than_one_hour_later() {
        MarketBook book = new MarketBook();
        book.setSnapshot(new MarketSnapshot("ES", t(0), 0, 100, 101, 1));
        MarketSnapshot far = new MarketSnapshot("ES", t(60 * 60 * 1000L + 1), 0, 100, 101, 1);
        assertTrue(book.isGapping(far));
    }

    @Test
    public void isGapping_false_at_exactly_one_hour() {
        MarketBook book = new MarketBook();
        book.setSnapshot(new MarketSnapshot("ES", t(0), 0, 100, 101, 1));
        MarketSnapshot oneHour = new MarketSnapshot("ES", t(60 * 60 * 1000L), 0, 100, 101, 1);
        assertFalse(oneHour + " is exactly at the gap boundary; should not be gapping",
                book.isGapping(oneHour));
    }

    @Test
    public void isGapping_false_on_empty_book() {
        MarketBook book = new MarketBook();
        MarketSnapshot s = new MarketSnapshot("ES", t(0), 0, 100, 101, 1);
        assertFalse(book.isGapping(s));
    }

    @Test
    public void isLocked_false_initially() {
        MarketBook book = new MarketBook();
        assertFalse(book.isLocked());
    }

    @Test
    public void isLocked_true_after_15_minutes_of_unchanged_midprice() {
        MarketBook book = new MarketBook();
        book.setSnapshot(new MarketSnapshot("ES", t(0), 0, 100, 101, 1));
        // 15 minutes + 1 second of same midprice
        book.setSnapshot(new MarketSnapshot("ES", t(15 * 60 * 1000L + 1000), 0, 100, 101, 1));
        assertTrue(book.isLocked());
    }

    @Test
    public void isLocked_false_below_15_minutes() {
        MarketBook book = new MarketBook();
        book.setSnapshot(new MarketSnapshot("ES", t(0), 0, 100, 101, 1));
        book.setSnapshot(new MarketSnapshot("ES", t(14 * 60 * 1000L), 0, 100, 101, 1));
        assertFalse(book.isLocked());
    }

    @Test
    public void isLocked_false_after_midprice_change() {
        MarketBook book = new MarketBook();
        book.setSnapshot(new MarketSnapshot("ES", t(0), 0, 100, 101, 1));
        book.setSnapshot(new MarketSnapshot("ES", t(15 * 60 * 1000L + 1000), 0, 100, 101, 1));
        // assert locked, then nudge price
        assertTrue(book.isLocked());
        book.setSnapshot(new MarketSnapshot("ES", t(16 * 60 * 1000L), 0, 101, 102, 1));
        assertFalse(book.isLocked());
    }

    private long t(long millisOffset) {
        // Some safely far-in-the-future epoch millis to avoid year-2008
        // calendar edge cases.
        return 1_700_000_000_000L + millisOffset;
    }
}
