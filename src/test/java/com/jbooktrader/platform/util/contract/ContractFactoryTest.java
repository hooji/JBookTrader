package com.jbooktrader.platform.util.contract;

import com.ib.client.Contract;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ContractFactoryTest {

    @Test
    public void makeFutureContract_sets_expected_fields() {
        Contract c = ContractFactory.makeFutureContract("ES", "CME");
        assertEquals("ES", c.symbol());
        assertEquals("FUT", c.getSecType());
        assertEquals("CME", c.exchange());
        assertEquals("USD", c.currency());
        assertNull("local symbol should be null for a generic future", c.localSymbol());
    }

    @Test
    public void makeContract_sets_all_fields_including_local_symbol() {
        Contract c = ContractFactory.makeContract("ES", "ESH26", "FUT", "CME", "USD");
        assertEquals("ES", c.symbol());
        assertEquals("ESH26", c.localSymbol());
        assertEquals("FUT", c.getSecType());
        assertEquals("CME", c.exchange());
        assertEquals("USD", c.currency());
    }
}
