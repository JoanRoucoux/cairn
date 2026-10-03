# Development

Prerequisites: **JDK 25** and **Docker**. Maven comes with the wrapper (`./mvnw`, `mvnw.cmd` on Windows cmd).

## Running locally

```bash
./mvnw verify                                                          # build, unit and integration tests, architecture, coverage
./mvnw spring-boot:run -pl cairn-api -Dspring-boot.run.profiles=local  # starts the API on :8080, authentication off
```

`spring-boot:run` starts what it needs from `compose.local.yaml` on its own: PostgreSQL on
`localhost:5432` (`app`/`app`), then a one-shot `schema` container that applies the Liquibase
changelog, and only then the application. The data lives in the `cairn-local-data` volume.
`compose.yaml` is the whole stack and is not involved.

With the `local` profile, Swagger UI serves the contract at `http://localhost:8080/swagger-ui.html`.
Without it, authentication is real and `CAIRN_PASSWORD` must be set.

The batch runs one job, named as `deploy/cairn.cron` names it, and exits. `run.at` makes each run a
new job instance. The worker also starts Kafka on `localhost:9092`. Each application can run
alongside the others:

```bash
./mvnw spring-boot:run -pl cairn-batch "-Dspring-boot.run.arguments=--spring.batch.job.name=snapshotJob run.at=$(date +%s)"
./mvnw spring-boot:run -pl cairn-batch "-Dspring-boot.run.arguments=--spring.batch.job.name=refreshQuotesJob assetClasses=EQUITY run.at=$(date +%s)"
./mvnw spring-boot:run -pl cairn-kafka
```

The containers keep running after the applications stop. `docker compose -f compose.local.yaml stop`
stops them, `docker compose -f compose.local.yaml down -v` also resets the database.

## Running with Docker Compose

`compose.yaml` runs the whole stack. `postgres` has no published port: only the other compose
services reach it, over the compose network, by service name.

```bash
export CAIRN_PASSWORD=s0me-real-secret
export POSTGRES_PASSWORD=s0me-real-secret
docker compose --profile migrate up --build schema   # one-shot: applies the Liquibase changelog
docker compose up -d --build api                      # starts the API on :8080 (and postgres)
docker compose run --rm batch --spring.batch.job.name=snapshotJob "run.at=$(date +%s)"   # one batch job, on demand
```

`CAIRN_PASSWORD` is required outside the `local` profile: `WebAuthnConfig` refuses to boot with
its default value once it detects it isn't running with `local` active (see the "Deviations from the
starter" section of [AGENTS.md](../AGENTS.md)).

`CAIRN_RP_ID` and `CAIRN_ORIGIN` are optional: `compose.yaml` only forwards them to the container
when set in the host shell, so leaving them unset lets `cairn-api/application.yml`'s own defaults
(`localhost` and `http://localhost:4200`) apply, which is enough for the single-user local
quickstart above. Export them (for example `CAIRN_RP_ID=cairn.example.com` and
`CAIRN_ORIGIN=https://cairn.example.com`) to point the passkey ceremony at a real domain.

### The whole stack, as the server runs it

The commands above start the API alone, which is what the frontend's dev server proxies to. They do
not exercise Caddy, and three defects have reached production precisely because nothing local did:
the proxy strips the `/api` prefix the contract does not carry, it puts the frontend and the API on
one origin, and outside the `local` profile CSRF is real. Add `web` and `caddy` and all three are
back under test:

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
the schema is migrated explicitly, out-of-band, and the batch jobs are meant to be triggered by
cron, not to run continuously.

### Kafka

`kafka` (a single-node KRaft broker) and `worker` (`cairn-kafka`, which declares the
`cairn.prices` and `cairn.portfolio` topics, runs the intraday refresh scheduler and consumes
`refresh.completed` events to record valuation points) come up with the rest of `docker compose up`.
`batch`, `worker` and `api` publish to the topics through `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`; the
API publishes only when a quote is entered by hand. Watch a topic from the host with the broker's
own console consumer:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic cairn.prices --from-beginning
```

There is no endpoint that triggers a refresh. A refresh is either a `refreshQuotesJob` batch run
(`docker compose run --rm batch --spring.batch.job.name=refreshQuotesJob assetClasses=EQUITY "run.at=$(date +%s)"`,
or the cron job in production) or a tick of the worker's intraday scheduler. Both publish to
`cairn.prices` and `cairn.portfolio`. Recording a quote by hand through the API publishes the same
two events, the second with the `MANUAL` trigger. `kafka` and `worker` being down does not fail a
refresh or a manual quote: publishing never fails the caller, and `api` stays `UP` even with the
broker stopped.

## Contract-first workflow

1. Edit `cairn-api/openapi/openapi.yaml` (the contract comes first).
2. `./mvnw compile` regenerates the interfaces and DTOs (`com.roucoux.cairn.generated.*`, build output, never edited).
3. Implement the new interface methods in a controller, mapping DTOs to the domain through the inbound ports.

## Testing

- **Unit tests** (`*Test`, surefire): domain services with plain JUnit and Mockito, controllers with `@WebMvcTest` and spring-security-test's `user()` post-processor (the security is session-based, not JWT), external clients against WireMock.
- **Integration tests** (`*IT`, failsafe): full application boot with `@SpringBootTest`, and Testcontainers PostgreSQL wherever a database is involved.
- **Business scenarios** (`CucumberIT`, failsafe): the `.feature` files under `cairn-api/src/test/resources/features/` run over real HTTP through the full Spring context. `trade.feature` is a good model for adding your own, with a step-definition class in the `cucumber/` package.
- **External providers**: tests tagged `external` run against the real Yahoo Finance, CoinGecko and SG Sirius with `./mvnw verify -pl cairn-adapter -am -Pexternal`. The nightly workflow runs them.
- **Architecture**: the hexagonal rules, checked on every build by ArchUnit.
- **Coverage**: JaCoCo gate at 70 % of lines per module. `./mvnw verify -DskipITs` skips the Testcontainers tests, and `cairn-adapter`'s gate then fails, which is expected.

## Quality and conventions

- Formatting: Spotless with palantir-java-format, through `./mvnw spotless:apply` and `./mvnw spotless:check`. A [lefthook](https://lefthook.dev) pre-commit hook runs `spotless:apply` and re-stages the result automatically (`lefthook install` once after cloning).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org). The pull request title is what is enforced, since the squash merge turns it into the commit on main.
- Schema changes only through `cairn-schema`'s Liquibase changelogs, applied out-of-band (`ddl-auto: validate`, never by an application).
- The rest of the conventions, and the traps worth knowing, are in [AGENTS.md](../AGENTS.md).
