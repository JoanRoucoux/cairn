# Operations

## Production layout

Cairn assumes a host shared with other applications. The server, the shared Caddy proxy and the
monitoring are managed in a separate infrastructure repository; Cairn owns `/srv/cairn` only.

```
/srv/proxy/    the shared Caddy, ports 80/443, one snippet per site in sites/
/srv/cairn/    compose.prod.yaml, .env, deploy.sh, run-batch.sh, cairn.cron
```

Every push to `main` deploys, through `.github/workflows/deploy.yml` (the Deployment section of
[AGENTS.md](../AGENTS.md) has the details). Every deploy that works publishes a CalVer release.

The core of what `deploy/deploy.sh` does, by hand, once the images are pulled:

```bash
cd /srv/cairn
sed -i "s|^TAG=.*|TAG=sha-1a2b3c4|" .env
docker compose -f compose.prod.yaml --profile migrate --profile batch pull schema api batch kafka worker registry
docker compose -f compose.prod.yaml --profile migrate run --rm -T schema </dev/null
docker compose -f compose.prod.yaml up -d --wait postgres kafka registry worker api
```

The migration runs first, on purpose: `ddl-auto: validate` means a failed migration must block the
deploy rather than half-start it. The script then rebuilds the deploy user's crontab from every
`/srv/*/*.cron` and removes the Cairn backend images other than the deployed tag.

## Domain and passkeys

`CAIRN_DOMAIN` is set in both `.env` files, `/srv/cairn/.env` and `/srv/proxy/.env`, and the two
must agree, since the api container reads one (as `CAIRN_RP_ID` and `CAIRN_ORIGIN`) and Caddy the
other. Use a subdomain, not the apex: an apex `rp-id` would make Cairn's passkeys usable by any
other application on the domain. Choose it once, too: `rp-id` is bound into every credential
registered against it, so changing the domain later breaks every existing passkey. See the
Deployment section of [AGENTS.md](../AGENTS.md) for the routing details.

## Push monitors

`deploy/run-batch.sh` pings an Uptime Kuma push monitor after each cron-triggered batch run, and
the worker's schedulers ping their own. Each monitor's URL lives in `/srv/cairn/.env`, under the
variable name its heartbeat uses:

| Variable             | Job                                       | Schedule                              |
| -------------------- | ----------------------------------------- | ------------------------------------- |
| `KUMA_PUSH_EQUITY`   | `refreshQuotesJob` (EQUITY)               | cron `0 19 * * 1-5`                   |
| `KUMA_PUSH_ETF`      | `refreshQuotesJob` (ETF)                  | cron `15 19 * * 1-5`                  |
| `KUMA_PUSH_FUND`     | `refreshQuotesJob` (FUND)                 | cron `0 11 * * 2-6`                   |
| `KUMA_PUSH_SNAPSHOT` | `snapshotJob`                             | cron `30 23 * * *`, interval 25 h     |
| `KUMA_PUSH_INTRADAY` | worker's intraday refresh scheduler       | not a cron job                        |
| `KUMA_PUSH_SUMMARY`  | worker's daily Telegram summary scheduler | `0 45 19 * * MON-FRI`, interval 73 h  |

The summary runs Monday to Friday only, so its monitor waits 73 h: a 25 h interval would alert
every weekend. A weekday failure is noticed the same evening anyway, when no message arrives.

## Backfilling quotes

`backfillQuotesJob` loads the daily close history of instruments from their price provider and
upserts it into `quotes`, one row per instrument and day. It has no cron entry and no push monitor:
it is run by hand. Run it after adding a line with a new title whose history you want on the charts, or after
an outage that left gaps in the daily closes. It is idempotent, since an existing day is overwritten
with the provider's close, so running it twice is harmless. Instruments priced by hand (`MANUAL`)
are skipped.

Parameters, all optional:

| Parameter      | Meaning                                                                                 |
| -------------- | --------------------------------------------------------------------------------------- |
| `instrumentId` | UUID of one instrument. Without it, every non-manual instrument is backfilled.          |
| `from`         | First day to keep, `YYYY-MM-DD`. Without it, `2015-01-01`.                              |
| `run.at`       | Unique value that keeps the run distinct in the logs, as for the other jobs.           |

```bash
cd /srv/cairn
docker compose -f compose.prod.yaml --profile batch run --rm -T batch \
  --spring.batch.job.name=backfillQuotesJob instrumentId=<uuid> from=2024-01-01 \
  "run.at=$(date +%Y%m%dT%H%M%S)" </dev/null
```

`deploy/run-batch.sh` is not used here: it insists on a push monitor variable and pings it, and this
job has none. Each instrument goes to the provider that supports its price source, with its own
depth: Yahoo returns its whole series (`range=max`), SG Sirius and Amundi their whole NAV series,
and CoinGecko only the last 90 days, whatever `from` says. `from` only trims what the provider returned. A
provider failure for one instrument fails the step and the job, and the instruments already
processed keep their rows; fix the cause and rerun it.

