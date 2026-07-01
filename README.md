# Food Manager

A food management/calorie counting full-stack app, based on a spring boot backend + react frontend. I used an LLM to format the rest of this because I did not want to manually write out the structure LOL.

## layout

```
FoodManager/
├── backend/          # spring boot api (java 17, boot 4)
│   └── src/
│       ├── main/
│       │   ├── java/com/foodmanager/foodmanager/
│       │   │   ├── client/       # external-API adapters (openfoodfacts)
│       │   │   ├── config/       # security, cors, OFF config beans
│       │   │   ├── controller/   # rest endpoints
│       │   │   ├── service/      # business logic + caching
│       │   │   ├── repo/         # jpa repositories
│       │   │   ├── entity/       # jpa entities
│       │   │   ├── dto/          # response records + dto/off/ for OFF shapes
│       │   │   ├── exception/    # custom exceptions + global handler
│       │   │   └── FoodmanagerApplication.java
│       │   └── resources/        # application.properties
│       └── test/                 # tests, mirror main package layout
├── frontend/         # react + vite (jsx)
│   └── src/
│       ├── pages/    # one component per screen
│       ├── api.js    # axios calls to the backend
│       └── ...
└── README.md         # you are here
```

## backend layers

```
HTTP request
   │
   ▼
controller   ── takes the request, hands to the service
   │
   ▼
service      ── the brains. cache logic, OFF orchestration, rules
   │
   ▼
repo / client── jpa repo for db access, client for external APIs
   │
   ▼
H2 / OFF     ── the database (file-based H2) and OpenFoodFacts API
```

The response walks back up the same way, but the service hands the controller a **dto** (data transfer object) instead of the raw entity, so we never leak fields like the password hash out to the client.

