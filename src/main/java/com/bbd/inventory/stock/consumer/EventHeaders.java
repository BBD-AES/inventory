package com.bbd.inventory.stock.consumer;

/**
 * Message header keys the producer side sets on every event (CONVENTION §8.2 / §12).
 * Consumers read event_id to de-dupe (at-least-once delivery) and tenancy_id to keep
 * tenant isolation downstream.
 */
public final class EventHeaders {

    private EventHeaders() {}

    public static final String EVENT_ID = "event_id";
    public static final String TENANCY_ID = "tenancy_id";
}
