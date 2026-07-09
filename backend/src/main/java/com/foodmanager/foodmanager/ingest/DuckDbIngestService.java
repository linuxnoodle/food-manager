package com.foodmanager.foodmanager.ingest;

import com.foodmanager.foodmanager.config.OpenFoodFactsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * Builds/refreshes the duckdb mirror. Source-agnostic: callers hand it the
 * gzipped JSONL product stream (a local file via the ingest profile, or an http
 * download via the selfhost flag) and it does the rest.
 * <p>
 * Full reload, no deltas: uses DuckDB-native read_json_auto (which handles gzip
 * transparently) for a single-query product load, then swaps the staging
 * table in for off_products. This is ~100x faster than row-by-row JDBC batches.
 * The ingredients taxonomy is still parsed from the vendored ingredients.txt.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DuckDbIngestService {

    private final OpenFoodFactsProperties props;

    public record SyncResult(long products, long taxonomyEntries) {}

    /**
     * Quick check: does the duckdb file exist and contain product data?
     * Used by SelfhostSync to skip the initial re-download on restart.
     */
    public boolean isMirrorBuilt() {
        File dbFile = new File(props.duckdbPath());
        if (!dbFile.isFile() || dbFile.length() == 0) return false;
        try (Connection c = openReadOnly()) {
            c.createStatement().executeQuery("SELECT 1 FROM off_products LIMIT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** full reload from an already-downloaded dump file (skips the save-to-temp step). */
    public SyncResult fullSyncFromFile(File dumpFile) {
        ensureParentDir(props.duckdbPath());
        long products = 0, tax = 0;
        try (Connection c = openReadWrite()) {
            c.setAutoCommit(false);
            applySchema(c);
            tax = loadTaxonomy(c);
            products = loadProducts(c, dumpFile);
            c.commit();
        } catch (Exception e) {
            throw new IllegalStateException("duckdb sync failed: " + e.getMessage(), e);
        }
        log.info("sync done: {} products, {} taxonomy entries", products, tax);
        return new SyncResult(products, tax);
    }

    /** full reload: products from a gzipped JSONL stream plus the taxonomy. */
    public SyncResult fullSync(InputStream productGzStream) {
        ensureParentDir(props.duckdbPath());
        long products = 0, tax = 0;
        File dumpFile = null;
        try {
            dumpFile = saveToTempFile(productGzStream);
            try (Connection c = openReadWrite()) {
                c.setAutoCommit(false);
                applySchema(c);
                tax = loadTaxonomy(c);
                products = loadProducts(c, dumpFile);
                c.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException("duckdb sync failed: " + e.getMessage(), e);
        } finally {
            // temp file kept for debugging; delete manually when done
        }
        log.info("sync done: {} products, {} taxonomy entries", products, tax);
        return new SyncResult(products, tax);
    }

    /** taxonomy only. */
    public long syncTaxonomyOnly() {
        ensureParentDir(props.duckdbPath());
        try (Connection c = openReadWrite()) {
            c.setAutoCommit(false);
            applySchema(c);
            long tax = loadTaxonomy(c);
            c.commit();
            return tax;
        } catch (Exception e) {
            throw new IllegalStateException("duckdb taxonomy sync failed: " + e.getMessage(), e);
        }
    }

    // --- save the HTTP stream to a temp file so DuckDB's read_json_auto can read it ---

    private File saveToTempFile(InputStream in) throws Exception {
        File tmp = File.createTempFile("off-dump-", ".jsonl.gz");
        long total = 0, lastLogged = 0;
        byte[] buf = new byte[8192];
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
                if (total - lastLogged >= 100_000_000) {
                    log.info("downloaded {} MB so far", total / 1_000_000);
                    lastLogged = total;
                }
            }
        }
        log.info("download complete: {} MB", total / 1_000_000);
        return tmp;
    }

    // --- products: DuckDB-native JSONL ingest (single SQL query, fast) ---

    private long loadProducts(Connection c, File dumpFile) throws Exception {
        long start = System.currentTimeMillis();
        exec(c, "DROP TABLE IF EXISTS off_products_new");

        String path = dumpFile.getAbsolutePath().replace("\\", "/");
        // Use read_json with explicit column types (works reliably on huge files
        // where auto_detect can fail). Array fields come in as JSON and are
        // cast to VARCHAR[] via ::.
        String sql = "CREATE TABLE off_products_new AS SELECT "
            + "code, product_name, brands, quantity, "
            + "COALESCE(image_url, image_front_small_url) AS image_url, "
            + "ingredients_text, "
            + "ingredients_tags::VARCHAR[] AS ingredients_tags, "
            + "allergens_tags::VARCHAR[] AS allergens_tags, "
            + "additives_tags::VARCHAR[] AS additives_tags, "
            + "CAST(nutriments.proteins_100g AS DOUBLE) AS proteins_100g, "
            + "CAST(nutriments.carbohydrates_100g AS DOUBLE) AS carbohydrates_100g, "
            + "CAST(nutriments.sugars_100g AS DOUBLE) AS sugars_100g, "
            + "CAST(nutriments.fat_100g AS DOUBLE) AS fat_100g, "
            + "CAST(nutriments.salt_100g AS DOUBLE) AS salt_100g, "
            + "CAST(nutriments.\"energy-kcal_100g\" AS DOUBLE) AS energy_kcal_100g, "
            + "COALESCE(nutriscore_grade->>'$', nutriscore_data.grade->>'$') AS nutriscore_grade, "
            + "COALESCE(nova_group::INTEGER, CAST(nutriments.\"nova-group\" AS INTEGER)) AS nova_group, "
            + "last_modified_t::BIGINT AS last_modified_t "
            + "FROM read_json('" + path.replace("'", "''") + "', "
            + "columns={code: VARCHAR, product_name: VARCHAR, brands: VARCHAR, quantity: VARCHAR, "
            + "image_url: VARCHAR, image_front_small_url: VARCHAR, ingredients_text: VARCHAR, "
            + "ingredients_tags: JSON, allergens_tags: JSON, additives_tags: JSON, "
            + "nutriments: JSON, nutriscore_grade: JSON, nutriscore_data: JSON, "
            + "nova_group: JSON, last_modified_t: VARCHAR}, format='auto')";

        exec(c, sql);
        long count;
        try (var rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM off_products_new")) {
            rs.next();
            count = rs.getLong(1);
        }
        // swap: drop old, rename new into place
        exec(c, "DROP TABLE IF EXISTS off_products");
        exec(c, "ALTER TABLE off_products_new RENAME TO off_products");
        upsertMeta(c, "last_full_load_products", String.valueOf(System.currentTimeMillis()));
        log.info("loaded {} products in {}s", count, (System.currentTimeMillis() - start) / 1000);
        return count;
    }

    // --- taxonomy: reload from vendored ingredients.txt ---

    private long loadTaxonomy(Connection c) throws Exception {
        List<IngredientsTaxonomyParser.TaxonomyEntry> entries;
        try (var in = new ClassPathResource("taxonomies/ingredients.txt").getInputStream()) {
            entries = IngredientsTaxonomyParser.parse(in);
        }
        exec(c, "DELETE FROM off_taxonomy_ingredient");
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO off_taxonomy_ingredient (tag, name, synonyms) VALUES (?,?,?) "
                        + "ON CONFLICT (tag) DO NOTHING")) {
            int inBatch = 0;
            for (IngredientsTaxonomyParser.TaxonomyEntry e : entries) {
                ps.setString(1, e.tag());
                ps.setString(2, e.name());
                ps.setArray(3, c.createArrayOf("VARCHAR", e.synonyms().toArray(String[]::new)));
                ps.addBatch();
                if (++inBatch % 5000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        upsertMeta(c, "last_full_load_taxonomy", String.valueOf(System.currentTimeMillis()));
        log.info("loaded {} ingredient taxonomy entries", entries.size());
        return entries.size();
    }

    // --- helpers ---

    private Connection openReadWrite() throws Exception {
        return DriverManager.getConnection("jdbc:duckdb:" + props.duckdbPath());
    }

    private Connection openReadOnly() throws Exception {
        Properties info = new Properties();
        info.setProperty("access_mode", "read_only");
        return DriverManager.getConnection("jdbc:duckdb:" + props.duckdbPath(), info);
    }

    private void applySchema(Connection c) throws Exception {
        String sql;
        try (var in = new ClassPathResource("db/duckdb/schema.sql").getInputStream()) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                if (!stmt.isBlank()) st.execute(stmt);
            }
        }
    }

    private static void exec(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement()) { st.execute(sql); }
    }

    private void upsertMeta(Connection c, String key, String value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO off_meta VALUES (?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static void ensureParentDir(String dbPath) {
        java.io.File f = new java.io.File(dbPath);
        java.io.File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("could not create dir {}", parent);
        }
    }
}