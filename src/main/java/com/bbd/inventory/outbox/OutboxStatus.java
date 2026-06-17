package com.bbd.inventory.outbox;

public enum OutboxStatus {
    PENDING,                    // written by business transactional, not yet on Kafka
    SENT                        // relay confirmed publish.
}
