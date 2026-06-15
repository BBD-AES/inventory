package com.bbd.inventory.common;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 *  to manually control how Spring Data decides whether to INSERT or UPDATE.
 *
 *  DEFAULT:    save() -> SELECT first -> INSERT or UPDATE
 *  benefit:    if two kafka consumers race on the same eventId, both INSERT can return
 *              empty before either insert commits. So nothing is guaranteed.
 */

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "processed_event")
public class ProcessedEvent implements Persistable<String> {

    //UUID - idempotency key (PK -> unique)
    @Id @Column(length = 36) private String eventId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
    }

    @Override public String getId() { return eventId; }

    /*
        tells Spring Data to skip the SELECT phase as it's a new entity
        and go straight to INSERT

        @Transient needed to be ignored by ORM, don't want it to be mapped
        as database-column
     */

    @Override @Transient
    public boolean isNew() { return true; }
}
