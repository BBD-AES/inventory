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

    @Builder
    public OutboxEvent(String eventId, String topic, String msgKey, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.msgKey = msgKey;
        this.payload = payload;
        this.outboxStatus = OutboxStatus.PENDING;
    }

    public void markSent() {
        this.outboxStatus = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() { this.outboxStatus = OutboxStatus.FAILED; }
    public void bumpAttempt() { this.attempts++; }

}
