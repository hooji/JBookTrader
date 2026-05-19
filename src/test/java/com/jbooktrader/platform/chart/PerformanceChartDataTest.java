package com.jbooktrader.platform.chart;

import com.jbooktrader.platform.marketbook.MarketSnapshot;
import org.jfree.data.xy.OHLCDataItem;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Tests for {@link PerformanceChartData}. Tied to CODE_REVIEW.md §1.15, §1.16.
 *
 * Reading internal lists from a chart-data object built by the backtest
 * thread is normally hard — but the chart code itself does the same trick
 * (toArray on those lists). We use reflection so the test can be written
 * without needing PerformanceChart on the EDT.
 */
public class PerformanceChartDataTest {

    @SuppressWarnings("unchecked")
    private static List<OHLCDataItem> prices(PerformanceChartData pcd) throws Exception {
        Field f = PerformanceChartData.class.getDeclaredField("prices");
        f.setAccessible(true);
        return (List<OHLCDataItem>) f.get(pcd);
    }

    @SuppressWarnings("unchecked")
    private static List<TimedValue> rawProfits(PerformanceChartData pcd) throws Exception {
        Field f = PerformanceChartData.class.getDeclaredField("strategyProfits");
        f.setAccessible(true);
        return (List<TimedValue>) f.get(pcd);
    }

    @SuppressWarnings("unchecked")
    private static List<OHLCDataItem> strategyPnL(PerformanceChartData pcd) throws Exception {
        Field f = PerformanceChartData.class.getDeclaredField("strategyPnL");
        f.setAccessible(true);
        return (List<OHLCDataItem>) f.get(pcd);
    }

    private static PerformanceChartData fresh() {
        return new PerformanceChartData(BarSize.Second1, Collections.emptyList(), "S");
    }

    @Test
    public void newly_constructed_is_empty() {
        PerformanceChartData pcd = fresh();
        assertTrue(pcd.isEmpty());
    }

    @Test
    public void update_with_one_snapshot_does_not_immediately_flush_bar() throws Exception {
        PerformanceChartData pcd = fresh();
        pcd.update(new MarketSnapshot("ES", 1000L, 0, 100, 101, 1));
        // The current bar is in-flight; nothing flushed to `prices` yet.
        assertEquals(0, prices(pcd).size());
    }

    @Test
    public void two_updates_in_different_bars_flushes_the_first() throws Exception {
        PerformanceChartData pcd = fresh();
        // BarSize.Second1 = 1000ms
        pcd.update(new MarketSnapshot("ES", 1000L, 0, 100, 101, 1));
        pcd.update(new MarketSnapshot("ES", 2000L, 0, 100, 101, 1));
        // Second update is a new bar; first is now flushed.
        assertEquals(1, prices(pcd).size());
    }

    /**
     * BUG CR §1.15: timestamps that go backwards silently overwrite the
     * current bar instead of being rejected or flushed.
     */
    @Test
    public void backwards_timestamps_should_not_silently_overwrite() throws Exception {
        PerformanceChartData pcd = fresh();
        pcd.update(new MarketSnapshot("ES", 5000L, 0, 100, 101, 1));
        pcd.update(new MarketSnapshot("ES", 6000L, 0, 110, 111, 1));
        int sizeAfterForward = prices(pcd).size();
        // Now a backwards update.
        pcd.update(new MarketSnapshot("ES", 4000L, 0, 90, 91, 1));
        // Expectation: either an exception, or the backwards data is
        // dropped, or it lands in a *new* historical bar. What must NOT
        // happen is silent corruption of the current bar.
        // Today this is exactly what happens — the new value updates the
        // current bar. We assert that the prices list has *grown* (a new
        // bar was added) which catches the bug.
        int sizeAfterBackward = prices(pcd).size();
        // Today: sizeAfterBackward == sizeAfterForward (no new bar created;
        // value silently merged into current). Expected: > sizeAfterForward.
        assertTrue("backwards update should not be merged into the current bar (today: " +
                sizeAfterForward + " -> " + sizeAfterBackward + ")",
                sizeAfterBackward > sizeAfterForward);
    }

    /**
     * Boundary case: a timestamp exactly on a bar boundary lives in the
     * *previous* bar (because adjustmentValue == 0 when time % freq == 0).
     */
    @Test
    public void exact_boundary_timestamp_belongs_to_previous_bar() throws Exception {
        PerformanceChartData pcd = fresh();
        // 0 ms is the very first bar boundary; 1000 ms is the next.
        pcd.update(new MarketSnapshot("ES", 0L, 0, 100, 101, 1));
        pcd.update(new MarketSnapshot("ES", 1000L, 0, 110, 111, 1));
        // First update is at barTime 0; second is at barTime 1000.
        assertEquals("two distinct bars expected", 1, prices(pcd).size());
        OHLCDataItem first = prices(pcd).get(0);
        assertEquals(0L, first.getDate().getTime());
    }

    @Test
    public void raw_profits_grow_unbounded_with_updates() throws Exception {
        // CR §4.3: there is no retention bound on raw TimedValue lists.
        PerformanceChartData pcd = fresh();
        for (int i = 0; i < 1000; i++) {
            pcd.updateStrategyPnL(new TimedValue(i * 1000L, i * 0.1));
        }
        assertEquals(1000, rawProfits(pcd).size());
    }

    /**
     * Concurrent reads from the EDT-simulator while writes happen from the
     * worker-simulator should not produce
     * ConcurrentModificationException.  This is the closest we can get
     * to the real (Swing-rendering) race without spinning up a real chart.
     *
     * Expected to FAIL when CME is thrown. Could pass spuriously, so we
     * loop a few times to increase the odds of catching it.
     */
    @Test
    public void concurrent_update_and_read_does_not_throw() throws Exception {
        for (int trial = 0; trial < 3; trial++) {
            PerformanceChartData pcd = fresh();
            AtomicReference<Throwable> err = new AtomicReference<>();
            CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < 50_000; i++) {
                        pcd.updateStrategyPnL(new TimedValue(i * 1000L, i * 0.01));
                    }
                } catch (Throwable t) {
                    err.set(t);
                }
            });
            CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < 5000; i++) {
                        // Defensive copy to iterate — like JFreeChart's toArray.
                        List<TimedValue> copy;
                        try {
                            copy = new ArrayList<>(rawProfits(pcd));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        // Touch elements to trigger CME if any.
                        for (TimedValue tv : copy) {
                            double ignored = tv.getValue();
                        }
                    }
                } catch (Throwable t) {
                    err.set(t);
                }
            });
            writer.get();
            reader.get();
            // We may or may not catch the race; just assert if it fired,
            // it wasn't a CME.
            if (err.get() != null) {
                fail("concurrent update/read produced an exception: " + err.get());
            }
        }
    }
}
