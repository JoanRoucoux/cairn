#!/usr/bin/env bash
# Runs on the server as the deploy user. Shipped by .github/workflows/deploy.yml.
#
# A file rather than a script piped into `ssh bash -s`: `docker compose run` attaches the caller's
# stdin to the container, so a piped script is read and discarded by the migration container.
# The first release migrated the database and then silently never started the API.
set -euo pipefail

TAG="${1:?tag required}"
GHCR_USER="${2:?github actor required}"
# The token arrives on stdin, never in argv: arguments are readable through /proc.
IFS= read -r GHCR_TOKEN

cd /srv/cairn

# cairn-web's deploy edits the same .env, and GitHub concurrency groups do not span repositories.
exec 9> /srv/cairn/.deploy.lock
flock -w 600 9 || { echo "deploy lock still held after 600 s" >&2; exit 1; }

printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
trap 'docker logout ghcr.io > /dev/null || true' EXIT

sed -i "s|^TAG=.*|TAG=${TAG}|" .env

# batch as well: cron runs it later without registry credentials.
docker compose -f compose.prod.yaml --profile migrate --profile batch pull schema api batch
# Schema first and alone: the API must never meet a database older than itself.
docker compose -f compose.prod.yaml --profile migrate run --rm -T schema </dev/null
# --wait fails this command when the API never turns healthy.
docker compose -f compose.prod.yaml up -d --wait --wait-timeout 180 postgres api

# Every application's fragment at once: installing this one alone would wipe the others' jobs.
awk 1 /srv/*/*.cron | crontab -

# `prune -f` only removes untagged images, and every deploy leaves three tagged ones behind.
# A rollback pulls its images again.
docker image ls --format '{{.Repository}}:{{.Tag}}' \
  | grep -E '^ghcr\.io/joanroucoux/cairn-(api|schema|batch):' \
  | grep -vE ":${TAG}\$" \
  | xargs -r docker image rm || true
docker image prune -f
