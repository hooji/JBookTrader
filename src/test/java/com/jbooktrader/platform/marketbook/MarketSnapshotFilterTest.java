package com.jbooktrader.platform.marketbook;

import com.toedter.calendar.JTextFieldDateEditor;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.*;

/**
 * Tests for {@link MarketSnapshotFilter}. Tied to CODE_REVIEW.md §1.10.
 */
public class MarketSnapshotFilterTest {

    @Test
    public void contains_inside_range() {
        MarketSnapshotFilter f = filter("January 1, 2026", "December 31, 2026");
        long t = epochNY(2026, 5, 13, 12, 0, 0);
        assertTrue(f.contains(t));
    }

    @Test
    public void contains_at_from_boundary() {
        MarketSnapshotFilter f = filter("January 1, 2026", "December 31, 2026");
        long t = epochNY(2026, 1, 1, 0, 0, 0);
        assertTrue(f.contains(t));
    }

    @Test
    public void contains_at_to_boundary_2359_59() {
        MarketSnapshotFilter f = filter("January 1, 2026", "December 31, 2026");
        long t = epochNY(2026, 12, 31, 23, 59, 59);
        assertTrue(f.contains(t));
    }

    @Test
    public void rejects_after_to_boundary() {
        MarketSnapshotFilter f = filter("January 1, 2026", "December 31, 2026");
        long t = epochNY(2027, 1, 1, 0, 0, 0);
        assertFalse(f.contains(t));
    }

    @Test
    public void rejects_before_from_boundary() {
        MarketSnapshotFilter f = filter("January 1, 2026", "December 31, 2026");
        long t = epochNY(2025, 12, 31, 23, 59, 59);
        assertFalse(f.contains(t));
    }

    @Test(expected = RuntimeException.class)
    public void rejects_from_after_to() {
        filter("December 31, 2026", "January 1, 2026");
    }

    /**
     * BUG CR §1.10: MarketSnapshotFilter hard-codes America/New_York as the
     * date-interpretation timezone, ignoring the file's declared timezone.
     * For a Tokyo data file, dates entered in the UI would be interpreted
     * 13–14 hours off.
     *
     * This test calls out that the filter does NOT accept a timezone — it
     * documents the limitation rather than asserting the (currently
     * impossible) correct behavior.
     */
    @Test
    public void filter_constructor_has_no_timezone_parameter() {
        // Documenting that there's no way to pass a timezone into the filter.
        // When the bug is fixed by exposing a timezone, this comment becomes
        // stale and the test should be replaced with positive coverage.
        try {
            MarketSnapshotFilter.class.getConstructor(JTextFieldDateEditor.class,
                    JTextFieldDateEditor.class, java.util.TimeZone.class);
            fail("the filter now accepts a timezone — update this test");
        } catch (NoSuchMethodException expected) {
            // Today: no timezone parameter exists. That's the bug.
        }
    }

    /**
     * BUG CR §1.10: toDate is set to 23:59:59.000 (millis=0), so a snapshot
     * at exactly 23:59:59.500 should be included but the filter's `time <=
     * toDate` check considers it outside the range.
     *
     * Expected to FAIL until toDate is set to 23:59:59.999 (or the check
     * uses a half-open <=).
     */
    @Test
    public void to_boundary_should_include_sub_second() {
        MarketSnapshotFilter f = filter("January 1, 2026", "December 31, 2026");
        long t = epochNY(2026, 12, 31, 23, 59, 59) + 500; // +500 ms
        assertTrue("23:59:59.500 on the to-date should be included",
                f.contains(t));
    }

    // ---- helpers ----

    private static MarketSnapshotFilter filter(String from, String to) {
        return new MarketSnapshotFilter(editor(from), editor(to));
    }

    private static JTextFieldDateEditor editor(String prettyDate) {
        try {
            JTextFieldDateEditor e = new JTextFieldDateEditor();
            SimpleDateFormat sdf = new SimpleDateFormat("MMMMM d, yyyy");
            sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
            Date d = sdf.parse(prettyDate);
            e.setDate(d);
            return e;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static long epochNY(int y, int mo, int d, int h, int mi, int s) {
        return ZonedDateTime.of(LocalDate.of(y, mo, d).atTime(h, mi, s),
                ZoneId.of("America/New_York")).toInstant().toEpochMilli();
    }
}
