#!/usr/bin/env bash
# Runs one batch job from the deploy user's crontab, then pings its Uptime Kuma push monitor:
#   run-batch.sh <heartbeat variable in /srv/cairn/.env> <batch arguments...>
# Only a successful run pings, so a job that fails or never starts shows as down.
set -euo pipefail

HEARTBEAT="${1:?heartbeat variable required}"
shift

cd /srv/cairn

exec 8> "/srv/cairn/.batch-${HEARTBEAT}.lock"
flock -n 8 || { echo "a ${HEARTBEAT} run is still in progress" >&2; exit 1; }

# A deploy rewrites TAG and migrates the schema: wait for one in progress, and hold it off meanwhile.
exec 9> /srv/cairn/.deploy.lock
flock -w 600 9 || { echo "deploy lock still held after 600 s" >&2; exit 1; }

# The jobs have no incrementer, and Spring Batch refuses to rerun a completed instance with the
# same identifying parameters: run.at makes every run a new instance.
docker compose -f compose.prod.yaml --profile batch run --rm -T batch \
  "$@" "run.at=$(date +%Y%m%dT%H%M%S)" </dev/null

url=$(grep -E "^${HEARTBEAT}=" .env | cut -d= -f2- | tr -d '\r"' || true)
curl -fsS -m 10 --retry 3 -o /dev/null "${url:?${HEARTBEAT} is missing from /srv/cairn/.env}"
