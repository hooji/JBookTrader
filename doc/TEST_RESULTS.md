# JBookTrader Test Results

Status of the test suite under `src/test/` against the JBookTrader
`2026.1-SNAPSHOT` source tree.

> Companion documents: [CODE_REVIEW.md](CODE_REVIEW.md) for the bug
> descriptions, [TESTING.md](TESTING.md) for the proposed-tests catalog.

## TL;DR

```
mvn test
...
Tests run: 281, Failures: 37, Errors: 0, Skipped: 0
```

- **244 passing tests** — positive regression coverage of code that
  works correctly today.
- **37 failing tests** — every one of them is a bug-demonstration test
  that asserts the *correct* behavior. Each one will flip from red to
  green when the corresponding bug is fixed.

If you run the suite and see any of these 37 tests pass without a fix
having gone in, that's a flag — investigate, because the bug analysis
may be wrong. (See [§3](#3-what-to-do-if-a-failing-test-passes-unexpectedly).)

## 1. Failing tests, mapped to bugs

The table below lists every failing test in the `2026.1-SNAPSHOT` tree
together with the corresponding section of [CODE_REVIEW.md](CODE_REVIEW.md)
and a one-line explanation. Severity is from the code review.

### High-severity bugs (correctness, security, concurrency)

| Test | Bug | CR § | Severity |
|------|-----|------|----------|
| `ResultComparatorTest.maxDD_should_sort_ascending_lowerIsBetter` | Optimizer surfaces the **worst** drawdown at the top of results — comparator sorts descending for every metric, including "lower is better" ones. | §1.1 | HIGH |
| `ResultComparatorTest.maxSL_should_sort_ascending_lowerIsBetter` | Same for max single loss. | §1.1 | HIGH |
| `BackTestFileReaderTest.cache_should_invalidate_when_contents_change_but_size_does_not` | Cache key is `(filename, size)`. Two files with different contents but identical byte length collide → stale data is returned. | §1.3 | HIGH |
| `OrderIdFactoryTest.concurrent_increment_should_not_lose_increments` | `nextOrderID` is a plain `int` with no synchronization. Concurrent increments are lost; **~30-60% of 160k increments routinely lost** under contention. Duplicate order IDs are how IB rejects orders. | §1.5 | HIGH |
| `HolidayScheduleTest.christmas_2021_should_be_holiday` | Holiday table jumps from 2020 to 2026 — Christmas Day, Independence Day, etc. are not flagged for 2021-2025. | §1.7 | HIGH |
| `HolidayScheduleTest.christmas_2022_should_be_holiday` | Same. | §1.7 | HIGH |
| `HolidayScheduleTest.christmas_2023_should_be_holiday` | Same. | §1.7 | HIGH |
| `HolidayScheduleTest.christmas_2024_should_be_holiday` | Same. | §1.7 | HIGH |
| `HolidayScheduleTest.christmas_2025_should_be_holiday` | Same. | §1.7 | HIGH |
| `HolidayScheduleTest.independence_day_2023_should_be_holiday` | Same. | §1.7 | HIGH |
| `HolidayScheduleTest.juneteenth_2023_should_be_holiday` | Juneteenth (federal holiday since 2021) missing entirely. | §1.7 | HIGH |
| `PerformanceManagerTest.updateOnTrade_with_zero_to_zero_should_not_NPE` | `updateOnTrade(0, …, 0, …)` (recovery / no-op path) dereferences a null `Trade` and NPEs. | §1.14 | HIGH |
| `PerformanceChartDataTest.backwards_timestamps_should_not_silently_overwrite` | Out-of-order timestamps silently mutate the *current* bar instead of being rejected or flushed. Catches the case where 3 updates produce only 1 bar with corrupt OHLC. | §1.15 | HIGH |
| `OptimizationMapTest.empty_results_should_not_crash` | Constructor IOOBE on an empty results list. | §1.19 | HIGH |
| `WebHandlerTest.path_traversal_should_be_rejected` | `/../../../etc/passwd.htm` resolves outside `reports/`. Combined with trivial Basic Auth → arbitrary file disclosure. | §1.20 | HIGH |
| `TradeTest.getAverageBoughtPrice_with_no_buys_should_not_be_NaN` | Short-only trade → divide by zero → NaN price. | §5.1 | HIGH |
| `TradeTest.getAverageSoldPrice_with_no_sells_should_not_be_NaN` | Long-only trade → divide by zero → NaN price. | §5.1 | HIGH |
| `CentroidGradientOptimizerRunnerTest.centroid_with_all_zero_or_negative_metrics_should_not_be_NaN` | `sumOfPerformance == 0` → NaN centroid → propagates through `(int) NaN = 0` → silently corrupted bounds in next pass. | §5.2 | HIGH |
| `CentroidGradientOptimizerRunnerTest.centroid_with_one_result_should_not_be_NaN` | Same — `cutoff = (int)(1 * 0.382) == 0`, inner loop skipped. | §5.2 | HIGH |
| `CentroidGradientOptimizerRunnerTest.gradient_with_all_zero_metrics_should_not_be_NaN` | Same idiom in `GradientOptimizerRunner`. | §5.3 | HIGH |
| `CentroidGradientOptimizerRunnerTest.gradient_with_uniform_metric_should_not_be_NaN` | When all metrics equal, `range = max - min = 0` → NaN normalization. | §5.3 | HIGH |
| `PerformanceEvaluatorTest.all_wins_should_not_produce_infinity` | All-wins trade list returns `optimalLeverage = pi = optimalGrowth = +∞`; this strategy then ranks above everything in the optimizer. | §5.6 | HIGH |
| `FunctionEvaluatorTest.no_losses_should_yield_finite_max_leverage` | `getMaxLeverage() = -1 / 0 = -∞` passed as the right-bracket of golden-section search. | §5.7 | HIGH |

### Medium-severity bugs (validation, schedule, partial-fills, edge cases)

| Test | Bug | CR § | Severity |
|------|-----|------|----------|
| `MarketSnapshotFilterTest.to_boundary_should_include_sub_second` | Date filter sets `toDate` to `23:59:59.000`, dropping any snapshot at `23:59:59.500+`. | §1.10 | MEDIUM |
| `TradeTest.slippage_should_be_accumulated_across_partial_fills` | Slippage is overwritten by each fill, not accumulated. Multi-leg trades report only the last leg's slippage scaled by total quantity. | §1.13 | MEDIUM |
| `WebAuthenticatorTest.slash_separator_collision_should_be_rejected` | `authPair = user + "/" + password` — `(alice/x, y)` collides with `(alice, x/y)`. | §1.21 | MEDIUM |
| `StrategyParamTest.setStep_zero_should_be_rejected` | No validation; the optimizer's `for (v = min; v <= max; v += step)` then never terminates. | §5.5 | MEDIUM |
| `StrategyParamTest.setStep_negative_should_be_rejected` | Same. | §5.5 | MEDIUM |
| `StrategyParamTest.setMin_above_max_should_be_rejected` | No validation; produces negative `getRange()`. | §5.5 | MEDIUM |
| `StrategyParamTest.setMax_below_min_should_be_rejected` | Same. | §5.5 | MEDIUM |
| `StrategyParamTest.getMiddle_does_not_overflow_for_large_min_max` | `(min + max) / 2d` computes the addition as `int` before promotion → overflow for `min + max > Integer.MAX_VALUE`. | §5.5 | LOW |
| `PerformanceManagerTest.first_trade_loss_should_not_count_as_drawdown_from_zero` | `peakNetProfit` starts at 0 instead of starting equity, so a first-trade loss is reported as drawdown. | §5.13 | LOW |
| `BalanceEMATest.length_zero_should_be_rejected` | `length=0` → `multiplier=2.0` → EMA diverges. | §5.8 | MEDIUM |
| `BalanceEMATest.length_negative_should_be_rejected` | `length=-1` → `multiplier=∞` → NaN propagation. | §5.8 | MEDIUM |
| `LineParserTest.bid_greater_than_ask_should_throw` | A data line with `bid > ask` is silently accepted; downstream snapshots have negative spreads. | §8.1 | MEDIUM |

### Low-severity bugs

| Test | Bug | CR § | Severity |
|------|-----|------|----------|
| `MarketDepthModelTest.hasValidBidStructure_empty_book_should_be_false` | `hasValidBidStructure()` returns `true` for an empty list (the for-loop simply doesn't enter). Not exploitable today because `MarketDepth.isValidDepth()` size-checks first; documenting the API misbehavior. | — | LOW |
| `BalanceVelocityTest.reset_should_zero_observable_value` | `BalanceEMA.reset()` zeros `value`; `BalanceVelocity.reset()` clears only `fast`/`slow` and leaves `value` stale. Inconsistent across indicators. | — | LOW |

### 37 failing tests, 23 unique bugs

The 37 failing tests cover **23 unique bugs** from the code review.
Some bugs are documented by multiple tests (the 7 Holiday tests are
all the same root cause, for example).

## 2. Passing tests

The other 244 tests are positive coverage of code that works
correctly. They protect against regressions in the existing 28-test
leverage / Kelly evaluator suite (already present) plus everything new:

| Subsystem | Tests | Notes |
|-----------|-------|-------|
| `optimizer` (StrategyParam(s), OptimizationResult, PerformanceMetric, OptimizerRunner.getTasks) | ~40 | Cartesian-product, get-by-name, copy-constructor invariants. |
| `marketbook` (BalanceAggregator, MarketBook, MarketSnapshot, MarketSnapshotFilter, SnapshotComparator) | ~30 | Locking detection, gap detection, balance scaling, date-range filtering. |
| `marketdepth` (MarketDepthModel, MarketDepthItem) | ~19 | Insert/delete/update, depth cap at 10, valid bid/ask structure. |
| `commission` (Commission, CommissionFactory) | ~10 | Per-contract rate, minimum, max-percent cap, factory presets. |
| `schedule` (TradingSchedule, HolidaySchedule) | ~17 | Schedule boundaries, holiday lookups, timezone parsing. |
| `snapshotwriter` (TimeFilter) | 7 | Recordable-hour windows. |
| `chart` (Bar, BarSize, TimedValue, PerformanceChartData) | ~16 | OHLC initialization, bucket boundaries. |
| `ibhandler` (OrderKeeper, OrderExecution, OrderIdFactory) | ~12 | Including the **concurrent OrderKeeper** test (which passes — it's already `synchronized`). |
| `web` (WebAuthenticator, WebHandler) | ~6 | Default credentials work, index page returns 200. |
| `util/contract` (ContractFactory) | 2 | Futures contract factory. |
| `util/format` (NumberFormatterFactory) | 4 | Decimal separator, grouping, banker's rounding. |
| `preferences` (JBTPreferences, PreferencesHolder) | ~9 | Default-value sanity checks, round-trip persistence. |
| `indicator` (IndicatorManager, BalanceEMA, BalanceVelocity, TensorEqualizer) | ~17 | Dedup-by-key, gap-reset, warm-up, convergence. |
| `performance` (Trade, FunctionEvaluator, PerformanceEvaluator, PerformanceManager) | ~15 | Including positive coverage of getters / no-trade defaults. |
| `backtest` (LineParser, BackTestFileReader) | ~22 | Parsing edge cases, caching behavior. |
| `test` (existing leverage tests) | 28 | Unchanged from upstream. |

Total: 281 tests, 244 passing.

## 3. What to do if a failing test passes unexpectedly

A failing test passing without a corresponding fix is a signal worth
investigating. Possibilities, in rough order of likelihood:

1. **The bug was already fixed somewhere.** Check `git log` for changes
   to the file under test.
2. **The test is flaky** (concurrency / timing). The concurrency tests
   in particular —
   `OrderIdFactoryTest.concurrent_increment_should_not_lose_increments`,
   `PerformanceChartDataTest.concurrent_update_and_read_does_not_throw` —
   may produce occasional false negatives on lightly loaded systems.
   Re-run a few times; if it consistently passes, treat it as fixed.
3. **The bug analysis was wrong.** This is the most interesting case —
   it means the code is doing the right thing for a reason I didn't
   appreciate. Pick the test apart and update CODE_REVIEW.md.

If a *passing* test starts failing after a fix, that's also a signal —
the fix likely broke something the test was protecting.

## 4. How the bug-demonstration tests are structured

Each bug-demonstration test:

- **Asserts the correct behavior** (not the current buggy behavior).
  That way the test fails today and passes after the fix, with no
  test-rewrite needed.
- **Carries a Javadoc explaining the bug**, with the corresponding
  CODE_REVIEW.md section reference and a note that the test is
  expected to fail until the fix lands.
- Where possible, includes the actual failure mode in the assertion
  message — e.g., the `OrderIdFactory` test reports the exact number
  of increments lost; the `PerformanceChartData` test reports the
  before/after bar count.

## 5. Notes on test infrastructure

A few unusual patterns are used to make difficult-to-construct classes
testable without source changes:

- **`TestSupport.ensureInitialised()`** initializes `Dispatcher` exactly
  once per JVM (it creates `reports/` and `marketData/`, scans the
  classpath for strategies). Tests that need a working `EventReport`,
  `PreferencesHolder`, or web-handler base directories call this from
  `@BeforeClass`.
- **`sun.misc.Unsafe.allocateInstance()`** is used to instantiate
  `OptimizerRunner` subclasses, `PerformanceManager`, etc. without
  invoking their constructors (which would otherwise require an
  `OptimizerDialog` / `Strategy` / live IB connection). After
  allocation, fields are set reflectively. This keeps the tests small
  and focused on the math; once the heavy dependencies are made
  injectable or interfaced, these reflection helpers can be replaced
  with constructor-based setup.
- **Mockito 5** is used to stub `PerformanceManager`, `HttpExchange`,
  and `OptimizerDialog` where their full implementations would pull in
  too much. The dependency is `<scope>test</scope>` only.

## 6. Running the suite

```sh
# Full suite
mvn test

# Single class
mvn test -Dtest=ResultComparatorTest

# Single method
mvn test -Dtest=HolidayScheduleTest#christmas_2023_should_be_holiday

# With more parallelism / stress on the concurrency tests
mvn test -Dtest=OrderIdFactoryTest -DforkCount=4
```

The full suite runs in under 15 seconds on a modern machine. The slow
tests are the `OrderIdFactoryTest` 5-second timeout test (intentional)
and the `IndicatorManagerTest` warmup test (~3s — feeds 10 800
synthetic snapshots through an indicator manager).
