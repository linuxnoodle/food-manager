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

controller → service → repo/client → H2 (+ OpenFoodFacts). Controllers stay thin — they just map urls to service calls. Services hold the real logic (validation, nutrition math, OFF orchestration, caching). Repos do the db access, the OFF client talks to the external API. The service always hands the controller a **dto** instead of the raw entity, so fields like the password hash never reach the client.

packages: `client/` (OFF adapter) · `config/` (security, cors, OFF beans) · `controller/` (rest endpoints) · `service/` (logic) · `repo/` (jpa) · `entity/` (tables) · `dto/` (wire records) · `exception/` (custom exceptions mapped to http statuses by `GlobalExceptionHandler`).

## database

`backend/data/foodmanager.mv.db` — persistent across restarts. Configuration lives in `application.properties`:

```properties
spring.datasource.url=jdbc:h2:file:./data/foodmanager;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

The H2 web console is at http://localhost:8080/h2-console in dev (JDBC URL `jdbc:h2:file:./data/foodmanager`, user `sa`, blank password). `ddl-auto=update` adds new tables/columns on boot but never drops — fine for dev, swap to `validate` + Flyway for real migrations. `src/test/resources/application.properties` overrides the URL to in-memory H2 so `mvn test` doesn't pollute the dev DB. OpenFoodFacts knobs (cache TTLs, timeouts, user agent) also live in `application.properties` as `app.off.*`.

## API endpoints

All endpoints require an authenticated session cookie (from `POST /api/auth/login`), except register + login themselves. Unauthenticated → 401. Errors are `application/problem+json` (RFC 7807).

```
POST /api/auth/login              body: { identifier, password }            -> sets session cookie
POST /api/user/register           body: { username, email, password }       -> 201

GET  /api/food/search?include=<csv>&exclude=<csv>&page=1&size=20
GET  /api/food/local-search?include=<csv>&exclude=<csv>&page=1&size=20
GET  /api/food/{code}             (?refresh=true to bypass cache)
GET  /api/ingredients/autocomplete?q=<text>

POST /api/log                     body: { code, quantity, unit, meal, loggedAt? }
GET  /api/log                     (?date=YYYY-MM-DD to filter to one day)

