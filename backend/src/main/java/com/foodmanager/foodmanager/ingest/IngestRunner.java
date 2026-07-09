package com.foodmanager.foodmanager.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;

/**
 * Manual ingest path: run with the "ingest" profile and point app.off.dump-path
 * at a locally-downloaded openfoodfacts-products.jsonl.gz. the heavy lifting is
 * in {@link DuckDbIngestService}; this just opens the file and hands it over.
 * <p>
 * For the auto-download path (no local file needed), use app.off.selfhost=true
 * instead, handled by {@link SelfhostSync}.
 */
@Component
@Profile("ingest")
@Slf4j
@RequiredArgsConstructor
public class IngestRunner implements CommandLineRunner {

    private final DuckDbIngestService ingest;

    @Value("${app.off.dump-path:}")
    private String dumpPath;

    @Override
    public void run(String... args) throws Exception {
        if (dumpPath == null || dumpPath.isBlank()) {
            log.warn("app.off.dump-path not set -- refreshing taxonomy only, no products loaded");
            ingest.syncTaxonomyOnly();
            return;
        }
        try (FileInputStream in = new FileInputStream(dumpPath)) {
            ingest.fullSync(in);
        }
    }
}
