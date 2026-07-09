package com.foodmanager.foodmanager.ingest;

import com.foodmanager.foodmanager.config.OpenFoodFactsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class DuckDbIngestServiceTest {

    private File dbFile;
    private DuckDbIngestService service;

    @BeforeEach
    void setUp() {
        dbFile = new File("target/test-off-" + System.nanoTime() + ".duckdb");
        dbFile.deleteOnExit();
        OpenFoodFactsProperties props = new OpenFoodFactsProperties(
                "https://world.openfoodfacts.org",
                "FoodManager/0.1 (test)",
                java.time.Duration.ofDays(30),
                java.time.Duration.ofHours(1),
                java.time.Duration.ofHours(24),
                java.time.Duration.ofSeconds(3),
                java.time.Duration.ofSeconds(5),
                "local",
                dbFile.getAbsolutePath(),
                false,
                "https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz",
                java.time.Duration.ofHours(24)
        );
        service = new DuckDbIngestService(props);
    }

    @AfterEach
    void tearDown() {
        if (dbFile != null && dbFile.exists()) dbFile.delete();
    }

    @Test
    void nativeIngestPopulatesAllFields() throws Exception {
        File testDump = new File("src/test/resources/fixtures/sample-products.jsonl.gz");
        assertTrue(testDump.isFile(), "test fixture missing");

        DuckDbIngestService.SyncResult result;
        try (FileInputStream in = new FileInputStream(testDump)) {
            result = service.fullSync(in);
        }

        assertEquals(4, result.products());
        assertTrue(result.taxonomyEntries() > 0);

        // verify product 1: full fields, image_url from top-level
        try (Connection c = openReadOnly(); ResultSet rs = c.createStatement()
                .executeQuery("SELECT * FROM off_products WHERE code = '0000000000001'")) {
            assertTrue(rs.next());
            assertEquals("Test Chicken Breast", rs.getString("product_name"));
            assertEquals("Acme", rs.getString("brands"));
            assertEquals("500g", rs.getString("quantity"));
            assertEquals("http://example.com/img.jpg", rs.getString("image_url"));
            assertEquals("chicken breast", rs.getString("ingredients_text"));
            Object[] tags = (Object[]) rs.getArray("ingredients_tags").getArray();
            assertArrayEquals(new String[]{"en:chicken", "en:chicken-breast"},
                    Arrays.copyOf(tags, tags.length, String[].class));
            assertEquals(31.0, rs.getDouble("proteins_100g"), 0.001);
            assertEquals(0.0, rs.getDouble("carbohydrates_100g"), 0.001);
            assertEquals(0.0, rs.getDouble("sugars_100g"), 0.001);
            assertEquals(3.6, rs.getDouble("fat_100g"), 0.001);
            assertEquals(0.1, rs.getDouble("salt_100g"), 0.001);
            assertEquals(165.0, rs.getDouble("energy_kcal_100g"), 0.001);
            assertEquals("a", rs.getString("nutriscore_grade"));
            assertEquals(1, rs.getInt("nova_group"));
            assertEquals(1700000000L, rs.getLong("last_modified_t"));
            assertFalse(rs.next());
        }

        // product 2: image_url via coalesce from image_front_small_url
        try (Connection c = openReadOnly(); ResultSet rs = c.createStatement()
                .executeQuery("SELECT code, image_url, nutriscore_grade, nova_group FROM off_products WHERE code = '0000000000002'")) {
            assertTrue(rs.next());
            assertEquals("http://example.com/choc_small.jpg", rs.getString("image_url"));
            assertEquals("e", rs.getString("nutriscore_grade"));
            assertEquals(4, rs.getInt("nova_group"));
        }

        // product 3: all nulls/missing
        try (Connection c = openReadOnly(); ResultSet rs = c.createStatement()
                .executeQuery("SELECT * FROM off_products WHERE code = '0000000000003'")) {
            assertTrue(rs.next());
            assertNull(rs.getString("product_name"));
            assertNull(rs.getString("brands"));
            assertNull(rs.getString("quantity"));
            assertNull(rs.getString("ingredients_text"));
            assertNull(rs.getArray("ingredients_tags"));
            assertNull(rs.getObject("proteins_100g"));
            assertNull(rs.getObject("carbohydrates_100g"));
            assertNull(rs.getObject("sugars_100g"));
            assertNull(rs.getObject("fat_100g"));
            assertNull(rs.getObject("salt_100g"));
            assertNull(rs.getObject("energy_kcal_100g"));
            assertNull(rs.getString("nutriscore_grade"));
            assertNull(rs.getObject("nova_group"));
            assertNull(rs.getObject("last_modified_t"));
        }

        // product 4: nova_group from nutriments."nova-group", nutriscore from nutriscore_data.grade
        try (Connection c = openReadOnly(); ResultSet rs = c.createStatement()
                .executeQuery("SELECT * FROM off_products WHERE code = '0000000000004'")) {
            assertTrue(rs.next());
            assertNull(rs.getString("image_url"));
            assertEquals("c", rs.getString("nutriscore_grade"));
            assertEquals(3, rs.getInt("nova_group"));
        }
    }

    @Test
    void isMirrorBuiltDetects() throws Exception {
        assertFalse(service.isMirrorBuilt());
        try (FileInputStream in = new FileInputStream("src/test/resources/fixtures/sample-products.jsonl.gz")) {
            service.fullSync(in);
        }
        assertTrue(service.isMirrorBuilt());
    }

    private Connection openReadOnly() throws Exception {
        Properties info = new Properties();
        info.setProperty("access_mode", "read_only");
        return DriverManager.getConnection("jdbc:duckdb:" + dbFile.getAbsolutePath(), info);
    }
}