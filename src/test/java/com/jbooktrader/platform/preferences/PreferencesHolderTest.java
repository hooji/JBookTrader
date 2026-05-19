package com.jbooktrader.platform.preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PreferencesHolderTest {

    private String savedHost;
    private String savedMaxLeverage;

    @Before
    public void saveDefaults() {
        PreferencesHolder p = PreferencesHolder.getInstance();
        savedHost = p.get(JBTPreferences.Host);
        savedMaxLeverage = p.get(JBTPreferences.MaxLeverage);
    }

    @After
    public void restoreDefaults() {
        PreferencesHolder p = PreferencesHolder.getInstance();
        p.set(JBTPreferences.Host, savedHost);
        p.set(JBTPreferences.MaxLeverage, savedMaxLeverage);
    }

    @Test
    public void singleton_returns_same_instance() {
        assertSame(PreferencesHolder.getInstance(), PreferencesHolder.getInstance());
    }

    @Test
    public void set_then_get_roundtrips_string() {
        PreferencesHolder p = PreferencesHolder.getInstance();
        p.set(JBTPreferences.Host, "test-host");
        assertEquals("test-host", p.get(JBTPreferences.Host));
    }

    @Test
    public void getInt_returns_default_when_not_set() {
        PreferencesHolder p = PreferencesHolder.getInstance();
        // Port has default "7496"
        assertEquals(7496, p.getInt(JBTPreferences.Port));
    }

    @Test
    public void getDouble_parses_default() {
        PreferencesHolder p = PreferencesHolder.getInstance();
        assertEquals(10.0, p.getDouble(JBTPreferences.MaxLeverage), 0);
    }

    /**
     * BUG CR §8.4: A corrupted numeric value in the OS prefs store causes
     * PreferencesHolder.getInt to throw NumberFormatException with no
     * graceful fallback. Document the current behavior.
     */
    @Test(expected = NumberFormatException.class)
    public void getInt_throws_on_non_numeric_value() {
        PreferencesHolder p = PreferencesHolder.getInstance();
        p.set(JBTPreferences.MaxLeverage, "not-a-number");
        p.getInt(JBTPreferences.MaxLeverage);
    }
}