POST /api/recipes                 body: { name, description?, servings, instructions?, ingredients:[{code,quantity,unit}] }
GET  /api/recipes
```

### auth

#### `POST /api/auth/login`
- **Request body:** `{ "identifier": "<email or username>", "password": "<plain>" }`
- **Response 200:** `{ "id": "<uuid>", "username": "...", "email": "..." }`
- **Response 401:** bad credentials
- **Side effect:** sets `session=<token>` HttpOnly cookie. Send `withCredentials: true` from axios and the browser handles the rest.

#### `POST /api/user/register`
- **Request body:** `{ "username": "...", "email": "...", "password": "..." }`
- **Response 201:** `{ "id": "<uuid>", "username": "...", "email": "..." }`
- **Response 409:** username or email already taken

### food

#### `GET /api/food/search`
Ingredient-based structured search. Filters use canonical taxonomy tags (`en:chicken`, `en:gluten`) — get them from `/api/ingredients/autocomplete`, never let users free-type them (OFF's v2 search needs canonical IDs).

- **Query params:** `include` (CSV of tags that must be present), `exclude` (CSV of tags that must be absent), `page` (1-based, default 1), `size` (default 20, clamped to 50). At least one of `include`/`exclude` must be non-empty.
- **Response 200:**
  ```json
  {
    "page": 1, "size": 20, "totalCount": 4593065,
    "items": [
      { "code": "5449000000996", "name": "Coca-Cola",
        "brand": "The Coca-Cola Company", "imageUrl": "https://...",
        "nutriscoreGrade": "e", "novaGroup": 4,
        "allergens": ["en:milk"], "fromCache": false }
    ],
    "fromCache": false
  }
  ```
  `totalCount` is OFF's count across all pages, not `items.length`.
- **Response 400:** no filters / malformed tag
- **Response 502/503/504:** OFF unreachable / rate-limited / timed out

#### `GET /api/food/local-search`
Same query shape as `/search` but only queries the local H2 cache (never hits OFF). Useful for "things I've looked at before" or offline mode. Same response shape as `/search` (always `fromCache: true`).

#### `GET /api/food/{code}`
Full detail for one food. `code` is the opaque OFF product code (from `/search` items' `code`), not a user-typed barcode.

- **Query params:** `?refresh=true` (optional) — bypass cache, force a fresh OFF fetch.
- **Response 200:**
  ```json
  {
    "code": "5449000000996", "name": "Original Taste", "brand": "Coca-Cola",
    "quantity": "33 cl", "imageUrl": "https://...",
    "nutriscoreGrade": "e", "novaGroup": 4,
    "ingredientsText": "carbonated water, sugar, ...",
    "ingredientsTags": ["en:carbonated-water", "en:sugar"],
    "allergens": [], "additives": ["en:e338", "en:e150d"],
    "kcal": 42.0, "proteinG": 0.0, "fatG": 0.0,
    "carbsG": 10.6, "sugarG": 10.6, "saltG": 0.0,
    "lastFetchedAt": "2026-07-08T...", "fromCache": true
  }
  ```
  All nutrient fields are per-100g and may be `null` if OFF doesn't have them.
- **Response 404:** unknown code or tombstoned product

#### `GET /api/ingredients/autocomplete`
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
  Send the `tag` back verbatim in `include=` / `exclude=`. Display the `name`.

### food log

#### `POST /api/log`
Log a food you ate. `code` comes from `/api/food/search`. The food is fetched + cached from OFF if it isn't already, and the response nutrients are scaled to the quantity (per-100g × quantity / 100).

- **Request body:**
  ```json
  { "code": "5449000000996", "quantity": 250, "unit": "ml", "meal": "lunch", "loggedAt": "2026-07-08T12:30:00Z" }
  ```
  `meal` is one of `breakfast|lunch|dinner|snack`, `unit` is `g` or `ml`, `loggedAt` is optional (defaults to server-now).
- **Response 201:**
  ```json
  {
    "id": "<uuid>", "code": "5449000000996", "name": "Original Taste", "imageUrl": "https://...",
    "quantity": 250.0, "unit": "ml", "meal": "lunch", "loggedAt": "2026-07-08T12:30:00Z",
    "kcal": 105.0, "proteinG": 0.0, "fatG": 0.0, "carbsG": 26.5, "sugarG": 26.5, "saltG": 0.0
  }
  ```
  Nutrients are scaled to the quantity; a field is `null` if OFF didn't have it.
- **Response 400:** blank code, quantity ≤ 0, or a bad `unit`/`meal`.
- **Response 404:** code OFF doesn't know about.

#### `GET /api/log`
List the current user's entries, newest first.

- **Query params:** `?date=YYYY-MM-DD` (optional) — restrict to one UTC day.
- **Response 200:** an array of the same entry shape as `POST`.

### recipes

#### `POST /api/recipes`
Create a recipe. Each ingredient's `code` resolves to a cached Food row (fetched from OFF on a miss), and the per-serving nutrition is summed across ingredients and divided by `servings`.

- **Request body:**
  ```json
  {
    "name": "Chicken & rice",
    "description": "weeknight thing",
    "servings": 2,
    "instructions": "cook the rice, then ...",
    "ingredients": [
      { "code": "...", "quantity": 150, "unit": "g" },
      { "code": "...", "quantity": 200, "unit": "g" }
    ]
  }
  ```
  `unit` is `g` or `ml`. `description` and `instructions` are optional.
- **Response 201:**
  ```json
  {
    "id": "<uuid>", "name": "Chicken & rice", "description": "weeknight thing",
    "servings": 2, "instructions": "cook the rice, then ...",
    "ingredients": [ { "code": "...", "name": "...", "imageUrl": "...", "quantity": 150.0, "unit": "g" } ],
    "nutrition": { "kcal": 320.5, "proteinG": 28.0, "fatG": 5.0, "carbsG": 40.0, "sugarG": 0.5, "saltG": 0.2 },
    "createdAt": "2026-07-08T12:30:00Z"
  }
  ```
  `nutrition` values are per serving. Any nutrient none of the ingredients reported is `null`.
- **Response 400:** blank name, no ingredients, quantity ≤ 0, bad unit, or a blank ingredient `code`.
- **Response 404:** an ingredient `code` OFF doesn't know about.

#### `GET /api/recipes`
List the current user's recipes, newest first. Same response shape as `POST`, one object per recipe.

### status codes

| Status | Meaning |
|---|---|
| 200 | success |
| 201 | created (register, log entry, recipe) |
| 400 | bad request (bad query, malformed tag, bad code, invalid log/recipe input) |
| 401 | not authenticated (no/invalid session cookie) |
| 404 | food not found in OFF (or tombstoned locally) |
| 409 | username or email already taken |
| 502 | OFF returned 5xx on a cache-miss (no row to fall back to) |
| 503 | OFF returned 429 — frontend must back off (IP bans are manual to undo) |
| 504 | OFF timed out on a cache-miss |

## frontend layout

React 19 + Vite. mui for components, react-router for pages, axios for api calls.

- **src/pages/** — one `.jsx` per screen: `Login`, `Register`, `Dashboard`, `FoodSearch`, `RecipeCreation`.
- **src/api.js** — every backend call lives here so components don't scatter axios calls around.
- **src/App.jsx** — the router + overall shell. **src/main.jsx** — entry point. **src/assets/** — images.

Frontend talks to backend over http at `/api/...`; the backend allows `http://localhost:4201` (`CorsConfig`), so the dev server runs there. All calls go through one shared axios instance (`withCredentials: true` carries the session cookie):

