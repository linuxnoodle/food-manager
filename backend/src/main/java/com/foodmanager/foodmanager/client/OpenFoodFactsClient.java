package com.foodmanager.foodmanager.client;

import com.foodmanager.foodmanager.dto.MacroFilter;
import com.foodmanager.foodmanager.dto.off.OffV2SearchResponse;
import com.foodmanager.foodmanager.dto.off.OffV3Product;

import java.util.List;

/**
 * Where food data comes from. Two impls, picked by app.off.mode: the http one
 * calls OFF over the wire (rate-limited), the duckdb one reads our self-hosted
 * mirror. FoodSearchService / FoodService / IngredientSuggestService only ever
 * hold this interface, so they don't care which is live.
 */
public interface OpenFoodFactsClient {

    OffV2SearchResponse search(List<String> includeTags, List<String> excludeTags,
                               MacroFilter macros, int page, int size);

    FetchResult fetchProduct(String code);

    ConditionalFetchResult fetchProductConditional(String code, String etag, String lastModified);

    List<String> suggestIngredients(String q);

    // result of a product read. Miss = OFF (or the mirror) has no row for the code.
    sealed interface FetchResult permits FetchResult.Miss, FetchResult.Hit {
        record Miss() implements FetchResult {}
        record Hit(OffV3Product product, String etag, String lastModified) implements FetchResult {}
    }

    // result of a conditional product read (If-None-Match / If-Modified-Since).
    sealed interface ConditionalFetchResult
            permits ConditionalFetchResult.NotModified, ConditionalFetchResult.Modified, ConditionalFetchResult.Miss {
        record NotModified() implements ConditionalFetchResult {}
        record Modified(OffV3Product product, String etag, String lastModified) implements ConditionalFetchResult {}
        record Miss() implements ConditionalFetchResult {}
    }
}
