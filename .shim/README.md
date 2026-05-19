# Documentation-screenshot shim

This directory contains the temporary fixture used to drive UI screenshots
for the documents under `.doc/`. It is **not** part of the runtime
JBookTrader application.

## `QuickTestStrategy.java`

A throw-away strategy with very loose entry/exit thresholds that produces
trades on the bundled `marketData/ES.txt` data file. The screenshots in
`.doc/images/performance-chart.png` and `.doc/images/backtest-results.png`
were taken with this strategy temporarily copied to
`src/main/java/com/jbooktrader/strategy/QuickTestStrategy.java`.

To regenerate the screenshots:

1. Copy the file:
   ```sh
   cp .shim/QuickTestStrategy.java src/main/java/com/jbooktrader/strategy/
   ```
2. Build:
   ```sh
   mvn clean package -DskipTests
   ```
3. Launch JBookTrader and run a backtest of `QuickTestStrategy` against
   `marketData/ES.txt`.
4. Capture the windows you need (chart, results, etc.).
5. Remove the file:
   ```sh
   rm src/main/java/com/jbooktrader/strategy/QuickTestStrategy.java
   ```
6. Rebuild for distribution:
   ```sh
   mvn clean package -DskipTests
   ```

The shim is a real `StrategyTestES` subclass (24-hour-ish window so it
fits any data range) using the `TensorEqualizer` indicator. It is
mathematically arbitrary — its only purpose is to exercise the
trade-generation, chart, and report code paths during screenshot capture.
