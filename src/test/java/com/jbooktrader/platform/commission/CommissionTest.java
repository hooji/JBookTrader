package com.jbooktrader.platform.commission;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CommissionTest {

    @Test
    public void per_contract_rate_applied() {
        Commission c = new Commission(2.0, 1.0); // rate 2/contract, min 1
        assertEquals(10.0, c.getCommission(5, 100.0), 1e-9);
    }

    @Test
    public void minimum_applied_when_per_contract_below_minimum() {
        Commission c = new Commission(2.0, 5.0);
        assertEquals(5.0, c.getCommission(1, 100.0), 1e-9);
    }

    @Test
    public void zero_contracts_yields_minimum() {
        Commission c = new Commission(2.0, 1.0);
        assertEquals(1.0, c.getCommission(0, 100.0), 1e-9);
    }

    @Test
    public void max_percent_caps_per_contract() {
        // 1% cap on a $100 trade with 1 contract → max $1 commission
        Commission c = new Commission(5.0, 0.5, 0.01);
        assertEquals(1.0, c.getCommission(1, 100.0), 1e-9);
    }

    @Test
    public void max_percent_does_not_apply_when_per_contract_already_under_cap() {
        // 50% cap on a $100 trade with 1 contract → cap is $50, but per-contract is $5 → $5 wins
        Commission c = new Commission(5.0, 0.5, 0.5);
        assertEquals(5.0, c.getCommission(1, 100.0), 1e-9);
    }

    @Test
    public void toString_is_legible() {
        Commission c = new Commission(2.0, 1.0);
        assertEquals("2.0 per share/contract, 1.0 minimum per trade", c.toString());
    }
}
