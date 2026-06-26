package com.bbd.inventory.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/*

    Actually completes the Transactional Outbox Pattern. After Outbox Pattern is executed,
    Kafka hasn't received anything yet, so a Message Relay is used.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 10;

    // Exponential backoff for transient send failures: 5s, 10s, 20s ... capped at 5min,
    // mirroring the consumer-side DefaultErrorHandler so a multi-min broker outage rides out.
    private static final long BASE_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 300_000L;

    // FAILED rows are re-driven back to PENDING after this cooldown (broker likely back).
    private static final Duration REDRAIN_COOLDOWN = Duration.ofMinutes(5);

    @Scheduled(fixedDelay = 1000)       // poll every 1s after prev run finishes.
    public void relay() {
        Instant now = Instant.now();
        List<OutboxEvent> batch = outboxRepository.findDue(
                OutboxStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent ev : batch) {
            try {
                kafkaTemplate.send(ev.getTopic(), ev.getMsgKey(), ev.getPayload())
                        .get(5, TimeUnit.SECONDS);
                ev.markSent();
            } catch (Exception ex) {
                ev.bumpAttempt();
                if (ev.getAttempts() >= MAX_ATTEMPTS) {
                    ev.markFailed(Instant.now().plus(REDRAIN_COOLDOWN));
                    log.error("outbox send FAILED, will re-drain after {} outboxId={} topic={} attempts={}",
                            REDRAIN_COOLDOWN, ev.getId(), ev.getTopic(), ev.getAttempts(), ex);
                } else {
                    ev.scheduleRetry(nextBackoff(ev.getAttempts()));
                    log.warn("relay failed, backing off outboxId={} topic={} attempts={}",
                            ev.getId(), ev.getTopic(), ev.getAttempts(), ex);
                }
            }

            // The scheduled thread has no open session/tx (OSIV covers web threads only),
            // so the entity is detached and dirty-checking won't flush. Persist explicitly,
            // else SENT never sticks and every PENDING row is re-sent every poll (dup storm).
            outboxRepository.save(ev);
        }
    }

    // Periodically resurrect FAILED rows whose cooldown has elapsed: PENDING + fresh budget.
    @Scheduled(fixedDelay = 60_000)
    public void redrainFailed() {
        Instant now = Instant.now();
        List<OutboxEvent> stuck = outboxRepository.findDue(
                OutboxStatus.FAILED, now, PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent ev : stuck) {
            ev.requeue();
            outboxRepository.save(ev);
            log.warn("re-draining FAILED outbox back to PENDING outboxId={} topic={}",
                    ev.getId(), ev.getTopic());
        }
    }

    private static Instant nextBackoff(int attempts) {
        long delay = Math.min(MAX_BACKOFF_MS,
                (long) (BASE_BACKOFF_MS * Math.pow(2, attempts - 1)));
        return Instant.now().plusMillis(delay);
    }
}
