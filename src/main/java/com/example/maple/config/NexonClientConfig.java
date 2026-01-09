package com.example.maple.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@Configuration

public class NexonClientConfig {
    @Bean
    public RestTemplate nexonRestTemplate(
            RestTemplateBuilder builder,
            @Value("${nexon.api.key}") String apiKey
    ) {
        return builder
                .defaultHeader("x-nxopen-api-key", apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}