```js
// api.js
import axios from 'axios'
const api = axios.create({ baseURL: 'http://localhost:8080/api', withCredentials: true })
export default api

export const authApi = {
  login: (identifier, password) => api.post('/auth/login', { identifier, password }),
  register: (username, email, password) => api.post('/user/register', { username, email, password }),
}
export const foodApi = {
  search: (include, exclude, page = 1, size = 20) =>
    api.get('/food/search', { params: { include: include.join(','), exclude: exclude.join(','), page, size } }),
  localSearch: (include, exclude, page = 1, size = 20) =>
    api.get('/food/local-search', { params: { include: include.join(','), exclude: exclude.join(','), page, size } }),
  detail: (code, refresh = false) => api.get(`/food/${code}`, { params: refresh ? { refresh: true } : {} }),
  autocomplete: (q) => api.get('/ingredients/autocomplete', { params: { q } }),
}
export const logApi = {
  add: (code, quantity, unit, meal, loggedAt) => api.post('/log', { code, quantity, unit, meal, loggedAt }),
  list: (date) => api.get('/log', { params: date ? { date } : {} }),
}
export const recipeApi = {
  create: (recipe) => api.post('/recipes', recipe),
  list: () => api.get('/recipes'),
}
```

## TODO

**Done in v1:** food log (`/api/log`), recipes (`/api/recipes`). Both lean on the cached `Food` table for their nutrition numbers.

**Deferred (not yet):**
- Stale-while-revalidate on the detail path (currently blocking on conditional GET).
- ETag-based conditional GET (OFF doesn't return ETags currently, so Tier 1 falls back to TTL-only).
- Barcode scanner UI (would post to `/api/food/{code}` — already works).
- Admin-only refresh gate (`?refresh=true` is currently open to any authenticated user).
- Resilience4j circuit breaker / retry around OFF calls. Plain timeouts suffice for class-project scale.

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
