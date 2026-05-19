# JBookTrader Documentation

In-tree documentation for JBookTrader, organized by audience:

- **[QUICKSTART.md](QUICKSTART.md)** — Get from a clean checkout to a
  running backtest in about ten minutes. No IB account or live data
  required.
- **[USER_GUIDE.md](USER_GUIDE.md)** — Long-form reference for every UI
  surface: modes, dialogs, preferences, backtesting, optimization,
  forward-test/live-trade, web monitoring, notifications, on-disk
  state, and writing your own strategy.
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Internal architecture: how
  market depth becomes a `MarketSnapshot`, how strategies are
  discovered and driven, how the optimizer parallelizes work, the
  threading model, and a complete source map.
- **[CODE_REVIEW.md](CODE_REVIEW.md)** — Bug and edge-case audit of the
  `2026.1-SNAPSHOT` source tree, organized by severity (critical /
  security / thread safety / resources / numeric / etc.). Each finding
  cites file:line.
- **[TESTING.md](TESTING.md)** — Inventory of existing unit tests and a
  paranoid, prioritized list of proposed new tests, with cross-refs to
  the bugs in `CODE_REVIEW.md`.
- **[TEST_RESULTS.md](TEST_RESULTS.md)** — Current `mvn test` status
  (281 tests, 37 failing). Every failing test maps to a specific bug
  in `CODE_REVIEW.md`; the table shows which test surfaces which bug.

Screenshots live under `images/` and are referenced from the documents.

The `.docx` files alongside these Markdown documents
(`JBookTrader.UserGuide.docx`, `TWSConfigurationForJBookTader.docx`) are
the original Google-Docs–exported manuals; they predate several
features documented here. Where this in-tree documentation disagrees
with them, prefer this one.

If you regenerate the screenshots, see [`/.shim/README.md`](../.shim/README.md)
for the small fixture used to drive a backtest that actually trades on
the bundled sample data.
