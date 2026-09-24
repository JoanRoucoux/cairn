#!/usr/bin/env bash
set -euo pipefail

TAG="${1:?tag required}"
GHCR_USER="${2:?github actor required}"
# The token arrives on stdin, never in argv: arguments are readable through /proc.
IFS= read -r GHCR_TOKEN

cd /srv/cairn

exec 9> /srv/cairn/.deploy.lock
flock -w 600 9 || { echo "deploy lock still held after 600 s" >&2; exit 1; }

printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
trap 'docker logout ghcr.io > /dev/null || true' EXIT

sed -i "s|^TAG=.*|TAG=${TAG}|" .env

# batch as well: cron runs it later without registry credentials.
docker compose -f compose.prod.yaml --profile migrate --profile batch pull schema api batch kafka worker
docker compose -f compose.prod.yaml --profile migrate run --rm -T schema </dev/null
docker compose -f compose.prod.yaml up -d --wait --wait-timeout 300 postgres kafka worker api

awk 1 /srv/*/*.cron | crontab -

docker image ls --format '{{.Repository}}:{{.Tag}}' \
  | grep -E '^ghcr\.io/joanroucoux/cairn-(api|schema|batch|kafka):' \
  | grep -vE ":${TAG}\$" \
  | xargs -r docker image rm || true
docker image prune -f
