package com.jbooktrader.platform.marketbook;

import com.jbooktrader.platform.marketdepth.MarketDepthModel;
import org.junit.Test;

import static org.junit.Assert.*;

public class BalanceAggregatorTest {

    @Test
    public void newly_constructed_is_empty() {
        BalanceAggregator b = new BalanceAggregator();
        assertTrue(b.isEmpty());
    }

    @Test
    public void after_aggregate_is_not_empty() {
        BalanceAggregator b = new BalanceAggregator();
        b.aggregate(bidBook(10), askBook(10));
        assertFalse(b.isEmpty());
    }

    @Test
    public void equal_sizes_balance_zero() {
        BalanceAggregator b = new BalanceAggregator();
        b.aggregate(bidBook(10), askBook(10));
        assertEquals(0.0, b.getBalance(), 1e-9);
    }

    @Test
    public void all_bids_balance_positive_100() {
        BalanceAggregator b = new BalanceAggregator();
        b.aggregate(bidBook(10), askBook(0));
        assertEquals(100.0, b.getBalance(), 1e-9);
    }

    @Test
    public void all_asks_balance_negative_100() {
        BalanceAggregator b = new BalanceAggregator();
        b.aggregate(bidBook(0), askBook(10));
        assertEquals(-100.0, b.getBalance(), 1e-9);
    }

    @Test
    public void averages_over_multiple_aggregations() {
        BalanceAggregator b = new BalanceAggregator();
        b.aggregate(bidBook(10), askBook(0));   // +1.0 (100%)
        b.aggregate(bidBook(0), askBook(10));   // -1.0 (-100%)
        // Mean is 0, scaled by 100.
        assertEquals(0.0, b.getBalance(), 1e-9);
    }

    @Test
    public void clear_resets_state() {
        BalanceAggregator b = new BalanceAggregator();
        b.aggregate(bidBook(10), askBook(0));
        b.clear();
        assertTrue(b.isEmpty());
    }

    /**
     * Both books empty (cumulativeBid + cumulativeAsk == 0) → divide by zero
     * → NaN balance value gets stored.  Currently silently accepted.
     */
    @Test
    public void aggregate_with_empty_books_produces_nan_balance() {
        BalanceAggregator b = new BalanceAggregator();
        MarketDepthModel emptyBids = new MarketDepthModel();
        MarketDepthModel emptyAsks = new MarketDepthModel();
        b.aggregate(emptyBids, emptyAsks);
        // Document: today this stores a NaN. A defensive implementation
        // would refuse the input or default to 0.
        assertTrue("aggregating empty books yields NaN balance",
                Double.isNaN(b.getBalance()) || b.getBalance() == 0.0);
    }

    private MarketDepthModel bidBook(int sizePerLevel) {
        MarketDepthModel m = new MarketDepthModel();
        for (int i = 0; i < 10; i++) {
            m.insert(i, 100.0 - i, sizePerLevel);
        }
        return m;
    }

    private MarketDepthModel askBook(int sizePerLevel) {
        MarketDepthModel m = new MarketDepthModel();
        for (int i = 0; i < 10; i++) {
            m.insert(i, 100.0 + i, sizePerLevel);
        }
        return m;
    }
}
