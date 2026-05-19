package com.jbooktrader.platform.ibhandler;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class OrderKeeperTest {

    @Test
    public void newly_constructed_has_no_open_orders() {
        OrderKeeper k = new OrderKeeper();
        assertFalse(k.hasOpenOrders());
    }

    @Test
    public void add_then_getOpenOrder_returns_same() {
        OrderKeeper k = new OrderKeeper();
        OpenOrder order = new OpenOrder(42, "TestStrategy", 1);
        k.add(order);
        assertSame(order, k.getOpenOrder(42));
    }

    @Test
    public void getOpenOrder_returns_null_for_unknown_id() {
        OrderKeeper k = new OrderKeeper();
        assertNull(k.getOpenOrder(99));
    }

    @Test
    public void hasOpenOrders_after_add_then_remove() {
        OrderKeeper k = new OrderKeeper();
        k.add(new OpenOrder(1, "S", 1));
        assertTrue(k.hasOpenOrders());
        k.removeOpenOrder(1);
        assertFalse(k.hasOpenOrders());
    }

    @Test
    public void concurrent_add_and_remove_no_exception() throws InterruptedException {
        OrderKeeper k = new OrderKeeper();
        int threads = 8, perThread = 1000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        int id = base + i;
                        k.add(new OpenOrder(id, "S", 1));
                        k.getOpenOrder(id);
                        k.removeOpenOrder(id);
                    }
                } catch (Throwable th) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        assertEquals(0, errors.get());
        assertFalse(k.hasOpenOrders());
    }
}
