package com.jbooktrader.platform.optimizer;

import com.jbooktrader.platform.util.TestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;

import static org.junit.Assert.*;

/**
 * Tests for {@link OptimizerRunner#getTasks(StrategyParams, java.util.Set)}.
 * Tied to CODE_REVIEW.md §6.2.
 *
 * Like the centroid test, we bypass the constructor (which requires a
 * Strategy and OptimizerDialog) and exercise the math via reflection.
 */
public class OptimizerRunnerGetTasksTest {

    @BeforeClass
    public static void initDispatcher() {
        TestSupport.ensureInitialised();
    }

    private static BruteForceOptimizerRunner makeRunner() throws Exception {
        Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
        Field f = unsafeCls.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method alloc = unsafeCls.getMethod("allocateInstance", Class.class);
        return (BruteForceOptimizerRunner) alloc.invoke(unsafe, BruteForceOptimizerRunner.class);
    }

    @SuppressWarnings("unchecked")
    private static LinkedList<StrategyParams> getTasks(OptimizerRunner r, StrategyParams p) throws Exception {
        Method m = OptimizerRunner.class.getDeclaredMethod("getTasks",
                StrategyParams.class, java.util.Set.class);
        m.setAccessible(true);
        return (LinkedList<StrategyParams>) m.invoke(r, p, new HashSet<String>());
    }

    @Test
    public void single_param_step_one_produces_full_range() throws Exception {
        BruteForceOptimizerRunner r = makeRunner();
        StrategyParams p = new StrategyParams();
        p.add("a", 0, 4, 1, 0);
        LinkedList<StrategyParams> tasks = getTasks(r, p);
        // values 0,1,2,3,4 → 5 tasks
        assertEquals(5, tasks.size());
    }

    @Test
    public void single_param_step_two_skips_alternate_values() throws Exception {
        BruteForceOptimizerRunner r = makeRunner();
        StrategyParams p = new StrategyParams();
        p.add("a", 0, 10, 2, 0);
        LinkedList<StrategyParams> tasks = getTasks(r, p);
        // values 0,2,4,6,8,10 → 6 tasks
        assertEquals(6, tasks.size());
    }

    /**
     * BUG CR §6.2: When (max - min) % step != 0, the for-loop excludes max.
     * For min=1, max=10, step=3: values are 1,4,7 (max=10 dropped silently).
     *
     * Expected to FAIL until the loop is fixed to always include max.
     */
    @Test
    public void max_should_be_included_even_when_not_aligned_to_step() throws Exception {
        BruteForceOptimizerRunner r = makeRunner();
        StrategyParams p = new StrategyParams();
        p.add("a", 1, 10, 3, 1);
        LinkedList<StrategyParams> tasks = getTasks(r, p);
        boolean sawMax = false;
        for (StrategyParams t : tasks) {
            if (t.get(0).getValue() == 10) { sawMax = true; break; }
        }
        assertTrue("the user-declared max=10 should appear among generated values",
                sawMax);
    }

    @Test
    public void two_params_produces_cartesian_product() throws Exception {
        BruteForceOptimizerRunner r = makeRunner();
        StrategyParams p = new StrategyParams();
        p.add("a", 0, 2, 1, 0); // 0,1,2 → 3 values
        p.add("b", 0, 3, 1, 0); // 0,1,2,3 → 4 values
        LinkedList<StrategyParams> tasks = getTasks(r, p);
        assertEquals(12, tasks.size());
    }

    @Test
    public void duplicate_keys_deduplicated_via_uniqueParams_set() throws Exception {
        BruteForceOptimizerRunner r = makeRunner();
        Method m = OptimizerRunner.class.getDeclaredMethod("getTasks",
                StrategyParams.class, java.util.Set.class);
        m.setAccessible(true);
        StrategyParams p = new StrategyParams();
        p.add("a", 0, 2, 1, 0);
        java.util.HashSet<String> uniques = new java.util.HashSet<>();
        @SuppressWarnings("unchecked")
        LinkedList<StrategyParams> first  = (LinkedList<StrategyParams>) m.invoke(r, p, uniques);
        @SuppressWarnings("unchecked")
        LinkedList<StrategyParams> second = (LinkedList<StrategyParams>) m.invoke(r, p, uniques);
        assertEquals("first call should produce 3 tasks", 3, first.size());
        assertEquals("second call should produce 0 tasks (all dupes)", 0, second.size());
    }
}
