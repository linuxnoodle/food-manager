# Food Manager

A food management/calorie counting full-stack app, based on a spring boot backend + react frontend. 
I used an LLM to format the rest of this because I did not want to manually write out the structure LOL.

## layout

```
FoodManager/
├── backend/          # spring boot api (java 17)
│   └── src/
│       ├── main/
│       │   ├── java/com/foodmanager/foodmanager/
│       │   │   ├── config/       # security + cors beans
│       │   │   ├── controller/   # rest endpoints, the entry points
│       │   │   ├── service/      # business logic, the actual work
│       │   │   ├── repo/         # jpa repositories, db access
│       │   │   ├── entity/       # jpa entities, db tables as objects
│       │   │   ├── dto/          # request/response records, what goes over the wire
│       │   │   ├── exception/    # custom exceptions + global handler
│       │   │   └── FoodmanagerApplication.java  # the main, boots everything
│       │   └── resources/        # static + templates (empty for now), config goes here later
│       └── test/                 # tests, mirror the main package layout
├── frontend/         # react + vite (jsx)
│   └── src/
│       ├── pages/    # one component per screen (login, register, dashboard, ...)
│       ├── assets/   # images, svg
│       ├── api.js    # axios calls to the backend, all in one place
│       ├── App.jsx   # routes + top level layout
│       └── main.jsx  # mounts react to the dom
└── README.md         # you are here
```

## backend layers

a request comes in at the top, goes down, comes back up. each layer only talks to the one right below it.

```
HTTP request
   │
   ▼
controller   ── takes the request, hands it to the service
   │
   ▼
service      ── the brains. hashing, rules, "does this user exist", etc
   │
   ▼
repo         ── jpa, just db queries (findByUsername, save, ...)
   │
   ▼
entity       ── a db row as a java object
   │
   ▼
H2 (the db)
```

the response walks back up the same way, but the service hands the controller a **dto** (data transfer object) instead of the raw entity, so we never leak fields like the password hash out to the client.

what goes where:
- **config/** — `@Configuration` beans. security filter chain, password encoder, cors. the stuff that wires the app together.
- **controller/** — `@RestController` classes. map urls to service methods. keep them thin, no real logic here.
- **service/** — `@Service` classes. all the actual logic (register, login, ...).
- **repo/** — interfaces extending `JpaRepository`. db access only.
- **entity/** — `@Entity` classes. one per table (users, sessions, ...).
- **dto/** — `record` types. the shape of data going in and out of the api. keeps entities out of responses.
- **exception/** — custom runtime exceptions + the `GlobalExceptionHandler` that maps them to http statuses.

## frontend layout

react 19 + vite. mui for components, react-router for pages, axios for api calls.

- **src/pages/** — one `.jsx` per screen. `Login`, `Register`, `Dashboard`, `FoodSearch`, `RecipeCreation`.
- **src/api.js** — every backend call lives here so components don't scatter axios calls around.
- **src/App.jsx** — the router + overall shell.
- **src/main.jsx** — entry point, renders `<App />`.
- **src/assets/** — images.

## how they talk

frontend talks to the backend over http at `/api/...`. backend allows `http://localhost:4201` (see `CorsConfig`), so the frontend dev server runs there. a call flows:

```
src/api.js  ──HTTP──▶  controller  ──▶  service  ──▶  repo  ──▶  H2
```

and the response comes back the same way, ending as a dto the react page can render.
