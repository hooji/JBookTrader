# JBookTrader Code Review

A systematic review of the JBookTrader 2026.1-SNAPSHOT source tree
looking for bugs, edge cases, thread-safety issues, resource leaks, and
correctness problems. Each finding includes a file:line reference, a
description, what could go wrong, and a severity rating.

This is the kind of review intended to make you slightly uncomfortable.
Most items will have never bitten anybody — they are listed because they
*could*, and because they're cheap to fix or test against. Treat the
HIGH severity items as actionable; treat the rest as a punch list to
work through opportunistically.

> Companion: see [TESTING.md](TESTING.md) for the unit tests that would
> catch each of these.

## Contents
- [1. Critical — silent correctness bugs](#1-critical--silent-correctness-bugs)
- [2. Security](#2-security)
- [3. Thread safety](#3-thread-safety)
- [4. Resource and memory](#4-resource-and-memory)
- [5. Numeric edge cases (divide-by-zero, NaN, Infinity)](#5-numeric-edge-cases-divide-by-zero-nan-infinity)
- [6. Optimizer-specific bugs](#6-optimizer-specific-bugs)
- [7. Chart / performance subsystem](#7-chart--performance-subsystem)
- [8. Validation and input handling](#8-validation-and-input-handling)
- [9. Time, timezone, and schedule issues](#9-time-timezone-and-schedule-issues)
- [10. Code quality and dead code](#10-code-quality-and-dead-code)

---

## 1. Critical — silent correctness bugs

### 1.1 `ResultComparator` sorts the wrong way for "lower is better" metrics — HIGH
`src/main/java/com/jbooktrader/platform/optimizer/ResultComparator.java:17-19`
```java
return Double.compare(r2.get(performanceMetric), r1.get(performanceMetric));
```
Always sorts descending. For `MaxSL` (max single loss) and `MaxDD` (max
drawdown) — where *smaller* is better — the optimizer surfaces the
**worst** parameter sets at the top of the results table, and the top-100
written to `<Strategy>Optimizer.htm` are the worst by these metrics.
`Duration` (average trade length) also has no obvious "higher is better"
interpretation. The `PerformanceMetric` enum carries no direction hint;
the comparator treats them all the same.

### 1.2 Multi-pass optimizers never clear `optimizationResults` between passes — HIGH
`src/main/java/com/jbooktrader/platform/optimizer/OptimizerRunner.java:31, 278`
`CentroidOptimizerRunner.java`, `GradientOptimizerRunner.java`,
`DivideAndConquerOptimizerRunner.java`

`OptimizerRunner.run()` clears results at the start of the run but not
between passes. Centroid, Gradient, and Divide-and-Conquer all run
multiple passes, each calling `execute(tasks, passNumber)` which
`addAll`s results to a shared `CopyOnWriteArrayList`. The "top N" used
by the next pass (e.g., D&C's `optimizationResults.get(0..maxIndex)`,
Centroid's `getCentroid()`) therefore picks from the accumulated set
including stale coarse-pass results. Refinement is polluted.

### 1.3 `BackTestFileReader` caches snapshots in static state with no synchronization — HIGH
`src/main/java/com/jbooktrader/platform/backtest/BackTestFileReader.java:23-24, 41-72`

`snapshots` and `cacheKey` are static. `BackTester` (single-strategy
backtest), `PortfolioBackTester` (loops over strategies), and the
optimizer (parallel workers) can all instantiate `BackTestFileReader`
concurrently. The cache check, the `snapshots = new ArrayList<>()`
reset, and the `cacheKey = key` assignment are interleaved with no
synchronization. Possible: one consumer reads a partially-populated
list, or two consumers race to fill it and one sees the other's stale
list.

The cache also has no eviction — once a backtest loads a 1 GB file, the
list is pinned until the JVM exits.

### 1.4 `MarketDataHandler.unsubscribe()` does not actually unsubscribe — HIGH
`src/main/java/com/jbooktrader/platform/ibhandler/MarketDataHandler.java:62-67`
```java
public void unsubscribe() {
    requestId++;
    socket.cancelMktDepth(requestId, true);
    requestId++;
    socket.cancelMktData(requestId);
}
```
It increments `requestId` to a *new* value and cancels that — but no
subscription was ever made with that ID. The original market-depth and
market-data subscriptions made by `subscribe()` (with their actual
request IDs stored in `marketDepths`) are never cancelled. After
disconnect, IB Gateway may continue streaming and the next session will
collide on the same client ID.

### 1.5 `OrderIdFactory` reads/writes `nextOrderID` without synchronization — HIGH
`src/main/java/com/jbooktrader/platform/ibhandler/OrderIdFactory.java:11, 26-37`

`nextOrderID` is a plain `int`. `setNextOrderID` is called from the IB
reader thread (`Trader.nextValidId`), while `getNextOrderID` and
`incrementOrderID` are called from order-placement code paths driven by
the snapshot consumer thread (`StrategyRunner`). Without `volatile` /
synchronized / `AtomicInteger`, increments can be lost (read-modify-write
race) and reads can see stale values — duplicate order IDs are how IB
rejects orders.

### 1.6 `setMode(BackTest)` does not disable email notifications — MEDIUM
`src/main/java/com/jbooktrader/platform/model/Dispatcher.java:228-237`,
`src/main/java/com/jbooktrader/platform/email/Notifier.java:99-111`

`Dispatcher.setMode` toggles `eventReport.disable()` only in
`Optimization` mode; `BackTest` and `BackTestAll` leave reporting (and
the notifier's mode check) enabled. `Notifier.send` checks
`mode != Optimization && != BackTest && != BackTestAll`, so the notifier
*itself* skips sending — good. But trade-notification *strings* are
still built (`PerformanceManager.java:228-231`) and submitted to the
queue, then dropped — wasted work and confusing logs. More importantly,
in `BackTest` mode `EventReport` is fully on, generating large
per-strategy HTML logs even though the user usually doesn't want them.

### 1.7 Holiday schedule has a five-year gap (2021–2025) — HIGH
`src/main/java/com/jbooktrader/platform/schedule/HolidaySchedule.java`

Holidays are hard-coded for 2009–2020 and 2026. **Any backtest, forward
test, or live trade on a date in 2021–2025 will treat every day —
including Christmas and Independence Day — as a normal trading day.**
`PositionManager.setTargetPosition` no-ops on holidays as a safety, so
during those five years that safety is silently disabled.

### 1.8 `HolidaySchedule` conflates "Early Close" with full closure — MEDIUM
`src/main/java/com/jbooktrader/platform/schedule/HolidaySchedule.java`
`src/main/java/com/jbooktrader/platform/position/PositionManager.java:135-138`

`isHolidayOrEarlyClose` returns true for both. `PositionManager.isHoliday`
uses it to block all trading. So days marked "Early Close" — which are
real trading days with shortened hours — are treated as fully closed,
effectively dropping trades.

### 1.9 `ExitScheduler` always schedules exit 24 hours from now — HIGH
`src/main/java/com/jbooktrader/platform/util/ui/ExitScheduler.java:30-34`
```java
LocalDateTime targetTime = LocalDateTime.now().plusDays(1).withHour(hour).withMinute(minute);
```
The intent is "exit at the next 17:00 local". If you start JBookTrader
at 09:00 with `SessionExitTime=17:00`, this computes 17:00 *tomorrow* —
so the app keeps running through today's close and exits *during* the
next trading day. The right computation is "next 17:00, whether that's
today or tomorrow." Also uses system default timezone, which won't match
the trading-instrument timezone for non-NY users.

### 1.10 `MarketSnapshotFilter` ignores the data file's declared timezone — MEDIUM
`src/main/java/com/jbooktrader/platform/marketbook/MarketSnapshotFilter.java:15`
```java
Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
```
Filter dates are interpreted in NY time regardless of the file's
`timeZone=` header. For a Tokyo-time data file, the date filter is off
by 13–14 hours, which can drop or add a day's worth of data at each
boundary.

Also: `toDate` is set to `23:59:59.000` (millis 0), so any snapshot at
exactly `23:59:59.001+` is dropped from the last day of the range.
Trivial in practice but a real off-by-millis.

### 1.11 Strategy class discovery loads every classpath class — MEDIUM
`src/main/java/com/jbooktrader/platform/strategy/StrategyLoader.java:33-85`

`StrategyLoader.getClassNames()` walks every URL on
`System.getProperty("java.class.path")`, including bundled JARs, and
calls `Class.forName(...)` on each. Side-effects from static
initializers in unrelated classes can execute at startup. The IDE
classpath in particular can be huge.

It also excludes anything whose path *contains* `"Test"` — case
sensitive — so a perfectly legitimate strategy named e.g.
`MyTestStrategy.java` is silently dropped. (This came up while preparing
the documentation screenshots — the original shim was named
`QuickTestStrategy` and discovery worked only because it was scanned via
the unpacked classes directory, not the JAR exclusion path.)

### 1.12 `MainFrameController.tradeAll` has no confirmation — MEDIUM
`src/main/java/com/jbooktrader/platform/model/MainFrameController.java:84-91, 201-210`

The menu item *"Trade all"* flips the system to live `Trade` mode and
adds every strategy in the table without asking the user "are you sure
you want to live-trade every strategy?". Combined with the
no-confirmation `--enable-auto-start true` CLI argument, this is a
foot-gun. Compare with *"Suspend trading"* which *does* confirm.

### 1.13 `Trade.slippage*Points` overwrites instead of accumulating — MEDIUM
`src/main/java/com/jbooktrader/platform/performance/Trade.java:17-27`

For a single trade that fills across multiple executions (partial fills,
which IB does send as multiple `execDetails` callbacks),
`updateTotalBought` is called multiple times, but
`slippageBoughtPoints = slippageBoughtPoints` just replaces the previous
value. Cumulative `getSlippageAmount()` then reports only the last
fill's slippage scaled by the total quantity.

### 1.14 `PerformanceManager.updateOnTrade` dereferences `trade` before it is created — HIGH
`src/main/java/com/jbooktrader/platform/performance/PerformanceManager.java:165-172`
```java
if (previousPosition == 0 && position != 0) {
    trade = new Trade(multiplier); ...
}
if (position == 0) {
    trade.setExitTime(snapshotTime);  // NPE if trade was never created
}
```
If the very first `updateOnTrade(...)` is invoked with `previousPosition
== 0 && position == 0` (defensive recovery, recovery from a partial
state, or an over-fill scenario where the strategy ends up exactly
where it started), `trade` is still null and line 171 NPEs.

### 1.15 Performance-chart bar bucketing silently corrupts on out-of-order timestamps — HIGH
`src/main/java/com/jbooktrader/platform/chart/PerformanceChartData.java:91, 118, 142, 171`

```java
if (barTime > strategyPnLbar.getTime()) {
    // flush and start new bar
}
```
The guard is strict `>`. If two updates arrive for the *same* bar time,
they both update the current bar (intended). If a later update arrives
with `barTime < current`, the code silently updates the *current* bar
instead — overwriting a younger bar with older data. Worst case during
multi-strategy backtests where snapshots can interleave across threads.

### 1.16 `PerformanceChartData` lists are read on the EDT while a worker mutates them — HIGH
`src/main/java/com/jbooktrader/platform/chart/PerformanceChartData.java:34-45, 184-198`

`prices`, `strategyPnL`, `portfolioPnL`, indicator lists, and the raw
`strategyProfits`/`portfolioProfits` lists are mutated from the
backtest worker thread (`BackTester`, `PortfolioBackTester`,
`OptimizerWorker`) and read by the EDT (chart rendering, scroll bar
range updates, `MarketTimeLine.getNormalHours()`). No synchronization.
`toArray(...)` is not atomic — `ConcurrentModificationException` or
partial reads are possible.

### 1.17 `MarketTimeLine.getNormalHours()` IOOBE on empty data — HIGH
`src/main/java/com/jbooktrader/platform/chart/MarketTimeLine.java:28`
```java
OHLCDataItem firstItem = items.get(0);
```
Right-click → *Chart* on a strategy whose `PerformanceChartData` is
empty (no snapshots ever fed) → `IndexOutOfBoundsException`. The
upstream check at
`MainFrameController.java:233-235` validates *non-null* PerformanceChartData
but not non-empty.

### 1.18 `DateScrollBar.rangeUpdate` initializes `max` to `Double.MIN_VALUE` — HIGH
`src/main/java/com/jbooktrader/platform/chart/DateScrollBar.java:54`
```java
double max = Double.MIN_VALUE;
```
`Double.MIN_VALUE` is the *smallest positive normal* (~4.9e-324), **not**
the most-negative value. For a PnL series that is entirely negative
(deep drawdown), every value is less than the initial `max`, and the
Y-axis upper bound ends up effectively at zero. The chart displays
correctly until the first positive PnL bar; for a "blew it up early"
strategy it never shows the actual range.

### 1.19 `OptimizationMap` crashes on edge cases — HIGH
`src/main/java/com/jbooktrader/platform/chart/OptimizationMap.java`

Multiple boundary issues that crash the dialog rather than degrading
gracefully:
- Line 81, 160, 207: `optimizationResults.get(0)` without `isEmpty()` check.
- Line 88: `verticalCombo.setSelectedIndex(1)` crashes if the strategy
  has only one parameter.
- Lines 281, 293-294: `(value - min) / (max - min)` and
  `255.0 / (max - min)` divide by zero when every result has the same
  metric value → NaN → `Color(NaN, NaN, NaN)` throws.

### 1.20 Path traversal in `WebHandler` — HIGH
`src/main/java/com/jbooktrader/platform/web/WebHandler.java:85-90`
```java
String path = (resource.endsWith("htm") ? reportsDir : resourcesDir) + resource;
BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path));
```
The `resource` comes directly from `httpExchange.getRequestURI().getPath()`
and is concatenated to a directory prefix with no canonicalisation. A
request for `/../../../etc/passwd.htm` resolves to
`reports/../../../etc/passwd.htm` → outside the reports dir. Combined
with the trivial HTTP Basic auth, this exposes the host filesystem to
anyone who can reach the web port and brute-force the credentials.

### 1.21 `WebAuthenticator` separator collision and timing-attack — MEDIUM
`src/main/java/com/jbooktrader/platform/web/WebAuthenticator.java:19, 23-25`
```java
authPair = prefs.get(WebAccessUser) + "/" + prefs.get(WebAccessPassword);
...
return authPair.equals(userName + "/" + password);
```
- `username="admin/x", password="y"` and `username="admin",
  password="x/y"` both produce `"admin/x/y"`. Collision.
- `String.equals` short-circuits on first mismatching character → timing
  attack on the password (and username), giving roughly one bit of
  information per request to anyone with stable RTT measurements.

---

## 2. Security

In addition to §1.20 (path traversal) and §1.21 (auth):

- **Plaintext passwords in OS preferences.** `WebAccessPassword` and
  `SmtpPassword` are stored as plain strings via
  `java.util.prefs.Preferences` — on Linux, that's a flat XML file under
  `~/.java/.userPrefs/com/jbooktrader/JBookTrader/`.
  *See* `JBTPreferences.java:15-16`, `PreferencesHolder.java:42-43`.
- **No rate limiting on web auth.** The embedded `HttpServer` accepts
  arbitrarily many failed Basic-Auth attempts with no backoff.
- **Web server binds all interfaces.** `MonitoringServer.java:30`
  uses `new InetSocketAddress(port)` (no host arg). Default-bind on
  every interface; combined with the trivial auth and the path
  traversal, this is exploitable.
- **No HTTPS for the embedded server.** Basic Auth credentials and all
  report content traverse the wire in cleartext.
- **HTML injection in reports.** `EventReport.report(String reporter,
  String message)` appends both fields directly into the HTML row. A
  reporter or message string containing `<script>...</script>` ends up
  literally in the report. Low impact for local files; concerning if
  the file is later served via the monitoring HTTP server.

---

## 3. Thread safety

### 3.1 `SimpleDateFormat` / `DecimalFormat` instances shared across threads — HIGH
`SimpleDateFormat` and `DecimalFormat` are documented as **not
thread-safe**. The codebase shares them freely:
- `src/main/java/com/jbooktrader/platform/report/EventReport.java:18-19`
  (`dateFormat`, `timeFormat`) — `EventReport.report()` is called from
  many threads.
- `src/main/java/com/jbooktrader/platform/snapshotwriter/SnapshotWriter.java:22`
  (`dateFormat`, `df6`, `df2`) — `write()` called from the IB callback
  thread.
- `src/main/java/com/jbooktrader/platform/snapshotwriter/TimeFilter.java:13, 23-31`
  (`currentTimeCalendar` shared without sync; `setTimeInMillis` + reads
  not atomic across threads).
- `src/main/java/com/jbooktrader/platform/schedule/HolidaySchedule.java:192-208`
  (`dateFormat`) — guarded by `synchronized` on `getHolidayOrEarlyClose`
  and `isHolidayOrEarlyClose`. OK here.
- `src/main/java/com/jbooktrader/platform/util/ntp/DaySchedule.java:12`
  (static `Calendar cal`) — all access is `synchronized`, but on an
  instance monitor; since the field is static, multiple `DaySchedule`
  instances would share the field without sharing the lock. There's
  currently only one instance in practice; still fragile.
- `src/main/java/com/jbooktrader/platform/optimizer/ComputationalTimeEstimator.java:23`
  (`sdf`) — called from worker threads. Concurrent `sdf.format(...)` is
  the canonical "produces wrong dates or NPEs at random".

### 3.2 `OptimizerRunner.iterationsCompleted` updates Swing from worker threads — HIGH
`src/main/java/com/jbooktrader/platform/optimizer/OptimizerRunner.java:233-243`

`iterationsCompleted` is invoked from `OptimizerWorker.call()` (worker
threads) and inside calls `optimizerDialog.setProgress(...)` →
JProgressBar mutation. `setProgress` *does* wrap in
`SwingUtilities.invokeLater`, so this is actually OK — but
`completedSteps.getAndAdd(...)` is fine (AtomicLong), and
`lastUpdateTime` is a plain `long` accessed by multiple worker threads
with no synchronization. Torn reads possible on 32-bit JVMs; "1 second
throttle" can briefly misfire many times.

### 3.3 `MarketDataHandler.requestId` mutated unsynchronized — MEDIUM
`src/main/java/com/jbooktrader/platform/ibhandler/MarketDataHandler.java:27, 55, 63-66, 76, 107`

`requestId` is incremented from both the IB reader thread (callbacks
into `tickSize`, `updateMktDepth`) and from the main thread
(`subscribe(Contract)` invoked from `OrderManagerAssistant.addStrategy`).
No synchronization or `AtomicInteger`. Possible duplicate IDs assigned
to different subscriptions.

### 3.4 `OptimizerWorker` shares one `MarketBook` and one `IndicatorManager` across strategies — MEDIUM
`src/main/java/com/jbooktrader/platform/optimizer/OptimizerWorker.java:38-39`

A worker holds many strategy instances (up to `StrategiesPerProcessor`,
default 50). All of them share one `MarketBook` and one
`IndicatorManager`. The `IndicatorManager` deduplicates indicators by
key (class name + parameters), which is the perf optimization. But the
shared `MarketBook` carries `isLocked` / `lastTimePriceChanged` state
that is now mixed across strategies — if strategy A's behavior causes
the book to be flagged locked, strategy B's indicators are reset too.
Within a single worker thread this is at least consistent (sequential);
across workers different worker threads have their own books.

### 3.5 `Notifier` dies on the first SMTP error — MEDIUM
`src/main/java/com/jbooktrader/platform/email/Notifier.java:74-83`
```java
try {
    while (!(msg = emailMessageQueue.take()).equals(endMessage)) {
        send(msg, false);
    }
} catch (MessagingException | InterruptedException ee) {
    eventReport.report(ee);
}
```
One transient SMTP failure exits the loop. The worker thread dies
silently and all subsequent `Notifier.submit(...)` calls queue messages
that will never be sent. The user receives no further notifications for
the rest of the JBookTrader process lifetime, with no in-UI indicator.

### 3.6 `Notifier.shutdown()` is sentinel-string based — LOW
`src/main/java/com/jbooktrader/platform/email/Notifier.java:28, 77`

The end-of-stream sentinel is the literal string `"quit"`. If any
real notification message happens to equal that string (unlikely but
possible — e.g., a strategy logs "quit" as a status), the notifier
shuts itself down. Use a separate `running` flag instead.

### 3.7 `EventReport.isEnabled` is a plain boolean — LOW
`src/main/java/com/jbooktrader/platform/report/EventReport.java:21, 44-50, 64-68`

Toggled from the Swing EDT (via `Dispatcher.setMode`) and read from
many threads. Not `volatile`. The visibility flip can be arbitrarily
delayed; in practice nearly always observed.

### 3.8 `MessageDialog.showMessage` is not invoked on the EDT — MEDIUM
`src/main/java/com/jbooktrader/platform/util/ui/MessageDialog.java:15-29`

Called from worker threads (e.g., `OptimizerDialog.showMessage` itself
*does* `SwingUtilities.invokeLater`, but
`OrderManagerAssistant.connect` calls `MessageDialog.showMessage`
directly from the snapshot consumer thread). `JOptionPane.showMessageDialog`
on a non-EDT thread is undefined behavior — can deadlock if any other
thread is holding the AWT tree lock.

---

## 4. Resource and memory

### 4.1 `SnapshotWriter` files are never closed — MEDIUM
`src/main/java/com/jbooktrader/platform/snapshotwriter/SnapshotWriter.java:23-42`

The `PrintWriter` is opened in append mode and the class exposes no
`close()` method. There's a `flush()` on every write so data on disk is
durable, but the OS file handle is held until the JVM exits. With many
contracts (one writer per ticker), file-handle exhaustion is possible
on long-running processes.

### 4.2 `BackTestFileReader` cache holds the full snapshot list forever — MEDIUM
`src/main/java/com/jbooktrader/platform/backtest/BackTestFileReader.java:23, 50-72`

The static `snapshots` field is set on every load and never cleared. On
a multi-GB data file, that's multi-GB pinned for the JVM lifetime even
after the user closes every dialog. Consider weak-reference caching or
explicit eviction.

### 4.3 `PerformanceChartData` grows unbounded — HIGH for long backtests
`src/main/java/com/jbooktrader/platform/chart/PerformanceChartData.java:21-37`

For a multi-day, second-resolution backtest:
- `prices` gets one OHLC item per `BarSize` bucket (default 1 min →
  ~1440 entries/day) — OK.
- `strategyProfits` and `portfolioProfits` get one `TimedValue` per
  raw snapshot — ~86 400 entries/day per strategy, **per object**.
  These coexist with the OHLC lists and serve only as inputs to
  `getTradeProfitSeries()`. For a 200-day backtest, that's ~17 million
  objects per strategy.

Same issue per-indicator: one list of `OHLCDataItem` per indicator.

### 4.4 `WebHandler` reads the whole file into a byte array — MEDIUM
`src/main/java/com/jbooktrader/platform/web/WebHandler.java:87-90`
```java
out = new byte[(int) new File(path).length()];
bis.read(out);
```
- `(int)` cast: > 2 GB report → integer overflow → wrong size →
  truncated read or `NegativeArraySizeException`.
- `bis.read(out)` may return fewer bytes than requested — the rest of
  the array is left as `0x00`.
- No `try-with-resources`; if `bis.read` throws, the stream leaks.
- A 100 MB optimization report served over HTTP buffers fully in heap
  before sending.

### 4.5 Chart frames use `HIDE_ON_CLOSE` by default — MEDIUM
`src/main/java/com/jbooktrader/platform/chart/PerformanceChart.java:120-124`
`src/main/java/com/jbooktrader/platform/chart/OptimizationMap.java:117-124`

Both register a `WindowListener` whose `windowClosing` calls
`setDefaultCloseOperation(DISPOSE_ON_CLOSE)` — but the default is
`HIDE_ON_CLOSE`, so by the time `windowClosing` fires the frame is
already going to be hidden, not disposed. All listeners, charts, and
references to backtest data remain in memory.

### 4.6 `NTPClock` resolves all hosts at startup, blocking on DNS — MEDIUM
`src/main/java/com/jbooktrader/platform/util/ntp/NTPClock.java:62-74`

If DNS is slow or unreachable (no internet, captive portal, etc.),
14 sequential `InetAddress.getByName(...)` calls block JBookTrader
startup. If fewer than 3 resolve, the entire application throws and
exits — there is no degraded-mode fallback to the system clock.

Additionally: three of the 14 servers
(`time-a/b/c.timefreq.bldrdoc.gov`) were retired by NIST years ago.

### 4.7 No max-cartesian-product check in the optimizer — HIGH
`src/main/java/com/jbooktrader/platform/optimizer/OptimizerRunner.java:85-99, 245-274`

`cartesianProduct` materializes the entire product as
`List<List<Integer>>` before any worker starts. With four parameters of
range 100, step 1 (the kind of config a careless user might enter),
that's 100 million combinations × ~40 bytes per outer list ≈ 4 GB. OOM
before the first strategy runs. A pre-flight estimate (count, not
materialize) would be cheap and would let the user choose to abort.

---

## 5. Numeric edge cases (divide-by-zero, NaN, Infinity)

### 5.1 `Trade.getAverageBoughtPrice` / `getAverageSoldPrice` — HIGH
`src/main/java/com/jbooktrader/platform/performance/Trade.java:54, 58`
```java
return totalBought / quantityBought;
return totalSold   / quantitySold;
```
A short-only trade has `quantityBought == 0`; a long-only has
`quantitySold == 0`. Either path returns NaN, which then poisons
`Trade.toString` and `StrategyReport` columns.

### 5.2 `CentroidOptimizerRunner.getCentroid` — HIGH
`src/main/java/com/jbooktrader/platform/optimizer/CentroidOptimizerRunner.java:39-66`

- `cutoff = (int)(size * 0.382)`. For 1 or 2 results, `cutoff == 0` →
  the inner loop never enters → `sumOfPerformance` stays 0 → division
  on line 61 produces NaN centroid → propagates through `Math.floor` /
  `Math.ceil` → `(int)` casts of NaN → `0`. Subsequent pass uses
  silently-bogus bounds.
- Same `sumOfPerformance == 0` when all results have `performanceValue
  <= 0` (e.g., every result is a net loss with `OG=0`).

### 5.3 `GradientOptimizerRunner.getCentroid` — HIGH
`src/main/java/com/jbooktrader/platform/optimizer/GradientOptimizerRunner.java:55, 77`

- `range = max - min` then `x = (value - min) / range` → divide by zero
  when all results have the same metric value.
- `min` is then clamped to 0 *after* `range` is computed → the formula
  uses an inconsistent `min` for the subsequent normalization.
- `sumOfPerformance == 0` (no `value > min`) → NaN centroid same as
  Centroid runner.

### 5.4 `DivideAndConquerOptimizerRunner` divides by `(maxPartsPerDimension - 1)` — MEDIUM
`src/main/java/com/jbooktrader/platform/optimizer/DivideAndConquerOptimizerRunner.java:46`
```java
int step = Math.max(1, (param.getMax() - param.getMin()) / (maxPartsPerDimension - 1));
```
Same pattern in `CentroidOptimizerRunner.java:107` and
`GradientOptimizerRunner.java:123`. If the `DivideAndConquerCoverage`
preference is set to `1` (the user can type any number), divide by
zero. Default is `3` so usually OK.

### 5.5 `StrategyParam` accepts step=0 → infinite loop — MEDIUM
`src/main/java/com/jbooktrader/platform/optimizer/StrategyParam.java:37-71`

No validation in setters. `setStep(0)` allowed; the optimizer's
`for (value = min; value <= max; value += step)` loop never terminates.
`setMin(value)` and `setMax(value)` allow `min > max`. `getRange()`
returns negative.

`getMiddle()` is `(min + max) / 2d` — integer addition happens before
double conversion, so `min + max` can overflow for extreme values.

### 5.6 `PerformanceEvaluator` propagates infinity — HIGH
`src/main/java/com/jbooktrader/platform/performance/PerformanceEvaluator.java:47, 60`

For an "all wins" trade list, `optimalLeverage = optimalGrowth = pi =
Double.POSITIVE_INFINITY`. Line 60 then divides `growth / kellyLeverage`;
if `kellyLeverage == 0` (Maximum-Search returned 0 fallback), divide by
zero → infinity → `Double.compare` treats it deterministically but the
optimizer ranks it at the top.

`PerformanceManager.updateAtEnd` then uses `pi * apd * sqrt(trades)` for
OG; an infinite `pi` ⇒ infinite OG ⇒ infinite metric ⇒ this strategy
"wins" every optimization unconditionally.

### 5.7 `FunctionEvaluator` divides by `elapsedTime` — HIGH
`src/main/java/com/jbooktrader/platform/performance/FunctionEvaluator.java:19, 27`

If all trades share the same timestamp (synthetic test data, or all
fills in one second), `elapsedTime == 0` → `distance` is `NaN` →
kernel weights are `NaN` → Kelly / Youden / Power evaluators return
NaN → MaximumSearch may infinite-loop or return garbage leverage.

`getMaxLeverage() = -1 / largestLoss` returns `-Infinity` when no
losses exist; passed as `right` to MaximumSearch with `left = 0` makes
`right < left` and the golden-section termination condition is wrong.

### 5.8 `BalanceEMA` length 0 produces multiplier 2 — MEDIUM
`src/main/java/com/jbooktrader/indicator/balance/BalanceEMA.java:13-16`

Length=0 → `multiplier = 2.0 / 1.0 = 2.0`. Length=-1 → divide by zero →
Infinity → NaN. No validation. The optimizer can be configured to test
negative or zero values via the `Min Value` field (no clamp).

### 5.9 `MarketDepthModel.getBestPrice()` on empty list — MEDIUM
`src/main/java/com/jbooktrader/platform/marketdepth/MarketDepthModel.java:40-42`

`items.getFirst()` throws `NoSuchElementException` if the list is
empty. The caller (`MarketDepth.update`) gates with `isValidDepth()`,
but `isValidDepth` checks size == maxDepth before calling
`getBestPrice` — so this is OK if the order is preserved, but the
method itself is unsafe and any new caller would trip.

### 5.10 `MarketDepthModel.update` deref of `listIterator.next()` — MEDIUM
`src/main/java/com/jbooktrader/platform/marketdepth/MarketDepthModel.java:33-38`
```java
MarketDepthItem item = items.listIterator(position).next();
if (item != null) { item.set(price, size); }
```
`listIterator(position).next()` throws `NoSuchElementException` if
`position >= items.size()`, and never returns null. The null check is
dead code; the real failure mode is the exception.

### 5.11 `PorfolioBackTestRunner` divides by `strategies.size()` if empty — MEDIUM
`src/main/java/com/jbooktrader/platform/portfolio/PorfolioBackTestRunner.java:54`

If the user unchecks every strategy in the *Portfolio backtest* dialog
and clicks *Optimize*, the for-loop produces zero entries and the
subsequent `aveDuration /= strategies.size()` divides by zero. Result
is NaN displayed in the portfolio summary.

### 5.12 `BackTestFileReader.LineParser` numeric tolerance — LOW
`src/main/java/com/jbooktrader/platform/backtest/LineParser.java:75-79`

`time <= previousTime` rejects same-second snapshots. This is *probably*
intentional (one snapshot per second by design), but the data writer
side (`SnapshotWriter`) doesn't actively guarantee unique seconds. A
data file with two snapshots in the same second throws and aborts the
backtest with a noisy message.

### 5.13 `PerformanceManager.peakNetProfit` starts at zero — LOW
`src/main/java/com/jbooktrader/platform/performance/PerformanceManager.java:36, 218`

A strategy that loses on its first trade gets `peakNetProfit == 0`,
`netProfit == -100`, so `maxDrawdown = peak - net = 100`. The strategy
never had a peak — its drawdown should be measured against the starting
equity, which is what most platforms do. Cosmetic for evaluation but
material if drawdown is used as a kill-switch.

---

## 6. Optimizer-specific bugs

Beyond §1.1, §1.2, §4.7, §5.2-5.5, §3.1-3.2:

### 6.1 `OptimizerRunner` exception handling loses cause — MEDIUM
`src/main/java/com/jbooktrader/platform/optimizer/OptimizerRunner.java:69, 75, 106, 163`

`throw new RuntimeException(e.getMessage());` — drops the cause chain.
Diagnosing optimizer failures requires the stack trace; this discards
it.

### 6.2 Off-by-one: parameter range does not always include `max` — MEDIUM
`src/main/java/com/jbooktrader/platform/optimizer/OptimizerRunner.java:251`

`for (int value = param.getMin(); value <= param.getMax(); value += step)`
only includes `max` when `(max - min) % step == 0`. min=1, max=10, step=3
silently excludes 10. The UI lets the user enter any step.

### 6.3 `DivideAndConquerOptimizerRunner` uses `Math.log(n)` not `log2(n)` — MEDIUM
`src/main/java/com/jbooktrader/platform/optimizer/DivideAndConquerOptimizerRunner.java:66`

Picks `max(1, (int) Math.log(size))` — natural log. For 1000 results
that's `ln(1000) ≈ 6.9 → 6`, not the more intuitive `log2(1000) ≈ 10` or
`log10(1000) = 3`. Possibly intentional, but undocumented.

### 6.4 Centroid / Gradient runners print to stdout — LOW
`CentroidOptimizerRunner.java:83, 131-138`,
`GradientOptimizerRunner.java:99, 147-154`

`System.out.println` debug output left in shipping code.

### 6.5 Centroid / Gradient have ~95% identical code — LOW
The two runner classes differ essentially only in `getCentroid()`.
Bug fixes need to be applied in two places (the `Math.floor`/`(int)`
NaN cast bug from §5.2-5.3 affects both identically, for example).

### 6.6 `OptimizerRunner.cancel` is non-interrupting — MEDIUM
`OptimizerRunner.java:152-167, 170-173`

`cancel()` sets a flag but workers only check it every ≥10 000
strategy-iterations. For a fast strategy, that's seconds. The
`ExecutorService` is `shutdown()` not `shutdownNow()` — already-running
tasks complete normally. Pressing Cancel on a 100-million-strategy
brute-force run can take minutes to actually stop.

### 6.7 `OptimizerRunner.execute()` creates a fresh thread pool every pass — LOW
`OptimizerRunner.java:152`

`Executors.newFixedThreadPool(...)` per pass; multi-pass optimizers
churn the pool. Cheap, but the `finally`
`optimizationExecutor.shutdown()` will NPE if the constructor threw.

### 6.8 `ComputationalTimeEstimator.updates` is incremented unsynchronized — LOW
`ComputationalTimeEstimator.java:23, 28-31`

Plain `int updates` modified from multiple worker threads via
`getTimeLeft`. Lost updates possible. Low impact (progress estimate
only).

### 6.9 `StrategyParams.get(int)` is O(n) on `LinkedList` — LOW (perf)
`StrategyParams.java:50-52`

Inside the worker hot loop. Per task, the cartesian-product builder
iterates `strategyParams.get(index)` for every parameter — O(n²) per
task. For 5 parameters × millions of tasks the constant factor is
small but noticeable in profiles.

---

## 7. Chart / performance subsystem

Beyond §1.15-1.19 and §4.3, §4.5:

### 7.1 `PerformanceChart.setTimeline` requires non-empty price list — HIGH
`PerformanceChart.java:67-71` → `MarketTimeLine.java:28`

The *Trading Hours* timeline option calls `getNormalHours()` which
does `prices.get(0)`. Empty chart → IOOBE.

### 7.2 `OptimizationMap` paint scales divide by zero — HIGH
`OptimizationMap.java:281, 293-294`

When every result has the same metric value (e.g., 0 trades across the
board for an over-constrained search), `(max - min) == 0` and the
color computation produces NaN → `new Color(NaN, NaN, NaN)` throws
`IllegalArgumentException`.

### 7.3 `ChartMonitor` cursor flicker — MEDIUM
`ChartMonitor.java:18-20`
```java
setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
super.paint(g);
setCursor(Cursor.getDefaultCursor());
```
Runs on every paint. Mouse moves over the chart → wait cursor flicker.
Also: no try/finally — if `super.paint` throws, the cursor is stuck.

### 7.4 `PerformanceChart` adds an annotation per trade — MEDIUM
`PerformanceChart.java:238-245`

Each trade fill becomes a `CircledTextAnnotation` on the price plot.
For a high-frequency strategy backtested over months (the
`QuickTestStrategy` shim produced ~22 000 trades in 5 days of data),
the EDT freezes for several seconds adding annotations and the chart
becomes sluggish to pan/zoom.

### 7.5 `DateScrollBar` reentrant adjustment — MEDIUM
`DateScrollBar.java:107-119`

`adjustmentValueChanged` mutates `dateAxis.setRange(...)` which fires
`axisChanged` which calls `setValue(...)` which can fire another
`adjustmentValueChanged`. Currently terminates because the new value
matches; under unusual cases (rounding, NaN bounds) it might not.

### 7.6 `PorfolioBackTestRunner` portfolio metrics are placeholders — MEDIUM
`PorfolioBackTestRunner.java:65`
```java
pmd.setPortfolioResults(trades, net, 0, 0, 0, 0, aveDuration);
```
`maxDrawdown`, `optimalGrowth`, `pi`, `apd` are passed as literal zeros.
The portfolio backtest UI shows real numbers for the per-strategy
rows but zero for the portfolio aggregate.

### 7.7 `PortfolioBackTester` hard-codes `BarSize.Hour1` — LOW
`PortfolioBackTester.java:50`

Unlike single-strategy backtest where the user can pick a bar size,
portfolio backtest always uses 1-hour bars.

---

## 8. Validation and input handling

### 8.1 `LineParser` does not validate `bid <= ask` — MEDIUM
`src/main/java/com/jbooktrader/platform/backtest/LineParser.java:81-91`

A line with `bid > ask` is parsed without complaint. `MarketSnapshot`
then reports a *negative spread*; `MarketDepth.takeMarketSnapshot()`
guards with `isValidDepth()` for live data but the file reader has no
analogous validation. Indicators downstream see `getPrice() = (bid +
ask) / 2` which still works numerically but `getAsk() - getBid()` is
negative.

### 8.2 `LineParser` "volume must be positive" message is wrong — LOW
`LineParser.java:86-89`
```java
if (volume < 0) {
    throw new RuntimeException("Volume must be a positive integer");
}
```
0 is non-negative but not positive; only negative is rejected. Cosmetic.

### 8.3 `LineParser` accepts non-strictly-increasing time silently for first row — LOW
`LineParser.java:75-79`

The check is `time <= previousTime`, where `previousTime` starts at 0.
A file whose first snapshot timestamp is 0 (epoch start) would be
silently accepted. The next snapshot at any time > 0 wins. Not a real
risk with valid data, but worth a unit test.

### 8.4 `PreferencesHolder.getInt` / `getDouble` throw on bad input — MEDIUM
`PreferencesHolder.java:28-36`

If a user has manually edited their prefs node and corrupted a numeric
value, `Integer.parseInt(...)` / `Double.parseDouble(...)` throws
`NumberFormatException` from many call sites with no catch — JBookTrader
fails to start with a stack trace.

### 8.5 `JBTPreferences` defaults are stringly-typed — LOW
`JBTPreferences.java:6-83`

Every default is a string; numeric conversions happen on every read.
Typos in the default string aren't caught until that pref is read.
E.g., setting `MaxLeverage("Maximum leverage", "10x")` would compile
fine and only blow up at runtime when leverage is checked.

### 8.6 `NumberFormatterFactory` may ClassCastException by locale — LOW
`NumberFormatterFactory.java:17`
```java
DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance();
```
On some locales (e.g., Arabic), `NumberFormat.getNumberInstance()`
returns a non-`DecimalFormat` (e.g., `ArabicDigitsDecimalFormat` —
which is a subclass, OK) or a different subtype entirely on some
non-Sun JDKs. Use `NumberFormat.getNumberInstance(Locale.US)` for
predictability.

### 8.7 `TradingSchedule` `year=2008` hardcoded — LOW
`src/main/java/com/jbooktrader/platform/schedule/TradingSchedule.java:122`

`calendar.set(Calendar.YEAR, 2008)` — the schedule's reference epoch
is set to 2008 with the comment "has to be before the first timestamp
in the data file". Backtesting data from before 2008 is not supported
even though there's no other reason it wouldn't be.

### 8.8 `StrategyES` and friends hard-code multiplier and commission — LOW
`StrategyES.java:22-27`

Contract multiplier `50` and `getBundledNorthAmericaFutureCommission()`
are hard-coded into the base class. CME ES E-mini multiplier is indeed
50 today (CME Micro E-mini is 5), but the value is baked into every
strategy that extends `StrategyES` and is not driven by IB's contract
multiplier on live data. If CME ever revalues, every commit is needed.

---

## 9. Time, timezone, and schedule issues

Beyond §1.7-1.10:

### 9.1 `DaySchedule` hard-codes America/New_York — MEDIUM
`DaySchedule.java:12, 38-44`

The trading-period heuristics (`hour >= 7 && hour < 16`) and the
day-of-week check all evaluate in NY time, regardless of the
strategy's `TradingSchedule.timeZone`. A strategy trading
Asia/Singapore against a TWS feed in Asia/Singapore time would have
its "is end of day" / "is reset time" checks misfire by 13 hours.

### 9.2 `DaySchedule.isResetTime` triggers only at hour == 7 — LOW
`DaySchedule.java:46-59`

If the snapshot stream happens to skip hour 7 entirely (gap in feed,
or strategy started after 7), the reset never fires for that day.
The 12-hour guard is "elapsed since last reset" which makes the next
hour-7 trigger correctly, so worst case is "one day with no reset" —
unlikely to break anything but worth a test.

### 9.3 `TimeFilter` mathematical confusion — LOW
`TimeFilter.java:10-11`
```java
private final static long secondsInMinute = 60;
private final static long secondsInHour = secondsInMinute * secondsInMinute;
```
`secondsInMinute * secondsInMinute = 60 * 60 = 3600 = secondsInHour`,
by happy mathematical coincidence (60 minutes per hour × 60 seconds per
minute). The expression should be `minutesInHour * secondsInMinute` to
be self-documenting and survive future maintenance.

### 9.4 `MarketBook` lock detection uses fixed 15 minutes — LOW
`MarketBook.java:11, 31-58`

A 15-minute price-flat threshold may be appropriate for liquid futures
during regular trading hours but is unreasonable for less-liquid
instruments or overnight sessions where 15 minutes of flat is normal.

---

## 10. Code quality and dead code

These are low-severity items, listed for completeness:

- **Typo**: `OptimizerRunner.java:194` — `otpimizerReportHeaders`
- **Typo**: `CentroidOptimizerRunner.java:89, GradientOptimizerRunner.java:105` —
  "neightborhhoid"
- **Dead code**: `DivideAndConquerOptimizerRunner.java:31-34` — `maxRange`
  computed but never used
- **Dead code**: `MarketDepthModel.java:35` — `if (item != null)` is
  unreachable
- **Dead `System.out.println` statements**: throughout the optimizer
  runners
- **Inconsistent access**: `Bar.setClose` is public but `setHigh`/`setLow`
  are package-private
- **Raw type**: `OptimizationMap.java:46` — `JComboBox` without generic
- **Constant naming**: `CircledTextAnnotation.java:23` — `static final
  int radius` should be `RADIUS`
- **Misnomer**: `CircledTextAnnotation` doesn't draw any text
- **Logging**: `RuntimeException` thrown with only `e.getMessage()` loses
  cause chain in many places (`OptimizerRunner.java:69/75/106/163,
  StrategyLoader.java:111-113, etc.`)
- **Duplicate code**: Centroid/Gradient optimizer runners are ~95%
  identical
- **Magic numbers**: `MarketDepthModel`'s `10` (maxDepth), bar-size
  thresholds, etc.
- **Confusing condition**: `BackTestFileReader.java:46`
  `key.equals(cacheKey) && !snapshots.isEmpty()` — when `cacheKey` is
  null the short-circuit saves us, but it's not obvious from the code
- **API mismatch**: `MarketBook.isLocked()` is the only mutating method
  that *doesn't* take a parameter — its sole side effect is hidden
  inside `setSnapshot`
- **README still references**: `code.google.com/p/jbooktrader/wiki/ReleaseNotes`
  in `MainFrameController.java:273` — Google Code shut down in 2016

---

## Severity counts

|              | Count |
|--------------|-------|
| **HIGH**     | ~30   |
| **MEDIUM**   | ~40   |
| **LOW**      | ~25   |

The largest concentrations are in the chart/performance subsystem
(thread safety, NaN propagation) and the optimizer (memory, sort
direction, NaN propagation, multi-pass state leak). The single highest
operational risk is the **2021–2025 hole in `HolidaySchedule`** — easy
to fix, with real consequences if not.
