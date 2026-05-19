package com.jbooktrader.platform.marketbook;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SnapshotComparatorTest {

    @Test
    public void sorts_by_time_ascending() {
        SnapshotComparator cmp = new SnapshotComparator();
        List<MarketSnapshot> list = new ArrayList<>();
        list.add(new MarketSnapshot(5000L, 0, 100, 101, 0));
        list.add(new MarketSnapshot(2000L, 0, 100, 101, 0));
        list.add(new MarketSnapshot(7000L, 0, 100, 101, 0));
        list.sort(cmp);
        assertEquals(2000L, list.get(0).getTime());
        assertEquals(5000L, list.get(1).getTime());
        assertEquals(7000L, list.get(2).getTime());
    }

    @Test
    public void equal_times_compare_zero() {
        SnapshotComparator cmp = new SnapshotComparator();
        MarketSnapshot a = new MarketSnapshot(1L, 0, 100, 101, 0);
        MarketSnapshot b = new MarketSnapshot(1L, 0, 100, 101, 0);
        assertEquals(0, cmp.compare(a, b));
    }
}
