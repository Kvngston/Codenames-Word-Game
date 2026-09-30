#!/usr/bin/env bash
# Deploys one release tag on the VM. It is the forced command for the CI deploy
# key (see README), so that key can do nothing but this: the only input is the
# tag, passed by `ssh <host> <tag>` and read from SSH_ORIGINAL_COMMAND.
#
# Everything runs inside main(), which bash reads in full before running,
# because `git checkout` below replaces this file mid-run.
set -euo pipefail

main() {
  local tag="${1:-${SSH_ORIGINAL_COMMAND:-}}"
  if [[ ! "$tag" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]]; then
    echo "Usage: ci-deploy.sh <release-tag>" >&2
    exit 2
  fi

  local deploy_dir repo
  deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  repo="$(git -C "$deploy_dir" rev-parse --show-toplevel)"

  echo "==> Fetching $tag"
  git -C "$repo" fetch --quiet --force --tags origin
  if ! git -C "$repo" rev-parse --quiet --verify "refs/tags/$tag^{commit}" > /dev/null; then
    echo "No tag named $tag on origin." >&2
    exit 3
  fi
  git -C "$repo" checkout --quiet --force --detach "refs/tags/$tag"

  echo "==> Building and restarting the app"
  cd "$deploy_dir"
  printf 'APP_VERSION=%s\n' "$tag" > release.env
  docker compose -f compose.prod.yaml up -d --build --remove-orphans
  # Compose doesn't restart containers when a mounted config file changes, and
  # neither Caddy nor Prometheus watches its files, so reload both. Grafana
  # rescans its dashboards by itself.
  docker compose -f compose.prod.yaml exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
  docker compose -f compose.prod.yaml kill -s HUP prometheus > /dev/null
  docker image prune -f > /dev/null

  echo "==> $tag is deployed"
}

main "$@"; exit $?
