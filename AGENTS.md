# AGENTS.md

Guidance for AI coding agents working in this repository. See the [README](README.md) for the full project overview.

## Project

Cairn — Spring Boot 4.1 / Java 25 backend in hexagonal architecture, generated from java-starter with these modules: **api, domain, adapter, schema, batch, kafka**. Base package: `com.roucoux.cairn`.

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
- `cairn-kafka` — third entry point over the same hexagon: `KafkaWorkerApplication` (base package, same reason as `cairn-batch`), `kafka/config/` (`TopicsConfig` declares the `cairn.prices` and `cairn.portfolio` topics as `NewTopic` beans, `WorkerDomainConfig` is its composition root). It has no web server (`spring.main.web-application-type: none`) and stays up (`keep-alive: true`) to host the `KafkaAdmin` that creates the topics on startup, the intraday refresh scheduler (EQUITY+ETF every 15 min Mon-Fri 9:00-17:45, CRYPTO every 15 min, Europe/Paris) and the `ValuationConsumer` (`kafka/consumer/`) that turns each `refresh.completed` event on `cairn.portfolio` into a recorded valuation point, consumer group `cairn-valuation`. In the worker, the CoinGecko adapter is wired as a plain prototype rather than a scoped proxy: a scoped proxy over a prototype target makes a new adapter per method call, which would lose the one grouped call per refresh. Depends on `cairn-adapter` at **runtime scope**, exactly like `cairn-api`/`cairn-batch`. The `adapter/messaging/` package (`cairn-adapter`) is where the actual publishing lives: `adapter/` (`KafkaEventPublisher`, `EventEnvelope`, `PriceUpdatedData`, `ValuationRecordedData`), `config/` (`KafkaMessagingConfig`), `properties/` (`KafkaMessagingProperties`).
- `com.roucoux.cairn.generated.*` is build output of openapi-generator: never edit it, edit the spec and rebuild. Contract-first: the spec changes before the code.
- The hexagonal rules are law, enforced by the ArchUnit tests in the application modules. The demo features are reference implementations of a full slice — model new features on them.

## Conventions

