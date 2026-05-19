package com.jbooktrader.platform.snapshotwriter;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

/**
 * Tests for {@link TimeFilter}. Tied to CODE_REVIEW.md §9.3.
 */
public class TimeFilterTest {

    @Test
    public void in_range_is_recordable() {
        TimeFilter f = new TimeFilter(7, 16);
        assertTrue(f.isRecordable(epochNY(2026, 5, 13, 10, 0, 0)));
    }

    @Test
    public void before_from_hour_not_recordable() {
        TimeFilter f = new TimeFilter(7, 16);
        assertFalse(f.isRecordable(epochNY(2026, 5, 13, 6, 59, 59)));
    }

    @Test
    public void at_from_hour_recordable() {
        TimeFilter f = new TimeFilter(7, 16);
        assertTrue(f.isRecordable(epochNY(2026, 5, 13, 7, 0, 0)));
    }

    /**
     * Today the upper bound is inclusive at the *top of the hour* — 16:00:00
     * is included but 16:00:01 is not. That's surprising behavior.
     */
    @Test
    public void at_top_of_to_hour_recordable() {
        TimeFilter f = new TimeFilter(7, 16);
        assertTrue(f.isRecordable(epochNY(2026, 5, 13, 16, 0, 0)));
    }

    @Test
    public void one_second_past_top_of_to_hour_not_recordable() {
        TimeFilter f = new TimeFilter(7, 16);
        assertFalse(f.isRecordable(epochNY(2026, 5, 13, 16, 0, 1)));
    }

    @Test
    public void late_morning_recordable() {
        TimeFilter f = new TimeFilter(7, 16);
        assertTrue(f.isRecordable(epochNY(2026, 5, 13, 11, 30, 0)));
    }

    @Test
    public void late_evening_not_recordable() {
        TimeFilter f = new TimeFilter(7, 16);
        assertFalse(f.isRecordable(epochNY(2026, 5, 13, 22, 0, 0)));
    }

    private static long epochNY(int y, int mo, int d, int h, int mi, int s) {
        return ZonedDateTime.of(LocalDate.of(y, mo, d).atTime(h, mi, s),
                ZoneId.of("America/New_York")).toInstant().toEpochMilli();
    }
}
