-- tables for the self-hosted OFF mirror (app.off.mode=local). one flat row per
-- product with only the fields fetchProduct/search read -- autocomplete uses
-- off_taxonomy_ingredient instead. no classic indexes on purpose, duckdb is
-- columnar so scanning this column set is cheap on its own.

CREATE TABLE IF NOT EXISTS off_products (
    code                VARCHAR PRIMARY KEY,
    product_name        VARCHAR,
    brands              VARCHAR,
    quantity            VARCHAR,
    image_url           VARCHAR,
    ingredients_text    VARCHAR,
    ingredients_tags    VARCHAR[],
    allergens_tags      VARCHAR[],
    additives_tags      VARCHAR[],
    proteins_100g       DOUBLE,
    carbohydrates_100g  DOUBLE,
    sugars_100g         DOUBLE,
    fat_100g            DOUBLE,
    salt_100g           DOUBLE,
    energy_kcal_100g    DOUBLE,
    nutriscore_grade    VARCHAR,
    nova_group          INTEGER,
    last_modified_t     BIGINT
);

CREATE TABLE IF NOT EXISTS off_taxonomy_ingredient (
    tag         VARCHAR PRIMARY KEY,
    name        VARCHAR,
    synonyms    VARCHAR[]
);

CREATE TABLE IF NOT EXISTS off_meta (
    key    VARCHAR PRIMARY KEY,
    value  VARCHAR
);
