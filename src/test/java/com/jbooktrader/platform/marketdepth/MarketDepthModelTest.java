package com.jbooktrader.platform.marketdepth;

import org.junit.Test;

import java.util.NoSuchElementException;

import static org.junit.Assert.*;

/**
 * Tests for {@link MarketDepthModel}.
 *
 * Several tests document missing edge-case handling. Tied to CODE_REVIEW.md
 * §5.9, §5.10.
 */
public class MarketDepthModelTest {

    @Test
    public void newly_constructed_model_is_empty() {
        MarketDepthModel m = new MarketDepthModel();
        assertEquals(0, m.getSize());
        assertEquals(0, m.getCumulativeSize());
    }

    @Test
    public void insert_at_position_zero_adds_item() {
        MarketDepthModel m = new MarketDepthModel();
        m.insert(0, 100.0, 5);
        assertEquals(1, m.getSize());
        assertEquals(100.0, m.getBestPrice(), 0);
        assertEquals(5, m.getCumulativeSize());
    }

    @Test
    public void insert_caps_at_ten_items() {
        MarketDepthModel m = new MarketDepthModel();
        for (int i = 0; i < 15; i++) {
            m.insert(i % 10, 100.0 - i, 1);
        }
        assertEquals("max depth is 10", 10, m.getSize());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void insert_past_end_throws() {
        MarketDepthModel m = new MarketDepthModel();
        m.insert(5, 100.0, 1);
    }

    @Test
    public void delete_at_valid_position_removes_item() {
        MarketDepthModel m = new MarketDepthModel();
        m.insert(0, 100.0, 5);
        m.insert(1, 99.0, 3);
        m.delete(0);
        assertEquals(1, m.getSize());
        assertEquals(99.0, m.getBestPrice(), 0);
    }

    @Test
    public void delete_at_invalid_position_is_noop() {
        MarketDepthModel m = new MarketDepthModel();
        m.delete(5); // empty list
        assertEquals(0, m.getSize());
        m.insert(0, 100.0, 1);
        m.delete(5); // index past size
        assertEquals(1, m.getSize());
    }

    /**
     * BUG CR §5.10: update() on an out-of-range position currently throws
     * NoSuchElementException from listIterator.next(), which is inconsistent
     * with delete()'s tolerant behavior. Document the current behavior.
     */
    @Test(expected = NoSuchElementException.class)
    public void update_out_of_range_throws_nse() {
        MarketDepthModel m = new MarketDepthModel();
        m.update(0, 100.0, 5);
    }

    @Test
    public void update_at_valid_position_replaces_item() {
        MarketDepthModel m = new MarketDepthModel();
        m.insert(0, 100.0, 5);
        m.update(0, 99.0, 7);
        assertEquals(99.0, m.getBestPrice(), 0);
        assertEquals(7, m.getCumulativeSize());
    }

    /**
     * BUG CR §5.9: getBestPrice() on an empty model throws
     * NoSuchElementException with no friendly error message. Callers must
     * already guard against this; documenting the current behavior.
     */
    @Test(expected = NoSuchElementException.class)
    public void getBestPrice_on_empty_throws() {
        MarketDepthModel m = new MarketDepthModel();
        m.getBestPrice();
    }

    @Test
    public void getCumulativeSize_sums_all_items() {
        MarketDepthModel m = new MarketDepthModel();
        m.insert(0, 100.0, 5);
        m.insert(1, 99.0, 7);
        m.insert(2, 98.0, 13);
        assertEquals(25, m.getCumulativeSize());
    }

    @Test
    public void reset_clears_items() {
        MarketDepthModel m = new MarketDepthModel();
        m.insert(0, 100.0, 1);
        m.insert(1, 99.0, 2);
        m.reset();
        assertEquals(0, m.getSize());
    }

    @Test
    public void hasValidBidStructure_descending_prices_positive_sizes() {
        MarketDepthModel m = makeFullBidBook();
        assertTrue(m.hasValidBidStructure());
    }

    @Test
    public void hasValidBidStructure_equal_prices_rejected() {
        MarketDepthModel m = new MarketDepthModel();
        for (int i = 0; i < 10; i++) {
            m.insert(i, 100.0, 1); // all same price
        }
        assertFalse(m.hasValidBidStructure());
    }

    @Test
    public void hasValidBidStructure_zero_size_rejected() {
        MarketDepthModel m = new MarketDepthModel();
        for (int i = 0; i < 10; i++) {
            m.insert(i, 100.0 - i, i == 5 ? 0 : 1); // one zero
        }
        assertFalse(m.hasValidBidStructure());
    }

    @Test
    public void hasValidAskStructure_ascending_prices_positive_sizes() {
        MarketDepthModel m = makeFullAskBook();
        assertTrue(m.hasValidAskStructure());
    }

    @Test
    public void hasValidAskStructure_descending_rejected() {
        MarketDepthModel m = makeFullBidBook(); // bid-style (descending)
        assertFalse(m.hasValidAskStructure());
    }

    /**
     * Question: does the empty model count as "valid"?  Today hasValidBidStructure
     * iterates an empty list and returns true, which is misleading. A consumer
     * (MarketDepth.isValidDepth) double-checks size==maxDepth so this isn't
     * exploitable today, but the method should not lie about an empty book.
     */
    @Test
    public void hasValidBidStructure_empty_book_should_be_false() {
        MarketDepthModel m = new MarketDepthModel();
        assertFalse("an empty book is not a valid bid structure",
                m.hasValidBidStructure());
    }

    private MarketDepthModel makeFullBidBook() {
        MarketDepthModel m = new MarketDepthModel();
        for (int i = 0; i < 10; i++) {
            m.insert(i, 100.0 - i, 1);
        }
        return m;
    }

    private MarketDepthModel makeFullAskBook() {
        MarketDepthModel m = new MarketDepthModel();
        for (int i = 0; i < 10; i++) {
            m.insert(i, 100.0 + i, 1);
        }
        return m;
    }
}
