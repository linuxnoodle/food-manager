package com.foodmanager.foodmanager.config;

import com.foodmanager.foodmanager.dto.IngredientSuggestion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Wires up everything OFF-related: the {@link RestClient} used by the client, the
 * {@link OpenFoodFactsProperties} bound from app.off.*, and the Caffeine cache used
 * for ingredient taxonomy autocomplete (in-memory is correct here — taxonomy is not
 * user data, loss on restart is fine).
 */
@Configuration
@EnableConfigurationProperties(OpenFoodFactsProperties.class)
@EnableScheduling
public class OpenFoodFactsConfig {

    /**
     * Single RestClient configured with the OFF base URL, mandatory User-Agent, and timeouts.
     * Named explicitly so future RestClient beans (e.g. for other integrations) don't conflict.
     */
    @Bean(name = "offRestClient")
    public RestClient offRestClient(OpenFoodFactsProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeout().toMillis());
        factory.setReadTimeout((int) props.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("User-Agent", props.userAgent())
                .defaultHeader("Accept", "application/json")
                .requestFactory(factory)
                .build();
    }

    /**
     * Tier 3 cache. Bounded size since the query space is small (autocomplete strings).
     * TTL comes from props; entries expire as a whole.
     */
    @Bean
    public Cache<String, List<IngredientSuggestion>> ingredientSuggestionCache(OpenFoodFactsProperties props) {
        return Caffeine.newBuilder()
                .expireAfterWrite(props.taxonomyCacheTtl())
                .maximumSize(10_000)
                .build();
    }
}
