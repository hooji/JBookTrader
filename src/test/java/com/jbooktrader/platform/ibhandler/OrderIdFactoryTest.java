package com.jbooktrader.platform.ibhandler;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests for {@link OrderIdFactory}. Tied to CODE_REVIEW.md §1.5.
 *
 * The factory's `nextOrderID` field is a plain int with no synchronization.
 * Concurrent increments can be lost (read-modify-write race), and
 * cross-thread reads can return stale values. The concurrent test below is
 * expected to FAIL until the field becomes an AtomicInteger (or the
 * increment/get/set are properly synchronized).
 */
public class OrderIdFactoryTest {

    @Test
    public void acquire_returns_false_without_setter_within_5s() throws Exception {
        OrderIdFactory f = new OrderIdFactory();
        long start = System.currentTimeMillis();
        boolean ok = f.acquireNextOrderID();
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(ok);
        assertTrue("should block roughly 5 seconds, was " + elapsed, elapsed >= 4500);
    }

    @Test
    public void acquire_returns_true_after_setter() throws Exception {
        OrderIdFactory f = new OrderIdFactory();
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            f.setNextOrderID(42);
        }).start();
        boolean ok = f.acquireNextOrderID();
        assertTrue(ok);
        assertEquals(42, f.getNextOrderID());
    }

    @Test
    public void incrementOrderID_increments_by_one() {
        OrderIdFactory f = new OrderIdFactory();
        f.setNextOrderID(10);
        f.incrementOrderID();
        f.incrementOrderID();
        assertEquals(12, f.getNextOrderID());
    }

    /**
     * BUG CR §1.5: concurrent incrementOrderID is racy because nextOrderID
     * is a plain int, not AtomicInteger. After N threads each increment
     * `perThread` times, the final value should be `start + N*perThread`.
     * If increments are lost, the final value is smaller.
     *
     * Expected to FAIL until the field is made atomic.
     */
    @Test
    public void concurrent_increment_should_not_lose_increments() throws Exception {
        OrderIdFactory f = new OrderIdFactory();
        f.setNextOrderID(0);

        int threads = 16;
        int perThread = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        f.incrementOrderID();
                    }
                } catch (InterruptedException e) {
                    // ignore
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));

        int expected = threads * perThread;
        int actual = f.getNextOrderID();
        assertEquals("concurrent increments must not be lost; expected "
                + expected + " but lost " + (expected - actual),
                expected, actual);
    }

    /**
     * BUG CR §1.5: a value set on one thread may not be visible on another
     * because `nextOrderID` is not volatile. Visibility can be arbitrarily
     * delayed. We can't deterministically observe staleness across threads,
     * but this test at least asserts that setNextOrderID followed by a read
     * from another thread sees the value within a reasonable bound.
     */
    @Test
    public void cross_thread_set_should_be_visible() throws Exception {
        OrderIdFactory f = new OrderIdFactory();
        AtomicInteger observed = new AtomicInteger(-1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try {
                start.await();
                long deadline = System.currentTimeMillis() + 1000;
                while (System.currentTimeMillis() < deadline) {
                    int v = f.getNextOrderID();
                    if (v == 4242) {
                        observed.set(v);
                        done.countDown();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            }
        });
        reader.start();
        f.setNextOrderID(4242);
        start.countDown();
        boolean saw = done.await(2, TimeUnit.SECONDS);
        assertTrue("reader thread should observe 4242 within 2s", saw);
        assertEquals(4242, observed.get());
    }
}
