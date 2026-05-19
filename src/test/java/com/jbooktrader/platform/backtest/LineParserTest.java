package com.jbooktrader.platform.backtest;

import com.jbooktrader.platform.marketbook.MarketSnapshot;
import com.jbooktrader.platform.marketbook.MarketSnapshotFilter;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

/**
 * Tests for {@link LineParser}. Tied to CODE_REVIEW.md §5.12, §8.1, §8.2, §8.3.
 */
public class LineParserTest {

    @Test
    public void comment_line_returns_null() {
        LineParser p = new LineParser(null);
        assertNull(p.process("# this is a comment"));
    }

    @Test
    public void blank_line_returns_null() {
        LineParser p = new LineParser(null);
        assertNull(p.process(""));
        assertNull(p.process("   "));
    }

    @Test
    public void timeZone_property_sets_formatter_and_returns_null() {
        LineParser p = new LineParser(null);
        assertNull(p.process("timeZone=America/New_York"));
        // Now a data line should parse.
        MarketSnapshot s = p.process("113018,070000,21.91,2730.5,2730.75,4");
        assertNotNull(s);
    }

    @Test
    public void wellformed_snapshot_line_parsed() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        MarketSnapshot s = p.process("113018,070000,21.91,2730.5,2730.75,4");
        assertEquals(21.91, s.getBalance(), 1e-9);
        assertEquals(2730.5, s.getBid(), 1e-9);
        assertEquals(2730.75, s.getAsk(), 1e-9);
        assertEquals(4, s.getVolume());
    }

    @Test(expected = RuntimeException.class)
    public void snapshot_line_before_timezone_throws() {
        LineParser p = new LineParser(null);
        p.process("113018,070000,21.91,2730.5,2730.75,4");
    }

    @Test(expected = RuntimeException.class)
    public void wrong_column_count_throws() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070000,21.91,2730.5,2730.75"); // 5 columns
    }

    @Test(expected = RuntimeException.class)
    public void seven_columns_throws() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070000,21.91,2730.5,2730.75,4,extra");
    }

    @Test(expected = RuntimeException.class)
    public void negative_volume_throws() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070000,21.91,2730.5,2730.75,-1");
    }

    @Test
    public void zero_volume_accepted() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        MarketSnapshot s = p.process("113018,070000,21.91,2730.5,2730.75,0");
        assertEquals(0, s.getVolume());
    }

    @Test(expected = NumberFormatException.class)
    public void non_numeric_volume_throws() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070000,21.91,2730.5,2730.75,xx");
    }

    @Test(expected = NumberFormatException.class)
    public void non_numeric_bid_throws() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070000,21.91,xx,2730.75,1");
    }

    @Test(expected = RuntimeException.class)
    public void going_back_in_time_throws() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070010,21.91,2730.5,2730.75,1");
        p.process("113018,070005,21.91,2730.5,2730.75,1"); // earlier
    }

    @Test(expected = RuntimeException.class)
    public void duplicate_timestamp_throws() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070010,21.91,2730.5,2730.75,1");
        p.process("113018,070010,21.92,2730.5,2730.75,2"); // same time
    }

    @Test
    public void sequential_same_minute_uses_fastpath() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        // First snapshot establishes the cached minute.
        MarketSnapshot a = p.process("113018,070000,1,100,101,1");
        MarketSnapshot b = p.process("113018,070001,1,100,101,1");
        // 1-second delta in time-millis
        assertEquals(a.getTime() + 1000L, b.getTime());
    }

    @Test
    public void crossing_minute_boundary_parses_correctly() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        MarketSnapshot a = p.process("113018,070059,1,100,101,1");
        MarketSnapshot b = p.process("113018,070100,1,100,101,1");
        // 1 second apart, even though seconds went 59 → 0
        assertEquals(a.getTime() + 1000L, b.getTime());
    }

    @Test
    public void filter_excluded_returns_null() {
        MarketSnapshotFilter filter = Mockito.mock(MarketSnapshotFilter.class);
        Mockito.when(filter.contains(Mockito.anyLong())).thenReturn(false);
        LineParser p = new LineParser(filter);
        p.process("timeZone=America/New_York");
        assertNull(p.process("113018,070000,21.91,2730.5,2730.75,4"));
    }

    @Test
    public void filter_included_returns_snapshot() {
        MarketSnapshotFilter filter = Mockito.mock(MarketSnapshotFilter.class);
        Mockito.when(filter.contains(Mockito.anyLong())).thenReturn(true);
        LineParser p = new LineParser(filter);
        p.process("timeZone=America/New_York");
        MarketSnapshot s = p.process("113018,070000,21.91,2730.5,2730.75,4");
        assertNotNull(s);
    }

    /**
     * BUG CR §8.1: A line with bid > ask is accepted silently. The
     * downstream MarketSnapshot reports a negative spread; indicators
     * computed off `getPrice()` continue but bid/ask asymmetry is
     * mathematically nonsense.
     *
     * Expected to FAIL until validation is added.
     */
    @Test(expected = RuntimeException.class)
    public void bid_greater_than_ask_should_throw() {
        LineParser p = new LineParser(null);
        p.process("timeZone=America/New_York");
        p.process("113018,070000,0,2731.0,2730.0,1");
    }
}
