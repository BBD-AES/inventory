package com.bbd.inventory.common;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 *
 *  DEFAULT:    save() -> SELECT first -> INSERT or UPDATE
 *  benefit:    if two kafka consumers race on the same eventId, both INSERT can return
 *              empty before either insert commits. So nothing is guaranteed.
 */

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
