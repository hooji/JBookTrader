package com.jbooktrader.platform.chart;

import org.junit.Test;

import static org.junit.Assert.*;

public class BarSizeTest {

    @Test
    public void getBarSize_known_names() {
        assertEquals(BarSize.Second1,  BarSize.getBarSize("1 second"));
        assertEquals(BarSize.Minute1,  BarSize.getBarSize("1 minute"));
        assertEquals(BarSize.Hour2,    BarSize.getBarSize("2 hour"));
    }

    @Test
    public void getBarSize_unknown_returns_null() {
        // Today: silent null. A defensive implementation might throw.
        assertNull(BarSize.getBarSize("nope"));
    }

    @Test
    public void getSize_returns_seconds_times_1000() {
        assertEquals(1000,    BarSize.Second1.getSize());
        assertEquals(60_000,  BarSize.Minute1.getSize());
        assertEquals(3_600_000, BarSize.Hour1.getSize());
        assertEquals(7_200_000, BarSize.Hour2.getSize());
    }

    @Test
    public void name_returns_display_string() {
        assertEquals("1 minute", BarSize.Minute1.getName());
        assertEquals("15 seconds", BarSize.Second15.getName());
    }
}
