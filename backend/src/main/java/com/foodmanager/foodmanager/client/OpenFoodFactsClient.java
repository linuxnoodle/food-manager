package com.foodmanager.foodmanager.client;

import com.foodmanager.foodmanager.dto.off.OffTaxonomySuggestionsResponse;
import com.foodmanager.foodmanager.dto.off.OffV2SearchResponse;
import com.foodmanager.foodmanager.dto.off.OffV3Product;
import com.foodmanager.foodmanager.dto.off.OffV3ProductResponse;
import com.foodmanager.foodmanager.exception.UpstreamException;
import com.foodmanager.foodmanager.exception.UpstreamRateLimitedException;
import com.foodmanager.foodmanager.exception.UpstreamTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Single point of contact with the OFF HTTP API. All network exceptions are
 * translated into our own {@code Upstream*Exception} types so the rest of the
 * codebase never sees Spring's HTTP client types.
 * <p>
 * Three operations:
 * <ul>
 *   <li>{@link #search} — v2 structured search (v3 has no search endpoint).</li>
 *   <li>{@link #fetchProduct} — v3 product read (no conditional headers).</li>
 *   <li>{@link #fetchProductConditional} — v3 product read with If-None-Match /
 *       If-Modified-Since, so we can short-circuit on 304.</li>
 *   <li>{@link #suggestIngredients} — v3 taxonomy autocomplete.</li>
 * </ul>
 */
@Component
@Slf4j
public class OpenFoodFactsClient {

    private final RestClient offRestClient;

    /** fields= pinned so we never get surprised by a new OFF field name. */
    public static final String V2_SEARCH_FIELDS =
            "code,product_name,brands,image_url,image_front_small_url," +
            "ingredients_tags,allergens_tags,nutriments,nova_group,nutriscore_grade";

    public static final String V3_PRODUCT_FIELDS =
            "code,product_name,brands,quantity,image_url,ingredients_text," +
            "ingredients_tags,allergens_tags,additives_tags,nova_group," +
            "nutriscore_grade,nutriments";

    // explicit constructor so the @Qualifier-named bean is wired unambiguously
    public OpenFoodFactsClient(@Qualifier("offRestClient") RestClient offRestClient) {
        this.offRestClient = offRestClient;
    }

    // --- v2 search ---

    public OffV2SearchResponse search(List<String> includeTags, List<String> excludeTags, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/api/v2/search")
                .queryParam("fields", V2_SEARCH_FIELDS)
                .queryParam("page", page)
                .queryParam("page_size", size);

        // OFF's tag filter syntax: tagtype_N / tag_contains_N / tag_N, indices must be contiguous from 0
        int idx = 0;
        for (String tag : includeTags) {
            b.queryParam("tagtype_" + idx, "ingredients");
            b.queryParam("tag_contains_" + idx, "contains");
            b.queryParam("tag_" + idx, tag);
            idx++;
        }
        for (String tag : excludeTags) {
            b.queryParam("tagtype_" + idx, "ingredients");
            b.queryParam("tag_contains_" + idx, "does_not_contain");
            b.queryParam("tag_" + idx, tag);
            idx++;
        }

        String uri = b.build().toUriString();
        try {
            ResponseEntity<OffV2SearchResponse> resp = offRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new UpstreamException("OFF search returned " + res.getStatusCode());
                    })
                    .onStatus(s -> s.value() == 429, (req, res) -> {
                        throw new UpstreamRateLimitedException("OFF rate-limited search");
                    })
                    .toEntity(OffV2SearchResponse.class);
            return resp.getBody() == null ? new OffV2SearchResponse(0L, page, size, List.of()) : resp.getBody();
        } catch (ResourceAccessException e) {
            throw new UpstreamTimeoutException("OFF search unreachable: " + e.getMessage(), e);
        } catch (UpstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            // HttpClientErrorException etc. — wrap to keep client types out of callers
            throw new UpstreamException("OFF search failed: " + e.getMessage(), e);
        }
    }

    // --- v3 product read (full) ---

    public FetchResult fetchProduct(String code) {
        try {
            ResponseEntity<OffV3ProductResponse> resp = offRestClient.get()
                    .uri(uri -> uri.path("/api/v3/product/{code}.json")
                            .queryParam("fields", V3_PRODUCT_FIELDS)
                            .build(java.util.Map.of("code", code)))
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new UpstreamException("OFF fetch returned " + res.getStatusCode());
                    })
                    .onStatus(s -> s.value() == 429, (req, res) -> {
                        throw new UpstreamRateLimitedException("OFF rate-limited fetch");
                    })
                    .toEntity(OffV3ProductResponse.class);

            return parseFetch(resp.getStatusCode().value(), resp.getBody(), resp.getHeaders());
        } catch (ResourceAccessException e) {
            throw new UpstreamTimeoutException("OFF fetch unreachable: " + e.getMessage(), e);
        } catch (UpstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e instanceof HttpClientErrorException.NotFound) return new FetchResult.Miss();
            throw new UpstreamException("OFF fetch failed: " + e.getMessage(), e);
        }
    }

    // --- v3 product read (conditional) ---

    public ConditionalFetchResult fetchProductConditional(String code, String etag, String lastModified) {
        try {
            ResponseEntity<OffV3ProductResponse> resp = offRestClient.get()
                    .uri(uri -> uri.path("/api/v3/product/{code}.json")
                            .queryParam("fields", V3_PRODUCT_FIELDS)
                            .build(java.util.Map.of("code", code)))
                    .header("If-None-Match", etag)
                    .header("If-Modified-Since", lastModified)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new UpstreamException("OFF conditional fetch returned " + res.getStatusCode());
                    })
                    .onStatus(s -> s.value() == 429, (req, res) -> {
                        throw new UpstreamRateLimitedException("OFF rate-limited fetch");
                    })
                    .toEntity(OffV3ProductResponse.class);

            int status = resp.getStatusCode().value();
            if (status == 304) return new ConditionalFetchResult.NotModified();
            return parseConditional(status, resp.getBody(), resp.getHeaders());
        } catch (ResourceAccessException e) {
            throw new UpstreamTimeoutException("OFF conditional fetch unreachable: " + e.getMessage(), e);
        } catch (UpstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e instanceof HttpClientErrorException.NotFound) return new ConditionalFetchResult.Miss();
            throw new UpstreamException("OFF conditional fetch failed: " + e.getMessage(), e);
        }
    }

    // --- v3 taxonomy suggestions ---

    public List<String> suggestIngredients(String q) {
        try {
            ResponseEntity<OffTaxonomySuggestionsResponse> resp = offRestClient.get()
                    .uri(uri -> uri.path("/api/v3/taxonomy_suggestions")
                            .queryParam("tagtype", "ingredients")
                            .queryParam("string", q)
                            .queryParam("lc", "en")
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new UpstreamException("OFF taxonomy returned " + res.getStatusCode());
                    })
                    .onStatus(s -> s.value() == 429, (req, res) -> {
                        throw new UpstreamRateLimitedException("OFF rate-limited taxonomy");
                    })
                    .toEntity(OffTaxonomySuggestionsResponse.class);
            OffTaxonomySuggestionsResponse body = resp.getBody();
            return body == null ? List.of() : body.suggestions();
        } catch (ResourceAccessException e) {
            throw new UpstreamTimeoutException("OFF taxonomy unreachable: " + e.getMessage(), e);
        } catch (UpstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UpstreamException("OFF taxonomy failed: " + e.getMessage(), e);
        }
    }

    // --- helpers ---

    private FetchResult parseFetch(int status, OffV3ProductResponse body, HttpHeaders headers) {
        if (status == 404 || body == null || !body.isFound()) {
            return new FetchResult.Miss();
        }
        return new FetchResult.Hit(body.product(), headers.getETag(), headers.getFirst("Last-Modified"));
    }

    private ConditionalFetchResult parseConditional(int status, OffV3ProductResponse body, HttpHeaders headers) {
        if (status == 404 || body == null || !body.isFound()) {
            return new ConditionalFetchResult.Miss();
        }
        return new ConditionalFetchResult.Modified(body.product(), headers.getETag(), headers.getFirst("Last-Modified"));
    }

    // --- result types (sealed so service-layer switches are exhaustive) ---

    public sealed interface FetchResult permits FetchResult.Miss, FetchResult.Hit {
        record Miss() implements FetchResult {}
        record Hit(OffV3Product product, String etag, String lastModified) implements FetchResult {}
    }

    public sealed interface ConditionalFetchResult
            permits ConditionalFetchResult.NotModified, ConditionalFetchResult.Modified, ConditionalFetchResult.Miss {
        record NotModified() implements ConditionalFetchResult {}
        record Modified(OffV3Product product, String etag, String lastModified) implements ConditionalFetchResult {}
        record Miss() implements ConditionalFetchResult {}
    }
}
