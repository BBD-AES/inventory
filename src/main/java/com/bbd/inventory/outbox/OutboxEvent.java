package com.bbd.inventory.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    // downstream dedup key - same UUID as payload.eventId
    @Column(nullable = false, length = 36)
    private String eventId;

    @Column(nullable = false)
    private String topic;

    // Kafka partition key = soNumber
    @Column(nullable = false)
    private String msgKey;

    // serialized StockReplenished JSON
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private OutboxStatus outboxStatus;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant sentAt;     // null until relay sends

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int attempts;

    // Earliest instant the relay may (re)send this row. Drives backoff while PENDING
    // and the re-drain cooldown while FAILED. Legacy rows may be null = send now.
    private Instant nextRetryAt;

    @Builder
    public OutboxEvent(String eventId, String topic, String msgKey, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.msgKey = msgKey;
        this.payload = payload;
        this.outboxStatus = OutboxStatus.PENDING;
        this.nextRetryAt = Instant.now();   // immediately due
    }

    public void markSent() {
        this.outboxStatus = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void bumpAttempt() { this.attempts++; }

    // Transient failure: stay PENDING, retry no earlier than {@code next}.
    public void scheduleRetry(Instant next) { this.nextRetryAt = next; }

    // Attempt ceiling hit: terminal-ish FAILED, eligible for re-drain at {@code redrainAt}.
    public void markFailed(Instant redrainAt) {
        this.outboxStatus = OutboxStatus.FAILED;
        this.nextRetryAt = redrainAt;
    }

    // Re-drain: push a FAILED row back to PENDING with a fresh retry budget.
    public void requeue() {
        this.outboxStatus = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextRetryAt = Instant.now();
    }

}
