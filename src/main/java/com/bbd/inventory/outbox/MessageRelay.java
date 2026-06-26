package com.bbd.inventory.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private static final int MAX_ATTEMPTS = 10;

    @Scheduled(fixedDelay = 1000)       // poll every 1s after prev run finishes.
    public void relay() {
        var batch =
                outboxRepository.findTop100ByOutboxStatusOrderByIdAsc(OutboxStatus.PENDING);
        for (OutboxEvent ev : batch) {
            try {
                kafkaTemplate.send(ev.getTopic(), ev.getMsgKey(), ev.getPayload())
                        .get(5, TimeUnit.SECONDS);
                ev.markSent();
            } catch (Exception ex) {
                ev.bumpAttempt();
                if (ev.getAttempts() >= MAX_ATTEMPTS) ev.markFailed();

                log.warn("relay failed outboxId={} topic={} attempts={}",
                        ev.getId(), ev.getTopic(), ev.getAttempts(), ex);
            }

            // The scheduled thread has no open session/tx (OSIV covers web threads only),
            // so the entity is detached and dirty-checking won't flush. Persist explicitly,
            // else SENT never sticks and every PENDING row is re-sent every poll (dup storm).
            outboxRepository.save(ev);
        }

    }

}
