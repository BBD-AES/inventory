package com.bbd.inventory.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(fixedDelay = 1000)       // poll every 1s after prev run finishes.
    public void relay() {
        var batch =
                outboxRepository.findTop100ByOutboxStatusOrderByIdAsc(OutboxStatus.PENDING);
        for (OutboxEvent ev : batch) {
            try {
                /*
                    .get() makes the asynchronous .send relay block until kafka
                    acknolwedges write.
                 */
                kafkaTemplate.send(ev.getTopic(), ev.getMsgKey(),
                        ev.getPayload()).get();
                ev.markSent();
                outboxRepository.save(ev);      // flip PENDING -> SENT after broker ack
            } catch (Exception ex) {
                log.warn("relay failed, outboxId={} topic={}, retry next tick",
                        ev.getId(), ev.getTopic(), ex);
            } // leave PENDING -> picked up next tick.
        }

    }

}
