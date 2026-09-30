# Monitoring

Prometheus scrapes the API every 15 seconds and keeps 30 days of data (capped
at 5 GB). Grafana shows it at `https://<API_DOMAIN>/grafana/`, where you sign in
as `admin` with `GRAFANA_ADMIN_PASSWORD` from `.env`. The **Word Agents**
dashboard is the home page.

```
Prometheus ──▶ app:8081/actuator/prometheus   (API, not reachable through Caddy)
           ──▶ node-exporter:9100             (VM CPU, load, memory, disk)
           ──▶ rabbitmq:15692                 (broker connections and queues)
Grafana ──▶ Prometheus                        (Caddy proxies /grafana)
```

## What's on the dashboard

| Section | Shows |
|---|---|
| Now | Ongoing games, active rooms, players online, requests/s, 5xx rate, p95 and p99 |
| Rooms and games | Active rooms by phase, ongoing games and players over time, rooms/joins/games per hour, moves per second by action |
| Load and latency | Requests/s by endpoint; p50, p95 and p99 overall and per endpoint |
| Errors | Non-2xx responses by class and status, 5xx by endpoint and exception, logged warnings and errors, RabbitMQ connections |
| Server | API and VM CPU, load average, JVM heap, GC, DB connection pool, VM memory |

A **game is ongoing** when its room is in the `clue` or `guessing` phase. A
**room is active** when anyone has joined, moved or left in the last 15
minutes; idle rooms stay in the database for 24 hours before the janitor
deletes them.

Expected client errors (409 for an illegal move, 429 for a rate limit) show up
under `CLIENT_ERROR`, not as 5xx.

## Metrics the API adds

| Metric | Type | Labels |
|---|---|---|
| `wordagents_rooms` | gauge | `phase` (lobby, clue, guessing, finished), `scope` (`all`, or `active` for the last 15 min) |
| `wordagents_room_players` | gauge | same as above |
| `wordagents_players_online` | gauge | Players with an open WebSocket |
| `wordagents_rooms_opened_total` | counter | |
| `wordagents_players_joined_total` | counter | |
| `wordagents_games_started_total` | counter | |
| `wordagents_games_finished_total` | counter | |
| `wordagents_game_actions_total` | counter | `action` (take-seat, give-clue, guess, …), `outcome` (`ok` or `rejected`) |

The room gauges come from one database query every 15 seconds, so a scrape
never touches MySQL. Spring Boot adds the rest: `http_server_requests_seconds`
(a histogram, so p95/p99 are computed with `histogram_quantile`), JVM, Hikari,
logback and process metrics.

## Alerts

`alerts.yml` defines: API down, 5xx rate above 5%, more than 5 errors logged in
10 minutes, p95 above 500 ms, p99 above 1 s, CPU above 85%, heap above 90%,
requests waiting for a DB connection, and disk above 85%. The latency and
error-rate alerts only fire when there's real traffic (more than one request
every 10 seconds).

They appear under **Alerting → Alert rules** in Grafana and on Prometheus's
`/alerts` page. Nothing is notified yet: to get emails or chat messages, add a
contact point in Grafana (**Alerting → Contact points**) and create Grafana
alert rules from these queries, or add Alertmanager.

## Changing things

The dashboard and alert files are read from the repo checkout, so edit them in
the repo and merge. `ci-deploy.sh` reloads Prometheus after each deploy, and
Grafana picks up dashboard changes within a few seconds. Dashboards can't be
saved from the Grafana UI; export the JSON and commit it instead.

## Running it locally

```bash
cd server
docker compose --profile monitoring up -d
./gradlew bootRun
```

Grafana is at http://localhost:3000 (no sign-in needed) and Prometheus at
http://localhost:9090. The `node` target shows as down locally, since
node-exporter only runs in production. The raw metrics are at
http://localhost:8081/actuator/prometheus.
