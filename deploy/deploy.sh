#!/usr/bin/env bash
# Runs on the server as the deploy user. Shipped by .github/workflows/release.yml.
#
# It is a file rather than a script piped into `ssh bash -s`, and that is the whole point:
# `docker compose run` attaches the caller's stdin to the container, so a piped script is read
# and discarded by the migration container. The first release migrated the database and then
# silently never started the API, reporting success all the way.
set -euo pipefail

TAG="${1:?tag required}"
GHCR_USER="${2:?github actor required}"
# The token arrives on stdin, never in argv: arguments are readable by any other process on the
# host through /proc.
IFS= read -r GHCR_TOKEN

printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

cd /srv/cairn
sed -i "s|^TAG=.*|TAG=${TAG}|" .env

docker compose -f compose.prod.yaml --profile migrate pull schema api
# Schema first and alone: the API must never meet a database older than itself. </dev/null is
# not decoration, see the header.
docker compose -f compose.prod.yaml --profile migrate run --rm -T schema </dev/null
# --wait makes this command fail when the API never turns healthy, instead of succeeding and
# leaving the verdict to a caller that cannot see inside the container.
docker compose -f compose.prod.yaml up -d --wait --wait-timeout 180 postgres api

docker logout ghcr.io
docker image prune -f
