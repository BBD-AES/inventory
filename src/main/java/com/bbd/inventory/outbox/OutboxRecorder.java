package com.bbd.inventory.outbox;



import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;


@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    // no transactional, joins the caller's transaction
    public void record(String topic, String key, String eventId, Object payload) {

        String json = objectMapper.writeValueAsString(payload);
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .msgKey(key)
                .payload(json)
                .build());
    }
}
