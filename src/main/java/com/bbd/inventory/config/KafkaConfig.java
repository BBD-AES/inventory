package com.bbd.inventory.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;


@Configuration
public class KafkaConfig {


    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    /*
        Dead Letter Topic (DLT) :   Kafka topic where messages are sent when they cannot
                                    be processed successfully after a configured num of tries.

        w/o DLT; read message -> exception occur -> kafka redeliver -> exception occur again
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        return new DefaultErrorHandler(
                // failed msg -> <topic>.DLT
//                new DeadLetterPublishingRecoverer(template),
                // retry 2x @ 1s
                new FixedBackOff(1000L, 2));
    }
}
