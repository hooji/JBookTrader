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

Screenshots live under `images/` and are referenced from the documents.

The `.docx` files alongside these Markdown documents
(`JBookTrader.UserGuide.docx`, `TWSConfigurationForJBookTader.docx`) are
the original Google-Docs–exported manuals; they are out of date as of
the `2026.1` release. Where this in-tree documentation disagrees with
them, prefer this one.

If you regenerate the screenshots, see [`/.shim/README.md`](../.shim/README.md)
for the small fixture used to drive a backtest that actually trades on
the bundled sample data.
