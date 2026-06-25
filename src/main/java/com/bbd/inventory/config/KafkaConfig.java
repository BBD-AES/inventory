package com.bbd.inventory.config;

import com.bbd.inventory.global.error.TransientServiceException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.core.JacksonException;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;


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

        need a TaskScheduler bean (e.g. ThreadPoolTaskScheduler)
     */
    @Bean
    public DefaultErrorHandler errorHandler(
            KafkaTemplate<String, String> template,
            KafkaListenerEndpointRegistry registry,
            TaskScheduler taskScheduler) {

        var recoverer = new DeadLetterPublishingRecoverer(
                template,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", 0));

        // backoff: generous, capped - covers multi-min outrage, eventually DLTs a dead dep
        var backOff = new ExponentialBackOffWithMaxRetries(15);
        backOff.setInitialInterval(5_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(300_000L);                       // 5 min cap.

        var pauseService = new ListenerContainerPauseService(registry, taskScheduler);
        var backOffHandler = new ContainerPausingBackOffHandler(pauseService);

        var handler = new DefaultErrorHandler(recoverer, backOff, backOffHandler);


        // P1: default -> DLT now; only transient retries
        handler.setClassifications(Map.of(TransientServiceException.class, true), false);

        return handler;
    }

    @Bean
    public KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("procurement.stock-in-requested.DLT")
                        .partitions(1)
                        .build(),

                TopicBuilder.name("sales.order.received.DLT")
                        .partitions(1)
                        .build(),

                TopicBuilder.name("item.changed.DLT")
                        .partitions(1)
                        .build()
        );
    }
}
