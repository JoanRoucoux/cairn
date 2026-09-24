# Cairn

Cairn — Spring Boot backend in hexagonal architecture, generated from [java-starter](https://github.com/JoanRoucoux/java-starter) with these modules: **api, domain, adapter, schema, batch**.

There is no parent pom: the root `pom.xml` only aggregates, and every module is a standalone Maven project parented by `spring-boot-starter-parent`. A module can be moved to its own repository as-is.

## Stack

| Tool                                     | Role                                                        |
| ---------------------------------------- | ----------------------------------------------------------- |
| Spring Boot 4.1 / Java 25                | Application framework, Maven modules with wrapper           |
| openapi-generator (contract-first)       | `cairn-api/openapi/openapi.yaml` → interfaces + DTOs  |
| Spring Security + WebAuthn               | Session-based passkey authentication (see AGENTS.md's Deviations from the starter) |
| RestClient                               | External API client adapter (timeouts via properties)       |
| Spring Data JPA + PostgreSQL             | Persistence adapter                                         |
| Liquibase (`cairn-schema`)         | Versioned changelogs, applied out-of-band — never by an app |
| Spring Batch (`cairn-batch`)       | Chunk-oriented jobs over the same domain as the API         |
| Testcontainers, WireMock, ArchUnit       | Integration tests, client tests, architecture enforcement   |
| Cucumber                                 | Business-scenario acceptance tests, over real HTTP          |

## Getting started

Prerequisites: **JDK 25** and **Docker**. Maven comes with the wrapper (`./mvnw`, `mvnw.cmd` on Windows cmd).

```bash
./mvnw verify                                        # build + unit/integration tests + architecture + coverage
./mvnw spring-boot:run -pl cairn-api           # starts the API on :8080
```

The database schema is applied separately, and only when it changes:

```bash
./mvnw liquibase:update -pl cairn-schema       # migrates a local PostgreSQL reachable at localhost:5432
```

`liquibase:update` only needs to run once, and again after adding a changeset — starting or restarting an application never touches the schema.

The demo job runs on demand and exits when it is done:

```bash
./mvnw spring-boot:run -pl cairn-batch
```

Without an identity provider, activate the `local` profile to disable authentication: `./mvnw spring-boot:run -pl cairn-api -Dspring-boot.run.profiles=local`.

## Running with Docker Compose

`compose.yaml` runs the whole stack: `postgres` has no published port — only the other compose
services reach it, over the compose network, by service name.

```bash
cd apps/cairn
export CAIRN_PASSWORD=s0me-real-secret
export POSTGRES_PASSWORD=s0me-real-secret
docker compose --profile migrate up --build schema   # one-shot: applies the Liquibase changelog
docker compose up -d --build api                      # starts the API on :8080
docker compose run --rm batch                          # runs the batch job once, on demand
```

`CAIRN_PASSWORD` is required outside the `local` profile — `WebAuthnConfig` refuses to boot with
its default value once it detects it isn't running with `local` active (see AGENTS.md's Deviations
from the starter, point 1).

`CAIRN_RP_ID`/`CAIRN_ORIGIN` are optional: `compose.yaml` only forwards them to the container when
set in the host shell, so leaving them unset lets `cairn-api/application.yml`'s own defaults
(`localhost` / `http://localhost:4200`) apply, which is enough for the single-user local quickstart
above. Export them (e.g. `export CAIRN_RP_ID=cairn.example.com
CAIRN_ORIGIN=https://cairn.example.com`) to point the passkey ceremony at a real domain.


### The whole stack, as the server runs it

The commands above start the API alone, which is what `pnpm start` proxies to. They do not exercise
Caddy, and three defects have reached production precisely because nothing local did: the proxy
strips the `/api` prefix the contract does not carry, it puts the frontend and the API on one
origin, and outside the `local` profile CSRF is real. Add `web` and `caddy` and all three are back
under test:

```bash
export CAIRN_PASSWORD=s0me-real-secret
export POSTGRES_PASSWORD=s0me-real-secret
export CAIRN_ORIGIN=http://localhost   # the browser's origin through Caddy, not ng serve's :4200
export WEB_TAG=sha-1a2b3c4             # a deployed frontend, or a tag you built yourself

docker compose --profile migrate up --build schema
docker compose up -d --build
```

Then open `http://localhost`, never `http://localhost:8080`: the second bypasses the proxy and with
it everything this stack exists to check. `curl -sS -o /dev/null -w '%{http_code}'
http://localhost/api/actuator/health` answers 200 only if the prefix is being stripped, which is
the same assertion the deploy workflow makes against production.

To run a frontend that is not deployed, build it in its own repository under a tag and name it:

```bash
docker build -t ghcr.io/joanroucoux/cairn-web:local ../cairn-web
WEB_TAG=local docker compose up -d
```

`schema` and `batch` both carry a `profiles` entry so `docker compose up` alone never starts them:
the schema is migrated explicitly, out-of-band, and the batch job is meant to be triggered by cron
(`docker compose run --rm batch`), not to run continuously.

### Kafka

`kafka` (a single-node KRaft broker) and `worker` (`cairn-kafka`, which declares the
`cairn.prices`/`cairn.portfolio` topics, runs the intraday refresh scheduler and consumes
`refresh.completed` events to record valuation points) come up with the rest of `docker compose
up`. Both `api` and `batch` publish to them through `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`. Watch a
topic from the host with the broker's own console consumer:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic cairn.prices --from-beginning
```

A refresh (`POST /quotes/refresh`, `docker compose run --rm batch`, or the cron job) publishes to
`cairn.prices` and `cairn.portfolio` regardless of which entry point triggered it. `kafka` and
`worker` being down does not fail a refresh: publishing is fire-and-forget from the caller's point
of view, `api` stays `UP` even with the broker stopped.

## Running in production

Cairn assumes a host shared with other applications. The server, the shared Caddy proxy and the
monitoring belong to the `infra` repository; Cairn owns `/srv/cairn` only.

```
/srv/proxy/    infra's: Caddy alone, ports 80/443, one snippet per site in sites/
/srv/cairn/    compose.prod.yaml, .env, deploy.sh, run-batch.sh, cairn.cron
```

Every push to `main` deploys (see AGENTS.md's Deployment section). The core of what deploy.sh does,
by hand, once the images are pulled:

```bash
cd /srv/cairn
sed -i "s|^TAG=.*|TAG=sha-1a2b3c4|" .env
docker compose -f compose.prod.yaml --profile migrate --profile batch pull schema api batch
docker compose -f compose.prod.yaml --profile migrate run --rm -T schema </dev/null
docker compose -f compose.prod.yaml up -d --wait postgres api
```

The migration runs first, on purpose: `ddl-auto: validate` means a failed migration must block the
deploy rather than half-start it.

`CAIRN_DOMAIN` is set in both `.env` files and the two must agree, since Caddy reads one and the
api container the other. Use a subdomain, not the apex: an apex `rp-id` would make Cairn's passkeys
usable by any other application on the domain. Choose it once, too — `rp-id` is bound into every
credential registered against it, so changing the domain later breaks every existing passkey. See
AGENTS.md's Deployment section for the routing details.

### Push monitors

`deploy/run-batch.sh` pings an Uptime Kuma push monitor after each cron-triggered batch run, and
the worker's intraday scheduler pings its own. Each monitor's URL lives in `/srv/cairn/.env`, under
the variable name its heartbeat uses:

| Variable               | Job                | Cron                     |
| ----------------------- | ------------------ | ------------------------ |
| `KUMA_PUSH_EQUITY`      | `refreshQuotesJob` (EQUITY) | `0 19 * * 1-5`     |
| `KUMA_PUSH_ETF`         | `refreshQuotesJob` (ETF)    | `15 19 * * 1-5`    |
| `KUMA_PUSH_FUND`        | `refreshQuotesJob` (FUND)   | `0 11 * * 2-6`     |
| `KUMA_PUSH_SNAPSHOT`    | `snapshotJob`                | `30 23 * * *`, interval 25 h |
| `KUMA_PUSH_INTRADAY`    | worker's intraday refresh scheduler | not a cron job |
| `KUMA_PUSH_SUMMARY`     | worker's daily Telegram summary scheduler | `0 45 19 * * MON-FRI`, interval 73 h |

The summary runs Monday to Friday only, so its monitor waits 73 h: a 25 h interval would alert
every weekend. A weekday failure is noticed the same evening anyway, when no message arrives.

### Telegram summary

Monday to Friday at 19:45 (Europe/Paris), the worker sends net worth, the day's change and each
envelope's change to Telegram, with the dashboard's 1D figures. To set it up:

1. Create a bot with [@BotFather](https://t.me/BotFather) (`/newbot`) and put its token in
   `/srv/cairn/.env` as `TELEGRAM_BOT_TOKEN`.
2. Send the bot any message, then open `https://api.telegram.org/bot<token>/getUpdates` and put
   `message.chat.id` in `/srv/cairn/.env` as `TELEGRAM_CHAT_ID`.
3. Create the push monitor above and put its URL in `/srv/cairn/.env` as `KUMA_PUSH_SUMMARY`.

The bot token never appears in the logs, not even on a failed send: `docker logs cairn-worker-1`
must never show it.

## Project structure

```
pom.xml                    Aggregator only: <modules>, no inheritance
cairn-domain/        model/, exception/ (business/ holds BusinessException + its subclasses,
                           technical/ holds TechnicalException + its), port/in/ (use cases),
                           port/out/ (external providers, repositories), service/ — plain Java,
                           ZERO dependencies (a Maven guarantee, not just a convention)
cairn-adapter/       client/ (properties/, config/, adapter/), persistence/ where applicable —
                           depends on cairn-domain
cairn-api/           Spring Boot application: REST exposition
├── openapi/openapi.yaml   The REST contract (source of truth, edited first)
├── application/           controller/ (implements the generated interfaces), mapper/ (domain↔DTO,
│                          one class per resource), exception/ (@RestControllerAdvice)
├── infrastructure/        config/ (SecurityConfig, one XxxDomainConfig per slice)
└── generated/             openapi build output (never edited, never committed)
cairn-schema/        Liquibase changelogs (db/changelog/) — owns the schema, no Java code
cairn-batch/         Spring Boot application: Spring Batch jobs over cairn-domain/cairn-adapter
compose.prod.yaml          Production overlay: GHCR images, no published port
cairn.caddy                Cairn's routing, deployed into the shared proxy's sites/
deploy/                    deploy.sh, run-batch.sh and cairn.cron, shipped to /srv/cairn
```

Dependency rules: `cairn-domain` depends on nothing but the JDK (a Maven guarantee); `cairn-adapter` implements the domain's outbound ports and reaches the domain only through its ports, model and exceptions (ArchUnit); `cairn-api`/`cairn-batch` depend on `cairn-adapter` at **runtime scope only**, so neither can reach adapter internals even by accident. Errors map by family in the `@RestControllerAdvice` — `BusinessException` → 422, `TechnicalException` → 502; authentication and authorization (401/403) are handled by Spring Security.

`cairn-schema` is applied out-of-band (ops or pipeline, `liquibase:update`) — a running application **never** migrates the database itself. The application modules depend on it at **test scope only**, so their integration tests can migrate their own throwaway Testcontainers database with the real changelog.

The demo features are reference implementations of a full hexagonal slice — use them as the model for your own, then replace them.

## Loading a portfolio

`POST /portfolio/import` takes a semicolon-separated CSV and creates whatever the rows refer to and
does not exist yet: accounts, instruments and positions. `GET /portfolio/import/template` returns
the header to fill in, produced from the same constant the parser reads so the two cannot drift
apart, followed by two example rows. A line starting with `#` is skipped, so the examples can stay
in the file.

```
account;accountType;institution;instrument;isinOrTicker;quantity;averageCost
# Sample Broker;PEA;Sample Bank;Sample S&P 500 ETF;FR0011550185;12;26.65
# Sample Broker;CTO;Sample Bank;Sample Bank Share;GLE.PA;10;
```

`isinOrTicker` is whatever identifies the instrument: an ISIN, a ticker, or a provider id such as
`bitcoin`. The import first looks for an existing instrument with that ISIN or source reference.
Failing that, it asks Yahoo Finance, the only price source able to look an instrument up. A
CoinGecko coin, an SG Sirius fund or a manually priced instrument must therefore be created before
the import, which then finds it by its source reference. Leave `averageCost` empty for a position
with no known cost basis.

Two properties worth knowing before running it:

- **All or nothing.** One unreadable or unresolvable row and nothing is written; the 422 lists
  every refused row with its line number, so the file is fixed in one pass rather than one deploy
  at a time.
- **It updates, it never deletes.** A row whose (account, instrument) pair already exists updates
  its quantity and cost basis, which makes replaying a corrected file safe. A position removed from
  the file stays in the database: deleting is an explicit `DELETE /holdings/{id}`.

`GET /portfolio/export` is the inverse operation and a one-request backup. Take one before
importing over an existing portfolio, since an import overwrites quantities silently.

## Contract-first workflow

1. Edit `cairn-api/openapi/openapi.yaml` (the contract comes first).
2. `./mvnw compile` regenerates the interfaces and DTOs (`com.roucoux.cairn.generated.*` — build output, never edited).
3. Implement the new interface methods in a controller, mapping DTOs to the domain through the inbound ports.

## Testing

- **Unit tests** (`*Test`, surefire): domain services with plain JUnit/Mockito, controllers with `@WebMvcTest` + `jwt()`, external clients against WireMock.
- **Integration tests** (`*IT`, failsafe): full application boot with `@SpringBootTest`, and Testcontainers PostgreSQL wherever a database is involved.
- **Business scenarios** (`CucumberIT`, failsafe): `.feature` files under `cairn-api/src/test/resources/features/` run over real HTTP through the full Spring context — `quote.feature` is the reference scenario for adding your own.
- **Architecture**: the hexagonal rules, checked on every build.
- **Coverage**: JaCoCo gate at 70% lines per module.

## Quality and conventions

- Formatting: Spotless with palantir-java-format — `./mvnw spotless:apply` / `spotless:check`. A [lefthook](https://lefthook.dev) pre-commit hook runs `spotless:apply` and re-stages the result automatically (`lefthook install` once after cloning).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org).
- Schema changes only through `cairn-schema`'s Liquibase changelogs, applied out-of-band (`ddl-auto: validate` — never by an application).
