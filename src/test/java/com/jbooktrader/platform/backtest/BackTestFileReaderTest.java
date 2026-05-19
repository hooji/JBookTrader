package com.jbooktrader.platform.backtest;

import com.jbooktrader.platform.marketbook.MarketSnapshot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Tests for {@link BackTestFileReader}. Tied to CODE_REVIEW.md §1.3, §4.2.
 */
public class BackTestFileReaderTest {

    private Path tempFile;

    @Before
    public void setUp() throws IOException {
        tempFile = Files.createTempFile("jbt-test-", ".txt");
    }

    @After
    public void tearDown() throws IOException {
        if (tempFile != null) Files.deleteIfExists(tempFile);
    }

    private static String HEADER =
            "# header\n" +
            "timeZone=America/New_York\n";

    /** Stub progress listener. */
    private static ProgressListener nullProgress() {
        return new ProgressListener() {
            @Override public void setProgress(long count, long iterations, String text) {}
            @Override public void setProgress(String progressText) {}
            @Override public boolean isCancelled() { return false; }
        };
    }

    @Test(expected = RuntimeException.class)
    public void missing_file_throws() {
        new BackTestFileReader("/nonexistent/path/file.txt", null);
    }

    @Test
    public void wellformed_file_loads_expected_number_of_snapshots() throws IOException {
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 10; i++) {
            // Date format MMddyy (6 chars), time HHmmss (6 chars).
            sb.append(String.format("010126,07%02d00,1.0,100.0,100.5,1%n", i));
        }
        Files.writeString(tempFile, sb.toString());

        BackTestFileReader r = new BackTestFileReader(tempFile.toString(), null);
        List<MarketSnapshot> snaps = r.load(nullProgress());
        assertEquals(10, snaps.size());
    }

    /**
     * BUG CR §4.2 / §1.3: The cache is static. A second `new BackTestFileReader`
     * pointing at the same file returns the cached snapshot list.
     *
     * Today this is the desired behavior. The test documents it; if/when the
     * cache is replaced (e.g., for memory/concurrency reasons), this test
     * needs to be revisited.
     */
    @Test
    public void second_load_uses_cache() throws IOException {
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 5; i++) {
            sb.append(String.format("010126,07%02d00,1.0,100.0,100.5,1%n", i));
        }
        Files.writeString(tempFile, sb.toString());

        BackTestFileReader r1 = new BackTestFileReader(tempFile.toString(), null);
        List<MarketSnapshot> first = r1.load(nullProgress());
        BackTestFileReader r2 = new BackTestFileReader(tempFile.toString(), null);
        List<MarketSnapshot> second = r2.load(nullProgress());
        // Same list reference — both readers see the cached instance.
        assertSame("cache returns the same list instance", first, second);
    }

    /**
     * BUG CR §1.3: The cache key is "fileName + ',' + fileSize". If a file's
     * contents change but its byte length stays the same (very plausible:
     * the same number of bytes per line), the cache returns stale data.
     *
     * Expected to FAIL until the cache key includes a hash or modification time.
     */
    @Test
    public void cache_should_invalidate_when_contents_change_but_size_does_not() throws IOException {
        // Two files with the same number of bytes but different contents.
        String a = HEADER + "010126,070000,1.00,100.0,100.5,1\n";
        String b = HEADER + "010126,070000,9.99,100.0,100.5,1\n";
        assertEquals("test fixture should produce same-length contents",
                a.length(), b.length());
        Files.writeString(tempFile, a);
        BackTestFileReader r1 = new BackTestFileReader(tempFile.toString(), null);
        List<MarketSnapshot> first = r1.load(nullProgress());
        double balanceA = first.get(0).getBalance();
        assertEquals(1.00, balanceA, 1e-9);

        Files.writeString(tempFile, b);
        BackTestFileReader r2 = new BackTestFileReader(tempFile.toString(), null);
        List<MarketSnapshot> second = r2.load(nullProgress());
        double balanceB = second.get(0).getBalance();
        assertEquals("cache should have invalidated and seen new contents",
                9.99, balanceB, 1e-9);
    }

    /**
     * BUG CR §1.3: With a static cache and concurrent callers (portfolio
     * backtest, parallel optimizer workers can hit different files), races
     * are possible. Run two concurrent loads and assert at least both
     * return a non-empty list with no exception.
     */
    @Test
    public void concurrent_loads_do_not_throw() throws Exception {
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 100; i++) {
            sb.append(String.format("010126,07%02d%02d,1.0,100.0,100.5,1%n", i / 60, i % 60));
        }
        Files.writeString(tempFile, sb.toString());

        AtomicLong sizeA = new AtomicLong();
        AtomicLong sizeB = new AtomicLong();

        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> {
            BackTestFileReader r = new BackTestFileReader(tempFile.toString(), null);
            sizeA.set(r.load(nullProgress()).size());
        });
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> {
            BackTestFileReader r = new BackTestFileReader(tempFile.toString(), null);
            sizeB.set(r.load(nullProgress()).size());
        });

        a.get();
        b.get();
        assertTrue("concurrent load A should see snapshots", sizeA.get() > 0);
        assertTrue("concurrent load B should see snapshots", sizeB.get() > 0);
    }
}
