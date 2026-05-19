package com.jbooktrader.platform.preferences;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link JBTPreferences}. Tied to CODE_REVIEW.md §8.5.
 *
 * Every numeric default should parse cleanly as the type the code expects.
 * Catches future typos like "10x" in a default that's read with parseInt.
 */
public class JBTPreferencesTest {

    @Test
    public void all_prefs_have_non_null_name() {
        for (JBTPreferences p : JBTPreferences.values()) {
            assertNotNull("pref " + p + " has null name", p.getName());
        }
    }

    @Test
    public void all_prefs_have_non_null_default() {
        for (JBTPreferences p : JBTPreferences.values()) {
            assertNotNull("pref " + p + " has null default", p.getDefault());
        }
    }

    @Test
    public void numeric_int_defaults_parse_cleanly() {
        // These prefs are read via PreferencesHolder.getInt
        JBTPreferences[] intPrefs = {
                JBTPreferences.Port,
                JBTPreferences.ClientID,
                JBTPreferences.WebAccessPort,
                JBTPreferences.PotfolioOptimizerWindowWidth,
                JBTPreferences.PotfolioOptimizerWindowHeight,
                JBTPreferences.UpperChartWeight,
                JBTPreferences.MarketDataTimeoutSeconds,
                JBTPreferences.OpenOrderTimeoutSeconds,
                JBTPreferences.SmtpPort,
                JBTPreferences.OptimizerMinTrades,
                JBTPreferences.OptimizerWindowWidth,
                JBTPreferences.OptimizerWindowHeight,
                JBTPreferences.MainWindowWidth,
                JBTPreferences.MainWindowHeight,
                JBTPreferences.PerformanceChartWidth,
                JBTPreferences.PerformanceChartHeight,
                JBTPreferences.DivideAndConquerCoverage,
                JBTPreferences.StrategiesPerProcessor,
                JBTPreferences.OptimizationMapWidth,
                JBTPreferences.OptimizationMapHeight,
        };
        for (JBTPreferences p : intPrefs) {
            try {
                Integer.parseInt(p.getDefault());
            } catch (NumberFormatException e) {
                fail("default for " + p + " (\"" + p.getDefault() + "\") does not parse as int");
            }
        }
    }

    @Test
    public void numeric_double_defaults_parse_cleanly() {
        JBTPreferences[] doublePrefs = {
                JBTPreferences.MaxLeverage,
        };
        for (JBTPreferences p : doublePrefs) {
            try {
                Double.parseDouble(p.getDefault());
            } catch (NumberFormatException e) {
                fail("default for " + p + " (\"" + p.getDefault() + "\") does not parse as double");
            }
        }
    }
}