## Telegram summary

Monday to Friday at 19:45 (Europe/Paris), the worker sends net worth, the day's change and each
envelope's change to Telegram, with the dashboard's 1D figures. To set it up:

1. Create a bot with [@BotFather](https://t.me/BotFather) (`/newbot`) and put its token in
   `/srv/cairn/.env` as `TELEGRAM_BOT_TOKEN`.
2. Open the profile page, Telegram, Connect Telegram, then press Start in Telegram.
3. Create the push monitor above and put its URL in `/srv/cairn/.env` as `KUMA_PUSH_SUMMARY`.

The weekly (Saturday 09:00), monthly (the 1st, 09:00) and yearly (January 1st, 09:05) recaps reuse the same bot and have no push monitor: a failed send only shows in the worker log, and a recap missed while the worker is down is not sent later.

The bot token never appears in the logs, not even on a failed send: neither `docker logs cairn-worker-1`
nor the api container's logs may ever show it. Until the chat is linked, recaps are skipped with the INFO line
`summary not sent: no Telegram chat linked`, and the daily one still pings its heartbeat.

## Reading the events

Event payloads on `cairn.prices` and `cairn.portfolio` are Avro binary, so the broker's console
consumer prints unreadable values. Decode them with `kcat` through the registry's Confluent
compatible API, from a throwaway container on the production network (`cairn_default`):

```bash
docker run --rm --network cairn_default edenhill/kcat:1.7.1 \
  -b kafka:9092 -t cairn.portfolio -C -e -o -5 -s value=avro -r http://registry:8080/apis/ccompat/v7
```

`-e` stops at the end of the topic and `-o -5` reads the last five records of each partition; until 2026-11-01 the old JSON records still retained make kcat stop with `Invalid CP1 magic byte 123`, so read only the newest record with `-o -1` until then. The registry keeps at most 5 database connections (`select count(*) from pg_stat_activity where datname = 'app' and application_name not like 'psql%'` on the `postgres` container). To list the registered schemas, call the registry from the same
network (the `worker` container has no `curl`):

```bash
docker run --rm --network cairn_default curlimages/curl:8.11.1 -s \
  http://registry:8080/apis/registry/v3/groups/com.roucoux.cairn.events/artifacts
```

Two artifacts are expected, `PriceUpdatedEvent` and `RefreshCompletedEvent`, registered by the
worker at startup. The registry is not published on the host.

## Reading the logs

Logs stay in Docker; their rotation is the host's concern (infra repository). In prod every line is
ECS JSON (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` in `compose.prod.yaml`), local runs stay plain text.

```bash
cd /srv/cairn
docker compose -f compose.prod.yaml logs --no-log-prefix api | jq -R 'fromjson? | select(.log.level != "INFO")'
docker compose -f compose.prod.yaml logs --no-log-prefix api worker | jq -R 'fromjson? | select(.traceId == "<trace id>")'
docker compose -f compose.prod.yaml logs --no-log-prefix api | jq -R 'fromjson? | select(.useCase) | {useCase, method, outcome, durationMs}'
```

`fromjson?` skips the few non-JSON lines printed before Logback starts. Every call to an inbound port
produces one `cairn.usecase` line with `useCase`, `method`, `outcome` (`success` or `failure`),
`durationMs` and, on failure, the `exception` class. Arguments, results and the exception message are
never logged there, since a domain message can carry a quantity.

Every line logged while handling a request, a Kafka record, a batch chunk or a scheduled run
carries `traceId` and `spanId`; startup lines do not. The trace id follows an event from `api` to `worker`
through the Kafka record headers, so the second command above shows both sides of a quote
announcement. Batch use case lines carry a trace id too, but one per chunk or item, not one per job
run. Nothing is exported: `management.tracing.export.otlp.enabled` and
`management.otlp.metrics.export.enabled` are both `false`. Do not switch off
`management.tracing.export.enabled` instead, which in Spring Boot 4 also stops the propagation and
the `[traceId-spanId]` prefix of the text format.

A stack trace appears once, where the failure leaves the application: a 502 in the API, a skipped
item or a failed job in a batch, a Kafka publish or consume, a scheduler run. A business refusal
raised by a use case (422, 404) is a single WARN line without stack. A CSV import rejected for its
shape (by `PortfolioCsvReader`, before any use case runs) leaves no log line.
A failed batch job is one ERROR line without stack;
the stack is Spring Batch's own step failure line just above it. An asynchronous Kafka send failure
is logged by Spring Kafka's `LoggingProducerListener`, in its own wording.
