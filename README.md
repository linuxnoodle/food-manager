# Food Manager

A food management/calorie counting full-stack app, based on a spring boot backend + react frontend. I used an LLM to format the rest of this because I did not want to manually write out the structure LOL.

## layout

```
FoodManager/
├── backend/          # spring boot api (java 17, boot 4)
│   └── src/main/
│       ├── java/com/foodmanager/foodmanager/
│       │   ├── client/       # OpenFoodFactsClient (http or duckdb impl) + off DTOs
│       │   ├── ingest/       # build/refresh the self-hosted duckdb mirror
│       │   ├── config/       # security, cors, OFF beans, scheduling
│       │   ├── controller/   # rest endpoints
│       │   ├── service/      # logic + caching
│       │   ├── repo/         # jpa repositories
│       │   ├── entity/       # jpa entities
│       │   ├── dto/          # wire records (+ dto/off/ for OFF shapes)
│       │   └── exception/    # custom exceptions + global handler
│       └── resources/
│           ├── application.properties
│           ├── db/duckdb/schema.sql        # off_products + off_taxonomy_ingredient
│           └── taxonomies/ingredients.txt  # vendored OFF ingredient taxonomy (2.7 MB)
└── frontend/         # react + vite (jsx)
```

## backend layers

controller → service → repo/client → H2 (+ the OFF data source). Controllers stay thin, services hold the real logic (validation, nutrition math, caching), repos do db access. The OFF data source is pluggable behind the `OpenFoodFactsClient` interface — either an http impl that calls `world.openfoodfacts.org`, or a duckdb impl that reads a self-hosted mirror. The service always hands the controller a **dto**, not the raw entity, so the password hash etc. never leak.

## data sources — the `app.off` switch

All food data flows through one interface, picked by `app.off.*`:

