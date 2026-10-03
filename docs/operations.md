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
docker compose -f compose.prod.yaml --profile migrate --profile batch pull schema api batch kafka worker
docker compose -f compose.prod.yaml --profile migrate run --rm -T schema </dev/null
docker compose -f compose.prod.yaml up -d --wait postgres kafka worker api
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

## Telegram summary

Monday to Friday at 19:45 (Europe/Paris), the worker sends net worth, the day's change and each
envelope's change to Telegram, with the dashboard's 1D figures. To set it up:

1. Create a bot with [@BotFather](https://t.me/BotFather) (`/newbot`) and put its token in
   `/srv/cairn/.env` as `TELEGRAM_BOT_TOKEN`.
2. Send the bot any message, then open `https://api.telegram.org/bot<token>/getUpdates` and put
   `message.chat.id` in `/srv/cairn/.env` as `TELEGRAM_CHAT_ID`.
3. Create the push monitor above and put its URL in `/srv/cairn/.env` as `KUMA_PUSH_SUMMARY`.

The bot token never appears in the logs, not even on a failed send: `docker logs cairn-worker-1`
must never show it.
