# JBookTrader Architecture

This document describes the internal architecture of JBookTrader: how the
pieces fit together, the data that flows between them, and the lifecycle of a
strategy from a market-depth tick to an order being placed at Interactive
Brokers.

JBookTrader is a desktop Java application that lets you record, backtest,
optimize, forward-test, and live-trade market-depth (Level 2) trading
strategies through Interactive Brokers' Trader Workstation (TWS) or IB
Gateway. It is a single Swing-based JAR with no server side. State lives on
disk under the working directory; configuration lives in the Java
`Preferences` store.

## 1. High-level picture

```
                                +-----------------------+
                                |   TWS / IB Gateway    |
                                | (external IB process) |
                                +-----------+-----------+
                                            ^
                                            | TWS API (socket)
                                            v
+----------------+        +-----------------+-----------------+
|                |        |          ibhandler              |
|  Swing UI      |        |  Trader (EWrapper)              |
|  (dialogs)     |        |  TraderAssistant (EClient)      |
|                |        |  MarketDataHandler              |
|                |        |  OrderHandler                   |
|                |        +-----------------+---------------+
|                |                          |
|                |                          | MarketSnapshot queue
|                |                          v
|                |        +-----------------+----------------+
|                |        |  StrategyRunner (background      |
|                |<-------+    consumer thread)              |
|                |        +-----------------+----------------+
|                |                          |
|                |                          | per-strategy
|                |                          v
|                |        +-----------------+----------------+
|                |        |  Strategy.process()              |
|                |        |   onBookSnapshot() -> orders     |
|                |        |   updates Indicator(s), Position |
|                |        |   PerformanceManager metrics     |
|                |        +-----------------+----------------+
|                |                          |
|                |                          v
|                |        +-----------------+----------------+
|                |        |  OrderManagerAssistant.trade()   |
|                |        |   -> placeMarketOrder            |
|                |        +----------------------------------+
+----------------+
```

Two big things to notice:
- The same in-memory `Strategy` and indicator machinery is used in **all**
  modes. Backtesting feeds it `MarketSnapshot`s from a file; live trading
  feeds it `MarketSnapshot`s built from TWS market-depth callbacks. Code
  paths converge at `Strategy.process()` and `OrderManagerAssistant.trade()`.
- The `Mode` (Trade / BackTest / Optimization / etc.) controls side effects:
  whether the snapshot pipeline goes through a `BlockingQueue` from TWS,
  whether reporting/notifications are enabled, whether orders are sent to IB
  or simulated.

![JBookTrader main window](images/main-window.png)

## 2. Bootstrap

Entry point: `com.jbooktrader.platform.startup.JBookTrader`
(`src/main/java/com/jbooktrader/platform/startup/JBookTrader.java`).

`main()` performs the following:

1. Configures the FlatLaf "Gray IntelliJ" look-and-feel.
2. Takes an exclusive file lock on `$TMPDIR/JBookTrader.lock` so only one
   instance can run at a time.
3. Calls `Dispatcher.getInstance().init()` which:
   - Resolves `homeDir` to the current working directory.
   - Creates `reports/` and `marketData/` under it if missing.
   - Records `src/main/resources/` as the resources directory.
   - Calls `StrategyLoader.getStrategies()` to scan the classpath for
     classes that extend `Strategy` and instantiate them.
4. Constructs `MainFrameController` which builds `MainFrameDialog` (the main
   window) and wires up listeners for the menu and popup-menu actions.

If an optional CLI argument is `true`, JBookTrader switches to Trade mode
immediately and adds every loaded strategy to the order manager
(`tradeAll`). Useful for unattended startup.

## 3. The Dispatcher: app-wide service locator

`com.jbooktrader.platform.model.Dispatcher` is a singleton that holds the
global services and the current `Mode`. Most subsystems pull what they need
from it rather than holding direct references:

| Service                  | Purpose                                            |
|--------------------------|----------------------------------------------------|
| `EventReport`            | HTML event log written under `reports/`            |
| `OrderManager` / `OrderManagerAssistant` | Order routing & per-strategy bookkeeping |
| `PortfolioManager`       | Aggregate account value & leverage gate            |
| `DaySchedule`            | Trading-day awareness (reset, end-of-day checks)   |
| `NTPClock`               | Network-time-corrected wall clock                  |
| `List<Strategy>`         | All discovered strategy instances                  |
| `Mode` + `previousMode`  | Current operational mode                           |

