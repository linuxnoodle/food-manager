package com.foodmanager.foodmanager.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads OFF's ingredients taxonomy .txt (taxonomies/food/ingredients.txt) into
 * (tag, name, synonyms) rows. The file is one DAG entry per blank-line-separated
 * block: the "en: ..." line is the canonical english name (plus any english
 * synonyms), other "xx:" lines are translations we drop (english-only), "<"
 * lines are parents, "#" are comments, and the global stopwords/synonyms headers
 * we just skip.
 * <p>
 * The tag is "en:" + dasherized name -- deliberately the same normalization as
 * IngredientSuggestService.toTag, so autocomplete tags line up exactly with the
 * ingredients_tags values on products.
 */
public final class IngredientsTaxonomyParser {

    public record TaxonomyEntry(String tag, String name, List<String> synonyms) {}

    // matches "en: chicken meatball" -- 2-letter lang, colon, space, rest is the name(s)
    private static final Pattern NAME_LINE = Pattern.compile("^([a-z]{2}): (.*)$");

    private IngredientsTaxonomyParser() {}

    public static List<TaxonomyEntry> parse(InputStream in) {
        List<TaxonomyEntry> out = new ArrayList<>();
        List<String> englishNames = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) continue;        // comment
                if (line.isBlank()) {                       // blank = end of entry
                    flush(out, englishNames);
                    englishNames = null;
                    continue;
                }
                if (line.startsWith("<")) continue;         // parent reference
                var m = NAME_LINE.matcher(line);
                if (!m.matches()) continue;                 // property line (wikidata:en: .. etc.)
                if (!"en".equals(m.group(1))) continue;     // translation -- english-only, so skip
                List<String> cleaned = splitNames(m.group(2));
                if (cleaned.isEmpty()) continue;
                englishNames = cleaned;
            }
            flush(out, englishNames);
        } catch (IOException e) {
            throw new IllegalStateException("could not read ingredients taxonomy: " + e.getMessage(), e);
        }
        return out;
    }

    private static void flush(List<TaxonomyEntry> out, List<String> englishNames) {
        if (englishNames == null || englishNames.isEmpty()) return;  // entry has no english name, drop it
        String name = englishNames.get(0);
        List<String> syns = new ArrayList<>(englishNames.size() - 1);
        for (int i = 1; i < englishNames.size(); i++) syns.add(englishNames.get(i));
        out.add(new TaxonomyEntry("en:" + dasherize(name), name, syns));
    }

    private static List<String> splitNames(String csv) {
        List<String> out = new ArrayList<>();
        for (String n : csv.split(",")) {
            String t = n.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // keep in sync with IngredientSuggestService.toTag
    static String dasherize(String displayName) {
        return displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }
}
