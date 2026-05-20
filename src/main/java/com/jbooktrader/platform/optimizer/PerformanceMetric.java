package com.jbooktrader.platform.optimizer;

/**
 * @author Eugene Kononov
 */
public enum PerformanceMetric {
    Trades("Trades", false), // number of trades
    Duration("Duration", false), // average trade duration in minutes
    MaxSL("MSL", true), // maximum single loss — lower is better
    MaxDD("MDD", true), // maximum drawdown — lower is better
    APD("APD", false), // average intraday profit to drawdown
    OG("OG", false), // optimal growth
    PI("PI", false), // performance index
    NetProfit("Net Profit", false);

    private final String name;
    private final boolean lowerIsBetter;

    PerformanceMetric(String name, boolean lowerIsBetter) {
        this.name = name;
        this.lowerIsBetter = lowerIsBetter;
    }

    public static PerformanceMetric getColumn(String name) {
        for (PerformanceMetric performanceMetric : values()) {
            if (performanceMetric.name.equals(name)) {
                return performanceMetric;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public boolean isLowerBetter() {
        return lowerIsBetter;
    }
}
