package com.jbooktrader.platform.marketbook;

import org.junit.Test;

import static org.junit.Assert.*;

public class MarketSnapshotTest {

    @Test
    public void getPrice_is_midpoint_of_bid_and_ask() {
        MarketSnapshot s = new MarketSnapshot(1000L, 5.0, 100.0, 101.0, 12);
        assertEquals(100.5, s.getPrice(), 0);
    }

    @Test
    public void getPrice_with_negative_spread_is_still_midpoint() {
        // Sanity: parser may yield bid > ask if validation is missing.
        MarketSnapshot s = new MarketSnapshot(1000L, 5.0, 101.0, 100.0, 1);
        assertEquals(100.5, s.getPrice(), 0);
    }

    @Test
    public void endOfStream_marker_has_time_minus_one() {
        MarketSnapshot eos = new MarketSnapshot();
        assertTrue(eos.isEndOfStream());
        assertEquals(-1, eos.getTime());
    }

    @Test
    public void regular_snapshot_is_not_endOfStream() {
        MarketSnapshot s = new MarketSnapshot(1000L, 0, 100, 100, 0);
        assertFalse(s.isEndOfStream());
    }

    @Test
    public void volume_zero_is_accepted() {
        MarketSnapshot s = new MarketSnapshot(1000L, 0, 100, 100, 0);
        assertEquals(0, s.getVolume());
    }

    @Test
    public void contract_label_is_stored() {
        MarketSnapshot s = new MarketSnapshot("ESH26", 1000L, 0, 100, 100, 0);
        assertEquals("ESH26", s.getContract());
    }
}
