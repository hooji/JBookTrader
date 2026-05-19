# JBookTrader Testing

This document inventories what unit tests JBookTrader currently has,
proposes new tests organized by subsystem, and ties each proposed test
to a specific concern (often a finding in [CODE_REVIEW.md](CODE_REVIEW.md)).

Philosophy: **paranoid coverage**. Tests are cheap to run, bugs in
trading code are expensive to find in production. The bar for "is this
worth a test?" is low. If a function has a boundary condition, a
divide-by-zero risk, a thread, a clock, or a file — test it.

## Contents
- [1. Existing tests](#1-existing-tests)
- [2. Test infrastructure observations](#2-test-infrastructure-observations)
- [3. Proposed new tests](#3-proposed-new-tests)
  - [3.1 Backtest file parsing](#31-backtest-file-parsing)
  - [3.2 Market depth and balance](#32-market-depth-and-balance)
  - [3.3 Indicators](#33-indicators)
  - [3.4 Position / Trade / PerformanceManager](#34-position--trade--performancemanager)
  - [3.5 Optimizer](#35-optimizer)
  - [3.6 Chart / PerformanceChartData](#36-chart--performancechartdata)
  - [3.7 Schedule and clock](#37-schedule-and-clock)
  - [3.8 Order management](#38-order-management)
  - [3.9 Snapshot writer](#39-snapshot-writer)
  - [3.10 Preferences](#310-preferences)
  - [3.11 Web monitoring server](#311-web-monitoring-server)
  - [3.12 Strategy loader](#312-strategy-loader)
  - [3.13 Notifier](#313-notifier)
  - [3.14 NTP clock](#314-ntp-clock)
- [4. Integration and end-to-end tests](#4-integration-and-end-to-end-tests)
- [5. Property-based and fuzz tests](#5-property-based-and-fuzz-tests)
- [6. Performance / load tests](#6-performance--load-tests)

---

## 1. Existing tests

Three test classes under
`src/test/java/com/jbooktrader/platform/test/`, 28 tests total, all
passing.

| File                            | Tests | Subject                                                        |
|---------------------------------|-------|-----------------------------------------------------------------|
| `GeometricUtilityTest.java`     | 23    | Kelly / Youden / PowerEvaluatorHalfKelly leverage calculations  |
| `PaperTest.java`                | 1     | Smoke test of `PrudenceEvaluator` (mostly commented out)        |
| `PerformanceIndexTest.java`     | 4     | A reference implementation of optimal-leverage / max-PI search  |

Coverage summary:

```
JUnit 4, no test discovery framework beyond Surefire's defaults.
```

| Subsystem                          | Has tests? |
|------------------------------------|------------|
| Kelly / Youden / Half-Kelly leverage | ✔        |
| Performance-index calculation      | ✔ (partial) |
| Everything else                    | ✘          |

`mvn test` produces:

```
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
```

`GeometricUtilityTest.evaluate(...)` writes diagnostic
`System.out.println` output during the run; the tests assert on
`PowerEvaluatorHalfKelly` only, not on Kelly/Youden/Youden2 (those are
computed and printed but not asserted). That's not coverage of the
intermediate evaluators — just exercise of them.

## 2. Test infrastructure observations

Before adding more tests:

- **No assertion library beyond JUnit's `Assert.assertEquals(double,
  double, double)`.** Adding AssertJ or Hamcrest would make
  expectations more legible (especially around `assertThat(x).isNaN()`,
  `isPositiveInfinity()`, etc.).
- **No mocking framework.** Most JBookTrader classes pull dependencies
  out of `Dispatcher` directly (e.g. `Dispatcher.getInstance().getEventReport()`),
  which makes unit testing in isolation painful. Add Mockito (or
  refactor `Dispatcher` to be injectable).
- **No test-only fixtures directory.** A small `src/test/resources/`
  with hand-crafted tiny data files (1 minute of well-formed snapshots,
  an over-the-line corrupt file, etc.) would be reusable across many
  tests.
- **JUnit 4 is in use** (`@Test` from `org.junit`). JUnit 5 is widely
  available and supports parameterized tests, dynamic tests, and time
  assertions out of the box — many of the proposed tests below would
  benefit. Migrating is straightforward; both can coexist via
  `junit-vintage-engine`.
- **`PaperTest.java`** contains only a commented-out `System.out.println`
  inside a test method that creates an evaluator and discards the
  result. It asserts nothing. Delete or finish.
- **Naming**: existing tests live in `com.jbooktrader.platform.test` —
  unusual. Convention is to mirror the package under test
  (`com.jbooktrader.platform.performance`, etc.). Worth a one-time
  reorganisation.

## 3. Proposed new tests

Each subsection lists tests with a short rationale. Where applicable,
the bug from [CODE_REVIEW.md](CODE_REVIEW.md) is cited. "(CR §X.Y)" =
catches the issue from that section of the code review.

### 3.1 Backtest file parsing

`LineParser` and `BackTestFileReader` parse the most security- and
correctness-sensitive input the system has: market data. Comprehensive
coverage:

#### `LineParserTest`
- Parses a well-formed snapshot line with all six columns.
- Returns null for comment lines starting with `#`.
- Returns null for blank lines.
- Returns null for `timeZone=America/New_York` and sets the formatter.
- Throws on a snapshot line before any `timeZone=` is seen.
- Throws on a line with 5 columns.
- Throws on a line with 7 columns.
- Throws on negative volume. (CR §8.2)
- Accepts zero volume.
- Throws on non-numeric volume.
- Throws on non-numeric bid/ask/balance.
- Throws when current snapshot's timestamp is before the previous. (CR §5.12, §8.3)
- Throws when current snapshot's timestamp equals the previous.
- Boundary: snapshot at second 59 followed by second 0 of next minute parses correctly.
- Boundary: snapshot at hour 23:59:59 followed by 00:00:00 next day parses correctly.
- Boundary: snapshot crossing daylight-savings transition parses correctly.
- Same-minute fast-path: a sequence within the same minute uses the
  cached parse and produces the same timestamp as a parse-from-scratch.
- **MISSING VALIDATION**: bid > ask should be flagged (CR §8.1) — write
  the failing test now, then either fix the code or relax the test
  with a documented justification.
- Returns the snapshot when no filter is set.
- Returns null when filter excludes the timestamp.
- Returns the snapshot when filter includes the timestamp.

#### `BackTestFileReaderTest`
- Reads a tiny fixture file of N lines and returns N snapshots.
- Caches the result: second load with the same file/filter returns the
  same instance.
- Cache invalidates when the file size changes.
- **MISSING**: cache invalidates when the file's contents change but
  size stays the same (CR §1.3 — modify a value but keep file size identical).
- Cache invalidates when the filter changes.
- Two simultaneous loads from different threads do not produce
  corrupted state. (CR §1.3 — thread safety)
- Cache holds onto the snapshot list (memory leak check). (CR §4.2)
- Throws `RuntimeException` if the file does not exist.
- Throws on a malformed line, including the offending line text.
- Progress listener invoked at expected intervals.
- Mixed line-ending file (LF and CRLF) parses correctly. (CR §4.5 size-counting note)
- UTF-8 BOM at file start does not break parsing.

### 3.2 Market depth and balance

#### `MarketDepthModelTest`
- `insert(0, ...)` on empty model adds one item.
- `insert(0, ...)` 11 times caps at 10. (test the cap)
- `insert(position, ...)` with position == size appends.
- `insert(position, ...)` with position > size throws `IndexOutOfBoundsException`.
- `delete(0)` on empty model is a no-op (current behavior).
- `delete(position)` with position >= size is a no-op.
- `update(0, ...)` on empty model throws `NoSuchElementException`. (CR §5.10)
- `update(position, ...)` with position >= size throws `NoSuchElementException`.
- `getBestPrice()` on empty model throws `NoSuchElementException`. (CR §5.9)
- `hasValidBidStructure` returns true for descending prices with positive sizes.
- `hasValidBidStructure` returns false if two adjacent prices are equal.
- `hasValidBidStructure` returns false if any size is zero or negative.
- `hasValidBidStructure` returns false on empty model (currently returns true — bug?).
- `hasValidAskStructure` mirror tests.
- `getCumulativeSize` returns sum.
- `getCumulativeSize` returns 0 on empty model.
- `reset()` clears items.

#### `BalanceAggregatorTest`
- `aggregate` with cumulativeBid == cumulativeAsk → balance 0.
- `aggregate` with all-bid (cumulativeAsk == 0) → balance 100.
- `aggregate` with all-ask (cumulativeBid == 0) → balance −100.
- `aggregate` with cumulativeBid + cumulativeAsk == 0 → divide by zero (NaN).
- `clear()` resets samples and sum.
- `isEmpty()` true after construction; false after one `aggregate`.
- `getBalance` divides by `samples`: divide by zero if never aggregated.
- Repeated aggregation produces correct mean.

#### `MarketBookTest`
- `setSnapshot` updates current snapshot.
- `isLocked` returns true after 15 minutes of mid-price stability.
- `isLocked` returns false the moment mid-price changes.
- `isGapping` returns true when next snapshot is > 1 hour ahead.
- `isGapping` returns false at 59 minutes 59 seconds.
- `isEmpty` true before any setSnapshot, false after.
- Locked → unlocked transition emits one event report. (CR §1.16 — thread safety)

### 3.3 Indicators

#### `IndicatorManagerTest`
- `addIndicator` dedupes by `getKey()`.
- `addIndicator` returns the existing instance when the key matches.
- `updateIndicators` returns false until `MIN_SAMPLE_SIZE` is reached.
- `updateIndicators` returns true at exactly `MIN_SAMPLE_SIZE`.
- `updateIndicators` resets on first call (previousSnapshotTime == 0
  produces a "gap"). Document or fix. (CR — note in §IndicatorManager)
- `updateIndicators` resets when gap > 5 minutes.
- `updateIndicators` does NOT reset for 4:59 gap.
- `updateIndicators` resets when `marketBook.isLocked()`.
- `updateIndicators` calls `calculate()` on every registered indicator.
- `resetIndicators` resets `samples` to 0.
- `resetIndicators` no-ops when marketBook is empty.

#### `BalanceEMATest`
- Length=10 with constant balance → EMA converges to that balance.
- Length=10 with step input → EMA tracks with expected lag.
- Length=1 produces multiplier 1 (EMA == latest balance).
- **Length=0 produces multiplier 2.0 → diverges.** (CR §5.8) — write test
  to fail loudly.
- **Length=−1 produces NaN.** (CR §5.8)
- `reset()` returns to 0.
- After `reset()`, first sample biases toward 0.

#### `BalanceVelocity` / `BalanceAcceleration` / `BalanceSigma` / `LogPriceVelocity`
Each indicator should have:
- Constant input → expected value (typically 0 for velocity/acceleration).
- Step input → known transient.
- After `reset()`, behavior matches construction state.
- Sample sequence reproducing a known hand-computed value.
- Length parameter validation (length ≤ 0 behavior).
- Numerical stability: extreme bid/ask/balance values do not produce NaN/Inf.

#### `TensorEqualizerTest`
- Constant balance, constant price → tension and sigmaTension both 0.
- Balance velocity > 0, price velocity = 0 → positive tension.
- Balance velocity = 0, price velocity > 0 → negative tension.
- Sigma-tension is the volatility-normalized tension (variance > 0).
- Variance = 0 → sigmaTension is NaN (or 0 by convention).
- `reset` zeros internal sums.
- Length parameter boundary cases.

### 3.4 Position / Trade / PerformanceManager

#### `TradeTest`
- `updateTotalBought` accumulates quantity and total.
- `updateTotalSold` accumulates quantity and total.
- `getAverageBoughtPrice` with no buys → NaN. (CR §5.1)
- `getAverageSoldPrice` with no sells → NaN. (CR §5.1)
- `getAverageBoughtPrice` with one fill returns that fill's price.
- `getAverageBoughtPrice` with multiple fills returns weighted average.
- `getSlippageAmount` reflects the *last* slippage value, not cumulative.
  (CR §1.13) — assert the current behavior, document it as a bug.
- Multiple partial fills update slippage incorrectly: write a test that
  documents the bug.
- `getTimeInMarket` returns `exit - entry`.
- `getTimeInMarket` for a trade with no exit time returns
  `−entryTime` (huge negative). Document/fix. (CR — note in §Trade)

#### `PositionManagerTest`
- `setTargetPosition` no-ops if target == current target.
- `setTargetPosition` no-ops on holidays. (CR §1.8 — early close handling)
- `setTargetPosition` no-ops within `minimumRemainingTimeMinutes` of trading-day end
  when *exposure increasing*.
- `setTargetPosition` allows reducing exposure within 15 minutes of close.
- `update` with side BOT increments position.
- `update` with side SLD decrements position.
- `update` updates avgFillPrice.
- `update` computes slippage correctly (BOT and SLD branches).
- `update` records position history in BackTest mode.
- `update` does NOT record history in Trade mode.
- `update` calls `performanceManager.updateOnTrade` with the signed quantity.

#### `PerformanceManagerTest`
- New manager has 0 trades, 0 net profit, 0 max drawdown.
- After one round-trip trade (long open + flat close), trades == 1.
- `updateMetrics` with non-zero position adjusts net profit by price * mult * pos.
- `updateOnTrade` from previousPosition==0 to position!=0 creates a Trade.
- **`updateOnTrade` from previousPosition==0 to position==0 NPEs** (CR §1.14)
  — assert it currently does, then fix.
- `updateOnTrade` from non-zero to zero sets exitTime.
- `peakNetProfit` updates monotonically; `maxDrawdown` is peak − netProfit.
- **First trade is a loss → drawdown reported as |loss| with peak = 0.**
  (CR §5.13) — document as known behavior.
- `tradeReturn` calculation: `tradeProfit / (avgFillPrice * multiplier)`.
- `tradeReturn` with `avgFillPrice == 0` → NaN, captured in `tradeReturns`.
- `updateAtEnd` calls `PerformanceEvaluator.evaluate` and stores
  `optimalLeverage` / `pi` / `optimalGrowth`.
- `updateAtEnd` is no-op on empty trade-returns list.
- `getAPD` returns 0 when `cumulativeIntraTradeDD == 0`.
- `getPercentProfitableTrades` returns 0 with 0 trades.
- `getAveDuration` returns 0 with 0 trades.

#### `CommissionTest`
- Per-contract rate applied correctly.
- Minimum commission applied when computed < minimum.
- Max-percent cap applied when configured and below per-contract rate.
- Zero contracts → minimum commission (current behavior).
- Negative contracts → undefined; assert and either fix or document.

### 3.5 Optimizer

#### `StrategyParamTest`
- Construction sets all fields.
- Copy constructor copies all fields.
- **`setStep(0)` allowed → use this in a sketch test to drive `getTasks`
  into an infinite loop.** Replace with validation. (CR §5.5)
- `setMin(value)` where value > max → range becomes negative; assert.
- `setMax(value)` where value < min → assert.
- `getMiddle()` returns (min + max) / 2.
- **`getMiddle()` with min = Integer.MAX_VALUE, max = 1 → overflow.** (CR §5.5)
- `getRange()` returns max - min.
- `getRange()` with min > max → negative.

#### `StrategyParamsTest`
- `getKey` produces "/"-separated values.
- `getKey` for empty params is empty string.
- `add` appends.
- `get(int)` returns the parameter at that index.
- `get(String name)` returns the parameter by name.
- `get(String name)` throws when not found.
- Copy constructor deep-copies each `StrategyParam`.

#### `ResultComparatorTest`
- For `NetProfit`, sorts descending (correct).
- For `OG`, sorts descending (correct).
- **For `MaxDD`, sorts descending — surfaces the WORST drawdowns at the
  top.** (CR §1.1) — assert current behavior, then fix and re-assert.
- **For `MaxSL`, same.**
- Handles NaN deterministically (Double.compare semantics).
- Handles Infinity deterministically.
- Two equal metric values compare as 0.

#### `OptimizerRunnerTest`
- `getTasks` produces the full cartesian product of parameter ranges.
- `getTasks` excludes duplicates via the `uniqueParams` set.
- `getTasks` produces tasks where each value is `min + k*step`, k=0..n.
- **`getTasks` does NOT include `max` when `(max - min) % step != 0`.**
  (CR §6.2) — write test to fail, decide whether to fix the loop to
  always include max.
- `setTotalSteps` correctly computes snapshot * strategy product.
- Cancellation flag is observed by workers within reasonable latency.
- Two passes accumulate results (current behavior). (CR §1.2) — assert
  and fix.

#### `BruteForceOptimizerRunnerTest`
- Single pass: result count matches expected number of unique parameter sets.
- Cancellation mid-pass returns a partial result.
- Optimizing with zero trades produced → empty results, no crash.

#### `DivideAndConquerOptimizerRunnerTest`
- **Configured with `DivideAndConquerCoverage == 1` → divide by zero.**
  (CR §5.4)
- Multi-pass converges (range shrinks each pass).
- Boundary clamping: param near min/max doesn't push search outside
  the originally-declared range.
- Pass count terminates eventually (no infinite loop on degenerate input).

#### `CentroidOptimizerRunnerTest`
- **`sumOfPerformance == 0` (no positive results) → NaN centroid.** (CR §5.2)
- **`optimizationResults.size() * 0.382 < 1` → cutoff == 0 → all results
  skipped → NaN.** (CR §5.2)
- Centroid matches a hand-computed weighted average for a small case.
- **Configured with `partsPerDimension == 1` → divide by zero.** (CR §5.4)
- Range shrinks by golden ratio each pass.
- Terminates when `maxRange <= 1`.

#### `GradientOptimizerRunnerTest`
- **`max == min` (all results have same metric) → divide by zero in
  normalization.** (CR §5.3)
- **Negative-clamped `min` produces inconsistent normalization.** (CR §5.3)
- Centroid follows the gradient on a small synthetic example.

#### `OptimizerWorkerTest`
- Worker with one task produces one result (if minTrades and inclusion
  pass).
- Worker with `minTrades` filter > actual trades → empty results.
- Worker with `inclusionCriteria == "Profitable strategies"` filters
  out negative-net strategies.
- Worker with `inclusionCriteria == "All strategies"` includes all.
- Worker shares one `MarketBook` across strategies — assert that
  state from strategy A's `isLocked` flag doesn't bleed into strategy
  B's indicators. (CR §3.4)
- Cancellation mid-worker returns partial result.
- Exception thrown from strategy.onBookSnapshot is propagated.

#### `ComputationalTimeEstimatorTest`
- `getTimeLeft(0)` returns "more than 0" or similar; should not NaN.
- `getTimeLeft(completed)` returns 0 at completion.
- `getTimeLeft` between bursty progress updates does not jitter wildly.
- **Concurrent calls from multiple threads do not crash
  `SimpleDateFormat`.** (CR §3.1) — repeatable failure under load.

### 3.6 Chart / PerformanceChartData

#### `BarTest`
- New `Bar(time, value)` has all OHLC == value.
- `setHigh` / `setLow` / `setClose` update fields.
- `setHigh(NaN)` accepted; assert and document. (CR §10)
- `getTime` is immutable.

#### `BarSizeTest`
- `getBarSize("1 minute")` returns `BarSize.Minute1`.
- `getBarSize("bogus")` returns null. (CR §10) — wrap with `Optional` or document.
- `getSize` for `Hour2` returns 7_200_000.
- `values()` order matches declaration.

#### `PerformanceChartDataTest`
- New instance is empty (`isEmpty()` true).
- `updateStrategyPnL` adds a Bar at first call.
- A second update within the same bar window updates high/low/close.
- A second update in a new bar window flushes the previous bar and
  starts a fresh one.
- **A second update in a strictly *earlier* bar window silently
  overwrites the current bar.** (CR §1.15)
- Same tests for `updatePortfolioPnL`, indicator bars, price bars.
- Boundary: a timestamp exactly equal to `N * frequency` belongs to bar
  N. (CR §7 — half-open boundary discussion)
- Boundary: timestamp `N * frequency + 1` belongs to bar `N + 1`.
- `getStrategyNetProfitDataset` produces a deterministic ordered array.
- Concurrent `updateStrategyPnL` from one thread and
  `getStrategyNetProfitDataset` from another → no
  `ConcurrentModificationException`. (CR §1.16, §3) — fails under load.
- Long backtest (100k+ snapshots) doesn't OOM under -Xmx256m. (CR §4.3)

#### `MarketTimeLineTest`
- Empty price list → `getNormalHours` throws
  `IndexOutOfBoundsException`. (CR §1.17) — assert and then fix.
- Single-day data → one continuous segment.
- Two-day data with 18-hour gap → two segments with the gap excluded.
- Gap of exactly 12 hours: edge of the exclusion logic (CR §7.5).

#### `DateScrollBarTest`
- **`rangeUpdate()` on a dataset with all-negative values returns Y-axis
  max ≈ 0.** (CR §1.18) — write the failing test, then fix `max =
  Double.NEGATIVE_INFINITY`.
- `rangeUpdate()` on empty dataset doesn't NPE.
- `adjustmentValueChanged` updates the date axis range correctly.

#### `OptimizationMapTest`
- **Empty results list crashes the constructor.** (CR §1.19)
- **Strategy with one parameter → `setSelectedIndex(1)` crashes.** (CR §1.19)
- **All results have the same metric value → divide by zero in paint scale.** (CR §1.19, §7.2)
- `createTopResult` returns the params of the highest-ranked result.
- Color combo change re-renders without crash.

### 3.7 Schedule and clock

#### `TradingScheduleTest`
- `contains(time)` returns true at start time.
- `contains(time)` returns false one ms before start time.
- `contains(time)` returns true one ms before end time.
- `contains(time)` returns false at end time.
- `getRemainingTime` returns positive while in schedule.
- `getRemainingTime` returns negative outside schedule (current behavior).
- Schedule crossing midnight throws (currently rejected by constructor).
- Invalid timezone throws.
- Invalid time format ("25:00") throws.
- Schedule advances across days correctly.
- Schedule advances across DST transitions correctly.
- Schedule for an exchange in a non-NY timezone evaluates in the
  correct zone.

#### `HolidayScheduleTest`
- Known Christmas (12/25/2026) is a holiday.
- Known Early Close (e.g. 12/24/2026) is flagged. (CR §1.8) — split
  early-close from full-close.
- **A date in 2022 (gap) returns NOT a holiday.** (CR §1.7) — write
  the failing test, then add the missing years.
- Saturday/Sunday are not in the map but are still non-trading days
  (caller must check day-of-week separately).
- Date format parsing is timezone-stable.

#### `DayScheduleTest`
- `isTradingDay()` Saturday → false.
- `isTradingDay()` Sunday → false.
- `isTradingDay()` Christmas → false (when in map).
- `isTradingDay()` regular Tuesday → true.
- `isEndOfTradingDay()` at 15:59 → false.
- `isEndOfTradingDay()` at 16:00 → true.
- `isTradingPeriod()` at 06:59 → false.
- `isTradingPeriod()` at 07:00 → true.
- `isTradingPeriod()` at 15:59 → true.
- `isTradingPeriod()` at 16:00 → false.
- `isResetTime()` triggers exactly once per day at the first 07:xx call.
- `isResetTime()` does not retrigger if called multiple times at hour 7.
- `isResetTime()` re-triggers next day.
- **`isResetTime()` does NOT trigger if hour 7 is skipped** (CR §9.2).

#### `ExitSchedulerTest`
- Configured "17:00" with current time 09:00 schedules for **today's
  17:00**, not tomorrow's. (CR §1.9) — write the failing test, then fix.
- Configured "17:00" with current time 18:00 schedules for tomorrow's 17:00.
- Misformatted prefs ("17") throws a clear error.
- The scheduled task is cancellable (test the leak — currently
  `scheduler.shutdown()` is called immediately).

### 3.8 Order management

#### `OrderKeeperTest`
- `add` then `getOpenOrder` returns the same order.
- `hasOpenOrders` true after add, false after remove.
- Concurrent add/remove/get from many threads — no exception (using
  `Concurrent` test patterns).

#### `OrderIdFactoryTest`
- `acquireNextOrderID` blocks until `setNextOrderID` is called.
- `acquireNextOrderID` returns false after 5 seconds with no setter.
- `incrementOrderID` advances by one.
- **Concurrent `incrementOrderID` from many threads produces lost
  increments.** (CR §1.5) — write the failing test, then change to
  `AtomicInteger`.
- **`setNextOrderID` followed by `getNextOrderID` from another thread
  may see stale value.** (CR §1.5) — fails under sustained load.

#### `OrderManagerAssistantTest`
- `addStrategy` rejects a duplicate strategy with a clear exception.
- `clearAllStrategies` empties both `strategies` and `marketBooks`.
- `trade` with `delta == 0` no-ops.
- `trade` in BackTest mode produces a simulated `OrderExecution` at the
  expected price.
- `trade` in Trade mode submits a real order via the (mock) `OrderHandler`.
- `trade` in ForwardTest mode produces a simulated fill.
- `trade` honors `PortfolioManager.isWithinMaxLeverage`.
- `forceClose` only acts in Trade/ForwardTest modes.

### 3.9 Snapshot writer

#### `SnapshotWriterTest`
- A new file gets the header written.
- An existing file gets appended without re-writing the header.
- `write(MarketSnapshot)` produces one line in the expected CSV format.
- Snapshot at time T renders as `MMddyy,HHmmss` in the configured timezone.
- **Concurrent `write` calls from two threads do not produce mangled
  output.** (CR §3.1) — `SimpleDateFormat`/`DecimalFormat` not thread-safe.

#### `SnapshotWriterManagerTest`
- `saveSnapshot` creates a new writer per ticker (and only one).
- `saveSnapshot` respects `TimeFilter` (07:00–16:00 by default).
- Two saves to the same ticker append to the same file.

#### `TimeFilterTest`
- `isRecordable` true within hours.
- `isRecordable` false outside hours.
- Boundary: exactly 07:00:00 → true.
- Boundary: exactly 16:00:00 → true (current behavior — top of toHour is inclusive). (CR §9.3)
- Boundary: 16:00:01 → false.
- **Concurrent `isRecordable` calls don't corrupt the shared `Calendar`.** (CR §3.1)

### 3.10 Preferences

#### `JBTPreferencesTest`
- Each enum value has a non-null name.
- Each enum value has a non-null default (use `""` if intentionally blank).
- All numeric defaults parse cleanly as their intended type.
  (Run `Integer.parseInt(pref.getDefault())` on every int pref.) (CR §8.5)

#### `PreferencesHolderTest`
- `getInt` returns the default when no value set.
- `setInt` round-trips through `getInt`.
- `getInt` on a corrupted value throws `NumberFormatException`. (CR §8.4)
- `getDouble` similar.

### 3.11 Web monitoring server

#### `WebAuthenticatorTest`
- `checkCredentials("admin", "admin")` matches default.
- `checkCredentials("admin", "x")` returns false.
- **`checkCredentials("admin/x", "y")` matches default
  user="admin", password="x/y" due to separator collision.** (CR §1.21)
- Empty username or password rejected.

#### `WebHandlerTest`
- GET `/` returns 200 and HTML with the strategy table.
- GET `/EventReport.htm` returns the file contents.
- **GET `/../../../etc/passwd.htm` returns the file contents** —
  path traversal. (CR §1.20) — write the failing test, then fix.
- GET `/missing.htm` returns 500/404 (currently throws unhandled).
- A file larger than 2 GB triggers integer overflow in `(int) length()`.
  (CR §4.4) — skip if not practical to run, but document.

### 3.12 Strategy loader

#### `StrategyLoaderTest`
- A class in `com.jbooktrader.strategy` extending `Strategy` is loaded.
- A class in `com.jbooktrader.strategy.base` (abstract base) is NOT loaded.
- A class anywhere else extending `Strategy` is NOT loaded.
- **A class named `MyTestStrategy` is NOT loaded (because path contains
  "Test").** (CR §1.11) — write failing test, then narrow the
  exclusion to actual test classes.
- A class without a `(StrategyParams)` constructor surfaces a clear error.
- A class whose `setParams` throws surfaces the error from
  `Class.forName` (currently masked).

### 3.13 Notifier

Heavily mock-dependent; integrate with a fake SMTP (Wiser, GreenMail).

#### `NotifierTest`
- Disabled `Notification` pref → `submit(...)` is a no-op.
- Enabled → `submit("hi")` results in a sent message (against fake SMTP).
- Message HTML is preserved.
- Two `submit`s send two messages in order.
- **An SMTP exception terminates the worker thread silently.** (CR §3.5)
- **A real message with text "quit" shuts down the notifier.** (CR §3.6)
- `shutdown` after some submits drains the queue (current behavior?).

### 3.14 NTP clock

#### `NTPClockTest`
- Construction with `<3` resolvable servers throws (current behavior).
- A single failing server (`getTime` IOException) is skipped.
- `getTime()` returns wall-clock + offset.
- After construction, `offset` is non-zero (or close to it) given a
  working fake NTP server.
- `shutDown` stops the scheduler.

Note: these tests need a mock NTP layer (subclass / inject). Currently
`NTPClock` is hard-bound to `NTPUDPClient`, which makes isolation
painful. (CR §4.6)

## 4. Integration and end-to-end tests

Above the unit-test level:

- **End-to-end backtest** of the bundled `marketData/ES.txt` with each
  bundled strategy. Assert specific numbers (Trades count, NetProfit,
  MaxDD) — provides a regression baseline. Currently tests stop at the
  performance evaluators in isolation.
- **End-to-end portfolio backtest** with two strategies. Same baseline
  approach.
- **Optimizer smoke test**: brute force with a tiny range and a tiny
  data file. Assert it produces > 0 results and the top result's
  parameters match a hand-computed expectation.
- **Mode transitions**: setMode through each Mode value, assert side
  effects (eventReport enabled/disabled, monitoring server started,
  exit scheduler started, etc.) using mocks.
- **Holiday short-circuit**: live trading on a holiday → no orders are
  submitted.

## 5. Property-based and fuzz tests

JBookTrader has a lot of parsing and a lot of numeric pipelines.
Property-based testing (jqwik, junit-quickcheck) is a great fit:

- **`LineParser` fuzz**: generate arbitrary text and assert
  `process(line)` either returns a valid `MarketSnapshot` or throws a
  `RuntimeException` — never returns garbage.
- **`StrategyParam` range fuzz**: generate (min, max, step), assert the
  cartesian-product loop terminates (catches step=0).
- **Indicator inputs**: generate sequences of (bid, ask, balance) and
  assert no indicator ever produces NaN or Infinity for finite finite
  input.
- **`Commission.getCommission` fuzz**: generate (contracts, price), assert
  result is non-negative and >= minimum.
- **`Trade.updateTotalBought` / `updateTotalSold` fuzz**: random
  sequence of partial fills, assert `getAverageBoughtPrice` matches a
  reference implementation.

## 6. Performance / load tests

These don't need to live in `mvn test`; a separate `mvn verify -P perf`
profile or a small `bench/` directory using JMH would work.

- **`BackTester.execute` on a 10 MB file**: assert wall-clock < 5s on a
  reference machine. Catches regression from a future "process each
  snapshot through SimpleDateFormat" mistake.
- **`OptimizerWorker.call` on N=1000 strategies × 1M snapshots**: assert
  wall-clock and memory bounds. Catches the unbounded memory growth
  in `PerformanceChartData`. (CR §4.3)
- **`PerformanceChartData` memory**: feed 1M snapshots, assert
  `Runtime.getRuntime().totalMemory()` < 256 MB.
- **`Notifier` throughput**: 10 000 submit/sec for 1 minute against
  fake SMTP — assert no message lost, no thread death.
- **`HttpServer` web monitor under 100 concurrent requests** — assert
  no race producing wrong HTML for a strategy's status.

---

## Priority order

If you can only add a handful, the highest-value tests in this list are
the ones that catch HIGH-severity items from the code review:

1. `ResultComparatorTest` — surfaces §1.1 (sort direction wrong for
   MaxDD/MaxSL).
2. `HolidayScheduleTest` covering 2021-2025 — surfaces §1.7.
3. `OrderIdFactoryTest` concurrency — surfaces §1.5.
4. `WebHandlerTest` path traversal — surfaces §1.20.
5. `PerformanceManagerTest` first-trade-zero-position NPE — surfaces §1.14.
6. `PerformanceChartDataTest` out-of-order bar handling — surfaces §1.15.
7. `DateScrollBarTest` all-negative-values — surfaces §1.18.
8. `OptimizationMapTest` empty-result / single-param / flat-metric —
   surfaces §1.19.
9. `CentroidOptimizerRunnerTest` zero-sum centroid — surfaces §5.2.
10. `GradientOptimizerRunnerTest` flat-metric divide-by-zero — surfaces §5.3.

These ten alone would cover the ten most-impactful issues from the
code review.