- **`app.off.mode=remote`** (default) — `HttpOpenFoodFactsClient` calls OFF over the wire. Rate-limited (10 search + 15 product reads per min/IP; OFF bans scrapers), so everything is cached (see [caching](#caching)). No setup.
- **`app.off.mode=local`** — `DuckDbOpenFoodFactsClient` reads a local DuckDB mirror. You build it first (see [building the mirror](#building-the-mirror)), then all reads hit the local file — **zero OFF calls**, no rate limits.
- **`app.off.selfhost=true`** — the launch flag. On startup the app **downloads the full OFF JSONL dump itself** and ingests it into DuckDB, then serves from the mirror (the duckdb client activates automatically, no separate mode flip). A daily `@Scheduled` re-sync keeps it fresh. No shell script, no systemd — everything in-process.

In local/selfhost mode the macro-only search quirk disappears (OFF silently drops nutrient filters with no tag anchor; our own queries don't), and autocomplete tags come straight from the canonical taxonomy so they always match product `ingredients_tags`.

## building the mirror

The ingest uses **DuckDB-native `read_json`** — a single SQL query instead of row-by-row JDBC batches. This drops the ingest time from ~60 min to ~2 min (plus download time). The dump is never committed (it's multi-GB); only the 2.7 MB `ingredients.txt` taxonomy is vendored in-repo so autocomplete works out-of-the-box. The DuckDB mirror itself is tracked via Git LFS (`*.duckdb`) and is **799 MB for 4.6M products** (columnar compression).

- **Launch flag (no local file):**
  ```
  ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dapp.off.selfhost=true"
  # downloads app.off.dump-url (default: static.openfoodfacts.org openfoodfacts-products.jsonl.gz)
  # → ingests → serves from duckdb → re-syncs every app.off.resync-interval (default 24h)
  # At ~12 GB compressed download (~8 min), the DuckDB SQL itself runs in ~2 min for 4.6M products.
  # On restart, skips the download if the temp file and DuckDB mirror are already built.
  ```
- **Manual ingest from a file you fetched yourself** (keep for low-bandwidth hosts):
  ```
  ./mvnw spring-boot:run --spring.profiles.active=ingest -Dapp.off.dump-path=/path/to/openfoodfacts-products.jsonl.gz
  # builds the mirror, then keep running with app.off.mode=local to serve from it
  ```
  Leave `dump-path` out to only refresh the taxonomy.

## caching (remote mode)

OFF is rate-limited hard, so remote mode caches aggressively:

| Tier | What | Where | TTL |
|---|---|---|---|
| 1 | product detail rows | H2 `foods` | 30d (`app.off.cache-ttl`) |
| 2 | whole search-result pages | H2 `search_cache` | 1h (`app.off.search-cache-ttl`) |
| 3 | ingredient autocomplete | Caffeine in-memory | 24h (`app.off.taxonomy-cache-ttl`) |

In local/selfhost mode the cache layers just sit under the already-local data — harmless, mostly redundant.

## database

App tables (H2): users, sessions, food log, recipes, the OFF cache — live in file-based H2 at `backend/data/foodmanager.mv.db`. H2 web console at http://localhost:8080/h2-console (user `sa`, blank pw). `ddl-auto=update` adds tables/columns on boot but never drops. `src/test/resources/application.properties` swaps H2 to in-memory for tests.

The **OFF mirror** is a separate DuckDB file (tracked via Git LFS as `*.duckdb`) at `app.off.duckdb-path` (default `./data/off/foodmanager.duckdb`). Two stores, one process. DuckDB file is **799 MB** for 4.6M products — uses columnar compression (null bitmaps, RLE, dictionary). No indexes needed; columnar scan over the small field set is fast enough on its own.

## API endpoints

All require an authenticated session cookie (from `POST /api/auth/login`) except register + login themselves. Unauthenticated → 401. Errors are `application/problem+json` (RFC 7807).

```
POST /api/auth/login              { identifier, password }            -> sets session cookie, 200
POST /api/user/register           { username, email, password }       -> 201

GET  /api/food/search?include=<csv>&exclude=<csv>&minProtein=&maxCarbs=&maxSugar=&maxFat=&maxSalt=&page=1&size=20
GET  /api/food/local-search      (same params)
GET  /api/food/{code}            (?refresh=true to bypass cache)
GET  /api/ingredients/autocomplete?q=<text>

POST /api/log                     { code, quantity, unit, meal, loggedAt? }   -> 201, scaled nutrition
GET  /api/log                     (?date=YYYY-MM-DD)

POST /api/recipes                 { name, description?, servings, instructions?, ingredients:[{code,quantity,unit}] } -> 201
GET  /api/recipes
```

**search** — `include`/`exclude` are canonical taxonomy tags (`en:chicken`, `en:-gluten`); get them from autocomplete, don't free-type. The `minProtein` / `maxCarbs` / `maxSugar` / `maxFat` / `maxSalt` params are per-100g macro bounds; in local mode they work on their own, in remote mode OFF silently ignores nutrient filters with no tag anchor so send at least one ingredient tag too. Response: `{ page, size, totalCount, items: [{code,name,brand,imageUrl,nutriscoreGrade,novaGroup,allergens,fromCache}], fromCache }`.

**`{code}` detail** — `{ code, name, brand, quantity, imageUrl, nutriscoreGrade, novaGroup, ingredientsText, ingredientsTags, allergens, additives, kcal, proteinG, fatG, carbsG, sugarG, saltG, lastFetchedAt, fromCache }` — all nutrients per-100g, nullable. 404 if unknown/tombstoned.

**autocomplete** — `[{ tag:"en:chicken", name:"chicken" }]`; send `tag` back verbatim in `include=`/`exclude=`, show `name`.

**food log** — `unit` is `g` or `ml`, `meal` is `breakfast|lunch|dinner|snack`, `loggedAt` optional (server-now). Returned nutrients are scaled to the quantity (per-100g × qty / 100). `GET` lists the user's entries newest-first, optional `?date=` filters to one UTC day. 400 on bad input, 404 for an unknown `code`.

**recipes** — each ingredient `code` resolves to a food row; per-serving nutrition is summed across ingredients and divided by `servings`. `nutrition` per serving, any nutrient none of the ingredients reported is `null`. `GET` lists the user's recipes newest-first.

### status codes

| Status | Meaning |
|---|---|
| 200 | success |
| 201 | created (register, log entry, recipe) |
| 400 | bad request (bad query, malformed tag, bad code, invalid log/recipe input) |
| 401 | not authenticated |
| 404 | food not found / tombstoned |
| 409 | username or email already taken |
| 502/503/504 | OFF 5xx / 429 / timeout on a remote cache-miss (local/selfhost mode never produces these) |

## frontend layout

React 19 + Vite. mui for components, react-router for pages, axios for calls. `src/pages/` is one `.jsx` per screen (`Login`, `Register`, `Dashboard`, `FoodSearch`, `RecipeCreation`); `src/api.js` holds every backend call so components don't scatter axios; `src/App.jsx` is the router/shell. The dev server runs at `http://localhost:4201` (allowed by `CorsConfig`), hitting the backend at `http://localhost:8080/api/...` through one shared axios instance with `withCredentials: true` (carries the session cookie).

```js
export const foodApi = {
  search: (include, exclude, macros = {}, page = 1, size = 20) =>
    api.get('/food/search', { params: { include: include.join(','), exclude: exclude.join(','), ...macros, page, size } }),
  autocomplete: (q) => api.get('/ingredients/autocomplete', { params: { q } }),
  detail: (code) => api.get(`/food/${code}`),
}
export const logApi   = { add: (e) => api.post('/log', e),        list: (date) => api.get('/log', { params: date ? { date } : {} }) }
export const recipeApi = { create: (r) => api.post('/recipes', r), list: () => api.get('/recipes') }
```

## TODO

**Done:** food log + recipes (cached nutrition); macro/nutrient filters on search; self-hosted DuckDB mirror with the `selfhost` launch flag + daily re-sync.

**Deferred:** stale-while-revalidate on the detail path; ETag conditional GET (OFF doesn't send ETags); barcode-scanner UI; admin-only `?refresh` gate; circuit breaker around remote OFF.

## how to run

```bash
# backend (default: remote OFF, over the wire)
cd backend && ./mvnw spring-boot:run

# backend, self-hosted: downloads + builds the duckdb mirror, serves from it, re-syncs daily
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dapp.off.selfhost=true"

# backend, local mirror you already built
cd backend && ./mvnw spring-boot:run -Dapp.off.mode=local

# frontend (separate terminal)
cd frontend && npm run dev
```