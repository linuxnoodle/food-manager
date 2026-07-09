package com.foodmanager.foodmanager.client;

import com.foodmanager.foodmanager.dto.MacroFilter;
import com.foodmanager.foodmanager.dto.off.OffTaxonomySuggestionsResponse;
import com.foodmanager.foodmanager.dto.off.OffV2SearchResponse;
import com.foodmanager.foodmanager.dto.off.OffV3ProductResponse;
import com.foodmanager.foodmanager.exception.UpstreamException;
import com.foodmanager.foodmanager.exception.UpstreamRateLimitedException;
import com.foodmanager.foodmanager.exception.UpstreamTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The http impl -- calls OFF over the wire. Only active when app.off.mode=remote
 * (or unset), which keeps it as the default so nothing changes until you've built
 * the duckdb mirror and flipped the switch to "local".
 * <p>
 * All network exceptions are translated into our own Upstream*Exception types so
 * the rest of the codebase never sees Spring's http client types.
 */
@Component
@Slf4j
@ConditionalOnExpression("'${app.off.selfhost:false}' != 'true' and '${app.off.mode:remote}' != 'local'")
public class HttpOpenFoodFactsClient implements OpenFoodFactsClient {

    private final RestClient offRestClient;

    /** fields= pinned so we never get surprised by a new OFF field name. */
    public static final String V2_SEARCH_FIELDS =
            "code,product_name,brands,image_url,image_front_small_url," +
            "ingredients_tags,allergens_tags,nutriments,nova_group,nutriscore_grade";

    public static final String V3_PRODUCT_FIELDS =
            "code,product_name,brands,quantity,image_url,ingredients_text," +
            "ingredients_tags,allergens_tags,additives_tags,nova_group," +
            "nutriscore_grade,nutriments";

    // url-encoded comparison operators for the nutrient filter param names (>, <)
    private static final String GT = "%3E";
    private static final String LT = "%3C";

    // explicit constructor so the @Qualifier-named bean is wired unambiguously
    public HttpOpenFoodFactsClient(@Qualifier("offRestClient") RestClient offRestClient) {
        this.offRestClient = offRestClient;
    }

    // --- v2 search ---

    @Override
    public OffV2SearchResponse search(List<String> includeTags, List<String> excludeTags, MacroFilter macros, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/api/v2/search")
                .queryParam("fields", V2_SEARCH_FIELDS)
                .queryParam("page", page)
                .queryParam("page_size", size);

        // v2 filters by field-name params, not the legacy tagtype_N triplets (those
        // are /cgi/search.pl v1 syntax, which v2 silently ignores and returns the
        // whole db). one ingredients_tags value: comma = AND, "-" after the language
        // prefix = NOT. so [en:chicken] minus [en:gluten] -> "en:chicken,en:-gluten".
        List<String> terms = new ArrayList<>(includeTags);
        for (String tag : excludeTags) {
            int colon = tag.indexOf(':');
            terms.add(colon >= 0 ? tag.substring(0, colon + 1) + "-" + tag.substring(colon + 1) : "-" + tag);
        }
        if (!terms.isEmpty()) {
            b.queryParam("ingredients_tags", String.join(",", terms));
        }

        // nutrient filters: operator + value go in the param NAME (proteins_100g>20).
        // we pre-encode "<"/">" as %3C/%3E, and below build with encoded=true and
        // pass a URI object — RestClient's .uri(String) would double-encode the "%"
        // and OFF would then see a literal "%3E" in the name and silently drop the
        // filter. the value is discarded per the OFF spec, so none is passed. these
        // only apply when a tag filter anchors them.
        if (macros != null) {
            if (macros.minProtein() != null) b.queryParam("proteins_100g" + GT + macros.minProtein());
            if (macros.maxCarbs() != null)   b.queryParam("carbohydrates_100g" + LT + macros.maxCarbs());
            if (macros.maxSugar() != null)   b.queryParam("sugars_100g" + LT + macros.maxSugar());
            if (macros.maxFat() != null)     b.queryParam("fat_100g" + LT + macros.maxFat());
            if (macros.maxSalt() != null)    b.queryParam("salt_100g" + LT + macros.maxSalt());
        }

        // encoded=true: our %3C/%3E are already encoded so don't re-encode them, and
        // leave the raw ":" in ingredients_tags alone (OFF accepts it). pass a URI,
        // not a String, so RestClient doesn't run its own encoding pass over it.
        URI uri = b.build(true).toUri();
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

    @Override
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

    @Override
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

    @Override
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
}
