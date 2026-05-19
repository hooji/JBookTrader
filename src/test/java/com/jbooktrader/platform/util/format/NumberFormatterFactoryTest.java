package com.jbooktrader.platform.util.format;

import org.junit.Test;

import java.text.DecimalFormat;

import static org.junit.Assert.*;

public class NumberFormatterFactoryTest {

    @Test
    public void no_grouping_by_default() {
        DecimalFormat f = NumberFormatterFactory.getNumberFormatter(2);
        assertEquals("1234.57", f.format(1234.567));
    }

    @Test
    public void with_grouping_uses_comma_separator() {
        DecimalFormat f = NumberFormatterFactory.getNumberFormatter(0, true);
        assertEquals("1,234", f.format(1234));
    }

    @Test
    public void zero_fraction_digits_uses_bankers_rounding() {
        DecimalFormat f = NumberFormatterFactory.getNumberFormatter(0);
        assertEquals("12", f.format(12.4));
        // DecimalFormat's default rounding mode is HALF_EVEN ("banker's
        // rounding"), so 12.5 rounds to 12 (nearest even) and 13.5 to 14.
        // This is documented because it surprises users who expect 12.5 → 13.
        assertEquals("12", f.format(12.5));
        assertEquals("14", f.format(13.5));
        // For non-half values the rounding is normal:
        assertEquals("13", f.format(12.6));
    }

    @Test
    public void decimal_separator_is_dot() {
        DecimalFormat f = NumberFormatterFactory.getNumberFormatter(2);
        assertTrue(f.format(1.5).contains("."));
    }
}
