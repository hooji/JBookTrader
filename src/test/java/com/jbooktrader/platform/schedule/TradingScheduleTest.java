package com.jbooktrader.platform.schedule;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class TradingScheduleTest {

    @Test(expected = RuntimeException.class)
    public void invalid_timezone_rejected() {
        new TradingSchedule("09:30", "16:00", "Mars/Olympus_Mons");
    }

    @Test(expected = RuntimeException.class)
    public void end_before_start_rejected() {
        new TradingSchedule("16:00", "09:30", "America/New_York");
    }

    @Test(expected = RuntimeException.class)
    public void start_equal_end_rejected() {
        new TradingSchedule("12:00", "12:00", "America/New_York");
    }

    @Test(expected = RuntimeException.class)
    public void malformed_time_rejected() {
        new TradingSchedule("25:00", "16:00", "America/New_York");
    }

    @Test(expected = RuntimeException.class)
    public void hours_out_of_range_rejected() {
        new TradingSchedule("09:30", "26:00", "America/New_York");
    }

    @Test(expected = RuntimeException.class)
    public void minutes_out_of_range_rejected() {
        new TradingSchedule("09:60", "16:00", "America/New_York");
    }

    @Test(expected = RuntimeException.class)
    public void missing_colon_rejected() {
        new TradingSchedule("0930", "1600", "America/New_York");
    }

    @Test
    public void contains_true_inside_window() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "America/New_York");
        long t = epochNY(2026, 5, 13, 10, 0);
        assertTrue(s.contains(t));
    }

    @Test
    public void contains_false_outside_window() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "America/New_York");
        long t = epochNY(2026, 5, 13, 17, 0);
        assertFalse(s.contains(t));
    }

    @Test
    public void contains_at_start_boundary_is_inclusive() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "America/New_York");
        long t = epochNY(2026, 5, 13, 9, 30);
        assertTrue(s.contains(t));
    }

    @Test
    public void contains_at_end_boundary_is_exclusive() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "America/New_York");
        long t = epochNY(2026, 5, 13, 16, 0);
        assertFalse(s.contains(t));
    }

    @Test
    public void contains_advances_calendar_to_next_day() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "America/New_York");
        // Two days later, inside the window
        long t = epochNY(2026, 5, 15, 10, 0);
        assertTrue(s.contains(t));
    }

    @Test
    public void getRemainingTime_positive_inside_window() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "America/New_York");
        long t = epochNY(2026, 5, 13, 10, 0);
        long remaining = s.getRemainingTime(t);
        // 6 hours = 21600000 ms
        assertTrue("expected ~6 hours, got " + remaining,
                remaining > 0 && remaining <= 6 * 60 * 60 * 1000L);
    }

    @Test
    public void toString_includes_start_end_timezone() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "America/New_York");
        String text = s.toString();
        assertTrue(text.contains("09:30"));
        assertTrue(text.contains("16:00"));
        assertTrue(text.contains("America/New_York"));
    }

    @Test
    public void getTimeZone_returns_configured_timezone() {
        TradingSchedule s = new TradingSchedule("09:30", "16:00", "Asia/Tokyo");
        assertEquals("Asia/Tokyo", s.getTimeZone().getID());
    }

    private static long epochNY(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDate.of(year, month, day).atTime(hour, minute),
                ZoneId.of("America/New_York")).toInstant().toEpochMilli();
    }
}
