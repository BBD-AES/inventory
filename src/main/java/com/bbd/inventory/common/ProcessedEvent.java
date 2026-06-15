package com.bbd.inventory.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "processed_event")
public class ProcessedEvent {

    //UUID - idempotency key (PK -> unique)
    @Id @Column(length = 36) private String eventId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
    }
}
