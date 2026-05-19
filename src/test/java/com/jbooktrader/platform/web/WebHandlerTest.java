package com.jbooktrader.platform.web;

import com.jbooktrader.platform.model.Dispatcher;
import com.jbooktrader.platform.model.Mode;
import com.jbooktrader.platform.util.TestSupport;
import com.sun.net.httpserver.HttpExchange;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * Tests for {@link WebHandler}. Tied to CODE_REVIEW.md §1.20.
 *
 * The headline bug: the handler concatenates the request path onto a base
 * directory with no canonicalisation. A path like /../../../etc/passwd.htm
 * escapes the reports directory and reads any file the JVM can read.
 */
public class WebHandlerTest {

    @BeforeClass
    public static void initDispatcher() {
        // WebHandler has a static initializer that reads reportsDir and
        // resourcesDir from Dispatcher. Force Dispatcher.init() first, and
        // give Dispatcher a mode (the index page reads it).
        TestSupport.ensureInitialised();
        if (Dispatcher.getInstance().getMode() == null) {
            Dispatcher.getInstance().setMode(Mode.BackTest);
        }
    }

    private static HttpExchange exchangeFor(String pathAndQuery) throws IOException {
        HttpExchange ex = Mockito.mock(HttpExchange.class);
        Mockito.when(ex.getRequestURI()).thenReturn(URI.create(pathAndQuery));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        Mockito.when(ex.getResponseBody()).thenReturn(body);
        return ex;
    }

    @Test
    public void root_path_returns_html_status() throws IOException {
        HttpExchange ex = exchangeFor("/");
        new WebHandler().handle(ex);
        Mockito.verify(ex).sendResponseHeaders(Mockito.eq(200), Mockito.anyLong());
    }

    @Test
    public void empty_path_returns_html_status() throws IOException {
        HttpExchange ex = exchangeFor("");
        new WebHandler().handle(ex);
        Mockito.verify(ex).sendResponseHeaders(Mockito.eq(200), Mockito.anyLong());
    }

    /**
     * BUG CR §1.20: path traversal. A request to `/../../../etc/passwd.htm`
     * is naively concatenated to reportsDir → reports/../../../etc/passwd.htm
     * → resolves outside the reports directory. The handler reads ANY file
     * the JVM can read.
     *
     * Expected to FAIL until the handler canonicalises the resolved path
     * and rejects requests that escape the served directory.
     */
    @Test
    public void path_traversal_should_be_rejected() throws Exception {
        // We need a file *outside* the reports directory that the test can
        // verify is reachable. Use a temp file with a known body.
        Path target = Files.createTempFile("jbt-traversal-", ".htm");
        Files.writeString(target, "SECRET-CONTENTS");

        try {
            // Build a relative-with-dot-dot path from reports/ to the temp.
            // reportsDir is the JBookTrader working dir + "reports/".
            // From there, "../" gets back to the cwd; we then need to walk
            // up to target's parent.
            Path reportsDir = Paths.get(System.getProperty("user.dir"), "reports").toAbsolutePath();
            Path traversal = reportsDir.relativize(target.toAbsolutePath());
            String requestPath = "/" + traversal.toString().replace('\\', '/');

            HttpExchange ex = exchangeFor(requestPath);
            try {
                new WebHandler().handle(ex);
                // We expect either an exception or a non-2xx response.
                fail("path traversal should not be served — request " + requestPath);
            } catch (FileNotFoundException expected) {
                // Acceptable: the handler refused to open the file. (Unlikely
                // path-traversal also implies rejection, but FNF is fine.)
            } catch (IOException expected) {
                // Any other IOException is also acceptable.
            } catch (SecurityException expected) {
                // Best case: explicit security exception.
            }
        } finally {
            Files.deleteIfExists(target);
        }
    }

    /**
     * BUG CR §1.20: even a benign missing-htm request currently throws an
     * uncaught FileNotFoundException — there's no 404 handling.
     */
    @Test(expected = FileNotFoundException.class)
    public void missing_htm_throws_uncaught_today() throws IOException {
        HttpExchange ex = exchangeFor("/does-not-exist.htm");
        new WebHandler().handle(ex);
    }
}
