package com.bbd.inventory.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.TopicPartition;
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

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", 0));

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer,
                // retry 2x @ 1s
                new FixedBackOff(1000L, 2));

        handler.addNotRetryableExceptions(JsonProcessingException.class);

        return handler;
    }
}
