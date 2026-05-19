package com.jbooktrader.platform.schedule;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.*;

/**
 * Tests for {@link HolidaySchedule}. Tied to CODE_REVIEW.md §1.7, §1.8.
 *
 * The big bug: holidays are hard-coded for 2009-2020 and 2026 only.  Years
 * 2021-2025 are completely missing — Christmas, Independence Day, etc. all
 * count as normal trading days during that gap.  The "gap year" tests
 * below assert that these are recognized as holidays; they will FAIL today.
 */
public class HolidayScheduleTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Test
    public void christmas_2009_is_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue(h.isHolidayOrEarlyClose(epoch(2009, 12, 25)));
    }

    @Test
    public void christmas_2018_is_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue(h.isHolidayOrEarlyClose(epoch(2018, 12, 25)));
    }

    @Test
    public void christmas_2026_is_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue(h.isHolidayOrEarlyClose(epoch(2026, 12, 25)));
    }

    @Test
    public void getHolidayOrEarlyClose_returns_label() {
        HolidaySchedule h = new HolidaySchedule();
        assertEquals("Christmas Day", h.getHolidayOrEarlyClose(epoch(2026, 12, 25)));
    }

    @Test
    public void regular_weekday_is_not_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        // Wednesday May 13, 2026 — no holiday
        assertFalse(h.isHolidayOrEarlyClose(epoch(2026, 5, 13)));
    }

    /**
     * BUG CR §1.7: 2021–2025 has no holiday entries. Any date in those years
     * is treated as a regular trading day. Expected to FAIL until the missing
     * years are added.
     */
    @Test
    public void christmas_2021_should_be_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        // Dec 24, 2021 was observed because Dec 25 fell on Saturday
        assertTrue("2021 Christmas (or its observed date) should be a holiday",
                h.isHolidayOrEarlyClose(epoch(2021, 12, 24))
                || h.isHolidayOrEarlyClose(epoch(2021, 12, 25)));
    }

    @Test
    public void christmas_2022_should_be_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        // Dec 26, 2022 was observed because Dec 25 fell on Sunday
        assertTrue("2022 Christmas (or its observed date) should be a holiday",
                h.isHolidayOrEarlyClose(epoch(2022, 12, 25))
                || h.isHolidayOrEarlyClose(epoch(2022, 12, 26)));
    }

    @Test
    public void christmas_2023_should_be_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue("2023 Christmas should be a holiday",
                h.isHolidayOrEarlyClose(epoch(2023, 12, 25)));
    }

    @Test
    public void christmas_2024_should_be_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue("2024 Christmas should be a holiday",
                h.isHolidayOrEarlyClose(epoch(2024, 12, 25)));
    }

    @Test
    public void christmas_2025_should_be_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue("2025 Christmas should be a holiday",
                h.isHolidayOrEarlyClose(epoch(2025, 12, 25)));
    }

    @Test
    public void independence_day_2023_should_be_holiday() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue("July 4, 2023 should be a holiday",
                h.isHolidayOrEarlyClose(epoch(2023, 7, 4)));
    }

    @Test
    public void juneteenth_2023_should_be_holiday() {
        // Federal holiday since June 2021.
        HolidaySchedule h = new HolidaySchedule();
        assertTrue("Juneteenth 2023 should be a holiday",
                h.isHolidayOrEarlyClose(epoch(2023, 6, 19)));
    }

    /**
     * BUG CR §1.8: "Early Close" and full closure are indistinguishable.
     * isHolidayOrEarlyClose returns true for both, blocking all trading on
     * what should be a shortened-hours day.
     *
     * Today this test passes trivially (returns true); we keep it to document
     * the expected behavior of an "isHoliday" vs "isEarlyClose" distinction
     * that would discriminate.
     */
    @Test
    public void early_close_2026_is_currently_flagged_the_same_as_full_close() {
        HolidaySchedule h = new HolidaySchedule();
        assertTrue("Nov 27 2026 (early close) is conflated with full close",
                h.isHolidayOrEarlyClose(epoch(2026, 11, 27)));
        assertEquals("Early Close", h.getHolidayOrEarlyClose(epoch(2026, 11, 27)));
    }

    private static long epoch(int year, int month, int day) {
        return LocalDate.of(year, month, day)
                .atStartOfDay(NY)
                .toInstant().toEpochMilli();
    }
}
