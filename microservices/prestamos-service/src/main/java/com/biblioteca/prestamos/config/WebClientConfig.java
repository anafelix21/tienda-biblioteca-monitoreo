package com.biblioteca.prestamos.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient librosWebClient(@Value("${libros.url}") String url,
                                     @Value("${libros.timeout-ms}") long timeoutMs) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMs)
                .responseTimeout(Duration.ofMillis(timeoutMs));

        return WebClient.builder()
                .baseUrl(url)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}
