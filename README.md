# Word Agents

A real-time word-association party game for two teams, played on everyone's own phone. Each team's spymaster gives one-word clues, and their operatives try to find their team's agents among 25 cover words while avoiding the assassin.

Only spymasters' devices ever receive the secret map. The server hides the card roles from everyone else, so there's nothing to peek at.

## How to play

1. **Open a room.** The host enters a codename, opens an operation, and shares the room code or link.
2. **Pick seats.** Each team needs one spymaster and at least one operative. In the lobby, the host can rename the teams, choose word packs, and add the table's own words.
3. **Give a clue.** On their turn, the spymaster enters one word and a number from 0 to 9. The number is how many cards the clue points to; operatives get that many guesses plus one. A 0 clue has no guess limit.
4. **Guess.** Operatives tap a word to mark it, then either:
   - **Submit** to reveal it and keep guessing, or
   - **End turn** to reveal it and pass the turn. With nothing marked, End turn just passes; you must make at least one guess first.
5. **Win.** Contact all of your team's agents first. A bystander or rival card passes the turn, and the assassin loses the game on the spot.

If a clue matches a word on the board, the opposing spymaster decides on their own screen whether it stands. If they uphold the penalty, the clue-giver reveals one of their own cards before giving a new clue.

## Features

- Every player uses their own device; seats are remembered on each device, so a refresh or reconnect keeps your place.
- Live updates over WebSockets, with presence dots showing who's online.
- Built-in genre packs, plus custom word packs you can save, edit, and share by code or link (`/pack/CODE`).
- Custom words for a single game, which always make it onto the board.
- A spymaster-only map with a cover toggle, and an assassin warning while you write your clue.
- A live activity feed of clues and guesses.
- Rate limiting on the API.

## Stack

| Part | Tech |
|---|---|
| Frontend | React 19 + Vite, TypeScript, `@stomp/stompjs`; deployed to Vercel as a static site |
| API | Java 25, Spring Boot 4.1 (Web MVC, WebSocket/STOMP, Data JPA), Gradle |
| Database | MySQL 8.4, schema managed by Liquibase |
| Realtime | STOMP over WebSocket, relayed through RabbitMQ so several API instances can share sessions |
| Hosting | One Oracle Cloud VM running Caddy, the API, MySQL and RabbitMQ with Docker Compose |

## Repository layout

```
artifacts/word-agents/     React app (the game UI)
  src/App.tsx              Screens: home, lobby, game table, dialogs
  src/word-packs.tsx       Word list panel and the pack library
  src/room-client.ts       API calls and the useRoom hook (REST + STOMP)
  src/pack-client.ts       Word pack API calls and local pack storage
  src/index.css            Design tokens and styles
lib/game-core/             Shared TypeScript types for the API contract
server/                    Spring Boot API
  src/main/java/.../game   Entities and GameRules (every move and permission check)
  src/main/java/.../room   REST API, per-player views, STOMP auth and broadcasting
  src/main/resources/db    Liquibase changelogs
  deploy/                  Production Compose file, Caddy config, VM scripts
.github/workflows/         Test, release and deploy pipelines
```

## Running locally

You'll need Node 24, pnpm 10, JDK 25 and Docker.

```bash
pnpm install
```

Start MySQL and RabbitMQ:

```bash
cd server && docker compose up -d
```

Start the API on port 8080:

```bash
cd server && ./gradlew bootRun
```

Start the web app on port 5173. It proxies `/api` and `/ws` to `localhost:8080`:

```bash
PORT=5173 pnpm --filter @workspace/word-agents run dev
```

Open http://localhost:5173. To play alone, open extra players in private windows or other browsers; each browser holds one seat per room.

> **macOS note:** the pnpm workspace strips the darwin native binaries that Vite needs (Rollup, esbuild, Lightning CSS), so on a Mac you may have to install those separately and point `NODE_PATH` at them.

## Tests and checks

Rules unit tests and integration tests. The integration tests use real MySQL and RabbitMQ through Testcontainers, so Docker must be running:

```bash
cd server && ./gradlew test
```

TypeScript typecheck across the workspace:

```bash
pnpm run typecheck
```

## Configuration

**API** (environment variables):

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | MySQL connection (JDBC URL) |
| `RABBITMQ_HOST`, `RABBITMQ_STOMP_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` | STOMP relay |
| `ALLOWED_ORIGINS` | Comma-separated origins allowed to call the API and open the WebSocket |
| `PORT` | HTTP port (default 8080) |

**Frontend:**

| Variable | Purpose |
|---|---|
| `VITE_API_URL` | Origin of the API in production. Leave unset in development to use the Vite proxy. |

## Deployment

- **API:** one Oracle Cloud Always Free VM runs `server/deploy/compose.prod.yaml` (Caddy + API + MySQL + RabbitMQ). Setup steps are in [server/deploy/README.md](server/deploy/README.md).
- **Frontend:** Vercel, built from `artifacts/word-agents` (see `vercel.json`). Vercel's own Git deployments are disabled; CI deploys instead.
- **Monitoring:** Prometheus and Grafana run on the same VM. Grafana at `/grafana` on the API domain shows load, errors, p95/p99 latency, active rooms and ongoing games; see [server/deploy/monitoring/README.md](server/deploy/monitoring/README.md).
- **CI/CD:** pull requests run the tests. Each merge to `main` is tested, tagged as the next version and deployed to the VM and Vercel. The version bump is a patch by default; put `#minor` or `#major` in a commit message to bump further.

## How it works

- **The server is authoritative.** Clients only send actions (`take-seat`, `give-clue`, `guess`, `end-turn`, and so on); every rule is enforced in `GameRules`.
- **Secrets never leave the server.** Each player gets their own view of the room, and unrevealed card roles are removed for anyone who isn't a spymaster.
- **Moves are serialized.** Each move runs in a transaction that locks the room row. Views are built inside that transaction and sent after commit.
- **Realtime.** Each player subscribes to their own view at `/user/topic/view`; moves themselves go over HTTP. RabbitMQ relays messages, so any API instance can reach a player connected to another.
- **Seat tokens** are stored as SHA-256 hashes.