`Dispatcher.setMode(Mode)` is the only "state machine" of significance.
Transitioning into `Trade` or `ForwardTest` opens the IB connection and
starts the optional monitoring HTTP server. Transitioning into `BackTest`,
`BackTestAll`, or `Optimization` disconnects from IB. Transitioning into
`Optimization` also silences the event-report writer so a brute-force run of
millions of strategies doesn't drown the disk in HTML.

`Dispatcher` also implements a small pub/sub: views register as
`ModelListener` and receive `Event.ModeChanged`, `Event.StrategyUpdate`,
`Event.SystemStatusUpdate`, `Event.Error` events.

## 4. Modes

`com.jbooktrader.platform.model.Mode`:

| Mode           | Source of snapshots             | Orders go to | Reporting | Web server |
|----------------|---------------------------------|--------------|-----------|------------|
| `Trade`        | TWS market-depth callbacks      | IB (real)    | on        | on (if cfg)|
| `ForwardTest`  | TWS market-depth callbacks      | simulated    | on        | on (if cfg)|
| `ForceClose`   | TWS market-depth callbacks      | IB (real)    | on        | on         |
| `BackTest`     | File (single strategy)          | simulated    | on        | off        |
| `BackTestAll`  | File (portfolio of strategies)  | simulated    | on        | off        |
| `Optimization` | File (millions of param combos) | simulated    | off       | off        |

`ForceClose` is a special transient state entered via the *Suspend trading*
menu item or automatically by watchdogs (market-data timeout, fatal
exception): all open positions are flattened, then trading stops.

## 5. Strategies, indicators, parameters

### `Strategy` (abstract base)
`com.jbooktrader.platform.strategy.Strategy` is the contract every strategy
extends. A strategy is constructed by reflection from `StrategyLoader` with
a fresh `StrategyParams`. The lifecycle in chronological order:

1. **Construction.** The concrete subclass's no-arg-equivalent constructor
   calls `setParams()` to declare its tunable integer parameters
   (`addParam(name, min, max, value)`), and then a base class like
   `StrategyES` calls `setStrategy(contract, tradingSchedule, multiplier,
   commission)` to register the instrument, trading window, contract
   multiplier, and commission model.
2. **Indicators.** Just before the strategy is actually used,
   `OrderManagerAssistant.addStrategy()` installs a fresh `IndicatorManager`
   and calls `setIndicators()` — the strategy registers each `Indicator` it
   needs via `addIndicator(...)`. Indicators are deduplicated by their key
   (class name + parameters), so two strategies sharing the same indicator
   instance share computation.
3. **Per-snapshot processing.** `Strategy.process()` is called once per
   `MarketSnapshot`:
   - All indicators recompute (`IndicatorManager.updateIndicators()`).
   - If we are inside the trading schedule, indicators are warmed up, and
     mode isn't `ForceClose`, the strategy's `onBookSnapshot()` runs.
   - The strategy calls `goLong(n)` / `goShort(n)` / `goFlat()`, which only
     set a *target position* on the `PositionManager`. The actual order is
     placed by `OrderManagerAssistant.trade(strategy)` immediately after.
4. **Per-trade callbacks.** When a fill comes back (live) or is simulated
   (backtest/forward-test), `PerformanceManager.updateOnTrade(...)` records
   the trade and updates running statistics.

A complete sample strategy lives in
`src/main/java/com/jbooktrader/strategy/ESLongTensorEqualizer1.java`:

```java
public class ESLongTensorEqualizer1 extends ESLongTensorEqualizerBase {
    public ESLongTensorEqualizer1(StrategyParams p) { super(p); }
    @Override public void setParams() {
        addParam(PERIOD, 10, 10000, 3000);
        addParam(SCALE,  1, 1500, 1092);
        addParam(ENTRY,  0, 1000, 227);
        addParam(EXIT,   0, 1000, 539);
    }
}
```

