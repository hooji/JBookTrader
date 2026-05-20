package com.jbooktrader.platform.ibhandler;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eugene Kononov
 */
class OrderIdFactory {
    private final Semaphore orderIdSemaphore;
    private final AtomicInteger nextOrderID = new AtomicInteger();

    OrderIdFactory() {
        orderIdSemaphore = new Semaphore(0);
    }

    boolean acquireNextOrderID() {
        try {
            return orderIdSemaphore.tryAcquire(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to acquire next order ID", e);
        }
    }


    int getNextOrderID() {
        return nextOrderID.get();
    }

    void setNextOrderID(int nextOrderID) {
        this.nextOrderID.set(nextOrderID);
        orderIdSemaphore.release();
    }

    void incrementOrderID() {
        nextOrderID.incrementAndGet();
    }

}
