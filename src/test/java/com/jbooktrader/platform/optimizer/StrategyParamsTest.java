package com.jbooktrader.platform.optimizer;

import org.junit.Test;

import static org.junit.Assert.*;

public class StrategyParamsTest {

    @Test
    public void newly_constructed_params_is_empty() {
        StrategyParams p = new StrategyParams();
        assertEquals(0, p.size());
        assertTrue(p.getAll().isEmpty());
    }

    @Test
    public void add_appends_in_order() {
        StrategyParams p = new StrategyParams();
        p.add("a", 0, 10, 1, 5);
        p.add("b", 0, 20, 2, 10);
        assertEquals(2, p.size());
        assertEquals("a", p.get(0).getName());
        assertEquals("b", p.get(1).getName());
    }

    @Test
    public void get_by_name_finds_param() {
        StrategyParams p = new StrategyParams();
        p.add("Period", 1, 100, 1, 50);
        assertEquals("Period", p.get("Period").getName());
        assertEquals(50, p.get("Period").getValue());
    }

    @Test(expected = RuntimeException.class)
    public void get_by_unknown_name_throws() {
        StrategyParams p = new StrategyParams();
        p.add("Period", 1, 100, 1, 50);
        p.get("DoesNotExist");
    }

    @Test
    public void copy_constructor_deep_copies() {
        StrategyParams src = new StrategyParams();
        src.add("a", 0, 10, 1, 5);
        StrategyParams dst = new StrategyParams(src);
        // Mutating the copy should not affect the source.
        dst.get(0).setValue(999);
        assertEquals(5, src.get(0).getValue());
        assertEquals(999, dst.get(0).getValue());
    }

    @Test
    public void getKey_separates_values_with_slash() {
        StrategyParams p = new StrategyParams();
        p.add("a", 0, 10, 1, 3);
        p.add("b", 0, 10, 1, 7);
        assertEquals("3/7", p.getKey());
    }

    @Test
    public void getKey_for_empty_is_empty_string() {
        StrategyParams p = new StrategyParams();
        assertEquals("", p.getKey());
    }
}
