package com.jbooktrader.platform.util;

import com.jbooktrader.platform.model.Dispatcher;

/**
 * Helpers for tests that need a partially-initialized {@link Dispatcher}.
 *
 * Dispatcher.init() creates reports/ and marketData/ directories and scans
 * the classpath for strategies. For unit tests we want the cheap setup
 * without strategy discovery (which loads every classpath class and
 * triggers static initializers).
 *
 * Use {@link #ensureInitialised()} from a @BeforeClass when you need a
 * working EventReport or a reports directory.
 */
public final class TestSupport {

    private static volatile boolean initialised;

    private TestSupport() {}

    /**
     * Initialise Dispatcher exactly once per JVM. Idempotent.
     */
    public static synchronized void ensureInitialised() {
        if (initialised) return;
        try {
            Dispatcher.getInstance().init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        initialised = true;
    }
}
