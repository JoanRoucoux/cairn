# AGENTS.md

Guidance for AI coding agents working in this repository. See the [README](README.md) for the full project overview.

## Project

Cairn — Spring Boot 4.1 / Java 25 backend in hexagonal architecture, generated from java-starter with these modules: **api, domain, adapter, schema, batch**. Base package: `com.roucoux.cairn`.

**There is no parent pom.** The root `pom.xml` is an aggregator (`<modules>` only) and no module declares it as a `<parent>`; each module is parented by `spring-boot-starter-parent` with an empty `<relativePath/>` and carries its own dependencies, versions and quality plugins. The duplication of the quality block (Spotless, JaCoCo, Failsafe) across modules is **deliberate** — it is what makes a module extractable into its own repository. Keep the copies in sync; do not factor them out into the root.

## Commands

| Command                                        | Purpose                                                   |
| ---------------------------------------------- | --------------------------------------------------------- |
| `./mvnw verify`                                | Build, unit + integration tests, ArchUnit, coverage check |
| `./mvnw verify -DskipITs`                      | Everything except the Testcontainers tests (no Docker)    |
| `./mvnw spotless:check` / `spotless:apply`     | Formatting check / fix (palantir-java-format)             |
| `./mvnw spring-boot:run -pl cairn-api`   | Run the API locally                                       |

Before considering a change done, run the same pipeline as CI: `spotless:check` then `verify` (needs Docker for the `*IT` tests).

## Architecture

- `cairn-domain` — **zero compile-scope dependencies**: pure Java, no Spring, no JPA — a Maven guarantee, not just a convention. `model/`, `exception/` (`business/` holds the abstract `BusinessException` base — extending `RuntimeException` — alongside its concrete subclasses; `technical/` holds `TechnicalException` the same way), `port/in/`, `port/out/`, `service/`. Domain services are plain classes, instantiated by the composition roots in the application modules. A port and its failure contract live together — the quote ports (`FetchQuotePort` and friends) and `MarketDataUnavailableException` are both here; the adapters raise the latter.
- `cairn-adapter` — outbound adapters, each split by role (`properties/`, `config/`, `adapter/` for a client; `entity/`, `repository/`, `adapter/` for persistence — even where it means an `adapter.adapter` package name), depending on `cairn-domain` at **compile scope**. `ArchitectureTest` checks the same boundaries again once the application is assembled.
- `cairn-api` — Spring Boot main, `application/` (`controller/` = controllers implementing the **generated** interfaces, `mapper/` = domain↔DTO mapping — **one class per resource, never a shared mapper**, `exception/` = the `@RestControllerAdvice`), `infrastructure/config/` (security, and **one `XxxDomainConfig` per slice**). Depends on `cairn-adapter` at **runtime scope only** — adapters are wired into the context but invisible at compile time. The advice maps `BusinessException` → 422 and `TechnicalException` → 502; 401/403 are left to Spring Security. The OpenAPI contract lives in this module, not at the repository root.
- `cairn-schema` — Liquibase changelogs only, no Java code. Owns the schema and is applied out-of-band, by ops or a pipeline (`./mvnw liquibase:update -pl cairn-schema`) — **no running application ever migrates the database**. The application modules depend on it at **test scope only** (never widen, never add it to `cairn-adapter`), purely so their integration tests can migrate their own throwaway Testcontainers database against the real changelog before `ddl-auto: validate` checks it.
- `cairn-batch` — second Spring Boot application over the same `cairn-domain`/`cairn-adapter`: `BatchApplication` (in the base package, so the component scan reaches the adapters), `batch/job/` (the chunk-oriented step, wired to ports only) and `batch/config/` (its composition root). Depends on `cairn-adapter` at **runtime scope**, exactly like `cairn-api`. Its metadata tables come from a `cairn-schema` changeset, with `spring.batch.jdbc.initialize-schema: never`.
- `com.roucoux.cairn.generated.*` is build output of openapi-generator: never edit it, edit the spec and rebuild. Contract-first: the spec changes before the code.
- The hexagonal rules are law, enforced by the ArchUnit tests in the application modules. The demo features are reference implementations of a full slice — model new features on them.

## Conventions

