package com.jbooktrader.strategy;

import com.jbooktrader.indicator.combo.TensorEqualizer;
import com.jbooktrader.platform.optimizer.StrategyParams;
import com.jbooktrader.strategy.base.StrategyTestES;

/**
 * Shim strategy: very low thresholds so trades fire quickly on the included ES.txt
 * sample, used only to capture screenshots that exercise the chart and report code.
 * Not a real trading strategy.
 */
public class QuickTestStrategy extends StrategyTestES {
    private static final String PERIOD = "Period";
    private static final String SCALE = "Scale";
    private static final String ENTRY = "Entry";
    private static final String EXIT = "Exit";

    private TensorEqualizer tensorEqualizer;
    private final int entry, exit;

    public QuickTestStrategy(StrategyParams params) {
        super(params);
        entry = getParam(ENTRY);
        exit = getParam(EXIT);
    }

    @Override
    public void setParams() {
        addParam(PERIOD, 10, 1000, 60);
        addParam(SCALE, 1, 500, 50);
        addParam(ENTRY, 0, 200, 10);
        addParam(EXIT, 0, 200, 5);
    }

    @Override
    public void setIndicators() {
        tensorEqualizer = (TensorEqualizer) addIndicator(new TensorEqualizer(getParam(PERIOD), getParam(SCALE)));
    }

    @Override
    public void onBookSnapshot() {
        double tension = tensorEqualizer.getTension();
        double sigmaTension = tensorEqualizer.getSigmaTension();

        if (tension <= exit) {
            goFlat();
        } else if (sigmaTension >= entry) {
            goLong(1);
        }
    }
}
