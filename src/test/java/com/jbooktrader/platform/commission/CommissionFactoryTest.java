package com.jbooktrader.platform.commission;

import org.junit.Test;

import static org.junit.Assert.*;

public class CommissionFactoryTest {

    @Test
    public void bundled_north_america_future_commission_is_2_25() {
        Commission c = CommissionFactory.getBundledNorthAmericaFutureCommission();
        assertEquals(2.25, c.getCommission(1, 100.0), 1e-9);
    }

    @Test
    public void micro_future_commission_is_0_62() {
        Commission c = CommissionFactory.getMicroFutureCommission();
        assertEquals(0.62, c.getCommission(1, 100.0), 1e-9);
    }

    @Test
    public void nymex_future_commission_is_2_31() {
        Commission c = CommissionFactory.getNYMEXFutureCommission();
        assertEquals(2.31, c.getCommission(1, 100.0), 1e-9);
    }

    @Test
    public void five_contracts_scale_by_rate() {
        Commission c = CommissionFactory.getBundledNorthAmericaFutureCommission();
        assertEquals(2.25 * 5, c.getCommission(5, 100.0), 1e-9);
    }
}
