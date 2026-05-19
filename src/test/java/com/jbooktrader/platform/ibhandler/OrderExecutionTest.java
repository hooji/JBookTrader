package com.jbooktrader.platform.ibhandler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OrderExecutionTest {

    @Test
    public void getters_return_constructor_args() {
        OrderExecution e = new OrderExecution(42, "ESH26", 5, "BOT", 100.25);
        assertEquals(42, e.getOrderID());
        assertEquals("ESH26", e.getInstrument());
        assertEquals(5, e.getQuantity());
        assertEquals("BOT", e.getSide());
        assertEquals(100.25, e.getAverageFillPrice(), 0);
    }

    @Test
    public void sold_side_handled() {
        OrderExecution e = new OrderExecution(7, "ESH26", 3, "SLD", 99.5);
        assertEquals("SLD", e.getSide());
    }
}
