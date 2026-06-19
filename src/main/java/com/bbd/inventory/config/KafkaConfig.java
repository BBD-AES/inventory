package com.bbd.inventory.config;

import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import tools.jackson.core.JacksonException;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;


@Configuration
public class KafkaConfig {


    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
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

        handler.addNotRetryableExceptions(JacksonException.class);

        return handler;
    }

    @Bean
    public KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("sales.order.received.DLT")
                        .partitions(1)
                        .build(),

                TopicBuilder.name("item.changed.DLT")
                        .partitions(1)
                        .build()
        );
    }
}
