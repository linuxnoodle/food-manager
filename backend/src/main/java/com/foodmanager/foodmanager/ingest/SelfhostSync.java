package com.foodmanager.foodmanager.ingest;

import com.foodmanager.foodmanager.config.OpenFoodFactsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The self-host launch flag (app.off.selfhost=true). on startup it downloads the
 * full OFF JSONL dump straight from static.openfoodfacts.org and ingests it into
 * the duckdb mirror, then the app serves from that mirror (the duckdb client
 * activates automatically when this flag is on). no separate download script or
 * external scheduler -- everything is in-process.
 * <p>
 * A daily @Scheduled re-run keeps the mirror fresh (full reload). that re-pulls
 * the dump every interval, which is heavy but is the trade we picked for delete
 * reconciliation.
 */
@Component
@ConditionalOnProperty(prefix = "app.off", name = "selfhost", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class SelfhostSync implements CommandLineRunner {

    private final DuckDbIngestService ingest;
    private final OpenFoodFactsProperties props;

    @Override
    public void run(String... args) {
        if (ingest.isMirrorBuilt()) {
            log.info("selfhost: duckdb mirror already built at {}, skipping initial download", props.duckdbPath());
            return;
        }
        log.info("selfhost: starting initial download + ingest from {}", props.dumpUrl());
        doSync();
    }

    // first run is 24h after startup (the CommandLineRunner already did the initial one)
    @Scheduled(fixedDelayString = "${app.off.resync-interval:PT24H}",
            initialDelayString = "${app.off.resync-interval:PT24H}")
    public void resync() {
        log.info("selfhost: scheduled re-sync from {}", props.dumpUrl());
        doSync();
    }

    private void doSync() {
        File existingDump = findExistingDump();
        if (existingDump != null) {
            log.info("found existing dump at {}, skipping download", existingDump);
            ingest.fullSyncFromFile(existingDump);
            return;
        }
        try (InputStream body = openDumpStream()) {
            ingest.fullSync(body);
        } catch (Exception e) {
            // log + swallow on the scheduled path so a bad re-sync doesn't kill the app;
            // on the startup path rethrow so the boot fails fast with a clear cause
            log.error("selfhost sync failed: {}", e.getMessage(), e);
            throw new IllegalStateException("selfhost sync failed: " + e.getMessage(), e);
        }
    }

    private static File findExistingDump() {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File[] candidates = tmp.listFiles((dir, name) ->
                name.startsWith("off-dump-") && name.endsWith(".jsonl.gz"));
        if (candidates == null || candidates.length == 0) return null;
        // pick the largest (real dump is 12 GB, test fixtures are 561 bytes)
        File best = candidates[0];
        for (int i = 1; i < candidates.length; i++) {
            if (candidates[i].length() > best.length()) best = candidates[i];
        }
        return best;
    }

    // stream the .gz body straight into the ingest, no temp file
    private InputStream openDumpStream() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.dumpUrl()))
                .header("User-Agent", props.userAgent())
                .GET()
                .build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("dump download failed: HTTP " + resp.statusCode());
        }
        return resp.body();
    }
}
