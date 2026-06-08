package com.bbd.inventory.stock.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/*
    Building one configured RestClient pointed at the Items service. Answers
    "which host is Items" question. Externalized to config so only YAML changes
    between dev/prod.
 */

@Configuration
public class ItemClientConfig {

    /*
        base-url setting is configured in YAML under items:
     */
    @Bean
    RestClient itemRestClient(@Value("${items.base-url}") String baseUrl) {
        // timeouts so a slow/dead Items can't hang a stock write
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
