# [Project name]

_Replace the heading above with the project's name, and this line with one sentence describing what this app does for users._

## Run & Operate

- `cd server && docker compose up -d` — local MySQL 8.4 and RabbitMQ 4.1 (STOMP plugin on 61613)
- `cd server && ./gradlew bootRun` — Spring Boot API on port 8080 (needs JDK 25)
- `PORT=5173 pnpm --filter @workspace/word-agents run dev` — React app; proxies `/api` and `/ws` to `localhost:8080`
- `cd server && ./gradlew test` — rules unit tests plus integration tests on real MySQL/RabbitMQ via Testcontainers (Docker must be running)
- `pnpm run typecheck` — TypeScript typecheck
- Server env: `DATABASE_URL` (JDBC URL), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_STOMP_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`, `ALLOWED_ORIGINS` (comma-separated), `PORT`
- Frontend env: `VITE_API_URL` — origin of the Spring API in production
- Production: one Oracle Cloud VM running `server/deploy/compose.prod.yaml` (Caddy + app + MySQL + RabbitMQ); see `server/deploy/README.md`
- CI/CD: PRs run `.github/workflows/test.yml`; each merge to main is tested, released (patch bump, or `#minor` / `#major` in a commit message) and deployed to the VM and Vercel

## Stack

- API: Java 25, Spring Boot 4.1 (Web MVC, WebSocket/STOMP, Data JPA), Gradle (Kotlin DSL)
- DB: MySQL 8.4, schema managed by Liquibase (YAML changelogs); Hibernate only validates
- Realtime: STOMP over WebSocket, relayed through RabbitMQ so multiple API instances share sessions
- Frontend: React + Vite (pnpm workspace), `@stomp/stompjs`; deployed to Vercel as a static site
- API is packaged as a Docker image (`server/Dockerfile`)

## Where things live

- `server/src/main/java/com/tk/wordagents/game` — entities (`Room`, `Player`, `Card`, `Clue`) and `GameRules`, where every move and permission check lives
- `server/src/main/java/com/tk/wordagents/room` — REST API, `RoomService` (locked read-modify-write per move), `RoomViews` (per-player projection), STOMP auth and broadcasting
- `server/src/main/resources/db/changelog` — Liquibase changelogs
- `lib/game-core` — TypeScript types for the API contract; keep in sync with `RoomViews` and `GameAction`
- `artifacts/word-agents/src/room-client.ts` — client API calls and the `useRoom` hook

## Architecture decisions

- The server is authoritative; clients only send actions. The secret map is hidden by projection: `RoomViews.viewFor()` nulls unrevealed card roles for anyone who isn't a spymaster.
- Each move runs in a transaction holding `SELECT … FOR UPDATE` on the room row. Views are built inside that transaction and sent after commit, so broadcasting never needs a second DB connection.
- Each player subscribes to `/user/topic/view`. The WebSocket CONNECT frame carries `room` and `token` headers; clients may subscribe only to their own view and may not SEND. Moves go over HTTP.
- User-destination and user-registry broadcasts through RabbitMQ let any instance push to a player connected to another. Views go to every player in the room; presence only drives the online dots.
- Seat tokens are stored as SHA-256 hashes.

## Product

_Describe the high-level user-facing capabilities of this app once they exist._

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Gotchas

- Booleans are `TINYINT(1)` in MySQL (Liquibase's mapping); `hibernate.type.preferred_boolean_jdbc_type: TINYINT` keeps schema validation happy.
- Clue numbers and guess budgets store "unlimited" as NULL.
- Presence for sessions on another instance appears only after the next user-registry broadcast, which can take several seconds.
- Local frontend dev on macOS: the pnpm workspace strips darwin native binaries (rollup, esbuild), so Vite needs them supplied separately.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