The `ESLongTensorEqualizerBase` parent's `onBookSnapshot()` reads a single
indicator (`TensorEqualizer`) and calls `goLong(1)` or `goFlat()` based on
sigma-tension thresholds. The base class `StrategyES`
(`strategy/base/StrategyES.java`) pins it to the CME ES future, a
10:05–15:25 New York trading schedule, a multiplier of 50, and the bundled
North-America-futures commission.

### `Indicator`
`com.jbooktrader.platform.indicator.Indicator` has two abstract methods,
`calculate()` and `reset()`. Each indicator holds a reference to the active
`MarketBook` and reads `marketBook.getSnapshot()` to pull the latest bid,
ask, volume, and computed *book balance* (a scalar derived from the L2
depth — see § 8). Indicators are keyed by their constructor parameters so
that, e.g., two strategies asking for `TensorEqualizer(3000, 1092)` share
the same instance.

Bundled indicators live in `src/main/java/com/jbooktrader/indicator/`:
- `balance/` — features over the book balance (`BalanceEMA`,
  `BalanceVelocity`, `BalanceAcceleration`, `BalanceSigma`).
- `price/` — `LogPriceVelocity`.
- `combo/` — `TensorEqualizer`, which fuses balance velocity and price
  velocity into a sigma-normalized "tension" signal.

`IndicatorManager` decides whether enough samples have flowed for indicator
output to be trusted (`MIN_SAMPLE_SIZE = 180 * 60` ticks ≈ 3 hours of
1-second snapshots). It also resets all indicators if there is a gap of
more than 5 minutes in the data, so backtests across discontinuities behave
the same as a fresh trading day.

### `StrategyParams` and `StrategyParam`
A parameter is an integer with `min`, `max`, `step`, and `value`. The
optimizer cartesian-products them. `Strategy.addParam(name, min, max, value)`
computes a default `step = max(1, (max-min)/5)`, which is overrideable in
the optimizer dialog.

### `StrategyLoader`
Scans every URL on the system class path; collects all `.class` entries
under `com/jbooktrader/strategy/` (excluding `base/`); reflectively
instantiates each non-abstract class that extends `Strategy` with the
single-arg `(StrategyParams)` constructor. This is what makes adding a new
strategy a drop-in operation: any class in that package is picked up
automatically the next time JBookTrader starts.

## 6. Market data plumbing

### Live: TWS callbacks → MarketDepth → MarketSnapshot → queue
`com.jbooktrader.platform.ibhandler.Trader` extends `EWrapperAdapter` and
receives every TWS callback. The interesting ones:

- `updateMktDepth(tickerId, position, operation, side, price, size)` —
  forwarded to `MarketDataHandler` → `MarketDepth.update(...)`. A
  `MarketDepth` keeps two `MarketDepthModel`s (10 levels of bids and 10
  levels of asks) and runs them through `BalanceAggregator` to derive a
  per-tick book balance in [-100, +100].
- `tickSize(tickerId, field, size)` — used to detect the most liquid
  contract month for a roll. `MarketDepth.processVolume()` picks the
  current "front month" automatically once it has three candidates and
  enough volume.
- `execDetails(...)` — turned into `OrderExecution` events delivered to
  the `OrderHandlerListener` (i.e. the `OrderManager`).

Every wall-clock second, `MarketDepth.takeMarketSnapshot()` collapses the
in-flight depth state into one `MarketSnapshot(time, balance, bid, ask,
volume)` for the contract's `localSymbol`. This snapshot is pushed onto a
`BlockingQueue<MarketSnapshot>` that the `OrderHandler` owns. The
`StrategyRunner` background thread consumes that queue and dispatches each
snapshot to every strategy whose ticker matches.

`StrategyRunner` also has a watchdog (`MarketDataTimeoutTask`) that, while
the current `DaySchedule` says we are inside the trading period, transitions
to `ForceClose` if no snapshot has arrived for `MarketDataTimeoutSeconds`
(default 60).

### Backtest: file → MarketSnapshot list
For backtests/optimization, market data comes from a flat text file with
this header and per-second rows
(see `marketData/ES.txt`):

