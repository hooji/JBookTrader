package com.jbooktrader.platform.chart;

import org.junit.Test;

import static org.junit.Assert.*;

public class TimedValueTest {

    @Test
    public void getters_return_constructor_args() {
        TimedValue tv = new TimedValue(1000L, 42.5);
        assertEquals(1000L, tv.getTime());
        assertEquals(42.5, tv.getValue(), 0);
    }

    @Test
    public void independent_instances_are_independent() {
        TimedValue a = new TimedValue(1L, 1);
        TimedValue b = new TimedValue(2L, 2);
        assertNotEquals(a.getTime(), b.getTime());
    }
}