what goes where:
- **client/** — external-API adapters. Currently just `OpenFoodFactsClient`. Speaks HTTP to OFF, translates exceptions, returns plain records.
- **config/** — `@Configuration` beans. security filter chain, password encoder, cors, OFF `RestClient` + cache beans.
- **controller/** — `@RestController` classes. map urls to service methods. keep them thin, no real logic here.
- **service/** — `@Service` classes. all the actual logic.
- **repo/** — interfaces extending `JpaRepository`. db access only.
- **entity/** — `@Entity` classes. one per table.
- **dto/** — `record` types for what goes over the wire. `dto/off/` is OFF-specific response shapes.
- **exception/** — custom runtime exceptions + the `GlobalExceptionHandler` that maps them to http statuses.

## database (file-based H2)

`backend/data/foodmanager.mv.db` — persistent across restarts. Configuration lives in `application.properties`:

```properties
spring.datasource.url=jdbc:h2:file:./data/foodmanager;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

The H2 web console is exposed at http://localhost:8080/h2-console in dev (JDBC URL `jdbc:h2:file:./data/foodmanager`, user `sa`, blank password). `ddl-auto=update` adds new tables/columns on boot but never drops — fine for dev, swap to `validate` + Flyway for real migrations.

`src/test/resources/application.properties` overrides the URL to in-memory H2 so `mvn test` doesn't pollute the dev DB.

## OpenFoodFacts integration

The backend talks to three OFF endpoints:

| Purpose | Endpoint | Notes |
|---|---|---|
| Structured ingredient search | `GET /api/v2/search?tagtype_N=ingredients&tag_contains_N=contains\|does_not_contain&tag_N=...` | v3 has no search; this is the only filter-based search. |
| Product detail | `GET /api/v3/product/{code}.json?fields=...` | v3 returns `status: "success"\|"failure"`, no ETag/Last-Modified. |
| Ingredient autocomplete | `GET /api/v3/taxonomy_suggestions?tagtype=ingredients&string=...` | Returns display names; we derive canonical `en:...` tags from them. |

**Required by OFF terms**: every request carries `User-Agent: FoodManager/0.1 (contact@example.com)` (configurable via `app.off.user-agent`).

### Three-tier cache

OFF is rate-limited hard (10 req/min/IP for search, 15 req/min/IP for product reads) and bans IPs that exceed it.

| Tier | What | Where | TTL |
|---|---|---|---|
| 1 | One row per OFF product | H2 `foods` table | 30d (`app.off.cache-ttl`) |
| 2 | Whole search-result pages | H2 `search_cache` table | 1h (`app.off.search-cache-ttl`) |
| 3 | Ingredient autocomplete | Caffeine in-memory | 24h (`app.off.taxonomy-cache-ttl`) |

Lookup flow for `GET /api/food/{code}`:
1. Cache miss → full GET to OFF, save row + headers.
2. Cache hit + age < TTL → return immediately, no OFF call.
3. Cache hit + age ≥ TTL + no ETag → full GET, replace fields.
4. Cache hit + age ≥ TTL + ETag → conditional GET (currently always degrades to (3) because OFF doesn't send ETags).
5. OFF returns 404 / `status: "failure"` → tombstone the row + 404 to the client.
6. OFF returns 5xx/timeout + we have a cached row → serve stale, log warning.

Searches additionally:
- Cache the full result page under a deterministic key (`sha256(include | exclude | page | size)`) so pagination back/forth doesn't re-hit OFF.
- Coalesce concurrent identical searches via an in-flight `ConcurrentHashMap` — N users typing the same filter = 1 OFF call.
- Upsert every result into the `foods` table as a side effect, populating Tier 1 for free.

### Configuration knobs (in `application.properties`)

```properties
app.off.base-url=https://world.openfoodfacts.org
app.off.user-agent=FoodManager/0.1 (contact@example.com)
app.off.cache-ttl=P30D              # tier 1: food detail rows
app.off.search-cache-ttl=PT1H       # tier 2: search result pages
app.off.taxonomy-cache-ttl=PT24H    # tier 3: ingredient autocomplete
app.off.connect-timeout=3s
app.off.read-timeout=5s
```

## API endpoints

All require an authenticated session cookie (from `POST /api/auth/login`). Unauthenticated → 401. Errors are `application/problem+json` (RFC 7807).

### auth + users (pre-existing)

```
POST /api/auth/login        body: { identifier, password }   -> sets session cookie, returns { id, username, email }
POST /api/user/register     body: { username, email, password } -> 201, returns { id, username, email }
```

### food (new)

```
GET  /api/food/search?include=<csv>&exclude=<csv>&page=1&size=20
GET  /api/food/local-search?include=<csv>&exclude=<csv>&page=1&size=20
GET  /api/food/{code}
GET  /api/food/{code}?refresh=true

GET  /api/ingredients/autocomplete?q=<text>
```

See `docs/api.md` (or the section below) for parameter contracts and response shapes.

### status codes you'll see

| Status | Meaning |
|---|---|
| 200 | success |
| 400 | bad query (no include/exclude, malformed tag, bad code) |
| 401 | not authenticated (no/invalid session cookie) |
| 404 | food not found in OFF (or tombstoned locally) |
| 502 | OFF returned 5xx on a cache-miss (no row to fall back to) |
| 503 | OFF returned 429 — frontend must back off (IP bans are manual to undo) |
| 504 | OFF timed out on a cache-miss |

## frontend layout

React 19 + Vite. mui for components, react-router for pages, axios for api calls.

- **src/pages/** — one `.jsx` per screen. `Login`, `Register`, `Dashboard`, `FoodSearch`, `RecipeCreation`.
- **src/api.js** — every backend call lives here so components don't scatter axios calls around.
- **src/App.jsx** — the router + overall shell.
- **src/main.jsx** — entry point, renders `<App />`.
- **src/assets/** — images.

Frontend talks to backend over http at `/api/...`. backend allows `http://localhost:4201` (see `CorsConfig`), so the frontend dev server runs there. A call flows:

```
src/api.js  ──HTTP──▶  controller  ──▶  service  ──▶  repo/client  ──▶  H2 / OFF
```

and the response comes back as a dto the react page can render.

# HTTP API reference (frontend integration)

## auth

### `POST /api/auth/login`
- **Request body:** `{ "identifier": "<email or username>", "password": "<plain>" }`
- **Response 200:** `{ "id": "<uuid>", "username": "...", "email": "..." }`
- **Response 401:** `application/problem+json` with bad-credentials detail
- **Side effect:** sets `session=<token>` HttpOnly cookie. Send `withCredentials: true` from axios and the browser handles the rest.

### `POST /api/user/register`
- **Request body:** `{ "username": "...", "email": "...", "password": "..." }`
- **Response 201:** `{ "id": "<uuid>", "username": "...", "email": "..." }`
- **Response 409:** username or email already taken

## food

### `GET /api/food/search`
Ingredient-based structured search. Filters use canonical taxonomy tags (`en:chicken`, `en:gluten`). Get tags from `/api/ingredients/autocomplete`, never let users free-type them.

- **Query params:**
  - `include` — CSV of ingredient tags that must be present (e.g. `en:chicken,en:rice`)
  - `exclude` — CSV of ingredient tags that must be absent (e.g. `en:gluten`)
  - `page` — 1-based page number (default 1)
  - `size` — page size (default 20, clamped to 50)
- **At least one of** `include` **or** `exclude` **must be non-empty.**
- **Response 200:**
  ```json
  {
    "page": 1, "size": 20, "totalCount": 4593065,
    "items": [
      { "code": "5449000000996", "name": "Coca-Cola",
        "brand": "The Coca-Cola Company",
        "imageUrl": "https://...",
        "nutriscoreGrade": "e", "novaGroup": 4,
        "allergens": ["en:milk"], "fromCache": false }
    ],
    "fromCache": false
  }
  ```
  `totalCount` is OFF's count across all pages, not `items.length`.
- **Response 400:** no filters / malformed tag
- **Response 502/503/504:** OFF unreachable / rate-limited / timed out

### `GET /api/food/local-search`
Same query shape as `/search` but only queries the local H2 cache (never hits OFF). Useful for "things I've looked at before" UX or offline mode.

- **Response shape:** same as `/search` (always `fromCache: true`).

### `GET /api/food/{code}`
Full detail for one food. `code` is the opaque OFF product code (returned by `/search` items' `code` field). Not a user-typed barcode.

- **Query params:** `?refresh=true` (optional) — bypass cache, force a fresh OFF fetch.
- **Response 200:**
  ```json
  {
    "code": "5449000000996",
    "name": "Original Taste",
    "brand": "Coca-Cola",
    "quantity": "33 cl",
    "imageUrl": "https://...",
    "nutriscoreGrade": "e", "novaGroup": 4,
    "ingredientsText": "carbonated water, sugar, ...",
    "ingredientsTags": ["en:carbonated-water", "en:sugar", ...],
    "allergens": [], "additives": ["en:e338", "en:e150d"],
    "kcal": 42.0, "proteinG": 0.0, "fatG": 0.0,
    "carbsG": 10.6, "sugarG": 10.6, "saltG": 0.0,
    "lastFetchedAt": "2025-07-01T19:28:32.375185281Z",
    "fromCache": true
  }
  ```
  All nutrient fields are per-100g and may be `null` if OFF doesn't have them.
- **Response 404:** unknown code or tombstoned product

### `GET /api/ingredients/autocomplete`
Returns canonical ingredient tags matching a prefix string. Use to populate include/exclude pickers.

- **Query params:** `?q=<text>` (URL-encoded, min 1 char)
- **Response 200:**
  ```json
  [
    { "tag": "en:chicken-wing-meat-and-skin", "name": "Chicken wing meat and skin" },
    { "tag": "en:chicken",                    "name": "chicken" },
    { "tag": "en:chicken-breast",             "name": "chicken breast" }
  ]
  ```
  Send the `tag` value back verbatim as a value in `include=` / `exclude=`. Display the `name`.

## frontend integration sketch

```js
// api.js — add to the existing file
import api from './api'  // existing axios instance, baseURL=http://localhost:8080/api, withCredentials:true

export const authApi = {
  login: (identifier, password) =>
    api.post('/auth/login', { identifier, password }),
  register: (username, email, password) =>
    api.post('/user/register', { username, email, password }),
}

export const foodApi = {
  search: (include, exclude, page = 1, size = 20) =>
    api.get('/food/search', { params: { include: include.join(','), exclude: exclude.join(','), page, size } }),
  localSearch: (include, exclude, page = 1, size = 20) =>
    api.get('/food/local-search', { params: { include: include.join(','), exclude: exclude.join(','), page, size } }),
  detail: (code, refresh = false) =>
    api.get(`/food/${code}`, { params: refresh ? { refresh: true } : {} }),
  autocomplete: (q) =>
    api.get('/ingredients/autocomplete', { params: { q } }),
}
```

Suggested `FoodSearch.jsx` UX:
1. Two multi-select pickers ("include" / "exclude"), each backed by `foodApi.autocomplete` debounced to ~300ms (don't fire on every keystroke — see `/search` 503 risk).
2. On submit, call `foodApi.search(includeTags, excludeTags, page)`. Render `items` as cards showing `name`, `brand`, `nutriscoreGrade`, and an allergen warning badge if `allergens.length > 0`.
3. Clicking an item navigates to `/food/${code}` which calls `foodApi.detail(code)` and shows the full ingredients list, additives, nutrients.
4. Show a "cached, last updated X ago" badge when `fromCache: true`.
5. On 503 from `/search`, surface a "rate limited, try again in a minute" message — don't auto-retry.

## TODO

**Deferred (not in v1, schema-ready):**
- Stale-while-revalidate on the detail path (currently blocking on conditional GET).
- ETag-based conditional GET (OFF doesn't return ETags currently, so Tier 1 falls back to TTL-only — same code path either way).
- User-scoped food log ("what did I eat today?"). The `Food` entity is ready to be referenced from a future `FoodLog` table; no migration needed.
- Barcode scanner UI (would post to `/api/food/{code}` — already works).
- Admin-only refresh gate (`?refresh=true` is currently open to any authenticated user).
- Resilience4j circuit breaker / retry around OFF calls. Plain timeouts suffice for class-project scale.
- Bulk OFF data import (the docs recommend the daily JSONL export if you need >100 products; we don't).

## Basic verification

- `GET /api/food/5449000000996` (Coca-Cola) returns full detail from OFF on first call, `fromCache:true` on second.
- `GET /api/food/search?include=en:chicken&exclude=en:gluten` returns 4.5M+ matches paginated.
- `GET /api/ingredients/autocomplete?q=chick` returns `{tag, name}` pairs with derived `en:` tags.
- `GET /api/food/local-search?include=en:carbonated-water` finds the previously-cached Coca-Cola row.
- 404 on bad barcode, 400 on malformed tag, 401 without session cookie.

## how to run

```bash
# backend
cd backend && ./mvnw spring-boot:run
# listens on http://localhost:8080
# h2 console: http://localhost:8080/h2-console (jdbc:h2:file:./data/foodmanager, user sa, blank pw)

# frontend (separate terminal)
cd frontend && npm run dev
# listens on http://localhost:4201
```