- Commits follow [Conventional Commits](https://www.conventionalcommits.org).
- Formatting is Spotless/palantir; records for immutable data; constructor injection without Lombok. A [lefthook](https://lefthook.dev) pre-commit hook runs `spotless:apply` and re-stages the result automatically (`lefthook install` once after cloning).
- Sibling modules are depended on through an explicit version property (`cairn-domain.version` and friends), never `${project.version}` — that would silently mean the wrong thing once a module is extracted.
- Schema changes only through `cairn-schema`'s Liquibase changesets (`ddl-auto: validate` will fail otherwise). Changeset ids are sequential and descriptive (`003-add-index`).

## Testing

- Naming drives the phase: `*Test` = surefire (unit, no Docker), `*IT` = failsafe (integration, Testcontainers).
- Controllers: `@WebMvcTest` + `@Import(SecurityConfig.class)` + `@MockitoBean` ports + `spring-security-test`'s `user()` post-processor (session-based, see Deviations from the starter) — no `@MockitoBean JwtDecoder`.
- Persistence: `@DataJpaTest` + `@ServiceConnection` PostgreSQL container, schema generated from the JPA mapping (`spring.jpa.hibernate.ddl-auto=create-drop`, set locally on the test). `cairn-adapter` has a test-only `TestApplication` (`@SpringBootConfiguration`) because it contains no Spring Boot app.
- Full boot: the `*IT` tests of the application modules migrate their Testcontainers database with the real `cairn-schema` changelog before `ddl-auto: validate` runs.
- Business scenarios: `CucumberIT` (`cairn-api`, `cucumber/` package) runs every `.feature` file under `src/test/resources/features/` over real HTTP through the full Spring context (`CucumberSpringConfiguration`), security opened up via `app.security.permit-all`. It is a `*IT` like any other. Add a feature by adding a `.feature` file plus a step-definition class in `cucumber/`; a `@Before` hook (`Hooks`) resets shared fixtures between scenarios. Cucumber glue classes must be `public`, unlike the rest of this test suite.
- Bean-wiring code (`@Bean` methods) is unit-tested by calling those methods directly, so the coverage gate does not depend on Docker being available.
- External clients: WireMockServer without any Spring context.
- ArchUnit rules are plain JUnit `@Test` methods over a static `ClassFileImporter` on purpose — do not migrate them to `@AnalyzeClasses`/`@ArchTest`. A rule whose subject matches nothing fails, so keep rules next to the code they constrain.
- Coverage gate: 70% lines per module (JaCoCo, merged unit+IT data).

## Deviations from the starter

Four points where Cairn intentionally diverges from the java-starter template. Read as decisions,
not drift.

1. **Session-based security, not JWT.** The starter is a stateless OAuth2 resource server; Cairn
   authenticates with WebAuthn, which needs server-side state to hold the challenge between the
   registration/assertion options call and its verification. Consequence on tests: controller
   tests use spring-security-test's `user()` post-processor instead of `jwt()`, and carry no
   `@MockitoBean JwtDecoder`.
2. **CSRF is active**, where the starter disables it. Disabling CSRF is correct for a bearer token
   carried in a header, which a browser never attaches on its own — but the WebAuthn session is
   carried by a cookie, which the browser does attach automatically, so CSRF protection stays on.
3. **`@Tag("external")` tests.** A Maven profile (`external`) and a nightly CI job, absent from
   the starter, run these tests against the real upstream providers (Yahoo Finance, CoinGecko,
   Societe Generale Sirius). They are the only tests that catch a provider changing its response
   format; everything else runs against WireMock.
4. **`numeric(28,12)` for quantities**, where the starter uses `numeric(19,4)`. A starter-precision
   column would round a Bitcoin holding's quantity to four decimal places.

## Deployment

The host is assumed to run **several applications**, so Cairn owns no ports. Two compose projects,
joined by an external Docker network created once with `docker network create edge`:

- **`proxy/`** — the shared edge proxy, deployed to `/srv/proxy`. Caddy alone, holding ports 80/443
  and the certificate volume, `import`ing every `sites/*.caddy` snippet. **It does not belong to
  Cairn** and is only kept here until a second application needs it, at which point it moves to its
  own repository. Redeploying Cairn must never restart it.
- **`compose.prod.yaml`** — a standalone overlay, not a merge target for `compose.yaml`: it pulls
  prebuilt images from GHCR (`ghcr.io/joanroucoux/cairn-{api,web,batch,schema}:${TAG}`) instead of
  building, and publishes no port at all.

`cairn.caddy` is Cairn's own site snippet, deployed into the proxy's `sites/`. Routing is by path
(`/api/*`, `/login`, `/logout`, `/webauthn/*` to `api`, everything else to `web`), so no backend
hostname is baked into `cairn-web`'s image and both halves share one origin, which the session
cookie requires (`secure`, `SameSite=Strict`).

Only `api` and `web` join `edge`. **`postgres` deliberately stays on the default network**, out of
reach of every other application sharing the proxy.

`CAIRN_DOMAIN` is set twice, and the two must agree: in `/srv/cairn/.env` (feeding the api
container's `CAIRN_RP_ID`/`CAIRN_ORIGIN`) and in `/srv/proxy/.env` (feeding the snippet's site
address, since Caddy is what reads it). It should be a subdomain, never the apex: an apex `rp-id`
would make Cairn's passkeys usable by every other application on the domain. Changing it after the
first passkey registration breaks every existing credential — `rp-id` is bound into them.

Never run `compose.yaml` and `compose.prod.yaml` on the same host: both declare
`postgres`/`api`/`web`/`schema`/`batch` against the same `cairn-data` volume name.

## Gotchas

- The aggregator declares the Spotless plugin although it holds no Java: `spotless:check` from the root resolves the plugin prefix per project and fails on any project that lacks it.
- The demo table is named `positions` (plural): `POSITION` is a reserved word in PostgreSQL.
- **`cairn-schema` stays a test-scope dependency of the application modules only** — never add it (or `liquibase-core`) to `cairn-domain`/`cairn-adapter`, and never widen its scope past `test`. An application must never be able to migrate the database itself.
- **Without Docker, `cairn-adapter`'s coverage gate fails under `-DskipITs`**: expected, not a regression — its persistence code is only exercised by `*IT` tests.
- **`cucumber-junit-platform-engine` must stay pinned to a version built against the same `junit-jupiter` line Spring Boot manages** (see `cairn-api/pom.xml`'s `cucumber.version` comment): a newer Cucumber needs a newer JUnit Platform than this project's dependency management provides, and fails at test discovery with `NoClassDefFoundError`.
- **`CucumberIT` always reports "Tests run: 0"** in the Surefire/Failsafe console summary — cosmetic, not a sign the suite didn't run. `cucumber.plugin=pretty,summary` (`junit-platform.properties`) prints the real scenario/step counts right above it.
- `mvnw` must stay executable on Linux CI (`git update-index --chmod=+x mvnw` if git loses the mode on Windows).
- GitHub Actions in `.github/workflows/` are pinned by commit SHA — when adding one, pin it the same way.
