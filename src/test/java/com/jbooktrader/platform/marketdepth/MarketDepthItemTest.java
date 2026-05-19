package com.jbooktrader.platform.marketdepth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MarketDepthItemTest {

    @Test
    public void getters_return_constructor_args() {
        MarketDepthItem item = new MarketDepthItem(100.5, 7);
        assertEquals(100.5, item.getPrice(), 0);
        assertEquals(7, item.getSize());
    }

    @Test
    public void set_overrides_fields() {
        MarketDepthItem item = new MarketDepthItem(100.5, 7);
        item.set(99.0, 12);
        assertEquals(99.0, item.getPrice(), 0);
        assertEquals(12, item.getSize());
    }
}
