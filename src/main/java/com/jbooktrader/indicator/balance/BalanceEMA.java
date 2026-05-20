package com.jbooktrader.indicator.balance;

import com.jbooktrader.platform.indicator.Indicator;

/**
 * Exponential moving average of the balance in the limit order book.
 *
 * @author Eugene Kononov
 */
public class BalanceEMA extends Indicator {
    private final double multiplier;

    public BalanceEMA(int length) {
        super(validateLength(length));
        multiplier = 2.0 / (length + 1.0);
    }

    private static int validateLength(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("BalanceEMA length must be positive, got " + length);
        }
        return length;
    }

    @Override
    public void calculate() {
        double balance = marketBook.getSnapshot().getBalance();
        value += (balance - value) * multiplier;
    }

    @Override
    public void reset() {
        value = 0;
    }

}
