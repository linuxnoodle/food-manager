package com.foodmanager.foodmanager.client;

import com.foodmanager.foodmanager.config.OpenFoodFactsProperties;
import com.foodmanager.foodmanager.dto.MacroFilter;
import com.foodmanager.foodmanager.dto.off.OffNutriments;
import com.foodmanager.foodmanager.dto.off.OffV2Product;
import com.foodmanager.foodmanager.dto.off.OffV2SearchResponse;
import com.foodmanager.foodmanager.dto.off.OffV3Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The local impl -- reads our self-hosted duckdb mirror instead of calling OFF.
 * Active only when app.off.mode=local. Same return shapes as the http client, so
 * the services above it don't notice the swap.
 * <p>
 * Each method opens a short read-only connection. that's deliberate: a fresh
 * connection per call means the nightly ingest (which takes the file read-write)
 * isn't held off by a long-lived reader. duckdb connection open is cheap, no
 * network involved.
 */
@Component
@Slf4j
@ConditionalOnExpression("'${app.off.selfhost:false}' == 'true' or '${app.off.mode:remote}' == 'local'")
@RequiredArgsConstructor
public class DuckDbOpenFoodFactsClient implements OpenFoodFactsClient {

    private final OpenFoodFactsProperties props;

    // --- search ---

    @Override
    public OffV2SearchResponse search(List<String> includeTags, List<String> excludeTags,
                                      MacroFilter macros, int page, int size) {
        ArrayList<String> where = new ArrayList<>();
        ArrayList<Object> params = new ArrayList<>();
        if (includeTags != null && !includeTags.isEmpty()) {
            where.add("list_has_all(ingredients_tags, ?)");
            params.add(includeTags);
        }
        if (excludeTags != null && !excludeTags.isEmpty()) {
            where.add("NOT list_has_any(ingredients_tags, ?)");
            params.add(excludeTags);
        }
        if (macros != null) {
            if (macros.minProtein() != null) { where.add("proteins_100g > ?");      params.add(macros.minProtein()); }
            if (macros.maxCarbs() != null)   { where.add("carbohydrates_100g < ?"); params.add(macros.maxCarbs()); }
            if (macros.maxSugar() != null)   { where.add("sugars_100g < ?");        params.add(macros.maxSugar()); }
            if (macros.maxFat() != null)     { where.add("fat_100g < ?");           params.add(macros.maxFat()); }
            if (macros.maxSalt() != null)    { where.add("salt_100g < ?");          params.add(macros.maxSalt()); }
        }
        String clause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        int offset = Math.max(0, (page - 1) * size);

        String dataSql = "SELECT code, product_name, brands, image_url, ingredients_tags, allergens_tags, "
                + "nutriscore_grade, nova_group, energy_kcal_100g, proteins_100g, fat_100g, "
                + "carbohydrates_100g, sugars_100g, salt_100g FROM off_products"
                + clause + " ORDER BY code LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) FROM off_products" + clause;

        try (Connection c = openReadOnly()) {
            long total = 0;
            try (PreparedStatement ps = c.prepareStatement(countSql)) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total = rs.getLong(1);
                }
            }
            List<OffV2Product> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(dataSql)) {
                bindParams(ps, params);
                ps.setInt(params.size() + 1, size);
                ps.setInt(params.size() + 2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) items.add(toV2Product(rs));
                }
            }
            return new OffV2SearchResponse(total, page, size, items);
        } catch (SQLException e) {
            throw new IllegalStateException("duckdb search failed: " + e.getMessage(), e);
        }
    }

    // --- product detail ---

    @Override
    public FetchResult fetchProduct(String code) {
        try (Connection c = openReadOnly();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT code, product_name, brands, quantity, image_url, ingredients_text, "
                             + "ingredients_tags, allergens_tags, additives_tags, nova_group, "
                             + "nutriscore_grade, energy_kcal_100g, proteins_100g, fat_100g, "
                             + "carbohydrates_100g, sugars_100g, salt_100g, last_modified_t "
                             + "FROM off_products WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new FetchResult.Miss();
                return new FetchResult.Hit(toV3Product(rs), null, null);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("duckdb fetch failed: " + e.getMessage(), e);
        }
    }

    // locally the mirror IS the source of truth, so there's no "revalidate against
    // upstream" -- treat a conditional read as always-modified (or miss)
    @Override
    public ConditionalFetchResult fetchProductConditional(String code, String etag, String lastModified) {
        FetchResult r = fetchProduct(code);
        if (r instanceof FetchResult.Miss) return new ConditionalFetchResult.Miss();
        FetchResult.Hit h = (FetchResult.Hit) r;
        return new ConditionalFetchResult.Modified(h.product(), null, null);
    }

    // --- autocomplete ---

    @Override
    public List<String> suggestIngredients(String q) {
        if (q == null || q.isBlank()) return List.of();
        String pattern = q.trim().toLowerCase() + "%";
        String sql = "SELECT name FROM off_taxonomy_ingredient "
                + "WHERE name ILIKE ? "
                + "OR len(list_filter(coalesce(synonyms, []), s -> s ILIKE ?)) > 0 "
                + "ORDER BY name LIMIT 20";
        try (Connection c = openReadOnly();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("duckdb taxonomy query failed: " + e.getMessage(), e);
        }
    }

    // --- internals ---

    private Connection openReadOnly() throws SQLException {
        // read-only so the ingest writer can grab the file exclusively between calls.
        // note: duckdb ignores URL "?access_mode=..." (treats it as part of the
        // filename), so the config has to go through Properties.
        java.util.Properties p = new java.util.Properties();
        p.setProperty("access_mode", "read_only");
        return DriverManager.getConnection("jdbc:duckdb:" + props.duckdbPath(), p);
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof List<?> list) {
                String[] arr = list.stream().filter(Objects::nonNull).map(String::valueOf).toArray(String[]::new);
                ps.setArray(i + 1, ps.getConnection().createArrayOf("VARCHAR", arr));
            } else {
                ps.setObject(i + 1, p);
            }
        }
    }

    private OffV2Product toV2Product(ResultSet rs) throws SQLException {
        return new OffV2Product(
                rs.getString("code"),
                rs.getString("product_name"),
                rs.getString("brands"),
                rs.getString("image_url"),
                null,
                toStringList(rs.getArray("ingredients_tags")),
                toStringList(rs.getArray("allergens_tags")),
                nutriments(rs),
                (Integer) rs.getObject("nova_group"),
                rs.getString("nutriscore_grade")
        );
    }

    private OffV3Product toV3Product(ResultSet rs) throws SQLException {
        return new OffV3Product(
                rs.getString("code"),
                rs.getString("product_name"),
                rs.getString("brands"),
                rs.getString("quantity"),
                rs.getString("image_url"),
                rs.getString("ingredients_text"),
                toStringList(rs.getArray("ingredients_tags")),
                toStringList(rs.getArray("allergens_tags")),
                toStringList(rs.getArray("additives_tags")),
                (Integer) rs.getObject("nova_group"),
                rs.getString("nutriscore_grade"),
                nutriments(rs),
                (Long) rs.getObject("last_modified_t")
        );
    }

    private OffNutriments nutriments(ResultSet rs) throws SQLException {
        return new OffNutriments(
                rs.getObject("energy_kcal_100g", Double.class),
                rs.getObject("proteins_100g", Double.class),
                rs.getObject("fat_100g", Double.class),
                rs.getObject("carbohydrates_100g", Double.class),
                rs.getObject("sugars_100g", Double.class),
                rs.getObject("salt_100g", Double.class)
        );
    }

    private static List<String> toStringList(Array arr) throws SQLException {
        if (arr == null) return List.of();
        Object o = arr.getArray();
        if (o instanceof Object[] a) {
            return Arrays.stream(a).filter(Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of();
    }
}