- Commits follow [Conventional Commits](https://www.conventionalcommits.org). What is enforced is the **pull request title**, which the squash merge turns into the commit on main (`pr.yml`).
- **Comments are the exception, not the norm**, in code and in configuration alike. Write one only for a trap that no test and no error message would catch. If a failing test would catch the edit the comment warns about, or if the sentence belongs in this file, it does not belong in the file it annotates.
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
- Tests that would otherwise need a live broker replace `PublishEventPort` with a stub/mock (`QuoteAnnouncementService` and its callers depend only on the port), exactly like `FetchQuotePort` and the other outbound ports: `RefreshQuotesJobIT` mocks `AnnounceQuotesUseCase`, and the API and batch full-context ITs build the real publisher but never publish. Only `cairn-adapter`'s `KafkaEventPublisherIT` and `cairn-kafka`'s `KafkaWorkerApplicationIT` and `ValuationRoundTripIT` exercise the real Kafka wiring, both through a Testcontainers broker.
- Coverage gate: 70% lines per module (JaCoCo, merged unit+IT data).

## Deviations from the starter

Four points where Cairn intentionally diverges from the java-starter template. Read as decisions,
not drift.

1. **Session-based security, not JWT.** The starter is a stateless OAuth2 resource server; Cairn
   authenticates with WebAuthn, which needs server-side state to hold the challenge between the
   registration/assertion options call and its verification. Consequence on tests: controller
   tests use spring-security-test's `user()` post-processor instead of `jwt()`, and carry no
   `@MockitoBean JwtDecoder`. The session itself is stored in PostgreSQL by Spring Session JDBC
   (tables from changeset 013, 30 days sliding, persistent `SESSION` cookie), so a deploy no
   longer signs everyone out.
2. **CSRF is active**, where the starter disables it. Disabling CSRF is correct for a bearer token
   carried in a header, which a browser never attaches on its own — but the WebAuthn session is
   carried by a cookie, which the browser does attach automatically, so CSRF protection stays on.
3. **`@Tag("external")` tests.** A Maven profile (`external`) and a nightly CI job, absent from
   the starter, run these tests against the real upstream providers (Yahoo Finance, CoinGecko,
   Societe Generale Sirius). They are the only tests that catch a provider changing its response
   format; everything else runs against WireMock.
4. **`numeric(28,12)` for quantities**, where the starter uses `numeric(19,4)`. A starter-precision
   column would round a Bitcoin holding's quantity to four decimal places.

## Portfolio import

`POST /portfolio/import` is one of two places where several writes must succeed or fail together,
each with its own transaction boundary in `infrastructure/transaction/`: `PortfolioImportTransaction`
for the import, and `InstrumentDeletionTransaction` for deleting an instrument, which also deletes
every one of its holdings first. Both exist because `cairn-domain` is plain Java and cannot open a
transaction itself. Both call the use case rather than implementing it, on purpose: `useCasesAreImplementedByDomainServicesOnly`
rejects an inbound port implemented outside `..domain.service..`, and a wrapper that implemented it
was the first shape tried for the import. The corresponding controller depends on the wrapper class,
not on the port, so the transaction cannot be bypassed by accident.

Validation happens twice on purpose: `PortfolioCsvReader` checks shape, types and enums, the domain
checks business rules. Both refuse with **every** offending row, never just the first, and both
leave the database untouched. The reader throws `ImportFileRejectedException`, whose errors already
carry the one-based file line. The domain throws `PortfolioImportRejectedException`, whose errors
carry a zero-based row index, and the controller places them back on their lines through the
`ImportFile` the reader returned. A row's line is not its index plus a constant: the reader skips
blank lines and lines starting with `#`, which is how the template's commented example rows stay in
the file without ever being imported. The advice only renders the lines as an RFC 9457 `errors`
extension member.

An `ImportError` carries an **`ImportErrorCode` and the offending token, never a sentence**. The
consumer is a bilingual UI whose convention is that error wording comes from its own translation
files, never from the server, so a message built here could not be shown. Adding a failure mode
means adding a code to the enum, to the contract's `ImportErrorResponse`, and to the consumer's
translations — deliberately three visible places rather than one silent string.

An import matches an existing instrument by ISIN **or** source reference before resolving, which is
what lets `import.feature` exercise the whole HTTP path without calling Yahoo or CoinGecko. Keep
new import scenarios on already-existing instruments for the same reason.

## Deployment

The host runs **several applications**, so Cairn owns neither ports nor host configuration. Both
belong to the `infra` repository: Terraform for the OVH resources, Ansible for the server, the
shared Caddy proxy in `/srv/proxy` and the monitoring. Cairn joins the proxy over the external
`edge` network and drops its own site snippet into `/srv/proxy/sites/`.

**`compose.prod.yaml`** is a standalone file, not a merge target for `compose.yaml`: it pulls
prebuilt images from GHCR and publishes no port. The backend images are tagged `${TAG}` and the
frontend `${WEB_TAG}`, deliberately two variables: `cairn-web` is a separate repository with its
own history, and neither half waits on the other to deploy.

`cairn.caddy` is Cairn's own site snippet, deployed into the proxy's `sites/`. Routing is by path,
so no backend hostname is baked into `cairn-web`'s image and both halves share one origin, which
the session cookie requires (`secure`, `SameSite=Strict`). Caddy resolves `api` and `web` over
`edge` per request, not at startup, so the proxy comes up and recovers whether or not Cairn is
running.

**`/api` is a proxy-only prefix and must be stripped.** The contract declares `/session`,
`/portfolio` and the rest at the root; the prefix exists solely to tell the two backends apart at
this one point, and `cairn-web`'s `proxy.conf.json` strips it the same way in development. Hence
`handle_path /api/*`, not `handle`. Forwarded verbatim it 404s every authenticated call while
looking healthy from outside: unauthenticated, Spring answers 401 before routing, so a missing
route is indistinguishable from a guarded one. What Spring Security serves itself is forwarded
unchanged: `/logout*` for sign-out, and the two endpoints the SPA plays the passkey ceremony
against itself: `/webauthn/*` for the registration options and verification, and `/login/webauthn`
for the assertion endpoint (it sits under `/login` rather than `/webauthn`). `/login` itself is
deliberately absent and matched by neither `/webauthn/*` nor `/login/webauthn`: the frontend owns
that path, since Spring's generated sign-in page is switched off.

Only `api` and `web` join `edge`. **`postgres` deliberately stays on the default network**, out of
reach of every other application sharing the proxy. So do `kafka` and `worker`: nothing outside
this compose project needs to reach either.

**`kafka`/`worker`.** `kafka` is a single-node KRaft broker (`apache/kafka`, no ZooKeeper),
`worker` is `cairn-kafka`'s image: it declares the `cairn.prices`/`cairn.portfolio` topics on
startup, runs the intraday refresh scheduler and consumes `refresh.completed` events; it has no web
server (`spring.main.web-application-type: none`). `deploy.sh`'s `up`
argument order (`postgres kafka worker api`) is not a start order and `api` does not depend on
`worker`: the worker creates the topics on its first start, and an event published before that is
dropped and logged. `deploy.sh` pulls/prunes the `cairn-kafka` image alongside the other three.
**PostgreSQL first, then the
event**: `QuoteAnnouncementService` publishes only after the caller's transaction has written the
quote/refresh outcome, never before, so a broker outage can drop an event but never leaves a
published event pointing at data that was never saved.

`CAIRN_DOMAIN` is set twice, and the two must agree: in `/srv/cairn/.env` (feeding the api
container's `CAIRN_RP_ID`/`CAIRN_ORIGIN`) and in `/srv/proxy/.env`, written by infra's Ansible
(feeding the snippet's site address, since Caddy is what reads it). It should be a subdomain,
never the apex: an apex `rp-id`
would make Cairn's passkeys usable by every other application on the domain. Changing it after the
first passkey registration breaks every existing credential — `rp-id` is bound into them.

Never run `compose.yaml` and `compose.prod.yaml` on the same host: both declare
`postgres`/`api`/`web`/`schema`/`batch`/`kafka`/`worker` against the same `cairn-data` and
`kafka-data` volume names.

**Deploying.** `.github/workflows/deploy.yml` runs on every push to `main`: it calls `ci.yml`,
builds `api`, `schema`, `batch` and `kafka`, pushes them to GHCR as `sha-` followed by the commit's
first 7 characters, ships `compose.prod.yaml`, `cairn.caddy`, `deploy/deploy.sh`, `deploy/run-batch.sh`
and `deploy/cairn.cron` to the server, reloads the shared proxy rather than restarting it so the
other sites keep serving, applies the Liquibase changelog on its own before anything
starts, then brings up `postgres`, `kafka`, `worker` and `api` and waits for `/api/actuator/health`. Rolling back is
running the workflow by hand with the full SHA of an earlier commit: it checks that the images
exist, then deploys that commit's files and images without building. A running deploy is never
cancelled; GitHub keeps only the newest pending run in the `deploy` concurrency group, so a
rollback dispatched while another run waits can be superseded by a later push, and the run list
must be checked after dispatching one. Rolling back to a commit deployed before lot K means
dispatching the Deploy workflow with "Use workflow from" set to the last release tag before lot K,
so the old workflow runs with its old image list; `kafka` and `worker` then keep running untouched
until the next deploy, which is harmless. `cairn-web` has the mirror workflow for `web` alone; both
scripts take `/srv/cairn/.deploy.lock` because both edit `/srv/cairn/.env`, and GitHub concurrency
does not span repositories.

**Scheduled jobs.** `deploy/cairn.cron` holds the batch schedule, in the server's timezone
(Europe/Paris). `deploy.sh` rebuilds the deploy user's crontab from every application's
`/srv/*/*.cron`, so never install a fragment alone. Each line goes through `deploy/run-batch.sh`,
which adds a unique `run.at` job parameter (the jobs have no incrementer, and Spring Batch refuses
to rerun a completed instance with identical parameters) and pings the Uptime Kuma push monitor
named in `/srv/cairn/.env` only when the run succeeds. Intraday refreshes are not cron jobs: they
live in the worker itself, as a `@Scheduled` method (zone Europe/Paris, EQUITY+ETF every 15 min
Mon-Fri 9:00-17:45, CRYPTO every 15 min), which is why `CRYPTO` has left `deploy/cairn.cron`. Its
heartbeat is the `KUMA_PUSH_INTRADAY` push monitor, pinged only when a run refreshed something or
had no failure. `snapshotJob` runs at 23:30 Paris (`30 23 * * *`), recording the day's measured
portfolio value and its `ACCOUNT_TYPE`/`ASSET_CLASS` ventilations; its heartbeat is the
`KUMA_PUSH_SNAPSHOT` push monitor, interval 25 h. The worker's `DailySummaryScheduler`
(`kafka/schedule/`) sends the day's Telegram summary Monday to Friday at 19:45 Paris
(`0 45 19 * * MON-FRI`), reading `GetPerformanceUseCase.performance(D1)` so its numbers match the
dashboard's 1J tile; not on weekends, when a stock's day change would just replay Friday's. Its
heartbeat is the `KUMA_PUSH_SUMMARY` push monitor, interval 73 h (see the README's push monitors
section for why 25 h does not fit a weekday-only job). `TelegramNotificationAdapter`
(`cairn-adapter`'s `client/`) is wired in every application, not the worker alone:
`TelegramClientProperties`' `botToken`/`chatId` carry no validation annotation, so `cairn-api` and
`cairn-batch` start without any `TELEGRAM_*` variable set, and the adapter only checks them when a
send is actually attempted.

**Disk.** `deploy.sh` deletes every Cairn backend image except the deployed tag: `docker image
prune` only removes untagged images, and each deploy leaves three tagged ones behind.

The deploy authenticates as the `deploy` user with the key in the `DEPLOY_SSH_KEY` secret of the
`production` environment, which both repositories need. The host address and its SSH host key sit
in the workflow in clear: neither is a secret, and pinning the fingerprint is what stops a deploy
from trusting whatever answers on that address. Reinstalling the server changes that host key.

Rollback reaches only commits deployed this way: earlier commits have no `sha-` images. Liquibase
never undoes a changeset either, so rolling back across a schema change leaves an older API facing a
newer schema, which `ddl-auto: validate` may refuse.

### Releasing

Work happens on a short branch and lands through a pull request; a squash merge makes the PR title
the commit on main. The merge deploys, and a deploy that worked publishes a CalVer release,
`vYYYY.MM.DD.n`, whose notes git-cliff builds from the commit subjects since the previous one
(`cliff.toml`). The Releases page is therefore the history of what production has run. There is no
CHANGELOG.md: it would mean a bot commit on main per deploy, for content the Releases page already
holds. A rollback redeploys an already released commit and mints no version.

## Gotchas

- **A killed migration leaves a lock that blocks every later deploy.** Liquibase takes a row in
  `DATABASECHANGELOGLOCK` before applying anything and releases it on exit. A `schema` container
  killed mid-run, by a reboot or a `docker kill`, never releases it: the next deploy waits five
  minutes on `Waiting for changelog lock` and fails with `Could not acquire change log lock`.
  Nothing times out on its own and no retry helps. Clear it on the server, then redeploy:

  ```bash
  docker exec -i cairn-postgres-1 psql -U app -d app -c "update databasechangeloglock set locked = false, lockgranted = null, lockedby = null;"
  ```

  Check first that no migration is genuinely running (`docker ps | grep schema`): releasing the
  lock under a live migration lets a second one run against a half-applied changeset.

- **`deploy.sh` is shipped as a file, never piped into `ssh bash -s`.** `docker compose run`
  attaches the caller's stdin to the container, so a piped script is read and discarded by the
  migration container: the first release migrated the database and then silently never started the
  API.

- **The `local` profile hides the whole security layer.** `app.security.permit-all=true` disables
  CSRF and authentication outright, so nothing that depends on them is exercised until production.
  That is how the CSRF token handler shipped rejecting every write the SPA made: Spring's own forms
  carry an XOR-masked token, Angular echoes the raw cookie value in a header, and only one of the
  two was accepted. Reads were unaffected, which made it look like a loading problem. When touching
  `WebAuthnConfig`, reason about both clients, and check against the deployed application.

- **A blank string is not a value.** The unique indexes on `instruments` are partial (`WHERE isin IS NOT NULL`), so
  rows without an ISIN or a source reference only coexist while the column is `null`. The web form posts
  `""` for an untouched field, and two of those collide: the second crypto created without an ISIN answered
  500. `Instrument` now normalises blanks to `null`. Give any new nullable column the same treatment.
- **A domain invariant throws a `BusinessException`, never an `IllegalArgumentException`**, which the advice
  would not map at all and would surface as a 500. `DataIntegrityViolationException` maps to 409.
- The aggregator declares the Spotless plugin although it holds no Java: `spotless:check` from the root resolves the plugin prefix per project and fails on any project that lacks it.
- The demo table is named `positions` (plural): `POSITION` is a reserved word in PostgreSQL.
- **`cairn-schema` stays a test-scope dependency of the application modules only** — never add it (or `liquibase-core`) to `cairn-domain`/`cairn-adapter`, and never widen its scope past `test`. An application must never be able to migrate the database itself.
- **Without Docker, `cairn-adapter`'s coverage gate fails under `-DskipITs`**: expected, not a regression — its persistence code is only exercised by `*IT` tests.
- **`cucumber-junit-platform-engine` must stay pinned to a version built against the same `junit-jupiter` line Spring Boot manages** (see `cairn-api/pom.xml`'s `cucumber.version` comment): a newer Cucumber needs a newer JUnit Platform than this project's dependency management provides, and fails at test discovery with `NoClassDefFoundError`.
- **`CucumberIT` always reports "Tests run: 0"** in the Surefire/Failsafe console summary — cosmetic, not a sign the suite didn't run. `cucumber.plugin=pretty,summary` (`junit-platform.properties`) prints the real scenario/step counts right above it.
- `mvnw` must stay executable on Linux CI (`git update-index --chmod=+x mvnw` if git loses the mode on Windows).
- GitHub Actions in `.github/workflows/` are pinned by commit SHA — when adding one, pin it the same way.
