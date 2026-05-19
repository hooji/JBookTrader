package com.jbooktrader.platform.chart;

import org.junit.Test;

import static org.junit.Assert.*;

public class BarTest {

    @Test
    public void single_value_constructor_sets_all_OHLC() {
        Bar b = new Bar(1000L, 42.0);
        assertEquals(42.0, b.getOpen(), 0);
        assertEquals(42.0, b.getHigh(), 0);
        assertEquals(42.0, b.getLow(), 0);
        assertEquals(42.0, b.getClose(), 0);
        assertEquals(1000L, b.getTime());
    }

    @Test
    public void setHigh_setLow_setClose_update_fields() {
        Bar b = new Bar(1000L, 42.0);
        b.setHigh(50.0);
        b.setLow(30.0);
        b.setClose(45.0);
        assertEquals(50.0, b.getHigh(), 0);
        assertEquals(30.0, b.getLow(), 0);
        assertEquals(45.0, b.getClose(), 0);
        // open is final-after-construction
        assertEquals(42.0, b.getOpen(), 0);
    }

    /**
     * No NaN guard today. Document the current behavior; a defensive
     * implementation would reject NaN.
     */
    @Test
    public void setHigh_accepts_NaN_silently_today() {
        Bar b = new Bar(1000L, 42.0);
        b.setHigh(Double.NaN);
        assertTrue(Double.isNaN(b.getHigh()));
    }
}
