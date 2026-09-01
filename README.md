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

`schema` and `batch` both carry a `profiles` entry so `docker compose up` alone never starts them:
the schema is migrated explicitly, out-of-band, and the batch job is meant to be triggered by cron
(`docker compose run --rm batch`), not to run continuously.

## Running in production

Cairn assumes a host shared with other applications, so it owns no ports: a separate Caddy project
terminates TLS for everything, and Cairn joins it over an external Docker network.

```
/srv/proxy/    proxy/compose.yaml + Caddyfile + sites/   Caddy alone, ports 80/443
/srv/cairn/    compose.prod.yaml + .env                  postgres, api, web, batch
```

Set up the proxy once, then never again when deploying Cairn:

```bash
docker network create edge
cp env.example .env        # in /srv/proxy: set CAIRN_DOMAIN to the real subdomain
cp cairn.caddy /srv/proxy/sites/
docker compose -f /srv/proxy/compose.yaml up -d
```

Then, for each release:

```bash
export TAG=v1.2.3
export CAIRN_DOMAIN=cairn.example.com
export CAIRN_PASSWORD=s0me-real-secret
export POSTGRES_PASSWORD=s0me-real-secret
docker compose -f compose.prod.yaml --profile migrate run --rm schema
docker compose -f compose.prod.yaml up -d api web
```

The migration runs first, on purpose: `ddl-auto: validate` means a failed migration must block the
deploy rather than half-start it.

`CAIRN_DOMAIN` is set in both `.env` files and the two must agree, since Caddy reads one and the
api container the other. Use a subdomain, not the apex: an apex `rp-id` would make Cairn's passkeys
usable by any other application on the domain. Choose it once, too — `rp-id` is bound into every
credential registered against it, so changing the domain later breaks every existing passkey. See
AGENTS.md's Deployment section for the routing details.

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
proxy/                     The shared edge proxy — NOT Cairn's, kept here until a second
                           application needs it (see AGENTS.md's Deployment section)
```

Dependency rules: `cairn-domain` depends on nothing but the JDK (a Maven guarantee); `cairn-adapter` implements the domain's outbound ports and reaches the domain only through its ports, model and exceptions (ArchUnit); `cairn-api`/`cairn-batch` depend on `cairn-adapter` at **runtime scope only**, so neither can reach adapter internals even by accident. Errors map by family in the `@RestControllerAdvice` — `BusinessException` → 422, `TechnicalException` → 502; authentication and authorization (401/403) are handled by Spring Security.

`cairn-schema` is applied out-of-band (ops or pipeline, `liquibase:update`) — a running application **never** migrates the database itself. The application modules depend on it at **test scope only**, so their integration tests can migrate their own throwaway Testcontainers database with the real changelog.

The demo features are reference implementations of a full hexagonal slice — use them as the model for your own, then replace them.

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