```
# This historical data file was created by JBookTrader
# Each line represents a 1-second snapshot of the market and contains 6 columns
# 1. date in the MMddyy format
# 2. time in the HHmmss format
# 3. book balance
# 4. best bid
# 5. best ask
# 6. volume

timeZone=America/New_York

113018,070000,21.91,2730.5,2730.75,4
113018,070001,21.39,2730.5,2730.75,21
...
```

`BackTestFileReader.load()` parses the file into `List<MarketSnapshot>` (and
caches the result keyed on file path, size, and optional date filter so
repeated runs of the same data don't repay the parse cost). Date filtering
is handled by `MarketSnapshotFilter`. The `BackTester` then walks the list
and calls `marketBook.setSnapshot(...)` + `strategy.processInstant(...)` for
every entry, exactly the same code path used live, minus the IB socket.

These files can be produced from live data by `SnapshotWriterManager` /
`SnapshotWriter` (under `platform/snapshotwriter/`), which records every
snapshot between 07:00 and 16:00 to `marketData/<ticker>.txt`.

### `MarketBook` and locking
`MarketBook` holds the latest `MarketSnapshot` for one instrument plus a
*lock* detector: if the mid price doesn't change for 15 minutes, the book
is flagged as "locked" — an indication that the data is stale or markets
are dead. `IndicatorManager` resets all indicators whenever the book is
locked, so a long flat patch can't poison EMA-style state.

## 7. Order management

`OrderManager` is the public face; `OrderManagerAssistant` does the work
(`platform/ordermanager/`).

- `connect()` resolves the IB account from preferences and calls
  `OrderHandler.connect(host, port, clientID, account)` →
  `TraderAssistant.connect(...)` which opens the TWS socket, attaches an
  `EReader`, and waits on a semaphore for the account to be confirmed.
- `addStrategy(strategy)` installs the strategy in `strategies` (a
  map keyed by a generated id), attaches its `IndicatorManager`, calls
  `setIndicators()`, and — in live or forward-test mode — subscribes to
  market depth for the strategy's contract and registers the strategy as a
  listener on the shared `StrategyRunner`.
- `trade(strategy)` is called by `Strategy.processInstant()` after
  `onBookSnapshot()`. It diffs the strategy's current position against the
  target the strategy set, gates by the leverage limit
  (`PortfolioManager.isWithinMaxLeverage()`), and:
  - In `Trade` mode (or `ForceClose` originated from `Trade`), submits a
    real `MKT` order via `TraderAssistant.placeMarketOrder(...)`.
  - In `ForwardTest`, fabricates an `OrderExecution` filled at the
    expected price and feeds it back to `PositionManager.update()`.
- `forceClose(reason)` is the central kill-switch: it is called by the
  watchdogs and by `Trader.error(...)` when IB sends a fatal error code.
- `getSystemStatus()` returns the string rendered into the main window's
  title bar (`Account: ..., Trades: ..., P&L: ..., Portfolio: ..., Balance: ...`).

Open orders are tracked in `OrderKeeper`; an open order older than
`OpenOrderTimeoutSeconds` (default 120) tells the watchdog to force-close.

## 8. Book balance and the `BalanceAggregator`

The defining feature of JBookTrader is that strategies see *book balance*
rather than a candlestick. Per L2 update, `BalanceAggregator.aggregate(bids,
asks)` computes:

```
balance = (cumulativeBidSize - cumulativeAskSize)
        / (cumulativeBidSize + cumulativeAskSize)
```

across the top 10 levels. `MarketDepth` accumulates these in-second and
reports their mean times 100 (to put it in [-100, +100]) as the
`balance` field of the next `MarketSnapshot`. Positive balance means more
size resting on the bid; negative means more on the ask. All bundled
indicators are functions of this balance and the mid-price.

## 9. Performance, charting, reports

### `PerformanceManager` (per strategy)
Tracks net profit, intra-trade and cumulative drawdown, peak NP, single-
trade losses, time-in-market, trade count, and a list of per-trade returns.
On trade completion it derives:

- **APD** (`netProfit / cumulativeIntraTradeDD`),
- **OG / PI**: optimal-growth and performance-index metrics computed by
  `PerformanceEvaluator` (Kelly-flavored leverage estimation),
- **MaxSL, MaxDD, % profitable, AveDuration**: classic measures.

`updateAtEnd()` is called once at the end of a backtest to finalize these
totals.

### `PerformanceChartData` and `PerformanceChart`
Built during a backtest: for every `MarketSnapshot` we bucket the
mid-price into an OHLC bar of the configured `BarSize` (1s..2h, see
`platform/chart/BarSize.java`), and for each indicator we bucket its
value into a separate sub-chart. Trade markers are recorded with their
fill prices. `PerformanceChart` then renders a JFreeChart combined plot:
price + trade arrows on top, indicator panels in the middle, cumulative
net-profit on the bottom.

![Performance chart for a backtest](images/performance-chart.png)

### Reports
Three classes write HTML under `reports/`:
- `EventReport` — chronological log (errors, mode changes, IB events).
- `StrategyReport` — per-strategy live/forward-test log.
- `OptimizationReport` — the top-100 parameter sets of an optimization
  run, sorted by the selected metric.

## 10. Optimizer

`OptimizerDialog` collects: data file, optional date range, performance
metric (Net Profit / OG / PI / APD), kernel (used by some evaluators),
inclusion criteria ("All strategies" or "Profitable strategies"),
parameter bounds (Lenient or Strict), search method, and per-parameter
min/max/step.

Four search methods, each a subclass of `OptimizerRunner`:

| Method            | Idea                                                       |
|-------------------|------------------------------------------------------------|
| Brute force       | Cartesian product of every (min..max step) per parameter   |
| Divide & Conquer  | Brute force, then narrow around the best, recurse N times  |
| Centroid          | Nelder-Mead-style centroid moves over the parameter space  |
| Gradient          | Local hill-climb around a seed                             |

`OptimizerRunner` parallelizes by handing batches of `StrategyParams`
("tasks") to an `ExecutorService` with `availableProcessors + 1` threads.
Each `OptimizerWorker` constructs fresh `Strategy` instances (via the
reflective `(StrategyParams)` constructor captured at runner creation
time), runs the full `BackTester` against the cached
`List<MarketSnapshot>`, scores them with the chosen metric, and reports
`OptimizationResult`s back to the runner. The runner sorts them with
`ResultComparator`, writes the top 100 to
`reports/<Strategy>Optimizer.htm`, and updates the UI.

The "Optimization Map" button (`chart/OptimizationMap.java`) renders the
result surface as a 2D heat-map when two parameters are varied.

![Optimizer dialog](images/optimizer.png)

## 11. Portfolio backtest

`PortfolioBackTestDialog` + `PorfolioBackTestRunner` +
`PortfolioBackTester` run every selected strategy serially against the same
data file, aggregate per-trade profits as if they were trades from a single
combined portfolio, and render the per-strategy and combined equity curves
in `PortfolioChart`.

![Portfolio backtest dialog](images/portfolio-backtest.png)

## 12. Configuration & preferences

`JBTPreferences` enumerates every preference name and its default
(see `platform/preferences/JBTPreferences.java`). `PreferencesHolder` is a
thin wrapper around `java.util.prefs.Preferences` rooted at
`com.jbooktrader.JBookTrader` (per-user, OS-level prefs store — on Linux
it lands under `~/.java/.userPrefs/`). `PreferencesDialog` is the
six-tab Swing panel that edits them; changes persist immediately on OK.

## 13. Web monitoring server

When `WebAccess=enabled`, `MonitoringServer` starts an embedded
`com.sun.net.httpserver.HttpServer` on `WebAccessPort` (default 1235) with
HTTP Basic auth (`WebAccessUser` / `WebAccessPassword`). `WebHandler`
serves three things:
- `/` — an HTML status table of every live strategy (ticker, price,
  position, trade count, net profit), linking to per-strategy HTML
  reports.
- Anything ending in `.htm` is served from `reports/`.
- Everything else (CSS, the favicon, the logo) is served from
  `src/main/resources/`.

It's intentionally minimal — useful for checking on a headless trading
host from a phone.

## 14. Email notifications

`Notifier` (`platform/email/`) wraps Jakarta Mail. When `Notification=
enabled`, it submits trade events as plain-text emails (subject/recipients/
SMTP creds from preferences) to the configured recipient list. The
`TradeNotification` class produces the body text. `Notifier.test()` is
hooked up to the "Test" button on the Notifications preferences tab.

## 15. Threading model

| Thread                                     | Owner                                   |
|--------------------------------------------|-----------------------------------------|
| Swing EDT                                  | UI                                       |
| `StrategyRunner` "Runner" executor (1)     | Consumes the snapshot queue              |
| `MarketDataTimeoutTask` (Timer)            | Force-close watchdog                     |
| `TraderAssistant` `EReader` executor (1)   | Reads TWS socket and dispatches callbacks|
| `Notifier` executor                        | Sends emails                             |
| `NTPClock` scheduled executor              | Periodic NTP offset refresh              |
| `OptimizerRunner` `ExecutorService` (CPUs+1)| Parallel parameter evaluation            |
| `MonitoringServer` single-thread executor  | Serves HTTP                              |

The Swing EDT only mutates view state. Background events that need a UI
update go through `SwingUtilities.invokeLater(...)` or the
`Dispatcher.fireModelChanged` listener fan-out.

## 16. Source map

```
src/main/java/com/jbooktrader
├── platform/
│   ├── startup/         JBookTrader (main)
│   ├── model/           Dispatcher, Mode, MainFrameController, TableDataModel
│   ├── dialog/          MainFrameDialog, AboutDialog, JBTDialog
│   ├── preferences/     JBTPreferences enum, PreferencesHolder, PreferencesDialog
│   ├── strategy/        Strategy base, StrategyRunner, StrategyLoader, info dialog
│   ├── indicator/       Indicator base, IndicatorManager
│   ├── marketbook/      MarketSnapshot, MarketBook, BalanceAggregator, filter
│   ├── marketdepth/     MarketDepth, MarketDepthModel (10-level book)
│   ├── ibhandler/       Trader (EWrapper), TraderAssistant, OrderHandler & friends
│   ├── ordermanager/    OrderManager, OrderManagerAssistant
│   ├── position/        PositionManager, Position
│   ├── portfolio/       PortfolioManager, portfolio backtest dialog/runner/chart
│   ├── performance/     PerformanceManager, evaluators (Kelly/Youden/Power/...)
│   ├── backtest/        BackTestDialog, BackTester, BackTestFileReader, LineParser
│   ├── optimizer/       OptimizerDialog, 4 runners, params model, results model
│   ├── chart/           PerformanceChart, BarSize, Bar, OptimizationMap
│   ├── snapshotwriter/  Records live snapshots to disk
│   ├── schedule/        TradingSchedule, HolidaySchedule, EndOfYearSchedule
│   ├── commission/      Commission, CommissionFactory
│   ├── email/           Notifier, TradeNotification
│   ├── report/          EventReport, StrategyReport, OptimizationReport, StatusReport
│   ├── web/             MonitoringServer, WebHandler, WebAuthenticator
│   └── util/
│       ├── ui/          MessageDialog, SpringUtilities, TitledSeparator, ExitScheduler
│       ├── format/      NumberFormatterFactory
│       ├── contract/    ContractFactory (IB Contract helpers)
│       └── ntp/         NTPClock, DaySchedule
├── indicator/           Bundled indicators (balance/, price/, combo/)
└── strategy/            Bundled strategies + base classes (StrategyES, StrategyNG, ...)
```

## 17. Extension points at a glance

- **New strategy:** drop a `public class FooStrategy extends StrategyXX`
  into `com/jbooktrader/strategy/`. Implement `setParams()`,
  `setIndicators()`, and `onBookSnapshot()`. No registration needed —
  `StrategyLoader` finds it.
- **New indicator:** subclass `Indicator`, implement `calculate()` and
  `reset()`, register it from your strategy's `setIndicators()`.
- **New instrument base:** subclass `Strategy` (or copy `StrategyES`) and
  set the IB `Contract`, trading schedule, contract multiplier, and
  commission in the constructor.
- **New commission model:** add a factory method to `CommissionFactory` or
  construct `new Commission(rate, min)` directly.
- **New optimizer search:** subclass `OptimizerRunner`, implement
  `optimize()`, add it to `OptimizerDialog`'s combo box.
- **New performance metric:** add an enum value to `PerformanceMetric` and
  a column to the results table; teach `ResultComparator` how to sort it.
